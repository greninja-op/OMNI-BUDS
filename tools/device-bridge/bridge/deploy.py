"""Safe, state-aware Android deployment pipeline for the local-first device bridge.

Why this module exists
----------------------
The harness originally installed with an unconditional ``adb install -r``. On the
Xiaomi/POCO HyperOS device used for development that produced::

    INSTALL_FAILED_USER_RESTRICTED: Install canceled by user

while a plain ``adb install`` of the same APK succeeded immediately, and ``-r``
subsequently succeeded once the package existed. The failure was never a phone
misconfiguration and never an ADB-version difference - it was this harness passing a
reinstall flag for a package that was not installed. The evidence and the hypotheses that
were ruled out are in ``docs/development/adb-deployment/troubleshooting.md``.

Everything that already exists in :mod:`bridge.adb` is reused rather than reimplemented, so
each question has exactly one answer in this codebase:

* how ``adb devices -l`` is parsed - ``parse_device_listing``;
* how a device API level is read and validated - ``parse_sdk_value``;
* what an install command may contain - ``build_install_argv``, which cannot emit ``-g``;
* how the foreground package is read - ``parse_focus_package``;
* the subprocess seam - ``CommandRunner`` / ``CommandResult`` (bytes, ``timeout_s`` keyword).

Safety rules the pipeline does not negotiate:

* no ``-g``, ever; permission granting is a separate named call on ``AdbTransport``;
* no ``adb uninstall``, no ``pm clear`` - nothing is destroyed to make an install succeed;
* no blind retry after a rejection;
* explicit device selection - never a random target when more than one is addressable;
* a stage is reported succeeded only when a command confirmed it, and launch is confirmed by
  a live process *and* by the foreground package, not by ``am start`` printing a line.

The whole pipeline runs against an injected ``runner``, so tests exercise every stage with a
scripted fake and no device attached.
"""

from __future__ import annotations

import os
import re
import shutil
from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path
from typing import Sequence

from .adb import (
    CommandResult,
    CommandRunner,
    AdbDeviceRecord,
    build_install_argv,
    parse_device_listing,
    parse_focus_package,
    parse_sdk_value,
    subprocess_command_runner,
)
from .errors import AdbFailed

#: Failures whose only "fix" is removing or replacing what is already on the phone. The
#: pipeline never acts on them; it returns them for a human decision.
DESTRUCTIVE_RECOVERY_REQUIRED: frozenset["Failure"] = frozenset()  # bound after Failure is defined


class Stage(str, Enum):
    """Ordered pipeline stages. Values are the stable names used in reports."""

    ADB_VALIDATED = "ADB_VALIDATED"
    BUILD = "BUILD"
    DEVICE_RESOLVED = "DEVICE_RESOLVED"
    APK_VALIDATED = "APK_VALIDATED"
    INSTALL = "INSTALL"
    PACKAGE_VERIFIED = "PACKAGE_VERIFIED"
    LAUNCH = "LAUNCH"
    PROCESS_ALIVE = "PROCESS_ALIVE"
    FOREGROUND_VERIFIED = "FOREGROUND_VERIFIED"


class Failure(str, Enum):
    """Classified failures. Unknown causes stay unknown rather than being guessed."""

    NO_DEVICES = "NO_DEVICES"
    AMBIGUOUS_DEVICE = "AMBIGUOUS_DEVICE"
    UNAUTHORIZED = "UNAUTHORIZED"
    OFFLINE = "OFFLINE"
    ADB_INVALID = "ADB_INVALID"
    BUILD_FAILED = "BUILD_FAILED"
    APK_MISSING = "APK_MISSING"
    APK_EMPTY = "APK_EMPTY"
    APK_INVALID = "APK_INVALID"
    PACKAGE_MISMATCH = "PACKAGE_MISMATCH"
    SDK_INCOMPATIBLE = "SDK_INCOMPATIBLE"
    INSTALL_USER_RESTRICTED = "INSTALL_USER_RESTRICTED"
    INSTALL_ALREADY_EXISTS = "INSTALL_ALREADY_EXISTS"
    INSTALL_SIGNATURE_MISMATCH = "INSTALL_SIGNATURE_MISMATCH"
    INSTALL_TRANSPORT_FAILED = "INSTALL_TRANSPORT_FAILED"
    DEVICE_LOST_DURING_INSTALL = "DEVICE_LOST_DURING_INSTALL"
    NO_LAUNCH_ACTIVITY = "NO_LAUNCH_ACTIVITY"
    LAUNCH_FAILED = "LAUNCH_FAILED"
    PROCESS_NOT_RUNNING = "PROCESS_NOT_RUNNING"
    NOT_FOREGROUND = "NOT_FOREGROUND"
    INSTALL_FAILED = "INSTALL_FAILED"


