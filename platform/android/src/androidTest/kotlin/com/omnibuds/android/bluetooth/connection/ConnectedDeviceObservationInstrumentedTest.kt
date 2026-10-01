package com.omnibuds.android.bluetooth.connection

import android.content.Context
import com.omnibuds.android.bluetooth.SystemTimeProvider
import com.omnibuds.android.bluetooth.adapter.AndroidAdapterStateSource
import com.omnibuds.android.bluetooth.adapter.SystemBluetoothAdapterHandle
import com.omnibuds.android.bluetooth.capability.SystemApiLevelProvider
import com.omnibuds.android.bluetooth.capability.SystemTargetSdkProvider
import com.omnibuds.android.bluetooth.permission.AndroidPermissionStateProvider
import com.omnibuds.android.bluetooth.permission.InMemoryPermissionRequestLedger
import com.omnibuds.android.bluetooth.permission.PermissionStandingReader
import com.omnibuds.android.bluetooth.permission.SystemPermissionStandingReader
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.platform.ConnectedDeviceEventChannel
import com.omnibuds.core.platform.ConnectedDeviceObserver
import com.omnibuds.core.platform.DeviceObservation
import com.omnibuds.core.platform.ObservationRound
import com.omnibuds.core.platform.ObservedProfile
import com.omnibuds.core.platform.ProfileSupportReport
import com.omnibuds.core.platform.isAnswerable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Phase 3's instrumented suite for connected-device observation: **authored and compiled, never run.**
 *
 * ADR-P3-014 is the standing instruction, and it is the reason this file says so in its own header. No
 * handset was attached to this workstream, no `connected*` Gradle task was executed, and nothing in here
 * is evidence about what a phone does. Every claim it would settle stays capped at `IMPLEMENTED` and is
 * collected in the deferred device session. Compiling it is not nothing - it is the difference between a
 * plan and a paragraph, and it proves the calls exist in the compile SDK - but a green
 * `compileDebugAndroidTestKotlin` says precisely nothing about behaviour on hardware.
 *
 * Three rules bind every test in this file, and they are the rules the device-access policy imposes
 * (`docs/security/device-access-policy.md`, Phase 3 section):
 *
 * 1. **Nothing here prints a device name, address or identifier.** Instrumented output leaves the phone
 *    through logcat and through the generated XML results, which are files in this repository's build
 *    directories. Assertions are on counts, categories, states and shapes, and [assertNoAddressLeak] is
 *    applied to every string this suite produces, so the rule is a failure rather than an intention.
 * 2. **Nothing here requests, grants or revokes a permission.** There is no prompt call anywhere in this
 *    module, and a test run nobody is watching is not allowed to become a background component that asks.
 *    Where standing is refused, the suite scripts a reader that says so rather than changing the device:
 *    a refusal is a result to record, not an obstacle to route around.
 * 3. **Nothing here touches a device.** No pairing, no connect, no discovery, no scan, no socket, no audio
 *    path, no write of any kind - and `DependencyDirectionTest` rule 7 now refuses those by name in this
 *    source set too, which is the guard gap ADR-P3-007 named and this phase closes.
 *
 * A failure when these are eventually run is evidence, not a flake to retry: it would mean the platform
 * answered differently from the API-35 stubs and the AOSP reference source this phase was written against,
 * and the correct response is to record which row of `connection-observation-research.md` is wrong.
 */
