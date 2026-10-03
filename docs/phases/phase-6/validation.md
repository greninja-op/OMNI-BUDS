# Phase 6 — Validation

**Phase:** 6 — Bluetooth Transport Layer · **Owner:** orchestrator
**Code commit:** `83740c5` — `feat(core,platform): give the transport boundaries a lifecycle, an operation
surface, and a mechanism` (21 files, +1,975 / −30; 13 new source/test files + 8 amended).
**Full gate command:** `./gradlew --offline :core:test :platform:android:test
:platform:android:compileDebugAndroidTestKotlin :platform:android:lintDebug --rerun-tasks`
**Gate result:** `BUILD SUCCESSFUL`. Core JVM **549 tests, 0 failures** (Phase 6 added **12**);
`:platform:android` unit **102 per variant, 0 failures** (Phase 6 added **12**); `compileDebugAndroidTestKotlin`
and `lintDebug` clean; 17 architecture checks green.
**Environment header (TST-MOCK-006):** MOCKED ENVIRONMENT. No radio, no handset, no network.

---

## 1. Acceptance criteria (prompt §19), each with the evidence that holds it

- [x] **Previous phase contracts respected.** Reuse, not rebuild (ADR-P6-001): `TransportContract`/
  `BluetoothTransport`/the five boundaries and the value types are extended in place; Phase 3's
  profile≠channel rule and Phase 5's "identification is advisory only" both hold — no transport imports a
  device/protocol type.
- [x] **Transport taxonomy explicit.** Physical link / profile / control channel / audio path kept distinct;
  `TransportKindPinningTest`.
- [x] **BLE/GATT abstractions exist.** `GattTransport` characteristic/discovery/notification/MTU surface
  (ADR-P6-003); `AndroidGattTransport` mechanism behind the seam.
- [x] **Classic/RFCOMM abstractions exist.** `RfcommTransport` stream surface with `RfcommEndpoint`
  (caller-supplied, no default); `AndroidRfcommTransport`.
- [x] **Audio profile observation architecturally separate.** Enforced by the layer map (transport L1 cannot
  import audio L2) and the guard's audio-path token ban (ADR-P6-009).
- [x] **Transport lifecycle documented.** `TransportState` + `TransportStateTransitions` + `state: StateFlow`
  (ADR-P6-002); `TransportStateTest` (6).
- [x] **Structured error mapping exists.** `TransportErrorMapping` onto existing categories, no new category,
  retry class inherited (ADR-P6-006).
- [x] **Cancellation and timeout behaviour defined.** `TIMEOUT` on unresolved connect; `withTimeoutOrNull` in
  the framework; `NonCancellable` teardown; clock via `TimeProvider` seam (ADR-P6-007/010).
- [x] **Resource ownership explicit.** One mutex, no queue, idempotent close, subscription cancelled on
  collector end; no leaked handle/socket/collector (ADR-P6-007/011).
- [x] **Concurrent operation behaviour tested.** Serialisation and the "refuse, don't queue" guard asserted
  by `AndroidTransportTest.operationOnAnIdleChannelIsRefusedNotQueued`, `repeatedCloseIsIdempotentSuccess`.
- [x] **Unknown transport behaviour is safe.** `UndeterminedTransportResolver` selects nothing; absence is
  `NO_EVIDENCE`, not unsupported (ADR-P6-005); `TransportResolverTest` (6).
- [x] **No unsupported transport claimed available.** `TransportBoundary` refuses `available` below
  `LAB_TESTED`; a resolution caps at `INFERRED`.
- [x] **No vendor-specific command introduced.** No framing, no opcode, no device UUID; `exchange` opaque.
- [x] **No audio processing introduced.** Nothing in transport touches media.
- [x] **Core remains platform-independent.** No `android.*`/audio/protocol import in `core.transport`;
  `DependencyDirectionTest`.
- [x] **Automated tests pass.** 549 core + 102 platform unit, 0 failures.
- [x] **Build and static analysis pass.** `BUILD SUCCESSFUL`, `lintDebug` clean, `-Werror` clean
  (the single documented `@Suppress("DEPRECATION")` is confined to `SystemGattTransportHandle.kt`).
