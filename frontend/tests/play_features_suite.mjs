import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
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
    cleanup() {
      for (const slot of slots) {
        if (typeof slot?.cleanup === 'function') slot.cleanup();
      }
    },
  };
}

function loadServiceModule(relativePath, fetchImpl, extraGlobals = {}) {
  const baseContext = {
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
    fetch: fetchImpl,
    ...extraGlobals,
  };
  const resolver = createInternalResolver(baseContext, () => ({}));
  const module = { exports: {} };
  runWithSharedModules(
    transpileFile(relativePath),
    {
      ...baseContext,
      module,
      exports: module.exports,
      require: resolver,
    },
    { filename: relativePath }
  );
  return module.exports;
}

const SAMPLE_WATCH_PET = {
  enabled: true,
  stage: 'HAPPY',
  continuousWatchSeconds: 300,
  sweatyThresholdSeconds: 900,
  dizzyThresholdSeconds: 1080,
  faintThresholdSeconds: 1200,
  restMinutes: 5,
  fainted: false,
  resting: false,
  restLockUntil: null,
  restRemainingSeconds: 0,
};

test('playFeaturesService validates bounds, formats countdown, and calls all play-features APIs', async () => {
  const calls = [];
  const svc = loadServiceModule('../lib/services/playFeaturesService.ts', async (url, init) => {
    calls.push({ url, method: init?.method || 'GET', body: init?.body ? JSON.parse(init.body) : null });
    if (url.includes('/users/play-features/status')) {
      return jsonResponse({
        success: true,
        message: 'Thành công',
        data: {
          active: true,
          settings: {
            watchPetEnabled: true,
            watchPetFaintMinutes: 20,
            watchPetRestMinutes: 5,
            lootDropEnabled: true,
            recapToNextEnabled: true,
          },
          watchPet: SAMPLE_WATCH_PET,
          loot: { enabled: true, ready: true, combo: 2 },
          recap: {
            enabled: true,
            pendingGates: [{ sourceEpisodeId: 'ep1', targetEpisodeId: 'ep2' }],
            unlockedEpisodeIds: ['ep0'],
          },
        },
      });
    }
    if (url.endsWith('/users/play-features/loot/spawn')) {
      return jsonResponse({
        success: true,
        message: 'Thành công',
        data: { dropToken: 'drop-tok-1', visibleSeconds: 15, expiresAt: '2026-11-26T10:00:18', combo: 2 },
      });
    }
    if (url.endsWith('/users/play-features/recap/quiz?episodeId=ep1')) {
      return jsonResponse({
        success: true,
        message: 'Thành công',
        data: {
          token: 'recap-tok-1',
          episodeId: 'ep1',
          episodeTitle: 'Tập 1',
          targetEpisodeId: 'ep2',
          quizType: 'ORDER_SCENES',
          prompt: 'Xếp lại các cảnh',
          items: [
            { id: 'i1', text: 'Cảnh B' },
            { id: 'i2', text: 'Cảnh A' },
            { id: 'i3', text: 'Cảnh C' },
          ],
          expiresAt: '2026-11-26T10:10:00',
          expiresInSeconds: 600,
        },
      });
    }
    return jsonResponse({ success: true, message: 'OK', data: {} });
  });

  assert.equal(svc.formatCountdownSeconds(0), '00:00');
  assert.equal(svc.formatCountdownSeconds(305), '05:05');
  assert.equal(
    svc.validatePlaySettings({
      watchPetEnabled: true,
      watchPetFaintMinutes: 20,
      watchPetRestMinutes: 5,
      lootDropEnabled: true,
      recapToNextEnabled: true,
    }),
    null
  );
  assert.ok(
    svc.validatePlaySettings({
      watchPetEnabled: true,
      watchPetFaintMinutes: 9,
      watchPetRestMinutes: 5,
      lootDropEnabled: true,
      recapToNextEnabled: true,
    })
  );
  assert.ok(
    svc.validatePlaySettings({
      watchPetEnabled: true,
      watchPetFaintMinutes: 20,
      watchPetRestMinutes: 16,
      lootDropEnabled: true,
      recapToNextEnabled: true,
    })
  );

  const status = await svc.fetchPlayFeaturesStatus('kid-jwt', 'ser_1');
  assert.equal(status.active, true);
  assert.equal(status.loot.ready, true);
  assert.equal(status.recap.pendingGates[0].targetEpisodeId, 'ep2');

  const drop = await svc.spawnLootDrop('kid-jwt');
  assert.equal(drop.dropToken, 'drop-tok-1');
  assert.equal(drop.visibleSeconds, 15);

  const quiz = await svc.fetchRecapQuiz('kid-jwt', 'ep1');
  assert.equal(quiz.quizType, 'ORDER_SCENES');
  assert.equal(quiz.items.length, 3);
  assert.equal(calls.length, 3);
});

