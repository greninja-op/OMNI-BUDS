package com.omnibuds.core.protocol

import com.omnibuds.core.common.OmniBudsError

/**
 * A device-originated edge a protocol session reports, as opposed to the state a caller asked for.
 *
 * The whole set exists to enforce prompt §13's central separation — *no conflation of requested state with
 * device-confirmed state*. When a caller issues a command the *requested* value belongs to the
 * [ProtocolCommand] and its [ProtocolResponse]; what the device subsequently *says* it is arrives here, as a
 * [StateChanged] the device caused. A consumer that treated an ack as a state change is the failure this
 * split refuses: an acknowledged write means the channel accepted bytes, not that any setting took effect
 * (`acknowledged` is delivery-level, [ProtocolResponse]).
 *
 * These are edges on a bounded `Flow` (declared on [ProtocolSession.events]), not a store. The session's
 * [ProtocolState] `StateFlow` is the authoritative "where is this session"; an event says *that a thing
 * happened*, and a consumer that was not attached misses the edge and reads the state flow instead — the
 * same notification-not-history discipline Phase 4's `DeviceSessionEvent` uses.
 */
sealed interface ProtocolEvent {
    /** The device's own value for [feature] changed, as the device reported it (never the requested value). */
    data class StateChanged(val feature: String, val observed: String, val atEpochMillis: Long?) : ProtocolEvent

    /** A command's answer arrived and matched a [ProtocolCommand.correlationId]. Delivery, not effect. */
    data class CommandCompleted(
        val commandId: String,
        val correlationId: String,
        val acknowledged: Boolean,
        val atEpochMillis: Long?,
    ) : ProtocolEvent

    /** The underlying transport dropped; the session moves to [ProtocolState.DEGRADED] or [ProtocolState.FAILED]. */
    data class TransportDisconnected(val reason: String?, val atEpochMillis: Long?) : ProtocolEvent

    /** The protocol raised a structured fault; the error carries the category, never a raw exception. */
    data class ProtocolError(val error: OmniBudsError, val atEpochMillis: Long?) : ProtocolEvent

    /** `initialize()` finished; the transition to READY/DEGRADED is reported separately via the state flow. */
    data class InitializationCompleted(val succeeded: Boolean, val atEpochMillis: Long?) : ProtocolEvent

    /** The device announced that its own capability set changed (e.g. a feature appeared or vanished). */
    data class CapabilityUpdate(val feature: String, val supported: Boolean, val atEpochMillis: Long?) : ProtocolEvent
}