DESTRUCTIVE_RECOVERY_REQUIRED = frozenset(
    {
        Failure.INSTALL_SIGNATURE_MISMATCH,
        Failure.INSTALL_ALREADY_EXISTS,
        Failure.SDK_INCOMPATIBLE,
    }
)


#: Order matters: a HyperOS refusal can carry a stack trace that mentions unrelated words, so
#: specific install codes are matched before generic transport and transport before generic state.
_FAILURE_PATTERNS: tuple[tuple[str, Failure], ...] = (
    ("INSTALL_FAILED_USER_RESTRICTED", Failure.INSTALL_USER_RESTRICTED),
    ("INSTALL_FAILED_ALREADY_EXISTS", Failure.INSTALL_ALREADY_EXISTS),
    ("INSTALL_FAILED_UPDATE_INCOMPATIBLE", Failure.INSTALL_SIGNATURE_MISMATCH),
    ("SIGNATURE_VERIFICATION_ERROR", Failure.INSTALL_SIGNATURE_MISMATCH),
    ("INSTALL_FAILED_OLDER_SDK", Failure.SDK_INCOMPATIBLE),
    ("device not found", Failure.DEVICE_LOST_DURING_INSTALL),
    ("no devices/emulators found", Failure.NO_DEVICES),
    ("cmd: Failure", Failure.INSTALL_TRANSPORT_FAILED),
    ("unauthorized", Failure.UNAUTHORIZED),
    ("offline", Failure.OFFLINE),
)


def classify_failure(text: str) -> Failure:
    """Map installer output to a classification.

    Anything unrecognised stays ``INSTALL_FAILED``. Inventing a category for an unseen code
    would hide a new device behaviour behind an old explanation.
    """

    upper = text.upper()
    for needle, failure in _FAILURE_PATTERNS:
        if needle.upper() in upper:
            return failure
    return Failure.INSTALL_FAILED


@dataclass(frozen=True)
class ApkMetadata:
    """What was read out of an APK. ``None`` means unreadable, never empty."""

    package: str | None = None
    min_sdk: int | None = None
    version_code: int | None = None


_PACKAGE_RE = re.compile(r"package:\s*name='(?P<name>[^']+)'(?:\s+versionCode='(?P<code>-?\d+)')?")
_SDK_RE = re.compile(r"sdkVersion:'(?P<sdk>\d+)'")
_MIN_SDK_RE = re.compile(r"minSdkVersion:'(?P<sdk>\d+)'")


def parse_aapt_badging(text: str) -> ApkMetadata:
    """Read package id, versionCode and the minimum SDK out of ``aapt dump --badging`` output.

    A missing package line yields an empty record rather than a guess: an APK whose identity
    cannot be read must not be installed under an assumed package name.
    """

    package = _PACKAGE_RE.search(text)
    if package is None:
        return ApkMetadata()
    sdk = _SDK_RE.search(text) or _MIN_SDK_RE.search(text)
    code = package.group("code")
    return ApkMetadata(
        package=package.group("name"),
        min_sdk=int(sdk.group("sdk")) if sdk else None,
        version_code=int(code) if code is not None else None,
    )


