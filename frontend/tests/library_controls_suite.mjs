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
    URL,
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

function findNodes(node, predicate, acc = []) {
  if (!node) return acc;
  if (Array.isArray(node)) {
    for (const child of node) findNodes(child, predicate, acc);
    return acc;
  }
  if (typeof node === 'object') {
    if (predicate(node)) acc.push(node);
    findNodes(node.props?.children, predicate, acc);
  }
  return acc;
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
    createElement(type, props, ...children) {
      if (shouldInlineComponent(type)) return type(props || {});
      const flat = children.length === 1 ? children[0] : children.length > 1 ? children : undefined;
      return { type, props: { ...props, ...(flat !== undefined ? { children: flat } : {}) } };
    },
  };
  return {
    react,
    reset() {
      cursor = 0;
    },
    flush() {
      const pending = effects;
      effects = [];
      for (const fn of pending) fn();
    },
  };
}

function makeStatefulWatchlist() {
  const watchlist = new Set();
  const favorites = new Set();
  const ratings = new Map();
  const calls = [];
  return {
    watchlist,
    favorites,
    ratings,
    calls,
    context: {
      isWatchlisted: (id) => watchlist.has(id),
      toggleWatchlist: async (id, title, poster) => {
        calls.push({ type: 'watchlist', id, title, poster });
        if (watchlist.has(id)) watchlist.delete(id);
        else watchlist.add(id);
        return true;
      },
      isFavorite: (id) => favorites.has(id),
      toggleFavorite: async (id, title, poster) => {
        calls.push({ type: 'favorite', id, title, poster });
        if (favorites.has(id)) favorites.delete(id);
        else favorites.add(id);
        return true;
      },
      getRating: (id) => ratings.get(id),
      rateMovie: async (id, score, title) => {
        calls.push({ type: 'rate', id, score, title });
        ratings.set(id, score);
        return true;
      },
      removeRating: async (id, title) => {
        calls.push({ type: 'removeRating', id, title });
        ratings.delete(id);
        return true;
      },
      isActionPending: () => false,
      isActionUnconfirmed: () => false,
      getActionIntent: () => null,
      getUnconfirmedRating: () => undefined,
      openChat: () => {},
    },
  };
}

test('ItemLibraryPanel supports watch later, favorite, and 1-10 rating set, change, and remove', async () => {
  const h = makeReact();
  const store = makeStatefulWatchlist();
  const mod = loadTsModule('../features/library/components/LibraryControls.tsx', {
    require: (name) => {
      if (name === 'react') return { __esModule: true, default: h.react, ...h.react };
      if (name === 'lucide-react') return new Proxy({}, { get: (_, k) => String(k) });
      if (name.includes('WatchlistContext')) return { useWatchlist: () => store.context };
      return {};
    },
  });

  const render = () => {
    h.reset();
    return mod.ItemLibraryPanel({
      itemId: 'ep_peppa_1',
      title: 'Tập 1',
      poster: 'https://img/p.jpg',
      scopeLabel: 'Tập đang xem',
      testIdPrefix: 'episode',
    });
  };

  let tree = render();
  const wlBtn = findNodes(tree, (n) => n.props?.['data-testid'] === 'episode-watchlist-toggle')[0];
  const favBtn = findNodes(tree, (n) => n.props?.['data-testid'] === 'episode-favorite-toggle')[0];
  assert.equal(wlBtn.props['aria-pressed'], false);
  assert.equal(favBtn.props['aria-pressed'], false);

  await wlBtn.props.onClick();
  await favBtn.props.onClick();
  tree = render();
  assert.equal(findNodes(tree, (n) => n.props?.['data-testid'] === 'episode-watchlist-toggle')[0].props['aria-pressed'], true);
  assert.equal(findNodes(tree, (n) => n.props?.['data-testid'] === 'episode-favorite-toggle')[0].props['aria-pressed'], true);

  const score8 = findNodes(tree, (n) => n.props?.['data-testid'] === 'episode-rate-8')[0];
  assert.ok(score8.props.className.includes('min-h-[44px]') && score8.props.className.includes('min-w-[44px]'));
  await score8.props.onClick();
  tree = render();
  assert.equal(store.ratings.get('ep_peppa_1'), 8);
  assert.equal(findNodes(tree, (n) => n.props?.['data-testid'] === 'episode-rate-8')[0].props['aria-pressed'], true);

  const score10 = findNodes(tree, (n) => n.props?.['data-testid'] === 'episode-rate-10')[0];
  await score10.props.onClick();
  tree = render();
  assert.equal(store.ratings.get('ep_peppa_1'), 10);

  const removeBtn = findNodes(tree, (n) => n.props?.['data-testid'] === 'episode-rating-remove')[0];
  assert.ok(removeBtn);
  await removeBtn.props.onClick();
  tree = render();
  assert.equal(store.ratings.has('ep_peppa_1'), false);
  assert.equal(findNodes(tree, (n) => n.props?.['data-testid'] === 'episode-rating-remove').length, 0);
});

