# OMNIBUDS — PHASE 0: SECURITY & SAFETY GOVERNANCE

- Document: `docs/phases/phase-0/security-governance.md` — binding project-wide safety and privacy rulebook.
- Owner: Phase 0, Agent 4 (Security & Safety), per `docs/phases/phase-0/execution-prompt.md` §4.
- Authority: `docs/MASTER-CONTEXT.md` is the master source of truth (master §58); on conflict it wins over this file and over the Phase 0 prompt.
- Rule grammar: `SEC-<area>-<NNN>`. Cross-referenced siblings: `docs/phases/phase-0/requirements.md` (REQ-P0-008, REQ-P0-007), `docs/phases/phase-0/risk-register.md` (RISK-*), `docs/phases/phase-0/specs.md`, `docs/phases/phase-0/architecture-governance.md`, `docs/phases/phase-0/testing-governance.md`, `docs/phases/phase-0/audio-governance.md`, `docs/phases/phase-0/decisions.md` (ADR-P0-*), `docs/phases/phase-0/validation.md`.

Vocabulary used normatively (do not substitute synonyms):

- CapabilityState: `UNKNOWN | UNSUPPORTED | READ_ONLY | SUPPORTED_VOLATILE | SUPPORTED_PERSISTENT | PERSISTENCE_VERIFIED` (master §10)
- CodecState: `SUPPORTED | AVAILABLE | ENABLED | NEGOTIATED | ACTIVE | CONFIGURABLE` (master §15)
- VerificationLevel / ProtocolConfidence: `INFERRED | IMPLEMENTED | LAB_TESTED | HARDWARE_VERIFIED | PERSISTENCE_VERIFIED` (master §27, §54)
- Error categories: `BluetoothDisabled`, `PermissionDenied`, `DeviceDisconnected`, `TransportUnavailable`, `GattFailure`, `RfcommFailure`, `ProtocolMismatch`, `UnsupportedFeature`, `WriteRejected`, `VerificationFailed`, `Timeout`, `FirmwareMismatch`, `CodecUnavailable` (master §30)
- Absence of information is `UNKNOWN` — never `false`, `0`, `"unsupported"` (master §53); no fabricated metadata (master §6; execution §18).

---

# 1. Purpose And The Two Harms

OmniBuds writes to consumer audio hardware over partly undocumented vendor protocols and stores
Bluetooth identifiers that identify a person's belongings. Two distinct harms are governed here.

- **SEC-GEN-001** These rules bind every later phase. Narrowing one requires an ADR in that phase's decisions log plus a stated residual risk entry; no silent relaxation.
- **SEC-GEN-002** Harm class A — **user privacy harm**: addresses, fingerprints, manufacturer data and packet captures can identify a person and their devices. Every collected field is a liability.
- **SEC-GEN-003** Harm class B — **hardware harm**: one malformed or unverified write can misconfigure or brick a device the user owns. This harm is irreversible from inside the app; the app controls no rollback.
- **SEC-GEN-004** Where safety and product ambition conflict, the safe reading wins: "do not show it" beats "show it and hope" (master §2, §11).
- **SEC-GEN-005** OmniBuds never simulates a hardware capability, a permission state, a device state, or a data value it could not actually obtain (master §2, §53).

# 2. Permissions Governance

The Android permission model is a boundary to respect, not an obstacle to route around.

- **SEC-PERM-001** Never promise functionality Android does not grant (master §4). Each row below is a permission-gated *candidate*, not a delivered feature.
- **SEC-PERM-002** Map every capability to its conceptual permission class before any phase designs it:

