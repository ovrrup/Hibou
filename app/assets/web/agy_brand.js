/**
 * Hibou Brand Identity & Dynamic UI Enhancement
 * Application ID: agy.hibou
 * Version: 1.0.0-beta.1
 */
(function() {
  'use strict';

  window.__HIBOU_BRAND__ = {
    name: 'Hibou',
    version: '1.0.0-beta.1',
    appId: 'agy.hibou',
    copper: '#D58151',
    amber: '#F59E0B',
    bgDark: '#0B0E17',
    surfaceDark: '#121624'
  };

  function applyBrandIdentity() {
    // Ensure window / tab title is Hibou
    if (document.title && !document.title.includes('Hibou')) {
      document.title = 'Hibou';
    }

    // Set brand metadata attribute
    document.documentElement.setAttribute('data-app-id', 'agy.hibou');
    document.documentElement.setAttribute('data-app-version', '1.0.0-beta.1');
    document.documentElement.setAttribute('data-brand', 'hibou');

    // Inject Hibou brand style variables
    var styleId = 'hibou-brand-vars';
    if (!document.getElementById(styleId)) {
      var style = document.createElement('style');
      style.id = styleId;
      style.textContent = [
        ':root {',
        '  --hibou-copper: #D58151;',
        '  --hibou-amber: #F59E0B;',
        '  --hibou-bg: #0B0E17;',
        '  --hibou-surface: #121624;',
        '  --hibou-border: rgba(213, 129, 81, 0.2);',
        '}',
        'html.dark {',
        '  --accent-glow: #F59E0B;',
        '}',
        'button.agy-brand-btn {',
        '  border-color: var(--hibou-copper) !important;',
        '}'
      ].join('\n');
      document.head.appendChild(style);
    }
  }

  function scrubDomBranding() {
    if (document.title === 'Jetski Web' || document.title === 'Antigravity') {
      document.title = 'Hibou';
    }
    // Check specific splash or header elements
    var titles = document.querySelectorAll('h1, h2, h3, .app-title, [data-testid="app-title"]');
    titles.forEach(function(el) {
      if (el.textContent === 'Jetski' || el.textContent === 'Antigravity' || el.textContent === 'Leostryx') {
        el.textContent = 'Hibou';
      }
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function() {
      applyBrandIdentity();
      scrubDomBranding();
    });
  } else {
    applyBrandIdentity();
    scrubDomBranding();
  }

  // Periodic safeguard
  setInterval(scrubDomBranding, 1500);
})();
