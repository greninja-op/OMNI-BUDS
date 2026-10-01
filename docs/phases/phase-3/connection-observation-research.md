# Phase 3 — Android Bluetooth connection observation: what the platform actually exposes

**Agent B (Android Bluetooth connection-observation specialist) deliverable.** Research only: this
file declares no permission, changes no manifest, no build file and no source (`SEC-PERM-003`).
Research date: 2026-10-01. Companion document:
`docs/phases/phase-2/bluetooth-api-research.md` (adapter state and permissions); this file assumes
its vocabulary and extends it.

---

## 0. Method, evidence labels and limits

### 0.1 The verification ladder used here

| Label | What it means | What it can prove | What it cannot prove |
|---|---|---|---|
| **[DOC]** | A `developer.android.com` page read **in this session**, quoted or near-quoted. | Documented intent; documented permission sentences; documented behavioural rules. | What a particular handset does. |
| **[SDK]** | An artifact shipped with the compileSdk 35 platform data on this workstation, read in this session (`data/api-versions.xml`, `android-stubs-src.jar`, `android.jar`, `data/broadcast_actions.txt`). | **Existence** of a name in the public API, its introduction API level, its Javadoc as shipped to us, and whether the system classifies an action as a system broadcast. A name absent from `android.jar` is not compilable by a third-party app — that is a finding, not an inference. | Runtime behaviour. Stubs throw `Stub!`; they carry no logic. |
| **[AOSP-35]** | Source read in this session from AOSP tag `android-15.0.0_r11` (= API 35, our `compileSdk`). | How the reference implementation actually behaves: what it returns on denial, who sends a broadcast, with which receiver permission, and which members are `@SystemApi`/`@hide`. | A promise. It is one implementation of one release; OEMs fork it, and `@hide` members are explicitly not a contract. |
| **[INFER]** | My conclusion drawn from labelled facts. Flagged every time. | Reasoning. | Anything. Never a platform promise. |
| **[UNVERIFIED]** | Not settleable from what I read. Listed in §11 with what was tried. | — | Must not be used as an implementation justification. |

### 0.2 Where the local evidence came from

`local.properties` points at an installed Android SDK. Per the Phase 3 instruction the absolute path
is machine-local and is **not** recorded here; it is referred to as **"the compileSdk 35 platform
data"**. Four artifacts inside it were read directly with Python (`xml.etree`, `zipfile`, byte probes
on class files). `javap` and `strings` are not installed on this workstation, so no claim in this
file depends on them.

- `platforms/android-35/data/api-versions.xml` — root element `<api version="3">`, 6486 direct
  children (mostly `<sdk>` and `<class>`); class names are slash-separated
  (`android/bluetooth/BluetoothManager`); members carry `since`, and a missing `since` means "at or
  below the enclosing class's own level". Parsed with `xml.etree`. **[SDK]**
- `platforms/android-35/android-stubs-src.jar` — 51 entries under `android/bluetooth/`, each the
  real Javadoc-bearing source stub for the public surface. This is the same doc text the public
  reference pages render, but read as a file we can quote exactly. **[SDK]**
- `platforms/android-35/android.jar` — used for negative existence proofs: a member name that does
  not occur in a class file's constant pool is not in the public API at all. **[SDK]**
- `platforms/android-35/data/broadcast_actions.txt` — 336 lines, the SDK's own list of system
  broadcast actions. This is the list `Context.registerReceiver` points at as authoritative for the
  receiver-export-flag exemption **[DOC]**, so it is checked per action in §4. **[SDK]**

### 0.3 The device limit, stated plainly

No phone, no emulator and no `adb` target was used. `adb devices -l` was run against the
installation's platform-tools in this session and printed only

```
List of devices attached
```

i.e. zero attached devices. **[INFER]** (observation of a command result, not a platform claim.)
Consequently **no claim in this file is `LAB_TESTED` or `HARDWARE_VERIFIED`**; the highest rung
reached is "the source of API 35 does this / the documentation says so". §11 is the list a future
device session must script against.

### 0.4 Two scope warnings inherited from Phase 2 and still true

1. **Requirements are keyed to `targetSdkVersion`, not to the device's API level.** The recurring
   stub sentence is verbatim: "For apps targeting `Build.VERSION_CODES.R` or lower, this requires the
   `BLUETOOTH` permission… For apps targeting `Build.VERSION_CODES.S` or or higher, this requires the
   `BLUETOOTH_CONNECT` permission…". **[SDK]** Bands below are written **t≤30** / **t≥31**.
2. **The live reference pages now render API levels above our `compileSdk`.** They show members and
   deprecations "Added in API level 36/37" which cannot be called from a `compileSdk 35` build.
   **[DOC]** + **[INFER]**. Everything in this file was re-checked against the API-35 stubs and the
   `android-15.0.0_r11` source, not against the current page rendering. Where a page and API 35
   disagree, §13 records it.

---

## 1. Project configuration this document assumes

Read in this session from the repository, not assumed:

| Fact | Value | Source |
|---|---|---|
| `minSdk` | 26 (Android 8.0) | `gradle/libs.versions.toml` line 13 `androidMinSdk = "26"` |
| `compileSdk` | 35 | same, line 12 `androidCompileSdk = "35"` |
| `targetSdk` | 35 declared in the catalog (line 14) but **no module applies it**: `platform/android/build.gradle.kts` sets only `compileSdk` (l.21) and `minSdk` (l.24) | repo files, read this session |
| Consequence | The observer must resolve targetSdk at runtime (`context.applicationInfo.targetSdkVersion`) and never assume it from the catalog — Phase 2 §1 reached the same conclusion and it is load-bearing again. **[INFER]** | `docs/phases/phase-2/bluetooth-api-research.md` §1 |
| Manifest today | `platform/android/src/main/AndroidManifest.xml` is `<manifest … />` — no `uses-permission`, no receiver, machine-checked to stay that way | repo file, read this session |
| Adapter-state facts | Already settled in Phase 2: `getAdapter`/`isEnabled`/`getState`/`ACTION_STATE_CHANGED` need **no** permission for a t≥31 app | Phase 2 §3 rows 1–4, §4 items 1–3 |

Phase 3's constraints as I read them: no pairing, no connecting, no scanning, no audio path, no
aggressive polling. Every question below is answered under those constraints.

---

## 2. Q1 — six concepts that must not be conflated

### Answer

There is **one API for each** of the six, and **each one is blind to the other five**. None of them
is "is this earbud connected to this phone". The closest thing to a single truthful answer is
`BluetoothDevice.ACTION_ACL_CONNECTED`/`_DISCONNECTED` plus the per-device GATT list, and even that
pair misses per-profile and audio state.

### Evidence, per concept

**(a) Bonded state — "has paired at some point".**
Answered by `BluetoothAdapter.getBondedDevices()` (`Set<BluetoothDevice>`, API 5) and
`BluetoothDevice.getBondState()` (API 5). **[SDK]** `api-versions.xml`
`android/bluetooth/BluetoothAdapter` → `getBondedDevices()Ljava/util/Set;` with no `since`
(eff. 5); `android/bluetooth/BluetoothDevice` → `getBondState()I` no `since` (eff. 5).
What it does **not** answer: whether anything is connected. The platform says so in the stub Javadoc
for `BOND_BONDED`, verbatim: "*Being bonded (paired) with a remote device does not necessarily mean
the device is currently connected. It just means that the pending procedure was completed at some
earlier time, and the link key is still stored locally, ready to use on the next connection.*"
**[SDK]** What it cannot see: a device that is connected but never bonded (possible for
non-bonded LE GATT links) — such a device is not in `getBondedDevices()` at all. **[AOSP-35]**
`GattService.java` l.1862-1867 seeds the candidate set from `mAdapterService.getBondedDevices()`, so
the GATT-layer enumeration universe is derived from bond records.

**(b) Connection state (adapter-level, "anything at all is connected").**
Answered by `BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED` + `EXTRA_CONNECTION_STATE`
(API 11, public **[SDK]**) and `BluetoothAdapter.getProfileConnectionState(int)` (API 14 **[SDK]**).
What it does **not** answer: *which* device and *which* profile. The public Javadoc for the action is
explicit and is worth quoting in full because it is the single most-misread sentence in this area:

> "Intent used to broadcast the change in connection state of the local Bluetooth adapter to a
> profile of the remote device. When the adapter is not connected to any profiles of any remote
> devices and it attempts a connection to a profile this intent will be sent. **Once connected, this
> intent will not be sent for any more connection attempts to any profiles of any remote device.**
> When the adapter disconnects from the last profile its connected to of any remote device, this
> intent will be sent." **[SDK]** (identical text at **[AOSP-35]**
> `framework/java/android/bluetooth/BluetoothAdapter.java` l.3059-region doc block)

**[AOSP-35]** confirms this is literally implemented as a 0↔1 edge trigger on global counters:
`btservice/AdapterProperties.java` l.856-908 `updateCountersAndCheckForConnectionStateChange()`
returns true for `CONNECTED` only when `mProfilesConnected == 1`, and for `DISCONNECTED` only when
`mProfilesConnected == 0 && mProfilesConnecting == 0`; the broadcast is constructed and sent only
inside that `if` (`updateOnProfileConnectionChanged` l.735-789; `new Intent(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)`
at l.773, `mService.sendBroadcastAsUser(intent, UserHandle.ALL, BLUETOOTH_CONNECT, …)` at l.782).
Note also l.774: the intent **does** carry `BluetoothDevice.EXTRA_DEVICE`, although the public Javadoc
for the action lists only the two state extras. **[INFER]** So the second earbud of a pair connecting
while the first is already up produces **no adapter-level event at all**. What it cannot see: any
transition that does not change the aggregate from 0 to ≥1 or back to 0.

**(c) Per-profile connection state, aggregate.**
`BluetoothAdapter.getProfileConnectionState(int profile)` — "This function can be used to check
whether the local Bluetooth adapter is connected to **any** remote device for a specific profile.
Profile can be one of `BluetoothProfile#HEADSET`, `BluetoothProfile#A2DP`." **[SDK]** The Javadoc
enumerates exactly two profiles, and it is a *profile-level* answer, not a device-level one.
**[AOSP-35]** the backing store is `AdapterProperties.java` l.102
`HashMap<Integer, Pair<Integer,Integer>> mProfileConnectionState` keyed by profile with a device
*count*, updated by l.910-959 `updateProfileConnectionState()`, whose comments document a lossy
collapse: "If numDevices is > 1 and one of the devices is changing state, decrement numDevices but
maintain oldState if it is Connected or Connecting"; the getter (l.690-698) simply returns `p.first`,
or `STATE_DISCONNECTED` when the map has no entry for that profile. **[INFER]** With multipoint audio
(`getMaxConnectedAudioDevices()` ≥ 2, public at API 33 **[SDK]**) one device dropping can leave the
profile reporting `STATE_CONNECTED`. This call cannot be a per-device signal.

**(d) Per-device, per-profile connection state.**
Answered only by a **profile proxy**: `BluetoothProfile.getConnectionState(BluetoothDevice)`,
`getConnectedDevices()`, `getDevicesMatchingConnectionStates(int[])` (all API 11 **[SDK]**), obtained
through `BluetoothAdapter.getProfileProxy(Context, ServiceListener, int)` which returns `boolean` and
delivers the object asynchronously to `ServiceListener.onServiceConnected(int, BluetoothProfile)`
**[SDK]**. What it does **not** answer: anything about a profile you did not bind, and nothing about
the ACL link. What it cannot see: profiles with no public proxy constructor (§6).

