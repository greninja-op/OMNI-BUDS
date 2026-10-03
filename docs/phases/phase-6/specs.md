# Phase 6 — Specs

**Phase:** 6 · **Owner:** orchestrator
**Document status:** normative contracts for the tree at `83740c5`. Where a contract is enforced by a
constructor, the layer map, or a test, that is named. Cites `decisions.md` (ADR-P6-001 … 012).

## 1. Transport taxonomy (prompt §7; ADR-P6-002/004/009)

| Member | Category | Control channel? | Opened by |
|---|---|---|---|
| `TransportKind.BLE` | physical link | availability only (`BleTransport`); a BLE control service *is* GATT | — (no scan this phase) |
| `TransportKind.GATT` | attribute protocol | yes | `TRANSPORT_GATT_OPEN` (6) |
| `TransportKind.RFCOMM` | SPP byte stream | yes | `TRANSPORT_RFCOMM_OPEN` (6) |
| `TransportKind.CLASSIC_BLUETOOTH` | other classic (SDP/L2CAP/vendor) | boundary only | 6 at earliest |
| `TransportKind.LE_AUDIO` | isochronous control | boundary only (empty) | 10 inspection |
| `TransportKind.VENDOR_SPECIFIC` | mechanism kind | no mechanism behind it yet | 20/23 |
| `TransportKind.UNKNOWN` | not established | never reported as usable | — |

A2DP, AVRCP, HFP have **no** `TransportKind` — they are media/call profiles modelled in `audio/` as
`AudioTransportKind`, never control channels OmniBuds opens (ADR-P3-002). `profile-connection ≠ channel-
availability`.

## 2. Lifecycle state machine (prompt §8; ADR-P6-002)

`TransportState{UNAVAILABLE, IDLE, CONNECTING, CONNECTED, CLOSING, CLOSED, DISCONNECTED, FAILED, UNKNOWN}`.
`TransportStateTransitions`:

| From | Legal successors |
|---|---|
| `UNKNOWN` | UNAVAILABLE, IDLE, CONNECTING, CONNECTED |
| `UNAVAILABLE` | IDLE (re-check); terminal for operations |
| `IDLE` | CONNECTING, UNAVAILABLE, CLOSED |
| `CONNECTING` | CONNECTED, CLOSING (+ FAILED/DISCONNECTED sinks) |
| `CONNECTED` | CLOSING, DISCONNECTED, IDLE, UNKNOWN (+ FAILED) |
| `CLOSING` | CLOSED, DISCONNECTED, IDLE, UNKNOWN (+ FAILED) |
| `DISCONNECTED` | CONNECTING, IDLE, CLOSED, UNKNOWN |
| `CLOSED` | UNKNOWN; **terminal** — no `→ CONNECTING` |
| `FAILED` | CONNECTING, IDLE, CLOSING, CLOSED, UNKNOWN |

`allowedNext` always adds `FAILED`, `DISCONNECTED`, and self (idempotent). `canExchange(s) == (s ==
CONNECTED)`; `isTerminal ∈ {CLOSED, UNAVAILABLE}`. **There is no `IDLE → CONNECTED` edge** — `CONNECTED` is
reachable only from `CONNECTING`, i.e. only on the platform's confirmation. `state: StateFlow<TransportState>`
is the single authority; `isOpen` is `state == CONNECTED`, stored nowhere else.

## 3. GATT contract (`GattTransport`; prompt §9; ADR-P6-003)

