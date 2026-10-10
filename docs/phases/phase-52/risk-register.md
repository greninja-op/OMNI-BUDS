# Phase 52 — Risk Register: Production QA & Release Readiness

## 1. Risk Assessment Matrix

| Risk ID | Risk Description | Category | Likelihood | Impact | Severity | Mitigation / Status |
|---|---|---|---|---|---|---|
| **RSK-52-01** | Physical Earbud Firmware Incompatibilities in Real Field Deployments | Hardware / Protocol | High | Medium | **HIGH** | OmniBuds implements strict unknown-device default-deny and read-only fallback. Firmware compatibility resolver flags unverified revisions. Hardware verification gate is recorded as `NOT VERIFIED (BLOCKED_NO_ENVIRONMENT)` until physical HIL execution. |
| **RSK-52-02** | Absence of Physical Hardware Test Environment | Hardware QA | Definite | High | **HIGH** | Honestly documented. No test results fabricated. Recommendation is CONDITIONAL GO for Software RC1, requiring physical test pass prior to general public release. |
| **RSK-52-03** | Absence of Production Signing Keystore | Release Engineering | Definite | High | **MEDIUM** | Standard release engineering practice. Signing gate accurately reflects `BLOCKED_NO_KEYS`. Unsigned companion shell APK generated; signing delegated to secure CI/CD secrets manager. |
| **RSK-52-04** | GitHub App Workflow Permission Boundary (403 on Push) | Infrastructure / CI | Definite | Low | **LOW** | Local `.github/workflows/release-verification.yml` exists and is validated. Pushing is managed via separate administrative token or PR with workflow scope. |
| **RSK-52-05** | Android Platform Limitation: Non-Observable Battery & Codec via Public APIs | Platform / API | High | Medium | **MEDIUM** | Documented under Known Limitations. Android Bluetooth adapters honestly report `NOT_OBSERVABLE` and `NOT_SELECTABLE` rather than guessing or fabricating fake metrics. |
| **RSK-52-06** | Android Target is Library (AAR), Not Standalone End-User APK | Architecture | Definite | Medium | **LOW** | OmniBuds `:platform:android` is designed as a core SDK/library (`omnibuds-android-1.0.0.aar`). Companion shell application serves as reference host container. |
| **RSK-52-07** | Linux Desktop BlueZ Permissions & DBus Session Availability | Desktop / OS | Medium | Medium | **MEDIUM** | Desktop distribution tarball includes launcher script. BlueZ adapter falls back to `UnsupportedDesktopBluetoothAdapter` if DBus or BlueZ daemon is unreachable. |

---

## 2. Risk Acceptance & Escalation

- **Accepted for Software RC1**: RSK-52-03 (Signing Keys), RSK-52-04 (Workflow Scope), RSK-52-05 (Android API boundaries), RSK-52-06 (Library AAR scope), RSK-52-07 (Desktop BlueZ).
- **Blocking General Public Store Deployment**: RSK-52-01 and RSK-52-02 (Physical Hardware HIL verification must occur before claiming verified device support).
