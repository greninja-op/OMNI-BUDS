package com.omnibuds.core.extension

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 23: feature schema and value-validation tests.
 */
class FeatureSchemaTest {

    private fun intFeature() = VendorFeatureDefinition(
        id = VendorFeatureId("vendor.acme.volume"),
        extensionId = VendorExtensionId("ext.acme.budsproto"),
        namespace = "acme.buds",
        canonicalName = "volume",
        category = "audio",
        valueType = VendorValueType.INT,
        constraints = ValueConstraints(minInt = 0, maxInt = 100, intStep = 5),
        writable = true,
    )

    @Test
    fun `valid int value`() {
        val result = intFeature().validateValue(VendorFeatureValue.IntValue(50))
        assertTrue(result.isValid())
    }

    @Test
    fun `int below minimum rejected`() {
        val result = intFeature().validateValue(VendorFeatureValue.IntValue(-1))
        assertTrue(result is ValueValidationResult.Invalid)
    }

    @Test
    fun `int above maximum rejected`() {
        val result = intFeature().validateValue(VendorFeatureValue.IntValue(101))
        assertTrue(result is ValueValidationResult.Invalid)
    }

    @Test
    fun `int step enforced`() {
        val result = intFeature().validateValue(VendorFeatureValue.IntValue(53))
        assertTrue(result is ValueValidationResult.Invalid)
    }

    @Test
    fun `wrong type rejected without coercion`() {
        // A string "50" is not silently coerced to int 50.
        val result = intFeature().validateValue(VendorFeatureValue.EnumValue("50"))
        assertTrue(result is ValueValidationResult.Invalid)
    }

    @Test
    fun `enum constrained`() {
        val f = VendorFeatureDefinition(
            id = VendorFeatureId("vendor.acme.ancmode"),
            extensionId = VendorExtensionId("ext.acme.budsproto"),
        namespace = "acme.buds",
            canonicalName = "anc_mode",
            category = "audio",
            valueType = VendorValueType.ENUM,
            constraints = ValueConstraints(allowedEnumValues = setOf("off", "on", "adaptive")),
            writable = true,
        )
        assertTrue(f.validateValue(VendorFeatureValue.EnumValue("on")).isValid())
        val bad = f.validateValue(VendorFeatureValue.EnumValue("turbo"))
        assertTrue(bad is ValueValidationResult.Invalid)
    }

    @Test
    fun `structured required fields`() {
        val f = VendorFeatureDefinition(
            id = VendorFeatureId("vendor.acme.eqpreset"),
            extensionId = VendorExtensionId("ext.acme.budsproto"),
        namespace = "acme.buds",
            canonicalName = "eq_preset",
            category = "audio",
            valueType = VendorValueType.STRUCTURED,
            constraints = ValueConstraints(
                requiredFields = setOf("bass", "treble"),
                allowedFields = setOf("bass", "treble", "mid"),
            ),
            writable = true,
        )
        val missing = f.validateValue(
            VendorFeatureValue.StructuredValue(mapOf(
                "bass" to VendorFeatureValue.IntValue(5),
            )),
        )
        assertTrue(missing is ValueValidationResult.Invalid)

        val unexpected = f.validateValue(
            VendorFeatureValue.StructuredValue(mapOf(
                "bass" to VendorFeatureValue.IntValue(5),
                "treble" to VendorFeatureValue.IntValue(5),
                "sub" to VendorFeatureValue.IntValue(5),
            )),
        )
        assertTrue(unexpected is ValueValidationResult.Invalid)

        val ok = f.validateValue(
            VendorFeatureValue.StructuredValue(mapOf(
                "bass" to VendorFeatureValue.IntValue(5),
                "treble" to VendorFeatureValue.IntValue(5),
            )),
        )
        assertTrue(ok.isValid())
    }

    @Test
    fun `list length bounded`() {
        val f = VendorFeatureDefinition(
            id = VendorFeatureId("vendor.acme.gestures"),
            extensionId = VendorExtensionId("ext.acme.budsproto"),
        namespace = "acme.buds",
            canonicalName = "gestures",
            category = "input",
            valueType = VendorValueType.LIST,
            constraints = ValueConstraints(maxListLength = 3),
            writable = true,
        )
        val tooMany = f.validateValue(
            VendorFeatureValue.ListValue(List(4) { VendorFeatureValue.IntValue(it) }),
        )
        assertTrue(tooMany is ValueValidationResult.Invalid)
    }

    @Test
    fun `default value must satisfy constraints`() {
        try {
            VendorFeatureDefinition(
                id = VendorFeatureId("vendor.acme.volume"),
                extensionId = VendorExtensionId("ext.acme.budsproto"),
        namespace = "acme.buds",
                canonicalName = "volume",
                category = "audio",
                valueType = VendorValueType.INT,
                constraints = ValueConstraints(minInt = 0, maxInt = 100),
                defaultValue = VendorFeatureValue.IntValue(150),
            )
            assertTrue(false, "should have thrown")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("default value"))
        }
    }

    @Test
    fun `float boundaries`() {
        val f = VendorFeatureDefinition(
            id = VendorFeatureId("vendor.acme.balance"),
            extensionId = VendorExtensionId("ext.acme.budsproto"),
        namespace = "acme.buds",
            canonicalName = "balance",
            category = "audio",
            valueType = VendorValueType.FLOAT,
            constraints = ValueConstraints(minFloat = -1.0, maxFloat = 1.0),
            writable = true,
        )
        assertTrue(f.validateValue(VendorFeatureValue.FloatValue(0.5)).isValid())
        assertTrue(f.validateValue(VendorFeatureValue.FloatValue(1.5)) is ValueValidationResult.Invalid)
    }
}
