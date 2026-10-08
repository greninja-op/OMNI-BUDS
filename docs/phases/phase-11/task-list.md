# Phase 11 — Codec Capability Engine: Task List

All tasks complete. Checked entries are done and validated.

## Domain vocabulary (core/audio — extended)

- [x] `CodecEvidenceSource` — 7-source provenance enum
- [x] `EvidenceConfidence` — UNKNOWN/INFERRED/OBSERVED/VERIFIED (ordered)
- [x] `CodecEvidence` — source × confidence × time × detail
- [x] `CodecObservability` — OBSERVABLE/PARTIALLY_OBSERVABLE/NOT_OBSERVABLE/UNKNOWN
- [x] `CodecFreshness` — CURRENT/STALE/UNKNOWN
- [x] `CodecMetadata` + `CodecBitrate` sealed (exact/range/adaptive/unknown, all nullable-or-unknown)
- [x] `Codec` extended with `OPUS`
- [x] `CodecCapability` extended with `evidence`, `observability`, `metadata` (backward-compatible defaults)

## Engine (core/codec — new)

- [x] `CodecRuntimeState` — ladder state + parameters + freshness + evidence
- [x] `CodecDiagnostic` — bounded diagnostics
- [x] `CodecSnapshot` — immutable per-device, derived `activeCodec`, explicit limitations
- [x] `CodecObservationSource` — the port (capabilities / runtime / flow)
- [x] `CodecCapabilityEngine` — per-device lifecycle, staleness, Flow, injected dispatcher+clock
- [x] Error categories: `CODEC_OBSERVATION_FAILED`, `CODEC_NOT_OBSERVABLE`, `CODEC_STATE_STALE`

## Platform adapters

- [x] `CodecObservationHandle` — raw-primitives seam (`RawCodecInfo`, `CodecIdKind`)
- [x] `CodecApi35` — API-35-isolated (init guard, VerifyError-safe)
- [x] `SystemCodecObservationHandle` — permission-first, delegates on 35+
- [x] `AndroidCodecObservationSource` — honest degradation (empty→UNKNOWN/NOT_OBSERVABLE; list→SUPPORTED/OBSERVED; runtime→null)
- [x] `codec/mapping` — both constant families; LC3→LE_AUDIO; unknown→null

## Tests

- [x] Core: `CodecDomainTest` (6), `CodecCapabilityStateTest` (9), `CodecMetadataTest` (8),
  `CodecEvidenceTest` (4), `CodecCapabilityEngineTest` (11), `CodecScopeTest` (4)
- [x] `OmniBudsErrorCategoryTest` updated for 3 new categories
- [x] `DependencyDirectionTest` layer map: `codec` → 3
- [x] Android: `CodecMappingTest` (6), `AndroidCodecObservationSourceTest` (6)
- [x] Full suite: 816 core + 128 android = **944 tests, all passing**

## Documentation

- [x] `docs/phases/phase-11/`: requirements, design, architecture, specs, task-list,
  test-plan, validation, decisions, risk-register
- [x] Index updates: `docs/README.md`, `docs/decisions/README.md`, `docs/MASTER-CONTEXT.md`,
  root `README.md`

## Deferred (not Phase 11)

- [ ] Codec configuration/switching (later phase; legitimate APIs only)
- [ ] Physical-device verification of `CodecApi35` binder path (needs hardware)
- [ ] Per-device negotiated codec (needs a public platform API that does not exist)
- [ ] Production UI
