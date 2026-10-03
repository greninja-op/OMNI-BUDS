package com.omnibuds.core.protocol

import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.state.VerificationLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The protocol lifecycle contract driven by a **test-only scripted session** (prompt §16: scripted protocol
 * implementations are permitted but confined to test source and never presented as real support —
 * ADR-P7-010). It proves the state machine is implementable, that `execute` is refused before `READY`, that a
 * command is sent at most once, and that close is idempotent — the behaviours prompt §12 and §11 require.
 *
 * Tier T1: no radio, no vendor protocol, scripted transport.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProtocolSessionTest {

    /** A scripted transport that records how many times each command was sent. */
    private class ScriptedAdapter(
        var openSucceeds: Boolean = true,
        var exchangeResult: OperationOutcome<ByteArray?> = OperationOutcome.Success(byteArrayOf(7)),
    ) : ProtocolTransportAdapter {
        var exchangeCalls = 0
        override val requiredTransport = TransportKind.GATT
        override var isConnected = false
        override suspend fun open(): OperationOutcome<Unit> {
            isConnected = openSucceeds
            return if (openSucceeds) OperationOutcome.Success(Unit) else
                OperationOutcome.Failure(OmniBudsError(OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE, "a", "d"))
        }
        override suspend fun exchange(commandId: String, payload: ByteArray, timeoutMillis: Long): OperationOutcome<ByteArray?> {
            exchangeCalls += 1
            return exchangeResult
        }
        override suspend fun close(): OperationOutcome<Unit> {
            isConnected = false
            return OperationOutcome.Success(Unit)
        }
    }

    /** A minimal session that honours [ProtocolStateTransitions]; the reference for the guards below. */
    private class ScriptedSession(
        private val adapter: ProtocolTransportAdapter,
        private val initSucceeds: Boolean = true,
    ) : ProtocolSession {
        private val _state = MutableStateFlow(ProtocolState.RESOLVED)
        override val descriptor: ProtocolDefinition = ProtocolDefinition(
            protocolId = "fixture.scripted",
            displayName = "Scripted Fixture",
            vendor = null,
            transport = TransportKind.GATT,
            version = "1",
            commands = emptyMap(),
            responses = emptyMap(),
            capabilityMappings = emptyMap(),
            confidence = VerificationLevel.IMPLEMENTED,
        )
        override val state: StateFlow<ProtocolState> = _state.asStateFlow()
        override val events = kotlinx.coroutines.flow.MutableSharedFlow<ProtocolEvent>(
            replay = 0, extraBufferCapacity = 32,
        )

        override suspend fun initialize(): OperationOutcome<Unit> {
            move(ProtocolState.CREATED)
            move(ProtocolState.INITIALIZING)
            val opened = adapter.open()
            return if (opened is OperationOutcome.Success && initSucceeds) {
                move(ProtocolState.READY)
                OperationOutcome.Success(Unit)
            } else {
                move(ProtocolState.FAILED)
                OperationOutcome.Failure(OmniBudsError(OmniBudsErrorCategory.PROTOCOL_MISMATCH, "init", "init failed"))
            }
        }

        override suspend fun execute(command: ProtocolCommand): OperationOutcome<ProtocolResponse> {
            if (!ProtocolStateTransitions.canExecute(_state.value)) {
                return OperationOutcome.Failure(
                    OmniBudsError(OmniBudsErrorCategory.INVALID_STATE, "execute", "session not ready"),
                )
            }
            // Sent exactly once; a timeout is returned, never re-sent (prompt §11, ADR-P7-004).
            val reply = adapter.exchange(command.commandId, command.payload, command.timeoutMillis ?: 1_000L)
            return when (reply) {
                is OperationOutcome.Success -> ProtocolResponse(
                    commandId = command.commandId,
                    correlationId = command.correlationId,
                    payload = reply.value,
                    acknowledged = reply.value != null,
                ).let { OperationOutcome.Success(it) }
                is OperationOutcome.Failure -> OperationOutcome.Failure(reply.error)
                OperationOutcome.Cancelled -> OperationOutcome.Cancelled
            }
        }

        override suspend fun close(): OperationOutcome<Unit> {
            if (ProtocolStateTransitions.isTerminal(_state.value)) return OperationOutcome.Success(Unit)
            move(ProtocolState.CLOSING)
            adapter.close()
            _state.value = ProtocolState.CLOSED
            return OperationOutcome.Success(Unit)
        }

        private fun move(next: ProtocolState) {
            check(ProtocolStateTransitions.canTransition(_state.value, next)) { "illegal ${_state.value} -> $next" }
            _state.value = next
        }
    }

    @Test
    fun aSessionIsReadyOnlyAfterInitializeSucceeds() = runTest {
        val session = ScriptedSession(ScriptedAdapter())
        assertEquals(ProtocolState.RESOLVED, session.state.value)

        assertIs<OperationOutcome.Success<Unit>>(session.initialize())
        assertEquals(ProtocolState.READY, session.state.value)
    }

    @Test
    fun aFailedInitializeLeavesTheSessionNotReady() = runTest {
        val session = ScriptedSession(ScriptedAdapter(), initSucceeds = false)

        assertIs<OperationOutcome.Failure>(session.initialize())
        assertEquals(ProtocolState.FAILED, session.state.value)
    }

    @Test
    fun executeBeforeReadyIsRefusedAndSendsNothing() = runTest {
        val adapter = ScriptedAdapter()
        val session = ScriptedSession(adapter)

        val outcome = assertIs<OperationOutcome.Failure>(
            session.execute(ProtocolCommand("c", ByteArray(0), "k", null)),
        )
        assertEquals(OmniBudsErrorCategory.INVALID_STATE, outcome.error.category)
        assertEquals(0, adapter.exchangeCalls, "a not-ready session must not touch the transport")
    }

    @Test
    fun aCommandIsSentExactlyOnceEvenWhenTheReplyFails() = runTest {
        val adapter = ScriptedAdapter(
            exchangeResult = OperationOutcome.Failure(
                OmniBudsError(OmniBudsErrorCategory.TIMEOUT, "x", "timed out"),
            ),
        )
        val session = ScriptedSession(adapter)
        session.initialize()

        assertIs<OperationOutcome.Failure>(session.execute(ProtocolCommand("c", ByteArray(0), "k", null)))
        assertEquals(1, adapter.exchangeCalls, "a timed-out command is never auto-retried (ADR-P7-004)")
    }

    @Test
    fun closeIsIdempotent() = runTest {
        val session = ScriptedSession(ScriptedAdapter())
        session.initialize()

        assertIs<OperationOutcome.Success<*>>(session.close())
        assertIs<OperationOutcome.Success<*>>(session.close())
        assertEquals(ProtocolState.CLOSED, session.state.value)
    }

    @Test
    fun eventsReachACollectorAttachedBeforeTheyAreEmitted() = runTest {
        val session = ScriptedSession(ScriptedAdapter())
        val received = mutableListOf<ProtocolEvent>()
        // UNDISPATCHED so the collector subscribes synchronously before the emit — a replay=0 stream drops
        // anything sent to a not-yet-attached collector, and that drop is the contract, not a bug to hide.
        val collector = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            session.events.collect { received += it }
        }

        session.events.tryEmit(ProtocolEvent.InitializationCompleted(succeeded = true, atEpochMillis = 1L))
        runCurrent() // let the attached collector drain the buffered edge (SharedFlow delivery is asynchronous)

        assertEquals(1, received.size)
        assertIs<ProtocolEvent.InitializationCompleted>(received.single())
        collector.cancel()
    }
}
