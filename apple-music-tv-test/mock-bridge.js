// จำลอง bridge.js + background สำหรับหน้า mock: โหลดฟอนต์จาก bytes และตอบ lyrics-req ด้วย LRC ตัวอย่าง
(async () => {
  const FONTS = [
    ['/ext/fonts/inter-latin.woff2', 'U+0000-00FF,U+0131,U+0152-0153,U+02BB-02BC,U+02C6,U+02DA,U+02DC,U+0304,U+0308,U+0329,U+2000-206F,U+20AC,U+2122,U+2191,U+2193,U+2212,U+2215,U+FEFF,U+FFFD'],
    ['/ext/fonts/inter-latin-ext.woff2', 'U+0100-02BA,U+02BD-02C5,U+02C7-02CC,U+02CE-02D7,U+02DD-02FF,U+1D00-1DBF,U+1E00-1E9F,U+1EF2-1EFF,U+2020,U+20A0-20AB,U+20AD-20C0,U+2113,U+2C60-2C7F,U+A720-A7FF'],
    ['/ext/fonts/anuphan-thai.woff2', 'U+02D7,U+0303,U+0331,U+0E01-0E5B,U+200C-200D,U+25CC'],
  ];
  const LRC_TH = `[ti:ฝนตกไหม]
[00:06.50]เสียงฝนพรำ ข้างนอกหน้าต่าง
[00:11.20]I still remember every word you said
[00:16.00]ฉันยังรอ ใต้แสงไฟถนน
[00:21.30]Hold on, the night is young
[00:26.40]
[00:31.00]ถ้าฟ้ายังร้องไห้ ฉันจะอยู่ตรงนี้
[00:36.20]Through the rain, through the fire
[00:41.10]ทุกคืนที่เหงา ใจยังเรียกหาเธอ
[00:46.50]We'll be fine, we'll be fine
[00:51.80]น้ำตาที่ไหล ปนไปกับสายฝน
[00:57.00]Don't let go of my hand tonight
[01:02.40]เพลงนี้ร้องให้เธอ คนเดียวเท่านั้น
[01:08.00]`;
  const LRC_EN = `[00:02.00]Headlights on the midnight highway
[00:06.80]Radio playing all our favorite songs
[00:11.50]Every mile I'm getting closer
[00:16.20]To the place where I belong
[00:21.00]Oh, oh, keep on driving
[00:25.70]Till the morning finds us home`;
  window.addEventListener('message', (e) => {
    const m = e.data;
    if (!m || m.__amtv !== 'page') return;
    if (m.type === 'lyrics-req') {
      window.__lastReq = m.track;
      const synced = /ฝน/.test(m.track.title) ? LRC_TH : LRC_EN;
      setTimeout(() => window.postMessage({ __amtv: 'bridge', type: 'lyrics-res', id: m.id, ok: true, result: { synced, plain: null } }, '*'), 300);
    } else if (m.type === 'zoom') {
      window.postMessage({ __amtv: 'bridge', type: 'zoom-res', id: m.id, ok: false }, '*');
    }
  });
  await Promise.all(FONTS.map(async ([u, r]) => {
    const buf = await (await fetch(u)).arrayBuffer();
    const f = new FontFace('AMTV Sans', buf, { weight: '100 900', unicodeRange: r });
    await f.load(); document.fonts.add(f);
  }));
  document.documentElement.setAttribute('data-amtv-fonts', 'ready');
})();
