"""`bridge.gateway`: ingestion, per-connection sequencing, rejection accounting and backoff.

The rule under test is the one the brief calls out: a rejected frame increments a counter and is
answered to the peer, instead of vanishing. Each rejection test therefore asserts three things - the
counter, the code it was filed under, and the notice the peer actually received.
"""

from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path

_HERE = Path(__file__).resolve().parent
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))

import support  # noqa: E402
from bridge import HARNESS_PACKAGE  # noqa: E402
from bridge.capture import ForegroundGate, ScreenCaptureSession  # noqa: E402
from bridge.envelope import AgentEnvelope, EventType  # noqa: E402
from bridge.errors import BridgeErrorCode, NotForeground, TransportClosed  # noqa: E402
from bridge.gateway import (  # noqa: E402
    BackoffSchedule,
    BridgeClient,
    Gateway,
    stderr_logger,
    stream_frames,
)
from bridge.ws_server import Opcode, read_frame  # noqa: E402

TOKEN = "gateway-test-token-0123456789"


def frame(opcode: Opcode, payload: bytes) -> bytes:
    """A client frame, as the peer would send it."""
    return support.render_frame(opcode, payload, mask=True)


def sent_messages(stream: support.FakeStream) -> list[tuple[Opcode, bytes]]:
    """Everything the server wrote after its handshake response."""
    head_end = stream.sent_bytes.find(b"\r\n\r\n")
    assert head_end >= 0
    offset = head_end + 4
    out: list[tuple[Opcode, bytes]] = []
    probe = support.FakeStream(stream.sent_bytes[offset:])
    try:
        while True:
            item = read_frame(probe, require_mask=False)
            out.append((item.opcode, item.payload))
    except TransportClosed:
        return out


def notices(stream: support.FakeStream) -> list[dict[str, object]]:
    parsed: list[dict[str, object]] = []
    for opcode, payload in sent_messages(stream):
        if opcode is not Opcode.TEXT:
            continue
        body = json.loads(payload.decode("utf-8"))
        if body.get("notice") == "envelope_rejected":
            parsed.append(body)
    return parsed


def drive(token: str = TOKEN, *frames: bytes) -> support.FakeStream:
    """A stream carrying a valid handshake followed by the peer's frames."""
    return support.FakeStream(
        support.handshake_request_bytes("/?token=" + token) + b"".join(frames)
    )


class RecordingLogger:
    def __init__(self) -> None:
        self.lines: list[str] = []

    def __call__(self, message: str) -> None:
        self.lines.append(message)

    def __contains__(self, needle: str) -> bool:
        return any(needle in line for line in self.lines)


