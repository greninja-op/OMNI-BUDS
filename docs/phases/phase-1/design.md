# Phase 1 — Design

**Phase:** 1 — Project Foundation & Kotlin Architecture · **Scope ids:** `CORE`, `KMP`, `BUILD`
**Owner:** orchestrator (Agent 2 architecture deliverable is `architecture-review.md`; Agent 1 is `repository-analysis.md`; Agent 5 is `kmp-review.md`)
**Requirements covered:** REQ-P1-001 … REQ-P1-022 (referenced by id and short title only; text lives in `requirements.md`)
**Decisions:** `docs/phases/phase-1/decisions.md` — ADR-P1-001 … ADR-P1-021
**Template:** `docs/templates/design-template.md`; the contents list that governs this file is Phase 1 execution prompt section 46.

Verification basis for everything below: `./gradlew build` BUILD SUCCESSFUL; 71 main source files / 5,196 lines and 41 test files / 5,885 lines; 302 tests in 37 classes, 0 failures, 0 errors, 0 skipped; git branch `main`, 5 commits, HEAD `a5f3bf2`. This document describes the architecture **as built**, not as intended. Where the build is weaker than the prose, that is stated in the section it belongs to and again in section 19. Section 6 carries the architecture correction made at `a5f3bf2` (ADR-P1-020) and section 14 the dependency correction (ADR-P1-021); both replace what earlier drafts of this file recorded as a limitation.

---

## 1. Final module architecture

Two Gradle modules exist (ADR-P1-001). Every other module sketched by prompt section 5 was deliberately not created, because an empty module is a claim of structure without substance.

```text
                         settings.gradle.kts  (root "OmniBuds")
                                    │
                 ┌──────────────────┴──────────────────┐
                 │                                     │
            :core                            :platform:android
            Kotlin/JVM                       com.android.library
            71 files / 5,196 lines           0 Kotlin sources
            no Android, no java.*,           1 source file: AndroidManifest.xml
            zero production deps             (no permissions, no components)
                 ▲
                 │        api(project(":core"))
                 └──────────────────────────────┘
```

`pluginManagement` uses `google()`, `mavenCentral()`, `gradlePluginPortal()`; `dependencyResolutionManagement` sets `FAIL_ON_PROJECT_REPOS` with `google()` + `mavenCentral()` only, so no module can quietly add a repository.

| Module | Layer | Purpose | May depend on | Must NOT depend on |
|---|---|---|---|---|
| `:core` | core domain + abstractions | Platform-independent vocabulary, state model, contracts | stdlib only — **zero production dependencies** (ADR-P1-021); test artifacts are `testImplementation`/`testRuntimeOnly` and never reach a consumer's classpath | `android.*`, `androidx.*`, `java.*`/`javax.*`, any module, any UI/DB/vendor code — asserted by `core/src/test/kotlin/com/omnibuds/core/architecture/DependencyDirectionTest.kt`; an external library is not covered by that scan, so the empty production `dependencies` block is a build-script reading |
| `:platform:android` | platform implementation boundary | The seam Android code enters through | `:core` (as `api`) | `:core` may never depend back on it (ARCH-DEP-001); in Phase 1 it may contain no sources at all, which `platformAndroidModuleStillContainsNoSources` asserts |

Deferred module openings, each owned by the phase that first has real code for it: `:bluetooth:*` Phase 6, `:protocols:<vendor>` Phases 19/41, `:database` Phase 22, `:protocol-lab` Phase 20, `:desktop` Phase 47, `:android` app shell Phase 49 (ADR-P1-001).

Toolchain, pinned in `gradle/libs.versions.toml` and selected only per invocation (ADR-P1-014): Gradle 8.9 wrapper, AGP 8.7.3, Kotlin 2.0.21, JDK 17.0.20 (Microsoft OpenJDK, user-level), compileSdk 35, minSdk 26 (provisional, ADR-P1-015), JVM target 17. `local.properties` holds the SDK path and is git-ignored; no `org.gradle.java.home` is written to any shared Gradle file, so this build cannot disturb another project's toolchain. A root `.editorconfig` is tracked and records those conventions for editors (charset, LF endings, final newline, 4-space Kotlin indentation, 120 columns, TOML/YAML at 2, Markdown exempt from trailing-whitespace and line-length rules); the build neither reads nor enforces it, so it complements ADR-P1-011's compiler-plus-architecture-test baseline rather than adding a tool.

---

## 2. Package architecture

Package root is `com.omnibuds.core.<area>` (ADR-P1-002, which amends Phase 0 `specs.md` §1.2); `com.omnibuds.android.<area>` is reserved for the platform module. One top-level declaration per file, file name matches the declaration, and `packageStatementsMatchSourceDirectories` fails the build if a package drifts from its directory — because every import-based rule below depends on that match.

