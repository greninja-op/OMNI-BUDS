package com.omnibuds.android.bluetooth.permission

import com.omnibuds.core.platform.BluetoothPermission
import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers which permissions this process has already asked the user about.
 *
 * The platform cannot tell the difference between "never asked" and "asked and refused":
 * `checkSelfPermission` returns the same denial for both. That distinction is the whole content of
 * [com.omnibuds.core.platform.PermissionState.NOT_REQUESTED], and Phase 2 prompt section 5.3 forbids
 * repeatedly triggering prompts - which requires knowing that one was already triggered. So the app
 * keeps the record itself, in memory, for the life of the process.
 *
 * It is deliberately not persisted. A stored "we already asked" would survive an OS permission reset
 * and keep the app from asking again, turning a temporary denial into a permanent one - the exact
 * false claim ADR-P2-012 refuses.
 */
fun interface PermissionRequestLedger {
    fun wasRequested(permission: BluetoothPermission): Boolean
}

/** The production ledger: in-memory, process-scoped, safe to read from any thread. */
class InMemoryPermissionRequestLedger : PermissionRequestLedger {
    private val requested = ConcurrentHashMap.newKeySet<BluetoothPermission>()

    /** Records that a request for [permission] was issued. Called by whoever owns the prompt. */
    fun record(permission: BluetoothPermission) {
        requested.add(permission)
    }

    override fun wasRequested(permission: BluetoothPermission): Boolean = permission in requested
}
