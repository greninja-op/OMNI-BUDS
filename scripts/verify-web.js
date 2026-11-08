const fs = require('fs');
const path = require('path');

const html = fs.readFileSync(path.join(__dirname, '..', 'index.html'), 'utf8');

const requiredIds = [
  'heroCanvas',
  'dynamicIsland',
  'phoneNotificationTrigger',
  'phoneNotificationShade',
  'ringLeft', 'valLeft',
  'ringRight', 'valRight',
  'ringCase', 'valCase',
  'rawHexPill',
  'ancDbText',
  'ancStepperContainer',
  'packetTicker',
  'morphPillsScroll',
  'morphSvgWrap',
  'morphChipset',
  'morphCategoryTag',
  'morphTitle',
  'morphExamples',
  'morphBatteryLayout',
  'morphTailoredFeatures',
  'radarStage',
  'radarCanvas',
  'radarSunCore',
  'pairingDeviceTitle',
  'pairingInstructions',
  'pairingLogTerminal',
  'btnModeRestorer',
  'btnModeStock',
  'fidelityCurvePath',
  'fidelityCurveFill',
  'fidelityStatusBadge',
  'eqSlidersContainer',
  'atmosRoomSvg',
  'atmosMeshPolygon',
  'atmosRaysPath',
  'inspectorProtocolName',
  'inspectorTargetChips',
  'inspectorStack',
  'inspectorFrame',
  'inspectorPoll',
  'inspectorLatency',
  'btnSimulatePogoByte',
  'pogoRawByteDisplay',
  'pogoBit7State',
  'pogoBatteryValue',
  'stemInteractiveTouchZone',
  'stemTriggerFeedback',
  'navbarVibeToggle',
  'vibeStatusText'
];

let missing = 0;
requiredIds.forEach(id => {
  const pattern = new RegExp(`id=["']${id}["']`);
  if (!pattern.test(html)) {
    console.error('Missing ID in index.html:', id);
    missing++;
  } else {
    console.log('✓ Found ID:', id);
  }
});

console.log('Total checked:', requiredIds.length, '| Missing:', missing);
if (missing > 0) process.exit(1);

// Also check external resource references in index.html
const localRefs = [
  'css/main.css',
  'js/audio-controller.js',
  'js/hero-canvas.js',
  'js/iphone-simulator.js',
  'js/form-factors.js',
  'js/radar-canvas.js',
  'js/bento-interactive.js',
  'js/main.js'
];

localRefs.forEach(ref => {
  const fullPath = path.join(__dirname, '..', ref);
  if (!fs.existsSync(fullPath)) {
    console.error('Referenced local file missing:', ref);
    missing++;
  } else {
    console.log('✓ File exists:', ref);
  }
});

if (missing === 0) {
  console.log('ALL INTEGRITY CHECKS PASSED PERFECTLY!');
}
