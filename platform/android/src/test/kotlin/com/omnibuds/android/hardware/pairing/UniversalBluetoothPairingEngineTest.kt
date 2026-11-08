package com.omnibuds.android.hardware.pairing

import com.omnibuds.android.presentation.devices.AndroidDiscoveredDevice
import com.omnibuds.android.ui.compose.screens.calculateCollisionFreePlacements
import com.omnibuds.core.common.TransportKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UniversalBluetoothPairingEngineTest {

    // -- 1. Smart Audio Device Filter Tests -----------------------------------

    @Test
    fun `accepts known audio earbud and headphone brands by name`() {
        val acceptedNames = listOf(
            "realme Buds T300",
            "AirPods Pro",
            "AirPods Max",
            "OnePlus Buds Pro 2",
            "Sony WH-1000XM5",
            "Sony WF-1000XM4",
            "Sony WI-C100",
            "Galaxy Buds2 Pro",
            "Nothing Ear (2)",
            "CMF Buds Pro 2",
            "Bose QuietComfort 45",
            "Sennheiser Momentum 4",
            "JBL Live 660NC",
            "boAt Rockerz 255 Pro+",
            "OnePlus Bullets Wireless Z2",
            "Soundcore Liberty 4 NC",
            "EarFun Air Pro 3",
            "Shokz OpenRun Pro",
            "Beats Studio Pro",
            "Powerbeats Pro",
            "Edifier W820NB Plus",
            "Pixel Buds Pro",
            "Huawei FreeBuds Pro 3",
            "Redmi Buds 5 Pro",
            "SoundPEATS Air4 Pro",
            "Marshall Major IV",
            "LinkBuds S",
            "Generic TWS Earbuds",
        )

        for (name in acceptedNames) {
            val isAudio = UniversalBluetoothPairingEngine.isAudioWearableDevice(
                name = name,
                bluetoothClass = null,
            )
            assertTrue(isAudio, "Expected '$name' to be recognized as an audio wearable")
        }
    }

    @Test
    fun `accepts devices advertising Fast Pair UUID 0xFE2C without recognized name`() {
        val isAudio = UniversalBluetoothPairingEngine.isAudioWearableDevice(
            name = null,
            bluetoothClass = null,
            rawUuids = listOf(UniversalBluetoothPairingEngine.FAST_PAIR_SERVICE_UUID),
        )
        assertTrue(isAudio, "Fast Pair advertisement (0xFE2C) must be accepted even without a device name")
    }

    @Test
    fun `accepts devices advertising A2DP Sink or LE Audio UUIDs`() {
        val a2dpAccepted = UniversalBluetoothPairingEngine.isAudioWearableDevice(
            name = "Audio Sink Device",
            bluetoothClass = null,
            rawUuids = listOf(UniversalBluetoothPairingEngine.A2DP_SINK_UUID),
        )
        assertTrue(a2dpAccepted, "A2DP Sink UUID must be accepted")

        val leAudioAccepted = UniversalBluetoothPairingEngine.isAudioWearableDevice(
            name = null,
            bluetoothClass = null,
            rawUuids = listOf(UniversalBluetoothPairingEngine.LE_AUDIO_ASCS_UUID),
        )
        assertTrue(leAudioAccepted, "LE Audio ASCS UUID (0x184E) must be accepted")
    }

    @Test
    fun `accepts Apple AirPods proximity packets in manufacturer data`() {
        val appleAirPodsData = mapOf(
            0x004C to byteArrayOf(0x07, 0x19, 0x01, 0x02, 0x20),
        )
        val isAudio = UniversalBluetoothPairingEngine.isAudioWearableDevice(
            name = null,
            bluetoothClass = null,
            manufacturerData = appleAirPodsData,
        )
        assertTrue(isAudio, "Apple Continuity proximity beacon (0x004C type 0x07) must be accepted")
    }

    @Test
    fun `strictly rejects car audio systems and infotainment kits`() {
        val carNames = listOf(
            "Toyota Touch 2",
            "Innova Crysta BT",
            "Fortuner Multimedia",
            "Hyundai Creta Audio",
            "Honda Handsfree",
            "Maruti Suzuki SmartPlay",
            "Kia Seltos AVNT",
            "BMW BT_Audio",
            "Audi MMI Navigation",
            "Ford SYNC",
            "Skoda Bolero",
            "Volkswagen Infotainment",
            "Car Kit BT-400",
            "Generic Car-Kit",
            "Mahindra Scorpio BT",
        )

        for (name in carNames) {
            val isAudio = UniversalBluetoothPairingEngine.isAudioWearableDevice(
                name = name,
                bluetoothClass = null,
            )
            assertFalse(isAudio, "Car system '$name' must be rejected from audio wearable discovery")
        }
    }

    @Test
    fun `strictly rejects TVs, smartwatches, and computing devices`() {
        val nonWearables = listOf(
            "Apple Watch Series 9",
            "Galaxy Watch 6",
            "Sony Bravia 4K TV",
            "LG Smart TV OLED",
            "Firestick Remote",
            "Roku TV",
            "MacBook Pro 16",
            "Dell XPS Laptop",
            "iPad Pro",
            "iPhone 15 Pro",
            "HP LaserJet Printer",
        )

        for (name in nonWearables) {
            val isAudio = UniversalBluetoothPairingEngine.isAudioWearableDevice(
                name = name,
                bluetoothClass = null,
            )
            assertFalse(isAudio, "Non-wearable device '$name' must be filtered out")
        }
    }

    @Test
    fun `rejects unknown random BLE beacons with null name and no audio UUIDs`() {
        val isAudio = UniversalBluetoothPairingEngine.isAudioWearableDevice(
            name = null,
            bluetoothClass = null,
            serviceUuids = null,
            manufacturerData = null,
        )
        assertFalse(isAudio, "Random BLE beacons without identification must be rejected")
    }

    // -- 2. Name Normalization & Deduplication Logic Tests --------------------

    @Test
    fun `normalizes device names accurately for cross-transport deduplication`() {
        assertEquals("realmebudst300", UniversalBluetoothPairingEngine.normalizeDeviceName("realme Buds T300"))
        assertEquals("airpodspro2", UniversalBluetoothPairingEngine.normalizeDeviceName("  AirPods Pro 2  "))
        assertEquals("sonywh1000xm5", UniversalBluetoothPairingEngine.normalizeDeviceName("Sony WH-1000XM5"))
        assertEquals("", UniversalBluetoothPairingEngine.normalizeDeviceName(null))
        assertEquals("", UniversalBluetoothPairingEngine.normalizeDeviceName("   "))
    }

    @Test
    fun `deduplicator upgrades BLE RPA to Classic BR-EDR address when matching real device name`() {
        val deduplicator = HybridDeviceDeduplicator()

        // Step 1: BLE RPA discovery (e.g. random private address)
        val bleBud = AndroidDiscoveredDevice(
            id = "E1:22:33:44:55:66",
            name = "realme Buds T300",
            address = "E1:22:33:44:55:66",
            rssi = -65,
            transportKind = TransportKind.BLE,
        )
        deduplicator.upsert(bleBud)

        assertEquals(1, deduplicator.currentDevices.size)
        assertEquals("E1:22:33:44:55:66", deduplicator.currentDevices[0].address)
        assertEquals(TransportKind.BLE, deduplicator.currentDevices[0].transportKind)

        // Step 2: Classic discovery with public BR/EDR bondable address and same physical name
        val classicBud = AndroidDiscoveredDevice(
            id = "AA:BB:CC:DD:EE:FF",
            name = "realme Buds T300",
            address = "AA:BB:CC:DD:EE:FF",
            rssi = -60,
            transportKind = TransportKind.CLASSIC_BLUETOOTH,
        )
        deduplicator.upsert(classicBud)

        // Must upgrade the entry to the Classic BR/EDR address!
        assertEquals(1, deduplicator.currentDevices.size, "BLE RPA must be deduplicated in favor of Classic address")
        assertEquals("AA:BB:CC:DD:EE:FF", deduplicator.currentDevices[0].address)
        assertEquals(TransportKind.CLASSIC_BLUETOOTH, deduplicator.currentDevices[0].transportKind)
    }

    @Test
    fun `deduplicator upgrades BLE RPA to Classic BR-EDR address when matching Fast Pair Model ID`() {
        val deduplicator = HybridDeviceDeduplicator()

        // BLE device advertising Fast Pair Model ID (0x42ABCD)
        val bleBud = AndroidDiscoveredDevice(
            id = "F0:11:22:33:44:55",
            name = null,
            address = "F0:11:22:33:44:55",
            rssi = -70,
            transportKind = TransportKind.BLE,
        )
        deduplicator.upsert(bleBud, fastPairModelId = "42ABCD")

        assertEquals(1, deduplicator.currentDevices.size)
        assertEquals("F0:11:22:33:44:55", deduplicator.currentDevices[0].address)

        // Classic inquiry discovers the physical earbud with the same Fast Pair ID
        val classicBud = AndroidDiscoveredDevice(
            id = "11:22:33:44:55:66",
            name = "realme Buds T300",
            address = "11:22:33:44:55:66",
            rssi = -68,
            transportKind = TransportKind.CLASSIC_BLUETOOTH,
        )
        deduplicator.upsert(classicBud, fastPairModelId = "42ABCD")

        assertEquals(1, deduplicator.currentDevices.size)
        assertEquals("11:22:33:44:55:66", deduplicator.currentDevices[0].address)
        assertEquals(TransportKind.CLASSIC_BLUETOOTH, deduplicator.currentDevices[0].transportKind)
    }

    @Test
    fun `preserves multiple distinct Classic devices sharing the same commercial model name`() {
        val deduplicator = HybridDeviceDeduplicator()

        // Device 1: User A's realme Buds T300
        val budA = AndroidDiscoveredDevice(
            id = "11:22:33:44:55:66",
            name = "realme Buds T300",
            address = "11:22:33:44:55:66",
            rssi = -55,
            transportKind = TransportKind.CLASSIC_BLUETOOTH,
        )
        deduplicator.upsert(budA)

        // Device 2: User B's realme Buds T300 in the same room
        val budB = AndroidDiscoveredDevice(
            id = "AA:BB:CC:DD:EE:FF",
            name = "realme Buds T300",
            address = "AA:BB:CC:DD:EE:FF",
            rssi = -75,
            transportKind = TransportKind.CLASSIC_BLUETOOTH,
        )
        deduplicator.upsert(budB)

        // Both distinct physical devices must be preserved and selectable
        assertEquals(2, deduplicator.currentDevices.size, "Distinct Classic MAC addresses must never be merged")
        val addresses = deduplicator.currentDevices.map { it.address }.toSet()
        assertTrue(addresses.contains("11:22:33:44:55:66"))
        assertTrue(addresses.contains("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `preserves multiple unnamed devices without collapsing them into one`() {
        val deduplicator = HybridDeviceDeduplicator()

        val dev1 = AndroidDiscoveredDevice(
            id = "11:22:33:44:55:66",
            name = null,
            address = "11:22:33:44:55:66",
            transportKind = TransportKind.BLE,
        )
        val dev2 = AndroidDiscoveredDevice(
            id = "AA:BB:CC:DD:EE:FF",
            name = null,
            address = "AA:BB:CC:DD:EE:FF",
            transportKind = TransportKind.BLE,
        )

        deduplicator.upsert(dev1)
        deduplicator.upsert(dev2)

        assertEquals(2, deduplicator.currentDevices.size, "Unnamed devices must not collide on fallback display name")
    }

    @Test
    fun `refreshes signal and updates name when name changed arrives`() {
        val deduplicator = HybridDeviceDeduplicator()

        val dev = AndroidDiscoveredDevice(
            id = "11:22:33:44:55:66",
            name = "Old Name",
            address = "11:22:33:44:55:66",
            rssi = -80,
            transportKind = TransportKind.CLASSIC_BLUETOOTH,
        )
        deduplicator.upsert(dev)

        // Fresh RSSI update
        val refreshed = dev.copy(rssi = -50)
        deduplicator.upsert(refreshed)
        assertEquals(-50, deduplicator.currentDevices[0].rssi)

        // Name update
        deduplicator.updateDeviceName("11:22:33:44:55:66", "realme Buds T300")
        assertEquals("realme Buds T300", deduplicator.currentDevices[0].name)
    }

    // -- 3. RememberedDevicesStore Persistence Tests --------------------------

    @Test
    fun `remembered devices store saves, retrieves, updates, and deletes devices`() {
        val store = RememberedDevicesStore()
        assertTrue(store.getAllDevices().isEmpty())

        val device1 = RememberedDevice(
            address = "AA:BB:CC:11:22:33",
            name = "realme Buds T300",
            formFactor = "STEM_IN_EAR",
            firstPairedTimestamp = 1000L,
            lastConnectedTimestamp = 1000L,
            autoReconnect = true,
        )
        store.saveDevice(device1)

        val retrieved = store.getDevice("AA:BB:CC:11:22:33")
        assertNotNull(retrieved)
        assertEquals("realme Buds T300", retrieved?.name)
        assertEquals("STEM_IN_EAR", retrieved?.formFactor)
        assertTrue(retrieved?.autoReconnect == true)

        // Update last connected
        store.updateLastConnected("AA:BB:CC:11:22:33", timestamp = 2000L)
        assertEquals(2000L, store.getDevice("AA:BB:CC:11:22:33")?.lastConnectedTimestamp)

        // Update auto reconnect
        store.setAutoReconnect("AA:BB:CC:11:22:33", enabled = false)
        assertFalse(store.getDevice("AA:BB:CC:11:22:33")?.autoReconnect == true)

        // Multiple devices ordering by last connected
        val device2 = RememberedDevice(
            address = "AA:BB:CC:44:55:66",
            name = "AirPods Pro",
            formFactor = "STEM_IN_EAR",
            lastConnectedTimestamp = 3000L,
        )
        store.saveDevice(device2)

        val all = store.getAllDevices()
        assertEquals(2, all.size)
        assertEquals("AirPods Pro", all[0].name) // 3000L > 2000L
        assertEquals("realme Buds T300", all[1].name)

        // Remove
        store.removeDevice("AA:BB:CC:11:22:33")
        assertNull(store.getDevice("AA:BB:CC:11:22:33"))
        assertEquals(1, store.getAllDevices().size)

        // Clear
        store.clear()
        assertTrue(store.getAllDevices().isEmpty())
    }

    // -- 4. Orbital Placement with Bonded Devices Tests -----------------------

    @Test
    fun `bonded devices surface on outer orbit with Paired category tag`() {
        val bondedBud = AndroidDiscoveredDevice(
            id = "bonded-address",
            name = "realme Buds T300",
            address = "AA:BB:CC:11:22:33",
            rssi = null,
            transportKind = TransportKind.CLASSIC_BLUETOOTH,
            isBonded = true,
        )
        val newBud = AndroidDiscoveredDevice(
            id = "new-address",
            name = "OnePlus Buds Pro",
            address = "AA:BB:CC:44:55:66",
            rssi = -50,
            transportKind = TransportKind.BLE,
            isBonded = false,
        )

        val placements = calculateCollisionFreePlacements(
            devices = listOf(bondedBud, newBud),
            maxRadiusPx = 300f,
            context = null,
        )

        val bondedPlacement = placements.first { it.device.id == "bonded-address" }
        assertEquals(2, bondedPlacement.orbitIndex, "Bonded device should be placed on outer orbit (index 2)")
        assertEquals("Paired", bondedPlacement.categoryTag, "Bonded device category tag should be 'Paired'")

        val newPlacement = placements.first { it.device.id == "new-address" }
        assertEquals(0, newPlacement.orbitIndex, "Strong RSSI (-50) should be on inner orbit (index 0)")
        assertEquals("TWS", newPlacement.categoryTag)
    }

    // -- 5. BondingStage States Representation Tests -------------------------

    @Test
    fun `bonding stage states provide accurate status messages`() {
        val idle = BondingStage.Idle
        assertEquals(BondingStage.Idle, idle)

        val bonding = BondingStage.Bonding(
            address = "AA:BB:CC:11:22:33",
            name = "realme Buds T300",
            message = "Pairing with realme Buds T300... Confirm on screen if prompted",
        )
        assertEquals("AA:BB:CC:11:22:33", bonding.address)
        assertTrue(bonding.message.contains("Confirm on screen"))

        val bonded = BondingStage.Bonded(
            address = "AA:BB:CC:11:22:33",
            name = "realme Buds T300",
            message = "Connecting Audio & Features...",
        )
        assertEquals("Connecting Audio & Features...", bonded.message)

        val audioConnecting = BondingStage.AudioConnecting(
            address = "AA:BB:CC:11:22:33",
            name = "realme Buds T300",
            message = "Connecting Audio & Features...",
        )
        assertEquals("AA:BB:CC:11:22:33", audioConnecting.address)

        val connected = BondingStage.Connected(
            address = "AA:BB:CC:11:22:33",
            name = "realme Buds T300",
        )
        assertEquals("realme Buds T300", connected.name)

        val failed = BondingStage.Failed(
            address = "AA:BB:CC:11:22:33",
            name = "realme Buds T300",
            error = "Pairing timed out (15s). Ensure earbuds are in pairing mode.",
            canRetry = true,
        )
        assertTrue(failed.canRetry)
        assertTrue(failed.error.contains("timed out"))
    }
}
