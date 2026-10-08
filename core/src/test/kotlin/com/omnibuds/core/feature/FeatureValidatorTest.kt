package com.omnibuds.core.feature

import com.omnibuds.core.capability.CapabilityAvailability
import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.common.SideEffectClass
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.config.ConfigurationValue
import com.omnibuds.core.state.CapabilityState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The ten-step validation pipeline.
 *
 * Every step is tested in isolation against pure fixtures: an undefined
 * feature, a positively unsupported one, an unknown one, a read-only write, an
 * invalid value, an unmet dependency, a conflict, an unimplemented protocol
 * operation, and a missing transport. A refusal must name the first failing
 * prerequisite in pipeline order and must never send a command — the scripted
 * port records every call, so any leak would show.
 */
class FeatureValidatorTest {

    private val anc = FeatureId.of("noise-control", "anc")
    private val unknownFeature = FeatureId.of("noise-control", "never-defined")

    private val definitions = StandardFeatures.asMap
    private val validator = FeatureValidator()
    private val port = ScriptedFeaturePort()

    private fun snapshotFor(vararg states: Pair<FeatureId, CapabilityState>) =
        FeatureTestFixtures.snapshot(
            *states.map { (feature, state) -> FeatureTestFixtures.record(feature, state) }.toTypedArray(),
        )

    private fun writeOp(feature: FeatureId, value: ConfigurationValue = ConfigurationValue.BooleanValue(true)) =
        FeatureOperation.write(feature, value, "op-1")

    @Test
    fun step1AnUndefinedFeatureIsRefused() {
        val result = validator.validate(
            writeOp(unknownFeature),
            definitions[unknownFeature],
            snapshotFor(anc to CapabilityState.SUPPORTED_VOLATILE),
            port,
            emptyMap(),
        )
        val invalid = assertIs<FeatureValidation.Invalid>(result)
        assertEquals(FeatureErrorCode.FEATURE_UNKNOWN, invalid.code)
        assertTrue(port.calls.isEmpty(), "a refused operation must not touch the port")
    }

    @Test
    fun step2UnsupportedIsRefusedAsUnsupportedNotUnknown() {
        val result = validator.validate(
            writeOp(anc),
            definitions[anc],
            snapshotFor(anc to CapabilityState.UNSUPPORTED),
            port,
            emptyMap(),
        )
        val invalid = assertIs<FeatureValidation.Invalid>(result)
        assertEquals(FeatureErrorCode.FEATURE_UNSUPPORTED, invalid.code)
    }

    @Test
    fun step2UnknownCapabilityIsRefusedAsUnknown() {
        val result = validator.validate(
            writeOp(anc),
            definitions[anc],
            snapshotFor(anc to CapabilityState.UNKNOWN),
            port,
            emptyMap(),
        )
        val invalid = assertIs<FeatureValidation.Invalid>(result)
        assertEquals(FeatureErrorCode.FEATURE_UNKNOWN, invalid.code)
    }

    @Test
    fun step3AWriteToAReadOnlyFeatureIsRefused() {
        val result = validator.validate(
            writeOp(anc),
            definitions[anc],
            snapshotFor(anc to CapabilityState.READ_ONLY),
            port,
            emptyMap(),
        )
        val invalid = assertIs<FeatureValidation.Invalid>(result)
        assertEquals(FeatureErrorCode.FEATURE_READ_ONLY, invalid.code)
    }

    @Test
    fun step3AnUnavailableFeatureIsRefused() {
        val snapshot = FeatureTestFixtures.snapshot(
            FeatureTestFixtures.record(anc, CapabilityState.SUPPORTED_VOLATILE),
            availability = mapOf(anc to CapabilityAvailability.UNAVAILABLE),
        )
        val result = validator.validate(writeOp(anc), definitions[anc], snapshot, port, emptyMap())
        val invalid = assertIs<FeatureValidation.Invalid>(result)
        assertEquals(FeatureErrorCode.FEATURE_UNAVAILABLE, invalid.code)
    }

    @Test
    fun step4AWrongShapedValueIsRefused() {
        val result = validator.validate(
            writeOp(anc, ConfigurationValue.StringValue("loud")),
            definitions[anc],
            snapshotFor(anc to CapabilityState.SUPPORTED_VOLATILE),
            port,
            emptyMap(),
        )
        val invalid = assertIs<FeatureValidation.Invalid>(result)
        assertEquals(FeatureErrorCode.INVALID_VALUE, invalid.code)
        assertTrue(invalid.detail.contains("boolean", ignoreCase = true))
    }

    @Test
    fun step4AnOutOfRangeValueIsRefused() {
        val level = StandardFeatures.ANC_LEVEL.feature
        val result = validator.validate(
            FeatureOperation.write(level, ConfigurationValue.IntValue(-1), "op-1"),
            StandardFeatures.asMap[level],
            snapshotFor(
                level to CapabilityState.SUPPORTED_VOLATILE,
                anc to CapabilityState.SUPPORTED_VOLATILE,
            ),
            port,
            emptyMap(),
        )
        val invalid = assertIs<FeatureValidation.Invalid>(result)
        assertEquals(FeatureErrorCode.INVALID_VALUE, invalid.code)
    }

