# Phase 47 — Desktop Bluetooth Layer Requirements

## 1. Overview & Scope

Phase 47 implements the desktop Bluetooth integration boundary for OmniBuds. In accordance with the foundational architectural principle that **OmniBuds never simulates a hardware capability**, this phase establishes a modular desktop layer that allows OmniBuds to discover desktop Bluetooth capabilities, observe device availability, manage connection sessions where host APIs permit it, and expose truthful device information to the shared Kotlin Multiplatform core.

Desktop operating systems (Linux, macOS, Windows) differ substantially in Bluetooth API maturity, permission models, supported transports, and vendor protocol accessibility. These differences are represented explicitly rather than concealed behind inaccurate cross-platform abstractions.

---

## 2. Requirements Matrix

| Requirement ID | Description | Rationale | Dependencies | Priority | Acceptance Criteria | Verification Method | Implementation Status |
|---|---|---|---|---|---|---|---|
| **REQ-47-01** | Explicit Desktop Bluetooth Availability | Operating system adapter presence, power state, and permissions must be distinguished without guessing. | Phase 46 `PlatformDescriptor` | P0 | Distinguish `AVAILABLE`, `UNAVAILABLE`, `DISABLED`, `PERMISSION_REQUIRED`, `PERMISSION_DENIED`, `UNSUPPORTED`, `INITIALIZING`, `UNKNOWN`. | Unit tests in `DesktopBluetoothLayerTest` | Implemented |
| **REQ-47-02** | Desktop Discovered Device Observation Model | Preserve observations with exact platform identifier, name, address, RSSI, and provenance. | Phase 46 `PlatformType` | P0 | Platform identifier non-blank, sensitive address redacted in display labels, missing fields kept null. | Unit tests in `DesktopBluetoothLayerTest` | Implemented |
| **REQ-47-03** | Deterministic Desktop Discovery Lifecycle | Discovery sessions must support start, stop, cancellation, deduplication, and cleanup. | `DesktopDiscoveredDevice` | P0 | Stop releases resources idempotently; late callbacks dropped after stop; adapter disabling halts scan. | Unit tests with `DeterministicSimulatedDesktopAdapter` | Implemented |
| **REQ-47-04** | Separation of OS Connection vs Transport Session vs Vendor Protocol | Distinguish OS paired/connected link from application transport session and vendor control session. | Phase 46 `PlatformConnectionSession` | P0 | `DesktopConnectionSessionState` models 3 distinct axes; `isVendorControllable` requires both transport & protocol. | Unit tests in `DesktopBluetoothLayerTest` | Implemented |
| **REQ-47-05** | Concurrency Guard & Duplicate Session Prevention | Prevent duplicate active transport sessions for the same logical device identifier. | `DesktopBluetoothAdapter` | P0 | Opening second concurrent transport session returns `INVALID_STATE` failure. | Unit tests in `DesktopBluetoothLayerTest` | Implemented |
| **REQ-47-06** | Truthful Platform Adapters & Boundaries | Provide honest adapter implementations for Generic Desktop, Linux (BlueZ), macOS (CoreBluetooth), and Windows (WinRT). | `PlatformType`, `PlatformDescriptor` | P0 | Adapters report exact platform type, candidate transports, permissions, and offline limitations honestly. | Unit tests in `DesktopBluetoothLayerTest` | Implemented |
| **REQ-47-07** | Deterministic Offline Test Double | Isolated test double in test sources to validate discovery, errors, and failure injection without physical hardware. | Core test suite | P0 | Zero test doubles in `src/main/kotlin`; verified by `DependencyDirectionTest`. | Full test suite execution | Implemented |
| **REQ-47-08** | Zero Audio Media Interception or DSP Simulation | No media audio capture, decoding, or fake software ANC/DSP in desktop layer. | Master Context | P0 | Enforced by architecture checks; no audio routing manipulation. | Architecture & regression suite | Implemented |
