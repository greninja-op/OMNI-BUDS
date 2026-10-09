# Phase 33 — Decisions

## D-33-01: test-only framework
**Decision:** Fixtures and analysis live in `core/src/test/`.
**Rationale:** Production never handles PCM samples.

## D-33-02: minimal metric set
**Decision:** peak, RMS, silence, clipping, discontinuity, channels,
mismatch. No FFT, no SNR.
**Rationale:** Defensible minimum; sine analysis doesn't prove fidelity.

## D-33-03: no acoustic category
**Decision:** TimingCategory has no acoustic latency entry.
**Rationale:** Cannot be measured in software; prevents false claims.

## D-33-04: synthetic provenance
**Decision:** Every fixture carries a "synthetic" provenance string.
**Rationale:** Prevents mistaking fixtures for hardware data.

## D-33-05: audits over new production code
**Decision:** No new production audio code this phase.
**Rationale:** The existing engines already implement the state model.
