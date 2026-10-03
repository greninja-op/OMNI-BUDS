"""OmniBuds device bridge - the host-side half of a local-first Android device harness.

Scope of this package
---------------------
Everything here runs on the workstation. It deploys an APK that another process built, captures
the screen, extracts the live UI hierarchy and speaks a small JSON-envelope WebSocket protocol on
the loopback interface. This package never invokes a build tool and never touches a device by
itself: it constructs and executes ADB argv lists, and the person operating the phone decides
when they run. It contains no OmniBuds product logic and no Bluetooth logic: a harness artifact
must never be mistaken for evidence about earbuds.

Safety boundary (binding, from `docs/security/device-access-policy.md`)
----------------------------------------------------------------------
1. The device is a personal phone, not a lab instrument. Every operation is scoped to the
   harness package `com.omnibuds.tools.shell` and nothing else.
2. Screen capture and UI dumps may only happen while that package is foreground. The gate is
   implemented in `bridge.capture.ForegroundGate` and is consulted before every capture and
   every input action; it fails closed.
3. Permissions are granted one at a time by an explicit `grant()` call. There is no `-g`
   blanket grant anywhere in this package.
4. Every socket binds `127.0.0.1`. No cloud, no telemetry, no outbound calls.
5. Frames live in bounded in-memory buffers only; nothing is written to disk by this package.
6. No error is converted into a silent default return value (see `bridge.errors`).

Harness identity constants live at package level because three modules need exactly one copy
of them: a resource id that drifts from `tools/companion-shell/src/main/res/values/ids.xml`
silently turns a targeted tap into a blind one.
"""

from __future__ import annotations

#: Application id of the inspection target, from
#: `tools/companion-shell/build.gradle.kts` (`applicationId`).
HARNESS_PACKAGE = "com.omnibuds.tools.shell"

#: Activity relative to the package, from `tools/companion-shell/src/main/AndroidManifest.xml`.
#: The leading dot is the Android shorthand for "same package".
HARNESS_ACTIVITY = ".ShellActivity"

#: The stable resource ids the harness declares in `res/values/ids.xml`. These names are the
#: contract between the two halves of the harness; renaming one breaks the verification suite.
HARNESS_RESOURCE_IDS: tuple[str, ...] = (
    "bridge_title",
    "bridge_status",
    "bridge_probe_button",
    "bridge_counter_value",
    "bridge_toggle",
    "bridge_input_field",
)

#: Content descriptions the harness sets on those views, from `ShellActivity.kt`.
HARNESS_CONTENT_DESCRIPTIONS: tuple[str, ...] = (
    "bridge-title",
    "bridge-status",
    "bridge-probe-button",
    "bridge-counter",
    "bridge-toggle",
    "bridge-input",
)

#: Text emitted by the harness counter view: `COUNTER_PREFIX` in `ShellActivity.kt` followed by
#: the probe count. Exposed here so the verification suite asserts against the same literal the
#: app uses instead of re-inventing one.
HARNESS_COUNTER_PREFIX = "probes: "

#: `:platform:android` `minSdk` (research doc section 1). `preflight()` reports the device SDK;
#: whether that SDK is in range is a derived statement, never a default.
MIN_SUPPORTED_SDK = 26

__all__ = [
    "HARNESS_PACKAGE",
    "HARNESS_ACTIVITY",
    "HARNESS_RESOURCE_IDS",
    "HARNESS_CONTENT_DESCRIPTIONS",
    "HARNESS_COUNTER_PREFIX",
    "MIN_SUPPORTED_SDK",
    "harness_resource_id",
]


def harness_resource_id(short_name: str) -> str:
    """Return the fully qualified ``resource-id`` as a ``uiautomator`` dump spells it.

    A dump reports ``com.omnibuds.tools.shell:id/bridge_probe_button``. Callers must match that
    whole string, because a bare ``bridge_probe_button`` query would also match a foreign package
    that happened to pick the same local name.

    Raises:
        ValueError: ``short_name`` is not one of `HARNESS_RESOURCE_IDS`. An unknown name is a
            defect in the caller, not a query that returns nothing; returning the string anyway
            would let a typo degrade into "element not found".
    """
    if short_name not in HARNESS_RESOURCE_IDS:
        raise ValueError(
            "unknown harness resource id: "
            + repr(short_name)
            + "; permitted set is "
            + ", ".join(sorted(HARNESS_RESOURCE_IDS))
        )
    return HARNESS_PACKAGE + ":id/" + short_name
