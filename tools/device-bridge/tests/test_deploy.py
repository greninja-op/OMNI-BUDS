"""Host-side tests for the deployment pipeline.

Every case runs against a scripted fake ADB. No device is attached, and nothing here claims
otherwise: a fake that answers like a HyperOS phone proves the harness handles that answer, not
that the phone behaves this way. Real-device evidence lives in
`docs/development/adb-deployment/validation.md`.

The scripted transport fails the test if the manager issues a command that was not expected, or
runs out of scripted answers, so a silent extra call (an uninstall, a permission grant, a blind
retry) cannot pass unnoticed.
"""

from __future__ import annotations

import contextlib
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from bridge.deploy import (  # noqa: E402
    DESTRUCTIVE_RECOVERY_REQUIRED,
    DeploymentManager,
    Failure,
    Stage,
    classify_failure,
)

PACKAGE = "com.omnibuds.tools.shell"
DEVICES_OUT = f"List of devices attached\nEMULATOR device product:duchamp model:2311DRK48I transport_id:2\n\n"


class Completed:
    """Stands in for subprocess.CompletedProcess without importing the real type's contract."""

    def __init__(self, returncode: int = 0, stdout: str = "", stderr: str = ""):
        self.returncode = returncode
        self.stdout = stdout
        self.stderr = stderr


class FakeAdb:
    def __init__(self, responses: list[tuple[object, object]]):
        self.responses = list(responses)
        self.calls: list[list[str]] = []

    def __call__(self, argv, timeout):
        argv = list(argv)
        self.calls.append(argv)
        # Matched by expectation rather than by position: the queue says which commands are
        # allowed, not the order they were written in. An unscripted command still raises, so a
        # silent uninstall, a permission grant or a blind retry cannot slip through.
        for index, (matcher, result) in enumerate(self.responses):
            if matcher(argv):
                del self.responses[index]
                return result(argv) if callable(result) else result
        raise AssertionError(f"unscripted command issued: {argv}")

    @property
    def joined(self) -> str:
        return " | ".join(" ".join(call) for call in self.calls)


def contains(*needles: str):
    def predicate(argv):
        joined = " ".join(argv)
        return all(needle in joined for needle in needles)

    return predicate


def version_ok():
    return contains("version"), Completed(stdout="Android Debug Bridge version 1.0.41\nVersion 37.0.1")


def build_ok():
    return contains("gradlew-fake"), Completed(stdout="BUILD SUCCESSFUL in 1s\n")


def scripted(package_installed: bool, install_output: str = "Performing Streamed Install\nSuccess\n"):
    """The full command sequence a clean deployment produces, in order."""

    pm_packages = (
        f"package:{PACKAGE}\n" if package_installed else "package:com.example.other\n"
    )
    return [
        version_ok(),
        build_ok(),
        (contains("devices"), Completed(stdout=DEVICES_OUT)),
        (contains("ro.build.version.sdk"), Completed(stdout="34\n")),
        (contains("pm", "list", "packages"), Completed(stdout=pm_packages)),
        (contains("install"), Completed(stdout=install_output)),
        (contains("pm", "list", "packages"), Completed(stdout=f"package:{PACKAGE}\n")),
        (contains("resolve-activity"), Completed(stdout=f"priority=0 match=0x108000\n{PACKAGE}/.ShellActivity\n")),
        (contains("am", "start"), Completed(stdout=f"Starting: Intent {{ cmp={PACKAGE}/.ShellActivity }}\n")),
        (contains("pidof"), Completed(stdout="8703\n")),
    ]


class Harness(unittest.TestCase):
    """Shared fixture: an APK on disk and a manager whose metadata read is stubbed."""

    def build_manager(self, responses, metadata=(PACKAGE, 26)):
        manager = DeploymentManager("/fake/adb", ".", runner=FakeAdb(responses))
        manager.read_apk_metadata = lambda _apk: metadata
        return manager

    @contextlib.contextmanager
    def apk_file(self, content: bytes = b"zip-ish"):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "app-debug.apk"
            apk.write_bytes(content)
            yield apk

    def deploy(self, manager, apk, **kwargs):
        return manager.deploy(
            gradle_task="assembleDebug",
            apk_path=apk,
            expected_package=PACKAGE,
            build_command=["gradlew-fake"],
            **kwargs,
        )


