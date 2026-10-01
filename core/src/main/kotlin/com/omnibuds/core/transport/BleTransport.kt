package com.omnibuds.core.transport

/**
 * The Bluetooth Low Energy link as a boundary, before any protocol runs over it.
 *
 * BLE is a physical layer and an advertising regime. A device can be reachable over BLE, advertise
 * manufacturer-specific data, and still expose no control channel there. Master section 8 names
 * exactly this pair of facts as one of the things Phase 2 must not collapse — a device "may use BLE
 * for advertisements/bookkeeping" and "RFCOMM for control" at the same time — and ADR-P0-003 makes
 * that combination a supported configuration rather than a special case, which is why the boundary
 * exists separately from [GattTransport] instead of being folded into it.
 *
 * **This interface adds no member, and that is an honest outcome, not an unfinished one.**
 * Phase 2 prompt section 5.6 authorises boundaries only, and each candidate member would claim a
 * capability the phase has not established:
 *  - a scan or advertisement member — device discovery is Phase 3
 *    (`BluetoothOperation.DEVICE_DISCOVERY_SCAN`) and Phase 2 prompt section 6 excludes it;
 *  - a characteristic, descriptor or handle member — that is [GattTransport]'s vocabulary, and
 *    OQ-PROTO-01 defers the concrete channel shape to Phase 6;
 *  - a `TransportKind` naming BLE — [com.omnibuds.core.common.TransportKind] deliberately has no
 *    `BLE` constant, because a control channel over BLE *is* a GATT service. Inventing a second
 *    kind for one physical link would let a single operation have two identities, which is the
 *    "assume GATT" defect (PROTO-XPORT-001) arriving from the other direction.
 *
 * What the boundary still reserves: the link-level facts that the attribute protocol does not
 * answer — whether the platform can observe BLE at all, whether an advertisement was seen, and
 * whether availability over this link differs from availability of the GATT service riding on it.
 * Those acquire types in the phase that can answer them.
 *
 * Implementing phase: Phase 3 for link observation, Phase 6 for any channel
 * (`BluetoothOperation.TRANSPORT_GATT_OPEN`). Until then no implementation exists, and the audit
 * recommendation for this file is explicit: either Phase 6 gives it distinct meaning or it retires
 * it in favour of [GattTransport]. A marker with a documented reason is preferred to an interface
 * filled with invented methods, which is how fabricated support begins (ADR-P1-013).
 */
interface BleTransport : BluetoothTransport
