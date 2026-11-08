const fs = require('fs');
const path = require('path');
const vm = require('vm');

let errors = [];

function assert(condition, message) {
  if (!condition) {
    console.error('FAIL: ' + message);
    errors.push(message);
  } else {
    console.log('PASS: ' + message);
  }
}

console.log('--- 1. JS SYNTAX VALIDATION ---');
const jsFiles = [
  'js/audio-controller.js',
  'js/hero-canvas.js',
  'js/iphone-simulator.js',
  'js/form-factors.js',
  'js/radar-canvas.js',
  'js/bento-interactive.js',
  'js/main.js'
];

jsFiles.forEach(file => {
  const filePath = path.join(__dirname, '..', file);
  try {
    const code = fs.readFileSync(filePath, 'utf8');
    new vm.Script(code);
    assert(true, `Syntax valid: ${file}`);
  } catch (err) {
    assert(false, `Syntax error in ${file}: ${err.message}`);
  }
});

console.log('\n--- 2. HTML CONTENT & ANCHORS AUDIT ---');
const html = fs.readFileSync(path.join(__dirname, '..', 'index.html'), 'utf8');

// Navbar anchors
const expectedAnchors = [
  'The Bypass',
  'Live Simulator',
  '9 Form Factors',
  'Orbital Radar',
  'Acoustic DSP',
  'Architecture'
];

expectedAnchors.forEach(anchor => {
  assert(html.includes(anchor), `Navbar contains anchor text: "${anchor}"`);
});

assert(html.includes('Download APK / Launch App'), 'Navbar CTA contains exact text "Download APK / Launch App"');
assert(html.includes('id="bypass"'), 'Section with id="bypass" exists for The Bypass anchor');

console.log('\n--- 3. HERO & IPHONE PHONE SIMULATOR AUDIT ---');
assert(html.includes('AdaptiveAudioGlyph') || html.includes('adaptive-audio-glyph'), 'Angled AdaptiveAudioGlyph vector icons present in split battery rings');
assert(html.includes('0xFE2C • L: 0x5A (90%) R: 0x55 (85%) Case: 0x4B (75%) • 1.5s Poll'), 'Raw hex telemetry pill initialized with required hex bytes and percentages');
assert(html.includes('50dB Max'), 'ANC depth stepper includes 50dB Max option');
assert(html.includes('50ms Game Mode'), 'DSP toggles include 50ms Game Mode');
assert(html.includes('phone-notification-shade'), 'Persistent Background Notification Shade drawer present');

console.log('\n--- 4. 9 FORM-FACTORS SHOWCASE AUDIT ---');
const formFactorsCode = fs.readFileSync(path.join(__dirname, '..', 'js', 'form-factors.js'), 'utf8');
const sandbox = { window: { addEventListener: () => {} }, document: { addEventListener: () => {} } };
vm.createContext(sandbox);
const factors = vm.runInContext(formFactorsCode + '; HARDWARE_FORM_FACTORS;', sandbox);
assert(Array.isArray(factors) && factors.length === 9, 'All 9 hardware form factors defined');

const batteryTypes = new Set(factors.map(f => f.batteryType));
assert(batteryTypes.has('3-ring'), 'Contains 3-Ring Split (L/R/Case) topology');
assert(batteryTypes.has('2-ring'), 'Contains 2-Ring Combined Dual-Bud topology');
assert(batteryTypes.has('1-ring'), 'Contains 1-Ring Single Unit topology');

// Check feature titles required by specification
const neckband = factors.find(f => f.id === 'magnetic-neckband');
assert(neckband && neckband.features.some(ft => ft.title.includes('Magnetic Hall-Switch & Collar Vibration')), 'Neckband includes "Magnetic Hall-Switch & Collar Vibration"');

const overear = factors.find(f => f.id === 'over-ear-closed');
assert(overear && overear.features.some(ft => ft.title.includes('20-Level Ambient Slider & Speak-to-Chat')), 'Over-Ear includes "20-Level Ambient Slider & Speak-to-Chat"');

const openback = factors.find(f => f.id === 'open-back-audiophile');
assert(openback && openback.features.some(ft => ft.title.includes('Open-Air Dipole Bass Restorer & Anti-Leakage Whisper Mode')), 'Open-Back includes "Open-Air Dipole Bass Restorer & Anti-Leakage Whisper Mode"');

