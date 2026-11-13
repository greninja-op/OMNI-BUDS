const fs = require("fs");
const path = require("path");

const C = "MOONDROP";
const CS = "moondrop";
const APP = {
  name: "MOONDROP Link",
  platforms: ["android", "ios"],
  packageIds: [],
  appStoreUrls: ["https://moondroplab.com/en/moondrop-link"],
  appVersionObserved: null,
  notes: "Official MOONDROP companion app. Product pages document EQ, custom touch controls, ANC/transparency switching and firmware/settings for supported TWS models. Package ID not verified from this region."
};
const NOAPP = {
  name: "No official companion app",
  platforms: [],
  packageIds: [],
  appStoreUrls: [],
  appVersionObserved: null,
  notes: "Neckband/cable model with inline controls; no app, EQ or firmware UI."
};
const S = (u, t) => ({ url: u, type: "official-product", title: t, accessed: "2026-11-10" });
const APPURL = "https://moondroplab.com/en/moondrop-link";

function tws(o) {
  return {
    company: C, companySlug: CS,
    family: o.family, model: o.model, slug: o.slug,
    generation: o.generation ?? "1", variant: o.variant ?? null,
    releaseYear: o.releaseYear ?? null,
    formFactor: o.formFactor ?? "tws-bud",
    officialApp: o.app === false ? NOAPP : APP,
    battery: {
      budsDisplay: o.budsDisplay ?? (o.app === false ? "combined" : "split"),
      caseDisplay: o.caseDisplay ?? (o.app === false ? "hidden" : "separate"),
      caseVisibility: o.caseVisibility ?? (o.app === false ? "unknown" : "only-when-docked"),
      caseWhileBudsOut: o.caseWhileBudsOut ?? (o.app === false ? "hidden" : "last-known"),
      budsHoursAnc: o.hAnc ?? null,
      budsHoursNoAnc: o.hNoAnc ?? null,
      caseHours: o.hCase ?? null,
      chargingIndicators: o.led ?? "USB-C charging; case LED; app/OS battery readout.",
      notes: o.batNote ?? "Battery hours are the official Moondrop figures at the codec stated on the product page. Case value is last-known while buds are out because the case has no independent radio."
    },
    anc: {
      supported: o.anc ?? null,
      modes: o.anc ? ["anc", "transparency", "off"] : [],
      adaptive: o.adaptive ?? null,
      ancDb: o.ancDb ?? null,
      transparencyDb: null,
      personalized: o.personalized ?? null,
      windNoise: o.windNoise ?? null,
      notes: o.ancNote ?? null
    },
    features: o.features,
    controls: o.controls ?? {
      types: ["touch"],
      gestures: [
        { gesture: "Single tap", action: "Play/pause; answer/end call", configurableInApp: true, notes: null },
        { gesture: "Double tap (left/right)", action: "Previous/next track", configurableInApp: true, notes: null },
        { gesture: "Long press", action: "Voice assistant and/or ANC mode cycle", configurableInApp: true, notes: "Exact mapping is model-specific and customisable in MOONDROP Link." }
      ],
      physicalButtons: null,
      configurableInApp: true,
      notes: "Touch mapping is customisable in MOONDROP Link."
    },
    audio: {
      eqPresets: o.presets ?? [],
      customEq: o.customEq ?? true,
      eqBands: o.eqBands ?? null,
      spatialAudio: false,
      headTracking: false,
      codecs: o.codecs,
      hiRes: o.hiRes ?? null,
      multipoint: o.multipoint ?? null,
      wearDetection: o.wear ?? false,
      notes: o.audioNote ?? null
    },
    appOnlyFeatures: o.appOnly ?? ["EQ / tuning", "Custom touch mapping", "ANC mode control", "Firmware update"],
    featureDeltaFromPrevious: o.delta ?? [],
    sources: [S(o.url, o.title), { url: APPURL, type: "official-support", title: "MOONDROP Link app", accessed: "2026-11-10" }],
    confidence: o.confidence ?? "medium",
    gaps: o.gaps ?? [],
    notes: o.notes ?? null,
    researchedAt: "2026-11-10"
  };
}

