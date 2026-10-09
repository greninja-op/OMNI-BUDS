# Phase 22 — Decisions

## D-22-01: JSON documents, not SQL, in core
**Decision:** Knowledge persists as JSON documents behind injected
read/write function types; no SQL in core.
**Rationale:** Core must stay JVM-clean and KMP-safe. The existing
ConfigurationStorage KV pattern and hand-written JSON codec already
establish this approach. A SQL backend can be added later without
changing the domain API.

## D-22-02: function-type storage seam
**Decision:** `saveAll`/`loadAll` take `suspend (key, value) -> Boolean`
and `suspend (key) -> String?` instead of importing ConfigurationStorage.
**Rationale:** `configuration` is layer 5; importing it from `knowledge`
(layer 5) is a sideways architecture violation. The seam keeps layers clean.

## D-22-03: knowledge describes, never authorizes
**Decision:** No transport handles, no command constructors, no write APIs
in the knowledge package. Import never registers adapters.
**Rationale:** The core principle of the phase.

## D-22-04: staged loads
**Decision:** `loadAll` decodes everything before committing; any corrupt
record blocks the load and is reported.
**Rationale:** Readers never see partial state; corruption is visible,
never silent.

## D-22-05: ambiguity-preserving queries
**Decision:** Identity lookups return all candidates; unknown firmware gets
only explicitly safe protocols.
**Rationale:** Matches Phase 21's ambiguity discipline.
