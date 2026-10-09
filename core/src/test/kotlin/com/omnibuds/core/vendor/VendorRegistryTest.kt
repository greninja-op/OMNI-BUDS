package com.omnibuds.core.vendor

import com.omnibuds.core.device.DeviceFingerprint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlinx.coroutines.test.runTest

/**
 * Phase 19: vendor registry and matching tests.
 */
class VendorRegistryTest {

    private fun fingerprint() = DeviceFingerprint()

    @Test
    fun `null adapter matches nothing`() {
        val adapter = NullVendorAdapter()
        assertEquals(
            MatchResult.NotMatched,
            adapter.match(fingerprint()),
        )
    }

    @Test
    fun `empty registry resolves to null`() = runTest {
        val registry = VendorRegistry()
        assertNull(registry.resolve(fingerprint()))
    }

    @Test
    fun `registry with only null adapter resolves to null`() = runTest {
        val registry = VendorRegistry(listOf(NullVendorAdapter()))
        assertNull(registry.resolve(fingerprint()))
    }

    @Test
    fun `ambiguous match blocks resolution`() = runTest {
        val ambiguous = object : VendorAdapter by NullVendorAdapter() {
            override fun match(fingerprint: DeviceFingerprint) =
                MatchResult.Ambiguous("insufficient identity")
        }
        val registry = VendorRegistry(listOf(ambiguous))
        assertNull(registry.resolve(fingerprint()))
        assertTrue(registry.isAmbiguous(fingerprint()))
    }

    @Test
    fun `multiple matches block resolution`() = runTest {
        val a = object : VendorAdapter by NullVendorAdapter() {
            override val adapterId = "a"
            override fun match(fingerprint: DeviceFingerprint) =
                MatchResult.Matched("a", "test")
        }
        val b = object : VendorAdapter by NullVendorAdapter() {
            override val adapterId = "b"
            override fun match(fingerprint: DeviceFingerprint) =
                MatchResult.Matched("b", "test")
        }
        val registry = VendorRegistry(listOf(a, b))
        // Conflicting matches → safest is no adapter.
        assertNull(registry.resolve(fingerprint()))
    }

    @Test
    fun `single exact match resolves`() = runTest {
        val adapter = object : VendorAdapter by NullVendorAdapter() {
            override val adapterId = "test.adapter"
            override fun match(fingerprint: DeviceFingerprint) =
                MatchResult.Matched("test.adapter", "test evidence")
        }
        val registry = VendorRegistry(listOf(NullVendorAdapter(), adapter))
        assertEquals("test.adapter", registry.resolve(fingerprint())?.adapterId)
    }

    @Test
    fun `adapter ids listed`() {
        val registry = VendorRegistry(listOf(NullVendorAdapter()))
        assertEquals(listOf("omnibuds.null"), registry.adapterIds())
    }

    @Test
    fun `null adapter protocol has no commands`() {
        val adapter = NullVendorAdapter()
        assertTrue(adapter.protocol.commands.isEmpty())
        assertTrue(adapter.protocol.responses.isEmpty())
    }
}
