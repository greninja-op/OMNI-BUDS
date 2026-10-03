# Phase 6 — Architecture Audit

**Phase:** 6 — Bluetooth Transport Layer · **Owner:** orchestrator (Agent A role)
**Document status:** the pre-execution inspection that shaped ADR-P6-001 … 0nn, read out of the tree at
the Phase 6 start (`db9326a`, Phase 5's head). The headline finding is uncomfortable for a prompt that
opens with "Create or refine platform-independent interfaces": **most of Phase 6's interface vocabulary
already exists**, built by Phases 1 and 2 and left deliberately unimplemented with Phase 6 named as the
phase that fills it. The audit therefore spends most of its effort on *what not to duplicate* and on the
one inherited data contradiction that must be settled before code.

## 1. What already exists (Phase 1/2's transport layer — reuse, do not rebuild)

`core/src/main/kotlin/com/omnibuds/core/transport/` (layer 1) already holds a complete boundary
hierarchy, verified by `TransportBoundariesTest`:

| Type | Shape today | Phase 1/2 intent |
|---|---|---|
| `TransportContract` | `kind: TransportKind`, `isOpen`, `suspend open()/close()/exchange(req, timeout)` | the root control-channel contract; **no `:core` type may implement it** |
| `BluetoothTransport : TransportContract` | adds `suspend probeAvailability(): OperationOutcome<TransportAvailability>` | the Bluetooth-specific boundary; `open/close/exchange` left unimplemented **for Phase 6** |
| `GattTransport`, `RfcommTransport`, `ClassicTransport`, `BleTransport`, `LeAudioTransport` | each pins one `TransportKind`, adds nothing | deliberately thin markers; the concrete members are "handed to Phase 6 by OQ-PROTO-01" |
| `TransportAvailability` | `(kind, available, reason)` with the pair locked by `init` | the safe-unknown / no-silent-fallback primitive |
| `TransportBoundary` | `(kind, availability, supportEvidence, notes)`, refuses `available` below `LAB_TESTED` | one candidate row |
| `TransportNegotiation` | `(candidates, selected)` + `preferring(order)` + `isUndetermined` | **already the resolver's result type** — offers vs selection, no fallthrough |
| `TransportRequest` / `TransportResponse` | opaque byte payload, hand-written array equality, no-bytes `toString` | the `exchange` payload types |

`docs/phases/phase-2/transport-boundaries.md` §7 states the Phase 6 mandate in the repo's own words:
"Phase 6 … implements `open`/`close`/`exchange` and `probeAvailability` behind `:platform:android`, and
decides the `BleTransport`/`GattTransport` question this document leaves open." So the phase's centre of
gravity is (a) the **lifecycle state machine**, (b) the **deferred per-transport mechanics**, (c) the
**resolver contract** over the existing negotiation type, (d) the **error mapping and concurrency
ownership**, and (e) the **Android mechanism** — not a new interface tree.

`TransportManager`/factory from prompt §5, the resolver from §12, the state machine from §8, and the
GATT/RFCOMM members from §9/§10 are all named as *deferred to Phase 6* in Phase 2's cross-cutting list
(items 1–5). This phase is their owner.

## 2. The layer map already enforces the audio/control separation (prompt §11)

`DependencyDirectionTest` places `common/state` at L0, `transport/platform` at L1, and `audio/capability/
device/config/diagnostics` at L2, failing any import whose target layer ≥ source layer (own area exempt).
`core.transport` currently imports only `common`, `state`, and itself — verified. Consequence: a control
transport **cannot** import `com.omnibuds.core.audio`, so the media path is structurally out of reach of
the transport package, and `AudioTransportState`/`AudioTransportKind` (`CLASSIC_A2DP`, `HFP`, `LE_AUDIO`,
`UNKNOWN`) stay the only home for media-audio facts. Prompt §11's "keep audio transport separate from
control sessions" needs no new wall — Phase 6 must keep the edge absent and add a guard test that it stays
absent. The profile-vs-channel rule from ADR-P3-002 (a device linked over A2DP is **not** evidence a
control channel exists) already fixes the vocabulary split; no `TransportKind` for A2DP/AVRCP/HFP exists
or will be added.

## 3. Error categories are already sufficient — no new ones (prompt §13)

`OmniBudsErrorCategory` (extended by ADR-P2-004) already carries `TRANSPORT_UNAVAILABLE`, `GATT_FAILURE`,
`RFCOMM_FAILURE`, `TIMEOUT`, `DEVICE_DISCONNECTED`, `BLUETOOTH_DISABLED`, `PERMISSION_DENIED`,
`INVALID_STATE`, `UNSUPPORTED_OPERATION`, `CONNECTION_UNAVAILABLE`, `RESOURCE_UNAVAILABLE`,
`PROTOCOL_MISMATCH`, `READ_FAILED`, `WRITE_REJECTED`, `UNKNOWN_FAILURE`, and **each carries a retry
classification** (`RETRY_AFTER_REREAD` / `SAFE_TO_RETRY` / `NEVER_RETRY`). Prompt §13's "distinguish
retryable from non-retryable" and "do not treat timeout as proof of no effect" are therefore already
modelled — `TIMEOUT` is `RETRY_AFTER_REREAD` (re-read state, do not re-send), and the transport
documentation already states it never auto-retries a side-effecting write. Phase 6's job is the *mapping*
from platform exceptions to these categories, done in the platform, plus a domain `TransportState`/result
that never surfaces a raw Android exception. Following ADR-P3-006's precedent, Phase 6 adds **no new
error category** unless a real need is found, and records any exception it cannot classify.

## 4. The one inherited contradiction Phase 6 must settle before writing code

`TransportKind` **does** have a `BLE` member (added in Phase 2 so `BleTransport` would have a kind to
report — `TransportKind.kt` KDoc: "Phase 2 originally listed only GATT, which left BleTransport with
nothing to report as its kind"). Yet `BleTransport.kt`'s KDoc still asserts the opposite — "`TransportKind`
deliberately has no `BLE` constant" — while its body returns `TransportKind.BLE`, and Phase 2's
transport-boundaries §2.1 repeats the "no BLE constant" claim. So the enum and the two documents
contradict each other, exactly the "records assert what the tree no longer does" defect that recurs in
this project (RISK-039 lineage). Phase 6 opens a channel over BLE and must answer the question the doc
deferred: **is `BleTransport` a distinct control channel, or is a BLE control channel just GATT?** The
domain answer (ADR-P6-xxx): a control channel *over* BLE is a GATT service; `BleTransport` is retained as
the **link-layer availability boundary only** (no independent `open/exchange` semantics beyond GATT), and
the stale "no BLE constant" sentences are corrected, not silently deleted. This is recorded rather than
quietly rewritten.

## 5. Reuse decisions and the shape of the work

- **Domain (`core.transport`, L1):** add `TransportState` + a `TransportStateTransitions` object modelled
  on `ConnectionStateTransitions` (forward map, an unavoidable failure sink, idempotent self-transition),
  wired so a transport reports `CONNECTED` only after the platform confirms it (prompt §8). Give
  `BluetoothTransport` a `state: StateFlow<TransportState>` so the machine has an authoritative home.
  Add the deferred **capability-specific members** to `GattTransport` (service/characteristic/discriptor
  identity as data, read/write, subscribe/unsubscribe via Flow, MTU) and `RfcommTransport` (caller-supplied
  service UUID, byte-stream read/write — **no framing member**, per PROTO-ABST-006), each behind its own
  interface so no single oversized interface forces irrelevant methods (prompt §6). Add `TransportResolver`
  returning `TransportResolution` **over `TransportNegotiation`**, with a safe-unknown default and no
  manufacturer rule (prompt §12).
- **Platform (`:platform:android`, behind the seam):** real `BluetoothGatt`- and `BluetoothSocket`-backed
  transports implementing `open/close/exchange/probeAvailability`, callbacks confined to the adapter, one
  operation in flight via a `Mutex`, cancellation and `NonCancellable` cleanup, exceptions mapped to the
  existing categories, no auto-connect, no scan, no device UUID. Registered at the `di/OmniBudsBluetooth.kt`
  seam. **Written and seam-tested, never run on a handset** — the user's explicit "Domain + Android
  mechanism" choice, matching Phase 2/3 (ADR-P6-xxx records the ceiling: `IMPLEMENTED`, first device-verified
  use lands with the Phase 19 vendor device per `GattTransport`'s own KDoc).
- **Guards/tests:** scripted doubles in test source only; `PhaseSixScopeTest` asserts no operation beyond the
  authorized set is newly reachable, no transport auto-opens, the control package imports no audio/protocol,
  and test doubles stay out of `main`.

## 6. What this audit refuses to invent

No device-specific GATT service, characteristic UUID, RFCOMM channel number, or packet framing (prompt §9/§10,
PROTO-NOMAGIC-002). No codec/audio work (§11). No transport-selection order in `:core` (PROTO-XPORT-005,
`preferring` already enforces it). No new error category. No scanner. No automatic pairing/connect/write.
Every capability ceiling stays `IMPLEMENTED`: the strongest statement Phase 6 can make about any channel is
"a lifecycle-safe mechanism exists that would open it", and `TransportBoundary` already refuses to let that
be recorded as an *available* channel below `LAB_TESTED` — which is the same fabricated-support wall
ADR-P1-013 raised, and this phase does not take it down.
