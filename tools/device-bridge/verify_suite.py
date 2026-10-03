#!/usr/bin/env python3
"""Offline, deterministic self-check for the host side of the device bridge.

What this proves, and what it does not
--------------------------------------
Every check here runs against recorded fixtures and a fake ADB transport, in memory, with no socket
that has to be reachable and no dependency that has to be installed. It proves that the parsing,
construction, validation and protocol logic behave as documented for the inputs given.

It proves nothing about a phone. Nothing here ran `adb`, `gradlew`, or any device command, and no
claim in this output should be read as device-verified. The behaviours that need a live run are
listed in `README.md` under "What is NOT verified".

A check only prints PASS if it executed. A check that cannot run prints SKIP, and SKIP is not a
pass: it is reported separately and fails the exit status if any check was expected to run.

Usage (from the repository root):

    python tools/device-bridge/verify_suite.py
"""

from __future__ import annotations

import ast
import base64
import json
import sys
from pathlib import Path
from typing import Callable

_ROOT = Path(__file__).resolve().parent
for _entry in (str(_ROOT), str(_ROOT / "tests")):
    if _entry not in sys.path:
        sys.path.insert(0, _entry)

import support  # noqa: E402  (path bootstrap above must run first)
from bridge import HARNESS_ACTIVITY, HARNESS_PACKAGE, harness_resource_id  # noqa: E402
from bridge.adb import AdbTransport, build_install_argv, parse_getprop_dump  # noqa: E402
from bridge.capture import (  # noqa: E402
    ForegroundGate,
    FrameRingBuffer,
    capture_frame,
    read_png_ihdr,
)
from bridge.envelope import (  # noqa: E402
    AGREED_IMAGE_ENCODINGS,
    MAX_FRAME_BYTES,
    REPLAY_CAPACITY_DEFAULT,
    AgentEnvelope,
    EventType,
    ReplayBuffer,
    SequenceLedger,
)
from bridge.errors import (  # noqa: E402
    AdbFailed,
    BridgeErrorCode,
    EnvelopeInvalid,
    FrameTooLarge,
    HierarchyRejected,
    InputRejected,
    NotForeground,
    TransportClosed,
)
from bridge.gateway import BackoffSchedule, Gateway  # noqa: E402
from bridge.hierarchy import assert_resolvable, centre_of, parse_dump, require_resolvable  # noqa: E402
from bridge.input_actions import (  # noqa: E402
    KEYEVENT_MAX,
    escape_input_text,
    keycode_to_int,
    tap,
    text,
)
from bridge.ws_server import (  # noqa: E402
    LOOPBACK_HOST,
    CloseCode,
    HandshakeRejected,
    Opcode,
    WebSocketClient,
    WebSocketServer,
    compute_accept_key,
    read_frame,
)

TOKEN = "verify-suite-token-0123456789"
EPOCH_SECONDS = 1_777_000_000.5
CHECKS: list[tuple[str, Callable[[], None]]] = []


def check(name: str) -> Callable[[Callable[[], None]], Callable[[], None]]:
    """Register a check. The decorator only records; nothing is skipped at registration time."""

    def register(function: Callable[[], None]) -> Callable[[], None]:
        CHECKS.append((name, function))
        return function

    return register


def expect(condition: bool, detail: str) -> None:
    if not condition:
        raise AssertionError(detail)


def expect_equal(actual: object, wanted: object, detail: str) -> None:
    if actual != wanted:
        raise AssertionError(detail + ": got " + repr(actual) + ", wanted " + repr(wanted))


def raises(error_type: type[BaseException], function: Callable[[], object], detail: str) -> BaseException:
    try:
        function()
    except error_type as exc:
        return exc
    except BaseException as exc:  # a different failure is still a failure of the expectation
        raise AssertionError(detail + ": raised " + type(exc).__name__ + " instead of " + error_type.__name__) from exc
    raise AssertionError(detail + ": nothing was raised")


# --------------------------------------------------------------------------- fixtures


def scripted_preflight_transport() -> tuple[AdbTransport, support.FakeAdbRunner]:
    """A transport whose every `getprop` answer comes from the recorded fixture text."""
    props = parse_getprop_dump(support.getprop_dump())
    transport, runner = support.make_transport()
    for key in (
        "ro.product.cpu.abi",
        "ro.build.version.sdk",
        "ro.build.version.release",
        "ro.product.model",
        "ro.product.manufacturer",
    ):
        runner.on_shell_value(key, props[key])
    return transport, runner


