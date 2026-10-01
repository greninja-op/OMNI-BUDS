package com.omnibuds.core.protocol

import com.omnibuds.core.state.VerificationLevel

/**
 * One operation a protocol knows how to perform, recorded as structure rather than as
 * bytes.
 *
 * This is the element PROTO-NOMAGIC-001 and master section 52 demand: a command is a
 * named definition carrying its meaning, its effect class and its bounds, and the hex
 * literals live in the protocol definition that references this record — never scattered
 * through call sites. [id] names the operation's meaning, so `readBatteryStatus` is an
 * id and `send0x0A` is a defect (specs.md section 1.5).
 *
 * **The retry shape is enforced by construction, not by convention.** [maxReadAttempts]
 * is required for [EffectClass.READ] and forbidden for both write classes. That is the
 * read-versus-write asymmetry of specs.md section 4 made unrepresentable to get wrong:
 * a definition that cannot state a read budget has no bounded retry policy, and a
 * definition that tries to state a write budget cannot be built at all. The ceiling is
 * [MAX_READ_ATTEMPTS] so no protocol record can produce an unbounded read loop, and the
 * floor is [MIN_READ_ATTEMPTS] because a budget below one attempt is a mistake rather
 * than a policy. These bounds match `ProtocolConfiguration`, which uses the same 1..5
 * range for the same reason.
 *
 * [timeoutMillis] is declared per operation because specs.md section 5.4 requires every
 * protocol operation to declare a timeout; null means the definition states no bound and
 * leaves the caller's bound in force — never "wait forever", which is what a default of
 * zero would silently mean (ADR-P0-016).
 *
 * [confidence] is how well this command is understood, on the evidence ladder
 * (PROTO-VERIFY-001). It is the narrowest gate in this type and the one most likely to
 * be ignored: a command at [VerificationLevel.INFERRED] may be parsed and recorded —
 * inference is legitimate research data (SEC-RES-002, SEC-RES-003) — but it **must not
 * be exposed as a user-visible control and must not be sent to a user's device**
 * (SEC-RES-004). A level is also meaningless without its subject, so this one belongs to
 * the command, not to the protocol (D3, PROTO-VERIFY-005).
 */
data class CommandDefinition(
    /** Symbolic operation identity, stable and unique within one protocol definition. */
    val id: String,

    /** Human-facing label, presentation only; never a lookup key. */
    val displayName: String,

    /** Whether this operation reads or changes the device, which decides retryability. */
    val effectClass: EffectClass,

    /** Per-operation wait bound in milliseconds, or null when the definition states none. */
    val timeoutMillis: Long?,

    /**
     * Ceiling on repeated attempts for one logical read, or null for a write.
     *
     * Non-null and within [MIN_READ_ATTEMPTS]..[MAX_READ_ATTEMPTS] exactly when
     * [effectClass] is [EffectClass.READ], and null otherwise.
     */
    val maxReadAttempts: Int?,

    /** How well this command is understood; gates exposure and hardware use, not parsing. */
    val confidence: VerificationLevel,
) {

    init {
        require(id.isNotBlank()) { "command id is the symbolic identity; a blank one cannot be referenced" }
        require(displayName.isNotBlank()) { "displayName is presentation text and cannot be blank" }
        require(timeoutMillis == null || timeoutMillis > 0) {
            "timeoutMillis must be unreported (null) or positive, was $timeoutMillis"
        }

        if (effectClass == EffectClass.READ) {
            val attempts = requireNotNull(maxReadAttempts) {
                "read command '$id' must declare maxReadAttempts in " +
                    "$MIN_READ_ATTEMPTS..$MAX_READ_ATTEMPTS; a read with no attempt bound is an " +
                    "unbounded retry loop waiting to happen"
            }
            require(attempts in MIN_READ_ATTEMPTS..MAX_READ_ATTEMPTS) {
                "maxReadAttempts for read command '$id' must be in " +
                    "$MIN_READ_ATTEMPTS..$MAX_READ_ATTEMPTS, was $attempts"
            }
        } else {
            require(maxReadAttempts == null) {
                "${effectClass.name} command '$id' cannot carry maxReadAttempts: repeating a " +
                    "side-effecting command is prohibited (specs.md section 4), so a write attempt " +
                    "budget has nothing to describe"
            }
        }
    }

    /** Whether this command changes the device. Used by paths that must not repeat it. */
    val isWrite: Boolean
        get() = effectClass != EffectClass.READ

    /**
     * Whether the recorded retry policy allows this command to be issued again
     * automatically.
     *
     * Delegated to [EffectClass.permitsAutomaticRetry] so the answer cannot drift away
     * from the effect class.
     */
    val permitsAutomaticRetry: Boolean
        get() = effectClass.permitsAutomaticRetry

    /**
     * Whether this command may back a control the user can touch.
     *
     * False at [VerificationLevel.INFERRED]: an inferred command is a hypothesis, and a
     * hypothesis rendered as a button is the fake capability detection master section 51
     * names as a defect. Recording and parsing stay permitted (SEC-RES-004).
     */
    val canBeExposedAsControl: Boolean
        get() = confidence != VerificationLevel.INFERRED

    companion object {
        /** A read budget under one attempt is not a retry policy. */
        const val MIN_READ_ATTEMPTS: Int = 1

        /** Ceiling on the read-attempt budget, so no definition creates an unbounded loop. */
        const val MAX_READ_ATTEMPTS: Int = 5
    }
}
