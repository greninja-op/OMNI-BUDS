package com.omnibuds.core.quality

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase 13 (OB-P13-REQ-019, OB-P13-REQ-021): the documented source precedence
 * ordering.
 */
class SourcePrecedenceTest {

    @Test
    fun `ranks are strictly ordered`() {
        val ordered = listOf(
            SourcePrecedence.VERIFIED_RUNTIME_OBSERVATION,
            SourcePrecedence.PLATFORM_RUNTIME_METADATA,
            SourcePrecedence.VERIFIED_DEVICE_PROTOCOL,
            SourcePrecedence.CAPABILITY_DATABASE,
            SourcePrecedence.STATIC_INFERENCE,
            SourcePrecedence.UNKNOWN,
        )
        for (i in 0 until ordered.size - 1) {
            assertTrue(
                ordered[i].rank > ordered[i + 1].rank,
                "${ordered[i]} must outrank ${ordered[i + 1]}",
            )
        }
    }

    @Test
    fun `vendor protocol never outranks platform runtime evidence`() {
        assertTrue(
            SourcePrecedence.PLATFORM_RUNTIME_METADATA.rank >
                SourcePrecedence.VERIFIED_DEVICE_PROTOCOL.rank,
        )
    }

    @Test
    fun `unknown is the lowest rank`() {
        for (other in SourcePrecedence.entries) {
            if (other != SourcePrecedence.UNKNOWN) {
                assertTrue(other.rank > SourcePrecedence.UNKNOWN.rank)
            }
        }
    }
}
