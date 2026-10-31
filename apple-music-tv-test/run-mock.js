// ทดสอบ page.js กับหน้า mock (fake MusicKit + mock bridge) และ screenshot 1920x1080
const { chromium } = require('playwright');
const http = require('http');
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const OUT = process.argv[2] || path.join(__dirname, 'shots');
fs.mkdirSync(OUT, { recursive: true });
const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.woff2': 'font/woff2' };

const server = http.createServer((req, res) => {
  const u = new URL(req.url, 'http://x');
  let p = u.pathname === '/' ? '/mock.html' : u.pathname;
  const file = p.startsWith('/ext/') ? path.join(ROOT, 'apple-music-tv', p.slice(5)) : path.join(__dirname, p);
  fs.readFile(file, (err, buf) => {
    if (err) { res.writeHead(404); return res.end(); }
    res.writeHead(200, { 'content-type': TYPES[path.extname(file)] || 'application/octet-stream' });
    res.end(buf);
  });
});

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let failures = 0;
const check = (cond, msg) => { console.log((cond ? 'PASS ' : 'FAIL ') + msg); if (!cond) failures++; };

(async () => {
  await new Promise((r) => server.listen(0, r));
  const base = 'http://127.0.0.1:' + server.address().port;
  const browser = await chromium.launch();

  async function newPage(query, size) {
    const ctx = await browser.newContext({ viewport: size || { width: 1920, height: 1080 }, locale: 'th-TH' });
    const page = await ctx.newPage();
    const errors = [];
    page.on('pageerror', (e) => errors.push(String(e)));
    page.on('console', (m) => { if (m.type() === 'error') errors.push('console: ' + m.text()); });
    await page.addInitScript({ path: path.join(ROOT, 'apple-music-tv', 'page.js') });
    await page.addInitScript({ path: path.join(__dirname, 'mock-bridge.js') });
    await page.goto(base + '/mock.html' + (query || ''));
    await page.waitForFunction(() => document.documentElement.getAttribute('data-amtv-fonts') === 'ready');
    await page.waitForFunction(() => !!document.querySelector('amtv-root'));
    return { page, errors, ctx };
  }
  const isOpen = (page) => page.evaluate(() => window.__amtv.state.open);

  // 1) หน้าเว็บปกติ + พิมพ์ "t" ในช่องค้นหา ต้องไม่เปิด overlay
  {
    const { page, errors, ctx } = await newPage('?t=20');
    check(await page.evaluate(() => document.documentElement.hasAttribute('data-amtv-skin')), 'skin attribute set');
    await page.click('#search');
    await page.keyboard.type('the weeknd');
    check(!(await isOpen(page)), 'typing "t" in search input does not open overlay');
    check((await page.inputValue('#search')) === 'the weeknd', 'search input received all keys');
    await page.screenshot({ path: path.join(OUT, '0-normal-page.png') });
    await page.click('h1');
    await page.keyboard.press('t');
    check(await isOpen(page), 'pressing T on page opens overlay');
    await sleep(2600);
    await page.screenshot({ path: path.join(OUT, '1-open.png') });
    const st = await page.evaluate(() => ({ kind: window.__amtv.state.lyrics && window.__amtv.state.lyrics.kind, cur: window.__amtv.state.cur, req: window.__lastReq }));
    check(st.kind === 'synced', 'synced lyrics loaded (' + st.kind + ')');
    check(st.cur >= 3, 'current lyric line advanced (cur=' + st.cur + ')');
    check(st.req && st.req.title.includes('ฝนตกไหม') && st.req.duration === 214, 'lyrics-req carries title/duration');
    const font = await page.evaluate(() => document.fonts.check('700 40px "AMTV Sans"', 'ฝนตก') && document.fonts.check('700 40px "AMTV Sans"', 'Hello'));
    check(font, 'AMTV Sans available for Thai + Latin');

    // hint ต้องหายไปหลัง 4.5 วิ, ปุ่มต้องซ่อนหลังไม่ขยับ 3.5 วิ
    await sleep(2200);
    const hidden = await page.evaluate(() => {
      const sr = document.querySelector('amtv-root').shadowRoot;
      return { hint: sr.querySelector('.hint').classList.contains('show'), idle: sr.querySelector('.ov').classList.contains('idle') };
    });
    check(!hidden.hint, 'shortcut hint hides after 4.5s');
    check(hidden.idle, 'controls/cursor idle after 3.5s');

    // pause
    await page.keyboard.press('Space');
    await sleep(1000);
    check(!(await page.evaluate(() => window.__mock.inst.isPlaying)), 'Space pauses');
    await page.screenshot({ path: path.join(OUT, '2-paused.png') });

    // seek ±10
    const t0 = await page.evaluate(() => window.__mock.inst.currentPlaybackTime);
    await page.keyboard.press('ArrowRight');
    await sleep(300);
    const t1 = await page.evaluate(() => window.__mock.inst.currentPlaybackTime);
    check(Math.abs(t1 - t0 - 10) <= 1, `→ seeks +10s (${t0} → ${t1})`);

    // click lyric line → seek
    await page.evaluate(() => { const l = [...document.querySelector('amtv-root').shadowRoot.querySelectorAll('.line')].find((x) => x.textContent.startsWith("We'll be fine")); l.click(); });
    await sleep(300);
    const t2 = await page.evaluate(() => window.__mock.inst.currentPlaybackTime);
    check(t2 >= 46 && t2 <= 47, 'clicking lyric line seeks to its time (' + t2 + ')');
    check(await page.evaluate(() => window.__mock.inst.isPlaying), 'clicking a line resumes playback');

    // hide lyrics
    await page.keyboard.press('l');
    await sleep(3000);
    check(await page.evaluate(() => document.querySelector('amtv-root').shadowRoot.querySelector('.ov').classList.contains('nolyrics')), 'L hides lyrics');
    await page.screenshot({ path: path.join(OUT, '3-lyrics-hidden.png') });
    await page.keyboard.press('l');

    // next track
    await page.keyboard.press('n');
    await sleep(2500);
    const title = await page.evaluate(() => window.__amtv.state.title);
    check(title === 'Midnight Highway', 'N skips to next track');
    await page.screenshot({ path: path.join(OUT, '4-next-track.png') });
    // P ภายใน 3 วิ = เพลงก่อนหน้า, เกิน 3 วิ = กลับต้นเพลง
    await page.evaluate(() => window.__mock.inst.seekToTime(30));
    await sleep(600);
    await page.keyboard.press('p');
    await sleep(500);
    check((await page.evaluate(() => window.__amtv.state.title)) === 'Midnight Highway' && (await page.evaluate(() => window.__mock.inst.currentPlaybackTime)) <= 1, 'P after 3s restarts track');
    await page.keyboard.press('p');
    await sleep(500);
    check((await page.evaluate(() => window.__amtv.state.title)).startsWith('ฝนตกไหม'), 'P within 3s goes to previous track');

    await page.keyboard.press('Escape');
    check(!(await isOpen(page)), 'Esc closes overlay');
    check(errors.length === 0, 'no pageerror / console error' + (errors.length ? ': ' + errors.join(' | ') : ''));
    await ctx.close();
  }

  // 2) ยังไม่มีเพลงเล่น
  {
    const { page, errors, ctx } = await newPage('?empty=1');
    await page.keyboard.press('t');
    await sleep(1500);
    await page.screenshot({ path: path.join(OUT, '5-nothing-playing.png') });
    check(await page.evaluate(() => document.querySelector('amtv-root').shadowRoot.querySelector('.ov').classList.contains('nothing')), 'empty state shown');
    check(errors.length === 0, 'no errors in empty state');
    await ctx.close();
  }

  // 3) 4K
  {
    const { page, errors, ctx } = await newPage('?t=33', { width: 3840, height: 2160 });
    await page.keyboard.press('t');
    await sleep(2500);
    await page.screenshot({ path: path.join(OUT, '6-open-4k.png') });
    check(errors.length === 0, 'no errors at 4K');
    await ctx.close();
  }

  // 4) unit: parseLRC / hiresArt
  {
    const { page, ctx } = await newPage('');
    const r = await page.evaluate(() => {
      const L = window.__amtv.parseLRC('[offset:+500]\n[ar:x]\n[00:10.00][00:20.50]Hook\n[00:12.345]<00:12.40>word <00:13.00>by\n[01:02:03]odd\n');
      const A = window.__amtv.hiresArt;
      return {
        L,
        a1: A('https://is1-ssl.mzstatic.com/image/thumb/Music/x/abc.jpg/{w}x{h}bb.jpg'),
        a2: A('https://is1-ssl.mzstatic.com/image/thumb/Music/x/abc.jpg/296x296bb.webp'),
        a3: A('https://is1-ssl.mzstatic.com/image/thumb/Music/x/abc.jpg/88x88bb-60.jpg?x=1'),
      };
    });
    check(r.L.length === 5 && r.L[0].t === 0 && Math.abs(r.L[1].t - 9.5) < 1e-6 && r.L[2].text === 'word by', 'parseLRC handles offset/multi-stamp/word-timing');
    check(r.a1.endsWith('/1400x1400bb.jpg') && r.a2.endsWith('/1400x1400bb.webp') && r.a3.endsWith('/1400x1400bb.jpg?x=1'), 'hiresArt rewrites sizes: ' + [r.a1, r.a2, r.a3].join(' '));
    await ctx.close();
  }

  await browser.close();
  server.close();
  console.log(failures ? `\n${failures} FAILED` : '\nALL PASSED');
  process.exit(failures ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(1); });
