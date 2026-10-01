# Phase 1 — Specs (project-wide implementation specifications, as realised)

**Phase:** 1 — Project Foundation & Kotlin Architecture · **Scope id:** `P1` · **Owner agent:** Phase 1 documentation workstream
**Document status:** reviewed against the repository at `main` / HEAD `a5f3bf2`.
**Scope.** These are the conventions the 71 main files / 5,196 lines of `:core` actually follow, written from the code rather than from the intent, plus the forward constraints Phase 1 deliberately does not implement. Section numbering mirrors Phase 0's `specs.md`, because Phase 1 source KDoc cites it by section (`specs.md section 2.2`, `section 4 rule 3`, `section 5.7`) and every one of those references must resolve here as well as there.
**Authority.** `docs/MASTER-CONTEXT.md` → Phase 0 documents → `docs/phases/phase-1/decisions.md` (ADRs P1-001 … P1-021 are referenced by id, never restated) → this file. Deviations from Phase 0 are marked ⚠ and stated as amendments, not presented as the rule all along (§12 collects them).

## 0. What enforces what

| Enforced by the build | Enforced by review only |
|---|---|
| Kotlin compiles for JVM target 17, AGP 8.7.3, Gradle 8.9; `allWarningsAsErrors = true` in both modules | Import layout: no formatter, no ktlint, no detekt, no Spotless, no Android lint gate (ADR-P1-011) |
| No Android, no `java.*`/`javax.*` import, no Bluetooth/media type in `:core` main source | Whether an import list is minimal or ordered — the compiler does not flag redundant same-package imports |
| Layer direction between core areas; every area registered; package matches directory | Style, ordering, line length, comment voice |
| No stub (`TODO(`, `NotImplementedError`, `error("`) in main source | Whether a KDoc claim about hardware is honest |
| No magic hex opcode or UUID literal in main code lines | Whether a rule in this file has been followed |
| No class named `Fake…`, `Mock…`, `Stub…`, `Dummy…` or `Test…` in main; `:platform:android` still source-free | Whether a differently-named main-source double exists |

Consequence of `allWarningsAsErrors = true`: an unused import, an unused parameter or a deprecation warning is a **build failure**, not a style nit. Delete the offending code; do not annotate it away. The 301-test suite passing means the tree currently compiles with zero warnings. A root `.editorconfig` now exists and records these conventions (charset, LF endings, final newline, 4-space Kotlin indent, 120-column limit, 2-space TOML/YAML, and the Markdown exceptions for wrapped prose and tables), which makes ADR-P1-011's "`.editorconfig`-style convention set" literal rather than aspirational — but nothing in the build reads that file, so the right-hand column above stays review-only.

## 1. Naming conventions (inherits Phase 0 §1)

### 1.1 Kotlin identifiers

| Kind | Rule as realised | Examples from `:core` |
|---|---|---|
| Type | `UpperCamelCase` noun; no `Impl` suffix unless an interface actually exists; no `Manager`/`Helper`/`Util` | `DeviceSession`, `CodecCapability`, `SnapshotBuilder` |
| Interface | Bare capability noun — no `I` prefix, no `able` gimmick | `EarbudProtocol`, `TransportContract`, `OmniBudsLogger`, `DeviceRepository` |
| Optional capability interface | `<Thing>Support` — a type a protocol may or may not implement | `FeatureReadSupport`, `FeatureWriteSupport`, `BatteryReportingSupport`, `FirmwareReportingSupport`, `AudioStateReportingSupport` |
| Contract seam | `<Subject>Contract` / `<Subject>Repository` / `<Subject>Access` | `TransportContract`, `DeviceRepository`, `ProtocolRecordAccess` |
| Record | Noun; past-participle when it is an observation, not a decision | `SavedDeviceRecord`, `DiscoveredCapabilityRecord`, `ParsedResponse` |
| Function | `verb`; `suspend` wherever a later implementation may await I/O | `identify()`, `discoverCapabilities()`, `readState()`, `exchange()`, `markSeen()` |
| Snapshot-producing function | `stateOf` / `load` / `find` / `candidatesFor` — a query never mutates | `DeviceCapabilities.stateOf`, `ProtocolRegistry.find` |
| Copy-on-write mutator | `with…` / `mergedWith` / `applyIfNewer` — returns a new value, never mutates. A mutator that can legitimately be refused returns the outcome instead: `attemptConnection` answers `OperationOutcome<DeviceState>` and leaves the receiver untouched on refusal | `withCapability`, `withBattery`, `withEvidence`, `mergedWith`, `save()`, `forget()` |
| Factory | `of`, `empty`, `unknown`, `unobserved`, `defaults`, `unmatched`, `initial`, `available`/`unavailable` | `DeviceIdentity.unknown()`, `AudioTransportState.unobserved()`, `ProtocolRegistry.empty()`, `TransportAvailability.available(kind)` |
| ⚠ Boolean | **Stored** booleans in a data-class constructor are bare adjectives — `readable`, `writable`, `available`, `configurable`, `acknowledged`, `enabled`, `requiresConnection`, `grantsCapabilitySupport`, `acceptsNotification`. `is`/`has`/`can`/`permits` prefixes are reserved for **derived** properties computed from state: `isControllable`, `isActive`, `isOperational`, `isRefused`, `hasPayload`, `isEntirelyUnknown`, `permitsAutomaticRetry`, `expectsFields` (34 derived boolean properties in main source: 24 `is…`, 5 `has…`, 5 `can`/`permits`/`expects`) | `FeatureCapability(readable = true, writable = false, …)`; `val isControllable get() = state.isControllable()` |

