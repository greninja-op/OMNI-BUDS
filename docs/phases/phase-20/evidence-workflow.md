# Phase 20 — Evidence Workflow

## Status lifecycle

```
INFERRED → IMPLEMENTED → LAB_TESTED → HARDWARE_VERIFIED → PERSISTENCE_VERIFIED
```

## Evidence required per transition

| Transition | Required evidence |
|---|---|
| INFERRED → IMPLEMENTED | Implementation exists with documented inputs, outputs, limitations, versions |
| IMPLEMENTED → LAB_TESTED | Automated/offline tests passed; fixtures and versions recorded |
| LAB_TESTED → HARDWARE_VERIFIED | Real-device evidence via authorized verification; synthetic insufficient |
| HARDWARE_VERIFIED → PERSISTENCE_VERIFIED | Setting observed from device after lifecycle boundary |

## Rules

- No level-skipping.
- No downgrade via promotion (downgrades are separate, audited).
- Hardware statuses require `hasRealDeviceEvidence=true`.
- Every transition recorded as `EvidenceTransition` (schema, from, to,
  evidence ref, timestamp, approved).
- The lab tracks the workflow; it never auto-promotes.
