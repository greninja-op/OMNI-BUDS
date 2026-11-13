#!/usr/bin/env python3
"""Normalize battery case-hour semantics across the research dataset.

`caseHours` historically mixed two meanings. This pass adds an explicit
`hoursBasis` plus `caseOnlyHours` / `totalWithCaseHours` while keeping the
original value for traceability.
"""

from __future__ import annotations

import json
import re
from pathlib import Path

COMPANIES = Path(__file__).resolve().parents[1] / "companies"

TOTAL_PATTERNS = (
    re.compile(r"\btotal\b.*\bcase\b", re.IGNORECASE),
    re.compile(r"\bwith (the )?case\b", re.IGNORECASE),
    re.compile(r"\bcase\b.*\btotal\b", re.IGNORECASE),
)
CASE_ONLY_PATTERNS = (
    re.compile(r"\bcase[- ]only\b", re.IGNORECASE),
    re.compile(r"\bcase (battery )?capacity\b", re.IGNORECASE),
    re.compile(r"\bcase battery\b", re.IGNORECASE),
)


def classify(notes: str, case_hours: object) -> str:
    if case_hours is None:
        return "unknown"
    text = notes or ""
    if any(pattern.search(text) for pattern in TOTAL_PATTERNS):
        return "total-with-case"
    if any(pattern.search(text) for pattern in CASE_ONLY_PATTERNS):
        return "case-only"
    return "unknown"


def main() -> None:
    counts = {"case-only": 0, "total-with-case": 0, "unknown": 0}
    files = 0
    records = 0

    for path in sorted(COMPANIES.glob("*.json")):
        try:
            data = json.loads(path.read_text(encoding="utf-8-sig"))
        except Exception:
            continue
        if not isinstance(data, list):
            continue
        changed = False
        for record in data:
            battery = record.get("battery")
            if not isinstance(battery, dict):
                continue
            records += 1
            case_hours = battery.get("caseHours")
            basis = classify(str(battery.get("notes") or ""), case_hours)
            counts[basis] += 1
            battery["hoursBasis"] = basis
            if basis == "case-only":
                battery["caseOnlyHours"] = case_hours
                battery["totalWithCaseHours"] = None
            elif basis == "total-with-case":
                battery["caseOnlyHours"] = None
                battery["totalWithCaseHours"] = case_hours
            else:
                battery["caseOnlyHours"] = None
                battery["totalWithCaseHours"] = None
            changed = True
        if changed:
            path.write_text(
                json.dumps(data, indent=2, ensure_ascii=False) + "\n",
                encoding="utf-8",
            )
            files += 1

    print(f"normalized {records} records in {files} files")
    print(counts)


if __name__ == "__main__":
    main()
