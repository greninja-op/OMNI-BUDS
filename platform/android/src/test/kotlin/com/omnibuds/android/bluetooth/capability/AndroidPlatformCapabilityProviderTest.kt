package com.omnibuds.android.bluetooth.capability

import com.omnibuds.android.bluetooth.permission.AndroidPermissionStateProvider
import com.omnibuds.android.bluetooth.permission.PermissionRequestLedger
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.platform.ApiAvailability
import com.omnibuds.core.platform.BluetoothPermission
import com.omnibuds.core.platform.BluetoothPlatformCapabilities
import com.omnibuds.core.platform.PermissionState
import com.omnibuds.core.platform.PlatformFeature
import com.omnibuds.core.state.VerificationLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The platform capability report, tested against scripted phone answers.
 *
 * The claims worth pinning here are the negative ones. Phase 2 prompt section 5.5 forbids reporting a
 * capability because an API class happens to exist, and the four kinds of fact - OS availability,
 * phone hardware, permission standing, connected-device support - must not be collapsed into one
 * confident boolean. Each test below is a way that a careless implementation could have said more than
 * the phone actually said.
 */
class AndroidPlatformCapabilityProviderTest {

    @Test
    fun theRunningApiLevelIsCarriedIntoTheReport() {
        val report = report(apiLevel = 34)

        assertEquals(34, report.apiLevel)
        assertTrue(report.isObserved, "a report built from real answers is not an empty report")
    }

    @Test
    fun adapterPresenceIsAnsweredFromTheHandle() {
        assertEquals(
            ApiAvailability.AVAILABLE,
            report(apiLevel = 34, adapterPresent = true).adapterPresent,
        )
        assertEquals(
            ApiAvailability.UNAVAILABLE,
            report(apiLevel = 34, adapterPresent = false).adapterPresent,
        )
    }

    @Test
    fun aPhoneThatWouldNotAnswerThePresenceQuestionFailsTheReport() {
        val outcome = provider(apiLevel = 34, adapterPresentFailure = IllegalStateException("no answer")).capabilities()

        assertIs<OperationOutcome.Failure>(outcome)
        assertEquals(OmniBudsErrorCategory.PLATFORM_EXCEPTION, outcome.error.category)
    }

    @Test
    fun advertisedFeaturesBecomeAvailableAndUnadvertisedOnesUnavailable() {
        val report = report(
            apiLevel = 34,
            features = mapOf(CLASSIC_FEATURE to true, BLE_FEATURE to false),
        )

        assertEquals(ApiAvailability.AVAILABLE, report.supportFor(PlatformFeature.CLASSIC_BLUETOOTH).apiAvailability)
        assertEquals(ApiAvailability.AVAILABLE, report.supportFor(PlatformFeature.RFCOMM_CLIENT).apiAvailability)
        assertEquals(ApiAvailability.UNAVAILABLE, report.supportFor(PlatformFeature.BLE_CENTRAL).apiAvailability)
        assertEquals(ApiAvailability.UNAVAILABLE, report.supportFor(PlatformFeature.GATT_CLIENT).apiAvailability)
    }

    @Test
    fun aProbeThatThrowsIsUnknownRatherThanAbsent() {
        val outcome = provider(
            apiLevel = 34,
            featureFailure = RuntimeException("the package manager would not say"),
        ).capabilities()

        assertIs<OperationOutcome.Success<BluetoothPlatformCapabilities>>(outcome)
        val report = outcome.value
        assertEquals(ApiAvailability.UNKNOWN, report.supportFor(PlatformFeature.CLASSIC_BLUETOOTH).apiAvailability)
        assertEquals(ApiAvailability.UNKNOWN, report.supportFor(PlatformFeature.BLE_CENTRAL).apiAvailability)
        // An unanswerable feature must not take the facts the phone did give down with it.
        assertEquals(34, report.apiLevel)
    }

    @Test
    fun leAudioReportsOnlyTheApiLevelItWasGiven() {
        val modern = report(apiLevel = 34)
        val legacy = report(apiLevel = 30)

        assertEquals(ApiAvailability.AVAILABLE, modern.supportFor(PlatformFeature.LE_AUDIO).apiAvailability)
        assertEquals(ApiAvailability.UNAVAILABLE, legacy.supportFor(PlatformFeature.LE_AUDIO).apiAvailability)
        assertFalse(
            TransportKind.LE_AUDIO in modern.candidateTransports,
            "a class existing in the OS is not a phone supporting LE Audio",
        )
    }

    @Test
    fun profileConnectionStateIsNeverClaimed() {
        val report = report(apiLevel = 34, features = mapOf(CLASSIC_FEATURE to true, BLE_FEATURE to true))

        assertEquals(
            ApiAvailability.UNKNOWN,
            report.supportFor(PlatformFeature.A2DP_CONNECTION_STATE).apiAvailability,
            "the profile question is Phase 3 and asking it would need a permission Phase 2 does not hold",
        )
        assertEquals(
            ApiAvailability.UNKNOWN,
            report.supportFor(PlatformFeature.HEADSET_CONNECTION_STATE).apiAvailability,
        )
    }

