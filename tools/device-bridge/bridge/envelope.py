"""The JSON envelope the two halves of the bridge speak, and the ordering rules around it.

Envelope contract
-----------------
Wire form is a JSON object with exactly these five keys:

```text
version    int    must be 1
seq_no     int    strictly increasing, starting at 1, per connection
timestamp  float  epoch seconds, finite, positive; caller-supplied, never read here
event_type str    SURFACE_FRAME | TELEMETRY | INPUT_ACTION
payload    object the per-type body, validated by type
```

Validation runs on both paths: `AgentEnvelope(...)` refuses an illegal value at construction, and
`decode` re-runs the same rules on whatever came off the wire. `encode` then enforces the size cap.
There is no lenient mode, because a bridge that accepts its own malformed frames cannot report
which side produced a wrong claim.

Ordering
--------
Sequence state is per connection, not global: two connections on one port are two independent
streams, and a shared counter would let one peer's retransmit silence the other's. The first
envelope on a connection must be `seq_no == 1`. That is a strictness with a reason - a peer that
starts at 17 cannot be distinguished from a peer that already lost 16 frames, and the difference is
the whole question when a frame stream is being used as evidence.

Sizes
-----
`MAX_FRAME_BYTES` is 512 KiB. A payload above it fails before it is buffered, in both directions.
"""

from __future__ import annotations

import base64
import binascii
import enum
import json
import math
from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Any, Mapping

from .errors import BridgeErrorCode, EnvelopeInvalid, FrameTooLarge

if TYPE_CHECKING:  # pragma: no cover - typing only, keeps the module free of an import cycle
    from .capture import FrameRecord

#: The only envelope version this build produces or accepts.
ENVELOPE_VERSION = 1

#: Inbound and outbound ceiling for one frame, in bytes.
MAX_FRAME_BYTES = 512 * 1024

#: How many recent envelopes stay replayable. 200 is roughly a screenful of state transitions plus
#: slack for a reconnect, and it is a ceiling: retention past it is a file, and files are out of
#: scope for this bridge.
REPLAY_CAPACITY_DEFAULT = 200

#: The one image encoding agreed for `SURFACE_FRAME`. PNG because `screencap -p` produces it and
#: because a lossless frame is what a UI-hierarchy claim is checked against.
IMAGE_ENCODING_PNG = "image/png"
AGREED_IMAGE_ENCODINGS: frozenset[str] = frozenset({IMAGE_ENCODING_PNG})

_INPUT_ACTION_NAMES: frozenset[str] = frozenset({"tap", "long_press", "text", "keyevent"})
_REQUIRED_KEYS: tuple[str, ...] = ("version", "seq_no", "timestamp", "event_type", "payload")


class EventType(enum.Enum):
    """The event vocabulary. Values are the wire strings, so the enum name is never a guess."""

    SURFACE_FRAME = "SURFACE_FRAME"
    TELEMETRY = "TELEMETRY"
    INPUT_ACTION = "INPUT_ACTION"

    @classmethod
    def from_wire(cls, name: object) -> "EventType":
        """Resolve a wire string, refusing anything not in the vocabulary.

        Raises:
            EnvelopeInvalid(ENVELOPE_EVENT_TYPE_UNKNOWN): unknown, non-string, or blank. The
                alternative - mapping an unrecognised type to a plausible one - is how a telemetry
                frame gets read as a frame of the screen.
        """
        if not isinstance(name, str) or not name.strip():
            raise EnvelopeInvalid(
                "event_type must be a non-blank string, got " + repr(name),
                code=BridgeErrorCode.ENVELOPE_EVENT_TYPE_UNKNOWN,
            )
        for member in cls:
            if member.value == name:
                return member
        raise EnvelopeInvalid(
            "unknown event_type " + repr(name) + "; this build speaks "
            + ", ".join(sorted(member.value for member in cls)),
            code=BridgeErrorCode.ENVELOPE_EVENT_TYPE_UNKNOWN,
            context={"reported_event_type": name},
        )


