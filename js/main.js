/* ==========================================================================
   OmniBuds Flagship Launch Experience — Main Coordinator
   Navigation, Auto-Detect Simulator & Device Switcher Interactions
   ========================================================================== */

document.addEventListener('DOMContentLoaded', () => {
  initSmoothScroll();
  initDownloadCta();
  initAutoDetectSimulator();
});

/* --------------------------------------------------------------------------
   Smooth Anchor Scrolling
   -------------------------------------------------------------------------- */
function initSmoothScroll() {
  const anchors = document.querySelectorAll('a[href^="#"]');
  anchors.forEach(a => {
    a.addEventListener('click', (e) => {
      const targetId = a.getAttribute('href').substring(1);
      const targetEl = document.getElementById(targetId);
      if (targetEl) {
        e.preventDefault();
        targetEl.scrollIntoView({ behavior: 'smooth' });
      }
    });
  });
}

/* --------------------------------------------------------------------------
   Download CTA Buttons Feedback
   -------------------------------------------------------------------------- */
function initDownloadCta() {
  const downloadBtns = document.querySelectorAll('.download-apk-trigger');
  downloadBtns.forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.preventDefault();
      const originalText = btn.innerHTML;
      btn.innerHTML = `
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/>
          <path d="m9 12 2 2 4-4"/>
        </svg>
        <span>Downloading OmniBuds-v1.8.4.apk...</span>
      `;
      btn.style.background = '#22E55E';
      btn.style.color = '#000';

      if (window.audioVibe) window.audioVibe.playChime(1100, 'sine', 0.25, 0.05);

      setTimeout(() => {
        btn.innerHTML = originalText;
        btn.style.background = '';
        btn.style.color = '';
      }, 3500);
    });
  });
}

/* --------------------------------------------------------------------------
   Auto-Detection & Hardware Memory Interactive Simulator
   -------------------------------------------------------------------------- */
function initAutoDetectSimulator() {
  const scanBtn = document.getElementById('btnTutorialScanSim');
  const btnText = document.getElementById('tutorialBtnText');
  const statusNote = document.getElementById('tutorialStatusNote');
  const scanStatus = document.getElementById('demoScanStatusText');
  const cards = document.querySelectorAll('.demo-device-card');

  const devices = [
    { name: 'realme Buds T300', sub: 'Auto-Connected • On-Chip Memory Locked' },
    { name: 'Sony WF-1000XM5', sub: 'LDAC 990k • Tailored Control Surface' },
    { name: 'AirPods Pro (2nd Gen)', sub: 'L2CAP Driver • Spatial Audio Ready' },
    { name: 'Galaxy Buds2 Pro', sub: 'Dual-Mode • 24-bit Hi-Fi Control' }
  ];

  let currentIdx = 0;

  const setActiveDevice = (index) => {
    cards.forEach((card, idx) => {
      const isCurrent = idx === index;
      card.classList.toggle('connected', isCurrent);
      const btn = card.querySelector('.demo-connect-btn');
      if (btn) {
        btn.classList.toggle('connected', isCurrent);
        btn.textContent = isCurrent ? 'Active' : 'Switch';
      }
    });

    const activeDev = devices[index];
    if (scanStatus) {
      scanStatus.textContent = `Locked: ${activeDev.name} • EEPROM Synced`;
    }
    if (statusNote) {
      statusNote.textContent = `Auto-detected ${activeDev.name} — on-chip preferences locked`;
    }

    // Also update notification demo header name!
    const notiDevName = document.getElementById('notiDeviceName');
    if (notiDevName) notiDevName.textContent = activeDev.name;

    if (window.audioVibe) {
      window.audioVibe.playChime(800 + index * 100, 'triangle', 0.12, 0.03);
    }
  };

  // Click card or switch button to change active device
  cards.forEach((card, idx) => {
    card.addEventListener('click', () => {
      currentIdx = idx;
      setActiveDevice(currentIdx);
    });
  });

  // Cycle via the simulation button
  if (scanBtn) {
    scanBtn.addEventListener('click', () => {
      if (btnText) btnText.textContent = 'Auto-Detecting...';
      scanBtn.style.opacity = '0.7';

      if (window.audioVibe) window.audioVibe.playChime(1050, 'sine', 0.1, 0.02);

      setTimeout(() => {
        currentIdx = (currentIdx + 1) % devices.length;
        setActiveDevice(currentIdx);
        if (btnText) btnText.textContent = 'Simulate Instant Auto-Detect';
        scanBtn.style.opacity = '1';
      }, 400);
    });
  }
}
