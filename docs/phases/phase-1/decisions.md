# Phase 1 — Architecture Decision Records

Phase: 1 — Project Foundation & Kotlin Architecture.
Authority: `docs/MASTER-CONTEXT.md`, then Phase 0 ADRs and governance rules, then this file. Where this phase had to amend Phase 0, the amendment is named explicitly rather than left implied (Phase 1 prompt section 3: "Do not contradict Phase 0 without documenting an architectural decision").

---

### ADR-P1-001 — Two modules now, the rest when they earn their keep
**Status.** accepted
**Context.** Phase 1 prompt section 5 sketches `android/`, `core/*`, `bluetooth/*`, `protocols/<11 vendors>`, `database/`, `protocol-lab/`, `testing/`, `desktop/` — and then says not to create empty or meaningless modules.
**Decision.** Create exactly `:core` (Kotlin/JVM, platform-independent) and `:platform:android` (Android library, zero Kotlin sources). Every other sketched module is created by the phase that first has real code for it: `:bluetooth:*` in Phase 6, `:protocols:<vendor>` in Phases 19/41, `:database` in Phase 22, `:protocol-lab` in Phase 20, `:desktop` in Phase 47, `:android` app shell in Phase 49.
**Consequences.** The build stays honest — an empty module is a claim of structure without substance. Adding a module later is cheap and is now a deliberate, phased act. `DependencyDirectionTest.platformAndroidModuleStillContainsNoSources` asserts the boundary module is still source-free, so this decision is checked rather than remembered.

### ADR-P1-002 — Package root is `com.omnibuds.core.<area>`
**Status.** accepted — amends Phase 0 `specs.md` section 1.2
**Context.** Phase 0 fixed the package root as bare `omnibuds`; the Phase 1 prompt section 31 uses `com.omnibuds.core.*`.
**Decision.** `com.omnibuds.core.<area>` is canonical, with `com.omnibuds.android.<area>` reserved for the platform module. Reverse-domain roots are what Android and KMP tooling expect, and a bare `omnibuds` root would collide with the application-id convention.
**Consequences.** Phase 0's example text is superseded for package roots only; the rest of `specs.md` (naming, state, error, coroutine, retry rules) stands unchanged and is enforced.

### ADR-P1-003 — The area layer map, and the three violations it caught
**Status.** accepted
**Decision.** Core areas are ordered into layers, and an import may only point at a strictly lower layer or its own area:

```text
0 common      (FeatureId, TransportKind, error + outcome vocabulary)
1 state       (CapabilityState, ConnectionState, VerificationLevel, SessionClassification)
1 transport   (TransportContract, request/response — platform-neutral contract)
2 device · capability · audio · config · diagnostics
3 session · persistence
4 protocol    (EarbudProtocol, protocol definitions, registry)
```

**Three real violations found by the enforcement test, and how they were fixed:**
1. `common -> transport`: the error model referenced `TransportKind`, which sat in `transport`. `TransportKind` moved to `common` — it is shared vocabulary, not a transport implementation detail (ADR-P1-003a).
2. `logging -> diagnostics`: a separate `logging` area needed diagnostic types, creating a sideways edge. The logging seam folded into `diagnostics`, where it belongs (ADR-P1-003b).
3. `state <-> capability/device/audio`: `DeviceState` lives in the state area but composes capability, device and audio, which is a cycle. `DeviceState` moved to a new `session` area above all three (ADR-P1-003c).
**Consequences.** The direction rule is now mechanically enforced by `coreAreasDependOnlyOnMoreFoundationalAreas`, and `everyCoreAreaIsRegisteredInTheLayerMap` forces a new area to be placed deliberately rather than absorbed silently.

### ADR-P1-004 — Three outcomes, thirteen-plus causes
**Status.** accepted
**Context.** Prompt section 23 lists success, failure, cancellation, timeout, unsupported, unknown and disconnected, and warns against a massive abstraction.
**Decision.** `OperationOutcome<T>` has exactly three cases: `Success`, `Failure(OmniBudsError)`, `Cancelled`. Timeout, unsupported, disconnected, protocol mismatch and codec unavailability are `OmniBudsErrorCategory` values inside `Failure`, not separate outcome shapes. Unknown is not an outcome at all: it is a property of the value, so a read that could not determine a measurement returns `Success` with that field unknown.
**Consequences.** Call sites handle three cases with the compiler's help; retry policy is derived from the error category, so a caller cannot accidentally decide that a timed-out write is retryable (ADR-P1-006).