test('QuickSaveButtons stops propagation and toggles watchlist and favorite cleanly', async () => {
  const h = makeReact();
  const store = makeStatefulWatchlist();
  const mod = loadTsModule('../features/library/components/LibraryControls.tsx', {
    require: (name) => {
      if (name === 'react') return { __esModule: true, default: h.react, ...h.react };
      if (name === 'lucide-react') return new Proxy({}, { get: (_, k) => String(k) });
      if (name.includes('WatchlistContext')) return { useWatchlist: () => store.context };
      return {};
    },
  });

  const render = () => {
    h.reset();
    return mod.QuickSaveButtons({
      itemId: 'ser_jadoo',
      title: 'Xin Chào Jadoo',
      poster: 'https://img/jadoo.jpg',
      testIdPrefix: 'card',
    });
  };

  let stopped = 0;
  let prevented = 0;
  const fakeEvent = {
    preventDefault: () => { prevented++; },
    stopPropagation: () => { stopped++; },
  };

  let tree = render();
  await findNodes(tree, (n) => n.props?.['data-testid'] === 'card-quick-watchlist')[0].props.onClick(fakeEvent);
  await findNodes(tree, (n) => n.props?.['data-testid'] === 'card-quick-favorite')[0].props.onClick(fakeEvent);
  assert.equal(stopped, 2);
  assert.equal(prevented, 2);
  assert.equal(store.watchlist.has('ser_jadoo'), true);
  assert.equal(store.favorites.has('ser_jadoo'), true);
});

test('SeriesDetailPage wires ItemLibraryPanel for both series and selected episode and QuickSaveButtons on episode rows', async () => {
  const h = makeReact();
  const playbackSource = loadTsModule('../lib/services/playbackSource.ts', {});
  const mediaRouting = loadTsModule('../lib/services/mediaRouting.ts', {});
  const episode = { id: 'ep_1', title: 'Tập 1', seasonId: 's1', number: 1, durationMs: 300000 };
  const pageMod = loadTsModule('../app/series/[id]/page.tsx', {
    window: { location: { href: 'http://localhost/series/ser_1' }, history: { replaceState() {} }, setTimeout, clearTimeout },
    document: { hidden: false, addEventListener() {}, removeEventListener() {} },
    require: (name) => {
      if (name === '@/lib/labels') return labels;
      if (name === 'react') return { __esModule: true, default: h.react, ...h.react };
      if (name === 'react-router') return { Link: 'route-link', useParams: () => ({ id: 'ser_1' }), useSearchParams: () => [new URLSearchParams()] };
      if (name.includes('catalogService')) {
        return {
          getCatalogSeriesDetail: async () => ({
            series: { id: 'ser_1', title: 'Heo Peppa' },
            seasons: [{ id: 's1', number: 1, title: 'Mùa 1' }],
            episodes: [episode],
          }),
          getCatalogEpisodeDetail: async () => ({ episode, mediaAssets: [], scenes: [], segments: [], youtube: { videoId: '11111111111', canEmbed: true } }),
        };
      }
      if (name.includes('episodeNavigation')) {
        return {
          sortCatalogSeasons: (items) => items,
          getInitialSeasonId: () => 's1',
          getInitialEpisode: () => episode,
          getEpisodesForSeason: (items) => items,
          getAdjacentEpisode: () => null,
          formatAdjacentEpisodeLabel: () => '',
        };
      }
      if (name.includes('mediaService')) return { resolveStreamUrl: (u) => u, getStoredAuthToken: () => null };
      if (name.includes('mediaRouting')) return mediaRouting;
      if (name.includes('playbackSource')) return playbackSource;
      if (name.includes('useWatchProgress')) return { useWatchProgress: () => ({ records: [], saveProgress: async () => null }) };
      if (name.includes('watchProgressService')) {
        return {
          readAutoNextPreference: () => false,
          writeAutoNextPreference() {},
          isUsableResumeProgress: () => false,
          isYoutubeAssetId: (id) => typeof id === 'string' && id.startsWith('youtube:'),
          toYoutubeAssetId: (id) => `youtube:${id}`,
        };
      }
      if (name.includes('SeriesDetailBanner')) return { SeriesDetailBanner: 'SeriesDetailBanner' };
      if (name.includes('NextEpisodeAction')) return { NextEpisodeAction: 'NextEpisodeAction' };
      if (name.includes('LibraryControls')) return { ItemLibraryPanel: 'ItemLibraryPanel', QuickSaveButtons: 'QuickSaveButtons' };
      return {};
    },
  });

  let tree;
  for (let i = 0; i < 6; i++) {
    h.reset();
    tree = pageMod.default();
    h.flush();
    await new Promise(setImmediate);
  }
  h.reset();
  tree = pageMod.default();

  const banner = findNodes(tree, (n) => n.type === 'SeriesDetailBanner')[0];
  assert.equal(banner.props.seriesActions.type, 'ItemLibraryPanel');
  assert.equal(banner.props.seriesActions.props.itemId, 'ser_1');
  assert.equal(banner.props.seriesActions.props.testIdPrefix, 'series');

  const episodePanel = findNodes(tree, (n) => n.type === 'ItemLibraryPanel' && n.props?.testIdPrefix === 'episode')[0];
  assert.ok(episodePanel);
  assert.equal(episodePanel.props.itemId, 'ep_1');

  const rowQuickSave = findNodes(tree, (n) => n.type === 'QuickSaveButtons' && n.props?.itemId === 'ep_1')[0];
  assert.ok(rowQuickSave);
});