```text
core/src/main/kotlin/com/omnibuds/core/
├── common/       6   FeatureId · TransportKind · OmniBudsError(+Category) ·
│                     OperationOutcome · RetryClass
├── state/        4   CapabilityState · ConnectionState (+ ConnectionStateTransitions) ·
│                     SessionClassification · VerificationLevel
├── transport/    4   TransportContract · TransportRequest · TransportResponse ·
│                     TransportAvailability
├── device/       6   DeviceIdentity · DeviceFingerprint · DeviceSession · BatteryState ·
│                     FirmwareInfo · ManufacturerDataEntry
├── capability/   7   FeatureCapability · DeviceCapabilities · CapabilityDefinition ·
│                     CoreFeature · FeatureCategory · VendorExtension · VendorFeatureMetadata
├── audio/        9   Codec · CodecFamily · CodecState · CodecCapability · CodecRegistry ·
│                     AudioTransportKind · AudioTransportState · ChannelMode · QualityMode
├── config/       7   ApplicationConfiguration · DeviceConfiguration · ProtocolConfiguration ·
│                     ConfigurationValue · FeatureFlag · FeatureFlagKind · DiagnosticMode
├── diagnostics/  6   OmniBudsLogger · DiagnosticEvent · DiagnosticSeverity ·
│                     DiagnosticCategory · DiagnosticSnapshot · SnapshotBuilder
├── session/      1   DeviceState                       (area created mid-phase, ADR-P1-003c;
│                     the sole owner of connection state, ADR-P1-020)
├── persistence/  5   DeviceRepository · CapabilityRepository · ProtocolRecordAccess ·
│                     SavedDeviceRecord · DiscoveredCapabilityRecord
└── protocol/    16   EarbudProtocol + 5 optional support interfaces · ProtocolDefinition ·
                      CommandDefinition · ResponseDefinition · CapabilityMapping · ProtocolRegistry ·
                      Parser · Encoder · ParsedResponse · ProtocolIdentification · EffectClass
```

No `utils`, `misc`, `helpers` or `new` packages exist (Phase 0 `specs.md` §1.2), and no package name contains a vendor name (ARCH-QUAL-014 has nowhere to live yet).

---

## 3. Dependency direction

Prompt section 32 states the rule in prose; ADR-P1-003 turns it into a layer number per area, and `coreAreasDependOnlyOnMoreFoundationalAreas` enforces it by scanning the imports of every main source file.

```text        layer
  4  protocol        EarbudProtocol, ProtocolDefinition, ProtocolRegistry
                            │
  3  session        persistence
     DeviceState        DeviceRepository, CapabilityRepository,
     (single truth)     ProtocolRecordAccess
                            │
  2  device · capability · audio · config · diagnostics   (five islands)
                            │
  1  state          transport
                            │
  0  common         FeatureId, TransportKind, OperationOutcome, OmniBudsError(+Category), RetryClass
```

The stack above reproduces the `areaLayer` map in `DependencyDirectionTest` verbatim — `common`=0; `state`, `transport`=1; `device`, `capability`, `audio`, `config`, `diagnostics`=2; `session`, `persistence`=3; `protocol`=4 — eleven registered areas, and `everyCoreAreaIsRegisteredInTheLayerMap` fails the build if a twelfth appears without a number.

The rule is stricter than "downward only": an import may point at a **strictly lower layer, or at its own area only**. Two areas sharing a layer may not reference each other at all, which is why layer 2 is five independent islands — `capability` cannot import `device`, `audio` cannot import `config`, and so on. Sideways edges are exactly the ones that turn into cycles later. ADR-P1-020 sits inside this map rather than changing it: `session` stays layer 3 because `DeviceState` composes `capability`, `device` and `audio`, and the rule that one type owns connection state is about *which field lives where*, not about a new edge — `device` now imports `state` for `SessionClassification` alone, where it previously also imported `ConnectionState` and `ConnectionStateTransitions` to keep a second copy.

Forbidden edges, each mechanically checked:

| Forbidden | Checker |
|---|---|
| `core → android.*`, `androidx.*`, `com.omnibuds.android` | `coreMainSourcesDoNotImportAndroidFrameworks` |
| `core → java.*`, `javax.*` | `coreMainSourcesDoNotImportJvmOnlyLibraries` |
| any upward or sideways area import, or an area not registered in the map | `coreAreasDependOnlyOnMoreFoundationalAreas`, `everyCoreAreaIsRegisteredInTheLayerMap` |
| `core → Bluetooth*/AudioTrack/AudioManager/AudioRecord/MediaPlayer` | `coreReferencesNoBluetoothOrAudioFrameworkTypes` |
| stubs (`TODO(`, `NotImplementedError`, `error("`), doubles, or any production implementation of `EarbudProtocol`/`DeviceRepository`/`CapabilityRepository`/`TransportContract` | `coreMainSourcesContainNoPlaceholderImplementations`, `productionSourcesDefineNoTestDoubles`, `noProductionClassImplementsTheProtocolOrRepositoryContracts` |
| magic hex literals or UUID literals | `coreContainsNoHardCodedProtocolLiterals` |
| sources inside `:platform:android` | `platformAndroidModuleStillContainsNoSources` |

Three real violations were found by these checks and fixed during the phase, not before (ADR-P1-003): `TransportKind` moved `transport → common` because the error model needed it (003a); the planned `logging` area folded into `diagnostics` because it needed diagnostic types (003b); `DeviceState` moved out of `state` into the new `session` area because it composes capability, device and audio and would have made `state ↔ capability/device/audio` a cycle (003c). A scan that finds nothing also fails, loudly (`mainSources()`), so these rules cannot pass by silently scanning the wrong directory.

---

## 4. Domain and platform boundary

The boundary is a Gradle module, not a convention. `:platform:android` is a real library module (`platform/android/build.gradle.kts`) whose only dependency is `api(project(":core"))`, with `namespace = "com.omnibuds.android"`, compileSdk 35, minSdk 26, JVM target 17, `allWarningsAsErrors` on, and **zero Kotlin sources**. Its manifest (`platform/android/src/main/AndroidManifest.xml`) declares no permissions, no components and no intent filters — declaring `BLUETOOTH_CONNECT` before any Bluetooth code exists would be a fabricated capability (ADR-P0-001, REQ-P1-003, REQ-P1-009).

Crossing rules, all already satisfied by the domain types:

