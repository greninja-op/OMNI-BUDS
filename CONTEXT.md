# CONTEXT.md — Living Conversation Context

> **RULE FOR THE AGENT — READ THIS FIRST, EVERY SESSION.**
>
> This file is the persistent memory of the working conversation. It exists so
> that if the user switches accounts, devices, or chats, the full context is
> preserved and work can continue without loss.
>
> 1. **Read this file at the start of every session** before doing any work.
> 2. **Update this file after every significant prompt** — new decisions, new
>    preferences, phase completions, commit SHAs, test counts, and anything
>    the next session would need to know.
> 3. **Never delete history** — append and update; keep the log chronological.
> 4. The latest entry at the bottom is the current state of the world.

---

## Project overview

**OmniBuds** (`greninja-op/OMNI-BUDS`) — a universal Bluetooth earbuds/headphones
hardware-control app. Backend-first: the capability and state model is finished
before any screen exists. Kotlin, `:core` (platform-independent) +
`:platform:android` (Android Bluetooth).

**Phases 0–12 are complete.** Phase 13 (Audio Quality & Negotiation State) has
**not** started and must not begin without an explicit user instruction.

## Current state (2026-10-09)

- **Phase 12** (Codec Support & Configuration Architecture) complete, pushed to
  `main` in 6 commits: `5eb3a3c`, `1597b05`, `2ddad61`, `108614b`, `19d0f6f`,
  `e70f2bd`.
- **Tests:** 1011 passing (877 core + 134 Android), 0 failures.
- **Key honest finding:** no public Android API exposes codec *control*
  (selection/configuration); the adapter correctly reports
  `NOT_SELECTABLE`/`NOT_CONFIGURABLE`. No fake control, no hidden APIs.
- **Security review:** PASS, no findings; hardening applied (bounded
  observation timeouts).

## Standing user preferences

1. **Push directly to `main`.** No PR, no separate branch. Ever.
2. **Single-batch pushes.** One `push_files` call per phase → one approval
   prompt, not five. (Set 2026-10-09 after the user complained about repeated
   approval prompts.)
3. **Periodic progress updates** during long-running build phases, not just a
   completion report at the end. (Set 2026-10-09.)
4. **Run at FULL SPEED.** No token conservation/throttling, even if it burns
   the weekly allowance and the 1B extra-token pool. (Set 2026-10-09.)
5. **Phase reports** go in the build thread after each phase.

## Standing constraints (every phase)

- No UI. No media-audio interception/decoding/re-encoding. No `RECORD_AUDIO`.
- No hidden APIs, reflection, shell commands, root, or system-file changes.
- No guessed vendor commands (protocol registry ships empty — correct).
- No physical-device testing (deferred by user directive).
- Never fabricate codec support or runtime state; preserve UNKNOWN and
  NOT_OBSERVABLE where the platform lacks evidence.
- Do not claim Gradle/Lint success unless those commands actually ran
  (Gradle daemon is broken in this sandbox; use the manual kotlinc toolchain).

## Key technical facts

- Toolchain: kotlinc 2.0.21, JVM 17, `-Werror`, JUnit Platform Console 1.10.1.
- `SideEffectClass` lives in `com.omnibuds.core.common` (moved from `feature`
  in Phase 12 — layer 3 `codec` cannot depend on layer 5 `feature`).
- GitHub writes use the `github` CLI (App API), never SSH/`git push`.
- Local git history is stale (phases pushed via API); never push local history
  blindly — build explicit file lists per push.
- Logo: `https://github.com/user-attachments/assets/a1a3b59f-ad00-43a8-ab1d-73066f74a4d1`
  (local copy `~/workspace/user/files/omnibuds-logo.png`); the README `<img>`
  tag must be preserved verbatim.

## Conversation log

- **2026-10-09 — Phase 11 completed** (Codec Capability Engine): 944 tests,
  5 commits (`f6d0d8a`…`cbedb2a`). Extended Phase 1 codec vocabulary; honest
  NOT_OBSERVABLE for active codec.
- **2026-10-09 — Phase 12 authorized and executed** (this chat): full codec
  control architecture (operations, transactions, verification, rollback,
  per-device serialization). Agents confirmed: no public Android control API,
  no verified vendor protocol. 1011 tests, security PASS, pushed in 6 commits.
- **2026-10-09 — User asked about token usage:** 18% of free weekly limit used
  (resets Oct 15, 9:36 PM IST); 1B extra-token pool untouched. Muse reports
  percentages, not exact token counts; no per-project breakdown exists.
- **2026-10-09 — User asked to reduce approval prompts:** single-batch pushes
  adopted; standing "always allow" must be set by the user in the app's
  permissions settings (agent cannot grant it).
- **2026-10-09 — This file created** per user request: living context,
  README-linked, updated after every prompt.
- **2026-10-09 — Phase 13 authorized and started** (Audio Quality & Negotiation
  State Engine): unified `AudioQualityState`, negotiation state machine,
  sessions, events, resolver with source precedence, conflict resolution.
  Observation/normalization/state/analytics only — no media interception.
- **2026-10-09 — Phase 13 implementation complete** (pending push): 10 new
  core types in `core.quality` (layer 4), `AudioQualityEngine`,
  `AudioQualityResolver`, Android bridge. **1065 tests passing**
  (926 core + 139 android), 0 failures. Docs: 8 records + architecture +
  negotiation-model. Phase 14 NOT started.
- **2026-10-09 — Phase 13 pushed to main** in 6 commits (batch 1: `9efee761`).
  All 31 files verified on remote `main`.
- **2026-10-09 — Phase 14 authorized and started** (Audio Path Validation):
  validation engine answering whether observed transport/device/route/codec/
  state is internally consistent and evidence-backed. No signal-path claims.
- **2026-10-09 — Phase 14 implementation complete** (pending push): 9 new
  core types + 10 rules in `core.validation` (layer 5),
  `AudioPathValidationEngine` with session generations. **1116 tests passing**
  (977 core + 139 android), 0 failures. Docs: 8 records. Phase 15 NOT started.

---

*Last updated: 2026-10-09 (Phase 14 complete, pending push).*
