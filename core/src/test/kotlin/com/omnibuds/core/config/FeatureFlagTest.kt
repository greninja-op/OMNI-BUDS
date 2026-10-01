package com.omnibuds.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins Phase 1 execution prompt section 40: a feature flag may gate UI, an
 * experimental protocol adapter or debug diagnostics, and it must never convert
 * "unsupported ANC" into "supported ANC".
 *
 * The enum is the closed set, so an out-of-range kind cannot be constructed at all;
 * these tests exist so that widening the set is a loud, deliberate act rather than a
 * one-line addition that quietly changes what a flag can do.
 */
class FeatureFlagTest {

    @Test
    fun everyFeatureFlagKindRefusesToGrantCapabilitySupport() {
        for (kind in FeatureFlagKind.entries) {
            assertFalse(kind.grantsCapabilitySupport, "$kind must not be able to grant hardware support")
        }
    }

    @Test
    fun declaredKindsAreExactlyThePermittedSet() {
        val expected = setOf(
            FeatureFlagKind.EXPERIMENTAL_UI,
            FeatureFlagKind.EXPERIMENTAL_PROTOCOL_ADAPTER,
            FeatureFlagKind.DEBUG_DIAGNOSTIC,
        )

        assertEquals(expected, FeatureFlagKind.entries.toSet())
        assertEquals(expected, FeatureFlagKind.permitted)
        assertEquals(3, FeatureFlagKind.entries.size)
    }

    @Test
    fun aFlagCanGateUiAnUnprovenAdapterAndDiagnostics() {
        val ui = FeatureFlag("ui.gesture-editor-v2", FeatureFlagKind.EXPERIMENTAL_UI, enabled = true)
        val adapter = FeatureFlag("protocol.vendor-x-experimental", FeatureFlagKind.EXPERIMENTAL_PROTOCOL_ADAPTER, enabled = true)
        val diagnostics = FeatureFlag("diagnostics.frame-timing", FeatureFlagKind.DEBUG_DIAGNOSTIC, enabled = true)

        assertTrue(ui.enabled)
        assertTrue(adapter.enabled)
        assertTrue(diagnostics.enabled)

        val config = ApplicationConfiguration(
            debugLoggingEnabled = true,
            diagnosticMode = DiagnosticMode.LOCAL_DEBUG,
            featureFlags = setOf(ui, adapter, diagnostics),
        )

        assertTrue(config.isGateOpen("ui.gesture-editor-v2"))
        assertTrue(config.isGateOpen("protocol.vendor-x-experimental"))
        assertTrue(config.isGateOpen("diagnostics.frame-timing"))

        // Gating is not support: none of the flags in this configuration can grant it.
        assertTrue(config.featureFlags.all { !it.kind.grantsCapabilitySupport })
        assertEquals(3, config.featureFlags.size)
    }

    @Test
    fun aDisabledOrAbsentGateIsClosedRatherThanAssumedOpen() {
        val closed = FeatureFlag("ui.gesture-editor-v2", FeatureFlagKind.EXPERIMENTAL_UI, enabled = false)
        val config = ApplicationConfiguration(
            debugLoggingEnabled = false,
            diagnosticMode = DiagnosticMode.OFF,
            featureFlags = setOf(closed),
        )

        assertFalse(config.isGateOpen("ui.gesture-editor-v2"))
        assertFalse(config.isGateOpen("ui.never-configured"))
        assertNull(config.flagFor("ui.never-configured"))
    }

    @Test
    fun aFlagIsAValueSoEquivalentFlagsCollapseInASet() {
        val flag = FeatureFlag("ui.gesture-editor-v2", FeatureFlagKind.EXPERIMENTAL_UI, enabled = true)
        val same = FeatureFlag("ui.gesture-editor-v2", FeatureFlagKind.EXPERIMENTAL_UI, enabled = true)

        assertEquals(flag, same)
        assertEquals(1, setOf(flag, same, flag.copy()).size)
        assertFalse(flag.copy(enabled = false).enabled)
    }

    @Test
    fun aBlankFlagIdIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            FeatureFlag(" ", FeatureFlagKind.EXPERIMENTAL_UI, enabled = true)
        }
    }
}
