"""`bridge.envelope`: construction rules, wire round trip, sequence discipline and replay.

Every refusal is asserted with its code, not its prose: the prose is for the operator, the code is
the contract (`docs/phases/phase-0/specs.md` section 3 rule 1 - errors are structured values).
"""

from __future__ import annotations

import base64
import json
import sys
import unittest
from pathlib import Path

_HERE = Path(__file__).resolve().parent
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))

import support  # noqa: E402
from bridge.capture import FrameRecord  # noqa: E402
from bridge.envelope import (  # noqa: E402
    AGREED_IMAGE_ENCODINGS,
    ENVELOPE_VERSION,
    IMAGE_ENCODING_PNG,
    MAX_FRAME_BYTES,
    REPLAY_CAPACITY_DEFAULT,
    AgentEnvelope,
    EnvelopeNotice,
    EventType,
    ReplayBuffer,
    SequenceLedger,
)
from bridge.errors import BridgeErrorCode, EnvelopeInvalid, FrameTooLarge  # noqa: E402

NOW = 1_777_000_000.125


def envelope(seq_no: int = 1, event_type: EventType = EventType.TELEMETRY, **overrides: object) -> AgentEnvelope:
    kwargs: dict[str, object] = {
        "version": ENVELOPE_VERSION,
        "seq_no": seq_no,
        "timestamp": NOW,
        "event_type": event_type,
        "payload": {"surface": "harness", "observed": True},
    }
    kwargs.update(overrides)
    return AgentEnvelope(**kwargs)  # type: ignore[arg-type]


class VocabularyTest(unittest.TestCase):
    def test_the_event_vocabulary_is_exactly_the_three_agreed_types(self) -> None:
        self.assertEqual(
            sorted(member.value for member in EventType),
            ["INPUT_ACTION", "SURFACE_FRAME", "TELEMETRY"],
        )

    def test_the_agreed_image_encoding_is_png_only(self) -> None:
        self.assertEqual(AGREED_IMAGE_ENCODINGS, frozenset({IMAGE_ENCODING_PNG}))

    def test_the_frame_cap_is_512_kib(self) -> None:
        self.assertEqual(MAX_FRAME_BYTES, 512 * 1024)

    def test_the_replay_window_is_two_hundred(self) -> None:
        self.assertEqual(REPLAY_CAPACITY_DEFAULT, 200)

    def test_from_wire_accepts_only_exact_names(self) -> None:
        self.assertIs(EventType.from_wire("TELEMETRY"), EventType.TELEMETRY)
        for bad in ("telemetry", "", "   ", "SCREEN", 7, None):
            with self.assertRaises(EnvelopeInvalid) as caught:
                EventType.from_wire(bad)
            self.assertEqual(caught.exception.code, BridgeErrorCode.ENVELOPE_EVENT_TYPE_UNKNOWN)


class ConstructionTest(unittest.TestCase):
    def test_a_valid_envelope_constructs(self) -> None:
        built = envelope()
        self.assertEqual(built.version, 1)
        self.assertEqual(built.seq_no, 1)
        self.assertEqual(built.event_type, EventType.TELEMETRY)

    def test_a_version_other_than_one_is_refused(self) -> None:
        for bad in (0, 2, -1):
            with self.assertRaises(EnvelopeInvalid) as caught:
                envelope(version=bad)
            self.assertEqual(caught.exception.code, BridgeErrorCode.ENVELOPE_VERSION_UNSUPPORTED)

    def test_a_version_that_is_not_an_integer_is_a_type_refusal_not_a_downgrade(self) -> None:
        for bad in ("1", 1.0, True):
            with self.assertRaises(EnvelopeInvalid) as caught:
                envelope(version=bad)
            self.assertEqual(caught.exception.code, BridgeErrorCode.ENVELOPE_FIELD_TYPE_INVALID)

    def test_a_non_positive_seq_no_is_refused_on_construction(self) -> None:
        for bad in (0, -1):
            with self.assertRaises(EnvelopeInvalid) as caught:
                envelope(seq_no=bad)
            self.assertEqual(caught.exception.code, BridgeErrorCode.ENVELOPE_SEQ_NOT_POSITIVE)

    def test_a_bool_is_not_a_sequence_number(self) -> None:
        with self.assertRaises(EnvelopeInvalid):
            envelope(seq_no=True)

    def test_timestamps_that_are_not_finite_positive_seconds_are_refused(self) -> None:
        for bad in (float("nan"), float("inf"), 0.0, -1.0, "1777000000"):
            with self.assertRaises(EnvelopeInvalid):
                envelope(timestamp=bad)

    def test_a_non_dict_payload_is_refused(self) -> None:
        for bad in ([], "text", 3, None):
            with self.assertRaises(EnvelopeInvalid):
                envelope(payload=bad)

    def test_the_payload_is_copied_so_later_mutation_cannot_rewrite_history(self) -> None:
        source = {"surface": "harness"}
        built = envelope(payload=source)
        source["surface"] = "tampered"
        self.assertEqual(built.payload["surface"], "harness")

    def test_an_event_type_that_is_not_a_member_is_refused(self) -> None:
        with self.assertRaises(EnvelopeInvalid):
            envelope(event_type="TELEMETRY")


