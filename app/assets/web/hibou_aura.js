/**
 * Hibou Animated Thermal Aura Background
 * Inspired by the vibrant electric thermal gradient artwork (deep royal blue,
 * undulating cyan aura, glowing fiery orange/amber 4-pointed flares, and risograph grain).
 * Optimized for mobile Android WebView: 60fps, low memory footprint, auto-pauses when signed in.
 */
(function () {
  "use strict";

  var canvas = null;
  var ctx = null;
  var animId = null;
  var running = false;
  var grainEl = null;

  function isLoginScreenPresent() {
    var root = document.getElementById("root");
    if (!root) return false;
    return !!(
      root.querySelector(".entrance-auth-panel") ||
      root.querySelector(".entrance-title") ||
      root.querySelector("[data-testid='onboarding-cancel-sign-in']")
    );
  }

  function createNoiseOverlay() {
    if (document.getElementById("hibou-aura-noise")) return;
    var overlay = document.createElement("div");
    overlay.id = "hibou-aura-noise";
    overlay.style.cssText = [
      "position: fixed",
      "inset: 0",
      "width: 100%",
      "height: 100%",
      "pointer-events: none",
      "z-index: 1",
      "opacity: 0.16",
      "mix-blend-mode: overlay",
      "background-repeat: repeat",
      "background-image: url(\"data:image/svg+xml;utf8,<svg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'><filter id='noise'><feTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='3' stitchTiles='stitch'/></filter><rect width='100%25' height='100%25' filter='url(%23noise)'/></svg>\")"
    ].join(";");
    grainEl = overlay;
    document.body.appendChild(overlay);
  }

  function createAuraCanvas() {
    if (canvas) return;
    canvas = document.createElement("canvas");
    canvas.id = "hibou-aura-bg";
    canvas.style.cssText = [
      "position: fixed",
      "inset: 0",
      "width: 100%",
      "height: 100%",
      "pointer-events: none",
      "z-index: 0",
      "background: #001242"
    ].join(";");
    ctx = canvas.getContext("2d");
    document.body.prepend(canvas);
    createNoiseOverlay();
    resize();
    window.addEventListener("resize", resize, { passive: true });
  }

  function resize() {
    if (!canvas) return;
    var dpr = Math.min(window.devicePixelRatio || 1, 1.5);
    var w = window.innerWidth || 360;
    var h = window.innerHeight || 640;
    canvas.width = Math.round(w * dpr);
    canvas.height = Math.round(h * dpr);
    if (ctx) ctx.scale(dpr, dpr);
  }

  // Draw 4-pointed thermal flare star with curved diamond rays
  function drawThermalStar(x, y, radius, angle, t) {
    ctx.save();
    ctx.translate(x, y);
    ctx.rotate(angle);

    // 1. Wide ambient amber bloom
    var gAmbient = ctx.createRadialGradient(0, 0, 0, 0, 0, radius * 2.2);
    gAmbient.addColorStop(0, "rgba(255, 140, 20, 0.45)");
    gAmbient.addColorStop(0.3, "rgba(255, 75, 0, 0.35)");
    gAmbient.addColorStop(0.65, "rgba(255, 40, 0, 0.12)");
    gAmbient.addColorStop(1, "rgba(0, 20, 90, 0)");
    ctx.fillStyle = gAmbient;
    ctx.beginPath();
    ctx.arc(0, 0, radius * 2.2, 0, Math.PI * 2);
    ctx.fill();

    // 2. Cyan aura halo surrounding the star
    var gCyan = ctx.createRadialGradient(0, 0, radius * 0.4, 0, 0, radius * 1.5);
    gCyan.addColorStop(0, "rgba(0, 225, 255, 0.55)");
    gCyan.addColorStop(0.5, "rgba(56, 189, 248, 0.3)");
    gCyan.addColorStop(1, "rgba(0, 20, 90, 0)");
    ctx.fillStyle = gCyan;
    ctx.beginPath();
    ctx.arc(0, 0, radius * 1.5, 0, Math.PI * 2);
    ctx.fill();

    // 3. Asymmetric 4-pointed flare star body (hot orange to vermilion)
    var rLong = radius * 1.6;
    var rShort = radius * 0.9;
    var rWaist = radius * 0.18;

    ctx.beginPath();
    // Top-right long ray
    ctx.moveTo(rLong, -rLong * 0.65);
    ctx.quadraticCurveTo(rWaist, -rWaist, 0, -rShort);
    // Top-left ray
    ctx.quadraticCurveTo(-rWaist, -rWaist, -rShort * 0.8, -rShort * 0.6);
    ctx.quadraticCurveTo(-rWaist, rWaist, -rLong * 0.85, rLong * 0.7);
    // Bottom-left ray
    ctx.quadraticCurveTo(rWaist, rWaist, rShort * 0.5, rShort * 0.6);
    ctx.quadraticCurveTo(rWaist, -rWaist, rLong, -rLong * 0.65);
    ctx.closePath();

    var gStar = ctx.createRadialGradient(0, 0, 0, 0, 0, rLong);
    gStar.addColorStop(0, "#ffffff");
    gStar.addColorStop(0.18, "#ffeed6");
    gStar.addColorStop(0.35, "#ff5d00");
    gStar.addColorStop(0.65, "#e62b00");
    gStar.addColorStop(1, "rgba(200, 20, 0, 0)");
    ctx.fillStyle = gStar;
    ctx.fill();

    // 4. Glowing white-hot star center
    var gCore = ctx.createRadialGradient(0, 0, 0, 0, 0, radius * 0.28);
    gCore.addColorStop(0, "#ffffff");
    gCore.addColorStop(0.4, "rgba(255, 255, 255, 0.95)");
    gCore.addColorStop(0.7, "rgba(255, 200, 140, 0.8)");
    gCore.addColorStop(1, "rgba(255, 90, 0, 0)");
    ctx.fillStyle = gCore;
    ctx.beginPath();
    ctx.arc(0, 0, radius * 0.28, 0, Math.PI * 2);
    ctx.fill();

    ctx.restore();
  }

  // Draw curving thermal wave ribbons (cyan aura and fiery crests)
  function drawThermalWaves(w, h, t) {
    var waveShift = Math.sin(t * 0.001) * 24;
    var waveShift2 = Math.cos(t * 0.0013) * 20;

    // A. Main Cyan Aura Stream flowing diagonally
    ctx.save();
    ctx.beginPath();
    ctx.moveTo(w * 0.95, -h * 0.05);
    ctx.bezierCurveTo(
      w * 0.75 + waveShift, h * 0.2,
      w * 0.45 + waveShift2, h * 0.55,
      -w * 0.1, h * 0.85
    );
    ctx.lineWidth = Math.min(w, h) * 0.38;
    var gCyanStream = ctx.createLinearGradient(w * 0.9, 0, 0, h * 0.8);
    gCyanStream.addColorStop(0, "rgba(0, 210, 255, 0.45)");
    gCyanStream.addColorStop(0.4, "rgba(125, 230, 255, 0.55)");
    gCyanStream.addColorStop(0.7, "rgba(0, 180, 255, 0.4)");
    gCyanStream.addColorStop(1, "rgba(0, 100, 255, 0)");
    ctx.strokeStyle = gCyanStream;
    ctx.lineCap = "round";
    ctx.stroke();

    // Bright cyan core line
    ctx.lineWidth = Math.min(w, h) * 0.12;
    var gWhiteCore = ctx.createLinearGradient(w * 0.8, 0, 0, h * 0.7);
    gWhiteCore.addColorStop(0, "rgba(255, 255, 255, 0.65)");
    gWhiteCore.addColorStop(0.5, "rgba(240, 253, 255, 0.75)");
    gWhiteCore.addColorStop(1, "rgba(180, 240, 255, 0.1)");
    ctx.strokeStyle = gWhiteCore;
    ctx.stroke();
    ctx.restore();

    // B. Top-Right Fiery Crescent Flame Wave
    ctx.save();
    var topFlameX = w * 0.82 + Math.cos(t * 0.0011) * 12;
    var topFlameY = h * 0.06 + Math.sin(t * 0.0014) * 10;
    var gTopFlame = ctx.createRadialGradient(topFlameX, topFlameY, 0, topFlameX, topFlameY, w * 0.45);
    gTopFlame.addColorStop(0, "#ffffff");
    gTopFlame.addColorStop(0.12, "#ff6b00");
    gTopFlame.addColorStop(0.35, "#e53300");
    gTopFlame.addColorStop(0.7, "rgba(220, 40, 0, 0.15)");
    gTopFlame.addColorStop(1, "rgba(0, 20, 80, 0)");
    ctx.fillStyle = gTopFlame;
    ctx.beginPath();
    ctx.arc(topFlameX, topFlameY, w * 0.45, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();

    // C. Bottom Fiery Peak Flame Wave
    ctx.save();
    var botFlameX = w * 0.5 + Math.sin(t * 0.0012) * 16;
    var botFlameY = h * 0.98 + Math.cos(t * 0.0015) * 12;
    var gBotFlame = ctx.createRadialGradient(botFlameX, botFlameY, 0, botFlameX, botFlameY, w * 0.5);
    gBotFlame.addColorStop(0, "#ffeed6");
    gBotFlame.addColorStop(0.15, "#ff5b00");
    gBotFlame.addColorStop(0.4, "#d82400");
    gBotFlame.addColorStop(0.75, "rgba(200, 30, 0, 0.12)");
    gBotFlame.addColorStop(1, "rgba(0, 15, 60, 0)");
    ctx.fillStyle = gBotFlame;
    ctx.beginPath();
    ctx.arc(botFlameX, botFlameY, w * 0.5, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
  }

  function render(time) {
    if (!running || !canvas || !ctx) return;

    var w = window.innerWidth || 360;
    var h = window.innerHeight || 640;

    // 1. Deep cobalt / royal blue gradient base
    var gBase = ctx.createRadialGradient(w * 0.75, h * 0.55, w * 0.1, w * 0.5, h * 0.5, Math.max(w, h));
    gBase.addColorStop(0, "#0038aa");
    gBase.addColorStop(0.35, "#002888");
    gBase.addColorStop(0.65, "#00185e");
    gBase.addColorStop(1, "#000a2b");
    ctx.fillStyle = gBase;
    ctx.fillRect(0, 0, w, h);

    // 2. Flowing thermal aura waves & flames
    drawThermalWaves(w, h, time);

    // 3. Signature 4-pointed thermal star flare on the mid-left
    var starX = w * 0.22 + Math.sin(time * 0.0009) * 10;
    var starY = h * 0.44 + Math.cos(time * 0.0011) * 12;
    var starR = Math.min(w, h) * 0.21 * (1 + 0.05 * Math.sin(time * 0.0018));
    var starAngle = -0.22 + Math.sin(time * 0.0007) * 0.05;
    drawThermalStar(starX, starY, starR, starAngle, time);

    animId = requestAnimationFrame(render);
  }

  function startAura() {
    if (running) return;
    createAuraCanvas();
    if (canvas) canvas.style.display = "block";
    if (grainEl) grainEl.style.display = "block";
    document.body.classList.add("hibou-login-active");
    running = true;
    animId = requestAnimationFrame(render);
  }

  function stopAura() {
    if (!running && !canvas && !grainEl) return;
    running = false;
    if (animId) {
      cancelAnimationFrame(animId);
      animId = null;
    }
    if (canvas && canvas.parentNode) {
      canvas.parentNode.removeChild(canvas);
      canvas = null;
      ctx = null;
    }
    if (grainEl && grainEl.parentNode) {
      grainEl.parentNode.removeChild(grainEl);
      grainEl = null;
    }
    document.body.classList.remove("hibou-login-active");
  }

  // Monitor DOM for Login Screen vs Main App Chat
  function checkState() {
    var onLogin = isLoginScreenPresent();
    if (onLogin && !running) {
      startAura();
    } else if (!onLogin && (running || canvas || grainEl)) {
      stopAura();
    }

    // Enhance Login Screen text and button
    if (onLogin) {
      enhanceLoginUI();
    }
  }

  function enhanceLoginUI() {
    // 1. Ensure title says "Welcome to Hibou"
    var title = document.querySelector(".entrance-title");
    if (title && (title.textContent.includes("Antigravity") || title.textContent.includes("Jetski") || title.textContent.includes("Leostryx"))) {
      title.textContent = "Welcome to Hibou";
    }

    // 2. Ensure Primary Login Button says "Login via Antigravity"
    var buttons = document.querySelectorAll(".entrance-auth-panel button");
    buttons.forEach(function (btn) {
      var txt = btn.textContent.trim();
      var titleAttr = btn.getAttribute("title") || "";
      if (
        txt === "Continue with Google" ||
        txt === "Login with Google" ||
        txt === "Sign in with Google" ||
        titleAttr === "Continue with Google"
      ) {
        btn.setAttribute("title", "Login via Antigravity");
        // Preserve icon, replace text node
        var hasSvg = btn.querySelector("svg, div.w-4");
        btn.innerHTML = (hasSvg ? hasSvg.outerHTML : "") + "Login via Antigravity";
        btn.classList.add("hibou-primary-login-btn");
      }
    });
  }

  var checkPending = false;
  function debouncedCheck() {
    if (checkPending) return;
    checkPending = true;
    requestAnimationFrame(function () {
      checkPending = false;
      checkState();
    });
  }

  var observer = new MutationObserver(debouncedCheck);

  function init() {
    checkState();
    var root = document.getElementById("root") || document.body;
    observer.observe(root, { childList: true, subtree: true });
    document.addEventListener("visibilitychange", function () {
      if (document.hidden && running) {
        if (animId) cancelAnimationFrame(animId);
        animId = null;
      } else if (!document.hidden && running && !animId) {
        animId = requestAnimationFrame(render);
      }
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
