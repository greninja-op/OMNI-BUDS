package com.omnibuds.core.platform

/**
 * A handle to something registered with the platform - a broadcast receiver, a callback, a scan.
 *
 * Phase 2 prompt section 5.4 requires that registration and unregistration be explicit and that
 * no observer leak. Modelling registration as a value that must be disposed, rather than as a
 * call the caller remembers, is what makes "did we clean up?" a question the type system can help
 * with. [dispose] is idempotent by contract, so a cancellation path that runs twice is harmless
 * instead of throwing.
 */
interface PlatformRegistration {
    /** Whether this handle still refers to a live platform registration. */
    val isActive: Boolean

    /**
     * Remove the registration and release whatever the platform held for it.
     *
     * Suspending because some platform teardowns are asynchronous. Implementations must be safe to
     * call more than once and must report failure rather than pretend the teardown happened.
     */
    suspend fun dispose()
}