- Android objects stop at the platform module and are translated into domain vocabulary. `DeviceIdentity` is four nullable strings (manufacturer, model, displayName, modelId); there is no `BluetoothDevice` field anywhere in `:core` (prompt §7).
- Android result codes cross as `OmniBudsErrorCategory` values, never as status integers (ARCH-AND-003).
- A transport implementation is reached only through `TransportContract`, and `:core` may not implement it (ADR-P1-013).
- Platform facts that core must not decide arrive as data: `BluetoothAdapter` state becomes `BLUETOOTH_DISABLED`, denied permission becomes `PERMISSION_DENIED`.

---

## 5. KMP strategy

Android-first, KMP-ready (prompt §8, ARCH-KMP-005). The strategy in one line: `:core` is written as if it were `commonMain` today, so that Phase 46 flips a plugin instead of re-cutting a boundary. Full assessment and the concrete migration steps are in `kmp-review.md`; the design consequences that matter here are:

- No `java.time` / `java.util` types: timestamps are `Long?` epoch millis (ADR-P1-012). `null` means unobserved, and is never `0`.
- Monotonic ordering inside a process is carried by `DeviceState.revision`, not by clock comparison — which is also what makes the wall clock safe to keep as a plain number.
- The only JVM-targeted annotation in the whole module is `@JvmInline` on `FeatureId` (`common/FeatureId.kt:15`), which is legal in common source.
- Zero `kotlinx.coroutines` *library* symbols are used in main sources; `suspend` is a language feature. ADR-P1-021 then removed the artifact itself, so `:core` compiles against the Kotlin stdlib and nothing else — there is no external coordinate to prove portable for a second target. Converting to multiplatform therefore cannot break on the coroutines surface — but see section 14 for what that also means.

```text
              :core  (today Kotlin/JVM, written common-first)
                 │
     ┌───────────┴───────────┐
 androidMain                desktopMain (Phase 47)
 Bluetooth, permissions,    Windows/macOS/Linux Bluetooth
 QST, OS audio, notifs      and desktop integration
```

---

## 6. State architecture

Prompt §24 forbids the three-way disagreement (UI says ON, backend says OFF, notification says UNKNOWN). The only defense is a single authoritative value with one owner; that value is `session/DeviceState.kt` (ADR-P1-003c moved it above the areas it composes, and ADR-P1-020 made the claim literal by deleting the competing copy that `device/DeviceSession.kt` used to hold).

```text
 device / platform reality
        │  (Phase 2+: Android adapter, GATT/RFCOMM callbacks)
        ▼
 TransportContract ──► EarbudProtocol.readState(session: DeviceState)
        │                       │  returns OperationOutcome<DeviceState>
        │                       ▼  with revision + 1
        └──── stale response ──► DeviceState.applyIfNewer(candidate)
                                        │
                                        ▼
                              DeviceState  (THE truth: immutable value)
                                 │        │        │
                    connection ──┘  capabilities  battery / audio
                    ConnectionState  DeviceCapabilities  BatteryState, AudioTransportState
                                        │
                                        ▼
                        application state (Phase 2+), then UI / notification / QST
```

The other session-level type deliberately holds none of that. ADR-P1-020 splits the two questions a caller can ask about a device:

```text
 device/DeviceSession                          session/DeviceState
 "which device, and did the user keep it?"     "where has it got to, and how sure are we?"
   sessionId                                     sessionId  (same value, carried through)
   identity      (DeviceIdentity)                identity     (DeviceIdentity)
   fingerprint   (DeviceFingerprint?)            connection   (ConnectionState)   ◄── the only
   classification(SessionClassification)         capabilities (DeviceCapabilities)     connection
   createdAtEpochMillis                          battery      (BatteryState)           value in the
                                                 audio        (AudioTransportState)     module
                                                 revision     (Long, monotonic)
   changes: withEvidence()  save()  forget()     lastUpdatedEpochMillis (Long?)
   (no time argument: a session value is
    not a state move)                             changes: attemptConnection() → OperationOutcome
                                                        withCapability/withCapabilities/
                                                  withBattery/withAudio, invalidatedForDisconnect
```

Mechanics that make it true:

- **Immutable, total.** `DeviceState` carries `sessionId`, `identity`, `connection`, `capabilities`, `battery`, `audio`, `revision`, `lastUpdatedEpochMillis`. Every mutator (`withCapability`, `withCapabilities`, `withBattery`, `withAudio`, `attemptConnection`, `invalidatedForDisconnect`) produces a **new instance with `revision + 1`**; nothing mutates in place, so no reader can observe a half-applied update (ARCH-STATE-001, ARCH-STATE-003).
- **`revision` is the ordering, not the clock.** `lastUpdatedEpochMillis` is nullable and only descriptive; a machine-clock change can never reorder facts because ordering is decided by the monotonic counter (ADR-P1-012). `DeviceSession` dropped its own `lastStateUpdateEpochMillis` for exactly this reason — `DeviceState.lastUpdatedEpochMillis` already reports it, and a second stamp is a second opinion.
- **`applyIfNewer` defeats the race of prompt §30.** The scenario: user sets ANC = ON → disconnect → a stale response arrives → state wrongly becomes ON. The response comes back through `readState`, which returns a *replacement* `DeviceState`. The owner applies it with `applyIfNewer(candidate)`, which adopts the candidate only when `candidate.sessionId == sessionId && candidate.revision > revision`. A stale response is therefore not an error to handle, it is simply older, and it is discarded while the caller can still tell that the revision did not move. Because the check is inside the data type, no caller has to remember it.
- **Reconnect safety.** `attemptConnection` refuses an illegal move as `OperationOutcome.Failure(OmniBudsError(INVALID_STATE))` and leaves the receiver's connection and revision untouched — refusing by *value*, so the caller keeps its error model instead of catching an exception — and legality comes from `ConnectionStateTransitions.canTransition`, so a callback cannot drag a `DISCONNECTED` device into `CONTROL_SESSION`. `invalidatedForDisconnect` keeps identity and discovered capability history while replacing live battery and audio readings with `BatteryState.unknown()` and `AudioTransportState.unobserved()` — a disconnected device reports unknown, never last-known-as-current (ARCH-STATE-006).
- **Unknown survives.** `initial()` starts `connection = UNKNOWN`, capabilities empty (so every `FeatureId` reads `CapabilityState.UNKNOWN`), battery and audio unobserved, `lastUpdatedEpochMillis = null`. Nothing is zeroed (ARCH-TERM-004, ARCH-QUAL-011).
- **Session evidence only fills gaps.** `DeviceSession.withEvidence` merges identity through `DeviceIdentity.mergedWith` (established fields are never restated) and *replaces* rather than merges a fingerprint, because two discovery passes are two observations, not one; `save()`/`forget()` touch `classification` alone and `temporary(...)` is the only factory. Connecting a device still cannot promote it to saved (ADR-P0-004, SEC-ID-005).