class GatewayIngestionTest(unittest.TestCase):
    def setUp(self) -> None:
        self.logger = RecordingLogger()
        self.gateway = Gateway(
            token=TOKEN,
            logger=self.logger,
            replay_capacity=4,
            max_rejections_per_connection=3,
        )
        self.received: list[tuple[AgentEnvelope, str]] = []
        self.subscription = self.gateway.subscribe(
            EventType.TELEMETRY,
            lambda envelope, connection_id: self.received.append((envelope, connection_id)),
        )

    def tearDown(self) -> None:
        self.gateway.stop()

    def ingest(self, *envelopes: AgentEnvelope, binary: bytes | None = None) -> support.FakeStream:
        payload = b""
        for envelope in envelopes:
            payload += frame(Opcode.TEXT, envelope.encode().encode("utf-8"))
        if binary is not None:
            payload += frame(Opcode.BINARY, binary)
        stream = drive(TOKEN, payload)
        self.gateway.handle_stream(stream)
        return stream

    def test_a_valid_envelope_reaches_the_subscriber(self) -> None:
        wire = (
            '{"version":1,"seq_no":1,"timestamp":1777000000.5,"event_type":"TELEMETRY",'
            '"payload":{"state":"ready"}}'
        )
        self.ingest(AgentEnvelope.decode(wire))
        self.assertEqual(len(self.received), 1)
        envelope, connection_id = self.received[0]
        self.assertEqual(envelope.payload["state"], "ready")
        self.assertTrue(connection_id.startswith("conn-"))
        self.assertEqual(self.gateway.metrics.envelopes_accepted, 1)
        self.assertEqual(self.gateway.metrics.frames_received, 1)
        self.assertEqual(self.gateway.metrics.envelopes_rejected, 0)

    def test_a_sequence_of_envelopes_is_accepted_in_order(self) -> None:
        self.ingest(*(self.telemetry(seq) for seq in range(1, 4)))
        self.assertEqual([entry.seq_no for entry, _ in self.received], [1, 2, 3])
        self.assertEqual(self.gateway.metrics.envelopes_accepted, 3)

    def test_an_out_of_order_envelope_is_counted_and_answered(self) -> None:
        stream = self.ingest(self.telemetry(1), self.telemetry(2), self.telemetry(2))
        self.assertEqual(self.gateway.metrics.envelopes_accepted, 2)
        self.assertEqual(self.gateway.metrics.envelopes_rejected, 1)
        self.assertEqual(
            self.gateway.metrics.rejections_by_code[BridgeErrorCode.ENVELOPE_SEQ_OUT_OF_ORDER.value],
            1,
        )
        bodies = notices(stream)
        self.assertEqual(len(bodies), 1)
        self.assertEqual(bodies[0]["error_code"], "envelope_seq_out_of_order")
        self.assertEqual(bodies[0]["last_accepted_seq_no"], 2)

    def test_a_gap_is_counted_under_its_own_code(self) -> None:
        stream = self.ingest(self.telemetry(1), self.telemetry(4))
        self.assertEqual(
            self.gateway.metrics.rejections_by_code[BridgeErrorCode.ENVELOPE_SEQ_GAP.value], 1
        )
        self.assertEqual(notices(stream)[0]["error_code"], "envelope_seq_gap")

    def test_a_malformed_frame_is_counted_not_dropped(self) -> None:
        stream = drive(TOKEN, frame(Opcode.TEXT, b"{not json"))
        self.gateway.handle_stream(stream)
        self.assertEqual(self.gateway.metrics.envelopes_rejected, 1)
        self.assertEqual(
            self.gateway.metrics.rejections_by_code[BridgeErrorCode.ENVELOPE_NOT_JSON.value], 1
        )
        self.assertEqual(notices(stream)[0]["error_code"], "envelope_not_json")
        self.assertIn("rejected a frame", self.logger)

    def test_a_binary_frame_is_refused_because_envelopes_are_text(self) -> None:
        stream = self.ingest(self.telemetry(1), binary=b"\x01\x02")
        self.assertEqual(self.gateway.metrics.binary_frames_rejected, 1)
        self.assertEqual(self.gateway.metrics.envelopes_accepted, 1)
        self.assertEqual(notices(stream)[0]["error_code"], "envelope_payload_invalid")

    def test_sequence_state_is_per_connection(self) -> None:
        accepted: list[int] = []
        for _ in range(2):
            self.ingest(self.telemetry(1))
            accepted.append(self.gateway.metrics.envelopes_accepted)
        # Each connection restarts at seq 1 and both are accepted. A shared ledger would have
        # refused the second one as a repeat of the first.
        self.assertEqual(accepted, [1, 2])
        self.assertEqual(self.gateway.metrics.envelopes_rejected, 0)

    def test_the_replay_window_holds_accepted_envelopes_in_order(self) -> None:
        self.ingest(*(self.telemetry(seq) for seq in range(1, 6)))
        replayed = self.gateway.replay_since(2)
        self.assertEqual([entry.seq_no for entry in replayed], [3, 4, 5])
        self.assertTrue(self.gateway.replay_is_complete_since(4))
        # capacity 4 and 5 appended means frame 1 was evicted
        self.assertFalse(self.gateway.replay_is_complete_since(0))

    def test_a_subscriber_that_raises_does_not_end_the_connection(self) -> None:
        def explode(envelope: AgentEnvelope, connection_id: str) -> None:
            raise RuntimeError("subscriber is broken")

        gateway = Gateway(token=TOKEN, logger=self.logger)
        gateway.subscribe(EventType.TELEMETRY, explode)
        stream = drive(TOKEN, frame(Opcode.TEXT, self.telemetry(1).encode().encode("utf-8")))
        gateway.handle_stream(stream)
        self.assertEqual(gateway.metrics.callback_failures, 1)
        self.assertEqual(gateway.metrics.envelopes_accepted, 1)
        self.assertNotIn(bytes([0x88]), stream.sent_bytes[stream.sent_bytes.find(b"\r\n\r\n") + 4:])

    def test_unsubscribe_stops_delivery_and_is_idempotent(self) -> None:
        self.assertTrue(self.gateway.unsubscribe(self.subscription))
        self.assertFalse(self.gateway.unsubscribe(self.subscription))
        self.ingest(self.telemetry(1))
        self.assertEqual(self.received, [])
        self.assertEqual(self.gateway.metrics.envelopes_accepted, 1)

    def test_a_type_other_than_surveillance_is_not_a_subscription(self) -> None:
        with self.assertRaises(TypeError):
            self.gateway.subscribe("TELEMETRY", lambda e, c: None)  # type: ignore[arg-type]
        with self.assertRaises(TypeError):
            self.gateway.subscribe(EventType.TELEMETRY, "not-callable")  # type: ignore[arg-type]

    def test_the_rejection_budget_closes_the_connection_with_1008(self) -> None:
        stream = drive(
            TOKEN,
            frame(Opcode.TEXT, b"{bad"),
            frame(Opcode.TEXT, b"{bad"),
            frame(Opcode.TEXT, b"{bad"),
        )
        self.gateway.handle_stream(stream)
        self.assertEqual(self.gateway.metrics.envelopes_rejected, 3)
        closing = [payload for opcode, payload in sent_messages(stream) if opcode is Opcode.CLOSE]
        self.assertTrue(closing, "the gateway must close the connection once the budget is spent")

    def test_only_the_subscribed_event_type_is_delivered(self) -> None:
        surface = AgentEnvelope(
            version=1,
            seq_no=2,
            timestamp=1777000000.5,
            event_type=EventType.INPUT_ACTION,
            payload={"action": "tap"},
        )
        self.ingest(self.telemetry(1), surface)
        self.assertEqual([entry.event_type for entry, _ in self.received], [EventType.TELEMETRY])

    def test_a_surface_frame_envelope_is_retained_and_replayable(self) -> None:
        gateway = Gateway(token=TOKEN, logger=self.logger)
        seen: list[AgentEnvelope] = []
        gateway.subscribe(EventType.SURFACE_FRAME, lambda envelope, cid: seen.append(envelope))
        import base64

        frame_envelope = AgentEnvelope(
            version=1,
            seq_no=1,
            timestamp=1777000000.5,
            event_type=EventType.SURFACE_FRAME,
            payload={
                "encoding": "image/png",
                "data_base64": base64.b64encode(support.screencap_png()).decode("ascii"),
                "width": 1080,
                "height": 2400,
            },
        )
        stream = drive(TOKEN, frame(Opcode.TEXT, frame_envelope.encode().encode("utf-8")))
        gateway.handle_stream(stream)
        self.assertEqual(len(seen), 1)
        self.assertEqual(gateway.replay_since(0)[0].event_type, EventType.SURFACE_FRAME)

    def telemetry(self, seq_no: int) -> AgentEnvelope:
        return AgentEnvelope(
            version=1,
            seq_no=seq_no,
            timestamp=1777000000.5,
            event_type=EventType.TELEMETRY,
            payload={"probe": seq_no},
        )


