"""Safe, state-aware Android deployment pipeline for the local-first device bridge.

Why this module exists
----------------------
The harness originally installed with an unconditional ``adb install -r``. On the
Xiaomi/POCO HyperOS device used for development that produced::

    INSTALL_FAILED_USER_RESTRICTED: Install canceled by user

while a plain ``adb install`` of the same APK succeeded immediately, and ``-r``
subsequently succeeded once the package existed. The failure was therefore never a
phone misconfiguration and never an ADB-version difference - it was the harness passing a
reinstall flag for a package that was not installed. This module encodes what the device
actually accepts, without weakening any safety rule:

* no ``-g`` (no blanket runtime-permission grant, ever, by default);
* no ``adb uninstall`` as a recovery step, and no ``pm clear``;
* no blind retry after a rejection;
* explicit device selection, never a random target when more than one is connected;
* a stage result is only reported as succeeded when it was verified.

Every external effect is funnelled through an injected ``runner`` callable so the whole
pipeline is testable with a fake transport and no device attached.
"""

from __future__ import annotations

import re
import subprocess
from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path
from typing import Callable, Sequence


class Stage(str, Enum):
    """Ordered pipeline stages. Values are the stable names used in reports."""

    BUILD = "BUILD"
    DEVICE_RESOLVED = "DEVICE_RESOLVED"
    APK_VALIDATED = "APK_VALIDATED"
    INSTALL = "INSTALL"
    PACKAGE_VERIFIED = "PACKAGE_VERIFIED"
    LAUNCH = "LAUNCH"
    PROCESS_ALIVE = "PROCESS_ALIVE"


class Failure(str, Enum):
    """Classified failures. Unknown causes stay unknown rather than being guessed."""

    NO_DEVICES = "NO_DEVICES"
    AMBIGUOUS_DEVICE = "AMBIGUOUS_DEVICE"
    UNAUTHORIZED = "UNAUTHORIZED"
    OFFLINE = "OFFLINE"
    BUILD_FAILED = "BUILD_FAILED"
    APK_MISSING = "APK_MISSING"
    APK_EMPTY = "APK_EMPTY"
    APK_INVALID = "APK_INVALID"
    PACKAGE_MISMATCH = "PACKAGE_MISMATCH"
    SDK_INCOMPATIBLE = "SDK_INCOMPATIBLE"
    INSTALL_USER_RESTRICTED = "INSTALL_USER_RESTRICTED"
    INSTALL_ALREADY_EXISTS = "INSTALL_ALREADY_EXISTS"
    INSTALL_SIGNATURE_MISMATCH = "INSTALL_SIGNATURE_MISMATCH"
    INSTALL_FAILED = "INSTALL_FAILED"
    INSTALL_TRANSPORT_FAILED = "INSTALL_TRANSPORT_FAILED"
    DEVICE_LOST_DURING_INSTALL = "DEVICE_LOST_DURING_INSTALL"
    NO_LAUNCH_ACTIVITY = "NO_LAUNCH_ACTIVITY"
    LAUNCH_FAILED = "LAUNCH_FAILED"
    PROCESS_NOT_RUNNING = "PROCESS_NOT_RUNNING"
    ADB_NOT_FOUND = "ADB_NOT_FOUND"
    ADB_INVALID = "ADB_INVALID"


#: Failure modes that would be "fixed" by removing the app. They are never auto-repaired.
DESTRUCTIVE_RECOVERY_REQUIRED: frozenset[Failure] = frozenset(
    {
        Failure.INSTALL_SIGNATURE_MISMATCH,
        Failure.INSTALL_ALREADY_EXISTS,
        Failure.SDK_INCOMPATIBLE,
    }
)


@dataclass(frozen=True)
class Device:
    """One entry from ``adb devices -l``."""

    serial: str
    state: str
    model: str | None = None
    product: str | None = None
    transport_id: str | None = None

    @property
    def is_ready(self) -> bool:
        return self.state == "device"


@dataclass
class DeployReport:
    """Structured outcome of a deployment attempt.

    ``succeeded`` is true only when every stage through ``PROCESS_ALIVE`` was verified. A
    run that stopped early reports the failed stage rather than a partial success.
    """

    stages: list[tuple[Stage, bool, str]] = field(default_factory=list)
    failure: Failure | None = None
    device_serial: str | None = None
    package: str | None = None
    activity: str | None = None
    pid: int | None = None
    install_flags: tuple[str, ...] = ()
    notes: list[str] = field(default_factory=list)

    @property
    def succeeded(self) -> bool:
        return self.failure is None and len(self.stages) == len(Stage) and all(ok for _, ok, _ in self.stages)

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


