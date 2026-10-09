package com.omnibuds.core.diagnostics

import com.omnibuds.core.security.LogRedactor

/**
 * Safe local diagnostic export.
 *
 * Phase 36: explicit, bounded, sanitized. Never automatic, never
 * uploaded. Raw payloads and secrets are excluded by construction —
 * the export only carries redacted messages, severities, categories,
 * timestamps, and correlation ids.
 */
object DiagnosticExporter {

    /** Export schema version. */
    const val SCHEMA_VERSION = 1

    /** Maximum export size in characters. */
    const val MAX_EXPORT_CHARS = 256 * 1_024 // 256 KiB

    /** Maximum events per export. */
    const val MAX_EXPORT_EVENTS = 512

    /** Export outcome. */
    sealed interface ExportResult {
        data class Exported(val content: String, val eventCount: Int) : ExportResult
        data class Refused(val reason: String) : ExportResult
    }

    /**
     * Build a sanitized export from stored events.
     * Newest events first; stops at the event or size cap.
     */
    fun export(
        stored: List<DiagnosticStore.StoredEvent>,
        createdEpochMillis: Long,
    ): ExportResult {
        if (stored.isEmpty()) {
            return ExportResult.Refused("no diagnostic events to export")
        }
        val selected = stored.takeLast(MAX_EXPORT_EVENTS).reversed()
        val sb = StringBuilder()
        sb.append("{\"schemaVersion\":").append(SCHEMA_VERSION)
        sb.append(",\"createdEpochMillis\":").append(createdEpochMillis)
        sb.append(",\"events\":[")
        var count = 0
        for ((index, s) in selected.withIndex()) {
            val entry = buildEntry(s)
            // Reserve room for the closing brackets.
            if (sb.length + entry.length + 4 > MAX_EXPORT_CHARS) break
            if (index > 0) sb.append(',')
            sb.append(entry)
            count++
        }
        sb.append("]}")
        if (count == 0) return ExportResult.Refused("export would exceed size limit")
        return ExportResult.Exported(sb.toString(), count)
    }

    private fun buildEntry(s: DiagnosticStore.StoredEvent): String {
        val e = s.event
        // Redact at export; the store holds pre-redaction text by design.
        val message = LogRedactor.redact(e.message)
        return buildString {
            append("{\"seq\":").append(s.sequence)
            append(",\"ts\":").append(e.timestampEpochMillis)
            append(",\"sev\":\"").append(e.severity.name).append('"')
            append(",\"cat\":\"").append(e.category.name).append('"')
            append(",\"msg\":").append(jsonString(message))
            if (e.operationId != null) {
                append(",\"op\":").append(jsonString(e.operationId))
            }
            append('}')
        }
    }

    private fun jsonString(value: String): String {
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
        for (c in value) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
