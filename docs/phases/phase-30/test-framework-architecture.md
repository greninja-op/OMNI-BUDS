# Phase 30 — Test Framework Architecture

## Package

`com.omnibuds.core.testkit` — test infrastructure only. Nothing in
production code references it; architecture tests should enforce this
in a later phase if desired.

## Components

| Component | Responsibility |
|---|---|
| TestCase | Structured case contract |
| TestFixture + FixtureValidator | Versioned, validated inputs |
| ScriptedTransport | Deterministic TransportContract double |
| FailureInjector | Seeded, scoped failures |
| TestResult + TestReport | Structured evidence and summaries |

## Layers A–E

A: pure unit (existing suite). B: component with fakes. C: integration
with fixtures. D: Android platform contracts. E: hardware-backed
(contracts only; campaigns in Phases 31–33).

## Execution

Ordinary suites: no Bluetooth adapter, no network, no hardware.
Deterministic: seeded, isolated, repeatable.
