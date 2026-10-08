package com.omnibuds.core.feature

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.config.ConfigurationValue

/**
 * What the feature engine currently believes about one feature's control state.
 *
 * This is the runtime *control* axis, and it is deliberately separate from every
 * other axis in the system: [com.omnibuds.core.state.CapabilityState] says what the
 * device implements, [com.omnibuds.core.state.ConnectionState] says whether the
 * link is up, [com.omnibuds.core.protocol.ProtocolState] says whether a protocol
 * session is ready, [com.omnibuds.core.transport.TransportState] says whether the
 * channel is open. None of them says "the last write to ANC is still in flight",
 * which is what this type says (Phase 9 prompt sections 9 and 10; the prompt's
 * section 2 rule 3 vocabulary — requested / device-confirmed / failed / unknown —
 * is reconciled onto these six states in ADR-P9-003).
 *
 * The load-bearing rule: **[Pending.requested] is never the device state.**
 * A requested value becomes device truth only when it arrives back as
 * [Confirmed] — either because the engine read it back after a write, or because
 * the device reported it on its own. A command acceptance is rung 2 of the
 * persistence ladder: it proves the channel carried the command, not that the
 * hardware applied it (Phase 7's [FeatureWriteSupport] contract).
 *
 * Every state that follows a concluded operation carries [lastConfirmed]: the most
 * recent value the device actually reported, or null when no value was ever
 * confirmed. It is *stale* knowledge — the UI may show it as "last known" but must
 * never present it as current. An unmeasured value stays unknown rather than
 * becoming a plausible number (ADR-P0-016): the field is null, not zero.
 */
sealed interface FeatureState {

    /** The feature this state is about. */
    val feature: FeatureId

    /**
     * The most recent device-confirmed value, or null if none was ever confirmed.
     * Stale by definition wherever it appears outside [Confirmed].
     */
    val lastConfirmed: ConfigurationValue?

    /** Nothing is currently established; any [lastConfirmed] is stale. */
    data class Unknown(
        override val feature: FeatureId,
        override val lastConfirmed: ConfigurationValue? = null,
    ) : FeatureState

    /**
     * The capability is established and the feature may be operated on, but no
     * value has been confirmed yet in this session.
     */
    data class Available(
        override val feature: FeatureId,
        override val lastConfirmed: ConfigurationValue? = null,
    ) : FeatureState

    /**
     * A write is in flight. [requested] is what was asked for — it is explicitly
     * *not* the device state, and nothing may read it as though it were.
     */
    data class Pending(
        override val feature: FeatureId,
        val requested: ConfigurationValue,
        override val lastConfirmed: ConfigurationValue?,
        val operationId: String,
    ) : FeatureState {
        init {
            require(operationId.isNotBlank()) { "a pending state must name the operation that caused it" }
        }
    }

    /** The device reported [value]; this is the authoritative reading. */
    data class Confirmed(
        override val feature: FeatureId,
        val value: ConfigurationValue,
    ) : FeatureState {
        override val lastConfirmed: ConfigurationValue = value
    }

    /**
     * The last operation did not achieve its goal. The device state is whatever
     * [lastConfirmed] says — possibly nothing — and [error] explains the failure
     * with its retry behaviour attached.
     */
    data class Failed(
        override val feature: FeatureId,
        val error: OmniBudsError,
        override val lastConfirmed: ConfigurationValue?,
    ) : FeatureState

    /**
     * Supported but not usable right now. [reason] is diagnostic text naming why
     * (e.g. "reported unavailable by discovery"); it carries no device identifiers
     * (SEC-LOG-002).
     */
    data class Unavailable(
        override val feature: FeatureId,
        val reason: String,
        override val lastConfirmed: ConfigurationValue? = null,
    ) : FeatureState {
        init {
            require(reason.isNotBlank()) { "an unavailable state must say why" }
        }
    }
}

/**
 * The legal moves of the control state machine, stated as a table rather than
 * scattered through the engine.
 *
 * The table is permissive where the engine's *validator* is strict: whether a
 * write may be *attempted* is decided by capability gating, access, dependencies
 * and conflicts ([FeatureValidator]), while this table decides whether a state
 * *transition* is coherent. A transition the validator would never produce — say
 * [FeatureState.Unknown] to [FeatureState.Pending] — is still refused here, so a
 * future caller cannot skip the validator and drive the repository directly.
 *
 * A `null` source means "the repository has never heard of this feature": only the
 * seeding states may be written, which is what
 * [FeatureEngine.adoptSnapshot] uses after discovery.
 */
object FeatureStateTransitions {

    /**
     * Whether the repository may move a feature from [from] to [to].
     *
     * Deterministic and total over the six states: every pair not listed is
     * illegal, and illegal moves are refused by [FeatureStateRepository] rather
     * than documented away.
     */
    fun isLegal(from: FeatureState?, to: FeatureState): Boolean {
        if (from != null && from.feature != to.feature) return false
        if (from == null) {
            return to is FeatureState.Unknown ||
                to is FeatureState.Available ||
                to is FeatureState.Unavailable
        }
        return when (from) {
            is FeatureState.Unknown -> to is FeatureState.Available ||
                to is FeatureState.Confirmed ||
                to is FeatureState.Failed ||
                to is FeatureState.Unavailable ||
                // Re-asserting unknown is idempotent: session invalidation moves
                // every tracked feature to Unknown, including ones already there.
                to is FeatureState.Unknown

            is FeatureState.Available -> to is FeatureState.Pending ||
                to is FeatureState.Confirmed ||
                to is FeatureState.Failed ||
                to is FeatureState.Unknown ||
                to is FeatureState.Unavailable

            is FeatureState.Pending -> to is FeatureState.Confirmed ||
                to is FeatureState.Failed ||
                to is FeatureState.Unknown ||
                to is FeatureState.Available ||
                to is FeatureState.Unavailable

            is FeatureState.Confirmed -> to is FeatureState.Pending ||
                to is FeatureState.Confirmed ||
                to is FeatureState.Failed ||
                to is FeatureState.Unknown ||
                to is FeatureState.Available ||
                to is FeatureState.Unavailable

            is FeatureState.Failed -> to is FeatureState.Pending ||
                to is FeatureState.Available ||
                to is FeatureState.Confirmed ||
                to is FeatureState.Unknown ||
                to is FeatureState.Unavailable

            is FeatureState.Unavailable -> to is FeatureState.Available ||
                to is FeatureState.Confirmed ||
                to is FeatureState.Failed ||
                to is FeatureState.Unknown
        }
    }
}
