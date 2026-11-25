import { labels } from './labels_fixture.mjs';
import { createInternalResolver, shouldInlineComponent, runWithSharedModules } from './internal_loader.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import ts from '../node_modules/typescript/lib/typescript.js';

function loadTsModule(relativePath, globals = {}) {
  const source = fs.readFileSync(new URL(relativePath, import.meta.url), 'utf8');
  const code = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      jsx: ts.JsxEmit.React,
      esModuleInterop: true,
      target: ts.ScriptTarget.ES2022,
    },
  }).outputText;
  const module = { exports: {} };
  const baseContext = {
    console,
    AbortController,
    URLSearchParams,
    setTimeout,
    clearTimeout,
    ...globals,
  };
  const customRequire = typeof globals.require === 'function' ? globals.require : () => ({});
  runWithSharedModules(code, {
    ...baseContext,
    module,
    exports: module.exports,
    require: createInternalResolver(baseContext, customRequire),
  });
  return module.exports;
}

function getText(node) {
  function collect(n) {
    if (Array.isArray(n)) return n.map(collect).join(' ');
    if (n && typeof n === 'object') return collect(n.props?.children);
    return typeof n === 'string' || typeof n === 'number' ? String(n) : '';
  }
  return collect(node).replace(/\s+/g, ' ').trim();
}

function makeReact() {
  const slots = [];
  let cursor = 0;
  const react = {
    useState(init) {
      const i = cursor++;
      if (!(i in slots)) slots[i] = { value: typeof init === 'function' ? init() : init };
      return [slots[i].value, (next) => { slots[i].value = typeof next === 'function' ? next(slots[i].value) : next; }];
    },
    useRef(init) {
      const i = cursor++;
      return (slots[i] ||= { current: init });
    },
    useEffect() {},
    createElement(type, props, ...children) {
      if (shouldInlineComponent(type)) return type(props || {});
      const flat = children.length === 1 ? children[0] : children.length > 1 ? children : undefined;
      return { type, props: { ...props, ...(flat !== undefined ? { children: flat } : {}) } };
    },
  };
  return { react, reset() { cursor = 0; } };
}

test('resolveRatingSummary formats averageRating with ratingCount and shows Chưa có đánh giá when count is 0 or fields are missing', () => {
  const rated = labels.resolveRatingSummary({ averageRating: 8.5, ratingCount: 12, rating: 0 });
  assert.equal(rated.hasRating, true);
  assert.equal(rated.label, '8.5/10 (12 lượt chấm)');
  assert.equal(rated.compactLabel, '8.5 (12)');

  const zeroCount = labels.resolveRatingSummary({ averageRating: null, ratingCount: 0, rating: 0 });
  assert.equal(zeroCount.hasRating, false);
  assert.equal(zeroCount.label, 'Chưa có điểm');

  const missingFields = labels.resolveRatingSummary({ rating: 0 });
  assert.equal(missingFields.hasRating, false);
  assert.equal(missingFields.label, 'Chưa có điểm');

  const legacyOnlyRating = labels.resolveRatingSummary({ rating: 7.5 });
  assert.equal(legacyOnlyRating.hasRating, false);
  assert.equal(legacyOnlyRating.label, 'Chưa có điểm');
});

