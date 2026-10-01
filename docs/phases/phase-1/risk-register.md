# Phase 1 — Risk Register

Numbering continues the single project-wide register started in Phase 0 (`RISK-001` … `RISK-015`); Phase 1 does not restart the sequence, because a risk register that resets loses history. Scale as in Phase 0: `low | medium | high`, exposure = probability × impact.

All entries below describe risks of the **foundation itself**. Phase 1 touched no hardware, so the hardware-facing risks in Phase 0 (RISK-001 … RISK-014) are unchanged and are not re-scored here; where a Phase 1 decision makes one of them worse or better, that is stated.

---

### RISK-016 — Toolchain reproducibility beyond this workstation
**Probability.** medium · **Impact.** medium · **Exposure.** medium
**Description.** Gradle 8.9, AGP 8.7.3 and Kotlin 2.0.21 were chosen from what this machine already had cached, and the JDK sits at a user-level path that is not on `PATH`. Another machine, a clean CI image, or a Rider/Android Studio upgrade that evicts cache entries may not resolve identically.
**Mitigation.** Versions pinned in `gradle/libs.versions.toml`; wrapper committed; no machine-specific path in any committed file; `repository-analysis.md` records exactly what was found and why it was chosen; the user-level Gradle configuration was deliberately left untouched so other projects on this machine keep working (ADR-P1-014).
**Detection.** A clean-checkout `./gradlew build` on a second machine or in CI fails to resolve, or resolves different versions.
**Fallback.** Move to a project-local toolchain provisioning mechanism, recorded as an ADR, rather than editing the workstation install.
**Owner.** Build/Release (Phase 51).

### RISK-017 — Architecture rules are enforced by source scanning, not bytecode
**Probability.** medium · **Impact.** high · **Exposure.** high
**Description.** `DependencyDirectionTest` reads source text: it can see imports, identifiers in code lines, and file layout, but not reflective access, not a transitive JVM-only dependency pulled in by a library, and not a JVM API reached through an alias. A later phase could reintroduce a platform coupling that the scan cannot see while the build still passes.
**Mitigation.** Rules expressed as imports *and* identifier patterns; the scan fails loudly when the source root is missing rather than passing vacuously; `:core` has zero production dependencies (ADR-P1-021), which shrinks the transitive surface to nothing.
**Detection.** Phase 46 KMP conversion attempt; a dependency added to `:core` in review; a future bytecode-level check.
**Fallback.** Adopt a bytecode- or AST-based architecture tool as an ADR-gated dependency, accepting the resolution cost from RISK-016.
**Owner.** Architecture Agent.

### RISK-018 — Contracts ossify before any hardware evidence exists
**Probability.** high · **Impact.** medium · **Exposure.** high
**Description.** This phase designed domain shapes without ever seeing a real device respond. A shape that looks reasonable now — the capability metadata set, `ConfigurationValue` as the generic feature payload, the codec ladder — may not fit a specific vendor's reality, and later phases tend to preserve an existing type rather than break it.
**Mitigation.** Deliberate non-modelling: ANC/EQ/gesture semantics are not invented (ADR-P1-007); feature writes travel as `ConfigurationValue` rather than as fabricated typed modes; `UNKNOWN` is the default for anything absent; `ProtocolRegistry` ships empty and is test-asserted empty.
**Detection.** The first vendor device in Phase 19/20 that cannot express a real response through the existing shapes; that finding must become an ADR, not a silent widening.
**Fallback.** Amend the contract under an ADR with the device evidence attached. The layer map keeps the blast radius to `capability`, `audio` or `protocol` rather than the whole core.
**Owner.** Protocol Research Agent (Phase 20 onward).

### RISK-019 — The Android boundary is configured but unproven
**Probability.** medium · **Impact.** medium · **Exposure.** medium
**Description.** `:platform:android` compiles as an empty library. The first real Android code in Phase 2 will exercise the parts that no empty module touches: manifest merging, permission lint, D8/R8, `compileSdk` API exposure, and the still-provisional `minSdk` 26 (ADR-P1-015).
**Mitigation.** The AGP/SDK combination is known-good on this machine (platform 35, build-tools 35.0.0, licenses accepted); the boundary module exists now precisely so the wiring is tested before Bluetooth code arrives; `minSdk` is flagged for re-decision rather than quietly inherited.
**Detection.** Phase 2's first platform class failing to compile, or a lint/manifest error surfacing only when permissions are declared.
**Fallback.** Raise `minSdk` or adjust the AGP configuration in Phase 2 with a recorded reason; do not weaken the domain boundary to route around it.
**Owner.** Bluetooth Agent (Phase 2).

### RISK-020 — No CI, so "verified" means verified once, here
**Probability.** high · **Impact.** medium · **Exposure.** high
**Description.** The build and 302 tests were executed on this workstation. With no CI and no git remote, nothing re-runs them on a later change, and Phase 0's RISK-015 remains only partly closed by initialising the repository.
**Mitigation.** Every Phase 1 claim in `validation.md` is tied to a command that was actually run; the definition of done requires a green `./gradlew build`; commit messages state measured results rather than intentions.
**Detection.** Any regression caught only by manual local runs; a contributor pushing without executing the suite.
**Fallback.** CI in Phase 2 (minimum: build plus `:core:test`) is the intended closure; until then, treat local execution as mandatory before each commit.
**Owner.** Orchestrator / Testing Agent.

