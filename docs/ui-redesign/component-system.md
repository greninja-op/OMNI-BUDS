# Component System

Reusable components (ui/compose/components + theme):
- `ProductImage` — bundled illustration by asset name, with grayscale
  treatment and a glyph-tile fallback if an asset is missing.
- `OmniIcons` — hand-built vector glyph set (headphones, buds L/R/pair,
  case, noise-off, sound waves, sparkle, media transport). Every path
  carries an explicit fill (lesson: Compose vector paths default to no
  fill and render invisibly).
- `BatteryRing` — per-component ring with component glyph + label.
- `NoiseControlSegmented` — animated-shape segmented buttons.
- `OmniBrand` / `OmniTokens` — color, spacing, radius, motion tokens.
- `ErrorBanner`, `StatusBadge`, `rememberMotionEnabled`.

Screens compose these; per-screen one-off styling is avoided. New
product imagery only ever enters through ProductCatalog + ProductImage.
