package com.omnibuds.android.bluetooth.mapping

import android.bluetooth.BluetoothAdapter
import com.omnibuds.core.platform.BluetoothAdapterState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The full translation table between Android's adapter integers and the domain's adapter state.
 *
 * Every row is pinned, including the rows a caller would rather not think about: no adapter, no
 * reading, an unrecognised number, a broadcast with no extra. Those are where a defaulted `DISABLED`
 * would turn "we did not look" into a claim about the user's phone (ADR-P0-016), so they are asserted
 * rather than left to the `else` branch's goodwill.
 */
class AdapterStateMappingTest {

    @Test
    fun enabledAdapterStateMapsToEnabled() {
        assertEquals(
            BluetoothAdapterState.ENABLED,
            bluetoothAdapterStateOf(adapterPresent = true, rawState = BluetoothAdapter.STATE_ON),
        )
    }

    @Test
    fun disabledAdapterStateMapsToDisabled() {
        assertEquals(
            BluetoothAdapterState.DISABLED,
            bluetoothAdapterStateOf(adapterPresent = true, rawState = BluetoothAdapter.STATE_OFF),
        )
    }

    @Test
    fun transitioningStatesMapToTheirOwnNames() {
        assertEquals(
            BluetoothAdapterState.ENABLING,
            bluetoothAdapterStateOf(adapterPresent = true, rawState = BluetoothAdapter.STATE_TURNING_ON),
        )
        assertEquals(
            BluetoothAdapterState.DISABLING,
            bluetoothAdapterStateOf(adapterPresent = true, rawState = BluetoothAdapter.STATE_TURNING_OFF),
        )
    }

    @Test
    fun anAbsentAdapterIsUnavailableWhateverItsLastReadingWas() {
        assertEquals(
            BluetoothAdapterState.UNAVAILABLE,
            bluetoothAdapterStateOf(adapterPresent = false, rawState = BluetoothAdapter.STATE_ON),
        )
        assertEquals(
            BluetoothAdapterState.UNAVAILABLE,
            bluetoothAdapterStateOf(adapterPresent = false, rawState = null),
        )
    }

    @Test
    fun aMissingReadingIsUnknownNotOff() {
        assertEquals(
            BluetoothAdapterState.UNKNOWN,
            bluetoothAdapterStateOf(adapterPresent = true, rawState = null),
        )
    }

    @Test
    fun anUnrecognisedNumberIsUnknownNotDisabled() {
        for (raw in listOf(RAW_STATE_UNREADABLE, BluetoothAdapter.ERROR, 0, 7, 99, Int.MIN_VALUE)) {
            assertEquals(
                BluetoothAdapterState.UNKNOWN,
                bluetoothAdapterStateOf(adapterPresent = true, rawState = raw),
                "raw state $raw is not one of the platform's four adapter states",
            )
        }
    }

    @Test
    fun theUnreadableMarkerIsOutsideThePlatformsStateRange() {
        // The marker only resolves to UNKNOWN because it collides with no real state, so the
        // collision-free property is the thing worth pinning.
        val realStates = listOf(
            BluetoothAdapter.STATE_OFF,
            BluetoothAdapter.STATE_TURNING_ON,
            BluetoothAdapter.STATE_ON,
            BluetoothAdapter.STATE_TURNING_OFF,
        )
        assertEquals(
            true,
            realStates.none { state -> state == RAW_STATE_UNREADABLE },
            "RAW_STATE_UNREADABLE must never equal a real adapter state",
        )
    }
}
