# Phase 46 — Risk Register & Mitigation Strategy

## 1. Identified Architectural & Migration Risks

| Risk ID | Description | Severity | Likelihood | Impact | Mitigation Strategy |
|---|---|---|---|---|---|
| **RSK-46-01** | Toolchain incompatibilities with KMP Gradle plugin in offline environment | High | High | High | Register `kotlin-multiplatform` in version catalog; compile via portable `kotlinc 2.0.21` JVM 17 target without requiring unavailable native toolchains. |
| **RSK-46-02** | Breaking architectural scope tests (`DependencyDirectionTest`) by moving files | Critical | Medium | Critical | Preserve existing `src/main/kotlin` source path layout; all 20+ scope tests pass without weakening. |
| **RSK-46-03** | Android framework types leaking into common core via platform seams | Critical | Low | Critical | Automated check `coreMainSourcesDoNotImportAndroidFrameworks` and `coreReferencesNoAndroidFrameworkTypes` strictly fail build on violation. |
| **RSK-46-04** | Breaking backwards compatibility on `BluetoothPlatformCapabilities` | High | Low | Medium | Added `platformType: PlatformType = PlatformType.UNKNOWN` as a defaulted constructor argument; existing call sites compile unchanged. |
| **RSK-46-05** | Premature desktop abstraction complicating future BlueZ integration | Medium | Low | Medium | Kept transport seams minimal (`PlatformTransportFactory`, `PlatformConnectionSession`); concrete desktop implementation deferred to Phase 47. |
| **RSK-46-06** | Test double leakage into production sources | Critical | Low | Critical | Checked mechanically by `productionSourcesDefineNoTestDoubles`; in-memory implementations use standard naming and zero mock frameworks. |

---

## 2. Contingency & Rollback Plans
- All changes are confined to Layer 1 platform abstractions in `:core` and platform adapter classes in `:platform:android`.
- Should any regression occur, each new file is isolated and can be reverted independently without affecting existing feature engines.
