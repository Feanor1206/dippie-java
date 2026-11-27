import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';
import { labels } from './labels_fixture.mjs';
import { createInternalResolver, shouldInlineComponent, runWithSharedModules } from './internal_loader.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '..');

function loadTsModule(relativePath, globals = {}) {
  const fullPath = path.resolve(root, relativePath);
  const source = fs.readFileSync(fullPath, 'utf8');
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
    URL,
    URLSearchParams,
    AbortController,
    encodeURIComponent,
    decodeURIComponent,
    setTimeout,
    clearTimeout,
    ...globals,
  };
  const customRequire = typeof globals.require === 'function' ? globals.require : () => ({});
  runWithSharedModules(
    code,
    {
      ...baseContext,
      module,
      exports: module.exports,
      require: createInternalResolver(baseContext, customRequire),
    },
    { filename: fullPath }
  );
  return module.exports;
}

function makeReactHarness() {
  let slots = [];
  let cursor = 0;
  let effects = [];
  const same = (a, b) => a && b && a.length === b.length && a.every((v, i) => Object.is(v, b[i]));
  const react = {
    Fragment: 'fragment',
    useState(initial) {
      const i = cursor++;
      if (!(i in slots)) slots[i] = { value: typeof initial === 'function' ? initial() : initial };
      return [
        slots[i].value,
        (next) => {
          slots[i].value = typeof next === 'function' ? next(slots[i].value) : next;
        },
      ];
    },
    useRef(initial) {
      const i = cursor++;
      return (slots[i] ||= { current: initial });
    },
    useMemo(factory, deps) {
      const i = cursor++;
      const prev = slots[i];
      if (!prev || !deps || !same(prev.deps, deps)) {
        slots[i] = { deps, value: factory() };
      }
      return slots[i].value;
    },
    useCallback(fn) {
      return fn;
    },
    useEffect(effect, deps) {
      const i = cursor++;
      const prev = slots[i];
      if (!prev || !deps || !same(prev.deps, deps)) {
        slots[i] = { deps, cleanup: prev?.cleanup };
        effects.push(() => {
          slots[i].cleanup?.();
          slots[i].cleanup = effect();
        });
      }
    },
    createElement(type, props, ...children) {
      if (shouldInlineComponent(type)) return type(props || {});
      return { type, props: { ...props, children } };
    },
  };
  return {
    react,
    render(fn) {
      cursor = 0;
      effects = [];
      const tree = fn();
      const pending = effects;
      effects = [];
      pending.forEach((run) => run());
      return tree;
    },
    unmount() {
      slots.forEach((slot) => slot?.cleanup?.());
    },
  };
}

function findNodes(node, predicate, out = []) {
  if (!node || typeof node !== 'object') return out;
  if (Array.isArray(node)) {
    for (const child of node) findNodes(child, predicate, out);
    return out;
  }
  if (predicate(node)) out.push(node);
  findNodes(node.props?.children, predicate, out);
  return out;
}

function getText(node) {
  if (node === null || node === undefined || typeof node === 'boolean') return '';
  if (typeof node === 'string' || typeof node === 'number') return String(node);
  if (Array.isArray(node)) return node.map(getText).join(' ');
  if (typeof node === 'object') return getText(node.props?.children);
  return '';
}

test('1. /account is reachable in mobile Navbar menu and desktop dropdown has menu ARIA and Escape dismissal', () => {
  const h = makeReactHarness();
  const listeners = {};
  const documentMock = {
    addEventListener(event, handler) {
      listeners[event] = handler;
    },
    removeEventListener(event) {
      delete listeners[event];
    },
  };
  const mod = loadTsModule('components/layout/Navbar.tsx', {
    document: documentMock,
    require(name) {
      if (name === '@/lib/labels') return labels;
      if (name === 'react') return { __esModule: true, default: h.react, ...h.react };
      if (name === 'react-router') return { Link: 'route-link', useLocation: () => ({ pathname: '/' }) };
      if (name.includes('WatchlistContext')) return { useWatchlist: () => ({ watchlist: ['m1'], openChat() {} }) };
      if (name.includes('AuthContext')) {
        return {
          useAuth: () => ({
            isAuthenticated: true,
            user: { username: 'parent', fullName: 'Phụ huynh', email: 'p@dippie.vn' },
            openAuthModal() {},
            logout() {},
          }),
        };
      }
      return {};
    },
  });

  let tree = h.render(() => mod.Navbar());
  const dropdownTrigger = findNodes(tree, (n) => n.props?.['data-testid'] === 'nav-user-dropdown-button')[0];
  assert.equal(dropdownTrigger.props['aria-haspopup'], 'menu');
  dropdownTrigger.props.onClick();

  const menuToggle = findNodes(tree, (n) => n.props?.['aria-label'] === 'Mở danh mục')[0];
  menuToggle.props.onClick();
  tree = h.render(() => mod.Navbar());

  const menu = findNodes(tree, (n) => n.props?.role === 'menu')[0];
  assert.ok(menu, 'Desktop user dropdown must expose role="menu"');
  const menuItems = findNodes(menu, (n) => n.props?.role === 'menuitem');
  assert.equal(menuItems.length, 7);
  assert.ok(menuItems.some((item) => item.props.to === '/lich-su'));

  const mobileNav = findNodes(tree, (n) => n.props?.id === 'mobile-navigation')[0];
  const mobileAccountLink = findNodes(mobileNav, (n) => n.props?.['data-testid'] === 'mobile-account-link')[0];
  assert.ok(mobileAccountLink, 'Authenticated mobile drawer must link to /account');
  assert.equal(mobileAccountLink.props.to, '/account');

  menu.props.onKeyDown({ key: 'Escape', preventDefault() {} });
  mobileNav.props.onKeyDown({ key: 'Escape', preventDefault() {} });
  tree = h.render(() => mod.Navbar());
  assert.equal(findNodes(tree, (n) => n.props?.role === 'menu').length, 0);
  assert.equal(findNodes(tree, (n) => n.props?.id === 'mobile-navigation').length, 0);
  h.unmount();
});