class PayloadRulesTest(unittest.TestCase):
    def test_telemetry_must_say_something(self) -> None:
        with self.assertRaises(EnvelopeInvalid) as caught:
            envelope(event_type=EventType.TELEMETRY, payload={})
        self.assertEqual(caught.exception.code, BridgeErrorCode.ENVELOPE_PAYLOAD_INVALID)

    def test_telemetry_keys_must_be_named(self) -> None:
        with self.assertRaises(EnvelopeInvalid):
            envelope(event_type=EventType.TELEMETRY, payload={"": "value"})

    def test_an_input_action_names_an_agreed_action(self) -> None:
        for action in ("tap", "long_press", "text", "keyevent"):
            envelope(event_type=EventType.INPUT_ACTION, payload={"action": action})
        with self.assertRaises(EnvelopeInvalid) as caught:
            envelope(event_type=EventType.INPUT_ACTION, payload={"action": "double_tap"})
        self.assertEqual(caught.exception.code, BridgeErrorCode.ENVELOPE_PAYLOAD_INVALID)

    def test_input_action_params_must_be_an_object_when_present(self) -> None:
        with self.assertRaises(EnvelopeInvalid):
            envelope(event_type=EventType.INPUT_ACTION, payload={"action": "tap", "params": [1, 2]})

    def test_a_surface_frame_must_declare_the_agreed_encoding(self) -> None:
        payload = _surface_payload()
        for encoding in ("image/jpeg", "PNG", "", None):
            with self.assertRaises(EnvelopeInvalid) as caught:
                envelope(event_type=EventType.SURFACE_FRAME, payload={**payload, "encoding": encoding})
            self.assertEqual(caught.exception.code, BridgeErrorCode.ENVELOPE_ENCODING_UNAGREED)

    def test_a_surface_frame_needs_image_bytes(self) -> None:
        payload = _surface_payload()
        del payload["data_base64"]
        with self.assertRaises(EnvelopeInvalid):
            envelope(event_type=EventType.SURFACE_FRAME, payload=payload)

    def test_unparseable_base64_is_refused(self) -> None:
        payload = _surface_payload()
        payload["data_base64"] = "not base64 at all!!"
        with self.assertRaises(EnvelopeInvalid):
            envelope(event_type=EventType.SURFACE_FRAME, payload=payload)

    def test_a_surface_frame_above_the_cap_is_refused_as_too_large(self) -> None:
        payload = _surface_payload()
        payload["data_base64"] = base64.b64encode(b"z" * (MAX_FRAME_BYTES + 1)).decode("ascii")
        with self.assertRaises(FrameTooLarge) as caught:
            envelope(event_type=EventType.SURFACE_FRAME, payload=payload)
        self.assertEqual(caught.exception.cap_bytes, MAX_FRAME_BYTES)

    def test_dimensions_must_be_positive_ints(self) -> None:
        payload = _surface_payload()
        payload["width"] = 0
        with self.assertRaises(EnvelopeInvalid):
            envelope(event_type=EventType.SURFACE_FRAME, payload=payload)


def _surface_payload() -> dict[str, object]:
    png = support.screencap_png()
    return {
        "encoding": IMAGE_ENCODING_PNG,
        "data_base64": base64.b64encode(png).decode("ascii"),
        "width": 1080,
        "height": 2400,
        "captured_at_epoch_ms": 1_777_000_000_000,
    }


