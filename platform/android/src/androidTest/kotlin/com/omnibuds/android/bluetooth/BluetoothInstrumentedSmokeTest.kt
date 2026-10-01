package com.omnibuds.android.bluetooth

import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibuds.android.bluetooth.adapter.AndroidAdapterStateSource
import com.omnibuds.android.bluetooth.adapter.SystemBluetoothAdapterHandle
import com.omnibuds.android.bluetooth.capability.SystemApiLevelProvider
import com.omnibuds.android.bluetooth.permission.AndroidPermissionStateProvider
import com.omnibuds.android.bluetooth.permission.InMemoryPermissionRequestLedger
import com.omnibuds.android.bluetooth.permission.SystemPermissionStandingReader
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.platform.BluetoothAdapterState
import com.omnibuds.core.platform.BluetoothPermission
import com.omnibuds.core.platform.PermissionState
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The first code in this project that executes Android's Bluetooth API on a real handset.
 *
 * Everything in `src/test` proves decisions through scripted seams and proves nothing about the
 * platform — Phase 2 recorded that as known issue 2 rather than pretending otherwise. This suite is
 * Phase 3's answer, and it is deliberately narrow: verify the wiring, then ask the four questions
 * Phase 2 refused to claim anything about. Can a `BluetoothManager` be obtained here? Does an
 * adapter-state read answer without a permission? Does a standing read answer without prompting?
 * Does the composed source produce a coherent mapping on a real radio?
 *
 * **Two rules bind every test in this file.**
 *
 * Nothing here requests, grants or revokes a permission. There is no prompt call in this module:
 * Phase 3 prompt section 13 forbids a background component asking, and a test run nobody is watching
 * is a background component. A refusal is a result to record, not an obstacle to route around.
 *
 * Nothing here prints a device name, address or identifier. These tests read the phone's *own*
 * adapter, and assertions are on states, categories and shapes. A later test that enumerates
 * devices must assert the same way (`docs/security/device-access-policy.md`, SEC-LOG-002), and
 * [assertNoAddressLeak] is the form that rule takes when it has to be enforceable.
 *
 * A failure here is evidence, not a flake to retry. It means the platform answered differently from
 * the documentation the Phase 2 permission matrix was transcribed from, and the correct response is
 * to record which row of that matrix is wrong.
 */
@RunWith(AndroidJUnit4::class)
class BluetoothInstrumentedSmokeTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theInstrumentationRunsAgainstOurOwnPackage() {
        // The device-access policy allows this workstream to touch only OmniBuds' own code. That
        // boundary is a test here, not a promise: if the instrumentation target is not our package,
        // nothing else in this file may be trusted.
        assertEquals(TARGET_PACKAGE, context.packageName, "instrumentation must not address another app")
    }

    @Test
    fun theBluetoothManagerIsObtainableFromTheSystemService() {
        val manager = context.getSystemService(BluetoothManager::class.java)

        // The documented route to the adapter from API 18 up. A null here would be a finding about
        // this handset, not a crash to hide - which is why the assertion is about obtaining the
        // service, and the adapter's presence is reported by the next test.
        assertNotNull(manager, "BluetoothManager is the documented entry point (ADR-P2-011 research Q1)")
    }

    @Test
    fun anAdapterStateReadAnswersWithoutHoldingAPermission() {
        val handle = SystemBluetoothAdapterHandle(context, SystemApiLevelProvider)
        val source = AndroidAdapterStateSource(handle)

        when (val outcome = runBlocking { source.readState() }) {
            is OperationOutcome.Success -> {
                val state = outcome.value
                if (handle.adapterPresent) {
                    assertNotEquals(
                        BluetoothAdapterState.UNKNOWN,
                        state,
                        "an adapter that exists returned no readable state; that is a platform finding, not a flake",
                    )
                    assertNotEquals(
                        BluetoothAdapterState.UNAVAILABLE,
                        state,
                        "the handle reported an adapter, so the mapping may not then call it unavailable",
                    )
                } else {
                    assertEquals(
                        BluetoothAdapterState.UNAVAILABLE,
                        state,
                        "a phone with no adapter must read as UNAVAILABLE and never as DISABLED (ADR-P0-016)",
                    )
                }
                assertNoAddressLeak(state.toString())
            }

            is OperationOutcome.Failure -> {
                // Phase 2's central claim, tested rather than asserted: for an app built with this
                // targetSdk, reading adapter state needs no permission. A refusal here would be that
                // claim being wrong on real hardware, which is exactly what this suite exists to find.
                assertNoAddressLeak(outcome.error.detail)
                throw AssertionError("the adapter read failed on real hardware: ${outcome.error}")
            }

            OperationOutcome.Cancelled -> throw AssertionError("a one-shot read was cancelled unbidden")
        }
    }

    @Test
    fun permissionStandingIsReadWithoutAnyPrompt() {
        val provider = AndroidPermissionStateProvider(
            reader = SystemPermissionStandingReader(context),
            ledger = InMemoryPermissionRequestLedger(),
        )

        for (permission in BluetoothPermission.entries) {
            val standing = provider.standingOf(permission)

            assertNotEquals(
                PermissionState.DENIED_PERMANENTLY,
                standing,
                "ADR-P2-012: the app may never conclude permanent denial, and a real device is not the exception",
            )
            assertNotEquals(
                PermissionState.REQUIRES_USER_ACTION,
                standing,
                "nothing in Phase 3 can establish an action required outside the app",
            )
            // UNKNOWN and NOT_REQUESTED are both allowed answers, and which one this handset returns
            // is itself the datum the Phase 2 matrix was written to be checked against.
            assertTrue(
                standing == PermissionState.GRANTED ||
                    standing == PermissionState.DENIED ||
                    standing == PermissionState.NOT_REQUESTED ||
                    standing == PermissionState.UNKNOWN,
                "$permission produced an unreachable standing: $standing",
            )
        }
    }

    /**
     * Fails if any text carries something shaped like a Bluetooth device address.
     *
     * SEC-LOG-002 states the rule for logs; a pattern check is the form it can take inside a test, so
     * that a later test cannot start leaking by adding an innocent message.
     */
    private fun assertNoAddressLeak(text: String?) {
        val sample = text ?: return
        assertFalse(
            ADDRESS_PATTERN.containsMatchIn(sample),
            "a diagnostic string appears to carry a device identifier",
        )
    }

    private companion object {
        const val TARGET_PACKAGE = "com.omnibuds.android"

        val ADDRESS_PATTERN = Regex("""(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}""")
    }
}
