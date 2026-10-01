package com.omnibuds.core.protocol

import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.device.FirmwareInfo

/**
 * A protocol that can report version information about the device's build.
 *
 * **What implementing this says, and what it does not.** It says this protocol family has a
 * firmware-reading path. Whether the connected device answers it, and what the answer is,
 * is a discovery result (PROTO-ABST-002). An unimplemented path yields `UNKNOWN` for the
 * firmware feature and never `UNSUPPORTED` (PROTO-ABST-003).
 *
 * A returned [FirmwareInfo] carries only versions that were actually read. Its
 * [FirmwareInfo.verification] states the evidence tier behind them and is the field a later
 * claim is measured against (PROTO-VERIFY-001), and its version fields stay null when the
 * device did not report them: there is no `"0.0.0"`, no empty string and no plausible
 * default here, because a fabricated version string is how a device that was never queried
 * ends up looking real (Phase 1 prompt section 53, master section 23, ADR-P0-016).
 *
 * This method also sits inside the identification gate rather than outside it: versions
 * belong to [com.omnibuds.core.device.DeviceFingerprint], and a firmware change outside the
 * compatible range recorded for a fingerprint revokes claims above `INFERRED` for the
 * affected capabilities (PROTO-VERIFY-007, `OmniBudsErrorCategory.FIRMWARE_MISMATCH`).
 * Reading a version therefore changes what may be claimed, which is why the result is
 * evidence-tiered rather than a bare string.
 *
 * No feature semantics live here: ANC, transparency, equalizer and gesture behaviour is not
 * modelled by any reporting interface in this package, because inventing those shapes in
 * Phase 1 would fabricate hardware behaviour. Features arrive as values addressed through
 * [com.omnibuds.core.protocol.FeatureReadSupport] and are defined by the phases that own
 * them (Phase 1 prompt sections 2, 26 and 51).
 *
 * Phase 1 defines the contract only: no firmware communication, no update logic, no
 * implementation in `:core` (Phase 1 prompt sections 2, 26 and 51).
 */
interface FirmwareReportingSupport {

    /** Read the version evidence the device reports, with its own verification tier. */
    suspend fun readFirmware(): OperationOutcome<FirmwareInfo>
}
