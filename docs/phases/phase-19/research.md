# Phase 19 — Research Evidence Ledger

**Date:** 2026-10-09
**Status:** No claim below exceeds INFERRED. No hardware validation performed.

## R19-001 — Bose BMAP protocol exists
- **Claim:** Bose headphones use a binary protocol ("BMAP") over RFCOMM.
- **Source:** bosectl (GitHub, MIT licensed), accessed 2026-10-09 via web search.
- **Evidence type:** Second-hand reverse-engineering report.
- **Confidence:** INFERRED
- **Reproducibility:** Cannot reproduce without hardware.
- **Limitations:** Byte-level details not accessible; correctness unverified.
- **Impact:** Candidate for future implementation if evidence improves.

## R19-002 — QCY MeloBuds Pro BLE protocol documented
- **Claim:** 40+ commands documented for QCY earbuds over BLE GATT.
- **Source:** Quicky / OpenQCY (GitHub), accessed 2026-10-09.
- **Evidence type:** Second-hand reverse-engineering report.
- **Confidence:** INFERRED
- **Limitations:** License unverified; no hardware.
- **Impact:** Candidate for future implementation.

## R19-003 — No in-repo protocol evidence
- **Claim:** The OmniBuds repository contains no vendor protocol
  specifications, captured traces, or device-specific command documentation.
- **Source:** Repository audit 2026-10-09 (grep across docs/ and source).
- **Evidence type:** Direct repository inspection.
- **Confidence:** IMPLEMENTED (verified by inspection)
- **Impact:** Phase 19 cannot implement any vendor adapter.

## R19-004 — Protocol registry ships empty by design
- **Claim:** `ProtocolRegistry` defaults to empty; this is the normal state.
- **Source:** `core/.../protocol/ProtocolRegistry.kt:11,42`
- **Evidence type:** Source inspection.
- **Confidence:** IMPLEMENTED
- **Impact:** No vendor protocol is expected to exist yet.
