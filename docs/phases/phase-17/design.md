# Phase 17 — Design: Persistent Configuration Engine

## Architecture

```
ConfigurationValue (Phase 9, core.config)
         ↓
DeviceConfiguration / GlobalConfiguration (Phase 17)
         ↓
ConfigurationValidator → ConfigurationEngine → ConfigurationStorage
         ↓                      ↓
ApplicationEligibility    InMemory (tests) / File (Android)
```

## New types (`com.omnibuds.core.configuration`, layer 5)

| Type | Role |
|---|---|
| `DeviceConfigurationKey` | Fingerprint-derived device key |
| `DeviceConfiguration` / `GlobalConfiguration` | Immutable configs |
| `ConfigurationRepository` | Read/write/reset contract |
| `DeviceConfigurationResult` / `ConfigurationWriteResult` | Explicit outcomes |
| `ConfigurationStorage` | Low-level KV interface |
| `InMemoryConfigurationStorage` | Test storage |
| `ConfigurationValueJson` | Pure-Kotlin JSON codec |
| `ConfigurationCodec` | Config envelope codec |
| `ConfigurationValidator` | Pre-persistence validation |
| `ConfigurationMigration` / `MigrationRegistry` | Schema evolution |
| `ConfigurationEngine` | Orchestrator + Flow |
| `ApplicationEligibility` | Capability-aware gating |

## Android (`com.omnibuds.android.bluetooth.storage`)

`FileConfigurationStorage`: atomic temp+rename writes, sanitized keys.
Core never touches `java.io`.

## Key decisions

1. **Saved ≠ applied.** Eligibility gating; scope tests ban claim vocabulary.
2. **Pure-Kotlin JSON.** No serialization framework; deterministic format.
3. **Per-device mutexes.** Independent devices never block each other.
4. **Future schemas throw.** Never silently overwrite unknown versions.
5. **File storage in Android module.** Core stays JVM-free (architecture test).