The architecture review's finding, and what closed it: this section previously recorded a named limitation — `session/DeviceState.kt` and `device/DeviceSession.kt` **both carried a `ConnectionState`** (`DeviceState.connection` and `DeviceSession.connectionState`), each file's own documentation claiming to be the single authority, with no test touching `DeviceState` at all. ADR-P1-020 corrected the architecture rather than documenting the defect: `DeviceSession` now declares five properties (`sessionId`, `identity`, `fingerprint`, `classification`, `createdAtEpochMillis`) and no connection state, so no caller can read two values for one device. `session/DeviceStateTest` (12 tests) now exercises the half that was unproved — `revision` ordering, `applyIfNewer` staleness rejection, refusal-without-mutation on an illegal move, disconnect invalidation and `isOperational`. What still rests on inspection: no mechanical check forbids a sixth property that re-introduces a connection state, and no reflection-based test asserts `DeviceSession`'s field list (REQ-P1-008 AC-8, REQ-P1-017 AC-8); the layer rule would catch only an *upward* import, not a same-direction one.

---

## 7. Capability architecture

`state/CapabilityState.kt` is a six-value evidence ladder — `UNKNOWN, UNSUPPORTED, READ_ONLY, SUPPORTED_VOLATILE, SUPPORTED_PERSISTENT, PERSISTENCE_VERIFIED` — with three derivations (`isControllable`, `isEstablished`, `isReadable`) so no call site re-implements the classification (ARCH-CAP-002, REQ-P1-006). Declaration order is meaningful; `DeviceCapabilities.mergedWith` keeps the record with more evidence using the same ladder as its ranking.

`capability/FeatureCapability.kt` makes illegal claims unconstructable through `init` checks: `UNKNOWN` and `UNSUPPORTED` must be neither readable nor writable; `READ_ONLY` must be readable and not writable; the three `SUPPORTED_*` rungs must be both. `SUPPORTED_PERSISTENT` additionally requires `VerificationLevel.HARDWARE_VERIFIED` or better, and `PERSISTENCE_VERIFIED` requires exactly `VerificationLevel.PERSISTENCE_VERIFIED` — which is ARCH-PERSIST-002 expressed as a constructor that refuses, not a comment that hopes. `Boolean?` is used nowhere for capability truth (ARCH-STATE-002).

Feature identity is `common/FeatureId.kt`: a `@JvmInline value class` with a private constructor, only obtainable through `of(vararg segments)` (≥2 segments, each matching lower-kebab), `ofVendor(vendor, feature)`, or `parseOrNull(raw)` for a stored identifier — which enforces the same two-segment grammar rather than guessing a shape, so a one-segment string in a future protocol record decodes to null instead of becoming an identity. `isVendorExtension` is the single shape test for `vendor.<vendor>.<feature>`, and `vendorName` reads the vendor segment. `common/FeatureIdTest` (7) pins all of it. Core features are catalogued in `capability/CoreFeature.kt` — 15 ids in functional namespaces (`noise-control.anc`, `input.gestures`, `power.case-battery`) — whose `init` block checks that the identity list and the definition list describe the same set and that no core id sits under the reserved `vendor` root. `VendorExtension` refuses construction unless the id really is `vendor.<vendor>.<feature>` — by delegating to `FeatureId.isVendorExtension`, no longer by re-implementing the test — **and** its vendor segment equals its `VendorFeatureMetadata.vendor`. That is the whole of "vendor features without polluting common interfaces" (prompt §21, ARCH-PROTO-003): there is no place for `if (sony)`.

---

## 8. Device architecture

`device/DeviceIdentity.kt` — four nullable strings, blank normalised to null, `unknown()`, and `mergedWith` that can only fill gaps and never restate a field already known. `device/DeviceFingerprint.kt` — the evidence bundle (`manufacturerData`, `serviceUuids`, `characteristicUuids`, `deviceClass`, `transportCandidates`, `protocolCandidates`, `firmware`) plus a deterministic, versioned `identityKey()` string prefixed `omnibuds-fingerprint/v1` with punctuation escaped and tokens sorted, so the same evidence always yields the same key. It is what `SavedDeviceRecord` keys on, which keeps a MAC address out of storage (SEC-ID rules, ADR-P1-010). `device/BatteryState.kt` — per-side and case levels as `Int?` bounded to 0..100 with charging flags as `Boolean?` (a *measurement*, where Phase 0 §2.2 permits null; `unknown()` is all-null, never 0%). `device/FirmwareInfo.kt` — three version strings plus the `VerificationLevel` of the evidence behind them. `device/DeviceSession.kt` — after ADR-P1-020 it declares five properties and no connection state: `sessionId`, `identity`, `fingerprint`, `classification` and a nullable caller-supplied `createdAtEpochMillis`. `withEvidence` merges identity through `DeviceIdentity.mergedWith` and replaces rather than merges the fingerprint, `save()` flips only the classification, `forget()` documents precisely that it asserts nothing about the phone's pairing record, `isSaved` derives from the classification, and `temporary(...)` is the companion factory. The illegal-move refusal that used to live here as `transitionedTo` is now `DeviceState.attemptConnection`, which returns `Failure(INVALID_STATE)` rather than throwing (§6); `lastStateUpdateEpochMillis` was dropped here because `DeviceState.lastUpdatedEpochMillis` already reports it.

