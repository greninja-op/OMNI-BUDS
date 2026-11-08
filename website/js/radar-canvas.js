/* ==========================================================================
   OmniBuds Solar System Orbital Radar Discovery Demo
   360° Conic Sweep, Fairy-Dust Shimmer Particles & Hardware Bonding Tether
   ========================================================================== */

const RADAR_DEVICES = [
  {
    id: 'realme-t300',
    name: 'realme Buds T300',
    type: 'TWS Stem',
    orbit: 1, // -40 dBm (r = 110)
    angle: 0.85,
    rssi: '-38 dBm',
    mac: '74:23:44:A8:12:F1',
    pairingInstructions: 'Open case lid. Hold physical button on back of case for 3s until white LED pulses.',
    log: 'createBond() -> BR/EDR + BLE Dual-Mode -> BES2600 Linked'
  },
  {
    id: 'bullets-z2',
    name: 'OnePlus Bullets Z2',
    type: 'Neckband',
    orbit: 1, // -40 dBm
    angle: 3.6,
    rssi: '-42 dBm',
    mac: '6C:29:95:C3:88:B0',
    pairingInstructions: 'Separate magnetic earbuds to power on. Hold center button for 3s until LED pulses white.',
    log: 'Fast Pair 0xFE2C -> Magnetic Hall Connected -> A2DP Stream'
  },
  {
    id: 'sony-xm5',
    name: 'Sony WH-1000XM5',
    type: 'Over-Ear',
    orbit: 2, // -60 dBm (r = 190)
    angle: 1.9,
    rssi: '-58 dBm',
    mac: 'AC:80:0A:71:00:23',
    pairingInstructions: 'Hold power key for 7s until blue LED flashes in pairs and voice states "Bluetooth Pairing".',
    log: 'SPP/RFCOMM Bound -> LDAC 990kbps Negotiated -> QN1 Ready'
  },
  {
    id: 'airpods-pro2',
    name: 'AirPods Pro 2',
    type: 'TWS In-Ear',
    orbit: 2, // -60 dBm
    angle: 4.8,
    rssi: '-62 dBm',
    mac: '9C:35:EB:D0:11:09',
    pairingInstructions: 'Keep lid open with buds seated. Hold back setup button until amber LED turns flashing white.',
    log: 'Apple L2CAP 0x1001 Hooked -> CoreAudio Restorer Active'
  },
  {
    id: 'bose-open',
    name: 'Bose Ultra Open',
    type: 'Clip Cuff',
    orbit: 3, // -80 dBm (r = 260)
    angle: 2.7,
    rssi: '-78 dBm',
    mac: '04:52:C7:99:EE:41',
    pairingInstructions: 'Place cuffs in case. Hold case button for 5s until front LED blinks continuous blue.',
    log: 'Qualcomm QCC GAIA Service 0x1101 -> Dipole Channel Sync'
  },
  {
    id: 'galaxy-buds2',
    name: 'Galaxy Buds2 Pro',
    type: 'Stemless Bean',
    orbit: 3, // -80 dBm
    angle: 5.8,
    rssi: '-81 dBm',
    mac: '50:32:37:B4:9C:A8',
    pairingInstructions: 'With both beans in ears, tap and hold both touchpads simultaneously for 3s until triple chime.',
    log: 'Samsung SPP Channel 0x0004 -> 24-bit SSC Decoder Bound'
  }
];

class OrbitalRadarCanvas {
  constructor() {
    this.canvas = document.getElementById('radarCanvas');
    if (!this.canvas) return;
    this.ctx = this.canvas.getContext('2d');

    this.sweepAngle = 0;
    this.sweepSpeed = 0.022; // ~60fps smooth rotation
    this.particles = [];
    this.activeDevice = RADAR_DEVICES[0];

    this.resize();
    window.addEventListener('resize', () => {
      this.resize();
      this.positionDeviceNodes();
    });

    this.initDeviceNodes();
    this.animate = this.animate.bind(this);
    requestAnimationFrame(this.animate);
  }

