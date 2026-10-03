"""The live UI hierarchy, as read from `uiautomator dump`, and what may be tapped in it.

Parsing discipline
------------------
`uiautomator` emits an XML document of `<node>` elements. It is parsed with
`xml.etree.ElementTree` from the standard library, and the record type holds exactly the fields the
bridge can act on: class, resource-id, text, content-desc, bounds, clickable, enabled, focused,
package, index and children. Everything else in a dump (`checkable`, `scrollable`, `hint`,
`drawing-order`, `password`, `selected`) is dropped, because a field we do not act on is a field we
cannot be accused of harvesting.

Nothing is defaulted. A missing required attribute, an unparsable boolean, a non-integer index or
bounds outside the display envelope all raise `HierarchyRejected` naming the node: a hierarchy we
cannot fully read is a hierarchy we must not act through.

Text vs absence
---------------
A dump writes `text=""` for a view with no text. In the record that becomes `None`, meaning "not
reported", per the three-tier rule in `docs/phases/phase-0/specs.md` section 2.2: absence is
absence, and an empty string would later match an empty query.
"""

from __future__ import annotations

import re
import xml.etree.ElementTree as ElementTree
from dataclasses import dataclass
from typing import Callable, Iterable

from .errors import BridgeErrorCode, HierarchyRejected

#: Bounds ceiling. A phone panel is far below this, so a coordinate above it is a corrupt dump
#: rather than a screen position, and must not become a tap.
MAX_BOUND_COORDINATE = 100_000

_BOUNDS_RE = re.compile(r"^\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]$")

#: The dump attributes that have no sensible "absent" reading. Missing one is a malformed dump.
_REQUIRED_NODE_ATTRIBUTES: tuple[str, ...] = (
    "index",
    "bounds",
    "class",
    "package",
    "clickable",
    "enabled",
    "focused",
)

#: Attributes whose empty string is a real absence rather than a value.
_OPTIONAL_TEXT_ATTRIBUTES: tuple[str, ...] = ("text", "resource-id", "content-desc")

Bounds = tuple[int, int, int, int]


@dataclass(frozen=True)
class UiElement:
    """One node of the hierarchy, with its children.

    Field notes:
        * `clazz` is the dump's `class` attribute (`class` being a reserved word in Python).
        * `resource_id` is the fully qualified form as the device spells it,
          `package:id/local_name`, or `None` when the view has no id.
        * `bounds` is `(x1, y1, x2, y2)` in display pixels, non-negative, `x2 >= x1`, `y2 >= y1`.
          Zero area is representable, because a view that has not been laid out really reports it -
          and it is exactly what `assert_resolvable` exists to refuse.
        * `children` is a tuple, so a parsed tree is immutable and cannot be edited into a shape
          that no longer matches the device.
    """

    clazz: str
    resource_id: str | None
    text: str | None
    content_desc: str | None
    bounds: Bounds
    clickable: bool
    enabled: bool
    focused: bool
    package: str
    index: int
    children: tuple["UiElement", ...]

    @property
    def centre(self) -> tuple[int, int]:
        return centre_of(self)

    @property
    def area(self) -> int:
        x1, y1, x2, y2 = self.bounds
        return (x2 - x1) * (y2 - y1)

    @property
    def short_id(self) -> str | None:
        """The local resource name (`bridge_probe_button`), for messages only - never for matching."""
        if self.resource_id is None:
            return None
        return self.resource_id.rsplit("/", 1)[-1]

    def describe(self) -> str:
        """A redaction-safe identity: class, id, and whether text is present - never the text itself.

        Text on screen can be anything the user was looking at a moment ago, so it is reported as
        "text present/absent" here and only compared explicitly by `find_by_text`.
        """
        return (
            "class=" + self.clazz
            + " resource_id=" + (self.resource_id if self.resource_id is not None else "<none>")
            + " index=" + str(self.index)
            + " bounds=" + format_bounds(self.bounds)
        )

    def walk(self) -> tuple["UiElement", ...]:
        """Depth-first, self first, in document order - so a match set is deterministic."""
        collected: list[UiElement] = []
        _walk_into(self, collected)
        return tuple(collected)


