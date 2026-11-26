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

function jsonResponse(body, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  };
}

function findNode(node, predicate) {
  if (!node || typeof node !== 'object') return null;
  if (Array.isArray(node)) return node.map((child) => findNode(child, predicate)).find(Boolean) || null;
  return predicate(node) ? node : findNode(node.props?.children, predicate);
}

const tick = () => new Promise((resolve) => setTimeout(resolve, 0));

function renderNextEpisodeAction(authState, serviceModule, initialProps, env = {}) {
  let slots = [];
  let cursor = 0;
  let pendingEffects = [];
  let props = initialProps;
  const same = (a, b) => a && b && a.length === b.length && a.every((v, i) => Object.is(v, b[i]));
  const react = {
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
    useCallback(fn, deps) {
      const i = cursor++;
      if (!slots[i] || !same(slots[i].deps, deps)) slots[i] = { fn, deps };
      return slots[i].fn;
    },
    useEffect(effect, deps) {
      const i = cursor++;
      const prev = slots[i];
      if (!prev || !deps || !same(prev.deps, deps)) {
        pendingEffects.push(() => {
          prev?.cleanup?.();
          slots[i] = { deps, cleanup: effect() };
        });
      }
    },
    createElement(type, nodeProps, ...children) {
      return { type, props: { ...nodeProps, children } };
    },
  };
  const iconStub = () => null;
  const mod = loadTsModule('../features/series/components/NextEpisodeAction.tsx', {
    window: env.window,
    document: env.document,
    require(name) {
      if (name === 'react') return react;
      if (name === 'lucide-react') return { CheckCircle2: iconStub, Play: iconStub };
      if (name.includes('AuthContext')) return { useAuth: () => authState };
      if (name.includes('nextEpisodeService')) return serviceModule;
      return {};
    },
  });

  return {
    render(nextProps = props) {
      props = nextProps;
      cursor = 0;
      const tree = mod.NextEpisodeAction(props);
      const effects = pendingEffects;
      pendingEffects = [];
      effects.forEach((fn) => fn());
      return tree;
    },
    unmount() {
      slots.forEach((slot) => slot?.cleanup?.());
    },
  };
}

test('getNextEpisode sends GET /api/users/next-episode with seriesId and Authorization header', async () => {
  const calls = [];
  const service = loadTsModule('../lib/services/nextEpisodeService.ts', {
    fetch: async (url, opts) => {
      calls.push({ url, opts });
      return jsonResponse({
        success: true,
        message: 'Thành công',
        data: {
          seriesId: 'ser/jadoo',
          episodeId: 'ep_3',
          episodeTitle: 'Tập 3',
          seasonNumber: 1,
          episodeNumber: 3,
          resumePositionMs: 125000,
          reason: 'RESUME',
        },
      });
    },
  });

  const result = await service.getNextEpisode('tok-123', 'ser/jadoo');
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, '/api/users/next-episode?seriesId=ser%2Fjadoo');
  assert.equal(calls[0].opts.headers.Authorization, 'Bearer tok-123');
  assert.equal(result.status, 'ready');
  assert.deepEqual(
    { ...result.recommendation },
    {
      seriesId: 'ser/jadoo',
      episodeId: 'ep_3',
      episodeTitle: 'Tập 3',
      seasonNumber: 1,
      episodeNumber: 3,
      resumePositionMs: 125000,
      reason: 'RESUME',
    }
  );
});

test('getNextEpisode maps data: null to completed result with Vietnamese message', async () => {
  const service = loadTsModule('../lib/services/nextEpisodeService.ts', {
    fetch: async () =>
      jsonResponse({
        success: true,
        message: 'Bạn đã xem hết bộ phim này.',
        data: null,
      }),
  });

  const result = await service.getNextEpisode('tok-123', 'ser_jadoo');
  assert.deepEqual(
    { ...result },
    {
      status: 'completed',
      message: 'Bạn đã xem hết bộ phim này.',
    }
  );
});

