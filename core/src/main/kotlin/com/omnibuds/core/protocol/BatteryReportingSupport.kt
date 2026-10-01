package com.omnibuds.core.protocol

import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.device.BatteryState

/**
 * A protocol that can report battery levels as the device states them.
 *
 * **What implementing this says, and what it does not.** It says this protocol family has a
 * battery reporting path. It does not say the connected device reports battery, and it does
 * not license a battery UI: that is decided by capability discovery, and a device nobody has
 * asked stays [com.omnibuds.core.state.CapabilityState.UNKNOWN] rather than `UNSUPPORTED`
 * (PROTO-ABST-002, PROTO-CAP-004, master section 53).
 *
 * The returned [BatteryState] must carry exactly what was reported, per side and per field:
 * a null level is "the device did not say", never `0`, never `-1`, and a real `0` is a flat
 * battery that must survive intact (master section 23, ADR-P0-016). A failed read yields a
 * [Failure][OperationOutcome.Failure] and leaves the previous reading's freshness to the
 * caller — it must not be converted into zeros on the way out (specs.md section 3 rule 5).
 *
 * Which channel the reading came from matters, and this contract cannot express it: a level
 * derived from HFP battery reporting is not vendor battery truth
 * (`protocol-governance.md` section 2, PROTO-XPORT-002 table row for HFP). That binding
 * belongs to the [com.omnibuds.core.capability.FeatureCapability] record discovery
 * produces, which carries a transport, so a later phase can label the source honestly.
 *
 * No feature semantics live here either: ANC, transparency, equalizer and gesture behaviour
 * is not modelled by any reporting interface in this package, because inventing those shapes
 * now would fabricate hardware behaviour. Such features arrive as values addressed through
 * [com.omnibuds.core.protocol.FeatureReadSupport] and defined by their own phases
 * (Phase 1 prompt sections 2 and 26).
 *
 * Phase 1 defines the contract only: no battery communication, no polling, no
 * notification subscription, no implementation in `:core` (Phase 1 prompt sections 2 and
 * 51).
 */
interface BatteryReportingSupport {

    /** Read the battery levels the device currently reports. */
    suspend fun readBattery(): OperationOutcome<BatteryState>
}