@dataclass(frozen=True)
class HierarchyDump:
    """A parsed dump: its root nodes plus the rotation the `hierarchy` element reported.

    `rotation` is `None` when the dump did not report it, which is a statement about the dump, not
    about the device.
    """

    roots: tuple[UiElement, ...]
    rotation: int | None

    def elements(self) -> tuple[UiElement, ...]:
        out: list[UiElement] = []
        for root in self.roots:
            out.extend(root.walk())
        return tuple(out)

    def find_by_resource_id(self, resource_id: str) -> tuple[UiElement, ...]:
        """Exact match on the qualified id. Use `bridge.harness_resource_id` to build it.

        An exact match is deliberate: a suffix match would let a foreign app's `:id/bridge_status`
        satisfy a harness query, and the tap would land outside the app the policy scopes us to.
        """
        if not resource_id:
            raise ValueError("find_by_resource_id requires a non-empty id")
        return self.all_matching(lambda element: element.resource_id == resource_id)

    def find_by_text(self, text: str) -> tuple[UiElement, ...]:
        if not text:
            raise ValueError(
                "find_by_text requires a non-empty needle; an empty needle would match every view "
                "that reports no text at all"
            )
        return self.all_matching(lambda element: element.text == text)

    def find_by_content_desc(self, content_desc: str) -> tuple[UiElement, ...]:
        if not content_desc:
            raise ValueError("find_by_content_desc requires a non-empty needle")
        return self.all_matching(lambda element: element.content_desc == content_desc)

    def all_matching(self, predicate: Callable[[UiElement], bool]) -> tuple[UiElement, ...]:
        """Every element satisfying `predicate`, in document order."""
        return tuple(element for element in self.elements() if predicate(element))

    def require_unique(self, elements: tuple[UiElement, ...], matcher: str) -> UiElement:
        """Reduce a match set to one element or refuse.

        Two matches means the bridge would be choosing between coordinates by list order, which is
        a blind tap with extra steps.
        """
        if len(elements) == 0:
            raise HierarchyRejected(
                "no element matches " + matcher + " in this dump; the hierarchy is re-read rather "
                "than guessed at",
                code=BridgeErrorCode.TARGET_NOT_FOUND,
                context={"matcher": matcher},
            )
        if len(elements) > 1:
            raise HierarchyRejected(
                matcher + " matched " + str(len(elements)) + " elements; refusing to pick one",
                code=BridgeErrorCode.TARGET_AMBIGUOUS,
                context={"matcher": matcher, "match_count": len(elements)},
            )
        return elements[0]


def parse_dump(xml_text: str) -> HierarchyDump:
    """Parse `uiautomator dump` output into a `HierarchyDump`.

    Accepts a `<hierarchy>` root (the normal form) or a single `<node>` root (a fragment some
    builds emit).

    Raises:
        HierarchyRejected(HIERARCHY_MALFORMED): not XML, not a hierarchy/node root, a required
            attribute missing or unparsable, or bounds out of range. No partial tree is returned:
            a half-read hierarchy is how a tap lands on the wrong view.
    """
    if not isinstance(xml_text, str) or not xml_text.strip():
        raise _malformed("the dump is empty", byte_count=len(xml_text) if isinstance(xml_text, str) else "n/a")
    try:
        root = ElementTree.fromstring(xml_text)
    except ElementTree.ParseError as exc:
        raise _malformed("the dump is not well-formed XML", parse_error=str(exc)) from exc
    if root.tag == "hierarchy":
        rotation_attr = root.attrib.get("rotation")
        rotation = _parse_rotation(rotation_attr) if rotation_attr is not None else None
        roots: list[UiElement] = []
        for child in list(root):
            if child.tag != "node":
                raise _malformed("a child of <hierarchy> is not a <node>", tag=child.tag)
            roots.append(_element_from(child))
        if not roots:
            raise _malformed("the hierarchy has no nodes")
        return HierarchyDump(roots=tuple(roots), rotation=rotation)
    if root.tag == "node":
        return HierarchyDump(roots=(_element_from(root),), rotation=None)
    raise _malformed("unexpected document root", tag=root.tag)


def _parse_rotation(raw: str | None) -> int | None:
    if raw is None:
        return None
    stripped = raw.strip()
    if not stripped:
        return None
    try:
        value = int(stripped)
    except ValueError as exc:
        raise _malformed("rotation attribute is not an integer", rotation=raw) from exc
    if value not in (0, 90, 180, 270):
        raise _malformed("rotation attribute is not a multiple of 90", rotation=value)
    return value


