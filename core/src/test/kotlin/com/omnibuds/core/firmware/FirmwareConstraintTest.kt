package com.omnibuds.core.firmware

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirmwareConstraintTest {

    @Test
    fun exactConstraint() {
        val exact = FirmwareConstraint.Exact(FirmwareVersion.parse("2.1.0"))
        assertTrue(exact.isSatisfiedBy(FirmwareVersion.parse("2.1.0")))
        assertFalse(exact.isSatisfiedBy(FirmwareVersion.parse("2.1.1")))
        assertFalse(exact.isSatisfiedBy(FirmwareVersion.Unknown))
    }

    @Test
    fun allowlistConstraint() {
        val allowlist = FirmwareConstraint.Allowlist(
            setOf(
                FirmwareVersion.parse("1.0.0"),
                FirmwareVersion.parse("1.1.0"),
                FirmwareVersion.parse("2.0.0"),
            )
        )
        assertTrue(allowlist.isSatisfiedBy(FirmwareVersion.parse("1.1.0")))
        assertTrue(allowlist.isSatisfiedBy(FirmwareVersion.parse("2.0.0")))
        assertFalse(allowlist.isSatisfiedBy(FirmwareVersion.parse("1.2.0")))
        assertFalse(allowlist.isSatisfiedBy(FirmwareVersion.Unknown))
    }

    @Test
    fun rangeConstraint() {
        val range = FirmwareConstraint.Range(
            minInclusive = FirmwareVersion.parse("2.0.0"),
            maxInclusive = FirmwareVersion.parse("3.0.0"),
        )
        assertTrue(range.isSatisfiedBy(FirmwareVersion.parse("2.0.0")))
        assertTrue(range.isSatisfiedBy(FirmwareVersion.parse("2.5.1")))
        assertTrue(range.isSatisfiedBy(FirmwareVersion.parse("3.0.0")))
        assertFalse(range.isSatisfiedBy(FirmwareVersion.parse("1.9.9")))
        assertFalse(range.isSatisfiedBy(FirmwareVersion.parse("3.0.1")))
        assertFalse(range.isSatisfiedBy(FirmwareVersion.Unknown))
    }

    @Test
    fun denylistConstraint() {
        val denylist = FirmwareConstraint.Denylist(
            setOf(
                FirmwareVersion.parse("1.4.2"),
                FirmwareVersion.parse("2.0.0-buggy"),
            )
        )
        assertFalse(denylist.isSatisfiedBy(FirmwareVersion.parse("1.4.2")))
        assertTrue(denylist.isSatisfiedBy(FirmwareVersion.parse("1.4.3")))
        assertTrue(denylist.isSatisfiedBy(FirmwareVersion.parse("2.1.0")))
        assertFalse(denylist.isSatisfiedBy(FirmwareVersion.Unknown))
    }

    @Test
    fun atLeastConstraint() {
        val atLeast = FirmwareConstraint.AtLeast(FirmwareVersion.parse("4.0.0"))
        assertTrue(atLeast.isSatisfiedBy(FirmwareVersion.parse("4.0.0")))
        assertTrue(atLeast.isSatisfiedBy(FirmwareVersion.parse("4.1.0")))
        assertTrue(atLeast.isSatisfiedBy(FirmwareVersion.parse("5.0.0")))
        assertFalse(atLeast.isSatisfiedBy(FirmwareVersion.parse("3.9.9")))
        assertFalse(atLeast.isSatisfiedBy(FirmwareVersion.Unknown))
    }
}