| Capability (conceptual) | Permission class needed conceptually | Install-time vs runtime | Android-version dependence | On refusal, report |
| --- | --- | --- | --- | --- |
| Adapter state (on/off) | Bluetooth adapter/connect class | Legacy install-time; connect class runtime from Android 12 | Naming and enforcement differ at API 31 | `PermissionDenied`, or `BluetoothDisabled` if the adapter is off |
| Bonded device list | Bluetooth connect class | Install-time legacy; runtime from Android 12 | Address visibility/redaction varies by version | No bonded list; identity `UNKNOWN` |
| Detect already-connected audio device | Bluetooth connect class | Runtime from Android 12 | Profile-query availability is OEM-dependent | Auto-detect (master §4) unavailable, never simulated |
| Classic discovery | Bluetooth scan + legacy admin class | Admin install-time; scan runtime from Android 12 | Result availability differs around API 31 | No discovery results |
| BLE scan / advertisements | Scan class; location class where the OS still ties scan results to location | Runtime | `neverForLocation`-style declarations behave differently across versions | No scan data; fingerprint fields `UNKNOWN` |
| Background scanning | Scan class plus background-location class where applicable | Runtime, separately granted | Hardly restricted before Android 10, heavily after | Do not claim background awareness |
| GATT read (services/characteristics/descriptors) | Bluetooth connect class | Runtime from Android 12 | — | `PermissionDenied`; no invented capability rows |
| GATT notify / write | Bluetooth connect class | Runtime from Android 12 | — | `PermissionDenied`; control hidden |
| RFCOMM/SPP control socket | Bluetooth connect class | Runtime from Android 12 | Socket availability OEM-dependent | `RfcommFailure` / `TransportUnavailable` / `PermissionDenied` |
| Audio transport + codec read-back | No app permission beyond OS surfaces; read-only where exposed | — | Read vs write exposure varies by OS and OEM | `CodecUnavailable`; `READ_ONLY`, never a fake setting (master §21) |
| Codec *configuration* | Not grantable by any app permission on most devices | — | OS/OEM policy, not permission policy | State the platform restriction (master §17) |
| Quick Settings tile | No permission can place a tile; the user adds it | — | The shade hosts no arbitrary custom panel (master §32) | Honest explanation of what the user must do |
| Persistent control notification | Notifications class | Runtime from Android 13 | — | No notification, no substitute control surface (master §33) |
| Foreground device-lifecycle service | Foreground-service class with nearby-device type | Runtime | Type rules and background-start limits tightened at Android 14 | Do not claim unattended reconnect |

- **SEC-PERM-003** This table is governance, not a declaration: this document declares no permission, adds no dependency, changes no build file. The implementing phase re-verifies every name, API level and OEM variation against current platform documentation — remembered behaviour is not evidence.
- **SEC-PERM-004** Request nothing the app cannot justify to the user in one sentence. Prohibited: bundling a permission with an unrelated feature, requesting "for later", or treating one grant as authority for another capability.
- **SEC-PERM-005** On refusal the app SHALL degrade honestly: emit `PermissionDenied`, surface the reason, and keep every dependent field `UNKNOWN`.
- **SEC-PERM-006** No re-prompt loop, no nagging path, and no feature silently gated behind a permission the user already declined.
- **SEC-PERM-007** No simulated data to cover a refusal: no placeholder device names, no cached value presented as live, no `UNSUPPORTED` standing in for a refused read.
- **SEC-PERM-008** No indirect acquisition of refused data: no reflection, no shell, no vendor workaround, no reliance on another app's grant, no scraping of system settings.
- **SEC-PERM-009** No rule or feature in any later phase may be phrased as "work around Android". The only compliant phrasing is "respect the platform restriction and report it".

# 3. Device Identifier Governance

A Bluetooth address is a durable handle on a person's belongings.

- **SEC-ID-001** Collect a Bluetooth address only where it is legitimately accessible under a granted permission (master §6). A redacted, masked, non-resolvable or absent address is `UNKNOWN`, never reconstructed.
- **SEC-ID-002** Never unmask, brute-force, correlate or re-derive an identifier the platform withheld. Withholding is the answer, and the answer is `UNKNOWN`.
- **SEC-ID-003** Treat advertised addresses as potentially rotating. A rotating/private address is neither proof of a new device nor a stable identifier; identity comes from the fingerprint (master §7), not from an address alone.
- **SEC-ID-004** Minimum retention: keep the least identifier content that makes the feature work, for the shortest period it is needed (master §6).
- **SEC-ID-005** Keep the two device lifecycles separate (master §5):

