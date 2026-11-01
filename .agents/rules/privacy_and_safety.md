# Standing Rule: Host File System & Mobile Device Privacy Guardrails

## 1. Host File System Boundary (Strict Local Project Isolation)
- **Zero Access Outside Workspace**: Under NO circumstances should any tool read, modify, execute, or delete files outside the OmniBuds workspace directory: `c:\my files in athuls lap\my files in athuls lap\projects\omni buds`.
- **Preserve Shared Toolchain Caches**: Never run global cleanup commands, delete `.gradle` caches in the user's home directory, or delete files belonging to other projects sharing the user's Gradle/SDK environment.

## 2. Connected Mobile Device Privacy Boundary
- **Package-Bound Interactions Only**: All ADB operations, intents, and process queries must target ONLY OmniBuds packages (`com.omnibuds`, `com.omnibuds.android`, `com.omnibuds.tools.companionshell`).
- **No Private Data Access**:
  - Never query `content://` providers, contacts, messages, call logs, media, or accounts.
  - Never list, read, or pull from `/sdcard/`, `/storage/`, `/data/system/`, or any other application's private data directories.
  - Never access settings or toggle global device options.
- **Fail-Closed Foreground Gate**: Screen captures and UI hierarchy inspections are permitted ONLY when an OmniBuds activity/window is confirmed to be in the foreground (`mCurrentFocus`). If any other application or OS settings screen is foreground, capture must immediately abort and fail closed.
- **In-Memory Frame Handling**: Captures and screenshots must be handled in memory or stored strictly within the project's git-ignored temporary directories, never committed.
