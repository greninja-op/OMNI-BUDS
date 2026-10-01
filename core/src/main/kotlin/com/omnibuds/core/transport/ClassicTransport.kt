package com.omnibuds.core.transport

/**
 * A control path over classic Bluetooth that is not an RFCOMM serial port.
 *
 * Master section 8 lists Classic Bluetooth as its own potential transport, and
 * `protocol-governance.md` section 2 records its roles — "device/classic info; some control paths",
 * with the rule that its **absence is not failure**. Many headsets are classic-only for metadata
 * and BLE-only for attributes; a session that sees one and concludes the other is the ADR-P0-003
 * defect this boundary exists to prevent.
 *
 * [com.omnibuds.core.common.TransportKind.CLASSIC_BLUETOOTH] is documented in that enum as
 * "other classic Bluetooth channel (SDP-discovered, vendor defined)", which is precisely the scope
 * left here after [RfcommTransport] takes the serial-port case: SDP-record discovery, an
 * L2CAP-level channel, or a vendor-defined classic socket.
 *
 * **What would be fabricated to add a member now:** an SDP lookup would require a service record and
 * a UUID — Phase 2 prompt section 6 forbids service discovery, and PROTO-NOMAGIC-002 forbids
 * service UUID literals standing in code as the evidence for a claim nobody has observed. A socket
 * parameter, a channel id or a connect member would be Phase 6 mechanics (OQ-PROTO-01), and an
 * implementation today could only answer with a made-up success, which ADR-P1-013 prohibits.
 *
 * Implementing phase: Phase 6 for any channel; Phase 3 for the connected-device facts that make a
 * classic candidate visible at all (`BluetoothOperation.CONNECTED_DEVICE_INSPECTION`).
 *
 * The boundary is deliberately thin: it pins
 * [com.omnibuds.core.common.TransportKind.CLASSIC_BLUETOOTH] as its [kind] and inherits
 * [BluetoothTransport.probeAvailability] to say whether such a channel could exist, which is all
 * Phase 2 can state honestly.
 */
interface ClassicTransport : BluetoothTransport {

    /** Pinned: this boundary answers for exactly one transport kind, never another. */
    override val kind: com.omnibuds.core.common.TransportKind
        get() = com.omnibuds.core.common.TransportKind.CLASSIC_BLUETOOTH
}
