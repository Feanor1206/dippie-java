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
    DOMException,
    URLSearchParams,
    Date,
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

function makeReactHarness() {
  const slots = [];
  let cursor = 0;
  let effects = [];
  let dirty = false;
  const sameDeps = (a, b) => a && b && a.length === b.length && a.every((v, i) => Object.is(v, b[i]));
  const react = {
    useState(initial) {
      const i = cursor++;
      if (!(i in slots)) slots[i] = { value: typeof initial === 'function' ? initial() : initial };
      return [
        slots[i].value,
        (next) => {
          slots[i].value = typeof next === 'function' ? next(slots[i].value) : next;
          dirty = true;
        },
      ];
    },
    useRef(initial) {
      const i = cursor++;
      return (slots[i] ||= { current: initial });
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
    resetCursor() {
      cursor = 0;
      dirty = false;
    },
    flushEffects() {
      const pending = effects;
      effects = [];
      for (const fn of pending) fn();
    },
    isDirty() {
      return dirty;
    },
    unmount() {
      for (const slot of slots) slot?.cleanup?.();
    },
  };
}

const utils = loadTsModule('../features/series/seriesBrowseUtils.ts', {
  require: (name) => (name === '@/lib/labels' ? labels : {}),
});

const sampleCards = [
  {
    id: 'ser_jadoo',
    title: 'Xin Chào Jadoo',
    originalTitle: 'Hello Jadoo',
    synopsis: 'Cô bé tinh nghịch Jadoo và gia đình.',
    releaseYearStart: 2011,
    releaseYearEnd: 2022,
    genres: [
      { id: 'comedy', label: 'Hài' },
      { id: 'family', label: 'Gia đình' },
      { id: 'animation', label: 'Hoạt hình' },
      { id: 'adventure', label: 'Phiêu lưu' },
    ],
    seasonCount: 4,
    episodeCount: 52,
    posterUrl: 'https://cdn.example.com/jadoo-poster.jpg',
    backdropUrl: 'https://cdn.example.com/jadoo-backdrop.jpg',
  },
  {
    id: 'ser_ori',
    title: 'Công chúa Ori',
    originalTitle: 'Princess Ori',
    synopsis: 'Hành trình kỳ diệu của công chúa Ori.',
    releaseYearStart: 2023,
    releaseYearEnd: null,
    genres: [
      { id: 'fantasy', label: 'Giả tưởng' },
      { id: 'adventure', label: 'Phiêu lưu' },
    ],
    seasonCount: 1,
    episodeCount: 12,
    posterUrl: 'https://cdn.example.com/ori-poster.jpg',
    backdropUrl: null,
  },
  {
    id: 'ser_larva',
    title: 'Larva',
    originalTitle: 'Ấu trùng tinh nghịch',
    synopsis: 'Hai chú ấu trùng hài hước.',
    releaseYearStart: 2015,
    releaseYearEnd: 2019,
    genres: [{ id: 'comedy', label: 'Hài' }],
    seasonCount: 3,
    episodeCount: 104,
    posterUrl: null,
    backdropUrl: null,
  },
];

test('seriesCardService parses complete cards and handles missing optional fields gracefully', async () => {
  const calls = [];
  const service = loadTsModule('../lib/services/seriesCardService.ts', {
    require: (name) => (name === '@/lib/labels' ? labels : {}),
    fetch: async (url, opts) => {
      calls.push({ url, opts });
      return {
        ok: true,
        status: 200,
        json: async () => [
          sampleCards[0],
          {
            id: 'ser_minimal',
            title: 'Bộ phim cũ',
            genres: [{ id: 'Comedy' }, { id: 'comedy', label: 'Trùng lặp' }, 'family'],
            seasonCount: -1,
            episodeCount: 'bad',
            releaseYearStart: 'invalid',
            posterUrl: '   ',
          },
        ],
      };
    },
  });

  service.clearSeriesCardsCache();
  const list = await service.listSeriesCards();
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, '/api/catalog/series/cards');
  assert.equal(list.length, 2);

  assert.equal(list[0].id, 'ser_jadoo');
  assert.equal(list[0].seasonCount, 4);
  assert.equal(list[0].episodeCount, 52);
  assert.equal(list[0].backdropUrl, 'https://cdn.example.com/jadoo-backdrop.jpg');

  assert.equal(list[1].id, 'ser_minimal');
  assert.equal(list[1].originalTitle, null);
  assert.equal(list[1].synopsis, null);
  assert.equal(list[1].releaseYearStart, null);
  assert.equal(list[1].releaseYearEnd, null);
  assert.equal(list[1].seasonCount, null);
  assert.equal(list[1].episodeCount, null);
  assert.equal(list[1].posterUrl, null);
  assert.equal(list[1].backdropUrl, null);
  assert.deepEqual(
    JSON.parse(JSON.stringify(list[1].genres)),
    [
      { id: 'Comedy', label: 'Hài hước' },
      { id: 'family', label: 'Gia đình' },
    ]
  );

  const fromCache = await service.getSeriesCardById('ser_jadoo');
  assert.equal(fromCache.title, 'Xin Chào Jadoo');
  assert.equal(calls.length, 1);

  const missing = await service.getSeriesCardById('nonexistent');
  assert.equal(missing, null);

  await service.listSeriesCards({ forceRefresh: true });
  assert.equal(calls.length, 2);
});

