# OmniBuds Component Catalog Specification

This catalog details the modular, reusable Jetpack Compose components designed for the OmniBuds visual overhaul. All components adhere strictly to the **fluorescent yellow / dark charcoal / deep black** palette, uniform outline vector icons, and a minimum **48dp** touch target.

---

## 1. Top App Bars

### `OmniTopAppBar`
- **Purpose**: Provides screen identification, navigation back actions, connection state status pills, and contextual menus.
- **Visual Spec**:
  - Container Color: `OmniCanvas` (`#0B0C0E`), zero drop shadow.
  - Hairline Bottom Border: `1dp` of `OmniBorderSubtle` (`#262930`).
  - Title: `HeadlineLarge` (22sp) in `OmniTextPrimary` (`#FFFFFF`).
  - Navigation Icon: 48x48dp touch box enclosing `OmniBudsOutlineIcons.ArrowBack`.
  - Action Slot: Accommodates status pill badges (e.g., `Bluetooth Active`) or icon buttons.

---

## 2. Navigation Surfaces

### `OmniBottomNavBar`
- **Purpose**: Primary bottom tab navigation for compact handheld phone layouts (<600dp width).
- **Visual Spec**:
  - Container Color: `OmniSurfaceElevated` (`#1F2228`).
  - Active Indicator: Pill-shaped container (`RadiusPill`) with `OmniAccentPrimary` (`#D4FF00`) tint at 15% opacity and a solid fluorescent yellow vector outline icon.
  - Active Text: `LabelMedium` in `OmniAccentPrimary` (`#D4FF00`).
  - Inactive Items: Outline vector icon and `LabelMedium` in `OmniTextSecondary` (`#9EA3AD`).
  - Touch Target: 64dp total height, minimum 48dp interactive width per item.

### `OmniNavigationRail`
- **Purpose**: Side navigation for landscape orientations, foldables, and large screen displays (>=600dp width).
- **Visual Spec**: Same visual hierarchy as `OmniBottomNavBar`, aligned vertically along the left screen border.

---

## 3. Structural Containers & Cards

### `OmniCard`
- **Purpose**: Foundational surface container for all content groupings, settings, and hardware telemetry.
- **Visual Spec**:
  - Background: `OmniSurfaceDefault` (`#16181C`).
  - Border: `1dp` solid border of `OmniBorderSubtle` (`#262930`).
  - Corner Radius: `RadiusLarge` (`12dp`).
  - Internal Padding: `16dp` default.
  - Press State: Subtle scale compression (`0.985f`) with `OmniSurfaceActive` (`#282C34`) background transition.

### `DeviceSummaryCard`
- **Purpose**: High-visibility representation of a paired or connected Bluetooth device on the dashboard.
- **Contents**:
  - Device vector icon (Headphones outline in fluorescent yellow accent).
  - Primary title (Model name) + secondary link subtitle (A2DP / HFP / LE Audio).
  - Status pill badge (`CONNECTED` in green, `DISCONNECTED` in neutral grey).
  - Mini battery telemetry strip (Left, Right, Case).
  - Trailing navigation chevron.

---

## 4. Hardware Telemetry & Control Components

### `SegmentedControlSwitch`
- **Purpose**: Tactile 3-way or 2-way hardware state selector (e.g. `Noise Cancelling` | `Ambient` | `Off`).
- **Visual Spec**:
  - Enclosing Track: Height 48dp, background `OmniCanvas` (`#0B0C0E`), `1dp` border `#262930`, `RadiusPill`.
  - Active Thumb: Smooth animated slider thumb with background `OmniAccentPrimary` (`#D4FF00`), text in `OmniCanvas` (`#0B0C0E` bold).
  - Inactive Segment: Text in `OmniTextSecondary` (`#9EA3AD`), full 48dp touch target per segment.

### `BatteryLevelIndicator`
- **Purpose**: Displays multi-component battery status (Left Bud, Right Bud, Charging Case).
- **Visual Spec**:
  - Layout: Horizontal tri-pill or row with dedicated component badges.
  - Active Level: High-contrast bar in `OmniAccentPrimary` (`#D4FF00`). Below 20% switches to `OmniStatusError` (`#EF4444`).
  - Charging State: Vector lightning bolt icon beside percentage.
  - Unobserved State: Displays `"Not reported"` with a neutral grey dashed bar. Never fabricates `0%`.

### `ScanningRadarCard`
- **Purpose**: Renders the active Bluetooth device scanning visualizer.
- **Visual Spec**:
  - Dynamic Concentric Rings: Animated vector circles pulsing outward in fluorescent yellow.
  - Central Device Glyph: Outline Bluetooth transceiver.
  - Status Text: Displays active search countdown and discovered count.

---

## 5. Feedback & Notice Banners

### `StatusPillBadge`
- **Purpose**: Compact semantic status indicator for connection, verification, and protocol states.
- **Variants**:
  - `CONNECTED`: Green background (15% alpha) + Green text (`#22C55E`).
  - `VERIFIED`: Fluorescent yellow background (15% alpha) + Yellow text (`#D4FF00`).
  - `STALE` / `WARNING`: Amber background (15% alpha) + Amber text (`#F59E0B`).
  - `ERROR`: Red background (15% alpha) + Red text (`#EF4444`).

### `CapabilityNoticeCard`
- **Purpose**: Translucently rendered hardware limitation notice.
- **Visual Spec**:
  - Border: Dashed `1dp` border in `OmniTextTertiary` (`#5F6573`).
  - Background: `OmniSurfaceDefault` (`#16181C`).
  - Content: Clear statement explaining that the device or Android OS does not expose the capability, maintaining total honesty with the user.
