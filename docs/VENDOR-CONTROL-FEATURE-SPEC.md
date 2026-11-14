# Earbud Control Features, Protocols and Model Implementation Specification

**Purpose:** define the features, protocol operations, model-specific configuration, UI visibility, confirmation behaviour and validation requirements for the OPPO / OnePlus / realme RFCOMM control family.

**Document type:** feature-addition and implementation specification. This is not a generic Bluetooth survey and does not claim every listed device has been physically tested.

## 1. Scope and implementation contract

The first target is the existing realme Buds T300 integration. Preserve its confirmed behaviour and use the full model catalogue below to expand compatibility. Never replace T300 commands, mode slots, gesture IDs or EQ mappings with values taken from another model.

This specification covers:
- Bluetooth control transport, framing and slice reassembly.
- Device identity, firmware and capability discovery.
- Event discovery and registration.
- Battery, case and wearing telemetry.
- ANC, transparency, adaptive modes and the separate ANC-hold cycle.
- Physical touch/button mapping, gesture editing and call gestures.
- Feature switches, game controls, EQ, BassWave and codec controls.
- Spatial audio, device management, find functions and hearing features.
- Model-driven UI visibility, notification/widget actions and background state.
- Evidence and validation requirements.
- Reverse-engineering work still required for vendor-app parity.

### Mandatory rules

1. The device-reported state is authoritative. A tap is a request, not proof of application.
2. An ACK with status zero means accepted, not necessarily applied.
3. Model metadata describes what to query and how to interpret it; live device replies decide what is available in the current session.
4. No command, payload, bit position, gesture action or mode name may be guessed and enabled for writes.
5. ANC write mapping and ANC report mapping are separate model-specific data.
6. ANC hold-cycle membership is separate from the gesture function that invokes the cycle.
7. Every exact model has its own mode tree, gesture menu, call menu, EQ map, codec list and exceptions.
8. Every UI surface consumes one shared capability/state snapshot.
9. Missing response is not automatically off or unsupported. Preserve UNKNOWN when evidence is insufficient.
10. Keep media audio untouched. Hardware control is a separate control plane.

### Evidence states

| State | Meaning | Permitted use |
|---|---|---|
| INFERRED | Plausible interpretation or incomplete trace | Research only |
| VENDOR-DERIVED | Visible in companion-app UI or data | Candidate profile, not hardware guarantee |
| CAPTURED | Exact bytes observed for a named model | Implement behind a model guard; test before enabling |
| IMPLEMENTED | Parser/builder/UI exists and local tests pass | Does not prove hardware acceptance |
| LAB-TESTED | Offline fixture/replay tests pass | Does not prove physical behaviour |
| HARDWARE-VERIFIED | Exact model and firmware apply operation and return resulting state/event | Enable for that exact scope |
| PERSISTENCE-VERIFIED | State survives the relevant disconnect/case/restart lifecycle | May claim persistence for that scope |
| UNSUPPORTED | Positive evidence the device/platform cannot do it | Hide active control |
| UNKNOWN | Evidence incomplete | Do not enable writes |

Evidence belongs to an exact model + firmware + feature + operation, not just a manufacturer.

## 2. Transport and connection ownership

### 2.1 Primary transport

This control family uses Bluetooth Classic RFCOMM/SPP for proprietary control messages. It is separate from A2DP audio and BLE battery advertisements.

Observed service UUIDs:
- Primary: 0000079A-D102-11E1-9B23-00025B00A5A5
- Fallback: 00001107-D102-11E1-9B23-00025B00A5A5

Represent connection attempts as a transport profile:
1. Try primary UUID with secure RFCOMM.
2. Try fallback UUID securely.
3. Try insecure variants only where platform/profile permits.
4. Use an SDP-derived RFCOMM channel only when established by evidence.
5. Bound connect attempts and close failed sockets.
6. Reconnect through one explicit policy.
7. Close session on disconnect, user disconnect, Bluetooth-off or service shutdown.

Reference implementation timings include a 12-second connection watchdog and bounded initial/reconnect attempts. Keep timing configurable and test it on supported Android versions.

### 2.2 Single control-channel owner

Only one application may own this control endpoint at a time. Audio can stay connected while another app owns the control channel.

Requirements:
- One authoritative control-session manager for the entire app.
- App screen, widget, tile and notification submit work to that manager; never open independent sockets.
- Distinguish audio connection from control availability.
- A failed control socket does not prove the earbuds are disconnected.
- Do not repeatedly compete with another companion app.
- A separate Fast Pair stream is battery-only and only a fallback where verified.

### 2.3 Fast Pair battery-only fallback

A separate Fast Pair Message Stream service has been observed:

df21fe2c-2515-4fdb-8886-f12c4d67927c

Message envelope:

~~~text
[group] [code] [length: u16 big-endian] [data]
~~~

It may report model ID, Bluetooth address and left/right/case levels. Observed raw level 0x7F means unknown; the high bit indicates charging.

This path is read-only. Do not use it for ANC, EQ, gesture or other writes.

## 3. Frame parsing and reassembly

The simple scan parser must be replaced with a bounded streaming parser. Bluetooth reads are arbitrary chunks: one read may contain half a frame, several frames or noise before a frame.

### 3.1 Frame layout

~~~text
AA
[TotalLength: LEB128]
[SliceType]
[Reserved = 00]
[SliceCounter when sliced]
[Command: u16 little-endian]
[Sequence: u8]
[PayloadLength: u16 little-endian]
[Payload]
~~~

| Field | Meaning |
|---|---|
| AA | Start marker |
| TotalLength | LEB128 length; seven bits per byte |
| SliceType | 00 whole, 01 first, 02 middle, 03 final |
| Reserved | Observed zero |
| SliceCounter | Present on slices |
| Command | Two-byte little-endian command ID |
| Sequence | Reply correlation byte; FF for unsolicited pushes |
| PayloadLength | Two-byte little-endian payload length |
| Payload | Command-specific bytes |

Whole-message total length is seven plus payload length. Lengths above 127 require multi-byte LEB128. Never assume one length byte.

### 3.2 Required streaming algorithm

~~~text
append(chunk):
    validate chunk length
    append to bounded input buffer

    while bytes remain:
        find next AA marker
        discard and record preceding noise

        decode LEB128:
            incomplete -> retain bytes and wait
            invalid/too long -> discard one byte and resynchronise

        decode slice type and total length
        reject invalid minimum length
        reject frame above configured maximum

        if complete frame is not buffered:
            wait for more input

        consume exactly one complete frame

        if whole:
            validate header and exact payload length
            emit message

        if first slice:
            reset stale reassembly
            validate initial counter
            begin bounded reassembly

        if middle/final:
            require active reassembly
            require expected counter/order
            append within reassembly limit
            reject duplicate, reordered or oversized slices

        if final:
            validate reconstructed header and payload length
            emit complete message
            clear reassembly
~~~

### 3.3 Parser hardening

- Handle every possible split point of a frame.
- Handle multiple frames in one read.
- Resynchronise after noise and malformed headers.
- Support up to three LEB128 length bytes for this protocol.
- Enforce an explicit per-frame limit. The inspected Kotlin framer uses 2048 bytes; choose one documented OmniBuds limit and test it.
- Enforce a separate maximum reassembled-message size.
- Validate slice counters and order. Do not blindly concatenate every middle/final slice.
- Reset reassembly on a new first slice, timeout, disconnect or invalid sequence.
- Validate that declared payload length matches available bytes. Do not silently shorten truncated payloads.
- Preserve raw bytes and structured parse failures for diagnostics.
- Parser failures must not crash the Bluetooth service or corrupt the next frame.
- Add property-based tests for random chunking, concatenated frames, noise, malformed LEB128, truncated payloads, reordered/duplicate slices and buffer overflow.

### 3.4 Sliced writes

Observed default outgoing frame budget is 660 bytes, subject to smaller device-reported limits. Send one slice at a time, wait for transport write completion, apply the profile's inter-slice gap and stop on failure. A failed slice invalidates the entire message.

## 4. Sequence numbers, replies and command families

### 4.1 Sequence ranges

| Value/range | Observed use |
|---|---|
| 00 | Plain status/connect-time reads |
| 01–BF | General request counter |
| C0–DF | Feature-operation engine |
| F0 | Battery query |
| F2 | Wearing query |
| FF | Unsolicited push |

Normal replies echo the request sequence. Reply command normally equals request command with bit 15 set, e.g. 0106 → 8106 and 0403 → 8403.

Correlate by both sequence and command. Do not correlate by command alone. Reserve sequence ranges to avoid collisions.

Some model entries carry a seqless exception: the device does not echo the sequence as expected. Use a model-specific serialized fallback correlation policy and mark it weakly correlated. Never apply it globally.

### 4.2 Command families

| Range | Role |
|---|---|
| 01xx | Queries/reads |
| 02xx | Event discovery, registration and pushes |
| 03xx | Firmware update; outside enabled scope |
| 04xx | Writes |
| 05xx | Requests/events initiated by earbuds |
| 0Fxx | Private protocol; no use without evidence |

### 4.3 Command bitmap and feature status

Handshake 0100 returns a command bitmap. Bits are least-significant-bit first. A set bit enables associated commands. Commands absent from the bitmap must not be sent, apart from documented always-available commands.

Feature query 010D contains a count followed by feature IDs. Reply contains ID/value pairs:
- ID present: firmware reports the feature.
- ID absent: the queried firmware did not report the feature.
- ID present with zero: feature reported but off.
- no reply: unknown.

The model profile chooses what to query. It does not override the live response.

## 5. Model identity and connection initialisation

### 5.1 Identification

Use Bluetooth name, product ID and firmware:
- Product ID is 24-bit little-endian from query 0103.
- Firmware query 0105 reports left/right/case version tuples.
- Colour/regional variants may normalise to one model record.

Matching order:
1. Exact name + product-ID match.
2. Exact Bluetooth-name match.
3. Product-ID match.
4. Safe, unique normalised-name fallback.

Retain all identity signals and the rule that selected the model. If the physical device changes, discard previous model capabilities, feature state, ANC state, gestures, EQ snapshot and event assumptions before rendering the new device.

### 5.2 Initialise one session

