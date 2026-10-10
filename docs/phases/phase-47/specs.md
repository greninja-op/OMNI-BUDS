# Phase 47 — Desktop Bluetooth Layer Specifications

## 1. Type & Interface Contracts

### 1.1 `DesktopBluetoothAvailability`
```kotlin
enum class DesktopBluetoothAvailability(val technicalName: String) {
    AVAILABLE("available"),
    UNAVAILABLE("unavailable"),
    DISABLED("disabled"),
    PERMISSION_REQUIRED("permission-required"),
    PERMISSION_DENIED("permission-denied"),
    UNSUPPORTED("unsupported"),
    INITIALIZING("initializing"),
    UNKNOWN("unknown");

    val isUsable: Boolean
    val isAuthorizationIssue: Boolean
    val isIndeterminate: Boolean
}
```

### 1.2 `DesktopDiscoveredDevice`
```kotlin
data class DesktopDiscoveredDevice(
    val platformIdentifier: String,
    val name: String? = null,
    val bluetoothAddress: String? = null,
    val observedTransports: Set<TransportKind> = emptySet(),
    val rssiDbm: Int? = null,
    val isPaired: Boolean = false,
    val isConnectedAtOsLevel: Boolean = false,
    val observedAtEpochMillis: Long? = null,
    val backendSource: String = "desktop-backend",
) {
    val safeLabel: String
}
```

### 1.3 `DesktopBluetoothAdapter`
```kotlin
interface DesktopBluetoothAdapter {
    val platformDescriptor: PlatformDescriptor
    suspend fun checkAvailability(): DesktopBluetoothAvailability
    fun observeAvailability(): Flow<DesktopBluetoothAvailability>
    suspend fun capabilities(): OperationOutcome<BluetoothPlatformCapabilities>
    suspend fun startDiscovery(): OperationOutcome<DesktopDeviceDiscoverySession>
    suspend fun getKnownDevices(): OperationOutcome<List<DesktopDiscoveredDevice>>
    suspend fun openTransportSession(
        deviceIdentifier: String,
        transportKind: TransportKind,
    ): OperationOutcome<DesktopTransportSession>
}
```

### 1.4 `DesktopConnectionSessionState`
```kotlin
data class DesktopConnectionSessionState(
    val platformIdentifier: String,
    val isConnectedAtOsLevel: Boolean,
    val activeTransportKind: TransportKind? = null,
    val hasActiveTransportSession: Boolean = false,
    val hasVendorProtocolSession: Boolean = false,
    val sessionStartedEpochMillis: Long? = null,
) {
    val isVendorControllable: Boolean
}
```

---

## 2. Error Categorization & Retry Policy

Operations map failures strictly to `OmniBudsError`:
- Discovery start failure when disabled/unavailable: `OmniBudsErrorCategory.ADAPTER_UNAVAILABLE`
- Discovery start when permission denied: `OmniBudsErrorCategory.PERMISSION_DENIED`
- Duplicate discovery session: `OmniBudsErrorCategory.INVALID_STATE`
- Unsupported transport kind: `OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE`
- Duplicate concurrent transport session on single device: `OmniBudsErrorCategory.INVALID_STATE`
