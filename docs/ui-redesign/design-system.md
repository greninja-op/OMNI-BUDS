# OmniBuds Design System Specification (v1.0)

## 1. Visual Identity & Brand Philosophy

OmniBuds embodies an **authentic hardware-control aesthetic**: technical, precise, high-contrast, and focused. It avoids generic consumer softness in favor of purposeful industrial instrumentation.

### Core Brand Principles:
- **Fluorescent Yellow (`#CCFF00` / `#D4FF00`)**: The signature signal color. Used with disciplined restraint for primary actions, active connection indicators, selected navigation items, and critical states. It cuts through the dark canvas with maximum legibility.
- **Deep Black Canvas (`#0B0C0E`)**: Near-absolute black backdrop that minimizes power consumption on OLED panels and creates deep contrast.
- **Dark Charcoal Surfaces (`#16181C` / `#1F2228`)**: Distinct structural card layers that organize hardware telemetry without unnecessary borders or visual noise.
- **Hairline Precision Borders (`#262930`)**: Replaces blurry drop shadows with crisp 1dp structural borders for an engineered hardware feel.
- **Uniform Outline Iconography**: 100% vector-based, outline-style glyphs with a consistent 24x24dp viewport, 1.5–2dp stroke weight, and optical center alignment. Zero AI-generated images and zero emoji placeholders.

---

## 2. Color Palette & Tokens

### 2.1 Dark Hardware Theme (Primary)

| Token Name | Hex Code | Role & Usage | Contrast Ratio (vs Canvas) |
|---|:---:|---|:---:|
| `OmniCanvas` | `#0B0C0E` | Main application background (Deep Black) | Base (1:1) |
| `OmniSurfaceDefault` | `#16181C` | Primary cards, list items, container backgrounds | 1.15:1 |
| `OmniSurfaceElevated` | `#1F2228` | Elevated cards, top bars, bottom sheets, dialogs | 1.35:1 |
| `OmniSurfaceActive` | `#282C34` | Hovered, focused, or active card states | 1.6:1 |
| `OmniBorderSubtle` | `#262930` | 1dp outline borders on cards and segmented controls | 1.5:1 |
| `OmniBorderFocused` | `#D4FF00` | 2dp focus ring for keyboard/switch navigation | 18.2:1 (AAA) |
| `OmniAccentPrimary` | `#D4FF00` | Primary call-to-action, active toggles, selected nav pill | 18.2:1 (AAA) |
| `OmniAccentDim` | `#99B800` | Secondary accent, inactive progress bars | 9.4:1 (AAA) |
| `OmniTextPrimary` | `#FFFFFF` | Headlines, primary device names, active values | 19.5:1 (AAA) |
| `OmniTextSecondary` | `#9EA3AD` | Subtitles, labels, secondary metadata, inactive tabs | 7.8:1 (AAA) |
| `OmniTextTertiary` | `#5F6573` | Disabled text, minor timestamps, unmeasured hints | 3.5:1 |
| `OmniStatusConnected` | `#22C55E` | Connected Bluetooth state, verified persistence | 8.9:1 (AAA) |
| `OmniStatusWarning` | `#F59E0B` | Stale telemetry, degraded audio link, volatile save | 10.1:1 (AAA) |
| `OmniStatusError` | `#EF4444` | Disconnected error, failed operation, unrecoverable | 5.2:1 (AA) |
| `OmniStatusNeutral` | `#4B515E` | Disconnected idle, unobserved capability | 2.8:1 |

---

## 3. Typography Scale

Built with the system sans-serif font family (Roboto / Inter) tuned for high legibility on hardware instrumentation displays:

