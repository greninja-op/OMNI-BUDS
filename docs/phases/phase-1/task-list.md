# Phase 1 — Task List

ID grammar: `TASK-P1-<NNN>` per ADR-P0-013. The Phase 1 prompt's section 48 shorthand (`P1-T001` … `P1-T027`) maps onto this list in the table at the end; nothing was dropped in the translation, and the extra tasks are the work the prompt's list did not anticipate.

Owner legend: **O** orchestrator, **W2** device workstream, **W3** capability workstream, **W4** audio workstream, **W5** config/diagnostics workstream, **W6** protocol/transport workstream, **W7** persistence/doubles workstream, **W8** requirements+specs docs, **W9** design/review docs, **W10** test/review docs.

Status legend: `done` = acceptance criteria verified against the repository (build output, a named test, or a file read back). No task is `done` because an agent said it was finished.

---

## Foundation

### TASK-P1-001 — Inspect repository, build system and toolchain
**Objective.** Establish ground truth before creating anything (prompt section 3).
**Dependencies.** Phase 0 complete. **Owner.** O.
**Files.** `docs/phases/phase-1/repository-analysis.md`.
**Notes.** No `java` or `gradle` on `PATH`; a JDK 17 found at the user-level install; Gradle 8.9/9.2.0 distributions and AGP 8.7.3/8.5.2 plus Kotlin 2.0.21 artifacts already cached; Android SDK with platform `android-35` and build-tools 34.0.0/35.0.0; not a git repository.
**Acceptance.** Every item in prompt sections 33 and section 4 Agent 1 has a recorded finding, including absences.
**Verification.** Read-back of the analysis document. **Status.** `done`

### TASK-P1-002 — Obtain the user's decision on the two deferred Phase 0 items
**Objective.** Resolve ADR-P0-021 (repository initialisation) and the Phase 1 module-shape question rather than assuming either.
**Dependencies.** TASK-P1-001. **Owner.** O.
**Outcome.** Approved: `git init` plus `.gitignore` with a commit after Phase 1; module shape `:core` + `:platform:android` library boundary, no application shell or UI.
**Acceptance.** Both decisions recorded with their consequence, not just the answer.
**Verification.** `decisions.md` ADR-P1-001; Phase 0 `decisions/README.md` open items closed. **Status.** `done`

### TASK-P1-003 — Initialise version control
**Dependencies.** TASK-P1-002. **Owner.** O.
**Files.** `.gitignore`, `.gitattributes`, `local.properties` (git-ignored).
**Notes.** `.gitignore` excludes build output, `local.properties`, IDE state, keystores, and OmniBuds-specific capture artifacts (`*.btsnoop`, `*.pcap`, packet-log and diagnostics-export directories) because those carry Bluetooth identifiers.
**Acceptance.** Ignored paths verified by `git check-ignore`; no machine-local file staged.
**Verification.** Inspected; four commits landed on branch `main`. **Status.** `done`

### TASK-P1-004 — Finalise module structure
**Dependencies.** TASK-P1-003. **Owner.** O.
**Files.** `settings.gradle.kts`, `core/build.gradle.kts`, `platform/android/build.gradle.kts`.
**Acceptance.** Two modules only; `:platform:android` depends on `:core` and not the reverse; no empty placeholder module was created for a phase that has not started (ADR-P1-001).
**Verification.** `./gradlew projects` plus `DependencyDirectionTest.platformAndroidModuleStillContainsNoSources`. **Status.** `done`

### TASK-P1-005 — Configure the Gradle foundation
**Dependencies.** TASK-P1-004. **Owner.** O.
**Files.** `gradle/wrapper/*`, `gradlew`, `gradlew.bat`, `gradle.properties`, `gradle/libs.versions.toml`.
**Notes.** Two script errors were found and fixed on the way: a version-catalog accessor written as `libs.versions.android.compileSdk` for a key named `androidCompileSdk`, and catalog reads placed inside the `android {}` receiver where they do not resolve. Both are recorded in `repository-analysis.md` rather than hidden.
**Acceptance.** Wrapper reproducible from a clean checkout; no machine-specific path in any committed file; repositories pinned with `FAIL_ON_PROJECT_REPOS`.
**Verification.** `./gradlew build` BUILD SUCCESSFUL. **Status.** `done`

