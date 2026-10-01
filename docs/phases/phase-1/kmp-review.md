# Phase 1 — KMP Architecture Review

**Deliverable of:** Agent 5 — KMP Architecture Agent (Phase 1 execution prompt section 4), against prompt section 8 (KMP-ready design) and `docs/phases/phase-0/architecture-governance.md` §10 (ARCH-KMP-001 … 005).
**Question this file answers:** if Phase 46 converts `:core` into a real Kotlin Multiplatform module, what survives unchanged, what has to move, and what only *looks* portable today.
**Evidence base:** the 71 main files / 5,196 lines of `core/src/main/kotlin/com/omnibuds/core`, the 41 files / 5,885 lines of `core/src/test`, `core/build.gradle.kts`, `platform/android/build.gradle.kts`, `gradle/libs.versions.toml`. HEAD `a5f3bf2`, 302 tests passing in 37 classes.
**Companion:** `architecture-review.md` Question 7 holds the verdict; this file holds the detail.

---

## 1. What is genuinely portable today

Verified by census, not by assumption. The complete set of `import` lines across all 71 main files — 78 of them — contains **no external coordinate at all**: every single one starts `com.omnibuds.core.`. There is no `java.*`, `javax.*`, `android.*`, `androidx.*` or `kotlinx.*` reference anywhere in `:core` main source. And as of ADR-P1-021 there is nothing external left to reference: `core/build.gradle.kts` declares **zero production dependencies**, its `dependencies` block being four test-only coordinates, and `kotlinx-coroutines-core` has been removed from `gradle/libs.versions.toml` as well. So the portability question is no longer "are these artifacts available on all targets?" but "there are none" — a `commonMain` conversion has no dependency to re-resolve, no version to reconcile, and no transitive JVM-only type that can arrive uninvited.

| Construct | Where | Why it is common-safe |
|---|---|---|
| `enum class` with constructor parameters and derived properties | `state/CapabilityState.kt`, `common/OmniBudsErrorCategory.kt`, `audio/Codec.kt` | language + common stdlib |
| `enum ... .entries` | `audio/CodecRegistry.kt` (`Codec.entries.filter`) | stable in common since Kotlin 1.9, and the module pins 2.0.21 |
| `sealed interface` with a variance modifier | `common/OperationOutcome.kt` (`OperationOutcome<out T>`) | common since Kotlin 1.5; `Success/Failure/Cancelled` carry no platform type |
| `@JvmInline value class` | `common/FeatureId.kt:15` | `kotlin.jvm.JvmInline` is an optional-expectation annotation: legal in `commonMain`, ignored on non-JVM targets. **This is the only JVM-targeted annotation in the module.** |
| `suspend` functions with no `kotlinx` symbols | `transport/TransportContract.kt`, `protocol/EarbudProtocol.kt`, all three `persistence/*` interfaces | `suspend` is a compiler feature, not a library dependency |
| `ByteArray`, `contentEquals`, `contentHashCode` | `transport/TransportRequest.kt`, `transport/TransportResponse.kt`, `protocol/ProtocolParser.kt`, `protocol/ProtocolEncoder.kt` | common stdlib; the hand-written `equals`/`hashCode` are correct on every target, whereas a JVM-generated one over an array property would have been wrong |
| `Regex`, `buildString`, `associateBy`, `sortedBy`, `toMutableMap`, `asSequence` | `device/DeviceFingerprint.kt`, `protocol/ProtocolRegistry.kt`, `diagnostics/SnapshotBuilder.kt`, `capability/DeviceCapabilities.kt` | all common stdlib |
| `String.uppercase()` / `lowercase()` (locale-free overloads) | `device/DeviceFingerprint.kt`, `audio/CodecRegistry.kt` | deliberate and important: `identityKey()` therefore produces the *same* string on every target, which the JVM-only `toUpperCase()` would not have guaranteed under a Turkish locale |
| `Long?` epoch-millis timestamps | `DeviceState`, `DeviceSession`, `SavedDeviceRecord`, `DiscoveredCapabilityRecord`, `ProtocolConfiguration` | ADR-P1-012 — see §3 |
| `require`/`check` refusals with rich messages | throughout (`FeatureCapability.init`, `ProtocolDefinition.init`, `CapabilityMapping.init`) | `IllegalArgumentException`/`IllegalStateException` are common |
| immutable value architecture (no shared mutable state) | every area; only `diagnostics/SnapshotBuilder` is mutable | concurrency hazards are platform-independent; nothing here depends on JVM memory semantics |

