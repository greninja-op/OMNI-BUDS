# Phase 33 — Design

## Modules (test-only)

`core/src/test/kotlin/com/omnibuds/core/audio/quality/`:

- `AudioFixture` / `AudioFixtures` — deterministic synthetic PCM.
- `SignalAnalysis` — peak, RMS, silence, clipping, discontinuity,
  channel identity, mismatch count.
- `TimingMeasurement` — MonotonicClock, FakeClock, TimingMeasurer,
  per-category measurements.

## Production boundary

No new production audio code. The app never handles PCM samples;
production verification is via existing codec-state and quality
engines plus new consistency/non-interference tests.

## Data flow

Fixture → analysis → expected-vs-actual comparison.
Events → TimingMeasurer (fake clock) → TimingMeasurement.

## Concurrency

All utilities are pure and stateless (except the per-test FakeClock);
no locks needed.

## Security boundaries

- No capture, no upload, no user audio.
- Synthetic provenance labels prevent mistaking fixtures for
  hardware measurements.
