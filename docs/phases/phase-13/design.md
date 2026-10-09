# Phase 13 — Design: Audio Quality & Negotiation State Engine

## Architecture

```
Android Bluetooth / Audio APIs (public only)
                 ↓
        Audio Transport Engine (Phase 10)
                 ↓
        Codec Capability Engine (Phase 11)
                 ↓
        Codec Control Engine (Phase 12)
                 ↓
        AudioQualityEngine (Phase 13)  ← NEW
                 ↓
        AudioQualityResolver (pure)    ← NEW
                 ↓
        AudioQualityState (per device) ← NEW
                 ↓
        Consumers (StateFlow + events)
```

## New types (`com.omnibuds.core.quality`, layer 4)

| Type | Role |
|---|---|
| `NegotiationState` | 9-state lifecycle enum |
| `NegotiationTransitions` | Legal-transition table |
| `NegotiationEvent` | Sealed immutable events (12 kinds) |
| `NegotiationSession` | Bounded session value |
| `AudioQualityState` | Unified per-device runtime state |
| `QualityProfile` | Immutable point-in-time snapshot |
| `AdaptiveState` | FIXED / ADAPTIVE / UNKNOWN |
| `SourcePrecedence` | Documented conflict hierarchy |
| `AudioQualityResolver` | Pure deterministic resolver |
| `AudioQualityEngine` | Stateful engine: ingest → resolve → events → Flow |

## Android (`com.omnibuds.android.bluetooth.audio.quality`)

`AndroidAudioQualityBridge` wires the three Phase 10/11/12 engine flows into
the quality engine. Device correlation is caller-supplied; uncorrelated
devices keep UNKNOWN codec fields.

## Key decisions

1. **Derived, not observed, negotiation.** The platform exposes no negotiation
   protocol events. `NegotiationState` is derived from transport/codec
   observations — documented as derived, never claimed as protocol truth.
2. **Negotiated ≠ active.** Separate fields; no automatic promotion.
3. **Resolver is pure.** All conflict logic in one testable function.
4. **Engine is stateful but honest.** Dedup by value equality; sessions
   bounded; disconnect terminates sessions and preserves history as STALE.
5. **No debouncing.** Inputs are snapshot-derived, not event bursts —
   documented per OB-P13-REQ-022.
6. **No quality scores.** Facts only (OB-P13-REQ-028).