test('seriesCardService rejects HTTP errors, malformed payloads, aborts, and timeouts', async () => {
  const errService = loadTsModule('../lib/services/seriesCardService.ts', {
    require: (name) => (name === '@/lib/labels' ? labels : {}),
    fetch: async () => ({ ok: false, status: 503, json: async () => ({}) }),
  });
  await assert.rejects(errService.listSeriesCards({ forceRefresh: true }), (err) => err.status === 503);

  const badService = loadTsModule('../lib/services/seriesCardService.ts', {
    require: (name) => (name === '@/lib/labels' ? labels : {}),
    fetch: async () => ({ ok: true, status: 200, json: async () => ({ notAnArray: true }) }),
  });
  await assert.rejects(badService.listSeriesCards({ forceRefresh: true }), (err) => err.status === 502);

  const missingIdService = loadTsModule('../lib/services/seriesCardService.ts', {
    require: (name) => (name === '@/lib/labels' ? labels : {}),
    fetch: async () => ({ ok: true, status: 200, json: async () => [{ title: 'No ID' }] }),
  });
  await assert.rejects(missingIdService.listSeriesCards({ forceRefresh: true }), (err) => err.status === 502);

  const slowService = loadTsModule('../lib/services/seriesCardService.ts', {
    require: (name) => (name === '@/lib/labels' ? labels : {}),
    fetch: (_url, opts) =>
      new Promise((_resolve, reject) => {
        opts.signal.addEventListener('abort', () => {
          const e = new Error('aborted');
          e.name = 'AbortError';
          reject(e);
        });
      }),
  });
  await assert.rejects(slowService.listSeriesCards({ timeoutMs: 20, forceRefresh: true }), (err) => err.isTimeout === true);

  const ac = new AbortController();
  const pending = slowService.listSeriesCards({ signal: ac.signal, forceRefresh: true });
  ac.abort();
  await assert.rejects(pending, (err) => err.isCanceled === true);
});

test('filtering by search (title & originalTitle, Vietnamese accents) and multi-select genres works', () => {
  const byOriginal = utils.filterAndSortSeriesCards(sampleCards, { q: 'Princess', genres: [], sort: 'title' });
  assert.deepEqual(Array.from(byOriginal, (c) => c.id), ['ser_ori']);

  const byFoldedVi = utils.filterAndSortSeriesCards(sampleCards, { q: 'cong chua', genres: [], sort: 'title' });
  assert.deepEqual(Array.from(byFoldedVi, (c) => c.id), ['ser_ori']);

  const bySingleGenre = utils.filterAndSortSeriesCards(sampleCards, { q: '', genres: ['fantasy'], sort: 'title' });
  assert.deepEqual(Array.from(bySingleGenre, (c) => c.id), ['ser_ori']);

  const byMultiGenre = utils.filterAndSortSeriesCards(sampleCards, {
    q: '',
    genres: ['fantasy', 'family'],
    sort: 'title',
  });
  assert.deepEqual(Array.from(byMultiGenre, (c) => c.id), ['ser_ori', 'ser_jadoo']);

  const combined = utils.filterAndSortSeriesCards(sampleCards, {
    q: 'larva',
    genres: ['comedy'],
    sort: 'title',
  });
  assert.deepEqual(Array.from(combined, (c) => c.id), ['ser_larva']);
});

test('all three sort orders sort accurately', () => {
  const byTitle = utils.filterAndSortSeriesCards(sampleCards, { q: '', genres: [], sort: 'title' });
  assert.deepEqual(Array.from(byTitle, (c) => c.id), ['ser_ori', 'ser_larva', 'ser_jadoo']);

  const byNewest = utils.filterAndSortSeriesCards(sampleCards, { q: '', genres: [], sort: 'newest' });
  assert.deepEqual(Array.from(byNewest, (c) => c.id), ['ser_ori', 'ser_larva', 'ser_jadoo']);

  const byEpisodes = utils.filterAndSortSeriesCards(sampleCards, { q: '', genres: [], sort: 'episodes' });
  assert.deepEqual(Array.from(byEpisodes, (c) => c.id), ['ser_larva', 'ser_jadoo', 'ser_ori']);
});

test('URL query round-trip preserves q, multi-select genres, and sort cleanly', () => {
  const defaultParams = utils.serializeSeriesBrowseParams({ q: '', genres: [], sort: 'title' });
  assert.equal(defaultParams.toString(), '');
  assert.deepEqual(
    JSON.parse(JSON.stringify(utils.parseSeriesBrowseParams(defaultParams))),
    { q: '', genres: [], sort: 'title' }
  );

  const original = { q: 'Jadoo', genres: ['comedy', 'family'], sort: 'episodes' };
  const serialized = utils.serializeSeriesBrowseParams(original);
  const parsed = utils.parseSeriesBrowseParams(serialized.toString());
  assert.equal(parsed.q, 'Jadoo');
  assert.deepEqual(Array.from(parsed.genres), ['comedy', 'family']);
  assert.equal(parsed.sort, 'episodes');

  const legacy = utils.parseSeriesBrowseParams('genre=Comedy&genre=fantasy&sort=unknown');
  assert.deepEqual(Array.from(legacy.genres), ['Comedy', 'fantasy']);
  assert.equal(legacy.sort, 'title');
});

