package com.omnibuds.core.protocol

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.state.VerificationLevel
import com.omnibuds.core.common.TransportKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ProtocolDefinition] as the single structural record that replaces scattered magic
 * values (master sections 51 and 52, PROTO-NOMAGIC-001..003, PROTO-VENDOR-002/003).
 *
 * Tier T1. Every id here is a fictional placeholder of the form
 * `example-vendor.test-protocol`; none names a real product, service or command.
 */
class ProtocolDefinitionTest {

    @Test
    fun noCommandOutsideTheRegisteredSetIsEverExposed() {
        val definition = readOnlyDefinition()

        assertNotNull(definition.command("readExampleStatus"))
        assertNull(definition.command("writeExampleLevel"))
        assertNull(definition.command(""))
        assertNull(definition.command("readExampleStatusAlso"))
    }

    @Test
    fun aCallerCannotSmuggleACommandIntoADefinitionItAlreadyBuiltTheMapFrom() {
        val commands = mutableMapOf("readExampleStatus" to readCommand("readExampleStatus"))
        val definition = definition(commands = commands)

        commands["injected"] = readCommand("injected")

        // The record copied the map, so every lookup and derived fact reports only what the
        // definition was built with; otherwise a protocol could grow commands after review.
        assertNull(definition.command("injected"))
        assertEquals(setOf("readExampleStatus"), definition.registeredCommandIds)
        assertEquals(setOf(BATTERY), definition.mappedFeatures)
    }

    @Test
    fun aReadOnlyDefinitionReportsNoWriteSupport() {
        val definition = readOnlyDefinition()
        val battery = assertNotNull(definition.mappingFor(BATTERY))

        assertFalse(definition.supportsWrites)
        assertFalse(battery.hasWriteCommand)
        // A response shape existing for a read command is not write support either.
        assertNotNull(definition.response("readExampleStatus"))
        assertNull(definition.response("writeExampleLevel"))
    }

    @Test
    fun aSingleWriteMappingIsEnoughToReportWriteSupport() {
        val definition = definition(
            commands = mapOf(
                "readExampleStatus" to readCommand("readExampleStatus"),
                "writeExampleLevel" to writeCommand("writeExampleLevel"),
            ),
            mappings = mapOf(
                BATTERY to CapabilityMapping(
                    feature = BATTERY,
                    readCommandId = "readExampleStatus",
                    writeCommandId = "writeExampleLevel",
                    effectClass = EffectClass.SIDE_EFFECTING_WRITE,
                ),
            ),
        )

        assertTrue(definition.supportsWrites)
    }

    @Test
    fun aMappingMayNotPointAtACommandTheProtocolNeverDefined() {
        val dangling = assertFailsWith<IllegalArgumentException> {
            definition(
                commands = mapOf("readExampleStatus" to readCommand("readExampleStatus")),
                mappings = mapOf(
                    BATTERY to CapabilityMapping(
                        feature = BATTERY,
                        readCommandId = "readExampleStatus",
                        writeCommandId = "writeExampleLevel",
                        effectClass = EffectClass.SIDE_EFFECTING_WRITE,
                    ),
                ),
            )
        }

        assertTrue(dangling.message.orEmpty().contains("writeExampleLevel"), dangling.message.orEmpty())
    }

    @Test
    fun aResponseShapeForAnUndefinedCommandIsRefused() {
        assertFailsWith<IllegalArgumentException> {
            definition(
                commands = mapOf("readExampleStatus" to readCommand("readExampleStatus")),
                responses = mapOf(
                    "readExampleEcho" to ResponseDefinition("readExampleEcho", listOf("value"), false),
                ),
            )
        }
    }

