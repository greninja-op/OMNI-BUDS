"""Ingestion: the layer that turns WebSocket frames into validated, sequenced, dispatched envelopes.

Two halves, one policy
----------------------
`Gateway` is the inbound side. It owns a `WebSocketServer`, keeps a `SequenceLedger` per connection,
replays through a `ReplayBuffer`, and fans envelopes out to subscribers.

`BridgeClient` is the outbound side: an envelope publisher that reconnects with exponential backoff.

The policy that binds both, stated plainly:

* **A rejected frame is counted, never dropped silently.** Every refusal increments a metric keyed
  by error code, is written to stderr as one bounded line, and is answered to the peer with a JSON
  notice carrying the code and the sequence number we expected. Silence is the failure mode this
  rule exists to prevent: an agent that stops receiving frames must be able to see that it stopped.
* **Reconnect is retry-safe; publish is not.** Forming a connection is idempotent, so it may be
  retried - with a bounded budget. Publishing an `INPUT_ACTION` is not, so `publish` never replays
  across a reconnect: it raises `TransportClosed` and leaves the decision to the caller. That is
  `docs/phases/phase-0/specs.md` section 4 (blind retry of a side-effecting command is prohibited)
  applied to this bridge.
* **No frame content in the log.** Only codes, counts and connection ids.

The rejection budget is finite: after `MAX_REJECTIONS_PER_CONNECTION` refusals the connection is
closed with 1008 (policy violation). A peer that cannot produce a valid envelope twice is not going
to produce one on the fifth try, and leaving it open grows the ledger and the buffer forever.
"""

from __future__ import annotations

import sys
import threading
import time
from dataclasses import dataclass, field
from typing import Callable, Protocol

from .capture import ForegroundGate, ScreenCaptureSession
from .envelope import (
    MAX_FRAME_BYTES,
    REPLAY_CAPACITY_DEFAULT,
    AgentEnvelope,
    EnvelopeNotice,
    EventType,
    ReplayBuffer,
    SequenceLedger,
)
from .errors import (
    BridgeError,
    BridgeErrorCode,
    EnvelopeInvalid,
    HandshakeRejected,
    TransportClosed,
)
from .ws_server import (
    LOOPBACK_HOST,
    ByteStream,
    CloseCode,
    Opcode,
    ReceivedMessage,
    Role,
    StreamFactory,
    WebSocketClient,
    WebSocketConnection,
    WebSocketServer,
)

#: Refusals tolerated on one connection before it is closed. Eight is two round trips of the same
#: mistake, which is enough to make the pattern obvious in the metrics.
MAX_REJECTIONS_PER_CONNECTION = 8

#: Subscribers are keyed by (event_type, callback) so an unsubscribe is exact and idempotent.
SubscriptionCallback = Callable[[AgentEnvelope, str], None]


class Logger(Protocol):
    """Sink for the bounded, content-free lines this module emits."""

    def __call__(self, message: str) -> None: ...


def stderr_logger(message: str) -> None:
    sys.stderr.write("omnibuds-device-bridge gateway: " + message[:400] + "\n")


@dataclass(frozen=True)
class BackoffSchedule:
    """Exponential backoff with a ceiling and a hard attempt budget.

    No jitter, deliberately: the peer is a single localhost process, so contention is not the
    problem, and a random component would make the reconnect sequence untestable. `attempts` is
    bounded because the project forbids unbounded retry loops.
    """

    initial_delay_s: float = 0.25
    factor: float = 2.0
    max_delay_s: float = 8.0
    max_attempts: int = 5

    def __post_init__(self) -> None:
        if self.initial_delay_s <= 0:
            raise ValueError("initial_delay_s must be positive")
        if self.factor < 1.0:
            raise ValueError("factor must be at least 1.0; backoff that shrinks is not backoff")
        if self.max_delay_s < self.initial_delay_s:
            raise ValueError("max_delay_s must not be below initial_delay_s")
        if self.max_attempts < 1:
            raise ValueError("max_attempts must be at least 1; a retry budget of zero is a single call")

    def delay_before_attempt(self, attempt: int) -> float:
        """The delay in seconds before retry number `attempt` (1-based: attempt 1 has no delay)."""
        if attempt < 1:
            raise ValueError("attempt is 1-based")
        if attempt == 1:
            return 0.0
        exponent = attempt - 2
        delay = self.initial_delay_s * (self.factor**exponent)
        return min(delay, self.max_delay_s)

    def schedule(self) -> tuple[float, ...]:
        """The full delay sequence, for logging and for tests that assert on it."""
        return tuple(self.delay_before_attempt(attempt) for attempt in range(1, self.max_attempts + 1))


