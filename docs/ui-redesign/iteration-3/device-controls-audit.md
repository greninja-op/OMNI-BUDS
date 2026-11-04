# Device Controls Audit — Iteration 3

## Why the sections were missing
Not a controls bug: an identity cascade. The device page resolves
its catalog entry (which carries feature knowledge) from the
overview display name. When identity degraded to "Unknown"
(identity-root-cause.md), the catalog returned the fallback entry
with `features = null`, the ANC gate (`knownFeatures?.anc`) failed,
the feature-row filter emptied, and the screen collapsed to battery
+ management actions. Fixing identity restores the sections for
every catalogued model; no controls code needed to change for this.

## What renders now, and under what rule
- **Noise control**: shown when the catalog verifies ANC for the
  matched model; modes = the model's known modes (ANC / Off /
  Transparency); selection reflects observed state only (none is
  fabricated — with no control link, nothing is selected);
  interactive only if the capability engine ever marks the control
  actionable, which it does not today.
- **Sound & features**: one card per catalog-known feature (EQ,
  spatial audio, wear detection, multipoint), non-interactive,
  subtitled "Not linked in OmniBuds yet". The genuinely observed
  codec row appears when the system exposes a codec.
- **Not shown, deliberately**: touch/gesture customization, gaming
  mode, and find-my-earbuds rows appear in the reference design for
  a vendor app, but OmniBuds' catalog has no verified knowledge
  entries for them and no control link exists; rendering them would
  be decoration dressed as capability. They join the UI if and when
  verified knowledge + a protocol link exist.
- **Management**: Disconnect (primary) and Forget (secondary), both
  behind confirmations; Forget also removes the device's product
  match, user photo, and user alias.

## Standing hazard (unchanged, still documented)
`DeviceWorkspaceViewModel.submitFeatureControl` self-asserts success
for actionable controls without a transport write. It remains
unreachable (capabilities ingest as Discovering only) and no adapter
may mark capabilities Ready until that path performs a real write +
readback.
