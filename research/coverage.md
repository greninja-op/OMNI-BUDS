# Research coverage

Status: **scope reset — mainstream consumer Bluetooth audio wearables only.**
Research is paused on niche clusters. Wave 1 data is retained; out-of-scope
brands are documented as future research, not actively expanded.

**Validated: 741 records across 83 companies — wave 1 complete.**
**Wave 2 retained: Moondrop 15, KZ 4, TRN 4, CCA 0.** These are kept only for
their actual TWS/Bluetooth records; the remaining Chi-Fi IEM crawl is stopped.

## Completed clusters

| Cluster | Companies | Models | File(s) | Notes |
| --- | --- | ---: | --- | --- |
| Audio majors A | Sony, Bose, JBL, Sennheiser | 56 | `companies/{sony,bose,jbl,sennheiser}.json` | 0 schema errors; battery contracts differ sharply by brand |
| Chinese ecosystem | Xiaomi, Redmi, POCO, Huawei, Honor, vivo, iQOO | 57 | `companies/{xiaomi,redmi,poco,huawei,honor,vivo,iqoo}.json` | 0 schema errors; official app is split, system Bluetooth UI collapses to the lowest percentage |
| Ecosystem | Apple, Beats, Samsung, Google | 41 | `companies/{apple,beats,samsung,google}.json` | 0 schema errors; Apple aggregates L/R, Samsung/Google split; Beats and AirPods Max have no case battery on several models |
| Nothing family | Nothing, CMF, OnePlus, realme, OPPO | 59 | `companies/{nothing,cmf,oneplus,realme,oppo}.json` | 0 schema errors; CMF Buds 1 is split L/C/R in Nothing X; realme T300 is split L/R with no case tile |
| Open-ear / neckband | Shokz, Oladance, Suunto, Nank, Sanag, Amazon | 49 | `companies/{shokz,oladance,suunto,nank,sanag,amazon}.json` | 0 schema errors; open-ear and bone-conduction devices often have combined or no case battery |
| Indian budget | boAt, Noise, Boult, pTron, Mivi, Truke, Wings, Fire-Boltt, Zebronics | 136 | `companies/{boat,noise,boult,ptron,mivi,truke,wings,fire-boltt,zebronics}.json` | 0 schema errors; battery layout often undocumented; app fragmentation is a real risk |
| Audiophile | Shure, Westone, Etymotic, Final, Campfire, Focal, Meze, HiFiMAN, Grado, Audeze | 30 | `companies/{shure,westone,etymotic,final,campfire,focal,meze,hifiman,grado,audeze}.json` | 0 schema errors; many models are wired-only; battery UI often LED-only |
| Gaming / remaining | Razer, SteelSeries, Logitech, HyperX, Turtle Beach, Corsair, EPOS, ASUS ROG, Sony INZONE, Motorola, Nokia, HMD, Microsoft, Nothing Ear (legacy) | 73 | `companies/{razer,steelseries,logitech,hyperx,turtle-beach,corsair,epos,asus-rog,sony-inzone,motorola,nokia,hmd,microsoft,nothing-ear}.json` | 0 schema errors; gaming headset battery UI is rarely documented per model |
| Accessories | Anker Soundcore, Edifier, 1MORE, SoundPEATS, QCY, Baseus, UGREEN, EarFun, Tribit, Tranya, Mpow | 141 | `companies/{anker-soundcore,edifier,1more,soundpeats,qcy,baseus,ugreen,earfun,tribit,tranya,mpow}.json` | 0 schema errors; battery layout mostly inferred; Tranya/Mpow have no app |
| Audio majors B | Audio-Technica, Beyerdynamic, AKG, Philips, Panasonic, LG, Jabra, Skullcandy, Marshall, B&W, B&O, Master & Dynamic, Technics | 99 | `companies/{audio-technica,beyerdynamic,akg,philips,panasonic,lg,jabra,skullcandy,marshall,bowers-wilkins,bang-olufsen,master-dynamic,technics}.json` | 0 schema errors; no vendor publishes ANC dB; battery UI inferred |