@dataclass(frozen=True)
class AgentEnvelope:
    """One validated envelope.

    Construction is the validation: an instance of this type is a claim the bridge is allowed to
    make, and nothing that fails a rule can exist as one. `payload` is copied at construction so a
    caller cannot change what was already sequenced and counted.
    """

    version: int
    seq_no: int
    timestamp: float
    event_type: EventType
    payload: Mapping[str, Any] = field(default_factory=dict)

    def __post_init__(self) -> None:
        if isinstance(self.version, bool) or not isinstance(self.version, int):
            raise EnvelopeInvalid(
                "version must be an int, got " + type(self.version).__name__,
                code=BridgeErrorCode.ENVELOPE_FIELD_TYPE_INVALID,
            )
        if self.version != ENVELOPE_VERSION:
            raise EnvelopeInvalid(
                "envelope version " + str(self.version) + " is not the agreed version "
                + str(ENVELOPE_VERSION) + "; a version we cannot speak is refused rather than "
                "downgraded",
                code=BridgeErrorCode.ENVELOPE_VERSION_UNSUPPORTED,
                context={"reported_version": self.version, "agreed_version": ENVELOPE_VERSION},
            )
        if isinstance(self.seq_no, bool) or not isinstance(self.seq_no, int):
            raise EnvelopeInvalid(
                "seq_no must be an int, got " + type(self.seq_no).__name__,
                code=BridgeErrorCode.ENVELOPE_FIELD_TYPE_INVALID,
            )
        if self.seq_no <= 0:
            raise EnvelopeInvalid(
                "seq_no must be strictly positive, got " + str(self.seq_no)
                + "; 0 and negatives are not positions in a sequence",
                code=BridgeErrorCode.ENVELOPE_SEQ_NOT_POSITIVE,
                context={"seq_no": self.seq_no},
            )
        if isinstance(self.timestamp, bool) or not isinstance(self.timestamp, (int, float)):
            raise EnvelopeInvalid(
                "timestamp must be a float of epoch seconds, got " + type(self.timestamp).__name__,
                code=BridgeErrorCode.ENVELOPE_FIELD_TYPE_INVALID,
            )
        stamp = float(self.timestamp)
        if not math.isfinite(stamp):
            raise EnvelopeInvalid(
                "timestamp " + repr(self.timestamp) + " is not finite",
                code=BridgeErrorCode.ENVELOPE_FIELD_TYPE_INVALID,
            )
        if stamp <= 0:
            raise EnvelopeInvalid(
                "timestamp must be positive epoch seconds, got " + repr(self.timestamp)
                + "; a zero timestamp is a placeholder, not a time",
                code=BridgeErrorCode.ENVELOPE_FIELD_TYPE_INVALID,
            )
        if not isinstance(self.event_type, EventType):
            raise EnvelopeInvalid(
                "event_type must be an EventType member, got " + repr(self.event_type),
                code=BridgeErrorCode.ENVELOPE_EVENT_TYPE_UNKNOWN,
            )
        if isinstance(self.payload, Mapping):
            payload = dict(self.payload)
        else:
            raise EnvelopeInvalid(
                "payload must be a JSON object, got " + type(self.payload).__name__,
                code=BridgeErrorCode.ENVELOPE_PAYLOAD_INVALID,
            )
        object.__setattr__(self, "payload", payload)
        _validate_payload(self.event_type, payload)

    # ---------------------------------------------------------------- encoding

    def to_wire_dict(self) -> dict[str, Any]:
        """The JSON-ready shape. `event_type` becomes its wire string, never its Python name."""
        return {
            "version": self.version,
            "seq_no": self.seq_no,
            "timestamp": float(self.timestamp),
            "event_type": self.event_type.value,
            "payload": dict(self.payload),
        }

    def encode(self, max_bytes: int = MAX_FRAME_BYTES) -> str:
        """Serialise, enforcing the size cap on the way out.

        Key order is sorted so two identical envelopes encode to identical bytes, which is what
        makes a recorded frame comparable to a re-sent one.

        Raises:
            FrameTooLarge: the encoded bytes exceed `max_bytes`. Refusing here stops the bridge from
                announcing a frame it would then fail to deliver.
        """
        try:
            text = json.dumps(self.to_wire_dict(), sort_keys=True, separators=(",", ":"), ensure_ascii=True)
        except (TypeError, ValueError) as exc:
            raise EnvelopeInvalid(
                "payload is not JSON-serialisable: " + type(exc).__name__,
                code=BridgeErrorCode.ENVELOPE_PAYLOAD_INVALID,
            ) from exc
        size = len(text.encode("utf-8"))
        if size > max_bytes:
            raise FrameTooLarge(
                "encoded envelope is " + str(size) + " bytes, above MAX_FRAME_BYTES ("
                + str(max_bytes) + ")",
                declared_bytes=size,
                cap_bytes=max_bytes,
                context={"event_type": self.event_type.value, "seq_no": self.seq_no},
            )
        return text

    def encoded_size_bytes(self) -> int:
        return len(self.encode().encode("utf-8"))

    # ---------------------------------------------------------------- decoding

    @classmethod
    def decode(cls, text: str) -> "AgentEnvelope":
        """Parse one wire envelope and re-run every construction rule.

        Raises:
            EnvelopeInvalid: not JSON (`ENVELOPE_NOT_JSON`), not an object, a missing key
                (`ENVELOPE_FIELD_MISSING`, naming the key), an unexpected key
                (`ENVELOPE_UNKNOWN_FIELD`), or any construction refusal.
        """
        if not isinstance(text, str) or not text.strip():
            raise EnvelopeInvalid(
                "decode requires a non-blank string",
                code=BridgeErrorCode.ENVELOPE_NOT_JSON,
            )
        try:
            parsed = json.loads(text)
        except json.JSONDecodeError as exc:
            raise EnvelopeInvalid(
                "frame is not JSON: " + exc.msg + " at offset " + str(exc.pos),
                code=BridgeErrorCode.ENVELOPE_NOT_JSON,
            ) from exc
        if not isinstance(parsed, dict):
            raise EnvelopeInvalid(
                "an envelope must be a JSON object, got " + type(parsed).__name__,
                code=BridgeErrorCode.ENVELOPE_FIELD_TYPE_INVALID,
            )
        missing = [key for key in _REQUIRED_KEYS if key not in parsed]
        if missing:
            raise EnvelopeInvalid(
                "envelope is missing required field(s): " + ", ".join(missing)
                + "; no field is inferred from a default",
                code=BridgeErrorCode.ENVELOPE_FIELD_MISSING,
                context={"missing": missing},
            )
        unexpected = sorted(key for key in parsed if key not in _REQUIRED_KEYS)
        if unexpected:
            raise EnvelopeInvalid(
                "envelope carries field(s) this version does not define: " + ", ".join(unexpected),
                code=BridgeErrorCode.ENVELOPE_UNKNOWN_FIELD,
                context={"unexpected": unexpected},
            )
        return cls(
            version=parsed["version"],
            seq_no=parsed["seq_no"],
            timestamp=parsed["timestamp"],
            event_type=EventType.from_wire(parsed["event_type"]),
            payload=parsed["payload"],
        )

    @classmethod
    def decode_within_cap(cls, text: str, max_bytes: int = MAX_FRAME_BYTES) -> "AgentEnvelope":
        """Decode, after checking the inbound byte cap.

        The size check happens on the bytes, before parsing: `json.loads` on an unbounded string is
        the memory-exhaustion case the cap exists for.
        """
        raw = text.encode("utf-8") if isinstance(text, str) else text
        size = len(raw)
        if size > max_bytes:
            raise FrameTooLarge(
                "inbound frame is " + str(size) + " bytes, above MAX_FRAME_BYTES (" + str(max_bytes) + ")",
                declared_bytes=size,
                cap_bytes=max_bytes,
            )
        if not isinstance(text, str):
            raise EnvelopeInvalid(
                "a frame must be text, got " + type(text).__name__,
                code=BridgeErrorCode.ENVELOPE_FIELD_TYPE_INVALID,
            )
        return cls.decode(text)

    # ---------------------------------------------------------------- producers

    @classmethod
    def from_frame(
        cls,
        frame: "FrameRecord",
        seq_no: int,
        timestamp: float,
    ) -> "AgentEnvelope":
        """Wrap a captured screen as a `SURFACE_FRAME`.

        The base64 expansion (4/3) plus the JSON envelope is why a device PNG near the cap does not
        fit: the refusal is in `encode`, and a caller that hits it has a frame to downscale, not a
        buffer to enlarge.
        """
        png = frame.png_bytes
        if len(png) > MAX_FRAME_BYTES:
            raise FrameTooLarge(
                "captured PNG is " + str(len(png)) + " bytes, above MAX_FRAME_BYTES ("
                + str(MAX_FRAME_BYTES) + ") before base64 expansion; the frame is not streamed",
                declared_bytes=len(png),
                cap_bytes=MAX_FRAME_BYTES,
            )
        return cls(
            version=ENVELOPE_VERSION,
            seq_no=seq_no,
            timestamp=timestamp,
            event_type=EventType.SURFACE_FRAME,
            payload={
                "encoding": IMAGE_ENCODING_PNG,
                "data_base64": base64.b64encode(png).decode("ascii"),
                "width": frame.width_hint,
                "height": frame.height_hint,
                "captured_at_epoch_ms": frame.captured_at_epoch_ms,
            },
        )