@dataclass
class DeployReport:
    """Structured outcome of a deployment attempt.

    ``succeeded`` is true only when every stage was reached and verified. A run that stops
    early reports the failed stage and never presents partial progress as success.
    """

    stages: list[tuple[Stage, bool, str]] = field(default_factory=list)
    failure: Failure | None = None
    device_serial: str | None = None
    device_model: str | None = None
    device_sdk: int | None = None
    package: str | None = None
    activity: str | None = None
    pid: int | None = None
    install_flags: tuple[str, ...] = ()
    apk_path: str | None = None
    notes: list[str] = field(default_factory=list)

    @property
    def succeeded(self) -> bool:
        return (
            self.failure is None
            and len(self.stages) == len(Stage)
            and all(ok for _, ok, _ in self.stages)
        )

    def record(self, stage: Stage, ok: bool, detail: str = "") -> None:
        self.stages.append((stage, ok, detail))

    def text(self) -> str:
        lines = [
            f"{stage.value}_{'SUCCESS' if ok else 'FAILED'}{f' :: {detail}' if detail else ''}"
            for stage, ok, detail in self.stages
        ]
        if self.failure is not None:
            lines.append(f"FAILURE={self.failure.value}")
        lines.append(f"OVERALL={'SUCCESS' if self.succeeded else 'NOT_VERIFIED'}")
        return "\n".join(lines)