test('2. ProfileOverview links to /account, has scrollable accessible tablist with arrow keys, 44px rating delete, and Vietnamese genres', async () => {
  const h = makeReactHarness();
  const focusedIds = [];
  const documentMock = {
    getElementById(id) {
      return {
        focus() {
          focusedIds.push(id);
        },
      };
    },
  };
  const iconStub = () => null;
  const mod = loadTsModule('features/profile/components/ProfileOverview.tsx', {
    document: documentMock,
    require(name) {
      if (name === '@/lib/labels') return labels;
      if (name === 'react') return { __esModule: true, default: h.react, ...h.react };
      if (name === 'react-router') return { Link: 'route-link' };
      if (name === 'lucide-react') return new Proxy({}, { get: () => iconStub });
      if (name === '@/lib/labels') return labels;
      if (name.includes('MovieCard')) return { MovieCard: 'MovieCard' };
      if (name.includes('WatchlistContext')) {
        return {
          useWatchlist: () => ({
            watchlist: ['m1'],
            favorites: [],
            ratings: { m1: 9 },
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
        return {
          useAuth: () => ({
            user: { fullName: 'Minh', email: 'minh@dippie.vn' },
            status: 'authenticated',
            openAuthModal() {},
            retryVerify() {},
          }),
        };
      }
      if (name.includes('catalogService')) {
        return {
          getCatalogMovieById: async () => ({
            id: 'm1',
            title: 'Tập 1 [abc12345678].mp4',
            year: 2020,
            rating: 9,
            averageRating: 9,
            ratingCount: 1,
            runtime: 'Series',
            genres: ['Animation', 'Comedy'],
            description: '',
            poster: '',
            backdrop: '',
          }),
        };
      }
      return {};
    },
  });

  mod.clearProfileMetadataCache();
  let tree = h.render(() => mod.ProfileOverview());
  await new Promise((r) => setImmediate(r));
  tree = h.render(() => mod.ProfileOverview());

  const accountLink = findNodes(tree, (n) => n.props?.['data-testid'] === 'profile-account-link')[0];
  assert.ok(accountLink, 'Authenticated profile must link to /account');
  assert.equal(accountLink.props.to, '/account');

  const profileText = getText(tree);
  assert.ok(profileText.includes('Hoạt hình'), 'Top genres must be localized to Vietnamese');
  assert.ok(!profileText.includes('Animation'), 'Raw English genre name must not appear');

  const tablist = findNodes(tree, (n) => n.props?.role === 'tablist')[0];
  assert.ok(tablist, 'Profile tabs must have role="tablist"');
  assert.ok(tablist.props.className.includes('overflow-x-auto'));
  assert.ok(tablist.props.className.includes('whitespace-nowrap'));

  const tabs = findNodes(tablist, (n) => n.props?.role === 'tab');
  assert.equal(tabs.length, 3);
  assert.equal(tabs[0].props['aria-selected'], true);
  assert.equal(tabs[1].props['aria-selected'], false);

  tabs[0].props.onKeyDown({ key: 'ArrowRight', preventDefault() {} });
  tree = h.render(() => mod.ProfileOverview());
  const tabsAfterArrow = findNodes(tree, (n) => n.props?.role === 'tab');
  assert.equal(tabsAfterArrow[1].props['aria-selected'], true);
  assert.equal(focusedIds.at(-1), 'profile-tab-favorites');

  tabsAfterArrow[1].props.onKeyDown({ key: 'End', preventDefault() {} });
  tree = h.render(() => mod.ProfileOverview());
  const panel = findNodes(tree, (n) => n.props?.role === 'tabpanel')[0];
  assert.equal(panel.props.id, 'profile-panel-ratings');
  assert.equal(panel.props['aria-labelledby'], 'profile-tab-ratings');

  const deleteRatingBtn = findNodes(panel, (n) => n.type === 'button' && n.props?.['aria-label'] === 'Xóa điểm')[0];
  assert.ok(deleteRatingBtn, 'Ratings panel must expose delete rating button');
  assert.ok(deleteRatingBtn.props.className.includes('min-h-[44px]'));
  assert.ok(deleteRatingBtn.props.className.includes('min-w-[44px]'));
  h.unmount();
});

test('3. Route document.title resolver and light-theme contrast fixes in MovieDetail and MovieDetailPage', () => {
  const routeTitle = loadTsModule('lib/routeTitle.ts');
  assert.equal(routeTitle.resolveRouteTitle('/'), 'Dippie · Phim hoạt hình');
  assert.equal(routeTitle.resolveRouteTitle('/series'), 'Phim bộ · Dippie');
  assert.equal(routeTitle.resolveRouteTitle('/series/ser_jadoo'), 'Xem phim bộ · Dippie');
  assert.equal(routeTitle.resolveRouteTitle('/search'), 'Kho phim · Dippie');
  assert.equal(routeTitle.resolveRouteTitle('/profile'), 'Thư viện · Dippie');
  assert.equal(routeTitle.resolveRouteTitle('/account'), 'Cài đặt tài khoản · Dippie');
  assert.equal(routeTitle.resolveRouteTitle('/unknown-path'), 'Không tìm thấy trang · Dippie');

  const movieDetailSource = fs.readFileSync(path.resolve(root, 'features/movies/components/MovieDetail.tsx'), 'utf8');
  assert.equal(movieDetailSource.includes('text-zinc-300'), false, 'MovieDetail must not keep invisible text-zinc-300');
  assert.equal(movieDetailSource.includes('hover:text-white'), false, 'MovieDetail must not keep hover:text-white on light surface');
  assert.equal(movieDetailSource.includes('bg-zinc-950'), false, 'MovieDetail fallback backdrop must not use bg-zinc-950');
  assert.equal(movieDetailSource.includes('hover:text-red-400'), false, 'MovieDetail remove rating link must use high-contrast red');
  assert.ok(movieDetailSource.includes('h-11 w-11 min-h-[44px] min-w-[44px]'), 'MovieDetail rating buttons must be 44x44px');

  const moviePageSource = fs.readFileSync(path.resolve(root, 'app/movies/[id]/page.tsx'), 'utf8');
  assert.equal(moviePageSource.includes('bg-zinc-800'), false, 'MovieDetailPage must not keep dark bg-zinc-800');

  const accountSource = fs.readFileSync(path.resolve(root, 'features/account/components/AccountSettings.tsx'), 'utf8');
  assert.equal(accountSource.includes('UTF-8'), false, 'AccountSettings must not expose UTF-8 byte jargon');
  assert.equal(accountSource.includes('byte'), false, 'AccountSettings must not expose byte jargon');

  const authModalSource = fs.readFileSync(path.resolve(root, 'features/auth/components/AuthModal.tsx'), 'utf8');
  assert.ok(authModalSource.includes('aria-pressed={isLogin}'));
  assert.ok(authModalSource.includes('role="alert"'));
});

test('4. cleanMediaTitle cleans raw filenames across FilmGrid, ContinueWatching, MovieCard, and SeriesDetailPage', () => {
  assert.equal(
    labels.cleanMediaTitle('Mẹ ơi, mua cho con một ít trà nhé! ⧸ Xin Chào Jadoo [ahtn4gQsBXo].mp4'),
    'Mẹ ơi, mua cho con một ít trà nhé! / Xin Chào Jadoo'
  );
  assert.equal(labels.formatRuntime('Series'), 'Phim bộ');

  const h = makeReactHarness();
  const filmShelfMod = loadTsModule('features/movies/components/FilmShelf.tsx', {
    React: h.react,
    require(name) {
      if (name === '@/lib/labels') return labels;
      if (name === 'react') return { __esModule: true, default: h.react, ...h.react };
      if (name === 'react-router') return { Link: 'route-link' };
      if (name === '@/lib/labels') return labels;
      if (name.includes('mediaRouting')) return { buildMediaItemHref: () => '/series/ser_jadoo?episode=ep1' };
      if (name.includes('ArtworkImage')) return { ArtworkImage: 'ArtworkImage' };
      if (name.includes('LibraryControls')) return { QuickSaveButtons: 'QuickSaveButtons' };
      return {};
    },
  });
  const shelfTree = h.render(() =>
    filmShelfMod.FilmGrid({
      movies: [
        {
          id: 'ep1',
          title: 'Tập đặc biệt [kS3zyuaG4q0].mp4',
          year: 2015,
          rating: 0,
          averageRating: null,
          ratingCount: 0,
          runtime: '10m',
          genres: ['comedy'],
          description: '',
          poster: '',
          backdrop: '',
          item_type: 'episode',
          series_id: 'ser_jadoo',
        },
      ],
    })
  );
  const shelfText = getText(shelfTree);
  assert.ok(shelfText.includes('Tập đặc biệt'));
  assert.equal(shelfText.includes('.mp4'), false);
  assert.equal(shelfText.includes('[kS3zyuaG4q0]'), false);

  const watchProgressService = loadTsModule('lib/services/watchProgressService.ts', {
    window: { localStorage: { getItem: () => null, setItem() {}, removeItem() {} } },
  });
  const movieProgress = loadTsModule('lib/services/movieProgress.ts', {
    window: { localStorage: { getItem: () => null, setItem() {}, removeItem() {} } },
  });
  const browseUtils = loadTsModule('features/series/seriesBrowseUtils.ts', {
    require: (name) => (name === '@/lib/labels' ? labels : {}),
  });
  const continueMod = loadTsModule('features/movies/components/ContinueWatching.tsx', {
    require(name) {
      if (name === '@/lib/labels') return labels;
      if (name === 'react') return { __esModule: true, default: h.react, ...h.react };
      if (name === 'react-router') return { Link: 'route-link' };
      if (name === '@/lib/labels') return labels;
      if (name.includes('useWatchProgress')) {
        return {
          useWatchProgress: () => ({
            records: [
              {
                episodeId: 'ep_jadoo_s03_0016',
                seriesId: 'ser_jadoo',
                seriesTitle: 'Xin Chào Jadoo',
                episodeNumber: 16,
                episodeTitle: 'Bộ sưu tập Jadoo [kS3zyuaG4q0].mp4',
                assetId: 'youtube:kS3zyuaG4q0',
                positionMs: 45000,
                durationMs: 600000,
                completed: false,
                version: 1,
                updatedAt: '2026-11-25T10:00:00Z',
              },
            ],
          }),
        };
      }
      if (name.includes('watchProgressService')) return watchProgressService;
      if (name.includes('movieProgress')) return movieProgress;
      if (name.includes('ArtworkImage')) return { ArtworkImage: 'ArtworkImage' };
      if (name.includes('seriesBrowseUtils')) return browseUtils;
      return {};
    },
  });
  const continueTree = h.render(() => continueMod.ContinueWatching({ films: [] }));
  const continueText = getText(continueTree);
  assert.ok(continueText.includes('Bộ sưu tập Jadoo'));
  assert.equal(continueText.includes('.mp4'), false);
});

test('5. SeriesDetailPage marks external-only episodes, sets document.title, uses 44px season buttons, 16px search, and plain Vietnamese warnings', async () => {
  const playbackSource = loadTsModule('lib/services/playbackSource.ts');
  assert.equal(
    playbackSource.isEpisodeExternalOnly({
      id: 'ep_ori_s05_0001',
      seriesId: 'ser_ori',
      title: 'Tập 1',
      rawJson: JSON.stringify({
        external_ids: [{ namespace: 'youtube_watch', value: 'SSubOeKMP2c' }],
      }),
    }),
    true
  );
  assert.equal(
    playbackSource.isEpisodeExternalOnly({
      id: 'ep_ori_s01_0001',
      seriesId: 'ser_ori',
      title: 'Tập 1',
      rawJson: JSON.stringify({
        external_ids: [
          { namespace: 'youtube_playback', value: 'AbCdEfGhIjK' },
          { namespace: 'youtube_watch', value: 'SSubOeKMP2c' },
        ],
      }),
    }),
    false
  );
  assert.equal(
    playbackSource.isEpisodeExternalOnly({
      id: 'ep_explicit',
      seriesId: 'ser_ori',
      title: 'Tập 2',
      canEmbed: false,
    }),
    true
  );

  const h = makeReactHarness();
  const documentMock = {
    title: '',
    hidden: false,
    addEventListener() {},
    removeEventListener() {},
  };
  const episodeNavigation = loadTsModule('lib/services/episodeNavigation.ts');
  const ep1 = {
    id: 'ep_1',
    seriesId: 'ser_ori',
    seasonId: 's1',
    number: 1,
    title: 'Mẹ ơi ⧸ Jadoo [ahtn4gQsBXo].mp4',
    durationMs: 600000,
    rawJson: JSON.stringify({ external_ids: [{ namespace: 'youtube_playback', value: 'ahtn4gQsBXo' }] }),
  };
  const ep2 = {
    id: 'ep_2',
    seriesId: 'ser_ori',
    seasonId: 's1',
    number: 2,
    title: 'Tập chỉ xem ngoài YouTube',
    durationMs: 600000,
    rawJson: JSON.stringify({ external_ids: [{ namespace: 'youtube_watch', value: 'SSubOeKMP2c' }] }),
  };

  const pageMod = loadTsModule('app/series/[id]/page.tsx', {
    document: documentMock,
    window: {
      location: { href: 'http://localhost:3000/series/ser_ori?episode=ep_1' },
      history: { replaceState() {} },
      setTimeout,
      clearTimeout,
    },
    require(name) {
      if (name === '@/lib/labels') return labels;
      if (name === 'react') return { __esModule: true, default: h.react, ...h.react };
      if (name === 'react-router') {
        return {
          Link: 'route-link',
          useParams: () => ({ id: 'ser_ori' }),
          useSearchParams: () => [new URLSearchParams('episode=ep_1')],
        };
      }
      if (name === '@/lib/labels') return labels;
      if (name.includes('playbackSource')) return playbackSource;
      if (name.includes('episodeNavigation')) return episodeNavigation;
      if (name.includes('catalogService')) {
        return {
          getCatalogSeriesDetail: async () => ({
            series: { id: 'ser_ori', title: 'Công chúa Ori' },
            seasons: [{ id: 's1', seriesId: 'ser_ori', number: 1, title: 'Mùa 1' }],
            episodes: [ep1, ep2],
          }),
          getCatalogEpisodeDetail: async () => ({
            episode: ep1,
            segments: [{ id: 'seg1', episodeId: 'ep_1', position: 1, title: 'Mở đầu' }],
            mediaAssets: [],
            scenes: [],
            youtube: { videoId: 'ahtn4gQsBXo', canEmbed: true },
            youtubeFallback: null,
          }),
        };
      }
      if (name.includes('mediaService')) return { resolveStreamUrl: (u) => u, getStoredAuthToken: () => null };
      if (name.includes('mediaRouting')) return { resolveFileTimestamp: () => null, resolveMediaSeekSeconds: () => null };
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
      if (name.includes('seriesCardService')) return { getSeriesCardById: async () => null };
      if (name.includes('NextEpisodeAction')) return { NextEpisodeAction: 'NextEpisodeAction' };
      if (name.includes('SeriesDetailBanner')) return { SeriesDetailBanner: 'SeriesDetailBanner' };
      if (name.includes('LibraryControls')) {
        return { ItemLibraryPanel: 'ItemLibraryPanel', QuickSaveButtons: 'QuickSaveButtons' };
      }
      if (name.includes('YouTubePlayer')) return { YouTubePlayer: 'YouTubePlayer' };
      return {};
    },
  });

  let tree;
  for (let i = 0; i < 6; i++) {
    tree = h.render(() => pageMod.default());
    await new Promise((r) => setImmediate(r));
  }
  tree = h.render(() => pageMod.default());

  assert.equal(documentMock.title, 'Mẹ ơi / Jadoo · Công chúa Ori · Dippie');
  const fullText = getText(tree);
  assert.ok(fullText.includes('Mẹ ơi / Jadoo'));
  assert.equal(fullText.includes('[ahtn4gQsBXo].mp4'), false);

  const seasonBtn = findNodes(tree, (n) => n.props?.['data-season-id'] === 's1')[0];
  assert.ok(seasonBtn.props.className.includes('min-h-[44px]'));
  assert.ok(seasonBtn.props.className.includes('min-w-[44px]'));

  const epSearchInput = findNodes(tree, (n) => n.props?.['data-testid'] === 'episode-search-input')[0];
  assert.ok(epSearchInput.props.className.includes('text-base'));

  const ep2Btn = findNodes(tree, (n) => n.props?.['data-episode-id'] === 'ep_2')[0];
  const externalBadge = findNodes(ep2Btn, (n) => n.props?.['data-testid'] === 'episode-external-badge')[0];
  assert.ok(externalBadge, 'External-only episode must be clearly badged in episode list');
  assert.match(getText(externalBadge), /Chỉ mở trên YouTube/);

  const ep1Btn = findNodes(tree, (n) => n.props?.['data-episode-id'] === 'ep_1')[0];
  assert.equal(findNodes(ep1Btn, (n) => n.props?.['data-testid'] === 'episode-external-badge').length, 0);

  const segmentRow = findNodes(tree, (n) => n.props?.['data-segment-id'] === 'seg1')[0];
  segmentRow.props.onClick();
  tree = h.render(() => pageMod.default());
  const warningText = getText(tree);
  assert.ok(warningText.includes('chưa có mốc thời gian bắt đầu'));
  assert.equal(warningText.includes('episode_start_ms'), false);
  assert.equal(warningText.includes('coverage'), false);
  h.unmount();
});

test('6. R3: In-season episode search folds Vietnamese diacritics and đ/Đ so "khung long" finds "khủng long"', async () => {
  const browseUtils = loadTsModule('features/series/seriesBrowseUtils.ts', {
    require: (name) => (name === '@/lib/labels' ? labels : {}),
  });
  assert.equal(browseUtils.foldVietnamese('Khủng long con'), 'khung long con');
  assert.equal(browseUtils.foldVietnamese('Chiến Tranh Vũ Trụ Dưới Mái Nhà'), 'chien tranh vu tru duoi mai nha');
  assert.equal(browseUtils.foldVietnamese('Ngày Đi Học Ở Trường'), 'ngay di hoc o truong');

  const h = makeReactHarness();
  const playbackSource = loadTsModule('lib/services/playbackSource.ts');
  const episodeNavigation = loadTsModule('lib/services/episodeNavigation.ts');
  const episodes = [
    { id: 'ep_dino', seriesId: 'ser_doraemon', seasonId: 's12', number: 1, title: 'Chú khủng long nhỏ của Nobita', durationMs: 600000 },
    { id: 'ep_space', seriesId: 'ser_doraemon', seasonId: 's12', number: 2, title: 'Chiến Tranh Vũ Trụ Dưới Mái Nhà', durationMs: 600000 },
    { id: 'ep_school', seriesId: 'ser_doraemon', seasonId: 's12', number: 3, title: 'Ngày đi học đầu tiên', durationMs: 600000 },
  ];

  const pageMod = loadTsModule('app/series/[id]/page.tsx', {
    document: { title: '', hidden: false, addEventListener() {}, removeEventListener() {} },
    window: {
      location: { href: 'http://localhost:3000/series/ser_doraemon?episode=ep_dino' },
      history: { replaceState() {} },
      setTimeout,
      clearTimeout,
    },
    require(name) {
      if (name === '@/lib/labels') return labels;
      if (name === 'react') return { __esModule: true, default: h.react, ...h.react };
      if (name === 'react-router') {
        return {
          Link: 'route-link',
          useParams: () => ({ id: 'ser_doraemon' }),
          useSearchParams: () => [new URLSearchParams('episode=ep_dino')],
        };
      }
      if (name === '@/lib/labels') return labels;
      if (name.includes('seriesBrowseUtils')) return browseUtils;
      if (name.includes('playbackSource')) return playbackSource;
      if (name.includes('episodeNavigation')) return episodeNavigation;
      if (name.includes('catalogService')) {
        return {
          getCatalogSeriesDetail: async () => ({
            series: { id: 'ser_doraemon', title: 'Doraemon' },
            seasons: [{ id: 's12', seriesId: 'ser_doraemon', number: 12, title: 'Mùa 12' }],
            episodes,
          }),
          getCatalogEpisodeDetail: async () => ({
            episode: episodes[0],
            segments: [],
            mediaAssets: [],
            scenes: [],
            youtube: { videoId: '11111111111', canEmbed: true },
          }),
        };
      }
      if (name.includes('mediaService')) return { resolveStreamUrl: (u) => u, getStoredAuthToken: () => null };
      if (name.includes('mediaRouting')) return { resolveFileTimestamp: () => null, resolveMediaSeekSeconds: () => null };
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
      if (name.includes('seriesCardService')) return { getSeriesCardById: async () => null };
      if (name.includes('NextEpisodeAction')) return { NextEpisodeAction: 'NextEpisodeAction' };
      if (name.includes('SeriesDetailBanner')) return { SeriesDetailBanner: 'SeriesDetailBanner' };
      if (name.includes('LibraryControls')) return { ItemLibraryPanel: 'ItemLibraryPanel', QuickSaveButtons: 'QuickSaveButtons' };
      if (name.includes('YouTubePlayer')) return { YouTubePlayer: 'YouTubePlayer' };
      return {};
    },
  });

  let tree;
  for (let i = 0; i < 6; i++) {
    tree = h.render(() => pageMod.default());
    await new Promise((r) => setImmediate(r));
  }
  tree = h.render(() => pageMod.default());

  const setSearch = (q) => {
    const input = findNodes(tree, (n) => n.props?.['data-testid'] === 'episode-search-input')[0];
    input.props.onChange({ target: { value: q } });
    tree = h.render(() => pageMod.default());
    return findNodes(tree, (n) => typeof n.props?.['data-episode-id'] === 'string').map((n) => n.props['data-episode-id']);
  };

  assert.deepEqual(setSearch('khung long'), ['ep_dino']);
  assert.deepEqual(setSearch('vu tru'), ['ep_space']);
  assert.deepEqual(setSearch('duoi mai nha'), ['ep_space']);
  assert.deepEqual(setSearch('di hoc'), ['ep_school']);
  assert.deepEqual(setSearch('ĐI HỌC'), ['ep_school']);
  h.unmount();
});

test('7. R4 & R7: CSS cascade enforces 44px chips/targets, 16px mobile selects, and cleaned Vietnamese/lowercase leftovers', () => {
  const css = fs.readFileSync(path.resolve(root, 'app/globals.css'), 'utf8');
  const layerMatch = css.match(/@layer components\s*\{([\s\S]*?)\n\}/);
  assert.ok(layerMatch, 'globals.css must declare @layer components');
  const layerBody = layerMatch[1];
  assert.match(layerBody, /\.series-filter-chip\s*\{[^}]*min-height:\s*44px/);
  assert.match(layerBody, /\.topic-chip\s*\{[^}]*min-height:\s*44px/);
  assert.match(layerBody, /\.shelf-link\s*\{[^}]*min-height:\s*44px/);
  assert.match(layerBody, /\.series-scroll-btn\s*\{[^}]*height:\s*44px[^}]*width:\s*44px/);

  const outsideLayer = css.replace(layerMatch[0], '');
  assert.equal(outsideLayer.includes('.series-filter-chip {'), false, '.series-filter-chip must live inside @layer components');
  assert.equal(css.includes('min-height: 38px'), false, 'Old 38px chip height must be removed');

  const seriesPageSource = fs.readFileSync(path.resolve(root, 'app/series/page.tsx'), 'utf8');
  const sortSelectMatch = seriesPageSource.match(/id="series-sort-select"[\s\S]*?className="([^"]+)"/);
  assert.ok(sortSelectMatch, 'SeriesBrowsePage must render #series-sort-select');
  assert.ok(sortSelectMatch[1].includes('text-base'), '#series-sort-select must use 16px (text-base) on mobile to prevent iOS zoom');
  assert.ok(sortSelectMatch[1].includes('sm:text-sm'));

  const searchExpSource = fs.readFileSync(path.resolve(root, 'features/search/components/SearchExperience.tsx'), 'utf8');
  const searchSelectMatch = searchExpSource.match(/id="search-sort-select"[\s\S]*?className="([^"]+)"/);
  assert.ok(searchSelectMatch && searchSelectMatch[1].includes('text-base'));

  const cinebotSource = fs.readFileSync(path.resolve(root, 'features/chat/components/CineBot.tsx'), 'utf8');
  assert.ok(cinebotSource.includes('inline-flex min-h-[44px] items-center rounded border border-ink/30 bg-mint'));

  const footerSource = fs.readFileSync(path.resolve(root, 'components/layout/Footer.tsx'), 'utf8');
  assert.ok(footerSource.includes("label: 'Hài hước'"));
  assert.equal(footerSource.includes("label: 'Hài'"), false);
  assert.ok(footerSource.includes('min-h-[44px]'));

  const homeSource = fs.readFileSync(path.resolve(root, 'app/page.tsx'), 'utf8');
  assert.ok(homeSource.includes("label: 'Hài hước'"));
  assert.equal(homeSource.includes("label: 'Hài'"), false);

  const movieDetailSource = fs.readFileSync(path.resolve(root, 'features/movies/components/MovieDetail.tsx'), 'utf8');
  assert.equal(movieDetailSource.includes('/search?genre=Animation'), false);
  assert.ok(movieDetailSource.includes('/search?genre=animation'));

  const moviePageSource = fs.readFileSync(path.resolve(root, 'app/movies/[id]/page.tsx'), 'utf8');
  assert.equal(moviePageSource.includes('/search?genre=Animation'), false);
  assert.ok(moviePageSource.includes('/search?genre=animation'));
});

