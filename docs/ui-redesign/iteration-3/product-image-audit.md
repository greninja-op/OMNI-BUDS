# Product Image Audit — Iteration 3

Policy is unchanged from Iteration 2 and re-verified here:

- The image resolver keys off the resolved identity: catalog match
  from the hardware name (never the user's display alias, never a
  degraded placeholder). The Iteration-3 identity fix removes the
  case where a nameless state silently forced generic artwork for a
  known model.
- Bundled photography: exactly one — Sony WI-C100, CC BY-SA 4.0,
  KKPCW (Kyu3) via Wikimedia Commons, attribution in the hero
  caption and About. No other model has a license-clear photograph;
  HARMAN/JBL terms prohibit reuse outright. Nothing AI-generated,
  nothing substituted.
- Everything else: vector category artwork, or the user's own photo
  of their device (picker flow, per-device, removed on Forget).
- "Fix the match" (search, confirm-current, generic choice, own
  photo) is unchanged and now also reachable in a healthy state for
  devices whose identity previously collapsed.

Reference-design note: the supplied reference shows a studio render
of the realme Buds T300. OmniBuds cannot bundle that image (no
license) and will not imitate it with generated art; a user who owns
the device can add their own photograph, which is the only exact
image of their actual unit.