The two rules that keep it that way run on every build: `DependencyDirectionTest.coreMainSourcesDoNotImportAndroidFrameworks` (line 92) and `coreMainSourcesDoNotImportJvmOnlyLibraries` (line 107). ARCH-KMP-004's "avoid `java.util`/`java.time` types crossing boundaries" is therefore satisfied structurally, not by discipline. Dropping the coroutines dependency removed a third exposure as well: with no external artifact on the compile classpath, no transitive JVM-only type can reach a `:core` signature through a dependency's own API — the residual risk is what section 7 names, a fully qualified or reflective JVM call written by hand inside this module.

---

## 2. What merely looks portable

These are the items I would not defend in front of a Phase 46 engineer.

1. **The coroutines promise was cashed out rather than kept.** At first writing this item read "`api(libs.kotlinx.coroutines.core)` is used by nobody in `:core`" — an unused dependency standing in for a portability claim. ADR-P1-021 deleted it from `core/build.gradle.kts` and from the catalog, so the item resolves in the direction that helps KMP: `:core` now ships **zero production dependencies**, and Phase 46 has no artifact to prove portable. What genuinely remains ahead is the dispatcher question, which no dependency removal answers. The census still shows zero occurrences of `Flow`, `StateFlow`, `SharedFlow`, `CoroutineScope`, `Dispatchers`, `SupervisorJob` or `withTimeout` in main source, and the only `kotlinx` symbols anywhere in the tree are test-only: `kotlinx.coroutines.yield` in `core/src/test/.../testing/ScriptedOutcome.kt` and `runTest` in `persistence/DeviceRepositoryContractTest` and `testing/TestDoublesAreNotHardwareTest`. So "the coroutines library is available on all KMP targets" remains true but untested here, and the question that historically forces KMP splits — what `Dispatchers.Main` means on a desktop target that has no main looper, and which dispatcher owns a Bluetooth callback — is entirely ahead. The library returns with the first `Flow` surface, in the phase that needs it, with a reason on the record.
2. **`TransportContract`'s shape is a JVM/Android-RFCOMM shape.** `open/close/exchange` request/response with no correlation id and no device-initiated notification seam is portable as a *type*, but its semantics assume a synchronous exchange. GATT indications (the pattern several earbud protocols actually use) cannot be modelled through it. KMP-safe does not mean platform-complete — see `architecture-review.md` Question 2.
3. **`CodecState` portability hides an ordinal dependency.** `CodecCapability.supportsAtLeast()` compares `state.ordinal`, which compiles everywhere but is fragile against enum edits everywhere. `CodecStateTest.ladderOrderSupportsItselfAndEveryRungBelow` pins it — a good example of a test that is more valuable in a KMP world than it looks.
4. **`Long?` timestamps are portable, and that is not the same as being a time abstraction.** There is no clock seam: `DeviceState.lastUpdatedEpochMillis`, `DiagnosticEvent.timestampEpochMillis` (non-null) and `SnapshotBuilder.build(capturedAtEpochMillis)` all require a *caller* to supply a reading. That is the right Phase 1 decision, but Phase 46 will still need one named source of wall time per target — an injected `() -> Long` or a small `expect`/`actual` clock — and it does not exist yet, so nothing can be written down as "the clock boundary".
5. **`@JvmInline` on `FeatureId` is safe, but its *JVM behaviour* is still untested.** `common/FeatureIdTest` (7) pins the grammar, `parseOrNull`, the `namespace`/`localName`/`segments` views and `isVendorExtension`/`vendorName`, so the *type* is now well covered — but nothing asserts anything about the inline representation itself (boxing, `qualifiedName` erasure, behaviour in a generic context), which is exactly the surface where a JVM target and a native target may legitimately differ. That claim therefore stays `INFERRED` until Phase 46 compiles a second target. The `common/` and `session/` holes this item recorded are closed: `common` has `FeatureIdTest`, `OmniBudsErrorCategoryTest` (8) and `OperationOutcomeTest` (5), and `session` has `DeviceStateTest` (12) covering the revision ladder, `applyIfNewer`, `attemptConnection`'s refusal and `invalidatedForDisconnect` (ADR-P1-020's tests). What remains uncovered is the `state` area, which still has no test class of its own — `CapabilityState`, `ConnectionState`, `VerificationLevel.atLeast` and `SessionClassification` are exercised only through the capability, device, session and config suites. None of these gaps is created by KMP; the inline representation and `state` are the two places a target-specific regression would first appear unnoticed.

