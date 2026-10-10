# Phase 45 — Firmware Identity and Observation Model

## 1. Separation of Concerns
Firmware identity describes an ephemeral software/ROM build deployed onto an earbud, not the device's persistent hardware identity.
- **Device Identity**: Represents the physical or cryptographic unit (Bluetooth address, serial number, stable device fingerprint key).
- **Firmware Version**: May change across update cycles without altering `DeviceIdentity`.
- **Protocol Version**: Defines the framing, opcode schema, and wire formats supported by an adapter.
- **Application & SDK Versions**: Evolve independently of the connected device.

## 2. Firmware Observation Data Model
An observation encapsulates:
```kotlin
data class FirmwareObservation(
    val deviceId: String,
    val rawValue: String?,
    val version: FirmwareVersion,
    val source: FirmwareObservationSource,
    val observedAtMs: Long,
    val verificationLevel: VerificationLevel,
    val hardwareRevision: String? = null,
    val protocolScope: String? = null,
    val validationState: FirmwareValidationState = FirmwareValidationState.VALID,
    val limitations: List<String> = emptyList(),
)
```

## 3. Observation Provenance Sources
1. `DEVICE_DIS_AUTHORITATIVE`: Read directly from standard Bluetooth Device Information Service (GATT 0x2A26).
2. `VENDOR_TELEMETRY`: Read via vendor-specific protocol status or query payload.
3. `PERSISTED_CACHE`: Loaded from a previously verified persistent store.
4. `ADVERTISEMENT_INFERRED`: Parsed from broadcast service data or manufacturer records.
5. `TEST_FIXTURE`: Injected by automated unit test or synthetic double.
6. `USER_MANUAL_ENTRY`: Input provided by user; explicitly forbidden from enabling mutating operations.
7. `UNKNOWN`: Unobserved or missing.