1. Handshake 0100 and parse bitmap.
2. Query product ID 0103.
3. Discover offered event codes 0200.
4. Select event subscriptions from offered codes and model capabilities.
5. Subscribe with 0205 or model-appropriate single-event registration 0201.
6. Query feature state 010D using model query list.
7. Read ANC current mode 010C 01 01 if supported.
8. Read hold-cycle membership separately.
9. Read battery 0106 and wearing 0109.
10. Read gestures 0108 when supported.
11. Read firmware 0105.
12. Read EQ/spatial/codec/game sound only when bitmap and profile allow.
13. Publish one coherent capability/state snapshot to app, widgets, notification and quick controls.

Batch query 012F may reduce requests where advertised. Validate every embedded command and length; do not assume all models support it.

### 5.3 Event discovery and registration

The device may reply to 0200 with 8200 containing offered event codes.

| Event | Meaning | Subscribe when |
|---|---|---|
| 01 | Battery | Offered |
| 02 | Wearing | Offered |
| 03 | ANC | Offered |
| 04 | Fit result | Fit command and event offered |
| 08 | Hearing/scan progress | Hearing workflow and event offered |
| 0B | Personal ANC result | Personal ANC command and event offered |
| F1 | Gesture fired | Diagnostic only; not state authority |

Subscription payload is count-first. Two events use count 02 followed by IDs 01 and 02. Single-event registration uses 0201. Register only event codes the device offered.

A missing event in the app log is not proof the firmware never pushes it. Verify offered event list, subscription and acknowledgement first.

## 6. Capability model and control visibility

The runtime decision uses three inputs:

1. Model profile: what to query, mode tables, menus, presets and exceptions.
2. Command bitmap: what connected firmware accepts.
3. Feature-state response: which switches connected firmware reports.

A row is enabled only when model data, live command support, feature status and platform capability are present. Model flags alone do not expose a read/write feature except explicit write-only cases.

| Row/control | Visibility condition |
|---|---|
| Battery | Valid cell observation and model battery layout |
| ANC segmented control | Model ANC tree + bitmap permits current-mode read/write |
| Gesture settings | Model gesture menu + read/write command |
| On-call editor | Non-empty call gesture menu |
| Find earbuds | Find command + noFind is false |
| Fit test | Fit command/event |
| EQ | Model EQ profile + matching command bits |
| BassWave | Correct single/multi-band profile + matching commands |
| Wind reduction | Live feature and model query rule |
| Game/low latency | Feature 06 or model-specific 28 |
| Game sound type | Feature 27 + command 0423 |
| Game EQ | Feature 21 + command 0420 |
| Codec picker | Model codec-picker + command 0123 + valid returned list |
| High-quality audio | Feature 18 and model rule; not a codec picker |
| Spatial | Device spatial mode or compatible phone spatializer |
| Hearing profile | Feature 0B + workflow commands |
| Personal ANC | Model flag + feature/command support |
| Dual-device list | Feature 11 + device query |
| Device priority | Model priority action + commands 0429/0132 |
| Triple connection | Explicit triple-device property |
| Find phone | Model findPhone flag and request evidence |
| Tap sensitivity | Model flag + 0133/042D |
| Alert volume | 0130/0427 |
| Head gesture type | Feature and command 0134/0431 |
| Write-only controls | Explicit model property; label state as last-requested, not confirmed |

All surfaces must consume one shared state. No independent support logic in activity/widget/notification. On disconnect, show last confirmed values as stale/dimmed and disable writes. On model change, clear prior model rows before drawing the new profile.

## 7. Feature protocol catalogue

### 7.1 Battery and case

| Operation | Identifier | Payload |
|---|---|---|
| Battery read | 0106 → 8106 | status, count, part/raw pairs |
| Battery event | 0204 event 01 | count, part/raw pairs |
| Fast Pair fallback | Separate stream | left/right/case bytes |

Part IDs: 01 left, 02 right, 03 case. Observed raw value: bits 0–6 level, bit 7 charging. Zero/unknown must not become 0%.

Requirements:
- Split/combined bud display comes from the model battery contract.
- Case can be separate, hidden or conditional.
- Case data can be live, last-known or absent when both buds are out.
- Do not show a case tile just because the product has a case.
- Track freshness and source (RFCOMM, Fast Pair or BLE).
- Poll only as fallback and never starve a user write.

### 7.2 Wearing and auto-pause

| Operation | Identifier |
|---|---|
| Wearing query | 0109 → 8109 |
| Wearing event | 0204 event 02 |
| Firmware auto-pause | Feature 04 |

Parts: 01 left, 02 right, 03 case. Observed values include 0 closed-case, 1/5 out, 3/7 in-ear, 4 in-case/open-lid. Some models lack a usable sensor. Never infer wear from Bluetooth connection state. App smart pause is distinct from firmware feature 04.

### 7.3 ANC, transparency and hold cycle

| Operation | Identifier |
|---|---|
| Read active mode | 010C 01 01 |
| Write active mode | 0404 01 01 + little-endian mode mask |
| Active mode event | 0204 event 03, payload 01 01 + mask |
| Read hold-cycle members | 010C 02 + kind |
| Write hold-cycle members | 0404 02 + kind + mask |
| Smart level | 010C 04 01 / event 03 04 01 + mask |

Two maps are mandatory:
- SET: UI mode → write bit/slot.
- REPORT: raw response/event → UI mode.

The event discriminator is critical:
- 03 01 01 + mask = active mode.
- 03 02 + hold type + mask = hold-cycle echo.
- 03 04 01 + mask = Smart-chosen level.

A real mode-change push follows a bud-side gesture after subscription. Gesture-fired F1 is diagnostic; the active-mode push/read is the state source.

**T300 baseline (product ID 062412):**
- mode type 5, slot 3: plain ANC.
- mode type 1, slot 0: Off.
- mode type 2, slot 1: Transparency.
- Hold cycle: shared type, current profile mask 8833.
- Never substitute OnePlus Buds 4 or another realme model's slots.

ANC UI:
- show only modes in exact model tree;
- use last device-confirmed mode;
- edit hold-cycle members separately;
- on refusal, keep old state and explain condition;
- Smart level only when reported;
- disconnected value remains stale.

### 7.4 Physical gesture/button editing

| Operation | Identifier |
|---|---|
| Read full table | 0108 → 8108 |
| Write full table | 0401 |
| Gesture-fired diagnostic | 0204 event F1 |

Entry layout: device, button, action, function.

Device: 01 left, 02 right, 04 both.
Button: 01 primary, 06 on-call.
Action: 01 single, 02 double, 03 triple, 04 hold, 05 slide, 06 long hold.

Observed function IDs:

| ID | Meaning |
|---|---|
| 00 | None |
| 01 | Play/pause |
| 02 | Internal listening-music; do not show by default |
| 03 | Voice assistant |
| 05 / 06 | Previous / next |
| 07 | Volume/slide |
| 08 | ANC cycle |
| 09 | Favourite music |
| 0A | Track/slide action |
| 0B / 0C | Volume up/down |
| 0D | Switch devices |
| 11 | Game mode |
| 12 | Zen mode |
| 16 | Collect music |
| 19 | AI summary |
| 1A / 1B | AI translation |
| 1C | Decline call |
| 1D | Answer/end call |
| 20 | Spy tap; meaning not established |

Unknown function bytes remain raw and hidden until verified.

Gesture option masks use one shared dictionary: bit 1 voice assistant; bit 4 play/pause; bits 8/16 volume up/down; bits 32/64 previous/next; bit 128 ANC cycle; bit 256 favourite; bit 512 none; bit 1024 volume; bit 2048 track switch; bit 4096 switch devices; bit 8192 game; higher defined bits are model-specific.

UI:
- L/R selector only for independent buds.
- One-button models hide L/R.
- Both-hold row only when profile enables it.
- On-call section only when callGestures exists.
- Multi-select only where the profile permits cycling; enforce minSelected.
- Build from last valid full table; update only target slot(s), write whole table, read back and diff.
- Never clear ANC hold function to disable cycle membership.

On-call group uses button 06 and may use device 04 for shared writes. Captured meanings include double-tap answer/end and long-hold decline. Expose only rows supplied by each model's callGestures.

### 7.5 Feature switches

Write: 0403 + feature ID + 01/00. Read: 010D + count + IDs. Live change: 0503 + count + ID/value pairs.

| ID | Feature | UI/logic |
|---|---|---|
| 04 | Wear auto-pause | Show if returned |
| 05 | Unknown/internal | Never expose |
| 06 | Low latency/game | Default unless model uses 28 |
| 07 | Party mode | Unverified; no enable without capture |
| 08 | Auto-answer | Model-specific, may be write-only |
| 09 | Voice enhancement | Requires model meaning and live status |
| 0B | Hearing profile | Opens workflow, not generic toggle |
| 0C | Personal ANC applied | Device result state, not generic toggle |
| 0F | Zen mode | Not initial surface |
| 11 | Dual connection | Show if returned |
| 13 | Sound record state | No generic switch |
| 16 | Lost reminder | Model-specific |
| 17 | Power saving | Restart/reconfigure lifecycle |
| 18 | High-quality audio | Quality switch, not codec selection |
| 1A | Wind noise | Model-specific query/support |
| 1B | Spatial/3D | Device or phone-side rendering |
| 1C | Smart volume | Show if returned |
| 1D | BassWave/dynamic sound | Correct level protocol variant |
| 1E | Log collection | Not consumer control |
| 21 | Game EQ | Context-aware flow |
| 26 | Find phone | Model-specific request |
| 27 | Game sound effects | Separate from latency |
| 28 | Main game mode | Selected models |
| 30 | Adaptive volume | Show if returned |
| 31 | Adaptive ear | Show if returned |
| 32 | Speech perception | Show if returned |
| 35 | Hold volume | Show if returned |
| 36 | Virtual bass | Model-specific |
| 37 | Swift Pair | Show if returned |
| 38 | Hearing optimisation | Confirm meaning per model |
| 3A | Sleep pause | Show if returned |
| 3B | Head-motion gestures | Separate type setting |

Other IDs in this family are server/system-dependent, unused or insufficiently understood. Keep AI, account, camera, firmware-update, voice-wakeup and health operations out of the active UI until a complete verified workflow exists.

### 7.6 Write queue and confirmation

Operation shape: key, ordered writes, confirmation policy READ/PUSH/ACK, reconnect-required flag.