class RoundTripTest(unittest.TestCase):
    def test_encode_then_decode_returns_an_equal_envelope(self) -> None:
        original = envelope(seq_no=5)
        decoded = AgentEnvelope.decode(original.encode())
        self.assertEqual(decoded, original)

    def test_the_wire_shape_has_exactly_the_five_agreed_keys(self) -> None:
        wire = json.loads(envelope().encode())
        self.assertEqual(
            sorted(wire),
            ["event_type", "payload", "seq_no", "timestamp", "version"],
        )

    def test_encoding_is_deterministic(self) -> None:
        first = envelope(payload={"b": 2, "a": 1}).encode()
        second = envelope(payload={"a": 1, "b": 2}).encode()
        self.assertEqual(first, second)

    def test_the_event_type_on_the_wire_is_the_upper_snake_value(self) -> None:
        self.assertEqual(json.loads(envelope().encode())["event_type"], "TELEMETRY")

    def test_a_frame_that_is_not_json_is_refused(self) -> None:
        for bad in ("", "   ", "{not json", "[]", "3"):
            with self.assertRaises(EnvelopeInvalid) as caught:
                AgentEnvelope.decode(bad)
            self.assertIn(
                caught.exception.code,
                (BridgeErrorCode.ENVELOPE_NOT_JSON, BridgeErrorCode.ENVELOPE_FIELD_TYPE_INVALID),
            )

    def test_a_missing_field_is_named_and_not_defaulted(self) -> None:
        wire = json.loads(envelope().encode())
        del wire["timestamp"]
        with self.assertRaises(EnvelopeInvalid) as caught:
            AgentEnvelope.decode(json.dumps(wire))
        self.assertEqual(caught.exception.code, BridgeErrorCode.ENVELOPE_FIELD_MISSING)
        self.assertEqual(caught.exception.context["missing"], ["timestamp"])

    def test_an_unknown_field_is_refused_rather_than_ignored(self) -> None:
        wire = json.loads(envelope().encode())
        wire["extra"] = "sneaky"
        with self.assertRaises(EnvelopeInvalid) as caught:
            AgentEnvelope.decode(json.dumps(wire))
        self.assertEqual(caught.exception.code, BridgeErrorCode.ENVELOPE_UNKNOWN_FIELD)

    def test_an_unknown_event_type_on_the_wire_is_refused(self) -> None:
        wire = json.loads(envelope().encode())
        wire["event_type"] = "SCREENSHOT"
        with self.assertRaises(EnvelopeInvalid) as caught:
            AgentEnvelope.decode(json.dumps(wire))
        self.assertEqual(caught.exception.code, BridgeErrorCode.ENVELOPE_EVENT_TYPE_UNKNOWN)

    def test_a_seq_no_of_zero_arrives_as_the_same_refusal_from_the_wire(self) -> None:
        wire = json.loads(envelope().encode())
        wire["seq_no"] = 0
        with self.assertRaises(EnvelopeInvalid) as caught:
            AgentEnvelope.decode(json.dumps(wire))
        self.assertEqual(caught.exception.code, BridgeErrorCode.ENVELOPE_SEQ_NOT_POSITIVE)

    def test_an_encoded_envelope_above_the_cap_is_refused(self) -> None:
        huge = envelope(payload={"blob": "x" * (MAX_FRAME_BYTES + 100)})
        with self.assertRaises(FrameTooLarge) as caught:
            huge.encode()
        self.assertGreater(caught.exception.declared_bytes, MAX_FRAME_BYTES)

    def test_decode_within_cap_refuses_an_oversized_string_before_parsing_it(self) -> None:
        oversized = "y" * (MAX_FRAME_BYTES + 1)
        with self.assertRaises(FrameTooLarge) as caught:
            AgentEnvelope.decode_within_cap(oversized)
        self.assertEqual(caught.exception.declared_bytes, len(oversized))

    def test_a_payload_that_is_not_serialisable_is_refused_not_silently_dropped(self) -> None:
        broken = envelope(payload={"fn": lambda value: value})
        with self.assertRaises(EnvelopeInvalid):
            broken.encode()


