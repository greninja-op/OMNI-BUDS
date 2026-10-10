package com.omnibuds.core.platform

/**
 * Injectable source of stable, correlated operation identifiers and random nonces.
 *
 * Core operations require non-blank identifiers for traceability, error correlation,
 * and replay protection. Test environments inject deterministic implementations.
 */
interface PlatformIdentifierSource {
    /**
     * Generates a non-blank operation ID, optionally incorporating [prefix].
     */
    fun generateOperationId(prefix: String? = null): String

    /**
     * Generates a non-blank nonce for replay protection and verification tokens.
     */
    fun generateNonce(): String
}

/**
 * Deterministic identifier provider for reproducible offline tests.
 */
class DeterministicIdentifierSource(
    private val defaultPrefix: String = "op",
    private var sequenceNumber: Long = 1L,
) : PlatformIdentifierSource {
    override fun generateOperationId(prefix: String?): String {
        val tag = prefix?.takeIf { it.isNotBlank() } ?: defaultPrefix
        val id = "$tag-${sequenceNumber++}"
        return id
    }

    override fun generateNonce(): String {
        val nonce = "nonce-${sequenceNumber++}"
        return nonce
    }

    fun reset(startSequence: Long = 1L) {
        sequenceNumber = startSequence
    }
}
