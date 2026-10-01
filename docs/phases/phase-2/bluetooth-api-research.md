# Phase 2 — Android Bluetooth API research: permission matrix and version behaviour

**Agent B (Android Bluetooth API specialist) deliverable.** Research only: this file declares no
permission, changes no manifest, no build file and no source (`SEC-PERM-003`).
Research date: 2026-10-01.

---

## 0. Method, evidence labels and limits

Every row was read from an official page on `developer.android.com` (Bluetooth permission guide,
behavior-change pages, permission/requesting guides, foreground-service guide, and the API reference
page for each member). Where a reference page is ambiguous or silent, the matching AOSP framework
source at tag `android-15.0.0_r11` (= Android 15, **API 35**, our `compileSdk`) was read as a
cross-check, because that is the code our `compileSdk 35` headers describe.

| Label | Meaning |
|---|---|
| **[DOC]** | Stated by the cited `developer.android.com` page. Short quotes are verbatim. |
| **[AOSP-35]** | Read from framework source at AOSP tag `android-15.0.0_r11`; supporting evidence, not a public contract. |
| **[INFER]** | My conclusion from labelled facts. Not a platform promise. |
| **[UNVERIFIED]** | Could not be confirmed. Must not be used as an implementation justification. §6 lists what was tried. |

Two scope warnings about the docs themselves:

1. **The live reference pages now render newer API levels than we compile against.** They show
   members "Added in API level 36/37" and deprecations "in API level 37" (e.g. all
   `connectGatt(...)` overloads are deprecated in 37 in favour of
   `connectGatt(BluetoothGattConnectionSettings, Executor, BluetoothGattCallback)`). Those do not
   exist at `compileSdk 35`. **[DOC]** with **[INFER]** that a `compileSdk 35` build cannot call them.
   Every band claim below was re-checked against the API 35 source tag.
2. **The permission requirement a page states is keyed to `targetSdkVersion`, not to the device's
   API level.** The recurring sentence form is "For apps targeting `Build.VERSION_CODES.R` or lower,
   this requires `BLUETOOTH`… For apps targeting `Build.VERSION_CODES.S` or higher, this requires
   `BLUETOOTH_CONNECT`…". **[DOC]** This is the single most misread fact in this area and it drives
   §7.

No device, emulator or `adb` was used (none is available; `docs/security/device-access-policy.md`
§"Status of device validation in Phase 2"). Therefore **no claim in this file is `LAB_TESTED` or
higher on the evidence ladder**: the highest rung reachable here is "the documentation says so".

---

## 1. Project configuration this matrix assumes

| Fact | Value | Where it comes from |
|---|---|---|
| `minSdk` | 26 (Android 8.0) | `gradle/libs.versions.toml`, `platform/android/build.gradle.kts` |
| `compileSdk` | 35 | same |
| `targetSdk` | **35 declared in the catalog, but no module currently uses it** — `:platform:android` is a `com.android.library` module and sets only `namespace`, `compileSdk`, `minSdk`. `targetSdkVersion` is an **application** property; a library has none. **[INFER]** | `platform/android/build.gradle.kts`, `settings.gradle.kts` |
| Consequence | The resolver must read the target SDK at runtime (`context.applicationInfo.targetSdkVersion`), never assume it from a build constant, and must fail safe if the app that eventually hosts this library declares something else. **[INFER]** | — |

Because targetSdk is the axis the framework checks, "API band" in the matrix below is written as
**t≤30** (targetSdk ≤ 30) and **t≥31** (targetSdk ≥ 31). Device API level appears only where the
docs key a requirement to it (the location-for-scan rule, the API-34 enforcement note, API 33
throttling codes).

---

## 2. Permission vocabulary

| Permission | Protection level | Class in practice | Since | Evidence |
|---|---|---|---|---|
| `BLUETOOTH` | `normal` | install-time, no prompt | API 1 | [DOC] `Manifest.permission` |
| `BLUETOOTH_ADMIN` | `normal` | install-time, no prompt | API 1 | [DOC] `Manifest.permission` |
| `BLUETOOTH_SCAN` | `dangerous` | **runtime** | API 31 | [DOC] `Manifest.permission`; guide: "The `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`, and `BLUETOOTH_SCAN` permissions are runtime permissions." |
| `BLUETOOTH_CONNECT` | `dangerous` | **runtime** | API 31 | [DOC] same |
| `BLUETOOTH_ADVERTISE` | `dangerous` | **runtime** | API 31 | [DOC] same |
| `ACCESS_FINE_LOCATION` | `dangerous` | runtime | API 1 | [DOC] `Manifest.permission` |
| `ACCESS_COARSE_LOCATION` | `dangerous` | runtime | API 1 | [DOC] `Manifest.permission` |
| `ACCESS_BACKGROUND_LOCATION` | `dangerous` + hard-restricted (installer allow-list) | runtime, separate grant | API 29 | [DOC] `Manifest.permission` |
| `BLUETOOTH_PRIVILEGED` | `signature\|privileged` | **unavailable to third-party apps** | API 19 | [DOC] `Manifest.permission`: "Not for use by third-party applications."; level from `frameworks/base/core/res/AndroidManifest.xml` [AOSP-35] |
| `FOREGROUND_SERVICE_LOCATION` | `normal\|instant` | install-time | API 34 | [DOC] `Manifest.permission` |

Requesting `BLUETOOTH_SCAN`/`CONNECT`/`ADVERTISE` shows the **Nearby devices** prompt group. **[DOC]**
Any resolver output naming a permission must also carry its class, because "install-time" means
`NOT_REQUIRED_AT_RUNTIME` for our UX, not "granted by the user".

---

## 3. Operation × API-band matrix

Legend per cell: `permission (install | runtime)`. `—` = no permission documented as required by
that call. "→" describes the failure mode the reference page documents.

