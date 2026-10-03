"""`bridge.input_actions`: escaping, coordinate resolution and the gate in front of everything.

The escaping proof is the part a reviewer should read first: `test_every_accepted_string_escapes_to_
a_space_free_metacharacter_free_argument` enumerates the shell's own metacharacters and asserts that
none of them can leave `escape_input_text`, which is what lets the bridge keep `shell=False` on the
workstation side while the device still re-parses the argument.
"""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

_HERE = Path(__file__).resolve().parent
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))

import support  # noqa: E402
from dataclasses import replace  # noqa: E402

from bridge import HARNESS_PACKAGE, harness_resource_id  # noqa: E402
from bridge.adb import AdbTransport  # noqa: E402
from bridge.capture import ForegroundGate  # noqa: E402
from bridge.errors import (  # noqa: E402
    BridgeErrorCode,
    HierarchyRejected,
    InputRejected,
    NotForeground,
)
from bridge.hierarchy import UiElement, parse_dump  # noqa: E402
from bridge.input_actions import (  # noqa: E402
    DEFAULT_LONG_PRESS_DURATION_MS,
    KEYEVENT_MAX,
    MAX_INPUT_TEXT_LENGTH,
    InputAction,
    Keycode,
    escape_input_text,
    keyevent,
    keycode_to_int,
    long_press,
    tap,
    text,
)

#: Characters the device shell would re-interpret inside a bare word. `=` is deliberately absent: an
#: assignment is only recognised before the command word, and `input text <arg>` always puts `input`
#: first, so `probe=1` reaches `input` unchanged.
SHELL_METACHARACTERS = "`$&*|;><(){}[]!#~\"'\\?"


def gated_fixture(focus_package: str = HARNESS_PACKAGE) -> tuple[AdbTransport, support.FakeAdbRunner, ForegroundGate]:
    transport, runner = support.make_transport()
    runner.on(
        "shell",
        "dumpsys",
        "window",
        stdout=(
            "  mCurrentFocus=Window{6d3b1c8 u0 " + focus_package + "/" + focus_package + ".ShellActivity}\n"
        ).encode("utf-8"),
        repeat=True,
    )
    runner.on("shell", "input", stdout=b"", repeat=True)
    return transport, runner, ForegroundGate(transport, HARNESS_PACKAGE)


class EscapeTest(unittest.TestCase):
    def test_a_space_becomes_the_documented_percent_s(self) -> None:
        self.assertEqual(escape_input_text("hello world"), "hello%sworld")

    def test_every_accepted_string_escapes_to_a_space_free_metacharacter_free_argument(self) -> None:
        accepted = [
            "probe",
            "probe 1",
            "a.b_c-d/e:f+g@h,i=j",
            "OmniBuds bridge shell",
            "ABC123",
        ]
        for value in accepted:
            escaped = escape_input_text(value)
            self.assertNotIn(" ", escaped, value)
            for character in SHELL_METACHARACTERS:
                self.assertNotIn(character, escaped, value + " contained " + repr(character))
            self.assertEqual(escaped.replace("%s", " "), value)

    def test_the_shell_metacharacters_are_all_refused(self) -> None:
        for character in SHELL_METACHARACTERS:
            with self.assertRaises(InputRejected) as caught:
                escape_input_text("probe" + character + "value")
            self.assertEqual(caught.exception.code, BridgeErrorCode.INPUT_CHARACTER_UNSAFE)
            self.assertEqual(caught.exception.context["position"], 5)

    def test_control_characters_are_refused(self) -> None:
        for value in ("probe\n", "probe\t", "probe\r", "probe\x00"):
            with self.assertRaises(InputRejected):
                escape_input_text(value)

    def test_non_ascii_is_refused_rather_than_mangled(self) -> None:
        with self.assertRaises(InputRejected) as caught:
            escape_input_text("pröbe")
        self.assertEqual(caught.exception.context["code_point"], "00F6")

    def test_a_literal_percent_is_refused_because_its_meaning_is_ours(self) -> None:
        with self.assertRaises(InputRejected):
            escape_input_text("100% probe")

    def test_an_empty_string_is_refused_because_it_reports_success_for_nothing(self) -> None:
        with self.assertRaises(InputRejected) as caught:
            escape_input_text("")
        self.assertEqual(caught.exception.code, BridgeErrorCode.INPUT_EMPTY)

    def test_an_over_long_string_is_refused(self) -> None:
        with self.assertRaises(InputRejected) as caught:
            escape_input_text("a" * (MAX_INPUT_TEXT_LENGTH + 1))
        self.assertEqual(caught.exception.code, BridgeErrorCode.INPUT_TOO_LONG)

    def test_a_non_string_is_refused(self) -> None:
        with self.assertRaises(InputRejected):
            escape_input_text(7)  # type: ignore[arg-type]