**(e) BLE / GATT link connection state.**
Answered by `BluetoothManager.getConnectedDevices(int profile)` /
`getDevicesMatchingConnectionStates(int profile, int[] states)` /
`getConnectionState(BluetoothDevice, int profile)` — all public since API 18 **[SDK]**
(`android/bluetooth/BluetoothManager` children inherit the class's `since="18"`).
**[AOSP-35]** is decisive about what these actually measure, and it is *not* "the device is
connected":

```java
// framework/java/android/bluetooth/BluetoothManager.java l.149-166, getDevicesMatchingConnectionStates
public List<BluetoothDevice> getDevicesMatchingConnectionStates(int profile, int[] states) {
    if (profile != BluetoothProfile.GATT && profile != BluetoothProfile.GATT_SERVER) {
        throw new IllegalArgumentException("Profile not supported: " + profile);   // l.151
    }
    ...
    IBluetoothGatt iGatt = mAdapter.getBluetoothGatt();
    if (iGatt == null) { return devices; }              // empty
    devices = iGatt.getDevicesMatchingConnectionStates(states, attributionSource)
```

`getConnectedDevices(int)` (l.125-127) is implemented as
`getDevicesMatchingConnectionStates(profile, new int[]{STATE_CONNECTED})` **[AOSP-35]**, so it
inherits the same restriction. And `getConnectionState(device, profile)` (l.99-108) is implemented as a
loop over `getConnectedDevices(profile)` returning `STATE_CONNECTED` on a match, else
`STATE_DISCONNECTED` **[AOSP-35]** — i.e. it has no `CONNECTING`/`DISCONNECTING` output at all.
**[SDK]** the shipped stub Javadoc already says `@param profile GATT or GATT_SERVER` on all three, so
the restriction is documented, just not in a form a reader expects.
What it does **not** answer: whether audio is up. A TWS pair streaming over A2DP with no GATT client
open is **absent** from this list. **[AOSP-35]** `gatt/GattService.java` l.1869-1882 builds
`connectedDevices` from `mClientMap.getConnectedDevices()` + `mServerMap.getConnectedDevices()` only.
What it cannot see: an LE link held open by the system for e.g. battery or BASS without a GATT
client for this device. Also worth noting: it is **system-wide, not caller-scoped** — the
attribution source is used for the permission check (l.1853-1856) but the connected-device set is the
whole adapter's. **[AOSP-35]**

**(f) Audio-profile connection state.** Two distinct things, both public, neither equal to (c)/(d):
- HFP SCO audio path: `BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED`
  (`"android.bluetooth.headset.profile.action.AUDIO_STATE_CHANGED"`, extras `EXTRA_STATE` /
  `EXTRA_PREVIOUS_STATE` / `EXTRA_DEVICE`, values `STATE_AUDIO_CONNECTING=11`,
  `STATE_AUDIO_CONNECTED=12`, `STATE_AUDIO_DISCONNECTED=10`), and
  `BluetoothHeadset.isAudioConnected(BluetoothDevice)` — "Check if Bluetooth SCO audio is connected."
  **[SDK]** This is *voice-channel* state, not profile-registration state.
- A2DP streaming state: `BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED` with `STATE_PLAYING=10` /
  `STATE_NOT_PLAYING=11`, and `BluetoothA2dp.isA2dpPlaying(device)` — "Check if A2DP profile is
  streaming music." **[SDK]** This is *media streaming*, not connection.
- LE Audio group membership (the closest thing to a documented "these two devices are one pair of
  earbuds"): `BluetoothLeAudio.getGroupId(device)` — "Devices with same group id belong to same group
  (i.e left and right earbud)"; `getConnectedGroupLeadDevice(int groupId)`; class doc "Android only
  supports one set of connected Bluetooth LeAudio device at a time." **[SDK]** All API 33 for the
  group members (`getGroupId`, `getConnectedGroupLeadDevice`, `GROUP_ID_INVALID` have `since="33"`,
  the class itself `since="31"`) **[SDK]**.
What none of these answer: whether the *device* is connected. What they cannot see: a classic
headset's media path (for SCO) or a LE Audio group (for the A2DP calls). **[INFER]**

**(g) Device availability ("known to this phone, currently not connected").**
There is **no API that answers this**. The nearest lawful construction is "in
`getBondedDevices()` and not in any connected set", plus the cached descriptors
`getName()`/`getAlias()`/`getType()`/`getBluetoothClass()`/`getUuids()`, each of which the stubs
document as returning `null`/`DEVICE_TYPE_UNKNOWN` "if there was a problem" / "if it's not
available" **[SDK]** — i.e. absence of a cached descriptor is indistinguishable from never having
met the device. What it cannot see: any device that is neither bonded nor currently connected is
**invisible without scanning**, and Phase 3 forbids scanning (§8, and `ACTION_FOUND` needs
`BLUETOOTH_SCAN` — §5). **[INFER]**

### Consequence for Phase 3

The domain model needs **six orthogonal fields per device** (bond, adapter-aggregate, profile-aggregate,
per-device-per-profile, GATT-link, audio-path) plus `UNKNOWN` on each, and a documented statement that
"connected" is not a defined term. A single `Boolean connected` on the device model is a defect the
moment it is written.

---

## 3. Q2 — enumeration: what can an app list, and is there one call for "all connected devices"

### Answer

**No — there is no single public call that returns "all connected Bluetooth devices".** You must
union across profile proxies whose identity you hard-code, and the two most tempting shortcuts are
broken in specific, provable ways: `BluetoothManager.getConnectedDevices(int)` throws for every
profile except GATT/GATT_SERVER, and `BluetoothAdapter.getSupportedProfiles()` — the one call that
would tell you *which* profiles to ask — is `@SystemApi`/`@hide` and privileged.

### Evidence

**3.1 The complete public enumeration surface, verified against `android.jar`.**
`BluetoothAdapter` exposes exactly **42 public methods** in the API-35 stub set **[SDK]**
(`api-versions.xml`, `android/bluetooth/BluetoothAdapter`, `method` children). Of those, the only ones
that observe devices are:

| Call | Since | What it enumerates |
|---|---|---|
| `getBondedDevices()` | 5 | bonded set only |
| `getProfileConnectionState(int)` | 14 | one aggregate `int`, no devices |
| `getProfileProxy(Context, ServiceListener, int)` | 11 | gateway to per-profile device lists |
| `closeProfileProxy(int, BluetoothProfile)` | 11 | teardown |
| `getMaxConnectedAudioDevices()` | 33 | a *capacity number*, not a device list; `-1` if service unreachable **[SDK]** |

**There is no `getConnectedDevices()` and no `getDevicesMatchingConnectionStates()` on
`BluetoothAdapter` in the public jar** — probed directly: `b'getConnectedDevices' in
android/bluetooth/BluetoothAdapter.class` → `False`, `b'getDevicesMatchingConnectionStates'` →
`False`. **[SDK]** (Those two names exist on `BluetoothAdapter` in AOSP-35 only as
`@SystemApi`/`@hide`; `registerBluetoothConnectionCallback` likewise — §4.2.)

`BluetoothManager` exposes exactly **5 public methods** **[SDK]**: `getAdapter`, `getConnectedDevices(I)`,
`getConnectionState(Landroid/bluetooth/BluetoothDevice;I)`,
`getDevicesMatchingConnectionStates(I[I)`, `openGattServer(...)`.

`BluetoothDevice` exposes exactly **21 public methods** **[SDK]**; the whole set is
`createRfcommSocketToServiceRecord`, `getAddress`, `getBluetoothClass`, `getBondState`, `getName`,
`createInsecureRfcommSocketToServiceRecord`, `fetchUuidsWithSdp`, `getUuids`, 5× `connectGatt`,
`getType`, `createBond`, `setPairingConfirmation`, `setPin`, `createInsecureL2capChannel`,
`createL2capChannel`, `getAlias`, `setAlias`, `getAddressType`.

**3.2 The profile constants reachable to a third-party app, API 26→35.**
From `api-versions.xml` / `android-stubs-src.jar` `android/bluetooth/BluetoothProfile` **[SDK]**:

| Constant | Value | `since` | Public proxy class | Class `since` |
|---|---|---|---|---|
| `HEADSET` | 1 | 11 | `BluetoothHeadset` | 11 |
| `A2DP` | 2 | 11 | `BluetoothA2dp` | 11 |
| `HEALTH` | 3 | 14 | `BluetoothHealth` | 14 — **deprecated**; stub carries `@Deprecated` on the constant |
| `GATT` | 7 | 18 | *(none)* | — |
| `GATT_SERVER` | 8 | 18 | *(none)* | — |
| `SAP` | 10 | 23 | `BluetoothSap` — **not public** (absent from the 64-class bluetooth list) | — |
| `HID_DEVICE` | 19 | 28 | `BluetoothHidDevice` | 28 |
| `HEARING_AID` | 21 | 29 | `BluetoothHearingAid` | 29 |
| `LE_AUDIO` | 22 | 33 | `BluetoothLeAudio` | 31 (class) |
| `CSIP_SET_COORDINATOR` | 25 | 33 | `BluetoothCsipSetCoordinator` | 33 |
| `HAP_CLIENT` | 28 | 33 | `BluetoothHapClient` — **not public** | — |

**Absent from the public constant set entirely** **[SDK]** (verified by enumerating every `field`
child of `android/bluetooth/BluetoothProfile`): `A2DP_SINK`, `AVRCP`, `AVRCP_CONTROLLER`, `HID_HOST`,
`PAN`, `PBAP`, `PBAP_CLIENT`, `MAP`, `MAP_CLIENT`, `OPP`, `LE_AUDIO_BROADCAST`,
`LE_AUDIO_BROADCAST_ASSISTANT`, `VOLUME_CONTROL`, `LE_CALL_CONTROL`. **[AOSP-35]** those exist but are
`@hide @SystemApi` in the same interface (`BluetoothProfile.java` l.100 `HID_HOST = 4`, l.107
`PAN = 5`, l.114 `PBAP = 6`, l.127 `MAP = 9`, l.137 `A2DP_SINK = 11`, l.144 `AVRCP_CONTROLLER = 12`,
l.158 `HEADSET_CLIENT = 16`, l.165 `PBAP_CLIENT = 17`, l.172 `MAP_CLIENT = 18`, l.182 `OPP = 20`,
l.195 `VOLUME_CONTROL = 23`, l.210 `LE_AUDIO_BROADCAST = 26`, l.228
`LE_AUDIO_BROADCAST_ASSISTANT = 29`).

**3.3 Which profile constants actually produce a working proxy.**
**[AOSP-35]** `framework/java/android/bluetooth/BluetoothAdapter.java` l.818 defines
`PROFILE_CONSTRUCTORS` as a 21-entry map. Intersecting that map with the public constant set above:

- **Work**: `HEADSET(1)`, `A2DP(2)`, `HID_DEVICE(19)`, `HEARING_AID(21)`, `LE_AUDIO(22)`,
  `CSIP_SET_COORDINATOR(25)`, `SAP(10)`, `HAP_CLIENT(28)`.
- **Return `false` immediately**: `HEALTH(3)` — `if (profile == BluetoothProfile.HEALTH) { Log.e(…,
  "getProfileProxy(): BluetoothHealth is deprecated"); return false; }`; `HEARING_AID(21)` when
  `!isHearingAidProfileSupported()`; and any profile with no constructor entry.
- **Documented-but-not-implemented**: **`GATT(7)` and `GATT_SERVER(8)` have no entry in
  `PROFILE_CONSTRUCTORS`** (`'BluetoothProfile.GATT' in PROFILE_CONSTRUCTORS block` → `False`), yet
  the shipped public Javadoc for `getProfileProxy` states verbatim: "Profile can be one of
  `BluetoothProfile#HEADSET`, `BluetoothProfile#A2DP`, **`BluetoothProfile#GATT`**,
  `BluetoothProfile#HEARING_AID` or `BluetoothProfile#GATT_SERVER`." **[SDK]**
  **[AOSP-35]** falls through to `Log.e("getProfileProxy(): Unknown profile " + profile); return false;`.
  **The two sources disagree.** I follow **[AOSP-35]** for *what happens* and **[SDK]** for *what we
  are allowed to compile*, and record the mismatch as device-checkable item U-3 rather than resolving
  it silently — the asymmetry itself is the deliverable: the only API that reaches GATT-layer
  connection state is `BluetoothManager`, which is a different object with a different lifecycle.

**3.4 The enumeration gap, stated in the words the brief asked for.**
`BluetoothAdapter.getSupportedProfiles()` — "Gets the currently supported profiles by the adapter.
This can be used to check whether a profile is supported before attempting to connect to its
respective proxy." — is `@hide` + `@SystemApi` +
`@RequiresPermission(allOf = {BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED})` **[AOSP-35]** l.2954, and is
absent from `android.jar` **[SDK]**. `BluetoothAdapter.getMostRecentlyConnectedDevices()` — "Fetches a
list of the most recently connected bluetooth devices ordered by how recently they were connected" —
is likewise `@hide`/`@SystemApi`/`allOf{BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED}` **[AOSP-35]**
l.2885. **[INFER]** Therefore **Phase 3 cannot ask the platform which profiles exist on this
handset**; it must carry its own profile list, and that list is a compile-time constant chosen by us,
which is exactly the assumption most likely to be wrong on an unusual OEM build.

**3.5 What a correct union looks like, and what it still misses.**
**[INFER]** from §2 and §3.3: the maximal lawful "connected devices" set at API 35 is

```
bonded = adapter.bondedDevices()                                    // API 5
gatt   = manager.getDevicesMatchingConnectionStates(GATT, [CONNECTED]) // API 18
audio  = ∪ over {A2DP, HEADSET, LE_AUDIO, HEARING_AID, CSIP_SET_COORDINATOR}
             proxy(p).devicesMatching([CONNECTED])                   // API 11/29/33/33/33
acl    = not enumerable — events only (§4), there is no getAclConnectedDevices()
```

unioned by `BluetoothDevice.getAddress()`. It misses: a device with an ACL link but no profile in the
union and no GATT client; and every profile the OEM added under a hidden constant. **[INFER]**

### Consequence for Phase 3

The engine is a **reconciling union over a hard-coded profile list**, and "no connected devices" can
only be reported as `NOT_OBSERVABLE_VIA_LISTED_PROFILES`, never as `NONE`. Phase 3 must define a
"profile registry" with a `SUPPORTED / NOT_SUPPORTED_BY_PLATFORM / UNKNOWN` value per profile, and
must document `BluetoothManager.getConnectedDevices(int)` as **GATT-only** — passing `A2DP` there is
an `IllegalArgumentException`, not an empty list (§6.1).

---

## 4. Q3 — events: which broadcasts a normal app can actually receive

### Answer

**Event-driven observation is possible**, and it does not require polling — but only for four
actions, all of which need `BLUETOOTH_CONNECT` granted and a **context-registered receiver flagged
`RECEIVER_EXPORTED`**. `BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED` is real and deliverable but
is an aggregate edge trigger, so it cannot be the primary signal. The per-profile connection-state
actions are the workable events; ACL connect/disconnect are the closest thing to a device-level
link event. **None of the actions needed here is `@SystemApi`-only** — but the one API that would
have made all this unnecessary (`BluetoothAdapter.registerBluetoothConnectionCallback`) is.

### 4.1 Existence, API level and receiver permission, per action

All rows: existence and `since` from the compileSdk 35 platform data **[SDK]**; the receiver-permission
column from **[AOSP-35]** where the sender was located, else from the shipped stub Javadoc.

| Action string | Constant | `since` | In SDK `broadcast_actions.txt`? | Sender-side receiver permission | Stub permission sentence |
|---|---|---|---|---|---|
| `android.bluetooth.adapter.action.CONNECTION_STATE_CHANGED` | `BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED` | 11 | yes (l.65) | `BLUETOOTH_CONNECT` — `sendBroadcastAsUser(intent, UserHandle.ALL, BLUETOOTH_CONNECT, …)` **[AOSP-35]** `AdapterProperties.java` l.772-784 | "For apps targeting … S or higher … requires `BLUETOOTH_CONNECT`" |
| `android.bluetooth.adapter.extra.CONNECTION_STATE` / `.PREVIOUS_CONNECTION_STATE` | `EXTRA_CONNECTION_STATE`, `EXTRA_PREVIOUS_CONNECTION_STATE` | 11 | n/a | n/a | no permission listed on the extras |
| `android.bluetooth.device.action.ACL_CONNECTED` | `BluetoothDevice.ACTION_ACL_CONNECTED` | 5 (class level) | yes (l.71) | `BLUETOOTH_CONNECT` — `mAdapterService.sendBroadcast(intent, BLUETOOTH_CONNECT, …)` **[AOSP-35]** `RemoteDevices.java` l.1304-1305 (inside `aclStateChangeCallback`, l.1173) | requires `BLUETOOTH_CONNECT` (t≥31) / `BLUETOOTH` (t≤30) |
| `android.bluetooth.device.action.ACL_DISCONNECTED` | `ACTION_ACL_DISCONNECTED` | 5 | yes (l.72) | `BLUETOOTH_CONNECT` — same call site l.1304-1305; separately `RemoteDevices.java` l.203-208 (`reset()`) | same |
| `android.bluetooth.device.action.ACL_DISCONNECT_REQUESTED` | `ACTION_ACL_DISCONNECT_REQUESTED` | 5 | yes (l.73) | not located in this session | same |
| `android.bluetooth.device.action.BOND_STATE_CHANGED` | `ACTION_BOND_STATE_CHANGED` | 5 | yes (l.75) | `BLUETOOTH_CONNECT` (pattern, not individually located) | requires `BLUETOOTH_CONNECT` (t≥31) |
| `android.bluetooth.device.action.NAME_CHANGED` | `ACTION_NAME_CHANGED` | 5 | yes (l.78) | not located | requires `BLUETOOTH_CONNECT` |
| `android.bluetooth.device.action.CLASS_CHANGED` | `ACTION_CLASS_CHANGED` | 5 | yes (l.76) | not located | requires `BLUETOOTH_CONNECT` |
| `android.bluetooth.device.action.ALIAS_CHANGED` | `ACTION_ALIAS_CHANGED` | 30 | yes (l.74) | not located | requires `BLUETOOTH_CONNECT` |
| `android.bluetooth.device.action.FOUND` | `ACTION_FOUND` | 5 | yes (l.77) | — | requires **`BLUETOOTH_SCAN`** (t≥31) **plus** `ACCESS_FINE_LOCATION` unless `neverForLocation` — quoted verbatim in §5.3 |
| `android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED` | `BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED` | 11 | yes (l.61) | `BLUETOOTH_CONNECT` by the stub requirement; **the individual send site was not located in this session** — the only A2DP send sites I read are `A2dpService.java` l.1163-1181 (`ACTION_ACTIVE_DEVICE_CHANGED`, `ACTION_CODEC_CONFIG_CHANGED`), both `sendBroadcast(intent, BLUETOOTH_CONNECT, …)` | requires `BLUETOOTH_CONNECT` |
| `android.bluetooth.a2dp.profile.action.PLAYING_STATE_CHANGED` | `BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED` | — | yes (l.62) | as above | requires `BLUETOOTH_CONNECT` |
| `android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED` | `BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED` | — | yes (l.83) | as above | requires `BLUETOOTH_CONNECT` |
| `android.bluetooth.headset.profile.action.AUDIO_STATE_CHANGED` | `BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED` | — | yes (l.82) | as above | requires `BLUETOOTH_CONNECT` |
| `android.bluetooth.hearingaid.profile.action.CONNECTION_STATE_CHANGED` | `BluetoothHearingAid.ACTION_CONNECTION_STATE_CHANGED` | 29 | yes (l.84) | not located | requires `BLUETOOTH_CONNECT` |
| `android.bluetooth.action.LE_AUDIO_CONNECTION_STATE_CHANGED` | `BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED` | 31 | yes (l.64) | not located | requires `BLUETOOTH_CONNECT` |
| `android.bluetooth.action.CSIS_CONNECTION_STATE_CHANGED` | `BluetoothCsipSetCoordinator.ACTION_CSIS_CONNECTION_STATE_CHANGED` | 33 | yes (l.63) | not located | "Requires `BLUETOOTH_CONNECT`" (stub lists **only** the S form; no t≤30 sentence — the constant is API 33 so that is consistent) |

Negative results, checked directly against `android.jar`'s `BluetoothDevice.class` and
`BluetoothAdapter.class` constant pools **[SDK]**: `ACTION_META_CHANGED`, `getMetadata`, `ACTION_BLE_ACL_CONNECTED`,
`BluetoothAdapter$BluetoothConnectionCallback` and `registerBluetoothConnectionCallback` are **all
absent from the public jar**. `BluetoothDevice`'s own 21-method list (§3.1) has no `isConnected()`.
**A name that is absent from `android.jar` is not usable by a third-party app, and that is a finding.**

### 4.2 The privileged-only mechanism that would have solved this

**[AOSP-35]** `framework/java/android/bluetooth/BluetoothAdapter.java` l.4716-4800:

```java
/**
 * Registers the BluetoothConnectionCallback to receive callback events when a bluetooth device
 * (classic or low energy) is connected or disconnected.
 * ...
 * @hide
 */
@SystemApi
@RequiresBluetoothConnectPermission
@RequiresPermission(allOf = {BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED})
public boolean registerBluetoothConnectionCallback(Executor executor, BluetoothConnectionCallback callback)

@SystemApi
public abstract static class BluetoothConnectionCallback {
    public void onDeviceConnected(BluetoothDevice device) {}
    public void onDeviceDisconnected(BluetoothDevice device, @DisconnectReason int reason) {}
}
```

**[INFER]** This is the API Phase 3 would actually want — a per-device connected/disconnected callback
with a *disconnect reason*. It is unreachable twice over: `@SystemApi`/`@hide` (not compilable) and
`BLUETOOTH_PRIVILEGED` (`signature|privileged`) even at runtime. `BLUETOOTH_PRIVILEGED`'s protection
level was read this session from `frameworks/base/core/res/AndroidManifest.xml` at tag
`android-15.0.0_r11`: `android:protectionLevel="signature|privileged"` for `BLUETOOTH_PRIVILEGED`,
`dangerous` for `BLUETOOTH_CONNECT`/`_SCAN`/`_ADVERTISE`, `normal` for `BLUETOOTH`/`BLUETOOTH_ADMIN`,
`dangerous|instant` for `ACCESS_FINE_LOCATION`, `signature|privileged` for `LOCAL_MAC_ADDRESS`.
**[AOSP-35]**

### 4.3 Export-flag requirements — the asymmetry Phase 2 left open, now closed differently

Phase 2 R-1 concluded "ACTION_STATE_CHANGED is a system broadcast → **do not** add the flag". For
Phase 3 that conclusion is **not safe**, and the two official pages disagree in a way that matters.

- **[DOC]** Behavior changes: apps targeting Android 14 — verbatim: "Apps and services that target
  Android 14 (API level 34) or higher and use context-registered receivers are **required** to specify
  a flag … either `RECEIVER_EXPORTED` or `RECEIVER_NOT_EXPORTED`", with subsection "**Exception for
  receivers that receive only system broadcasts** — If your app is registering a receiver only for
  system broadcasts through `Context#registerReceiver` methods … then it **shouldn't specify a flag**
  when registering the receiver."
- **[DOC]** Broadcasts guide — verbatim: "**Some system broadcasts come from highly privileged apps,
  such as Bluetooth and telephony**, that are part of the Android framework but **don't run under the
  system's unique process ID (UID)**. To receive all system broadcasts, including broadcasts from
  highly privileged apps, **flag your receiver with `RECEIVER_EXPORTED`**." and "If you flag your
  receiver with `RECEIVER_NOT_EXPORTED`, the receiver is able to receive some system broadcasts and
  broadcasts from your app, **but not broadcasts from the highly privileged apps**."
- **[SDK]** The action strings Phase 3 needs **are** all in `broadcast_actions.txt` (verified line by
  line in §4.1), which is the list the flag exemption refers to.
- **[AOSP-35]** Corroborates the second quote: the sender is the Bluetooth app process — the calls are
  `mService.sendBroadcastAsUser(intent, UserHandle.ALL, BLUETOOTH_CONNECT, …)` inside
  `com.android.bluetooth.btservice` / `com.android.bluetooth.a2dp`, i.e. a separate package UID, not
  `system_server`.

**Resolution, and which I follow.** These are not actually contradictory: "not *required* to specify a
flag" ≠ "safe to specify `RECEIVER_NOT_EXPORTED`". **[INFER]** I follow the broadcasts guide for the
*choice* and the behavior-change page for the *obligation*. Operationally, for a
`targetSdk ≥ 34` app observing Bluetooth:

1. Registering with the flag-free `registerReceiver(receiver, filter)` overload satisfies the
   requirement (system-broadcast exemption) and does not opt out of delivery. **[DOC]** + **[INFER]**
2. Registering via `ContextCompat.registerReceiver(...)` **must pass `RECEIVER_EXPORTED`**, because
   `ContextCompat` forces one of the two flags and `RECEIVER_NOT_EXPORTED` is documented to suppress
   highly-privileged-app broadcasts. **[DOC]**
3. **Never pass `RECEIVER_NOT_EXPORTED` for a Bluetooth action.** **[DOC]**
4. The constants `RECEIVER_EXPORTED`/`RECEIVER_NOT_EXPORTED` exist at API 33, the requirement is keyed
   to `targetSdkVersion ≥ 34` — Phase 2's off-by-one correction stands. **[DOC]**

Whether the *flag-free* overload really delivers Bluetooth broadcasts on every API-34/35 build is
device-level knowledge: **U-1**.

### 4.4 Manifest-declared receivers: only four of these actions are usable that way

**[DOC]** Broadcasts guide: "If your app targets Android 8.0 or higher, you cannot use the manifest to
declare a receiver for most implicit broadcasts (broadcasts that don't target your app specifically)."
The exemption list is published and I read it this session. Verbatim members relevant to us:
`BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED`, `BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED`,
`ACTION_ACL_CONNECTED`, `ACTION_ACL_DISCONNECTED`. **[DOC]**

**[INFER]** Consequences, stated as the exclusions they are:
`BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED`, `ACTION_BOND_STATE_CHANGED`,
`ACTION_NAME_CHANGED`, `ACTION_CLASS_CHANGED`, `ACTION_ALIAS_CHANGED`,
`ACTION_ACL_DISCONNECT_REQUESTED`, `ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED`,
`ACTION_CSIS_CONNECTION_STATE_CHANGED` and the hearing-aid connection action are **not** on that
exemption list, so they reach a **context-registered receiver only**. Since our module is a library
with no component declarations (and the manifest is machine-checked to stay that way), and since a
manifest receiver could not hold a runtime permission grant before first launch anyway, Phase 3's
receiver lifecycle should be context-registration, scoped to the observer's own lifetime.
The same page also warns, quoted: "note que … despite these implicit broadcasts remaining available in
the background, avoid registering listeners for them when possible" — i.e. even for the four exempt
actions, a manifest receiver is not the recommended shape. **[DOC]**

### 4.5 Three delivery caveats the public docs do not mention

- **[AOSP-35]** Every one of these intents is sent with
  `Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT` (and ACL additionally with
  `FLAG_RECEIVER_INCLUDE_BACKGROUND`, `RemoteDevices.java` l.1301-1302). The
  `REGISTERED_ONLY_BEFORE_BOOT` flag only restricts delivery *before* boot completes, so after
  `BOOT_COMPLETED` a manifest receiver is not excluded by the flag —
  but the implicit-broadcast rule of §4.4 still applies. **[INFER]** Practical reading: an observer that
  starts at boot has no ACL events for links that came up before it registered; it must take a
  snapshot on start. That is the documented-collision of "event-driven" with "must not poll", and it
  is why the snapshot-on-start in the Consequence line below is not optional.
- **[AOSP-35]** `RemoteDevices.java` l.1202-1210 (`aclStateChangeCallback`, from l.1173):
  `ACTION_ACL_CONNECTED` is only built when
  `mAdapterService.getState()` is `STATE_ON`/`STATE_TURNING_ON`; in `STATE_BLE_ON` the *hidden*
  `BluetoothAdapter.ACTION_BLE_ACL_CONNECTED` is used instead, and that constant is **absent from
  `android.jar`** (§4.1). **[INFER]** During the BLE-only ("Bluetooth off, scanner on") state an app
  sees **no ACL event at all**.
- **[AOSP-35]** `AdapterProperties.java` l.957 calls `invalidateGetProfileConnectionStateCache()`
  (wrapper l.289-291) → `BluetoothAdapter.invalidateGetProfileConnectionStateCache()` l.3079; the
  value returned by `getProfileConnectionState` is served from a **per-process IPC cache**
  (`IpcDataCache`, `PROFILE_API = "BluetoothAdapter_getProfileConnectionState"`,
  `BluetoothAdapter.java` l.3066-3070) and is invalidated by the Bluetooth app pushing an
  invalidation. **[INFER]** So a cached `CONNECTED` can be read briefly after the truth changed; and
  conversely reading it is *cheap*, which is good news for a snapshot-on-event design and bad news for
  a "read it in a loop and you'll catch the change" design.

### 4.6 Bond-state events, and the Android 16 nudge

**[SDK]** `ACTION_BOND_STATE_CHANGED` is public since API 5, requires `BLUETOOTH_CONNECT` for t≥31,
and "Always contains the extra fields `EXTRA_DEVICE`, `EXTRA_BOND_STATE` and
`EXTRA_PREVIOUS_BOND_STATE`". **[DOC]** Behavior changes: apps targeting Android 16 lists, under its
Bluetooth-stack section, the sentence "The app can monitor connection state changes by listening to
BluetoothDevice `ACTION_BOND_STATE_CHANGED`" (rendered in a non-English locale on the page I fetched;
constant names verbatim). **[INFER]** That sentence conflates bond state with connection state — a
device can be bonded and disconnected, and connected without bonding. I do **not** follow it as a
design instruction; I record it as evidence that even Google's own prose blurs the §2 concepts, which
is a reason for Phase 3 to name its six fields distinctly. It is also the same page that documents
`CompanionDeviceManager.removeBond(int)` as a new *disconnect/unpair* affordance — see §8.

### Consequence for Phase 3

Event-driven observation is viable with **context-registered, `RECEIVER_EXPORTED` (or flag-free)
receivers for: the five `*profile*.action.CONNECTION_STATE_CHANGED` actions +
`ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED` + `ACTION_CSIS_CONNECTION_STATE_CHANGED` +
`ACTION_ACL_CONNECTED`/`ACTION_ACL_DISCONNECTED` + `ACTION_BOND_STATE_CHANGED`**, plus
`ACTION_STATE_CHANGED` for adapter power (Phase 2). The architecture is
**snapshot-on-start + event-driven-delta + re-snapshot-on-permission-grant-and-on-adapter-on**, which
satisfies "no aggressive polling": one read burst per lifecycle edge, not a timer.

---

## 5. Q4 — what a `BluetoothDevice` legitimately exposes with no GATT and no probe

### Answer

Name, alias, address, address type, device type, class, bond state and cached UUIDs — all reachable
with `BLUETOOTH_CONNECT` (t≥31) and no air traffic. **`getMetadata(int)` is not reachable at all**:
it is `@SystemApi`/`@hide` and needs `BLUETOOTH_PRIVILEGED`. The class is documented by the platform
as an *unreliable hint* and the platform's own heuristic "errs on the side of false positives", which
means **nothing in `BluetoothClass` proves earbud-ness**. The address is the only stable-looking key
and it is documented as a plain hardware-address string with **no** privacy caveat anywhere in the
public surface — the risk is real and is recorded as U-2 rather than resolved.

### Evidence

| Member | `since` | Permission (stub, verbatim pattern) | Documented failure value | What it does **not** prove |
|---|---|---|---|---|
| `getAddress()` | 5 | **none — the stub carries no permission sentence at all** **[SDK]**; **[AOSP-35]** l.1544 `public String getAddress() { return mAddress; }` with **no** `@RequiresPermission` | n/a (pure field read) | identity across time; see §5.4 |
| `getAddressType()` | **35** | none **[SDK]**; returns one of `ADDRESS_TYPE_PUBLIC(0)`, `ADDRESS_TYPE_RANDOM(1)`, `ADDRESS_TYPE_ANONYMOUS(255)`, `ADDRESS_TYPE_UNKNOWN(65535)` | `ADDRESS_TYPE_UNKNOWN` = "Address type is unknown or unavailable" | — and it is **not available below API 35**, so a key strategy cannot depend on it for minSdk 26 |
| `getName()` | 5 | `BLUETOOTH` (t≤30) / `BLUETOOTH_CONNECT` (t≥31) | "or null if there was a problem"; and "The local adapter will automatically retrieve remote names when performing a device scan, and will cache them. **This method just returns the name for this device from the cache.**" | the device's type — and Phase 3's prompt already forbids inferring type from a name |
| `getAlias()` | **30** | same pair | "the Bluetooth alias, **the friendly device name if no alias**, or null if there was a problem" | anything about the device: it is "the **locally modifiable** name" **[SDK]** — user-editable text, and it silently falls back to `name` |
| `getType()` | 18 | same pair | "`DEVICE_TYPE_UNKNOWN` if it's not available" | earbud-ness. Values are `DEVICE_TYPE_CLASSIC(1)`, `DEVICE_TYPE_LE(2)`, `DEVICE_TYPE_DUAL(3)`, `DEVICE_TYPE_UNKNOWN(0)` — transport capability only |
| `getBluetoothClass()` | 5 | same pair | "Bluetooth class object, **or null on error**" | see §5.2 |
| `getBondState()` | 5 | same pair | — | connection (§2a) |
| `getUuids()` | 15 | same pair | "or null on error"; and: "**This method does not start a service discovery procedure** to retrieve the UUIDs from the remote device. Instead, the **local cached copy** of the service UUIDs are returned." **[SDK]** | freshness — it is a cache |
| `getMetadata(int)` | — | **not public.** **[AOSP-35]** l.3238 `@SystemApi @RequiresPermission(allOf = {BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED}) public @Nullable byte[] getMetadata(@MetadataKey int key)`; `setMetadata` l.3210 identical annotations. Absent from `android.jar` and from `api-versions.xml` **[SDK]** | — | — |

**5.1 Cached-descriptor trap.** **[INFER]** `getName()`, `getUuids()` and (below API 35) `getType()`
are documented cache reads. For a bonded-but-never-contacted device they can legitimately be `null` /
`UNKNOWN` while the device is perfectly present. An observer that maps `null` name to "unknown device"
will fabricate a device state. Also **[AOSP-35]** `BluetoothDevice.getName()`'s sibling doc says the
cache is filled by **scanning**, which Phase 3 does not do — so cold-cache is the normal case, not the
exception.

**5.2 What a `BluetoothClass` actually proves — the platform's own answer.** The shipped class Javadoc
is verbatim, and it forecloses the design:

> "**BluetoothClass is useful as a hint to roughly describe a device (for example to show an icon in
> the UI), but does not reliably describe which Bluetooth profiles or services are actually supported
> by a device.** Accurate service discovery is done through SDP requests, which are automatically
> performed when creating an RFCOMM socket…" **[SDK]**

and, on the one method that would let us ask "is this an A2DP device":

> "Check class bits for possible bluetooth profile support. This is a **simple heuristic** that tries
> to guess if a device with the given class bits might support specified profile. **It is not accurate
> for all devices. It tries to err on the side of false positives.**" — `doesClassMatch(int profile)`,
> **API 33** **[SDK]** (`android/bluetooth/BluetoothClass` → `doesClassMatch(I)Z since="33"`; the
> profile argument constants `PROFILE_HEADSET=0`, `PROFILE_A2DP=1`, `PROFILE_HID=3` are also `since="33"`)

Available without `doesClassMatch`: `getMajorDeviceClass()`, `getDeviceClass()`, `hasService(int)`
(all API 5 **[SDK]**). Earbud-shaped values that exist:
`Device.AUDIO_VIDEO_WEARABLE_HEADSET`, `Device.AUDIO_VIDEO_HEADPHONES` (= 1048),
`Device.AUDIO_VIDEO_UNCATEGORIZED` (= 1024), `Device.Major.AUDIO_VIDEO`, `Service.AUDIO`,
`Service.TELEPHONY`, and — API 33 — `Service.LE_AUDIO`. **[SDK]** **[INFER]** Note that a TWS pair can
report `HEADPHONES`, `WEARABLE_HEADSET`, `UNCATEGORIZED` or a stale CoD interchangeably across
firmware updates, and `Service.LE_AUDIO` is a *service bit* not a connection fact; so no single value
is decisive in either direction. CoD may be used **only as a tie-breaking hint with a documented
provenance label**, never as a classification. Phase 3's prompt already bans name-based typing; this
extends the ban to CoD-based typing.

**5.3 `ACTION_FOUND` is the only location-gated device broadcast.** Aggregated permission-mention
counts over the API-35 stub sources **[SDK]** (count of `Manifest.permission#X` occurrences per file):
`BluetoothAdapter` — `BLUETOOTH_CONNECT` 45, `BLUETOOTH_SCAN` 33, `BLUETOOTH_ADMIN` 16,
`BLUETOOTH_ADVERTISE` 3, `ACCESS_FINE_LOCATION` 6, `LOCAL_MAC_ADDRESS` 1;
`BluetoothDevice` — `BLUETOOTH_CONNECT` 83, `BLUETOOTH_ADMIN` 8, `BLUETOOTH_SCAN` 3,
`ACCESS_FINE_LOCATION` 2, `BLUETOOTH_PRIVILEGED` 2; `BluetoothManager` — `BLUETOOTH_CONNECT` 12,
nothing else; `BluetoothProfile` and `BluetoothClass` — **nothing at all**. Mapping each location
mention to its member **[SDK]**: all six on `BluetoothAdapter` land on `startDiscovery` and
`startLeScan`; both in `BluetoothDevice` land on `ACTION_FOUND`; `BLUETOOTH_PRIVILEGED` lands on
`setAlias` and `setPairingConfirmation`; `LOCAL_MAC_ADDRESS` on `BluetoothAdapter.getAddress`.
**[INFER]** Therefore: **no connection-observation member in the public surface mentions location**,
and `neverForLocation` changes nothing for Phase 3's operations — it only relaxes `BLUETOOTH_SCAN`'s
location requirement, which Phase 3 should not declare at all. Corroborating **[DOC]**
(Bluetooth permissions guide): the `neverForLocation` instruction is given in the context of
`BLUETOOTH_SCAN` only, together with its two stated costs — "If you include `neverForLocation` in your
`android:usesPermissionFlags`, **some BLE beacons are filtered from the scan results**" and, per the
per-method text, "it may restrict the types of Bluetooth devices you can interact with". **[SDK]**

**5.4 Address as an identity — what the docs actually say, and what AOSP shows.**
**[SDK]** `getAddress()`'s complete public documentation is: "Returns the hardware address of this
BluetoothDevice. For example, '00:11:22:AA:BB:CC'. @return Bluetooth hardware address as string."
That is the whole entry: no privacy qualifier, no stability guarantee, no resolvable/masked
distinction, and no permission. **[AOSP-35]** supplies the counter-evidence the public docs omit:
`BluetoothDevice` is described in its own class doc as "really just a thin wrapper for a Bluetooth
hardware address"; `AdapterProperties.java` l.618-672 (`cleanupPrevBondRecordsFor`) resolves a *separate*
identity address via `mService.getIdentityAddress(address)` and comments "Found an existing **LE-only**
device with the same **identity address** but different **pseudo address**", removing the stale bond
record; and `btservice/AdapterService.java` l.2401-2414 exposes `getIdentityAddress` behind
`service.enforceCallingOrSelfPermission(BLUETOOTH_PRIVILEGED, null)`. So the platform knows that the
address in a bond record may be a rotating random address, **and keeps the resolving API
privileged-only**. `BluetoothAdapter.getRemoteLeDevice(String address, int addressType)` (API 33
**[SDK]**) further confirms that an LE address plus an address type is treated as the real key.
**[INFER]** For **bonded classic / dual-mode** devices the address is the BR/EDR address and is
effectively stable — that is the case Phase 3 mostly cares about (earbuds you have paired). For
**LE-only, never-bonded** devices the value may rotate and `getAddress()` alone is not a durable key.
Nothing in the public documentation states this, so it is not something to assert: it is U-2, and the
design must be written so that a wrong answer to U-2 degrades into "device appears twice", not
"device identity silently merged".

### Consequence for Phase 3

Use only `getAddress()` + `getBondState()` + `getType()` + `getBluetoothClass()` + `getName()` +
`getAlias()` (guarded `SDK_INT ≥ 30`) + `getUuids()`, each tagged with a per-field
`PRESENT / ABSENT_CACHED / UNKNOWN` provenance. Drop `getMetadata` from the design entirely. Do not
use `getAddressType()` as a key input (API 35). Class of device may annotate, never classify. The
stable key question (§9 of the Phase 3 prompt) must be answered as "address, scoped to bonded devices,
with an explicit duplicate-on-rotate failure mode" — not as "address is a global identity".

---

## 6. Q5 — permissions, enforcement, and the empty-vs-absent hazard

### Answer

**Yes — a denied permission produces a result indistinguishable from "no devices connected", on every
one of the enumeration paths, and this is not speculation: it is what the API-35 reference
implementation does in code I read this session.** `SecurityException` is the *rare* outcome here, not
the common one. The observer therefore cannot infer anything from emptiness, and must gate every read
on `checkSelfPermission(BLUETOOTH_CONNECT) == GRANTED` plus `getState() == STATE_ON`.

### 6.1 What happens on denial, per call

| Mechanism | On missing `BLUETOOTH_CONNECT` (t≥31) | Evidence |
|---|---|---|
| `BluetoothAdapter.getBondedDevices()` | **empty set** — the service returns `Collections.emptyList()` | **[AOSP-35]** `btservice/AdapterService.java` l.2594-2603 `getBondedDevices(AttributionSource)`: `if (service == null || !Utils.checkConnectPermissionForDataDelivery(service, source, "AdapterService getBondedDevices")) { return Collections.emptyList(); }`. Client side adds a second identical-looking empty: `BluetoothAdapter.java` l.2917 `if (getState() != STATE_ON) return toDeviceSet(Arrays.asList());` and `return null;` on the fall-through path — matching the shipped doc "**or null on error**" plus "If Bluetooth state is not `STATE_ON`, this API will return an empty set" **[SDK]** |
| `BluetoothAdapter.getProfileConnectionState(p)` | **`STATE_DISCONNECTED`** | **[AOSP-35]** `AdapterService.java` l.2622-2641: `checkConnect = CompatChanges.isChangeEnabled(ENFORCE_CONNECT, callingUid); if (service == null || !callerIsSystemOrActiveOrManagedUser(...) || (checkConnect && !Utils.checkConnectPermissionForDataDelivery(...))) return BluetoothProfile.STATE_DISCONNECTED;` — four distinct causes, one value. Client side l.3094 adds a fifth: `if (getState() != STATE_ON) return STATE_DISCONNECTED;` **[SDK]** **[DOC]** the targetSdk gate is the Android 14 change, verbatim: "Android 14 enforces the `BLUETOOTH_CONNECT` permission when calling the `BluetoothAdapter` `getProfileConnectionState()` method for apps targeting Android 14 (API level 34) or higher. This method already required the `BLUETOOTH_CONNECT` permission, but it was not enforced." and `ChangeIds.java` l.28-30 `@ChangeId @EnabledSince(targetSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE) public static final long ENFORCE_CONNECT = 211757425L;` |
| `BluetoothManager.getDevicesMatchingConnectionStates(GATT, …)` / `getConnectedDevices(GATT)` | **empty list** | **[AOSP-35]** `gatt/GattService.java` l.1851-1856 `if (!Utils.checkConnectPermissionForDataDelivery(this, attributionSource, "GattService getDevicesMatchingConnectionStates")) return Collections.emptyList();` and `BluetoothManager.java` javadoc verbatim: "**The list will be empty on error.**" **[SDK]** |
| `BluetoothManager.getConnectionState(device, GATT)` | **`STATE_DISCONNECTED`** | **[AOSP-35]** implemented as "loop over `getConnectedDevices(profile)`; if no match, `return BluetoothProfile.STATE_DISCONNECTED;`" — so permission-denied (empty list) *becomes* `STATE_DISCONNECTED`. **[SDK]** stub: same pair, "Value is STATE_DISCONNECTED / CONNECTING / CONNECTED / DISCONNECTING" |
| `BluetoothProfile` proxy `getConnectedDevices()` / `getDevicesMatchingConnectionStates()` | **empty list**, "The list will be empty on error" **[SDK]**; `BluetoothA2dp`/`BluetoothHeadset`/`BluetoothLeAudio`/`BluetoothHearingAid` all repeat "Requires `BLUETOOTH_CONNECT`" per method **[SDK]** |
| `BluetoothDevice.getName/getAlias/getType/getBluetoothClass/getBondState/getUuids` | each documented as `null` / "`DEVICE_TYPE_UNKNOWN` if it's not available" / "or null on error" **[SDK]** |
| Any of the §4.1 broadcasts | **silently no event** — the senders pass `BLUETOOTH_CONNECT` as the *receiver* permission (`sendBroadcast(intent, BLUETOOTH_CONNECT, …)` **[AOSP-35]** `RemoteDevices.java` l.1302, `A2dpService.java` l.1169/1180, `AdapterProperties.java` l.782), so an ungranted receiver is simply not eligible. There is no error, no callback, no `onReceive`. |
| `BluetoothAdapter.getProfileProxy(...)` | `SecurityException("Need BLUETOOTH permission")` **only** when `context.getApplicationInfo().targetSdkVersion < Build.VERSION_CODES.S && context.checkSelfPermission(BLUETOOTH) != PERMISSION_GRANTED` — **[AOSP-35]** `BluetoothAdapter.java` l.3643-3649, with the comment "Preserve legacy compatibility where apps were depending on `registerStateChangeCallback()` performing a permissions check which has been relaxed in modern platform versions". Otherwise it returns `false` and the listener is never called. **[SDK]** "return true on success, false on error" |
| `BluetoothAdapter.getAddress()` (local adapter) | needs `BLUETOOTH_CONNECT` **and** `LOCAL_MAC_ADDRESS` (`signature|privileged`) **[SDK]**/**[AOSP-35]** → effectively unavailable; `enforceCallingOrSelfPermission` *does* throw |

**6.2 The only throwing paths.** **[AOSP-35]** `BluetoothAdapter.java` contains exactly one
`throw new SecurityException` (l.3648, the legacy `getProfileProxy` path). `enforceCallingOrSelfPermission`
in `AdapterService.java` throws for `BLUETOOTH_PRIVILEGED` / `LOCAL_MAC_ADDRESS` gates (l.2351, 2379,
2412, 2478, 2514, 2573, 2588, 2684, 2764, 2802) — all on paths a third-party app cannot reach anyway.
**[INFER]** So the rule for Phase 3 is inverted from usual Android practice: **do not write
`try { … } catch (SecurityException)` and consider the case handled.** The dominant failure mode is a
silent empty/disconnected value. Catching `SecurityException` is necessary (legacy band) and
nowhere near sufficient.

**6.3 Manifest names, classes, and bands.**

| Permission | Manifest name | Protection level (read this session) | Runtime? | Exists on device API | Gates, in Phase 3's scope |
|---|---|---|---|---|---|
| `BLUETOOTH` | `android.permission.BLUETOOTH` | `normal` **[AOSP-35]** | install-time | 1+ | t≤30 form of every row above **[SDK]** |
| `BLUETOOTH_ADMIN` | `android.permission.BLUETOOTH_ADMIN` | `normal` **[AOSP-35]** | install-time | 1+ | `ACTION_PAIRING_REQUEST`, `ACTION_UUID`, `fetchUuidsWithSdp` (t≤30 sentences) **[SDK]** — not needed for observation |
| `BLUETOOTH_CONNECT` | `android.permission.BLUETOOTH_CONNECT` | `dangerous` **[AOSP-35]** | **runtime** | **31+** | everything in §2, §3, §4.1 (t≥31) and §5 — i.e. the whole of Phase 3 |
| `BLUETOOTH_SCAN` | `android.permission.BLUETOOTH_SCAN` | `dangerous` **[AOSP-35]** | runtime | 31+ | only `ACTION_FOUND` / discovery actions **[SDK]** — Phase 3 must **not** declare it |
| `BLUETOOTH_PRIVILEGED` | `android.permission.BLUETOOTH_PRIVILEGED` | `signature\|privileged` **[AOSP-35]** | unreachable | 1+ | `getMetadata`, `setMetadata`, `getSupportedProfiles`, `getMostRecentlyConnectedDevices`, `registerBluetoothConnectionCallback`, `getIdentityAddress`, `BluetoothA2dp.getSupportedCodecTypes` **[AOSP-35]**/**[SDK]** |
| `ACCESS_FINE_LOCATION` | `android.permission.ACCESS_FINE_LOCATION` | `dangerous\|instant` **[AOSP-35]** | runtime | 1+ | discovery/scan only — **no** observation member references it (§5.3) |

**[DOC]** The runtime character and the prompt group, verbatim from the Bluetooth permissions guide:
"The `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`, and `BLUETOOTH_SCAN` permissions are **runtime
permissions**. Therefore, you must explicitly request user approval in your app before you can look
for Bluetooth devices, make a device discoverable to other devices, or **communicate with
already-paired Bluetooth devices**. When your app requests at least one of these permissions, the
system prompts the user to allow your app to access **Nearby devices**." and, for the manifest wording,
"If your app communicates with already-paired Bluetooth devices, declare the `BLUETOOTH_CONNECT`
permission." **[DOC]** Note the vocabulary: the platform's own gate is "communicate with already-paired
devices" — **[INFER]** Phase 3 never communicates with a device, but it *reads state about paired
devices*, and the per-member requirement sentences in §6.1 are unambiguous that these read-only calls
are gated anyway. That asymmetry (the guide's framing suggests "no communication ⇒ no permission",
the member docs say otherwise) is worth flagging in the phase's security section: **follow the member
docs**.

**6.4 Bands below API 31.** **[SDK]** The stubs carry a `BLUETOOTH` (install-time) sentence for t≤30 on
every observation member, so on device API 26–30 the requirement is legacy `BLUETOOTH` and there is
**no runtime permission and no revocable grant** — the state cannot be "denied" mid-session the way it
can on 31+. Phase 2's U-1 (does a t≥31 app that omits `BLUETOOTH` still succeed on device API 26–30?)
becomes **load-bearing for Phase 3**, because Phase 3 is the first phase whose feature actually needs
these calls; it stays U-1 here too, and it is the single most important device-session item.

### Answer to the explicit safety question

**Can a denied permission produce an empty result indistinguishable from "no devices connected"?**
**Yes, on every path, by design.** **[AOSP-35]** — `Collections.emptyList()` for `getBondedDevices`
and the GATT enumeration, `BluetoothProfile.STATE_DISCONNECTED` for the two state getters, silent
non-delivery for all broadcasts. **[SDK]** — and the docs say the same thing in prose: "The list will
be empty on error", "or null on error", "or `DEVICE_TYPE_UNKNOWN` if it's not available".
**[INFER]** Therefore the observer's *only* lawful shape is: **permission state and adapter state are
inputs to every observation, and any read performed while `BLUETOOTH_CONNECT` is not granted, or
`getState() != STATE_ON`, must be reported as `NOT_OBSERVABLE(permission-denied)` or
`NOT_OBSERVABLE(adapter-off)` and never as an empty device list.** The same value also arises from
`service == null` (Bluetooth process down) and from `!callerIsSystemOrActiveOrManagedUser` (secondary
user / headless), so the engine should carry a distinct `NOT_OBSERVABLE(platform-refused)` even when
the permission *is* granted and the result is still empty — Phase 2's D-8 ("non-permission failures
must not be relabelled `PermissionDenied`") generalises to "emptiness must never be relabelled as a
finding about devices".

---

## 7. Q6 — failure semantics and the real cost of profile-proxy inspection

### Answer

Reading per-device, per-profile state **requires** a profile proxy, obtaining one is **asynchronous
service binding** with an explicitly documented `false` failure and no documented error code, and the
proxy's availability is a *capability of the running Bluetooth app* rather than of the API level —
which is the documented seam an OEM differs on. Profile constants do **not** imply BLE support, and
the API surface contains no public per-device "is connected at all" call.

### Evidence

**7.1 `getProfileProxy` is async binding, not a getter.** **[SDK]** the public doc: "Profile can be one
of `HEADSET`, `A2DP`, `GATT`, `HEARING_AID`, or `GATT_SERVER`. **Clients must implement
`BluetoothProfile.ServiceListener` to get notified of the connection status and to get the proxy
object.** @return true on success, false on error"; `ServiceListener` is `onServiceConnected(int
profile, BluetoothProfile proxy)` / `onServiceDisconnected(int profile)`; teardown is
`closeProfileProxy(int, BluetoothProfile)`, whose own doc says "Profile can be one of
`BluetoothProfile#HEADSET` or `BluetoothProfile#A2DP`" — i.e. the *close* documentation is narrower
than the *get* documentation. **[AOSP-35]** `BluetoothAdapter.java` l.3619-3660: the `true` return
only means "a `ProfileConnection` was queued for registration"; `false` is returned for a null
context/listener, for `HEALTH`, for unsupported `HEARING_AID`, and for any profile absent from
`PROFILE_CONSTRUCTORS` (l.818). **[INFER]** Cost, per profile: one bind, one listener object, one
`ServiceListener` round-trip, one `closeProfileProxy`, plus a `DISCONNECTED` event you must treat as
"state unknown for that profile", not "no devices". Phase 3's budget should be
`O(|profile list|)` binds for the observer's lifetime — A2DP, HEADSET, LE_AUDIO, HEARING_AID,
CSIP_SET_COORDINATOR, and GATT via `BluetoothManager` (not a bind at all) — and this is a real
lifecycle design question, not a free call.