| # | Operation | t ≤ 30 | t ≥ 31 | Device-level special cases |
|---|---|---|---|---|
| 1 | `BluetoothManager.getAdapter()` | — | — | — [DOC][AOSP-35] |
| 2 | `BluetoothAdapter.isEnabled()` | `BLUETOOTH` (install) | — | — |
| 3 | `BluetoothAdapter.getState()` | `BLUETOOTH` (install) | — | — |
| 4 | Register `ACTION_STATE_CHANGED`, read `EXTRA_STATE`/`EXTRA_PREVIOUS_STATE` | `BLUETOOTH` (install) | — | `EXTRA_STATE` itself lists no permission; the receiver-export flag rule is §6 item R-1 |
| 5 | `startDiscovery()` | `BLUETOOTH_ADMIN` (install) + `ACCESS_FINE_LOCATION` (runtime) | `BLUETOOTH_SCAN` (runtime); **plus** `ACCESS_FINE_LOCATION` (runtime) *unless* `neverForLocation` is asserted | returns `false` when state ≠ `STATE_ON` |
| 6 | Receive `ACTION_FOUND` (extra `EXTRA_DEVICE`…) | `BLUETOOTH` (install) + `ACCESS_FINE_LOCATION` (runtime) | `BLUETOOTH_SCAN` (runtime) unless `neverForLocation` | broadcast constant itself carries the requirement |
| 7 | `ACTION_DISCOVERY_STARTED` / `ACTION_DISCOVERY_FINISHED` | `BLUETOOTH` (install) | `BLUETOOTH_SCAN` (runtime) | no location sentence on these two constants |
| 8 | `BluetoothLeScanner.startScan(...)` (callback, filter list, or `PendingIntent`) | `BLUETOOTH_ADMIN` (install) + location for **results**: `ACCESS_COARSE_LOCATION`, and `ACCESS_FINE_LOCATION` when the app targets Q+ | `BLUETOOTH_SCAN` (runtime) unless `neverForLocation` | `BLUETOOTH_PRIVILEGED` needed for scan-only mode, `SCAN_MODE_AMBIENT_DISCOVERY`, batched+abbreviated reports, and filters carrying a non-public device address/IRK [DOC] — not obtainable by us |
| 9 | `BluetoothGatt`: `connect()` via `connectGatt()`, `readCharacteristic`, `writeCharacteristic`, `readDescriptor`, `writeDescriptor`, `setCharacteristicNotification`, `close` | *see note A* | `BLUETOOTH_CONNECT` (runtime) | Android-15 reference lists no t≤30 sentence for `connectGatt` |
| 10 | RFCOMM client `createRfcommSocketToServiceRecord()` + `BluetoothSocket.connect()` | not stated per-method (note A); `IOException` "insufficient permissions" documented | `BLUETOOTH_CONNECT` (runtime) | `BLUETOOTH_PRIVILEGED` additionally required only when the socket data path ≠ `DATA_PATH_NO_OFFLOAD` [DOC] |
| 11 | RFCOMM server `listenUsingRfcommWithServiceRecord()` (insecure, L2CAP variants) | `BLUETOOTH` (install) | `BLUETOOTH_CONNECT` (runtime) | — |
| 12 | SDP lookup `fetchUuidsWithSdp()` / `ACTION_UUID` | `BLUETOOTH` (install) | `BLUETOOTH_CONNECT` (runtime) | — |
| 13 | `getBondedDevices()` | `BLUETOOTH` (install) | `BLUETOOTH_CONNECT` (runtime) | returns **empty set** when state ≠ `STATE_ON` [DOC] |
| 14 | `BluetoothDevice.getBondState()` | `BLUETOOTH` (install) | `BLUETOOTH_CONNECT` (runtime) | `ACTION_BOND_STATE_CHANGED`: same pair |
| 15 | `getProfileConnectionState(A2DP / HEADSET / …)` | `BLUETOOTH` (install) | `BLUETOOTH_CONNECT` (runtime) | **t≥34 on any device ≥31: enforcement actually turned on** (§6 R-3); returns `STATE_DISCONNECTED` when adapter off [AOSP-35] |
| 16 | `BluetoothDevice.getName()` / `getAlias()` | `BLUETOOTH` (install) | `BLUETOOTH_CONNECT` (runtime) | `getAlias()` only exists at API 30+ |
| 17 | `BluetoothDevice.getAddress()` | no permission documented | no permission documented | [DOC] silence vs [AOSP-35] no annotation → treat as §6 U-2 |
| 18 | `BluetoothAdapter.getName()` / local `getAddress()` | `BLUETOOTH` (install) | `BLUETOOTH_CONNECT` (runtime); local address also `LOCAL_MAC_ADDRESS` (signature) → effectively unavailable | — |
| 19 | `ACTION_REQUEST_ENABLE` / `ACTION_REQUEST_DISCOVERABLE` (user-consent intents) | `BLUETOOTH` (install) | `BLUETOOTH_CONNECT` / `BLUETOOTH_ADVERTISE` (runtime) | `BluetoothAdapter.enable()/disable()` deprecated API 33 and "will always fail and return false" for t≥33 |
| 20 | `BluetoothLeAudio` (`getConnectedDevices`, `getConnectionState`, `getGroupId`, `getConnectedGroupLeadDevice`, `ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED`) | n/a (class is API 31+) | `BLUETOOTH_CONNECT` (runtime) on every method | §4 item 11: API availability ≠ LE Audio working |
| 21 | `getProfileProxy(context, listener, LE_AUDIO)` | docs list no permission; [AOSP-35] **throws** `SecurityException("Need BLUETOOTH permission")` when `targetSdkVersion < 31` and `BLUETOOTH` is not held | — (no permission documented or enforced for t≥31 [AOSP-35]) | `BluetoothProfile.LE_AUDIO` constant = API 33 |

