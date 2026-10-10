package com.omnibuds.core.platform.desktop

import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.platform.PlatformType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopBluetoothLayerTest {

    // 1. Adapter Availability and Status
    @Test
    fun adapterAvailabilityDistinguishesStatesTruthfully() {
        assertTrue(DesktopBluetoothAvailability.AVAILABLE.isUsable)
        assertFalse(DesktopBluetoothAvailability.DISABLED.isUsable)
        assertFalse(DesktopBluetoothAvailability.UNAVAILABLE.isUsable)
        assertFalse(DesktopBluetoothAvailability.PERMISSION_REQUIRED.isUsable)
        assertFalse(DesktopBluetoothAvailability.PERMISSION_DENIED.isUsable)
        assertFalse(DesktopBluetoothAvailability.UNSUPPORTED.isUsable)
        assertFalse(DesktopBluetoothAvailability.INITIALIZING.isUsable)
        assertFalse(DesktopBluetoothAvailability.UNKNOWN.isUsable)

        assertTrue(DesktopBluetoothAvailability.PERMISSION_REQUIRED.isAuthorizationIssue)
        assertTrue(DesktopBluetoothAvailability.PERMISSION_DENIED.isAuthorizationIssue)
        assertFalse(DesktopBluetoothAvailability.DISABLED.isAuthorizationIssue)

        assertTrue(DesktopBluetoothAvailability.UNKNOWN.isIndeterminate)
        assertTrue(DesktopBluetoothAvailability.INITIALIZING.isIndeterminate)
        assertFalse(DesktopBluetoothAvailability.AVAILABLE.isIndeterminate)
    }

    @Test
    fun unsupportedAdapterTruthfullyReportsUnsupported() = runTest {
        val adapter = UnsupportedDesktopBluetoothAdapter()
        assertEquals(DesktopBluetoothAvailability.UNSUPPORTED, adapter.checkAvailability())
        assertEquals(DesktopBluetoothAvailability.UNSUPPORTED, adapter.observeAvailability().first())

        val caps = assertIs<OperationOutcome.Success<*>>(adapter.capabilities()).value
        assertNotNull(caps)

        val discOutcome = adapter.startDiscovery()
        val error = assertIs<OperationOutcome.Failure>(discOutcome).error
        assertEquals(OmniBudsErrorCategory.ADAPTER_UNAVAILABLE, error.category)

        val openOutcome = adapter.openTransportSession("dev1", TransportKind.BLE)
        val openError = assertIs<OperationOutcome.Failure>(openOutcome).error
        assertEquals(OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE, openError.category)
    }

    // 2. Linux BlueZ Adapter Boundary
    @Test
    fun linuxBluezAdapterReportsAvailabilityBasedOnDaemonAndRadio() = runTest {
        val noDaemon = LinuxBlueZDesktopAdapter(isDaemonAvailable = false, isAdapterPowered = false)
        assertEquals(DesktopBluetoothAvailability.UNAVAILABLE, noDaemon.checkAvailability())

        val poweredOff = LinuxBlueZDesktopAdapter(isDaemonAvailable = true, isAdapterPowered = false)
        assertEquals(DesktopBluetoothAvailability.DISABLED, poweredOff.checkAvailability())

        val ready = LinuxBlueZDesktopAdapter(isDaemonAvailable = true, isAdapterPowered = true)
        assertEquals(DesktopBluetoothAvailability.AVAILABLE, ready.checkAvailability())

        assertEquals(PlatformType.LINUX, ready.platformDescriptor.platformType)
        assertTrue(ready.platformDescriptor.candidateTransports.contains(TransportKind.CLASSIC_BLUETOOTH))
    }

    // 3. macOS CoreBluetooth Adapter Boundary
    @Test
    fun macOsCoreBluetoothAdapterReportsPermissionAndPowerAccurately() = runTest {
        val noTcc = MacOsCoreBluetoothDesktopAdapter(isTccAuthorized = false, isBluetoothPoweredOn = true)
        assertEquals(DesktopBluetoothAvailability.PERMISSION_REQUIRED, noTcc.checkAvailability())

        val unpowered = MacOsCoreBluetoothDesktopAdapter(isTccAuthorized = true, isBluetoothPoweredOn = false)
        assertEquals(DesktopBluetoothAvailability.DISABLED, unpowered.checkAvailability())

        val ready = MacOsCoreBluetoothDesktopAdapter(isTccAuthorized = true, isBluetoothPoweredOn = true)
        assertEquals(DesktopBluetoothAvailability.AVAILABLE, ready.checkAvailability())

        // Verify discovery rejection when permission missing
        val deniedDisc = noTcc.startDiscovery()
        val discError = assertIs<OperationOutcome.Failure>(deniedDisc).error
        assertEquals(OmniBudsErrorCategory.PERMISSION_DENIED, discError.category)
    }

    // 4. Windows WinRT Adapter Boundary
    @Test
    fun windowsWinRtAdapterReportsRadioPresenceAndState() = runTest {
        val noRadio = WindowsWinRtDesktopAdapter(isRadioPresent = false, isRadioOn = false)
        assertEquals(DesktopBluetoothAvailability.UNAVAILABLE, noRadio.checkAvailability())

        val radioOff = WindowsWinRtDesktopAdapter(isRadioPresent = true, isRadioOn = false)
        assertEquals(DesktopBluetoothAvailability.DISABLED, radioOff.checkAvailability())

        val ready = WindowsWinRtDesktopAdapter(isRadioPresent = true, isRadioOn = true)
        assertEquals(DesktopBluetoothAvailability.AVAILABLE, ready.checkAvailability())
    }

    // 5. Simulated Adapter Discovery Lifecycle & Cleanup
    @Test
    fun discoverySessionStartsStreamsAndCleansUpOnStop() = runTest {
        val adapter = DeterministicSimulatedDesktopAdapter()
        val sessionOutcome = adapter.startDiscovery()
        val session = assertIs<OperationOutcome.Success<DesktopDeviceDiscoverySession>>(sessionOutcome).value

        assertTrue(session.isActive)
        assertTrue(adapter.isDiscoveryActive())

        val emittedObservations = mutableListOf<DesktopDiscoveredDevice>()
        val collectJob = launch {
            session.observations.collect { emittedObservations.add(it) }
        }

        val dev1 = DesktopDiscoveredDevice(
            platformIdentifier = "/org/bluez/hci0/dev_11_22_33_44_55_66",
            name = "Studio Buds",
            bluetoothAddress = "11:22:33:44:55:66",
            observedTransports = setOf(TransportKind.BLE),
            rssiDbm = -65,
        )
        adapter.emitDiscoveredDevice(dev1)
        testScheduler.advanceUntilIdle()

        val stopOutcome = session.stop()
        assertIs<OperationOutcome.Success<Unit>>(stopOutcome)
        assertFalse(session.isActive)
        assertFalse(adapter.isDiscoveryActive())

        // Late event after stop must not be processed
        val dev2 = DesktopDiscoveredDevice(
            platformIdentifier = "/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF",
            name = "Late Device",
        )
        adapter.emitDiscoveredDevice(dev2)
        testScheduler.advanceUntilIdle()

        collectJob.cancel()
        assertEquals(1, emittedObservations.size)
        assertEquals("Studio Buds", emittedObservations.first().name)
    }

    @Test
    fun adapterDisabledDuringDiscoveryCancelsSessionAutomatically() = runTest {
        val adapter = DeterministicSimulatedDesktopAdapter()
        val sessionOutcome = adapter.startDiscovery()
        val session = assertIs<OperationOutcome.Success<DesktopDeviceDiscoverySession>>(sessionOutcome).value

        assertTrue(session.isActive)

        // Adapter disabled via hardware switch or OS radio toggle
        adapter.setAvailability(DesktopBluetoothAvailability.DISABLED)

        assertFalse(session.isActive)
        assertFalse(adapter.isDiscoveryActive())
    }

    @Test
    fun duplicateDiscoveryStartFailsWithStructuredError() = runTest {
        val adapter = DeterministicSimulatedDesktopAdapter()
        val firstOutcome = adapter.startDiscovery()
        assertIs<OperationOutcome.Success<*>>(firstOutcome)

        val secondOutcome = adapter.startDiscovery()
        val error = assertIs<OperationOutcome.Failure>(secondOutcome).error
        assertEquals(OmniBudsErrorCategory.INVALID_STATE, error.category)
    }

    // 6. Transport Session Management and Concurrency Guard
    @Test
    fun transportSessionLifecycleAndDuplicatePrevention() = runTest {
        val adapter = DeterministicSimulatedDesktopAdapter()

        val outcome1 = adapter.openTransportSession("dev1", TransportKind.BLE)
        val session1 = assertIs<OperationOutcome.Success<DesktopTransportSession>>(outcome1).value

        assertTrue(session1.isConnected)
        assertEquals(1, adapter.activeSessionCount())

        // Attempting duplicate session for same device must fail with invalid state error
        val outcomeDup = adapter.openTransportSession("dev1", TransportKind.BLE)
        val dupError = assertIs<OperationOutcome.Failure>(outcomeDup).error
        assertEquals(OmniBudsErrorCategory.INVALID_STATE, dupError.category)

        // Closing session releases lock
        session1.close()
        assertFalse(session1.isConnected)
        assertEquals(0, adapter.activeSessionCount())

        // Re-opening after close succeeds
        val outcomeReopen = adapter.openTransportSession("dev1", TransportKind.BLE)
        val sessionReopen = assertIs<OperationOutcome.Success<DesktopTransportSession>>(outcomeReopen).value
        assertTrue(sessionReopen.isConnected)
        assertEquals(1, adapter.activeSessionCount())
        sessionReopen.close()
    }

    @Test
    fun unsupportedTransportKindIsRejected() = runTest {
        val adapter = DeterministicSimulatedDesktopAdapter()
        // LE Audio is not in the candidate transports of this fake
        val outcome = adapter.openTransportSession("dev1", TransportKind.LE_AUDIO)
        val error = assertIs<OperationOutcome.Failure>(outcome).error
        assertEquals(OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE, error.category)
    }

    // 7. Desktop Discovered Device Integrity and Privacy Redaction
    @Test
    fun discoveredDeviceProtectsPrivacyAndMaintainsIntegrity() {
        val deviceWithName = DesktopDiscoveredDevice(
            platformIdentifier = "uuid-1234",
            name = "WH-1000XM5",
            bluetoothAddress = "AA:BB:CC:DD:EE:FF",
        )
        assertEquals("WH-1000XM5", deviceWithName.safeLabel)

        val deviceWithoutName = DesktopDiscoveredDevice(
            platformIdentifier = "12345678-90ab-cdef-1234-567890abcdef",
            name = null,
            bluetoothAddress = "AA:BB:CC:DD:EE:FF",
        )
        assertEquals("Device[12345678...]", deviceWithoutName.safeLabel)
    }

    // 8. Desktop Connection Session State Model
    @Test
    fun desktopConnectionSessionStateDistinguishesOsAndVendorControl() {
        val osConnectedOnly = DesktopConnectionSessionState(
            platformIdentifier = "dev1",
            isConnectedAtOsLevel = true,
            hasActiveTransportSession = false,
            hasVendorProtocolSession = false,
        )
        assertFalse(osConnectedOnly.isVendorControllable)

        val transportOpenNoProtocol = DesktopConnectionSessionState(
            platformIdentifier = "dev1",
            isConnectedAtOsLevel = true,
            activeTransportKind = TransportKind.BLE,
            hasActiveTransportSession = true,
            hasVendorProtocolSession = false,
        )
        assertFalse(transportOpenNoProtocol.isVendorControllable)

        val fullyControllable = DesktopConnectionSessionState(
            platformIdentifier = "dev1",
            isConnectedAtOsLevel = true,
            activeTransportKind = TransportKind.BLE,
            hasActiveTransportSession = true,
            hasVendorProtocolSession = true,
        )
        assertTrue(fullyControllable.isVendorControllable)
    }
}
