"""A RFC 6455 WebSocket server on the loopback interface, written against the standard library.

Why hand-rolled
---------------
The brief pins this package to the Python standard library, which rules out `websockets`,
`fastapi` and every other asyncio/uvicorn stack. RFC 6455's wire format is small enough to
implement exactly: the opening handshake, the frame header, client-to-server masking, the
text/binary/close/ping/pong opcodes, and the two error codes this bridge actually needs (1009 for an
oversized frame, 1002 for a protocol violation). What is deliberately *not* implemented is
documented at the bottom of this docstring and repeated in the README: no extensions, no
subprotocols, no RSV bits, no fragmented control frames, no TLS (`wss`) - a TLS listener would need
a certificate and would be the beginning of a network boundary this bridge is not allowed to have.

Safety properties, in the order the code enforces them
------------------------------------------------------
1. **Bind.** `LOOPBACK_HOST` is the only acceptable address. A host argument of `0.0.0.0`,
   `""`, a concrete LAN address or `::` raises at construction. There is no way to ask this server
   to listen beyond the machine.
2. **Token.** Every connection must present a token, as `?token=` on the request path or in the
   `Authorization` header. Comparison is constant-time (`hmac.compare_digest`). No token, or a
   different one, gets HTTP 401 and no WebSocket is created. The token is not logged.
3. **Size.** A frame header claiming more than `MAX_FRAME_BYTES` fails the connection with status
   1009 before any payload is buffered. Fragmented messages are counted cumulatively for the same
   reason.
4. **Masking.** Client frames must be masked; unmasked frames are a protocol error (1002). Server
   frames are never masked, per the RFC.
5. **Concurrency ceiling.** `MAX_CONNECTIONS`. Above it, 503. The harness needs one agent plus at
   most a couple of observers; a ceiling that is not there turns a leaky client into a thread leak.
6. **Teardown.** Every accepted socket is owned by exactly one connection object, tracked in the
   server registry, closed on stop, and joined. No socket outlives `stop()`.

The socket layer is a duck-typed read/write pair, so the frame and handshake code is testable
against an in-memory byte stream. The loopback integration test in `tests/` exercises the real
socket path.
"""

from __future__ import annotations

import base64
import hashlib
import hmac
import os
import secrets
import socket
import sys
import threading
import urllib.parse
from dataclasses import dataclass, field
from enum import IntEnum
from typing import Callable, Protocol

from .envelope import MAX_FRAME_BYTES
from .errors import (
    BridgeErrorCode,
    FrameTooLarge,
    HandshakeRejected,
    TransportClosed,
)

#: The only bind address this module accepts. See the safety properties above.
LOOPBACK_HOST = "127.0.0.1"

#: RFC 6455 section 1.3 magic value, concatenated with the client key before hashing.
WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

#: The version this bridge speaks. Anything else is refused with 426.
WEBSOCKET_VERSION = "13"

#: Header budget for one opening handshake. A handshake larger than this is not a client we
#: misfavour; it is a client trying to make us hold a buffer.
MAX_HANDSHAKE_BYTES = 8 * 1024

#: Documented ceiling on simultaneous connections: the agent, one observer, one spare. Raising it
#: is a deliberate edit with a reason, not a knob a caller turns.
MAX_CONNECTIONS = 4

#: Short-lived accept poll so `stop()` does not block in `accept()`.
ACCEPT_POLL_S = 0.2

#: Per-connection read poll, so a stalled peer cannot outlive `stop()`.
READ_POLL_S = 0.25

MIN_TOKEN_LENGTH = 16


class Opcode(IntEnum):
    """RFC 6455 section 5.2 opcodes."""

    CONTINUATION = 0x0
    TEXT = 0x1
    BINARY = 0x2
    CLOSE = 0x8
    PING = 0x9
    PONG = 0xA


class CloseCode(IntEnum):
    """The close codes this bridge sends or accepts."""

    NORMAL = 1000
    GOING_AWAY = 1001
    PROTOCOL_ERROR = 1002
    UNSUPPORTED_DATA = 1003
    NO_STATUS_RECEIVED = 1005
    ABNORMAL_CLOSURE = 1006
    INVALID_PAYLOAD = 1007
    POLICY_VIOLATION = 1008
    MESSAGE_TOO_BIG = 1009
    INTERNAL_ERROR = 1011


#: Status codes a close frame may legitimately carry.
_VALID_CLOSE_CODES: frozenset[int] = frozenset(
    {1000, 1001, 1002, 1003, 1007, 1008, 1009, 1010, 1011, 1012, 1013, 1014, 1015}
) | frozenset(range(3000, 5000))

_CONTROL_OPCODES: frozenset[Opcode] = frozenset({Opcode.CLOSE, Opcode.PING, Opcode.PONG})


class Role(IntEnum):
    SERVER = 1
    CLIENT = 2


class ByteStream(Protocol):
    """The minimum socket surface this module needs: `recv`, `sendall`, `shutdown`, `close`."""

    def recv(self, bufsize: int) -> bytes: ...

    def sendall(self, data: bytes) -> None: ...

    def shutdown(self, how: int) -> None: ...

    def close(self) -> None: ...


@dataclass(frozen=True)
class Frame:
    """One decoded WebSocket frame, masked or unmasked as it arrived."""

    fin: bool
    opcode: Opcode
    payload: bytes


@dataclass(frozen=True)
class ReceivedMessage:
    """A completed application message, or a control event.

    Exactly one of `text` / `data` is set for TEXT/BINARY messages; a control frame sets neither.
    `close_code` is set only on a close message, and is `None` when the peer sent an empty close
    frame (status 1005, "no status received").
    """

    opcode: Opcode
    text: str | None = None
    data: bytes | None = None
    close_code: int | None = None
    close_reason: str = ""
    connection_id: str = ""


@dataclass(frozen=True)
class HandshakeRequest:
    """A parsed opening request."""

    method: str
    path: str
    query: dict[str, list[str]]
    headers: dict[str, str] = field(default_factory=dict)

    def header(self, name: str) -> str | None:
        return self.headers.get(name.lower())

    @property
    def websocket_key(self) -> str | None:
        return self.header("sec-websocket-key")

    @property
    def version(self) -> str | None:
        return self.header("sec-websocket-version")

    def token_from_query(self) -> str | None:
        values = self.query.get("token")
        if not values:
            return None
        return values[0]

    def token_from_authorization(self) -> str | None:
        """Accept `Bearer <token>`, `token <token>` or a bare value.

        Three shapes because ADB-side and browser-side clients differ in what they can set; the
        comparison is still against one agreed secret, so this is parsing latitude, not authority.
        """
        raw = self.header("authorization")
        if raw is None:
            return None
        value = raw.strip()
        if not value:
            return None
        parts = value.split(None, 1)
        if len(parts) == 2 and parts[0].lower() in ("bearer", "token"):
            return parts[1].strip() or None
        return value


