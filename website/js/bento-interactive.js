/* ==========================================================================
   OmniBuds Interactive Bento Grid Deep-Tech Architecture
   Bento 1: CoreAudio Restorer Frequency Curve Graph
   Bento 2: 10-Band EQ, Dynamic BassWave (-5 to +5) & Dolby Atmos 3D Room
   Bento 3: Universal 5-Silicon Protocol Router with Circuit Bus Traces
   Bento 4: BleCommandSequencer Preemption & Pogo-Pin 0x80/0x7F Bitwise Decoder
   Bento 5: Physical Sensor & Gesture Remapper Matrix
   ========================================================================== */

class BentoInteractiveEngine {
  constructor() {
    this.initCoreAudioChart();
    this.init10BandEq();
    this.initDynamicBassWave();
    this.initDolbyAtmosRoom();
    this.initProtocolRouter();
    this.initSequencerAndPogo();
    this.initGestureRemapper();
  }

  /* ------------------------------------------------------------------------
     Bento 1: iOS CoreAudio Fidelity Restorer SVG Graph
     ------------------------------------------------------------------------ */
  initCoreAudioChart() {
    const btnRestorer = document.getElementById('btnModeRestorer');
    const btnStock = document.getElementById('btnModeStock');
    const pathCurve = document.getElementById('fidelityCurvePath');
    const fillArea = document.getElementById('fidelityCurveFill');
    const badgeLabel = document.getElementById('fidelityStatusBadge');

    if (!btnRestorer || !btnStock || !pathCurve) return;

    // SVG path data (width=500, height=200)
    // CoreAudio Restorer: Smooth audiophile curve with +3dB sub-bass and crisp 19.5kHz extension
    const restorerPath = 'M 20 110 C 60 85, 120 100, 200 102 C 280 102, 360 98, 420 95 C 460 92, 485 100, 490 108';
    const restorerFill = `${restorerPath} L 490 180 L 20 180 Z`;

    // Android Stock AAC: Dynamic range compression, harsh roll-off at 14.5kHz
    const stockPath = 'M 20 135 C 60 130, 120 120, 200 115 C 280 115, 340 125, 370 145 C 410 178, 450 185, 490 188';
    const stockFill = `${stockPath} L 490 180 L 20 180 Z`;

    const setMode = (mode) => {
      if (mode === 'restorer') {
        btnRestorer.classList.add('active');
        btnStock.classList.remove('active');
        pathCurve.setAttribute('d', restorerPath);
        pathCurve.setAttribute('stroke', '#E6FF00');
        if (fillArea) {
          fillArea.setAttribute('d', restorerFill);
          fillArea.setAttribute('fill', 'url(#restorerGrad)');
        }
        if (badgeLabel) {
          badgeLabel.innerHTML = 'RESTORED: <span style="color:#E6FF00;">19.5kHz Air-Band • 0dBFS Absolute Volume Lock • Fletcher-Munson Contour</span>';
        }
        if (window.audioVibe) window.audioVibe.playChime(880, 'sine', 0.15, 0.03);
      } else {
        btnStock.classList.add('active');
        btnRestorer.classList.remove('active');
        pathCurve.setAttribute('d', stockPath);
        pathCurve.setAttribute('stroke', '#FF4D4D');
        if (fillArea) {
          fillArea.setAttribute('d', stockFill);
          fillArea.setAttribute('fill', 'url(#stockGrad)');
        }
        if (badgeLabel) {
          badgeLabel.innerHTML = 'STOCK ANDROID: <span style="color:#FF4D4D;">14.5kHz Roll-off • -12dB Dynamic Range Crush</span>';
        }
        if (window.audioVibe) window.audioVibe.playChime(320, 'sine', 0.12, 0.03);
      }
    };

    btnRestorer.addEventListener('click', () => setMode('restorer'));
    btnStock.addEventListener('click', () => setMode('stock'));
  }

