import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from '../node_modules/typescript/lib/typescript.js';
import { createInternalResolver, runWithSharedModules } from './internal_loader.mjs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const root = path.resolve(__dirname, '..');

function transpileFile(relativePath) {
  const source = fs.readFileSync(new URL(relativePath, import.meta.url), 'utf8');
  return ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      jsx: ts.JsxEmit.React,
      esModuleInterop: true,
      target: ts.ScriptTarget.ES2022,
    },
  }).outputText;
}

function jsonResponse(body, status = 200, headers = {}) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
    headers: {
      get: (name) => headers[name] ?? headers[name.toLowerCase()] ?? null,
    },
  };
}

function collectNodes(node, predicate) {
  if (!node || typeof node !== 'object') return [];
  if (Array.isArray(node)) return node.flatMap((child) => collectNodes(child, predicate));
  return [...(predicate(node) ? [node] : []), ...collectNodes(node.props?.children, predicate)];
}

function collectText(node) {
  if (node === null || node === undefined || typeof node === 'boolean') return '';
  if (typeof node === 'string' || typeof node === 'number') return String(node);
  if (Array.isArray(node)) return node.map(collectText).join(' ');
  if (typeof node === 'object') return collectText(node.props?.children);
  return '';
}

function createIconProxy() {
  return new Proxy({}, { get: (_target, prop) => (props) => ({ type: String(prop), props }) });
}

function createHarness(relativePath, exportName, mocks = {}, globals = {}, inlineNames = new Set()) {
  let slots = [];
  let cursor = 0;
  let pendingEffects = [];
  let idCounter = 0;
  const same = (a, b) => a && b && a.length === b.length && a.every((v, i) => Object.is(v, b[i]));
  const Fragment = Symbol.for('react.fragment');

  const useEffect = (effect, deps) => {
    const i = cursor++;
    const prev = slots[i];
    if (!prev || !deps || !same(prev.deps, deps)) {
      pendingEffects.push(() => {
        prev?.cleanup?.();
        slots[i] = { deps, cleanup: effect() };
      });
    }
  };

  const react = {
    Fragment,
    createContext(defaultValue) {
      return {
        _value: defaultValue,
        Provider({ value, children }) {
          this._value = value;
          return { type: Fragment, props: { children } };
        },
      };
    },
    useContext(ctx) {
      return ctx?._value;
    },
    useId() {
      const i = cursor++;
      return (slots[i] ||= `id-${++idCounter}`);
    },
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
      const prev = slots[i];
      if (!prev || !deps || !same(prev.deps, deps)) slots[i] = { deps, value: fn };
      return slots[i].value;
    },
    useMemo(factory, deps) {
      const i = cursor++;
      const prev = slots[i];
      if (!prev || !deps || !same(prev.deps, deps)) slots[i] = { deps, value: factory() };
      return slots[i].value;
    },
    useEffect,
    useLayoutEffect: useEffect,
    createElement(type, props, ...children) {
      const mergedProps = { ...props, children };
      if (typeof type === 'function' && inlineNames.has(type.name)) {
        return type(mergedProps);
      }
      return { type, props: mergedProps };
    },
  };

  const baseContext = {
    React: react,
    console,
    AbortController,
    DOMException,
    URL,
    URLSearchParams,
    TextEncoder,
    Buffer,
    Event: globalThis.Event,
    CustomEvent: globalThis.CustomEvent,
    setTimeout,
    clearTimeout,
    setInterval,
    clearInterval,
    Date,
    ...globals,
  };

  const resolver = createInternalResolver(baseContext, (name) => {
    if (name === 'react') return react;
    if (name === 'lucide-react') return createIconProxy();
    if (name in mocks) return mocks[name];
    return {};
  });

  const wrappedRequire = (name) => {
    if (name === 'react') return react;
    if (name === 'lucide-react') return createIconProxy();
    if (name in mocks) return mocks[name];
    return resolver(name);
  };
  wrappedRequire.handlesSharedModules = true;

  const module = { exports: {} };
  runWithSharedModules(
    transpileFile(relativePath),
    {
      ...baseContext,
      module,
      exports: module.exports,
      require: wrappedRequire,
    },
    { filename: relativePath }
  );

  const Component = exportName ? module.exports[exportName] : module.exports.default;

  return {
    exports: module.exports,
    render(props = {}) {
      cursor = 0;
      pendingEffects = [];
      const tree = Component(props);
      const effects = pendingEffects.slice();
      pendingEffects = [];
      for (const run of effects) run();
      return tree;
    },
    unmount() {
      for (const slot of slots) {
        if (typeof slot?.cleanup === 'function') slot.cleanup();
      }
      slots = [];
    },
  };
}

