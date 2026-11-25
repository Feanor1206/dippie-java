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
    URL,
    URLSearchParams,
    Date,
    setTimeout,
    clearTimeout,
    setInterval,
    clearInterval,
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
    useId: () => 'yt1',
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
    unmount() {
      for (const slot of slots) slot?.cleanup?.();
    },
  };
}

test('YouTubePlayer saves progress on pause, every ~15s while playing, on ended, and on leaving page, and handles seek requests', async () => {
  const h = makeReact();
  let fakeNow = 1000;
  class FakeDate extends Date {
    static now() {
      return fakeNow;
    }
  }
  const intervals = new Map();
  let intervalSeq = 1;
  const windowListeners = new Map();
  const docListeners = new Map();
  let playerConfig = null;
  let currentSec = 0;
  let durationSec = 300;
  const seeks = [];
  let playCalls = 0;
  let destroyed = false;

  const fakePlayer = {
    getCurrentTime: () => currentSec,
    getDuration: () => durationSec,
    seekTo: (sec, allow) => seeks.push({ sec, allow }),
    playVideo: () => { playCalls++; },
    destroy: () => { destroyed = true; },
  };

  const fakeWindow = {
    location: { origin: 'http://localhost:3000' },
    YT: {
      PlayerState: { ENDED: 0, PLAYING: 1, PAUSED: 2 },
      Player: function Player(_el, config) {
        playerConfig = config;
        return fakePlayer;
      },
    },
    addEventListener: (ev, fn) => windowListeners.set(ev, fn),
    removeEventListener: (ev) => windowListeners.delete(ev),
  };

  const fakeDocument = {
    visibilityState: 'visible',
    addEventListener: (ev, fn) => docListeners.set(ev, fn),
    removeEventListener: (ev) => docListeners.delete(ev),
  };

  const mod = loadTsModule('../features/series/components/YouTubePlayer.tsx', {
    window: fakeWindow,
    document: fakeDocument,
    Date: FakeDate,
    setInterval: (fn, ms) => {
      const id = intervalSeq++;
      intervals.set(id, { fn, ms });
      return id;
    },
    clearInterval: (id) => intervals.delete(id),
    require: (name) => {
      if (name === 'react') return { __esModule: true, default: h.react, ...h.react };
      return {};
    },
  });

  const progressReports = [];
  let endedCount = 0;

  const render = (extraProps = {}) => {
    h.reset();
    const tree = mod.YouTubePlayer({
      videoId: '9aFcot7_7Q4',
      src: 'https://www.youtube-nocookie.com/embed/9aFcot7_7Q4?start=0&enablejsapi=1',
      title: 'Tập 1',
      onProgress: (snapshot, reason) => progressReports.push({ snapshot, reason }),
      onEnded: () => { endedCount++; },
      ...extraProps,
    });
    if (tree.props.ref) tree.props.ref.current = { id: tree.props.id };
    h.flush();
    return tree;
  };

  const iframe = render({ autoPlay: true });
  assert.equal(iframe.type, 'iframe');
  assert.ok(iframe.props.src.includes('enablejsapi=1'));
  assert.ok(iframe.props.src.includes('youtube-nocookie.com/embed/9aFcot7_7Q4'));

  await new Promise(setImmediate);
  assert.ok(playerConfig, 'YT.Player should initialize');

  playerConfig.events.onReady({ target: fakePlayer });
  assert.equal(playCalls, 1);

  currentSec = 20;
  playerConfig.events.onStateChange({ data: 1, target: fakePlayer });
  assert.equal(intervals.size, 1);

  const tickFn = [...intervals.values()][0].fn;
  fakeNow += 5000;
  currentSec = 25;
  tickFn();
  assert.equal(progressReports.length, 0);

  fakeNow += 11000;
  currentSec = 36;
  tickFn();
  assert.equal(progressReports.length, 1);
  assert.equal(progressReports[0].reason, 'interval');
  assert.equal(progressReports[0].snapshot.currentTimeSec, 36);

  currentSec = 48;
  playerConfig.events.onStateChange({ data: 2, target: fakePlayer });
  assert.equal(progressReports.length, 2);
  assert.equal(progressReports[1].reason, 'pause');
  assert.equal(progressReports[1].snapshot.currentTimeSec, 48);

  render({ seekRequest: { seconds: 120, nonce: 1, autoPlay: true } });
  assert.equal(seeks.length, 1);
  assert.equal(seeks[0].sec, 120);
  assert.equal(playCalls, 2);

  currentSec = 140;
  windowListeners.get('pagehide')();
  assert.equal(progressReports.length, 3);
  assert.equal(progressReports[2].reason, 'leave');
  assert.equal(progressReports[2].snapshot.currentTimeSec, 140);

  currentSec = 299;
  playerConfig.events.onStateChange({ data: 0, target: fakePlayer });
  assert.equal(endedCount, 1);
  assert.equal(progressReports[3].reason, 'ended');
  assert.equal(progressReports[3].snapshot.currentTimeSec, 300);

  h.unmount();
  assert.equal(destroyed, true);
});

