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
| “Up to 1.5×” / “up to 50 percent more” | Apple’s comparison against AirPods 4 with Active Noise Cancellation. The compare page ties the 1.5× wording to IEC 60268-24. Not converted to decibels here. | Apple compare page and Newsroom, 9 September 2026 |
| “Up to 3×” on AirPods Pro 3 | Apple’s comparison of Pro 3 against AirPods 5, also IEC 60268-24. A different product’s claim, recorded so the two are not mixed. | Apple compare page |
| Best-in-class open-ear ANC | A separate claim. Tested to IEC 60268-24 in July 2026 against the best-selling open-ear headphones then on sale. | Apple Newsroom footnote 1 |
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

Four noise-control states exist. Apple’s settings guide names Active Noise Cancellation, Transparency, Adaptive Audio, and Off. Press and hold on the stem cycles them. Either stem can be set to that cycle, or to Siri. The guide also says the set of modes in the cycle can be changed in AirPods settings. Apple does not publish a decibel value for any step of that cycle.

### Active Noise Cancellation

Both versions have it. On AirPods 4, ANC was a separate, more expensive model. Here it is on the $129 pair and the $149 pair.

Apple’s support article on these modes describes the mechanism, and it names AirPods 5 in both versions. Outward microphones hear the noise heading toward the ear. The earbud plays an opposing sound so the two meet and drop. An inward microphone hears what is left in the ear and the earbud counters that too. That pair of paths is feedforward plus feedback. There is no foam tip, so there is no seal doing the passive part of the job. With the electronics off, or the battery empty, the only reduction left is the open shape. The lab sweep in section 5 is the picture of that.

Apple’s comparison against the previous regular model is “up to 1.5× more Active Noise Cancellation than AirPods 4 with Active Noise Cancellation,” tested to IEC 60268-24. The newsroom says the same thing as “up to 50 percent more.” Those are one claim in two wordings. The newsroom credits a new multiport acoustic architecture and improved computational audio. This note does not turn 1.5× or 50 percent into decibels. A doubling of acoustic power is 3 dB. A sound that seems about half as loud is closer to 10 dB. Fifty percent more noise removed is not either of those, and Apple does not publish the curve.

A second sentence calls the result best in class for an open ear. That test was Apple, July 2026, AirPods 5 with iPhone 17, prerelease firmware and iOS 27, IEC 60268-24, against the best-selling wireless open-ear headphones then on sale. Apple does not publish that curve either.

On the same compare page, AirPods Pro 3 is listed as “up to 3× more Active Noise Cancellation than AirPods 5,” also IEC 60268-24. That is a claim about Pro 3, not a frequency curve for this model. It is here so the two products are not given each other’s number.

### Transparency

Transparency plays the outside world back into the ear. It is not the same thing as ANC Off. Off is the raw open fit. Transparency is a processed feed from the outside microphones. Apple describes it as more natural than the previous generation, so voices and nearby sounds stay intelligible. Apple does not publish a frequency-response curve of Transparency mode. A chart of “transparency versus frequency” would be invented, so it is not in this dossier.

### Adaptive Audio

Adaptive Audio blends Transparency and Active Noise Cancellation as the room changes. In Settings, under the AirPods name, Listening Mode, Adaptive, a slider pushes the blend toward more Transparency or more Noise Cancellation. The same slider is in Control Center. Apple does not publish the slider’s decibel range, and it does not publish a hearing-protection table for the default position, because this model does not have the Hearing Protection feature. See the next section.

### Conversation Awareness, Personalized Volume, and Voice Isolation

| Feature | What it does | Where it lives |
|---|---|---|
| Conversation Awareness | When the wearer starts speaking, media volume drops, and it comes back when they stop. If the audio is spoken, such as a podcast, it pauses after a few seconds. It does not open because someone else spoke first. It does not transcribe the conversation. Press and hold the stem to end it by hand. | On-device detection. AirPods 5, both versions, with a current system. Toggled under Audio & Routing. No published duck in decibels |
| Personalized Volume | Learns a preferred loudness and adjusts for the room and for how the wearer has set the volume over time. | Same models as Conversation Awareness. A toggle. No published curve |
| Voice Isolation | Clarifies the wearer’s voice on a call by reducing room noise and wind. The far end hears less of the room. No transcript. | Computational audio on the H2 path. No published attenuation |
| Loud Sound Reduction | Not on AirPods 5. Apple’s guide limits it to AirPods Pro 2, AirPods Pro 3, and AirPods Max 2. | Absent. Turning it off or on is not a control on this model |

