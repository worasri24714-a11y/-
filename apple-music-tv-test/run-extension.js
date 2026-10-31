// โหลด extension จริง (unpacked) แล้ว route https://music.apple.com/* ไปที่หน้า mock (ไม่มี mock-bridge)
// ตรวจว่า bridge.js โหลดฟอนต์จาก chrome.runtime.getURL ได้, skin.css ถูกใส่, page.js ↔ bridge ↔ background ทำงาน
const { chromium } = require('playwright');
const fs = require('fs'); const path = require('path'); const os = require('os');
const EXT = path.resolve(__dirname, '..', 'apple-music-tv');
const OUT = process.argv[2] || path.join(__dirname, 'shots');
let failures = 0;
const check = (c, m) => { console.log((c ? 'PASS ' : 'FAIL ') + m); if (!c) failures++; };
(async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'amtv-'));
  const ctx = await chromium.launchPersistentContext(dir, {
    channel: 'chromium', headless: true, viewport: { width: 1920, height: 1080 }, locale: 'th-TH',
    args: [`--disable-extensions-except=${EXT}`, `--load-extension=${EXT}`],
  });
  // LRCLIB จำลอง (ถ้า route ถึง service worker ได้)
  let lrcHits = 0;
  await ctx.route('https://lrclib.net/**', (route) => {
    lrcHits++;
    const u = new URL(route.request().url());
    if (u.pathname === '/api/get') return route.fulfill({ status: 404, body: '{}' });
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([
      { id: 1, duration: 300, syncedLyrics: '[00:01.00]wrong song' },
      { id: 2, duration: 213.6, syncedLyrics: '[00:01.00]ทดสอบจาก background\n[00:05.00]Line from LRCLIB mock' },
    ]) });
  });
  const html = fs.readFileSync(path.join(__dirname, 'mock.html'), 'utf8');
  await ctx.route('https://music.apple.com/**', (r) => r.fulfill({ status: 200, contentType: 'text/html; charset=utf-8', body: html }));
  let sw = ctx.serviceWorkers()[0] || await ctx.waitForEvent('serviceworker', { timeout: 10000 }).catch(() => null);
  check(!!sw, 'service worker started');
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  await page.goto('https://music.apple.com/th/browse?t=3');
  await page.waitForFunction(() => document.documentElement.getAttribute('data-amtv-fonts'), null, { timeout: 10000 }).catch(() => {});
  check((await page.evaluate(() => document.documentElement.getAttribute('data-amtv-fonts'))) === 'ready', 'bridge.js loaded fonts via chrome.runtime.getURL');
  check(await page.evaluate(() => document.fonts.check('700 40px "AMTV Sans"', 'ทดสอบ')), 'AMTV Sans usable in page');
  check(await page.evaluate(() => document.documentElement.hasAttribute('data-amtv-skin')), 'page.js ran (skin attribute)');
  check(await page.evaluate(() => getComputedStyle(document.body).backgroundColor === 'rgb(13, 13, 16)'), 'skin.css applied (dark bg)');
  check(await page.evaluate(() => getComputedStyle(document.querySelector('h1')).fontFamily.includes('AMTV Sans')), 'skin.css font-family applied');
  await page.screenshot({ path: path.join(OUT, '7-ext-normal-page.png') });
  await page.keyboard.press('t');
  await page.waitForTimeout(4000);
  const L = await page.evaluate(() => window.__amtv && window.__amtv.state.lyrics);
  console.log('lyrics state from real background:', L && L.kind, 'lrclib hits:', lrcHits);
  check(L && L.kind !== 'loading', 'background answered lyrics-req through bridge');
  if (lrcHits) check(L.kind === 'synced' && L.lines.some((x) => x.text === 'ทดสอบจาก background'), 'background picked LRCLIB result with closest duration');
  await page.screenshot({ path: path.join(OUT, '8-ext-overlay.png') });
  // Z (zoom) ผ่าน chrome.tabs.setZoom
  await page.keyboard.press('Escape');
  await page.keyboard.press('z');
  await page.waitForTimeout(800);
  check((await page.evaluate(() => localStorage.getItem('amtv:zoom'))) === '115', 'Z stores zoom 115 in localStorage');
  const zoomedByTabs = await page.evaluate(() => !document.documentElement.hasAttribute('data-amtv-zoom'));
  console.log('zoom via chrome.tabs.setZoom:', zoomedByTabs, ' devicePixelRatio:', await page.evaluate(() => devicePixelRatio));
  for (let i = 0; i < 3; i++) await page.keyboard.press('z');
  check((await page.evaluate(() => localStorage.getItem('amtv:zoom'))) === '100', 'Z cycles back to 100');
  check(errors.length === 0, 'no pageerror' + (errors.length ? ': ' + errors.join(' | ') : ''));
  await ctx.close();
  console.log(failures ? `\n${failures} FAILED` : '\nALL PASSED');
  process.exit(failures ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(1); });
