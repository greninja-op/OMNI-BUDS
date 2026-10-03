"""`bridge.capture`: the foreground gate, the PNG header read, and bounded frame retention.

The gate tests assert the absence of a command as well as the presence of an error. A capture that
was refused but still issued `exec-out screencap -p` would be a policy violation the test suite
would otherwise never see.
"""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

_HERE = Path(__file__).resolve().parent
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))

import support  # noqa: E402
from bridge import HARNESS_PACKAGE  # noqa: E402
from bridge.adb import AdbTransport  # noqa: E402
from bridge.capture import (  # noqa: E402
    DEFAULT_FRAME_CAPACITY,
    MAX_FRAME_CAPACITY,
    ForegroundGate,
    FrameRecord,
    FrameRingBuffer,
    ScreenCaptureSession,
    capture_frame,
    read_png_ihdr,
)
from bridge.errors import AdbFailed, BridgeErrorCode, NotForeground  # noqa: E402

PNG = support.screencap_png()


def gated_transport(focus_package: str = HARNESS_PACKAGE) -> tuple[AdbTransport, support.FakeAdbRunner]:
    transport, runner = support.make_transport()
    runner.on(
        "shell",
        "dumpsys",
        "window",
        stdout=(
            "  mCurrentFocus=Window{6d3b1c8 u0 "
            + focus_package
            + "/"
            + focus_package
            + ".ShellActivity}\n"
        ).encode("utf-8"),
        repeat=True,
    )
    runner.on("exec-out", "screencap", "-p", stdout=PNG, repeat=True)
    return transport, runner


class ForegroundGateTest(unittest.TestCase):
    def test_the_gate_passes_when_the_harness_is_foreground(self) -> None:
        transport, _ = gated_transport()
        gate = ForegroundGate(transport, HARNESS_PACKAGE)
        self.assertEqual(gate.assert_foreground(), HARNESS_PACKAGE)

    def test_the_gate_refuses_another_packages_screen_and_no_capture_is_sent(self) -> None:
        transport, runner = gated_transport(focus_package="com.example.foreigndemo")
        gate = ForegroundGate(transport, HARNESS_PACKAGE)
        with self.assertRaises(NotForeground) as caught:
            capture_frame(transport, gate)
        self.assertEqual(caught.exception.observed_package, "com.example.foreigndemo")
        self.assertEqual(caught.exception.expected_package, HARNESS_PACKAGE)
        self.assertEqual(caught.exception.code, BridgeErrorCode.NOT_FOREGROUND)
        self.assertEqual(len(runner.calls), 1, "only the focus read may happen; screencap must not")
        self.assertEqual(runner.calls[0][-2:], ["dumpsys", "window"])

    def test_an_unreadable_focus_refuses_the_capture(self) -> None:
        transport, runner = support.make_transport()
        runner.on("shell", "dumpsys", "window", stdout=b"WINDOW MANAGER DATA\n  mCurrentFocus=null\n")
        runner.on("exec-out", "screencap", "-p", stdout=PNG)
        gate = ForegroundGate(transport, HARNESS_PACKAGE)
        with self.assertRaises(AdbFailed) as caught:
            capture_frame(transport, gate)
        self.assertEqual(caught.exception.code, BridgeErrorCode.FOCUS_UNRESOLVED)
        self.assertEqual(len(runner.calls), 1)

    def test_the_gate_re_reads_focus_on_every_call(self) -> None:
        transport, runner = gated_transport()
        gate = ForegroundGate(transport, HARNESS_PACKAGE)
        gate.assert_foreground()
        gate.assert_foreground()
        self.assertEqual(len(runner.calls), 2)

    def test_a_gate_without_an_expected_package_is_refused_at_construction(self) -> None:
        transport, _ = support.make_transport()
        with self.assertRaises(ValueError):
            ForegroundGate(transport, "  ")

    def test_capturing_through_a_gate_that_vouches_for_another_transport_is_refused(self) -> None:
        transport, _ = gated_transport()
        other, _ = support.make_transport(serial="OTHERSERIAL")
        gate = ForegroundGate(transport, HARNESS_PACKAGE)
        with self.assertRaises(ValueError):
            capture_frame(other, gate)


