# Product Image Catalog

> **Superseded in Iteration 2 (2026-11-04):** the AI-generated
> illustrations described below were REMOVED from the app. The current
> system — one genuinely licensed photograph, vector category artwork,
> and user-supplied photos — is documented in
> `iteration-2/product-image-system.md`. This file remains as the
> record of the Iteration-1 approach.

## Approach and licensing
Product imagery is ORIGINAL illustration created for OmniBuds, bundled
locally as WebP in `platform/app/src/main/res/drawable-nodpi/`. No
manufacturer photography is redistributed (licensing), no remote URLs
are used at runtime (fragility), and no image is assigned to a model it
does not depict. Each illustration was visually inspected before
inclusion; the CMF Buds illustration was regenerated once because the
first version drew the wrong case shape (the product's signature is a
square case with an orange corner dial).

Dedicated illustrations (the devices this catalog was seeded with):
realme Buds T300, JBL Tune 130NC TWS, CMF Buds, Redmi Buds 5A,
Sony WI-C100, boAt Rockerz 450, Skullcandy Hesh ANC.

Generic category illustrations: `generic_tws`, `generic_over_ear`,
`generic_neckband` (neutral greyscale category art; two are greyscale
derivatives of catalog illustrations, produced when the image service
errored — they depict a category, never a model).

## Catalog (ProductCatalog.kt)
24 product-line entries with: id, advertised-name match keys,
manufacturer, model, category (TWS / OVER_EAR / NECKBAND), image asset
(dedicated or null = category generic), retail feature knowledge, and
exact-vs-family match flag. AirPods Max resolves as OVER_EAR, never as
TWS earbuds.

## Resolution order
1. User override (per device, persisted) — including an explicit
   "none of these" generic override.
2. Exact model match on the advertised name.
3. Family match (brand line known, model not).
4. Category fallback: real device name kept, category illustration,
   no invented manufacturer/model, no invented features.

## Fallback honesty
Unknown devices never receive another product's image or features.
Feature knowledge drives section VISIBILITY only; it never claims a
control works (see backend-integration.md).