**Note A (asymmetry to be honest about).** Several connect-class members (`connectGatt`,
`createRfcommSocketToServiceRecord`) list only the t≥31 sentence ("For apps targeting S or higher,
this requires `BLUETOOTH_CONNECT`") and no t≤30 sentence, while the guide's Android 11-or-lower
section states categorically that "`BLUETOOTH` is necessary to perform any Bluetooth classic or BLE
communication, such as requesting a connection, accepting a connection, and transferring data."
**[DOC]** **[INFER]** The per-method silence is a doc gap, not an exemption: the resolver should keep
`BLUETOOTH` for t≤30 on all connect-class operations and record the reason string as
"guide-level, not method-level".

---

## 4. Per-operation notes (claims → citations)

1. **Adapter handle.** `BluetoothManager.getAdapter()` — page shows no permission text [C-03];
   AOSP annotates `@RequiresNoPermission` [C-11]. `BluetoothAdapter.getDefaultAdapter()` is
   deprecated in API 31 in favour of `getSystemService(BluetoothManager.class).getAdapter()`, and
   returns "null if Bluetooth is not supported on this hardware platform" [C-02]. **[INFER]** The
   null-vs-disabled distinction is exactly the Phase 2 requirement "do not treat a missing adapter
   as a crash"; it is also the only *hardware* signal these two calls give.
2. **`isEnabled()` / `getState()`.** Both state only the t≤30 `BLUETOOTH` sentence, no S sentence
   [C-02]; AOSP pairs `@RequiresLegacyBluetoothPermission` with `@RequiresNoPermission` [C-11].
   → For our t≥31 app these are **permission-free on every device from 26 up**. This directly
   contradicts the pre-existing claim in `docs/phases/phase-0/security-governance.md`
   SEC-PERM-002 row "Adapter state (on/off)" ("connect class runtime from Android 12") — report,
   don't silently edit (§7 X-1).
3. **Adapter-state broadcast.** `ACTION_STATE_CHANGED` carries the t≤30 `BLUETOOTH` sentence and no
   S sentence; `EXTRA_STATE` lists no permission [C-02]. → observing the adapter needs no runtime
   permission for us.
4. **Classic discovery.** `startDiscovery()` requires `BLUETOOTH_SCAN` (t≥31) / `BLUETOOTH_ADMIN`
   (t≤30), and "In addition, this requires the `ACCESS_FINE_LOCATION` permission. For apps targeting
   S or higher, an alternative is a strong assertion that you will never derive the physical location
   of the device, made by declaring `usesPermissionFlags="neverForLocation"` … However, this
   assertion may restrict the types of Bluetooth devices you can interact with." [C-02] The same
   sentence is attached to `BluetoothDevice.ACTION_FOUND` [C-04]. `ACTION_DISCOVERY_STARTED`/`FINISHED`
   name `BLUETOOTH_SCAN` only [C-02]. Discovery "usually involves an inquiry scan of about 12
   seconds", is heavyweight, and returns `false` unless state is `STATE_ON` [C-02]. The guide adds:
   an app that "supports a service and can run on Android 10 (29) or Android 11, must also declare
   `ACCESS_BACKGROUND_LOCATION` … to discover Bluetooth devices" [C-01].
5. **BLE scan.** `BluetoothLeScanner.startScan(...)` (all three overloads) — `BLUETOOTH_SCAN` (t≥31)
   / `BLUETOOTH_ADMIN` (t≤30), the same location/`neverForLocation` sentence, and verbatim: "An app
   must have `ACCESS_COARSE_LOCATION` permission in order to get results. An App targeting Android Q
   or later must have `ACCESS_FINE_LOCATION` permission in order to get results." [C-05] The
   `BLUETOOTH_PRIVILEGED` cases are listed on the same page (scan-only mode when adapter state ≠
   `STATE_ON`, `SCAN_MODE_AMBIENT_DISCOVERY`, batched+abbreviated reports, and filters carrying a
   non-public device address/IRK) [C-05]. Unfiltered
   `startScan(callback)` scans "are stopped on screen off to save power" [C-05].
6. **GATT.** `BluetoothGatt.connect()`, `readCharacteristic`, `writeCharacteristic`,
   `readDescriptor`, `writeDescriptor`, `setCharacteristicNotification`, `close` all state "Requires
   `Manifest.permission.BLUETOOTH_CONNECT`" [C-06]; `connectGatt` likewise [C-04].
   `writeCharacteristic`/`writeDescriptor` return `BluetoothStatusCodes.ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION`,
   `ERROR_PROFILE_SERVICE_NOT_BOUND`, `ERROR_GATT_WRITE_NOT_ALLOWED`, `ERROR_GATT_WRITE_REQUEST_BUSY`,
   `ERROR_UNKNOWN` [C-06][C-10].
7. **RFCOMM/SPP.** `listenUsingRfcommWithServiceRecord`, `listenUsingInsecureRfcommWithServiceRecord`,
   `listenUsingL2capChannel`: legacy `BLUETOOTH` → `BLUETOOTH_CONNECT` [C-02].
   `createRfcommSocketToServiceRecord`: only "Throws IOException on error, for example Bluetooth not
   available, or insufficient permissions" [C-04] (see Note A). `BluetoothSocket.connect()`: t≥31
   `BLUETOOTH_CONNECT`, plus `BLUETOOTH_PRIVILEGED` only for non-`DATA_PATH_NO_OFFLOAD` paths [C-07].
   SDP: `fetchUuidsWithSdp` → `BLUETOOTH_CONNECT` [C-04].
8. **Bonded list / bond state.** `getBondedDevices()` → `BLUETOOTH_CONNECT` and "If Bluetooth state
   is not `STATE_ON`, this API will return an empty set" [C-02]; `getBondState()` →
   `BLUETOOTH_CONNECT` [C-04]; `createBond()` → t≤30 `BLUETOOTH_ADMIN`, t≥31 `BLUETOOTH_CONNECT`
   [C-04].
9. **Profile connection state.** `getProfileConnectionState(int)` → `BLUETOOTH_CONNECT` [C-02];
   Android 14 change: "Android 14 enforces the `BLUETOOTH_CONNECT` permission when calling the
   `BluetoothAdapter` `getProfileConnectionState()` method for apps targeting Android 14 (API level
   34) or higher. This method already required the `BLUETOOTH_CONNECT` permission, but it was not
   enforced." [C-08] [AOSP-35] returns `STATE_DISCONNECTED` if the adapter is off, so
   "profile disconnected" and "adapter off" are not distinguishable from this call.
10. **Names and addresses.** `BluetoothDevice.getName()`/`getAlias()` → `BLUETOOTH_CONNECT` [C-04];
    local `BluetoothAdapter.getAddress()` → `BLUETOOTH_CONNECT` **and** `LOCAL_MAC_ADDRESS` [C-02].
    `BluetoothDevice.getAddress()` lists no permission [C-04] and is unannotated in AOSP [C-11] —
    see §6 U-2.
11. **LE Audio.** Class `BluetoothLeAudio` "Added in API level 31"; docs: "This class provides the
    public APIs to control the LeAudio profile… Use `BluetoothAdapter.getProfileProxy` to get the
    `BluetoothLeAudio` proxy object… Each method is protected with its appropriate permission."
    [C-09] Member levels: `close`, `getConnectedDevices`, `getConnectionState`,
    `getDevicesMatchingConnectionStates`, `ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED` = API 31;
    `getGroupId`, `getConnectedGroupLeadDevice`, `GROUP_ID_INVALID` = API 33 [C-09]. AOSP 35 shows
    23 `@RequiresBluetoothConnectPermission` uses and a large `@SystemApi`/`@hide` remainder [C-11].
    `BluetoothProfile.LE_AUDIO` is "Added in API level 33" (value 22) [C-12], and the `getProfileProxy`
    javadoc still enumerates only HEADSET/A2DP/GATT/HEARING_AID/GATT_SERVER [C-02][C-11].
    **[INFER]** Capability inspection must gate LE Audio on `SDK_INT >= 33` **and** treat "API present"
    as only "OS API availability", the first of the four distinct facts Phase 2 §5.5 lists.
12. **Hardware feature checks (permission-free).** `packageManager.hasSystemFeature(FEATURE_BLUETOOTH)`
    / `FEATURE_BLUETOOTH_LE` and the `<uses-feature android:name="android.hardware.bluetooth…">`
    declarations are the documented way to ask about hardware rather than permissions [C-01].

---

## 5. Answers to the seven questions

### Q1 — per-operation requirements by band
See §3, with the per-call sentences in §4. The three structural answers:

- **Install-time (normal) legacy pair** `BLUETOOTH`/`BLUETOOTH_ADMIN` is what t≤30 requirements are
  made of — no prompt, so for our purposes they are "declare, don't ask".
- **Runtime (dangerous) trio** `BLUETOOTH_SCAN`/`CONNECT`/`ADVERTISE` covers t≥31 requirements and
  needs a real user grant.
- Adapter availability (rows 1–4) is in **neither** set for a t≥31 app: it is permission-free.

### Q2 — when location is really required

| Situation | Location needed? | Evidence |
|---|---|---|
| t≥31, `BLUETOOTH_SCAN` declared **with** `android:usesPermissionFlags="neverForLocation"` | **No** — remove `ACCESS_FINE_LOCATION` (or cap it `maxSdkVersion="30"`) | [C-01][C-02][C-05] |
| t≥31, `BLUETOOTH_SCAN` declared **without** the flag | **Yes** per the per-method sentences: "In addition, this requires `ACCESS_FINE_LOCATION`. For apps targeting S or higher, an alternative is a strong assertion…" | [C-02][C-04][C-05] |
| t≤30, classic discovery or BLE scan on a device API 29–30 | **Yes** `ACCESS_FINE_LOCATION` | [C-01][C-05] |
| t ≤ 28 (so no scoped permission exists at all) | `ACCESS_COARSE_LOCATION` is what the scan page says is needed "in order to get results"; the FINE requirement is triggered by the app's own targetSdk ("an App targeting Android Q or later"), not by the device | [C-05] |
| Discovery from a service while app targets 29–30 | **Also** `ACCESS_BACKGROUND_LOCATION` (restricted, installer allow-listed) | [C-01][DOC `Manifest.permission`] |
| Connect/GATT/RFCOMM/bonded-list/bond-state/name operations | **No location in any band** — location never appears in these requirement sentences | [C-02][C-04][C-06][C-07] |

Correct attribute name (the prompt's "BLUOTH_SCAN"-style shorthand is wrong): the *permission* names
are `android.permission.BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` / `BLUETOOTH_ADVERTISE`, and the flag
is declared as
`<uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation" />`.
There is no `BLUETOOTH_SCAN_PRIVILEGED`; the privileged permission is `BLUETOOTH_PRIVILEGED`
(`signature|privileged`, "Not for use by third-party applications"), and it is documented as
additionally needed for several non-default scan modes (§4 item 5) [C-01][DOC `Manifest.permission`][C-13].

Documented caveats of `neverForLocation`: "If you include `neverForLocation` in your
`android:usesPermissionFlags`, **some BLE beacons are filtered from the scan results**" [C-01], and
"this assertion **may restrict the types of Bluetooth devices** you can interact with" [C-02].
**[INFER]** For a control app that identifies devices partly by manufacturer advertisement data,
that filtering is a functional cost, so `neverForLocation` is a design decision for the phase that
scans, not a free win. Whether "results that could reveal location" are suppressed in other shapes
is **not** documented — §6 U-5.

### Q3 — legacy `BLUETOOTH`/`BLUETOOTH_ADMIN` on API 31+ and the declaration strategy

- For a t≥31 app the new scoped requirement replaces the legacy one: every reference page either
  names only the new permission or adds the t≤30 sentence conditionally (§4). [DOC]
- **[AOSP-35]** The framework encodes this as `@RequiresLegacyBluetoothPermission` +
  `@RequiresNoPermission`, and the one legacy runtime check found in the adapter path is explicitly
  target-gated: `getProfileProxy` throws `SecurityException("Need BLUETOOTH permission")` only when
  `context.getApplicationInfo().targetSdkVersion < Build.VERSION_CODES.S` [C-11]. **[INFER]** That
  is the mechanism by which the legacy permissions become inert for our app.
- The docs never say the word "ignored"; they say: "For your legacy Bluetooth-related permission
  declarations, **set `android:maxSdkVersion` to 30**. This app compatibility step helps the system
  grant your app only the Bluetooth permissions that it needs when installed on devices that run
  Android 12 or higher." [C-01] and the manifest comment "Request legacy Bluetooth permissions on
  older devices." [C-01] **[INFER]** Both halves matter: `maxSdkVersion="30"` keeps the legacy
  permission available on API 26–30 devices and absent on 31+.
- Recommended strategy for minSdk 26 / target 35, *when* the phase that needs it arrives:
  `BLUETOOTH`+`BLUETOOTH_ADMIN` with `android:maxSdkVersion="30"`; `BLUETOOTH_SCAN` (+
  `neverForLocation` if that phase can honestly assert it); `BLUETOOTH_CONNECT` only when the app
  actually communicates with a device; `ACCESS_FINE_LOCATION` with `maxSdkVersion="30"` only if
  scanning on ≤30 without a `neverForLocation` assertion is genuinely in scope, else omit it entirely.
  **[DOC]** the shapes, **[INFER]** the combination.

### Q4 — `DENIED_PERMANENTLY`

What the platform supports:

- **Detectable (public API, reliable):** current grant state only —
  `ContextCompat.checkSelfPermission` → `GRANTED` / `DENIED`, and the result array of
  `requestPermissions`.
- **Documented but *not* app-detectable:** permanent denial is real — "if the user taps Deny for a
  specific permission more than once … the user will no longer see the system permissions dialog if
  your app requests that permission again. The user's action implies "don't ask again", and is
  considered a **permanent denial**." [C-14] The only identification method the docs give is out of
  process: "`adb shell dumpsys package PACKAGE_NAME` … Permissions that have been denied once by the
  user are flagged by `USER_SET`. Permissions that have been denied permanently by selecting Deny
  twice are flagged by `USER_FIXED`", labelled "when testing and debugging" [C-14]. **[AOSP-35]**
  confirms no public read path: `FLAG_PERMISSION_USER_FIXED` is `@hide`/`@SystemApi` ("apps can no
  longer request this permission") and `getPermissionFlags` requires
  `GRANT`/`REVOKE`/`GET_RUNTIME_PERMISSIONS` [C-15] — unobtainable by us, and reading it any other
  way would break `SEC-PERM-008`.
- **`shouldShowRequestPermissionRationale`** — public docs give it no semantics beyond "Gets whether
  you should show UI with rationale before requesting a permission" [C-16], and the guide's rule is
  "If `checkSelfPermission()` returns `PERMISSION_DENIED`, call
  `shouldShowRequestPermissionRationale()`. If this method returns true, show an educational UI"
  [C-14]. **[INFER]** Since it is documented only for "show rationale now", `false` means "don't show
  a rationale screen" and is returned for *never-asked*, *permanently-denied*, *policy-fixed* and
  *auto-reset* alike. So it cannot distinguish them; treating `false` as proof of permanent denial is
  the exact defect the prompt forbids.
- **Anti-assumption rules the docs state outright:** "In certain situations, the permission might be
  denied automatically, without the user taking any action… It is important to not assume anything
  about automatic behavior. Each time your app needs to access functionality that requires a
  permission, check that your app is still granted that permission." [C-14] And Android 11 added
  auto-reset: "If users haven't interacted with an app for a few months, the system auto-resets the
  app's sensitive permissions." [C-17]

**Resolver ruling (§7 D-6):** emit `DENIED_PERMANENTLY` only when *all* of: our own persisted record
shows ≥ 1 denied `requestPermissions` result for that exact permission in this install;
`checkSelfPermission == DENIED`; and `shouldShowRequestPermissionRationale == false`; **and** the
state is labelled as an inference with its evidence, never as a platform fact. Otherwise `DENIED`,
`NOT_REQUESTED` or `UNKNOWN`. A "permanently denied" UI affordance may only ever be
"open app settings", which works in every one of those cases.

### Q5 — permission revoked mid-operation

- **There is no permission-scoped cancellation callback for a live scan/GATT op in the Bluetooth
  package.** Searched the API-35 reference pages for `onRequestPermissionsError` and
  `ERROR_INSUFFICIENT_PERMISSIONS`: **neither string exists on `BluetoothGatt`,
  `BluetoothGattCallback`, `BluetoothLeScanner`, `BluetoothAdapter`, `BluetoothDevice` or
  `BluetoothLeAudio`** [C-02][C-04][C-05][C-06][C-09], and the AOSP `BluetoothGattCallback`/
  `BluetoothLeScanner` at `android-15.0.0_r11` contain no permission callback either [C-11].
  **[INFER]** Any code or spec built on those two names would not compile.
- **What does exist:** the int error surface — `BluetoothStatusCodes.ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION`
  ("Added in API level 31 … indicating that the caller does not have the `BLUETOOTH_CONNECT`
  permission"), plus `ERROR_BLUETOOTH_NOT_ENABLED`, `ERROR_BLUETOOTH_NOT_ALLOWED`,
  `ERROR_DEVICE_NOT_BONDED` (31), `ERROR_GATT_WRITE_NOT_ALLOWED`, `ERROR_GATT_WRITE_REQUEST_BUSY`,
  `ERROR_PROFILE_SERVICE_NOT_BOUND`, `FEATURE_NOT_SUPPORTED` (33) [C-10], returned from the newer
  `BluetoothGatt` mutators and reported in their callbacks [C-06].
- `SecurityException` is the documented failure of the legacy path in `getProfileProxy` for t<31
  [C-11] and of some privileged-mode scan calls; most adapter calls instead **return `false` /
  an empty set / `STATE_DISCONNECTED`** rather than signalling "permission" at all — the ambiguity
  that matters for us (§4 items 8, 9, and `startDiscovery` [C-02]). **[UNVERIFIED]** which of
  throw-vs-return applies per call on real 31+ devices → §6 U-1.
- Revocation itself: the platform's own model is process death, not graceful degradation —
  `Context.revokeSelfPermissionsOnKill` (API 33) "Triggers the revocation of one or more permissions
  for the calling package… The revocation happens asynchronously and **kills all processes running in
  the calling UID**… Ultimately, you should never make assumptions about a permission status as users
  may grant or revoke them at any time." [C-18] **[INFER]** A mid-operation revocation may therefore
  never be observable as a callback at all: the process dies first. Design consequence: every
  operation re-checks grant state at its own entry point, and results stay `UNKNOWN` until proven.
- Scan-side throttling/failure surface, for the phase that scans:
  `ScanCallback.onScanFailure(int)` with `SCAN_FAILED_SCANNING_TOO_FREQUENTLY` ("as application tries
  to scan too frequently", API 33) and `SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES` (API 33), alongside
  the API-21 codes `ALREADY_STARTED`, `APPLICATION_REGISTRATION_FAILED`, `INTERNAL_ERROR`,
  `FEATURE_UNSUPPORTED` [C-19]. A `PendingIntent` scan reports failure via `EXTRA_ERROR_CODE` [C-05].

### Q6 — traps that make a naive Phase 2/3 implementation wrong

- **R-1: receiver-export flags are an API 34 / target-34 rule, not API 33, and system broadcasts are
  exempt.** "Apps and services that target Android 14 (API level 34) or higher and use
  context-registered receivers are **required** to specify a flag … either `RECEIVER_EXPORTED` or
  `RECEIVER_NOT_EXPORTED`", with "**Exception for receivers that receive only system broadcasts** …
  then it shouldn't specify a flag" [C-20], and `Context.registerReceiver` says the same for t≥34 and
  points at `BROADCAST_ACTIONS.TXT` in the SDK as the authoritative list [C-18]. `ACTION_STATE_CHANGED`
  is a system broadcast → **do not** add the flag for adapter observation; but the moment one
  `IntentFilter` mixes in a non-system action, the flag becomes mandatory [INFER]. The Phase 2 prompt's
  framing ("API 33+") is off by one band: the `RECEIVER_EXPORTED`/`RECEIVER_NOT_EXPORTED` **constants**
  are API 33 (they exist and may be passed from 33), but the **requirement** is keyed to
  `targetSdkVersion ≥ 34`, and `Context.registerReceiver(BroadcastReceiver, IntentFilter)` documents it
  as applying "For apps targeting `Build.VERSION_CODES.UPSIDE_DOWN_CAKE`" [C-18].
- **R-2: targetSdk ≠ device API.** Requirements are keyed to `targetSdkVersion` (§1, §4 item 2); device
  level decides whether the *new* permissions exist at all. An app asking for `BLUETOOTH_SCAN` on a
  device API 26–30 is asking for a permission that does not exist there. **[INFER]** Not stated on any
  page I read → §6 U-4; the resolver must not request API-31 permissions when `SDK_INT < 31`.
- **R-3: `getProfileConnectionState` only really enforces `BLUETOOTH_CONNECT` from target 34** [C-08],
  and returns `STATE_DISCONNECTED` when the adapter is off [AOSP-35] → two different truths can look
  identical. Never map `STATE_DISCONNECTED` to "no device paired/connected".
- **R-4: `enable()`/`disable()` are dead for us.** Deprecated in API 33; "Starting with
  `Build.VERSION_CODES.TIRAMISU`, applications are not allowed to enable/disable Bluetooth… For
  applications targeting `TIRAMISU` or above, this API will always fail and return false." [C-02] The
  sanctioned path is `ACTION_REQUEST_ENABLE`, which itself needs `BLUETOOTH_CONNECT` (t≥31) [C-02].
  **[INFER]** Adapter state therefore can be *observed* but never *caused* by us → `REQUIRES_USER_ACTION`.
- **R-5: empty is not absent.** `getBondedDevices()` → empty set, `getScanMode()` → `SCAN_MODE_NONE`,
  `startDiscovery()` → `false` when the adapter is off [C-02]. An implementation that writes
  `UNSUPPORTED`/empty-list from these is a `SEC-GEN-007` violation.
- **R-6: scan throttling.** Documented today only as `SCAN_FAILED_SCANNING_TOO_FREQUENTLY` /
  `SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES` (API 33) [C-19] and the screen-off stop for unfiltered scans
  [C-05]. The widely-quoted "5 scans per 30 s since Android 7.0" figure was **not** found on any
  current developer.android.com page → §6 U-3. Do not encode a number we cannot cite.
- **R-7: `neverForLocation` has a functional price** (beacon filtering, device-type restriction) —
  §4/§2 [C-01][C-02].
- **R-8: foreground services.** No Bluetooth-specific FGS type exists. `ServiceInfo` defines
  `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` (API 29) and nothing named `…_BLUETOOTH_*` [C-21]; the
  guide's connected-device row requires declaring `FOREGROUND_SERVICE_CONNECTED_DEVICE` plus being
  granted at least one of `BLUETOOTH_CONNECT`/`BLUETOOTH_ADVERTISE`/`BLUETOOTH_SCAN`/`UWB_RANGING` (or
  USB permission) [C-22]. Location is separate: type `location` needs `FOREGROUND_SERVICE_LOCATION`
  (API 34, `normal|instant`) **and** granted `ACCESS_COARSE/FINE_LOCATION` **and** enabled location
  services, and "you cannot create a location foreground service … while your app is in the
  background, unless you've been granted `ACCESS_BACKGROUND_LOCATION`" [DOC `Manifest.permission`][C-22].
  Android 14 also requires *every* FGS to declare a type [C-20]. Phase 2 forbids background services,
  so this is recorded only to stop a later phase re-deriving it wrongly.
- **R-9: `BluetoothDevice.getAddress()` is documented permission-free** while `getName()`/`getAlias()`
  need `BLUETOOTH_CONNECT` [C-04]. **[INFER]** An implementation can end up holding an address it was
  never meant to correlate; `SEC-ID-001/002` already governs that, and it is why the resolver's reason
  string must be per-operation, not per-address.
- **R-10: API 37 deprecations are visible in the docs but not available to us** (§0 warning 1).

### Q7 — what Phase 2 must declare now

**Nothing.** Recommended: leave `platform/android/src/main/AndroidManifest.xml` exactly as it is
(no `<uses-permission>`, no `<uses-feature>`), and let the resolver answer `NOT_REQUIRED` for every
Phase 2 operation.

Justification, per authorised operation:

| Phase 2 operation | Needs a manifest permission for a t=35 app? |
|---|---|
| `BluetoothManager.getAdapter()` | No [C-03][C-11] |
| `isEnabled()` / `getState()` | No [C-02][C-11] |
| Register `ACTION_STATE_CHANGED`, read `EXTRA_STATE` | No [C-02][C-18]; and no export flag (system broadcast, R-1) |
| `checkSelfPermission` / rationale inspection | No permission is needed to *inspect* permission state [INFER from C-14] |
| `packageManager.hasSystemFeature(FEATURE_BLUETOOTH / _LE)` | No [C-01] |
| `BluetoothAdapter.enable()/disable()` | Not usable at all (R-4) — declaring permissions cannot buy it back |
| `getProfileConnectionState` (only if capability inspection really calls it) | Needs `BLUETOOTH_CONNECT`, which is a **runtime** permission → that is a scanning/connecting-era dependency, not a Phase 2 one [C-02][C-08] |

The argument for declaring none, stated so it survives review:

1. A declaration we cannot tie to an executed call is a fabricated capability: "Permissions are added
   by the phase that actually uses them, with a documented justification in that phase's security
   section" (manifest comment) and `SEC-PERM-004` "Request nothing the app cannot justify to the user
   in one sentence… prohibiting … requesting 'for later'". Declaring `BLUETOOTH_CONNECT` now would
   also put the app in the **Nearby devices** prompt group with no feature behind it [C-01].
2. Manifest `<uses-permission>` in a **library** merges into every consumer APK, so an
   over-declaration here propagates silently — the cheapest fix is to declare it where it is used.
3. Even in a later phase, `BLUETOOTH_SCAN` alone carries a documented behavioral cost
   (`neverForLocation` filtering) that must be decided with the scan design in front of us [C-01].
4. The only *honest* Phase 2 manifest additions to consider are `<uses-feature
   android:name="android.hardware.bluetooth(_le)" android:required="false"/>`, which declare no
   permission at all; they change Play filtering, so leave them to the app module that ships [C-01].

**Boundary the resolver must keep:** `NOT_REQUIRED` in Phase 2 means "this operation needs nothing",
not "the app needs nothing". Rows 5–12 of §3 are the pre-computed answers for Phases 3+ so those
phases do not re-guess them — and each of them still owes its own re-verification (`SEC-PERM-003`).

---

## 6. UNVERIFIED / doc-ambiguous

| ID | Item | What I tried | Status |
|---|---|---|---|
| U-1 | Whether a t≥31 app that omits `BLUETOOTH` (`maxSdkVersion="30"` style) still succeeds on **device** API 26–30 for adapter calls, or hits `SecurityException` from those older framework builds | Reference pages only express the rule as "apps targeting R or lower"; the guide's `maxSdkVersion="30"` advice implies the legacy permission is still meaningful below 31 [C-01][C-02] | **UNVERIFIED** — needs a device on API 26–30. Phase 2 cannot resolve it because it declares no permission; it becomes load-bearing the moment the first legacy-band permission is declared |
| U-2 | Whether `BluetoothDevice.getAddress()` is genuinely permission-free or the docs simply omit the annotation | `BluetoothDevice` reference page lists no permission for `getAddress()` while `getName()`/`getAlias()` list `BLUETOOTH_CONNECT` [C-04]; AOSP 35 has no annotation on it [C-11] | **Doc-silent + AOSP-consistent**, but I will not state it as a granted capability. Resolver returns `UNKNOWN` for "address readable" until measured |
| U-3 | Numeric BLE scan throttling limit (the "5 scans / 30 s" figure) | WebSearch for a developer.android.com page; grep of `BluetoothLeScanner`, `ScanSettings`, `ScanCallback`, Bluetooth guide, behavior-change pages | **Not found on official pages.** Only the two API-33 error codes [C-19]. Do not encode a numeric policy |
| U-4 | Exact outcome of `requestPermissions()` for a permission the running platform does not define (e.g. `BLUETOOTH_SCAN` on API 30) | Checked `Manifest.permission` entries (API-level metadata only) and requesting guide (no statement) [C-14] | **UNVERIFIED.** Safe rule anyway: never request API-31 permissions when `SDK_INT < 31` [INFER] |
| U-5 | Whether `neverForLocation` suppresses scan results beyond the documented "some BLE beacons are filtered", and how approximate-vs-precise location interacts with scan results on ≤30 | Guide sentence [C-01]; Android 12 approximate-location section describes the FINE/COARSE request pairing but says nothing about Bluetooth scan results [DOC `about/versions/12/behavior-changes-all`] | **UNVERIFIED.** Phase 3 must re-read and, if still silent, treat as a device-validation item |
| U-6 | Whether `ACTION_STATE_CHANGED` is delivered as a sticky broadcast today (affects "initial state retrieval" in Phase 2 prompt §5.4) | `Context.registerReceiver` documents sticky-intent semantics generically [C-18]; the BluetoothAdapter page does not label `ACTION_STATE_CHANGED` sticky | **UNVERIFIED.** Do not rely on the `registerReceiver` return value for initial state; use `getState()` [INFER] |
| U-7 | Public-availability history of `BluetoothLeAudio` (page says "Added in API level 31" while `BluetoothProfile.LE_AUDIO` is 33 and half the class is `@SystemApi`) | `BluetoothLeAudio` reference page + per-member API levels [C-09]; `BluetoothProfile.LE_AUDIO` page [C-12]; AOSP `@SystemApi`/`@hide` counts [C-11] | **Doc-ambiguous.** Guard with `SDK_INT >= 33` for anything profile-based, and mark LE Audio support `UNKNOWN` otherwise |
| U-8 | Real-world enforcement of `@RequiresPermission` on API-35-era Bluetooth: does the framework throw, return `false`, or silently no-op | `@RequiresPermission` is not in public docs (404); `BluetoothStatusCodes` [C-10] and `getProfileProxy`'s explicit legacy throw [C-11] are all that is documented | **UNVERIFIED per call.** Structured errors must distinguish "platform refused" from "platform did not say why" |
| U-9 | `source.android.com` "Bluetooth permission mappings" table (referenced by the task brief as canonical) | Direct fetch returned **404** at `/compatibility/bluetooth/permission-mappings`; WebSearch did not surface a current equivalent | **Page no longer exists at that path.** All per-call facts in this file therefore come from the API reference pages themselves |

---

## 7. Implications for the version-aware permission resolver

### 7.1 Signature and inputs

The function is **not** `f(operation, apiLevel)`. It is
`f(operation, targetSdkVersion, deviceSdkInt)` (§1, R-2), returning
`Set<Requirement{permission, class, reason, source}>` plus a confidence label. Reason strings are
mandatory (Phase 2 §5.3), and each must be traceable to a §4/[C-] citation, i.e. to a page, not to
memory.

### 7.2 Decision rules the resolver should implement

- **D-1 — Adapter band.** `operation ∈ {getAdapter, isEnabled, getState, observe ACTION_STATE_CHANGED}`
  → `targetSdk ≤ 30` : `{BLUETOOTH (install)}`; `targetSdk ≥ 31` : `{}` (empty ⇒ `NOT_REQUIRED`).
  Source: [C-02][C-03][C-11].
- **D-2 — Scan band.** `targetSdk ≥ 31` : `{BLUETOOTH_SCAN (runtime)}` ∪ `{ACCESS_FINE_LOCATION
  (runtime)}` **unless** the manifest asserts `neverForLocation`; `targetSdk ≤ 30` :
  `{BLUETOOTH_ADMIN (install), ACCESS_FINE_LOCATION (runtime)}`. Sources: [C-01][C-02][C-05].
  If `deviceSdkInt < 31`, never emit `BLUETOOTH_SCAN` (U-4).
- **D-3 — Connect band.** `targetSdk ≥ 31` : `{BLUETOOTH_CONNECT (runtime)}`; `targetSdk ≤ 30` :
  `{BLUETOOTH (install)}`, reason "guide-level, not method-level" (Note A). Location is never added
  here (Q2 table).
- **D-4 — Privileged modes are out of reach.** Any requirement resolving to
  `BLUETOOTH_PRIVILEGED` / `LOCAL_MAC_ADDRESS` must be reported as an unsupported *platform
  restriction* (`REQUIRES_USER_ACTION`/`UNSUPPORTED_BY_PLATFORM`), never requested — `SEC-PERM-001`,
  `SEC-PERM-009` [C-01][C-02][C-05][C-07].
- **D-5 — Two required, not one.** A runtime requirement is only half the truth; the operation's
  *capability* preconditions (`STATE_ON`, feature present, API level) resolve separately, so
  "all permissions granted" must never collapse into "operation available".
- **D-6 — Never claim permanent denial** beyond what Q4 allows: only an inference carrying our own
  observation history, and only in the shape `DENIED_PERMANENTLY (inferred, evidence: N recorded
  denials + rationale=false)`. With no recorded denial, output `NOT_REQUESTED`; with contradictory
  data, `UNKNOWN` (ADR-P0-004).
- **D-7 — Check at every entry point.** Re-verify grant state on each operation, because revocation
  is asynchronous and process-killing (Q5) — cached permission state is a defect by itself [C-14][C-18].
- **D-8 — Non-permission failures must not be relabelled `PermissionDenied`.** `false` / empty set /
  `STATE_DISCONNECTED` from R-3/R-5 map to `BluetoothDisabled` or "indeterminate", not to a permission
  error, unless `ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION` was actually returned [C-02][C-10].
- **D-9 — API-availability axis.** Capability inspection returns four independent fields (OS API
  present / hardware feature present / permission granted / device support), each with `UNKNOWN`.
  "LE Audio API available" requires `SDK_INT ≥ 33` (profile constant `BluetoothProfile.LE_AUDIO`)
  *and* feature evidence, and never implies "active" (Phase 2 §5.5, U-7) [C-09][C-12].
- **D-10 — Receiver flag rule for the state observer:** flag-free registration for system-broadcast
  filters; if any non-system action enters the filter and `targetSdk ≥ 34`, the flag becomes
  mandatory (R-1) [C-18][C-20].

### 7.3 Where the resolver must return UNKNOWN rather than guess

1. **Cross-axis questions** — legacy permission required on a t≥31 app running on device API 26–30
   (U-1), and the outcome of requesting an undefined permission (U-4). These are the only two places
   where a naive band lookup would silently be wrong in the dangerous direction (asking for location
   we do not need, or omitting something that fails at runtime). `UNKNOWN` + "needs device
   validation", never a coin flip.
2. **Address readability** as a granted capability (U-2).
3. **Any "how many scans before throttling" answer** (U-3): the resolver may report
   `THROTTLED (observed)` only from a real `SCAN_FAILED_*` callback.
4. **`neverForLocation` side-effect scope** (U-5): the flag's benefit may be claimed; its cost is
   `UNKNOWN` in extent.
5. **Every non-documentable OEM difference** — the docs speak for AOSP; a specific phone is a separate
   evidence item, and `docs/security/device-access-policy.md` already commits us to treating
   unobserved device facts as `UNKNOWN`.

### 7.4 Findings that contradict existing project text (report; do not silently edit)

- **X-1** `security-governance.md` SEC-PERM-002, "Adapter state (on/off) … connect class runtime from
  Android 12": not supported by [C-02][C-03][C-11]. Adapter state is permission-free for a t≥31 app.
- **X-2** Phase 2 prompt §5.3 lists `BLUETOOTH`/`BLUETOOTH_ADMIN`/`ACCESS_*_LOCATION` as items to
  "inspect and verify the requirements for" *for this phase's operations* — correct as an audit task,
  but §7 shows the honest result is that Phase 2 needs none of them.
- **X-3** Phase 2 prompt §5.4's "Permission failures" for adapter-state observation: for our band, a
  permission failure is not a reachable state on this path (D-1) — the model should keep the state for
  later bands, but Phase 2 tests cannot produce it from a real platform path.

---

## 8. Citations

Guides / behavior changes (`developer.android.com`)

- **[C-01]** Bluetooth permissions — <https://developer.android.com/develop/connectivity/bluetooth/bt-permissions>
  (also reachable as `/guide/topics/connectivity/bluetooth/permissions`; sections "Declare
  permissions", "Target Android 12 or higher", "Strongly assert that your app doesn't derive physical
  location", "Target Android 11 or lower", "Discover local Bluetooth devices", "Check feature
  availability at runtime")
- **[C-08]** Behavior changes: apps targeting Android 14 — <https://developer.android.com/about/versions/14/behavior-changes-14>
  (§"Enforcement of BLUETOOTH_CONNECT permission in BluetoothAdapter", "Runtime-registered broadcasts
  receivers must specify export behavior", "Foreground service types are required")
- **[C-14]** Request app runtime permissions — <https://developer.android.com/training/permissions/requesting>
  (§"Handle permission denial", "Inspect denial status when testing and debugging")
- **[C-17]** Behavior changes: apps targeting Android 11 — <https://developer.android.com/about/versions/11/behavior-changes-11>
  (permissions auto-reset)
- **[C-20]** Behavior changes: Android 14 (same page as C-08), receiver-export section — <https://developer.android.com/about/versions/14/behavior-changes-14>
- **[C-22]** Foreground service types: overview and prerequisites — <https://developer.android.com/develop/background-work/services/fgs/service-types>

API reference

- **[C-02]** `BluetoothAdapter` (members: `isEnabled`, `getState`, `startDiscovery`,
  `cancelDiscovery`, `getBondedDevices`, `getProfileConnectionState`, `getName`, `getAddress`,
  `getScanMode`, `getRemoteDevice`, `getProfileProxy`, `enable`, `disable`, `getDefaultAdapter`,
  `ACTION_STATE_CHANGED`, `EXTRA_STATE`, `ACTION_DISCOVERY_STARTED`, `ACTION_DISCOVERY_FINISHED`,
  `ACTION_REQUEST_ENABLE`, `ACTION_REQUEST_DISCOVERABLE`, `listenUsingRfcommWithServiceRecord`) —
  <https://developer.android.com/reference/android/bluetooth/BluetoothAdapter>
- **[C-03]** `BluetoothManager.getAdapter()` — <https://developer.android.com/reference/android/bluetooth/BluetoothManager#getAdapter()>
- **[C-04]** `BluetoothDevice` (`getName`, `getAlias`, `getAddress`, `getBondState`, `connectGatt`,
  `createRfcommSocketToServiceRecord`, `createBond`, `fetchUuidsWithSdp`, `ACTION_FOUND`,
  `ACTION_BOND_STATE_CHANGED`) — <https://developer.android.com/reference/android/bluetooth/BluetoothDevice>
- **[C-05]** `BluetoothLeScanner` (`startScan` overloads, `stopScan`, location and
  `BLUETOOTH_PRIVILEGED` notes) — <https://developer.android.com/reference/android/bluetooth/le/BluetoothLeScanner>
- **[C-06]** `BluetoothGatt` (`connect`, `readCharacteristic`, `writeCharacteristic`,
  `readDescriptor`, `writeDescriptor`, `setCharacteristicNotification`, `close`, return-code tables) —
  <https://developer.android.com/reference/android/bluetooth/BluetoothGatt>
- **[C-07]** `BluetoothSocket.connect()` — <https://developer.android.com/reference/android/bluetooth/BluetoothSocket#connect()>
- **[C-09]** `BluetoothLeAudio` — <https://developer.android.com/reference/android/bluetooth/BluetoothLeAudio>
- **[C-10]** `BluetoothStatusCodes` — <https://developer.android.com/reference/android/bluetooth/BluetoothStatusCodes>
- **[C-12]** `BluetoothProfile` (`LE_AUDIO`, `HEADSET`, `A2DP`) — <https://developer.android.com/reference/android/bluetooth/BluetoothProfile>
- **[C-13]** `Manifest.permission` (`BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_SCAN`,
  `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_PRIVILEGED`, `ACCESS_FINE_LOCATION`,
  `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `FOREGROUND_SERVICE_LOCATION`) —
  <https://developer.android.com/reference/android/Manifest.permission>
- **[C-16]** `Activity.shouldShowRequestPermissionRationale(String)` —
  <https://developer.android.com/reference/android/app/Activity#shouldShowRequestPermissionRationale(java.lang.String)>
- **[C-18]** `Context` (`registerReceiver` overloads and export-flag text, `revokeSelfPermissionsOnKill`,
  `revokeSelfPermissionOnKill`) — <https://developer.android.com/reference/android/content/Context>
- **[C-19]** `ScanCallback` (`onScanFailure`, `SCAN_FAILED_*` constants) —
  <https://developer.android.com/reference/android/bluetooth/le/ScanCallback>
- **[C-21]** `ServiceInfo.FOREGROUND_SERVICE_TYPE_*` — <https://developer.android.com/reference/android/content/pm/ServiceInfo>

AOSP source, read as a cross-check only (never as a public contract)

- **[C-11]** Tag `android-15.0.0_r11` (API 35):
  `packages/modules/Bluetooth/framework/java/android/bluetooth/{BluetoothAdapter,BluetoothDevice,BluetoothManager,BluetoothGatt,BluetoothGattCallback,BluetoothLeAudio,BluetoothProfile,BluetoothStatusCodes}.java`
  and `.../android/bluetooth/le/BluetoothLeScanner.java`,
  plus `frameworks/base/core/java/android/app/Activity.java` and
  `frameworks/base/core/res/AndroidManifest.xml` —
  <https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/tags/android-15.0.0_r11/framework/java/android/bluetooth/BluetoothAdapter.java>
  / <https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-15.0.0_r11/core/res/AndroidManifest.xml>
- **[C-15]** Tag `android-15.0.0_r11`, `frameworks/base/core/java/android/content/pm/PackageManager.java`
  (`FLAG_PERMISSION_USER_FIXED` as `@hide`/`@SystemApi`, `getPermissionFlags` gated by
  `GRANT`/`REVOKE`/`GET_RUNTIME_PERMISSIONS`) —
  <https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-15.0.0_r11/core/java/android/content/pm/PackageManager.java>
- Legacy-band cross-check: tag `android-10.0.0_r1`, `frameworks/base/core/java/android/bluetooth/BluetoothAdapter.java`
  and `packages/apps/Bluetooth/src/com/android/bluetooth/btservice/AdapterService.java`.

---

*Prepared by Agent B, Phase 2. Highest evidence rung reached: documentation stated. Nothing here is
`LAB_TESTED` or `HARDWARE_VERIFIED`; the UNVERIFIED rows are the ones the phase that first performs
scanning or connecting must close out on a real device.*