class CaptureFrameTest(unittest.TestCase):
    def test_a_frame_carries_the_stamp_dimensions_and_bytes(self) -> None:
        transport, runner = gated_transport()
        gate = ForegroundGate(transport, HARNESS_PACKAGE)
        frame = capture_frame(transport, gate, clock=lambda: 1_777_000_000.5)
        self.assertEqual(frame.captured_at_epoch_ms, 1_777_000_000_500)
        self.assertEqual(frame.width_hint, 1080)
        self.assertEqual(frame.height_hint, 2400)
        self.assertEqual(frame.png_bytes, PNG)
        self.assertEqual(frame.byte_size, len(PNG))
        self.assertEqual(runner.calls[-1][-2:], ["screencap", "-p"])

    def test_a_frame_can_be_retained_in_a_buffer_as_it_is_taken(self) -> None:
        transport, _ = gated_transport()
        gate = ForegroundGate(transport, HARNESS_PACKAGE)
        buffer = FrameRingBuffer(5)
        frame = capture_frame(transport, gate, buffer=buffer)
        self.assertEqual(buffer.snapshot(), (frame,))

    def test_a_clock_that_returns_a_non_number_is_refused(self) -> None:
        transport, _ = gated_transport()
        gate = ForegroundGate(transport, HARNESS_PACKAGE)
        with self.assertRaises(TypeError):
            capture_frame(transport, gate, clock=lambda: "yesterday")

    def test_a_clock_that_returns_nan_is_refused(self) -> None:
        transport, _ = gated_transport()
        gate = ForegroundGate(transport, HARNESS_PACKAGE)
        with self.assertRaises(ValueError):
            capture_frame(transport, gate, clock=lambda: float("nan"))

    def test_the_repr_of_a_frame_prints_sizes_and_never_pixels(self) -> None:
        frame = FrameRecord(
            captured_at_epoch_ms=1,
            width_hint=1080,
            height_hint=2400,
            png_bytes=PNG,
        )
        printed = repr(frame)
        self.assertIn("byte_size=" + str(len(PNG)), printed)
        self.assertNotIn("IHDR", printed)
        self.assertNotIn("png_bytes=b'", printed)

    def test_a_frame_record_refuses_impossible_values(self) -> None:
        with self.assertRaises(ValueError):
            FrameRecord(captured_at_epoch_ms=-1, width_hint=10, height_hint=10, png_bytes=PNG)
        with self.assertRaises(ValueError):
            FrameRecord(captured_at_epoch_ms=1, width_hint=0, height_hint=10, png_bytes=PNG)
        with self.assertRaises(ValueError):
            FrameRecord(captured_at_epoch_ms=1, width_hint=10, height_hint=10, png_bytes=b"")


class PngHeaderTest(unittest.TestCase):
    def test_the_fixture_header_reads_its_declared_size(self) -> None:
        self.assertEqual(read_png_ihdr(PNG), (1080, 2400))

    def test_a_build_png_round_trips_through_the_parser(self) -> None:
        self.assertEqual(read_png_ihdr(support.build_png(720, 1560)), (720, 1560))

    def test_a_stream_without_the_signature_is_refused(self) -> None:
        with self.assertRaises(AdbFailed) as caught:
            read_png_ihdr(b"JPEG" + b"\x00" * 40)
        self.assertEqual(caught.exception.code, BridgeErrorCode.ADB_UNUSABLE_OUTPUT)

    def test_a_truncated_stream_is_refused(self) -> None:
        with self.assertRaises(AdbFailed):
            read_png_ihdr(PNG[:12])

    def test_a_first_chunk_that_is_not_ihdr_is_refused(self) -> None:
        tampered = PNG[:12] + b"iTXt" + PNG[16:]
        with self.assertRaises(AdbFailed):
            read_png_ihdr(tampered)

    def test_a_corrupt_ihdr_crc_is_refused(self) -> None:
        tampered = bytearray(PNG)
        tampered[32] ^= 0xFF
        with self.assertRaises(AdbFailed) as caught:
            read_png_ihdr(bytes(tampered))
        self.assertIn("CRC", caught.exception.message)

    def test_an_absurd_declared_size_is_refused(self) -> None:
        with self.assertRaises(AdbFailed):
            read_png_ihdr(support.build_png(2_000_000, 2_000_000))


