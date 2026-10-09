package com.omnibuds.core.battery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Phase 16 §24A: battery percentage tests.
 * Zero is a real reading; null is unknown; invalid values are rejected.
 */
class BatteryLevelTest {

    @Test
    fun `zero is a valid reading`() {
        assertEquals(0, BatteryLevel.of(0)!!.percentage)
    }

    @Test
    fun `one is valid`() {
        assertEquals(1, BatteryLevel.of(1)!!.percentage)
    }

    @Test
    fun `fifty is valid`() {
        assertEquals(50, BatteryLevel.of(50)!!.percentage)
    }

    @Test
    fun `ninety-nine is valid`() {
        assertEquals(99, BatteryLevel.of(99)!!.percentage)
    }

    @Test
    fun `one hundred is valid`() {
        assertEquals(100, BatteryLevel.of(100)!!.percentage)
    }

    @Test
    fun `null stays null`() {
        assertNull(BatteryLevel.of(null))
    }

    @Test
    fun `negative is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { BatteryLevel.of(-1) }
    }

    @Test
    fun `above one hundred is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { BatteryLevel.of(101) }
    }

    @Test
    fun `lenient parse drops invalid without throwing`() {
        assertNull(BatteryLevel.parseLenient(-5))
        assertNull(BatteryLevel.parseLenient(150))
        assertNull(BatteryLevel.parseLenient(null))
        assertEquals(42, BatteryLevel.parseLenient(42)!!.percentage)
    }

    @Test
    fun `zero is distinct from unknown`() {
        val zero = BatteryLevel.of(0)
        val unknown = BatteryLevel.of(null)
        // Zero is a value; unknown is the absence of one.
        assertEquals(0, zero!!.percentage)
        assertNull(unknown)
    }
}
