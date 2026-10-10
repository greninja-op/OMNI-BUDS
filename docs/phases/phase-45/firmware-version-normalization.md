# Phase 45 — Firmware Version Normalization & Parsing

## 1. Principles of Firmware Version Parsing

1. **Preserve Raw Values**: The original string returned by the earbud is always retained in `rawValue`.
2. **Never Invent Defaults**: An unread version is never defaulted to `0.0.0` or empty strings. It is represented as `FirmwareVersion.Unknown`.
3. **Structured Scheme Detection**:
   - Date formats (`YYYY.MM.DD`, `YYYY-MM-DD`, `YYYYMMDD`) are detected with calendar sanity checks (years 2000–2099, months 1–12, days 1–31) and parsed as `DateBased`.
   - Semantic versions (`major.minor.patch` with optional `preRelease`) are parsed as `Semantic`. SemVer major versions $\ge 2000$ are rejected from semantic parsing to prevent collisions with calendar years.
   - Alphanumeric build tokens (e.g. `4E71`, `5B58`, `6A301`) are parsed as `AlphanumericBuild` with generation, train letter, and sequence.
   - Monotonic build numbers (`build-1050`, `rev-55`, `1024`) are parsed as `BuildNumber`.
   - Freeform strings that do not match known schemes are retained as `Opaque`.
4. **Input Sanitization**:
   - Maximum raw length is bounded to 128 characters.
   - Strings containing control characters (ASCII < 32 or 127) are rejected by policy as `REJECTED_BY_POLICY`.
