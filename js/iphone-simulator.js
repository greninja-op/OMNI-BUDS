/* ==========================================================================
   OmniBuds Interactive iPhone 18 Pro Max Simulator
   Exact 1:1 In-App Android UI (WorkspaceScreen.kt + OmniBudsAppShell.kt)
   Static, Razor-Thin Titanium Profile, Zero Wobble/Hover Drift
   ========================================================================== */

class IPhoneSimulator {
  constructor() {
    this.currentMode = 'anc'; // 'anc', 'off', 'transparency'
    this.ancDepth = 50; // dB
    this.telemetryMode = 0; // 0: Hex+Dec, 1: Raw Hex, 2: Dec Only
    this.isShadeOpen = false;
    this.isSplitBatteryView = true;
    this.eqPresetIndex = 0;
    this.eqPresets = ['Flat', 'Bass Boost+', 'Clear Vocals', 'Pure Bass', 'Studio Hi-Fi'];

    // Battery levels
    this.batteries = {
      left: { pct: 90, hex: '0x5A', charging: true },
      right: { pct: 85, hex: '0x55', charging: true },
      case: { pct: 100, hex: '0x64', charging: false },
      earbudsCombined: { pct: 85, hex: '0x55', charging: true }
    };

    // Feature toggles
    this.features = {
      spatial: true,
      gameMode: false
    };

    this.init();
  }

  init() {
    this.bindNoiseCircles();
    this.bindFeatureControls();
    this.bindNotificationShade();
    this.bindTelemetryPill();
    this.bindDynamicIsland();
    this.bindBatteryViewToggle();
    this.updateBatteryArcs();
    this.logPacket('0x01', '0x01', '0x32', '18ms ACK (ANC 50dB)');
  }

  bindNoiseCircles() {
    const circleBtns = document.querySelectorAll('.noise-circle-btn, .anc-btn');
    circleBtns.forEach(btn => {
      btn.addEventListener('click', () => {
        const mode = btn.dataset.mode;
        if (mode) this.setAncMode(mode);
      });
    });

    const steppers = document.querySelectorAll('.stepper-btn');
    steppers.forEach(btn => {
      btn.addEventListener('click', () => {
        steppers.forEach(s => s.classList.remove('active'));
        btn.classList.add('active');
        this.ancDepth = parseInt(btn.dataset.depth, 10);
        if (this.currentMode === 'anc') {
          if (window.heroCanvas) window.heroCanvas.setMode('anc', this.ancDepth);
          this.logPacket('0x01', '0x05', `0x${this.ancDepth.toString(16).toUpperCase()}`, `Updated ANC Depth: -${this.ancDepth}dB`);
        }
      });
    });
  }

  setAncMode(mode) {
    this.currentMode = mode;

    // Update 3 Circular Mode Buttons inside Phone
    document.querySelectorAll('.noise-circle-btn').forEach(btn => {
      const isMatch = btn.dataset.mode === mode;
      btn.classList.toggle('active', isMatch);
      const parentWrap = btn.closest('.noise-circle-wrap');
      if (parentWrap) {
        const label = parentWrap.querySelector('.noise-circle-label');
        if (label) label.classList.toggle('active', isMatch);
      }
    });

    // Also sync old .anc-btn if present
    document.querySelectorAll('.anc-btn').forEach(btn => {
      btn.classList.toggle('active', btn.dataset.mode === mode);
    });

    // Update Notification Shade Buttons (Sync!)
    document.querySelectorAll('.shade-action-btn').forEach(btn => {
      btn.classList.toggle('active', btn.dataset.mode === mode);
    });

    const stepperContainer = document.getElementById('ancStepperContainer');

    if (mode === 'anc') {
      if (stepperContainer) stepperContainer.style.opacity = '1';
      this.logPacket('0x01', '0x01', '0x01', 'WRITE_TYPE_NO_RESPONSE ANC Active (-50dB)');
      if (window.heroCanvas) window.heroCanvas.setMode('anc', this.ancDepth);
      if (window.audioVibe) window.audioVibe.playAncTransition('anc');
    } else if (mode === 'transparency') {
      if (stepperContainer) stepperContainer.style.opacity = '0.35';
      this.logPacket('0x01', '0x02', '0x01', 'Dual-Mic Vocal Pass-Through On');
      if (window.heroCanvas) window.heroCanvas.setMode('transparency');
      if (window.audioVibe) window.audioVibe.playAncTransition('transparency');
    } else {
      if (stepperContainer) stepperContainer.style.opacity = '0.35';
      this.logPacket('0x01', '0x01', '0x00', 'Normal / Off Teardown [Mic Power Cut]');
      if (window.heroCanvas) window.heroCanvas.setMode('normal');
      if (window.audioVibe) window.audioVibe.playAncTransition('normal');
    }
  }

