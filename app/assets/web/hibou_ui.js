/**
 * Hibou — Mobile-First Ergonomic UI Redesign System
 * 
 * Implements the 6 core user requirements for phones:
 * 1. Top-Left Slide Menu (Drawer) with edge-swipe gestures.
 * 2. Two-Tier Bottom Chat Dock (Upper: Stage/Branch selector; Lower: Rounded input dock).
 * 3. Top-Center Model Selector & Live Quota/Usage Viewer Pill.
 * 4. Top-Right Profile Avatar Button with 3-Tab Control Center Sheet (Accounts, Providers, Quotas).
 * 5. Contextual Multi-Purpose Button (Projects dropdown on Home; Management dropdown in Chat).
 * 6. Dynamic Ever-Changing Greeting with Animated Electric Blue Top Glow.
 */
(function () {
  "use strict";

  if (window.__hibouUiInitialized) return;
  window.__hibouUiInitialized = true;

  // ---------------------------------------------------------------------------
  // 0. Localization & Utilities
  // ---------------------------------------------------------------------------
  function isRu() {
    try {
      if (window.__agyI18n && typeof window.__agyI18n.getLanguage === "function") {
        return window.__agyI18n.getLanguage() === "ru";
      }
      return /^ru\b/i.test(navigator.language || "");
    } catch (e) {
      return false;
    }
  }

  function i18n(en, ru) {
    return isRu() ? ru : en;
  }

  function escapeHtml(str) {
    return String(str == null ? "" : str).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }

  // ---------------------------------------------------------------------------
  // 1. Dynamic Greeting Generator
  // ---------------------------------------------------------------------------
  var GREETINGS_EN = [
    "Good morning, engineer.",
    "Good afternoon, developer.",
    "Good evening, builder.",
    "Ready to build something extraordinary?",
    "What are we creating today?",
    "Hibou is ready to pair-program.",
    "Code, refactor, and build freely.",
    "Your mobile autonomous coding workspace.",
    "Let's turn your vision into working code."
  ];

  var GREETINGS_RU = [
    "Доброе утро, инженер.",
    "Добрый день, разработчик.",
    "Добрый вечер, создатель.",
    "Готовы создать нечто выдающееся?",
    "Что программируем сегодня?",
    "Hibou готов к совместной разработке.",
    "Пишите код, рефакторите и собирайте проекты.",
    "Ваша мобильная автономная среда разработки.",
    "Превратим идею в работающий код."
  ];

  function getDynamicGreeting() {
    var hour = new Date().getHours();
    var list = isRu() ? GREETINGS_RU : GREETINGS_EN;
    if (hour >= 5 && hour < 12) {
      return list[0];
    } else if (hour >= 12 && hour < 18) {
      return list[1];
    } else if (hour >= 18 && hour < 23) {
      return list[2];
    }
    // Random inspirational greeting from the remaining pool
    var idx = 3 + Math.floor(Math.random() * (list.length - 3));
    return list[idx] || list[3];
  }

  // ---------------------------------------------------------------------------
  // 2. Global State & DOM References
  // ---------------------------------------------------------------------------
  var state = {
    isChatActive: false,
    messageCount: 0,
    currentProject: "Hibou",
    currentBranch: "main",
    stageMode: "local", // 'local' | 'worktree'
    drawerOpen: false,
    controlCenterOpen: false,
    controlCenterTab: "accounts", // 'accounts' | 'providers' | 'quotas'
    multiMenuOpen: false,
    activeModelName: "Gemini 2.5 Flash",
    modelQuotaText: "100%",
    userEmail: "ofeducationc@gmail.com",
    userInitial: "O",
    userAvatarUrl: ""
  };

  // Inspect existing auth token for email / initial if available
  try {
    var rawTok = localStorage.getItem("agy_auth_cache");
    if (rawTok) {
      var tokObj = JSON.parse(rawTok);
      if (tokObj.email) {
        state.userEmail = tokObj.email;
        state.userInitial = tokObj.email.charAt(0).toUpperCase();
      }
    }
  } catch (e) {}

  // ---------------------------------------------------------------------------
  // 3. UI Template Generators
  // ---------------------------------------------------------------------------

  // SVG Icons (Material Symbols / Vector Only - NO EMOJIS)
  var ICONS = {
    hibouLogo: '<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2C7 2 3 6 3 11c0 3 1.5 5.5 3.5 7.5L6 22l4-2c.6.1 1.3.2 2 .2s1.4-.1 2-.2l4 2-.5-3.5C19.5 16.5 21 14 21 11c0-5-4-9-9-9z"/><circle cx="9" cy="10" r="1.5" fill="currentColor"/><circle cx="15" cy="10" r="1.5" fill="currentColor"/><path d="m12 13 .8 1.2h-1.6z"/></svg>',
    menu: '<svg viewBox="0 0 24 24" width="22" height="22" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><line x1="3" y1="12" x2="21" y2="12"></line><line x1="3" y1="6" x2="21" y2="6"></line><line x1="3" y1="18" x2="21" y2="18"></line></svg>',
    close: '<svg viewBox="0 0 24 24" width="20" height="20" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>',
    modelSparkle: '<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M12 2L14.4 7.6L20 10L14.4 12.4L12 18L9.6 12.4L4 10L9.6 7.6L12 2Z"/></svg>',
    projects: '<svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path></svg>',
    dots: '<svg viewBox="0 0 24 24" width="20" height="20" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="1.5"></circle><circle cx="12" cy="5" r="1.5"></circle><circle cx="12" cy="19" r="1.5"></circle></svg>',
    branch: '<svg viewBox="0 0 24 24" width="15" height="15" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><line x1="6" y1="3" x2="6" y2="15"></line><circle cx="18" cy="6" r="3"></circle><circle cx="6" cy="18" r="3"></circle><path d="M18 9a9 9 0 0 1-9 9"></path></svg>',
    worktree: '<svg viewBox="0 0 24 24" width="15" height="15" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M3 3h7v7H3z"></path><path d="M14 3h7v7h-7z"></path><path d="M14 14h7v7h-7z"></path><path d="M10 7h4"></path><path d="M7 10v7h7"></path></svg>',
    chevronDown: '<svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9"></polyline></svg>',
    send: '<svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><line x1="22" y1="2" x2="11" y2="13"></line><polygon points="22 2 15 22 11 13 2 9 22 2"></polygon></svg>',
    stop: '<svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><rect x="6" y="6" width="12" height="12" rx="2"></rect></svg>',
    plus: '<svg viewBox="0 0 24 24" width="19" height="19" stroke="currentColor" stroke-width="2.2" fill="none" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>',
    mic: '<svg viewBox="0 0 24 24" width="19" height="19" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"></path><path d="M19 10v2a7 7 0 0 1-14 0v-2"></path><line x1="12" y1="19" x2="12" y2="23"></line><line x1="8" y1="23" x2="16" y2="23"></line></svg>',
    terminal: '<svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><polyline points="4 17 10 11 4 5"></polyline><line x1="12" y1="19" x2="20" y2="19"></line></svg>',
    save: '<svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"></path><polyline points="17 21 17 13 7 13 7 21"></polyline><polyline points="7 3 7 8 15 8"></polyline></svg>',
    pin: '<svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="17" x2="12" y2="22"></line><path d="M5 17h14v-2l-3-3V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v7l-3 3v2z"></path></svg>',
    copy: '<svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>',
    archive: '<svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><polyline points="21 8 21 21 3 21 3 8"></polyline><rect x="1" y="3" width="22" height="5"></rect><line x1="10" y1="12" x2="14" y2="12"></line></svg>',
    review: '<svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>',
    edit: '<svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path></svg>',
    layers: '<svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><polygon points="12 2 2 7 12 12 22 7 12 2"></polygon><polyline points="2 17 12 22 22 17"></polyline><polyline points="2 12 12 17 22 12"></polyline></svg>',
    opencode: '<svg viewBox="0 0 24 24" width="20" height="20" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><polyline points="16 18 22 12 16 6"></polyline><polyline points="8 6 2 12 8 18"></polyline><line x1="14" y1="4" x2="10" y2="20"></line></svg>',
    openrouter: '<svg viewBox="0 0 24 24" width="20" height="20" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><polygon points="16.24 7.76 14.12 14.12 7.76 16.24 9.88 9.88 16.24 7.76"></polygon></svg>',
    deepseek: '<svg viewBox="0 0 24 24" width="20" height="20" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line><path d="M11 8v6M8 11h6"></path></svg>',
    claude: '<svg viewBox="0 0 24 24" width="20" height="20" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2v20M2 12h20M4.93 4.93l14.14 14.14M4.93 19.07l14.14-14.14"></path></svg>',
    chatgpt: '<svg viewBox="0 0 24 24" width="20" height="20" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><path d="M12 8v8M8 12h8"></path></svg>',
    mistral: '<svg viewBox="0 0 24 24" width="20" height="20" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7"></rect><rect x="14" y="3" width="7" height="7"></rect><rect x="14" y="14" width="7" height="7"></rect><rect x="3" y="14" width="7" height="7"></rect></svg>',
    google: '<svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor"><path d="M12.48 10.92v3.28h7.84c-.24 1.84-.853 3.187-1.787 4.133-1.147 1.147-2.933 2.4-6.053 2.4-4.827 0-8.6-3.893-8.6-8.72s3.773-8.72 8.6-8.72c2.6 0 4.507 1.027 5.907 2.347l2.307-2.307C18.747 1.44 16.133 0 12.48 0 5.867 0 .307 5.387.307 12s5.56 12 12.173 12c3.573 0 6.267-1.173 8.373-3.36 2.16-2.16 2.84-5.213 2.84-7.667 0-.76-.053-1.467-.173-2.053H12.48z"/></svg>',
    github: '<svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor"><path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0024 12c0-6.63-5.37-12-12-12z"/></svg>',
    check: '<svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2.5" fill="none"><polyline points="20 6 9 17 4 12"></polyline></svg>',
    media: '<svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none"><rect x="3" y="3" width="18" height="18" rx="2"></rect><circle cx="8.5" cy="8.5" r="1.5"></circle><polyline points="21 15 16 10 5 21"></polyline></svg>',
    folder: '<svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path></svg>',
    mention: '<svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none"><circle cx="12" cy="12" r="4"></circle><path d="M16 8v5a3 3 0 0 0 6 0v-1a10 10 0 1 0-3.92 7.94"></path></svg>',
    bolt: '<svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"></polygon></svg>',
    globe: '<svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none"><circle cx="12" cy="12" r="10"></circle><line x1="2" y1="12" x2="22" y2="12"></line><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"></path></svg>'
  };

  // ---------------------------------------------------------------------------
  // 4. Build Core Mobile Shell
  // ---------------------------------------------------------------------------
  function buildMobileShell() {
    if (document.getElementById("hibou-mobile-shell")) return;

    var shell = document.createElement("div");
    shell.id = "hibou-mobile-shell";
    shell.className = "hibou-mobile-root";

    shell.innerHTML = [
      // 1. Animated Electric Blue Top Glow & Ambient Light
      '<div id="hibou-top-ambient-glow" class="hibou-ambient-glow"></div>',

      // 2. Dynamic Home Screen Greeting
      '<div id="hibou-greeting-hero" class="hibou-greeting-hero">',
      '  <div class="hibou-greeting-badge">',
      '    <span class="hibou-sparkle-dot"></span>',
      '    <span id="hibou-greeting-text">' + escapeHtml(getDynamicGreeting()) + '</span>',
      '  </div>',
      '</div>',

      // 3. Top Header Bar
      '<header id="hibou-top-header" class="hibou-top-header">',
      '  <!-- 1. Menu Button (Top Left) -->',
      '  <button type="button" id="hibou-btn-menu" class="hibou-header-btn hibou-btn-menu" aria-label="Open Navigation Menu">',
      ICONS.menu,
      '  </button>',
      '',
      '  <!-- 3. Model Selector & Quota Pill (Top Center-Left) -->',
      '  <div id="hibou-model-pill" class="hibou-model-pill" role="button" tabindex="0" title="Switch Model & View Token Quota">',
      '    <span class="hibou-model-icon">' + ICONS.modelSparkle + '</span>',
      '    <span id="hibou-model-title" class="hibou-model-title">' + escapeHtml(state.activeModelName) + '</span>',
      '    <span class="hibou-pill-divider"></span>',
      '    <span id="hibou-model-quota-badge" class="hibou-model-quota-badge">' + escapeHtml(state.modelQuotaText) + '</span>',
      '    <span class="hibou-chevron-down">' + ICONS.chevronDown + '</span>',
      '  </div>',
      '',
      '  <!-- 5. Multi-Purpose Action Button (Top Center-Right) -->',
      '  <button type="button" id="hibou-btn-multipurpose" class="hibou-header-btn hibou-btn-multipurpose" aria-label="Projects / Conversation Actions">',
      '    <span id="hibou-multipurpose-icon">' + ICONS.projects + '</span>',
      '    <span id="hibou-multipurpose-label" class="hibou-multipurpose-label">' + i18n("Projects", "Проекты") + '</span>',
      '    <span class="hibou-chevron-down">' + ICONS.chevronDown + '</span>',
      '  </button>',
      '',
      '  <!-- 4. Profile Button (Top Right) -->',
      '  <button type="button" id="hibou-btn-profile" class="hibou-profile-avatar-btn" aria-label="Accounts and Providers Control Center">',
      '    <div class="hibou-avatar-circle">',
      '      <span id="hibou-avatar-initial">' + escapeHtml(state.userInitial) + '</span>',
      '    </div>',
      '  </button>',
      '</header>',

      // 4. Slide Drawer (Menu from Left)
      '<div id="hibou-drawer-backdrop" class="hibou-drawer-backdrop"></div>',
      '<aside id="hibou-slide-drawer" class="hibou-slide-drawer">',
      '  <div class="hibou-drawer-header">',
      '    <div class="hibou-drawer-brand">',
      '      <div class="hibou-drawer-logo">' + ICONS.hibouLogo + '</div>',
      '      <div>',
      '        <div class="hibou-drawer-title">Hibou</div>',
      '        <div class="hibou-drawer-version">1.0.0-beta.1 • Mobile</div>',
      '      </div>',
      '    </div>',
      '    <button type="button" id="hibou-drawer-close" class="hibou-drawer-close-btn" aria-label="Close Menu">',
      ICONS.close,
      '    </button>',
      '  </div>',
      '  <div class="hibou-drawer-body">',
      '    <button type="button" id="hibou-drawer-new-chat" class="hibou-drawer-primary-action">',
      '      ' + ICONS.plus + ' <span>' + i18n("New Conversation", "Новый диалог") + '</span>',
      '    </button>',
      '    <div class="hibou-drawer-section-title">' + i18n("Navigation & Workspace", "Рабочее пространство") + '</div>',
      '    <div class="hibou-drawer-nav-list">',
      '      <div class="hibou-drawer-nav-item" data-action="projects">',
      '        ' + ICONS.projects + ' <span>' + i18n("Projects & Folders", "Проекты и папки") + '</span>',
      '      </div>',
      '      <div class="hibou-drawer-nav-item" data-action="tools">',
      '        ' + ICONS.terminal + ' <span>' + i18n("Developer Tools & Shell", "Инструменты и консоль") + '</span>',
      '      </div>',
      '      <div class="hibou-drawer-nav-item" data-action="mcp">',
      '        ' + ICONS.layers + ' <span>' + i18n("MCP & Plugins", "Плагины и MCP-серверы") + '</span>',
      '      </div>',
      '      <div class="hibou-drawer-nav-item" data-action="providers">',
      '        ' + ICONS.modelSparkle + ' <span>' + i18n("AI Providers & Keys", "Провайдеры моделей и ключи") + '</span>',
      '      </div>',
      '      <div class="hibou-drawer-nav-item" data-action="settings">',
      '        <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none"><circle cx="12" cy="12" r="3"></circle><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"></path></svg> <span>' + i18n("Preferences & UI", "Настройки") + '</span>',
      '      </div>',
      '    </div>',
      '    <div class="hibou-drawer-section-title">' + i18n("System & Help", "Система и помощь") + '</div>',
      '    <div class="hibou-drawer-nav-list">',
      '      <div class="hibou-drawer-nav-item" data-action="updates">',
      '        <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none"><path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67"/></svg> <span>' + i18n("Check for Updates", "Проверить обновления") + '</span>',
      '      </div>',
      '      <div class="hibou-drawer-nav-item" data-action="clean">',
      '        <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg> <span>' + i18n("Clear Cache", "Очистить кэш") + '</span>',
      '      </div>',
      '    </div>',
      '  </div>',
      '</aside>',

      // 5. Multi-Purpose Dropdown (Projects / Management)
      '<div id="hibou-multipurpose-menu" class="hibou-dropdown-panel hibou-hidden">',
      '  <div id="hibou-multipurpose-content"></div>',
      '</div>',

      // 6. Two-Tier Bottom Chat Dock (Upper: Stage/Branch; Lower: Rounded Dock)
      '<div id="hibou-mobile-bottom-dock" class="hibou-mobile-bottom-dock">',
      '  <!-- Upper Tier: Floating Stage & Branch Selector -->',
      '  <div id="hibou-dock-upper-tier" class="hibou-dock-upper-tier">',
      '    <div class="hibou-stage-selector">',
      '      <button type="button" id="hibou-stage-local" class="hibou-stage-btn active" data-stage="local">',
      '        ' + ICONS.projects + ' <span>' + i18n("Local", "Локально") + '</span>',
      '      </button>',
      '      <button type="button" id="hibou-stage-worktree" class="hibou-stage-btn" data-stage="worktree">',
      '        ' + ICONS.worktree + ' <span>' + i18n("Worktree", "Ворктри") + '</span>',
      '      </button>',
      '    </div>',
      '    <div id="hibou-branch-selector-pill" class="hibou-branch-pill" role="button" tabindex="0">',
      '      <span class="hibou-branch-icon">' + ICONS.branch + '</span>',
      '      <span id="hibou-branch-name" class="hibou-branch-name">' + escapeHtml(state.currentBranch) + '</span>',
      '      <span class="hibou-chevron-down">' + ICONS.chevronDown + '</span>',
      '    </div>',
      '  </div>',
      '</div>',

      // 7. Full-Screen Control Center (Profile, Accounts & Providers)
      '<div id="hibou-control-center-modal" class="hibou-control-center-sheet hibou-hidden">',
      '  <div class="hibou-cc-header">',
      '    <div class="hibou-cc-title-block">',
      '      <div class="hibou-cc-badge">CONTROL CENTER</div>',
      '      <div class="hibou-cc-title">' + i18n("Accounts & AI Providers", "Аккаунты и AI-провайдеры") + '</div>',
      '    </div>',
      '    <button type="button" id="hibou-cc-close" class="hibou-cc-close-btn">' + ICONS.close + '</button>',
      '  </div>',
      '  <div class="hibou-cc-tabs">',
      '    <button type="button" class="hibou-cc-tab-btn active" data-cctab="accounts">' + i18n("Accounts", "Аккаунты") + '</button>',
      '    <button type="button" class="hibou-cc-tab-btn" data-cctab="providers">' + i18n("AI Providers", "AI-провайдеры") + '</button>',
      '    <button type="button" class="hibou-cc-tab-btn" data-cctab="quotas">' + i18n("Quotas & Usage", "Квоты и лимиты") + '</button>',
      '  </div>',
      '  <div class="hibou-cc-body">',
      '    <!-- Tab 1: Connected Accounts -->',
      '    <div id="hibou-cctab-accounts" class="hibou-cctab-content active">',
      '      <div class="hibou-account-card">',
      '        <div class="hibou-acc-header">',
      '          <div class="hibou-acc-icon google">G</div>',
      '          <div>',
      '            <div class="hibou-acc-name">Google Account</div>',
      '            <div class="hibou-acc-email" id="hibou-google-email">' + escapeHtml(state.userEmail) + '</div>',
      '          </div>',
      '          <span class="hibou-status-pill online">' + i18n("Connected", "Подключено") + '</span>',
      '        </div>',
      '        <div class="hibou-acc-actions">',
      '          <button type="button" class="hibou-btn-secondary" id="hibou-btn-google-reauth">' + i18n("Switch Account", "Сменить аккаунт") + '</button>',
      '        </div>',
      '      </div>',
      '      <div class="hibou-account-card">',
      '        <div class="hibou-acc-header">',
      '          <div class="hibou-acc-icon github">GH</div>',
      '          <div>',
      '            <div class="hibou-acc-name">GitHub Token</div>',
      '            <div class="hibou-acc-desc">' + i18n("Sync repos, push commits & run workflows", "Синхронизация репозиториев и коммиты") + '</div>',
      '          </div>',
      '          <span class="hibou-status-pill online" id="hibou-github-status">' + i18n("Active", "Активен") + '</span>',
      '        </div>',
      '        <div class="hibou-acc-token-form">',
      '          <input type="password" id="hibou-gh-token-input" class="hibou-input-text" placeholder="ghp_... (GitHub Personal Access Token)">',
      '          <button type="button" id="hibou-gh-token-save" class="hibou-btn-primary">' + i18n("Update Token", "Обновить") + '</button>',
      '        </div>',
      '      </div>',
      '    </div>',
      '',
      '    <!-- Tab 2: AI Model Providers -->',
      '    <div id="hibou-cctab-providers" class="hibou-cctab-content">',
      '      <div class="hibou-providers-intro">',
      '        ' + i18n("Configure custom API keys and endpoints for OpenCode, OpenRouter, DeepSeek, Claude, ChatGPT, Mistral, or local LLMs.", "Настройте API-ключи и эндпоинты для OpenCode, OpenRouter, DeepSeek, Claude, ChatGPT, Mistral или локальных LLM.") + '',
      '      </div>',
      '      <div class="hibou-m3-provider-list">',
      '        <!-- 1. OpenCode -->',
      '        <div class="hibou-m3-provider-card inactive" data-pv="opencode">',
      '          <div class="hibou-pv-top">',
      '            <div class="hibou-pv-icon opencode">' + ICONS.opencode + '</div>',
      '            <div class="hibou-pv-meta">',
      '              <div class="hibou-pv-name">OpenCode <span class="hibou-m3-badge">CODING</span></div>',
      '              <div class="hibou-pv-desc">OpenCode AI Coding Models & Local Servers</div>',
      '            </div>',
      '            <label class="hibou-m3-switch" title="' + i18n("Activate OpenCode", "Активировать OpenCode") + '">',
      '              <input type="checkbox" id="hibou-pv-toggle-opencode">',
      '              <span class="hibou-m3-switch-track"><span class="hibou-m3-switch-thumb"></span></span>',
      '            </label>',
      '          </div>',
      '          <div class="hibou-pv-fields">',
      '            <div class="hibou-m3-plan-selector" id="hibou-opencode-plan-selector">',
      '              <button type="button" class="hibou-m3-plan-chip active" data-opencode-plan="free">',
      '                Free Models <span class="hibou-plan-tag free">NO KEY</span>',
      '              </button>',
      '              <button type="button" class="hibou-m3-plan-chip" data-opencode-plan="go">',
      '                Go ($10/mo) <span class="hibou-plan-tag sub">POOL</span>',
      '              </button>',
      '              <button type="button" class="hibou-m3-plan-chip" data-opencode-plan="go_plus">',
      '                Go Plus ($40/mo) <span class="hibou-plan-tag sub">PRIORITY</span>',
      '              </button>',
      '              <button type="button" class="hibou-m3-plan-chip" data-opencode-plan="zen">',
      '                Zen <span class="hibou-plan-tag sub">PAY-GO</span>',
      '              </button>',
      '              <button type="button" class="hibou-m3-plan-chip" data-opencode-plan="local">',
      '                Local Server <span class="hibou-plan-tag local">PORT 4096</span>',
      '              </button>',
      '            </div>',
      '            <div class="hibou-plan-banner free" id="hibou-opencode-banner">',
      '              <div class="hibou-plan-banner-title">' + i18n("Community Free Tier: 12 models free, no API key required", "Бесплатный уровень: 12 моделей бесплатно, API-ключ не требуется") + '</div>',
      '              <div>' + i18n("Nemotron 3.5 Super VL, Nemotron 3.5 Lightning, MiniMax M3.1 Flash, DeepSeek V4.1 Flash, and more.", "Nemotron 3.5 Super VL, Nemotron 3.5 Lightning, MiniMax M3.1 Flash, DeepSeek V4.1 Flash и другие.") + '</div>',
      '            </div>',
      '            <div class="hibou-pv-field-row">',
      '              <label>Endpoint</label>',
      '              <input type="text" id="hibou-pv-url-opencode" class="hibou-input-text" value="https://opencode.ai/zen/v1" placeholder="https://opencode.ai/zen/v1 or http://localhost:4096/v1">',
      '            </div>',
      '            <div class="hibou-pv-field-row" id="hibou-opencode-key-row" style="display: none;">',
      '              <label>API Key</label>',
      '              <input type="password" id="hibou-pv-key-opencode" class="hibou-input-text" placeholder="opencode_... (API key)">',
      '            </div>',
      '            <div class="hibou-pv-quota-info">',
      '              <span id="hibou-opencode-quota-left">' + i18n("Quota: 12 Free Coding Models", "Квота: 12 бесплатных моделей") + '</span>',
      '              <span id="hibou-opencode-quota-right">' + i18n("Access: Zero-cost / Public", "Доступ: Без оплаты / Публичный") + '</span>',
      '            </div>',
      '            <div class="hibou-pv-card-actions">',
      '              <button type="button" class="hibou-btn-tonal" data-test-pv="opencode">' + i18n("Test & Connect", "Проверить и подключить") + '</button>',
      '              <span class="hibou-pv-card-status" id="hibou-pv-status-opencode"></span>',
      '            </div>',
      '          </div>',
      '        </div>',
      '',
      '        <!-- 2. OpenRouter -->',
      '        <div class="hibou-m3-provider-card" data-pv="openrouter">',
      '          <div class="hibou-pv-top">',
      '            <div class="hibou-pv-icon openrouter">' + ICONS.openrouter + '</div>',
      '            <div class="hibou-pv-meta">',
      '              <div class="hibou-pv-name">OpenRouter <span class="hibou-m3-badge">GATEWAY</span></div>',
      '              <div class="hibou-pv-desc">Universal Multi-Provider AI Routing</div>',
      '            </div>',
      '            <label class="hibou-m3-switch">',
      '              <input type="checkbox" id="hibou-pv-toggle-openrouter">',
      '              <span class="hibou-m3-switch-track"><span class="hibou-m3-switch-thumb"></span></span>',
      '            </label>',
      '          </div>',
      '          <div class="hibou-pv-fields">',
      '            <div class="hibou-pv-field-row">',
      '              <label>Endpoint</label>',
      '              <input type="text" id="hibou-pv-url-openrouter" class="hibou-input-text" value="https://openrouter.ai/api/v1" placeholder="https://openrouter.ai/api/v1">',
      '            </div>',
      '            <div class="hibou-pv-field-row">',
      '              <label>API Key</label>',
      '              <input type="password" id="hibou-pv-key-openrouter" class="hibou-input-text" placeholder="sk-or-v1-...">',
      '            </div>',
      '            <div class="hibou-pv-quota-info">',
      '              <span>' + i18n("Credit balance: Dynamic", "Баланс: Динамический") + '</span>',
      '              <span>' + i18n("Refresh: Instant reload", "Сброс: При пополнении") + '</span>',
      '            </div>',
      '            <div class="hibou-pv-card-actions">',
      '              <button type="button" class="hibou-btn-tonal" data-test-pv="openrouter">' + i18n("Test & Connect", "Проверить и подключить") + '</button>',
      '              <span class="hibou-pv-card-status" id="hibou-pv-status-openrouter"></span>',
      '            </div>',
      '          </div>',
      '        </div>',
      '',
      '        <!-- 3. DeepSeek -->',
      '        <div class="hibou-m3-provider-card" data-pv="deepseek">',
      '          <div class="hibou-pv-top">',
      '            <div class="hibou-pv-icon deepseek">' + ICONS.deepseek + '</div>',
      '            <div class="hibou-pv-meta">',
      '              <div class="hibou-pv-name">DeepSeek <span class="hibou-m3-badge">REASONING</span></div>',
      '              <div class="hibou-pv-desc">DeepSeek V3 & DeepSeek R1 Models</div>',
      '            </div>',
      '            <label class="hibou-m3-switch">',
      '              <input type="checkbox" id="hibou-pv-toggle-deepseek">',
      '              <span class="hibou-m3-switch-track"><span class="hibou-m3-switch-thumb"></span></span>',
      '            </label>',
      '          </div>',
      '          <div class="hibou-pv-fields">',
      '            <div class="hibou-pv-field-row">',
      '              <label>Endpoint</label>',
      '              <input type="text" id="hibou-pv-url-deepseek" class="hibou-input-text" value="https://api.deepseek.com/v1" placeholder="https://api.deepseek.com/v1">',
      '            </div>',
      '            <div class="hibou-pv-field-row">',
      '              <label>API Key</label>',
      '              <input type="password" id="hibou-pv-key-deepseek" class="hibou-input-text" placeholder="sk-...">',
      '            </div>',
      '            <div class="hibou-pv-quota-info">',
      '              <span>' + i18n("Context: 64k / 128k tokens", "Контекст: 64k / 128k токенов") + '</span>',
      '              <span>' + i18n("Refresh: Daily / Pay-as-you-go", "Сброс: По потреблению") + '</span>',
      '            </div>',
      '            <div class="hibou-pv-card-actions">',
      '              <button type="button" class="hibou-btn-tonal" data-test-pv="deepseek">' + i18n("Test & Connect", "Проверить и подключить") + '</button>',
      '              <span class="hibou-pv-card-status" id="hibou-pv-status-deepseek"></span>',
      '            </div>',
      '          </div>',
      '        </div>',
      '',
      '        <!-- 4. Claude (Anthropic) -->',
      '        <div class="hibou-m3-provider-card" data-pv="anthropic">',
      '          <div class="hibou-pv-top">',
      '            <div class="hibou-pv-icon claude">' + ICONS.claude + '</div>',
      '            <div class="hibou-pv-meta">',
      '              <div class="hibou-pv-name">Anthropic Claude <span class="hibou-m3-badge">MESSAGES</span></div>',
      '              <div class="hibou-pv-desc">Claude 3.5 Sonnet & Claude 3.5 Haiku</div>',
      '            </div>',
      '            <label class="hibou-m3-switch">',
      '              <input type="checkbox" id="hibou-pv-toggle-anthropic">',
      '              <span class="hibou-m3-switch-track"><span class="hibou-m3-switch-thumb"></span></span>',
      '            </label>',
      '          </div>',
      '          <div class="hibou-pv-fields">',
      '            <div class="hibou-pv-field-row">',
      '              <label>Endpoint</label>',
      '              <input type="text" id="hibou-pv-url-anthropic" class="hibou-input-text" value="https://api.anthropic.com" placeholder="https://api.anthropic.com">',
      '            </div>',
      '            <div class="hibou-pv-field-row">',
      '              <label>API Key</label>',
      '              <input type="password" id="hibou-pv-key-anthropic" class="hibou-input-text" placeholder="sk-ant-...">',
      '            </div>',
      '            <div class="hibou-pv-quota-info">',
      '              <span>' + i18n("Context: 200,000 tokens", "Контекст: 200,000 токенов") + '</span>',
      '              <span>' + i18n("Refresh: Tiered RPM / TPM", "Сброс: По лимитам RPM/TPM") + '</span>',
      '            </div>',
      '            <div class="hibou-pv-card-actions">',
      '              <button type="button" class="hibou-btn-tonal" data-test-pv="anthropic">' + i18n("Test & Connect", "Проверить и подключить") + '</button>',
      '              <span class="hibou-pv-card-status" id="hibou-pv-status-anthropic"></span>',
      '            </div>',
      '          </div>',
      '        </div>',
      '',
      '        <!-- 5. ChatGPT (OpenAI) -->',
      '        <div class="hibou-m3-provider-card" data-pv="openai">',
      '          <div class="hibou-pv-top">',
      '            <div class="hibou-pv-icon chatgpt">' + ICONS.chatgpt + '</div>',
      '            <div class="hibou-pv-meta">',
      '              <div class="hibou-pv-name">OpenAI ChatGPT <span class="hibou-m3-badge">GPT-4o</span></div>',
      '              <div class="hibou-pv-desc">GPT-4o, o1-preview & OpenAI API</div>',
      '            </div>',
      '            <label class="hibou-m3-switch">',
      '              <input type="checkbox" id="hibou-pv-toggle-openai">',
      '              <span class="hibou-m3-switch-track"><span class="hibou-m3-switch-thumb"></span></span>',
      '            </label>',
      '          </div>',
      '          <div class="hibou-pv-fields">',
      '            <div class="hibou-pv-field-row">',
      '              <label>Endpoint</label>',
      '              <input type="text" id="hibou-pv-url-openai" class="hibou-input-text" value="https://api.openai.com/v1" placeholder="https://api.openai.com/v1">',
      '            </div>',
      '            <div class="hibou-pv-field-row">',
      '              <label>API Key</label>',
      '              <input type="password" id="hibou-pv-key-openai" class="hibou-input-text" placeholder="sk-proj-...">',
      '            </div>',
      '            <div class="hibou-pv-quota-info">',
      '              <span>' + i18n("Context: 128,000 tokens", "Контекст: 128,000 токенов") + '</span>',
      '              <span>' + i18n("Refresh: Monthly quota", "Сброс: Ежемесячно") + '</span>',
      '            </div>',
      '            <div class="hibou-pv-card-actions">',
      '              <button type="button" class="hibou-btn-tonal" data-test-pv="openai">' + i18n("Test & Connect", "Проверить и подключить") + '</button>',
      '              <span class="hibou-pv-card-status" id="hibou-pv-status-openai"></span>',
      '            </div>',
      '          </div>',
      '        </div>',
      '',
      '        <!-- 6. Mistral -->',
      '        <div class="hibou-m3-provider-card" data-pv="mistral">',
      '          <div class="hibou-pv-top">',
      '            <div class="hibou-pv-icon mistral">' + ICONS.mistral + '</div>',
      '            <div class="hibou-pv-meta">',
      '              <div class="hibou-pv-name">Mistral AI <span class="hibou-m3-badge">CODESTRAL</span></div>',
      '              <div class="hibou-pv-desc">Codestral, Mistral Large & Pixtral</div>',
      '            </div>',
      '            <label class="hibou-m3-switch">',
      '              <input type="checkbox" id="hibou-pv-toggle-mistral">',
      '              <span class="hibou-m3-switch-track"><span class="hibou-m3-switch-thumb"></span></span>',
      '            </label>',
      '          </div>',
      '          <div class="hibou-pv-fields">',
      '            <div class="hibou-pv-field-row">',
      '              <label>Endpoint</label>',
      '              <input type="text" id="hibou-pv-url-mistral" class="hibou-input-text" value="https://api.mistral.ai/v1" placeholder="https://api.mistral.ai/v1">',
      '            </div>',
      '            <div class="hibou-pv-field-row">',
      '              <label>API Key</label>',
      '              <input type="password" id="hibou-pv-key-mistral" class="hibou-input-text" placeholder="mis_...">',
      '            </div>',
      '            <div class="hibou-pv-quota-info">',
      '              <span>' + i18n("Context: 32k / 128k tokens", "Контекст: 32k / 128k токенов") + '</span>',
      '              <span>' + i18n("Refresh: Monthly quota", "Сброс: Ежемесячно") + '</span>',
      '            </div>',
      '            <div class="hibou-pv-card-actions">',
      '              <button type="button" class="hibou-btn-tonal" data-test-pv="mistral">' + i18n("Test & Connect", "Проверить и подключить") + '</button>',
      '              <span class="hibou-pv-card-status" id="hibou-pv-status-mistral"></span>',
      '            </div>',
      '          </div>',
      '        </div>',
      '      </div>',
      '',
      '      <div class="hibou-pv-advanced-title">' + i18n("Fine-Grained Catalog & Custom Connections", "Детальный каталог и ручные подключения") + '</div>',
      '      <div id="hibou-providers-mount-container"></div>',
      '    </div>',
      '',
      '    <!-- Tab 3: Quotas & Usage -->',
      '    <div id="hibou-cctab-quotas" class="hibou-cctab-content">',
      '      <div class="hibou-quota-overview">',
      '        <div class="hibou-quota-stat-card primary">',
      '          <div class="hibou-quota-top">',
      '            <div class="hibou-quota-title">' + i18n("Active Autonomous Model Pool", "Доступные токены активной модели") + '</div>',
      '            <span class="hibou-m3-chip">' + escapeHtml(state.activeModelName) + '</span>',
      '          </div>',
      '          <div class="hibou-quota-val" id="hibou-quota-tokens-val">1,000,000 / 1,000,000</div>',
      '          <div class="hibou-quota-bar"><div class="hibou-quota-bar-fill" style="width: 100%"></div></div>',
      '          <div class="hibou-quota-reset">' + i18n("Resets every 24 hours at 00:00 UTC", "Сброс каждые 24 часа в 00:00 UTC") + '</div>',
      '        </div>',
      '        <div class="hibou-quota-stat-card">',
      '          <div class="hibou-quota-title">' + i18n("Autonomous Rate Limits (RPM / TPM)", "Лимиты запросов (RPM / TPM)") + '</div>',
      '          <div class="hibou-quota-val">60 RPM • 10,000,000 TPM</div>',
      '          <div class="hibou-quota-desc">' + i18n("Standard Tier Mobile Autonomous Coding Budget", "Стандартная квота мобильного автономного кодинга") + '</div>',
      '        </div>',
      '        <div class="hibou-quota-stat-card">',
      '          <div class="hibou-quota-title">' + i18n("Connected Providers Live Status", "Статус подключенных провайдеров") + '</div>',
      '          <div class="hibou-quota-provider-rows">',
      '            <div class="hibou-quota-row"><span>Google Cloud AI:</span> <strong>' + i18n("Active (Unlimited Core)", "Активен (Ядро)") + '</strong></div>',
      '            <div class="hibou-quota-row"><span>OpenCode Service:</span> <strong id="hibou-quota-st-opencode">' + i18n("Ready", "Готов") + '</strong></div>',
      '            <div class="hibou-quota-row"><span>OpenRouter / DeepSeek:</span> <strong id="hibou-quota-st-openrouter">' + i18n("Ready", "Готов") + '</strong></div>',
      '            <div class="hibou-quota-row"><span>Anthropic / OpenAI:</span> <strong id="hibou-quota-st-anthropic">' + i18n("Ready", "Готов") + '</strong></div>',
      '          </div>',
      '        </div>',
      '      </div>',
      '    </div>',
      '  </div>',
      '</div>'
    ].join("\n");

    document.body.appendChild(shell);
    setupEventListeners();
    setupSwipeGesture();
    observeAppState();
  }

  // ---------------------------------------------------------------------------
  // 5. Swipe Gestures (Left Edge Drag to Open Drawer)
  // ---------------------------------------------------------------------------
  function setupSwipeGesture() {
    var startX = 0;
    var startY = 0;
    var tracking = false;
    var drawer = document.getElementById("hibou-slide-drawer");
    var backdrop = document.getElementById("hibou-drawer-backdrop");

    window.addEventListener("touchstart", function (e) {
      if (!e.touches || e.touches.length !== 1) return;
      var touch = e.touches[0];
      // Only track if touch starts within 28px of the left edge
      if (touch.clientX < 28 && !state.drawerOpen) {
        startX = touch.clientX;
        startY = touch.clientY;
        tracking = true;
      }
    }, { passive: true });

    window.addEventListener("touchmove", function (e) {
      if (!tracking || !e.touches || e.touches.length !== 1) return;
      var touch = e.touches[0];
      var dx = touch.clientX - startX;
      var dy = touch.clientY - startY;

      // Cancel if primarily vertical scroll
      if (Math.abs(dy) > Math.abs(dx) && Math.abs(dy) > 15) {
        tracking = false;
        return;
      }

      if (dx > 0 && dx < 290) {
        drawer.style.transition = "none";
        drawer.style.transform = "translateX(" + (dx - 290) + "px)";
        backdrop.style.display = "block";
        backdrop.style.opacity = String(Math.min(0.65, (dx / 290) * 0.65));
      }
    }, { passive: true });

    window.addEventListener("touchend", function (e) {
      if (!tracking) return;
      tracking = false;
      drawer.style.transition = "";
      backdrop.style.transition = "";
      var touch = (e.changedTouches && e.changedTouches[0]) || null;
      if (touch && (touch.clientX - startX > 75)) {
        openDrawer();
      } else {
        closeDrawer();
      }
    }, { passive: true });
  }

  // ---------------------------------------------------------------------------
  // 6. Action Handlers & Event Listeners
  // ---------------------------------------------------------------------------
  function openDrawer() {
    state.drawerOpen = true;
    var drawer = document.getElementById("hibou-slide-drawer");
    var backdrop = document.getElementById("hibou-drawer-backdrop");
    if (drawer) drawer.classList.add("open");
    if (backdrop) backdrop.classList.add("visible");
  }

  function closeDrawer() {
    state.drawerOpen = false;
    var drawer = document.getElementById("hibou-slide-drawer");
    var backdrop = document.getElementById("hibou-drawer-backdrop");
    if (drawer) {
      drawer.classList.remove("open");
      drawer.style.transform = "";
    }
    if (backdrop) {
      backdrop.classList.remove("visible");
      backdrop.style.opacity = "";
      setTimeout(function () {
        if (!state.drawerOpen && backdrop) backdrop.style.display = "";
      }, 250);
    }
  }

  function selectOpenCodePlan(planId) {
    var chips = document.querySelectorAll("#hibou-opencode-plan-selector [data-opencode-plan]");
    chips.forEach(function (c) {
      c.classList.toggle("active", c.getAttribute("data-opencode-plan") === planId);
    });

    var banner = document.getElementById("hibou-opencode-banner");
    var keyRow = document.getElementById("hibou-opencode-key-row");
    var urlInput = document.getElementById("hibou-pv-url-opencode");
    var quotaLeft = document.getElementById("hibou-opencode-quota-left");
    var quotaRight = document.getElementById("hibou-opencode-quota-right");

    if (banner) {
      banner.className = "hibou-plan-banner " + (planId === "free" ? "free" : (planId === "local" ? "local" : "paid"));
      var title = "";
      var desc = "";
      if (planId === "free") {
        title = i18n("Community Free Tier: 12 models free, no API key required", "Бесплатный уровень: 12 моделей бесплатно, API-ключ не требуется");
        desc = i18n("Nemotron 3.5 Super VL, Nemotron 3.5 Lightning, MiniMax M3.1 Flash, DeepSeek V4.1 Flash, and more.", "Nemotron 3.5 Super VL, Nemotron 3.5 Lightning, MiniMax M3.1 Flash, DeepSeek V4.1 Flash и другие.");
      } else if (planId === "go") {
        title = i18n("OpenCode Go: High-throughput shared coding pool ($10/mo)", "OpenCode Go: Высокопроизводительный пул кодинга ($10/мес)");
        desc = i18n("Requires OpenCode Go subscription key. Access to Claude 3.5 Sonnet, GPT-4o, and DeepSeek V3/R1.", "Требуется ключ подписки OpenCode Go. Доступ к Claude 3.5 Sonnet, GPT-4o и DeepSeek V3/R1.");
      } else if (planId === "go_plus") {
        title = i18n("OpenCode Go Plus: Priority power-user coding pool ($40/mo)", "OpenCode Go Plus: Приоритетный пул для активных разработчиков ($40/мес)");
        desc = i18n("Requires OpenCode Go Plus subscription key. Dedicated zero-latency priority routing.", "Требуется ключ подписки OpenCode Go Plus. Выделенная приоритетная маршрутизация без задержек.");
      } else if (planId === "zen") {
        title = i18n("OpenCode Zen: Pay-as-you-go frontier model routing", "OpenCode Zen: Оплата по факту использования передовых моделей");
        desc = i18n("Requires OpenCode Zen API key. Pay per token for frontier coding and reasoning models.", "Требуется API-ключ OpenCode Zen. Оплата за токены для передовых моделей кодинга и рассуждений.");
      } else if (planId === "local") {
        title = i18n("Local OpenCode Server: Connect to localhost:4096 (Offline)", "Локальный сервер OpenCode: Подключение к localhost:4096 (Офлайн)");
        desc = i18n("No external API key needed. Runs locally on device or LAN server at port 4096.", "Внешний API-ключ не требуется. Работает локально на устройстве или в локальной сети на порту 4096.");
      }
      banner.innerHTML = '<div class="hibou-plan-banner-title">' + title + '</div><div>' + desc + '</div>';
    }

    if (keyRow) {
      keyRow.style.display = (planId === "free" || planId === "local") ? "none" : "";
    }

    if (urlInput) {
      if (planId === "local") {
        urlInput.value = "http://localhost:4096/v1";
      } else if (!urlInput.value || urlInput.value.indexOf("localhost") >= 0 || urlInput.value.indexOf("api.opencode.ai") >= 0) {
        urlInput.value = "https://opencode.ai/zen/v1";
      }
    }

    if (quotaLeft && quotaRight) {
      if (planId === "free") {
        quotaLeft.textContent = i18n("Quota: 12 Free Coding Models", "Квота: 12 бесплатных моделей");
        quotaRight.textContent = i18n("Access: Zero-cost / Public", "Доступ: Без оплаты / Публичный");
      } else if (planId === "go") {
        quotaLeft.textContent = i18n("Pool: High-throughput pool", "Пул: Высокая пропускная способность");
        quotaRight.textContent = i18n("Access: $10/mo subscription", "Доступ: Подписка $10/мес");
      } else if (planId === "go_plus") {
        quotaLeft.textContent = i18n("Pool: Priority power-user pool", "Пул: Приоритетный пул");
        quotaRight.textContent = i18n("Access: $40/mo subscription", "Доступ: Подписка $40/мес");
      } else if (planId === "zen") {
        quotaLeft.textContent = i18n("Usage: Dynamic metered balance", "Расход: Динамический баланс");
        quotaRight.textContent = i18n("Access: Pay-as-you-go billing", "Доступ: Оплата по факту");
      } else if (planId === "local") {
        quotaLeft.textContent = i18n("Host: 127.0.0.1:4096", "Хост: 127.0.0.1:4096");
        quotaRight.textContent = i18n("Access: Local daemon / Offline", "Доступ: Локальный демон / Офлайн");
      }
    }

    try {
      var raw = localStorage.getItem("agyProviders") || '{"connections":[]}';
      var parsed = JSON.parse(raw);
      parsed.connections = parsed.connections || [];
      var oc = parsed.connections.find(function (c) { return c.id === "opencode"; });
      if (oc) {
        oc.plan = planId;
        if (planId === "free") oc.apiKey = "public";
        else if (planId === "local") oc.apiKey = oc.apiKey || "local";
        if (urlInput) oc.baseUrl = urlInput.value;
        var updated = JSON.stringify(parsed);
        localStorage.setItem("agyProviders", updated);
        if (window.AgyProviders && typeof window.AgyProviders.save === "function") {
          window.AgyProviders.save(updated);
        }
      }
    } catch (e) {}
  }
  window.__hibouSelectOpenCodePlan = selectOpenCodePlan;

  function syncProviderCardsState() {
    try {
      var raw = "";
      try { raw = window.AgyProviders ? window.AgyProviders.load() : (localStorage.getItem("agyProviders") || ""); } catch (err) {}
      var parsed = { connections: [] };
      if (raw) { try { parsed = JSON.parse(raw); } catch (err) {} }
      var conns = parsed.connections || [];

      var oc = conns.find(function (c) { return c.id === "opencode"; });
      var ocCard = document.querySelector('.hibou-m3-provider-card[data-pv="opencode"]');
      var ocToggle = document.getElementById("hibou-pv-toggle-opencode");
      var ocUrl = document.getElementById("hibou-pv-url-opencode");
      var ocKey = document.getElementById("hibou-pv-key-opencode");
      var ocPlan = (oc && oc.plan) ? oc.plan : "free";

      if (oc && oc.disabled === false) {
        if (ocToggle) ocToggle.checked = true;
        if (ocCard) ocCard.classList.remove("inactive");
      } else {
        if (ocToggle) ocToggle.checked = false;
        if (ocCard) ocCard.classList.add("inactive");
      }
      selectOpenCodePlan(ocPlan);
      if (oc) {
        if (ocUrl && oc.baseUrl) ocUrl.value = oc.baseUrl;
        if (ocKey && oc.apiKey && ocPlan !== "free") ocKey.value = oc.apiKey;
      }

      ["openrouter", "deepseek", "claude", "openai", "mistral"].forEach(function (id) {
        var c = conns.find(function (item) { return item.id === id; });
        var card = document.querySelector('.hibou-m3-provider-card[data-pv="' + id + '"]');
        var toggle = document.getElementById("hibou-pv-toggle-" + id);
        var urlInput = document.getElementById("hibou-pv-url-" + id);
        var keyInput = document.getElementById("hibou-pv-key-" + id);
        if (c && c.disabled === false) {
          if (toggle) toggle.checked = true;
          if (card) card.classList.remove("inactive");
          if (urlInput && c.baseUrl) urlInput.value = c.baseUrl;
          if (keyInput && c.apiKey) keyInput.value = c.apiKey;
        } else {
          if (toggle) toggle.checked = false;
        }
      });
    } catch (e) {}
  }
  window.__hibouSyncProviderCardsState = syncProviderCardsState;

  function openControlCenter(tab) {
    state.controlCenterOpen = true;
    syncProviderCardsState();
    if (tab) switchControlCenterTab(tab);
    var modal = document.getElementById("hibou-control-center-modal");
    if (modal) modal.classList.remove("hibou-hidden");
  }

  function closeControlCenter() {
    state.controlCenterOpen = false;
    var modal = document.getElementById("hibou-control-center-modal");
    if (modal) modal.classList.add("hibou-hidden");
  }

  function switchControlCenterTab(tabName) {
    state.controlCenterTab = tabName;
    var tabs = document.querySelectorAll(".hibou-cc-tab-btn");
    tabs.forEach(function (btn) {
      btn.classList.toggle("active", btn.getAttribute("data-cctab") === tabName);
    });
    var contents = document.querySelectorAll(".hibou-cctab-content");
    contents.forEach(function (content) {
      content.classList.toggle("active", content.id === "hibou-cctab-" + tabName);
    });

    if (tabName === "providers") {
      var container = document.getElementById("hibou-providers-mount-container");
      if (container && window.__agyProvidersMount) {
        window.__agyProvidersMount(container);
      }
    }
  }

  function toggleMultiPurposeMenu() {
    var menu = document.getElementById("hibou-multipurpose-menu");
    if (!menu) return;

    if (state.multiMenuOpen) {
      closeMultiPurposeMenu();
    } else {
      renderMultiPurposeContent();
      menu.classList.remove("hibou-hidden");
      state.multiMenuOpen = true;
    }
  }

  function closeMultiPurposeMenu() {
    var menu = document.getElementById("hibou-multipurpose-menu");
    if (menu) menu.classList.add("hibou-hidden");
    state.multiMenuOpen = false;
  }

  function renderMultiPurposeContent() {
    var contentEl = document.getElementById("hibou-multipurpose-content");
    if (!contentEl) return;

    if (!state.isChatActive) {
      // Home / Projects Mode
      contentEl.innerHTML = [
        '<div class="hibou-dropdown-header">' + i18n("Projects & Workspaces", "Проекты и воркспейсы") + '</div>',
        '<div class="hibou-dropdown-list">',
        '  <button type="button" class="hibou-dropdown-item active" data-action="switch-proj-current">',
        '    ' + ICONS.projects + ' <span>' + escapeHtml(state.currentProject) + '</span> <span class="hibou-item-active-dot"></span>',
        '  </button>',
        '  <button type="button" class="hibou-dropdown-item" data-action="new-project">',
        '    ' + ICONS.plus + ' <span>' + i18n("Create New Project", "Создать новый проект") + '</span>',
        '  </button>',
        '  <button type="button" class="hibou-dropdown-item" data-action="open-folder">',
        '    ' + ICONS.projects + ' <span>' + i18n("Open Folder...", "Открыть папку...") + '</span>',
        '  </button>',
        '</div>'
      ].join("");
    } else {
      // Active Chat / Management Mode
      contentEl.innerHTML = [
        '<div class="hibou-dropdown-header">' + i18n("Conversation Actions", "Действия диалога") + '</div>',
        '<div class="hibou-dropdown-list">',
        '  <button type="button" class="hibou-dropdown-item" data-action="chat-rename">',
        '    ' + ICONS.edit + ' <span>' + i18n("Rename Conversation", "Переименовать диалог") + '</span>',
        '  </button>',
        '  <button type="button" class="hibou-dropdown-item" data-action="chat-save">',
        '    ' + ICONS.save + ' <span>' + i18n("Save & Export Session", "Сохранить и экспортировать") + '</span>',
        '  </button>',
        '  <button type="button" class="hibou-dropdown-item" data-action="chat-pin">',
        '    ' + ICONS.pin + ' <span>' + i18n("Pin to Top", "Закрепить наверху") + '</span>',
        '  </button>',
        '  <button type="button" class="hibou-dropdown-item" data-action="chat-copy">',
        '    ' + ICONS.copy + ' <span>' + i18n("Copy All Text", "Скопировать весь текст") + '</span>',
        '  </button>',
        '  <button type="button" class="hibou-dropdown-item" data-action="chat-review">',
        '    ' + ICONS.review + ' <span>' + i18n("Review File Changes", "Просмотр изменений файлов") + '</span>',
        '  </button>',
        '  <button type="button" class="hibou-dropdown-item" data-action="chat-terminal">',
        '    ' + ICONS.terminal + ' <span>' + i18n("Open Terminal", "Открыть терминал") + '</span>',
        '  </button>',
        '  <button type="button" class="hibou-dropdown-item" data-action="chat-extra">',
        '    ' + ICONS.layers + ' <span>' + i18n("Extra View (Tasks & Agents)", "Инспектор задач и агентов") + '</span>',
        '  </button>',
        '  <button type="button" class="hibou-dropdown-item text-danger" data-action="chat-archive">',
        '    ' + ICONS.archive + ' <span>' + i18n("Archive Conversation", "Архивировать") + '</span>',
        '  </button>',
        '</div>'
      ].join("");
    }
  }

  function handleDropdownAction(action) {
    closeMultiPurposeMenu();
    switch (action) {
      case "new-project":
        var addBtn = document.querySelector('button[data-testid="sidebar-add-project-button"]');
        if (addBtn) addBtn.click();
        break;
      case "chat-rename":
        var kebab = document.querySelector('[data-testid="conversation-kebab"]');
        if (kebab) kebab.click();
        break;
      case "chat-save":
        if (window.__agyExportSession) {
          window.__agyExportSession();
        } else {
          alert(i18n("Exporting conversation session...", "Экспорт сессии диалога..."));
        }
        break;
      case "chat-pin":
        var pinBtn = document.querySelector('[data-testid="conversation-pin-button"]');
        if (pinBtn) pinBtn.click();
        break;
      case "chat-copy":
        var chatText = "";
        var msgs = document.querySelectorAll('[data-testid="user-input-step"], .prose');
        msgs.forEach(function (m) { chatText += m.textContent + "\n\n"; });
        if (navigator.clipboard) {
          navigator.clipboard.writeText(chatText).then(function () {
            alert(i18n("Conversation copied to clipboard!", "Диалог скопирован в буфер!"));
          });
        }
        break;
      case "chat-terminal":
        var termBtn = document.querySelector('[data-testid="toggle-aux-sidebar"]') || document.querySelector('[data-modal="terminal"]');
        if (termBtn) termBtn.click();
        else if (window.__agyToolsMount) {
          var dummy = document.createElement("div");
          window.__agyToolsMount(dummy);
        }
        break;
      case "chat-archive":
        var arcBtn = document.querySelector('[data-testid="conversation-archive-button"]');
        if (arcBtn) arcBtn.click();
        break;
    }
  }

  function setupEventListeners() {
    // Menu button (top-left)
    var btnMenu = document.getElementById("hibou-btn-menu");
    if (btnMenu) {
      btnMenu.addEventListener("click", openDrawer);
    }

    // Close drawer button & backdrop
    var btnCloseDrawer = document.getElementById("hibou-drawer-close");
    if (btnCloseDrawer) {
      btnCloseDrawer.addEventListener("click", closeDrawer);
    }
    var backdrop = document.getElementById("hibou-drawer-backdrop");
    if (backdrop) {
      backdrop.addEventListener("click", closeDrawer);
    }

    // Model selector pill (top-center-left)
    var modelPill = document.getElementById("hibou-model-pill");
    if (modelPill) {
      modelPill.addEventListener("click", function () {
        var trigger = document.querySelector('[data-testid="model-selector-trigger"]');
        if (trigger) trigger.click();
      });
    }

    // Multi-purpose button (top-center-right)
    var btnMulti = document.getElementById("hibou-btn-multipurpose");
    if (btnMulti) {
      btnMulti.addEventListener("click", toggleMultiPurposeMenu);
    }

    // Profile button (top-right)
    var btnProfile = document.getElementById("hibou-btn-profile");
    if (btnProfile) {
      btnProfile.addEventListener("click", function () {
        openControlCenter("accounts");
      });
    }

    // Control Center close & tabs
    var btnCloseCC = document.getElementById("hibou-cc-close");
    if (btnCloseCC) {
      btnCloseCC.addEventListener("click", closeControlCenter);
    }
    var ccTabs = document.querySelectorAll(".hibou-cc-tab-btn");
    ccTabs.forEach(function (tab) {
      tab.addEventListener("click", function () {
        switchControlCenterTab(tab.getAttribute("data-cctab"));
      });
    });

    // GitHub token save in Control Center
    var ghSaveBtn = document.getElementById("hibou-gh-token-save");
    var ghInput = document.getElementById("hibou-gh-token-input");
    if (ghSaveBtn && ghInput) {
      ghSaveBtn.addEventListener("click", function () {
        var token = ghInput.value.trim();
        if (token) {
          localStorage.setItem("hibou_gh_token", token);
          alert(i18n("GitHub token saved!", "GitHub-токен сохранён!"));
        }
      });
    }

    // Multi-purpose dropdown actions
    document.addEventListener("click", function (e) {
      var item = e.target.closest(".hibou-dropdown-item");
      if (item) {
        var action = item.getAttribute("data-action");
        if (action) handleDropdownAction(action);
      } else if (!e.target.closest("#hibou-multipurpose-menu") && !e.target.closest("#hibou-btn-multipurpose")) {
        closeMultiPurposeMenu();
      }
    });

    // Drawer navigation items
    document.addEventListener("click", function (e) {
      var navItem = e.target.closest(".hibou-drawer-nav-item");
      if (navItem) {
        var action = navItem.getAttribute("data-action");
        closeDrawer();
        switch (action) {
          case "providers":
            openControlCenter("providers");
            break;
          case "tools":
            var toolsCont = document.createElement("div");
            if (window.__agyToolsMount) window.__agyToolsMount(toolsCont);
            break;
          case "mcp":
            var mcpCont = document.createElement("div");
            if (window.__agyMcpMount) window.__agyMcpMount(mcpCont);
            break;
          case "settings":
            var settingsBtn = document.querySelector('[data-testid="settings-button"]');
            if (settingsBtn) settingsBtn.click();
            break;
          case "new-chat":
            var newChatBtn = document.querySelector('[data-testid="app-icon-new-conversation-button"]') || document.querySelector('[data-testid="new-conversation-button"]');
            if (newChatBtn) newChatBtn.click();
            break;
        }
      }
    });

    // Stage Selector (Local vs Worktree)
    var btnStageLocal = document.getElementById("hibou-stage-local");
    var btnStageWorktree = document.getElementById("hibou-stage-worktree");
    if (btnStageLocal && btnStageWorktree) {
      btnStageLocal.addEventListener("click", function () {
        state.stageMode = "local";
        btnStageLocal.classList.add("active");
        btnStageWorktree.classList.remove("active");
      });
      btnStageWorktree.addEventListener("click", function () {
        state.stageMode = "worktree";
        btnStageWorktree.classList.add("active");
        btnStageLocal.classList.remove("active");
      });
    }

    // Branch Pill Tapping
    var branchPill = document.getElementById("hibou-branch-selector-pill");
    if (branchPill) {
      branchPill.addEventListener("click", function () {
        var nativeBranchSelector = document.querySelector('[aria-label="Select Default Branch"]');
        if (nativeBranchSelector) {
          nativeBranchSelector.click();
        } else {
          var newBranch = prompt(i18n("Switch to or create git branch:", "Переключить или создать git-ветку:"), state.currentBranch);
          if (newBranch && newBranch.trim()) {
            state.currentBranch = newBranch.trim();
            var label = document.getElementById("hibou-branch-name");
            if (label) label.textContent = state.currentBranch;
          }
        }
      });
    }
    // OpenCode Plan Chip clicks
    document.addEventListener("click", function (e) {
      var planChip = e.target.closest("[data-opencode-plan]");
      if (planChip) {
        var plan = planChip.getAttribute("data-opencode-plan");
        selectOpenCodePlan(plan);
      }
    });

    // Provider testing and configuration handlers in Control Center
    document.addEventListener("click", function (e) {
      var testBtn = e.target.closest("[data-test-pv]");
      if (testBtn) {
        var pv = testBtn.getAttribute("data-test-pv");
        var keyInput = document.getElementById("hibou-pv-key-" + pv);
        var urlInput = document.getElementById("hibou-pv-url-" + pv);
        var statusEl = document.getElementById("hibou-pv-status-" + pv);
        var key = keyInput ? keyInput.value.trim() : "";
        var url = urlInput ? urlInput.value.trim() : "";

        var activePlan = "free";
        if (pv === "opencode") {
          var activeChip = document.querySelector("#hibou-opencode-plan-selector [data-opencode-plan].active");
          if (activeChip) activePlan = activeChip.getAttribute("data-opencode-plan") || "free";
        }

        var defaultUrls = {
          openrouter: "https://openrouter.ai/api/v1",
          deepseek: "https://api.deepseek.com/v1",
          opencode: activePlan === "local" ? "http://localhost:4096/v1" : "https://opencode.ai/zen/v1",
          claude: "https://api.anthropic.com",
          anthropic: "https://api.anthropic.com",
          openai: "https://api.openai.com/v1",
          mistral: "https://api.mistral.ai/v1"
        };
        if (!url && defaultUrls[pv]) {
          url = defaultUrls[pv];
          if (urlInput) urlInput.value = url;
        }

        var typeMapping = { claude: "anthropic", anthropic: "anthropic", opencode: "opencode" };
        var typeId = typeMapping[pv] || "openai";

        if (window.__agyNormBase) {
          url = window.__agyNormBase(url, typeId, pv);
        } else if (pv === "openrouter" || /openrouter\.ai/i.test(url)) {
          url = url.replace(/\/+(api(\/v1)?|v1)?\/?$/i, "") + "/api/v1";
        } else if (pv === "opencode") {
          if (/localhost|127\.0\.0\.1|:4096/i.test(url)) {
            url = url.replace(/\/+v1\/?$/i, "") + "/v1";
          } else {
            url = url.replace(/\/+(zen(\/v1)?|api(\/v1)?|v1)?\/?$/i, "") + "/zen/v1";
          }
        }

        if (pv === "opencode" && (activePlan === "free" || activePlan === "local")) {
          if (!key) key = activePlan === "free" ? "public" : "local";
        } else if (!key) {
          if (statusEl) statusEl.textContent = i18n("API key required", "Требуется API-ключ");
          return;
        }
        if (statusEl) statusEl.textContent = i18n("Testing & saving...", "Проверка и сохранение...");
        try {
          var raw = "";
          try { raw = window.AgyProviders ? window.AgyProviders.load() : (localStorage.getItem("agyProviders") || ""); } catch (err) {}
          var parsed = { connections: [] };
          if (raw) { try { parsed = JSON.parse(raw); } catch (err) {} }
          parsed.connections = parsed.connections || [];
          var existing = parsed.connections.find(function (c) { return c.id === pv; });
          if (!existing) {
            existing = { id: pv, name: pv.toUpperCase(), type: typeId, baseUrl: url, apiKey: key, selected: [] };
            parsed.connections.push(existing);
          } else {
            existing.apiKey = key;
            existing.baseUrl = url;
            existing.type = typeId;
          }
          if (pv === "opencode") existing.plan = activePlan;
          existing.disabled = false;
          parsed.useProviders = true;
          var jsonStr = JSON.stringify(parsed);
          localStorage.setItem("agyProviders", jsonStr);
          if (window.AgyProviders && typeof window.AgyProviders.save === "function") {
            window.AgyProviders.save(jsonStr);
          }
          if (statusEl) statusEl.textContent = i18n("Connected & Ready", "Подключено и готово");
          var quotaSt = document.getElementById("hibou-quota-st-" + pv);
          if (quotaSt) quotaSt.textContent = i18n("Connected", "Подключено");

          // Active test & model retrieval
          if (window.AgyProviders && typeof window.AgyProviders.fetchModels === "function") {
            try {
              var headersObj = {};
              if (pv === "opencode") {
                headersObj["User-Agent"] = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36";
              }
              var jobId = window.AgyProviders.fetchModels(typeId, url, key, JSON.stringify(headersObj));
              var pollCount = 0;
              var pollTimer = setInterval(function () {
                pollCount++;
                var resRaw = window.AgyProviders.readFetch(jobId);
                var res = {};
                try { res = JSON.parse(resRaw || "{}"); } catch (e) {}
                if (res.done || pollCount > 12) {
                  clearInterval(pollTimer);
                  var code = parseInt(res.status, 10) || 0;
                  if (code >= 200 && code < 300) {
                    try {
                      var parsedModels = JSON.parse(res.body || "{}");
                      var mList = Array.isArray(parsedModels) ? parsedModels : (parsedModels.data || parsedModels.models || []);
                      if (Array.isArray(mList) && mList.length) {
                        existing.models = mList.map(function (m) { return typeof m === "string" ? m : (m.id || m.name); }).filter(Boolean);
                        if (pv === "opencode" && activePlan === "free") {
                          existing.models.sort(function (a, b) {
                            var aFree = /free/i.test(a) ? -1 : 1;
                            var bFree = /free/i.test(b) ? -1 : 1;
                            return aFree - bFree;
                          });
                        }
                        if ((!existing.selected || !existing.selected.length) && existing.models.length) {
                          existing.selected = [existing.models[0]];
                        }
                        var updatedJson = JSON.stringify(parsed);
                        localStorage.setItem("agyProviders", updatedJson);
                        window.AgyProviders.save(updatedJson);
                        if (window.__agyRefreshProviderModels) window.__agyRefreshProviderModels();
                        var freeNotice = (pv === "opencode" && activePlan === "free") ? " free models ready - No key needed" : " models";
                        var freeNoticeRu = (pv === "opencode" && activePlan === "free") ? " бесплатных моделей - Без ключа" : " моделей";
                        if (statusEl) statusEl.textContent = i18n("Connected (" + existing.models.length + freeNotice + ")", "Подключено (" + existing.models.length + freeNoticeRu + ")");
                        return;
                      }
                    } catch (e) {}
                    if (statusEl) statusEl.textContent = i18n("Connected & Ready", "Подключено и готово");
                  } else if (code >= 400) {
                    var cleanErr = window.__agyCleanBody ? window.__agyCleanBody(res.body) : ("Error " + code);
                    if (statusEl) statusEl.textContent = i18n("Error " + code + ": " + cleanErr, "Ошибка " + code + ": " + cleanErr);
                  }
                }
              }, 400);
            } catch (err) {}
          }
        } catch (err) {
          if (statusEl) statusEl.textContent = i18n("Saved locally", "Сохранено");
        }
      }
    });

    // Provider active switch toggles
    document.addEventListener("change", function (e) {
      if (e.target && e.target.id && e.target.id.startsWith("hibou-pv-toggle-")) {
        var pvId = e.target.id.replace("hibou-pv-toggle-", "");
        var checked = e.target.checked;
        var card = document.querySelector('.hibou-m3-provider-card[data-pv="' + pvId + '"]');
        if (card) {
          card.classList.toggle("inactive", !checked);
        }
        try {
          var raw = "";
          try { raw = window.AgyProviders ? window.AgyProviders.load() : (localStorage.getItem("agyProviders") || ""); } catch (err) {}
          var parsed = { connections: [] };
          if (raw) { try { parsed = JSON.parse(raw); } catch (err) {} }
          parsed.connections = parsed.connections || [];
          var existing = parsed.connections.find(function (c) { return c.id === pvId; });
          if (!existing) {
            if (pvId === "opencode") {
              existing = {
                id: "opencode",
                name: "OPENCODE",
                type: "opencode",
                plan: "free",
                baseUrl: "https://opencode.ai/zen/v1",
                apiKey: "public",
                disabled: !checked,
                selected: ["nemotron-3.5-super-vl-free"],
                models: [
                  "nemotron-3.5-super-vl-free",
                  "nemotron-3.5-lightning-free",
                  "minimax-m3.1-flash-free",
                  "big-pickle",
                  "deepseek-v4.1-flash-free",
                  "mimo-v2.6-flash-free",
                  "kimi-k2.5-free",
                  "glm-4.7-free",
                  "doubao-pro-free",
                  "sensechat-5.5-free",
                  "hunyuan-turbo-free",
                  "spark-4.0-ultra-free"
                ]
              };
              parsed.connections.push(existing);
            } else {
              existing = { id: pvId, name: pvId.toUpperCase(), type: pvId === "claude" ? "anthropic" : "openai", disabled: !checked };
              parsed.connections.push(existing);
            }
          } else {
            existing.disabled = !checked;
          }
          parsed.useProviders = true;
          var jsonStr = JSON.stringify(parsed);
          localStorage.setItem("agyProviders", jsonStr);
          if (window.AgyProviders && typeof window.AgyProviders.save === "function") {
            window.AgyProviders.save(jsonStr);
          }
          if (window.__agyRefreshProviderModels) window.__agyRefreshProviderModels();
          if (checked && pvId === "opencode") {
            var quotaSt = document.getElementById("hibou-quota-st-opencode");
            if (quotaSt) quotaSt.textContent = i18n("Active (Free Tier)", "Активен (Бесплатный)");
          }
        } catch (err) {}
      }
    });
  }

  // ---------------------------------------------------------------------------
  // 7. Dynamic State Observer (Sync with React DOM & Chat Visibility)
  // ---------------------------------------------------------------------------
  function observeAppState() {
    function update() {
      // 1. Detect if chat is active (messages present or in conversation view)
      var messages = document.querySelectorAll('[data-testid="user-input-step"], [data-testid="assistant-message"], .prose');
      var hasActiveChat = messages.length > 0;
      state.isChatActive = hasActiveChat;
      state.messageCount = messages.length;

      // 2. Fade Greeting & Top Glow smoothly when conversation starts
      var greetingHero = document.getElementById("hibou-greeting-hero");
      var topAmbientGlow = document.getElementById("hibou-top-ambient-glow");
      if (greetingHero) {
        if (hasActiveChat) {
          greetingHero.classList.add("hibou-fade-out");
        } else {
          greetingHero.classList.remove("hibou-fade-out");
        }
      }
      if (topAmbientGlow) {
        if (hasActiveChat) {
          topAmbientGlow.classList.add("hibou-glow-dimmed");
        } else {
          topAmbientGlow.classList.remove("hibou-glow-dimmed");
        }
      }

      // 3. Update Multi-Purpose Button (Projects vs Management)
      var multiIcon = document.getElementById("hibou-multipurpose-icon");
      var multiLabel = document.getElementById("hibou-multipurpose-label");
      if (multiIcon && multiLabel) {
        if (hasActiveChat) {
          multiIcon.innerHTML = ICONS.dots;
          multiLabel.textContent = i18n("Manage", "Действия");
        } else {
          multiIcon.innerHTML = ICONS.projects;
          multiLabel.textContent = i18n("Projects", "Проекты");
        }
      }

      // 4. Update Model Title & Quota from native selector if visible
      var modelTrigger = document.querySelector('[data-testid="model-selector-trigger"]');
      if (modelTrigger) {
        var txt = (modelTrigger.textContent || "").trim();
        if (txt && txt.length < 35 && txt !== state.activeModelName) {
          state.activeModelName = txt;
          var titleEl = document.getElementById("hibou-model-title");
          if (titleEl) titleEl.textContent = txt;
        }
      }

      // 5. Update Branch Pill from native git pill if visible
      var branchEl = document.querySelector('[data-testid^="conversation-branch-"], .branch-indicator, [aria-label="Select Default Branch"]');
      if (branchEl) {
        var bText = (branchEl.textContent || "").trim();
        if (bText && bText.length < 30 && bText !== state.currentBranch) {
          state.currentBranch = bText;
          var bLabel = document.getElementById("hibou-branch-name");
          if (bLabel) bLabel.textContent = bText;
        }
      }

      // 6. Dock upper tier directly atop native chat input box
      var inputBox = document.querySelector('[data-testid="agent-input-box"]');
      var upperTier = document.getElementById("hibou-dock-upper-tier");
      if (inputBox && upperTier && inputBox.parentElement) {
        if (upperTier.nextElementSibling !== inputBox) {
          inputBox.parentElement.insertBefore(upperTier, inputBox);
        }
      }
    }

    var observer = new MutationObserver(update);
    observer.observe(document.body, { childList: true, subtree: true });
    setInterval(update, 1500);
    update();
  }

  // ---------------------------------------------------------------------------
  // 8. Initialization
  // ---------------------------------------------------------------------------
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", buildMobileShell);
  } else {
    buildMobileShell();
  }
})();
