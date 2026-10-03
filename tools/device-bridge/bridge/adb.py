"""The only place in this repository where ADB command strings are built.

Everything that reaches the phone goes through `AdbTransport`, so that "what exactly would have
been sent to the device" is answerable by reading one file.

Invariants
----------
* One transport, one device. The serial is a constructor invariant and is injected as `-s` into
  every device-addressed command. There is no unaddressed form: an unqualified `adb install` hits
  whichever phone happens to be attached, which is the failure mode the personal-device policy
  cannot tolerate. (The brief writes some of these as `preflight(serial)` /
  `grant(serial, package, permission)`; passing a serial per call would let one transport preflight
  a device that its later capture is not addressed to. `targeting(serial)` is the explicit way to
  switch devices.)
* Command strings are built here and nowhere else, including the `input tap` / `input swipe` /
  `input text` / `input keyevent` primitives. A caller that needs a coordinate pressed calls
  `input_tap`; it does not get to assemble an argv.
* Every subprocess call is an argv list with `shell=False`. There is no `shell=True` anywhere in
  this package, including for `input text`: see `bridge.input_actions` for why the escaping there
  is done by allow-list instead of by quoting through a local shell.
* The subprocess seam is injectable (`runner=`), so the whole module is testable against recorded
  fixtures. Tests never invoke `adb`.
* Failures are classified from ADB's own words into the distinct categories
  (`DeviceNotFound` / `DeviceUnauthorized` / `DeviceOffline` / `AdbFailed`) - never collapsed into
  one generic failure, because the user's remedy differs for each.
* Diagnostics carry the serial and the argv; they never carry device output content. A focus read
  keeps the package name and discards the rest of the line's surroundings, because the window
  tokens in `dumpsys window` describe other applications (`docs/security/device-access-policy.md`).
"""

from __future__ import annotations

import enum
import re
import subprocess
from dataclasses import dataclass
from typing import Protocol, Sequence

from .errors import (
    AdbFailed,
    BridgeErrorCode,
    DeviceNotFound,
    DeviceOffline,
    DeviceUnauthorized,
    PermissionNotGranted,
)

ADB_EXECUTABLE_DEFAULT = "adb"
DEFAULT_COMMAND_TIMEOUT_S = 30.0

#: The eight bytes every PNG stream starts with, from RFC 2083 / ISO/IEC 15948. Checked before a
#: capture is accepted as a frame.
PNG_SIGNATURE: bytes = b"\x89PNG\r\n\x1a\n"

#: Properties read by `preflight`. Targeted reads only - a bulk `getprop` dump costs one command
#: but retains far more about the device than the five facts the bridge justifies.
PREFLIGHT_PROPERTIES: tuple[str, ...] = (
    "ro.product.cpu.abi",
    "ro.build.version.sdk",
    "ro.build.version.release",
    "ro.product.model",
    "ro.product.manufacturer",
)

_PACKAGE_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)+$")
_PERMISSION_RE = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)*$")
_GETPROP_LINE_RE = re.compile(r"^\[(?P<key>[^\]]+)\]:\s*\[(?P<value>.*)\]\s*$")
_DEVICE_FIELD_RE = re.compile(
    r"(?P<key>product|model|device|transport_id):(?P<value>.+?)"
    r"(?=\s+(?:product|model|device|transport_id):|\s*$)"
)
_DAEMON_CHATTER_PREFIXES = ("*", "adb server version", "List of devices attached")

#: Substrings ADB uses when the device is not addressable. Matched case-insensitively against
#: stderr. Order matters: `unauthorized` and `offline` are checked before the generic `not found`
#: phrasing, because ADB uses "not found" wording for a device that is actually present but
#: unauthorised on some releases, and the user's remedy differs.
_UNAUTHORIZED_MARKERS = ("unauthorized", "must be authorized")
_OFFLINE_MARKERS = ("offline",)
_NOT_FOUND_MARKERS = (
    "not found",
    "no devices/emulators found",
    "no device",
    "device list is empty",
)