test('getNextEpisode propagates error status and message, rejects malformed data, and supports abort/timeout', async () => {
  const unauthorizedService = loadTsModule('../lib/services/nextEpisodeService.ts', {
    fetch: async () =>
      jsonResponse({ success: false, message: 'Chưa xác thực người dùng', data: null }, 401),
  });
  await assert.rejects(
    unauthorizedService.getNextEpisode('expired', 'ser_1'),
    (err) => err.status === 401 && err.message === 'Chưa xác thực người dùng'
  );

  const malformedService = loadTsModule('../lib/services/nextEpisodeService.ts', {
    fetch: async () =>
      jsonResponse({
        success: true,
        data: { seriesId: 's1', episodeId: 'e1', episodeTitle: 'T1', reason: 'UNKNOWN', resumePositionMs: 0 },
      }),
  });
  await assert.rejects(malformedService.getNextEpisode('tok', 's1'), (err) => err.status === 502);

  const slowService = loadTsModule('../lib/services/nextEpisodeService.ts', {
    fetch: () => new Promise(() => {}),
  });
  await assert.rejects(
    slowService.getNextEpisode('tok', 's1', { timeoutMs: 15 }),
    (err) => err.isTimeout === true && err.status === 408
  );
  const controller = new AbortController();
  const pending = slowService.getNextEpisode('tok', 's1', { signal: controller.signal });
  controller.abort();
  await assert.rejects(pending, (err) => err.isCanceled === true && err.status === 499);
});

test('formatResumeTime and formatNextEpisodeLabel format RESUME and NEXT_UNWATCHED labels accurately', () => {
  const service = loadTsModule('../lib/services/nextEpisodeService.ts', {
    fetch: async () => {
      throw new Error('unexpected');
    },
  });

  assert.equal(service.formatResumeTime(0), '00:00');
  assert.equal(service.formatResumeTime(-5000), '00:00');
  assert.equal(service.formatResumeTime(NaN), '00:00');
  assert.equal(service.formatResumeTime(9000), '00:09');
  assert.equal(service.formatResumeTime(125999), '02:05');
  assert.equal(service.formatResumeTime(3661000), '61:01');

  assert.deepEqual(
    {
      ...service.formatNextEpisodeLabel({
        reason: 'RESUME',
        episodeNumber: 3,
        resumePositionMs: 125000,
      }),
    },
    { label: 'Xem tiếp tập 3', resumeTime: '02:05' }
  );

  assert.deepEqual(
    {
      ...service.formatNextEpisodeLabel({
        reason: 'RESUME',
        episodeNumber: null,
        resumePositionMs: 45000,
      }),
    },
    { label: 'Xem tiếp', resumeTime: '00:45' }
  );

  assert.deepEqual(
    {
      ...service.formatNextEpisodeLabel({
        reason: 'NEXT_UNWATCHED',
        episodeNumber: 4,
        resumePositionMs: 0,
      }),
    },
    { label: 'Xem tập 4', resumeTime: null }
  );

  assert.deepEqual(
    {
      ...service.formatNextEpisodeLabel({
        reason: 'NEXT_UNWATCHED',
        episodeNumber: null,
        resumePositionMs: 0,
      }),
    },
    { label: 'Xem tập tiếp theo', resumeTime: null }
  );

  assert.equal(
    service.buildNextEpisodeHref(
      { seriesId: 'ser_1', episodeId: 'ep_3', reason: 'RESUME', resumePositionMs: 95000 },
      'asset_9'
    ),
    '/series/ser_1?episode=ep_3&asset=asset_9&t=95000&timeline=file'
  );

  assert.equal(
    service.buildNextEpisodeHref({
      seriesId: 'ser_1',
      episodeId: 'ep_4',
      reason: 'NEXT_UNWATCHED',
      resumePositionMs: 0,
    }),
    '/series/ser_1?episode=ep_4'
  );
});

