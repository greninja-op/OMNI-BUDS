"""`bridge.adb`: parsing, argv construction and the distinct device states.

Every test here drives a real `AdbTransport` over `FakeAdbRunner`. No adb binary is invoked, and no
device is addressed: the transport is exercised through its injected subprocess seam.
"""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

_HERE = Path(__file__).resolve().parent
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))

import support  # noqa: E402  (the bootstrap above must run before package imports)
from bridge.adb import (  # noqa: E402
    AdbDeviceState,
    AdbTransport,
    DeviceProfile,
    build_install_argv,
    parse_device_listing,
    parse_focus_package,
    parse_getprop_dump,
    parse_prop_value,
    parse_sdk_value,
)
from bridge.errors import (  # noqa: E402
    AdbFailed,
    BridgeErrorCode,
    DeviceNotFound,
    DeviceOffline,
    DeviceUnauthorized,
    PermissionNotGranted,
)


class DeviceListingTest(unittest.TestCase):
    def test_listing_separates_device_from_unauthorized_and_offline(self) -> None:
        records = parse_device_listing(support.adb_devices_listing())
        by_serial = {record.serial: record for record in records}
        self.assertEqual(
            by_serial["BRIDGEFA0001"].state, AdbDeviceState.DEVICE
        )
        self.assertEqual(
            by_serial["BRIDGEFA0002"].state, AdbDeviceState.UNAUTHORIZED
        )
        self.assertEqual(
            by_serial["BRIDGEFA0003"].state, AdbDeviceState.OFFLINE
        )
        self.assertEqual(
            by_serial["BRIDGEFA0004"].state, AdbDeviceState.SIDELOAD
        )

    def test_listing_carries_product_model_and_transport_id(self) -> None:
        records = parse_device_listing(support.adb_devices_listing())
        authorized = records[0]
        self.assertEqual(authorized.serial, "BRIDGEFA0001")
        self.assertEqual(authorized.product, "vermeer")
        self.assertEqual(authorized.model, "2311DRK48I")
        self.assertEqual(authorized.device, "vermeer")
        self.assertEqual(authorized.transport_id, "7")

    def test_a_model_name_containing_spaces_does_not_swallow_the_next_field(self) -> None:
        records = parse_device_listing(support.adb_devices_listing())
        sideload = [record for record in records if record.serial == "BRIDGEFA0004"][0]
        self.assertEqual(sideload.model, "Synthetic Device Name")
        self.assertEqual(sideload.device, "foobar")
        self.assertEqual(sideload.transport_id, "10")

    def test_only_the_device_state_is_addressable(self) -> None:
        records = parse_device_listing(support.adb_devices_listing())
        addressable = {record.serial for record in records if record.is_addressable}
        self.assertEqual(addressable, {"BRIDGEFA0001", "emulator-5554"})

    def test_an_unrecognised_state_token_stays_unknown_with_its_raw_text(self) -> None:
        records = parse_device_listing("List of devices attached\nFAKESERIAL1   tidying product:x\n")
        self.assertEqual(records[0].state, AdbDeviceState.UNKNOWN)
        self.assertEqual(records[0].state_token, "tidying")

    def test_a_line_that_is_not_a_record_fails_rather_than_being_skipped(self) -> None:
        with self.assertRaises(AdbFailed) as caught:
            parse_device_listing("List of devices attached\njustoneserial\n")
        self.assertEqual(caught.exception.code, BridgeErrorCode.ADB_UNUSABLE_OUTPUT)

    def test_daemon_chatter_lines_are_tolerated(self) -> None:
        records = parse_device_listing("* daemon started successfully *\nFAKESERIAL2   device\n")
        self.assertEqual(len(records), 1)


