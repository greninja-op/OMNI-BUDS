# Phase 39 — Protocol Operation Inventory

**Status:** No vendor protocol operations are implemented.

The `VendorAdapter.protocol` contract exists; the only registered
adapter is `omnibuds.null`, which defines zero operations.

When a vendor adapter is added in a future phase, each operation
must document:

- Stable operation ID.
- Model/firmware scope.
- Required transport, capability, authorization.
- Input/output schemas and validation.
- Timeout, cancellation, retry classification.
- Read-back verification strategy.
- Failure and recovery behavior.
- Evidence level.

No operation may be invented to make an implementation appear
complete.
