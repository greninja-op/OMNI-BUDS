package com.omnibuds.app

import com.omnibuds.android.presentation.devices.AndroidDiscoveredDevice
import com.omnibuds.android.presentation.workspace.ProductCatalog
import com.omnibuds.core.globalstate.BatteryState
import com.omnibuds.core.globalstate.CapabilityState
import com.omnibuds.core.globalstate.ConnectionState
import com.omnibuds.core.globalstate.DeviceStateEvent
import com.omnibuds.core.globalstate.GlobalDeviceId
import com.omnibuds.core.globalstate.GlobalDeviceStateRepository
import com.omnibuds.core.globalstate.IdentityState
import com.omnibuds.core.globalstate.ObservationProvenance
import com.omnibuds.core.globalstate.ObservedValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Feeds real system observations into the OmniBuds global state engine.
 *
 * The presentation layer reads everything from [GlobalDeviceStateRepository];
 * until now nothing ever wrote to it, so every device screen was empty. This
 * bridge registers each discovered device with:
 *  - identity: the name/alias the device itself reports, refreshed
 *    whenever better evidence arrives (Iteration 3 — identity used to be
 *    frozen at first registration, so a device first seen without a name
 *    stayed nameless forever). The manufacturer is attached only when
 *    the product catalog verifies the match; otherwise the placeholder
 *    is kept internally and filtered out by the presentation resolver,
 *    never displayed,
 *  - connection: the live profile connection state from Android,
 *  - capabilities: Discovering — standard observation is running, vendor
 *    feature protocols are not verified, so controls render as UNKNOWN
 *    instead of fake-working,
 *  - battery: only when Android broadcasts a real level for the device.
 *
 * Sequences are strictly increasing per device, as the aggregator requires.
 */
