"""Screen capture, the foreground gate that authorises it, and where frames are allowed to live.

The policy this module implements
---------------------------------
`docs/security/device-access-policy.md` names "screen recording, scrcpy, `screencap` beyond our own
app's window when it would capture other apps' content" as an action that requires asking the user
first, every time. The bridge does not ask mid-run, so instead it never captures when our harness
app is not the foreground window: `ForegroundGate.assert_foreground()` runs before every capture
and before every input action, and it fails closed when focus is unreadable.

Frames are held in memory only, in a bounded ring (`FrameRingBuffer`). Nothing in this module opens
a file: a screenshot of a personal phone written to disk is a copy of that phone's screen surviving
the run, and there is no phase that needs one.

The clock is injected, never read ambiently: `captured_at_epoch_ms` is derived from a caller-supplied
`clock` callable so the suite is deterministic (Phase 1 `specs.md` section 2.4).
"""

from __future__ import annotations

import struct
import time
import zlib
from dataclasses import dataclass
from typing import Callable, Iterable

from .adb import AdbTransport, PNG_SIGNATURE
from .errors import AdbFailed, BridgeErrorCode, NotForeground

#: Default frame retention. Five frames is the smallest number that still shows a before/after pair
#: for a tap and a redraw, while keeping the worst-case footprint at roughly five PNGs.
DEFAULT_FRAME_CAPACITY = 5

#: Hard ceiling on the ring. A larger buffer is a memory-leak request wearing a tuning knob's
#: clothes; a caller who needs more should be streaming, not retaining.
MAX_FRAME_CAPACITY = 64


class ForegroundGate:
    """Refuses device interaction unless the harness package holds window focus.

    No caching: every `assert_foreground()` re-reads focus from the device. A remembered
    "was foreground" is precisely how a capture ends up being somebody else's home screen, and
    focus changes without notifying us.
    """

    def __init__(self, transport: AdbTransport, expected_package: str) -> None:
        if not expected_package or not expected_package.strip():
            raise ValueError("ForegroundGate requires an explicit expected package")
        self._transport = transport
        self._expected_package = expected_package.strip()

    @property
    def transport(self) -> AdbTransport:
        return self._transport

    @property
    def expected_package(self) -> str:
        return self._expected_package

    def assert_foreground(self) -> str:
        """Return the observed package when it is ours; raise otherwise.

        Raises:
            NotForeground: focus belongs to another package. The message names both packages,
                because "which app is on screen right now" is the fact the operator needs, and it
                is the only fact about other apps this bridge is allowed to state.
            AdbFailed(FOCUS_UNRESOLVED): focus could not be read at all. Not converted into a
                refusal-with-a-reason string, and never into a pass.
        """
        observed = self._transport.current_focus_package()
        if observed != self._expected_package:
            raise NotForeground(
                "refusing device capture/input: foreground package is " + repr(observed)
                + ", not the harness package " + repr(self._expected_package)
                + "; bring the harness to the foreground with start_activity and retry",
                expected_package=self._expected_package,
                observed_package=observed,
                context={"expected_package": self._expected_package, "observed_package": observed},
            )
        return observed


@dataclass(frozen=True)
class FrameRecord:
    """One captured screen, in memory, with the facts needed to stream it.

    `width_hint` / `height_hint` are read from the PNG `IHDR` fields, so they describe the image
    bytes we hold rather than a display size we assumed. They are called hints because the image
    can be letterboxed or rotated relative to the panel; the bounds in a UI dump are the authority
    for coordinates, not these.

    `repr` prints sizes only. A frame's bytes must never reach a log (Phase 1 `specs.md` section 6:
    `toString()` is a disclosure surface).
    """

    captured_at_epoch_ms: int
    width_hint: int
    height_hint: int
    png_bytes: bytes

    def __post_init__(self) -> None:
        if self.captured_at_epoch_ms < 0:
            raise ValueError("captured_at_epoch_ms cannot be negative")
        if self.width_hint <= 0 or self.height_hint <= 0:
            raise ValueError("a frame with a non-positive dimension is not a frame")
        if not self.png_bytes:
            raise ValueError("a frame with no bytes is not a capture")

    @property
    def byte_size(self) -> int:
        return len(self.png_bytes)

    def __repr__(self) -> str:
        return (
            "FrameRecord(captured_at_epoch_ms="
            + str(self.captured_at_epoch_ms)
            + ", width_hint="
            + str(self.width_hint)
            + ", height_hint="
            + str(self.height_hint)
            + ", byte_size="
            + str(self.byte_size)
            + ")"
        )