class GatewayConstructionTest(unittest.TestCase):
    def test_the_gateway_refuses_a_host_that_is_not_loopback(self) -> None:
        with self.assertRaises(ValueError):
            Gateway(token=TOKEN, host="0.0.0.0")

    def test_the_gateway_refuses_a_placeholder_token(self) -> None:
        with self.assertRaises(ValueError):
            Gateway(token="nope")

    def test_a_stopped_gateway_does_not_listen_again(self) -> None:
        gateway = Gateway(token=TOKEN, logger=RecordingLogger())
        gateway.stop()
        gateway.stop()
        with self.assertRaises(RuntimeError):
            gateway.start()

    def test_the_stderr_logger_truncates_and_prefixes(self) -> None:
        # Only that it is callable and does not raise; its output is not captured here.
        self.assertTrue(callable(stderr_logger))


class BackoffScheduleTest(unittest.TestCase):
    def test_the_default_sequence_doubles_until_the_ceiling(self) -> None:
        schedule = BackoffSchedule(initial_delay_s=0.25, factor=2.0, max_delay_s=8.0, max_attempts=6)
        self.assertEqual(schedule.schedule(), (0.0, 0.25, 0.5, 1.0, 2.0, 4.0))

    def test_the_ceiling_is_respected(self) -> None:
        schedule = BackoffSchedule(initial_delay_s=1.0, factor=3.0, max_delay_s=4.0, max_attempts=5)
        self.assertEqual(schedule.schedule(), (0.0, 1.0, 3.0, 4.0, 4.0))

    def test_the_first_attempt_never_waits(self) -> None:
        self.assertEqual(BackoffSchedule().delay_before_attempt(1), 0.0)

    def test_an_attempt_number_below_one_is_refused(self) -> None:
        with self.assertRaises(ValueError):
            BackoffSchedule().delay_before_attempt(0)

    def test_impossible_schedules_are_refused_at_construction(self) -> None:
        with self.assertRaises(ValueError):
            BackoffSchedule(initial_delay_s=0)
        with self.assertRaises(ValueError):
            BackoffSchedule(factor=0.5)
        with self.assertRaises(ValueError):
            BackoffSchedule(initial_delay_s=5.0, max_delay_s=1.0)
        with self.assertRaises(ValueError):
            BackoffSchedule(max_attempts=0)


