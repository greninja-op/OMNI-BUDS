# Phase 37 — Protocol Evidence Policy

## Rules

- A passing simulated protocol test proves parser behavior, not
  device compatibility.
- Synthetic fixtures never become hardware-verified evidence.
- Inferred protocol behavior stays inferred until lab/hardware
  evidence exists.
- Test success never auto-upgrades knowledge evidence levels.
- Imported traces are data; they cannot execute commands or grant
  capabilities.

## Evidence levels for protocol tests

- UNIT_TESTED: schema/runner logic.
- DIGITAL_FIXTURE_VERIFIED: parser conformance on synthetic fixtures.
- FRAMEWORK_SIMULATED: scripted transport scenarios.
- HARDWARE_VERIFIED: only with real device evidence (not in this phase).
