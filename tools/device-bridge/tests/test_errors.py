"""`bridge.errors`: the categories, their codes, their retry classes and the shape of a failure.

The point of this file of tests is the rule in `bridge/errors.py`: an error is a structured value
that a caller branches on. So the assertions are about codes and retry classes, never about the
wording of a message.
"""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

_HERE = Path(__file__).resolve().parent
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))

from bridge.errors import (  # noqa: E402
    AdbFailed,
    BridgeError,
    BridgeErrorCode,
    DeviceNotFound,
    DeviceOffline,
    DeviceUnauthorized,
    EnvelopeInvalid,
    FrameTooLarge,
    HandshakeRejected,
    HierarchyRejected,
    InputRejected,
    NotForeground,
    PermissionNotGranted,
    RetryClass,
    TransportClosed,
)

#: Every category the brief names, plus the three added in `bridge/errors.py` with a stated reason.
MANDATED_CATEGORIES: tuple[type[BridgeError], ...] = (
    DeviceNotFound,
    DeviceUnauthorized,
    AdbFailed,
    TransportClosed,
    NotForeground,
    EnvelopeInvalid,
    FrameTooLarge,
    PermissionNotGranted,
)
ADDITIONAL_CATEGORIES: tuple[type[BridgeError], ...] = (
    DeviceOffline,
    HierarchyRejected,
    InputRejected,
    HandshakeRejected,
)


class HierarchyShapeTest(unittest.TestCase):
    def test_every_category_is_a_bridge_error(self) -> None:
        for category in MANDATED_CATEGORIES + ADDITIONAL_CATEGORIES:
            self.assertTrue(issubclass(category, BridgeError), category.__name__)
            self.assertTrue(issubclass(category, Exception), category.__name__)

    def test_bridge_error_is_not_a_silent_value(self) -> None:
        self.assertFalse(issubclass(BridgeError, (ValueError, KeyError)), "a policy refusal must not look like a lookup miss")

    def test_a_blank_message_is_refused(self) -> None:
        with self.assertRaises(ValueError):
            AdbFailed("   ")

    def test_a_category_without_an_explicit_code_uses_its_default(self) -> None:
        self.assertIs(EnvelopeInvalid("x").code, BridgeErrorCode.ENVELOPE_PAYLOAD_INVALID)
        self.assertIs(TransportClosed("x").code, BridgeErrorCode.TRANSPORT_CLOSED)

    def test_the_base_class_declares_a_retry_class_for_every_category(self) -> None:
        for category in MANDATED_CATEGORIES + ADDITIONAL_CATEGORIES:
            self.assertIsInstance(category.retry_class, RetryClass, category.__name__)


class VocabularyTest(unittest.TestCase):
    def test_error_codes_are_unique_strings(self) -> None:
        values = [member.value for member in BridgeErrorCode]
        self.assertEqual(len(values), len(set(values)))
        for value in values:
            self.assertTrue(value.islower() or "_" in value, value)

    def test_every_default_code_is_in_the_vocabulary(self) -> None:
        for category in MANDATED_CATEGORIES + ADDITIONAL_CATEGORIES:
            self.assertIsInstance(category.default_code, BridgeErrorCode, category.__name__)

    def test_the_mandated_default_codes_match_their_categories(self) -> None:
        pairs = {
            DeviceNotFound: BridgeErrorCode.DEVICE_NOT_FOUND,
            DeviceUnauthorized: BridgeErrorCode.DEVICE_UNAUTHORIZED,
            DeviceOffline: BridgeErrorCode.DEVICE_OFFLINE,
            TransportClosed: BridgeErrorCode.TRANSPORT_CLOSED,
            EnvelopeInvalid: BridgeErrorCode.ENVELOPE_PAYLOAD_INVALID,
            FrameTooLarge: BridgeErrorCode.FRAME_TOO_LARGE,
            PermissionNotGranted: BridgeErrorCode.PERMISSION_NOT_GRANTED,
            InputRejected: BridgeErrorCode.INPUT_CHARACTER_UNSAFE,
        }
        for category, code in pairs.items():
            self.assertIs(category.default_code, code, category.__name__)


class RetryDisciplineTest(unittest.TestCase):
    def test_a_side_effecting_refusal_is_never_marked_safe_to_retry(self) -> None:
        for category in (
            PermissionNotGranted,
            EnvelopeInvalid,
            FrameTooLarge,
            InputRejected,
            HandshakeRejected,
        ):
            self.assertIs(category.retry_class, RetryClass.NEVER_RETRY, category.__name__)

    def test_a_device_state_failure_is_resolved_by_re_reading(self) -> None:
        for category in (DeviceNotFound, DeviceOffline, AdbFailed, TransportClosed, NotForeground, HierarchyRejected):
            self.assertIs(category.retry_class, RetryClass.RETRY_AFTER_REREAD, category.__name__)

    def test_an_unauthorized_device_is_not_retried_in_a_loop(self) -> None:
        # The user has to act on the phone; retrying until they do is a hang, not a recovery.
        self.assertIs(DeviceUnauthorized.retry_class, RetryClass.NEVER_RETRY)