- [x] **All mandatory documentation exists.** `requirements, design, specs, task-list, test-plan, validation,
  decisions (ADR-P6-001…012), risk-register (RISK-098…109)` + `architecture-audit` + staged `execution-prompt`.
- [x] **Physical-device verification marked deferred.** §3 below; `NOT RUN`, never skipped.
- [x] **Git diff contains only authorized changes.** `git show --stat 83740c5` is 21 Phase 6 paths; the
  concurrent `tools/device-bridge/**` workstream was never staged.

## 2. Claims and their honest ceiling (TST-REC/TST-MOCK-001)

Every Phase 6 capability caps at **`IMPLEMENTED`**. The transport *machinery* — lifecycle, operation
surface, resolver contract, concurrency discipline, and the Android mechanism — is implemented; **no channel
is proven against a device**, no `CONNECTED` was ever observed on real hardware, no protocol runs over any
channel, and no transport is claimed available for any specific earbud. `TransportResolution` selects
nothing by design. A successful `open()` means only that the platform confirmed a link; it is not evidence
of a usable control channel (prompt's closing principle), and nothing here lets a caller read it as such.

## 3. Deferred physical-device verification (standing directive; ADR-P3-014)

`NOT RUN` (not `SKIPPED`): that a real GATT connect resolves its callback to `CONNECTED`; that discovery and
characteristic read/write behave against a live attribute table; that notifications arrive and cancellation
unsubscribes; that MTU negotiation grants; that an RFCOMM socket connects by service UUID and streams; and
that each raw platform status maps to the category `TransportErrorMapping` names. All accumulate on the
project-wide deferred-device list for the end of the project and gate no boundary; each is a
`HARDWARE_VERIFIED` obligation (TST-HW-003).

## 4. Findings closed inside the phase

- **Inherited doc/code contradiction (ADR-P6-004).** `BleTransport` and Phase 2's boundaries doc claimed
  "`TransportKind` has no `BLE` constant" while the enum and `BleTransport`'s own body return it. Corrected
  in the open; the link-vs-control role decided (a BLE control channel is GATT).
- **Two guards legitimately superseded.** `TransportBoundariesTest`'s and `DependencyDirectionTest`'s
  forbidden-token lists moved client GATT/RFCOMM opens to the allowed side because Phase 6 owns the
  mechanism, while keeping scan/discovery/server-listen/audio/UI/pairing forbidden (ADR-P6-008/012). Amended
  with dated comments, not deleted — a subsequent reader sees the phase boundary that changed them.
- **Two real defects caught by the tests before commit.** `fail(category,…)` was building its error with a
  fixed category, discarding the caller's (surfaced by the timeout test); and `open()` moved
  `IDLE → CONNECTED` directly, which the machine — correctly — refused. Both fixed in `83740c5`; the state
  machine earned its keep on its first use.

## 5. Outstanding risks

RISK-098…109 in `risk-register.md`. The two that stay genuinely open for later phases are **RISK-098** (a
successful connect must never be read as protocol compatibility — enforcement of the *next* step belongs to
the capability/protocol phases) and **RISK-099/107** (the mechanism is compiled and seam-tested, never run;
the inherited guards were narrowed legitimately and must not be widened by accident).

## 6. Phase 7 readiness

Phase 6 hands Phase 7 (and the protocol phases) real, lifecycle-safe channels: `open/close/exchange`/
`probeAvailability` with a state machine that refuses premature `CONNECTED`, caller-addressed GATT/RFCOMM
operations carrying opaque bytes and no vendor assumptions, an error taxonomy that distinguishes retryable
from not, and a resolver contract whose shipped implementation decides nothing. A protocol layer can map
onto a `TransportKind` (Phase 7 onward) without editing these boundaries, because protocol semantics were
kept out of them. **Phase 7 is not authorized** and waits for its own execution prompt (ADR-P0-009).