@dataclass
class GatewayMetrics:
    """Counters for one gateway. Everything here is a number a reviewer can audit; nothing is a string.

    `rejections_by_code` is the field that answers "why did the frames stop": a growing count under
    a specific `BridgeErrorCode` value is a diagnosis, where a dropped frame would have been silence.
    """

    frames_received: int = 0
    envelopes_accepted: int = 0
    envelopes_rejected: int = 0
    binary_frames_rejected: int = 0
    notices_sent: int = 0
    notice_failures: int = 0
    callback_failures: int = 0
    reconnects_attempted: int = 0
    reconnects_succeeded: int = 0
    connections_opened: int = 0
    connections_closed: int = 0
    rejections_by_code: dict[str, int] = field(default_factory=dict)

    def record_rejection(self, code: BridgeErrorCode) -> None:
        key = code.value
        self.rejections_by_code[key] = self.rejections_by_code.get(key, 0) + 1

    def as_dict(self) -> dict[str, object]:
        return {
            "frames_received": self.frames_received,
            "envelopes_accepted": self.envelopes_accepted,
            "envelopes_rejected": self.envelopes_rejected,
            "binary_frames_rejected": self.binary_frames_rejected,
            "notices_sent": self.notices_sent,
            "notice_failures": self.notice_failures,
            "callback_failures": self.callback_failures,
            "reconnects_attempted": self.reconnects_attempted,
            "reconnects_succeeded": self.reconnects_succeeded,
            "connections_opened": self.connections_opened,
            "connections_closed": self.connections_closed,
            "rejections_by_code": dict(self.rejections_by_code),
        }


@dataclass(frozen=True)
class Subscription:
    """A handle that can be revoked. Keeping a live callback without one is how a leak becomes permanent."""

    event_type: EventType
    callback: SubscriptionCallback
    token: str