class FlagSelectionTests(Harness):
    def test_absent_package_is_installed_without_the_reinstall_flag(self):
        # This is the defect that started the audit: -r on an absent package is what the device
        # refused. The harness must derive the flag from observed state, not assume it.
        with self.apk_file() as apk:
            manager = self.build_manager(scripted(package_installed=False))
            report = self.deploy(manager, apk)

            self.assertTrue(report.succeeded, report.text())
            install_call = next(call for call in manager._runner.calls if "install" in call)
            self.assertNotIn("-r", install_call)
            self.assertNotIn("-g", install_call)

    def test_present_package_is_reinstalled_so_data_is_preserved(self):
        with self.apk_file() as apk:
            manager = self.build_manager(scripted(package_installed=True))
            report = self.deploy(manager, apk)

            self.assertTrue(report.succeeded, report.text())
            install_call = next(call for call in manager._runner.calls if "install" in call)
            self.assertIn("-r", install_call)
            self.assertNotIn("-g", install_call)
            self.assertEqual(report.install_flags, ("-r",))

    def test_rejected_install_never_triggers_uninstall_or_clear(self):
        refusal = (
            "adb.exe: failed to install: Failure "
            "[INSTALL_FAILED_USER_RESTRICTED: Install canceled by user]"
        )
        with self.apk_file() as apk:
            responses = scripted(package_installed=False, install_output=refusal)
            manager = self.build_manager(responses)
            report = self.deploy(manager, apk)

            self.assertIs(report.failure, Failure.INSTALL_USER_RESTRICTED)
            self.assertFalse(report.succeeded)
            self.assertNotIn("uninstall", manager._runner.joined)
            self.assertNotIn("pm clear", manager._runner.joined)
            self.assertTrue(any("no destructive recovery attempted" in note for note in report.notes))

    def test_an_extra_unscripted_command_fails_loudly(self):
        with self.apk_file() as apk:
            responses = scripted(package_installed=False)
            responses.pop()  # remove pidof: the manager must not invent a process id
            manager = self.build_manager(responses)

            with self.assertRaises(AssertionError):
                self.deploy(manager, apk)


class DeviceSelectionTests(Harness):
    def test_no_devices_is_reported_rather_than_treated_as_success(self):
        manager = self.build_manager(
            [version_ok(), (contains("devices"), Completed(stdout="List of devices attached\n\n"))]
        )

        device, failure = manager.resolve_device(None)

        self.assertIsNone(device)
        self.assertIs(failure, Failure.NO_DEVICES)

    def test_two_ready_devices_demand_an_explicit_serial(self):
        out = "List of devices attached\nAAA device model:one\nBBB device model:two\n"
        manager = self.build_manager(
            [
                version_ok(),
                (contains("devices"), Completed(stdout=out)),
                (contains("devices"), Completed(stdout=out)),
            ]
        )

        device, failure = manager.resolve_device(None)

        self.assertIsNone(device)
        self.assertIs(failure, Failure.AMBIGUOUS_DEVICE)

    def test_explicit_serial_is_not_substituted_with_another_device(self):
        out = "List of devices attached\nAAA unauthorized model:one\nBBB device model:two\n"
        manager = self.build_manager(
            [
                version_ok(),
                (contains("devices"), Completed(stdout=out)),
                (contains("devices"), Completed(stdout=out)),
            ]
        )

        self.assertIs(manager.resolve_device("AAA")[1], Failure.UNAUTHORIZED)
        self.assertEqual(manager.resolve_device("BBB")[0].serial, "BBB")

    def test_ambiguity_stops_the_pipeline_before_any_install(self):
        out = "List of devices attached\nAAA device model:one\nBBB device model:two\n"
        with self.apk_file() as apk:
            manager = self.build_manager(
                [version_ok(), build_ok(), (contains("devices"), Completed(stdout=out))]
            )
            report = self.deploy(manager, apk)

            self.assertIs(report.failure, Failure.AMBIGUOUS_DEVICE)
            self.assertNotIn("install", manager._runner.joined)

    def test_non_adb_executable_is_rejected(self):
        manager = self.build_manager([(contains("version"), Completed(returncode=1, stderr="not a program"))])

        self.assertIs(manager.validate_executable(), Failure.ADB_INVALID)


