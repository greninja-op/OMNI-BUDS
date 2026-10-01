package com.omnibuds.core.platform

/**
 * The platform's clock, as an injectable seam.
 *
 * Core code may not read a clock directly: doing so would both make a test depend on wall time and
 * smuggle a JVM-only API into a module that must stay Kotlin-Multiplatform-capable
 * (ADR-P1-012). A null reading means the platform did not supply one, which is recorded as
 * "time unknown" rather than defaulted to zero (ADR-P0-016).
 */
fun interface TimeProvider {
    fun nowEpochMillis(): Long?
}

/** A clock that never reports a time, for code paths where timing is genuinely unavailable. */
object NoTimeProvider : TimeProvider {
    override fun nowEpochMillis(): Long? = null
}