Reason for the ⚠ amendment: an `is`-prefixed constructor parameter reads as a conclusion the type computed, when in Phase 1 it is an *input the caller asserts* and the `init` block then validates against `state`. `Phase 0 §1.1`'s `isReadable` example survives as the derived-property rule; the affordances themselves are stored bare. A boolean is still never used where a state enum is required — `configurable` is the only permitted exception and ADR-P1-005 states why it is orthogonal to the ladder.

### 1.2 Modules and packages

| Item | Rule |
|---|---|
| ⚠ Package root (ADR-P1-002, amends Phase 0 §1.2) | `com.omnibuds.core.<area>` for `:core`; `com.omnibuds.android.<area>` reserved for `:platform:android`. Phase 0's bare `omnibuds` root is superseded; its other naming rules stand. Area names replace Phase 0's illustrative list — its `domain` idea is realised as the two layer-0/1 areas `common` and `state`, and the eleven areas below are the set |
| Module names | `:core` (Kotlin/JVM library) and `:platform:android` (Android library). Directory `platform/android`, namespace `com.omnibuds.android`. New modules are created by the phase that has real code for them (ADR-P1-001). |
| Areas and their sizes | `common` 6 files/238 lines · `state` 4/207 · `transport` 4/331 · `device` 6/581 · `capability` 7/830 · `audio` 9/499 · `config` 7/372 · `diagnostics` 6/371 · `session` 1/158 · `persistence` 5/389 · `protocol` 16/1,212 — 71 files / 5,196 lines total |
| File rule | One primary top-level declaration per file, file name equal to that declaration (67 of 71 files). The four exceptions add only declarations *of the same subject*: extension functions over the enum in the file — `state/CapabilityState.kt` (`isControllable`, `isEstablished`, `isReadable`), `state/VerificationLevel.kt` (`atLeast`), `common/OperationOutcome.kt` (`getOrNull()`) — or the subject's own transition table, `state/ConnectionState.kt` + `ConnectionStateTransitions`. Nothing unrelated shares a file |
| Package statement | Must match the directory — checked by `DependencyDirectionTest.packageStatementsMatchSourceDirectories` |
| Forbidden packages | no `utils`, `misc`, `common2`, `new`; no vendor name in any package (`capability/VendorExtension.kt` is generic; vendor packages arrive with `:protocols:<vendor>`) |
| Imports | explicit, fully qualified, no wildcards (0 in the tree); same-package types are not imported — the two exceptions in `diagnostics/OmniBudsLogger.kt` are harmless but should be pruned at next touch |

### 1.3 Enums and states

| Rule | Realised as |
|---|---|
| Type name is a state noun; members `UPPER_SNAKE_CASE` | `CapabilityState.PERSISTENCE_VERIFIED`, `CodecState.NEGOTIATED` |
| Every state enum that can honestly be unread carries an explicit unknown member | `UNKNOWN` in `CapabilityState`, `CodecState`, `ConnectionState`, `TransportKind`, `AudioTransportKind`, `CodecFamily`, `ChannelMode`, `QualityMode`, `Codec`; `GENERAL` is `DiagnosticCategory`'s fallback; `VENDOR` is `FeatureCategory`'s |
| Declaration order is the ladder and is load-bearing | `CapabilityState`, `CodecState`, `VerificationLevel` — documented in each KDoc; `atLeast()` and `supportsAtLeast()` compare ordinals; `DeviceCapabilities.evidenceOf` restates the ladder as numbers so a silent reorder becomes a compile error |
| Attributes an enum owns are declared per member, not decided at call sites | `OmniBudsErrorCategory(retryClass, invalidatesSession)`, `DiagnosticSeverity(requiresOptIn)`, `DiagnosticMode(requiresOptIn)`, `FeatureFlagKind(grantsCapabilitySupport)`, `Codec(displayName, family)`; derived facts are properties (`EffectClass.permitsAutomaticRetry`) |
| Machine identity and display label stay separate | `Codec.name` is identity, `Codec.displayName` is a label; `ConfigurationValue.ModeValue(technicalName, displayName)`; `CapabilityDefinition.displayName` is never a lookup key |
| Closed vocabularies are pinned by a test where the vocabulary is the contract | `FeatureFlagTest.declaredKindsAreExactlyThePermittedSet`, `CoreFeatureTest.theCategoryVocabularyIsTheContractedNine`, `DiagnosticSeverityTest.theSeverityVocabularyIsExactlyTheSixDocumentedLevels`. ⚠ `OmniBudsErrorCategory` is now pinned the same way by `common/OmniBudsErrorCategoryTest.theCanonicalCategoriesAreAllPresent` (all sixteen names), `.categoriesThatLeaveTheDeviceStateUncertainSaySo` (`invalidatesSession` for all sixteen) and `.everyCategoryDeclaresItsRetryClassAndNoneIsUnspecified and .onlyIdempotentReadsMayBeRetriedWithoutCheckingStateFirst` plus `.aTimedOutWriteIsResolvedByReReadingRatherThanByResending` (`retryClass` for eleven of sixteen); the remaining five `RETRY_AFTER_REREAD` members are still read from the enum, not asserted (REQ-P1-013) |
| A nested enum is allowed when it means only in context | `ProtocolIdentification.MatchEvidence` |
| Adding a member that affects capability or codec semantics needs an ADR | Phase 0 §1.3, unchanged; `Codec` entries are the documented registry-data change (ADR-P1-005, `CodecRegistry` KDoc) |

