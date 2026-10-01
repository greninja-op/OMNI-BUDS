package com.omnibuds.core.transport

import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.common.TransportKind

/**
 * The Bluetooth-specific shape of a [TransportContract], stated as a boundary only.
 *
 * Phase 2 is authorised to establish transport *boundaries* and nothing more (Phase 2 prompt
 * section 5.6, section 6). This interface is that boundary: it names the questions every Bluetooth
 * control channel must eventually answer, in vocabulary the core already owns, and it stops there.
 * [TransportContract] carries the mechanics (`open`, `close`, `exchange`) and deliberately no
 * Bluetooth-specific method, because PROTO-ABST-006 forbids expressing protocol semantics in
 * transport primitives and OQ-PROTO-01 hands characteristics, socket parameters and callback shapes
 * to Phase 6. A second root interface above it therefore adds no behaviour, only the place where
 * Bluetooth-specific documentation and availability vocabulary live — which is what the sub-
 * interfaces in this package need to inherit from.
 *
 * **What this phase does not implement, and why that is not a gap.** [open], [close] and [exchange]
 * are inherited and stay unimplemented here: no type in `:core` may implement them at all, and an
 * implementation would be a channel that reports working without a radio behind it (Phase 1 prompt
 * section 27, ADR-P1-013). Phase 6 (`BluetoothOperation.TRANSPORT_GATT_OPEN` and
 * `TRANSPORT_RFCOMM_OPEN`) is the phase that first authorises opening a channel; Phase 2 authorises
 * only inspecting the adapter, permissions, platform capabilities and these boundaries
 * (`BluetoothOperation.PLATFORM_CAPABILITY_INSPECTION`).
 *
 * Sub-interfaces: [GattTransport], [RfcommTransport], [ClassicTransport], [BleTransport],
 * [LeAudioTransport]. Each one's documentation states why it exists separately (master section 8)
 * and what it deliberately does not add.
 */
interface BluetoothTransport : TransportContract {

    /**
     * Which Bluetooth channel this boundary stands for.
     *
     * Inherited from [TransportContract] unchanged — restated here only so the Bluetooth rule can
     * live with the Bluetooth boundary:
     *  - The value comes from this transport's own identity. It is never inferred from a device
     *    name, a model string or a vendor: [TransportKind] has no vendor member, and
     *    `VENDOR_SPECIFIC` is a mechanism kind with no mechanism behind it yet (ADR-P1-008,
     *    PROTO-VENDOR-004).
     *  - [TransportKind.UNKNOWN] is a legal reading of it while nothing has been established, and
     *    it is a different statement from "no channel exists" (`TransportKind` documentation).
     *  - It never licenses another channel. A request that fails on this kind stays attributed to
     *    this kind (PROTO-XPORT-007).
     */
    override val kind: TransportKind

    /**
     * Ask whether this kind of channel could exist for the caller, without trying to use it.
     *
     * Probing availability is **not** opening a channel: it reads platform-side facts — whether the
     * adapter exists and is on, whether the permission the channel would need has been granted,
     * whether this OS exposes the API at all (`BluetoothPlatformCapabilities.candidateTransports`)
     * — and reports them as a [TransportAvailability] record. It sends nothing, discovers nothing,
     * writes nothing (PROTO-XPORT-008 constrains probing on an unidentified device to exactly this
     * kind of question) and creates no characteristic handle, socket or callback registration, all
     * of which are Phase 6 mechanics.
     *
     * The returned record must name this boundary's [kind]; a probe that answers about a different
     * transport is the "assume GATT" defect (PROTO-XPORT-001) in a new costume.
     *
     * **No Phase 2 code calls this.** Phase 2 declares the signature so the phase that opens
     * channels does not have to invent the shape, and so a caller today cannot receive a
     * transport-shaped object with no availability question to ask of it. Because `:core` may not
     * hold a test double in main source (ADR-P1-013), no implementation exists to answer it yet.
     */
    suspend fun probeAvailability(): OperationOutcome<TransportAvailability>
}
