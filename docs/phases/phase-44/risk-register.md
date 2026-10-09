# Phase 44 — Risk Register

---

| Risk ID | Description | Severity | Probability | Mitigation Strategy |
|---|---|---|---|---|
| **RISK-P44-01** | Speculative Protocol Upgrade: Selecting a newer protocol revision than the connected hardware supports. | Critical | Medium | Strict deterministic constraint matching. Never pick newest version without explicit evidence. Reject mutating commands if version is unverified. |
| **RISK-P44-02** | Lexicographical Version Sorting Bug (e.g. "1.10" < "1.9"). | High | Low | Implement typed numeric parsing for SemVer and integer revisions; explicit unit tests for multi-digit component boundaries. |
| **RISK-P44-03** | Silent Metadata Corruption during Schema Migration. | High | Low | Fail-closed transactional migrations; unknown required fields throw; provenance IDs must match 1:1 before and after. |
| **RISK-P44-04** | Accidental Bypassing of Centralized Authorization via Version Resolver. | Critical | Low | Compatibility resolution only returns candidate selection metadata; authorization pipeline remains downstream and enforces explicit policy check. |
| **RISK-P44-05** | Fallback Leakage Across Vendors. | Critical | Low | Namespaced protocol definitions; compatibility resolver enforces matching vendor namespace before candidate evaluation. |
