"""`bridge.ws_server`: the RFC 6455 handshake, the frame codec, the cap and the close handshake.

Protocol assertions are anchored on bytes from RFC 6455 itself rather than on this package's own
writer, so a symmetric bug in `read_frame` and `write_frame` cannot make the suite green. The
loopback integration tests at the end are the only place a real socket is used; they bind
`127.0.0.1` on an ephemeral port and skip themselves if the environment forbids sockets, rather than
reporting a pass they did not earn.
"""

from __future__ import annotations

import socket
import sys
import unittest
from pathlib import Path

_HERE = Path(__file__).resolve().parent
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))

import support  # noqa: E402
from bridge.envelope import MAX_FRAME_BYTES  # noqa: E402
from bridge.errors import (  # noqa: E402
    BridgeErrorCode,
    FrameTooLarge,
    HandshakeRejected,
    TransportClosed,
)
from bridge.ws_server import (  # noqa: E402
    LOOPBACK_HOST,
    MAX_CONNECTIONS,
    CloseCode,
    Opcode,
    ReceivedMessage,
    Role,
    WebSocketClient,
    WebSocketConnection,
    WebSocketServer,
    apply_mask,
    build_accept_response,
    compute_accept_key,
    decode_close_payload,
    encode_close_payload,
    extract_request_token,
    parse_handshake,
    read_frame,
    token_matches,
    validate_upgrade_headers,
    write_frame,
)

TOKEN = "bridge-test-token-0123456789"

#: RFC 6455 section 5.7: a masked client text frame carrying "Hello".
RFC_MASKED_HELLO_FRAME = bytes([0x81, 0x85, 0x37, 0xFA, 0x21, 0x3D, 0x7F, 0x9F, 0x4D, 0x51, 0x58])


def server_side_connection(inbound: bytes = b"") -> tuple[WebSocketConnection, support.FakeStream]:
    stream = support.FakeStream(inbound)
    return WebSocketConnection(stream, role=Role.SERVER, connection_id="conn-test"), stream


def client_side_connection(inbound: bytes = b"") -> tuple[WebSocketConnection, support.FakeStream]:
    stream = support.FakeStream(inbound)
    return WebSocketConnection(stream, role=Role.CLIENT, connection_id="client-test"), stream


def written_frames(raw: bytes, *, require_mask: bool) -> list[tuple[Opcode, bytes]]:
    """Decode every frame in `raw` so a test can assert on what was actually written."""
    out: list[tuple[Opcode, bytes]] = []
    probe = support.FakeStream(raw)
    try:
        while True:
            frame = read_frame(probe, require_mask=require_mask)
            out.append((frame.opcode, frame.payload))
    except TransportClosed:
        return out


class AcceptKeyTest(unittest.TestCase):
    def test_the_rfc_example_key_produces_the_rfc_example_accept(self) -> None:
        self.assertEqual(
            compute_accept_key("dGhlIHNhbXBsZSBub25jZQ=="),
            "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=",
        )

    def test_a_blank_key_is_refused_rather_than_hashed(self) -> None:
        with self.assertRaises(ValueError):
            compute_accept_key("   ")

    def test_surrounding_whitespace_does_not_change_the_derivation(self) -> None:
        self.assertEqual(
            compute_accept_key("  dGhlIHNhbXBsZSBub25jZQ== "),
            "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=",
        )


