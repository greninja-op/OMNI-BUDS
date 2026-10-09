# Phase 37 — Protocol Regression Catalog

## Known regressions

(none recorded yet — this catalog is the living record)

## Process

When a protocol bug is found:

1. Preserve a minimal reproducible fixture.
2. Record expected behavior.
3. Add a regression test (prefix `proto.parser.` etc.).
4. Fix the defect.
5. Verify the test fails before / passes after.
6. Retain the test.

## Fixture rules

- Synthetic provenance labeled.
- No secrets or unredacted identifiers.
- Small, reviewable hex inputs.
