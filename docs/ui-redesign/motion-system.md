# Motion System

Principles: smooth, purposeful, lightweight, consistent. No bounce, no
decorative perpetual animation, nothing that fakes hardware feedback.

- All motion is gated on the system animator scale via
  `rememberMotionEnabled()` (Settings.Global.ANIMATOR_DURATION_SCALE);
  reduced motion = instant transitions.
- Destination changes: 160 ms fade + 220 ms short slide.
- Dashboard cards: 300 ms fade-in on first appearance.
- Product hero: 420 ms fade + 0.97→1.0 settle scale.
- Battery rings: 700 ms tween when a reported value changes (the value
  itself is never animated toward an invented number).
- Noise-control segments: corner radius + color animate 260 ms between
  the capsule (selected) and rounded-rectangle (unselected) shapes.
- Add-device scan: three expanding pulse rings (the only looping
  animation in the app, and it means "scanning is live"); the scan
  auto-stops after 30 s and stops immediately on leaving the screen.
- Only transform/alpha properties animate — no layout thrash.
