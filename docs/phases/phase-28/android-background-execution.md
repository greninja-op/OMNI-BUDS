# Phase 28 — Android Background Execution

## Mechanisms used

- `Application.ActivityLifecycleCallbacks` — foreground/background.
- `ComponentCallbacks2.onTrimMemory` — background backstop.
- No WorkManager, no JobScheduler, no foreground service in this phase.

## API-level rules

| API | Notes |
|---|---|
| 26–30 | Background execution limits; no implicit broadcasts for most actions |
| 31+ | Foreground-service launch restrictions; exact-alarm restrictions |
| 33+ | Runtime notification permission affects foreground-service notifications |
| 34–35 | Foreground-service types enforced; `dataSync` etc. require declaration |

## Restrictions honored

- No polling, no permanent service, no repeated service starts.
- Background-restricted apps defer work.
- Bluetooth callbacks used where the platform delivers them; no
  assumption of a complete durable record.

## Service eligibility

No foreground service is created. See service-eligibility.md.