---

## 3. The epoch-millis decision, re-examined (ADR-P1-012)

`java.time` was rejected because it is JVM-only, and `kotlin.time.Instant` because it needs experimental opt-in on Kotlin 2.0.21. `Long?` epoch millis is the correct outcome for Phase 1 and it does four jobs at once: it keeps `:core` import-clean, it keeps `null` meaning "never observed" rather than a fabricated epoch (ARCH-TERM-004), it removes clock-comparison from the ordering problem, and it means Phase 46 rewrites **no** field type. The two costs are named honestly: (a) `Long` carries no timezone or calendar, so any future UI that shows "last seen 3 minutes ago" formats platform-side — which is where it belongs anyway; and (b) monotonic ordering *inside a process* is carried by `DeviceState.revision`, not by the clock, so `revision` is load-bearing for the stale-response defense (`session/DeviceState.kt:117`, `applyIfNewer`) — a load that is now test-backed rather than asserted, since `DeviceStateTest` pins `.aStaleResponseCannotOverwriteNewerState`, `.anEqualRevisionIsNotNewerInformation` and `.aNewerStateFromADifferentSessionIsNeverAdopted`. If Phase 46 later adopts `kotlin.time`, that is a one-line-per-field sweep, and the ordering rule it must not disturb is already fixed by those tests: otherwise the clock and the counter become two orderings again — which is the same failure ADR-P1-020 removed at the connection-state level.

---

## 4. `expect` / `actual` seams a future Bluetooth transport needs

**Inside `:core`: none.** Not "few" — none. Every platform capability that later phases need is already expressed as an *interface plus injected value*: `TransportContract` (channel), `TransportAvailability` (per-kind, per-purpose evidence), `EarbudProtocol` + the five optional support interfaces (operations), `DeviceRepository`/`CapabilityRepository`/`ProtocolRecordAccess` (storage), `OmniBudsLogger` + `DiagnosticSnapshot`/`SnapshotBuilder` (reporting), `ApplicationConfiguration` (mode and flags). ADR-P1-009's constructor injection is what makes this work: a target supplies implementations, it does not supply `actual` declarations.

That is a recommendation, not an accident, and it should survive Phase 46: **prefer interfaces over `expect`/`actual` for Bluetooth.** An `expect` declaration forces every target to answer for a Bluetooth stack it may not have, and the natural `actual` for an unsupported platform is a throw — which ARCH-AND-005 forbids ("report the restriction, never as a capability") and which `TransportAvailability` already answers correctly with `available = false` plus a mandatory `reason` of `TRANSPORT_UNAVAILABLE`. Interfaces can say "this platform cannot"; `expect`/`actual` has to invent something.

Legitimate future `expect`/`actual` candidates, all small and all outside the domain types: a clock reading (`() -> Long`), a per-target dispatcher provider once scopes exist, an OS hex/byte utility when `ManufacturerDataEntry.dataHex` grows a real codec, and a storage driver seam in Phase 22 (Room vs SQLDelight per target). None of them belongs in `:core`'s domain areas; if one ever must, it is an ADR.

---

## 5. `androidMain` versus a future `desktopMain`

```text
 commonMain (= today's :core, unchanged)
   DeviceIdentity · DeviceFingerprint · DeviceSession · DeviceState · CapabilityState ·
   ConnectionState(+Transitions) · VerificationLevel · SessionClassification · FeatureId ·
   FeatureCapability · DeviceCapabilities · CoreFeature · VendorExtension · Codec/CodecState/
   CodecCapability/CodecRegistry · AudioTransportState · ConfigurationValue · the three
   configurations · FeatureFlag · error + outcome vocabulary · ProtocolDefinition/Command/
   Response/CapabilityMapping/Registry/Parser/Encoder/Identification/EffectClass ·
   EarbudProtocol + optional support interfaces · TransportContract/Request/Response/
   Availability · persistence contracts + records · diagnostics models
        │                                       │
   androidMain (from Phase 2)              desktopMain (Phase 47)
     BluetoothAdapter/Device/GATT/Socket       BlueZ (Linux) / WinRT (Windows) /
     LeAudio, SCO, A2DP/AVRCP/HFP control      CoreBluetooth (macOS) equivalents
     runtime permission gates (API 31 model,   no notification shade, no Quick
       minSdk revisit per ADR-P1-015)          Settings tile — report as unsupported
     AudioManager / codec-override surfaces    tray / system-integration surfaces
     notifications, Quick Settings tiles       file-based config + storage driver
     WorkManager / foreground service          desktop process lifecycle
     Android logcat sink + redactor
```