### Battery-display findings from the completed cluster

These are product requirements, not trivia:

- **Sennheiser**: the official app shows one combined earbud percentage, taken
  from the lower-charging bud. It is not a split L/R display. The case is a
  separate reading.
- **Bose**: one combined earbud percentage, also using the lower bud. The case
  reading is **last-known from when the buds were docked**, not live while worn.
- **JBL**: split L/R plus a separate case reading; the case value refreshes when
  the buds are placed in the case with the lid open, and may otherwise be absent.
- **Sony**: Sound Connect shows split L/R, while the Android/iOS system status
  shows the lower bud. OmniBuds should follow the official app contract for the
  workspace, not the system's single-value summary.

Implication: a single generic "three rings" renderer is wrong. Each model needs
an explicit `battery` contract (`budsDisplay`, `caseDisplay`, `caseVisibility`,
`caseWhileBudsOut`) and the UI must render from that contract.

### Battery-display findings from the Chinese ecosystem cluster

- **Xiaomi / Redmi / POCO**: the official Xiaomi Earbuds app shows per-bud and
  case detail (split), but the Android system Bluetooth UI collapses left, right,
  and case into the **single lowest percentage**. OmniBuds should follow the
  official app, not the system summary.
- **Huawei**: several models publish two battery profiles depending on the
  priority mode. FreeBuds 6 is 5.5 h / 32 h ANC-off and 4.0 h / 24 h ANC-on in
  sound-priority mode, but 6 h / 36 h and 4.5 h / 27 h in connection-priority
  mode. FreeLace Pro 2 has a similar split.
- **vivo**: the Wi-Fi lossless path drops TWS 5 Pro playback to 5 h (4 h with
  ANC) versus 11 h over Bluetooth.
- **Honor**: regional claims differ materially (Earbuds Open 7 h/26 h global vs
  6 h/22 h regional). Do not hard-code one region's numbers.
- **Redmi**: Redmi Buds 8 Pro regresses to 8 h/33 h from Buds 6 Pro's 9.5 h/36 h;
  the cheaper non-Pro Redmi Buds 8 reaches 11 h/44 h.

### Battery-display findings from the ecosystem cluster

- **Apple**: one combined bud percentage plus a separate case percentage. The
  official UI does not document a split L/R main view.
- **Samsung and Google**: each bud is shown separately; the case is a separate
  reading.
- **Beats**: TWS records are split in the Beats app, but iOS Settings/widget can
  aggregate the pair. Some models have no case battery at all.
- **No case battery**: Beats Solo Buds has a passive USB-C cradle (no case
  level, no case charging). AirPods Max (both variants), Beats 360, Studio Pro,
  Solo 4 and Flex also have no case battery; `caseDisplay` is `hidden`, not
  `separate`.
- **Samsung**: the case percentage remains visible in the Battery Info widget
  when buds are out, but it is last-known, not live.
- **Pixel Buds Pro 2 / 2a**: the battery widget stays visible while
  disconnected (Bluetooth on, buds nearby), but the case reading is still
  last-known because the case has no independent radio.
- **Galaxy Buds On**: no ANC despite being a 2026 flagship open-ear model.

### Battery-display findings from the Nothing / CMF / OnePlus / realme / OPPO cluster

- **CMF Buds 1 is not a combined-battery device.** Nothing X shows **100% L /
  80% C / 100% R** — split L/R plus a separate case tile. The case tile requires
  the case to be open. If OmniBuds shows one combined percentage for CMF Buds 1,
  that is an OmniBuds parsing/rendering defect.
- **realme Buds T300 is split L/R with no case tile at all** in realme Link.
  The product has a charging case, but the official app does not surface its
  percentage. Research therefore records `caseDisplay = hidden`.
  **App-level override:** OmniBuds shows a separate case tile while at least one
  bud is docked (`caseDisplay = separate`, `caseVisibility = only-when-docked`,
  `caseWhileBudsOut = hidden`), because that is the requested user-facing
  behaviour and the T300 reports a 1..100 case level through the docked bud's
  pogo pins.