test('mediaService distinguishes X-Dippie-Lock-Reason 423 from screen-time 423', async () => {
  const dispatched = [];
  const fakeWindow = {
    localStorage: {
      getItem: (key) => (key === 'dippie_auth_token' ? 'kid-jwt' : null),
    },
    dispatchEvent: (ev) => dispatched.push({ type: ev.type, detail: ev.detail }),
  };

  let nextHeader = 'WATCH_PET_REST';
  const mediaSvc = loadServiceModule(
    '../lib/services/mediaService.ts',
    async () => jsonResponse({ success: false, message: 'Đang nghỉ mắt', data: null }, 423, nextHeader ? { 'X-Dippie-Lock-Reason': nextHeader } : {}),
    { window: fakeWindow }
  );

  const petLocked = await mediaSvc.requestPlaybackGrantResult('asset_1');
  assert.equal(petLocked.status, 423);
  assert.equal(petLocked.lockReason, 'WATCH_PET_REST');
  assert.equal(dispatched[0].type, 'dippie:play-features-locked');
  assert.equal(dispatched[0].detail.reason, 'WATCH_PET_REST');

  nextHeader = '';
  const timeLocked = await mediaSvc.requestPlaybackGrantResult('asset_1');
  assert.equal(timeLocked.status, 423);
  assert.equal(dispatched[1].type, 'dippie:screen-time-locked');
});