class EnvelopeNotice:
    """The reply this bridge sends when a frame was refused.

    Not an `AgentEnvelope`: a notice is about the stream, not device evidence, and letting it carry a
    `seq_no` would invite a peer to treat a refusal as part of the sequence. It is deliberately small:
    the code, a message, the sequence position we expected, and a redacted context.

    The point of its existence is the gateway rule "a rejected frame is never dropped silently" - the
    counter is the audit trail, this notice is how the sender learns about it.
    """

    __slots__ = ("code", "message", "last_accepted_seq_no", "context")

    def __init__(
        self,
        *,
        code: BridgeErrorCode,
        message: str,
        last_accepted_seq_no: int | None = None,
        context: Mapping[str, Any] | None = None,
    ) -> None:
        if not isinstance(code, BridgeErrorCode):
            raise TypeError("an EnvelopeNotice needs a BridgeErrorCode")
        if not message.strip():
            raise ValueError("an EnvelopeNotice must say why")
        self.code = code
        self.message = message
        self.last_accepted_seq_no = last_accepted_seq_no
        self.context = dict(context or {})

    def as_dict(self) -> dict[str, Any]:
        body: dict[str, Any] = {
            "notice": "envelope_rejected",
            "error_code": self.code.value,
            "message": self.message,
        }
        if self.last_accepted_seq_no is not None:
            body["last_accepted_seq_no"] = self.last_accepted_seq_no
        if self.context:
            body["context"] = dict(self.context)
        return body

    def as_text(self) -> str:
        return json.dumps(self.as_dict(), sort_keys=True, separators=(",", ":"), ensure_ascii=True)


