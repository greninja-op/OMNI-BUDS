# Phase 24 — State Source Precedence

Property-specific precedence; no universal rule.

## Rules

1. **Desired configuration** — Phase 17 config repository. Establishes
   what the user wants, never what the device applies.
2. **Device-observed feature values** — valid read-back or documented
   observation source. A stale read-back never overrides a newer valid
   observation.
3. **Acknowledgements** — record what the protocol guarantees; not
   equivalent to read-back.
4. **Battery** — platform or verified protocol observations only.
5. **Audio route** — Android audio-device observation APIs; does not prove
   proprietary ANC mode.
6. **Codec state** — Phase 11–14 observation models.
7. **Persistence** — Phase 18 verification results only.
8. **Capabilities** — capability discovery + verified protocol metadata;
   a protocol schema does not prove device support.
9. **Identity** — fingerprinting evidence; ambiguous stays ambiguous.

## Conflicts

Two equally credible sources in conflict → both preserved; the snapshot
exposes the conflict (e.g. `IdentityState.Ambiguous`). An unknown source
never overwrites a verified value with a fabricated default.