### 1.4 Tests (inherits Phase 0 §1.4)

Class `<Subject>Test`, `<Subject>ContractTest` for a seam (`DeviceRepositoryContractTest`), `<Subject>IntegrationTest`/`<Subject>HardwareTest` later; architecture checks live in `architecture/` and doubles in `testing/`. Method names are behaviour-plus-condition lowerCamel sentences with no backticks (`.aMergeNeverDowngradesObservedPersistenceToSessionOnlySupport`). ⚠ **No `TEST-P1-<NNN>` id appears in any method name**: Phase 1 test ids are assigned in `test-plan.md`, and `TestDoublesAreNotHardwareTest`'s KDoc records that decision rather than inventing numbers here. Tier tags (`Tier T1`, testing-governance TST-TIER-001) appear in 14 of 37 test classes; the other 23 — including the four classes added with `a5f3bf2` (`FeatureIdTest`, `OmniBudsErrorCategoryTest`, `OperationOutcomeTest`, `DeviceStateTest`) and the rewritten `DeviceSessionTest` — are T1 by environment but untagged, a convention gap, not a rule change.

### 1.5 Protocol and capability naming

- ⚠ **Feature identity casing (ADR-P1-008, amends Phase 0 §1.5 examples).** `FeatureId` is validated lower-kebab, dot-separated: `noise-control.anc`, `power.case-battery`, `vendor.sony.adaptive-sound-control`. Phase 0's camelCase examples (`noiseControl.anc`, `input.gesture.doubleTapLeft`) are superseded; the underlying rules — one registry, stable strings, display label never becomes identity — stand. `CoreFeature` is that registry for the fifteen universal identities.
- ⚠ `FeatureId.qualifiedName` is a stable contract: renaming one is a breaking change requiring an ADR, because Phase 22 keys protocol records on it.
- Command and field names describe meaning, never bytes: `readBatteryStatus` is an id, `send0x0A` is a defect. `TransportRequest.commandId` is a symbolic name (`example-vendor.read-battery-status`), and `ProtocolEncoder`/`ProtocolParser` are addressed by it.
- Vendor features are addressed as vendor extensions only; `VendorExtension` refuses a core-shaped identity and a metadata/vendor mismatch (ADR-P1-008).
- **Resolved at `a5f3bf2`.** `capability/VendorExtension.kt` no longer documents a live `FeatureId` defect and no longer keeps the dead `||` branch that worked around one: `VendorExtension.isVendorFeature(feature)` is a one-line delegation to `FeatureId.isVendorExtension`, and the file's KDoc now explains the check that genuinely belongs in `init` — the vendor *segment* must equal `VendorFeatureMetadata.vendor`, which no single type can decide alone. In the same commit `FeatureId.parseOrNull` was tightened to require the same minimum two segments as `of`, so a stored one-segment name decodes to null instead of becoming an identity; `common/FeatureIdTest` pins both rules.

## 2. State conventions (inherits Phase 0 §2)

### 2.1 The rule

State is modelled explicitly, `null` is not a general-purpose state substitute, and the honest default is the *unknown* member, never a fabricated positive (master §53).

### 2.2 Three representation tiers, as realised

| Tier | Used in Phase 1 for | Absence appears as | Concrete evidence |
|---|---|---|---|
| Enum state | Capability, codec, connection, verification, transport, category, severity, flag kind | `UNKNOWN` member | `stateOf()` returns `UNKNOWN`, not `UNSUPPORTED`, on a lookup miss |
| Nullable scalar | Battery levels and charging flags, sample rate, bit depth, bitrate, protocol version, timestamps, `detail`, `documentation`, `payload` | `null`, and blank text normalised to `null` | `DeviceIdentity.normalised()`; `BatteryState` rejects out-of-range but accepts `0` |
| Typed failure | Any operation that could not complete | `OperationOutcome.Failure(OmniBudsError)` | `DeviceState.attemptConnection` returns `Failure(INVALID_STATE)` rather than throwing, and leaves the receiver untouched |

A real zero stays zero: `BatteryState(leftLevel = 0)` is a flat battery, `IntValue(0)` is a reported value, and `ParsedResponse` distinguishes "reported 0" from "did not report" (`ParsedResponseTest.aReportedZeroAndAReportedFalseAreRealReadingsAndArePreserved`).

### 2.3 Mandatory state vocabularies as realised