    @Test
    fun nothingIsClaimedUsableBecauseNoRadioWasOpened() {
        val report = report(apiLevel = 34, features = mapOf(CLASSIC_FEATURE to true, BLE_FEATURE to true))

        for (feature in PlatformFeature.entries) {
            val support = report.supportFor(feature)
            assertEquals(
                VerificationLevel.INFERRED,
                support.hardwareEvidence,
                "$feature hardware was not tested in Phase 2",
            )
            assertFalse(support.isUsable, "$feature must not be reported usable without hardware evidence")
            assertNotNull(support.blockingReason, "$feature should state which fact is missing")
        }
    }

    @Test
    fun thePermissionSectionReportsEveryPermissionInTheModel() {
        val report = report(apiLevel = 35, granted = emptySet())

        assertEquals(BluetoothPermission.entries.toSet(), report.permissionStatus.keys)
        for (permission in BluetoothPermission.entries) {
            assertNotEquals(
                PermissionState.DENIED_PERMANENTLY,
                report.permissionStatus.getValue(permission),
                "the platform layer may never infer permanent denial (ADR-P2-012)",
            )
        }
    }

    @Test
    fun anAuthorisedInspectionReportsNoPermissionInItsWay() {
        val report = report(apiLevel = 35, granted = emptySet())

        assertEquals(
            PermissionState.NOT_REQUIRED,
            report.supportFor(PlatformFeature.CLASSIC_BLUETOOTH).permissionState,
            "capability inspection is the operation this report belongs to, and it asks for nothing",
        )
    }

    @Test
    fun aTransportRowReportsTheStandingOfThePermissionItWouldNeed() {
        val report = report(apiLevel = 35, granted = setOf(BluetoothPermission.BLUETOOTH_CONNECT.manifestName))

        assertEquals(
            PermissionState.GRANTED,
            report.supportFor(PlatformFeature.GATT_CLIENT).permissionState,
            "the row names what would stand in the way when a later phase opens the transport",
        )
    }

    @Test
    fun anUnknownTargetSdkIsNotSilentlyReadAsNoRequirement() {
        val outcome = provider(apiLevel = 34, targetSdk = null).capabilities()

        assertIs<OperationOutcome.Success<BluetoothPlatformCapabilities>>(outcome)
        assertEquals(
            PermissionState.UNKNOWN,
            outcome.value.supportFor(PlatformFeature.RFCOMM_CLIENT).permissionState,
        )
    }

    @Test
    fun candidateTransportsComeFromThePhonesOwnFlags() {
        val both = report(apiLevel = 34, features = mapOf(CLASSIC_FEATURE to true, BLE_FEATURE to true))
        val classicOnly = report(apiLevel = 34, features = mapOf(CLASSIC_FEATURE to true, BLE_FEATURE to false))
        val neither = report(apiLevel = 34, features = mapOf(CLASSIC_FEATURE to false, BLE_FEATURE to false))

        assertEquals(
            setOf(TransportKind.CLASSIC_BLUETOOTH, TransportKind.RFCOMM, TransportKind.BLE, TransportKind.GATT),
            both.candidateTransports,
        )
        assertEquals(setOf(TransportKind.CLASSIC_BLUETOOTH, TransportKind.RFCOMM), classicOnly.candidateTransports)
        assertTrue(neither.candidateTransports.isEmpty())
    }

    private fun report(
        apiLevel: Int,
        features: Map<String, Boolean> = mapOf(CLASSIC_FEATURE to true, BLE_FEATURE to true),
        adapterPresent: Boolean = true,
        granted: Set<String> = emptySet(),
        targetSdk: Int? = apiLevel,
    ): BluetoothPlatformCapabilities =
        assertOnSuccess(
            provider(
                apiLevel = apiLevel,
                features = features,
                adapterPresent = { adapterPresent },
                granted = granted,
                targetSdk = targetSdk,
            ).capabilities(),
        )

    private fun provider(
        apiLevel: Int,
        features: Map<String, Boolean> = mapOf(CLASSIC_FEATURE to true, BLE_FEATURE to true),
        featureFailure: Throwable? = null,
        adapterPresent: () -> Boolean = { true },
        adapterPresentFailure: Throwable? = null,
        granted: Set<String> = emptySet(),
        targetSdk: Int? = apiLevel,
    ): AndroidPlatformCapabilityProvider {
        val probe = PlatformFeatureProbe { name ->
            featureFailure?.let { problem -> throw problem }
            features.getValue(name)
        }
        val presence = {
            adapterPresentFailure?.let { problem -> throw problem }
            adapterPresent()
        }
        return AndroidPlatformCapabilityProvider(
            apiLevel = ApiLevelProvider { apiLevel },
            featureProbe = probe,
            permissionProvider = AndroidPermissionStateProvider(
                reader = { manifestName -> manifestName in granted },
                ledger = PermissionRequestLedger { false },
            ),
            targetSdk = TargetSdkProvider { targetSdk },
            adapterPresent = presence,
        )
    }

    private fun <T> assertOnSuccess(outcome: OperationOutcome<T>): T {
        assertIs<OperationOutcome.Success<T>>(outcome)
        return outcome.value
    }

    private companion object {
        const val CLASSIC_FEATURE = "android.hardware.bluetooth"
        const val BLE_FEATURE = "android.hardware.bluetooth_le"
    }
}
