# Phase 20 — Test Plan

## Trace format (`TraceFormatTest`, 7 tests)
Valid trace; duplicate IDs; out-of-order sequences; length mismatch;
oversized payload; source type distinctness; redaction inconsistency.

## Parser (`ParserFrameworkTest`, 6 tests)
Valid parse; incomplete; oversized declaration; multi-message buffer;
empty payload; limit exceeded.

## Analysis (`LabAnalysisTest`, 10 tests)
Correlation pairing; unmatched preserved; ambiguous not paired; timeline
order; differential without semantics; schema version conflicts; fixture
classification; runner determinism; synthetic promotion blocked; evidence
requirements.

## Safety (`LabSafetyTest`, 5 tests)
No device-control vocabulary; no UI; no script execution; no network;
no fabrication vocabulary.

## Regression
Full suite: 1128 core + 142 android, 0 failures.
