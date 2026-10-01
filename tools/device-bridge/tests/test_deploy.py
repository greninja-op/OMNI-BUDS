"""Host-side tests for the deployment pipeline.

Everything runs against a scripted fake transport: no device is attached, and nothing here claims
otherwise. A fake that answers like a HyperOS phone proves the harness handles that answer, not
that the phone behaves this way - the physical evidence is recorded separately in
``docs/development/adb-deployment/validation.md``.

The fake is strict in both directions: a command with no scripted answer raises, and a scripted
answer that never gets consumed is reported by the assertions that check the command log. A silent
extra call - an uninstall, a permission grant, a blind retry - therefore cannot pass unnoticed.

Commands and output are deliberately built the way real ADB emits them, so the shared parsers in
``bridge.adb`` are exercised rather than a parallel set of test-only fixtures.
"""

from __future__ import annotations

import contextlib
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from bridge.adb import CommandResult  # noqa: E402
from bridge.deploy import (  # noqa: E402
    DESTRUCTIVE_RECOVERY_REQUIRED,
    ApkMetadata,
    DeploymentManager,
    Failure,
    Stage,
    classify_failure,
    parse_aapt_badging,
)

PACKAGE = "com.omnibuds.tools.shell"
SERIAL = "8TCABAIFWOZTDICI"

DEVICES_ONE = (
    "List of devices attached\n"
    f"{SERIAL}         device product:duchamp model:2311DRK48I device:duchamp transport_id:2\n"
    "\n"
)
DEVICES_NONE = "List of devices attached\n\n"
DEVICES_AMBIGUOUS = (
    "List of devices attached\n"
    "AAA device product:alpha model:Alpha transport_id:1\n"
    "BBB device product:beta model:Beta transport_id:2\n\n"
)
DEVICES_UNAUTHORIZED = (
    "List of devices attached\nAAA unauthorized\nBBB device product:beta model:Beta transport_id:2\n\n"
)
FOCUSOURS = (
    "  WINDOW MANAGER WINDOWS\n"
    f"  mCurrentFocus=Window{{19d109f u0 {PACKAGE}/{PACKAGE}.ShellActivity}}\n"
)
FOCUS_OTHER = "  mCurrentFocus=Window{c28e719 u0 com.google.android.youtube/com.google.android.apps.yousee.Settings}\n"


def result(stdout: str = "", stderr: str = "", returncode: int = 0) -> CommandResult:
    return CommandResult(returncode=returncode, stdout=stdout.encode(), stderr=stderr.encode())


class FakeTransport:
    """Scripted answers keyed by substring, matched in declaration order."""

    def __init__(self, scripted: list[tuple[str, CommandResult]]):
        self.scripted = list(scripted)
        self.calls: list[list[str]] = []

    def __call__(self, argv: list[str], *, timeout_s: float) -> CommandResult:
        joined = " ".join(argv)
        self.calls.append(list(argv))
        for index, (needle, answer) in enumerate(self.scripted):
            if needle in joined:
                del self.scripted[index]
                return answer
        raise AssertionError(f"unscripted command issued: {joined}")

    @property
    def joined(self) -> str:
        return " | ".join(" ".join(call) for call in self.calls)

    def remaining(self) -> list[str]:
        return [needle for needle, _ in self.scripted]


def happy_path(installed_before: bool = False, focus: str = FOCUSOURS) -> list[tuple[str, CommandResult]]:
    """Every command a clean run issues, in the order the pipeline reaches them."""

    listing = f"package:{PACKAGE}\n" if installed_before else "package:com.example.other\n"
    return [
        ("version", result(stdout="Android Debug Bridge version 1.0.41\nVersion 37.0.1-15733141\n")),
        ("gradle-fake", result(stdout="BUILD SUCCESSFUL in 1s\n")),
        ("devices -l", result(stdout=DEVICES_ONE)),
        ("ro.build.version.sdk", result(stdout="34\n")),
        ("pm list packages", result(stdout=listing)),
        ("install", result(stdout="Performing Streamed Install\nSuccess\n")),
        ("pm list packages", result(stdout=f"package:{PACKAGE}\n")),
        ("resolve-activity", result(stdout=f"priority=0 preferredOrder=0 match=0x108000\n{PACKAGE}/.ShellActivity\n")),
        ("am start", result(stdout=f"Starting: Intent {{ cmp={PACKAGE}/{PACKAGE}.ShellActivity }}\n")),
        ("pidof", result(stdout="8703\n")),
        ("dumpsys window", result(stdout=focus)),
    ]


