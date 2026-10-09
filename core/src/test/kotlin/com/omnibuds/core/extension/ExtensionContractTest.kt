package com.omnibuds.core.extension

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phase 23: extension contract and identifier tests.
 */
class ExtensionContractTest {

    @Test
    fun `valid feature identifier`() {
        val id = VendorFeatureId("vendor.acme.buds.ancplus")
        assertEquals("acme", id.manufacturer)
        assertEquals("buds", id.family)
        assertEquals("ancplus", id.feature)
    }

    @Test
    fun `invalid feature identifier rejected`() {
        assertFailsWith<IllegalArgumentException> {
            VendorFeatureId("acme.anc")
        }
        assertFailsWith<IllegalArgumentException> {
            VendorFeatureId("vendor.acme.buds")
        }
        assertFailsWith<IllegalArgumentException> {
            VendorFeatureId("vendor.Acme.Buds.ANC")
        }
    }

    @Test
    fun `identifier built from parts`() {
        val id = VendorFeatureId.of("acme", "buds", "ancplus")
        assertNotNull(id)
        assertEquals("vendor.acme.buds.ancplus", id.value)
        assertNull(VendorFeatureId.of("Acme", "buds", "ancplus"))
    }

    @Test
    fun `extension identifier validated`() {
        val id = VendorExtensionId("ext.acme.budsproto")
        assertEquals("ext.acme.budsproto", id.value)
        assertFailsWith<IllegalArgumentException> {
            VendorExtensionId("acme.budsproto")
        }
    }

    @Test
    fun `descriptor requires version and manufacturer`() {
        assertFailsWith<IllegalArgumentException> {
            VendorExtensionDescriptor(
                id = VendorExtensionId("ext.acme.p"),
                manufacturerId = "",
                protocolFamily = "acme-rfcomm",
                version = "1.0",
            )
        }
    }

    @Test
    fun `trust levels are ordered`() {
        val levels = ExtensionTrustLevel.entries
        assertTrue(levels.indexOf(ExtensionTrustLevel.DESCRIPTIVE) <
            levels.indexOf(ExtensionTrustLevel.VERIFIED))
    }
}