class PayloadShapeTest(unittest.TestCase):
    def test_not_foreground_carries_both_packages(self) -> None:
        error = NotForeground(
            "the harness is not foreground",
            expected_package="com.omnibuds.tools.shell",
            observed_package="com.example.foreigndemo",
        )
        self.assertEqual(error.expected_package, "com.omnibuds.tools.shell")
        self.assertEqual(error.observed_package, "com.example.foreigndemo")
        self.assertEqual(error.as_dict()["error_code"], "not_foreground")

    def test_frame_too_large_carries_the_size_and_the_cap(self) -> None:
        error = FrameTooLarge("too big", declared_bytes=900_000, cap_bytes=524_288)
        self.assertEqual(error.declared_bytes, 900_000)
        self.assertEqual(error.cap_bytes, 524_288)
        self.assertEqual(error.as_dict()["context"]["declared_bytes"], 900_000)

    def test_adb_failed_carries_the_status_and_the_argv(self) -> None:
        error = AdbFailed("command failed", returncode=1, argv=("adb", "install", "x.apk"))
        self.assertEqual(error.returncode, 1)
        self.assertEqual(error.argv, ("adb", "install", "x.apk"))
        self.assertEqual(error.code, BridgeErrorCode.ADB_COMMAND_FAILED)

    def test_handshake_rejected_carries_the_http_status(self) -> None:
        error = HandshakeRejected("no token", code=BridgeErrorCode.WS_TOKEN_MISSING, http_status=401)
        self.assertEqual(error.http_status, 401)
        self.assertEqual(error.as_dict()["context"]["http_status"], 401)

    def test_the_serialised_form_is_json_friendly(self) -> None:
        import json

        error = HierarchyRejected("bad bounds", code=BridgeErrorCode.BOUNDS_OUT_OF_RANGE, context={"reported_bounds": "[0,0]"})
        decoded = json.loads(json.dumps(error.as_dict()))
        self.assertEqual(decoded["error_code"], "bounds_out_of_range")
        self.assertEqual(decoded["error_class"], "HierarchyRejected")
        self.assertEqual(decoded["retry_class"], "RETRY_AFTER_REREAD")
        self.assertEqual(decoded["context"]["reported_bounds"], "[0,0]")

    def test_a_stringified_error_names_its_code_first(self) -> None:
        self.assertTrue(str(BridgeError("boom")).startswith("[adb_command_failed]"))

    def test_context_is_copied_so_a_caller_cannot_mutate_a_diagnostic(self) -> None:
        source = {"key": "value"}
        error = BridgeError("boom", context=source)
        source["key"] = "tampered"
        self.assertEqual(error.context["key"], "value")


class NoSilentDefaultRuleTest(unittest.TestCase):
    """The documented rule, checked as behaviour rather than as prose.

    Each case below is a place where a lenient bridge would return a plausible value; the package
    must raise instead. These are the smallest reachable examples of that claim.
    """

    def test_an_unusable_output_cannot_become_an_empty_string(self) -> None:
        from bridge.adb import parse_prop_value
        from bridge.errors import AdbFailed

        with self.assertRaises(AdbFailed):
            parse_prop_value("\n")

    def test_an_empty_buffer_cannot_become_a_frame(self) -> None:
        from bridge.capture import FrameRingBuffer

        buffer = FrameRingBuffer(2)
        with self.assertRaises(LookupError):
            buffer.latest()

    def test_a_malformed_dump_cannot_become_an_empty_tree(self) -> None:
        from bridge.errors import HierarchyRejected
        from bridge.hierarchy import parse_dump

        with self.assertRaises(HierarchyRejected):
            parse_dump("<hierarchy>")

    def test_a_bad_sequence_number_cannot_become_the_previous_one(self) -> None:
        from bridge.envelope import AgentEnvelope, EventType, SequenceLedger

        ledger = SequenceLedger()
        first = AgentEnvelope(
            version=1, seq_no=1, timestamp=1777000000.0, event_type=EventType.TELEMETRY, payload={"a": 1}
        )
        ledger.accept(first)
        with self.assertRaises(EnvelopeInvalid):
            ledger.accept(
                AgentEnvelope(
                    version=1,
                    seq_no=1,
                    timestamp=1777000000.0,
                    event_type=EventType.TELEMETRY,
                    payload={"a": 2},
                )
            )
        self.assertEqual(ledger.accepted_count, 1)


if __name__ == "__main__":
    unittest.main()