class SequenceLedger:
    """Per-connection monotonic sequence state.

    One ledger per connection, owned by the gateway. It answers a single question - does this frame
    continue this stream? - and names the numbers when it says no.
    """

    def __init__(self, *, first_seq_no: int = 1) -> None:
        if first_seq_no != 1:
            raise ValueError("first_seq_no is fixed at 1; a stream does not get to pick its origin")
        self._last_accepted: int | None = None
        self._accepted = 0
        self._rejected = 0

    @property
    def last_accepted_seq_no(self) -> int | None:
        """`None` until the first frame is accepted, meaning "nothing has been sequenced yet"."""
        return self._last_accepted

    @property
    def accepted_count(self) -> int:
        return self._accepted

    @property
    def rejected_count(self) -> int:
        return self._rejected

    def accept(self, envelope: AgentEnvelope) -> AgentEnvelope:
        """Record `envelope` as the next in sequence, or raise.

        Raises:
            EnvelopeInvalid(ENVELOPE_SEQ_START_NOT_ONE): the first frame of a connection is not 1.
            EnvelopeInvalid(ENVELOPE_SEQ_OUT_OF_ORDER): a repeat or a stale frame; the message names
                the received seq and the last accepted one, and says which of the two it is.
            EnvelopeInvalid(ENVELOPE_SEQ_GAP): a jump forward; the message gives the expected seq and
                how many frames are missing. A gap is not a reorder - after one, the stream cannot be
                claimed to be complete.
        """
        received = envelope.seq_no
        if self._last_accepted is None:
            if received != 1:
                self._rejected += 1
                raise EnvelopeInvalid(
                    "the first envelope on this connection carries seq_no " + str(received)
                    + "; sequences start at 1, so this stream is already missing frames",
                    code=BridgeErrorCode.ENVELOPE_SEQ_START_NOT_ONE,
                    context={"received_seq_no": received, "expected_seq_no": 1},
                )
        elif received <= self._last_accepted:
            self._rejected += 1
            is_repeat = received == self._last_accepted
            relation = "a repeat of" if is_repeat else "older than"
            relation_key = "repeat" if is_repeat else "older"
            raise EnvelopeInvalid(
                "envelope seq_no " + str(received) + " is " + relation + " the last accepted seq_no "
                + str(self._last_accepted) + " on this connection; frames are not reordered into a "
                "sequence",
                code=BridgeErrorCode.ENVELOPE_SEQ_OUT_OF_ORDER,
                context={
                    "received_seq_no": received,
                    "last_accepted_seq_no": self._last_accepted,
                    "relation": relation_key,
                },
            )
        elif received > self._last_accepted + 1:
            missing = received - self._last_accepted - 1
            self._rejected += 1
            raise EnvelopeInvalid(
                "envelope seq_no " + str(received) + " leaves a gap of " + str(missing)
                + " frame(s) after the last accepted seq_no " + str(self._last_accepted)
                + "; expected " + str(self._last_accepted + 1),
                code=BridgeErrorCode.ENVELOPE_SEQ_GAP,
                context={
                    "received_seq_no": received,
                    "last_accepted_seq_no": self._last_accepted,
                    "expected_seq_no": self._last_accepted + 1,
                    "missing_frames": missing,
                },
            )
        self._last_accepted = received
        self._accepted += 1
        return envelope


