package com.omnibuds.core.transport

/**
 * Where to connect an RFCOMM socket, as a caller supplies it.
 *
 * An RFCOMM channel is addressed by an SDP service UUID, a specific channel number, or both, and which one
 * applies is a fact about a *device's* published service records — never a value Phase 6 may hard-code
 * (PROTO-NOMAGIC-002). This type is therefore empty of any default: it carries whichever of
 * [serviceUuid] and [channel] the caller resolved from evidence, and the transport connects to exactly
 * what it is handed.
 *
 * [serviceUuid] and [channel] are both nullable but not *both* null, enforced below: an RFCOMM connect with
 * no address is not "connect to the default", it is a caller that knows nothing and must say so with a
 * refusal rather than open a socket on a guess. A `null` field here means "this part was not used to pick
 * the channel", never "the device has no such thing."
 */
data class RfcommEndpoint(
    /** The SDP service record to connect against, or null when the caller targets a raw channel. */
    val serviceUuid: GattUuid? = null,

    /** An explicit RFCOMM channel number, or null when resolution is by [serviceUuid]. */
    val channel: Int? = null,
) {
    init {
        require(serviceUuid != null || channel != null) {
            "an RFCOMM endpoint must name a service UUID, a channel, or both; naming neither is not " +
                "a default, it is no address at all (PROTO-NOMAGIC-002)"
        }
        if (channel != null) {
            require(channel in CHANNEL_MIN..CHANNEL_MAX) {
                "an RFCOMM channel is $CHANNEL_MIN..$CHANNEL_MAX, was $channel"
            }
        }
    }

    companion object {
        private const val CHANNEL_MIN = 1
        private const val CHANNEL_MAX = 30
    }
}
