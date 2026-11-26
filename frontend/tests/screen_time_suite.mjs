import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import ts from '../node_modules/typescript/lib/typescript.js';
import { createInternalResolver, runWithSharedModules } from './internal_loader.mjs';

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

function createComponentHarness(relativePath, exportName, mocks = {}, globals = {}, inlineNames = new Set()) {
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
  const code = transpileFile(relativePath);
  runWithSharedModules(
    code,
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

function loadServiceModule(relativePath, globals = {}) {
  const module = { exports: {} };
  const code = transpileFile(relativePath);
  runWithSharedModules(
    code,
    {
      console,
      AbortController,
      DOMException,
      URL,
      URLSearchParams,
      Event: globalThis.Event,
      setTimeout,
      clearTimeout,
      ...globals,
      module,
      exports: module.exports,
      require: () => ({}),
    },
    { filename: relativePath }
  );
  return module.exports;
}

function sampleStatus(overrides = {}) {
  return {
    limited: true,
    unlimited: false,
    profileId: 10,
    day: '2026-11-26',
    dailyMinutes: 60,
    quizGrade: 'LOP_1',
    quizGradeLabel: 'Lớp 1',
    rewardMinutes: 5,
    maxBonusMinutes: 30,
    dailySeconds: 3600,
    watchedSeconds: 600,
    rewardSeconds: 0,
    grantSeconds: 0,
    bountySeconds: 0,
    remainingSeconds: 3000,
    locked: false,
    bonusCapSeconds: 1800,
    bonusRemainingSeconds: 1800,
    bonusCapReached: false,
    ...overrides,
  };
}

test('screenTimeService formats remaining time, usage duration, and validates parent inputs', () => {
  const svc = loadServiceModule('../lib/services/screenTimeService.ts');

  assert.equal(svc.formatRemainingTime(0, true), 'Hết giờ xem');
  assert.equal(svc.formatRemainingTime(45, false), 'Còn 45 giây');
  assert.equal(svc.formatRemainingTime(600, false), 'Còn 10 phút');
  assert.equal(svc.formatRemainingTime(5400, false), 'Còn 1 giờ 30 phút');
  assert.equal(svc.formatRemainingTime(null, false), '');

  assert.equal(svc.formatUsageDuration(0), '0 phút');
  assert.equal(svc.formatUsageDuration(35), '35 giây');
  assert.equal(svc.formatUsageDuration(900), '15 phút');
  assert.equal(svc.formatUsageDuration(3600), '1 giờ');

  assert.equal(
    svc.validateScreenTimeSettings({ dailyMinutes: 60, rewardMinutes: 5, maxBonusMinutes: 30 }),
    null
  );
  assert.ok(
    svc.validateScreenTimeSettings({ dailyMinutes: 601, rewardMinutes: 5, maxBonusMinutes: 30 })
  );
  assert.ok(
    svc.validateScreenTimeSettings({ dailyMinutes: 60, rewardMinutes: 0, maxBonusMinutes: 30 })
  );
  assert.ok(
    svc.validateScreenTimeSettings({ dailyMinutes: 60, rewardMinutes: 5, maxBonusMinutes: 121 })
  );
  assert.equal(svc.validateGrantMinutes(15), null);
  assert.ok(svc.validateGrantMinutes(0));
  assert.ok(svc.validateGrantMinutes(181));
});

test('ScreenTimeChip displays remaining time in profile bar for kid mode and stays hidden for main profile', () => {
  const kidStatus = sampleStatus({ remainingSeconds: 720, locked: false });
  const kidHarness = createComponentHarness(
    '../components/layout/Navbar.tsx',
    'Navbar',
    {
      'react-router': {
        Link: (props) => ({ type: 'a', props }),
        useLocation: () => ({ pathname: '/' }),
      },
      '@/lib/labels': { getUserInitials: () => 'BB' },
      '@/lib/context/WatchlistContext': {
        useWatchlist: () => ({ watchlist: [], openChat: () => {} }),
      },
      '@/lib/context/AuthContext': {
        useAuth: () => ({
          user: { id: 1, username: 'parent', email: 'p@test.vn', role: 'ROLE_USER' },
          isAuthenticated: true,
          openAuthModal: () => {},
          logout: () => {},
          isKidMode: true,
          activeProfile: { id: 10, name: 'Bé Bông', avatarKey: 'bunny', createdAt: '' },
          parentModeEnabled: true,
          openProfilePicker: () => {},
        }),
      },
      '@/lib/context/ScreenTimeContext': {
        useOptionalScreenTime: () => ({
          screenTime: kidStatus,
          isLimited: true,
          isLocked: false,
          remainingSeconds: 720,
        }),
      },
    },
    { document: { addEventListener: () => {}, removeEventListener: () => {} } },
    new Set(['ScreenTimeChip'])
  );

  const kidTree = kidHarness.render();
  const barChips = collectNodes(kidTree, (n) => n.props?.['data-testid'] === 'profile-bar-screen-time-chip');
  assert.equal(barChips.length, 1);
  assert.match(collectText(barChips[0]), /Còn 12 phút/);
  kidHarness.unmount();

  const mainHarness = createComponentHarness(
    '../components/layout/Navbar.tsx',
    'Navbar',
    {
      'react-router': {
        Link: (props) => ({ type: 'a', props }),
        useLocation: () => ({ pathname: '/' }),
      },
      '@/lib/labels': { getUserInitials: () => 'PM' },
      '@/lib/context/WatchlistContext': {
        useWatchlist: () => ({ watchlist: [], openChat: () => {} }),
      },
      '@/lib/context/AuthContext': {
        useAuth: () => ({
          user: { id: 1, username: 'parent', email: 'p@test.vn', role: 'ROLE_USER' },
          isAuthenticated: true,
          openAuthModal: () => {},
          logout: () => {},
          isKidMode: false,
          activeProfile: null,
          parentModeEnabled: true,
          openProfilePicker: () => {},
        }),
      },
      '@/lib/context/ScreenTimeContext': {
        useOptionalScreenTime: () => ({
          screenTime: null,
          isLimited: false,
          isLocked: false,
          remainingSeconds: null,
        }),
      },
    },
    { document: { addEventListener: () => {}, removeEventListener: () => {} } },
    new Set(['ScreenTimeChip'])
  );

  const mainTree = mainHarness.render();
  const mainChips = collectNodes(mainTree, (n) => n.props?.['data-testid'] === 'profile-bar-screen-time-chip');
  assert.equal(mainChips.length, 0);
  mainHarness.unmount();
});

test('EpisodePlayerPanel shows player chip, pauses video at 0, and displays friendly lock overlay', () => {
  let pauseCalls = 0;
  const videoRef = { current: { pause: () => { pauseCalls += 1; } } };
  const lockedStatus = sampleStatus({ remainingSeconds: 0, locked: true, watchedSeconds: 3600 });

  const harness = createComponentHarness(
    '../features/series/components/EpisodePlayerPanel.tsx',
    'EpisodePlayerPanel',
    {
      '@/features/series/components/PlaybackReportButton': {
        PlaybackReportButton: () => null,
      },
      '@/lib/labels': {
        cleanMediaTitle: (t) => t || '',
        formatDurationMs: () => '00:00',
      },
      '@/lib/services/playbackSource': {
        getUnavailableReasonMessage: () => 'Không phát được',
      },
      '@/lib/context/AuthContext': {
        useOptionalAuth: () => ({ isKidMode: true, token: 'child-jwt' }),
      },
      '@/lib/context/ScreenTimeContext': {
        useOptionalScreenTime: () => ({
          screenTime: lockedStatus,
          isLimited: true,
          isLocked: true,
          remainingSeconds: 0,
          applyScreenTimeStatus: () => {},
        }),
      },
    },
    {},
    new Set(['ScreenTimeChip', 'ScreenTimeLockOverlay'])
  );

  const tree = harness.render({
    seriesTitle: 'Hoạt Hình Gấu',
    selectedEpisode: { id: 'ep1', title: 'Tập 1' },
    episodeDetail: null,
    playbackSource: { kind: 'file', asset: { id: 'asset1' } },
    isLoadingEpisode: false,
    isResolvingStream: false,
    episodeError: null,
    playbackWarning: null,
    onDismissWarning: () => {},
    isUnmountedDriveFallback: false,
    onRetryOriginalFile: () => {},
    onRetryEpisode: () => {},
    streamUrl: '/api/media/stream/asset1',
    fileRetryCount: 0,
    videoRef,
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
    savedProgress: undefined,
    onResumePlayback: () => {},
    autoNext: false,
    onAutoNextToggle: () => {},
    nextCountdown: null,
    nextEpisodeLabel: '',
    hasNextEpisode: false,
    onCancelNextCountdown: () => {},
    progressError: null,
  });

  assert.equal(pauseCalls, 1);
  const playerChip = collectNodes(tree, (n) => n.props?.['data-testid'] === 'player-screen-time-chip');
  assert.equal(playerChip.length, 1);
  assert.match(collectText(playerChip[0]), /Hết giờ xem/);

  const overlay = collectNodes(tree, (n) => n.props?.['data-testid'] === 'screen-time-lock-overlay');
  assert.equal(overlay.length, 1);
  assert.match(collectText(overlay[0]), /Hết giờ xem hôm nay rồi/);
  const quizBtn = collectNodes(tree, (n) => n.props?.['data-testid'] === 'open-quiz-challenge-button');
  assert.equal(quizBtn.length, 1);
  assert.match(collectText(quizBtn[0]), /Trả lời câu hỏi để thêm phút/);
  harness.unmount();
});

test('ScreenTimeLockOverlay runs full multiple-choice quiz flow: 4 buttons, gentle wrong reveal, fresh question reward and new remaining time', async () => {
  const requests = [];
  let issueCount = 0;
  const fetchMock = async (url, init = {}) => {
    requests.push({ url, method: init.method || 'GET', body: init.body ? JSON.parse(init.body) : null });
    if (url === '/api/users/screen-time/challenge') {
      issueCount += 1;
      return jsonResponse({
        success: true,
        message: 'Thành công',
        data: {
          token: `opaque-token-${issueCount}`,
          question: '7 + 5 = ?',
          options: [
            { key: 'A', text: '10' },
            { key: 'B', text: '11' },
            { key: 'C', text: '12' },
            { key: 'D', text: '13' },
          ],
          subject: 'TOAN',
          subjectLabel: 'Toán',
          grade: 'LOP_1',
          gradeLabel: 'Lớp 1',
          setName: 'Toán học',
          rewardMinutes: 5,
          expiresAt: '2026-11-26T10:02:00',
          expiresInSeconds: 120,
          bonusRemainingSeconds: 1800,
        },
      });
    }
    if (url === '/api/users/screen-time/challenge/answer') {
      const payload = JSON.parse(init.body);
      if (payload.option !== 'C') {
        return jsonResponse({
          success: true,
          message: 'Gần đúng rồi! Đáp án là C: 12 nè',
          data: {
            correct: false,
            correctOption: 'C',
            correctText: '12',
            explanation: '7 + 5 = 12',
            earnedSeconds: 0,
            earnedMinutes: 0,
            bonusCapReached: false,
            status: sampleStatus({ remainingSeconds: 0, locked: true, watchedSeconds: 3600 }),
          },
        });
      }
      return jsonResponse({
        success: true,
        message: 'Đúng rồi! Bé được cộng thêm 5 phút xem phim.',
        data: {
          correct: true,
          correctOption: 'C',
          correctText: '12',
          explanation: '7 + 5 = 12',
          earnedSeconds: 300,
          earnedMinutes: 5,
          bonusCapReached: false,
          status: sampleStatus({ remainingSeconds: 300, locked: false, rewardSeconds: 300, watchedSeconds: 3600 }),
        },
      });
    }
    throw new Error(`Unexpected URL ${url}`);
  };

  let updatedStatus = null;
  let continued = false;
  const initialStatus = sampleStatus({ remainingSeconds: 0, locked: true, watchedSeconds: 3600 });

  const harness = createComponentHarness(
    '../features/profiles/components/ScreenTimeLockOverlay.tsx',
    'ScreenTimeLockOverlay',
    {},
    { fetch: fetchMock },
    new Set(['QuizQuestionCard'])
  );

  const props = {
    status: initialStatus,
    token: 'child-jwt',
    onStatusUpdated: (next) => {
      updatedStatus = next;
    },
    onContinueWatching: () => {
      continued = true;
    },
  };

  let tree = harness.render(props);
  const openBtn = collectNodes(tree, (n) => n.props?.['data-testid'] === 'open-quiz-challenge-button')[0];
  assert.ok(openBtn);
  openBtn.props.onClick();
  await new Promise((resolve) => setTimeout(resolve, 10));

  tree = harness.render(props);
  const questionNode = collectNodes(tree, (n) => n.props?.['data-testid'] === 'quiz-challenge-question')[0];
  assert.equal(collectText(questionNode).trim(), '7 + 5 = ?');
  assert.equal(collectNodes(tree, (n) => n.props?.['data-testid'] === 'quiz-read-aloud-button').length, 1);
  assert.equal(collectNodes(tree, (n) => n.type === 'input').length, 0);

  collectNodes(tree, (n) => n.props?.['data-testid'] === 'quiz-option-A')[0].props.onClick();
  tree = harness.render(props);
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'quiz-confirm-answer-button')[0].props.onClick();
  await new Promise((resolve) => setTimeout(resolve, 10));
  tree = harness.render(props);

  const wrongFeedback = collectNodes(tree, (n) => n.props?.['data-testid'] === 'quiz-feedback')[0];
  assert.match(collectText(wrongFeedback), /Gần đúng rồi! Đáp án là C: 12 nè/);

  collectNodes(tree, (n) => n.props?.['data-testid'] === 'quiz-next-question-button')[0].props.onClick();
  await new Promise((resolve) => setTimeout(resolve, 10));
  tree = harness.render(props);

  collectNodes(tree, (n) => n.props?.['data-testid'] === 'quiz-option-C')[0].props.onClick();
  tree = harness.render(props);
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'quiz-confirm-answer-button')[0].props.onClick();
  await new Promise((resolve) => setTimeout(resolve, 10));
  tree = harness.render(props);

  assert.equal(updatedStatus?.remainingSeconds, 300);
  assert.equal(updatedStatus?.locked, false);
  const successFeedback = collectNodes(tree, (n) => n.props?.['data-testid'] === 'quiz-feedback')[0];
  assert.match(collectText(successFeedback), /cộng thêm 5 phút/);
  const remainingNode = collectNodes(tree, (n) => n.props?.['data-testid'] === 'quiz-new-remaining')[0];
  assert.match(collectText(remainingNode), /Còn 5 phút/);

  const continueBtn = collectNodes(tree, (n) => n.props?.['data-testid'] === 'quiz-continue-watching')[0];
  assert.ok(continueBtn);
  continueBtn.props.onClick();
  assert.equal(continued, true);
  assert.equal(requests.length, 4);
  harness.unmount();
});