test('MovieCard, MovieDetail, SeriesCard, and FilmGrid display averageRating/ratingCount and never show 0 / 10 or ★ 0', () => {
  const mediaRouting = loadTsModule('../lib/services/mediaRouting.ts', {});
  const utils = loadTsModule('../features/series/seriesBrowseUtils.ts', {
    require: (name) => (name === '@/lib/labels' ? labels : {}),
  });
  const baseWatchlist = {
    isWatchlisted: () => false,
    toggleWatchlist: () => {},
    isFavorite: () => false,
    toggleFavorite: () => {},
    getRating: () => undefined,
    rateMovie: () => {},
    removeRating: () => {},
    isActionPending: () => false,
    isActionUnconfirmed: () => false,
    getActionIntent: () => null,
    getUnconfirmedRating: () => undefined,
    openChat: () => {},
  };

  const renderCard = (movie) => {
    const h = makeReact();
    const mod = loadTsModule('../features/movies/components/MovieCard.tsx', {
      require: (name) => {
        if (name === '@/lib/labels') return labels;
        if (name === 'react') return { __esModule: true, default: h.react, ...h.react };
        if (name === 'react-router') return { Link: 'route-link' };
        if (name === 'lucide-react') return new Proxy({}, { get: (_, k) => String(k) });
        if (name.includes('mediaRouting')) return mediaRouting;
        if (name.includes('WatchlistContext')) return { useWatchlist: () => baseWatchlist };
        return {};
      },
    });
    h.reset();
    return getText(mod.MovieCard({ movie, fluid: true }));
  };

  const unratedCardText = renderCard({
    id: 'm1',
    title: 'Heo Peppa',
    year: 2004,
    rating: 0,
    averageRating: null,
    ratingCount: 0,
    runtime: 'Series',
    genres: ['family'],
    description: '',
    poster: '',
    backdrop: '',
  });
  assert.ok(unratedCardText.includes('Chưa có điểm'));
  assert.doesNotMatch(unratedCardText, /★\s*0|0\s*\/\s*10/);

  const ratedCardText = renderCard({
    id: 'm2',
    title: 'Doraemon',
    year: 2005,
    rating: 0,
    averageRating: 9.2,
    ratingCount: 4,
    runtime: '22m',
    genres: ['family'],
    description: '',
    poster: '',
    backdrop: '',
  });
  assert.ok(ratedCardText.includes('9.2 (4)'));

  const detailH = makeReact();
  const detailMod = loadTsModule('../features/movies/components/MovieDetail.tsx', {
    require: (name) => {
      if (name === '@/lib/labels') return labels;
      if (name === 'react') return { __esModule: true, default: detailH.react, ...detailH.react };
      if (name === 'react-router') return { Link: 'route-link' };
      if (name === 'lucide-react') return new Proxy({}, { get: (_, k) => String(k) });
      if (name === '@/lib/context/AuthContext') return { useAuth: () => ({ status: 'guest', user: null }) };
      if (name.includes('WatchlistContext')) return { useWatchlist: () => baseWatchlist };
      return {};
    },
  });
  detailH.reset();
  const unratedDetailText = getText(
    detailMod.MovieDetail({
      movie: {
        id: 'm1',
        title: 'Heo Peppa',
        year: 2004,
        rating: 0,
        runtime: 'Series',
        genres: ['family'],
        description: 'Mô tả',
        poster: '',
        backdrop: '',
      },
      similarMovies: [],
    })
  );
  assert.ok(unratedDetailText.includes('Chưa có điểm'));
  assert.doesNotMatch(unratedDetailText, /0\s*\/\s*10/);

  const seriesH = makeReact();
  const seriesMod = loadTsModule('../features/series/components/SeriesCard.tsx', {
    require: (name) => {
      if (name === '@/lib/labels') return labels;
      if (name === 'react') return { __esModule: true, default: seriesH.react, ...seriesH.react };
      if (name === 'react-router') return { Link: 'route-link' };
      if (name === 'lucide-react') return { Play: 'PlayIcon' };
      if (name.includes('ArtworkImage')) return { ArtworkImage: 'ArtworkImage' };
      if (name.includes('seriesBrowseUtils')) return utils;
      return {};
    },
  });
  seriesH.reset();
  const seriesRatedText = getText(
    seriesMod.SeriesCard({
      card: {
        id: 'ser_peppa',
        title: 'Heo Peppa',
        originalTitle: 'Peppa Pig',
        synopsis: 'Vui nhộn',
        releaseYearStart: 2004,
        releaseYearEnd: null,
        genres: [{ id: 'family', label: 'Gia đình' }],
        seasonCount: 2,
        episodeCount: 51,
        posterUrl: null,
        backdropUrl: null,
        averageRating: 8.8,
        ratingCount: 5,
      },
    })
  );
  assert.ok(seriesRatedText.includes('8.8/10 (5 lượt chấm)'));
});