- The real difference between the two is **case exposure**, not bud splitting.
- OnePlus/OPPO HeyMelody case visibility and realme Air-series case tiles remain
  partly inferred; those are marked as gaps.

### Battery-display findings from the open-ear / neckband cluster

- **Shokz OpenFit** shows split L/R plus a separate case percentage. Shokz
  bone-conduction models show one combined percentage and no case.
- **Oladance OWS 1** has a case with no battery, so there is no case percentage.
  OWS Sports has no charging case at all.
- **Suunto Wing/Wing 2/Aqua** “case” is a powerbank, not a charging case; the app
  does not document powerbank charge.
- **Amazon Echo Buds** shows split L/R plus a separate case, but the case LED
  reports the lowest-charging bud, not an average.
- **Shokz OpenSwim Pro** uses one combined battery with two runtimes in one
  device: 9 h Bluetooth / 6 h MP3.
- **Echo Frames** have no case and a single combined battery.
- Nank/Sanag TWS battery UI remains `unknown`; their neckband/back-hook models are
  combined single-unit displays.

### Battery-display findings from the Indian budget cluster

- **boAt Hearables** does split L/R where documented: Zenith Pro’s support page
  says the app shows both earbuds, and Crown launch coverage says individual
  levels. Most other boAt models remain unknown.
- **Noise N1 Pro** exposes only the phone’s single combined bud percentage; its
  FAQ says no case percentage and no L/R split.
- **Noise Air Buds 6** uses BUDSLINK, not Noise Audio/NoiseFit; several upgrades
  are paid in-app purchases.
- **ANC does not imply an app**: Boult Klarity 4 has 35 dB ANC but no companion
  app; battery is a phone-level combined value or case LEDs.
- Several budget models use numeric case LEDs instead of an app; the case value
  can be live on-device while the buds remain combined.

### Battery-display findings from the audiophile cluster

- **Final ZE8000 / ZE8000 MK2**: split L/R buds; the case is hidden from the app
  and only has a case LED.
- **Campfire Orbit**: the case is a four-LED 25/50/75/100% indicator that only
  lights when the lid opens or the case button is pressed; the phone shows buds
  only.
- **Shure AONIC 215 Gen 2 TW2**: the case reports charge-count LEDs, not a
  percentage; the app’s L/R-vs-combined layout is undocumented.
- **Grado GT220**: combined buds value; case hidden; no app at all.
- **HiFiMAN Svanar family**: mostly no app battery UI; case state is LED-only.
- **Focal Bathys / Audeze Maxwell / Mobius / Penrose**: one combined headphone
  percentage, no case concept.

### Battery-display findings from the gaming / remaining cluster

- **Razer Hammerhead Pro HyperSpeed**: the case LED reports only case battery
  while the case is open; no bud percentages, no display when closed.
- **HMD Amped Buds**: no companion app; the case is a phone power bank, giving
  95 h total ANC-off / 58 h ANC-on.
- **Sony INZONE Buds**: case adds only 12 h to 12 h buds — a 1:1 ratio.
- **Corsair HS80 Max**: runtime depends heavily on connection mode — 130 h over
  Bluetooth vs 65 h on 2.4 GHz with RGB off.
- **SteelSeries Arctis GameBuds**: 40 h total is quoted only with noise control
  off; ANC-on runtime is unpublished.

### Battery-display findings from the accessory cluster

- **Soundcore Liberty 5 Pro / Liberty 4 Pro**: the charging case has its own
  display and touch bar, so the case is a control surface and ANC readout, not
  just a battery row.
- **Baseus Bowie MA10**: 8 h per charge but a claimed 140 h total with case — an
  extreme case-to-bud ratio that needs verification.