class DeploymentManager:
    """Detect, build, validate, install, verify, launch, and confirm the foreground."""

    def __init__(
        self,
        *,
        adb_path: str,
        repo_root: str | os.PathLike[str],
        runner: CommandRunner | None = None,
        timeout_s: float = 180.0,
    ) -> None:
        if not adb_path or not str(adb_path).strip():
            raise ValueError("DeploymentManager requires an ADB executable path or name")
        self._adb = str(adb_path).strip()
        self._repo = Path(repo_root)
        self._runner: CommandRunner = runner if runner is not None else subprocess_command_runner
        self._timeout_s = float(timeout_s)

    # ---- transport plumbing ---------------------------------------------------

    def _run(self, argv: Sequence[str], *, timeout_s: float | None = None) -> CommandResult:
        return self._runner(list(argv), timeout_s=timeout_s if timeout_s is not None else self._timeout_s)

    def _device_argv(self, serial: str, *args: str) -> list[str]:
        return [self._adb, "-s", serial, *args]

    def validate_executable(self) -> Failure | None:
        """Reject anything that does not identify itself as ADB, instead of falling through."""

        result = self._run([self._adb, "version"], timeout_s=20.0)
        if result.returncode != 0 or "Android Debug Bridge" not in result.stdout_text:
            return Failure.ADB_INVALID
        return None

    def devices(self) -> tuple[AdbDeviceRecord, ...]:
        result = self._run([self._adb, "devices", "-l"], timeout_s=20.0)
        return parse_device_listing(result.stdout_text)

    def resolve_device(self, serial: str | None) -> tuple[AdbDeviceRecord | None, Failure | None, str | None]:
        """Never deploy to a random device.

        Ambiguity is an answer, not a choice: with more than one addressable device and no
        explicit serial, the pipeline stops.
        """

        found = self.devices()
        if not found:
            return None, Failure.NO_DEVICES, "no device is listed by adb devices -l"

        if serial is not None:
            target = next((d for d in found if d.serial == serial), None)
            if target is None:
                return None, Failure.NO_DEVICES, f"serial {serial!r} is not attached"
            if not target.is_addressable:
                return None, self._state_failure(target), f"{serial} is {target.state.value}"
            return target, None, None

        ready = [d for d in found if d.is_addressable]
        if not ready:
            states = {d.state.value for d in found}
            if "unauthorized" in states:
                return None, Failure.UNAUTHORIZED, "accept the USB debugging authorisation prompt on the device"
            return None, Failure.OFFLINE, "no addressable device"
        if len(ready) > 1:
            return None, Failure.AMBIGUOUS_DEVICE, "pass an explicit serial; candidates: " + ", ".join(d.serial for d in ready)
        return ready[0], None, None

    @staticmethod
    def _state_failure(device: AdbDeviceRecord) -> Failure:
        return Failure.UNAUTHORIZED if device.state.value == "unauthorized" else Failure.OFFLINE

    # ---- device and APK facts -------------------------------------------------

    def device_sdk_level(self, serial: str) -> int | None:
        """The device API level, or ``None`` when it cannot be read. Unknown stays unknown."""

        result = self._run(self._device_argv(serial, "shell", "getprop", "ro.build.version.sdk"), timeout_s=20.0)
        try:
            return parse_sdk_value(result.stdout_text)
        except AdbFailed:
            return None

    def is_package_installed(self, serial: str, package: str) -> bool:
        result = self._run(self._device_argv(serial, "shell", "pm", "list", "packages"), timeout_s=45.0)
        wanted = f"package:{package}"
        return any(line.strip() == wanted for line in result.stdout_text.splitlines())

    def read_apk_metadata(self, apk: Path) -> ApkMetadata:
        """Read identity and minimum SDK with ``aapt``.

        Only ``aapt`` is used: ``aapt2`` has no equivalent ``dump --badging`` output, and
        pretending otherwise would produce a confident-looking wrong answer. No tool means an
        empty record, which the pipeline treats as ``APK_INVALID`` rather than as compatible.
        """

        aapt = self._resolve_tool("aapt")
        if aapt is None:
            return ApkMetadata()
        result = self._run([aapt, "dump", "--badging", str(apk)], timeout_s=90.0)
        if result.returncode != 0:
            return ApkMetadata()
        return parse_aapt_badging(result.stdout_text)

    @staticmethod
    def _resolve_tool(name: str) -> str | None:
        found = shutil.which(name)
        if found:
            return found
        for var in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
            root = os.environ.get(var)
            if not root:
                continue
            build_tools = Path(root) / "build-tools"
            if not build_tools.is_dir():
                continue
            for candidate in sorted((p for p in build_tools.iterdir() if p.is_dir()), reverse=True):
                for suffix in (".exe", ""):
                    tool = candidate / f"{name}{suffix}"
                    if tool.is_file():
                        return str(tool)
        return None

    def resolve_launcher_activity(self, serial: str, package: str) -> str | None:
        """Resolve the real launchable component. The activity name is never assumed."""

        result = self._run(
            self._device_argv(
                serial, "shell", "cmd", "package", "resolve-activity",
                "-c", "android.intent.category.LAUNCHER", "--brief", package,
            ),
            timeout_s=45.0,
        )
        for line in reversed(result.stdout_text.splitlines()):
            token = line.strip()
            if token.startswith(f"{package}/") and " " not in token:
                return token
        return None

    def foreground_package(self, serial: str) -> str | None:
        """The foreground package, or ``None`` when it cannot be established.

        ``parse_focus_package`` raises on an unresolvable focus line and the exception is
        converted to ``None`` deliberately: callers treat "unknown" as a refusal to capture or
        tap, never as permission.
        """

        result = self._run(self._device_argv(serial, "shell", "dumpsys", "window"), timeout_s=45.0)
        try:
            return parse_focus_package(result.stdout_text)
        except AdbFailed:
            return None

    def process_id(self, serial: str, package: str) -> int | None:
        result = self._run(self._device_argv(serial, "shell", "pidof", package), timeout_s=30.0)
        tokens = result.stdout_text.split()
        return int(tokens[0]) if tokens and tokens[0].isdigit() else None

    # ---- the pipeline ---------------------------------------------------------

    def deploy(
        self,
        *,
        gradle_task: str,
        apk_path: str | os.PathLike[str],
        expected_package: str,
        serial: str | None = None,
        build_command: Sequence[str] | None = None,
    ) -> DeployReport:
        """Run every stage in order, verifying each one before reporting it."""

        report = DeployReport(package=expected_package, device_serial=serial)

        invalid = self.validate_executable()
        if invalid is not None:
            report.failure = invalid
            report.record(Stage.ADB_VALIDATED, False, "the configured executable did not identify itself as ADB")
            return report
        report.record(Stage.ADB_VALIDATED, True, self._adb)

        argv = list(build_command) if build_command is not None else [str(self._repo / "gradlew"), gradle_task]
        build = self._run(argv, timeout_s=max(self._timeout_s, 900.0))
        if build.returncode != 0:
            report.failure = Failure.BUILD_FAILED
            report.record(Stage.BUILD, False, (build.stderr_text or build.stdout_text)[-400:])
            return report
        report.record(Stage.BUILD, True, gradle_task)

        # Device first: APK compatibility must be judged against a real API level, not a guess.
        device, device_failure, detail = self.resolve_device(serial)
        if device is None:
            report.failure = device_failure or Failure.NO_DEVICES
            report.record(Stage.DEVICE_RESOLVED, False, detail or "")
            return report
        report.device_serial = device.serial
        report.device_model = device.model
        report.device_sdk = self.device_sdk_level(device.serial)
        report.record(
            Stage.DEVICE_RESOLVED,
            True,
            f"{device.serial} ({device.model or 'model not reported'}, API {report.device_sdk if report.device_sdk is not None else 'unknown'})",
        )

        apk = Path(apk_path)
        validation = self._validate_apk(apk, expected_package, report.device_sdk)
        if validation is not None:
            report.failure = validation
            report.record(Stage.APK_VALIDATED, False, validation.value)
            return report
        report.apk_path = str(apk)
        report.record(Stage.APK_VALIDATED, True, apk.name)

        reinstall = self.is_package_installed(device.serial, expected_package)
        install_argv = build_install_argv(self._adb, device.serial, str(apk), reinstall=reinstall)
        report.install_flags = tuple(arg for arg in install_argv if arg.startswith("-") and arg != "-s")
        install = self._run(install_argv, timeout_s=300.0)
        output = f"{install.stdout_text}\n{install.stderr_text}"
        if install.returncode != 0 or "Success" not in output:
            failure = classify_failure(output)
            report.failure = failure
            report.record(Stage.INSTALL, False, failure.value)
            report.notes.append(
                "no destructive recovery attempted: nothing was uninstalled and no application data was cleared"
            )
            if failure in DESTRUCTIVE_RECOVERY_REQUIRED:
                report.notes.append(
                    "this class of failure can only be cleared by removing or replacing the existing "
                    "install, which needs an explicit human decision"
                )
            if failure is Failure.INSTALL_USER_RESTRICTED and not reinstall:
                report.notes.append(
                    "the refusal carried -r for a package read as absent; the verified path on this "
                    "device is a plain install, so the flag must not be forced"
                )
            return report
        report.record(Stage.INSTALL, True, f"reinstall={reinstall}")

        if not self.is_package_installed(device.serial, expected_package):
            report.failure = Failure.PACKAGE_MISMATCH
            report.record(Stage.PACKAGE_VERIFIED, False, "the package is not listed after a reported success")
            return report
        report.record(Stage.PACKAGE_VERIFIED, True, expected_package)

        activity = self.resolve_launcher_activity(device.serial, expected_package)
        if activity is None:
            report.failure = Failure.NO_LAUNCH_ACTIVITY
            report.record(Stage.LAUNCH, False, "no launcher activity resolved for the package")
            return report
        report.activity = activity
        launch = self._run(
            self._device_argv(device.serial, "shell", "am", "start", "-n", activity),
            timeout_s=60.0,
        )
        launch_text = f"{launch.stdout_text}{launch.stderr_text}"
        if launch.returncode != 0 or "Error" in launch_text or "Exception" in launch_text:
            report.failure = Failure.LAUNCH_FAILED
            report.record(Stage.LAUNCH, False, launch_text[-300:])
            return report
        report.record(Stage.LAUNCH, True, activity)

        pid = self.process_id(device.serial, expected_package)
        if pid is None:
            report.failure = Failure.PROCESS_NOT_RUNNING
            report.record(Stage.PROCESS_ALIVE, False, "pidof returned no process")
            return report
        report.pid = pid
        report.record(Stage.PROCESS_ALIVE, True, f"pid {pid}")

        focus = self.foreground_package(device.serial)
        if focus != expected_package:
            report.failure = Failure.NOT_FOREGROUND
            detail = f"foreground is {focus or 'unreadable'}"
            report.notes.append(
                "capture and input stay gated until the harness package is foreground: another "
                "app's screen is out of scope under docs/security/device-access-policy.md"
            )
            report.record(Stage.FOREGROUND_VERIFIED, False, detail)
            return report
        report.record(Stage.FOREGROUND_VERIFIED, True, expected_package)
        return report

    def _validate_apk(self, apk: Path, expected_package: str, device_sdk: int | None) -> Failure | None:
        if not apk.is_file():
            return Failure.APK_MISSING
        if apk.stat().st_size == 0:
            return Failure.APK_EMPTY
        metadata = self.read_apk_metadata(apk)
        if metadata.package is None:
            return Failure.APK_INVALID
        if metadata.package != expected_package:
            return Failure.PACKAGE_MISMATCH
        if metadata.min_sdk is None:
            return Failure.APK_INVALID
        if device_sdk is not None and device_sdk < metadata.min_sdk:
            return Failure.SDK_INCOMPATIBLE
        return None
