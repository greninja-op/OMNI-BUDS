#!/usr/bin/env python3
"""Generate the expanded verified-device catalog from research JSON.

Only mainstream consumer audio companies are included. Records must have a
known buds/case battery contract and high/medium confidence. Pilot models are
excluded because they are curated by hand in VerifiedDeviceCatalog.kt.
"""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
COMPANIES = ROOT / "research" / "companies"
OUT = (
    ROOT
    / "platform"
    / "android"
    / "src"
    / "main"
    / "kotlin"
    / "com"
    / "omnibuds"
    / "android"
    / "hardware"
    / "capability"
    / "GeneratedVerifiedDevices.kt"
)

SCOPE = {
    "apple", "beats", "samsung", "google", "sony", "bose", "jbl", "sennheiser",
    "nothing", "cmf", "oneplus", "realme", "oppo", "xiaomi", "redmi", "poco",
    "huawei", "honor", "vivo", "iqoo", "anker-soundcore", "edifier", "1more",
    "soundpeats", "qcy", "baseus", "ugreen", "earfun", "tribit", "boat",
    "noise", "boult", "ptron", "mivi", "truke", "wings", "fire-boltt",
    "zebronics", "audio-technica", "beyerdynamic", "akg", "philips",
    "panasonic", "lg", "jabra", "skullcandy", "marshall", "bowers-wilkins",
    "bang-olufsen", "master-dynamic", "technics", "shokz", "oladance",
    "suunto", "nank", "sanag", "amazon",
}

PILOT_SLUGS = {
    "cmf-buds", "realme-buds-t300", "galaxy-buds3-pro", "sony-wf-1000xm5",
    "bose-quietcomfort-ultra-earbuds", "airpods-pro-2-usb-c", "jbl-live-beam-3",
    "sennheiser-momentum-true-wireless-4", "nothing-ear-a",
    "oneplus-buds-pro-3", "xiaomi-buds-5-pro", "pixel-buds-pro-2",
    "galaxy-buds3", "airpods-4", "airpods-4-anc", "sony-wh-1000xm5",
    "bose-quietcomfort-ultra-headphones", "jbl-tour-pro-3",
    "sennheiser-accentum-plus-wireless", "soundcore-liberty-5-pro",
    "edifier-neobuds-pro-2",
}

BUDS = {"split": "SPLIT", "combined": "COMBINED", "unknown": "UNKNOWN"}
CASE = {"separate": "SEPARATE", "hidden": "HIDDEN", "unknown": "UNKNOWN"}
VIS = {
    "always": "ALWAYS",
    "only-when-docked": "ONLY_WHEN_DOCKED",
    "only-when-case-open": "ONLY_WHEN_CASE_OPEN",
    "only-when-charging": "ONLY_WHEN_CHARGING",
    "unknown": "UNKNOWN",
}
OUT_CASE = {
    "live": "LIVE",
    "last-known": "LAST_KNOWN",
    "hidden": "HIDDEN",
    "unknown": "UNKNOWN",
}


def kstr(value: object) -> str:
    text = "" if value is None else str(value)
    text = text.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$").replace("\n", "\\n")
    return f'"{text}"'


def main() -> None:
    seen: set[str] = set()
    records: list[dict] = []

    for path in sorted(COMPANIES.glob("*.json")):
        if path.stem not in SCOPE:
            continue
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except Exception:
            continue
        if not isinstance(data, list):
            continue
        for record in data:
            slug = str(record.get("slug") or "").strip()
            if not slug or slug in seen or slug in PILOT_SLUGS:
                continue
            battery = record.get("battery") or {}
            buds = str(battery.get("budsDisplay") or "unknown")
            case = str(battery.get("caseDisplay") or "unknown")
            confidence = str(record.get("confidence") or "low")
            if buds not in ("split", "combined") or case not in ("separate", "hidden"):
                continue
            if confidence not in ("high", "medium"):
                continue
            seen.add(slug)
            records.append(record)

    lines = [
        "// GENERATED FILE — do not edit by hand.",
        "// Source: research/companies/*.json via research/tools/generate_catalog.py",
        "package com.omnibuds.android.hardware.capability",
        "",
        "/** Expanded verified battery/identity catalog for mainstream models. */",
        "object GeneratedVerifiedDevices {",
        "    val profiles: List<VerifiedDeviceProfile> = listOf(",
    ]

    for record in records:
        battery = record.get("battery") or {}
        app = (record.get("officialApp") or {}).get("name") or "Unknown app"
        model = record.get("model") or record.get("slug") or ""
        aliases = [model]
        feature_ids = [
            str(feature.get("id"))
            for feature in (record.get("features") or [])
            if feature.get("id")
        ]
        lines += [
            "        VerifiedDeviceProfile(",
            f"            slug = {kstr(record.get('slug'))},",
            f"            manufacturer = {kstr(record.get('company'))},",
            f"            model = {kstr(model)},",
            f"            officialAppName = {kstr(app)},",
            f"            aliases = listOf({', '.join(kstr(a) for a in aliases)}),",
            f"            featureIds = setOf({', '.join(kstr(f) for f in feature_ids)}),",
            "            batteryContract = BatteryDisplayContract(",
            f"                budsDisplay = BatteryBudsDisplay.{BUDS[buds]},",
            f"                caseDisplay = BatteryCaseDisplay.{CASE[case]},",
            f"                caseVisibility = BatteryCaseVisibility.{VIS.get(str(battery.get('caseVisibility') or 'unknown'), 'UNKNOWN')},",
            f"                caseWhileBudsOut = BatteryCaseWhileBudsOut.{OUT_CASE.get(str(battery.get('caseWhileBudsOut') or 'unknown'), 'UNKNOWN')},",
            "            ),",
            "        ),",
        ]

    lines += ["    )", "}", ""]
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"generated {len(records)} profiles -> {OUT}")


if __name__ == "__main__":
    main()
