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

test('ParentBountySection creates tasks and approves pending submissions with PIN', async () => {
  const requests = [];
  let screenTimeRefreshed = 0;
  let pendingSubmissions = [
    {
      id: 77,
      taskId: 1,
      taskTitle: 'Dọn đồ chơi',
      taskIcon: 'toys',
      profileId: 10,
      profileName: 'Bé Bông',
      profileAvatarKey: 'bunny',
      usageDay: '2026-11-26',
      status: 'PENDING',
      rewardMinutes: 15,
      submittedAt: '2026-11-26T10:00:00Z',
      decidedAt: null,
      rejectNote: null,
    },
  ];
  let tasks = [
    {
      id: 1,
      title: 'Dọn đồ chơi',
      rewardMinutes: 15,
      icon: 'toys',
      active: true,
      repeatableDaily: true,
      profileIds: [10],
      createdAt: '',
      updatedAt: '',
    },
  ];

  const fetchMock = async (url, init = {}) => {
    const method = init.method || 'GET';
    const body = init.body ? JSON.parse(init.body) : null;
    requests.push({ url, method, body });
    if (url === '/api/users/bounties' && method === 'GET') {
      return jsonResponse({ success: true, message: 'OK', data: { tasks, submissions: pendingSubmissions } });
    }
    if (url === '/api/users/bounties/tasks' && method === 'POST') {
      const created = { id: 2, ...body, createdAt: '', updatedAt: '' };
      tasks = [...tasks, created];
      return jsonResponse({ success: true, message: 'Đã tạo nhiệm vụ.', data: created }, 201);
    }
    if (url === '/api/users/bounties/submissions/77/approve' && method === 'POST') {
      pendingSubmissions = [];
      return jsonResponse({
        success: true,
        message: 'Đã duyệt nhiệm vụ.',
        data: {
          submission: { id: 77, taskId: 1, taskTitle: 'Dọn đồ chơi', profileId: 10, profileName: 'Bé Bông', profileAvatarKey: 'bunny', usageDay: '2026-11-26', status: 'APPROVED', rewardMinutes: 15, submittedAt: '', decidedAt: '', rejectNote: null },
          screenTime: { limited: true, unlimited: false, profileId: 10, day: '2026-11-26', dailyMinutes: 60, mathLevel: 1, rewardMinutes: 5, maxBonusMinutes: 30, dailySeconds: 3600, watchedSeconds: 0, rewardSeconds: 0, grantSeconds: 0, bountySeconds: 900, remainingSeconds: 4500, locked: false, bonusCapSeconds: 1800, bonusRemainingSeconds: 1800, bonusCapReached: false },
        },
      });
    }
    throw new Error(`Unexpected ${method} ${url}`);
  };

  const harness = createComponentHarness(
    '../features/account/components/ParentBountySection.tsx',
    'ParentBountySection',
    {},
    { fetch: fetchMock },
    new Set(['SiteDialog'])
  );

  const props = {
    token: 'parent-jwt',
    childProfiles: [{ id: 10, name: 'Bé Bông', avatarKey: 'bunny', createdAt: '' }],
    onScreenTimeUpdated: () => {
      screenTimeRefreshed += 1;
    },
  };

  let tree = harness.render(props);
  await new Promise((resolve) => setTimeout(resolve, 10));
  tree = harness.render(props);

  assert.match(collectText(tree).replace(/\s+/g, ' '), /1 lượt chờ duyệt/);
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'add-bounty-task-button')[0].props.onClick();
  tree = harness.render(props);

  collectNodes(tree, (n) => n.props?.['data-testid'] === 'bounty-title-input')[0].props.onChange({ target: { value: 'Đọc truyện 15 phút' } });
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'bounty-reward-input')[0].props.onChange({ target: { value: '20' } });
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'bounty-pin-input')[0].props.onChange({ target: { value: '1234' } });
  tree = harness.render(props);
  await collectNodes(tree, (n) => n.type === 'form')[0].props.onSubmit({ preventDefault: () => {} });
  tree = harness.render(props);

  const createReq = requests.find((r) => r.method === 'POST' && r.url === '/api/users/bounties/tasks');
  assert.equal(createReq?.body.title, 'Đọc truyện 15 phút');
  assert.equal(createReq?.body.rewardMinutes, 20);
  assert.deepEqual(createReq?.body.profileIds, [10]);

  collectNodes(tree, (n) => n.props?.['data-testid'] === 'approve-bounty-submission-77')[0].props.onClick();
  tree = harness.render(props);
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'bounty-pin-input')[0].props.onChange({ target: { value: '1234' } });
  tree = harness.render(props);
  await collectNodes(tree, (n) => n.type === 'form')[0].props.onSubmit({ preventDefault: () => {} });
  tree = harness.render(props);

  assert.equal(screenTimeRefreshed, 1);
  assert.match(collectText(tree), /Đã duyệt nhiệm vụ/);
  harness.unmount();
});