const models = [
  tws({
    family: "MOCA", model: "MOCA", slug: "moondrop-moca", releaseYear: 2024,
    url: "https://moondroplab.com/en/products/moca/", title: "MOONDROP MOCA True Wireless Stereo",
    hNoAnc: 6, hCase: 15, codecs: ["SBC", "AAC", "LC3"], anc: true, ancDb: 40,
    features: [
      { id: "noise-control.anc", name: "Hybrid wide-band ANC (40 dB)", availability: "both", notes: "40 dB hybrid ANC claimed by Moondrop." },
      { id: "noise-control.transparency", name: "Transparency mode", availability: "both", notes: null },
      { id: "sensing.wear-detection", name: "Photo-sensor wear detection", availability: "both", notes: "Remove to pause, insert to play." },
      { id: "connectivity.multipoint", name: "Dual-device connection", availability: "device-only", notes: "Not listed in the MOCA specification block." },
      { id: "power.wireless-charging", name: "Wireless charging case", availability: "device-only", notes: null },
      { id: "gaming.low-latency", name: "60 ms low-latency game mode", availability: "both", notes: null },
      { id: "equalization.bass-tunings", name: "Five bass tunings", availability: "official-app", notes: null }
    ],
    wear: true, multipoint: null,
    audioNote: "TiN ceramic dome dynamic driver. LC3 support is listed; no LDAC.",
    gaps: ["Battery layout in MOONDROP Link is not documented; split/separate is inferred.", "Multipoint is not listed; treat as unconfirmed.", "No official ANC-on runtime published."]
  }),
  tws({
    family: "PUDDING", model: "PUDDING", slug: "moondrop-pudding", releaseYear: 2026,
    url: "https://moondroplab.com/en/products/pudding/", title: "MOONDROP PUDDING ANC True Wireless Stereo",
    hNoAnc: 12.5, hCase: 41, codecs: ["SBC", "AAC", "LHDC"], anc: true,
    features: [
      { id: "noise-control.anc", name: "ANC", availability: "both", notes: null },
      { id: "equalization.equalizer", name: "MOONDROP Link EQ", availability: "official-app", notes: null },
      { id: "gaming.low-latency", name: "Low-latency game mode", availability: "both", notes: null }
    ],
    audioNote: "LHDC support listed; no LDAC.",
    gaps: ["ANC depth and modes are not published.", "Battery layout in MOONDROP Link is inferred.", "No ANC-on runtime published."]
  }),
  tws({
    family: "GOLDEN AGES", model: "GOLDEN AGES 2", slug: "moondrop-golden-ages-2", generation: "2", releaseYear: 2025,
    url: "https://moondroplab.com/en/products/golden-ages-2/", title: "MOONDROP GOLDEN AGES 2 Planar True Wireless Stereo",
    hNoAnc: 8, hCase: 24, codecs: ["SBC", "AAC", "LDAC", "LHDC", "LC3"], anc: false, customEq: true,
    features: [
      { id: "driver.planar", name: "Planar driver", availability: "device-only", notes: null },
      { id: "equalization.equalizer", name: "MOONDROP Link EQ", availability: "official-app", notes: null }
    ],
    audioNote: "Planar magnetic TWS. LDAC, LHDC and LC3 listed.",
    gaps: ["No ANC.", "Battery layout in MOONDROP Link is inferred.", "No IP rating published."]
  }),
  tws({
    family: "SPACE TRAVEL", model: "SPACE TRAVEL 2 ULTRA", slug: "moondrop-space-travel-2-ultra", generation: "2", variant: "Ultra", releaseYear: 2025,
    url: "https://moondroplab.com/en/products/space-travel-2-ultra/", title: "MOONDROP SPACE TRAVEL 2 ULTRA Planar True Wireless Stereo",
    hNoAnc: 7, hCase: 24, codecs: ["SBC", "AAC", "LDAC"], anc: null,
    features: [
      { id: "driver.planar", name: "Planar driver", availability: "device-only", notes: null },
      { id: "audio.hi-res", name: "LDAC", availability: "device-only", notes: null },
      { id: "equalization.equalizer", name: "MOONDROP Link EQ", availability: "official-app", notes: null }
    ],
    audioNote: "Planar driver; LDAC is the headline codec.",
    gaps: ["Official page does not state ANC, transparency or IP rating for the ULTRA; unverified.", "Battery layout in MOONDROP Link is inferred.", "No ANC-on runtime published."]
  }),
  tws({
    family: "SPACE TRAVEL", model: "SPACE TRAVEL 2", slug: "moondrop-space-travel-2", generation: "2", releaseYear: 2025,
    url: "https://moondroplab.com/en/products/space-travel-2/", title: "MOONDROP SPACE TRAVEL 2 True Wireless Stereo",
    hNoAnc: 7, hCase: 24, codecs: ["SBC", "AAC"], anc: true,
    features: [
      { id: "noise-control.anc", name: "Wide-band ANC with NPU AI noise cancelling", availability: "both", notes: null },
      { id: "connectivity.multipoint", name: "Dual-device connection", availability: "device-only", notes: null },
      { id: "gaming.low-latency", name: "55 ms low-latency game mode", availability: "both", notes: null },
      { id: "equalization.equalizer", name: "MOONDROP app EQ and tune sharing", availability: "official-app", notes: null }
    ],
    multipoint: true,
    audioNote: "Single feedforward ANC. New MOONDROP app adds DSP graphic EQ and tune community.",
    gaps: ["ANC depth not published.", "Battery layout in MOONDROP Link is inferred.", "No ANC-on runtime published."]
  }),
  tws({
    family: "PILL", model: "PILL", slug: "moondrop-pill", releaseYear: 2025, formFactor: "open-ear-clip",
    url: "https://moondroplab.com/en/products/pill/", title: "MOONDROP PILL OWS Ear-clip Earbuds",
    hNoAnc: 8, hCase: 20, codecs: ["SBC", "AAC"], anc: false,
    features: [
      { id: "form.open-ear", name: "Open-ear clip design", availability: "device-only", notes: "Dipole directional sound transmission to reduce leakage." },
      { id: "connectivity.multipoint", name: "Dual-device connection", availability: "device-only", notes: null },
      { id: "gaming.low-latency", name: "55 ms game mode", availability: "both", notes: null },
      { id: "equalization.equalizer", name: "MOONDROP app EQ / bass control", availability: "official-app", notes: "Three bass tunings." },
      { id: "durability.ipx4", name: "IPX4", availability: "device-only", notes: null }
    ],
    multipoint: true,
    audioNote: "13 mm dynamic driver; open-ear tuning. No ANC; ENC call noise cancellation only.",
    gaps: ["Battery layout in MOONDROP Link is inferred.", "Codec list is SBC/AAC only on the official page."]
  }),
  tws({
    family: "ULTRASONIC", model: "ULTRASONIC", slug: "moondrop-ultrasonic", releaseYear: 2024,
    url: "https://moondroplab.com/en/products/ultrasonic/", title: "MOONDROP ULTRASONIC True Wireless Stereo",
    hNoAnc: 6, hCase: 18, codecs: ["SBC", "AAC", "LC3", "LDAC"], anc: null,
    features: [
      { id: "driver.hybrid", name: "13 mm dynamic + balanced-armature tweeter", availability: "device-only", notes: null },
      { id: "audio.hi-res", name: "LDAC / LC3", availability: "device-only", notes: null },
      { id: "gaming.low-latency", name: "55 ms game mode", availability: "both", notes: null },
      { id: "equalization.equalizer", name: "MOONDROP Link EQ", availability: "official-app", notes: null }
    ],
    audioNote: "Hybrid two-way crossover; sapphire diaphragm dynamic driver.",
    gaps: ["Official page does not state ANC; unverified.", "Battery layout in MOONDROP Link is inferred.", "No IP rating published."]
  }),
  tws({
    family: "BLOCK", model: "BLOCK", slug: "moondrop-block", releaseYear: 2024,
    url: "https://moondroplab.com/en/products/block/", title: "MOONDROP BLOCK True Wireless Stereo (Earbuds)",
    hNoAnc: 6, hCase: 15, codecs: ["SBC", "AAC"], anc: false,
    features: [
      { id: "form.flat-head", name: "Semi-in-ear flat-head design", availability: "device-only", notes: null },
      { id: "equalization.equalizer", name: "MOONDROP Link EQ", availability: "official-app", notes: null }
    ],
    audioNote: "Flat-head earbud shape; no ANC.",
    gaps: ["No ANC/transparency.", "Battery layout in MOONDROP Link is inferred.", "No IP rating published."]
  }),
  tws({
    family: "GOLDEN AGES", model: "GOLDEN AGES", slug: "moondrop-golden-ages", releaseYear: 2024,
    url: "https://moondroplab.com/en/products/golden-ages/", title: "MOONDROP GOLDEN AGES True Wireless Stereo",
    hNoAnc: 6, hCase: 18, codecs: ["SBC", "AAC", "LDAC", "LC3"], anc: false,
    features: [
      { id: "audio.hi-res", name: "LDAC / LC3", availability: "device-only", notes: null },
      { id: "equalization.equalizer", name: "MOONDROP Link EQ", availability: "official-app", notes: null }
    ],
    audioNote: "LDAC and LC3 support; no ANC.",
    gaps: ["No ANC.", "Battery layout in MOONDROP Link is inferred.", "Driver type not stated in the specification block."]
  }),
  tws({
    family: "VOYAGER", model: "VOYAGER", slug: "moondrop-voyager", releaseYear: 2023, formFactor: "neckband", app: false,
    url: "https://moondroplab.com/en/products/voyager/", title: "MOONDROP VOYAGER Bluetooth Neckband Earphone",
    hNoAnc: 12, hCase: null, codecs: ["SBC", "AAC", "aptX", "aptX HD", "aptX Adaptive", "LHDC"], anc: false,
    features: [
      { id: "form.neckband", name: "Bluetooth neckband", availability: "device-only", notes: null },
      { id: "audio.hi-res", name: "LHDC / aptX HD", availability: "device-only", notes: null }
    ],
    controls: {
      types: ["button"],
      gestures: [
        { gesture: "Inline buttons", action: "Power, volume, play/pause, track skip, calls", configurableInApp: false, notes: "Fixed inline controls." }
      ],
      physicalButtons: "Inline remote on the neckband.",
      configurableInApp: false,
      notes: "No app."
    },
    audioNote: "Neckband with LHDC/aptX HD. No ANC.",
    appOnly: [],
    gaps: ["No battery display contract: no app and no case, OS shows one combined percentage.", "No ANC.", "Exact button map not published."]
  }),
  tws({
    family: "SPACE TRAVEL", model: "SPACE TRAVEL", slug: "moondrop-space-travel", releaseYear: 2023,
    url: "https://moondroplab.com/en/products/space-travel/", title: "MOONDROP SPACE TRAVEL True Wireless Stereo",
    hNoAnc: 4, hCase: 12, codecs: ["SBC", "AAC"], anc: true,
    features: [
      { id: "noise-control.anc", name: "ANC", availability: "both", notes: "Moondrop markets Space Travel as noise-cancelling; depth not published." },
      { id: "gaming.low-latency", name: "Low-latency game mode", availability: "both", notes: null },
      { id: "equalization.equalizer", name: "MOONDROP Link EQ", availability: "official-app", notes: null }
    ],
    audioNote: "37 mAh buds / 380 mAh case. 4 h + 12 h is the official runtime.",
    gaps: ["ANC depth/modes not published on the official spec page.", "Battery layout in MOONDROP Link is inferred.", "No ANC-on runtime published."]
  }),
  tws({
    family: "ALICE", model: "ALICE", slug: "moondrop-alice", releaseYear: 2022,
    url: "https://moondroplab.com/en/products/alice/", title: "MOONDROP ALICE True Wireless Stereo",
    hNoAnc: 8, hCase: 40, codecs: ["SBC", "AAC", "aptX Adaptive"], anc: false,
    features: [
      { id: "driver.ult", name: "10 mm U.L.T. super-linear dynamic driver", availability: "device-only", notes: "Same driver family as KATO." },
      { id: "audio.hi-res", name: "aptX Adaptive", availability: "device-only", notes: null },
      { id: "equalization.equalizer", name: "MOONDROP Link EQ / 10 tunings", availability: "official-app", notes: null }
    ],
    audioNote: "QCC5151, 10 mm DLC composite diaphragm. No ANC documented.",
    gaps: ["No ANC documented; treat as absent.", "Battery layout in MOONDROP Link is inferred.", "Codec list from official page: AAC/SBC/aptX Adaptive."]
  }),
  tws({
    family: "NEKOCAKE", model: "NEKOCAKE", slug: "moondrop-nekocake", releaseYear: 2021,
    url: "https://moondroplab.com/en/products/nekocake/", title: "MOONDROP NEKOCAKE True Wireless Stereo",
    hNoAnc: 4, hCase: 12, codecs: ["SBC", "AAC"], anc: true,
    features: [
      { id: "noise-control.anc", name: "ANC digital active noise cancellation", availability: "both", notes: null },
      { id: "equalization.equalizer", name: "MOONDROP Link EQ", availability: "official-app", notes: null }
    ],
    audioNote: "2022 revision page; 4 h buds + 12 h case.",
    gaps: ["ANC depth not published.", "Battery layout in MOONDROP Link is inferred.", "No ANC-on runtime published."]
  }),
  tws({
    family: "SPARKS", model: "SPARKS", slug: "moondrop-sparks", releaseYear: 2021,
    url: "https://moondroplab.com/en/products/sparks/", title: "MOONDROP SPARKS True Wireless Stereo",
    hNoAnc: 8, hCase: 48, codecs: ["SBC", "AAC", "aptX", "aptX Adaptive"], anc: false,
    features: [
      { id: "driver.hemc", name: "6 mm HEMC dynamic driver", availability: "device-only", notes: null },
      { id: "audio.hi-res", name: "aptX Adaptive", availability: "device-only", notes: null },
      { id: "equalization.equalizer", name: "MOONDROP Link EQ", availability: "official-app", notes: null }
    ],
    audioNote: "50 mAh buds + 700 mAh case; 8 h + 48 h official.",
    gaps: ["No ANC.", "Battery layout in MOONDROP Link is inferred.", "No IP rating published."]
  }),
  tws({
    family: "LITTLE WHITE", model: "LITTLE WHITE", slug: "moondrop-little-white", releaseYear: 2023, formFactor: "neckband", app: false,
    url: "https://moondroplab.com/en/products/littlewhite/", title: "MOONDROP LITTLEWHITE Bluetooth Neckband",
    hNoAnc: 12, hCase: null, codecs: ["SBC", "AAC", "aptX", "aptX HD", "aptX Adaptive", "LHDC"], anc: false,
    features: [
      { id: "connectivity.wireless-cable", name: "Bluetooth neckband cable for IEMs", availability: "device-only", notes: "Turns wired IEMs into a wireless neckband." },
      { id: "audio.hi-res", name: "LHDC / aptX HD", availability: "device-only", notes: null }
    ],
    controls: {
      types: ["button"],
      gestures: [
        { gesture: "Inline buttons", action: "Power, volume, play/pause, track skip, calls", configurableInApp: false, notes: "Fixed inline controls." }
      ],
      physicalButtons: "Inline remote on the neckband.",
      configurableInApp: false,
      notes: "No app."
    },
    audioNote: "45.4 g neckband cable; 12 h battery; LHDC/aptX HD.",
    appOnly: [],
    gaps: ["No battery display contract: no app and no case, OS shows one combined percentage.", "No ANC.", "Connector variants (MMCX/2-pin) not differentiated on the page."]
  })
];

const out = path.join(__dirname, "moondrop.json");
fs.writeFileSync(out, JSON.stringify(models, null, 2) + "\n");
console.log("wrote", out, models.length);
