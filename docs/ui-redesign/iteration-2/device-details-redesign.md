# Device Details Redesign — Iteration 2

Layout (in order), per the corrected information architecture:

1. **Header** — shell top bar: back, "Device details" (unchanged;
   system back walks the in-app stack, fixed in v1.7).
2. **Product identity** — visual hero (genuine photo / user's photo /
   category artwork; greyscale when disconnected), device name as
   reported, category caption, connection status with the quiet dot,
   photo attribution line when a licensed photo is shown, and the
   "Wrong product shown? Fix the match" action.
3. **Battery** — see battery-architecture.md. TWS with component
   data: LEFT | CASE | RIGHT rings with per-slot "Not reported"
   placeholders. Aggregate-only: one ring (Earbuds/Battery) with
   charging / last-known / stale captions as applicable. Over-ear
   and neckband: single representation. Ring colors: green > 20%,
   amber 11–20%, red ≤ 10%, grey for last-known/unknown.
4. **Noise control** — only for models known to have ANC. Modes from
   model knowledge; selection reflects observed state only (none is
   ever fabricated — with no control link, nothing is selected);
   interactive only if the capability engine marks the control
   actionable (none today); caption states the view-only status
   plainly.
5. **Device features** — known features of the model (EQ, spatial,
   wear detection, multipoint) as non-interactive rows with a
   "Not linked" state chip and an explanatory line. The genuinely
   observed codec row ("Sound — Active codec: X") appears when the
   system exposes it. Section omitted entirely when nothing applies
   (the previous empty-card bug is fixed).
6. **Limitations** — engine-supplied limitation lines, unchanged.
7. **Device management** — Disconnect / Forget as secondary buttons,
   now behind confirmation dialogs that also disclose side effects
   (Forget removes the product match and user photo).

Removed: the media-player card (volume slider, prev/play-pause/next)
and its entire plumbing through shell and MainActivity — including
the play/pause icon whose state was a local optimistic flag that
could misrepresent actual playback. SystemDiscoveryProvider's
AudioManager connection check is a different code path and remains.

Match-fix dialog (rebuilt): shows the current match state, "This
match is correct" (pins the automatic match per device), catalog
search, "Use a photo of your device", "Remove your photo", generic
choice, back-to-automatic, and corrected copy (it no longer claims
the device name changes — it never did).