```text
CapabilityState      UNKNOWN | UNSUPPORTED | READ_ONLY | SUPPORTED_VOLATILE
                     | SUPPORTED_PERSISTENT | PERSISTENCE_VERIFIED        (unchanged)
VerificationLevel    INFERRED | IMPLEMENTED | LAB_TESTED | HARDWARE_VERIFIED
                     | PERSISTENCE_VERIFIED                               (unchanged)
ConnectionState      UNKNOWN | DISCOVERED | PAIRED | CONNECTED | IDENTIFYING
                     | CAPABILITY_DISCOVERY | READY | CONTROL_SESSION | DISCONNECTED
                     | TEMPORARILY_UNAVAILABLE | ERROR                    (11 values)
SessionClassification TEMPORARY | SAVED
CodecState           UNKNOWN | UNSUPPORTED | SUPPORTED | AVAILABLE | ENABLED
                     | NEGOTIATED | ACTIVE   + configurable: Boolean attribute
```

- ⚠ **ADR-P1-016 supersedes Phase 0 §2.3's `SessionState`.** The six mixed values are replaced by the two orthogonal types above; `CONNECTING`, `ACTIVE_SESSION`, `STALE` and `FORGETTABLE` are combinations, and staleness is derived (`DeviceState.revision`, `ConnectionStateTransitions`), never stored twice. **ADR-P1-020 applies the same rule inside Phase 1's own types:** `ConnectionState` is held only in `DeviceState.connection` and `SessionClassification` only in `DeviceSession.classification`; `device/DeviceSession.kt` declares no connection property, so no caller can read two different connection values for one device.
- ⚠ **ADR-P1-005 amends ADR-P0-015's `CodecState`.** Five positive rungs became seven ordinals (adding `UNKNOWN`, `UNSUPPORTED`), `CONFIGURABLE` left the enum and became `CodecCapability.configurable`, and the prompt's "selected" is `ENABLED`.
- ⚠ `OmniBudsErrorCategory` is the sixteen-member union of Phase 0's thirteen plus `READ_FAILED`, `UNKNOWN_DEVICE`, `INVALID_STATE`, with `GATT_FAILURE`, `RFCOMM_FAILURE`, `FIRMWARE_MISMATCH` retained (ADR-P1-006).

### 2.4 Time and ordering

Timestamps are `Long?` epoch milliseconds; `null` means "not observed" and is never filled with `0` (ADR-P1-012). No core type reads a clock — `atEpochMillis` / `updatedAtEpochMillis` / `capturedAtEpochMillis` are always caller-supplied. Ordering within a process is expressed by `DeviceState.revision` (`INITIAL_REVISION = 0`, `+1` per mutator, `applyIfNewer` discards older), never by wall-clock comparison. A transition that carries no timestamp leaves the update time unknown instead of inheriting the previous stamp.

### 2.5 Facts that are derived, never stored

`isControllable`, `isOperational`, `isActive`, `isUsable`, `supportsAtLeast`, `isRefused`, `isIdentified`, `isDefinitive`, `isVendorExtension`, `hasPayload`, `isAsymmetric`, `isFullyObserved`, `knownFieldCount`, `permitsAutomaticRetry`, `canBeExposedAsControl` all compute from state or evidence. Nothing that can be derived from the ladder is also stored beside it, because two representations drift.

## 3. Error conventions (inherits Phase 0 §3)

`OperationOutcome<out T>` = `Success(value)` | `Failure(error)` | `Cancelled` — three cases; timeout, unsupported, disconnected, mismatch and codec-unavailable are categories inside `Failure`, and unknown is a property of a value rather than an outcome (ADR-P1-004). Helpers: `map`, `valueOrNull`, `errorOrNull`, `isSuccess`, `getOrNull()`.

`OmniBudsError(category, operationId, detail?, transport = TransportKind.UNKNOWN, attempts = 1)` with `attempts >= 1` and `invalidatesSession` delegated to the category.

| Category | retryClass | invalidatesSession | Category | retryClass | invalidatesSession |
|---|---|---|---|---|---|
| `BLUETOOTH_DISABLED` | RETRY_AFTER_REREAD | false | `WRITE_REJECTED` | NEVER_RETRY | true |
| `PERMISSION_DENIED` | NEVER_RETRY | false | `VERIFICATION_FAILED` | NEVER_RETRY | true |
| `DEVICE_DISCONNECTED` | RETRY_AFTER_REREAD | true | `TIMEOUT` | RETRY_AFTER_REREAD | true |
| `TRANSPORT_UNAVAILABLE` | RETRY_AFTER_REREAD | false | `FIRMWARE_MISMATCH` | NEVER_RETRY | false |
| `GATT_FAILURE` | RETRY_AFTER_REREAD | true | `CODEC_UNAVAILABLE` | NEVER_RETRY | false |
| `RFCOMM_FAILURE` | RETRY_AFTER_REREAD | true | `UNKNOWN_DEVICE` | NEVER_RETRY | false |
| `PROTOCOL_MISMATCH` | NEVER_RETRY | true | `INVALID_STATE` | NEVER_RETRY | false |
| `UNSUPPORTED_FEATURE` | NEVER_RETRY | false | `READ_FAILED` | SAFE_TO_RETRY | false |

