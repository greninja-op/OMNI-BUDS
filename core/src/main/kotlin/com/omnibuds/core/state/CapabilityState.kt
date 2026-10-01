package com.omnibuds.core.state

/**
 * What OmniBuds is entitled to believe about one capability of one device.
 *
 * The distinction that matters most is between [UNKNOWN] and [UNSUPPORTED]:
 * "not discovered" and "not verified" are not evidence of absence, and collapsing
 * them either direction is the failure this project exists to avoid (ADR-P0-004,
 * ADR-P0-016, master section 53).
 *
 * Declaration order is meaningful: it is an evidence ladder, and a capability may
 * only move up on evidence of the matching tier (testing-governance.md).
 */
enum class CapabilityState {
    /** Not discovered, or discovered but not determinable from here. */
    UNKNOWN,

    /** Positively established that this device does not implement the feature. */
    UNSUPPORTED,

    /** Reported by the device but not user-settable. */
    READ_ONLY,

    /** Settable, but the setting does not survive a reconnect. */
    SUPPORTED_VOLATILE,

    /** Settable and read back as applied; durability not yet proven across reconnect. */
    SUPPORTED_PERSISTENT,

    /** Settable and proven to survive a real disconnect/reconnect cycle. */
    PERSISTENCE_VERIFIED,
}

/** Whether OmniBuds may offer this capability as an actual control. */
fun CapabilityState.isControllable(): Boolean = when (this) {
    CapabilityState.SUPPORTED_VOLATILE,
    CapabilityState.SUPPORTED_PERSISTENT,
    CapabilityState.PERSISTENCE_VERIFIED,
    -> true

    CapabilityState.UNKNOWN,
    CapabilityState.UNSUPPORTED,
    CapabilityState.READ_ONLY,
    -> false
}

/** Whether anything at all is known about the capability. */
fun CapabilityState.isEstablished(): Boolean = this != CapabilityState.UNKNOWN

/** Whether the current value may be displayed as a device-reported reading. */
fun CapabilityState.isReadable(): Boolean =
    this != CapabilityState.UNKNOWN && this != CapabilityState.UNSUPPORTED
