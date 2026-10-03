package com.omnibuds.android.bluetooth.transport

import com.omnibuds.core.transport.GattCharacteristic
import com.omnibuds.core.transport.GattService

/**
 * The raw shape a Bluetooth transport mechanism reports, before any of it is read as a domain outcome.
 *
 * These types name no Android class, for the reason [com.omnibuds.android.bluetooth.connection.ConnectedDeviceHandle]
 * gives: the decisions Phase 6 makes about a channel — that a status of `GATT_SUCCESS` means the *operation*
 * succeeded and not that the device is controllable, that a write acknowledged at the link and not the
 * value, that a socket that could not be created is a different fact from one that was created and dropped
 * — are testable only if the mechanism can report them without a radio. The one file permitted to touch
 * `android.bluetooth.*` is the `System*TransportHandle` that fills these in; everything above is ordinary
 * Kotlin, unit-tested against a scripted handle.
 */

/**
 * One channel-level result the platform reported, in the platform's own terms.
 *
 * [ok] is "the stack said this particular operation completed", which is rung 2 of the persistence ladder
 * at best (delivery, not application — [com.omnibuds.core.transport.TransportResponse.acknowledged]).
 * [status] carries the raw integer (a GATT status code, or a socket error class ordinal) so the mapping
 * table can distinguish an auth failure from a timeout from a "device changed state under us", none of
 * which a bare boolean could.
 */
data class RawChannelResult(
    val ok: Boolean,
    val status: Int?,
)

/** A connect attempt's outcome, kept separate from an operation's because a failed connect has no bytes. */
sealed interface RawConnectResult {
    /** The platform confirmed the link. Only this case moves a transport to `CONNECTED`. */
    data object Established : RawConnectResult

    /** The platform refused or dropped the attempt, with the raw status that says which. */
    data class Refused(val status: Int?) : RawConnectResult

    /** The mechanism was present but has not answered — a connect callback that never fired. Not a failure. */
    data object Unanswered : RawConnectResult
}

/** What a discovery pass produced: the services, or the fact that discovery failed or is not possible. */
sealed interface RawDiscoveryResult {
    data class Reported(val services: List<GattService>) : RawDiscoveryResult
    data object Failed : RawDiscoveryResult
    data object Unanswered : RawDiscoveryResult
}

/** The bytes one read or one notification delivered; [Missing] is "the attribute had no value", not zero bytes. */
sealed interface RawReadResult {
    data class Value(val bytes: ByteArray) : RawReadResult {
        override fun equals(other: Any?): Boolean = this === other || (other is Value && bytes.contentEquals(other.bytes))
        override fun hashCode(): Int = bytes.contentHashCode()
    }
    data object Missing : RawReadResult
    data class Failed(val status: Int?) : RawReadResult
}

/**
 * The narrow seam over one GATT client, phrased without a framework type.
 *
 * Every member is a single platform action, and the handle is permitted to block on the platform's own
 * synchronous timeout where one exists; the serialisation, the lifecycle state machine and the error
 * mapping all live above it, in [AndroidGattTransport], so they are the parts a test exercises. A handle
 * answers about the channel it was opened for; there is no address in a signature here because the
 * framework device handle is acquired once, at open, inside the `System` implementation (SEC-ID-003 — an
 * address is never a parameter that could be logged at this level).
 */
interface GattTransportHandle {
    /**
     * Ask whether this channel *could* be used, without opening it.
     *
     * Reads adapter-on, permission-standing and API-presence facts and reports them as a
     * [RawChannelResult] (ok = usable, status = the refusal when not). It sends nothing and registers
     * nothing (PROTO-XPORT-008): this is the only thing a pre-open availability question may honestly do.
     */
    suspend fun probe(): RawChannelResult

    /** Ask the platform to connect. Returns once the connect callback has resolved, or Unanswered on a timeout. */
    suspend fun connect(): RawConnectResult

    /** Run service discovery on the connected channel. */
    suspend fun discover(): RawDiscoveryResult

    suspend fun read(target: GattCharacteristic): RawReadResult

    suspend fun write(target: GattCharacteristic, value: ByteArray, withResponse: Boolean): RawChannelResult

    /** Turn notifications on or off for one characteristic; ok means the descriptor write landed. */
    suspend fun setSubscribed(target: GattCharacteristic, enabled: Boolean): RawChannelResult

    /**
     * Enrol [sink] for value changes on [target]. A subscription a caller stops must remove its entry;
     * [cancel] releases the platform's characteristic-notification registration for that characteristic so
     * a dropped collector cannot leak it (prompt §14's "no leaked notification collectors").
     */
    fun subscribe(target: GattCharacteristic, sink: (RawReadResult) -> Unit)

    fun cancelSubscription(target: GattCharacteristic)

    /** Negotiate a larger ATT payload; returns the effective MTU the platform granted, or null if refused. */
    suspend fun requestMtu(requested: Int): Int?

    /** Close the GATT client. Idempotent; the platform throws on a double close, so the impl must not. */
    suspend fun close(): RawChannelResult
}
