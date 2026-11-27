import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from '../node_modules/typescript/lib/typescript.js';
import { createInternalResolver, runWithSharedModules } from './internal_loader.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
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
    if (name === 'react-router') {
      return {
        Link: (props) => ({ type: 'a', props: { href: props.to, ...props } }),
        useNavigate: () => () => {},
      };
    }
    if (name in mocks) return mocks[name];
    return {};
  });

  const wrappedRequire = (name) => {
    if (name === 'react') return react;
    if (name === 'lucide-react') return createIconProxy();
    if (name === 'react-router') {
      return {
        Link: (props) => ({ type: 'a', props: { href: props.to, ...props } }),
        useNavigate: () => () => {},
      };
    }
    if (name in mocks) return mocks[name];
    return resolver(name);
  };
  wrappedRequire.handlesSharedModules = true;

  const mod = { exports: {} };
  runWithSharedModules(
    transpileFile(relativePath),
    {
      ...baseContext,
      module: mod,
      exports: mod.exports,
      require: wrappedRequire,
    },
    { filename: relativePath }
  );

  const Component = exportName === 'default' ? mod.exports.default : mod.exports[exportName];

  const flushEffects = () => {
    const fns = pendingEffects.splice(0);
    for (const fn of fns) fn();
  };

  return {
    exports: mod.exports,
    render(props = {}) {
      cursor = 0;
      const out = Component(props);
      flushEffects();
      return out;
    },
    unmount() {
      for (const slot of slots) slot?.cleanup?.();
      slots = [];
    },
  };
}

test('Pre-reader quiz speaks math symbols in Vietnamese, reads all 4 options, provides per-option speaker buttons, distinct shapes, and selection before submit', () => {
  const spoken = [];
  function FakeUtterance(text) {
    this.text = text;
    this.lang = '';
    this.rate = 1;
  }
  const fakeWindow = {
    speechSynthesis: {
      cancel() {},
      speak(u) {
        spoken.push({ text: u.text, lang: u.lang });
      },
    },
    SpeechSynthesisUtterance: FakeUtterance,
  };

  const harness = createComponentHarness(
    '../features/quiz/components/QuizQuestionCard.tsx',
    'QuizQuestionCard',
    {},
    { window: fakeWindow, SpeechSynthesisUtterance: FakeUtterance }
  );

  const submitted = [];
  const options = [
    { key: 'A', text: '10' },
    { key: 'B', text: '12' },
    { key: 'C', text: '14' },
    { key: 'D', text: '15' },
  ];

  let tree = harness.render({
    question: '7 + 5 = ?',
    options,
    subject: 'TOAN',
    subjectLabel: 'Toán',
    gradeLabel: 'Mẫu giáo',
    submitting: false,
    onSelectOption: (opt) => submitted.push(opt),
  });

  const optNodes = ['A', 'B', 'C', 'D'].map(
    (k) => collectNodes(tree, (n) => n.props?.['data-testid'] === `quiz-option-${k}`)[0]
  );
  assert.deepEqual(
    optNodes.map((n) => n.props['data-option-shape']),
    ['triangle', 'diamond', 'circle', 'square']
  );
  assert.equal(String(optNodes[2].props.className).includes('#d1fae5'), false);
  assert.ok(String(optNodes[2].props.className).includes('#ede9fe'));

  collectNodes(tree, (n) => n.props?.['data-testid'] === 'quiz-read-aloud-button')[0].props.onClick();
  assert.equal(spoken.length, 1);
  assert.equal(spoken[0].lang, 'vi-VN');
  assert.match(spoken[0].text, /7 cộng 5 bằng bao nhiêu\?/);
  assert.match(spoken[0].text, /Đáp án A: 10.*Đáp án B: 12.*Đáp án C: 14.*Đáp án D: 15/);

  const speakBtnB = collectNodes(tree, (n) => n.props?.['data-testid'] === 'quiz-speak-option-B')[0];
  assert.ok(speakBtnB);
  assert.ok(String(speakBtnB.props.className).includes('min-h-[56px]'));
  assert.ok(String(speakBtnB.props.className).includes('min-w-[44px]'));
  speakBtnB.props.onClick();
  assert.equal(spoken[1].text, 'Đáp án B: 12');

  const confirmBefore = collectNodes(tree, (n) => n.props?.['data-testid'] === 'quiz-confirm-answer-button')[0];
  assert.equal(confirmBefore.props.disabled, true);

  optNodes[1].props.onClick();
  assert.deepEqual(submitted, []);

  tree = harness.render({
    question: '7 + 5 = ?',
    options,
    subject: 'TOAN',
    subjectLabel: 'Toán',
    gradeLabel: 'Mẫu giáo',
    submitting: false,
    onSelectOption: (opt) => submitted.push(opt),
  });
  assert.equal(collectNodes(tree, (n) => n.props?.['data-testid'] === 'quiz-selected-pill').length, 1);
  const confirmAfter = collectNodes(tree, (n) => n.props?.['data-testid'] === 'quiz-confirm-answer-button')[0];
  assert.equal(confirmAfter.props.disabled, false);
  assert.match(collectText(confirmAfter), /Trả lời đáp án B: 12/);
  confirmAfter.props.onClick();
  assert.deepEqual(submitted, ['B']);

  const revealTree = harness.render({
    question: '7 + 5 = ?',
    options,
    subject: 'TOAN',
    submitting: false,
    wrongReveal: {
      chosenOption: 'A',
      correctOption: 'B',
      correctText: '12',
      explanation: null,
      message: 'Gần đúng rồi! Đáp án là B: 12 nè',
    },
    onSelectOption: () => {},
  });
  assert.equal(collectNodes(revealTree, (n) => n.props?.['data-testid'] === 'quiz-option-correct-icon-B').length, 1);
  assert.equal(collectNodes(revealTree, (n) => n.props?.['data-testid'] === 'quiz-option-wrong-icon-A').length, 1);
  harness.unmount();
});

