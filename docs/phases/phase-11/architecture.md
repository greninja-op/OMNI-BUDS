# Phase 11 — Codec Capability Engine: Architecture

This document explains the codec architecture in depth for future phases
(especially the codec-control phase, which must understand exactly what
observation guarantees before it writes anything).

## 1. Layering

```
com.omnibuds.core.audio   (layer 2 — vocabulary, no dependencies)
    Codec, CodecFamily, CodecState, CodecCapability,
    CodecEvidence{,Source}, EvidenceConfidence, CodecObservability,
    CodecMetadata, CodecBitrate, CodecFreshness, ChannelMode, QualityMode

com.omnibuds.core.codec   (layer 3 — engine, depends on audio + device)
    CodecCapabilityEngine, CodecSnapshot, CodecRuntimeState,
    CodecObservationSource (port), CodecDiagnostic

com.omnibuds.android.bluetooth.audio.codec   (platform — depends on core)
    CodecObservationHandle (seam), SystemCodecObservationHandle,
    CodecApi35 (API-35-isolated), AndroidCodecObservationSource,
    codec/mapping (only translator)
```

The vocabulary layer has no dependencies; the engine layer coordinates; the
platform layer translates. Nothing below depends on anything above
(`DependencyDirectionTest` enforces this).

## 2. The evidence ladder

`CodecState` declaration order is the ladder and is load-bearing:

```
UNKNOWN < UNSUPPORTED < SUPPORTED < AVAILABLE < ENABLED < NEGOTIATED < ACTIVE
```

A codec moves up only on evidence of the matching tier. `supportsAtLeast`
uses ordinal comparison gated on the SUPPORTED floor, so UNKNOWN and
UNSUPPORTED satisfy no positive target. `isActive` is exact — only the ACTIVE
rung, never `negotiated || enabled`.

`configurable` is orthogonal: writability is not a degree of activity.

## 3. Provenance flow

```
Platform read
    → RawCodecInfo (primitives only)
    → mapping (platform id → Codec, or null)
    → CodecCapability(codec, state, configurable, evidence, observability, metadata)
    → CodecCapabilityEngine.readSnapshot
    → CodecSnapshot (immutable, per-device)
    → StateFlow consumers
```

Evidence is attached at the mapping layer (where the source is known) and
preserved unchanged through the engine. Confidence never increases.

## 4. Observability semantics

| Observability | Meaning | Example |
|---|---|---|
| OBSERVABLE | Public API exposes this | Local codec list on API 35+ |
| PARTIALLY_OBSERVABLE | Some dimensions only | (reserved for protocol sources) |
| NOT_OBSERVABLE | No public API | Active A2DP codec, LE Audio config |
| UNKNOWN | Not yet determined | Fresh capability record |

NOT_OBSERVABLE + UNKNOWN state = "the platform does not expose this".
It is never rendered, stored, or reasoned about as "unsupported".

## 5. Snapshot lifecycle

```
start(device) → read capabilities + runtime → publish CURRENT snapshot
              → collect runtime flow (catch-and-continue)
refresh(device) → re-read → replace snapshot (host-triggered only; no polling)
stop(device) → cancel flow → mark runtime STALE (capabilities survive)
start(device) again → fresh read → CURRENT (stale record replaced)
```

## 6. Transport derivation

The snapshot's `transport` comes from the codec family's unanimity:
all-LE_AUDIO → LE_AUDIO; all-CLASSIC_A2DP → CLASSIC_A2DP; mixed or empty →
UNKNOWN. LC3 therefore always yields LE_AUDIO; a mixed report yields UNKNOWN
rather than a guessed transport.

## 7. Error model

| Category | Retry | Session |
|---|---|---|
| CODEC_OBSERVATION_FAILED | SAFE_TO_RETRY (reads are side-effect-free) | untouched |
| CODEC_NOT_OBSERVABLE | NEVER_RETRY (needs OS upgrade) | untouched |
| CODEC_STATE_STALE | SAFE_TO_RETRY (re-observe) | untouched |

A failed observation is never converted into "codec unsupported" — the
failure is recorded as a diagnostic and the last good snapshot is kept.

## 8. What the control phase must know

When a later phase implements codec configuration, it must:
- Read `configurable` (observed writability), never assume it.
- Write only through legitimate APIs/protocols that exist at that time.
- Never use the observation port for writes (it has no write methods by construction).
- Preserve the ladder: a failed configuration does not demote the capability rung.
- Keep evidence: a configured-then-read-back codec is VERIFIED; a merely requested one is not.
