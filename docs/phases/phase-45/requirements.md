# Phase 45 — Requirements: Firmware Compatibility & Device Revision Management

## 1. Functional Requirements

1. **Explicit Version Domain Separation**:
   - Firmware versions must be represented separately from protocol, SDK, vendor integration, application, and metadata schema versions.
   - Firmware versions must support multiple schemes without forcing semantic versioning:
     - Semantic versioning (major, minor, patch, pre-release)
     - Monotonic build numbers / revisions
     - Date-based versions (calendar dates)
     - Alphanumeric vendor builds (e.g. Apple AirPods generation/train/sequence)
     - Opaque vendor strings
     - Unknown (explicit sentinel, never "0.0.0" or arbitrary default).
   - Lexical comparison must be avoided when structured schemes are present.

2. **Firmware Observation and Provenance**:
   - Every observation must capture: device ID, raw value, typed `FirmwareVersion`, source category, timestamp, verification level, hardware revision, protocol scope, validation status, and documented limitations.
   - Distinct observation sources must be recognized: authoritative device interface (DIS 0x2A26), vendor telemetry, persistent cache, advertisement inference, test fixture, and manual user entry.
   - Manually entered or unverified inferred versions must not authorize mutating operations.
   - Observations must be evaluated against a bounded freshness window (default 5 minutes).

3. **Deterministic Compatibility Model**:
   - Evidence-backed rules must govern compatibility by manufacturer, model scope, firmware constraint, target scope, outcome, and evidence reference.
   - Constraints must support: Any, Exact, Allowlist, Range (bounded, scheme-validated), Denylist, and AtLeast.
   - Conflicting rules must resolve deterministically, prioritizing exact model-specific rules and failing safe to Incompatible when a denylist rule matches.
   - Broad wildcards must not override negative evidence.

4. **Firmware-Dependent Capabilities**:
   - Capabilities conditional upon firmware revisions must evaluate to explicit `CapabilityState` rungs.
   - Statically claiming `PERSISTENCE_VERIFIED` from firmware metadata alone is forbidden.
   - Missing or unknown firmware must downgrade or restrict capabilities to read-only or unknown.

5. **State Invalidation and Change Handling**:
   - When a device firmware shifts, active capability resolutions and pending operations predicated on the prior firmware must be invalidated or cancelled.
   - Stale observations must not restore previously invalid states.

6. **Operation Authorization Gating**:
   - Mutating operations require strictly `COMPATIBLE` standing and trustworthy observation provenance.
   - Unsupported, unknown, stale, or malformed firmware must deny mutating operations.
   - Read-only operations remain accessible when explicitly permitted by policy.

7. **Persistence and Migrations**:
   - Schema versioning must protect persistent firmware compatibility records.
   - Corrupted or invalid records must be rejected safely during migration.
