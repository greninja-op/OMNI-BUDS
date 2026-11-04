# Device Details — Design

Layout (top to bottom), inspired by the Pixel Buds page's information
architecture, executed in the OmniBuds identity:

1. **Product hero** — the product's own illustration, large, on a
   charcoal surface; grayscale when disconnected. Entrance: 420 ms
   fade + subtle settle scale (reduced-motion aware).
2. **Identity** — device name, category caption, connection status
   with a single quiet dot (yellow = connected).
3. **Match fix** — "Wrong product shown? Fix the match" opens the
   catalog picker (same category first, radio rows, a generic option,
   and a return-to-automatic option when an override is set).
4. **Battery** — rings per genuinely reported component (left / case /
   right) with per-component bud/case glyphs; a single reported value
   renders as one ring ("Earbuds: N%"). Never fabricated splits.
5. **Playback card** (connected only) — volume + AVRCP transport,
   standard Android controls that genuinely work.
6. **Active noise control** — segmented buttons; modes come from model
   knowledge; selected = fluorescent-yellow capsule; corners animate
   between capsule and rounded-rectangle shapes (260 ms). Selection
   reflects observed state only; an unlinked tap answers with an
   explanation, never a fake state change.
7. **More controls** — only features the model is known to have or the
   device has genuinely exposed; short human subtitles; transparency
   lives in the noise group only (never duplicated).
8. **Sound** row only when a codec is genuinely observable.
9. **Forget / Disconnect** — demoted to secondary outlined buttons at
   the bottom of the page; they are not the headline actions.
