# Phase 6 — Design

**Phase:** 6 · **Owner:** orchestrator
**Document status:** describes the tree at `83740c5`. ADRs are cited from `decisions.md` (ADR-P6-001 … 012)
by id, requirements from `requirements.md` (`OB-P6-REQ-001 … 022`).

## 1. What the phase is, honestly

Phase 6 turns the transport **boundaries** Phases 1/2 declared-but-left-empty into a working architecture:
a lifecycle machine, the deferred per-channel operation surface, a resolution contract whose only shipped
implementation decides nothing, the concurrency and error discipline, and a real Android mechanism behind a
framework-free seam. It is *written and seam-tested, never run on a radio* — the user chose "Domain +
Android mechanism," and the Phase 2/3 precedent is that the platform mechanism is genuine code compiled and
instrumented-tested but not executed on hardware. The strongest claim any channel makes is `IMPLEMENTED`.

The prompt's `DeviceTransport/TransportManager/TransportResolver` sketch maps onto what already exists
(ADR-P6-001): its `connect()/disconnect()` are the inherited `open()/close()`, its `state` is a new
`StateFlow<TransportState>` on `BluetoothTransport`, and its `TransportResolver` is a new contract over the
existing `TransportNegotiation`. No parallel tree.

## 2. The layering

```
core.transport (L1)                     platform/android/bluetooth/transport
─────────────────────                   ─────────────────────────────────────
TransportContract                       GattTransportHandle / RfcommTransportHandle   (seams, no android.*)
  └─ BluetoothTransport ─ state         AndroidGattTransport / AndroidRfcommTransport (logic over the seam)
       ├─ GattTransport   ─ mtu/discover/    └─ drives handle, owns StateFlow + Mutex
       │                    read/write/notify
       ├─ RfcommTransport  ─ endpoint/read/write
       ├─ Classic/Ble/LeAudio (availability boundaries)
     TransportState + Transitions       SystemGattTransportHandle / SystemRfcommTransportHandle
     TransportResolver + Resolution          └─ the ONLY files importing android.bluetooth.*
     GattAttributes / RfcommEndpoint    BluetoothTransportFactory (Context+device → channel; opens nothing)
```

`core.transport` imports only `common`/`state`/itself — verified by `TransportBoundariesTest` and the layer
map. Because transport is L1 and audio/protocol are L2, a control channel **cannot** import them: prompt
§11's audio/control separation is structural, not a convention (ADR-P6-009).

## 3. The lifecycle machine (ADR-P6-002)

`TransportState{UNAVAILABLE,IDLE,CONNECTING,CONNECTED,CLOSING,CLOSED,DISCONNECTED,FAILED,UNKNOWN}` with
`TransportStateTransitions` shaped like `ConnectionStateTransitions`: a forward map, `FAILED`/`DISCONNECTED`
reachable from every live state, and idempotent self-transitions. `BluetoothTransport.state: StateFlow<…>` is
the single authority; `isOpen` is a read-through (`state == CONNECTED`), so the two cannot disagree — the
same de-duplication Phase 1 applied to `DeviceSession`. The load-bearing rule: `CONNECTED` is reached only
from `CONNECTING` on the platform's own confirmation; there is no `IDLE → CONNECTED` edge, so a transport
cannot be reported connected before it is connected. A `CLOSED`/`UNAVAILABLE` channel is terminal — a stale
callback cannot resurrect it, which is prompt §14's "a closed transport accepts no new operation."

## 4. The operation surface, kept generic (ADR-P6-003)

The mechanics Phase 1 deferred to Phase 6 are added as **capability-specific members on the sub-interfaces**,
never one fat `DeviceTransport`:
- **GATT** — `discoverServices()`, `readCharacteristic(target)`, `writeCharacteristic(target, value,
  withResponse)`, `notifications(target): Flow`, `mtu`, all keyed by caller-supplied `GattUuid`/
  `GattCharacteristic`. Properties (`READ/WRITE/NOTIFY/…`) come from the device's declaration; a write to a
  non-writable attribute is refused before the handle is touched. `exchange` is honestly refused
  (`UNSUPPORTED_OPERATION`) because a GATT write needs an attribute the opaque request doesn't carry — the
  byte-stream `exchange` belongs to channels that are byte streams.