test('KidBountyBoard submits a task and lets parent approve with PIN right in kid mode', async () => {
  let appliedStatus = null;
  let state = 'AVAILABLE';
  const fetchMock = async (url, init = {}) => {
    const method = init.method || 'GET';
    if (url === '/api/users/bounties/kid' && method === 'GET') {
      return jsonResponse({
        success: true,
        message: 'OK',
        data: {
          profileId: 10,
          day: '2026-11-26',
          tasks: [
            {
              id: 5,
              title: 'Gấp chăn gọn',
              rewardMinutes: 15,
              icon: 'bed',
              repeatableDaily: true,
              state,
              pendingSubmissionId: state === 'PENDING' ? 88 : null,
            },
          ],
          submissions: [],
        },
      });
    }
    if (url === '/api/users/bounties/tasks/5/submit' && method === 'POST') {
      state = 'PENDING';
      return jsonResponse(
        {
          success: true,
          message: 'Đã nộp nhiệm vụ.',
          data: { id: 88, taskId: 5, taskTitle: 'Gấp chăn gọn', profileId: 10, profileName: 'Bé Bông', profileAvatarKey: 'bunny', usageDay: '2026-11-26', status: 'PENDING', rewardMinutes: 15, submittedAt: '', decidedAt: null, rejectNote: null },
        },
        201
      );
    }
    if (url === '/api/users/bounties/submissions/88/approve' && method === 'POST') {
      state = 'COMPLETED';
      return jsonResponse({
        success: true,
        message: 'Đã duyệt.',
        data: {
          submission: { id: 88, taskId: 5, taskTitle: 'Gấp chăn gọn', profileId: 10, profileName: 'Bé Bông', profileAvatarKey: 'bunny', usageDay: '2026-11-26', status: 'APPROVED', rewardMinutes: 15, submittedAt: '', decidedAt: '', rejectNote: null },
          screenTime: { limited: true, unlimited: false, profileId: 10, day: '2026-11-26', dailyMinutes: 60, mathLevel: 1, rewardMinutes: 5, maxBonusMinutes: 30, dailySeconds: 3600, watchedSeconds: 0, rewardSeconds: 0, grantSeconds: 0, bountySeconds: 900, remainingSeconds: 4500, locked: false, bonusCapSeconds: 1800, bonusRemainingSeconds: 1800, bonusCapReached: false },
        },
      });
    }
    throw new Error(`Unexpected ${method} ${url}`);
  };

  const harness = createComponentHarness(
    '../features/profiles/components/KidBountyBoard.tsx',
    'KidBountyBoard',
    {
      '@/lib/context/ScreenTimeContext': {
        useOptionalScreenTime: () => ({
          applyScreenTimeStatus: (next) => {
            appliedStatus = next;
          },
        }),
      },
    },
    { fetch: fetchMock },
    new Set(['SiteDialog'])
  );

  let tree = harness.render({ token: 'child-jwt' });
  await new Promise((resolve) => setTimeout(resolve, 10));
  tree = harness.render({ token: 'child-jwt' });

  collectNodes(tree, (n) => n.props?.['data-testid'] === 'kid-bounty-submit-5')[0].props.onClick();
  await new Promise((resolve) => setTimeout(resolve, 15));
  tree = harness.render({ token: 'child-jwt' });
  assert.equal(collectNodes(tree, (n) => n.props?.['data-testid'] === 'kid-bounty-pending-badge-5').length, 1);

  collectNodes(tree, (n) => n.props?.['data-testid'] === 'kid-bounty-parent-approve-88')[0].props.onClick();
  tree = harness.render({ token: 'child-jwt' });
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'kid-bounty-review-pin-input')[0].props.onChange({ target: { value: '2468' } });
  tree = harness.render({ token: 'child-jwt' });
  await collectNodes(tree, (n) => n.type === 'form')[0].props.onSubmit({ preventDefault: () => {} });
  tree = harness.render({ token: 'child-jwt' });

  assert.equal(appliedStatus?.bountySeconds, 900);
  assert.equal(collectNodes(tree, (n) => n.props?.['data-testid'] === 'kid-bounty-completed-badge-5').length, 1);
  harness.unmount();
});

