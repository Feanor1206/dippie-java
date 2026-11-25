import { labels } from './labels_fixture.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '..');

function loadTsModule(relativePath, extraSandbox = {}) {
  const fullPath = path.resolve(root, relativePath);
  const source = fs.readFileSync(fullPath, 'utf8');
  const compiled = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
      esModuleInterop: true,
    },
  }).outputText;
  const moduleObj = { exports: {} };
  const sandbox = {
    module: moduleObj,
    exports: moduleObj.exports,
    URLSearchParams,
    encodeURIComponent,
    decodeURIComponent,
    AbortController,
    DOMException,
    setTimeout,
    clearTimeout,
    console,
    ...extraSandbox,
  };
  vm.runInNewContext(compiled, sandbox, { filename: fullPath });
  return moduleObj.exports;
}

const playbackSource = loadTsModule('lib/services/playbackSource.ts');
const mediaRouting = loadTsModule('lib/services/mediaRouting.ts');

function find(node, predicate) {
  if (!node || typeof node !== 'object') return null;
  if (Array.isArray(node)) {
    for (const child of node) {
      const hit = find(child, predicate);
      if (hit) return hit;
    }
    return null;
  }
  if (predicate(node)) return node;
  return find(node.props?.children, predicate);
}

function textContent(node) {
  if (node === null || node === undefined || typeof node === 'boolean') return '';
  if (typeof node === 'string' || typeof node === 'number') return String(node);
  if (Array.isArray(node)) return node.map(textContent).join(' ');
  if (typeof node === 'object') return textContent(node.props?.children);
  return '';
}

