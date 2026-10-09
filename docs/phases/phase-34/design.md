# Phase 34 — Design

## Modules

`core/recovery/`:

- `FailureClassification.kt` — categories, metadata, classifier.
- `RecoveryPolicy.kt` — deterministic decisions.
- `RecoveryStateMachine.kt` — explicit lifecycle.
- `RecoveryEvents.kt` — bounded diagnostic sink.

## Reuse

`RetryClass` (Phase 0), `ReconnectPolicy` (Phase 28),
`FailureInjector`/`ScriptedTransport` (Phase 30). No competing
state machines or retry loops.

## Data flow

Error → classify → ClassifiedFailure → RecoveryPolicy.decide →
RecoveryDecision → state machine transition → event recorded.

## Concurrency

Classifier and policy are pure objects. The state machine is
single-owner (per device). The event sink is synchronized and bounded.

## Security boundaries

- No payload/secret/credential logging.
- Authorization revalidated before any retry.
- Ambiguous writes never replayed.
