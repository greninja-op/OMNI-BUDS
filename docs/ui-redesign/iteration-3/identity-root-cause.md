# Identity Root Cause — Iteration 3

## Symptom
The device-details screen showed the literal text "Unknown" as the
device name for real, paired devices — and, as a cascade, lost its
product image, noise-control section, and feature sections, collapsing
to battery + Disconnect/Forget.

## The failure chain (all links verified in code)

1. **Identity was frozen at first registration.**
   `DeviceStateBridge.syncDevice` ingested `IdentityUpdated` only
   inside its first-registration gate. Any later sync — even one
   carrying the device's real name — updated connection only.

2. **The first observation often legitimately has no name.**
   A device can first reach the bridge from a scan upsert whose
   `BluetoothDevice.name` is null (advertisements frequently omit the
   name; classic discovery has no scan record), or from a bonded
   `describe()` executed before the BLUETOOTH_CONNECT grant settled
   (on Android 12+ `getName()` throws without it, and the provider
   correctly converts that to null). The discovery layer preserved
   better names when they arrived later — the bridge never re-asked.

3. **The placeholder became the display name.**
   The bridge stores the manufacturer as the literal string
   "Unknown" (an internal sentinel: the system does not report a
   manufacturer). `DeviceWorkspaceViewModel.buildOverview` composed
   the header as `model ?: manufacturer ?: ...`, so a null name
   promoted the sentinel into the UI verbatim: **"Unknown"**.
   `DevicesViewModel` had the same defect for repository-backed
   devices (`name = modelId ?: manufacturerId`), and
   `AndroidDiscoveredDevice.displayName` fell back to
   "Unknown Device (<address>)", leaking a raw address.

4. **The cascade.**
   The device page resolved its product-catalog entry from
   `overview.displayName`. With the name degraded to "Unknown", the
   catalog returned the category fallback with `features = null` —
   so the ANC section, feature rows, and product imagery all
   vanished even though the dashboard (which resolves from the
   discovery-layer name) could look correct. One identity defect
   presented as three separate feature regressions.

5. **The alias was never read.**
   `BluetoothDevice.getAlias()` (API 30+) — the name Android itself
   shows in Bluetooth settings — was not consulted anywhere.

## The fix

- **Core resolver** (`core/device/DisplayIdentityResolver.kt`): the
  single place a display identity is composed. Priority: user alias →
  Bluetooth alias → Bluetooth name → verified "Manufacturer Model" →
  neutral fallback "Bluetooth audio device". Candidates that are
  blank, the literal placeholder (any case), or MAC-shaped are
  rejected as evidence. Manufacturer/model fields are filled only by
  verified identification and stay separate from the display name.
- **Provider**: captures `device.alias` (API 30+, defensive) in both
  the bonded `describe()` path and the scan `upsert` path, preserving
  previously seen aliases.
- **Bridge**: identity is re-ingested whenever the reported evidence
  (alias/name) improves or changes — no longer frozen. Verified
  identification tries both the name and the alias against the
  catalog (a system-settings rename must not forfeit verification).
- **ViewModels**: both name mappings now go through the resolver;
  `overview.manufacturer` carries the sentinel-filtered value (null
  when unclaimed), and `overview.model` no longer masquerades the
  reported name as a verified model (the catalog layer owns model
  identity, as it already did for imagery and features).
- **User alias**: the hero's pencil action sets a per-device display
  alias (persisted, cleared on Forget). It changes only what
  OmniBuds shows; the Bluetooth name is untouched, and catalog
  matching always uses the hardware name, never the alias.

## Tests
- `DisplayIdentityResolverTest` (core, 11 tests): name, alias,
  disagreement, missing name, missing manufacturer, verified model,
  user alias, placeholder rejection, MAC rejection, determinism,
  similar-name independence.
- `DeviceStateBridgeIdentityTest` (platform/app, 4 tests): the
  regression itself — nameless first sighting, then the name arrives
  and identity follows; alias-as-evidence with name verification;
  no churn on unchanged evidence; unverifiable names.
- `DeviceOverviewIdentityTest` (platform/android, 3 tests): the
  overview can never surface the placeholder; discovery display
  names follow alias → name → fallback.