def read_png_ihdr(png: bytes) -> tuple[int, int]:
    """Parse the `IHDR` chunk of a PNG and return `(width, height)`.

    Hand-rolled on purpose: the shape is fixed by ISO/IEC 15948 - 8-byte signature, then a chunk of
    4-byte big-endian length, 4-byte type, payload and 4-byte CRC - and pulling in an image library
    to read 8 bytes would put a dependency between the bridge and its own evidence.

    Raises:
        AdbFailed(ADB_UNUSABLE_OUTPUT): short input, wrong signature, wrong chunk order or type, an
            `IHDR` length other than 13, a CRC mismatch, or a non-positive/absurd dimension. Each
            one means the bytes we were handed are not a screen, and the caller must see that
            rather than a `0 x 0` frame.
    """
    if len(png) < len(PNG_SIGNATURE) + 8 + 13:
        raise _unusable("PNG stream is shorter than a signature plus an IHDR chunk", byte_count=len(png))
    if not png.startswith(PNG_SIGNATURE):
        raise _unusable("PNG signature missing", byte_count=len(png))
    length = int.from_bytes(png[8:12], "big")
    chunk_type = png[12:16]
    if chunk_type != b"IHDR":
        raise _unusable("first PNG chunk is not IHDR", first_chunk=chunk_type.decode("latin-1", errors="replace"))
    if length != 13:
        raise _unusable("IHDR payload length is not 13", ihdr_length=length)
    payload = png[16:29]
    stored_crc = int.from_bytes(png[29:33], "big")
    computed_crc = zlib.crc32(chunk_type + payload) & 0xFFFFFFFF
    if stored_crc != computed_crc:
        raise _unusable("IHDR CRC mismatch", stored_crc=stored_crc, computed_crc=computed_crc)
    width, height = struct.unpack(">II", payload[:8])
    if width <= 0 or height <= 0:
        raise _unusable("IHDR declares a non-positive image size", width=width, height=height)
    # 100000 px is far beyond any phone panel and far below any plausible corruption marker.
    if width > 100_000 or height > 100_000:
        raise _unusable("IHDR declares an implausible image size", width=width, height=height)
    return width, height


def _unusable(message: str, **context: object) -> AdbFailed:
    return AdbFailed(
        "captured bytes are not a usable PNG: " + message,
        code=BridgeErrorCode.ADB_UNUSABLE_OUTPUT,
        context=context,
    )


class FrameRingBuffer:
    """Bounded in-memory retention of the last `capacity` frames.

    Why eviction is explicit and counted: a PNG of a phone screen is on the order of hundreds of
    kilobytes to a few megabytes, so an append-only list is a memory leak with a nice API. The
    buffer therefore drops the oldest frame on push, hands it back to the caller, and counts the
    eviction, so "we lost frames" is a fact a run can be audited for instead of a thing that
    happened quietly.
    """

    def __init__(self, capacity: int = DEFAULT_FRAME_CAPACITY) -> None:
        if isinstance(capacity, bool) or not isinstance(capacity, int):
            raise TypeError("FrameRingBuffer capacity must be an int")
        if capacity < 1:
            raise ValueError("FrameRingBuffer capacity must be at least 1")
        if capacity > MAX_FRAME_CAPACITY:
            raise ValueError(
                "FrameRingBuffer capacity " + str(capacity) + " exceeds MAX_FRAME_CAPACITY ("
                + str(MAX_FRAME_CAPACITY) + "); retaining more frames is not this buffer's job"
            )
        self._capacity = capacity
        self._frames: list[FrameRecord] = []
        self._evictions = 0
        self._pushed = 0
        self._closed = False

    @property
    def capacity(self) -> int:
        return self._capacity

    @property
    def evictions(self) -> int:
        return self._evictions

    @property
    def pushed_total(self) -> int:
        return self._pushed

    @property
    def is_closed(self) -> bool:
        return self._closed

    def __len__(self) -> int:
        return len(self._frames)

    def push(self, frame: FrameRecord) -> FrameRecord | None:
        """Append `frame`, returning the frame it evicted, or `None` when nothing was evicted.

        Raises:
            ValueError: pushing into a closed buffer. The alternative is dropping the caller's
                frame and reporting success.
        """
        if self._closed:
            raise ValueError("FrameRingBuffer is closed; refusing to accept a frame nobody can read back")
        if not isinstance(frame, FrameRecord):
            raise TypeError("FrameRingBuffer holds FrameRecord values only")
        evicted: FrameRecord | None = None
        if len(self._frames) == self._capacity:
            evicted = self._frames.pop(0)
            self._evictions += 1
        self._frames.append(frame)
        self._pushed += 1
        return evicted

    def latest(self) -> FrameRecord:
        """The newest frame.

        Raises:
            LookupError: the buffer is empty. Returning `None` would let a caller that forgot to
                capture first carry on as if there were a frame.
        """
        if not self._frames:
            raise LookupError("frame buffer is empty; no capture has been taken in this session")
        return self._frames[-1]

    def oldest(self) -> FrameRecord:
        if not self._frames:
            raise LookupError("frame buffer is empty; no capture has been taken in this session")
        return self._frames[0]

    def snapshot(self) -> tuple[FrameRecord, ...]:
        """Oldest first. A copy, so a caller cannot mutate retention by holding the internal list."""
        return tuple(self._frames)

    def clear(self) -> None:
        """Drop retained frames without closing the buffer.

        Exposed because a capture session that has validated what it needed should give the memory
        back immediately rather than at process exit.
        """
        self._frames.clear()

    def close(self) -> None:
        """Release every frame and refuse further use. Idempotent."""
        self._frames.clear()
        self._closed = True

    def __enter__(self) -> "FrameRingBuffer":
        return self

    def __exit__(self, exc_type: object, exc: object, tb: object) -> None:
        self.close()

    def __iter__(self) -> Iterable[FrameRecord]:
        return iter(self.snapshot())

    def __repr__(self) -> str:
        return (
            "FrameRingBuffer(size=" + str(len(self._frames)) + ", capacity=" + str(self._capacity)
            + ", evictions=" + str(self._evictions) + ")"
        )


