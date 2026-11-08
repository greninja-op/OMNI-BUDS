/* ==========================================================================
   OmniBuds Web Audio Vibe Controller & Procedural Sound FX
   High-Fidelity Synthetic Audio Feedback using Web Audio API
   ========================================================================== */

class AudioVibeEngine {
  constructor() {
    this.ctx = null;
    this.isEnabled = false;
    this.ambientGain = null;
    this.ambientOsc = null;
  }

  init() {
    if (this.ctx) return;
    const AudioContext = window.AudioContext || window.webkitAudioContext;
    if (!AudioContext) return;
    this.ctx = new AudioContext();

    // Create subtle ambient low-frequency room drone
    this.ambientGain = this.ctx.createGain();
    this.ambientGain.gain.setValueAtTime(0, this.ctx.currentTime);
    this.ambientGain.connect(this.ctx.destination);

    this.ambientOsc = this.ctx.createOscillator();
    this.ambientOsc.type = 'sine';
    this.ambientOsc.frequency.setValueAtTime(55, this.ctx.currentTime); // 55Hz sub tone
    this.ambientOsc.connect(this.ambientGain);
    this.ambientOsc.start();
  }

  toggle() {
    if (!this.ctx) this.init();
    if (this.ctx.state === 'suspended') {
      this.ctx.resume();
    }

    this.isEnabled = !this.isEnabled;
    const now = this.ctx.currentTime;

    if (this.isEnabled) {
      this.ambientGain.gain.setTargetAtTime(0.015, now, 0.2);
      this.playChime(640, 'triangle', 0.15, 0.05);
      setTimeout(() => this.playChime(960, 'sine', 0.2, 0.04), 80);
    } else {
      this.ambientGain.gain.setTargetAtTime(0, now, 0.2);
    }
    return this.isEnabled;
  }

  // Play a soft high-tech acoustic feedback chime
  playChime(freq = 880, type = 'sine', duration = 0.15, maxGain = 0.04) {
    if (!this.isEnabled || !this.ctx) return;
    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();
    const now = this.ctx.currentTime;

    osc.type = type;
    osc.frequency.setValueAtTime(freq, now);
    osc.frequency.exponentialRampToValueAtTime(freq * 1.05, now + duration);

    gain.gain.setValueAtTime(0.001, now);
    gain.gain.linearRampToValueAtTime(maxGain, now + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.0001, now + duration);

    osc.connect(gain);
    gain.connect(this.ctx.destination);

    osc.start(now);
    osc.stop(now + duration);
  }

  // ANC mode acoustic transition
  playAncTransition(mode) {
    if (!this.isEnabled || !this.ctx) return;
    const now = this.ctx.currentTime;

    if (mode === 'anc') {
      // Deep cancellation drop tone
      const osc = this.ctx.createOscillator();
      const gain = this.ctx.createGain();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(240, now);
      osc.frequency.exponentialRampToValueAtTime(40, now + 0.25);
      gain.gain.setValueAtTime(0.04, now);
      gain.gain.exponentialRampToValueAtTime(0.001, now + 0.25);
      osc.connect(gain);
      gain.connect(this.ctx.destination);
      osc.start(now);
      osc.stop(now + 0.25);
    } else if (mode === 'transparency') {
      // High-air harmonic pass tone
      const osc = this.ctx.createOscillator();
      const gain = this.ctx.createGain();
      osc.type = 'triangle';
      osc.frequency.setValueAtTime(440, now);
      osc.frequency.exponentialRampToValueAtTime(1200, now + 0.22);
      gain.gain.setValueAtTime(0.025, now);
      gain.gain.exponentialRampToValueAtTime(0.001, now + 0.22);
      osc.connect(gain);
      gain.connect(this.ctx.destination);
      osc.start(now);
      osc.stop(now + 0.22);
    } else {
      // Normal click
      this.playChime(520, 'sine', 0.1, 0.03);
    }
  }

  // Radar Lock Laser Ping
  playRadarLock() {
    if (!this.isEnabled || !this.ctx) return;
    const now = this.ctx.currentTime;
    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();

    osc.type = 'sine';
    osc.frequency.setValueAtTime(1200, now);
    osc.frequency.exponentialRampToValueAtTime(320, now + 0.3);

    gain.gain.setValueAtTime(0.05, now);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.3);

    osc.connect(gain);
    gain.connect(this.ctx.destination);
    osc.start(now);
    osc.stop(now + 0.3);
  }
}

window.audioVibe = new AudioVibeEngine();
