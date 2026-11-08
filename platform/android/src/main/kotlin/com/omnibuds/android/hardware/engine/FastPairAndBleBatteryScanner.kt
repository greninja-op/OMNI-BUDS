@file:Suppress("DEPRECATION")

package com.omnibuds.android.hardware.engine

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.omnibuds.android.hardware.protocol.KnownUuids
import com.omnibuds.android.hardware.protocol.TwsBatteryReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Universal Multi-Tier BLE Battery Scanner.
 *
 * Implements Layer 1:
 * Fallback chain:
 * 1. Google Fast Pair BLE Advertisement Parser (Service UUID 0xFE2C):
 *    Extracts Left, Right, Case battery levels from Fast Pair BLE advertisements (Account Key Data /
 *    Battery Notification bytes: Type 0b0011 or 0b0100 where subsequent 3 bytes represent Left, Right, Case).
 * 2. Apple Proximity Beacon Parser (Manufacturer ID 0x004C):
 *    Extracts Left, Right, Case battery levels from 27-byte Continuity/Proximity payloads (0x0719 nibbles).
 * 3. Dispatches verified telemetry to the shared flow, mapping RPA/LE addresses to bonded devices when correlated.
 */
class FastPairAndBleBatteryScanner(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    companion object {
        private const val TAG = "FastPairBleScanner"
    }

    private val _batteryReports = MutableSharedFlow<Pair<String, TwsBatteryReport>>(extraBufferCapacity = 64)
    val batteryReports: SharedFlow<Pair<String, TwsBatteryReport>> = _batteryReports.asSharedFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val lastReports = ConcurrentHashMap<String, TwsBatteryReport>()

    private var leScanner: BluetoothLeScanner? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            processScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { processScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "BLE scan failed with code: $errorCode")
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_isScanning.value) return

        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            Log.d(TAG, "Bluetooth not available/enabled for BLE scan")
            return
        }

        leScanner = adapter.bluetoothLeScanner
        if (leScanner == null) {
            Log.d(TAG, "BluetoothLeScanner unavailable")
            return
        }

        val filters = listOf(
            // 1. Google Fast Pair Service Data (0xFE2C)
            ScanFilter.Builder()
                .setServiceData(ParcelUuid(KnownUuids.FAST_PAIR_SERVICE), null)
                .build(),
            // 2. Google Fast Pair Service UUID (0xFE2C)
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(KnownUuids.FAST_PAIR_SERVICE))
                .build(),
            // 3. Apple Proximity Beacons (Manufacturer 0x004C) with Continuity 0x07 0x19 prefix
            ScanFilter.Builder()
                .setManufacturerData(AppleProximityParser.APPLE_MANUFACTURER_ID, byteArrayOf(0x07, 0x19), byteArrayOf(0xFF.toByte(), 0xFF.toByte()))
                .build(),
            // 4. Apple Manufacturer ID (0x004C)
            ScanFilter.Builder()
                .setManufacturerData(AppleProximityParser.APPLE_MANUFACTURER_ID, null)
                .build(),
            // 5. Realme / BBK / OnePlus Manufacturer ID (0x072F)
            ScanFilter.Builder()
                .setManufacturerData(0x072F, null)
                .build(),
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        try {
            leScanner?.startScan(filters, settings, scanCallback)
            _isScanning.value = true
            Log.i(TAG, "Universal Fast Pair & BLE Battery Scanner started")
        } catch (e: Exception) {
            Log.w(TAG, "startScan error: ${e.message}")
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return
        try {
            leScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.w(TAG, "stopScan error: ${e.message}")
        } finally {
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun processScanResult(result: ScanResult) {
        val device = result.device ?: return
        val address = device.address ?: return
        val record = result.scanRecord ?: return

        var parsedReport: TwsBatteryReport? = null
        var reportSource = ""

        // 1. Check Google Fast Pair BLE Advertisement (0xFE2C)
        val fpData = record.getServiceData(ParcelUuid(KnownUuids.FAST_PAIR_SERVICE))
        if (fpData != null && fpData.isNotEmpty()) {
            FastPairAdvParser.parse(fpData)?.let {
                parsedReport = it
                reportSource = "Google Fast Pair BLE Adv"
            }
        }

        // 2. Check Apple Proximity Beacon (0x004C)
        if (parsedReport == null) {
            val appleData = record.getManufacturerSpecificData(AppleProximityParser.APPLE_MANUFACTURER_ID)
            if (appleData != null && appleData.isNotEmpty()) {
                AppleProximityParser.parse(appleData)?.let {
                    parsedReport = it
                    reportSource = "Apple Proximity Continuity"
                }
            }
        }

        // 3. Fallback to other service data
        if (parsedReport == null) {
            record.serviceData?.values?.forEach { sData ->
                FastPairAdvParser.parse(sData)?.let {
                    parsedReport = it
                    reportSource = "Service Data Fallback"
                    return@forEach
                }
            }
        }

        val report = parsedReport ?: return

        // Dispatch for scanned address
        dispatchReportIfChanged(address, report, reportSource)

        // If the scanned BLE device address is an RPA, correlate with bonded devices by device name
        val devName = try { device.name } catch (_: Exception) { null }
        if (!devName.isNullOrBlank()) {
            try {
                val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                adapter?.bondedDevices?.forEach { bonded ->
                    val bName = bonded.name
                    if (bName != null && bName.equals(devName, ignoreCase = true) && bonded.address != address) {
                        dispatchReportIfChanged(bonded.address, report, "$reportSource (Correlated: $bName)")
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun dispatchReportIfChanged(address: String, report: TwsBatteryReport, source: String) {
        val last = lastReports[address]
        if (last == null || last.left != report.left || last.right != report.right ||
            last.case != report.case || last.leftCharging != report.leftCharging ||
            last.rightCharging != report.rightCharging || last.caseCharging != report.caseCharging
        ) {
            lastReports[address] = report
            Log.i(TAG, "[$source] Battery updated for $address -> L:${report.left}% R:${report.right}% Case:${report.case}%")
            scope.launch {
                _batteryReports.emit(address to report)
            }
        }
    }
}
