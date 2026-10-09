# Phase 37 — Design

## Modules

`core/protocoltest/`:

- `ProtocolTestCase` / `ProtocolTestCaseValidator` — schema.
- `ProtocolTestRunner` — deterministic runner.
- `ProtocolCampaign` / `ProtocolCampaigns` — selection.

## Reuse

LabParser/ParseOutcome (Phase 20), TestCase/TestResult (Phase 30),
InputValidator (Phase 35), recovery contracts (Phase 34). No
competing runner.

## Data flow

TestCase → validate → filter → sort → execute (isolated) →
compare → ProtocolTestResult → TestResult.

## Concurrency

Sequential execution; no shared mutable state between cases.

## Security boundaries

- Declarative cases only.
- Fixture IDs are plain identifiers.
- Traces never executed.
- No hardware authorization bypass.