### TASK-P1-006 — Establish the package structure and area layer map
**Dependencies.** TASK-P1-005. **Owner.** O.
**Files.** `core/src/main/kotlin/com/omnibuds/core/<area>/` (11 areas).
**Acceptance.** `com.omnibuds.core.<area>` per ADR-P1-002; every area registered in the layer map.
**Verification.** `DependencyDirectionTest.everyCoreAreaIsRegisteredInTheLayerMap`, `packageStatementsMatchSourceDirectories`. **Status.** `done`

### TASK-P1-007 — Author the shared kernel serially
**Objective.** Pin the vocabulary every parallel workstream would otherwise invent differently.
**Dependencies.** TASK-P1-006. **Owner.** O.
**Files.** `common/FeatureId.kt`, `common/TransportKind.kt`, `common/OmniBudsError.kt`, `common/OmniBudsErrorCategory.kt`, `common/RetryClass.kt`, `common/OperationOutcome.kt`, `state/CapabilityState.kt`, `state/VerificationLevel.kt`, `state/ConnectionState.kt`, `state/SessionClassification.kt`.
**Acceptance.** Kernel compiles alone under `allWarningsAsErrors` before any workstream starts.
**Verification.** `:core:compileKotlin`; now covered by `FeatureIdTest`, `OmniBudsErrorCategoryTest`, `OperationOutcomeTest`. **Status.** `done`

## Domain models

### TASK-P1-008 — Device identity and firmware evidence
**Owner.** W2. **Files.** `device/DeviceIdentity.kt`, `device/FirmwareInfo.kt`.
**Acceptance.** Unknown fields stay unknown; blank text cannot masquerade as a known value; firmware evidence is a separate record from identity because an update changes one and not the other.
**Verification.** `DeviceIdentityTest`. **Status.** `done`

### TASK-P1-009 — Device fingerprint
**Owner.** W2. **Files.** `device/DeviceFingerprint.kt`, `device/ManufacturerDataEntry.kt`.
**Acceptance.** Deterministic order-stable `identityKey()` that excludes volatile fields and carries no MAC address; empty evidence distinguishable from sparse evidence.
**Verification.** `DeviceFingerprintTest`. **Status.** `done`

### TASK-P1-010 — Connection state model
**Owner.** O (kernel), W2 (initial session holder).
**Files.** `state/ConnectionState.kt`, `state/ConnectionStateTransitions` (same file).
**Acceptance.** Eleven states per prompt section 12; illegal moves refused; every state can reach `ERROR`; self-transition idempotent.
**Verification.** `DeviceStateTest` (after TASK-P1-026 moved ownership). **Status.** `done`

### TASK-P1-011 — Device session record
**Owner.** W2. **Files.** `device/DeviceSession.kt`.
**Acceptance.** Saved versus temporary distinguished; saving is explicit; forgetting claims nothing about the pairing record or hardware.
**Verification.** `DeviceSessionTest`. **Status.** `done` — rewritten by TASK-P1-026.

### TASK-P1-012 — Battery model
**Owner.** W2. **Files.** `device/BatteryState.kt`.
**Acceptance.** Unknown level is null and never 0; a real 0 reading survives; range validated; asymmetry requires both sides known.
**Verification.** `BatteryStateTest` — one assertion in it was self-contradictory and was corrected during integration. **Status.** `done`

### TASK-P1-013 — Capability model
**Owner.** W3. **Files.** `capability/FeatureCapability.kt`.
**Acceptance.** Six states; affordances consistent with state enforced by construction; `PERSISTENCE_VERIFIED` impossible without matching evidence tier.
**Verification.** `FeatureCapabilityTest`. **Status.** `done`

### TASK-P1-014 — Device capabilities container
**Owner.** W3. **Files.** `capability/DeviceCapabilities.kt`.
**Acceptance.** Absent feature reads `UNKNOWN`, never `UNSUPPORTED`; immutable updates; merge never downgrades better-established state.
**Verification.** `DeviceCapabilitiesTest`. **Status.** `done`

