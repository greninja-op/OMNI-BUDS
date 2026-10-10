# Phase 45 — Architectural Decisions: Firmware Compatibility & Device Revision Management

## Decision Record 1: Separation of Firmware Version from Protocol and Identity
- **Context**: A device identity must remain stable across firmware updates. Protocol implementations and schema versions also evolve independently of on-earbud firmware.
- **Decision**: `FirmwareVersion` and `FirmwareObservation` are dedicated types distinct from `ProtocolVersion`, `ProtocolIdentity`, and `DeviceIdentity`.
- **Consequence**: Updating earbud firmware does not forge a new `DeviceIdentity` or require changes to wire protocol definitions.

## Decision Record 2: Rejection of Artificial Semantic Versioning for Firmware
- **Context**: Earbud manufacturers use SemVer, integer builds, calendar dates, alphanumeric build strings, and opaque hashes. Forcing SemVer leads to false orderings.
- **Decision**: Implement a sealed hierarchy of `FirmwareVersion` subtypes. Lexical comparison is disallowed unless the scheme is explicitly ordered.
- **Consequence**: Comparisons between mismatched schemes or opaque versions are strictly bounded and safe.

## Decision Record 3: Fail-Safe Rule Conflict Resolution
- **Context**: Multiple rules may match a device (e.g. a family-wide rule and a model-specific rule).
- **Decision**: Model-specific rules take precedence over family-wide rules. If outcomes conflict, any rule specifying `INCOMPATIBLE` immediately trumps positive rules and fails safe.
- **Consequence**: Known-bad firmware versions cannot be inadvertently authorized by broad compatibility rules.

## Decision Record 4: Freshness-Bounded Firmware Observations
- **Context**: Firmware states can become stale if a device undergoes an out-of-band update or reboot.
- **Decision**: Firmware observations carry timestamps and a maximum allowable age (TTL 5 minutes). Expired observations yield `STALE_FIRMWARE_OBSERVATION` and deny mutating operations.
- **Consequence**: Protects the earbud from receiving commands that are incompatible with newly flashed firmware.

## Decision Record 5: Strict Gating of Mutating Operations
- **Context**: Issuing write commands on unsupported or unknown firmware revisions can crash or corrupt device state.
- **Decision**: All mutating operations require `COMPATIBLE` status. Unknown, ambiguous, stale, or incompatible firmware limits execution to safe read-only operations where permitted.
- **Consequence**: Adheres strictly to the core product safety principles.
