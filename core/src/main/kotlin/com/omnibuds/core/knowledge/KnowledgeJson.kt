package com.omnibuds.core.knowledge

/**
 * Minimal deterministic JSON codec for knowledge records.
 *
 * Phase 22: pure Kotlin, no serialization framework — following the
 * `ConfigurationValueJson` pattern. Keys are emitted in fixed order;
 * encoding is deterministic for the same input.
 *
 * Supported values: null, Boolean, Long/Int/Double, String,
 * List<Any?>, Map<String, Any?>.
 */
object KnowledgeJson {

    /** Encode a value to JSON text. */
    fun encode(value: Any?): String = buildString { appendValue(value) }

    private fun StringBuilder.appendValue(value: Any?) {
        when (value) {
            null -> append("null")
            is Boolean -> append(if (value) "true" else "false")
            is Int -> append(value.toString())
            is Long -> append(value.toString())
            is Double -> append(value.toString())
            is String -> appendQuoted(value)
            is List<*> -> {
                append('[')
                value.forEachIndexed { i, item ->
                    if (i > 0) append(',')
                    appendValue(item)
                }
                append(']')
            }
            is Map<*, *> -> {
                append('{')
                // Deterministic: sort keys.
                val entries = value.entries
                    .map { (it.key as String) to it.value }
                    .sortedBy { it.first }
                entries.forEachIndexed { i, (k, v) ->
                    if (i > 0) append(',')
                    appendQuoted(k)
                    append(':')
                    appendValue(v)
                }
                append('}')
            }
            else -> throw IllegalArgumentException(
                "unsupported JSON value type: ${value::class.simpleName}",
            )
        }
    }

    private fun StringBuilder.appendQuoted(s: String) {
        append('"')
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }

    /**
     * Decode JSON text to a value tree, or null when malformed.
     * Numbers decode as Long when integral, Double otherwise.
     */
    fun decode(json: String): Any? = try {
        val parser = Parser(json)
        val value = parser.parseValue()
        parser.skipWhitespace()
        check(parser.atEnd())
        value
    } catch (e: Exception) {
        null
    }

    private class Parser(val s: String) {
        var pos = 0

        fun atEnd(): Boolean = pos >= s.length

        fun skipWhitespace() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun parseValue(): Any? {
            skipWhitespace()
            if (atEnd()) throw IllegalArgumentException("unexpected end")
            return when (s[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                else -> parseNumber()
            }
        }

        private fun parseLiteral(literal: String, value: Any?): Any? {
            if (!s.startsWith(literal, pos)) throw IllegalArgumentException("bad literal")
            pos += literal.length
            return value
        }

        private fun parseObject(): Map<String, Any?> {
            pos++ // {
            val map = linkedMapOf<String, Any?>()
            skipWhitespace()
            if (!atEnd() && s[pos] == '}') { pos++; return map }
            while (true) {
                skipWhitespace()
                val key = parseString() as String
                skipWhitespace()
                if (atEnd() || s[pos] != ':') throw IllegalArgumentException("expected :")
                pos++
                map[key] = parseValue()
                skipWhitespace()
                if (atEnd()) throw IllegalArgumentException("unterminated object")
                when (s[pos]) {
                    ',' -> { pos++; continue }
                    '}' -> { pos++; return map }
                    else -> throw IllegalArgumentException("expected , or }")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            pos++ // [
            val list = mutableListOf<Any?>()
            skipWhitespace()
            if (!atEnd() && s[pos] == ']') { pos++; return list }
            while (true) {
                list.add(parseValue())
                skipWhitespace()
                if (atEnd()) throw IllegalArgumentException("unterminated array")
                when (s[pos]) {
                    ',' -> { pos++; continue }
                    ']' -> { pos++; return list }
                    else -> throw IllegalArgumentException("expected , or ]")
                }
            }
        }

        private fun parseString(): String {
            if (s[pos] != '"') throw IllegalArgumentException("expected string")
            pos++
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw IllegalArgumentException("unterminated string")
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd()) throw IllegalArgumentException("bad escape")
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) throw IllegalArgumentException("bad unicode")
                                sb.append(s.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            else -> throw IllegalArgumentException("bad escape: $e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun parseNumber(): Number {
            val start = pos
            if (!atEnd() && s[pos] == '-') pos++
            while (!atEnd() && s[pos].isDigit()) pos++
            val isDouble = !atEnd() && (s[pos] == '.' || s[pos] == 'e' || s[pos] == 'E')
            if (isDouble) {
                while (!atEnd() && (s[pos].isDigit() || s[pos] in ".eE+-")) pos++
                return s.substring(start, pos).toDouble()
            }
            return s.substring(start, pos).toLong()
        }
    }

    // --- typed accessors for entity codecs ---

    @Suppress("UNCHECKED_CAST")
    fun obj(map: Map<String, Any?>, key: String): Map<String, Any?>? =
        map[key] as? Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    fun list(map: Map<String, Any?>, key: String): List<Any?> =
        (map[key] as? List<Any?>) ?: emptyList()

    fun string(map: Map<String, Any?>, key: String): String? =
        map[key] as? String

    fun stringList(map: Map<String, Any?>, key: String): List<String> =
        list(map, key).mapNotNull { it as? String }

    fun stringSet(map: Map<String, Any?>, key: String): Set<String> =
        stringList(map, key).toSet()

    fun long(map: Map<String, Any?>, key: String): Long? =
        (map[key] as? Number)?.toLong()

    fun int(map: Map<String, Any?>, key: String): Int? =
        (map[key] as? Number)?.toInt()

    fun bool(map: Map<String, Any?>, key: String, default: Boolean = false): Boolean =
        (map[key] as? Boolean) ?: default
}
