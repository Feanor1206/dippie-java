import { runWithSharedModules } from './internal_loader.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import ts from '../node_modules/typescript/lib/typescript.js';

const record = (extra = {}) => ({ episodeId: 'ep1', seriesId: 'series1', seriesTitle: 'Jadoo', seasonId: 'season1', seasonNumber: 1,
  seasonTitle: 'Mùa 1', episodeNumber: 1, episodeTitle: 'Tập một', assetId: 'asset1', positionMs: 60000,
  durationMs: 1000000, completed: false, version: 0, updatedAt: '2026-10-27T00:00:00Z', ...extra });
const draft = (extra = {}) => ({ ...record(), ...extra });
const reply = (data, status = 200) => ({ ok: status >= 200 && status < 300, status, json: async () => ({ success: true, data }) });
const tick = () => new Promise(resolve => setTimeout(resolve, 0));
function load(file, globals = {}) {
  const module = { exports: {} };
  const source = fs.readFileSync(new URL(file, import.meta.url), 'utf8');
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText;
  runWithSharedModules(code, { module, exports: module.exports, process: { env: {} }, console, AbortController,
    URLSearchParams, setTimeout, clearTimeout, fetch: async () => { throw Error('Unexpected network'); }, ...globals });
  return module.exports;
}
function environment(fetch) {
  const stored = new Map();
  const window = { localStorage: { getItem: key => stored.get(key) ?? null, setItem: (key, val) => stored.set(key, val), removeItem: key => stored.delete(key) } };
  return { stored, service: load('../lib/services/watchProgressService.ts', { window, fetch }) };
}
function hookHarness(auth, service) {
  const slots = []; let cursor = 0; let pending = []; let authValue = auth;
  const same = (a,b) => a && b && a.length === b.length && a.every((v,i) => Object.is(v,b[i]));
  const react = {
    useState(init) { const i = cursor++; if (!slots[i]) slots[i] = { value: typeof init === 'function' ? init() : init };
      return [slots[i].value, val => { slots[i].value = typeof val === 'function' ? val(slots[i].value) : val; }]; },
    useRef(value) { const i = cursor++; return slots[i] ||= { current: value }; },
    useCallback(fn, deps) { const i = cursor++; if (!slots[i] || !same(slots[i].deps,deps)) slots[i] = { fn, deps }; return slots[i].fn; },
    useEffect(fn,deps) { const i = cursor++; if (!slots[i] || !same(slots[i].deps,deps)) {
      pending.push(() => { slots[i]?.cleanup?.(); slots[i] = { deps, cleanup: fn() }; });
    } },
  };
  const hook = load('../lib/hooks/useWatchProgress.ts', { require: name => name === 'react' ? react : name.includes('AuthContext')
    ? { useAuth: () => authValue } : service });
  return { render(nextAuth = authValue) { authValue = nextAuth; cursor = 0; const output = hook.useWatchProgress();
    const effects = pending; pending = []; effects.forEach(fn => fn()); return output; },
    close() { slots.forEach(slot => slot?.cleanup?.()); } };
}
const guest = { status: 'guest', token: null, user: null, handleSessionExpired() {} };
const account = (id, token) => ({ ...guest, status: 'authenticated', user: { id }, token });

test('Guest storage is defensive, bounded and persists file identity without touching account API', () => {
  const { service, stored } = environment();
  stored.set(service.GUEST_WATCH_PROGRESS_KEY, '{invalid');
  assert.equal(service.getGuestWatchProgress().length, 0);
  for (let i=0;i<35;i++) service.saveGuestWatchProgress(draft({ episodeId: `ep${i}` }), Date.now()+i);
  assert.equal(service.getGuestWatchProgress().length, 30);
  assert.equal(service.getGuestWatchProgress()[0].episodeId, 'ep34');
  const href = service.buildWatchProgressHref(record());
  const url = new URL(href, 'http://localhost');
  assert.equal(url.searchParams.get('asset'), 'asset1');
  assert.equal(url.searchParams.get('t'), '60000');
  assert.equal(url.searchParams.get('timeline'), 'file');
  assert.equal(service.isUsableResumeProgress(record({ completed: true })), false);
  assert.equal(service.isUsableResumeProgress(record({ positionMs: 1000000 })), false);
  assert.throws(() => service.saveGuestWatchProgress(draft({ positionMs: NaN })));
  assert.throws(() => service.saveGuestWatchProgress(draft({ positionMs: -1 })));
  assert.equal(service.readAutoNextPreference(), false);
  service.writeAutoNextPreference(true); assert.equal(service.readAutoNextPreference(), true);
});

