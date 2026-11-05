# AirPods 5 — device research dossier

**Status.** Second catalogue dossier, in the same shape as the AirPods Pro 3 note. This one is the regular AirPods, not AirPods Pro.

**Retrieved.** 5 November 2026 (Asia/Kolkata).

**Which generation.** AirPods 4 (2024) is the previous regular model. AirPods 5 was announced on 9 September 2026 and reached stores on 18 September 2026, so this note is AirPods 5. AirPods 4 appears only where Apple or a lab compares against it.

**Two versions, not an ANC split.** Both versions have Active Noise Cancellation. The split is the case. AirPods 5 at $129 comes with a USB-C charging case. AirPods 5 with Wireless Charging Case at $149 adds battery life, a speaker in the case, wireless charging, and volume swipe on the stem. The earbuds are not interchangeable between those cases.

**Photographs.** The PDF opens with three product photographs from Apple, each in the same kind of rounded frame: the pair and the closed wireless charging case from [apple.com/airpods-5](https://www.apple.com/airpods-5/), and the speaker-mesh close-up from the Apple Store gallery. Files: `images/airpods-5-pair.png`, `images/airpods-5-case.png`, and `images/airpods-5-mesh.png`.

**Parts.** The pages after the opening use Apple’s photographs and Apple’s interior drawings. A thin line runs from the label to the part, and the line does not cover it. A black opening Apple shows and does not name is left unnamed. The millimetre figures are Apple’s published overall sizes. Each earbud, either version, is 30.2 × 18.3 × 18.1 mm. Either case is 46.2 mm high, 50.1 mm wide, and 21.2 mm deep. Files: `images/airpods-5-plate-earbud.png`, `airpods-5-plate-mesh.png`, `airpods-5-plate-stem.png`, `airpods-5-plate-driver.png`, `airpods-5-plate-inside.png`, and `airpods-5-plate-case.png`.

**Figures.** The designed charts are in `AirPods-5-research.pdf` beside this note. Frequency plots use a logarithmic frequency axis. Blue is ANC. Stone is the open fit with ANC off. There is no foam seal on this model, so stone does not mean a tip.

**Scope.** This file records what Apple publishes about AirPods 5, plus one independent lab curve that Apple does not publish. It is catalogue research. It is not a measurement made on hardware in this repository, and it does not change the Phase 42 rule that an Android app cannot set these features through a supported Apple API. The stem controls themselves are on the earbud, so a person can still press the stem when the phone is not an iPhone.

## How to read a decibel in this paper

Two different decibel numbers appear below. They are not interchangeable, and neither is Apple’s “50 percent” sentence.

| Figure | What it measures | Source |
|---|---|---|
| “Up to 50 percent more” | Apple’s comparison of noise removed versus AirPods 4 with Active Noise Cancellation. Not converted to decibels here. | Apple Newsroom, 9 September 2026, and the 9A350 firmware note |
| Best-in-class open-ear ANC | A separate claim. Tested to IEC 60268-24 in July 2026. | Apple Newsroom footnote 1 |
| Lab attenuation and frequency response | One fixture measurement (B&K 5128) of the wireless-case model: outside noise blocked with ANC off versus ANC on, and playback balance against that lab’s preference curve. | SoundGuys product page, retrieved 5 November 2026 |

Apple does not publish a frequency-response curve, a driver diameter, or an ANC attenuation curve in decibels versus frequency. This revision did not find an Apple hearing-protection datasheet for AirPods 5. Where a number is absent, this dossier says so.

A doubling of acoustic **power** is 3 dB. A four-times change in power is 6 dB. A sound that seems about half as loud to a listener is closer to 10 dB. Fifty percent more noise removed is not the same kind of number as either of those, and this note does not rewrite it as decibels.

## 1. Identity

| Field | Value | Evidence |
|---|---|---|
| Product | AirPods 5, the regular open-ear AirPods, 2026 | Apple Newsroom and tech specs |
| Announced | 9 September 2026 | Apple Newsroom |
| In stores | 18 September 2026 | Apple Newsroom |
| US price at announcement | $129 with the USB-C case. $149 with the Wireless Charging Case | Apple Newsroom |
| Chip in each earbud | Apple H2 | Apple tech specs, both versions |
| Bluetooth | 5.3 | Apple tech specs |
| Ingress | IP57, earbuds and charging case, IEC 60529 | Apple tech specs footnote. Sweat and water during non-water sport. Not permanent. Do not charge while wet. Not a swim rating |
| Each earbud, both versions | 30.2 mm high, 18.3 mm wide, 18.1 mm deep, 4.3 g | Apple tech specs |
| USB-C case | 46.2 mm high, 50.1 mm wide, 21.2 mm deep, 32.3 g | Apple tech specs, AirPods 5 |
| Wireless charging case | Same outer size, 34.7 g, and it has a speaker | Apple tech specs, AirPods 5 with Wireless Charging Case |
| Ear tips | None. Open-ear fit | The product has no tip in the box list or on the photographs |
| Cable | Not in the published in-the-box list. The list is the earbuds, the case, and documentation | Apple tech specs |
| Earbud model numbers, USB-C version | A3531, A3532, A3533 | Apple Support, “Identify your AirPods”, published 25 September 2026 |
| Case, USB-C version | A3530. USB-C on the bottom, status light on the front. No speaker listed | Same page |
| Earbud model numbers, wireless-case version | A3439, A3440, A3441 | Same page |
| Case, wireless version | A3529. USB-C, Apple Watch charger, and Qi. Speaker on the bottom. Status light on the front | Same page |
| Left and right assignment | Apple lists three model numbers for each version and does not say which body is which. The photographs print L and R on the stems | Not assigned here |
| Case compatibility | Each case fits only its own earbuds. The USB-C case does not fit the wireless-case earbuds, and the reverse is also true | Apple Support, “Identify your AirPods” |
| Current firmware, Apple’s list | 9A350, both versions | Apple Support, “About firmware updates for AirPods”. The page’s published date in the copy read here is 17 September 2026 |

IP57 means dust-protected (the “5”) and protected against temporary immersion (the “7”, 1 metre for 30 minutes under the IEC lab test).

The product photographs and the part plates are Apple’s, from apple.com/airpods-5 and the Apple Store gallery. Each label sits outside the photograph, and a thin line points at the part. The driver diameter is still unpublished.

## 2. Acoustic hardware

Apple’s published audio hardware, the same list on both versions, without numbers Apple does not give:

- Custom high-excursion Apple driver. Diameter, impedance, and sensitivity are not published.
- Custom high-dynamic-range amplifier.
- Apple H2. Apple’s interior drawing prints “H2” on the chip in the stem.
- Active Noise Cancellation, Adaptive Audio, Transparency, Conversation Awareness, Voice Isolation, Personalized Spatial Audio with dynamic head tracking, Adaptive EQ, and studio-quality audio recording.
- A vent system for pressure equalization.
- A new multiport acoustic architecture, described as inspired by AirPods Pro 3 and re-engineered for this open ear. Apple does not publish port dimensions.
- No silicone tip and no foam. The passive block is whatever the open shape gives, which the lab curve shows is close to nothing under a few hundred hertz.

There is no published frequency-response window such as “20 Hz to 20 kHz” from Apple. The lab curve in section 5 is the only frequency plot in this dossier, and it is relative to someone else’s target, not an Apple specification.

## 3. Listening modes

The user guide names Transparency, Adaptive Audio, Active Noise Cancellation, and Off for AirPods 5. Press and hold on the stem cycles the listening modes. Either stem can be set to that action, or to Siri, in Settings.

### Active Noise Cancellation

Both versions have it. That is the change from AirPods 4, where ANC was a separate, more expensive model.

Apple’s comparison, in the 9 September 2026 newsroom and again in the 9A350 firmware note: up to 50 percent more external noise removed than AirPods 4 with Active Noise Cancellation. The newsroom credits a new multiport acoustic architecture and improved computational audio. This note does not convert that sentence into decibels.

A different sentence, with its own footnote, calls the ANC best in class for an open ear. Testing was by Apple in July 2026, AirPods 5 paired with iPhone 17, prerelease firmware and iOS 27, noise reduction in accordance with IEC 60268-24, against the best-selling wireless open-ear headphones on sale at the time. Apple does not publish that curve.

There is no foam tip. With the electronics off, or the battery empty, the only reduction left is the open shape. The lab sweep in section 5 is the picture of that.

### Transparency

Apple describes an improved Transparency mode that more naturally represents voices and nearby sounds. No decibel curve is published for it.

### Adaptive Audio

Adaptive Audio blends Transparency and Active Noise Cancellation from the noise around the listener. The user guide says the blend can be pushed toward more Transparency or more Noise Cancellation with a slider in Control Center. Apple does not publish the slider’s decibel range.

### Conversation Awareness and Voice Isolation

Both are on the spec list. Conversation Awareness lowers media volume when the wearer starts speaking. Apple does not publish how many decibels that duck is. Voice Isolation is listed for calls. No attenuation figure is published.

### Off

The listening-mode guide includes Off. This note does not add a condition for when Off appears in the menu, because the guide’s “enable Off first” sentence is written for AirPods Pro 2 and AirPods Pro 3, not for AirPods 5.

## 4. Independent lab curve

SoundGuys measured AirPods 5 with Wireless Charging Case on a B&K 5128. The page was read on 5 November 2026. These decibels are that fixture, not Apple’s 50 percent claim, and not a hearing-protection rating. The reviewers say an unsealed earbud is hard to measure twice the same way, because fit moves the result. Take the curve as one seating of one pair.

Attenuation is how many decibels of outside noise the fixture no longer sees. Higher is more blocking. A number near zero is an open ear. A small negative means that point measured slightly louder than the open fixture, which at the bottom of the range is the noise of the measurement as much as a feature.

### ANC off, the open fit

| Hz | dB | Hz | dB | Hz | dB |
|---|---|---|---|---|---|
| 21 | 2.1 | 325 | −0.8 | 3070 | 14.6 |
| 26 | −0.3 | 410 | −0.9 | 3870 | 10.4 |
| 33 | 0.0 | 516 | −1.2 | 4870 | 11.2 |
| 41 | 0.0 | 613 | −1.9 | 6130 | 17.7 |
| 52 | 0.0 | 772 | −2.2 | 7720 | 34.1 |
| 65 | −0.1 | 972 | −2.8 | 9720 | 15.1 |
| 82 | −0.1 | 1220 | −2.8 | 12200 | 5.7 |
| 103 | −0.1 | 1540 | −1.4 | 15400 | 9.3 |
| 130 | −0.1 | 1940 | 4.1 | 19400 | 19.3 |
| 163 | −0.2 | 2440 | 11.5 | | |
| 205 | −0.3 | | | | |
| 259 | −0.5 | | | | |

Under about 200 Hz the open fit is within a few tenths of a decibel of nothing. The shape itself starts to block above roughly 2 kHz. The highest point on this sweep with ANC off is 34.1 dB at 7720 Hz.

### ANC on

| Hz | dB | Hz | dB | Hz | dB |
|---|---|---|---|---|---|
| 21 | 2.0 | 325 | 10.9 | 3070 | 29.9 |
| 26 | −2.2 | 410 | 13.3 | 3870 | 22.1 |
| 33 | −0.9 | 516 | 14.7 | 4870 | 16.6 |
| 41 | −0.7 | 613 | 15.7 | 6130 | 19.9 |
| 52 | −0.4 | 772 | 17.1 | 7720 | 25.8 |
| 65 | 0.5 | 972 | 19.2 | 9720 | 15.7 |
| 82 | 1.7 | 1220 | 21.5 | 12200 | 12.9 |
| 103 | 3.9 | 1540 | 18.3 | 15400 | 10.7 |
| 130 | 7.8 | 1940 | 19.8 | 19400 | 14.9 |
| 163 | 7.9 | 2440 | 27.8 | | |
| 205 | 9.3 | | | | |
| 259 | 9.4 | | | | |

The deepest ANC-on point on this sweep is 29.9 dB at 3070 Hz. At 7720 Hz the open fit alone (34.1 dB) measured higher than ANC on (25.8 dB). Cancellation is not an increase at every frequency.

SoundGuys’ own summary of perceived loudness is a 59 percent reduction with ANC, against 53 percent for AirPods 4 with ANC. That six-point gap is their metric. It is not Apple’s “up to 50 percent more.”

## 5. Playback balance, and the equalizer

Same lab, same model, decibels relative to the SoundGuys preference curve. Above zero is louder than their target. Below zero is quieter. This is not an absolute sound-pressure level.

| Hz | dB | Hz | dB | Hz | dB |
|---|---|---|---|---|---|
| 20 | −23.2 | 250 | −0.2 | 2500 | 3.3 |
| 25 | −15.0 | 315 | 0.1 | 3150 | 5.2 |
| 32 | −8.4 | 400 | 0.3 | 4000 | 6.6 |
| 40 | −4.1 | 500 | −0.1 | 5000 | 5.7 |
| 50 | −1.9 | 630 | −0.2 | 6300 | 2.8 |
| 63 | −0.3 | 800 | 0.2 | 8000 | 0.8 |
| 80 | 0.9 | 1000 | −0.1 | 10000 | −3.3 |
| 100 | 0.2 | 1250 | 0.2 | 12500 | −2.8 |
| 125 | −0.6 | 1600 | 0.1 | 16000 | −9.2 |
| 160 | −0.3 | 2000 | 2.6 | 20000 | −22.1 |
| 200 | −0.1 | | | | |

From about 80 Hz to 1.6 kHz the curve is within about 1 dB of the target. The open fit gives up the bass: −8.4 dB at 32 Hz, −23.2 dB at 20 Hz. The largest lift on the sweep is +6.6 dB at 4 kHz. The top end falls to −9.2 dB at 16 kHz and −22.1 dB at 20 kHz.

### Adaptive EQ, and Custom EQ

Adaptive EQ is on the spec list. It is automatic. The inward-facing microphone is the sensor Apple lists for looking at what reaches the ear. This note does not invent a sample rate or a band count for that loop.

Custom EQ is separate. Apple Support, “Customize headphone audio levels”, says a three-band equalizer is available on AirPods 4 or later, AirPods Pro 2 or later, and AirPods Max 2. The path is Settings, the AirPods, Audio & Routing, Equalizer, Custom. The sliders are Low, Mid, and High. The setting is stored on the earbuds and follows them to another paired Apple device. Recommended restores Apple’s profile. Apple does not publish the decibel range, the center frequencies, or Q. There is no 10-band equalizer in that control.

## 6. Microphones and the other sensors

Apple’s sensor list, both versions:

- Dual beamforming microphones
- Inward-facing microphone
- Optical in-ear sensor
- Motion-detecting accelerometer
- Speech-detecting accelerometer
- Force sensor
- Force sensor with volume swipe, listed only for AirPods 5 with Wireless Charging Case

That is three microphone channels per earbud: the outward pair, shared by voice and by the noise the canceller listens to, and one inward microphone, shared by what the ear actually hears and by Adaptive EQ. Six channels across the pair. The speech-detecting accelerometer is not a microphone. The optical in-ear sensor is not a heart-rate sensor. Heart rate is not on this spec page.

The pair caption calls the grille the interior acoustic speaker mesh, and it calls the silver end of the stem the charging connector. A separate close-up on the same page is captioned “external stem microphone.” Apple does not publish a drawing that separates those two jobs at the end of the stem, so this note does not invent a second hole. The black opening on the inner face is visible in the photographs and is not named in their captions. It stays unnamed, even though an optical in-ear sensor is on the sensor list.

## 7. Controls

From Apple’s tech specs and the AirPods user guide.

### Gestures that are fixed in the default list

| Gesture | Result |
|---|---|
| Press once | Play or pause. Answer a call. Mute or unmute. Camera remote, with the latest firmware on a compatible device |
| Press twice | Skip forward. End a call |
| Press three times | Skip back |
| Press and hold | Switch among Active Noise Cancellation, Transparency, and Adaptive Audio |
| Swipe up or down | Volume. AirPods 5 with Wireless Charging Case only |
| Press and hold both stems | Start Live Translation |
| “Siri” or “Hey Siri” | The phone’s assistant |

Mute and end-call can be swapped, so one press ends the call and two presses mute, or the default the other way. The other control follows automatically.

### Gestures that can be reassigned

Press and hold, per stem. Settings, the AirPods, Press and Hold AirPods, Left or Right. The guide’s choices for this generation are the listening-mode cycle, or Siri. One stem can be modes and the other can be Siri.

Volume swipe is described only as swipe up or down. Apple does not publish a decibel step.

### The case

The USB-C case charges from USB-C. The wireless charging case charges from USB-C, an Apple Watch charger, or a Qi-certified charger. The charge guide lists MagSafe for other cases, and it does not list either AirPods 5 case under MagSafe.

The wireless case has a speaker on the bottom for charging and battery status, and for Find My. Find My with that speaker requires iOS 27 or later. The USB-C case’s identify entry does not mention a speaker.

The status light is on the front of both cases. This note does not specify a tap pattern for pairing or a factory reset, because those steps were not on the pages read for this revision.

### One earbud

The listening-mode guide says to wear one or both. The microphone setting Automatically Switch uses whichever earbud is available, and a single earbud becomes the microphone. Always Left and Always Right keep that choice even if that earbud is out of the ear or in the case. This note does not claim that Active Noise Cancellation keeps running on one earbud, because that sentence was not in the AirPods 5 pages read here.

### Hands-free, beyond the stem

Nod yes or shake no answers Siri’s question about a call, a message, or a notification. Announce Calls and Announce Notifications have to be on. Siri AI, below, is the phone’s assistant. The earbud does not run that model.

## 8. Speech, text, and what is actually decoded

Live Translation is the speech-to-text path Apple describes. It needs Apple Intelligence turned on, the latest system, the latest firmware, and it is in beta. Setup is in the Translate app, after the languages are downloaded. Start it by holding both stems, by saying “Siri, start Live Translation”, or with the Action button on iPhone. The wearer hears the other person’s speech in the language they chose. Apple says it is not available in every language or region. This note does not copy another model’s language list onto AirPods 5.

Siri, and “Hey Siri”, are the phone’s voice assistant. Siri AI is a host feature rolling out in beta with iOS 27, macOS 27, and watchOS 27, on an Apple Intelligence device, in English first, with more languages named for later. Apple says it will not initially be available in the EU on iOS, iPadOS, and watchOS, and it is not available to users under 13. Hands-free access is described as working on all AirPods. The decoder is on the phone, not in the earbud.

Studio-quality audio recording is on the spec list. Apple does not publish the sample rate or the bit depth.

Announce Calls and Announce Notifications are the phone reading text aloud. They are also the switch that enables the nod and the shake.

## 9. Battery, case, and charging

Apple’s test, July and August 2026, preproduction earbuds and preproduction iPhone 18 Pro, prerelease software, 358 iTunes tracks at 256 kbps AAC, volume at 50 percent. Listening time is until the first earbud stops. Spatial Audio and Conversation Awareness were off.

| Condition | AirPods 5 | AirPods 5 with Wireless Charging Case |
|---|---|---|
| ANC on, one charge | Up to 4 hours | Up to 5 hours |
| Noise control off, one charge | Up to 6 hours | Up to 7 hours |
| ANC on, with the case | Up to 20 hours | Up to 22 hours |
| 5 minutes in the case | About 1 hour, noise control off | About 1 hour, noise control off |

The wireless-case model’s 5 hours with ANC is the figure Apple compares with AirPods 4 with ANC, as one hour longer. The case totals are recharges, so they are not drawn on the same axis as the single-charge hours. Apple does not publish a noise-control-off total with the case, a talk-time figure, or a Transparency-only battery figure in the pages read here.

## 10. Connectivity

Bluetooth 5.3. The spec page says AirPods can be used as wireless headphones with non-Apple devices, and that functionality may be limited. Standard Bluetooth audio is the path that works. The stem is on the earbud, so presses still happen. Writing Active Noise Cancellation, Custom EQ, or the press-and-hold assignment from an Android app is not a supported Apple API. This dossier does not change that.

Personalized Spatial Audio needs an iPhone with a TrueDepth camera to build the profile, and it is not available when the buds are used with other devices. The profile syncs across Apple devices on a current system.

## 11. Feature inventory

| Feature | On AirPods 5 | Where it lives |
|---|---|---|
| Active Noise Cancellation | Both versions | H2, both stems can cycle it |
| Transparency | Both versions | Same cycle |
| Adaptive Audio | Both versions, plus a slider toward either mode | H2, and Control Center |
| Conversation Awareness | Both versions | Lowers media when the wearer speaks. No published duck |
| Voice Isolation | Both versions | Calls. No published figure |
| Adaptive EQ | Both versions | Automatic. Inward microphone |
| Custom EQ | AirPods 4 or later, so both versions, on iOS 27 | Three sliders. Range unpublished |
| Personalized Spatial Audio | Both versions | Profile from a TrueDepth iPhone |
| Volume swipe | Wireless-case version only | Stem |
| Live Translation | Both versions, beta, Apple Intelligence | Phone, started from both stems |
| Siri AI | Host feature, beta, English first | Phone. Hands-free from the buds |
| Studio-quality recording | Listed | Sample rate unpublished |
| Find My speaker | Wireless charging case only | Needs iOS 27 or later |
| Optical in-ear sensor | Both versions | Not a heart-rate sensor |
| Hearing Aid, heart rate | Not on the spec page | Not claimed |
| MagSafe | Not listed for either case | USB-C, and Qi plus Apple Watch charger on the wireless case only |

## 12. What this dossier will not claim

- A decibel value for “50 percent more”, or a curve for the IEC 60268-24 test.
- An ANSI hearing-protection total, an NRR, or an octave-band seal table. None was found for this model.
- Driver diameter, impedance, sensitivity, a sample rate, or a bit depth.
- A ± range, a center frequency, or a Q for the three Custom EQ sliders.
- That the black opening is the optical sensor. It might be. Apple’s caption does not say so.
- A separate hole for the charging contact and the stem microphone. The captions name both jobs and do not separate them.
- Heart rate, a hearing-aid gain, or Loud Sound Reduction.
- MagSafe, ear tips, or UWB in the case.
- That one swipe is a known number of decibels.
- That an Android app can write ANC, EQ, or gestures.
- Left versus right model numbers. Apple lists three per version and does not assign them.

## Sources

- Apple Newsroom, “Apple introduces AirPods 5, featuring best-in-class open-ear Active Noise Cancellation”, 9 September 2026. https://www.apple.com/newsroom/2026/09/apple-introduces-airpods-5-with-best-in-class-open-ear-active-noise-cancellation/
- Apple Support, AirPods 5 tech specs. https://support.apple.com/en-us/148759
- Apple Support, AirPods 5 with Wireless Charging Case tech specs. https://support.apple.com/en-us/148760
- Apple Support, “Identify your AirPods”, published 25 September 2026. https://support.apple.com/en-us/109525
- Apple Support, “About firmware updates for AirPods”, 9A350, page date 17 September 2026 in the copy read here. https://support.apple.com/en-us/106340
- Apple Support, “Use controls and gestures with your AirPods”. https://support.apple.com/guide/airpods/use-controls-and-gestures-with-your-airpods-devb2c431317/web
- Apple Support, “Change settings for AirPods or AirPods Pro”. https://support.apple.com/guide/airpods/change-settings-for-airpods-or-airpods-pro-dev57e5b7e58/web
- Apple Support, “Switch between listening modes on AirPods”. https://support.apple.com/guide/airpods/switch-between-listening-modes-dev9812f5cc3/web
- Apple Support, “Charge AirPods, AirPods Pro, or AirPods Max”. https://support.apple.com/guide/airpods/charge-airpods-devde25a4bbe/web
- Apple Support, “Customize headphone audio levels”, the three-band equalizer on AirPods 4 or later. https://support.apple.com/en-us/102663
- Product photographs: apple.com/airpods-5 and the Apple Store gallery `airpods-5-202609`. Saved beside this note under `images/`
- SoundGuys, “Apple AirPods 5 with Wireless Charging Case”, B&K 5128 tables, retrieved 5 November 2026. https://www.soundguys.com/product/apple-airpods-5-with-wireless-charging-case/