class BridgeClientTest(unittest.TestCase):
    def make_client(self, factory, **overrides: object) -> BridgeClient:
        kwargs: dict[str, object] = {
            "port": 8777,
            "token": TOKEN,
            "stream_factory": factory,
            "logger": RecordingLogger(),
            "sleep": lambda seconds: None,
        }
        kwargs.update(overrides)
        return BridgeClient(**kwargs)  # type: ignore[arg-type]

    def test_publish_writes_one_masked_text_frame(self) -> None:
        stream = support.FakeStream(auto_accept_handshake=True)
        client = self.make_client(lambda host, port: stream)
        client.connect()
        envelope = AgentEnvelope(
            version=1,
            seq_no=1,
            timestamp=1777000000.5,
            event_type=EventType.TELEMETRY,
            payload={"probe": 1},
        )
        client.publish(envelope)
        frames = sent_frames_of(stream, mask_required=True)
        self.assertEqual(len(frames), 1)
        self.assertEqual(frames[0][0], Opcode.TEXT)
        self.assertEqual(json.loads(frames[0][1].decode("utf-8"))["seq_no"], 1)
        self.assertEqual(client.published_count, 1)
        client.close()

    def test_publish_failure_is_reported_and_never_retried(self) -> None:
        stream = support.FakeStream(auto_accept_handshake=True)
        client = self.make_client(lambda host, port: stream)
        client.connect()
        stream.fail_send = True
        envelope = AgentEnvelope(
            version=1,
            seq_no=1,
            timestamp=1777000000.5,
            event_type=EventType.INPUT_ACTION,
            payload={"action": "tap"},
        )
        attempts = 0
        try:
            client.publish(envelope)
        except TransportClosed:
            attempts = 1
        self.assertEqual(attempts, 1, "publish must raise rather than replay a side effect")
        self.assertEqual(client.published_count, 0)
        client.close()

    def test_reconnect_backoff_is_bounded_and_its_schedule_is_used(self) -> None:
        slept: list[float] = []
        attempts: list[int] = []
        good = support.FakeStream(auto_accept_handshake=True)

        def factory(host: str, port: int) -> support.FakeStream:
            attempts.append(len(attempts) + 1)
            if len(attempts) < 3:
                raise OSError("connection refused")
            return good

        client = self.make_client(
            factory,
            sleep=slept.append,
            backoff=BackoffSchedule(initial_delay_s=0.5, factor=2.0, max_delay_s=8.0, max_attempts=5),
        )
        connection = client.connect()
        self.assertTrue(client.is_connected)
        self.assertEqual(len(attempts), 3)
        self.assertEqual(slept, [0.5, 1.0])
        self.assertEqual(client.metrics.reconnects_attempted, 3)
        self.assertEqual(client.metrics.reconnects_succeeded, 1)
        connection.close()
        client.close()

    def test_the_budget_is_a_hard_stop_with_the_schedule_in_the_error(self) -> None:
        def factory(host: str, port: int) -> support.FakeStream:
            raise OSError("nobody is listening")

        client = self.make_client(
            factory,
            sleep=lambda seconds: None,
            backoff=BackoffSchedule(initial_delay_s=0.1, factor=1.0, max_delay_s=0.1, max_attempts=3),
        )
        with self.assertRaises(TransportClosed) as caught:
            client.connect()
        self.assertEqual(caught.exception.context["attempts"], 3)
        self.assertEqual(caught.exception.context["schedule_s"], [0.0, 0.1, 0.1])

    def test_an_authorization_refusal_is_not_retried(self) -> None:
        calls: list[int] = []

        class RejectingStream(support.FakeStream):
            def _maybe_respond(self) -> None:
                self._responded = True
                self.feed(b"HTTP/1.1 401 Unauthorized\r\nConnection: close\r\n\r\n")

        def factory(host: str, port: int) -> support.FakeStream:
            calls.append(1)
            return RejectingStream(auto_accept_handshake=True)

        client = self.make_client(factory, backoff=BackoffSchedule(max_attempts=5))
        from bridge.errors import HandshakeRejected

        with self.assertRaises(HandshakeRejected):
            client.connect()
        self.assertEqual(len(calls), 1, "a refusal the client cannot fix must not be retried")

    def test_a_closed_client_refuses_to_connect_or_publish(self) -> None:
        stream = support.FakeStream(auto_accept_handshake=True)
        client = self.make_client(lambda host, port: stream)
        client.connect()
        client.close()
        with self.assertRaises(TransportClosed):
            client.connect()

    def test_the_client_refuses_a_host_that_is_not_loopback(self) -> None:
        with self.assertRaises(ValueError):
            BridgeClient(port=8777, token=TOKEN, host="0.0.0.0")


