package com.omnibuds.core.capability

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.state.CapabilityState
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The identity registry, checked as a contract rather than as a list (phase-1 prompt
 * sections 14, 20 and 21, specs section 1.5).
 *
 * What is actually being defended: one stable name per feature, functional namespaces
 * only, no vendor able to pose as universal, and a documentation catalogue that cannot
 * drift away from the identities it describes. None of these tests assert that any device
 * supports anything — presence in a catalogue is precisely not that.
 */
class CoreFeatureTest {

    /** Namespace roots that describe a function rather than a manufacturer. */
    private val functionalNamespaces: Set<String> = setOf(
        "noise-control",
        "equalization",
        "input",
        "sensing",
        "connectivity",
        "power",
        "audio-quality",
        "firmware",
    )

    /** Brand names, taken from the protocol families listed in the Phase 1 target tree. */
    private val brandNames: Set<String> = setOf(
        "apple",
        "bose",
        "jbl",
        "motorola",
        "nothing",
        "oneplus",
        "oppo",
        "samsung",
        "sony",
        "soundcore",
        "soundpeats",
    )

    /** The namespace a category implies, or `null` where the category is not universal. */
    private fun namespaceOf(category: FeatureCategory): String? = when (category) {
        FeatureCategory.NOISE_CONTROL -> "noise-control"
        FeatureCategory.EQUALIZATION -> "equalization"
        FeatureCategory.INPUT -> "input"
        FeatureCategory.SENSING -> "sensing"
        FeatureCategory.CONNECTIVITY -> "connectivity"
        FeatureCategory.POWER -> "power"
        FeatureCategory.AUDIO_QUALITY -> "audio-quality"
        FeatureCategory.FIRMWARE -> "firmware"
        FeatureCategory.VENDOR -> null
    }

    private fun namedIdentities(): List<FeatureId> = listOf(
        CoreFeature.ANC,
        CoreFeature.TRANSPARENCY,
        CoreFeature.ADAPTIVE_ANC,
        CoreFeature.EQUALIZER,
        CoreFeature.GESTURES,
        CoreFeature.WEAR_DETECTION,
        CoreFeature.SPATIAL_AUDIO,
        CoreFeature.HEAD_TRACKING,
        CoreFeature.MULTIPOINT,
        CoreFeature.BATTERY,
        CoreFeature.CASE_BATTERY,
        CoreFeature.FIRMWARE_INFO,
        CoreFeature.GAMING_MODE,
        CoreFeature.VOICE_PROMPTS,
        CoreFeature.SIDETONE,
    )

    @Test
    fun allFifteenCoreIdentitiesAreRegisteredAndUnique() {
        val named = namedIdentities()

        assertEquals(15, named.size)
        assertEquals(15, named.distinct().size, "two core names must not point at one identity")
        assertEquals(15, CoreFeature.all.size)
        assertEquals(15, CoreFeature.all.distinct().size)
        assertContentEquals(named.sortedBy { it.qualifiedName }, CoreFeature.all.sortedBy { it.qualifiedName })
        named.forEach { assertTrue(it in CoreFeature.all, "${it.qualifiedName} is missing from all") }
        named.forEach { assertTrue(CoreFeature.isCore(it), "${it.qualifiedName} must report itself as core") }
    }

    /**
     * No core identity may be carried as a vendor extension, and none is shaped like one.
     *
     * Section 14's list ends with "Vendor Extensions" and section 20's does not: vendor
     * functionality is deliberately outside the core registry and represented by
     * [VendorExtension] instead, which is what keeps a manufacturer's feature from being
     * presented as a universal one.
     */
    @Test
    fun noCoreIdentityIsAVendorExtension() {
        CoreFeature.all.forEach { feature ->
            assertFalse(feature.isVendorExtension, "${feature.qualifiedName} is universal, not vendor-scoped")
            assertFalse(
                VendorExtension.isVendorFeature(feature),
                "${feature.qualifiedName} must not fit a vendor id",
            )
            assertNull(VendorExtension.vendorSegmentOf(feature), "${feature.qualifiedName} names no vendor")
        }
        assertFalse(CoreFeature.isCore(FeatureId.ofVendor("sony", "adaptive-sound-control")))
    }

