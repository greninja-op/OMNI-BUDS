"""Coordinate and key input, gated the same way as capture.

Order inside every action here is fixed, and that order is the safety property:

1. Refuse locally - this transport is the one the gate vouches for; the target belongs to the
   harness package; the string or keycode can be expressed safely.
2. Ask the device whether the harness holds window focus (`ForegroundGate.assert_foreground()`).
3. Only then hand the resolved coordinate/code to `AdbTransport`, which is where the argv is built.

Step 1 before step 2 means a nonsensical request costs nothing and sends nothing. Step 2 before
step 3 is the device-access policy: no input into another application, ever. A refusal at steps 1
and 2 means the command was never sent, which is why refusals raise `InputRejected`,
`NotForeground` or `HierarchyRejected` rather than `AdbFailed` - after a refusal the device state is
known unchanged, and conflating the two would make an un-sent tap look like a failed tap.

On `shell=True` and escaping
-----------------------------
The brief allows a single `sh -c` where `input text` escaping genuinely requires one. This package
does not use one, and the reason is worth stating exactly: `adb shell <args>` concatenates the
argument list with single spaces and hands the result to **the device's** `/system/bin/sh`. A local
`sh -c` would therefore protect nothing - the re-parse happens on the phone, on the far side of the
USB link. So the argv stays a list (`shell=False`, no local shell at any point) and safety comes
from an allow-list on the characters we are willing to let through: `escape_input_text` is pure, and
`tests/test_input_actions.py` proves that its output contains no space and no shell metacharacter
for every string it accepts.
"""

from __future__ import annotations

import enum
from dataclasses import dataclass

from .adb import AdbTransport, CommandOutcome
from .capture import ForegroundGate
from .errors import BridgeErrorCode, InputRejected, NotForeground
from .hierarchy import UiElement, require_resolvable

#: `input text` renders a space as `%s`. This is the only escape sequence this module emits.
SPACE_TOKEN = "%s"

#: Characters that survive the device-side shell re-parse unchanged and are not special to `input`
#: itself. Everything outside this set is refused rather than quoted, because every metacharacter in
#: the rejected set (`;`, `|`, `&`, `$`, backtick, quotes, globs, redirection, newline) is exactly
#: what a shell would re-interpret on the phone.
SAFE_INPUT_CHARACTERS: frozenset[str] = frozenset(
    "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    "._-/:+=@,"
)

#: A command line the device shell will not build, or a string a test did not mean to type.
MAX_INPUT_TEXT_LENGTH = 512

#: Android's long-press threshold is around 500 ms; 800 ms is this bridge's chosen margin, not a
#: platform promise. Declared as a constant rather than a magic number at the call site, and listed
#: in the README as needing confirmation on the live device.
DEFAULT_LONG_PRESS_DURATION_MS = 800
MIN_LONG_PRESS_DURATION_MS = 1
MAX_LONG_PRESS_DURATION_MS = 60_000

#: `input keyevent <code>` accepts a numeric keycode. This envelope is the bridge's own guard,
#: chosen to cover the documented `android.view.KeyEvent` constants (which run from
#: `KEYCODE_UNKNOWN` = 0 up to values past 255 in current releases) while rejecting the negative and
#: enormous values a corrupt caller could produce. Whether a particular code does anything on a
#: particular phone is a live-device question, recorded as unverified in the README.
KEYEVENT_MIN = 0
KEYEVENT_MAX = 255


class Keycode(enum.IntEnum):
    """The keycodes this bridge names, from `android.view.KeyEvent`.

    Sparse on purpose: a keycode is a device-wide action, and an unbounded int at the call site is
    how a UI test ends up pressing POWER.
    """

    UNKNOWN = 0
    HOME = 3
    BACK = 4
    VOLUME_UP = 24
    VOLUME_DOWN = 25
    POWER = 26
    TAB = 61
    ENTER = 66
    DELETE = 67
    MEDIA_PLAY_PAUSE = 85
    APP_SWITCH = 187


class InputAction(enum.Enum):
    """Machine keys for the actions this module performs."""

    TAP = "tap"
    LONG_PRESS = "long_press"
    TEXT = "text"
    KEYEVENT = "keyevent"


