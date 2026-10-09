package com.omnibuds.core.hil

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private fun profile(
    schemaVersion: Int = 1,
    apiLevel: Int? = 35,
    verificationStatus: ProfileVerificationStatus = ProfileVerificationStatus.SIMULATED,
) = HardwareProfile(
    profileId = "hil-profile-1",
    schemaVersion = schemaVersion,
    hostModel = "Pixel 8",
    hostBuild = "AP1A",
    apiLevel = apiLevel,
    appVersion = "0.38.0",
    gitCommit = "abc123",
    deviceManufacturer = null,
    deviceModel = null,
    firmwareVersion = null,
    transportId = null,
    protocolId = null,
    prerequisites = listOf("simulated-transport"),
    allowedOperations = setOf("read-only"),
    verificationStatus = verificationStatus,
)

private fun campaign(
    checks: List<HilCheck> = listOf(
        HilCheck("hil.id.discovery", "device discovery", OperationCategory.READ_ONLY_OBSERVATION, requiresPhysical = false),
        HilCheck("hil.conn.establish", "connection establishment", OperationCategory.CONNECTION_MANAGEMENT, requiresPhysical = true),
    ),
) = HilCampaign(
    campaignId = "hil-smoke",
    version = 1,
    description = "smoke",
    checks = checks,
)

class HardwareProfileValidatorTest {

    @Test
    fun `valid profile passes`() {
        assertTrue(HardwareProfileValidator.validate(profile()) is ProfileValidation.Valid)
    }

    @Test
    fun `unknown fields are explicit nulls not invented`() {
        val p = profile()
        assertEquals(null, p.deviceManufacturer)
        assertEquals(null, p.firmwareVersion)
    }

    @Test
    fun `bad schema version fails`() {
        assertTrue(
            HardwareProfileValidator.validate(profile(schemaVersion = 99))
                is ProfileValidation.Invalid,
        )
    }

    @Test
    fun `api level out of range fails`() {
        assertTrue(
            HardwareProfileValidator.validate(profile(apiLevel = 21))
                is ProfileValidation.Invalid,
        )
    }

    @Test
    fun `blank profile id fails`() {
        assertTrue(
            HardwareProfileValidator.validate(profile().copy(profileId = " "))
                is ProfileValidation.Invalid,
        )
    }
}

class HilSafetyGateTest {

    private val gate = HilSafetyGate(HilSafetyPolicy.DEFAULT_SAFE)

    @Test
    fun `physical execution denied by default`() {
        val d = gate.decide(
            HilEnvironment.PHYSICAL_ANDROID_HOST,
            OperationCategory.READ_ONLY_OBSERVATION,
        )
        assertTrue(d is SafetyDecision.Denied)
    }

    @Test
    fun `hardware writes denied by default`() {
        val d = gate.decide(
            HilEnvironment.SIMULATED,
            OperationCategory.HARDWARE_WRITE,
        )
        assertTrue(d is SafetyDecision.Denied)
    }

    @Test
    fun `simulated read-only allowed`() {
        val d = gate.decide(
            HilEnvironment.SIMULATED,
            OperationCategory.READ_ONLY_OBSERVATION,
        )
        assertTrue(d is SafetyDecision.Allowed)
    }

    @Test
    fun `destructive operations can never be routine`() {
        assertThrows<IllegalArgumentException> {
            HilSafetyPolicy(
                allowlistedOperations = setOf(OperationCategory.FIRMWARE_UPDATE),
            )
        }
    }

    @Test
    fun `writes require physical execution invariant`() {
        assertThrows<IllegalArgumentException> {
            HilSafetyPolicy(
                physicalExecutionAllowed = false,
                hardwareWritesAllowed = true,
            )
        }
    }

    @Test
    fun `mutating operation needs all seven authorizations`() {
        val denied = gate.authorizeMutatingOperation(
            supportedDevice = true,
            protocolVerified = true,
            operatorConsent = false,
            rollbackAvailable = true,
            timeoutBounded = true,
            readBackPlanned = true,
            auditable = true,
        )
        assertTrue(denied is SafetyDecision.Denied)
        assertTrue((denied as SafetyDecision.Denied).reason.contains("operator consent"))
    }

