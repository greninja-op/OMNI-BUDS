package com.omnibuds.core.globalstate

/**
 * Stable device identifier for the global state engine.
 *
 * Phase 24 (OB-P24-REQ-001): address-free stable identity. The engine
 * never uses Bluetooth addresses as identifiers.
 */
@JvmInline
value class GlobalDeviceId(val value: String) {
    init {
        require(value.isNotBlank()) { "device id must not be blank" }
    }
}
