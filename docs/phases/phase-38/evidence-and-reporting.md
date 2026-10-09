# Phase 38 — Evidence and Reporting

## Report contents

Campaign ID/version, environment, app build/commit, profile ref,
start/end times, status counts, evidence refs, safety outcome,
cleanup outcome, limitations, deferred checks.

## Statuses

PASS, FAIL, SKIPPED, BLOCKED, CANCELLED, INVALID,
INFRASTRUCTURE_ERROR, DEFERRED_TO_FINAL_HARDWARE_VERIFICATION.

## Rules

- Outcome and evidence level are separate fields.
- A missing prerequisite is never a PASS.
- Physical checks that didn't run are DEFERRED, not passed.
- Evidence is redacted before persistent logs (LogRedactor).
- No Bluetooth credentials or secrets stored.