test('WatchPetWidget draws SVG without images, supports rest countdown and medicine math heal', async () => {
  let updatedPet = null;
  let restCalled = 0;

  const harness = createComponentHarness(
    '../features/profiles/components/WatchPetWidget.tsx',
    'WatchPetWidget',
    {
      '@/lib/services/playFeaturesService': {
        formatCountdownSeconds: (s) => `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`,
        fetchWatchPetMedicineChallenge: async () => ({
          token: 'med-tok-1',
          question: '3 + 4 = ?',
          mathLevel: 1,
          expiresAt: '2026-11-26T10:02:00',
          expiresInSeconds: 120,
        }),
        submitWatchPetMedicineAnswer: async (_tok, _ctok, answer) => ({
          correct: answer === 7,
          message: answer === 7 ? 'Thú ảo đã khỏe lại rồi!' : 'Chưa đúng rồi!',
          watchPet: answer === 7 ? { ...SAMPLE_WATCH_PET, stage: 'HAPPY', continuousWatchSeconds: 0 } : { ...SAMPLE_WATCH_PET, stage: 'FAINTED', fainted: true },
        }),
      },
    },
    {},
    new Set(['WatchPetArt'])
  );

  const faintedPet = { ...SAMPLE_WATCH_PET, stage: 'FAINTED', fainted: true, continuousWatchSeconds: 1200 };
  let tree = harness.render({
    watchPet: faintedPet,
    token: 'kid-jwt',
    restCountdown: 0,
    onStartRest: async () => {
      restCalled += 1;
      return { ...faintedPet, stage: 'RESTING', resting: true, restRemainingSeconds: 300 };
    },
    onWatchPetUpdated: (next) => {
      updatedPet = next;
    },
  });

  const svgs = collectNodes(tree, (n) => n.type === 'svg');
  const imgs = collectNodes(tree, (n) => n.type === 'img');
  assert.ok(svgs.length >= 2);
  assert.equal(imgs.length, 0);

  const restBtn = collectNodes(tree, (n) => n.props?.['data-testid'] === 'watch-pet-rest-button')[0];
  assert.ok(restBtn);
  assert.match(collectText(restBtn).replace(/\s+/g, ' '), /Cho thú ngủ 5 phút/);
  restBtn.props.onClick();
  await new Promise((r) => setTimeout(r, 0));
  assert.equal(restCalled, 1);

  const medBtn = collectNodes(tree, (n) => n.props?.['data-testid'] === 'watch-pet-medicine-button')[0];
  medBtn.props.onClick();
  await new Promise((r) => setTimeout(r, 0));
  tree = harness.render({
    watchPet: faintedPet,
    token: 'kid-jwt',
    restCountdown: 0,
    onStartRest: async () => null,
    onWatchPetUpdated: (next) => {
      updatedPet = next;
    },
  });
  const qNode = collectNodes(tree, (n) => n.props?.['data-testid'] === 'watch-pet-medicine-question')[0];
  assert.equal(collectText(qNode), '3 + 4 = ?');

  const input = collectNodes(tree, (n) => n.props?.['data-testid'] === 'watch-pet-medicine-input')[0];
  input.props.onChange({ target: { value: '7' } });
  tree = harness.render({
    watchPet: faintedPet,
    token: 'kid-jwt',
    restCountdown: 0,
    onStartRest: async () => null,
    onWatchPetUpdated: (next) => {
      updatedPet = next;
    },
  });
  const form = collectNodes(tree, (n) => n.type === 'form')[0];
  await form.props.onSubmit({ preventDefault() {} });
  assert.equal(updatedPet?.stage, 'HAPPY');

  const restingTree = harness.render({
    watchPet: { ...faintedPet, stage: 'RESTING', resting: true, restRemainingSeconds: 299 },
    token: 'kid-jwt',
    restCountdown: 299,
    onStartRest: async () => null,
    onWatchPetUpdated: () => {},
  });
  const countdownNode = collectNodes(restingTree, (n) => n.props?.['data-testid'] === 'watch-pet-rest-countdown')[0];
  assert.equal(collectText(countdownNode), '04:59');
  harness.cleanup();
});

test('LootDropOverlay shows floating chest, quick 10s math quiz with combo bonus, and submits answer', async () => {
  let opened = 0;
  let submittedAnswer = null;

  const harness = createComponentHarness('../features/profiles/components/LootDropOverlay.tsx', 'LootDropOverlay');

  const chestTree = harness.render({
    activeDrop: { dropToken: 'd1', visibleSeconds: 15, expiresAt: '', combo: 2 },
    dropSecondsLeft: 12,
    activeQuiz: null,
    quizSecondsLeft: 0,
    feedback: null,
    opening: false,
    submitting: false,
    onOpenChest: async () => {
      opened += 1;
    },
    onSubmitAnswer: async () => null,
  });

  const chestBtn = collectNodes(chestTree, (n) => n.props?.['data-testid'] === 'loot-chest-button')[0];
  assert.ok(chestBtn);
  await chestBtn.props.onClick();
  assert.equal(opened, 1);

  const quizProps = {
    activeDrop: null,
    dropSecondsLeft: 0,
    activeQuiz: {
      token: 'a1',
      question: '5 + 6 = ?',
      mathLevel: 2,
      timerSeconds: 10,
      expiresAt: '',
      combo: 2,
      baseRewardMinutes: 3,
      potentialComboBonusMinutes: 1,
    },
    quizSecondsLeft: 8,
    feedback: null,
    opening: false,
    submitting: false,
    onOpenChest: async () => {},
    onSubmitAnswer: async (val) => {
      submittedAnswer = val;
      return null;
    },
  };
  let quizTree = harness.render(quizProps);
  assert.match(collectText(quizTree).replace(/\s+/g, ' '), /\+\s*4 phút/);
  assert.match(collectText(quizTree).replace(/\s+/g, ' '), /Combo hiện tại: 2/);

  const input = collectNodes(quizTree, (n) => n.props?.['data-testid'] === 'loot-quiz-input')[0];
  input.props.onChange({ target: { value: '11' } });
  quizTree = harness.render(quizProps);
  const form = collectNodes(quizTree, (n) => n.type === 'form')[0];
  await form.props.onSubmit({ preventDefault() {} });
  assert.equal(submittedAnswer, 11);
  harness.cleanup();
});