test('Loot chest HTTP 500 preserves selected answer, pauses timer, allows retry to credit bonus once, and supports neutral dismiss', async () => {
  let answerAttempts = 0;
  let updatedScreenTime = null;

  const fetchMock = async (url, init = {}) => {
    const method = init.method || 'GET';
    if (url.startsWith('/api/users/play-features/status')) {
      return jsonResponse({
        success: true,
        data: {
          active: true,
          settings: {
            watchPetEnabled: false,
            watchPetFaintMinutes: 20,
            watchPetRestMinutes: 5,
            lootDropEnabled: true,
            recapToNextEnabled: false,
          },
          watchPet: { enabled: false, stage: 'HAPPY', continuousWatchSeconds: 0, restMinutes: 5, fainted: false, resting: false, restRemainingSeconds: 0 },
          loot: { enabled: true, ready: true, combo: 1 },
          recap: { enabled: false, pendingGates: [], unlockedEpisodeIds: [] },
        },
      });
    }
    if (url === '/api/users/play-features/loot/spawn' && method === 'POST') {
      return jsonResponse({
        success: true,
        data: { dropToken: 'drop-1', visibleSeconds: 15, expiresAt: '2026-11-26T12:00:00Z', combo: 1 },
      });
    }
    if (url === '/api/users/play-features/loot/open' && method === 'POST') {
      return jsonResponse({
        success: true,
        data: {
          token: 'loot-q-1',
          question: '2 + 3 = ?',
          options: [
            { key: 'A', text: '4' },
            { key: 'B', text: '5' },
            { key: 'C', text: '6' },
            { key: 'D', text: '7' },
          ],
          subject: 'TOAN',
          subjectLabel: 'Toán',
          grade: 'MAU_GIAO',
          gradeLabel: 'Mẫu giáo',
          setName: 'Toán học',
          timerSeconds: 25,
          expiresAt: '2026-11-26T12:00:30Z',
          combo: 1,
          baseRewardMinutes: 3,
          potentialComboBonusMinutes: 0,
        },
      });
    }
    if (url === '/api/users/play-features/loot/answer' && method === 'POST') {
      answerAttempts += 1;
      if (answerAttempts === 1) {
        return jsonResponse({ success: false, message: 'Internal Server Error' }, 500);
      }
      return jsonResponse({
        success: true,
        message: 'Chúc mừng! Bé mở rương nhận thêm 3 phút xem phim.',
        data: {
          correct: true,
          correctOption: 'B',
          correctText: '5',
          explanation: null,
          earnedSeconds: 180,
          earnedMinutes: 3,
          combo: 2,
          comboBonusMinutes: 0,
          bonusCapReached: false,
          status: {
            limited: true,
            unlimited: false,
            profileId: 9,
            day: '2026-11-26',
            dailyMinutes: 30,
            rewardMinutes: 5,
            maxBonusMinutes: 30,
            dailySeconds: 1800,
            watchedSeconds: 600,
            rewardSeconds: 180,
            grantSeconds: 0,
            bountySeconds: 0,
            remainingSeconds: 1380,
            locked: false,
            bonusCapSeconds: 1800,
            bonusRemainingSeconds: 1620,
            bonusCapReached: false,
          },
        },
      });
    }
    return jsonResponse({ success: true, data: {} });
  };

  let latestHook = null;
  function HookProbe(props) {
    const mod = createHookUsePlayFeatures(props);
    latestHook = mod;
    return null;
  }

  let createHookUsePlayFeatures = null;
  const harness = createComponentHarness(
    '../features/profiles/hooks/usePlayFeatures.ts',
    'usePlayFeatures',
    {},
    { fetch: fetchMock }
  );
  createHookUsePlayFeatures = harness.exports.usePlayFeatures;
  void HookProbe;

  const hookProps = {
    enabled: true,
    token: 'kid-jwt',
    seriesId: 'ser_1',
    isPlaying: true,
    isScreenTimeLocked: false,
    onScreenTimeUpdated: (st) => {
      updatedScreenTime = st;
    },
  };

  latestHook = harness.render(hookProps);
  await new Promise((r) => setTimeout(r, 20));
  latestHook = harness.render(hookProps);
  await new Promise((r) => setTimeout(r, 20));
  latestHook = harness.render(hookProps);

  assert.equal(latestHook.activeDrop?.dropToken, 'drop-1');
  await latestHook.handleOpenLootChest();
  latestHook = harness.render(hookProps);
  assert.equal(latestHook.activeLootQuiz?.timerSeconds, 25);

  const firstAttempt = await latestHook.handleSubmitLootAnswer('B');
  assert.equal(firstAttempt, null);
  latestHook = harness.render(hookProps);
  assert.ok(latestHook.activeLootQuiz);
  assert.equal(latestHook.lootSelectedOption, 'B');
  assert.match(latestHook.lootRetryError, /Đáp án của bé vẫn được giữ nguyên/);

  const overlayHarness = createComponentHarness(
    '../features/profiles/components/LootDropOverlay.tsx',
    'LootDropOverlay',
    {},
    {},
    new Set(['QuizQuestionCard'])
  );
  let dismissed = false;
  let retriedWith = null;
  const overlayTree = overlayHarness.render({
    activeDrop: null,
    dropSecondsLeft: 0,
    activeQuiz: latestHook.activeLootQuiz,
    quizSecondsLeft: latestHook.lootQuizSecondsLeft,
    selectedOption: latestHook.lootSelectedOption,
    retryError: latestHook.lootRetryError,
    feedback: null,
    opening: false,
    submitting: false,
    onOpenChest: async () => {},
    onSubmitAnswer: async (opt) => {
      retriedWith = opt;
      return latestHook.handleSubmitLootAnswer(opt);
    },
    onDismissQuiz: () => {
      dismissed = true;
    },
  });

  const retryBtn = collectNodes(overlayTree, (n) => n.props?.['data-testid'] === 'loot-retry-submit-button')[0];
  assert.ok(retryBtn);
  assert.match(collectText(retryBtn), /Gửi lại đáp án B/);
  retryBtn.props.onClick();
  await new Promise((r) => setTimeout(r, 20));
  assert.equal(retriedWith, 'B');
  assert.equal(updatedScreenTime?.remainingSeconds, 1380);

  latestHook = harness.render(hookProps);
  assert.equal(latestHook.activeLootQuiz, null);
  assert.equal(latestHook.lootRetryError, null);
  assert.match(latestHook.lootFeedback?.text || '', /nhận thêm 3 phút/);

  collectNodes(overlayTree, (n) => n.props?.['data-testid'] === 'loot-retry-dismiss-button')[0].props.onClick();
  assert.equal(dismissed, true);

  overlayHarness.unmount();
  harness.unmount();
});