const SAMPLE_SCREEN_TIME = {
  limited: true,
  unlimited: false,
  profileId: 10,
  day: '2026-11-26',
  dailyMinutes: 60,
  mathLevel: 1,
  rewardMinutes: 5,
  maxBonusMinutes: 30,
  dailySeconds: 3600,
  watchedSeconds: 3600,
  rewardSeconds: 0,
  grantSeconds: 0,
  bountySeconds: 0,
  remainingSeconds: 0,
  locked: true,
  bonusCapSeconds: 1800,
  bonusRemainingSeconds: 1800,
  bonusCapReached: false,
};

const SAMPLE_WATCH_PET_FAINTED = {
  enabled: true,
  stage: 'FAINTED',
  continuousWatchSeconds: 1200,
  sweatyThresholdSeconds: 900,
  dizzyThresholdSeconds: 1080,
  faintThresholdSeconds: 1200,
  restMinutes: 5,
  fainted: true,
  resting: false,
  restLockUntil: null,
  restRemainingSeconds: 0,
};

const SAMPLE_WATCH_PET_HAPPY = {
  ...SAMPLE_WATCH_PET_FAINTED,
  stage: 'HAPPY',
  continuousWatchSeconds: 120,
  fainted: false,
};

const SAMPLE_DROP = {
  dropToken: 'drop-1',
  visibleSeconds: 15,
  expiresAt: '2026-11-26T10:00:15Z',
  combo: 1,
};

function makeBasePlayerProps(overrides = {}) {
  return {
    seriesTitle: 'Siêu Nhân',
    selectedEpisode: { id: 'ep_1', title: 'Tập 1', number: 1 },
    episodeDetail: null,
    playbackSource: { kind: 'file', asset: { id: 'a1' } },
    isLoadingEpisode: false,
    isResolvingStream: false,
    episodeError: null,
    playbackWarning: null,
    onDismissWarning: () => {},
    isUnmountedDriveFallback: false,
    onRetryOriginalFile: () => {},
    onRetryEpisode: () => {},
    streamUrl: '/api/media/stream/a1',
    fileRetryCount: 0,
    videoRef: { current: null },
    youtubeSeekRequest: null,
    shouldAutoPlayYoutube: false,
    youtubeHandlers: { onProgress: () => {}, onEnded: () => {}, onPlayingChange: () => {}, onTimeTick: () => {} },
    videoHandlers: {
      onLoadedMetadata: () => {},
      onLoadedData: () => {},
      onCanPlay: () => {},
      onError: () => {},
      onTimeUpdate: () => {},
      onPlay: () => {},
      onPlaying: () => {},
      onPause: () => {},
      onSeeking: () => {},
      onEnded: () => {},
    },
    offerResume: false,
    autoNext: false,
    onAutoNextToggle: () => {},
    nextCountdown: null,
    nextEpisodeLabel: 'Tập 2',
    hasNextEpisode: true,
    onCancelNextCountdown: () => {},
    progressError: null,
    isVipLocked: false,
    vipMathLevel: 3,
    onVipUnlocked: () => {},
    kidPlayOverlay: {
      watchPet: SAMPLE_WATCH_PET_HAPPY,
      isPetLocked: false,
      restCountdown: 0,
      onStartRest: async () => null,
      onWatchPetUpdated: () => {},
      activeDrop: null,
      dropSecondsLeft: 0,
      activeLootQuiz: null,
      lootQuizSecondsLeft: 0,
      lootFeedback: null,
      lootOpening: false,
      lootSubmitting: false,
      onOpenLootChest: async () => {},
      onSubmitLootAnswer: async () => null,
      activeRecapEpisodeId: null,
      onRecapUnlocked: () => {},
      onRecapRewatch: () => {},
    },
    ...overrides,
  };
}