@RunWith(AndroidJUnit4::class)
class ConnectedDeviceObservationInstrumentedTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * 1. A refused standing produces a typed refusal and no device list, on the real object graph.
     *
     * ADR-P3-009's ordering is the design's central claim, and this is the form of it that a handset can
     * settle without any device being connected: the composition is real - framework handle, resolver,
     * provider - while the standing is scripted to a refusal. If the platform's empty collection could
     * still reach the projection here, the ordering would be a comment rather than a mechanism. The
     * assertion is about the *absence* of a list, which is why the round's type is what is inspected.
     *
     * This says nothing about whether the phone would have granted anything, because nothing is requested.
     */
    @Test
    fun aRefusedStandingProducesATypedRefusalAndNoDeviceList() = observe {
        val source = composedSource(reader = PermissionStandingReader { false })

        val round = source.snapshot()

        assertIs<ObservationRound.Failure<DeviceObservation>>(round)
        assertEquals(OmniBudsErrorCategory.PERMISSION_DENIED, round.error.category)
        assertNoAddressLeak(round.error.detail)
    }

    /**
     * 2. The same refusal holds for the profile-capability question, before any service is bound.
     *
     * A refusal that still enumerated would bind profile services it has no business binding, which is the
     * resource half of ADR-P3-008's cleanup obligation. Checked as a refusal of the outcome type, because
     * [ProfileSupportReport] has no representation for "a list from a round that was not allowed to produce
     * one" either.
     */
    @Test
    fun aRefusedStandingNeverAsksWhichProfilesCouldAnswer() = observe {
        val source = composedSource(reader = PermissionStandingReader { false })

        val support = source.profileSupport()

        assertIs<OperationOutcome.Failure>(support)
        assertEquals(OmniBudsErrorCategory.PERMISSION_DENIED, support.error.category)
    }

    /**
     * 3. The maintained union is the list this handset is actually asked about, all seven entries.
     *
     * The enumeration list is a maintained decision (ADR-P3-008), so the first thing a handset can settle
     * is whether the union this project carries is a union the platform can be asked about at all. Each
     * entry may answer `ANSWERABLE`, `NOT_ANSWERABLE` or `UNKNOWN` and all three are legitimate on a real
     * build; what would be a finding is an entry *missing* from the report, because a profile nobody asked
     * about silently shrinks what an empty device list can mean.
     */
    @Test
    fun theProfileReportCoversEveryEntryInTheMaintainedUnion() = observe {
        val source = composedSource(reader = SystemPermissionStandingReader(context))

        val support = source.profileSupport()

        if (support is OperationOutcome.Success) {
            val missing = ObservedProfile.enumerationUnion.filter { profile -> profile !in support.value.entries }
            assertEquals(
                emptyList(),
                missing.map { profile -> profile.technicalName },
                "a profile absent from the report is a profile nobody asked about",
            )
        } else {
            // A refusal is the expected answer wherever BLUETOOTH_CONNECT is not held, and recording it is
            // this suite's job rather than a denial to route around. The run notes which standing the
            // handset was in; the report is not rewritten to look like a census.
            assertIs<OperationOutcome.Failure>(support)
            assertEquals(OmniBudsErrorCategory.PERMISSION_DENIED, support.error.category)
        }
    }

    /**
     * 4. Through the engine, a platform that declines never reads as an empty room.
     *
     * The invariant ADR-P3-015 rules 4 and 7 exist to protect, and this is the only tier that can falsify
     * it end to end: if the first round reports no device at all, the projection must be able to say that
     * every profile in the union answered. The tempting alternative - a handset where LE Audio, CSIP and
     * the hearing-aid profile all decline, and a UI that says "no earbuds are connected" - is exactly the
     * failure this phase was designed around, and it is a platform question (U-7) rather than a code one.
     *
     * [ConnectedDeviceObserver.observe] is collected with a one-round take, so the run registers, reads,
     * publishes and then cancels and disposes through the observer's own `finally`. No timer and no
     * waiting: the first round is emitted by the snapshot the observer takes itself.
     */
    @Test
    fun aDecliningPlatformNeverLooksLikeAnEmptyRoom() = observe {
        val observer = ConnectedDeviceObserver(
            source = composedSource(reader = SystemPermissionStandingReader(context)),
            adapterStates = instrumentedAdapterSource(context),
            time = SystemTimeProvider,
        )

        val rounds = observer.observe().take(1).toList()
        val projection = observer.snapshot.value

        assertEquals(1, rounds.size, "the observer owes exactly one answer for the round it took")
        val first = rounds.first()
        if (first is ObservationRound.Success && first.devices.isEmpty()) {
            assertTrue(
                projection.isUnionComplete,
                "an empty projection may only be published when every profile answered; this one did not",
            )
        }
    }

    /**
     * 5. Opening a channel registers and unregisters cleanly under the exported flag.
     *
     * ADR-P3-013 changed this module's receivers to `RECEIVER_EXPORTED` on the platform's own written
     * guidance, and the honest summary of the evidence so far is that no handset has confirmed it. What a
     * run can settle with no user action is the weaker half: that a registration carrying these Bluetooth
     * actions on this build neither throws nor leaks, and that disposing once is enough. Whether the
     * announcements actually *arrive* is U-4, needs a headset to connect, and is deferred manual step 8.
     */
    @Test
    fun theConnectionChannelRegistersAndReleasesCleanly() = observe {
        val source = composedSource(reader = SystemPermissionStandingReader(context))

        val opened = source.openConnectionEvents()

        if (opened is OperationOutcome.Failure) {
            // A refusal is a result. It is recorded with its category and its redacted detail, nothing more.
            assertEquals(OmniBudsErrorCategory.PERMISSION_DENIED, opened.error.category)
            assertNoAddressLeak(opened.error.detail)
            return@observe
        }
        assertIs<OperationOutcome.Success<ConnectedDeviceEventChannel>>(opened)
        val channel = opened.value
        assertTrue(channel.registration.isActive, "the open should have registered with the platform")

        channel.registration.dispose()

        assertFalse(channel.registration.isActive, "one dispose must release the channel")
    }

    /**
     * 6. A profile beyond this handset's API level never reports itself answerable.
     *
     * The API-level gate is the part of the union this project controls, and it is checkable on any device
     * with no user action. A pass here is not evidence that the in-range profiles work - U-3 and U-7 are
     * about those - it is evidence that this project is not claiming an enumeration it cannot have asked
     * for.
     */
    @Test
    fun noProfileBeyondTheHandsetsApiLevelIsReportedAnswerable() = observe {
        val source = composedSource(reader = SystemPermissionStandingReader(context))
        val support = source.profileSupport()

        if (support !is OperationOutcome.Success) return@observe
        val deviceLevel = SystemApiLevelProvider.apiLevel()
        val beyond = ObservedProfile.enumerationUnion.filter { profile -> profile.introducedApiLevel > deviceLevel }
        val wronglyAnswerable = beyond.filter { profile -> support.value.supportFor(profile).isAnswerable() }

        assertEquals(
            emptyList(),
            wronglyAnswerable.map { profile -> profile.technicalName },
            "a profile whose constant does not exist at this API level cannot be answered for",
        )
    }

    /**
     * 7. No text this observation produces carries something shaped like a device address.
     *
     * SEC-LOG-002 and ADR-P3-010: the key type redacts itself, and that property is worth testing against
     * real platform data rather than against a fixture written by the same head that wrote the type. The
     * whole-record check is deliberately conservative - a user's own device label that happens to look like
     * an address would fail here, and failing is the correct response for a suite whose output leaves the
     * phone. Only counts and shapes are allowed to leave.
     */
    @Test
    fun noReportedObservationCarriesADeviceIdentifier() = observe {
        val source = composedSource(reader = SystemPermissionStandingReader(context))

        val round = source.snapshot()

        if (round is ObservationRound.Success) {
            for (record in round.devices) {
                assertNoAddressLeak(record.toString())
                assertNoAddressLeak(record.key.toString())
            }
        }
        assertNoAddressLeak(describeRound(round))
    }

    // ---- Helpers ---------------------------------------------------------------------------------------

    /**
     * Runs one observation body.
     *
     * `runBlocking` rather than a coroutine test rule: JUnit 4 is what Android's instrumentation runner is
     * built on (ADR-P3-007), and the bodies await only single buffered platform reads, so no virtual time
     * is needed and none is invented. There is no sleep and no poll in this file.
     */
    private fun observe(body: suspend () -> Unit) = runBlocking { body() }

    private fun composedSource(reader: PermissionStandingReader): AndroidConnectedDeviceSource =
        AndroidConnectedDeviceSource(
            handle = SystemConnectedDeviceHandle(context, SystemApiLevelProvider),
            permissionProvider = permissionProvider(reader),
            apiLevel = SystemApiLevelProvider,
            targetSdk = SystemTargetSdkProvider(context),
            time = SystemTimeProvider,
        )

    /**
     * A provider with an empty request ledger.
     *
     * The ledger is process-local and nothing in this suite asks the user for anything, so it starts and
     * stays empty. The standings that come out of it are the platform's answer, not one this run produced.
     */
    private fun permissionProvider(reader: PermissionStandingReader): AndroidPermissionStateProvider =
        AndroidPermissionStateProvider(reader = reader, ledger = InMemoryPermissionRequestLedger())

    /**
     * A round described without any device data: a case name, a count and a stage.
     *
     * This is the only form in which a round may leave the phone, and it exists as a function rather than
     * an inline string so that a later test cannot improvise a message that quotes an observation.
     */
    private fun describeRound(round: ObservationRound<DeviceObservation>): String = when (round) {
        is ObservationRound.Success -> "success count=${round.devices.size} stage=${round.stage}"
        is ObservationRound.Failure -> "failure category=${round.error.category}"
        ObservationRound.Cancelled -> "cancelled"
    }

    private fun assertNoAddressLeak(text: String?) {
        val sample = text ?: return
        assertFalse(
            ADDRESS_PATTERN.containsMatchIn(sample),
            "a diagnostic string appears to carry a device identifier",
        )
    }

    private companion object {
        val ADDRESS_PATTERN = Regex("""(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}""")
    }
}

/**
 * The adapter source the engine needs, built the way the production composition builds it.
 *
 * Declared here rather than inlined so that a run of this suite exercises the same pairing the DI factory
 * promises: one adapter handle, one receiver for it, one channel for the device announcements
 * (`omniBudsConnectedDeviceObserver` in `di/OmniBudsBluetooth.kt` takes this as a parameter for exactly
 * that reason).
 */
internal fun instrumentedAdapterSource(context: Context): AndroidAdapterStateSource = AndroidAdapterStateSource(
    SystemBluetoothAdapterHandle(context, SystemApiLevelProvider),
)
