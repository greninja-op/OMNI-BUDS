# Phase 45 — Firmware Capability Matrix

| Vendor / Model | Firmware Constraint | Capability / Feature | State when Satisfied | Fallback State | Evidence Ref |
|---|---|---|---|---|---|
| OmniBuds Pro (`OB-PRO-01`) | `>=2.0.0` | `noise-control.anc` | `SUPPORTED_VOLATILE` | `UNSUPPORTED` | `ANC-FW-2.0` |
| Sony (`WF-1000XM4`) | `>=2.0.0` | `noise-control.anc` | `SUPPORTED_VOLATILE` | `READ_ONLY` | `EVID-SONY-200` |
| Apple (`AirPods Pro 2`) | `>=5B58` | `noise-control.anc` | `SUPPORTED_VOLATILE` | `READ_ONLY` | `EVID-APPLE-5B58` |
| Acme Buds (`AB-01`) | `in [1.0.0, 1.1.0]` | `equalizer.preset` | `SUPPORTED_VOLATILE` | `UNSUPPORTED` | `ACME-EQ-VERIFIED` |
| Generic / Unobserved | `*` (Any) | Telemetry read | `READ_ONLY` | `UNKNOWN` | `GEN-TELEMETRY` |

Note: Capability persistence (`PERSISTENCE_VERIFIED`) is dynamic and requires real reconnect verification; it is never claimed statically from firmware metadata alone.
