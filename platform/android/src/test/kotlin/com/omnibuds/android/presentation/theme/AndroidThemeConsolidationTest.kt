package com.omnibuds.android.presentation.theme

import com.omnibuds.android.presentation.accessibility.AndroidAccessibilityNode
import com.omnibuds.android.presentation.accessibility.AndroidAccessibilityRole
import com.omnibuds.android.presentation.workspace.ControlExecutionStatus
import com.omnibuds.android.presentation.workspace.FeatureCapabilityKind
import com.omnibuds.android.presentation.workspace.HardwareControlModel
import com.omnibuds.core.presentation.accessibility.AccessibilitySpec
import com.omnibuds.core.presentation.theme.OmniBudsColors
import com.omnibuds.core.presentation.theme.OmniBudsElevation
import com.omnibuds.core.presentation.theme.OmniBudsShapes
import com.omnibuds.core.presentation.theme.OmniBudsSpacing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidThemeConsolidationTest {

    @Test
    fun androidColorsMatchUnifiedCoreTokensAcrossAllModes() {
        assertEquals(OmniBudsColors.Dark.background, AndroidColors.Dark.background)
        assertEquals(OmniBudsColors.Dark.surface, AndroidColors.Dark.surface)
        assertEquals(OmniBudsColors.Dark.primary, AndroidColors.Dark.primary)
        assertEquals(OmniBudsColors.Dark.statusAvailable, AndroidColors.Dark.statusAvailable)
        assertEquals(OmniBudsColors.Dark.statusError, AndroidColors.Dark.statusError)

        assertEquals(OmniBudsColors.Light.background, AndroidColors.Light.background)
        assertEquals(OmniBudsColors.Light.surface, AndroidColors.Light.surface)
        assertEquals(OmniBudsColors.Light.primary, AndroidColors.Light.primary)
        assertEquals(OmniBudsColors.Light.statusAvailable, AndroidColors.Light.statusAvailable)
        assertEquals(OmniBudsColors.Light.statusError, AndroidColors.Light.statusError)

        assertEquals(OmniBudsColors.HighContrast.background, AndroidColors.HighContrast.background)
        assertEquals(OmniBudsColors.HighContrast.surface, AndroidColors.HighContrast.surface)
        assertEquals(OmniBudsColors.HighContrast.primary, AndroidColors.HighContrast.primary)
        assertEquals(OmniBudsColors.HighContrast.statusAvailable, AndroidColors.HighContrast.statusAvailable)
        assertEquals(OmniBudsColors.HighContrast.statusError, AndroidColors.HighContrast.statusError)
    }

    @Test
    fun androidSpacingMatchesUnifiedCoreSpacing() {
        assertEquals(OmniBudsSpacing.space0, AndroidSpacing.noneDp)
        assertEquals(OmniBudsSpacing.space4, AndroidSpacing.xsDp)
        assertEquals(OmniBudsSpacing.space8, AndroidSpacing.smDp)
        assertEquals(OmniBudsSpacing.space16, AndroidSpacing.mdDp)
        assertEquals(OmniBudsSpacing.space24, AndroidSpacing.lgDp)
        assertEquals(OmniBudsSpacing.space32, AndroidSpacing.xlDp)
        assertEquals(OmniBudsSpacing.space48, AndroidSpacing.xxlDp)
    }

    @Test
    fun androidElevationMatchesUnifiedCoreElevation() {
        assertEquals(OmniBudsElevation.level0, AndroidElevation.level0Dp)
        assertEquals(OmniBudsElevation.level1, AndroidElevation.level1Dp)
        assertEquals(OmniBudsElevation.level2, AndroidElevation.level2Dp)
        assertEquals(OmniBudsElevation.level3, AndroidElevation.level3Dp)
        assertEquals(OmniBudsElevation.level4, AndroidElevation.level4Dp)
        assertEquals(OmniBudsElevation.level5, AndroidElevation.level5Dp)
    }

    @Test
    fun androidShapesMatchUnifiedCoreShapes() {
        assertEquals(OmniBudsShapes.radiusNone, AndroidShapes.noneDp)
        assertEquals(OmniBudsShapes.radiusMedium, AndroidShapes.smallDp)
        assertEquals(OmniBudsShapes.radiusLarge, AndroidShapes.mediumDp)
        assertEquals(OmniBudsShapes.radiusExtraLarge, AndroidShapes.largeDp)
        assertEquals(OmniBudsShapes.radiusPill, AndroidShapes.pillDp)
    }

    @Test
    fun touchTargetSatisfiesWcagAccessibilityRequirement() {
        assertEquals(AccessibilitySpec.MIN_TOUCH_TARGET_DP, AndroidTouchTargets.minTouchTargetDp)
        assertTrue(AndroidTouchTargets.minTouchTargetDp >= 48)

        // Node check
        val buttonNode = AndroidAccessibilityNode(
            id = "btn-scan",
            label = "Scan",
            role = AndroidAccessibilityRole.BUTTON,
            touchTargetWidthDp = 48,
            touchTargetHeightDp = 48,
        )
        assertTrue(buttonNode.meetsTouchTargetRequirement())

        val undersizedNode = AndroidAccessibilityNode(
            id = "btn-small",
            label = "Small",
            role = AndroidAccessibilityRole.BUTTON,
            touchTargetWidthDp = 36,
            touchTargetHeightDp = 36,
        )
        assertFalse(undersizedNode.meetsTouchTargetRequirement())
    }

    @Test
    fun windowSizeClassPartitionsWidthDpCorrectly() {
        assertEquals(AndroidWindowSizeClass.COMPACT, AndroidWindowSizeClass.fromWidthDp(360))
        assertEquals(AndroidWindowSizeClass.COMPACT, AndroidWindowSizeClass.fromWidthDp(599))
        assertEquals(AndroidWindowSizeClass.MEDIUM, AndroidWindowSizeClass.fromWidthDp(600))
        assertEquals(AndroidWindowSizeClass.MEDIUM, AndroidWindowSizeClass.fromWidthDp(839))
        assertEquals(AndroidWindowSizeClass.EXPANDED, AndroidWindowSizeClass.fromWidthDp(840))
        assertEquals(AndroidWindowSizeClass.EXPANDED, AndroidWindowSizeClass.fromWidthDp(1200))
    }

    @Test
    fun hardwareControlModelPreservesSixCapabilityLevels() {
        val control = HardwareControlModel(
            featureId = "anc",
            displayName = "ANC",
            capabilityKind = FeatureCapabilityKind.PERSISTENCE_VERIFIED,
            executionStatus = ControlExecutionStatus.IDLE,
        )
        assertTrue(control.isActionable)
        assertFalse(control.isReadOnly)
        assertFalse(control.isUnsupported)
        assertFalse(control.isUnknown)

        val pending = control.copy(executionStatus = ControlExecutionStatus.PENDING)
        assertFalse(pending.isActionable)

        val unsupported = control.copy(capabilityKind = FeatureCapabilityKind.UNSUPPORTED)
        assertFalse(unsupported.isActionable)
        assertTrue(unsupported.isUnsupported)
    }
}
