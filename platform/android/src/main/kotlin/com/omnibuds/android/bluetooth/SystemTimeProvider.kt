package com.omnibuds.android.bluetooth

import com.omnibuds.core.platform.TimeProvider

/**
 * Reads the wall clock from the running platform.
 *
 * `:core` may not read a clock directly - doing so would both make a test depend on wall time and
 * smuggle a JVM-only call into a module that must stay Kotlin-Multiplatform-capable (ADR-P1-012) - so
 * the reading lives here, behind the seam core declares. An observation timestamps are evidence about
 * when a fact was looked at; a platform that gave no reading leaves the time null rather than
 * inventing zero, which would sort an unknown event to the epoch (ADR-P0-016).
 */
object SystemTimeProvider : TimeProvider {
    override fun nowEpochMillis(): Long? = runCatching { System.currentTimeMillis() }.getOrNull()
}
