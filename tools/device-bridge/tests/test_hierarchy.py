"""`bridge.hierarchy`: parsing a dump, resolving targets, and refusing to tap what cannot be justified.

The fixture in `tests/fixtures/uiautomator_shell.xml` is hand-authored from the harness app's own
resource ids, content descriptions and text, including the `probes: <n>` counter the Probe button
increments. Bounds are arithmetic on the harness's 24dp padding on a 1080-wide panel.
"""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

_HERE = Path(__file__).resolve().parent
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))

import support  # noqa: E402
from bridge import HARNESS_CONTENT_DESCRIPTIONS, HARNESS_COUNTER_PREFIX, HARNESS_PACKAGE  # noqa: E402
from bridge import HARNESS_RESOURCE_IDS  # noqa: E402
from bridge import harness_resource_id  # noqa: E402
from bridge.errors import BridgeErrorCode, HierarchyRejected  # noqa: E402
from bridge.hierarchy import (  # noqa: E402
    MAX_BOUND_COORDINATE,
    UiElement,
    assert_resolvable,
    centre_of,
    format_bounds,
    parse_bounds,
    parse_dump,
    require_resolvable,
)


def element(
    *,
    clazz: str = "android.widget.Button",
    resource_id: str | None = HARNESS_PACKAGE + ":id/bridge_probe_button",
    text: str | None = "Probe",
    content_desc: str | None = "bridge-probe-button",
    bounds: tuple[int, int, int, int] = (66, 182, 1014, 326),
    clickable: bool = True,
    enabled: bool = True,
    focused: bool = False,
    package: str = HARNESS_PACKAGE,
    index: int = 0,
    children: tuple[UiElement, ...] = (),
) -> UiElement:
    return UiElement(
        clazz=clazz,
        resource_id=resource_id,
        text=text,
        content_desc=content_desc,
        bounds=bounds,
        clickable=clickable,
        enabled=enabled,
        focused=focused,
        package=package,
        index=index,
        children=children,
    )


class HierarchyDumpParseTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.dump = parse_dump(support.uiautomator_dump_text())

    def test_rotation_is_read_from_the_hierarchy_element(self) -> None:
        self.assertEqual(self.dump.rotation, 0)

    def test_the_tree_has_one_root_frame_and_six_leaf_views(self) -> None:
        self.assertEqual(len(self.dump.roots), 1)
        classes = [node.clazz for node in self.dump.elements()]
        self.assertEqual(classes[0], "android.widget.FrameLayout")
        for expected in (
            "android.widget.TextView",
            "android.widget.Button",
            "android.widget.CheckBox",
            "android.widget.EditText",
        ):
            self.assertIn(expected, classes)

    def test_every_harness_resource_id_resolves_to_exactly_one_element(self) -> None:
        for short_name in HARNESS_RESOURCE_IDS:
            matches = self.dump.find_by_resource_id(harness_resource_id(short_name))
            self.assertEqual(len(matches), 1, short_name + " must resolve uniquely")
            self.assertEqual(matches[0].package, HARNESS_PACKAGE)

    def test_every_harness_content_description_resolves_to_exactly_one_element(self) -> None:
        for description in HARNESS_CONTENT_DESCRIPTIONS:
            matches = self.dump.find_by_content_desc(description)
            self.assertEqual(len(matches), 1, description + " must resolve uniquely")

    def test_the_counter_reads_the_live_probe_text(self) -> None:
        counter = self.dump.find_by_resource_id(harness_resource_id("bridge_counter_value"))[0]
        self.assertEqual(counter.text, HARNESS_COUNTER_PREFIX + "3")
        self.assertEqual(counter.content_desc, "bridge-counter")

    def test_an_empty_text_attribute_becomes_absence_not_an_empty_string(self) -> None:
        field = self.dump.find_by_resource_id(harness_resource_id("bridge_input_field"))[0]
        self.assertIsNone(field.text)
        self.assertEqual(len(self.dump.find_by_text("Input")), 0, "the hint is not the text")

    def test_boolean_attributes_are_read_as_booleans(self) -> None:
        button = self.dump.find_by_resource_id(harness_resource_id("bridge_probe_button"))[0]
        self.assertTrue(button.clickable)
        self.assertTrue(button.enabled)
        self.assertFalse(button.focused)
        field = self.dump.find_by_resource_id(harness_resource_id("bridge_input_field"))[0]
        self.assertTrue(field.focused)

    def test_bounds_are_four_ints_in_the_documented_order(self) -> None:
        title = self.dump.find_by_resource_id(harness_resource_id("bridge_title"))[0]
        self.assertEqual(title.bounds, (66, 66, 520, 130))
        self.assertTrue(all(isinstance(value, int) for value in title.bounds))

    def test_nesting_and_index_survive_parsing(self) -> None:
        column = [node for node in self.dump.elements() if node.clazz == "android.widget.LinearLayout"][0]
        self.assertEqual(len(column.children), 6)
        self.assertEqual([child.index for child in column.children], [0, 1, 2, 3, 4, 5])

    def test_attributes_the_bridge_does_not_act_on_are_dropped(self) -> None:
        field = self.dump.find_by_resource_id(harness_resource_id("bridge_input_field"))[0]
        for absent in ("hint", "checkable", "scrollable", "drawing_order", "password", "selected"):
            self.assertFalse(hasattr(field, absent), absent + " must not be retained")

    def test_document_order_is_stable_across_parses(self) -> None:
        again = parse_dump(support.uiautomator_dump_text())
        self.assertEqual(
            [node.resource_id for node in again.elements()],
            [node.resource_id for node in self.dump.elements()],
        )

    def test_a_foreign_element_is_not_reachable_by_a_harness_query(self) -> None:
        self.assertEqual(self.dump.find_by_resource_id("com.other.app:id/bridge_status"), ())

    def test_describe_reports_identity_but_never_the_text_on_screen(self) -> None:
        counter = self.dump.find_by_resource_id(harness_resource_id("bridge_counter_value"))[0]
        self.assertNotIn("probes", counter.describe())
        self.assertIn("bridge_counter_value", counter.describe())


