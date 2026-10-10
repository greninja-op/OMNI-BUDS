# Phase 46 — Technical Specifications: Kotlin Multiplatform Core & Platform Independence

## 1. Shared Platform Model Specifications

### 1.1 `PlatformType`
```kotlin
package com.omnibuds.core.platform

enum class PlatformType(val technicalName: String) {
    ANDROID("android"),
    LINUX("linux"),
    MACOS("macos"),
    WINDOWS("windows"),
    DESKTOP_GENERIC("desktop-generic"),
    UNKNOWN("unknown");

    val isDesktop: Boolean
    val isMobile: Boolean
}
```
- **Invariants**:
  - `isDesktop` is `true` for `LINUX`, `MACOS`, `WINDOWS`, `DESKTOP_GENERIC`; `false` otherwise.
  - `isMobile` is `true` exclusively for `ANDROID`.
  - `UNKNOWN` returns `false` for both `isDesktop` and `isMobile`.

### 1.2 `PlatformDescriptor`
```kotlin
package com.omnibuds.core.platform

import com.omnibuds.core.common.TransportKind

data class PlatformDescriptor(
    val platformType: PlatformType,
    val osName: String? = null,
    val osVersion: String? = null,
    val architecture: String? = null,
    val apiLevel: Int? = null,
    val candidateTransports: Set<TransportKind> = emptySet(),
    val capabilities: BluetoothPlatformCapabilities = BluetoothPlatformCapabilities.unobserved(),
) {
    val isDesktop: Boolean get() = platformType.isDesktop
    val isMobile: Boolean get() = platformType.isMobile

    companion object {
        fun unobserved(): PlatformDescriptor
    }
}
```
- **Validation**: `apiLevel` must be positive if present; throws `IllegalArgumentException` if `<= 0`.

### 1.3 `PlatformIdentifierSource`
```kotlin
package com.omnibuds.core.platform

interface PlatformIdentifierSource {
    fun generateOperationId(prefix: String? = null): String
    fun generateNonce(): String
}

class DeterministicIdentifierSource(
    private val defaultPrefix: String = "op",
    private var sequenceNumber: Long = 1L,
) : PlatformIdentifierSource {
    fun reset(startSequence: Long = 1L)
}
```
- **Specifications**:
  - `generateOperationId`: Returns `"$prefix-$seq"`, non-blank.
  - `generateNonce`: Returns `"nonce-$seq"`, non-blank.

### 1.4 `PlatformStoragePort`
```kotlin
package com.omnibuds.core.platform

import com.omnibuds.core.common.OperationOutcome

interface PlatformStoragePort {
    suspend fun get(key: String): OperationOutcome<String?>
    suspend fun set(key: String, value: String): OperationOutcome<Unit>
    suspend fun remove(key: String): OperationOutcome<Unit>
    suspend fun contains(key: String): OperationOutcome<Boolean>
    suspend fun clear(): OperationOutcome<Unit>
}

class InMemoryStoragePort : PlatformStoragePort
```
- **Specifications**:
  - Keys must be non-blank. Blank keys are rejected.
  - Returns `OperationOutcome.Success` or structured `OperationOutcome.Failure`.

### 1.5 `PlatformLifecycleState` & `PlatformLifecycleSource`
```kotlin
package com.omnibuds.core.platform

import kotlinx.coroutines.flow.Flow

enum class PlatformLifecycleState {
    FOREGROUND,
    BACKGROUND,
    SUSPENDED,
    TERMINATING;

    val isInteractive: Boolean
}

interface PlatformLifecycleSource {
    val currentState: PlatformLifecycleState
    val lifecycleEvents: Flow<PlatformLifecycleState>
}
```
- **Specifications**:
  - `isInteractive` is `true` only for `FOREGROUND`.

### 1.6 `PlatformDiagnosticSink`
```kotlin
package com.omnibuds.core.platform

import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.state.VerificationLevel

interface PlatformDiagnosticSink {
    fun isEnabled(level: VerificationLevel): Boolean
    fun emit(level: VerificationLevel, tag: String, message: String): OperationOutcome<Unit>
}

object NoOpDiagnosticSink : PlatformDiagnosticSink
```
- **Specifications**:
  - `NoOpDiagnosticSink.isEnabled` returns `false`.
  - `NoOpDiagnosticSink.emit` returns `OperationOutcome.Success(Unit)`.

### 1.7 `PlatformTransportFactory` & `PlatformConnectionSession`
```kotlin
package com.omnibuds.core.platform

import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.common.TransportKind

interface PlatformConnectionSession : PlatformRegistration {
    val transportKind: TransportKind
    val isConnected: Boolean
}

interface PlatformTransportFactory {
    val supportedTransports: Set<TransportKind>
    suspend fun openSession(
        deviceAddress: String,
        kind: TransportKind,
    ): OperationOutcome<PlatformConnectionSession>
}
```

---

## 2. Android Implementation Specifications

### 2.1 `AndroidPlatformDescriptor`
- Package: `com.omnibuds.android.compat`
- Reads `Build.VERSION.SDK_INT`, `Build.VERSION.RELEASE`, and `Build.SUPPORTED_ABIS`.
- Sets `platformType = PlatformType.ANDROID`.

### 2.2 `AndroidPlatformIdentifierSource`
- Package: `com.omnibuds.android.compat`
- Generates UUID-backed operation IDs (`"android-op-${UUID.randomUUID()}"`) and nonces.

### 2.3 `AndroidPlatformLifecycleSource`
- Package: `com.omnibuds.android.lifecycle`
- Implements `PlatformLifecycleSource` backed by `MutableStateFlow<PlatformLifecycleState>`.