`state/SessionClassification.kt` (`TEMPORARY`/`SAVED`) is orthogonal to `state/ConnectionState.kt` (11 values) by decision: ADR-P1-016 supersedes Phase 0 `specs.md` §2.3's `SessionState`, so `CONNECTING`, `ACTIVE_SESSION`, `STALE` and `FORGETTABLE` become *derivations of two independent facts* instead of a second source of truth. A device can be connected without being saved (REQ-P1-008) because saving is a field the user changes, not a side effect of connecting.

---

## 9. Audio architecture

Domain concepts only; nothing here touches Android audio APIs (prompt §6 Audio, REQ-P1-002).

`audio/CodecState.kt` is the ordinal ladder `UNKNOWN < UNSUPPORTED < SUPPORTED < AVAILABLE < ENABLED < NEGOTIATED < ACTIVE`, and `audio/CodecCapability.kt` carries `configurable` as a **separate boolean** (ADR-P1-005, amending ADR-P0-015) because configurability is orthogonal: a codec can be `ACTIVE` *and* configurable, and no single-valued ladder can say both. `supportsAtLeast()` deliberately returns false for `UNKNOWN`/`UNSUPPORTED` — those are not rungs on the positive ladder, so "supported at least AVAILABLE" is a claim about an observed codec, never about an absent one. Six booleans would have permitted `active = true, supported = false`; the ladder makes that unrepresentable (REQ-P1-007).

`audio/Codec.kt` names SBC, AAC, aptX, aptX HD, aptX Adaptive, aptX Lossless, LDAC, LC3 (+ `UNKNOWN`), each tagged with a `CodecFamily` (`CLASSIC_A2DP`/`LE_AUDIO`/`UNKNOWN`); `audio/CodecRegistry.kt` resolves a normalised label to a codec and refuses to bind one label to two codecs. `audio/AudioTransportState.kt` is the observed picture: transport kind, nullable codec, `CodecState`, nullable sample rate / bit depth (8..32 enforced) / bitrate, `ChannelMode`, `QualityMode`, with `unobserved()` as the honest starting point and `isFullyObserved` as an explicit statement of how much is actually known.

Open gap, accepted rather than papered over: `CodecCapability` holds one record per codec, so Phase 1 cannot say "the phone supports LDAC, this headset does not" (ADR-P1-018, owned by Phase 11). The ladder still encodes the weakest link correctly; the per-endpoint claim is missing, not faked.

---

## 10. Protocol architecture

`protocol/EarbudProtocol.kt` declares exactly `identify(fingerprint)`, `discoverCapabilities()` and `readState(session)` (ADR-P1-007). Everything else the prompt listed is an **optional capability interface** — `FeatureReadSupport`, `FeatureWriteSupport`, `BatteryReportingSupport`, `FirmwareReportingSupport`, `AudioStateReportingSupport` — that an implementation may or may not implement, discovered by type check plus negotiation. A vendor with no equalizer simply does not implement the interface, instead of returning a fake success (ARCH-PROTO-001/002). Feature reads and writes are generic over `FeatureId` and `config/ConfigurationValue`, so ANC/EQ/gesture *value shapes* are not invented here (ADR-P1-007 explicitly defers them to the phases that own them, REQ-P1-015).

Protocol knowledge is data: `ProtocolDefinition` (id, display name, optional vendor, `TransportKind` — required and never `UNKNOWN`, version, commands, responses, capability mappings, `confidence`), `CommandDefinition` (id, display name, `EffectClass`, timeout, read-attempt bound 1..5 required for reads and *forbidden* for writes, `VerificationLevel`), `ResponseDefinition`, `CapabilityMapping` (which refuses a write command with no `EffectClass`, and a `READ` effect on a write), `ProtocolIdentification` with `MatchEvidence` including `FALLBACK_UNKNOWN` pinned to `INFERRED` confidence, `ParsedResponse` addressed by symbolic field names, `ProtocolParser`/`ProtocolEncoder` as pure `(commandId, ByteArray)` functions. Retry policy is thus a property of the definition (`EffectClass.permitsAutomaticRetry`), never of a call site (ARCH-QUAL-009, Phase 0 `specs.md` §4).

`ProtocolRegistry` ships **empty by design** and `ProtocolRegistryTest` asserts the emptiness, the absence of any answer, and that no fingerprint yields a candidate — one of three independent machine-checked statements that Phase 1 talks to no hardware (ADR-P1-013). It is an immutable value (`register`/`without` return new instances and duplicate ids are refused), deliberately not a singleton: whoever owns the knowledge injects it (ARCH-STATE-003, ADR-P1-009).

The transport half of the boundary is `transport/TransportContract.kt`: `kind`, `isOpen`, `suspend open()/close()/exchange(request, timeoutMillis)`. It is silent about *how* — no characteristic handle, no socket parameter, no UUID, no callback shape (ARCH-DEP-004) — which is what lets one protocol drive a GATT device and an RFCOMM device alike (REQ-P1-002). It does not retry, because retry is a decision about effects. `TransportAvailability` records per-kind, per-purpose findings with `available` and `reason` locked together by construction, so an absent channel is always reported with its cause and a missing GATT service can never be read as "no control channel" (PROTO-XPORT-001/005) — nor silently replaced, since a fallback must be asked for explicitly (PROTO-XPORT-007).