### Off

The listening-mode guide includes Off. The controls page for AirPods 5 names the hold as switching among Active Noise Cancellation, Transparency, and Adaptive Audio, and the settings guide says which modes are in that cycle can be changed. This note does not add a condition that Off must be enabled first. That sentence in Apple’s guide is written for AirPods Pro 2 and AirPods Pro 3, not for AirPods 5.

### What is not a listening mode on this model

Hearing Protection, the Hearing Test, the Hearing Aid feature, Conversation Boost, and Loud Sound Reduction are on the compare page for AirPods Pro, not in the AirPods 5 columns, and the support article that describes loud-environment protection names Pro 2, Pro 3, and Max 2. Heart-rate sensing is the same: it is a Pro 3 row, and it is not in the AirPods 5 sensor list. This dossier does not borrow Pro 3’s 26–32 dB hearing-protection band, its 12 dB passive NRR, or its octave-band seal table. Those numbers belong to a sealed earbud.

## 4. There is no official per-frequency decibel table

AirPods Pro 3 has one official per-frequency table: the unpowered seal, octave by octave, on Apple’s hearing-protection sheet. AirPods 5 does not have that sheet. Searches of Apple’s manuals list for this model, and of the tech specs, did not turn up an ANSI hearing-protection datasheet, an NRR, a CSA class, or an octave-band mean.

So the only per-frequency decibels in this dossier are the laboratory sweep in section 5. They are one fixture’s reading of one seating. They are not a rating Apple prints, and they are not a substitute for the missing hearing-protection table.

## 5. Independent lab curve — attenuation versus frequency

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

### What this table supports

The deepest ANC-on point on this sweep is 29.9 dB at 3070 Hz. At 103 Hz, near the bottom of speech and the middle of rumble, ANC on is 3.9 dB and ANC off is −0.1 dB. The canceller is doing almost all of the low-frequency work, because the open shape is not. By 205 Hz ANC on is 9.3 dB. By 1 kHz it is 19.2 dB. By 2.4 kHz it is 27.8 dB.

At 7720 Hz the open fit alone measured 34.1 dB and ANC on measured 25.8 dB. Cancellation is not an increase at every frequency. The open shape already blocks some of the high treble, and turning the canceller on can measure lower there on this fixture.

Small negatives on the ANC-off sweep, down to −2.8 dB around 1 kHz, are plotted as measured. They are not clipped to zero. At the bottom of the range they are the noise of an unsealed measurement as much as a feature of the earbud.

SoundGuys’ own summary of perceived loudness is a 59 percent reduction with ANC, against 53 percent for AirPods 4 with ANC. That six-point gap is their metric. It is not Apple’s “up to 1.5×” or “up to 50 percent more.” Their page also gives this model an isolation score of 2.9 out of 10, an ANC score of 5.9 out of 10, an MDAQS timbre score of 3.7 out of 5, and an editorial score of 6.4 out of 10. Those are scores, not decibels.

They also note that these earbuds change balance with listening volume, which they call LDEQ, so one curve is not every volume. Fit moves an unsealed bud, so one seating is not every ear.

RTINGS has a review at rtings.com/headphones/reviews/apple/airpods-5. It is behind a paywall and was not opened for this note. Category averages quoted from other sites are not copied here. The Pro 3 dossier includes a secondary RTINGS table because those category numbers were in a public summary. This model does not get a parallel table until the numbers are read from RTINGS itself.

Apple’s 1.5× claim and Pro 3’s 3× claim are not rows in this table. They are whole-signal comparisons under IEC 60268-24, and Apple does not publish the weighting or the curve.

