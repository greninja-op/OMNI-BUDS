# Phase 1 — Testing Review

**Document class:** Phase 1 deliverable of the Testing workstream (execution prompt §4, Agent 4).
**Scope.** The test architecture as it was actually built and run in Phase 1, its position against `docs/phases/phase-0/testing-governance.md`, and the gaps that later phases must close. Per-unit test records, ids, tier table and the REQ matrix are in `docs/phases/phase-1/test-plan.md`; this document does not restate them.
**Nothing here is a capability claim.** Phase 1 tested no device, no phone, no emulator and no Bluetooth stack.

---

## 1. Shape of the suite as built

| Fact | Value |
|---|---|
| Tests executed | 302 · 0 failures · 0 errors · 0 skipped |
| Build | `./gradlew build` → BUILD SUCCESSFUL |
| Main sources | 71 files / 5,196 lines, of which 2,807 lines begin with a comment marker (`//`, `/*`, `*`) |
| Test sources | 41 files / 5,885 lines: 37 test classes (5,498 lines) + 4 test-only doubles |
| Test tier | every test is `T1` (JVM unit); no other tier exists |
| Packages with tests | `architecture`, `audio`, `capability`, `common`, `config`, `device`, `diagnostics`, `persistence`, `protocol`, `session`, `testing`, `transport` |
| Tests by package | protocol 60, capability 56, device 37, audio 31, config 19, common 19, transport 19, diagnostics 22, architecture 11, session 12, testing 8, persistence 7 |
| Suspending tests | 13, driven by `kotlinx.coroutines.test.runTest` (persistence contracts and the double guard) |
| Negative/invariant assertions | 72 typed `assertFailsWith` call sites (71 `IllegalArgumentException`, 1 `IllegalStateException`) |

Test-to-source line ratio slightly above 1:1 is not padding: the dominant test idiom is "assert that a claim cannot be constructed", which needs a case per illegal combination, and every case carries a one-line reason so a future reader knows which rule it protects.

### 1.1 Source sets and module wiring

- `core/src/main/kotlin` — the platform-independent domain, Kotlin/JVM, `jvmTarget 17`, with **no production dependency at all** (ADR-P1-021): `kotlinx-coroutines-core` was removed once the census showed no main source imports `kotlinx.coroutines`, because `suspend` is a language feature. Test-only artifacts (`kotlin-test`, `junit-jupiter`, `kotlinx-coroutines-test`, `junit-platform-launcher`) are the module's entire dependency declaration.
- `core/src/test/kotlin` — every test and every double. Nothing in `src/test` can reach a shipped artifact, which is what makes the double policy enforceable rather than aspirational.
- `platform/android/src/main` — exists, compiles as an Android library, and holds **zero** Kotlin sources; `platformAndroidModuleStillContainsNoSources` keeps it that way (ADR-P1-001, ADR-P1-015).
- No `androidTest` source set, no instrumentation, no Robolectric, no shared-test fixture module. Phase 1 deliberately has no test that needs an Android runtime, because it has no Android code.

### 1.2 Framework choices and why

- **JUnit 5 (`junit-jupiter` 5.10.1) via `useJUnitPlatform()`**, plus `junit-platform-launcher` at test runtime. Chosen because JUnit 4 cannot drive a `kotlin.test` suite on a modern JDK without the vintage engine, and because the platform launcher is what a later CI and a later coverage plugin will read. `testLogging` is configured for `failed` and `skipped` events with `FULL` exception format, so a failure is legible in the console without opening the XML.
- **`kotlin.test` for assertions**, not AssertJ or Truth. It compiles unchanged when `:core` moves to Kotlin Multiplatform in Phase 46 (ADR-P1-004 KMP boundary), and its `assertFailsWith`/`assertEquals`/`assertSame` set covers everything Phase 1 needs. The `Test`/`assertEquals` imports in every test file are `kotlin.test.*`; the `@Test` annotation is Jupiter's.
- **`kotlinx-coroutines-test` for the 13 suspending cases**, because the persistence seams are `suspend` and a real `runBlocking` would both hide dispatch assumptions and drift from the coroutine rules in `specs.md` §5. It is `testImplementation` and is the only coroutines artifact in the tree: `:core`'s production dependency set is empty (ADR-P1-021).
- **No mocking library.** MockK was explicitly not added (ADR-P1-011): it is JVM-only, its dynamic proxies would obscure the "no fake hardware" rule behind generated stubs, and hand-written fakes are cheaper to audit in a project whose central product rule is about fakes.
- **No test-level parallelism, no test-order dependency.** No `junit-platform.properties` exists, so JUnit's parallel execution is not enabled; `org.gradle.parallel` and `org.gradle.caching` in `gradle.properties` parallelise tasks, not test workers. Ordering-sensitive behaviour (registry listings, snapshot maps) is pinned by assertions inside tests rather than by runner configuration.

