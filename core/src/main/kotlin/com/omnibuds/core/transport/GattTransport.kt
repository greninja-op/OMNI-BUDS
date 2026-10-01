package com.omnibuds.core.transport

/**
 * The GATT attribute protocol as a control channel: services, characteristics and descriptors.
 *
 * This is the channel most modern vendor control services are written against, and it is also the
 * channel OmniBuds is forbidden to assume. ADR-P0-003 and master section 8 exist because a headset
 * routinely pairs a BLE/GATT service used for advertising and bookkeeping with an RFCOMM channel
 * that carries the actual configuration — treating "no GATT service" as "no control channel" is the
 * single most common way a universal control app ends up reporting a working device as unsupported
 * (PROTO-XPORT-001, ARCH-XPORT-001).
 *
 * The boundary pins one inherited fact and adds nothing else: an implementation reports
 * [com.omnibuds.core.common.TransportKind.GATT] as its [kind]. Everything GATT-shaped that could be
 * declared now — service discovery, characteristic enumeration, read/write/notify members, MTU,
 * connection priority, a callback shape — is excluded by Phase 2 prompt section 6 (no GATT service
 * discovery, no characteristic enumeration, no characteristic writes) and deferred by OQ-PROTO-01,
 * which hands "characteristics, socket parameters, callback shape" to Phase 6.
 *
 * **What would be fabricated to add it now:** a service-discovery member would promise an inspection
 * Phase 2 may not perform; a characteristic-write member would be a write path with no verification
 * ladder behind it (PROTO-RESEARCH-002 forbids writes before step 7 of the research workflow, and
 * there is no protocol to justify one); a callback member would fix the Phase 6
 * transport mechanics in a phase whose only authorised statement is "a control channel must be
 * able to report its availability and carry requests".
 *
 * Implementing phase: Phase 6 (`BluetoothOperation.TRANSPORT_GATT_OPEN`, authorizedInPhase = 6);
 * the first device-verified use lands with the Phase 19 vendor device, and nothing may be reported
 * above `IMPLEMENTED` before it executes (PROTO-VERIFY-001).
 *
 * See `docs/phases/phase-2/transport-boundaries.md` for the deferred list and the reason each
 * absence exists.
 */
interface GattTransport : BluetoothTransport {

    /** Pinned: this boundary answers for exactly one transport kind, never another. */
    override val kind: com.omnibuds.core.common.TransportKind
        get() = com.omnibuds.core.common.TransportKind.GATT
}
