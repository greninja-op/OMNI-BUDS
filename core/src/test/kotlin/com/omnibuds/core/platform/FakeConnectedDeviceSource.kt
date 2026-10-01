package com.omnibuds.core.platform

import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.yield

/**
 * A scripted connected-device source for unit tests.
 *
 * It exists so the reconciliation engine can be tested without a radio, which is the whole argument
 * for putting the engine in `:core` (ADR-P3-003, Phase 2 prompt section 9). It is in test source and
 * can never reach production: `DependencyDirectionTest` fails the build if a `Fake*` type appears in
 * main sources.
 *
 * Deliberate properties, following [FakeAdapterStateSource] rather than inventing a second discipline:
 *  - snapshot rounds come from a fixed queue and report a structured failure if it runs dry, so a test
 *    cannot assert against an answer nobody scripted;
 *  - [ConnectedDeviceSource.snapshot] yields before answering, because a real enumeration takes time
 *    and the interesting ordering question is what the platform announces *during* it. Without the
 *    yield, the mid-snapshot cases would be tests of coroutine luck rather than of the engine's buffer;
 *  - every open, support question and dispose is counted, so a leaked registration or a
 *    double-registration is observable rather than merely plausible;
 *  - [failDisposal] models a platform teardown that throws, which is the case a `finally` block and a
 *    bound profile service are supposed to survive.
 */
class FakeConnectedDeviceSource(
    private vararg val rounds: ObservationRound<DeviceObservation>,
    private val eventScript: List<DeviceConnectionEvent> = emptyList(),
    private val support: OperationOutcome<ProfileSupportReport> = OperationOutcome.Success(allProfilesAnswerable()),
    private val keepEventsOpen: Boolean = false,
    private val openCancels: Boolean = false,
) {
    var snapshotCalls = 0
        private set

    var openCalls = 0
        private set

    var supportCalls = 0
        private set

    var disposeCalls = 0
        private set

    var activeRegistrations = 0
        private set

    var failDisposal = false
    var failOpen = false

    fun asSource(): ConnectedDeviceSource = Source()

    private inner class Source : ConnectedDeviceSource {
        override suspend fun profileSupport(): OperationOutcome<ProfileSupportReport> {
            supportCalls += 1
            return support
        }

        override suspend fun snapshot(): ObservationRound<DeviceObservation> {
            snapshotCalls += 1
            yield()
            return rounds.getOrElse(snapshotCalls - 1) {
                ObservationRound.Failure(
                    OmniBudsError(
                        category = OmniBudsErrorCategory.UNKNOWN_FAILURE,
                        operationId = "fake-device-source.snapshot",
                        detail = "the test queue of snapshot rounds ran dry at call $snapshotCalls",
                    ),
                )
            }
        }

        override suspend fun openConnectionEvents(): OperationOutcome<ConnectedDeviceEventChannel> {
            openCalls += 1
            if (failOpen) {
                return OperationOutcome.Failure(
                    OmniBudsError(
                        category = OmniBudsErrorCategory.RESOURCE_UNAVAILABLE,
                        operationId = "fake-device-source.open",
                        detail = "the platform refused to register a connection listener",
                    ),
                )
            }
            if (openCancels) return OperationOutcome.Cancelled

            activeRegistrations += 1
            var live = true
            val registration = object : PlatformRegistration {
                override val isActive: Boolean get() = live

                override suspend fun dispose() {
                    disposeCalls += 1
                    if (failDisposal) {
                        throw IllegalStateException("simulated platform teardown failure")
                    }
                    if (live) {
                        live = false
                        activeRegistrations -= 1
                    }
                }
            }

            // A scripted prefix makes dedupe, ordering and teardown deterministic; keepEventsOpen then
            // models the real receiver, whose announcements never stop arriving, so the single-slot and
            // cancellation cases can be tested without depending on emission timing between coroutines.
            val stream: Flow<DeviceConnectionEvent> = flow {
                eventScript.forEach { event -> emit(event) }
                if (keepEventsOpen) awaitCancellation()
            }

            return OperationOutcome.Success(
                object : ConnectedDeviceEventChannel {
                    override val registration: PlatformRegistration = registration
                    override val events: Flow<DeviceConnectionEvent> = stream
                },
            )
        }
    }
}

/** A support report in which every profile of the maintained union answered. */
fun allProfilesAnswerable(): ProfileSupportReport =
    profilesAnswerable(ObservedProfile.enumerationUnion.toSet())

/** A support report in which only [answerable] answered; the rest are declines, not silences. */
fun profilesAnswerable(answerable: Set<ObservedProfile>): ProfileSupportReport = ProfileSupportReport(
    ObservedProfile.enumerationUnion.associateWith {
        if (it in answerable) ProfileObservationSupport.ANSWERABLE else ProfileObservationSupport.NOT_ANSWERABLE
    },
)

/** A report in which nothing answered, which is the case an empty device list must never explain. */
fun noProfilesAnswerable(): ProfileSupportReport = ProfileSupportReport(
    ObservedProfile.enumerationUnion.associateWith { ProfileObservationSupport.NOT_ANSWERABLE },
)
