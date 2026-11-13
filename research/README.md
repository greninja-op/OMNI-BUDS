# OmniBuds Device & Feature Research

This directory is the verified knowledge base that will drive OmniBuds' per-model
feature surface: identity, official-app feature sets, battery display semantics,
ANC, gestures/buttons, EQ, spatial audio, multipoint, wear detection, and every
other model-specific capability.

## Current project focus (2026-11-10)

Active scope is **mainstream consumer Bluetooth audio wearables only**:

- True wireless earbuds (TWS)
- Wireless over-ear and on-ear headphones
- Wireless neckbands
- Open-ear hooks and clip-on earbuds
- Bone-conduction headsets

Out of current scope (kept as future research, not actively expanded):

- Wired-first audiophile IEM brands and their non-Bluetooth products
- Chi-Fi brands whose current catalog is wired-only
- Aviation, industrial, and hearing-protection comms
- Sleep buds and hearing aids
- Smart glasses
- Gaming headsets
- Kids' audio and translation/AI earbuds
- Phones and other non-audio devices

## Scope

Included:

- True wireless earbuds (TWS), including stem, bud, open, and clip designs
- Open-ear hooks and clips
- Neckbands and wireless headsets
- Over-ear and on-ear Bluetooth headphones
- Bone-conduction headsets
- Bluetooth audio wearables with an official companion app or documented
  on-device controls

Excluded:

- Speakers, soundbars, car kits, TVs, set-top boxes, smart-home devices
- Wired-only audio products unless they have a Bluetooth variant
- Phones, watches, and other non-audio devices

## Unit of record

One record = one exact model/variant/generation. A "Galaxy Buds3" and a
"Galaxy Buds3 Pro" are separate records. A "CMF Buds" and "CMF Buds Pro 2" are
separate records. Store each company's records as a JSON array in
`research/companies/<company-slug>.json`.

Every record follows `research/schema/device-model.schema.json`.

## Source policy

1. **Official first**: manufacturer product/spec pages, support pages, user
   manuals, firmware release notes, official app store listings, in-app feature
   descriptions, and official regional pages.
2. **App facts**: record the official app name, platform availability, package
   ID where known, and the app version/date observed if available.
3. **Secondary sources** (reputable reviews, teardown sites, retailer spec
   sheets) may fill a missing technical fact only when no official source exists.
   Mark `confidence` accordingly and put the uncertainty in `gaps`.
4. **Never invent**: absent ANC dB, battery hours, gesture behaviour, or app
   features stay `null`/empty and are listed in `gaps`.
5. **Every record needs sources**: URL, source type, and access date
   (`2026-11-10` for this initial pass).

## Battery semantics (required, because this is a product-visible contract)

Each record must answer:

- `budsDisplay`: are the two buds shown **split** (L/R separately) or
  **combined** (one percentage)?
- `caseDisplay`: is the case shown **separately**, **combined** with the buds,
  or **hidden**?
- `caseVisibility`: when is the case shown? always / only while buds are docked /
  only while the case is open / only while charging / unknown.
- `caseWhileBudsOut`: when both buds are out, is the case reading live,
  last-known, or hidden?
- Claimed hours with ANC, without ANC, and case hours when officially published.

This is what makes the workspace match the official app instead of showing a
generic ring layout.

## Differentiation rules

- `generation` is the model generation (e.g. "3", "2nd gen", "2024").
- `variant` is the commercial variant (Pro, Ultra, Lite, Plus, Open, ANC, etc.).
- `featureDeltaFromPrevious` lists only features that are genuinely new or
  removed relative to the previous generation/variant, with a source.
- Do not collapse a family into one record. If the official app shows different
  toggles for two variants, they are two records.

## Feature vocabulary

Use stable lowercase kebab-case ids. Core ids should align with OmniBuds'
`CoreFeature` where possible (`noise-control.anc`, `noise-control.transparency`,
`equalization.equalizer`, `sensing.wear-detection`, `connectivity.multipoint`,
etc.). Vendor-only features use `vendor.<vendor>.<feature>`.

## Layout

```text
research/
├── README.md
├── schema/device-model.schema.json
├── companies.json                 # company registry, waves, official app seeds
└── companies/<company-slug>.json  # one array of model records per company
```

## Status

Wave 1 is in progress. Coverage and gaps are tracked in `research/coverage.md`
when the first wave lands. A record is not "done" until it conforms to the
schema, has at least one official source, and states its gaps.
