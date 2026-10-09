# Phase 44 — Backward Compatibility and Deprecation Policy

---

## 1. Multi-Revision Coexistence

- An adapter integration may ship with support for both legacy (e.g. revision 1) and current (revision 2) wire protocols.
- Legacy implementations must remain available for older hardware builds.
- Introducing a revision 2 implementation must never remove or silently upgrade a revision 1 definition without evidence that all affected devices support revision 2.

---

## 2. Deprecation Process

- Implementations marked `@Deprecated` or with `deprecationNotice` generate structured diagnostic warnings during capability discovery.
- Deprecated protocols remain functional for read operations and supported writes until officially decommissioned after end-of-life testing.
- Historical evidence records for deprecated protocols are permanently preserved in the knowledge base.
