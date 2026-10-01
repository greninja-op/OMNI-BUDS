# OMNIBUDS — PHASE 0 — AUDIO GOVERNANCE

**Document:** `docs/phases/phase-0/audio-governance.md` — Phase 0, Agent 5 workstream (Audio Architecture Agent, `docs/phases/phase-0/execution-prompt.md` §4)
**Status:** Normative rules. Binding on Phases 10–15 and on every later phase that touches audio.
**Sources:** `docs/MASTER-CONTEXT.md` §2, §3, §8, §14–§23, §29, §31, §33, §51–§55; `docs/phases/phase-0/execution-prompt.md` §2, §4, §7 (REQ-P0-003/009/010/011), §8, §16, §17, §22, §23.
**Conflict rule:** where the two sources disagree, `docs/MASTER-CONTEXT.md` wins and the deviation is recorded in Appendix A — never silently resolved (master §58, phase-0 §23).

## 0. Terminology contract (binding)

| Contract | Exact permitted values |
| --- | --- |
| `CodecState` | `SUPPORTED` \| `AVAILABLE` \| `ENABLED` \| `NEGOTIATED` \| `ACTIVE` \| `CONFIGURABLE` |
| `CapabilityState` | `UNKNOWN` \| `UNSUPPORTED` \| `READ_ONLY` \| `SUPPORTED_VOLATILE` \| `SUPPORTED_PERSISTENT` \| `PERSISTENCE_VERIFIED` |
| `VerificationLevel` / `ProtocolConfidence` | `INFERRED` \| `IMPLEMENTED` \| `LAB_TESTED` \| `HARDWARE_VERIFIED` \| `PERSISTENCE_VERIFIED` |
| Audio-relevant error categories | `BluetoothDisabled`, `PermissionDenied`, `DeviceDisconnected`, `TransportUnavailable`, `GattFailure`, `RfcommFailure`, `ProtocolMismatch`, `UnsupportedFeature`, `WriteRejected`, `VerificationFailed`, `Timeout`, `FirmwareMismatch`, `CodecUnavailable` |
| ID grammar | `REQ-<SCOPE>-<NNN>`, `TASK-<SCOPE>-<NNN>`, `TEST-<SCOPE>-<NNN>`, `ADR-<SCOPE>-<NNN>`, `RISK-<NNN>`; audio rules in this file use `AUD-<area>-<NNN>` |

**AUD-TERM-001** Absence of information SHALL be represented as `UNKNOWN`. It SHALL NOT be collapsed into `0`, `false`, `null`-as-meaningless, or `"unsupported"` (master §53, §23; phase-0 §9.2).
**AUD-TERM-002** No audio number, codec name, or capability label SHALL be rendered, logged, or reported unless it was obtained from a source permitted by §7 of this document.

## 1. Purpose and scope restriction