## 6. Playback balance, and the equalizer question

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

### What can change the balance

| Control | What it is | Who sets it | Published range |
|---|---|---|---|
| Custom EQ | Three sliders: Low, Mid, High. Path: Settings, the AirPods, Audio & Routing, Equalizer, Custom. Stored on the earbuds and follows them to another paired Apple device. Recommended restores Apple’s profile | The wearer | None. Apple does not publish ± decibels, center frequencies, or Q. There is no 10-band equalizer in this control |
| Adaptive EQ | Automatic correction for how the earbud sits, using the inward microphone. Apple’s product page calls the current version next-generation and says it tunes across a wider range of frequencies than before | The earbud | None. No sample rate and no band count |
| Personalized Spatial Audio | A head-related profile from a TrueDepth iPhone, with dynamic head tracking | The wearer, once, then it syncs | Not an equalizer. Unavailable when the buds are used with non-Apple devices |
| Headphone Accommodations | A hearing-based playback shape. On AirPods 3 or later, so both versions. An audiogram from the phone, tone choices aimed at balanced, vocal, or bright sound, plus amplification and left/right balance | The wearer, in Accessibility | Apple does not publish the decibel value of each step |
| Personalized Volume | Learns a preferred loudness and adjusts for the room | A toggle under Audio & Routing | No published curve |
| Hearing Aid gain | A clinical gain from an audiogram | Not on this model | Do not copy Pro 3’s personal-audiogram description onto AirPods 5 |
| Music EQ in the Music app | The phone’s own equalizer for Music | The phone | Separate from the three sliders stored on the earbuds |

Custom EQ is on AirPods 4 or later, so both AirPods 5 versions have it, on a current system. Apple Support, “Customize headphone audio levels”, is the source for the three sliders. The lab curve above is one seating at one volume, before any of these controls. SoundGuys’ note about loudness-dependent balance means a Custom EQ setting and a volume change can both move the curve the wearer hears, and Apple does not publish either movement in decibels.

## 7. Microphones and the other sensors

Apple’s sensor list is the same on both versions, except the volume swipe.

| Sensor | Count | Job | Shared with |
|---|---|---|---|
| Dual beamforming microphones | Two per earbud, four across the pair | Voice for calls and for the recordings Apple calls studio-quality. Also the outward ears of Active Noise Cancellation | The same pair does both. There is not a separate “call mic” and a separate “ANC mic” in the spec |
| Inward-facing microphone | One per earbud | What is left inside the ear, which the canceller counters. Also the input Adaptive EQ uses | Feedback cancellation and Adaptive EQ |
| Speech-detecting accelerometer | One per earbud | Detects that the wearer is speaking, for voice pickup and for Conversation Awareness | Not a microphone. It does not hear the room |
| Motion-detecting accelerometer | One per earbud | Head tracking for Spatial Audio, and the nod and shake | Not a microphone |
| Optical in-ear sensor | One per earbud | Knows the earbud is in an ear. That is automatic ear detection | Not a heart-rate sensor. Heart rate is not on this spec page |
| Force sensor | One per earbud | Presses | The wireless-case model adds a swipe on the same stem |
| Force sensor with volume swipe | Wireless-case model only | Volume | The $129 model does not have this gesture |

Three microphone channels per earbud, six across the pair. The outward pair is shared by voice and by feedforward cancellation. The inward microphone is shared by feedback cancellation and Adaptive EQ. Counting “six microphones” as six independent jobs would double-count.

The pair caption calls the grille the interior acoustic speaker mesh, and it calls the silver end of the stem the charging connector. A separate close-up on the same page is captioned “external stem microphone.” Apple does not publish a drawing that separates those two jobs at the end of the stem, so this note does not invent a second hole. The black opening on the inner face is visible in the photographs and is not named in their captions. It stays unnamed, even though an optical in-ear sensor is on the sensor list.

There is no skin-contact heart-rate sensor, no U1 or second-generation Ultra Wideband chip in the case, and no ear-tip fit sensor. Those are Pro 3 parts.

## 8. Controls

