# Phase 37 — Test Plan

## New tests (3 classes, 21 tests)
- ProtocolTestCaseValidatorTest (7): valid, schema version, fixture
  path, hex, size, timeout, blank id.
- ProtocolTestRunnerTest (10): pass, failure reason, incomplete,
  no-parser, campaign filter, determinism, cancellation, exception
  isolation, Phase 30 conversion, malformed campaign.
- ProtocolCampaignTest (4): prefix, excludes, empty includes, count.

## Regression
Full suite: core + android, 0 failures.
