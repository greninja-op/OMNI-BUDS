# Phase 46 — Persistence Boundaries & Storage Abstraction

## 1. Storage Architecture and Data Classification

OmniBuds clearly separates transient observations from persistent user data:

| Data Category | Mutability | Lifetime | Storage Location | Contract |
|---|---|---|---|---|
| **Saved Devices** | Explicit user action | Persistent | Local Storage | `DeviceRepository` |
| **Verified Configurations** | Explicit user action | Persistent | Local Storage | `ConfigurationRepository` |
| **Hardware Observations** | Ephemeral | Active session | Memory (`GlobalDeviceState`) | In-memory only |
| **Firmware Rules** | Static / Read-only | Application version | Resource JSON | `FirmwareCompatibilityResolver` |
| **Diagnostic Events** | Ephemeral / Opt-in | Bounded buffer | Memory / Optional log | `OmniBudsLogger` |

---

## 2. Invariants Preserved
1. **Scanning is Never Saving**: Discovering or connecting to a device never writes to disk. Only an explicit user command ("Add to My Devices") persists a device.
2. **Local Deletion Guarantee**: Forgetting a device removes all local data (credentials, keys, cache) without attempting unauthorized over-the-air writes.
3. **No Database Leaks in Core**: Storage implementations (Android DataStore, SQLite, desktop flat files) sit behind `PlatformStoragePort` and `DeviceRepository`.