class Gateway:
    """Ingestion layer: validate, sequence, retain, dispatch, account.

    Threading model: the server calls `_on_message` from a per-connection thread, so all ledger,
    buffer and metrics mutation happens under one lock. Dispatch to subscribers happens *outside*
    that lock, because a subscriber that calls back into the gateway (the common case: "capture the
    next frame") must not deadlock against the lock it is already waiting on.
    """

    def __init__(
        self,
        *,
        token: str,
        port: int = 0,
        host: str = LOOPBACK_HOST,
        replay_capacity: int = REPLAY_CAPACITY_DEFAULT,
        max_rejections_per_connection: int = MAX_REJECTIONS_PER_CONNECTION,
        logger: Logger = stderr_logger,
        clock: Callable[[], float] = time.time,
    ) -> None:
        if host != LOOPBACK_HOST:
            raise ValueError("the gateway listens on " + LOOPBACK_HOST + " only; got " + repr(host))
        if max_rejections_per_connection < 1:
            raise ValueError("max_rejections_per_connection must be at least 1")
        self._clock = clock
        self._logger = logger
        self._max_rejections = max_rejections_per_connection
        self._replay = ReplayBuffer(replay_capacity)
        self._metrics = GatewayMetrics()
        self._lock = threading.RLock()
        self._ledgers: dict[str, SequenceLedger] = {}
        self._rejections: dict[str, int] = {}
        self._subscribers: dict[EventType, list[Subscription]] = {
            event_type: [] for event_type in EventType
        }
        self._subscription_tokens: dict[str, EventType] = {}
        self._server = WebSocketServer(
            token=token,
            port=port,
            host=host,
            on_open=self._on_open,
            on_message=self._on_message,
            on_close=self._on_close,
            name="omnibuds-device-bridge-gateway",
        )
        self._started = False
        self._stopped = False

    # ------------------------------------------------------------------ lifecycle

    def start(self) -> int:
        """Bind the loopback listener. Returns the port actually bound."""
        if self._started:
            raise RuntimeError("the gateway is already started")
        if self._stopped:
            raise RuntimeError("the gateway has been stopped; construct a new one")
        port = self._server.start()
        self._started = True
        return port

    def stop(self) -> None:
        """Close every connection and stop listening. Idempotent."""
        if self._stopped:
            return
        self._server.stop(CloseCode.GOING_AWAY)
        self._stopped = True

    def __enter__(self) -> "Gateway":
        self.start()
        return self

    def __exit__(self, exc_type: object, exc: object, tb: object) -> None:
        self.stop()

    @property
    def port(self) -> int:
        return self._server.port

    @property
    def server(self) -> WebSocketServer:
        return self._server

    @property
    def metrics(self) -> GatewayMetrics:
        return self._metrics

    @property
    def replay(self) -> ReplayBuffer:
        return self._replay

    @property
    def connection_ids(self) -> tuple[str, ...]:
        return self._server.connection_ids

    def handle_stream(self, stream: ByteStream, *, peer_name: str = "in-memory") -> WebSocketConnection | None:
        """Drive one connection over an arbitrary stream, without a listener.

        This is the seam the protocol tests use: same handshake, same dispatch, no port.
        """
        return self._server.handle_stream(stream, peer_name=peer_name)

    # ------------------------------------------------------------------ subscriptions

    def subscribe(self, event_type: EventType, callback: SubscriptionCallback) -> Subscription:
        """Register `callback` for one event type.

        A caller that wants everything subscribes three times, explicitly; a wildcard subscription
        would make "who received this frame" unanswerable from the code.
        """
        if not isinstance(event_type, EventType):
            raise TypeError("subscribe requires an EventType member, got " + repr(event_type))
        if not callable(callback):
            raise TypeError("subscribe requires a callable")
        token = "sub-" + str(id(callback)) + "-" + event_type.value
        subscription = Subscription(event_type=event_type, callback=callback, token=token)
        with self._lock:
            self._subscribers[event_type].append(subscription)
            self._subscription_tokens[token] = event_type
        return subscription

    def unsubscribe(self, subscription: Subscription) -> bool:
        """Revoke a subscription. Returns whether it was still registered (idempotent)."""
        with self._lock:
            bucket = self._subscribers[subscription.event_type]
            for index, existing in enumerate(bucket):
                if existing.token == subscription.token:
                    del bucket[index]
                    self._subscription_tokens.pop(subscription.token, None)
                    return True
        return False

    def subscriber_count(self, event_type: EventType) -> int:
        with self._lock:
            return len(self._subscribers[event_type])

    # ------------------------------------------------------------------ server callbacks

    def _on_open(self, connection: WebSocketConnection) -> None:
        with self._lock:
            self._ledgers[connection.id] = SequenceLedger()
            self._rejections[connection.id] = 0
            self._metrics.connections_opened += 1

    def _on_close(self, connection: WebSocketConnection, close_code: int | None) -> None:
        with self._lock:
            self._ledgers.pop(connection.id, None)
            rejections = self._rejections.pop(connection.id, 0)
            self._metrics.connections_closed += 1
        self._logger(
            "connection " + connection.id + " closed with status " + str(close_code)
            + "; it carried " + str(rejections) + " rejection(s)"
        )

    def _on_message(self, connection: WebSocketConnection, message: ReceivedMessage) -> None:
        self._metrics.frames_received += 1
        if message.opcode is not Opcode.TEXT or message.text is None:
            with self._lock:
                self._metrics.binary_frames_rejected += 1
                self._record_rejection(connection, BridgeErrorCode.ENVELOPE_PAYLOAD_INVALID)
            self._logger(
                "connection " + connection.id + " sent a " + message.opcode.name
                + " frame; envelopes are text frames only"
            )
            self._send_notice(connection, EnvelopeNotice(
                code=BridgeErrorCode.ENVELOPE_PAYLOAD_INVALID,
                message="envelopes arrive as text frames",
            ))
            self._enforce_budget(connection)
            return
        try:
            envelope = AgentEnvelope.decode_within_cap(message.text)
        except BridgeError as exc:
            self._refuse(connection, exc)
            return
        with self._lock:
            ledger = self._ledgers.get(connection.id)
            if ledger is None:
                # The connection opened before any ledger was registered, or closed during the
                # read. Either way this frame cannot be sequenced, so it is refused rather than
                # accepted into an untracked stream.
                self._refuse(
                    connection,
                    EnvelopeInvalid(
                        "no sequence ledger for connection " + connection.id,
                        code=BridgeErrorCode.TRANSPORT_CLOSED,
                    ),
                )
                return
            try:
                ledger.accept(envelope)
            except EnvelopeInvalid as exc:
                self._refuse(connection, exc, ledger=ledger)
                return
            self._replay.append(envelope)
            self._metrics.envelopes_accepted += 1
        self._dispatch(envelope, connection.id)

    def _dispatch(self, envelope: AgentEnvelope, connection_id: str) -> None:
        with self._lock:
            targets = list(self._subscribers[envelope.event_type])
        for subscription in targets:
            try:
                subscription.callback(envelope, connection_id)
            except Exception as exc:  # a broken subscriber must not end a peer's connection
                with self._lock:
                    self._metrics.callback_failures += 1
                self._logger(
                    "subscriber " + subscription.token + " raised " + type(exc).__name__
                    + " on " + envelope.event_type.value + " seq " + str(envelope.seq_no)
                )

    def _refuse(self, connection: WebSocketConnection, error: BaseException, ledger: SequenceLedger | None = None) -> None:
        """Count it, say what it was, and tell the peer. Never drop it here."""
        code = error.code if isinstance(error, BridgeError) else BridgeErrorCode.ENVELOPE_PAYLOAD_INVALID
        message = error.message if isinstance(error, BridgeError) else str(error)
        with self._lock:
            self._metrics.envelopes_rejected += 1
            self._record_rejection(connection, code)
            if ledger is None:
                # A frame that failed to decode still belongs to a stream, and "where were we" is
                # the first question its sender has.
                ledger = self._ledgers.get(connection.id)
            expected = ledger.last_accepted_seq_no if ledger is not None else None
        notice = EnvelopeNotice(
            code=code,
            message=message,
            last_accepted_seq_no=expected,
            context=dict(error.context) if isinstance(error, BridgeError) else {},
        )
        self._logger("rejected a frame on " + connection.id + ": " + notice.as_text())
        self._send_notice(connection, notice)
        self._enforce_budget(connection)

    def _record_rejection(self, connection: WebSocketConnection, code: BridgeErrorCode) -> None:
        self._metrics.record_rejection(code)
        self._rejections[connection.id] = self._rejections.get(connection.id, 0) + 1

    def _send_notice(self, connection: WebSocketConnection, notice: EnvelopeNotice) -> None:
        if connection.is_closed:
            return
        try:
            connection.send_text(notice.as_text())
        except (TransportClosed, OSError) as exc:
            with self._lock:
                self._metrics.notice_failures += 1
            self._logger("could not deliver a rejection notice to " + connection.id + ": " + type(exc).__name__)
            return
        with self._lock:
            self._metrics.notices_sent += 1

    def _enforce_budget(self, connection: WebSocketConnection) -> None:
        with self._lock:
            rejections = self._rejections.get(connection.id, 0)
        if rejections >= self._max_rejections:
            self._logger(
                "closing " + connection.id + " after " + str(rejections)
                + " rejection(s), at the per-connection budget"
            )
            connection.close(CloseCode.POLICY_VIOLATION, "rejection budget exhausted")

    # ------------------------------------------------------------------ replay

    def replay_since(self, seq_no: int) -> tuple[AgentEnvelope, ...]:
        """Retained envelopes after `seq_no`, oldest first.

        Raises:
            EnvelopeInvalid: `seq_no` is not a sequence position.
        """
        return self._replay.replay_since(seq_no)

    def replay_is_complete_since(self, seq_no: int) -> bool:
        """Whether `replay_since(seq_no)` covers everything since that frame.

        `False` means the answer is a partial window, and the caller must not present it as a
        replay. This exists so "we lost the frames" is a question with a yes/no answer.
        """
        return not self._replay.is_gap_before_replay_window(seq_no)


