# Phase 24 — Global State Model

Per-device `GlobalDeviceState` with typed submodels:

| Submodel | Source | Notes |
|---|---|---|
| `IdentityState` | Fingerprinting / identity evidence | Unknown / Ambiguous / Identified |
| `ConnectionState` | Transport/session observation | Disconnected / Connecting / Connected / Disconnecting |
| `ProtocolState` | Protocol resolver | Unresolved / Ambiguous / Resolved / Incompatible |
| `CapabilityState` | Capability discovery | NotDiscovered / Discovering / Ready / Failed |
| `FeatureState` | Feature engine + config | desired / executing / acknowledged / observed |
| `BatteryState` | Battery engine | Unknown / Known(level, charging) |
| `AudioState` | Audio transport engine | route + codec observations |
| `ConfigurationState` | Phase 17 config repo | desired values |
| `PersistenceState` | Phase 18 verification | NotVerified / Verifying / Verified / Failed |
| `VendorFeatureState` | Vendor extensions | values + extension ids |

Every externally observed value is an `ObservedValue<T>` with
`ObservationProvenance` and `Freshness`. The snapshot's
`publishedAtMillis` is engine-side, not an observation timestamp.