test('Account requests use Authorization, strict integer positions and explicit version, never URL tokens', async () => {
  const calls = [];
  const { service } = environment(async (url, opts) => { calls.push({url,opts}); return reply(record({version:1})); });
  await service.saveWatchProgress('secret', 'ep1', draft({ positionMs: 12345 }), 0);
  assert.equal(calls[0].opts.headers.Authorization, 'Bearer secret');
  assert.ok(!calls[0].url.includes('secret'));
  assert.deepEqual(JSON.parse(calls[0].opts.body), {assetId:'asset1',positionMs:12345,expectedVersion:0});
  await assert.rejects(service.saveWatchProgress('secret','ep1',draft({positionMs:1.5}),0));
  await assert.rejects(service.saveWatchProgress('secret','ep1',draft(),-2));
  assert.equal(calls.length,1);
});

test('Network timeout and caller cancellation finish even when fetch ignores abort', async () => {
  const { service } = environment(() => new Promise(() => {}));
  await assert.rejects(service.listWatchProgress('secret',{timeoutMs:10}), error => error.isTimeout);
  const controller = new AbortController();
  const request = service.listWatchProgress('secret',{signal:controller.signal}); controller.abort();
  await assert.rejects(request, error => error.isCanceled);
});

test('Switching account hides old records immediately and late reply cannot enter the new scope', async () => {
  const waiting = [];
  const { service, stored } = environment((url, opts) => new Promise(resolve => waiting.push({resolve,signal:opts.signal})));
  const h = hookHarness(account(1,'one'),service);
  h.render(); await tick();
  h.render(account(2,'two')); await tick();
  assert.equal(waiting[0].signal.aborted,true);
  waiting[0].resolve(reply([record({episodeTitle:'Private user one'})]));
  waiting[1].resolve(reply([])); await tick(); await tick();
  assert.equal(h.render().records.length,0);
  assert.equal(stored.size,0);
  h.close();
});

test('Guest records are not merged into the account, and logout never writes account rows to local storage', async () => {
  const { service, stored } = environment(async () => reply([]));
  service.saveGuestWatchProgress(draft()); const original = stored.get(service.GUEST_WATCH_PROGRESS_KEY);
  const h = hookHarness(guest,service); h.render(); assert.equal(h.render().records.length,1);
  h.render(account(1,'one')); await tick(); assert.equal(h.render().records.length,0);
  h.render(guest); assert.equal(h.render().records.length,1);
  assert.equal(stored.get(service.GUEST_WATCH_PROGRESS_KEY),original); h.close();
});

test('409 refreshes version and retries once; if retry also returns 409, keeps the server record and reports conflict', async () => {
  const putVersions = [];
  const { service } = environment(async (url, opts) => {
    if (opts.method === 'PUT') {
      putVersions.push(JSON.parse(opts.body).expectedVersion);
      return reply(null, 409);
    }
    return reply(url.endsWith('/ep1') ? record({ positionMs: 400000, version: 4 }) : [record({ version: 3 })]);
  });
  const h = hookHarness(account(1, 'one'), service);
  h.render();
  await tick();
  const result = await h.render().saveProgress(draft({ positionMs: 80000 }));
  assert.equal(result, null);
  assert.deepEqual(putVersions, [3, 4]);
  const output = h.render();
  assert.equal(output.records[0].positionMs, 400000);
  assert.match(output.error, /cửa sổ khác/);
  h.close();
});