From Apple’s tech specs, the AirPods user guide, and the accessibility guide. There is no mechanical button. The press is a force sensor on the stem.

### Gestures

| Gesture | Media | Call | Other |
|---|---|---|---|
| Press once | Play or pause | Answer. Mute or unmute | Camera remote, with the latest firmware on a compatible iPhone or iPad |
| Press twice | Next track | End the call | |
| Press three times | Previous track | | |
| Press and hold | Cycles the listening modes | | Or Siri, if that stem is set to Siri |
| Press and hold both stems | | | Live Translation. Also “Siri, start Live Translation”, the iPhone Action button, the Translate app, or a Phone or FaceTime control |
| Swipe up or down | Volume | Volume | Wireless-case model only |
| “Siri” or “Hey Siri” | | | The phone’s assistant |
| Nod or shake | | Answers Siri’s spoken question about a call, a message, or a notification | Only if Announce Calls and Announce Notifications are on |

Mute and end-call can be swapped, so one press ends the call and two presses mute, or the default the other way. The other control follows automatically.

### What can be reassigned

| Control | Where | Choices Apple names | What is not published |
|---|---|---|---|
| Press and hold, per stem | Settings, the AirPods, Press and Hold AirPods, Left or Right | The listening-mode cycle, or Siri. One stem can be modes and the other can be Siri | Which modes are in the cycle can be customized. Apple does not publish a decibel for any mode |
| Press Speed | Settings, Accessibility, AirPods | How quickly a double or triple press must land | No milliseconds |
| Press and Hold Duration | Same screen | How long a hold must last before it fires | No milliseconds |
| Volume swipe | Same screen, and only on the wireless-case model, AirPods Pro 2, and AirPods Pro 3 | Can be turned off. The length of the swipe can be changed | No decibel per swipe, and no named length in decibels |
| Tone Volume | Settings, Accessibility, AirPods, on AirPods 3 or later | Loudness of the earbud’s own tones, such as the low-battery sound | No decibel scale |
| Spatial Audio head tracking | Same screen | Follow iPhone makes supported audio seem to come from the phone instead of following the head | Not an equalizer |
| Microphone | Settings, the AirPods | Automatically Switch, Always Left, Always Right | |

### The case

The USB-C case charges from USB-C. The wireless charging case charges from USB-C, an Apple Watch charger, or a Qi-certified charger. The charge guide lists MagSafe for other cases, and it does not list either AirPods 5 case under MagSafe. The two cases are not interchangeable. Apple’s identify page says each case fits only its own earbuds.

The wireless case has a speaker on the bottom for charging and battery status, and for Find My. Find My with that speaker requires iOS 27 or later. The USB-C case’s identify entry does not mention a speaker. Find My on both versions can show the buds and can send a separation alert. Precision Finding, the Ultra Wideband arrow, is a Pro feature and is not described for these cases.

The status light is on the front of both cases. This note does not specify a tap pattern for pairing or a factory reset, because those steps were not on the pages read for this revision. Engraving a name or an emoji is an Apple Store option. It is not a control.

### One earbud out

| What happens | Behavior |
|---|---|
| Remove one earbud, Automatic Ear Detection on | Playback pauses. Put it back, or press the stem, and it resumes. Apple does not publish a resume delay in seconds |
| Remove both | Playback stops and does not resume by itself |
| Automatic Ear Detection off | Audio keeps going to the earbuds whether they are worn. Features that depend on detection stay off |
| Not wearing them, detection on | Audio comes from the phone or computer speakers |
| Noise Cancellation with One AirPod | Supported. Settings, Accessibility, AirPods, Noise Cancellation with One AirPod. Apple’s noise-control article names AirPods 4 with ANC, AirPods 5, and AirPods Pro |
| Microphone | Automatically Switch uses whichever earbud is available, so one earbud becomes the microphone. Always Left or Always Right keeps that choice even if that earbud is out of the ear or in the case |
| Pause when falling asleep | Pause Media When Falling Asleep, under the AirPods name, Audio & Routing if it is not on the first screen. The settings guide includes AirPods 5. No delay in seconds is published |