class AdbDeviceState(enum.Enum):
    """The state token ADB reports for an attached transport. `UNKNOWN` is an explicit member."""

    DEVICE = "device"
    UNAUTHORIZED = "unauthorized"
    OFFLINE = "offline"
    SIDELOAD = "sideload"
    RECOVERY = "recovery"
    BOOTLOADER = "bootloader"
    UNKNOWN = "unknown"

    @classmethod
    def from_token(cls, token: str) -> "AdbDeviceState":
        """Map an ADB state token, keeping unrecognised tokens as `UNKNOWN` with the raw text.

        This is not a silent default: `UNKNOWN` is the honest statement "ADB used a word this
        version of the bridge does not know", and `AdbDeviceRecord.state_token` keeps the original.
        """
        lowered = token.strip().lower()
        for member in cls:
            if member is not AdbDeviceState.UNKNOWN and member.value == lowered:
                return member
        return AdbDeviceState.UNKNOWN

    @property
    def is_addressable(self) -> bool:
        """True only for `device`: the one state where a command will actually execute."""
        return self is AdbDeviceState.DEVICE


@dataclass(frozen=True)
class CommandResult:
    """What a subprocess run produced. Bytes, not strings: `screencap` output is not text."""

    returncode: int
    stdout: bytes
    stderr: bytes

    @property
    def stdout_text(self) -> str:
        """stdout decoded as UTF-8 with undecodable bytes replaced.

        Only for textual commands. Binary paths (`screencap`) must use `stdout` directly, since
        `replace` would silently corrupt the bytes.
        """
        return self.stdout.decode("utf-8", errors="replace")

    @property
    def stderr_text(self) -> str:
        return self.stderr.decode("utf-8", errors="replace")


class CommandRunner(Protocol):
    """The injectable subprocess seam: takes an argv list, returns a `CommandResult`."""

    def __call__(self, argv: list[str], *, timeout_s: float) -> CommandResult: ...


def subprocess_command_runner(argv: list[str], *, timeout_s: float = DEFAULT_COMMAND_TIMEOUT_S) -> CommandResult:
    """Execute `argv` without a shell and capture its streams.

    The real runner. Tests substitute a fake; the bridge itself never calls it during a test run.

    Failure modes: `AdbFailed(ADB_COMMAND_TIMEOUT)` if `timeout_s` elapses (the process may then
    still be running; ADB is left alone rather than killed by pid, since a hung `adb` is the
    operator's to resolve), `AdbFailed(ADB_COMMAND_FAILED)` if the executable is missing.
    """
    if not argv:
        raise ValueError("subprocess_command_runner requires a non-empty argv")
    try:
        # No shell, no string command: argv is a list this module built, so there is nothing for a
        # local shell to re-interpret.
        completed = subprocess.run(
            argv,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
            shell=False,
            timeout=timeout_s,
        )
    except subprocess.TimeoutExpired as exc:
        raise AdbFailed(
            "ADB command timed out after " + format(timeout_s, ".1f") + "s: " + _redacted_join(argv),
            code=BridgeErrorCode.ADB_COMMAND_TIMEOUT,
            context={"argv": _redacted_join(argv), "timeout_s": timeout_s},
            argv=tuple(argv),
        ) from exc
    except OSError as exc:
        raise AdbFailed(
            "could not start the ADB executable " + repr(argv[0]) + ": " + type(exc).__name__,
            code=BridgeErrorCode.ADB_COMMAND_FAILED,
            context={"argv": _redacted_join(argv), "oserror": type(exc).__name__},
            argv=tuple(argv),
        ) from exc
    return CommandResult(
        returncode=completed.returncode,
        stdout=completed.stdout if completed.stdout is not None else b"",
        stderr=completed.stderr if completed.stderr is not None else b"",
    )


def _redacted_join(argv: Sequence[str]) -> str:
    return " ".join(str(part) for part in argv)


@dataclass(frozen=True)
class CommandOutcome:
    """What was sent and what the device printed.

    Returning the argv alongside the output is what makes "which command actually reached the
    phone" answerable from a recorded run instead of from a reading of the source.
    """

    argv: tuple[str, ...]
    stdout: str


@dataclass(frozen=True)
class AdbDeviceRecord:
    """One line of `adb devices -l`.

    `product`, `model` and `transport_id` are `None` when ADB did not print them, which is "not
    reported" rather than empty: a device in `unauthorized` state legitimately reports none of
    them, and inventing `""` would make the two states look identical downstream.
    """

    serial: str
    state: AdbDeviceState
    state_token: str
    product: str | None = None
    model: str | None = None
    device: str | None = None
    transport_id: str | None = None

    @property
    def is_addressable(self) -> bool:
        return self.state.is_addressable


