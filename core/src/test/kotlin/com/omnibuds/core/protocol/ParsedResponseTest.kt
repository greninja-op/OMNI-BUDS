package com.omnibuds.core.protocol

import com.omnibuds.core.config.ConfigurationValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ParsedResponse] absence semantics, which is where "unknown must remain unknown" is won
 * or lost in the protocol layer (Phase 0 `specs.md` section 2.2, section 3 rule 5,
 * master section 23, ADR-P0-016).
 *
 * Tier T1.
 */
class ParsedResponseTest {

    @Test
    fun aMissingFieldReadsAsUnknownAndNotAsZero() {
        val parsed = ParsedResponse("readExampleStatus", mapOf("reported" to ConfigurationValue.IntValue(40)))

        assertNull(parsed["level"])
        assertFalse(parsed.has("level"))
        assertEquals(setOf("reported"), parsed.reportedFieldNames)
        assertEquals(1, parsed.reportedFieldCount)
    }

    @Test
    fun aReportedZeroAndAReportedFalseAreRealReadingsAndArePreserved() {
        val parsed = ParsedResponse(
            commandId = "readExampleStatus",
            fields = mapOf(
                "level" to ConfigurationValue.IntValue(0),
                "charging" to ConfigurationValue.BooleanValue(false),
            ),
        )

        // A flat battery reports 0; a device that said "not charging" reported false.
        // Neither is the same statement as silence, and neither may be dropped.
        assertEquals(ConfigurationValue.IntValue(0), parsed["level"])
        assertEquals(ConfigurationValue.BooleanValue(false), parsed["charging"])
        assertTrue(parsed.has("level"))
    }

    @Test
    fun anEmptyParsedAnswerIsExpressibleRatherThanAFabricatedOne() {
        val parsed = ParsedResponse("readExampleStatus", emptyMap())

        assertTrue(parsed.isEmpty)
        assertNull(parsed["anything"])
    }

    @Test
    fun aCallerCannotEditAParsedAnswerThroughTheMapItSupplied() {
        val source = mutableMapOf<String, ConfigurationValue>("level" to ConfigurationValue.IntValue(10))
        val parsed = ParsedResponse("readExampleStatus", source)

        source["level"] = ConfigurationValue.IntValue(99)
        source["late"] = ConfigurationValue.IntValue(1)

        assertEquals(ConfigurationValue.IntValue(10), parsed["level"])
        assertNull(parsed["late"])
        assertEquals(1, parsed.reportedFieldCount)
    }

    @Test
    fun aBlankCorrelationKeyIsRefused() {
        assertFailsWith<IllegalArgumentException> { ParsedResponse("", emptyMap()) }
    }

    @Test
    fun answersToDifferentCommandsAreNotInterchangeable() {
        val level = ParsedResponse("readExampleStatus", mapOf("value" to ConfigurationValue.IntValue(1)))
        val mode = ParsedResponse("readExampleMode", mapOf("value" to ConfigurationValue.IntValue(1)))

        assertEquals(level["value"], mode["value"])
        assertNotEquals(level, mode)
    }
}