Rules realised:

1. Two refusal channels, deliberately different. `init { require(…) }`/`check(…)` reject an **unconstructible claim** (`TransportAvailability` with a reason but available, `FeatureFlag` of a support-granting kind, `CodecCapability` at a contradictory rung) — that is a programmer error the program cannot recover from. `OperationOutcome.Failure` reports an **operation that did not work** — a device result the caller must branch on. A state machine that throws takes the caller's error model away from it.
2. No error is converted into a fabricated value: a failed or absent read leaves the field null/`UNKNOWN` (`ADR-P0-016`).
3. `UnsupportedFeature` may only follow an established `UNSUPPORTED`; a lookup miss yields `UNKNOWN`, so no code path produces a refusal from a missing record.
4. An unavailable transport produces `TRANSPORT_UNAVAILABLE` for *that* kind plus a recorded `TransportAvailability` entry — never a silent fallback to another channel.
5. `detail` and `OmniBudsError` carry no Bluetooth address, device name or manufacturer data; redaction is owed at emission (SEC-LOG-002), and Phase 1 ships no redactor (ADR-P1-019).
6. `invalidatesSession` categories force re-discovery before the next write.

## 4. Retry conventions (inherits Phase 0 §4; the asymmetry is structural)

Blind automatic retry of a side-effecting command is prohibited, and Phase 1 makes it unrepresentable rather than discouraged:

| Where | The rule as built |
|---|---|
| `EffectClass` | `READ` is the only class with `permitsAutomaticRetry = true`; `SIDE_EFFECTING_WRITE` and `IRREVERSIBLE_WRITE` are both false |
| `CommandDefinition` | `maxReadAttempts` **required** and in 1..5 for `READ`; **must be null** for either write class — a definition stating a write budget cannot be constructed |
| `CapabilityMapping` | a `writeCommandId` requires an `effectClass` that is not `READ`; an effect class describing a change requires a write command |
| `ProtocolConfiguration` | `maxReadAttempts` in 1..5 and reads-only; there is no `writeAttempts`, no `retryWrites`, no reusable backoff field |
| `TransportContract.exchange` | never retries; retry is a decision about effects and lives on the command definition |
| `OmniBudsErrorCategory.retryClass` | the recovery path for a timed-out write is `RETRY_AFTER_REREAD` — re-read, then decide; a `Timeout` implies unknown state, never failure or success |

Timeout bounds are per operation: `CommandDefinition.timeoutMillis` and `TransportRequest.timeoutMillis` are `Long?` where `null` = "the definition states none" (not unlimited, not zero), and an implementation must wait no longer than the smaller of the request's and the caller's bound.

## 5. Coroutine, concurrency and Flow conventions (inherits Phase 0 §5, rules 1–9)

### 5.1 What Phase 1 actually contains

`suspend` is the whole async surface: 21 suspend operations across `TransportContract` (3), `EarbudProtocol` (3), the five reporting interfaces (5), `DeviceRepository` (5), `CapabilityRepository` (3), `ProtocolRecordAccess` (2). **No main source imports `kotlinx.coroutines.*`** — no `CoroutineScope`, no `Dispatchers`, no `GlobalScope`, no `launch`, no `Flow`, no `runBlocking`; `suspend` is a language feature, so the contracts compile against the stdlib alone. **ADR-P1-021 removed `kotlinx-coroutines-core` from `:core` and from the catalog**, because a declared dependency is a claim about what the code does and this one was used by nobody: `:core` now has zero production dependencies. `Flow` therefore arrives with the phase that needs it — the Phase 2 state engine — with a recorded reason, and the first `StateFlow` must come with a requirement rather than as a tidy-up. Test sources keep `kotlinx-coroutines-test` (`runTest` in exactly two classes, `DeviceRepositoryContractTest` and `TestDoublesAreNotHardwareTest`, both exercising suspend seams), and `testing/ScriptedOutcome.kt` uses `kotlinx.coroutines.yield`.

### 5.2 Constraints Phase 1 imposes on the phases that add concurrency

1. **Structured concurrency (0 §5.1).** Every long-lived scope is owned and named; nothing is launched from a domain object. `:core` types hold no scope — state arrives as values (`EarbudProtocol.readState(session)` returns a newer `DeviceState`), so there is nothing to leak.
2. **Dispatchers (0 §5.2).** Callback funneling and dispatcher choice belong to the transport implementation in `:platform:android`; no core signature exposes a dispatcher.
3. **Cancellation (0 §5.3).** `Cancelled` is a distinct outcome, never success; a cancelled operation still owes its handle cleanup. `ScriptedOutcome.next()` yields first so a cancelled test coroutine does not consume a step — the same contract a transport owes.
4. **Timeouts (0 §5.4).** Declared per operation, surfaced as `TIMEOUT`, never as a silent null (§4).
5. **Flow (0 §5.5).** None exists yet. When it does: latest-value device state as `StateFlow`, events as `SharedFlow`, no cold flow for device state, every flow declaring buffering and overflow. The snapshot types designed to be wrapped are already single-owner immutable values: `DeviceState`, `DeviceCapabilities`, `AudioTransportState`, `BatteryState`, `TransportAvailability`.
6. **Commands are functions, never emitted values (0 §5.6).** `FeatureWriteSupport.writeFeature` returns an outcome; no state flow has a write path into it.
7. **One owner per mutable area (0 §5.7).** The state engine owns `DeviceState`, the capability engine owns `DeviceCapabilities`; everyone else reads and returns copies. ADR-P1-020 makes the same rule true of the value itself: `DeviceState` is the only type holding connection state, reached through `attemptConnection`. `SnapshotBuilder` and the `testing/` doubles are explicitly one-owner-not-thread-safe; `ProtocolRegistry` and `DeviceCapabilities` are immutable values with no ambient singleton, and no `:core` object is a mutable global (ADR-P1-009).
8. **Lifecycle-bound work (0 §5.8).** Owed by Phase 2's composition root; `:core` has no lifecycle awareness of any kind.
9. **Serialised writes (0 §5.9).** Implementation-level: one control channel at a time, so response correlation stays possible. Phase 1 expresses it as a contract obligation on `TransportContract`, not as code.

