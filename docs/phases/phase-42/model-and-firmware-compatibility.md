# Phase 42 — Model and Firmware Compatibility

## Identity model

- **Family match:** Apple company ID 0x004C + audio
  class-of-device → AirPods family. Exact generation, Pro/Max
  variant, and model number are NOT resolved.
- **Ambiguous:** Apple company ID without audio class (could be
  iPhone, Mac, Watch) → writes disabled, read-only.
- **Not matched:** no Apple company ID.

## Firmware

No firmware-dependent behavior is claimed. `supportedFirmware`
is null (firmware-independent). Firmware-specific capability
differences are not asserted without evidence.

## Generations

AirPods 1–4, AirPods Pro 1–3, AirPods Max are not distinguished.
Research shows generation-specific features (e.g. Pro 3 heart-rate
monitor) exist, but OmniBuds claims no generation-specific
behavior.
