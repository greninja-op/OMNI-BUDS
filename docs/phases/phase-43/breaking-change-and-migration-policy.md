# Phase 43 — Breaking Change and Migration Policy

---

## 1. Deprecation & Breaking Change Policy

To preserve stability across community integrations:

1. **Deprecation Period:**
   - Any public SDK API slated for removal or modification must be marked with `@Deprecated` across at least one minor release cycle (`1.x.0`).
   - A replacement method or migration guideline must be documented in release notes.

2. **Major Version Increments:**
   - Breaking contract modifications trigger a major version bump (`1.x.x` -> `2.0.0`).
   - Older adapters targeting `major = 1` will be rejected deterministically by `CommunityPackageValidator` and the host runtime until recompiled against SDK v2.

3. **Additive Changes:**
   - New capability fields or result variants must provide default parameters or backwards-compatible fallbacks so existing compiled adapters continue operating without errors.
