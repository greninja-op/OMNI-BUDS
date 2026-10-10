# Phase 49 — Android UI Architectural Decisions

## ADR-49-01: Pure-Kotlin Presentation Layer Decoupled from Jetpack Compose

- **Context**: The build environment is an offline, headless container running Kotlin 2.0.21. The Compose compiler plugin requires online Maven dependencies matching the compiler version, which are unavailable offline. Furthermore, running headless Compose UI instrumentation tests without an Android device or emulator is impossible.
- **Decision**: Structurally bifurcate the Android presentation layer into a 100% pure-Kotlin domain presentation package (`com.omnibuds.android.presentation.*`) and a thin Compose rendering package (`com.omnibuds.android.ui.compose.*`). All business logic, view models, mutable state management, accessibility semantics, and cross-surface coordinators reside in `presentation/` and are fully testable on the standard JVM with JUnit 5 and coroutines-test.
- **Consequences**: Enables deterministic, rapid, 100% offline regression testing with 0% risk of untested business logic. The Compose UI layer acts strictly as a declarative, thin wrapper around immutable `StateFlow` models.

---

## ADR-49-02: Unified Surface Coordinator Across Android UI, Quick Settings, Notifications, and Widgets

- **Context**: OmniBuds implements multiple user-facing Android surfaces: the main app UI, Quick Settings tiles (Phase 25), persistent media-style notifications (Phase 26), and app widgets (Phase 27). Without central coordination, user selection in the main app could diverge from the device displayed on the home screen or notification drawer.
- **Decision**: Introduce `AndroidSurfaceCoordinator`, observing `GlobalDeviceStateRepository`. When a user navigates to a device in the UI, `selectDevice(id)` publishes to all registered surfaces simultaneously. If no device is explicitly selected, the coordinator safely resolves to the single active device if unambiguous, or `null` if zero or multiple devices are connected.
- **Consequences**: Guarantees zero split-brain behavior across OS surfaces. Preserves battery and system resources by harmonizing background refresh triggers.

---

## ADR-49-03: Coroutine Test Scheduling with `runCurrent()` on `backgroundScope`

- **Context**: Several view models launch long-lived StateFlow collectors in `backgroundScope`. During unit tests, using `testScheduler.advanceUntilIdle()` caused hangs or missed conflated updates when combined with active collectors waiting on unbuffered flows.
- **Decision**: Ensure fake test providers configure `MutableSharedFlow(replay = 1, extraBufferCapacity = 64)` and utilize explicit step-wise scheduling with `testScheduler.runCurrent()` when emitting events and verifying intermediate states.
- **Consequences**: Eliminates flake, deadlocks, and scheduler starvation while accurately testing asynchronous event propagation.

---

## ADR-49-04: Non-Fabrication Principle for Battery and Audio Codecs

- **Context**: Android framework APIs may not expose battery levels for certain components (e.g. charging case on generic BLE devices) or may hide active audio codec parameters on restricted ROMs.
- **Decision**: In strict accordance with the non-negotiable principle that OmniBuds never simulates hardware, unobserved battery components are set to `null` rather than a synthetic `0%` or placeholder. Unobservable codecs are flagged with `isObservable = false` and an honest platform limitation explanation.
- **Consequences**: Eliminates user confusion and maintains strict architectural integrity.
