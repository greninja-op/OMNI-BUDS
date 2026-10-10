package com.omnibuds.core.firmware

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FirmwareVersionTest {

    @Test
    fun parseSemanticVersions() {
        val v1 = FirmwareVersion.parse("1.2.3")
        assertIs<FirmwareVersion.Semantic>(v1)
        assertEquals(1, v1.major)
        assertEquals(2, v1.minor)
        assertEquals(3, v1.patch)
        assertNull(v1.preRelease)

        val v2 = FirmwareVersion.parse("v2.0")
        assertIs<FirmwareVersion.Semantic>(v2)
        assertEquals(2, v2.major)
        assertEquals(0, v2.minor)
        assertEquals(0, v2.patch)

        val v3 = FirmwareVersion.parse("1.0.4-rc1")
        assertIs<FirmwareVersion.Semantic>(v3)
        assertEquals(1, v3.major)
        assertEquals(0, v3.minor)
        assertEquals(4, v3.patch)
        assertEquals("rc1", v3.preRelease)

        assertTrue(v2 > v1)
    }

    @Test
    fun parseBuildNumberVersions() {
        val b1 = FirmwareVersion.parse("1024", FirmwareScheme.BUILD_NUMBER)
        assertIs<FirmwareVersion.BuildNumber>(b1)
        assertEquals(1024L, b1.build)

        val b2 = FirmwareVersion.parse("build-1050", FirmwareScheme.BUILD_NUMBER)
        assertIs<FirmwareVersion.BuildNumber>(b2)
        assertEquals(1050L, b2.build)

        assertTrue(b2 > b1)
    }

    @Test
    fun parseDateBasedVersions() {
        val d1 = FirmwareVersion.parse("2023.11.05")
        assertIs<FirmwareVersion.DateBased>(d1)
        assertEquals(2023, d1.year)
        assertEquals(11, d1.month)
        assertEquals(5, d1.day)

        val d2 = FirmwareVersion.parse("2024-01-15")
        assertIs<FirmwareVersion.DateBased>(d2)
        assertEquals(2024, d2.year)
        assertEquals(1, d2.month)
        assertEquals(15, d2.day)

        assertTrue(d2 > d1)
    }

    @Test
    fun parseAlphanumericBuildVersions() {
        val a1 = FirmwareVersion.parse("4E71")
        assertIs<FirmwareVersion.AlphanumericBuild>(a1)
        assertEquals(4, a1.generation)
        assertEquals('E', a1.trainLetter)
        assertEquals(71, a1.sequence)

        val a2 = FirmwareVersion.parse("5B58")
        assertIs<FirmwareVersion.AlphanumericBuild>(a2)
        assertEquals(5, a2.generation)
        assertEquals('B', a2.trainLetter)
        assertEquals(58, a2.sequence)

        assertTrue(a2 > a1)
    }

    @Test
    fun parseUnknownOrEmptyVersions() {
        assertEquals(FirmwareVersion.Unknown, FirmwareVersion.parse(null))
        assertEquals(FirmwareVersion.Unknown, FirmwareVersion.parse(""))
        assertEquals(FirmwareVersion.Unknown, FirmwareVersion.parse("   "))
        assertEquals(FirmwareVersion.Unknown, FirmwareVersion.parse("unknown"))
        assertEquals(FirmwareVersion.Unknown, FirmwareVersion.parse("0.0.0"))
    }

    @Test
    fun parseOpaqueVersions() {
        val o = FirmwareVersion.parse("CUSTOM-BLOB-HASH-X99", FirmwareScheme.OPAQUE)
        assertIs<FirmwareVersion.Opaque>(o)
        assertEquals("CUSTOM-BLOB-HASH-X99", o.rawValue)
    }

    @Test
    fun boundsMaxRawLength() {
        val longString = "A".repeat(300)
        val parsed = FirmwareVersion.parse(longString)
        assertEquals(128, parsed.rawValue.length)
    }
}
