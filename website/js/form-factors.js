/* ==========================================================================
   OmniBuds 9 Hardware Form-Factors Morphing Showcase
   Hardware Silhouette Vector Morphing, Battery Gauge Layouts & Feature Swapper
   ========================================================================== */

const HARDWARE_FORM_FACTORS = [
  {
    id: 'stem-in-ear',
    name: 'Stem In-Ear',
    category: 'Form-Factor 01 // TWS',
    devices: 'realme Buds T300 • AirPods Pro 2 • OnePlus Buds Pro 3',
    chipset: 'BES2600YP / Apple H2 / Airoha AB1562A',
    batteryType: '3-ring',
    batteries: [
      { name: 'Left Earbud', pct: 90, status: 'Active (ANC)' },
      { name: 'Right Earbud', pct: 85, status: 'Active (ANC)' },
      { name: 'Charging Case', pct: 75, status: 'Dock Standby' }
    ],
    features: [
      {
        icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/></svg>`,
        title: 'Stem Force Squeeze Calibration',
        desc: 'Custom pressure-sensor threshold tuning (100g to 450g) to prevent accidental cheek trigger.'
      },
      {
        icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="m9 12 2 2 4-4"/></svg>`,
        title: 'In-Ear Fit Seal Test',
        desc: 'Internal error-mic pink-noise acoustic sweep verifies sub-bass acoustic airtightness.'
      }
    ],
    svg: `
      <svg viewBox="0 0 200 200" fill="none" xmlns="http://www.w3.org/2000/svg">
        <!-- Angled Stem In-Ear Vector Silhouette -->
        <defs>
          <linearGradient id="stemShellGrad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stop-color="#FFFFFF"/>
            <stop offset="60%" stop-color="#E2E5EB"/>
            <stop offset="100%" stop-color="#B2B6C4"/>
          </linearGradient>
          <linearGradient id="tipGrad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stop-color="#8E929B"/>
            <stop offset="100%" stop-color="#555861"/>
          </linearGradient>
        </defs>
        <!-- Silicone Ear Tip -->
        <ellipse cx="65" cy="72" rx="26" ry="34" transform="rotate(-25 65 72)" fill="url(#tipGrad)"/>
        <ellipse cx="65" cy="72" rx="14" ry="20" transform="rotate(-25 65 72)" fill="#1C1D21"/>
        <!-- Acoustic Head Shell -->
        <path d="M78 52 C 95 38, 130 50, 138 78 C 144 98, 134 116, 118 122 L 128 178 C 129 184, 124 190, 118 190 C 112 190, 107 185, 106 179 L 98 122 C 82 120, 68 100, 68 84 Z" fill="url(#stemShellGrad)"/>
        <!-- Feedforward Mic Vent -->
        <rect x="120" y="65" width="4" height="12" rx="2" fill="#1C1D21"/>
        <!-- Force Sensor Indent -->
        <rect x="110" y="142" width="3" height="18" rx="1.5" fill="#1C1D21" opacity="0.6"/>
        <!-- Gold Charging Contacts -->
        <circle cx="112" cy="186" r="2.5" fill="#E6FF00"/>
        <circle cx="120" cy="186" r="2.5" fill="#E6FF00"/>
      </svg>
    `
  },
  {
    id: 'stem-half-in-ear',
    name: 'Stem Half-In-Ear',
    category: 'Form-Factor 02 // Open Non-Sealed',
    devices: 'AirPods 4 • Nothing Ear (stick)',
    chipset: 'Apple H2 / BES2700 / Qualcomm QCC3056',
    batteryType: '3-ring',
    batteries: [
      { name: 'Left Earbud', pct: 85, status: 'Active (Open)' },
      { name: 'Right Earbud', pct: 85, status: 'Active (Open)' },
      { name: 'Charging Case', pct: 60, status: 'Dock Standby' }
    ],
    features: [
      {
        icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M2 12h20M7 8l-5 4 5 4M17 8l5 4-5 4"/></svg>`,
        title: 'Low-Pressure Concha Airflow',
        desc: 'Adaptive bass equalizer compensates for variable ear canal acoustics without silicone seals.'
      },
      {
        icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z"/><path d="M19 10v2a7 7 0 0 1-14 0v-2"/></svg>`,
        title: 'Directional Acoustic Porting',
        desc: 'Phase-aligned rear vents cancel 75% of outgoing acoustic radiation for whisper-quiet privacy.'
      }
    ],
    svg: `
      <svg viewBox="0 0 200 200" fill="none" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <linearGradient id="halfShell" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stop-color="#FFFFFF"/>
            <stop offset="100%" stop-color="#CCD0DC"/>
          </linearGradient>
        </defs>
        <!-- Sculpted Half In-Ear Bulb -->
        <path d="M70 65 C 70 42, 100 32, 125 45 C 145 56, 148 85, 132 105 L 134 175 C 134 182, 128 188, 121 188 C 114 188, 109 182, 109 175 L 105 115 C 80 115, 70 92, 70 65 Z" fill="url(#halfShell)"/>
        <!-- Acoustic Port Open Grille -->
        <ellipse cx="88" cy="65" rx="12" ry="18" fill="#1C1D21"/>
        <line x1="88" y1="52" x2="88" y2="78" stroke="#5E616C" stroke-width="1.5"/>
        <line x1="82" y1="58" x2="94" y2="72" stroke="#5E616C" stroke-width="1.5"/>
        <!-- Rear Bass Duct Port -->
        <rect x="133" y="60" width="3.5" height="14" rx="1.5" fill="#1C1D21"/>
      </svg>
    `
  },
  {
    id: 'stemless-bean',
    name: 'Stemless Bean / Bud',
    category: 'Form-Factor 03 // Ergonomic Bud',
    devices: 'Galaxy Buds2 Pro • Sony WF-1000XM5 • Sennheiser MTW4',
    chipset: 'Samsung Shannon / Sony V2 / Qualcomm S5 Gen 2',
    batteryType: '3-ring',
    batteries: [
      { name: 'Left Bean', pct: 95, status: 'Active (LDAC)' },
      { name: 'Right Bean', pct: 95, status: 'Active (LDAC)' },
      { name: 'Charging Case', pct: 80, status: 'Dock Standby' }
    ],
    features: [
      {
        icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>`,
        title: '360° Gyro Head Tracking',
        desc: 'Low-latency 6-axis IMU quaternion streaming locks audio soundstage to physical space.'
      },
      {
        icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 6 6 18M6 6l12 12"/></svg>`,
        title: 'Capacitive Touch Lockout',
        desc: 'Single-touch gesture lock mode prevents accidental pause when adjusting fit in ear concha.'
      }
    ],
    svg: `
      <svg viewBox="0 0 200 200" fill="none" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <radialGradient id="beanGrad" cx="40%" cy="30%" r="70%">
            <stop offset="0%" stop-color="#FFFFFF"/>
            <stop offset="60%" stop-color="#C2C5D1"/>
            <stop offset="100%" stop-color="#2D2F38"/>
          </radialGradient>
        </defs>
        <!-- Silicone Ear Tip Angle -->
        <ellipse cx="62" cy="115" rx="20" ry="28" transform="rotate(35 62 115)" fill="#686C77"/>
        <ellipse cx="62" cy="115" rx="9" ry="15" transform="rotate(35 62 115)" fill="#1C1D21"/>
        <!-- Ergonomic Bean Silhouette -->
        <path d="M85 70 C 95 45, 145 45, 160 80 C 172 108, 150 145, 120 152 C 95 158, 70 140, 75 110 C 78 95, 80 85, 85 70 Z" fill="url(#beanGrad)"/>
        <!-- Wind-Noise Aerodynamic Metal Mesh Vent -->
        <ellipse cx="140" cy="85" rx="9" ry="6" fill="#1C1D21" stroke="#E6FF00" stroke-width="0.8"/>
        <!-- Gold Charging Ring Pogo -->
        <circle cx="108" cy="140" r="3" fill="#E6FF00"/>
        <circle cx="120" cy="138" r="3" fill="#E6FF00"/>
      </svg>
    `
  },
  {
    id: 'open-ring',
    name: 'Open-Ring Bud',
    category: 'Form-Factor 04 // Toroidal Ring',
    devices: 'Sony LinkBuds WF-L900',
    chipset: 'Sony Integrated V1 / Mediatek MT2822',
    batteryType: '3-ring',
    batteries: [
      { name: 'Left Ring', pct: 70, status: 'Open Awareness' },
      { name: 'Right Ring', pct: 70, status: 'Open Awareness' },
      { name: 'Charging Case', pct: 55, status: 'Dock Standby' }
    ],
    features: [
      {
        icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><circle cx="12" cy="12" r="4"/></svg>`,
        title: 'Toroidal Donut Driver Directivity',
        desc: 'Open center hole lets natural real-world conversations in with zero software latency.'
      },
      {
        icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 12v4a8 8 0 0 0 16 0v-4"/><path d="M12 2v4"/></svg>`,
        title: 'Wide Area Tap Sensor',
        desc: 'Vibration sensor registers double-taps anywhere between earlobe and cheekbone.'
      }
    ],
    svg: `
      <svg viewBox="0 0 200 200" fill="none" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <linearGradient id="ringShell" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stop-color="#FFFFFF"/>
            <stop offset="100%" stop-color="#9EA1AC"/>
          </linearGradient>
        </defs>
        <!-- Upper Pill Body -->
        <ellipse cx="100" cy="72" rx="36" ry="30" fill="url(#ringShell)"/>
        <!-- Toroidal Open Ring Driver -->
        <circle cx="100" cy="138" r="32" fill="url(#ringShell)"/>
        <!-- The Open Acoustic Center Hole! -->
        <circle cx="100" cy="138" r="16" fill="#050507" stroke="#1C1D21" stroke-width="3"/>
        <!-- Silicone Fitting Wing -->
        <path d="M68 62 C 60 50, 64 35, 78 30 C 85 28, 92 36, 88 48 Z" fill="#757984"/>
      </svg>
    `
  },
  {
    id: 'open-ear-hook',
    name: 'Open-Ear Hook',
    category: 'Form-Factor 05 // Conduction Hook',
    devices: 'Nothing Ear (open) • Powerbeats Pro • Shokz OpenFit',
    chipset: 'BES2700IBP / Apple H1 / Qualcomm S3',
    batteryType: '3-ring',
    batteries: [
      { name: 'Left Hook', pct: 80, status: 'Dipole Active' },
      { name: 'Right Hook', pct: 80, status: 'Dipole Active' },
      { name: 'Charging Case', pct: 90, status: 'Dock Standby' }
    ],
    features: [
      {
        icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 9a6 6 0 1 1 12 0c0 4.5-6 9-6 9s-6-4.5-6-9Z"/><circle cx="12" cy="9" r="2"/></svg>`,
        title: 'Open-Air Dipole Bass Restorer',
        desc: 'Reverse-phase wave cancellation reduces sound leakage while synthesizing sub-bass pressure.'
      },
      {
        icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>`,
        title: 'Liquid Silicone Weight Balance',
        desc: 'Nickel-titanium memory wire wraps smoothly around cartilage with zero pinch points.'
      }
    ],
    svg: `
      <svg viewBox="0 0 200 200" fill="none" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <linearGradient id="hookGrad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stop-color="#F4F5F7"/>
            <stop offset="100%" stop-color="#9CA0AD"/>
          </linearGradient>
        </defs>
        <!-- Silicone Ear Hook Curve -->
        <path d="M72 135 C 45 105, 48 55, 95 40 C 135 28, 160 55, 155 95 C 152 120, 138 135, 122 132" fill="none" stroke="#7A7E8A" stroke-width="12" stroke-linecap="round"/>
        <!-- Front Acoustic Pod -->
        <ellipse cx="72" cy="138" rx="22" ry="18" fill="url(#hookGrad)"/>
        <!-- Acoustic Porting Mesh -->
        <rect x="62" y="132" width="20" height="8" rx="3" fill="#1C1D21"/>
        <!-- Rear Counterweight Pod -->
        <ellipse cx="120" cy="135" rx="14" ry="18" fill="#1C1D21" stroke="#E6FF00" stroke-width="1"/>
      </svg>
    `
  },
  {
    id: 'open-ear-clip',
    name: 'Open-Ear Clip Cuff',
    category: 'Form-Factor 06 // Ear Cuff',
    devices: 'Bose Ultra Open Earbuds • Huawei FreeClip',
    chipset: 'Qualcomm QCC5171 / BES2600 / Kirin A2',
    batteryType: '2-ring',
    batteries: [
      { name: 'Left Cuff', pct: 88, status: 'C-Bridge Linked (Dual-Bud)' },
      { name: 'Right Cuff', pct: 88, status: 'C-Bridge Linked (Dual-Bud)' }
    ],
    features: [
      {
        icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m16 16 3-8 3 8c-.87.65-1.92 1-3 1s-2.13-.35-3-1Z"/><path d="m2 16 3-8 3 8c-.87.65-1.92 1-3 1s-2.13-.35-3-1Z"/><path d="M7 21h10"/></svg>`,
        title: 'Auto L/R Inversion Engine',
        desc: 'Identical symmetric earbuds automatically calibrate left and right channels based on wear angle.'
      },
      {
        icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>`,
        title: 'Cartilage Comfort Equalizer',
        desc: 'Dynamically shifts mid-band frequencies based on acoustic reflectance against human pinna.'
      }
    ],
    svg: `
      <svg viewBox="0 0 200 200" fill="none" xmlns="http://www.w3.org/2000/svg">
        <!-- Front Acoustic Sphere -->
        <circle cx="70" cy="100" r="22" fill="#F4F5F7"/>
        <circle cx="70" cy="100" r="12" fill="#1C1D21"/>
        <!-- Flexible C-Bridge Connector -->
        <path d="M70 78 C 70 48, 130 48, 130 78 L 130 122 C 130 152, 70 152, 70 122" fill="none" stroke="#7E828E" stroke-width="8" stroke-linecap="round"/>
        <!-- Rear Battery Comfort Bean -->
        <ellipse cx="130" cy="100" rx="18" ry="24" fill="#2C2E38" stroke="#E6FF00" stroke-width="1.2"/>
      </svg>
    `
  },
  {
    id: 'magnetic-neckband',
    name: 'Magnetic Wireless Neckband',
    category: 'Form-Factor 07 // Heavy-Duty Neckband',
    devices: 'OnePlus Bullets Wireless Z2 • realme Buds Wireless 3',
    chipset: 'BES2600 / Qualcomm QCC3040 / Airoha AB1561',
    batteryType: '1-ring',
    batteries: [
      { name: 'Collar Battery Unit', pct: 94, status: '45-Hour Endurance' }
    ],
    features: [
      {
        icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m15 15-6 6M9 9l6-6M9 15l6 6M15 9l-6-6"/></svg>`,
        title: 'Magnetic Hall-Switch & Collar Vibration',
        desc: 'Snap earbuds together to pause audio; physical collar vibration motor alerts on incoming calls and tactile sub-bass.'
      },
      {
        icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 12h-4l-3 9L9 3l-3 9H2"/></svg>`,
        title: 'Collar Bass Haptic Motor',
        desc: 'Physical vibration motor delivers incoming call alerts and tactile sub-bass resonance.'
      }
    ],
    svg: `
      <svg viewBox="0 0 200 200" fill="none" xmlns="http://www.w3.org/2000/svg">
        <!-- Flexible Silicone Neckband Collar Wire -->
        <path d="M40 160 C 25 100, 45 40, 100 40 C 155 40, 175 100, 160 160" fill="none" stroke="#252730" stroke-width="12" stroke-linecap="round"/>
        <!-- Metal Collar Weights -->
        <rect x="28" y="140" width="16" height="35" rx="6" fill="#F4F5F7"/>
        <rect x="156" y="140" width="16" height="35" rx="6" fill="#F4F5F7"/>
        <!-- Thin lead wires to magnetic snapped buds -->
        <path d="M36 175 C 45 190, 80 185, 95 160" fill="none" stroke="#7A7E8C" stroke-width="2"/>
        <path d="M164 175 C 155 190, 120 185, 105 160" fill="none" stroke="#7A7E8C" stroke-width="2"/>
        <!-- Snapped Magnetic Earbuds -->
        <rect x="88" y="148" width="12" height="24" rx="4" fill="#F4F5F7"/>
        <rect x="100" y="148" width="12" height="24" rx="4" fill="#F4F5F7"/>
        <line x1="100" y1="148" x2="100" y2="172" stroke="#E6FF00" stroke-width="2"/>
      </svg>
    `
  },
  {
    id: 'over-ear-closed',
    name: 'Over-Ear Closed-Back Headset',
    category: 'Form-Factor 08 // Studio Cans',
    devices: 'Sony WH-1000XM5 • Apple AirPods Max • Bose QuietComfort Ultra',
    chipset: 'Sony Integrated V1 + QN1 / Apple H1 Dual / Qualcomm S5',
    batteryType: '1-ring',
    batteries: [
      { name: 'Headset Lithium Cell', pct: 82, status: '50-Hour Endurance' }
    ],
    features: [
      {
        icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 18v-6a9 9 0 0 1 18 0v6"/><path d="M21 19a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3zM3 19a2 2 0 0 0 2 2h1a2 2 0 0 0 2-2v-3a2 2 0 0 0-2-2H3z"/></svg>`,
        title: '20-Level Ambient Slider & Speak-to-Chat',
        desc: 'Micron-level DAC stepped attenuator lets you blend ambient world sound from 1% to 100% paired with wear detection.'
      },
      {
        icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z"/><path d="M19 10v2a7 7 0 0 1-14 0v-2"/></svg>`,
        title: 'Speak-to-Chat Optical Proximity',
        desc: 'Capacitive headband wear detection instantly pauses music when pulled off ears.'
      }
    ],
    svg: `
      <svg viewBox="0 0 200 200" fill="none" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <linearGradient id="headbandGrad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stop-color="#FFFFFF"/>
            <stop offset="100%" stop-color="#9EA1B0"/>
          </linearGradient>
        </defs>
        <!-- Telescoping Steel Headband -->
        <path d="M48 115 C 42 55, 65 30, 100 30 C 135 30, 158 55, 152 115" fill="none" stroke="url(#headbandGrad)" stroke-width="6"/>
        <path d="M60 48 C 80 38, 120 38, 140 48" fill="none" stroke="#252730" stroke-width="14" stroke-linecap="round"/>
        <!-- Left Plush Earcup & Cushion -->
        <ellipse cx="44" cy="125" rx="18" ry="32" fill="#1C1D21"/>
        <ellipse cx="50" cy="125" rx="14" ry="26" fill="#F4F5F7"/>
        <!-- Right Plush Earcup & Cushion -->
        <ellipse cx="156" cy="125" rx="18" ry="32" fill="#1C1D21"/>
        <ellipse cx="150" cy="125" rx="14" ry="26" fill="#F4F5F7"/>
      </svg>
    `
  },
  {
    id: 'open-back-audiophile',
    name: 'Open-Back Audiophile Headphone',
    category: 'Form-Factor 09 // Reference Planar',
    devices: 'HiFiMAN Deva Pro • Grado GW100x • Sennheiser HD 660S2 BLE',
    chipset: 'Qualcomm QCC5125 (LDAC 990kbps + aptX Adaptive)',
    batteryType: '1-ring',
    batteries: [
      { name: 'DAC/Amp Modular Cell', pct: 76, status: 'High-Current Mode' }
    ],
    features: [
      {
        icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="m4.93 4.93 14.14 14.14M19.07 4.93 4.93 19.07"/></svg>`,
        title: 'Open-Air Dipole Bass Restorer & Anti-Leakage Whisper Mode',
        desc: 'Planar magnetic reverse-phase bass restorer recovers sub-audible air frequencies while whisper mode prevents room bleed.'
      },
      {
        icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M13 2 3 14h9l-1 8 10-12h-9l1-8z"/></svg>`,
        title: 'High-Impedance 300Ω Driver Match',
        desc: 'Pre-amp gain boost bypasses Android OS 16-bit limiter for lossless dynamic headroom.'
      }
    ],
    svg: `
      <svg viewBox="0 0 200 200" fill="none" xmlns="http://www.w3.org/2000/svg">
        <!-- Suspension Headband Frame -->
        <path d="M45 110 C 40 45, 70 25, 100 25 C 130 25, 160 45, 155 110" fill="none" stroke="#5E626F" stroke-width="3"/>
        <path d="M55 42 C 75 34, 125 34, 145 42" fill="none" stroke="#A7AAB5" stroke-width="7" stroke-linecap="round"/>
        <!-- Left Open Honeycomb Cup -->
        <circle cx="44" cy="120" r="28" fill="#1C1D21" stroke="#F4F5F7" stroke-width="3"/>
        <circle cx="44" cy="120" r="20" fill="none" stroke="#E6FF00" stroke-width="1.5" stroke-dasharray="3,3"/>
        <!-- Right Open Honeycomb Cup -->
        <circle cx="156" cy="120" r="28" fill="#1C1D21" stroke="#F4F5F7" stroke-width="3"/>
        <circle cx="156" cy="120" r="20" fill="none" stroke="#E6FF00" stroke-width="1.5" stroke-dasharray="3,3"/>
      </svg>
    `
  }
];

