# Phase 7 — Test Plan

**Phase:** 7 · **Owner:** orchestrator
**Document status:** the deterministic tests holding Phase 7's contracts. **31 new tests, all tier T1** —
pure-JVM, no radio, no scheduler except `runTest` for the scripted session. Scripted protocol/transport
doubles live in **test source only** and are never presented as support (prompt §16; ADR-P7-010). Mocked
environment (TST-MOCK-006). Physical-device protocol verification is deferred, `NOT RUN`, never `SKIPPED`
(TST-REC-005).

Total after Phase 7: **core 580** (was 549, +31), **`:platform:android` 102 per variant** (Phase 7 added no
platform code), `lintDebug` clean, `compileDebugAndroidTestKotlin` clean, architecture checks green.

## 1. Lifecycle — `ProtocolStateTest` (6) · REQ-006…008

| test | contract |
|---|---|
| `readyIsReachableOnlyThroughInitializing` | no `CREATED/RESOLVED → READY` edge (prompt §12) |
| `onlyReadyMayExecuteAndOnlyReadyOrDegradedMayRead` | operation gating by state |
| `failureAndCloseAreReachableFromEveryOperationalState` | sinks from any live state |
| `closedIsTerminalAndNotResurrectedByALateCallback` | `CLOSED` accepts no revival (§14) |
| `stayingPutIsAlwaysLegal` | self-transition idempotent |
| `degradedRecoversOnlyByReinitializingNotByClaimingReady` | the READY-invariant is global |

## 2. Resolution — `ProtocolResolverTest` (8) · REQ-009…012

| test | contract |
|---|---|
| `theShippedEmptyRegistryResolvesNothingToUnknown` | empty registry ⇒ UNKNOWN (not a fake support claim) |
| `thinEvidenceYieldsInsufficientNotUnknown` | absence-of-evidence ≠ no-match |
| `aSingleCompatibleCandidateResolvesToIt` | one candidate ⇒ RESOLVED (a candidate, not a verdict) |
| `severalCompatibleCandidatesStayAmbiguousAndSelectNone` | ambiguity preserved, no winner (ADR-P5-008 discipline) |
| `aCandidateWhoseTransportIsUnavailableIsUnsupportedNotChosenElsewhere` | no fallthrough to a substitute channel |
| `aVersionIncompatibleCandidateIsReportedAsIncompatible` | INCOMPATIBLE_VERSION distinct |
| `resolutionOrderIsDeterministicRegardlessOfRegistryInsertionOrder` | deterministic, name-independent ordering |
| `anEmptyFingerprintCandidateListIsNeverActionable` | thin input never actionable |

## 3. Commands & responses — `ProtocolCommandTest` (7) · REQ-013…015

| test | contract |
|---|---|
| `byteIdenticalCommandsAreEqualRegardlessOfArrayIdentity` | array-content equality |
| `aDifferentCorrelationIdMakesADifferentCommand` | correlation participates in identity |
| `aBlankCorrelationIdIsRefused` | unattributable replies prevented (§11) |
| `anOverLargePayloadIsRefusedAtConstruction` | payload bound (`MAX_PAYLOAD_BYTES`, §15) |
| `aZeroTimeoutIsRefusedButNullMeansUnbound` | null ≠ zero, never "forever" (ADR-P0-016) |
| `toStringReportsLengthNeverPayloadBytes` | SEC-LOG-004 |
| `responseDistinguishesAbsentFromEmptyPayload` | null vs empty payload |

## 4. Session mechanism — `ProtocolSessionTest` (6) · REQ-007, 008, 014, 016

Driven by a **test-only scripted session + adapter** (never shipped):

| test | contract |
|---|---|
| `aSessionIsReadyOnlyAfterInitializeSucceeds` | `RESOLVED→…→READY` only on init success |
| `aFailedInitializeLeavesTheSessionNotReady` | FAILED, never READY |
| `executeBeforeReadyIsRefusedAndSendsNothing` | not-ready ⇒ `INVALID_STATE`, zero transport calls |
| `aCommandIsSentExactlyOnceEvenWhenTheReplyFails` | a timeout is never auto-retried (ADR-P7-004) |
| `closeIsIdempotent` | repeated close succeeds; terminal |
| `eventsReachACollectorAttachedBeforeTheyAreEmitted` | bounded `replay=0` delivery to an attached collector |

## 5. Registry (reused, prompt §16 "Registry" group)

Phase 1's `ProtocolRegistryTest` already covers valid registration, duplicate rejection, unknown lookup and
the empty-registry guarantee; Phase 7's `PhaseSevenScopeTest.theShippedRegistryHoldsNoProtocol` re-asserts
the emptiness through the Phase 7 entry point (`candidatesFor` names nothing on the empty registry).
Deterministic lookup is covered by the resolver's ordering test.

## 6. Architecture & scope — `PhaseSevenScopeTest` (4) · REQ-002, 003, 012, 019

| test | contract |
|---|---|
| `noProductionSourceImplementsTheRuntimeProtocolInterfaces` | no `: ProtocolSession`/`: ProtocolTransportAdapter` in `src/main` (no fake hardware) |
| `theProtocolPackageImportsNoAndroidClass` | `:core` platform-independence; protocols depend on transport abstractions only (§16) |
| `resolutionAndRegistryCodeNeverOpenOrExchangeAChannel` | resolution connects and executes nothing (§10) |
| `theShippedRegistryHoldsNoProtocol` | the framework ships knowing no protocol (§17) |

## 7. Deferred physical-device verification (`NOT RUN`)

No real device answered. `HARDWARE_VERIFIED`/`PERSISTENCE_VERIFIED`, real command/response exchange, an actual
session reaching `READY` against a radio, and event delivery from a live device are all unexecuted and
accumulate on the project-wide deferred-device list for the end of the project (ADR-P3-014); they gate no
phase boundary and are never reported as working.
