/* ==========================================================================
   OmniBuds Interactive Titanium iPhone 16 Pro Simulator
   Live Mini-Build of the OmniBuds Native Control Interface
   ========================================================================== */

class IPhoneSimulator {
  constructor() {
    this.currentMode = 'anc'; // 'anc', 'off', 'transparency'
    this.ancDepth = 50; // dB
    this.telemetryMode = 0; // 0: Hex+Dec, 1: Raw Hex, 2: Dec Only
    this.isShadeOpen = false;

    // Battery levels
    this.batteries = {
      left: { pct: 90, hex: '0x5A', charging: true },
      right: { pct: 85, hex: '0x55', charging: true },
      case: { pct: 75, hex: '0x4B', charging: false }
    };

    // DSP Toggles
    this.dspStates = {
      spatial: true,
      gameMode: false,
      dolby: true,
      volEnhance: false
    };

    this.init();
  }

  init() {
    this.bindAncControls();
    this.bindDspToggles();
    this.bindNotificationShade();
    this.bindTelemetryPill();
    this.bindDynamicIsland();
    this.updateBatteryArcs();
    this.logPacket('0x01', '0x01', '0x32', '18ms ACK (ANC 50dB)');
  }

  bindAncControls() {
    const ancBtns = document.querySelectorAll('.anc-btn');
    ancBtns.forEach(btn => {
      btn.addEventListener('click', (e) => {
        const mode = btn.dataset.mode;
        this.setAncMode(mode);
      });
    });

    const steppers = document.querySelectorAll('.stepper-btn');
    steppers.forEach(btn => {
      btn.addEventListener('click', () => {
        steppers.forEach(s => s.classList.remove('active'));
        btn.classList.add('active');
        this.ancDepth = parseInt(btn.dataset.depth, 10);
        document.getElementById('ancDbText').textContent = `-${this.ancDepth}dB`;
        if (this.currentMode === 'anc') {
          if (window.heroCanvas) window.heroCanvas.setMode('anc', this.ancDepth);
          this.logPacket('0x01', '0x05', `0x${this.ancDepth.toString(16).toUpperCase()}`, `Updated ANC Depth: -${this.ancDepth}dB`);
        }
      });
    });
  }

  setAncMode(mode) {
    this.currentMode = mode;

    // Update App Buttons
    document.querySelectorAll('.anc-btn').forEach(b => {
      b.classList.toggle('active', b.dataset.mode === mode);
    });

    // Update Notification Shade Buttons (Sync!)
    document.querySelectorAll('.shade-action-btn').forEach(b => {
      b.classList.toggle('active', b.dataset.mode === mode);
    });

    const dbBadge = document.getElementById('ancDbText');
    const stepperContainer = document.getElementById('ancStepperContainer');

    if (mode === 'anc') {
      if (dbBadge) dbBadge.textContent = `-${this.ancDepth}dB`;
      if (stepperContainer) stepperContainer.style.opacity = '1';
      this.logPacket('0x01', '0x01', '0x01', 'WRITE_TYPE_NO_RESPONSE ANC Active');
      if (window.heroCanvas) window.heroCanvas.setMode('anc', this.ancDepth);
      if (window.audioVibe) window.audioVibe.playAncTransition('anc');
    } else if (mode === 'transparency') {
      if (dbBadge) dbBadge.textContent = '+6.0dB';
      if (stepperContainer) stepperContainer.style.opacity = '0.35';
      this.logPacket('0x01', '0x02', '0x01', 'Dual-Mic Vocal Pass-Through On');
      if (window.heroCanvas) window.heroCanvas.setMode('transparency');
      if (window.audioVibe) window.audioVibe.playAncTransition('transparency');
    } else {
      if (dbBadge) dbBadge.textContent = 'OFF';
      if (stepperContainer) stepperContainer.style.opacity = '0.35';
      this.logPacket('0x01', '0x01', '0x00', 'Teardown Sequence [Mic Power Cut]');
      if (window.heroCanvas) window.heroCanvas.setMode('normal');
      if (window.audioVibe) window.audioVibe.playAncTransition('normal');
    }
  }

  bindDspToggles() {
    const capsules = document.querySelectorAll('.dsp-toggle-capsule');
    capsules.forEach(cap => {
      cap.addEventListener('click', () => {
        const dspKey = cap.dataset.dsp;
        this.dspStates[dspKey] = !this.dspStates[dspKey];
        cap.classList.toggle('active', this.dspStates[dspKey]);

        if (window.audioVibe) {
          window.audioVibe.playChime(this.dspStates[dspKey] ? 750 : 380, 'sine', 0.1, 0.03);
        }

        const stateTxt = this.dspStates[dspKey] ? 'ENABLED' : 'BYPASS';
        this.logPacket('0x08', '0x0A', this.dspStates[dspKey] ? '0x01' : '0x00', `DSP [${dspKey.toUpperCase()}] -> ${stateTxt}`);
      });
    });
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
        this.setAncMode(mode);
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

  updateBatteryArcs() {
    const circumference = 2 * Math.PI * 18; // r=18 => ~113.1
    
    const updateRing = (ringId, textId, pct) => {
      const ring = document.getElementById(ringId);
      const text = document.getElementById(textId);
      if (ring) {
        ring.style.strokeDasharray = `${circumference}`;
        const offset = circumference - (pct / 100) * circumference;
        ring.style.strokeDashoffset = `${offset}`;
      }
      if (text) {
        text.textContent = `${pct}%`;
      }
    };

    updateRing('ringLeft', 'valLeft', this.batteries.left.pct);
    updateRing('ringRight', 'valRight', this.batteries.right.pct);
    updateRing('ringCase', 'valCase', this.batteries.case.pct);
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