test('Rating or removing a rating invalidates series card cache, emits dippie:rating-changed, and immediately refreshes series banner, episode header, cards, movie detail, and profile', async () => {
  const listeners = new Map();
  const dispatched = [];
  const fakeWindow = {
    localStorage: { getItem: () => null, setItem() {}, removeItem() {} },
    location: { href: 'http://localhost:3000/series/ser_larva?episode=ep_1' },
    history: { replaceState() {} },
    addEventListener(type, fn) {
      if (!listeners.has(type)) listeners.set(type, new Set());
      listeners.get(type).add(fn);
    },
    removeEventListener(type, fn) {
      listeners.get(type)?.delete(fn);
    },
    dispatchEvent(event) {
      dispatched.push(event);
      for (const fn of [...(listeners.get(event.type) || [])]) {
        fn(event);
      }
      return true;
    },
    setTimeout,
    clearTimeout,
  };

  let clearCardsCalls = 0;
  const sameDeps = (a, b) => a && b && a.length === b.length && a.every((x, i) => Object.is(x, b[i]));
  const makeStatefulReact = () => {
    const slots = [];
    let cursor = 0;
    let effects = [];
    const react = {
      Fragment: 'fragment',
      createContext: () => ({ Provider: 'Provider' }),
      useContext: () => null,
      useState(init) {
        const i = cursor++;
        if (!(i in slots)) slots[i] = { v: typeof init === 'function' ? init() : init };
        return [
          slots[i].v,
          (next) => {
            if (!slots[i]) return;
            slots[i].v = typeof next === 'function' ? next(slots[i].v) : next;
          },
        ];
      },
      useRef(init) {
        const i = cursor++;
        return (slots[i] ||= { current: init });
      },
      useMemo(factory, deps) {
        const i = cursor++;
        if (!slots[i] || !sameDeps(slots[i].deps, deps)) slots[i] = { v: factory(), deps };
        return slots[i].v;
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
      createElement(type, props, ...children) {
        if (shouldInlineComponent(type)) return type(props || {});
        const flat = children.length === 1 ? children[0] : children.length > 1 ? children : undefined;
        return { type, props: { ...props, ...(flat !== undefined ? { children: flat } : {}) } };
      },
    };
    return {
      react,
      render(fn) {
        cursor = 0;
        const tree = fn();
        const pending = effects;
        effects = [];
        pending.forEach((run) => run());
        return tree;
      },
      unmount() {
        slots.forEach((slot) => slot?.cleanup?.());
        slots.length = 0;
        effects = [];
      },
    };
  };

  const wlH = makeStatefulReact();
  const okJson = (data) => ({ ok: true, status: 200, json: async () => ({ success: true, data }) });
  const wlMod = loadTsModule('../lib/context/WatchlistContext.tsx', {
    window: fakeWindow,
    localStorage: fakeWindow.localStorage,
    CustomEvent,
    Event,
    DOMException,
    setTimeout: () => 1,
    clearTimeout: () => {},
    fetch: async () => okJson([]),
    require: (name) => {
      if (name === 'react') return { __esModule: true, default: wlH.react, ...wlH.react, lazy: () => () => null };
      if (name === './AuthContext') {
        return {
          useAuth: () => ({
            user: { id: 1, username: 'u', email: 'u@example.test', role: 'ROLE_USER' },
            token: 'jwt-1',
            status: 'authenticated',
            isAuthenticated: true,
            handleSessionExpired() {},
            isAuthModalOpen: false,
          }),
        };
      }
      if (name.includes('seriesCardService')) {
        return {
          clearSeriesCardsCache() {
            clearCardsCalls += 1;
          },
        };
      }
      return {};
    },
  });

  let wlValue = wlH.render(() => wlMod.WatchlistProvider({ children: null })).props.value;
  await new Promise((r) => setImmediate(r));
  wlValue = wlH.render(() => wlMod.WatchlistProvider({ children: null })).props.value;

  let currentSeriesRating = { averageRating: null, ratingCount: 0 };
  let currentEpisodeRating = { averageRating: null, ratingCount: 0 };
  let episodeDetailFetches = 0;
  let seriesCardForceRefreshes = 0;
  const pageH = makeStatefulReact();
  const playbackSource = loadTsModule('../lib/services/playbackSource.ts', {});
  const mediaRouting = loadTsModule('../lib/services/mediaRouting.ts', {});
  const episodeNavigation = loadTsModule('../lib/services/episodeNavigation.ts', {});
  const browseUtils = loadTsModule('../features/series/seriesBrowseUtils.ts', {
    require: (name) => (name === '@/lib/labels' ? labels : {}),
  });
  const bannerMod = loadTsModule('../features/series/components/SeriesDetailBanner.tsx', {
    require: (name) => {
      if (name === 'react') return { __esModule: true, default: pageH.react, ...pageH.react };
      if (name === 'react-router') return { Link: 'route-link' };
      if (name === '@/lib/labels') return labels;
      if (name.includes('seriesBrowseUtils')) return browseUtils;
      return {};
    },
  });
  const ep = { id: 'ep_1', seriesId: 'ser_larva', seasonId: 's1', number: 1, title: 'Kem', durationMs: 180000 };
  const pageMod = loadTsModule('../app/series/[id]/page.tsx', {
    window: fakeWindow,
    URL,
    document: { title: '', hidden: false, addEventListener() {}, removeEventListener() {} },
    require: (name) => {
      if (name === 'react') return { __esModule: true, default: pageH.react, ...pageH.react };
      if (name === 'react-router') {
        return {
          Link: 'route-link',
          useParams: () => ({ id: 'ser_larva' }),
          useSearchParams: () => [new URLSearchParams('episode=ep_1')],
        };
      }
      if (name === '@/lib/labels') return labels;
      if (name.includes('WatchlistContext')) return { RATING_CHANGED_EVENT: wlMod.RATING_CHANGED_EVENT };
      if (name.includes('seriesBrowseUtils')) return browseUtils;
      if (name.includes('playbackSource')) return playbackSource;
      if (name.includes('mediaRouting')) return mediaRouting;
      if (name.includes('episodeNavigation')) return episodeNavigation;
      if (name.includes('mediaService')) return { resolveStreamUrl: (u) => u, getStoredAuthToken: () => null };
      if (name.includes('useWatchProgress')) return { useWatchProgress: () => ({ records: [], saveProgress() {} }) };
      if (name.includes('watchProgressService')) {
        return {
          readAutoNextPreference: () => false,
          writeAutoNextPreference() {},
          isUsableResumeProgress: () => false,
          toYoutubeAssetId: (id) => `youtube:${id}`,
          isYoutubeAssetId: (id) => Boolean(id?.startsWith('youtube:')),
        };
      }
      if (name.includes('catalogService')) {
        return {
          getCatalogSeriesDetail: async () => ({
            series: { id: 'ser_larva', title: 'Larva' },
            seasons: [{ id: 's1', seriesId: 'ser_larva', number: 1, title: 'Mùa 1' }],
            episodes: [ep],
          }),
          getCatalogEpisodeDetail: async () => {
            episodeDetailFetches += 1;
            return {
              episode: ep,
              segments: [],
              mediaAssets: [],
              scenes: [],
              youtube: { videoId: '11111111111', canEmbed: true },
            };
          },
          getCatalogMovieById: async (id) => ({
            id,
            title: 'Kem',
            year: 2011,
            rating: 0,
            ...currentEpisodeRating,
            runtime: '3m',
            genres: ['comedy'],
            description: '',
            poster: '',
            backdrop: '',
          }),
        };
      }
      if (name.includes('seriesCardService')) {
        return {
          getSeriesCardById: async (_id, opts) => {
            if (opts?.forceRefresh) seriesCardForceRefreshes += 1;
            return {
              id: 'ser_larva',
              title: 'Larva',
              originalTitle: 'Larva',
              synopsis: null,
              releaseYearStart: 2011,
              releaseYearEnd: null,
              genres: [{ id: 'comedy', label: 'Hài hước' }],
              seasonCount: 1,
              episodeCount: 1,
              posterUrl: null,
              backdropUrl: null,
              ...currentSeriesRating,
            };
          },
        };
      }
      if (name.includes('SeriesDetailBanner')) return bannerMod;
      if (name.includes('NextEpisodeAction')) return { NextEpisodeAction: 'NextEpisodeAction' };
      if (name.includes('LibraryControls')) return { ItemLibraryPanel: 'ItemLibraryPanel', QuickSaveButtons: 'QuickSaveButtons' };
      if (name.includes('YouTubePlayer')) return { YouTubePlayer: 'YouTubePlayer' };
      return {};
    },
  });

  const findNodes = (node, pred, acc = []) => {
    if (!node || typeof node !== 'object') return acc;
    if (Array.isArray(node)) {
      node.forEach((child) => findNodes(child, pred, acc));
      return acc;
    }
    if (pred(node)) acc.push(node);
    findNodes(node.props?.children, pred, acc);
    return acc;
  };

  const settlePage = async () => {
    let tree;
    for (let i = 0; i < 6; i++) {
      tree = pageH.render(() => pageMod.default());
      await new Promise((r) => setImmediate(r));
    }
    return pageH.render(() => pageMod.default());
  };

  let pageTree = await settlePage();
  const initialBanner = findNodes(pageTree, (n) => n.type === bannerMod.SeriesDetailBanner)[0];
  assert.equal(initialBanner.props.card.ratingCount, 0);
  const initialEpRating = findNodes(pageTree, (n) => n.props?.['data-testid'] === 'episode-header-rating')[0];
  assert.equal(getText(initialEpRating), 'Chưa có điểm');
  assert.equal(episodeDetailFetches, 1);

  let profileMovieFetches = 0;
  let profileRating = { averageRating: null, ratingCount: 0 };
  const profH = makeStatefulReact();
  const profMod = loadTsModule('../features/profile/components/ProfileOverview.tsx', {
    window: fakeWindow,
    require: (name) => {
      if (name === 'react') return { __esModule: true, default: profH.react, ...profH.react };
      if (name === 'react-router') return { Link: 'route-link' };
      if (name === 'lucide-react') return new Proxy({}, { get: () => () => null });
      if (name === '@/lib/labels') return labels;
      if (name.includes('MovieCard')) return { MovieCard: 'MovieCard' };
      if (name.includes('WatchlistContext')) {
        return {
          RATING_CHANGED_EVENT: wlMod.RATING_CHANGED_EVENT,
          useWatchlist: () => ({
            watchlist: ['ser_larva'],
            favorites: [],
            ratings: { ser_larva: 8 },
            openChat() {},
            isLibraryLoading: false,
            libraryError: null,
            refetchLibrary() {},
            isGuestMode: false,
            toggleWatchlist() {},
            toggleFavorite() {},
            rateMovie() {},
            removeRating() {},
            isActionPending: () => false,
            isActionUnconfirmed: () => false,
            getActionIntent: () => null,
            getUnconfirmedRating: () => undefined,
            showToast() {},
          }),
        };
      }
      if (name.includes('AuthContext')) {
        return { useAuth: () => ({ user: { id: 1, username: 'u' }, status: 'authenticated', openAuthModal() {}, retryVerify() {} }) };
      }
      if (name.includes('catalogService')) {
        return {
          getCatalogMovieById: async (id) => {
            profileMovieFetches += 1;
            return {
              id,
              title: 'Larva',
              year: 2011,
              rating: 0,
              ...profileRating,
              runtime: 'Series',
              genres: ['comedy'],
              description: '',
              poster: '',
              backdrop: '',
            };
          },
        };
      }
      return {};
    },
  });

  profMod.clearProfileMetadataCache();
  let profTree = profH.render(() => profMod.ProfileOverview());
  await new Promise((r) => setImmediate(r));
  profTree = profH.render(() => profMod.ProfileOverview());
  assert.equal(profileMovieFetches, 1);
  assert.equal(findNodes(profTree, (n) => n.type === 'MovieCard')[0].props.movie.ratingCount, 0);

  let rowForceRefreshes = 0;
  const rowH = makeStatefulReact();
  const rowMod = loadTsModule('../features/series/components/SeriesRow.tsx', {
    window: fakeWindow,
    require: (name) => {
      if (name === 'react') return { __esModule: true, default: rowH.react, ...rowH.react };
      if (name === 'react-router') return { Link: 'route-link' };
      if (name === 'lucide-react') return { ChevronLeft: 'L', ChevronRight: 'R' };
      if (name.includes('WatchlistContext')) return { RATING_CHANGED_EVENT: wlMod.RATING_CHANGED_EVENT };
      if (name.includes('SeriesCard')) return { SeriesCard: 'SeriesCard' };
      if (name.includes('seriesCardService')) {
        return {
          listSeriesCards: async (opts) => {
            if (opts?.forceRefresh) rowForceRefreshes += 1;
            return [
              {
                id: 'ser_larva',
                title: 'Larva',
                originalTitle: null,
                synopsis: null,
                releaseYearStart: 2011,
                releaseYearEnd: null,
                genres: [],
                seasonCount: 1,
                episodeCount: 1,
                posterUrl: null,
                backdropUrl: null,
                ...currentSeriesRating,
              },
            ];
          },
        };
      }
      return {};
    },
  });
  rowH.render(() => rowMod.SeriesRow({}));
  await new Promise((r) => setImmediate(r));
  let rowTree = rowH.render(() => rowMod.SeriesRow({}));
  assert.equal(findNodes(rowTree, (n) => n.type === 'SeriesCard')[0].props.card.ratingCount, 0);

  currentSeriesRating = { averageRating: 8.0, ratingCount: 1 };
  profileRating = { averageRating: 8.0, ratingCount: 1 };
  await wlValue.rateMovie('ser_larva', 8, 'Larva');
  await new Promise((r) => setImmediate(r));
  await new Promise((r) => setImmediate(r));

  assert.equal(clearCardsCalls, 1);
  assert.equal(seriesCardForceRefreshes, 1);
  pageTree = pageH.render(() => pageMod.default());
  const updatedBanner = findNodes(pageTree, (n) => n.type === bannerMod.SeriesDetailBanner)[0];
  assert.equal(updatedBanner.props.card.averageRating, 8.0);
  assert.equal(updatedBanner.props.card.ratingCount, 1);

  rowTree = rowH.render(() => rowMod.SeriesRow({}));
  await new Promise((r) => setImmediate(r));
  rowTree = rowH.render(() => rowMod.SeriesRow({}));
  assert.equal(rowForceRefreshes, 1);
  assert.equal(findNodes(rowTree, (n) => n.type === 'SeriesCard')[0].props.card.averageRating, 8.0);

  profTree = profH.render(() => profMod.ProfileOverview());
  assert.equal(profileMovieFetches, 2);
  assert.equal(findNodes(profTree, (n) => n.type === 'MovieCard')[0].props.movie.averageRating, 8.0);

  currentEpisodeRating = { averageRating: 9.0, ratingCount: 1 };
  await wlValue.rateMovie('ep_1', 9, 'Kem');
  await new Promise((r) => setImmediate(r));
  await new Promise((r) => setImmediate(r));

  pageTree = pageH.render(() => pageMod.default());
  const updatedEpRating = findNodes(pageTree, (n) => n.props?.['data-testid'] === 'episode-header-rating')[0];
  assert.equal(getText(updatedEpRating), '★ 9.0/10 (1 lượt chấm)');
  assert.equal(episodeDetailFetches, 1, 'Rating an episode must refresh rating without reloading playback');

  currentEpisodeRating = { averageRating: null, ratingCount: 0 };
  await wlValue.removeRating('ep_1', 'Kem');
  await new Promise((r) => setImmediate(r));
  await new Promise((r) => setImmediate(r));

  pageTree = pageH.render(() => pageMod.default());
  const removedEpRating = findNodes(pageTree, (n) => n.props?.['data-testid'] === 'episode-header-rating')[0];
  assert.equal(getText(removedEpRating), 'Chưa có điểm');

  profH.unmount();
  profileRating = { averageRating: null, ratingCount: 0 };
  await wlValue.removeRating('ser_larva', 'Larva');
  await new Promise((r) => setImmediate(r));
  profH.render(() => profMod.ProfileOverview());
  await new Promise((r) => setImmediate(r));
  const remountedProf = profH.render(() => profMod.ProfileOverview());
  assert.equal(profileMovieFetches, 3, 'Unmounted profile cache must be invalidated by rating removal');
  assert.equal(findNodes(remountedProf, (n) => n.type === 'MovieCard')[0].props.movie.ratingCount, 0);

  let moviePageRating = { averageRating: null, ratingCount: 0 };
  const mpH = makeStatefulReact();
  const mpMod = loadTsModule('../app/movies/[id]/page.tsx', {
    window: fakeWindow,
    document: { title: '' },
    require: (name) => {
      if (name === 'react') return { __esModule: true, default: mpH.react, ...mpH.react };
      if (name === 'react-router') return { Link: 'route-link', useParams: () => ({ id: 'ser_larva' }) };
      if (name === '@/lib/labels') return labels;
      if (name.includes('WatchlistContext')) return { RATING_CHANGED_EVENT: wlMod.RATING_CHANGED_EVENT };
      if (name.includes('MovieDetail')) return { MovieDetail: 'MovieDetail' };
      if (name.includes('catalogService')) {
        return {
          getCatalogMovieById: async (id) => ({
            id,
            title: 'Larva',
            year: 2011,
            rating: 0,
            ...moviePageRating,
            runtime: 'Series',
            genres: ['comedy'],
            description: '',
            poster: '',
            backdrop: '',
          }),
          browseCatalog: async () => ({ items: [] }),
        };
      }
      return {};
    },
  });
  mpH.render(() => mpMod.default());
  await new Promise((r) => setImmediate(r));
  let mpTree = mpH.render(() => mpMod.default());
  assert.equal(findNodes(mpTree, (n) => n.type === 'MovieDetail')[0].props.movie.ratingCount, 0);

  moviePageRating = { averageRating: 7.5, ratingCount: 2 };
  await wlValue.rateMovie('ser_larva', 7, 'Larva');
  await new Promise((r) => setImmediate(r));
  mpTree = mpH.render(() => mpMod.default());
  assert.equal(findNodes(mpTree, (n) => n.type === 'MovieDetail')[0].props.movie.averageRating, 7.5);
  assert.equal(findNodes(mpTree, (n) => n.type === 'MovieDetail')[0].props.movie.ratingCount, 2);

  pageH.unmount();
  rowH.unmount();
  profH.unmount();
  mpH.unmount();
  wlH.unmount();
});
