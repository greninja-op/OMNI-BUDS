package com.omnibuds.core.testkit

import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.transport.TransportContract
import com.omnibuds.core.transport.TransportRequest
import com.omnibuds.core.transport.TransportResponse

/**
 * One scripted exchange outcome.
 */
sealed interface ScriptedOutcome {
    /** Return a successful response. */
    data class Respond(val response: TransportResponse) : ScriptedOutcome

    /** Fail with the given error category. */
    data class Fail(val category: OmniBudsErrorCategory, val detail: String? = null) : ScriptedOutcome

    /** Simulate a closed channel: DEVICE_DISCONNECTED. */
    data object Disconnect : ScriptedOutcome
}

/**
 * A deterministic scripted transport for tests.
 *
 * Phase 30 (OB-P30-REQ-003): implements the real [TransportContract].
 * Outcomes are consumed in order; when the script is exhausted, the
 * transport fails closed with TIMEOUT rather than inventing a response.
 * Test-only: lives in the testkit package, never in production paths.
 */
class ScriptedTransport(
    override val kind: TransportKind = TransportKind.GATT,
    private val script: MutableList<ScriptedOutcome> = mutableListOf(),
    private val openOutcome: OperationOutcome<Unit> =
        OperationOutcome.Success(Unit),
) : TransportContract {

    private var open = false
    private val _exchanges = mutableListOf<TransportRequest>()
    /** Requests received, in order (for assertions). */
    val exchanges: List<TransportRequest> get() = _exchanges.toList()

    override val isOpen: Boolean get() = open

    /** Append scripted outcomes. */
    fun script(vararg outcomes: ScriptedOutcome) {
        script.addAll(outcomes)
    }

    /** Outcomes remaining in the script. */
    fun remaining(): Int = script.size

    override suspend fun open(): OperationOutcome<Unit> {
        return when (val outcome = openOutcome) {
            is OperationOutcome.Success -> {
                open = true
                outcome
            }
            else -> outcome
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
                OmniBudsError.of(
                    OmniBudsErrorCategory.DEVICE_DISCONNECTED,
                    request.commandId,
                    "exchange on closed scripted transport",
                    kind,
                ),
            )
        }
        _exchanges.add(request)
        val outcome = script.removeFirstOrNull()
            ?: return OperationOutcome.Failure(
                OmniBudsError.of(
                    OmniBudsErrorCategory.TIMEOUT,
                    request.commandId,
                    "script exhausted; failing closed",
                    kind,
                ),
            )
        return when (outcome) {
            is ScriptedOutcome.Respond ->
                OperationOutcome.Success(outcome.response)
            is ScriptedOutcome.Fail ->
                OperationOutcome.Failure(
                    OmniBudsError.of(outcome.category, request.commandId, outcome.detail, kind),
                )
            is ScriptedOutcome.Disconnect -> {
                open = false
                OperationOutcome.Failure(
                    OmniBudsError.of(
                        OmniBudsErrorCategory.DEVICE_DISCONNECTED,
                        request.commandId,
                        "scripted disconnect",
                        kind,
                    ),
                )
            }
        }
    }
}