test('resolveActivePlayerOverlay enforces strict priority: time-up > watch-pet > recap > vip > loot', () => {
  const mod = createHarness('../features/profiles/overlayPriority.ts', 'resolveActivePlayerOverlay').exports;
  const resolve = mod.resolveActivePlayerOverlay;

  assert.equal(
    resolve({ hasTimeUpLock: true, hasWatchPetLock: true, hasRecapGate: true, hasVipLock: true, hasLootOverlay: true }),
    'time-up'
  );
  assert.equal(
    resolve({ hasTimeUpLock: false, hasWatchPetLock: true, hasRecapGate: true, hasVipLock: true, hasLootOverlay: true }),
    'watch-pet'
  );
  assert.equal(
    resolve({ hasTimeUpLock: false, hasWatchPetLock: false, hasRecapGate: true, hasVipLock: true, hasLootOverlay: true }),
    'recap'
  );
  assert.equal(
    resolve({ hasTimeUpLock: false, hasWatchPetLock: false, hasRecapGate: false, hasVipLock: true, hasLootOverlay: true }),
    'vip'
  );
  assert.equal(
    resolve({ hasTimeUpLock: false, hasWatchPetLock: false, hasRecapGate: false, hasVipLock: false, hasLootOverlay: true }),
    'loot'
  );
  assert.equal(
    resolve({ hasTimeUpLock: false, hasWatchPetLock: false, hasRecapGate: false, hasVipLock: false, hasLootOverlay: false }),
    null
  );
});