@dataclass(frozen=True)
class InputOutcome:
    """Proof of what was sent, carried back from the transport.

    `argv` is the exact command list, so "we think we tapped" is answerable with the coordinate
    actually addressed rather than with an intention.
    """

    action: InputAction
    argv: tuple[str, ...]
    device_output: str
    coordinate: tuple[int, int] | None
    keycode: int | None
    text_length: int | None

    def as_dict(self) -> dict[str, object]:
        return {
            "action": self.action.value,
            "argv": list(self.argv),
            "coordinate": list(self.coordinate) if self.coordinate is not None else None,
            "keycode": self.keycode,
            "text_length": self.text_length,
        }

    @classmethod
    def from_outcome(
        cls,
        action: InputAction,
        outcome: CommandOutcome,
        *,
        coordinate: tuple[int, int] | None = None,
        keycode: int | None = None,
        text_length: int | None = None,
    ) -> "InputOutcome":
        return cls(
            action=action,
            argv=outcome.argv,
            device_output=outcome.stdout,
            coordinate=coordinate,
            keycode=keycode,
            text_length=text_length,
        )


def escape_input_text(value: str) -> str:
    """Turn a Python string into one `input text` argument the device shell will not re-interpret.

    Spaces become `%s`. Every other character must be in `SAFE_INPUT_CHARACTERS`, or the whole
    string is refused. There is no partial application: half-accepting a string would type some of
    it and silently drop the rest.

    Raises:
        InputRejected(INPUT_EMPTY): an empty string, which is a command with no observable effect.
        InputRejected(INPUT_TOO_LONG): above `MAX_INPUT_TEXT_LENGTH`.
        InputRejected(INPUT_CHARACTER_UNSAFE): a control character, a non-ASCII character, a shell
            metacharacter, or a literal `%` - whose meaning after the `%s` convention is not
            something this bridge will assert without a device run. The message names the offending
            position and code point, so the refusal is actionable rather than mysterious.
    """
    if not isinstance(value, str):
        raise InputRejected(
            "text input requires a str, got " + type(value).__name__,
            code=BridgeErrorCode.INPUT_CHARACTER_UNSAFE,
        )
    if value == "":
        raise InputRejected(
            "refusing to send an empty text action: it would type nothing and report success",
            code=BridgeErrorCode.INPUT_EMPTY,
        )
    if len(value) > MAX_INPUT_TEXT_LENGTH:
        raise InputRejected(
            "text input of " + str(len(value)) + " characters exceeds MAX_INPUT_TEXT_LENGTH ("
            + str(MAX_INPUT_TEXT_LENGTH) + ")",
            code=BridgeErrorCode.INPUT_TOO_LONG,
            context={"length": len(value)},
        )
    escaped_chars: list[str] = []
    for position, character in enumerate(value):
        if character == " ":
            escaped_chars.append(SPACE_TOKEN)
            continue
        if character not in SAFE_INPUT_CHARACTERS:
            raise InputRejected(
                "cannot safely express character " + repr(character) + " at position " + str(position)
                + " (U+" + format(ord(character), "04X") + ") for `input text`; the device shell would "
                "re-parse it, so the whole action is refused rather than sending a mangled command. "
                "Supported: A-Z a-z 0-9 . _ - / : + = @ , and space (sent as %s)",
                code=BridgeErrorCode.INPUT_CHARACTER_UNSAFE,
                context={"position": position, "code_point": format(ord(character), "04X")},
            )
        escaped_chars.append(character)
    return "".join(escaped_chars)


def keycode_to_int(keycode: int) -> int:
    """Validate a keycode as an int in `KEYEVENT_MIN..KEYEVENT_MAX`.

    `bool` is rejected explicitly: `True` is an `int` in Python, and a truthiness slip that becomes
    keycode 1 is not a state worth discovering on somebody's phone.
    """
    if isinstance(keycode, bool):
        raise InputRejected(
            "keycode must be an int or a Keycode, not a bool",
            code=BridgeErrorCode.INPUT_KEYCODE_TYPE_INVALID,
        )
    if isinstance(keycode, Keycode):
        value = int(keycode)
    elif isinstance(keycode, int):
        value = keycode
    else:
        raise InputRejected(
            "keycode must be an int or a Keycode, got " + type(keycode).__name__,
            code=BridgeErrorCode.INPUT_KEYCODE_TYPE_INVALID,
        )
    if value < KEYEVENT_MIN or value > KEYEVENT_MAX:
        raise InputRejected(
            "keycode " + str(value) + " is outside the documented envelope "
            + str(KEYEVENT_MIN) + ".." + str(KEYEVENT_MAX),
            code=BridgeErrorCode.INPUT_KEYCODE_OUT_OF_RANGE,
            context={"keycode": value, "min": KEYEVENT_MIN, "max": KEYEVENT_MAX},
        )
    return value


def _require_same_transport(transport: AdbTransport, gate: ForegroundGate) -> None:
    if gate.transport is not transport:
        raise ValueError(
            "the foreground gate must vouch for the same transport the action is sent through"
        )