test('RecapQuizOverlay supports tap-to-order scenes and unlocks next episode on server confirmation', async () => {
  let submittedPayload = null;
  let unlockedTarget = null;
  let rewatched = 0;

  const harness = createComponentHarness(
    '../features/profiles/components/RecapQuizOverlay.tsx',
    'RecapQuizOverlay',
    {
      '@/lib/services/playFeaturesService': {
        fetchRecapQuiz: async () => ({
          token: 'recap-1',
          episodeId: 'ep1',
          episodeTitle: 'Tập 1',
          targetEpisodeId: 'ep2',
          quizType: 'ORDER_SCENES',
          prompt: 'Xếp lại các cảnh theo đúng thứ tự trong tập vừa xem nhé!',
          items: [
            { id: 'i1', text: 'Cảnh hai' },
            { id: 'i2', text: 'Cảnh một' },
            { id: 'i3', text: 'Cảnh ba' },
          ],
          expiresAt: '2026-11-26T10:10:00',
          expiresInSeconds: 600,
        }),
        submitRecapQuizAnswer: async (_token, payload) => {
          submittedPayload = payload;
          const ok = payload.orderedIds.join(',') === 'i2,i1,i3';
          return {
            correct: ok,
            episodeId: 'ep1',
            targetEpisodeId: 'ep2',
            unlocked: ok,
            message: ok ? 'Giỏi quá! Bé đã mở khóa tập tiếp theo rồi!' : 'Chưa đúng rồi!',
          };
        },
      },
    }
  );

  const props = {
    token: 'kid-jwt',
    episodeId: 'ep1',
    nextEpisodeLabel: 'Tập 2',
    onUnlocked: (target) => {
      unlockedTarget = target;
    },
    onRewatchEpisode: () => {
      rewatched += 1;
    },
  };

  harness.render(props);
  await new Promise((r) => setTimeout(r, 0));
  let tree = harness.render(props);

  collectNodes(tree, (n) => n.props?.['data-testid'] === 'recap-item-i2')[0].props.onClick();
  tree = harness.render(props);
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'recap-item-i1')[0].props.onClick();
  tree = harness.render(props);
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'recap-item-i3')[0].props.onClick();
  tree = harness.render(props);

  const form = collectNodes(tree, (n) => n.type === 'form')[0];
  await form.props.onSubmit({ preventDefault() {} });
  assert.deepEqual(Array.from(submittedPayload.orderedIds), ['i2', 'i1', 'i3']);

  tree = harness.render(props);
  const continueBtn = collectNodes(tree, (n) => n.props?.['data-testid'] === 'recap-continue-next-button')[0];
  assert.ok(continueBtn);
  continueBtn.props.onClick();
  assert.equal(unlockedTarget, 'ep2');

  const rewatchBtn = collectNodes(tree, (n) => n.props?.['data-testid'] === 'recap-rewatch-button')[0];
  rewatchBtn.props.onClick();
  assert.equal(rewatched, 1);
  harness.cleanup();
});

