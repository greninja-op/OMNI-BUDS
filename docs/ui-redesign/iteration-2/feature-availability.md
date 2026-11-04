# Feature Availability — Iteration 2

Classification of every control the device page can show, per the
brief's five states. The governing fact: the app bridge ingests
capabilities as Discovering for every device, so no vendor feature
control is engine-verified today — and the UI now says exactly that
instead of dressing placeholders up as features.

| Feature | State | Presentation |
|---|---|---|
| Connection status | Implemented + working | Status dot + text from live multi-signal detection |
| Battery (aggregate) | Implemented + working | Ring from system broadcasts; freshness captions |
| Battery (L/R/case) | Model + pipeline implemented; **no source integrated** | Slots render when a source reports them; "Not reported" otherwise |
| Codec observation | Implemented + working (when the system exposes it) | "Sound — Active codec" info row |
| Product match override | Implemented + working | Picker with search/confirm/generic/own-photo |
| Disconnect / Forget | Implemented (best-effort platform APIs) | Confirmation dialogs; outcomes worded "requested", failures route to system Settings |
| ANC / transparency modes | **Supported by models, not implemented** (no verified vendor protocol) | View-only segmented display, nothing selected, "View only" caption |
| Equalizer presets | Supported by some models, not implemented | "Device features" row, "Not linked" chip |
| Spatial audio | Supported by some models, not implemented | Same |
| Wear detection | Model knowledge incomplete in catalog (flags exist, all false) | Row appears only if catalog knowledge asserts it |
| Multipoint | Same as wear detection | Same |
| Low latency / sidetone / voice prompts | Unknown/unverified for all catalog models | Never shown (catalog has() returns false) |
| Software DSP EQ / fake ANC / fake spatial | **Prohibited** | Not present anywhere |

## Known hazard, documented (not introduced by this iteration)
`DeviceWorkspaceViewModel.submitFeatureControl` asserts
FeatureObserved + Succeeded itself when a control is actionable,
without a transport call. It is unreachable today (capabilities are
never Ready), but **no vendor adapter may mark capabilities Ready
until that path performs a real protocol write + readback**. The
noise-control UI calls it only when the engine marks the control
actionable.

## Copy rules enforced
- "Brand link not built yet" and similar placeholder strings are
  removed from the device page.
- Nothing tappable exists that cannot act: view-only surfaces are
  non-interactive and labelled.
- Unknown stays unknown: no zero batteries, no default modes, no
  pre-selected segments.
