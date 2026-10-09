package com.omnibuds.core.verification

/**
 * Stable identifier for one verification operation.
 *
 * Phase 18 (OB-P18-REQ-001): unique per attempt. Restarting creates a new
 * attempt with a new ID — history is never rewritten.
 */
@JvmInline
value class VerificationId private constructor(val value: String) {

    companion object {
        private var counter = 0L
        private val lock = Any()

        /** Mint a new unique identifier. */
        fun new(): VerificationId = synchronized(lock) {
            counter++
            VerificationId("vrf-${System.currentTimeMillis()}-$counter")
        }

        fun of(value: String): VerificationId {
            require(value.isNotBlank()) { "verification id must not be blank" }
            return VerificationId(value)
        }
    }

    override fun toString(): String = value
}
