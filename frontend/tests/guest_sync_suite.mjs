import { labels } from './labels_fixture.mjs';
import { createInternalResolver, runWithSharedModules } from './internal_loader.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from '../node_modules/typescript/lib/typescript.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const watchlistFile = path.resolve(__dirname, '../lib/context/WatchlistContext.tsx');
const libraryServiceFile = path.resolve(__dirname, '../lib/services/libraryService.ts');
const guestSyncDialogFile = path.resolve(__dirname, '../features/library/components/GuestSyncDialog.tsx');

const turn = () => new Promise((resolve) => setImmediate(resolve));
const ok = (data) => ({ ok: true, status: 200, json: async () => ({ success: true, data }) });

function makeSyncHarness(fetcher, authRef, options = {}) {
  const slots = [];
  let index = 0;
  let effects = [];
  let changed = false;
  let value = null;
  let lastTree = null;
  const same = (a, b) => a && b && a.length === b.length && a.every((x, i) => Object.is(x, b[i]));

  const hooks = {
    createContext: () => ({ Provider: 'Provider' }),
    useContext: () => null,
    useState(initial) {
      const i = index++;
      if (!slots[i]) slots[i] = { v: typeof initial === 'function' ? initial() : initial };
      return [
        slots[i].v,
        (next) => {
          slots[i].v = typeof next === 'function' ? next(slots[i].v) : next;
          changed = true;
        },
      ];
    },
    useRef(initial) {
      const i = index++;
      if (!slots[i]) slots[i] = { current: initial };
      return slots[i];
    },
    useCallback(fn, deps) {
      const i = index++;
      if (!slots[i] || !same(slots[i].deps, deps)) slots[i] = { v: fn, deps };
      return slots[i].v;
    },
    useEffect(fn, deps) {
      const i = index++;
      if (!slots[i] || !same(slots[i].deps, deps)) {
        const prev = slots[i];
        slots[i] = { deps, cleanup: prev?.cleanup };
        effects.push(() => {
          slots[i].cleanup?.();
          slots[i].cleanup = fn();
        });
      }
    },
    useMemo(fn, deps) {
      const i = index++;
      if (!slots[i] || !same(slots[i].deps, deps)) slots[i] = { v: fn(), deps };
      return slots[i].v;
    },
    createElement(type, props, ...children) {
      return { type, props: { ...props, children } };
    },
  };

  const storage = new Map();
  const dispatchedEvents = [];
  const localStorage = {
    getItem: (k) => storage.get(k) ?? null,
    setItem: (k, v) => storage.set(k, String(v)),
    removeItem: (k) => storage.delete(k),
    clear: () => storage.clear(),
  };

  const windowObj = {
    localStorage,
    dispatchEvent: (ev) => {
      dispatchedEvents.push(ev.type);
      return true;
    },
  };

  const rawCode = fs.readFileSync(watchlistFile, 'utf8');
  const code = ts.transpileModule(rawCode, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      jsx: ts.JsxEmit.React,
      esModuleInterop: true,
      target: ts.ScriptTarget.ES2022,
    },
  }).outputText;

  const module = { exports: {} };
  const baseContext = {
    process: { env: {} },
    console,
    AbortController,
    DOMException,
    CustomEvent,
    Event,
    fetch: fetcher,
    window: windowObj,
    localStorage,
    setTimeout: options.realTimers ? setTimeout : () => 1,
    clearTimeout: options.realTimers ? clearTimeout : () => {},
  };
  runWithSharedModules(code, {
    ...baseContext,
    module,
    exports: module.exports,
    require: createInternalResolver(baseContext, (name) => {
      if (name === '@/lib/labels') return labels;
      if (name === 'react') return { __esModule: true, default: hooks, ...hooks, lazy: () => () => null };
      if (name === './AuthContext') return { useAuth: () => authRef.current };
      return {};
    }),
  });

  function render() {
    index = 0;
    changed = false;
    lastTree = module.exports.WatchlistProvider({ children: null });
    value = lastTree.props.value;
    const pending = effects;
    effects = [];
    pending.forEach((fn) => fn());
    return value;
  }

  async function settle() {
    for (let i = 0; i < 12; i++) {
      await turn();
      if (changed || i === 0) render();
    }
    return value;
  }

  return {
    render,
    settle,
    storage,
    dispatchedEvents,
    get value() { return value; },
    get tree() { return lastTree; },
  };
}