Runner = Callable[[Sequence[str], float], subprocess.CompletedProcess]


def default_runner(argv: Sequence[str], timeout: float) -> subprocess.CompletedProcess:
    """Run a command without a shell. Never ``shell=True``: arguments are already a list."""

    return subprocess.run(list(argv), capture_output=True, text=True, timeout=timeout, check=False)


_DEVICE_LINE = re.compile(
    r"^(?P<serial>\S+)\s+(?P<state>device|unauthorized|offline|recovery|sideload|bootloader)"
    r"(?:\s+.*model:(?P<model>\S+))?(?:\s+.*product:(?P<product>\S+))?(?:\s+transport_id:(?P<transport>\S+))?"
)

_FAILURE_PATTERNS: tuple[tuple[str, Failure], ...] = (
    ("INSTALL_FAILED_USER_RESTRICTED", Failure.INSTALL_USER_RESTRICTED),
    ("INSTALL_FAILED_ALREADY_EXISTS", Failure.INSTALL_ALREADY_EXISTS),
    ("INSTALL_FAILED_UPDATE_INCOMPATIBLE", Failure.INSTALL_SIGNATURE_MISMATCH),
    ("SIGNATURE_VERIFICATION_ERROR", Failure.INSTALL_SIGNATURE_MISMATCH),
    ("INSTALL_FAILED_OLDER_SDK", Failure.SDK_INCOMPATIBLE),
    ("INSTALL_FAILED_SHARE_UID", Failure.INSTALL_FAILED),
    ("device not found", Failure.DEVICE_LOST_DURING_INSTALL),
    ("no devices/emulators found", Failure.NO_DEVICES),
    ("offline", Failure.OFFLINE),
    ("unauthorized", Failure.UNAUTHORIZED),
    ("cmd: Failure", Failure.INSTALL_TRANSPORT_FAILED),
)


def classify_failure(text: str) -> Failure:
    """Map installer output to a stage-level classification.

    Order matters: a HyperOS rejection can carry both a code and the phrase "offline" in a
    stack trace, so the specific install codes are matched before generic transport text.
    """

    upper = text.upper()
    for needle, failure in _FAILURE_PATTERNS:
        if needle.upper() in upper:
            return failure
    return Failure.INSTALL_FAILED


