# Phase 37 — Specifications

## ProtocolTestCase

- SUPPORTED_SCHEMA_VERSION = 1; MAX_HEX_CHARS = 131,072;
  MAX_TIMEOUT_MILLIS = 30,000; MIN_TIMEOUT_MILLIS = 1.
- Fields as documented in protocol-test-case-schema.md.

## ProtocolTestCaseValidator

- validate: TestCaseValidation.Valid | Invalid(reasons sorted).

## ProtocolTestRunner

- ProtocolTestRunner(parsers, maxCasesPerCampaign = 1000).
- run(runId, campaignId, cases, cancelled): List<ProtocolTestResult>.
- ProtocolTestResult: runId, testCaseId, campaignId, protocolId,
  expectedOutcome, actualOutcome, passed, failureReason?,
  durationMillis.
- Phase 30 mapping: call-site conversion to TestResult (proven by test).

## ProtocolCampaign

- selects(testCaseId): excludes win; empty includes = all.
- ProtocolCampaigns: 4 standard campaigns.