  /* ------------------------------------------------------------------------
     Bento 2: 10-Band Parametric EQ
     ------------------------------------------------------------------------ */
  init10BandEq() {
    this.eqBands = [32, 64, 125, 250, 500, '1k', '2k', '4k', '8k', '16k'];
    this.presets = {
      purebass: [92, 88, 76, 55, 50, 50, 52, 54, 56, 58],
      vocals: [40, 45, 50, 68, 85, 88, 78, 65, 55, 50],
      nature: [60, 62, 58, 55, 54, 55, 58, 62, 64, 65],
      bassboost: [98, 95, 85, 68, 54, 50, 54, 60, 68, 72]
    };

    const container = document.getElementById('eqSlidersContainer');
    if (!container) return;

    container.innerHTML = '';
    this.eqSliders = [];

    this.eqBands.forEach((band, idx) => {
      const col = document.createElement('div');
      col.className = 'eq-band-col';

      const rail = document.createElement('div');
      rail.className = 'eq-slider-rail';

      const fill = document.createElement('div');
      fill.className = 'eq-slider-fill';
      fill.style.height = `${this.presets.nature[idx]}%`;

      rail.appendChild(fill);

      const label = document.createElement('div');
      label.className = 'eq-band-label';
      label.textContent = typeof band === 'number' ? `${band}` : band;

      // Allow dragging or clicking slider rail
      let isDragging = false;
      const setGainFromY = (e) => {
        const rect = rail.getBoundingClientRect();
        const clientY = e.clientY || (e.touches && e.touches[0].clientY);
        const ratio = Math.max(0, Math.min(1, 1 - (clientY - rect.top) / rect.height));
        fill.style.height = `${Math.round(ratio * 100)}%`;
      };

      rail.addEventListener('mousedown', (e) => {
        isDragging = true;
        setGainFromY(e);
      });
      window.addEventListener('mousemove', (e) => {
        if (isDragging) setGainFromY(e);
      });
      window.addEventListener('mouseup', () => { isDragging = false; });

      col.appendChild(rail);
      col.appendChild(label);
      container.appendChild(col);
      this.eqSliders.push(fill);
    });

    // Preset Buttons (Pure Bass, Clear Vocals, Nature Balance, Bass Boost+)
    const presetBtns = document.querySelectorAll('.eq-preset-btn');
    presetBtns.forEach(btn => {
      btn.addEventListener('click', () => {
        presetBtns.forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        const pKey = btn.dataset.preset;
        const targetValues = this.presets[pKey];
        if (targetValues) {
          this.eqSliders.forEach((slider, i) => {
            slider.style.height = `${targetValues[i]}%`;
          });
          if (window.audioVibe) window.audioVibe.playChime(600 + Object.keys(this.presets).indexOf(pKey) * 60, 'sine', 0.1, 0.02);
        }
      });
    });
  }

  /* ------------------------------------------------------------------------
     Dynamic BassWave (-5 to +5) Controller
     ------------------------------------------------------------------------ */
  initDynamicBassWave() {
    const slider = document.getElementById('basswaveSlider');
    const display = document.getElementById('basswaveValueDisplay');
    if (!slider || !display) return;

    slider.addEventListener('input', () => {
      const val = parseInt(slider.value, 10);
      const sign = val > 0 ? '+' : '';
      const db = (val * 1.0).toFixed(1);
      display.textContent = `${sign}${db} dB (Level ${sign}${val})`;

      // Dynamically adjust low-band EQ sliders (32Hz, 64Hz, 125Hz)
      if (this.eqSliders && this.eqSliders.length >= 3) {
        const base = 55 + val * 6;
        this.eqSliders[0].style.height = `${Math.min(100, Math.max(10, base + 15))}%`;
        this.eqSliders[1].style.height = `${Math.min(100, Math.max(10, base + 8))}%`;
        this.eqSliders[2].style.height = `${Math.min(100, Math.max(10, base))}%`;
      }

      if (window.audioVibe) window.audioVibe.playChime(300 + (val + 5) * 40, 'triangle', 0.08, 0.02);
    });
  }

