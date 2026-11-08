package com.omnibuds.android.ui.compose.screens

import com.omnibuds.android.presentation.devices.AndroidDiscoveredDevice
import com.omnibuds.android.ui.compose.model.AudioFormFactor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class OrbitalScannerPlacementTest {

    private val maxRadiusPx = 300f

    @Test
    fun `empty list returns empty placements`() {
        val placements = calculateCollisionFreePlacements(
            devices = emptyList(),
            maxRadiusPx = maxRadiusPx,
            context = null,
        )
        assertTrue(placements.isEmpty())
    }

    @Test
    fun `assigns devices to correct orbits based on RSSI ranges`() {
        val strong = AndroidDiscoveredDevice(
            id = "strong-dev",
            name = "realme Buds T300",
            address = "AA:BB:CC:11:22:33",
            rssi = -42,
        )
        val mid = AndroidDiscoveredDevice(
            id = "mid-dev",
            name = "OnePlus Buds Pro",
            address = "AA:BB:CC:44:55:66",
            rssi = -65,
        )
        val weak = AndroidDiscoveredDevice(
            id = "weak-dev",
            name = "Sony WH-1000XM5",
            address = "AA:BB:CC:77:88:99",
            rssi = -82,
        )
        val nullRssi = AndroidDiscoveredDevice(
            id = "null-dev",
            name = "Unknown Bud",
            address = "AA:BB:CC:00:11:22",
            rssi = null,
        )

        val placements = calculateCollisionFreePlacements(
            devices = listOf(strong, mid, weak, nullRssi),
            maxRadiusPx = maxRadiusPx,
            context = null,
        )

        assertEquals(4, placements.size)

        val strongPlacement = placements.first { it.device.id == "strong-dev" }
        assertEquals(0, strongPlacement.orbitIndex, "Strong RSSI (-42 dBm) should be on Inner Orbit (0)")
        assertEquals(maxRadiusPx * 0.40f, strongPlacement.radiusPx)

        val midPlacement = placements.first { it.device.id == "mid-dev" }
        assertEquals(1, midPlacement.orbitIndex, "Mid RSSI (-65 dBm) should be on Middle Orbit (1)")
        assertEquals(maxRadiusPx * 0.66f, midPlacement.radiusPx)

        val weakPlacement = placements.first { it.device.id == "weak-dev" }
        assertEquals(2, weakPlacement.orbitIndex, "Weak RSSI (-82 dBm) should be on Outer Orbit (2)")
        assertEquals(maxRadiusPx * 0.88f, weakPlacement.radiusPx)

        val nullPlacement = placements.first { it.device.id == "null-dev" }
        assertEquals(2, nullPlacement.orbitIndex, "Null RSSI should default to Outer Orbit (2)")
        assertEquals(maxRadiusPx * 0.88f, nullPlacement.radiusPx)
    }

    @Test
    fun `collision-free angle separation for multiple devices on the same orbit`() {
        val dev1 = AndroidDiscoveredDevice(id = "dev1", name = "Bud 1", address = "00:00:00:00:00:01", rssi = -50)
        val dev2 = AndroidDiscoveredDevice(id = "dev2", name = "Bud 2", address = "00:00:00:00:00:02", rssi = -48)
        val dev3 = AndroidDiscoveredDevice(id = "dev3", name = "Bud 3", address = "00:00:00:00:00:03", rssi = -52)

        val placements = calculateCollisionFreePlacements(
            devices = listOf(dev1, dev2, dev3),
            maxRadiusPx = maxRadiusPx,
            context = null,
        )

        assertEquals(3, placements.size)
        // All 3 on orbit 0
        assertTrue(placements.all { it.orbitIndex == 0 })

        // Check angular distances between all pairs
        val angles = placements.map { it.angleDeg }
        for (i in angles.indices) {
            for (j in i + 1 until angles.size) {
                val diff = abs(angles[i] - angles[j])
                val circularDiff = minOf(diff, 360f - diff)
                assertTrue(
                    circularDiff >= 119f,
                    "Angular distance between items on 3-item orbit should be 120° (got $circularDiff)",
                )
            }
        }
    }

    @Test
    fun `resolves category badges and form factors accurately`() {
        val twsStem = AndroidDiscoveredDevice(id = "1", name = "realme Buds T300", address = "01:00:00:00:00:01")
        val twsBean = AndroidDiscoveredDevice(id = "2", name = "Galaxy Buds2 Pro", address = "02:00:00:00:00:01")
        val neckband = AndroidDiscoveredDevice(id = "3", name = "OnePlus Bullets Wireless Z2", address = "03:00:00:00:00:01")
        val headset = AndroidDiscoveredDevice(id = "4", name = "Sony WH-1000XM5", address = "04:00:00:00:00:01")
        val bone = AndroidDiscoveredDevice(id = "5", name = "Shokz OpenRun Pro", address = "05:00:00:00:00:01")
        val openClip = AndroidDiscoveredDevice(id = "6", name = "Bose Ultra Open Earbuds", address = "06:00:00:00:00:01")
        val openRing = AndroidDiscoveredDevice(id = "7", name = "Sony LinkBuds WF-L900", address = "07:00:00:00:00:01")
        val stemHalf = AndroidDiscoveredDevice(id = "8", name = "AirPods 3", address = "08:00:00:00:00:01")
        val openHook = AndroidDiscoveredDevice(id = "9", name = "Powerbeats Pro", address = "09:00:00:00:00:01")

        val placements = calculateCollisionFreePlacements(
            devices = listOf(twsStem, twsBean, neckband, headset, bone, openClip, openRing, stemHalf, openHook),
            maxRadiusPx = maxRadiusPx,
            context = null,
        )

        val map = placements.associateBy { it.device.id }

        assertEquals(AudioFormFactor.STEM_IN_EAR, map["1"]?.formFactor)
        assertEquals("TWS", map["1"]?.categoryTag)

        assertEquals(AudioFormFactor.BUD_BEAN_IN_EAR, map["2"]?.formFactor)
        assertEquals("TWS", map["2"]?.categoryTag)

        assertEquals(AudioFormFactor.NECKBAND, map["3"]?.formFactor)
        assertEquals("Neckband", map["3"]?.categoryTag)

        assertEquals(AudioFormFactor.HEADSET_OVER_ON_EAR, map["4"]?.formFactor)
        assertEquals("Headset", map["4"]?.categoryTag)

        assertEquals(AudioFormFactor.BONE_CONDUCTION, map["5"]?.formFactor)
        assertEquals("Bone", map["5"]?.categoryTag)

        assertEquals(AudioFormFactor.OPEN_EAR_CLIP, map["6"]?.formFactor)
        assertEquals("Open-Ear", map["6"]?.categoryTag)

        assertEquals(AudioFormFactor.BUD_OPEN_RING, map["7"]?.formFactor)
        assertEquals("TWS", map["7"]?.categoryTag)

        assertEquals(AudioFormFactor.STEM_HALF_IN_EAR, map["8"]?.formFactor)
        assertEquals("TWS", map["8"]?.categoryTag)

        assertEquals(AudioFormFactor.OPEN_EAR_HOOK, map["9"]?.formFactor)
        assertEquals("Open-Ear", map["9"]?.categoryTag)
    }
}
