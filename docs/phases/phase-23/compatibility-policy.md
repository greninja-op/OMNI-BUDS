# Phase 23 — Compatibility Policy

## Dimensions

Manufacturer (stable ID), exact model, hardware revision, firmware version,
protocol ID/version, transport, feature-specific support, known limitations.

## Mandatory rules

1. Never match on manufacturer display name alone.
2. Never assume sibling models share command semantics.
3. Never assume firmware compatibility without evidence.
4. Treat unknown firmware as a separate compatibility case.
5. Treat ambiguous identity as insufficient for model-specific writes.
6. Reject protocol-version mismatches unless documented otherwise.
7. Re-evaluate on identity/firmware evidence changes.
8. Invalidate cached resolutions on metadata changes.
9. Never auto-replay denied operations after compatibility changes.
10. Never enable a feature solely because its descriptor exists.

## Results

`Compatible(reason, evidence, identityAmbiguous)`,
`Incompatible(reason, evidence)`, `UnknownFirmware(reason, evidence)`.
Every result carries its reason and evidence.
