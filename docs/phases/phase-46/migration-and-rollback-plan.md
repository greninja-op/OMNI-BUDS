# Phase 46 — Migration and Rollback Plan

## 1. Migration Sequence

The platform independence transition follows an incremental, backwards-compatible path:

1. **Phase 46 (Completed)**:
   - Establish Layer 1 platform abstraction contracts in `:core/src/main/kotlin/com/omnibuds/core/platform/`.
   - Update Android platform adapters to implement these contracts.
   - Register `kotlin-multiplatform` in version catalog.
   - Validate full core and android test suites (1937 tests passed).
2. **Phase 47 (Next)**:
   - Introduce desktop Bluetooth transport layer (`:platform:desktop`) implementing `PlatformTransportFactory`, `PlatformDescriptor`, and `BluetoothPlatform` on Linux (BlueZ) and macOS.
3. **Phase 48**:
   - Establish desktop storage, process lifecycle, and background service execution.

---

## 2. Rollback Procedures

Should any platform compatibility regression occur:
1. Reverting the new platform abstraction files under `core/platform` and `platform/android/compat` restores the exact pre-Phase 46 state.
2. The default parameter `platformType = PlatformType.UNKNOWN` on `BluetoothPlatformCapabilities` ensures that reverting the parameter does not break binary compatibility.
