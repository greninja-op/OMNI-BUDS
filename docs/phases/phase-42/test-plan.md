# Phase 42 — Test Plan

## New tests (2 classes, 18 tests)
- AppleAirpodsAdapterTest (12): family match, ambiguous
  (no-audio-class, no-class), non-Apple not matched, audio-class
  without Apple ID not matched, unobserved, deterministic,
  evidence present, unsupported declared with reasons,
  read-only observations contain no controls, battery
  without-provenance unknown, missing battery unknown.
- AirpodsContractTest (6 via harness): Phase 41 contract.

## Regression
Full suite: core + android, 0 failures.
