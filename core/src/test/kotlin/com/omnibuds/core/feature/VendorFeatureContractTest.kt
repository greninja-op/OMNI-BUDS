package com.omnibuds.core.feature

import com.omnibuds.core.capability.FeatureCategory
import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.config.ConfigurationValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The vendor extension contract at the feature layer.
 *
 * Vendor uniqueness must survive the generic engine: a manufacturer's feature
 * stays addressable under its own namespace, rides the same value shapes and
 * validation as core features, and never becomes a branch in shared code. No
 * real vendor is named anywhere in these tests — only the `example-vendor`
 * placeholder — and `PhaseNineScopeTest` enforces the same for main sources.
 */
class VendorFeatureContractTest {

    @Test
    fun defineCreatesAVendorNamespacedDefinition() {
        val definition = VendorFeatureContract.define(
            vendor = "example-vendor",
            feature = "auto-transparency",
            displayName = "Automatic transparency",
            valueType = FeatureValueType.BOOLEAN,
        )
        assertEquals(FeatureId.ofVendor("example-vendor", "auto-transparency"), definition.feature)
        assertEquals(FeatureCategory.VENDOR, definition.category)
        assertEquals("example-vendor", definition.vendor)
        assertTrue(VendorFeatureContract.isVendorFeature(definition.feature))
        assertFalse(VendorFeatureContract.isVendorFeature(StandardFeatures.ANC.feature))
    }

    @Test
    fun vendorDefinitionsValidateValuesLikeCoreOnes() {
        val definition = VendorFeatureContract.define(
            vendor = "example-vendor",
            feature = "gaming-mode",
            displayName = "Gaming mode",
            valueType = FeatureValueType.ENUM,
            constraints = FeatureConstraints(allowedModes = setOf("off", "low-latency")),
        )
        assertNull(
            definition.validateValue(ConfigurationValue.ModeValue("low-latency", "Low latency")),
        )
        assertEquals(
            FeatureErrorCode.INVALID_VALUE,
            definition.validateValue(ConfigurationValue.ModeValue("turbo", "Turbo")),
        )
    }

    @Test
    fun vendorOpaquePayloadsRideAsCustomValues() {
        val definition = VendorFeatureContract.define(
            vendor = "example-vendor",
            feature = "sound-profile",
            displayName = "Sound profile",
            valueType = FeatureValueType.CUSTOM,
            constraints = FeatureConstraints(maxEntries = 256),
        )
        assertNull(definition.validateValue(ConfigurationValue.CustomValue("opaque-blob")))
        assertEquals(
            FeatureErrorCode.INVALID_VALUE,
            definition.validateValue(ConfigurationValue.CustomValue("x".repeat(257))),
        )
    }

    @Test
    fun vendorIdentitiesMustBeWellFormed() {
        // Uppercase or blank segments are refused by FeatureId's grammar.
        assertFailsWith<IllegalArgumentException> {
            VendorFeatureContract.define(
                vendor = "Example-Vendor",
                feature = "mode",
                displayName = "Mode",
                valueType = FeatureValueType.BOOLEAN,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            VendorFeatureContract.define(
                vendor = "",
                feature = "mode",
                displayName = "Mode",
                valueType = FeatureValueType.BOOLEAN,
            )
        }
    }

    @Test
    fun vendorRelationsParticipateInDependencyEvaluation() {
        val vendorFeature = FeatureId.ofVendor("example-vendor", "adaptive-sound")
        val definition = VendorFeatureContract.define(
            vendor = "example-vendor",
            feature = "adaptive-sound",
            displayName = "Adaptive sound",
            valueType = FeatureValueType.BOOLEAN,
            relations = listOf(
                FeatureRelation.Requires(vendorFeature, StandardFeatures.ANC.feature),
            ),
        )
        val report = FeatureDependencyEvaluator.evaluate(
            definition,
            FeatureTestFixtures.snapshot(
                FeatureTestFixtures.record(
                    vendorFeature,
                    com.omnibuds.core.state.CapabilityState.SUPPORTED_VOLATILE,
                ),
            ).capabilities,
            emptyMap(),
        )
        // ANC is unknown: the write is refused, exactly as for a core feature.
        assertFalse(report.mayWrite)
        assertTrue(report.mayRead)
    }
}
