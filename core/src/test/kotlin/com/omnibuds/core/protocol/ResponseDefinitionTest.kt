package com.omnibuds.core.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [ResponseDefinition] as the declared shape of an answer (PROTO-NOMAGIC-001,
 * `protocol-governance.md` section 10, specs.md section 1.5 naming).
 *
 * Tier T1.
 */
class ResponseDefinitionTest {

    @Test
    fun declaredFieldsAreAddressableByNameNotByPosition() {
        val shape = ResponseDefinition(
            commandId = "readExampleStatus",
            fieldNames = listOf("level", "charging"),
            acceptsNotification = true,
        )

        assertTrue(shape.declaresField("level"))
        assertTrue(shape.declaresField("charging"))
        assertFalse(shape.declaresField("byteAtOffset3"))
        assertTrue(shape.expectsFields)
    }

    @Test
    fun anAcknowledgementOnlyCommandIsExpressedAsAnEmptyShape() {
        val shape = ResponseDefinition("writeExampleLevel", emptyList(), acceptsNotification = false)

        assertFalse(shape.expectsFields)
        assertEquals(0, shape.fieldNames.size)
    }

    @Test
    fun aDuplicatedFieldNameIsRefusedBecauseOneKeyCannotCarryTwoMeanings() {
        assertFailsWith<IllegalArgumentException> {
            ResponseDefinition("readExampleStatus", listOf("level", "level"), acceptsNotification = false)
        }
    }

    @Test
    fun aBlankFieldNameOrCommandIdIsRefused() {
        assertFailsWith<IllegalArgumentException> {
            ResponseDefinition("readExampleStatus", listOf("level", " "), acceptsNotification = false)
        }
        assertFailsWith<IllegalArgumentException> {
            ResponseDefinition("", listOf("level"), acceptsNotification = false)
        }
    }

    @Test
    fun fieldOrderIsExactlyTheOrderItWasDeclaredIn() {
        val shape = ResponseDefinition(
            commandId = "readExampleStatus",
            fieldNames = listOf("second", "first", "third"),
            acceptsNotification = false,
        )

        assertEquals(listOf("second", "first", "third"), shape.fieldNames)
    }
}
