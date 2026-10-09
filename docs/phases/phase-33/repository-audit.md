# Phase 33 — Repository Audit

**Date:** 2026-10-09

## Key finding: the app does not own the media data path

OmniBuds manages hardware settings and observes platform-reported
audio state. It never captures, decodes, re-encodes, routes, or
processes PCM samples. No `RECORD_AUDIO`, no `AudioRecord`, no
`MediaRecorder`, no media pipeline in production code.

## Existing audio architecture

| Package | Contents |
|---|---|
| `core/audio/` | Transport engine, codec capability/state/evidence models, observability, freshness, LE Audio |
| `core/codec/` | Codec control state, snapshots, transactions (Phases 12–13) |
| `core/quality/` | AudioQualityEngine, negotiation state machine, resolver, precedence |
| `core/processing/` | ProcessingOwnership — DSP boundary (app observes, does not process) |
| `platform/android/bluetooth/audio/` | SystemAudioTransportHandle — platform observation only |

## Codec state ladder (existing)

`CodecState`: UNKNOWN < UNSUPPORTED < SUPPORTED < AVAILABLE <
ENABLED < NEGOTIATED < ACTIVE, with orthogonal `configurable`.
`CodecObservability`: OBSERVABLE / PARTIALLY_OBSERVABLE /
NOT_OBSERVABLE / UNDETERMINED. Active-codec observation on Android is
NOT_OBSERVABLE via public API (Phase 11 finding).

## Existing tests

`AudioQualityEngineTest`, `NegotiationStateMachineTest`,
`TransportSeparationTest`, `SourcePrecedenceTest`, and others — state
consistency already covered at the engine level.

## Gaps

1. No test-only digital fixture framework (silence, sine, clipping).
2. No offline signal-integrity utilities (peak, RMS, clipping, silence).
3. No deterministic timing-measurement abstraction with fake clock.
4. No explicit non-interference architecture tests.
5. No audio-quality evidence matrix or fixture specification docs.

## Implementation strategy

- Test-only (`core/src/test/`): `AudioFixture` generator,
  `SignalAnalysis` utilities, `TimingMeasurement` with fake clock.
- Main: no new production audio code; tests only verify existing
  boundaries.
- New consistency tests on the existing codec-state ladder.