test('EpisodePlayerPanel never stacks two overlays and closes VipUnlockDialog when a higher lock activates', () => {
  let screenTimeState = { isLimited: true, isLocked: true, remainingSeconds: 0, screenTime: SAMPLE_SCREEN_TIME, applyScreenTimeStatus: () => {} };

  const harness = createHarness(
    '../features/series/components/EpisodePlayerPanel.tsx',
    'EpisodePlayerPanel',
    {
      '@/lib/labels': { cleanMediaTitle: (s) => s || '', formatDurationMs: () => '0:00' },
      '@/lib/services/playbackSource': { getUnavailableReasonMessage: () => 'unavailable' },
      '@/lib/context/AuthContext': {
        useOptionalAuth: () => ({ isKidMode: true, isAuthenticated: true, token: 'child-jwt', openAuthModal: () => {} }),
      },
      '@/lib/context/ScreenTimeContext': {
        useOptionalScreenTime: () => screenTimeState,
      },
      '@/features/profiles/components/ScreenTimeLockOverlay': {
        ScreenTimeLockOverlay: () => ({ type: 'div', props: { 'data-testid': 'stub-time-up' } }),
      },
      '@/features/profiles/components/WatchPetWidget': {
        WatchPetWidget: ({ hideCorner }) => ({
          type: 'div',
          props: { 'data-testid': hideCorner ? 'stub-watch-pet-overlay' : 'stub-watch-pet-corner' },
        }),
      },
      '@/features/profiles/components/RecapQuizOverlay': {
        RecapQuizOverlay: () => ({ type: 'div', props: { 'data-testid': 'stub-recap' } }),
      },
      '@/features/profiles/components/LootDropOverlay': {
        LootDropOverlay: () => ({ type: 'div', props: { 'data-testid': 'stub-loot' } }),
      },
      '@/features/profiles/components/VipUnlockDialog': {
        VipUnlockDialog: () => ({ type: 'div', props: { 'data-testid': 'stub-vip-dialog' } }),
      },
    },
    {},
    new Set(['ScreenTimeLockOverlay', 'WatchPetWidget', 'RecapQuizOverlay', 'LootDropOverlay', 'VipUnlockDialog', 'VipBadge'])
  );

  const count = (tree, testId) => collectNodes(tree, (n) => n.props?.['data-testid'] === testId).length;

  const allFiveProps = makeBasePlayerProps({
    isVipLocked: true,
    kidPlayOverlay: {
      ...makeBasePlayerProps().kidPlayOverlay,
      watchPet: SAMPLE_WATCH_PET_FAINTED,
      isPetLocked: true,
      activeRecapEpisodeId: 'ep_0',
      activeDrop: SAMPLE_DROP,
      dropSecondsLeft: 12,
    },
  });

  let tree = harness.render(allFiveProps);
  assert.equal(count(tree, 'stub-time-up'), 1);
  assert.equal(count(tree, 'stub-watch-pet-overlay'), 0);
  assert.equal(count(tree, 'stub-watch-pet-corner'), 0);
  assert.equal(count(tree, 'stub-recap'), 0);
  assert.equal(count(tree, 'vip-episode-lock-overlay'), 0);
  assert.equal(count(tree, 'stub-loot'), 0);

  screenTimeState = { ...screenTimeState, isLocked: false, remainingSeconds: 600, screenTime: { ...SAMPLE_SCREEN_TIME, locked: false, remainingSeconds: 600 } };
  tree = harness.render(allFiveProps);
  assert.equal(count(tree, 'stub-time-up'), 0);
  assert.equal(count(tree, 'stub-watch-pet-overlay'), 1);
  assert.equal(count(tree, 'stub-watch-pet-corner'), 0);
  assert.equal(count(tree, 'stub-recap'), 0);
  assert.equal(count(tree, 'vip-episode-lock-overlay'), 0);
  assert.equal(count(tree, 'stub-loot'), 0);

  const recapAndBelowProps = makeBasePlayerProps({
    isVipLocked: true,
    kidPlayOverlay: {
      ...makeBasePlayerProps().kidPlayOverlay,
      watchPet: SAMPLE_WATCH_PET_HAPPY,
      isPetLocked: false,
      activeRecapEpisodeId: 'ep_0',
      activeDrop: SAMPLE_DROP,
      dropSecondsLeft: 12,
    },
  });
  tree = harness.render(recapAndBelowProps);
  assert.equal(count(tree, 'stub-recap'), 1);
  assert.equal(count(tree, 'vip-episode-lock-overlay'), 0);
  assert.equal(count(tree, 'stub-watch-pet-corner'), 0);
  assert.equal(count(tree, 'stub-loot'), 0);

  const vipAndLootProps = makeBasePlayerProps({
    isVipLocked: true,
    kidPlayOverlay: {
      ...makeBasePlayerProps().kidPlayOverlay,
      watchPet: SAMPLE_WATCH_PET_HAPPY,
      isPetLocked: false,
      activeRecapEpisodeId: null,
      activeDrop: SAMPLE_DROP,
      dropSecondsLeft: 12,
    },
  });
  tree = harness.render(vipAndLootProps);
  assert.equal(count(tree, 'vip-episode-lock-overlay'), 1);
  assert.equal(count(tree, 'stub-loot'), 0);
  assert.equal(count(tree, 'stub-watch-pet-corner'), 0);

  collectNodes(tree, (n) => n.props?.['data-testid'] === 'open-vip-unlock-button')[0].props.onClick();
  tree = harness.render(vipAndLootProps);
  assert.equal(count(tree, 'stub-vip-dialog'), 1);

  tree = harness.render(recapAndBelowProps);
  assert.equal(count(tree, 'stub-recap'), 1);
  assert.equal(count(tree, 'stub-vip-dialog'), 0);

  const lootOnlyProps = makeBasePlayerProps({
    isVipLocked: false,
    kidPlayOverlay: {
      ...makeBasePlayerProps().kidPlayOverlay,
      watchPet: SAMPLE_WATCH_PET_HAPPY,
      isPetLocked: false,
      activeRecapEpisodeId: null,
      activeDrop: SAMPLE_DROP,
      dropSecondsLeft: 12,
    },
  });
  tree = harness.render(lootOnlyProps);
  assert.equal(count(tree, 'stub-loot'), 1);
  assert.equal(count(tree, 'stub-watch-pet-corner'), 1);
  assert.equal(count(tree, 'series-video-player'), 1);

  harness.unmount();
});