test('ScreenTimeLockOverlay prioritizes Xong hôm nay, shows saved progress and next available time, and grants extra minutes via Parent PIN', async () => {
  const requests = [];
  let updated = null;
  let finished = 0;

  const fetchMock = async (url, init = {}) => {
    requests.push({ url, method: init.method, body: init.body ? JSON.parse(init.body) : null });
    if (url === '/api/users/screen-time/parent-grant') {
      return jsonResponse({
        success: true,
        message: 'Ba mẹ đã cho thêm 15 phút xem phim hôm nay.',
        data: {
          limited: true,
          unlimited: false,
          profileId: 5,
          day: '2026-11-26',
          dailyMinutes: 30,
          rewardMinutes: 5,
          maxBonusMinutes: 30,
          dailySeconds: 1800,
          watchedSeconds: 1800,
          rewardSeconds: 0,
          grantSeconds: 900,
          bountySeconds: 0,
          remainingSeconds: 900,
          locked: false,
          bonusCapSeconds: 1800,
          bonusRemainingSeconds: 1800,
          bonusCapReached: false,
        },
      });
    }
    return jsonResponse({ success: false, message: 'Unexpected' }, 400);
  };

  const harness = createComponentHarness(
    '../features/profiles/components/ScreenTimeLockOverlay.tsx',
    'ScreenTimeLockOverlay',
    {},
    { fetch: fetchMock }
  );

  const props = {
    status: {
      limited: true,
      unlimited: false,
      profileId: 5,
      day: '2026-11-26',
      dailyMinutes: 30,
      quizGrade: 'MAU_GIAO',
      quizGradeLabel: 'Mẫu giáo',
      rewardMinutes: 5,
      maxBonusMinutes: 30,
      dailySeconds: 1800,
      watchedSeconds: 1800,
      rewardSeconds: 0,
      grantSeconds: 0,
      bountySeconds: 0,
      remainingSeconds: 0,
      locked: true,
      bonusCapSeconds: 1800,
      bonusRemainingSeconds: 1800,
      bonusCapReached: false,
    },
    token: 'child-jwt',
    savedProgressLabel: 'Tập 1 · 03:20',
    onStatusUpdated: (next) => {
      updated = next;
    },
    onContinueWatching: () => {},
    onFinishSession: () => {
      finished += 1;
    },
  };

  let tree = harness.render(props);
  const finishBtn = collectNodes(tree, (n) => n.props?.['data-testid'] === 'time-up-finish-today-button')[0];
  const quizBtn = collectNodes(tree, (n) => n.props?.['data-testid'] === 'open-quiz-challenge-button')[0];
  assert.ok(String(finishBtn.props.className).includes('btn-primary'));
  assert.ok(String(quizBtn.props.className).includes('btn-secondary'));
  assert.match(collectText(collectNodes(tree, (n) => n.props?.['data-testid'] === 'time-up-saved-progress')[0]), /Tập 1 · 03:20/);
  assert.match(collectText(collectNodes(tree, (n) => n.props?.['data-testid'] === 'time-up-next-available')[0]), /Ngày mai/);

  finishBtn.props.onClick({ preventDefault() {} });
  assert.equal(finished, 1);

  collectNodes(tree, (n) => n.props?.['data-testid'] === 'time-up-parent-grant-button')[0].props.onClick();
  tree = harness.render(props);
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'time-up-parent-pin-input')[0].props.onChange({
    target: { value: '2468' },
  });
  tree = harness.render(props);
  await collectNodes(tree, (n) => n.props?.['data-testid'] === 'time-up-parent-grant-form')[0].props.onSubmit({
    preventDefault() {},
  });
  tree = harness.render(props);

  assert.equal(requests.length, 1);
  assert.deepEqual(requests[0].body, { pin: '2468', minutes: 15 });
  assert.equal(updated?.remainingSeconds, 900);
  assert.equal(updated?.locked, false);
  harness.unmount();
});