function createSeriesPageHarness(options = {}) {
  const pageFile = path.resolve(root, 'app/series/[id]/page.tsx');
  const source = fs.readFileSync(pageFile, 'utf8');
  const code = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      jsx: ts.JsxEmit.React,
      esModuleInterop: true,
      target: ts.ScriptTarget.ES2022,
    },
  }).outputText;

  const episode = options.episode || {
    id: 'ep1',
    title: 'Tập 1: Bắt đầu',
    seasonId: 'season1',
    number: 1,
    durationMs: 600000,
  };
  const episodes = options.episodes || [episode];
  const seasons = options.seasons || [{ id: 'season1', number: 1, title: 'Mùa 1' }];

  let slots = [];
  let index = 0;
  let effects = [];
  let tree = null;
  let nextTimerId = 1;
  const timers = new Map();
  const saveCalls = [];
  let currentUrl = `http://localhost:3000/series/ser1${options.query ? `?${options.query}` : ''}`;

  const fakeSetTimeout = (fn, delay) => {
    const id = nextTimerId++;
    timers.set(id, { fn, delay });
    return id;
  };
  const fakeClearTimeout = (id) => {
    timers.delete(id);
  };

  const react = {
    useState(initial) {
      const i = index++;
      if (!(i in slots)) {
        slots[i] = { value: typeof initial === 'function' ? initial() : initial };
      }
      return [
        slots[i].value,
        (next) => {
          slots[i].value = typeof next === 'function' ? next(slots[i].value) : next;
        },
      ];
    },
    useRef(initial) {
      const i = index++;
      if (!(i in slots)) slots[i] = { current: initial };
      return slots[i];
    },
    useEffect(effect, deps) {
      const i = index++;
      const previous = slots[i];
      if (!previous || !deps || deps.some((v, j) => !Object.is(v, previous.deps[j]))) {
        slots[i] = { deps, cleanup: previous?.cleanup };
        effects.push(() => {
          slots[i].cleanup?.();
          slots[i].cleanup = effect();
        });
      }
    },
    createElement(type, props, ...children) {
      return { type, props: { ...props, children } };
    },
  };

  const windowMock = {
    location: {
      get href() {
        return currentUrl;
      },
    },
    history: {
      replaceState(_state, _title, url) {
        currentUrl = String(url);
      },
    },
    setTimeout: fakeSetTimeout,
    clearTimeout: fakeClearTimeout,
  };

  const documentMock = {
    hidden: false,
    addEventListener() {},
    removeEventListener() {},
  };

  const moduleObj = { exports: {} };
  vm.runInNewContext(
    code,
    {
      module: moduleObj,
      exports: moduleObj.exports,
      AbortController,
      URL,
      URLSearchParams,
      window: windowMock,
      document: documentMock,
      setTimeout: fakeSetTimeout,
      clearTimeout: fakeClearTimeout,
      console,
      require(name) {
        if (name === '@/lib/labels') return labels;
        if (name === 'react') return { __esModule: true, default: react, ...react };
        if (name === 'react-router') {
          return {
            Link: 'route-link',
            useParams: () => ({ id: 'ser1' }),
            useSearchParams: () => [new URLSearchParams(options.query || '')],
          };
        }
        if (name.includes('catalogService')) {
          return {
            getCatalogSeriesDetail: async () => ({
              series: { id: 'ser1', title: 'Xin Chào Jadoo' },
              seasons,
              episodes,
            }),
            getCatalogEpisodeDetail: options.getCatalogEpisodeDetail,
          };
        }
        if (name.includes('episodeNavigation')) {
          return {
            sortCatalogSeasons: (items) => items,
            getInitialSeasonId: () => seasons[0]?.id || null,
            getInitialEpisode: (_items, _seasonId, targetId) =>
              episodes.find((ep) => ep.id === targetId) || episodes[0] || null,
            getEpisodesForSeason: (items) => items,
            getAdjacentEpisode: () => null,
          };
        }
        if (name.includes('mediaService')) {
          return {
            resolveStreamUrl: (url) => url,
            getStoredAuthToken: () => options.authToken ?? null,
            requestPlaybackGrant: options.requestPlaybackGrant || (async () => null),
            requestPlaybackGrantResult:
              options.requestPlaybackGrantResult ||
              (async () => ({ ok: false, status: 404, grant: null })),
          };
        }
        if (name.includes('mediaRouting')) return mediaRouting;
        if (name.includes('playbackSource')) return playbackSource;
        if (name.includes('useWatchProgress')) {
          return {
            useWatchProgress: () => ({
              records: options.progressRecords || [],
              saveProgress(draft, reason) {
                saveCalls.push({ draft, reason });
                return Promise.resolve(null);
              },
            }),
          };
        }
        if (name.includes('watchProgressService')) {
          return {
            readAutoNextPreference: () => false,
            writeAutoNextPreference: () => {},
            isUsableResumeProgress: () => false,
          };
        }
        return {};
      },
    },
    { filename: pageFile }
  );

  function render() {
    index = 0;
    effects = [];
    tree = moduleObj.exports.default();
    for (const effect of effects) effect();
    return tree;
  }

  async function settle(rounds = 6) {
    for (let i = 0; i < rounds; i++) {
      render();
      await new Promise(setImmediate);
    }
    return render();
  }

  function fireTimersByDelay(minDelay) {
    const matching = [...timers.entries()].filter(([, t]) => t.delay >= minDelay);
    for (const [id, timer] of matching) {
      timers.delete(id);
      timer.fn();
    }
  }

  function unmount() {
    for (const slot of slots) {
      slot?.cleanup?.();
    }
    timers.clear();
  }

  return {
    render,
    settle,
    getTree: () => tree,
    getCurrentUrl: () => currentUrl,
    timers,
    fireTimersByDelay,
    saveCalls,
    unmount,
  };
}

