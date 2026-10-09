package com.omnibuds.core.sdk.testing

import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.transport.TransportContract
import com.omnibuds.core.transport.TransportRequest
import com.omnibuds.core.transport.TransportResponse

/**
 * Scripted fake transport exchange outcome for offline SDK testing.
 */
sealed interface SdkFakeStep {
    /** Respond with given response payload and acknowledged flag. */
    data class Respond(val payload: ByteArray?, val acknowledged: Boolean = true) : SdkFakeStep

    /** Fail with given OmniBuds error category and optional detail. */
    data class Fail(val category: OmniBudsErrorCategory, val detail: String? = null) : SdkFakeStep

    /** Simulate link disconnect. */
    data object Disconnect : SdkFakeStep
}

/**
 * Deterministic offline fake transport implementation for community adapter tests.
 *
 * Implements [TransportContract] for offline test validation without touching physical Bluetooth.
 */
class ScriptedFakeTransport(
    override val kind: TransportKind = TransportKind.GATT,
    private val script: MutableList<SdkFakeStep> = mutableListOf(),
    private val openOutcome: OperationOutcome<Unit> = OperationOutcome.Success(Unit),
) : TransportContract {

    private var open = false
    private val _recordedRequests = mutableListOf<TransportRequest>()
    val recordedRequests: List<TransportRequest> get() = _recordedRequests.toList()

    override val isOpen: Boolean get() = open

    fun script(vararg steps: SdkFakeStep) {
        script.addAll(steps)
    }

    fun remainingSteps(): Int = script.size

    override suspend fun open(): OperationOutcome<Unit> {
        return when (openOutcome) {
            is OperationOutcome.Success -> {
                open = true
                openOutcome
            }
            is OperationOutcome.Failure -> {
                open = false
                openOutcome
            }
            is OperationOutcome.Cancelled -> {
                open = false
                openOutcome
            }
        }
    }

    override suspend fun close(): OperationOutcome<Unit> {
        open = false
        return OperationOutcome.Success(Unit)
    }

    override suspend fun exchange(
        request: TransportRequest,
        timeoutMillis: Long,
    ): OperationOutcome<TransportResponse> {
        if (!open) {
            return OperationOutcome.Failure(
                OmniBudsError(
                    category = OmniBudsErrorCategory.DEVICE_DISCONNECTED,
                    operationId = request.commandId,
                    detail = "Channel is closed",
                ),
            )
        }

        _recordedRequests.add(request)

        if (script.isEmpty()) {
            return OperationOutcome.Failure(
                OmniBudsError(
                    category = OmniBudsErrorCategory.TIMEOUT,
                    operationId = request.commandId,
                    detail = "Script exhausted waiting for command: ${request.commandId}",
                ),
            )
        }

        return when (val step = script.removeAt(0)) {
            is SdkFakeStep.Respond -> {
                OperationOutcome.Success(
                    TransportResponse(
                        commandId = request.commandId,
                        payload = step.payload,
                        acknowledged = step.acknowledged,
                    ),
                )
            }
            is SdkFakeStep.Fail -> {
                OperationOutcome.Failure(
                    OmniBudsError(
                        category = step.category,
                        operationId = request.commandId,
                        detail = step.detail ?: "Scripted failure",
                    ),
                )
            }
            is SdkFakeStep.Disconnect -> {
                open = false
                OperationOutcome.Failure(
                    OmniBudsError(
                        category = OmniBudsErrorCategory.DEVICE_DISCONNECTED,
                        operationId = request.commandId,
                        detail = "Device disconnected during exchange",
                    ),
                )
            }
        }
    }
}