class ReplayBuffer:
    """The last `capacity` envelopes, in arrival order, with wraparound handled honestly.

    A fixed-capacity ring, not a growing list: after wraparound the first entry is the oldest
    *retained* envelope, not the oldest ever sent. `evictions` and `appended_total` make that
    distinction visible, and `is_gap_before_replay_window` states it to a caller that asks for a
    frame that is already gone - so "here is everything since N" never quietly means "here is a
    subset of everything since N".
    """

    def __init__(self, capacity: int = REPLAY_CAPACITY_DEFAULT) -> None:
        if isinstance(capacity, bool) or not isinstance(capacity, int):
            raise TypeError("ReplayBuffer capacity must be an int")
        if capacity < 1:
            raise ValueError("ReplayBuffer capacity must be at least 1")
        self._capacity = capacity
        self._entries: list[AgentEnvelope] = []
        self._evictions = 0
        self._appended_total = 0

    @property
    def capacity(self) -> int:
        return self._capacity

    @property
    def evictions(self) -> int:
        return self._evictions

    @property
    def appended_total(self) -> int:
        return self._appended_total

    def append(self, envelope: AgentEnvelope) -> AgentEnvelope | None:
        """Add an envelope, returning the one it displaced, or `None` when nothing was evicted."""
        if not isinstance(envelope, AgentEnvelope):
            raise TypeError("ReplayBuffer holds AgentEnvelope values only")
        evicted: AgentEnvelope | None = None
        if len(self._entries) == self._capacity:
            evicted = self._entries.pop(0)
            self._evictions += 1
        self._entries.append(envelope)
        self._appended_total += 1
        return evicted

    def snapshot(self) -> tuple[AgentEnvelope, ...]:
        """Oldest retained first. Correct after wraparound because retention is insertion-ordered."""
        return tuple(self._entries)

    def latest_seq_no(self) -> int | None:
        """`None` when empty: no frame has been retained, which is not seq_no 0."""
        if not self._entries:
            return None
        return self._entries[-1].seq_no

    def oldest_retained_seq_no(self) -> int | None:
        if not self._entries:
            return None
        return self._entries[0].seq_no

    def replay_since(self, seq_no: int) -> tuple[AgentEnvelope, ...]:
        """Envelopes with `seq_no` strictly greater than the one given, oldest first.

        Use `is_gap_before_replay_window` alongside this whenever the answer is being treated as a
        complete replay: the list returned here is complete only within what is retained.

        Raises:
            EnvelopeInvalid: `seq_no` is not a sequence position. A caller asking for "everything
                after -5" has a bug, and answering it would hide it.
        """
        if isinstance(seq_no, bool) or not isinstance(seq_no, int) or seq_no < 0:
            raise EnvelopeInvalid(
                "replay_since requires a non-negative int seq_no, got " + repr(seq_no),
                code=BridgeErrorCode.ENVELOPE_SEQ_NOT_POSITIVE,
            )
        return tuple(envelope for envelope in self._entries if envelope.seq_no > seq_no)

    def is_gap_before_replay_window(self, seq_no: int) -> bool:
        """True when frames between `seq_no` and the oldest retained one have been evicted."""
        oldest = self.oldest_retained_seq_no()
        if oldest is None or seq_no >= oldest:
            return False
        return seq_no + 1 < oldest

    def clear(self) -> None:
        """Drop retained envelopes. Counters stay, because what was evicted is history."""
        self._entries.clear()

    def __len__(self) -> int:
        return len(self._entries)

    def __repr__(self) -> str:
        return (
            "ReplayBuffer(size=" + str(len(self._entries)) + ", capacity=" + str(self._capacity)
            + ", evictions=" + str(self._evictions) + ")"
        )


