package com.omnibuds.android.lifecycle

import com.omnibuds.core.platform.PlatformLifecycleSource
import com.omnibuds.core.platform.PlatformLifecycleState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android implementation of the platform-independent [PlatformLifecycleSource] contract.
 */
class AndroidPlatformLifecycleSource(
    initialState: PlatformLifecycleState = PlatformLifecycleState.BACKGROUND,
) : PlatformLifecycleSource {

    private val _state = MutableStateFlow(initialState)

    override val currentState: PlatformLifecycleState
        get() = _state.value

    override val lifecycleEvents: Flow<PlatformLifecycleState> = _state.asStateFlow()

    fun updateState(newState: PlatformLifecycleState) {
        _state.value = newState
    }
}
