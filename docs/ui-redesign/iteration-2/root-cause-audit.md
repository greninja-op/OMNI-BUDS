# Iteration 2 — Root-Cause Audit

Method: four parallel read-only investigations of the full repository
(battery pipeline; product identity/catalog; device-details UI and
controls; external photo-licensing research), consolidated and reviewed
by the orchestrator. All findings are from code unless marked research.

---

## Issue 1 — Product images are AI-generated illustrations, not authentic photography

- **Problem:** The 7 seeded models render AI-generated illustrations
  presented as product imagery. No photographic product image exists in
  the app.
- **Affected files:** `presentation/workspace/ProductCatalog.kt`;
  `platform/app/src/main/res/drawable-nodpi/product_*.webp` (7 files) +
  `generic_*.webp` (3 files, 2 of them greyscale derivatives of the
  illustrations); `ui/compose/components/ProductImage.kt`.
- **Root cause:** Iteration 1 deliberately chose original illustration
  over scraped manufacturer photography on licensing grounds, but
  presented the illustrations as product representations without
  authenticity labeling. No per-asset provenance metadata existed
  anywhere (verified: catalog entries carry no source/license fields;
  no sidecar files).
- **Existing behavior:** Recognized model → its illustration;
  everything else → one of 3 shared generic illustrations (17 of 24
  catalog entries share them, so e.g. AirPods Pro, Galaxy Buds and
  Pixel Buds Pro display the identical image).
- **Licensing research (external):** Wikimedia Commons + manufacturer
  terms checked per model. Exactly ONE seeded model has a genuinely
  licensed exact-model photograph: **Sony WI-C100** — CC BY-SA 4.0,
  author KKPCW (Kyu3), 2048×2048, Commons file page
  `File:Sony_in-ear_headphone_WI-C100.jpg` (visually verified by the
  orchestrator: the module in the photo is printed "WI-C100"). For the
  other six: no freely licensed exact-model photo exists on Commons,
  and no manufacturer media kit permitting app bundling could be
  verified; HARMAN/JBL terms **explicitly prohibit** reuse of product
  images. Openverse/Flickr CC was unreachable (Cloudflare) — an
  unexplored avenue, not a verified negative.
- **Intended behavior:** Genuine licensed photography where it legally
  exists, with provenance + attribution; no AI imagery anywhere; no
  unlicensed bundling; honest neutral category representation
  otherwise; a legitimate path to a genuine image of the user's exact
  device — the user's own photo.
- **Proposed correction:** Remove all 10 illustration WebPs. Bundle the
  WI-C100 Commons photo (WebP conversion, format-only; image unedited)
  with catalog-level source/license/attribution metadata and an
  in-app attribution on the About screen. Add a per-device
  "use a photo of your device" flow (system photo picker → app-private
  storage keyed by device address) that takes precedence over catalog
  imagery. Everything else renders a vector category artwork (drawn,
  clearly not a photograph). Photos render with ContentScale.Fit —
  never cropped.
- **Required tests:** Catalog tests asserting no entry references a
  removed illustration asset; resolution tests (Issue 2).

## Issue 2 — Identity resolution is a fragile substring matcher

- **Problem:** `EXACT` confidence is granted on bare substring hits;
  brand-free keys ("buds t300", "buds 5a") can capture other brands'
  products; generations collapse (all AirPods Pro generations → one
  entry; "CMF Buds" also matches CMF Buds Pro; Sony "wf-1000" spans
  XM3/XM4/XM5); Sennheiser Momentum True Wireless resolves as over-ear;
  unknown-category devices default to TWS artwork; a clone advertising
  "AirPods Pro" receives full Apple identity at EXACT confidence.
- **Affected files:** `ProductCatalog.kt` (resolve/candidatesFor),
  `KnownDeviceFeatures.kt` (override-blind legacy path).
- **Root cause:** Single `firstOrNull` list-order scan, `contains()`
  matching, no tokenization, no brand requirement, no generation
  model, `exact` flag defaulting true even for entries with no image.
- **Existing behavior:** see audit table (24 entries, order = priority).
- **Intended behavior:** Brand-qualified exact matching for the seeded
  models; longest/most-specific key wins; family confidence (not
  EXACT) where the generation/variant is not distinguishable from the
  advertised name; correct category for Momentum TW; override honored
  by every consumer.
