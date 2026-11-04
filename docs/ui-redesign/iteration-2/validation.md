# Validation — Iteration 2 (OmniBuds v1.9)

## Build
- Full source compile (core + platform/android + platform/app):
  exit 0, 0 errors, 5 warnings (pre-existing style warnings).
- Package: OmniBuds-v1.9.apk — versionCode 9, versionName "1.9",
  minSdk 26, targetSdk 35, 11,899,596 bytes,
  SHA-256 42d45f2b2c1e593645d5a8c0e06b775b4b9d75512eee29b65658c6d3338f7adc.
- Signing: same debug certificate as every previous build
  (SHA-256 19d9f28f4dcea87e850f8d65364afdd92e16922e1540885c3645a3c4938c3b93),
  so v1.9 installs directly over v1.8.
- Entry diff vs v1.8: 64 → 55 entries; removed = exactly the 9
  AI-generated/generic WebP assets; added = none. resources.arsc
  477,672 → 476,596 bytes (drawable table change only).
- The single remaining photograph (product_sony_wi_c100.webp) is
  byte-identical inside the APK to the licensed source asset.
- Dex verification (class definitions parsed, 18,200 total):
  ProductCatalog, ProductVisual, BatteryState$Component(Level)
  DEFINED; MediaKey and MediaCard classes ABSENT; the string
  "Brand link not built yet" ABSENT; no references to removed
  assets; the WI-C100 asset name and its CC BY-SA 4.0 attribution
  string present.

## Tests — 103 run, 103 passed (executed, not asserted)
New in this iteration:
- ProductCatalogTest — 14 (brand guard, variant guard, generation
  honesty, category integrity, override + generic, license metadata)
- BatteryComponentsPresentationTest — 6 (kind-based mapping,
  components-only, empty-known, no borrowed aggregate, staleness,
  range validation)
- BatteryComponentsAggregationTest — 3 (propagation, stale-sequence
  protection, per-device isolation)
- AppleAirpodsAdapterTest — +1 regression test (aggregate never
  attributed to buds when a source claims to distinguish them)
Regression suites re-run and green:
- Core battery package (JUnit 5): 31
- Global state: AggregationTest 6, GlobalStateModelTest 5,
  MultiDeviceTest 4
- BatteryStateTest 9; AppleAirpodsAdapterTest + contract 25 total
  (with BatteryPresentationTest, JUnit 5 batch)
- BatteryPresentationTest (existing) 4 — passes with the corrected
  fromComponents semantics.

## Environment limits (unchanged, stated plainly)
No emulator, device, or Bluetooth exists in the build environment.
On-device behavior of v1.9 (photo picker flow, dashboard art,
battery captions with real devices) requires the user's phone test.
No screenshots could be captured here — see visual-qa.md.