| Device class | Enters by | Leaves by | Identifier retention |
| --- | --- | --- | --- |
| Active session | Currently connected | Disconnect, unless explicitly saved | Cleared with the session; no durable record |
| Saved device | Explicit user action "Add to My Devices" | Explicit user deletion | Retained only while saved; capability/protocol knowledge may persist with the save |
| Transiently observed device | Appeared in a scan or discovery result | Observation ends | Not retained at all |

- **SEC-ID-006** The app SHALL NOT permanently retain every Bluetooth device it briefly saw (master §5). Scanning is not saving.
- **SEC-ID-007** Every saved device SHALL be listed with its stored data and deletable in one action; deletion SHALL be complete across identifier, fingerprint, captured metadata, cached protocol observations and derived logs for that device.
- **SEC-ID-008** Prohibited as stored data: pairing material, link keys, identity-resolving material, and any non-Bluetooth hardware identifier used for correlation.
- **SEC-ID-009** No cross-device or cross-user profile built from identifiers. OmniBuds knows the devices on this phone, on this user's behalf (master §28).

# 4. Data Minimisation And Privacy

- **SEC-PRIV-001** Storage is local-first by default. Any byte leaving the device needs a phase that authorises it, a disclosure, and a user action.
- **SEC-PRIV-002** No telemetry, analytics, crash reporting, ad SDK, cloud sync, backup service or account system exists in the planning so far. Do not invent one in a phase that did not specify it (execution §2).
- **SEC-PRIV-003** Before any new data category leaves the device, the responsible phase SHALL disclose what leaves, to whom, why, how often, retention, and the off switch. Disclosure ships with the transfer, never after it.
- **SEC-PRIV-004** Enforce separation of the user device database from the global protocol database (master §28): protocol entries hold model-level knowledge only — no address, no device name, no per-user timeline.
- **SEC-PRIV-005** Contributing laboratory findings outward is a deliberate, reviewed act, and the contribution SHALL be stripped of user-identifying material first. Future community/SDK phases inherit this; it is not a licence to upload.
- **SEC-PRIV-006** Unknown metadata stays unknown: a missing manufacturer, model, firmware version, battery level (master §23) or codec parameter is `UNKNOWN`/null — not `0`, not "not supported", not a plausible guess.
- **SEC-PRIV-007** Battery and sensor reads are displayed, not stored as history, unless a phase explicitly requires history and states its retention.
- **SEC-PRIV-008** Capability claims follow the per-device verified / not-verified / unsupported report shape (master §55); blanket statements such as "OmniBuds supports Sony" are prohibited.

# 5. Logging And Diagnostics Governance

- **SEC-LOG-001** Permitted log content: timestamps, error category from the master §30 set, operation, transport, protocol family, CapabilityState/CodecState transitions, VerificationLevel changes.
- **SEC-LOG-002** Mandatory redaction at the point of emission — not on export: Bluetooth addresses, device names bound to an address, manufacturer data payloads, characteristic payloads containing identifiers, pairing material, link keys, identity-resolving material, and any field in §3.
- **SEC-LOG-003** Where a log must refer to an address for debugging, use a purpose-built stable-but-not-reversible local reference and never log the raw value alongside it.
- **SEC-LOG-004** Release builds SHALL NOT emit raw packet bytes at any level.
- **SEC-LOG-005** Packet capture (master §26 Packet Logger; Phase 36) is opt-in per session, and the opt-in UI SHALL state plainly that captures may contain personal identifiers and may contain pairing or identity material.
- **SEC-LOG-006** Captures SHALL expire: bounded store, explicit age/count limit, visible wipe action. Captures are laboratory artefacts, not product history.
- **SEC-LOG-007** A diagnostic report (master §31) is user-facing, so it SHALL be fully rendered and reviewable by the user before sharing, and its default content SHALL omit raw identifiers unless the user deliberately enables a verbose mode that repeats the SEC-ID-001 and §5 warnings.
- **SEC-LOG-008** Prohibited: attaching a log or report silently to any network request, bug report or support ticket; background log upload; retaining a report after the user deleted the device (SEC-ID-007).

# 6. Unknown-Device Safety