### ADR-P1-005 — Codec state as an ordinal ladder, `configurable` as an attribute
**Status.** accepted — amends ADR-P0-015
**Context.** Prompt section 17 proposes six booleans and invites a better design. Six booleans permit nonsense such as `active = true, supported = false`, and the prompt's five names omit `CONFIGURABLE` entirely (the conflict Phase 0 recorded).
**Decision.** `CodecState` is an ordered ladder `UNKNOWN < UNSUPPORTED < SUPPORTED < AVAILABLE < ENABLED < NEGOTIATED < ACTIVE`, with `UNKNOWN` and `UNSUPPORTED` added below the five positive rungs because `audio-governance.md` AUD-STATE-005 requires an unknown codec state. `configurable` becomes a separate boolean because it is orthogonal: a codec can be `ACTIVE` *and* configurable, which no single-valued ladder can express. The prompt's "selected" is the `ENABLED` rung.
**Consequences.** An illegal codec claim cannot be constructed; "LDAC enabled while AAC is active" is two records that each tell the truth. The cost is that `CONFIGURABLE` is no longer an enum member, so any Phase 0 text implying it is one is read as referring to the attribute.

### ADR-P1-006 — Error categories are the union of both sources
**Status.** accepted — amends ADR-P0-012
**Context.** Phase 0 fixed thirteen categories; prompt section 22 adds `ReadFailed`, `UnknownDevice`, `InvalidState` while omitting `GattFailure`, `RfcommFailure`, `FirmwareMismatch`.
**Decision.** All sixteen are canonical. Nothing was dropped, and `retryClass` plus `invalidatesSession` are declared per category rather than chosen at call sites: reads are `SAFE_TO_RETRY`, a timed-out write is `RETRY_AFTER_REREAD`, a rejected or unknown-effect write is `NEVER_RETRY`.
**Consequences.** `TransportKind`-shaped specificity survives (RFCOMM and GATT failures stay distinguishable), and the write-retry prohibition is a property of the data, not a coding habit.

### ADR-P1-007 — One narrow protocol contract, optional capability interfaces
**Status.** accepted
**Context.** Prompt section 26 lists `identify`, `discoverCapabilities`, `readState`, `readBattery`, `readFirmware`, `setANC`, `setTransparency`, `readEQ`, `writeEQ`, `readGestures`, `writeGestures`. Master section 9 says do not force every vendor to implement every method.
**Decision.** `EarbudProtocol` declares only `identify`, `discoverCapabilities` and `readState`. Everything else is a separate optional interface a protocol may or may not implement: `FeatureReadSupport`, `FeatureWriteSupport`, `BatteryReportingSupport`, `FirmwareReportingSupport`, `AudioStateReportingSupport`. Feature reads and writes are expressed generically over `FeatureId` and `ConfigurationValue`.
**Consequences.** A vendor without gesture support simply does not implement the interface, instead of returning a fake success. ANC, EQ and gesture *semantics* are deliberately not modelled yet: inventing their value shapes in Phase 1 would be fabricating hardware behavior, so they travel as `ConfigurationValue` until the phases that own them define them (master section 12, ADR-P0-001).

### ADR-P1-008 — Feature identity is namespaced text, not a brand conditional
**Status.** accepted — amends Phase 0 `specs.md` section 1.5 examples
**Context.** Prompt section 20 wants features addressed generically; Phase 0 examples used camelCase namespaces (`noiseControl.anc`).
**Decision.** `FeatureId` is an validated, lower-kebab, dotted identifier. Core features sit under functional namespaces (`noise-control.anc`), vendor features under `vendor.<vendor>.<feature>`, and `VendorExtension` refuses construction unless the identifier really is a vendor extension and its vendor segment matches its metadata.
**Consequences.** `if (sony)` logic has nowhere to live. The `FeatureId` string is a stable contract: renaming one is a breaking change needing an ADR, because Phase 22 will key protocol records on it.