1. Mark busy; do not move displayed value.
2. Send one write with unique sequence.
3. Match ACK by sequence and command.
4. Status 00 sends next write.
5. Status 01 means capability absent.
6. Other non-zero means refused under current condition.
7. READ confirmation sends a new sequenced query; only matching reply confirms.
8. PUSH confirmation requires genuine event; an ordinary read is not a push.
9. ACK confirmation only for proven write-only operations.
10. Restart operation remains pending through reconnect and confirms from post-reconnect report.
11. Partial multi-write failure triggers a fresh read of affected state.
12. On timeout/refusal/disconnect, retain last confirmed state.
13. Late reply cannot complete a newer operation.

Reference timings observed: about 3 seconds for ACK/read/push and up to 15 seconds for restart. Make configurable. Some devices push before ACK; update state cache but do not complete operation before required ACK.

### 7.7 EQ and BassWave

| Operation | Identifier |
|---|---|
| Current preset | 010F |
| Custom preset list | 0122 |
| Built-in preset | 0406 + slot |
| Custom create/save/delete | 0418 |
| EQ changed | 0504 |
| Custom list changed | 0506 |
| BassWave switch | Feature 1D |
| Single BassWave level | 041B FB 05 + level |
| Dynamic bands read | 012C |
| Dynamic band changed | 0508 |

Custom preset payload:
~~~text
[action][two-byte gain-range tag][preset ID][name length][UTF-8 name][band count]
repeat: [frequency u16 little-endian][gain signed byte]
~~~

Actions: 01 create (send ID 0), 02 save/select, 03 delete. Re-read the list after create/delete because IDs may be assigned or renumbered.

Six-band default frequencies: 62, 250, 1000, 4000, 8000, 16000 Hz. Ten-band profiles use 31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000 Hz. Use exact model frequencies and preset limit.

Three-band dynamic sound: band 1 low, 2 middle, 3 high. Band ID is part of write. Some models read via 012C rather than 0124. Single-band BassWave uses its own path.

EQ UI:
- preset chips use model mode/slot map;
- custom EQ appears only if customEq and command exist;
- respect model band count/frequencies/preset limit;
- read back after create/delete/write;
- cache by physical identity;
- stale reads cannot override a newer selection.

### 7.8 Codec/high-quality audio

| ID | Codec |
|---|---|
| 1 | SBC |
| 2 | AAC |
| 3 | LDAC |
| 4 | aptX |
| 5 | aptX HD |
| 6 | aptX Adaptive |
| 7 | LHDC |
| 8 | LHDC V5 |

Read current codec 0114; offered codec mask 0123. The mask is little-endian and bit k represents codec k+1. Trust only on models whose profile establishes a valid list.

Codec picker write: 041A + codec + Hi-Res flag + 00. Feature 18 is a quality mode, not a phone codec selector. Android Bluetooth negotiates the active codec. Some devices refuse High-quality audio while AAC/SBC is active; others accept it without a guaranteed audible change. Explain the dependency and handle restart separately.

### 7.9 Spatial/head tracking

Two mechanisms:
1. Basic spatial switch, often feature 1B.
2. Typed spatial mode via 012A read, 0422 write and 0510 event.

Observed types: 0 off, 1 fixed, 2 head tracking. Show only modes in the exact profile. Some effects are rendered in earbuds; others require a compatible phone spatializer. Phone-side rendering is not an RFCOMM write. Gate on Android API, platform API and OEM support. Spatial and High-quality audio may conflict; sequence and confirm both writes.

### 7.10 Game and game EQ

| Function | Identifier |
|---|---|
| Low latency/game | Feature 06 or model-specific 28 |
| Game push | 0204 event 05 |
| Game sound read | 012B |
| Game sound write | 0423 + type + 01 |
| Game EQ | Feature 21 + command 0420 |

Where game-sound commands exist, keep main game and low-latency controls separate. Some models put game state at a different event payload offset; use model profile offset. Some models require READ confirmation instead of PUSH.

Game EQ requires feature 21, command 0420 and Android Usage Access. Detect foreground games using app category where available; send game-enter/leave state. Stop when disconnected, disabled, permission-revoked or asleep. No microphone/audio capture is required.

### 7.11 Dual/triple device

| Operation | Identifier |
|---|---|
| Device list | 0112 |
| Device list event | 0204 event 06 |
| Dual switch | Feature 11 |
| Phone/auto-switch state | 0413 |
| Device manager | 0429 |
| Preferred device read | 0132 |

List contains MAC, name, state and flags. Returned MAC order is reversed from write order. State 02 connected, 00 disconnected. Disconnected entries may remain. Show connect/disconnect/priority only where model dualDevice operations and bitmap permit. Triple label only when tripleDevice is explicit.

### 7.12 Find earbuds/phone

Find earbuds: 0400 01 starts and 0400 00 stops. Rings both buds. Warn if likely in ear. Some devices ACK but produce no tone; noFind suppresses row.

Find phone: model-flagged devices may send 0502 05 to ring the phone and 0502 06 to stop. Phone plays local sound, exposes stop action and uses bounded timeout. Show only with findPhone and request evidence; hide on desktop.

### 7.13 Alert volume, tap sensitivity and head gestures

| Setting | Read | Write | Event |
|---|---|---|---|
| Prompt volume | 0130 | 0427 level 1–10 | 0204 event 0F |
| Tap sensitivity | 0133 | 042D level 1–5 | 0204 event 0F |
| Head gesture type | 0134 | 0431 type | 0204 event F5 |

Send alert-volume only on slider release because each write may play a prompt. Tap sensitivity lower means more sensitive. Offer reset if default read exists. Head gesture row requires matching feature and commands.

### 7.14 Hearing, fit and personal ANC

Hearing profile:
- Feature 0B;
- active record 0115;
- scan 011E;
- test/scan 040D;
- record/tone 040E;
- restore 0411;
- scan data 0415;
- filters 0116/011F;
- progress event 08, scan result 0E.

Fit:
- 0405 01 start, 0405 00 stop;
- event 04 carries per-bud result;
- require subscription;
- stop on dialog close/deadline;
- require reliable wear semantics.

Personal ANC:
- 011A reads stored result;
- 0412 actions: test, use stored, cancel;
- event 0B reports result;
- show only with model flag, bitmap and live status;
- vendor-derived/unverified models remain disabled.

## 8. UI rules for all surfaces

### Home
- Battery per model contract.
- ANC from model mode tree.
- Low latency/game, wind, spatial, hearing, EQ and dual connection only with live support.
- noFind suppresses Find earbuds.
- Hide spatial if neither device-side nor phone-side rendering is usable.

### Settings
- Gesture editor from model gesture menu and physical topology.
- Call editor only when callGestures exists.
- Fit test only with command/event.
- Alert volume/tap sensitivity only with required commands.
- Personal ANC/hearing only with exact model config.
- Codec picker, device priority and triple label only under model-specific gates.

### Notification/widget/quick settings
- One shared service/session/operation queue.
- Notification displays current battery and up to three model-valid actions.
- Tiles map only to supported features.
- Widget ANC cycling uses model's supported modes, not a generic three-mode assumption.
- Disconnected state shows stale/last-confirmed values and disables writes.

## 9. Complete model catalogue

This is the full model-profile snapshot. It is profile data, not a claim that every feature has been physically tested.

Conventions:
- ANC: mode type @ write slot, optional child variants in brackets.
- Gesture/call rows: gesture key : option-mask; /minN is minimum selected options.
- EQ: kind, preset mode-to-slot map, custom support, band variants.
- DEFAULT status query means default query policy plus profile extras.
- Confirmed is the explicit confirmation list in the record; dash means none recorded.

