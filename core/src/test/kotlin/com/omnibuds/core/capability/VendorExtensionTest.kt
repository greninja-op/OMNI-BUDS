package com.omnibuds.core.capability

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.state.CapabilityState
import com.omnibuds.core.state.VerificationLevel
import com.omnibuds.core.common.TransportKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The boundary between universal and manufacturer-specific, enforced by construction
 * (phase-1 prompt sections 20, 21 and 24, protocol-governance section 11).
 *
 * The shape being protected: `vendor.<vendor>.<feature>` is the only identity a vendor
 * extension may carry, and it must name the same vendor its metadata does. That single rule
 * is what stops a Sony-only behaviour from being registered as a universal feature — and
 * what stops a vendor behaviour from being filed over the top of a core identity and
 * quietly redefining it.
 *
 * Note that none of these tests upgrades a claim: a vendor extension that exists in the
 * catalogue still reports `UNKNOWN` about the device in front of it, because being
 * advertised is not being read back (PROTO-VENDOR-003).
 */
class VendorExtensionTest {

    private val adaptiveSoundControl: FeatureId = FeatureId.ofVendor("sony", "adaptive-sound-control")

    private val sonyMetadata: VendorFeatureMetadata = VendorFeatureMetadata(
        vendor = "sony",
        vendorFeatureName = "Adaptive Sound Control",
        documentation = "Advertised by the manufacturer's own app; no read from a device is recorded.",
        confidence = VerificationLevel.INFERRED,
    )

    private fun capability(feature: FeatureId, state: CapabilityState): FeatureCapability {
        val affordances = when (state) {
            CapabilityState.UNKNOWN,
            CapabilityState.UNSUPPORTED,
            -> false to false

            CapabilityState.READ_ONLY -> true to false

            CapabilityState.SUPPORTED_VOLATILE,
            CapabilityState.SUPPORTED_PERSISTENT,
            CapabilityState.PERSISTENCE_VERIFIED,
            -> true to true
        }
        return FeatureCapability(
            feature = feature,
            state = state,
            readable = affordances.first,
            writable = affordances.second,
            transport = TransportKind.VENDOR_SPECIFIC,
            protocolId = null,
            requiresConnection = true,
            verification = VerificationLevel.INFERRED,
        )
    }

    /**
     * The documented vendor id shape is accepted and recognised, and the extension reports
     * the identity it was built from.
     *
     * Both the guard's own check and the kernel property are asserted: the kernel
     * `isVendorExtension` implementation was repaired during this phase (it once compared
     * the whole namespace `vendor.sony` against `"vendor"`, so it could never fire for the
     * ids `FeatureId.ofVendor` produces), and pinning the positive case here is what stops
     * that regression from returning unnoticed. [CoreFeatureTest] asserts the negative case
     * for universal identities.
     */
    @Test
    fun aVendorNamespacedIdentityIsAcceptedAndRecognised() {
        assertTrue(VendorExtension.isVendorFeature(adaptiveSoundControl))
        assertTrue(adaptiveSoundControl.isVendorExtension)
        assertEquals("sony", adaptiveSoundControl.vendorName)
        assertEquals("sony", VendorExtension.vendorSegmentOf(adaptiveSoundControl))

        val extension = VendorExtension(
            metadata = sonyMetadata,
            capability = capability(adaptiveSoundControl, CapabilityState.UNKNOWN),
            payload = null,
        )

        assertEquals(adaptiveSoundControl, extension.feature)
        assertEquals("sony", extension.metadata.vendor)
        assertNull(extension.payload)
    }

    /**
     * A core-style identity cannot be carried as a vendor extension.
     *
     * This is the guard against "the vendor's version of ANC is now everybody's ANC": the
     * shared identity is untouched, and the vendor behaviour has to be filed under its own
     * namespace where it can be described honestly.
     */
    @Test
    fun aCoreStyleIdentityIsRejectedAsAVendorExtension() {
        val coreCapability = capability(CoreFeature.ANC, CapabilityState.SUPPORTED_VOLATILE)

        val failure = assertFailsWith<IllegalArgumentException> {
            VendorExtension(metadata = sonyMetadata, capability = coreCapability, payload = null)
        }
        assertTrue(
            failure.message?.contains(CoreFeature.ANC.qualifiedName) == true,
            "the rejection should name the identity it refused",
        )
    }

    /** A vendor cannot claim another vendor's namespace, even when the shape is right. */
    @Test
    fun aVendorMismatchBetweenMetadataAndIdentityIsRejected() {
        val borrowed = sonyMetadata.copy(vendor = "bose", vendorFeatureName = "Adaptive Sound Control")

        assertFailsWith<IllegalArgumentException> {
            VendorExtension(
                metadata = borrowed,
                capability = capability(adaptiveSoundControl, CapabilityState.UNKNOWN),
                payload = null,
            )
        }
    }

