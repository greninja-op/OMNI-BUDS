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
        val id = VendorFeatureId("vendor.acme.anc-plus")
        assertEquals("acme", id.vendor)
        assertEquals("anc-plus", id.feature)
    }

    @Test
    fun `invalid feature identifier rejected`() {
        assertFailsWith<IllegalArgumentException> {
            VendorFeatureId("acme.anc")
        }
        assertFailsWith<IllegalArgumentException> {
            VendorFeatureId("vendor.acme")
        }
        assertFailsWith<IllegalArgumentException> {
            VendorFeatureId("vendor.Acme.Buds.ANC")
        }
    }

    @Test
    fun `identifier built from parts`() {
        val id = VendorFeatureId.of("acme", "anc-plus")
        assertNotNull(id)
        assertEquals("vendor.acme.anc-plus", id.value)
        assertNull(VendorFeatureId.of("Acme", "anc-plus"))
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
