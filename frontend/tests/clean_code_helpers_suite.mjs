import test from 'node:test';
import assert from 'node:assert/strict';
import { labels } from './labels_fixture.mjs';
import { createInternalResolver } from './internal_loader.mjs';

function makeReactHarness() {
  const slots = [];
  let cursor = 0;
  let effects = [];
  const sameDeps = (a, b) => a && b && a.length === b.length && a.every((v, i) => Object.is(v, b[i]));
  const react = {
    useState(init) {
      const i = cursor++;
      if (!(i in slots)) slots[i] = { value: typeof init === 'function' ? init() : init };
      return [
        slots[i].value,
        (next) => {
          slots[i].value = typeof next === 'function' ? next(slots[i].value) : next;
        },
      ];
    },
    useRef(init) {
      const i = cursor++;
      return (slots[i] ||= { current: init });
    },
    useMemo(factory, deps) {
      const i = cursor++;
      if (!slots[i] || !sameDeps(slots[i].deps, deps)) slots[i] = { value: factory(), deps };
      return slots[i].value;
    },
    useCallback(fn, deps) {
      return react.useMemo(() => fn, deps);
    },
    useEffect(fn, deps) {
      const i = cursor++;
      const prev = slots[i];
      if (!prev || !deps || !sameDeps(prev.deps, deps)) {
        slots[i] = { deps, cleanup: prev?.cleanup };
        effects.push(() => {
          slots[i].cleanup?.();
          slots[i].cleanup = fn();
        });
      }
    },
  };
  return {
    react,
    run(fn) {
      cursor = 0;
      const result = fn();
      const pending = effects;
      effects = [];
      for (const ef of pending) ef();
      return result;
    },
    unmount() {
      for (const slot of slots) slot?.cleanup?.();
    },
  };
}

test('labels helpers resolve genre entries, duration formatting, and user initials', () => {
  assert.equal(labels.resolveGenreLabel({ id: 'comedy', label: 'Raw' }), 'Hài hước');
  assert.equal(labels.resolveGenreLabel({ id: 'custom', label: 'drama' }), 'Chính kịch');
  assert.equal(labels.resolveGenreLabel('slice-of-life'), 'Đời thường');
  assert.equal(labels.formatDurationMs(0), '00:00');
  assert.equal(labels.formatDurationMs(65_000), '01:05');
  assert.equal(labels.formatDurationMs(3_661_000), '61:01');
  assert.equal(labels.getUserInitials('Nguyen Van A'), 'NA');
  assert.equal(labels.getUserInitials('dippie'), 'DI');
  assert.equal(labels.getUserInitials('', 'K'), 'K');
});

test('libraryService validates guest storage and reconciles optimistic journal operations', () => {
  const store = new Map();
  const localStorage = {
    getItem: (k) => store.get(k) ?? null,
    setItem: (k, v) => store.set(k, String(v)),
    removeItem: (k) => store.delete(k),
  };
  const baseCtx = { window: { localStorage }, localStorage, DOMException, TypeError };
  const resolve = createInternalResolver(baseCtx, (name) => {
    if (name.includes('seriesCardService')) return { clearSeriesCardsCache() {} };
    return {};
  });
  const lib = resolve('@/lib/services/libraryService');

  localStorage.setItem(
    'dippie_guest_library_v1',
    JSON.stringify({ watchlist: ['m1', '', 42], favorites: ['m2'], ratings: { m1: 8.6, bad: 99 } })
  );
  assert.deepEqual(JSON.parse(JSON.stringify(lib.readGuestLibrary())), {
    watchlist: ['m1'],
    favorites: ['m2'],
    ratings: { m1: 9 },
  });

  const journal = new Map([
    [
      'watchlist:m2',
      { id: 1, action: 'watchlist', movieId: 'm2', readVersionAtStart: 1, committedAtReadVersion: null, status: 'pending', desiredValue: true },
    ],
    [
      'watchlist:m1',
      { id: 2, action: 'watchlist', movieId: 'm1', readVersionAtStart: 1, committedAtReadVersion: null, status: 'uncertain', desiredValue: false },
    ],
    [
      'rating:m3',
      { id: 3, action: 'rating', movieId: 'm3', readVersionAtStart: 1, committedAtReadVersion: 1, status: 'committed', desiredValue: 10 },
    ],
  ]);

  const reconciled = lib.reconcileLibrarySnapshot(
    { watchlist: ['m1'], favorites: [], ratings: { m3: 10 } },
    journal,
    2
  );
  assert.deepEqual(JSON.parse(JSON.stringify(reconciled.watchlist.sort())), ['m1', 'm2']);
  assert.equal(journal.has('watchlist:m1'), false);
  assert.equal(journal.has('rating:m3'), false);
  assert.equal(journal.has('watchlist:m2'), true);
  assert.equal(lib.isUncertainNetworkError(new TypeError('offline')), true);
  assert.equal(lib.isUncertainNetworkError(new Error('http 500')), false);
});

