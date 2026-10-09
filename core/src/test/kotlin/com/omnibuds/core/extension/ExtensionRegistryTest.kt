package com.omnibuds.core.extension

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Phase 23: registry tests.
 */
class ExtensionRegistryTest {

    private fun descriptor(id: String = "ext.acme.budsproto") =
        VendorExtensionDescriptor(
            id = VendorExtensionId(id),
            manufacturerId = "m-acme",
            protocolFamily = "acme-rfcomm",
            version = "1.0",
            compatibleModelIds = setOf("model-a"),
            featureNamespaces = setOf("acme.buds"),
            lifecycle = ExtensionLifecycle.ACTIVE,
        )

    private fun feature(
        id: String = "vendor.acme.ancplus",
        ext: String = "ext.acme.budsproto",
    ) = VendorFeatureDefinition(
        id = VendorFeatureId(id),
        extensionId = VendorExtensionId(ext),
        namespace = "acme.buds",
        canonicalName = "anc_plus",
        category = "audio",
        valueType = VendorValueType.BOOLEAN,
        readable = true,
        writable = true,
    )

    @Test
    fun `valid extension registration`() = runTest {
        val registry = ExtensionRegistry()
        val result = registry.registerExtension(descriptor())
        assertTrue(result is RegistrationResult.Registered)
        assertEquals("ext.acme.budsproto", (result as RegistrationResult.Registered).id)
    }

    @Test
    fun `duplicate extension rejected`() = runTest {
        val registry = ExtensionRegistry()
        registry.registerExtension(descriptor())
        val result = registry.registerExtension(descriptor())
        assertTrue(result is RegistrationResult.Rejected)
        assertTrue((result as RegistrationResult.Rejected).reason.contains("duplicate"))
    }

    @Test
    fun `disabled extension cannot register`() = runTest {
        val registry = ExtensionRegistry()
        val result = registry.registerExtension(
            descriptor().copy(lifecycle = ExtensionLifecycle.DISABLED),
        )
        assertTrue(result is RegistrationResult.Rejected)
    }

    @Test
    fun `circular extension dependency rejected`() = runTest {
        val registry = ExtensionRegistry()
        registry.registerExtension(
            descriptor("ext.acme.a").copy(
                dependencies = setOf(VendorExtensionId("ext.acme.b")),
            ),
        )
        val result = registry.registerExtension(
            descriptor("ext.acme.b").copy(
                dependencies = setOf(VendorExtensionId("ext.acme.a")),
            ),
        )
        assertTrue(result is RegistrationResult.Rejected)
        assertTrue((result as RegistrationResult.Rejected).reason.contains("circular"))
    }

    @Test
    fun `feature requires registered extension`() = runTest {
        val registry = ExtensionRegistry()
        val result = registry.registerFeature(feature())
        assertTrue(result is RegistrationResult.Rejected)
        assertTrue((result as RegistrationResult.Rejected).reason.contains("not registered"))
    }

    @Test
    fun `duplicate feature rejected`() = runTest {
        val registry = ExtensionRegistry()
        registry.registerExtension(descriptor())
        registry.registerFeature(feature())
        val result = registry.registerFeature(feature())
        assertTrue(result is RegistrationResult.Rejected)
    }

    @Test
    fun `undeclared namespace rejected`() = runTest {
        val registry = ExtensionRegistry()
        registry.registerExtension(descriptor().copy(featureNamespaces = setOf("acme.other")))
        val result = registry.registerFeature(feature())
        assertTrue(result is RegistrationResult.Rejected)
        assertTrue((result as RegistrationResult.Rejected).reason.contains("namespace"))
    }

    @Test
    fun `circular feature dependency rejected`() = runTest {
        val registry = ExtensionRegistry()
        registry.registerExtension(descriptor())
        registry.registerFeature(
            feature("vendor.acme.feata").copy(
                dependencies = setOf(VendorFeatureId("vendor.acme.featb")),
            ),
        )
        val result = registry.registerFeature(
            feature("vendor.acme.featb").copy(
                dependencies = setOf(VendorFeatureId("vendor.acme.feata")),
            ),
        )
        assertTrue(result is RegistrationResult.Rejected)
    }

    @Test
    fun `resolve candidates by device`() = runTest {
        val registry = ExtensionRegistry()
        registry.registerExtension(descriptor())
        registry.registerExtension(
            descriptor("ext.acme.other").copy(compatibleModelIds = setOf("model-b")),
        )
        val candidates = registry.resolveCandidates(
            manufacturerId = "m-acme",
            modelId = "model-a",
            firmwareVersion = null,
            protocolId = null,
            transport = null,
        )
        assertEquals(1, candidates.size)
        assertEquals("ext.acme.budsproto", candidates[0].id.value)
    }

    @Test
    fun `deprecated extension not resolved`() = runTest {
        val registry = ExtensionRegistry()
        registry.registerExtension(descriptor())
        registry.deprecate(VendorExtensionId("ext.acme.budsproto"))
        val candidates = registry.resolveCandidates(
            manufacturerId = "m-acme", modelId = null,
            firmwareVersion = null, protocolId = null, transport = null,
        )
        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `unknown extension lookup returns null`() = runTest {
        val registry = ExtensionRegistry()
        assertEquals(null, registry.extension(VendorExtensionId("ext.nope.nope")))
        assertEquals(null, registry.feature(VendorFeatureId("vendor.nope.nope")))
    }
}
