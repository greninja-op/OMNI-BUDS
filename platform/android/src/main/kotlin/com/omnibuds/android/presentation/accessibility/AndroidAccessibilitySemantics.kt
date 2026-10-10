package com.omnibuds.android.presentation.accessibility

import com.omnibuds.android.presentation.theme.AndroidTouchTargets

/**
 * Android Accessibility roles corresponding to accessibility service concepts (TalkBack / AccessibilityNodeInfo).
 */
enum class AndroidAccessibilityRole {
    BUTTON,
    SWITCH,
    SLIDER,
    TAB,
    TAB_PANEL,
    LIST_ITEM,
    LIST,
    TEXT,
    HEADER,
    STATUS,
    ALERT,
    DIALOG,
}

/**
 * Observable semantic state of an accessible component on Android.
 */
data class AndroidAccessibilityState(
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
 * User actions that can be triggered through TalkBack, accessibility services, or hardware keyboard.
 */
enum class AndroidAccessibilityAction {
    CLICK,
    TOGGLE,
    SET_VALUE,
    FOCUS,
    EXPAND,
    COLLAPSE,
    DISMISS,
    ACTIVATE,
}

/**
 * Semantic accessibility node representing an Android UI component in the accessibility tree.
 */
data class AndroidAccessibilityNode(
    val id: String,
    val label: String,
    val role: AndroidAccessibilityRole,
    val state: AndroidAccessibilityState = AndroidAccessibilityState(),
    val contentDescription: String? = null,
    val stateDescription: String? = null,
    val hint: String? = null,
    val touchTargetWidthDp: Int = AndroidTouchTargets.minTouchTargetDp,
    val touchTargetHeightDp: Int = AndroidTouchTargets.minTouchTargetDp,
    val supportedActions: Set<AndroidAccessibilityAction> = emptySet(),
    val children: List<AndroidAccessibilityNode> = emptyList(),
) {
    /**
     * Checks if this interactable node satisfies the minimum 48x48 dp touch target rule.
     */
    fun meetsTouchTargetRequirement(): Boolean {
        val isInteractive = supportedActions.isNotEmpty() || role in listOf(
            AndroidAccessibilityRole.BUTTON,
            AndroidAccessibilityRole.SWITCH,
            AndroidAccessibilityRole.SLIDER,
            AndroidAccessibilityRole.TAB,
            AndroidAccessibilityRole.LIST_ITEM,
        )
        if (!isInteractive) return true
        return touchTargetWidthDp >= AndroidTouchTargets.minTouchTargetDp &&
            touchTargetHeightDp >= AndroidTouchTargets.minTouchTargetDp
    }

    /**
     * Synthesizes the full TalkBack spoken description.
     */
    fun synthesizeTalkBackAnnouncement(): String {
        val parts = mutableListOf<String>()
        val mainText = contentDescription ?: label
        if (mainText.isNotBlank()) parts.add(mainText)

        stateDescription?.let { if (it.isNotBlank()) parts.add(it) }
            ?: run {
                when (role) {
                    AndroidAccessibilityRole.SWITCH -> {
                        parts.add(if (state.isChecked == true) "On" else "Off")
                    }
                    AndroidAccessibilityRole.TAB -> {
                        if (state.isSelected == true) parts.add("Selected")
                    }
                    AndroidAccessibilityRole.SLIDER -> {
                        state.valueDescription?.let { parts.add(it) }
                    }
                    else -> Unit
                }
            }

        if (!state.isEnabled) {
            parts.add("Disabled")
        }

        if (state.isBusy) {
            parts.add("In progress")
        }

        state.errorMessage?.let { parts.add("Error: $it") }
        hint?.let { parts.add(it) }

        return parts.joinToString(", ")
    }
}

/**
 * Headless evaluator for the Android Accessibility Tree.
 */
class AndroidAccessibilityTree(val root: AndroidAccessibilityNode) {

    fun findNodeById(id: String): AndroidAccessibilityNode? {
        fun search(node: AndroidAccessibilityNode): AndroidAccessibilityNode? {
            if (node.id == id) return node
            for (child in node.children) {
                val found = search(child)
                if (found != null) return found
            }
            return null
        }
        return search(root)
    }

    fun allNodes(): List<AndroidAccessibilityNode> {
        val result = mutableListOf<AndroidAccessibilityNode>()
        fun collect(node: AndroidAccessibilityNode) {
            result.add(node)
            node.children.forEach(::collect)
        }
        collect(root)
        return result
    }

    fun findNonCompliantTouchTargets(): List<AndroidAccessibilityNode> {
        return allNodes().filterNot { it.meetsTouchTargetRequirement() }
    }
}
