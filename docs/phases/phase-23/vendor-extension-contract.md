# Phase 23 — Vendor Extension Contract

## Supported interface

`VendorFeatureExtension`: descriptor + `resolveFeatures`.
`VendorFeatureReader`: `observeFeature` (optional).
`VendorFeatureWriter`: `executeFeature` (optional).

An extension implements only what it supports.

## Lifecycle

DRAFT → ACTIVE → DEPRECATED → DISABLED. Registration validates structure.
Deprecation preserves history. Disablement removes from resolution.

## Dependencies

Extension-level dependencies (other extension IDs) checked for cycles at
registration. Feature-level dependencies checked at execution against
observed device state.

## Permissions

Extensions have no permissions of their own. Every operation passes the
centralized access policy (injected by the caller).

## Supported operations

Only what the descriptor declares and the feature definition supports.
Read/write/ack/read-back/idempotency declared per operation.

## Restrictions

- No unrestricted transport access.
- No raw commands.
- No dynamic code loading or downloaded scripts.
- No bypassing the access policy.
- No cross-device state leakage.
- Metadata never authorizes writes.
