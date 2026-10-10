package com.omnibuds.core.platform

import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.state.VerificationLevel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phase 46: Tests verifying Kotlin Multiplatform abstractions and platform-independent boundaries.
 */
class PlatformIndependenceTest {

    @Test
    fun platformTypeIdentifiesDesktopVsMobileAccurately() {
        assertTrue(PlatformType.ANDROID.isMobile)
        assertFalse(PlatformType.ANDROID.isDesktop)

        assertTrue(PlatformType.LINUX.isDesktop)
        assertFalse(PlatformType.LINUX.isMobile)

        assertTrue(PlatformType.MACOS.isDesktop)
        assertFalse(PlatformType.MACOS.isMobile)

        assertTrue(PlatformType.WINDOWS.isDesktop)
        assertFalse(PlatformType.WINDOWS.isMobile)

        assertTrue(PlatformType.DESKTOP_GENERIC.isDesktop)
        assertFalse(PlatformType.DESKTOP_GENERIC.isMobile)

        assertFalse(PlatformType.UNKNOWN.isDesktop)
        assertFalse(PlatformType.UNKNOWN.isMobile)
    }

    @Test
    fun platformDescriptorCapturesMetadataWithoutFabrication() {
        val unobserved = PlatformDescriptor.unobserved()
        assertEquals(PlatformType.UNKNOWN, unobserved.platformType)
        assertNull(unobserved.apiLevel)
        assertNull(unobserved.osVersion)
        assertFalse(unobserved.capabilities.isObserved)

        val desktopDescriptor = PlatformDescriptor(
            platformType = PlatformType.LINUX,
            osName = "Linux",
            osVersion = "6.8.0",
            architecture = "x86_64",
            candidateTransports = setOf(TransportKind.CLASSIC_BLUETOOTH, TransportKind.BLE),
        )

        assertTrue(desktopDescriptor.isDesktop)
        assertFalse(desktopDescriptor.isMobile)
        assertEquals("Linux", desktopDescriptor.osName)
        assertEquals("6.8.0", desktopDescriptor.osVersion)
        assertEquals("x86_64", desktopDescriptor.architecture)
        assertContainsTransport(desktopDescriptor.candidateTransports, TransportKind.CLASSIC_BLUETOOTH)
    }

    @Test
    fun deterministicIdentifierSourceProvidesSequentialIds() {
        val source = DeterministicIdentifierSource(defaultPrefix = "test-op")

        val id1 = source.generateOperationId()
        val id2 = source.generateOperationId()
        val custom = source.generateOperationId(prefix = "custom")

        assertEquals("test-op-1", id1)
        assertEquals("test-op-2", id2)
        assertEquals("custom-3", custom)

        val nonce1 = source.generateNonce()
        val nonce2 = source.generateNonce()
        assertEquals("nonce-4", nonce1)
        assertEquals("nonce-5", nonce2)

        source.reset(10L)
        assertEquals("test-op-10", source.generateOperationId())
    }

    @Test
    fun inMemoryStoragePortSatisfiesStorageContract() = runTest {
        val storage = InMemoryStoragePort()

        val getMissing = storage.get("missing.key")
        val successMissing = assertIs<OperationOutcome.Success<String?>>(getMissing)
        assertNull(successMissing.value)

        val setResult = storage.set("device.preferred_codec", "LDAC")
        assertIs<OperationOutcome.Success<Unit>>(setResult)

        val getStored = storage.get("device.preferred_codec")
        val successStored = assertIs<OperationOutcome.Success<String?>>(getStored)
        assertEquals("LDAC", successStored.value)

        val containsBefore = storage.contains("device.preferred_codec")
        assertTrue(assertIs<OperationOutcome.Success<Boolean>>(containsBefore).value)

        val removeResult = storage.remove("device.preferred_codec")
        assertIs<OperationOutcome.Success<Unit>>(removeResult)

        val containsAfter = storage.contains("device.preferred_codec")
        assertFalse(assertIs<OperationOutcome.Success<Boolean>>(containsAfter).value)

        storage.set("key1", "val1")
        storage.set("key2", "val2")
        storage.clear()
        assertFalse(assertIs<OperationOutcome.Success<Boolean>>(storage.contains("key1")).value)
        assertFalse(assertIs<OperationOutcome.Success<Boolean>>(storage.contains("key2")).value)
    }

    @Test
    fun platformLifecycleStateDistinguishesInteractive() {
        assertTrue(PlatformLifecycleState.FOREGROUND.isInteractive)
        assertFalse(PlatformLifecycleState.BACKGROUND.isInteractive)
        assertFalse(PlatformLifecycleState.SUSPENDED.isInteractive)
        assertFalse(PlatformLifecycleState.TERMINATING.isInteractive)
    }

    @Test
    fun noOpDiagnosticSinkSafelyIgnoresEvents() {
        assertFalse(NoOpDiagnosticSink.isEnabled(VerificationLevel.LAB_TESTED))
        val outcome = NoOpDiagnosticSink.emit(VerificationLevel.LAB_TESTED, "tag", "msg")
        assertIs<OperationOutcome.Success<Unit>>(outcome)
    }

    @Test
    fun platformCapabilitiesCarryPlatformType() {
        val unobserved = BluetoothPlatformCapabilities.unobserved()
        assertEquals(PlatformType.UNKNOWN, unobserved.platformType)

        val desktopCaps = unobserved.copy(platformType = PlatformType.LINUX)
        assertEquals(PlatformType.LINUX, desktopCaps.platformType)
    }

    private fun assertContainsTransport(transports: Set<TransportKind>, target: TransportKind) {
        assertTrue(transports.contains(target), "Expected set to contain $target, was $transports")
    }
}