test('SeriesDetailPage saves YouTube C1 progress, offers resume, enables auto-next, and crosses season boundaries', async () => {
  const h = makeReact();
  const playbackSource = loadTsModule('../lib/services/playbackSource.ts', {});
  const mediaRouting = loadTsModule('../lib/services/mediaRouting.ts', {});
  const watchProgressService = loadTsModule('../lib/services/watchProgressService.ts', {
    window: { localStorage: { getItem: () => null, setItem() {}, removeItem() {} } },
  });
  const episodeNavigation = loadTsModule('../lib/services/episodeNavigation.ts', {});

  const seasons = [
    { id: 's1', seriesId: 'ser_1', number: 1, title: 'Mùa 1' },
    { id: 's2', seriesId: 'ser_1', number: 2, title: 'Mùa 2' },
  ];
  const episodes = [
    { id: 'ep_s1_1', seriesId: 'ser_1', seasonId: 's1', number: 1, title: 'Tập 1', durationMs: 300000 },
    { id: 'ep_s2_1', seriesId: 'ser_1', seasonId: 's2', number: 1, title: 'Mở màn mùa 2', durationMs: 300000 },
  ];

  const savedDrafts = [];
  let autoNextPref = true;
  const pageMod = loadTsModule('../app/series/[id]/page.tsx', {
    window: { location: { href: 'http://localhost/series/ser_1' }, history: { replaceState() {} }, setTimeout, clearTimeout },
    document: { hidden: false, addEventListener() {}, removeEventListener() {} },
    require: (name) => {
      if (name === '@/lib/labels') return labels;
      if (name === 'react') return { __esModule: true, default: h.react, ...h.react };
      if (name === 'react-router') return { Link: 'route-link', useParams: () => ({ id: 'ser_1' }), useSearchParams: () => [new URLSearchParams(), () => {}] };
      if (name.includes('catalogService')) {
        return {
          getCatalogSeriesDetail: async () => ({
            series: { id: 'ser_1', title: 'Heo Peppa' },
            seasons,
            episodes,
          }),
          getCatalogEpisodeDetail: async (id) => ({
            episode: episodes.find((e) => e.id === id),
            mediaAssets: [],
            scenes: [],
            segments: [],
            youtube: { videoId: id === 'ep_s1_1' ? '11111111111' : '22222222222', canEmbed: true },
          }),
        };
      }
      if (name.includes('episodeNavigation')) return episodeNavigation;
      if (name.includes('mediaService')) return { resolveStreamUrl: (u) => u, getStoredAuthToken: () => null };
      if (name.includes('mediaRouting')) return mediaRouting;
      if (name.includes('playbackSource')) return playbackSource;
      if (name.includes('useWatchProgress')) {
        return {
          useWatchProgress: () => ({
            records: [
              {
                episodeId: 'ep_s1_1',
                seriesId: 'ser_1',
                seriesTitle: 'Heo Peppa',
                seasonId: 's1',
                seasonNumber: 1,
                seasonTitle: 'Mùa 1',
                episodeNumber: 1,
                episodeTitle: 'Tập 1',
                assetId: 'youtube:11111111111',
                positionMs: 45000,
                durationMs: 300000,
                completed: false,
                version: 0,
                updatedAt: '2026-11-20T00:00:00Z',
              },
            ],
            saveProgress: async (draft, reason) => {
              savedDrafts.push({ draft, reason });
              return null;
            },
          }),
        };
      }
      if (name.includes('watchProgressService')) {
        return {
          ...watchProgressService,
          readAutoNextPreference: () => autoNextPref,
          writeAutoNextPreference: (v) => { autoNextPref = v; },
        };
      }
      if (name.includes('YouTubePlayer')) return { YouTubePlayer: 'YouTubePlayer' };
      if (name.includes('SeriesDetailBanner')) return { SeriesDetailBanner: 'SeriesDetailBanner' };
      if (name.includes('NextEpisodeAction')) return { NextEpisodeAction: 'NextEpisodeAction' };
      if (name.includes('LibraryControls')) return { ItemLibraryPanel: 'ItemLibraryPanel', QuickSaveButtons: 'QuickSaveButtons' };
      return {};
    },
  });

  let tree;
  const settle = async () => {
    for (let i = 0; i < 6; i++) {
      h.reset();
      tree = pageMod.default();
      h.flush();
      await new Promise(setImmediate);
    }
    h.reset();
    tree = pageMod.default();
    return tree;
  };

  tree = await settle();

  const nextBtn = findNodes(tree, (n) => n.props?.['data-testid'] === 'next-episode')[0];
  assert.equal(nextBtn.props.disabled, false);
  assert.equal(getText(nextBtn), 'Sang Mùa 2 · Tập 1');

  const resumeBox = findNodes(tree, (n) => n.props?.['data-testid'] === 'resume-choice')[0];
  assert.ok(resumeBox, 'Resume offer must show for YouTube episodes with saved progress');

  const autoNextCheckbox = findNodes(tree, (n) => n.props?.['data-testid'] === 'auto-next-checkbox')[0];
  assert.ok(autoNextCheckbox, 'Auto-next checkbox must be visible for YouTube playback');

  const resumeBtn = findNodes(resumeBox, (n) => n.type === 'button' && getText(n) === 'Xem tiếp')[0];
  resumeBtn.props.onClick();
  tree = await settle();
  const ytNode = findNodes(tree, (n) => n.type === 'YouTubePlayer')[0];
  assert.equal(ytNode.props.seekRequest.seconds, 45);

  ytNode.props.onProgress({ currentTimeSec: 90, durationSec: 300 }, 'pause');
  assert.equal(savedDrafts.length, 1);
  assert.equal(savedDrafts[0].draft.assetId, 'youtube:11111111111');
  assert.equal(savedDrafts[0].draft.positionMs, 90000);
  assert.equal(savedDrafts[0].reason, 'pause');

  ytNode.props.onEnded();
  h.reset();
  tree = pageMod.default();
  const countdown = findNodes(tree, (n) => n.props?.['data-testid'] === 'next-countdown')[0];
  assert.ok(countdown, 'YouTube ended with autoNext enabled must start countdown');
  assert.match(getText(countdown), /Sang Mùa 2 · Tập 1/);
  h.unmount();
});