class FormFactorsShowcase {
  constructor() {
    this.currentIndex = 0;
    this.init();
  }

  init() {
    this.renderSelectorPills();
    this.selectCategory(0);
  }

  renderSelectorPills() {
    const container = document.getElementById('morphPillsScroll');
    if (!container) return;

    container.innerHTML = '';
    HARDWARE_FORM_FACTORS.forEach((item, index) => {
      const btn = document.createElement('button');
      btn.className = `morph-pill-btn ${index === 0 ? 'active' : ''}`;
      btn.innerHTML = `<span>${index + 1}.</span> <span>${item.name}</span>`;
      btn.addEventListener('click', () => {
        this.selectCategory(index);
      });
      container.appendChild(btn);
    });
  }

  selectCategory(index) {
    this.currentIndex = index;
    const data = HARDWARE_FORM_FACTORS[index];

    // Update active pill
    const pills = document.querySelectorAll('.morph-pill-btn');
    pills.forEach((p, i) => p.classList.toggle('active', i === index));

    // Update SVG silhouette with smooth fade
    const svgWrap = document.getElementById('morphSvgWrap');
    if (svgWrap) {
      svgWrap.style.opacity = '0';
      svgWrap.style.transform = 'scale(0.92)';
      setTimeout(() => {
        svgWrap.innerHTML = data.svg;
        svgWrap.style.opacity = '1';
        svgWrap.style.transform = 'scale(1)';
      }, 150);
    }

    // Update meta titles
    document.getElementById('morphCategoryTag').textContent = data.category;
    document.getElementById('morphTitle').textContent = data.name;
    document.getElementById('morphExamples').textContent = data.devices;
    document.getElementById('morphChipset').textContent = `Silicon: ${data.chipset}`;

    // Render Dynamic Battery Gauges (3-Ring vs 1-Ring)
    this.renderBatteryGauges(data);

    // Render Tailored Features
    this.renderFeatures(data);

    if (window.audioVibe) window.audioVibe.playChime(600 + index * 40, 'triangle', 0.12, 0.03);
  }