test('label formatting handles counts, year ranges, episode tags, and progress percentages', () => {
  assert.equal(utils.formatSeriesCounts(2, 24), '2 mùa · 24 tập');
  assert.equal(utils.formatSeriesCounts(1, null), '1 mùa');
  assert.equal(utils.formatSeriesCounts(0, 12), '12 tập');
  assert.equal(utils.formatSeriesCounts(null, null), '');

  assert.equal(utils.formatYearRange(2011, 2022), '2011 đến 2022');
  assert.equal(utils.formatYearRange(2023, 2023), '2023');
  assert.equal(utils.formatYearRange(2023, null), '2023');
  assert.equal(utils.formatYearRange(null, 2020), '2020');
  assert.equal(utils.formatYearRange(null, null), '');

  assert.equal(utils.formatEpisodeTag(5), 'Tập 5');
  assert.equal(utils.formatEpisodeTag(0), '');
  assert.equal(utils.formatEpisodeTag(null), '');

  assert.equal(utils.computeProgressPercent(30000, 120000), 25);
  assert.equal(utils.computeProgressPercent(150000, 120000), 100);
  assert.equal(utils.computeProgressPercent(30000, null), null);
});

test('artwork fallback selection and initials generation behave predictably', () => {
  assert.deepEqual(
    { ...utils.pickSeriesArtwork({ backdropUrl: 'https://b', posterUrl: 'https://p' }, 'backdrop') },
    { primary: 'https://b', fallback: 'https://p' }
  );
  assert.deepEqual(
    { ...utils.pickSeriesArtwork({ backdropUrl: null, posterUrl: 'https://p' }, 'backdrop') },
    { primary: 'https://p', fallback: null }
  );
  assert.deepEqual(
    { ...utils.pickSeriesArtwork({ backdropUrl: '', posterUrl: '' }, 'backdrop') },
    { primary: null, fallback: null }
  );
  assert.deepEqual(
    { ...utils.pickMovieArtwork({ thumbnailUrl: 'https://t', backdrop: 'https://b', poster: 'https://p' }) },
    { primary: 'https://t', fallback: 'https://b' }
  );
  assert.equal(utils.getTitleInitials('Xin Chào Jadoo'), 'XJ');
  assert.equal(utils.getTitleInitials('Larva'), 'LA');
  assert.equal(utils.getTitleInitials(''), '?');

  const extracted = utils.extractSeriesGenres(sampleCards);
  assert.equal(extracted.length, 5);
  assert.equal(extracted[0].id, 'family');
});

test('SeriesRow renders heading, view-all link, accessible scroll buttons, and cards', () => {
  const h = makeReactHarness();
  const mod = loadTsModule('../features/series/components/SeriesRow.tsx', {
    require: (name) => {
      if (name === 'react') return { __esModule: true, default: h.react, ...h.react };
      if (name === 'react-router') return { Link: 'route-link' };
      if (name === 'lucide-react') return { ChevronLeft: 'LeftIcon', ChevronRight: 'RightIcon' };
      if (name.includes('SeriesCard')) return { SeriesCard: 'SeriesCard' };
      if (name.includes('seriesCardService')) return { listSeriesCards: async () => sampleCards };
      return {};
    },
  });

  h.resetCursor();
  const tree = mod.SeriesRow({ cards: sampleCards, loading: false, error: null });
  h.flushEffects();
  assert.ok(getText(tree).includes('Phim bộ'));
  const viewAll = findNodes(tree, (n) => n.props?.['data-testid'] === 'series-row-view-all')[0];
  assert.equal(viewAll.props.to, '/series');
  const prevBtn = findNodes(tree, (n) => n.props?.['data-testid'] === 'series-row-prev')[0];
  const nextBtn = findNodes(tree, (n) => n.props?.['data-testid'] === 'series-row-next')[0];
  assert.ok(prevBtn.props['aria-label']);
  assert.ok(nextBtn.props['aria-label']);
  assert.equal(findNodes(tree, (n) => n.type === 'SeriesCard').length, 3);
  h.unmount();
});