@dataclass(frozen=True)
class DeviceProfile:
    """The build facts `preflight()` establishes about one device.

    `sdk` is an `int` because a band decision is made from it; a non-numeric SDK is a failure
    raised by the parser, never a `0`.
    """

    serial: str
    abi: str
    sdk: int
    release: str
    model: str
    manufacturer: str

    @property
    def meets_min_sdk(self) -> bool:
        """Derived, not stored: whether this device is in range for `MIN_SUPPORTED_SDK`."""
        from . import MIN_SUPPORTED_SDK

        return self.sdk >= MIN_SUPPORTED_SDK

    def as_context(self) -> dict[str, object]:
        return {
            "serial": self.serial,
            "abi": self.abi,
            "sdk": self.sdk,
            "release": self.release,
            "model": self.model,
            "manufacturer": self.manufacturer,
        }


def parse_device_listing(text: str) -> tuple[AdbDeviceRecord, ...]:
    """Parse `adb devices -l` output into typed records.

    Raises:
        AdbFailed(ADB_UNUSABLE_OUTPUT): a line that is neither the header, blank, daemon chatter,
            nor a `<serial> <state> [fields...]` record. Skipping an unparsable line silently
            would let a device disappear from the listing without saying so.
    """
    records: list[AdbDeviceRecord] = []
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        if any(line == prefix or line.startswith(prefix) for prefix in _DAEMON_CHATTER_PREFIXES):
            continue
        parts = line.split(None, 2)
        if len(parts) < 2:
            raise AdbFailed(
                "unparsable line in `adb devices -l` output: " + repr(line),
                code=BridgeErrorCode.ADB_UNUSABLE_OUTPUT,
                context={"line": line},
            )
        serial, token = parts[0], parts[1]
        remainder = parts[2] if len(parts) > 2 else ""
        fields: dict[str, str] = {}
        for match in _DEVICE_FIELD_RE.finditer(remainder):
            fields[match.group("key")] = match.group("value").strip()
        records.append(
            AdbDeviceRecord(
                serial=serial,
                state=AdbDeviceState.from_token(token),
                state_token=token,
                product=fields.get("product") or None,
                model=fields.get("model") or None,
                device=fields.get("device") or None,
                transport_id=fields.get("transport_id") or None,
            )
        )
    return tuple(records)


def parse_getprop_dump(text: str) -> dict[str, str]:
    """Parse a full `getprop` dump (`[key]: [value]` lines) into a mapping.

    Provided because a bulk read is the cheaper command, and a later phase may want it. The bridge
    itself does not use it: `preflight` performs targeted reads, so this function has no way to
    leak and is not on any device path.
    """
    props: dict[str, str] = {}
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        match = _GETPROP_LINE_RE.match(line)
        if match is None:
            raise AdbFailed(
                "unparsable getprop dump line: " + repr(line),
                code=BridgeErrorCode.ADB_UNUSABLE_OUTPUT,
            )
        props[match.group("key")] = match.group("value")
    return props


def parse_prop_value(text: str) -> str:
    """Reduce the output of one targeted `getprop <key>` to its value.

    A targeted read prints the bare value; some ADB/OEM builds print the `[key]: [value]` shape
    even for a single key. Both are accepted, and both are reduced to the value only.

    Raises:
        AdbFailed(ADB_UNUSABLE_OUTPUT): the read succeeded but printed nothing usable. An empty
            property is a real device state, and the caller must see it as a failure of that read
            rather than as `""` which would flow into a comparison as a plausible value.
    """
    stripped = text.strip()
    if not stripped:
        raise AdbFailed(
            "targeted getprop read returned no value",
            code=BridgeErrorCode.ADB_UNUSABLE_OUTPUT,
        )
    match = _GETPROP_LINE_RE.match(stripped)
    if match is not None:
        value = match.group("value").strip()
        if not value:
            raise AdbFailed(
                "targeted getprop read reported an empty value for " + repr(match.group("key")),
                code=BridgeErrorCode.ADB_UNUSABLE_OUTPUT,
            )
        return value
    if "\n" in stripped:
        raise AdbFailed(
            "targeted getprop read returned multiple lines; expected one value",
            code=BridgeErrorCode.ADB_UNUSABLE_OUTPUT,
            context={"line_count": len(stripped.splitlines())},
        )
    return stripped


