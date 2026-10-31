// Apple Music TV Mode — content script (MAIN world, document_start)
// อ่านสถานะเพลงจากเว็บ music.apple.com เอง (ไม่ใช้ Apple Music API / developer token)
// UI ทั้งหมดอยู่ใน Shadow DOM สร้างด้วย createElement เท่านั้น (เว็บบังคับ Trusted Types)
"use strict";

(() => {
  if (window.__amtvLoaded) return;
  window.__amtvLoaded = true;

  const FONT_STACK = '"AMTV Sans", -apple-system, "SF Pro Display", "Segoe UI Variable Display", "Leelawadee UI", sans-serif';
  const LYRIC_LEAD = 0.25;
  const IDLE_MS = 3500;
  const ZOOMS = [100, 115, 130, 150];
  const ZOOM_KEY = "amtv:zoom";

  // ---------------------------------------------------------------- hooks
  const actionHandlers = Object.create(null);
  let positionState = null; // { duration, position, rate, at }
  let lastMedia = null;

  try {
    const MS = window.MediaSession && MediaSession.prototype;
    if (MS && MS.setActionHandler) {
      const origSet = MS.setActionHandler;
      MS.setActionHandler = function (action, handler) {
        try { actionHandlers[action] = handler || null; } catch (e) {}
        return origSet.apply(this, arguments);
      };
    }
    if (MS && MS.setPositionState) {
      const origPos = MS.setPositionState;
      MS.setPositionState = function (state) {
        try {
          if (state && isFinite(state.position)) {
            positionState = { duration: state.duration, position: state.position, rate: state.playbackRate || 1, at: performance.now() };
          } else positionState = null;
        } catch (e) {}
        return origPos.apply(this, arguments);
      };
    }
  } catch (e) {}

  try {
    const MP = HTMLMediaElement.prototype;
    const origPlay = MP.play;
    MP.play = function () {
      try { if (this instanceof HTMLAudioElement || this.tagName === "VIDEO") lastMedia = this; } catch (e) {}
      return origPlay.apply(this, arguments);
    };
  } catch (e) {}

  // ---------------------------------------------------------------- utils
  const $ = (tag, cls, parent, text) => {
    const el = document.createElement(tag);
    if (cls) el.className = cls;
    if (text != null) el.textContent = text;
    if (parent) parent.appendChild(el);
    return el;
  };
  const SVGNS = "http://www.w3.org/2000/svg";
  const icon = (paths, parent, vb) => {
    const s = document.createElementNS(SVGNS, "svg");
    s.setAttribute("viewBox", vb || "0 0 24 24");
    s.setAttribute("aria-hidden", "true");
    for (const d of paths) {
      const p = document.createElementNS(SVGNS, "path");
      p.setAttribute("d", d);
      s.appendChild(p);
    }
    if (parent) parent.appendChild(s);
    return s;
  };
  const ICONS = {
    play: ["M7 4.6v14.8c0 .8.9 1.3 1.6.9l12-7.4c.6-.4.6-1.3 0-1.7l-12-7.4C7.9 3.3 7 3.8 7 4.6z"],
    pause: ["M6.5 4h3c.6 0 1 .4 1 1v14c0 .6-.4 1-1 1h-3c-.6 0-1-.4-1-1V5c0-.6.4-1 1-1z", "M14.5 4h3c.6 0 1 .4 1 1v14c0 .6-.4 1-1 1h-3c-.6 0-1-.4-1-1V5c0-.6.4-1 1-1z"],
    next: ["M2.5 6.2v11.6c0 .7.8 1.1 1.4.7l8.6-5.8c.5-.3.5-1.1 0-1.4L3.9 5.5c-.6-.4-1.4 0-1.4.7z", "M11.5 6.2v11.6c0 .7.8 1.1 1.4.7l8.6-5.8c.5-.3.5-1.1 0-1.4l-8.6-5.8c-.6-.4-1.4 0-1.4.7z"],
    prev: ["M21.5 6.2v11.6c0 .7-.8 1.1-1.4.7l-8.6-5.8c-.5-.3-.5-1.1 0-1.4l8.6-5.8c.6-.4 1.4 0 1.4.7z", "M12.5 6.2v11.6c0 .7-.8 1.1-1.4.7l-8.6-5.8c-.5-.3-.5-1.1 0-1.4l8.6-5.8c.6-.4 1.4 0 1.4.7z"],
    lyrics: ["M4 4h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H9.5L5 21.5V18H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zm3.2 5.2a1.4 1.4 0 0 0-1.4 1.4c0 .8.6 1.4 1.3 1.4-.1.7-.5 1.2-1.1 1.5l.4.7c1.3-.5 2.2-1.6 2.2-3 0-1.2-.6-2-1.4-2zm4.6 0a1.4 1.4 0 0 0-1.4 1.4c0 .8.6 1.4 1.3 1.4-.1.7-.5 1.2-1.1 1.5l.4.7c1.3-.5 2.2-1.6 2.2-3 0-1.2-.6-2-1.4-2z"],
    full: ["M4 9V5a1 1 0 0 1 1-1h4v2H6v3H4zm11-5h4a1 1 0 0 1 1 1v4h-2V6h-3V4zM4 15h2v3h3v2H5a1 1 0 0 1-1-1v-4zm14 0h2v4a1 1 0 0 1-1 1h-4v-2h3v-3z"],
    close: ["M6.4 5 12 10.6 17.6 5 19 6.4 13.4 12l5.6 5.6-1.4 1.4L12 13.4 6.4 19 5 17.6 10.6 12 5 6.4z"],
    tv: ["M3 5h18a1 1 0 0 1 1 1v11a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1zm1 2v9h16V7H4zm4 13h8v1.5H8z"],
    note: ["M17.5 3.2 9.3 5A1 1 0 0 0 8.5 6v9.6a3.4 3.4 0 1 0 2 3.1V10l7-1.5v5.1a3.4 3.4 0 1 0 2 3.1V4.2a1 1 0 0 0-1.2-1z"],
  };

  const fmt = (s) => {
    if (!isFinite(s) || s < 0) s = 0;
    s = Math.floor(s);
    const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), r = s % 60;
    return (h ? h + ":" + String(m).padStart(2, "0") : m) + ":" + String(r).padStart(2, "0");
  };

  function hiresArt(url) {
    if (!url) return "";
    url = String(url);
    if (url.includes("{w}")) return url.replace("{w}", "1400").replace("{h}", "1400").replace("{f}", "jpg").replace("{c}", "bb");
    return url.replace(/\/(\d{2,4})x(\d{2,4})([a-z]{0,3})(-\d+)?\.(jpe?g|png|webp)(?=$|[?#])/i,
      (_, w, h, suf, q, ext) => `/1400x1400${suf || "bb"}.${ext}`);
  }

  function isEditable(el) {
    if (!el || el.nodeType !== 1) return false;
    if (el.isContentEditable) return true;
    const tag = el.tagName;
    if (tag === "TEXTAREA" || tag === "SELECT") return true;
    if (tag === "INPUT") {
      const t = (el.type || "text").toLowerCase();
      return !["button", "submit", "reset", "checkbox", "radio", "range", "color", "file", "image"].includes(t);
    }
    const role = el.getAttribute && el.getAttribute("role");
    return role === "textbox" || role === "searchbox" || role === "combobox";
  }

  // ---------------------------------------------------------------- player
  function getMK() {
    try {
      const MK = window.MusicKit;
      if (!MK || typeof MK.getInstance !== "function") return null;
      return MK.getInstance() || null;
    } catch (e) { return null; }
  }

  function getMedia() {
    if (lastMedia && lastMedia.src !== "" && !lastMedia.ended) return lastMedia;
    try {
      const all = document.querySelectorAll("audio, video");
      for (const m of all) if (!m.paused) return m;
      if (lastMedia) return lastMedia;
      return all[0] || null;
    } catch (e) { return lastMedia; }
  }

  function largestArtwork(list) {
    if (!list || !list.length) return "";
    let best = list[0], bestW = 0;
    for (const a of list) {
      const w = parseInt(String(a.sizes || "").split("x")[0], 10) || 0;
      if (w >= bestW) { bestW = w; best = a; }
    }
    return best && best.src || "";
  }

  // อ่านสถานะ: MusicKit → mediaSession → <audio>
  function readState() {
    const st = { title: "", artist: "", album: "", art: "", duration: 0, time: NaN, playing: false, source: "" };
    const mk = getMK();
    if (mk) {
      try {
        const item = mk.nowPlayingItem;
        const a = item && (item.attributes || item);
        if (a && (a.name || item.title)) {
          st.title = a.name || item.title || "";
          st.artist = a.artistName || item.artistName || "";
          st.album = a.albumName || item.albumName || "";
          const art = a.artwork || item.artwork;
          st.art = art && art.url ? art.url : (item.artworkURL || "");
          const ms = a.durationInMillis || item.playbackDuration;
          st.duration = mk.currentPlaybackDuration || (ms ? ms / 1000 : 0);
          const t = mk.currentPlaybackTime;
          if (isFinite(t)) st.time = t;
          st.playing = typeof mk.isPlaying === "boolean" ? mk.isPlaying : mk.playbackState === 2;
          st.source = "musickit";
        }
      } catch (e) {}
    }
    const ms = navigator.mediaSession;
    if (!st.title && ms && ms.metadata && ms.metadata.title) {
      const md = ms.metadata;
      st.title = md.title || "";
      st.artist = md.artist || "";
      st.album = md.album || "";
      st.art = largestArtwork(md.artwork);
      st.source = "mediasession";
      st.playing = ms.playbackState === "playing";
    }
    if (!st.art && ms && ms.metadata) st.art = largestArtwork(ms.metadata.artwork);
    const media = getMedia();
    if (media) {
      if (!st.title && media.currentSrc) st.source = st.source || "audio";
      if (!isFinite(st.time) && isFinite(media.currentTime)) st.time = media.currentTime;
      if (!st.duration && isFinite(media.duration)) st.duration = media.duration;
      if (st.source !== "musickit") st.playing = !media.paused && !media.ended;
    }
    if (!isFinite(st.time) && positionState) {
      st.time = positionState.position + (st.playing ? (performance.now() - positionState.at) / 1000 * positionState.rate : 0);
      if (!st.duration) st.duration = positionState.duration;
    }
    if (!isFinite(st.time)) st.time = 0;
    st.art = hiresArt(st.art);
    return st;
  }

  function callAction(name, details) {
    const h = actionHandlers[name];
    if (typeof h === "function") {
      try { h(Object.assign({ action: name }, details || {})); return true; } catch (e) {}
    }
    return false;
  }

  const player = {
    play() {
      const mk = getMK();
      if (mk && mk.nowPlayingItem) { try { return void mk.play(); } catch (e) {} }
      if (callAction("play")) return;
      const m = getMedia(); if (m) m.play().catch(() => {});
    },
    pause() {
      const mk = getMK();
      if (mk && mk.nowPlayingItem) { try { return void mk.pause(); } catch (e) {} }
      if (callAction("pause")) return;
      const m = getMedia(); if (m) m.pause();
    },
    toggle() { (state.playing ? player.pause : player.play)(); },
    next() {
      const mk = getMK();
      if (mk && mk.nowPlayingItem && mk.skipToNextItem) { try { return void mk.skipToNextItem(); } catch (e) {} }
      callAction("nexttrack");
    },
    prev() {
      const mk = getMK();
      if (mk && mk.nowPlayingItem && mk.skipToPreviousItem) { try { return void mk.skipToPreviousItem(); } catch (e) {} }
      callAction("previoustrack");
    },
    seek(t) {
      t = Math.max(0, state.duration ? Math.min(t, state.duration - 0.5) : t);
      clock.set(t);
      const mk = getMK();
      if (mk && mk.nowPlayingItem && mk.seekToTime) {
        try { const r = mk.seekToTime(t); if (r && r.catch) r.catch(() => {}); return; } catch (e) {}
      }
      if (callAction("seekto", { seekTime: t, fastSeek: false })) return;
      const m = getMedia(); if (m) m.currentTime = t;
    },
  };

  // เวลาเดินลื่น: interpolate ระหว่างค่าที่อ่านจาก player
  const clock = {
    base: 0, at: 0, playing: false, lastSample: NaN,
    now() { return this.playing ? this.base + (performance.now() - this.at) / 1000 : this.base; },
    set(t) { this.base = t; this.at = performance.now(); this.lastSample = NaN; },
    sample(t, playing) {
      const pred = this.now();
      if (playing !== this.playing) { this.playing = playing; this.set(t); this.lastSample = t; return; }
      if (t === this.lastSample) return;
      this.lastSample = t;
      const integer = Math.abs(t - Math.round(t)) < 1e-6;
      if (integer) {
        // บาง player ให้ค่าเป็นวินาทีเต็ม — เชื่อเฉพาะเมื่อหลุดช่วง
        if (pred < t - 0.05 || pred > t + 1.05) this.set(t + (playing ? 0.5 : 0));
      } else {
        const d = t - pred;
        if (Math.abs(d) > 0.3) this.set(t);
        else { this.base = pred + d * 0.5; this.at = performance.now(); }
      }
    },
  };

  // ---------------------------------------------------------------- lyrics
  function parseLRC(text) {
    const out = [];
    let offset = 0;
    for (const raw of String(text || "").split(/\r?\n/)) {
      const off = raw.match(/^\[offset:\s*([+-]?\d+)\]/i);
      if (off) { offset = parseInt(off[1], 10) / 1000; continue; }
      const re = /\[(\d{1,3}):(\d{1,2}(?:[.:]\d{1,3})?)\]/g;
      const times = [];
      let m, last = 0;
      while ((m = re.exec(raw))) {
        if (m.index !== last) break;
        times.push(parseInt(m[1], 10) * 60 + parseFloat(m[2].replace(":", ".")));
        last = re.lastIndex;
      }
      if (!times.length) continue;
      const words = raw.slice(last).replace(/<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>/g, "").trim();
      for (const t of times) out.push({ t: Math.max(0, t - offset), text: words });
    }
    out.sort((a, b) => a.t - b.t);
    if (out.length && out[0].t > 4 && out[0].text) out.unshift({ t: 0, text: "" });
    return out;
  }

  let reqSeq = 0;
  const pending = new Map();
  window.addEventListener("message", (e) => {
    if (e.source !== window) return;
    const m = e.data;
    if (!m || m.__amtv !== "bridge") return;
    if (m.type === "fonts-ready") return;
    const p = pending.get(m.id);
    if (p) { pending.delete(m.id); p(m); }
  });
  function ask(type, payload, timeout) {
    return new Promise((resolve) => {
      const id = "amtv" + (++reqSeq) + "_" + Date.now();
      const timer = setTimeout(() => { pending.delete(id); resolve({ ok: false, timeout: true }); }, timeout || 15000);
      pending.set(id, (m) => { clearTimeout(timer); resolve(m); });
      window.postMessage(Object.assign({ __amtv: "page", type, id }, payload), "*");
    });
  }

  // ---------------------------------------------------------------- state
  const state = {
    open: false, key: "", title: "", artist: "", album: "", art: "", duration: 0, playing: false, hasTrack: false,
    lyrics: null, // { kind: 'synced'|'plain'|'none'|'loading', lines: [] }
    showLyrics: true, cur: -2,
  };
  const lyricsCache = new Map();

  // ---------------------------------------------------------------- styles
  const CSS = `
:host { all: initial; }
*, *::before, *::after { box-sizing: border-box; }
.root { position: fixed; inset: 0; pointer-events: none; z-index: 2147483646;
  font-family: ${FONT_STACK}; color: #fff; -webkit-font-smoothing: antialiased; text-rendering: optimizeLegibility;
  --u: min(1vw, 1.7778vh); --ease: cubic-bezier(.22,.8,.2,1); }
button { font: inherit; color: inherit; background: none; border: 0; padding: 0; margin: 0; cursor: pointer; -webkit-tap-highlight-color: transparent; }
svg { display: block; width: 100%; height: 100%; fill: currentColor; }

/* ปุ่มลอย */
.fab { position: fixed; right: 24px; bottom: 24px; pointer-events: auto; display: flex; align-items: center; gap: 10px;
  padding: 12px 20px 12px 16px; border-radius: 999px; font-size: 17px; font-weight: 650; letter-spacing: -.01em;
  background: rgba(28,28,32,.72); backdrop-filter: blur(20px) saturate(1.6); -webkit-backdrop-filter: blur(20px) saturate(1.6);
  border: 1px solid rgba(255,255,255,.14); box-shadow: 0 10px 30px rgba(0,0,0,.35);
  transition: transform .25s var(--ease), background .2s, opacity .3s; }
.fab:hover { transform: translateY(-2px) scale(1.03); background: rgba(250,45,72,.9); }
.fab:focus-visible { outline: 3px solid #fff; outline-offset: 3px; }
.fab svg { width: 22px; height: 22px; }
.fab kbd { font: inherit; font-size: 12px; font-weight: 700; padding: 2px 7px; border-radius: 6px; background: rgba(255,255,255,.16); }
.root.is-open .fab { opacity: 0; pointer-events: none; }

/* overlay */
.ov { position: fixed; inset: 0; pointer-events: auto; background: #08080a; overflow: hidden; opacity: 0; visibility: hidden;
  transition: opacity .55s var(--ease), visibility 0s linear .55s; }
.root.is-open .ov { opacity: 1; visibility: visible; transition: opacity .55s var(--ease), visibility 0s; }
.ov.idle { cursor: none; }

.bg { position: absolute; inset: 0; overflow: hidden; }
.scene { position: absolute; inset: 0; opacity: 0; transition: opacity 1.6s ease; }
.scene.show { opacity: 1; }
.scene img { position: absolute; left: 50%; top: 50%; width: 150vmax; height: 150vmax; margin: -75vmax 0 0 -75vmax; object-fit: cover;
  filter: blur(90px) saturate(1.7) brightness(.72); will-change: transform; }
.scene img.b1 { animation: drift1 52s ease-in-out infinite alternate; }
.scene img.b2 { opacity: .55; mix-blend-mode: screen; animation: drift2 60s ease-in-out infinite alternate; }
@keyframes drift1 { from { transform: scale(1.05) rotate(0deg) translate(-4%, -2%); } to { transform: scale(1.35) rotate(24deg) translate(4%, 3%); } }
@keyframes drift2 { from { transform: scale(1.4) rotate(180deg) translate(6%, 4%); } to { transform: scale(1.1) rotate(140deg) translate(-5%, -4%); } }
.vignette { position: absolute; inset: 0;
  background: radial-gradient(ellipse at 50% 45%, rgba(0,0,0,0) 35%, rgba(0,0,0,.5) 100%),
              linear-gradient(to bottom, rgba(0,0,0,.25), rgba(0,0,0,0) 22%, rgba(0,0,0,0) 70%, rgba(0,0,0,.35)); }

/* แถบบน */
.top { position: absolute; left: 0; right: 0; top: 0; display: flex; align-items: center; justify-content: space-between;
  padding: calc(var(--u)*2.2) calc(var(--u)*3.4); z-index: 3; }
.brand { display: flex; align-items: center; gap: calc(var(--u)*.7); font-size: calc(var(--u)*1.05); font-weight: 650; letter-spacing: -.01em; opacity: .78; }
.brand .logo { width: calc(var(--u)*1.9); height: calc(var(--u)*1.9); border-radius: calc(var(--u)*.45); padding: calc(var(--u)*.35);
  background: linear-gradient(160deg, #fb5c74, #fa233b); box-shadow: 0 2px 10px rgba(0,0,0,.25); }
.top-r { display: flex; align-items: center; gap: calc(var(--u)*1.1); }
.clock { font-size: calc(var(--u)*1.55); font-weight: 600; font-variant-numeric: tabular-nums; letter-spacing: -.01em; opacity: .9; }
.tbtn { width: calc(var(--u)*2.6); height: calc(var(--u)*2.6); padding: calc(var(--u)*.6); border-radius: 50%;
  background: rgba(255,255,255,.1); transition: background .2s, opacity .4s, transform .2s; }
.tbtn:hover, .tbtn:focus-visible { background: rgba(255,255,255,.24); outline: none; transform: scale(1.06); }

.hint { position: absolute; left: 50%; top: calc(var(--u)*2.2); transform: translate(-50%, -140%); z-index: 4;
  display: flex; gap: calc(var(--u)*1.3); align-items: center; padding: calc(var(--u)*.7) calc(var(--u)*1.4);
  border-radius: 999px; background: rgba(20,20,24,.55); backdrop-filter: blur(24px); -webkit-backdrop-filter: blur(24px);
  border: 1px solid rgba(255,255,255,.12); font-size: calc(var(--u)*.9); font-weight: 500; line-height: 1.35; white-space: nowrap;
  opacity: 0; transition: transform .6s var(--ease), opacity .6s; }
.hint.show { transform: translate(-50%, 0); opacity: 1; }
.hint span { opacity: .82; }
.hint b { font-weight: 700; padding: .1em .45em; margin-right: .35em; border-radius: .35em; background: rgba(255,255,255,.16); opacity: 1; }

.toast { position: absolute; left: 50%; bottom: calc(var(--u)*4); transform: translate(-50%, 30%); z-index: 5;
  padding: calc(var(--u)*.85) calc(var(--u)*1.8); border-radius: 999px; background: rgba(20,20,24,.62);
  backdrop-filter: blur(24px); -webkit-backdrop-filter: blur(24px); border: 1px solid rgba(255,255,255,.12);
  font-size: calc(var(--u)*1.2); font-weight: 600; line-height: 1.35; opacity: 0; transition: opacity .35s, transform .45s var(--ease); }
.toast.show { opacity: 1; transform: translate(-50%, 0); }

/* เนื้อหาหลัก */
.stage { position: absolute; inset: 0; display: grid; grid-template-columns: 44% 56%;
  padding: calc(var(--u)*5.2) calc(var(--u)*4) calc(var(--u)*3.6);
  transition: grid-template-columns .9s var(--ease); z-index: 2; }
.ov.nolyrics .stage { grid-template-columns: 100% 0%; }
.left { display: flex; flex-direction: column; align-items: center; justify-content: center; min-width: 0; }
.panel { --art: calc(var(--u)*26.5); width: var(--art); max-width: 100%; transition: width .9s var(--ease); }
.ov.nolyrics .panel { --art: calc(var(--u)*31); }
.art-wrap { position: relative; width: 100%; aspect-ratio: 1; }
.art { position: absolute; inset: 0; border-radius: calc(var(--u)*.9); overflow: hidden; background: rgba(255,255,255,.08);
  box-shadow: 0 calc(var(--u)*1.6) calc(var(--u)*4.5) rgba(0,0,0,.55), 0 calc(var(--u)*.3) calc(var(--u)*.9) rgba(0,0,0,.3);
  transform: scale(1); transition: transform .7s cubic-bezier(.34,1.36,.5,1), box-shadow .7s var(--ease); }
.ov.paused .art { transform: scale(.9); box-shadow: 0 calc(var(--u)*.8) calc(var(--u)*2.4) rgba(0,0,0,.4); }
.art img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; transition: opacity .6s ease; }
.art img.fade { opacity: 0; }
.art .ph { position: absolute; inset: 28%; opacity: .35; }

.info { margin-top: calc(var(--u)*2.1); min-width: 0; }
.title { font-size: calc(var(--u)*2.2); font-weight: 750; letter-spacing: -.022em; line-height: 1.3;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.meta { margin-top: calc(var(--u)*.25); font-size: calc(var(--u)*1.4); font-weight: 500; letter-spacing: -.01em; line-height: 1.35;
  color: rgba(255,255,255,.62); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

.prog { margin-top: calc(var(--u)*1.5); }
.bar { position: relative; height: calc(var(--u)*1.4); display: flex; align-items: center; cursor: pointer; }
.bar .rail { position: relative; width: 100%; height: calc(var(--u)*.42); border-radius: 999px; background: rgba(255,255,255,.22); overflow: hidden;
  transition: height .2s var(--ease); }
.bar:hover .rail { height: calc(var(--u)*.7); }
.bar .fill { position: absolute; left: 0; top: 0; bottom: 0; width: 100%; background: rgba(255,255,255,.88); transform-origin: left center; transform: scaleX(0); }
.times { display: flex; justify-content: space-between; margin-top: calc(var(--u)*.2); font-size: calc(var(--u)*.95); font-weight: 600;
  font-variant-numeric: tabular-nums; color: rgba(255,255,255,.55); letter-spacing: 0; }

.ctrls { margin-top: calc(var(--u)*1.1); display: flex; align-items: center; justify-content: center; gap: calc(var(--u)*3.2);
  position: relative; transition: opacity .5s var(--ease), transform .5s var(--ease); }
.cbtn { width: calc(var(--u)*3.3); height: calc(var(--u)*3.3); padding: calc(var(--u)*.4); border-radius: 50%; opacity: .92;
  transition: transform .18s var(--ease), background .2s, opacity .2s; }
.cbtn.big { width: calc(var(--u)*4.6); height: calc(var(--u)*4.6); padding: calc(var(--u)*.55); }
.cbtn:hover, .cbtn:focus-visible { background: rgba(255,255,255,.14); outline: none; opacity: 1; }
.cbtn:active { transform: scale(.88); }
.cbtn.lyr { position: absolute; right: 0; width: calc(var(--u)*2.7); height: calc(var(--u)*2.7); padding: calc(var(--u)*.55);
  border-radius: calc(var(--u)*.6); opacity: .55; }
.cbtn.lyr.on { opacity: 1; background: rgba(255,255,255,.2); }
.ov.idle .ctrls, .ov.idle .tbtn { opacity: 0; pointer-events: none; }
.ov.idle .ctrls { transform: translateY(calc(var(--u)*.6)); }

/* เนื้อเพลง */
.right { position: relative; min-width: 0; overflow: hidden; transition: opacity .6s var(--ease); }
.ov.nolyrics .right { opacity: 0; pointer-events: none; }
.lyrics { position: absolute; inset: calc(var(--u)*-2) 0 calc(var(--u)*-1) calc(var(--u)*1.5); overflow: hidden;
  -webkit-mask-image: linear-gradient(to bottom, transparent 0, #000 15%, #000 78%, transparent 100%);
  mask-image: linear-gradient(to bottom, transparent 0, #000 15%, #000 78%, transparent 100%); }
.track { position: absolute; left: 0; right: calc(var(--u)*3); top: 0; will-change: transform;
  transition: transform .75s cubic-bezier(.3,.8,.25,1); }
.line { display: block; width: 100%; text-align: left; padding: calc(var(--u)*.85) calc(var(--u)*1.1); margin: 0 0 calc(var(--u)*.35) calc(var(--u)*-1.1);
  border-radius: calc(var(--u)*.9); font-size: calc(var(--u)*3.15); font-weight: 800; line-height: 1.32; letter-spacing: -.018em;
  color: #fff; opacity: .3; filter: blur(calc(var(--d, 0) * var(--u) * .12));
  transform-origin: left center; transform: scale(.97);
  transition: opacity .6s ease, filter .6s ease, transform .75s var(--ease), text-shadow .6s ease, background .2s;
  overflow-wrap: anywhere; }
.line.cur { opacity: 1; filter: none; transform: scale(1); text-shadow: 0 0 calc(var(--u)*1.4) rgba(255,255,255,.42), 0 0 calc(var(--u)*.35) rgba(255,255,255,.25); }
.line.gap { font-size: calc(var(--u)*2.4); letter-spacing: .12em; }
.line.gap.cur { animation: breathe 2.4s ease-in-out infinite; text-shadow: none; }
@keyframes breathe { 0%,100% { opacity: .55; transform: scale(.94); } 50% { opacity: 1; transform: scale(1.02); } }
.ov:not(.idle) .lyrics:hover .line { filter: none; }
.ov:not(.idle) .line:hover { background: rgba(255,255,255,.08); }
.lyrics.plain .line { opacity: .82; filter: none; transform: none; font-size: calc(var(--u)*2.5); font-weight: 700; padding-top: calc(var(--u)*.4); padding-bottom: calc(var(--u)*.4); cursor: default; }
.lyrics.plain .track { transition: transform 1s linear; }
.lyrics.loading .track { opacity: 0; }
.lyrics .status { position: absolute; left: 0; top: 40%; font-size: calc(var(--u)*2.4); font-weight: 800; letter-spacing: .12em; opacity: 0;
  transition: opacity .4s; pointer-events: none; }
.lyrics.loading .status { animation: breathe 2.4s ease-in-out infinite; }
.lyrics:not(.loading) .status { visibility: hidden; }
.credit { position: absolute; right: calc(var(--u)*3); bottom: calc(var(--u)*-2.6); font-size: calc(var(--u)*.75); font-weight: 500; opacity: .35; }

/* ไม่มีเพลง */
.empty { position: absolute; inset: 0; display: none; flex-direction: column; align-items: center; justify-content: center; text-align: center; z-index: 2; gap: calc(var(--u)*1.2); }
.ov.nothing .empty { display: flex; }
.ov.nothing .stage { display: none; }
.empty .big { width: calc(var(--u)*7); height: calc(var(--u)*7); padding: calc(var(--u)*1.6); border-radius: calc(var(--u)*1.6);
  background: linear-gradient(160deg, #fb5c74, #fa233b); box-shadow: 0 calc(var(--u)*1) calc(var(--u)*3) rgba(250,35,59,.35); margin-bottom: calc(var(--u)*1); }
.empty h1 { margin: 0; font-size: calc(var(--u)*3); font-weight: 750; letter-spacing: -.02em; line-height: 1.3; }
.empty p { margin: 0; font-size: calc(var(--u)*1.35); font-weight: 500; line-height: 1.4; color: rgba(255,255,255,.6); }
.ov.nothing .bg .fallback { opacity: 1; }
.bg .fallback { position: absolute; inset: 0; opacity: 0; transition: opacity 1s;
  background: radial-gradient(circle at 20% 20%, #5b1a2a 0, transparent 55%), radial-gradient(circle at 80% 70%, #1d2a5c 0, transparent 55%), #0b0b10; }

@media (prefers-reduced-motion: reduce) {
  .scene img.b1, .scene img.b2 { animation: none; }
}
`;

  // ---------------------------------------------------------------- UI
  let ui = null;

  function buildUI() {
    const host = document.createElement("amtv-root");
    host.setAttribute("data-amtv", "");
    const shadow = host.attachShadow({ mode: "open" });
    try {
      const sheet = new CSSStyleSheet();
      sheet.replaceSync(CSS);
      shadow.adoptedStyleSheets = [sheet];
    } catch (e) {
      $("style", null, shadow, CSS);
    }
    const root = $("div", "root", shadow);

    // ปุ่มลอย
    const fab = $("button", "fab", root);
    fab.type = "button";
    fab.title = "เปิดโหมดทีวี (T)";
    icon(ICONS.tv, fab);
    $("span", null, fab, "TV Mode");
    $("kbd", null, fab, "T");
    fab.addEventListener("click", () => openTV());

    const ov = $("div", "ov nothing", root);
    ov.setAttribute("role", "dialog");
    ov.setAttribute("aria-label", "Apple Music TV Mode");
    const bg = $("div", "bg", ov);
    $("div", "fallback", bg);
    $("div", "vignette", ov);

    // top
    const top = $("div", "top", ov);
    const brand = $("div", "brand", top);
    const logo = $("div", "logo", brand); icon(ICONS.note, logo);
    $("span", null, brand, "Music · TV Mode");
    const topR = $("div", "top-r", top);
    const clockEl = $("div", "clock", topR);
    const btnFull = $("button", "tbtn", topR); btnFull.title = "เต็มจอ (F)"; icon(ICONS.full, btnFull);
    const btnClose = $("button", "tbtn", topR); btnClose.title = "ปิด (Esc)"; icon(ICONS.close, btnClose);

    const hint = $("div", "hint", ov);
    for (const [k, t] of [["Space", "เล่น/หยุด"], ["← →", "±10 วิ"], ["N / P", "เพลงถัดไป/ก่อนหน้า"], ["L", "เนื้อเพลง"], ["F", "เต็มจอ"], ["Esc", "ปิด"]]) {
      const s = $("span", null, hint);
      $("b", null, s, k);
      s.appendChild(document.createTextNode(t));
    }
    const toast = $("div", "toast", ov);

    // stage
    const stage = $("div", "stage", ov);
    const left = $("div", "left", stage);
    const panel = $("div", "panel", left);
    const artWrap = $("div", "art-wrap", panel);
    const art = $("div", "art", artWrap);
    const ph = $("div", "ph", art); icon(ICONS.note, ph);
    const artImg = $("img", null, art);
    artImg.alt = "";
    artImg.decoding = "async";
    artImg.referrerPolicy = "no-referrer";
    const info = $("div", "info", panel);
    const title = $("div", "title", info);
    const meta = $("div", "meta", info);
    const prog = $("div", "prog", panel);
    const bar = $("div", "bar", prog);
    bar.setAttribute("role", "slider");
    bar.setAttribute("aria-label", "ตำแหน่งเพลง");
    const rail = $("div", "rail", bar);
    const fill = $("div", "fill", rail);
    const times = $("div", "times", prog);
    const tEl = $("span", null, times, "0:00");
    const rEl = $("span", null, times, "-0:00");
    const ctrls = $("div", "ctrls", panel);
    const bPrev = $("button", "cbtn", ctrls); bPrev.title = "ก่อนหน้า (P)"; icon(ICONS.prev, bPrev);
    const bPlay = $("button", "cbtn big", ctrls); bPlay.title = "เล่น/หยุด (Space)";
    const svgPlay = icon(ICONS.play, bPlay), svgPause = icon(ICONS.pause, bPlay);
    const bNext = $("button", "cbtn", ctrls); bNext.title = "ถัดไป (N)"; icon(ICONS.next, bNext);
    const bLyr = $("button", "cbtn lyr on", ctrls); bLyr.title = "เนื้อเพลง (L)"; icon(ICONS.lyrics, bLyr);

    const right = $("div", "right", stage);
    const lyr = $("div", "lyrics loading", right);
    const trk = $("div", "track", lyr);
    $("div", "status", lyr, "• • •");
    const credit = $("div", "credit", right);

    // empty
    const empty = $("div", "empty", ov);
    const eb = $("div", "big", empty); icon(ICONS.note, eb);
    $("h1", null, empty, "ยังไม่มีเพลงที่กำลังเล่น");
    $("p", null, empty, "เลือกเพลงใน Apple Music แล้วกดเล่น — กด Esc หรือ T เพื่อกลับไปหน้าเว็บ");

    for (const b of [bPrev, bPlay, bNext, bLyr, btnFull, btnClose]) b.type = "button";
    bPrev.addEventListener("click", () => doPrev());
    bNext.addEventListener("click", () => player.next());
    bPlay.addEventListener("click", () => player.toggle());
    bLyr.addEventListener("click", () => toggleLyrics());
    btnFull.addEventListener("click", () => toggleFullscreen());
    btnClose.addEventListener("click", () => closeTV());
    bar.addEventListener("click", (e) => {
      const r = rail.getBoundingClientRect();
      const f = Math.min(1, Math.max(0, (e.clientX - r.left) / r.width));
      if (state.duration) player.seek(f * state.duration);
    });
    ov.addEventListener("dblclick", (e) => {
      const el = e.composedPath()[0];
      if (el && el.closest && el.closest("button, .line, .bar")) return;
      toggleFullscreen();
    });
    ov.addEventListener("mousemove", wake, { passive: true });
    ov.addEventListener("pointerdown", wake, { passive: true });
    ov.addEventListener("wheel", wake, { passive: true });

    (document.documentElement).appendChild(host);
    ui = { host, root, fab, ov, bg, clockEl, hint, toast, artImg, ph, title, meta, fill, tEl, rEl, svgPlay, svgPause,
      bLyr, lyr, trk, credit, lines: [], scenes: [] };
    svgPause.style.display = "none";
  }

  // ---------------------------------------------------------------- render
  function setArtwork(url) {
    if (!ui) return;
    const img = ui.artImg;
    if (!url) { img.removeAttribute("src"); img.style.visibility = "hidden"; ui.ph.style.display = ""; }
    else if (img.getAttribute("src") !== url) {
      const pre = new Image();
      pre.referrerPolicy = "no-referrer";
      pre.onload = pre.onerror = () => {
        if (state.art !== url) return;
        img.classList.add("fade");
        setTimeout(() => {
          img.src = url;
          img.style.visibility = "";
          ui.ph.style.display = "none";
          requestAnimationFrame(() => img.classList.remove("fade"));
        }, img.getAttribute("src") ? 250 : 0);
      };
      pre.src = url;
    }
    // พื้นหลัง: scene ใหม่ crossfade ทับ scene เก่า
    const last = ui.scenes[ui.scenes.length - 1];
    if (last && last.dataset.src === (url || "")) return;
    if (!url) { for (const s of ui.scenes) s.classList.remove("show"); return; }
    const scene = $("div", "scene", null);
    scene.dataset.src = url;
    for (const c of ["b1", "b2"]) {
      const im = $("img", c, scene);
      im.alt = "";
      im.referrerPolicy = "no-referrer";
      im.decoding = "async";
      im.src = url;
    }
    ui.bg.appendChild(scene);
    ui.scenes.push(scene);
    const firstImg = scene.firstChild;
    const show = () => requestAnimationFrame(() => requestAnimationFrame(() => {
      scene.classList.add("show");
      const old = ui.scenes.slice(0, -1);
      setTimeout(() => { for (const s of old) { s.remove(); ui.scenes.splice(ui.scenes.indexOf(s), 1); } }, 1800);
    }));
    if (firstImg.complete) show(); else { firstImg.onload = show; firstImg.onerror = show; }
  }

  function renderTrackInfo() {
    if (!ui) return;
    ui.title.textContent = state.title;
    ui.meta.textContent = [state.artist, state.album].filter(Boolean).join(" — ");
    setArtwork(state.art);
    ui.ov.classList.toggle("nothing", !state.hasTrack);
  }

  function renderPlaying() {
    if (!ui) return;
    ui.ov.classList.toggle("paused", !state.playing);
    ui.svgPlay.style.display = state.playing ? "none" : "";
    ui.svgPause.style.display = state.playing ? "" : "none";
  }

  function lyricsVisible() {
    const L = state.lyrics;
    return state.showLyrics && L && (L.kind === "loading" || ((L.kind === "synced" || L.kind === "plain") && L.lines.length));
  }

  function renderLyrics() {
    if (!ui) return;
    const L = state.lyrics || { kind: "none", lines: [] };
    ui.trk.replaceChildren();
    ui.lines = [];
    state.cur = -2;
    ui.lyr.classList.toggle("loading", L.kind === "loading");
    ui.lyr.classList.toggle("plain", L.kind === "plain");
    ui.credit.textContent = L.kind === "synced" || L.kind === "plain" ? "เนื้อเพลงจาก LRCLIB" : "";
    if (L.kind === "synced" || L.kind === "plain") {
      L.lines.forEach((ln, i) => {
        const el = $("div", "line" + (ln.text ? "" : " gap"), ui.trk, ln.text || "• • •");
        if (L.kind === "synced") {
          el.addEventListener("click", () => { player.seek(ln.t); if (!state.playing) player.play(); });
          el.style.cursor = "pointer";
        }
        el.style.setProperty("--d", "3");
        ui.lines.push(el);
      });
      ui.trk.style.transform = "translateY(" + Math.round(ui.lyr.clientHeight * 0.4) + "px)";
    }
    ui.ov.classList.toggle("nolyrics", !lyricsVisible());
    ui.bLyr.classList.toggle("on", state.showLyrics);
  }

  function lineIndexAt(lines, t) {
    let lo = 0, hi = lines.length - 1, ans = -1;
    while (lo <= hi) {
      const mid = (lo + hi) >> 1;
      if (lines[mid].t <= t) { ans = mid; lo = mid + 1; } else hi = mid - 1;
    }
    return ans;
  }

  function updateLyricsPosition(t, force) {
    const L = state.lyrics;
    if (!ui || !L || !ui.lines.length || !lyricsVisible()) return;
    const H = ui.lyr.clientHeight;
    if (L.kind === "plain") {
      const p = state.duration ? Math.min(1, Math.max(0, t / state.duration)) : 0;
      const total = ui.trk.scrollHeight;
      const y = H * 0.35 - p * Math.max(0, total - H * 0.3);
      ui.trk.style.transform = "translateY(" + Math.round(y) + "px)";
      return;
    }
    const idx = lineIndexAt(L.lines, t + LYRIC_LEAD);
    if (idx === state.cur && !force) return;
    state.cur = idx;
    ui.lines.forEach((el, i) => {
      const d = idx < 0 ? i + 1 : Math.abs(i - idx);
      el.classList.toggle("cur", i === idx);
      el.style.setProperty("--d", String(Math.min(d, 5)));
    });
    const target = ui.lines[Math.max(0, idx)];
    if (target) {
      const y = H * 0.4 - (target.offsetTop + target.offsetHeight / 2);
      ui.trk.style.transform = "translateY(" + Math.round(y) + "px)";
    }
  }

  let lastProgW = -1;
  function renderTime(t) {
    if (!ui) return;
    const d = state.duration || 0;
    const p = d ? Math.min(1, Math.max(0, t / d)) : 0;
    if (Math.abs(p - lastProgW) > 0.0002) { ui.fill.style.transform = "scaleX(" + p.toFixed(5) + ")"; lastProgW = p; }
    const a = fmt(t), b = "-" + fmt(Math.max(0, d - t));
    if (ui.tEl.textContent !== a) ui.tEl.textContent = a;
    if (ui.rEl.textContent !== b) ui.rEl.textContent = b;
  }

  function renderClock() {
    if (!ui) return;
    try { ui.clockEl.textContent = new Date().toLocaleTimeString("th-TH", { hour: "2-digit", minute: "2-digit" }); }
    catch (e) { ui.clockEl.textContent = new Date().toTimeString().slice(0, 5); }
  }

  // ---------------------------------------------------------------- lyrics fetch
  function trackKey(s) { return [s.title, s.artist, s.album].join("|"); }

  async function loadLyrics(s, key) {
    if (lyricsCache.has(key)) { applyLyrics(key, lyricsCache.get(key)); return; }
    state.lyrics = { kind: "loading", lines: [] };
    renderLyrics();
    const res = await ask("lyrics-req", { track: { title: s.title, artist: s.artist, album: s.album, duration: s.duration } }, 20000);
    let L;
    const r = res && res.result;
    if (r && r.synced) L = { kind: "synced", lines: parseLRC(r.synced) };
    else if (r && r.plain) L = { kind: "plain", lines: r.plain.split(/\r?\n/).map((x) => ({ t: 0, text: x.trim() })) };
    else L = { kind: "none", lines: [], instrumental: !!(r && r.instrumental) };
    if (L.kind === "synced" && !L.lines.length) L.kind = "none";
    if (res && res.ok !== false) lyricsCache.set(key, L);
    applyLyrics(key, L);
  }

  function applyLyrics(key, L) {
    if (key !== state.key) return;
    state.lyrics = L;
    renderLyrics();
    requestAnimationFrame(() => updateLyricsPosition(clock.now(), true));
    if (state.open && L.kind === "none" && state.showLyrics) toast(L.instrumental ? "เพลงบรรเลง — ไม่มีเนื้อร้อง" : "ไม่พบเนื้อเพลงสำหรับเพลงนี้");
  }

  // ---------------------------------------------------------------- poll
  function poll() {
    let s;
    try { s = readState(); } catch (e) { return; }
    const hasTrack = !!s.title;
    const key = hasTrack ? trackKey(s) : "";
    if (key !== state.key || s.art !== state.art) {
      const changed = key !== state.key;
      Object.assign(state, { key, title: s.title, artist: s.artist, album: s.album, art: s.art, hasTrack });
      if (changed) {
        lastProgW = -1;
        state.lyrics = null;
        clock.set(s.time);
        if (hasTrack) loadLyrics(s, key);
        else renderLyrics();
      }
      renderTrackInfo();
    }
    if (s.duration && Math.abs(s.duration - state.duration) > 0.5) {
      state.duration = s.duration;
      if (state.lyrics && state.lyrics.kind === "loading") { /* duration มาทีหลัง — ใช้ค่าเดิม */ }
    }
    if (s.playing !== state.playing) { state.playing = s.playing; renderPlaying(); }
    clock.sample(s.time, s.playing);
  }

  let pollTimer = 0;
  function schedulePoll() {
    clearInterval(pollTimer);
    pollTimer = setInterval(poll, state.open ? 250 : 1000);
  }

  let raf = 0;
  function frame() {
    raf = 0;
    if (!state.open) return;
    const t = Math.min(clock.now(), state.duration || Infinity);
    renderTime(t);
    updateLyricsPosition(t);
    raf = requestAnimationFrame(frame);
  }

  // ---------------------------------------------------------------- interactions
  let idleTimer = 0;
  function wake() {
    if (!ui) return;
    ui.ov.classList.remove("idle");
    clearTimeout(idleTimer);
    idleTimer = setTimeout(() => ui.ov.classList.add("idle"), IDLE_MS);
  }

  let toastTimer = 0;
  function toast(msg) {
    if (!ui) return;
    ui.toast.textContent = msg;
    ui.toast.classList.add("show");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => ui.toast.classList.remove("show"), 2200);
  }

  let hintTimer = 0;
  function showHint() {
    ui.hint.classList.add("show");
    clearTimeout(hintTimer);
    hintTimer = setTimeout(() => ui.hint.classList.remove("show"), 4500);
  }

  let weFullscreened = false;
  function toggleFullscreen() {
    try {
      if (document.fullscreenElement) { document.exitFullscreen().catch(() => {}); weFullscreened = false; }
      else { document.documentElement.requestFullscreen({ navigationUI: "hide" }).then(() => { weFullscreened = true; }, () => toast("เบราว์เซอร์ไม่อนุญาตเต็มจอ")); }
    } catch (e) {}
  }

  function toggleLyrics() {
    state.showLyrics = !state.showLyrics;
    renderLyrics();
    requestAnimationFrame(() => updateLyricsPosition(clock.now(), true));
    toast(state.showLyrics ? "แสดงเนื้อเพลง" : "ซ่อนเนื้อเพลง");
  }

  function doPrev() {
    if (clock.now() > 3) player.seek(0);
    else player.prev();
  }

  function openTV() {
    if (!ui) return;
    if (state.open) return;
    state.open = true;
    poll();
    ui.root.classList.add("is-open");
    document.documentElement.setAttribute("data-amtv-open", "");
    renderTrackInfo();
    renderPlaying();
    renderLyrics();
    renderClock();
    schedulePoll();
    wake();
    showHint();
    if (!raf) raf = requestAnimationFrame(frame);
    requestAnimationFrame(() => updateLyricsPosition(clock.now(), true));
  }

  function closeTV() {
    if (!state.open) return;
    state.open = false;
    ui.root.classList.remove("is-open");
    document.documentElement.removeAttribute("data-amtv-open");
    if (document.fullscreenElement && weFullscreened) { document.exitFullscreen().catch(() => {}); }
    weFullscreened = false;
    schedulePoll();
  }

  // ---------------------------------------------------------------- zoom (หน้าเว็บปกติ)
  function readZoom() {
    try { const z = parseInt(localStorage.getItem(ZOOM_KEY), 10); return ZOOMS.includes(z) ? z : 100; } catch (e) { return 100; }
  }
  async function applyZoom(z, announce) {
    try { localStorage.setItem(ZOOM_KEY, String(z)); } catch (e) {}
    const res = await ask("zoom", { factor: z / 100 }, 1500);
    if (res && res.ok) document.documentElement.removeAttribute("data-amtv-zoom");
    else if (z === 100) document.documentElement.removeAttribute("data-amtv-zoom");
    else document.documentElement.setAttribute("data-amtv-zoom", String(z)); // fallback: CSS zoom ใน skin.css
    if (announce) pageToast("ขนาดหน้า " + z + "%");
  }
  let pageToastTimer = 0;
  function pageToast(msg) {
    if (!ui) return;
    if (state.open) return toast(msg);
    const fab = ui.fab;
    const span = fab.querySelector("span");
    span.textContent = msg;
    clearTimeout(pageToastTimer);
    pageToastTimer = setTimeout(() => { span.textContent = "TV Mode"; }, 1600);
  }

  // ---------------------------------------------------------------- keyboard
  function onKey(e) {
    if (e.defaultPrevented && !state.open) return;
    const path = e.composedPath ? e.composedPath() : [];
    const target = path[0] || e.target;
    if (isEditable(target) || isEditable(document.activeElement)) return;
    if (e.ctrlKey || e.metaKey || e.altKey) return;
    const k = e.key;
    const lower = k && k.length === 1 ? k.toLowerCase() : k;
    const eat = () => { e.preventDefault(); e.stopPropagation(); e.stopImmediatePropagation(); };

    if (!state.open) {
      if (e.repeat) return;
      if (lower === "t") { eat(); openTV(); }
      else if (lower === "z") { eat(); const cur = readZoom(); applyZoom(ZOOMS[(ZOOMS.indexOf(cur) + 1) % ZOOMS.length], true); }
      return;
    }

    // ถ้าโฟกัสอยู่บนปุ่มใน overlay ให้ Enter/Space กดปุ่มนั้นตามปกติ
    const onButton = target && target.tagName === "BUTTON" && ui && ui.host.shadowRoot.contains(target);
    wake();
    switch (lower) {
      case " ": case "Enter": case "k":
        if (onButton && lower !== "k") return;
        eat(); if (!e.repeat) player.toggle(); break;
      case "ArrowLeft":
        eat(); if (e.shiftKey) { if (!e.repeat) doPrev(); } else player.seek(clock.now() - 10); break;
      case "ArrowRight":
        eat(); if (e.shiftKey) { if (!e.repeat) player.next(); } else player.seek(clock.now() + 10); break;
      case "n": eat(); if (!e.repeat) player.next(); break;
      case "p": eat(); if (!e.repeat) doPrev(); break;
      case "l": eat(); if (!e.repeat) toggleLyrics(); break;
      case "f": eat(); if (!e.repeat) toggleFullscreen(); break;
      case "Escape": case "t": case "Backspace": eat(); closeTV(); break;
      case "MediaPlayPause": eat(); player.toggle(); break;
      case "MediaPlay": eat(); player.play(); break;
      case "MediaPause": eat(); player.pause(); break;
      case "MediaTrackNext": eat(); player.next(); break;
      case "MediaTrackPrevious": eat(); doPrev(); break;
      case "MediaStop": eat(); player.pause(); break;
      case "ArrowUp": case "ArrowDown": case "Tab": break;
      default:
        // กันไม่ให้คีย์ลัดของเว็บ Apple ทำงานซ้อนตอนเปิดโหมดทีวี
        if (k && k.length === 1) eat();
    }
  }

  // ---------------------------------------------------------------- init
  function init() {
    try {
      buildUI();
    } catch (e) {
      console.warn("[AMTV] UI build failed", e);
      return;
    }
    document.documentElement.setAttribute("data-amtv-skin", "");
    window.addEventListener("keydown", onKey, true);
    document.addEventListener("fullscreenchange", () => { if (!document.fullscreenElement) weFullscreened = false; });
    setInterval(() => { if (state.open) renderClock(); }, 10000);
    schedulePoll();
    poll();
    const z = readZoom();
    if (z !== 100) applyZoom(z, false);
    // ถ้า Apple เปลี่ยนหน้า (SPA) แล้ว host หลุด ให้ใส่กลับ
    setInterval(() => { if (ui && !ui.host.isConnected) document.documentElement.appendChild(ui.host); }, 2000);
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init, { once: true });
  else init();

  // สำหรับทดสอบ
  window.__amtv = { open: openTV, close: closeTV, state, parseLRC, hiresArt, poll };
})();
