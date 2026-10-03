"""Structured failures for the device bridge.

The rule that owns this file
----------------------------
**No error in this package is converted into a silent default return value.** A failed ADB read
does not yield `""`, a missing focus window does not yield `None`, a bad sequence number does not
yield the previous envelope, and an oversized frame does not yield a truncated one. Every one of
those states has a named category here, and the only honest way to leave the function is to raise
it. This mirrors `docs/phases/phase-0/specs.md` section 3 rule 5 ("no error is converted into a
fabricated value") and section 2.2 ("typed failure" as the third representation tier).

Two refusal channels, deliberately different (Phase 0/1 convention):

* `ValueError` / `TypeError` from a constructor or a precondition means "this call is
  unconstructible" - a defect in the caller, not a device outcome.
* A `BridgeError` subclass means "the operation did not work" - the caller must branch on it.

Categories beyond the eight the brief names
-------------------------------------------
`HierarchyRejected`, `InputRejected` and `HandshakeRejected` are added because collapsing them
would destroy exactly the distinction the brief insists on (Phase 2 research doc, rule D-8: a
failure must not be relabelled as a different layer's failure):

* a malformed or untappable UI node is neither a transport failure nor an envelope failure;
* a refused keystroke never reached the device, so reporting it as `AdbFailed` would claim a
  command that was not sent;
* a rejected WebSocket handshake fails at the HTTP layer, before any transport exists, and the
  HTTP status is part of the diagnosis.

Every category also declares a retry class (Phase 0 section 3 rule 1). `NEVER_RETRY` on a
side-effecting operation is the point: a failed `install` or `tap` must be re-read before it is
re-issued, never blindly replayed.
"""

from __future__ import annotations

import enum
from typing import Any, Mapping


class RetryClass(enum.Enum):
    """How a caller may act on a failure - carried by the category, not improvised at the call site."""

    SAFE_TO_RETRY = "SAFE_TO_RETRY"
    RETRY_AFTER_REREAD = "RETRY_AFTER_REREAD"
    NEVER_RETRY = "NEVER_RETRY"


class BridgeErrorCode(str, enum.Enum):
    """Stable machine keys for every failure this package can raise.

    The value is the identity; the human message is presentation and may change freely. Tests pin
    codes, never prose.
    """

    # ADB transport
    DEVICE_NOT_FOUND = "device_not_found"
    DEVICE_UNAUTHORIZED = "device_unauthorized"
    DEVICE_OFFLINE = "device_offline"
    ADB_COMMAND_FAILED = "adb_command_failed"
    ADB_COMMAND_TIMEOUT = "adb_command_timeout"
    ADB_UNUSABLE_OUTPUT = "adb_unusable_output"
    FOCUS_UNRESOLVED = "focus_unresolved"

    # foreground policy
    NOT_FOREGROUND = "not_foreground"

    # permissions
    PERMISSION_NOT_GRANTED = "permission_not_granted"

    # hierarchy
    HIERARCHY_MALFORMED = "hierarchy_malformed"
    BOUNDS_OUT_OF_RANGE = "bounds_out_of_range"
    TARGET_ZERO_AREA = "target_zero_area_bounds"
    TARGET_DISABLED = "target_disabled"
    TARGET_NOT_CLICKABLE = "target_not_clickable"
    TARGET_NOT_FOUND = "target_not_found"
    TARGET_AMBIGUOUS = "target_ambiguous"

    # input
    INPUT_EMPTY = "input_text_empty"
    INPUT_CHARACTER_UNSAFE = "input_text_character_unsafe"
    INPUT_TOO_LONG = "input_text_too_long"
    INPUT_KEYCODE_OUT_OF_RANGE = "input_keycode_out_of_range"
    INPUT_KEYCODE_TYPE_INVALID = "input_keycode_type_invalid"

    # envelopes
    ENVELOPE_NOT_JSON = "envelope_not_json"
    ENVELOPE_FIELD_MISSING = "envelope_field_missing"
    ENVELOPE_UNKNOWN_FIELD = "envelope_unknown_field"
    ENVELOPE_VERSION_UNSUPPORTED = "envelope_version_unsupported"
    ENVELOPE_EVENT_TYPE_UNKNOWN = "envelope_event_type_unknown"
    ENVELOPE_SEQ_NOT_POSITIVE = "envelope_seq_not_positive"
    ENVELOPE_SEQ_START_NOT_ONE = "envelope_seq_start_not_one"
    ENVELOPE_SEQ_OUT_OF_ORDER = "envelope_seq_out_of_order"
    ENVELOPE_SEQ_GAP = "envelope_seq_gap"
    ENVELOPE_FIELD_TYPE_INVALID = "envelope_field_type_invalid"
    ENVELOPE_PAYLOAD_INVALID = "envelope_payload_invalid"
    ENVELOPE_ENCODING_UNAGREED = "envelope_image_encoding_unagreed"

    # frames and transport-level protocol
    FRAME_TOO_LARGE = "frame_too_large"
    TRANSPORT_CLOSED = "transport_closed"
    WS_PROTOCOL_ERROR = "websocket_protocol_error"
    WS_HANDSHAKE_REJECTED = "websocket_handshake_rejected"
    WS_TOKEN_MISSING = "websocket_token_missing"
    WS_VERSION_UNSUPPORTED = "websocket_version_unsupported"
    WS_CONNECTION_LIMIT = "websocket_connection_limit"

    # gateway policy
    EVENT_TYPE_UNSUBSCRIBED = "event_type_unsubscribed"