| Product ID | Model | ANC tree | Gestures | Call gestures | EQ | Codecs | Special properties | Status query | Confirmed |
|---|---|---|---|---|---|---|---|---|---|
| 050010 | OPPO Enco Quiet | — | — | — | kind=0 | — | — | DEFAULT | — |
| 050410 | OPPO Enco M31 | — | — | — | kind=0 | ldac, aac, sbc | — | 05 | — |
| 050412 | realme Buds Wireless 2S | — | — | — | kind=0 | aac | — | DEFAULT | — |
| 050414 | OnePlus Bullets Wireless Z2 | — | — | — | kind=0 | aac, sbc | — | DEFAULT | — |
| 050810 | OPPO Enco M32 | — | — | — | kind=0 | aac, sbc | — | DEFAULT | — |
| 050812 | realme Buds Wireless 3 | 5@3; 1@0; 2@1 | — | — | kind=1; slots=103@2 101@0 104@3 102@1; bass-engine | aac, sbc | windMode; bassEngine; findPhone; spatialToggle | 05 06 11 1B | battery, anc, game, hires, spatial, golden, eq, dual, wear |
| 050C10 | OPPO Enco M33 | 5@0; 1@1; 2@2 | 25:fixed/min2; 26:0x1200 | 36:0x80000; 37:0x40000 | kind=2; slots=1@0 13@2 3@3 2@1; bass-engine | aac, sbc | bassEngine | 05 1D | — |
| 050C12 | realme Buds Wireless 3 Neo | — | 1:0x2265; 2:0x2265; 3:0x2265; 4:0x2265 | — | kind=1; slots=103@2 101@0 104@3 102@1; bass-engine | aac, sbc | bassEngine; findPhone; autoAnswer; oneButton | 05 06 11 | — |
| 050C14 | OnePlus BulletsWireless Z2 ANC | 5@0; 1@1; 2@2 | 25:fixed; 26:0x1200 | 36:0x80000; 37:0x40000 | kind=4; slots=11@0 13@2 14@3 12@1; custom(max 3); bass-engine | — | bassEngine; customEq; customEqLimit=3 | 05 1D | — |
| 051014 | OnePlus Bullets Wireless Z3 | — | 1:0x4; 2:0x40; 3:0x20; 4:0x1 | 1:0x80000; 4:0x40000 | kind=4; slots=11@0 14@3 12@1 13@2; bass-engine | aac, sbc | bassEngine; customEqLimit=3 | 05 1B 1D | battery, spatial, eq |
| 051412 | realme Buds Wireless 5 ANC | 5@3[7@5 4@3 8@4 3@2]; 1@0; 2@1 | 1:0x2265; 2:0x2265; 3:0x2265; 4:0x2265 | — | kind=1; slots=103@2 101@0 104@3 102@1; dynamic=3 | aac | windMode; findPhone; spatialToggle; autoAnswer; voiceBoost; oneButton | 05 06 09 11 1B | — |
| 051812 | realme Buds Wireless 6 Neo | — | 1:0x2265; 2:0x2265; 3:0x2265; 4:0x2265 | — | kind=1; slots=103@2 101@0 104@3 102@1; dynamic=3 | — | findPhone; spatialToggle; autoAnswer; oneButton | 05 06 11 1B | — |
| 051C52 | realme Buds Wireless 6 | — | 1:0x2265; 2:0x2265; 3:0x2265; 4:0x2265 | — | kind=1; slots=103@2 101@0 104@3 102@1; dynamic=3 | — | findPhone; spatialToggle; autoAnswer; oneButton | 05 06 11 1B | — |
| 052052 | realme Buds Wireless 6 ANC | 5@3[7@5 4@3 8@4 3@2]; 1@0; 2@1 | 1:0x2265; 2:0x2265; 3:0x2265; 4:0x2265 | — | kind=1; slots=103@2 101@0 104@3 102@1; dynamic=3 | — | windMode; findPhone; spatialToggle; autoAnswer; voiceBoost; oneButton | 05 06 09 11 1B | — |
| 060020 | realme Buds T100 | — | — | — | kind=0 | aac, sbc | seqless | 05 06 0B 09 | battery, game, golden |
| 060410 | OPPO Enco Free | — | 2:0x7; 7:fixed; 8:fixed | — | kind=0 | — | — | 05 | — |
| 060412 | OPPO O-Free | — | — | — | kind=0 | aptx-hd, aac, sbc | — | DEFAULT | — |
| 060414 | OnePlus Buds | — | 2:0x65 | — | kind=0 | aac, sbc | oldTrackBytes | 05 | — |
| 060810 | OPPO Enco W31 | — | — | — | kind=0 | — | — | 05 | — |
| 060812 | realme Buds Air 3 | — | — | — | kind=0 | aac, sbc | — | DEFAULT | — |
| 060814 | OnePlus Buds Z | — | 2:0x65 | — | kind=0 | — | oldTrackBytes | 05 | — |
| 060C10 | OPPO Enco W51 | — | 2:0x2E7; 3:0x201 | — | kind=0 | — | — | 05 04 | — |
| 060C12 | realme Buds Q2s | — | — | — | kind=0 | aac, sbc | — | DEFAULT | — |
| 060C14 | OnePlus Buds Pro | 7@4; 4@3; 3@2; 1@0; 2@1 | 3:0x21; 11:0x80; 14:fixed; 15:fixed | — | kind=0; slots=11@0 13@3 14@2 12@1; custom(max 3) | lhdc, aac, sbc | customEq; holdAncOne | 05 04 0B 0F 11 | — |
| 061010 | OPPO Enco W11 | — | — | — | kind=0 | aac, sbc | — | DEFAULT | — |
| 061012 | realme Buds Air 3S | — | 2:0x27D; 3:0x27D; 4:0x27D | — | kind=1; slots=103@2 101@0 104@3 102@1; custom(max 3) | aac | customEq; bothHold | 05 06 | — |
| 061014 | OnePlus Buds Z2 | 4@3; 3@2; 1@0; 2@1 | 1:0x264; 2:0x265; 3:0x265; 11:0x80; 6:0x1200 | — | kind=0; slots=11@0 13@3 14@2 12@1; custom(max 3) | aac, sbc | customEq | 05 04 | battery, anc, game, gestures, wear, find |
| 061410 | OPPO Enco X | 4@3; 3@2; 1@0; 2@1 | 2:0x264; 3:0x203; 5:0xE00; 11:0x80; 6:0x1200 | — | kind=1; slots=5@0 6@1 7@2 | lhdc, aac, sbc | — | 05 04 0B 18 | battery, anc, hires, golden, eq, gestures, wear, find |
| 061412 | realme Buds T100 | — | — | — | kind=0 | aac, sbc | seqless | DEFAULT | battery, game, golden |
| 061414 | OnePlus Nord Buds | — | 1:0x204; 2:0x265; 3:0x261; 6:0x1200 | — | kind=4; slots=11@0 13@3 14@2 12@1; custom(max 3) | aac, sbc | customEq | 05 06 | — |
| 061810 | OPPO Enco Air | — | 2:0x267; 3:0x2201; 4:0x218; 13:fixed | — | kind=0 | aac, sbc | — | 05 | — |
| 061812 | realme Buds Air 3 Neo | — | — | — | kind=0 | aac | — | DEFAULT | battery |
| 061814 | OnePlus Buds N | — | 1:0x204; 2:0x265; 3:0x261; 6:0x1200 | — | kind=4; slots=11@0 13@3 14@2 12@1; custom(max 3) | aac, sbc | customEq | 05 06 | — |
| 061C10 | OPPO Enco Play | — | 2:0x267; 3:0x2201; 4:0x218; 13:fixed | — | kind=0 | aac, sbc | voiceBoost | 05 09 | — |
| 061C12 | realme Buds Air 5 Pro | 5@3[7@5 4@3 8@4 3@2]; 1@0; 2@1 | 2:0x245; 3:0x2279; 11:fixed/min2 | — | kind=1; slots=103@2 101@0 104@3 102@1; bass-engine | ldac, aac, sbc | windMode; bassEngine; spatialToggle; personalAnc; autoAnswer; voiceBoost; holdMask=8833; sharedHold | 05 06 09 0B 11 18 1B 04 | anc |
| 061C14 | OnePlus Nord Buds CE | — | 1:0x204; 2:0x2265; 3:0x2261 | — | kind=4; slots=12@1 17@0 14@2 15@3 | aac, sbc | — | 05 06 | — |
| 062010 | OPPO Enco Free2 | 5@0; 1@1; 2@2; 6@3 | 1:0x204; 2:0x264; 3:0x2203; 11:fixed; 5:0xE00 | — | kind=1; slots=1@0 2@1 3@2 4@3 | aac, sbc | personalAnc | 05 04 0B 0C 11 06 | — |
| 062012 | realme Buds Air 5 | 5@3[7@5 4@3 8@4 3@2]; 1@0; 2@1 | 2:0x245; 3:0x3279; 11:fixed/min2 | — | kind=1; slots=103@2 101@0 104@3 102@1; bass-engine | aac, sbc | windMode; bassEngine; findPhone; autoAnswer; voiceBoost; holdMask=8833; sharedHold | 05 04 06 09 | battery, anc, game, eq, gestures, wear, find |
| 062014 | OnePlus Buds Pro 2 | 5@1[7@7 4@4 8@5 3@6]; 1@0[1@3]; 2@2[2@8] | 16:0x204; 17:0x2265; 18:0x2261; 20:fixed | 32:0x80000; 33:0x40000 | kind=4; slots=11@0 13@3 14@2 12@1; custom(max 3); bass-engine | lhdc, aac, sbc | bassEngine; hearingScan; personalAnc; customEq; gameConfirm=read; spatialModes=0/1/2; autoVolume | 05 04 0B 0C 0F 11 18 06 1D 1C | — |
| 062410 | OPPO Enco Buds | — | 1:0x204; 2:0x264; 3:0x2201; 4:0x218; 12:fixed | — | kind=0 | aac, sbc | — | 05 06 | — |
| 062412 | realme Buds T300 | 5@3; 1@0; 2@1 | 2:0x245; 3:0x3279; 11:fixed/min2 | — | kind=1; slots=103@2 101@0 104@3 102@1 | aac, sbc | findPhone; spatialToggle; holdMask=8833; sharedHold; gameConfirm=read | 05 06 1B | battery, anc, game, spatial, eq, gestures, find |
| 062414 | OnePlus Nord Buds 2 | 5@0; 1@1; 2@2 | 1:0x204; 2:0x2265; 3:0x2261; 11:fixed; 6:0x1200 | 29:0x80000; 31:0x40000 | kind=4; slots=11@0 13@2 14@3 12@1; custom(max 2); bass-engine | aac, sbc | bassEngine; customEq; customEqLimit=2 | 05 06 1D | — |
| 062810 | OPPO Enco Air Lite | — | 1:0x204; 2:0x264; 3:0x2201; 4:0x218; 12:fixed | — | kind=0 | — | — | 05 06 | — |
| 062812 | realme Buds T110 | — | 2:0x27D; 3:0x27D; 4:0x27D | — | kind=0 | aac | bothHold; seqless; gameConfirm=read | 05 06 | battery, gestures |
| 062814 | OnePlus Buds Ace | 5@0; 1@1; 2@2 | 1:0x204; 2:0x2265; 3:0x2261; 11:fixed; 6:0x1200 | 29:0x80000; 31:0x40000 | kind=4; slots=11@0 13@2 12@1; custom(max 2); bass-engine | aac, sbc | bassEngine; customEq; customEqLimit=2 | 05 06 1D | anc, game |
| 062C10 | OPPO Enco W31 Lite | — | — | — | kind=0 | — | — | DEFAULT | — |
| 062C14 | OnePlus Nord Buds 2r | — | 1:0x204; 2:0x2265; 3:0x2261; 6:0x1200 | 29:0x80000; 31:0x40000 | kind=4; slots=11@0 12@2 13@1; custom(max 3) | aac, sbc | customEq; gameConfirm=read | 05 06 | battery, game, eq, gestures, find |
| 063010 | OPPO Enco R | — | 1:0x204; 2:0x2267; 3:0x2263; 4:0x218 | — | kind=1; slots=1@0 2@1 3@2 4@3 | aac | eqAltNames; eqGentle4 | 05 06 | — |
| 063012 | realme Buds Air6 Pro | 5@3[7@5 4@3 8@4 3@2]; 1@0; 2@1 | 2:0x245; 3:0x2279; 11:fixed/min2 | — | kind=1; slots=103@2 101@0 104@3 102@1; bass-engine | ldac, aac, sbc | windMode; bassEngine; spatialToggle; personalAnc; autoAnswer; voiceBoost; holdMask=8833; sharedHold | 05 06 09 0B 11 18 1B 04 | battery, anc, game, spatial, golden, eq, dual, gestures, find |
| 063014 | OnePlus Buds Pro 2（轻享版） | 5@1[7@7 4@4 8@5 3@6]; 1@0[1@3]; 2@2[2@8] | 16:0x204; 17:0x2265; 18:0x2261; 20:fixed | 32:0x80000; 33:0x40000 | kind=4; slots=11@0 13@3 14@2 12@1; custom(max 3); bass-engine | — | bassEngine; hearingScan; personalAnc; customEq; autoVolume | 05 04 0B 0C 0F 11 18 06 1D 1C | — |
| 063410 | OPPO Enco Air2 | — | 2:0x264; 3:0x2203; 4:0x218 | — | kind=1; slots=1@0 2@1 3@2 | aac, sbc | eqAltNames | 05 06 | — |
| 063412 | realme Buds Air6 | 5@3[7@5 4@3 8@4 3@2]; 1@0; 2@1 | 2:0x245; 3:0x3279; 11:fixed/min2 | — | kind=1; slots=103@2 101@0 104@3 102@1; bass-engine | lhdc5, aac, sbc | windMode; bassEngine; findPhone; autoAnswer; voiceBoost; holdMask=8833; sharedHold | 05 04 06 09 18 | battery, anc, game, hires, eq, dual, wear, find |
| 063414 | OnePlus Buds Pro 2R | 5@1[7@7 4@4 8@5 3@6]; 1@0[1@3]; 2@2[2@8] | 16:0x204; 17:0x2265; 18:0x2261; 20:fixed | 32:0x80000; 33:0x40000 | kind=4; slots=11@0 13@3 14@2 12@1; custom(max 3); bass-engine | lhdc, aac, sbc | bassEngine; hearingScan; personalAnc; customEq; autoVolume | 05 04 0B 0C 0F 11 18 06 1D 1C | — |
| 063810 | OPPO Enco Air2 Pro | 5@0; 1@1; 2@2; 6@3 | 1:0x204; 2:0x8264; 3:0xA203; 6:0x1218; 11:fixed | — | kind=2; slots=1@0 2@1 3@2 | aac, sbc | gameConfirm=read | 05 04 06 | — |
| 063812 | realme Buds N1 Pro | 5@3[7@5 4@3 8@4 3@2]; 1@0; 2@1 | 2:0x245; 3:0x3279; 11:fixed/min2 | — | kind=1; slots=103@2 101@0 104@3 102@1; bass-engine | — | windMode; bassEngine; findPhone; autoAnswer; voiceBoost; holdMask=8833; sharedHold | 05 04 06 09 18 | — |
| 063C10 | OPPO Enco X2 | 5@1[7@7 4@4 8@5 3@6]; 1@0[1@3]; 2@2[2@8 6@9] | 16:0x264; 17:0x267; 18:0x267; 5:0xE00; 20:fixed | 32:0x80000; 33:0x40000 | kind=1; slots=10@3 9@0 6@1 8@2 | lhdc, aac, sbc | hiRes; hearingScan; personalAnc | 05 04 0B 0C 11 13 18 06 | — |
| 063C12 | realme Buds T310 | 5@3[4@3 8@4 3@2]; 1@0; 2@1 | 2:0x245; 3:0x2279; 11:fixed/min2 | — | kind=1; slots=103@2 101@0 104@3 102@1; custom(max 3); dynamic=3; bass-engine | aac, sbc | windMode; bassEngine; findPhone; spatialToggle; customEq; volumeEnhancer; voiceBoost; holdMask=8833; sharedHold; gameConfirm=read | 05 06 09 11 1B 1D | battery, anc, wind, game, spatial, eq, dual, gestures, find |
| 063C14 | OnePlus Buds 3 | 5@1[7@7 4@4 8@5 3@6]; 1@0[1@3]; 2@2[2@8] | 1:0x204; 2:0x2265; 3:0x2261; 27:fixed/min1; 5:0xE00 | 29:0x80000; 31:0x40000 | kind=4; slots=11@0 14@2 12@1; custom(max 3); bass-engine | lhdc, aac, sbc | bassEngine; hearingScan; customEq; gameSoundFx; gameConfirm=read; autoVolume | 05 04 0B 11 18 06 1B 1D | battery, anc, spatial, golden, eq, dual, gestures, wear, find |
| 064010 | OPPO Enco Free2i | 5@0; 1@1; 2@2; 6@3 | 1:0x204; 2:0x264; 3:0x2203; 11:fixed; 5:0xE00 | — | kind=1; slots=1@0 2@1 3@2 4@3 | — | personalAnc | 05 04 0C 11 06 | — |
| 064012 | realme Buds N1 | 5@3[4@3 8@4 3@2]; 1@0; 2@1 | 2:0x245; 3:0x2279; 11:fixed/min2 | — | kind=1; slots=103@2 101@0 104@3 102@1; dynamic=3 | aac, sbc | windMode; findPhone; spatialToggle; voiceBoost; holdMask=8833; sharedHold | 05 06 09 11 1B | — |
| 064014 | OnePlus Buds Pro 3 | 5@1[7@7 4@4 8@5 3@6]; 1@0[1@3]; 2@2[2@8] | 16:0x204; 17:0x2267; 18:0x312263; 5:0xE00; 20:fixed | 32:0x80200; 33:0x40200; 34:0x20200 | kind=4; slots=11@0 13@1 14@2 12@3 30@7; custom(max 3); bass-engine | lhdc5, aac, sbc | bassEngine; hearingScan; customEq; gameSoundFx; spatialModes=0/1/2; autoVolume | 05 04 0B 11 13 18 06 1D | battery, anc, hires, spatial, golden, eq, dual, gestures, wear, find |
| 064410 | OPPO Enco Air2i | — | 1:0x204; 2:0x2265; 3:0x2261; 4:0x218 | — | kind=2; slots=1@0 2@1 3@2 | aac, sbc | — | 05 06 | — |
| 064412 | realme Buds T01 | — | 2:0x27D; 3:0x27D; 4:0x27D | — | kind=2; slots=105@0 11@1 106@2 | aac, sbc | volumeEnhancer; bothHold; seqless; gameConfirm=read | 05 06 | — |
| 064414 | OnePlus Nord Buds 3 Pro | 5@1[7@7 4@4 8@5 3@6]; 1@0[1@3]; 2@2[2@8] | 1:0x204; 2:0x2267; 3:0x2263; 6:0x218; 11:fixed | 29:0x80000; 31:0x40000 | kind=4; slots=11@3 14@4 12@5; custom(max 3); bass-engine | aac, sbc | bassEngine; hearingScan; personalAnc; customEq | 05 04 0B 0C 11 18 06 1B 1D | battery, anc, game, hires, spatial, golden, eq, dual, gestures, wear, find |
| 064810 | OPPO Enco Buds2 | — | 1:0x204; 2:0x2265; 3:0x2261; 4:0x218 | — | kind=2; slots=1@0 2@1 3@2 | aac, sbc | — | 05 06 | battery, game, eq, gestures |
| 064812 | realme Buds Air7 | 5@3[7@5 4@3 8@4 3@2]; 1@0; 2@1 | 2:0x245; 3:0x2279; 11:fixed/min2 | — | kind=1; slots=103@2 101@0 104@3 102@1; dynamic=3 | lhdc5, aac, sbc | windMode; findPhone; spatialToggle; autoAnswer; voiceBoost; holdMask=8833; sharedHold | 05 04 06 09 11 18 1B | battery, anc, game, hires, spatial, golden, eq, dual, gestures, wear, find |
| 064814 | OnePlus Buds V | — | 1:0x204; 2:0x2265; 3:0x2261; 6:0x1200 | 29:0x80000; 31:0x40000 | kind=4; slots=11@0 12@2 13@1; custom(max 3) | aac | customEq | 05 06 | — |
| 064C10 | OPPO Enco Air3 | — | 1:0x204; 2:0x2267; 3:0x2263; 4:0x218 | — | kind=2; slots=1@0 2@1 3@2 | aac, sbc | — | 05 11 06 1B | — |
| 064C12 | realme Buds Air7 Pro | 5@3[7@5 4@3 8@4 3@2]; 1@0; 2@1 | 2:0x245; 3:0x2261; 11:fixed/min2 | — | kind=1; slots=103@2 101@0 104@3 102@1; dynamic=3 | lhdc5, aac, sbc | windMode; findPhone; spatialToggle; autoAnswer; voiceBoost; holdMask=8833; sharedHold | DEFAULT | battery, anc, wind, game, hires, spatial, golden, eq, gestures, wear, find |
| 064C14 | OnePlus Buds Ace 2 | 5@0; 1@1; 2@2 | 1:0x204; 2:0x2267; 3:0x2263; 6:0x218; 11:fixed/min1 | 29:0x80000; 31:0x40000 | kind=4; slots=11@3 14@4 12@5; custom(max 3); bass-engine | — | bassEngine; hearingScan; customEq | 05 0B 11 06 1B 1D | — |
| 065010 | OPPO Enco R Pro | 5@0; 1@1; 2@2; 6@3 | 1:0x204; 2:0x264; 3:0x2203; 6:0x1218; 11:fixed | — | kind=2; slots=18@0 2@1 3@2 | aac | — | 05 04 06 | — |
| 065014 | OnePlus Nord Buds 3 | 5@0; 1@1; 2@2 | 1:0x204; 2:0x2267; 3:0x2263; 6:0x218; 11:fixed/min1 | 29:0x80000; 31:0x40000 | kind=4; slots=11@3 14@4 12@5; custom(max 3); bass-engine | aac, sbc | bassEngine; hearingScan; customEq | 05 0B 11 06 1B 1D | battery, anc, game, spatial, golden, eq, dual, gestures, find |
| 065052 | realme Buds T200 Lite | — | 2:0x27D; 3:0x27D; 4:0x27D | — | kind=1; slots=103@2 101@0 104@3 102@1 | aac, sbc | noFind; bothHold | 05 06 11 | battery, game, eq, gestures |
| 065414 | OnePlus Buds 4 | 5@1[4@4 8@5 3@6 7@7]; 10@11; 2@2[2@8]; 1@0[1@3] | 1:0x204; 2:0x2267; 3:0x302263; 27:fixed/min1; 5:0xE00 | 29:0x80200; 30:0x20200; 31:0x40200 | kind=4; slots=11@0 14@1 12@2; custom(max 3); bass-engine | lhdc5, aac, sbc | bassEngine; hearingScan; customEq; autoVolume | 05 04 0B 11 18 06 1B 1D 1C | battery, anc, wind, game, hires, spatial, golden, eq, dual, gestures, wear, find |
| 065452 | realme Buds T200 | 5@3; 1@0; 2@1 | 2:0x245; 3:0x2279; 11:fixed/min2 | — | kind=1; slots=103@2 101@0 104@3 102@1; dynamic=3; bass-engine | ldac, aac, sbc | bassEngine; findPhone; spatialToggle; holdMask=8833; sharedHold; gameConfirm=read | 05 06 11 18 1B 1D | battery, anc, game, hires, spatial, golden, eq, dual, gestures, wear, find |
| 065810 | OPPO Enco R2 | — | 1:0x204; 2:0x2267; 3:0x2263; 4:0x218 | — | kind=2; slots=21@0 2@1 3@2 | aac, sbc | — | 05 11 06 1B | — |
| 065852 | realme Buds T200x | 5@3; 1@0; 2@1 | 2:0x27D; 3:0x227D; 11:fixed/min2 | — | kind=1; slots=103@2 101@0 104@3 102@1; custom(max 3); bass-engine | aac, sbc | bassEngine; customEq; noFind; holdMask=8833; sharedHold; bothHold | 05 06 11 1D | battery, anc, game, eq, dual, gestures |
| 065854 | OnePlus Nord Buds 3r | — | 1:0x204; 2:0x2265; 3:0x302261; 6:0x218 | 29:0x80200; 31:0x40200 | kind=4; slots=11@0 14@2 12@1; custom(max 3) | aac, sbc | customEq; gameConfirm=read | 05 11 06 1B 1E | battery, game, spatial, eq, dual, gestures, find |
| 065C10 | OPPO Enco Air3 Pro | 5@1[7@7 4@4 8@5 3@6]; 1@0[1@3]; 2@2[2@8] | 1:0x204; 2:0x2267; 3:0x2263; 6:0x218; 11:fixed | — | kind=2; slots=1@0 2@1 20@2 | ldac, aac, sbc | hiRes; hearingScan; personalAnc | 05 04 0B 0C 11 18 06 1B | battery, anc, game, hires, spatial, golden, eq, dual, gestures, wear, find |
| 065C12 | realme Buds Clip | — | 2:0x245; 3:0x2279 | — | kind=1; slots=103@2 101@0 104@3 102@1; dynamic=3 | aac, sbc | spatialToggle; virtualBass | 05 06 11 1B | battery, spatial, eq, gestures, find |
| 065C14 | OnePlus Open Buds | — | 2:0x2265; 3:0x302279; 4:0x218 | 29:0x80200; 30:0x20200 | kind=4; slots=35@0 34@1 28@2 | — | dualDevice=connectDisconnectDevice; tapLevel; holdVolume; autoVolume | DEFAULT | — |
| 066010 | OPPO Enco Free3 | 5@1[7@7 4@4 8@5 3@6]; 1@0[1@3]; 2@2[2@8] | 1:0x204; 2:0x2267; 3:0x2263; 6:0x218; 11:fixed | — | kind=2; slots=1@0 2@1 20@2 | ldac, aac, sbc | hiRes; hearingScan; personalAnc | 05 04 0B 0C 11 18 06 1B | — |
| 066012 | realme TechLife Buds | — | 2:0x245; 3:0x2279 | — | kind=1; slots=103@2 101@0 104@3 102@1 | — | spatialToggle | 05 06 11 1B | — |
| 066054 | OnePlus Buds 3V | — | 1:0x204; 2:0x2265; 3:0x302261; 6:0x218 | 29:0x80200; 31:0x40200 | kind=4; slots=11@0 14@2 12@1; custom(max 3) | — | customEq; gameSoundFx | 05 11 06 1B 1E | — |
| 066414 | OnePlus Buds Ace 3 | 5@1[7@7 4@4 8@5 3@6]; 1@0[1@3]; 2@2[2@8] | 1:0x204; 2:0x2265; 3:0x302261; 5:0xE00; 11:fixed | 29:0x80200; 31:0x40200 | kind=4; slots=11@0 14@2 12@1; custom(max 3); bands=31/62/125/250/500/1000/2000/4000/8000/16000; bass-engine | lhdc5, aac | bassEngine; hearingScan; dualDevice=connectDisconnectDevice/setPriorityDevice; customEq; gameSoundFx; fastPair; holdMask=8833 | 05 04 0B 11 18 06 1B 1D 27 28 37 | — |
| 066452 | realme Buds T500 Pro | 5@3[7@5 4@3 8@4 3@2]; 1@0; 2@1 | 2:0x245; 3:0x2279; 11:fixed/min2 | — | kind=1; slots=103@2 101@0 104@3 102@1; custom(max 3); dynamic=3 | lhdc5, aac, sbc | windMode; findPhone; spatialToggle; tripleDevice; customEq; autoAnswer; volumeEnhancer; voiceBoost; holdMask=8833; sharedHold | 05 04 06 09 11 18 1B | battery, anc, wind, game, hires, spatial, golden, eq, dual, gestures, wear, find |
| 066810 | OPPO Enco X3i | 5@1[7@7 4@4 8@5 3@6]; 1@0[1@3]; 2@2[2@8] | 1:0x204; 2:0x2265; 3:0x2261; 27:fixed; 5:0xE00 | 29:0x80000; 31:0x40000 | kind=2; slots=1@0 2@1 3@2; bass-engine | lhdc5, aac, sbc | bassEngine; hearingScan; autoVolume | 05 04 0B 11 18 06 1B 1D 1C | — |
| 066812 | realme Buds Air8 | 5@3[7@5 4@3 8@4 3@2]; 1@0; 2@1 | 2:0x245; 3:0x2279; 11:fixed/min2 | — | kind=1; slots=103@2 101@0 104@3 102@1; custom(max 3); dynamic=3 | lhdc5, aac, sbc | windMode; findPhone; spatialToggle; tripleDevice; customEq; autoAnswer; voiceBoost; holdMask=8833; sharedHold | 05 04 06 09 11 18 1B | battery, anc, game, hires, spatial, golden, eq, dual, gestures, wear, find |
| 066814 | OnePlus Nord Buds 4 Pro | 5@1[7@7 4@4 8@5 3@6]; 1@0[1@3]; 2@2[2@8] | 1:0x204; 2:0x2265; 3:0x302261; 5:0xE00; 11:fixed | 29:0x80200; 31:0x40200 | kind=4; slots=11@0 14@2 12@1; custom(max 3); bands=31/62/125/250/500/1000/2000/4000/8000/16000; bass-engine | lhdc5, aac, sbc | bassEngine; hearingScan; dualDevice=connectDisconnectDevice/setPriorityDevice; customEq; gameSoundFx; fastPair; holdMask=8833 | 05 04 0B 11 18 06 1B 1D 27 28 37 | battery, anc, game, spatial, golden, eq, dual, gestures, wear, find |
| 066C10 | OPPO Enco Air2（新声版） | — | 1:0x204; 2:0x2267; 3:0x2263; 4:0x218 | — | kind=2; slots=1@0 2@1 3@2 | — | — | 05 06 1B | — |
| 066C12 | realme Buds Air8 Pro | 5@3[7@5 4@3 8@4 3@2]; 1@0; 2@1 | 2:0x245; 3:0x2261; 11:fixed/min2 | — | kind=1; slots=103@2 101@0 104@3 102@1; dynamic=3 | lhdc5, aac, sbc | windMode; findPhone; spatialToggle; tripleDevice; autoAnswer; voiceBoost; holdMask=8833; sharedHold | 05 04 06 09 0B 11 18 1B | battery, anc, game, hires, spatial, golden, dual, gestures, wear |
| 067010 | OPPO Enco Air3i | — | 1:0x204; 2:0x2267; 3:0x2263; 4:0x218 | — | kind=2; slots=1@0 2@1 3@2 | aac, sbc | — | 05 06 1B | — |
| 067014 | OnePlus Nord Buds 4 | 5@1[7@7 4@4 8@5 3@6]; 1@0[1@3]; 2@2[2@8] | 1:0x204; 2:0x2265; 3:0x102261; 6:0x218; 11:fixed | 29:0x80200; 31:0x40200 | kind=4; slots=11@0 14@1 12@2; custom(max 3); bands=31/62/125/250/500/1000/2000/4000/8000/16000; bass-engine | aac, sbc | bassEngine; hearingScan; dualDevice=connectDisconnectDevice; customEq; gameSoundFx; fastPair; holdMask=8833 | 05 04 0B 11 06 1B 1D 1E 27 28 37 | battery, anc, game, spatial, golden, eq, dual, gestures, wear, find |
| 067052 | realme Buds T500 | 5@3; 1@0; 2@1 | 2:0x245; 3:0x2279; 11:fixed/min2 | — | kind=1; slots=103@2 101@0 104@3 102@1; dynamic=3 | — | findPhone; spatialToggle; tripleDevice; holdMask=8833; sharedHold | 05 06 11 1B | — |
| 067410 | OPPO Enco X3 | 5@1[7@7 4@4 8@5 3@6]; 1@0[1@3]; 2@2[2@8 6@9] | 16:0x204; 17:0x2267; 18:0x312263; 5:0xE00; 20:fixed | 32:0x80200; 33:0x40200; 34:0x20200 | kind=4; slots=26@0 27@1 28@2 29@3 30@7; custom(max 3) | lhdc, aac | hearingScan; customEq; gameSoundFx; spatialModes=0/1/2; autoVolume | 05 04 0B 11 13 18 06 1B 1C | — |
| 067414 | OnePlus Flow Buds | 5@1[7@7 4@4 3@6]; 1@0[1@3]; 2@2[2@8] | 1:0x204; 2:0x2267; 3:0x302263; 5:0xE00; 11:fixed | 29:0x80200; 31:0x40200 | kind=4; slots=11@0 14@2 12@1; custom(max 3); bands=31/62/125/250/500/1000/2000/4000/8000/16000; bass-engine | — | bassEngine; hearingScan; dualDevice=connectDisconnectDevice/setPriorityDevice; customEq; gameSoundFx; fastPair; holdMask=8833 | 05 04 0B 11 18 06 1B 1D 1E 27 28 37 | — |
| 067452 | realme Buds T500 Pro Harry Potter Edition | 5@3[7@5 4@3 8@4 3@2]; 1@0; 2@1 | 2:0x245; 3:0x2279; 11:fixed/min2 | — | kind=1; slots=103@2 101@0 104@3 102@1; dynamic=3 | — | windMode; findPhone; spatialToggle; tripleDevice; autoAnswer; voiceBoost; holdMask=8833; sharedHold | 05 04 06 09 11 18 1B | — |
| 067810 | OPPO Enco Air3s | — | 1:0x204; 2:0x2267; 3:0x2263; 4:0x218 | — | kind=2; slots=1@0 2@1 3@2 | aac, sbc | — | 05 11 06 1B | — |
| 067814 | OnePlus Buds Pro 4 | 5@1[7@7 4@4 8@5 3@6]; 10@11[11@12 12@13 13@14]; 1@0[1@3]; 2@2[2@8] | 16:0x1000204; 17:0x802265; 18:0x2812261; 5:0xE00; 20:fixed | 32:0x80200; 33:0x40200; 34:0x20200 | kind=4; slots=11@0 13@1 14@2 12@3 30@7; custom(max 3); bands=31/62/125/250/500/1000/2000/4000/8000/16000; bass-engine | — | bassEngine; hearingScan; customEq; gameSoundFx; fastPair; holdMask=8397441; spatialModes=0/1/2; autoVolume | 05 04 0B 0F 11 13 18 06 1B 1D 1C 1E 27 28 32 37 3B 3A | — |
| 067852 | realme TechLife Buds T500 Pro | 5@3[7@5 4@3 8@4 3@2]; 1@0; 2@1 | 2:0x245; 3:0x2279; 11:fixed/min2 | — | kind=1; slots=103@2 101@0 104@3 102@1; dynamic=3 | — | windMode; findPhone; spatialToggle; tripleDevice; autoAnswer; voiceBoost; holdMask=8833; sharedHold | 05 04 06 09 11 18 1B | — |
| 067C10 | OPPO Enco Air4 Pro | 5@1[7@7 4@4 8@5 3@6]; 1@0[1@3]; 2@2[2@8] | 1:0x204; 2:0x2267; 3:0x2263; 6:0x218; 11:fixed | 29:0x80000; 31:0x40000 | kind=2; slots=1@0 2@1 3@2 | lhdc5, aac, sbc | hearingScan; personalAnc | 05 04 0B 0C 11 18 06 1B | — |
| 068010 | OPPO Enco Buds2 Pro | — | 1:0x204; 2:0x2265; 3:0x2261; 6:0x1200 | 29:0x80000; 31:0x40000 | kind=2; slots=11@0 12@2 13@1 | aac, sbc | gameConfirm=read | 05 06 | battery, eq, gestures, find |
| 068410 | OPPO Enco R3 | — | 1:0x204; 2:0x2267; 3:0x2263; 4:0x218 | — | kind=2; slots=25@0 2@1 3@2 | — | — | 05 06 1B | — |
| 068414 | OnePlus Buds | — | 2:0x65 | — | kind=0 | aac, sbc | oldTrackBytes | 05 | — |
| 068810 | OPPO Enco Air 3i | — | 1:0x204; 2:0x2265; 3:0x2261; 4:0x218 | — | kind=2; slots=1@0 2@1 3@2 | aac, sbc | — | 05 06 | — |
| 068814 | OnePlus Buds Z | — | 2:0x65 | — | kind=0 | — | oldTrackBytes | 05 | — |
| 068C10 | OPPO Enco Free4 | 5@1[4@4 8@5 3@6 7@7]; 10@11; 2@2[2@8]; 1@0[1@3] | 1:0x204; 2:0x2267; 3:0x302263; 27:fixed/min1; 5:0xE00 | 29:0x80200; 30:0x20200; 31:0x40200 | kind=4; slots=26@0 28@1 29@2; custom(max 3) | — | hearingScan; customEq; autoVolume | 05 04 0B 11 18 06 1B 1C | — |
| 069010 | OPPO Enco Air4 | 5@0; 1@1; 2@2 | 1:0x204; 2:0x2267; 3:0x2263; 6:0x218; 11:fixed/min1 | 29:0x80000; 31:0x40000 | kind=2; slots=1@0 2@1 3@2 | aac, sbc | hearingScan | 05 0B 11 06 1B | — |
| 069850 | OPPO Enco Air4i | — | 1:0x204; 2:0x2265; 3:0x2261; 6:0x218 | 29:0x80200; 31:0x40200 | kind=4; slots=22@0 31@2 32@1; custom(max 3) | — | customEq; gameSoundFx | 05 11 06 1B | — |
| 069C10 | OPPO Enco R3 Pro | 5@1[7@7 4@4 8@5 3@6]; 1@0[1@3]; 2@2[2@8] | 1:0x204; 2:0x2267; 3:0x2263; 6:0x218; 11:fixed | 29:0x80000; 31:0x40000 | kind=2; slots=33@0 2@1 3@2 | lhdc5 | hearingScan; personalAnc | 05 04 0B 0C 11 18 06 1B | — |
| 06A010 | OPPO Enco Buds3 Pro+ | 5@0; 1@1; 2@2 | 1:0x204; 2:0x2267; 3:0x2263; 6:0x218; 11:fixed/min1 | 29:0x80000; 31:0x40000 | kind=2; slots=1@0 2@1 3@2 | aac, sbc | hearingScan | 05 0B 11 06 1B | — |
| 06A450 | OPPO Enco Buds3 | — | 1:0x204; 2:0x2265; 3:0x302261; 6:0x218 | 29:0x80200; 31:0x40200 | kind=3; slots=22@0 31@2 32@1 | aac, sbc | — | 05 11 06 1B | — |
| 06A850 | OPPO Enco Buds3 Pro | — | 1:0x204; 2:0x2265; 3:0x2261; 6:0x218 | 29:0x80200; 31:0x40200 | kind=4; slots=22@0 31@2 32@1; custom(max 3) | aac, sbc | customEq; gameSoundFx | 05 11 06 | battery, game, eq, dual, gestures, find |
| 06AC50 | OPPO Enco R4 | — | 1:0x204; 2:0x2265; 3:0x2261; 6:0x218 | 29:0x80200; 31:0x40200 | kind=4; slots=22@0 31@2 32@1; custom(max 3) | — | customEq; gameSoundFx | 05 11 06 1B | — |
| 06B050 | OPPO Enco Air4（新声版） | — | 1:0x204; 2:0x2265; 3:0x302261; 6:0x218 | 29:0x80200; 31:0x40200 | kind=3; slots=22@0 31@2 32@1 | — | — | 05 11 06 1B | — |
| 06B450 | OPPO Enco R5 | — | 1:0x204; 2:0x2265; 3:0x302261; 6:0x218 | 29:0x80200; 31:0x40200 | kind=3; slots=22@0 31@2 32@1 | — | — | 05 11 06 1B 1E | — |
| 06B810 | OPPO Enco Clip | — | 2:0x2267; 3:0x30227B; 4:0x218 | 29:0x80200; 30:0x20200 | kind=4; slots=35@0 34@1 28@2 | — | dualDevice=connectDisconnectDevice; tapLevel; holdVolume; autoVolume | 05 04 11 06 1B 1C 35 | — |
| 06BC10 | OPPO Enco X3s | 5@1[4@4 8@5 3@6 7@7]; 10@11; 2@2[2@8]; 1@0[1@3] | 1:0x204; 2:0x2267; 3:0x302263; 27:fixed/min1; 5:0xE00 | 29:0x80200; 30:0x20200; 31:0x40200 | kind=4; slots=26@0 28@1 29@2 30@3; custom(max 3) | lhdc5, aac, sbc | hearingScan; customEq; autoVolume | 05 04 0B 11 18 06 1B 1C | — |
| 06C010 | OPPO Enco Free4（丹拿版） | 5@1[4@4 8@5 3@6 7@7]; 10@11; 2@2[2@8]; 1@0[1@3] | 1:0x204; 2:0x2267; 3:0x302263; 27:fixed/min1; 5:0xE00 | 29:0x80200; 30:0x20200; 31:0x40200 | kind=4; slots=26@0 28@1 29@2 30@3; custom(max 3) | — | hearingScan; customEq; autoVolume | 05 04 0B 11 18 06 1B 1C | — |
| 06C410 | OPPO Enco Air5 Pro | 5@1[7@7 4@4 8@5 3@6]; 1@0[1@3]; 2@2[2@8] | 1:0x204; 2:0x2267; 3:0x302263; 5:0xE00; 11:fixed | 29:0x80200; 31:0x40200 | kind=4; slots=26@0 28@2 29@1; custom(max 3); bands=31/62/125/250/500/1000/2000/4000/8000/16000; bass-engine | lhdc5, aac, sbc | bassEngine; hearingScan; dualDevice=connectDisconnectDevice/setPriorityDevice; customEq; gameSoundFx; fastPair; holdMask=8833 | 05 04 0B 11 18 06 1B 1D 27 28 37 | battery, anc, spatial, dual, gestures, find |
| 06C810 | OPPO Enco Air5s | 5@1[7@7 4@4 3@6]; 1@0[1@3]; 2@2[2@8] | 1:0x204; 2:0x2267; 3:0x302263; 5:0xE00; 11:fixed | 29:0x80200; 31:0x40200 | kind=4; slots=26@0 28@2 29@1; custom(max 3); bands=31/62/125/250/500/1000/2000/4000/8000/16000; bass-engine | aac, sbc | bassEngine; hearingScan; dualDevice=connectDisconnectDevice/setPriorityDevice; customEq; gameSoundFx; fastPair; holdMask=8833 | 05 04 0B 11 18 06 1B 1D 1E 27 28 37 | — |
| 06D810 | OPPO Enco Air5 | 5@1[7@7 4@4 8@5 3@6]; 1@0[1@3]; 2@2[2@8] | 1:0x204; 2:0x2267; 3:0x102263; 6:0x218; 11:fixed | 29:0x80200; 31:0x40200 | kind=4; slots=26@0 28@1 29@2; custom(max 3); bands=31/62/125/250/500/1000/2000/4000/8000/16000 | aac, sbc | hearingScan; dualDevice=connectDisconnectDevice; customEq; gameSoundFx; fastPair; holdMask=8833 | 05 04 0B 11 06 1B 1E 27 28 37 | battery, anc, game, spatial, golden, eq, dual, gestures, wear, find |
| 06E010 | OPPO Enco Clip2 | — | 2:0x2267; 3:0x10227B; 5:0x600 | 29:0x80200; 30:0x20200 | kind=4; slots=26@0 34@3 28@2 30@4 | lhdc5, aac, sbc | dualDevice=connectDisconnectDevice/setPriorityDevice; fastPair; autoVolume | 05 04 11 18 16 06 1B 1C 1E 37 | — |
| 06E410 | OnePlus Buds Clip | — | 2:0x802265; 3:0x802279; 5:0x600 | 29:0x80200; 30:0x20200 | kind=4; slots=11@0 34@3 14@2 30@4 | lhdc5, aac, sbc | fastPair; autoVolume | 05 04 11 18 16 06 1B 1C 1E 37 | battery, game, hires, spatial, eq, dual, gestures, wear, find |
| 06E810 | OPPO Enco Air5s（星光版） | 5@1[7@7 4@4 3@6]; 1@0[1@3]; 2@2[2@8] | 1:0x204; 2:0x2267; 3:0x302263; 5:0xE00; 11:fixed | 29:0x80200; 31:0x40200 | kind=4; slots=26@0 28@2 29@1; custom(max 3); bands=31/62/125/250/500/1000/2000/4000/8000/16000; bass-engine | — | bassEngine; hearingScan; dualDevice=connectDisconnectDevice/setPriorityDevice; customEq; gameSoundFx; fastPair; holdMask=8833 | 05 04 0B 11 18 06 1B 1D 1E 27 28 37 | — |
| 06EC10 | OPPO Enco X4 | 5@1[7@7 4@4 8@5 3@6]; 10@11[11@12 12@13 13@14]; 1@0[1@3]; 2@2[2@8] | 16:0x1000204; 17:0x802265; 18:0x2812261; 5:0xE00; 20:fixed | 32:0x80200; 33:0x40200; 34:0x20200 | kind=4; slots=42@0 43@7 28@2 46@1 29@3; custom(max 3); bands=31/62/125/250/500/1000/2000/4000/8000/16000; bass-engine | lhdc, aac, sbc | bassEngine; hearingScan; customEq; gameSoundFx; fastPair; holdMask=8397441; spatialModes=0/1/2; autoVolume | 05 04 0B 0F 11 13 18 06 1B 1D 1C 1E 27 28 32 37 3B 3A | — |
| 06F010 | OPPO Enco Air4s | 5@0; 1@1; 2@2 | 1:0x204; 2:0x2267; 3:0x2263; 6:0x218; 11:fixed/min1 | 29:0x80000; 31:0x40000 | kind=2; slots=1@0 2@1 3@2 | — | hearingScan | 05 0B 11 06 1B | — |
| 06F450 | OPPO Enco Buds3s | — | 1:0x204; 2:0x2265; 3:0x2261; 6:0x218 | 29:0x80200; 31:0x40200 | kind=4; slots=26@0 28@2 29@1; custom(max 3) | aac, sbc | customEq | 05 11 06 1B 1E | — |
| 1B5374 | realme Buds Air 2 Neo | — | — | — | kind=0 | aac, sbc | — | DEFAULT | — |
| 3D59CF | realme Buds Air | — | — | — | kind=0 | aac | — | DEFAULT | — |
| 49074B | realme Cobble Speaker | — | — | — | kind=0 | — | — | DEFAULT | battery, game |
| 693878 | realme Buds Q2 | — | — | — | kind=0 | aac, sbc | — | DEFAULT | — |
| 8CD10F | realme Buds Air Pro | — | — | — | kind=0 | aac, sbc | — | DEFAULT | — |
| A5FD66 | realme Brick Speaker | — | — | — | kind=0 | — | — | DEFAULT | — |
| BA5D56 | realme Buds Air 2 | — | — | — | kind=0 | aac, sbc | — | DEFAULT | — |
| C1E2B1 | realme Buds Wireless 2 | — | — | — | kind=0 | ldac, aac, sbc | — | DEFAULT | — |
| C70A6D | realme Buds Wireless 2 Neo | — | — | — | kind=0 | — | — | DEFAULT | — |
| C70A6E | DIZO Wireless | — | — | — | kind=0 | sbc | — | DEFAULT | — |
| C70A6F | DIZO GoPods D | — | — | — | kind=0 | sbc | — | DEFAULT | — |
| C70A70 | DIZO GoPods | — | — | — | kind=0 | — | — | DEFAULT | — |
| C70A75 | realme Pocket Speaker | — | — | — | kind=0 | — | — | DEFAULT | — |
| E3009F | realme Buds Wireless Pro | — | — | — | kind=0 | ldac, aac, sbc | — | DEFAULT | — |
| F85450 | realme Buds Air Neo | — | — | — | kind=0 | aac, sbc | — | DEFAULT | — |