test('PlayFeaturesSettings validates bounds and saves parent settings with PIN', async () => {
  let savedPayload = null;
  let closed = 0;

  const harness = createComponentHarness(
    '../features/account/components/PlayFeaturesSettings.tsx',
    'PlayFeaturesSettings',
    {
      '@/components/ui/SiteDialog': {
        SiteDialog: ({ children }) => ({ type: 'div', props: { children } }),
      },
      '@/lib/services/playFeaturesService': {
        validatePlaySettings: (input) => {
          if (input.watchPetFaintMinutes < 10 || input.watchPetFaintMinutes > 60) return 'Ngưỡng thú ảo mệt xỉu cần từ 10 đến 60 phút.';
          if (input.watchPetRestMinutes < 3 || input.watchPetRestMinutes > 15) return 'Thời gian cho thú nghỉ mắt cần từ 3 đến 15 phút.';
          return null;
        },
        fetchParentPlaySettings: async () => [
          {
            profileId: 9,
            name: 'Bé Bi',
            avatarKey: 'bear',
            watchPetEnabled: true,
            watchPetFaintMinutes: 20,
            watchPetRestMinutes: 5,
            lootDropEnabled: true,
            recapToNextEnabled: true,
          },
        ],
        updateParentPlaySettings: async (_token, _profileId, payload) => {
          savedPayload = payload;
          return { profileId: 9, name: 'Bé Bi', avatarKey: 'bear', ...payload };
        },
      },
    },
    {},
    new Set(['SiteDialog'])
  );

  const props = {
    token: 'parent-jwt',
    profile: { id: 9, name: 'Bé Bi', avatarKey: 'bear', createdAt: '' },
    onClose: () => {
      closed += 1;
    },
  };

  harness.render(props);
  await new Promise((r) => setTimeout(r, 0));
  let tree = harness.render(props);

  collectNodes(tree, (n) => n.props?.['data-testid'] === 'play-setting-faint-minutes-input')[0].props.onChange({
    target: { value: '5' },
  });
  tree = harness.render(props);
  await collectNodes(tree, (n) => n.type === 'form')[0].props.onSubmit({ preventDefault() {} });
  tree = harness.render(props);
  assert.equal(savedPayload, null);
  assert.match(collectText(collectNodes(tree, (n) => n.props?.['data-testid'] === 'play-settings-error')[0]), /10 đến 60/);

  collectNodes(tree, (n) => n.props?.['data-testid'] === 'play-setting-faint-minutes-input')[0].props.onChange({
    target: { value: '25' },
  });
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'play-setting-rest-minutes-input')[0].props.onChange({
    target: { value: '7' },
  });
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'play-setting-loot-toggle')[0].props.onChange({
    target: { checked: false },
  });
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'play-setting-pin-input')[0].props.onChange({
    target: { value: '1234' },
  });
  tree = harness.render(props);
  await collectNodes(tree, (n) => n.type === 'form')[0].props.onSubmit({ preventDefault() {} });
  assert.deepEqual(JSON.parse(JSON.stringify(savedPayload)), {
    pin: '1234',
    watchPetEnabled: true,
    watchPetFaintMinutes: 25,
    watchPetRestMinutes: 7,
    lootDropEnabled: false,
    recapToNextEnabled: true,
  });
  assert.equal(closed, 1);
  harness.cleanup();
});

