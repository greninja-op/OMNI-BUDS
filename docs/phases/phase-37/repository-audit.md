# Phase 37 — Repository Audit

**Date:** 2026-10-09

## Existing protocol/test infrastructure (verified)

| Component | Location | Notes |
|---|---|---|
| `LabParser` / `ParseOutcome` | `core/lab/ParserFramework.kt` | Typed framing + parse outcomes; parsers analyze, never transmit |
| `FixtureGenerator` / `ParserFixture` | `core/lab/FixtureTestRunner.kt` | Deterministic fixtures with provenance; synthetic never mislabeled |
| `ProtocolTrace` / `TraceValidator` | `core/lab/` | Trace validation |
| `TestCase` contract | `core/testkit/TestCaseContract.kt` | Determinism classes, categories |
| `FailureInjector` / `ScriptedTransport` | `core/testkit/` | Deterministic failure injection |
| InputValidator | `core/security/` | Bounded limits (Phase 35) |
| Recovery contracts | `core/recovery/` | Failure classification, bounded retry |

## Gaps

1. No versioned protocol test-case schema with validation.
2. No deterministic protocol test runner (campaigns, isolation,
   budgets, cancellation).
3. No protocol regression catalog.
4. No campaign selection/filtering model.
5. No structured protocol test reports integrated with Phase 30.

## Implementation strategy

New `core/protocoltest/` package:

- `ProtocolTestCase.kt` — versioned schema + validator.
- `ProtocolTestRunner.kt` — deterministic runner: load → validate →
  isolate → execute → compare → report. Bounded, cancellable.
- `ProtocolCampaign.kt` — campaign selection and filtering.
- `ProtocolTestReport.kt` — structured results.

Reuse LabParser, TestCase, FailureInjector, InputValidator,
recovery contracts. No competing runner; no hardware writes.