## 6. Data-model conventions (as realised)

| Convention | Realisation |
|---|---|
| Immutable value records | `data class` for every observation, and no observable `var`: the only mutable state in main source is `SnapshotBuilder`'s private accumulator, plus locals inside the two hand-written `hashCode()` bodies |
| Closed unions | `sealed interface` + data class/object: `OperationOutcome`, `ConfigurationValue` (4 cases) — no `Any`, no open-ended map, so `when` stays exhaustive at compile time |
| Typed single strings | `@JvmInline value class FeatureId` with a private constructor and `of`/`ofVendor` factories |
| Single-value namespaces | `object CoreFeature`, `object CodecRegistry`, `object ConnectionStateTransitions`; `CoreFeature` asserts in `init` that its identity list, its definition catalogue and the reserved vendor root cannot drift apart |
| Invariants in `init` | `require`/`check` with a message naming the illegality *and* why it would have been dangerous |
| No fabricated defaults | No parameter whose presence would be a claim has a default: `FirmwareInfo.verification`, all eight fields of `AudioTransportState`, `CodecCapability`, `FeatureCapability`, `TransportAvailability`, `DiagnosticEvent` and `ProtocolDefinition` are mandatory constructor arguments. Optional fields default only to `null` or `emptyList()` |
| Blank text is not a value | `require(id.isNotBlank())` on every identity, label and citation; reported device text normalises blank to unknown via `DeviceIdentity.of` / `normalised` |
| Unknown expressed by absence | an omitted map key or an absent record, never a placeholder entry |
| Collections are copied | `entries.toMutableMap()` for defensive copies, deliberately not `toMap()`, which may return the same instance: `DeviceCapabilities`, `ProtocolDefinition` indexes, `ParsedResponse.fieldIndex`, `DeviceConfiguration.of` |
| Byte arrays need hand-written equality | `TransportRequest`, `TransportResponse`: `contentEquals`/`contentHashCode`, `toString()` printing byte counts and never bytes (SEC-LOG-004), no defensive copy so a sender cannot mutate after the fact |
| `toString()` is a disclosure surface | prints lengths and identities, not payloads or raw evidence |
| Records of observation | fingerprints, capabilities, codec records and vendor extensions state what was seen; they never negotiate, read, write or control |

## 7. Dependency rules

**Module direction.** `:platform:android` → `:core`; never the reverse; `:core` has no dependency on a database implementation, a UI toolkit or vendor protocol code (prompt §32), and after ADR-P1-021 it declares **no production dependency at all** — its `dependencies` block is test-only.

**Layer map (ADR-P1-003).** An import may point only at a **strictly lower** layer, or at the importing file's own area; a same-layer cross-area import fails, as does an upward one. The numbers below are the `areaLayer` map in `DependencyDirectionTest` verbatim.

```text
0 common      FeatureId, TransportKind, error + outcome vocabulary
1 state       CapabilityState, ConnectionState, VerificationLevel, SessionClassification
1 transport   TransportContract, request/response — platform-neutral contract
2 device · capability · audio · config · diagnostics
3 session · persistence
4 protocol    EarbudProtocol, protocol definitions, registry
```

**The rule as mechanically enforced** (`architecture/DependencyDirectionTest.kt`, 11 checks, standard library only): area = the path segment after `com/omnibuds/core/`; target area = the segment after `com.omnibuds.core.` in an import; a violation is `(areaLayer[target] ?: Int.MAX_VALUE) >= sourceLayer`. Additional scans: forbidden import prefixes `android.`, `androidx.`, `com.omnibuds.android`, `java.`, `javax.`; forbidden code tokens `Bluetooth*`, `AudioTrack`, `AudioManager`, `AudioRecord`, `MediaPlayer`; forbidden stubs `TODO(`, `NotImplementedError`, `error("`; forbidden test-double names; forbidden production implementations of `EarbudProtocol`, `DeviceRepository`, `CapabilityRepository`, `TransportContract`; magic literals matching `0x[0-9a-fA-F]{2,}` or any 8-4-4-4-12 UUID; package-versus-directory equality; `:platform:android` source emptiness. Every scan `fail()`s when its source root is missing or empty, so a moved directory cannot make a rule pass by finding nothing. Comment and KDoc lines are stripped, so documenting a forbidden thing is not a violation.