### Hands-free, beyond the stem

Nod yes or shake no answers Siri’s question about a call, a message, or a notification. Announce Calls and Announce Notifications have to be on. Siri AI, in the next section, is the phone’s assistant. The earbud does not run that model. Conversation Awareness is also hands-free: it reacts to the wearer speaking, and a press-and-hold ends that lowered-volume state by hand.

## 9. Speech, text, and what is actually decoded

The earbuds capture and play. The language models run on the phone.

| Path | What is decoded | Where |
|---|---|---|
| “Siri” or “Hey Siri” | A wake phrase, then the request | Phone. Hands-free from either bud |
| Siri AI | A more capable assistant, beta, on an Apple Intelligence device. iOS 27, macOS 27, and watchOS 27. English first, more languages later. Not initially in the EU on iOS, iPadOS, and watchOS. Not for users under 13. Apple describes hands-free access on all AirPods | Phone. Not an in-earbud model |
| Announce Calls and Announce Notifications | The phone reads a caller or a notification aloud, then accepts a nod or a shake | Phone speaks. The buds detect the nod |
| Live Translation | The other person’s speech, played back in the wearer’s language. Beta. Apple Intelligence on, latest firmware, iPhone 15 Pro or later, iOS 26 or later, languages downloaded in the Translate app. Start with both stems, “Siri, start Live Translation”, the Action button, the Translate app, or Phone or FaceTime | Phone. Processing is on the iPhone |
| Conversation Awareness | Detects that the wearer started speaking. It does not transcribe the words | On the earbuds |
| Voice Isolation | Cleans the outbound voice. No transcript | On the H2 path |
| Live Listen | The phone’s microphone, played into the earbuds. An accessibility feature, not a translator | Phone |
| Studio-quality recording | The product page ties the dual beamforming microphones to calls and to these recordings. Sample rate and bit depth are unpublished. This note does not copy Pro 3’s firmware-note app list (Camera, Voice Memos, Messages) onto AirPods 5, because the 9A350 note does not name those apps | Capture on the buds. The file lives on the phone |

Live Translation languages on the Apple Store buy page, read 5 November 2026: Chinese Mandarin Simplified, Chinese Mandarin Traditional, English UK, English US, French France, German Germany, Italian Italy, Japanese Japan, Korean South Korea, Portuguese Brazil, and Spanish Spain. Not every region. Apple’s usual note applies: generative models can be wrong, and this is a beta.

There is no on-earbud captioning. There is no Hearing Aid feature that turns the buds into a personal amplifier. There is no Workout Buddy on this spec page. Those stay off the inventory.

## 10. Battery, case, and charging

Apple’s test, July and August 2026, preproduction earbuds and preproduction iPhone 18 Pro, prerelease software, 358 iTunes tracks at 256 kbps AAC, volume at 50 percent. Listening time is until the first earbud stops. Spatial Audio and Conversation Awareness were off.

| Condition | AirPods 5 | AirPods 5 with Wireless Charging Case |
|---|---|---|
| ANC on, one charge | Up to 4 hours | Up to 5 hours |
| Noise control off, one charge | Up to 6 hours | Up to 7 hours |
| ANC on, with the case | Up to 20 hours | Up to 22 hours |
| 5 minutes in the case | About 1 hour, noise control off | About 1 hour, noise control off |

The wireless-case model’s 5 hours with ANC is the figure Apple compares with AirPods 4 with ANC, as one hour longer. The case totals are recharges, so they are not drawn on the same axis as the single-charge hours. Apple does not publish a noise-control-off total with the case, a talk-time figure, or a Transparency-only battery figure in the pages read here.

## 11. Connectivity