| Style Name | Size | Weight | Line Height | Tracking | Usage |
|---|:---:|:---:|:---:|:---:|---|
| `HeadlineLarge` | 28sp | SemiBold (600) | 34sp | -0.2sp | Screen titles (Devices, Workspace) |
| `HeadlineMedium` | 22sp | SemiBold (600) | 28sp | 0.0sp | Device model name in hero view |
| `TitleLarge` | 18sp | Medium (500) | 24sp | +0.1sp | Card group headers, section titles |
| `TitleMedium` | 15sp | Medium (500) | 20sp | +0.1sp | Device list card item title |
| `BodyLarge` | 15sp | Normal (400) | 22sp | +0.2sp | Descriptive guidance, dialog message |
| `BodyMedium` | 13sp | Normal (400) | 18sp | +0.2sp | Metadata values, secondary descriptions |
| `LabelLarge` | 14sp | SemiBold (600) | 18sp | +0.4sp | Button labels, primary action buttons |
| `LabelMedium` | 12sp | Medium (500) | 16sp | +0.5sp | Navigation bar labels, status badges |
| `LabelSmall` | 10sp | Bold (700) | 14sp | +0.6sp | Chip tags, battery percentage pill |

---

## 4. Spacing & Grid System

Based on a strict **4dp base unit**:
- `space-2` (2dp): Micro hair spacing, badge insets
- `space-4` (4dp): Inset borders, chip internal padding
- `space-8` (8dp): Component internal spacing, icon-to-text margin
- `space-12` (12dp): Card vertical content spacing, list item margins
- `space-16` (16dp): Standard screen horizontal gutter, card padding
- `space-20` (20dp): Section separation between distinct cards
- `space-24` (24dp): Group headers margin top
- `space-32` (32dp): Hero card bottom margin, empty state illustration padding
- `space-48` (48dp): Minimum touch target dimension

---

## 5. Corner Radius System

- `RadiusNone` (`0dp`): Edge-to-edge bars, dividers
- `RadiusSmall` (`4dp`): Small badges, status indicators, sliders
- `RadiusMedium` (`8dp`): Secondary cards, button controls, input fields
- `RadiusLarge` (`12dp`): Primary device cards, modal dialogs, elevated containers
- `RadiusPill` (`999dp`): Primary action buttons, segmented control thumbs, navigation pills

---

## 6. Surface Elevation & Layering

OmniBuds uses **tonal elevation and hairline border strokes** rather than fuzzy drop shadows:
- **Layer 0 (Canvas)**: `#0B0C0E` — Full screen window background
- **Layer 1 (Card / Content Surface)**: `#16181C`, with `1dp` border of `#262930`
- **Layer 2 (Elevated Sheets / Overlays)**: `#1F2228`, with `1dp` border of `#353A45`
- **Layer 3 (Floating Bars / Dialogs)**: `#242830`, with `1dp` border of `#404552`

---

## 7. Icon Conventions

1. **Vector-Only Constraint**: All icons are defined as pure vector paths (`ImageVector`) in 24x24dp viewports.
2. **Stroke Uniformity**: Consistent stroke weight (1.75dp visual equivalent) across all glyphs.
3. **No AI Images**: Never embed or generate raster graphics (.png, .webp, .jpg) for UI controls.
4. **No Emoji Strings**: Emojis are banned as UI icon stand-ins.
5. **Tint Policy**: Icons adapt strictly to `LocalContentColor`, tinting fluorescent yellow when active/selected, white for primary, and `#9EA3AD` for inactive.

---

## 8. Motion & Micro-Interactions

- **Duration**: Fast and decisive (150ms for state changes, 220ms for screen transitions).
- **Easing**: `FastOutSlowInEasing` for structural movement; `LinearOutSlowInEasing` for dialogs and sheets.
- **Scanning Radar Animation**: Subtle concentric ring expansion (1.0 to 1.8 scale with 1.0 to 0.0 alpha fade over 1400ms) indicating active Bluetooth scanning.
- **Tactile Feedback**: Interactive cards provide subtle scale bounce (`0.98f`) on press with standard Android haptic click.

---

## 9. Accessibility Specifications

- **Touch Targets**: All clickable surfaces maintain minimum **48x48dp** touch target bounds (`AndroidTouchTargets.minTouchTargetDp`).
- **Contrast Ratios**: All primary text maintains WCAG AAA (19.5:1), secondary text maintains WCAG AAA (7.8:1), and fluorescent yellow accent exceeds 18:1.
- **Screen Reader (TalkBack)**: Every custom widget, battery bar, and segmented control provides explicit semantic content descriptions (e.g., *"Left earbud battery 85%, charging"*).
