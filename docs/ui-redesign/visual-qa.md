# Visual QA

## What was verified, and how
- Every product illustration was rendered and visually inspected
  before inclusion (10/10 assets). One rejection (CMF case shape) was
  regenerated and re-inspected.
- Every custom icon glyph was rendered off-device (SVG rasterization)
  and inspected, after an earlier release shipped invisible icons.
- The APK package was verified structurally before delivery: entry
  list diffed against the previous build, resource table size,
  version stamps, signing certificate, and dex presence of the new
  classes (ProductCatalog, ProductImage, brand theme).

## What could NOT be verified here
This environment has no Android emulator or device access, so rendered
in-app screenshots cannot be produced here. On-device review on the
user's phone is the render test (same constraint as every prior
OmniBuds build). Items awaiting on-device confirmation for v1.8:
dashboard card legibility (the JBL illustration is dark-on-dark),
hero crop behavior at 216 dp, grayscale treatment feel, and the
override picker's effect on FP-BT.

## Known remaining visual limitations
- The Add-device (discovery) results list still uses text rows rather
  than product thumbnails; it inherits the brand theme.
- Family-level products (e.g. Galaxy Buds) use category illustrations;
  dedicated artwork exists for the 7 seeded models.
- Illustrations are representations, not photographs; fine product
  details may differ slightly from retail hardware.