test('1. Source selection table: available true/false/absent x youtube x youtubeFallback', () => {
  const episode = { id: 'ep1', title: 'Episode 1', durationMs: 600000 };
  const ytPrimary = { videoId: '11111111111', canEmbed: true };
  const ytPrimaryNoEmbed = { videoId: '11111111111', canEmbed: false };
  const ytFallback = { videoId: '22222222222', canEmbed: true };
  const ytFallbackNoEmbed = { videoId: '22222222222', canEmbed: false };
  const ytInvalid = { videoId: 'https://youtube.com/watch?v=bad', canEmbed: true };

  const makeAsset = (available) => ({
    id: 'ast1',
    contentType: 'video',
    role: 'feature',
    storageProvider: 'drive',
    ...(available === undefined ? {} : { available }),
  });

  const cases = [
    {
      name: 'available=true prefers file over youtube and fallback',
      mediaAssets: [makeAsset(true)],
      youtube: ytPrimary,
      youtubeFallback: ytFallback,
      expectKind: 'file',
      expectAssetId: 'ast1',
    },
    {
      name: 'available absent (older backend) prefers file over fallback',
      mediaAssets: [makeAsset(undefined)],
      youtube: null,
      youtubeFallback: ytFallback,
      expectKind: 'file',
      expectAssetId: 'ast1',
    },
    {
      name: 'available=false falls back to embeddable youtube',
      mediaAssets: [makeAsset(false)],
      youtube: ytPrimary,
      youtubeFallback: ytFallback,
      expectKind: 'youtube',
      expectOrigin: 'youtube',
      expectVideoId: '11111111111',
      expectFallback: true,
    },
    {
      name: 'available=false falls back to embeddable youtubeFallback when youtube is null',
      mediaAssets: [makeAsset(false)],
      youtube: null,
      youtubeFallback: ytFallback,
      expectKind: 'youtube',
      expectOrigin: 'youtubeFallback',
      expectVideoId: '22222222222',
      expectFallback: true,
    },
    {
      name: 'available=false skips non-embeddable youtube when youtubeFallback is embeddable',
      mediaAssets: [makeAsset(false)],
      youtube: ytPrimaryNoEmbed,
      youtubeFallback: ytFallback,
      expectKind: 'youtube',
      expectOrigin: 'youtubeFallback',
      expectVideoId: '22222222222',
      expectFallback: true,
    },
    {
      name: 'available=false skips invalid youtube id and uses youtubeFallback',
      mediaAssets: [makeAsset(false)],
      youtube: ytInvalid,
      youtubeFallback: ytFallback,
      expectKind: 'youtube',
      expectOrigin: 'youtubeFallback',
      expectVideoId: '22222222222',
      expectFallback: true,
    },
    {
      name: 'available=false with non-embeddable youtubeFallback is unavailable with external watch link',
      mediaAssets: [makeAsset(false)],
      youtube: null,
      youtubeFallback: ytFallbackNoEmbed,
      expectKind: 'unavailable',
      expectReason: 'file_unreachable_youtube_external',
      expectWatchId: '22222222222',
      expectCanRetryFile: true,
    },
    {
      name: 'available=false with no youtube sources is unavailable (file_unreachable)',
      mediaAssets: [makeAsset(false)],
      youtube: null,
      youtubeFallback: null,
      expectKind: 'unavailable',
      expectReason: 'file_unreachable',
      expectWatchId: null,
      expectCanRetryFile: true,
    },
    {
      name: 'no file assets + embeddable youtube plays youtube without fallback notice',
      mediaAssets: [],
      youtube: ytPrimary,
      youtubeFallback: null,
      expectKind: 'youtube',
      expectOrigin: 'youtube',
      expectVideoId: '11111111111',
      expectFallback: false,
    },
    {
      name: 'no file assets + youtubeFallback plays youtubeFallback without fallback notice',
      mediaAssets: [],
      youtube: null,
      youtubeFallback: ytFallback,
      expectKind: 'youtube',
      expectOrigin: 'youtubeFallback',
      expectVideoId: '22222222222',
      expectFallback: false,
    },
    {
      name: 'no file assets + non-embeddable youtube is unavailable with watch link and no file retry',
      mediaAssets: [],
      youtube: ytPrimaryNoEmbed,
      youtubeFallback: null,
      expectKind: 'unavailable',
      expectReason: 'youtube_embed_disabled',
      expectWatchId: '11111111111',
      expectCanRetryFile: false,
    },
    {
      name: 'no file assets + no youtube is unavailable (no_source)',
      mediaAssets: [],
      youtube: null,
      youtubeFallback: null,
      expectKind: 'unavailable',
      expectReason: 'no_source',
      expectWatchId: null,
      expectCanRetryFile: false,
    },
  ];

  for (const row of cases) {
    const result = playbackSource.selectPlaybackSource({
      episodeDetail: {
        episode,
        segments: [],
        scenes: [],
        mediaAssets: row.mediaAssets,
        youtube: row.youtube,
        youtubeFallback: row.youtubeFallback,
      },
      selectedEpisodeId: 'ep1',
      startTimeMs: 30000,
    });

    assert.equal(result.kind, row.expectKind, row.name);
    if (row.expectKind === 'file') {
      assert.equal(result.asset.id, row.expectAssetId, row.name);
    } else if (row.expectKind === 'youtube') {
      assert.equal(result.origin, row.expectOrigin, row.name);
      assert.equal(result.youtube.videoId, row.expectVideoId, row.name);
      assert.equal(result.isFallback, row.expectFallback, row.name);
      assert.equal(result.embedUrl, `https://www.youtube-nocookie.com/embed/${row.expectVideoId}?start=30`, row.name);
    } else {
      assert.equal(result.reason, row.expectReason, row.name);
      assert.equal(result.canRetryFile, row.expectCanRetryFile, row.name);
      assert.equal(result.youtubeVideoId, row.expectWatchId, row.name);
      if (row.expectWatchId) {
        assert.equal(result.youtubeWatchUrl, `https://www.youtube.com/watch?v=${row.expectWatchId}&t=30`, row.name);
      } else {
        assert.equal(result.youtubeWatchUrl, null, row.name);
      }
    }
  }
});

