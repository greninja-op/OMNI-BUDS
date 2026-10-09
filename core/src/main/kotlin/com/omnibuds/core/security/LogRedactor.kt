package com.omnibuds.core.security

/**
 * Central redaction boundary for diagnostic text.
 *
 * Phase 35: complements OmniBudsLogger's sink-side redaction with a
 * testable pure function. Redaction never crashes — on any failure
 * it returns a safe placeholder.
 */
object LogRedactor {

    /** Patterns treated as sensitive. */
    private val sensitivePatterns = listOf(
        // Bluetooth MAC addresses.
        Regex("([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}"),
        // Bearer tokens / API keys in text.
        Regex("(?i)(token|secret|password|apikey|api_key)\\s*[:=]\\s*\\S+"),
    )

    private const val REDACTED = "[REDACTED]"

    /**
     * Redact sensitive substrings. Never throws.
     */
    fun redact(text: String): String = try {
        var result = text
        for (pattern in sensitivePatterns) {
            result = pattern.replace(result, REDACTED)
        }
        result
    } catch (_: Exception) {
        REDACTED
    }

    /**
     * Safe diagnostic message builder: formats with redaction applied
     * to every argument's string form.
     */
    fun message(template: String, vararg args: Any?): String {
        val rendered = try {
            template.format(*args.map { redact(it.toString()) }.toTypedArray())
        } catch (_: Exception) {
            "diagnostic formatting failed"
        }
        return redact(rendered)
    }
}