### ADR-P1-009 — Manual constructor injection, no framework, no ambient registry
**Status.** accepted
**Context.** Prompt section 28 requires explicit dependencies, injectable platform services, domain testability without Android, and no framework chosen because it is popular.
**Decision.** Dependencies are constructor parameters. No DI library is added in Phase 1. `:core` exposes contracts only, so it needs no container; the composition root arrives with the first Android code in Phase 2, inside `:platform:android`. No mutable singleton is introduced: `ProtocolRegistry.empty()` is an immutable value, and state is passed as values.
**Consequences.** Swapping a transport or protocol for a test double is ordinary code, satisfying the testability requirement with zero dependencies. If a container is later adopted it is an ADR of its own, and nothing in `:core` will need to change.

### ADR-P1-010 — Persistence is contracts; serialization is deferred deliberately
**Status.** accepted
**Context.** Prompt sections 25 and 38 ask for repository abstractions and a serialization choice "where models need serialization for future storage".
**Decision.** Phase 1 ships `DeviceRepository`, `CapabilityRepository` and `ProtocolRecordAccess` as suspend-function interfaces with structured results, and chooses no storage engine and no serialization library. The forward constraints are recorded now so the later choice is not arbitrary: stable field names, an explicit version field, unknown-safe decoding, no Android objects ever serialised, and the user device database kept separate from the protocol database (master section 28).
**Consequences.** No dependency weight in the foundation phase; the serialization decision lands in Phase 17 or 22 with real usage to justify it, rather than being guessed now. `SavedDeviceRecord` holds a fingerprint-derived `identityKey` rather than a MAC address, per identifier minimisation.

### ADR-P1-011 — Quality baseline is the compiler plus dependency-free architecture tests
**Status.** accepted
**Context.** Prompt section 35 asks for an appropriate baseline and warns against stacking overlapping tools. The user also requires that this project not disturb other projects' toolchains on this workstation.
**Decision.** `allWarningsAsErrors = true` in both modules, an `.editorconfig`-style convention set documented in `specs.md`, and `DependencyDirectionTest` — a source-scan architecture test written with the standard library only. No detekt, no ktlint, no Spotless, no MockK, no Android lint gate wired into `check` yet.
**Consequences.** The rules that matter most in Phase 1 (no Android in core, no Bluetooth identifiers, no stubs, no doubles in production code, no magic hex or UUIDs, upward imports forbidden) are enforced by something that runs on every build, at the cost of zero additional dependencies. A broader linter remains a later, opt-in ADR.

### ADR-P1-012 — Epoch milliseconds, nullable, for all time
**Status.** accepted
**Context.** Sessions and states need creation and last-update times; `java.time` is JVM-only and `kotlin.time.Instant` needs experimental opt-in in this Kotlin version.
**Decision.** Timestamps are `Long?` epoch milliseconds. `null` means genuinely unobserved, which is distinct from `0`. Monotonic ordering inside a process is expressed by `DeviceState.revision`, not by wall-clock comparison.
**Consequences.** `:core` stays free of `java.*` — which `DependencyDirectionTest` now enforces — and KMP conversion in Phase 46 does not require a time abstraction rewrite.

### ADR-P1-013 — Test doubles are test-only, and "nothing is implemented" is asserted
**Status.** accepted
**Context.** Prompt sections 26, 27 and 53 forbid fakes that could be mistaken for hardware support, while section 36 requires real tests.
**Decision.** Every double (`FakeDeviceRepository`, `FakeCapabilityRepository`, `FakeProtocolRecordAccess`, `ScriptedOutcome`) lives in `core/src/test`, defaults to empty or unscripted, and refuses to invent success: an unscripted queue raises rather than returning a happy default, and a scripted failure is never swallowed. `ProtocolRegistry` ships empty, with a test asserting emptiness, and no `src/main` class implements a protocol, transport or repository contract.
**Consequences.** Phase 1's central claim — that nothing here talks to hardware — is machine-checked from three directions (registry emptiness, no production implementations, no doubles in main source).

