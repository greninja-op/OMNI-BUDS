# Phase 50 — Unified UI/UX Technical Specifications

## 1. Design Token Contracts

The unified design tokens are defined in `com.omnibuds.core.presentation.theme`:

```kotlin
package com.omnibuds.core.presentation.theme

enum class OmniBudsThemeMode {
    SYSTEM,
    DARK,
    LIGHT,
    HIGH_CONTRAST,
}

data class OmniBudsColors(
    val background: String,
    val surface: String,
    val surfaceElevated: String,
    val surfaceVariant: String,
    val onBackground: String,
    val onSurface: String,
    val onSurfaceVariant: String,
    val primary: String,
    val onPrimary: String,
    val primaryContainer: String,
    val onPrimaryContainer: String,
    val outline: String,
    val outlineVariant: String,
    val focusRing: String,
    val statusAvailable: String,
    val statusWarning: String,
    val statusError: String,
    val statusNeutral: String,
    val statusActive: String,
) {
    companion object {
        val Dark: OmniBudsColors
        val Light: OmniBudsColors
        val HighContrast: OmniBudsColors
    }
}
```

### Spacing & Sizing Scale
- Grid increment: 4dp
- Scales: `space0` (0), `space4` (4), `space8` (8), `space12` (12), `space16` (16), `space20` (20), `space24` (24), `space32` (32), `space48` (48).

### Corner Radius Shapes
- `radiusNone`: 0
- `radiusSmall`: 4
- `radiusMedium`: 8
- `radiusLarge`: 12
- `radiusExtraLarge`: 16
- `radiusPill`: 999

### Motion & Animation Specifications
- `durationShortMs`: 150 (quick state toggles, button highlights)
- `durationMediumMs`: 300 (card expansions, dialog appearances)
- `durationLongMs`: 500 (navigation page transitions)
- `reducedMotion`: When system reduced-motion is enabled, all durations resolve to 0 ms.

---

## 2. Shared State-to-Label & Terminology Specifications

```kotlin
package com.omnibuds.core.presentation.state

enum class DevicePresentationState(
    val label: String,
    val description: String,
    val isControllable: Boolean,
) {
    UNKNOWN("Unknown", "Device state is unobserved.", false),
    DISCOVERED("Discovered", "Device observed nearby via Bluetooth discovery.", false),
    PAIRED("Paired", "Device is bonded in host OS settings.", false),
    CONNECTING("Connecting…", "Establishing Bluetooth connection.", false),
    CONNECTED_IDENTIFYING("Connected (Identifying)", "Reading device identity and capabilities.", false),
    READY("Ready", "Device is connected and controllable.", true),
    CONTROL_ACTIVE("Control Session Active", "Vendor protocol session active.", true),
    DISCONNECTING("Disconnecting…", "Closing Bluetooth connection.", false),
    DISCONNECTED("Disconnected", "Device is disconnected.", false),
    TEMPORARILY_UNAVAILABLE("Temporarily Unavailable", "Device link is temporarily unreachable.", false),
    ADAPTER_UNAVAILABLE("Bluetooth Unavailable", "Host Bluetooth adapter is not present.", false),
    ADAPTER_DISABLED("Bluetooth Disabled", "Host Bluetooth radio is turned off.", false),
    PERMISSION_REQUIRED("Permission Required", "Bluetooth permission not granted by OS.", false),
    PLATFORM_UNSUPPORTED("Platform Unsupported", "Bluetooth stack unsupported on this OS.", false),
    READ_ONLY("Read-Only", "Telemetry observable but settings cannot be modified.", false),
    OPERATION_PENDING("Working…", "Control command executing on device.", false),
    OPERATION_REJECTED("Operation Rejected", "Device or authorization policy rejected command.", false),
    OUTCOME_UNKNOWN("Outcome Unknown", "Command completion could not be verified.", false),
    PERSISTENCE_VERIFIED("Persistence Verified", "Setting verified retained across power cycles.", true),
}
```

---

## 3. Hardware-Control Feedback Specifications