test('MovieCard actionMode="favorite" removes favorites and ProfileOverview uses it with accurate empty-state copy', async () => {
  const h = makeReact();
  const store = makeStatefulWatchlist();
  store.favorites.add('ser_peppa');
  const mediaRouting = loadTsModule('../lib/services/mediaRouting.ts', {});

  const cardMod = loadTsModule('../features/movies/components/MovieCard.tsx', {
    require: (name) => {
      if (name === '@/lib/labels') return labels;
      if (name === 'react') return { __esModule: true, default: h.react, ...h.react };
      if (name === 'react-router') return { Link: 'route-link' };
      if (name === 'lucide-react') return new Proxy({}, { get: (_, k) => String(k) });
      if (name.includes('mediaRouting')) return mediaRouting;
      if (name.includes('WatchlistContext')) return { useWatchlist: () => store.context };
      return {};
    },
  });

  h.reset();
  const cardTree = cardMod.MovieCard({
    movie: { id: 'ser_peppa', title: 'Heo Peppa', year: 2004, rating: 0, runtime: 'Series', genres: ['Family'], description: '', poster: '', backdrop: '' },
    fluid: true,
    actionMode: 'favorite',
  });
  const favBtn = findNodes(cardTree, (n) => n.props?.['data-testid'] === 'movie-card-favorite-btn')[0];
  assert.ok(favBtn);
  assert.match(favBtn.props['aria-label'], /Bỏ Heo Peppa khỏi Yêu thích/);
  favBtn.props.onClick({ preventDefault() {}, stopPropagation() {} });
  assert.equal(store.favorites.has('ser_peppa'), false);

  const ph = makeReact();
  const profileMod = loadTsModule('../features/profile/components/ProfileOverview.tsx', {
    require: (name) => {
      if (name === '@/lib/labels') return labels;
      if (name === 'react') return { __esModule: true, default: ph.react, ...ph.react };
      if (name === 'react-router') return { Link: 'route-link' };
      if (name === 'lucide-react') return new Proxy({}, { get: (_, k) => String(k) });
      if (name.includes('MovieCard')) return { MovieCard: 'MovieCard' };
      if (name.includes('WatchlistContext')) {
        return {
          useWatchlist: () => ({
            watchlist: [],
            favorites: ['ser_peppa'],
            ratings: {},
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
          getCatalogMovieById: async () => ({
            id: 'ser_peppa',
            title: 'Heo Peppa',
            year: 2004,
            rating: 0,
            runtime: 'Series',
            genres: ['Family'],
            description: '',
            poster: '',
            backdrop: '',
          }),
        };
      }
      return {};
    },
  });

  profileMod.clearProfileMetadataCache();
  let tree;
  for (let i = 0; i < 6; i++) {
    ph.reset();
    tree = profileMod.ProfileOverview();
    ph.flush();
    await new Promise(setImmediate);
  }
  const emptyState = findNodes(tree, (n) => typeof n.type === 'function' && n.type.name === 'EmptyState')[0];
  assert.ok(emptyState);
  assert.doesNotMatch(emptyState.props.description, /nút Bookmark/);
  assert.match(emptyState.props.description, /nút Xem sau/);

  const favTabBtn = findNodes(tree, (n) => n.type === 'button' && getText(n).includes('Yêu thích'))[0];
  favTabBtn.props.onClick();
  ph.reset();
  tree = profileMod.ProfileOverview();
  const favCard = findNodes(tree, (n) => n.type === 'MovieCard')[0];
  assert.equal(favCard.props.actionMode, 'favorite');
});