    @Test
    fun everyCoreNamespaceIsFunctionalAndNeverABrand() {
        CoreFeature.all.forEach { feature ->
            assertTrue(
                feature.namespace in functionalNamespaces,
                "${feature.qualifiedName} sits under '${feature.namespace}', which is not a functional area",
            )
            assertFalse(feature.namespace in brandNames, "${feature.qualifiedName} is branded")
            assertFalse(feature.namespace.contains('.'), "a core id is one namespace and one name")
            assertTrue(feature.localName.isNotBlank(), "${feature.qualifiedName} has no name")
            assertEquals(2, feature.qualifiedName.split('.').size, "${feature.qualifiedName} is not two segments")
        }
    }

    @Test
    fun definitionsCoverTheIdentityListExactlyOnce() {
        val defined = CoreFeature.definitions.map { it.feature }

        assertEquals(CoreFeature.all.size, defined.size, "one definition per identity, counted once each")
        assertEquals(CoreFeature.all.toSet(), defined.toSet(), "the two catalogues describe the same features")
        assertEquals(defined.size, defined.distinct().size, "no identity is defined twice")
        defined.forEach { assertTrue(CoreFeature.isCore(it), "definition for a non-core identity: $it") }
        CoreFeature.all.forEach { feature ->
            assertTrue(feature in defined, "${feature.qualifiedName} has no definition")
        }
    }

    @Test
    fun everyDefinitionSitsInTheCategoryItsNamespaceImplies() {
        CoreFeature.definitions.forEach { definition ->
            assertEquals(
                namespaceOf(definition.category),
                definition.feature.namespace,
                "${definition.feature.qualifiedName} is filed under ${definition.category}",
            )
            assertNotEquals(FeatureCategory.VENDOR, definition.category, "a core feature is never vendor-only")
            assertTrue(definition.displayName.isNotBlank(), "${definition.feature.qualifiedName} has no label")
        }
    }

    @Test
    fun displayNamesAreLabelsAndAreNotReusedAcrossTheCatalogue() {
        val labels = CoreFeature.definitions.map { it.displayName }

        assertEquals(labels.size, labels.distinct().size, "two features sharing one label invites the wrong lookup")
        labels.forEach { assertTrue(it.isNotBlank(), "a blank label is not a label") }
    }

    /** The nine areas are a contract; adding one changes capability semantics. */
    @Test
    fun theCategoryVocabularyIsTheContractedNine() {
        assertContentEquals(
            listOf(
                FeatureCategory.NOISE_CONTROL,
                FeatureCategory.EQUALIZATION,
                FeatureCategory.INPUT,
                FeatureCategory.SENSING,
                FeatureCategory.CONNECTIVITY,
                FeatureCategory.POWER,
                FeatureCategory.AUDIO_QUALITY,
                FeatureCategory.FIRMWARE,
                FeatureCategory.VENDOR,
            ),
            FeatureCategory.entries.toList(),
        )
    }

    @Test
    fun isCoreSeparatesCoreFromUnrelatedAndVendorIdentities() {
        assertTrue(CoreFeature.isCore(CoreFeature.BATTERY))
        assertTrue(CoreFeature.isCore(CoreFeature.CASE_BATTERY))
        assertFalse(CoreFeature.isCore(FeatureId.of("power", "case-battery-temperature")))
        assertFalse(CoreFeature.isCore(FeatureId.ofVendor("bose", "adaptive-noise-cancellation")))
        assertFalse(CoreFeature.isCore(FeatureId.of(VendorExtension.VENDOR_ROOT, "adaptive-sound-control")))
    }

    /**
     * A hint documents the shape a feature commonly takes across devices. Nothing in this
     * catalogue may read as a support claim: listing a feature must leave a device's
     * standing for it `UNKNOWN`, and a hint must never become a record's state.
     */
    @Test
    fun aSupportedValueHintIsDocumentationAndNeverABuiltInSupportClaim() {
        val unexamined = DeviceCapabilities.empty()

        CoreFeature.definitions.forEach { definition ->
            assertEquals(
                CapabilityState.UNKNOWN,
                unexamined.stateOf(definition.feature),
                "${definition.feature.qualifiedName} is listed, therefore not yet known",
            )
            assertNotEquals(CapabilityState.UNSUPPORTED, unexamined.stateOf(definition.feature))
        }
        assertTrue(unexamined.controllable.isEmpty(), "a catalogue is not a discovery result")
        assertTrue(unexamined.unsupported.isEmpty(), "listing features is not ruling them out")
    }
}
