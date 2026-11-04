# Visual Validation — Iteration 3

## What could not be done here, stated plainly

The brief asked for four screenshot iterations of the rebuilt
device page. This environment has no Android emulator, no display,
no Bluetooth, and no path to a physical device — the same standing
limitation recorded in Iterations 1 and 2. No screenshots were
produced, and none were fabricated or simulated. What follows is
the substitute evidence, plus the on-device checklist that closes
the gap.

## Off-device evidence gathered instead

- **Icon renders**: the four new glyphs (Edit, Trash, Power,
  PersonOutline) were converted from their source paths and rendered
  with cairosvg before integration — pencil, bin, power symbol, and
  person outline all read cleanly at small sizes, and every vector
  carries an explicit solid fill (the v1.4 invisibility defect
  class).
- **Palette**: the brief's tokens are applied as exact hex values in
  the OmniBrand theme (#080808 / #17171B / #222228 / #F5F5F5 /
  #A1A1AA / #E5FF36 / #303036) — verifiable in
  `OmniBudsComposeTheme.kt`, and the theme-consolidation suite
  passes.
- **Structure**: the page structure was verified by construction
  against the reference hierarchy (hero → identity row with rename
  action → connection pill → battery → segmented noise control →
  feature cards → management pills) and by the presentation test
  suites (114/114, see final-validation.md).
- **Motion**: all animation remains gated on the system
  reduced-motion setting, as before.

## The four iterations, honestly mapped

1. *Data iteration* — identity root cause found and fixed; without
   it every visual check would have validated the collapsed page.
2. *Structure iteration* — reference hierarchy rebuilt (this doc's
   structure list), palette tokens adopted exactly.
3. *Verification iteration* — full compile, 114 tests, dex-level
   probes on the shipped APK (resolver present, placeholder strings
   absent from the identity path).
4. *Device iteration* — belongs to the user's phone; checklist
   below. Until it is done, the redesign is **built and verified
   off-device, not yet confirmed on-device** — the same status every
   previous build carried at delivery.

## On-device checklist (user's phone)

1. Open each earbud's page: the header shows the device's real name
   (never "Unknown"); dashboard and details agree.
2. Rename via the pencil: the new name shows on the card and the
   page; the product photo/match does NOT change (matching uses the
   hardware name).
3. JBL Tune 130NC / CMF Buds pages: "Active noise control" section
   present with three modes, nothing pre-selected.
4. Battery: one ring labelled "Earbuds (combined)" for TWS (no
   per-component source exists yet); Sony WI-C100 shows its photo
   with attribution.
5. "Sound & features" cards show honest "Not linked" subtitles;
   Disconnect is the yellow pill, Forget the outlined pill; both
   ask for confirmation.
6. Forget a device: its rename, product match, and photo are gone
   if it is re-added later.