def compute_accept_key(sec_websocket_key: str) -> str:
    """The RFC 6455 `Sec-WebSocket-Accept` value for a client key.

    `base64(SHA-1(key + GUID))`, with the key taken verbatim after trimming surrounding whitespace.
    """
    if not isinstance(sec_websocket_key, str) or not sec_websocket_key.strip():
        raise ValueError("compute_accept_key requires a non-blank Sec-WebSocket-Key")
    material = (sec_websocket_key.strip() + WEBSOCKET_GUID).encode("ascii")
    return base64.b64encode(hashlib.sha1(material).digest()).decode("ascii")


def parse_handshake(data: bytes) -> HandshakeRequest:
    """Parse an opening handshake into method, path, query and lower-cased headers.

    Raises:
        HandshakeRejected(400): not bytes, no request line, a malformed request line, a header line
            with no colon, or a non-ASCII header. Each names what it found, because a rejected
            handshake that cannot explain itself is undebuggable.
    """
    if not isinstance(data, bytes):
        raise _rejected("handshake input must be bytes", 400)
    try:
        text = data.decode("ascii")
    except UnicodeDecodeError as exc:
        raise _rejected("handshake contains non-ASCII bytes", 400) from exc
    if text.endswith("\r\n\r\n"):
        text = text[:-4]
    lines = text.split("\r\n")
    if not lines or not lines[0]:
        raise _rejected("handshake has no request line", 400)
    request_parts = lines[0].split(" ")
    if len(request_parts) != 3 or not request_parts[2].startswith("HTTP/"):
        raise _rejected(
            "request line is not `<METHOD> <path> HTTP/1.1`: " + repr(lines[0]),
            400,
        )
    method, target, _version = request_parts
    split = urllib.parse.urlsplit(target)
    query = urllib.parse.parse_qs(split.query, keep_blank_values=True)
    headers: dict[str, str] = {}
    for line in lines[1:]:
        if not line:
            continue
        if ":" not in line:
            raise _rejected("header line has no colon: " + repr(line), 400)
        name, _, value = line.partition(":")
        headers[name.strip().lower()] = value.strip()
    return HandshakeRequest(method=method, path=split.path, query=query, headers=headers)


def extract_request_token(request: HandshakeRequest) -> str | None:
    """The token a client offered, from the query parameter or the Authorization header.

    Query first: a browser-driven client cannot set headers on a `WebSocket` constructor, and the
    query parameter is the only shape available to it. Both are checked against the same secret.
    """
    return request.token_from_query() or request.token_from_authorization()


def token_matches(offered: str | None, expected: str) -> bool:
    """Constant-time comparison against the agreed token. `None` never matches."""
    if offered is None:
        return False
    return hmac.compare_digest(offered.encode("utf-8"), expected.encode("utf-8"))


def validate_upgrade_headers(request: HandshakeRequest) -> None:
    """Check the four headers that make this a WebSocket upgrade.

    Raises:
        HandshakeRejected: 400 for a missing/wrong `Upgrade` or `Connection`, or a missing key;
            426 for a version that is not 13.
    """
    if request.method != "GET":
        raise _rejected("only GET may open a WebSocket; got " + repr(request.method), 400)
    upgrade = request.header("upgrade")
    if upgrade is None or upgrade.strip().lower() != "websocket":
        raise _rejected("Upgrade header is not `websocket`", 400)
    connection = request.header("connection")
    if connection is None or "upgrade" not in [token.strip().lower() for token in connection.split(",")]:
        raise _rejected("Connection header does not include the `Upgrade` token", 400)
    if request.websocket_key is None or not request.websocket_key.strip():
        raise _rejected("Sec-WebSocket-Key is absent", 400)
    if request.version != WEBSOCKET_VERSION:
        raise HandshakeRejected(
            "Sec-WebSocket-Version is " + repr(request.version) + "; this server speaks "
            + WEBSOCKET_VERSION,
            code=BridgeErrorCode.WS_VERSION_UNSUPPORTED,
            http_status=426,
            reason="unsupported version",
        )


def build_accept_response(request: HandshakeRequest) -> bytes:
    """The 101 response, with the accept key derived from the client's key."""
    key = request.websocket_key
    if key is None:
        raise _rejected("cannot accept a handshake with no key", 400)
    accept = compute_accept_key(key)
    return (
        "HTTP/1.1 101 Switching Protocols\r\n"
        "Upgrade: websocket\r\n"
        "Connection: Upgrade\r\n"
        "Sec-WebSocket-Accept: " + accept + "\r\n"
        "\r\n"
    ).encode("ascii")


#: The reason phrases this server writes, so a refusal reads as the standard status it is.
HTTP_REASON_PHRASES: dict[int, str] = {
    400: "Bad Request",
    401: "Unauthorized",
    426: "Upgrade Required",
    503: "Service Unavailable",
}


def reject_reason(status: int) -> str:
    return HTTP_REASON_PHRASES.get(status, "Rejected")


def build_reject_response(status: int, reason: str = "", extra_headers: dict[str, str] | None = None) -> bytes:
    """A failure response with an empty body. No stack, no echo of the offered token."""
    lines = [
        "HTTP/1.1 " + str(status) + " " + (reason.strip() or reject_reason(status)),
        "Connection: close",
        "Content-Length: 0",
    ]
    for name, value in (extra_headers or {}).items():
        lines.append(name + ": " + value)
    return ("\r\n".join(lines) + "\r\n\r\n").encode("ascii")


def _rejected(message: str, status: int) -> HandshakeRejected:
    return HandshakeRejected(
        message,
        code=BridgeErrorCode.WS_HANDSHAKE_REJECTED,
        http_status=status,
        reason=message,
    )


# ---------------------------------------------------------------- the frame layer