test('NextEpisodeAction hides for guests, renders action/completed states, stays silent on 500, leaves 401 handling to the session observer, and refreshes on focus', async () => {
  let fetchCount = 0;
  let currentResponse = jsonResponse({
    success: true,
    data: {
      seriesId: 'ser_1',
      episodeId: 'ep_2',
      episodeTitle: 'Tập 2',
      seasonNumber: 1,
      episodeNumber: 2,
      resumePositionMs: 125000,
      reason: 'RESUME',
    },
  });

  const service = loadTsModule('../lib/services/nextEpisodeService.ts', {
    fetch: async () => {
      fetchCount++;
      return currentResponse;
    },
  });

  const guestView = renderNextEpisodeAction(
    { isAuthenticated: false, token: null, handleSessionExpired() {} },
    service,
    { seriesId: 'ser_1', progressRevision: '', onSelectRecommendation() {} }
  );
  assert.equal(guestView.render(), null);
  assert.equal(fetchCount, 0);
  guestView.unmount();

  const listeners = new Map();
  const fakeWindow = {
    addEventListener: (event, fn) => listeners.set(event, fn),
    removeEventListener: (event) => listeners.delete(event),
  };
  const fakeDocument = {
    hidden: false,
    addEventListener: () => {},
    removeEventListener: () => {},
  };

  let expiredCalls = 0;
  const selected = [];
  const view = renderNextEpisodeAction(
    {
      isAuthenticated: true,
      token: 'tok-ok',
      handleSessionExpired: () => {
        expiredCalls++;
      },
    },
    service,
    {
      seriesId: 'ser_1',
      progressRevision: 'v1',
      onSelectRecommendation: (rec) => selected.push(rec),
    },
    { window: fakeWindow, document: fakeDocument }
  );

  view.render();
  await tick();
  let tree = view.render();
  const button = findNode(tree, (n) => n.props?.['data-testid'] === 'next-episode-action');
  assert.ok(button);
  assert.match(JSON.stringify(button), /Xem tiếp tập 2/);
  assert.match(JSON.stringify(button), /02:05/);
  button.props.onClick();
  assert.equal(selected.length, 1);
  assert.equal(selected[0].episodeId, 'ep_2');

  currentResponse = jsonResponse({
    success: true,
    message: 'Bạn đã xem hết bộ phim này.',
    data: null,
  });
  listeners.get('focus')();
  await tick();
  tree = view.render();
  const badge = findNode(tree, (n) => n.props?.['data-testid'] === 'series-completed-badge');
  assert.ok(badge);
  assert.match(JSON.stringify(badge), /Đã xem hết/);

  currentResponse = jsonResponse({ success: false, message: 'Server error', data: null }, 500);
  view.render({
    seriesId: 'ser_1',
    progressRevision: 'v2',
    onSelectRecommendation: (rec) => selected.push(rec),
  });
  await tick();
  tree = view.render();
  assert.equal(expiredCalls, 0);
  assert.doesNotMatch(JSON.stringify(tree), /Server error/);

  currentResponse = jsonResponse({ success: false, message: 'Chưa xác thực người dùng', data: null }, 401);
  view.render({
    seriesId: 'ser_1',
    progressRevision: 'v3',
    onSelectRecommendation: (rec) => selected.push(rec),
  });
  await tick();
  tree = view.render();
  assert.equal(expiredCalls, 0);
  assert.equal(tree, null);

  view.unmount();
});