class Harness(unittest.TestCase):
    def manager(self, scripted, metadata: ApkMetadata | None = None) -> DeploymentManager:
        manager = DeploymentManager(adb_path="/fake/adb", repo_root=".", runner=FakeTransport(scripted))
        manager.read_apk_metadata = (
            (lambda _apk: metadata or ApkMetadata(PACKAGE, 26, 1)) if metadata is not None else
            (lambda _apk: ApkMetadata(PACKAGE, 26, 1))
        )
        return manager

    @contextlib.contextmanager
    def apk(self, content: bytes = b"zip-ish"):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "app-debug.apk"
            path.write_bytes(content)
            yield path

    def deploy(self, manager: DeploymentManager, apk: Path):
        return manager.deploy(
            gradle_task="assembleDebug",
            apk_path=apk,
            expected_package=PACKAGE,
            serial=SERIAL,
            build_command=["gradle-fake"],
        )


class FlagSelectionTests(Harness):
    def test_absent_package_is_installed_without_the_reinstall_flag(self):
        # The audited defect: -r for an absent package is what the device refused.
        with self.apk() as apk:
            manager = self.manager(happy_path(installed_before=False))
            report = self.deploy(manager, apk)

            self.assertTrue(report.succeeded, report.text())
            install = next(c for c in manager._runner.calls if "install" in c)
            self.assertNotIn("-r", install)
            self.assertNotIn("-g", install)
            self.assertEqual(report.install_flags, ())

    def test_present_package_is_reinstalled_so_its_data_survives(self):
        with self.apk() as apk:
            manager = self.manager(happy_path(installed_before=True))
            report = self.deploy(manager, apk)

            self.assertTrue(report.succeeded, report.text())
            install = next(c for c in manager._runner.calls if "install" in c)
            self.assertIn("-r", install)
            self.assertNotIn("-g", install)
            self.assertEqual(report.install_flags, ("-r",))

    def test_no_scripted_answer_is_consumed_twice_or_skipped(self):
        with self.apk() as apk:
            scripted = happy_path()
            scripted.pop()  # drop the dumpsys window answer: the pipeline must not invent focus
            manager = self.manager(scripted)

            with self.assertRaises(AssertionError):
                self.deploy(manager, apk)

    def test_serial_is_always_addressed_explicitly(self):
        with self.apk() as apk:
            manager = self.manager(happy_path())
            self.deploy(manager, apk)

            device_calls = [c for c in manager._runner.calls if "-s" in " ".join(c)]
            self.assertTrue(device_calls, "device commands must carry -s")
            for call in device_calls:
                self.assertIn(SERIAL, call)


class InstallFailureTests(Harness):
    def refusing(self, message: str):
        scripted = happy_path()
        scripted[5] = ("install", result(returncode=1, stderr=message))
        return scripted

    def test_user_restricted_refusal_is_classified_and_leaves_the_app_installed(self):
        with self.apk() as apk:
            manager = self.manager(
                self.refusing("adb.exe: failed to install: Failure [INSTALL_FAILED_USER_RESTRICTED: Install canceled by user]")
            )
            report = self.deploy(manager, apk)

            self.assertIs(report.failure, Failure.INSTALL_USER_RESTRICTED)
            self.assertFalse(report.succeeded)
            self.assertNotIn("uninstall", manager._runner.joined)
            self.assertNotIn("pm clear", manager._runner.joined)
            self.assertTrue(any("no destructive recovery attempted" in note for note in report.notes))

    def test_signature_mismatch_is_returned_for_a_human_decision(self):
        with self.apk() as apk:
            manager = self.manager(
                self.refusing("Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package signatures do not match]")
            )
            report = self.deploy(manager, apk)

            self.assertIs(report.failure, Failure.INSTALL_SIGNATURE_MISMATCH)
            self.assertIn(report.failure, DESTRUCTIVE_RECOVERY_REQUIRED)
            self.assertTrue(any("human decision" in note for note in report.notes))
            self.assertNotIn("uninstall", manager._runner.joined)

    def test_transport_failure_is_not_reported_as_a_policy_refusal(self):
        with self.apk() as apk:
            manager = self.manager(
                self.refusing("cmd: Failure calling service package: Broken pipe (32)")
            )
            report = self.deploy(manager, apk)

            self.assertIs(report.failure, Failure.INSTALL_TRANSPORT_FAILED)

    def test_a_reported_success_that_leaves_nothing_installed_is_not_a_success(self):
        with self.apk() as apk:
            scripted = happy_path()
            scripted[6] = ("pm list packages", result(stdout="package:com.example.other\n"))
            manager = self.manager(scripted)
            report = self.deploy(manager, apk)

            self.assertIs(report.failure, Failure.PACKAGE_MISMATCH)
            self.assertFalse(report.succeeded)