class HandshakeParseTest(unittest.TestCase):
    def test_the_request_line_path_and_query_are_parsed(self) -> None:
        request = parse_handshake(support.handshake_request_bytes("/bridge?token=" + TOKEN))
        self.assertEqual(request.method, "GET")
        self.assertEqual(request.path, "/bridge")
        self.assertEqual(request.token_from_query(), TOKEN)
        self.assertEqual(request.websocket_key, "dGhlIHNhbXBsZSBub25jZQ==")
        self.assertEqual(request.version, "13")

    def test_the_well_formed_request_validates(self) -> None:
        validate_upgrade_headers(parse_handshake(support.handshake_request_bytes("/")))

    def test_a_missing_upgrade_header_is_a_400(self) -> None:
        request = parse_handshake(support.handshake_request_bytes("/", upgrade=""))
        with self.assertRaises(HandshakeRejected) as caught:
            validate_upgrade_headers(request)
        self.assertEqual(caught.exception.http_status, 400)

    def test_a_connection_header_without_the_upgrade_token_is_a_400(self) -> None:
        request = parse_handshake(support.handshake_request_bytes("/", connection="keep-alive"))
        with self.assertRaises(HandshakeRejected) as caught:
            validate_upgrade_headers(request)
        self.assertEqual(caught.exception.http_status, 400)

    def test_the_upgrade_token_may_arrive_among_several(self) -> None:
        request = parse_handshake(support.handshake_request_bytes("/", connection="keep-alive, Upgrade"))
        validate_upgrade_headers(request)

    def test_a_missing_key_is_a_400(self) -> None:
        request = parse_handshake(
            b"GET / HTTP/1.1\r\nHost: x\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
            b"Sec-WebSocket-Version: 13\r\n\r\n"
        )
        with self.assertRaises(HandshakeRejected) as caught:
            validate_upgrade_headers(request)
        self.assertEqual(caught.exception.code, BridgeErrorCode.WS_HANDSHAKE_REJECTED)

    def test_a_version_other_than_13_is_a_426(self) -> None:
        request = parse_handshake(support.handshake_request_bytes("/", version="8"))
        with self.assertRaises(HandshakeRejected) as caught:
            validate_upgrade_headers(request)
        self.assertEqual(caught.exception.http_status, 426)
        self.assertEqual(caught.exception.code, BridgeErrorCode.WS_VERSION_UNSUPPORTED)

    def test_a_request_line_that_is_not_method_path_http_is_a_400(self) -> None:
        with self.assertRaises(HandshakeRejected):
            parse_handshake(b"garbage\r\n\r\n")
        with self.assertRaises(HandshakeRejected) as caught:
            validate_upgrade_headers(parse_handshake(b"POST / HTTP/1.1\r\nUpgrade: websocket\r\n\r\n"))
        self.assertEqual(caught.exception.http_status, 400)

    def test_a_head_request_is_refused_because_only_get_may_open_a_socket(self) -> None:
        with self.assertRaises(HandshakeRejected):
            validate_upgrade_headers(parse_handshake(support.handshake_request_bytes("/", method="HEAD")))

    def test_a_header_line_without_a_colon_is_a_400(self) -> None:
        with self.assertRaises(HandshakeRejected):
            parse_handshake(b"GET / HTTP/1.1\r\nnonsense header\r\n\r\n")

    def test_non_ascii_headers_are_refused(self) -> None:
        with self.assertRaises(HandshakeRejected):
            parse_handshake("GET / HTTP/1.1\r\nX-Note: caf\u00e9\r\n\r\n".encode("utf-8"))

    def test_the_accept_response_carries_the_derived_key(self) -> None:
        request = parse_handshake(support.handshake_request_bytes("/"))
        response = build_accept_response(request)
        self.assertIn(b"HTTP/1.1 101 Switching Protocols", response)
        self.assertIn(b"Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", response)


class TokenExtractionTest(unittest.TestCase):
    def test_the_query_parameter_is_a_token(self) -> None:
        request = parse_handshake(support.handshake_request_bytes("/?token=" + TOKEN))
        self.assertEqual(extract_request_token(request), TOKEN)

    def test_the_authorization_header_is_a_token_in_three_shapes(self) -> None:
        for shape in (TOKEN, "Bearer " + TOKEN, "token " + TOKEN):
            request = parse_handshake(support.handshake_request_bytes("/", authorization=shape))
            self.assertEqual(extract_request_token(request), TOKEN)

    def test_no_token_at_all_is_none_not_an_empty_string(self) -> None:
        self.assertIsNone(extract_request_token(parse_handshake(support.handshake_request_bytes("/"))))

    def test_comparison_is_exact_and_none_never_matches(self) -> None:
        self.assertTrue(token_matches(TOKEN, TOKEN))
        self.assertFalse(token_matches(TOKEN[:-1], TOKEN))
        self.assertFalse(token_matches(None, TOKEN))
        self.assertFalse(token_matches("", TOKEN))


class MaskingTest(unittest.TestCase):
    def test_masking_is_its_own_inverse(self) -> None:
        payload = b"envelope-with-a-long-ish-body"
        masked = apply_mask(b"\x01\x02\x03\x04", payload)
        self.assertNotEqual(masked, payload)
        self.assertEqual(apply_mask(b"\x01\x02\x03\x04", masked), payload)

    def test_an_empty_payload_masks_to_nothing(self) -> None:
        self.assertEqual(apply_mask(b"\x00\x00\x00\x00", b""), b"")

    def test_a_mask_that_is_not_four_bytes_is_refused(self) -> None:
        with self.assertRaises(ValueError):
            apply_mask(b"\x01\x02", b"abc")

    def test_the_rfc_example_masking_is_reproduced_byte_for_byte(self) -> None:
        self.assertEqual(apply_mask(b"\x37\xfa\x21\x3d", b"Hello"), b"\x7f\x9f\x4d\x51\x58")