class KeycodeTest(unittest.TestCase):
    def test_named_keycodes_are_the_documented_values(self) -> None:
        self.assertEqual(int(Keycode.BACK), 4)
        self.assertEqual(int(Keycode.HOME), 3)
        self.assertEqual(int(Keycode.ENTER), 66)

    def test_the_envelope_bounds_are_accepted(self) -> None:
        self.assertEqual(keycode_to_int(0), 0)
        self.assertEqual(keycode_to_int(KEYEVENT_MAX), KEYEVENT_MAX)

    def test_values_outside_the_envelope_are_refused(self) -> None:
        for bad in (-1, KEYEVENT_MAX + 1, 10_000):
            with self.assertRaises(InputRejected) as caught:
                keycode_to_int(bad)
            self.assertEqual(caught.exception.code, BridgeErrorCode.INPUT_KEYCODE_OUT_OF_RANGE)

    def test_a_bool_is_refused_because_true_is_one_in_python(self) -> None:
        with self.assertRaises(InputRejected) as caught:
            keycode_to_int(True)
        self.assertEqual(caught.exception.code, BridgeErrorCode.INPUT_KEYCODE_TYPE_INVALID)

    def test_a_string_is_refused(self) -> None:
        with self.assertRaises(InputRejected):
            keycode_to_int("4")  # type: ignore[arg-type]