- **SEC-UNK-001** An unrecognised device begins in read-only mode (master §25; REQ-P0-008; ADR-P0-006). Read-only is the default and the burden of proof lies on leaving it.
- **SEC-UNK-002** Safe discovery may touch only: device metadata, services, characteristics, descriptors, manufacturer data, standard battery, and standard Bluetooth information (master §6, §25).
- **SEC-UNK-003** Standard-profile reads (for example the standard battery service) remain `READ_ONLY` capabilities, and an unreadable level stays unknown rather than 0% (master §23).
- **SEC-UNK-004** Absolute prohibition: no random writes to unknown characteristics.
- **SEC-UNK-005** Absolute prohibition: no blind fuzzing of devices (master §25; execution §18).
- **SEC-UNK-006** Absolute prohibition: no undocumented commands sent to real hardware (master §25).
- **SEC-UNK-007** Absolute prohibition: never assume a UUID's purpose without evidence; never assume a packet byte's meaning without evidence (execution §15).
- **SEC-UNK-008** No write path selected by heuristic, name match or "most vendors do this". Selection is by documented protocol knowledge, or it does not happen.
- **SEC-UNK-009** No capability inferred from a desirable control surface: not discovered is not `UNSUPPORTED`, and not verified is not `SUPPORTED` (master §11, §53).
- **SEC-UNK-010** The UI for an unknown device SHALL show what was read and what remains unknown, and SHALL expose nothing that could write.

# 7. Write-Safety Governance

A write is the only place OmniBuds can cause hardware harm.

- **SEC-WRITE-001** Any write to real hardware requires all of: (1) device identity known — fingerprint matched to a documented model, not a guess (master §54); (2) protocol known with confidence at least `LAB_TESTED` for that command (master §27); (3) command understood, meaning purpose and effect documented; (4) command present in the protocol database as a structured entry, not reconstructed at a call site (master §52); (5) reversible, or explicitly confirmed by the user (SEC-WRITE-004); (6) a baseline read of the affected state taken first, so "did it work" is answerable (master §24).
- **SEC-WRITE-002** No write that fails any precondition above — including "just to see what happens".
- **SEC-WRITE-003** No automatic or background write to an undocumented characteristic.
- **SEC-WRITE-004** Consequential changes — not trivially undoable by the user, or altering device behaviour for other phones or users — require visible confirmation stating what changes and whether it persists on the device.
- **SEC-WRITE-005** A timed-out write SHALL be verified by re-reading state, never blindly re-sent (execution §9.5):

```text
SET <feature> -> Timeout
-> READ CURRENT STATE (do not resend)
-> compare with intended value
-> report actual state, or VerificationFailed
```

- **SEC-WRITE-006** Read and write retry policies are distinct (master §30): reads may retry in a controlled way; side-effecting writes may not.
- **SEC-WRITE-007** A successful write is not proof of persistence. `SUPPORTED_PERSISTENT` requires the full chain READ, WRITE, READ BACK, VERIFY, DISCONNECT, RECONNECT, READ AGAIN, VERIFY (master §24; REQ-P0-007). Anything less is `SUPPORTED_VOLATILE` or `UNKNOWN`.
- **SEC-WRITE-008** Rejected or unavailable writes are reported as `WriteRejected` / `UnsupportedFeature` and the control is removed or marked unavailable — not retried into silence.
- **SEC-WRITE-009** No magic protocol bytes at call sites; writes go through the documented protocol layer so SEC-WRITE-001 is checkable (master §51, §52).
- **SEC-WRITE-010** No blocking write operation on the main thread (execution §18).

# 8. Firmware-Update Policy

- **SEC-FW-001** Firmware updating is explicitly out of scope for every phase planned so far (master §48). It is the highest-severity hardware-harm surface: a wrong or interrupted image bricks a device the user owns.
- **SEC-FW-002** No code may initiate, imply, stage, partially implement or scaffold a firmware update path — including read-only "preparation" that later becomes a writer, hidden feature flags, or an OTA payload handler.
- **SEC-FW-003** Firmware *version* reads are in scope as metadata (master §6) and inform compatibility only. Phase 45 "Firmware Compatibility" means knowing which firmware a protocol works against; it does not authorise writing firmware.
- **SEC-FW-004** Mismatch is reported as `FirmwareMismatch` and the affected capability is demoted to `UNKNOWN` or `UNSUPPORTED` with an explanation (master §30, §55).
- **SEC-FW-005** Any future firmware capability requires its own phase, its own risk entry, and an ADR. Until then every report reads "Firmware Update: unsupported".