### TASK-P1-015 — Feature identity system and core feature registry
**Owner.** W3, O (kernel grammar). **Files.** `capability/CoreFeature.kt`, `capability/FeatureCategory.kt`, `capability/CapabilityDefinition.kt`.
**Acceptance.** Fifteen core features namespaced functionally, not by brand; ids unique; none reports itself as a vendor extension.
**Verification.** `CoreFeatureTest`, `FeatureIdTest`. **Status.** `done`

### TASK-P1-016 — Vendor extension model
**Owner.** W3. **Files.** `capability/VendorFeatureMetadata.kt`, `capability/VendorExtension.kt`.
**Acceptance.** A vendor extension cannot be constructed under a core identity, and its vendor segment must match its metadata.
**Verification.** `VendorExtensionTest`. **Status.** `done` — stale workaround documentation removed by TASK-P1-027.

### TASK-P1-017 — Audio transport and codec models
**Owner.** W4. **Files.** `audio/Codec.kt`, `audio/CodecFamily.kt`, `audio/AudioTransportKind.kt`, `audio/ChannelMode.kt`, `audio/QualityMode.kt`, `audio/CodecRegistry.kt`.
**Acceptance.** aptX variants distinct; LC3 in the LE Audio family; an unrecognised vendor codec label resolves to nothing rather than onto a real codec.
**Verification.** `CodecTest`, `CodecRegistryTest`. **Status.** `done`

### TASK-P1-018 — Codec state model
**Owner.** W4. **Files.** `audio/CodecState.kt`, `audio/CodecCapability.kt`.
**Acceptance.** Ordinal ladder replacing six independent booleans; `configurable` orthogonal; supported-but-inactive expressible; `UNKNOWN` distinct from `UNSUPPORTED` (ADR-P1-005).
**Verification.** `CodecStateTest`, `CodecCapabilityTest`. **Status.** `done`

### TASK-P1-019 — Audio quality state
**Owner.** W4. **Files.** `audio/AudioTransportState.kt`.
**Acceptance.** Unobserved sample rate, bit depth and bitrate are null, specifically not 96000/24/328; ranges validated; `isFullyObserved` false unless everything is present.
**Verification.** `AudioTransportStateTest`. **Status.** `done`

### TASK-P1-020 — Error and outcome models
**Owner.** O. **Files.** `common/OmniBudsError.kt`, `common/OmniBudsErrorCategory.kt`, `common/RetryClass.kt`, `common/OperationOutcome.kt`.
**Acceptance.** Sixteen categories with derived retry class and session-invalidation flags; three outcome cases only (ADR-P1-004, ADR-P1-006).
**Verification.** `OmniBudsErrorCategoryTest`, `OperationOutcomeTest`. **Status.** `done`

### TASK-P1-021 — Transport abstraction contracts
**Owner.** W6. **Files.** `transport/TransportContract.kt`, `TransportRequest.kt`, `TransportResponse.kt`, `TransportAvailability.kt`.
**Acceptance.** No transport assumed available; array-backed payload equality correct; unavailable transport requires a reason.
**Verification.** `TransportRequestTest`, `TransportResponseTest`, `TransportAvailabilityTest`. **Status.** `done`

### TASK-P1-022 — Protocol abstraction contracts
**Owner.** W6. **Files.** `protocol/EarbudProtocol.kt`, `ProtocolDefinition.kt`, `CommandDefinition.kt`, `ResponseDefinition.kt`, `ParsedResponse.kt`, `CapabilityMapping.kt`, `ProtocolParser.kt`, `ProtocolEncoder.kt`, `ProtocolIdentification.kt`, `EffectClass.kt`, five optional capability interfaces, `ProtocolRegistry.kt`.
**Acceptance.** Narrow core protocol with optional capability interfaces rather than a forced-everything god interface; no fake implementation; registry empty.
**Verification.** `ProtocolDefinitionTest`, `CommandDefinitionTest`, `CapabilityMappingTest`, `EffectClassTest`, `ProtocolRegistryTest`, `EarbudProtocolContractTest`. **Status.** `done`

### TASK-P1-023 — Repository abstractions
**Owner.** W7. **Files.** `persistence/SavedDeviceRecord.kt`, `DeviceRepository.kt`, `DiscoveredCapabilityRecord.kt`, `CapabilityRepository.kt`, `ProtocolRecordAccess.kt`.
**Acceptance.** Contracts only, no storage engine; saved record carries a fingerprint-derived key rather than an address; user data and protocol knowledge kept separate.
**Verification.** `DeviceRepositoryContractTest`. **Status.** `done`