test('FilmShelf renders artwork, series_title, and episode badge on recommendation cards', () => {
  const h = makeReactHarness();
  const mediaRouting = loadTsModule('../lib/services/mediaRouting.ts', {});
  const mod = loadTsModule('../features/movies/components/FilmShelf.tsx', {
    React: h.react,
    require: (name) => {
      if (name === '@/lib/labels') return labels;
      if (name === 'react-router') return { Link: 'route-link' };
      if (name.includes('mediaRouting')) return mediaRouting;
      if (name.includes('ArtworkImage')) return { ArtworkImage: 'ArtworkImage' };
      return {};
    },
  });
  const gridTree = mod.FilmGrid({
    movies: [
      {
        id: 'ep_jadoo_1',
        title: 'ngày đi học',
        year: 2020,
        rating: 8.5,
        runtime: '12m',
        genres: ['Comedy'],
        description: 'Tập mở đầu vui nhộn.',
        poster: 'https://cdn.example.com/ep1-poster.jpg',
        backdrop: 'https://cdn.example.com/ep1-backdrop.jpg',
        item_type: 'episode',
        series_id: 'ser_jadoo',
        series_title: 'Xin Chào Jadoo',
        episode_number: 7,
      },
    ],
  });
  const card = findNodes(gridTree, (n) => n.props?.['data-testid'] === 'film-card')[0];
  assert.equal(card.props.to, '/series/ser_jadoo?episode=ep_jadoo_1');
  const art = findNodes(card, (n) => n.type === 'ArtworkImage')[0];
  assert.equal(art.props.src, 'https://cdn.example.com/ep1-backdrop.jpg');
  assert.equal(art.props.fallbackSrc, 'https://cdn.example.com/ep1-poster.jpg');
  const text = getText(card);
  assert.ok(text.includes('Xin Chào Jadoo'));
  assert.ok(text.includes('Tập 7'));
  assert.ok(text.includes('Ngày đi học'));
});

test('ArtworkImage falls back from primary src to fallbackSrc to initials panel on error', () => {
  const h = makeReactHarness();
  const mod = loadTsModule('../features/series/components/ArtworkImage.tsx', {
    require: (name) => {
      if (name === 'react') return { __esModule: true, default: h.react, ...h.react };
      if (name.includes('seriesBrowseUtils')) return utils;
      return {};
    },
  });

  const render = (props) => {
    h.resetCursor();
    const tree = mod.ArtworkImage(props);
    h.flushEffects();
    return tree;
  };

  let tree = render({
    src: 'https://primary.jpg',
    fallbackSrc: 'https://secondary.jpg',
    alt: 'Xin Chào Jadoo',
    title: 'Xin Chào Jadoo',
  });
  let img = findNodes(tree, (n) => n.type === 'img')[0];
  assert.equal(img.props.src, 'https://primary.jpg');
  assert.equal(img.props.loading, 'lazy');

  img.props.onError();
  tree = render({
    src: 'https://primary.jpg',
    fallbackSrc: 'https://secondary.jpg',
    alt: 'Xin Chào Jadoo',
    title: 'Xin Chào Jadoo',
  });
  img = findNodes(tree, (n) => n.type === 'img')[0];
  assert.equal(img.props.src, 'https://secondary.jpg');

  img.props.onError();
  tree = render({
    src: 'https://primary.jpg',
    fallbackSrc: 'https://secondary.jpg',
    alt: 'Xin Chào Jadoo',
    title: 'Xin Chào Jadoo',
  });
  assert.equal(findNodes(tree, (n) => n.type === 'img').length, 0);
  const fallback = findNodes(tree, (n) => n.props?.['data-testid'] === 'artwork-fallback')[0];
  assert.ok(fallback);
  assert.ok(getText(fallback).includes('XJ'));
});

test('SeriesCard caps genre tags at 3, links to /series/{id}, and includes synopsis and Xem CTA', () => {
  const h = makeReactHarness();
  const artworkMod = loadTsModule('../features/series/components/ArtworkImage.tsx', {
    require: (name) => {
      if (name === 'react') return { __esModule: true, default: h.react, ...h.react };
      if (name.includes('seriesBrowseUtils')) return utils;
      return {};
    },
  });
  const mod = loadTsModule('../features/series/components/SeriesCard.tsx', {
    require: (name) => {
      if (name === '@/lib/labels') return labels;
      if (name === 'react') return { __esModule: true, default: h.react, ...h.react };
      if (name === 'react-router') return { Link: 'route-link' };
      if (name === 'lucide-react') return { Play: 'PlayIcon' };
      if (name.includes('ArtworkImage')) return artworkMod;
      if (name.includes('seriesBrowseUtils')) return utils;
      return {};
    },
  });

  h.resetCursor();
  const tree = mod.SeriesCard({ card: sampleCards[0] });
  assert.equal(tree.props['data-testid'], 'series-card');
  const cardLink = findNodes(tree, (n) => n.type === 'route-link')[0];
  assert.equal(cardLink.props.to, '/series/ser_jadoo');
  const genresWrap = findNodes(tree, (n) => n.props?.['data-testid'] === 'series-card-genres')[0];
  assert.equal(genresWrap.props.children.length, 3);
  const text = getText(tree);
  assert.ok(text.includes('Xin Chào Jadoo'));
  assert.ok(text.includes('2011 đến 2022'));
  assert.ok(text.includes('4 mùa · 52 tập'));
  assert.ok(text.includes('Xem'));
  assert.ok(text.includes('Cô bé tinh nghịch Jadoo'));
  const overlayNodes = findNodes(tree, (n) => typeof n.props?.className === 'string' && n.props.className.includes('series-card-overlay'));
  assert.equal(overlayNodes.length, 0, 'Synopsis and Xem CTA live directly in the visible card body without hover-only overlay');
});

