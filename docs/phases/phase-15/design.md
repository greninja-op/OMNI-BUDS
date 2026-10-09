# Phase 15 — Design: Hardware DSP / Audio Separation

## Architecture

```
CoreFeature identities (Phase 8)
         ↓
FeatureCapability records (Phase 8)
         ↓
ProcessingDomainResolver (Phase 15)  ← NEW: annotates with domain
         ↓
AudioProcessingDomain
         ↓
ProcessingControlBoundary (Phase 15) ← NEW: guards control requests
```

## New types (`com.omnibuds.core.processing`, layer 5)

| Type | Role |
|---|---|
| `AudioProcessingDomain` | 5-value domain enum |
| `ProcessingDomainResolver` | Pure resolver + eligibility |
| `ProcessingOwnership` | Ownership + persistence model |
| `ProcessingControlBoundary` | Cross-domain request guards |

## Reused (not duplicated)

- `FeatureCapability`, `CoreFeature`, `FeatureId` (Phase 8)
- `FeatureState` requested/confirmed split (Phase 9)
- `VerificationLevel` (Phase 11)
- `ConfigurationValue` (Phase 9)
- `FeatureRelation` dependencies (Phase 9)

## Key decisions

1. **Annotation, not replacement.** The resolver adds the domain dimension
   to existing capability records.
2. **UNKNOWN by default.** Hardware domain requires protocol + verification.
3. **No substitution.** Cross-domain requests are denied, never rerouted.
4. **Empty registry is normal.** No verified protocols → NOT_CONFIGURABLE.