class DeviceSelectionTests(Harness):
    def test_no_devices_stops_before_any_build_side_effect_on_the_phone(self):
        scripted = [
            ("version", result(stdout="Android Debug Bridge version 1.0.41")),
            ("gradle-fake", result(stdout="BUILD SUCCESSFUL\n")),
            ("devices -l", result(stdout=DEVICES_NONE)),
        ]
        with self.apk() as apk:
            manager = self.manager(scripted)
            report = self.deploy(manager, apk)

            self.assertIs(report.failure, Failure.NO_DEVICES)
            self.assertNotIn("install", manager._runner.joined)

    def test_two_addressable_devices_demand_an_explicit_serial(self):
        found = FakeTransport([("devices -l", result(stdout=DEVICES_AMBIGUOUS))])
        manager = DeploymentManager(adb_path="/fake/adb", repo_root=".", runner=found)

        device, failure, detail = manager.resolve_device(None)

        self.assertIsNone(device)
        self.assertIs(failure, Failure.AMBIGUOUS_DEVICE)
        self.assertIn("AAA", detail or "")

    def test_an_unauthorised_serial_is_never_substituted_with_another_device(self):
        found = FakeTransport([("devices -l", result(stdout=DEVICES_UNAUTHORIZED))])
        manager = DeploymentManager(adb_path="/fake/adb", repo_root=".", runner=found)

        device, failure, _ = manager.resolve_device("AAA")

        self.assertIsNone(device)
        self.assertIs(failure, Failure.UNAUTHORIZED)

    def test_a_ready_serial_is_chosen_although_an_unauthorised_device_is_listed(self):
        found = FakeTransport([("devices -l", result(stdout=DEVICES_UNAUTHORIZED))])
        manager = DeploymentManager(adb_path="/fake/adb", repo_root=".", runner=found)

        device, failure, _ = manager.resolve_device("BBB")

        self.assertIsNone(failure)
        self.assertEqual(device.serial, "BBB")

    def test_non_adb_executable_is_rejected_rather_than_swapped(self):
        manager = DeploymentManager(
            adb_path="/fake/not-adb",
            repo_root=".",
            runner=FakeTransport([("version", result(returncode=1, stderr="not a program"))]),
        )

        self.assertIs(manager.validate_executable(), Failure.ADB_INVALID)


class ApkValidationTests(Harness):
    def test_missing_file_is_a_failure_not_a_skip(self):
        with tempfile.TemporaryDirectory() as directory:
            manager = self.manager(happy_path())
            report = self.deploy(manager, Path(directory) / "absent.apk")

            self.assertIs(report.failure, Failure.APK_MISSING)

    def test_empty_file_is_rejected(self):
        with self.apk(content=b"") as apk:
            manager = self.manager(happy_path())
            report = self.deploy(manager, apk)

            self.assertIs(report.failure, Failure.APK_EMPTY)

    def test_package_id_mismatch_blocks_the_install(self):
        with self.apk() as apk:
            manager = DeploymentManager(adb_path="/fake/adb", repo_root=".", runner=FakeTransport(happy_path()))
            manager.read_apk_metadata = lambda _apk: ApkMetadata("com.evil.lookalike", 26, 1)
            report = self.deploy(manager, apk)

            self.assertIs(report.failure, Failure.PACKAGE_MISMATCH)
            self.assertNotIn("install", manager._runner.joined)

    def test_apk_above_the_device_api_level_is_refused(self):
        scripted = happy_path()
        scripted[3] = ("ro.build.version.sdk", result(stdout="24\n"))
        with self.apk() as apk:
            manager = self.manager(scripted, metadata=ApkMetadata(PACKAGE, 26, 1))
            report = self.deploy(manager, apk)

            self.assertIs(report.failure, Failure.SDK_INCOMPATIBLE)

    def test_unreadable_metadata_is_invalid_rather_than_assumed_compatible(self):
        with self.apk() as apk:
            manager = DeploymentManager(adb_path="/fake/adb", repo_root=".", runner=FakeTransport(happy_path()))
            manager.read_apk_metadata = lambda _apk: ApkMetadata()
            report = self.deploy(manager, apk)

            self.assertIs(report.failure, Failure.APK_INVALID)

    def test_aapt_badging_output_is_parsed_without_inventing_numbers(self):
        text = (
            "package: name='com.example.app' versionCode='7' versionName='1.2' platformBuildVersionName=''\n"
            "sdkVersion:'21'\n"
            "targetSdkVersion:'34'\n"
        )
        parsed = parse_aapt_badging(text)

        self.assertEqual(parsed.package, "com.example.app")
        self.assertEqual(parsed.min_sdk, 21)
        self.assertEqual(parsed.version_code, 7)

        empty = parse_aapt_badging("some unrelated tool output")
        self.assertIsNone(empty.package)
        self.assertIsNone(empty.min_sdk)
        self.assertIsNone(empty.version_code)