---

## 2. The fake and double strategy (`core/src/test/kotlin/com/omnibuds/core/testing/`)

Four types, all test-only, all named `Fake…`/`Scripted…` so that `productionSourcesDefineNoTestDoubles` would catch a migration into `src/main`:

| Double | Stands in for | Default state | Refuses to |
|---|---|---|---|
| `FakeDeviceRepository` | `DeviceRepository` | empty map, no pre-seeded devices | save a blank `identityKey`; `markSeen` a device the user never saved |
| `FakeCapabilityRepository` | `CapabilityRepository` | empty cache; `load` → `Success(null)` | return a synthesised "empty capability set" for a key it has nothing for |
| `FakeProtocolRecordAccess` | `ProtocolRecordAccess` | matches no protocol for any fingerprint | resolve an unscripted protocol id, or offer candidates nobody scripted |
| `ScriptedOutcome<T>` | verdict queue for any operation | empty queue | return a default `Success` — it raises `IllegalStateException` instead |

Three design decisions carry the whole policy:

1. **Defaults are empty, not agreeable.** A generous fake is the failure mode prompt §53 prohibits, so a double only produces a populated answer when a test scripted one. `TestDoublesAreNotHardwareTest` exists solely to pin this: an unscripted step raises, a scripted failure comes back as a failure, a scripted cancellation comes back as `Cancelled` and stores nothing.
2. **A consumed verdict writes nothing.** `save`/`store` consult the queue *before* mutating the map, so "the fake said Failure" and "the fake stored it anyway" cannot coexist. That is what lets `nothingButAnExplicitSaveEverEntersTheUsersList` and `aScriptedFailureIsReturnedAsFailureAndNotSwallowedIntoADefaultSuccess` mean anything.
3. **Every refusal is stamped.** `DOUBLE_MARKER` (`"FakeDeviceRepository [test double, no storage]"`) is embedded in refusal detail text so a leaked double is identifiable in any log line (TST-MOCK-002), and each class KDoc opens with a bold "THIS IS NOT STORAGE AND NOT HARDWARE" statement naming the properties it does *not* provide (durability across process death, encryption at rest, migration).

**Deviation from the prompt, recorded rather than smoothed over.** Prompt §27 sketches `FakeProtocol` and `FakeDeviceSession`. Neither exists. `EarbudProtocol` has no double in any source set — the only disk reference to the name in test code is the architecture scan's own contract list — and a session is built by hand as a value through `DeviceSession.temporary(...)` in `DeviceSessionTest`. The reasoning is defensible for Phase 1 (a protocol double with no protocol record would have to invent identification answers, which is precisely the lie the doubles policy forbids), but it means **the `T2`/`T3` seams have no test harness yet** — see gap G-2.

---

## 3. How state-machine and invariant testing works here