test('2. Source selection respects manual asset choice and falls back to alternate available file for automatic selection', () => {
  const episode = { id: 'ep1', title: 'Episode 1', durationMs: 600000 };
  const detail = {
    episode,
    segments: [],
    scenes: [],
    mediaAssets: [
      { id: 'ast_down', contentType: 'video', role: 'feature', storageProvider: 'drive', available: false },
      { id: 'ast_up', contentType: 'video', role: 'feature', storageProvider: 'local', available: true },
    ],
    youtube: null,
    youtubeFallback: { videoId: '9aFcot7_7Q4', canEmbed: true },
  };

  const autoResult = playbackSource.selectPlaybackSource({
    episodeDetail: detail,
    selectedEpisodeId: 'ep1',
    preferredAssetId: 'ast_down',
  });
  assert.equal(autoResult.kind, 'file');
  assert.equal(autoResult.asset.id, 'ast_up');

  const manualUnavailable = playbackSource.selectPlaybackSource({
    episodeDetail: detail,
    selectedEpisodeId: 'ep1',
    manualAssetId: 'ast_down',
  });
  assert.equal(manualUnavailable.kind, 'youtube');
  assert.equal(manualUnavailable.isFallback, true);
  assert.equal(manualUnavailable.failedAsset.id, 'ast_down');
});

test('3. Fallback start-time carry-over helper preserves priority and clamps invalid/oversized values', () => {
  assert.equal(
    playbackSource.resolveFallbackStartTimeMs({
      videoCurrentTimeSec: 81.9,
      pendingSeekSec: 40,
      trackedTimeSec: 20,
      deepLinkTimeMs: '15000',
    }),
    81900
  );
  assert.equal(
    playbackSource.resolveFallbackStartTimeMs({
      videoCurrentTimeSec: 0,
      pendingSeekSec: 45.5,
      deepLinkTimeMs: '10000',
    }),
    45500
  );
  assert.equal(
    playbackSource.resolveFallbackStartTimeMs({
      videoCurrentTimeSec: 0,
      pendingSeekSec: null,
      trackedTimeSec: 0,
      deepLinkTimeMs: '62000',
    }),
    62000
  );
  assert.equal(
    playbackSource.resolveFallbackStartTimeMs({
      segmentStartMs: 120000,
      sceneStartMs: 5000,
    }),
    120000
  );
  assert.equal(playbackSource.resolveYoutubeStartSeconds(81900, 600000), 81);
  assert.equal(playbackSource.resolveYoutubeStartSeconds(600000, 600000), 0);
  assert.equal(playbackSource.resolveYoutubeStartSeconds(-5000, 600000), 0);
  assert.equal(playbackSource.resolveYoutubeStartSeconds('invalid', 600000), 0);
});

