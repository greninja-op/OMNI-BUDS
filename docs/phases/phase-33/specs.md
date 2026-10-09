# Phase 33 — Specifications

## AudioFixture

fixtureId, fixtureVersion (1), sampleRateHz (48000), channelCount
(1–2), bitDepth (16/24/32), samples (interleaved, [-1, 1]),
provenance. frameCount = samples.size / channelCount.

## SignalAnalysis

- peak: max |sample|, [0,1]; empty → 0.
- rms: sqrt(mean(x²)), [0,1]; empty → 0.
- isSilent: rms < 1e-4.
- clippingCount: |x| >= 0.999; ratio = count/size; empty → 0.
- discontinuityCount(samples, threshold=0.5): adjacent jumps > threshold.
- channelsIdentical: 2ch, all |Δ| <= 1e-9.
- mismatchCount(actual, expected, tol=1e-9).

## TimingMeasurement

MonotonicClock.nowNanos; FakeClock.advance(millis).
TimingCategory: COMMAND_DISPATCH, VENDOR_CONTROL_RESPONSE,
STATE_OBSERVATION_DELAY, CONNECTION_SETUP, CODEC_STATE_UPDATE.
TimingMeasurement.elapsedMillis null when incomplete.
No ACOUSTIC category by design.
