# Phase 44 — Evidence and Provenance Policy for Version Claims

---

## 1. Evidence Ladder for Protocol Versions

Protocol compatibility claims adhere strictly to the OmniBuds evidence ladder:

1. `INFERRED`: Theoretical compatibility derived from vendor marketing or sibling model similarity. Allowed for documentation; **strictly prohibited** from driving mutating commands or resolving version constraints.
2. `IMPLEMENTED`: Code path and codecs written in OmniBuds, passing syntax checks.
3. `LAB_TESTED`: Offline verification against synthetic frames, fuzz tests, and recorded packet captures. Minimum threshold for read-only observations.
4. `HARDWARE_VERIFIED`: Physical device verified with actual Bluetooth packets (deferred to Phase 52).
5. `PERSISTENCE_VERIFIED`: Full state persistence across disconnects verified on hardware (deferred to Phase 52).

---

## 2. Anti-Self-Promotion

Neither community adapters nor newly created vendor extensions may self-declare `HARDWARE_VERIFIED` or `PERSISTENCE_VERIFIED`. All automated builds in Phase 44 remain bounded at `LAB_TESTED`.