test('ChildProfilesSection links PlayFeaturesSettings per child profile and EpisodePlayerPanel hides kid overlays outside kid mode', async () => {
  const sectionHarness = createComponentHarness(
    '../features/account/components/ChildProfilesSection.tsx',
    'ChildProfilesSection',
    {
      'react-router': { useNavigate: () => () => {} },
      '@/lib/context/AuthContext': {
        useAuth: () => ({
          token: 'parent-jwt',
          parentModeEnabled: true,
          childProfiles: [{ id: 15, name: 'Bé Mây', avatarKey: 'bunny', createdAt: '' }],
          refreshProfiles: async () => null,
          selectChildProfile: async () => ({ success: true }),
        }),
      },
      '@/lib/services/screenTimeService': {
        DAILY_MINUTE_PRESETS: [30, 60, 90, 120],
        MATH_LEVEL_OPTIONS: [{ level: 1, shortLabel: '10', label: 'Mức 1' }],
        fetchParentScreenTimeOverview: async () => [],
        formatDailyLimitLabel: () => '60 phút',
        formatRemainingTime: () => '',
        formatUsageDuration: () => '',
      },
    }
  );
  const sectionTree = sectionHarness.render();
  const linkBtn = collectNodes(sectionTree, (n) => n.props?.['data-testid'] === 'play-features-settings-button-15')[0];
  assert.ok(linkBtn);
  assert.match(collectText(linkBtn), /Tính năng xem phim/);
  sectionHarness.cleanup();

  const panelHarness = createComponentHarness(
    '../features/series/components/EpisodePlayerPanel.tsx',
    'EpisodePlayerPanel',
    {
      '@/lib/context/AuthContext': {
        useOptionalAuth: () => ({ isKidMode: false, token: 'main-jwt' }),
      },
      '@/lib/context/ScreenTimeContext': {
        useOptionalScreenTime: () => ({ isLimited: false, isLocked: false, remainingSeconds: null, screenTime: null }),
      },
      '@/lib/labels': {
        cleanMediaTitle: (s) => s || '',
        formatDurationMs: () => '00:00',
      },
      '@/features/series/components/YouTubePlayer': {
        YouTubePlayer: (props) => ({ type: 'YouTubePlayer', props }),
      },
    }
  );
  const panelTree = panelHarness.render({
    seriesTitle: 'Phim',
    selectedEpisode: { id: 'ep1', title: 'Tập 1', number: 1 },
    episodeDetail: null,
    playbackSource: { kind: 'youtube', youtube: { videoId: 'abcdefghijk' }, embedUrl: 'https://www.youtube-nocookie.com/embed/abcdefghijk', watchUrl: 'https://youtube.com', isFallback: false },
    isLoadingEpisode: false,
    isResolvingStream: false,
    episodeError: null,
    playbackWarning: null,
    onDismissWarning: () => {},
    isUnmountedDriveFallback: false,
    onRetryOriginalFile: () => {},
    onRetryEpisode: () => {},
    streamUrl: '',
    fileRetryCount: 0,
    videoRef: { current: null },
    youtubeSeekRequest: null,
    shouldAutoPlayYoutube: false,
    youtubeHandlers: { onProgress: () => {}, onEnded: () => {}, onPlayingChange: () => {}, onTimeTick: () => {} },
    videoHandlers: { onLoadedMetadata: () => {}, onLoadedData: () => {}, onCanPlay: () => {}, onError: () => {}, onTimeUpdate: () => {}, onPlay: () => {}, onPlaying: () => {}, onPause: () => {}, onSeeking: () => {}, onEnded: () => {} },
    offerResume: false,
    onResumePlayback: () => {},
    autoNext: false,
    onAutoNextToggle: () => {},
    nextCountdown: null,
    nextEpisodeLabel: 'Tập 2',
    hasNextEpisode: true,
    onCancelNextCountdown: () => {},
    progressError: null,
    kidPlayOverlay: {
      watchPet: { ...SAMPLE_WATCH_PET, stage: 'FAINTED', fainted: true },
      isPetLocked: true,
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
      activeRecapEpisodeId: 'ep1',
      onRecapUnlocked: () => {},
      onRecapRewatch: () => {},
    },
  });

  assert.equal(collectNodes(panelTree, (n) => n.type?.name === 'WatchPetWidget').length, 0);
  assert.equal(collectNodes(panelTree, (n) => n.type?.name === 'LootDropOverlay').length, 0);
  assert.equal(collectNodes(panelTree, (n) => n.type?.name === 'RecapQuizOverlay').length, 0);
  const yt = collectNodes(panelTree, (n) => n.props?.['data-testid'] === 'series-youtube-player')[0];
  assert.equal(yt.props.paused, false);
  panelHarness.cleanup();
});