### TASK-P1-024 — Configuration, diagnostics and logging models
**Owner.** W5. **Files.** `config/*.kt` (7), `diagnostics/*.kt` (6, including the logging seam).
**Acceptance.** Application, device and protocol configuration are distinct types; feature flags have exactly three kinds and none grants capability support; `TRACE`/`PACKET` require opt-in.
**Verification.** `ConfigurationSeparationTest`, `FeatureFlagTest`, `DiagnosticSeverityTest`, `DiagnosticSnapshotTest`, `SnapshotBuilderTest`, `OmniBudsLoggerTest`. **Status.** `done`

### TASK-P1-025 — Device state and concurrency protection
**Owner.** O. **Files.** `session/DeviceState.kt`.
**Acceptance.** Monotonic `revision`; `applyIfNewer` discards older state from the same session and refuses cross-session adoption; disconnect clears live readings while retaining established capability knowledge.
**Verification.** `DeviceStateTest` (13 tests, added by TASK-P1-026 — the type shipped untested initially). **Status.** `done`

## Cross-cutting corrections found by review

### TASK-P1-026 — Single owner of connection state
**Objective.** The architecture review answered **NO** to question 8: `DeviceSession` and `DeviceState` both held connection state for one device.
**Dependencies.** TASK-P1-011, TASK-P1-025. **Owner.** O.
**Files.** `device/DeviceSession.kt`, `session/DeviceState.kt`, both test files.
**Notes.** The rejected alternative — keep both copies and document one as derived — was refused because a derived copy is still a copy.
**Acceptance.** Exactly one type holds `ConnectionState`; the refusal path returns a structured failure rather than throwing or silently ignoring.
**Verification.** `DeviceStateTest`, `DeviceSessionTest`; recorded as ADR-P1-020. **Status.** `done`

### TASK-P1-027 — Repair the kernel defects the workstreams reported
**Dependencies.** TASK-P1-007, TASK-P1-015, TASK-P1-016. **Owner.** O.
**Files.** `common/FeatureId.kt`, `capability/VendorExtension.kt`.
**Notes.** A workstream found that `isVendorExtension` could never be true for ids `ofVendor` produced (namespace compared as `"vendor"` when it is `"vendor.sony"`); the kernel was fixed rather than the workaround kept. `parseOrNull` also accepted single-segment names that `of()` refuses.
**Acceptance.** Vendor identity detection correct and no longer duplicated by an OR workaround; the decoder enforces the same grammar as the factory.
**Verification.** `FeatureIdTest`, `VendorExtensionTest`. **Status.** `done`

### TASK-P1-028 — Dependency hygiene in `:core`
**Dependencies.** TASK-P1-005, TASK-P1-022. **Owner.** O.
**Files.** `core/build.gradle.kts`, `gradle/libs.versions.toml`.
**Notes.** `kotlinx-coroutines-core` was declared and unused; `suspend` needs no library.
**Acceptance.** No production dependency without a reason (prompt section 34).
**Verification.** `./gradlew build`; recorded as ADR-P1-021. **Status.** `done`

## Validation and documentation

### TASK-P1-029 — Establish test infrastructure and doubles
**Owner.** W7. **Files.** `core/src/test/kotlin/com/omnibuds/core/testing/*` (4 files).
**Acceptance.** Doubles default to empty, never invent success, and live only in test source.
**Verification.** `TestDoublesAreNotHardwareTest`. **Status.** `done`

### TASK-P1-030 — Architecture validation
**Owner.** O. **Files.** `core/src/test/kotlin/com/omnibuds/core/architecture/DependencyDirectionTest.kt`.
**Acceptance.** Eleven mechanical checks; scans fail loudly when the source root is missing rather than passing vacuously.
**Verification.** Executed as part of `:core:test`. **Status.** `done`

### TASK-P1-031 — Build and test execution
**Dependencies.** TASK-P1-008 … TASK-P1-030. **Owner.** O.
**Acceptance.** `./gradlew build` succeeds with `allWarningsAsErrors` in both modules.
**Verification.** Measured: BUILD SUCCESSFUL; 37 test classes, 302 tests, 0 failures, 0 errors, 0 skipped. **Status.** `done`

