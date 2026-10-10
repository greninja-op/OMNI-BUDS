# Phase 50 — Architecture & Design Decisions

## Decision 1: Token Sharing via Platform-Independent Core Module (`:core`)
- **Context**: Design tokens must be unified between Android and Desktop without duplicating hex strings and numeric values, while avoiding coupling Android dependencies to Desktop or vice versa.
- **Alternatives Considered**:
  1. *Dedicated `:designsystem` multiplatform module*: Would require updating project build files and offline classpaths without build daemon access.
  2. *Duplicate tokens in both modules*: Violates DRY and leads to visual drift.
  3. *Share platform-neutral definitions in `:core` (`com.omnibuds.core.presentation`)*: Both `:platform:android` and `:platform:desktop` already depend on `:core`. Pure Kotlin data classes and objects can reside in `:core` with zero UI framework dependencies.
- **Decision**: Adopt Option 3. Define platform-neutral tokens, state mappings, and semantic contracts in `com.omnibuds.core.presentation`, and have Android and Desktop theme adapters consume them.
- **Consequences**: Zero new Gradle dependencies, zero risk of Android/Desktop framework leakage, full compile-time validation in offline harness.

---

## Decision 2: Distinct Platform Layouts with Shared Semantics
- **Context**: Mobile and desktop environments possess radically different input mechanisms, screen geometry, and interaction norms.
- **Decision**: Avoid forcing pixel-identical layouts. Mobile uses touch-first card stacks, bottom navigation/drawers, and system back handlers; Desktop uses a navigation rail, high-density tables/cards, keyboard shortcuts, and resizable multi-column views. Both share the exact same tokens, terminology, capability states, and feedback contracts.
- **Consequences**: Preserves native platform user experience without compromising design system cohesion.

---

## Decision 3: Backward-Compatible Type Aliasing for Platform Presentation Adapters
- **Context**: Phase 48 and Phase 49 established extensive unit test suites referencing `com.omnibuds.android.presentation.theme.AndroidColors`, `com.omnibuds.desktop.theme.DesktopColors`, `BatteryPresentationModel`, etc.
- **Decision**: Keep the existing platform-specific class names as thin wrappers or typealiases delegating to the unified core tokens and models.
- **Consequences**: Eliminates code duplication while guaranteeing 100% backward compatibility for all existing tests and call sites.

---

## Decision 4: Centralized 17-State Device Presentation Mapping
- **Context**: Different screens and platforms previously used slightly differing labels (e.g. "Working..." vs "Operation pending", "Ready" vs "Controllable").
- **Decision**: Establish an authoritative enum `DevicePresentationState` in `:core` with canonical label, description, and status color token, mapped deterministically from `GlobalDeviceState`.
- **Consequences**: Both platforms and all surfaces (Android app, Desktop app, Quick Settings tile, notification, widget) use identical vocabulary.

---

## Decision 5: Honest Battery & Codec Presentation Architecture
- **Context**: Hardware telemetry may be missing or unobservable due to OS restrictions.
- **Decision**: Preserve `null` battery readings as "Unknown" (never coerce to 0%). Surface honest OS explanations for unobservable codecs rather than synthesizing fake values.
- **Consequences**: Upholds the project's foundational rule of Hardware Truthfulness.
