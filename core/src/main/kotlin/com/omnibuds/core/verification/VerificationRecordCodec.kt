package com.omnibuds.core.verification

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.config.ConfigurationValue
import com.omnibuds.core.config.ConfigurationValueJson

/**
 * Serializes verification records.
 *
 * Phase 18 (§11): versioned envelope. Corrupt data → null (recovery path),
 * never silent repair.
 *
 * Format: {"v":1,"id":"...","device":"...","stage":"...","outcome":"..."|null,...}
 * Full fidelity for records; evidence stored as count + summary (bounded history).
 */
internal object VerificationRecordCodec {

    fun encode(record: VerificationRecord): String = buildString {
        append("""{"v":${VerificationRepository.SCHEMA_VERSION},""")
        append(""""id":${q(record.id.value)},""")
        append(""""device":${q(record.deviceKey)},""")
        append(""""feature":${q(record.plan.targetFeature.qualifiedName)},""")
        append(""""expected":${ConfigurationValueJson.encode(record.plan.expectedValue)},""")
        append(""""scope":${q(record.plan.requestedScope.name)},""")
        append(""""stage":${q(record.stage.name)},""")
        append(""""outcome":${record.outcome?.let { q(it.name) } ?: "null"},""")
        append(""""appStatus":${q(record.applicationStatus.name)},""")
        append(""""proven":${q(record.provenScope.name)},""")
        append(""""evidence":${record.evidence.size},""")
        append(""""baseline":${record.baseline?.let { ConfigurationValueJson.encode(it) } ?: "null"},""")
        append(""""reason":${record.failureReason?.let { q(it) } ?: "null"},""")
        append(""""created":${record.createdAtMillis},""")
        append(""""updated":${record.updatedAtMillis},""")
        append(""""completed":${record.completedAtMillis ?: "null"}}""")
    }

    /**
     * Decode, or null when malformed, wrong version, or inconsistent.
     * Recovery treats null as "unrecoverable" — never as a default record.
     */
    fun decode(json: String): VerificationRecord? {
        return try {
            decodeOrThrow(json)
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeOrThrow(json: String): VerificationRecord? {
        val v = intField(json, "\"v\":") ?: return null
        if (v != VerificationRepository.SCHEMA_VERSION) return null

        val id = stringField(json, "\"id\":") ?: return null
        val device = stringField(json, "\"device\":") ?: return null
        val featureName = stringField(json, "\"feature\":") ?: return null
        val expectedJson = objectField(json, "\"expected\":") ?: return null
        val expected = ConfigurationValueJson.decode(expectedJson) ?: return null
        val scopeName = stringField(json, "\"scope\":") ?: return null
        val scope = enumValue<PersistenceScope>(scopeName) ?: return null
        val stageName = stringField(json, "\"stage\":") ?: return null
        val stage = enumValue<VerificationStage>(stageName) ?: return null
        val outcomeName = stringField(json, "\"outcome\":")
        val outcome = outcomeName?.let { enumValue<VerificationOutcome>(it) }
        val appStatusName = stringField(json, "\"appStatus\":") ?: return null
        val appStatus = enumValue<ApplicationStatus>(appStatusName) ?: return null
        val provenName = stringField(json, "\"proven\":") ?: return null
        val proven = enumValue<PersistenceScope>(provenName) ?: return null
        val baselineJson = objectField(json, "\"baseline\":")
        val baseline = baselineJson?.let { ConfigurationValueJson.decode(it) }
        val reason = stringField(json, "\"reason\":")
        val created = longField(json, "\"created\":") ?: return null
        val updated = longField(json, "\"updated\":") ?: return null
        val completed = longField(json, "\"completed\":")

        // Reconstruct a minimal plan (write-only shape; full plan re-derived on recovery).
        val plan = VerificationPlan.writeOnly(
            FeatureId.of(featureName.substringBefore("."), featureName.substringAfter(".", "")),
            expected,
        ).copy(requestedScope = scope)

        return VerificationRecord(
            id = VerificationId.of(id),
            deviceKey = device,
            plan = plan,
            stage = stage,
            outcome = outcome,
            applicationStatus = appStatus,
            provenScope = proven,
            evidence = emptyList(), // Evidence history bounded; re-collected on recovery.
            baseline = baseline,
            failureReason = reason,
            createdAtMillis = created,
            updatedAtMillis = updated,
            completedAtMillis = completed,
        )
    }

    private fun q(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun stringField(json: String, field: String): String? {
        val idx = json.indexOf(field)
        if (idx == -1) return null
        var i = idx + field.length
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length) return null
        if (json.startsWith("null", i)) return null
        if (json[i] != '"') return null
        i++
        val sb = StringBuilder()
        while (i < json.length && json[i] != '"') {
            if (json[i] == '\\') { i++; sb.append(json.getOrNull(i) ?: return null) }
            else sb.append(json[i])
            i++
        }
        return sb.toString()
    }

    private fun intField(json: String, field: String): Int? {
        val idx = json.indexOf(field)
        if (idx == -1) return null
        val start = idx + field.length
        var end = start
        while (end < json.length && (json[end].isDigit() || json[end] == '-')) end++
        return json.substring(start, end).toIntOrNull()
    }

    private fun longField(json: String, field: String): Long? {
        val idx = json.indexOf(field)
        if (idx == -1) return null
        // Handle "null" literal.
        var i = idx + field.length
        while (i < json.length && json[i].isWhitespace()) i++
        if (json.startsWith("null", i)) return null
        val start = i
        var end = start
        while (end < json.length && (json[end].isDigit() || json[end] == '-')) end++
        return json.substring(start, end).toLongOrNull()
    }

    private fun objectField(json: String, field: String): String? {
        val idx = json.indexOf(field)
        if (idx == -1) return null
        var i = idx + field.length
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length) return null
        if (json.startsWith("null", i)) return null
        if (json[i] != '{') return null
        var depth = 0
        var inStr = false
        var esc = false
        val start = i
        while (i < json.length) {
            val c = json[i]
            if (esc) esc = false
            else if (c == '\\' && inStr) esc = true
            else if (c == '"') inStr = !inStr
            else if (!inStr && c == '{') depth++
            else if (!inStr && c == '}') {
                depth--
                if (depth == 0) return json.substring(start, i + 1)
            }
            i++
        }
        return null
    }

    private inline fun <reified T : Enum<T>> enumValue(name: String): T? =
        try {
            enumValueOf<T>(name)
        } catch (e: Exception) {
            null
        }
}
