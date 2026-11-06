package com.omnibuds.android.presentation.workspace

import com.omnibuds.android.presentation.workspace.ProductCatalog.Category
import com.omnibuds.android.presentation.workspace.ProductCatalog.Confidence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Iteration 2: identity resolution must be brand-qualified, generation
 * honest, and incapable of confidently assigning an unrelated product.
 */
class ProductCatalogTest {

    @Test
    fun `realme T300 resolves exact with brand present`() {
        val info = ProductCatalog.resolve("realme Buds T300")
        assertEquals(Confidence.EXACT, info.confidence)
        assertEquals("realme_buds_t300", info.catalogId)
        assertEquals(Category.TWS, info.category)
        assertEquals("product_realme_buds_t300", info.imageAsset)
    }

    @Test
    fun `model name without brand does not capture the brand entry`() {
        val info = ProductCatalog.resolve("Buds T300")
        assertEquals(Confidence.CATEGORY_FALLBACK, info.confidence)
        assertNull(info.catalogId)
        assertNull(info.manufacturer)
    }

    @Test
    fun `another brand's T300 does not become a realme`() {
        val info = ProductCatalog.resolve("Acme Buds T300")
        assertNull(info.catalogId)
        assertEquals(Confidence.CATEGORY_FALLBACK, info.confidence)
    }

    @Test
    fun `CMF Buds Pro is not CMF Buds`() {
        val pro = ProductCatalog.resolve("CMF Buds Pro")
        assertTrue(pro.catalogId != "cmf_buds")
        assertEquals(Confidence.FAMILY, pro.confidence)
        assertEquals("CMF by Nothing", pro.manufacturer)
        val plain = ProductCatalog.resolve("CMF Buds")
        assertEquals("cmf_buds", plain.catalogId)
        assertEquals(Confidence.EXACT, plain.confidence)
    }

    @Test
    fun `Redmi Buds 5 is not the 5A`() {
        val five = ProductCatalog.resolve("Redmi Buds 5")
        assertTrue(five.catalogId != "redmi_buds_5a")
        val fiveA = ProductCatalog.resolve("Redmi Buds 5A")
        assertEquals("redmi_buds_5a", fiveA.catalogId)
        assertEquals(Confidence.EXACT, fiveA.confidence)
    }

    @Test
    fun `JBL compact model code resolves`() {
        val info = ProductCatalog.resolve("JBL TUNE130NC TWS")
        assertEquals("jbl_tune_130nc", info.catalogId)
        assertEquals(Confidence.EXACT, info.confidence)
    }

    @Test
    fun `Sony WI-C100 carries the licensed photograph and attribution`() {
        val info = ProductCatalog.resolve("Sony WI-C100")
        assertEquals(Confidence.EXACT, info.confidence)
        assertEquals(Category.NECKBAND, info.category)
        assertEquals("product_sony_wi_c100", info.imageAsset)
        assertTrue(info.imageAttribution?.contains("CC BY-SA 4.0") == true)
    }

    @Test
    fun `AirPods Pro is family confidence because the generation is unknowable`() {
        val info = ProductCatalog.resolve("AirPods Pro")
        assertEquals("airpods_pro", info.catalogId)
        assertEquals(Confidence.FAMILY, info.confidence)
        assertNull(info.imageAsset)
    }

    @Test
    fun `AirPods Max is over-ear never TWS`() {
        val info = ProductCatalog.resolve("AirPods Max")
        assertEquals(Category.OVER_EAR, info.category)
    }

    @Test
    fun `Momentum True Wireless is TWS while Momentum headphones are over-ear`() {
        val tw = ProductCatalog.resolve("Sennheiser Momentum True Wireless 4")
        assertEquals(Category.TWS, tw.category)
        val overEar = ProductCatalog.resolve("Sennheiser Momentum 4")
        assertEquals(Category.OVER_EAR, overEar.category)
    }

    @Test
    fun `unknown device falls back with no invented identity or image`() {
        val info = ProductCatalog.resolve("Acme SoundPods X9")
        assertEquals(Confidence.CATEGORY_FALLBACK, info.confidence)
        assertNull(info.manufacturer)
        assertNull(info.model)
        assertNull(info.imageAsset)
        assertNull(info.features)
    }

    @Test
    fun `user override wins over name matching`() {
        val info = ProductCatalog.resolve("realme Buds T300", "jbl_tune_130nc")
        assertEquals(Confidence.OVERRIDE, info.confidence)
        assertEquals("jbl_tune_130nc", info.catalogId)
    }

    @Test
    fun `generic override yields category fallback identity`() {
        val info = ProductCatalog.resolve(
            "realme Buds T300",
            ProductCatalog.GENERIC_OVERRIDE_ID,
        )
        assertEquals(Confidence.OVERRIDE, info.confidence)
        assertNull(info.catalogId)
        assertNull(info.manufacturer)
        assertEquals(Category.TWS, info.category)
    }

    @Test
    fun `bundled models carry verified product images`() {
        val withImages = ProductCatalog.allEntries().filter { it.imageAsset != null }
        assertTrue(withImages.any { it.id == "realme_buds_t300" })
        assertTrue(withImages.any { it.id == "sony_wi_c100" })
        assertTrue(withImages.any { it.id == "cmf_buds" })
        assertTrue(withImages.any { it.id == "redmi_buds_5a" })
    }
}
