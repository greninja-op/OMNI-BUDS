# Phase 46 — Platform Abstraction Policy & Contracts

This document specifies the purpose, ownership, lifecycle, thread-safety, cancellation behavior, error semantics, and required guarantees for each platform abstraction introduced in Phase 46.

---

## 1. `TimeProvider`
- **Purpose**: Supplies current epoch milliseconds for time-to-live evaluations (e.g. firmware observation freshness, session timeouts) without leaking `java.time` or `System.currentTimeMillis()`.
- **Ownership**: Injected into core coordinators and repositories from the platform composition root.
- **Lifecycle**: Singleton spanning the host process lifetime.
- **Thread-Safety**: Must be lock-free and thread-safe.
- **Cancellation**: Non-suspending, immediate return.
- **Error Semantics**: Returns `null` when wall time is unavailable; never defaults to 0.
- **Required Guarantees**: Monotonically increasing or accurate real-time values where supported.
- **Platform Limitations**: OS NTP adjustments may skew readings; callers must handle non-monotonic jumps safely.

---

## 2. `PlatformIdentifierSource`
- **Purpose**: Generates stable, non-blank, correlated operation identifiers (`operationId`) and nonces for audit logs, telemetry, and replay protection.
- **Ownership**: Platform adapter instance injected into operation dispatchers and repositories.
- **Lifecycle**: Process lifetime.
- **Thread-Safety**: Must be safe under high-frequency concurrent invocations.
- **Cancellation**: Non-suspending, non-blocking.
- **Error Semantics**: Guarantees non-blank string returns.
- **Required Guarantees**: Uniqueness across all calls within the session.
- **Platform Limitations**: Android uses `java.util.UUID`; deterministic test suites use `DeterministicIdentifierSource`.

---

## 3. `PlatformStoragePort`
- **Purpose**: Asynchronous key-value record persistence to store saved devices and verified user configurations without coupling core to Android DataStore or desktop SQLite.
- **Ownership**: Owned by host application, passed to repository implementations.
- **Lifecycle**: Open during application lifetime; safely flushed on shutdown.
- **Thread-Safety**: Thread-safe per key; atomic writes.
- **Cancellation**: Suspending calls respect coroutine cancellation without leaving corrupted partial files.
- **Error Semantics**: Returns `OperationOutcome.Success` or structured `OperationOutcome.Failure`. Never throws unhandled platform exceptions.
- **Required Guarantees**: Atomicity per record; data survives process restarts.
- **Platform Limitations**: Android sandboxes storage per UID; desktop stores in standard user directories (`~/.local/share/omnibuds` or `%APPDATA%`).

---

## 4. `PlatformDiagnosticSink`
- **Purpose**: Common telemetry and log event emission to host system logging facilities.
- **Ownership**: Diagnostic coordinator.
- **Lifecycle**: Process lifetime.
- **Thread-Safety**: Non-blocking and thread-safe.
- **Cancellation**: Fire-and-forget or non-blocking buffer.
- **Error Semantics**: Swallows logging failures gracefully; never breaks core logic.
- **Required Guarantees**: Mandatory redaction of hardware identifiers before emission.
- **Platform Limitations**: Android Logcat truncates messages exceeding 4KB.

---

## 5. `PlatformLifecycleSource`
- **Purpose**: Bridges host application lifecycle events (foreground, background, shutdown) into core background coordinators.
- **Ownership**: Platform application lifecycle monitor.
- **Lifecycle**: Process lifetime.
- **Thread-Safety**: Backed by coroutine `StateFlow` / `Flow`.
- **Cancellation**: Cold flow unsubscribes cleanly when the collector scope cancels.
- **Error Semantics**: Emits typed `PlatformLifecycleState` values.
- **Required Guarantees**: Deterministic transition ordering.
- **Platform Limitations**: Desktop daemons lack foreground/background UI transitions and remain in active/background states.

---

## 6. `PlatformTransportFactory`
- **Purpose**: Seam for creating physical transport connection sessions (`PlatformConnectionSession`) without referencing Android BluetoothGatt/BluetoothSocket or desktop D-Bus sockets.
- **Ownership**: Transport manager.
- **Lifecycle**: Process lifetime.
- **Thread-Safety**: Session creation is thread-safe; session operations are serialized per connection.
- **Cancellation**: Cancelling the session job terminates the underlying socket/channel immediately.
- **Error Semantics**: Returns structured `OperationOutcome.Failure` on failure; never throws uncaught exceptions.
- **Required Guarantees**: Idempotent disposal via `PlatformRegistration.dispose()`.
- **Platform Limitations**: Desktop stacks (BlueZ) require DBus daemon permissions; Android requires `BLUETOOTH_CONNECT`.
