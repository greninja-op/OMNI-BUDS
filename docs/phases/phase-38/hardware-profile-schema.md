# Phase 38 — Hardware Profile Schema

## Version

Schema version 1.

## Fields

profileId, schemaVersion, hostModel?, hostBuild?, apiLevel?,
appVersion, gitCommit, deviceManufacturer?, deviceModel?,
firmwareVersion?, transportId?, protocolId?, prerequisites,
allowedOperations, verificationStatus.

## Rules

- Unavailable fields are explicit nulls.
- apiLevel, when present: 26–99.
- verificationStatus: DECLARED, SIMULATED, HARDWARE_VERIFIED.
- Keep user device config separate from the protocol knowledge
  database.