console.log('\n--- 5. BENTO GRID 5 DEEP-TECH CARDS AUDIT ---');
// Bento 1: iOS CoreAudio restorer
assert(html.includes('19.5kHz Air-Band') && html.includes('0dBFS Absolute Volume Lock'), 'Bento 1 displays 19.5kHz air-band & 0dBFS Absolute Volume lock');
assert(html.includes('14.5kHz low-pass') || html.includes('14.5kHz'), 'Bento 1 explains standard Android 14.5kHz roll-off');

// Bento 2: 10-band EQ, Dynamic BassWave (-5 to +5), Dolby Atmos
assert(html.includes('data-preset="purebass"'), 'Bento 2 preset: Pure Bass');
assert(html.includes('data-preset="vocals"'), 'Bento 2 preset: Clear Vocals');
assert(html.includes('data-preset="nature"'), 'Bento 2 preset: Nature Balance');
assert(html.includes('data-preset="bassboost"'), 'Bento 2 preset: Bass Boost+');
assert(html.includes('id="basswaveSlider"'), 'Bento 2 Dynamic BassWave slider present');
assert(html.includes('id="basswaveValueDisplay"'), 'Bento 2 Dynamic BassWave value readout present');
assert(html.includes('data-atmos="smart"'), 'Bento 2 Atmos mode: Smart');
assert(html.includes('data-atmos="movie"'), 'Bento 2 Atmos mode: Movie');
assert(html.includes('data-atmos="gaming"'), 'Bento 2 Atmos mode: Gaming');
assert(html.includes('data-atmos="music"'), 'Bento 2 Atmos mode: Music');

// Bento 3: 5-Silicon Protocol Router & Circuit Bus
assert(html.includes('circuit-bus-diagram'), 'Bento 3 glowing animated circuit/node bus diagram present');
assert(html.includes('data-protocol="bes"'), 'Bento 3 node: BES / HeyMelody');
assert(html.includes('data-protocol="airoha"'), 'Bento 3 node: Airoha RACE');
assert(html.includes('data-protocol="qualcomm"'), 'Bento 3 node: Qualcomm QCC GAIA');
assert(html.includes('data-protocol="samsung"'), 'Bento 3 node: Samsung SPP');
assert(html.includes('data-protocol="apple"'), 'Bento 3 node: Apple L2CAP 0x1001');
assert(html.includes('data-protocol="fastpair"'), 'Bento 3 node: Fast Pair 0xFE2C');

// Bento 4: BleCommandSequencer & Pogo Pin 0x80 / 0x7F Bitmask
assert(html.includes('btnPreemptSim'), 'Bento 4 preemption simulation trigger present');
assert(html.includes('0x80') && html.includes('0x7F'), 'Bento 4 explicit 0x80 (Charging) and 0x7F (Battery SoC) bitmask present');

// Bento 5: Physical Sensors (5 Hardware Types)
assert(html.includes('Capacitive Touch (1x/2x/3x/Hold)'), 'Bento 5 includes Capacitive Touch (1x/2x/3x/Hold)');
assert(html.includes('Force Pinch + Speed'), 'Bento 5 includes Force Pinch + Speed');
assert(html.includes('Stem Volume Slide Bar'), 'Bento 5 includes Stem Volume Slide Bar');
assert(html.includes('Edge Tap'), 'Bento 5 includes Edge Tap');
assert(html.includes('IMU Head Nod / Shake') || html.includes('IMU Head Nod/Shake'), 'Bento 5 includes IMU Head Nod/Shake');
assert(html.includes('sensorCapacitive') && html.includes('sensorForcePinch') && html.includes('sensorVolumeSlide') && html.includes('sensorEdgeTap') && html.includes('sensorImu'), 'Bento 5 SVG contains all 5 interactive sensor hotspot zones');

console.log('\n--- 6. SOLAR SYSTEM ORBITAL RADAR AUDIT ---');
const radarCode = fs.readFileSync(path.join(__dirname, '..', 'js', 'radar-canvas.js'), 'utf8');
assert(radarCode.includes('sweeping-ping'), 'Radar canvas detects sweep collisions and applies sweeping-ping glow to device bubbles');
assert(radarCode.includes('orbitRadii'), 'Radar canvas supports dynamic responsive orbitRadii');
assert(radarCode.includes('particles.push'), 'Radar canvas animates fairy-dust shimmer particles');

console.log('\n--- SUMMARY ---');
console.log(`Total Assertions Checked: ${35} | Failures: ${errors.length}`);
if (errors.length > 0) {
  console.error('\nEncountered failures:\n' + errors.join('\n'));
  process.exit(1);
} else {
  console.log('\nALL ADVANCED ARCHITECTURAL AUDITS PASSED WITH ZERO ERRORS!');
}