**AUD-SCOPE-001** This file is the architectural rulebook for the audio subsystem. It defines vocabulary, state semantics, dependency boundaries and verification gates that later phases MUST obey. It does not design the subsystem's classes.
**AUD-SCOPE-002** Phase 0 implements nothing: no A2DP, no LE Audio, no codec configuration, no audio processing, no Android audio or Bluetooth API calls, no Kotlin, no Gradle, no audio configuration code, no commands run (phase-0 §2). Placeholder interface stubs are out of scope here; the audio subsystem is built in Phases 10–15.
**AUD-SCOPE-003** No fake placeholder implementation of an audio system SHALL be created, and no audio rule in this file SHALL be softened later without an `ADR-<SCOPE>-<NNN>` entry in `docs/phases/phase-0/decisions.md` (or the owning phase's `decisions.md`).
**AUD-SCOPE-004** Every rule in this file is expressed so that it is checkable by documentation review in Phase 0 and by code review in Phases 10–15. Test ids (`TEST-AUDIO-<NNN>`) belong to `docs/phases/phase-0/testing-governance.md`, not here.

## 2. Audio path isolation (master §3)

The normal media path, which OmniBuds does not enter:

```text
Music / Video App
       ↓
Android Audio Stack
       ↓
Bluetooth Audio Transport
       ↓
Earbuds / Headphones
```

Where OmniBuds sits — control/configuration channel only, alongside the path:

```text
                  Android Audio
                       │
                       ▼
                  Bluetooth
                       │
                       ▼
                    Device

OmniBuds
    │
    └──── control / configuration channel
```

**AUD-PATH-001** OmniBuds SHALL NOT capture, process, re-encode or re-transmit media audio. The forbidden default is `capture audio → process audio → re-encode audio → transmit audio again` (master §3).
**AUD-PATH-002** Media transport SHALL remain owned by the normal Android/Bluetooth audio path (master §3). OmniBuds reads transport state and writes configuration state only.
**AUD-PATH-003** The prohibition is justified by the consequences it prevents: degraded **quality** (second encode/decode generation loss, loss of native codec behaviour), degraded **latency**, altered or defeated **codec behaviour** including adaptive bitrate control, increased **battery** drain, and reduced **stability** (a second competing audio/Bluetooth path). Any proposal for in-path behaviour MUST argue against these five consequences.
**AUD-PATH-004** The baseline decision is recorded as "OmniBuds stays outside the normal media audio path" — phase-0 §13 ADR-P0-002, the canonical instance of master §47's `ADR-001` — and SHALL NOT be revisited by implementation convenience.
**AUD-PATH-005** In-path behaviour could ever be proposed only when **both** hold: (a) an accepted `ADR-<SCOPE>-<NNN>` states the reason, the measured quality/latency/battery cost, and the fallback when it is unavailable; and (b) the feature is explicitly labelled to the user as a **software feature**, never as a device hardware capability (master §2, §22).
**AUD-PATH-006** Even when permitted by AUD-PATH-005, in-path behaviour is **not part of the primary hardware-control experience** (master §2) and SHALL be opt-in, per-session, visibly marked, and disabled by default.
**AUD-PATH-007** The audio subsystem SHALL be able to report its own path status (`OUT_OF_MEDIA_PATH` by default) so that Phases 14 (`Audio Path Validation`) and 33 can test AUD-PATH-001 rather than assume it.

## 3. Place in the layer model, and forbidden dependencies

Dependency direction (phase-0 §8.2), with the audio subsystem's position marked:

```text
UI → Application / State → Core Domain → Protocol / Capability abstractions → Platform transport implementations
                              ↑ audio state model, CodecState and codec registry live here
```

**AUD-LAYER-001** The audio state model SHALL be platform-neutral core domain: `AudioState` is listed as a shared/KMP-portable area (phase-0 §8.4). No `android.*` type SHALL appear in it.
**AUD-LAYER-002** Reads of Android audio/Bluetooth APIs SHALL live behind the platform boundary (phase-0 §8.3) and merely map platform observations into `CodecState` / audio-quality vocabulary. The mapping rules are part of this governance, not of the adapter's private logic.
**AUD-LAYER-003** The audio subsystem SHALL NOT become a second Bluetooth stack. It MUST NOT own adapter state, discovery, connection lifecycle, transport selection, pairing, retries, or reconnect. It consumes those and maps their failures to `BluetoothDisabled`, `DeviceDisconnected`, `TransportUnavailable`, `GattFailure`, `RfcommFailure`, `Timeout`.
**AUD-LAYER-004** Vendor protocol code SHALL NOT own global audio state (phase-0 §8.2 closing). Protocol adapters report codec observations; the audio subsystem is the single reconciler that produces the authoritative `CodecState` set. No UI or domain component may read a "codec active" flag written directly by vendor protocol code.
**AUD-LAYER-005** UI SHALL consume derived render models only. No codec conditionals, no manufacturer-specific audio logic, no hidden global audio state (master §51; phase-0 §18).
**AUD-LAYER-006** Two contradictory `CodecState` values for the same codec in the same session SHALL NOT coexist. The conflict resolves to `UNKNOWN` plus `VerificationFailed`, and is surfaced in diagnostics (§13).

## 4. Classic Bluetooth and LE Audio are distinct transports (master §20)

```text
Classic Bluetooth → A2DP → SBC / AAC / aptX / LDAC / etc.
LE Audio          → LC3
```

**AUD-XPORT-001** Transport family SHALL be a first-class dimension of every audio record. A codec name alone never identifies the state of a session.
**AUD-XPORT-002** LE Audio MUST NOT be modelled as "another A2DP codec" (master §20). LC3 is not an A2DP codec entry, and the A2DP codec-capability semantics do not transfer to it unmodified.
**AUD-XPORT-003** These stay separate in the data model: transport family; session identity; codec/config enumeration and vocabulary; configuration parameter set; state sources and observers; quality fields; error mapping; verification path.
**AUD-XPORT-004** A device exposing both Classic and LE Audio sessions SHALL report per-transport codec state. A single mixed ladder across transports is prohibited, and cross-transport state bleed (e.g. A2DP `ACTIVE` inferred from an ISOC/LE Audio observation) is prohibited.
**AUD-XPORT-005** OmniBuds SHALL NOT implement or imply silent fallback between Classic and LE Audio; transport selection is platform-owned. Platform differences are tracked as RISK-006 (`docs/phases/phase-0/risk-register.md`).

## 5. Codec registry governance (master §14, §19; REQ-P0-010)

**AUD-REG-001** The registry SHALL be able to represent at least: SBC, AAC, aptX, aptX HD, aptX Adaptive, aptX Lossless **only where actually exposed**, LDAC, LC3, and future/vendor codecs (master §14, §19).
**AUD-REG-002** The aptX variants are **separate capabilities** and SHALL NEVER collapse into one `aptX = supported` flag where the distinction matters (master §19). Supporting aptX evidences nothing about aptX HD, aptX Adaptive or aptX Lossless.
**AUD-REG-003** "Where actually exposed" is a read condition, not a marketing condition: aptX Lossless and vendor codecs remain `UNKNOWN` until an actual source (§7) exposes them, even when the model is theoretically capable.
**AUD-REG-004** Adding a codec SHALL be a registry-data change (plus, where needed, per-codec state-mapping rules) — never scattered conditionals in audio, UI, or protocol code (master §51, §52). Registry entries SHALL carry: stable codec id, display name, transport family, parameter vocabulary (sample rate, bit depth, bitrate, channel mode, quality mode), permitted state sources, known OS/platform restrictions, confidence. The physical schema is fixed in Phase 11, not here.
**AUD-REG-005** Codec identity SHALL be data with documentation, not magic bytes, magic UUIDs or hard-coded device assumptions (master §51, §52).
**AUD-REG-006** Registry membership SHALL NOT produce a state: "the registry contains LDAC" MUST NOT yield `AVAILABLE`, `ENABLED`, `NEGOTIATED` or `ACTIVE` for any device.

## 6. `CodecState` semantics (master §15)

| State | Precise meaning | Minimum evidence | Confused with (forbidden) |
| --- | --- | --- | --- |
| `SUPPORTED` | The codec is exposed as implementable by **one endpoint** — device or host — recorded per endpoint, never merged | Device capability response / host API / registry declaration (`INFERRED`) | Availability in this session |
| `AVAILABLE` | Both endpoints support it **and** the current transport/session reports that it can be used now | A read made during a live session | User preference; theoretical device capability |
| `ENABLED` | The codec is offered or preferred for this session by the host stack or by explicit user selection. Phase-0's "Selected" maps here | Stack/user selection state actually read | `NEGOTIATED`, `ACTIVE` |
| `NEGOTIATED` | The transport negotiation result for this session includes this codec | Negotiation-state read | `ACTIVE` |
| `ACTIVE` | The codec **currently carries this session's media audio**, per §11's gate | Authoritative read passing the verification checklist | `NEGOTIATED`, `ENABLED`, `SUPPORTED` |
| `CONFIGURABLE` | Orthogonal property: at least one parameter of this codec in this session may legitimately be written (§9) | OS or protocol write path verified | A rung of the activity ladder |

Ladder (`CONFIGURABLE` sits beside it, not on it):

```text
SUPPORTED (recorded per endpoint: device | host)
  ↓ both ends support it AND the current session reports it usable
AVAILABLE
  ↓ offered / preferred / user-selected this session   ← phase-0 §17 "Selected"
ENABLED  ↓ transport negotiation result confirms it
NEGOTIATED  ↓ carrying this session's media audio, verified per AUD-VERIFY-002
ACTIVE
```

**AUD-STATE-001** The six-value set above is normative (master §15, REQ-P0-009). The five-name ladder in phase-0 §17 is a narrower restatement: it renames `ENABLED` as "Selected" and omits `CONFIGURABLE`. See Appendix A, C-01.
**AUD-STATE-002** A user selection is an **input to** `ENABLED`; it is never evidence of `NEGOTIATED` or `ACTIVE`.
**AUD-STATE-003** `SUPPORTED` SHALL keep distinct device-side and host-side records (master §15 "Earbuds support / Phone supports", §18). The pair is `SUPPORTED` only when both ends are.
**AUD-STATE-004** Each state SHALL carry its source, read time and `VerificationLevel`.
**AUD-STATE-005** A codec whose state was never read SHALL be `UNKNOWN`, not `UNSUPPORTED` (master §53).

Worked example A — device supports LDAC, AAC is the codec in use (master §15, §18):

| Codec | SUPPORTED (device / host) | AVAILABLE | ENABLED | NEGOTIATED | ACTIVE |
| --- | --- | --- | --- | --- | --- |
| LDAC | YES / YES | YES | NO | NO | NO |
| AAC | YES / YES | YES | YES | YES | YES |

Required rendering: `Audio: AAC (active). LDAC supported, not in use.` Prohibited: `LDAC ACTIVE`, or any single "Codec: LDAC" line taken from support/preference rather than from the `ACTIVE` read (master §18).

Worked example B — LDAC enabled but not negotiated (master §15, literal values):

| Codec | Earbuds support | Phone support | AVAILABLE | ENABLED | NEGOTIATED | ACTIVE |
| --- | --- | --- | --- | --- | --- | --- |
| LDAC | YES | YES | YES | YES | NO | NO |

Required rendering: `LDAC enabled (preferred), not negotiated — not carrying audio. Active codec: <read value, or UNKNOWN if unread>.` Prohibited: `LDAC ACTIVE`, and prohibited: rendering the active codec as `UNSUPPORTED` merely because it is not LDAC. `CONFIGURABLE` is judged separately: an `ACTIVE` LDAC whose quality priority cannot be written on this phone renders as active plus read-only, never as a control (master §21).

Display rule for each conflicting pair:

| Conflicting facts | Required display |
| --- | --- |
| `SUPPORTED` YES, `ACTIVE` NO | "supported, not in use"; never "active" |
| `ENABLED` YES, `NEGOTIATED` NO | "enabled / selected, not negotiated" |
| `NEGOTIATED` YES, `ACTIVE` unverified | "negotiated; active state not verified" |
| `ACTIVE` YES, `CONFIGURABLE` NO | show active codec; expose values read-only, no control (master §21) |
| Registry says capable, session says unknown | `UNKNOWN`; availability is never theoretical (§7) |
| Two contradictory `ACTIVE` claims | `UNKNOWN` + `VerificationFailed` (AUD-LAYER-006) |

## 7. Where each state may come from

| State | Permitted sources | Explicitly prohibited |
| --- | --- | --- |
| `SUPPORTED` | Device protocol capability response; host/platform exposed codec list; fingerprint/registry declaration marked `INFERRED` | Presentation in marketing text; assumption from brand or model family |
| `AVAILABLE` | Live-session codec list from the platform; device-reported capability set read this session | Theory of device capability (master §14); "the chipset supports it" |
| `ENABLED` | Host codec preference actually read; user selection recorded by the OS | A selection stored only in OmniBuds' own preferences |
| `NEGOTIATED` | Transport state read for the connected session; protocol response that documents negotiation results | `ENABLED` alone; `SUPPORTED` on both ends |
| `ACTIVE` | Authoritative read of the codec currently carrying media audio, gated by §11 | Any of the above rungs; inference from sample rate or bitrate |
| `CONFIGURABLE` | Verified OS setting; verified protocol command | Guessing from another phone, OEM build or firmware version |

**AUD-SRC-001** Every audio observation SHALL record which source produced it, with a timestamp and a `VerificationLevel`.
**AUD-SRC-002** Availability and use SHALL NOT be inferred from a device's theoretical capability (master §14 closing: "Do not assume that a codec is available merely because a device is theoretically capable of it").
**AUD-SRC-003** The concrete Android API surface for each read SHALL be decided in Phases 10–11 and recorded in that phase's `specs.md`; this file fixes only which state each source may establish.
**AUD-SRC-004** A source that cannot be reached for this transport yields `UNKNOWN`, never a stale last-known value presented as current.

## 8. Audio quality state model (master §16; REQ-P0-011)

Conceptual contract, fields only (master §16 `AudioTransportState`, REQ-P0-011): `transport`, `codec`, `sampleRate`, `bitsPerSample`, `bitrate`, `channelMode`, `qualityMode`, `active`.

| Field group | Value when unknown | Never render as |
| --- | --- | --- |
| `transport`, `codec` | `UNKNOWN` | "A2DP" or "SBC" as a default guess |
| `sampleRate`, `bitsPerSample`, `bitrate` | `UNKNOWN` per field | `0`, `-1`, `44100`, `48000`, `16`, `330000` |
| `channelMode`, `qualityMode`, `active` | `UNKNOWN` | `false`, `"stereo"`, `"balanced"`, or `false` read as "not in use" |

**AUD-QUAL-001** These eight fields are the minimum representable set (master §16, REQ-P0-011). Each SHALL be unknown independently: a partial read is a valid result and SHALL render exactly what was read.
**AUD-QUAL-002** Fabricated numbers are prohibited — `0`, `-1`, `-`, empty strings and plausible defaults are all fabrication when the value was not read (master §16 "Never show fake numbers").
**AUD-QUAL-003** Additional observables named by master §16 — connection priority, adaptive mode, LE Audio state, high-quality state, low-latency state — MAY be represented **only** where actually exposed, and MUST NOT be dropped silently from the contract; the final field list is Phase 13's decision (Appendix A, C-05).
**AUD-QUAL-004** `active` SHALL mean the §6 `ACTIVE` rung and SHALL NOT be computed as `negotiated || enabled`.
**AUD-QUAL-005** Precedent for unknown representation: battery uses `null`, not `0%` (master §23). The same rule governs every audio field above — absence is a distinct state, not a numeric floor (master §53).
**AUD-QUAL-006** Quality snapshots SHALL carry source, read time and level; on disconnect every field becomes `UNKNOWN` for display purposes, with the last verified read kept only in diagnostics history.

## 9. Codec configuration and OS restrictions (master §17, §21)

**AUD-CONFIG-001** Where Android legitimately permits control, account for codec, sample rate, bit depth, bitrate, quality mode, connection priority, adaptive mode and channel mode (master §21). Where it does not, control does not exist.
**AUD-CONFIG-002** If the OS exposes only read access, the state SHALL be `READ_ONLY` and a value SHALL be displayed exactly as read.
**AUD-CONFIG-003** Creating a fake setting when the OS exposes none is prohibited (master §21, §2). This covers disabled-but-rendered-as-enabled controls, decorative sliders, and "coming soon" affordances that imply a capability.
**AUD-CONFIG-004** OmniBuds SHALL NOT claim, document, hint at, or test-assert that it can force LDAC on any Android device, or on any class of Android devices. What is possible depends jointly on the Android OS, the Bluetooth stack, the OEM implementation, the phone hardware and the headset (master §17). RISK-005.
**AUD-CONFIG-005** LDAC's sound-quality priority, balanced mode, connection-quality priority and adaptive behaviour SHALL be exposed only where the read or the write is verified, and the **actual negotiated state SHALL always be shown separately from the requested/priority state** (master §17).
**AUD-CONFIG-006** A write SHALL be read back and, where persistence is claimed, verified through disconnect/reconnect before it is classified `PERSISTENCE_VERIFIED`; otherwise `SUPPORTED_VOLATILE` (master §24). A blocked write SHALL yield `CodecUnavailable`, `UnsupportedFeature` or `WriteRejected` — never a silent no-op.

## 10. Hardware DSP versus phone-side DSP (master §22)

Preferred path for hardware EQ/ANC/etc.:

```text
OmniBuds
   ↓
Vendor protocol
   ↓
Earbud DSP
```

**AUD-DSP-001** Audio features implemented by the device SHALL be driven through the vendor protocol to the earbud DSP, not emulated on the phone.
**AUD-DSP-002** Phone-side audio processing SHALL NOT impersonate a missing hardware feature. Fake hardware EQ, software ANC, fake transparency and similar are prohibited (master §2, §22).
**AUD-DSP-003** A phone-side audio feature may exist only as an explicitly labelled software feature under AUD-PATH-005, and SHALL NOT appear in the hardware-control surface as if it were device capability.
**AUD-DSP-004** DSP-related device state (EQ bands, presets, modes) SHALL be read from the device; it SHALL NOT be computed locally and reported back as the device's state. Phase 15 owns the separation implementation.

## 11. Audio verification rule (phase-0 §17)

| `VerificationLevel` | What it licenses | May it render `ACTIVE`? |
| --- | --- | --- |
| `INFERRED` | A declared `SUPPORTED` record, visibly marked as inferred | No |
| `IMPLEMENTED` | That the state model and mapping exist in code | No |
| `LAB_TESTED` | Behaviour against mocks/simulations, labelled as test output | No — never as device state |
| `HARDWARE_VERIFIED` | A read taken against the real connected device and mapped through documented rules | Yes — minimum level |
| `PERSISTENCE_VERIFIED` | That a written configuration survives disconnect/reconnect (master §24) | Yes, plus persistence claims |

**AUD-VERIFY-001** The five levels SHALL never be conflated (phase-0 §16).
**AUD-VERIFY-002** Before any UI, notification, widget, report or test output may state that a codec is `ACTIVE`, all of the following MUST hold:

```text
[ ] device identity known (master §54) and the correct protocol / transport is known
[ ] session currently connected on that transport
[ ] authoritative read of the state that carries media audio taken and mapped
    through documented CodecState rules (AUD-LAYER-002)
[ ] read performed against real hardware (HARDWARE_VERIFIED), not a mock
[ ] read within the freshness window for this session
[ ] no contradictory ACTIVE claim (AUD-LAYER-006)
[ ] claim wording checked: "LDAC Active" appears only if LDAC is ACTIVE
```

**AUD-VERIFY-003** If any step fails, the highest claim permitted is the highest rung with valid evidence — typically `NEGOTIATED`, `AVAILABLE`, `SUPPORTED` or `UNKNOWN` — and the shortfall SHALL itself be `UNKNOWN`, not `UNSUPPORTED` (master §53).
**AUD-VERIFY-004** "LDAC Active" or any codec-active claim shown while active state is unverified is a Phase 0 acceptance failure (phase-0 §17, §22 Audio block).
**AUD-VERIFY-005** `TEST-<SCOPE>-<NNN>` cases enforcing AUD-VERIFY-002 belong to `docs/phases/phase-0/testing-governance.md` and Phase 10/13/14 test plans.

## 12. Feature dependencies affecting audio (master §29)

**AUD-DEP-001** Conflicts, prerequisites, mutually exclusive modes, firmware limitations and transport limitations SHALL be evaluated by `FeatureDependencyEngine`. Audio rules here are engine-agnostic; the engine's governance is `docs/phases/phase-0/protocol-governance.md` (see Appendix A, C-04).
**AUD-DEP-002** LDAC + multipoint is the reference conflict: the pair "may not simultaneously be possible on a specific device" (master §29). When the engine reports a conflict, the app SHALL NOT render both as simultaneously `ACTIVE` or both as persistently enabled.
**AUD-DEP-003** The app SHALL NOT claim two features are active if the hardware cannot actually do both (master §29). Co-activity is established only by a single verified read window on the same session showing both `ACTIVE`, never by two independent write/enable operations.
**AUD-DEP-004** When a conflict is undocumented for a device, the app SHALL NOT advertise the combination while still rendering each individually observed state; unknown interaction is `UNKNOWN`, which is neither "compatible" nor "exclusive".
**AUD-DEP-005** Dependency verdicts SHALL carry `ProtocolConfidence`, the limiting reason (firmware, transport, codec bandwidth), and the affected `CodecState` set, and SHALL NOT disable a control silently.

## 13. Diagnostics (master §31)

Audio content of the diagnostic report — schema owned by Phase 36, field contract fixed here so later phases cannot invent it:

| Group | Fields | Unknown representation |
| --- | --- | --- |
| Transport | transport family, per-transport session state | `UNKNOWN`, plus reason (transport not exposed / not read) |
| Codec matrix | every registry codec present for the device, with all six `CodecState` values | `UNKNOWN` per state, never blank or `false` |
| Quality | sample rate, bit depth, bitrate, channel mode, quality mode, active | `UNKNOWN` per field (master §16, §23) |
| Configuration | `CONFIGURABLE` verdicts, `READ_ONLY` verdicts, OS restriction reasons, LDAC priority vs negotiated state | "not exposed by OS" recorded as a fact about the exposure, not about the codec |
| Dependencies | conflict/prerequisite verdicts and their confidence | `UNKNOWN` interaction stated as such (AUD-DEP-004) |
| Evidence and path status | per observation: source, timestamp, `VerificationLevel`, error category; `OUT_OF_MEDIA_PATH`, or any in-path software feature with its ADR id and label | failed read carries `VerificationFailed` / `Timeout` / `CodecUnavailable` / `TransportUnavailable`; an in-path feature without an ADR is reported as not permitted (master §47) |

**AUD-DIAG-001** The report SHALL distinguish "not read", "not exposed", "read failed" and "explicitly reported unsupported by the device"; the first three are `UNKNOWN` (master §53).
**AUD-DIAG-002** The report SHALL NOT contain a single "codec" string substituted for the matrix; a summary line is allowed only when it is the `ACTIVE` codec and no other codec is `ACTIVE`.
**AUD-DIAG-003** Notification and widget codec lines (master §33, §34) obey the same truth rules as the UI: "Codec: LDAC" appears only for a verified `ACTIVE` LDAC, and only values actually available are shown (master §33).
**AUD-DIAG-004** Diagnostics SHALL be sufficient to reproduce an unsupported-device claim without re-querying hardware, i.e. every `UNKNOWN` carries its reason and level.

## 14. Deliberately left to Phases 10–15

| Open question | Deferred to |
| --- | --- |
| Which Android APIs legitimately expose transport/codec state, and their OS version ceilings | Phase 10 |
| Who owns `AudioTransportState`, and its Flow/state and freshness contract | Phase 10, `specs.md` |
| Registry physical schema, codec id grammar, per-codec parameter vocabularies | Phase 11 |
| Whether `CONFIGURABLE` is per-codec or per-parameter | Phase 11/13 |
| Per-codec ranges: LDAC quality modes, aptX Adaptive variants, AAC profile detail, LC3 configuration set | Phase 12 |
| Whether LE Audio needs its own ladder beyond the six states (must not weaken master §15 by invention) | Phase 12/13 |
| Bitrate/adaptive semantics and how `ACTIVE` is kept fresh; final §16 field list | Phase 13 |
| How out-of-path behaviour is *proved* in CI (`TEST-AUDIO-<NNN>` regression harness) | Phase 14 |
| EQ/ANC domain ownership and the DSP-vs-software boundary in code | Phase 15 |
| KMP split boundary for audio models | Phase 46 |

## Appendix A — Source conflict register

| ID | Conflict | Resolution (MASTER-CONTEXT.md wins) |
| --- | --- | --- |
| C-01 | phase-0 §17 ladder `Supported / Available / Selected / Negotiated / Active` (5 names, "Selected", no `CONFIGURABLE`) vs master §15 and REQ-P0-009 `SUPPORTED / AVAILABLE / ENABLED / NEGOTIATED / ACTIVE / CONFIGURABLE` (6 states) | Six-state `CodecState` set is normative. "Selected" is an alias for the `ENABLED` rung (a user selection is an input to `ENABLED`, AUD-STATE-002). `CONFIGURABLE` is retained as an orthogonal writability property, not a seventh rung. phase-0 §17's five claims stay usable as wording if each maps to a `CodecState`. |
| C-02 | phase-0 §4 (Agent 5) output path `docs/phase-0/audio-governance.md` vs phase-0 §6 tree `docs/phases/phase-0/` | This file lives at `docs/phases/phase-0/audio-governance.md`, matching §6 and the existing repository. Orchestrator should note the convention in `decisions.md`; not architectural. |
| C-03 | phase-0 §16 spellings `LAB-TESTED`, `HARDWARE-VERIFIED`, `PERSISTENCE-VERIFIED` and two names for the same ladder (master §27 `ProtocolConfidence`) | Contract spellings `LAB_TESTED` / `HARDWARE_VERIFIED` / `PERSISTENCE_VERIFIED`. `ProtocolConfidence` qualifies protocol knowledge; `VerificationLevel` qualifies a specific capability or audio claim. Same five values, never conflated (AUD-VERIFY-001). |
| C-04 | §12 cross-references `docs/phases/phase-0/protocol-governance.md` for the dependency engine, but that file is not an assigned deliverable in phase-0 §4 | Surfaced, not invented. Orchestrator must assign it or fold protocol/dependency governance into `architecture-governance.md`; until then audio rules stay engine-agnostic per AUD-DEP-001. |
| C-05 | master §16 prose observables (connection priority, adaptive mode, LE Audio/high-quality/low-latency state) exceed the 8-field `AudioTransportState` and REQ-P0-011's list | The 8 fields are the normative minimum (AUD-QUAL-001); the extras are permitted-when-exposed, prohibited-when-fabricated (AUD-QUAL-003), finalised in Phase 13. |
| C-06 | master §47 example id `ADR-001` vs ID grammar `ADR-<SCOPE>-<NNN>` | phase-0 §13 `ADR-P0-002` is the canonical instance of the media-path decision; cross-referenced in AUD-PATH-004. |

## Appendix B — Traceability

| Source requirement / acceptance item | Where satisfied |
| --- | --- |
| REQ-P0-003 Audio Path Isolation; master §47 `ADR-001` | §2 (AUD-PATH-001…007) |
| REQ-P0-009 Codec Truth; phase-0 §22 "active vs supported" | §6 (AUD-STATE-001…005, ladder, two worked examples) |
| REQ-P0-010 Audio Codec Coverage; §22 AAC / LDAC / aptX / LC3 blocks | §4 (AUD-XPORT), §5 (AUD-REG-001…006) |
| REQ-P0-011 Audio Quality State; master §16, §23 | §8 (AUD-QUAL-001…006) |
| phase-0 §17 Audio Verification Rule | §11 (AUD-VERIFY-001…005) |
| phase-0 §8.2/§8.3/§8.4 layer model | §3 (AUD-LAYER-001…006) |
| master §29 feature dependencies | §12 (AUD-DEP-001…005) |
| master §31 diagnostics | §13 (AUD-DIAG-001…004) |
| phase-0 §23 failure conditions | §1 (no implementation), §2/§10 (no fake hardware), §6/§11 (no codec-state omission) |

**Sibling Phase 0 documents:** `docs/phases/phase-0/architecture-governance.md`, `docs/phases/phase-0/protocol-governance.md` (see C-04), `docs/phases/phase-0/testing-governance.md`, `docs/phases/phase-0/security-governance.md`, `docs/phases/phase-0/repository-audit.md`, `docs/phases/phase-0/git-workflow.md`, `docs/phases/phase-0/requirements.md`, `docs/phases/phase-0/specs.md`, `docs/phases/phase-0/task-list.md`, `docs/phases/phase-0/test-plan.md`, `docs/phases/phase-0/validation.md`, `docs/phases/phase-0/decisions.md`, `docs/phases/phase-0/risk-register.md` (RISK-005 codec control restrictions, RISK-006 LE Audio platform differences, RISK-008 firmware incompatibility, RISK-011/012 cross-device and cross-phone differences).

**End of audio governance. Phase 0 rulebook only: no audio subsystem is implemented here.**