test('SeriesBrowsePage mirrors search, genre chips, and sort into URL and supports retry', async () => {
  let params = new URLSearchParams();
  let failNext = true;
  const h = makeReactHarness();
  const setSearchParams = (next) => {
    params = next instanceof URLSearchParams ? next : new URLSearchParams(next);
  };
  const mod = loadTsModule('../app/series/page.tsx', {
    require: (name) => {
      if (name === 'react') return { __esModule: true, default: h.react, ...h.react };
      if (name === 'react-router') return { useSearchParams: () => [params, setSearchParams] };
      if (name === 'lucide-react') return { Search: 'SearchIcon', X: 'XIcon' };
      if (name.includes('seriesCardService')) {
        return {
          listSeriesCards: async () => {
            if (failNext) throw new Error('Backend offline');
            return sampleCards;
          },
        };
      }
      if (name.includes('SeriesCard')) return { SeriesCard: 'SeriesCard' };
      if (name.includes('seriesBrowseUtils')) return utils;
      return {};
    },
  });

  const render = () => {
    h.resetCursor();
    const tree = mod.default();
    h.flushEffects();
    return tree;
  };
  const settle = async () => {
    let tree;
    for (let i = 0; i < 6; i++) {
      tree = render();
      await new Promise(setImmediate);
    }
    return render();
  };

  let tree = await settle();
  const retryBtn = findNodes(tree, (n) => n.props?.['data-testid'] === 'series-retry-button')[0];
  assert.ok(retryBtn);

  failNext = false;
  retryBtn.props.onClick();
  tree = await settle();

  const cards = findNodes(tree, (n) => n.type === 'SeriesCard');
  assert.equal(cards.length, 3);
  assert.ok(getText(tree).includes('3 phim bộ'));

  const sortSelect = findNodes(tree, (n) => n.props?.['data-testid'] === 'series-sort-select')[0];
  sortSelect.props.onChange({ target: { value: 'episodes' } });
  tree = render();
  assert.equal(params.get('sort'), 'episodes');
  const sortedCards = findNodes(tree, (n) => n.type === 'SeriesCard');
  assert.equal(sortedCards[0].props.card.id, 'ser_larva');

  const fantasyChip = findNodes(tree, (n) => n.props?.['data-genre-id'] === 'fantasy')[0];
  fantasyChip.props.onClick();
  tree = render();
  assert.equal(params.get('genres'), 'fantasy');
  assert.equal(findNodes(tree, (n) => n.type === 'SeriesCard').length, 1);

  h.unmount();
});

test('ContinueWatching renders thumbnail, series title, episode tag, progress bar, and preserves resume link', () => {
  const h = makeReactHarness();
  const watchProgressService = loadTsModule('../lib/services/watchProgressService.ts', {
    window: { localStorage: { getItem: () => null, setItem() {}, removeItem() {} } },
  });
  const movieProgress = loadTsModule('../lib/services/movieProgress.ts', {
    window: { localStorage: { getItem: () => null, setItem() {}, removeItem() {} } },
  });
  const mod = loadTsModule('../features/movies/components/ContinueWatching.tsx', {
    require: (name) => {
      if (name === '@/lib/labels') return labels;
      if (name === 'react') return { __esModule: true, default: h.react, ...h.react };
      if (name === 'react-router') return { Link: 'route-link' };
      if (name.includes('useWatchProgress')) {
        return {
          useWatchProgress: () => ({
            records: [
              {
                episodeId: 'ep_1',
                seriesId: 'ser_jadoo',
                seriesTitle: 'Xin Chào Jadoo',
                seasonId: 's1',
                seasonNumber: 1,
                seasonTitle: 'Mùa 1',
                episodeNumber: 3,
                episodeTitle: 'Ngày đi học',
                thumbnailUrl: 'https://cdn.example.com/ep3.jpg',
                assetId: 'ast_1',
                positionMs: 60000,
                durationMs: 240000,
                completed: false,
                version: 1,
                updatedAt: '2026-11-20T08:00:00+07:00',
              },
            ],
          }),
        };
      }
      if (name.includes('watchProgressService')) return watchProgressService;
      if (name.includes('movieProgress')) return movieProgress;
      if (name.includes('ArtworkImage')) return { ArtworkImage: 'ArtworkImage' };
      if (name.includes('seriesBrowseUtils')) return utils;
      return {};
    },
  });

  h.resetCursor();
  const tree = mod.ContinueWatching({ films: [
    { movieId: 'newer', title: 'Phim mới xem', positionMs: 24000, durationMs: 100000, completed: false, updatedAt: '2026-11-20T02:00:00Z' },
  ] });
  const [newest, card] = findNodes(tree, (n) => n.props?.['data-testid'] === 'continue-card');
  assert.equal(newest.props.to, '/movies/newer?t=24000');
  const watchedAt = findNodes(card, (n) => n.type === 'time')[0];
  const expectedTime = new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  }).format(Date.parse('2026-11-20T01:00:00Z'));
  assert.equal(getText(watchedAt), expectedTime);
  assert.ok(card);
  assert.equal(card.props.to, '/series/ser_jadoo?episode=ep_1&asset=ast_1&t=60000&timeline=file');
  const art = findNodes(card, (n) => n.type === 'ArtworkImage')[0];
  assert.equal(art.props.src, 'https://cdn.example.com/ep3.jpg');
  const bar = findNodes(card, (n) => n.props?.['data-testid'] === 'continue-progress-bar')[0];
  assert.equal(bar.props['aria-valuenow'], 25);
  const text = getText(card);
  assert.ok(text.includes('Xin Chào Jadoo'));
  assert.ok(text.includes('Tập 3'));
  assert.ok(text.includes('1:00'));
});

