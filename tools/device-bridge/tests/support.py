"""Shared test scaffolding: fixture loading and a fake ADB transport.

Nothing in here touches a device or a build tool. `FakeAdbRunner` stands in for the subprocess seam
of `bridge.adb.AdbTransport`, records every argv it was handed, and raises on an unscripted command -
a test that quietly got `b""` back from an unexpected command would otherwise assert about nothing.

The path bootstrap at the top exists because this tree is not an installed distribution: it makes
`python -m unittest discover -s tools/device-bridge/tests` work from the repository root without a
`setup.py`, which the standard-library-only constraint rules out anyway.
"""

from __future__ import annotations

import struct
import sys
import zlib
from pathlib import Path
from typing import Callable

_PACKAGE_ROOT = Path(__file__).resolve().parents[1]
_TESTS_DIR = Path(__file__).resolve().parent
for _entry in (str(_PACKAGE_ROOT), str(_TESTS_DIR)):
    if _entry not in sys.path:
        sys.path.insert(0, _entry)

from bridge.adb import AdbTransport, CommandResult  # noqa: E402  (bootstrap runs before package imports)
from bridge.ws_server import (  # noqa: E402
    Opcode,
    compute_accept_key,
    write_frame,
)

FIXTURES = _TESTS_DIR / "fixtures"
HARNESS_SERIAL = "BRIDGEFA0001"


def fixture_bytes(name: str) -> bytes:
    path = FIXTURES / name
    if not path.is_file():
        raise FileNotFoundError("missing fixture " + str(path))
    return path.read_bytes()


def fixture_text(name: str) -> str:
    return fixture_bytes(name).decode("utf-8")


def uiautomator_dump_text() -> str:
    return fixture_text("uiautomator_shell.xml")


def adb_devices_listing() -> str:
    return fixture_text("adb_devices.txt")


def getprop_dump() -> str:
    return fixture_text("getprop_output.txt")


def window_focus_dump() -> str:
    return fixture_text("window_focus.txt")


def screencap_png() -> bytes:
    return fixture_bytes("screencap_header.png")


def build_png(width: int, height: int) -> bytes:
    """A minimal but structurally valid PNG: signature, IHDR, IEND.

    Enough for `read_png_ihdr` including the CRC it verifies. No IDAT, because the bridge never
    decodes pixels - it reads the header and streams bytes it does not interpret.
    """
    signature = b"\x89PNG\r\n\x1a\n"
    ihdr_payload = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    ihdr = struct.pack(">I", len(ihdr_payload)) + b"IHDR" + ihdr_payload
    ihdr += struct.pack(">I", zlib.crc32(b"IHDR" + ihdr_payload) & 0xFFFFFFFF)
    iend = struct.pack(">I", 0) + b"IEND" + struct.pack(">I", zlib.crc32(b"IEND") & 0xFFFFFFFF)
    return signature + ihdr + iend


class UnscriptedCommand(AssertionError):
    """Raised when the bridge sends a command the test did not authorise.

    This is the assertion that keeps "the bridge never ran adb install -g" true: an unexpected
    command fails the run rather than returning an empty success.
    """


class FakeAdbRunner:
    """A `CommandRunner` double: exact-suffix scripting, full call recording.

    Registration is by command tail, e.g. `on("shell", "getprop", "ro.build.version.sdk",
    stdout=b"34")`, so a test asserts on the argv the bridge built rather than on a mock's idea of
    it. Responses are returned in registration order when a pattern matches more than once, and a
    pattern that has been consumed stops matching unless it was registered with `repeat=True`.
    """

    def __init__(self, adb_path: str = "adb") -> None:
        self.adb_path = adb_path
        self.calls: list[list[str]] = []
        self._scripts: list[_ScriptedCommand] = []

    def on(
        self,
        *suffix: str,
        returncode: int = 0,
        stdout: bytes = b"",
        stderr: bytes = b"",
        repeat: bool = False,
        handler: Callable[[list[str]], CommandResult] | None = None,
    ) -> "FakeAdbRunner":
        self._scripts.append(
            _ScriptedCommand(
                suffix=tuple(suffix),
                returncode=returncode,
                stdout=stdout,
                stderr=stderr,
                repeat=repeat,
                handler=handler,
                consumed=False,
            )
        )
        return self

    def on_shell_value(self, key: str, value: str) -> "FakeAdbRunner":
        """Script one targeted `getprop <key>` read, in the bare-value shape ADB returns."""
        return self.on("shell", "getprop", key, stdout=value.encode("utf-8") + b"\n", repeat=True)

    def __call__(self, argv: list[str], *, timeout_s: float) -> CommandResult:
        if not isinstance(argv, list) or not all(isinstance(part, str) for part in argv):
            raise AssertionError("the bridge must pass an argv list of strings, got " + repr(argv))
        self.calls.append(list(argv))
        for script in self._scripts:
            if script.matches(argv):
                script.consumed = True
                if script.handler is not None:
                    return script.handler(argv)
                return CommandResult(
                    returncode=script.returncode,
                    stdout=script.stdout,
                    stderr=script.stderr,
                )
        raise UnscriptedCommand(
            "unscripted adb command: " + " ".join(argv) + "; the test did not authorise this device "
            "interaction"
        )

    @property
    def joined_calls(self) -> tuple[str, ...]:
        return tuple(" ".join(call) for call in self.calls)

    def commands_matching(self, *suffix: str) -> tuple[tuple[str, ...], ...]:
        """Every recorded call whose argv contains `suffix` contiguously.

        Lets a test say "no `-g` was ever passed to install" instead of eyeballing the list.
        """
        window = len(suffix)
        found: list[tuple[str, ...]] = []
        for call in self.calls:
            for start in range(max(0, len(call) - window + 1)):
                if tuple(call[start : start + window]) == suffix:
                    found.append(tuple(call))
                    break
        return tuple(found)