    @Test
    fun `mutating operation allowed when all present`() {
        val allowed = gate.authorizeMutatingOperation(
            supportedDevice = true,
            protocolVerified = true,
            operatorConsent = true,
            rollbackAvailable = true,
            timeoutBounded = true,
            readBackPlanned = true,
            auditable = true,
        )
        assertTrue(allowed is SafetyDecision.Allowed)
    }
}

class HilCampaignExecutorTest {

    private val executor = HilCampaignExecutor(HilSafetyGate(HilSafetyPolicy.DEFAULT_SAFE))

    @Test
    fun `dry run executes no physical operations`() {
        val rig = DryRunHilRig(environment = HilEnvironment.SIMULATED)
        val report = executor.execute(campaign(), rig)
        assertFalse(rig.performedPhysicalOperation)
        assertEquals(
            listOf("initialize", "cleanup"),
            rig.requestedOperations,
        )
    }

    @Test
    fun `physical checks deferred not passed`() {
        val rig = DryRunHilRig(environment = HilEnvironment.SIMULATED)
        val report = executor.execute(campaign(), rig)
        val physical = report.checkOutcomes.single { it.checkId == "hil.conn.establish" }
        assertEquals(
            CheckStatus.DEFERRED_TO_FINAL_HARDWARE_VERIFICATION,
            physical.status,
        )
    }

    @Test
    fun `simulated read-only check passes`() {
        val rig = DryRunHilRig(environment = HilEnvironment.SIMULATED)
        val report = executor.execute(campaign(), rig)
        val check = report.checkOutcomes.single { it.checkId == "hil.id.discovery" }
        assertEquals(CheckStatus.PASS, check.status)
    }

    @Test
    fun `all stages complete in dry run`() {
        val rig = DryRunHilRig()
        val report = executor.execute(campaign(), rig)
        assertEquals(CampaignStage.values().size, report.stageOutcomes.size)
        assertTrue(report.stageOutcomes.values.all { it is StageOutcome.Completed })
    }

    @Test
    fun `cleanup failure is preserved in the report`() {
        val rig = DryRunHilRig(failCleanup = true)
        val report = executor.execute(campaign(), rig)
        assertTrue(report.stageOutcomes[CampaignStage.CLEANUP] is StageOutcome.Failed)
        assertFalse(report.clean)
    }

    @Test
    fun `init failure skips checks`() {
        val rig = DryRunHilRig(failInitialize = true)
        val report = executor.execute(campaign(), rig)
        assertTrue(report.checkOutcomes.isEmpty())
        assertTrue(report.stageOutcomes[CampaignStage.INITIALIZE_ENVIRONMENT] is StageOutcome.Failed)
    }

    @Test
    fun `cancellation stops the campaign`() {
        val rig = DryRunHilRig()
        val report = executor.execute(campaign(), rig) { true }
        assertTrue(report.stageOutcomes.values.any { it is StageOutcome.Cancelled })
    }

    @Test
    fun `empty campaign fails validation`() {
        val rig = DryRunHilRig()
        val report = executor.execute(campaign(checks = emptyList()), rig)
        assertTrue(report.stageOutcomes[CampaignStage.VALIDATE_CONFIG] is StageOutcome.Failed)
    }

    @Test
    fun `environment is recorded in the report`() {
        val rig = DryRunHilRig(environment = HilEnvironment.EMULATOR)
        val report = executor.execute(campaign(), rig)
        assertEquals(HilEnvironment.EMULATOR, report.environment)
    }
}

class HilEnvironmentTest {

    @Test
    fun `simulated does not involve physical hardware`() {
        assertFalse(HilEnvironment.SIMULATED.involvesPhysicalHardware())
        assertFalse(HilEnvironment.EMULATOR.involvesPhysicalHardware())
    }

    @Test
    fun `verified environments involve physical hardware`() {
        assertTrue(HilEnvironment.HARDWARE_DEVICE_VERIFIED.involvesPhysicalHardware())
        assertTrue(HilEnvironment.ACOUSTICALLY_MEASURED.involvesPhysicalHardware())
    }

    @Test
    fun `all six environments exist`() {
        assertEquals(6, HilEnvironment.values().size)
    }
}