test('8. Finding 3: External-only episodes show clear badge and a confirmation-gated Mở trên YouTube action on episode rows, player area, FilmGrid, and MovieCard', async () => {
  const playbackSource = loadTsModule('lib/services/playbackSource.ts');
  const epOriS5 = {
    id: 'ep_ori_s05_0001',
    seriesId: 'ser_ori',
    seasonId: 'sea_ori_s05',
    number: 1,
    title: 'Nhiệm Vụ Bất Khả Thi',
    durationMs: 1320000,
    rawJson: JSON.stringify({
      external_ids: [{ namespace: 'youtube_watch', value: 'SSubOeKMP2c' }],
    }),
  };
  assert.equal(playbackSource.isEpisodeExternalOnly(epOriS5), true);
  assert.equal(
    playbackSource.resolveExternalYoutubeWatchUrl(epOriS5),
    'https://www.youtube.com/watch?v=SSubOeKMP2c&t=0'
  );

  const h = makeReactHarness();
  const episodeNavigation = loadTsModule('lib/services/episodeNavigation.ts');
  const pageMod = loadTsModule('app/series/[id]/page.tsx', {
    document: { title: '', hidden: false, visibilityState: 'visible', addEventListener() {}, removeEventListener() {} },
    window: {
      location: { href: 'http://localhost:3000/series/ser_ori?episode=ep_ori_s05_0001' },
      history: { replaceState() {} },
      addEventListener() {},
      removeEventListener() {},
      setTimeout,
      clearTimeout,
    },
    require(name) {
      if (name === '@/lib/labels') return labels;
      if (name === 'react') return { __esModule: true, default: h.react, ...h.react };
      if (name === 'react-router') {
        return {
          Link: 'route-link',
          useParams: () => ({ id: 'ser_ori' }),
          useSearchParams: () => [new URLSearchParams('episode=ep_ori_s05_0001')],
        };
      }
      if (name === '@/lib/labels') return labels;
      if (name.includes('playbackSource')) return playbackSource;
      if (name.includes('episodeNavigation')) return episodeNavigation;
      if (name.includes('catalogService')) {
        return {
          getCatalogSeriesDetail: async () => ({
            series: { id: 'ser_ori', title: 'Công chúa Ori' },
            seasons: [{ id: 'sea_ori_s05', seriesId: 'ser_ori', number: 5, title: 'Mùa 5' }],
            episodes: [epOriS5],
          }),
          getCatalogEpisodeDetail: async () => ({
            episode: epOriS5,
            segments: [],
            mediaAssets: [],
            scenes: [],
            youtube: { videoId: 'SSubOeKMP2c', canEmbed: false },
            youtubeFallback: null,
          }),
        };
      }
      if (name.includes('mediaService')) return { resolveStreamUrl: (u) => u, getStoredAuthToken: () => null };
      if (name.includes('mediaRouting')) return { resolveFileTimestamp: () => null, resolveMediaSeekSeconds: () => null };
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
      if (name.includes('seriesCardService')) return { getSeriesCardById: async () => null };
      if (name.includes('NextEpisodeAction')) return { NextEpisodeAction: 'NextEpisodeAction' };
      if (name.includes('SeriesDetailBanner')) return { SeriesDetailBanner: 'SeriesDetailBanner' };
      if (name.includes('LibraryControls')) return { ItemLibraryPanel: 'ItemLibraryPanel', QuickSaveButtons: 'QuickSaveButtons' };
      if (name.includes('YouTubePlayer')) return { YouTubePlayer: 'YouTubePlayer' };
      return {};
    },
  });

  let tree;
  for (let i = 0; i < 6; i++) {
    tree = h.render(() => pageMod.default());
    await new Promise((r) => setImmediate(r));
  }
  tree = h.render(() => pageMod.default());

  const playerBadge = findNodes(tree, (n) => n.props?.['data-testid'] === 'player-external-badge')[0];
  assert.ok(playerBadge);
  assert.match(getText(playerBadge), /Chỉ mở trên YouTube/);
  const playerYoutubeLink = findNodes(tree, (n) => n.props?.testId === 'open-on-youtube-link')[0];
  assert.ok(playerYoutubeLink);
  assert.equal(playerYoutubeLink.props.url, 'https://www.youtube.com/watch?v=SSubOeKMP2c&t=0');
  assert.ok(playerYoutubeLink.props.className.includes('min-h-[44px]'));

  const rowYoutubeLink = findNodes(tree, (n) => n.props?.testId === 'episode-row-youtube-link')[0];
  assert.ok(rowYoutubeLink);
  assert.equal(rowYoutubeLink.props.url, 'https://www.youtube.com/watch?v=SSubOeKMP2c&t=0');
  assert.ok(rowYoutubeLink.props.className.includes('min-h-[44px]'));
  assert.match(getText(rowYoutubeLink), /Mở trên YouTube/);
  h.unmount();

  const filmH = makeReactHarness();
  const filmShelfMod = loadTsModule('features/movies/components/FilmShelf.tsx', {
    React: filmH.react,
    require(name) {
      if (name === 'react') return { __esModule: true, default: filmH.react, ...filmH.react };
      if (name === 'react-router') return { Link: 'route-link' };
      if (name === '@/lib/labels') return labels;
      if (name.includes('playbackSource')) return playbackSource;
      if (name.includes('mediaRouting')) return { buildMediaItemHref: () => '/series/ser_ori?episode=ep_ori_s05_0001' };
      if (name.includes('ArtworkImage')) return { ArtworkImage: 'ArtworkImage' };
      if (name.includes('LibraryControls')) return { QuickSaveButtons: 'QuickSaveButtons' };
      return {};
    },
  });
  const gridTree = filmH.render(() =>
    filmShelfMod.FilmGrid({
      movies: [
        {
          id: 'ep_ori_s05_0001',
          title: 'Nhiệm Vụ Bất Khả Thi',
          year: 2011,
          rating: 0,
          averageRating: null,
          ratingCount: 0,
          runtime: '22m',
          genres: ['animation', 'comedy', 'family'],
          description: '',
          poster: '',
          backdrop: '',
          item_type: 'episode',
          series_id: 'ser_ori',
          externalOnly: true,
          youtubeVideoId: 'SSubOeKMP2c',
        },
      ],
    })
  );
  const gridBadge = findNodes(gridTree, (n) => n.props?.['data-testid'] === 'film-external-badge')[0];
  assert.ok(gridBadge);
  assert.match(getText(gridBadge), /Chỉ mở trên YouTube/);
  const gridYtLink = findNodes(gridTree, (n) => n.props?.testId === 'film-open-youtube-link')[0];
  assert.ok(gridYtLink);
  assert.equal(gridYtLink.props.url, 'https://www.youtube.com/watch?v=SSubOeKMP2c&t=0');
  assert.ok(gridYtLink.props.className.includes('min-h-[44px]'));

  const cardH = makeReactHarness();
  const movieCardMod = loadTsModule('features/movies/components/MovieCard.tsx', {
    require(name) {
      if (name === 'react') return { __esModule: true, default: cardH.react, ...cardH.react };
      if (name === 'react-router') return { Link: 'route-link' };
      if (name === 'lucide-react') return new Proxy({}, { get: () => 'Icon' });
      if (name === '@/lib/labels') return labels;
      if (name.includes('playbackSource')) return playbackSource;
      if (name.includes('mediaRouting')) return { buildMediaItemHref: () => '/series/ser_ori?episode=ep_ori_s05_0001' };
      if (name.includes('WatchlistContext')) {
        return {
          useWatchlist: () => ({
            isWatchlisted: () => false,
            toggleWatchlist() {},
            isFavorite: () => false,
            toggleFavorite() {},
            isActionPending: () => false,
            isActionUnconfirmed: () => false,
            getActionIntent: () => null,
          }),
        };
      }
      return {};
    },
  });
  const cardTree = cardH.render(() =>
    movieCardMod.MovieCard({
      movie: {
        id: 'ep_ori_s05_0001',
        title: 'Nhiệm Vụ Bất Khả Thi',
        year: 2011,
        rating: 0,
        runtime: '22m',
        genres: ['animation'],
        description: '',
        poster: '',
        backdrop: '',
        externalOnly: true,
        youtubeVideoId: 'SSubOeKMP2c',
      },
      fluid: true,
    })
  );
  assert.ok(findNodes(cardTree, (n) => n.props?.['data-testid'] === 'movie-external-badge')[0]);
  const mcYtLink = findNodes(cardTree, (n) => n.props?.testId === 'movie-open-youtube-link')[0];
  assert.ok(mcYtLink);
  assert.equal(mcYtLink.props.url, 'https://www.youtube.com/watch?v=SSubOeKMP2c&t=0');
});