class SequenceLedgerTest(unittest.TestCase):
    def test_a_clean_run_of_1_to_5_is_accepted(self) -> None:
        ledger = SequenceLedger()
        for seq_no in range(1, 6):
            ledger.accept(envelope(seq_no=seq_no))
        self.assertEqual(ledger.last_accepted_seq_no, 5)
        self.assertEqual(ledger.accepted_count, 5)

    def test_nothing_has_been_sequenced_before_the_first_frame(self) -> None:
        self.assertIsNone(SequenceLedger().last_accepted_seq_no)

    def test_a_connection_that_starts_at_seventeen_is_refused_with_its_own_code(self) -> None:
        ledger = SequenceLedger()
        with self.assertRaises(EnvelopeInvalid) as caught:
            ledger.accept(envelope(seq_no=17))
        self.assertEqual(caught.exception.code, BridgeErrorCode.ENVELOPE_SEQ_START_NOT_ONE)
        self.assertIn("17", caught.exception.message)

    def test_a_repeat_is_named_as_a_repeat(self) -> None:
        ledger = SequenceLedger()
        ledger.accept(envelope(seq_no=1))
        ledger.accept(envelope(seq_no=2))
        with self.assertRaises(EnvelopeInvalid) as caught:
            ledger.accept(envelope(seq_no=2))
        self.assertEqual(caught.exception.code, BridgeErrorCode.ENVELOPE_SEQ_OUT_OF_ORDER)
        self.assertEqual(caught.exception.context["relation"], "repeat")
        self.assertEqual(caught.exception.context["received_seq_no"], 2)
        self.assertEqual(caught.exception.context["last_accepted_seq_no"], 2)

    def test_a_stale_frame_is_named_as_older(self) -> None:
        ledger = SequenceLedger()
        for seq_no in range(1, 6):
            ledger.accept(envelope(seq_no=seq_no))
        with self.assertRaises(EnvelopeInvalid) as caught:
            ledger.accept(envelope(seq_no=3))
        self.assertEqual(caught.exception.code, BridgeErrorCode.ENVELOPE_SEQ_OUT_OF_ORDER)
        self.assertEqual(caught.exception.context["relation"], "older")

    def test_a_gap_is_a_different_failure_with_the_missing_count(self) -> None:
        ledger = SequenceLedger()
        ledger.accept(envelope(seq_no=1))
        with self.assertRaises(EnvelopeInvalid) as caught:
            ledger.accept(envelope(seq_no=4))
        self.assertEqual(caught.exception.code, BridgeErrorCode.ENVELOPE_SEQ_GAP)
        self.assertEqual(caught.exception.context["missing_frames"], 2)
        self.assertEqual(caught.exception.context["expected_seq_no"], 2)
        self.assertIn("gap of 2", caught.exception.message)

    def test_a_rejected_frame_does_not_move_the_ledger(self) -> None:
        ledger = SequenceLedger()
        ledger.accept(envelope(seq_no=1))
        with self.assertRaises(EnvelopeInvalid):
            ledger.accept(envelope(seq_no=9))
        self.assertEqual(ledger.last_accepted_seq_no, 1)
        self.assertEqual(ledger.rejected_count, 1)

    def test_the_origin_of_a_stream_is_not_configurable(self) -> None:
        with self.assertRaises(ValueError):
            SequenceLedger(first_seq_no=2)