- **RFCOMM** — an `endpoint` (`RfcommEndpoint`, service UUID and/or channel, never a baked default),
  `read()`/`write()` of raw bytes, and a real `exchange` (write whole, read one run). No framing member: the
  socket has no message boundaries and inventing one would state a protocol the layer hasn't defined
  (PROTO-ABST-006). A channel-number-only endpoint is refused at the platform, because Android exposes no
  public client API to dial a bare channel — a limitation stated, not hidden.

Every identifier is data the caller supplies; nothing in `:core` holds a vendor UUID (PROTO-NOMAGIC-002).

## 5. Concurrency and ownership (ADR-P6-007)

Each transport serialises operations under one `Mutex`, checking the state machine inside the lock so a
caller cannot interleave a write with a teardown. There is no work queue — a not-`CONNECTED` channel
*refuses* rather than buffers (bounded by construction). Teardown runs `NonCancellable`; `close()` is
idempotent; a notification collector releases its platform registration when the collector ends
(`awaitClose { cancelSubscription }`), so a dropped consumer cannot leak the CCCD write. There is no
auto-reconnect: a drop becomes `DISCONNECTED`/`FAILED` and reopening is an explicit `open()` by the owner
(§16 forbids automatic connection). `:core` reads no clock; the framework adapter owns real deadlines
(ADR-P6-010).

## 6. Errors (ADR-P6-006)

No new category. `TransportErrorMapping` turns a raw GATT/socket status into an existing
`OmniBudsErrorCategory` (`GATT_FAILURE`/`RFCOMM_FAILURE`/`PERMISSION_DENIED`/`TIMEOUT`/
`RESOURCE_UNAVAILABLE`/`WRITE_REJECTED`/`INVALID_STATE`), each of which already carries its retry class, so
"retryable vs not" and "a timeout never means no-effect" are inherited facts, not re-decided per call site.
A raw framework exception is caught at the `System*Handle` boundary and surfaced as a typed failure;
`OperationOutcome<…>` is all that crosses into `:core` (prompt §13).

## 7. Resolution (ADR-P6-005)

`TransportResolver.resolve(candidates): TransportResolution` composes the existing `TransportNegotiation`;
its only implementation, `UndeterminedTransportResolver`, classifies candidates into `SINGLE_CANDIDATE` /
`CANDIDATES_AMBIGUOUS` / `NO_CANDIDATE_AVAILABLE` / `NO_EVIDENCE` and **selects nothing** (`selected = null`,
enforced in `TransportResolution.init`). It reads transport-layer data only — `transport` cannot import a
`device` type (L1↛L2), so the caller supplies the candidate boundaries it built from whatever evidence it
holds; this is prompt §12's "adapt to actual models" applied against the layer map. No probe, no scan, no
order, no manufacturer rule.

## 8. The Android mechanism (ADR-P6-008)

`SystemGattTransportHandle` bridges `BluetoothGattCallback` to coroutine continuations
(`CompletableDeferred`, `withTimeoutOrNull`, a per-characteristic pending map so notification sinks stay
distinct) and `SystemRfcommTransportHandle` runs the blocking socket on `Dispatchers.IO`. These two files
are the only ones importing `android.bluetooth.*`; the `@SuppressLint("MissingPermission")` and a documented
`@Suppress("DEPRECATION")` (the pre-API-33 characteristic value API, unavoidable at minSdk 26) live here and
nowhere else. `BluetoothTransportFactory` builds a channel from a `Context` + `BluetoothDevice` +
`RfcommEndpoint` and **opens nothing** — construction allocates no socket and no GATT client. This is a
factory, not a licence to auto-connect.

## 9. What the design refuses to be

Not a scanner or discovery engine (ADR-P6-012). Not a protocol layer (no framing, no opcodes, no device
UUIDs). Not an audio path (structurally walled off). Not proof that any device is reachable — the ceiling is
`IMPLEMENTED` and the first real `CONNECTED` belongs to the deferred device session. Not a selection policy:
the resolver that ships decides nothing on purpose.
