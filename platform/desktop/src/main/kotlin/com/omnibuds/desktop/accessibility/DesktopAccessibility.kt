package com.omnibuds.desktop.accessibility

/**
 * Standard WAI-ARIA and desktop accessibility roles for components.
 */
enum class AccessibilityRole {
    BUTTON,
    TOGGLE,
    SLIDER,
    TAB,
    TAB_PANEL,
    LIST_ITEM,
    LIST,
    TEXT,
    HEADING,
    REGION,
    STATUS,
    ALERT,
    DIALOG,
    LINK,
}

/**
 * Observable semantic state of an accessible component.
 */
data class AccessibilityState(
    val isFocused: Boolean = false,
    val isEnabled: Boolean = true,
    val isChecked: Boolean? = null,
    val isSelected: Boolean? = null,
    val isBusy: Boolean = false,
    val isExpanded: Boolean? = null,
    val valueDescription: String? = null,
    val errorMessage: String? = null,
)

/**
 * User actions that can be triggered through assistive technology or keyboard.
 */
enum class AccessibilityAction {
    CLICK,
    TOGGLE,
    SET_VALUE,
    FOCUS,
    EXPAND,
    COLLAPSE,
    DISMISS,
}

/**
 * Keyboard key definition for desktop navigation.
 */
data class DesktopKeyStroke(
    val key: String,
    val isCtrlDown: Boolean = false,
    val isShiftDown: Boolean = false,
    val isAltDown: Boolean = false,
)

/**
 * Semantic accessibility node representing a desktop UI element.
 */
data class AccessibilityNode(
    val id: String,
    val label: String,
    val role: AccessibilityRole,
    val state: AccessibilityState = AccessibilityState(),
    val hint: String? = null,
    val supportedActions: Set<AccessibilityAction> = emptySet(),
    val children: List<AccessibilityNode> = emptyList(),
) {
    /**
     * Find a node in this tree by id.
     */
    fun findById(targetId: String): AccessibilityNode? {
        if (id == targetId) return this
        for (child in children) {
            val found = child.findById(targetId)
            if (found != null) return found
        }
        return null
    }

    /**
     * Flatten all focusable elements in logical reading order.
     */
    fun focusableNodes(): List<AccessibilityNode> {
        val result = mutableListOf<AccessibilityNode>()
        if (state.isEnabled && (supportedActions.contains(AccessibilityAction.FOCUS) ||
                role in setOf(
                    AccessibilityRole.BUTTON,
                    AccessibilityRole.TOGGLE,
                    AccessibilityRole.SLIDER,
                    AccessibilityRole.TAB,
                    AccessibilityRole.LIST_ITEM,
                    AccessibilityRole.LINK,
                ))
        ) {
            result.add(this)
        }
        for (child in children) {
            result.addAll(child.focusableNodes())
        }
        return result
    }
}

/**
 * Deterministic keyboard focus manager for desktop UI.
 */
class DesktopFocusManager {
    private var focusedNodeId: String? = null

    val currentFocusedId: String? get() = focusedNodeId

    fun requestFocus(nodeId: String) {
        focusedNodeId = nodeId
    }

    fun clearFocus() {
        focusedNodeId = null
    }

    /**
     * Move focus forwards (Tab key) through the focusable elements of [root].
     */
    fun moveFocusNext(root: AccessibilityNode): String? {
        val focusables = root.focusableNodes()
        if (focusables.isEmpty()) return null
        val currentIndex = focusables.indexOfFirst { it.id == focusedNodeId }
        val nextIndex = if (currentIndex < 0 || currentIndex >= focusables.size - 1) 0 else currentIndex + 1
        val nextId = focusables[nextIndex].id
        focusedNodeId = nextId
        return nextId
    }

    /**
     * Move focus backwards (Shift+Tab) through the focusable elements of [root].
     */
    fun moveFocusPrevious(root: AccessibilityNode): String? {
        val focusables = root.focusableNodes()
        if (focusables.isEmpty()) return null
        val currentIndex = focusables.indexOfFirst { it.id == focusedNodeId }
        val prevIndex = if (currentIndex <= 0) focusables.size - 1 else currentIndex - 1
        val prevId = focusables[prevIndex].id
        focusedNodeId = prevId
        return prevId
    }
}