  renderBatteryGauges(data) {
    const layout = document.getElementById('morphBatteryLayout');
    if (!layout) return;

    layout.innerHTML = '';
    const circumference = 2 * Math.PI * 22; // r=22 => ~138.2

    data.batteries.forEach(b => {
      const gauge = document.createElement('div');
      gauge.className = 'morph-battery-gauge';

      const offset = circumference - (b.pct / 100) * circumference;

      gauge.innerHTML = `
        <div class="gauge-circular">
          <svg viewBox="0 0 52 52">
            <circle cx="26" cy="26" r="22" stroke="rgba(255,255,255,0.08)" stroke-width="4" fill="none"/>
            <circle cx="26" cy="26" r="22" stroke="#22E55E" stroke-width="4" fill="none"
              stroke-linecap="round"
              stroke-dasharray="${circumference}"
              stroke-dashoffset="${offset}"/>
          </svg>
          <div class="gauge-center-text">${b.pct}%</div>
        </div>
        <div class="gauge-info">
          <div class="gauge-title">${b.name}</div>
          <div class="gauge-status">${b.status}</div>
        </div>
      `;
      layout.appendChild(gauge);
    });
  }

  renderFeatures(data) {
    const container = document.getElementById('morphTailoredFeatures');
    if (!container) return;

    container.innerHTML = '';
    data.features.forEach(f => {
      const card = document.createElement('div');
      card.className = 'tailored-card';
      card.innerHTML = `
        <div class="tailored-icon">${f.icon}</div>
        <div class="tailored-title">${f.title}</div>
        <div class="tailored-desc">${f.desc}</div>
      `;
      container.appendChild(card);
    });
  }
}

window.addEventListener('DOMContentLoaded', () => {
  window.formFactorsShowcase = new FormFactorsShowcase();
});
