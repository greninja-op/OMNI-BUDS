# Device Details Design — Iteration 3

Visual foundation: the supplied reference (vendor app, realme Buds
T300 page), rebuilt in OmniBuds' own system with honest states.

## Palette (brief tokens, adopted exactly)
Background #080808 · primary surface #17171B · secondary surface
#222228 · text #F5F5F5 / #A1A1AA · accent #E5FF36 · border #303036.
Accent discipline unchanged: selection, primary action, connected
state. Battery state colors stay restrained (green/amber/red/grey).

## Structure (top to bottom)
1. Shell header: back, "Device details", overflow (unchanged).
2. Hero: product visual in a 28dp surface (genuine photo / user
   photo / category artwork, greyscale when disconnected), name with
   a pencil action (per-device display alias), category caption,
   connection pill (dot + label, bordered).
3. Battery: LEFT | CASE | RIGHT rings when a source reports
   components (unreported slots are explicit); otherwise one larger
   aggregate ring labelled "Earbuds (combined)" for TWS. Ring =
   state-colored progress arc, component glyph, label + percentage
   beneath; charging and last-known/stale captions as data dictates.
4. Active noise control: header row (accent icon, title, honest
   subtitle), segmented control in a #222228 container — selected
   segment is the accent capsule with stacked icon + label,
   unselected segments transparent. View-only until a verified
   control link exists; selection is observed state only.
5. Sound & features: individual 24dp cards, accent icon, title,
   truthful subtitle ("Not linked in OmniBuds yet" / observed codec).
6. Limitations (engine-supplied, when present).
7. Management: Forget (outlined pill, trash icon) and Disconnect
   (accent pill, power icon), both behind confirmations.

## Deliberate deviations from the reference
- No fake toggle (the reference's gaming-mode switch), no chevron
  rows that lead nowhere, no per-bud percentages without a source,
  no bundled vendor render (licensing). Each deviation is a place
  where the reference — a first-party vendor app with a private
  protocol and full image rights — can do things a universal
  third-party app honestly cannot today.
- Motion: state-driven only (ring progress, segment selection, hero
  entrance), all gated on the system reduced-motion setting.
