package com.omnibuds.core.firmware

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.state.CapabilityState

class FirmwareCapabilityAndLifecycleTest {

    private val ancFeature = FeatureId.of("noise-control", "anc")

    @Test
    fun firmwareDependentCapabilityEvaluation() {
        val ancDep = FirmwareDependentCapability(
            featureId = ancFeature,
            modelScope = setOf("OB-PRO-01"),
            firmwareConstraint = FirmwareConstraint.AtLeast(FirmwareVersion.parse("2.0.0")),
            availableState = CapabilityState.SUPPORTED_VOLATILE,
            fallbackState = CapabilityState.UNSUPPORTED,
            evidenceReference = "ANC-FW-2.0",
        )

        assertEquals(
            CapabilityState.SUPPORTED_VOLATILE,
            ancDep.evaluate("OB-PRO-01", FirmwareVersion.parse("2.0.0"))
        )
        assertEquals(
            CapabilityState.SUPPORTED_VOLATILE,
            ancDep.evaluate("OB-PRO-01", FirmwareVersion.parse("2.1.0"))
        )
        assertEquals(
            CapabilityState.UNSUPPORTED,
            ancDep.evaluate("OB-PRO-01", FirmwareVersion.parse("1.9.0"))
        )
        assertEquals(
            CapabilityState.UNKNOWN,
            ancDep.evaluate("OB-PRO-01", FirmwareVersion.Unknown)
        )
    }

    @Test
    fun stateInvalidatorUpdatesCapabilitiesOnFirmwareChange() {
        val ancDep = FirmwareDependentCapability(
            featureId = ancFeature,
            modelScope = setOf("OB-PRO-01"),
            firmwareConstraint = FirmwareConstraint.AtLeast(FirmwareVersion.parse("2.0.0")),
            availableState = CapabilityState.SUPPORTED_VOLATILE,
            fallbackState = CapabilityState.UNSUPPORTED,
            evidenceReference = "ANC-FW-2.0",
        )

        val invalidator = FirmwareStateInvalidator(listOf(ancDep))

        val initial = mapOf(ancFeature.qualifiedName to CapabilityState.SUPPORTED_VOLATILE)

        // Downgrade or mismatch firmware
        val updated = invalidator.reconcileCapabilitiesOnFirmwareChange(
            modelId = "OB-PRO-01",
            previousVersion = FirmwareVersion.parse("2.0.0"),
            newVersion = FirmwareVersion.parse("1.8.0"),
            currentCapabilities = initial,
        )

        assertEquals(CapabilityState.UNSUPPORTED, updated[ancFeature.qualifiedName])
    }

    @Test
    fun cancelPendingOperationWhenFirmwareNoLongerSatisfiesConstraint() {
        val invalidator = FirmwareStateInvalidator()
        val constraint = FirmwareConstraint.AtLeast(FirmwareVersion.parse("2.0.0"))

        assertFalse(
            invalidator.shouldCancelPendingOperation(
                operationRequiredConstraint = constraint,
                newVersion = FirmwareVersion.parse("2.1.0"),
            )
        )

        assertTrue(
            invalidator.shouldCancelPendingOperation(
                operationRequiredConstraint = constraint,
                newVersion = FirmwareVersion.parse("1.5.0"),
            )
        )

        assertTrue(
            invalidator.shouldCancelPendingOperation(
                operationRequiredConstraint = constraint,
                newVersion = FirmwareVersion.Unknown,
            )
        )
    }
}