class FrameReadTest(unittest.TestCase):
    def test_the_rfc_masked_client_frame_decodes_to_hello(self) -> None:
        frame = read_frame(support.FakeStream(RFC_MASKED_HELLO_FRAME), require_mask=True)
        self.assertTrue(frame.fin)
        self.assertEqual(frame.opcode, Opcode.TEXT)
        self.assertEqual(frame.payload, b"Hello")

    def test_the_rfc_126_length_frame_decodes(self) -> None:
        payload = b"a" * 600
        header = bytearray([0x81, 126])
        header.extend(len(payload).to_bytes(2, "big"))
        frame = read_frame(support.FakeStream(bytes(header) + payload), require_mask=False)
        self.assertEqual(frame.payload, payload)

    def test_a_64_bit_length_frame_decodes(self) -> None:
        payload = b"z" * 70_000
        header = bytearray([0x82, 127])
        header.extend(len(payload).to_bytes(8, "big"))
        frame = read_frame(support.FakeStream(bytes(header) + payload), require_mask=False)
        self.assertEqual(len(frame.payload), 70_000)

    def test_an_unmasked_client_frame_is_a_protocol_error(self) -> None:
        stream = support.FakeStream(support.render_frame(Opcode.TEXT, b"hi", mask=False))
        with self.assertRaises(TransportClosed) as caught:
            read_frame(stream, require_mask=True)
        self.assertEqual(caught.exception.code, BridgeErrorCode.WS_PROTOCOL_ERROR)
        self.assertEqual(caught.exception.context["ws_close_code"], int(CloseCode.PROTOCOL_ERROR))

    def test_a_masked_server_frame_is_a_protocol_error(self) -> None:
        stream = support.FakeStream(support.render_frame(Opcode.TEXT, b"hi", mask=True))
        with self.assertRaises(TransportClosed):
            read_frame(stream, require_mask=False)

    def test_a_set_rsv_bit_is_a_protocol_error(self) -> None:
        raw = bytearray(support.render_frame(Opcode.TEXT, b"hi", mask=True))
        raw[0] |= 0x10
        with self.assertRaises(TransportClosed):
            read_frame(support.FakeStream(bytes(raw)), require_mask=True)

    def test_an_unknown_opcode_is_a_protocol_error(self) -> None:
        raw = bytearray(support.render_frame(Opcode.TEXT, b"hi", mask=True))
        raw[0] = (raw[0] & 0xF0) | 0x6
        with self.assertRaises(TransportClosed):
            read_frame(support.FakeStream(bytes(raw)), require_mask=True)

    def test_a_frame_above_the_cap_fails_before_the_payload_is_read(self) -> None:
        declared = MAX_FRAME_BYTES + 1
        header = bytearray([0x82, 127])
        header.extend(declared.to_bytes(8, "big"))
        stream = support.FakeStream(bytes(header) + b"x" * 4096)
        with self.assertRaises(FrameTooLarge) as caught:
            read_frame(stream, require_mask=False)
        self.assertEqual(caught.exception.declared_bytes, declared)
        self.assertEqual(caught.exception.cap_bytes, MAX_FRAME_BYTES)
        self.assertEqual(stream.pending, b"x" * 4096, "the payload must not be buffered")

    def test_a_redundant_16_bit_length_is_a_protocol_error(self) -> None:
        header = bytearray([0x81, 126])
        header.extend((5).to_bytes(2, "big"))
        with self.assertRaises(TransportClosed):
            read_frame(support.FakeStream(bytes(header) + b"hello"), require_mask=False)

    def test_a_redundant_64_bit_length_is_a_protocol_error(self) -> None:
        header = bytearray([0x81, 127])
        header.extend((5).to_bytes(8, "big"))
        with self.assertRaises(TransportClosed):
            read_frame(support.FakeStream(bytes(header) + b"hello"), require_mask=False)

    def test_a_64_bit_length_with_the_high_bit_set_is_refused(self) -> None:
        header = bytearray([0x81, 127])
        header.extend((1 << 63).to_bytes(8, "big"))
        with self.assertRaises(TransportClosed):
            read_frame(support.FakeStream(bytes(header)), require_mask=False)

    def test_a_fragmented_control_frame_is_a_protocol_error(self) -> None:
        stream = support.FakeStream(support.render_frame(Opcode.PING, b"hi", mask=True, fin=False))
        with self.assertRaises(TransportClosed):
            read_frame(stream, require_mask=True)

    def test_a_control_frame_over_125_bytes_is_a_protocol_error(self) -> None:
        header = bytearray([0x89, 0x80 | 126])
        header.extend((200).to_bytes(2, "big"))
        header.extend(b"\x01\x02\x03\x04")
        header.extend(b"\x05" * 200)
        with self.assertRaises(TransportClosed):
            read_frame(support.FakeStream(bytes(header)), require_mask=True)

    def test_a_truncated_frame_reports_the_partial_read(self) -> None:
        with self.assertRaises(TransportClosed) as caught:
            read_frame(support.FakeStream(b"\x81"), require_mask=False)
        self.assertEqual(caught.exception.code, BridgeErrorCode.TRANSPORT_CLOSED)