function loadLibraryService(fetcher, storage) {
  const localStorage = {
    getItem: (k) => storage.get(k) ?? null,
    setItem: (k, v) => storage.set(k, String(v)),
    removeItem: (k) => storage.delete(k),
  };
  const code = ts.transpileModule(fs.readFileSync(libraryServiceFile, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText;
  const module = { exports: {} };
  const baseContext = {
    process: { env: {} },
    console,
    AbortController,
    DOMException,
    CustomEvent,
    Event,
    fetch: fetcher,
    window: { localStorage, dispatchEvent: () => true },
    localStorage,
    setTimeout,
    clearTimeout,
  };
  runWithSharedModules(code, {
    ...baseContext,
    module,
    exports: module.exports,
    require: createInternalResolver(baseContext, () => ({})),
  });
  return module.exports;
}

function findNodes(node, pred, out = []) {
  if (!node || typeof node !== 'object') return out;
  if (Array.isArray(node)) {
    for (const child of node) findNodes(child, pred, out);
    return out;
  }
  if (pred(node)) out.push(node);
  findNodes(node.props?.children, pred, out);
  return out;
}

for (const status of ['guest', 'checking']) {
  test(`Guest storage is isolated and prompts once on ${status} to authenticated transition without silent writes`, async () => {
    const requests = [];
    const authRef = { current: { status, user: null, token: null, isAuthenticated: false } };
    const view = makeSyncHarness(async (url, init) => {
      requests.push({ url, init });
      return ok(url.endsWith('/watchlist') ? [{ movieId: 'member-film' }] : []);
    }, authRef);
    view.storage.set('dippie_guest_library_v1', JSON.stringify({
      watchlist: ['guest-film'], favorites: ['guest-favorite'], ratings: { 'guest-film': 9 },
    }));
    view.storage.set('dippie_guest_watch_progress_v1', JSON.stringify([
      { episodeId: 'guest-episode', assetId: 'youtube:11111111111', positionMs: 12000, completed: false },
    ]));
    view.render();
    await view.settle();
    authRef.current = { status: 'authenticated', user: { id: 1 }, token: 'member-token', isAuthenticated: true };
    let value = view.render();
    assert.equal(value.watchlist.length, 0);
    value = await view.settle();
    assert.deepEqual(Array.from(value.watchlist), ['member-film']);
    assert.equal(value.favorites.length, 0);
    assert.equal(value.ratings['guest-film'], undefined);
    assert.equal(value.toasts.length, 0);
    assert.ok(value.guestSyncPrompt, 'Must ask once before transferring guest data');
    assert.equal(value.guestSyncPrompt.totalCount, 4);
    await value.refetchLibrary();
    assert.ok(requests.length >= 3);
    assert.ok(requests.every(({ init }) => init.method === undefined && init.headers.Authorization === 'Bearer member-token'));
    assert.ok(requests.every(({ url }) => !url.includes('watch-progress')));
  });
}

test('Switching directly to a different account immediately hides the previous library', async () => {
  const authRef = { current: { status: 'authenticated', user: { id: 1 }, token: 'a', isAuthenticated: true } };
  const view = makeSyncHarness(async (url, init) => ok(url.endsWith('/watchlist') ? [{ movieId: init.headers.Authorization }] : []), authRef);
  view.render();
  assert.deepEqual(Array.from((await view.settle()).watchlist), ['Bearer a']);
  authRef.current = { status: 'authenticated', user: { id: 2 }, token: 'b', isAuthenticated: true };
  assert.equal(view.render().watchlist.length, 0);
  assert.deepEqual(Array.from((await view.settle()).watchlist), ['Bearer b']);
});

test('Authenticated library requests never turn an unavailable server into a guest write', async () => {
  const authRef = { current: { status: 'authenticated', user: { id: 1 }, token: 'a', isAuthenticated: true } };
  const view = makeSyncHarness(async () => ({ ok: false, status: 405 }), authRef);
  view.render();
  const value = await view.settle();
  assert.ok(value.libraryError);
  assert.equal(value.isGuestMode, false);
  assert.equal(view.storage.has('dippie_guest_library_v1'), false);
});

test('Refreshing one account keeps guest collection out of server ratings and favorites until confirmed', async () => {
  const authRef = { current: { status: 'authenticated', user: { id: 1 }, token: 'a', isAuthenticated: true } };
  const view = makeSyncHarness(async (url) => ok(url.endsWith('/ratings') ? [{ movieId: 'member-film', rating: 8 }] : []), authRef);
  view.storage.set('dippie_guest_library_v1', JSON.stringify({ favorites: ['guest-film'], ratings: { 'member-film': 1 } }));
  view.render();
  const value = await view.settle();
  assert.equal(value.ratings['member-film'], 8);
  assert.equal(value.favorites.length, 0);
  assert.ok(value.guestSyncPrompt);
});

test('Confirming guest sync merges watchlist, favorites, ratings, and watch progress once, clears guest keys, and shows one toast', async () => {
  const requests = [];
  const authRef = {
    current: {
      user: null,
      token: null,
      status: 'guest',
      isAuthenticated: false,
      handleSessionExpired: () => {},
      isAuthModalOpen: false,
    },
  };

  const h = makeSyncHarness(async (url, opts) => {
    const method = opts?.method || 'GET';
    const body = opts?.body ? JSON.parse(opts.body) : null;
    requests.push({ method, url, body });

    if (method === 'GET' && url.endsWith('/users/watchlist')) return ok([{ movieId: 'already_saved' }]);
    if (method === 'GET' && url.endsWith('/users/favorites')) return ok([]);
    if (method === 'GET' && url.endsWith('/users/ratings')) return ok([{ movieId: 'already_rated', rating: 7 }]);
    if (method === 'GET' && url.endsWith('/users/watch-progress')) {
      return ok([{ episodeId: 'ep_existing', version: 2, positionMs: 20000, completed: false }]);
    }
    return ok({});
  }, authRef);

  h.storage.set(
    'dippie_guest_library_v1',
    JSON.stringify({
      watchlist: ['already_saved', 'ser_peppa_pig'],
      favorites: ['ser_jadoo'],
      ratings: { already_rated: 4, ep_doraemon_1: 9 },
    })
  );
  h.storage.set(
    'dippie_guest_watch_progress_v1',
    JSON.stringify([
      {
        episodeId: 'ep_doraemon_1',
        seriesId: 'ser_doraemon',
        seriesTitle: 'Doraemon',
        episodeTitle: 'Tập 1',
        assetId: 'youtube:9aFcot7_7Q4',
        positionMs: 65000,
        durationMs: 600000,
        completed: false,
        version: 0,
        updatedAt: '2026-11-24T00:00:00Z',
      },
      {
        episodeId: 'ep_existing',
        seriesId: 'ser_jadoo',
        seriesTitle: 'Jadoo',
        episodeTitle: 'Tập 2',
        assetId: 'youtube:AbCdEfGhIjK',
        positionMs: 90000,
        durationMs: 600000,
        completed: false,
        version: 0,
        updatedAt: '2026-11-24T00:00:00Z',
      },
    ])
  );

  h.render();
  await h.settle();
  assert.deepEqual([...h.value.watchlist], ['already_saved', 'ser_peppa_pig']);

  authRef.current = {
    user: { id: 42, username: 'parent', email: 'parent@example.test', role: 'ROLE_USER' },
    token: 'jwt-42',
    status: 'authenticated',
    isAuthenticated: true,
    handleSessionExpired: () => {},
    isAuthModalOpen: false,
  };

  h.render();
  await h.settle();

  assert.ok(h.value.guestSyncPrompt, 'Prompt must appear before writing guest data');
  assert.equal(requests.filter((r) => r.method !== 'GET').length, 0, 'No writes before confirmation');

  await h.value.confirmGuestSync();
  await h.settle();

  const postWatchlist = requests.filter((r) => r.method === 'POST' && r.url.endsWith('/users/watchlist'));
  assert.equal(postWatchlist.length, 1);
  assert.equal(postWatchlist[0].body.movieId, 'ser_peppa_pig');

  const postFavorites = requests.filter((r) => r.method === 'POST' && r.url.endsWith('/users/favorites'));
  assert.equal(postFavorites.length, 1);
  assert.equal(postFavorites[0].body.movieId, 'ser_jadoo');

  const postRatings = requests.filter((r) => r.method === 'POST' && r.url.endsWith('/users/ratings'));
  assert.equal(postRatings.length, 1);
  assert.deepEqual(postRatings[0].body, { movieId: 'ep_doraemon_1', rating: 9 });

  const putProgress = requests.filter((r) => r.method === 'PUT' && r.url.includes('/users/watch-progress/'));
  assert.equal(putProgress.length, 2);
  const newEpPut = putProgress.find((r) => r.url.endsWith('/ep_doraemon_1'));
  const existingEpPut = putProgress.find((r) => r.url.endsWith('/ep_existing'));
  assert.deepEqual(newEpPut.body, { assetId: 'youtube:9aFcot7_7Q4', positionMs: 65000, expectedVersion: -1 });
  assert.deepEqual(existingEpPut.body, { assetId: 'youtube:AbCdEfGhIjK', positionMs: 90000, expectedVersion: 2 });

  assert.equal(h.value.guestSyncPrompt, null);
  assert.equal(h.storage.has('dippie_guest_library_v1'), false);
  assert.equal(h.storage.has('dippie_guest_watch_progress_v1'), false);
  assert.ok(h.dispatchedEvents.includes('dippie:watch-progress-synced'));

  const syncToasts = h.value.toasts.filter((t) => t.message.includes('đồng bộ'));
  assert.equal(syncToasts.length, 1);

  assert.ok(h.value.isWatchlisted('ser_peppa_pig'));
  assert.ok(h.value.isFavorite('ser_jadoo'));
  assert.equal(h.value.getRating('ep_doraemon_1'), 9);
  assert.equal(h.value.getRating('already_rated'), 7);

  await h.value.refetchLibrary();
  await h.settle();
  assert.equal(h.value.toasts.filter((t) => t.message.includes('đồng bộ')).length, 1);
});

test('Guest to account login with empty guest storage shows no prompt, performs no merge writes, and shows no sync toast', async () => {
  const writes = [];
  const authRef = {
    current: {
      user: null,
      token: null,
      status: 'guest',
      isAuthenticated: false,
      handleSessionExpired: () => {},
      isAuthModalOpen: false,
    },
  };

  const h = makeSyncHarness(async (url, opts) => {
    if (opts?.method && opts.method !== 'GET') writes.push({ url, method: opts.method });
    return ok([]);
  }, authRef);

  h.render();
  await h.settle();

  authRef.current = {
    user: { id: 7, username: 'empty', email: 'empty@example.test', role: 'ROLE_USER' },
    token: 'jwt-7',
    status: 'authenticated',
    isAuthenticated: true,
    handleSessionExpired: () => {},
    isAuthModalOpen: false,
  };

  h.render();
  await h.settle();

  assert.equal(h.value.guestSyncPrompt, null);
  assert.equal(writes.length, 0);
  assert.equal(h.value.toasts.length, 0);
});

test('Declining guest sync performs zero writes, clears both guest keys, and does not re-prompt on reload', async () => {
  const writes = [];
  const authRef = {
    current: {
      user: null,
      token: null,
      status: 'guest',
      isAuthenticated: false,
      handleSessionExpired: () => {},
      isAuthModalOpen: false,
    },
  };

  const h = makeSyncHarness(async (url, opts) => {
    const method = opts?.method || 'GET';
    if (method !== 'GET') writes.push({ url, method });
    return ok(url.endsWith('/watchlist') ? [{ movieId: 'member_only' }] : []);
  }, authRef);

  h.storage.set('dippie_guest_library_v1', JSON.stringify({ watchlist: ['other_person_film'], favorites: ['fav_1'], ratings: { fav_1: 8 } }));
  h.storage.set(
    'dippie_guest_watch_progress_v1',
    JSON.stringify([{ episodeId: 'ep_other', assetId: 'youtube:abcdefghijk', positionMs: 30000, completed: false }])
  );

  h.render();
  await h.settle();

  authRef.current = {
    user: { id: 12, username: 'mom', email: 'mom@example.test', role: 'ROLE_USER' },
    token: 'jwt-12',
    status: 'authenticated',
    isAuthenticated: true,
    handleSessionExpired: () => {},
    isAuthModalOpen: false,
  };

  h.render();
  await h.settle();
  assert.ok(h.value.guestSyncPrompt);

  h.value.declineGuestSync();
  await h.settle();

  assert.equal(writes.length, 0);
  assert.equal(h.value.guestSyncPrompt, null);
  assert.equal(h.storage.has('dippie_guest_library_v1'), false);
  assert.equal(h.storage.has('dippie_guest_watch_progress_v1'), false);
  assert.deepEqual([...h.value.watchlist], ['member_only']);
  assert.equal(h.value.toasts.length, 1);
  assert.equal(h.value.toasts[0].type, 'info');

  await h.value.refetchLibrary();
  await h.settle();
  assert.equal(h.value.guestSyncPrompt, null);
  assert.equal(writes.length, 0);
});

test('Authenticated boot after page reload (checking -> authenticated) asks once and merges upon confirmation', async () => {
  const writes = [];
  const authRef = {
    current: {
      user: null,
      token: null,
      status: 'checking',
      isAuthenticated: false,
      handleSessionExpired: () => {},
      isAuthModalOpen: false,
    },
  };

  const h = makeSyncHarness(async (url, opts) => {
    const method = opts?.method || 'GET';
    if (method !== 'GET') writes.push({ url, method, body: opts?.body ? JSON.parse(opts.body) : null });
    return ok([]);
  }, authRef);

  h.storage.set('dippie_guest_library_v1', JSON.stringify({ watchlist: ['reloaded_film'], favorites: [], ratings: {} }));
  h.render();
  await h.settle();
  assert.equal(h.value.guestSyncPrompt, null);

  authRef.current = {
    user: { id: 33, username: 'reloaded', email: 'reloaded@example.test', role: 'ROLE_USER' },
    token: 'jwt-33',
    status: 'authenticated',
    isAuthenticated: true,
    handleSessionExpired: () => {},
    isAuthModalOpen: false,
  };
  h.render();
  await h.settle();

  assert.ok(h.value.guestSyncPrompt, 'Authenticated boot from checking must show confirmation dialog');
  assert.equal(h.value.guestSyncPrompt.watchlistCount, 1);
  assert.equal(writes.length, 0);

  await h.value.confirmGuestSync();
  await h.settle();

  assert.equal(writes.length, 1);
  assert.equal(writes[0].body.movieId, 'reloaded_film');
  assert.equal(h.storage.has('dippie_guest_library_v1'), false);
  assert.equal(h.value.guestSyncPrompt, null);
  assert.equal(h.value.toasts.length, 1);
  assert.equal(h.value.toasts[0].type, 'success');
});

test('Partial guest sync clears only succeeded items, keeps failed items with full progress metadata, toasts partial status, and succeeds on retry', async () => {
  let failPending = true;
  const requests = [];
  const authRef = {
    current: {
      user: null,
      token: null,
      status: 'guest',
      isAuthenticated: false,
      handleSessionExpired: () => {},
      isAuthModalOpen: false,
    },
  };

  const h = makeSyncHarness(async (url, opts) => {
    const method = opts?.method || 'GET';
    const body = opts?.body ? JSON.parse(opts.body) : null;
    requests.push({ method, url, body });

    if (method === 'GET') return ok([]);
    if (failPending && method === 'POST' && url.endsWith('/users/watchlist') && body.movieId === 'wl_fail') {
      return { ok: false, status: 500, json: async () => ({ success: false }) };
    }
    if (failPending && method === 'PUT' && url.endsWith('/ep_fail')) {
      throw new Error('network error');
    }
    return ok({});
  }, authRef);

  const progressOk = {
    episodeId: 'ep_ok',
    seriesId: 'ser_doraemon',
    seriesTitle: 'Doraemon',
    episodeTitle: 'Tập 1',
    assetId: 'youtube:9aFcot7_7Q4',
    positionMs: 40000,
    durationMs: 600000,
    completed: false,
    version: 0,
    updatedAt: '2026-11-24T00:00:00Z',
  };
  const progressFail = {
    episodeId: 'ep_fail',
    seriesId: 'ser_jadoo',
    seriesTitle: 'Xin Chào Jadoo',
    episodeTitle: 'Tập 2',
    assetId: 'youtube:AbCdEfGhIjK',
    positionMs: 85000,
    durationMs: 600000,
    completed: false,
    version: 0,
    updatedAt: '2026-11-24T00:01:00Z',
  };

  h.storage.set(
    'dippie_guest_library_v1',
    JSON.stringify({
      watchlist: ['wl_ok', 'wl_fail'],
      favorites: ['fav_ok'],
      ratings: { rate_ok: 8 },
    })
  );
  h.storage.set('dippie_guest_watch_progress_v1', JSON.stringify([progressOk, progressFail]));

  h.render();
  await h.settle();

  authRef.current = {
    user: { id: 55, username: 'partial', email: 'partial@example.test', role: 'ROLE_USER' },
    token: 'jwt-55',
    status: 'authenticated',
    isAuthenticated: true,
    handleSessionExpired: () => {},
    isAuthModalOpen: false,
  };

  h.render();
  await h.settle();
  await h.value.confirmGuestSync();
  await h.settle();

  const remainingLib = JSON.parse(h.storage.get('dippie_guest_library_v1'));
  assert.deepEqual(remainingLib, {
    watchlist: ['wl_fail'],
    favorites: [],
    ratings: {},
  });

  const remainingProg = JSON.parse(h.storage.get('dippie_guest_watch_progress_v1'));
  assert.equal(remainingProg.length, 1);
  assert.equal(remainingProg[0].episodeId, 'ep_fail');
  assert.equal(remainingProg[0].seriesTitle, 'Xin Chào Jadoo');
  assert.equal(remainingProg[0].positionMs, 85000);

  assert.ok(h.dispatchedEvents.includes('dippie:watch-progress-synced'));
  assert.ok(h.dispatchedEvents.includes('dippie:rating-changed'));

  const partialToast = h.value.toasts.at(-1);
  assert.equal(partialToast.type, 'info');
  assert.match(partialToast.message, /một phần/i);
  assert.match(partialToast.message, /4\/6/);

  failPending = false;
  requests.length = 0;
  await h.value.refetchLibrary();
  await h.settle();

  const retryPosts = requests.filter((r) => r.method === 'POST' || r.method === 'PUT');
  assert.equal(retryPosts.length, 2);
  assert.equal(h.storage.has('dippie_guest_library_v1'), false);
  assert.equal(h.storage.has('dippie_guest_watch_progress_v1'), false);
  const retryToast = h.value.toasts.at(-1);
  assert.equal(retryToast.type, 'success');
});

test('Total guest sync failure preserves both guest keys, emits error toast, and skips progress-synced event', async () => {
  const authRef = {
    current: {
      user: null,
      token: null,
      status: 'guest',
      isAuthenticated: false,
      handleSessionExpired: () => {},
      isAuthModalOpen: false,
    },
  };

  const h = makeSyncHarness(async (url, opts) => {
    const method = opts?.method || 'GET';
    if (method === 'GET' && !url.endsWith('/users/watch-progress')) return ok([]);
    return { ok: false, status: 500, json: async () => ({ success: false }) };
  }, authRef);

  const progressRow = {
    episodeId: 'ep_1',
    seriesId: 'ser_1',
    seriesTitle: 'Heo Peppa',
    episodeTitle: 'Tập 1',
    assetId: 'youtube:11111111111',
    positionMs: 30000,
    durationMs: 300000,
    completed: false,
    version: 0,
    updatedAt: '2026-11-24T00:00:00Z',
  };

  h.storage.set(
    'dippie_guest_library_v1',
    JSON.stringify({ watchlist: ['ser_1'], favorites: ['ser_2'], ratings: { ser_1: 9 } })
  );
  h.storage.set('dippie_guest_watch_progress_v1', JSON.stringify([progressRow]));

  h.render();
  await h.settle();

  authRef.current = {
    user: { id: 88, username: 'failuser', email: 'fail@example.test', role: 'ROLE_USER' },
    token: 'jwt-88',
    status: 'authenticated',
    isAuthenticated: true,
    handleSessionExpired: () => {},
    isAuthModalOpen: false,
  };

  h.render();
  await h.settle();
  await h.value.confirmGuestSync();
  await h.settle();

  assert.deepEqual(JSON.parse(h.storage.get('dippie_guest_library_v1')), {
    watchlist: ['ser_1'],
    favorites: ['ser_2'],
    ratings: { ser_1: 9 },
  });
  assert.equal(JSON.parse(h.storage.get('dippie_guest_watch_progress_v1')).length, 1);
  assert.equal(h.dispatchedEvents.includes('dippie:watch-progress-synced'), false);
  assert.equal(h.value.toasts.length, 1);
  assert.equal(h.value.toasts[0].type, 'error');
  assert.match(h.value.toasts[0].message, /Chưa thể đồng bộ/);
});

test('Guest sync clears library key when library succeeds and only progress fails', async () => {
  const authRef = {
    current: {
      user: null,
      token: null,
      status: 'guest',
      isAuthenticated: false,
      handleSessionExpired: () => {},
      isAuthModalOpen: false,
    },
  };

  const h = makeSyncHarness(async (url, opts) => {
    const method = opts?.method || 'GET';
    if (method === 'GET' && url.endsWith('/users/watch-progress')) {
      return { ok: false, status: 503, json: async () => ({ success: false }) };
    }
    return ok([]);
  }, authRef);

  h.storage.set('dippie_guest_library_v1', JSON.stringify({ watchlist: ['ser_ok'], favorites: [], ratings: {} }));
  h.storage.set(
    'dippie_guest_watch_progress_v1',
    JSON.stringify([
      {
        episodeId: 'ep_only_prog',
        seriesId: 'ser_ok',
        seriesTitle: 'Heo Peppa',
        episodeTitle: 'Tập 1',
        assetId: 'youtube:11111111111',
        positionMs: 15000,
        durationMs: 300000,
        completed: false,
        version: 0,
        updatedAt: '2026-11-24T00:00:00Z',
      },
    ])
  );

  h.render();
  await h.settle();

  authRef.current = {
    user: { id: 91, username: 'split', email: 'split@example.test', role: 'ROLE_USER' },
    token: 'jwt-91',
    status: 'authenticated',
    isAuthenticated: true,
    handleSessionExpired: () => {},
    isAuthModalOpen: false,
  };

  h.render();
  await h.settle();
  await h.value.confirmGuestSync();
  await h.settle();

  assert.equal(h.storage.has('dippie_guest_library_v1'), false);
  assert.equal(JSON.parse(h.storage.get('dippie_guest_watch_progress_v1')).length, 1);
  assert.equal(h.value.toasts.at(-1).type, 'info');
});

test('Guest watch progress sync retries once on 409 conflict with refreshed version and skips overwrite if server is already ahead', async () => {
  const storage = new Map();
  storage.set(
    'dippie_guest_watch_progress_v1',
    JSON.stringify([
      { episodeId: 'ep_retry', assetId: 'youtube:11111111111', positionMs: 120000, completed: false },
      { episodeId: 'ep_ahead', assetId: 'youtube:22222222222', positionMs: 50000, completed: false },
    ])
  );

  const calls = [];
  let retryAttempt = 0;
  const lib = loadLibraryService(async (url, opts) => {
    const method = opts?.method || 'GET';
    const body = opts?.body ? JSON.parse(opts.body) : null;
    calls.push({ method, url, body });

    if (method === 'GET' && url.endsWith('/users/watch-progress')) {
      return ok([
        { episodeId: 'ep_retry', version: 2, positionMs: 30000, completed: false },
        { episodeId: 'ep_ahead', version: 1, positionMs: 10000, completed: false },
      ]);
    }
    if (method === 'PUT' && url.endsWith('/ep_retry')) {
      retryAttempt += 1;
      if (retryAttempt === 1) return { ok: false, status: 409, json: async () => ({ success: false }) };
      return ok({ version: 6 });
    }
    if (method === 'GET' && url.endsWith('/ep_retry')) {
      return ok({ episodeId: 'ep_retry', version: 5, positionMs: 40000, completed: false });
    }
    if (method === 'PUT' && url.endsWith('/ep_ahead')) {
      return { ok: false, status: 409, json: async () => ({ success: false }) };
    }
    if (method === 'GET' && url.endsWith('/ep_ahead')) {
      return ok({ episodeId: 'ep_ahead', version: 3, positionMs: 90000, completed: false });
    }
    return ok({});
  }, storage);

  const controller = new AbortController();
  const result = await lib.syncGuestDataToAccount(
    'tok-conflict',
    { watchlist: [], favorites: [], ratings: {} },
    controller.signal,
    () => true
  );

  assert.equal(result.failedCount, 0);
  assert.equal(result.mergedProgressCount, 1);
  assert.equal(storage.has('dippie_guest_watch_progress_v1'), false);

  const retryPuts = calls.filter((c) => c.method === 'PUT' && c.url.endsWith('/ep_retry'));
  assert.equal(retryPuts.length, 2);
  assert.equal(retryPuts[0].body.expectedVersion, 2);
  assert.equal(retryPuts[1].body.expectedVersion, 5);

  const aheadPuts = calls.filter((c) => c.method === 'PUT' && c.url.endsWith('/ep_ahead'));
  assert.equal(aheadPuts.length, 1, 'Must not overwrite when refreshed server progress is already ahead');
});

test('Guest sync enforces per-request timeouts so one stalled write fails fast while remaining items still sync', async () => {
  const storage = new Map();
  storage.set(
    'dippie_guest_library_v1',
    JSON.stringify({ watchlist: ['wl_hang', 'wl_fast'], favorites: [], ratings: {} })
  );

  const lib = loadLibraryService((url, opts) => {
    const body = opts?.body ? JSON.parse(opts.body) : null;
    if (body?.movieId === 'wl_hang') {
      return new Promise(() => {});
    }
    return Promise.resolve(ok({}));
  }, storage);

  const serverLib = { watchlist: [], favorites: [], ratings: {} };
  const result = await lib.syncGuestDataToAccount(
    'tok-timeout',
    serverLib,
    new AbortController().signal,
    () => true,
    25
  );

  assert.equal(result.syncedWriteCount, 1);
  assert.equal(result.failedCount, 1);
  assert.deepEqual(serverLib.watchlist, ['wl_fast']);
  assert.deepEqual(JSON.parse(storage.get('dippie_guest_library_v1')), {
    watchlist: ['wl_hang'],
    favorites: [],
    ratings: {},
  });
});

test('GuestSyncDialog presents the exact Vietnamese confirmation title and both confirm/decline actions', () => {
  const react = {
    useId: () => 'guest-sync-id',
    useRef: (initial) => ({ current: initial }),
    useLayoutEffect() {},
    createElement(type, props, ...children) {
      if (typeof type === 'function') return type({ ...props, children });
      return { type, props: { ...props, children } };
    },
  };
  const code = ts.transpileModule(fs.readFileSync(guestSyncDialogFile, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.React, esModuleInterop: true, target: ts.ScriptTarget.ES2022 },
  }).outputText;
  const module = { exports: {} };
  const baseContext = { console };
  runWithSharedModules(code, {
    ...baseContext,
    module,
    exports: module.exports,
    require: createInternalResolver(baseContext, (name) => (name === 'react' ? { __esModule: true, default: react, ...react } : {})),
  });

  let confirmed = 0;
  let declined = 0;
  const tree = module.exports.GuestSyncDialog({
    summary: { watchlistCount: 2, favoritesCount: 1, ratingsCount: 0, progressCount: 1, totalCount: 4 },
    busy: false,
    onConfirm: () => { confirmed += 1; },
    onDecline: () => { declined += 1; },
    onDismiss: () => {},
  });

  const serialized = JSON.stringify(tree);
  assert.match(serialized, /Chuyển phim đã lưu trên máy vào tài khoản\?/);
  assert.match(serialized, /Không, xóa dữ liệu tạm/);
  const confirmBtn = findNodes(tree, (n) => n.props?.['data-testid'] === 'guest-sync-confirm')[0];
  const declineBtn = findNodes(tree, (n) => n.props?.['data-testid'] === 'guest-sync-decline')[0];
  assert.ok(confirmBtn);
  assert.ok(declineBtn);
  confirmBtn.props.onClick();
  declineBtn.props.onClick();
  assert.equal(confirmed, 1);
  assert.equal(declined, 1);
});

test('Guest watch progress sync writes oldest episode first so the most recently watched episode stays newest on server', async () => {
  const storage = new Map();
  storage.set(
    'dippie_guest_watch_progress_v1',
    JSON.stringify([
      {
        episodeId: 'ep_newest',
        assetId: 'youtube:33333333333',
        positionMs: 45000,
        completed: false,
        updatedAt: '2026-11-26T00:05:00Z',
      },
      {
        episodeId: 'ep_middle',
        assetId: 'youtube:22222222222',
        positionMs: 30000,
        completed: false,
        updatedAt: '2026-11-26T00:02:00Z',
      },
      {
        episodeId: 'ep_oldest',
        assetId: 'youtube:11111111111',
        positionMs: 15000,
        completed: false,
        updatedAt: '2026-11-26T00:00:00Z',
      },
    ])
  );

  const putEpisodeOrder = [];
  const lib = loadLibraryService(async (url, opts) => {
    const method = opts?.method || 'GET';
    if (method === 'PUT' && url.includes('/users/watch-progress/')) {
      putEpisodeOrder.push(url.split('/').at(-1));
      return ok({ version: 0 });
    }
    return ok([]);
  }, storage);

  const result = await lib.syncGuestDataToAccount(
    'tok-order',
    { watchlist: [], favorites: [], ratings: {} },
    new AbortController().signal,
    () => true
  );

  assert.equal(result.failedCount, 0);
  assert.equal(result.mergedProgressCount, 3);
  assert.deepEqual(putEpisodeOrder, ['ep_oldest', 'ep_middle', 'ep_newest']);
});
