package com.omnibuds.core.validation

/**
 * How much a validation result matters, independent of its [ValidationStatus].
 *
 * Phase 14 (OB-P14-REQ-003): severity is orthogonal to status. A WARNING
 * never invalidates an otherwise consistent model; a missing optional
 * parameter is INFO, never CRITICAL. A definite device-association
 * violation is ERROR or CRITICAL depending on consequence.
 *
 * Severity policy:
 * - INFO: informational; no action needed (e.g. optional parameter absent).
 * - WARNING: worth noting; the model is still consistent (e.g. stale but
 *   superseded observation, unobservable optional fact).
 * - ERROR: a proven invariant violation that undermines trust in the
 *   validated aspect (e.g. codec/transport mismatch, wrong-device
 *   attribution).
 * - CRITICAL: a violation with safety/privacy consequences or that
 *   invalidates the whole snapshot (e.g. active route on a disconnected
 *   session, device-identity confusion).
 */
enum class ValidationSeverity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL,
}
