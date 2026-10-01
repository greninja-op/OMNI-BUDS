package com.omnibuds.android.bluetooth.adapter

import com.omnibuds.core.platform.PlatformRegistration

/**
 * The narrow seam between the adapter-state source and the Android framework.
 *
 * It speaks in the platform's own raw integers, not in `:core`'s [com.omnibuds.core.platform.BluetoothAdapterState],
 * on purpose: translation happens in exactly one place (`bluetooth/mapping`), so a later Android
 * version that changes what a number means breaks one function rather than every consumer that
 * compared states. Everything above this interface is testable on a JVM without a radio, which is how
 * Phase 2 prompt section 9's adapter tests run at all.
 *
 * Three members, because three questions are authorised: is there an adapter, what state is it in,
 * and will it announce changes. Adding a fourth - enabling the adapter, listing bonded devices,
 * opening a transport - would put an unauthorised capability behind a name that sounds innocuous
 * (Phase 2 prompt section 6).
 */
interface BluetoothAdapterHandle {
    /** Whether this phone exposes a Bluetooth adapter at all. False is a real answer, not an error. */
    val adapterPresent: Boolean

    /** The platform's adapter state integer, or null when there was nothing to ask. */
    fun readRawState(): Int?

    /**
     * Registers for adapter-state broadcasts and calls [emit] with each raw state the platform sends.
     *
     * The returned registration must be disposed by the caller; implementations are responsible for
     * making disposal idempotent, because the observer's teardown can run on a cancellation path that
     * has already disposed it.
     */
    fun openStateChanges(emit: (Int) -> Unit): PlatformRegistration
}