| Item | What Apple publishes | What this note will not add |
|---|---|---|
| Radio | Bluetooth 5.3 | A codec list beyond what the sources name |
| Codec in Apple’s battery test | 256 kbps AAC | A claim that every stream is 256 kbps |
| Codec in the lab review | AAC. SoundGuys says not aptX and not LDAC | SBC, unless a page that measured this model lists it. A German roundup is not that page |
| Lossless | Not an AirPods 5 wireless specification. The compare page’s USB-C lossless row is not applied to these columns in this note | A sample rate for a wire that is not in the spec |
| Automatic switching | Among Apple devices on the same Apple Account | Classic Bluetooth multipoint as an Android feature |
| Audio sharing | Bring a second pair near the iPhone, iPad, or Apple TV | A third-party share protocol |
| Non-Apple phones | Usable as Bluetooth headphones. Functionality may be limited. The stem still presses. Writing ANC, Custom EQ, or the press-and-hold assignment from an Android app is not a supported Apple API | A workaround |
| Spatial profile | Built on an iPhone with a TrueDepth camera. Syncs across Apple devices. Not available with other devices | |
| Find My | Both versions, with separation alerts. The wireless case can play a sound on iOS 27 or later | Precision Finding. No UWB is listed |

## 12. Feature inventory

| Feature | On this model | The wearer can edit | Apple published a decibel figure |
|---|---|---|---|
| Active Noise Cancellation | Both versions | Yes, in the hold cycle, and one-earbud ANC is a separate accessibility switch | No. “Up to 1.5×” versus AirPods 4 with ANC, IEC 60268-24. Not a curve |
| Transparency | Both versions | Yes, in the cycle | No |
| Adaptive Audio | Both versions | Yes. A slider toward Transparency or ANC | No |
| Off | Both versions | Yes, in the cycle | No. The lab’s ANC-off sweep is the open fit, not an Apple rating |
| Conversation Awareness | Both versions | A toggle. Press and hold ends it | No duck in decibels |
| Personalized Volume | Both versions | A toggle | No |
| Voice Isolation | Both versions | A call setting | No |
| Adaptive EQ | Both versions | Automatic | No |
| Custom EQ | Both versions, AirPods 4 or later, current system | Low, Mid, High. Recommended restores Apple’s profile | No range, no centers, no Q |
| Personalized Spatial Audio | Both versions | A TrueDepth profile, and head tracking can follow the phone | No |
| Headphone Accommodations | AirPods 3 or later, so yes | Accessibility | No step size published here |
| Volume swipe | Wireless-case version only | Can be turned off. Swipe length can be changed | No decibels per swipe |
| Press Speed and hold duration | Both versions | Accessibility | No milliseconds |
| Noise Cancellation with One AirPod | Both versions | Accessibility switch | No |
| Automatic Ear Detection | Both versions, on by default | Can be turned off | No resume delay |
| Pause Media When Falling Asleep | Both versions | A toggle under the AirPods name | No delay |
| Live Translation | Both versions, beta | Languages downloaded in Translate | No |
| Siri and Siri AI | Hands-free on the buds. The model is on the phone | Announce Calls and Announce Notifications gate the nod and the shake | No |
| Studio-quality recording | Listed | | No sample rate |
| Find My | Both versions. Speaker on the wireless case only, iOS 27 or later | | |
| Hearing Protection, Hearing Test, Hearing Aid, Conversation Boost, Loud Sound Reduction | Not on this model | | Pro 3’s tables do not transfer |
| Heart rate | Not on this model | | |
| Ear tips, UWB, MagSafe | Not on this model | | |
| Cases interchangeable | No. Each case fits only its own earbuds | | |

## 13. What this dossier will not claim

- A decibel value for “up to 1.5×” or “up to 50 percent more”, or for Pro 3’s “up to 3× versus AirPods 5”. Both are IEC 60268-24 comparisons. Neither curve is published.
- An ANSI hearing-protection total, an NRR, a CSA class, or an octave-band seal table. None was found for this model, and Pro 3’s sheet does not apply to an open ear.
- Hearing Protection, the Hearing Test, Hearing Aid, Conversation Boost, or Loud Sound Reduction.
- Heart rate, ear tips, UWB, Precision Finding, or MagSafe.
- Driver diameter, impedance, sensitivity, a published “20 Hz to 20 kHz” window, a sample rate, or a bit depth.
- A ± range, a center frequency, or a Q for the three Custom EQ sliders, or a 10-band table.
- That the black opening is the optical sensor. It might be. Apple’s caption does not say so.
- A separate hole for the charging contact and the stem microphone. The captions name both jobs and do not separate them.
- Left versus right model numbers. Apple lists three per version and does not assign them. The photographs print L and R on the stems.
- That one swipe is a known number of decibels, or that Press Speed has a published millisecond value.
- A resume delay in seconds, a pairing-button tap pattern, or a factory-reset gesture.
- That the two cases can be swapped.
- That an Android app can write ANC, EQ, or gestures. The stem press still works on the earbud.
- RTINGS category averages. The review exists and was not opened.
- SoundGuys’ case weight or their millimetre figures where they disagree with Apple’s tech specs.
- That 9A350 added studio recording inside Camera, Voice Memos, or Messages. That app list is a Pro 3 firmware note.

