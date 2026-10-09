# Phase 30 — Coverage and Quality Gates

## Gates

- All new deterministic tests pass.
- Existing regression tests pass (or pre-existing failures documented).
- No fixture claims fabricated real-device provenance.
- No unsupported feature bypasses authorization.
- No parser accepts malformed input contrary to contract.
- No lifecycle test leaves unmanaged tasks/resources.
- No hard conflict produces an executable plan.
- No test treats unknown battery/codec as confirmed.
- Reports preserve failure/blocked statuses.
- Build/test commands reproducible from docs.

## Coverage

Measured with the repository's existing tools where available. This
phase adds 29 framework-validating tests (total suite: see validation.md).
No coverage percentage is claimed without measurement; untested
critical paths are documented in validation.md.

## Anti-gaming

No trivial assertions to inflate counts; every test asserts behavior
relevant to its requirement refs.
