package com.omnibuds.core.access

import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.diagnostics.DiagnosticCategory
import com.omnibuds.core.diagnostics.DiagnosticEvent
import com.omnibuds.core.diagnostics.DiagnosticSeverity
import com.omnibuds.core.diagnostics.OmniBudsLogger

/**
 * Privacy-conscious diagnostics for access decisions.
 *
 * Phase 21 (OB-P21-REQ-020): bounded, typed reason-code events. Never logs
 * credentials, raw payloads, or Bluetooth addresses — the device handle is
 * the address-free identityKey.
 */
class AccessDiagnostics(
    private val logger: OmniBudsLogger,
    private val maxEvents: Int = 256,
) {
    private var emitted = 0

    /** Log a classification event. */
    fun classification(deviceKey: String, state: DeviceAccessState) {
        emit(
            message = "device $deviceKey classified as ${state.classification}",
            category = DiagnosticCategory.PERMISSION,
            error = null,
        )
    }

    /** Log an ambiguity event. */
    fun ambiguity(deviceKey: String, candidateCount: Int) {
        emit(
            message = "device $deviceKey has ambiguous identity ($candidateCount candidates); writes disabled",
            category = DiagnosticCategory.PERMISSION,
            error = OmniBudsError(
                category = OmniBudsErrorCategory.INVALID_STATE,
                operationId = "ambiguous-identity",
                detail = "ambiguous identity",
            ),
        )
    }

    /** Log a denied operation with its typed reason. */
    fun denied(deviceKey: String, decision: AccessDecision) {
        emit(
            message = "device $deviceKey denied ${decision.operation} on " +
                "${decision.capabilityId ?: "device"}: ${decision.reason}",
            category = DiagnosticCategory.PERMISSION,
            error = OmniBudsError(
                category = OmniBudsErrorCategory.PERMISSION_DENIED,
                operationId = "access-denied",
                detail = decision.reason.name,
            ),
        )
    }

    /** Log stale evidence. */
    fun staleEvidence(deviceKey: String, reason: String) {
        emit(
            message = "device $deviceKey evidence stale: $reason; operations re-evaluated",
            category = DiagnosticCategory.PERMISSION,
            error = null,
        )
    }

    private fun emit(
        message: String,
        category: DiagnosticCategory,
        error: OmniBudsError?,
    ) {
        // Bounded: drop events beyond the cap rather than growing memory.
        if (emitted >= maxEvents) return
        emitted++
        if (!logger.isEnabled(DiagnosticSeverity.INFO)) return
        logger.log(
            DiagnosticEvent(
                timestampEpochMillis = System.currentTimeMillis(),
                severity = DiagnosticSeverity.INFO,
                category = category,
                message = message,
                operationId = null,
                error = error,
            ),
        )
    }
}