test('4. requestPlaybackGrantResult reports HTTP failure status codes without breaking requestPlaybackGrant', async () => {
  const mkService = (fetchImpl) =>
    loadTsModule('lib/services/mediaService.ts', {
      fetch: fetchImpl,
      window: { localStorage: { getItem: () => 'tok' } },
      localStorage: { getItem: () => 'tok' },
    });

  const okSvc = mkService(async () => ({
    ok: true,
    status: 200,
    json: async () => ({ assetId: 'a1', grant: 'g1', streamUrl: '/api/media/stream/a1?grant=g1', expiresIn: 60 }),
  }));
  const okRes = await okSvc.requestPlaybackGrantResult('a1');
  assert.equal(okRes.ok, true);
  assert.equal(okRes.status, 200);
  assert.equal(okRes.grant.streamUrl, '/api/media/stream/a1?grant=g1');
  assert.equal((await okSvc.requestPlaybackGrant('a1')).streamUrl, '/api/media/stream/a1?grant=g1');

  for (const status of [404, 403, 503]) {
    const errSvc = mkService(async () => ({ ok: false, status }));
    const errRes = await errSvc.requestPlaybackGrantResult('a1');
    assert.equal(errRes.ok, false);
    assert.equal(errRes.status, status);
    assert.equal(errRes.grant, null);
    assert.equal(await errSvc.requestPlaybackGrant('a1'), null);
  }
});

test('5. Proactive fallback (available: false) renders YouTube fallback with deep-link start time and retry recovers file', async () => {
  let fetchCount = 0;
  const harness = createSeriesPageHarness({
    query: 'episode=ep1&t=42000&timeline=file&asset=ast1',
    getCatalogEpisodeDetail: async () => {
      fetchCount++;
      return {
        episode: { id: 'ep1', title: 'Tập 1: Bắt đầu', durationMs: 600000 },
        segments: [],
        scenes: [],
        mediaAssets: [
          {
            id: 'ast1',
            contentType: 'video',
            role: 'feature',
            storageProvider: 'drive',
            available: fetchCount >= 2,
          },
        ],
        youtube: null,
        youtubeFallback: { videoId: '9aFcot7_7Q4', canEmbed: true },
      };
    },
  });

  let tree = await harness.settle();
  const iframe = find(tree, (n) => n.props?.['data-testid'] === 'series-youtube-player');
  assert.ok(iframe, 'YouTube fallback iframe must render when file asset has available=false');
  assert.equal(iframe.props.src, 'https://www.youtube-nocookie.com/embed/9aFcot7_7Q4?start=42');

  const notice = find(tree, (n) => n.props?.['data-testid'] === 'playback-fallback-notice');
  assert.ok(notice, 'Fallback inline notice must render');
  assert.equal(notice.props.role, 'status');
  assert.match(textContent(notice), /Tệp video chưa sẵn sàng, đang phát bản YouTube\./);

  const retryBtn = find(notice, (n) => n.props?.['data-testid'] === 'retry-original-file');
  assert.ok(retryBtn, 'Retry original file button must exist');
  assert.match(textContent(retryBtn), /Thử lại tệp gốc/);

  retryBtn.props.onClick();
  tree = await harness.settle();
  assert.equal(fetchCount, 2);

  const video = find(tree, (n) => n.props?.['data-testid'] === 'series-video-player');
  assert.ok(video, 'Video player must recover once file becomes available on retry');
  assert.equal(video.props.src, '/api/media/stream/ast1');
  assert.equal(find(tree, (n) => n.props?.['data-testid'] === 'playback-fallback-notice'), null);
  assert.equal(harness.saveCalls.length, 0);

  harness.unmount();
});

