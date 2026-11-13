const fs = require("fs");
const path = require("path");

const C = "KZ";
const CS = "kz";
const NOAPP = {
  name: "No official companion app",
  platforms: [],
  packageIds: [],
  appStoreUrls: [],
  appVersionObserved: null,
  notes: "KZ does not publish a companion app for this product; all controls are on-device. Battery is the phone/OS aggregate only."
};
const S = (slug, title) => ({ url: `https://kz-audio.com/kz-${slug}.html`, type: "official-product", title, accessed: "2026-11-10" });

function rec(o) {
  const isModule = o.form === "other" || o.form === "neckband";
  const batteryNotes = o.batNote ?? (o.hCase
    ? "Official single-bud runtime plus case capacity/recharge count; no app means the case percentage is not surfaced."
    : "Official single-bud runtime and case capacity; KZ does not publish a total with case for this model.");
  return {
    company: C, companySlug: CS,
    family: o.family, model: o.model, slug: o.slug,
    generation: o.gen ?? "1", variant: o.variant ?? null,
    releaseYear: o.year ?? null,
    formFactor: o.form,
    officialApp: NOAPP,
    battery: {
      budsDisplay: o.budsDisplay ?? "combined",
      caseDisplay: "hidden",
      caseVisibility: "unknown",
      caseWhileBudsOut: "hidden",
      budsHoursAnc: o.hAnc ?? null,
      budsHoursNoAnc: o.hNoAnc ?? null,
      caseHours: o.hCase ?? null,
      chargingIndicators: o.led ?? "Case LED / phone status bar only; no numeric case percentage in an app.",
      notes: batteryNotes
    },
    anc: {
      supported: o.anc ?? null,
      modes: o.anc ? ["anc", "transparency", "off"] : [],
      adaptive: o.adaptive ?? null,
      ancDb: o.ancDb ?? null,
      transparencyDb: null,
      personalized: null,
      windNoise: o.wind ?? null,
      notes: o.ancNote ?? null
    },
    features: o.features,
    controls: o.controls ?? {
      types: isModule && o.form === "other" ? ["touch"] : ["touch"],
      gestures: o.gestures ?? [
        { gesture: "Single tap", action: "Play/pause; answer/end call", configurableInApp: false, notes: null },
        { gesture: "Double tap (left/right)", action: "Previous/next track", configurableInApp: false, notes: null },
        { gesture: "Long press", action: "Voice assistant / reject call", configurableInApp: false, notes: null },
        { gesture: "Triple tap", action: "Game mode", configurableInApp: false, notes: "Where documented on the product page." }
      ],
      physicalButtons: o.buttons ?? null,
      configurableInApp: false,
      notes: o.ctrlNote ?? "Fixed on-device controls; no app remapping."
    },
    audio: {
      eqPresets: [],
      customEq: false,
      eqBands: null,
      spatialAudio: false,
      headTracking: false,
      codecs: o.codecs ?? [],
      hiRes: o.hiRes ?? false,
      multipoint: o.multipoint ?? null,
      wearDetection: o.wear ?? false,
      notes: o.audioNote ?? null
    },
    appOnlyFeatures: [],
    featureDeltaFromPrevious: o.delta ?? [],
    sources: [S(o.path, o.title)],
    confidence: o.conf ?? "medium",
    gaps: o.gaps ?? ["No official app; battery split/case percentage behaviour is not documented.", "No ANC-on runtime published where ANC exists."],
    notes: o.notes ?? null,
    researchedAt: "2026-11-10"
  };
}