test('WeekendVaultBanner displays Vé xem phim bar and unlocks with parent PIN', async () => {
  let updatedVault = null;
  const fetchMock = async (url, init = {}) => {
    if (url === '/api/users/profiles/10/weekend-vault/unlock' && init.method === 'POST') {
      return jsonResponse({
        success: true,
        message: 'Đã mở khóa.',
        data: {
          profileId: 10,
          name: 'Bé Bông',
          avatarKey: 'bunny',
          status: {
            enabled: true,
            profileId: 10,
            day: '2026-11-26',
            weekStart: '2026-11-23',
            weekend: false,
            progressPercent: 60,
            unlocked: true,
            manualUnlocked: true,
            locked: false,
            days: [],
          },
        },
      });
    }
    throw new Error(`Unexpected ${url}`);
  };

  const harness = createComponentHarness(
    '../features/profiles/components/WeekendVaultBanner.tsx',
    'WeekendVaultBanner',
    {},
    { fetch: fetchMock },
    new Set(['SiteDialog'])
  );

  const props = {
    token: 'child-jwt',
    status: {
      enabled: true,
      profileId: 10,
      day: '2026-11-26',
      weekStart: '2026-11-23',
      weekend: false,
      progressPercent: 60,
      unlocked: false,
      manualUnlocked: false,
      locked: true,
      days: [
        { day: '2026-11-23', dayOfWeek: 1, ended: true, stoppedInTime: true, mathRewardCount: 3, mathGoalMet: true, earnedPercent: 40 },
        { day: '2026-11-24', dayOfWeek: 2, ended: true, stoppedInTime: true, mathRewardCount: 0, mathGoalMet: false, earnedPercent: 20 },
      ],
    },
    onStatusUpdated: (next) => {
      updatedVault = next;
    },
  };

  let tree = harness.render(props);
  assert.match(collectText(tree), /Vé xem phim/);
  assert.match(collectText(collectNodes(tree, (n) => n.props?.['data-testid'] === 'weekend-vault-percent')[0]), /60\s*%/);

  collectNodes(tree, (n) => n.props?.['data-testid'] === 'kid-vault-unlock-button')[0].props.onClick();
  tree = harness.render(props);
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'kid-vault-pin-input')[0].props.onChange({ target: { value: '1234' } });
  tree = harness.render(props);
  await collectNodes(tree, (n) => n.type === 'form')[0].props.onSubmit({ preventDefault: () => {} });
  tree = harness.render(props);

  assert.equal(updatedVault?.locked, false);
  assert.equal(updatedVault?.manualUnlocked, true);
  harness.unmount();
});

test('ParentVipSeriesSection marks VIP series per child and saves with PIN', async () => {
  const requests = [];
  const fetchMock = async (url, init = {}) => {
    const method = init.method || 'GET';
    const body = init.body ? JSON.parse(init.body) : null;
    requests.push({ url, method, body });
    if (url === '/api/users/vip-series' && method === 'GET') {
      return jsonResponse({
        success: true,
        message: 'OK',
        data: [
          { profileId: 10, name: 'Bé Bông', avatarKey: 'bunny', mathLevel: 2, vipMathLevel: 3, seriesIds: [] },
        ],
      });
    }
    if (url === '/api/users/profiles/10/vip-series' && method === 'PUT') {
      return jsonResponse({
        success: true,
        message: 'Đã lưu danh sách phim VIP.',
        data: { profileId: 10, name: 'Bé Bông', avatarKey: 'bunny', mathLevel: 2, vipMathLevel: 3, seriesIds: body.seriesIds },
      });
    }
    throw new Error(`Unexpected ${method} ${url}`);
  };

  const harness = createComponentHarness(
    '../features/account/components/ParentVipSeriesSection.tsx',
    'ParentVipSeriesSection',
    {
      '@/lib/services/seriesCardService': {
        listSeriesCards: async () => [
          { id: 'ser_vip', title: 'Siêu Nhân Toán Học', genres: [] },
          { id: 'ser_norm', title: 'Thỏ Nâu', genres: [] },
        ],
      },
    },
    { fetch: fetchMock },
    new Set(['SiteDialog'])
  );

  const props = { token: 'parent-jwt', childProfiles: [{ id: 10, name: 'Bé Bông', avatarKey: 'bunny', createdAt: '' }] };
  let tree = harness.render(props);
  await new Promise((resolve) => setTimeout(resolve, 10));
  tree = harness.render(props);

  assert.match(collectText(tree).replace(/\s+/g, ' '), /Mức 3/);
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'vip-series-checkbox-ser_vip')[0].props.onChange();
  tree = harness.render(props);
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'save-vip-series-button')[0].props.onClick();
  tree = harness.render(props);
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'vip-pin-input')[0].props.onChange({ target: { value: '2468' } });
  tree = harness.render(props);
  await collectNodes(tree, (n) => n.type === 'form')[0].props.onSubmit({ preventDefault: () => {} });
  tree = harness.render(props);

  const saveReq = requests.find((r) => r.method === 'PUT' && r.url === '/api/users/profiles/10/vip-series');
  assert.deepEqual(saveReq?.body, { pin: '2468', seriesIds: ['ser_vip'] });
  assert.match(collectText(tree), /Đã lưu danh sách phim VIP cho Bé Bông/);
  harness.unmount();
});

