# Phase 43 — SDK Versioning Policy

---

## 1. Semantic Versioning Rules

The Community Protocol SDK uses Semantic Versioning (`MAJOR.MINOR.PATCH`):

- **MAJOR (breaking changes):**
  - Modification, removal, or renaming of abstract methods in `CommunityProtocolAdapter`.
  - Breaking changes to core contracts (`DeviceFingerprint`, `FeatureId`, `CapabilityState`).
  - Strict rejection: The host application deterministically rejects packages where `sdkVersion.major != SdkVersion.CURRENT.major`.

- **MINOR (backward-compatible additions):**
  - Additive helper functions, new optional fields with defaults, or new `SdkOperationResult` variants handled gracefully by host fallbacks.

- **PATCH (backward-compatible fixes):**
  - Bug fixes, documentation updates, internal performance optimizations in test fixtures or validator rules.

---

## 2. Compatibility Matrix

| SDK Version | Host Version Support | Backward Compatible |
|---|---|---|
| `1.0.0` | 1.x.x | Yes |
| `1.1.0` | 1.x.x | Yes |
| `2.0.0` | 2.x.x only | No (requires migration) |