---

## 11. Persistence architecture

Contracts only (prompt §25, ADR-P1-010, REQ-P1-018). `persistence/DeviceRepository` (`savedDevices/save/forget/find/markSeen`), `CapabilityRepository` (`load/store/clear`) and `ProtocolRecordAccess` (`recordsFor(fingerprint)/record(protocolId)`) are suspend interfaces returning `OperationOutcome<...>` keyed by `identityKey`. Records are `SavedDeviceRecord` (fingerprint-derived key, identity, optional firmware, saved/last-seen stamps) and `DiscoveredCapabilityRecord` (key, `DeviceCapabilities`, discovery time, `VerificationLevel`) — the cached evidence is stored *with its verification tier* so a later read cannot silently promote it (ARCH-PERSIST-001).

Absent on purpose: no storage engine, no Room/SQLDelight/SQLite dependency, no DAO, no schema, no migration mechanism, and no implementation of any of these interfaces in `:core` (asserted by `noProductionClassImplementsTheProtocolOrRepositoryContracts`). ARCH-BOUND-003's separation of the protocol database from the user device database is respected by having two contracts (`ProtocolRecordAccess` vs `DeviceRepository`) rather than one repository, and by the forward constraint recorded in ADR-P1-010.

---

## 12. Configuration model

Three configurations, never mixed (prompt §39, REQ-P1-020), and `ConfigurationSeparationTest` (19 config tests total) exists to keep them apart:

- `config/ApplicationConfiguration` — `debugLoggingEnabled`, `DiagnosticMode`, `Set<FeatureFlag>`; `defaults()` is off/OFF/empty, and its `init` refuses any diagnostic mode above `OFF` that has not declared `requiresOptIn`.
- `config/DeviceConfiguration` — `Map<FeatureId, ConfigurationValue>`, i.e. what the device is set to.
- `config/ProtocolConfiguration` — protocol id, optional version, `TransportKind`, optional timeout, optional bounded read attempts.

`FeatureFlag` cannot be constructed from a kind outside `FeatureFlagKind.permitted` (`EXPERIMENTAL_UI`, `EXPERIMENTAL_PROTOCOL_ADAPTER`, `DEBUG_DIAGNOSTIC`), and the constructor additionally refuses **any** kind whose `grantsCapabilitySupport` is true — so "unsupported ANC" cannot be flagged into "supported ANC" even by someone who adds a new kind without editing the set (prompt §40, REQ-P1-015). `ConfigurationValue` is a closed sealed interface (`Boolean`/`Int`/`String`/`ModeValue`) — deliberately not a bag of `Any?`.

---

## 13. Error model

`common/OperationOutcome.kt` has exactly three cases: `Success(value)`, `Failure(error)`, `Cancelled` (ADR-P1-004, REQ-P1-013). Timeout, unsupported, disconnected, protocol mismatch and codec unavailability are categories *inside* `Failure`, not outcome shapes, so call sites handle three cases with the compiler's help. `Cancelled` is not a failure and owes no re-read: the device is untouched by definition.

`common/OmniBudsErrorCategory.kt` holds **16 values**, the union of Phase 0's thirteen and prompt §22's three (ADR-P1-006, amending ADR-P0-012): `BLUETOOTH_DISABLED, PERMISSION_DENIED, DEVICE_DISCONNECTED, TRANSPORT_UNAVAILABLE, GATT_FAILURE, RFCOMM_FAILURE, PROTOCOL_MISMATCH, UNSUPPORTED_FEATURE, READ_FAILED, WRITE_REJECTED, VERIFICATION_FAILED, TIMEOUT, FIRMWARE_MISMATCH, CODEC_UNAVAILABLE, UNKNOWN_DEVICE, INVALID_STATE`. Each carries `retryClass` (`SAFE_TO_RETRY`/`RETRY_AFTER_REREAD`/`NEVER_RETRY`) and `invalidatesSession`, so retry policy is derived from data rather than chosen at the call site: reads are safe to retry, a timed-out write is `RETRY_AFTER_REREAD`, a rejected or unknown-effect write is `NEVER_RETRY`. `OmniBudsError` carries category, operation id, optional detail, `TransportKind` (default `UNKNOWN`) and bounded `attempts`; a failed battery read produces unknown battery, never 0% (ARCH-QUAL-011). The table is no longer prose-only: `common/OmniBudsErrorCategoryTest` (8) pins the sixteen member names, every `invalidatesSession` tag, `READ_FAILED`/`SAFE_TO_RETRY`, the nine `NEVER_RETRY` members and `TIMEOUT`/`RETRY_AFTER_REREAD`, while `common/OperationOutcomeTest` (5) pins the three-case shape, `map` touching only `Success`, and the `attempts >= 1` guard. Five `RETRY_AFTER_REREAD` retry classes remain unpinned (REQ-P1-013 records which).

`TransportKind` lives in `common`, not `transport` (ADR-P1-003a) — it is shared vocabulary. GATT and RFCOMM failures stay distinguishable, which is how "which channel broke" survives into diagnostics.

---

## 14. Concurrency model

The contracts are concurrency-shaped without being concurrency-implemented (prompt §29/§30, REQ-P1-004):

