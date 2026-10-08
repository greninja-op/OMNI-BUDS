package com.omnibuds.core.feature

import com.omnibuds.core.capability.FeatureCategory
import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.config.ConfigurationValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The standard catalogue's integrity and the EQ/gesture value contracts.
 *
 * The catalogue is vocabulary, not a support claim: these tests pin its shape
 * — every definition valid, every feature defined once, relations referencing
 * known features, value constructors producing values the definitions accept —
 * without asserting anything about any device.
 */
class StandardFeaturesTest {

    @Test
    fun everyDefinitionIsValidAndUnique() {
        val features = StandardFeatures.all.map { it.feature }
        assertEquals(features.size, features.toSet().size, "a feature must not be defined twice")
        assertTrue(StandardFeatures.all.isNotEmpty())
        for (definition in StandardFeatures.all) {
            assertTrue(definition.displayName.isNotBlank())
        }
    }

    @Test
    fun theMapAgreesWithTheList() {
        assertEquals(StandardFeatures.all.size, StandardFeatures.asMap.size)
        for (definition in StandardFeatures.all) {
            assertEquals(definition, StandardFeatures.asMap[definition.feature])
        }
    }

    @Test
    fun relationsReferenceKnownFeatures() {
        val known = StandardFeatures.all.map { it.feature }.toSet()
        for (definition in StandardFeatures.all) {
            for (relation in definition.relations) {
                val others = when (relation) {
                    is FeatureRelation.Requires -> listOf(relation.prerequisite)
                    is FeatureRelation.RequiresOneOf -> relation.options
                    is FeatureRelation.ConflictsWith -> listOf(relation.other)
                    is FeatureRelation.MutuallyExclusive -> relation.others
                    is FeatureRelation.Implies -> listOf(relation.implied)
                    is FeatureRelation.VendorException -> emptyList()
                }
                for (other in others) {
                    assertTrue(
                        other in known,
                        "${definition.feature.qualifiedName} relates to unknown feature " +
                            other.qualifiedName,
                    )
                }
            }
        }
    }

    @Test
    fun noStandardDefinitionIsVendorNamespaced() {
        for (definition in StandardFeatures.all) {
            assertTrue(!definition.feature.isVendorExtension, "${definition.feature.qualifiedName} leaked vendor scope")
            assertNull(definition.vendor)
            assertTrue(definition.category != FeatureCategory.VENDOR)
        }
    }

    @Test
    fun ancLevelRequiresAnc() {
        val relations = StandardFeatures.ANC_LEVEL.relations
        assertEquals(1, relations.size)
        val requires = relations.single() as FeatureRelation.Requires
        assertEquals(StandardFeatures.ANC.feature, requires.prerequisite)
    }

    @Test
    fun eqPresetValuesValidateAgainstTheEqContract() {
        val value = EqualizerValues.preset("bass-boost")
        assertNull(StandardFeatures.EQUALIZER.validateValue(value))

        val graphic = EqualizerValues.graphic(
            listOf(
                EqualizerValues.graphicBand(60.0, 3.0),
                EqualizerValues.graphicBand(1000.0, -1.5),
                EqualizerValues.graphicBand(12000.0, 2.0),
            ),
        )
        assertNull(StandardFeatures.EQUALIZER.validateValue(graphic))

        val parametric = EqualizerValues.parametric(
            listOf(EqualizerValues.parametricBand(120.0, 4.0, 1.2, "peaking")),
        )
        assertNull(StandardFeatures.EQUALIZER.validateValue(parametric))
    }

    @Test
    fun eqValuesSupportVariableBandCounts() {
        val oneBand = EqualizerValues.graphic(listOf(EqualizerValues.graphicBand(100.0, 2.0)))
        val tenBands = EqualizerValues.graphic(
            (1..10).map { EqualizerValues.graphicBand(it * 100.0, 0.0) },
        )
        assertNull(StandardFeatures.EQUALIZER.validateValue(oneBand))
        assertNull(StandardFeatures.EQUALIZER.validateValue(tenBands))
    }

