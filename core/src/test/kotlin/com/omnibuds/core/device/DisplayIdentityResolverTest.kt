package com.omnibuds.core.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Iteration 3: display identity must come from the best available
 * evidence, and a placeholder must never become a device's name.
 */
class DisplayIdentityResolverTest {

    @Test
    fun `valid bluetooth name is displayed`() {
        val r = DisplayIdentityResolver.resolve(bluetoothName = "realme Buds T300")
        assertEquals("realme Buds T300", r.displayName)
        assertEquals(DisplayIdentityResolver.Source.BLUETOOTH_NAME, r.source)
        // The name is evidence, not a verified identification:
        assertNull(r.manufacturer)
        assertNull(r.model)
    }

    @Test
    fun `alias is preferred over the raw name`() {
        val r = DisplayIdentityResolver.resolve(
            bluetoothAlias = "Boat Rockerz",
            bluetoothName = "boAt Rockerz 450",
        )
        assertEquals("Boat Rockerz", r.displayName)
        assertEquals(DisplayIdentityResolver.Source.BLUETOOTH_ALIAS, r.source)
    }

    @Test
    fun `alias and name disagreement keeps both fields honest`() {
        val r = DisplayIdentityResolver.resolve(
            verifiedManufacturer = "boAt",
            verifiedModel = "Rockerz 450",
            bluetoothAlias = "My Headphones",
            bluetoothName = "boAt Rockerz 450",
        )
        assertEquals("My Headphones", r.displayName)
        // Verified identification still fills the identity fields:
        assertEquals("boAt", r.manufacturer)
        assertEquals("Rockerz 450", r.model)
    }

    @Test
    fun `missing name falls back to the verified model identity`() {
        val r = DisplayIdentityResolver.resolve(
            verifiedManufacturer = "Sony",
            verifiedModel = "WI-C100",
        )
        assertEquals("Sony WI-C100", r.displayName)
        assertEquals(DisplayIdentityResolver.Source.VERIFIED_MODEL, r.source)
    }

    @Test
    fun `no evidence at all yields the neutral fallback never Unknown`() {
        val r = DisplayIdentityResolver.resolve()
        assertEquals("Bluetooth audio device", r.displayName)
        assertEquals(DisplayIdentityResolver.Source.FALLBACK, r.source)
    }

    @Test
    fun `the placeholder literal is rejected as evidence`() {
        val r = DisplayIdentityResolver.resolve(
            bluetoothAlias = "Unknown",
            bluetoothName = "unknown",
        )
        assertEquals("Bluetooth audio device", r.displayName)
    }

    @Test
    fun `a MAC address is not a name`() {
        val r = DisplayIdentityResolver.resolve(bluetoothName = "AC:12:3F:44:9B:01")
        assertEquals("Bluetooth audio device", r.displayName)
    }

    @Test
    fun `user alias outranks every other evidence`() {
        val r = DisplayIdentityResolver.resolve(
            userAlias = "Gym Buds",
            verifiedManufacturer = "realme",
            verifiedModel = "Buds T300",
            bluetoothAlias = "realme Buds T300",
            bluetoothName = "realme Buds T300",
        )
        assertEquals("Gym Buds", r.displayName)
        assertEquals(DisplayIdentityResolver.Source.USER_ALIAS, r.source)
        // ...while the hardware identity stays intact underneath:
        assertEquals("realme", r.manufacturer)
        assertEquals("Buds T300", r.model)
    }

    @Test
    fun `blank and whitespace evidence is ignored`() {
        val r = DisplayIdentityResolver.resolve(
            userAlias = "   ",
            bluetoothAlias = "",
            bluetoothName = "JBL TUNE130NC TWS",
        )
        assertEquals("JBL TUNE130NC TWS", r.displayName)
    }

    @Test
    fun `similar names on different devices resolve independently`() {
        val a = DisplayIdentityResolver.resolve(bluetoothName = "Redmi Buds 5A")
        val b = DisplayIdentityResolver.resolve(bluetoothName = "Redmi Buds 5")
        assertEquals("Redmi Buds 5A", a.displayName)
        assertEquals("Redmi Buds 5", b.displayName)
    }

    @Test
    fun `resolution is deterministic across reconnects`() {
        val inputs = mapOf<String, String?>(
            "bluetoothName" to "CMF Buds",
        )
        val first = DisplayIdentityResolver.resolve(bluetoothName = inputs["bluetoothName"])
        val second = DisplayIdentityResolver.resolve(bluetoothName = inputs["bluetoothName"])
        assertEquals(first, second)
    }
}
