# Visual QA — Iteration 2

## What could not be done here, stated plainly
This build environment has no emulator, no display, and no Android
device, so **no screenshots of v1.9 were captured** and no on-device
visual pass was possible. Any screenshot in a report from this
environment would be fabricated; none is included. Device-rendered
verification is the user's phone test of the delivered APK.

## Off-device verification performed instead
- Vector artwork audited as rendered images: the new Neckband glyph
  was rendered to PNG and visually inspected before shipping; the
  category artwork uses the same OmniIcons set whose fills were
  verified in v1.4 (every path carries an explicit fill — the
  invisibility bug class from v1.4 cannot recur unnoticed because
  the glyphs were rendered).
- The one bundled photograph was visually verified as the genuine
  product before conversion: the photographed module is printed
  "SONY CORP. WI-C100" with the red right-ear marker.
- Layout behavior follows from deterministic code paths exercised
  by unit tests for the state inputs (identity, battery components,
  staleness); the Compose layouts themselves are code-reviewed
  against the design doc (device-details-redesign.md).

## Specific things to check on the phone
1. Dashboard: every device except the WI-C100 shows a drawn
   category symbol (earbuds/headphones/neckband) — no photographs
   except the WI-C100 and any photo you add yourself.
2. WI-C100 page: the real desk photograph, whole and uncropped,
   with the KKPCW / CC BY-SA 4.0 credit line under it.
3. Device page: no media card anywhere.
4. Battery: single aggregate ring as before; when disconnected, the
   ring greys and reads "Last known — the device is disconnected."
5. "Fix the match": search filters the list; "This match is
   correct" pins the match; "Use a photo of your device" opens the
   system picker and your photo then appears on card + hero.
6. Forget: confirmation appears; after forgetting, the device's
   match and photo are gone too.
7. Feature rows (EQ etc.) show "Not linked" and do not respond to
   taps; noise-control segments are visible but view-only with the
   "View only" caption.
