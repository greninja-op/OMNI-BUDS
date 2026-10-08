package com.omnibuds.core.feature

import com.omnibuds.core.config.ConfigurationValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The extended value shapes and their constraints.
 *
 * New [ConfigurationValue] subtypes must refuse the classic smuggling routes —
 * NaN, infinities, blank identities, duplicate field names, unbounded
 * collections — at construction, because a value that cannot be constructed
 * badly cannot corrupt the engine later (Phase 9 security review).
 */
class FeatureValueTest {

    // ---- new ConfigurationValue shapes ----

    @Test
    fun floatValueRefusesNonNumbers() {
        assertFailsWith<IllegalArgumentException> { ConfigurationValue.FloatValue(Double.NaN) }
        assertFailsWith<IllegalArgumentException> { ConfigurationValue.FloatValue(Double.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { ConfigurationValue.FloatValue(Double.NEGATIVE_INFINITY) }
        ConfigurationValue.FloatValue(0.5)
    }

    @Test
    fun rangeValueRequiresOrderedFiniteBounds() {
        assertFailsWith<IllegalArgumentException> { ConfigurationValue.RangeValue(10.0, 5.0) }
        assertFailsWith<IllegalArgumentException> { ConfigurationValue.RangeValue(Double.NaN, 5.0) }
        val range = ConfigurationValue.RangeValue(20.0, 20_000.0)
        assertEquals(20.0, range.min)
        assertEquals(20_000.0, range.max)
    }

    @Test
    fun structuredValueRequiresUniqueNonBlankNamesAndBoundedFields() {
        val field = { name: String ->
            ConfigurationValue.StructuredField(name, ConfigurationValue.BooleanValue(true))
        }
        assertFailsWith<IllegalArgumentException> { ConfigurationValue.StructuredValue(emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            ConfigurationValue.StructuredValue(listOf(field("a"), field("a")))
        }
        assertFailsWith<IllegalArgumentException> { field("  ") }
        val tooMany = (1..(ConfigurationValue.StructuredValue.MAX_STRUCTURED_FIELDS + 1)).map { field("f$it") }
        assertFailsWith<IllegalArgumentException> { ConfigurationValue.StructuredValue(tooMany) }

        val value = ConfigurationValue.StructuredValue(listOf(field("a"), field("b")))
        assertEquals(ConfigurationValue.BooleanValue(true), value["a"])
        assertNull(value["missing"])
    }

    @Test
    fun bitmaskValueRequiresNonEmptyBoundedFlags() {
        assertFailsWith<IllegalArgumentException> { ConfigurationValue.BitmaskValue(emptySet()) }
        assertFailsWith<IllegalArgumentException> { ConfigurationValue.BitmaskValue(setOf("ok", "  ")) }
        val tooMany =
            (1..(ConfigurationValue.BitmaskValue.MAX_FLAGS + 1)).map { "flag$it" }.toSet()
        assertFailsWith<IllegalArgumentException> { ConfigurationValue.BitmaskValue(tooMany) }
        ConfigurationValue.BitmaskValue(setOf("left", "right"))
    }

    @Test
    fun customValueIsBoundedAndNonBlank() {
        assertFailsWith<IllegalArgumentException> { ConfigurationValue.CustomValue("  ") }
        assertFailsWith<IllegalArgumentException> {
            ConfigurationValue.CustomValue("x".repeat(ConfigurationValue.CustomValue.MAX_CUSTOM_LENGTH + 1))
        }
        ConfigurationValue.CustomValue("opaque-vendor-payload")
    }

    // ---- FeatureValueType.accepts ----

    @Test
    fun everyShapeAcceptsExactlyItsOwnValueKind() {
        val cases = mapOf(
            FeatureValueType.BOOLEAN to ConfigurationValue.BooleanValue(true),
            FeatureValueType.ENUM to ConfigurationValue.ModeValue("anc", "ANC"),
            FeatureValueType.INTEGER to ConfigurationValue.IntValue(3),
            FeatureValueType.FLOAT to ConfigurationValue.FloatValue(0.5),
            FeatureValueType.RANGE to ConfigurationValue.RangeValue(0.0, 1.0),
            FeatureValueType.STRING to ConfigurationValue.StringValue("jazz"),
            FeatureValueType.STRUCTURED to ConfigurationValue.StructuredValue(
                listOf(ConfigurationValue.StructuredField("a", ConfigurationValue.BooleanValue(true))),
            ),
            FeatureValueType.BITMASK to ConfigurationValue.BitmaskValue(setOf("left")),
            FeatureValueType.CUSTOM to ConfigurationValue.CustomValue("payload"),
        )
        for ((type, value) in cases) {
            assertTrue(type.accepts(value), "$type should accept $value")
            for ((otherType, otherValue) in cases) {
                if (otherType != type) {
                    assertFalse(type.accepts(otherValue), "$type should not accept $otherValue")
                }
            }
        }
    }

    // ---- FeatureConstraints ----

    @Test
    fun constraintsRefuseContradictoryBounds() {
        assertFailsWith<IllegalArgumentException> { FeatureConstraints(minInt = 10, maxInt = 5) }
        assertFailsWith<IllegalArgumentException> { FeatureConstraints(stepInt = 0) }
        assertFailsWith<IllegalArgumentException> { FeatureConstraints(minFloat = 2.0, maxFloat = 1.0) }
        assertFailsWith<IllegalArgumentException> { FeatureConstraints(allowedModes = emptySet()) }
        assertFailsWith<IllegalArgumentException> { FeatureConstraints(maxStringLength = 0) }
    }

    @Test
    fun integerConstraintsEnforceMinMaxAndStep() {
        val c = FeatureConstraints(minInt = 0, maxInt = 10, stepInt = 2)
        assertNull(c.violationOf(ConfigurationValue.IntValue(4)))
        assertTrue(c.violationOf(ConfigurationValue.IntValue(-1))!!.contains("minimum"))
        assertTrue(c.violationOf(ConfigurationValue.IntValue(11))!!.contains("maximum"))
        assertTrue(c.violationOf(ConfigurationValue.IntValue(3))!!.contains("step"))
    }

    @Test
    fun floatAndModeAndStringConstraintsEnforceTheirBounds() {
        val floats = FeatureConstraints(minFloat = 0.0, maxFloat = 1.0)
        assertNull(floats.violationOf(ConfigurationValue.FloatValue(0.5)))
        assertTrue(floats.violationOf(ConfigurationValue.FloatValue(1.5))!!.contains("maximum"))

        val modes = FeatureConstraints(allowedModes = setOf("off", "anc"))
        assertNull(modes.violationOf(ConfigurationValue.ModeValue("anc", "ANC")))
        assertTrue(modes.violationOf(ConfigurationValue.ModeValue("turbo", "Turbo"))!!.contains("not among"))

        val strings = FeatureConstraints(maxStringLength = 4)
        assertNull(strings.violationOf(ConfigurationValue.StringValue("jazz")))
        assertTrue(strings.violationOf(ConfigurationValue.StringValue("jazz-fusion"))!!.contains("exceeds"))
    }

    @Test
    fun bitmaskConstraintsEnforceAllowedFlags() {
        val c = FeatureConstraints(allowedFlags = setOf("left", "right"))
        assertNull(c.violationOf(ConfigurationValue.BitmaskValue(setOf("left"))))
        assertTrue(
            c.violationOf(ConfigurationValue.BitmaskValue(setOf("left", "nose"))!!)!!.contains("unknown flags"),
        )
    }

    @Test
    fun constraintsDoNotJudgeValuesOutsideTheirKind() {
        val c = FeatureConstraints(minInt = 0, maxInt = 10)
        // A boolean is not an integer; shape is the type's job, not the constraints'.
        assertNull(c.violationOf(ConfigurationValue.BooleanValue(true)))
        assertNull(c.violationOf(ConfigurationValue.RangeValue(0.0, 5.0)))
    }
}
