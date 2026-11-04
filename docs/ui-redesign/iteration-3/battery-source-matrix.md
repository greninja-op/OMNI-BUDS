# Battery Source Matrix — Iteration 3

Supersedes the Iteration-2 matrix; the picture is unchanged after a
fresh end-to-end audit (see battery-root-cause.md).

| Device | Aggregate | Left | Right | Case | Source | Notes |
|---|---|---|---|---|---|---|
| realme Buds T300 | YES | no | no | no | Android battery-level broadcast | Vendor app shows components via its private protocol — not documented, not integrated |
| JBL Tune 130NC TWS | YES | no | no | no | same broadcast | — |
| CMF Buds | YES | no | no | no | same broadcast | — |
| Redmi Buds 5A | YES | no | no | no | same broadcast | — |
| Sony WI-C100 | YES | n/a | n/a | n/a | same broadcast | Single battery by design |
| boAt Rockerz 450 | YES | n/a | n/a | n/a | same broadcast | Single battery |
| Skullcandy Hesh ANC | YES | n/a | n/a | n/a | same broadcast | Single battery |
| Any other audio device | when broadcast | no | no | no | same broadcast | No broadcast → battery shown as unknown, never estimated |

Component pipeline status: model, bridge contract, presentation,
and UI are implemented and unit-tested with component data; no
production source reports components today. GATT Battery Service
(0x180F/0x2A19) is the leading candidate source — SIG-standard, but
the app has no GATT connection lifecycle and the work cannot be
validated without hardware, so it remains unimplemented by decision,
not by oversight.
