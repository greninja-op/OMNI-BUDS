# Phase 46 — Coroutines, Reactive State & Concurrency Architecture

## 1. Concurrency Policy and Primitives

OmniBuds enforces strict structured concurrency across all portable modules using `kotlinx.coroutines`:

1. **Dispatcher Decoupling**:
   - The shared core contains **zero** hardcoded references to `Dispatchers.Main` or Android-specific loopers.
   - Background tasks execute on caller-provided execution contexts or standard multiplatform dispatchers (`Dispatchers.Default`, `Dispatchers.IO`).
2. **Deterministic State Synchronization**:
   - Shared mutable state is guarded via `kotlinx.coroutines.sync.Mutex` or single-threaded state engine loops.
   - Atomic state transitions are emitted using `StateFlow` and `SharedFlow`.
3. **Structured Lifecycle & Cancellation**:
   - Platform observation flows (`observeAdapterState`, `lifecycleEvents`) are cold.
   - Resource disposal is bound to the collector's coroutine scope. Cancellation automatically unregisters platform hooks and releases handles.
4. **No Orphan Background Jobs**:
   - Long-lived monitoring tasks must be attached to explicit parent jobs.
   - Process teardown triggers structured cancellation across all active sessions.
