package com.omnibuds.core.lifecycle

import kotlinx.coroutines.Job

/**
 * Registry of lifecycle-owned resources.
 *
 * Phase 28 (OB-P28-REQ-009/026): explicit ownership. Every registered
 * resource is released when its owner ends. No leaked callbacks,
 * collectors, or jobs.
 */
class ResourceLifecycleRegistry {
    private val lock = Any()
    private val resources = mutableMapOf<String, OwnedResource>()

    /**
     * Register a resource under an owner key.
     */
    fun register(owner: String, resource: OwnedResource) {
        synchronized(lock) {
            resources[owner]?.release() // Replace: old resource released first.
            resources[owner] = resource
        }
    }

    /**
     * Release all resources for an owner.
     */
    fun release(owner: String) {
        synchronized(lock) {
            resources.remove(owner)?.release()
        }
    }

    /**
     * Release everything. Used on process termination (best-effort).
     */
    fun releaseAll() {
        synchronized(lock) {
            resources.values.forEach { it.release() }
            resources.clear()
        }
    }

    /** Number of tracked resources (for tests/diagnostics). */
    fun size(): Int = synchronized(lock) { resources.size }

    /** True when the owner has a registered resource. */
    fun has(owner: String): Boolean = synchronized(lock) { owner in resources }
}

/**
 * A releasable resource.
 */
sealed interface OwnedResource {
    /** Release the resource. Idempotent. */
    fun release()

    /** A coroutine job. */
    data class CoroutineJob(val job: Job) : OwnedResource {
        override fun release() {
            job.cancel()
        }
    }

    /** A platform callback registration with an unregister action. */
    data class Callback(val unregister: () -> Unit) : OwnedResource {
        private var released = false
        override fun release() {
            if (!released) {
                released = true
                unregister()
            }
        }
    }

    /** A closeable handle. */
    data class Handle(val close: () -> Unit) : OwnedResource {
        private var released = false
        override fun release() {
            if (!released) {
                released = true
                close()
            }
        }
    }
}