const models = [
  rec({
    family: "Carol", model: "KZ Carol", slug: "kz-carol", year: 2024, form: "tws-bud", path: "carol",
    title: "KZ Carol", hNoAnc: 6, anc: true, codecs: ["SBC", "AAC"],
    features: [
      { id: "noise-control.anc", name: "6-mic ANC with transparency and standard modes", availability: "device-only", notes: "Long-press left bud cycles noise reduction / transparency / standard." },
      { id: "controls.touch", name: "Touch controls", availability: "device-only", notes: "Play/pause, tracks, calls, voice assistant, mode switch." },
      { id: "gaming.low-latency", name: "Low-latency mode", availability: "device-only", notes: null }
    ],
    ancNote: "KZ calls it 6-mic noise cancellation; depth not published. No app.",
    audioNote: "BT 5.3, SBC/AAC only. No LDAC/aptX.",
    gaps: ["ANC depth not published.", "No app; Android shows one combined bud percentage and no case level.", "No ANC-on runtime or total-with-case figure published."]
  }),
  rec({
    family: "Carol", model: "KZ Carol Pro", slug: "kz-carol-pro", year: 2025, form: "tws-bud", path: "carol-pro",
    title: "KZ Carol Pro", anc: true, ancDb: 55, codecs: ["SBC", "AAC", "LDAC"], hNoAnc: null,
    features: [
      { id: "noise-control.anc", name: "6-mic hybrid ANC (55 dB)", availability: "device-only", notes: null },
      { id: "noise-control.transparency", name: "Transparency mode", availability: "device-only", notes: null },
      { id: "audio.hi-res", name: "LDAC", availability: "device-only", notes: null },
      { id: "gaming.low-latency", name: "55 ms low-latency mode", availability: "device-only", notes: null }
    ],
    batNote: "KZ quotes 35 h total with case but does not split bud vs case hours on the page.",
    ancNote: "55 dB depth is KZ's claim. Three ANC modes (ANC/transparency/standard).",
    audioNote: "BT 5.4, LDAC/AAC/SBC.",
    gaps: ["Bud vs case runtime split is not published; 35 h is a total.", "No app; OS shows one combined percentage.", "LDAC runtime not published."]
  }),
  rec({
    family: "Krio", model: "KZ Krio", slug: "kz-krio", year: 2025, form: "tws-bud", path: "krio",
    title: "KZ Krio", anc: true, ancDb: 55, codecs: ["SBC", "AAC", "aptX", "aptX Adaptive", "LDAC"],
    features: [
      { id: "noise-control.anc", name: "6-mic hybrid ANC (55 dB)", availability: "device-only", notes: "ANC / transparency / standard modes." },
      { id: "audio.hi-res", name: "LDAC and aptX Adaptive", availability: "device-only", notes: null },
      { id: "gaming.low-latency", name: "55 ms low-latency mode", availability: "device-only", notes: null },
      { id: "calls.6-mic", name: "6-mic call noise cancellation with wind resistance", availability: "device-only", notes: null }
    ],
    batNote: "KZ quotes 32 h total with case; bud/case split not published.",
    ancNote: "55 dB hybrid ANC; three modes.",
    audioNote: "BT 5.3. LDAC, aptX Adaptive, aptX, AAC, SBC.",
    gaps: ["Bud vs case runtime split not published.", "No app; OS aggregate only.", "No ANC-on runtime."]
  }),
  rec({
    family: "Sora", model: "KZ Sora", slug: "kz-sora", year: 2025, form: "tws-bud", path: "sora",
    title: "KZ Sora", hNoAnc: 5, hCase: 20, anc: false, codecs: ["SBC", "AAC"],
    features: [
      { id: "controls.touch", name: "Touch controls", availability: "device-only", notes: "Play/pause, tracks, calls, assistant, high-performance/standard mode." },
      { id: "gaming.low-latency", name: "High-performance low-latency mode", availability: "device-only", notes: null }
    ],
    audioNote: "BT 5.4, SBC/AAC. 5 h buds + 25 h total.",
    gaps: ["No ANC.", "No app; OS aggregate only.", "No LDAC/aptX."]
  }),
  rec({
    family: "XTRA", model: "KZ XTRA", slug: "kz-xtra", year: 2024, form: "tws-bud", path: "xtra",
    title: "KZ XTRA", anc: true, ancDb: 55, codecs: ["SBC", "AAC", "aptX Adaptive"],
    features: [
      { id: "noise-control.anc", name: "55 dB hybrid ANC", availability: "device-only", notes: "KZ comparison table lists 55 dB noise-cancellation depth." },
      { id: "audio.hi-res", name: "aptX Adaptive / Snapdragon Sound", availability: "device-only", notes: null },
      { id: "gaming.low-latency", name: "55 ms low-latency mode", availability: "device-only", notes: null },
      { id: "calls.cvc", name: "CVC HD call noise cancellation", availability: "device-only", notes: null }
    ],
    batNote: "KZ quotes 32 h total with case; bud/case split not published.",
    ancNote: "Qualcomm QCC3091, 55 dB ANC claim.",
    audioNote: "BT 5.4, AAC/SBC/aptX Adaptive, 24-bit/96 kHz claim.",
    gaps: ["Bud vs case runtime split not published.", "No app; OS aggregate only.", "ANC-on runtime not published."]
  }),
  rec({
    family: "XTRA", model: "KZ XTRA Pro", slug: "kz-xtra-pro", year: 2025, form: "tws-bud", path: "xtra-pro",
    title: "KZ XTRA Pro", hNoAnc: 8, hCase: 32, anc: true,
    features: [
      { id: "noise-control.anc", name: "6-mic dual-feed ANC", availability: "device-only", notes: "Dual-core DSP + 6-mic beamforming." },
      { id: "audio.hi-res", name: "LDAC and aptX Adaptive", availability: "device-only", notes: null },
      { id: "gaming.low-latency", name: "Low-latency mode", availability: "device-only", notes: null },
      { id: "power.quick-charge", name: "10-minute quick charge", availability: "device-only", notes: null }
    ],
    batNote: "KZ quotes 8+ h single charge and 40 h total with case; 8 h and 32 h case are the implied split.",
    ancNote: "Qualcomm QCC5181, 6-mic ANC; depth not published.",
    audioNote: "BT 5.4, LDAC/AAC/SBC/aptX Adaptive.",
    gaps: ["ANC depth and ANC-on runtime not published.", "No app; OS aggregate only.", "8 h bud runtime is KZ's 'more than 8 h' claim, rounded."]
  }),
  rec({
    family: "VXS", model: "KZ VXS", slug: "kz-vxs", year: 2021, form: "tws-bud", path: "vxs",
    title: "KZ VXS", hNoAnc: 5, anc: false, codecs: [],
    features: [
      { id: "controls.touch", name: "Touch controls", availability: "device-only", notes: "Single/double/long/triple tap map documented." },
      { id: "gaming.low-latency", name: "Gaming mode", availability: "device-only", notes: "Triple-tap right bud." },
      { id: "connectivity.dual-host", name: "Dual-host design", availability: "device-only", notes: "Each bud has an independent chip." }
    ],
    audioNote: "BT 5.2, QCC3040. Codec list is not published on the page.",
    gaps: ["Codec list not published.", "No ANC.", "No app; case has no percentage."]
  }),
  rec({
    family: "VXS", model: "KZ VXS Pro", slug: "kz-vxs-pro", year: 2023, form: "tws-bud", path: "vxs-pro",
    title: "KZ VXS Pro", hNoAnc: 8, anc: false, codecs: ["SBC", "AAC", "aptX", "aptX HD", "aptX Adaptive"],
    features: [
      { id: "audio.hi-res", name: "aptX Adaptive / aptX HD", availability: "device-only", notes: null },
      { id: "connectivity.dual-host", name: "Dual-host design", availability: "device-only", notes: null },
      { id: "gaming.low-latency", name: "Gaming mode", availability: "device-only", notes: "Triple tap." }
    ],
    audioNote: "BT 5.3, QCC5171. 8 h buds, 400 mAh case (~4 recharges).",
    gaps: ["No ANC.", "Total with case not published.", "No app."]
  }),
  rec({
    family: "SA08", model: "KZ SA08", slug: "kz-sa08", year: 2020, form: "tws-bud", path: "sa08",
    title: "KZ SA08", hNoAnc: null, anc: false, codecs: [],
    features: [
      { id: "driver.8ba", name: "8 balanced-armature drivers", availability: "device-only", notes: "4 BA per side (22955 + 29689 + 31736)." },
      { id: "controls.touch", name: "Touch controls", availability: "device-only", notes: null }
    ],
    audioNote: "BT 5.0; 30 mAh per bud, 400 mAh case. Codecs not published.",
    gaps: ["Runtime and codec list not published.", "No ANC.", "No app."]
  }),
  rec({
    family: "SA08", model: "KZ SA08 Pro", slug: "kz-sa08-pro", year: 2021, form: "tws-bud", path: "sa08-pro",
    title: "KZ SA08 Pro", anc: false, codecs: ["SBC", "AAC", "aptX"], hNoAnc: null,
    features: [
      { id: "driver.8ba", name: "8 balanced-armature drivers", availability: "device-only", notes: null },
      { id: "audio.aptx", name: "aptX", availability: "device-only", notes: null },
      { id: "gaming.low-latency", name: "Gaming mode", availability: "device-only", notes: "Triple tap." },
      { id: "controls.touch", name: "Touch controls", availability: "device-only", notes: null }
    ],
    batNote: "KZ quotes 18 h running time; it is unclear whether this is buds-only or with case.",
    audioNote: "BT 5.2, QCC3040, 8 BA. 18 h claim.",
    gaps: ["18 h is not split between buds and case.", "No ANC.", "No app."]
  }),
  rec({
    family: "SK10", model: "KZ SK10", slug: "kz-sk10", year: 2021, form: "tws-bud", path: "sk10",
    title: "KZ SK10", hNoAnc: 6.5, anc: false, codecs: [],
    features: [
      { id: "driver.hybrid", name: "1 BA + 1 DD hybrid", availability: "device-only", notes: null },
      { id: "controls.touch", name: "Touch controls", availability: "device-only", notes: null }
    ],
    audioNote: "BT 5.2; 48 mAh buds, 400 mAh case. Codecs not published.",
    gaps: ["Codec list not published.", "No ANC.", "No app."]
  }),
  rec({
    family: "SK10", model: "KZ SK10 Pro", slug: "kz-sk10-pro", year: 2022, form: "tws-bud", path: "sk10-pro",
    title: "KZ SK10 Pro", hNoAnc: 6, anc: false, codecs: ["SBC", "AAC", "aptX"],
    features: [
      { id: "driver.hybrid", name: "1 BA + 1 DD hybrid", availability: "device-only", notes: null },
      { id: "audio.aptx", name: "aptX", availability: "device-only", notes: null },
      { id: "gaming.low-latency", name: "Game / normal mode", availability: "device-only", notes: null }
    ],
    audioNote: "BT 5.2, QCC3040. 6 h buds, 400 mAh case (~3 charges).",
    gaps: ["No ANC.", "Total with case not published.", "No app."]
  }),
  rec({
    family: "SKS", model: "KZ SKS", slug: "kz-sks", year: 2021, form: "tws-bud", path: "sks",
    title: "KZ SKS", anc: false, codecs: [],
    features: [
      { id: "driver.hybrid", name: "10 mm dual-magnet DD + 30019 BA", availability: "device-only", notes: null },
      { id: "connectivity.independent-buds", name: "Independent left/right use", availability: "device-only", notes: null }
    ],
    audioNote: "BT 5.2; 400 mAh case. Runtime and codecs not published.",
    gaps: ["Runtime and codec list not published.", "No ANC.", "No app."]
  }),
  rec({
    family: "Z3", model: "KZ Z3", slug: "kz-z3", year: 2021, form: "tws-bud", path: "z3",
    title: "KZ Z3", anc: false, codecs: [],
    features: [
      { id: "driver.hybrid", name: "10 mm dual-magnet DD + 30019 BA", availability: "device-only", notes: null },
      { id: "controls.touch", name: "Touch controls", availability: "device-only", notes: null }
    ],
    audioNote: "BT 5.2; 300 mAh case. Runtime and codecs not published.",
    gaps: ["Runtime and codec list not published.", "No ANC.", "No app."]
  }),
  rec({
    family: "Z1", model: "KZ Z1", slug: "kz-z1", year: 2020, form: "tws-bud", path: "z1",
    title: "KZ Z1", anc: false, codecs: [],
    features: [
      { id: "driver.xun", name: "10 mm dual-magnet XUN dynamic driver", availability: "device-only", notes: null },
      { id: "controls.touch", name: "Touch controls", availability: "device-only", notes: null }
    ],
    audioNote: "BT 5.0; 30 mAh buds, 400 mAh case. Runtime and codecs not published.",
    gaps: ["Runtime and codec list not published.", "No ANC.", "No app."]
  }),
  rec({
    family: "Z1", model: "KZ Z1 Pro", slug: "kz-z1-pro", year: 2021, form: "tws-bud", path: "z1-pro",
    title: "KZ Z1 Pro", anc: false, codecs: [],
    features: [
      { id: "controls.touch", name: "Touch controls", availability: "device-only", notes: null }
    ],
    audioNote: "BT 5.2; 28 mAh buds, 400 mAh case. Runtime and codecs not published.",
    gaps: ["Runtime and codec list not published.", "No ANC.", "No app."]
  }),
  rec({
    family: "E10", model: "KZ E10", slug: "kz-e10", year: 2020, form: "tws-bud", path: "e10",
    title: "KZ E10", hNoAnc: 4, anc: false, codecs: ["SBC", "AAC"],
    features: [
      { id: "driver.10-unit", name: "10-unit hybrid array", availability: "device-only", notes: "10 mm DD + BAs in a 6-layer PCB design." },
      { id: "controls.touch", name: "Touch controls", availability: "device-only", notes: null }
    ],
    audioNote: "BT 5.0, QCC3020. 3-5 h buds, 1100 mAh case; ~100 h standby.",
    gaps: ["No ANC.", "No app.", "Case total runtime not published (only capacity)."]
  }),
  rec({
    family: "S1", model: "KZ S1", slug: "kz-s1", year: 2020, form: "tws-bud", path: "s1-s1d",
    title: "KZ S1 / S1D", hNoAnc: 3, anc: false, codecs: [],
    features: [
      { id: "driver.hybrid", name: "Hybrid driver (S1)", availability: "device-only", notes: "S1 uses a hybrid configuration; S1D uses a single dynamic driver." },
      { id: "controls.touch", name: "Touch controls", availability: "device-only", notes: null }
    ],
    audioNote: "BT 5.0; 40 mAh buds, 300 mAh case.",
    gaps: ["Codec list not published.", "No ANC.", "S1 and S1D share one product page; exact driver complement per variant is only partly documented."]
  }),
  rec({
    family: "S1", model: "KZ S1D", slug: "kz-s1d", year: 2020, form: "tws-bud", path: "s1-s1d", variant: "S1D",
    title: "KZ S1 / S1D", hNoAnc: 3, anc: false, codecs: [],
    features: [
      { id: "driver.dynamic", name: "Single dynamic driver (S1D)", availability: "device-only", notes: "'Small steel gun' dynamic unit per KZ." },
      { id: "controls.touch", name: "Touch controls", availability: "device-only", notes: null }
    ],
    audioNote: "BT 5.0; 40 mAh buds, 300 mAh case.",
    gaps: ["Codec list not published.", "No ANC.", "Shares a product page with S1; variant-specific tuning not documented."]
  }),
  rec({
    family: "S2", model: "KZ S2", slug: "kz-s2", year: 2020, form: "tws-bud", path: "s2",
    title: "KZ S2", hNoAnc: 3, anc: false, codecs: [],
    features: [
      { id: "driver.dynamic", name: "Third-generation dual-magnet dynamic driver", availability: "device-only", notes: null },
      { id: "controls.touch", name: "Touch controls", availability: "device-only", notes: null }
    ],
    audioNote: "BT 5.0; 40 mAh buds, 400 mAh case; >100 h case standby.",
    gaps: ["Codec list not published.", "No ANC.", "No app."]
  }),
  rec({
    family: "BTE", model: "KZ BTE", slug: "kz-bte", year: 2020, form: "neckband", path: "bte",
    title: "KZ BTE", hNoAnc: 8, anc: false, codecs: ["SBC", "AAC"],
    features: [
      { id: "form.neckband", name: "Neckband-style wireless earphone", availability: "device-only", notes: "130 mAh battery, wire-control pod." },
      { id: "controls.inline", name: "Inline wire controls", availability: "device-only", notes: "Play/pause, volume, tracks, calls, sound mode." }
    ],
    controls: {
      types: ["button"],
      gestures: [
        { gesture: "► single press", action: "Play/pause; answer call", configurableInApp: false, notes: null },
        { gesture: "+ / − short press", action: "Volume up/down", configurableInApp: false, notes: null },
        { gesture: "+ / − long press", action: "Next/previous track", configurableInApp: false, notes: null },
        { gesture: "+ and − together", action: "Switch bass/standard sound mode", configurableInApp: false, notes: null }
      ],
      physicalButtons: "Inline play/pause and volume buttons.",
      configurableInApp: false,
      notes: "No app."
    },
    audioNote: "BT 5.0-class chipset, AAC. 130 mAh, 8 h playback, 100 h standby.",
    batNote: "Single battery in the neckband; no case. 8 h official, 100 h standby.",
    gaps: ["Bluetooth version not stated in the spec block.", "No ANC.", "No app."]
  }),
  rec({
    family: "H10", model: "KZ H10", slug: "kz-h10", year: 2022, form: "over-ear", path: "h10",
    title: "KZ H10", hNoAnc: 30, hAnc: 25, anc: true, codecs: ["SBC", "AAC"],
    features: [
      { id: "noise-control.anc", name: "ANC mode", availability: "device-only", notes: "25 h in ANC mode vs 30 h standard." },
      { id: "connectivity.wired", name: "3.5 mm wired input", availability: "device-only", notes: "Wireless and wired headset." },
      { id: "controls.buttons", name: "Physical controls", availability: "device-only", notes: null }
    ],
    controls: {
      types: ["button"],
      gestures: [
        { gesture: "Play/pause button", action: "Play/pause; answer/end call; double-click mode", configurableInApp: false, notes: null },
        { gesture: "Volume + / −", action: "Volume; long press track skip", configurableInApp: false, notes: null }
      ],
      physicalButtons: "Play/pause, volume +, volume −, 3.5 mm jack.",
      configurableInApp: false,
      notes: "No app."
    },
    audioNote: "BT 5.0; 500 mAh; 30 h standard / 25 h ANC.",
    batNote: "Single headphone battery; no case. 30 h standard / 25 h ANC.",
    gaps: ["ANC depth not published.", "Codec list limited to SBC/AAC.", "No app."]
  }),
  rec({
    family: "T10", model: "KZ T10", slug: "kz-t10", year: 2021, form: "over-ear", path: "t10",
    title: "KZ T10", hNoAnc: 38, anc: true, ancDb: 25, codecs: ["SBC", "AAC"],
    features: [
      { id: "noise-control.anc", name: "ANC (>25 dB)", availability: "device-only", notes: null },
      { id: "connectivity.wired", name: "3.5 mm wired input", availability: "device-only", notes: null },
      { id: "controls.buttons", name: "Physical controls", availability: "device-only", notes: null }
    ],
    controls: {
      types: ["button"],
      gestures: [{ gesture: "Physical buttons", action: "Power, volume, play/pause, calls", configurableInApp: false, notes: null }],
      physicalButtons: "On-ear cup buttons.",
      configurableInApp: false,
      notes: "No app."
    },
    audioNote: "BT 5.0; 800 mAh; 38 h playback, 60 h standby.",
    batNote: "Single headphone battery; no case. 38 h official.",
    gaps: ["Codec list limited to SBC/AAC.", "No app.", "ANC-on runtime not separately published."]
  }),
  rec({
    family: "VX10", model: "KZ VX10", slug: "kz-vx10", year: 2021, form: "tws-bud", path: "vx10",
    title: "KZ VX10", hNoAnc: 4, hCase: 20, anc: false, codecs: ["SBC", "AAC"],
    features: [
      { id: "driver.hybrid", name: "1 DD + 4 BA hybrid", availability: "device-only", notes: null },
      { id: "controls.touch", name: "Touch controls", availability: "device-only", notes: null },
      { id: "gaming.low-latency", name: "Game mode", availability: "device-only", notes: "Triple tap." }
    ],
    audioNote: "BT 5.2; 35 mAh buds, 400 mAh case; 4 h + 20 h.",
    gaps: ["No ANC.", "No app.", "No LDAC/aptX."]
  }),

  // Ear-hook / cable modules
  rec({
    family: "AZ09", model: "KZ AZ09", slug: "kz-az09", year: 2021, form: "other", path: "az09",
    title: "KZ AZ09 Ear hooks", anc: false, codecs: ["SBC", "AAC"],
    features: [
      { id: "connectivity.wireless-cable", name: "TWS ear-hook adapter for IEMs", availability: "device-only", notes: "C-pin connector; sold without earphones." },
      { id: "controls.touch", name: "Touch controls on hooks", availability: "device-only", notes: null }
    ],
    batNote: "50 mAh per hook, 800 mAh case; runtime not published.",
    audioNote: "BT 5.2; 4.5 g per hook.",
    gaps: ["Runtime not published.", "Codec list not explicit; SBC/AAC presumed from the chip class.", "No app."]
  }),
  rec({
    family: "AZ09", model: "KZ AZ09 Pro", slug: "kz-az09-pro", year: 2022, form: "other", path: "az09-pro",
    title: "KZ AZ09 Pro Ear hooks", anc: false, codecs: ["SBC", "AAC"],
    features: [
      { id: "connectivity.wireless-cable", name: "TWS ear-hook adapter for IEMs", availability: "device-only", notes: "C-pin; 4.5 g per hook." },
      { id: "controls.button", name: "One-click button controls", availability: "device-only", notes: null }
    ],
    controls: {
      types: ["button"],
      gestures: [
        { gesture: "Single click", action: "Play/pause; answer/end call", configurableInApp: false, notes: null },
        { gesture: "Double click (left/right)", action: "Previous/next track", configurableInApp: false, notes: null },
        { gesture: "Long press", action: "Voice assistant; reject call", configurableInApp: false, notes: null }
      ],
      physicalButtons: "Button on each ear hook.",
      configurableInApp: false,
      notes: "No app."
    },
    batNote: "50 mAh per hook, 800 mAh case; runtime not published.",
    gaps: ["Runtime not published.", "No app.", "Codec list not explicit."]
  }),
  rec({
    family: "AZ10", model: "KZ AZ10", slug: "kz-az10", year: 2022, form: "other", path: "az10",
    title: "KZ AZ10 Ear hooks", hNoAnc: 6, anc: false, codecs: ["SBC", "AAC"],
    features: [
      { id: "connectivity.wireless-cable", name: "TWS ear-hook adapter for IEMs", availability: "device-only", notes: "C-pin; 4.5 g per hook." },
      { id: "controls.touch", name: "Touch controls", availability: "device-only", notes: null }
    ],
    batNote: "6 h per hook; 50 mAh per hook, 800 mAh case (~9 charges).",
    audioNote: "BT 5.2; C-pin.",
    gaps: ["No ANC.", "No app.", "Codec list not explicit."]
  }),
  rec({
    family: "AZ15", model: "KZ AZ15", slug: "kz-az15", year: 2022, form: "other", path: "az15",
    title: "KZ AZ15 Ear hooks", anc: false, codecs: ["SBC", "AAC", "aptX"],
    features: [
      { id: "connectivity.wireless-cable", name: "TWS ear-hook adapter for IEMs", availability: "device-only", notes: "QCC3046." },
      { id: "controls.touch", name: "Touch controls", availability: "device-only", notes: null }
    ],
    batNote: "50 mAh per hook, 800 mAh case; runtime not published.",
    audioNote: "BT 5.2, QCC3046, aptX/AAC/SBC.",
    gaps: ["Runtime not published.", "No ANC.", "No app."]
  }),
  rec({
    family: "AZ20", model: "KZ AZ20", slug: "kz-az20", year: 2023, form: "other", path: "az20",
    title: "KZ AZ20 Ear hooks", hNoAnc: 6, anc: false, codecs: ["SBC", "AAC"],
    features: [
      { id: "connectivity.wireless-cable", name: "TWS ear-hook adapter for IEMs", availability: "device-only", notes: null },
      { id: "controls.touch", name: "Touch controls", availability: "device-only", notes: null }
    ],
    batNote: "6 h per hook; 35 mAh per hook, 800 mAh case (~9 charges).",
    audioNote: "BT 5.3.",
    gaps: ["No ANC.", "No app.", "Codec list not explicit."]
  }),
  rec({
    family: "XZ10", model: "KZ XZ10", slug: "kz-xz10", year: 2023, form: "other", path: "xz10",
    title: "KZ XZ10 Ear hooks", hNoAnc: 12, anc: false, codecs: ["SBC", "AAC", "aptX"],
    features: [
      { id: "connectivity.wireless-cable", name: "TWS ear-hook adapter for IEMs", availability: "device-only", notes: "C-pin; QCC5171." },
      { id: "audio.aptx", name: "aptX", availability: "device-only", notes: null },
      { id: "controls.button", name: "Button controls", availability: "device-only", notes: null }
    ],
    controls: {
      types: ["button"],
      gestures: [
        { gesture: "Single click", action: "Play/pause; answer/end call", configurableInApp: false, notes: null },
        { gesture: "Double click", action: "Previous/next track", configurableInApp: false, notes: null },
        { gesture: "Long press", action: "Voice assistant; reject call", configurableInApp: false, notes: null }
      ],
      physicalButtons: "Button on each ear hook.",
      configurableInApp: false,
      notes: "No app."
    },
    batNote: "12 h per hook; 50 mAh per hook, 800 mAh case.",
    audioNote: "BT 5.3, QCC5171, SBC/AAC/aptX.",
    gaps: ["No ANC.", "No app.", "No LDAC."]
  }),
  rec({
    family: "AN01", model: "KZ AN01", slug: "kz-an01", year: 2024, form: "other", path: "an01",
    title: "KZ AN01 Ear hooks", hNoAnc: 8, anc: true, codecs: ["SBC", "AAC"],
    features: [
      { id: "connectivity.wireless-cable", name: "TWS ear-hook adapter for IEMs", availability: "device-only", notes: null },
      { id: "noise-control.anc", name: "ANC / transparency modes", availability: "device-only", notes: "Long-press left hook cycles transparency/ANC." },
      { id: "gaming.low-latency", name: "Game / standard modes", availability: "device-only", notes: null }
    ],
    batNote: "8 h per hook; 50 mAh per hook, 400 mAh case.",
    ancNote: "Rare ANC ear-hook adapter. Depth not published.",
    audioNote: "BT 5.4.",
    gaps: ["ANC depth not published.", "No app.", "Codec list not explicit."]
  }),
  rec({
    family: "AE01", model: "KZ AE01", slug: "kz-ae01", year: 2024, form: "other", path: "ae01",
    title: "KZ AE01 Ear hooks", hNoAnc: 6, anc: false, codecs: ["SBC", "AAC"],
    features: [
      { id: "connectivity.wireless-cable", name: "TWS ear-hook adapter for IEMs", availability: "device-only", notes: "0.75 mm 2-pin." },
      { id: "controls.button", name: "Button controls", availability: "device-only", notes: null }
    ],
    controls: {
      types: ["button"],
      gestures: [
        { gesture: "Single click", action: "Play/pause; answer/end call", configurableInApp: false, notes: null },
        { gesture: "Double click", action: "Previous/next track", configurableInApp: false, notes: null },
        { gesture: "Long press", action: "Voice assistant; reject call", configurableInApp: false, notes: null }
      ],
      physicalButtons: "Button on each ear hook.",
      configurableInApp: false,
      notes: "No app."
    },
    batNote: "6 h per hook; 50 mAh per hook, 400 mAh case.",
    audioNote: "BT 5.4, 0.75 mm 2-pin.",
    gaps: ["No ANC.", "No app.", "Codec list not explicit."]
  }),
  rec({
    family: "AE01", model: "KZ AE01 Pro", slug: "kz-ae01-pro", year: 2023, form: "other", path: "ae01-pro",
    title: "KZ AE01 Pro Ear hooks", hNoAnc: 9, anc: false, codecs: ["SBC", "AAC"],
    features: [
      { id: "connectivity.wireless-cable", name: "TWS ear-hook adapter for IEMs", availability: "device-only", notes: null },
      { id: "controls.button", name: "Button controls", availability: "device-only", notes: null }
    ],
    controls: {
      types: ["button"],
      gestures: [
        { gesture: "Single / double / triple click", action: "Play/pause, tracks, game mode", configurableInApp: false, notes: null },
        { gesture: "Long press", action: "Full-power mode; voice assistant; reject", configurableInApp: false, notes: null }
      ],
      physicalButtons: "Button on each ear hook.",
      configurableInApp: false,
      notes: "No app."
    },
    batNote: "9 h per hook; 50 mAh per hook, 400 mAh case.",
    audioNote: "BT 5.2.",
    gaps: ["No ANC.", "No app.", "Codec list not explicit."]
  }),
  rec({
    family: "Bluetooth Cable", model: "KZ Bluetooth 5.0 Cable", slug: "kz-bluetooth-5-0-cable", year: 2020, form: "neckband", path: "bluetooth-50-cable",
    title: "KZ Bluetooth 5.0 Cable", anc: false, codecs: ["SBC", "AAC"],
    features: [
      { id: "connectivity.wireless-cable", name: "Bluetooth upgrade cable for IEMs", availability: "device-only", notes: "MMCX/2-pin variants; inline remote." },
      { id: "calls.cvc", name: "CVC noise-cancelling microphone", availability: "device-only", notes: null }
    ],
    controls: {
      types: ["button"],
      gestures: [{ gesture: "Inline buttons", action: "Power, volume, play/pause, calls", configurableInApp: false, notes: null }],
      physicalButtons: "Inline remote.",
      configurableInApp: false,
      notes: "No app."
    },
    batNote: "No battery capacity or runtime published on the page.",
    audioNote: "BT 5.0, AAC-LC, 576 kbps claim. CSR-class chipset.",
    gaps: ["Battery capacity and runtime not published.", "No ANC.", "No app."]
  }),
  rec({
    family: "Bluetooth Cable", model: "KZ Bluetooth 4.2 Cable", slug: "kz-bluetooth-4-2-cable", year: 2019, form: "neckband", path: "bluetooth-42-cable",
    title: "KZ Bluetooth 4.2 Cable", anc: false, codecs: ["SBC", "AAC"],
    features: [
      { id: "connectivity.wireless-cable", name: "Bluetooth upgrade cable for IEMs", availability: "device-only", notes: "Legacy 4.2 cable; still listed." }
    ],
    controls: {
      types: ["button"],
      gestures: [{ gesture: "Inline buttons", action: "Power, volume, play/pause, calls", configurableInApp: false, notes: null }],
      physicalButtons: "Inline remote.",
      configurableInApp: false,
      notes: "No app."
    },
    batNote: "Legacy product; battery specs not published on the current page.",
    audioNote: "Bluetooth 4.2.",
    gaps: ["Battery and codec details not published.", "Legacy product; availability varies.", "No app."]
  })
];

const out = path.join(__dirname, "kz.json");
fs.writeFileSync(out, JSON.stringify(models, null, 2) + "\n");
console.log("wrote", out, models.length);
