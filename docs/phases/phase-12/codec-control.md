# Phase 12 — Codec Control Guide

**Status:** Informative. How to use the codec control architecture.

---

## The one rule

> Never report requested state as confirmed state.

`CodecControlState.confirmedCodec` is the only field that may be presented as
"the codec in use". It advances **only** on `CodecOperationResult.Verified`.

## Selecting a codec

```kotlin
val op = CodecOperation.select(
    device = identity,
    codec = Codec.LDAC,
    operationId = "ui-123",           // caller-supplied, for correlation
    verificationStrategy = CodecVerificationStrategy.PLATFORM_OBSERVATION,
)
when (val result = engine.execute(op)) {
    is CodecOperationResult.Verified -> show(result.codec)        // confirmed
    is CodecOperationResult.NotSelectable -> explainLimitation()  // honest
    is CodecOperationResult.VerificationFailed ->
        show("still ${result.observedCodec}")                     // never LDAC
    ...
}
```

## Configuring a codec

```kotlin
val config = CodecConfiguration(
    codec = Codec.LDAC,
    qualityMode = QualityMode.BALANCED,   // the only field LDAC exposes
)
val op = CodecOperation.configure(device, config, "ui-124", ...)
```

Field support is validated before any mechanism runs: setting `bitrate` on
LDAC is `Rejected` (the field is not exposed), not silently dropped.

## Reading state

```kotlin
engine.states.value[device]?.let { state ->
    // Display ONLY these as current, and only when fresh:
    if (state.isCurrent) show(state.confirmedCodec)
    // These are diagnostics, never display copy:
    state.requestedCodec
    state.observedCodec
}
```

## What each codec can do (public Android APIs)

| Codec | Select | Configure | Verify |
|---|---|---|---|
| SBC, AAC, aptX ×4, LDAC, LC3 | No | No | No |

This is the honest platform reality, documented in `platform-limitations.md`.
The architecture is ready the day a legitimate mechanism appears: implement
`CodecControlAdapter` + `CodecControlCapabilityResolver` for it; the engine
does not change.
