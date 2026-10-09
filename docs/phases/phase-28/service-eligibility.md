# Phase 28 — Service Eligibility

## Decision

**No foreground service is created in Phase 28.**

## Analysis

| Criterion | Assessment |
|---|---|
| User-visible continuous task | None identified; observation is event-driven |
| Why ordinary process/callbacks insufficient | N/A — no continuous task exists |
| Foreground-service type | None applicable |
| Required permissions | None requested |
| Battery/privacy impact | A permanent service would cost battery for no product benefit |

## Conclusion

An always-on service solely to keep the process alive, refresh the
widget, update notifications, or preserve a Bluetooth session has no
supported justification and is explicitly forbidden by the phase scope.
If a future phase identifies a legitimate user-visible continuous task,
it must document the full eligibility table above before adding one.
