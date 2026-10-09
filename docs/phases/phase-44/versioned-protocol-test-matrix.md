# Phase 44 — Versioned Protocol Test Matrix

---

## Test Dimensions & Scenarios

| Area | Test Identifier | Input Conditions | Expected Outcome |
|---|---|---|---|
| **Version Parsing** | `P44-VER-01` | Semantic version string `"2.1.0"` | Parses to `Semantic(2, 1, 0)` |
| | `P44-VER-02` | Integer revision string `"3"` | Parses to `IntegerRevision(3)` |
| | `P44-VER-03` | Vendor string `"bose-cp-v2"` | Parses to `VendorDefined("bose-cp-v2")` |
| | `P44-VER-04` | Malformed string `"-1.0.0"` or `""` | Returns `null` / rejects cleanly |
| | `P44-VER-05` | Ordering check `1.10.0` vs `1.9.0` | `1.10.0 > 1.9.0` (numeric comparison) |
| **Constraint Matching** | `P44-CST-01` | Exact match constraint | Matches identical version only |
| | `P44-CST-02` | Semantic range `[1.0.0, 2.0.0]` | Matches `1.5.0`, rejects `2.1.0` |
| | `P44-CST-03` | Integer range `[1, 3]` | Matches `2`, rejects `4` |
| | `P44-CST-04` | Enumerated set | Matches set elements only |
| **Resolution Engine** | `P44-RES-01` | Single matching candidate | `COMPATIBLE` outcome |
| | `P44-RES-02` | Version constraint mismatch | `INCOMPATIBLE` outcome |
| | `P44-RES-03` | Device version unknown | `UNKNOWN_VERSION` outcome |
| | `P44-RES-04` | Missing transport | `INCOMPATIBLE` (transport unsupported) |
| | `P44-RES-05` | Two equal matching candidates | `AMBIGUOUS`, `selected = null` |
| | `P44-RES-06` | Fallback leakage check | No cross-vendor match allowed |
| **Schema Migration** | `P44-MIG-01` | Migrate Schema 1 to Schema 2 | Preserves evidence IDs & fields |
| | `P44-MIG-02` | Corrupted migration input | Transaction fails; state unchanged |
| | `P44-MIG-03` | Unsupported future schema (e.g. 99) | Throws `UnsupportedSchemaVersionException` |
| **Message Codecs** | `P44-MSG-01` | Version 1 wire payload | Decodes according to v1 schema |
| | `P44-MSG-02` | Version 2 wire payload with extended fields | Decodes according to v2 schema |
| | `P44-MSG-03` | Malformed / truncated packet | Returns error result; no exceptions |
| **Security Gates** | `P44-SEC-01` | Attempt mutating command on `UNKNOWN_VERSION` | Operation rejected by policy |
| | `P44-SEC-02` | Attempt mutating command on `AMBIGUOUS` | Operation rejected by policy |
| | `P44-SEC-03` | Self-promoted hardware verification | Rejected at registry gate |