test('usePlayFeatures blocks loot spawning and clears active loot immediately when a higher overlay opens', async () => {
  let spawnCalls = 0;
  const fetchMock = async (url, init = {}) => {
    if (url.includes('/users/play-features/status')) {
      return jsonResponse({
        success: true,
        message: 'OK',
        data: {
          active: true,
          settings: { watchPetEnabled: true, watchPetFaintMinutes: 20, watchPetRestMinutes: 5, lootDropEnabled: true, recapToNextEnabled: true },
          watchPet: SAMPLE_WATCH_PET_HAPPY,
          loot: { enabled: true, ready: true, combo: 0 },
          recap: { enabled: true, pendingGates: [], unlockedEpisodeIds: [] },
        },
      });
    }
    if (url.endsWith('/users/play-features/loot/spawn') && init.method === 'POST') {
      spawnCalls += 1;
      return jsonResponse({ success: true, message: 'OK', data: SAMPLE_DROP });
    }
    return jsonResponse({ success: true, message: 'OK', data: {} });
  };

  const harness = createHarness(
    '../features/profiles/hooks/usePlayFeatures.ts',
    'usePlayFeatures',
    {},
    { fetch: fetchMock }
  );

  let state = harness.render({
    enabled: true,
    token: 'kid-jwt',
    seriesId: 'ser_1',
    isPlaying: true,
    isScreenTimeLocked: false,
    isBlockedByHigherOverlay: true,
  });
  await new Promise((r) => setTimeout(r, 15));
  state = harness.render({
    enabled: true,
    token: 'kid-jwt',
    seriesId: 'ser_1',
    isPlaying: true,
    isScreenTimeLocked: false,
    isBlockedByHigherOverlay: true,
  });
  assert.equal(spawnCalls, 0);
  assert.equal(state.activeDrop, null);

  state = harness.render({
    enabled: true,
    token: 'kid-jwt',
    seriesId: 'ser_1',
    isPlaying: true,
    isScreenTimeLocked: false,
    isBlockedByHigherOverlay: false,
  });
  await new Promise((r) => setTimeout(r, 15));
  state = harness.render({
    enabled: true,
    token: 'kid-jwt',
    seriesId: 'ser_1',
    isPlaying: true,
    isScreenTimeLocked: false,
    isBlockedByHigherOverlay: false,
  });
  assert.equal(spawnCalls, 1);
  assert.equal(state.activeDrop?.dropToken, 'drop-1');

  state = harness.render({
    enabled: true,
    token: 'kid-jwt',
    seriesId: 'ser_1',
    isPlaying: true,
    isScreenTimeLocked: false,
    isBlockedByHigherOverlay: true,
  });
  state = harness.render({
    enabled: true,
    token: 'kid-jwt',
    seriesId: 'ser_1',
    isPlaying: true,
    isScreenTimeLocked: false,
    isBlockedByHigherOverlay: true,
  });
  assert.equal(state.activeDrop, null);
  assert.equal(state.dropSecondsLeft, 0);
  harness.unmount();
});

