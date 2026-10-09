# Phase 44 — Version Domains Specification

---

## 1. Domain Separation Overview

In OmniBuds, the following six version domains are strictly isolated. They must never be conflated, aliased, or compared against one another:

```
+-----------------------------------------------------------------------------------+
| 1. APPLICATION VERSION (OmniBuds App Build, e.g. v1.2.0 build 44)                  |
|    - Independent of all device, protocol, and adapter versions.                   |
+-----------------------------------------------------------------------------------+
| 2. COMMUNITY PROTOCOL SDK VERSION (SdkVersion, e.g. 1.0.0)                         |
|    - Governs the developer-facing extension API contract.                        |
+-----------------------------------------------------------------------------------+
| 3. VENDOR INTEGRATION CONTRACT VERSION (Int, e.g. 1)                               |
|    - Governs the adapter lifecycle, registration, and host callback contract.     |
+-----------------------------------------------------------------------------------+
| 4. METADATA SCHEMA VERSION (Int, e.g. SchemaVersion(1))                            |
|    - Governs the serialization format of stored protocol definitions & evidence.  |
+-----------------------------------------------------------------------------------+
| 5. DEVICE PROTOCOL VERSION (ProtocolVersion: Semantic / Integer / Named / Unknown) |
|    - Identifies wire framing, opcodes, payload formats, and interpretation.        |
+-----------------------------------------------------------------------------------+
| 6. DEVICE FIRMWARE REVISION (FirmwareRevision, e.g. "4.2.1-build88")               |
|    - Observed hardware/ROM build string. Not equal to protocol version.           |
+-----------------------------------------------------------------------------------+
```

---

## 2. Domain Details

### A. Protocol Version (`ProtocolVersion`)
- **Semantics:** Identifies a specific protocol contract, wire format, message interpretation, or command behavior.
- **Schemes:**
  - `Semantic(major, minor, patch)`: For protocols with explicit SemVer contracts.
  - `IntegerRevision(revision)`: For protocols using monotonic integer versioning (e.g. revision 1, 2, 3).
  - `VendorDefined(identifier)`: For protocols with proprietary string tokens (e.g. "v2-anc-extended", "rev-b").
  - `Unknown`: Explicit sentinel when the device protocol version cannot be observed or determined.
- **Rules:** Never default unknown to 0.0.0 or the app version. Never assume forward or backward compatibility without documented contracts.

### B. Protocol Schema Version (`ProtocolSchemaVersion`)
- **Semantics:** Identifies the format of stored protocol metadata, capability declarations, message schemas, or evidence records in the database.
- **Type:** Strongly typed integer wrapper (`ProtocolSchemaVersion(val version: Int)`).
- **Rules:** Changing a local metadata schema version does not imply the device's wire protocol changed. Unsupported schema versions must fail cleanly.

### C. Firmware Version (`FirmwareRevision`)
- **Semantics:** Identifies the device firmware revision when reliably observed via Bluetooth DIS, manufacturer payload, or telemetry.
- **Rules:** Firmware version is an environmental observation, not a wire protocol version. Firmware may correlate with protocol behavior, but must never be treated as a protocol version without evidence.

### D. SDK Version (`SdkVersion`)
- **Semantics:** Identifies the public Community Protocol SDK contract from Phase 43.
- **Rules:** An adapter compiled against SDK 1.0.0 does not imply the device speaks Protocol 1.0.0.

### E. Integration Version (`IntegrationContractVersion`)
- **Semantics:** Identifies a vendor adapter implementation release.
- **Rules:** A single adapter implementation may support multiple protocol revisions across different firmware levels. Updating adapter code does not change connected device hardware.

### F. Application Version
- **Semantics:** OmniBuds Android application release and build number.
- **Rules:** Completely decoupled from protocol processing.
