package com.omnibuds.core.platform

import kotlinx.coroutines.flow.Flow

/**
 * Source of host application lifecycle signals.
 *
 * Keeps Android ActivityLifecycleCallbacks and desktop windowing lifecycle
 * models behind this platform-independent seam.
 */
interface PlatformLifecycleSource {
    /** Current snapshot of host lifecycle state. */
    val currentState: PlatformLifecycleState

    /** Observable flow of lifecycle state transitions. */
    val lifecycleEvents: Flow<PlatformLifecycleState>
}