class TapActionTest(unittest.TestCase):
    def test_a_tap_addresses_the_resolved_centre_after_the_gate(self) -> None:
        transport, runner, gate = gated_fixture()
        dump = parse_dump(support.uiautomator_dump_text())
        button = dump.find_by_resource_id(harness_resource_id("bridge_probe_button"))[0]
        outcome = tap(transport, button, gate)
        self.assertEqual(outcome.coordinate, (540, 254))
        self.assertEqual(outcome.action, InputAction.TAP)
        self.assertEqual(
            outcome.argv,
            ("adb", "-s", "BRIDGEFA0001", "shell", "input", "tap", "540", "254"),
        )
        self.assertEqual(runner.calls[0][-2:], ["dumpsys", "window"])
        self.assertEqual(runner.calls[1][-4:], ["input", "tap", "540", "254"])

    def test_a_foreign_foreground_app_stops_the_tap_before_any_input_command(self) -> None:
        transport, runner, gate = gated_fixture(focus_package="com.example.foreigndemo")
        dump = parse_dump(support.uiautomator_dump_text())
        button = dump.find_by_resource_id(harness_resource_id("bridge_probe_button"))[0]
        with self.assertRaises(NotForeground):
            tap(transport, button, gate)
        self.assertFalse(any("input" in call for call in runner.calls))

    def test_an_element_from_another_package_never_reaches_the_device(self) -> None:
        transport, runner, gate = gated_fixture()
        foreign = UiElement(
            clazz="android.widget.Button",
            resource_id="com.example.foreigndemo:id/bridge_probe_button",
            text="Probe",
            content_desc="bridge-probe-button",
            bounds=(66, 182, 1014, 326),
            clickable=True,
            enabled=True,
            focused=False,
            package="com.example.foreigndemo",
            index=0,
            children=(),
        )
        with self.assertRaises(NotForeground) as caught:
            tap(transport, foreign, gate)
        self.assertEqual(caught.exception.observed_package, "com.example.foreigndemo")
        self.assertEqual(runner.calls, [], "not even the focus read may happen for a foreign node")

    def test_a_non_clickable_element_is_refused_without_sending_input(self) -> None:
        transport, runner, gate = gated_fixture()
        dump = parse_dump(support.uiautomator_dump_text())
        title = dump.find_by_resource_id(harness_resource_id("bridge_title"))[0]
        with self.assertRaises(HierarchyRejected) as caught:
            tap(transport, title, gate)
        self.assertEqual(caught.exception.code, BridgeErrorCode.TARGET_NOT_CLICKABLE)
        self.assertFalse(any("input" in call for call in runner.calls))

    def test_a_zero_area_element_is_refused(self) -> None:
        transport, runner, gate = gated_fixture()
        collapsed = UiElement(
            clazz="android.widget.Button",
            resource_id=harness_resource_id("bridge_probe_button"),
            text="Probe",
            content_desc="bridge-probe-button",
            bounds=(400, 400, 400, 400),
            clickable=True,
            enabled=True,
            focused=False,
            package=HARNESS_PACKAGE,
            index=2,
            children=(),
        )
        with self.assertRaises(HierarchyRejected) as caught:
            tap(transport, collapsed, gate)
        self.assertEqual(caught.exception.code, BridgeErrorCode.TARGET_ZERO_AREA)
        self.assertFalse(any("input" in call for call in runner.calls))

    def test_a_disabled_element_is_refused(self) -> None:
        transport, _ = support.make_transport()
        runner = _  # readability
        dump = parse_dump(support.uiautomator_dump_text())
        button = dump.find_by_resource_id(harness_resource_id("bridge_probe_button"))[0]
        disabled = UiElement(**{**button.__dict__, "enabled": False})  # type: ignore[arg-type]
        runner.on("shell", "dumpsys", "window", stdout=b"  mCurrentFocus=Window{1 u0 com.omnibuds.tools.shell/.ShellActivity}\n")
        runner.on("shell", "input", stdout=b"")
        gate = ForegroundGate(transport, HARNESS_PACKAGE)
        with self.assertRaises(HierarchyRejected) as caught:
            tap(transport, disabled, gate)
        self.assertEqual(caught.exception.code, BridgeErrorCode.TARGET_DISABLED)

    def test_allow_non_clickable_is_a_named_decision_at_the_call_site(self) -> None:
        transport, runner, gate = gated_fixture()
        dump = parse_dump(support.uiautomator_dump_text())
        counter = dump.find_by_resource_id(harness_resource_id("bridge_counter_value"))[0]
        outcome = tap(transport, counter, gate, allow_non_clickable=True)
        self.assertEqual(outcome.coordinate, ((66 + 330) // 2, (326 + 378) // 2))
        self.assertTrue(any("input" in call for call in runner.calls))

    def test_a_gate_built_on_another_transport_is_refused(self) -> None:
        transport, _ = support.make_transport()
        other, runner_other = support.make_transport(serial="OTHERSERIAL")
        runner_other.on("shell", "dumpsys", "window", stdout=b"  mCurrentFocus=Window{1 u0 com.omnibuds.tools.shell/.ShellActivity}\n")
        dump = parse_dump(support.uiautomator_dump_text())
        button = dump.find_by_resource_id(harness_resource_id("bridge_probe_button"))[0]
        with self.assertRaises(ValueError):
            tap(transport, button, ForegroundGate(other, HARNESS_PACKAGE))


class LongPressTest(unittest.TestCase):
    def test_a_long_press_is_a_zero_distance_swipe_at_the_centre(self) -> None:
        transport, runner, gate = gated_fixture()
        dump = parse_dump(support.uiautomator_dump_text())
        button = dump.find_by_resource_id(harness_resource_id("bridge_probe_button"))[0]
        outcome = long_press(transport, button, gate)
        self.assertEqual(outcome.action, InputAction.LONG_PRESS)
        self.assertEqual(
            outcome.argv,
            (
                "adb",
                "-s",
                "BRIDGEFA0001",
                "shell",
                "input",
                "swipe",
                "540",
                "254",
                "540",
                "254",
                str(DEFAULT_LONG_PRESS_DURATION_MS),
            ),
        )
        self.assertEqual(runner.calls[0][-2:], ["dumpsys", "window"])

    def test_a_duration_outside_the_envelope_is_refused_before_the_gate(self) -> None:
        transport, runner, gate = gated_fixture()
        dump = parse_dump(support.uiautomator_dump_text())
        button = dump.find_by_resource_id(harness_resource_id("bridge_probe_button"))[0]
        with self.assertRaises(InputRejected):
            long_press(transport, button, gate, duration_ms=0)
        self.assertEqual(runner.calls, [])

    def test_a_non_integer_duration_is_refused(self) -> None:
        transport, runner, gate = gated_fixture()
        dump = parse_dump(support.uiautomator_dump_text())
        button = dump.find_by_resource_id(harness_resource_id("bridge_probe_button"))[0]
        with self.assertRaises(InputRejected):
            long_press(transport, button, gate, duration_ms=800.5)  # type: ignore[arg-type]
        with self.assertRaises(InputRejected):
            long_press(transport, button, gate, duration_ms=True)  # type: ignore[arg-type]
        self.assertEqual(runner.calls, [])


class TextAndKeyActionTest(unittest.TestCase):
    def test_typing_sends_one_escaped_argument(self) -> None:
        transport, runner, gate = gated_fixture()
        outcome = text(transport, "probe one", gate)
        self.assertEqual(outcome.action, InputAction.TEXT)
        self.assertEqual(
            outcome.argv,
            ("adb", "-s", "BRIDGEFA0001", "shell", "input", "text", "probe%sone"),
        )
        self.assertEqual(outcome.text_length, len("probe one"))
        self.assertEqual(runner.calls[0][-2:], ["dumpsys", "window"])

    def test_an_unescapable_string_is_refused_before_the_focus_read(self) -> None:
        transport, runner, gate = gated_fixture()
        with self.assertRaises(InputRejected):
            text(transport, "probe;rm", gate)
        self.assertEqual(runner.calls, [], "no device command may precede a refusal of the argument")

    def test_a_keyevent_sends_the_validated_code(self) -> None:
        transport, runner, gate = gated_fixture()
        outcome = keyevent(transport, Keycode.ENTER, gate)
        self.assertEqual(outcome.keycode, 66)
        self.assertEqual(
            outcome.argv,
            ("adb", "-s", "BRIDGEFA0001", "shell", "input", "keyevent", "66"),
        )

    def test_an_out_of_range_keycode_is_refused_before_the_gate(self) -> None:
        transport, runner, gate = gated_fixture()
        with self.assertRaises(InputRejected):
            keyevent(transport, 999, gate)
        self.assertEqual(runner.calls, [])

    def test_input_text_at_the_transport_level_refuses_an_argument_with_a_space(self) -> None:
        transport, runner = support.make_transport()
        runner.on("shell", "input", stdout=b"")
        with self.assertRaises(ValueError):
            transport.input_text("probe one")


class OutcomeShapeTest(unittest.TestCase):
    def test_an_outcome_serialises_without_device_output(self) -> None:
        transport, _, gate = gated_fixture()
        dump = parse_dump(support.uiautomator_dump_text())
        button = dump.find_by_resource_id(harness_resource_id("bridge_probe_button"))[0]
        payload = tap(transport, button, gate).as_dict()
        self.assertEqual(payload["action"], "tap")
        self.assertEqual(payload["coordinate"], [540, 254])
        self.assertIsNone(payload["keycode"])


if __name__ == "__main__":
    unittest.main()