class FailureClassificationTest(unittest.TestCase):
    def _transport_with_stderr(self, stderr: str) -> tuple[AdbTransport, support.FakeAdbRunner]:
        transport, runner = support.make_transport()
        runner.on("shell", "getprop", "ro.product.cpu.abi", returncode=1, stderr=stderr.encode("utf-8"))
        return transport, runner

    def test_device_not_found_is_its_own_category(self) -> None:
        transport, _ = self._transport_with_stderr("adb: device 'NOPE' not found\n")
        with self.assertRaises(DeviceNotFound):
            transport.preflight()

    def test_unauthorized_is_its_own_category_and_names_the_phone_action(self) -> None:
        transport, _ = self._transport_with_stderr("adb: device unauthorized.\n")
        with self.assertRaises(DeviceUnauthorized) as caught:
            transport.preflight()
        self.assertIn("approve", caught.exception.message.lower())

    def test_offline_is_its_own_category(self) -> None:
        transport, _ = self._transport_with_stderr("error: device offline\n")
        with self.assertRaises(DeviceOffline):
            transport.preflight()

    def test_an_unexplained_nonzero_status_is_adbfailed_not_a_invented_category(self) -> None:
        transport, _ = self._transport_with_stderr("some other adb complaint\n")
        with self.assertRaises(AdbFailed) as caught:
            transport.preflight()
        self.assertEqual(caught.exception.code, BridgeErrorCode.ADB_COMMAND_FAILED)
        self.assertEqual(caught.exception.returncode, 1)

    def test_stderr_is_bounded_in_the_diagnostic(self) -> None:
        transport, _ = self._transport_with_stderr("x" * 4000)
        with self.assertRaises(AdbFailed) as caught:
            transport.preflight()
        self.assertLessEqual(len(caught.exception.context["stderr_excerpt"]), 200)

    def test_require_addressable_reports_the_state_it_saw(self) -> None:
        transport, runner = support.make_transport(serial="BRIDGEFA0002")
        runner.on("devices", "-l", stdout=support.adb_devices_listing().encode("utf-8"))
        with self.assertRaises(DeviceUnauthorized):
            transport.require_addressable()

    def test_require_addressable_reports_a_missing_serial_as_not_found(self) -> None:
        transport, runner = support.make_transport(serial="NOTATTACHED")
        runner.on("devices", "-l", stdout=support.adb_devices_listing().encode("utf-8"))
        with self.assertRaises(DeviceNotFound):
            transport.require_addressable()


