package com.omnibuds.android.bluetooth.transport

import com.omnibuds.core.common.OmniBudsErrorCategory

/**
 * The one place a raw Bluetooth status integer becomes a domain error category, phrased without a
 * framework import.
 *
 * Prompt §13's rule — "do not expose raw Android exceptions through core" and "distinguish retryable from
 * non-retryable" — is honoured here rather than at each call site: the mapping is a table, so it can be
 * unit-tested against the numbers the stack actually returns (ADR-P2-008's discipline of keeping a
 * platform integer and a domain decision in named, checkable places). The categories are Phase 2's
 * existing ones with their own retry classes attached (ADR-P6-006); this table decides *which* category,
 * never *whether to retry*, which stays the category's property read downstream.
 *
 * The integer constants are the framework's `BluetoothGatt.GATT_*` values transcribed by number, so this
 * file may live above the seam. Only the `System*Handle` files import the framework; a drift between a
 * number here and the constant it stands for is caught by the mapping tests, which assert the specific
 * statuses the reference documents (GATT read/write failures, authentication, connection-congestion
 * timeout) map where each belongs.
 */
internal object TransportErrorMapping {
    // android.bluetooth.BluetoothGatt status codes, transcribed (their values are stable across API levels).
    private const val GATT_SUCCESS = 0
    private const val GATT_INVALID_HANDLE = 1
    private const val GATT_READ_NOT_PERMITTED = 2
    private const val GATT_WRITE_NOT_PERMITTED = 3
    private const val GATT_INSUFFICIENT_AUTHENTICATION = 5
    private const val GATT_REQUEST_NOT_SUPPORTED = 6
    private const val GATT_INSUFFICIENT_ENCRYPTION = 15
    private const val GATT_CONNECTION_CONGESTED = 14
    private const val GATT_FAILURE = 0x85
    private const val GATT_TIMEOUT = 0x08

    /** The success value; kept public so the transport can test `status == OK_STATUS` without a magic number. */
    const val OK_STATUS = GATT_SUCCESS

    /** Map a GATT operation's status to the category that says what went wrong, or null when it succeeded. */
    fun gattCategory(status: Int): OmniBudsErrorCategory? = when (status) {
        GATT_SUCCESS -> null
        GATT_INSUFFICIENT_AUTHENTICATION, GATT_INSUFFICIENT_ENCRYPTION -> OmniBudsErrorCategory.PERMISSION_DENIED
        GATT_READ_NOT_PERMITTED, GATT_WRITE_NOT_PERMITTED -> OmniBudsErrorCategory.WRITE_REJECTED
        GATT_INVALID_HANDLE, GATT_REQUEST_NOT_SUPPORTED -> OmniBudsErrorCategory.INVALID_STATE
        GATT_CONNECTION_CONGESTED -> OmniBudsErrorCategory.RESOURCE_UNAVAILABLE
        GATT_TIMEOUT -> OmniBudsErrorCategory.TIMEOUT
        GATT_FAILURE -> OmniBudsErrorCategory.GATT_FAILURE
        else -> OmniBudsErrorCategory.GATT_FAILURE
    }

    /** A connect attempt that produced no link is a transport unavailability unless a status says otherwise. */
    fun connectCategory(status: Int?): OmniBudsErrorCategory =
        status?.let(::gattCategory) ?: OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE
}