test('6. Runtime <video> error carries currentTime to YouTube fallback and retry restores seek time', async () => {
  const harness = createSeriesPageHarness({
    query: 'episode=ep1',
    getCatalogEpisodeDetail: async () => ({
      episode: { id: 'ep1', title: 'Tập 1: Bắt đầu', durationMs: 600000 },
      segments: [],
      scenes: [],
      mediaAssets: [
        { id: 'ast1', contentType: 'video', role: 'feature', storageProvider: 'drive', available: true },
      ],
      youtube: null,
      youtubeFallback: { videoId: 'AbCdEfGhIjK', canEmbed: true },
    }),
  });

  let tree = await harness.settle();
  const video = find(tree, (n) => n.props?.['data-testid'] === 'series-video-player');
  assert.ok(video, 'Initial file video must render');

  const videoFake = {
    currentTime: 73.8,
    duration: 600,
    readyState: 2,
    paused: false,
    src: '/api/media/stream/ast1',
    play: async () => {},
  };
  video.props.ref.current = videoFake;
  video.props.onError();

  tree = await harness.settle();
  const iframe = find(tree, (n) => n.props?.['data-testid'] === 'series-youtube-player');
  assert.ok(iframe, 'YouTube fallback must activate on video error');
  assert.equal(iframe.props.src, 'https://www.youtube-nocookie.com/embed/AbCdEfGhIjK?start=73');

  const notice = find(tree, (n) => n.props?.['data-testid'] === 'playback-fallback-notice');
  assert.ok(notice, 'Inline fallback notice must appear');
  assert.match(textContent(notice), /Tệp video chưa sẵn sàng, đang phát bản YouTube\./);

  const retryBtn = find(notice, (n) => n.props?.['data-testid'] === 'retry-original-file');
  retryBtn.props.onClick();
  tree = await harness.settle();

  const recoveredVideo = find(tree, (n) => n.props?.['data-testid'] === 'series-video-player');
  assert.ok(recoveredVideo, 'Clicking retry must return to video element');
  const nextVideoFake = { currentTime: 0, duration: 600, readyState: 1, play: async () => {} };
  recoveredVideo.props.ref.current = nextVideoFake;
  recoveredVideo.props.onLoadedMetadata();
  assert.equal(Math.floor(nextVideoFake.currentTime), 73);
  assert.equal(harness.saveCalls.length, 0);

  harness.unmount();
});

test('7. Runtime grant 404/403/500 failures switch automatically to youtubeFallback or unavailable card', async () => {
  for (const status of [404, 403, 500]) {
    const harness = createSeriesPageHarness({
      query: 'episode=ep1&t=25000',
      authToken: 'valid-token',
      requestPlaybackGrantResult: async () => ({ ok: false, status, grant: null }),
      getCatalogEpisodeDetail: async () => ({
        episode: { id: 'ep1', title: 'Tập 1', durationMs: 600000 },
        segments: [],
        scenes: [],
        mediaAssets: [
          { id: 'ast1', contentType: 'video', role: 'feature', storageProvider: 'drive' },
        ],
        youtube: null,
        youtubeFallback: { videoId: 'ZyXwVuTsRqP', canEmbed: true },
      }),
    });

    const tree = await harness.settle();
    const iframe = find(tree, (n) => n.props?.['data-testid'] === 'series-youtube-player');
    assert.ok(iframe, `HTTP ${status} grant failure must fall back to YouTube`);
    assert.equal(iframe.props.src, 'https://www.youtube-nocookie.com/embed/ZyXwVuTsRqP?start=25');
    assert.ok(find(tree, (n) => n.props?.['data-testid'] === 'playback-fallback-notice'));
    harness.unmount();
  }

  const noFallbackHarness = createSeriesPageHarness({
    query: 'episode=ep1',
    authToken: 'valid-token',
    requestPlaybackGrantResult: async () => ({ ok: false, status: 403, grant: null }),
    getCatalogEpisodeDetail: async () => ({
      episode: { id: 'ep1', title: 'Tập Bảo Vệ', durationMs: 600000 },
      segments: [],
      scenes: [],
      mediaAssets: [
        { id: 'ast1', contentType: 'video', role: 'feature', storageProvider: 'drive', accessPolicy: 'private' },
      ],
      youtube: null,
      youtubeFallback: null,
    }),
  });

  const cardTree = await noFallbackHarness.settle();
  const card = find(cardTree, (n) => n.props?.['data-testid'] === 'playback-unavailable-card');
  assert.ok(card, 'Grant 403 without YouTube fallback must show unavailable card');
  assert.equal(card.props.role, 'alert');
  assert.equal(card.props['aria-live'], 'assertive');
  assert.match(textContent(card), /Không phát được tập này/);
  assert.match(textContent(card), /Tập Bảo Vệ/);
  assert.ok(find(card, (n) => n.props?.['data-testid'] === 'retry-original-file'));
  noFallbackHarness.unmount();
});

