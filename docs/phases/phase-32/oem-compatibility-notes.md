# Phase 32 — OEM Compatibility Notes

## Status

All OEM-specific statements below are **UNVERIFIED** in this phase —
no OEM devices were tested. They are recorded as risks and
investigation targets, not facts.

## Known risk categories

- **Background process management:** OEMs (e.g. aggressive task
  killers) may terminate the app despite correct lifecycle handling.
  Status: UNVERIFIED.
- **Battery optimization:** Doze/app-standby and OEM battery savers may
  delay widget/notification updates. Status: UNVERIFIED.
- **Bluetooth stack behavior:** GATT/RFCOMM behavior varies by chipset
  and OEM stack. Status: UNVERIFIED.
- **Permission/settings navigation:** OEM skins differ in permission
  UX. Status: UNVERIFIED.
- **Widget refresh:** Launcher-dependent; OEM launchers may ignore
  update requests. Status: UNVERIFIED.
- **Quick Settings tile:** Tile availability UX varies. Status: UNVERIFIED.

## General Android behavior (verified by documentation)

- API 31+ Bluetooth runtime permissions.
- API 33+ notification runtime permission.
- Background execution limits from API 26.

## Recommendation

OEM verification belongs to the hardware-testing phase with real
devices. No OEM compatibility is claimed here.