def _element_from(node: ElementTree.Element) -> UiElement:
    attrib = node.attrib
    for attribute in _REQUIRED_NODE_ATTRIBUTES:
        if attribute not in attrib:
            raise _malformed(
                "a node is missing the required attribute " + repr(attribute),
                present=sorted(attrib),
            )
    index_raw = attrib["index"].strip()
    try:
        index = int(index_raw)
    except ValueError as exc:
        raise _malformed("index is not an integer", index=attrib["index"]) from exc
    if index < 0:
        raise _malformed("index is negative", index=index)
    clazz = attrib["class"].strip()
    if not clazz:
        raise _malformed("class attribute is blank")
    package = attrib["package"].strip()
    if not package:
        raise _malformed("package attribute is blank", node_class=clazz)
    optional: dict[str, str | None] = {}
    for attribute in _OPTIONAL_TEXT_ATTRIBUTES:
        raw = attrib.get(attribute)
        optional[attribute] = raw.strip() if raw is not None and raw.strip() else None
    children = tuple(_element_from(child) for child in list(node))
    return UiElement(
        clazz=clazz,
        resource_id=optional["resource-id"],
        text=optional["text"],
        content_desc=optional["content-desc"],
        bounds=parse_bounds(attrib["bounds"]),
        clickable=_parse_bool(attrib["clickable"], "clickable"),
        enabled=_parse_bool(attrib["enabled"], "enabled"),
        focused=_parse_bool(attrib["focused"], "focused"),
        package=package,
        index=index,
        children=children,
    )


def _parse_bool(raw: str, attribute: str) -> bool:
    value = raw.strip().lower()
    if value == "true":
        return True
    if value == "false":
        return False
    raise _malformed(
        attribute + " is not true or false", attribute=attribute, reported=raw
    )


def parse_bounds(raw: str) -> Bounds:
    """Parse `[x1,y1][x2,y2]` into four ints, rejecting shapes that cannot be a screen rect.

    Raises:
        HierarchyRejected(BOUNDS_OUT_OF_RANGE): unparsable text, a negative origin, an inverted
            rectangle (`x2 < x1`), or a coordinate above `MAX_BOUND_COORDINATE`. Clamping or
            defaulting would put a tap at a coordinate no view occupies.
    """
    match = _BOUNDS_RE.match(raw.strip())
    if match is None:
        raise HierarchyRejected(
            "bounds are not in [x1,y1][x2,y2] form",
            code=BridgeErrorCode.BOUNDS_OUT_OF_RANGE,
            context={"reported_bounds": raw},
        )
    x1, y1, x2, y2 = (int(group) for group in match.groups())
    if x1 < 0 or y1 < 0:
        raise HierarchyRejected(
            "bounds have a negative origin",
            code=BridgeErrorCode.BOUNDS_OUT_OF_RANGE,
            context={"reported_bounds": raw},
        )
    if x2 < x1 or y2 < y1:
        raise HierarchyRejected(
            "bounds are inverted",
            code=BridgeErrorCode.BOUNDS_OUT_OF_RANGE,
            context={"reported_bounds": raw},
        )
    if max(x1, y1, x2, y2) > MAX_BOUND_COORDINATE:
        raise HierarchyRejected(
            "bounds exceed the coordinate envelope this bridge will address",
            code=BridgeErrorCode.BOUNDS_OUT_OF_RANGE,
            context={"reported_bounds": raw, "ceiling": MAX_BOUND_COORDINATE},
        )
    return (x1, y1, x2, y2)


def format_bounds(bounds: Bounds) -> str:
    x1, y1, x2, y2 = bounds
    return "[" + str(x1) + "," + str(y1) + "][" + str(x2) + "," + str(y2) + "]"