- **Edifier NeoBuds Pro 2**: flagship ANC with only 4 h ANC on / 16.5 h total.
- **SoundPEATS Space Pro**: 151 h single-charge claim on an over-ear; no case row.
- **Tribit FlyBuds 3**: 110 h total but no per-bud hours published.
- **QCY T13 ANC2**: 60 h total — double the 30 h T13 ANC.
- **Soundcore AeroFit 2 Pro**: two contracts in one product — 34 h case total in
  open-ear form, 24 h in ANC form, depending on hook position.
- **Tranya / Mpow**: no official companion app; only OS-level aggregate battery,
  no split and no case percentage.

### Battery-display findings from audio majors B

- **No vendor publishes ANC attenuation in dB** across these 99 records; modes and
  adaptive/personalised flags are source-backed, dB is null.
- **“Live” case battery while buds are out is usually impossible.** Cases without
  their own radio cannot report live; `last-known` is the realistic state. Smart
  cases (Jabra Gen 2 LE Audio, B&W Pi8 retransmission, LG T90S) may differ, but
  UI behaviour is undocumented.
- **Codec changes the display math.** Technics AZ80 drops from 24 h ANC total
  (AAC) to 16 h with LDAC; AZ100 drops from 28 h to 18 h. Runtime must be
  codec-aware.
- **Extreme outliers**: AKG N9 Hybrid 55 h ANC / 100 h no ANC; Marshall Major V
  100+ h; Skullcandy Push ANC Active 37 h ANC / 58 h no ANC.
- **Marketing totals hide weak bud runtime**: LG xboom Buds Plus is 7.5 h ANC /
  10 h no ANC but “30 h total”; Buds Lite is 8.5 / 11.5 h with 35 h total.
- **B&O Beoplay Eleven** official spec is 6 h ANC / 20 h total and 8 h no ANC /
  28 h total — not the commonly quoted 24 h.
- **Jabra Gen 2 divergence**: Elite 10 Gen 2 is 6 h / 27 h ANC; Elite 8 Active
  Gen 2 is 8 h / 32 h ANC, despite sharing the app and case design.
- **Same case, different totals**: Marshall Motif II is 30 h ANC / 43 h no ANC;
  Skullcandy Method 540 is 32 h / 40 h.

## Current active scope

Included for the product now:

- Mainstream TWS earbuds
- Wireless over-ear / on-ear headphones
- Neckbands
- Open-ear and clip-on earbuds
- Bone-conduction headsets

Out of current scope (retained as future research):

- Wired-first audiophile IEM brands and non-Bluetooth products
- Chi-Fi brands whose current catalog is wired-only
- Aviation / industrial / hearing-protection comms
- Sleep buds and hearing aids
- Smart glasses
- Gaming headsets
- Kids / translation / AI earbuds
- Phones and non-audio devices

## In progress (wave 2, reduced scope)

Chi-Fi / IEM A is stopped for now. Only the already-captured TWS/Bluetooth
records from Moondrop, KZ, TRN and the empty CCA file are retained. The
remaining Chi-Fi brands and all other wave-2 clusters are deferred.

| Cluster | Companies | Status |
| --- | --- | --- |
| Chi-Fi / IEM brands A | Moondrop, KZ, TRN, CCA | stopped; TWS records retained |
| Chi-Fi / IEM brands A remainder | Kbear, Tripowin, Blon, Tin HiFi, Simgot, Dunu, TRUTHEAR, Tanchjim, Kiwi Ears, Softears, Ikko, Kinera, Letshuoer, Thieaudio, Yanyin, Penon, NiceHCK, XINHS | deferred |

### Chi-Fi / IEM A progress

- **Moondrop**: 15 records, validated.
- **KZ**: 4 records, validated (XTRA Pro, Krio, Carol Pro, Sora).
- **TRN**: 4 records, validated (T350, T300, AM1, BT1).
- **CCA**: no current Bluetooth model found. Official site returned a transport
  error and the Linsoul catalog is wired-only; `cca.json` is an empty array by
  design, with the blocker recorded here.
- **Stopped for current scope**: the remaining Chi-Fi / IEM brands are deferred
  because the active product focus is normal TWS, wireless headsets, neckbands,
  open-ear/clip, and bone-conduction products from mainstream consumer brands.

