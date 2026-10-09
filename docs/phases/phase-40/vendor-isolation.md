# Phase 40 — Vendor Isolation

## Guarantees (tested)

- Integrations register independently.
- Each resolves only within its identity scope.
- Protocol definitions are scoped per integration.
- Features register under their own integration (Phase 23
  namespaces).
- Device state is per-device (Phase 24 aggregators).
- Persistence is per-device (Phase 18).
- Adapter failures don't break the registry.

## Test adapters

All multi-vendor tests use clearly synthetic adapters
(`synth.a`, `synth.b`). Synthetic is never presented as real
vendor evidence.