def gated_transport(focus_package: str) -> tuple[AdbTransport, support.FakeAdbRunner, ForegroundGate]:
    transport, runner = support.make_transport()
    runner.on(
        "shell",
        "dumpsys",
        "window",
        stdout=(
            "WINDOW MANAGER DATA\n  mCurrentFocus=Window{6d3b1c8 u0 "
            + focus_package
            + "/"
            + focus_package
            + ".ShellActivity}\n"
        ).encode("utf-8"),
        repeat=True,
    )
    runner.on("exec-out", "screencap", "-p", stdout=support.screencap_png(), repeat=True)
    runner.on("shell", "input", stdout=b"", repeat=True)
    return transport, runner, ForegroundGate(transport, HARNESS_PACKAGE)


def harness_dump() -> object:
    return parse_dump(support.uiautomator_dump_text())


def element_by_id(short_name: str) -> object:
    return harness_dump().find_by_resource_id(harness_resource_id(short_name))[0]


def telemetry(seq_no: int) -> AgentEnvelope:
    return AgentEnvelope(
        version=1,
        seq_no=seq_no,
        timestamp=EPOCH_SECONDS,
        event_type=EventType.TELEMETRY,
        payload={"probe": seq_no},
    )


def client_frame(payload: bytes, opcode: Opcode = Opcode.TEXT) -> bytes:
    return support.render_frame(opcode, payload, mask=True)


def server_replies(stream: support.FakeStream) -> list[tuple[Opcode, bytes]]:
    """Decode what a server wrote after its handshake response."""
    head_end = stream.sent_bytes.find(b"\r\n\r\n")
    expect(head_end >= 0, "the server wrote no response head")
    probe = support.FakeStream(stream.sent_bytes[head_end + 4:])
    out: list[tuple[Opcode, bytes]] = []
    try:
        while True:
            frame = read_frame(probe, require_mask=False)
            out.append((frame.opcode, frame.payload))
    except TransportClosed:
        return out


# --------------------------------------------------------------------------- checks


@check("preflight parses five build facts from the recorded getprop fixture")
def check_preflight_from_fixture() -> None:
    transport, runner = scripted_preflight_transport()
    profile = transport.preflight()
    expect_equal(profile.abi, "arm64-v8a", "ABI")
    expect_equal(profile.sdk, 34, "SDK")
    expect_equal(profile.release, "14", "release")
    expect_equal(profile.model, "2311DRK48I", "model")
    expect_equal(profile.manufacturer, "Xiaomi", "manufacturer")
    expect(profile.meets_min_sdk, "API 34 must satisfy the project minSdk of 26")
    expect_equal(len(runner.calls), 5, "preflight must issue exactly five targeted reads")
    for call in runner.calls:
        expect(call[4] == "getprop" and len(call) == 6, "each read must name one property: " + str(call))
    expect(not any(len(call) == 5 for call in runner.calls), "a bare `getprop` dump must not be issued")


@check("install command carries -r and never -g")
def check_install_command() -> None:
    argv = build_install_argv("adb", "SERIAL", "/tmp/harness-debug.apk", reinstall=True)
    expect("-r" in argv, "reinstall must be requested with -r")
    expect("-g" not in argv, "install must never blanket-grant permissions with -g")
    transport, runner = support.make_transport()
    runner.on("install", stdout=b"Success\n", repeat=True)
    transport.install("/tmp/harness-debug.apk")
    recorded = runner.calls[0]
    expect_equal(recorded, ["adb", "-s", "BRIDGEFA0001", "install", "-r", "/tmp/harness-debug.apk"], "argv")
    for call in runner.calls:
        expect("-g" not in call and "--grant-runtime-permissions" not in call, "a blanket grant appeared")
    second = build_install_argv("adb", "S", "/tmp/a.apk", reinstall=False)
    expect("-r" not in second, "a first install must not claim to reinstall")
    expect("-g" not in second, "no path may add -g")


@check("permissions are granted one at a time, never as part of install")
def check_grant_is_explicit() -> None:
    transport, runner = support.make_transport()
    runner.on("shell", "pm", "grant", stdout=b"", repeat=True)
    transport.grant(HARNESS_PACKAGE, "android.permission.POST_NOTIFICATIONS")
    argv = runner.calls[0]
    expect(argv[3:6] == ["shell", "pm", "grant"], "grant must be a pm grant: " + str(argv))
    expect_equal(argv[-2], HARNESS_PACKAGE, "grant names the package")
    expect_equal(argv[-1], "android.permission.POST_NOTIFICATIONS", "grant names exactly one permission")
    expect_raises_value_error(transport, "android.permission.*")


def expect_raises_value_error(transport: AdbTransport, permission: str) -> None:
    raises(ValueError, lambda: transport.grant(HARNESS_PACKAGE, permission), "a wildcard grant must be refused")