class _ScriptedCommand:
    def __init__(
        self,
        *,
        suffix: tuple[str, ...],
        returncode: int,
        stdout: bytes,
        stderr: bytes,
        repeat: bool,
        handler: Callable[[list[str]], CommandResult] | None,
        consumed: bool,
    ) -> None:
        self.suffix = suffix
        self.returncode = returncode
        self.stdout = stdout
        self.stderr = stderr
        self.repeat = repeat
        self.handler = handler
        self.consumed = consumed

    def matches(self, argv: list[str]) -> bool:
        """True when the scripted tokens appear contiguously anywhere in the argv.

        The `-s <serial>` prefix and the trailing argument of a command are both irrelevant to what
        a test is authorising, so a script for `("shell", "input")` covers `input tap`, `input text`
        and `input keyevent`, while a script for a full `("install", "-r", path)` still matches only
        that command.
        """
        if self.consumed and not self.repeat:
            return False
        if not self.suffix:
            return False
        window = len(self.suffix)
        if len(argv) < window:
            return False
        for start in range(len(argv) - window + 1):
            if tuple(argv[start : start + window]) == self.suffix:
                return True
        return False


def make_transport(
    serial: str = HARNESS_SERIAL,
    runner: FakeAdbRunner | None = None,
) -> tuple[AdbTransport, FakeAdbRunner]:
    """A real `AdbTransport` over a fake runner. The runner is the only substituted part."""
    fake = runner if runner is not None else FakeAdbRunner()
    transport = AdbTransport(serial=serial, adb_path=fake.adb_path, runner=fake, timeout_s=5.0)
    return transport, fake


class FakeStream:
    """An in-memory `ByteStream`, so the protocol layer is tested without a socket or a listener.

    `auto_accept_handshake` makes it behave like a real RFC 6455 server for the client-side tests:
    the first complete `GET` request written to it produces a `101` response carrying the accept key
    derived from the key that was actually sent, which is what `WebSocketClient` verifies.
    """

    def __init__(self, inbound: bytes = b"", *, auto_accept_handshake: bool = False) -> None:
        self._inbound = bytearray(inbound)
        self.sent = bytearray()
        self.closed = False
        self.shutdown_flags: list[int] = []
        self.read_calls = 0
        self.fail_send: bool = False
        self._auto_accept = auto_accept_handshake
        self._responded = False

    # ------------------------------------------------------------------ ByteStream surface

    def recv(self, bufsize: int) -> bytes:
        self.read_calls += 1
        if not self._inbound and self._auto_accept and not self._responded:
            self._maybe_respond()
        if not self._inbound:
            return b""
        take = bytes(self._inbound[:bufsize])
        del self._inbound[: len(take)]
        return take

    def sendall(self, data: bytes) -> None:
        if self.fail_send:
            raise OSError("simulated transport failure")
        self.sent.extend(data)
        if self._auto_accept and not self._responded:
            self._maybe_respond()

    def shutdown(self, how: int) -> None:
        self.shutdown_flags.append(int(how))

    def close(self) -> None:
        self.closed = True

    # ------------------------------------------------------------------ test helpers

    def feed(self, data: bytes) -> None:
        self._inbound.extend(data)

    @property
    def pending(self) -> bytes:
        """Bytes the peer has sent that this side has not read yet."""
        return bytes(self._inbound)

    @property
    def sent_bytes(self) -> bytes:
        return bytes(self.sent)

    def _maybe_respond(self) -> None:
        marker = b"\r\n\r\n"
        if marker not in self.sent:
            return
        head = bytes(self.sent).split(marker, 1)[0].decode("latin-1")
        key: str | None = None
        for line in head.split("\r\n")[1:]:
            name, _, value = line.partition(":")
            if name.strip().lower() == "sec-websocket-key":
                key = value.strip()
        self._responded = True
        if key is None:
            self._inbound.extend(b"HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\n")
            return
        accept = compute_accept_key(key)
        self._inbound.extend(
            (
                "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                "Sec-WebSocket-Accept: " + accept + "\r\n\r\n"
            ).encode("ascii")
        )


def handshake_request_bytes(
    path: str = "/",
    *,
    key: str = "dGhlIHNhbXBsZSBub25jZQ==",
    version: str = "13",
    upgrade: str = "websocket",
    connection: str = "Upgrade",
    authorization: str | None = None,
    method: str = "GET",
) -> bytes:
    """A complete opening handshake, as a client would send it."""
    lines = [
        method + " " + path + " HTTP/1.1",
        "Host: 127.0.0.1:8777",
        "Upgrade: " + upgrade,
        "Connection: " + connection,
        "Sec-WebSocket-Key: " + key,
        "Sec-WebSocket-Version: " + version,
    ]
    if authorization is not None:
        lines.append("Authorization: " + authorization)
    return ("\r\n".join(lines) + "\r\n\r\n").encode("ascii")


def render_frame(opcode: Opcode, payload: bytes, *, mask: bool, fin: bool = True) -> bytes:
    """Encode one frame through the production writer, for feeding a reader."""
    sink = FakeStream()
    write_frame(sink, opcode, payload, mask=mask, fin=fin)
    return sink.sent_bytes


__all__ = [
    "CommandResult",
    "FakeAdbRunner",
    "FakeStream",
    "UnscriptedCommand",
    "HARNESS_SERIAL",
    "FIXTURES",
    "fixture_bytes",
    "fixture_text",
    "uiautomator_dump_text",
    "adb_devices_listing",
    "getprop_dump",
    "window_focus_dump",
    "screencap_png",
    "build_png",
    "make_transport",
    "handshake_request_bytes",
    "render_frame",
]