- **Proposed correction:** Rewrite matching: tokenized keys, exact
  entries require their brand token (or a model code unique enough to
  stand alone, e.g. "wi-c100", "130nc"); specificity scoring instead of
  list order; airpods_pro / pixel_buds_pro downgraded to FAMILY
  confidence (generation unknowable from the name); Momentum split
  into TW (TWS) and over-ear entries; UNKNOWN category renders the
  neutral artwork, not TWS art.
- **Required tests:** New `ProductCatalogTest`: brand-guard cases
  (generic "Buds T300" without realme → not EXACT realme), generation
  cases (AirPods Pro → FAMILY), CMF Buds Pro ≠ CMF Buds, Momentum TW
  category, unknown fallback, override precedence incl. `__generic__`.

## Issue 3 — Media player card on the device page

- **Problem:** Volume slider + prev/play-pause/next on device details.
  The play/pause icon is a **local optimistic flag** (`remember {
  mutableStateOf(false) }` in `MediaCard`) never synced to real
  playback state — it can misrepresent what is playing.
- **Affected files:** `WorkspaceScreen.kt` (MediaCard, MediaKey),
  `OmniBudsAppShell.kt`, `MainActivity.kt` (mediaVolume fields,
  refreshMediaVolume/applyMediaVolume/dispatchMediaKey).
- **Root cause:** Added in an earlier iteration as "genuinely working
  standard controls" without a product decision that a media player
  belongs in a hardware-management app.
- **Existing behavior:** Card visible when connected; slider drives
  STREAM_MUSIC; keys dispatch AVRCP to the active media session.
- **Must NOT be removed:** `SystemDiscoveryProvider.audioOutputConnected()`
  uses a separate AudioManager path as one of three connection-truth
  signals (v1.5/v1.7 false-negative fix). It is untouched.
- **Proposed correction:** Remove the card and the entire pass-through
  plumbing (screen/shell/MainActivity). OmniBuds stays out of the
  media path.
- **Required tests:** Compile + package verification; dex probes
  confirming MediaCard/MediaKey absence and connection classes intact.

## Issue 4 — Battery: the component model exists and is not wired (central defect)

- **Problem:** The UI can render per-component rings, yet no real
  device can ever populate them. Only one aggregate value reaches the
  UI.
- **Affected files:** `core/battery/*` + `core/device/BatteryState.kt`
  (Phase-16 stack), `core/globalstate/*` (Phase-24 live stack),
  `core/presentation/battery/UnifiedBatteryModel.kt`,
  `platform/android/.../presentation/battery/*`,
  `platform/app/.../DeviceStateBridge.kt`, `MainActivity.kt`.
- **Root cause:** Two parallel architectures. The Phase-16 stack is
  component-aware and well tested (BatteryEngine, BatterySnapshot,
  per-component freshness/provenance/conflict resolution) but **dead
  code**: BatteryEngine is instantiated only in tests; its Android
  observation source is an explicit no-op; `fromComponents` is called
  only from tests. The live Phase-24 `globalstate.BatteryState.Known`
  has **no component slots at all**. The app's only ingestion is
  MainActivity's `BATTERY_LEVEL_CHANGED` broadcast receiver → one
  Int → bridge → aggregate. No GATT Battery Service reads, no HFP
  parsing, no vendor battery source exists (`BatteryReportingSupport`
  has zero implementations).
- **Existing behavior:** Aggregate battery mostly works and is honest
  (range-checked, deduped, 0 never used as unknown). But: components
  never exist; charging is structurally always null (broadcast carries
  none); **disconnect never invalidates battery** — the last value
  renders as if live indefinitely; bridge dedupe + retained state mean
  a reconnect with the same level never refreshes the timestamp;
  presentation computes `isStale` (60 s) but the ring UI ignores it;
  component glyphs are chosen by substring match on a name string.
- **Landmine (D4):** `AirpodsCapabilityScope.batteryFromAndroidAggregate
  (aggregate, sourceDistinguishesBuds = true)` **copies the aggregate
  into both buds** — the exact fabrication the product forbids.
  Unreachable in production and untested in the `true` branch, but it
  must not survive this iteration.