test('VipUnlockDialog solves a level+1 challenge and unlocks the episode', async () => {
  let unlockedEpisode = null;
  const fetchMock = async (url, init = {}) => {
    if (url === '/api/users/vip-series/episodes/ep_vip_1/challenge') {
      return jsonResponse({
        success: true,
        message: 'OK',
        data: {
          token: 'vip-tok-1',
          episodeId: 'ep_vip_1',
          seriesId: 'ser_vip',
          question: '14 + 5 = ?',
          mathLevel: 2,
          expiresAt: '2026-11-26T10:02:00Z',
          expiresInSeconds: 120,
        },
      });
    }
    if (url === '/api/users/vip-series/episodes/ep_vip_1/unlock' && init.method === 'POST') {
      const body = JSON.parse(init.body);
      if (body.answer !== 19) {
        return jsonResponse({
          success: true,
          message: 'Chưa đúng rồi!',
          data: { correct: false, unlocked: false, episodeId: 'ep_vip_1', day: '2026-11-26' },
        });
      }
      return jsonResponse({
        success: true,
        message: 'Mở khóa thành công!',
        data: { correct: true, unlocked: true, episodeId: 'ep_vip_1', day: '2026-11-26' },
      });
    }
    throw new Error(`Unexpected ${url}`);
  };

  const harness = createComponentHarness(
    '../features/profiles/components/VipUnlockDialog.tsx',
    'VipUnlockDialog',
    {},
    { fetch: fetchMock },
    new Set(['SiteDialog'])
  );

  const props = {
    episodeId: 'ep_vip_1',
    episodeTitle: 'Tập 1',
    token: 'child-jwt',
    onUnlocked: (id) => {
      unlockedEpisode = id;
    },
    onClose: () => {},
  };

  let tree = harness.render(props);
  await new Promise((resolve) => setTimeout(resolve, 10));
  tree = harness.render(props);

  assert.match(collectText(collectNodes(tree, (n) => n.props?.['data-testid'] === 'vip-challenge-question')[0]), /14 \+ 5 = \?/);
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'vip-answer-input')[0].props.onChange({ target: { value: '18' } });
  tree = harness.render(props);
  await collectNodes(tree, (n) => n.type === 'form')[0].props.onSubmit({ preventDefault: () => {} });
  tree = harness.render(props);
  assert.equal(unlockedEpisode, null);
  assert.match(collectText(collectNodes(tree, (n) => n.props?.['data-testid'] === 'vip-feedback')[0]), /Chưa đúng/);

  collectNodes(tree, (n) => n.props?.['data-testid'] === 'vip-answer-input')[0].props.onChange({ target: { value: '19' } });
  tree = harness.render(props);
  await collectNodes(tree, (n) => n.type === 'form')[0].props.onSubmit({ preventDefault: () => {} });
  assert.equal(unlockedEpisode, 'ep_vip_1');
  harness.unmount();
});

