# Phase 23 — Vendor Feature Schema

## Feature definition fields

Stable ID, namespace, extension ID, canonical name, category, value type,
constraints, default, read/write access, dependencies, conflicts, required
operations, applicable models, firmware constraints, read-back support,
persistence-verification support, evidence IDs, verification status,
deprecation flag.

## Value types

| Type | Kotlin | Notes |
|---|---|---|
| Boolean | `BooleanValue` | — |
| Int | `IntValue` | min/max/step |
| Float | `FloatValue` | min/max (Double) |
| Enum | `EnumValue` | allowed set |
| Structured | `StructuredValue` | required/allowed fields |
| List | `ListValue` | max length |

## Validation

Deterministic, side-effect-free. Type checked first, then constraints.
Invalid values return typed reasons; never coerced.

## Metadata vs values

The schema describes what values are legal. A valid value is not proof
the device supports the feature — capability and access checks are
separate.
