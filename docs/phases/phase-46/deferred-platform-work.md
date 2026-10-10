# Phase 46 — Deferred Platform Work & Future Roadmaps

## 1. Strictly Deferred Items

The following items were explicitly excluded from Phase 46 in accordance with architectural boundaries and phase directives:

| Deferred Capability | Designated Phase | Rationale |
|---|---|---|
| **Desktop Bluetooth Layer** | **Phase 47** | Implementation of Linux BlueZ, macOS CoreBluetooth, and Windows Bluetooth transport adapters belongs in Phase 47. |
| **Desktop Storage & Lifecycle**| **Phase 48** | Desktop persistence (SQLite/flat-file), daemon lifecycle, and system integration belong in Phase 48. |
| **User Interface (UI)** | **Phases 49–51** | Compose Multiplatform UI, screen models, and desktop/mobile user interfaces are scheduled for Phases 49–51. |
| **Physical Hardware Verification**| **Phase 52** | Physical testing on real Android devices and real earbud hardware is deferred until the application is fully assembled in Phase 52. |

---

## 2. Integrity and Truthfulness Guarantees

- No desktop transport is claimed as implemented in Phase 46.
- The shared core defines the platform seams (`PlatformTransportFactory`, `PlatformDescriptor`, `PlatformLifecycleSource`) without faking desktop implementations.
- All verification was conducted using offline, automated unit tests and deterministic fixtures.