```kotlin
package com.omnibuds.core.presentation.control

enum class ControlExecutionStatus {
    IDLE,
    PENDING,
    SUCCEEDED,
    REJECTED,
    TIMED_OUT,
    AMBIGUOUS,
    FAILED,
}

enum class FeatureCapabilityKind {
    UNSUPPORTED,
    UNKNOWN,
    READ_ONLY,
    VOLATILE,
    PERSISTENT,
    PERSISTENCE_VERIFIED,
}

data class UnifiedHardwareControlModel(
    val featureId: String,
    val displayName: String,
    val category: String,
    val capabilityKind: FeatureCapabilityKind,
    val currentValue: String?,
    val desiredValue: String?,
    val acknowledgedValue: String?,
    val observedValue: String?,
    val executionStatus: ControlExecutionStatus,
    val availableOptions: List<String>,
    val failureReason: String?,
    val explanation: String?,
) {
    val isActionable: Boolean
        get() = capabilityKind in listOf(
            FeatureCapabilityKind.VOLATILE,
            FeatureCapabilityKind.PERSISTENT,
            FeatureCapabilityKind.PERSISTENCE_VERIFIED,
        ) && executionStatus != ControlExecutionStatus.PENDING
}
```

---

## 4. Truthful Battery & Audio Presentation Contracts

```kotlin
package com.omnibuds.core.presentation.battery

data class UnifiedBatteryComponent(
    val componentName: String,
    val levelPercent: Int?,
    val isCharging: Boolean?,
    val isPresent: Boolean = true,
) {
    init {
        levelPercent?.let {
            require(it in 0..100) { "levelPercent must be 0..100, was $it" }
        }
    }
}

data class UnifiedBatteryModel(
    val isAvailable: Boolean,
    val components: List<UnifiedBatteryComponent>,
    val overallPercent: Int?,
    val isCharging: Boolean?,
    val lastUpdatedMillis: Long?,
    val isStale: Boolean,
    val unavailableReason: String?,
)
```

```kotlin
package com.omnibuds.core.presentation.audio

data class UnifiedCodecItem(
    val codecName: String,
    val isSupported: Boolean,
    val isConfigurable: Boolean = false,
    val isNegotiated: Boolean = false,
    val isActive: Boolean = false,
)

data class UnifiedAudioModel(
    val activeCodecName: String?,
    val isCodecObservable: Boolean,
    val isCodecSelectable: Boolean,
    val knownSupportedCodecs: List<UnifiedCodecItem>,
    val audioTransport: String?,
    val routeDescription: String?,
    val observableSampleRateHz: Int?,
    val observableBitDepth: Int?,
    val unavailableReason: String?,
)
```

---

## 5. Accessibility Specifications
- **Contrast Ratios**: Verified via automated RGB luminance calculation.
  - Dark Mode: `onBackground` (`#E6EDF3`) on `background` (`#121316`) = 15.6:1 (Passes AAA).
  - Light Mode: `onBackground` (`#0F172A`) on `background` (`#F8FAFC`) = 16.8:1 (Passes AAA).
  - High Contrast: `onBackground` (`#FFFFFF`) on `background` (`#000000`) = 21:1 (Passes AAA).
- **Target Sizes**:
  - Android: `minTouchTargetDp` = 48dp.
  - Desktop: Comfortable padding (8dp..12dp) with visible focus ring (2px solid `focusRing`).
- **Screen Reader Announcements**:
  - Battery: Formatted as `"{Component} {N} percent, charging"` with `", reading may be outdated"` when stale.
  - Codecs: Explicit explanation when unobservable, never fabricating readings.

---

## 6. Localization & Terminology Dictionary

Centralized dictionary defined in `com.omnibuds.core.presentation.strings.OmniBudsStrings`:
- App Name: `"OmniBuds"`
- Common Capabilities:
  - ANC: `"Active Noise Cancellation"`
  - Transparency: `"Transparency"`
  - Normal: `"Normal (Off)"`
  - Equalizer: `"Equalizer"`
  - Spatial Audio: `"Spatial Audio"`
  - Multipoint: `"Multipoint Connection"`
  - Wear Detection: `"In-Ear Detection"`
  - Gestures: `"Touch Controls"`
- Error Messages:
  - Unauthorized: `"Operation was not authorized by security policy."`
  - Timeout: `"Hardware did not acknowledge command in time."`
  - Ambiguous: `"Connection dropped before hardware acknowledged change."`
  - Stale: `"Telemetry reading may be outdated."`
  - Bluetooth Disabled: `"Bluetooth radio is disabled. Please turn it on in system settings."`
  - Permission Denied: `"Bluetooth permission was denied. Grant permission in system settings to discover devices."`