  /* ------------------------------------------------------------------------
     Dolby Atmos 3D Room Visualizer (Smart, Movie, Gaming, Music)
     ------------------------------------------------------------------------ */
  initDolbyAtmosRoom() {
    const atmosBtns = document.querySelectorAll('.atmos-mode-btn');
    const roomSvg = document.getElementById('atmosRoomSvg');
    if (!atmosBtns.length || !roomSvg) return;

    const roomProfiles = {
      smart: {
        points: '20,60 120,20 280,20 380,60 380,75 280,75 120,75 20,75',
        rays: 'M 200,60 L 50,45 M 200,60 L 350,45 M 200,60 L 150,25 M 200,60 L 250,25',
        color: '#E6FF00'
      },
      movie: {
        points: '10,70 90,10 310,10 390,70 390,80 310,80 90,80 10,80',
        rays: 'M 200,65 L 30,30 M 200,65 L 370,30 M 200,65 L 110,15 M 200,65 L 290,15',
        color: '#00F0FF'
      },
      gaming: {
        points: '40,55 140,25 260,25 360,55 360,70 260,70 140,70 40,70',
        rays: 'M 200,55 L 70,40 M 200,55 L 330,40 M 200,55 L 170,25 M 200,55 L 230,25',
        color: '#22E55E'
      },
      music: {
        points: '30,62 130,18 270,18 370,62 370,75 270,75 130,75 30,75',
        rays: 'M 200,62 L 60,35 M 200,62 L 340,35 M 200,62 L 140,20 M 200,62 L 260,20',
        color: '#FFB020'
      }
    };

    atmosBtns.forEach(btn => {
      btn.addEventListener('click', () => {
        atmosBtns.forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        const pKey = btn.dataset.atmos;
        const profile = roomProfiles[pKey];

        const meshPolygon = document.getElementById('atmosMeshPolygon');
        const rayPaths = document.getElementById('atmosRaysPath');

        if (meshPolygon && rayPaths && profile) {
          meshPolygon.setAttribute('points', profile.points);
          meshPolygon.setAttribute('stroke', profile.color);
          rayPaths.setAttribute('d', profile.rays);
          rayPaths.setAttribute('stroke', profile.color);
        }

        if (window.audioVibe) window.audioVibe.playChime(720, 'triangle', 0.1, 0.03);
      });
    });
  }

  /* ------------------------------------------------------------------------
     Bento 3: Universal 5-Silicon Protocol Router Blueprint
     ------------------------------------------------------------------------ */
  initProtocolRouter() {
    const protocols = {
      bes: {
        name: 'BES / HeyMelody (Realme, OnePlus, Oppo)',
        target: 'BES2500 / BES2600 / BES2700 Series',
        stack: 'Custom BLE GATT UUID: 0000FD5B-0000-1000-8000-00805F9B34FB',
        frame: '0x01 [CMD_ANC] 0x01 [OP_SET] 0x02 [VAL_30DB] 0x00 0x00 [CRC_BE]',
        pollRate: '1.5s Adaptive Keep-Alive with Interactive Preempt',
        latency: '18ms (WRITE_TYPE_NO_RESPONSE Direct HW Register)',
        busX: 75
      },
      airoha: {
        name: 'Airoha RACE Protocol (Sony, Xiaomi, EarFun)',
        target: 'Airoha AB1562 / AB1565 / MT2822',
        stack: 'RACE Command Framing (Opcode 0x0A02 Channel 0x05)',
        frame: '0x05 0x5A [HEADER] 0x04 [LEN] 0x0A 0x02 [CMD] 0x01 [ANC_ON] 0x3F [XOR_CKSUM]',
        pollRate: '2.0s Multi-Cell Polling Loop',
        latency: '22ms Instant State Sync',
        busX: 225
      },
      qualcomm: {
        name: 'Qualcomm QCC GAIA (Bose, Sennheiser, Audio-Technica)',
        target: 'QCC3040 / QCC5141 / QCC5171 / S5 Gen 2',
        stack: 'GAIA v3 Bluetooth RFCOMM / BLE Service 0x1101',
        frame: '0xFF 0x01 [GAIA_HDR] 0x03 [LEN] 0x02 0x14 [NOISE_CANCELLATION] 0x03 [VAL]',
        pollRate: '1.8s Battery Notification Push Subscriptions',
        latency: '24ms Event-Driven Execution',
        busX: 375
      },
      samsung: {
        name: 'Samsung SPP RFCOMM (Galaxy Buds Family)',
        target: 'Shannon / Broadcom BCM43015 Dual-Bud',
        stack: 'Bluetooth RFCOMM Channel 0x0004 with CRC16-CCITT',
        frame: '0xFD [START] 0x00 0x06 [LEN] 0xC2 [MSG_SET_ANC] 0x01 0x00 0x9A 0x3F [CRC16]',
        pollRate: '1.0s Multi-Payload Telemetry Stream',
        latency: '20ms Direct Hardware Register Access',
        busX: 525
      },
      apple: {
        name: 'Apple L2CAP 0x1001 (AirPods Reverse-Engineered)',
        target: 'Apple H1 / H2 Custom Co-Processors',
        stack: 'L2CAP Dynamic PSM 0x1001 Channel Telemetry Emulation',
        frame: '0x07 [AIRPODS_MAGIC] 0x19 0x01 [PRO_CASE_ACK] 0x5A 0x55 0x4B [L/R/CASE_BYTES]',
        pollRate: 'Proximity RSSI + BLE Beacon Sniffer (Zero Battery Drain)',
        latency: '15ms Raw L2CAP Stream Multiplexing',
        busX: 675
      },
      fastpair: {
        name: 'Google Fast Pair 0xFE2C Native Handshake',
        target: 'All Dual-Mode BR/EDR + BLE Compliant Audio Hardware',
        stack: 'GATT Service 0xFE2C (Key-based Pairing & Account Key Store)',
        frame: '0xFE2C [SVC_UUID] 0x00 [SALT_HASH] 0x4B 0x7E 0x11 0x90 [ECDH_AES128_AUTH]',
        pollRate: 'Instant Passive Advertising Discovery Loop',
        latency: '<12ms Zero-Click Peripheral Discovery',
        busX: 825
      }
    };

    const nodes = document.querySelectorAll('.protocol-chip-node');
    const nameEl = document.getElementById('inspectorProtocolName');
    const targetEl = document.getElementById('inspectorTargetChips');
    const stackEl = document.getElementById('inspectorStack');
    const frameEl = document.getElementById('inspectorFrame');
    const pollEl = document.getElementById('inspectorPoll');
    const latencyEl = document.getElementById('inspectorLatency');
    const pulseDot = document.getElementById('circuitPulseDot');

    nodes.forEach(node => {
      node.addEventListener('click', () => {
        nodes.forEach(n => n.classList.remove('active'));
        node.classList.add('active');
        const pKey = node.dataset.protocol;
        const data = protocols[pKey];

        if (data && nameEl) {
          nameEl.textContent = data.name;
          targetEl.textContent = data.target;
          stackEl.textContent = data.stack;
          frameEl.textContent = data.frame;
          pollEl.textContent = data.pollRate;
          latencyEl.textContent = data.latency;

          // Animate circuit pulse dot to this silicon node's bus coordinate
          if (pulseDot) {
            pulseDot.setAttribute('cx', `${data.busX}`);
          }
        }

        if (window.audioVibe) window.audioVibe.playChime(900, 'sine', 0.08, 0.03);
      });
    });
  }