# 9. Protocol-Research Authorization

- **SEC-RES-001** Research only devices and software the development team is authorized to analyze (master §26).
- **SEC-RES-002** Record provenance for every discovered command: source (own capture, published documentation, third party), supporting evidence, and the device and firmware it was observed on.
- **SEC-RES-003** Record a confidence level per entry using the ProtocolConfidence vocabulary `INFERRED | IMPLEMENTED | LAB_TESTED | HARDWARE_VERIFIED | PERSISTENCE_VERIFIED` (master §27); these levels are never conflated (execution §16).
- **SEC-RES-004** Findings stay gated behind their verification level: `INFERRED` is data, not a feature — it may not drive a UI control and may not be sent to a user's device.
- **SEC-RES-005** Research writes occur only in the protocol laboratory (master §26; Phase 20) on authorized devices under team control, following DISCOVER, IDENTIFY, READ, OBSERVE, UNDERSTAND, VALIDATE, WRITE, READ BACK, DISCONNECT, RECONNECT, VERIFY (master §26; execution §15).
- **SEC-RES-006** Never begin research with arbitrary writes (execution §15).
- **SEC-RES-007** A user's device is never a research target by default; research capability must not be switched on implicitly by shipping a diagnostic or laboratory feature.
- **SEC-RES-008** Authorization and licence review of a target precedes analysis; uncertainty about authorization is a stop condition, not a risk to accept.

# 10. Background-Operation Compliance

- **SEC-BG-001** All background behaviour — screen off, app closed, reconnect, adapter toggle, reboot, case open, saved-device restoration — SHALL comply with Android background execution restrictions (master §35).
- **SEC-BG-002** No unsafe background Bluetooth service (master §51): no always-on background scanning, no silent reconnect loop, no write issued while the user is neither present nor informed.
- **SEC-BG-003** Where Android forbids what the product would want, report the limitation honestly in UI and documentation; never describe the blocked feature as working (master §4).
- **SEC-BG-004** Notifications, Quick Settings and widgets use only legitimate platform mechanisms, show only values actually available, and assume no control panel the platform does not grant (master §32, §33, §34).
- **SEC-BG-005** Background execution is not a licence to log identifiers while the app is hidden; §3 and §5 rules apply identically to background code paths.
- **SEC-BG-006** Any background capability requires a phase-level requirement stating its trigger, permission class, lifecycle, battery cost, and what the app reports when Android declines it.

# 11. Vendor Protocol Handling And Third-Party Code

- **SEC-VEND-001** Do not extract executable code from undocumented vendor applications: no decompiled logic, no re-hosted vendor binary, no lifted command blobs copied into a class "because they work".
- **SEC-VEND-002** Treat reverse-engineered command sequences as data carrying a confidence level and provenance (SEC-RES-002, SEC-RES-003), never as inline constants.
- **SEC-VEND-003** Vendor extensions carry the same write-safety and privacy rules as common features (master §13, §25).
- **SEC-VEND-004** Third-party libraries require review before adoption and inherit this rulebook: no silent network I/O, no identifier logging, no bundled telemetry.
- **SEC-VEND-005** Protocol definitions, commands, responses, parsers and encoders are documented structures (master §52); an undocumented byte with an assumed meaning is prohibited (execution §15).

# 12. Security Risk Linkage

The rules above are mitigations for named risks in `docs/phases/phase-0/risk-register.md`; the owning phase SHALL cross-reference in both directions.

| Risk | Register id | Governing rules |
| --- | --- | --- |
| Authentication/encryption of control channels and stored data | RISK-004 | SEC-ID-001..009, SEC-LOG-002/003, SEC-PRIV-001..005, SEC-VEND-002 |
| Protocol write safety | RISK-010 | SEC-WRITE-001..010, SEC-UNK-004..009 |
| Background execution restrictions | RISK-007 | SEC-BG-001..006, plus the background rows of SEC-PERM-002 |
| Unknown proprietary protocols | RISK-003 | SEC-UNK-001..010, SEC-RES-001..008 |
| Firmware incompatibility | RISK-008 | SEC-FW-001..005 |
| Persistence assumptions | RISK-009 | SEC-WRITE-007 |

