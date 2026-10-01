package com.omnibuds.core.protocol

import com.omnibuds.core.capability.DeviceCapabilities
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.device.DeviceFingerprint
import com.omnibuds.core.session.DeviceState

/**
 * The core contract every protocol implementation shares, and deliberately the only one.
 *
 * **Why there are no feature operations here.** master section 9 lists `setAnc()`,
 * `setTransparency()`, `readEqualizer()`, `writeGestures()` and the rest as the
 * *conceptual* surface of a protocol, and then states the constraint that decides how it
 * is coded: "Do not force every vendor to implement every method. Capability negotiation
 * determines what is actually available" (master section 9; PROTO-ABST-001; ADR-P0-007;
 * Phase 1 prompt section 26: define the contract, do not create fake implementations that
 * claim these operations work).
 *
 * Putting those operations on this interface would make every one of them mandatory. A
 * vendor whose device has no equalizer would still have to implement `writeEqualizer()`,
 * and the only honest implementation available in Phase 1 is one that returns a failure no
 * test can distinguish from a stub — while the only implementation that *compiles
 * pleasantly* is the fake that prompt section 53 prohibits, which sets a field and calls
 * it hardware control. Either way the interface would be manufactured out of operations
 * this project has not discovered for any device.
 *
 * So optional behaviour lives in separate capability interfaces —
 * [FeatureReadSupport], [FeatureWriteSupport], [BatteryReportingSupport],
 * [FirmwareReportingSupport], [AudioStateReportingSupport] — which a protocol implementation
 * may or may not implement, and which a session discovers by type check plus negotiation
 * rather than by a method that must exist. Adding a feature to the vocabulary therefore
 * adds an interface, never edits this one, which is what keeps
 * `docs/phases/phase-0/architecture-governance.md` ARCH-PROTO-001 and master section 13
 * true: vendor-specific capability stays reachable without polluting the common surface.
 *
 * Phase 1 ships this contract with no implementations. Nothing here opens a channel, sends
 * a packet or touches a Bluetooth API: that is the platform layer's work behind
 * [com.omnibuds.core.transport.TransportContract] (ADR-P0-003, ADR-P0-008, Phase 1 prompt
 * sections 2, 32 and 51), and a `:core` implementation would necessarily be pretending.
 *
 * Ordering is a rule of the layers above, not of this interface: identity precedes state
 * reads and reads precede control (PROTO-ABST-004, PROTO-RESEARCH-006). Nothing here can
 * enforce it, so [com.omnibuds.core.state.ConnectionStateTransitions] and the state engine
 * own it; this interface only refuses to help anyone skip it by never exposing a raw
 * command path.
 */
interface EarbudProtocol {

    /**
     * The protocol family this implementation speaks, matching a
     * [ProtocolDefinition.protocolId].
     *
     * A name owned by the implementation, never chosen by the caller: the whole point of
     * [identify] is that the device decides which protocol governs it
     * (PROTO-ABST-005 forbids shortcutting identity by hard-coding a model into a
     * control path).
     */
    val protocolId: String

    /**
     * Decide whether this protocol governs the device described by [fingerprint].
     *
     * The result carries its evidence in [ProtocolIdentification.matchedBy], including the
     * `FALLBACK_UNKNOWN` answer, because "nothing matched" is a finding a session must be
     * able to record rather than a hole for a caller to fill with a guess
     * (PROTO-ID-002, PROTO-ID-003). A fingerprint whose fields are unobserved is a legal
     * input and yields an unknown answer, not an exception.
     */
    suspend fun identify(fingerprint: DeviceFingerprint): OperationOutcome<ProtocolIdentification>

    /**
     * Establish what this device can actually do.
     *
     * The result is a [DeviceCapabilities] snapshot in which unexamined features read
     * [com.omnibuds.core.state.CapabilityState.UNKNOWN] and only a conclusive negative
     * earns `UNSUPPORTED` (PROTO-CAP-004, master section 53). Implementing a
     * [FeatureWriteSupport] interface is not evidence and does not appear here — presence
     * of a vendor class says nothing about a device (PROTO-ABST-002).
     */
    suspend fun discoverCapabilities(): OperationOutcome<DeviceCapabilities>

    /**
     * Read current device state and return it as a newer [DeviceState].
     *
     * Takes the session's existing authoritative record and returns a replacement with a
     * higher revision, so that state flows transport/protocol -&gt; [DeviceState] -&gt;
     * application state and never sideways (Phase 1 prompt section 24). A field this
     * protocol cannot read must be carried through unchanged from [session], not zeroed,
     * not guessed, and not marked stale by a protocol that merely has no opinion
     * (specs.md section 2.2, ADR-P0-016). The caller merges the result with
     * [DeviceState.applyIfNewer], which is what makes a response arriving after a
     * disconnect harmless (Phase 1 prompt section 30).
     */
    suspend fun readState(session: DeviceState): OperationOutcome<DeviceState>
}