## Deferred (not researched)

These clusters were dropped from the active program by user priority on
2026-11-10. They are listed here as known coverage gaps, not as completed work.
If partial files from an earlier run exist for these companies, they are not
part of the active validated scope.

| Cluster | Companies | Status |
| --- | --- | --- |
| Chi-Fi / portable audio B | FiiO, iBasso, Shanling, HiBy, Cayin, Questyle, Astell&Kern, Lotoo, Unique Melody, 64 Audio, JH Audio, Empire Ears, Vision Ears, Noble Audio | deferred |
| Smart glasses | Ray-Ban Meta, Oakley Meta, Bose Frames, Amazon Echo Frames, Soundcore Frames, Solos, Lucyd, Fauna, Vue | deferred |
| Sleep + hearing | Bose Sleepbuds, Soundcore Sleep, Ozlo, Kokoon, QuietOn, Amazfit, Jabra Enhance, Eargo, Lexie, Bose SoundControl, Sennheiser All-Day Clear, Sony CRE, Audien, Nuheara | deferred |
| Aviation / industrial | Bose A20/A30, Lightspeed, David Clark, Telex, 3M Peltor, Sensear, ISOtunes, Earmor, Sordin, MSA | deferred |
| More audio majors | Denon, Marantz, Yamaha, Onkyo, Pioneer, JVC, Kenwood, Klipsch, Jamo, Cambridge Audio, NAD, Sivga, Sendy, HarmonicDyne | deferred |
| More smartphone / budget | Meizu, ZTE/nubia, Infinix, Tecno, itel, Lava, Micromax, Lenovo/Thinkplus, Sharp, Fujitsu, Kyocera, TCL, Hisense, Skyworth | deferred |
| Kids / translation | Puro, JBL Jr, BuddyPhones, Timekettle, Pocketalk, Waverly, iFLYTEK, Ola Friend | deferred |

## Known systemic gaps

- Most brands do not publish ANC or transparency dB figures; those remain null.
- App package IDs and versions are inconsistently exposed in store listings.
- Case-visibility behaviour is often undocumented; records mark inference and
  confidence rather than guessing silently.
- Regional app variants can differ; the record should note the region observed.
- `caseHours` currently mixes total-with-case and case-only values across brands;
  normalize before the UI consumes it.

## App integration status

- **VerifiedDeviceCatalog**: 423 mainstream profiles — 21 curated pilot models
  plus 402 generated from the research JSON
  (`research/tools/generate_catalog.py`).
- **Battery contract**: `WorkspaceScreen` / `AdaptiveBatteryRings` consume the
  verified contract; CMF Buds 1 is split + case-when-open, T300 is split with a
  case tile only while a bud is docked, Apple is combined, Samsung/Sony are split.
- **Feature mapping**: generated records now carry research `featureIds`, mapped
  to workspace capabilities (`anc_mode`, `transparency_mode`, `eq_preset`,
  `spatial_audio`, `game_mode`, `volume_enhancer`, `wear_detection`,
  `multipoint`, `ultra_bass`, `touch_controls`).
- **Control guardrail**: `VerifiedControlCatalog` filters to ids with a real
  dispatch path; `writableCapabilityIds` only exposes driver-backed brands
  (CMF/Nothing/OnePlus/realme/OPPO/Samsung/Xiaomi/Apple). Driverless brands stay
  battery/identity/read-only.
- **UI**: additive “Layout matched to …” caption under the battery rings with a
  fade animation. Existing animations and layout are untouched.
- **Tests**: `VerifiedDeviceCatalogTest` covers contracts, aliases, variant
  separation, writable filtering, feature mapping, and the generated catalog.
  `gradlew test assembleDebug` is green.

## Remaining

1. Normalize `caseHours` (`case-only` vs `total-with-case`) in the research JSON.
2. On-device verification of CMF Buds 1 and realme T300 once a phone is connected.
3. Optional: add more brand-specific control cards for generated models.
