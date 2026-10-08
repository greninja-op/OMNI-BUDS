# Phase 11 — Codec Capability Engine: Risk Register

## RISK-P11-001 — Platform reports local capabilities as device capabilities

- **Likelihood:** Medium | **Impact:** Medium
- **Description:** `getSupportedCodecTypes()` reports the *phone's* codecs; a
  consumer could misread "SBC SUPPORTED" as "the earbuds support SBC".
- **Mitigation:** Evidence detail names the API; KDoc stresses per-endpoint
  semantics; the pair is SUPPORTED only when both ends report it (existing
  `CodecState` contract).
- **Residual:** Consumer education; a future peer-capability source is needed
  for the full pair.

## RISK-P11-002 — Consumers render UNKNOWN as a codec name or "off"

- **Likelihood:** Medium | **Impact:** High
- **Description:** A future UI reads `activeCodec = null` as "nothing playing"
  or picks the first SUPPORTED codec as "the codec".
- **Mitigation:** Normative specs (§6 invariants); `activeCodec` KDoc ("unreported,
  never nothing playing"); NOT_OBSERVABLE observability on the record.
- **Residual:** UI-phase review must check every codec display path.

## RISK-P11-003 — Hidden-API temptation for the active codec

- **Likelihood:** Medium | **Impact:** High
- **Description:** "Just reflect into the hidden getCodecStatus" would give the
  active codec today and break on the next OS release.
- **Mitigation:** ADR-P11-003 records the decision; `CodecScopeTest` bans
  reflection hacks vocabulary; architecture.md documents the limitation.
- **Residual:** Revisit only if a public API appears.

## RISK-P11-004 — Scope creep into codec control

- **Likelihood:** Medium | **Impact:** High
- **Description:** "Just expose one setter" erodes the observation-only boundary.
- **Mitigation:** `CodecScopeTest`; the port has no write methods by construction;
  `configurable` records writability without providing a path.
- **Residual:** Reviewer vigilance.

## RISK-P11-005 — Stale state rendered as current by a buggy consumer

- **Likelihood:** Low | **Impact:** Medium
- **Description:** A consumer ignores `freshness` and shows a STALE codec as live.
- **Mitigation:** Freshness is a first-class enum, not a timestamp to interpret;
  engine tests prove the transitions.
- **Residual:** Consumer contracts in later phases.

## RISK-P11-006 — VerifyError on API < 35

- **Likelihood:** Low | **Impact:** High
- **Description:** `BluetoothCodecType` references on older runtimes.
- **Mitigation:** ADR-P11-004 (`CodecApi35` isolated, init check, SDK guard).
- **Residual:** Needs an API-26–34 device to prove the negative; deferred with
  hardware testing.

## RISK-P11-007 — aptX Adaptive/Lossless confusion

- **Likelihood:** Low | **Impact:** Medium
- **Description:** Users expect these codecs; the platform has no constant for
  them, so they stay NOT_OBSERVABLE and users may call it a bug.
- **Mitigation:** Domain identities exist; mapping test documents the gap;
  limitations are explicit in docs.
- **Residual:** Vendor-protocol phase may observe them via DEVICE_PROTOCOL source.

## RISK-P11-008 — Evidence detail as an information leak

- **Likelihood:** Low | **Impact:** Low
- **Description:** Detail strings could accumulate device-specific data.
- **Mitigation:** Convention (API names only) + `CodecEvidenceTest` + security
  review; no addresses/secrets in details.
- **Residual:** None known.