test('SeriesDetailBanner renders backdrop overlay, genres, counts, and falls back without card', () => {
  const h = makeReactHarness();
  const mod = loadTsModule('../features/series/components/SeriesDetailBanner.tsx', {
    require: (name) => {
      if (name === 'react') return { __esModule: true, default: h.react, ...h.react };
      if (name === 'react-router') return { Link: 'route-link' };
      if (name === '@/lib/labels') return labels;
      if (name.includes('seriesBrowseUtils')) return utils;
      return {};
    },
  });

  h.resetCursor();
  const withCard = mod.SeriesDetailBanner({
    series: { id: 'ser_jadoo', title: 'Xin Chào Jadoo', releaseYearStart: 2011 },
    card: sampleCards[0],
    seasonCount: 4,
    episodeCount: 52,
    nextEpisodeAction: { type: 'NextEpisodeAction', props: {} },
  });
  h.flushEffects();
  const bgImg = findNodes(withCard, (n) => n.type === 'img')[0];
  assert.equal(bgImg.props.src, 'https://cdn.example.com/jadoo-backdrop.jpg');
  assert.equal(bgImg.props.loading, 'lazy');
  const text = getText(withCard);
  assert.ok(text.includes('4 mùa · 52 tập'));
  assert.ok(text.includes('Hài'));
  assert.ok(text.includes('Gia đình'));

  const h2 = makeReactHarness();
  const mod2 = loadTsModule('../features/series/components/SeriesDetailBanner.tsx', {
    require: (name) => {
      if (name === 'react') return { __esModule: true, default: h2.react, ...h2.react };
      if (name === 'react-router') return { Link: 'route-link' };
      if (name === '@/lib/labels') return labels;
      if (name.includes('seriesBrowseUtils')) return utils;
      return {};
    },
  });
  h2.resetCursor();
  const fallbackTree = mod2.SeriesDetailBanner({
    series: { id: 'ser_old', title: 'Old Series', genreIds: 'Comedy,Family' },
    card: null,
    seasonCount: 2,
    episodeCount: 10,
    nextEpisodeAction: null,
  });
  assert.equal(findNodes(fallbackTree, (n) => n.type === 'img').length, 0);
  const fallbackText = getText(fallbackTree);
  assert.ok(fallbackText.includes('2 mùa · 10 tập'));
  assert.ok(fallbackText.includes('Hài'));
  assert.ok(fallbackText.includes('Gia đình'));
});