### Profile interpretation

- Gesture option masks are bitsets decoded through the shared dictionary.
- ANC slots are model-specific; construct separate write and report maps.
- EQ mode and slot are distinct. Slot goes on wire; mode is canonical app identity.
- Status query describes what to ask, not what is automatically supported.
- Special flags do not override missing live bitmap except for explicit write-only operations.
- Empty confirmation means unverified, not unsupported.
- Never copy a sibling model profile without a capture or explicit evidence.

## 10. REA investigation status and remaining work

REA is an application/binary reverse-engineering workflow. It is appropriate for inspecting Android companion-app APKs and native libraries when their original source is unavailable. It is not a source-code-repository decoder.

The protocol source and model catalogue were statically inspected for this specification. No REA execution is claimed: no official companion-app APKs were supplied, and the review environment did not have a working REA analysis installation.

For later REA work, acquire the exact official APK/version and investigate:
1. package identity, signature, manifest, permissions and native libraries;
2. service discovery and transport selection;
3. control-session ownership and reconnect policy;
4. command builders and payload serializers;
5. read/event decoders;
6. model catalogue and UI visibility conditions;
7. UI control → protocol operation call paths;
8. version changes to commands/model data.

Correlate every candidate with a before/after Bluetooth capture and a read-back from the exact device. A decompiled enum name is only a lead. Review app terms, applicable law and licenses before redistributing decompiled material. Do not copy third-party implementation code into OmniBuds.