class PreflightTest(unittest.TestCase):
    def _scripted(self) -> tuple[AdbTransport, support.FakeAdbRunner]:
        transport, runner = support.make_transport()
        for key, value in (
            ("ro.product.cpu.abi", "arm64-v8a"),
            ("ro.build.version.sdk", "34"),
            ("ro.build.version.release", "14"),
            ("ro.product.model", "2311DRK48I"),
            ("ro.product.manufacturer", "Xiaomi"),
        ):
            runner.on_shell_value(key, value)
        return transport, runner

    def test_preflight_reads_five_targeted_properties_and_nothing_else(self) -> None:
        transport, runner = self._scripted()
        profile = transport.preflight()
        self.assertIsInstance(profile, DeviceProfile)
        self.assertEqual(len(runner.calls), 5)
        for call in runner.calls:
            self.assertEqual(call[:4], ["adb", "-s", "BRIDGEFA0001", "shell"])
            self.assertEqual(call[4], "getprop")
            self.assertEqual(len(call), 6)  # exactly one property name per command, never a bare dump
        # No bulk dump: a bare `getprop` with no key would be the sixth-command shape.
        self.assertNotIn(
            ["adb", "-s", "BRIDGEFA0001", "shell", "getprop"],
            [call for call in runner.calls],
        )

    def test_preflight_reports_the_build_facts(self) -> None:
        transport, _ = self._scripted()
        profile = transport.preflight()
        self.assertEqual(profile.abi, "arm64-v8a")
        self.assertEqual(profile.sdk, 34)
        self.assertEqual(profile.release, "14")
        self.assertEqual(profile.model, "2311DRK48I")
        self.assertEqual(profile.manufacturer, "Xiaomi")
        self.assertEqual(profile.serial, "BRIDGEFA0001")
        self.assertTrue(profile.meets_min_sdk)

    def test_a_sdk_below_the_project_floor_is_said_not_hidden(self) -> None:
        transport, runner = support.make_transport()
        for key, value in (
            ("ro.product.cpu.abi", "armeabi-v7a"),
            ("ro.build.version.sdk", "23"),
            ("ro.build.version.release", "6.0"),
            ("ro.product.model", "OLDCONTAINER"),
            ("ro.product.manufacturer", "Synthetic"),
        ):
            runner.on_shell_value(key, value)
        self.assertFalse(transport.preflight().meets_min_sdk)

    def test_a_non_numeric_sdk_is_a_failure_not_zero(self) -> None:
        transport, runner = support.make_transport()
        for key, value in (
            ("ro.product.cpu.abi", "arm64-v8a"),
            ("ro.build.version.sdk", "unknown"),
            ("ro.build.version.release", "14"),
            ("ro.product.model", "X"),
            ("ro.product.manufacturer", "Y"),
        ):
            runner.on_shell_value(key, value)
        with self.assertRaises(AdbFailed) as caught:
            transport.preflight()
        self.assertEqual(caught.exception.code, BridgeErrorCode.ADB_UNUSABLE_OUTPUT)

    def test_a_blank_property_is_a_failure_not_an_empty_string(self) -> None:
        transport, runner = support.make_transport()
        runner.on("shell", "getprop", "ro.product.cpu.abi", stdout=b"\n")
        with self.assertRaises(AdbFailed):
            transport.preflight()

    def test_parse_prop_value_accepts_both_output_shapes(self) -> None:
        self.assertEqual(parse_prop_value("arm64-v8a\n"), "arm64-v8a")
        self.assertEqual(parse_prop_value("[ro.product.cpu.abi]: [arm64-v8a]\n"), "arm64-v8a")

    def test_parse_prop_value_refuses_empty_and_multiline(self) -> None:
        with self.assertRaises(AdbFailed):
            parse_prop_value("   \n")
        with self.assertRaises(AdbFailed):
            parse_prop_value("arm64-v8a\nsecond line\n")

    def test_parse_sdk_value(self) -> None:
        self.assertEqual(parse_sdk_value("34"), 34)
        with self.assertRaises(AdbFailed):
            parse_sdk_value("0")
        with self.assertRaises(AdbFailed):
            parse_sdk_value("-1")

    def test_parse_getprop_dump_reads_the_fixture(self) -> None:
        props = parse_getprop_dump(support.getprop_dump())
        self.assertEqual(props["ro.build.version.sdk"], "34")
        self.assertEqual(props["ro.product.cpu.abi"], "arm64-v8a")
        self.assertEqual(props["ro.product.manufacturer"], "Xiaomi")

    def test_parse_getprop_dump_refuses_a_line_it_cannot_read(self) -> None:
        with self.assertRaises(AdbFailed):
            parse_getprop_dump("[ro.good]: [fine]\nnot a property line\n")