class BridgeError(Exception):
    """Base class: a category, a stable code, a human message and a bounded diagnostic context.

    Invariants:
        * `code` is a `BridgeErrorCode`, never a free-form string, so a caller can branch on it
          without parsing prose.
        * `context` is a plain mapping of already-redacted facts. It must never hold screen
          content, hierarchy dumps, other packages' window tokens or file contents from outside
          our own app: those are the personal data the device-access policy puts off limits.
        * `retry_class` is declared by the category, so a retry decision cannot be attached to
          the wrong failure at the call site.
    """

    default_code: BridgeErrorCode = BridgeErrorCode.ADB_COMMAND_FAILED
    retry_class: RetryClass = RetryClass.RETRY_AFTER_REREAD

    def __init__(
        self,
        message: str,
        *,
        code: BridgeErrorCode | None = None,
        context: Mapping[str, Any] | None = None,
    ) -> None:
        if not message or not message.strip():
            raise ValueError("a BridgeError must carry a non-blank human message")
        super().__init__(message)
        self.code: BridgeErrorCode = code if code is not None else self.default_code
        self.message: str = message
        self.context: dict[str, Any] = dict(context) if context is not None else {}

    @property
    def machine_code(self) -> str:
        """The code as its string value, for JSON error notices."""
        return self.code.value

    def as_dict(self) -> dict[str, Any]:
        """Serialisable shape used by the gateway's rejection notice.

        Never includes the raw device output; only the bounded, already-redacted context.
        """
        return {
            "error_code": self.code.value,
            "error_class": type(self).__name__,
            "message": self.message,
            "retry_class": self.retry_class.value,
            "context": dict(self.context),
        }

    def __str__(self) -> str:
        return "[" + self.code.value + "] " + self.message


class DeviceNotFound(BridgeError):
    """The serial this transport targets is not attached to the workstation right now.

    Distinct from `DeviceUnauthorized`: the device is absent, so there is nothing for the user to
    approve on the phone. Reporting the two the same way would send the user to a dialog that does
    not exist.
    """

    default_code = BridgeErrorCode.DEVICE_NOT_FOUND
    retry_class = RetryClass.RETRY_AFTER_REREAD


class DeviceUnauthorized(BridgeError):
    """ADB reached the phone but the RSA fingerprint was never accepted on the device.

    Only the user can fix this, on the phone screen. The bridge must not retry in a loop.
    """

    default_code = BridgeErrorCode.DEVICE_UNAUTHORIZED
    retry_class = RetryClass.NEVER_RETRY


class DeviceOffline(BridgeError):
    """The device is listed by ADB but is in `offline` state, so no command will be executed.

    Declared as its own category because the brief requires `offline` to be distinguished from
    `device` and `unauthorized`; the remedy (re-plug / `adb kill-server`) differs from both.
    """

    default_code = BridgeErrorCode.DEVICE_OFFLINE
    retry_class = RetryClass.RETRY_AFTER_REREAD


class AdbFailed(BridgeError):
    """An ADB command ran and returned a non-zero status, or its output was unusable.

    Failure modes: `ADB_COMMAND_FAILED` (non-zero exit), `ADB_COMMAND_TIMEOUT` (the runner's
    timeout fired), `ADB_UNUSABLE_OUTPUT` (exit 0 but the bytes are not what the command
    promises, e.g. `screencap` output without the PNG signature), `FOCUS_UNRESOLVED` (no window
    focus line to read, which the foreground gate treats as "cannot confirm", never as "allowed").
    """

    default_code = BridgeErrorCode.ADB_COMMAND_FAILED
    retry_class = RetryClass.RETRY_AFTER_REREAD

    def __init__(
        self,
        message: str,
        *,
        code: BridgeErrorCode | None = None,
        context: Mapping[str, Any] | None = None,
        returncode: int | None = None,
        argv: tuple[str, ...] | None = None,
    ) -> None:
        super().__init__(message, code=code, context=context)
        self.returncode = returncode
        self.argv = argv


class TransportClosed(BridgeError):
    """The socket or pipe ended before the operation completed.

    Covers EOF during a frame read, a closed connection, and an outbound client that exhausted
    its bounded reconnect budget.
    """

    default_code = BridgeErrorCode.TRANSPORT_CLOSED
    retry_class = RetryClass.RETRY_AFTER_REREAD