test('Guest hitting a protected episode gets a friendly login prompt and button', () => {
  let openedMode = null;
  const harness = createHarness(
    '../features/series/components/EpisodePlayerPanel.tsx',
    'EpisodePlayerPanel',
    {
      '@/lib/labels': { cleanMediaTitle: (s) => s || '', formatDurationMs: () => '0:00' },
      '@/lib/services/playbackSource': { getUnavailableReasonMessage: () => 'Tài khoản hiện chưa có quyền xem bản video này.' },
      '@/lib/context/AuthContext': {
        useOptionalAuth: () => ({
          isKidMode: false,
          isAuthenticated: false,
          token: null,
          openAuthModal: (mode) => {
            openedMode = mode;
          },
        }),
      },
      '@/lib/context/ScreenTimeContext': { useOptionalScreenTime: () => null },
    }
  );

  const tree = harness.render(
    makeBasePlayerProps({
      playbackSource: {
        kind: 'unavailable',
        reason: 'forbidden',
        failedAsset: null,
        canRetryFile: true,
        youtubeWatchUrl: null,
        youtubeVideoId: null,
      },
      streamUrl: '',
      kidPlayOverlay: undefined,
    })
  );

  assert.match(collectText(tree), /Bạn đăng nhập tài khoản Dippie để xem bản video này/);
  const loginBtn = collectNodes(tree, (n) => n.props?.['data-testid'] === 'player-login-button')[0];
  assert.ok(loginBtn);
  loginBtn.props.onClick();
  assert.equal(openedMode, 'login');
  harness.unmount();
});

test('PIN 429 lockout shows remaining wait time from Retry-After and network failures show friendly Vietnamese', async () => {
  const apiReq = createHarness('../lib/services/apiRequest.ts', 'formatRateLimitMessage').exports;
  assert.equal(apiReq.formatRetryAfterDuration(45), '45 giây');
  assert.equal(apiReq.formatRetryAfterDuration(120), '2 phút');
  assert.equal(apiReq.formatRetryAfterDuration(270), '4 phút 30 giây');
  assert.equal(
    apiReq.formatRateLimitMessage(
      'Bạn đã nhập sai mã PIN quá nhiều lần. Vui lòng thử lại sau ít phút.',
      'fallback',
      270
    ),
    'Bạn đã nhập sai mã PIN quá nhiều lần. Vui lòng thử lại sau 4 phút 30 giây.'
  );

  const profile429 = createHarness(
    '../lib/services/profileService.ts',
    'exitChildProfile',
    {},
    {
      fetch: async () =>
        jsonResponse(
          { success: false, message: 'Bạn đã nhập sai mã PIN quá nhiều lần. Vui lòng thử lại sau ít phút.' },
          429,
          { 'Retry-After': '185' }
        ),
    }
  ).exports;

  await assert.rejects(
    profile429.exitChildProfile('child-jwt', '9999'),
    (err) => err.status === 429 && err.retryAfterSeconds === 185 && /3 phút 5 giây/.test(err.message)
  );

  const profileOffline = createHarness(
    '../lib/services/profileService.ts',
    'fetchProfilesState',
    {},
    {
      fetch: async () => {
        throw new TypeError('Failed to fetch');
      },
    }
  ).exports;

  await assert.rejects(
    profileOffline.fetchProfilesState('parent-jwt'),
    (err) => err.status === 0 && /Không kết nối được với Dippie/.test(err.message)
  );
});