test('9. Finding 5: Canonical genre ids and legacy 12-string multilingual genre lists map cleanly to Vietnamese labels on every card', () => {
  const legacyDump = [
    'animation', 'Hoạt hình', 'Animation', '애니메이션',
    'comedy', 'Hài hước', 'Comedy', '코미디',
    'family', 'Gia đình', 'Family', '가족',
  ];
  const canonicalIds = ['animation', 'comedy', 'family', 'school', 'slice_of_life'];

  assert.deepEqual([...labels.normalizeGenreLabels(legacyDump)], ['Hoạt hình', 'Hài hước', 'Gia đình']);
  assert.equal(labels.formatGenreList(legacyDump), 'Hoạt hình, Hài hước, Gia đình');
  assert.equal(labels.chatGenres(legacyDump), 'Hoạt hình, Hài hước, Gia đình');
  assert.deepEqual([...labels.normalizeGenreLabels(canonicalIds)], [
    'Hoạt hình', 'Hài hước', 'Gia đình', 'Học đường', 'Đời thường',
  ]);
  assert.equal(labels.chatGenres(canonicalIds), 'Hoạt hình, Hài hước, Gia đình, Học đường, Đời thường');
  assert.equal(labels.genreLabel('slice_of_life'), 'Đời thường');
  assert.equal(labels.genreLabel('일상'), 'Đời thường');
  assert.equal(labels.genreLabel('학원'), 'Học đường');
  assert.equal(labels.genreLabel('Unlisted'), 'Unlisted');

  const h = makeReactHarness();
  const filmShelfMod = loadTsModule('features/movies/components/FilmShelf.tsx', {
    React: h.react,
    require(name) {
      if (name === '@/lib/labels') return labels;
      if (name === 'react') return { __esModule: true, default: h.react, ...h.react };
      if (name === 'react-router') return { Link: 'route-link' };
      if (name === '@/lib/labels') return labels;
      if (name.includes('mediaRouting')) return { buildMediaItemHref: () => '/series/ser_doraemon?episode=ep1' };
      if (name.includes('ArtworkImage')) return { ArtworkImage: 'ArtworkImage' };
      if (name.includes('LibraryControls')) return { QuickSaveButtons: 'QuickSaveButtons' };
      return {};
    },
  });
  const gridTree = h.render(() =>
    filmShelfMod.FilmGrid({
      movies: [
        {
          id: 'ep_rec_legacy',
          title: 'Tập gợi ý cũ',
          year: 2015,
          rating: 0,
          runtime: '10m',
          genres: legacyDump,
          description: '',
          poster: '',
          backdrop: '',
        },
        {
          id: 'ep_rec_canonical',
          title: 'Tập gợi ý chuẩn',
          year: 2015,
          rating: 0,
          runtime: '10m',
          genres: ['slice_of_life', 'school', 'family'],
          description: '',
          poster: '',
          backdrop: '',
        },
      ],
    })
  );
  const genreContainers = findNodes(gridTree, (n) => n.props?.['data-testid'] === 'film-card-genres');
  assert.equal(genreContainers.length, 2);
  assert.equal(getText(genreContainers[0]), 'Hoạt hình Hài hước Gia đình');
  assert.equal(getText(genreContainers[1]), 'Đời thường Học đường Gia đình');

  const cardH = makeReactHarness();
  const movieCardMod = loadTsModule('features/movies/components/MovieCard.tsx', {
    require(name) {
      if (name === 'react') return { __esModule: true, default: cardH.react, ...cardH.react };
      if (name === 'react-router') return { Link: 'route-link' };
      if (name === 'lucide-react') return new Proxy({}, { get: () => 'Icon' });
      if (name === '@/lib/labels') return labels;
      if (name.includes('mediaRouting')) return { buildMediaItemHref: () => '/movies/m1' };
      if (name.includes('WatchlistContext')) {
        return {
          useWatchlist: () => ({
            isWatchlisted: () => false,
            toggleWatchlist() {},
            isFavorite: () => false,
            toggleFavorite() {},
            isActionPending: () => false,
            isActionUnconfirmed: () => false,
            getActionIntent: () => null,
          }),
        };
      }
      return {};
    },
  });
  const movieCardTree = cardH.render(() =>
    movieCardMod.MovieCard({
      movie: {
        id: 'm1',
        title: 'Doraemon',
        year: 2005,
        rating: 0,
        runtime: '22m',
        genres: [...legacyDump, 'slice_of_life'],
        description: '',
        poster: '',
        backdrop: '',
      },
      fluid: true,
    })
  );
  const movieCardGenres = findNodes(movieCardTree, (n) => n.props?.['data-testid'] === 'movie-card-genres')[0];
  assert.equal(getText(movieCardGenres), 'Hoạt hình, Hài hước, Gia đình, Đời thường');

  const seriesCardService = loadTsModule('lib/services/seriesCardService.ts', {
    require: (name) => (name === '@/lib/labels' ? labels : {}),
  });
  const normalizedCard = seriesCardService.normalizeSeriesCard({
    id: 'ser_doraemon',
    title: 'Doraemon',
    genres: [
      { id: 'slice_of_life', label: 'slice_of_life' },
      { id: '일상', label: '일상' },
      { id: 'school', label: 'School' },
      { id: 'family', label: '가족' },
    ],
  });
  assert.deepEqual(
    [...normalizedCard.genres.map((g) => g.label)],
    ['Đời thường', 'Học đường', 'Gia đình']
  );

  const seriesCardH = makeReactHarness();
  const browseUtils = loadTsModule('features/series/seriesBrowseUtils.ts', {
    require: (name) => (name === '@/lib/labels' ? labels : {}),
  });
  const seriesCardMod = loadTsModule('features/series/components/SeriesCard.tsx', {
    require(name) {
      if (name === 'react') return { __esModule: true, default: seriesCardH.react, ...seriesCardH.react };
      if (name === 'react-router') return { Link: 'route-link' };
      if (name === 'lucide-react') return { Play: 'Play' };
      if (name === '@/lib/labels') return labels;
      if (name.includes('ArtworkImage')) return { ArtworkImage: 'ArtworkImage' };
      if (name.includes('LibraryControls')) return { QuickSaveButtons: 'QuickSaveButtons' };
      if (name.includes('seriesBrowseUtils')) return browseUtils;
      return {};
    },
  });
  const seriesCardTree = seriesCardH.render(() =>
    seriesCardMod.SeriesCard({
      card: {
        ...normalizedCard,
        genres: [
          { id: 'animation', label: 'Animation' },
          { id: '애니메이션', label: '애니메이션' },
          { id: 'slice_of_life', label: 'slice_of_life' },
          { id: 'comedy', label: '코미디' },
        ],
      },
    })
  );
  const seriesGenresText = getText(findNodes(seriesCardTree, (n) => n.props?.['data-testid'] === 'series-card-genres')[0]);
  assert.equal(seriesGenresText, 'Hoạt hình Đời thường Hài hước');
});