@check("the foreground gate refuses capture of another app and sends no capture command")
def check_foreground_gate_refusal() -> None:
    transport, runner, gate = gated_transport("com.example.foreigndemo")
    error = raises(NotForeground, lambda: capture_frame(transport, gate), "a foreign window must be refused")
    assert isinstance(error, NotForeground)
    expect_equal(error.observed_package, "com.example.foreigndemo", "the refusal names what was foreground")
    expect_equal(error.expected_package, HARNESS_PACKAGE, "and what was required")
    capture_commands = [call for call in runner.calls if "screencap" in call]
    expect_equal(capture_commands, [], "no capture command may be issued after a refusal")


@check("capture succeeds only while the harness is foreground")
def check_gate_allows_our_app() -> None:
    transport, runner, gate = gated_transport(HARNESS_PACKAGE)
    frame = capture_frame(transport, gate, clock=lambda: EPOCH_SECONDS)
    expect_equal(frame.width_hint, 1080, "width from IHDR")
    expect_equal(frame.height_hint, 2400, "height from IHDR")
    expect_equal(frame.captured_at_epoch_ms, int(EPOCH_SECONDS * 1000), "timestamp from the injected clock")
    expect_equal(frame.png_bytes, support.screencap_png(), "bytes are the capture, unchanged")
    expect(runner.calls[0][-2:] == ["dumpsys", "window"], "the gate is consulted before the capture")
    expect(runner.calls[1][-2:] == ["screencap", "-p"], "the capture follows the gate")


@check("an unreadable focus fails closed rather than capturing")
def check_gate_fails_closed() -> None:
    transport, runner = support.make_transport()
    runner.on("shell", "dumpsys", "window", stdout=b"  mCurrentFocus=null\n", repeat=True)
    runner.on("exec-out", "screencap", "-p", stdout=support.screencap_png(), repeat=True)
    gate = ForegroundGate(transport, HARNESS_PACKAGE)
    error = raises(AdbFailed, lambda: capture_frame(transport, gate), "unresolvable focus must fail")
    assert isinstance(error, AdbFailed)
    expect_equal(error.code, BridgeErrorCode.FOCUS_UNRESOLVED, "focus refusal code")
    expect(not any("screencap" in call for call in runner.calls), "an unknown state may not be captured")


@check("the hierarchy fixture resolves every companion-shell id and content description")
def check_hierarchy_resolution() -> None:
    dump = harness_dump()
    for short_name in ("bridge_title", "bridge_status", "bridge_probe_button", "bridge_counter_value", "bridge_toggle", "bridge_input_field"):
        matches = dump.find_by_resource_id(harness_resource_id(short_name))
        expect_equal(len(matches), 1, short_name + " must resolve to exactly one node")
        expect_equal(matches[0].package, HARNESS_PACKAGE, short_name + " package")
    for description in ("bridge-title", "bridge-status", "bridge-probe-button", "bridge-counter", "bridge-toggle", "bridge-input"):
        expect_equal(len(dump.find_by_content_desc(description)), 1, description)
    counter = element_by_id("bridge_counter_value")
    expect_equal(counter.text, "probes: 3", "the counter text must be read verbatim")
    field = element_by_id("bridge_input_field")
    expect(field.text is None, "an empty text attribute is absence, not an empty string")
    expect(dump.rotation == 0, "rotation")