The split rule is ARCH-KMP-001's table and prompt §8's list, unchanged: *whoever touches an OS API owns a platform source set; whoever decides what a capability means does not.* Nothing in the `commonMain` column above currently violates it, and the `platform/*` code must not leak upward into it — `TransportContract`'s KDoc already states that nothing in `:core` may implement it, and `noProductionClassImplementsTheProtocolOrRepositoryContracts` enforces exactly that.

Vendor protocol modules (Phase 19/41) belong in `commonMain` too: a parser over `ByteArray` is portable by construction, which is the whole argument for keeping protocol knowledge as data (ARCH-QUAL-002, `coreContainsNoHardCodedProtocolLiterals`).

---

## 6. Concrete Phase 46 migration steps

Ordered so that each step is checkable before the next:

1. Add `kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }` to `gradle/libs.versions.toml`; replace `alias(libs.plugins.kotlin.jvm)` with it in `core/build.gradle.kts`.
2. Delete the `java { sourceCompatibility/targetCompatibility }` block and `javaParameters`; set `jvmTarget` per target and use a Gradle toolchain for JDK 17 — while keeping ADR-P1-014's rule that the JDK is selected by `JAVA_HOME` per invocation and nothing machine-level is written to shared Gradle files.
3. Declare targets incrementally: `jvm()` first (so the 302 tests still run), then `androidTarget()`, then one native target. Do not add desktop targets before Phase 47 needs them — ADR-P1-001's "earn their keep" applies to targets as much as to modules.
4. Move `core/src/main/kotlin` → `core/src/commonMain/kotlin`. **One source edit is expected, and only one:** `common/FeatureId.kt` uses `@JvmInline` resolved through the JVM default import, so in `commonMain` it needs an explicit `import kotlin.jvm.JvmInline` (an optional-expectation annotation, legal there and ignored on non-JVM targets — the annotation itself stays). No other file names anything outside `com.omnibuds.core.*`, which is the payoff of §1, and ADR-P1-021 means no dependency line has to move with it.
5. Split tests: `architecture/DependencyDirectionTest.kt` (`java.io.File`) and the six `::class.java` assertions in `testing/TestDoublesAreNotHardwareTest.kt` (lines 228–237) move to `jvmTest`; the remaining 39 files move to `commonTest`. `ScriptedOutcome.kt` needs `kotlinx-coroutines-test` in `commonTest` (available on all targets). These two are the only JVM-coupled test files in the tree — one by import, one by reflection — and `kotlinx.coroutines.test.runTest` in `persistence/DeviceRepositoryContractTest` is multiplatform-safe, so it needs no split.
6. **Re-home the two file-system paths in `DependencyDirectionTest`** — `File("src/main/kotlin")` (line 25) and `File("../platform/android/src/main")` (line 265) — to `src/commonMain/kotlin` etc. If this is missed the tests do not pass vacuously: `mainSources()` fails loudly with the working directory in the message (lines 45–52), which is exactly the guard Phase 0's "a rule that cannot be checked is advisory" demanded.
7. ~~Move `api(libs.kotlinx.coroutines.core)` to `commonMain` dependencies~~ — nothing to move: ADR-P1-021 deleted the coordinate, so `commonMain` starts with **zero** dependencies and only the stdlib. If Phase 2 introduced the first `Flow` surface before Phase 46, that phase will have added coroutines to `commonMain` with a recorded reason, and this step becomes "already done, with evidence".
8. `:platform:android` keeps `api(project(":core"))` unchanged; the `kotlin-android` plugin there may be dropped once the Android target comes from core, or kept — decide in Phase 46, not now, and record it as an ADR.
9. First real proof: run the full suite on `jvm`, `android` and one native target. Until a non-JVM compile succeeds, every portability claim in this document is inference at `VerificationLevel.INFERRED`, and `validation.md` must say so rather than upgrading it.
10. Only then consider the named `expect`/`actual` seams of §4 — one clock, one dispatcher provider — each in its own commit with its own ADR.