class FocusReadTest(unittest.TestCase):
    def test_focus_read_keeps_only_the_package(self) -> None:
        package = parse_focus_package(support.window_focus_dump())
        self.assertEqual(package, "com.omnibuds.tools.shell")
        self.assertNotIn("Window", package)
        self.assertNotIn("systemui", package)

    def test_a_foreign_foreground_package_is_reported_so_the_gate_can_refuse(self) -> None:
        text = "  mCurrentFocus=Window{1a2b3c u0 com.example.foreigndemo/.MainActivity}\n"
        self.assertEqual(parse_focus_package(text), "com.example.foreigndemo")

    def test_mcurrentfocus_is_preferred_over_mfocusedapp(self) -> None:
        text = (
            "  mFocusedApp=AppToken{9 Token{2 ActivityRecord{1 u0 com.other.app/.A t5 f}}}\n"
            "  mCurrentFocus=Window{6d3b1c8 u0 com.omnibuds.tools.shell/com.omnibuds.tools.shell.ShellActivity}\n"
        )
        self.assertEqual(parse_focus_package(text), "com.omnibuds.tools.shell")

    def test_mfocusedapp_is_the_documented_fallback(self) -> None:
        text = (
            "  mFocusedApp=AppToken{91aa2b3 Token{4f7c1d8 "
            "ActivityRecord{1a2b3c4 u0 com.omnibuds.tools.shell/.ShellActivity t21 f}}}\n"
        )
        self.assertEqual(parse_focus_package(text), "com.omnibuds.tools.shell")

    def test_a_null_focus_is_an_unresolved_state_not_a_pass(self) -> None:
        with self.assertRaises(AdbFailed) as caught:
            parse_focus_package("  mCurrentFocus=null\n  mFocusedApp=null\n")
        self.assertEqual(caught.exception.code, BridgeErrorCode.FOCUS_UNRESOLVED)

    def test_no_focus_lines_at_all_is_an_unresolved_state(self) -> None:
        with self.assertRaises(AdbFailed):
            parse_focus_package("WINDOW MANAGER WINDOWS dumpsys (dumpsys window)\n  nothing here\n")

    def test_transport_focus_read_sends_one_dumpsys_and_returns_one_word(self) -> None:
        transport, runner = support.make_transport()
        runner.on("shell", "dumpsys", "window", stdout=support.window_focus_dump().encode("utf-8"))
        package = transport.current_focus_package()
        self.assertEqual(package, "com.omnibuds.tools.shell")
        self.assertEqual(runner.calls, [["adb", "-s", "BRIDGEFA0001", "shell", "dumpsys", "window"]])


