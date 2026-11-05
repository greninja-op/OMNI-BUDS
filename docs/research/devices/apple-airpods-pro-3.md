# AirPods Pro (3rd generation) — device research dossier

**Status.** Sample dossier for one model. Written so the shape can be reused for later earbuds after review.

**Retrieved.** 5 November 2026 (Asia/Kolkata).

**Photographs.** The PDF opens with three product photographs from Apple, each in the same kind of rounded frame: the hero pair on [apple.com/airpods-pro](https://www.apple.com/airpods-pro/), the open case from the Apple Store gallery, and the heart-rate close-up from the same product page. Files: `images/apple-airpods-pro-3-pair.png`, `images/apple-airpods-pro-3-case.png`, and `images/apple-airpods-pro-3-heart-rate.png`.

**Parts.** The pages after the opening use Apple’s photographs. A thin line runs from the label to the part, and the line does not cover it. A mesh Apple does not name is left unnamed. The millimetre figures are Apple’s published overall sizes: each earbud 30.9 × 19.2 × 27.0 mm, the case 47.2 × 62.2 × 21.8 mm. Files: `images/parts-earbud.png`, `parts-heart.png`, `parts-stem.png`, `parts-fit.png`, `parts-inside.png`, and `parts-case.png`.

**Figures.** The designed charts are in `AirPods-Pro-3-research.pdf` beside this note. Frequency plots use a logarithmic frequency axis. Blue is ANC, teal is Transparency, amber is Adaptive Audio, and stone is the unpowered seal.

**Scope.** This file records what Apple publishes about AirPods Pro 3, plus one independent lab curve that Apple does not publish. It is catalogue research. It is not a measurement made on hardware in this repository, and it does not change the Phase 42 rule that an Android app cannot set these features through a supported Apple API. The stem controls themselves are on the earbud, so a person can still change listening mode with a press even when the phone is not an iPhone.

## How to read a decibel in this paper

Three different decibel numbers appear below. They are not interchangeable.

| Figure | What it measures | Source |
|---|---|---|
| Hearing Protection total attenuation | Estimated real-world protection in sustained noise, as a single number for a whole noise environment. Given as a range: most users, then proficient users. | Apple, ANSI/ASA S12.68, datasheet dated September 2025 |
| Passive octave-band means | How much the seal blocks, earbud powered off, at one octave frequency. | Same Apple datasheet, ANSI S3.19-1974 |
| Lab attenuation and frequency response | One fixture measurement (B&K 5128) of playback balance, and of outside noise blocked with ANC off versus ANC on. | SoundGuys product page, retrieved 5 November 2026 |
| RTINGS category averages | One number per band or per noise scene: overall, bass, mids, treble, airplane, office, street. | A public October 2025 summary of an RTINGS review. The review page itself is behind a login, so these figures are cited as secondary |

Apple does not publish a frequency-response curve, a driver diameter, a microphone part count beyond the sensor list, or an ANC attenuation curve in decibels versus frequency. Where a number is absent, this dossier says so. It does not fill the gap with a typical value.

A doubling of acoustic **power** is 3 dB. A four-times change in power is 6 dB. A sound that seems about half as loud to a listener is closer to 10 dB. Apple’s “up to 2× more noise removed than AirPods Pro 2” is a power-style claim from an IEC 60268-24 test, not a promise that the world sounds half as loud.

## 1. Identity

| Field | Value | Evidence |
|---|---|---|
| Product | AirPods Pro 3 (3rd generation of AirPods Pro) | Apple tech specs, year introduced 2025 |
| Announced | 9 September 2025 | Apple Newsroom |
| In stores | 19 September 2025 | Apple Newsroom |
| US price at announcement | $249 | Apple Newsroom and the Apple Store page |
| Chip in each earbud | Apple H2 | Apple tech specs |
| Chip in the case | Second-generation Ultra Wideband | Apple tech specs |
| Bluetooth | 5.3 | Apple tech specs |
| Ingress | IP57, earbuds and charging case, IEC 60529 | Apple tech specs |
| Each earbud | 30.9 mm high, 19.2 mm wide, 27.0 mm deep, 5.55 g | Apple tech specs |
| Case | 47.2 mm high, 62.2 mm wide, 21.8 mm deep, 43.99 g | Apple tech specs |
| Ear tips in the box | Foam-infused silicone, five sizes: XXS, XS, S, M, L | Apple tech specs and Newsroom |
| Cable | USB-C charge cable is sold separately | Apple tech specs |
| Earbud model numbers | A3063, A3064, A3065 | Apple Support, “Identify your AirPods” |
| Case model number | A3122. FCC IDs on the regulatory sheet are BCG-A3063, BCG-A3064, and BCG-A3122 | Apple regulatory compliance PDF |
| Left and right assignment | Model tables commonly list A3064 as the left earbud and A3063 as the right. Apple’s identify page lists A3065 with them and does not say which body it is | Apple Wiki model table and a retailer lookup. A3065 is not assigned here |
| Retail order number | MFHP4, and MFHP4HN/A on one India listing | Apple Wiki, and Croma’s product page. A code written MFH4HN/A is missing a character |
| Current firmware, Apple’s list | 9A348 | Apple Support, “About firmware updates for AirPods”, read with this revision. Build 8B30 was a December 2025 update, not the current build |
| What A3047 and A3048 are | AirPods Pro 2 with the USB-C case, together with case A3049 or, on other lists, A2968 | Apple Support, “Identify your AirPods”. They are the previous generation |

IP57 means dust-protected (the “5”) and protected against temporary immersion (the “7”, 1 metre for 30 minutes under the IEC lab test). Apple says the rating is for sweat and water during non-water sport, it is not permanent, and the case must not be charged while wet. It is not a swim rating.

The product photographs and the part plates are Apple’s, from apple.com/airpods-pro and the Apple Store. Each label sits outside the photograph, and a thin line points at the part. The driver diameter is still unpublished.

## 2. Acoustic hardware

Apple’s published audio hardware, without numbers Apple does not give:

- Custom high-excursion Apple driver. Diameter, impedance, and sensitivity are not published. A wireless bud’s driver is not wired out, so impedance is not a user-facing spec.
- Custom high-dynamic-range amplifier.
- Apple H2 runs the listening modes, Adaptive EQ, and the computational audio features.
- Multiport acoustic architecture. Apple’s description is that it controls the airflow that carries sound into the ear. An inward-facing microphone looks into the ear canal.
- A vent equalizes pressure, so the seal does not leave a plugged-ear feeling.
- Foam-infused tips add passive isolation on top of the silicone shell. XXS is the size added for this generation.

There is no published frequency-response window such as “20 Hz to 20 kHz” from Apple. The lab curve in section 6 is the only frequency plot in this dossier, and it is relative to someone else’s target, not an Apple specification.

## 3. Listening modes

Four noise-control states exist. Apple’s settings guide names Active Noise Cancellation, Transparency, Adaptive Audio, and Off.

### Active Noise Cancellation

Outside microphones hear the noise heading toward the ear. The H2 builds an opposing sound so the two meet and drop. The inward microphone hears what is left inside the canal and trims the cancellation. That pair of paths is feedforward plus feedback. The foam tip is the passive part: it blocks sound before any electronics run, and it is the only protection left when the battery is empty or the mode is Off.

Apple’s marketing claim, from a July 2025 IEC 60268-24 test on an iPhone 16 with prerelease firmware and iOS 26: up to 2× more noise removed than AirPods Pro 2, and up to 4× more than the original AirPods Pro. The same test was used for “world’s best in-ear ANC” against the best-selling wireless in-ear headphones then on sale. Apple does not publish the curve from that test.

The hearing-protection sheet gives the decibel figure Apple is willing to print for ANC in sustained noise. Across 90, 95, 100, 105, and 110 dBA, estimated total attenuation is **26 to 32 dB**. The low end is what most users (about 80 percent) can expect to reach or beat. The high end is what proficient users (about 20 percent) can expect. Fit changes the result. Noise dominated by frequencies above 2000 Hz can come out lower than that single number. The feature is not for impulse sounds (gunfire, fireworks, jackhammers) and not for sustained sound louder than 110 dBA.

### Transparency

Transparency plays the outside world back into the ear, shaped so voices and the wearer’s own voice stay natural instead of muffled by the seal. It is not the same thing as ANC Off. Off is the raw seal. Transparency is a processed feed from the outside microphones.

On the same hearing-protection sheet, Transparency’s estimated total attenuation **rises as the room gets louder**, because Loud Sound Reduction turns protection up:

| Environmental noise | Total attenuation | Protected exposure limit (7 days) | Unprotected limit |
|---|---|---|---|
| 90 dBA | 4–9 dB | 10–32 hours | 4 hours |
| 95 dBA | 8–14 dB | 8–32 hours | 1 hour 15 minutes |
| 100 dBA | 13–18 dB | 8–25 hours | 24 minutes |
| 105 dBA | 17–21 dB | 6–16 hours | 8 minutes |
| 110 dBA | 20–25 dB | 4–13 hours | 2.5 minutes |

With the Hearing Aid feature on, Transparency attenuation can be lower once the environment is at or above 95 dB. If Loud Sound Reduction is switched off in Accessibility, Transparency and Adaptive Audio may provide no reduction at all.

Apple does not publish a frequency-response curve of Transparency mode. A chart of “transparency versus frequency” would be invented, so it is not in this dossier.

### Adaptive Audio

Adaptive Audio moves between cancellation and transparency as the environment changes. The hearing-protection figures below are the default setting. Apple notes that customization changes them.

| Environmental noise | Total attenuation | Protected exposure limit (7 days) |
|---|---|---|
| 90 dBA | 19–24 dB | unlimited |
| 95 dBA | 22–27 dB | unlimited |
| 100 dBA | 25–30 dB | 126 hours to unlimited |
| 105 dBA | 26–31 dB | 50–159 hours |
| 110 dBA | 26–31 dB | 16–50 hours |

### Off, and the passive seal

Off, or a dead battery, disables Hearing Protection. The only number that then applies is the passive Noise Reduction Rating: **12 dB**. Apple’s worked example: 92 dBA at the ear, minus 12 dB, leaves about 80 dB. Canada’s CSA class for that passive performance is **C**. The passive NRR does not apply in Transparency, Adaptive Audio, or ANC.

Real-ear passive means with the battery out, ANSI S3.19-1974, ten subjects summarized as mean and standard deviation:

| Octave band | 125 Hz | 250 Hz | 500 Hz | 1 kHz | 2 kHz | 4 kHz | 8 kHz |
|---|---|---|---|---|---|---|---|
| Mean attenuation | 10 dB | 9 dB | 13 dB | 18 dB | 26 dB | 32 dB | 32 dB |
| Standard deviation | 3 dB | 2 dB | 3 dB | 3 dB | 3 dB | 4 dB | 5 dB |

The seal does little at 125–250 Hz and much more from 2 kHz up. That is why ANC exists: the electronics have to do the work the foam cannot do in the bass.

### Modes that sit on top of the four states

| Feature | What it does | Where it lives |
|---|---|---|
| Conversation Awareness | When the wearer starts speaking, media volume drops and the mode opens toward transparency so the conversation is audible. It does not open because someone else spoke first. It does not transcribe the conversation. | On-device detection, toggled in the AirPods audio settings. iOS 18 or later with Pro 2 and later |
| Personalized Volume | Learns a preferred loudness and adjusts for the room. | Same generation requirement as Conversation Awareness |
| Loud Sound Reduction | The protection layer inside Transparency and Adaptive Audio. On by default whenever a listening mode is on. Can be disabled, and then those two modes may not reduce noise. | Accessibility setting |
| Personalized Spatial Audio with dynamic head tracking | Renders a positioned soundfield and updates it from head motion. A personal profile needs an iPhone with a TrueDepth camera and then syncs to other Apple devices. Not available on non-Apple devices. | H2 plus the motion accelerometer. Content must be supported; not all audio is Dolby Atmos |
| Adaptive EQ | Continuously retunes the driver, especially the bass, from what the inward microphone actually hears in the canal. Compensates for a loose or tight seal. It is not a preset the user edits. | Always part of playback. No user sliders |

## 4. The only official per-frequency decibel table

The octave-band table in section 3 is Apple’s per-frequency data. It is passive and unpowered. There is no official table of ANC decibels at each frequency.

Reading it: at 125 Hz the mean seal is 10 dB, so a 90 dB rumble is still about 80 dB before ANC. At 4 kHz and 8 kHz the mean seal is 32 dB, which is already in the same neighborhood as Apple’s active hearing-protection total. ANC’s job is the low end.

## 5. Independent lab curve — attenuation versus frequency

Source: SoundGuys, measured on a B&K 5128, retrieved 5 November 2026 from their AirPods Pro 3rd Gen product page. One published curve. Not an Apple specification, and not a measurement made for this repository. Their own summary rollups on that page are passive isolation **20 dB**, ANC depth **30 dB**, and a **V-shaped** signature. The rows below are the curve, which is more informative than the rollup.

Higher means more outside sound blocked. “ANC off” is the seal on that fixture. “ANC on” is the seal plus cancellation.

| Hz | ANC off (dB) | ANC on (dB) |
|---|---|---|
| 21 | −0.6 | 2.3 |
| 26 | 0.9 | 14.8 |
| 33 | 0.0 | 16.6 |
| 41 | 0.8 | 23.9 |
| 52 | −0.1 | 31.1 |
| 65 | 0.8 | 31.7 |
| 82 | 0.8 | 34.4 |
| 103 | 0.6 | 41.7 |
| 130 | −0.2 | 32.6 |
| 163 | 1.1 | 40.4 |
| 205 | 2.5 | 37.9 |
| 259 | 3.5 | 39.9 |
| 325 | 6.5 | 34.7 |
| 410 | 9.9 | 38.9 |
| 516 | 9.7 | 32.2 |
| 613 | 9.2 | 28.5 |
| 772 | 13.4 | 31.2 |
| 972 | 15.4 | 33.8 |
| 1220 | 17.7 | 37.3 |
| 1540 | 20.1 | 37.6 |
| 1940 | 23.6 | 43.1 |
| 2440 | 28.4 | 42.8 |
| 3070 | 37.7 | 43.5 |
| 3870 | 45.3 | 36.7 |
| 4870 | 24.3 | 23.9 |
| 6130 | 26.6 | 29.0 |
| 7720 | 27.7 | 30.6 |
| 9720 | 38.9 | 36.1 |
| 12200 | 32.5 | 27.3 |
| 15400 | 42.0 | 37.4 |
| 19400 | 26.1 | 27.4 |

What the table supports:

- Below about 200 Hz the seal does almost nothing (about 0 to 3 dB). ANC is doing essentially all of the work. Around 100 Hz the published ANC-on point is 41.7 dB against 0.6 dB passive.
- The deepest ANC-on point in the table is **43.5 dB at 3070 Hz**.
- From roughly 4 kHz upward the seal is already strong, and at several points ANC on measures **less** attenuation than ANC off (3870 Hz: 36.7 versus 45.3; 15400 Hz: 37.4 versus 42.0). Cancellation is not uniformly “more blocking” across the whole spectrum.
- This curve is a different test from Apple’s 26–32 dB hearing-protection total. A single-number rating and a fixture sweep are not expected to match.

SoundGuys did not publish a Transparency transfer curve on the page retrieved. Their short spec line says the ambient mode is adaptive transparency, and lists modes as Off, ANC, and transparency. Apple’s own list also includes Adaptive Audio as a distinct mode.

A public October 2025 summary of an RTINGS review gives category averages, not a frequency curve. The review page is behind a login, so the numbers below are a secondary citation. They are charted in the PDF on their own figure, apart from the SoundGuys sweep.

| RTINGS category, as cited | AirPods Pro 3 | AirPods Pro 2, same summary |
|---|---|---|
| Overall | 28.69 dB | 24.48 dB |
| Bass | 23.84 dB | 20.83 dB |
| Mids | 32.38 dB | 26.68 dB |
| Treble | 30.51 dB | 26.47 dB |
| Airplane | 26.78 dB | 23.48 dB |
| Office | 24.95 dB | 21.63 dB |
| Street | 30.01 dB | 25.52 dB |

The overall step from 24.48 dB to 28.69 dB is 4.21 dB, about 2.6 times the acoustic power. That sits near Apple’s “up to 2×” power claim. It is not the same test as Apple’s 26–32 dB hearing-protection band, and it is not the SoundGuys sweep, whose deepest point is 43.5 dB at 3.1 kHz.

## 6. Playback frequency balance, and the equalizer question

Apple does not publish a frequency response. There is no 10-band parametric equalizer, and Apple does not publish a Q factor or a decibel limit for any slider. What the wearer can actually set:

| Control | Who sets it | What it changes |
|---|---|---|
| Custom EQ | The user, on iPhone, iPad, or Mac | Three bands only: Low, Mid, and High. Settings, the AirPods name, Audio & Routing, Equalizer, Custom. Stored on the earbuds and carried to other paired Apple devices. Choose Recommended to return to Apple’s profile. Added in the iOS 27 era. Apple’s guide does not publish the slider range in decibels, the center frequency of each band, or a Q |
| Adaptive EQ | The earbud, continuously | Level versus frequency to match the seal the inward mic measures. Separate from the three sliders |
| Personalized Spatial Audio | The user, once, with a TrueDepth iPhone | An ear-geometry profile for spatial rendering |
| Headphone Accommodations | The user, in Accessibility | A hearing-based playback shape. Apple exposes an audiogram (from the Hearing Test or imported) and tone choices aimed at balanced, vocal, or bright sound, plus amplification strength and left/right balance. Apple does not publish the decibel value of each step |
| Hearing Aid | The user, after a Hearing Test, mild to moderate loss, age 18 or older | Frequency-specific gain from the audiogram, with automatic Conversation Boost on this model. FDA-authorized. This is amplification for hearing loss, not a music EQ |
| iOS Music EQ | The phone’s Music settings | A playback preset on the phone (bass booster and similar). It is not stored in the earbud, and spatial rendering can take the path around it |

SoundGuys’ B&K 5128 curve is **decibels relative to the SoundGuys headphone preference curve**. A positive number means louder than their target. It is not an absolute sound-pressure level, and it is not Apple’s tuning spec.

| Hz | dB vs their target | Hz | dB vs their target |
|---|---|---|---|
| 20 | +4.3 | 800 | +0.4 |
| 25 | +4.4 | 1000 | +0.9 |
| 32 | +4.5 | 1250 | +0.5 |
| 40 | +4.3 | 1600 | +1.8 |
| 50 | +3.8 | 2000 | +4.9 |
| 63 | +3.0 | 2500 | +6.8 |
| 80 | +1.6 | 3150 | +5.1 |
| 100 | −0.2 | 4000 | +3.5 |
| 125 | −2.1 | 5000 | +6.1 |
| 160 | −2.7 | 6300 | +7.7 |
| 200 | −1.6 | 8000 | +5.1 |
| 250 | −0.9 | 10000 | −0.3 |
| 315 | −0.6 | 12500 | +2.7 |
| 400 | +0.1 | 16000 | −11.5 |
| 500 | −0.6 | 20000 | −36.6 |
| 630 | 0.0 | | |

Reading the shape, without turning it into a taste verdict:

- Sub-bass sits about 4 dB above their target, then the response falls through 160 Hz (about −2.7 dB).
- The midrange from 250 Hz to 1.2 kHz stays within about 1 dB of the target.
- There is a presence lift around 2.5 kHz (+6.8 dB) and another around 6.3 kHz (+7.7 dB). That upper lift is what a “V” description is pointing at, together with the sub-bass rise.
- Past 10 kHz the curve leaves the target: −11.5 dB at 16 kHz and −36.6 dB at 20 kHz. Energy at 20 kHz is not musically decisive for most listeners; it does mean the extreme top is not flat on this fixture.

No total-harmonic-distortion table from Apple. A Korean lab note (0db.co.kr, 21 September 2025) discusses THD and latency for one sample and says latency is similar to AirPods Pro 2. This dossier does not copy unpublished graph values from that page.

## 7. Microphones and the other sensors

Apple’s sensor list, read as the contents of each earbud, names three microphone channels plus four non-microphone sensors. A pair is therefore six microphone channels. Apple does not publish a teardown count, a capsule model, or a sample rate.

The guess that “two microphones are for ANC and the other four are for voice” does not match the published design. The outside microphones are shared. They form a beam toward the mouth for calls, and they sample outside noise for feedforward ANC. The inward microphone is also shared: feedback ANC, and Adaptive EQ.

| Sensor, each earbud | Count | Job |
|---|---|---|
| Outward beamforming microphones | 2 | Voice pickup aimed at the mouth. Feedforward ANC (the noise before it reaches the seal). Live Translation and Siri use this voice path together with the phone |
| Inward-facing microphone | 1 | Points into the ear canal. Feedback ANC (what the seal failed to stop). Adaptive EQ (what the driver actually delivered) |
| Speech-detecting accelerometer | 1 | Feels vibration from the wearer’s own voice in the jaw and skull. Helps the system tell “I am talking” from “the room is talking.” It is not a microphone and it does not turn speech into text |
| Motion-detecting accelerometer | 1 | Head motion for spatial head tracking, and the nod / shake gestures |
| Skin-detect sensor | 1 | In-ear detection for Automatic Ear Detection |
| Heart-rate sensor | 1 | Optical pulse sensor for workouts in the Fitness app and compatible apps. iOS 26 or later. Not an audio sensor |
| Force sensor with volume swipe | 1 stem | The only control surface. Covered in section 8 |

Apple’s newsroom calls the microphones “ultra-low-noise.” That is a description, not a self-noise figure in dBA.

Pair total: 6 microphone channels, 2 speech accelerometers, 2 motion accelerometers, 2 skin sensors, 2 heart-rate sensors, 2 force-sensor stems. The case adds a speaker used by Find My, a second-generation Ultra Wideband chip for precision finding, and the charging coil. The case speaker is not a voice microphone.

## 8. Controls

There is no separate mechanical button. Nothing clicks. Each stem is a force sensor: a press is detected from pressure on the stem, and a light swipe on the same stem changes volume. Apple’s name for the control is “force sensor with volume swipe.” Both stems can do every gesture. One stem can be assigned to Siri on press-and-hold while the other cycles listening modes.

### Gestures that are fixed

| Gesture | Media | Phone or FaceTime call | Other |
|---|---|---|---|
| Press once | Play or pause | Answer. Also mute or unmute | Shutter / camera remote in a compatible camera app on iOS 26 or iPadOS 26 or later |
| Press twice | Next track | End the call, in the default setup | |
| Press three times | Previous track | | |
| Swipe up or down | Volume | Volume | Not described as reassignable |
| Say “Siri” or “Hey Siri” | Playback, calls, directions, and other Siri actions | Same | Needs a paired Apple device. “Siri” without “Hey” needs the H2 (this model has it) and is language-dependent. Often needs a network |
| Nod up and down | | Accept a call, or reply to an announced notification | Only when Announce Calls and Announce Notifications are on |
| Shake side to side | | Decline a call, or dismiss the announcement | Same requirement |

### Gestures that can be reassigned

| Setting | Default | Choices |
|---|---|---|
| Press and hold, left stem and right stem | Listening mode, cycling ANC and Transparency | Listening mode, or Siri. The two stems may differ: one can be Siri and the other listening mode |
| Which modes the hold cycles | ANC and Transparency | Any two or three of ANC, Transparency, Adaptive Audio, and Off. If both stems are set to listening mode, editing one stem’s mode list changes both |
| Mute / unmute versus end call | Press once mutes. Press twice ends the call | Those two actions can be swapped |

Press-once for play/pause, press-twice for next track, and press-three-times for previous track are not described as reassignable on this model. Volume swipe is not described as reassignable. Apple does not publish a decibel step per swipe.

Under Settings, Accessibility, AirPods, the phone can change Press Speed, Press and Hold Duration, the volume of the earbuds’ own tones, and whether spatial audio follows the head or stays on the phone. Noise Cancellation with One AirPod can be turned on, so cancellation is available while only one earbud is worn. That switch does not, by itself, publish what the other ear does when the switch is left off.

The call microphone can be set to switch automatically, always the left earbud, or always the right. If it is pinned to one side, that earbud stays the microphone even after it leaves the ear.

### The case

Apple’s spec page describes the speaker, the lanyard loop, and the status light. It does not describe a pairing button. Reviews in 2026, including 9to5Mac, say the physical rear button is gone and pairing is a tap on the case. One review says the tap is on the indicator light. The exact factory-reset gesture is not in the Apple page used here, so it is not specified.

### Both stems at once

Press and hold the stem on **both** earbuds together to start Live Translation.

### One earbud out

Automatic Ear Detection is on by default. It uses the skin-detect sensor.

- Remove one earbud: playback pauses. Put it back in: playback resumes.
- Remove both: playback stops and does not resume by itself.
- Wear neither, with detection on: audio comes out of the phone or computer speakers.
- Wear one, with the other in the case: audio plays on the worn earbud.
- Turn detection off: audio keeps going to the earbuds whether or not they are in the ears, and the features that depend on detection stay off.
- Noise Cancellation with One AirPod, in Accessibility, allows ANC while a single earbud is worn.

A call can be taken with one earbud. Each bud has its own microphones, so the worn bud still has a voice path. Apple does not publish a number of seconds for the automatic resume.

### Hands-free, beyond the stem

- “Siri” / “Hey Siri” for calls, playback, and directions.
- Announce Calls speaks the caller. A nod answers, a shake declines, without touching the stem and without speaking.
- Announce Notifications speaks the notification. The same nod and shake reply or dismiss.
- Conversation Awareness lowers media when the wearer speaks, so a nearby person can be answered without pulling an earbud out.
- Live Translation is the hands-free cross-language path in section 9.
- Pause Media When Falling Asleep is a setting on Pro 3. The phone pauses audio when it decides the wearer has fallen asleep.

## 9. Speech, text, and what is actually decoded

The earbuds do not contain a general speech-to-text engine that turns everything they hear into a transcript. Several features use speech. The recognition, when there is any, runs with the paired Apple device.

| Feature | Speech in | Text or speech out | Where the decode happens |
|---|---|---|---|
| Siri wake word | “Siri” or “Hey Siri” | A chime, then the answer spoken back | Wake can be recognized with the H2 path. The request is handled on the paired Apple device. A network is often required. This is not open dictation |
| Siri answers | A spoken request | Spoken reply in the earbuds | Text-to-speech from the Apple device, played back over the buds |
| Announce Calls and Announce Notifications | None from the wearer | The phone speaks caller and notification text into the earbuds | Text-to-speech on the phone. Nod and shake answer without speech |
| Live Translation | The other person’s speech, and the wearer’s speech | Translated speech in the wearer’s ear. The wearer’s words can be shown as a live transcript on the iPhone, held so the other person can read them | Apple Intelligence on a nearby iPhone, iOS 26 or later. Beta. Not an on-bud translator |
| Conversation Awareness | Wearer’s voice activity | No text. Media ducks, mode opens | On-bud voice-activity decision from the mics and the speech accelerometer |
| Voice Isolation | The wearer’s voice on a call | Cleaner uplink audio. No transcript | Noise rejection in the voice path so the far end hears less of the room |
| Hearing Aid and Conversation Boost | The room, especially speech | Louder, clearer sound in the ear. No transcript | Frequency gain from the audiogram. Boost emphasizes speech. Clinical feature, not a captioner |
| Live Listen | The iPhone microphone | That audio in the earbuds | The phone is the remote microphone. No transcription is part of the feature |
| Studio-quality audio recording | The wearer’s voice | A recording in the phone’s apps. Sample rate and bit depth are still unpublished | Firmware notes for 8A356 name the Camera app, Voice Memos, and dictation in Messages |
| Workout Buddy | None required from the wearer | Spoken encouragement, milestones, and a summary after the workout | Apple Intelligence on the iPhone, iOS 27 or later, English or Spanish. It uses Fitness history and the current workout. It is text-to-speech from the phone, not speech-to-text in the earbud |

Live Translation languages on the Apple Store page retrieved 5 November 2026: Chinese (Mandarin, Simplified), Chinese (Mandarin, Traditional), English (UK and US), French (France), German (Germany), Italian (Italy), Japanese (Japan), Korean (South Korea), Portuguese (Brazil), and Spanish (Spain). At announcement on 9 September 2025 the shipping set was English, French, German, Portuguese, and Spanish, with Italian, Japanese, Korean, and simplified Chinese described as coming later. Availability is still regional, and the feature is marked beta.

Siri AI is a separate host feature. A current Apple Store footnote describes hands-free Siri AI as an iOS 27, macOS 27, and watchOS 27 beta that works on all AirPods, starting in English, French, Japanese, Korean, Portuguese, and Spanish, and not initially in the EU. That is phone software, not a decoder inside this earbud.

Nothing in the published feature list captions a podcast, a song, or a meeting into a text file stored on the earbuds. Apple does not describe the recognizer as Whisper, and it does not publish a sample rate of 48 kHz for the cancellation loop.

Workout Buddy, from Apple’s Fitness guide, covers outdoor and indoor run and walk, outdoor and indoor cycle, hiking, elliptical, stair stepper, HIIT, and functional or traditional strength training. A MacRumors report says Pro 3 can use it without an Apple Watch because the earbud measures heart rate. Apple’s own guide describes the spoken cues as pace, distance, duration, milestones, and a closing summary. It does not publish a heart-rate-zone script.

## 10. Battery, case, and charging

Test conditions, unless noted: Apple, July and August 2025, preproduction buds and case, preproduction iPhone 17 Pro, prerelease software, 256 kbps AAC playlist, volume at 50 percent. “Up to” means the first bud to stop.

| Condition | Time |
|---|---|
| ANC on | 8 hours |
| Spatial Audio and head tracking on | 7.5 hours |
| Heart-rate sensing during an outdoor walk, Transparency, music playing | 6.5 hours |
| Transparency with Hearing Aid on, phone not playing audio, volume and amplification at 50 percent | 10 hours |
| ANC on, buds plus case, repeated discharge | 24 hours |
| 5 minutes in the case, then play with ANC | about 1 hour |

Case charging: MagSafe, Apple Watch charger, a Qi-certified charger, or USB-C. Apple’s spec page does not say Qi2. The case has a speaker for Find My and a lanyard loop. The tech-specs footnote for this model says Find My functionality requires iOS 26 or later. A later multi-product store page uses different footnote numbers for other models; the Pro 3 tech-specs page is the one used here.

## 11. Connectivity

| Topic | What is published |
|---|---|
| Radio | Bluetooth 5.3 |
| With an Apple device | AAC is the format Apple used in its own battery test (256 kbps AAC). Full Apple features need a current OS |
| With a non-Apple device | Apple says they work as Bluetooth headphones and functionality may be limited |
| Codecs Apple’s spec page does not list | SoundGuys lists AAC and SBC, and does not list a multipoint count. aptX and LDAC are not part of any official Pro 3 spec retrieved for this dossier |
| Automatic switching | Among Apple devices on the same Apple Account. That is not classic Bluetooth multipoint |
| From Android | Audio, calls, and the on-stem listening-mode press work. Apple settings (ANC from the phone, spatial audio, Find My, firmware, per-bud battery in an app) are not available through a supported Android API. See the Phase 42 evidence register |

Lossless listening is not an AirPods Pro 3 wireless specification on the tech-specs page. A store footnote about ultra-low-latency lossless over a wired USB-C connection belongs to other products on that shared page and is not applied to this model here.

## 12. Feature inventory

| Feature | On Pro 3 | User can edit it | Decibel or frequency data published by Apple |
|---|---|---|---|
| ANC | Yes | Choose it in the hold cycle, or from Control Center on Apple devices | 26–32 dB total attenuation in the hearing-protection test. No official curve versus frequency |
| Transparency | Yes | Same | 4–25 dB, rising with the noise level. No frequency curve |
| Adaptive Audio | Yes | Same, and customization changes the protection figures | 19–31 dB at default |
| Off | Yes | Same | Passive NRR 12 dB, plus the octave-band means |
| Adaptive EQ | Yes | No | None |
| Custom EQ, three bands | Yes, iOS 27 era, stored on the earbuds | Low, Mid, and High. Apple does not publish the range, the center frequencies, or a Q | None |
| 10-band parametric EQ | Not a product feature | — | A generated preset with one Q on every band is not a measurement of this model |
| Spatial Audio, personalized, head tracked | Yes | Profile on a TrueDepth iPhone | None |
| Conversation Awareness | Yes | Toggle | None |
| Personalized Volume | Yes | Toggle | None |
| Loud Sound Reduction | Yes, default on | Can be disabled | Covered by the Transparency and Adaptive rows |
| Voice Isolation | Yes | Call audio feature | None |
| Hearing Test, Hearing Aid, Conversation Boost | Yes, regional, 18+, mild to moderate, FDA-authorized aid | Audiogram and aid settings | Aid gain curve is personal, not a published product curve |
| Hearing Protection | Yes, regional | Tied to listening mode and Loud Sound Reduction | Sections 3 and 4 |
| Live Translation | Yes, beta, Apple Intelligence iPhone | Language | Not an acoustic spec |
| Siri, both wake phrases | Yes | Hold can also invoke Siri | — |
| Announce Calls / Notifications, nod and shake | Yes | Toggles | — |
| Camera remote | Yes, iOS 26 / iPadOS 26 | Press once in a camera app | — |
| Automatic Ear Detection | Yes | Toggle | — |
| Sleep pause | Yes | Toggle | — |
| Heart rate in workouts | Yes, iOS 26 | During a workout | — |
| In-ear fit tones / ear-tip test | Part of fitting for hearing features; Apple’s sheet tells the wearer to seat the tip until it feels snug | — | A poor seal lowers every attenuation number in this paper |
| Find My, case speaker, precision finding | Yes | — | — |
| Multipoint | Not published | — | — |
| On-bud speech-to-text of arbitrary audio | Not provided | — | — |

## 13. What this dossier will not claim

- No frequency response measured by this project.
- No driver size, THD, latency in milliseconds, or microphone self-noise.
- No per-microphone part numbers, and no claim that a teardown found more than the three channels Apple lists.
- No Transparency frequency curve. Apple’s hearing-protection table is the Transparency decibel source: 4–9 dB at 90 dBA, rising to 20–25 dB at 110 dBA.
- No 48 kHz cancellation rate, no 90 percent noise-reduction claim, and no fixed hearing-aid boost such as +6 to +14 dB. Aid gain follows the wearer’s audiogram.
- No 10-band equalizer, no Q factors, and no published decibel range for the three Custom EQ sliders.
- No statement that OmniBuds, or any Android app, can write ANC, EQ, or gestures to this model through a supported API.
- The SoundGuys rows are one lab’s published table, read on 5 November 2026. A later firmware can move a curve. The RTINGS category averages are a secondary citation. Apple’s hearing-protection rows are from the September 2025 datasheet.

## Sources

- Apple product photographs: the hero pair on [apple.com/airpods-pro](https://www.apple.com/airpods-pro/), the open-case gallery image on the Apple Store page, and the heart-rate close-up from the same product page. Saved beside this note under `images/`
- Apple, AirPods Pro 3 technical specifications, including the support mirror dated as year introduced 2025: [apple.com/airpods-pro/specs](https://www.apple.com/airpods-pro/specs/) and [support.apple.com/en-us/125135](https://support.apple.com/en-us/125135)
- Apple Newsroom, 9 September 2025, “Introducing AirPods Pro 3”
- Apple Store, AirPods Pro 3, price and the Live Translation language list as retrieved 5 November 2026
- Apple Support, “Identify your AirPods” (109525), “Use controls and gestures with your AirPods”, “Change the settings of your AirPods and AirPods Pro” (108764, published 14 September 2026), “Customize EQ on AirPods”, “Adjust the accessibility settings for AirPods”, “Use Workout Buddy”, and “About firmware updates for AirPods”
- Apple, regulatory compliance information for A3063, A3064, and A3122
- Apple, Hearing Protection data sheet for AirPods Pro 3, September 2025, regulatory file `A3063_A3064_HP_Datasheet_ANSI_MH_English`, and support article 120850
- SoundGuys, AirPods Pro 3rd Gen product page, lab tables retrieved 5 November 2026
- RTINGS category averages, cited from a public summary of the October 2025 review. The review page is behind a login, so those seven numbers are a secondary citation and are not a frequency curve
- This repository, `docs/phases/phase-42/research-and-evidence-register.md`, for the Android control boundary only
