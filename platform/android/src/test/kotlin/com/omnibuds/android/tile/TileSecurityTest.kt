package com.omnibuds.android.tile

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Phase 25: security tests.
 */
class TileSecurityTest {

    @Test
    fun `tile labels never carry identifiers`() {
        // TileState labels are fixed strings; the mapper never interpolates
        // device ids, addresses, or protocol payloads.
        val tile = TileStateMapper.map(null)
        assertFalse(tile.label.contains(":"))
        assertFalse((tile.subtitle ?: "").contains(":"))
    }

    @Test
    fun `refusal reasons are typed not strings`() {
        val refusals: List<TileDispatchRefusal> = listOf(
            TileDispatchRefusal.NoTarget("x"),
            TileDispatchRefusal.AmbiguousTarget(emptyList()),
            TileDispatchRefusal.SessionInvalid("x"),
            TileDispatchRefusal.Unsupported("anc"),
            TileDispatchRefusal.ReadOnly("anc"),
            TileDispatchRefusal.StaleState("anc"),
            TileDispatchRefusal.UnknownState("anc"),
            TileDispatchRefusal.AccessDenied("x"),
            TileDispatchRefusal.DuplicateClick("anc"),
        )
        assertTrue(refusals.size == 9)
    }

    @Test
    fun `tile service requires installed dependencies`() {
        TileDependencies.resetForTests()
        try {
            TileDependencies.coordinator()
            assertTrue(false, "should have thrown")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("not installed"))
        }
    }
}
