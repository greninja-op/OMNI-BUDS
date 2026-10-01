package com.omnibuds.core.device

/**
 * One manufacturer-specific data record as it was observed, kept as opaque evidence.
 *
 * Devices put vendor-defined records into advertisements and into the metadata the
 * platform exposes. This type stores only what was seen: [companyId] is the
 * identifier attached to the record and [dataHex] is its payload as hex text. It
 * deliberately stores no interpretation, no field names, and no length, because
 * reading a meaning out of these bytes is a protocol record's job and doing it early
 * turns an observation into a claim (Phase 1 prompt sections 10, 52 and 53;
 * ADR-P0-018: enumeration precedes identification).
 *
 * A null field means "this part was not observed". It does not mean the device has no
 * manufacturer data — that is a positive establishment and belongs in the capability
 * layer as `CapabilityState.UNSUPPORTED` with evidence behind it, never here
 * (ADR-P0-016, master section 53).
 *
 * The generated `toString()` prints the payload verbatim, so an entry must never be
 * logged wholesale: manufacturer data is redacted at the point of emission
 * (`docs/phases/phase-0/security-governance.md` SEC-LOG-002).
 */
data class ManufacturerDataEntry(
    /** The company identifier attached to the record, or null if it was not seen. */
    val companyId: Int? = null,

    /** The record payload as hex text, or null if the payload was not seen. */
    val dataHex: String? = null,
) {
    /**
     * True when the record carries no observed content at all — a placeholder, not
     * evidence. [DeviceFingerprint] excludes such entries from its identity key so
     * that "nothing was read" cannot inflate into "something is known".
     */
    val isEntirelyUnobserved: Boolean
        get() = companyId == null && DeviceIdentity.normalised(dataHex) == null
}