## Sources

- Apple Newsroom, “Apple introduces AirPods 5, featuring best-in-class open-ear Active Noise Cancellation”, 9 September 2026. https://www.apple.com/newsroom/2026/09/apple-introduces-airpods-5-with-best-in-class-open-ear-active-noise-cancellation/
- Apple, Compare AirPods models. The 1.5× and 3× claims, both footnoted to IEC 60268-24, and the feature rows that are present on Pro and absent here. https://www.apple.com/airpods/compare/
- Apple Support, AirPods 5 tech specs. https://support.apple.com/en-us/148759
- Apple Support, AirPods 5 with Wireless Charging Case tech specs. https://support.apple.com/en-us/148760
- Apple Support, “Identify your AirPods”, published 25 September 2026. https://support.apple.com/en-us/109525
- Apple Support, “About firmware updates for AirPods”, 9A350, page date 17 September 2026 in the copy read here. https://support.apple.com/en-us/106340
- Apple Support, “Active Noise Cancellation and Transparency modes”. Feedforward and feedback, and Noise Cancellation with One AirPod on AirPods 5. https://support.apple.com/en-us/108918
- Apple Support, “Use controls and gestures with your AirPods”. https://support.apple.com/guide/airpods/use-controls-and-gestures-with-your-airpods-devb2c431317/web
- Apple Support, “Change settings for AirPods or AirPods Pro”. Pause Media When Falling Asleep includes AirPods 5. https://support.apple.com/guide/airpods/change-settings-for-airpods-or-airpods-pro-dev57e5b7e58/web
- Apple Support, “Switch between listening modes on AirPods”. https://support.apple.com/guide/airpods/switch-between-listening-modes-dev9812f5cc3/web
- Apple Support, “Change force sensor, force sensor with volume swipe, or button settings for AirPods”. Press Speed, hold duration, and volume swipe on the wireless-case model. https://support.apple.com/guide/airpods/change-forcesensor-or-button-settings-dev68116dd52/web
- Apple Support, “Adjust the accessibility settings for AirPods on iPhone”. Tone Volume and Spatial Audio head tracking on AirPods 3 or later. https://support.apple.com/guide/iphone/adjust-airpods-settings-iph345efe861/ios
- Apple Support, “Play audio on AirPods, AirPods Pro, or AirPods Max”. Removing one earbud pauses; putting it back or pressing the stem resumes. https://support.apple.com/guide/airpods/play-audio-on-your-airpods-dev216d7c746/web
- Apple Support, “Charge AirPods, AirPods Pro, or AirPods Max”. https://support.apple.com/guide/airpods/charge-airpods-devde25a4bbe/web
- Apple Support, “Customize headphone audio levels”, the three-band equalizer on AirPods 4 or later. https://support.apple.com/en-us/102663
- Apple Support, Live Translation. https://support.apple.com/en-us/123185
- Apple Store buy page for AirPods 5, Live Translation language list, read 5 November 2026.
- Product photographs: apple.com/airpods-5 and the Apple Store gallery `airpods-5-202609`. Saved beside this note under `images/`
- SoundGuys, “Apple AirPods 5 with Wireless Charging Case”, B&K 5128 tables, retrieved 5 November 2026. https://www.soundguys.com/product/apple-airpods-5-with-wireless-charging-case/