**7.2 There is no error surface for it.** **[SDK]** `BluetoothStatusCodes` (API 31) is the modern
status vocabulary, but `getProfileProxy` predates it and returns a bare `boolean`. Phase 2 §4 item 6
and §5 record which `BluetoothStatusCodes` members exist (`ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION`,
`ERROR_BLUETOOTH_NOT_ENABLED`, `ERROR_BLUETOOTH_NOT_ALLOWED`, `ERROR_DEVICE_NOT_BONDED`,
`ERROR_PROFILE_SERVICE_NOT_BOUND`, `FEATURE_NOT_SUPPORTED`, …). **[INFER]**
`ERROR_PROFILE_SERVICE_NOT_BOUND` is the code that *would* describe "your proxy is gone", and it is
**not returned by any observation call in this surface** — it belongs to GATT/RFCOMM mutators. A
Phase 3 model that surfaces "profile service not bound" as a platform-returned code would be
fabricating an API contract; it may only be an internal state we derive.

**7.3 Profile constants do not imply BLE, and `getType()` is not a profile statement.**
**[SDK]** `BluetoothProfile` has no LE/classic dimension at all: `A2DP`/`HEADSET` are BR/EDR,
`LE_AUDIO`/`HEARING_AID`/`CSIP_SET_COORDINATOR`/`HAP_CLIENT` are LE-based, `GATT` is the LE link —
and nothing on the constant says so. `BluetoothDevice.getType()` returns transport capability
(`DEVICE_TYPE_CLASSIC/LE/DUAL`), which is a device property, not a profile statement; a `DUAL` device
can be connected over either. The only public per-transport connection signal is
`BluetoothDevice.EXTRA_TRANSPORT` on `ACTION_ACL_CONNECTED`/`_DISCONNECTED` — "Used as an int extra
field in `ACTION_ACL_CONNECTED` and `ACTION_ACL_DISCONNECTED` intents to indicate which transport is
connected. Possible values are: `TRANSPORT_BREDR` and `TRANSPORT_LE`" **[SDK]** — **and that constant
is `since="33"`** while the actions are `since="5"` **[SDK]**. **[INFER]** So on API 26–32 the extra
is present at runtime in the intent but not in the documented public surface we compile against; a
transport-aware observer must be gated `SDK_INT >= 33` and degrade to "transport unknown" below it.
`FEATURE_BLUETOOTH_LE` (`PackageManager.FEATURE_BLUETOOTH_LE`, per Phase 2 §4 item 12) is a
*hardware* question and is the right place to ask it.