test('R5: nextEpisodeService preserves backend assetId and chooses episode vs file resume timeline cleanly', async () => {
  const service = loadTsModule('../lib/services/nextEpisodeService.ts', {
    fetch: async () =>
      jsonResponse({
        success: true,
        message: 'Thành công',
        data: {
          seriesId: 'ser_doraemon',
          episodeId: 'ep_doraemon_s05_0209',
          episodeTitle: 'Cuộc Chiến Của tay Vua Súng',
          seasonNumber: 5,
          episodeNumber: 209,
          resumePositionMs: 60000,
          reason: 'RESUME',
          assetId: 'youtube:0FHNLZszGiU',
        },
      }),
  });

  const result = await service.getNextEpisode('tok-123', 'ser_doraemon');
  assert.equal(result.status, 'ready');
  assert.equal(result.recommendation.assetId, 'youtube:0FHNLZszGiU');
  assert.equal(
    service.buildNextEpisodeHref(result.recommendation),
    '/series/ser_doraemon?episode=ep_doraemon_s05_0209&t=60000&timeline=episode'
  );
  assert.equal(
    service.buildNextEpisodeHref({
      seriesId: 'ser_jadoo',
      episodeId: 'ep_1',
      reason: 'RESUME',
      resumePositionMs: 45000,
      assetId: 'asset_local_1',
    }),
    '/series/ser_jadoo?episode=ep_1&asset=asset_local_1&t=45000&timeline=file'
  );

  const replacedUrls = [];
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
      return { type, props: { ...props, children } };
    },
  };

  const playbackSource = loadTsModule('../lib/services/playbackSource.ts', {});
  const mediaRouting = loadTsModule('../lib/services/mediaRouting.ts', {});
  const episodeNavigation = loadTsModule('../lib/services/episodeNavigation.ts', {});
  const ep1 = { id: 'ep_1', seriesId: 'ser_doraemon', seasonId: 's5', number: 1, title: 'Tập 1', durationMs: 600000 };
  const ep209 = { id: 'ep_doraemon_s05_0209', seriesId: 'ser_doraemon', seasonId: 's5', number: 209, title: 'Tập 209', durationMs: 600000 };

  const pageMod = loadTsModule('../app/series/[id]/page.tsx', {
    URL,
    window: {
      location: { href: 'http://localhost:3000/series/ser_doraemon?episode=ep_1' },
      history: { replaceState: (_s, _t, url) => replacedUrls.push(String(url)) },
      setTimeout,
      clearTimeout,
    },
    document: { title: '', hidden: false, addEventListener() {}, removeEventListener() {} },
    require(name) {
      if (name === '@/lib/labels') return labels;
      if (name === 'react') return { __esModule: true, default: react, ...react };
      if (name === 'react-router') {
        return {
          Link: 'route-link',
          useParams: () => ({ id: 'ser_doraemon' }),
          useSearchParams: () => [new URLSearchParams('episode=ep_1'), (params, options) => {
            assert.equal(options.replace, true);
            replacedUrls.push('http://localhost/series/ser_doraemon?' + params);
          }],
        };
      }
      if (name.includes('catalogService')) {
        return {
          getCatalogSeriesDetail: async () => ({
            series: { id: 'ser_doraemon', title: 'Doraemon' },
            seasons: [{ id: 's5', seriesId: 'ser_doraemon', number: 5, title: 'Mùa 5' }],
            episodes: [ep1, ep209],
          }),
          getCatalogEpisodeDetail: async (id) => ({
            episode: id === 'ep_1' ? ep1 : ep209,
            segments: [],
            mediaAssets: [],
            scenes: [],
            youtube: { videoId: '0FHNLZszGiU', canEmbed: true },
          }),
        };
      }
      if (name.includes('episodeNavigation')) return episodeNavigation;
      if (name.includes('playbackSource')) return playbackSource;
      if (name.includes('mediaRouting')) return mediaRouting;
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
      if (name.includes('seriesCardService')) return { getSeriesCardById: async () => null };
      if (name.includes('SeriesDetailBanner')) return { SeriesDetailBanner: 'SeriesDetailBanner' };
      if (name.includes('NextEpisodeAction')) return { NextEpisodeAction: 'NextEpisodeAction' };
      if (name.includes('LibraryControls')) return { ItemLibraryPanel: 'ItemLibraryPanel', QuickSaveButtons: 'QuickSaveButtons' };
      if (name.includes('YouTubePlayer')) return { YouTubePlayer: 'YouTubePlayer' };
      return {};
    },
  });

  let tree;
  for (let i = 0; i < 6; i++) {
    cursor = 0;
    tree = pageMod.default();
    const pending = effects;
    effects = [];
    pending.forEach((fn) => fn());
    await tick();
  }
  const banner = findNode(tree, (n) => n.type === 'SeriesDetailBanner');
  const nextAction = banner.props.nextEpisodeAction;
  nextAction.props.onSelectRecommendation(result.recommendation);

  const lastUrl = replacedUrls.at(-1);
  assert.ok(lastUrl, 'Selecting next episode recommendation must update URL');
  const parsed = new URL(lastUrl);
  assert.equal(parsed.searchParams.get('episode'), 'ep_doraemon_s05_0209');
  assert.equal(parsed.searchParams.get('t'), '60000');
  assert.equal(parsed.searchParams.get('timeline'), 'episode');
  assert.equal(parsed.searchParams.get('asset'), null);

  nextAction.props.onSelectRecommendation({
    ...result.recommendation,
    assetId: 'asset_file_42',
  });
  const fileUrl = new URL(replacedUrls.at(-1));
  assert.equal(fileUrl.searchParams.get('timeline'), 'file');
  assert.equal(fileUrl.searchParams.get('asset'), 'asset_file_42');
  slots.forEach((slot) => slot?.cleanup?.());
});