**What the scans cannot see — and therefore stays a review rule:** a fully qualified inline reference used without an import (`java.util.UUID…`) evades the import-prefix checks, which is why wildcard-free explicit imports and code review remain load-bearing; `internal` and same-package types are invisible to the area rule; nothing greps for "UI"; `TransportKind.GATT` and `RFCOMM` are legitimate vocabulary, not implementation.

## 8. Test conventions

- Layout mirrors ten of the eleven main areas — no `state/` test class exists, so `CapabilityState`, `ConnectionState`, `VerificationLevel` and `SessionClassification` are covered only through the capability, device, session and config suites — plus `architecture/` (11 checks) and `testing/` (3 `Fake*` doubles, `ScriptedOutcome`, and the double guard `TestDoublesAreNotHardwareTest`). Test classes per area: common 19 · session 12 · transport 19 · capability 56 · audio 31 · device 37 · protocol 60 · diagnostics 22 · config 19 · persistence 7 · testing 8 · architecture 11 = 301 in 37 classes across 41 test files.
- Assertions come from `kotlin.test` (`assertEquals` 400, `assertTrue` 215, `assertFalse` 182, `assertNull` 134, `assertFailsWith` 93, `assertNotEquals` 72, `assertSame` 5, `assertContentEquals` 3, occurrences counted across the test tree including the `import kotlin.test.*` lines) on the JUnit platform. No mocking framework, no AssertJ, no reflection-based helper.
- Outcomes are read through `isSuccess` / `valueOrNull` / `errorOrNull` rather than by casting, so "Success carrying null" stays distinguishable from "Failure" — the distinction SEC-UNK-001 turns on.
- Construction refusals are asserted as `assertFailsWith<IllegalArgumentException>`; class-local helpers stay private (`FeatureCapabilityTest.assertRejected`), shared doubles belong in `testing/`.
- Fixtures are fictional: `example-vendor.test-protocol`, `fictional audio works`, `FAW-BUDS-ONE`, invented service tokens, `deviceClass = 7936` as a decimal. No real vendor name and no real UUID anywhere. The only hex literals in the tree are inert payload bytes in `TransportRequestTest` and `TransportResponseTest` (`0xAA`, `0x55`, `0x7F`) — no protocol fact is expressed as a literal, and the magic-literal scan itself applies to main source only.
- Doubles default to empty or unscripted and refuse to invent success; a scripted failure or cancellation is returned as itself.
- Phase 1 hardware ceiling: nothing here is above `IMPLEMENTED` in `VerificationLevel`, and no test result may be cited as `HARDWARE_VERIFIED` (testing-governance TST-MOCK-001).

## 9. Serialization rules — deferred by ADR-P1-010, with the constraints recorded now

**Phase 1 chooses no format, ships no annotations and adds no serialization dependency.** `ConfigurationValue`'s KDoc states that a wrong annotation on a persisted value is a migration problem rather than a typing problem. The forward constraints bind whoever chooses (Phase 17 or 22):

