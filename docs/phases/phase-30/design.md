# Phase 30 — Design

## Modules

`core/testkit/` (test infrastructure; never in production paths):

- `TestCaseContract` — TestCase, TestCategory, DeterminismClass.
- `TestFixture` — versioned fixtures + FixtureValidator.
- `ScriptedTransport` — deterministic TransportContract double.
- `FailureInjector` — seeded, scoped failure injection.
- `TestEvidence` — TestResult, TestReport, evidence levels.

## Layers

- A (unit): existing 1604 tests.
- B (component): engines with fakes (e.g. ScriptedTransport).
- C (integration): module interactions with fixtures.
- D (platform): Android contracts where supported.
- E (hardware): contracts defined, campaigns deferred to Phases 31–33.

## Data flow

TestCase → fixtures (validated) → scripted doubles → assertions →
TestResult → TestReport.

## Determinism

Seeded randomness only; no wall-clock; isolation per test.

## Security boundaries

- No production backdoors; injector never referenced by production.
- Reports capped; no credentials/payloads.
- Simulated evidence never upgrades capability verification.