class NotForeground(BridgeError):
    """The window with focus is not the harness app, so capture or input is refused.

    This is the machine-readable form of `docs/security/device-access-policy.md`: a screenshot of
    another app is somebody else's content. The gate raises this instead of capturing, and it
    raises when focus cannot be resolved at all - an unconfirmed state is a refusal, not a pass.
    """

    default_code = BridgeErrorCode.NOT_FOREGROUND
    retry_class = RetryClass.RETRY_AFTER_REREAD

    def __init__(
        self,
        message: str,
        *,
        code: BridgeErrorCode | None = None,
        context: Mapping[str, Any] | None = None,
        expected_package: str,
        observed_package: str,
    ) -> None:
        super().__init__(message, code=code, context=context)
        self.expected_package = expected_package
        self.observed_package = observed_package


class EnvelopeInvalid(BridgeError):
    """A JSON envelope failed validation, on encode or on decode.

    Sequence failures name the offending number and the last accepted one, and distinguish a
    repeat/out-of-order frame from a gap, because the recovery differs: a stale duplicate is
    dropped by the caller, a gap means the stream is no longer trustworthy.
    """

    default_code = BridgeErrorCode.ENVELOPE_PAYLOAD_INVALID
    retry_class = RetryClass.NEVER_RETRY


class FrameTooLarge(BridgeError):
    """A frame exceeds `MAX_FRAME_BYTES`, in either direction.

    Inbound, the connection is failed with status 1009 rather than buffered: accepting a declared
    length we then try to hold is the memory-exhaustion path this cap exists to close.
    """

    default_code = BridgeErrorCode.FRAME_TOO_LARGE
    retry_class = RetryClass.NEVER_RETRY

    def __init__(
        self,
        message: str,
        *,
        code: BridgeErrorCode | None = None,
        context: Mapping[str, Any] | None = None,
        declared_bytes: int,
        cap_bytes: int,
    ) -> None:
        payload = dict(context or {})
        payload.setdefault("declared_bytes", declared_bytes)
        payload.setdefault("cap_bytes", cap_bytes)
        super().__init__(message, code=code, context=payload)
        self.declared_bytes = declared_bytes
        self.cap_bytes = cap_bytes


class PermissionNotGranted(BridgeError):
    """The device refused an explicit `pm grant` for one permission.

    Raised only from the per-permission call. A blanket grant (`install -g`) would have made this
    state unreachable, which is exactly why this package does not offer one.
    """

    default_code = BridgeErrorCode.PERMISSION_NOT_GRANTED
    retry_class = RetryClass.NEVER_RETRY


class HierarchyRejected(BridgeError):
    """The UI dump is malformed, or the resolved target cannot be justified as tappable.

    Sub-states: `HIERARCHY_MALFORMED`, `BOUNDS_OUT_OF_RANGE` (a dump we cannot trust),
    `TARGET_ZERO_AREA` / `TARGET_DISABLED` / `TARGET_NOT_CLICKABLE` (a node that is present but
    must not be tapped), and `TARGET_NOT_FOUND` / `TARGET_AMBIGUOUS` (a matcher that did not
    resolve to exactly one node). The bridge does not tap coordinates it cannot justify, so these
    are refusals raised *before* any command is sent.
    """

    default_code = BridgeErrorCode.HIERARCHY_MALFORMED
    retry_class = RetryClass.RETRY_AFTER_REREAD


class InputRejected(BridgeError):
    """An input action was refused before leaving the workstation.

    Sub-states: an unsafe or unescapable character, an out-of-range keycode, an empty or absurdly
    long string. The command was never sent, so the device state is known unchanged - which is why
    this is not an `AdbFailed`.
    """

    default_code = BridgeErrorCode.INPUT_CHARACTER_UNSAFE
    retry_class = RetryClass.NEVER_RETRY


class HandshakeRejected(BridgeError):
    """The WebSocket opening handshake was refused, with the HTTP status the client received.

    Failure modes: `WS_HANDSHAKE_REJECTED` (not a WebSocket upgrade, missing key, bad request),
    `WS_TOKEN_MISSING` (no token, or a token that does not match), `WS_VERSION_UNSUPPORTED`
    (version header is not 13), `WS_CONNECTION_LIMIT` (`MAX_CONNECTIONS` reached).
    """

    default_code = BridgeErrorCode.WS_HANDSHAKE_REJECTED
    retry_class = RetryClass.NEVER_RETRY

    def __init__(
        self,
        message: str,
        *,
        code: BridgeErrorCode | None = None,
        context: Mapping[str, Any] | None = None,
        http_status: int,
        reason: str = "",
    ) -> None:
        payload = dict(context or {})
        payload.setdefault("http_status", http_status)
        super().__init__(message, code=code, context=payload)
        self.http_status = http_status
        self.reason = reason


__all__ = [
    "RetryClass",
    "BridgeErrorCode",
    "BridgeError",
    "DeviceNotFound",
    "DeviceUnauthorized",
    "DeviceOffline",
    "AdbFailed",
    "TransportClosed",
    "NotForeground",
    "EnvelopeInvalid",
    "FrameTooLarge",
    "PermissionNotGranted",
    "HierarchyRejected",
    "InputRejected",
    "HandshakeRejected",
]
