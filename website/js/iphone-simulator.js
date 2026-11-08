/* ==========================================================================
   OmniBuds Interactive iPhone 18 Pro Max Simulator & Notification Sync
   Exact 1:1 In-App Android UI (WorkspaceScreen.kt + OmniBudsAppShell.kt)
   Clean, Static Profile, Zero Wobble, Synchronized with Notification Shade
   ========================================================================== */

class IPhoneSimulator {
  constructor() {
    this.currentMode = 'anc'; // 'anc', 'off', 'transparency'
    this.eqPresetIndex = 0;
    this.eqPresets = ['Flat', 'Studio Hi-Fi', 'Bass Boost+', 'Clear Vocals', 'Pure Bass'];

    // Battery levels
    this.batteries = {
      left: { pct: 90 },
      right: { pct: 85 },
      case: { pct: 75 }
    };

    // Feature toggles
    this.features = {
      spatial: true,
      gameMode: false
    };

    this.init();
  }

  init() {
    this.bindNoiseControls();
    this.bindFeatureControls();
    this.bindNotificationQuickControls();
    this.updateBatteryArcs();
  }

  bindNoiseControls() {
    // Both In-Phone circular buttons and Notification shade buttons
    const allModeButtons = document.querySelectorAll('.noise-circle-btn, .noti-action-btn');
    allModeButtons.forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.preventDefault();
        const mode = btn.dataset.mode;
        if (mode) this.setAncMode(mode);
      });
    });
  }

  setAncMode(mode) {
    this.currentMode = mode;

    // 1. Sync in-phone noise circular buttons
    document.querySelectorAll('.noise-circle-btn').forEach(btn => {
      const isMatch = btn.dataset.mode === mode;
      btn.classList.toggle('active', isMatch);
      const parentWrap = btn.closest('.noise-circle-wrap');
      if (parentWrap) {
        const label = parentWrap.querySelector('.noise-circle-label');
        if (label) label.classList.toggle('active', isMatch);
      }
    });

    // 2. Sync notification drawer action buttons
    document.querySelectorAll('.noti-action-btn').forEach(btn => {
      btn.classList.toggle('active', btn.dataset.mode === mode);
    });

    // 3. Audio & Hero Canvas synchronization
    if (mode === 'anc') {
      if (window.heroCanvas) window.heroCanvas.setMode('anc', 50);
      if (window.audioVibe) window.audioVibe.playAncTransition('anc');
    } else if (mode === 'transparency') {
      if (window.heroCanvas) window.heroCanvas.setMode('transparency');
      if (window.audioVibe) window.audioVibe.playAncTransition('transparency');
    } else {
      if (window.heroCanvas) window.heroCanvas.setMode('normal');
      if (window.audioVibe) window.audioVibe.playAncTransition('normal');
    }
  }

  bindFeatureControls() {
    // Equalizer Presets Button (In-Phone)
    const btnEq = document.getElementById('btnEqPreset');
    const subtitleEq = document.getElementById('featureEqSubtitle');
    const notiEq = document.getElementById('notiToggleEq');

    const cycleEq = () => {
      this.eqPresetIndex = (this.eqPresetIndex + 1) % this.eqPresets.length;
      const currentPreset = this.eqPresets[this.eqPresetIndex];
      if (subtitleEq) subtitleEq.textContent = `Preset: ${currentPreset}`;
      if (notiEq) notiEq.innerHTML = `<span>Preset: ${currentPreset}</span>`;
      if (window.audioVibe) window.audioVibe.playChime(650 + this.eqPresetIndex * 70, 'sine', 0.1, 0.02);
    };

    if (btnEq) btnEq.addEventListener('click', cycleEq);
    if (notiEq) notiEq.addEventListener('click', cycleEq);

    // Spatial Audio Switch
    const switchSpatial = document.getElementById('switchSpatial');
    if (switchSpatial) {
      switchSpatial.addEventListener('click', () => {
        this.features.spatial = !this.features.spatial;
        switchSpatial.classList.toggle('active', this.features.spatial);
        if (window.audioVibe) {
          window.audioVibe.playChime(this.features.spatial ? 880 : 440, 'sine', 0.1, 0.02);
        }
      });
    }

    // Game Mode Switch (Sync both Phone and Notification bar)
    const switchGameMode = document.getElementById('switchGameMode');
    const notiGame = document.getElementById('notiToggleGame');

    const toggleGameMode = () => {
      this.features.gameMode = !this.features.gameMode;
      if (switchGameMode) switchGameMode.classList.toggle('active', this.features.gameMode);
      if (notiGame) {
        notiGame.style.borderColor = this.features.gameMode ? 'var(--volt-primary)' : 'rgba(255, 255, 255, 0.1)';
        notiGame.style.color = this.features.gameMode ? 'var(--volt-primary)' : '#E2E4E9';
      }
      if (window.audioVibe) {
        window.audioVibe.playChime(this.features.gameMode ? 920 : 380, 'sine', 0.1, 0.02);
      }
    };

    if (switchGameMode) switchGameMode.addEventListener('click', toggleGameMode);
    if (notiGame) notiGame.addEventListener('click', toggleGameMode);
  }

  bindNotificationQuickControls() {
    // Clicking any action in notification shade switches seamlessly
    const notiButtons = document.querySelectorAll('.noti-action-btn');
    notiButtons.forEach(btn => {
      btn.addEventListener('click', () => {
        const mode = btn.dataset.mode;
        if (mode) this.setAncMode(mode);
      });
    });
  }

  updateBatteryArcs() {
    const circumference = 2 * Math.PI * 26; // r=26 => ~163.36
    const updateRing = (ringId, pct) => {
      const ring = document.getElementById(ringId);
      if (ring) {
        ring.style.strokeDasharray = `${circumference}`;
        const offset = circumference - (pct / 100) * circumference;
        ring.style.strokeDashoffset = `${offset}`;
      }
    };

    updateRing('ringLeft', this.batteries.left.pct);
    updateRing('ringRight', this.batteries.right.pct);
    updateRing('ringCase', this.batteries.case.pct);
  }
}

// Global bootstrap
document.addEventListener('DOMContentLoaded', () => {
  window.iphoneSimulator = new IPhoneSimulator();
});
