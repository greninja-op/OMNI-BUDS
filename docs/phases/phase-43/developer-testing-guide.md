# Phase 43 — Developer Testing Guide

---

## 1. Overview

The Community Protocol SDK provides developer tooling to build and test protocol adapters completely offline, without physical Bluetooth hardware.

---

## 2. Testing Tools

### 2.1 `ScriptedFakeTransport`
A programmable fake implementing `TransportContract`:
```kotlin
val transport = ScriptedFakeTransport()
transport.script(
    SdkFakeStep.Respond(byteArrayOf(0x01, 0x50)),
    SdkFakeStep.Fail(OmniBudsErrorCategory.TIMEOUT, "Simulated timeout"),
    SdkFakeStep.Disconnect
)

transport.open()
val response = transport.exchange(request, 1000L)
```

### 2.2 `CommunityConformanceRunner`
Executes automated checks ensuring adapter conformance:
```kotlin
val summary = CommunityConformanceRunner.verifyAdapter(adapter, sampleFingerprint)
assertTrue(summary.passed)
```

### 2.3 `CommunityPackageValidator`
Statically inspects your metadata package for syntax, version, and dependency validity:
```kotlin
val report = CommunityPackageValidator.validate(pkg)
assertTrue(report.isValid)
```
