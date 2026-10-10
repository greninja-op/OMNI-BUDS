# Phase 50 — Unified UI/UX Consolidation Requirements

## 1. Overview & Scope Baseline
This document specifies the authoritative requirements for Phase 50: Unified UI/UX Consolidation of OmniBuds across Android and Desktop platforms. The objective is to establish one coherent product experience sharing a visual identity, semantic design tokens, common terminology, truthful hardware-capability states, and accessibility standards while preserving platform-native interaction conventions (touch-first on Android, mouse/keyboard on Desktop).

---

## 2. Requirements Matrix

### OB-P50-REQ-001: Centralized Semantic Design Tokens
- **Description**: Define a single, centralized set of platform-neutral design tokens for primary/secondary colors, surface hierarchy, text colors, borders, status indicators, typography scales, spacing grid, component corner shapes, elevation, focus rings, motion conventions, and semantic icon meanings.
- **Rationale**: Eliminates hardcoded visual constants and visual drift between Android and Desktop, ensuring unified brand identity.
- **Dependencies**: Phase 48 DesktopTheme, Phase 49 AndroidThemeTokens.
- **Priority**: P0 (Must Have).
- **Acceptance Criteria**:
  1. Tokens defined in shared platform-neutral layer.
  2. Dark, Light, and High Contrast palettes specified with verified WCAG 2.1 AA/AAA contrast ratios.
  3. Spacing grid strictly based on 4dp/8dp base intervals.
  4. Both Android and Desktop themes consume these shared semantic values.
- **Verification Method**: Automated token consistency test suite verifying shared token resolution across themes.
- **Implementation Status**: Proposed.

---

### OB-P50-REQ-002: Architectural Separation Without Coupling
- **Description**: Share design tokens and presentation semantic contracts through the platform-independent `:core` module (`com.omnibuds.core.presentation`) without introducing UI framework dependencies (no Compose, Android framework, or Desktop GUI libraries in core).
- **Rationale**: Both `:platform:android` and `:platform:desktop` already depend on `:core`. Placing pure Kotlin token definitions in `:core` eliminates duplication without cross-platform contamination.
- **Dependencies**: `:core` module architecture and `DependencyDirectionTest`.
- **Priority**: P0 (Must Have).
- **Acceptance Criteria**:
  1. No Android, Compose, or desktop GUI dependencies in `:core`.
  2. `:platform:android` does not depend on `:platform:desktop` or vice versa.
  3. `DependencyDirectionTest` passes with zero violations.
- **Verification Method**: Architecture check via `DependencyDirectionTest.kt`.
- **Implementation Status**: Proposed.

---

### OB-P50-REQ-003: Reusable Component Catalog & Semantic Contracts
- **Description**: Establish a catalog of reusable components with documented visual contracts, accessibility semantics, interaction states, and unsupported data behavior.
- **Rationale**: Ensures components present identical semantics across platforms (buttons, badges, cards, sliders, toggles, empty states, error banners).
- **Dependencies**: OB-P50-REQ-001.
- **Priority**: P0 (Must Have).
- **Acceptance Criteria**:
  1. Catalog specifies visual appearance, accessibility role, states, and fallbacks.
  2. Desktop headless renderer and Android Compose UI conform to the catalog semantics.
- **Verification Method**: Semantic unit tests on both Android and Desktop component layers.
- **Implementation Status**: Proposed.

---

### OB-P50-REQ-004: Consistent Device Lifecycle & State Terminology
- **Description**: Standardize device presentation state mapping across Android and Desktop. Ensure domain lifecycle states (`ConnectionState`, `SessionClassification`, `CapabilityState`, etc.) map to identical user-facing terminology and badges.
- **Rationale**: Users must not see conflicting state labels (e.g. "Connected" vs "Discovered") for the same physical device state.
- **Dependencies**: Phase 24 Global Device State Engine, Phase 48/49 presentation states.
- **Priority**: P0 (Must Have).
- **Acceptance Criteria**:
  1. 17 standard presentation states mapped consistently across both platforms.
  2. Distinctions between Discovered, Connected, Controllable, and Read-Only are strictly preserved.
  3. Never presents a discovered device as connected.
