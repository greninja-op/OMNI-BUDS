package com.omnibuds.android.hardware.engine

import android.bluetooth.BluetoothDevice
import android.util.Log
import com.omnibuds.android.hardware.protocol.HexUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class NoiseControlDiagnosticState(
    val lastOutgoingHex: String = "--",
    val lastLatencyMs: Long = 0L,
    val writeCount: Int = 0,
    val activeOffByte: Byte = 0x00,
    val activeAncByte: Byte = 0x01,
    val activeTransByte: Byte = 0x02,
    val confirmedMode: String = "UNKNOWN",
)

/**
 * Non-Blocking "Latest-Wins" GATT Command Sequencer.
 *
 * Implements:
 * 1. User Commands Preempt Battery Polling (500ms cooldown after user tap, immediate poll abortion).
 * 2. Rapid-Tap Coalescing (zero backpressure): intermediate mode transitions are skipped;
 *    the earbuds jump directly to the latest tapped mode.
 * 3. Exact onCharacteristicWrite synchronization + 60ms clean BES firmware settle gap.
 * 4. Stale Notification Ignore Lock (250ms window): rejects out-of-order notifications
 *    from older taps.
 * 5. Authoritative 300ms Post-Tap Verification Query.
 * 6. Hardware Command Idempotency Guard (120ms debounce against duplicate intents).
 * 7. Live Telemetry Tracking (outgoing hex, write latency ms, write count).
 */
