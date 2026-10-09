# Phase 19 — Test Plan

## Vendor matching (`VendorRegistryTest`, 8 tests)
- Null adapter matches nothing.
- Empty registry → null.
- Registry with only null adapter → null.
- Ambiguous match blocks resolution; isAmbiguous true.
- Multiple matches block resolution (conflict → null).
- Single exact match resolves.
- Adapter IDs listed.
- Null adapter declares zero commands.

## Regression
Full suite must pass with no behavior change to Phases 7–18.

## Explicitly not tested
Protocol parsing, command encoding, feature control, battery read-back,
persistence verification — no adapter exists to test these. Marked BLOCKED
in requirements.
