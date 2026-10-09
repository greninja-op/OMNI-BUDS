# Phase 24 — Test Plan

## Domain (`GlobalStateModelTest`, 5 tests)
Empty snapshot; immutability; observation usability; ID validation;
partial state explicitness.

## Aggregation (`AggregationTest`, 6 tests)
Connection→protocol→capabilities; duplicates; out-of-order rejection;
old-session rejection; feature request→observation; wrong-device rejection.

## Multi-device (`MultiDeviceTest`, 4 tests)
Two independent devices; disconnect isolation; no leakage; removed vs
disconnected.

## Derived/separation/consistency (`DerivedStateTest`, 10 tests)
Control permitted; per-capability readiness; desired≠observed; stale
detection; desired≠observed separation; ambiguous ops preserve confirmed
state; validator violations; clean state.

## Regression
Full suite: core + android, 0 failures.
