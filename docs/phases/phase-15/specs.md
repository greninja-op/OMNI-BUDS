# Phase 15 — Specifications

## AudioProcessingDomain

DEVICE_HARDWARE_DSP, DEVICE_FIRMWARE, ANDROID_PLATFORM, APPLICATION_LOGIC,
UNKNOWN (default).

## Resolver rules

1. verification < IMPLEMENTED → UNKNOWN.
2. protocolId == null → ambiguous UNKNOWN (read-only).
3. Android capabilities → ANDROID_PLATFORM.
4. Codec/routing/transport ≠ DSP.
5. Verified protocol + IMPLEMENTED+ → DEVICE_HARDWARE_DSP.

## Control eligibility

Allowed only when: writable + domain known + requested == resolved.
Otherwise Denied with reason.

## ProcessingOwnership

Feature, device, owner/observableBy/changeableBy/confirmedBy domains,
persistent flag, PersistenceOwner (DEVICE_FIRMWARE / ANDROID_PLATFORM /
APP_PREFERENCE / SESSION_ONLY).
