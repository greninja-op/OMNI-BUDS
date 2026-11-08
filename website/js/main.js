/* ==========================================================================
   OmniBuds Flagship Launch Experience — Main Coordinator
   Navigation, Audio Vibe Controller & Global Interactions
   ========================================================================== */

document.addEventListener('DOMContentLoaded', () => {
  initNavbarAudioVibe();
  initSmoothScroll();
  initDownloadCta();
});

/* --------------------------------------------------------------------------
   Audio Vibe Navigation Button
   -------------------------------------------------------------------------- */
function initNavbarAudioVibe() {
  const btn = document.getElementById('navbarVibeToggle');
  const label = document.getElementById('vibeStatusText');
  if (!btn || !label) return;

  btn.addEventListener('click', () => {
    if (!window.audioVibe) return;
    const isActive = window.audioVibe.toggle();
    btn.classList.toggle('active', isActive);
    label.textContent = isActive ? 'LIVE WAVE: ON' : 'LIVE WAVE: OFF';
  });
}

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
