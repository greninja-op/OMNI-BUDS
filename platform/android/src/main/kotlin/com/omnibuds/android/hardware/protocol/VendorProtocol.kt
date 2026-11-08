package com.omnibuds.android.hardware.protocol

import java.util.UUID

/**
 * Diagnostic and vendor protocol domain models for multi-brand TWS communication.
 */
data class TwsBatteryReport(
    val left: Int? = null,
    val right: Int? = null,
    val case: Int? = null,
    val leftCharging: Boolean = false,
    val rightCharging: Boolean = false,
    val caseCharging: Boolean = false,
    val caseIsLive: Boolean = true,
    val rawHex: String? = null,
    val sourceProtocol: String = "RFCOMM SPP",
    val timestamp: Long = System.currentTimeMillis(),
) {
    val hasComponentData: Boolean
        get() = left != null || right != null || case != null
}

data class GattServiceDiagnostic(
    val uuid: String,
    val name: String,
    val characteristics: List<GattCharDiagnostic>,
)

data class GattCharDiagnostic(
    val uuid: String,
    val properties: List<String>,
    val canRead: Boolean,
    val canWrite: Boolean,
    val canNotify: Boolean,
    var lastValueHex: String? = null,
)

data class DiagnosticLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,
    val message: String,
    val rawHex: String? = null,
)

object KnownUuids {
    // Client Characteristic Configuration Descriptor (CCCD) for enabling notifications
    val CCCD_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    // Google Fast Pair Service (widely used across Realme, Nothing, Xiaomi, Pixel, Sony)
    val FAST_PAIR_SERVICE: UUID = UUID.fromString("0000FE2C-0000-1000-8000-00805F9B34FB")

    // Standard BLE Battery Service
    val BATTERY_SERVICE: UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
    val BATTERY_LEVEL_CHAR: UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")

    // HeyMelody / Realme / OPPO / OnePlus BBK proprietary vendor BLE services
    val REALME_BLE_SERVICE_1: UUID = UUID.fromString("00000100-3C17-D293-8E48-14FE2E4DA212")
    val REALME_BLE_CHAR_TX_1: UUID = UUID.fromString("00000101-3C17-D293-8E48-14FE2E4DA212")
    val REALME_BLE_CHAR_RX_1: UUID = UUID.fromString("00000102-3C17-D293-8E48-14FE2E4DA212")

    val REALME_BLE_SERVICE_2: UUID = UUID.fromString("00000200-3C17-D293-8E48-14FE2E4DA212")
    val REALME_BLE_CHAR_TX_2: UUID = UUID.fromString("00000201-3C17-D293-8E48-14FE2E4DA212")
    val REALME_BLE_CHAR_RX_2: UUID = UUID.fromString("00000202-3C17-D293-8E48-14FE2E4DA212")

    // BES (Bestechnic) & Airoha proprietary BLE services (commonly used on Realme, Boat, CMF)
    val BES_OTA_SERVICE: UUID = UUID.fromString("00000001-0000-1000-8000-00805F9B34FB")
    val BES_CHAR_TX: UUID = UUID.fromString("00000002-0000-1000-8000-00805F9B34FB")
    val BES_CHAR_RX: UUID = UUID.fromString("00000003-0000-1000-8000-00805F9B34FB")

    // Nordic UART service (general fallback on custom BLE stacks)
    val NORDIC_UART_SERVICE: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    val NORDIC_UART_TX: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
    val NORDIC_UART_RX: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")

    // Classic Bluetooth RFCOMM / SPP UUIDs
    val REALME_RFCOMM_UUID: UUID = UUID.fromString("0000079A-D102-11E1-9B23-00025B00A5A5")
    val STANDARD_SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
}