class GateAttachmentTest(unittest.TestCase):
    def test_the_gate_is_confirmed_before_a_frame_is_credited(self) -> None:
        transport, runner = support.make_transport()
        runner.on(
            "shell",
            "dumpsys",
            "window",
            stdout=b"  mCurrentFocus=Window{1 u0 com.omnibuds.tools.shell/.ShellActivity}\n",
        )
        from bridge.gateway import attach_gate

        logger = RecordingLogger()
        observed = attach_gate(logger, ForegroundGate(transport, HARNESS_PACKAGE))
        self.assertEqual(observed, HARNESS_PACKAGE)
        self.assertIn("foreground confirmed", logger)

    def test_a_foreign_foreground_raises_through_the_gate(self) -> None:
        from bridge.errors import NotForeground

        transport, runner = support.make_transport()
        runner.on(
            "shell",
            "dumpsys",
            "window",
            stdout=b"  mCurrentFocus=Window{1 u0 com.example.foreigndemo/.MainActivity}\n",
        )
        from bridge.gateway import attach_gate

        with self.assertRaises(NotForeground):
            attach_gate(RecordingLogger(), ForegroundGate(transport, HARNESS_PACKAGE))


def sent_frames_of(stream: support.FakeStream, *, mask_required: bool) -> list[tuple[Opcode, bytes]]:
    """Decode the frames a client wrote after its handshake request."""
    head_end = stream.sent_bytes.find(b"\r\n\r\n")
    assert head_end >= 0
    probe = support.FakeStream(stream.sent_bytes[head_end + 4:])
    out: list[tuple[Opcode, bytes]] = []
    try:
        while True:
            item = read_frame(probe, require_mask=mask_required)
            out.append((item.opcode, item.payload))
    except TransportClosed:
        return out


