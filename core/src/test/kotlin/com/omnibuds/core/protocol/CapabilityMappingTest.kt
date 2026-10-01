package com.omnibuds.core.protocol

import com.omnibuds.core.common.FeatureId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [CapabilityMapping] as the only place a feature and a command may meet
 * (PROTO-NOMAGIC-001/003, PROTO-VENDOR-002/003, specs.md section 1.5, master section 53).
 *
 * Tier T1. The vendor half of the fixture is an obviously fictional brand; no real vendor
 * is named or characterised anywhere in this file.
 */
class CapabilityMappingTest {

    @Test
    fun aWriteCommandWithoutAnEffectClassIsUnconstructible() {
        assertFailsWith<IllegalArgumentException> {
            CapabilityMapping(
                feature = ANC,
                readCommandId = "readAncMode",
                writeCommandId = "writeAncMode",
                effectClass = null,
            )
        }
    }

    @Test
    fun aMappingWithNeitherCommandIsRefusedBecauseUnmodelledMeansNoMappingAtAll() {
        assertFailsWith<IllegalArgumentException> {
            CapabilityMapping(feature = ANC, readCommandId = null, writeCommandId = null, effectClass = null)
        }
        // The empty mapping would look like knowledge in a listing while pointing at
        // nothing; absence from the map is the correct encoding of "unmodelled".
        assertFailsWith<IllegalArgumentException> {
            CapabilityMapping(ANC, null, null, EffectClass.READ)
        }
    }

    @Test
    fun aWriteCommandNeverCarriesTheReadEffectClass() {
        assertFailsWith<IllegalArgumentException> {
            CapabilityMapping(
                feature = ANC,
                readCommandId = "readAncMode",
                writeCommandId = "writeAncMode",
                effectClass = EffectClass.READ,
            )
        }
    }

    @Test
    fun aWriteEffectClassWithoutAWriteCommandIsRefused() {
        assertFailsWith<IllegalArgumentException> {
            CapabilityMapping(
                feature = ANC,
                readCommandId = "readAncMode",
                writeCommandId = null,
                effectClass = EffectClass.SIDE_EFFECTING_WRITE,
            )
        }
    }

    @Test
    fun aReadOnlyMappingIsTheSameShapeAsTheLaddersReadOnlyRung() {
        val readOnly = CapabilityMapping(
            feature = BATTERY,
            readCommandId = "readExampleBatteryLevel",
            writeCommandId = null,
            effectClass = EffectClass.READ,
        )

        assertTrue(readOnly.hasReadCommand)
        assertFalse(readOnly.hasWriteCommand)
        assertTrue(readOnly.permitsAutomaticRetry)
        // A writable protocol record cannot be implied by a device that merely reports.
        assertNull(readOnly.writeCommandId)
    }

    @Test
    fun aReadWriteMappingGatesItsRetryOnTheDeclaredEffect() {
        val mapping = CapabilityMapping(
            feature = ANC,
            readCommandId = "readAncMode",
            writeCommandId = "writeAncMode",
            effectClass = EffectClass.SIDE_EFFECTING_WRITE,
        )

        assertTrue(mapping.hasWriteCommand)
        assertFalse(mapping.permitsAutomaticRetry)
    }

    @Test
    fun anIrreversibleWriteIsExpressibleAndIsNeverAutomaticallyRepeated() {
        val mapping = CapabilityMapping(
            feature = FeatureId.of("firmware", "example-reset"),
            readCommandId = null,
            writeCommandId = "writeExampleFactoryReset",
            effectClass = EffectClass.IRREVERSIBLE_WRITE,
        )

        assertFalse(mapping.permitsAutomaticRetry)
        assertEquals(EffectClass.IRREVERSIBLE_WRITE, mapping.effectClass)
        assertNull(mapping.readCommandId)
    }

    @Test
    fun aMappingWithoutAnEffectClassIsAllowedForAReadPath() {
        val minimal = CapabilityMapping(
            feature = ANC,
            readCommandId = "readAncMode",
            writeCommandId = null,
            effectClass = null,
        )

        assertFalse(minimal.permitsAutomaticRetry)
        assertFalse(minimal.hasWriteCommand)
    }

    @Test
    fun aVendorFeatureStaysSeparateFromACoreFeature() {
        val core = CapabilityMapping(ANC, "readAncMode", null, EffectClass.READ)
        val vendor = CapabilityMapping(VENDOR_FEATURE, "readExampleVendorMode", null, EffectClass.READ)

        assertFalse(core.isVendorExtension)
        assertTrue(vendor.isVendorExtension)
        assertNull(core.feature.vendorName)
        assertEquals("example-vendor", vendor.feature.vendorName)
        assertNotEquals(core.feature, vendor.feature)
        assertEquals(ANC, FeatureId.of("noise-control", "anc"))
    }

    @Test
    fun aBlankCommandIdIsNotTheSameThingAsNoCommand() {
        assertFailsWith<IllegalArgumentException> {
            CapabilityMapping(feature = ANC, readCommandId = " ", writeCommandId = null, effectClass = null)
        }
        assertFailsWith<IllegalArgumentException> {
            CapabilityMapping(feature = ANC, readCommandId = null, writeCommandId = "  ", effectClass = null)
        }
        val readOnly = CapabilityMapping(
            feature = ANC,
            readCommandId = "readAncMode",
            writeCommandId = null,
            effectClass = null,
        )
        assertNull(readOnly.writeCommandId)
    }

    private companion object {
        val ANC: FeatureId = FeatureId.of("noise-control", "anc")
        val BATTERY: FeatureId = FeatureId.of("power", "battery")
        val VENDOR_FEATURE: FeatureId = FeatureId.ofVendor("example-vendor", "some-feature")
    }
}