class FrameWriteTest(unittest.TestCase):
    def test_a_client_text_frame_is_masked_and_self_decoding(self) -> None:
        raw = support.render_frame(Opcode.TEXT, b"Hello", mask=True)
        self.assertEqual(raw[0], 0x81)
        self.assertEqual(raw[1] & 0x80, 0x80)
        self.assertEqual(raw[1] & 0x7F, 5)
        self.assertEqual(len(raw), 2 + 4 + 5)
        frame = read_frame(support.FakeStream(raw), require_mask=True)
        self.assertEqual(frame.payload, b"Hello")

    def test_a_server_frame_is_never_masked(self) -> None:
        self.assertEqual(support.render_frame(Opcode.TEXT, b"Hello", mask=False), bytes([0x81, 0x05]) + b"Hello")

    def test_a_large_payload_uses_the_16_bit_length(self) -> None:
        payload = b"q" * 300
        raw = support.render_frame(Opcode.BINARY, payload, mask=False)
        self.assertEqual(raw[1], 126)
        self.assertEqual(int.from_bytes(raw[2:4], "big"), 300)

    def test_writing_above_the_cap_is_refused_rather_than_tried(self) -> None:
        with self.assertRaises(FrameTooLarge):
            write_frame(support.FakeStream(), Opcode.BINARY, b"z" * (MAX_FRAME_BYTES + 1), mask=False)

    def test_two_frames_from_the_same_writer_use_different_mask_keys(self) -> None:
        first = support.render_frame(Opcode.TEXT, b"same", mask=True)
        second = support.render_frame(Opcode.TEXT, b"same", mask=True)
        self.assertNotEqual(first[2:6], second[2:6])


class ClosePayloadTest(unittest.TestCase):
    def test_status_and_reason_round_trip(self) -> None:
        payload = encode_close_payload(CloseCode.GOING_AWAY, "harness stopping")
        status, reason = decode_close_payload(payload)
        self.assertEqual(status, int(CloseCode.GOING_AWAY))
        self.assertEqual(reason, "harness stopping")

    def test_an_empty_close_payload_means_no_status(self) -> None:
        self.assertEqual(decode_close_payload(b""), (None, ""))

    def test_a_one_byte_payload_is_a_protocol_error(self) -> None:
        with self.assertRaises(TransportClosed):
            decode_close_payload(b"\x03")

    def test_an_unregistered_status_is_refused(self) -> None:
        with self.assertRaises(TransportClosed):
            decode_close_payload((1004).to_bytes(2, "big"))

    def test_the_private_use_range_is_accepted(self) -> None:
        status, _ = decode_close_payload((4000).to_bytes(2, "big") + b"reason")
        self.assertEqual(status, 4000)

    def test_a_non_utf8_reason_is_refused(self) -> None:
        with self.assertRaises(TransportClosed):
            decode_close_payload((1000).to_bytes(2, "big") + b"\xff\xfe")

    def test_an_overlong_reason_is_refused(self) -> None:
        with self.assertRaises(ValueError):
            encode_close_payload(CloseCode.NORMAL, "x" * 200)


