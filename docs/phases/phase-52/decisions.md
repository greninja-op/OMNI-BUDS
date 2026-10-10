# Phase 52 — Architectural Decision Records (ADRs)

## ADR-52-01: Honest Classification of Hardware Gates Without Physical Environment

### Context
Phase 52 is the terminal phase authorizing production release. OmniBuds specifies physical hardware testing protocols (Phase 38 HIL). However, the container environment contains no physical Bluetooth controllers, headphones, or mobile phones. Earlier phases established deterministic simulated test suites.

### Decision
We strictly enforce the **Fundamental Principle of Hardware Truth**: simulated success must NEVER be conflated with hardware verification.
All hardware gates are explicitly evaluated as `NOT VERIFIED (BLOCKED_NO_ENVIRONMENT)`. Under no circumstances will test fixtures or mocks be claimed as physical evidence.

### Consequences
- Positives: Zero false claims of hardware compliance. Prevents deceptive release declarations.
- Negatives: Requires the release recommendation to be conditional (`CONDITIONAL GO`), awaiting real-world HIL lab execution.

---

## ADR-52-02: Handling of Known Phase 51 Workflow Permission Boundary

### Context
In Phase 51, `.github/workflows/release-verification.yml` was generated locally, but pushing via the GitHub API was blocked with HTTP 403 because the GitHub App lacked the `workflow` permission scope.

### Decision
We preserve the untracked `.github/workflows/release-verification.yml` file in the working tree and document the permission boundary as an environment/security demarcation. We explicitly refuse to perform insecure workarounds (such as embedding executable CI configurations into documentation directories). Pushing and workflow permissions are managed via external CI administration.

### Consequences
- Positives: Maintains security integrity of CI/CD configuration.
- Negatives: Untracked `.github/` directory remains in local working tree.

---

## ADR-52-03: Preservation of Executable Bit Modification on `scripts/release_build.sh`

### Context
`git status` reports `scripts/release_build.sh` as modified. Inspection of `git diff` shows the only change is file mode `100644` -> `100755` (adding execute permissions).

### Decision
Retain the executable bit on `scripts/release_build.sh`. This is an unambiguous improvement that permits direct script execution in POSIX shells without requiring `bash scripts/...`.

### Consequences
- Positives: Improved developer and CI ergonomics.
- Negatives: None.

---

## ADR-52-04: Release Recommendation Formulation (Conditional Go for RC1)

### Context
Release management requires an unequivocal outcome: GO, CONDITIONAL GO, or NO-GO.
All 2,056 automated software tests pass (100%). All 5 distribution artifacts are verified with SHA-256 digests. Security, privacy, and redaction policies pass. However, physical hardware verification is missing due to the lack of an authorized physical environment, and private signing keys are absent (`BLOCKED_NO_KEYS`).

### Decision
Issue a **CONDITIONAL GO** verdict:
1. The codebase, architecture, and artifacts are verified and approved as **Software Release Candidate 1 (RC1)**.
2. Production store publishing and physical device support claims remain conditioned on real-device HIL verification and production key provisioning by authorized human release managers.

### Consequences
- Prevents premature public store publication.
- Provides fully verified, reproducible binaries for hardware lab testing.