class MalformedDumpTest(unittest.TestCase):
    def test_a_document_that_is_not_xml_is_rejected(self) -> None:
        with self.assertRaises(HierarchyRejected) as caught:
            parse_dump("<hierarchy rotation=\"0\"><node")
        self.assertEqual(caught.exception.code, BridgeErrorCode.HIERARCHY_MALFORMED)

    def test_an_empty_dump_is_rejected(self) -> None:
        with self.assertRaises(HierarchyRejected):
            parse_dump("")

    def test_an_unexpected_root_tag_is_rejected(self) -> None:
        with self.assertRaises(HierarchyRejected):
            parse_dump("<screen><node index=\"0\" /></screen>")

    def test_a_hierarchy_with_no_nodes_is_rejected(self) -> None:
        with self.assertRaises(HierarchyRejected):
            parse_dump('<hierarchy rotation="0"></hierarchy>')

    def test_a_missing_required_attribute_is_rejected(self) -> None:
        document = (
            '<hierarchy rotation="0"><node index="0" bounds="[0,0][10,10]" class="android.view.View" '
            'package="com.omnibuds.tools.shell" clickable="true" enabled="true" /></hierarchy>'
        )
        with self.assertRaises(HierarchyRejected) as caught:
            parse_dump(document)
        self.assertIn("focused", caught.exception.message)

    def test_a_non_boolean_attribute_is_rejected_rather_than_read_as_true(self) -> None:
        document = (
            '<hierarchy rotation="0"><node index="0" bounds="[0,0][10,10]" class="android.view.View" '
            'package="com.omnibuds.tools.shell" clickable="maybe" enabled="true" focused="false" /></hierarchy>'
        )
        with self.assertRaises(HierarchyRejected) as caught:
            parse_dump(document)
        self.assertIn("clickable", caught.exception.message)

    def test_a_non_integer_index_is_rejected(self) -> None:
        document = (
            '<hierarchy rotation="0"><node index="one" bounds="[0,0][10,10]" class="android.view.View" '
            'package="com.omnibuds.tools.shell" clickable="true" enabled="true" focused="false" /></hierarchy>'
        )
        with self.assertRaises(HierarchyRejected):
            parse_dump(document)

    def test_a_blank_class_or_package_is_rejected(self) -> None:
        blank_class = (
            '<hierarchy rotation="0"><node index="0" bounds="[0,0][10,10]" class="" '
            'package="com.omnibuds.tools.shell" clickable="true" enabled="true" focused="false" />'
            "</hierarchy>"
        )
        with self.assertRaises(HierarchyRejected) as caught:
            parse_dump(blank_class)
        self.assertIn("class", caught.exception.message)
        blank_package = (
            '<hierarchy rotation="0"><node index="0" bounds="[0,0][10,10]" class="android.view.View" '
            'package="" clickable="true" enabled="true" focused="false" /></hierarchy>'
        )
        with self.assertRaises(HierarchyRejected) as caught:
            parse_dump(blank_package)
        self.assertIn("package", caught.exception.message)

    def test_a_node_child_of_hierarchy_is_rejected(self) -> None:
        with self.assertRaises(HierarchyRejected):
            parse_dump('<hierarchy rotation="0"><view index="0" /></hierarchy>')

    def test_an_unparsable_rotation_is_rejected(self) -> None:
        with self.assertRaises(HierarchyRejected):
            parse_dump('<hierarchy rotation="sideways"><node index="0" bounds="[0,0][1,1]" '
                       'class="android.view.View" package="com.omnibuds.tools.shell" clickable="true" '
                       'enabled="true" focused="false" /></hierarchy>')

    def test_a_rotation_that_is_not_a_multiple_of_ninety_is_rejected(self) -> None:
        with self.assertRaises(HierarchyRejected):
            parse_dump('<hierarchy rotation="45"><node index="0" bounds="[0,0][1,1]" '
                       'class="android.view.View" package="com.omnibuds.tools.shell" clickable="true" '
                       'enabled="true" focused="false" /></hierarchy>')

    def test_a_single_node_root_is_accepted_as_a_fragment(self) -> None:
        dump = parse_dump('<node index="0" bounds="[0,0][10,20]" class="android.view.View" '
                          'package="com.omnibuds.tools.shell" clickable="true" enabled="true" '
                          'focused="false" />')
        self.assertEqual(len(dump.roots), 1)
        self.assertIsNone(dump.rotation)