test('ScreenTimeLockOverlay hides quiz button and suggests asking a parent when daily bonus cap is reached', () => {
  const cappedStatus = sampleStatus({
    remainingSeconds: 0,
    locked: true,
    watchedSeconds: 5400,
    rewardSeconds: 1800,
    bonusRemainingSeconds: 0,
    bonusCapReached: true,
  });

  const harness = createComponentHarness(
    '../features/profiles/components/ScreenTimeLockOverlay.tsx',
    'ScreenTimeLockOverlay'
  );

  const tree = harness.render({
    status: cappedStatus,
    token: 'child-jwt',
    onStatusUpdated: () => {},
    onContinueWatching: () => {},
  });

  assert.match(collectText(tree), /Hết giờ xem hôm nay rồi/);
  const capNote = collectNodes(tree, (n) => n.props?.['data-testid'] === 'bonus-cap-reached-note');
  assert.equal(capNote.length, 1);
  assert.match(collectText(capNote[0]), /nhờ ba mẹ/i);
  const quizBtn = collectNodes(tree, (n) => n.props?.['data-testid'] === 'open-quiz-challenge-button');
  assert.equal(quizBtn.length, 0);
  harness.unmount();
});

test('useScreenTimeHeartbeat sends heartbeat every 15s only when playing in kid mode, never for main profile', () => {
  let fakeNow = 1_000_000;
  let scheduledCallback = null;
  let scheduledDelay = null;
  let clearedCount = 0;

  const FakeDate = { now: () => fakeNow };
  const fakeSetInterval = (fn, ms) => {
    scheduledCallback = fn;
    scheduledDelay = ms;
    return 99;
  };
  const fakeClearInterval = () => {
    clearedCount += 1;
    scheduledCallback = null;
  };

  const sent = [];
  const harness = createComponentHarness(
    '../features/profiles/hooks/useScreenTimeHeartbeat.ts',
    'useScreenTimeHeartbeat',
    {},
    { Date: FakeDate, setInterval: fakeSetInterval, clearInterval: fakeClearInterval, document: { visibilityState: 'visible' } }
  );

  harness.render({ enabled: false, isPlaying: true, onHeartbeat: (s) => sent.push(s) });
  assert.equal(scheduledCallback, null);
  assert.equal(sent.length, 0);

  harness.render({ enabled: true, isPlaying: false, onHeartbeat: (s) => sent.push(s) });
  assert.equal(scheduledCallback, null);

  harness.render({ enabled: true, isPlaying: true, onHeartbeat: (s) => sent.push(s) });
  assert.equal(scheduledDelay, 15_000);
  assert.ok(typeof scheduledCallback === 'function');

  fakeNow += 15_000;
  scheduledCallback();
  assert.deepEqual(sent, [15]);

  fakeNow += 15_000;
  scheduledCallback();
  assert.deepEqual(sent, [15, 15]);

  harness.render({ enabled: true, isPlaying: false, onHeartbeat: (s) => sent.push(s) });
  assert.equal(scheduledCallback, null);
  assert.ok(clearedCount >= 1);
  harness.unmount();
});

