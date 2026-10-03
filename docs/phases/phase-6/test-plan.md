# Phase 6 — Test Plan

**Phase:** 6 · **Owner:** orchestrator
**Document status:** the deterministic tests holding Phase 6's contracts. **24 new tests** (12 core, 12
platform), all tier **T1** — the transport logic runs on a JVM against scripted handles, so it needs no
radio. Physical-device transport verification is **deferred** (ADR-P3-014) and recorded `NOT RUN`, never
`SKIPPED` (TST-REC-005). Mocked environment only (TST-MOCK-006): every handle is a fake that answers;
nothing here claims a device was reached.

Total after Phase 6: **core 549** (was 537, +12), **`:platform:android` 102 per variant** (was 90, +12),
`lintDebug` clean, `compileDebugAndroidTestKotlin` clean, 17 architecture checks green.

## 1. Lifecycle machine — `TransportStateTest` (6) · REQ-004, REQ-005

| test | contract |
|---|---|
| `aFreshChannelRestsIdleAndCannotExchange` | only `CONNECTED` may carry an operation |
| `connectingToConnectedIsTheOnlyWayIn` | no `IDLE → CONNECTED` edge (prompt §8) |
| `everyLiveStateCanFallIntoFailureOrDrop` | `FAILED`/`DISCONNECTED` are universal sinks |
| `aClosedChannelDoesNotReopenItself` | `CLOSED` is terminal; only a fresh channel resets it |
| `stayingPutIsAlwaysLegal` | self-transition idempotent for every state |
| `unavailableIsTerminalButReobservable` | an unavailable channel refuses work, allows re-check |

## 2. Resolution — `TransportResolverTest` (6) · REQ-014…016

| test | contract |
|---|---|
| `noCandidatesIsNoEvidenceAndCarriesNoRefusal` | absence ≠ refusal (ADR-P0-016) |
| `allRefusedIsNoCandidateAvailableWithAReason` | a refusal carries its category |
| `exactlyOneUsableCandidateIsReportedButNotSelected` | the resolver never picks (ADR-P6-005) |
| `severalUsableCandidatesStayAmbiguousWithoutAnOrder` | ambiguity preserved, no ordering |
| `aResolutionCannotBeBuiltThatSelectsAChannel` | constructor rejects a non-null `selected` |
| `resolutionConfidenceIsCappedAtInferred` | nothing opened ⇒ nothing above `INFERRED` |

## 3. Transport mechanism — `AndroidTransportTest` (12) · REQ-004…012, REQ-017…020

| test | contract |
|---|---|
| `openDrivesTheStateMachineToConnected` | `open()` → `IDLE→CONNECTING→CONNECTED` on confirmation |
| `aRefusedConnectLandsOnFailedNotConnected` | a refused connect is `FAILED`, never `CONNECTED` |
| `anUnresolvedConnectIsTimeoutNotSilentSuccess` | an unanswered callback is `TIMEOUT` (prompt §8) |
| `operationOnAnIdleChannelIsRefusedNotQueued` | a not-`CONNECTED` channel refuses (prompt §14) |
| `gattExchangeIsRefusedBecauseGattIsAddressedPerCharacteristic` | no opaque command path on GATT (§6/§9) |
| `writeExceedingTheNegotiatedMtuIsRefusedNotFragmented` | MTU bound enforced, no transport framing (§9) |
| `writeToANonWritableCharacteristicIsRejectedBeforeTouchingTheHandle` | properties from the device are honoured |
| `discoveringServicesReturnsWhatThePlatformReported` | discovery returns caller-neutral data |
| `cancellingANotificationSubscriptionReleasesThePlatformRegistration` | collector end unsubscribes (ADR-P6-011) |
| `repeatedCloseIsIdempotentSuccess` | `close()` twice is a success (§8 repeated-op) |
| `rfcommExchangeWritesWholeThenReadsOneRun` | stream `exchange`, no framing (§10) |
| `rfcommWriteFailureMapsToRfcommCategory` | platform failure → `RFCOMM_FAILURE`, state → `FAILED` (§13) |

## 4. Inherited guards amended, not bypassed

- `TransportBoundariesTest.noPhaseTwoTransportCodeOpensOrProbesAChannel` was narrowed: `connectGatt`,
  `BluetoothGatt`, `writeCharacteristic` are no longer forbidden tokens because **Phase 6 owns the client
  mechanism** (ADR-P6-003); framework symbols stay confined to `System*TransportHandle` (verified by the
  `probeAvailability` call-site rule and the remaining token list). Amended at `83740c5`, not deleted.
- `DependencyDirectionTest.platformModuleContainsNoUnauthorisedCapabilities` moved client-transport opens
  onto the allowed side while **keeping `startScan`/`startDiscovery`/`BluetoothLeScanner`/`listenUsingRfcomm`/
  audio-path/UI forbidden** (ADR-P6-012). Amended at `83740c5`.
- `TransportKindPinningTest` doubles gained minimal bodies for the new interface members; still asserts each
  boundary pins exactly one kind.

## 5. Architecture group (prompt §18)

- **Core platform-independence:** no `core.transport` file imports `android.*`, `core.audio`, `core.protocol`
  or `core.persistence` — the layer map makes the wrong edges uncompilable; `TransportBoundariesTest` scans
  the package.
- **Vendor-protocol code depends on no socket class:** none exists (no protocol over transport yet).
- **Test doubles are test-only:** the scripted handles are in `src/test`; no fake transport in `src/main`.
- **No production fake hardware behaviour:** the `System*Handle` files call real framework APIs and are never
  executed here; the JVM tests drive fakes.

## 6. Deferred physical-device verification (`NOT RUN`)

Confirming on a handset that: a GATT connect resolves the callback and reaches `CONNECTED`; discovery returns
real services; a notification survives and its cancellation unsubscribes; the MTU negotiation grants; an
RFCOMM socket connects by service UUID and reads/writes; each maps a real platform status to the category
this table names. All are `NOT RUN` and gate no phase boundary (ADR-P3-014); each is a `HARDWARE_VERIFIED`
obligation for the deferred device session (TST-HW-003).