  resize() {
    const rect = this.canvas.parentElement.getBoundingClientRect();
    this.width = rect.width;
    this.height = rect.height;
    this.centerX = this.width / 2;
    this.centerY = this.height / 2;
    this.dpr = Math.min(window.devicePixelRatio || 1, 2);

    this.canvas.width = this.width * this.dpr;
    this.canvas.height = this.height * this.dpr;
    this.ctx.scale(this.dpr, this.dpr);

    // Responsive scaling factor for orbits and planets
    this.scale = Math.max(0.55, Math.min(1.0, (this.width - 20) / 600));
    this.orbitRadii = {
      1: 110 * this.scale,
      2: 190 * this.scale,
      3: 260 * this.scale
    };

    // Update CSS orbit rings dimensions responsively
    const o1 = document.querySelector('.orbit-1');
    const o2 = document.querySelector('.orbit-2');
    const o3 = document.querySelector('.orbit-3');
    if (o1) { o1.style.width = `${this.orbitRadii[1] * 2}px`; o1.style.height = `${this.orbitRadii[1] * 2}px`; }
    if (o2) { o2.style.width = `${this.orbitRadii[2] * 2}px`; o2.style.height = `${this.orbitRadii[2] * 2}px`; }
    if (o3) { o3.style.width = `${this.orbitRadii[3] * 2}px`; o3.style.height = `${this.orbitRadii[3] * 2}px`; }
  }

  initDeviceNodes() {
    const stage = document.getElementById('radarStage');
    if (!stage) return;

    // Remove existing nodes
    const existing = stage.querySelectorAll('.orbit-device-node');
    existing.forEach(e => e.remove());

    RADAR_DEVICES.forEach(dev => {
      const node = document.createElement('div');
      node.className = `orbit-device-node ${dev.id === this.activeDevice.id ? 'active' : ''}`;
      node.dataset.id = dev.id;

      let iconSvg = '';
      if (dev.type.includes('TWS') || dev.type.includes('Bean')) {
        iconSvg = `<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 10a6 6 0 0 0-12 0v8h12v-8Z"/><path d="M12 18v3"/></svg>`;
      } else if (dev.type.includes('Neckband')) {
        iconSvg = `<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 14a8 8 0 0 1 16 0M4 14v4M20 14v4"/></svg>`;
      } else {
        iconSvg = `<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 18v-6a9 9 0 0 1 18 0v6M21 19a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3zM3 19a2 2 0 0 0 2 2h1a2 2 0 0 0 2-2v-3a2 2 0 0 0-2-2H3z"/></svg>`;
      }

      node.innerHTML = `
        <div class="device-bubble">
          ${iconSvg}
        </div>
        <div class="node-label">${dev.name} • ${dev.rssi}</div>
      `;

      node.addEventListener('click', () => {
        this.selectDevice(dev);
      });

      stage.appendChild(node);
    });

    this.positionDeviceNodes();
    this.updatePairingBanner(this.activeDevice);
  }

  positionDeviceNodes() {
    const orbitRadii = this.orbitRadii || {
      1: 110,
      2: 190,
      3: 260
    };

    RADAR_DEVICES.forEach(dev => {
      const node = document.querySelector(`.orbit-device-node[data-id="${dev.id}"]`);
      if (!node) return;

      const r = orbitRadii[dev.orbit];
      const x = this.centerX + Math.cos(dev.angle) * r;
      const y = this.centerY + Math.sin(dev.angle) * r;

      node.style.left = `${x}px`;
      node.style.top = `${y}px`;
    });
  }

  selectDevice(dev) {
    this.activeDevice = dev;
    document.querySelectorAll('.orbit-device-node').forEach(node => {
      node.classList.toggle('active', node.dataset.id === dev.id);
    });

    this.updatePairingBanner(dev);
    if (window.audioVibe) window.audioVibe.playRadarLock();
  }

  updatePairingBanner(dev) {
    const title = document.getElementById('pairingDeviceTitle');
    const desc = document.getElementById('pairingInstructions');
    const term = document.getElementById('pairingLogTerminal');

    if (title) title.textContent = `${dev.name} [ ${dev.type} ]`;
    if (desc) desc.textContent = dev.pairingInstructions;
    if (term) term.innerHTML = `&gt; ${dev.log} [${dev.mac}]`;
  }