test('ChildProfilesSection shows today usage summary, edits screen-time settings, and sends one-off 15 minute grant with PIN', async () => {
  const requests = [];
  const initialOverview = [
    {
      profileId: 10,
      name: 'Bé Bông',
      avatarKey: 'bunny',
      status: sampleStatus({
        profileId: 10,
        dailyMinutes: 60,
        mathLevel: 1,
        rewardMinutes: 5,
        maxBonusMinutes: 30,
        watchedSeconds: 900,
        rewardSeconds: 300,
        grantSeconds: 0,
        remainingSeconds: 3000,
      }),
    },
  ];

  const fetchMock = async (url, init = {}) => {
    const method = init.method || 'GET';
    const body = init.body ? JSON.parse(init.body) : null;
    requests.push({ url, method, body });

    if (url === '/api/users/profiles/screen-time' && method === 'GET') {
      return jsonResponse({ success: true, message: 'Thành công', data: initialOverview });
    }
    if (url === '/api/users/quiz/sets' && method === 'GET') {
      return jsonResponse({ success: true, message: 'OK', data: { sets: [], ownSetCount: 0, maxSetsPerAccount: 20, maxQuestionsPerSet: 500, maxCsvBytes: 1048576, grades: [], subjects: [] } });
    }
    if (url === '/api/users/quiz/profiles' && method === 'GET') {
      return jsonResponse({ success: true, message: 'OK', data: [] });
    }
    if (url === '/api/users/profiles/10/screen-time' && method === 'PUT') {
      return jsonResponse({
        success: true,
        message: 'Đã lưu cài đặt thời gian xem.',
        data: {
          profileId: 10,
          name: 'Bé Bông',
          avatarKey: 'bunny',
          status: sampleStatus({
            dailyMinutes: body.dailyMinutes,
            rewardMinutes: body.rewardMinutes,
            maxBonusMinutes: body.maxBonusMinutes,
            watchedSeconds: 900,
            rewardSeconds: 300,
            remainingSeconds: body.dailyMinutes * 60 + 300 - 900,
          }),
        },
      });
    }
    if (url === '/api/users/profiles/10/screen-time/grant' && method === 'POST') {
      return jsonResponse({
        success: true,
        message: 'Đã cộng thêm 15 phút xem phim cho bé.',
        data: {
          profileId: 10,
          name: 'Bé Bông',
          avatarKey: 'bunny',
          status: sampleStatus({
            watchedSeconds: 900,
            rewardSeconds: 300,
            grantSeconds: body.minutes * 60,
            remainingSeconds: 3000 + body.minutes * 60,
          }),
        },
      });
    }
    throw new Error(`Unexpected request ${method} ${url}`);
  };

  const harness = createComponentHarness(
    '../features/account/components/ChildProfilesSection.tsx',
    'ChildProfilesSection',
    {
      'react-router': { useNavigate: () => () => {} },
      '@/lib/context/AuthContext': {
        useAuth: () => ({
          token: 'parent-jwt',
          parentModeEnabled: true,
          childProfiles: [{ id: 10, name: 'Bé Bông', avatarKey: 'bunny', dailyMinutes: 60, quizGrade: 'LOP_1', quizGradeLabel: 'Lớp 1', rewardMinutes: 5, maxBonusMinutes: 30, createdAt: '' }],
          refreshProfiles: async () => null,
          selectChildProfile: async () => ({ success: true }),
        }),
      },
    },
    { fetch: fetchMock },
    new Set(['SiteDialog'])
  );

  let tree = harness.render();
  await new Promise((resolve) => setTimeout(resolve, 10));
  tree = harness.render();

  const summaryNode = collectNodes(tree, (n) => n.props?.['data-testid'] === 'child-screen-time-summary-10')[0];
  assert.ok(summaryNode);
  const summaryText = collectText(summaryNode);
  assert.match(summaryText, /15 phút/);
  assert.match(summaryText, /5 phút/);
  assert.match(summaryText, /Còn 50 phút/);

  const settingsBtn = collectNodes(tree, (n) => n.props?.['data-testid'] === 'screen-time-settings-button-10')[0];
  settingsBtn.props.onClick();
  tree = harness.render();

  collectNodes(tree, (n) => n.props?.['data-testid'] === 'screen-time-daily-minutes-input')[0].props.onChange({ target: { value: '45' } });
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'screen-time-reward-minutes-input')[0].props.onChange({ target: { value: '10' } });
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'screen-time-max-bonus-input')[0].props.onChange({ target: { value: '40' } });
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'screen-time-settings-pin-input')[0].props.onChange({ target: { value: '2468' } });
  tree = harness.render();

  await collectNodes(tree, (n) => n.type === 'form')[0].props.onSubmit({ preventDefault: () => {} });
  tree = harness.render();

  const settingsReq = requests.find((r) => r.method === 'PUT' && r.url === '/api/users/profiles/10/screen-time');
  assert.deepEqual(settingsReq?.body, {
    pin: '2468',
    dailyMinutes: 45,
    unlimited: false,
    rewardMinutes: 10,
    maxBonusMinutes: 40,
  });

  const grantBtn = collectNodes(tree, (n) => n.props?.['data-testid'] === 'grant-time-button-10')[0];
  grantBtn.props.onClick();
  tree = harness.render();

  collectNodes(tree, (n) => n.props?.['data-testid'] === 'grant-pin-input')[0].props.onChange({ target: { value: '2468' } });
  tree = harness.render();

  await collectNodes(tree, (n) => n.type === 'form')[0].props.onSubmit({ preventDefault: () => {} });
  tree = harness.render();

  const grantReq = requests.find((r) => r.method === 'POST' && r.url === '/api/users/profiles/10/screen-time/grant');
  assert.deepEqual(grantReq?.body, { pin: '2468', minutes: 15 });
  assert.match(collectText(tree), /Đã cộng thêm 15 phút xem phim cho Bé Bông/);
  harness.unmount();
});