test('8. Metadata timeout (~10s) falls back to YouTube; loadedmetadata before timeout cancels fallback', async () => {
  const timeoutHarness = createSeriesPageHarness({
    query: 'episode=ep1&t=19000',
    getCatalogEpisodeDetail: async () => ({
      episode: { id: 'ep1', title: 'Tập Treo', durationMs: 600000 },
      segments: [],
      scenes: [],
      mediaAssets: [
        { id: 'ast1', contentType: 'video', role: 'feature', storageProvider: 'drive' },
      ],
      youtube: null,
      youtubeFallback: { videoId: 'TimeoutYt01', canEmbed: true },
    }),
  });

  let tree = await timeoutHarness.settle();
  assert.ok(find(tree, (n) => n.props?.['data-testid'] === 'series-video-player'));
  assert.ok(timeoutHarness.timers.size > 0, 'Metadata timeout timer must be scheduled');

  timeoutHarness.fireTimersByDelay(playbackSource.PLAYBACK_METADATA_TIMEOUT_MS);
  tree = await timeoutHarness.settle();

  const iframe = find(tree, (n) => n.props?.['data-testid'] === 'series-youtube-player');
  assert.ok(iframe, 'Metadata timeout must automatically switch to YouTube fallback');
  assert.equal(iframe.props.src, 'https://www.youtube-nocookie.com/embed/TimeoutYt01?start=19');
  assert.ok(find(tree, (n) => n.props?.['data-testid'] === 'playback-fallback-notice'));
  timeoutHarness.unmount();

  const healthyHarness = createSeriesPageHarness({
    query: 'episode=ep1',
    getCatalogEpisodeDetail: async () => ({
      episode: { id: 'ep1', title: 'Tập Bình Thường', durationMs: 600000 },
      segments: [],
      scenes: [],
      mediaAssets: [
        { id: 'ast1', contentType: 'video', role: 'feature', storageProvider: 'drive' },
      ],
      youtube: null,
      youtubeFallback: { videoId: 'TimeoutYt01', canEmbed: true },
    }),
  });

  tree = await healthyHarness.settle();
  const video = find(tree, (n) => n.props?.['data-testid'] === 'series-video-player');
  video.props.onLoadedMetadata();
  tree = await healthyHarness.settle();
  assert.equal(healthyHarness.timers.size, 0, 'Loaded metadata must cancel metadata timeout timer');
  assert.ok(find(tree, (n) => n.props?.['data-testid'] === 'series-video-player'));
  healthyHarness.unmount();
});

