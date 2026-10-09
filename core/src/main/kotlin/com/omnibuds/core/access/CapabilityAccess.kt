package com.omnibuds.core.access

import com.omnibuds.core.state.CapabilityState

/**
 * Access dimension for a capability.
 *
 * Phase 21 (OB-P21-REQ-013): support state (what the device can do) and
 * access (what OmniBuds may do) are separate. A READ_ONLY capability may
 * be observed but never written.
 */
enum class CapabilityAccess {
    /** Support unknown; no operations authorized. */
    UNKNOWN,

    /** Positively unsupported; no operations authorized. */
    UNSUPPORTED,

    /** May be observed; writes denied. */
    READ_ONLY,

    /** May be observed and written through a verified path. */
    READ_WRITE,

    /** May be written but not meaningfully observed. */
    WRITE_ONLY,
}

/**
 * A capability record with support, access, and verification kept distinct.
 *
 * Phase 21 (OB-P21-REQ-014) — the ten rules:
 * 1. UNKNOWN stays UNKNOWN when discovery is incomplete.
 * 2. A missing protocol operation is not a successful no-op.
 * 3. Manufacturer advertisement ≠ connected-model support.
 * 4. One model's implementation ≠ sibling-model support.
 * 5. Parsing a message ≠ authorization to write it.
 * 6. Synthetic fixtures ≠ real-device capability.
 * 7. Local preferences ≠ hardware state.
 * 8. Unmet prerequisites → unavailable.
 * 9. Verified read ≠ write permission.
 * 10. Firmware compatibility and protocol safety still apply.
 */
data class CapabilityAccessRecord(
    val capabilityId: String,
    /** What the device supports (Phase 8 evidence ladder). */
    val support: CapabilityState,
    /** What OmniBuds may do. */
    val access: CapabilityAccess,
    /** True when a write path has been verified (read-back or persistence). */
    val writePathVerified: Boolean,
    /** True when firmware is known compatible with this capability. */
    val firmwareCompatible: Boolean,
    /** Evidence references backing this record. */
    val evidence: List<String>,
) {
    init {
        // Rule 9: read access never implies write access.
        if (access == CapabilityAccess.READ_WRITE) {
            require(writePathVerified) {
                "READ_WRITE access requires a verified write path"
            }
        }
        // Rule 1: unknown support cannot carry a positive access grant.
        if (support == CapabilityState.UNKNOWN) {
            require(access == CapabilityAccess.UNKNOWN || access == CapabilityAccess.UNSUPPORTED) {
                "unknown support must not carry a positive access grant"
            }
        }
        // UNSUPPORTED support cannot be writable.
        if (support == CapabilityState.UNSUPPORTED) {
            require(access != CapabilityAccess.READ_WRITE && access != CapabilityAccess.WRITE_ONLY) {
                "unsupported capability cannot be writable"
            }
        }
    }

    companion object {
        /** Default record: unknown support, no access. */
        fun unknown(capabilityId: String): CapabilityAccessRecord =
            CapabilityAccessRecord(
                capabilityId = capabilityId,
                support = CapabilityState.UNKNOWN,
                access = CapabilityAccess.UNKNOWN,
                writePathVerified = false,
                firmwareCompatible = true,
                evidence = emptyList(),
            )
    }
}

/**
 * Capability access evaluator.
 * Bridges Phase 8 capability discovery with the Phase 21 access policy.
 */
object CapabilityAccessEvaluator {

    /**
     * Derive the access policy inputs for a capability.
     * Returns whether a write operation would be permitted.
     */
    fun canWrite(
        deviceState: DeviceAccessState,
        record: CapabilityAccessRecord,
    ): AccessDecision = DeviceAccessPolicy.evaluate(
        state = deviceState,
        operation = OperationCategory.HARDWARE_STATE_WRITE,
        capabilityId = record.capabilityId,
        capabilityWriteVerified = record.writePathVerified &&
            (record.access == CapabilityAccess.READ_WRITE ||
                record.access == CapabilityAccess.WRITE_ONLY),
        firmwareCompatible = record.firmwareCompatible,
        evidenceFresh = true,
    )

    /** Whether a read operation would be permitted. */
    fun canRead(
        deviceState: DeviceAccessState,
        record: CapabilityAccessRecord,
    ): AccessDecision = DeviceAccessPolicy.evaluate(
        state = deviceState,
        operation = OperationCategory.HARDWARE_STATE_READ,
        capabilityId = record.capabilityId,
        evidenceFresh = true,
    )
}
