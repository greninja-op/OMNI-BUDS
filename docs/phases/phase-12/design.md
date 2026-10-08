# Phase 12 — Codec Support & Configuration Architecture: Design

**Status:** Authoritative for Phase 12 implementation.
**Scope:** The codec control architecture: operation model, configuration model,
capability gating, transaction lifecycle, verification, rollback, concurrency.

---

## 1. The honest premise

Phase 11 established that no public Android API exposes codec *observation* of
the active codec. Phase 12's audit (Agents 1, 3, 4) established the stronger
finding:

**No public Android API exposes codec *control* either.** There is no
`setCodecConfig`, no `selectCodec`, no `setCodecPriority` in the public SDK.
The vendor-protocol registry ships empty. Therefore:

- The architecture is complete and production-quality.
- Every control operation on public Android APIs resolves to
  `NOT_SELECTABLE` / `NOT_CONFIGURABLE` with `PLATFORM_LIMITATION` evidence.
- This is the correct behavior, not a stub. A future public API or verified
  vendor protocol arrives as a new `CodecControlAdapter` implementation behind
  the same interface — the engine does not change.

## 2. The five levels (§1)

```
KNOW → OBSERVE → SUPPORT → CONFIGURE → CHANGE
```

Each is a separate modeled fact in `CodecControlCapability`:

| Dimension | Meaning | Android reality |
|---|---|---|
| observable | runtime state visible | partial (Phase 11) |
| supported | device/platform reports it | per Phase 11 |
| selectable | can switch to it | **false** |
| configurable | can tune its parameters | **false** |
| verifiable | can confirm a change | **false** |

`controllable = selectable || configurable`. Read-only observation never counts.

## 3. Operation model (§6)

`CodecOperation` is a value (mirroring `FeatureOperation`):

- `operationId` — caller-supplied, never minted from wall-clock in core
- `device`, `codec`, `type`, `requestedConfiguration?`
- `expectedTransport`, `timeoutMillis?`, `verificationStrategy`
- `sideEffect` — `READ_ONLY_SAFE` only for `REFRESH_STATE`; everything else is
  `SIDE_EFFECTING` and never retried blindly

`init` validates: `CONFIGURE_CODEC` requires a configuration; all others forbid
one; `UNKNOWN` codec rejected; timeout positive.

## 4. Selection vs configuration (§5)

Distinct types, distinct preconditions:

- `SELECT_CODEC` → requires `supported && selectable`
- `CONFIGURE_CODEC` → requires `supported && configurable` + field validation
- `ENABLE/DISABLE_CODEC` → requires `supported && selectable`
- `RESET_CONFIGURATION` → requires `configurable`
- `REFRESH_STATE` → always permitted (read-only)

## 5. Transaction lifecycle (§21)

```
PRECHECK → REQUEST → APPLY → RE-OBSERVE → VERIFY → COMMIT
```

- **PRECHECK**: capability, transport, liveness, field support, allowed values,
  operation support, verification capability. Runs before the device lock;
  failures never touch the adapter.
- **REQUEST**: records `requestedCodec`/`requestedConfiguration`. Never
  presented as reality.
- **APPLY**: `adapter.apply()` under `withTimeout`. Only the engine's own
  timeout becomes `TimedOut`; caller cancellation propagates.
- **RE-OBSERVE**: `adapter.observeAfterApply()`.
- **VERIFY**: observed vs requested per strategy. `NONE` caps at
  `APPLIED_UNVERIFIED`.
- **COMMIT**: advances `confirmedCodec` **only** on `Verified`.

## 6. Requested vs confirmed (§8)

`CodecControlState` keeps four separate fields:

- `requested*` — what was asked for
- `observed*` — what the platform last reported
- `confirmed*` — what verification proved (the only field presentable as "in use")
- `previousConfirmed*` — history for rollback

The canonical honest flow: request LDAC → apply reports success →
re-observation still AAC → `VERIFICATION_FAILED`, confirmed stays AAC.

## 7. Results (§7)

14 structured states in a sealed hierarchy. `isConfirmed` is true only for
`Verified`. `isTerminalRefusal` classifies `UNSUPPORTED`, `NOT_SELECTABLE`,
`NOT_CONFIGURABLE`, `NOT_OBSERVABLE`, `PLATFORM_UNAVAILABLE`. Every result
carries its `operationId`.

## 8. Verification (§28–29)

Strategies: `PLATFORM_OBSERVATION`, `DEVICE_PROTOCOL_READBACK`,
`AUDIO_DEVICE_OBSERVATION`, `COMBINED`, `NONE`. `NONE` never verifies.
Verification compares the observed codec (and supported config fields) against
the request. Levels (`IMPLEMENTED` etc.) are never auto-promoted to
`HARDWARE_VERIFIED`.

## 9. Rollback (§22)

On verification failure, the engine re-asserts the last verified
(`confirmed`) codec through a synthetic select (marked `isRollback` to prevent
recursion, and not overwriting the user's recorded request). If rollback is
impossible or unverified, state is marked stale and a refresh is required.
Rollback never pretends success.

## 10. Concurrency, timeouts, cancellation (§23–26)

- Per-device `Mutex` (mirroring `FeatureEngine`'s per-feature locks);
  independent devices proceed independently.
- `withTimeout` per operation; timeout → `TimedOut`, never success.
- `CancellationException` from the caller propagates; cancelled operations
  never commit.
- Disconnect (precheck or mid-transaction) → `DeviceDisconnected`, state stale.

## 11. Validation (§19–20)

Seven rules, each rejecting before any mechanism is touched:

1. codec capability (supported/selectable/configurable per type)
2. transport (LC3 + CLASSIC_A2DP rejected; LE_AUDIO family check)
3. device connection (liveness gate; unknown devices read-only)
4. field support (`CodecFieldSupport`: only LDAC exposes `QUALITY_MODE`)
5. allowed values (positive numerics; enum-bounded modes)
6. operation support (capability per type)
7. verification capability (non-NONE strategy requires verifiable)

`DEVICE_OBSERVED` constraints override theoretical ones where they differ.

## 12. Configuration model (§18)

`CodecConfiguration`: codec + qualityMode + bitrate + sampleRateHz + bitDepth +
channelMode + adaptiveMode. Every field nullable or explicitly unknown.
`init` rejects non-positive numerics. `CodecConfiguration.empty(codec)` for
"identified, nothing requested".

## 13. Vendor protocol path (§16–17)

The engine talks only to `CodecControlAdapter`. A vendor-protocol adapter
would sit behind the Protocol Abstraction Engine:

```
CodecControlEngine → CodecControlAdapter → Protocol Engine → Vendor Protocol
```

No guessed commands exist. The registry is empty, so this path is unavailable
and the architecture correctly reports `NOT_CONFIGURABLE`.

## 14. What was NOT built

Per the phase boundary: no UI, no media interception, no decode/re-encode, no
shell/root, no hidden APIs, no guessed vendor commands, no fake persistence,
no physical-device testing, no Phase 13.