test('R2: null and zero years never render "null", "0", or empty separators across cards, banner, detail, and sort', () => {
  assert.equal(labels.formatYear(null), '');
  assert.equal(labels.formatYear(undefined), '');
  assert.equal(labels.formatYear(0), '');
  assert.equal(labels.formatYear(-10), '');
  assert.equal(labels.formatYear(2005), '2005');

  const nullYearCard = {
    id: 'ser_doraemon',
    title: 'Doraemon',
    originalTitle: 'Doraemon',
    synopsis: 'Chú mèo máy đến từ tương lai.',
    releaseYearStart: null,
    releaseYearEnd: null,
    genres: [{ id: 'family', label: 'Gia đình' }],
    seasonCount: 12,
    episodeCount: 337,
    posterUrl: null,
    backdropUrl: null,
    averageRating: null,
    ratingCount: 0,
  };
  const zeroYearCard = {
    ...nullYearCard,
    id: 'ser_zero',
    title: 'Zero Year',
    releaseYearStart: 0,
    releaseYearEnd: 0,
    seasonCount: null,
    episodeCount: null,
  };

  const sortedNewest = utils.filterAndSortSeriesCards([nullYearCard, zeroYearCard, sampleCards[0]], {
    q: '',
    genres: [],
    sort: 'newest',
  });
  assert.equal(sortedNewest[0].id, 'ser_jadoo');

  const h = makeReactHarness();
  const cardMod = loadTsModule('../features/series/components/SeriesCard.tsx', {
    require: (name) => {
      if (name === '@/lib/labels') return labels;
      if (name === 'react') return { __esModule: true, default: h.react, ...h.react };
      if (name === 'react-router') return { Link: 'route-link' };
      if (name === 'lucide-react') return { Play: 'PlayIcon' };
      if (name.includes('ArtworkImage')) return { ArtworkImage: 'ArtworkImage' };
      if (name.includes('LibraryControls')) return { QuickSaveButtons: 'QuickSaveButtons' };
      if (name.includes('seriesBrowseUtils')) return utils;
      return {};
    },
  });
  h.resetCursor();
  const nullCardTree = cardMod.SeriesCard({ card: nullYearCard });
  const nullCardText = getText(nullCardTree);
  assert.doesNotMatch(nullCardText, /\bnull\b|^·|·\s*·/);
  assert.equal(findNodes(nullCardTree, (n) => n.props?.['data-testid'] === 'series-card-years').length, 0);
  assert.ok(nullCardText.includes('12 mùa · 337 tập'));

  h.resetCursor();
  const zeroCardTree = cardMod.SeriesCard({ card: zeroYearCard });
  const zeroCardText = getText(zeroCardTree);
  assert.doesNotMatch(zeroCardText, /\b0\b|\bnull\b|·/);

  const bh = makeReactHarness();
  const bannerMod = loadTsModule('../features/series/components/SeriesDetailBanner.tsx', {
    require: (name) => {
      if (name === '@/lib/labels') return labels;
      if (name === 'react') return { __esModule: true, default: bh.react, ...bh.react };
      if (name === 'react-router') return { Link: 'route-link' };
      if (name.includes('seriesBrowseUtils')) return utils;
      return {};
    },
  });
  bh.resetCursor();
  const nullBannerTree = bannerMod.SeriesDetailBanner({
    series: { id: 'ser_doraemon', title: 'Doraemon' },
    card: nullYearCard,
    seasonCount: 12,
    episodeCount: 337,
    nextEpisodeAction: null,
  });
  const nullBannerText = getText(nullBannerTree);
  assert.doesNotMatch(nullBannerText, /\bnull\b|·\s*·/);
  assert.equal(findNodes(nullBannerTree, (n) => n.props?.['data-testid'] === 'series-banner-years').length, 0);

  const mcH = makeReactHarness();
  const mediaRouting = loadTsModule('../lib/services/mediaRouting.ts', {});
  const baseWl = {
    isWatchlisted: () => false,
    toggleWatchlist() {},
    isFavorite: () => false,
    toggleFavorite() {},
    getRating: () => undefined,
    rateMovie() {},
    removeRating() {},
    isActionPending: () => false,
    isActionUnconfirmed: () => false,
    getActionIntent: () => null,
    getUnconfirmedRating: () => undefined,
    openChat() {},
  };
  const mcMod = loadTsModule('../features/movies/components/MovieCard.tsx', {
    require: (name) => {
      if (name === '@/lib/labels') return labels;
      if (name === 'react') return { __esModule: true, default: mcH.react, ...mcH.react };
      if (name === 'react-router') return { Link: 'route-link' };
      if (name === 'lucide-react') return new Proxy({}, { get: (_, k) => String(k) });
      if (name.includes('mediaRouting')) return mediaRouting;
      if (name.includes('WatchlistContext')) return { useWatchlist: () => baseWl };
      return {};
    },
  });
  mcH.resetCursor();
  const nullMovieCardText = getText(
    mcMod.MovieCard({
      movie: {
        id: 'ep_doraemon_1',
        title: 'Tập 1',
        year: null,
        rating: 0,
        runtime: '22m',
        genres: ['family'],
        description: '',
        poster: '',
        backdrop: '',
      },
    })
  );
  assert.ok(nullMovieCardText.includes('22 phút'));
  assert.doesNotMatch(nullMovieCardText, /\bnull\b|·/);

  mcH.resetCursor();
  const zeroMovieCardText = getText(
    mcMod.MovieCard({
      movie: {
        id: 'ep_zero',
        title: 'Tập 2',
        year: 0,
        rating: 0,
        runtime: '',
        genres: ['family'],
        description: '',
        poster: '',
        backdrop: '',
      },
    })
  );
  assert.doesNotMatch(zeroMovieCardText, /\b0\b|\bnull\b|·/);

  const pageSource = fs.readFileSync(new URL('../app/series/page.tsx', import.meta.url), 'utf8');
  assert.ok(pageSource.includes('lọc theo thể loại hoặc xếp theo tên, năm và số tập.'));
  assert.equal(pageSource.includes('theo thể loại, năm phát hành và số tập.'), false);
});