def capture_frame(
    transport: AdbTransport,
    gate: ForegroundGate,
    *,
    buffer: FrameRingBuffer | None = None,
    clock: Callable[[], float] = time.time,
) -> FrameRecord:
    """Capture one screen, but only with the foreground gate cleared first.

    Order is the whole point of the function: gate, then `screencap`. Reversing it would mean the
    policy decides after the personal data has already been read into memory.

    `buffer` is optional so a caller that wants a one-off frame can take one; when given, the frame
    is retained there as well as returned.

    Raises:
        ValueError: `gate.transport is not transport`. A gate that vouches for device A while
            capturing device B is a bug that the gate exists to stop.
        NotForeground / AdbFailed: from the gate, before any capture command is sent.
        AdbFailed: the capture failed or the bytes are not a PNG.
    """
    if gate.transport is not transport:
        raise ValueError(
            "the foreground gate must be built on the same transport the frame is captured from"
        )
    gate.assert_foreground()
    png = transport.screencap_png()
    width, height = read_png_ihdr(png)
    captured_at = _to_epoch_ms(clock())
    frame = FrameRecord(
        captured_at_epoch_ms=captured_at,
        width_hint=width,
        height_hint=height,
        png_bytes=png,
    )
    if buffer is not None:
        buffer.push(frame)
    return frame


def _to_epoch_ms(epoch_seconds: float) -> int:
    if not isinstance(epoch_seconds, (int, float)) or isinstance(epoch_seconds, bool):
        raise TypeError("clock callable must return epoch seconds as a number")
    seconds = float(epoch_seconds)
    if seconds != seconds or seconds in (float("inf"), float("-inf")):
        raise ValueError("a non-finite clock reading cannot become a timestamp")
    if seconds < 0:
        raise ValueError("epoch seconds must not be negative")
    return int(seconds * 1000)


class ScreenCaptureSession:
    """Transport + gate + ring buffer with one owner and one deterministic teardown.

    The context manager exists so a frame cannot outlive the read that made it: leaving the block
    closes the buffer (dropping every retained PNG) and closes the transport. There is no `__del__`
    and no background thread here; cleanup is a call, not a hope.
    """

    def __init__(
        self,
        transport: AdbTransport,
        gate: ForegroundGate,
        *,
        capacity: int = DEFAULT_FRAME_CAPACITY,
        clock: Callable[[], float] = time.time,
    ) -> None:
        if gate.transport is not transport:
            raise ValueError("the capture session's gate must vouch for the same transport it captures from")
        self._transport = transport
        self._gate = gate
        self._buffer = FrameRingBuffer(capacity)
        self._clock = clock
        self._closed = False

    @property
    def transport(self) -> AdbTransport:
        return self._transport

    @property
    def gate(self) -> ForegroundGate:
        return self._gate

    @property
    def buffer(self) -> FrameRingBuffer:
        return self._buffer

    @property
    def frames(self) -> tuple[FrameRecord, ...]:
        return self._buffer.snapshot()

    @property
    def is_closed(self) -> bool:
        return self._closed

    def capture(self) -> FrameRecord:
        """One gated capture into this session's ring.

        Raises:
            AdbFailed(TRANSPORT_CLOSED): the session has been closed. A capture after teardown must
                be an error, not a silent `None`.
        """
        if self._closed:
            raise AdbFailed(
                "this capture session is closed",
                code=BridgeErrorCode.TRANSPORT_CLOSED,
            )
        return capture_frame(self._transport, self._gate, buffer=self._buffer, clock=self._clock)

    def close(self) -> None:
        """Drop retained frames, then close the gate's transport. Idempotent, order-fixed."""
        if self._closed:
            return
        self._buffer.close()
        self._transport.close()
        self._closed = True

    def __enter__(self) -> "ScreenCaptureSession":
        return self

    def __exit__(self, exc_type: object, exc: object, tb: object) -> None:
        self.close()


__all__ = [
    "DEFAULT_FRAME_CAPACITY",
    "MAX_FRAME_CAPACITY",
    "ForegroundGate",
    "FrameRecord",
    "FrameRingBuffer",
    "ScreenCaptureSession",
    "capture_frame",
    "read_png_ihdr",
]