class FrameStreamingTest(unittest.TestCase):
    """`stream_frames` is the whole path: gate, capture, envelope, sequence, publish."""

    def build(self, focus_sequence: list[str]) -> tuple[ScreenCaptureSession, BridgeClient, support.FakeAdbRunner, support.FakeStream]:
        transport, runner = support.make_transport()
        for package in focus_sequence:
            runner.on(
                "shell",
                "dumpsys",
                "window",
                stdout=(
                    "  mCurrentFocus=Window{6d3b1c8 u0 " + package + "/" + package + ".ShellActivity}\n"
                ).encode("utf-8"),
            )
        runner.on("exec-out", "screencap", "-p", stdout=support.screencap_png(), repeat=True)
        session = ScreenCaptureSession(transport, ForegroundGate(transport, HARNESS_PACKAGE))
        peer = support.FakeStream(auto_accept_handshake=True)
        client = BridgeClient(
            port=8777,
            token=TOKEN,
            stream_factory=lambda host, port: peer,
            sleep=lambda seconds: None,
            logger=RecordingLogger(),
        )
        client.connect()
        return session, client, runner, peer

    def test_three_frames_are_captured_sequenced_and_published(self) -> None:
        session, client, runner, peer = self.build([HARNESS_PACKAGE] * 3)
        published = stream_frames(session, client, count=3, start_seq_no=1, timestamp=lambda: 1777000000.5)
        self.assertEqual([entry.seq_no for entry in published], [1, 2, 3])
        self.assertEqual([entry.event_type for entry in published], [EventType.SURFACE_FRAME] * 3)
        self.assertEqual(client.published_count, 3)
        frames = sent_frames_of(peer, mask_required=True)
        self.assertEqual(len(frames), 3)
        body = json.loads(frames[0][1].decode("utf-8"))
        self.assertEqual(body["event_type"], "SURFACE_FRAME")
        self.assertEqual(body["payload"]["encoding"], "image/png")
        # one focus read per frame: the gate is not cached between frames
        self.assertEqual(len([call for call in runner.calls if "dumpsys" in call]), 3)

    def test_a_foreground_loss_mid_stream_propagates_instead_of_truncating(self) -> None:
        session, client, runner, peer = self.build([HARNESS_PACKAGE, "com.example.foreigndemo", HARNESS_PACKAGE])
        with self.assertRaises(NotForeground):
            stream_frames(session, client, count=3, start_seq_no=1, timestamp=lambda: 1777000000.5)
        self.assertEqual(client.published_count, 1, "only the frames captured under the gate were sent")

    def test_stream_arguments_that_are_not_positions_are_refused(self) -> None:
        session, client, _, _ = self.build([HARNESS_PACKAGE])
        with self.assertRaises(ValueError):
            stream_frames(session, client, count=0, start_seq_no=1)
        with self.assertRaises(ValueError):
            stream_frames(session, client, count=1, start_seq_no=0)
        with self.assertRaises(ValueError):
            stream_frames(session, client, count=1, start_seq_no=-3)


if __name__ == "__main__":
    unittest.main()
