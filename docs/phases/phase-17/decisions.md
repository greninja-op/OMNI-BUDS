# Phase 17 — Decisions

## D-17-01: pure-Kotlin JSON, no framework
**Decision:** Hand-written deterministic JSON codec; no kotlinx.serialization.
**Rationale:** Avoids Gradle plugin complexity; format fully controlled;
core stays dependency-free.

## D-17-02: file storage in Android module
**Decision:** `FileConfigurationStorage` lives in
`com.omnibuds.android.bluetooth.storage`; core has `ConfigurationStorage`
interface + in-memory impl only.
**Rationale:** Core forbids JVM-only libraries (architecture test).

## D-17-03: explicit result types
**Decision:** Sealed result interfaces instead of nullable returns.
**Rationale:** NotFound/Invalid/NeedsMigration are different facts.

## D-17-04: future schemas throw
**Decision:** `MigrationException` on newer-than-supported versions; never
overwrite.
**Rationale:** Silent data loss is worse than explicit failure.

## D-17-05: per-device mutexes
**Decision:** Independent `Mutex` per device key; separate global lock.
**Rationale:** Devices never block each other; no global lock bottleneck.
