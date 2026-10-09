# Phase 38 — Design

## Modules

`core/hil/` (layer 5):

- `HilEnvironment` — environment model.
- `HardwareProfile` / `HardwareProfileValidator` — profile schema.
- `HilSafetyPolicy` / `HilSafetyGate` — executable safety.
- `HilRig` / `DryRunHilRig` — rig abstraction.
- `HilCampaign` / `HilCampaignExecutor` / `HilExecutionReport` —
  campaigns and reporting.

## Reuse

TestCase (Phase 30), DeviceProfile (Phase 31), LogRedactor (Phase
35), DiagnosticStore (Phase 36), recovery contracts (Phase 34).
No competing schemas.

## Data flow

Campaign → validate → rig.initialize → safety-gated checks →
cleanup → report. Physical checks deferred, never passed.

## Security boundaries

- Safety defaults deny physical execution and writes.
- No Bluetooth API calls exist in this phase.
- Evidence redacted before persistence.
