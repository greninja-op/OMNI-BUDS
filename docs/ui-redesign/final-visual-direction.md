# OmniBuds — Final Visual Direction (UI redesign, shipped in v1.8)

## Identity
OmniBuds is a fixed-identity product: deep black background, charcoal
surfaces, white primary text, muted grey secondary text, and ONE accent —
fluorescent yellow — reserved for selection, active hardware modes,
primary actions, and the connected state. Battery keeps a restrained
green; warning amber; error red.

This replaces the previous system dynamic-color theme, under which the
app inherited the phone wallpaper's blue. The app is dark by design on
every device; there is no light theme and nothing to configure
(Settings > Appearance states this plainly instead of offering dead
theme controls).

## Tokens (OmniBrand / OmniTokens)
- Background #050506 · Surface #101013 · SurfaceVariant #17171B ·
  Elevated #1E1E23 · Highest #26262C
- Accent #E9FF3C · OnAccent #0A0A0A · Text #F5F5F0 · Secondary #9C9CA3 ·
  Border #26262B · Disabled #55555C · Success #34A853 · Warning #FFB020 ·
  Error #FF5449
- Spacing 4/8/12/16/24/32 dp · Radius 12/20/28/full · Motion 160/260/420 ms

Material 3 remains the implementation toolkit; the color scheme, card
geometry, and component composition are the brand's own. Typography
stays on the M3 scale.

## Rules
- Yellow is never decoration. If it is yellow, it means something:
  selected, active, connected, or the primary action.
- Disconnected devices render their product image in grayscale.
- No app-created emojis anywhere in UI copy (standing rule).
- Product imagery is model-specific illustration, never a generic icon
  for a recognized product (see product-image-catalog.md).