test('ContinueWatching renders YouTube episodes with episode timeline resume link', () => {
  const h = makeReact();
  const watchProgressService = loadTsModule('../lib/services/watchProgressService.ts', {
    window: { localStorage: { getItem: () => null, setItem() {}, removeItem() {} } },
  });
  const movieProgress = loadTsModule('../lib/services/movieProgress.ts', {
    window: { localStorage: { getItem: () => null, setItem() {}, removeItem() {} } },
  });
  const utils = loadTsModule('../features/series/seriesBrowseUtils.ts', {
    require: (name) => (name === '@/lib/labels' ? labels : {}),
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
                episodeId: 'ep_doraemon_1',
                seriesId: 'ser_doraemon',
                seriesTitle: 'Doraemon',
                seasonId: 's5',
                seasonNumber: 5,
                seasonTitle: 'Mùa 5',
                episodeNumber: 209,
                episodeTitle: 'Máy thời gian',
                thumbnailUrl: 'https://i.ytimg.com/vi/9aFcot7_7Q4/hqdefault.jpg',
                assetId: 'youtube:9aFcot7_7Q4',
                positionMs: 120000,
                durationMs: 600000,
                completed: false,
                version: 1,
                updatedAt: '2026-11-22T00:00:00Z',
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

  h.reset();
  const tree = mod.ContinueWatching({ films: [] });
  const card = findNodes(tree, (n) => n.props?.['data-testid'] === 'continue-card')[0];
  assert.ok(card);
  assert.equal(card.props.to, '/series/ser_doraemon?episode=ep_doraemon_1&t=120000&timeline=episode');
  assert.match(getText(card), /Doraemon/);
  assert.match(getText(card), /Tập 209/);
});

test('Finding 1: YouTubePlayer does not emit progress on ready, pause, pagehide, or unmount before playback starts', async () => {
  const h = makeReact();
  const windowListeners = new Map();
  const docListeners = new Map();
  let playerConfig = null;
  const fakePlayer = {
    getCurrentTime: () => 39.719,
    getDuration: () => 1325,
    seekTo: () => {},
    playVideo: () => {},
    destroy: () => {},
  };
  const fakeWindow = {
    location: { origin: 'http://localhost:3000' },
    YT: {
      PlayerState: { ENDED: 0, PLAYING: 1, PAUSED: 2 },
      Player: function Player(_el, config) {
        playerConfig = config;
        return fakePlayer;
      },
    },
    addEventListener: (ev, fn) => windowListeners.set(ev, fn),
    removeEventListener: (ev) => windowListeners.delete(ev),
  };
  const fakeDocument = {
    visibilityState: 'hidden',
    addEventListener: (ev, fn) => docListeners.set(ev, fn),
    removeEventListener: (ev) => docListeners.delete(ev),
  };

  const mod = loadTsModule('../features/series/components/YouTubePlayer.tsx', {
    window: fakeWindow,
    document: fakeDocument,
    require: (name) => (name === 'react' ? { __esModule: true, default: h.react, ...h.react } : {}),
  });

  const progressReports = [];
  const tickReports = [];
  h.reset();
  const tree = mod.YouTubePlayer({
    videoId: '0FHNLZszGiU',
    src: 'https://www.youtube-nocookie.com/embed/0FHNLZszGiU?start=39&enablejsapi=1',
    title: 'Nobita đi vũ trụ',
    onProgress: (snapshot, reason) => progressReports.push({ snapshot, reason }),
    onTimeTick: (snapshot) => tickReports.push(snapshot),
  });
  if (tree.props.ref) tree.props.ref.current = { id: tree.props.id };
  h.flush();

  await new Promise(setImmediate);
  playerConfig.events.onReady({ target: fakePlayer });
  assert.equal(tickReports.length, 1);
  assert.equal(tickReports[0].currentTimeSec, 39.719);

  playerConfig.events.onStateChange({ data: 2, target: fakePlayer });
  windowListeners.get('pagehide')();
  docListeners.get('visibilitychange')();
  h.unmount();

  assert.equal(progressReports.length, 0);
});

test('Finding 1: SeriesDetailPage saves nothing for unplayed ?t= deep links or resume prompts, and flushes played episodes once on episode/season switch', async () => {
  const playbackSource = loadTsModule('../lib/services/playbackSource.ts', {});
  const mediaRouting = loadTsModule('../lib/services/mediaRouting.ts', {});
  const watchProgressService = loadTsModule('../lib/services/watchProgressService.ts', {
    window: { localStorage: { getItem: () => null, setItem() {}, removeItem() {} } },
  });
  const episodeNavigation = loadTsModule('../lib/services/episodeNavigation.ts', {});

  const seasons = [
    { id: 'sea_doraemon_s05', seriesId: 'ser_doraemon', number: 5, title: 'Mùa 5' },
    { id: 'sea_doraemon_s12', seriesId: 'ser_doraemon', number: 12, title: 'Mùa 12 theo POPS Kids' },
  ];
  const episodes = [
    { id: 'ep_doraemon_s05_0209', seriesId: 'ser_doraemon', seasonId: 'sea_doraemon_s05', number: 209, title: 'Tập 209', durationMs: 1325000 },
    { id: 'ep_doraemon_s12_0599', seriesId: 'ser_doraemon', seasonId: 'sea_doraemon_s12', number: 599, title: 'Nobita đi vũ trụ', durationMs: 1325000 },
  ];

  const mountPage = async (query, records = []) => {
    const h = makeReact();
    const savedDrafts = [];
    const pageMod = loadTsModule('../app/series/[id]/page.tsx', {
      window: {
        location: { href: `http://localhost:3000/series/ser_doraemon?${query}` },
        history: { replaceState() {} },
        addEventListener() {},
        removeEventListener() {},
        setTimeout,
        clearTimeout,
      },
      document: { hidden: false, visibilityState: 'visible', addEventListener() {}, removeEventListener() {} },
      require: (name) => {
        if (name === '@/lib/labels') return labels;
        if (name === 'react') return { __esModule: true, default: h.react, ...h.react };
        if (name === 'react-router') return { Link: 'route-link', useParams: () => ({ id: 'ser_doraemon' }), useSearchParams: () => [new URLSearchParams(query), () => {}] };
        if (name.includes('catalogService')) {
          return {
            getCatalogSeriesDetail: async () => ({
              series: { id: 'ser_doraemon', title: 'Doraemon' },
              seasons,
              episodes,
            }),
            getCatalogEpisodeDetail: async (id) => ({
              episode: episodes.find((e) => e.id === id),
              mediaAssets: [],
              scenes: [],
              segments: [],
              youtube: { videoId: id === 'ep_doraemon_s05_0209' ? '0FHNLZszGiU' : 'SpaceEp0599', canEmbed: true },
            }),
          };
        }
        if (name.includes('episodeNavigation')) return episodeNavigation;
        if (name.includes('mediaService')) return { resolveStreamUrl: (u) => u, getStoredAuthToken: () => null };
        if (name.includes('mediaRouting')) return mediaRouting;
        if (name.includes('playbackSource')) return playbackSource;
        if (name.includes('useWatchProgress')) {
          return {
            useWatchProgress: () => ({
              records,
              saveProgress: async (draft, reason) => {
                savedDrafts.push({ draft, reason });
                return null;
              },
            }),
          };
        }
        if (name.includes('watchProgressService')) {
          return { ...watchProgressService, readAutoNextPreference: () => false, writeAutoNextPreference: () => {} };
        }
        if (name.includes('YouTubePlayer')) return { YouTubePlayer: 'YouTubePlayer' };
        if (name.includes('SeriesDetailBanner')) return { SeriesDetailBanner: 'SeriesDetailBanner' };
        if (name.includes('NextEpisodeAction')) return { NextEpisodeAction: 'NextEpisodeAction' };
        if (name.includes('LibraryControls')) return { ItemLibraryPanel: 'ItemLibraryPanel', QuickSaveButtons: 'QuickSaveButtons' };
        return {};
      },
    });

    const settle = async () => {
      let tree;
      for (let i = 0; i < 6; i++) {
        h.reset();
        tree = pageMod.default();
        h.flush();
        await new Promise(setImmediate);
      }
      h.reset();
      return pageMod.default();
    };

    return { h, savedDrafts, settle };
  };

  const deepLinkRun = await mountPage('episode=ep_doraemon_s12_0599&t=39719&timeline=episode');
  let tree = await deepLinkRun.settle();
  const deepLinkYt = findNodes(tree, (n) => n.type === 'YouTubePlayer')[0];
  deepLinkYt.props.onTimeTick({ currentTimeSec: 39.719, durationSec: 1325 });
  const s5Btn = findNodes(tree, (n) => n.props?.['data-season-id'] === 'sea_doraemon_s05')[0];
  s5Btn.props.onClick();
  deepLinkYt.props.onProgress({ currentTimeSec: 39.719, durationSec: 1325 }, 'leave');
  await deepLinkRun.settle();
  assert.equal(deepLinkRun.savedDrafts.length, 0);
  deepLinkRun.h.unmount();

  const resumePromptRun = await mountPage('episode=ep_doraemon_s05_0209', [
    {
      episodeId: 'ep_doraemon_s05_0209',
      seriesId: 'ser_doraemon',
      seriesTitle: 'Doraemon',
      seasonId: 'sea_doraemon_s05',
      seasonNumber: 5,
      seasonTitle: 'Mùa 5',
      episodeNumber: 209,
      episodeTitle: 'Tập 209',
      assetId: 'youtube:0FHNLZszGiU',
      positionMs: 360000,
      durationMs: 1325000,
      completed: false,
      version: 2,
      updatedAt: '2026-11-23T00:00:00Z',
    },
  ]);
  tree = await resumePromptRun.settle();
  assert.ok(findNodes(tree, (n) => n.props?.['data-testid'] === 'resume-choice')[0]);
  const resumeYt = findNodes(tree, (n) => n.type === 'YouTubePlayer')[0];
  resumeYt.props.onTimeTick({ currentTimeSec: 360, durationSec: 1325 });
  const s12FromResume = findNodes(tree, (n) => n.props?.['data-season-id'] === 'sea_doraemon_s12')[0];
  s12FromResume.props.onClick();
  resumeYt.props.onProgress({ currentTimeSec: 360, durationSec: 1325 }, 'leave');
  await resumePromptRun.settle();
  assert.equal(resumePromptRun.savedDrafts.length, 0);
  resumePromptRun.h.unmount();

  const playedSwitchRun = await mountPage('episode=ep_doraemon_s05_0209&t=360000&timeline=episode');
  tree = await playedSwitchRun.settle();
  const playedYt = findNodes(tree, (n) => n.type === 'YouTubePlayer')[0];
  playedYt.props.onPlayingChange(true);
  playedYt.props.onTimeTick({ currentTimeSec: 362, durationSec: 1325 });
  const s12Btn = findNodes(tree, (n) => n.props?.['data-season-id'] === 'sea_doraemon_s12')[0];
  s12Btn.props.onClick();
  playedYt.props.onProgress({ currentTimeSec: 362, durationSec: 1325 }, 'leave');
  await playedSwitchRun.settle();
  assert.equal(playedSwitchRun.savedDrafts.length, 1);
  assert.equal(playedSwitchRun.savedDrafts[0].draft.episodeId, 'ep_doraemon_s05_0209');
  assert.equal(playedSwitchRun.savedDrafts[0].draft.positionMs, 362000);
  assert.equal(playedSwitchRun.savedDrafts[0].reason, 'episode-change');
  playedSwitchRun.h.unmount();
});

test('Finding 1: File playback shares the same playback-start gate and single-flush-on-switch rule', async () => {
  const playbackSource = loadTsModule('../lib/services/playbackSource.ts', {});
  const mediaRouting = loadTsModule('../lib/services/mediaRouting.ts', {});
  const watchProgressService = loadTsModule('../lib/services/watchProgressService.ts', {
    window: { localStorage: { getItem: () => null, setItem() {}, removeItem() {} } },
  });
  const episodeNavigation = loadTsModule('../lib/services/episodeNavigation.ts', {});

  const seasons = [
    { id: 's1', seriesId: 'ser_file', number: 1, title: 'Mùa 1' },
    { id: 's2', seriesId: 'ser_file', number: 2, title: 'Mùa 2' },
  ];
  const episodes = [
    { id: 'ep_f1', seriesId: 'ser_file', seasonId: 's1', number: 1, title: 'Tập 1', durationMs: 600000 },
    { id: 'ep_f2', seriesId: 'ser_file', seasonId: 's2', number: 2, title: 'Tập 2', durationMs: 600000 },
  ];

  const mountFilePage = async () => {
    const h = makeReact();
    const savedDrafts = [];
    const pageMod = loadTsModule('../app/series/[id]/page.tsx', {
      window: {
        location: { href: 'http://localhost:3000/series/ser_file?episode=ep_f1&t=120000&timeline=file&asset=ast1' },
        history: { replaceState() {} },
        addEventListener() {},
        removeEventListener() {},
        setTimeout,
        clearTimeout,
      },
      document: { hidden: false, visibilityState: 'visible', addEventListener() {}, removeEventListener() {} },
      require: (name) => {
        if (name === '@/lib/labels') return labels;
        if (name === 'react') return { __esModule: true, default: h.react, ...h.react };
        if (name === 'react-router') return { Link: 'route-link', useParams: () => ({ id: 'ser_file' }), useSearchParams: () => [new URLSearchParams('episode=ep_f1&t=120000&timeline=file&asset=ast1'), () => {}] };
        if (name.includes('catalogService')) {
          return {
            getCatalogSeriesDetail: async () => ({
              series: { id: 'ser_file', title: 'Phim Tệp' },
              seasons,
              episodes,
            }),
            getCatalogEpisodeDetail: async (id) => ({
              episode: episodes.find((e) => e.id === id),
              mediaAssets: [
                {
                  id: id === 'ep_f1' ? 'ast1' : 'ast2',
                  contentType: 'video',
                  role: 'feature',
                  storageProvider: 'local',
                  available: true,
                  durationMs: 600000,
                  coverageJson: JSON.stringify([{ episode_id: id, scope: 'full_episode' }]),
                },
              ],
              scenes: [],
              segments: [],
              youtube: null,
            }),
          };
        }
        if (name.includes('episodeNavigation')) return episodeNavigation;
        if (name.includes('mediaService')) return { resolveStreamUrl: (u) => u, getStoredAuthToken: () => null };
        if (name.includes('mediaRouting')) return mediaRouting;
        if (name.includes('playbackSource')) return playbackSource;
        if (name.includes('useWatchProgress')) {
          return {
            useWatchProgress: () => ({
              records: [],
              saveProgress: async (draft, reason) => {
                savedDrafts.push({ draft, reason });
                return null;
              },
            }),
          };
        }
        if (name.includes('watchProgressService')) {
          return { ...watchProgressService, readAutoNextPreference: () => false, writeAutoNextPreference: () => {} };
        }
        if (name.includes('YouTubePlayer')) return { YouTubePlayer: 'YouTubePlayer' };
        if (name.includes('SeriesDetailBanner')) return { SeriesDetailBanner: 'SeriesDetailBanner' };
        if (name.includes('NextEpisodeAction')) return { NextEpisodeAction: 'NextEpisodeAction' };
        if (name.includes('LibraryControls')) return { ItemLibraryPanel: 'ItemLibraryPanel', QuickSaveButtons: 'QuickSaveButtons' };
        return {};
      },
    });
    const settle = async () => {
      let tree;
      for (let i = 0; i < 6; i++) {
        h.reset();
        tree = pageMod.default();
        h.flush();
        await new Promise(setImmediate);
      }
      h.reset();
      return pageMod.default();
    };
    return { h, savedDrafts, settle };
  };

  const unplayed = await mountFilePage();
  let tree = await unplayed.settle();
  const unplayedVideo = findNodes(tree, (n) => n.props?.['data-testid'] === 'series-video-player')[0];
  unplayedVideo.props.ref.current = {
    currentTime: 120,
    duration: 600,
    readyState: 2,
    paused: true,
    src: '/api/media/stream/ast1',
    play: async () => {},
  };
  unplayedVideo.props.onLoadedMetadata();
  unplayedVideo.props.onPause();
  const s2BtnUnplayed = findNodes(tree, (n) => n.props?.['data-season-id'] === 's2')[0];
  s2BtnUnplayed.props.onClick();
  unplayed.h.unmount();
  assert.equal(unplayed.savedDrafts.length, 0);

  const played = await mountFilePage();
  tree = await played.settle();
  const playedVideo = findNodes(tree, (n) => n.props?.['data-testid'] === 'series-video-player')[0];
  playedVideo.props.ref.current = {
    currentTime: 145,
    duration: 600,
    readyState: 2,
    paused: false,
    src: '/api/media/stream/ast1',
    play: async () => {},
  };
  playedVideo.props.onPlay();
  const s2BtnPlayed = findNodes(tree, (n) => n.props?.['data-season-id'] === 's2')[0];
  s2BtnPlayed.props.onClick();
  played.h.unmount();
  assert.equal(played.savedDrafts.length, 1);
  assert.equal(played.savedDrafts[0].draft.episodeId, 'ep_f1');
  assert.equal(played.savedDrafts[0].draft.positionMs, 145000);
  assert.equal(played.savedDrafts[0].reason, 'episode-change');
});
