# Phase 37 — Protocol Test Runner

## Flow

1. Load validated test cases.
2. Filter by campaign.
3. Sort by testCaseId (deterministic).
4. Cap at maxCasesPerCampaign (1000).
5. For each case (unless cancelled): resolve parser, parse input,
   compare outcome, record result.
6. Convert to Phase 30 TestResult.

## Properties

- Deterministic ordering.
- Per-test timeout (declared; enforced by the harness).
- Cancellation between cases.
- Isolation: parser exceptions become `error` outcomes, never crash
  the campaign.
- No network, no hardware.
- Imported traces are data, never executed.

## Outcome mapping

Parsed → `parsed`; Incomplete → `incomplete`; UnsupportedFormat →
`rejected`; no parser → `no-parser`; exception → `error`.