test('EpisodePlayerPanel pauses and resumes video on loot quiz and blocking overlays, unifies time + eye-break bar, and scrolls into view', () => {
  let pauseCalls = 0;
  let playCalls = 0;
  let scrollCalls = 0;

  const fakeVideo = {
    paused: false,
    ended: false,
    pause() {
      pauseCalls += 1;
      this.paused = true;
    },
    play() {
      playCalls += 1;
      this.paused = false;
      return Promise.resolve();
    },
  };

  const harness = createComponentHarness(
    '../features/series/components/EpisodePlayerPanel.tsx',
    'EpisodePlayerPanel',
    {
      '@/lib/context/AuthContext': {
        useOptionalAuth: () => ({ isAuthenticated: true, isKidMode: true, token: 'kid-jwt' }),
      },
      '@/lib/context/ScreenTimeContext': {
        useOptionalScreenTime: () => ({
          isLimited: true,
          isLocked: false,
          remainingSeconds: 600,
          screenTime: {
            limited: true,
            unlimited: false,
            profileId: 3,
            day: '2026-11-26',
            dailyMinutes: 30,
            quizGrade: 'LOP_1',
            quizGradeLabel: 'Lớp 1',
            rewardMinutes: 5,
            maxBonusMinutes: 30,
            dailySeconds: 1800,
            watchedSeconds: 1200,
            rewardSeconds: 0,
            grantSeconds: 0,
            bountySeconds: 0,
            remainingSeconds: 600,
            locked: false,
            bonusCapSeconds: 1800,
            bonusRemainingSeconds: 1800,
            bonusCapReached: false,
          },
          applyScreenTimeStatus() {},
        }),
      },
      '@/lib/labels': { cleanMediaTitle: (t) => t || '', formatDurationMs: () => '01:00' },
      '@/lib/services/playbackSource': { getUnavailableReasonMessage: () => '' },
    },
    {
      window: {
        innerHeight: 800,
        matchMedia: () => ({ matches: false }),
        addEventListener() {},
        removeEventListener() {},
      },
      document: {
        documentElement: { dataset: {} },
        querySelector: () => null,
        contains: () => true,
      },
    },
    new Set(['ScreenTimeChip'])
  );

  const baseProps = {
    seriesTitle: 'Ấu trùng tinh nghịch',
    selectedEpisode: { id: 'ep1', seriesId: 'ser1', title: 'Tập 1', number: 1 },
    episodeDetail: null,
    playbackSource: { kind: 'file', asset: { id: 'ast1' } },
    isLoadingEpisode: false,
    isResolvingStream: false,
    episodeError: null,
    playbackWarning: null,
    onDismissWarning: () => {},
    isUnmountedDriveFallback: false,
    onRetryOriginalFile: () => {},
    onRetryEpisode: () => {},
    streamUrl: '/api/media/stream/ast1',
    fileRetryCount: 0,
    videoRef: { current: fakeVideo },
    youtubeSeekRequest: null,
    shouldAutoPlayYoutube: false,
    youtubeHandlers: { onProgress() {}, onEnded() {}, onPlayingChange() {}, onTimeTick() {} },
    videoHandlers: {
      onLoadedMetadata() {},
      onLoadedData() {},
      onCanPlay() {},
      onError() {},
      onTimeUpdate() {},
      onPlay() {},
      onPlaying() {},
      onPause() {},
      onSeeking() {},
      onEnded() {},
    },
    offerResume: false,
    onResumePlayback: () => {},
    autoNext: false,
    onAutoNextToggle: () => {},
    nextCountdown: null,
    nextEpisodeLabel: 'Tập 2',
    hasNextEpisode: true,
    onCancelNextCountdown: () => {},
    progressError: null,
    playerScrollNonce: 0,
    kidPlayOverlay: {
      watchPet: {
        enabled: true,
        stage: 'HAPPY',
        continuousWatchSeconds: 120,
        sweatyThresholdSeconds: 900,
        dizzyThresholdSeconds: 1080,
        faintThresholdSeconds: 1200,
        restMinutes: 5,
        fainted: false,
        resting: false,
        restLockUntil: null,
        restRemainingSeconds: 0,
      },
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
  };

  let tree = harness.render(baseProps);
  assert.equal(collectNodes(tree, (n) => n.props?.['data-testid'] === 'kid-playback-status-bar').length, 1);
  assert.equal(collectNodes(tree, (n) => n.props?.['data-testid'] === 'player-screen-time-progress').length, 1);
  assert.equal(collectNodes(tree, (n) => n.props?.['data-testid'] === 'player-eye-break-indicator').length, 1);
  assert.equal(collectNodes(tree, (n) => n.props?.['data-testid'] === 'screen-time-threshold-live').length, 1);

  const playerFrame = collectNodes(tree, (n) => n.props?.['data-testid'] === 'series-player-frame')[0];
  playerFrame.props.ref.current = {
    getBoundingClientRect: () => ({ top: -240 }),
    scrollIntoView: () => {
      scrollCalls += 1;
    },
  };

  tree = harness.render({
    ...baseProps,
    playerScrollNonce: 1,
    kidPlayOverlay: {
      ...baseProps.kidPlayOverlay,
      activeLootQuiz: {
        token: 'q1',
        question: '1 + 1 = ?',
        options: [
          { key: 'A', text: '1' },
          { key: 'B', text: '2' },
          { key: 'C', text: '3' },
          { key: 'D', text: '4' },
        ],
        subject: 'TOAN',
        subjectLabel: 'Toán',
        grade: 'MAU_GIAO',
        gradeLabel: 'Mẫu giáo',
        setName: 'Toán',
        timerSeconds: 25,
        expiresAt: '',
        combo: 0,
        baseRewardMinutes: 3,
        potentialComboBonusMinutes: 0,
      },
      lootQuizSecondsLeft: 20,
    },
  });

  const expandedFrame = collectNodes(tree, (n) => n.props?.['data-testid'] === 'series-player-frame')[0];
  assert.ok(String(expandedFrame.props.className).includes('min-h-[30rem]'));
  assert.ok(String(expandedFrame.props.className).includes('aspect-auto'));
  assert.equal(pauseCalls, 1);
  assert.equal(scrollCalls, 1);

  harness.render({
    ...baseProps,
    playerScrollNonce: 1,
  });
  assert.equal(playCalls, 1);

  harness.unmount();
});

test('useBlockingOverlayFocus moves focus in, traps Tab and Shift+Tab, and restores focus to opener on close', () => {
  let keydownHandler = null;
  let activeEl = null;

  const opener = {
    name: 'opener',
    focus() {
      activeEl = this;
    },
  };
  const firstBtn = {
    name: 'first',
    focus() {
      activeEl = this;
    },
  };
  const lastBtn = {
    name: 'last',
    focus() {
      activeEl = this;
    },
  };
  activeEl = opener;

  const fakeContainer = {
    contains: (target) => target === firstBtn || target === lastBtn,
    querySelector: () => firstBtn,
    querySelectorAll: () => [firstBtn, lastBtn],
    focus() {
      activeEl = this;
    },
  };

  const fakeDocument = {
    get activeElement() {
      return activeEl;
    },
    querySelector: () => null,
    contains: () => true,
  };
  const fakeWindow = {
    addEventListener(type, fn) {
      if (type === 'keydown') keydownHandler = fn;
    },
    removeEventListener(type, fn) {
      if (type === 'keydown' && keydownHandler === fn) keydownHandler = null;
    },
  };

  function FocusProbe({ active }) {
    const ref = useRefHook(fakeContainer);
    useBlockingFocusHook(ref, active);
    return null;
  }

  let useRefHook = null;
  let useBlockingFocusHook = null;
  const harness = createComponentHarness(
    '../features/profiles/hooks/useBlockingOverlayFocus.ts',
    'useBlockingOverlayFocus',
    {},
    { document: fakeDocument, window: fakeWindow }
  );
  useBlockingFocusHook = harness.exports.useBlockingOverlayFocus;
  void FocusProbe;

  const probeHarness = createComponentHarness(
    '../features/profiles/components/RecapQuizOverlay.tsx',
    'RecapQuizOverlay',
    {
      '@/lib/services/playFeaturesService': {
        fetchRecapQuiz: async () => ({
          token: 'r1',
          episodeId: 'ep1',
          episodeTitle: 'Tập 1',
          targetEpisodeId: 'ep2',
          quizType: 'TITLE_CHOICE',
          prompt: 'Tập vừa rồi tên là gì?',
          items: [
            { id: 'c1', text: 'Tập một' },
            { id: 'c2', text: 'Tập hai' },
          ],
          expiresAt: '',
          expiresInSeconds: 300,
        }),
        submitRecapQuizAnswer: async () => ({ correct: true, episodeId: 'ep1', targetEpisodeId: 'ep2', unlocked: true, message: 'OK' }),
      },
    },
    { document: fakeDocument, window: fakeWindow }
  );
  void useRefHook;

  const containerRef = { current: fakeContainer };
  let cleanupFn = null;
  const singleEffectHarness = (() => {
    const mod = { exports: {} };
    const reactMock = {
      useEffect(fn) {
        cleanupFn = fn();
      },
    };
    runWithSharedModules(
      transpileFile('../features/profiles/hooks/useBlockingOverlayFocus.ts'),
      {
        React: reactMock,
        document: fakeDocument,
        window: fakeWindow,
        setTimeout: () => 1,
        clearTimeout: () => {},
        module: mod,
        exports: mod.exports,
        require: (name) => (name === 'react' ? reactMock : {}),
      }
    );
    return mod.exports.useBlockingOverlayFocus;
  })();

  singleEffectHarness(containerRef, true);
  assert.equal(activeEl, firstBtn);
  assert.equal(typeof keydownHandler, 'function');

  activeEl = lastBtn;
  let prevented = false;
  keydownHandler({
    key: 'Tab',
    shiftKey: false,
    preventDefault() {
      prevented = true;
    },
  });
  assert.equal(prevented, true);
  assert.equal(activeEl, firstBtn);

  prevented = false;
  keydownHandler({
    key: 'Tab',
    shiftKey: true,
    preventDefault() {
      prevented = true;
    },
  });
  assert.equal(prevented, true);
  assert.equal(activeEl, lastBtn);

  cleanupFn?.();
  assert.equal(activeEl, opener);

  probeHarness.unmount();
  harness.unmount();
});

test('KidHomeExperience places avatar, giant resume tile, and series covers above tasks/hero, and hides WeekendVaultBanner when standalone films = 0', async () => {
  const harness = createComponentHarness(
    '../features/profiles/components/KidHomeExperience.tsx',
    'KidHomeExperience',
    {
      '@/lib/context/AuthContext': {
        useOptionalAuth: () => ({
          isAuthenticated: true,
          isKidMode: true,
          token: 'kid-jwt',
          activeProfile: { id: 7, name: 'Bé Mây', avatarKey: 'bunny' },
        }),
      },
      '@/lib/services/seriesCardService': {
        listSeriesCards: async () => [
          { id: 'ser_larva', title: 'Ấu trùng tinh nghịch', episodeCount: 10, seasonCount: 1, genres: ['comedy'], posterUrl: '', backdropUrl: '' },
          { id: 'ser_doraemon', title: 'Doraemon', episodeCount: 12, seasonCount: 1, genres: ['family'], posterUrl: '', backdropUrl: '' },
        ],
      },
      '@/lib/hooks/useWatchProgress': {
        useWatchProgress: () => ({
          records: [
            {
              episodeId: 'ep_larva_1',
              assetId: 'ast_1',
              seriesId: 'ser_larva',
              seriesTitle: 'Ấu trùng tinh nghịch',
              episodeTitle: 'Kem ốc quế',
              episodeNumber: 1,
              positionMs: 45_000,
              durationMs: 180_000,
              completed: false,
              updatedAt: '2026-11-26T10:00:00Z',
              version: 1,
            },
          ],
        }),
      },
      '@/lib/services/vipService': {
        fetchKidVipStatus: async () => ({ vipSeriesIds: [], unlockedEpisodeIds: [], vipQuizGradeLabel: 'Lớp 2' }),
      },
      '@/lib/services/weekendVaultService': {
        fetchWeekendVaultStatus: async () => ({
          enabled: true,
          profileId: 7,
          today: '2026-11-26',
          weekStart: '2026-11-23',
          weekend: false,
          progressPercent: 40,
          unlocked: false,
          manualUnlocked: false,
          locked: true,
          days: [],
        }),
      },
      '@/lib/services/bountyService': {
        fetchKidBountyBoard: async () => ({ tasks: [] }),
      },
      '@/features/series/components/SeriesCard': {
        SeriesCard: ({ card }) => ({ type: 'div', props: { 'data-testid': 'series-cover-card', children: card.title } }),
      },
      '@/features/series/components/ArtworkImage': {
        ArtworkImage: ({ children, title }) => ({ type: 'div', props: { 'data-testid': 'resume-artwork', children: [title, children] } }),
      },
      '@/features/series/seriesBrowseUtils': {
        computeProgressPercent: () => 25,
        formatEpisodeTag: (n) => (n ? `Tập ${n}` : ''),
      },
      '@/lib/services/watchProgressService': {
        isUsableResumeProgress: () => true,
        buildWatchProgressHref: (ep) => `/series/${ep.seriesId}?episode=${ep.episodeId}`,
      },
      '@/lib/services/movieProgress': {
        isUsableMovieProgress: () => false,
        buildMovieProgressHref: () => '/movies/x',
      },
      '@/lib/labels': { cleanMediaTitle: (s) => s || '' },
    },
    {
      fetch: async (url) => {
        if (String(url).includes('/users/weekend-vault')) {
          return jsonResponse({
            success: true,
            data: {
              enabled: true,
              profileId: 7,
              today: '2026-11-26',
              weekStart: '2026-11-23',
              weekend: false,
              progressPercent: 40,
              unlocked: false,
              manualUnlocked: false,
              locked: true,
              days: [],
            },
          });
        }
        if (String(url).includes('/users/bounties')) {
          return jsonResponse({ success: true, data: [] });
        }
        if (String(url).includes('/users/vip')) {
          return jsonResponse({
            success: true,
            data: { vipSeriesIds: [], unlockedEpisodeIds: [], vipQuizGradeLabel: 'Lớp 2' },
          });
        }
        return jsonResponse({ success: true, data: {} });
      },
    },
    new Set(['KidModeDashboard', 'ChildAvatarBadge', 'SeriesCard'])
  );

  let tree = harness.render({ films: [], standaloneFilmCount: 0 });
  await new Promise((r) => setTimeout(r, 15));
  tree = harness.render({ films: [], standaloneFilmCount: 0 });

  assert.equal(collectNodes(tree, (n) => n.props?.['data-testid'] === 'kid-home-avatar').length, 1);
  assert.match(collectText(collectNodes(tree, (n) => n.props?.['data-testid'] === 'kid-home-greeting')[0]), /Chào Bé Mây!/);
  assert.equal(collectNodes(tree, (n) => n.props?.['data-testid'] === 'kid-home-resume-tile').length, 1);
  assert.equal(collectNodes(tree, (n) => n.props?.['data-testid'] === 'series-cover-card').length, 2);

  const orderedTestIds = collectNodes(
    tree,
    (n) =>
      n.props?.['data-testid'] === 'kid-home-resume-tile' ||
      n.props?.['data-testid'] === 'kid-home-series-section' ||
      n.props?.['data-testid'] === 'kid-mode-dashboard' ||
      n.props?.['data-testid'] === 'kid-home-explore-card'
  ).map((n) => n.props['data-testid']);
  assert.deepEqual(orderedTestIds, [
    'kid-home-resume-tile',
    'kid-mode-dashboard',
    'kid-home-series-section',
    'kid-home-explore-card',
  ]);

  assert.equal(collectNodes(tree, (n) => n.type?.name === 'WeekendVaultBanner').length, 0);

  const withFilmTree = harness.render({ films: [], standaloneFilmCount: 2 });
  assert.equal(collectNodes(withFilmTree, (n) => n.type?.name === 'WeekendVaultBanner').length, 1);

  harness.unmount();
});

test('RecapQuizOverlay reads prompt and scene choices aloud, guides step by step, and offers Xong hôm nay session exit; SeriesDetailPage orders player above metadata on mobile', async () => {
  const spoken = [];
  function FakeUtterance(text) {
    this.text = text;
    this.lang = '';
  }
  const fakeWindow = {
    speechSynthesis: {
      cancel() {},
      speak(u) {
        spoken.push({ text: u.text, lang: u.lang });
      },
    },
    SpeechSynthesisUtterance: FakeUtterance,
    addEventListener() {},
    removeEventListener() {},
  };

  let finishedSession = 0;
  const fetchMock = async (url) => {
    if (String(url).includes('/users/play-features/recap/quiz')) {
      return jsonResponse({
        success: true,
        data: {
          token: 'recap-tok',
          episodeId: 'ep1',
          episodeTitle: 'Tập 1',
          targetEpisodeId: 'ep2',
          quizType: 'ORDER_SCENES',
          prompt: 'Xếp các cảnh theo thứ tự nhé',
          items: [
            { id: 's1', text: 'Mèo con thức dậy' },
            { id: 's2', text: 'Cả nhóm đi thả diều' },
          ],
          expiresAt: '2026-11-26T12:00:00Z',
          expiresInSeconds: 600,
        },
      });
    }
    return jsonResponse({ success: true, data: {} });
  };

  const harness = createComponentHarness(
    '../features/profiles/components/RecapQuizOverlay.tsx',
    'RecapQuizOverlay',
    {},
    { fetch: fetchMock, window: fakeWindow, SpeechSynthesisUtterance: FakeUtterance }
  );

  const props = {
    token: 'kid-jwt',
    episodeId: 'ep1',
    nextEpisodeLabel: 'Tập 2',
    onUnlocked: () => {},
    onRewatchEpisode: () => {},
    onFinishSession: () => {
      finishedSession += 1;
    },
  };

  harness.render(props);
  await new Promise((r) => setTimeout(r, 15));
  let tree = harness.render(props);

  assert.match(collectText(collectNodes(tree, (n) => n.props?.['data-testid'] === 'recap-step-guide')[0]), /Bước 1/);
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'recap-read-aloud-button')[0].props.onClick();
  assert.equal(spoken[0].lang, 'vi-VN');
  assert.match(spoken[0].text, /Mèo con thức dậy/);
  assert.match(spoken[0].text, /Cả nhóm đi thả diều/);

  collectNodes(tree, (n) => n.props?.['data-testid'] === 'recap-speak-item-s2')[0].props.onClick();
  assert.equal(spoken[1].text, 'Cả nhóm đi thả diều');

  collectNodes(tree, (n) => n.props?.['data-testid'] === 'recap-item-s1')[0].props.onClick();
  tree = harness.render(props);
  assert.match(collectText(collectNodes(tree, (n) => n.props?.['data-testid'] === 'recap-step-guide')[0]), /Bước 2/);

  collectNodes(tree, (n) => n.props?.['data-testid'] === 'recap-finish-today-button')[0].props.onClick({
    preventDefault() {},
  });
  assert.equal(finishedSession, 1);
  harness.unmount();

  const seriesPageSource = fs.readFileSync(path.join(root, 'app/series/[id]/page.tsx'), 'utf8');
  assert.ok(seriesPageSource.includes('order-2 md:order-1'));
  assert.ok(seriesPageSource.includes('order-1 grid grid-cols-1 gap-8 md:order-2'));
});