**7.4 What an OEM is documented to be able to differ on.** Each of these is a documented degree of
freedom, not a guess:
- **Number of simultaneous audio devices**: `getMaxConnectedAudioDevices()` — "Get the maximum number
  of connected devices per audio profile for this device … **or -1 if the Bluetooth service can't be
  reached**" **[SDK]**. **[AOSP-35]** the default comes from an overlay resource *and* a system
  property: `AdapterProperties.java` l.226-240 reads
  `com.android.bluetooth.R.integer.config_bluetooth_max_connected_audio_devices`, then
  `SystemProperties.getInt(MAX_CONNECTED_AUDIO_DEVICES_PROPERTY, …)` may override it, then a
  `Math.min/Math.max` clamp fixes the range; the field defaults to 1 at l.110.
  **[INFER]** Multipoint earbuds therefore produce device-count>1 states on some handsets and 1 on
  others, and the aggregate-collapse of §2c behaves differently.
- **Whether a profile exists at all**: Javadoc "Android only supports **one** connected Bluetooth A2dp
  device at a time" (`BluetoothA2dp`), "Android only supports one set of connected Bluetooth LeAudio
  device at a time" (`BluetoothLeAudio`), "Android only supports one set of connected Bluetooth
  Hearing Aid device at a time" (`BluetoothHearingAid`) **[SDK]**; and `getProfileProxy` returns
  `false` for `HEARING_AID` when `!isHearingAidProfileSupported()`, i.e. capability, not API level,
  decides **[AOSP-35]**.
