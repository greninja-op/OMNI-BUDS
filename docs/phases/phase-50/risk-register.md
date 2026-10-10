# Phase 50 — Risk Register

| Risk ID | Category | Description | Likelihood | Impact | Mitigation Strategy | Status |
|---|---|---|---|---|---|---|
| **R50-01** | Architecture | Adding `presentation` to `:core` could violate `DependencyDirectionTest`. | Medium | High | Explicitly register `"presentation" to 6` in `areaLayer` map; ensure presentation imports only downward (layers 0..5) and contains zero Android/UI framework code. | Mitigated |
| **R50-02** | Token Drift | Platform-specific themes could diverge from unified tokens over time. | Medium | Medium | Automated cross-platform token consistency test asserting exact equality between platform tokens and core tokens. | Mitigated |
| **R50-03** | State Drift | Android surfaces (Tile, Notification, Widget) might use differing state labels than desktop. | Low | High | All surfaces consume the canonical `DevicePresentationState` and `OmniBudsStrings` from `:core`. | Mitigated |
| **R50-04** | Hardware Truthfulness | Missing battery or unobservable codec telemetry could be accidentally filled with fake defaults. | Low | High | Strict unit tests verifying `null` battery levels remain "Unknown" and codec absence is explained truthfully. | Mitigated |
| **R50-05** | Accessibility Gaps | Desktop keyboard navigation or Android touch targets (<48dp) might fail WCAG standards. | Medium | Medium | Automated tests verifying minimum 48x48dp bounds for interactive Android components and complete accessibility node trees on Desktop. | Mitigated |
| **R50-06** | Test Regression | Refactoring presentation models could break existing 2,025 tests from Phases 48 & 49. | Medium | High | Maintain thin adapter classes and backward-compatible signatures; run full regression test harness frequently. | Mitigated |
| **R50-07** | Display Server Absence | Offline headless environment cannot run pixel-based visual regression tools. | High | Low | Rely on deterministic semantic tree testing, WCAG color contrast formulas, and state assertion tests. | Accepted |