- **Construction-time invariants, tested by trying to break them.** The house idiom is `assertFailsWith<IllegalArgumentException> { … }` against an `init` block or a factory guard: affordance/state pairs (`FeatureCapabilityTest`), the codec ladder floor (`CodecStateTest.nonPositiveTargetIsNeverSatisfied`), read-attempt budgets on write commands (`CommandDefinitionTest.aWriteCarriesNoAttemptBudgetAtAll`), battery ranges, blank identifiers, `TransportKind.UNKNOWN` inside a protocol definition, and the feature-id grammar (`FeatureIdTest.aSingleSegmentNameIsRefusedRatherThanGuessedIntoShape`). A nonsense claim that cannot be constructed needs no test at the call site; the tests exist to keep a future `init` relaxation from going unnoticed.
- **Safety tables tested as data, not as prose.** `OmniBudsErrorCategoryTest` enumerates all sixteen categories and pins every `retryClass` and `invalidatesSession` value, which is the machine-checked form of "a timed-out write is re-read, never re-sent"; `OperationOutcomeTest` pins that `map` touches only `Success` and that `Cancelled` is neither success nor failure. Before commit `a5f3bf2` both rules lived only in KDoc and in whichever neighbouring test happened to read them.
- **Ordinal ladders as data, tested as order.** `CapabilityState`, `VerificationLevel` and `CodecState` encode their evidence ladder in declaration order. Tests assert the order itself (`ladderOrderSupportsItselfAndEveryRungBelow`) and the derived predicates (`isControllable`, `supportsAtLeast`, `atLeast`), so reordering an enum breaks a test rather than silently changing merge rules. `DeviceCapabilities.evidenceOf` deliberately re-states the ladder as numbers so a reorder surfaces as a non-exhaustive `when` at compile time.
- **State machines exercised exhaustively over the state set, in the one type that owns them.** `DeviceStateTest.everyStateCanFallIntoErrorAndASelfMoveIsIdempotent` loops over `ConnectionState.entries` rather than sampling pairs — an accidental narrowing of the transition table fails — and `anIllegalConnectionMoveIsRefusedWithStructuredErrorAndChangesNothing` asserts both the category (`INVALID_STATE`) and that the refused move left the connection and the `revision` untouched. The staleness rule of prompt §30 is tested as a negative (`aStaleResponseCannotOverwriteNewerState`, `anEqualRevisionIsNotNewerInformation`, `aNewerStateFromADifferentSessionIsNeverAdopted`) so ordering is proven to travel in the data. One honesty note the test itself makes visible: reaching a given state for the table sweep needs `copy(connection = …)` (the file's private `forceConnection` helper), because `DeviceState` is a `data class` and `attemptConnection` is a convention a caller can bypass — `test-plan.md` gap `TEST-P1-065`.
- **Merge algebra tested as an algebra.** `DeviceCapabilitiesTest` pins the properties that make merging discovery passes safe: never downgrade a higher rung, a tie keeps the receiver wholesale and is deterministic, features only one side held survive, an explicit `UNKNOWN` record is out-ranked by any established state, and a handed-in map is copied so no later mutation reaches the snapshot.
- **Immutability and detachment.** `withProducesANewInstanceAndLeavesTheOriginalAlone`, `eachUpdateMovesTheRevisionForwardAndLeavesTheOriginalAlone`, `aBuiltSnapshotIsDetachedFromTheBuilderThatProducedIt`, `deviceConfigurationSnapshotsTheSourceMapSoLaterMutationCannotReachIt`, `registeringReturnsANewValueAndLeavesTheOriginalUntouched`, `aCallerCannotSmuggleACommandIntoADefinitionItAlreadyBuiltTheMapFrom` — value semantics is asserted, not assumed, because ADR-P1-009's no-singleton rule depends on it.
- **Content equality over arrays.** `TransportRequestTest`/`TransportResponseTest` pin `contentEquals`-based identity and the null-versus-empty distinction, because response correlation and caching would otherwise be silently wrong.
- **No clock in the tests and none in the code.** Timestamps are supplied by the caller, so no test reads the wall clock and none needs to be re-run at a particular time; `anUnrecordedCreationTimeStaysUnrecorded` (a session with no recorded start is `null`, not `0`) and `anInitialStateAssertsNothingAboutAnything` (a state with no recorded update is `null`) are the interesting cases this makes deterministic.

---

## 4. How architecture rules became executable tests

`DependencyDirectionTest` is 11 checks over the *text* of `src/main/kotlin`, written with the standard library only (ADR-P1-011). The interesting engineering is in its own honesty:

- **It fails loudly when its inputs are missing.** `mainSources()` calls `fail(...)` if `src/main/kotlin` is absent, and again if it yields no `.kt` files; `platformAndroidModuleStillContainsNoSources` fails rather than passes if the Android directory is gone. A scan that silently finds nothing would otherwise be a green test proving nothing.
- **Comment and KDoc lines are excluded from identifier scans.** `codeLinesOf()` strips `//`, `/*`, `*` and `@/` lines, so documenting `BluetoothGatt` or `AudioManager` as a forbidden concept is not a violation. This is what lets `TransportContract`'s KDoc explain what it must never do while the scan still forbids doing it.
- **Import rules and identifier rules are different scans.** Imports are matched on the `import` statement prefix; framework identifiers and magic literals are matched on code lines. `java.io` in the test source set is legitimate — the test reads the tree — and the rules apply to main only.
- **What it can see and what it cannot.** It sees text patterns and the layer map; it does not see a compiled dependency graph, transitive module dependencies or reflection. A rule like "domain does not import UI" is enforced name-wise (`android.`/`androidx.`/`com.omnibuds.android`) plus the fact that no UI module exists; when a UI module is created, this check needs widening (gap G-6).
- **It protects decisions, not moods.** Each check cites the ADR it defends: ADR-P0-008 and prompt §32 for Android, ADR-P1-012 for `java.*`, ADR-P1-003 for the layer map, ADR-P1-013 for doubles and contracts-only, ADR-P1-001 for the empty boundary module, master §52/ADR-P0-003 for magic literals. That citation is what makes them un-deletable: removing a check deletes a decision.

This is how prose became a gate: Phase 0 wrote several hundred rules in `architecture-governance.md` and `testing-governance.md`; the mechanically expressible subset now runs on every build, and the residual prose is exactly the part a reviewer must still read.

---

## 5. Tier mapping against `testing-governance.md`

| Tier | Reachable in Phase 1? | Reason |
|---|---|---|
| T1 unit | **yes — 302 tests** | pure logic, no radio, no stack, no device; ceiling `IMPLEMENTED` |
| T2 protocol/contract | no | requires recorded or simulated traffic; `ProtocolParser`/`ProtocolEncoder` are contracts with no implementation and no double |
| T3 integration | no | requires a faked platform-Bluetooth boundary behind a transport implementation; `TransportContract` has no implementation to fake around |
| T4 hardware-in-the-loop | no | no device; conditions 1–8 of TST-HW-001 cannot begin |
| T5 persistence | no | T4 is a prerequisite, and disconnect/reconnect must be physical (TST-PER-004) |
| T6 cross-device / T7 cross-phone | no | no model or OEM build was tested; brand-level claims are forbidden outright (TST-HW-004) |
| T8 regression | no | preserve-only tier requiring an identical recorded environment; nothing stores that yet |

Highest VerificationLevel justified by anything in Phase 1: **`IMPLEMENTED`**, and only for code shapes that exist. Nothing justifies `LAB_TESTED` (no recorded capture, no simulator), and `HARDWARE_VERIFIED`/`PERSISTENCE_VERIFIED` are **unavailable**, not pending. `CodecState.ACTIVE`/`NEGOTIATED` are explicitly capped at T4 evidence by TST-TIER-003: Phase 1 constructs those rungs as values and reports nothing about any device. Governance's stricter `IMPLEMENTED` ceiling is used in preference to the template's looser unit-tier row (see `test-plan.md` §1).

---

## 6. Gaps, with the phase that must close each

| Id | Gap | Evidence | Closes in |
|---|---|---|---|
| G-1 | **No CI.** The whole suite runs only when the orchestrator types `./gradlew build`; nothing enforces it on change. Build records `TEST-P1-001`…`004`, `006` are therefore `MANUAL`. | no workflow files; prompt §49 clean-build assertions cannot be automated yet | **Phase 2**, before any platform code lands |
| G-2 | **No double for the protocol/transport seams.** `EarbudProtocol`, `TransportContract`, `ProtocolParser`, `ProtocolEncoder` and the five `…ReportingSupport` interfaces have no test implementation in any source set, so no test drives a session over a faked channel. | grep of `core/src/test` shows only the architecture scan naming those contracts | **Phase 2/3** (T2), **Phase 6** (T3) |
| G-3 | **No coverage measurement.** Line/branch coverage of the domain is unknown; "302 green" says nothing about the untested paths inside `DeviceFingerprint.identityKey()` or `ProtocolDefinition.init`. | no Kover/Jacoco plugin in either module | **Phase 2** |
| G-4 | **No mutation testing.** The suite's own strength is unmeasured; the defects in `test-plan.md` §14 (D-1…D-3 caught by tests, D-4…D-7 caught by the review pass) are the only evidence that it bites. | no PIT/MutationScope configuration | Phase 2+ (nice to have; the D-1…D-7 record is the interim evidence) |
| G-5 | **No instrumentation test and no Android test source set.** Correct for Phase 1 (no Android sources), but the moment `:platform:android` gains code it needs a runner, permissions handling and a device/ emulator policy decided *before* the first commit, not after. | `platform/android/src` has no `androidTest` | **Phase 2** |
| G-6 | **Test-name ↔ `TEST-P1-<NNN>` traceability is not enforced.** `docs/phases/phase-0/specs.md` §1.4 requires every test method to carry its id "in its name or a documented tag". No method name carries an id; three test KDocs explicitly defer numbering because the test-plan "does not exist yet", and the four files added in `a5f3bf2` carry no ids either. This plan supplies the documented mapping at class-record granularity, but nothing machine-checks that a new test gets an id, and a new test can silently join a class and inflate a record's count. | `ConfigurationSeparationTest.kt:24`, `DeviceIdentityTest.kt:16`, `TestDoublesAreNotHardwareTest.kt:48` | **Phase 2**: add an architecture-style check (name pattern or `@Tag`) that fails a test method with no id |
| G-7 | **CLOSED in commit `a5f3bf2`.** `DeviceState` — the single owner of connection state after ADR-P1-020 — now has `DeviceStateTest`: 12 tests over `attemptConnection` (legal move advances `revision`, illegal move returns `Failure(INVALID_STATE)` and changes nothing), `applyIfNewer` (older, equal, and cross-session candidates all rejected; strictly newer same-session adopted), `invalidatedForDisconnect` (live readings zero to unknown, capability records survive) and `isOperational`. What remains open here is not the absence of tests but the bypass those tests had to use: `copy(connection = …)` still rewrites the owner without consulting `ConnectionStateTransitions`. | `core/src/test/kotlin/com/omnibuds/core/session/DeviceStateTest.kt`; `test-plan.md` `TEST-P1-060`/`TEST-P1-061`, residual gap `TEST-P1-065` | residual rule-enforcement: **Phase 2** |
| G-8 | **Largely closed in commit `a5f3bf2`.** `common` now has a test package: `FeatureIdTest` (7, grammar both directions plus `parseOrNull`), `OmniBudsErrorCategoryTest` (7, all sixteen categories with `invalidatesSession` exhaustive and `retryClass` pinned for the eleven safety-critical ones), `OperationOutcomeTest` (5). Still covered only indirectly: `TransportKind` and `RetryClass` as subjects of their own — no assertion enumerates their members — and the five transport-shaped categories' `retryClass`. | `core/src/test/kotlin/com/omnibuds/core/common/`; `test-plan.md` `TEST-P1-062`…`064`, residual `TEST-P1-054` | Phase 2 for the two vocabularies |
| G-9 | **No hardware harness, no fixture capture, no replay format.** `TST-TIER-004` requires environment identity on every T4+ result; nothing exists to record it, so no later phase can start T4 without building the record format first. | no capture/replay code in the tree | **Phase 2** for the seam, Phases 16/20 for the lab |
| G-10 | **Architecture checks are text-based.** They will not catch a same-name import alias, a reflection call, or a service-loader-discovered implementation; and "domain does not import UI" is name-based because no UI module exists to reference. | `DependencyDirectionTest` regexes | Phase 25+ (when a UI module genuinely exists) |
| G-11 | **No lint gate wired into `check`.** No detekt, no ktlint, no Spotless, no Android lint (ADR-P1-011); formatting drift is unchecked, `allWarningsAsErrors` covers compiler-visible issues only. | build files | Phase 2, as an ADR with a cost statement |

---

## 7. What Phase 2 must add before Bluetooth code lands

Ordered, and each item is a precondition rather than a nice-to-have:

1. **CI running `./gradlew build`** on every change, so build records `TEST-P1-001`…`006` become `AUTOMATED` instead of orchestrator memory, and so G-1's "skipped test is a phase failure" rule is machine-visible.
2. **Coverage measurement with a recorded baseline**, so a new domain file cannot be added without a test.
3. **The `TEST-P1-<NNN>` naming/tag check** (G-6) — cheap to add while the suite is 41 files and harder to retrofit painlessly at 300 tests.
4. **Enforce, do not merely test, the single-owner rule** (G-7's residue). `DeviceStateTest` now pins `attemptConnection`, `revision`, `applyIfNewer` and `invalidatedForDisconnect`, and ADR-P1-020 left `DeviceSession` with no connection field to disagree about; what is still missing is a check that stops a caller rewriting `connection` through `copy(...)`, which the table sweep itself uses as a fixture. Phase 2's session holder needs a state-update path — and ideally a `DependencyDirectionTest`-style scan — before any surface reads state.
5. **A fake transport + fake protocol harness** (G-2) with the same refuse-to-invent discipline as `com.omnibuds.core.testing`, plus a scripted-bytes fixture format recording provenance (`CAPTURED FROM REAL DEVICE (identity …)` / `VENDOR-DOCUMENTED` / `HAND-AUTHORED`, TST-MOCK-005). Without provenance in the format, every T2 run later inherits an unknown evidence tier.
6. **An `androidTest` policy and a device/adapter-state stub** (G-5): which tests need a physical device, how they are named so a hardware test cannot join the default run (`specs.md` §1.4), and what environment identity is captured per run (TST-TIER-004).
7. **A write-gate test for the unknown device** before the first characteristic write exists: unknown-device read-only (TST-SAFE-001), no blind writes (TST-SAFE-002), no fuzzing in any tier (TST-SAFE-003), and a test that a timed-out write is resolved by re-reading rather than re-sending (TST-SAFE-005/006) — the model for all four already exists as data in `EffectClass` and `OmniBudsErrorCategory.retryClass`; Phase 2 needs the behaviour test.
8. **Re-run discipline stated up front** (TST-REG-001): touching the transport/protocol boundary obligates T3 plus T4 on at least one real device per affected fingerprint, and a capability/codec state logic change obligates T1 plus affected T3. Deciding this after Bluetooth lands is how a project ends up with claims it cannot trace.

---

## 8. Assessment

The foundation is genuinely tested rather than nominally tested: 302 unit tests, a test-to-source line ratio above 1:1 (5,885 test lines against 5,196 main lines), invariant-by-construction as the dominant idiom, exhaustive iteration over state sets and over the sixteen error categories rather than sampled pairs, and seven real defects recorded in `test-plan.md` §14 rather than quietly fixed — three caught by the suite while it was being integrated (one of them a three-way architecture violation set), four caught by the review pass, including the two-owner connection-state defect that ADR-P1-020 then re-architected and `DeviceStateTest` pinned. The architecture rules are executable, self-guarding against vacuous passes, and cited to the ADRs they protect.

Its weaknesses are structural, not quality-related: only T1 exists, there is no CI, no coverage number, no hardware harness, and no automated traceability between a test name and its `TEST-ID`. The gap that was a real hole in this phase's own scope — `DeviceState`, the authoritative-state mechanism REQ-P1-017 exists to protect, untested — is now closed by `core/src/test/kotlin/com/omnibuds/core/session/DeviceStateTest.kt`, and `common` is no longer a test-free area. `Ready for Phase 2` should still not be answered YES while G-1 (no CI) and G-6 (no id traceability) are open, because both become progressively more expensive to close once platform code exists.
