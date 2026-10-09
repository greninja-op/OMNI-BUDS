# Phase 22 — Design

## Architecture

New package `com.omnibuds.core.knowledge` at layer 5:

- **KnowledgeIds.kt** — stable value-class identifiers, `KnowledgeLifecycle`
  (7 states), `RecordMetadata`.
- **KnowledgeEntities.kt** — Manufacturer, DeviceModel, FirmwareProfile,
  ProtocolDefinition.
- **KnowledgeSchemas.kt** — MessageSchema (+MessageField), CapabilityDefinition,
  OperationDefinition.
- **Evidence.kt** — EvidenceRecord, Claim, Source, EvidenceType, SourceType.
- **KnowledgeJson.kt** — deterministic hand-written JSON codec.
- **KnowledgeCodecs.kt** — per-entity encode/decode with "kind" discriminator.
- **KnowledgeRepository.kt** — repository interface + `InMemoryKnowledgeRepository`
  (Mutex-guarded, staged loads, namespaced KV persistence via injected
  function types).
- **KnowledgeQuery.kt** — typed, bounded, paginated queries.
- **KnowledgePackage.kt** — versioned import/export with validation and dry-run.
- **KnowledgeLifecycle.kt** — transition rules, activation checks, event bus.

## Repository and domain boundaries

Knowledge (descriptive) vs ProtocolRegistry (executable) vs Capability
Discovery (observed) vs Feature Engine (control) vs Persistence Verification
(evidence of retention) vs User Config (preferences) vs Lab (research).
Stable-ID references only; no shared mutable state.

## Data flow

Records enter via import (validated) or direct puts. Queries read through
`KnowledgeQuery`. Changes publish `KnowledgeChangeEvent`s. Persistence is a
JSON snapshot per record behind injected read/write functions.

## Schema design

Normalized entities with stable IDs; cross-references by ID; metadata on
every record (version, lifecycle, timestamps, limitations).

## Protocol and firmware versioning

Protocols carry name+family+version; firmware profiles carry version
constraints; unknown firmware is explicit; compatibility is never assumed.

## Evidence evaluation

Deterministic: verification transitions require the documented evidence;
synthetic can never reach hardware statuses; contradictions preserved.

## Query behavior

Bounded, paginated, deterministically ordered. Ambiguity preserved.
No-match distinguished from incomplete knowledge.

## Migration strategy

Schema version metadata; explicit migrations; unsupported future versions
rejected; corrupt records reported, never silently dropped.

## Import/export

Versioned packages; full validation before application; dry-run;
all-or-nothing import; deterministic export; provenance preserved.

## Security

Untrusted input until validated; size/nesting limits; no eval; no
executable content; knowledge never authorizes writes.

## Concurrency

Mutex-guarded repository; staged loads; readers never see partial commits.

## Extensibility

New entity kinds: add to codecs + repository + package. New queries: add
to KnowledgeQuery. New lifecycle states: extend transition table.
