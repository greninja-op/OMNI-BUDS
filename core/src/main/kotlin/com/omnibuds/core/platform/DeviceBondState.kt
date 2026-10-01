package com.omnibuds.core.platform

/**
 * Whether Android has a bond with this device - a separate fact from whether it is connected.
 *
 * Phase 3 prompt section 6 makes the distinction mandatory, and it is not a nuance: a paired device
 * that is sitting in a drawer at home is `BONDED` and `DISCONNECTED` simultaneously, and presenting
 * either one as the other is how an app ends up showing a device as "connected" when the user's
 * audio is coming from the phone's speaker. Collapsing the two into a single enum would require a
 * compound member, and a compound member cannot say which of its two halves just changed
 * (ADR-P3-001).
 *
 * Bond *management* is absent from this phase by design: prompt sections 16 and 17 forbid initiating
 * pairing, and reading a bond state is not the same operation as creating one.
 */
enum class DeviceBondState {
    /** No report was obtained. Not "unpaired" (ADR-P0-016). */
    UNKNOWN,

    /** The device is bonded with the phone and was seen in the bond list. */
    BONDED,

    /** A bond is in progress. Read-only here: Phase 3 never starts one. */
    BONDING,

    /** The platform positively reports no bond exists. */
    NONE,
}

/** True only on a positive report of an existing bond; [UNKNOWN] is deliberately not enough. */
fun DeviceBondState.isBonded(): Boolean = this == DeviceBondState.BONDED

/** True when the bond state is known either way, which is what an "unpaired" affordance needs. */
fun DeviceBondState.isKnown(): Boolean = this != DeviceBondState.UNKNOWN
