package com.omnibuds.core.platform

import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.state.VerificationLevel

/**
 * Common sink interface for platform logging and telemetry.
 *
 * Implementations live in platform modules (Android Logcat, Desktop syslog/stdout).
 */
interface PlatformDiagnosticSink {
    fun isEnabled(level: VerificationLevel): Boolean
    fun emit(level: VerificationLevel, tag: String, message: String): OperationOutcome<Unit>
}

/**
 * No-op diagnostic sink that drops all events safely.
 */
object NoOpDiagnosticSink : PlatformDiagnosticSink {
    override fun isEnabled(level: VerificationLevel): Boolean = false
    override fun emit(level: VerificationLevel, tag: String, message: String): OperationOutcome<Unit> =
        OperationOutcome.Success(Unit)
}