def _require_target_is_harness(element: UiElement, gate: ForegroundGate) -> None:
    """Refuse to tap a node that belongs to a package other than the harness.

    A hierarchy dump is a snapshot of a moment, and the moment can have ended: the dump a second old
    can still name another application's view. Coordinates are addressable only when their package is
    ours, which is the capture rule applied to input.
    """
    if element.package != gate.expected_package:
        raise NotForeground(
            "refusing to tap an element belonging to " + repr(element.package)
            + "; the bridge only addresses views in " + repr(gate.expected_package),
            expected_package=gate.expected_package,
            observed_package=element.package,
            context={"element": element.describe()},
        )


def tap(
    transport: AdbTransport,
    element: UiElement,
    gate: ForegroundGate,
    *,
    allow_non_clickable: bool = False,
) -> InputOutcome:
    """Tap the resolved centre of `element`.

    `allow_non_clickable` is keyword-only and defaults to `False`: opting out of the clickability
    check has to be a visible decision at the call site, because it is the decision that turns a
    justified tap into a coordinate guess.

    Raises:
        NotForeground: the harness is not foreground, or the element belongs to another package.
        HierarchyRejected: the element's bounds cannot justify a tap.
        AdbFailed and friends: from the transport, after the gate has passed.
    """
    _require_same_transport(transport, gate)
    _require_target_is_harness(element, gate)
    gate.assert_foreground()
    coordinate = require_resolvable(element, allow_non_clickable=allow_non_clickable)
    outcome = transport.input_tap(coordinate[0], coordinate[1])
    return InputOutcome.from_outcome(InputAction.TAP, outcome, coordinate=coordinate)


def long_press(
    transport: AdbTransport,
    element: UiElement,
    gate: ForegroundGate,
    *,
    duration_ms: int = DEFAULT_LONG_PRESS_DURATION_MS,
    allow_non_clickable: bool = False,
) -> InputOutcome:
    """Hold at `element`'s centre for `duration_ms`.

    Expressed as a zero-distance `input swipe`, which is how plain `input` expresses a hold at all.
    """
    if isinstance(duration_ms, bool) or not isinstance(duration_ms, int):
        raise InputRejected(
            "duration_ms must be an int, got " + type(duration_ms).__name__,
            code=BridgeErrorCode.INPUT_KEYCODE_TYPE_INVALID,
        )
    if duration_ms < MIN_LONG_PRESS_DURATION_MS or duration_ms > MAX_LONG_PRESS_DURATION_MS:
        raise InputRejected(
            "duration_ms " + str(duration_ms) + " is outside "
            + str(MIN_LONG_PRESS_DURATION_MS) + ".." + str(MAX_LONG_PRESS_DURATION_MS),
            code=BridgeErrorCode.INPUT_KEYCODE_OUT_OF_RANGE,
            context={"duration_ms": duration_ms},
        )
    _require_same_transport(transport, gate)
    _require_target_is_harness(element, gate)
    gate.assert_foreground()
    coordinate = require_resolvable(element, allow_non_clickable=allow_non_clickable)
    x, y = coordinate
    outcome = transport.input_swipe(x, y, x, y, duration_ms)
    return InputOutcome.from_outcome(InputAction.LONG_PRESS, outcome, coordinate=coordinate)


def text(transport: AdbTransport, value: str, gate: ForegroundGate) -> InputOutcome:
    """Type `value` into whatever currently holds input focus.

    No coordinates are involved, which is precisely why the gate matters most here: the target of a
    text action is whichever field the phone happens to be focused on, and the bridge will not find
    that out after the fact.
    """
    escaped = escape_input_text(value)
    _require_same_transport(transport, gate)
    gate.assert_foreground()
    outcome = transport.input_text(escaped)
    return InputOutcome.from_outcome(InputAction.TEXT, outcome, text_length=len(value))


def keyevent(transport: AdbTransport, keycode: int, gate: ForegroundGate) -> InputOutcome:
    """Send one key event, validated against the documented envelope before it is sent."""
    value = keycode_to_int(keycode)
    _require_same_transport(transport, gate)
    gate.assert_foreground()
    outcome = transport.input_keyevent(value)
    return InputOutcome.from_outcome(InputAction.KEYEVENT, outcome, keycode=value)


__all__ = [
    "SPACE_TOKEN",
    "SAFE_INPUT_CHARACTERS",
    "MAX_INPUT_TEXT_LENGTH",
    "DEFAULT_LONG_PRESS_DURATION_MS",
    "MIN_LONG_PRESS_DURATION_MS",
    "MAX_LONG_PRESS_DURATION_MS",
    "KEYEVENT_MIN",
    "KEYEVENT_MAX",
    "Keycode",
    "InputAction",
    "InputOutcome",
    "escape_input_text",
    "keycode_to_int",
    "tap",
    "long_press",
    "text",
    "keyevent",
]