- **Binaural pair representation**: `BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED` and
  `BluetoothHearingAid.ACTION_CONNECTION_STATE_CHANGED` both say verbatim "**in the binaural case,
  there will be two different LE devices for the left and right side and each device will have their
  own connection state changes**" **[SDK]**. So the same physical product appears as two devices on
  LE Audio and as one on A2DP. Grouping is only documented via `getGroupId` (API 33, LE Audio / CSIS)
  and `BluetoothDevice.EXTRA_IS_COORDINATED_SET_MEMBER` (API 33, **"Can contain … if they are
  available"**, and only on `ACTION_FOUND` — the discovery broadcast **[SDK]**). **[INFER]**
  A2DP-only earbuds have **no public grouping API**; any left/right correlation for classic earbuds is
  our own inference and must be labelled as such.
- **Vendor AT-command surface**: `BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT` and
  `sendVendorSpecificResultCode(device, command, arg)` — "Currently only
  `VENDOR_RESULT_CODE_COMMAND_ANDROID` is allowed as `command`" **[SDK]**. **[INFER]** The widely
  desired "ask the earbud for battery/ANC state over a vendor AT command" path is a *write* onto a
  live HFP connection, which Phase 3 forbids (§8).
- **Whether the reference implementation's timings/edges hold at all**: **[DOC]** the docs speak for
  AOSP; Phase 2 §7.3 rule 5 already commits us to treating a specific handset as a separate evidence
  item. `docs/security/device-access-policy.md` records that nothing in this project has been
  hardware-verified.

### Consequence for Phase 3

Profile-proxy inspection is a **bounded, explicit, asynchronous resource**: a per-profile state
machine (`UNBOUND / BINDING / BOUND / LOST`), one bind per profile for the observer's lifetime, no
retry loop, and `LOST` mapped to "this profile contributes no evidence" rather than "no devices".
Transport awareness needs an `SDK_INT >= 33` gate. Classic-earbud left/right pairing must be either
dropped from the requirements or labelled as our inference with a confidence field.

---

## 8. Q7 — what Phase 3 must NOT be able to do (and the named calls that could)

### Answer

Good news, with three named exceptions. Nothing in the §2–§5 observation set initiates a connection,
changes a codec, alters audio routing or pairs anything — and I can point at the *specific*
look-alike calls that do, so they can be excluded by name in the phase's guard tests.

### 8.1 Read-only, confirmed by their own documentation

**[SDK]** Pure data reads, no documented air or state effect: `BluetoothDevice.getAddress()`,
`getType()`, `getBondState()`, `getBluetoothClass()`, `getName()` (explicitly "just returns the name …
from the cache"), `getAlias()`, `getUuids()` (explicitly "**does not start a service discovery
procedure**"), `BluetoothClass.getMajorDeviceClass()/getDeviceClass()/hasService()/doesClassMatch()`,
`BluetoothAdapter.getState()/isEnabled()/getBondedDevices()/getProfileConnectionState()`,
`BluetoothManager.getConnectedDevices()/getDevicesMatchingConnectionStates()/getConnectionState()`,
`BluetoothProfile.getConnectionState()/getConnectedDevices()/getDevicesMatchingConnectionStates()`,
`BluetoothA2dp.isA2dpPlaying()`, `BluetoothHeadset.isNoiseReductionSupported()/isVoiceRecognitionSupported()/isAudioConnected()`,
`BluetoothLeAudio.getGroupId()/getConnectedGroupLeadDevice()`, `getMaxConnectedAudioDevices()`,
and every §4.1 broadcast reception. **[AOSP-35]** `getAddress()` is literally `return mAddress;`.
**[INFER]** `getProfileProxy` binds to the local Bluetooth service; it does not touch the radio or the
remote device — the doc's verb set is "get the profile proxy object associated with the profile", and
`ServiceListener` reports the *IPC* connection, not a Bluetooth link. **[UNVERIFIED]** as a runtime
claim: U-6.

### 8.2 Named calls that DO mutate — avoid by name

1. **`BluetoothDevice.fetchUuidsWithSdp()`** (and its `@Transport` overload) — the doc I read says
   "**Perform a service discovery on the remote device** to get the UUIDs supported with the specific
   transport. This API is asynchronous and `ACTION_UUID` intent is sent…" **[SDK]**, and it is gated
   `BLUETOOTH_ADMIN` (t≤30) / `BLUETOOTH_CONNECT` (t≥31) **[SDK]**. **[INFER]** **This is the trap.**
   It is an over-the-air SDP transaction against a named device — activity indistinguishable from
   probing a paired device — and its public Javadoc does not say "this talks to the device" in words
   an implementer would refuse on. Phase 3 must use `getUuids()` (cache) or nothing.
2. **`BluetoothHeadset.startVoiceRecognition(device)` / `stopVoiceRecognition(device)`** —
   `stopVoiceRecognition` is documented as "Stop Bluetooth Voice Recognition mode, and **shut down the
   Bluetooth audio path**" and `startVoiceRecognition` requires `BLUETOOTH_CONNECT` **and**
   `MODIFY_PHONE_STATE` (`signature|privileged|role`, read this session in `core/res/AndroidManifest.xml`
   **[AOSP-35]**). **[INFER]** An *audio-path* mutation whose name suggests a query.
3. **`BluetoothHeadset.sendVendorSpecificResultCode(device, command, arg)`** — sends a vendor AT
   command down a live HFP connection **[SDK]**; it is the seed of every "just ask the earbud" design.
4. **`BluetoothDevice.setAlias(String)`** — "This method **overwrites** the previously stored alias.
   The new alias is saved in local storage so that the change is **preserved over power cycles**", and
   it additionally "requires the calling app to be associated with Companion Device Manager", with a
   `BLUETOOTH_PRIVILEGED` bypass path **[SDK]**. Renaming a user's device is a user-visible mutation.
   Read-only Phase 3 uses `getAlias()` only.
5. **`BluetoothDevice.createBond()` / `createBond(int transport, OobData)` /
   `setPairingConfirmation(boolean)` / `setPin(byte[])`** — pairing; `setPairingConfirmation` mentions
   `BLUETOOTH_PRIVILEGED` in its stub text **[SDK]**. Excluded by the phase's own rules.
6. **`BluetoothAdapter.enable()` / `disable()`** — deprecated at API 33 and "For applications targeting
   `TIRAMISU` or above, this API will always fail and return false" (Phase 2 R-4, **[DOC]**/**[SDK]**).
   Already unusable.
7. **`connectGatt(...)`, `createRfcommSocketToServiceRecord(...)`, `createL2capChannel(int)`** —
   connection-initiating; `BluetoothDevice`'s own class doc says "A `BluetoothDevice` lets you **create
   a connection** with the respective device or query information about it" **[SDK]**; excluded.
8. **Codec / audio-routing surfaces** — `BluetoothA2dp.getSupportedCodecTypes()` is
   `@RequiresPermission(BLUETOOTH_PRIVILEGED)` and therefore unreachable **[SDK]**;
   `BluetoothCodecConfig`/`BluetoothCodecStatus`/`BluetoothLeAudioCodecConfig` (API 33) exist but
   belong to audio control, not observation; and **[AOSP-35]** `A2dpService.java` l.1163-1181 shows the
   hidden `BluetoothA2dp.ACTION_ACTIVE_DEVICE_CHANGED` (audio routing) and
   `ACTION_CODEC_CONFIG_CHANGED` broadcasts. **[INFER]** Phase 3 should explicitly **not** subscribe to
   `ACTION_ACTIVE_DEVICE_CHANGED` / codec-change actions even though they are tempting, because that
   crosses the "does not alter or model the audio path" boundary. They also are not on the
   `broadcast_actions.txt` system list for the codec action I checked (`grep bluetooth
   broadcast_actions.txt` returned 25 lines and did not include `a2dp.profile.action.ACTIVE_DEVICE` or
   `CODEC_CONFIG`), which is consistent with them being non-public.
9. **Android 16 / CompanionDeviceManager** — **[DOC]** the Android 16 targeting page's Bluetooth
   section states that apps targeting Android 16 "can now disconnect Bluetooth devices using a public
   API in `CompanionDeviceManager`", naming `removeBond(int)`. **[INFER]** That is an
   unpair/disconnect capability introduced above our `compileSdk`; it is not callable at 35, and any
   future CDM-based phase must decide separately whether disconnecting is in scope.

### Consequence for Phase 3

A **negative allow-list** is now available and should be encoded as a test: the observer may reference
`getBondedDevices`, `getProfileConnectionState`, `getProfileProxy`/`closeProfileProxy`, the
`BluetoothProfile` triad, the `BluetoothManager` triad, the §5 member set, and the §4.1 actions — and
must fail a guard test if it references `fetchUuidsWithSdp`, `createBond`, `setPin`,
`setPairingConfirmation`, `setAlias`, `connectGatt`, `create*Socket`/`createL2capChannel`,
`start/stopVoiceRecognition`, `sendVendorSpecificResultCode`, `enable`/`disable`,
`ACTION_ACTIVE_DEVICE_CHANGED`, `removeBond`, or any `BLUETOOTH_PRIVILEGED`-gated member.
Two boundary calls need an explicit ruling from the phase owner: `getProfileProxy` (binds an IPC
service — permitted by the reading of its doc, but see U-6) and `ACTION_ALIAS_CHANGED` /
`ACTION_NAME_CHANGED` (read-only *observations* whose underlying `setAlias` is a mutation we never call
— I recommend subscribing, since they are the only public signal that a device's displayed identity
changed).

---

## 9. Compatibility matrix — observation mechanisms by API band

Legend: **A** = available; **A\*** = available but with a documented band-specific caveat named below;
**U** = unavailable (name absent from the public API at that band); **D** = deliverable to a receiver
only if `BLUETOOTH_CONNECT` is granted (API 31+ semantics); **?** = evidence insufficient at that band
→ see §11. Every row's source is the compileSdk 35 platform data (`since` attributes) unless marked.

| Mechanism (row cites its `since` evidence) | 26 | 29 | 31 | 33 | 35 |
|---|---|---|---|---|---|
| `BluetoothAdapter.getBondedDevices()` — [SDK] eff. 5 | A | A | A (needs `BLUETOOTH_CONNECT`) | A | A |
| `BluetoothAdapter.getProfileConnectionState(int)` — [SDK] since 14 | A | A | A (requires **t≥34** for enforcement — [DOC] C-08, [AOSP-35] `ENFORCE_CONNECT`) | A | A |
| `BluetoothAdapter.getProfileProxy` + `BluetoothProfile` triad — [SDK] 11 | A | A | A (`SecurityException` only for t<31 legacy — [AOSP-35] l.3648) | A | A |
| `BluetoothManager.getConnectedDevices/getDevicesMatchingConnectionStates/getConnectionState` — [SDK] 18 | A | A | A (**GATT+GATT_SERVER only**; other values throw `IllegalArgumentException` — [AOSP-35]) | A | A |
| `BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED` + extras — [SDK] 11 | A | A | A/D | A | A |
| `BluetoothDevice.ACTION_ACL_CONNECTED` / `_DISCONNECTED` — [SDK] 5 | A | A | A/D | A (`EXTRA_TRANSPORT` becomes public — [SDK] 33) | A |
| `BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED` — [SDK] 5, [S-04] l.73 | A | A | A/D | A | A |
| `…ACTION_BOND_STATE_CHANGED` + extras — [SDK] 5 | A | A | A/D | A | A |
| `…ACTION_NAME_CHANGED` / `ACTION_CLASS_CHANGED` — [SDK] 5 | A | A | A/D | A | A |
| `…ACTION_ALIAS_CHANGED` + `getAlias()` — [SDK] 30 | U | U | A | A | A |
| `BluetoothDevice.getType()` / `DEVICE_TYPE_*` — [SDK] 18 | A | A | A/D | A | A |
| `BluetoothDevice.getBluetoothClass()` + `getDeviceClass/getMajorDeviceClass/hasService` — [SDK] 5 | A | A | A/D | A | A |
| `BluetoothClass.doesClassMatch()` + `PROFILE_*` + `Service.LE_AUDIO` — [SDK] 33 | U | U | U | A | A |
| `BluetoothDevice.getUuids()` (cache read) — [SDK] 15 | A | A | A/D | A | A |
| `BluetoothDevice.getAddress()` — [SDK] 5, no permission sentence | A | A | A (still no permission documented) | A | A |
| `BluetoothDevice.getAddressType()` + `ADDRESS_TYPE_*` — [SDK] 35 (UNKNOWN added 33) | U | U | U | U | A |
| `BluetoothHearingAid` proxy + connection-state action — [SDK] 29 | U | A | A | A | A |
| `BluetoothLeAudio` class — [SDK] 31 | U | U | A\* (**profile constant `LE_AUDIO` is 33** — [SDK]; class exists but nothing to ask it about until 33; Phase 2 U-7) | A | A |
| `BluetoothLeAudio.getGroupId/getConnectedGroupLeadDevice/GROUP_ID_INVALID`, `ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED` — [SDK] 33 / 31 | U | U | action only | A | A |
| `BluetoothCsipSetCoordinator` + `ACTION_CSIS_CONNECTION_STATE_CHANGED` + `EXTRA_IS_COORDINATED_SET_MEMBER` — [SDK] 33 | U | U | U | A | A |
| `BluetoothAdapter.getMaxConnectedAudioDevices()` — [SDK] 33 | U | U | U | A | A |
| `BluetoothAdapter.getRemoteLeDevice(String,int)` — [SDK] 33 | U | U | U | A | A |
| Manifest-declared receiver for ACL / A2DP / HEADSET connection actions — [DOC] broadcast-exceptions | A | A | A | A | A |
| Manifest-declared receiver for adapter-level or bond-state actions — [DOC] (absent from exemption list) | U | U | U | U | U |
| `RECEIVER_EXPORTED` flag exists — [DOC] Phase 2 R-1 + behavior-changes-14 | U | U | U (constants are 33) | A | A (required at t≥34, and required *exported* for Bluetooth — [DOC] broadcasts guide) |
| `BluetoothAdapter.registerBluetoothConnectionCallback` — [AOSP-35] `@SystemApi` | U | U | U | U | **U for third-party apps at every band** |
| `BluetoothDevice.getMetadata/setMetadata` — [AOSP-35] `@SystemApi` + `BLUETOOTH_PRIVILEGED` | U | U | U | U | U |
| `BluetoothAdapter.getSupportedProfiles`, `getMostRecentlyConnectedDevices` — [AOSP-35] `@SystemApi` + `BLUETOOTH_PRIVILEGED` | U | U | U | U | U |
| `BluetoothDevice.isConnected()` — [AOSP-35] l.2188 `@SystemApi @hide` | U | U | U | U | U |

Band caveats that are **not** device-level and are settled by the API table: `getAlias`/`ACTION_ALIAS_CHANGED`
need 30; `HEARING_AID` needs 29; anything LE-Audio/CSIP-shaped needs 33; `EXTRA_TRANSPORT` needs 33;
`getAddressType` needs 35. **[SDK]**

---

## 10. Permission matrix — observation mechanisms

Requirement sentences are the shipped stub text **[SDK]**; enforcement outcomes are **[AOSP-35]**
unless marked **[INFER]**. "Location?" is the answer to "does this operation ever mention location".

| Mechanism | t ≤ 30 (device 26–30) | t ≥ 31 (device 31+) | Location? | Outcome on denial (t≥31) |
|---|---|---|---|---|
| `getBondedDevices()` | `BLUETOOTH` (install) | `BLUETOOTH_CONNECT` (runtime) | No | **empty set** (`null` on other errors) |
| `getProfileConnectionState(p)` | `BLUETOOTH` | `BLUETOOTH_CONNECT`, enforced from **t≥34** [DOC] | No | **`STATE_DISCONNECTED`** (also `service==null`, adapter-off, non-active-user) |
| `getProfileProxy()` | `BLUETOOTH` — else `SecurityException` [AOSP-35] | no permission on this member in the stub; binding is not itself gated [AOSP-35] | No | `false` return, no listener callback; legacy band throws |
| `BluetoothProfile.getConnectedDevices()` etc. | `BLUETOOTH` | `BLUETOOTH_CONNECT` | No | **empty list**, "empty on error" [SDK] |
| `BluetoothManager.getConnectedDevices(GATT)` / `getDevicesMatchingConnectionStates(GATT, ·)` | `BLUETOOTH` | `BLUETOOTH_CONNECT` | No | **empty list**; `getConnectionState` then reports `STATE_DISCONNECTED` |
| `BluetoothManager.*(A2DP \| HEADSET \| …)` | — | — | No | **`IllegalArgumentException` — throws regardless of permission** [AOSP-35] |
| `ACTION_CONNECTION_STATE_CHANGED` (adapter) | `BLUETOOTH` | `BLUETOOTH_CONNECT` (receiver permission) | No | silent non-delivery |
| `ACTION_ACL_CONNECTED` / `_DISCONNECTED` / `_DISCONNECT_REQUESTED` | `BLUETOOTH` | `BLUETOOTH_CONNECT` | No | silent non-delivery |
| `ACTION_BOND_STATE_CHANGED` | `BLUETOOTH` | `BLUETOOTH_CONNECT` | No | silent non-delivery |
| `ACTION_NAME_CHANGED` / `ACTION_CLASS_CHANGED` / `ACTION_ALIAS_CHANGED` | `BLUETOOTH` | `BLUETOOTH_CONNECT` | No | silent non-delivery |
| `Bluetooth{A2dp,Headset,LeAudio,HearingAid,CsipSetCoordinator}.ACTION_*_STATE_CHANGED` | `BLUETOOTH` | `BLUETOOTH_CONNECT` | No | silent non-delivery |
| `getName/getAlias/getType/getBluetoothClass/getBondState/getUuids` | `BLUETOOTH` | `BLUETOOTH_CONNECT` | No | `null` / `DEVICE_TYPE_UNKNOWN` |
| `getAddress()` | none documented | none documented | No | none — pure field read [AOSP-35] |
| `getMetadata/setMetadata` | — | `BLUETOOTH_CONNECT` **+** `BLUETOOTH_PRIVILEGED` | No | unreachable: `SecurityException` from `enforceCallingOrSelfPermission` [AOSP-35] |
| `getSupportedProfiles/getMostRecentlyConnectedDevices/registerBluetoothConnectionCallback/getIdentityAddress` | — | `BLUETOOTH_CONNECT` **+** `BLUETOOTH_PRIVILEGED` | No | unreachable |
| `BluetoothA2dp.getSupportedCodecTypes()` | `BLUETOOTH` | `BLUETOOTH_PRIVILEGED` [SDK] | No | unreachable |
| `ACTION_FOUND` (**out of scope**, listed to show the contrast) | `BLUETOOTH` + `ACCESS_FINE_LOCATION` | `BLUETOOTH_SCAN` + `ACCESS_FINE_LOCATION` unless `neverForLocation` | **Yes** [SDK] | silent non-delivery |

**[INFER] Recommended declaration for the phase that ships this engine** (not a recommendation Phase 3
must accept, and no manifest change is made by this document): `BLUETOOTH_CONNECT` (runtime) is the
only new declaration with a Phase 3 call behind it; `BLUETOOTH` with `android:maxSdkVersion="30"` per
Phase 2 §3; **no** `BLUETOOTH_SCAN`, therefore **no** `ACCESS_FINE_LOCATION`, therefore
`neverForLocation` is moot (§5.3). `BLUETOOTH_CONNECT` puts the app in the **Nearby devices** prompt
group **[DOC]**, so the one-sentence justification `SEC-PERM-004` demands has to be about *reading
which paired devices are currently connected* — and must be honest that the app cannot tell the user
why a denial looks identical to "nothing connected" (§6.4).

---

## 11. UNVERIFIED — the list a device session must script against

Nothing here can be closed by more reading. Ordered by how much of the design each one can break.

| ID | Item | What I read, and why it is not enough | Required observation |
|---|---|---|---|
| **U-1** | Does a `targetSdk ≥ 31` app with **no** legacy `BLUETOOTH` declaration successfully call `getBondedDevices()`, `getProfileConnectionState()` and the `BluetoothManager` trio on a **device** running API 26–30? | Stubs express the rule only as "for apps targeting R or lower" **[SDK]**; Phase 2 U-1 reached the same wall. Whether the API-26–30 framework enforces `BLUETOOTH` against a t=35 app is a property of that build. | Run all four calls on a ≤30 handset with and without `BLUETOOTH maxSdkVersion="30"`. |
| **U-2** | Is `BluetoothDevice.getAddress()` for a **bonded** earbud stable across time, reboots and firmware updates, and is it the identity address (not a rotating/resolvable random one)? | The public doc is one sentence and says nothing about privacy or stability **[SDK]**; **[AOSP-35]** proves the platform distinguishes "identity address" from "pseudo address" for LE-only devices but keeps `getIdentityAddress` privileged. | Snapshot addresses over ≥ 7 days with daily reconnects; compare against a second handset and against the vendor app's display. |
| **U-3** | What does `getProfileProxy(ctx, listener, BluetoothProfile.GATT \| GATT_SERVER)` actually return on an API-35 device? | **[SDK]** the public Javadoc says GATT is a valid profile; **[AOSP-35]** `PROFILE_CONSTRUCTORS` has no GATT entry and the code path logs "Unknown profile" and returns `false`. Source disagreement I can only resolve on a handset. | Bind/unbind each of HEADSET, A2DP, LE_AUDIO, HEARING_AID, CSIP_SET_COORDINATOR, GATT, GATT_SERVER, SAP, HAP_CLIENT, HEALTH and record return value + `onServiceConnected` outcome. |
| **U-4** | Do the §4.1 broadcasts actually **arrive** at a `RECEIVER_EXPORTED` context receiver on real API-31/33/34/35 devices, and does a flag-free `registerReceiver(receiver, filter)` really deliver them at `targetSdk 34+` (versus `RECEIVER_NOT_EXPORTED`, which the docs say will not)? | Two official pages give opposite-looking guidance (**[DOC]** §4.3); sender code is **[AOSP-35]**, one implementation. | Connect/disconnect a real earbud and log per-action receipt under all three registration shapes. |
| **U-5** | Does `BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED` fire at all on the test handset, and does it stay silent on the second earbud as **[AOSP-35]**'s counters say? | Counter logic is reference code, not a contract; the Javadoc describes the edge but no OEM obligation. | Multipoint pair: connect L only, then R only; log every adapter-level event with extras. |
| **U-6** | Does `getProfileProxy` have any observable radio, battery or audio side effect on a real handset (a momentary reconnect, an active-device switch, a codec re-negotiation)? | Its documentation says only "get the profile proxy object" **[SDK]**; I cannot prove absence of side effects from a stub, and the phase's safety boundary depends on it. | Watch the earbud itself (vendor app state, audio path, `dumpsys bluetooth_manager`) around bind/unbind while nothing else runs. |
| **U-7** | Which profile states are populated on the test OEM build for `getProfileConnectionState(CSIP_SET_COORDINATOR \| HAP_CLIENT \| SAP)`? | **[AOSP-35]** `AdapterProperties` only aggregates the 14 profiles whose `ACTION_CONNECTION_STATE_CHANGED` it registers (l.258-271) — CSIP and HAP_CLIENT are absent from that filter, so the map has no entry and the call returns `STATE_DISCONNECTED` forever; but `AdapterProperties` is one implementation and OEMs fork it. | Read all public profile constants on a shipping handset with each profile active. |
| **U-8** | Does `BluetoothLeAudio.getGroupId()` return a usable, non-`GROUP_ID_INVALID` group id for the target earbuds, and is it stable across reconnects? | Documented as a group id **[SDK]** with no stability or availability guarantee; availability depends on the device being LE Audio, which many TWS products are not. | LE-Audio-capable handset + LE-Audio earbuds; log group ids and lead devices through connect/disconnect/one-side-out-of-case. |
| **U-9** | What is the actual latency and ordering of ACL events versus profile events versus `ACTION_CONNECTION_STATE_CHANGED`, and are profile events ever **coalesced or dropped** under load? | **[AOSP-35]** `Utils.getTempBroadcastOptions()` is passed to every `sendBroadcast`; I did not resolve what those options contain for delivery latency, and background-broadcast policy is a device-level matter. | Timestamp each action type across 20 connect/disconnect cycles; compute reorder and gap statistics. |
| **U-10** | Is `BluetoothDevice.EXTRA_TRANSPORT` populated on API 26–32 devices where the constant is not public? | **[SDK]** constant is `since="33"`; **[AOSP-35]** the sender always puts it. A ≤32 build could omit or rename the key. | On a 26–30 handset, dump the intent extras bundle of an ACL event. |
| **U-11** | Does a **secondary / clipped / headless-user** profile get the broadcasts at all (`sendBroadcastAsUser(UserHandle.ALL…)` says yes), and do the binder calls succeed (`callerIsSystemOrActiveOrManagedUser` says no for some)? | **[AOSP-35]** one reference implementation's user gate; not documented anywhere public. | Run the observer as a secondary user / work profile and record the divergence. |
| **U-12** | Behaviour of every observation call while `getState()` is `STATE_TURNING_ON` / `STATE_TURNING_OFF`, and in the BLE-only (hidden `STATE_BLE_ON`) window. | **[SDK]** documents only `STATE_ON` (empty set for `getBondedDevices`); **[AOSP-35]** shows a separate hidden ACL action for `STATE_BLE_ON` and `STATE_DISCONNECTED` for turning states. | Toggle Bluetooth via the quick-settings tile while continuously registering and reading; log the full matrix. |

Twelve items. U-1, U-3, U-4 and U-6 are the ones that can invalidate a design; U-4 is the only one that
can invalidate *event-driven observation as a whole*, which makes it the first thing a device session
should attempt.

---

## 12. Temptations this research closes

Each of these looks like the obvious design and is ruled out by a citation above.

1. **"Call `BluetoothManager.getConnectedDevices(BluetoothProfile.A2DP)` — it's literally the API for connected devices."**
   It throws `IllegalArgumentException`; GATT/GATT_SERVER only, and it reaches the GATT service, not
   audio. **[AOSP-35]** §2e/§6.1.
2. **"A single call gives me the connected set."** No such public call exists; `getSupportedProfiles()`
   (the "which profiles?" call) is `@SystemApi`+privileged. **[AOSP-35]** §3.4.
3. **"Empty list ⇒ nothing is connected."** On every enumeration path, permission-denied, adapter-off,
   service-null and non-active-user all return the same empty/`STATE_DISCONNECTED` value.
   **[AOSP-35]** §6.1/§6.4. This is Phase 2 R-5 generalised, and it is the phase's central hazard.
4. **"Listen for `ACTION_CONNECTION_STATE_CHANGED` and I'll see every device come and go."** It is a
   global 0↔1 edge trigger; the second earbud produces nothing. **[SDK]**/**[AOSP-35]** §2b.
5. **"Mark the receiver `RECEIVER_NOT_EXPORTED` because these are system broadcasts."** Bluetooth
   broadcasts come from the Bluetooth app UID, not system; `NOT_EXPORTED` is documented to lose them.
   **[DOC]** §4.3.
6. **"A manifest receiver gets me events without a live observer."** Only four Bluetooth actions are on
   the implicit-broadcast exemption list; adapter-level and bond-state actions are not. **[DOC]** §4.4.
7. **"Poll every two seconds to catch transitions."** Unnecessary (events exist, §4) and it defeats the
   IPC-cache design the platform actually uses **[AOSP-35]** §4.5; polling is forbidden by the phase.
8. **"Use `getMetadata()` for vendor keys / `isConnected()` for a device-level answer / the new
   `BluetoothConnectionCallback` for clean events."** All three are `@SystemApi`/`@hide`, two of them
   additionally need `BLUETOOTH_PRIVILEGED`, and none is in `android.jar`. **[SDK]** + **[AOSP-35]**
   §4.2/§5.
9. **"CoD says `AUDIO_VIDEO_HEADPHONES`, so it's an earbud."** The platform documents class as a hint
   that "does not reliably describe … profiles or services", and its own classifier "errs on the side
   of false positives". **[SDK]** §5.2.
10. **"The address is a hardware MAC, so it's a global stable ID."** Public docs say nothing about
    privacy; reference code distinguishes identity from pseudo addresses and keeps the resolver
    privileged. Treat as U-2, not as a given. **[SDK]**/**[AOSP-35]** §5.4.
11. **"`getProfileConnectionState(A2DP)` tells me which A2DP device is up."** It is a per-profile
    aggregate with a documented lossy multi-device collapse and a Javadoc that lists only two profiles.
    **[SDK]**/**[AOSP-35]** §2c.
12. **"`fetchUuidsWithSdp()` is just a read of what the device supports."** It is an over-the-air
    service-discovery transaction. **[SDK]** §8.2 item 1.
13. **"Bind every profile so the state is always hot."** Each bind is an async IPC connection with no
    error code and a `false` failure; `HEALTH` is refused by the implementation and the *close* doc
    covers two profiles while the *get* doc covers five. **[AOSP-35]**/**[SDK]** §7.1.
14. **"Subscribe to `ACTION_ACTIVE_DEVICE_CHANGED` / codec changes — it's only observing."** That is the
    audio-routing surface; Phase 3's boundary excludes modelling or touching it. **[AOSP-35]** §8.2
    item 8.
15. **"Ask the earbud something over a vendor AT command."** `sendVendorSpecificResultCode` writes to a
    live HFP connection; `startVoiceRecognition` additionally needs `MODIFY_PHONE_STATE` and
    `stopVoiceRecognition` shuts down the audio path. **[SDK]**/**[AOSP-35]** §8.2 items 2–3.
16. **"Treat `ACTION_FOUND` as a cheap way to discover devices."** It needs `BLUETOOTH_SCAN`
    (+ location unless `neverForLocation`) — i.e. it is scanning, which the phase forbids, and it is the
    only device broadcast that mentions location at all. **[SDK]** §5.3.

---

## 13. Findings that contradict existing project text (report; do not silently edit)

- **X-1** Phase 2 §6 R-1 / D-10 concludes adapter-state observation should register **flag-free**
  because "`ACTION_STATE_CHANGED` is a system broadcast → do not add the flag". For **connection**
  observation that reasoning does not transfer: the broadcasts guide states that Bluetooth broadcasts
  come from a highly-privileged non-system UID and are lost by `RECEIVER_NOT_EXPORTED`, and that
  `RECEIVER_EXPORTED` is what receives them **[DOC]** §4.3. Phase 2's advice is still right for its
  own action (it is *not required* to specify a flag), but a reader who carries "system broadcast ⇒ no
  flag needed / NOT_EXPORTED is fine" into Phase 3 will build a silently dead observer. Recommend the
  governance text be re-checked against §4.3 by the owning agent.
- **X-2** Phase 2 §3 row 15 (`getProfileConnectionState`) documents the enforcement band and the
  adapter-off collapse, and is correct. It does not record that the value is served from a
  **per-process IPC cache invalidated by push** **[AOSP-35]** §4.5, nor that
  `BluetoothManager.getConnectedDevices(int)` is GATT-only and throws otherwise **[AOSP-35]** §2e.
  Neither is a contradiction, but both change what a "row" in the matrix means for a reader
  designing an observer.
- **X-3** Phase 2 §3 rows 13–14 record `getBondedDevices()` → "empty set when state ≠ `STATE_ON`".
  The API-35 implementation shows a **second**, indistinguishable empty-set cause (permission denied)
  and a `null` return on the fall-through path **[AOSP-35]** §6.1. The row is not wrong; it is
  incomplete in the exact direction Phase 3 cares about.
- **X-4** Nothing in Phase 2's document anticipates that the **profile** connection actions
  (`android.bluetooth.a2dp.profile.action.*`, `…headset.profile.action.*`,
  `android.bluetooth.action.LE_AUDIO_*`, `…CSIS_*`) are a separate, larger and more useful event
  surface than the adapter-level action. They appear in no Phase 2 row. This is additive, not
  contradictory, and is noted so the phase-3 security section re-derives each of its own
  requirements (`SEC-PERM-003`) instead of inheriting row 15's phrasing.

---

## 14. Citations — everything read in this session

Local, compileSdk 35 platform data (path withheld as machine-local; `local.properties` in the repo
root records it):

- **[S-01]** `platforms/android-35/data/api-versions.xml` — root `<api version="3">`; class/membership
  and `since` lookups for `android/bluetooth/{BluetoothAdapter, BluetoothDevice, BluetoothManager,
  BluetoothProfile, BluetoothProfile$ServiceListener, BluetoothClass, BluetoothClass$Device,
  BluetoothClass$Device$Major, BluetoothClass$Service}` and the full 64-entry
  `android/bluetooth/**` class inventory. Parsed with `xml.etree`.
- **[S-02]** `platforms/android-35/android-stubs-src.jar` — entries `android/bluetooth/{BluetoothManager,
  BluetoothProfile, BluetoothDevice, BluetoothAdapter, BluetoothClass, BluetoothA2dp, BluetoothHeadset,
  BluetoothLeAudio, BluetoothHearingAid, BluetoothCsipSetCoordinator}.java`. All Javadoc quotations in
  §2–§8 come from these files. Read with `zipfile`.
- **[S-03]** `platforms/android-35/android.jar` — constant-pool presence probes on
  `android/bluetooth/{BluetoothDevice, BluetoothAdapter, BluetoothManager}.class` for negative
  existence claims (`getMetadata`, `isConnected`, `getBatteryLevel`, `getIdentityAddress`,
  `ACTION_META_CHANGED`, `registerBluetoothConnectionCallback`, `BluetoothConnectionCallback`,
  `ACTION_BLE_ACL_CONNECTED`, `getSupportedProfiles`, `getMostRecentlyConnectedDevices`,
  `BluetoothAdapter.getConnectedDevices`). Also checked
  `platforms/android-35/core-for-system-modules.jar`, which contains **no** `android/bluetooth`
  entries (1831 entries, zero matches) — recorded so nobody reads that jar as an alternate source for
  these names.
- **[S-04]** `platforms/android-35/data/broadcast_actions.txt` — 336 lines; the 25
  `android.bluetooth.*` lines quoted with their file line numbers in §4.1.

AOSP, tag `android-15.0.0_r11` (API 35), read in full this session; supporting evidence, never a
public contract:

- **[A-01]** `packages/modules/Bluetooth/framework/java/android/bluetooth/BluetoothAdapter.java` —
  `PROFILE_CONSTRUCTORS` l.818; `getMaxConnectedAudioDevices` l.2780;
  `getMostRecentlyConnectedDevices` l.2885 (`@SystemApi`/`@hide`/`allOf{CONNECT,PRIVILEGED}`);
  `getBondedDevices` l.2917; `getSupportedProfiles` l.2954 (`@SystemApi`/`@hide`/privileged);
  `getProfileConnectionState` + `IpcDataCache` l.3030-3110; `getProfileProxy` +
  `SecurityException` l.3619-3660/3648; `registerBluetoothConnectionCallback` /
  `unregisterBluetoothConnectionCallback` / `class BluetoothConnectionCallback` l.4716-4800;
  `ACTION_BLE_ACL_CONNECTED` (hidden).
  <https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/tags/android-15.0.0_r11/framework/java/android/bluetooth/BluetoothAdapter.java>
- **[A-02]** `.../framework/java/android/bluetooth/BluetoothDevice.java` — `ACTION_ACL_CONNECTED`
  l.179, `ACTION_ACL_DISCONNECTED` l.210, `getAddress` l.1544, `getAlias` l.1667,
  `isConnected` l.2188 (`@SystemApi`), `getUuids` l.2308, `fetchUuidsWithSdp` l.2342/2372,
  `setMetadata` l.3210, `getMetadata` l.3238 (both `@SystemApi` + `allOf{CONNECT,PRIVILEGED}`).
- **[A-03]** `.../framework/java/android/bluetooth/BluetoothManager.java` — `getConnectionState`,
  `getConnectedDevices`, `getDevicesMatchingConnectionStates` bodies (GATT/GATT_SERVER guard).
- **[A-04]** `.../framework/java/android/bluetooth/BluetoothProfile.java` — public and
  `@SystemApi`/`@hide` profile constants with values (l.100-228).
- **[A-05]** `.../android/app/src/com/android/bluetooth/btservice/AdapterService.java` —
  `getBondedDevices(AttributionSource)` l.2594-2603; `getProfileConnectionState` l.2622-2641;
  `getIdentityAddress` l.2401-2414; `getBondedDevices()` l.4748;
  `notifyProfileConnectionStateChangeToGatt`/`handleProfileConnectionStateChange` l.6031-6053;
  `enforceCallingOrSelfPermission(BLUETOOTH_PRIVILEGED)` sites l.2351-2802;
  `sendBroadcastMultiplePermissions(..., new String[]{BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED}, …)`
  l.1412.
- **[A-06]** `.../btservice/AdapterProperties.java` — profile-action switch l.148-204; internal
  `IntentFilter` registration l.256-272; `mMaxConnectedAudioDevices` default l.110 and the
  resource + `SystemProperties` override l.226-240;
  `getProfileConnectionState` l.690-698; `updateCountersAndCheckForConnectionStateChange`
  l.856-908; `updateProfileConnectionState` l.910-959 (cache invalidation call at l.957, wrapper at
  l.289-291); `updateOnProfileConnectionChanged` l.735-789 — intent built l.773-778 including
  `BluetoothDevice.EXTRA_DEVICE` at l.774, adapter-level `sendBroadcastAsUser(..., UserHandle.ALL,
  BLUETOOTH_CONNECT, …)` at l.782; `cleanupPrevBondRecordsFor` l.618-672;
  `ACTION_LOCAL_NAME_CHANGED`/`ACTION_BLUETOOTH_ADDRESS_CHANGED` send sites l.963-1010.
- **[A-07]** `.../btservice/RemoteDevices.java` — `reset()` ACL-disconnect send l.190-210;
  `aclStateChangeCallback` l.1173-1306 (adapter-state gating, `EXTRA_TRANSPORT`,
  `sendBroadcast(intent, BLUETOOTH_CONNECT, …)` l.1304-1305).
- **[A-08]** `.../gatt/GattService.java` — binder `getDevicesMatchingConnectionStates` l.434-441;
  implementation l.1851-1888 (permission→empty; bonded non-BR/EDR candidate seeding;
  `mClientMap`/`mServerMap` connected sets).
- **[A-09]** `.../android/app/src/com/android/bluetooth/a2dp/A2dpService.java` —
  `sendBroadcast(intent, BLUETOOTH_CONNECT, …)` for `ACTION_ACTIVE_DEVICE_CHANGED` and
  `ACTION_CODEC_CONFIG_CHANGED` l.1163-1181.
- **[A-10]** `.../android/app/src/com/android/bluetooth/ChangeIds.java` l.22-31 —
  `ENFORCE_CONNECT = 211757425L`, `@EnabledSince(targetSdkVersion = UPSIDE_DOWN_CAKE)`.
- **[A-11]** `.../android/app/src/com/android/bluetooth/Utils.java` and `.../btservice/AbstractionLayer.java`
  (`BT_ACL_STATE_CONNECTED=0x00`, `BT_ACL_STATE_DISCONNECTED=0x01`) — read for the state vocabulary.
- **[A-12]** `frameworks/base/core/res/AndroidManifest.xml` — protection levels for `BLUETOOTH`,
  `BLUETOOTH_ADMIN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`,
  `BLUETOOTH_PRIVILEGED`, `ACCESS_FINE_LOCATION`, `LOCAL_MAC_ADDRESS`, `MODIFY_PHONE_STATE`.
  <https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-15.0.0_r11/core/res/AndroidManifest.xml>

`developer.android.com`, read this session:

- **[C-01]** Bluetooth permissions — <https://developer.android.com/develop/connectivity/bluetooth/bt-permissions>
  ("Target Android 12 or higher", "Strongly assert that your app doesn't derive physical location",
  the runtime-permission + **Nearby devices** paragraph).
- **[C-02]** Behavior changes: apps targeting Android 14 — <https://developer.android.com/about/versions/14/behavior-changes-14>
  ("Enforcement of `BLUETOOTH_CONNECT` permission in `BluetoothAdapter`", "Runtime-registered broadcast
  receivers must specify export behavior" + "Exception for receivers that receive only system
  broadcasts").
- **[C-03]** Broadcasts overview — <https://developer.android.com/guide/components/broadcasts>
  ("About system broadcasts", the implicit-broadcast restriction for targetSdk 26+, "Choose whether the
  broadcast receiver should be exported", "Some system broadcasts come from highly privileged apps,
  such as Bluetooth and telephony…", "BROADCAST_ACTIONS.TXT file in the Android SDK").
- **[C-04]** Implicit broadcast exemption list —
  <https://developer.android.com/guide/components/broadcast-exceptions>
  (`BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED`, `BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED`,
  `ACTION_ACL_CONNECTED`, `ACTION_ACL_DISCONNECTED` and the surrounding rule text). Fetched in a
  non-English locale; the constant lists are locale-independent.
- **[C-05]** Behavior changes: apps targeting Android 16 —
  <https://developer.android.com/about/versions/16/behavior-changes-16> (Bluetooth-stack section: CDM
  disconnect, `removeBond(int)`, the `ACTION_BOND_STATE_CHANGED` sentence). Also fetched in a
  non-English locale; see §4.6 for how much weight I put on it.
- **[C-06]** `BluetoothDevice` / `BluetoothAdapter` reference pages — attempted in this session and
  **not usable**: both fetches returned only the site navigation shell and were truncated before the
  class content. Every statement that would have come from them is sourced from **[S-02]** instead,
  which is the same doc text in file form. Recorded so that the absence of a reference-page citation
  is not mistaken for an unverified claim.
- Attempted and rejected as a source: `https://developer.android.com/develop/connectivity/bluetooth/bt-connect-devices`
  → **404**. A WebSearch for third-party confirmation of ACL delivery behaviour returned only
  Stack Overflow / vendor-blog results; per `SEC-PERM-003` none of it is cited anywhere above.
  <https://stackoverflow.com/questions/67722950/android-12-new-bluetooth-permissions>,
  <https://bleadvertiserapp.medium.com/android-15-broke-your-ble-app-new-permission-rules-3d8cb3c9ba86>
  (surfaced, **not** used).

Repository files read this session: `local.properties`, `gradle/libs.versions.toml`,
`platform/android/build.gradle.kts`, `platform/android/src/main/AndroidManifest.xml`,
`docs/phases/phase-2/bluetooth-api-research.md` (structure, bands, R-1/R-5/D-8/D-10, U-1/U-2/U-7).

---

*Prepared by Agent B, Phase 3. Highest evidence rung reached: reference-implementation source and
official documentation, both read in this session. Nothing here is `LAB_TESTED` or
`HARDWARE_VERIFIED`; the twelve UNVERIFIED rows in §11 are the script a device session must run before
any Phase 3 claim about observed device state can be promoted above `UNKNOWN`.*
