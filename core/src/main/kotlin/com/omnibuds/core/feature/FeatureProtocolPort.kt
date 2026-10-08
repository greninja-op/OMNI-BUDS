package com.omnibuds.core.feature

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.config.ConfigurationValue

/**
 * The read-only seam the feature engine drives to reach a device.
 *
 * This is the Phase 8 [com.omnibuds.core.capability.CapabilityDiscoverySource]
 * pattern applied to feature control (ADR-P9-002): the engine is *handed* a port,
 * and whatever binds a port to a real Phase 7 protocol session — a resolved
 * `EarbudProtocol`'s [com.omnibuds.core.protocol.FeatureReadSupport] /
 * [com.omnibuds.core.protocol.FeatureWriteSupport] — is wired in a later phase,
 * where depending on the protocol layer is legal. The engine therefore never
 * imports the protocol layer, never sees a transport, and never touches a
 * Bluetooth API; it cannot bypass the protocol abstractions because it cannot
 * reach past them.
 *
 * Everything here is addressed by [FeatureId] and valued in [ConfigurationValue]:
 * the port is the feature layer's view of the protocol contracts, and the
 * value-shape checking ([FeatureValueType.accepts]) plus untrusted-input
 * validation happen in the engine, not in the port.
 *
 * Phase 9 ships no production implementation. The only implementations are
 * test-only scripted ports in test sources, which `PhaseNineScopeTest` enforces
 * (the Phase 7/8 precedent: a test double reachable from production code could
 * report hardware behaviour that does not exist).
 */
interface FeatureProtocolPort {

    /** Whether the bound protocol can read feature values. */
    val supportsRead: Boolean

    /** Whether the bound protocol can write feature values. */
    val supportsWrite: Boolean

    /**
     * Read the current value of [feature].
     *
     * Suspending because the answer comes from a device. Cancellation must
     * propagate and report `Cancelled`, not success. A failed read yields no
     * value at all, never a zeroed one.
     */
    suspend fun read(feature: FeatureId): OperationOutcome<ConfigurationValue>

    /**
     * Ask the device to set [feature] to [value].
     *
     * Success means the channel reported that the command was accepted — rung 2
     * of the persistence ladder. It does not establish that the value was
     * applied; the engine reads the feature back and records the comparison.
     * A timeout is not a licence to send again: the engine follows it with a
     * read, never a re-issue (PROTO-ERR-002).
     */
    suspend fun write(feature: FeatureId, value: ConfigurationValue): OperationOutcome<Unit>
}