test('ChildProfilesSection supports daily minutes on create, shows card presets, and saves unlimited immediately', async () => {
  const requests = [];
  const storageValues = new Map();
  const fakeWindow = {
    localStorage: {
      setItem: (key, value) => storageValues.set(key, value),
      getItem: (key) => storageValues.get(key) ?? null,
    },
    dispatchEvent: () => true,
  };

  const fetchMock = async (url, init = {}) => {
    const method = init.method || 'GET';
    const body = init.body ? JSON.parse(init.body) : null;
    requests.push({ url, method, body });

    if (url === '/api/users/profiles/screen-time' && method === 'GET') {
      return jsonResponse({
        success: true,
        message: 'Thành công',
        data: [
          {
            profileId: 10,
            name: 'Bé Bông',
            avatarKey: 'bunny',
            status: sampleStatus({ dailyMinutes: 60, unlimited: false, remainingSeconds: 3600 }),
          },
        ],
      });
    }
    if (url === '/api/users/quiz/sets' && method === 'GET') {
      return jsonResponse({ success: true, message: 'OK', data: { sets: [], ownSetCount: 0, maxSetsPerAccount: 20, maxQuestionsPerSet: 500, maxCsvBytes: 1048576, grades: [], subjects: [] } });
    }
    if (url === '/api/users/quiz/profiles' && method === 'GET') {
      return jsonResponse({ success: true, message: 'OK', data: [] });
    }
    if (url === '/api/users/profiles' && method === 'POST') {
      return jsonResponse(
        {
          success: true,
          message: 'Đã tạo hồ sơ trẻ em.',
          data: {
            id: 11,
            name: body.name,
            avatarKey: body.avatarKey,
            dailyMinutes: body.unlimited ? null : body.dailyMinutes,
            unlimited: Boolean(body.unlimited),
            createdAt: '2026-11-26T10:00:00Z',
          },
        },
        201
      );
    }
    if (url === '/api/users/profiles/10/screen-time' && method === 'PUT') {
      return jsonResponse({
        success: true,
        message: 'Đã lưu cài đặt thời gian xem.',
        data: {
          profileId: 10,
          name: 'Bé Bông',
          avatarKey: 'bunny',
          status: sampleStatus({
            limited: !body.unlimited,
            unlimited: Boolean(body.unlimited),
            dailyMinutes: body.unlimited ? null : body.dailyMinutes,
            remainingSeconds: body.unlimited ? null : body.dailyMinutes * 60,
            locked: false,
          }),
        },
      });
    }
    throw new Error(`Unexpected request ${method} ${url}`);
  };

  const harness = createComponentHarness(
    '../features/account/components/ChildProfilesSection.tsx',
    'ChildProfilesSection',
    {
      'react-router': { useNavigate: () => () => {} },
      '@/lib/context/AuthContext': {
        useAuth: () => ({
          token: 'parent-jwt',
          parentModeEnabled: true,
          childProfiles: [{ id: 10, name: 'Bé Bông', avatarKey: 'bunny', dailyMinutes: 60, unlimited: false, createdAt: '' }],
          refreshProfiles: async () => null,
          selectChildProfile: async () => ({ success: true }),
        }),
      },
    },
    { fetch: fetchMock, window: fakeWindow },
    new Set(['SiteDialog'])
  );

  let tree = harness.render();
  await new Promise((resolve) => setTimeout(resolve, 10));
  tree = harness.render();

  const limitLabel = collectNodes(tree, (n) => n.props?.['data-testid'] === 'child-daily-limit-10')[0];
  assert.match(collectText(limitLabel).replace(/\s+/g, ' '), /Thời gian xem mỗi ngày: 60 phút/);
  assert.equal(collectNodes(tree, (n) => n.props?.['data-testid'] === 'grant-time-button-10').length, 1);

  collectNodes(tree, (n) => n.props?.['data-testid'] === 'add-child-profile-button')[0].props.onClick();
  tree = harness.render();
  collectNodes(tree, (n) => n.props?.id === 'child-profile-name')[0].props.onChange({ target: { value: 'Bé Na' } });
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'create-daily-preset-90')[0].props.onClick();
  collectNodes(tree, (n) => n.props?.id === 'child-profile-pin')[0].props.onChange({ target: { value: '1234' } });
  tree = harness.render();
  await collectNodes(tree, (n) => n.type === 'form')[0].props.onSubmit({ preventDefault: () => {} });
  tree = harness.render();

  const createReq = requests.find((r) => r.method === 'POST' && r.url === '/api/users/profiles');
  assert.deepEqual(createReq?.body, {
    pin: '1234',
    name: 'Bé Na',
    avatarKey: 'bear',
    dailyMinutes: 90,
    unlimited: false,
    quizGrade: 'LOP_1',
  });

  collectNodes(tree, (n) => n.props?.['data-testid'] === 'screen-time-settings-button-10')[0].props.onClick();
  tree = harness.render();
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'screen-time-unlimited-toggle')[0].props.onClick();
  tree = harness.render();
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'screen-time-settings-pin-input')[0].props.onChange({ target: { value: '1234' } });
  tree = harness.render();
  await collectNodes(tree, (n) => n.type === 'form')[0].props.onSubmit({ preventDefault: () => {} });
  tree = harness.render();

  const unlimitedReq = requests.find((r) => r.method === 'PUT' && r.url === '/api/users/profiles/10/screen-time');
  assert.equal(unlimitedReq?.body.unlimited, true);
  assert.equal(unlimitedReq?.body.dailyMinutes, null);
  const updatedLabel = collectNodes(tree, (n) => n.props?.['data-testid'] === 'child-daily-limit-10')[0];
  assert.match(collectText(updatedLabel).replace(/\s+/g, ' '), /Thời gian xem mỗi ngày: Không giới hạn/);
  assert.ok(storageValues.has('dippie:screen-time-updated'));
  harness.unmount();
});