test('Round 2 Kid UX fixes: 6-digit PIN on ScreenTimeLockOverlay, normal-flow overlays, WatchPet without daily grant trap, reactive TTS fallback notice, bilingual pre-reader English, and scaled loot timer', async () => {
  const grantedCalls = [];
  const lockHarness = createComponentHarness(
    '../features/profiles/components/ScreenTimeLockOverlay.tsx',
    'ScreenTimeLockOverlay',
    {
      '@/lib/services/screenTimeService': {
        fetchScreenTimeChallenge: async () => ({}),
        formatRemainingTime: () => '15 phút',
        grantExtraMinutesInKidMode: async (_tok, pin, mins) => {
          grantedCalls.push({ pin, mins });
          return {
            message: 'Đã cộng thêm 15 phút.',
            status: {
              limited: true,
              unlimited: false,
              locked: false,
              dailyMinutes: 30,
              dailySeconds: 1800,
              watchedSeconds: 1800,
              rewardSeconds: 0,
              grantSeconds: 900,
              bountySeconds: 0,
              remainingSeconds: 900,
              rewardMinutes: 5,
              maxBonusMinutes: 30,
              bonusRemainingSeconds: 1800,
              bonusCapReached: false,
            },
          };
        },
        submitScreenTimeChallengeAnswer: async () => ({}),
      },
    }
  );

  const lockStatus = {
    limited: true,
    unlimited: false,
    locked: true,
    dailyMinutes: 30,
    dailySeconds: 1800,
    watchedSeconds: 1800,
    rewardSeconds: 0,
    grantSeconds: 0,
    bountySeconds: 0,
    remainingSeconds: 0,
    rewardMinutes: 5,
    maxBonusMinutes: 0,
    bonusRemainingSeconds: 0,
    bonusCapReached: true,
  };
  const lockProps = {
    status: lockStatus,
    token: 'kid-jwt',
    savedProgressLabel: 'Tập 2 · 04:12',
    onStatusUpdated() {},
    onContinueWatching() {},
  };

  let lockTree = lockHarness.render(lockProps);
  const lockRoot = collectNodes(lockTree, (n) => n.props?.['data-testid'] === 'screen-time-lock-overlay')[0];
  assert.ok(String(lockRoot.props.className).includes('relative'));
  assert.equal(String(lockRoot.props.className).includes('absolute inset-0'), false);
  assert.match(collectText(lockTree), /chế độ hết giờ là nghỉ ngơi hôm nay/);

  collectNodes(lockTree, (n) => n.props?.['data-testid'] === 'time-up-parent-grant-button')[0].props.onClick();
  lockTree = lockHarness.render(lockProps);
  collectNodes(lockTree, (n) => n.props?.['data-testid'] === 'time-up-parent-pin-input')[0].props.onChange({
    target: { value: '123456' },
  });
  lockTree = lockHarness.render(lockProps);
  await collectNodes(lockTree, (n) => n.props?.['data-testid'] === 'time-up-parent-grant-form')[0].props.onSubmit({
    preventDefault() {},
  });
  await new Promise((r) => setTimeout(r, 10));
  assert.deepEqual(grantedCalls, [{ pin: '123456', mins: 15 }]);
  lockHarness.unmount();

  const petHarness = createComponentHarness(
    '../features/profiles/components/WatchPetWidget.tsx',
    'WatchPetWidget',
    {}
  );
  const petTree = petHarness.render({
    watchPet: {
      enabled: true,
      stage: 'FAINTED',
      fainted: true,
      resting: false,
      faintMinutes: 20,
      restMinutes: 3,
      continuousWatchSeconds: 1200,
      restRemainingSeconds: 0,
    },
    token: 'kid-jwt',
    restCountdown: 0,
    onStartRest: async () => null,
    onWatchPetUpdated() {},
    hideCorner: true,
  });
  const petOverlay = collectNodes(petTree, (n) => n.props?.['data-testid'] === 'watch-pet-overlay')[0];
  assert.ok(String(petOverlay.props.className).includes('relative'));
  assert.equal(collectNodes(petTree, (n) => n.props?.['data-testid'] === 'watch-pet-parent-grant-button').length, 0);
  petHarness.unmount();

  const englishSpoken = [];
  class FakeUtterance {
    constructor(text) {
      this.text = text;
      this.lang = '';
      this.rate = 1;
    }
  }
  const enOnlyWindow = {
    speechSynthesis: {
      getVoices: () => [{ lang: 'en-US', name: 'English' }],
      cancel() {},
      speak(u) {
        englishSpoken.push(u);
      },
    },
    SpeechSynthesisUtterance: FakeUtterance,
  };

  const cardHarness = createComponentHarness(
    '../features/quiz/components/QuizQuestionCard.tsx',
    'QuizQuestionCard',
    {},
    { window: enOnlyWindow, SpeechSynthesisUtterance: FakeUtterance }
  );

  let readAloudNotified = 0;
  const viCardProps = {
    question: '4 + 3 = ?',
    options: [
      { key: 'A', text: '6' },
      { key: 'B', text: '7' },
      { key: 'C', text: '8' },
      { key: 'D', text: '9' },
    ],
    subject: 'TOAN',
    subjectLabel: 'Toán',
    gradeLabel: 'Mẫu giáo',
    submitting: false,
    onReadAloud: () => {
      readAloudNotified += 1;
    },
    onSelectOption() {},
  };

  let viCardTree = cardHarness.render(viCardProps);
  assert.equal(collectNodes(viCardTree, (n) => n.props?.['data-testid'] === 'quiz-speech-notice').length, 0);
  collectNodes(viCardTree, (n) => n.props?.['data-testid'] === 'quiz-read-aloud-button')[0].props.onClick();
  viCardTree = cardHarness.render(viCardProps);
  assert.equal(readAloudNotified, 1);
  assert.equal(
    collectNodes(viCardTree, (n) => n.props?.['data-testid'] === 'quiz-speech-notice').length,
    1,
    'Missing vi-VN voice must trigger a reactive speech notice re-render'
  );

  const enCardProps = {
    question: "What animal says 'Meow'?",
    options: [
      { key: 'A', text: 'Cat' },
      { key: 'B', text: 'Dog' },
      { key: 'C', text: 'Duck' },
      { key: 'D', text: 'Cow' },
    ],
    subject: 'TIENG_ANH',
    subjectLabel: 'Tiếng Anh',
    gradeLabel: 'Mẫu giáo',
    submitting: false,
    onSelectOption() {},
  };
  const enCardTree = cardHarness.render(enCardProps);
  const stemNode = collectNodes(enCardTree, (n) => n.props?.['data-testid'] === 'quiz-question-text')[0];
  assert.match(collectText(stemNode), /Con vật nào kêu 'meo meo'\? \(What animal says 'Meow'\?\)/);
  collectNodes(enCardTree, (n) => n.props?.['data-testid'] === 'quiz-read-aloud-button')[0].props.onClick();
  assert.equal(englishSpoken.length, 1);
  assert.equal(englishSpoken[0].lang, 'en-US');
  assert.match(englishSpoken[0].text, /^What animal says 'Meow'\?/);
  cardHarness.unmount();

  const playFeaturesHook = createComponentHarness('../features/profiles/hooks/usePlayFeatures.ts', null);
  assert.equal(playFeaturesHook.exports.resolveLootTimerSeconds('MAU_GIAO', 10), 25);
  assert.equal(playFeaturesHook.exports.resolveLootTimerSeconds('LOP_1', 10), 20);
  assert.equal(playFeaturesHook.exports.resolveLootTimerSeconds('LOP_2', 10), 15);

  const recapExports = createComponentHarness('../features/profiles/components/RecapQuizOverlay.tsx', null).exports;
  assert.equal(recapExports.localizeRecapItemText('Violet').text, 'Ấu trùng Tím (Violet)');
  assert.equal(recapExports.localizeRecapItemText('Yellow').text, 'Ấu trùng Vàng (Yellow)');
});
