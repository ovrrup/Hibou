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
    color: '#D58151',
    accent: '#F59E0B'
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
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', applyBrandIdentity);
  } else {
    applyBrandIdentity();
  }

  // Periodic safeguard for title
  setInterval(function() {
    if (document.title === 'Jetski Web' || document.title === 'Antigravity') {
      document.title = 'Hibou';
    }
  }, 1000);
})();
