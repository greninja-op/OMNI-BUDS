package com.omnibuds.core.presentation

import com.omnibuds.core.globalstate.BatteryState
import com.omnibuds.core.globalstate.CapabilityState
import com.omnibuds.core.globalstate.ConnectionState
import com.omnibuds.core.globalstate.GlobalDeviceId
import com.omnibuds.core.globalstate.GlobalDeviceState
import com.omnibuds.core.globalstate.IdentityState
import com.omnibuds.core.globalstate.ObservationProvenance
import com.omnibuds.core.globalstate.ObservedValue
import com.omnibuds.core.globalstate.OperationStatus
import com.omnibuds.core.globalstate.PersistenceState
import com.omnibuds.core.globalstate.ProtocolState
import com.omnibuds.core.presentation.accessibility.AccessibilitySpec
import com.omnibuds.core.presentation.audio.UnifiedAudioModel
import com.omnibuds.core.presentation.battery.UnifiedBatteryModel
import com.omnibuds.core.presentation.components.ComponentCatalogSpec
import com.omnibuds.core.presentation.components.ComponentKind
import com.omnibuds.core.presentation.control.ControlExecutionStatus
import com.omnibuds.core.presentation.control.FeatureCapabilityKind
import com.omnibuds.core.presentation.control.UnifiedHardwareControlModel
import com.omnibuds.core.presentation.state.DevicePresentationState
import com.omnibuds.core.presentation.state.DeviceStateMapper
import com.omnibuds.core.presentation.strings.OmniBudsStrings
import com.omnibuds.core.presentation.theme.OmniBudsColors
import com.omnibuds.core.presentation.theme.OmniBudsElevation
import com.omnibuds.core.presentation.theme.OmniBudsIcon
import com.omnibuds.core.presentation.theme.OmniBudsMotion
import com.omnibuds.core.presentation.theme.OmniBudsShapes
import com.omnibuds.core.presentation.theme.OmniBudsSpacing
import com.omnibuds.core.presentation.theme.OmniBudsThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnifiedDesignSystemTest {

    // --- 1. Token & Palette Tests ---

    @Test
    fun tokensMatchAcrossAllThemesAndAreValidHex() {
        val hexPattern = Regex("^#[0-9A-Fa-f]{6}$")

        for (colors in listOf(OmniBudsColors.Dark, OmniBudsColors.Light, OmniBudsColors.HighContrast)) {
            assertTrue(hexPattern.matches(colors.background), "Invalid hex: ${colors.background}")
            assertTrue(hexPattern.matches(colors.surface), "Invalid hex: ${colors.surface}")
            assertTrue(hexPattern.matches(colors.surfaceElevated), "Invalid hex: ${colors.surfaceElevated}")
            assertTrue(hexPattern.matches(colors.surfaceVariant), "Invalid hex: ${colors.surfaceVariant}")
            assertTrue(hexPattern.matches(colors.onBackground), "Invalid hex: ${colors.onBackground}")
            assertTrue(hexPattern.matches(colors.onSurface), "Invalid hex: ${colors.onSurface}")
            assertTrue(hexPattern.matches(colors.onSurfaceVariant), "Invalid hex: ${colors.onSurfaceVariant}")
            assertTrue(hexPattern.matches(colors.primary), "Invalid hex: ${colors.primary}")
            assertTrue(hexPattern.matches(colors.onPrimary), "Invalid hex: ${colors.onPrimary}")
            assertTrue(hexPattern.matches(colors.outline), "Invalid hex: ${colors.outline}")
            assertTrue(hexPattern.matches(colors.statusAvailable), "Invalid hex: ${colors.statusAvailable}")
            assertTrue(hexPattern.matches(colors.statusWarning), "Invalid hex: ${colors.statusWarning}")
            assertTrue(hexPattern.matches(colors.statusError), "Invalid hex: ${colors.statusError}")
            assertTrue(hexPattern.matches(colors.statusNeutral), "Invalid hex: ${colors.statusNeutral}")
            assertTrue(hexPattern.matches(colors.statusActive), "Invalid hex: ${colors.statusActive}")
        }
    }

    @Test
    fun themeResolutionResolvesCorrectPalette() {
        assertEquals(OmniBudsColors.Dark, OmniBudsColors.resolve(OmniBudsThemeMode.DARK))
        assertEquals(OmniBudsColors.Light, OmniBudsColors.resolve(OmniBudsThemeMode.LIGHT))
        assertEquals(OmniBudsColors.HighContrast, OmniBudsColors.resolve(OmniBudsThemeMode.HIGH_CONTRAST))
        assertEquals(OmniBudsColors.Dark, OmniBudsColors.resolve(OmniBudsThemeMode.SYSTEM, systemIsDark = true))
        assertEquals(OmniBudsColors.Light, OmniBudsColors.resolve(OmniBudsThemeMode.SYSTEM, systemIsDark = false))
    }

    @Test
    fun wcagContrastMeetsWcagAaAndAaa() {
        // Dark Mode contrast
        assertTrue(
            AccessibilitySpec.passesWcagAaa(OmniBudsColors.Dark.onBackground, OmniBudsColors.Dark.background),
            "Dark onBackground on background must pass WCAG AAA (>= 7:1)",
        )
        assertTrue(
            AccessibilitySpec.passesWcagAa(OmniBudsColors.Dark.onSurface, OmniBudsColors.Dark.surface),
            "Dark onSurface on surface must pass WCAG AA (>= 4.5:1)",
        )

        // Light Mode contrast
        assertTrue(
            AccessibilitySpec.passesWcagAaa(OmniBudsColors.Light.onBackground, OmniBudsColors.Light.background),
            "Light onBackground on background must pass WCAG AAA (>= 7:1)",
        )
        assertTrue(
            AccessibilitySpec.passesWcagAa(OmniBudsColors.Light.onSurface, OmniBudsColors.Light.surface),
            "Light onSurface on surface must pass WCAG AA (>= 4.5:1)",
        )

        // High Contrast Mode
        assertTrue(
            AccessibilitySpec.passesWcagAaa(OmniBudsColors.HighContrast.onBackground, OmniBudsColors.HighContrast.background),
            "HighContrast onBackground on background must pass WCAG AAA (>= 7:1)",
        )
        assertTrue(
            AccessibilitySpec.passesWcagAaa(OmniBudsColors.HighContrast.onSurface, OmniBudsColors.HighContrast.surface),
            "HighContrast onSurface on surface must pass WCAG AAA (>= 7:1)",
        )
    }

    @Test
    fun spacingGridConformsToFourDpGrid() {
        val spaces = listOf(
            OmniBudsSpacing.space0,
            OmniBudsSpacing.space4,
            OmniBudsSpacing.space8,
            OmniBudsSpacing.space12,
            OmniBudsSpacing.space16,
            OmniBudsSpacing.space20,
            OmniBudsSpacing.space24,
            OmniBudsSpacing.space32,
            OmniBudsSpacing.space48,
        )
        for (s in spaces) {
            assertEquals(0, s % 4, "Spacing value $s must be a multiple of 4dp")
        }
    }

    @Test
    fun shapesHierarchyOrdered() {
        assertTrue(OmniBudsShapes.radiusNone < OmniBudsShapes.radiusSmall)
        assertTrue(OmniBudsShapes.radiusSmall < OmniBudsShapes.radiusMedium)
        assertTrue(OmniBudsShapes.radiusMedium < OmniBudsShapes.radiusLarge)
        assertTrue(OmniBudsShapes.radiusLarge < OmniBudsShapes.radiusExtraLarge)
        assertTrue(OmniBudsShapes.radiusExtraLarge < OmniBudsShapes.radiusPill)
    }

    @Test
    fun elevationHierarchyOrdered() {
        assertTrue(OmniBudsElevation.level0 < OmniBudsElevation.level1)
        assertTrue(OmniBudsElevation.level1 < OmniBudsElevation.level2)
        assertTrue(OmniBudsElevation.level2 < OmniBudsElevation.level3)
        assertTrue(OmniBudsElevation.level3 < OmniBudsElevation.level4)
        assertTrue(OmniBudsElevation.level4 < OmniBudsElevation.level5)
    }

    @Test
    fun motionDurationsOrdered() {
        assertTrue(OmniBudsMotion.durationFastMs < OmniBudsMotion.durationMediumMs)
        assertTrue(OmniBudsMotion.durationMediumMs < OmniBudsMotion.durationSlowMs)
    }

    @Test
    fun semanticIconsHaveDescriptions() {
        for (icon in OmniBudsIcon.values()) {
            assertTrue(icon.description.isNotBlank(), "Icon $icon must have a descriptive label")
        }
    }

    // --- 2. State Mapping & Terminology Tests ---

    @Test
    fun stateMapperDeterministicMappings() {
        val deviceId = GlobalDeviceId("dev-1")

        // 1. Null state -> UNKNOWN
        assertEquals(DevicePresentationState.UNKNOWN, DeviceStateMapper.mapFromGlobalState(null))

        // 2. Disconnected
        val emptyState = GlobalDeviceState.empty(deviceId)
        assertEquals(DevicePresentationState.DISCONNECTED, DeviceStateMapper.mapFromGlobalState(emptyState))

        // 3. Connecting
        val connectingState = emptyState.copy(connection = ConnectionState.Connecting("sess-1"))
        assertEquals(DevicePresentationState.CONNECTING, DeviceStateMapper.mapFromGlobalState(connectingState))

        // 4. Connected but Unidentified
        val connectedUnidentified = emptyState.copy(
            connection = ConnectionState.Connected(sessionId = "sess-1", generation = 1L, transport = "BLE"),
            identity = IdentityState.Unknown,
        )
        assertEquals(DevicePresentationState.CONNECTED_IDENTIFYING, DeviceStateMapper.mapFromGlobalState(connectedUnidentified))

        // 5. Connected + Identified + Protocol Negotiating/Unresolved
        val connectedResolvingProto = connectedUnidentified.copy(
            identity = IdentityState.Identified("Sony", "WF-1000XM5", "HIGH", "1.0"),
            protocol = ProtocolState.Unresolved,
        )
        assertEquals(DevicePresentationState.READ_ONLY, DeviceStateMapper.mapFromGlobalState(connectedResolvingProto))

        // 6. Connected + Protocol Resolved + Capabilities Discovering
        val connectedDiscoveringCaps = connectedResolvingProto.copy(
            protocol = ProtocolState.Resolved("sony-proto", "1.0", compatible = true),
            capabilities = CapabilityState.Discovering("Probing GATT"),
        )
        assertEquals(DevicePresentationState.CAPABILITY_DISCOVERY_IN_PROGRESS, DeviceStateMapper.mapFromGlobalState(connectedDiscoveringCaps))

        // 7. Connected + Protocol Resolved + Capabilities Ready
        val connectedReady = connectedDiscoveringCaps.copy(
            capabilities = CapabilityState.Ready(setOf("anc", "eq")),
        )
        assertEquals(DevicePresentationState.READY, DeviceStateMapper.mapFromGlobalState(connectedReady))

        // 8. Connected + Persistence Verified
        val persistenceVerified = connectedReady.copy(
            persistence = PersistenceState.Verified(scope = "device-scope", atMillis = 1000L),
        )
        assertEquals(DevicePresentationState.PERSISTENCE_VERIFIED, DeviceStateMapper.mapFromGlobalState(persistenceVerified))

        // 9. In-Flight Operation Pending
        val pendingState = connectedReady.copy(
            features = connectedReady.features.copy(
                executing = mapOf("anc" to OperationStatus.Pending(startedAtMillis = 1000L)),
            ),
        )
        assertEquals(DevicePresentationState.OPERATION_PENDING, DeviceStateMapper.mapFromGlobalState(pendingState))

        // 10. Disconnecting
        val disconnectingState = emptyState.copy(connection = ConnectionState.Disconnecting("sess-1"))
        assertEquals(DevicePresentationState.DISCONNECTING, DeviceStateMapper.mapFromGlobalState(disconnectingState))
    }

    // --- 3. Hardware Control Model Tests ---

    @Test
    fun controlModelPreservesSixCapabilityLevelsAndActionability() {
        val unsupported = UnifiedHardwareControlModel(
            featureId = "anc",
            displayName = "ANC",
            capabilityKind = FeatureCapabilityKind.UNSUPPORTED,
        )
        assertFalse(unsupported.isActionable)
        assertTrue(unsupported.isUnsupported)

        val readOnly = UnifiedHardwareControlModel(
            featureId = "anc",
            displayName = "ANC",
            capabilityKind = FeatureCapabilityKind.READ_ONLY,
        )
        assertFalse(readOnly.isActionable)
        assertTrue(readOnly.isReadOnly)

        val actionable = UnifiedHardwareControlModel(
            featureId = "anc",
            displayName = "ANC",
            capabilityKind = FeatureCapabilityKind.PERSISTENT,
            executionStatus = ControlExecutionStatus.IDLE,
        )
        assertTrue(actionable.isActionable)
        assertFalse(actionable.isPending)

        val pending = actionable.copy(executionStatus = ControlExecutionStatus.PENDING)
        assertFalse(pending.isActionable, "Control must NOT be actionable while operation is in-flight")
        assertTrue(pending.isPending)
    }

    // --- 4. Truthful Battery & Audio Tests ---

    @Test
    fun batteryModelPreservesNullabilityAndDetectsStaleness() {
        // Unknown battery
        val unknown = UnifiedBatteryModel.fromCoreState(BatteryState.Unknown)
        assertFalse(unknown.isAvailable)
        assertNull(unknown.overallPercent)
        assertFalse(unknown.overallPercent == 0, "Unknown battery must NEVER be coerced to 0%")
        assertNotNull(unknown.unavailableReason)

        // Known battery
        val now = 1_000_000L
        val obs = ObservedValue(
            value = Unit,
            provenance = ObservationProvenance(
                sourceId = "batt-src",
                observedAtMillis = now,
                receivedAtMillis = now,
                sessionId = "s-1",
                connectionGeneration = 1L,
                protocolVersion = "1.0",
            ),
        )
        val known = BatteryState.Known(levelPercent = 80, charging = true, observation = obs)
        val model = UnifiedBatteryModel.fromCoreState(known, currentEpochMillis = now + 10_000L)

        assertTrue(model.isAvailable)
        assertEquals(80, model.overallPercent)
        assertEquals(true, model.isCharging)
        assertFalse(model.isStale)
        assertTrue(model.summaryText.contains("80%"))

        // Stale battery (>60s)
        val staleModel = UnifiedBatteryModel.fromCoreState(known, currentEpochMillis = now + 75_000L)
        assertTrue(staleModel.isStale)
        assertTrue(staleModel.summaryText.contains("(stale)"))
        assertTrue(staleModel.accessibilityAnnouncement.contains("reading may be outdated"))
    }

    @Test
    fun audioModelTruthfullySurfacesUnobservableCodec() {
        val audioModel = UnifiedAudioModel.unavailable()
        assertFalse(audioModel.isCodecObservable)
        assertNull(audioModel.activeCodecName)
        assertFalse(audioModel.isCodecSelectable)
        assertNotNull(audioModel.unavailableReason)
        assertTrue(audioModel.accessibilityAnnouncement.contains("not observable"))
    }

    // --- 5. Component Catalog & Strings Tests ---

    @Test
    fun componentCatalogCoversAllComponentKinds() {
        val kindsInCatalog = ComponentCatalogSpec.entries.map { it.kind }.toSet()
        for (kind in ComponentKind.values()) {
            assertTrue(kindsInCatalog.contains(kind), "ComponentCatalogSpec missing entry for $kind")
        }
    }

    @Test
    fun stringsDictionaryHasAllKeyConstants() {
        assertTrue(OmniBudsStrings.APP_NAME.isNotBlank())
        assertTrue(OmniBudsStrings.FEATURE_ANC.isNotBlank())
        assertTrue(OmniBudsStrings.FEATURE_TRANSPARENCY.isNotBlank())
        assertTrue(OmniBudsStrings.OP_WORKING.isNotBlank())
        assertTrue(OmniBudsStrings.PERMISSION_REQUIRED_GUIDANCE.isNotBlank())
        assertTrue(OmniBudsStrings.ADAPTER_DISABLED_GUIDANCE.isNotBlank())
    }
}