## 11. Capture and verification procedure

For each model/firmware:
1. Record exact product ID, Bluetooth name, firmware versions and phone OS.
2. Capture clean connection and initialisation.
3. Record command bitmap and offered events.
4. Change one companion-app setting at a time.
5. Capture on/off and every selectable mode/preset.
6. Record outgoing command, ACK, subsequent read and push.
7. Repeat physical gestures from the earbuds.
8. Disconnect/reconnect and check persistence.
9. Repeat battery/wear tests with case open/closed and single-bud docking.
10. Store raw evidence and interpretation separately.

## 12. Acceptance tests

### Frame parser
- Every split point of a valid frame parses identically.
- Multiple concatenated frames emit separately.
- Noise is discarded without losing frames.
- Multi-byte LEB128 works.
- Invalid LEB128 resynchronises.
- Oversized frame/reassembly rejected.
- Sliced message requires correct order/counter and complete payload.
- Disconnect/timeout/new first slice clears reassembly.
- PayLen mismatch is malformed, never a shortened valid payload.

### Capability/visibility
- Unknown model exposes no model write controls.
- Missing ANC tree hides ANC.
- Missing bitmap bit hides command.
- Missing feature ID hides switch.
- Previous model profile never leaks after device change.
- One-button, both-hold, per-bud hold and on-call screens match model record.