### RISK-021 — A test double copied into production becomes a fabricated capability
**Probability.** low · **Impact.** high · **Exposure.** medium
**Description.** The doubles in `core/src/test/.../testing/` return scripted successes. If a later phase lifts one into `src/main` — or writes an equivalent "temporary" implementation — the app would report hardware behavior that never happened, which is the single failure mode this project exists to avoid.
**Mitigation.** Doubles live only in test source, so a production module cannot see them at compile time; `DependencyDirectionTest.productionSourcesDefineNoTestDoubles` and `noProductionClassImplementsTheProtocolOrRepositoryContracts` fail the build if a `Fake*`/`Stub*` type or a production implementation of a protocol, transport or repository contract appears.
**Detection.** Those two checks; code review for any class returning `OperationOutcome.Success` without a transport behind it.
**Fallback.** Delete the impostor implementation and mark the affected capability `UNKNOWN` until real evidence exists.
**Owner.** Testing Agent / Security Agent.

### RISK-022 — `FeatureId` strings become load-bearing before they are stable
**Probability.** medium · **Impact.** high · **Exposure.** high
**Description.** Feature identities are the join key between the capability engine, protocol records and (later) persisted configuration. Renaming `noise-control.anc` after Phase 22 has stored records against it silently orphans knowledge.
**Mitigation.** Grammar validated by `FeatureId` and pinned by tests; a rename is documented as a breaking change requiring an ADR; `parseOrNull` refuses text that does not match the grammar rather than guessing an identity.
**Detection.** A persisted or protocol-recorded id that no longer resolves to a core feature; tests around `CoreFeature` ids.
**Fallback.** Keep the old id as an accepted alias with recorded provenance rather than rewriting stored data.
**Owner.** Core Agent / Protocol Research Agent.

### RISK-023 — Documentation drifts behind code inside a single phase
**Probability.** high · **Impact.** low · **Exposure.** medium
**Description.** Phase 1's review documents were written against the pre-correction code, so they described a duplicated connection state, a kernel defect that had been fixed, and a dependency that had been removed. Left unreconciled, the phase record would have documented a build that no longer exists.
**Mitigation.** Two reconciliation passes re-checked the documents against the corrected tree; `validation.md` states which numbers were measured and when; ADRs are the durable record while prose is treated as a snapshot.
**Detection.** A cited symbol, test name or count that does not exist in the repository.
**Fallback.** Re-verify by reading files rather than accepting an agent's summary; correct rather than delete.
**Owner.** Orchestrator / Documentation Agent.

### RISK-024 — The shared Gradle cache on this machine is a dependency this project does not control
**Probability.** medium · **Impact.** low · **Exposure.** medium
**Description.** The build resolves through `~/.gradle/caches`, which is shared with the user's other Android projects and the Rider installation. Cache eviction, a JDK update, or an SDK component change made for another project can break this build for reasons that have nothing to do with this repository's contents. The user has explicitly required that no toolchain be modified for that reason.
**Mitigation.** Nothing outside the project directory is written except additive cache entries; no global git or Gradle configuration was touched; `repository-analysis.md` records the discovered toolchain so a broken build can be diagnosed rather than guessed at.
**Detection.** A build failure whose diff-relative cause is empty, resolved by re-running on a clean checkout elsewhere.
**Fallback.** Pin a project-local toolchain in an ADR — never by editing the existing shared install.
**Owner.** Orchestrator / Build owner.

### RISK-025 — Per-endpoint codec truth is not yet modelled
**Probability.** high · **Impact.** medium · **Exposure.** high
**Description.** A codec may be supported by the phone and not by the headset, or vice versa. Phase 1 holds one record per codec, so the honest sentence "your phone supports LDAC, this headset does not" is not expressible (ADR-P1-018).
**Mitigation.** The gap is recorded as an open ADR rather than closed by a nullable field that would read as `UNKNOWN` and hide the real reason; the ordinal ladder at least encodes the weakest link correctly.
**Detection.** Phase 11 requirements work, or the first UI that needs to explain a codec refusal.
**Fallback.** Add an explicit endpoint discriminator in Phase 11 with tests that forbid collapsing the two records into one.
**Owner.** Audio Agent (Phase 11).

---

## Register summary

| Risk | Exposure | Owner phase |
|---|---|---|
| RISK-016, 020, 024 | medium/high | 2 (CI), 51 (release engineering) |
| RISK-017, 023 | high / medium | 46 (KMP), every phase for review discipline |
| RISK-018, 022 | high | 19, 20, 22 |
| RISK-019 | medium | 2 |
| RISK-021 | medium | every phase |
| RISK-025 | high | 11 |

Phase 1 closes none of these permanently. Two of them (RISK-018, RISK-021) are the risks this phase most directly creates by existing: contracts invite later phases to assume they are right, and doubles invite them to be reused. Both are guarded mechanically rather than by intention.
