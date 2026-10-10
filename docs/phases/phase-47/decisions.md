# Phase 47 — Architectural Decisions Record (ADR)

## ADR-P47-001: Desktop Bluetooth Layer Placement in `:core`

### Context
Phase 46 established platform-independent abstraction seams in `core/platform/` while keeping the repository source layout as standard Kotlin (`src/main/kotlin`). For Phase 47, we needed to introduce desktop Bluetooth contracts without introducing native OS runtime dependencies into `:core` or prematurely creating a fragmented `:platform:desktop` module that cannot compile in the offline sandbox environment without external C/native bindings.

### Decision
Place portable desktop Bluetooth interfaces and contracts under `com.omnibuds.core.platform.desktop` inside the `:core` module. Concrete OS boundaries (`LinuxBlueZDesktopAdapter`, `MacOsCoreBluetoothDesktopAdapter`, `WindowsWinRtDesktopAdapter`) represent the platform contracts and return structured `OperationOutcome` values, while concrete low-level native sockets/D-Bus bindings are left for the desktop packaging phase (Phases 48+).

### Consequences
- Downward dependency direction is strictly preserved (Layer 1).
- Portable core contracts can be shared cleanly with future Compose Desktop UI (Phase 48).
- The entire layer is verified offline by pure Kotlin tests without breaking `DependencyDirectionTest`.

---

## ADR-P47-002: Strict Tri-State Connection Axis Model

### Context
Operating system settings often show a Bluetooth audio headset as "Connected" (via A2DP or HFP). However, this does not mean the application holds an open RFCOMM or GATT socket, nor that the earbud supports an open vendor control protocol.

### Decision
`DesktopConnectionSessionState` models connection as three orthogonal, non-interchangeable states:
1. `isConnectedAtOsLevel`: System audio link status.
2. `hasActiveTransportSession`: Application GATT/RFCOMM channel status.
3. `hasVendorProtocolSession`: Authenticated vendor protocol status.

Control commands are gated behind `isVendorControllable`, which strictly requires both an active transport session and a verified vendor protocol session.

---

## ADR-P47-003: Pure Isolation of Test Doubles to Test Source Set

### Context
`DependencyDirectionTest.productionSourcesDefineNoTestDoubles()` forbids defining any class starting with `Fake`, `Mock`, `Stub`, or `Test` in main sources.

### Decision
The simulation adapter (`DeterministicSimulatedDesktopAdapter`, `SimulatedDesktopDiscoverySession`, `SimulatedDesktopTransportSession`) is confined exclusively to `core/src/test/kotlin/com/omnibuds/core/platform/desktop/`. Production sources contain only true domain contracts and truthful platform boundaries.