  animate() {
    this.sweepAngle = (this.sweepAngle + this.sweepSpeed) % (Math.PI * 2);

    this.ctx.clearRect(0, 0, this.width, this.height);

    const cx = this.centerX;
    const cy = this.centerY;
    const orbitRadii = this.orbitRadii || { 1: 110, 2: 190, 3: 260 };
    const maxRadius = orbitRadii[3] + 15;

    // Ping & illuminate device nodes as radar sweep passes over them
    RADAR_DEVICES.forEach(dev => {
      let diff = (this.sweepAngle - dev.angle) % (Math.PI * 2);
      if (diff < 0) diff += Math.PI * 2;
      const node = document.querySelector(`.orbit-device-node[data-id="${dev.id}"]`);
      if (node) {
        if (diff < 0.28) {
          node.classList.add('sweeping-ping');
        } else {
          node.classList.remove('sweeping-ping');
        }
      }
    });

    // 1. Rotating Conic Radar Sweep
    const sweepArc = Math.PI / 4; // 45 degree beam tail
    const gradient = this.ctx.createRadialGradient(cx, cy, 20, cx, cy, maxRadius);
    gradient.addColorStop(0, 'rgba(230, 255, 0, 0.25)');
    gradient.addColorStop(1, 'rgba(230, 255, 0, 0.0)');

    this.ctx.save();
    this.ctx.beginPath();
    this.ctx.moveTo(cx, cy);
    this.ctx.arc(cx, cy, maxRadius, this.sweepAngle - sweepArc, this.sweepAngle);
    this.ctx.closePath();
    this.ctx.fillStyle = gradient;
    this.ctx.fill();

    // Sharp Sweep Ray Line
    const rayX = cx + Math.cos(this.sweepAngle) * maxRadius;
    const rayY = cy + Math.sin(this.sweepAngle) * maxRadius;
    this.ctx.beginPath();
    this.ctx.moveTo(cx, cy);
    this.ctx.lineTo(rayX, rayY);
    this.ctx.strokeStyle = 'rgba(230, 255, 0, 0.75)';
    this.ctx.lineWidth = 1.8;
    this.ctx.stroke();
    this.ctx.restore();

    // 2. Spawn Fairy-Dust Particles along sweep ray
    if (Math.random() < 0.6) {
      const dist = 30 + Math.random() * (maxRadius - 30);
      this.particles.push({
        x: cx + Math.cos(this.sweepAngle) * dist,
        y: cy + Math.sin(this.sweepAngle) * dist,
        vx: (Math.random() - 0.5) * 0.4 + Math.cos(this.sweepAngle - 0.5) * 0.2,
        vy: (Math.random() - 0.5) * 0.4 + Math.sin(this.sweepAngle - 0.5) * 0.2,
        alpha: 0.9,
        size: 1 + Math.random() * 2,
        color: Math.random() > 0.3 ? '#E6FF00' : '#22E55E'
      });
    }

    // 3. Render and update particles
    for (let i = this.particles.length - 1; i >= 0; i--) {
      const p = this.particles[i];
      p.x += p.vx;
      p.y += p.vy;
      p.alpha -= 0.012;

      if (p.alpha <= 0) {
        this.particles.splice(i, 1);
        continue;
      }

      this.ctx.beginPath();
      this.ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2);
      this.ctx.fillStyle = p.color;
      this.ctx.globalAlpha = p.alpha;
      this.ctx.fill();
      this.ctx.globalAlpha = 1.0;
    }

    // 4. Draw Fluorescent Yellow Bonding Tether to Active Device
    if (this.activeDevice) {
      const r = orbitRadii[this.activeDevice.orbit];
      const targetX = cx + Math.cos(this.activeDevice.angle) * r;
      const targetY = cy + Math.sin(this.activeDevice.angle) * r;

      this.ctx.save();
      // Outer glow laser
      this.ctx.beginPath();
      this.ctx.moveTo(cx, cy);
      this.ctx.lineTo(targetX, targetY);
      this.ctx.strokeStyle = 'rgba(230, 255, 0, 0.25)';
      this.ctx.lineWidth = 6;
      this.ctx.stroke();

      // Sharp core beam with dashed animation
      this.ctx.beginPath();
      this.ctx.moveTo(cx, cy);
      this.ctx.lineTo(targetX, targetY);
      this.ctx.strokeStyle = '#E6FF00';
      this.ctx.lineWidth = 1.8;
      this.ctx.setLineDash([6, 4]);
      this.ctx.lineDashOffset = -performance.now() / 25;
      this.ctx.stroke();
      this.ctx.restore();
    }

    requestAnimationFrame(this.animate);
  }
}

window.addEventListener('DOMContentLoaded', () => {
  window.orbitalRadar = new OrbitalRadarCanvas();
});