test('409 on YouTube watch progress refreshes the server version and succeeds on the single retry', async () => {
  const puts = [];
  let putAttempt = 0;
  const ytRecord = (extra = {}) =>
    record({
      episodeId: 'ep_doraemon_s05_0209',
      assetId: 'youtube:0FHNLZszGiU',
      positionMs: 360000,
      version: 2,
      ...extra,
    });
  const { service } = environment(async (url, opts) => {
    if (opts.method === 'PUT') {
      putAttempt += 1;
      const body = JSON.parse(opts.body);
      puts.push(body);
      if (putAttempt === 1) return reply(null, 409);
      return reply(ytRecord({ positionMs: body.positionMs, version: 4 }));
    }
    if (url.endsWith('/ep_doraemon_s05_0209')) {
      return reply(ytRecord({ positionMs: 350000, version: 3 }));
    }
    return reply([ytRecord({ version: 2 })]);
  });

  const h = hookHarness(account(1, 'one'), service);
  h.render();
  await tick();
  const saved = await h.render().saveProgress(
    draft({
      episodeId: 'ep_doraemon_s05_0209',
      assetId: 'youtube:0FHNLZszGiU',
      positionMs: 360000,
    }),
    'episode-change'
  );
  assert.ok(saved);
  assert.equal(saved.version, 4);
  assert.equal(saved.positionMs, 360000);
  assert.deepEqual(puts.map((p) => p.expectedVersion), [2, 3]);
  assert.equal(h.render().error, null);
  h.close();
});

test('Per-episode saves serialize all reasons including leave (one in flight, latest position wins, identical leave deduplicated)', async () => {
  let inFlight = 0;
  let maxConcurrent = 0;
  let serverVersion = 2;
  const sentPuts = [];
  const gates = [];
  const ytRecord = (extra = {}) =>
    record({
      episodeId: 'ep_doraemon_s05_0209',
      assetId: 'youtube:0FHNLZszGiU',
      positionMs: 360000,
      version: 2,
      ...extra,
    });

  const { service } = environment(async (url, opts) => {
    if (opts.method === 'PUT') {
      inFlight += 1;
      maxConcurrent = Math.max(maxConcurrent, inFlight);
      const body = JSON.parse(opts.body);
      sentPuts.push({ body, keepalive: Boolean(opts.keepalive) });
      await new Promise((resolve) => gates.push(resolve));
      inFlight -= 1;
      if (body.expectedVersion !== serverVersion) {
        return reply(null, 409);
      }
      serverVersion += 1;
      return reply(ytRecord({ positionMs: body.positionMs, version: serverVersion }));
    }
    return reply([ytRecord({ version: 2 })]);
  });

  const h = hookHarness(account(1, 'one'), service);
  h.render();
  await tick();
  const output = h.render();

  const p1 = output.saveProgress(
    draft({ episodeId: 'ep_doraemon_s05_0209', assetId: 'youtube:0FHNLZszGiU', positionMs: 360000 }),
    'episode-change'
  );
  await tick();
  const pDupLeave = output.saveProgress(
    draft({ episodeId: 'ep_doraemon_s05_0209', assetId: 'youtube:0FHNLZszGiU', positionMs: 360000 }),
    'leave'
  );
  assert.equal(await pDupLeave, null);

  const p2 = output.saveProgress(
    draft({ episodeId: 'ep_doraemon_s05_0209', assetId: 'youtube:0FHNLZszGiU', positionMs: 365000 }),
    'pause'
  );
  const p3 = output.saveProgress(
    draft({ episodeId: 'ep_doraemon_s05_0209', assetId: 'youtube:0FHNLZszGiU', positionMs: 375000 }),
    'leave'
  );

  assert.equal(sentPuts.length, 1);
  assert.equal(maxConcurrent, 1);
  gates.shift()();
  await tick();
  await tick();

  assert.equal(sentPuts.length, 2);
  assert.equal(maxConcurrent, 1);
  gates.shift()();
  const [r1, r2, r3] = await Promise.all([p1, p2, p3]);
  assert.equal(r1.version, 3);
  assert.equal(r2, null);
  assert.equal(r3.version, 4);
  assert.equal(r3.positionMs, 375000);
  assert.deepEqual(
    sentPuts.map((entry) => ({
      positionMs: entry.body.positionMs,
      expectedVersion: entry.body.expectedVersion,
      keepalive: entry.keepalive,
    })),
    [
      { positionMs: 360000, expectedVersion: 2, keepalive: false },
      { positionMs: 375000, expectedVersion: 3, keepalive: true },
    ]
  );
  h.close();
});

