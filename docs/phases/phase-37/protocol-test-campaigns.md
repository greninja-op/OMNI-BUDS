# Phase 37 — Protocol Test Campaigns

## Campaigns

| Campaign | Selects | Purpose |
|---|---|---|
| parser-regression | `proto.parser.*` | Fast parser conformance |
| framing-conformance | `proto.framing.*` | Message boundaries |
| malformed-input-safety | `proto.malformed.*` | Malformed/boundary safety |
| full-offline | all | Full offline regression |

## Selection rules

- Prefix includes; explicit excludes win.
- Deterministic ordering by testCaseId.
- Independent outcomes preserved on failure.
- Not applicable / skipped / blocked / invalid / failed stay distinct
  (via Phase 30 TestResultCategory).

## Scope honesty

No campaign is labeled "full coverage". full-offline covers the
offline inventory only.
