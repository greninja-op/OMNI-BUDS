package com.omnibuds.core.vendor

import com.omnibuds.core.device.DeviceFingerprint
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Registry of vendor adapters.
 *
 * Phase 19: adapters register here. Resolution is deterministic:
 * exact matches win; ambiguous matches never enable writes; no match
 * falls through to the unknown-device path.
 */
class VendorRegistry(
    adapters: Collection<VendorAdapter> = emptyList(),
) {
    private val mutex = Mutex()
    private val adapters = adapters.toList()

    /** Register an adapter, returning a new registry. */
    fun register(adapter: VendorAdapter): VendorRegistry =
        VendorRegistry(adapters + adapter)

    /** All registered adapter IDs. */
    fun adapterIds(): List<String> = adapters.map { it.adapterId }

    /**
     * Resolve the best adapter for a device.
     * - Exactly one [MatchResult.Matched] → that adapter.
     * - Any [MatchResult.Ambiguous] → null (safe fallback; writes disabled).
     * - Zero matches → null (unknown device).
     * - Multiple matched → null (conflicting; safest is no adapter).
     */
    suspend fun resolve(fingerprint: DeviceFingerprint): VendorAdapter? = mutex.withLock {
        val matched = adapters.filter {
            it.match(fingerprint) is MatchResult.Matched
        }
        val ambiguous = adapters.any {
            it.match(fingerprint) is MatchResult.Ambiguous
        }
        if (ambiguous) return@withLock null
        if (matched.size != 1) return@withLock null
        matched.first()
    }

    /** True when any adapter reports ambiguity for this device. */
    suspend fun isAmbiguous(fingerprint: DeviceFingerprint): Boolean = mutex.withLock {
        adapters.any { it.match(fingerprint) is MatchResult.Ambiguous }
    }
}