- **Intended behavior:** One component-aware model on the live path:
  components as typed slots (LEFT/RIGHT/CASE), nullable levels,
  per-component availability, observed-at provenance, freshness states
  (live / last-known / stale), disconnect marking, per-device
  isolation, out-of-order protection. Aggregate-only sources stay
  aggregate-only. No value is ever manufactured.
- **Proposed correction:** Extend `globalstate.BatteryState.Known`
  with typed component readings (default empty). Bridge: clear the
  dedupe cache on disconnect (reconnect re-emits and refreshes
  provenance); add a component-ingestion entry point on the bridge so
  a future verified source lands without model changes (no production
  component source exists today — stated plainly in the source
  matrix). Presentation maps components by kind, surfaces
  stale/last-known, and the UI renders L|CASE|R with per-slot
  "Not reported" placeholders, aggregate-only single-ring otherwise.
  Fix D4 by construction: the helper can no longer place an aggregate
  into component slots. GATT Battery Service (0x180F/0x2A19, SIG
  standard) is documented as the next candidate source and explicitly
  NOT implemented here: the app has no GATT connection lifecycle, and
  adding one without on-device validation would be unverifiable.
- **Required tests:** Component propagation through
  repository/aggregator; bridge battery behavior (dedupe, disconnect
  reset, component ingestion); presentation mapping (partial
  components, aggregate-only, last-known); AirPods scope no-duplication
  test for the previously untested branch; existing battery suites
  re-run.

## Issue 5 — Feature controls are placeholders presented as a finished surface

- **Problem:** Every feature row's connected-state subtitle is
  "Brand link not built yet"; the mode-picker dialog offers radios
  that are disabled; the ANC segmented group looks interactive but a
  tap only ever produces a toast.
- **Root cause:** The bridge ingests capabilities only as
  `Discovering`, so every control's capability kind is UNKNOWN,
  `isActionable` is always false, READ_ONLY/UNSUPPORTED are never
  assigned. Visibility is driven purely by retail model knowledge.
  Separately, the VM's actionable path (`submitFeatureControl`)
  self-asserts `FeatureObserved` + `Succeeded` with no transport call
  — a latent fake-success path if capabilities ever became Ready
  without a real adapter behind them.
- **Existing behavior:** T300 shows EQ + Spatial rows; ANC group shows
  catalog modes with nothing ever selected (no observed state exists).
  "More controls" can also render an empty Surface (audio model is
  always non-null).
- **Intended behavior (brief §7/§8):** classify every control;
  implemented+working → interactive; known-but-not-linked → clearly
  labelled non-interactive capability display; unsupported/unknown →
  omitted. No fake pickers, no fake selection, no "Brand link not
  built yet".
- **Proposed correction:** Device page gains a "Device features"
  section: noise modes shown as a static capability display (no
  selection, caption stating control is not available in OmniBuds for
  this device), feature rows non-interactive with a "Not linked"
  state label; the mode-picker dialog is removed; the empty-section
  bug is fixed; the genuine codec observation row stays. The VM
  self-acknowledgement hazard is documented (not reachable while
  capabilities are Discovering; flagged in feature-availability.md as
  a precondition for any future vendor adapter).
- **Required tests:** Visibility/classification covered by catalog +
  presentation tests; copy audit (no banned strings in the APK dex).

## Issue 6 — Device management has no confirmations; override has gaps

- **Problem:** Forget/Disconnect fire immediately (no confirmation);
  the picker has no search, no "this match is correct" action, and
  its copy promises the *name* follows the choice — it never does.
  Overrides are never cleaned up on Forget.
- **Proposed correction:** Confirmation dialogs for Forget/Disconnect
  (honest "requested" outcome wording stays). Picker: search field,
  current-match row pinned with a Confirm action (persists the
  automatic match as an override), generic option, your-own-photo
  option, corrected copy. Forget clears that device's override +
  photo.
- **Required tests:** Override persistence covered indirectly via
  catalog resolve tests; forget-cleanup verified in code review +
  package checks (no device available for UI tests — see visual-qa).

## Issue 7 — Verification limits of this environment

- No Android emulator/KVM/device exists in this sandbox, so the
  brief's screenshot-capture iterations cannot be executed here and
  mockups will not be substituted. Visual QA = off-device asset
  inspection + the user's phone. All other acceptance items (audit,
  models, pipeline, tests, build, docs) are executed and evidenced.