  /* ------------------------------------------------------------------------
     Bento 4: BleCommandSequencer & Pogo Pin 0x80 / 0x7F Bitwise Decoder
     ------------------------------------------------------------------------ */
  initSequencerAndPogo() {
    // 1. Preemption Simulator Trigger
    const btnPreempt = document.getElementById('btnPreemptSim');
    const statusText = document.getElementById('sequencerStatusText');
    const timingBar = document.getElementById('omniTimingBar');

    if (btnPreempt && statusText) {
      btnPreempt.addEventListener('click', () => {
        if (timingBar) {
          timingBar.style.boxShadow = '0 0 25px #E6FF00';
          timingBar.style.transform = 'scale(1.02)';
        }
        statusText.innerHTML = '<span style="color:#E6FF00;">⚡ PREEMPTION TRIGGERED: 1.5s Polling Aborted in &lt;18ms (WRITE_TYPE_NO_RESPONSE Direct HW Register)</span>';
        if (window.audioVibe) window.audioVibe.playChime(1200, 'sine', 0.15, 0.05);

        setTimeout(() => {
          if (timingBar) {
            timingBar.style.boxShadow = '';
            timingBar.style.transform = '';
          }
          statusText.innerHTML = '<span style="color:#22E55E;">● Heartbeat: 1.5s Polling (Standby)</span>';
        }, 2500);
      });
    }

    // 2. Pogo-Pin Hardware Bitwise Decoder (0x80 Charging Flag & 0x7F Battery Mask)
    const btnSimulate = document.getElementById('btnSimulatePogoByte');
    const byteDisplay = document.getElementById('pogoRawByteDisplay');
    const bit7Display = document.getElementById('pogoBit7State');
    const batteryDisplay = document.getElementById('pogoBatteryValue');

    if (!btnSimulate || !byteDisplay) return;

    const sampleBytes = [
      { raw: '0xDA', byteVal: 0xDA, bin: '11011010' },
      { raw: '0x55', byteVal: 0x55, bin: '01010101' },
      { raw: '0xCB', byteVal: 0xCB, bin: '11001011' },
      { raw: '0x64', byteVal: 0x64, bin: '01100100' },
      { raw: '0x8F', byteVal: 0x8F, bin: '10001111' }
    ];

    let currentIdx = 0;

    btnSimulate.addEventListener('click', () => {
      currentIdx = (currentIdx + 1) % sampleBytes.length;
      const sample = sampleBytes[currentIdx];

      // Bitwise decode using standard 0x80 and 0x7F masks
      const isCharging = (sample.byteVal & 0x80) !== 0;
      const soc = sample.byteVal & 0x7F;

      byteDisplay.textContent = `${sample.raw} (${sample.bin})`;
      bit7Display.innerHTML = isCharging
        ? '<span style="color:#22E55E;">0x80 Mask [Bit 7 = 1] -> CHARGING VOLTAGE DETECTED</span>'
        : '<span style="color:#9C9EA8;">0x80 Mask [Bit 7 = 0] -> DISCHARGING IN EAR</span>';

      const hexSoC = `0x${soc.toString(16).toUpperCase()}`;
      batteryDisplay.innerHTML = `0x7F Mask [Bits 0-6 = ${hexSoC}] -> <strong style="color:#F4F5F7;">${soc}%</strong> State of Charge`;

      if (window.audioVibe) window.audioVibe.playChime(760, 'triangle', 0.1, 0.03);
    });
  }

