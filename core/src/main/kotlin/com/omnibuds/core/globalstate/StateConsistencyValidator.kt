package com.omnibuds.core.globalstate

/**
 * State-consistency validator.
 *
 * Phase 24 (OB-P24-REQ-017): detects impossible or suspicious combinations.
 * Reports inconsistencies; applies only documented deterministic corrections;
 * never silently rewrites evidence.
 */
object StateConsistencyValidator {

    /**
     * Validate a snapshot. Returns the list of violations found.
     */
    fun validate(state: GlobalDeviceState): List<ConsistencyViolation> {
        val violations = mutableListOf<ConsistencyViolation>()

        // A writable feature requires a compatible protocol.
        if (state.protocol is ProtocolState.Incompatible &&
            state.features.observed.isNotEmpty()
        ) {
            violations.add(
                ConsistencyViolation(
                    code = "PROTOCOL_INCOMPATIBLE_WITH_OBSERVED_STATE",
                    reason = "device-observed feature state exists despite incompatible protocol",
                ),
            )
        }

        // Ambiguous identity + model-specific authorization is suspicious.
        if (state.identity is IdentityState.Ambiguous &&
            state.protocol is ProtocolState.Resolved
        ) {
            violations.add(
                ConsistencyViolation(
                    code = "AMBIGUOUS_IDENTITY_WITH_RESOLVED_PROTOCOL",
                    reason = "protocol resolved while device identity is ambiguous",
                ),
            )
        }

        // Stale observations must not be presented as current.
        val staleAsCurrent = state.features.observed.values.filter {
            it.freshness == Freshness.STALE || it.freshness == Freshness.EXPIRED
        }
        if (staleAsCurrent.isNotEmpty()) {
            violations.add(
                ConsistencyViolation(
                    code = "STALE_OBSERVATION",
                    reason = "${staleAsCurrent.size} observed values are stale or expired",
                ),
            )
        }

        // Desired configuration must not be represented as observed.
        // (Structural check: desired and observed are separate maps by
        // construction; this validates no key was copied without provenance.)
        for ((id, desired) in state.features.desired) {
            val observed = state.features.observed[id]
            if (observed != null && observed.provenance.sourceId == "user-preference") {
                violations.add(
                    ConsistencyViolation(
                        code = "PREFERENCE_AS_OBSERVATION",
                        reason = "feature $id has a user preference recorded as device observation",
                    ),
                )
            }
        }

        return violations
    }
}

/** A single consistency violation. */
data class ConsistencyViolation(
    val code: String,
    val reason: String,
)