  bindFeatureControls() {
    // Equalizer Presets Button
    const btnEq = document.getElementById('btnEqPreset');
    const subtitleEq = document.getElementById('featureEqSubtitle');
    if (btnEq && subtitleEq) {
      btnEq.addEventListener('click', () => {
        this.eqPresetIndex = (this.eqPresetIndex + 1) % this.eqPresets.length;
        const currentPreset = this.eqPresets[this.eqPresetIndex];
        subtitleEq.textContent = `Preset: ${currentPreset}`;
        if (window.audioVibe) window.audioVibe.playChime(650 + this.eqPresetIndex * 80, 'sine', 0.1, 0.02);
        this.logPacket('0x07', '0x02', `0x0${this.eqPresetIndex}`, `DSP Equalizer -> ${currentPreset}`);
      });
    }

    // Spatial Audio Switch
    const switchSpatial = document.getElementById('switchSpatial');
    if (switchSpatial) {
      switchSpatial.addEventListener('click', () => {
        this.features.spatial = !this.features.spatial;
        switchSpatial.classList.toggle('active', this.features.spatial);
        if (window.audioVibe) {
          window.audioVibe.playChime(this.features.spatial ? 880 : 440, 'sine', 0.1, 0.02);
        }
        this.logPacket('0x08', '0x01', this.features.spatial ? '0x01' : '0x00', `Spatial Audio -> ${this.features.spatial ? 'ON' : 'OFF'}`);
      });
    }

    // Game Mode Switch
    const switchGameMode = document.getElementById('switchGameMode');
    if (switchGameMode) {
      switchGameMode.addEventListener('click', () => {
        this.features.gameMode = !this.features.gameMode;
        switchGameMode.classList.toggle('active', this.features.gameMode);
        if (window.audioVibe) {
          window.audioVibe.playChime(this.features.gameMode ? 920 : 380, 'sine', 0.1, 0.02);
        }
        this.logPacket('0x05', '0x01', this.features.gameMode ? '0x01' : '0x00', `50ms Low-Latency Game Mode -> ${this.features.gameMode ? 'ENABLED' : 'DISABLED'}`);
      });
    }

    // Topbar header buttons
    const radarBtn = document.getElementById('appRadarBtn');
    if (radarBtn) {
      radarBtn.addEventListener('click', () => {
        if (window.audioVibe) window.audioVibe.playChime(1050, 'triangle', 0.1, 0.02);
        this.logPacket('0x02', '0x10', '0x01', 'BLE 5.3 Active Radar Ping Initiated');
        // Smooth scroll to orbital radar section
        const radarSec = document.getElementById('orbital-radar');
        if (radarSec) radarSec.scrollIntoView({ behavior: 'smooth' });
      });
    }

    const settingsBtn = document.getElementById('appSettingsBtn');
    if (settingsBtn) {
      settingsBtn.addEventListener('click', () => {
        if (window.audioVibe) window.audioVibe.playChime(720, 'sine', 0.08, 0.02);
        this.logPacket('0x09', '0x01', '0x00', 'Opening Hardware Diagnostics Panel');
      });
    }
  }