test('EpisodePlaybackTracker gates saves until playback starts and flushes episode switch once', () => {
  const resolve = createInternalResolver({ console, AbortController }, (name) => {
    if (name === '@/lib/labels') return labels;
    return {};
  });
  const mod = resolve('@/features/series/hooks/useSeriesPlayback');
  const tracker = new mod.EpisodePlaybackTracker();

  tracker.prepareEpisode('ep1');
  assert.equal(tracker.hasStarted('ep1'), false);
  assert.equal(tracker.canSave('ep1', 'pause'), false);
  assert.equal(tracker.canSave('ep1', 'leave'), false);

  tracker.markStarted('ep1');
  assert.equal(tracker.hasStarted('ep1'), true);
  assert.equal(tracker.canSave('ep1', 'pause'), true);
  assert.equal(tracker.canSave('ep1', 'episode-change'), true);
  assert.equal(tracker.canSave('ep1', 'episode-change'), false);
  assert.equal(tracker.canSave('ep1', 'leave'), false);

  assert.deepEqual(
    JSON.parse(JSON.stringify(mod.extractEpisodeRating({ episode: { id: 'ep1', averageRating: 8.5, ratingCount: 4 } }, null))),
    { averageRating: 8.5, ratingCount: 4 }
  );
  assert.equal(mod.extractEpisodeRating(null, null), null);
});

test('useCatalogGenres and useSeriesCards fetch, normalize, refresh on rating change, and abort on unmount', async () => {
  const listeners = new Map();
  const windowMock = {
    addEventListener(name, fn) {
      listeners.set(name, fn);
    },
    removeEventListener(name) {
      listeners.delete(name);
    },
  };
  let abortedGenreSignal = false;
  let seriesCalls = 0;

  const h1 = makeReactHarness();
  const baseCtx1 = { window: windowMock, AbortController, fetch: async () => ({}) };
  const resolve1 = createInternalResolver(baseCtx1, (name) => {
    if (name === 'react') return h1.react;
    if (name === '@/lib/labels') return labels;
    if (name.includes('WatchlistContext')) return { RATING_CHANGED_EVENT: 'dippie:rating-changed' };
    if (name.includes('catalogService')) {
      return {
        getCatalogGenres: ({ signal }) => {
          signal.addEventListener('abort', () => {
            abortedGenreSignal = true;
          });
          return Promise.resolve([{ id: 'comedy', label: 'Comedy', count: 5 }]);
        },
      };
    }
    return {};
  });
  const { useCatalogGenres } = resolve1('@/lib/hooks/useCatalogData');
  assert.equal(h1.run(() => useCatalogGenres()), null);
  await new Promise(setImmediate);
  assert.deepEqual(JSON.parse(JSON.stringify(h1.run(() => useCatalogGenres()))), [
    { id: 'comedy', label: 'Hài hước', count: 5 },
  ]);
  h1.unmount();
  assert.equal(abortedGenreSignal, true);

  const h2 = makeReactHarness();
  const baseCtx2 = { window: windowMock, AbortController };
  const resolve2 = createInternalResolver(baseCtx2, (name) => {
    if (name === 'react') return h2.react;
    if (name === '@/lib/labels') return labels;
    if (name.includes('WatchlistContext')) return { RATING_CHANGED_EVENT: 'dippie:rating-changed' };
    if (name.includes('seriesCardService')) {
      return {
        listSeriesCards: async () => {
          seriesCalls += 1;
          if (seriesCalls === 1) throw new Error('offline');
          return [{ id: 'ser_1', title: 'Jadoo', averageRating: 9, ratingCount: 2 }];
        },
      };
    }
    return {};
  });
  const { useSeriesCards } = resolve2('@/lib/hooks/useCatalogData');
  h2.run(() => useSeriesCards(true));
  await new Promise(setImmediate);
  const failedState = h2.run(() => useSeriesCards(true));
  assert.equal(failedState.error, 'offline');
  failedState.retry();
  h2.run(() => useSeriesCards(true));
  await new Promise(setImmediate);
  const loadedState = h2.run(() => useSeriesCards(true));
  assert.equal(loadedState.cards.length, 1);
  listeners.get('dippie:rating-changed')?.();
  h2.run(() => useSeriesCards(true));
  await new Promise(setImmediate);
  assert.equal(seriesCalls, 3);
  h2.unmount();
});

test('useProfileMetadata caches movie metadata, aborts stale items, and refreshes on rating change', async () => {
  const listeners = new Map();
  const windowMock = {
    addEventListener(name, fn) {
      listeners.set(name, fn);
    },
    removeEventListener(name) {
      listeners.delete(name);
    },
  };
  let calls = 0;
  const h = makeReactHarness();
  const baseCtx = { window: windowMock, AbortController, Date };
  const resolve = createInternalResolver(baseCtx, (name) => {
    if (name === 'react') return h.react;
    if (name.includes('WatchlistContext')) return { RATING_CHANGED_EVENT: 'dippie:rating-changed' };
    if (name.includes('catalogService')) {
      return {
        getCatalogMovieById: async (id) => {
          calls += 1;
          return { id, title: `Film ${id}`, rating: 0, year: 2020, runtime: '10m', genres: [], description: '', poster: '', backdrop: '' };
        },
      };
    }
    return {};
  });

  const { useProfileMetadata, clearProfileMetadataCache } = resolve('@/features/profile/useProfileMetadata');
  clearProfileMetadataCache();
  const ids = ['m1'];
  h.run(() => useProfileMetadata(ids));
  await new Promise(setImmediate);
  const resolved = h.run(() => useProfileMetadata(ids)).resolveItem('m1');
  assert.equal(resolved.status, 'found');
  assert.equal(resolved.movie.title, 'Film m1');
  assert.equal(calls, 1);

  listeners.get('dippie:rating-changed')?.({ detail: { movieId: 'm1' } });
  await new Promise(setImmediate);
  assert.equal(calls, 2);
  h.unmount();
});