### ADR-P1-014 — Toolchain pinned from what this workstation already provides
**Status.** accepted
**Context.** Neither `java` nor `gradle` was on `PATH`. A JDK 17 exists at the user-level `AppData\Local\jdk-17`, Gradle 8.9 and 9.2.0 distributions and AGP 8.7.3/8.5.2 plus Kotlin 2.0.21 artifacts are already in the shared Gradle cache, and the Android SDK has platform 35 with build-tools 35.
**Decision.** Pin Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, compileSdk 35, JVM target 17, all in `gradle/libs.versions.toml`. Select the JDK with `JAVA_HOME` per invocation only, and the SDK with git-ignored `local.properties`. Do not write `org.gradle.java.home` into any user-level or shared Gradle file, and do not install, move or modify any JDK, Gradle distribution, SDK component or IDE runtime on this machine.
**Consequences.** The build is reproducible and verified against real artifacts rather than assumed, and other projects' toolchains are untouched. Recorded honestly: no `.gitignore`d file was committed, and the only writes outside the project directory are Gradle's own additive dependency-cache entries.

### ADR-P1-015 — minSdk 26 is provisional and expires at Phase 2
**Status.** accepted — revisit required
**Context.** The Android library module needs a `minSdk`, but Phase 1 contains no Android code and the Bluetooth runtime-permission model changed at API 31.
**Decision.** Set 26 so the foundation is not artificially narrowed, and record that Phase 2 must re-decide it against the real permission and background-execution requirements, with the reason written into that phase's security section.
**Consequences.** A number needed to compile was chosen explicitly and flagged, rather than copied from a template and defended later.

### ADR-P1-016 — ConnectionState plus SessionClassification supersede Phase 0's SessionState
**Status.** accepted — amends Phase 0 `specs.md` section 2.3
**Context.** Prompt section 12 specifies an eleven-state `ConnectionState`; Phase 0 `specs.md` section 2.3 listed a six-value `SessionState` mixing connection status with saved/temporary status.
**Decision.** Two orthogonal types: `ConnectionState` for where the device has got to (transport and discovery progress), `SessionClassification` for whether the user saved it. `DeviceSession` carries both. `CONNECTING`, `ACTIVE_SESSION`, `STALE` and `FORGETTABLE` are expressed by combinations rather than a single enum.
**Consequences.** The "is it merely stale?" question becomes a derivation instead of a second source of truth — which is exactly the failure prompt section 24 forbids. `TEMPORARILY_UNAVAILABLE` from the prompt's list is retained.

### ADR-P1-017 — Commit scopes `build` and `deps` are added
**Status.** accepted — amends Phase 0 `git-workflow.md`
**Context.** Phase 0 enumerated scopes `core, bluetooth, transport, protocol, capability, audio, persistence, ui, docs, test`; Phase 1's real work was build-infrastructure commits, and the prompt's own example used `docs(phase-0)`.
**Decision.** Add `build` (Gradle, wrapper, catalog, modules) and `deps` (dependency additions with a recorded reason), and accept a phase scope such as `docs(phase-0)`.
**Consequences.** Commit history reads meaningfully without inventing a scope per commit; the earlier `phase-0` commits were retro-labelled rather than rewritten, since history is not amended after the fact.

### ADR-P1-018 — Endpoint-differentiated codec support is an open model gap
**Status.** **open — deferred to Phase 11**
**Context.** `audio-governance.md` AUD-STATE-003 requires a separate SUPPORTED record per endpoint (phone and headset), and prompt section 15's own LDAC example is exactly that case. Phase 1's `CodecCapability` holds one record per codec.
**Decision.** Accepted as a known limitation rather than papered over: Phase 1 does not model per-endpoint codec support. The ladder still encodes the weakest link correctly, but the UI cannot yet say "the phone supports LDAC, this headset does not".
**Consequences.** Named explicitly in `validation.md` as deferred, with Phase 11 owning the fix. A future per-endpoint discriminator must not be smuggled in as a nullable field that reads as unknown.

### ADR-P1-019 — Phase 1 leaves the diagnostic redactor unimplemented
**Status.** accepted — gap recorded
**Context.** `SEC-LOG-002` requires redaction at the point of emission. A `DiagnosticEvent.redacted()` helper was considered and rejected as unsound: honest masking depends on which sink is receiving the event and on knowing which fields carry identifiers, neither of which a data class can determine.
**Decision.** Ship no partial redactor. `DiagnosticEvent.message` is documented as pre-redaction text; redaction is an obligation of the sink built in Phase 36, and `DiagnosticSeverity.PACKET` and `TRACE` carry `requiresOptIn = true` so a sink cannot enable them by accident.
**Consequences.** The privacy rule is enforced by the opt-in flag and the documented contract rather than by a half-working function that would create false confidence.
