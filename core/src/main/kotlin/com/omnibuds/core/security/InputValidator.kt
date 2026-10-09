package com.omnibuds.core.security

/**
 * Centralized bounded input validation for untrusted data.
 *
 * Phase 35: all data from external devices, imported traces,
 * fixtures, and knowledge sources is untrusted until validated.
 * Limits are justified below; they are generous enough for
 * legitimate protocol traffic and tight enough to bound memory/CPU.
 */
object InputValidator {

    /**
     * Maximum accepted protocol message size in bytes.
     * Justification: Bluetooth vendor control frames are small
     * (tens to hundreds of bytes); 64 KiB is far above any
     * legitimate frame and bounds allocation.
     */
    const val MAX_MESSAGE_BYTES = 65_536

    /**
     * Maximum collection size in a parsed structure.
     * Justification: capability/feature lists are small; 1024
     * bounds quadratic behavior without rejecting real devices.
     */
    const val MAX_COLLECTION_SIZE = 1_024

    /**
     * Maximum parser nesting depth.
     * Justification: protocol structures are flat; 16 is generous.
     */
    const val MAX_NESTING_DEPTH = 16

    /**
     * Maximum imported trace/file size in bytes (16 MiB).
     * Justification: traces are diagnostic artifacts, not media.
     */
    const val MAX_IMPORT_BYTES = 16 * 1_024 * 1_024

    /** Validation failure. */
    sealed interface ValidationResult {
        data object Valid : ValidationResult
        data class Invalid(val reason: String) : ValidationResult
    }

    fun validateMessageLength(length: Int): ValidationResult =
        when {
            length < 0 -> ValidationResult.Invalid("negative length: $length")
            length > MAX_MESSAGE_BYTES ->
                ValidationResult.Invalid("message too large: $length > $MAX_MESSAGE_BYTES")
            else -> ValidationResult.Valid
        }

    fun validateCollectionSize(size: Int): ValidationResult =
        when {
            size < 0 -> ValidationResult.Invalid("negative size: $size")
            size > MAX_COLLECTION_SIZE ->
                ValidationResult.Invalid("collection too large: $size > $MAX_COLLECTION_SIZE")
            else -> ValidationResult.Valid
        }

    fun validateNestingDepth(depth: Int): ValidationResult =
        when {
            depth < 0 -> ValidationResult.Invalid("negative depth: $depth")
            depth > MAX_NESTING_DEPTH ->
                ValidationResult.Invalid("nesting too deep: $depth > $MAX_NESTING_DEPTH")
            else -> ValidationResult.Valid
        }

    fun validateImportSize(bytes: Long): ValidationResult =
        when {
            bytes < 0 -> ValidationResult.Invalid("negative import size")
            bytes > MAX_IMPORT_BYTES ->
                ValidationResult.Invalid("import too large: $bytes > $MAX_IMPORT_BYTES")
            else -> ValidationResult.Valid
        }

    /**
     * Overflow-safe addition for length calculations.
     * Returns null on overflow instead of wrapping.
     */
    fun safeAdd(a: Long, b: Long): Long? {
        val result = a + b
        // Overflow iff the signs of a and b match but differ from result's.
        if ((a xor result) and (b xor result) < 0) return null
        return result
    }

    /**
     * Validates a frame's declared length against the available buffer.
     */
    fun validateFrame(
        declaredLength: Int,
        bufferSize: Int,
        offset: Int,
    ): ValidationResult {
        if (offset < 0 || offset > bufferSize) {
            return ValidationResult.Invalid("offset out of range: $offset")
        }
        val lengthCheck = validateMessageLength(declaredLength)
        if (lengthCheck is ValidationResult.Invalid) return lengthCheck
        val end = safeAdd(offset.toLong(), declaredLength.toLong())
            ?: return ValidationResult.Invalid("offset+length overflows")
        if (end > bufferSize) {
            return ValidationResult.Invalid(
                "frame overruns buffer: offset=$offset length=$declaredLength size=$bufferSize",
            )
        }
        return ValidationResult.Valid
    }
}
