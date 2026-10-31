// Apple Music TV Mode — service worker
// ดึงเนื้อเพลงจาก LRCLIB (หน้าเว็บ music.apple.com fetch เองไม่ได้เพราะ CSP)
"use strict";

const API = "https://lrclib.net/api";
const HEADERS = { "Lrclib-Client": "Apple Music TV Mode 1.0 (browser extension)" };
const CACHE_PREFIX = "lrc:";
const CACHE_MAX = 300;
const mem = new Map();

function clean(s) {
  return String(s || "")
    .replace(/\s*[([](?:feat\.?|ft\.?|featuring|with)\s[^)\]]*[)\]]/gi, "")
    .replace(/\s+-\s+(?:Single|EP)\s*$/i, "")
    .replace(/\s*[([](?:Single|EP)[)\]]\s*$/i, "")
    .replace(/\s+/g, " ")
    .trim();
}

// ตัดส่วนเสริมอย่าง "(Remastered 2011)" / "- Live" ไว้ใช้เป็นตัวเลือกสำรอง
function stripExtras(s) {
  return s
    .replace(/\s*[([][^)\]]*(?:remaster|version|edit|mix|live|mono|stereo|deluxe|acoustic)[^)\]]*[)\]]/gi, "")
    .replace(/\s+-\s+.*(?:remaster|version|edit|live|mix).*$/i, "")
    .trim();
}

function firstArtist(s) {
  return s.split(/\s*(?:,|&|×|\bx\b|\band\b|และ)\s*/i)[0].trim();
}

function key(t) {
  return CACHE_PREFIX + [t.title, t.artist, t.album, Math.round(t.duration || 0)].join("␟").toLowerCase();
}

async function getJSON(path, params) {
  const url = new URL(API + path);
  for (const [k, v] of Object.entries(params)) if (v !== undefined && v !== "") url.searchParams.set(k, v);
  const res = await fetch(url.toString(), { headers: HEADERS });
  if (res.status === 404) return null;
  if (!res.ok) throw new Error("LRCLIB HTTP " + res.status);
  return res.json();
}

function toResult(rec) {
  if (!rec) return null;
  if (!rec.syncedLyrics && !rec.plainLyrics && !rec.instrumental) return null;
  return {
    synced: rec.syncedLyrics || null,
    plain: rec.plainLyrics || null,
    instrumental: !!rec.instrumental,
    source: "LRCLIB",
    id: rec.id,
  };
}

function pickBest(list, duration) {
  if (!Array.isArray(list) || !list.length) return null;
  const d = duration || 0;
  let best = null;
  let bestScore = Infinity;
  for (const r of list) {
    if (!r.syncedLyrics && !r.plainLyrics) continue;
    const diff = d && r.duration ? Math.abs(r.duration - d) : 5;
    if (d && diff > 20) continue;
    const score = diff + (r.syncedLyrics ? 0 : 30);
    if (score < bestScore) { bestScore = score; best = r; }
  }
  return best;
}

async function lookup(t) {
  const title = clean(t.title);
  const artist = clean(t.artist);
  const album = clean(t.album);
  const duration = t.duration ? Math.round(t.duration) : undefined;
  if (!title) return null;

  // 1) /get แบบตรงตัว
  let plainFallback = null;
  if (artist) {
    try {
      const rec = await getJSON("/get", { track_name: title, artist_name: artist, album_name: album, duration });
      const r = toResult(rec);
      if (r && r.synced) return r;
      if (r) plainFallback = r;
    } catch (e) { /* ลองค้นต่อ */ }
  }

  // 2) /search หลายรูปแบบ เลือกตัวที่มี synced และ duration ใกล้สุด
  const tries = [];
  const add = (p) => { const k = JSON.stringify(p); if (!tries.some((x) => JSON.stringify(x) === k)) tries.push(p); };
  add({ track_name: title, artist_name: artist });
  add({ track_name: stripExtras(title), artist_name: firstArtist(artist) });
  add({ q: `${stripExtras(title)} ${firstArtist(artist)}`.trim() });
  add({ track_name: stripExtras(title) });

  let fallback = null;
  for (const p of tries) {
    if (!p.track_name && !p.q) continue;
    try {
      const list = await getJSON("/search", p);
      const best = pickBest(list, duration);
      if (best && best.syncedLyrics) return toResult(best);
      if (best && !fallback) fallback = best;
    } catch (e) { /* ข้าม */ }
  }
  return toResult(fallback) || plainFallback;
}

async function cacheGet(k) {
  if (mem.has(k)) return mem.get(k);
  try {
    const o = await chrome.storage.local.get(k);
    if (o && k in o) { mem.set(k, o[k].v); return o[k].v; }
  } catch (e) { /* ไม่มี storage */ }
  return undefined;
}

async function cacheSet(k, v) {
  mem.set(k, v);
  try {
    await chrome.storage.local.set({ [k]: { v, at: Date.now() } });
    const all = await chrome.storage.local.get(null);
    const keys = Object.keys(all).filter((x) => x.startsWith(CACHE_PREFIX));
    if (keys.length > CACHE_MAX) {
      keys.sort((a, b) => all[a].at - all[b].at);
      await chrome.storage.local.remove(keys.slice(0, keys.length - CACHE_MAX));
    }
  } catch (e) { /* ไม่เป็นไร */ }
}

const inflight = new Map();

async function getLyrics(t) {
  const k = key(t);
  const hit = await cacheGet(k);
  if (hit !== undefined) return hit;
  if (inflight.has(k)) return inflight.get(k);
  const p = (async () => {
    let r = null;
    let failed = false;
    try { r = await lookup(t); } catch (e) { failed = true; }
    // ไม่ cache กรณี error เครือข่าย จะได้ลองใหม่ครั้งหน้า
    if (!failed) await cacheSet(k, r);
    return r;
  })();
  inflight.set(k, p);
  try { return await p; } finally { inflight.delete(k); }
}

if (typeof chrome !== "undefined" && chrome.runtime && chrome.runtime.onMessage) {
  chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
    if (!msg || typeof msg !== "object") return false;
    if (msg.type === "lyrics-req" && msg.track) {
      getLyrics(msg.track).then(
        (result) => sendResponse({ ok: true, result }),
        (e) => sendResponse({ ok: false, error: String(e && e.message || e) })
      );
      return true;
    }
    if (msg.type === "zoom" && sender.tab && typeof msg.factor === "number") {
      chrome.tabs.setZoom(sender.tab.id, Math.min(3, Math.max(0.5, msg.factor))).then(
        () => sendResponse({ ok: true }),
        () => sendResponse({ ok: false })
      );
      return true;
    }
    return false;
  });
}

// สำหรับทดสอบใน Node
if (typeof module !== "undefined") module.exports = { clean, stripExtras, firstArtist, pickBest, lookup, getLyrics };
