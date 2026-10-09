# Phase 20 — Security Model

## Trust boundaries

1. **Imported files are untrusted.** Size-checked, decode-bounded,
   schema-validated before any parsing.
2. **Imported metadata is not identity.** File metadata never becomes
   device identity.
3. **Parsed data is not commands.** No path from parser output to
   device transmission exists.

## Prohibited

- Script/command execution from imported files.
- Network access.
- Raw Bluetooth writes.
- Trace auto-execution or replay.
- Authentication/pairing bypass.
- Hidden APIs.

## Sensitive data

- Redaction types: addresses, identifiers, credentials, personal data,
  proprietary sections.
- Structural changes recorded; sanitized ≠ byte-identical.
- Diagnostics must not leak payloads; bounded hex only.
- Trace isolation per device identity.

## Resource safety

- Bounded inputs at every layer (see `LabLimits`).
- No unbounded buffering, pathological regex, or uncontrolled recursion.
- Cancellation supported for long analysis.
- Failed imports never corrupt existing traces.

## Verification

`LabSafetyTest` (5 tests) + `LabSecurityTest` scope guards enforce these
boundaries in CI.