- Every hardware-facing operation is `suspend`: `TransportContract.open/close/exchange`, `EarbudProtocol.identify/discoverCapabilities/readState`, the five optional support interfaces, all three repository interfaces. Blocking client APIs are therefore confined to implementations that do not exist yet.
- Timeouts are explicit parameters and *definitions* (`TransportContract.exchange(timeoutMillis)`, `TransportRequest.timeoutMillis`, `CommandDefinition.timeoutMillis`, `ProtocolConfiguration.timeoutMillis`), never implicit waits.
- Cancellation is a first-class outcome (`OperationOutcome.Cancelled`), and `ScriptedOutcome.cancel()` proves test-side that a cancellation is surfaced as cancellation rather than swallowed into success.
- Shared mutable state: `:core` has none. `DeviceState`, `DeviceSession`, `DeviceCapabilities`, `ProtocolRegistry`, all configuration and capability records are immutable values; the only mutable holders in the module are `diagnostics/SnapshotBuilder` (a builder, single-threaded by contract) and `core/src/test/.../testing/*` doubles.
- **What is not here yet:** zero `kotlinx.coroutines` library symbols appear in main sources — no `Flow`, `StateFlow`, `SharedFlow`, `CoroutineScope`, `Dispatchers`, `withTimeout`, no supervisor strategy — and `:core` no longer declares the library at all. ADR-P1-021 removed `kotlinx-coroutines-core` because an unused dependency is a claim about what the code does, and this one claimed a `Flow` surface that does not exist; the coroutines artifact returns with the phase that introduces the first real flow. Suspending contracts survive the deletion untouched, because `suspend` is a language feature: `21` suspend operations still compile against the stdlib alone, and `kotlinx-coroutines-test` stays test-only for `runTest`. So prompt §29's `Flow`/`StateFlow` requirement is satisfied at the *contract* level (the shapes that will carry them are already correct: suspend results, immutable state values, explicit owners) and not at the runtime level. `specs.md` §5 (Phase 0) remains the binding rule when the surfaces arrive.

---

## 15. Dependency injection

ADR-P1-009, prompt §28, REQ-P1-016-adjacent. Dependencies are constructor parameters; no DI library is added; `:core` exposes contracts only and therefore needs no container; the composition root arrives with the first Android code in Phase 2, inside `:platform:android`. No mutable singleton exists — `ProtocolRegistry.empty()` is an immutable value and `CoreFeature`/`CodecRegistry` are stateless catalogues of constants. Swapping a transport or protocol for a double is ordinary code: `FakeDeviceRepository`, `FakeCapabilityRepository` and `FakeProtocolRecordAccess` implement the interfaces and are invisible outside `core/src/test`, which is exactly prompt §28's testability requirement met with zero dependencies. If a container is adopted later it is its own ADR and nothing in `:core` changes.

---

## 16. Serialization model

Deferred deliberately (ADR-P1-010, prompt §38): no serializer, no annotations, no JSON/protobuf, no storage engine in Phase 1. The forward constraints are recorded now so the Phase 17/22 choice is not arbitrary — stable field names, an explicit version field, unknown-safe decoding, **no Android object ever serialised**, and the user device database kept separate from the protocol database. Two pieces of the design already carry their share of the load: `FeatureId.qualifiedName` is the documented stable key that protocol records will be filed under (ADR-P1-008 — renaming one is a breaking change needing an ADR), and `DeviceFingerprint.identityKey()` is versioned as `omnibuds-fingerprint/v1`, so the key format can evolve without invalidating old rows.

---

## 17. Testing architecture

```text
core/src/test/kotlin/com/omnibuds/core/       41 files / 5,885 lines / 302 tests / 37 classes
├── architecture/  DependencyDirectionTest ................. 11
├── audio/         5 classes ................................ 31
├── capability/    4 classes ................................ 56
├── common/        3 classes ................................ 19   (added at a5f3bf2)
├── config/        2 classes ................................ 19
├── device/        4 classes ................................ 37
├── diagnostics/   4 classes ................................ 22
├── persistence/   DeviceRepositoryContractTest ............   7
├── protocol/      8 classes ................................ 60
├── session/       DeviceStateTest .........................  12   (added at a5f3bf2)
├── testing/       1 class + 4 helper files .................  8
└── transport/     3 classes ................................ 19
```

Single source set `core/src/test`, JUnit 5 via `useJUnitPlatform()`, `kotlin-test` assertions, `kotlinx-coroutines-test.runTest` where a `suspend` contract is exercised, `kotlin-stdlib` only in main. `allWarningsAsErrors = true` in both modules is the static-analysis half of the quality baseline; no detekt, ktlint, Spotless, MockK or Android lint gate is wired in (ADR-P1-011, REQ-P1-021) because the rules that matter in Phase 1 are enforced by `DependencyDirectionTest` on every build at zero dependency cost — the dependency cost argument now literally holds, since `:core` ships no production dependency (ADR-P1-021). A root `.editorconfig` records the same conventions for editors but is read by nothing in the build. Test logging reports `failed` and `skipped` with full exception format, so a silently skipped test is visible.

Doubles follow ADR-P1-013: each defaults to empty rather than helpful, `ScriptedOutcome.next()` **throws** when nothing was scripted instead of inventing a happy path, a scripted failure is returned as failure and never swallowed, and each carries a `DOUBLE_MARKER` string inside its refusal messages so a double's output is identifiable in any log. `TestDoublesAreNotHardwareTest` asserts from the other side that the persistence seams are interfaces and that the only implementors in the tree are the named fakes (REQ-P1-009, REQ-P1-016).

Gap, named rather than glossed: ten of the eleven main areas now have a test class of their own — `session` gained `DeviceStateTest` and `common` gained `FeatureIdTest`, `OmniBudsErrorCategoryTest` and `OperationOutcomeTest` at `a5f3bf2`, which closed the gap this section used to record — but `state` still has none. `CapabilityState`, `ConnectionState`, `VerificationLevel.atLeast` and `SessionClassification` are covered only through the capability, device, session and config suites, and the contracts Phase 1 refuses to implement (`EarbudProtocol`, `TransportContract`, the five optional reporting interfaces, `ProtocolParser`, `ProtocolEncoder`, `CapabilityDefinition`) are named in no test at all, so their shape is read from source. See section 6 and `architecture-review.md` Question 8.

