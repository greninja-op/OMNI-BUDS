package com.omnibuds.core.feature

import com.omnibuds.core.capability.CapabilityAvailability
import com.omnibuds.core.capability.CapabilitySnapshot
import com.omnibuds.core.capability.DeviceCapabilities
import com.omnibuds.core.capability.DiscoveryState
import com.omnibuds.core.capability.FeatureCapability
import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.config.ConfigurationValue
import com.omnibuds.core.state.CapabilityState
import com.omnibuds.core.state.VerificationLevel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Shared fixtures for the Phase 9 feature-engine tests.
 *
 * Everything here is scripted: no device is implied, and the ceiling is
 * IMPLEMENTED. The scripted port is the only [FeatureProtocolPort] in the
 * repository, and it lives in test sources — `PhaseNineScopeTest` enforces
 * that no production source implements the seam.
 */
internal object FeatureTestFixtures {

    /** A capability record with the affordances its rung requires. */
    fun record(
        feature: FeatureId,
        state: CapabilityState,
        transport: TransportKind = TransportKind.GATT,
        availability: CapabilityAvailability = CapabilityAvailability.AVAILABLE,
    ): FeatureCapability = when (state) {
        CapabilityState.UNKNOWN -> FeatureCapability.unknown(feature)
        CapabilityState.UNSUPPORTED -> FeatureCapability.unsupported(feature, "p9-fixture", transport)
        CapabilityState.READ_ONLY -> FeatureCapability(
            feature = feature,
            state = state,
            readable = true,
            writable = false,
            transport = transport,
            protocolId = "p9-fixture",
            requiresConnection = true,
            verification = VerificationLevel.IMPLEMENTED,
        )

        CapabilityState.SUPPORTED_VOLATILE -> FeatureCapability(
            feature = feature,
            state = state,
            readable = true,
            writable = true,
            transport = transport,
            protocolId = "p9-fixture",
            requiresConnection = true,
            verification = VerificationLevel.IMPLEMENTED,
        )

        // SUPPORTED_PERSISTENT needs at least HARDWARE_VERIFIED evidence, and
        // PERSISTENCE_VERIFIED needs exactly PERSISTENCE_VERIFIED (FeatureCapability init).
        CapabilityState.SUPPORTED_PERSISTENT -> FeatureCapability(
            feature = feature,
            state = state,
            readable = true,
            writable = true,
            transport = transport,
            protocolId = "p9-fixture",
            requiresConnection = true,
            verification = VerificationLevel.HARDWARE_VERIFIED,
        )

        CapabilityState.PERSISTENCE_VERIFIED -> FeatureCapability(
            feature = feature,
            state = state,
            readable = true,
            writable = true,
            transport = transport,
            protocolId = "p9-fixture",
            requiresConnection = true,
            verification = VerificationLevel.PERSISTENCE_VERIFIED,
        )
    }

    /** A snapshot over [records], with availability defaulting to AVAILABLE. */
    fun snapshot(
        vararg records: FeatureCapability,
        availability: Map<FeatureId, CapabilityAvailability> = emptyMap(),
    ): CapabilitySnapshot {
        var capabilities = DeviceCapabilities.empty()
        for (record in records) {
            capabilities = capabilities.with(record.feature, record)
        }
        return CapabilitySnapshot(
            subjectRef = "p9-fixture-device",
            protocolId = "p9-fixture",
            protocolVersion = "1",
            discoveredAtEpochMillis = null,
            completion = DiscoveryState.COMPLETE,
            capabilities = capabilities,
            availability = availability,
            evidence = emptyList(),
            partialFailures = emptyList(),
            unresolvedConflicts = emptyList(),
            schemaVersion = CapabilitySnapshot.SCHEMA_VERSION,
        )
    }
}

/**
 * A scripted [FeatureProtocolPort] for tests.
 *
 * Reads and writes are programmed per feature with [onRead]/[onWrite]; anything
 * unprogrammed fails with [OmniBudsErrorCategory.READ_FAILED] /
 * [OmniBudsErrorCategory.WRITE_REJECTED] so an unscripted call is loud, not
 * silently successful. Calls are recorded in [calls] for assertions. [supportsRead]
 * and [supportsWrite] are constructor flags.
 */
internal class ScriptedFeaturePort(
    override val supportsRead: Boolean = true,
    override val supportsWrite: Boolean = true,
) : FeatureProtocolPort {

    sealed interface Call {
        data class Read(val feature: FeatureId) : Call
        data class Write(val feature: FeatureId, val value: ConfigurationValue) : Call
    }

    private val mutex = Mutex()
    val calls = mutableListOf<Call>()

    var readHandler: suspend (FeatureId) -> OperationOutcome<ConfigurationValue> = { feature ->
        OperationOutcome.Failure(
            OmniBudsError.of(
                com.omnibuds.core.common.OmniBudsErrorCategory.READ_FAILED,
                "unscripted-read",
                "no read scripted for ${feature.qualifiedName}",
            ),
        )
    }

    var writeHandler: suspend (FeatureId, ConfigurationValue) -> OperationOutcome<Unit> = { feature, _ ->
        OperationOutcome.Failure(
            OmniBudsError.of(
                com.omnibuds.core.common.OmniBudsErrorCategory.WRITE_REJECTED,
                "unscripted-write",
                "no write scripted for ${feature.qualifiedName}",
            ),
        )
    }

    /** The value the port reports on read-back; kept in sync by [writeHandler] wrappers. */
    val deviceValues = mutableMapOf<FeatureId, ConfigurationValue>()

    override suspend fun read(feature: FeatureId): OperationOutcome<ConfigurationValue> {
        mutex.withLock { calls += Call.Read(feature) }
        return readHandler(feature)
    }

    override suspend fun write(feature: FeatureId, value: ConfigurationValue): OperationOutcome<Unit> {
        mutex.withLock { calls += Call.Write(feature, value) }
        return writeHandler(feature, value)
    }

    fun readsOf(feature: FeatureId): Int = calls.filterIsInstance<Call.Read>().count { it.feature == feature }
    fun writesOf(feature: FeatureId): Int = calls.filterIsInstance<Call.Write>().count { it.feature == feature }
}
