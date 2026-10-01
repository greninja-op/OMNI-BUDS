package com.omnibuds.core.config

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.common.TransportKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins Phase 1 execution prompt section 39: application, device and protocol
 * configuration are three different concepts and must not be interchangeable.
 *
 * The primary guarantee is the type system itself - the three types share no
 * supertype above `Any`, have disjoint property sets, and passing one where another is
 * expected is a compile error rather than a review finding. The assertions below are
 * regression pins against a future refactor that reintroduces a shared wrapper, a
 * common base type or a catch-all "config bag".
 *
 * Traceability note for the orchestrator: names follow specs.md section 1.4
 * (behavior-plus-condition) and carry no `TEST-P1-<NNN>` id, because the Phase 1
 * test-plan does not exist yet and numbering those ids is a documents-side decision.
 */
class ConfigurationSeparationTest {

    private val anc: FeatureId = FeatureId.of("noise-control", "anc")
    private val leftTap: FeatureId = FeatureId.of("input", "gesture-double-tap-left")

    private fun anApplication(): ApplicationConfiguration = ApplicationConfiguration.defaults()

    private fun aDevice(): DeviceConfiguration = DeviceConfiguration.of(
        mapOf(anc to ConfigurationValue.ModeValue("adaptive", "Adaptive")),
    )

    private fun aProtocol(): ProtocolConfiguration = ProtocolConfiguration(
        protocolId = "vendor-example-control",
        protocolVersion = "1.2",
        transport = TransportKind.GATT,
        timeoutMillis = 1_500L,
        maxReadAttempts = 2,
    )

    @Test
    fun theThreeConfigurationTypesAreDistinctTypes() {
        assertNotEquals(ApplicationConfiguration::class.simpleName, DeviceConfiguration::class.simpleName)
        assertNotEquals(DeviceConfiguration::class.simpleName, ProtocolConfiguration::class.simpleName)
        assertNotEquals(ApplicationConfiguration::class.simpleName, ProtocolConfiguration::class.simpleName)
    }

    @Test
    fun noConfigurationTypeEqualsAnotherEvenWithSimilarIntent() {
        // Read as values: a configuration of one kind is never equal to, and so never a
        // stand-in for, a configuration of another kind.
        assertNotEquals<Any>(anApplication(), aDevice())
        assertNotEquals<Any>(aDevice(), aProtocol())
        assertNotEquals<Any>(anApplication(), aProtocol())
    }

    @Test
    fun eachConfigurationTypeHasAnIndependentConstructionPath() {
        val application = anApplication()
        val device = aDevice()
        val protocol = aProtocol()

        // Application config is about OmniBuds' own behaviour.
        assertFalse(application.debugLoggingEnabled)
        assertEquals(DiagnosticMode.OFF, application.diagnosticMode)

        // Device config is keyed by feature identity.
        assertEquals(ConfigurationValue.ModeValue("adaptive", "Adaptive"), device.valueFor(anc))

        // Protocol config is keyed by protocol and transport, with no feature identity.
        assertEquals("vendor-example-control", protocol.protocolId)
        assertEquals(TransportKind.GATT, protocol.transport)
    }

    @Test
    fun defaultsLeaveDebugLoggingOffAndDiagnosticModeOff() {
        val defaults = ApplicationConfiguration.defaults()

        assertFalse(defaults.debugLoggingEnabled)
        assertEquals(DiagnosticMode.OFF, defaults.diagnosticMode)
        assertFalse(defaults.diagnosticMode.requiresOptIn)
        assertTrue(defaults.featureFlags.isEmpty())
    }

    @Test
    fun diagnosticsOffOpensNoGateAndNeedsNoOptInRecord() {
        val config = ApplicationConfiguration(
            debugLoggingEnabled = false,
            diagnosticMode = DiagnosticMode.OFF,
            featureFlags = emptySet(),
        )

        assertFalse(config.isGateOpen("ui.anything"))
        assertNull(config.flagFor("ui.anything"))
    }

    @Test
    fun everyDiagnosticModeAboveOffDeclaresAnOptInRequirement() {
        assertTrue(DiagnosticMode.entries.filter { it != DiagnosticMode.OFF }.all { it.requiresOptIn })
    }

    @Test
    fun valueForAnAbsentFeatureIsNullAndNeverAnInventedDefault() {
        assertNull(aDevice().valueFor(leftTap))
        assertNull(DeviceConfiguration.empty().valueFor(anc))
    }

    @Test
    fun deviceConfigurationSnapshotsTheSourceMapSoLaterMutationCannotReachIt() {
        val source = mutableMapOf<FeatureId, ConfigurationValue>(
            anc to ConfigurationValue.BooleanValue(true),
        )
        val device = DeviceConfiguration.of(source)

        source[leftTap] = ConfigurationValue.IntValue(3)

        assertNull(device.valueFor(leftTap))
        assertEquals(1, device.entries.size)
        assertEquals(ConfigurationValue.BooleanValue(true), device.valueFor(anc))
    }

    @Test
    fun protocolConfigurationRejectsANonPositiveTimeout() {
        assertFailsWith<IllegalArgumentException> {
            ProtocolConfiguration("p", null, TransportKind.RFCOMM, 0L, null)
        }
        assertFailsWith<IllegalArgumentException> {
            ProtocolConfiguration("p", null, TransportKind.RFCOMM, -250L, null)
        }
    }

    @Test
    fun protocolConfigurationRejectsAReadBudgetOutsideTheOneToFiveWindow() {
        assertFailsWith<IllegalArgumentException> {
            ProtocolConfiguration("p", null, TransportKind.GATT, null, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            ProtocolConfiguration("p", null, TransportKind.GATT, null, 6)
        }

        assertEquals(1, aProtocolWithAttempts(1).maxReadAttempts)
        assertEquals(5, aProtocolWithAttempts(5).maxReadAttempts)
    }

    @Test
    fun unreportedProtocolFactsStayNullInsteadOfBecomingPlaceholderValues() {
        val config = ProtocolConfiguration(
            protocolId = "vendor-example-control",
            protocolVersion = null,
            transport = TransportKind.UNKNOWN,
            timeoutMillis = null,
            maxReadAttempts = null,
        )

        assertNull(config.protocolVersion)
        assertNull(config.timeoutMillis)
        assertNull(config.maxReadAttempts)
    }

    @Test
    fun protocolConfigurationRejectsABlankProtocolIdOrBlankVersion() {
        assertFailsWith<IllegalArgumentException> {
            ProtocolConfiguration(" ", null, TransportKind.GATT, null, null)
        }
        assertFailsWith<IllegalArgumentException> {
            ProtocolConfiguration("p", "", TransportKind.GATT, null, null)
        }
    }

    @Test
    fun modeValueKeepsTheMachineKeyApartFromTheDisplayLabel() {
        val mode = ConfigurationValue.ModeValue("adaptive", "Adaptive")

        assertNotEquals(mode.technicalName, mode.displayName)
        assertFailsWith<IllegalArgumentException> {
            ConfigurationValue.ModeValue(" ", "Adaptive")
        }
    }

    private fun aProtocolWithAttempts(attempts: Int): ProtocolConfiguration = ProtocolConfiguration(
        protocolId = "p",
        protocolVersion = null,
        transport = TransportKind.GATT,
        timeoutMillis = null,
        maxReadAttempts = attempts,
    )
}
