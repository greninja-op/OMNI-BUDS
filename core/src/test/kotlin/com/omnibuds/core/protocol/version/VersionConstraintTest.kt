package com.omnibuds.core.protocol.version

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionConstraintTest {

    @Test
    fun exactMatching() {
        val constraint = VersionConstraint.Exact(ProtocolVersion.Semantic(1, 2, 0))
        assertTrue(constraint.isSatisfiedBy(ProtocolVersion.Semantic(1, 2, 0)))
        assertFalse(constraint.isSatisfiedBy(ProtocolVersion.Semantic(1, 2, 1)))
        assertFalse(constraint.isSatisfiedBy(ProtocolVersion.IntegerRevision(1)))
        assertFalse(constraint.isSatisfiedBy(ProtocolVersion.Unknown))
    }

    @Test
    fun semanticRangeMatching() {
        val constraint = VersionConstraint.SemanticRange(
            min = ProtocolVersion.Semantic(1, 0, 0),
            maxInclusive = ProtocolVersion.Semantic(2, 0, 0),
        )
        assertTrue(constraint.isSatisfiedBy(ProtocolVersion.Semantic(1, 0, 0)))
        assertTrue(constraint.isSatisfiedBy(ProtocolVersion.Semantic(1, 5, 2)))
        assertTrue(constraint.isSatisfiedBy(ProtocolVersion.Semantic(2, 0, 0)))
        assertFalse(constraint.isSatisfiedBy(ProtocolVersion.Semantic(2, 0, 1)))
        assertFalse(constraint.isSatisfiedBy(ProtocolVersion.Semantic(0, 9, 9)))
        assertFalse(constraint.isSatisfiedBy(ProtocolVersion.Unknown))
    }

    @Test
    fun integerRangeMatching() {
        val constraint = VersionConstraint.IntegerRange(minRevision = 1, maxRevision = 3)
        assertTrue(constraint.isSatisfiedBy(ProtocolVersion.IntegerRevision(1)))
        assertTrue(constraint.isSatisfiedBy(ProtocolVersion.IntegerRevision(2)))
        assertTrue(constraint.isSatisfiedBy(ProtocolVersion.IntegerRevision(3)))
        assertFalse(constraint.isSatisfiedBy(ProtocolVersion.IntegerRevision(0)))
        assertFalse(constraint.isSatisfiedBy(ProtocolVersion.IntegerRevision(4)))
        assertFalse(constraint.isSatisfiedBy(ProtocolVersion.Unknown))
    }

    @Test
    fun enumeratedMatching() {
        val constraint = VersionConstraint.Enumerated(
            setOf(
                ProtocolVersion.VendorDefined("rev-a"),
                ProtocolVersion.VendorDefined("rev-c"),
            ),
        )
        assertTrue(constraint.isSatisfiedBy(ProtocolVersion.VendorDefined("rev-a")))
        assertTrue(constraint.isSatisfiedBy(ProtocolVersion.VendorDefined("rev-c")))
        assertFalse(constraint.isSatisfiedBy(ProtocolVersion.VendorDefined("rev-b")))
        assertFalse(constraint.isSatisfiedBy(ProtocolVersion.Unknown))
    }

    @Test
    fun anyKnownAndNoneMatching() {
        assertTrue(VersionConstraint.AnyKnown.isSatisfiedBy(ProtocolVersion.Semantic(1, 0, 0)))
        assertTrue(VersionConstraint.AnyKnown.isSatisfiedBy(ProtocolVersion.IntegerRevision(1)))
        assertFalse(VersionConstraint.AnyKnown.isSatisfiedBy(ProtocolVersion.Unknown))

        assertFalse(VersionConstraint.None.isSatisfiedBy(ProtocolVersion.Semantic(1, 0, 0)))
        assertFalse(VersionConstraint.None.isSatisfiedBy(ProtocolVersion.Unknown))
    }
}
