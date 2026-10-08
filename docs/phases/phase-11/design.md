# Phase 11 — Codec Capability Engine: Design

## 1. Problem

OmniBuds must answer "what codec is this device using" without becoming an audio
application and without inventing answers. The hard truth discovered in Phase 11
research: **Android's public API does not expose the active/negotiated codec.**
`BluetoothCodecConfig`/`BluetoothCodecStatus` exist as public data classes (API 33+),
but no public method returns instances of them; `BluetoothLeAudio` has no codec
getter at any API level; no codec broadcast is public. The only public codec
observation is `BluetoothA2dp.getSupportedCodecTypes()` (API 35+) — the *local
phone's* capabilities, not per-device state.

An architecture that pretends otherwise would be a fabrication engine. Phase 11
is therefore designed around honest observability: rich models for what *can* be
known, explicit NOT_OBSERVABLE for what cannot, and UNKNOWN that never degrades
into "unsupported".

## 2. Reuse, not reinvention

Phase 1 already built a sophisticated codec vocabulary (ADR-P1-005): the `Codec`
enum with `CodecFamily`, the `CodecState` evidence ladder
(UNKNOWN/UNSUPPORTED/SUPPORTED/AVAILABLE/ENABLED/NEGOTIATED/ACTIVE), and
`CodecCapability` with orthogonal `configurable`. Phase 11 **extends** this —
it does not create a parallel taxonomy. The prompt's seven states map onto the
ladder (six rungs) plus the orthogonal configurable flag, which is precisely
the decomposition the prompt demands.

What Phase 11 adds:
- **Provenance**: `CodecEvidence` (source × confidence × time × API detail).
- **Observability**: `CodecObservability` (platform limits vs device limits).
- **Metadata**: `CodecMetadata` (nullable sample rate/bits/channel/bitrate/quality).
- **Runtime half**: `CodecRuntimeState` (live state, timestamped, freshness-aware).
- **Snapshot**: immutable per-device `CodecSnapshot` with explicit limitations.
- **Engine**: `CodecCapabilityEngine` (per-device lifecycle, Flow, staleness).

## 3. Architecture

```
platform/android                                   :core
┌──────────────────────────────────┐              ┌─────────────────────────────────┐
│ SystemCodecObservationHandle     │ raw          │ CodecCapabilityEngine           │
│  - API-35-isolated (CodecApi35)  │ ──────────── │  - per-device snapshots         │
│  - permission-first reads        │  primitives  │  - start/stop/refresh           │
├──────────────────────────────────┤              │  - staleness on stop            │
│ AndroidCodecObservationSource    │ domain       ├─────────────────────────────────┤
│  - implements CodecObservation   │ ──────────── │ com.omnibuds.core.audio         │
│    Source port                   │  types       │  (vocabulary, extended)         │
├──────────────────────────────────┤              │  Codec, CodecState (+OPUS),     │
│ codec/mapping                    │              │  CodecCapability (+evidence,    │
│  - ONLY translator               │              │   observability, metadata),    │
└──────────────────────────────────┘              │  CodecEvidence, CodecMetadata…  │
                                                  └─────────────────────────────────┘
```

### 3.1 The ladder, not booleans

Six independent booleans permit `active=true, supported=false`. The `CodecState`
ladder makes each state a distinct rung — "supported but not active" is one
ordinary value, and the ladder order is load-bearing for `supportsAtLeast`.
`configurable` stays orthogonal because a codec can be ACTIVE *and*
configurable, or ACTIVE and read-only.

### 3.2 Evidence never inflates

`EvidenceConfidence` orders UNKNOWN < INFERRED < OBSERVED < VERIFIED.
Normalization preserves confidence; a database inference that passes through
three layers is still INFERRED. The engine's `toObservability()` maps sources
to observability without upgrading confidence.

### 3.3 Staleness as a state, not a deletion

`stop()` marks the runtime record STALE. Consumers see "was LDAC-active, now
stale" — never a silent disappearance, never a stale ACTIVE rendering as
current. Reconnect re-observes from the platform; the stale record does not
survive.

### 3.4 The honest adapter

| Platform fact | Adapter behavior |
|---|---|
| API 35+ `getSupportedCodecTypes()` | → SUPPORTED rung, OBSERVED, per-codec |
| Below API 35 / no permission | → all codecs UNKNOWN + NOT_OBSERVABLE |
| Active/negotiated codec | → null runtime ("unobserved"), NOT_OBSERVABLE + limitation |
| aptX Adaptive/Lossless | → identity exists, NOT_OBSERVABLE (no platform constant) |
| LE Audio runtime config | → NOT_OBSERVABLE + limitation |

## 4. Key decisions (see decisions.md)

- **ADR-P11-001**: Extend the Phase 1 codec vocabulary; no parallel taxonomy.
- **ADR-P11-002**: Add OPUS to the domain enum (platform genuinely reports it).
- **ADR-P11-003**: Active codec is NOT_OBSERVABLE via public APIs — verified by
  android.jar reflection + api-versions.xml, not assumed.
- **ADR-P11-004**: `CodecApi35` isolated (ADR-P10-005 pattern) for VerifyError safety.
- **ADR-P11-005**: Staleness marks, never deletes; reconnect re-observes.
- **ADR-P11-006**: Three error categories; observation failure ≠ "unsupported".
- **ADR-P11-007**: `CodecScopeTest` machine-checks the no-control/no-interception boundary.
- **ADR-P11-008**: Engine in `com.omnibuds.core.codec` (layer 3), vocabulary stays in
  `com.omnibuds.core.audio` (layer 2).

## 5. What Phase 11 does NOT do

Codec switching/forcing, LDAC/aptX/LC3 configuration, quality-mode writes,
priority modification, hidden settings, shell commands, vendor writes, media
capture/decode/re-encode, DSP, production UI, physical-device testing. The
`configurable` flag records *writability observed*, never a write path.
