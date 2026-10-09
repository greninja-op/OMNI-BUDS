# Phase 19 — Target Selection

**Date:** 2026-10-09
**Status:** BLOCKED — no candidate meets the evidence bar for safe implementation.

## Candidates evaluated

### 1. Bose BMAP (QuietComfort 35 / QC Ultra Headphones 2)
- **Source:** bosectl (GitHub, MIT licensed)
- **Evidence:** Reverse-engineered from HCI traffic; 121+63+54 tests across
  Python/Rust/C++; captured fixtures; protocol notes.
- **Transport:** Bluetooth RFCOMM (channel 8 for QC35, channel 2 for QC Ultra 2).
- **Operations:** NC control, EQ, battery, button remap via SETGET/START operators.
- **Assessment:** Strongest candidate. MIT license permits use. However:
  - Byte-level command IDs and message formats not accessible from this
    environment (documentation fetch failed).
  - No physical hardware to validate implementation correctness.
  - Second-hand reverse-engineering; cannot verify without device.
- **Verdict:** REJECTED for implementation (insufficient accessible evidence).

### 2. QCY MeloBuds Pro (Quicky protocol)
- **Source:** OpenQCY / Quicky (GitHub)
- **Evidence:** 40+ documented commands, GATT service docs.
- **License:** Unclear from available information.
- **Verdict:** REJECTED (license unverified, no hardware).

### 3. Realme Buds T310
- **Source:** Single Python script (GitHub)
- **Evidence:** Minimal; ANC control only; no protocol spec.
- **Verdict:** REJECTED (insufficient documentation).

### 4. Technics EAH-AZ100 (Airoha RACE)
- **Source:** Community controller (GitHub)
- **Evidence:** Reverse-engineered from APK; 30+ settings.
- **Verdict:** REJECTED (no hardware, complex protocol, license unclear).

### 5. Huawei FreeBuds
- **Source:** FreeBuddies (GitHub)
- **Evidence:** Comprehensive; SPP protocol.
- **Verdict:** REJECTED (Huawei-specific, no hardware to validate).

### 6. SoundPEATS (Qualcomm GAIA V2)
- **Source:** budsctl (GitHub)
- **Evidence:** GAIA is a Qualcomm standard; implementation from owned device.
- **Verdict:** REJECTED (no SoundPEATS hardware available for validation).

## Decision

**No vendor adapter will be implemented in Phase 19.**

Rationale:
1. The phase forbids fabricating protocols. Implementing from incomplete
   second-hand documentation without hardware validation risks exactly that.
2. The phase explicitly instructs: "If no candidate has sufficient evidence
   for safe implementation, stop vendor implementation and report the blocker."
3. All candidates lack either accessible byte-level specifications, clear
   licensing, or hardware for validation.

## What Phase 19 WILL deliver

1. **Vendor-adapter infrastructure:** fingerprint matching, protocol registry
   integration, capability declaration framework — with all operations honestly
   reporting UNSUPPORTED/UNKNOWN.
2. **Research inventory** (`research.md`): evidence ledger showing what exists
   and what's missing for each candidate.
3. **Protocol documentation template** (`protocol.md`): structure for future
   evidence-backed protocol docs, marked as aspirational.
4. **All 11 required documents**, describing the infrastructure actually built.

## Required evidence before enabling write operations (future)

For any future vendor adapter:
- [ ] Byte-level protocol specification from a verifiable source.
- [ ] License permitting implementation and redistribution.
- [ ] At least one captured fixture or documented test vector.
- [ ] Physical hardware for validation (deferred to hardware phase).
- [ ] Firmware version(s) the specification applies to.
