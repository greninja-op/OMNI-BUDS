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
 *    OQ-PROTO-01 deferred the concrete channel shape to Phase 6, which put it there and not here;
 *  - a `TransportKind` naming BLE — resolved at Phase 6 (ADR-P6-004): [com.omnibuds.core.common.TransportKind.BLE]
 *    **does** exist (Phase 2 added it so this boundary had a kind to report), and this file previously
 *    claimed the opposite while returning it. The kind describes the *link*, not a control channel; a
 *    vendor control service reachable over BLE is a GATT service, so opening and exchanging happen through
 *    [GattTransport]. One link having a link-kind and a riding-service-kind is not the "two identities for
 *    one operation" defect (PROTO-XPORT-001) — an operation is addressed to exactly one of them, and the
 *    link kind answers availability questions the attribute kind cannot.
 *
 * What the boundary still reserves: the link-level facts that the attribute protocol does not
 * answer — whether the platform can observe BLE at all, whether an advertisement was seen, and
 * whether availability over this link differs from availability of the GATT service riding on it.
 * Those acquire types in the phase that can answer them.
 *
 * Decided at Phase 6 (ADR-P6-004): `BleTransport` is the **link-layer availability boundary**, not a
 * control channel. It answers whether the platform can observe BLE at all and whether that availability
 * differs from availability of the [GattTransport] riding on it; `open`/`exchange` for a device over BLE
 * go through [GattTransport], because a control service on a BLE link *is* a GATT service. It therefore
 * stays a member interface that inherits [BluetoothTransport.probeAvailability] and adds no control
 * mechanics of its own — which is Phase 6 giving it distinct meaning, not an invented member.
 */
interface BleTransport : BluetoothTransport {

    /** Pinned: this boundary answers for exactly one transport kind, never another. */
    override val kind: com.omnibuds.core.common.TransportKind
        get() = com.omnibuds.core.common.TransportKind.BLE
}
