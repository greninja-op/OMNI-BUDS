# Battery Root Cause — Iteration 3

## Question
Why does a TWS device show one aggregate percentage instead of
left / case / right?

## Finding: nothing is dropped — the components never exist in any
## signal standard Android gives a third-party app

Pipeline audit, end to end:

- **Source**: the only battery signal the app receives is the system
  broadcast `android.bluetooth.device.action.BATTERY_LEVEL_CHANGED`,
  whose single extra is one device-level integer. The receiver in
  `MainActivity` reads exactly that extra; there are no component
  extras in this broadcast to drop.
- **Other standard surfaces checked**: `BluetoothDevice` battery is
  the same device-level value; the @hide per-device API remains
  non-public; HFP indicators are device-level; BLE advertisements
  from the seeded models carry no standardized component fields, and
  no vendor payload format is documented/verified for them.
- **Transport/domain**: Iteration 2 verified the rest of the chain —
  the domain model (`BatteryState.Known.components`), the bridge
  contract (`onBatteryComponents`), the aggregator (sequence-safe,
  per-device isolated), the presentation mapping (by component kind),
  and the LEFT | CASE | RIGHT UI with explicit "Not reported" slots
  all work with component data; the tests prove it. The single
  missing link is a *source*, not code.
- **The Iteration-2 defect class is closed**: the one place that
  could have fabricated components (the AirPods scope copying the
  aggregate into both buds) was rewritten and is regression-tested.

## Conclusion
For every currently seeded device the honest presentation is the
aggregate ring, labelled "Earbuds (combined)" for TWS so it cannot be
mistaken for a per-bud reading. The moment a verified per-component
source is integrated (the GATT Battery Service remains the first
candidate once it can be validated on hardware; vendor protocols
only with verified documentation), the three slots light up with no
further model or UI work. Fabricating the split — e.g. mirroring the
aggregate into three rings — was considered and rejected: it would
display two invented numbers next to one real one.