class InstallAndGrantTest(unittest.TestCase):
    def test_install_uses_r_and_never_g(self) -> None:
        transport, runner = support.make_transport()
        runner.on("install", "-r", "/tmp/harness-debug.apk", stdout=b"Performing Streamed Install\nSuccess\n")
        outcome = transport.install("/tmp/harness-debug.apk")
        self.assertEqual(
            outcome.argv,
            ("adb", "-s", "BRIDGEFA0001", "install", "-r", "/tmp/harness-debug.apk"),
        )
        self.assertIn("Success", outcome.stdout)
        for call in runner.calls:
            self.assertNotIn("-g", call)
            self.assertNotIn("--grant-runtime-permissions", call)

    def test_install_without_reinstall_omits_r_but_still_omits_g(self) -> None:
        argv = build_install_argv("adb", "SERIAL", "/tmp/a.apk", reinstall=False)
        self.assertEqual(argv, ["adb", "-s", "SERIAL", "install", "/tmp/a.apk"])
        self.assertNotIn("-g", argv)

    def test_install_refuses_a_path_that_is_not_an_apk(self) -> None:
        transport, _ = support.make_transport()
        with self.assertRaises(ValueError):
            transport.install("/tmp/somefile.txt")
        with self.assertRaises(ValueError):
            transport.install("   ")

    def test_grant_is_a_separate_explicit_call(self) -> None:
        transport, runner = support.make_transport()
        runner.on(
            "shell",
            "pm",
            "grant",
            "com.omnibuds.tools.shell",
            "android.permission.POST_NOTIFICATIONS",
            stdout=b"",
        )
        outcome = transport.grant("com.omnibuds.tools.shell", "android.permission.POST_NOTIFICATIONS")
        self.assertEqual(
            outcome.argv,
            (
                "adb",
                "-s",
                "BRIDGEFA0001",
                "shell",
                "pm",
                "grant",
                "com.omnibuds.tools.shell",
                "android.permission.POST_NOTIFICATIONS",
            ),
        )
        # Exactly one permission per call: no space-separated list ever reaches `pm grant`.
        self.assertNotIn(" ", outcome.argv[-1])

    def test_a_refused_grant_becomes_permission_not_granted(self) -> None:
        transport, runner = support.make_transport()
        runner.on(
            "shell",
            "pm",
            "grant",
            "com.omnibuds.tools.shell",
            "android.permission.CAMERA",
            returncode=1,
            stderr=b"Security exception: Permission android.permission.CAMERA "
                   b"has not been declared by com.omnibuds.tools.shell\n",
        )
        with self.assertRaises(PermissionNotGranted) as caught:
            transport.grant("com.omnibuds.tools.shell", "android.permission.CAMERA")
        self.assertEqual(caught.exception.code, BridgeErrorCode.PERMISSION_NOT_GRANTED)

    def test_grant_refuses_a_wildcard_or_blank_permission(self) -> None:
        transport, _ = support.make_transport()
        with self.assertRaises(ValueError):
            transport.grant("com.omnibuds.tools.shell", "android.permission.*")
        with self.assertRaises(ValueError):
            transport.grant("com.omnibuds.tools.shell", "")
        with self.assertRaises(ValueError):
            transport.grant("not a package", "android.permission.X")

    def test_start_activity_addresses_the_harness_component(self) -> None:
        transport, runner = support.make_transport()
        runner.on(
            "shell",
            "am",
            "start",
            "-n",
            "com.omnibuds.tools.shell/.ShellActivity",
            stdout=b"Starting: Intent { cmp=com.omnibuds.tools.shell/.ShellActivity }\n",
        )
        outcome = transport.start_activity("com.omnibuds.tools.shell", ".ShellActivity")
        self.assertEqual(
            outcome.argv,
            ("adb", "-s", "BRIDGEFA0001", "shell", "am", "start", "-n", "com.omnibuds.tools.shell/.ShellActivity"),
        )

    def test_force_stop_cannot_be_aimed_at_an_unnameable_package(self) -> None:
        transport, runner = support.make_transport()
        runner.on("shell", "am", "force-stop", "com.omnibuds.tools.shell", stdout=b"")
        transport.force_stop("com.omnibuds.tools.shell")
        with self.assertRaises(ValueError):
            transport.force_stop("-l")


class ScreenshotAndDumpTest(unittest.TestCase):
    def test_screencap_uses_exec_out_and_returns_bytes(self) -> None:
        png = support.screencap_png()
        transport, runner = support.make_transport()
        runner.on("exec-out", "screencap", "-p", stdout=png)
        self.assertEqual(transport.screencap_png(), png)
        self.assertEqual(runner.calls, [["adb", "-s", "BRIDGEFA0001", "exec-out", "screencap", "-p"]])

    def test_a_text_screencap_response_is_refused_not_returned(self) -> None:
        transport, runner = support.make_transport()
        runner.on("exec-out", "screencap", "-p", stdout=b"error: permission denied")
        with self.assertRaises(AdbFailed) as caught:
            transport.screencap_png()
        self.assertEqual(caught.exception.code, BridgeErrorCode.ADB_UNUSABLE_OUTPUT)

    def test_an_empty_screencap_response_is_refused(self) -> None:
        transport, runner = support.make_transport()
        runner.on("exec-out", "screencap", "-p", stdout=b"")
        with self.assertRaises(AdbFailed):
            transport.screencap_png()

    def test_uiautomator_dump_extracts_the_document_from_the_noise(self) -> None:
        payload = b"UI hierarchy dumped to: /dev/tty\n<?xml version='1.0'?><hierarchy rotation=\"0\"></hierarchy>\n"
        transport, runner = support.make_transport()
        runner.on("exec-out", "uiautomator", "dump", "/dev/tty", stdout=payload)
        document = transport.uiautomator_dump()
        self.assertTrue(document.startswith("<?xml"))
        self.assertTrue(document.endswith("</hierarchy>"))

    def test_uiautomator_dump_without_a_document_is_refused(self) -> None:
        transport, runner = support.make_transport()
        runner.on("exec-out", "uiautomator", "dump", "/dev/tty", stdout=b"ERROR: could not get idle state.")
        with self.assertRaises(AdbFailed):
            transport.uiautomator_dump()

    def test_pidof_reports_a_running_process(self) -> None:
        transport, runner = support.make_transport()
        runner.on("shell", "pidof", "com.omnibuds.tools.shell", stdout=b"12345\n")
        self.assertEqual(transport.pidof("com.omnibuds.tools.shell"), 12345)

    def test_pidof_of_nothing_is_none_and_says_so(self) -> None:
        transport, runner = support.make_transport()
        runner.on("shell", "pidof", "com.omnibuds.tools.shell", stdout=b"\n")
        self.assertIsNone(transport.pidof("com.omnibuds.tools.shell"))

    def test_pidof_refuses_to_pick_between_processes(self) -> None:
        transport, runner = support.make_transport()
        runner.on("shell", "pidof", "com.omnibuds.tools.shell", stdout=b"12345 67890\n")
        with self.assertRaises(AdbFailed):
            transport.pidof("com.omnibuds.tools.shell")