### ANC/gestures
- T300 uses only its own write/report tree.
- Event 03 01 01 updates active mode.
- Event 03 02 updates hold-cycle only.
- Smart level separate.
- Hold-cycle writes read back.
- Gesture write starts from full table and verifies full read-back.
- Short response never replaces valid table.
- Call/primary slots cannot collide.
- F1 never updates ANC/game state.

### Feature engine
- ACK alone cannot move READ/PUSH-confirmed toggle.
- READ requires exact sequence.
- PUSH requires genuine event.
- Refusal states are distinct.
- Partial multi-write failure triggers resynchronisation.
- Restart writes survive disconnect and confirm after reconnect.
- Late replies cannot complete a newer operation.
- Busy state clears on timeout/failure/disconnect.

### EQ/codec/device manager
- EQ selection reads back.
- Create/delete re-reads preset list.
- Six/ten band models render exact frequencies.
- Dynamic-band write includes band ID and reads correct command.
- Codec picker contains only returned codecs.
- High-quality audio is not described as phone codec selection.
- Spatial/Hi-Res conflict is confirmed after both writes.
- Device manager rows hidden when commands absent.
- Triple label only on triple-enabled profiles.

## 13. Implementation backlog

### Priority 0 — parser and state correctness
- Replace simple scan with bounded LEB128 framing and ordered slice reassembly.
- Add parser recovery/malformed-input tests.
- Verify event offer/subscription for battery, wear and ANC.
- Distinguish ANC mode event from hold-cycle echo.
- Enforce sequence + command matching and seqless exception.
- One control-channel owner for all UI surfaces.

