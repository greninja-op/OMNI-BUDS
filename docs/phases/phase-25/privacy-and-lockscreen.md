# Phase 25 — Privacy and Lock Screen

## Lock-screen behavior

Tile labels are generic ("OmniBuds") with status subtitles
("Connected", "No device"). No device names, addresses, battery levels
of other devices, or feature values beyond the connected device's
basic status.

## Intent security

- No PendingIntents carry device selectors; target resolution happens
  in-process at click time.
- No untrusted extras; the service exposes no receivers.
- `TileDependencies` is process-scoped; no exported components beyond
  the tile service itself.

## Authorization

Every hardware action re-checks the centralized access policy at
dispatch. A tile click is never an authorization bypass.

## Logging

No credentials, raw protocol payloads, or private identifiers in tile
labels, subtitles, or logs. Diagnostics use typed reason codes.
