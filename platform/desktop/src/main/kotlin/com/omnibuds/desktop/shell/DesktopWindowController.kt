package com.omnibuds.desktop.shell

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Window dimension and visibility state.
 */
data class WindowState(
    val title: String = "OmniBuds — Desktop Hardware Control",
    val width: Int = 1024,
    val height: Int = 720,
    val isMaximized: Boolean = false,
    val isMinimized: Boolean = false,
    val isVisible: Boolean = true,
) {
    init {
        require(width >= MIN_WIDTH) { "width must be >= $MIN_WIDTH, was $width" }
        require(height >= MIN_HEIGHT) { "height must be >= $MIN_HEIGHT, was $height" }
    }

    companion object {
        const val MIN_WIDTH = 640
        const val MIN_HEIGHT = 480
    }
}

/**
 * Window controller managing desktop window lifecycle and geometry.
 */
class DesktopWindowController(
    initialState: WindowState = WindowState(),
) {
    private val _windowState = MutableStateFlow(initialState)
    val windowState: StateFlow<WindowState> = _windowState.asStateFlow()

    fun resize(width: Int, height: Int) {
        val clampedWidth = width.coerceAtLeast(WindowState.MIN_WIDTH)
        val clampedHeight = height.coerceAtLeast(WindowState.MIN_HEIGHT)
        _windowState.update {
            it.copy(width = clampedWidth, height = clampedHeight, isMaximized = false)
        }
    }

    fun toggleMaximize() {
        _windowState.update { it.copy(isMaximized = !it.isMaximized) }
    }

    fun minimize() {
        _windowState.update { it.copy(isMinimized = true) }
    }

    fun restore() {
        _windowState.update { it.copy(isMinimized = false) }
    }

    fun setTitle(title: String) {
        _windowState.update { it.copy(title = title) }
    }

    fun close() {
        _windowState.update { it.copy(isVisible = false) }
    }
}
