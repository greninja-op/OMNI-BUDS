# Phase 42 — Deferred Hardware Verification (Phase 52)

All physical tests deferred. Prerequisites: real AirPods, Android
phone, Phase 52 authorization.

1. **Family identification** — pair AirPods; confirm company-ID +
   audio-class fingerprint matches family, exact model stays
   unresolved.
2. **Non-match** — confirm iPhone/Mac fingerprints are Ambiguous,
   not matched.
3. **Metadata observations** — connection state, name, profiles
   match Android's own Bluetooth settings.
4. **Battery accuracy** — compare OmniBuds battery (null or
   Android value) against Android's reported value; confirm no
   fabricated per-bud/case values.
5. **No audio interference** — media playback unaffected while
   observations run.
6. **Authorization** — confirm no write path exists for the
   adapter; ambiguous devices stay read-only.
7. **Diagnostics** — sanitized export contains no PII.

Safety: no writes, no pairing automation, no protocol commands.