@check("centre resolution against known bounds")
def check_centre_resolution() -> None:
    button = element_by_id("bridge_probe_button")
    expect_equal(button.bounds, (66, 182, 1014, 326), "fixture bounds")
    expect_equal(centre_of(button), (540, 254), "probe button centre")
    expect_equal(require_resolvable(button), (540, 254), "resolvable centre")
    toggle = element_by_id("bridge_toggle")
    expect_equal(centre_of(toggle), ((66 + 400) // 2, (378 + 450) // 2), "checkbox centre")
    odd = centre_of(element_by_id("bridge_title"))
    expect_equal(odd, ((66 + 520) // 2, (66 + 130) // 2), "title centre floors toward the origin")


@check("an element that cannot justify a tap is refused before any command")
def check_resolvability_refusal() -> None:
    title = element_by_id("bridge_title")
    block = assert_resolvable(title)
    expect(block is not None, "a non-clickable view must not be tappable by default")
    assert block is not None
    expect_equal(block.code, BridgeErrorCode.TARGET_NOT_CLICKABLE, "refusal code")
    expect(assert_resolvable(title, allow_non_clickable=True) is None, "the escape hatch must work when passed")
    transport, runner, gate = gated_transport(HARNESS_PACKAGE)
    raises(HierarchyRejected, lambda: tap(transport, title, gate), "tap must refuse a non-clickable target")
    expect(not any("input" in call for call in runner.calls), "a refused tap may not reach the device")


@check("out-of-range bounds are rejected rather than clamped")
def check_bounds_rejection() -> None:
    document = (
        '<hierarchy rotation="0"><node index="0" bounds="[0,0][200000,10]" class="android.view.View" '
        'package="' + HARNESS_PACKAGE + '" clickable="true" enabled="true" focused="false" /></hierarchy>'
    )
    raises(HierarchyRejected, lambda: parse_dump(document), "absurd bounds must be rejected")
    inverted = (
        '<hierarchy rotation="0"><node index="0" bounds="[100,100][10,10]" class="android.view.View" '
        'package="' + HARNESS_PACKAGE + '" clickable="true" enabled="true" focused="false" /></hierarchy>'
    )
    raises(HierarchyRejected, lambda: parse_dump(inverted), "inverted bounds must be rejected")
    truncated = '<hierarchy rotation="0"><node index="0" bounds="broken"'
    raises(HierarchyRejected, lambda: parse_dump(truncated), "malformed XML must be rejected")


@check("envelope round trip through encode and decode")
def check_envelope_round_trip() -> None:
    original = telemetry(7)
    wire = original.encode()
    decoded = AgentEnvelope.decode(wire)
    expect_equal(decoded, original, "round trip")
    expect_equal(json.loads(wire)["event_type"], "TELEMETRY", "wire event type")
    png_backed = AgentEnvelope.from_frame(
        support_frame(), seq_no=8, timestamp=EPOCH_SECONDS
    )
    reopened = AgentEnvelope.decode(png_backed.encode())
    expect_equal(base64.b64decode(reopened.payload["data_base64"]), support.screencap_png(), "frame bytes survive")
    expect_equal(reopened.payload["encoding"], "image/png", "declared encoding")


def support_frame() -> object:
    from bridge.capture import FrameRecord

    png = support.screencap_png()
    width, height = read_png_ihdr(png)
    return FrameRecord(
        captured_at_epoch_ms=int(EPOCH_SECONDS * 1000),
        width_hint=width,
        height_hint=height,
        png_bytes=png,
    )


@check("a sequence repeat and a sequence gap are refused as different failures")
def check_sequence_refusals() -> None:
    ledger = SequenceLedger()
    for seq_no in (1, 2, 3):
        ledger.accept(telemetry(seq_no))
    repeat = raises(EnvelopeInvalid, lambda: ledger.accept(telemetry(3)), "a repeat must be refused")
    assert isinstance(repeat, EnvelopeInvalid)
    expect_equal(repeat.code, BridgeErrorCode.ENVELOPE_SEQ_OUT_OF_ORDER, "repeat code")
    expect_equal(repeat.context["relation"], "repeat", "repeat is named as a repeat")
    gap = raises(EnvelopeInvalid, lambda: ledger.accept(telemetry(6)), "a gap must be refused")
    assert isinstance(gap, EnvelopeInvalid)
    expect_equal(gap.code, BridgeErrorCode.ENVELOPE_SEQ_GAP, "gap code")
    expect_equal(gap.context["missing_frames"], 2, "the gap size is stated")
    wrong_origin = raises(
        EnvelopeInvalid, lambda: SequenceLedger().accept(telemetry(4)), "a stream must start at 1"
    )
    assert isinstance(wrong_origin, EnvelopeInvalid)
    expect_equal(wrong_origin.code, BridgeErrorCode.ENVELOPE_SEQ_START_NOT_ONE, "origin code")


@check("a SURFACE_FRAME claiming an unagreed encoding is refused")
def check_unagreed_encoding() -> None:
    payload = {
        "encoding": "image/jpeg",
        "data_base64": base64.b64encode(support.screencap_png()).decode("ascii"),
        "width": 1080,
        "height": 2400,
    }
    error = raises(
        EnvelopeInvalid,
        lambda: AgentEnvelope(
            version=1, seq_no=1, timestamp=EPOCH_SECONDS, event_type=EventType.SURFACE_FRAME, payload=payload
        ),
        "an unagreed encoding must be refused",
    )
    assert isinstance(error, EnvelopeInvalid)
    expect_equal(error.code, BridgeErrorCode.ENVELOPE_ENCODING_UNAGREED, "encoding code")
    expect("image/png" in AGREED_IMAGE_ENCODINGS, "png is the agreed encoding")


@check("a frame above 512 KiB is refused on the way out and on the way in")
def check_oversized_frame() -> None:
    huge = telemetry(1)
    bloated = AgentEnvelope(
        version=1,
        seq_no=1,
        timestamp=EPOCH_SECONDS,
        event_type=EventType.TELEMETRY,
        payload={"blob": "x" * (MAX_FRAME_BYTES + 64)},
    )
    error = raises(FrameTooLarge, lambda: bloated.encode(), "outbound oversize must be refused")
    assert isinstance(error, FrameTooLarge)
    expect(error.declared_bytes > MAX_FRAME_BYTES, "the declared size is reported")
    del huge

    header = bytearray([0x82, 127])
    header.extend((MAX_FRAME_BYTES + 4096).to_bytes(8, "big"))
    stream = support.FakeStream(bytes(header) + b"y" * 4096)
    inbound = raises(
        FrameTooLarge, lambda: read_frame(stream, require_mask=False), "inbound oversize must be refused"
    )
    assert isinstance(inbound, FrameTooLarge)
    expect_equal(inbound.cap_bytes, MAX_FRAME_BYTES, "the cap is the agreed 512 KiB")
    expect_equal(stream.pending, b"y" * 4096, "the oversized payload must not be buffered")


@check("the WebSocket handshake derives the RFC 6455 example accept key")
def check_accept_key() -> None:
    accept = compute_accept_key("dGhlIHNhbXBsZSBub25jZQ==")
    expect_equal(accept, "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", "RFC 6455 section 1.3 example")


@check("a connection without the token is refused with 401 and no WebSocket")
def check_token_refusal() -> None:
    server = WebSocketServer(token=TOKEN)
    stream = support.FakeStream(support.handshake_request_bytes("/"))
    server.handle_stream(stream)
    expect(b"HTTP/1.1 401 Unauthorized" in stream.sent_bytes, "401 must be written")
    expect(b"101" not in stream.sent_bytes, "no WebSocket may open without a token")
    expect_equal(server.metrics["connections_refused_no_token"], 1, "the refusal is counted")


@check("a tokened handshake opens and serves a text frame")
def check_handshake_and_frame_exchange() -> None:
    seen: list[str] = []
    server = WebSocketServer(token=TOKEN, on_message=lambda connection, message: seen.append(message.text or ""))
    stream = support.FakeStream(
        support.handshake_request_bytes("/?token=" + TOKEN)
        + client_frame(b'{"version":1}')
        + support.render_frame(Opcode.CLOSE, b"", mask=True)
    )
    server.handle_stream(stream)
    expect(b"HTTP/1.1 101 Switching Protocols" in stream.sent_bytes, "101 must be written")
    expect(seen == ['{"version":1}'], "the frame must reach the handler, got " + repr(seen))
    replies = server_replies(stream)
    expect(replies and replies[-1][0] is Opcode.CLOSE, "the close handshake must complete")


@check("an unmasked client frame fails the connection with 1002")
def check_unmasked_frame_protocol_error() -> None:
    server = WebSocketServer(token=TOKEN)
    unmasked = support.render_frame(Opcode.TEXT, b"lazy", mask=False)
    stream = support.FakeStream(support.handshake_request_bytes("/?token=" + TOKEN) + unmasked)
    server.handle_stream(stream)
    replies = server_replies(stream)
    expect(bool(replies) and replies[0][0] is Opcode.CLOSE, "a protocol violation must be closed out")
    status = int.from_bytes(replies[0][1][:2], "big")
    expect_equal(status, int(CloseCode.PROTOCOL_ERROR), "close status 1002")
    expect_equal(server.metrics["protocol_errors"], 1, "the violation is counted")


@check("an oversized inbound frame fails the connection with 1009 rather than buffering")
def check_oversized_close_code() -> None:
    server = WebSocketServer(token=TOKEN, on_message=lambda connection, message: None)
    oversized = bytearray([0x82, 0xFF])
    oversized.extend((MAX_FRAME_BYTES + 1).to_bytes(8, "big"))
    oversized.extend(b"\x00\x00\x00\x00")
    stream = support.FakeStream(support.handshake_request_bytes("/?token=" + TOKEN) + bytes(oversized))
    server.handle_stream(stream)
    replies = server_replies(stream)
    expect(bool(replies) and replies[0][0] is Opcode.CLOSE, "the connection must be closed")
    expect_equal(int.from_bytes(replies[0][1][:2], "big"), int(CloseCode.MESSAGE_TOO_BIG), "close status 1009")
    expect_equal(server.metrics["frames_too_large"], 1, "the oversize is counted")


@check("the replay window keeps the last 200 envelopes in order after wraparound")
def check_replay_ordering() -> None:
    buffer = ReplayBuffer()
    expect_equal(buffer.capacity, REPLAY_CAPACITY_DEFAULT, "capacity")
    for seq_no in range(1, 251):
        buffer.append(telemetry(seq_no))
    snapshot = buffer.snapshot()
    expect_equal(len(snapshot), 200, "retained count")
    expect_equal([entry.seq_no for entry in snapshot][0], 51, "oldest retained")
    expect_equal([entry.seq_no for entry in snapshot][-1], 250, "newest retained")
    expect_equal(buffer.evictions, 50, "evictions are counted")
    expect_equal([entry.seq_no for entry in buffer.replay_since(245)], [246, 247, 248, 249, 250], "replay order")
    expect(buffer.is_gap_before_replay_window(10), "an evicted window must be reported as a gap")
    expect(not buffer.is_gap_before_replay_window(200), "an in-window position is not a gap")


@check("the gateway counts a rejected frame and answers it instead of dropping it")
def check_gateway_rejection_accounting() -> None:
    logger_lines: list[str] = []
    gateway = Gateway(token=TOKEN, logger=logger_lines.append, max_rejections_per_connection=8)
    stream = support.FakeStream(
        support.handshake_request_bytes("/?token=" + TOKEN)
        + client_frame(telemetry(1).encode().encode("utf-8"))
        + client_frame(b"{ not json")
        + client_frame(telemetry(2).encode().encode("utf-8"))
    )
    gateway.handle_stream(stream)
    expect_equal(gateway.metrics.envelopes_accepted, 2, "two good frames")
    expect_equal(gateway.metrics.envelopes_rejected, 1, "one refused frame")
    expect_equal(
        gateway.metrics.rejections_by_code[BridgeErrorCode.ENVELOPE_NOT_JSON.value], 1, "filed under its code"
    )
    notices = [
        json.loads(payload.decode("utf-8"))
        for opcode, payload in server_replies(stream)
        if opcode is Opcode.TEXT
    ]
    expect(len(notices) == 1, "the peer must receive exactly one notice, got " + str(notices))
    expect_equal(notices[0]["error_code"], "envelope_not_json", "notice code")
    expect_equal(notices[0]["last_accepted_seq_no"], 1, "the notice states the sequence position")
    expect(bool(logger_lines), "the rejection must be logged, not silent")
    gateway.stop()


@check("input text escaping: spaces become %s and unsafe characters refuse the action")
def check_text_escaping() -> None:
    expect_equal(escape_input_text("probe one two"), "probe%sone%stwo", "space escape")
    for value in ("probe one", "a.b/c:d+e@f,g-h_i"):
        escaped = escape_input_text(value)
        expect(" " not in escaped, "an escaped argument must contain no space")
        for metacharacter in ("&", ";", "|", "$", "`", "<", ">", "*", "?", "[", "]", "{", "}", "!", "#", "~", "'", '"', "\\"):
            expect(metacharacter not in escaped, repr(metacharacter) + " must not survive escaping")
        expect_equal(escaped.replace("%s", " "), value, "the escape is lossless for accepted input")
    for value in ("probe;rm", "probe $(id)", "caf\u00e9", "100%", "probe\n"):
        raises(InputRejected, lambda value=value: escape_input_text(value), repr(value) + " must be refused")


@check("keyevent validation bounds the code before it can reach the device")
def check_keycode_validation() -> None:
    expect_equal(keycode_to_int(4), 4, "a documented code is accepted")
    expect_equal(keycode_to_int(KEYEVENT_MAX), KEYEVENT_MAX, "the envelope maximum is accepted")
    for bad in (-1, KEYEVENT_MAX + 1, 70000):
        raises(InputRejected, lambda bad=bad: keycode_to_int(bad), str(bad) + " must be refused")
    raises(InputRejected, lambda: keycode_to_int(True), "a bool must be refused")


@check("a tap sent through the gate addresses the resolved centre")
def check_tap_command_shape() -> None:
    transport, runner, gate = gated_transport(HARNESS_PACKAGE)
    outcome = tap(transport, element_by_id("bridge_probe_button"), gate)
    expect_equal(outcome.coordinate, (540, 254), "coordinate")
    expect_equal(
        outcome.argv,
        ("adb", "-s", "BRIDGEFA0001", "shell", "input", "tap", "540", "254"),
        "argv",
    )
    expect(runner.calls[0][-2:] == ["dumpsys", "window"], "the gate ran first")


@check("launch addresses the harness component and nothing else")
def check_start_activity_command() -> None:
    transport, runner = support.make_transport()
    runner.on("shell", "am", "start", stdout=b"Starting: Intent { cmp=com.omnibuds.tools.shell/.ShellActivity }\n")
    outcome = transport.start_activity(HARNESS_PACKAGE, HARNESS_ACTIVITY)
    expect_equal(
        outcome.argv,
        (
            "adb",
            "-s",
            "BRIDGEFA0001",
            "shell",
            "am",
            "start",
            "-n",
            HARNESS_PACKAGE + "/" + HARNESS_ACTIVITY,
        ),
        "the component must be the harness activity",
    )
    expect(HARNESS_PACKAGE in outcome.argv[-1], "the launched package is the harness")
    expect(not any("force-stop" in call for call in runner.calls), "a launch must not stop anything")


@check("a gated capture streams end to end as sequenced SURFACE_FRAME envelopes")
def check_frame_streaming_end_to_end() -> None:
    from bridge.capture import ScreenCaptureSession
    from bridge.gateway import BridgeClient, stream_frames

    transport, runner, gate = gated_transport(HARNESS_PACKAGE)
    session = ScreenCaptureSession(transport, gate, capacity=3, clock=lambda: EPOCH_SECONDS)
    peer = support.FakeStream(auto_accept_handshake=True)
    client = BridgeClient(
        port=8777,
        token=TOKEN,
        stream_factory=lambda host, port: peer,
        sleep=lambda seconds: None,
        logger=lambda message: None,
    )
    client.connect()
    published = stream_frames(session, client, count=2, start_seq_no=1, timestamp=lambda: EPOCH_SECONDS)
    expect_equal([entry.seq_no for entry in published], [1, 2], "sequence assigned in order")
    expect_equal([entry.event_type for entry in published], [EventType.SURFACE_FRAME, EventType.SURFACE_FRAME], "event type")
    frames = []
    head_end = peer.sent_bytes.find(b"\r\n\r\n")
    expect(head_end >= 0, "the client wrote a handshake")
    probe = support.FakeStream(peer.sent_bytes[head_end + 4:])
    try:
        while True:
            frames.append(read_frame(probe, require_mask=True))
    except TransportClosed:
        pass
    expect_equal(len(frames), 2, "two frames on the wire")
    body = json.loads(frames[0].payload.decode("utf-8"))
    expect_equal(body["event_type"], "SURFACE_FRAME", "wire event type")
    expect_equal(base64.b64decode(body["payload"]["data_base64"]), support.screencap_png(), "frame bytes are the capture")
    expect(gate.transport is transport, "the gate vouched for the device that was captured")
    capture_reads = [call for call in runner.calls if "screencap" in call]
    expect_equal(len(capture_reads), 2, "one gated capture per streamed frame")
    focus_reads = [call for call in runner.calls if "dumpsys" in call]
    expect_equal(len(focus_reads), 2, "the gate re-reads focus for every frame; it is never cached")
    client.close()
    session.close()


@check("the bridge binds loopback only and refuses any other address")
def check_loopback_only() -> None:
    raises(ValueError, lambda: WebSocketServer(token=TOKEN, host="0.0.0.0"), "0.0.0.0 must be refused")
    raises(ValueError, lambda: WebSocketServer(token=TOKEN, host=""), "a wildcard empty bind must be refused")
    raises(ValueError, lambda: WebSocketClient(port=1, token=TOKEN, host="0.0.0.0"), "client must refuse 0.0.0.0")
    raises(ValueError, lambda: Gateway(token=TOKEN, host="0.0.0.0").start(), "the gateway must refuse 0.0.0.0")
    server = WebSocketServer(token=TOKEN)
    expect_equal(LOOPBACK_HOST, "127.0.0.1", "the only bind address named by the server")


@check("the reconnect schedule is exponential, capped and bounded")
def check_backoff_schedule() -> None:
    schedule = BackoffSchedule(initial_delay_s=0.25, factor=2.0, max_delay_s=2.0, max_attempts=6)
    expect_equal(schedule.schedule(), (0.0, 0.25, 0.5, 1.0, 2.0, 2.0), "doubling with a ceiling")
    raises(ValueError, lambda: BackoffSchedule(max_attempts=0), "an unbounded budget must be refused")
    raises(ValueError, lambda: BackoffSchedule(factor=0.5), "a shrinking backoff must be refused")


@check("the frame ring buffer is bounded and counts its evictions")
def check_frame_ring_buffer() -> None:
    buffer = FrameRingBuffer(5)
    from bridge.capture import FrameRecord

    png = support.screencap_png()
    for index in range(9):
        buffer.push(
            FrameRecord(
                captured_at_epoch_ms=index,
                width_hint=1080,
                height_hint=2400,
                png_bytes=png,
            )
        )
    expect_equal(len(buffer), 5, "bounded at five frames")
    expect_equal(buffer.evictions, 4, "evictions counted")
    expect_equal([frame.captured_at_epoch_ms for frame in buffer.snapshot()], [4, 5, 6, 7, 8], "oldest first")


@check("the source tree uses only the standard library and never a local shell")
def check_source_hygiene() -> None:
    modules = [
        "bridge/__init__.py",
        "bridge/errors.py",
        "bridge/adb.py",
        "bridge/capture.py",
        "bridge/hierarchy.py",
        "bridge/input_actions.py",
        "bridge/envelope.py",
        "bridge/ws_server.py",
        "bridge/gateway.py",
    ]
    local_top_level = {"bridge", "support", "verify_suite"}
    subprocess_entries = {"run", "Popen", "call", "check_output", "check_call"}
    for relative in modules:
        path = _ROOT / relative
        expect(path.is_file(), "expected module " + str(path))
        source = path.read_text(encoding="utf-8")
        tree = ast.parse(source, filename=relative)
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                for alias in node.names:
                    expect(
                        alias.name.split(".")[0] in local_top_level
                        or alias.name.split(".")[0] in sys.stdlib_module_names,
                        relative + " imports " + alias.name + ", which is not standard library",
                    )
            if isinstance(node, ast.ImportFrom) and node.module and node.level == 0:
                expect(
                    node.module.split(".")[0] in local_top_level
                    or node.module.split(".")[0] in sys.stdlib_module_names,
                    relative + " imports from " + node.module,
                )
            if isinstance(node, ast.Call) and getattr(node.func, "attr", "") == "bind":
                rendered = ast.unparse(node)
                expect("LOOPBACK_HOST" in rendered, relative + " binds something other than loopback: " + rendered)
            if isinstance(node, ast.Call) and getattr(node.func, "attr", getattr(node.func, "id", "")) in subprocess_entries:
                for keyword in node.keywords:
                    if keyword.arg == "shell":
                        value = getattr(keyword.value, "value", None)
                        expect(value is False, relative + " line " + str(node.lineno) + " enables a local shell")
                first = node.args[0] if node.args else None
                expect(
                    isinstance(first, (ast.List, ast.Tuple, ast.Name, ast.Call, ast.Subscript, ast.Attribute)),
                    relative + " line " + str(node.lineno) + " passes a string command to a subprocess call",
                )


@check("no file is created under the bridge tree while these checks run")
def check_no_persistence() -> None:
    before = {path for path in _ROOT.rglob("*") if path.is_file() and "__pycache__" not in path.parts}

    def exercise() -> None:
        transport, runner, gate = gated_transport(HARNESS_PACKAGE)
        capture_frame(transport, gate)
        buffer = FrameRingBuffer(5)
        png = support.screencap_png()
        from bridge.capture import FrameRecord

        buffer.push(
            FrameRecord(captured_at_epoch_ms=1, width_hint=1080, height_hint=2400, png_bytes=png)
        )
        AgentEnvelope(
            version=1,
            seq_no=1,
            timestamp=EPOCH_SECONDS,
            event_type=EventType.TELEMETRY,
            payload={"probe": 1},
        ).encode()

    exercise()
    after = {path for path in _ROOT.rglob("*") if path.is_file() and "__pycache__" not in path.parts}
    new = sorted(str(path.relative_to(_ROOT)) for path in (after - before))
    expect_equal(new, [], "the bridge must not persist anything under the project directory")


# --------------------------------------------------------------------------- runner


def main(argv: list[str] | None = None) -> int:
    del argv  # the suite takes no options: it either runs or it does not
    print("OmniBuds device bridge - offline self-check")
    print("fixtures: " + str(support.FIXTURES))
    print("device commands executed: 0 (no adb, no gradlew, no emulator)")
    print("")
    passed: list[str] = []
    failed: list[tuple[str, str]] = []
    for name, function in CHECKS:
        try:
            function()
        except AssertionError as exc:
            failed.append((name, str(exc)))
            print("FAIL  " + name)
            print("      " + str(exc).splitlines()[0])
        except Exception as exc:  # an unexpected raise is a failure, never a pass
            failed.append((name, type(exc).__name__ + ": " + str(exc)))
            print("FAIL  " + name)
            print("      unexpected " + type(exc).__name__ + ": " + str(exc).splitlines()[0])
        else:
            passed.append(name)
            print("PASS  " + name)
    print("")
    print("checked: " + str(len(CHECKS)) + "  passed: " + str(len(passed)) + "  failed: " + str(len(failed)))
    if failed:
        print("RESULT: FAIL")
        for name, detail in failed:
            print("  - " + name + " :: " + detail)
        return 1
    print("RESULT: PASS")
    print(
        "Scope note: every check above ran against fixtures and a fake transport. Nothing here is"
        " evidence about the phone; see README.md section 'What is NOT verified'."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
