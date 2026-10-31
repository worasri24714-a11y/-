// Apple Music TV Mode — content script (ISOLATED world)
// 1) โหลดฟอนต์ที่ bundle มาจาก bytes (เลี่ยง CSP font-src และใช้ได้ใน Shadow DOM)
// 2) relay ข้อความระหว่าง page.js (MAIN world) กับ background
"use strict";

(() => {
  const FAMILY = "AMTV Sans";
  const FONTS = [
    { file: "fonts/inter-latin.woff2",
      range: "U+0000-00FF,U+0131,U+0152-0153,U+02BB-02BC,U+02C6,U+02DA,U+02DC,U+0304,U+0308,U+0329,U+2000-206F,U+20AC,U+2122,U+2191,U+2193,U+2212,U+2215,U+FEFF,U+FFFD" },
    { file: "fonts/inter-latin-ext.woff2",
      range: "U+0100-02BA,U+02BD-02C5,U+02C7-02CC,U+02CE-02D7,U+02DD-02FF,U+0304,U+0308,U+0329,U+1D00-1DBF,U+1E00-1E9F,U+1EF2-1EFF,U+2020,U+20A0-20AB,U+20AD-20C0,U+2113,U+2C60-2C7F,U+A720-A7FF" },
    { file: "fonts/anuphan-thai.woff2",
      range: "U+02D7,U+0303,U+0331,U+0E01-0E5B,U+200C-200D,U+25CC" },
  ];

  async function loadFonts() {
    const results = await Promise.allSettled(FONTS.map(async (f) => {
      const res = await fetch(chrome.runtime.getURL(f.file));
      const buf = await res.arrayBuffer();
      const face = new FontFace(FAMILY, buf, { weight: "100 900", style: "normal", unicodeRange: f.range, display: "swap" });
      await face.load();
      document.fonts.add(face);
    }));
    const ok = results.every((r) => r.status === "fulfilled");
    document.documentElement.setAttribute("data-amtv-fonts", ok ? "ready" : "partial");
    window.postMessage({ __amtv: "bridge", type: "fonts-ready", ok }, "*");
  }
  loadFonts().catch(() => {});

  window.addEventListener("message", (e) => {
    if (e.source !== window) return;
    const m = e.data;
    if (!m || m.__amtv !== "page") return;
    if (m.type === "lyrics-req" || m.type === "zoom") {
      const { __amtv, id, ...payload } = m;
      try {
        chrome.runtime.sendMessage(payload, (res) => {
          const err = chrome.runtime.lastError;
          window.postMessage({ __amtv: "bridge", type: m.type.replace("-req", "") + "-res", id,
            ok: !err && !!(res && res.ok), result: res ? res.result : null }, "*");
        });
      } catch (err) {
        // extension ถูก reload — แจ้งกลับว่าไม่สำเร็จ
        window.postMessage({ __amtv: "bridge", type: m.type.replace("-req", "") + "-res", id, ok: false, result: null }, "*");
      }
    }
  });
})();
