package com.omnibuds.core.platform

/**
 * The state of the phone's own Bluetooth adapter, as far as the platform will report it.
 *
 * This describes the *host device*, not any headset. Phase 2 prompt section 5.2 requires these
 * facts be distinguished rather than collapsed, because "no adapter", "adapter off" and "we are
 * not allowed to look" lead to completely different user-facing behaviour: the first two are the
 * phone's state, the third is a permission outcome, and conflating them produces a app that tells
 * the user their Bluetooth is broken when the app was simply refused.
 *
 * [UNKNOWN] is a real and expected value: a read that failed, a state that has not been sampled
 * yet, or a platform that will not answer without a permission we do not hold. It is never
 * rendered as "off" (ADR-P0-016).
 */
enum class BluetoothAdapterState {
    /** Not sampled, unreadable, or refused. Not evidence that Bluetooth is unavailable. */
    UNKNOWN,

    /** The device has no usable Bluetooth adapter, or the platform could not obtain one. */
    UNAVAILABLE,

    /** An adapter exists and is switched off. */
    DISABLED,

    /** A transition to enabled is in progress. */
    ENABLING,

    /** An adapter exists and is on. */
    ENABLED,

    /** A transition to disabled is in progress. */
    DISABLING,
}

/** Whether adapters in this state can be used for any inspection at all. */
fun BluetoothAdapterState.isUsable(): Boolean = this == BluetoothAdapterState.ENABLED

/** True only when an adapter exists and is provably switched off - never for [UNKNOWN]. */
fun BluetoothAdapterState.isProvablyDisabled(): Boolean = this == BluetoothAdapterState.DISABLED

/** True when the state tells us nothing, so no conclusion about Bluetooth may be drawn. */
fun BluetoothAdapterState.isIndeterminate(): Boolean =
    this == BluetoothAdapterState.UNKNOWN || this == BluetoothAdapterState.UNAVAILABLE
