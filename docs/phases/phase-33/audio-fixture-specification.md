# Phase 33 — Audio Fixture Specification

## Format

See specs.md. All fixtures synthetic; provenance string mandatory.

## Generators

| Fixture | Content |
|---|---|
| silence | all zeros, stereo |
| sine-1khz | 1000 Hz sine, amplitude 0.5, dual mono |
| left-only | left sine, right silence |
| clipped | full-scale constant |
| discontinuity | 0.5 → -0.5 step mid-buffer |
| impulse | single full-scale impulse at frame 0 |

## Rules

- Fixed parameters; no randomness.
- Small buffers (4800 frames) — no large assets.
- Provenance must start with "synthetic".
- Never upload; never capture user audio.