### TASK-P1-032 — Phase 1 documentation set
**Dependencies.** TASK-P1-031. **Owner.** W8, W9, W10, O.
**Files.** The eight records in `docs/phases/phase-1/` plus six review documents.
**Acceptance.** Produced from `docs/templates/`; every cited symbol, test name and count verified against the repository.
**Verification.** Two reconciliation passes re-checked the documents after the TASK-P1-026/027/028 corrections. **Status.** `done`

### TASK-P1-033 — Update the root README
**Dependencies.** TASK-P1-032. **Owner.** O.
**Files.** `README.md`.
**Acceptance.** Contains the required hardware-truth statement verbatim; states Android-first, Kotlin, KMP-ready, backend-first, audio-path isolation, vendor protocol architecture and the current phase; claims support for no device.
**Verification.** Read back against prompt section 43. **Status.** `done`

### TASK-P1-034 — Phase 0 regression check
**Dependencies.** TASK-P1-031. **Owner.** O.
**Acceptance.** Phase 0's mechanically executable document checks still pass; no Phase 0 rule was contradicted without an ADR (prompt section 55).
**Verification.** Terminology and ID-grammar scans re-run; Phase 0 `docs/` untouched apart from the status banner. **Status.** `done`

### TASK-P1-035 — Phase 1 validation and stop
**Dependencies.** All above. **Owner.** O.
**Files.** `docs/phases/phase-1/validation.md`.
**Acceptance.** Definition of done walked item by item; the eight architecture questions answered; Phase 2 readiness stated.
**Verification.** `validation.md`. **Status.** `done`

---

## Mapping from the prompt's seed list

| Prompt id | Canonical id | Prompt id | Canonical id |
|---|---|---|---|
| `P1-T001` inspect repository | TASK-P1-001 | `P1-T015` codec model | TASK-P1-017, TASK-P1-018 |
| `P1-T002` finalize module structure | TASK-P1-004 | `P1-T016` codec state | TASK-P1-018 |
| `P1-T003` Gradle foundation | TASK-P1-005 | `P1-T017` `AudioTransportState` | TASK-P1-019 |
| `P1-T004` Kotlin configuration | TASK-P1-005, TASK-P1-006 | `P1-T018` error model | TASK-P1-020 |
| `P1-T005` package structure | TASK-P1-006 | `P1-T019` protocol abstraction | TASK-P1-022 |
| `P1-T006` `DeviceIdentity` | TASK-P1-008 | `P1-T020` repository abstractions | TASK-P1-023 |
| `P1-T007` `DeviceFingerprint` | TASK-P1-009 | `P1-T021` DI strategy | ADR-P1-009 (documented; no code needed) |
| `P1-T008` `DeviceSession` | TASK-P1-011, TASK-P1-026 | `P1-T022` coroutine conventions | TASK-P1-007, specs; `Flow` deferred (ADR-P1-021) |
| `P1-T009` connection state | TASK-P1-010 | `P1-T023` test infrastructure | TASK-P1-029 |
| `P1-T010` capability model | TASK-P1-013 | `P1-T024` domain tests | TASK-P1-029 … TASK-P1-031 |
| `P1-T011` `DeviceCapabilities` | TASK-P1-014 | `P1-T025` architecture validation | TASK-P1-030 |
| `P1-T012` `BatteryState` | TASK-P1-012 | `P1-T026` update README | TASK-P1-033 |
| `P1-T013` audio transport | TASK-P1-017 | `P1-T027` final review and validation | TASK-P1-032 … TASK-P1-035 |
| `P1-T014` codec/audio quality | TASK-P1-019 | — | — |

Tasks with no prompt seed, added because the work was real: TASK-P1-002, TASK-P1-003, TASK-P1-007, TASK-P1-015, TASK-P1-016, TASK-P1-021, TASK-P1-024, TASK-P1-025, TASK-P1-026, TASK-P1-027, TASK-P1-028, TASK-P1-034.

## Summary

35 / 35 tasks `done`. Three of them (TASK-P1-026, TASK-P1-027, TASK-P1-028) exist only because the architecture review and the workstreams found real defects; recording them as tasks rather than as quiet edits is what keeps the phase history truthful.