  bindBatteryViewToggle() {
    const toggleBtn = document.getElementById('btnToggleBatteryView');
    const container = document.getElementById('batteryRingsContainer');
    if (!toggleBtn || !container) return;

    toggleBtn.addEventListener('click', () => {
      this.isSplitBatteryView = !this.isSplitBatteryView;
      toggleBtn.textContent = this.isSplitBatteryView ? 'Switch to Combined' : 'Switch to Split';

      if (this.isSplitBatteryView) {
        // State A: Split 3-Ring View (L, R, Case)
        container.className = 'adaptive-battery-rings split-view';
        container.innerHTML = `
          <div class="battery-ring-slot">
            <div class="battery-gauge-wrapper">
              <svg class="battery-gauge-svg" viewBox="0 0 64 64">
                <circle class="gauge-bg" cx="32" cy="32" r="26"/>
                <circle class="gauge-fill" id="ringLeft" cx="32" cy="32" r="26"/>
              </svg>
              <div class="gauge-center-icon">
                <svg class="adaptive-audio-glyph" viewBox="0 0 16 16" width="16" height="16" fill="none">
                  <g transform="rotate(-12 8 8)">
                    <ellipse cx="6" cy="6" rx="3.2" ry="4.8" fill="#8E929B"/>
                    <path d="M7 4 C 9 3, 13 5, 13 8 C 13 10, 11 12, 9 12 L 10 15 C 10 15.5, 9.5 16, 9 16 C 8.5 16, 8 15.5, 8 15 L 7 12 Z" fill="#F4F5F7"/>
                  </g>
                </svg>
              </div>
            </div>
            <div class="battery-gauge-pct" id="valLeft">90%</div>
            <div class="battery-gauge-label">L ⚡</div>
          </div>

          <div class="battery-ring-slot">
            <div class="battery-gauge-wrapper">
              <svg class="battery-gauge-svg" viewBox="0 0 64 64">
                <circle class="gauge-bg" cx="32" cy="32" r="26"/>
                <circle class="gauge-fill" id="ringRight" cx="32" cy="32" r="26"/>
              </svg>
              <div class="gauge-center-icon">
                <svg class="adaptive-audio-glyph" viewBox="0 0 16 16" width="16" height="16" fill="none">
                  <g transform="rotate(12 8 8)">
                    <ellipse cx="10" cy="6" rx="3.2" ry="4.8" fill="#8E929B"/>
                    <path d="M9 4 C 7 3, 3 5, 3 8 C 3 10, 5 12, 7 12 L 6 15 C 6 15.5, 6.5 16, 7 16 C 7.5 16, 8 15.5, 8 15 L 9 12 Z" fill="#F4F5F7"/>
                  </g>
                </svg>
              </div>
            </div>
            <div class="battery-gauge-pct" id="valRight">85%</div>
            <div class="battery-gauge-label">R ⚡</div>
          </div>

          <div class="battery-ring-slot">
            <div class="battery-gauge-wrapper">
              <svg class="battery-gauge-svg" viewBox="0 0 64 64">
                <circle class="gauge-bg" cx="32" cy="32" r="26"/>
                <circle class="gauge-fill" id="ringCase" cx="32" cy="32" r="26"/>
              </svg>
              <div class="gauge-center-icon">
                <svg class="adaptive-audio-glyph" viewBox="0 0 24 24" width="18" height="18" fill="none">
                  <rect x="3" y="6" width="18" height="13" rx="5" fill="#F4F5F7"/>
                  <line x1="3" y1="10.5" x2="21" y2="10.5" stroke="#1C1D21" stroke-width="1.2"/>
                  <circle cx="12" cy="14" r="1.5" fill="#22E55E"/>
                </svg>
              </div>
            </div>
            <div class="battery-gauge-pct" id="valCase">75%</div>
            <div class="battery-gauge-label">Case</div>
          </div>
        `;
      } else {
        // State B: Combined TWS View (Earbuds L+R + Case)
        container.className = 'adaptive-battery-rings combined-view';
        container.innerHTML = `
          <div class="battery-ring-slot" id="slotEarbuds">
            <div class="battery-gauge-wrapper">
              <svg class="battery-gauge-svg" viewBox="0 0 64 64">
                <circle class="gauge-bg" cx="32" cy="32" r="26"/>
                <circle class="gauge-fill" id="ringEarbuds" cx="32" cy="32" r="26"/>
              </svg>
              <div class="gauge-center-icon">
                <svg class="adaptive-audio-glyph" viewBox="0 0 24 24" width="22" height="22" fill="none">
                  <g transform="translate(3, 2) rotate(-10 6 8)">
                    <ellipse cx="6" cy="6" rx="3.5" ry="5" fill="#8E929B"/>
                    <path d="M7 4 C9 3, 11 5, 11 8 C11 10, 9 12, 8 12 L9 15 C9 15.5, 8.5 16, 8 16 C7.5 16, 7 15.5, 7 15 L6 12 Z" fill="#F4F5F7"/>
                  </g>
                  <g transform="translate(13, 2) rotate(10 6 8)">
                    <ellipse cx="6" cy="6" rx="3.5" ry="5" fill="#8E929B"/>
                    <path d="M5 4 C3 3, 1 5, 1 8 C1 10, 3 12, 4 12 L3 15 C3 15.5, 3.5 16, 4 16 C4.5 16, 5 15.5, 5 15 L6 12 Z" fill="#F4F5F7"/>
                  </g>
                </svg>
              </div>
            </div>
            <div class="battery-gauge-pct" id="valEarbuds">85%</div>
            <div class="battery-gauge-label">L + R</div>
          </div>

          <div class="battery-ring-slot" id="slotCase">
            <div class="battery-gauge-wrapper">
              <svg class="battery-gauge-svg" viewBox="0 0 64 64">
                <circle class="gauge-bg" cx="32" cy="32" r="26"/>
                <circle class="gauge-fill" id="ringCase" cx="32" cy="32" r="26"/>
              </svg>
              <div class="gauge-center-icon">
                <svg class="adaptive-audio-glyph" viewBox="0 0 24 24" width="20" height="20" fill="none">
                  <rect x="3" y="6" width="18" height="13" rx="5" fill="#F4F5F7"/>
                  <line x1="3" y1="10.5" x2="21" y2="10.5" stroke="#1C1D21" stroke-width="1.2"/>
                  <circle cx="12" cy="14" r="1.5" fill="#22E55E"/>
                </svg>
              </div>
            </div>
            <div class="battery-gauge-pct" id="valCase">100%</div>
            <div class="battery-gauge-label">Case</div>
          </div>
        `;
      }

      this.updateBatteryArcs();
      if (window.audioVibe) window.audioVibe.playChime(780, 'sine', 0.08, 0.02);
      this.logPacket('0x04', '0x01', this.isSplitBatteryView ? '0x01' : '0x00', `Battery Telemetry Topology -> ${this.isSplitBatteryView ? 'SPLIT (L/R/Case)' : 'COMBINED (L+R/Case)'}`);
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

    if (this.isSplitBatteryView) {
      updateRing('ringLeft', this.batteries.left.pct);
      updateRing('ringRight', this.batteries.right.pct);
      updateRing('ringCase', 75);
    } else {
      updateRing('ringEarbuds', this.batteries.earbudsCombined.pct);
      updateRing('ringCase', this.batteries.case.pct);
    }
  }

  bindNotificationShade() {
    const trigger = document.getElementById('phoneNotificationTrigger');
    const shade = document.getElementById('phoneNotificationShade');

    if (trigger && shade) {
      trigger.addEventListener('click', () => {
        this.isShadeOpen = !this.isShadeOpen;
        shade.classList.toggle('open', this.isShadeOpen);
        trigger.innerHTML = this.isShadeOpen 
          ? '<span>▲ Dismiss Notification Shade</span>' 
          : '<span>▼ Pull for Background Notification Controls</span>';
        if (window.audioVibe) window.audioVibe.playChime(500, 'triangle', 0.08, 0.02);
      });
    }

    // Action buttons inside shade
    const shadeActions = document.querySelectorAll('.shade-action-btn');
    shadeActions.forEach(btn => {
      btn.addEventListener('click', () => {
        const mode = btn.dataset.mode;
        if (mode) this.setAncMode(mode);
      });
    });
  }

  bindTelemetryPill() {
    const pill = document.getElementById('rawHexPill');
    if (!pill) return;

    pill.addEventListener('click', () => {
      this.telemetryMode = (this.telemetryMode + 1) % 3;
      this.renderTelemetryText();
      if (window.audioVibe) window.audioVibe.playChime(800, 'sine', 0.08, 0.02);
    });

    this.renderTelemetryText();
  }

  renderTelemetryText() {
    const pill = document.getElementById('rawHexPill');
    if (!pill) return;

    if (this.telemetryMode === 0) {
      pill.innerHTML = `<span>TELEMETRY</span> <span>0xFE2C • L: ${this.batteries.left.hex} (${this.batteries.left.pct}%) R: ${this.batteries.right.hex} (${this.batteries.right.pct}%) Case: ${this.batteries.case.hex} (${this.batteries.case.pct}%) • 1.5s Poll</span>`;
    } else if (this.telemetryMode === 1) {
      pill.innerHTML = `<span>RAW HEX</span> <span>0xFE2C • L: ${this.batteries.left.hex} R: ${this.batteries.right.hex} Case: ${this.batteries.case.hex} • 1.5s Poll</span>`;
    } else {
      pill.innerHTML = `<span>TELEMETRY</span> <span>0xFE2C • L: ${this.batteries.left.pct}% R: ${this.batteries.right.pct}% Case: ${this.batteries.case.pct}% • 1.5s Poll</span>`;
    }
  }

  bindDynamicIsland() {
    const island = document.getElementById('dynamicIsland');
    if (!island) return;

    island.addEventListener('click', () => {
      island.classList.toggle('expanded');
      if (window.audioVibe) window.audioVibe.playChime(950, 'sine', 0.12, 0.03);
    });
  }

  logPacket(cmd, op, val, desc) {
    const ticker = document.getElementById('packetTicker');
    if (!ticker) return;
    ticker.innerHTML = `<span class="pulse"></span> [BLE] [${cmd} ${op} ${val}] ${desc}`;
  }
}

window.addEventListener('DOMContentLoaded', () => {
  window.phoneSim = new IPhoneSimulator();
});
