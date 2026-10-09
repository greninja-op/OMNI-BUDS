# Phase 32 — Decisions

## D-32-01: pure policy objects
**Decision:** Platform decisions as pure JVM-testable objects.
**Rationale:** Testable without emulators; no assumptions.

## D-32-02: no new permissions
**Decision:** Manifest unchanged; only BLUETOOTH_CONNECT declared.
**Rationale:** Least privilege; nothing new needed.

## D-32-03: five permission states
**Decision:** Distinguish denied/unavailable/restricted/unknown.
**Rationale:** Each needs different handling.

## D-32-04: audits not redesigns
**Decision:** Tile/notification/widget/lifecycle audited, not rebuilt.
**Rationale:** Existing implementations already correct.

## D-32-05: honest gaps
**Decision:** Emulator/Robolectric/OEM marked NOT_RUN/UNVERIFIED.
**Rationale:** No fake verification claims.