test('10. Finding 7: Remaining secondary links/buttons meet 44px touch targets and favicon assets exist without 404', () => {
  const navSource = fs.readFileSync(path.resolve(root, 'components/layout/Navbar.tsx'), 'utf8');
  assert.ok(navSource.includes('flex min-h-[44px] items-center justify-between rounded-lg px-3.5 py-2.5'));

  const cinebotSource = fs.readFileSync(path.resolve(root, 'features/chat/components/CineBot.tsx'), 'utf8');
  assert.equal(cinebotSource.includes('min-h-10'), false);

  const playerPanelSource = fs.readFileSync(path.resolve(root, 'features/series/components/EpisodePlayerPanel.tsx'), 'utf8');
  assert.ok(!playerPanelSource.includes('testId="youtube-external-link"'));
  assert.match(playerPanelSource, /data-testid="youtube-pause-cover"[\s\S]*?min-h-\[48px\]/);

  const bannerSource = fs.readFileSync(path.resolve(root, 'features/series/components/SeriesDetailBanner.tsx'), 'utf8');
  assert.match(bannerSource, /data-testid="series-breadcrumb-link"[\s\S]*?min-h-\[44px\] min-w-\[44px\]/);

  const css = fs.readFileSync(path.resolve(root, 'app/globals.css'), 'utf8');
  assert.match(css, /\.topic-chip\s*\{[^}]*min-height:\s*44px[^}]*min-width:\s*44px/);

  const indexHtml = fs.readFileSync(path.resolve(root, 'index.html'), 'utf8');
  assert.ok(indexHtml.includes('href="/favicon.ico"'));
  assert.ok(indexHtml.includes('href="/favicon.svg"'));

  const icoPath = path.resolve(root, 'public/favicon.ico');
  assert.ok(fs.existsSync(icoPath));
  const icoBuf = fs.readFileSync(icoPath);
  assert.ok(icoBuf.length > 50 && icoBuf.length < 4096);
  assert.equal(icoBuf.readUInt16LE(0), 0);
  assert.equal(icoBuf.readUInt16LE(2), 1);
  assert.equal(icoBuf.readUInt16LE(4), 1);
  assert.deepEqual([...icoBuf.subarray(22, 26)], [0x89, 0x50, 0x4e, 0x47]);

  const svgPath = path.resolve(root, 'public/favicon.svg');
  assert.ok(fs.existsSync(svgPath));
  const svgText = fs.readFileSync(svgPath, 'utf8');
  assert.ok(svgText.includes('<svg') && svgText.length < 1024);
});
