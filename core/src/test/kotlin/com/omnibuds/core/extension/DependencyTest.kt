package com.omnibuds.core.extension

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Phase 23: dependency and execution-contract tests.
 */
class DependencyTest {

    private fun feature(
        id: String,
        deps: Set<VendorFeatureId> = emptySet(),
        conflicts: Set<VendorFeatureId> = emptySet(),
    ) = VendorFeatureDefinition(
        id = VendorFeatureId(id),
        extensionId = VendorExtensionId("ext.acme.budsproto"),
        namespace = "acme.buds",
        canonicalName = id.substringAfterLast("."),
        category = "audio",
        valueType = VendorValueType.BOOLEAN,
        dependencies = deps,
        conflicts = conflicts,
    )

    @Test
    fun `satisfied dependencies`() {
        val f = feature(
            "vendor.acme.advanced",
            deps = setOf(VendorFeatureId("vendor.acme.basic")),
        )
        val known = mapOf(
            "vendor.acme.basic" to feature("vendor.acme.basic"),
            "vendor.acme.advanced" to f,
        )
        val result = FeatureDependencies.check(
            f,
            satisfiedFeatures = setOf(VendorFeatureId("vendor.acme.basic")),
            activeFeatures = emptySet(),
            knownFeatures = known,
        )
        assertTrue(result is DependencyResult.Satisfied)
    }

    @Test
    fun `missing prerequisite blocks`() {
        val f = feature(
            "vendor.acme.advanced",
            deps = setOf(VendorFeatureId("vendor.acme.basic")),
        )
        val known = mapOf(
            "vendor.acme.basic" to feature("vendor.acme.basic"),
            "vendor.acme.advanced" to f,
        )
        val result = FeatureDependencies.check(
            f, satisfiedFeatures = emptySet(),
            activeFeatures = emptySet(), knownFeatures = known,
        )
        assertTrue(result is DependencyResult.MissingPrerequisites)
    }

    @Test
    fun `unknown dependency unresolved`() {
        val f = feature(
            "vendor.acme.advanced",
            deps = setOf(VendorFeatureId("vendor.acme.ghost")),
        )
        val result = FeatureDependencies.check(
            f, satisfiedFeatures = emptySet(),
            activeFeatures = emptySet(), knownFeatures = emptyMap(),
        )
        assertTrue(result is DependencyResult.Unresolved)
    }

    @Test
    fun `conflict detected`() {
        val f = feature(
            "vendor.acme.modea",
            conflicts = setOf(VendorFeatureId("vendor.acme.modeb")),
        )
        val result = FeatureDependencies.check(
            f, satisfiedFeatures = emptySet(),
            activeFeatures = setOf(VendorFeatureId("vendor.acme.modeb")),
            knownFeatures = mapOf(
                "vendor.acme.modea" to f,
                "vendor.acme.modeb" to feature("vendor.acme.modeb"),
            ),
        )
        assertTrue(result is DependencyResult.Conflict)
    }
}

/**
 * Phase 23: execution-contract tests.
 */
class ExecutionContractTest {

    @Test
    fun `operation contract requires positive timeout`() {
        try {
            OperationContract(
                readable = true, writable = false,
                acknowledgementExpected = false, readBackSupported = false,
                idempotent = true, retrySafe = true, mayBeVolatile = false,
                timeoutMillis = 0L,
            )
            assertTrue(false, "should have thrown")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("timeout"))
        }
    }

    @Test
    fun `read results are typed`() {
        val results: List<VendorFeatureReadResult> = listOf(
            VendorFeatureReadResult.Success(VendorFeatureValue.BooleanValue(true)),
            VendorFeatureReadResult.NotReadable("write-only feature"),
            VendorFeatureReadResult.Unsupported("not on this model"),
            VendorFeatureReadResult.Unknown("disconnected after submission"),
            VendorFeatureReadResult.Denied("access policy denied"),
            VendorFeatureReadResult.Cancelled,
        )
        assertTrue(results.size == 6)
    }

    @Test
    fun `write results distinguish ambiguous outcomes`() {
        val unknown: VendorFeatureWriteResult =
            VendorFeatureWriteResult.Unknown("timeout after submission")
        val success: VendorFeatureWriteResult =
            VendorFeatureWriteResult.Success(VendorFeatureValue.BooleanValue(true))
        // Unknown is not success and not failure — it's a distinct state.
        assertTrue(unknown is VendorFeatureWriteResult.Unknown)
        assertTrue(success is VendorFeatureWriteResult.Success)
    }

    @Test
    fun `read-back mismatch carries both values`() {
        val result = VendorFeatureWriteResult.ReadBackMismatch(
            requested = VendorFeatureValue.IntValue(50),
            observed = VendorFeatureValue.IntValue(55),
        )
        assertTrue(result.requested == VendorFeatureValue.IntValue(50))
        assertTrue(result.observed == VendorFeatureValue.IntValue(55))
    }

    @Test
    fun `resolution ambiguity explicit`() {
        val resolution = VendorFeatureResolution.Ambiguous(
            candidates = listOf(
                VendorExtensionId("ext.acme.a"),
                VendorExtensionId("ext.acme.b"),
            ),
            reason = "two extensions match",
        )
        assertTrue(resolution.candidates.size == 2)
    }
}