def centre_of(element: UiElement) -> tuple[int, int]:
    """The integer centre of an element's bounds: `((x1+x2)//2, (y1+y2)//2)`.

    This is the only coordinate derivation the bridge offers, and it is derived from bounds the
    device reported a moment ago - not from a remembered layout. Odd/extent cases floor toward the
    origin, which is what `input tap` expects for a pixel coordinate.

    Raises:
        HierarchyRejected: `element.bounds` is not a 4-tuple. Only reachable if a caller built a
            record by hand, so it is a programmer error surfaced as a refusal.
    """
    if len(element.bounds) != 4:
        raise HierarchyRejected(
            "cannot compute a centre from bounds that are not a 4-tuple",
            code=BridgeErrorCode.BOUNDS_OUT_OF_RANGE,
            context={"bounds": str(element.bounds)},
        )
    x1, y1, x2, y2 = element.bounds
    return ((x1 + x2) // 2, (y1 + y2) // 2)


@dataclass(frozen=True)
class TapBlock:
    """Why an element may not be tapped. `code` is the machine key, `reason` the human sentence."""

    code: BridgeErrorCode
    reason: str

    def as_dict(self) -> dict[str, object]:
        return {"code": self.code.value, "reason": self.reason}


def assert_resolvable(element: UiElement, allow_non_clickable: bool = False) -> TapBlock | None:
    """Return why `element` cannot be tapped, or `None` when it can.

    The brief asks for a function that *returns the reason* rather than one that only raises, so a
    caller can put the reason into an envelope and tell the user what it found. Raising is
    `require_resolvable`, which is what `bridge.input_actions` uses before sending a command.

    Rules, in order of how much they hurt:
        1. zero-area bounds - nothing is there. A view that has not been laid out reports a
           degenerate rectangle, and a tap at its "centre" is a tap on whatever is behind it.
        2. `enabled == False` - a real rectangle that will ignore the tap, so the run would record a
           success the device never had.
        3. `clickable == False` - the view does not handle touches itself. `allow_non_clickable=True`
           is the escape hatch for e.g. a `CheckBox` label or a scroll target; it must be passed
           deliberately, by name, at the call site. It has no default of `True` anywhere in this
           package for that reason.
    """
    if len(element.bounds) != 4:
        raise HierarchyRejected(
            "element bounds are not a 4-tuple",
            code=BridgeErrorCode.BOUNDS_OUT_OF_RANGE,
            context={"element": element.describe()},
        )
    x1, y1, x2, y2 = element.bounds
    if x2 <= x1 or y2 <= y1:
        return TapBlock(
            BridgeErrorCode.TARGET_ZERO_AREA,
            "bounds have zero area (" + format_bounds(element.bounds) + "), so the centre coordinate "
            "belongs to no laid-out view",
        )
    if not element.enabled:
        return TapBlock(
            BridgeErrorCode.TARGET_DISABLED,
            "the view reports enabled=false, so a tap would be swallowed",
        )
    if not element.clickable and not allow_non_clickable:
        return TapBlock(
            BridgeErrorCode.TARGET_NOT_CLICKABLE,
            "the view reports clickable=false; pass allow_non_clickable=True at the call site if "
            "the intent is to tap a non-interactive region on purpose",
        )
    return None


def require_resolvable(element: UiElement, allow_non_clickable: bool = False) -> tuple[int, int]:
    """The tap coordinate for `element`, or a raised refusal.

    Raises:
        HierarchyRejected: `assert_resolvable` returned a reason. The refusal happens before any
            command is constructed, so a rejected tap never reaches the phone.
    """
    block = assert_resolvable(element, allow_non_clickable=allow_non_clickable)
    if block is not None:
        raise HierarchyRejected(
            "refusing to tap " + element.describe() + ": " + block.reason,
            code=block.code,
            context={"code": block.code.value, "element": element.describe()},
        )
    return centre_of(element)


def _walk_into(element: UiElement, sink: list[UiElement]) -> None:
    sink.append(element)
    for child in element.children:
        _walk_into(child, sink)


def _malformed(message: str, **context: object) -> HierarchyRejected:
    return HierarchyRejected(
        "malformed uiautomator dump: " + message,
        code=BridgeErrorCode.HIERARCHY_MALFORMED,
        context=context,
    )


def iter_flatten(dump: HierarchyDump) -> Iterable[UiElement]:
    """Iteration form of `HierarchyDump.elements()` for callers that prefer a generator."""
    return iter(dump.elements())


__all__ = [
    "MAX_BOUND_COORDINATE",
    "Bounds",
    "UiElement",
    "HierarchyDump",
    "TapBlock",
    "parse_dump",
    "parse_bounds",
    "format_bounds",
    "centre_of",
    "assert_resolvable",
    "require_resolvable",
    "iter_flatten",
]
