# Phase 10 — Audio Transport Engine: Risk Register

## RISK-P10-001 — Stale profile-proxy readings mislead reconciliation

- **Likelihood:** Medium | **Impact:** Medium
- **Description:** `getProfileProxy` binds are async; a slow binder yields UNKNOWN, and a cached
  proxy could report a state that changed mid-read.
- **Mitigation:** Session-based reads (bind→read→release per call); 800ms bind timeout → UNKNOWN;
  30s stale-device rule; conflicts preserve UNKNOWN rather than guessing.
- **Residual:** A rapidly flapping profile could oscillate the snapshot; accepted — the
  snapshots are truthful at each read.

## RISK-P10-002 — LE Audio misread as A2DP (or vice versa)

- **Likelihood:** Low | **Impact:** High
- **Description:** Treating LE Audio as "an A2DP codec" would corrupt codec-phase foundations.
- **Mitigation:** Separate transport kind, separate profile state, separate guarded handle;
  `PhaseTenScopeTest` and KDoc forbid the conflation; capability gate on API 33+.
- **Residual:** None known.

## RISK-P10-003 — Callback leaks on teardown races

- **Likelihood:** Low | **Impact:** Medium
- **Description:** `AudioDeviceCallback` or profile proxies surviving engine stop.
- **Mitigation:** Idempotent `AutoCloseable`; `stop()` cancels and joins the scope; proxies
  never held across reads; deterministic unregistration tested with fakes.
- **Residual:** The system handle's real unregistration is hardware-verified later.

## RISK-P10-004 — Permission revocation mid-observation

- **Likelihood:** Low | **Impact:** Low
- **Description:** `BLUETOOTH_CONNECT` revoked while observing → `SecurityException` on proxy reads.
- **Mitigation:** Source checks standing before reads; handle catches `SecurityException` →
  unknown readings; engine keeps last good snapshot + diagnostic.
- **Residual:** None.

## RISK-P10-005 — Scope creep into codec control or routing

- **Likelihood:** Medium | **Impact:** High
- **Description:** "Just one small codec query" or "just expose the route setter" erodes the
  observation-only boundary.
- **Mitigation:** `PhaseTenScopeTest` machine-checks the vocabulary bans; the seam has no write
  methods by construction; forbidden list in specs.md §8.
- **Residual:** Reviewer vigilance on future phases.

## RISK-P10-006 — Android 14/15 behavior changes

- **Likelihood:** Medium | **Impact:** Low
- **Description:** LE Audio default routing (14) and broadcast audio (15, `TYPE_BLE_BROADCAST`)
  introduce surfaces the engine has not seen.
- **Mitigation:** Unknown device types map to `UNKNOWN`, never to a guessed transport;
  reconciliation tolerates device-without-profile; research captured in specs.md §6.
- **Residual:** Revisit when targeting API 35+ behavior changes.

## RISK-P10-007 — `VerifyError` on API < 33 from LE Audio references

- **Likelihood:** Low | **Impact:** High
- **Description:** Class loading `BluetoothLeAudio` references on older runtimes.
- **Mitigation:** ADR-P10-005 (isolated class, guard + init check); `BluetoothProfile.LE_AUDIO`
  constant only touched on guarded paths (compile-time inlined int).
- **Residual:** Needs an API-26–32 device to prove the negative; deferred with hardware testing.

## RISK-P10-008 — Snapshot consumers misread UNKNOWN as "off"

- **Likelihood:** Medium | **Impact:** Medium
- **Description:** A future UI or phase reads `UNKNOWN`/`null` as "disconnected"/"nothing playing".
- **Mitigation:** Normative semantics in specs.md §1; KDoc on every UNKNOWN-bearing field;
  `activeTransport = null` documented as "unreported, not absent".
- **Residual:** Consumer education in Phase 11+.