---

## 18. Diagnostics and logging

Models only (prompt §41/§42, REQ-P1-019). `diagnostics/OmniBudsLogger` is a two-method seam (`isEnabled(severity)`, `log(event)`); `DiagnosticSeverity` is `TRACE, DEBUG, INFO, WARN, ERROR, PACKET` with `TRACE` and `PACKET` flagged `requiresOptIn = true` so a sink cannot enable the chatty levels by accident; `DiagnosticCategory` covers the ten areas; `DiagnosticEvent` is a structured value (timestamp, severity, category, message, optional operation id, optional `OmniBudsError`) whose `init` refuses a blank message — "blank is not a redacted value"; `DiagnosticSnapshot`/`SnapshotBuilder` assemble a read-only report, with capability states sorted by `qualifiedName` so a snapshot is reproducible, and an unrecorded feature reads back `UNKNOWN` rather than absent.

Absent on purpose (ADR-P1-019): no redactor. `DiagnosticEvent.message` is documented as pre-redaction text because honest masking depends on which sink is receiving and which fields carry identifiers — neither of which a data class can know. Redaction is the obligation of the Phase 36 sink, enforced meanwhile by the `requiresOptIn` flags and by `ApplicationConfiguration`'s refusal to enter a diagnostic mode that has not declared an opt-in. No sink is implemented, so nothing can log; `diagnostics` owns no truth and is never a state source (ARCH-BOUND-001).

---

## 19. Deliberately absent, and why

| Absent | Reason |
|---|---|
| Any Bluetooth implementation, `:bluetooth` module | prompt §2, §51; the concepts are referenced by `TransportKind`/`TransportContract` only (REQ-P1-009) |
| Android sources, permissions, notifications, Quick Settings, UI | prompt §52; ADR-P1-001; the manifest is empty by design |
| Vendor protocols, `:protocols/<vendor>`, any non-empty `ProtocolRegistry` | nothing has been discovered; ADR-P1-013 |
| Production implementations of any contract, and stubs (`TODO()`, `NotImplementedError`, `error(`) in main | a Phase 1 implementation would necessarily be a fake that claims to work (prompt §53), and a throwing stub reads as an implemented path |
| Persistence engine, schema, serialization library | ADR-P1-010; the choice needs real usage (Phase 17/22) |
| DI framework | ADR-P1-009 |
| Flow/StateFlow surfaces, scopes, dispatchers | section 14; the owner that publishes them is Phase 2+ |
| `kotlinx-coroutines-core` on `:core`'s classpath | ADR-P1-021: no main source used it, and a declared dependency is a claim about what the code does. It returns with the phase that introduces the first `Flow` surface |
| A second holder of connection state (`DeviceSession.connectionState`, `lastStateUpdateEpochMillis`, `transitionedTo`) | ADR-P1-020: they existed earlier in this phase and were deleted, because two authoritative values for one fact is exactly the disagreement prompt §24 forbids (section 6) |
| Detekt/ktlint/Spotless/MockK/Android-lint gate | ADR-P1-011; compiler + dependency-free architecture tests carry the baseline. `.editorconfig` records the conventions for editors, and the build reads nothing from it |
| `java.time` / `Locale` / any JVM-only API in main | ADR-P1-012; two import-scan tests catch an `java.*`/`javax.*` **import**, which a fully qualified inline call can step around — the honest limit is stated in `kmp-review.md` §7 |
| ANC/EQ/gesture value shapes, hardware-test suite, packet logging, log redaction, per-endpoint codec support | ADR-P1-007, ADR-P1-019, ADR-P1-018; deferred with an owner |
| CI | none exists; see `repository-analysis.md` |

---

## 20. Requirement cross-reference

| Requirement (id — short title) | Where satisfied in this design |
|---|---|
| REQ-P1-001 compiles · REQ-P1-012 reproducible build | §1 (`repository-analysis.md` for the toolchain story) |
| REQ-P1-002 core platform-independent · REQ-P1-011 enforceable dependency direction | §1 (zero production dependencies, ADR-P1-021), §3, §5 |
| REQ-P1-003 Android isolated · REQ-P1-004 KMP preserved | §4, §5 |
| REQ-P1-005 identity unknown values · REQ-P1-006 capability six states · REQ-P1-007 codec state | §7, §8, §9 |
| REQ-P1-008 session active vs saved · REQ-P1-017 authoritative state | §6 (ownership split as corrected by ADR-P1-020), §8 |
| REQ-P1-009 no faked hardware · REQ-P1-015 no prohibited implementation · REQ-P1-016 doubles cannot reach production · REQ-P1-010 domain tests | §10, §17, §19 (with the remaining `state`-area and unimplemented-contract gaps named in §17) |
| REQ-P1-013 structured errors · REQ-P1-014 generic feature identity | §7, §13 |
| REQ-P1-018 persistence contracts only · REQ-P1-019 diagnostics models · REQ-P1-020 configuration split | §11, §12, §16, §18 |
| REQ-P1-021 build-enforced quality baseline · REQ-P1-022 phase documented | §17, `docs/phases/phase-1/` (this file plus `requirements.md`, `specs.md`, `task-list.md`, `test-plan.md`, `risk-register.md`, `decisions.md` and the agent reviews `repository-analysis.md`, `architecture-review.md`, `domain-model-review.md`, `code-quality-review.md`, `kmp-review.md`, `testing-review.md`; `validation.md` is still outstanding, and these documents plus the root `README.md` are uncommitted at HEAD `a5f3bf2` — REQ-P1-022 records it) |