class DeviceStateBridge(
    private val repository: GlobalDeviceStateRepository,
    private val scope: CoroutineScope,
) {
    private val registered = ConcurrentHashMap.newKeySet<String>()
    private val lastIdentityEvidence = ConcurrentHashMap<String, String>()
    private val lastConnection = ConcurrentHashMap<String, Boolean>()
    private val lastBattery = ConcurrentHashMap<String, Int>()
    private val lastComponents = ConcurrentHashMap<String, List<BatteryState.ComponentLevel>>()
    private val generations = ConcurrentHashMap<String, AtomicLong>()

    /**
     * One ordered ingest pipeline. Events are allocated a sequence in call
     * order and delivered through this channel, so asynchronous ingestion
     * cannot reorder them and make the aggregator reject a newer event as
     * stale.
     */
    private val ingestQueue = Channel<DeviceStateEvent>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (event in ingestQueue) {
                repository.ingest(event)
            }
        }
    }

    private fun nextGeneration(deviceId: String): Long =
        generations.computeIfAbsent(deviceId) { AtomicLong(0L) }.incrementAndGet()

    private fun generationFor(deviceId: String): Long =
        generations.computeIfAbsent(deviceId) { AtomicLong(0L) }.get()

    private fun provenance(sessionId: String?, generation: Long): ObservationProvenance {
        val now = System.currentTimeMillis()
        return ObservationProvenance(
            sourceId = "android-system",
            observedAtMillis = now,
            receivedAtMillis = now,
            sessionId = sessionId,
            connectionGeneration = generation,
            protocolVersion = null,
        )
    }

    /**
     * Register (once) and refresh (on change) one discovered device.
     * Safe to call repeatedly with the same data — unchanged facts are not
     * re-ingested.
     */
    fun syncDevice(device: AndroidDiscoveredDevice, connection: ConnectionState) {
        val id = device.id
        val gid = GlobalDeviceId(id)
        val connected = connection is ConnectionState.Connected
        // Build events synchronously so per-device sequences are allocated in
        // call order; ingestion itself is async.
        val events = mutableListOf<DeviceStateEvent>()
        // Identity evidence: the best name the system currently reports.
        // Re-ingested whenever it improves or changes — a device first
        // seen mid-scan without a name must not stay nameless.
        val evidenceName = device.alias ?: device.name
        val evidenceKey = evidenceName ?: ""
        val firstRegistration = registered.add(id)
        val identityChanged = lastIdentityEvidence.put(id, evidenceKey) != evidenceKey
        if (firstRegistration || identityChanged) {
            // Verified identification: try each reported string (a user
            // may have renamed the device in system settings, so the
            // alias can fail to match while the hardware name verifies,
            // or vice versa). The display evidence stays alias-first.
            val verifiedInfo = listOfNotNull(device.name, device.alias)
                .map { ProductCatalog.resolve(it) }
                .firstOrNull {
                    it.confidence == ProductCatalog.Confidence.EXACT ||
                        it.confidence == ProductCatalog.Confidence.FAMILY
                }
            events += DeviceStateEvent.IdentityUpdated(
                deviceId = gid,
                sessionId = null,
                sequence = repository.nextSequence(id),
                sourceId = "android-system",
                identity = IdentityState.Identified(
                    manufacturerId = verifiedInfo?.manufacturer ?: "Unknown",
                    modelId = evidenceName,
                    confidence = when (verifiedInfo?.confidence) {
                        ProductCatalog.Confidence.EXACT -> "High"
                        ProductCatalog.Confidence.FAMILY -> "Medium"
                        else -> "Low"
                    },
                    firmwareVersion = null,
                ),
            )
        }
        val profile = com.omnibuds.android.hardware.capability.StrictBrandProfileResolver.resolve(
            device = null,
            nameOverride = evidenceName,
            addressOverride = id,
        )
        val verified = com.omnibuds.android.hardware.capability.VerifiedDeviceCatalog.resolve(
            evidenceName,
            id,
        )
        val readyCaps = verified?.writableCapabilityIds
            ?.let(com.omnibuds.android.hardware.capability.VerifiedControlCatalog::actionable)
            ?.takeIf { it.isNotEmpty() }
            ?: buildSet {
            if (profile.hasAnc) {
                add("anc_mode")
                if (profile.ancModes.any { it.contains("transparen", ignoreCase = true) }) {
                    add("transparency_mode")
                }
            }
            if (profile.eqPresets.isNotEmpty() ||
                profile.hasCustom3BandEq ||
                profile.hasParametric8BandEq ||
                profile.hasClearBass ||
                profile.hasBassWave ||
                profile.hasUltraBass ||
                profile.hasDynamicBass
            ) {
                add("eq_preset")
            }
            if (profile.gestures.isNotEmpty()) add("touch_controls")
            if (profile.hasSpatialAudio) add("spatial_audio")
            if (profile.hasVolumeEnhancer) add("volume_enhancer")
            if (profile.hasLowLagMode) add("game_mode")
            if (profile.hasInEarDetection) add("wear_detection")
            if (profile.hasDualConnection) add("multipoint")
            if (profile.hasUltraBass) add("ultra_bass")
        }
        val connectionChanged = firstRegistration || lastConnection[id] != connected
        val generation = if (connectionChanged) nextGeneration(id) else generationFor(id)
        if (firstRegistration || identityChanged) {
            events += DeviceStateEvent.CapabilitiesUpdated(
                deviceId = gid,
                sessionId = null,
                sequence = repository.nextSequence(id),
                sourceId = "android-system",
                capabilities = if (readyCaps.isEmpty()) {
                    CapabilityState.Discovering("no verified vendor schema for this device")
                } else {
                    CapabilityState.Ready(readyCaps)
                },
            )
        }
        if (firstRegistration) {
            lastConnection[id] = connected
            events += DeviceStateEvent.ConnectionChanged(
                deviceId = gid,
                sessionId = null,
                sequence = repository.nextSequence(id),
                sourceId = "android-system",
                connection = connection,
                generation = generation,
            )
        } else if (lastConnection[id] != connected) {
            lastConnection[id] = connected
            if (!connected) {
                // Forget the dedupe cache: after a reconnect, the same
                // level must be re-ingested so its provenance timestamp
                // refreshes instead of the pre-disconnect reading
                // silently ageing in place. The published battery is
                // deliberately retained: the presentation layer marks it
                // last-known/stale while disconnected, and core rules say
                // battery is never zeroed on disconnect.
                lastBattery.remove(id)
                lastComponents.remove(id)
            } else {
                events += DeviceStateEvent.CapabilitiesUpdated(
                    deviceId = gid,
                    sessionId = null,
                    sequence = repository.nextSequence(id),
                    sourceId = "android-system",
                    capabilities = if (readyCaps.isEmpty()) {
                    CapabilityState.Discovering("no verified vendor schema for this device")
                } else {
                    CapabilityState.Ready(readyCaps)
                },
                )
            }
            events += DeviceStateEvent.ConnectionChanged(
                deviceId = gid,
                sessionId = null,
                sequence = repository.nextSequence(id),
                sourceId = "android-system",
                connection = connection,
                generation = generation,
            )
        }
        if (events.isNotEmpty()) {
            events.forEach { ingestQueue.trySend(it) }
        }
    }

    /**
     * Per-component battery reported by a source that observes each
     * component separately (a verified vendor protocol, when one is
     * integrated). No production source calls this yet — standard
     * Android exposes only the aggregate in [onBatteryLevel]. Values
     * are validated per component; invalid components are dropped,
     * never coerced. The aggregate slot keeps the last standard
     * reading unless [aggregateLevel] supplies a fresher one.
     */
    fun onBatteryComponents(
        address: String,
        components: List<BatteryState.ComponentLevel>,
        aggregateLevel: Int? = null,
    ) {
        val valid = components.filter { it.levelPercent == null || it.levelPercent in 0..100 }
        if (valid.isEmpty() && aggregateLevel == null) return
        if (lastComponents[address] == valid && aggregateLevel == null) return
        lastComponents[address] = valid
        if (aggregateLevel != null && aggregateLevel in 0..100) {
            lastBattery[address] = aggregateLevel
        }
        val event = DeviceStateEvent.BatteryUpdated(
            deviceId = GlobalDeviceId(address),
            sessionId = null,
            sequence = repository.nextSequence(address),
            sourceId = "android-system",
            battery = BatteryState.Known(
                levelPercent = aggregateLevel ?: lastBattery[address],
                charging = null,
                observation = ObservedValue(Unit, provenance(null, generationFor(address))),
                components = valid,
            ),
        )
        ingestQueue.trySend(event)
    }

    /** A real battery level broadcast by Android for a device (0–100). */
    fun onBatteryLevel(address: String, level: Int) {
        if (level !in 0..100) return
        if (lastBattery[address] == level) return
        lastBattery[address] = level
        // An aggregate reading must never erase per-component telemetry that
        // arrived from a vendor protocol. Preserve the last known components
        // and only replace the aggregate slot.
        val components = lastComponents[address].orEmpty()
        val event = DeviceStateEvent.BatteryUpdated(
            deviceId = GlobalDeviceId(address),
            sessionId = null,
            sequence = repository.nextSequence(address),
            sourceId = "android-system",
            battery = BatteryState.Known(
                levelPercent = level,
                charging = null,
                observation = ObservedValue(Unit, provenance(null, generationFor(address))),
                components = components,
            ),
        )
        ingestQueue.trySend(event)
    }

    /** Clears all battery telemetry when a device disconnects or Bluetooth is disabled. */
    fun clearBattery(address: String) {
        lastBattery.remove(address)
        lastComponents.remove(address)
        val event = DeviceStateEvent.BatteryUpdated(
            deviceId = GlobalDeviceId(address),
            sessionId = null,
            sequence = repository.nextSequence(address),
            sourceId = "android-system",
            battery = BatteryState.Unknown,
        )
        ingestQueue.trySend(event)
    }
}