    @Test
    fun step5AnUnmetDependencyRefusesTheWrite() {
        val adaptive = StandardFeatures.ADAPTIVE_ANC.feature
        // ANC is unknown: the prerequisite is not satisfied, so the write is refused.
        val result = validator.validate(
            writeOp(adaptive),
            definitions[adaptive],
            snapshotFor(adaptive to CapabilityState.SUPPORTED_VOLATILE),
            port,
            emptyMap(),
        )
        val invalid = assertIs<FeatureValidation.Invalid>(result)
        assertEquals(FeatureErrorCode.DEPENDENCY_NOT_SATISFIED, invalid.code)
    }

    @Test
    fun step5AReadIsAllowedWhenThePrerequisiteIsMerelyUnknown() {
        val adaptive = StandardFeatures.ADAPTIVE_ANC.feature
        val result = validator.validate(
            FeatureOperation.read(adaptive, "op-1"),
            definitions[adaptive],
            snapshotFor(adaptive to CapabilityState.SUPPORTED_VOLATILE),
            port,
            emptyMap(),
        )
        // Reads change nothing, so an unknown prerequisite does not stop them.
        assertTrue(result is FeatureValidation.Valid)
    }

    @Test
    fun step6AnActiveConflictRefusesTheWrite() {
        val mode = StandardFeatures.ANC_MODE.feature
        val current = mapOf(
            anc to FeatureState.Confirmed(anc, ConfigurationValue.BooleanValue(true)),
        )
        val result = validator.validate(
            FeatureOperation.write(
                mode,
                ConfigurationValue.ModeValue("anc", "ANC"),
                "op-1",
            ),
            definitions[mode],
            snapshotFor(
                mode to CapabilityState.SUPPORTED_VOLATILE,
                anc to CapabilityState.SUPPORTED_VOLATILE,
            ),
            port,
            current,
        )
        val invalid = assertIs<FeatureValidation.Invalid>(result)
        assertEquals(FeatureErrorCode.FEATURE_CONFLICT, invalid.code)
    }

    @Test
    fun step6AnInactiveConflictingFeatureDoesNotBlock() {
        val mode = StandardFeatures.ANC_MODE.feature
        val current = mapOf(
            anc to FeatureState.Confirmed(anc, ConfigurationValue.BooleanValue(false)),
        )
        val result = validator.validate(
            FeatureOperation.write(
                mode,
                ConfigurationValue.ModeValue("anc", "ANC"),
                "op-1",
            ),
            definitions[mode],
            snapshotFor(
                mode to CapabilityState.SUPPORTED_VOLATILE,
                anc to CapabilityState.SUPPORTED_VOLATILE,
            ),
            port,
            current,
        )
        assertTrue(result is FeatureValidation.Valid)
    }

    @Test
    fun step7AProtocolWithoutWriteSupportRefusesWrites() {
        val readOnlyPort = ScriptedFeaturePort(supportsWrite = false)
        val result = validator.validate(
            writeOp(anc),
            definitions[anc],
            snapshotFor(anc to CapabilityState.SUPPORTED_VOLATILE),
            readOnlyPort,
            emptyMap(),
        )
        val invalid = assertIs<FeatureValidation.Invalid>(result)
        assertEquals(FeatureErrorCode.PROTOCOL_UNAVAILABLE, invalid.code)
    }

    @Test
    fun step7UnimplementedOperationTypesFailExplicitly() {
        val operation = FeatureOperation(
            operationId = "op-1",
            feature = anc,
            type = FeatureOperationType.SUBSCRIBE,
            requestedValue = null,
            timeoutMillis = null,
            sideEffect = SideEffectClass.SIDE_EFFECTING,
        )
        val result = validator.validate(
            operation,
            definitions[anc],
            snapshotFor(anc to CapabilityState.SUPPORTED_VOLATILE),
            port,
            emptyMap(),
        )
        val invalid = assertIs<FeatureValidation.Invalid>(result)
        assertEquals(FeatureErrorCode.OPERATION_NOT_IMPLEMENTED, invalid.code)
    }

    @Test
    fun step8AnUnknownTransportRefusesTheOperation() {
        val record = FeatureTestFixtures.record(
            anc,
            CapabilityState.SUPPORTED_VOLATILE,
            transport = TransportKind.UNKNOWN,
        )
        val result = validator.validate(
            writeOp(anc),
            definitions[anc],
            FeatureTestFixtures.snapshot(record),
            port,
            emptyMap(),
        )
        val invalid = assertIs<FeatureValidation.Invalid>(result)
        assertEquals(FeatureErrorCode.TRANSPORT_UNAVAILABLE, invalid.code)
    }

    @Test
    fun aFullyValidWritePasses() {
        val result = validator.validate(
            writeOp(anc),
            definitions[anc],
            snapshotFor(anc to CapabilityState.SUPPORTED_VOLATILE),
            port,
            emptyMap(),
        )
        assertTrue(result is FeatureValidation.Valid)
        assertTrue(port.calls.isEmpty(), "validation performs no I/O")
    }

    @Test
    fun aFullyValidReadPassesOnAReadOnlyFeature() {
        val result = validator.validate(
            FeatureOperation.read(anc, "op-1"),
            definitions[anc],
            snapshotFor(anc to CapabilityState.READ_ONLY),
            port,
            emptyMap(),
        )
        assertTrue(result is FeatureValidation.Valid)
    }
}