test('ContinueWatching keeps only the most recent unfinished episode per series, preserves same-second order, and handles unknown durations', () => {
  const h = makeReactHarness();
  const watchProgressService = loadTsModule('../lib/services/watchProgressService.ts', {
    window: { localStorage: { getItem: () => null, setItem() {}, removeItem() {} } },
  });
  const movieProgress = loadTsModule('../lib/services/movieProgress.ts', {
    window: { localStorage: { getItem: () => null, setItem() {}, removeItem() {} } },
  });

  const records = [
    watchProgressService.normalizeWatchProgressRecord({
      episodeId: 'ep_doraemon_211',
      seriesId: 'ser_doraemon',
      seriesTitle: 'Doraemon',
      episodeNumber: 211,
      episodeTitle: 'Tập 211 mới nhất',
      assetId: 'youtube:11111111111',
      positionMs: 45000,
      durationMs: 0,
      completed: false,
      version: 1,
      updatedAt: '2026-11-26T00:33:45Z',
    }),
    watchProgressService.normalizeWatchProgressRecord({
      episodeId: 'ep_doraemon_210',
      seriesId: 'ser_doraemon',
      seriesTitle: 'Doraemon',
      episodeNumber: 210,
      episodeTitle: 'Tập 210 cùng giây nhưng cũ hơn',
      assetId: 'youtube:22222222222',
      positionMs: 120000,
      durationMs: 600000,
      completed: false,
      version: 1,
      updatedAt: '2026-11-26T00:33:45Z',
    }),
    watchProgressService.normalizeWatchProgressRecord({
      episodeId: 'ep_doraemon_209',
      seriesId: 'ser_doraemon',
      seriesTitle: 'Doraemon',
      episodeNumber: 209,
      episodeTitle: 'Tập 209 cũ hơn nữa',
      assetId: 'youtube:33333333333',
      positionMs: 90000,
      durationMs: 600000,
      completed: false,
      version: 1,
      updatedAt: '2026-11-26T00:30:00Z',
    }),
    watchProgressService.normalizeWatchProgressRecord({
      episodeId: 'ep_near_end',
      seriesId: 'ser_ori',
      seriesTitle: 'Công chúa Ori',
      episodeNumber: 1,
      episodeTitle: 'Tập gần xong',
      assetId: 'youtube:44444444444',
      positionMs: 570000,
      durationMs: 600000,
      completed: false,
      version: 1,
      updatedAt: '2026-11-26T00:35:00Z',
    }),
    watchProgressService.normalizeWatchProgressRecord({
      episodeId: 'ep_jadoo_1',
      seriesId: 'ser_jadoo',
      seriesTitle: 'Xin Chào Jadoo',
      episodeNumber: 1,
      episodeTitle: 'Tập Jadoo',
      assetId: 'youtube:55555555555',
      positionMs: 120000,
      durationMs: 600000,
      completed: false,
      version: 1,
      updatedAt: '2026-11-26T00:25:00Z',
    }),
    watchProgressService.normalizeWatchProgressRecord({
      episodeId: 'ep_larva_1',
      seriesId: 'ser_larva',
      seriesTitle: 'Larva',
      episodeNumber: 1,
      episodeTitle: 'Tập Larva',
      assetId: 'youtube:66666666666',
      positionMs: 30000,
      durationMs: 120000,
      completed: false,
      version: 1,
      updatedAt: '2026-11-26T00:20:00Z',
    }),
  ];

  const mod = loadTsModule('../features/movies/components/ContinueWatching.tsx', {
    require: (name) => {
      if (name === '@/lib/labels') return labels;
      if (name === 'react') return { __esModule: true, default: h.react, ...h.react };
      if (name === 'react-router') return { Link: 'route-link' };
      if (name.includes('useWatchProgress')) return { useWatchProgress: () => ({ records }) };
      if (name.includes('watchProgressService')) return watchProgressService;
      if (name.includes('movieProgress')) return movieProgress;
      if (name.includes('ArtworkImage')) return { ArtworkImage: 'ArtworkImage' };
      if (name.includes('seriesBrowseUtils')) return utils;
      return {};
    },
  });

  h.resetCursor();
  const tree = mod.ContinueWatching({ films: [] });
  const cards = findNodes(tree, (n) => n.props?.['data-testid'] === 'continue-card');
  assert.equal(cards.length, 3);
  assert.equal(cards[0].props.to, '/series/ser_doraemon?episode=ep_doraemon_211&t=45000&timeline=episode');
  assert.equal(cards[1].props.to, '/series/ser_jadoo?episode=ep_jadoo_1&t=120000&timeline=episode');
  assert.equal(cards[2].props.to, '/series/ser_larva?episode=ep_larva_1&t=30000&timeline=episode');
  assert.equal(findNodes(cards[0], (n) => n.props?.['data-testid'] === 'continue-progress-bar').length, 0);
  assert.equal(findNodes(cards[1], (n) => n.props?.['data-testid'] === 'continue-progress-bar').length, 1);
});

test('ArtworkImage keeps a caller width instead of forcing w-full, so thumbnails beside text do not squeeze it', () => {
  const h = makeReactHarness();
  const mod = loadTsModule('../features/series/components/ArtworkImage.tsx', {
    require: (name) => {
      if (name === 'react') return { __esModule: true, default: h.react, ...h.react };
      if (name.includes('seriesBrowseUtils')) return utils;
      return {};
    },
  });
  const classOf = (props) => {
    h.resetCursor();
    const tree = mod.ArtworkImage({ src: 'https://a.jpg', alt: 'a', title: 'a', ...props });
    return tree.props.className.split(/\s+/);
  };

  const thumb = classOf({ aspectClassName: 'h-16 w-20', className: 'h-16 w-20 shrink-0' });
  assert.ok(thumb.includes('w-20'));
  assert.ok(!thumb.includes('w-full'));

  const card = classOf({});
  assert.ok(card.includes('w-full'));
  assert.ok(card.includes('aspect-video'));

  const responsive = classOf({ className: 'sm:w-40 max-w-xs' });
  assert.ok(responsive.includes('w-full'));
});