1. **Stable field names.** Serialized keys are the constructor parameter names; renaming a persisted field is a breaking change requiring an ADR, exactly as renaming a `FeatureId` is.
2. **Explicit version field.** Every persisted record carries its own version. The precedent already in code is `DeviceFingerprint`'s key prefix `omnibuds-fingerprint/v1`; a record without a version marker cannot be migrated.
3. **Unknown-safe decoding.** A field the reader does not recognise is dropped, not fatal; a field the writer never produced decodes as unknown (`null` or the type's `UNKNOWN` member), never as `0`, `false`, `""` or `UNSUPPORTED`. Where a vocabulary has no unknown member — `SessionClassification`, `VerificationLevel` — the record or field must be absent rather than decoded into a plausible value.
4. **No Android objects, ever.** Only `:core` value types are serializable; no `Context`, no framework type, no `ByteArray` payload archived in clear (`TransportRequest`/`TransportResponse` keep their bytes out of `toString()` for the same reason).
5. **Two databases stay separate.** The user device database and the global protocol database have separate seams (`DeviceRepository`/`CapabilityRepository` versus `ProtocolRecordAccess`), the protocol seam is read-only and id-returning, and no user identifier may enter the protocol database (master §28, SEC-PRIV-004/005).
6. **Identifier minimisation.** `SavedDeviceRecord` holds the fingerprint-derived `identityKey` and no MAC address, no raw manufacturer data, no `DeviceFingerprint` archive (SEC-ID-004/006/007).
7. **Enum `name` is the serializable identity**; `displayName` is presentation and must never be persisted as identity.
8. **Timestamps serialize as `Long?` epoch millis**; `null` round-trips as `null`.
9. **Persistence ladder.** Storage may raise nothing: a stored `PERSISTENCE_VERIFIED` is a hint until re-verified, and a cache key cannot distinguish firmwares (`DiscoveredCapabilityRecord` KDoc) — so the version a claim was established against must be recorded when per-firmware caching is wanted, not smuggled into `identityKey`.

## 10. API visibility rules

| Visibility | Use | Instances |
|---|---|---|
| `public` (default) | Every domain contract and record — `:core` types must be implementable and constructible from other modules | all 71 main files |
| `internal` | Only helpers that must be shared across areas without becoming API | `DeviceIdentity.normalised`, `VendorExtension.vendorSegmentOf` (2 uses) |
| `private constructor` | A type whose validity is enforced by a factory | `FeatureId` |
| `private` | Backing indexes and copied state that make a value tamper-proof | `DeviceCapabilities.entries`, `ProtocolDefinition.commandIndex/responseIndex/mappingIndex`, `ParsedResponse.fieldIndex`, `ProtocolRegistry.byId`, `CodecRegistry.lookup` |
| `protected` | Not used; no inheritance hierarchies exist — composition and interfaces only | — |
| Test-only seams | Achieved by source-set separation (`core/src/test`), never by widening production visibility | doubles in `testing/`; the private `RecordingLogger` in `OmniBudsLoggerTest` |

There is no `@VisibleForTesting`, no DI annotation and no ambient singleton (ADR-P1-009): dependencies are constructor parameters, `ProtocolRegistry.empty()` is an immutable value, and `FirmwareInfo.verification` has no default precisely because a type cannot honestly guess a caller's evidence tier. Adding a member to a released interface (`EarbudProtocol`, `TransportContract`, the three persistence seams) is a breaking change requiring an ADR — those interfaces are the contract Phase 2 and Phase 6 implement.

## 11. Documentation requirements

1. Every type carries KDoc stating what it is, what it does **not** claim, and the rule id, ADR id or prompt section it protects; every `require` message states the illegality and its danger.
2. Every nullable field's KDoc says what `null` means ("not reported", never "none" and never false).
3. Every derived boolean documents the rung or evidence it is computed from, and every ladder documents its order and its unknown rungs.
4. Hardware-truth ceiling: no document, comment or test may claim a state above the evidence tier reached — Phase 1 closes at `IMPLEMENTED`, and nothing here has been read from a device.
5. Prose may name a forbidden thing (`RFCOMM` writes, `BluetoothGatt`) because the scans strip comments; the code may never reference it. Illustrative pseudo-code is fenced and labelled, never a file on disk.
6. Undocumented deviation is a defect: conventions change in this file and in `decisions.md`, not in code comments. **The two stale rationales named here at first writing are closed at `a5f3bf2`:** `capability/VendorExtension.kt` no longer narrates a kernel defect that had already been repaired (§1.5), and `core/build.gradle.kts` no longer justifies itself with a state-"exposed as Flow" claim while declaring no `Flow` (§5.1) — the comment now records that the library returns with Phase 2.
7. `docs/phases/phase-1/` must hold the eight records named by prompt §44, each from `docs/templates/`. Seven exist now (`requirements`, `design`, `specs`, `task-list`, `test-plan`, `decisions`, `risk-register`) and `validation.md` is still outstanding; the root `README.md` required by prompt §43 exists in the working tree and opens with the hardware-truth statement verbatim — "OmniBuds controls actual device capabilities and does not simulate unsupported hardware functionality" — but is not yet committed, as are these phase documents (REQ-P1-022).
8. Because both modules fail the build on a warning, documentation edits must not introduce unused imports or parameters into a touched file — a doc change that leaves an unused import is a broken build.

## 12. Amendments made by Phase 1 to Phase 0's `specs.md`

| Phase 0 clause | Status after Phase 1 | Authority |
|---|---|---|
| §1.2 package root `omnibuds` | **Amended**: `com.omnibuds.core.<area>`, `com.omnibuds.android.<area>` reserved | ADR-P1-002 |
| §1.1 boolean property `is`/`has`/`can` | **Amended in scope**: prefixes now denote derived properties; stored constructor booleans are bare adjectives | realised convention, §1.1 |
| §1.5 camelCase capability id examples | **Amended**: validated lower-kebab dotted ids | ADR-P1-008 |
| §2.3 `CodecState` six members incl. `CONFIGURABLE` | **Amended**: seven-rung ladder + orthogonal `configurable` attribute | ADR-P1-005 (amends ADR-P0-015) |
| §2.3 `SessionState` six mixed values | **Superseded**: `ConnectionState` (11) held only by `DeviceState`, `SessionClassification` (2) held only by `DeviceSession` | ADR-P1-016, ADR-P1-020 |
| §3 thirteen error categories | **Amended to sixteen**: adds `READ_FAILED`, `UNKNOWN_DEVICE`, `INVALID_STATE`; nothing dropped | ADR-P1-006 (amends ADR-P0-012) |
| §1.1 (other than the boolean row), §1.2 file and package rules, §1.3, §1.4, §2.1, §2.2, §3 rules 1–6, §4 asymmetry, §5 rules 1–9, §6 traceability | **Unchanged and enforced** — inherited verbatim, and the load-bearing content of §1.4/§2.2/§3/§4/§5 is cited by section number in the source KDoc that follows it | — |
| Phase 0 `git-workflow.md` commit scopes | **Amended**: `build` and `deps` added; a phase scope such as `docs(phase-0)` accepted | ADR-P1-017 |