class ConnectionBehaviourTest(unittest.TestCase):
    def test_a_server_sends_unmasked_and_a_client_sends_masked(self) -> None:
        server, server_stream = server_side_connection()
        server.send_text("hello")
        self.assertEqual(server_stream.sent_bytes[1] & 0x80, 0)
        client, client_stream = client_side_connection()
        client.send_text("hello")
        self.assertEqual(client_stream.sent_bytes[1] & 0x80, 0x80)

    def test_a_ping_is_answered_with_a_pong_of_the_same_payload(self) -> None:
        connection, stream = server_side_connection()
        stream.feed(support.render_frame(Opcode.PING, b"are you there", mask=True))
        stream.feed(support.render_frame(Opcode.TEXT, b"after ping", mask=True))
        message = connection.receive()
        self.assertEqual(message.text, "after ping")
        frames = written_frames(stream.sent_bytes, require_mask=False)
        self.assertEqual(frames, [(Opcode.PONG, b"are you there")])

    def test_a_pong_is_reported_to_the_caller(self) -> None:
        connection, stream = client_side_connection()
        stream.feed(support.render_frame(Opcode.PONG, b"pong-body", mask=False))
        message = connection.receive()
        self.assertEqual(message.opcode, Opcode.PONG)
        self.assertEqual(message.data, b"pong-body")

    def test_fragmented_text_is_assembled_before_the_caller_sees_it(self) -> None:
        connection, stream = server_side_connection()
        stream.feed(support.render_frame(Opcode.TEXT, b'{"seq', mask=True, fin=False))
        stream.feed(support.render_frame(Opcode.CONTINUATION, b'_no":1}', mask=True, fin=True))
        message = connection.receive()
        self.assertEqual(message.text, '{"seq_no":1}')
        self.assertEqual(message.opcode, Opcode.TEXT)

    def test_a_fragmented_message_over_the_cap_is_refused(self) -> None:
        connection, stream = server_side_connection()
        chunk = b"z" * (MAX_FRAME_BYTES // 2 + 1)
        stream.feed(support.render_frame(Opcode.TEXT, chunk, mask=True, fin=False))
        stream.feed(support.render_frame(Opcode.CONTINUATION, chunk, mask=True, fin=True))
        with self.assertRaises(FrameTooLarge):
            connection.receive()

    def test_a_continuation_with_no_message_in_progress_is_a_protocol_error(self) -> None:
        connection, stream = server_side_connection()
        stream.feed(support.render_frame(Opcode.CONTINUATION, b"orphan", mask=True))
        with self.assertRaises(TransportClosed):
            connection.receive()

    def test_a_new_data_frame_mid_message_is_a_protocol_error(self) -> None:
        connection, stream = server_side_connection()
        stream.feed(support.render_frame(Opcode.TEXT, b"part", mask=True, fin=False))
        stream.feed(support.render_frame(Opcode.TEXT, b"again", mask=True, fin=True))
        with self.assertRaises(TransportClosed):
            connection.receive()

    def test_invalid_utf8_in_a_text_frame_is_reported_as_invalid_payload(self) -> None:
        connection, stream = server_side_connection()
        stream.feed(support.render_frame(Opcode.TEXT, b"\xff\xfe", mask=True))
        with self.assertRaises(TransportClosed) as caught:
            connection.receive()
        self.assertEqual(caught.exception.context["ws_close_code"], int(CloseCode.INVALID_PAYLOAD))

    def test_a_binary_message_is_handed_over_unchanged(self) -> None:
        connection, stream = server_side_connection()
        stream.feed(support.render_frame(Opcode.BINARY, b"\x00\x01\x02", mask=True))
        message = connection.receive()
        self.assertEqual(message.data, b"\x00\x01\x02")
        self.assertIsNone(message.text)

    def test_receiving_eof_raises_rather_than_returning_a_partial_message(self) -> None:
        connection, _ = server_side_connection()
        with self.assertRaises(TransportClosed):
            connection.receive()

    def test_the_close_handshake_echoes_the_peers_status(self) -> None:
        connection, stream = server_side_connection()
        payload = encode_close_payload(CloseCode.POLICY_VIOLATION, "no")
        stream.feed(support.render_frame(Opcode.CLOSE, payload, mask=True))
        message = connection.receive()
        self.assertEqual(message.close_code, int(CloseCode.POLICY_VIOLATION))
        self.assertTrue(connection.is_closed)
        frames = written_frames(stream.sent_bytes, require_mask=False)
        self.assertEqual(len(frames), 1)
        self.assertEqual(frames[0][0], Opcode.CLOSE)
        self.assertEqual(decode_close_payload(frames[0][1])[0], int(CloseCode.POLICY_VIOLATION))

    def test_a_close_with_no_status_is_answered_with_1000(self) -> None:
        connection, stream = server_side_connection()
        stream.feed(support.render_frame(Opcode.CLOSE, b"", mask=True))
        message = connection.receive()
        self.assertEqual(message.close_code, int(CloseCode.NO_STATUS_RECEIVED))
        frames = written_frames(stream.sent_bytes, require_mask=False)
        self.assertEqual(decode_close_payload(frames[0][1])[0], int(CloseCode.NORMAL))

    def test_sending_after_close_is_refused(self) -> None:
        connection, stream = server_side_connection()
        connection.close(CloseCode.NORMAL)
        with self.assertRaises(TransportClosed):
            connection.send_text("late")
        self.assertTrue(stream.closed)

    def test_closing_twice_sends_one_close_frame(self) -> None:
        connection, stream = server_side_connection()
        connection.close(CloseCode.NORMAL)
        connection.close(CloseCode.NORMAL)
        self.assertEqual(len(written_frames(stream.sent_bytes, require_mask=False)), 1)

    def test_counters_track_frames_and_bytes(self) -> None:
        connection, stream = server_side_connection()
        stream.feed(support.render_frame(Opcode.TEXT, b"hello", mask=True))
        connection.receive()
        connection.send_text("world")
        self.assertEqual(connection.frames_in, 1)
        self.assertEqual(connection.frames_out, 1)
        self.assertEqual(connection.bytes_in, 5)

    def test_an_outbound_message_over_the_cap_is_refused_before_sending(self) -> None:
        connection, stream = server_side_connection()
        with self.assertRaises(FrameTooLarge):
            connection.send_bytes(b"z" * (MAX_FRAME_BYTES + 1))
        self.assertEqual(stream.sent_bytes, b"")

    def test_a_ping_over_125_bytes_is_refused(self) -> None:
        connection, _ = server_side_connection()
        with self.assertRaises(ValueError):
            connection.send_ping(b"z" * 126)


class ServerHandshakeTest(unittest.TestCase):
    def make_server(self, **overrides: object) -> WebSocketServer:
        kwargs: dict[str, object] = {"token": TOKEN}
        kwargs.update(overrides)
        return WebSocketServer(**kwargs)  # type: ignore[arg-type]

    def drive(self, server: WebSocketServer, *frames: bytes, path: str = "/?token=" + TOKEN) -> support.FakeStream:
        stream = support.FakeStream(support.handshake_request_bytes(path) + b"".join(frames))
        server.handle_stream(stream, peer_name="unit-test")
        return stream

    def test_a_valid_token_opens_the_connection_and_serves_frames(self) -> None:
        received: list[str] = []
        server = self.make_server(
            on_message=lambda connection, message: received.append(message.text or "")
        )
        stream = self.drive(
            server,
            support.render_frame(Opcode.TEXT, b'{"hello":1}', mask=True),
            support.render_frame(Opcode.CLOSE, encode_close_payload(CloseCode.NORMAL, ""), mask=True),
        )
        self.assertEqual(received, ['{"hello":1}'])
        self.assertIn(b"HTTP/1.1 101 Switching Protocols", stream.sent_bytes)
        self.assertGreaterEqual(server.metrics["connections_accepted"], 1)
        self.assertEqual(server.metrics["connections_closed"], 1)
        self.assertEqual(server.connection_count, 0)

    def test_the_open_and_close_callbacks_fire_once_each(self) -> None:
        opened: list[str] = []
        closed: list[tuple[str, int | None]] = []
        server = self.make_server(
            on_open=lambda connection: opened.append(connection.id),
            on_close=lambda connection, code: closed.append((connection.id, code)),
        )
        self.drive(server, support.render_frame(Opcode.CLOSE, encode_close_payload(CloseCode.NORMAL, ""), mask=True))
        self.assertEqual(len(opened), 1)
        self.assertEqual(len(closed), 1)
        self.assertEqual(closed[0][1], int(CloseCode.NORMAL))

    def test_a_missing_token_gets_401_and_no_connection(self) -> None:
        server = self.make_server()
        stream = support.FakeStream(support.handshake_request_bytes("/"))
        server.handle_stream(stream)
        self.assertIn(b"HTTP/1.1 401 Unauthorized", stream.sent_bytes)
        self.assertEqual(server.metrics["connections_refused_no_token"], 1)

    def test_a_wrong_token_gets_401(self) -> None:
        server = self.make_server()
        stream = self.drive(server, path="/?token=something-else-123456")
        self.assertIn(b"401", stream.sent_bytes)

    def test_an_authorization_header_token_is_accepted(self) -> None:
        server = self.make_server()
        stream = support.FakeStream(
            support.handshake_request_bytes("/", authorization="Bearer " + TOKEN)
            + support.render_frame(Opcode.CLOSE, b"", mask=True)
        )
        server.handle_stream(stream)
        self.assertIn(b"101", stream.sent_bytes)

    def test_a_non_websocket_upgrade_gets_400(self) -> None:
        server = self.make_server()
        stream = support.FakeStream(support.handshake_request_bytes("/", upgrade="hippie"))
        server.handle_stream(stream)
        self.assertIn(b"HTTP/1.1 400 Bad Request", stream.sent_bytes)

    def test_a_wrong_version_gets_426(self) -> None:
        server = self.make_server()
        stream = support.FakeStream(support.handshake_request_bytes("/", version="7"))
        server.handle_stream(stream)
        self.assertIn(b"HTTP/1.1 426", stream.sent_bytes)

    def test_a_frame_above_the_cap_closes_the_connection_with_1009(self) -> None:
        seen: list[str] = []
        server = self.make_server(on_message=lambda connection, message: seen.append("handled"))
        oversized = bytearray([0x81, 0xFF])
        oversized.extend((MAX_FRAME_BYTES + 1000).to_bytes(8, "big"))
        oversized.extend(b"\x00\x00\x00\x00")
        stream = self.drive(server, bytes(oversized))
        frames = written_frames(stream.sent_bytes[stream.sent_bytes.index(b"\r\n\r\n") + 4:], require_mask=False)
        self.assertEqual(frames[0][0], Opcode.CLOSE)
        self.assertEqual(decode_close_payload(frames[0][1])[0], int(CloseCode.MESSAGE_TOO_BIG))
        self.assertEqual(seen, [])
        self.assertEqual(server.metrics["frames_too_large"], 1)

    def test_an_unmasked_frame_after_the_handshake_closes_with_1002(self) -> None:
        server = self.make_server()
        stream = self.drive(server, support.render_frame(Opcode.TEXT, b"lazy", mask=False))
        frames = written_frames(stream.sent_bytes[stream.sent_bytes.index(b"\r\n\r\n") + 4:], require_mask=False)
        self.assertEqual(decode_close_payload(frames[0][1])[0], int(CloseCode.PROTOCOL_ERROR))
        self.assertEqual(server.metrics["protocol_errors"], 1)

    def test_a_close_frame_ends_the_serving_loop_and_unregisters(self) -> None:
        server = self.make_server()
        self.drive(server, support.render_frame(Opcode.CLOSE, encode_close_payload(CloseCode.GOING_AWAY, "bye"), mask=True))
        self.assertEqual(server.connection_count, 0)

    def test_a_handler_exception_closes_the_connection_with_1011(self) -> None:
        def explode(connection: WebSocketConnection, message: object) -> None:
            raise RuntimeError("handler is broken")

        server = self.make_server(on_message=explode)
        stream = self.drive(server, support.render_frame(Opcode.TEXT, b"{}", mask=True))
        frames = written_frames(stream.sent_bytes[stream.sent_bytes.index(b"\r\n\r\n") + 4:], require_mask=False)
        self.assertEqual(decode_close_payload(frames[0][1])[0], int(CloseCode.INTERNAL_ERROR))
        self.assertEqual(server.metrics["handler_errors"], 1)

    def test_the_token_length_floor_is_enforced_at_construction(self) -> None:
        with self.assertRaises(ValueError):
            WebSocketServer(token="short")

    def test_construction_refuses_a_bind_address_that_is_not_loopback(self) -> None:
        for host in ("0.0.0.0", "", "::", "192.168.1.10", LOOPBACK_HOST + "x"):
            with self.assertRaises(ValueError):
                WebSocketServer(token=TOKEN, host=host)

    def test_construction_refuses_a_connection_ceiling_outside_the_documented_one(self) -> None:
        with self.assertRaises(ValueError):
            WebSocketServer(token=TOKEN, max_connections=MAX_CONNECTIONS + 1)
        with self.assertRaises(ValueError):
            WebSocketServer(token=TOKEN, max_connections=0)

    def test_a_stopped_server_refuses_to_listen_again(self) -> None:
        server = self.make_server()
        server.stop()
        with self.assertRaises(RuntimeError):
            server.start()

    def test_max_connections_is_the_documented_number(self) -> None:
        self.assertEqual(MAX_CONNECTIONS, 4)


class ClientHandshakeTest(unittest.TestCase):
    def test_a_server_that_never_answers_is_reported_as_a_closed_transport(self) -> None:
        client = WebSocketClient(port=8777, token=TOKEN, stream_factory=lambda host, port: support.FakeStream())
        with self.assertRaises(TransportClosed):
            client.connect()

    def test_a_server_that_completes_the_handshake_is_accepted(self) -> None:
        stream = support.FakeStream(auto_accept_handshake=True)
        client = WebSocketClient(port=8777, token=TOKEN, stream_factory=lambda host, port: stream)
        connection = client.connect()
        self.assertEqual(connection.role, Role.CLIENT)
        self.assertIn(b"Sec-WebSocket-Key:", stream.sent_bytes)
        self.assertIn(b"token=" + TOKEN.encode("ascii"), stream.sent_bytes)
        client.close()
        self.assertTrue(stream.closed)

    def test_a_mismatched_accept_key_is_rejected(self) -> None:
        class WrongKeyStream(support.FakeStream):  # auto_accept_handshake drives the override below
            def _maybe_respond(self) -> None:
                self._responded = True
                self.feed(
                    b"HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\n"
                    b"Connection: Upgrade\r\nSec-WebSocket-Accept: not-the-right-answer=\r\n\r\n"
                )

        client = WebSocketClient(
            port=8777,
            token=TOKEN,
            stream_factory=lambda host, port: WrongKeyStream(auto_accept_handshake=True),
        )
        with self.assertRaises(HandshakeRejected) as caught:
            client.connect()
        self.assertIn("Sec-WebSocket-Accept", caught.exception.message)

    def test_a_401_response_is_reported_as_a_refusal(self) -> None:
        class RejectingStream(support.FakeStream):
            def _maybe_respond(self) -> None:
                self._responded = True
                self.feed(b"HTTP/1.1 401 Unauthorized\r\nConnection: close\r\n\r\n")

        client = WebSocketClient(
            port=8777,
            token=TOKEN,
            stream_factory=lambda host, port: RejectingStream(auto_accept_handshake=True),
        )
        with self.assertRaises(HandshakeRejected) as caught:
            client.connect()
        self.assertEqual(caught.exception.http_status, 401)

    def test_the_client_refuses_a_host_that_is_not_loopback(self) -> None:
        with self.assertRaises(ValueError):
            WebSocketClient(port=8777, token=TOKEN, host="0.0.0.0")

    def test_connection_before_connect_is_a_typed_error(self) -> None:
        client = WebSocketClient(port=8777, token=TOKEN, stream_factory=lambda host, port: support.FakeStream())
        with self.assertRaises(TransportClosed):
            _ = client.connection


class LoopbackIntegrationTest(unittest.TestCase):
    """Real sockets, `127.0.0.1` only, ephemeral ports, short timeouts."""

    def setUp(self) -> None:
        try:
            probe = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            probe.bind((LOOPBACK_HOST, 0))
            probe.listen(1)
            probe.close()
        except OSError as exc:
            self.skipTest("loopback sockets are unavailable in this environment: " + type(exc).__name__)

    def test_a_client_and_server_exchange_a_message_over_loopback(self) -> None:
        replies: list[str] = []

        def echo(connection: WebSocketConnection, message: ReceivedMessage) -> None:
            text = message.text
            if text is None:
                return
            replies.append(text)
            connection.send_text("ack:" + text)

        server = WebSocketServer(token=TOKEN, port=0, on_message=echo)
        port = server.start()
        client = WebSocketClient(port=port, token=TOKEN)
        try:
            connection = client.connect()
            connection.send_text('{"seq_no":1}')
            answer = connection.receive()
            self.assertEqual(answer.text, 'ack:{"seq_no":1}')
            self.assertEqual(replies, ['{"seq_no":1}'])
            self.assertEqual(server.connection_count, 1)
        finally:
            client.close()
            server.stop()
        self.assertFalse(server.is_running)
        self.assertEqual(server.connection_count, 0)

    def test_a_client_without_a_token_is_refused_on_a_real_socket(self) -> None:
        server = WebSocketServer(token=TOKEN, port=0)
        port = server.start()
        raw = None
        try:
            raw = socket.create_connection((LOOPBACK_HOST, port), timeout=2.0)
            raw.settimeout(2.0)
            raw.sendall(support.handshake_request_bytes("/"))
            collected = bytearray()
            while b"\r\n\r\n" not in collected:
                chunk = raw.recv(512)
                if not chunk:
                    break
                collected.extend(chunk)
            self.assertIn(b"401 Unauthorized", bytes(collected))
            self.assertEqual(server.metrics["connections_refused_no_token"], 1)
        finally:
            if raw is not None:
                raw.close()
            server.stop()

    def test_the_connection_ceiling_answers_503_on_a_real_socket(self) -> None:
        server = WebSocketServer(token=TOKEN, port=0)
        port = server.start()
        held: list[socket.socket] = []
        try:
            for _ in range(MAX_CONNECTIONS):
                raw = socket.create_connection((LOOPBACK_HOST, port), timeout=2.0)
                raw.settimeout(2.0)
                raw.sendall(support.handshake_request_bytes("/?token=" + TOKEN))
                head = bytearray()
                while b"\r\n\r\n" not in head:
                    chunk = raw.recv(512)
                    if not chunk:
                        break
                    head.extend(chunk)
                self.assertIn(b"101", bytes(head))
                held.append(raw)
            extra = socket.create_connection((LOOPBACK_HOST, port), timeout=2.0)
            extra.settimeout(2.0)
            try:
                extra.sendall(support.handshake_request_bytes("/?token=" + TOKEN))
                answer = bytearray()
                while b"\r\n\r\n" not in answer:
                    chunk = extra.recv(512)
                    if not chunk:
                        break
                    answer.extend(chunk)
                self.assertIn(b"503", bytes(answer))
                self.assertEqual(server.metrics["connections_refused_limit"], 1)
            finally:
                extra.close()
        finally:
            for raw in held:
                raw.close()
            server.stop()

    def test_an_oversized_frame_from_a_real_client_closes_with_1009(self) -> None:
        received: list[str] = []
        server = WebSocketServer(token=TOKEN, port=0, on_message=lambda c, m: received.append(m.text or ""))
        port = server.start()
        raw = None
        try:
            raw = socket.create_connection((LOOPBACK_HOST, port), timeout=2.0)
            raw.settimeout(2.0)
            raw.sendall(support.handshake_request_bytes("/?token=" + TOKEN))
            head = bytearray()
            while b"\r\n\r\n" not in head:
                head.extend(raw.recv(512))
            oversized = bytearray([0x82, 0xFF])
            oversized.extend((MAX_FRAME_BYTES + 10).to_bytes(8, "big"))
            oversized.extend(b"\x00\x00\x00\x00")
            raw.sendall(bytes(oversized))
            answer = raw.recv(64)
            frames = written_frames(answer, require_mask=False)
            self.assertEqual(decode_close_payload(frames[0][1])[0], int(CloseCode.MESSAGE_TOO_BIG))
            self.assertEqual(received, [])
        finally:
            if raw is not None:
                raw.close()
            server.stop()


if __name__ == "__main__":
    unittest.main()
