# Phase 14 — Design: Audio Path Validation Engine

## Architecture

```
Phase 10/11/12 observations
         ↓
AudioQualityEngine (Phase 13)
         ↓
AudioPathValidationEngine (Phase 14)  ← NEW
  ├─ ValidationInput (coherent bundle)
  ├─ 10 ValidationRules (pure)
  ├─ ValidationAggregator (pure)
  └─ AudioPathValidationSnapshot (immutable)
         ↓
StateFlow<Map<DeviceIdentity, AudioPathValidationSnapshot>>
```

## New types (`com.omnibuds.core.validation`, layer 5)

| Type | Role |
|---|---|
| `ValidationStatus` | 7-state outcome enum |
| `ValidationSeverity` | INFO/WARNING/ERROR/CRITICAL |
| `ValidationCategory` | 7 categories |
| `ValidationResult` | Structured rule result |
| `ValidationRule` | Pure rule interface |
| `ValidationInput` | Coherent observation bundle |
| `AudioPathValidationSnapshot` | Immutable evaluation |
| `ValidationAggregator` | Deterministic aggregation |
| `AudioPathValidationEngine` | Stateful engine with generations |

## Rules (`validation/rules/`)

| Rule | Category | Checks |
|---|---|---|
| P14-DEVICE-001 | Device association | Quality state device matches |
| P14-DEVICE-002 | Device association | Disconnected ≠ active route |
| P14-DEVICE-003 | Device association | Stale codec not current |
| P14-TRANSPORT-001 | Transport | Transport/route coherence |
| P14-CODEC-001 | Codec | Codec/transport association |
| P14-CODEC-002 | Codec | Active claim structure |
| P14-ROUTE-001 | Route | Availability vs selection |
| P14-PARAM-001 | Parameters | Domain constraints |
| P14-FRESH-001 | Freshness | Currency coherence |
| P14-LIFE-001 | Lifecycle | Generation currency |

## Key decisions

1. **Validation ≠ signal verification.** VALID means "observations
   consistent", never "sound at the speaker".
2. **Session generations** gate obsolete events; disconnect bumps the
   generation.
3. **Pure rules, stateful engine.** Rules are testable in isolation.
4. **Dedup by content.** Identical observations don't re-emit.
5. **No new Android code.** The domain engine consumes the Phase 13
   quality engine, which the Android bridge already feeds.