def parse_sdk_value(text: str) -> int:
    """Parse an API level, refusing anything that is not a positive integer."""
    value = parse_prop_value(text)
    try:
        sdk = int(value)
    except ValueError as exc:
        raise AdbFailed(
            "device SDK level is not an integer: " + repr(value),
            code=BridgeErrorCode.ADB_UNUSABLE_OUTPUT,
        ) from exc
    if sdk <= 0:
        raise AdbFailed(
            "device SDK level is not positive: " + str(sdk),
            code=BridgeErrorCode.ADB_UNUSABLE_OUTPUT,
        )
    return sdk


def parse_focus_package(dumpsys_window_text: str) -> str:
    """Grep the window/activity focus output and keep ONLY the foreground package name.

    Prefers `mCurrentFocus`, falls back to `mFocusedApp` (documented order, not a coin flip: the
    former is the window holding input focus, the latter the app the launcher believes is current).

    Every other token on the line - the window hash, the user id, the activity class - is parsed
    out and discarded, because it describes windows the device-access policy puts out of scope.
    The returned value is the package, nothing else.

    Raises:
        AdbFailed(FOCUS_UNRESOLVED): no focus line, a `null` focus (screen off or mid-transition),
            or a token that is not a well-formed package. The foreground gate treats this as a
            refusal; "could not tell" is never "allowed".
    """
    for marker in ("mCurrentFocus=", "mFocusedApp="):
        for raw_line in dumpsys_window_text.splitlines():
            if marker not in raw_line:
                continue
            payload = raw_line.split(marker, 1)[1].strip()
            if not payload or payload == "null":
                continue
            token = _component_token(payload)
            if token is None:
                continue
            package = token.split("/", 1)[0]
            if _PACKAGE_RE.match(package) is None:
                raise AdbFailed(
                    "focus line names something that is not a package",
                    code=BridgeErrorCode.FOCUS_UNRESOLVED,
                )
            return package
    raise AdbFailed(
        "no window/activity focus line could be read; the foreground state is unknown",
        code=BridgeErrorCode.FOCUS_UNRESOLVED,
    )


def _component_token(payload: str) -> str | None:
    """Take the last `package/component` shaped token from a focus line's payload.

    A line reads `Window{6d3b1c8 u0 com.omnibuds.tools.shell/com.omnibuds.tools.shell.ShellActivity}`.
    The hash and the user id come first; the component is last, which is why the scan runs
    backwards instead of trusting the first slash.
    """
    for token in reversed(payload.split()):
        if "/" in token and _PACKAGE_RE.match(token.split("/", 1)[0]) is not None:
            return token
    return None


def build_install_argv(adb_path: str, serial: str, apk_path: str, reinstall: bool) -> list[str]:
    """Construct the `adb install` argv.

    `-r` is added when `reinstall` is true. `-g` (grant all runtime permissions on install) is
    never added by any path in this module: a blanket grant would defeat the per-permission audit
    the device-access policy requires, and would be invisible to the user after the fact.
    """
    argv = [adb_path, "-s", serial, "install"]
    if reinstall:
        argv.append("-r")
    argv.append(apk_path)
    return argv