class BoundsTest(unittest.TestCase):
    def test_bounds_parse_into_ints(self) -> None:
        self.assertEqual(parse_bounds("[66,182][1014,326]"), (66, 182, 1014, 326))

    def test_zero_area_bounds_parse_because_the_device_really_reports_them(self) -> None:
        self.assertEqual(parse_bounds("[10,10][10,10]"), (10, 10, 10, 10))

    def test_an_inverted_rectangle_is_rejected(self) -> None:
        with self.assertRaises(HierarchyRejected) as caught:
            parse_bounds("[100,100][10,10]")
        self.assertEqual(caught.exception.code, BridgeErrorCode.BOUNDS_OUT_OF_RANGE)

    def test_a_negative_origin_is_rejected(self) -> None:
        with self.assertRaises(HierarchyRejected):
            parse_bounds("[-10,0][10,10]")

    def test_a_coordinate_beyond_the_envelope_is_rejected(self) -> None:
        with self.assertRaises(HierarchyRejected):
            parse_bounds("[0,0][" + str(MAX_BOUND_COORDINATE + 1) + ",10]")

    def test_wrong_shapes_are_rejected(self) -> None:
        for bad in ("0,0,10,10", "[0 0][10 10]", "[0,0][10]", "", "bounds"):
            with self.assertRaises(HierarchyRejected):
                parse_bounds(bad)

    def test_format_bounds_round_trips(self) -> None:
        self.assertEqual(format_bounds((66, 182, 1014, 326)), "[66,182][1014,326]")


class CentreResolutionTest(unittest.TestCase):
    def test_the_centre_of_the_probe_button_is_its_own_arithmetic(self) -> None:
        target = element(bounds=(66, 182, 1014, 326))
        self.assertEqual(centre_of(target), (540, 254))
        self.assertEqual(target.centre, (540, 254))

    def test_an_odd_extent_floors_toward_the_origin(self) -> None:
        self.assertEqual(centre_of(element(bounds=(0, 0, 5, 5))), (2, 2))

    def test_area_is_derived_not_stored(self) -> None:
        self.assertEqual(element(bounds=(0, 0, 10, 20)).area, 200)


