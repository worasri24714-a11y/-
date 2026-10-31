// unit test background.js ด้วย fetch จำลอง
const assert = require('assert');
const calls = [];
global.chrome = { storage: { local: { _d: {}, async get(k) { return k === null ? this._d : (k in this._d ? { [k]: this._d[k] } : {}); }, async set(o) { Object.assign(this._d, o); }, async remove(ks) { for (const k of ks) delete this._d[k]; } } } };
global.fetch = async (url) => {
  const u = new URL(url); calls.push(u.pathname + '?' + u.searchParams.toString());
  const json = (status, body) => ({ status, ok: status < 400, json: async () => body });
  if (u.pathname === '/api/get') {
    if (u.searchParams.get('track_name') === 'Exact' ) return json(200, { id: 9, duration: 200, syncedLyrics: '[00:01.00]exact', plainLyrics: 'exact' });
    return json(404, {});
  }
  if (u.searchParams.get('track_name') === 'ฝนตกไหม' || (u.searchParams.get('q') || '').startsWith('ฝนตกไหม'))
    return json(200, [
      { id: 1, duration: 260, syncedLyrics: '[00:01.00]too long' },
      { id: 2, duration: 215, syncedLyrics: null, plainLyrics: 'plain only' },
      { id: 3, duration: 211, syncedLyrics: '[00:01.00]best' },
    ]);
  return json(200, []);
};
const bg = require('../apple-music-tv/background.js');
(async () => {
  assert.strictEqual(bg.clean('Song (feat. Someone) - Single'), 'Song');
  assert.strictEqual(bg.clean('แสงสุดท้าย - EP'), 'แสงสุดท้าย');
  assert.strictEqual(bg.clean('Track [ft. X]'), 'Track');
  assert.strictEqual(bg.stripExtras('Hey Jude (Remastered 2015)'), 'Hey Jude');
  assert.strictEqual(bg.firstArtist('A & B'), 'A');
  let r = await bg.getLyrics({ title: 'Exact', artist: 'X', album: 'Y', duration: 200 });
  assert.strictEqual(r.synced, '[00:01.00]exact'); assert.strictEqual(calls.length, 1);
  r = await bg.getLyrics({ title: 'Exact', artist: 'X', album: 'Y', duration: 200 });
  assert.strictEqual(calls.length, 1, 'cached');
  r = await bg.getLyrics({ title: 'ฝนตกไหม (feat. Nova)', artist: 'วงสายลม', album: 'แสงสุดท้าย - Single', duration: 214 });
  assert.strictEqual(r.synced, '[00:01.00]best');
  assert.ok(calls.some((c) => c.includes('/api/get') && c.includes('album_name=%E0%B9%81%E0%B8%AA%E0%B8%87%E0%B8%AA%E0%B8%B8%E0%B8%94%E0%B8%97%E0%B9%89%E0%B8%B2%E0%B8%A2&')), 'album cleaned before /get');
  r = await bg.getLyrics({ title: 'Nothing', artist: 'Nobody', album: '', duration: 100 });
  assert.strictEqual(r, null);
  console.log('background.js tests: ALL PASSED (' + calls.length + ' fetches)');
})().catch((e) => { console.error('FAIL', e); process.exit(1); });
