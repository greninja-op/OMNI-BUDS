/* ==========================================================================
   OmniBuds Hero Acoustic Waveform Canvas
   Real-Time Reactive Harmonic Soundwave Engine
   ========================================================================== */

class HeroWaveformCanvas {
  constructor(canvasId) {
    this.canvas = document.getElementById(canvasId);
    if (!this.canvas) return;
    this.ctx = this.canvas.getContext('2d');
    
    // Wave modes: 'normal', 'anc', 'transparency'
    this.mode = 'normal';
    this.ancDepth = 50; // -50dB

    // Animation properties
    this.time = 0;
    this.targetAmp = 55;
    this.currentAmp = 55;
    this.targetFreq = 0.007;
    this.currentFreq = 0.007;
    this.speed = 0.025;

    // Harmonic layers
    this.layers = [
      { color: 'rgba(230, 255, 0, 0.45)', lineWidth: 2.5, freqMult: 1.0, speedMult: 1.0, phaseOffset: 0 },
      { color: 'rgba(0, 240, 255, 0.25)', lineWidth: 1.8, freqMult: 1.4, speedMult: 1.3, phaseOffset: 2.1 },
      { color: 'rgba(34, 229, 94, 0.22)', lineWidth: 1.2, freqMult: 0.7, speedMult: 0.8, phaseOffset: 4.2 },
      { color: 'rgba(230, 255, 0, 0.12)', lineWidth: 1.0, freqMult: 2.2, speedMult: 1.8, phaseOffset: 1.5 }
    ];

    this.resize();
    window.addEventListener('resize', () => this.resize());
    this.animate = this.animate.bind(this);
    requestAnimationFrame(this.animate);
  }

  resize() {
    if (!this.canvas) return;
    const rect = this.canvas.parentElement.getBoundingClientRect();
    this.width = rect.width;
    this.height = rect.height;
    this.dpr = Math.min(window.devicePixelRatio || 1, 2);

    this.canvas.width = this.width * this.dpr;
    this.canvas.height = this.height * this.dpr;
    this.ctx.scale(this.dpr, this.dpr);
  }

  setMode(newMode, depth = 50) {
    this.mode = newMode;
    this.ancDepth = depth;

    if (this.mode === 'anc') {
      // Near flatline with subtle micro-damped oscillation
      this.targetAmp = Math.max(3, 50 - this.ancDepth * 0.95); // ~4.4px amplitude
      this.targetFreq = 0.003;
      this.speed = 0.008;
    } else if (this.mode === 'transparency') {
      // Energetic vocal-range harmonics & turbulent ripples
      this.targetAmp = 85;
      this.targetFreq = 0.014;
      this.speed = 0.045;
    } else {
      // Normal acoustic baseline
      this.targetAmp = 55;
      this.targetFreq = 0.007;
      this.speed = 0.025;
    }
  }

  animate() {
    this.time += this.speed;

    // Smooth lerping towards target parameters
    this.currentAmp += (this.targetAmp - this.currentAmp) * 0.08;
    this.currentFreq += (this.targetFreq - this.currentFreq) * 0.08;

    this.ctx.clearRect(0, 0, this.width, this.height);

    const centerY = this.height * 0.52;
    const width = this.width;

    // Draw multiple harmonic wave layers
    for (let l = 0; l < this.layers.length; l++) {
      const layer = this.layers[l];
      this.ctx.beginPath();
      this.ctx.lineWidth = layer.lineWidth;

      if (this.mode === 'anc') {
        this.ctx.strokeStyle = l === 0 ? 'rgba(34, 229, 94, 0.4)' : 'rgba(255, 255, 255, 0.05)';
      } else if (this.mode === 'transparency') {
        this.ctx.strokeStyle = l === 0 ? 'rgba(230, 255, 0, 0.8)' : (l === 1 ? 'rgba(255, 176, 32, 0.5)' : layer.color);
      } else {
        this.ctx.strokeStyle = layer.color;
      }

      for (let x = 0; x <= width; x += 3) {
        // Window function (Gaussian taper at sides)
        const normalizedX = (x / width) * 2 - 1;
        const envelope = Math.exp(-Math.pow(normalizedX * 1.6, 2));

        const angle = x * this.currentFreq * layer.freqMult + this.time * layer.speedMult + layer.phaseOffset;
        let yOffset = Math.sin(angle) * this.currentAmp * envelope;

        if (this.mode === 'transparency') {
          // Extra high-frequency vocal flutter
          yOffset += Math.sin(x * 0.04 + this.time * 3) * (this.currentAmp * 0.22) * envelope;
        }

        const y = centerY + yOffset;
        if (x === 0) {
          this.ctx.moveTo(x, y);
        } else {
          this.ctx.lineTo(x, y);
        }
      }
      this.ctx.stroke();
    }

    // Render Mode Label in Canvas Watermark
    this.ctx.save();
    this.ctx.font = '600 11px "JetBrains Mono", monospace';
    this.ctx.textAlign = 'right';
    
    if (this.mode === 'anc') {
      this.ctx.fillStyle = 'rgba(34, 229, 94, 0.6)';
      this.ctx.fillText(`HARDWARE SILENCE [ -${this.ancDepth}dB HYBRID ANC ]`, this.width - 40, centerY - 25);
    } else if (this.mode === 'transparency') {
      this.ctx.fillStyle = 'rgba(230, 255, 0, 0.8)';
      this.ctx.fillText('VOCAL PASS-THROUGH [ +6.0dB DUAL-MIC BEAMFORM ]', this.width - 40, centerY - 25);
    } else {
      this.ctx.fillStyle = 'rgba(156, 158, 168, 0.45)';
      this.ctx.fillText('ACOUSTIC PASSTHROUGH [ REGISTER TEARDOWN OFF ]', this.width - 40, centerY - 25);
    }
    this.ctx.restore();

    requestAnimationFrame(this.animate);
  }
}

window.addEventListener('DOMContentLoaded', () => {
  window.heroCanvas = new HeroWaveformCanvas('heroCanvas');
});