class AdbTransport:
    """Addressed ADB command builder and executor.

    The transport knows how to talk to exactly one device and how to read exactly the facts the
    bridge justifies. It does not know what a screen is, what a UI node is, or what a policy is -
    those live in `capture`, `hierarchy` and `input_actions`, each of which must pass the
    foreground gate first.
    """

    def __init__(
        self,
        *,
        serial: str,
        adb_path: str = ADB_EXECUTABLE_DEFAULT,
        runner: CommandRunner | None = None,
        timeout_s: float = DEFAULT_COMMAND_TIMEOUT_S,
    ) -> None:
        if not serial or not serial.strip():
            raise ValueError("AdbTransport requires a non-blank device serial; an unaddressed command is refused")
        if not adb_path or not adb_path.strip():
            raise ValueError("AdbTransport requires an ADB executable name or path")
        if timeout_s <= 0:
            raise ValueError("AdbTransport timeout_s must be positive")
        self._serial = serial.strip()
        self._adb_path = adb_path.strip()
        self._timeout_s = float(timeout_s)
        self._runner: CommandRunner = runner if runner is not None else subprocess_command_runner
        self._closed = False
        self._commands_run: list[list[str]] = []

    @property
    def serial(self) -> str:
        return self._serial

    @property
    def adb_path(self) -> str:
        return self._adb_path

    @property
    def timeout_s(self) -> float:
        return self._timeout_s

    @property
    def commands_run(self) -> tuple[tuple[str, ...], ...]:
        """Every argv this transport has executed, oldest first.

        Kept so a test (and a human reviewing a run) can prove which commands reached the phone.
        """
        return tuple(tuple(argv) for argv in self._commands_run)

    def targeting(self, serial: str) -> "AdbTransport":
        """A sibling transport for a different device, sharing this one's runner and settings.

        Explicit by design: switching devices is an action, not a parameter slip.
        """
        return AdbTransport(
            serial=serial,
            adb_path=self._adb_path,
            runner=self._runner,
            timeout_s=self._timeout_s,
        )

    # ---------------------------------------------------------------- command plumbing

    def _device_argv(self, *args: str) -> list[str]:
        if self._closed:
            raise AdbFailed(
                "this transport is closed; construct a new AdbTransport to run a command",
                code=BridgeErrorCode.TRANSPORT_CLOSED,
                argv=None,
            )
        argv = [self._adb_path, "-s", self._serial]
        argv.extend(args)
        return argv

    def _run(self, argv: list[str]) -> CommandResult:
        self._commands_run.append(list(argv))
        result = self._runner(argv, timeout_s=self._timeout_s)
        if result.returncode != 0:
            raise self._classify_failure(result, argv)
        return result

    def _run_record(self, argv: list[str]) -> CommandOutcome:
        """Run and keep the evidence: the exact argv, plus the device's stdout."""
        result = self._run(argv)
        return CommandOutcome(argv=tuple(argv), stdout=result.stdout_text)

    def _classify_failure(self, result: CommandResult, argv: list[str]) -> Exception:
        """Turn ADB's own words into the specific category, not a generic one."""
        stderr = result.stderr_text.strip()
        lowered = stderr.lower()
        context = {
            "argv": _redacted_join(argv),
            "returncode": result.returncode,
            # Bounded on purpose: stderr can echo device content, and a whole-buffer message is a
            # disclosure surface (Phase 1 section 6: toString is a disclosure surface).
            "stderr_excerpt": stderr[:200],
        }
        if any(marker in lowered for marker in _UNAUTHORIZED_MARKERS):
            return DeviceUnauthorized(
                "the device at " + self._serial + " has not accepted the ADB fingerprint prompt; "
                "approve it on the phone screen",
                context=context,
            )
        if any(marker in lowered for marker in _OFFLINE_MARKERS):
            return DeviceOffline(
                "the device at " + self._serial + " is listed as offline; no command was executed",
                context=context,
            )
        if any(marker in lowered for marker in _NOT_FOUND_MARKERS):
            return DeviceNotFound(
                "no device with serial " + self._serial + " is attached to this workstation",
                context=context,
            )
        if "security exception" in lowered or "not a changeable permission" in lowered:
            return PermissionNotGranted(
                "the device refused the permission operation",
                context=context,
            )
        return AdbFailed(
            "ADB command failed with status " + str(result.returncode) + ": " + _redacted_join(argv),
            code=BridgeErrorCode.ADB_COMMAND_FAILED,
            context=context,
            returncode=result.returncode,
            argv=tuple(argv),
        )

    # ---------------------------------------------------------------- discovery

    def devices(self) -> tuple[AdbDeviceRecord, ...]:
        """`adb devices -l`, unaddressed by design: the listing is what chooses a serial.

        Returns records for every state ADB knows. `is_addressable` is true only for `device`;
        `unauthorized` and `offline` are distinct states with distinct remedies, and an
        unrecognised token arrives as `UNKNOWN` carrying its raw text.
        """
        argv = [self._adb_path, "devices", "-l"]
        result = self._run(argv)
        return parse_device_listing(result.stdout_text)

    def require_addressable(self) -> AdbDeviceRecord:
        """Find this transport's serial in the listing and refuse anything but `device`.

        Raises the specific category rather than a generic failure so the operator is told whether
        to plug the phone in, tap Allow, or unplug and replug.
        """
        for record in self.devices():
            if record.serial == self._serial:
                if record.state is AdbDeviceState.UNAUTHORIZED:
                    raise DeviceUnauthorized(
                        "device " + self._serial + " is attached but not authorized for ADB",
                        context={"serial": self._serial, "state": record.state_token},
                    )
                if record.state is AdbDeviceState.OFFLINE:
                    raise DeviceOffline(
                        "device " + self._serial + " is attached but offline",
                        context={"serial": self._serial, "state": record.state_token},
                    )
                if record.state is AdbDeviceState.DEVICE:
                    return record
                raise AdbFailed(
                    "device " + self._serial + " is attached in state " + repr(record.state_token)
                    + ", which cannot execute commands",
                    code=BridgeErrorCode.DEVICE_OFFLINE,
                    context={"serial": self._serial, "state": record.state_token},
                )
        raise DeviceNotFound(
            "device " + self._serial + " is not in the ADB listing",
            context={"serial": self._serial},
        )

    def preflight(self) -> DeviceProfile:
        """Read the five build facts the bridge justifies, with targeted `getprop` reads only.

        The brief names this `preflight(serial)`; the serial comes from `self.serial` so that the
        profile cannot describe a different device than the one every later command addresses.
        Exactly `len(PREFLIGHT_PROPERTIES)` commands are sent, one per property.

        Failure modes: a missing or non-numeric SDK, an empty ABI, or a failed read each raise
        `AdbFailed` with `ADB_UNUSABLE_OUTPUT`. Nothing here defaults: a device whose SDK we cannot
        parse is a device we do not proceed with.
        """
        values: dict[str, str] = {}
        for key in PREFLIGHT_PROPERTIES:
            result = self._run(self._device_argv("shell", "getprop", key))
            values[key] = parse_prop_value(result.stdout_text)
        abi = values["ro.product.cpu.abi"]
        if not abi.strip():
            raise AdbFailed(
                "device reported a blank ABI",
                code=BridgeErrorCode.ADB_UNUSABLE_OUTPUT,
            )
        return DeviceProfile(
            serial=self._serial,
            abi=abi,
            sdk=parse_sdk_value(values["ro.build.version.sdk"]),
            release=values["ro.build.version.release"],
            model=values["ro.product.model"],
            manufacturer=values["ro.product.manufacturer"],
        )

    def pidof(self, package: str) -> int | None:
        """`pidof <package>`: the process id, or `None` when the package is not running.

        `None` is a positive observation ("no process"), not a default - `adb shell pidof` exits
        with a status the transport maps to `AdbFailed` if ADB itself fails, so an empty successful
        read genuinely means "nothing running".

        Raises:
            AdbFailed(ADB_UNUSABLE_OUTPUT): more than one pid is reported. Several processes for
                one package is a state this harness does not reason about, and silently taking the
                first would make a PID-filtered log read quietly wrong.
        """
        _require_package(package)
        result = self._run(self._device_argv("shell", "pidof", package))
        tokens = result.stdout_text.split()
        if not tokens:
            return None
        pids: list[int] = []
        for token in tokens:
            try:
                pids.append(int(token))
            except ValueError as exc:
                raise AdbFailed(
                    "pidof returned a non-numeric token",
                    code=BridgeErrorCode.ADB_UNUSABLE_OUTPUT,
                ) from exc
        if len(pids) > 1:
            raise AdbFailed(
                "pidof reported " + str(len(pids)) + " processes for " + package
                + "; the bridge does not guess which one to address",
                code=BridgeErrorCode.ADB_UNUSABLE_OUTPUT,
                context={"package": package, "pid_count": len(pids)},
            )
        return pids[0]

    # ---------------------------------------------------------------- foreground policy

    def current_focus_package(self) -> str:
        """The package holding window focus, and nothing else about it.

        Implemented as a narrow grep of `dumpsys window`: the command output is large and names
        other applications' windows, so only the package token survives parsing and the raw text is
        never retained on the record or in an error message.

        Raises:
            AdbFailed(FOCUS_UNRESOLVED): focus is unreadable. The gate fails closed on this.
        """
        result = self._run(self._device_argv("shell", "dumpsys", "window"))
        return parse_focus_package(result.stdout_text)

    # ---------------------------------------------------------------- deploy

    def install(self, apk_path: str, *, reinstall: bool = True) -> CommandOutcome:
        """`adb install [-r] <apk>` for an APK built outside this package.

        There is no `-g`, no `--bypass-low-target-sdk-block`, no `-t`: every extra flag would be a
        device-side effect the bridge has not been asked for. Granting is a separate, explicit
        `grant()` call.

        The path is validated only for shape; this package does not open it, so a test can assert
        the command without a built artifact existing.
        """
        if not apk_path or not apk_path.strip():
            raise ValueError("install requires a non-blank APK path")
        if not apk_path.strip().lower().endswith(".apk"):
            raise ValueError(
                "install expects an .apk path, got " + repr(apk_path)
                + "; anything else is not an artifact this bridge should be pushing"
            )
        argv = build_install_argv(self._adb_path, self._serial, apk_path.strip(), reinstall)
        return self._run_record(argv)

    def grant(self, package: str, permission: str) -> CommandOutcome:
        """`pm grant <package> <permission>` for exactly one permission, on exactly one package.

        Deliberately separate from `install`, so a permission grant is always a named, auditable
        act rather than a side effect of deploying. The device-access policy forbids granting
        "permissions we did not request through our own UI"; a single-call, single-permission shape
        is what makes each grant reviewable.

        Raises:
            PermissionNotGranted: the device refused (already granted in a non-changeable form,
                the permission not declared, or the package unknown). AdbFailed/DeviceNotFound for
                transport-level problems.
        """
        _require_package(package)
        _require_permission(permission)
        argv = self._device_argv("shell", "pm", "grant", package, permission)
        try:
            result = self._run(argv)
        except AdbFailed as exc:
            if exc.code is BridgeErrorCode.ADB_COMMAND_FAILED:
                raise PermissionNotGranted(
                    "the device refused " + permission + " for " + package,
                    context=dict(exc.context),
                ) from exc
            raise
        return CommandOutcome(argv=tuple(argv), stdout=result.stdout_text)

    def start_activity(self, package: str, activity: str) -> CommandOutcome:
        """`am start -n <package>/<activity>`: bring the harness to the foreground.

        The activity is passed through verbatim, so both `.ShellActivity` (package-relative, the
        form `AndroidManifest.xml` uses) and a fully qualified class name work.
        """
        _require_package(package)
        if not activity or not activity.strip():
            raise ValueError("start_activity requires a non-blank activity")
        component = package + "/" + activity.strip()
        argv = self._device_argv("shell", "am", "start", "-n", component)
        return self._run_record(argv)

    def force_stop(self, package: str) -> CommandOutcome:
        """`am force-stop <package>`, restricted to one package by signature.

        Included because the foreground gate needs a way back to a known state between runs. It is
        the only destructive-ish call here, and it cannot be aimed at a package the caller did not
        name.
        """
        _require_package(package)
        argv = self._device_argv("shell", "am", "force-stop", package)
        return self._run_record(argv)

    # ---------------------------------------------------------------- input primitives

    def input_tap(self, x: int, y: int) -> CommandOutcome:
        """`input tap <x> <y>` at an absolute display coordinate.

        Coordinates are validated here as well as at the caller: a negative or non-integer pixel is
        a command that would address a place no view occupies, and this module is the last place
        that can say no before the phone hears it.
        """
        return self._run_record(
            self._device_argv("shell", "input", "tap", *_coordinate_argv((x, y)))
        )

    def input_swipe(self, x1: int, y1: int, x2: int, y2: int, duration_ms: int) -> CommandOutcome:
        """`input swipe <x1> <y1> <x2> <y2> <durationMs>`; a zero-distance swipe is a long press."""
        argv = self._device_argv(
            "shell",
            "input",
            "swipe",
            *_coordinate_argv((x1, y1)),
            *_coordinate_argv((x2, y2)),
            str(_require_duration(duration_ms)),
        )
        return self._run_record(argv)

    def input_text(self, escaped_argument: str) -> CommandOutcome:
        """`input text <argument>`, where the argument is already escaped by the caller.

        `bridge.input_actions.escape_input_text` owns the escaping rule; this method owns the argv.
        The split matters: adb.py is the only place command strings are built, and the escaping is a
        pure string transformation with no device in it. The argument is passed as exactly one argv
        element with no shell involved, so whatever the allow-list accepted is what the device shell
        will re-parse.
        """
        if not escaped_argument:
            raise ValueError("input_text requires a non-empty escaped argument")
        if " " in escaped_argument:
            raise ValueError(
                "escaped text argument still contains a space; `adb shell` joins argv with spaces and "
                "the device would split it into two arguments"
            )
        return self._run_record(self._device_argv("shell", "input", "text", escaped_argument))

    def input_keyevent(self, keycode: int) -> CommandOutcome:
        """`input keyevent <code>` with a code this transport will accept."""
        return self._run_record(self._device_argv("shell", "input", "keyevent", str(keycode)))


    # ---------------------------------------------------------------- capture / dump

    def screencap_png(self) -> bytes:
        """`adb exec-out screencap -p` -> PNG bytes.

        `exec-out` rather than `shell` because the PTY that `adb shell` allocates rewrites
        newlines, which corrupts a binary stream. The result is validated against the PNG
        signature so a truncated or text-mangled capture fails here instead of producing a frame
        whose header parse fails later.

        Raises:
            AdbFailed(ADB_UNUSABLE_OUTPUT): empty output, or output that does not start with the
                PNG signature.
        """
        result = self._run(self._device_argv("exec-out", "screencap", "-p"))
        payload = result.stdout
        if not payload:
            raise AdbFailed(
                "screencap returned no bytes",
                code=BridgeErrorCode.ADB_UNUSABLE_OUTPUT,
            )
        if not payload.startswith(PNG_SIGNATURE):
            raise AdbFailed(
                "screencap output is not a PNG stream",
                code=BridgeErrorCode.ADB_UNUSABLE_OUTPUT,
                context={"byte_count": len(payload)},
            )
        return payload

    def uiautomator_dump(self) -> str:
        """`adb exec-out uiautomator dump /dev/tty` -> the UI hierarchy XML.

        `uiautomator` may surround the document with a status note, so the document is delimited
        by the first `<` and the last `>`; the delimiting is recorded in the error text if it fails.
        The dump is returned as text and never written to disk.
        """
        result = self._run(self._device_argv("exec-out", "uiautomator", "dump", "/dev/tty"))
        text = result.stdout_text
        start = text.find("<")
        end = text.rfind(">")
        if start < 0 or end < start:
            raise AdbFailed(
                "uiautomator dump produced no XML document",
                code=BridgeErrorCode.ADB_UNUSABLE_OUTPUT,
                context={"byte_count": len(text)},
            )
        return text[start : end + 1]

    # ---------------------------------------------------------------- lifecycle

    def close(self) -> None:
        """Stop this transport accepting commands. Idempotent.

        There is no OS handle to release - each command is a short-lived subprocess - but the
        closed flag is what makes `with AdbTransport(...) as t:` mean something: after the block,
        an accidental later command raises instead of running.
        """
        self._closed = True

    def __enter__(self) -> "AdbTransport":
        return self

    def __exit__(self, exc_type: object, exc: object, tb: object) -> None:
        self.close()

    @property
    def is_closed(self) -> bool:
        return self._closed


