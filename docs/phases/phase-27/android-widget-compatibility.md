# Phase 27 — Android Widget Compatibility

## SDK configuration

minSdk 26, targetSdk 35, compileSdk 35.

## Compatibility notes

| API | Notes |
|---|---|
| 26–30 | AppWidgetProvider fully supported; `updateAppWidget` works |
| 31+ | Widget description/preview honored; no behavior change |
| 35 | No new widget APIs required |

## RemoteViews constraints

Only framework views used (LinearLayout, TextView, Button). No custom
views. Content descriptions set for accessibility.

## Launcher differences

Launchers may ignore resize hints or preview images. Layouts are
self-contained and readable at minimum size. No launcher-specific hacks.

## updatePeriodMillis = 0

No platform polling. Refreshes are app-initiated from engine state while
the process is alive. Documented limitation: no real-time guarantee.