    @Test
    fun eqValuesRefuseInvalidFrequenciesAndGains() {
        // NaN/Infinity never survive construction — they cannot reach validation.
        assertFailsWith<IllegalArgumentException> {
            EqualizerValues.graphicBand(Double.NaN, 3.0)
        }
        assertFailsWith<IllegalArgumentException> {
            EqualizerValues.parametricBand(120.0, Double.POSITIVE_INFINITY, 1.0, "peaking")
        }
        assertFailsWith<IllegalArgumentException> {
            EqualizerValues.graphic(emptyList())
        }
    }

    @Test
    fun gestureValuesValidateAgainstTheGestureContract() {
        val value = GestureValues.configuration(
            listOf(
                GestureValues.assignment("double-tap", "left", "play-pause"),
                GestureValues.assignment("long-press", "right", "anc"),
                // Vendor-specific actions ride the same shape.
                GestureValues.assignment("triple-tap", "left", "custom-vendor-action"),
            ),
        )
        assertNull(StandardFeatures.GESTURES.validateValue(value))
    }

    @Test
    fun gestureValuesRefuseBlankParts() {
        assertFailsWith<IllegalArgumentException> { GestureValues.assignment("", "left", "play-pause") }
        assertFailsWith<IllegalArgumentException> { GestureValues.assignment("double-tap", "", "play-pause") }
        assertFailsWith<IllegalArgumentException> { GestureValues.assignment("double-tap", "left", "") }
        assertFailsWith<IllegalArgumentException> { GestureValues.configuration(emptyList()) }
    }

    @Test
    fun theKnownGestureVocabularyIsDocumentedNotEnforced() {
        // The definition deliberately does not close the action set: the engine
        // must accept a vendor action it has never heard of.
        assertTrue("double-tap" in StandardFeatures.KNOWN_GESTURES)
        assertTrue("left" in StandardFeatures.KNOWN_GESTURE_SIDES)
        assertTrue("play-pause" in StandardFeatures.KNOWN_GESTURE_ACTIONS)
        val unknownAction = GestureValues.configuration(
            listOf(GestureValues.assignment("double-tap", "left", "some-future-action")),
        )
        assertNull(StandardFeatures.GESTURES.validateValue(unknownAction))
    }

    @Test
    fun ancModeAcceptsOnlyItsDocumentedModes() {
        val definition = StandardFeatures.ANC_MODE
        assertNull(
            definition.validateValue(ConfigurationValue.ModeValue("adaptive", "Adaptive")),
        )
        assertEquals(
            FeatureErrorCode.INVALID_VALUE,
            definition.validateValue(ConfigurationValue.ModeValue("turbo", "Turbo")),
        )
        // The inactive mode follows the INACTIVE_MODE_NAME convention.
        assertTrue("off" in (definition.constraints?.allowedModes ?: emptySet()))
        assertEquals("off", INACTIVE_MODE_NAME)
    }

    @Test
    fun vendorDefinitionsCannotMasqueradeAsCore() {
        assertFailsWith<IllegalArgumentException> {
            FeatureDefinition(
                feature = FeatureId.ofVendor("example-vendor", "gaming-mode"),
                displayName = "Sneaky",
                category = FeatureCategory.CONNECTIVITY,
                valueType = FeatureValueType.BOOLEAN,
                vendor = null, // vendor-namespaced id without a vendor: refused
            )
        }
        assertFailsWith<IllegalArgumentException> {
            FeatureDefinition(
                feature = FeatureId.of("connectivity", "gaming-mode"),
                displayName = "Sneaky",
                category = FeatureCategory.VENDOR,
                valueType = FeatureValueType.BOOLEAN,
                vendor = "example-vendor", // vendor claim on a core id: refused
            )
        }
    }
}