def apply_mask(mask: bytes, data: bytes) -> bytes:
    """XOR `data` against a repeating 4-byte mask, as RFC 6455 section 5.3 defines it.

    Implemented as one big-integer XOR rather than a per-byte Python loop, because masking 512 KiB
    one byte at a time is the difference between a frame and a stall.
    """
    if len(mask) != 4:
        raise ValueError("a WebSocket mask is exactly 4 bytes")
    if not data:
        return b""
    repeated = (mask * ((len(data) + 3) // 4))[: len(data)]
    value = int.from_bytes(data, "big") ^ int.from_bytes(repeated, "big")
    return value.to_bytes(len(data), "big")


def _recv_exactly(stream: ByteStream, count: int) -> bytes:
    """Read until `count` bytes have arrived or the peer stops.

    Raises:
        TransportClosed: EOF before the count. A half frame is not a message, and returning the
            partial bytes would let a truncated payload be decoded as if it were complete.
    """
    if count == 0:
        return b""
    collected = bytearray()
    while len(collected) < count:
        try:
            chunk = stream.recv(count - len(collected))
        except (BlockingIOError, TimeoutError):
            continue
        except OSError as exc:
            raise TransportClosed(
                "socket read failed: " + type(exc).__name__,
                context={"error": type(exc).__name__},
            ) from exc
        if not chunk:
            raise TransportClosed(
                "peer closed the connection after " + str(len(collected)) + " of " + str(count)
                + " bytes",
                context={"received": len(collected), "wanted": count},
            )
        collected.extend(chunk)
    return bytes(collected)


def read_frame(
    stream: ByteStream,
    *,
    require_mask: bool,
    max_payload_bytes: int = MAX_FRAME_BYTES,
) -> Frame:
    """Read one frame header and its payload.

    Invariants enforced here, each with the RFC's own consequence:
        * RSV1/RSV2/RSV3 must be clear - no extensions are negotiated, so a set bit means the peer
          is speaking something this server did not agree to.
        * A length of 126 must not be encoded with a redundant value, and 127 must use the full
          64-bit form with the high bit clear (a length above 2^63 is not a message we can hold).
        * Client frames must be masked when `require_mask` is set; a frame from a server must not be.
        * Control frames must be final and at most 125 bytes.

    Raises:
        FrameTooLarge: the declared payload exceeds `max_payload_bytes`. Nothing is read after this -
            the caller closes with 1009 - because buffering the promise is the attack.
        TransportClosed: EOF mid-frame (`TRANSPORT_CLOSED`), or a protocol violation
            (`WS_PROTOCOL_ERROR`, whose context carries the close code the caller must send).
    """
    header = _recv_exactly(stream, 2)
    fin = (header[0] & 0x80) != 0
    rsv = header[0] & 0x70
    opcode_value = header[0] & 0x0F
    masked = (header[1] & 0x80) != 0
    length = header[1] & 0x7F
    if rsv:
        raise _protocol_error("RSV bits are set but no extension was negotiated", CloseCode.PROTOCOL_ERROR)
    if masked != require_mask:
        raise _protocol_error(
            "frame mask bit " + ("set" if masked else "clear") + "; a "
            + ("client" if require_mask else "server") + " frame must be "
            + ("masked" if require_mask else "unmasked"),
            CloseCode.PROTOCOL_ERROR,
        )
    try:
        opcode = Opcode(opcode_value)
    except ValueError as exc:
        raise _protocol_error(
            "unknown opcode 0x" + format(opcode_value, "02X"), CloseCode.PROTOCOL_ERROR
        ) from exc
    if length == 126:
        extended = _recv_exactly(stream, 2)
        length = int.from_bytes(extended, "big")
        if length < 126:
            raise _protocol_error("16-bit length used redundantly", CloseCode.PROTOCOL_ERROR)
    elif length == 127:
        extended = _recv_exactly(stream, 8)
        if extended[0] & 0x80:
            raise _protocol_error("64-bit length has the high bit set", CloseCode.PROTOCOL_ERROR)
        length = int.from_bytes(extended, "big")
        if length <= 0xFFFF:
            raise _protocol_error("64-bit length used redundantly", CloseCode.PROTOCOL_ERROR)
    if length > max_payload_bytes:
        raise FrameTooLarge(
            "frame header declares " + str(length) + " bytes, above the " + str(max_payload_bytes)
            + " byte budget; the connection is failed rather than buffered",
            declared_bytes=length,
            cap_bytes=max_payload_bytes,
            context={"opcode": opcode.name},
        )
    mask_key = _recv_exactly(stream, 4) if masked else b""
    payload = _recv_exactly(stream, length)
    if masked:
        payload = apply_mask(mask_key, payload)
    if opcode in _CONTROL_OPCODES:
        if not fin:
            raise _protocol_error("control frame is fragmented", CloseCode.PROTOCOL_ERROR)
        if length > 125:
            raise _protocol_error("control frame payload exceeds 125 bytes", CloseCode.PROTOCOL_ERROR)
    return Frame(fin=fin, opcode=opcode, payload=payload)


def write_frame(
    stream: ByteStream,
    opcode: Opcode,
    payload: bytes,
    *,
    mask: bool,
    fin: bool = True,
    max_payload_bytes: int = MAX_FRAME_BYTES,
) -> int:
    """Write one frame; returns the number of bytes handed to the transport.

    `mask=True` is the client side of RFC 6455 section 5.1 ("the client must mask"). The mask key
    comes from `secrets`, not `random`: a predictable masking key is a fingerprinting and
    cache-poisoning surface, and there is no reason to use anything weaker here.
    """
    size = len(payload)
    if size > max_payload_bytes:
        raise FrameTooLarge(
            "refusing to send a " + str(size) + " byte frame above the " + str(max_payload_bytes)
            + " byte budget",
            declared_bytes=size,
            cap_bytes=max_payload_bytes,
            context={"opcode": opcode.name, "direction": "outbound"},
        )
    mask_bit = 0x80 if mask else 0x00
    header = bytearray([(0x80 if fin else 0x00) | int(opcode)])
    if size < 126:
        header.append(mask_bit | size)
    elif size <= 0xFFFF:
        header.append(mask_bit | 126)
        header.extend(size.to_bytes(2, "big"))
    else:
        header.append(mask_bit | 127)
        header.extend(size.to_bytes(8, "big"))
    body = payload
    if mask:
        mask_key = secrets.token_bytes(4)
        body = mask_key + apply_mask(mask_key, payload)
    stream.sendall(bytes(header) + body)
    return len(header) + len(body)


def _protocol_error(message: str, close_code: CloseCode) -> TransportClosed:
    return TransportClosed(
        "websocket protocol violation: " + message,
        code=BridgeErrorCode.WS_PROTOCOL_ERROR,
        context={"ws_close_code": int(close_code)},
    )


def encode_close_payload(code: CloseCode | int, reason: str) -> bytes:
    """`2-byte status + UTF-8 reason`, the close frame's payload form."""
    value = int(code)
    if value not in _VALID_CLOSE_CODES:
        raise ValueError("close status " + str(value) + " is not a code this connection may send")
    reason_bytes = reason.encode("utf-8")
    if len(reason_bytes) > 123:
        raise ValueError("a close reason must fit in 123 bytes alongside its status")
    return value.to_bytes(2, "big") + reason_bytes


def decode_close_payload(payload: bytes) -> tuple[int | None, str]:
    """Split a close payload into `(status, reason)`; an empty payload is status 1005.

    Raises:
        TransportClosed(1007 / 1002): a one-byte payload, an unregistered status, or a reason that
            is not UTF-8. The RFC requires these to fail the connection rather than be guessed at.
    """
    if not payload:
        return None, ""
    if len(payload) == 1:
        raise _protocol_error("close frame payload is one byte", CloseCode.PROTOCOL_ERROR)
    status = int.from_bytes(payload[:2], "big")
    if status not in _VALID_CLOSE_CODES:
        raise TransportClosed(
            "close status " + str(status) + " is outside the registered ranges",
            code=BridgeErrorCode.WS_PROTOCOL_ERROR,
            context={"ws_close_code": int(CloseCode.PROTOCOL_ERROR), "reported_status": status},
        )
    try:
        reason = payload[2:].decode("utf-8")
    except UnicodeDecodeError as exc:
        raise TransportClosed(
            "close reason is not valid UTF-8",
            code=BridgeErrorCode.WS_PROTOCOL_ERROR,
            context={"ws_close_code": int(CloseCode.INVALID_PAYLOAD)},
        ) from exc
    return status, reason


class WebSocketConnection:
    """One open WebSocket, owned by whoever constructed it.

    Serialised writes: `send` holds a lock for the whole frame, because RFC 6455 frames from one
    endpoint must not interleave, and because the project's own concurrency rule is "one control
    channel at a time" (`docs/phases/phase-0/specs.md` section 5 rule 9).

    Fragmentation is reassembled internally; a caller only ever sees whole messages. Control frames
    may interleave fragments, which is why the ping/pong/close handling lives in the read loop and
    not beside it.
    """

    def __init__(
        self,
        stream: ByteStream,
        *,
        role: Role,
        connection_id: str,
        max_payload_bytes: int = MAX_FRAME_BYTES,
        peer_name: str = "",
    ) -> None:
        if max_payload_bytes < 1:
            raise ValueError("max_payload_bytes must be positive")
        self._stream = stream
        self._role = role
        self._id = connection_id
        self._max_payload_bytes = max_payload_bytes
        self._peer_name = peer_name
        self._send_lock = threading.Lock()
        self._closed = False
        self._close_sent = False
        self._close_received_code: int | None = None
        self._fragment_opcode: Opcode | None = None
        self._fragments: list[bytes] = []
        self._fragment_bytes = 0
        self.frames_in = 0
        self.frames_out = 0
        self.bytes_in = 0
        self.bytes_out = 0

        # ------------------------------------------------------------------ identity

    @property
    def id(self) -> str:
        return self._id

    @property
    def role(self) -> Role:
        return self._role

    @property
    def peer_name(self) -> str:
        return self._peer_name

    @property
    def is_closed(self) -> bool:
        return self._closed

    @property
    def close_code_from_peer(self) -> int | None:
        """The status the peer sent in its close frame, or `None` when it sent none.

        `None` covers both "not closed yet" and "closed without a status", which is why it is a
        property of the peer's frame rather than a field of this connection's state machine.
        """
        return self._close_received_code

    @property
    def require_inbound_mask(self) -> bool:
        """A server reads masked frames; a client reads unmasked ones."""
        return self._role is Role.SERVER

    @property
    def mask_outbound(self) -> bool:
        """The mirror image: a client masks what it writes, a server never does."""
        return self._role is Role.CLIENT

    # ------------------------------------------------------------------ sending

    def send_text(self, text: str) -> None:
        self._send(Opcode.TEXT, text.encode("utf-8"))

    def send_bytes(self, data: bytes) -> None:
        self._send(Opcode.BINARY, data)

    def send_ping(self, data: bytes = b"") -> None:
        if len(data) > 125:
            raise ValueError("a ping payload must fit in 125 bytes")
        self._send(Opcode.PING, data)

    def send_pong(self, data: bytes = b"") -> None:
        if len(data) > 125:
            raise ValueError("a pong payload must fit in 125 bytes")
        self._send(Opcode.PONG, data)

    def _send(self, opcode: Opcode, payload: bytes) -> None:
        if self._closed:
            raise TransportClosed(
                "connection " + self._id + " is closed; the message was not sent",
                context={"connection_id": self._id, "opcode": opcode.name},
            )
        if len(payload) > self._max_payload_bytes:
            raise FrameTooLarge(
                "outbound " + opcode.name + " payload is " + str(len(payload)) + " bytes, above the "
                + str(self._max_payload_bytes) + " byte budget",
                declared_bytes=len(payload),
                cap_bytes=self._max_payload_bytes,
            )
        with self._send_lock:
            try:
                written = write_frame(
                    self._stream,
                    opcode,
                    payload,
                    mask=self.mask_outbound,
                    max_payload_bytes=self._max_payload_bytes,
                )
            except OSError as exc:
                self._closed = True
                raise TransportClosed(
                    "connection " + self._id + " failed while sending: " + type(exc).__name__,
                    context={"connection_id": self._id, "error": type(exc).__name__},
                ) from exc
            self.frames_out += 1
            self.bytes_out += written

    def abort(self) -> None:
        """Tear the transport down without sending anything.

        RFC 6455 reserves status 1006 for exactly this case: the connection ended without a close
        frame. Writing a close frame to a peer that has already gone is not a handshake, it is
        theatre, and it makes "did the server end this on purpose?" unreadable in the bytes.
        """
        if self._closed:
            return
        self._closed = True
        self._close_sent = True
        try:
            self._stream.shutdown(socket.SHUT_WR)
        except OSError:
            pass
        try:
            self._stream.close()
        except OSError:
            pass

    def close(self, code: CloseCode | int = CloseCode.NORMAL, reason: str = "") -> None:
        """Send a close frame if one has not gone out, then tear the socket down. Idempotent.

        The frame is written through `write_frame`, so a client's close is masked exactly like every
        other client frame - an unmasked close is a protocol violation the peer must report.

        The peer's close is not waited for: a blocked or dead peer must not be able to keep this
        thread alive, and the RFC's "wait for the matching close" is best-effort by construction.
        """
        if self._closed:
            return
        try:
            payload = encode_close_payload(code, reason)
        except ValueError:
            payload = encode_close_payload(CloseCode.INTERNAL_ERROR, "")
        with self._send_lock:
            if not self._close_sent:
                try:
                    written = write_frame(
                        self._stream,
                        Opcode.CLOSE,
                        payload,
                        mask=self.mask_outbound,
                        max_payload_bytes=self._max_payload_bytes,
                    )
                    self.frames_out += 1
                    self.bytes_out += written
                except OSError:
                    pass  # a peer that already went is not a failure of our teardown
                except TransportClosed:
                    pass  # an oversized/rejected frame cannot happen for a <=125 byte close
                self._close_sent = True
        self._closed = True
        try:
            self._stream.shutdown(socket.SHUT_WR)
        except OSError:
            pass
        try:
            self._stream.close()
        except OSError:
            pass

    # ------------------------------------------------------------------ receiving

    def receive(self) -> ReceivedMessage:
        """Read until one whole message (or a close frame) is available.

        Raises:
            TransportClosed: EOF, a protocol violation, or a closed connection.
            FrameTooLarge: an oversized frame; the caller must fail the connection with 1009.
        """
        if self._closed:
            raise TransportClosed(
                "connection " + self._id + " is closed",
                context={"connection_id": self._id},
            )
        while True:
            frame = read_frame(
                self._stream,
                require_mask=self.require_inbound_mask,
                max_payload_bytes=self._max_payload_bytes,
            )
            self.frames_in += 1
            self.bytes_in += len(frame.payload)
            outcome = self._handle_frame(frame)
            if outcome is not None:
                return outcome

    def _handle_frame(self, frame: Frame) -> ReceivedMessage | None:
        opcode = frame.opcode
        if opcode in _CONTROL_OPCODES:
            return self._handle_control(frame)
        if opcode in (Opcode.TEXT, Opcode.BINARY):
            if self._fragment_opcode is not None:
                raise _protocol_error(
                    "a new data frame arrived mid-message", CloseCode.PROTOCOL_ERROR
                )
            if frame.fin:
                return self._complete_message(opcode, frame.payload)
            self._fragment_opcode = opcode
            self._fragments = [frame.payload]
            self._fragment_bytes = len(frame.payload)
            return None
        if opcode is Opcode.CONTINUATION:
            if self._fragment_opcode is None:
                raise _protocol_error("continuation frame with no message in progress", CloseCode.PROTOCOL_ERROR)
            self._fragment_bytes += len(frame.payload)
            if self._fragment_bytes > self._max_payload_bytes:
                raise FrameTooLarge(
                    "fragmented message has reached " + str(self._fragment_bytes) + " bytes, above the "
                    + str(self._max_payload_bytes) + " byte budget",
                    declared_bytes=self._fragment_bytes,
                    cap_bytes=self._max_payload_bytes,
                    context={"opcode": self._fragment_opcode.name},
                )
            self._fragments.append(frame.payload)
            if not frame.fin:
                return None
            assembled = b"".join(self._fragments)
            message_opcode = self._fragment_opcode
            self._fragment_opcode = None
            self._fragments = []
            self._fragment_bytes = 0
            return self._complete_message(message_opcode, assembled)
        raise _protocol_error("unexpected data opcode " + opcode.name, CloseCode.PROTOCOL_ERROR)

    def _handle_control(self, frame: Frame) -> ReceivedMessage | None:
        if frame.opcode is Opcode.PING:
            self.send_pong(frame.payload)
            return None
        if frame.opcode is Opcode.PONG:
            return ReceivedMessage(
                opcode=Opcode.PONG,
                data=frame.payload,
                connection_id=self._id,
            )
        if frame.opcode is Opcode.CLOSE:
            status, reason = decode_close_payload(frame.payload)
            self._close_received_code = status if status is not None else int(CloseCode.NO_STATUS_RECEIVED)
            if not self._close_sent:
                # Echo the status we were given, or 1000 when none was sent. This is the close
                # handshake: the peer is entitled to its own code back.
                echo = status if status is not None else int(CloseCode.NORMAL)
                self.close(echo, reason)
            else:
                self._closed = True
            return ReceivedMessage(
                opcode=Opcode.CLOSE,
                close_code=self._close_received_code,
                close_reason=reason,
                connection_id=self._id,
            )
        return None

    def _complete_message(self, opcode: Opcode, payload: bytes) -> ReceivedMessage:
        if opcode is Opcode.TEXT:
            try:
                text = payload.decode("utf-8")
            except UnicodeDecodeError as exc:
                raise TransportClosed(
                    "text frame is not valid UTF-8",
                    code=BridgeErrorCode.WS_PROTOCOL_ERROR,
                    context={"ws_close_code": int(CloseCode.INVALID_PAYLOAD)},
                ) from exc
            return ReceivedMessage(opcode=Opcode.TEXT, text=text, connection_id=self._id)
        return ReceivedMessage(opcode=Opcode.BINARY, data=payload, connection_id=self._id)


class StreamFactory(Protocol):
    """How the client reaches the server. Injectable so tests never open a socket."""

    def __call__(self, host: str, port: int) -> ByteStream: ...


def _tcp_stream_factory(host: str, port: int) -> ByteStream:
    if host != LOOPBACK_HOST:
        raise ValueError("refusing to open a device-bridge client socket to " + repr(host))
    return socket.create_connection((host, port), timeout=5.0)


class WebSocketClient:
    """A loopback WebSocket client that verifies the server's accept key.

    Verification is the point of having a client at all: the accept key is the only evidence the
    peer really understood the handshake, and a client that does not check it will happily talk to
    whatever happens to be listening on the port.
    """

    def __init__(
        self,
        *,
        port: int,
        token: str,
        host: str = LOOPBACK_HOST,
        path: str = "/",
        stream_factory: StreamFactory | None = None,
        max_payload_bytes: int = MAX_FRAME_BYTES,
    ) -> None:
        if host != LOOPBACK_HOST:
            raise ValueError(
                "the device-bridge client only connects to " + LOOPBACK_HOST + "; got " + repr(host)
            )
        if not token or len(token) < MIN_TOKEN_LENGTH:
            raise ValueError(
                "a client token of fewer than " + str(MIN_TOKEN_LENGTH) + " characters is a placeholder, "
                "not a secret"
            )
        if not 0 <= port <= 65535:
            raise ValueError("port must be in 0..65535")
        self._host = host
        self._port = port
        self._token = token
        self._path = path
        self._stream_factory: StreamFactory = stream_factory if stream_factory is not None else _tcp_stream_factory
        self._max_payload_bytes = max_payload_bytes
        self._connection: WebSocketConnection | None = None

    @property
    def connection(self) -> WebSocketConnection:
        """The live connection.

        Raises:
            TransportClosed: `connect()` has not run, or the connection was closed. Callers get an
                error rather than a `None` they have to remember to test.
        """
        if self._connection is None or self._connection.is_closed:
            raise TransportClosed(
                "the client has no open connection; call connect() first",
                context={"port": self._port},
            )
        return self._connection

    @property
    def is_connected(self) -> bool:
        return self._connection is not None and not self._connection.is_closed

    def connect(self) -> WebSocketConnection:
        """Open the transport, send the handshake, verify the response, return the connection.

        Raises:
            HandshakeRejected: the server did not switch protocols, or its `Sec-WebSocket-Accept`
                does not equal the value derived from our key.
            TransportClosed: the socket could not be opened.
        """
        try:
            stream = self._stream_factory(self._host, self._port)
        except OSError as exc:
            raise TransportClosed(
                "could not connect to " + self._host + ":" + str(self._port) + ": " + type(exc).__name__,
                context={"error": type(exc).__name__, "port": self._port},
            ) from exc
        key = base64.b64encode(os.urandom(16)).decode("ascii")
        query_token = "?token=" + urllib.parse.quote(self._token, safe="")
        request_target = self._path if "?" in self._path else self._path + query_token
        headers = [
            "GET " + request_target + " HTTP/1.1",
            "Host: " + self._host + ":" + str(self._port),
            "Upgrade: websocket",
            "Connection: Upgrade",
            "Sec-WebSocket-Key: " + key,
            "Sec-WebSocket-Version: " + WEBSOCKET_VERSION,
        ]
        surplus = b""
        status_line = ""
        accept: str | None = None
        try:
            stream.sendall(("\r\n".join(headers) + "\r\n\r\n").encode("ascii"))
            response_headers, surplus = _read_handshake_response(stream)
            status_line = response_headers.get("_status_line", "")
            accept = response_headers.get("sec-websocket-accept")
        except (OSError, TransportClosed) as exc:
            stream.close()
            if isinstance(exc, TransportClosed):
                raise
            raise TransportClosed(
                "handshake I/O failed: " + type(exc).__name__,
                context={"error": type(exc).__name__},
            ) from exc
        parts = status_line.split(" ")
        if len(parts) < 2 or parts[1] != "101":
            stream.close()
            raise HandshakeRejected(
                "server did not switch protocols: " + repr(status_line),
                code=BridgeErrorCode.WS_HANDSHAKE_REJECTED,
                http_status=int(parts[1]) if len(parts) > 1 and parts[1].isdigit() else 0,
                reason="handshake refused",
            )
        expected_accept = compute_accept_key(key)
        if accept != expected_accept:
            stream.close()
            raise HandshakeRejected(
                "server returned Sec-WebSocket-Accept " + repr(accept) + ", which is not the value "
                "derived from our key; the peer is not a RFC 6455 server we agreed to talk to",
                code=BridgeErrorCode.WS_HANDSHAKE_REJECTED,
                http_status=101,
                reason="accept key mismatch",
            )
        connection = WebSocketConnection(
            _PrefixStream(stream, surplus),
            role=Role.CLIENT,
            connection_id="client-" + secrets.token_hex(4),
            max_payload_bytes=self._max_payload_bytes,
            peer_name=self._host + ":" + str(self._port),
        )
        self._connection = connection
        return connection

    def close(self, code: CloseCode | int = CloseCode.NORMAL) -> None:
        if self._connection is not None:
            self._connection.close(code)
            self._connection = None


def _read_handshake_response(stream: ByteStream) -> tuple[dict[str, str], bytes]:
    """Read an HTTP response head, capped at `MAX_HANDSHAKE_BYTES`.

    Returns the header map plus any surplus bytes that arrived in the same burst, which belong to
    the first frame the server sends.
    """
    collected = bytearray()
    while b"\r\n\r\n" not in collected:
        if len(collected) > MAX_HANDSHAKE_BYTES:
            raise TransportClosed(
                "handshake response exceeds " + str(MAX_HANDSHAKE_BYTES) + " bytes",
                code=BridgeErrorCode.WS_PROTOCOL_ERROR,
            )
        try:
            chunk = stream.recv(512)
        except (BlockingIOError, TimeoutError):
            continue
        if not chunk:
            raise TransportClosed("peer closed during the handshake")
        collected.extend(chunk)
    head, surplus = collected.split(b"\r\n\r\n", 1)
    headers: dict[str, str] = {}
    lines = head.decode("ascii").split("\r\n")
    headers["_status_line"] = lines[0]
    for line in lines[1:]:
        if not line:
            continue
        name, _, value = line.partition(":")
        headers[name.strip().lower()] = value.strip()
    return headers, bytes(surplus)


MessageHandler = Callable[[WebSocketConnection, ReceivedMessage], None]
OpenHandler = Callable[[WebSocketConnection], None]
CloseHandler = Callable[[WebSocketConnection, int | None], None]


class WebSocketServer:
    """A single-purpose RFC 6455 server bound to `127.0.0.1`.

    Lifecycle: `start()` binds and spawns the accept thread; each accepted socket gets its own
    thread and exactly one `WebSocketConnection`; `stop()` closes the listener, sends a going-away
    close to every connection and joins every thread. There is no re-`start()` - a server object
    owns one listener for its whole life, which is what makes the connection registry trustworthy.
    """

    def __init__(
        self,
        *,
        token: str,
        port: int = 0,
        host: str = LOOPBACK_HOST,
        max_connections: int = MAX_CONNECTIONS,
        on_open: OpenHandler | None = None,
        on_message: MessageHandler | None = None,
        on_close: CloseHandler | None = None,
        listen_backlog: int = 4,
        max_payload_bytes: int = MAX_FRAME_BYTES,
        name: str = "omnibuds-device-bridge",
    ) -> None:
        if host != LOOPBACK_HOST:
            raise ValueError(
                "the device-bridge server binds " + LOOPBACK_HOST + " and nothing else; refusing "
                + repr(host) + ", which would expose the harness beyond this machine"
            )
        if not token or len(token) < MIN_TOKEN_LENGTH:
            raise ValueError(
                "the server token must be at least " + str(MIN_TOKEN_LENGTH) + " characters; a short "
                "or blank token means the check is decoration"
            )
        if not 0 <= port <= 65535:
            raise ValueError("port must be in 0..65535")
        if max_connections < 1 or max_connections > MAX_CONNECTIONS:
            raise ValueError(
                "max_connections must be between 1 and the documented MAX_CONNECTIONS ("
                + str(MAX_CONNECTIONS) + ")"
            )
        if max_payload_bytes < 1:
            raise ValueError("max_payload_bytes must be positive")
        self._token = token
        self._requested_port = port
        self._max_connections = max_connections
        self._max_payload_bytes = max_payload_bytes
        self._on_open = on_open
        self._on_message = on_message
        self._on_close = on_close
        self._name = name
        self._backlog = listen_backlog
        self._listener: socket.socket | None = None
        self._accept_thread: threading.Thread | None = None
        self._threads: dict[str, threading.Thread] = {}
        self._connections: dict[str, WebSocketConnection] = {}
        self._registry_lock = threading.Lock()
        self._running = False
        self._stopped = False
        self._shutdown_requested = False
        self.port = 0
        self.metrics: dict[str, int] = {
            "connections_accepted": 0,
            "connections_closed": 0,
            "connections_refused_no_token": 0,
            "connections_refused_limit": 0,
            "connections_refused_handshake": 0,
            "frames_too_large": 0,
            "protocol_errors": 0,
            "handler_errors": 0,
        }

    # ------------------------------------------------------------------ lifecycle

    def start(self) -> int:
        """Bind the loopback listener and start accepting. Returns the bound port.

        Raises:
            OSError: the port is taken or the loopback interface is unavailable. Surfaced as-is: a
                bind failure is the operator's to see, not something to relabel.
        """
        if self._running:
            raise RuntimeError("this server is already started")
        if self._stopped:
            raise RuntimeError("this server object has been stopped; construct a new one to listen again")
        listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            listener.bind((LOOPBACK_HOST, self._requested_port))
            listener.listen(self._backlog)
        except OSError:
            listener.close()
            raise
        listener.settimeout(ACCEPT_POLL_S)
        self._listener = listener
        self.port = int(listener.getsockname()[1])
        self._running = True
        self._accept_thread = threading.Thread(
            target=self._accept_loop,
            name=self._name + "-accept",
            daemon=True,
        )
        self._accept_thread.start()
        return self.port

    def stop(self, code: CloseCode | int = CloseCode.GOING_AWAY) -> None:
        """Close the listener, every connection and every thread. Idempotent."""
        if self._stopped:
            return
        self._shutdown_requested = True
        self._running = False
        listener = self._listener
        self._listener = None
        if listener is not None:
            try:
                listener.close()
            except OSError:
                pass
        with self._registry_lock:
            connections = list(self._connections.values())
        for connection in connections:
            connection.close(code)
        accept_thread = self._accept_thread
        if accept_thread is not None and accept_thread is not threading.current_thread():
            accept_thread.join(timeout=2.0)
        with self._registry_lock:
            threads = list(self._threads.items())
        for connection_id, thread in threads:
            if thread is threading.current_thread():
                continue
            thread.join(timeout=2.0)
            if thread.is_alive():
                self.metrics["handler_errors"] += 1
                _log_line(
                    self._name + ": connection thread for " + connection_id
                    + " did not exit within 2s of stop()"
                )
        self._stopped = True

    def __enter__(self) -> "WebSocketServer":
        self.start()
        return self

    def __exit__(self, exc_type: object, exc: object, tb: object) -> None:
        self.stop()

    @property
    def is_running(self) -> bool:
        return self._running

    @property
    def max_connections(self) -> int:
        return self._max_connections

    @property
    def connection_ids(self) -> tuple[str, ...]:
        with self._registry_lock:
            return tuple(self._connections)

    @property
    def connection_count(self) -> int:
        with self._registry_lock:
            return len(self._connections)

    # ------------------------------------------------------------------ accept / serve

    def _accept_loop(self) -> None:
        while self._running:
            listener = self._listener
            if listener is None:
                return
            try:
                client, address = listener.accept()
            except TimeoutError:
                continue
            except OSError:
                if self._running:
                    self.metrics["protocol_errors"] += 1
                return
            client.settimeout(READ_POLL_S)
            thread = threading.Thread(
                target=self._serve_socket,
                args=(client, address),
                name=self._name + "-conn",
                daemon=True,
            )
            thread.start()

    def _serve_socket(self, client: socket.socket, address: tuple[str, int]) -> None:
        peer = address[0] + ":" + str(address[1])
        try:
            self.handle_stream(client, peer_name=peer)
        except Exception as exc:  # a serving thread that dies silently is worse than one that logs
            self.metrics["handler_errors"] += 1
            _log_line(self._name + ": serving thread raised " + type(exc).__name__ + " for " + peer)
        finally:
            try:
                client.close()
            except OSError:
                pass

    def handle_stream(self, stream: ByteStream, *, peer_name: str = "in-memory") -> WebSocketConnection | None:
        """Perform the server handshake on `stream`, then serve it until it closes.

        Public because it is how the whole protocol layer is tested without a listener: the code
        path is identical, only the transport differs.

        Returns the connection when the stream completed, or `None` when the handshake was refused
        (in which case the refusal response has already been written).
        """
        surplus = b""
        try:
            request_bytes, surplus = _read_handshake_request(stream)
            request = parse_handshake(request_bytes)
        except HandshakeRejected as exc:
            self.metrics["connections_refused_handshake"] += 1
            _write_all(stream, build_reject_response(exc.http_status))
            _close_quietly(stream)
            return None
        except TransportClosed:
            self.metrics["connections_refused_handshake"] += 1
            _close_quietly(stream)
            return None
        with self._registry_lock:
            over_limit = len(self._connections) >= self._max_connections
        if over_limit:
            # The request is read before the refusal is written: closing with unread bytes in the
            # socket makes Windows send a reset instead of delivering the response, and a client
            # that cannot see its own 503 learns nothing about the limit.
            self.metrics["connections_refused_limit"] += 1
            _write_all(
                stream,
                build_reject_response(
                    503,
                    reject_reason(503),
                    {"X-Device-Bridge-Limit": str(self._max_connections)},
                ),
            )
            _close_quietly(stream)
            return None
        try:
            validate_upgrade_headers(request)
            offered = extract_request_token(request)
            if not token_matches(offered, self._token):
                self.metrics["connections_refused_no_token"] += 1
                _write_all(stream, build_reject_response(401))
                _close_quietly(stream)
                return None
            _write_all(stream, build_accept_response(request))
        except HandshakeRejected as exc:
            self.metrics["connections_refused_handshake"] += 1
            _write_all(stream, build_reject_response(exc.http_status))
            _close_quietly(stream)
            return None
        except TransportClosed:
            self.metrics["connections_refused_handshake"] += 1
            _close_quietly(stream)
            return None
        connection = WebSocketConnection(
            _PrefixStream(stream, surplus),
            role=Role.SERVER,
            connection_id="conn-" + secrets.token_hex(4),
            max_payload_bytes=self._max_payload_bytes,
            peer_name=peer_name,
        )
        self._register(connection)
        try:
            self._serve_connection(connection)
        finally:
            self._unregister(connection)
        return connection

    def _register(self, connection: WebSocketConnection) -> None:
        with self._registry_lock:
            self._connections[connection.id] = connection
            self.metrics["connections_accepted"] += 1

    def _unregister(self, connection: WebSocketConnection) -> None:
        with self._registry_lock:
            if self._connections.pop(connection.id, None) is not None:
                self.metrics["connections_closed"] += 1

    def _serve_connection(self, connection: WebSocketConnection) -> None:
        thread = threading.current_thread()
        with self._registry_lock:
            self._threads[connection.id] = thread
        close_code: int | None = None
        try:
            if self._on_open is not None:
                self._on_open(connection)
            while not connection.is_closed and not self._shutdown_requested:
                try:
                    message = connection.receive()
                except FrameTooLarge as exc:
                    self.metrics["frames_too_large"] += 1
                    close_code = int(CloseCode.MESSAGE_TOO_BIG)
                    _log_line(
                        self._name + ": failing connection " + connection.id + " with 1009: "
                        + str(exc.declared_bytes) + " > " + str(exc.cap_bytes)
                    )
                    connection.close(CloseCode.MESSAGE_TOO_BIG, "frame exceeds the agreed cap")
                    break
                except TransportClosed as exc:
                    close_code = exc.context.get("ws_close_code", int(CloseCode.ABNORMAL_CLOSURE))
                    if exc.code is BridgeErrorCode.WS_PROTOCOL_ERROR:
                        self.metrics["protocol_errors"] += 1
                        connection.close(
                            CloseCode(close_code) if close_code in _VALID_CLOSE_CODES else CloseCode.PROTOCOL_ERROR,
                            "protocol violation",
                        )
                    else:
                        # A plain EOF: the peer is gone, so nothing may be written to it. The close
                        # status this side would have used is 1006, which is not sendable.
                        connection.abort()
                    break
                if message.opcode is Opcode.CLOSE:
                    close_code = message.close_code
                    break
                if message.opcode is Opcode.PONG:
                    continue
                if self._on_message is not None:
                    try:
                        self._on_message(connection, message)
                    except Exception as exc:  # a handler failure ends the connection, visibly
                        self.metrics["handler_errors"] += 1
                        _log_line(
                            self._name + ": message handler raised " + type(exc).__name__
                            + " on " + connection.id
                        )
                        connection.close(CloseCode.INTERNAL_ERROR, "handler failure")
                        close_code = int(CloseCode.INTERNAL_ERROR)
                        break
        finally:
            if not connection.is_closed:
                connection.close(CloseCode.GOING_AWAY, "server stopping")
            with self._registry_lock:
                self._threads.pop(connection.id, None)
            if self._on_close is not None:
                try:
                    self._on_close(connection, close_code)
                except Exception as exc:  # teardown must not be blocked by a callback
                    self.metrics["handler_errors"] += 1
                    _log_line(self._name + ": close handler raised " + type(exc).__name__)


class _PrefixStream:
    """A stream with bytes already read from it, put back at the front.

    A loopback peer can send the handshake and the first frame in one burst, and the kernel may hand
    both to a single `recv`. Discarding the surplus would break that client for no reason, and
    pretending not to see it would corrupt the next frame. So the surplus is carried into the
    connection instead - read once, buffered in memory, never on disk.
    """

    def __init__(self, inner: ByteStream, prefix: bytes) -> None:
        self._inner = inner
        self._prefix = bytearray(prefix)

    def recv(self, bufsize: int) -> bytes:
        if self._prefix:
            take = bytes(self._prefix[:bufsize])
            del self._prefix[: len(take)]
            return take
        return self._inner.recv(bufsize)

    def sendall(self, data: bytes) -> None:
        self._inner.sendall(data)

    def shutdown(self, how: int) -> None:
        self._inner.shutdown(how)

    def close(self) -> None:
        self._inner.close()


def _read_handshake_request(stream: ByteStream) -> tuple[bytes, bytes]:
    """Read request bytes up to the header terminator, refusing to read past the cap.

    Returns `(head_with_terminator, surplus)`; the surplus belongs to the first frame.
    """
    collected = bytearray()
    while b"\r\n\r\n" not in collected:
        if len(collected) > MAX_HANDSHAKE_BYTES:
            raise TransportClosed(
                "handshake request exceeds " + str(MAX_HANDSHAKE_BYTES) + " bytes",
                code=BridgeErrorCode.WS_PROTOCOL_ERROR,
            )
        try:
            chunk = stream.recv(1024)
        except (BlockingIOError, TimeoutError):
            continue
        except OSError as exc:
            raise TransportClosed(
                "read failed during handshake: " + type(exc).__name__,
                context={"error": type(exc).__name__},
            ) from exc
        if not chunk:
            raise TransportClosed("peer closed before completing the handshake")
        collected.extend(chunk)
    head, surplus = collected.split(b"\r\n\r\n", 1)
    return bytes(head) + b"\r\n\r\n", bytes(surplus)


def _write_all(stream: ByteStream, data: bytes) -> None:
    try:
        stream.sendall(data)
    except OSError as exc:
        raise TransportClosed(
            "could not write to the peer: " + type(exc).__name__,
            context={"error": type(exc).__name__},
        ) from exc


def _close_quietly(stream: ByteStream) -> None:
    try:
        stream.close()
    except OSError:
        pass


def _log_line(message: str) -> None:
    """One line to stderr, size-bounded, content-free.

    Deliberately not the `logging` module with a handler attached: this package must not be the
    thing that decides where a workstation's logs go, and nothing here may quote frame content.
    """
    sys.stderr.write(message[:500] + "\n")


__all__ = [
    "LOOPBACK_HOST",
    "WEBSOCKET_GUID",
    "WEBSOCKET_VERSION",
    "MAX_HANDSHAKE_BYTES",
    "MAX_CONNECTIONS",
    "MIN_TOKEN_LENGTH",
    "Opcode",
    "CloseCode",
    "Role",
    "Frame",
    "ReceivedMessage",
    "HandshakeRequest",
    "WebSocketConnection",
    "WebSocketClient",
    "WebSocketServer",
    "compute_accept_key",
    "parse_handshake",
    "extract_request_token",
    "token_matches",
    "validate_upgrade_headers",
    "build_accept_response",
    "build_reject_response",
    "reject_reason",
    "HTTP_REASON_PHRASES",
    "apply_mask",
    "read_frame",
    "write_frame",
    "encode_close_payload",
    "decode_close_payload",
]