test('ScreenTimeContext clears limited state on unlimited heartbeat and refreshes on cross-tab update', async () => {
  const listeners = new Map();
  const fakeWindow = {
    addEventListener: (event, fn) => {
      const list = listeners.get(event) || [];
      list.push(fn);
      listeners.set(event, list);
    },
    removeEventListener: (event, fn) => {
      listeners.set(event, (listeners.get(event) || []).filter((item) => item !== fn));
    },
    dispatchEvent: () => true,
  };
  let statusCalls = 0;
  let capturedCtx = null;
  const fetchMock = async (url, init = {}) => {
    if (url === '/api/users/screen-time') {
      statusCalls += 1;
      return jsonResponse({
        success: true,
        message: 'OK',
        data: sampleStatus({
          dailyMinutes: statusCalls === 1 ? 60 : 120,
          remainingSeconds: statusCalls === 1 ? 3600 : 7200,
        }),
      });
    }
    if (url === '/api/users/screen-time/heartbeat' && init.method === 'POST') {
      return jsonResponse({
        success: true,
        message: 'OK',
        data: {
          limited: false,
          creditedSeconds: 0,
          ignored: true,
          remainingSeconds: null,
          locked: false,
          status: sampleStatus({
            limited: false,
            unlimited: true,
            dailyMinutes: null,
            remainingSeconds: null,
            locked: false,
          }),
        },
      });
    }
    throw new Error(`Unexpected ${url}`);
  };

  const harness = createComponentHarness(
    '../lib/context/ScreenTimeContext.tsx',
    'ScreenTimeProvider',
    {
      '@/lib/context/AuthContext': {
        useOptionalAuth: () => ({
          isAuthenticated: true,
          isKidMode: true,
          token: 'child-jwt',
          activeProfile: { id: 10, name: 'Bé Bông', avatarKey: 'bunny' },
        }),
      },
    },
    { fetch: fetchMock, window: fakeWindow, document: { visibilityState: 'visible', addEventListener: () => {}, removeEventListener: () => {} } }
  );

  const readProvider = () => {
    const tree = harness.render({ children: null });
    capturedCtx = tree.props.value;
    return capturedCtx;
  };

  readProvider();
  await new Promise((resolve) => setTimeout(resolve, 10));
  let ctx = readProvider();
  assert.equal(ctx.isLimited, true);
  assert.equal(ctx.remainingSeconds, 3600);

  for (const fn of listeners.get('storage') || []) {
    fn({ key: 'dippie:screen-time-updated' });
  }
  await new Promise((resolve) => setTimeout(resolve, 10));
  ctx = readProvider();
  assert.equal(ctx.remainingSeconds, 7200);

  await ctx.reportWatchHeartbeat(15);
  ctx = readProvider();
  assert.equal(ctx.isLimited, false);
  assert.equal(ctx.isUnlimited, true);
  assert.equal(ctx.isLocked, false);
  assert.equal(ctx.remainingSeconds, null);
  harness.unmount();
});