test('Selecting NextEpisodeAction on the currently loaded episode seeks in place without refetching episode detail', async () => {
  const slots = [];
  let cursor = 0;
  let effects = [];
  let currentParams = new URLSearchParams('episode=ep_doraemon_s05_0209');
  let episodeFetchCount = 0;
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
      return { type, props: { ...props, children } };
    },
  };

  const playbackSource = loadTsModule('../lib/services/playbackSource.ts', {});
  const mediaRouting = loadTsModule('../lib/services/mediaRouting.ts', {});
  const episodeNavigation = loadTsModule('../lib/services/episodeNavigation.ts', {});
  const ep209 = { id: 'ep_doraemon_s05_0209', seriesId: 'ser_doraemon', seasonId: 's5', number: 209, title: 'Tập 209', durationMs: 600000 };

  const pageMod = loadTsModule('../app/series/[id]/page.tsx', {
    URL,
    window: {
      location: { href: 'http://localhost:3000/series/ser_doraemon?episode=ep_doraemon_s05_0209' },
      history: { replaceState() {} },
      setTimeout,
      clearTimeout,
    },
    document: { title: '', hidden: false, addEventListener() {}, removeEventListener() {} },
    require(name) {
      if (name === '@/lib/labels') return labels;
      if (name === 'react') return { __esModule: true, default: react, ...react };
      if (name === 'react-router') {
        return {
          Link: 'route-link',
          useParams: () => ({ id: 'ser_doraemon' }),
          useSearchParams: () => [
            currentParams,
            (nextParams) => {
              currentParams = new URLSearchParams(nextParams);
            },
          ],
        };
      }
      if (name.includes('catalogService')) {
        return {
          getCatalogSeriesDetail: async () => ({
            series: { id: 'ser_doraemon', title: 'Doraemon' },
            seasons: [{ id: 's5', seriesId: 'ser_doraemon', number: 5, title: 'Mùa 5' }],
            episodes: [ep209],
          }),
          getCatalogEpisodeDetail: async () => {
            episodeFetchCount += 1;
            return {
              episode: ep209,
              segments: [],
              mediaAssets: [],
              scenes: [],
              youtube: { videoId: '0FHNLZszGiU', canEmbed: true },
            };
          },
        };
      }
      if (name.includes('episodeNavigation')) return episodeNavigation;
      if (name.includes('playbackSource')) return playbackSource;
      if (name.includes('mediaRouting')) return mediaRouting;
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
      if (name.includes('seriesCardService')) return { getSeriesCardById: async () => null };
      if (name.includes('SeriesDetailBanner')) return { SeriesDetailBanner: 'SeriesDetailBanner' };
      if (name.includes('NextEpisodeAction')) return { NextEpisodeAction: 'NextEpisodeAction' };
      if (name.includes('LibraryControls')) return { ItemLibraryPanel: 'ItemLibraryPanel', QuickSaveButtons: 'QuickSaveButtons' };
      if (name.includes('YouTubePlayer')) return { YouTubePlayer: 'YouTubePlayer' };
      return {};
    },
  });

  const settle = async () => {
    let tree;
    for (let i = 0; i < 5; i++) {
      cursor = 0;
      tree = pageMod.default();
      const pending = effects;
      effects = [];
      pending.forEach((fn) => fn());
      await tick();
    }
    return tree;
  };

  let tree = await settle();
  assert.equal(episodeFetchCount, 1);

  const banner = findNode(tree, (n) => n.type === 'SeriesDetailBanner');
  banner.props.nextEpisodeAction.props.onSelectRecommendation({
    seriesId: 'ser_doraemon',
    episodeId: 'ep_doraemon_s05_0209',
    episodeTitle: 'Tập 209',
    seasonNumber: 5,
    episodeNumber: 209,
    resumePositionMs: 360000,
    reason: 'RESUME',
    assetId: 'youtube:0FHNLZszGiU',
  });
  tree = await settle();
  assert.equal(episodeFetchCount, 1);
  let ytNode = findNode(tree, (n) => n.type === 'YouTubePlayer');
  assert.equal(ytNode.props.seekRequest.seconds, 360);
  assert.equal(ytNode.props.seekRequest.autoPlay, true);

  const bannerAfter = findNode(tree, (n) => n.type === 'SeriesDetailBanner');
  bannerAfter.props.nextEpisodeAction.props.onSelectRecommendation({
    seriesId: 'ser_doraemon',
    episodeId: 'ep_doraemon_s05_0209',
    episodeTitle: 'Tập 209',
    seasonNumber: 5,
    episodeNumber: 209,
    resumePositionMs: 0,
    reason: 'NEXT_UNWATCHED',
  });
  tree = await settle();
  assert.equal(episodeFetchCount, 1);
  ytNode = findNode(tree, (n) => n.type === 'YouTubePlayer');
  assert.equal(ytNode.props.seekRequest.seconds, 0);
  assert.equal(ytNode.props.seekRequest.autoPlay, true);
  slots.forEach((slot) => slot?.cleanup?.());
});