test('EpisodePlayerPanel locks VIP episodes in kid mode and ignores VIP for main profile, and KidModeDashboard hides for non-kid', () => {
  const sharedPlayerMocks = {
    '@/lib/labels': { cleanMediaTitle: (s) => s || '', formatDurationMs: () => '0:00' },
    '@/lib/services/playbackSource': { getUnavailableReasonMessage: () => '' },
  };
  const kidPlayer = createComponentHarness(
    '../features/series/components/EpisodePlayerPanel.tsx',
    'EpisodePlayerPanel',
    {
      ...sharedPlayerMocks,
      '@/lib/context/AuthContext': {
        useOptionalAuth: () => ({ isKidMode: true, token: 'child-jwt' }),
      },
      '@/lib/context/ScreenTimeContext': {
        useOptionalScreenTime: () => ({ isLimited: true, isLocked: false, remainingSeconds: 1800, screenTime: null }),
      },
    },
    {},
    new Set(['VipBadge'])
  );

  const playerProps = {
    seriesTitle: 'Siêu Nhân',
    selectedEpisode: { id: 'ep_vip_1', title: 'Tập 1', number: 1 },
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
    videoHandlers: { onLoadedMetadata: () => {}, onLoadedData: () => {}, onCanPlay: () => {}, onError: () => {}, onTimeUpdate: () => {}, onPlay: () => {}, onPlaying: () => {}, onPause: () => {}, onSeeking: () => {}, onEnded: () => {} },
    offerResume: false,
    autoNext: false,
    onAutoNextToggle: () => {},
    nextCountdown: null,
    nextEpisodeLabel: '',
    hasNextEpisode: false,
    onCancelNextCountdown: () => {},
    progressError: null,
    isVipLocked: true,
    vipMathLevel: 3,
    onVipUnlocked: () => {},
  };

  const kidTree = kidPlayer.render(playerProps);
  assert.equal(collectNodes(kidTree, (n) => n.props?.['data-testid'] === 'vip-episode-lock-overlay').length, 1);
  assert.equal(collectNodes(kidTree, (n) => n.props?.['data-testid'] === 'series-video-player').length, 0);
  kidPlayer.unmount();

  const mainPlayer = createComponentHarness(
    '../features/series/components/EpisodePlayerPanel.tsx',
    'EpisodePlayerPanel',
    {
      ...sharedPlayerMocks,
      '@/lib/context/AuthContext': {
        useOptionalAuth: () => ({ isKidMode: false, token: 'main-jwt' }),
      },
      '@/lib/context/ScreenTimeContext': {
        useOptionalScreenTime: () => null,
      },
    }
  );
  const mainTree = mainPlayer.render(playerProps);
  assert.equal(collectNodes(mainTree, (n) => n.props?.['data-testid'] === 'vip-episode-lock-overlay').length, 0);
  assert.equal(collectNodes(mainTree, (n) => n.props?.['data-testid'] === 'series-video-player').length, 1);
  mainPlayer.unmount();

  const mainDash = createComponentHarness(
    '../features/profiles/components/KidModeDashboard.tsx',
    'KidModeDashboard',
    {
      '@/lib/context/AuthContext': {
        useOptionalAuth: () => ({ isAuthenticated: true, isKidMode: false, token: 'main-jwt' }),
      },
    }
  );
  assert.equal(mainDash.render(), null);
  mainDash.unmount();
});

test('mediaService distinguishes X-Dippie-Lock-Reason on 423 so vault/VIP locks do not trigger screen-time lock', async () => {
  const dispatched = [];
  const fakeWindow = {
    dispatchEvent: (event) => {
      dispatched.push(event.type);
      return true;
    },
    localStorage: {
      getItem: () => 'child-jwt',
    },
  };

  let currentReason = 'WEEKEND_VAULT';
  const fetchMock = async () =>
    jsonResponse({ success: false, message: 'Khóa két sắt' }, 423, { 'X-Dippie-Lock-Reason': currentReason });

  const harness = createComponentHarness(
    '../lib/services/mediaService.ts',
    'requestPlaybackGrantResult',
    {},
    { fetch: fetchMock, window: fakeWindow }
  );

  const vaultResult = await harness.exports.requestPlaybackGrantResult('asset_1');
  assert.equal(vaultResult.status, 423);
  assert.equal(vaultResult.lockReason, 'WEEKEND_VAULT');
  assert.equal(dispatched.length, 0);

  currentReason = 'VIP_EPISODE';
  const vipResult = await harness.exports.requestPlaybackGrantResult('asset_2');
  assert.equal(vipResult.lockReason, 'VIP_EPISODE');
  assert.equal(dispatched.length, 0);

  currentReason = 'SCREEN_TIME';
  const screenResult = await harness.exports.requestPlaybackGrantResult('asset_3');
  assert.equal(screenResult.lockReason, 'SCREEN_TIME');
  assert.deepEqual(dispatched, ['dippie:screen-time-locked']);
});
