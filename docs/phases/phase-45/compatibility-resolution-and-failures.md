# Phase 45 — Compatibility Resolution & Failure Handling

## 1. Resolution Flow

```mermaid
flowchart TD
    Start[Observation & Candidates] --> CheckID[Identification Sufficient?]
    CheckID -- No --> RetInsufficient[INSUFFICIENT_EVIDENCE]
    CheckID -- Yes --> CheckValid[Validation State Valid?]
    CheckValid -- Malformed --> RetInvalid[INVALID_FIRMWARE]
    CheckValid -- Rejected --> RetPolicy[BLOCKED_BY_POLICY]
    CheckValid -- Yes --> CheckFresh[Observation Fresh?]
    CheckFresh -- Stale --> RetStale[STALE_FIRMWARE_OBSERVATION]
    CheckFresh -- Fresh --> CheckUnknown[Firmware Unknown?]
    CheckUnknown -- Yes --> EvalAgnostic[Candidate Agnostic?]
    EvalAgnostic -- Yes --> RetLimitations[COMPATIBLE_WITH_LIMITATIONS]
    EvalAgnostic -- No --> RetUnknown[UNKNOWN_FIRMWARE]
    CheckUnknown -- No --> MatchRules[Match Explicit Rules]
    MatchRules --> CheckConflict[Rule Conflict?]
    CheckConflict -- Deny Rule Present --> RetDeny[INCOMPATIBLE]
    CheckConflict -- Ambiguous --> RetAmbiguous[AMBIGUOUS]
    CheckConflict -- Clear --> DelegateResolver[Delegate Protocol Resolver]
    DelegateResolver --> FinalResult[Return FirmwareCompatibilityResult]
```

## 2. Failure Handling
- `INCOMPATIBLE`: Mutating and read-only operations denied. Diagnostic event emitted.
- `UNKNOWN_FIRMWARE`: Mutating operations blocked. Safe read-only permitted if adapter is firmware-agnostic.
- `STALE_FIRMWARE_OBSERVATION`: Operations blocked until a fresh on-device firmware read completes.
- `AMBIGUOUS`: Resolution aborted; no arbitrary winner selected.
