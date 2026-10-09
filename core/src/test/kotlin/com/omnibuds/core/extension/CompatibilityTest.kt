package com.omnibuds.core.extension

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Phase 23: compatibility tests.
 */
class CompatibilityTest {

    private fun descriptor() = VendorExtensionDescriptor(
        id = VendorExtensionId("ext.acme.budsproto"),
        manufacturerId = "m-acme",
        protocolFamily = "acme-rfcomm",
        version = "1.0",
        compatibleModelIds = setOf("model-a"),
        firmwareRules = listOf(">= 3.0"),
        compatibleProtocols = setOf("proto-1"),
        requiredTransports = setOf("rfcomm"),
        lifecycle = ExtensionLifecycle.ACTIVE,
    )

    private fun context(
        modelId: String? = "model-a",
        firmwareVersion: String? = "3.1",
        firmwareUnknown: Boolean = false,
        protocolId: String? = "proto-1",
        transport: String? = "rfcomm",
        identityAmbiguous: Boolean = false,
    ) = ExtensionCompatibility.evaluate(
        descriptor = descriptor(),
        manufacturerId = "m-acme",
        modelId = modelId,
        hardwareRevision = null,
        firmwareVersion = firmwareVersion,
        protocolId = protocolId,
        protocolVersion = "1.0",
        transport = transport,
        identityAmbiguous = identityAmbiguous,
        firmwareUnknown = firmwareUnknown,
    )

    @Test
    fun `exact supported model compatible`() {
        val result = context()
        assertTrue(result is CompatibilityResult.Compatible)
        assertTrue((result as CompatibilityResult.Compatible).evidence.isNotEmpty())
    }

    @Test
    fun `unsupported sibling model incompatible`() {
        val result = context(modelId = "model-b")
        assertTrue(result is CompatibilityResult.Incompatible)
    }

    @Test
    fun `manufacturer mismatch incompatible`() {
        val result = ExtensionCompatibility.evaluate(
            descriptor = descriptor(),
            manufacturerId = "m-other",
            modelId = null, hardwareRevision = null, firmwareVersion = null,
            protocolId = null, protocolVersion = null, transport = null,
            identityAmbiguous = false, firmwareUnknown = false,
        )
        assertTrue(result is CompatibilityResult.Incompatible)
    }

    @Test
    fun `unknown firmware is separate case`() {
        val result = context(firmwareVersion = null, firmwareUnknown = true)
        assertTrue(result is CompatibilityResult.UnknownFirmware)
    }

    @Test
    fun `firmware mismatch incompatible`() {
        val result = context(firmwareVersion = "2.0")
        assertTrue(result is CompatibilityResult.Incompatible)
    }

    @Test
    fun `protocol mismatch incompatible`() {
        val result = context(protocolId = "proto-2")
        assertTrue(result is CompatibilityResult.Incompatible)
    }

    @Test
    fun `transport mismatch incompatible`() {
        val result = context(transport = "ble")
        assertTrue(result is CompatibilityResult.Incompatible)
    }

    @Test
    fun `ambiguous identity flagged but compatible`() {
        val result = context(identityAmbiguous = true)
        assertTrue(result is CompatibilityResult.Compatible)
        assertTrue((result as CompatibilityResult.Compatible).identityAmbiguous)
    }

    @Test
    fun `inactive extension incompatible`() {
        val d = descriptor().copy(lifecycle = ExtensionLifecycle.DEPRECATED)
        val result = ExtensionCompatibility.evaluate(
            descriptor = d,
            manufacturerId = "m-acme", modelId = "model-a",
            hardwareRevision = null, firmwareVersion = "3.1",
            protocolId = "proto-1", protocolVersion = "1.0", transport = "rfcomm",
            identityAmbiguous = false, firmwareUnknown = false,
        )
        assertTrue(result is CompatibilityResult.Incompatible)
    }

    @Test
    fun `every result carries reason and evidence`() {
        val results = listOf(
            context(),
            context(modelId = "model-b"),
            context(firmwareVersion = null, firmwareUnknown = true),
        )
        for (r in results) {
            when (r) {
                is CompatibilityResult.Compatible -> {
                    assertTrue(r.reason.isNotBlank())
                    assertTrue(r.evidence.isNotEmpty())
                }
                is CompatibilityResult.Incompatible -> {
                    assertTrue(r.reason.isNotBlank())
                    assertTrue(r.evidence.isNotEmpty())
                }
                is CompatibilityResult.UnknownFirmware -> {
                    assertTrue(r.reason.isNotBlank())
                    assertTrue(r.evidence.isNotEmpty())
                }
            }
        }
    }
}