- **Verification Method**: Unit tests asserting deterministic state-to-label mappings.
- **Implementation Status**: Proposed.

---

### OB-P50-REQ-005: Unified Hardware-Control Feedback Contract
- **Description**: Enforce an identical 10-step interaction contract for hardware feature controls (ANC, Transparency, EQ, Gestures, Wear Detection, Multipoint, Spatial Audio).
- **Rationale**: Prevents optimistic UI updates from misrepresenting unconfirmed hardware states.
- **Dependencies**: Phase 9 Feature Engine, Phase 21 Access Control.
- **Priority**: P0 (Must Have).
- **Acceptance Criteria**:
  1. Six-state capability representation (`UNSUPPORTED`, `UNKNOWN`, `READ_ONLY`, `VOLATILE`, `PERSISTENT`, `PERSISTENCE_VERIFIED`).
  2. Distinct execution statuses (`IDLE`, `PENDING`, `SUCCEEDED`, `REJECTED`, `TIMED_OUT`, `AMBIGUOUS`, `FAILED`).
  3. Unsupported features remain disabled; unconfirmed changes show pending state.
- **Verification Method**: View model and interaction state unit tests on both platforms.
- **Implementation Status**: Proposed.

---

### OB-P50-REQ-006: Truthful Battery & Power Presentation
- **Description**: Consolidate the battery presentation model ensuring identical rules for per-component battery readings (Left, Right, Case), charging indicators, and stale observation detection.
- **Rationale**: Hardware truthfulness: missing battery telemetry must never be presented as 0%.
- **Dependencies**: Phase 16 Battery Engine, Phase 48/49 battery models.
- **Priority**: P0 (Must Have).
- **Acceptance Criteria**:
  1. Unknown battery readings remain `null` / "Unknown", never converted to 0%.
  2. Readings older than stale threshold (60,000 ms) are marked stale.
  3. Unreported components (e.g., case when unavailable) are omitted truthfully.
- **Verification Method**: Battery presentation tests on both platforms.
- **Implementation Status**: Proposed.

---

### OB-P50-REQ-007: Truthful Audio & Codec Telemetry
- **Description**: Consolidate audio and codec presentation models to distinguish supported, available, enabled, negotiated, active, and configurable codecs.
- **Rationale**: Neither Android public APIs nor desktop basic adapters expose active A2DP codecs without elevated/private APIs. No fake codec switching or synthesized sample rates may exist.
- **Dependencies**: Phase 11 Codec Engine, Phase 48/49 audio models.
- **Priority**: P0 (Must Have).
- **Acceptance Criteria**:
  1. Active codec is displayed only when backed by observable evidence.
  2. Unobservable codec states explain the platform limitation honestly.
  3. No fake codec switching controls or synthesized sample rates.
- **Verification Method**: Audio codec presentation tests on both platforms.
- **Implementation Status**: Proposed.

---

### OB-P50-REQ-008: Platform-Native UX Adaptation
- **Description**: Retain platform-native interaction models: touch targets (min 48dp), back gesture, and system surfaces on Android; keyboard shortcuts, focus ring, window resizing, and high-density layouts on Desktop.
- **Rationale**: Unified visual identity does not mean forced pixel identity. Each platform must feel natural.
- **Dependencies**: Android Accessibility Guidelines, Desktop WAI-ARIA guidelines.
- **Priority**: P0 (Must Have).
- **Acceptance Criteria**:
  1. Android enforces minimum 48x48dp touch targets.
  2. Desktop provides full keyboard navigation, logical focus order, and visible focus rings.
  3. No mobile bottom bars forced on desktop; no desktop sidebars forced on compact phones.
- **Verification Method**: Accessibility test suites for Android and Desktop.
- **Implementation Status**: Proposed.

---

### OB-P50-REQ-009: Standardized Navigation & Information Architecture
- **Description**: Standardize top-level destinations across both applications: Devices, Device Workspace, Diagnostics, Settings, and About.
- **Rationale**: Users navigating between mobile and desktop encounter consistent structure and terminology.
- **Dependencies**: Phase 48 DesktopNavigation, Phase 49 AndroidNavigation.
- **Priority**: P0 (Must Have).
- **Acceptance Criteria**:
  1. Both platforms share the exact 5 core destinations.
  2. Selected-device workspace state is isolated per device.
  3. Back navigation behaves deterministically on both platforms.