class ApkValidationTests(Harness):
    def test_missing_apk_is_a_failure_not_a_skip(self):
        with tempfile.TemporaryDirectory() as directory:
            manager = self.build_manager(scripted(package_installed=False))
            report = self.deploy(manager, Path(directory) / "absent.apk")

            self.assertIs(report.failure, Failure.APK_MISSING)

    def test_empty_apk_is_rejected(self):
        with self.apk_file(content=b"") as apk:
            manager = self.build_manager(scripted(package_installed=False))
            report = self.deploy(manager, apk)

            self.assertIs(report.failure, Failure.APK_EMPTY)

    def test_package_id_mismatch_blocks_the_install(self):
        with self.apk_file() as apk:
            manager = self.build_manager(scripted(package_installed=False), metadata=("com.evil.other", 26))
            report = self.deploy(manager, apk)

            self.assertIs(report.failure, Failure.PACKAGE_MISMATCH)
            self.assertNotIn("install", manager._runner.joined)

    def test_apk_newer_than_the_device_is_refused(self):
        responses = [
            version_ok(),
            build_ok(),
            (contains("devices"), Completed(stdout=DEVICES_OUT)),
            (contains("ro.build.version.sdk"), Completed(stdout="24\n")),
        ]
        with self.apk_file() as apk:
            manager = self.build_manager(responses, metadata=(PACKAGE, 26))
            report = self.deploy(manager, apk)

            self.assertIs(report.failure, Failure.SDK_INCOMPATIBLE)

    def test_unreadable_metadata_is_invalid_rather_than_assumed_compatible(self):
        with self.apk_file() as apk:
            manager = self.build_manager(scripted(package_installed=False), metadata=(None, None))
            report = self.deploy(manager, apk)

            self.assertIs(report.failure, Failure.APK_INVALID)


class LaunchAndStageTests(Harness):
    def test_a_clean_run_reports_every_stage_verified(self):
        with self.apk_file() as apk:
            manager = self.build_manager(scripted(package_installed=False))
            report = self.deploy(manager, apk)

            self.assertEqual([stage for stage, _, _ in report.stages], list(Stage))
            self.assertTrue(all(ok for _, ok, _ in report.stages))
            self.assertEqual(report.pid, 8703)
            self.assertEqual(report.activity, f"{PACKAGE}/.ShellActivity")
            self.assertIn("PROCESS_ALIVE_SUCCESS", report.text())

    def test_activity_is_resolved_not_assumed(self):
        with self.apk_file() as apk:
            responses = scripted(package_installed=False)
            responses[-3] = (contains("resolve-activity"), Completed(stdout="priority=0\nNo activity found\n"))
            manager = self.build_manager(responses)
            report = self.deploy(manager, apk)

            self.assertIs(report.failure, Failure.NO_LAUNCH_ACTIVITY)
            self.assertNotIn("am start", manager._runner.joined)

    def test_error_text_from_am_start_is_not_counted_as_a_launch(self):
        with self.apk_file() as apk:
            responses = scripted(package_installed=False)
            responses[-2] = (
                contains("am", "start"),
                Completed(stdout="Error: Activity class {com.omnibuds.tools.shell/x} does not exist."),
            )
            manager = self.build_manager(responses)
            report = self.deploy(manager, apk)

            self.assertIs(report.failure, Failure.LAUNCH_FAILED)
            self.assertFalse(report.succeeded)

    def test_started_but_dead_process_is_not_launch_success(self):
        with self.apk_file() as apk:
            responses = scripted(package_installed=False)
            responses[-1] = (contains("pidof"), Completed(returncode=1, stdout=""))
            manager = self.build_manager(responses)
            report = self.deploy(manager, apk)

            self.assertIs(report.failure, Failure.PROCESS_NOT_RUNNING)

    def test_build_failure_precedes_any_device_contact(self):
        with self.apk_file() as apk:
            manager = self.build_manager(
                [version_ok(), (contains("gradlew-fake"), Completed(returncode=1, stderr="boom"))]
            )
            report = self.deploy(manager, apk)

            self.assertIs(report.failure, Failure.BUILD_FAILED)
            self.assertNotIn("devices", manager._runner.joined)


class ClassificationTests(unittest.TestCase):
    def test_hyperos_rejection_is_named_exactly(self):
        self.assertIs(
            classify_failure("Failure [INSTALL_FAILED_USER_RESTRICTED: Install canceled by user]"),
            Failure.INSTALL_USER_RESTRICTED,
        )

    def test_signature_mismatch_is_recognised_and_gated(self):
        self.assertIs(
            classify_failure("Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match]"),
            Failure.INSTALL_SIGNATURE_MISMATCH,
        )
        self.assertIn(Failure.INSTALL_SIGNATURE_MISMATCH, DESTRUCTIVE_RECOVERY_REQUIRED)

    def test_an_unseen_code_is_not_forced_into_a_known_category(self):
        self.assertIs(classify_failure("Failure [INSTALL_FAILED_SOMETHING_NOVEL]"), Failure.INSTALL_FAILED)

    def test_transport_stack_trace_is_not_reported_as_a_policy_refusal(self):
        text = (
            "cmd: Failure calling service package: Broken pipe (32)\n"
            "at com.android.server.pm.PackageManagerShellCommand.runInstall"
        )
        self.assertIs(classify_failure(text), Failure.INSTALL_TRANSPORT_FAILED)


if __name__ == "__main__":
    unittest.main(verbosity=2)