test('ProfilePickerModal traps Tab focus, closes on Escape, and restores opener focus on unmount', async () => {
  let closed = 0;
  let openerFocused = 0;
  let firstFocused = 0;
  let lastFocused = 0;
  const keyListeners = [];

  class FakeElement {
    constructor(onFocus) {
      this.onFocus = onFocus;
    }
    focus() {
      this.onFocus();
      fakeDocument.activeElement = this;
    }
  }

  const openerEl = new FakeElement(() => {
    openerFocused += 1;
  });
  const firstEl = new FakeElement(() => {
    firstFocused += 1;
  });
  const lastEl = new FakeElement(() => {
    lastFocused += 1;
  });

  const fakeDocument = {
    activeElement: openerEl,
    body: { style: { overflow: '' } },
  };
  const fakeWindow = {
    addEventListener: (name, fn) => {
      if (name === 'keydown') keyListeners.push(fn);
    },
    removeEventListener: (name, fn) => {
      if (name === 'keydown') {
        const idx = keyListeners.indexOf(fn);
        if (idx >= 0) keyListeners.splice(idx, 1);
      }
    },
  };

  const harness = createHarness(
    '../features/profiles/components/ProfilePickerModal.tsx',
    'ProfilePickerModal',
    {
      'react-router': { useNavigate: () => () => {}, useLocation: () => ({ pathname: '/' }) },
      '@/lib/labels': { getUserInitials: (s) => (s || 'P').slice(0, 2).toUpperCase() },
      '@/lib/context/AuthContext': {
        useAuth: () => ({
          user: { id: 1, username: 'mom', fullName: 'Mẹ Lan', role: 'ROLE_USER' },
          isAuthenticated: true,
          parentModeEnabled: true,
          childProfiles: [{ id: 10, name: 'Bé Bông', avatarKey: 'bunny', createdAt: '' }],
          activeProfile: null,
          showProfilePicker: true,
          closeProfilePicker: () => {
            closed += 1;
          },
          selectChildProfile: async () => ({ success: true }),
        }),
      },
    },
    { document: fakeDocument, window: fakeWindow, HTMLElement: FakeElement }
  );

  const tree = harness.render();
  const dialogNode = collectNodes(tree, (n) => n.props?.['data-testid'] === 'profile-picker-modal')[0];
  assert.ok(dialogNode);
  assert.equal(fakeDocument.body.style.overflow, 'hidden');

  const fakeModalDom = {
    contains: (el) => el === firstEl || el === lastEl,
    querySelector: () => firstEl,
    querySelectorAll: () => [firstEl, lastEl],
  };
  dialogNode.props.ref.current = fakeModalDom;

  await new Promise((r) => setTimeout(r, 40));
  assert.equal(firstFocused, 1);

  let prevented = false;
  fakeDocument.activeElement = lastEl;
  keyListeners[0]({ key: 'Tab', shiftKey: false, preventDefault: () => { prevented = true; } });
  assert.equal(prevented, true);
  assert.equal(firstFocused, 2);

  prevented = false;
  fakeDocument.activeElement = firstEl;
  keyListeners[0]({ key: 'Tab', shiftKey: true, preventDefault: () => { prevented = true; } });
  assert.equal(prevented, true);
  assert.equal(lastFocused, 1);

  keyListeners[0]({ key: 'Escape', preventDefault: () => {} });
  assert.equal(closed, 1);

  harness.unmount();
  assert.equal(fakeDocument.body.style.overflow, '');
  assert.equal(openerFocused, 1);
});