class ReplayBufferTest(unittest.TestCase):
    def _run(self, count: int, capacity: int = REPLAY_CAPACITY_DEFAULT) -> ReplayBuffer:
        buffer = ReplayBuffer(capacity)
        for seq_no in range(1, count + 1):
            buffer.append(envelope(seq_no=seq_no))
        return buffer

    def test_the_default_capacity_is_the_documented_two_hundred(self) -> None:
        self.assertEqual(ReplayBuffer().capacity, 200)

    def test_ordering_is_correct_after_wraparound(self) -> None:
        buffer = self._run(250)
        self.assertEqual(len(buffer), 200)
        self.assertEqual(buffer.evictions, 50)
        self.assertEqual(buffer.appended_total, 250)
        seq_nos = [entry.seq_no for entry in buffer.snapshot()]
        self.assertEqual(seq_nos[0], 51)
        self.assertEqual(seq_nos[-1], 250)
        self.assertEqual(seq_nos, sorted(seq_nos))

    def test_replay_since_is_ordered_and_exclusive(self) -> None:
        buffer = self._run(10, capacity=4)
        replayed = buffer.replay_since(8)
        self.assertEqual([entry.seq_no for entry in replayed], [9, 10])

    def test_a_replay_window_that_lost_frames_says_so(self) -> None:
        buffer = self._run(10, capacity=4)
        self.assertTrue(buffer.is_gap_before_replay_window(1))
        self.assertFalse(buffer.is_gap_before_replay_window(7))
        self.assertFalse(buffer.is_gap_before_replay_window(10))

    def test_an_empty_buffer_has_no_latest_sequence(self) -> None:
        buffer = ReplayBuffer(3)
        self.assertIsNone(buffer.latest_seq_no())
        self.assertIsNone(buffer.oldest_retained_seq_no())
        self.assertEqual(buffer.replay_since(0), ())

    def test_a_negative_replay_position_is_refused(self) -> None:
        buffer = ReplayBuffer(3)
        for bad in (-1, True, "1"):
            with self.assertRaises(EnvelopeInvalid):
                buffer.replay_since(bad)  # type: ignore[arg-type]

    def test_holding_a_non_envelope_is_refused(self) -> None:
        with self.assertRaises(TypeError):
            ReplayBuffer(3).append({"seq_no": 1})  # type: ignore[arg-type]

    def test_clear_empties_without_resetting_the_eviction_history(self) -> None:
        buffer = self._run(6, capacity=4)
        buffer.clear()
        self.assertEqual(len(buffer), 0)
        self.assertEqual(buffer.evictions, 2)

    def test_a_capacity_of_zero_is_refused(self) -> None:
        with self.assertRaises(ValueError):
            ReplayBuffer(0)


class FrameEnvelopeTest(unittest.TestCase):
    def test_a_captured_frame_becomes_a_surface_frame(self) -> None:
        png = support.screencap_png()
        frame = FrameRecord(
            captured_at_epoch_ms=1_777_000_000_000,
            width_hint=1080,
            height_hint=2400,
            png_bytes=png,
        )
        wrapped = AgentEnvelope.from_frame(frame, seq_no=1, timestamp=NOW)
        self.assertEqual(wrapped.event_type, EventType.SURFACE_FRAME)
        self.assertEqual(wrapped.payload["encoding"], IMAGE_ENCODING_PNG)
        decoded = AgentEnvelope.decode(wrapped.encode())
        self.assertEqual(
            base64.b64decode(decoded.payload["data_base64"]),
            png,
        )
        self.assertEqual(decoded.payload["width"], 1080)

    def test_a_frame_larger_than_the_cap_never_becomes_an_envelope(self) -> None:
        frame = FrameRecord(
            captured_at_epoch_ms=1,
            width_hint=1080,
            height_hint=2400,
            png_bytes=b"z" * (MAX_FRAME_BYTES + 1),
        )
        with self.assertRaises(FrameTooLarge):
            AgentEnvelope.from_frame(frame, seq_no=1, timestamp=NOW)


class EnvelopeNoticeTest(unittest.TestCase):
    def test_a_notice_carries_the_code_and_the_expected_position(self) -> None:
        notice = EnvelopeNotice(
            code=BridgeErrorCode.ENVELOPE_SEQ_GAP,
            message="expected seq_no 2",
            last_accepted_seq_no=1,
            context={"missing_frames": 2},
        )
        parsed = json.loads(notice.as_text())
        self.assertEqual(parsed["error_code"], "envelope_seq_gap")
        self.assertEqual(parsed["last_accepted_seq_no"], 1)
        self.assertEqual(parsed["context"]["missing_frames"], 2)
        self.assertEqual(parsed["notice"], "envelope_rejected")

    def test_a_notice_without_a_reason_is_refused(self) -> None:
        with self.assertRaises(ValueError):
            EnvelopeNotice(code=BridgeErrorCode.ENVELOPE_PAYLOAD_INVALID, message="  ")

    def test_a_notice_without_a_code_is_refused(self) -> None:
        with self.assertRaises(TypeError):
            EnvelopeNotice(code="envelope_payload_invalid", message="nope")


if __name__ == "__main__":
    unittest.main()
