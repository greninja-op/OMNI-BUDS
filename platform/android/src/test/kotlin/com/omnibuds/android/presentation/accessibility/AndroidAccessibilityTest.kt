package com.omnibuds.android.presentation.accessibility

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AndroidAccessibilityTest {

    @Test
    fun `interactive components with 48dp or larger meet touch target requirement`() {
        val node48 = AndroidAccessibilityNode(
            id = "btn-scan",
            label = "Scan for Earbuds",
            role = AndroidAccessibilityRole.BUTTON,
            touchTargetWidthDp = 48,
            touchTargetHeightDp = 48,
            supportedActions = setOf(AndroidAccessibilityAction.CLICK),
        )
        assertTrue(node48.meetsTouchTargetRequirement())

        val node56 = AndroidAccessibilityNode(
            id = "btn-anc",
            label = "ANC Mode",
            role = AndroidAccessibilityRole.BUTTON,
            touchTargetWidthDp = 56,
            touchTargetHeightDp = 56,
            supportedActions = setOf(AndroidAccessibilityAction.CLICK),
        )
        assertTrue(node56.meetsTouchTargetRequirement())
    }

    @Test
    fun `sub-48dp interactive components fail touch target requirement`() {
        val smallNode = AndroidAccessibilityNode(
            id = "tiny-btn",
            label = "Tiny",
            role = AndroidAccessibilityRole.BUTTON,
            touchTargetWidthDp = 32,
            touchTargetHeightDp = 32,
            supportedActions = setOf(AndroidAccessibilityAction.CLICK),
        )
        assertFalse(smallNode.meetsTouchTargetRequirement())

        val tree = AndroidAccessibilityTree(root = smallNode)
        val nonCompliant = tree.findNonCompliantTouchTargets()
        assertEquals(1, nonCompliant.size)
        assertEquals("tiny-btn", nonCompliant.first().id)
    }

    @Test
    fun `talkback announcement synthesizes state and role accurately`() {
        val switchNode = AndroidAccessibilityNode(
            id = "switch-high-contrast",
            label = "High Contrast Mode",
            role = AndroidAccessibilityRole.SWITCH,
            state = AndroidAccessibilityState(isChecked = true, isEnabled = true),
            hint = "Double-tap to toggle",
        )
        val announcement = switchNode.synthesizeTalkBackAnnouncement()

        assertTrue(announcement.contains("High Contrast Mode"))
        assertTrue(announcement.contains("On"))
        assertTrue(announcement.contains("Double-tap to toggle"))
    }

    @Test
    fun `disabled and busy states are announced in TalkBack`() {
        val busyNode = AndroidAccessibilityNode(
            id = "btn-apply",
            label = "Apply Profile",
            role = AndroidAccessibilityRole.BUTTON,
            state = AndroidAccessibilityState(isEnabled = false, isBusy = true),
        )
        val announcement = busyNode.synthesizeTalkBackAnnouncement()

        assertTrue(announcement.contains("Disabled"))
        assertTrue(announcement.contains("In progress"))
    }
}