# 13. Status Of These Rules In Phase 0

- **SEC-TRACE-001** Phase 0 implements none of the above. This file is rules, not behaviour: no Kotlin, no Gradle, no manifest, no permission declaration, no runtime enforcement, no test run. Nothing here claims that any protection exists today.
- **SEC-TRACE-002** Each `SEC-*` rule MUST be converted by the implementing phase into a requirement `REQ-<SCOPE>-<NNN>` with rationale, dependencies, acceptance criteria and a verification method (master §41), plus a `TASK-<SCOPE>-<NNN>` and, where enforcement is machine-checkable, a `TEST-<SCOPE>-<NNN>` defined in `docs/phases/phase-0/testing-governance.md` terms.
- **SEC-TRACE-003** Expected conversion points, and no earlier: permissions in Phase 2; identifiers and session retention in Phases 3–5; unknown-device read-only in Phase 21; write safety in Phases 6–9 and 17–18; protocol research in Phase 20; background lifecycle in Phase 28; logging and diagnostics in Phase 36; hardening sweep in Phase 35.
- **SEC-TRACE-004** Until converted and verified, every rule's enforcement status is `UNKNOWN`, and `docs/phases/phase-0/validation.md` SHALL NOT mark any security rule as satisfied.
- **SEC-TRACE-005** Phase 35 re-audits this file line by line and records per rule: converted requirement id, verification method, residual gap.

---

# Appendix A — Conflicts Surfaced Between The Two Sources

Reported for the orchestrator (execution §5); not silently resolved. Master governs each case (master §58).

| # | Conflict | Resolution applied here |
| --- | --- | --- |
| A1 | Execution §4 gives Agent 4's output as `docs/phase-0/security-governance.md`; execution §6 mandates `docs/phases/phase-0/...` | Written to `docs/phases/phase-0/` per §6 and the orchestrator's instruction. Agents 1, 2, 3, 5 and 7 carry the same stale `docs/phase-0/` prefix and will collide with §6. |
| A2 | Execution §16 spells levels `LAB-TESTED`, `HARDWARE-VERIFIED`, `PERSISTENCE-VERIFIED`; master §27 uses underscore forms | Master's underscore forms used throughout. |
| A3 | Execution §9.3 lists 10 error categories; master §30 lists 13, adding `GattFailure`, `RfcommFailure`, `FirmwareMismatch` | Master's 13-category set is the project vocabulary (used in SEC-FW-004, SEC-WRITE-008, §2 table). |
| A4 | Execution §7 REQ-P0-007 starts the persistence chain at WRITE; master §24 requires READ before WRITE and explicit VERIFY steps | Master's fuller chain governs (SEC-WRITE-007); the baseline read is kept as precondition SEC-WRITE-001(6). |
| A5 | Execution §10/§11 use `P0-Tnnn` / `P0-TEST-nnn`; master §41/§44/§45 imply the `REQ-/TASK-/TEST-<SCOPE>-<NNN>` grammar | This document uses the canonical grammar (SEC-TRACE-002); task-list and test-plan authors should reconcile. |
| A6 | Master §4 promises no second pairing step, while the same section forbids promising what Android permits | Treated as self-resolving tension: auto-detection is a goal, and per SEC-PERM-001/009 it is either delivered or honestly reported. |
| A7 | Execution §16 defines `LAB_TESTED` as testing against a simulation/mock, which could be read as sufficient for shipping | Kept distinct: `LAB_TESTED` never justifies a write to a user's hardware (SEC-WRITE-001(2), SEC-RES-004). |

# Appendix B — Stop Conditions Honoured

No Bluetooth API, transport, protocol command, codec setting, UI element, database behaviour or
background service was implemented. No permission was declared, no dependency added, no command
run, no device contacted, no test executed. Phase 0 ends at this document, and the next phase
begins only on an explicit instruction (master §50, §57; execution §25).