    @Test
    fun aMapKeyThatDisagreesWithItsRecordIsRefusedEverywhereItCan() {
        assertFailsWith<IllegalArgumentException> {
            definition(commands = mapOf("wrong-key" to readCommand("readExampleStatus")))
        }
        assertFailsWith<IllegalArgumentException> {
            val commands = mapOf("readExampleStatus" to readCommand("readExampleStatus"))
            definition(
                commands = commands,
                responses = mapOf("other-key" to ResponseDefinition("readExampleStatus", listOf("v"), false)),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            definition(
                commands = mapOf("readExampleStatus" to readCommand("readExampleStatus")),
                mappings = mapOf(
                    ANC to CapabilityMapping(BATTERY, "readExampleStatus", null, EffectClass.READ),
                ),
            )
        }
    }

    @Test
    fun anAbsentMappingIsALookupMissAndNotAnUnsupportedVerdict() {
        val definition = readOnlyDefinition()

        // Unmodelled is not unsupported: the caller must keep the capability UNKNOWN
        // (PROTO-VENDOR-003, master section 53).
        assertNull(definition.mappingFor(ANC))
        assertNull(definition.mappingFor(VENDOR_FEATURE))
        assertNotNull(definition.mappingFor(BATTERY))
    }

    @Test
    fun coreAndVendorFeaturesAreSeparateKeysInOneDefinition() {
        val definition = definition(
            commands = mapOf(
                "readExampleStatus" to readCommand("readExampleStatus"),
                "readExampleVendorMode" to readCommand("readExampleVendorMode"),
            ),
            mappings = mapOf(
                BATTERY to CapabilityMapping(BATTERY, "readExampleStatus", null, EffectClass.READ),
                VENDOR_FEATURE to CapabilityMapping(
                    VENDOR_FEATURE,
                    "readExampleVendorMode",
                    null,
                    EffectClass.READ,
                ),
            ),
        )

        val core = assertNotNull(definition.mappingFor(BATTERY))
        val vendor = assertNotNull(definition.mappingFor(VENDOR_FEATURE))

        assertFalse(core.isVendorExtension)
        assertTrue(vendor.isVendorExtension)
        assertEquals("readExampleStatus", core.readCommandId)
        assertEquals("readExampleVendorMode", vendor.readCommandId)
        assertNotEquals(core.feature, vendor.feature)
    }

    @Test
    fun aStandardProtocolCarriesNoVendorAndAVendorProtocolNamesOne() {
        val standard = definition(vendor = null)
        val vendorSpecific = definition(vendor = "example-vendor")

        assertNull(standard.vendor)
        assertEquals("example-vendor", vendorSpecific.vendor)
        assertFailsWith<IllegalArgumentException> { definition(vendor = "  ") }
    }

    @Test
    fun aProtocolMustKnowWhichChannelItTravelsOver() {
        assertFailsWith<IllegalArgumentException> { definition(transport = TransportKind.UNKNOWN) }

        val classic = definition(transport = TransportKind.RFCOMM)
        val proprietary = definition(transport = TransportKind.VENDOR_SPECIFIC)

        assertEquals(TransportKind.RFCOMM, classic.transport)
        assertEquals(TransportKind.VENDOR_SPECIFIC, proprietary.transport)
    }

    @Test
    fun aVersionIsEitherReportedOrAbsentAndNeverBlank() {
        assertNull(definition(version = null).version)
        assertEquals("example-1", definition(version = "example-1").version)
        assertFailsWith<IllegalArgumentException> { definition(version = " ") }
    }

    private fun readOnlyDefinition(): ProtocolDefinition = definition(
        commands = mapOf("readExampleStatus" to readCommand("readExampleStatus")),
        responses = mapOf(
            "readExampleStatus" to ResponseDefinition("readExampleStatus", listOf("level"), true),
        ),
        mappings = mapOf(
            BATTERY to CapabilityMapping(BATTERY, "readExampleStatus", null, EffectClass.READ),
        ),
    )

    private fun readCommand(id: String): CommandDefinition = CommandDefinition(
        id = id,
        displayName = "Example read $id",
        effectClass = EffectClass.READ,
        timeoutMillis = 400,
        maxReadAttempts = 2,
        confidence = VerificationLevel.IMPLEMENTED,
    )

    private fun writeCommand(id: String): CommandDefinition = CommandDefinition(
        id = id,
        displayName = "Example write $id",
        effectClass = EffectClass.SIDE_EFFECTING_WRITE,
        timeoutMillis = 400,
        maxReadAttempts = null,
        confidence = VerificationLevel.IMPLEMENTED,
    )

    private fun definition(
        commands: Map<String, CommandDefinition> = mapOf(
            "readExampleStatus" to readCommand("readExampleStatus"),
        ),
        responses: Map<String, ResponseDefinition> = emptyMap(),
        mappings: Map<FeatureId, CapabilityMapping> = mapOf(
            BATTERY to CapabilityMapping(BATTERY, "readExampleStatus", null, EffectClass.READ),
        ),
        protocolId: String = "example-vendor.test-protocol",
        vendor: String? = "example-vendor",
        transport: TransportKind = TransportKind.GATT,
        version: String? = null,
        confidence: VerificationLevel = VerificationLevel.INFERRED,
    ): ProtocolDefinition = ProtocolDefinition(
        protocolId = protocolId,
        displayName = "Example test protocol",
        vendor = vendor,
        transport = transport,
        version = version,
        commands = commands,
        responses = responses,
        capabilityMappings = mappings,
        confidence = confidence,
    )

    private companion object {
        val BATTERY: FeatureId = FeatureId.of("power", "battery")
        val ANC: FeatureId = FeatureId.of("noise-control", "anc")
        val VENDOR_FEATURE: FeatureId = FeatureId.ofVendor("example-vendor", "some-feature")
    }
}