test('9. Unavailable card displays title, heading, reason, optional YouTube link, and a11y attributes', async () => {
  const noSourceHarness = createSeriesPageHarness({
    query: 'episode=ep1',
    getCatalogEpisodeDetail: async () => ({
      episode: { id: 'ep1', title: 'Tập Trống', durationMs: 600000 },
      segments: [],
      scenes: [],
      mediaAssets: [],
      youtube: null,
      youtubeFallback: null,
    }),
  });
  const noSourceTree = await noSourceHarness.settle();
  const noSourceCard = find(noSourceTree, (n) => n.props?.['data-testid'] === 'playback-unavailable-card');
  assert.ok(noSourceCard);
  assert.equal(noSourceCard.props.role, 'alert');
  assert.equal(noSourceCard.props['aria-live'], 'assertive');
  assert.match(textContent(noSourceCard), /Tập Trống/);
  assert.match(textContent(noSourceCard), /Không phát được tập này/);
  assert.match(textContent(noSourceCard), /chưa có nguồn video/i);
  assert.equal(find(noSourceCard, (n) => n.props?.['data-testid'] === 'open-on-youtube-link'), null);
  assert.equal(find(noSourceTree, (n) => n.type === 'video'), null);
  noSourceHarness.unmount();

  const externalYtHarness = createSeriesPageHarness({
    query: 'episode=ep1&t=35000',
    getCatalogEpisodeDetail: async () => ({
      episode: { id: 'ep1', title: 'Tập Ngoài YouTube', durationMs: 600000 },
      segments: [],
      scenes: [],
      mediaAssets: [
        { id: 'ast1', contentType: 'video', role: 'feature', storageProvider: 'drive', available: false },
      ],
      youtube: null,
      youtubeFallback: { videoId: 'ExtLink1234', canEmbed: false },
    }),
  });
  const extTree = await externalYtHarness.settle();
  const extCard = find(extTree, (n) => n.props?.['data-testid'] === 'playback-unavailable-card');
  assert.ok(extCard);
  assert.equal(extCard.props.role, 'alert');
  assert.equal(extCard.props['aria-live'], 'assertive');
  assert.match(textContent(extCard), /Tập Ngoài YouTube/);
  assert.match(textContent(extCard), /Không phát được tập này/);
  const extLink = find(extCard, (n) => n.props?.['data-testid'] === 'open-on-youtube-link');
  assert.ok(extLink);
  assert.equal(extLink.props.href, 'https://www.youtube.com/watch?v=ExtLink1234&t=35');
  assert.match(textContent(extLink), /Mở trên YouTube/);
  assert.ok(find(extCard, (n) => n.props?.['data-testid'] === 'retry-original-file'));
  externalYtHarness.unmount();

  const ytOnlyHarness = createSeriesPageHarness({
    query: 'episode=ep1&t=15000',
    getCatalogEpisodeDetail: async () => ({
      episode: { id: 'ep1', title: 'Larva Tập 1', durationMs: 300000 },
      segments: [],
      scenes: [],
      mediaAssets: [],
      youtube: { videoId: 'LarvaYt0001', canEmbed: true },
      youtubeFallback: null,
    }),
  });
  const ytOnlyTree = await ytOnlyHarness.settle();
  const ytOnlyIframe = find(ytOnlyTree, (n) => n.props?.['data-testid'] === 'series-youtube-player');
  assert.ok(ytOnlyIframe);
  assert.equal(ytOnlyIframe.props.src, 'https://www.youtube-nocookie.com/embed/LarvaYt0001?start=15');
  assert.equal(find(ytOnlyTree, (n) => n.props?.['data-testid'] === 'playback-fallback-notice'), null);
  ytOnlyHarness.unmount();
});

test('10. Deep links (?episode&t&timeline&asset) and manual asset selection remain intact', async () => {
  const coverage = JSON.stringify([
    { episode_id: 'ep1', source_in_ms: 30000, source_out_ms: 120000, episode_in_ms: 0 },
  ]);
  const harness = createSeriesPageHarness({
    query: 'episode=ep1&t=5000&timeline=file&asset=ast2',
    getCatalogEpisodeDetail: async () => ({
      episode: { id: 'ep1', title: 'Tập Deep Link', durationMs: 600000 },
      segments: [],
      scenes: [],
      mediaAssets: [
        { id: 'ast1', contentType: 'video', role: 'feature', storageProvider: 'drive', available: true, coverageJson: coverage },
        { id: 'ast2', contentType: 'video', role: 'feature', storageProvider: 'drive', available: true, coverageJson: coverage },
      ],
      youtube: null,
      youtubeFallback: { videoId: 'DeepLinkYt1', canEmbed: true },
    }),
  });

  let tree = await harness.settle();
  const video = find(tree, (n) => n.props?.['data-testid'] === 'series-video-player');
  assert.ok(video);
  assert.equal(video.props.src, '/api/media/stream/ast2');
  assert.equal(
    harness.getCurrentUrl(),
    'http://localhost:3000/series/ser1?episode=ep1&t=5000&timeline=file&asset=ast2'
  );

  const videoFake = { currentTime: 0, duration: 600, readyState: 1, play: async () => {} };
  video.props.ref.current = videoFake;
  video.props.onLoadedMetadata();
  assert.equal(videoFake.currentTime, 5);

  const asset1Btn = find(tree, (n) => n.props?.['data-asset-id'] === 'ast1');
  assert.ok(asset1Btn);
  asset1Btn.props.onClick();
  tree = await harness.settle();

  const switchedVideo = find(tree, (n) => n.props?.['data-testid'] === 'series-video-player');
  assert.ok(switchedVideo);
  assert.equal(switchedVideo.props.src, '/api/media/stream/ast1');

  harness.unmount();
});