class BridgeClient:
    """Outbound publisher: connect, publish envelopes, reconnect on failure with bounded backoff.

    `connect()` is the only place a retry happens, and it retries the *connection*, never a publish.
    `publish()` on a broken connection raises `TransportClosed` with the failure counted, leaving the
    caller to decide whether the effect may already have happened on the phone.
    """

    def __init__(
        self,
        *,
        port: int,
        token: str,
        host: str = LOOPBACK_HOST,
        path: str = "/",
        backoff: BackoffSchedule | None = None,
        sleep: Callable[[float], None] = time.sleep,
        stream_factory: StreamFactory | None = None,
        logger: Logger = stderr_logger,
    ) -> None:
        if host != LOOPBACK_HOST:
            raise ValueError("the bridge client connects to " + LOOPBACK_HOST + " only; got " + repr(host))
        self._backoff = backoff if backoff is not None else BackoffSchedule()
        self._sleep = sleep
        self._logger = logger
        self._metrics = GatewayMetrics()
        self._lock = threading.Lock()
        self._client = WebSocketClient(
            host=host,
            port=port,
            token=token,
            path=path,
            stream_factory=stream_factory,
        )
        self._published = 0
        self._consecutive_failures = 0
        self._closed = False

    @property
    def metrics(self) -> GatewayMetrics:
        return self._metrics

    @property
    def published_count(self) -> int:
        return self._published

    @property
    def is_connected(self) -> bool:
        return self._client.is_connected

    @property
    def connection(self) -> WebSocketConnection:
        return self._client.connection

    @property
    def role(self) -> Role:
        return self._client.connection.role

    def connect(self) -> WebSocketConnection:
        """Connect, retrying the handshake on failure up to the budget.

        Raises:
            TransportClosed: every attempt failed. The message names the attempt count and the
                schedule that was used, so the operator can see how long the bridge actually waited.
            HandshakeRejected: a rejection that backoff cannot fix (bad token, 401/400/426) is
                raised on the first attempt rather than retried - repeating an authorization failure
                is not a transient condition, it is the same refusal eight times.
        """
        if self._closed:
            raise TransportClosed("this client is closed")
        last_error: BaseException | None = None
        for attempt in range(1, self._backoff.max_attempts + 1):
            delay = self._backoff.delay_before_attempt(attempt)
            if delay > 0:
                self._sleep(delay)
            with self._lock:
                self._metrics.reconnects_attempted += 1
            try:
                connection = self._client.connect()
            except HandshakeRejected as exc:
                self._logger(
                    "handshake rejected on attempt " + str(attempt) + " with HTTP "
                    + str(exc.http_status) + "; not retrying"
                )
                raise
            except (TransportClosed, OSError) as exc:
                last_error = exc
                with self._lock:
                    self._consecutive_failures += 1
                self._logger(
                    "connect attempt " + str(attempt) + " of " + str(self._backoff.max_attempts)
                    + " failed: " + type(exc).__name__
                )
                continue
            with self._lock:
                self._metrics.reconnects_succeeded += 1
                self._consecutive_failures = 0
            return connection
        detail = ""
        if last_error is not None:
            detail = "; last failure was " + type(last_error).__name__
        raise TransportClosed(
            "could not reach the gateway after " + str(self._backoff.max_attempts)
            + " attempt(s), having waited " + str(self._backoff.schedule()) + "s between them"
            + detail + "; the reconnect budget is exhausted, and raising is the only honest answer",
            context={
                "attempts": self._backoff.max_attempts,
                "schedule_s": list(self._backoff.schedule()),
            },
        )

    def publish(self, envelope: AgentEnvelope) -> None:
        """Send one envelope as a text frame. No retry, ever.

        Raises:
            TransportClosed: not connected, or the write failed. The envelope was either delivered
                or not, and this function does not guess which: re-sending an `INPUT_ACTION` that
                already landed would double-tap the phone.
            FrameTooLarge: the encoded envelope is above the agreed cap.
        """
        if self._closed:
            raise TransportClosed("this client is closed; the envelope was not sent")
        text = envelope.encode(MAX_FRAME_BYTES)
        try:
            connection = self._client.connection
            connection.send_text(text)
        except (TransportClosed, OSError) as exc:
            with self._lock:
                self._consecutive_failures += 1
            raise TransportClosed(
                "publish failed and was not retried: " + type(exc).__name__,
                context={"seq_no": envelope.seq_no, "event_type": envelope.event_type.value},
            ) from exc
        with self._lock:
            self._published += 1

    def receive(self) -> ReceivedMessage:
        """Read one message from the gateway (notices, pings)."""
        return self._client.connection.receive()

    def close(self, code: CloseCode | int = CloseCode.NORMAL) -> None:
        """Close the client. Idempotent."""
        if self._closed:
            return
        self._client.close(code)
        self._closed = True

    def __enter__(self) -> "BridgeClient":
        self.connect()
        return self

    def __exit__(self, exc_type: object, exc: object, tb: object) -> None:
        self.close()


