# Phase 44 — Protocol Versioning Policy

---

## 1. Guiding Principles

1. **Evidence Precedes Version Claims:** A protocol version is never assumed simply because a product has a similar name or brand. Version claims require explicit discovery observations or verified firmware mappings.
2. **Conservative Defaults:** Where a device's protocol version cannot be reliably established, it is tagged as `ProtocolVersion.Unknown`.
3. **Immutability of Historical Definitions:** Stored protocol definitions are immutable facts from specific research points. They are never overwritten by later assumptions.
4. **No Speculative Forward Compatibility:** Unless a protocol vendor specification explicitly guarantees forward compatibility across a minor revision range, revisions are treated as exact or explicitly enumerated sets.
5. **Fail-Closed on Mismatch:** Any mismatch between observed version and supported constraints results in non-actionable resolution (`INCOMPATIBLE` or `UNKNOWN_VERSION`), completely disallowing control packets.