class TransportDisciplineTest(unittest.TestCase):
    def test_serial_is_injected_into_every_device_command(self) -> None:
        transport, runner = support.make_transport(serial="OTHERSERIAL")
        runner.on("shell", "pidof", "com.omnibuds.tools.shell", stdout=b"9\n")
        transport.pidof("com.omnibuds.tools.shell")
        self.assertEqual(runner.calls[-1][0:3], ["adb", "-s", "OTHERSERIAL"])

    def test_a_blank_serial_is_refused_at_construction(self) -> None:
        with self.assertRaises(ValueError):
            AdbTransport(serial="   ")

    def test_targeting_makes_a_sibling_transport_sharing_the_runner(self) -> None:
        transport, runner = support.make_transport()
        sibling = transport.targeting("SECOND")
        self.assertEqual(sibling.serial, "SECOND")
        self.assertEqual(sibling.adb_path, transport.adb_path)
        self.assertEqual(sibling.timeout_s, transport.timeout_s)
        runner.on("shell", "pidof", "com.omnibuds.tools.shell", stdout=b"4\n")
        self.assertEqual(sibling.pidof("com.omnibuds.tools.shell"), 4)
        self.assertEqual(runner.calls[-1][2], "SECOND")

    def test_a_closed_transport_refuses_further_commands(self) -> None:
        transport, runner = support.make_transport()
        runner.on("shell", "pidof", "com.omnibuds.tools.shell", stdout=b"4\n")
        transport.close()
        with self.assertRaises(AdbFailed) as caught:
            transport.pidof("com.omnibuds.tools.shell")
        self.assertEqual(caught.exception.code, BridgeErrorCode.TRANSPORT_CLOSED)

    def test_the_context_manager_closes_the_transport_it_opened(self) -> None:
        with AdbTransport(serial="BRIDGEFA0001", adb_path="adb", runner=support.FakeAdbRunner()) as opened:
            self.assertFalse(opened.is_closed)
        self.assertTrue(opened.is_closed)
        other, _ = support.make_transport()
        self.assertFalse(other.is_closed)

    def test_commands_run_is_an_audit_trail(self) -> None:
        transport, runner = support.make_transport()
        runner.on("shell", "pidof", "com.omnibuds.tools.shell", stdout=b"4\n")
        transport.pidof("com.omnibuds.tools.shell")
        self.assertEqual(
            transport.commands_run,
            (("adb", "-s", "BRIDGEFA0001", "shell", "pidof", "com.omnibuds.tools.shell"),),
        )


if __name__ == "__main__":
    unittest.main()
