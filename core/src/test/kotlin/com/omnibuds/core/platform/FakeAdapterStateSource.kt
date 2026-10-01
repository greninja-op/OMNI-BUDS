package com.omnibuds.core.platform

import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A scripted adapter-state source for unit tests.
 *
 * It exists so the observer machine can be tested without a radio (Phase 2 prompt section 9). It is
 * in test source and can never reach production: `DependencyDirectionTest` fails the build if a
 * `Fake*` type appears in main sources.
 *
 * Deliberate properties, because a fake that quietly succeeds hides the bugs we care about:
 *  - reads come from a fixed queue and throw if it runs dry, so a test cannot accidentally assert
 *    against an invented answer;
 *  - every open and dispose is counted, so registration leaks are observable;
 *  - [failDisposal] models a platform teardown that throws, which is the case a `finally` block is
 *    supposed to survive.
 */
class FakeAdapterStateSource(
    private vararg val reads: OperationOutcome<BluetoothAdapterState>,
    private val eventScript: List<BluetoothAdapterState> = emptyList(),
    private val keepOpen: Boolean = false,
) {
    var readCalls = 0
        private set

    var openCalls = 0
        private set

    var disposeCalls = 0
        private set

    var activeRegistrations = 0
        private set

    var failDisposal = false
    var failOpen = false

    private val source = object : AdapterStateSource {
        override suspend fun readState(): OperationOutcome<BluetoothAdapterState> {
            readCalls += 1
            return reads.getOrElse(readCalls - 1) {
                OperationOutcome.Failure(
                    OmniBudsError(
                        category = OmniBudsErrorCategory.UNKNOWN_FAILURE,
                        operationId = "fake-adapter-source.read-state",
                        detail = "the test queue of adapter reads ran dry at call $readCalls",
                    ),
                )
            }
        }

        override suspend fun openStateChanges(): OperationOutcome<AdapterStateChangeChannel> {
            openCalls += 1
            if (failOpen) {
                return OperationOutcome.Failure(
                    OmniBudsError(
                        category = OmniBudsErrorCategory.RESOURCE_UNAVAILABLE,
                        operationId = "fake-adapter-source.open",
                        detail = "the platform refused to register a state listener",
                    ),
                )
            }

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

            // A scripted prefix makes dedupe and teardown deterministic; keepOpen then models the real
            // platform, whose state stream never ends, so single-slot and cancellation behaviour can be
            // tested without depending on emission timing between coroutines.
            val stream: Flow<BluetoothAdapterState> = flow {
                eventScript.forEach { state -> emit(state) }
                if (keepOpen) awaitCancellation()
            }

            return OperationOutcome.Success(
                object : AdapterStateChangeChannel {
                    override val registration: PlatformRegistration = registration
                    override val states: Flow<BluetoothAdapterState> = stream
                },
            )
        }
    }

    fun asSource(): AdapterStateSource = source
}
