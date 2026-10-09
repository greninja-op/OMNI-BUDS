# Phase 44 — Compatibility Resolution Engine

---

## 1. Resolution Algorithm

The `CompatibilityResolver` evaluates evidence systematically:

1. **Namespace & Manufacturer Gate:** Candidate protocols must match the vendor namespace established by fingerprinting. Cross-vendor candidate consideration is prohibited.
2. **Transport Availability Gate:** Protocol candidates requiring transports not present on the host or connected device are filtered out as unsupported.
3. **Firmware Constraints Gate:** If the device firmware version is known, candidates with incompatible firmware constraints are removed.
4. **Version Constraint Gate:** The observed device protocol version is matched against each candidate's `VersionConstraint`.
5. **Outcome Determination:**
   - If 0 candidates survive due to version mismatch: return `INCOMPATIBLE`.
   - If 0 candidates survive due to unknown device version: return `UNKNOWN_VERSION`.
   - If 1 candidate survives: return `COMPATIBLE` (or `COMPATIBLE_WITH_LIMITATIONS` if minor limitations exist).
   - If >1 candidates survive: return `AMBIGUOUS` with all surviving candidates listed and `selected = null`.

---

## 2. Machine-Readable Reasons

Every resolution result provides a structured reason code (e.g. `ERR_VERSION_MISMATCH`, `ERR_MULTIPLE_CANDIDATES`, `ERR_UNKNOWN_DEVICE_VERSION`, `OK_EXACT_MATCH`) and actionable details for telemetry and debugging.
