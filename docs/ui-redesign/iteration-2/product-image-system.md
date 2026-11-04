# Product Image System — Iteration 2

## Policy
1. No AI-generated or synthetic product imagery anywhere in the app.
2. A photograph is bundled only when its license permits
   redistribution, with source, license, and attribution recorded on
   the catalog entry, shown beside the image in the app, credited in
   About, and documented here.
3. Where no licensed photograph exists, the product gets NO image —
   the UI draws a neutral vector category artwork (earbuds /
   headphones / neckband glyph on charcoal). A drawn symbol is honest;
   a substitute photograph of a different product is not.
4. The user's own photo of their own device is supported (below) —
   for most products it is the only genuinely authentic image of the
   exact physical device.

## Bundled photographs (complete list)
| Model | Source | License | Attribution | Asset |
|---|---|---|---|---|
| Sony WI-C100 | Wikimedia Commons, File:Sony in-ear_headphone_WI-C100.jpg | CC BY-SA 4.0 | KKPCW (Kyu3) | product_sony_wi_c100.webp (format-converted to WebP, image unedited, 2048×2048 source) |

ShareAlike note: the WebP conversion is a format change only; the
asset remains CC BY-SA 4.0 and its attribution travels with it.

## Models with NO bundled photograph (and why)
realme Buds T300, JBL Tune 130NC, CMF Buds, Redmi Buds 5A, boAt
Rockerz 450, Skullcandy Hesh ANC: no freely licensed exact-model
photograph exists on Wikimedia Commons (searched 2026-11-04), and no
manufacturer media terms permitting app bundling could be verified.
HARMAN/JBL terms explicitly prohibit reuse of their product images.
Openverse/Flickr CC could not be checked (blocked by a bot challenge
from this network) — an open avenue, not a closed one. These models
render category artwork, or the user's own photo.

## User-supplied photos
Device page → "Fix the match" → "Use a photo of your device" (system
photo picker). The image is copied to app-private storage
(`files/product_photos/<device>.jpg`), keyed by Bluetooth address,
and takes precedence over catalog imagery on both the dashboard card
and the device hero. Removing it (same dialog) deletes the file.
Forgetting the device deletes photo + catalog override together.

## Identity resolution (corrected)
Token-based matching with specificity scoring. Exact entries require
brand-qualified keys; a variant guard rejects exact matches when the
name carries an unaccounted variant token (CMF Buds Pro ≠ CMF Buds).
AirPods Pro / Pixel Buds Pro are family-confidence because the
advertised name cannot distinguish generations. Sennheiser Momentum
True Wireless resolves as TWS; Momentum headphones as over-ear.
Unknown devices keep their real name with category artwork and no
invented identity. Covered by ProductCatalogTest (14 tests).

## Rendering
Photographs render whole (ContentScale.Fit) in the hero — never
cropped into a different-looking product; dashboard cards crop as
thumbnails. Disconnected devices render photographs in greyscale.
