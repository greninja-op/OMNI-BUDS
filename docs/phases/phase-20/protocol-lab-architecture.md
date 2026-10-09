# Phase 20 — Protocol Lab Architecture

See `design.md` for the component map.

## The passive/active boundary

The laboratory is **passive**: it reads traces, parses bytes, correlates
events, and generates fixtures. It has:

- No Bluetooth handles.
- No transport write APIs.
- No command constructors.
- No device sessions.

The **active** side (device control) lives in `core.feature` behind
`FeatureProtocolPort`, gated by capability discovery, verification levels,
and user authorization. The lab never imports these.

A future engineer may use lab findings to write a protocol adapter, but that
is a separate, explicitly authorized implementation task — not an automatic
promotion.

## Data flow

Traces enter via import (bounded, validated, redacted). Analysis is pure
functions over immutable data. Fixtures and reports exit; nothing enters
a device.