def attach_gate(logger: Logger, gate: ForegroundGate) -> str:
    """Log the package the gate just confirmed, or refuse.

    Small and separate on purpose: the gateway must not be the place that decides whether capture is
    allowed, but every frame it ingests was taken under a gate, and recording which package that gate
    vouched for is what makes a stream of frames auditable after the fact.
    """
    observed = gate.assert_foreground()
    logger("foreground confirmed as " + observed)
    return observed


def stream_frames(
    session: ScreenCaptureSession,
    client: BridgeClient,
    *,
    count: int,
    start_seq_no: int,
    timestamp: Callable[[], float] = time.time,
) -> tuple[AgentEnvelope, ...]:
    """Capture `count` gated frames and publish each as a `SURFACE_FRAME` envelope.

    This is the whole frame-streaming path: gate, capture, envelope, sequence, send. It is deliberately
    strict:

    * `start_seq_no` must be at least 1, and the numbers are assigned here in order, so a caller cannot
      feed a stream whose sequence it did not control.
    * The first action of every iteration is the gate, inside `session.capture()`. A `NotForeground`
      raised mid-stream propagates: frames already sent were legitimately captured, and the caller is
      told the stream is broken rather than handed a short tuple as if it succeeded.
    * A `FrameTooLarge` from the encoder also propagates. The remedy is a smaller screen, not a
      bigger buffer, and silently dropping the frame would be the exact silent loss this module forbids.

    Raises:
        ValueError: `count` or `start_seq_no` is not a positive int.
    """
    if isinstance(count, bool) or not isinstance(count, int) or count < 1:
        raise ValueError("stream_frames needs a positive frame count")
    if isinstance(start_seq_no, bool) or not isinstance(start_seq_no, int) or start_seq_no < 1:
        raise ValueError("stream_frames needs a start_seq_no of at least 1; sequences start at 1")
    published: list[AgentEnvelope] = []
    for offset in range(count):
        frame = session.capture()
        envelope = AgentEnvelope.from_frame(frame, seq_no=start_seq_no + offset, timestamp=timestamp())
        client.publish(envelope)
        published.append(envelope)
    return tuple(published)


__all__ = [
    "MAX_REJECTIONS_PER_CONNECTION",
    "BackoffSchedule",
    "GatewayMetrics",
    "Gateway",
    "BridgeClient",
    "Subscription",
    "SubscriptionCallback",
    "Logger",
    "stderr_logger",
    "attach_gate",
    "stream_frames",
]
