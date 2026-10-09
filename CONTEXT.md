# CONTEXT.md — Agent Continuity File

> **AGENT: READ THIS FIRST, EVERY SESSION. UPDATE AFTER EVERY PROMPT.**
>
> This file is your memory. If you lose context, read this and you'll know:
> who the user is, what we're building, what's done, what the rules are,
> and what to do next. Keep it current — a stale CONTEXT.md is worse than none.

---

## 1. Who the user is

- **GitHub:** greninja-op (profile name Arjun Sabu, India)
- **Timezone:** Asia/Kolkata (IST)
- **Working style:** Casual and direct ("bro"), short messages, zero tolerance for hedging or invented numbers. Professional standard: trust earned through honest limitation reports and owning mistakes.
- **Language:** Follow his language turn by turn (recently all English).

## 2. What we're building

**OmniBuds** (`greninja-op/OMNI-BUDS`) — a universal Bluetooth earbuds/headphones hardware-control app. Backend-first: capability and state model before any UI. Kotlin, `:core` (platform-independent) + `:platform:android` (Android Bluetooth). Phases 0–52 planned.

**Phases 0–19 are complete.** Phase 20 has not started and must not begin without explicit user instruction.

## 3. Quota guardrail (STANDING — overrides all speed directives)

- **Free weekly quota:** check with `subscription-status status` before spawning each phase agent.
- **HARD STOP at 98%:** stop ALL work immediately — no new phases, no agents, no pushes. Notify the user that the weekly quota is exhausted.
- **1B additional-token pool:** NEVER touch without the user's explicit approval in chat. It is currently 0% used.
- **Last checked:** 2026-10-09 ~13:30 IST — 42% free used, resets Oct 15 9:36 PM IST, 1B pool untouched.
- **FULL SPEED** applies *within* the free quota only.

## 4. Standing preferences

1. **Push directly to `main`.** No PR, no separate branch. Ever.
2. **One batched `push_files` call per phase** → one approval prompt. (Shell arg-size limit may force splits for very large phases.)
3. **Periodic progress updates** during long builds, not just completion reports.
4. **Phase reports** go in the build thread after each phase.
5. **Stop at phase boundaries.** Never start the next phase without explicit authorization.

## 5. Standing constraints (every phase)

- No UI. No media-audio capture/decode/intercept/re-encode. No `RECORD_AUDIO`.
- No hidden APIs, reflection, shell commands, root, or system-file changes.
- No guessed vendor commands (protocol registry ships empty — correct).
- No physical-device testing (deferred by user directive).
- Never fabricate capabilities or state; UNKNOWN stays UNKNOWN, never becomes UNSUPPORTED.
- Never claim a push landed until remote files/commits are read back and verified.
- Never claim Gradle/Lint success unless those commands actually ran (Gradle daemon broken in sandbox; use manual kotlinc toolchain).

## 6. Anti-stale rule (user directive)

**Always fetch the REMOTE CONTEXT.md fresh before updating.** Merge into the newest remote version — never overwrite a newer remote with a stale local copy. Verify by readback after push. An agent that skips the CONTEXT.md update counts as incomplete.

## 7. Key technical facts

- **Toolchain:** kotlinc 2.0.21, JVM 17, `-Werror`, JUnit Platform Console 1.10.1.
- **GitHub writes:** `github` CLI (App API `push_files`), never SSH or `git push`. Never recommend SSH keys.
- **Local git history is stale/divergent** (API pushes bypass it). Build explicit file lists per push; never push local history blindly.
- **Architecture layers:** common(0), device(2), config(2), capability(2), persistence(3), protocol(4), then feature/battery/processing/configuration/validation/verification/vendor at 5. `DependencyDirectionTest` enforces downward-only.
- **Core forbids JVM-only imports** (`java.io` etc.) — file I/O lives in `:platform:android`.
- **`ConfigurationValueJson`** moved to `com.omnibuds.core.config` (Phase 18); old path is a deprecated typealias shim.
- **Logo:** `https://github.com/user-attachments/assets/a1a3b59f-ad00-43a8-ab1d-73066f74a4d1` — preserve the README `<img>` tag verbatim.
- **No emulator in sandbox** — on-device validation deferred to real hardware.

## 8. Phase history (condensed)

| Phase | What was built | Tests | Commit(s) |
|---|---|---|---|
| 0–6 | Foundation, device identity, transport, sessions | — | various |
| 7 | Protocol abstraction engine (registry ships empty) | — | various |
| 8 | Capability discovery engine | — | various |
| 9 | Hardware feature engine (6-state control) | — | various |
| 10 | Audio transport engine (observation-only) | 890 | 5 commits |
| 11 | Codec capability engine (honest NOT_OBSERVABLE) | 944 | 5 commits |
| 12 | Codec control architecture (honest NOT_SELECTABLE) | 1011 | 6 commits |
| 13 | Audio quality & negotiation state engine | 1065 | 6 commits |
| 14 | Audio path validation engine | 1116 | 5 commits |
| 15 | Hardware DSP / audio separation | 1126 | `ed37ab3` |
| 16 | Battery & power state engine | 1157 | `13ebb58` |
| 17 | Persistent configuration engine (saved ≠ applied) | 1189 | `9d99a4b` |
| 18 | Persistence verification framework (evidence-proven scopes) | 1234 | 2 commits |
| 19 | Vendor adapter infrastructure (no protocol — blocked, honest) | 1242 | `80320e5` |

**Current:** 1242 tests (1100 core + 142 android), 0 failures.

**Key honest findings to preserve:**
- No public Android API exposes the active codec → `NOT_OBSERVABLE`.
- No public Android API exposes codec control → `NOT_SELECTABLE`/`NOT_CONFIGURABLE`.
- No public API exposes Bluetooth battery → adapter reports unsupported.
- No vendor protocol has sufficient evidence → registry stays empty (correct).

## 9. Gotchas (things that bit us)

1. **`push_files` arg-size limit:** large phases must split into 2+ calls (code + docs).
2. **MCP timeouts on push:** if a push times out, verify on remote before retrying — it may have landed.
3. **Sideways layer imports** (5→5) fail `DependencyDirectionTest` — check before writing.
4. **`ProtocolDefinition` rejects `TransportKind.UNKNOWN`** — use a real transport.
5. **Deprecated typealias + `-Werror`:** update old imports, don't use the shim.
6. **Scope-test false positives:** banned-term lists must not match legitimate code (e.g. `Json.decode`).

## 10. Conversation log (2026-10-09)

- Phase 11→12: codec capability → control architecture. User asked about token usage (18% free used).
- User asked to reduce approval prompts → single-batch pushes adopted.
- Phase 13→14→15→16→17→18→19 executed sequentially, each pushed to `main` with remote verification.
- **Quota guardrail set (~13:35 IST):** stop at 98% free, never touch 1B pool without approval. Overrides the old "burn through everything" wording.
- **CONTEXT.md redesign requested (~13:37 IST):** this file rewritten as the canonical agent continuity file.

---

*Last updated: 2026-10-09 (Phase 21 pushed; Phase 22 not started; quota at 53%).*
