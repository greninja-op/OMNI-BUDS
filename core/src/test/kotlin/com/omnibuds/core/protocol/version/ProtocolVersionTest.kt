package com.omnibuds.core.protocol.version

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProtocolVersionTest {

    @Test
    fun parseSemanticValidStrings() {
        val v1 = ProtocolVersion.parseSemantic("1.2.3")
        assertNotNull(v1)
        assertEquals(1, v1.major)
        assertEquals(2, v1.minor)
        assertEquals(3, v1.patch)
        assertEquals("1.2.3", v1.rawValue)

        val v2 = ProtocolVersion.parseSemantic("2.0")
        assertNotNull(v2)
        assertEquals(2, v2.major)
        assertEquals(0, v2.minor)
        assertEquals(0, v2.patch)
        assertEquals("2.0.0", v2.rawValue)
    }

    @Test
    fun parseSemanticInvalidStrings() {
        assertNull(ProtocolVersion.parseSemantic(""))
        assertNull(ProtocolVersion.parseSemantic("abc"))
        assertNull(ProtocolVersion.parseSemantic("1"))
        assertNull(ProtocolVersion.parseSemantic("1.2.3.4"))
        assertNull(ProtocolVersion.parseSemantic("-1.0.0"))
    }

    @Test
    fun semanticOrderingIsNumericNotLexicographical() {
        val v9 = ProtocolVersion.Semantic(1, 9, 0)
        val v10 = ProtocolVersion.Semantic(1, 10, 0)
        assertTrue(v10 > v9, "1.10.0 must be greater than 1.9.0")

        val v1_0_10 = ProtocolVersion.Semantic(1, 0, 10)
        val v1_0_9 = ProtocolVersion.Semantic(1, 0, 9)
        assertTrue(v1_0_10 > v1_0_9, "1.0.10 must be greater than 1.0.9")
    }

    @Test
    fun parseIntegerRevision() {
        val r1 = ProtocolVersion.parseIntegerRevision("1")
        assertNotNull(r1)
        assertEquals(1, r1.revision)
        assertEquals("rev-1", r1.toString())

        val r2 = ProtocolVersion.parseIntegerRevision("rev-5")
        assertNotNull(r2)
        assertEquals(5, r2.revision)

        val r3 = ProtocolVersion.parseIntegerRevision("r10")
        assertNotNull(r3)
        assertEquals(10, r3.revision)

        assertNull(ProtocolVersion.parseIntegerRevision("invalid"))
        assertNull(ProtocolVersion.parseIntegerRevision("-1"))
    }

    @Test
    fun vendorDefinedTokens() {
        val token = ProtocolVersion.VendorDefined("bmap-v2-anc")
        assertEquals("bmap-v2-anc", token.rawValue)
        assertEquals("bmap-v2-anc", token.toString())
    }

    @Test
    fun unknownVersionSentinel() {
        val unknown = ProtocolVersion.Unknown
        assertEquals("unknown", unknown.rawValue)
        assertEquals("unknown", unknown.toString())
        assertEquals(0, unknown.compareTo(ProtocolVersion.Unknown))
    }
}
