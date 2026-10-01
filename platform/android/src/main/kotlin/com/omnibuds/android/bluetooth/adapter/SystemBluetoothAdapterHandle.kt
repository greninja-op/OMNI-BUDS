package com.omnibuds.android.bluetooth.adapter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.omnibuds.android.bluetooth.capability.ApiLevelProvider
import com.omnibuds.android.bluetooth.mapping.RAW_STATE_UNREADABLE
import com.omnibuds.core.platform.PlatformRegistration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The one implementation in this codebase that touches Android's Bluetooth adapter.
 *
 * `getSystemService(BluetoothManager::class.java)` is the documented route to a `BluetoothAdapter`
 * from API 18 up (`BluetoothManager.getAdapter()` returns null on hardware with no Bluetooth radio
 * at all, which is a real answer rather than a crash - Phase 2 prompt section 5.2). Reading
 * `getState()` needs no permission for an app targeting API 31 or above, which is why
 * `AndroidManifest.xml` declares none (ADR-P2-011, research Q1).
 *
 * Only the platform calls that answer those two questions appear here, and each is the smallest thing
 * that answers it: `getState()` for a single read, and one `ACTION_STATE_CHANGED` receiver for
 * changes. There is no
 * polling: a repeating read would be the hidden background work Phase 2 prompt section 5.8 forbids,
 * and the broadcast already exists.
 */
class SystemBluetoothAdapterHandle(
    private val context: Context,
    private val apiLevel: ApiLevelProvider,
) : BluetoothAdapterHandle {

    private val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter

    override val adapterPresent: Boolean
        get() = adapter != null

    /**
     * The platform's own state integer, or null when there is no adapter to ask.
     *
     * Deliberately not converted to a domain value here: the mapping lives in one place, and a null
     * from this method means "there was nothing to read", which the source resolves to
     * [com.omnibuds.core.platform.BluetoothAdapterState.UNAVAILABLE] rather than guessing off.
     */
    override fun readRawState(): Int? = adapter?.state

    override fun openStateChanges(emit: (Int) -> Unit): PlatformRegistration {
        val receiver = StateChangeReceiver(emit)
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        register(receiver, filter)
        return ReceiverRegistration(receiver)
    }

    /**
     * Registers so the Bluetooth stack can reach this receiver.
     *
     * Phase 2 chose `RECEIVER_NOT_EXPORTED` here on the reasoning that `ACTION_STATE_CHANGED` is a
     * protected system broadcast, and lint's own advice was quoted back as licence. Both readings were
     * half right, and ADR-P3-013 is the correction. Being *protected* - which this action is, listed in
     * AOSP's `core/res/AndroidManifest.xml` beside the ACL, bond and profile connection-state actions -
     * governs who may **send** it. The export flag governs who may **receive** it, and the platform's
     * receiver guidance is explicit that a not-exported receiver "is able to receive some system
     * broadcasts and broadcasts from your app, but not broadcasts from the highly privileged apps", with
     * Bluetooth named as one of those apps. A receiver that cannot be reached would report an adapter
     * that never changes, and a silent empty observation is the failure this project refuses everywhere
     * else; it is not allowed to smuggle itself in through a registration flag.
     *
     * Exporting costs almost nothing here, and the reason is stated rather than assumed: every action in
     * this filter is protected, so no third-party app can forge one and a sender would need the
     * Bluetooth stack's own privileges.
     *
     * The two version facts are still kept apart. The *requirement* to declare an export decision applies
     * to apps targeting API 34 or above; the *overload* carrying the flag exists from API 33; so the
     * branch keys on the device level, because calling the overload is what would throw on an older
     * phone. Below 33 there is no flag to pass, so nothing is.
     *
     * Both lint complaints on this body are the guarded pattern's own shadow, suppressed rather than
     * answered with an `androidx.core` dependency the phase has no reason to take: `InlinedApi` fires on
     * a constant that is only read inside the `>= 33` branch, and `UnspecifiedRegisterReceiverFlag`
     * fires on the flag-less branch because lint cannot inspect which actions the filter holds.
     */
    @SuppressLint("InlinedApi", "UnspecifiedRegisterReceiverFlag")
    private fun register(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (apiLevel.apiLevel() >= RECEIVER_FLAG_API_LEVEL) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    private class StateChangeReceiver(private val emit: (Int) -> Unit) : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            // A broadcast carrying no adapter-state extra reads as RAW_STATE_UNREADABLE and therefore
            // as UNKNOWN. It is not STATE_OFF, and treating "the platform said something changed but
            // not what" as "off" is the fabricated-failure class this boundary exists to stop.
            emit(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, RAW_STATE_UNREADABLE))
        }
    }

    /**
     * Unregisters exactly once.
     *
     * Idempotence is a contract requirement (`PlatformRegistration.dispose` may be called from a
     * cancellation path that runs twice), and unregistering a receiver the platform does not know
     * throws, so the guard is the difference between a clean teardown and a crash on the way out.
     * A `SecurityException` here is rethrown rather than swallowed: a teardown that failed has to
     * surface, because `AdapterStateObserver` records it as `teardownProblem` (ADR-P2-007).
     */
    private inner class ReceiverRegistration(private val receiver: BroadcastReceiver) : PlatformRegistration {
        private val registered = AtomicBoolean(true)

        override val isActive: Boolean
            get() = registered.get()

        override suspend fun dispose() {
            if (registered.compareAndSet(true, false)) {
                context.unregisterReceiver(receiver)
            }
        }
    }

    private companion object {
        const val RECEIVER_FLAG_API_LEVEL = 33
    }
}