class DeploymentManager:
    """Detect, build, validate, install, verify package and launch, with explicit stages."""

    def __init__(
        self,
        adb_path: str | Path,
        repo_root: str | Path,
        *,
        runner: Runner | None = None,
        command_timeout: float = 180.0,
    ) -> None:
        self._adb = str(adb_path)
        self._repo = Path(repo_root)
        self._runner: Runner = runner or default_runner
        self._timeout = command_timeout

    # ---- ADB plumbing ---------------------------------------------------------

    def _adb_argv(self, serial: str | None, *args: str) -> list[str]:
        if serial is None:
            return [self._adb, *args]
        return [self._adb, "-s", serial, *args]

    def _run(self, argv: Sequence[str], timeout: float | None = None):
        return self._runner(list(argv), timeout or self._timeout)

    def validate_executable(self) -> Failure | None:
        proc = self._run([self._adb, "version"], timeout=20.0)
        if proc.returncode != 0 or "Android Debug Bridge" not in (proc.stdout or ""):
            return Failure.ADB_INVALID
        return None

    def devices(self) -> list[Device]:
        proc = self._run([self._adb, "devices", "-l"], timeout=20.0)
        out = (proc.stdout or "").splitlines()
        parsed: list[Device] = []
        for line in out[1:]:
            match = _DEVICE_LINE.match(line.strip())
            if not match:
                continue
            groups = match.groupdict()
            parsed.append(
                Device(
                    serial=groups["serial"],
                    state=groups["state"],
                    model=groups.get("model"),
                    product=groups.get("product"),
                    transport_id=groups.get("transport"),
                )
            )
        return parsed

    def resolve_device(self, serial: str | None) -> tuple[Device | None, Failure | None]:
        """Never deploy to a random device.

        With more than one candidate and no explicit serial the answer is ambiguity, not a
        choice - the caller must say which phone they mean.
        """

        found = self.devices()
        if not found:
            return None, Failure.NO_DEVICES
        if serial:
            target = next((d for d in found if d.serial == serial), None)
            if target is None:
                return None, Failure.NO_DEVICES
            if target.state == "unauthorized":
                return None, Failure.UNAUTHORIZED
            if target.state != "device":
                return None, Failure.OFFLINE
            return target, None

        ready = [d for d in found if d.is_ready]
        if len(ready) == 0:
            if any(d.state == "unauthorized" for d in found):
                return None, Failure.UNAUTHORIZED
            return None, Failure.OFFLINE
        if len(ready) > 1:
            return None, Failure.AMBIGUOUS_DEVICE
        return ready[0], None

    # ---- APK metadata and validation ----------------------------------------

    def installed_package_state(self, serial: str, package: str) -> bool:
        proc = self._run(self._adb_argv(serial, "shell", "pm", "list", "packages"), timeout=45.0)
        return any(line.strip() == f"package:{package}" for line in (proc.stdout or "").splitlines())

    def device_api_level(self, serial: str) -> int | None:
        """The device's own API level, or None when it cannot be read. Unknown stays unknown."""

        proc = self._run(self._adb_argv(serial, "shell", "getprop", "ro.build.version.sdk"), timeout=20.0)
        text = (proc.stdout or "").strip()
        return int(text) if text.isdigit() else None

    def installed_version_code(self, serial: str, package: str) -> int | None:
        proc = self._run(
            self._adb_argv(serial, "shell", "dumpsys", "package", package),
            timeout=60.0,
        )
        match = re.search(r"versionCode=(\d+)", proc.stdout or "")
        return int(match.group(1)) if match else None

    # ---- The pipeline ---------------------------------------------------------

    def deploy(
        self,
        *,
        gradle_task: str,
        apk_path: str | Path,
        expected_package: str,
        serial: str | None = None,
        build_command: Sequence[str] | None = None,
    ) -> DeployReport:
        """Run the whole pipeline. Each stage is verified before it is reported."""

        report = DeployReport(package=expected_package)
        report.device_serial = serial

        invalid = self.validate_executable()
        if invalid is not None:
            report.failure = invalid
            report.record(Stage.DEVICE_RESOLVED, False, "adb executable rejected validation")
            return report

        # ---- BUILD
        argv = list(build_command) if build_command else [str(self._repo / "gradlew"), gradle_task]
        build = self._run(argv, timeout=max(self._timeout, 900.0))
        if build.returncode != 0:
            report.failure = Failure.BUILD_FAILED
            report.record(Stage.BUILD, False, (build.stderr or build.stdout or "")[-400:])
            return report
        report.record(Stage.BUILD, True, gradle_task)

        # ---- DEVICE_RESOLVED (first, so APK compatibility is judged against a real number)
        device, device_failure = self.resolve_device(serial)
        if device is None:
            report.failure = device_failure or Failure.NO_DEVICES
            report.record(Stage.DEVICE_RESOLVED, False, (device_failure or Failure.NO_DEVICES).value)
            return report
        report.device_serial = device.serial
        report.record(Stage.DEVICE_RESOLVED, True, f"{device.serial} ({device.model or 'model unknown'})")
        device_sdk = self.device_api_level(device.serial)

        # ---- APK_VALIDATED
        apk = Path(apk_path)
        validation = self._validate_apk(apk, expected_package, device_sdk)
        if validation is not None:
            report.failure = validation
            report.record(Stage.APK_VALIDATED, False, validation.value)
            return report
        report.record(Stage.APK_VALIDATED, True, apk.name)

        # ---- INSTALL: flags chosen from observed state, never a blanket -g.
        present = self.installed_package_state(device.serial, expected_package)
        flags = ("-r",) if present else ()
        report.install_flags = flags
        install = self._run([self._adb, "-s", device.serial, "install", *flags, str(apk)], timeout=300.0)
        output = f"{install.stdout or ''}\n{install.stderr or ''}"
        if install.returncode != 0 or "Success" not in output:
            failure = classify_failure(output)
            report.failure = failure
            report.record(Stage.INSTALL, False, failure.value)
            report.notes.append(
                "no destructive recovery attempted: the existing app was not uninstalled and no "
                "application data was cleared"
            )
            if failure in DESTRUCTIVE_RECOVERY_REQUIRED:
                report.notes.append(
                    "this failure class would require removing or replacing the existing install, "
                    "which needs an explicit human decision"
                )
            if failure is Failure.INSTALL_USER_RESTRICTED and not present:
                report.notes.append(
                    "this rejection occurred with -r on an ABSENT package; retrying without -r is "
                    "the verified path on this device, so the harness will not use -r for a first install"
                )
            return report
        report.record(Stage.INSTALL, True, "streamed install accepted")

        # ---- PACKAGE_VERIFIED
        if not self.installed_package_state(device.serial, expected_package):
            report.failure = Failure.PACKAGE_MISMATCH
            report.record(Stage.PACKAGE_VERIFIED, False, "package not listed after install")
            return report
        report.record(Stage.PACKAGE_VERIFIED, True, expected_package)

        # ---- LAUNCH: resolve the real activity instead of assuming a name.
        activity = self.resolve_launcher_activity(device.serial, expected_package)
        if activity is None:
            report.failure = Failure.NO_LAUNCH_ACTIVITY
            report.record(Stage.LAUNCH, False, "no launcher activity resolved")
            return report
        report.activity = activity
        launch = self._run(self._adb_argv(device.serial, "shell", "am", "start", "-n", activity), timeout=60.0)
        launch_text = f"{launch.stdout or ''}{launch.stderr or ''}"
        if launch.returncode != 0 or "Error" in launch_text or "Exception" in launch_text:
            report.failure = Failure.LAUNCH_FAILED
            report.record(Stage.LAUNCH, False, launch_text[-300:])
            return report
        report.record(Stage.LAUNCH, True, activity)

        # ---- PROCESS_ALIVE
        pid = self.process_id(device.serial, expected_package)
        if pid is None:
            report.failure = Failure.PROCESS_NOT_RUNNING
            report.record(Stage.PROCESS_ALIVE, False, "pidof returned nothing")
            return report
        report.pid = pid
        report.record(Stage.PROCESS_ALIVE, True, f"pid {pid}")
        return report

    def _validate_apk(self, apk: Path, expected_package: str, device_sdk: int | None) -> Failure | None:
        if not apk.is_file():
            return Failure.APK_MISSING
        if apk.stat().st_size == 0:
            return Failure.APK_EMPTY
        package, min_sdk = self.read_apk_metadata(apk)
        if package is None:
            return Failure.APK_INVALID
        if package != expected_package:
            return Failure.PACKAGE_MISMATCH
        if min_sdk is None:
            # No metadata tool available: report honestly rather than assume compatibility.
            return Failure.APK_INVALID
        if device_sdk is not None and device_sdk < min_sdk:
            return Failure.SDK_INCOMPATIBLE
        return None

    def read_apk_metadata(self, apk: Path) -> tuple[str | None, int | None]:
        """Package name and minSdkVersion from the APK, or (None, None) when unreadable.

        Returns None rather than a placeholder: fabricated metadata is how an invalid artifact
        gets reported as valid.
        """

        for tool in ("aapt", "aapt2"):
            exe = self._find_build_tool(tool)
            if exe is None:
                continue
            if tool == "aapt":
                proc = self._run([str(exe), "dump", "--badging", str(apk)], timeout=60.0)
                text = proc.stdout or ""
                package = re.search(r"package: name='([^']+)' versionCode='(\d+)'", text)
                sdk = re.search(r"sdkVersion:'(\d+)'", text)
                if package:
                    return package.group(1), int(sdk.group(1)) if sdk else None
            else:
                proc = self._run([str(exe), "dump", "badging", str(apk)], timeout=60.0)
                text = proc.stdout or ""
                package = re.search(r"package name='([^']+)'", text)
                sdk = re.search(r"sdkVersion='(\d+)'", text)
                if package:
                    return package.group(1), int(sdk.group(1)) if sdk else None
        return None, None

    def _find_build_tool(self, name: str) -> Path | None:
        import os

        roots: list[str] = []
        for var in ("ANDROID_BUILD_TOOLS", "ANDROID_HOME", "ANDROID_SDK_ROOT"):
            value = os.environ.get(var)
            if value:
                roots.append(value)
        candidates: list[Path] = []
        for root in roots:
            base = Path(root)
            build_tools = base / "build-tools"
            if build_tools.is_dir():
                candidates.extend(sorted(build_tools.glob(f"*/{name}.exe"), reverse=True))
                candidates.extend(sorted(build_tools.glob(f"*/{name}"), reverse=True))
            candidates.append(base / name)
        candidates.append(Path(name))
        for candidate in candidates:
            if candidate.is_file():
                return candidate
        return None

    def resolve_launcher_activity(self, serial: str, package: str) -> str | None:
        proc = self._run(
            self._adb_argv(serial, "shell", "cmd", "package", "resolve-activity", "-c",
                           "android.intent.category.LAUNCHER", "--brief", package),
            timeout=45.0,
        )
        for line in reversed((proc.stdout or "").splitlines()):
            line = line.strip()
            if line.startswith(package + "/") and " " not in line:
                return line
        return None

    def process_id(self, serial: str, package: str) -> int | None:
        proc = self._run(self._adb_argv(serial, "shell", "pidof", package), timeout=30.0)
        digits = (proc.stdout or "").strip().split()
        return int(digits[0]) if digits and digits[0].isdigit() else None
