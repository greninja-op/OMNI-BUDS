package com.omnibuds.core.config


/**
 * Minimal deterministic JSON codec for [ConfigurationValue].
 *
 * Phase 17: pure Kotlin, no serialization framework — the config domain
 * stays dependency-free and the format is fully under our control.
 * Unknown fields are ignored on read (forward tolerance); unknown value
 * types fail explicitly (never silently dropped).
 *
 * Format: {"t":"<type>","v":<value>} where type is one of
 * bool, int, string, mode, float, range, structured, bitmask, custom.
 */
object ConfigurationValueJson {

    fun encode(value: ConfigurationValue): String = when (value) {
        is ConfigurationValue.BooleanValue ->
            """{"t":"bool","v":${value.value}}"""
        is ConfigurationValue.IntValue ->
            """{"t":"int","v":${value.value}}"""
        is ConfigurationValue.StringValue ->
            """{"t":"string","v":${quote(value.value)}}"""
        is ConfigurationValue.ModeValue ->
            """{"t":"mode","v":${quote(value.technicalName)},"d":${quote(value.displayName)}}"""
        is ConfigurationValue.FloatValue ->
            """{"t":"float","v":${value.value}}"""
        is ConfigurationValue.RangeValue ->
            """{"t":"range","min":${value.min},"max":${value.max}}"""
        is ConfigurationValue.StructuredValue ->
            """{"t":"structured","v":[${value.fields.joinToString(",") {
                """{"n":${quote(it.name)},"v":${encode(it.value)}}"""
            }}]}"""
        is ConfigurationValue.BitmaskValue ->
            """{"t":"bitmask","v":[${value.flags.joinToString(",") { quote(it) }}]}"""
        is ConfigurationValue.CustomValue ->
            """{"t":"custom","v":${quote(value.payload)}}"""
    }

    /**
     * Decode, or null when the input is malformed or the type is unknown.
     * Malformed input is never silently coerced — callers treat null as
     * corruption and route to recovery.
     */
    fun decode(json: String): ConfigurationValue? = try {
        parseValue(json.trim())
    } catch (e: Exception) {
        null
    }

    // --- minimal parser ---

    private fun parseValue(s: String): ConfigurationValue? {
        val map = parseObject(s) ?: return null
        return when (map["t"]) {
            "bool" -> (map["v"] as? Boolean)?.let { ConfigurationValue.BooleanValue(it) }
            "int" -> (map["v"] as? Number)?.toInt()?.let { ConfigurationValue.IntValue(it) }
            "string" -> (map["v"] as? String)?.let { ConfigurationValue.StringValue(it) }
            "mode" -> {
                val tech = map["v"] as? String
                val disp = map["d"] as? String
                if (tech != null && disp != null) ConfigurationValue.ModeValue(tech, disp) else null
            }
            "float" -> (map["v"] as? Number)?.toDouble()?.let { ConfigurationValue.FloatValue(it) }
            "range" -> {
                val min = (map["min"] as? Number)?.toDouble()
                val max = (map["max"] as? Number)?.toDouble()
                if (min != null && max != null) ConfigurationValue.RangeValue(min, max) else null
            }
            "structured" -> {
                val raw = map["v"] as? String ?: return null
                val items: List<String> = try {
                    @Suppress("UNCHECKED_CAST")
                    Parser(raw).parseArray() as List<String>
                } catch (e: Exception) {
                    return null
                }
                val fields = items.mapNotNull { itemJson ->
                    val itemMap = try {
                        Parser(itemJson).parseObject()
                    } catch (e: Exception) {
                        null
                    } ?: return@mapNotNull null
                    val n = itemMap["n"] as? String ?: return@mapNotNull null
                    val vRaw = itemMap["v"] as? String ?: return@mapNotNull null
                    val v = parseValue(vRaw) ?: return@mapNotNull null
                    ConfigurationValue.StructuredField(n, v)
                }
                // All fields must parse; a partial structured value is corruption.
                if (fields.size != items.size) return null
                ConfigurationValue.StructuredValue(fields)
            }
            "bitmask" -> {
                val raw = map["v"] as? String ?: return null
                val flags = try {
                    @Suppress("UNCHECKED_CAST")
                    (Parser(raw).parseArray() as List<String>).toSet()
                } catch (e: Exception) {
                    return null
                }
                ConfigurationValue.BitmaskValue(flags)
            }
            "custom" -> (map["v"] as? String)?.let { ConfigurationValue.CustomValue(it) }
            else -> null // Unknown type: explicit failure, never silent.
        }
    }

    private fun quote(s: String): String = buildString {
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

    // Tiny recursive-descent parser for the subset we emit.
    private class Parser(val s: String) {
        var i = 0
        fun parse(): Any? {
            skipWs()
            return when (s.getOrNull(i)) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> { expect("true"); true }
                'f' -> { expect("false"); false }
                'n' -> { expect("null"); null }
                else -> parseNumber()
            }
        }
        fun parseObject(): Map<String, Any?>? {
            if (s.getOrNull(i) != '{') return null
            i++; val map = mutableMapOf<String, Any?>()
            skipWs()
            if (s.getOrNull(i) == '}') { i++; return map }
            while (true) {
                skipWs()
                val k = parseString() ?: return null
                skipWs(); if (s.getOrNull(i) != ':') return null; i++
                // For nested objects in structured values, capture raw.
                skipWs()
                val v: Any? = if (s.getOrNull(i) == '{' || s.getOrNull(i) == '[') {
                    val start = i; parse(); s.substring(start, i)
                } else {
                    parse()
                }
                map[k] = v
                skipWs()
                when (s.getOrNull(i)) {
                    ',' -> { i++; continue }
                    '}' -> { i++; return map }
                    else -> return null
                }
            }
        }
        fun parseArray(): List<Any?> {
            i++; val list = mutableListOf<Any?>()
            skipWs()
            if (s.getOrNull(i) == ']') { i++; return list }
            while (true) {
                skipWs()
                val v: Any? = if (s.getOrNull(i) == '{' || s.getOrNull(i) == '[') {
                    val start = i; parse(); s.substring(start, i)
                } else {
                    parse()
                }
                list.add(v)
                skipWs()
                when (s.getOrNull(i)) {
                    ',' -> { i++; continue }
                    ']' -> { i++; return list }
                    else -> throw IllegalArgumentException("bad array")
                }
            }
        }
        fun parseString(): String? {
            if (s.getOrNull(i) != '"') return null
            i++; val sb = StringBuilder()
            while (true) {
                val c = s.getOrNull(i++) ?: return null
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> when (val e = s.getOrNull(i++)) {
                        '"' -> sb.append('"'); '\\' -> sb.append('\\')
                        'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                        'u' -> {
                            val hex = s.substring(i, i + 4); i += 4
                            sb.append(hex.toInt(16).toChar())
                        }
                        else -> return null
                    }
                    else -> sb.append(c)
                }
            }
        }
        fun parseNumber(): Number {
            val start = i
            while (s.getOrNull(i)?.let { it.isDigit() || it == '-' || it == '+' || it == '.' || it == 'e' || it == 'E' } == true) i++
            val num = s.substring(start, i)
            return if ('.' in num || 'e' in num || 'E' in num) num.toDouble() else num.toLong()
        }
        fun expect(lit: String) {
            if (!s.startsWith(lit, i)) throw IllegalArgumentException("expected $lit")
            i += lit.length
        }
        fun skipWs() { while (s.getOrNull(i)?.isWhitespace() == true) i++ }
    }

    private fun parseObject(s: String): Map<String, Any?>? = try {
        Parser(s).parseObject()
    } catch (e: Exception) {
        null
    }
}