class LaunchAndForegroundTests(Harness):
    def test_a_clean_run_reports_every_stage_verified(self):
        with self.apk() as apk:
            manager = self.manager(happy_path())
            report = self.deploy(manager, apk)

            self.assertEqual([stage for stage, _, _ in report.stages], list(Stage))
            self.assertTrue(all(ok for _, ok, _ in report.stages))
            self.assertEqual(report.pid, 8703)
            self.assertEqual(report.activity, f"{PACKAGE}/.ShellActivity")
            self.assertIn("FOREGROUND_VERIFIED_SUCCESS", report.text())

    def test_activity_is_resolved_rather_than_assumed(self):
        scripted = happy_path()
        scripted[7] = ("resolve-activity", result(stdout="priority=0 match=0x108000\nNo activity found\n"))
        with self.apk() as apk:
            manager = self.manager(scripted)
            report = self.deploy(manager, apk)

            self.assertIs(report.failure, Failure.NO_LAUNCH_ACTIVITY)
            self.assertNotIn("am start", manager._runner.joined)

    def test_am_start_error_text_is_not_counted_as_a_launch(self):
        scripted = happy_path()
        scripted[8] = ("am start", result(stdout=f"Error: Activity class {{{PACKAGE}/x}} does not exist."))
        with self.apk() as apk:
            manager = self.manager(scripted)
            report = self.deploy(manager, apk)

            self.assertIs(report.failure, Failure.LAUNCH_FAILED)

    def test_a_process_that_is_not_running_is_not_launch_success(self):
        scripted = happy_path()
        scripted[9] = ("pidof", result(stdout="", returncode=1))
        with self.apk() as apk:
            manager = self.manager(scripted)
            report = self.deploy(manager, apk)

            self.assertIs(report.failure, Failure.PROCESS_NOT_RUNNING)

    def test_a_launch_that_does_not_reach_the_foreground_is_not_verified(self):
        # The device-access policy permits capture and input only while our package is
        # foreground, so a started-but-not-focused app must stop the run.
        with self.apk() as apk:
            manager = self.manager(happy_path(focus=FOCUS_OTHER))
            report = self.deploy(manager, apk)

            self.assertIs(report.failure, Failure.NOT_FOREGROUND)
            self.assertFalse(report.succeeded)
            self.assertTrue(any("capture and input stay gated" in note for note in report.notes))

    def test_build_failure_produces_no_device_command_at_all(self):
        scripted = [
            ("version", result(stdout="Android Debug Bridge version 1.0.41")),
            ("gradle-fake", result(returncode=1, stderr="FAILURE: Build failed")),
        ]
        with self.apk() as apk:
            manager = self.manager(scripted)
            report = self.deploy(manager, apk)

            self.assertIs(report.failure, Failure.BUILD_FAILED)
            self.assertNotIn("devices", manager._runner.joined)


class ClassificationTests(unittest.TestCase):
    def test_hyperos_rejection_is_named_exactly(self):
        self.assertIs(
            classify_failure("Failure [INSTALL_FAILED_USER_RESTRICTED: Install canceled by user]"),
            Failure.INSTALL_USER_RESTRICTED,
        )

    def test_an_unseen_code_is_not_forced_into_a_known_category(self):
        self.assertIs(classify_failure("Failure [INSTALL_FAILED_ENTIRELY_NEW]"), Failure.INSTALL_FAILED)

    def test_a_stack_trace_mentioning_offline_is_still_a_transport_failure(self):
        text = "cmd: Failure calling service package: Broken pipe (32)\nat com.android.server.pm.PackageManagerShellCommand"
        self.assertIs(classify_failure(text), Failure.INSTALL_TRANSPORT_FAILED)


class ConstructorTests(unittest.TestCase):
    def test_a_blank_adb_path_is_refused_at_construction(self):
        with self.assertRaises(ValueError):
            DeploymentManager(adb_path="   ", repo_root=".")

    def test_failure_labels_are_disjoint_from_stage_names(self):
        stage_values = {stage.value for stage in Stage}
        self.assertFalse(stage_values & {failure.value for failure in Failure})


if __name__ == "__main__":
    unittest.main(verbosity=1)