def _validate_payload(event_type: EventType, payload: dict[str, Any]) -> None:
    """Per-type payload rules. Shared by construction and by decode."""
    if event_type is EventType.SURFACE_FRAME:
        _validate_surface_frame(payload)
        return
    if event_type is EventType.TELEMETRY:
        if not payload:
            raise EnvelopeInvalid(
                "a TELEMETRY envelope with no fields reports nothing; an empty payload is refused "
                "rather than stored as a beat",
                code=BridgeErrorCode.ENVELOPE_PAYLOAD_INVALID,
            )
        for key in payload:
            if not isinstance(key, str) or not key.strip():
                raise EnvelopeInvalid(
                    "TELEMETRY payload keys must be non-blank strings",
                    code=BridgeErrorCode.ENVELOPE_PAYLOAD_INVALID,
                )
        return
    if event_type is EventType.INPUT_ACTION:
        action = payload.get("action")
        if not isinstance(action, str) or action not in _INPUT_ACTION_NAMES:
            raise EnvelopeInvalid(
                "INPUT_ACTION payload needs an `action` in {"
                + ", ".join(sorted(_INPUT_ACTION_NAMES))
                + "}, got " + repr(action),
                code=BridgeErrorCode.ENVELOPE_PAYLOAD_INVALID,
            )
        params = payload.get("params")
        if params is not None and not isinstance(params, dict):
            raise EnvelopeInvalid(
                "INPUT_ACTION `params` must be a JSON object when present, got "
                + type(params).__name__,
                code=BridgeErrorCode.ENVELOPE_PAYLOAD_INVALID,
            )
        return
    raise EnvelopeInvalid(
        "no payload rules for event type " + repr(event_type),
        code=BridgeErrorCode.ENVELOPE_EVENT_TYPE_UNKNOWN,
    )


def _validate_surface_frame(payload: dict[str, Any]) -> None:
    encoding = payload.get("encoding")
    if encoding is None:
        raise EnvelopeInvalid(
            "a SURFACE_FRAME must declare its image encoding; the bridge does not guess one from "
            "the bytes it happens to hold",
            code=BridgeErrorCode.ENVELOPE_ENCODING_UNAGREED,
        )
    if not isinstance(encoding, str) or encoding not in AGREED_IMAGE_ENCODINGS:
        raise EnvelopeInvalid(
            "SURFACE_FRAME claims encoding " + repr(encoding) + ", which was not agreed; this build "
            "accepts " + ", ".join(sorted(AGREED_IMAGE_ENCODINGS)),
            code=BridgeErrorCode.ENVELOPE_ENCODING_UNAGREED,
            context={"claimed_encoding": encoding if isinstance(encoding, str) else repr(encoding)},
        )
    data = payload.get("data_base64")
    if not isinstance(data, str) or not data:
        raise EnvelopeInvalid(
            "SURFACE_FRAME `data_base64` must be a non-blank string",
            code=BridgeErrorCode.ENVELOPE_PAYLOAD_INVALID,
        )
    try:
        decoded = base64.b64decode(data, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise EnvelopeInvalid(
            "SURFACE_FRAME `data_base64` is not valid base64",
            code=BridgeErrorCode.ENVELOPE_PAYLOAD_INVALID,
        ) from exc
    if not decoded:
        raise EnvelopeInvalid(
            "SURFACE_FRAME carries no image bytes",
            code=BridgeErrorCode.ENVELOPE_PAYLOAD_INVALID,
        )
    if len(decoded) > MAX_FRAME_BYTES:
        raise FrameTooLarge(
            "SURFACE_FRAME image is " + str(len(decoded)) + " bytes, above MAX_FRAME_BYTES ("
            + str(MAX_FRAME_BYTES) + ")",
            declared_bytes=len(decoded),
            cap_bytes=MAX_FRAME_BYTES,
        )
    for dimension in ("width", "height"):
        value = payload.get(dimension)
        if isinstance(value, bool) or not isinstance(value, int):
            raise EnvelopeInvalid(
                "SURFACE_FRAME `" + dimension + "` must be an int pixel count",
                code=BridgeErrorCode.ENVELOPE_PAYLOAD_INVALID,
            )
        if value <= 0:
            raise EnvelopeInvalid(
                "SURFACE_FRAME `" + dimension + "` must be positive, got " + str(value),
                code=BridgeErrorCode.ENVELOPE_PAYLOAD_INVALID,
            )


__all__ = [
    "ENVELOPE_VERSION",
    "MAX_FRAME_BYTES",
    "REPLAY_CAPACITY_DEFAULT",
    "IMAGE_ENCODING_PNG",
    "AGREED_IMAGE_ENCODINGS",
    "EventType",
    "AgentEnvelope",
    "EnvelopeNotice",
    "SequenceLedger",
    "ReplayBuffer",
]