test('Two local saves serialize their optimistic SQL versions', async () => {
  const versions = []; let version = 0;
  const { service } = environment(async (url, opts) => {
    if (opts.method === 'PUT') { const payload = JSON.parse(opts.body); versions.push(payload.expectedVersion);
      await tick(); return reply(record({positionMs:payload.positionMs,version:++version})); }
    return reply([record()]);
  });
  const h = hookHarness(account(1,'one'),service); h.render(); await tick();
  const output = h.render(); await Promise.all([output.saveProgress(draft({positionMs:70000})),output.saveProgress(draft({positionMs:80000}))]);
  assert.deepEqual(versions,[0,1]); assert.equal(h.render().records[0].positionMs,80000); h.close();
});

test('normalizeWatchProgressRecord treats zero durationMs as unknown and isProgressCompleted matches backend threshold', () => {
  const { service } = environment();
  const normalized = service.normalizeWatchProgressRecord(record({ durationMs: 0, positionMs: 45000 }));
  assert.equal(normalized.durationMs, null);
  assert.equal(service.isUsableResumeProgress(normalized), true);
  assert.equal(service.isProgressCompleted(570000, 600000), true);
  assert.equal(service.isProgressCompleted(569999, 600000), false);
  assert.equal(service.isProgressCompleted(3540000, 3600000), true);
  assert.equal(service.isProgressCompleted(3539999, 3600000), false);
  assert.equal(service.isProgressCompleted(60000, null), false);
  assert.equal(service.isUsableResumeProgress(record({ positionMs: 570000, durationMs: 600000, completed: false })), false);
});

