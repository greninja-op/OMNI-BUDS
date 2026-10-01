package com.omnibuds.core.capability

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.state.CapabilityState
import com.omnibuds.core.state.VerificationLevel
import com.omnibuds.core.state.atLeast
import com.omnibuds.core.state.isControllable
import com.omnibuds.core.common.TransportKind

/**
 * Everything OmniBuds is entitled to believe about one feature of one device.
 *
 * A capability is never `supported = true` (PROTO-CAP-001, master section 10). It is a
 * rung on the [CapabilityState] evidence ladder, plus the affordances, transport
 * binding, protocol binding, connection requirement and [VerificationLevel] that rung
 * was established with.
 *
 * **The affordances follow the state and cannot contradict it.** [state] decides what
 * [readable] and [writable] are allowed to say, and the `init` block refuses anything
 * else, so a record that claims a control no device was shown to have — or a reading
 * nothing supports — cannot be constructed at all. `UNKNOWN` means *nothing is
 * established*, which is a different statement from `UNSUPPORTED` (master section 53),
 * and neither may carry affordances.
 *
 * The two persistence states make the strongest claims, so their evidence floor is
 * enforced here as well: a durability claim that was never hardware-verified is
 * unrepresentable. Lower rungs are deliberately uncapped by construction — deciding
 * whether a `READ_ONLY` or `SUPPORTED_VOLATILE` reading earned its tier is the
 * discovery engine's job against protocol-governance sections 5, 6 and 7, not something
 * a frozen value object can judge.
 *
 * [protocolId] is nullable and `null` is an ordinary answer, not a failure: the protocol
 * governing a device is frequently not identified yet, and that belongs in the protocol
 * database as an open question rather than in an invented id. [transport] similarly
 * allows [TransportKind.UNKNOWN], which means "not determined here" and never "there is
 * no transport".
 *
 * This is a record of observations. It reads nothing, writes nothing and asserts no
 * hardware behaviour of its own (Phase 1 prompt sections 13, 51 and 53).
 */
data class FeatureCapability(
    /** Which feature this record is about; identity, never a label or a brand branch. */
    val feature: FeatureId,
    /** The highest rung the evidence supports, or [CapabilityState.UNKNOWN] when none does. */
    val state: CapabilityState,
    /** Whether a value may be displayed as a reading the device actually reported. */
    val readable: Boolean,
    /** Whether a write path has been established for this feature on this device. */
    val writable: Boolean,
    /** The channel this record was established over; [TransportKind.UNKNOWN] when undetermined. */
    val transport: TransportKind,
    /**
     * The protocol this record was established under, or `null` while the governing
     * protocol is unidentified. Blank strings are refused: an absent binding and a
     * meaningless one must not be two different ways of saying the same thing.
     */
    val protocolId: String?,
    /** Whether the record only means something while a session is live. */
    val requiresConnection: Boolean,
    /** How strongly this record's claim is supported; never reported above its evidence. */
    val verification: VerificationLevel,
) {

    init {
        when (state) {
            CapabilityState.UNKNOWN -> require(!readable && !writable) {
                "UNKNOWN $feature establishes nothing, so readable=$readable and writable=$writable " +
                    "are not permitted"
            }

            CapabilityState.UNSUPPORTED -> require(!readable && !writable) {
                "UNSUPPORTED $feature is a positive absence, and cannot also offer affordances " +
                    "(readable=$readable, writable=$writable)"
            }

            CapabilityState.READ_ONLY -> require(readable && !writable) {
                "READ_ONLY $feature must be readable and must not be writable, " +
                    "got readable=$readable, writable=$writable"
            }

            CapabilityState.SUPPORTED_VOLATILE,
            CapabilityState.SUPPORTED_PERSISTENT,
            CapabilityState.PERSISTENCE_VERIFIED,
            -> require(readable && writable) {
                "$state $feature is offered as a control, so it must be both readable and writable, " +
                    "got readable=$readable, writable=$writable"
            }
        }

        if (state == CapabilityState.SUPPORTED_PERSISTENT) {
            require(verification.atLeast(VerificationLevel.HARDWARE_VERIFIED)) {
                "SUPPORTED_PERSISTENT $feature asserts durability that has not been observed surviving, " +
                    "and needs at least ${VerificationLevel.HARDWARE_VERIFIED} evidence, not $verification"
            }
        }

        if (state == CapabilityState.PERSISTENCE_VERIFIED) {
            require(verification == VerificationLevel.PERSISTENCE_VERIFIED) {
                "PERSISTENCE_VERIFIED $feature claims survival of a real disconnect and reconnect, which is " +
                    "exactly what ${VerificationLevel.PERSISTENCE_VERIFIED} verification means; " +
                    "$verification cannot support it"
            }
        }

        require(protocolId == null || protocolId.isNotBlank()) {
            "protocolId for $feature is blank; pass null when the governing protocol is not identified"
        }
    }

    /**
     * Whether a control for this feature may be offered at all.
     *
     * Derived from [state] through the kernel's own rule rather than from [writable],
     * because "the code can send a write" and "this device is entitled to show a
     * control" are different claims (PROTO-CAP-005, master section 11).
     */
    val isControllable: Boolean
        get() = state.isControllable()

    companion object {
        /**
         * Nothing is known about [feature] on this device.
         *
         * The honest default for "not discovered yet": no affordances, no protocol, no
         * transport, no support claim. Absence from a [DeviceCapabilities] container
         * means the same thing as this record, and this record is how the discovery
         * engine says "still unknown" out loud (master section 53).
         */
        fun unknown(feature: FeatureId): FeatureCapability = FeatureCapability(
            feature = feature,
            state = CapabilityState.UNKNOWN,
            readable = false,
            writable = false,
            transport = TransportKind.UNKNOWN,
            protocolId = null,
            requiresConnection = false,
            verification = VerificationLevel.INFERRED,
        )

        /**
         * Absence positively established for [feature] on this device.
         *
         * Only a conclusive negative from a protocol known to govern the device earns
         * this record (PROTO-CAP-001); a failed or missing read earns [unknown] instead.
         */
        fun unsupported(feature: FeatureId, protocolId: String, transport: TransportKind): FeatureCapability =
            FeatureCapability(
                feature = feature,
                state = CapabilityState.UNSUPPORTED,
                readable = false,
                writable = false,
                transport = transport,
                protocolId = protocolId,
                requiresConnection = false,
                verification = VerificationLevel.HARDWARE_VERIFIED,
            )
    }
}
