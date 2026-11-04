# Backend Integration

The redesign is presentation-only. The Bluetooth engine, discovery
provider, device-state repository, and ViewModels are unchanged; the
UI renders their authoritative state exactly as before.

- Connection truth still comes from the multi-signal probe set
  (adapter queries, profile proxies, audio-output list) with re-sync
  on device-page open and on A2DP/headset broadcasts.
- ProductCatalog features affect section VISIBILITY only. A control
  becomes actionable only when the state engine marks it Ready —
  which, absent per-brand protocols, standard controls (media/volume)
  satisfy and vendor features do not. The workspace VM's
  self-acknowledgement trap remains mitigated exactly as before:
  capabilities are ingested as Discovering, never Ready.
- Battery values shown are the values the system reports; per-component
  rings appear only when components are actually reported.
- Product overrides are a presentation-layer concern stored in
  SharedPreferences (address -> catalog id); they change identity
  presentation and feature visibility, never device state.
- No mock data, no fake discovery results, no premature success states
  were introduced anywhere in this redesign.