class ResolvabilityTest(unittest.TestCase):
    def test_a_clickable_enabled_view_with_area_is_resolvable(self) -> None:
        self.assertIsNone(assert_resolvable(element()))

    def test_zero_area_bounds_are_refused(self) -> None:
        block = assert_resolvable(element(bounds=(500, 500, 500, 500)))
        self.assertIsNotNone(block)
        assert block is not None
        self.assertEqual(block.code, BridgeErrorCode.TARGET_ZERO_AREA)

    def test_a_disabled_view_is_refused_even_when_clickable(self) -> None:
        block = assert_resolvable(element(enabled=False))
        assert block is not None
        self.assertEqual(block.code, BridgeErrorCode.TARGET_DISABLED)

    def test_a_non_clickable_view_is_refused_by_default(self) -> None:
        block = assert_resolvable(element(clickable=False))
        assert block is not None
        self.assertEqual(block.code, BridgeErrorCode.TARGET_NOT_CLICKABLE)

    def test_the_escape_hatch_is_honoured_when_passed_deliberately(self) -> None:
        self.assertIsNone(assert_resolvable(element(clickable=False), allow_non_clickable=True))

    def test_zero_area_wins_over_disabled_because_there_is_nothing_to_tap(self) -> None:
        block = assert_resolvable(element(bounds=(10, 10, 10, 10), enabled=False, clickable=False))
        assert block is not None
        self.assertEqual(block.code, BridgeErrorCode.TARGET_ZERO_AREA)

    def test_require_resolvable_raises_the_refusal_for_input(self) -> None:
        with self.assertRaises(HierarchyRejected) as caught:
            require_resolvable(element(clickable=False))
        self.assertEqual(caught.exception.code, BridgeErrorCode.TARGET_NOT_CLICKABLE)

    def test_require_resolvable_returns_the_centre_when_it_passes(self) -> None:
        self.assertEqual(require_resolvable(element()), (540, 254))

    def test_a_tap_block_serialises_for_an_envelope(self) -> None:
        block = assert_resolvable(element(enabled=False))
        assert block is not None
        payload = block.as_dict()
        self.assertEqual(payload["code"], "target_disabled")
        self.assertIn("reason", payload)


class MatchingTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.dump = parse_dump(support.uiautomator_dump_text())

    def test_all_matching_returns_document_order(self) -> None:
        texts = self.dump.all_matching(lambda node: node.text is not None)
        self.assertEqual([node.text for node in texts][0], "OmniBuds bridge shell")

    def test_predicate_is_the_general_form_of_the_named_lookups(self) -> None:
        by_predicate = self.dump.all_matching(
            lambda node: node.resource_id == harness_resource_id("bridge_toggle")
        )
        self.assertEqual(by_predicate, self.dump.find_by_resource_id(harness_resource_id("bridge_toggle")))

    def test_require_unique_passes_a_single_match(self) -> None:
        matches = self.dump.find_by_resource_id(harness_resource_id("bridge_title"))
        self.assertIs(self.dump.require_unique(matches, "resource-id bridge_title"), matches[0])

    def test_require_unique_refuses_no_match(self) -> None:
        with self.assertRaises(HierarchyRejected) as caught:
            self.dump.require_unique((), "text=Anything")
        self.assertEqual(caught.exception.code, BridgeErrorCode.TARGET_NOT_FOUND)

    def test_require_unique_refuses_several_matches(self) -> None:
        matches = self.dump.all_matching(lambda node: node.clazz == "android.widget.TextView")
        self.assertGreater(len(matches), 1)
        with self.assertRaises(HierarchyRejected) as caught:
            self.dump.require_unique(matches, "class=TextView")
        self.assertEqual(caught.exception.code, BridgeErrorCode.TARGET_AMBIGUOUS)

    def test_blank_needles_are_refused_rather_than_matching_everything(self) -> None:
        with self.assertRaises(ValueError):
            self.dump.find_by_text("")
        with self.assertRaises(ValueError):
            self.dump.find_by_content_desc("")
        with self.assertRaises(ValueError):
            self.dump.find_by_resource_id("")


class HarnessIdentityTest(unittest.TestCase):
    def test_resource_ids_are_qualified_with_the_harness_package(self) -> None:
        self.assertEqual(
            harness_resource_id("bridge_probe_button"),
            HARNESS_PACKAGE + ":id/bridge_probe_button",
        )

    def test_an_unknown_resource_name_is_refused_at_the_call_site(self) -> None:
        with self.assertRaises(ValueError):
            harness_resource_id("bridge_probe_botton")

    def test_the_fixture_and_the_source_declare_the_same_ids(self) -> None:
        dump = parse_dump(support.uiautomator_dump_text())
        present = {node.short_id for node in dump.elements() if node.resource_id}
        for short_name in HARNESS_RESOURCE_IDS:
            self.assertIn(short_name, present)


if __name__ == "__main__":
    unittest.main()
