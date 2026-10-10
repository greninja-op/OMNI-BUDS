# Phase 48 — Desktop Application Architectural Decisions

## ADR-P48-001: Headless Pure-Kotlin Presentation Architecture

### Context
The execution environment lacks an active display server (X11/Wayland) and cannot download Compose Multiplatform GUI artifacts over TCP. Traditional desktop UI implementations tightly couple view models and presentation state to graphical canvas rendering, preventing thorough automated verification in headless CI environments.

### Decision
Implement the entire desktop presentation layer as pure Kotlin MVI/MVVM:
1. All screen states (`DevicesScreenState`, `WorkspaceScreenState`, `DiagnosticsScreenState`, `SettingsScreenState`), view models, navigation coordinators, window controllers, and theme models are pure Kotlin, depending strictly on `:core` and `kotlinx-coroutines`.
2. UI layout and structure are modeled via a semantic accessibility tree (`AccessibilityNode`), capturing roles, states, actions, and keyboard focus orders.
3. Actual GUI toolkit integration remains a thin rendering boundary (`DesktopUiRenderer`) capable of outputting semantic trees headlessly.

### Consequences
- **Positive**: 100% of UI business logic, user flows, error presentations, and accessibility models are testable deterministically in headless CI.
- **Positive**: Eliminates runtime crashes caused by missing graphics drivers in automated environments.
- **Negative**: Visual pixel rendering is not exercised in headless CI; visual layout testing requires interactive desktop execution in Phase 52.

---

## ADR-P48-002: Modular Desktop Boundary in `:platform:desktop`

### Context
Phase 46 established platform seams in `:core/src/main/kotlin/com/omnibuds/core/platform/` and Phase 47 delivered desktop Bluetooth adapter contracts in `core/platform/desktop/`. We evaluated whether the desktop application should reside in a new multiplatform module or in a dedicated `:platform:desktop` module.

### Decision
Place the desktop application in `:platform:desktop`, configured alongside `:platform:android` in `settings.gradle.kts`. `:platform:desktop` depends on `:core` and targets JVM 17.

### Consequences
- **Positive**: Clean separation of concerns. `:core` remains pure and unaware of desktop application logic.
- **Positive**: Enforces one-way dependency direction verified by `DependencyDirectionTest`.
- **Negative**: Requires independent classpath configuration in offline test harness.

---

## ADR-P48-003: Headless Accessibility Semantic Tree

### Context
Desktop applications must meet accessibility standards (WCAG 2.1 AA), including keyboard navigation, distinct accessibility roles, screen reader hints, and actionable state reporting.

### Decision
Implement a first-class `AccessibilityNode` semantic hierarchy and `DesktopFocusManager` within `:platform:desktop`. Every screen state can be projected into a complete semantic tree without requiring native AT-SPI / NSAccessibility bindings.

### Consequences
- **Positive**: Complete automated verification of keyboard traversal, actionable node roles, and state labels in unit tests.
- **Positive**: Directly maps to native accessibility bridges (e.g., Compose Accessibility or Java Accessibility API) in production deployments.