test('Unmount preserves in-flight save, broadcasts update to same-scope consumer without stale GET clobber, and isolates accounts', async () => {
  const listeners = new Map();
  const stored = new Map();
  const sharedWindow = {
    localStorage: {
      getItem: (key) => stored.get(key) ?? null,
      setItem: (key, val) => stored.set(key, val),
      removeItem: (key) => stored.delete(key),
    },
    addEventListener(type, fn) {
      if (!listeners.has(type)) listeners.set(type, new Set());
      listeners.get(type).add(fn);
    },
    removeEventListener(type, fn) {
      listeners.get(type)?.delete(fn);
    },
    dispatchEvent(event) {
      for (const fn of [...(listeners.get(event.type) || [])]) fn(event);
      return true;
    },
  };
  const pendingPuts = [];
  const pendingGets = [];
  let getCount = 0;
  const service = load('../lib/services/watchProgressService.ts', {
    window: sharedWindow,
    Event,
    CustomEvent,
    fetch: (url, opts = {}) => {
      if (opts.method === 'PUT') {
        return new Promise((resolve) => pendingPuts.push({ resolve, signal: opts.signal, body: JSON.parse(opts.body) }));
      }
      getCount += 1;
      if (getCount === 1) {
        return Promise.resolve(reply([record({ version: 2, positionMs: 30000, updatedAt: '2026-11-26T00:00:00Z' })]));
      }
      return new Promise((resolve) => pendingGets.push({ resolve, url, auth: opts.headers.Authorization }));
    },
  });

  const makeScopedHarness = (auth) => {
    const slots = []; let cursor = 0; let pending = []; let authValue = auth;
    const same = (a, b) => a && b && a.length === b.length && a.every((v, i) => Object.is(v, b[i]));
    const react = {
      useState(init) {
        const i = cursor++;
        if (!slots[i]) slots[i] = { value: typeof init === 'function' ? init() : init };
        return [slots[i].value, (val) => { slots[i].value = typeof val === 'function' ? val(slots[i].value) : val; }];
      },
      useRef(value) { const i = cursor++; return (slots[i] ||= { current: value }); },
      useCallback(fn, deps) { const i = cursor++; if (!slots[i] || !same(slots[i].deps, deps)) slots[i] = { fn, deps }; return slots[i].fn; },
      useEffect(fn, deps) {
        const i = cursor++;
        if (!slots[i] || !same(slots[i].deps, deps)) {
          pending.push(() => { slots[i]?.cleanup?.(); slots[i] = { deps, cleanup: fn() }; });
        }
      },
    };
    const hook = load('../lib/hooks/useWatchProgress.ts', {
      window: sharedWindow,
      Event,
      CustomEvent,
      require: (name) => (name === 'react' ? react : name.includes('AuthContext') ? { useAuth: () => authValue } : service),
    });
    return {
      render(nextAuth = authValue) {
        authValue = nextAuth; cursor = 0;
        const output = hook.useWatchProgress();
        const effects = pending; pending = []; effects.forEach((fn) => fn());
        return output;
      },
      close() { slots.forEach((slot) => slot?.cleanup?.()); },
    };
  };

  const playerHook = makeScopedHarness(account(1, 'tok1'));
  playerHook.render();
  await tick();
  const savePromise = playerHook.render().saveProgress(draft({ positionMs: 95000 }), 'pause');
  await tick();
  playerHook.close();

  assert.equal(pendingPuts.length, 1);
  assert.equal(pendingPuts[0].signal.aborted, false);

  const homeHook = makeScopedHarness(account(1, 'tok1'));
  const otherAccountHook = makeScopedHarness(account(2, 'tok2'));
  homeHook.render();
  otherAccountHook.render();
  await tick();

  pendingPuts[0].resolve(reply(record({ version: 3, positionMs: 95000, updatedAt: '2026-11-26T00:01:00Z' })));
  const saved = await savePromise;
  assert.equal(saved.version, 3);
  assert.equal(homeHook.render().records[0].positionMs, 95000);
  assert.equal(otherAccountHook.render().records.length, 0);

  pendingGets[0].resolve(reply([record({ version: 2, positionMs: 30000, updatedAt: '2026-11-26T00:00:00Z' })]));
  pendingGets[1].resolve(reply([]));
  await tick();
  await tick();
  assert.equal(homeHook.render().records[0].version, 3);
  assert.equal(homeHook.render().records[0].positionMs, 95000);
  assert.equal(otherAccountHook.render().records.length, 0);

  homeHook.close();
  otherAccountHook.close();
});

test('Reload syncs lower version after delete+recreate and leave double-409 refresh uses keepalive', async () => {
  let listPhase = 1;
  const putVersions = [];
  const episodeGets = [];
  const { service } = environment(async (url, opts = {}) => {
    if (opts.method === 'PUT') {
      const body = JSON.parse(opts.body);
      putVersions.push(body.expectedVersion);
      if (url.endsWith('/ep2')) return reply(null, 409);
      return reply(record({ version: body.expectedVersion + 1, positionMs: body.positionMs }));
    }
    if (url.endsWith('/ep2')) {
      episodeGets.push({ keepalive: Boolean(opts.keepalive) });
      return reply(record({ episodeId: 'ep2', version: 2, positionMs: 90000 }));
    }
    if (listPhase === 1) return reply([record({ version: 4, positionMs: 50000 })]);
    return reply([record({ version: 0, positionMs: 50000 })]);
  });

  const h = hookHarness(account(1, 'one'), service);
  h.render();
  await tick();
  listPhase = 2;
  h.render().reload();
  h.render();
  await tick();
  await h.render().saveProgress(draft({ positionMs: 75000 }), 'pause');
  assert.deepEqual(putVersions, [0]);

  await h.render().saveProgress(draft({ episodeId: 'ep2', positionMs: 60000 }), 'leave');
  assert.equal(episodeGets.length, 2);
  assert.equal(episodeGets[0].keepalive, true);
  assert.equal(episodeGets[1].keepalive, true);
  h.close();
});
