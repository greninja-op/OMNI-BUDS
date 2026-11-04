# Battery Source Matrix — Iteration 2

What each device can actually report today, through which verified
source. "Verified" = the code path exists and is exercised; nothing
here is assumed from marketing specifications.

| Device | Aggregate | Left | Right | Case | Source | Live/Cached | Limitations |
|---|---|---|---|---|---|---|---|
| realme Buds T300 | YES | no | no | no | Android BATTERY_LEVEL_CHANGED broadcast | Live when broadcast fires; last-known when disconnected | No per-component exposure via standard Android; vendor protocol not integrated |
| JBL Tune 130NC TWS | YES | no | no | no | same broadcast | same | same |
| CMF Buds | YES | no | no | no | same broadcast | same | same |
| Redmi Buds 5A | YES | no | no | no | same broadcast | same | same |
| Sony WI-C100 | YES | n/a | n/a | n/a | same broadcast | same | Single-device battery by design (neckband) |
| boAt Rockerz 450 | YES | n/a | n/a | n/a | same broadcast | same | Single battery (over-ear) |
| Skullcandy Hesh ANC | YES | n/a | n/a | n/a | same broadcast | same | Single battery (over-ear) |
| Any other audio device | when the system broadcasts it | no | no | no | same broadcast | same | Devices whose firmware does not trigger the broadcast show no battery — represented as unknown, never estimated |

## Component sources: status
- **Standard Android**: exposes one device-level value only (the
  broadcast the app already uses). No public API exposes per-bud or
  case values (documented in the codebase since Phase 16:
  `AndroidBatteryObservationSource` — getBatteryLevel() is @hide).
- **Vendor protocols**: none integrated. The adapter registry ships
  without a battery-capable adapter; no verified byte-level protocol
  documentation was available for the seeded brands (Phase 19
  finding, unchanged). No UUIDs or frames were guessed.
- **GATT Battery Service (0x180F / 0x2A19)**: a Bluetooth SIG
  standard (not a guess), and the most promising next source — but
  typically device-level, and the app has no GATT connection
  lifecycle today. NOT implemented in this iteration: adding an
  unvalidatable connection stack would violate the project's
  evidence rules. Recorded as the first candidate when on-device
  validation is available.
- **AirPods-family observations**: the read-only scope explicitly
  declares per-bud and case battery Unsupported on Android; its
  aggregate helper was corrected so it can never attribute an
  aggregate to components (regression-tested).

## Pipeline readiness
The domain model, bridge contract, presentation mapping, and
three-component UI are implemented and tested with component data —
so the day a verified per-component source is integrated, left /
right / case light up end-to-end with no further model work.