  /* ------------------------------------------------------------------------
     Bento 5: Physical Sensor & Gesture Remapper Matrix (5 Hardware Types)
     ------------------------------------------------------------------------ */
  initGestureRemapper() {
    const gestureRows = document.querySelectorAll('.gesture-row-item');
    const stemTouchZone = document.getElementById('stemInteractiveTouchZone');
    const actionLabel = document.getElementById('stemTriggerFeedback');

    if (!gestureRows.length) return;

    // Sensor SVG Hotspot Elements
    const sensorMap = {
      pinch: document.getElementById('sensorForcePinch'),
      capacitive: document.getElementById('sensorCapacitive'),
      volume: document.getElementById('sensorVolumeSlide'),
      edge: document.getElementById('sensorEdgeTap'),
      imu: document.getElementById('sensorImu')
    };

    const highlightHotspot = (sensorKey) => {
      Object.keys(sensorMap).forEach(key => {
        const el = sensorMap[key];
        if (!el) return;
        if (key === sensorKey) {
          el.setAttribute('stroke', '#E6FF00');
          el.setAttribute('stroke-width', '2.5');
          el.style.filter = 'drop-shadow(0 0 6px #E6FF00)';
        } else {
          el.setAttribute('stroke', '#5E616C');
          el.setAttribute('stroke-width', '1.2');
          el.style.filter = 'none';
        }
      });
    };

    const actionList = [
      'Toggle ANC (50dB ↔ Ambient)',
      'Play / Pause / Next Track',
      'Stepless Absolute Volume (0dBFS)',
      'Microphone Mute / Quick Voice',
      'Accept / Reject Incoming Call',
      'Ultra-Low 50ms Game Mode'
    ];

    gestureRows.forEach((row, i) => {
      row.addEventListener('click', () => {
        gestureRows.forEach(r => r.classList.remove('active'));
        row.classList.add('active');

        const sensorKey = row.dataset.sensor;
        highlightHotspot(sensorKey);

        // Cycle through next mapped action for fun interactive feedback
        const actionSpan = row.querySelector('.gesture-mapped-action');
        const nextAction = actionList[(i + 1) % actionList.length];
        if (actionSpan) {
          actionSpan.textContent = nextAction;
        }

        if (actionLabel) {
          actionLabel.innerHTML = `Hardware Sensor [${sensorKey.toUpperCase()}]: Remapped to <span style="color:#E6FF00;">${nextAction}</span>`;
        }

        if (window.audioVibe) window.audioVibe.playChime(820, 'sine', 0.08, 0.03);
      });
    });

    if (stemTouchZone) {
      stemTouchZone.addEventListener('click', () => {
        stemTouchZone.style.boxShadow = '0 0 25px #E6FF00';
        if (actionLabel) {
          actionLabel.innerHTML = '⚡ Physical Sensor Dispatch: <span style="color:#22E55E;">18ms Direct Gesture ACK</span>';
        }
        setTimeout(() => {
          stemTouchZone.style.boxShadow = 'none';
        }, 300);
        if (window.audioVibe) window.audioVibe.playChime(1050, 'sine', 0.12, 0.04);
      });
    }

    // Initialize with first active hotspot
    highlightHotspot('pinch');
  }
}

window.addEventListener('DOMContentLoaded', () => {
  window.bentoEngine = new BentoInteractiveEngine();
});