package com.omnibuds.android.hardware.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BluetoothAdapterStatus {
    ENABLED,
    DISABLED,
    TURNING_ON,
    TURNING_OFF,
}

object BluetoothAdapterStateStore {
    private val _status = MutableStateFlow(BluetoothAdapterStatus.ENABLED)
    val status: StateFlow<BluetoothAdapterStatus> = _status.asStateFlow()

    fun updateStatus(newStatus: BluetoothAdapterStatus) {
        _status.value = newStatus
    }
}