### Priority 1 — model-driven feature engine
- Import all 143 model profiles into canonical schema.
- Typed ANC, gesture, EQ, codec and exception properties.
- Separate write/report maps.
- Model-specific confirmation policies.
- Visibility from model + bitmap + feature response + platform capabilities.

### Priority 2 — confirmed controls
- Route writes through one operation queue.
- Replace optimistic toggles with Applying/Confirmed/Refused/Unknown.
- Rebuild touch/hold editing from full-table read-back.
- Hold-cycle editor independent of gesture assignment.
- Identical state across app, notification, widget and tiles.

### Priority 3 — feature additions
- EQ/custom presets/BassWave/dynamic bands.
- Game sound and Game EQ.
- Typed spatial modes and phone-rendering gate.
- Dual/triple device manager.
- Alert volume, tap sensitivity, head gestures.
- Hearing profile, fit test and personal ANC only on verified models.

### Priority 4 — evidence expansion
- Hardware-test exact model/firmware pairs.
- Resolve name/product-ID conflicts and variants.
- Promote each feature operation separately through evidence states.
- Keep UNKNOWN for untested profiles.
- Add a captured regression fixture for every command/event variant.

## 14. Definition of done

- Framing, lengths, slices and resynchronisation are robust.
- Every enabled feature has typed read/write/event definitions.
- Each model has its own ANC, gesture, EQ, codec and exception data.
- UI visibility uses profile + live capabilities + platform capability.
- Every write has an explicit confirmation policy.
- Existing T300 behaviour remains regression-safe.
- No model borrows another model's bitmask or gesture table.
- Enabled features carry evidence status.
- Unknown behaviour remains disabled and diagnosable.

**Engineering principle:** one shared feature engine and one typed model catalogue; protocol values, mode maps, payload variants, visibility and confirmation policies remain specific to each exact model.

