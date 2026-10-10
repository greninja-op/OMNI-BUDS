# Phase 45 — Test Plan: Firmware Compatibility & Device Revision Management

## 1. Test Objectives

Ensure full coverage for firmware parsing, observation provenance, compatibility resolution, capability mapping, lifecycle invalidation, operation gating, and security boundaries.

## 2. Test Suites

### 2.1 Parsing & Hierarchy (`FirmwareVersionTest`)
- Parse SemVer with and without pre-release tags (`1.2.3`, `v2.0`, `1.0.4-rc1`).
- Parse monotonic build numbers (`1024`, `build-1050`).
- Parse date-based versions (`2023.11.05`, `2024-01-15`).
- Parse Apple AirPods alphanumeric builds (`4E71`, `5B58`).
- Unknown sentinel handling (`null`, empty, blank, "unknown", "0.0.0").
- Input bounds enforcement (length truncated/bounded to 128 characters).

### 2.2 Constraints (`FirmwareConstraintTest`)
- Exact matching and case insensitivity.
- Allowlist matching and rejection.
- Range bounds with scheme consistency checking.
- Denylist rejection of known buggy versions.
- Minimum version (`AtLeast`) progression checks.

### 2.3 Observation & Provenance (`FirmwareObservationTest`)
- Authoritative DIS readings vs. unverified readings.
- Missing observation creation.
- Security rejection of control characters.
- User manual entry disallowed for mutations.
- Observation freshness TTL evaluation.

### 2.4 Resolver Integration (`FirmwareCompatibilityResolverTest`)
- Compatible resolution yielding `COMPATIBLE` status.
- Incompatible firmware rejected safely.
- Explicit denylist rule overriding broad family support.
- Stale observation yielding `STALE_FIRMWARE_OBSERVATION`.
- Unknown firmware yielding `UNKNOWN_FIRMWARE` and blocking mutations.
- Read-only fallback under `COMPATIBLE_WITH_LIMITATIONS`.

### 2.5 Capability & Lifecycle (`FirmwareCapabilityAndLifecycleTest`)
- Capability state evaluation against firmware constraints.
- Invalidation and downgrade of capabilities upon simulated firmware downgrade.
- Cancellation check for in-flight operations when firmware constraint becomes unsatisfied.

### 2.6 Operations & Migrations (`FirmwareOperationGateAndMigrationTest`)
- Mutating operations permitted only when compatibility is `COMPATIBLE`.
- Mutating operations blocked when firmware is unknown, stale, or read-only.
- Safe read-only operations permitted under limitations.
- Valid DTO record migration into `FirmwareCompatibilityRule`.
- Rejection of corrupt or incomplete records during migration.

### 2.7 Architectural Boundaries (`DependencyDirectionTest`)
- Enforce layer hierarchy rules on `core.firmware` (Layer 5).
- Prevent upward or sideways coupling.
