# Phase 7 — Validation

**Phase:** 7 — Protocol Abstraction Engine · **Owner:** orchestrator
**Code commit:** `d1fe1a0` — `feat(core): give the protocol engine its lifecycle, resolver and runtime
contracts` (10 files, +1,139; 5 new `src/main` protocol types + 5 new `src/test` files).
**Full gate command:** `./gradlew --offline :core:test :platform:android:test
:platform:android:compileDebugAndroidTestKotlin :platform:android:lintDebug --rerun-tasks`
**Gate result:** `BUILD SUCCESSFUL`. Core JVM **580 tests, 0 failures** (Phase 7 added **31**);
`:platform:android` unit **102 per variant, 0 failures** (Phase 7 added no platform code — it is a `:core`
phase); `compileDebugAndroidTestKotlin` and `lintDebug` clean; 17 architecture checks green;
`allWarningsAsErrors` clean.
**Environment header (TST-MOCK-006):** MOCKED ENVIRONMENT. No radio, no handset, no vendor protocol.

---

## 1. Acceptance criteria (prompt §20), each with the evidence that holds it

- [x] **Previous phase contracts respected.** Reuse, not duplication (ADR-P7-001): `ProtocolDefinition`,
  `ProtocolRegistry`, `CommandDefinition`/`EffectClass`/`ParsedResponse`, the capability interfaces and
  `EarbudProtocol` are unchanged; new types import them downward (layer map green).
- [x] **Core protocol interface exists.** `ProtocolSession` (+ `ProtocolTransportAdapter`) is the runtime
  contract; `EarbudProtocol` stays the stateless family (ADR-P7-002).
- [x] **Protocol descriptors structured.** `ProtocolDefinition` with `init` validation; no new descriptor.
- [x] **Protocol registry exists.** `ProtocolRegistry` (Phase 1) — immutable, duplicate-rejecting, empty;
  reused, not rebuilt.
- [x] **Protocol resolution is deterministic.** `RegistryProtocolResolver`; name-independent candidate order
  asserted (`ProtocolResolverTest.resolutionOrderIsDeterministic...`).
- [x] **Unknown and ambiguous handled safely.** Six distinct outcomes; ambiguity preserved with `selected = null`;
  empty registry ⇒ `UNKNOWN` (`ProtocolResolverTest`, `PhaseSevenScopeTest`).
- [x] **Protocol lifecycle explicit.** `ProtocolState` + transitions; READY only via INITIALIZING; CLOSED
  terminal (`ProtocolStateTest`, `ProtocolSessionTest`).
- [x] **Command and response contracts exist.** `ProtocolCommand`/`ProtocolResponse` with correlation, bounds,
  array equality (`ProtocolCommandTest`).
- [x] **Error mapping structured.** Reuses `OperationOutcome<OmniBudsError>`; no new category; malformed ⇒
  failure not crash.
- [x] **Retry safety defined.** One-shot `execute`; a timeout never re-sends; `EffectClass` asymmetry holds
  (`aCommandIsSentExactlyOnceEvenWhenTheReplyFails`).
- [x] **Vendor extension architecture exists.** `VendorExtension`/namespaced `FeatureId` reused; unknown ⇒
  unknown; no vendor feature implemented (ADR-P7-009).
- [x] **Verification levels explicit.** The master `VerificationLevel` ladder; §8 names documented as aliases,
  no fork (ADR-P7-003, `specs.md` §2).
- [x] **No invented vendor commands.** Registry empty; a source scan forbids a production session/adapter and
  any framework import (`PhaseSevenScopeTest`, `theShippedRegistryHoldsNoProtocol`).
- [x] **No false hardware support claimed.** Every capability `IMPLEMENTED` at most; device verification
  `NOT RUN`.
- [x] **Core remains platform-independent.** No `android.*` in `core.protocol` (scanned); layer map green.
- [x] **Automated tests pass.** 580 core + 102 android, 0 failures.
- [x] **Build and static analysis pass.** `BUILD SUCCESSFUL`, `lintDebug` clean, `-Werror` clean.
- [x] **All mandatory documentation exists.** `requirements, design, specs, task-list, test-plan, validation,
  decisions (ADR-P7-001…010), risk-register (RISK-110…121)` + `architecture-audit` + staged `execution-prompt`.
- [x] **Physical-device verification marked deferred.** §3.
- [x] **Git diff contains only authorized changes.** `git show --stat d1fe1a0` is 10 Phase 7 paths; the
  concurrent `tools/device-bridge/**` workstream was never staged.

## 2. Claims and their honest ceiling

Phase 7's framework — lifecycle, session contract, resolver, command/response, events, transport boundary — is
`IMPLEMENTED`. **No protocol is registered; no session is instantiated against a device; no command was ever
sent; `discoverCapabilities` is not reached** (capability discovery is Phase 8). The empty registry means the
correct resolution for every real device today is `UNKNOWN`, and that is the phase's condition, recorded rather
than papered over. `HARDWARE_VERIFIED`/`PERSISTENCE_VERIFIED` are unreachable and unclaimed.

## 3. Deferred physical-device verification (`NOT RUN`, standing directive)

Confirming on a real device that: a resolved protocol initializes to `READY`; a command's reply correlates;
a notification/indication reaches an event collector; a timeout returns without re-sending; a transport drop
moves the session to `DEGRADED`/`FAILED`. All accumulate on the project-wide deferred-device list for the end of
the project (ADR-P3-014) and gate no boundary.

## 4. Findings closed inside the phase

- **Two prompt/tree vocabulary collisions surfaced, not silently obeyed.** §8's `RESEARCHED`/`AUTOMATED_TESTED`
  and §11's `UNKNOWN_SIDE_EFFECT` were reconciled onto the existing `VerificationLevel`/`EffectClass` in
  ADR-P7-003/004 rather than forked into parallel enums.
- **Two Phase 7 defects caught by tests before commit.** The transition table had a nonsense `FAILED →
  CREATING_NULL` edge and a `DEGRADED → READY` shortcut that violated the phase's own "READY only via
  INITIALIZING" invariant; both fixed after `ProtocolStateTest` and the scripted session exercised the machine.
- **A reused-result decision.** Prompt §6's `ProtocolResult<T>` was not added; results stay
  `OperationOutcome<T>` (the project's convention), avoiding a second result type (ADR-P7-001's spirit).

## 5. Outstanding risks

RISK-110…121. The ones that stay genuinely open: **RISK-110/111** (a resolution must never be read as support
or chosen by a brand — the guards tripwire it, the enforcement lives in later phases), and **RISK-121**
(nothing is hardware-verified until the deferred device session). The empty registry (RISK-112) is a designed
state, and its tests make a quiet departure a build failure.

## 6. Phase 8 readiness

Phase 8 (capability discovery) is handed: a resolved-candidate pipeline (`ProtocolResolution`), a session
lifecycle with an explicit `READY`, structured read/write command contracts, and `EarbudProtocol`'s already
declared `discoverCapabilities` kept separate from the session. A capability phase can drive reads through
`ProtocolSession`/the support interfaces without touching these boundaries. **Phase 8 is not authorized** and
waits for its own execution prompt (ADR-P0-009).