    /**
     * The reserved root alone is not a vendor extension: an id has to name whose feature it
     * is. `vendor.<feature>` would otherwise let a vendor claim the whole reserved
     * namespace.
     */
    @Test
    fun anIdentityUnderTheReservedRootThatNamesNoVendorIsRejected() {
        val rootOnly = FeatureId.of(VendorExtension.VENDOR_ROOT, "adaptive-sound-control")

        assertFailsWith<IllegalArgumentException> {
            VendorExtension(metadata = sonyMetadata, capability = capability(rootOnly, CapabilityState.UNKNOWN))
        }
    }

    /**
     * Known-to-exist but unmodelled stays `UNKNOWN`, recorded rather than dropped, and is
     * never offered as a control (PROTO-VENDOR-002, PROTO-VENDOR-003).
     */
    @Test
    fun anAdvertisedVendorFeatureIsStillUnknownOnThisDevice() {
        val extension = VendorExtension(
            metadata = sonyMetadata,
            capability = capability(adaptiveSoundControl, CapabilityState.UNKNOWN),
            payload = "described in product marketing; unmapped",
        )

        assertEquals(CapabilityState.UNKNOWN, extension.capability.state)
        assertFalse(extension.capability.isControllable)
        assertFalse(extension.capability.readable)
        assertFalse(extension.capability.writable)

        val discovered = DeviceCapabilities.empty().with(adaptiveSoundControl, extension.capability)
        assertEquals(CapabilityState.UNKNOWN, discovered.stateOf(adaptiveSoundControl))
        assertTrue(discovered.controllable.isEmpty())
        assertTrue(discovered.unsupported.isEmpty(), "unmodelled is not unsupported")
    }

    /**
     * A vendor record cannot be filed over a core identity in a container either: the key
     * and the record must agree, so a vendor feature cannot displace the universal one.
     */
    @Test
    fun aVendorRecordCannotOverwriteACoreFeatureInASnapshot() {
        val extension = VendorExtension(
            metadata = sonyMetadata,
            capability = capability(adaptiveSoundControl, CapabilityState.SUPPORTED_VOLATILE),
            payload = null,
        )

        assertFailsWith<IllegalArgumentException> {
            DeviceCapabilities.empty().with(CoreFeature.ANC, extension.capability)
        }
        assertFailsWith<IllegalArgumentException> {
            DeviceCapabilities.empty().with(CoreFeature.ADAPTIVE_ANC, extension.capability)
        }
    }

    /** The payload is opaque text for the layer that owns it, never protocol content. */
    @Test
    fun thePayloadStaysOpaqueAndIsOptional() {
        val withPayload = VendorExtension(
            metadata = sonyMetadata,
            capability = capability(adaptiveSoundControl, CapabilityState.READ_ONLY),
            payload = "scene names reported by the manufacturer, held as text",
        )
        val withoutPayload = VendorExtension(
            metadata = sonyMetadata,
            capability = capability(adaptiveSoundControl, CapabilityState.READ_ONLY),
        )

        assertEquals("scene names reported by the manufacturer, held as text", withPayload.payload)
        assertNull(withoutPayload.payload)
    }

    /**
     * Metadata refuses a vendor value that could never match an id segment, because a
     * mismatch that cannot be noticed is a guard that silently never fires.
     */
    @Test
    fun metadataRejectsAVendorValueThatCouldNotMatchAnIdentity() {
        listOf("Sony", "SONY", "  ", "sony audio", "1more", "sony_", "").forEach { candidate ->
            assertFailsWith<IllegalArgumentException> {
                VendorFeatureMetadata(
                    vendor = candidate,
                    vendorFeatureName = null,
                    documentation = null,
                    confidence = VerificationLevel.INFERRED,
                )
            }
        }
    }

    /** Every protocol family in the target tree must remain registrable. */
    @Test
    fun metadataAcceptsTheVendorNamespacesTheRepositoryLists() {
        listOf(
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
        ).forEach { vendor ->
            val metadata = VendorFeatureMetadata(
                vendor = vendor,
                vendorFeatureName = null,
                documentation = null,
                confidence = VerificationLevel.INFERRED,
            )
            val feature = FeatureId.ofVendor(vendor, "adaptive-sound-control")

            assertEquals(vendor, VendorExtension.vendorSegmentOf(feature))
            assertTrue(VendorExtension.isVendorFeature(feature))
            assertEquals(
                feature,
                VendorExtension(metadata, capability(feature, CapabilityState.UNKNOWN)).feature,
            )
        }
    }

    /** Blank optional fields are refused the same way blank protocol ids are. */
    @Test
    fun metadataRejectsBlankTextWhereNullIsTheHonestAnswer() {
        assertFailsWith<IllegalArgumentException> {
            VendorFeatureMetadata("sony", "   ", null, VerificationLevel.INFERRED)
        }
        assertFailsWith<IllegalArgumentException> {
            VendorFeatureMetadata("sony", null, "   ", VerificationLevel.INFERRED)
        }

        val honest = VendorFeatureMetadata("sony", null, null, VerificationLevel.INFERRED)
        assertNull(honest.vendorFeatureName)
        assertNull(honest.documentation)
    }
}