class BleCommandSequencer(
    private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "BleCommandSequencer"
        const val IDEMPOTENCY_WINDOW_MS = 120L
        const val BATTERY_POLL_COOLDOWN_MS = 500L
        const val NOTIFICATION_GUARD_WINDOW_MS = 250L
        const val VERIFICATION_DELAY_MS = 300L
        const val FIRMWARE_SETTLE_GAP_MS = 60L
        const val GATT_WRITE_TIMEOUT_MS = 250L
    }

    private val lastUserCommandTimestamps = ConcurrentHashMap<String, AtomicLong>()
    private val latestRequestedModes = ConcurrentHashMap<String, AtomicReference<String?>>()
    private val inFlightModes = ConcurrentHashMap<String, AtomicReference<String?>>()
    private val isWriteInFlight = ConcurrentHashMap<String, AtomicBoolean>()
    private val writeCompletions = ConcurrentHashMap<String, CompletableDeferred<Int>>()
    private val lastDispatchedModes = ConcurrentHashMap<String, AtomicReference<String?>>()
    private val lastWrittenBytes = ConcurrentHashMap<String, ByteArray>()
    private val lastWriteTimes = ConcurrentHashMap<String, Long>()
    private val writeCounts = ConcurrentHashMap<String, Int>()
    private val verificationJobs = ConcurrentHashMap<String, Job>()
    private val sequencerJobs = ConcurrentHashMap<String, Job>()
    private val writeMutexes = ConcurrentHashMap<String, Mutex>()

    var onPreemptBatteryPoll: (() -> Unit)? = null

    private val _diagnosticStates = MutableStateFlow<Map<String, NoiseControlDiagnosticState>>(emptyMap())
    val diagnosticStates: StateFlow<Map<String, NoiseControlDiagnosticState>> = _diagnosticStates.asStateFlow()

    private fun getMutex(address: String): Mutex =
        writeMutexes.computeIfAbsent(address) { Mutex() }

    /**
     * Rule 1: Checks whether background battery polling is permitted.
     * Suspended for 500ms following any user interaction.
     */
    fun canPollBattery(address: String? = null): Boolean {
        val now = System.currentTimeMillis()
        if (address != null) {
            val lastCmd = lastUserCommandTimestamps[address]?.get() ?: 0L
            return (now - lastCmd) >= BATTERY_POLL_COOLDOWN_MS
        }
        return lastUserCommandTimestamps.values.none {
            (now - it.get()) < BATTERY_POLL_COOLDOWN_MS
        }
    }

    fun recordUserInteraction(address: String) {
        lastUserCommandTimestamps.computeIfAbsent(address) { AtomicLong(0L) }
            .set(System.currentTimeMillis())
        onPreemptBatteryPoll?.invoke()
    }

    /**
     * Rule 1.3: Hardware Command Idempotency Guard (120ms debounce).
     * Prevents identical consecutive [opcode + payload] writes within 120ms.
     */
    fun isDuplicateWrite(address: String, packet: ByteArray): Boolean {
        val now = System.currentTimeMillis()
        val lastTime = lastWriteTimes[address] ?: 0L
        val lastBytes = lastWrittenBytes[address]
        if (now - lastTime < IDEMPOTENCY_WINDOW_MS && lastBytes != null && lastBytes.contentEquals(packet)) {
            Log.d(TAG, "[$address] Suppressed duplicate consecutive write within ${IDEMPOTENCY_WINDOW_MS}ms (${HexUtils.formatHex(packet)})")
            return true
        }
        return false
    }

    /**
     * Records outgoing frame telemetry and updates diagnostic state (+1 write count).
     */
    fun recordWriteAttempt(
        address: String,
        packet: ByteArray,
        latencyMs: Long,
        mode: String,
        activeOff: Byte,
        activeAnc: Byte,
        activeTrans: Byte,
    ) {
        lastWriteTimes[address] = System.currentTimeMillis()
        lastWrittenBytes[address] = packet
        val count = (writeCounts[address] ?: 0) + 1
        writeCounts[address] = count

        val hex = HexUtils.formatHex(packet)
        val current = _diagnosticStates.value[address] ?: NoiseControlDiagnosticState()
        _diagnosticStates.value = _diagnosticStates.value + (address to current.copy(
            lastOutgoingHex = hex,
            lastLatencyMs = latencyMs,
            writeCount = count,
            activeOffByte = activeOff,
            activeAncByte = activeAnc,
            activeTransByte = activeTrans,
            confirmedMode = mode,
        ))
    }

    fun updateConfirmedMode(address: String, mode: String) {
        val current = _diagnosticStates.value[address] ?: NoiseControlDiagnosticState()
        _diagnosticStates.value = _diagnosticStates.value + (address to current.copy(confirmedMode = mode))
    }

    fun getLatestRequestedNoiseMode(address: String): String? =
        latestRequestedModes[address]?.get()

    fun getInFlightMode(address: String): String? =
        inFlightModes[address]?.get()

    fun isWriteBusy(address: String): Boolean =
        isWriteInFlight[address]?.get() == true

    /**
     * Rule 4: Ignores stale out-of-order BLE notifications within 250ms of a user write
     * unless the notification matches latestRequestedNoiseMode.
     */
    fun shouldIgnoreNotification(address: String, incomingMode: String?): Boolean {
        val now = System.currentTimeMillis()
        val lastCmd = lastUserCommandTimestamps[address]?.get() ?: 0L
        if (now - lastCmd < NOTIFICATION_GUARD_WINDOW_MS) {
            val target = latestRequestedModes[address]?.get()
            if (target != null && incomingMode != null && !incomingMode.equals(target, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    fun prepareWriteCompletion(address: String): CompletableDeferred<Int> {
        val deferred = CompletableDeferred<Int>()
        writeCompletions[address] = deferred
        return deferred
    }

    fun onCharacteristicWriteCompleted(address: String, status: Int) {
        isWriteInFlight[address]?.set(false)
        writeCompletions.remove(address)?.complete(status)
    }

    fun notifyRfcommWriteCompleted(address: String) {
        isWriteInFlight[address]?.set(false)
        writeCompletions.remove(address)?.complete(0)
    }

    /**
     * Rule 3: Non-blocking latest-wins rapid-tap coalescing.
     * Intermediate mode taps are replaced by the latest mode; the earbuds jump straight
     * to the final tapped state once the active write and 60ms settle gap finish.
     */
    fun submitNoiseMode(
        device: BluetoothDevice,
        mode: String,
        dispatchAction: suspend (targetMode: String) -> Boolean,
        onAuthoritativeVerify: suspend () -> Unit,
    ) {
        val address = device.address
        recordUserInteraction(address)
        latestRequestedModes.computeIfAbsent(address) { AtomicReference(null) }.set(mode)

        // Rule 3.4: Re-arm 300ms verification timer
        verificationJobs[address]?.cancel()
        verificationJobs[address] = scope.launch {
            delay(VERIFICATION_DELAY_MS)
            onAuthoritativeVerify()
        }

        // If sequencer loop is already running for this device, it will drain the updated mode
        val existingJob = sequencerJobs[address]
        if (existingJob != null && existingJob.isActive) {
            Log.d(TAG, "[$address] Sequencer active: coalesced rapid tap to '$mode' (intermediate skipped)")
            return
        }

        sequencerJobs[address] = scope.launch {
            val mutex = getMutex(address)
            mutex.withLock {
                val flightFlag = isWriteInFlight.computeIfAbsent(address) { AtomicBoolean(false) }
                val inFlightRef = inFlightModes.computeIfAbsent(address) { AtomicReference(null) }
                val lastDispatched = lastDispatchedModes.computeIfAbsent(address) { AtomicReference(null) }

                while (true) {
                    val target = latestRequestedModes[address]?.get() ?: break
                    if (target == lastDispatched.get()) {
                        break
                    }

                    flightFlag.set(true)
                    inFlightRef.set(target)
                    lastDispatched.set(target)

                    val writeDeferred = prepareWriteCompletion(address)
                    val sent = dispatchAction(target)

                    if (sent) {
                        try {
                            withTimeout(GATT_WRITE_TIMEOUT_MS) {
                                writeDeferred.await()
                            }
                        } catch (_: TimeoutCancellationException) {
                            Log.d(TAG, "[$address] Write completion timed out or non-GATT used; proceeding with settle gap")
                        }
                    } else {
                        writeCompletions.remove(address)?.complete(-1)
                    }

                    // Rule 3: 60ms clean BES firmware settle gap
                    delay(FIRMWARE_SETTLE_GAP_MS)
                    flightFlag.set(false)
                    inFlightRef.set(null)

                    // Check if another tap arrived during write or settle gap
                    val latest = latestRequestedModes[address]?.get()
                    if (latest == target || latest == null) {
                        break
                    }
                }
            }
        }
    }

    fun clear(address: String) {
        sequencerJobs.remove(address)?.cancel()
        verificationJobs.remove(address)?.cancel()
        writeCompletions.remove(address)?.cancel()
        inFlightModes.remove(address)
        lastUserCommandTimestamps.remove(address)
        latestRequestedModes.remove(address)
        isWriteInFlight.remove(address)
        lastDispatchedModes.remove(address)
        lastWrittenBytes.remove(address)
        lastWriteTimes.remove(address)
        writeCounts.remove(address)
        _diagnosticStates.value = _diagnosticStates.value - address
    }
}