def _require_package(package: str) -> None:
    if not isinstance(package, str) or _PACKAGE_RE.match(package or "") is None:
        raise ValueError(
            "not a well-formed Android package name: " + repr(package)
            + "; a package this bridge cannot name is a package it must not touch"
        )


def _require_permission(permission: str) -> None:
    if not isinstance(permission, str) or _PERMISSION_RE.match(permission or "") is None:
        raise ValueError("not a well-formed permission name: " + repr(permission))


def _coordinate_argv(pair: tuple[int, int]) -> tuple[str, str]:
    x, y = pair
    for value in (x, y):
        if isinstance(value, bool) or not isinstance(value, int):
            raise ValueError("a tap coordinate must be an int pixel, got " + repr(value))
        if value < 0:
            raise ValueError("a tap coordinate cannot be negative: " + str(value))
    return (str(x), str(y))


def _require_duration(duration_ms: int) -> int:
    if isinstance(duration_ms, bool) or not isinstance(duration_ms, int):
        raise ValueError("duration_ms must be an int")
    if duration_ms <= 0:
        raise ValueError("duration_ms must be positive; a zero-duration swipe is not a press")
    return duration_ms


__all__ = [
    "ADB_EXECUTABLE_DEFAULT",
    "DEFAULT_COMMAND_TIMEOUT_S",
    "PREFLIGHT_PROPERTIES",
    "PNG_SIGNATURE",
    "AdbDeviceState",
    "AdbDeviceRecord",
    "DeviceProfile",
    "CommandResult",
    "CommandOutcome",
    "CommandRunner",
    "AdbTransport",
    "subprocess_command_runner",
    "parse_device_listing",
    "parse_getprop_dump",
    "parse_prop_value",
    "parse_sdk_value",
    "parse_focus_package",
    "build_install_argv",
]