```
val mtu: MtuInfo?                                        // usablePayloadBytes = negotiated − 3
suspend fun discoverServices(): Outcome<List<GattService>>
suspend fun readCharacteristic(target): Outcome<ByteArray>
suspend fun writeCharacteristic(target, value, withResponse): Outcome<Unit>
fun notifications(target): Flow<Outcome<ByteArray>>      // cold; awaitClose unsubscribes
suspend fun exchange(...): Outcome<TransportResponse>    // → UNSUPPORTED_OPERATION (GATT is per-attribute)
```
Value types: `GattUuid(value: String)` (non-blank), `GattService(service, characteristics)`,
`GattCharacteristic(service, characteristic, properties: Set<CharacteristicProperty>, maxValueBytes: Int?)`
with `isReadable/isWritable/isNotifiable`, `CharacteristicProperty{READ,WRITE,WRITE_NO_RESPONSE,NOTIFY,INDICATE}`,
`MtuInfo(negotiatedMtu ≥ 23)`. A write to a non-writable attribute is `WRITE_REJECTED`; a payload above
`usablePayloadBytes` is `INVALID_STATE` (refused, **never fragmented** — framing is the protocol's). All
identifiers are caller-supplied; no device UUID lives here.

## 4. RFCOMM contract (`RfcommTransport`; prompt §10; ADR-P6-003)

```
val endpoint: RfcommEndpoint                             // serviceUuid and/or channel; not both null
suspend fun read(): Outcome<ByteArray>                   // a raw run; no message boundary implied
suspend fun write(bytes): Outcome<Unit>                  // serialised; opaque
suspend fun exchange(request, timeout): Outcome<TransportResponse>  // write whole, read one run
```
`RfcommEndpoint(serviceUuid: GattUuid?, channel: Int?)` requires at least one non-null; channel ∈ 1..30.
No framing member exists. On Android the client socket opens only by service UUID, so a channel-only
endpoint is refused at the platform (stated limitation, §8).

## 5. Error mapping (prompt §13; ADR-P6-006)

No new `OmniBudsErrorCategory`. `TransportErrorMapping.gattCategory(status)` maps the framework GATT codes:
success→null; auth/encryption→`PERMISSION_DENIED`; read/write-not-permitted→`WRITE_REJECTED`;
invalid-handle/request-not-supported→`INVALID_STATE`; congested→`RESOURCE_UNAVAILABLE`; timeout→`TIMEOUT`;
else→`GATT_FAILURE`. Socket failures → `RFCOMM_FAILURE`; unresolved connect → `TIMEOUT`; operation on a
non-`CONNECTED` channel → `RESOURCE_UNAVAILABLE`. Each category already carries its retry class
(`RETRY_AFTER_REREAD`/`SAFE_TO_RETRY`/`NEVER_RETRY`); a `TIMEOUT` is `RETRY_AFTER_REREAD` — re-read state,
never blindly re-send a side-effecting write. No raw exception crosses into `:core`; the boundary is
`OperationOutcome<T>`.

## 6. Resolution contract (prompt §12; ADR-P6-005)

`TransportResolver.resolve(candidates: List<TransportBoundary>): TransportResolution`.
`TransportResolution(negotiation, outcome: ResolutionOutcome, confidence, reason)` — `negotiation.selected`
**must be null** (constructor-enforced); `NO_CANDIDATE_AVAILABLE` requires a non-null `reason`, `NO_EVIDENCE`
requires it null (absence ≠ refusal). `ResolutionOutcome{SINGLE_CANDIDATE, CANDIDATES_AMBIGUOUS,
NO_CANDIDATE_AVAILABLE, NO_EVIDENCE}`. `UndeterminedTransportResolver` is the only implementation: it
classifies, orders nothing, connects nothing, and caps `confidence` at `INFERRED`. It consumes
transport-layer data only (`transport` may not import `device`, L1↛L2).

## 7. Concurrency and ownership (prompt §14; ADR-P6-007)

One `Mutex` per channel; operations check state inside the lock. No unbounded queue (refuse, don't buffer).
Cleanup `NonCancellable`; `close()` idempotent. Notifications via a cold `Flow`; collector end ⇒
`cancelSubscription`. No auto-reconnect, no engine-owned scope/dispatcher, no main-thread blocking (socket
I/O on `Dispatchers.IO`). A `CLOSED`/`FAILED` channel returns `RESOURCE_UNAVAILABLE` for any operation.

## 8. Known platform limitations

- The characteristic value API (`BluetoothGattCharacteristic.value`, 2-arg read/write, `writeDescriptor`)
  is deprecated at API 33 but unavoidable below it; minSdk 26 keeps it, suppressed **only** in
  `SystemGattTransportHandle.kt` with a written reason. Nothing in `:core` carries the suppression.
- Client RFCOMM by bare channel number has no public Android API; only service-UUID connects exist.
- `TRANSPORT_GATT_OPEN`/`TRANSPORT_RFCOMM_OPEN` are declared capabilities; the mechanism is compiled and
  seam-tested but **never executed on a device**, so no claim exceeds `IMPLEMENTED`.
- The `AndroidGattTransportTest` and its `AndroidRfcommTransport` counterparts prove the *logic* against
  scripted handles; the `System*Handle` files are exercised only by the instrumented suite, which is
  compiled and not run (ADR-P3-016). First device-verified use is the Phase 19 vendor.