---

## 7. What catches a regression, and what does not

**Would catch it:** `coreMainSourcesDoNotImportAndroidFrameworks` and `coreMainSourcesDoNotImportJvmOnlyLibraries` (both in `core/src/test/kotlin/com/omnibuds/core/architecture/DependencyDirectionTest.kt`) fire the moment anyone adds an `android.*`, `java.*` or `javax.*` **import** to `:core` main source. `packageStatementsMatchSourceDirectories` catches the package drift that would silently disable both, and `everyCoreAreaIsRegisteredInTheLayerMap` catches an area being absorbed to dodge a layer rule. Those four are the real KMP guards, and they are the reason REQ-P1-004 (KMP preserved) is defensible today.

**Would not catch it — and this is the honest hole:**

- a **fully-qualified** JVM call written inline with no import (`java.util.Locale.getDefault()`, `System.lineSeparator()`, `Thread.sleep(10)`, `String.format(...)`). Rules 1 and 2 scan `import` lines only; the body-scanning rules cover Bluetooth/media types, stubs, doubles, vendor contract implementations and magic literals — not platform APIs.
- **reflection**, which needs no import to bind to a JVM type: `Class.forName("java.util.UUID")`, `::class.java`, a method-handle lookup. The test tree already contains six `::class.java` assertions in `testing/TestDoublesAreNotHardwareTest.kt` (228–237), which is legitimate there and exactly the pattern that would be invisible here if someone wrote it in `:core`.
- a **transitive** JVM-only type reaching a public signature through a dependency. This is the one hole ADR-P1-021 has actually narrowed rather than merely documented: with zero production dependencies there is no transitive route today, and the import scan would not see one if a later phase re-opened it — a coroutines or Room artifact added to `commonMain` would have to be argued for in its own ADR, which is now the only gate.
- a **JVM-only stdlib function reachable without an import**, e.g. `String.format`, `"x".toUpperCase()`, `System.arraycopy`, `kotlin.jvm.*` helpers, `java.util` type inference through a platform type. The module compiles against the JVM stdlib, which is a superset of the common one, so the entire 301-test run can pass a change that breaks every other target.
- the **build script**, which is currently the least portable part of the module (`org.jetbrains.kotlin.jvm`, `JvmTarget`, the `java {}` block) and is checked by nothing.
- **any actual multiplatform compile** — none exists, and cannot until Phase 46 step 9.

Recommended cheap fixes, both within Phase 1's own patterns: extend the two platform rules to scan `codeLinesOf()` bodies for `java.`/`javax.`/`android.`/`kotlin.jvm.`/`String.format`/`System.`/`Thread.` tokens (the helper and the comment-stripping already exist for the other rules), and rename the two tests so they describe what they check — *imports*, not all usage. Until then, the correct statement is not "the core is JVM-clean" but "the core is import-clean on a scan that a body-level call can step around", and `architecture-review.md` records that as disagreement item 4.

---

## 8. Bottom line

`:core` is portable in the way that matters most and is hardest to retrofit: its **vocabulary** has no platform in it. A Phase 46 engineer should expect to move directories, replace one Gradle plugin, add one `import kotlin.jvm.JvmInline` line, split two test files and fix two hard-coded scan paths — and to change no domain type, no field, no signature and **no dependency line, because there are none to change**. That is the whole point of ADR-P1-012 and ADR-P1-002, and it is verified rather than asserted: zero external imports across 71 files (78 import lines, every one `com.omnibuds.core.*`), and, since ADR-P1-021, zero production dependencies in the build script that has to compile for every target. ADR-P1-020 helps the same cause from the other side: with one type owning connection state, the domain has one value to publish through a future `StateFlow` per device rather than two candidates to reconcile.

What Phase 1 does **not** buy is proof. The first compile for a second target is the first actual evidence, the coroutines and dispatcher surfaces that will decide whether the split stays clean are not written yet, and the scan that protects the property checks imports rather than code. Every portability statement in this document therefore sits at `VerificationLevel.INFERRED` — `state/VerificationLevel.kt` defines that rung as "derived from documentation or protocol inference. No execution", and `IMPLEMENTED` ("code exists that would perform it") is already one rung higher. The Phase 46 run of step 9 is what moves it to `LAB_TESTED` at best; nothing here touches hardware, so no statement in this file may be reported above those rungs.