test('ChildProfilesSection remembers parent PIN for routine setup dialogs, keeps destructive dialogs empty, and shows load retry on error', async () => {
  let overviewCalls = 0;
  let parentEnabled = false;
  const fetchMock = async (url, init = {}) => {
    const method = init.method || 'GET';
    if (url === '/api/users/parent-mode/enable' && method === 'POST') {
      parentEnabled = true;
      return jsonResponse({ success: true, message: 'OK', data: { enabled: true, profileCount: 0 } });
    }
    if (url === '/api/users/profiles/screen-time' && method === 'GET') {
      overviewCalls += 1;
      if (overviewCalls === 1) {
        throw new TypeError('Failed to fetch');
      }
      return jsonResponse({ success: true, message: 'OK', data: [] });
    }
    return jsonResponse({ success: true, message: 'OK', data: {} });
  };

  const harness = createHarness(
    '../features/account/components/ChildProfilesSection.tsx',
    'ChildProfilesSection',
    {
      'react-router': { useNavigate: () => () => {} },
      '@/lib/context/AuthContext': {
        useAuth: () => ({
          token: 'parent-jwt',
          parentModeEnabled: parentEnabled,
          childProfiles: parentEnabled ? [{ id: 10, name: 'Bé Bông', avatarKey: 'bunny', createdAt: '' }] : [],
          refreshProfiles: async () => null,
          selectChildProfile: async () => ({ success: true }),
        }),
      },
      '@/features/account/components/ParentBountySection': { ParentBountySection: () => null },
      '@/features/account/components/ParentWeekendVaultSection': { ParentWeekendVaultSection: () => null },
      '@/features/account/components/ParentVipSeriesSection': { ParentVipSeriesSection: () => null },
    },
    { fetch: fetchMock },
    new Set(['SiteDialog'])
  );

  let tree = harness.render();
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'enable-parent-mode-button')[0].props.onClick();
  tree = harness.render();
  collectNodes(tree, (n) => n.props?.id === 'parent-pin-new')[0].props.onChange({ target: { value: '2468' } });
  collectNodes(tree, (n) => n.props?.id === 'parent-pin-confirm')[0].props.onChange({ target: { value: '2468' } });
  tree = harness.render();
  await collectNodes(tree, (n) => n.type === 'form')[0].props.onSubmit({ preventDefault: () => {} });
  tree = harness.render();
  await new Promise((r) => setTimeout(r, 15));
  tree = harness.render();

  assert.equal(collectNodes(tree, (n) => n.props?.['data-testid'] === 'screen-time-overview-error').length, 1);
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'retry-screen-time-overview')[0].props.onClick();
  await new Promise((r) => setTimeout(r, 15));
  tree = harness.render();
  assert.equal(collectNodes(tree, (n) => n.props?.['data-testid'] === 'screen-time-overview-error').length, 0);

  collectNodes(tree, (n) => n.props?.['data-testid'] === 'add-child-profile-button')[0].props.onClick();
  tree = harness.render();
  const createPinInput = collectNodes(tree, (n) => n.props?.id === 'child-profile-pin')[0];
  assert.equal(createPinInput.props.value, '2468');
  assert.equal(collectNodes(tree, (n) => n.props?.['data-testid'] === 'session-pin-prefilled-note').length, 1);

  collectNodes(tree, (n) => n.props?.['data-testid'] === 'disable-parent-mode-button')[0].props.onClick();
  tree = harness.render();
  const disablePinInput = collectNodes(tree, (n) => n.props?.id === 'parent-pin-disable')[0];
  assert.equal(disablePinInput.props.value, '');

  harness.unmount();
});

test('Kid controls keep >=44px targets, numeric keyboard hints, gentle Dieppie copy, and MovieDetail handles 423 + nested dialog Escape', () => {
  const recapSource = fs.readFileSync(path.join(root, 'features/profiles/components/RecapQuizOverlay.tsx'), 'utf8');
  assert.equal(recapSource.includes('h-8 w-8'), false);
  assert.equal(recapSource.includes('min-h-[36px]'), false);
  assert.ok(recapSource.includes('h-11 w-11 min-h-[44px] min-w-[44px]'));

  const bountySource = fs.readFileSync(path.join(root, 'features/profiles/components/KidBountyBoard.tsx'), 'utf8');
  assert.equal(bountySource.includes('min-h-[40px]'), false);

  const watchPetSource = fs.readFileSync(path.join(root, 'features/profiles/components/WatchPetWidget.tsx'), 'utf8');
  assert.equal(watchPetSource.includes('ngất xỉu'), false);
  assert.equal(watchPetSource.includes('mệt xỉu'), false);
  assert.ok(watchPetSource.includes('Dieppie mỏi mắt lắm rồi!'));
  assert.ok(watchPetSource.includes('pattern="[0-9]*"'));

  const lockSource = fs.readFileSync(path.join(root, 'features/profiles/components/ScreenTimeLockOverlay.tsx'), 'utf8');
  assert.ok(lockSource.includes('overflow-y-auto'));
  assert.ok(lockSource.includes('pattern="[0-9]*"'));
  assert.ok(lockSource.includes('enterKeyHint="done"'));

  const movieDetailSource = fs.readFileSync(path.join(root, 'features/movies/components/MovieDetail.tsx'), 'utf8');
  assert.ok(movieDetailSource.includes("document.querySelector('dialog[open]')"));
  assert.ok(movieDetailSource.includes('resolveActivePlayerOverlay'));
});