class FrameRingBufferTest(unittest.TestCase):
    def _frame(self, stamp: int) -> FrameRecord:
        return FrameRecord(
            captured_at_epoch_ms=stamp,
            width_hint=1080,
            height_hint=2400,
            png_bytes=support.build_png(1080, 2400),
        )

    def test_default_capacity_is_five(self) -> None:
        self.assertEqual(DEFAULT_FRAME_CAPACITY, 5)
        self.assertEqual(FrameRingBuffer().capacity, 5)

    def test_pushing_past_capacity_evicts_the_oldest_and_says_so(self) -> None:
        buffer = FrameRingBuffer(5)
        for index in range(7):
            buffer.push(self._frame(1000 + index))
        self.assertEqual(len(buffer), 5)
        self.assertEqual(buffer.evictions, 2)
        self.assertEqual(buffer.pushed_total, 7)
        stamps = [frame.captured_at_epoch_ms for frame in buffer.snapshot()]
        self.assertEqual(stamps, [1002, 1003, 1004, 1005, 1006])

    def test_push_returns_the_evicted_frame(self) -> None:
        buffer = FrameRingBuffer(2)
        self.assertIsNone(buffer.push(self._frame(1)))
        self.assertIsNone(buffer.push(self._frame(2)))
        evicted = buffer.push(self._frame(3))
        self.assertIsNotNone(evicted)
        assert evicted is not None
        self.assertEqual(evicted.captured_at_epoch_ms, 1)

    def test_latest_and_oldest_are_positional_not_temporal_assumptions(self) -> None:
        buffer = FrameRingBuffer(3)
        buffer.push(self._frame(5))
        buffer.push(self._frame(4))
        self.assertEqual(buffer.oldest().captured_at_epoch_ms, 5)
        self.assertEqual(buffer.latest().captured_at_epoch_ms, 4)

    def test_reading_an_empty_buffer_raises_rather_than_returning_none(self) -> None:
        buffer = FrameRingBuffer(3)
        with self.assertRaises(LookupError):
            buffer.latest()
        with self.assertRaises(LookupError):
            buffer.oldest()

    def test_capacities_outside_the_documented_envelope_are_refused(self) -> None:
        with self.assertRaises(ValueError):
            FrameRingBuffer(0)
        with self.assertRaises(ValueError):
            FrameRingBuffer(MAX_FRAME_CAPACITY + 1)
        with self.assertRaises(TypeError):
            FrameRingBuffer(True)
        with self.assertRaises(TypeError):
            FrameRingBuffer("five")

    def test_clear_releases_memory_without_closing_the_buffer(self) -> None:
        buffer = FrameRingBuffer(3)
        buffer.push(self._frame(1))
        buffer.clear()
        self.assertEqual(len(buffer), 0)
        buffer.push(self._frame(2))
        self.assertEqual(len(buffer), 1)

    def test_a_closed_buffer_refuses_frames_and_closing_is_idempotent(self) -> None:
        buffer = FrameRingBuffer(3)
        buffer.close()
        buffer.close()
        self.assertTrue(buffer.is_closed)
        with self.assertRaises(ValueError):
            buffer.push(self._frame(1))

    def test_the_buffer_context_manager_closes_it(self) -> None:
        with FrameRingBuffer(2) as buffer:
            buffer.push(self._frame(1))
        self.assertTrue(buffer.is_closed)

    def test_holding_a_non_frame_is_refused(self) -> None:
        buffer = FrameRingBuffer(2)
        with self.assertRaises(TypeError):
            buffer.push("not a frame")  # type: ignore[arg-type]


class CaptureSessionTest(unittest.TestCase):
    def test_a_session_retains_frames_and_tears_down_both_parts(self) -> None:
        transport, _ = gated_transport()
        gate = ForegroundGate(transport, HARNESS_PACKAGE)
        with ScreenCaptureSession(transport, gate, capacity=2, clock=lambda: 100.0) as session:
            first = session.capture()
            second = session.capture()
            self.assertEqual(session.frames, (first, second))
            self.assertEqual(len(session.buffer), 2)
        self.assertTrue(session.buffer.is_closed)
        self.assertTrue(transport.is_closed)
        self.assertTrue(session.is_closed)

    def test_capturing_after_close_is_an_error_not_a_none(self) -> None:
        transport, _ = gated_transport()
        session = ScreenCaptureSession(transport, ForegroundGate(transport, HARNESS_PACKAGE))
        session.close()
        with self.assertRaises(AdbFailed) as caught:
            session.capture()
        self.assertEqual(caught.exception.code, BridgeErrorCode.TRANSPORT_CLOSED)

    def test_a_session_whose_gate_vouches_for_another_device_is_refused(self) -> None:
        transport, _ = gated_transport()
        other, _ = support.make_transport(serial="OTHERSERIAL")
        with self.assertRaises(ValueError):
            ScreenCaptureSession(other, ForegroundGate(transport, HARNESS_PACKAGE))


if __name__ == "__main__":
    unittest.main()