- **Verification Method**: Navigation coordinator unit tests on both platforms.
- **Implementation Status**: Proposed.

---

### OB-P50-REQ-010: Consolidated Accessibility Standards
- **Description**: Enforce WCAG 2.1 AA/AAA compliance, screen reader announcement synthesis (TalkBack on Android, semantic tree on Desktop), non-color-only state indicators, and text scaling support.
- **Rationale**: Ensures the application is accessible to all users across all platforms.
- **Dependencies**: OB-P50-REQ-001, OB-P50-REQ-008.
- **Priority**: P0 (Must Have).
- **Acceptance Criteria**:
  1. Color contrast meets or exceeds 4.5:1 for normal text (7:1 in High Contrast).
  2. State changes include textual or semantic equivalents (not color alone).
  3. TalkBack and Desktop accessibility nodes synthesize complete descriptions.
- **Verification Method**: Automated accessibility test suites on both platforms.
- **Implementation Status**: Proposed.

---

### OB-P50-REQ-011: Centralized Terminology & Localization Dictionary
- **Description**: Provide a centralized dictionary of user-facing strings, capability labels, and error messages to eliminate terminology discrepancies.
- **Rationale**: Prevents phrasing drift (e.g. "Noise Cancellation" vs "Active Noise Cancellation").
- **Dependencies**: None.
- **Priority**: P1 (Important).
- **Acceptance Criteria**:
  1. Standard labels defined for features, states, and errors.
  2. Both platforms consume the shared dictionary.
- **Verification Method**: Localization consistency unit test.
- **Implementation Status**: Proposed.

---

### OB-P50-REQ-012: Settings & Diagnostics Parity
- **Description**: Align settings preferences (theme mode, high contrast, discovery timeout, diagnostic retention) and ensure sanitized diagnostic event exports.
- **Rationale**: Consistency in user control and debugging capabilities across platforms.
- **Dependencies**: Phase 35 Security Redaction, Phase 48/49 Settings/Diagnostics.
- **Priority**: P1 (Important).
- **Acceptance Criteria**:
  1. Shared settings share identical semantics and validation bounds.
  2. Sensitive identifiers (MAC addresses, keys) are redacted in all UI diagnostic logs.
- **Verification Method**: Settings and diagnostics unit tests on both platforms.
- **Implementation Status**: Proposed.

---

### OB-P50-REQ-013: Security & Privacy Invariance
- **Description**: UI consolidation must never bypass centralized authorization, alter hardware commands, or expose unredacted secrets.
- **Rationale**: Preserves the security boundaries established in Phases 0-49.
- **Dependencies**: Phase 21 Access Control, Phase 35 Security.
- **Priority**: P0 (Must Have).
- **Acceptance Criteria**:
  1. Operations dispatch strictly through established feature authorization engines.
  2. Zero telemetry, analytics, or network permissions introduced.
  3. No raw protocol commands exposed to UI.
- **Verification Method**: Security regression tests (`TileSecurityTest`, `NotificationSecurityTest`, `DependencyDirectionTest`).
- **Implementation Status**: Proposed.

---

### OB-P50-REQ-014: Full Automated Regression Verification
- **Description**: Maintain 100% pass rate across Core, Android, and Desktop test suites, including new design system and cross-platform consistency tests.
- **Rationale**: Ensures consolidation does not introduce regressions into existing functionality.
- **Dependencies**: All prior phase test suites.
- **Priority**: P0 (Must Have).
- **Acceptance Criteria**:
  1. All 2,025 existing baseline tests pass.
  2. New tests validate token consistency, state mappings, and accessibility.
  3. Core, Android, and Desktop build cleanly with `-Werror`.
- **Verification Method**: Execution of offline test harness (`p49-verify.sh` / `p50-verify.sh`).
- **Implementation Status**: Proposed.
