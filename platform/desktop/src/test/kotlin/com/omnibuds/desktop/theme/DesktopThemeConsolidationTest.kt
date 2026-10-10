package com.omnibuds.desktop.theme

import com.omnibuds.core.presentation.control.ControlExecutionStatus
import com.omnibuds.core.presentation.control.FeatureCapabilityKind
import com.omnibuds.core.presentation.theme.OmniBudsColors
import com.omnibuds.core.presentation.theme.OmniBudsShapes
import com.omnibuds.core.presentation.theme.OmniBudsSpacing
import com.omnibuds.desktop.presentation.workspace.HardwareControlItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopThemeConsolidationTest {

    @Test
    fun desktopColorsMatchUnifiedCoreTokensAcrossAllModes() {
        assertEquals(OmniBudsColors.Dark.background, DesktopColors.Dark.background)
        assertEquals(OmniBudsColors.Dark.surface, DesktopColors.Dark.surface)
        assertEquals(OmniBudsColors.Dark.primary, DesktopColors.Dark.primary)
        assertEquals(OmniBudsColors.Dark.statusAvailable, DesktopColors.Dark.statusAvailable)
        assertEquals(OmniBudsColors.Dark.statusError, DesktopColors.Dark.statusError)

        assertEquals(OmniBudsColors.Light.background, DesktopColors.Light.background)
        assertEquals(OmniBudsColors.Light.surface, DesktopColors.Light.surface)
        assertEquals(OmniBudsColors.Light.primary, DesktopColors.Light.primary)
        assertEquals(OmniBudsColors.Light.statusAvailable, DesktopColors.Light.statusAvailable)
        assertEquals(OmniBudsColors.Light.statusError, DesktopColors.Light.statusError)

        assertEquals(OmniBudsColors.HighContrast.background, DesktopColors.HighContrast.background)
        assertEquals(OmniBudsColors.HighContrast.surface, DesktopColors.HighContrast.surface)
        assertEquals(OmniBudsColors.HighContrast.primary, DesktopColors.HighContrast.primary)
        assertEquals(OmniBudsColors.HighContrast.statusAvailable, DesktopColors.HighContrast.statusAvailable)
        assertEquals(OmniBudsColors.HighContrast.statusError, DesktopColors.HighContrast.statusError)
    }

    @Test
    fun desktopSpacingMatchesUnifiedCoreSpacing() {
        assertEquals(OmniBudsSpacing.space4, DesktopSpacing.space4)
        assertEquals(OmniBudsSpacing.space8, DesktopSpacing.space8)
        assertEquals(OmniBudsSpacing.space12, DesktopSpacing.space12)
        assertEquals(OmniBudsSpacing.space16, DesktopSpacing.space16)
        assertEquals(OmniBudsSpacing.space20, DesktopSpacing.space20)
        assertEquals(OmniBudsSpacing.space24, DesktopSpacing.space24)
        assertEquals(OmniBudsSpacing.space32, DesktopSpacing.space32)
        assertEquals(OmniBudsSpacing.space48, DesktopSpacing.space48)
    }

    @Test
    fun desktopShapesMatchUnifiedCoreShapes() {
        assertEquals(OmniBudsShapes.radiusNone, DesktopShapes.radiusNone)
        assertEquals(OmniBudsShapes.radiusSmall, DesktopShapes.radiusSmall)
        assertEquals(OmniBudsShapes.radiusMedium, DesktopShapes.radiusMedium)
        assertEquals(OmniBudsShapes.radiusLarge, DesktopShapes.radiusLarge)
    }

    @Test
    fun desktopThemeResolvesCleanlyAcrossAllModesAndDensities() {
        val darkTheme = DesktopTheme.resolve(ThemeMode.DARK, UiDensity.COMFORTABLE)
        assertEquals(DesktopColors.Dark, darkTheme.colors)
        assertEquals(UiDensity.COMFORTABLE, darkTheme.density)

        val lightTheme = DesktopTheme.resolve(ThemeMode.LIGHT, UiDensity.COMPACT)
        assertEquals(DesktopColors.Light, lightTheme.colors)
        assertEquals(UiDensity.COMPACT, lightTheme.density)

        val highContrastTheme = DesktopTheme.resolve(ThemeMode.HIGH_CONTRAST)
        assertEquals(DesktopColors.HighContrast, highContrastTheme.colors)
    }

    @Test
    fun desktopHardwareControlItemReflectsSixCapabilityLevels() {
        val unsupportedControl = HardwareControlItem(
            featureId = "anc",
            displayName = "ANC",
            description = "Active Noise Cancellation",
            isSupported = false,
            isActionable = false,
            observedValue = null,
            requestedValue = null,
            acknowledgedValue = null,
        )
        assertEquals(FeatureCapabilityKind.UNSUPPORTED, unsupportedControl.capabilityKind)
        assertTrue(unsupportedControl.isUnsupported)
        assertFalse(unsupportedControl.isActionable)

        val readOnlyControl = HardwareControlItem(
            featureId = "temp",
            displayName = "Temperature",
            description = "Sensor reading",
            isSupported = true,
            isActionable = false,
            observedValue = "24C",
            requestedValue = null,
            acknowledgedValue = null,
        )
        assertEquals(FeatureCapabilityKind.READ_ONLY, readOnlyControl.capabilityKind)
        assertTrue(readOnlyControl.isReadOnly)
        assertFalse(readOnlyControl.isActionable)

        val persistentControl = HardwareControlItem(
            featureId = "eq",
            displayName = "Equalizer",
            description = "Sound profile",
            isSupported = true,
            isActionable = true,
            observedValue = "Bass Boost",
            requestedValue = null,
            acknowledgedValue = null,
            executionStatus = ControlExecutionStatus.IDLE,
        )
        assertEquals(FeatureCapabilityKind.PERSISTENT, persistentControl.capabilityKind)
        assertTrue(persistentControl.isActionable)
        assertFalse(persistentControl.isPending)
    }
}
