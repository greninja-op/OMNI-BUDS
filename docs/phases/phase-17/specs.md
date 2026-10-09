# Phase 17 — Specifications

## DeviceConfigurationKey
Value class wrapping fingerprint-derived identityKey; blank refused.

## DeviceConfiguration
key, schemaVersion, preferences: Map<String, ConfigurationValue>,
updatedAtMillis. `empty()` factory.

## GlobalConfiguration
schemaVersion, preferences, updatedAtMillis. `empty()` factory.

## Results
DeviceConfigurationResult: Found / NotFound / Invalid / NeedsMigration /
ReadFailed. ConfigurationWriteResult: Saved / ValidationFailed /
WriteFailed.

## ConfigurationStorage
read/write/delete; write returns true only when committed.

## JSON format
Value: `{"t":"<type>","v":<value>}`. Config envelope:
`{"v":<version>,"u":<timestamp>,"p":{<key>:<value>,...}}`.

## Validator
Max key 128 chars; max string 4096; max 256 prefs; schema bounds.

## Migrations
Contiguous chain from MIN_SUPPORTED_VERSION; future → MigrationException.

## Engine
Per-device Mutex; global Mutex; StateFlows with dedup; migrate function.

## Eligibility
Eligible / NotEligible(reason). Checks: feature known, capability
supported, writable, value plausible.
