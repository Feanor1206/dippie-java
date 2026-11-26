import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';
import { labels } from './labels_fixture.mjs';
import { createInternalResolver, runWithSharedModules } from './internal_loader.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '..');

function compile(relPath) {
  return ts.transpileModule(fs.readFileSync(path.join(root, relPath), 'utf8'), {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      jsx: ts.JsxEmit.React,
      esModuleInterop: true,
      target: ts.ScriptTarget.ES2022,
    },
  }).outputText;
}

function loadService(relPath, fetchImpl) {
  const module = { exports: {} };
  const baseContext = {
    fetch: fetchImpl,
    AbortController,
    setTimeout,
    clearTimeout,
    TextEncoder,
    URL,
    URLSearchParams,
    console,
  };
  runWithSharedModules(compile(relPath), {
    ...baseContext,
    module,
    exports: module.exports,
    require: createInternalResolver(baseContext, () => ({})),
  });
  return module.exports;
}

function createHarness(relPath, mocks = {}, env = {}) {
  const slots = [];
  let cursor = 0;
  let effects = [];
  const same = (a, b) => a && b && a.length === b.length && a.every((v, i) => Object.is(v, b[i]));

  const react = {
    useId: () => 'test-id',
    useState(initial) {
      const i = cursor++;
      if (!(i in slots)) slots[i] = { v: typeof initial === 'function' ? initial() : initial };
      return [
        slots[i].v,
        (next) => {
          slots[i].v = typeof next === 'function' ? next(slots[i].v) : next;
        },
      ];
    },
    useRef(initial) {
      const i = cursor++;
      return (slots[i] ||= { current: initial });
    },
    useMemo(factory, deps) {
      const i = cursor++;
      if (!slots[i] || !deps || !same(slots[i].deps, deps)) slots[i] = { v: factory(), deps };
      return slots[i].v;
    },
    useCallback(fn) {
      return fn;
    },
    useEffect(fn, deps) {
      const i = cursor++;
      const prev = slots[i];
      if (!prev || !deps || !same(prev.deps, deps)) {
        slots[i] = { deps, cleanup: prev?.cleanup };
        effects.push(() => {
          slots[i].cleanup?.();
          slots[i].cleanup = fn();
        });
      }
    },
    useLayoutEffect() {},
    createElement(type, props, ...children) {
      return { type, props: { ...props, children } };
    },
  };

  const module = { exports: {} };
  const baseContext = {
    console,
    AbortController,
    URL,
    URLSearchParams,
    TextEncoder,
    setTimeout,
    clearTimeout,
    ...env,
  };
  runWithSharedModules(compile(relPath), {
    ...baseContext,
    module,
    exports: module.exports,
    require: createInternalResolver(baseContext, (name) => {
      if (name === 'react') return { __esModule: true, default: react, ...react };
      if (name === '@/lib/labels') return labels;
      if (mocks[name]) return mocks[name];
      for (const [key, val] of Object.entries(mocks)) {
        if (key.startsWith('*') && name.includes(key.slice(1))) return val;
      }
      return {};
    }),
  });

  function render(fn) {
    cursor = 0;
    effects = [];
    const tree = fn(module.exports);
    const pending = effects;
    effects = [];
    pending.forEach((run) => run());
    return tree;
  }

  return { mod: module.exports, render };
}

function findNodes(node, pred, out = []) {
  if (!node || typeof node !== 'object') return out;
  if (Array.isArray(node)) {
    for (const child of node) findNodes(child, pred, out);
    return out;
  }
  if (pred(node)) out.push(node);
  findNodes(node.props?.children, pred, out);
  return out;
}

test('S8 requestPasswordReset trims/lowercases email, always returns neutral message on 202, and handles 429 and 404/405 gracefully', async () => {
  const requests = [];
  const responses = [
    { ok: true, status: 202, json: async () => ({ message: 'ignored server detail' }) },
    { ok: false, status: 429, json: async () => ({ detail: 'Quá nhiều yêu cầu đặt lại mật khẩu.' }) },
    { ok: false, status: 404, json: async () => { throw new Error('HTML'); } },
    { ok: false, status: 405, json: async () => ({}) },
  ];
  const api = loadService('lib/services/passwordResetService.ts', async (url, init) => {
    requests.push({ url, init });
    return responses.shift();
  });

  await assert.rejects(api.requestPasswordReset('khong-hop-le'), (err) => err.code === 'validation');
  assert.equal(requests.length, 0);

  const result = await api.requestPasswordReset('  Parent@Dippie.VN  ');
  assert.equal(result.message, api.PASSWORD_RESET_REQUEST_SENT_MESSAGE);
  assert.equal(requests[0].url, '/api/auth/password-reset/request');
  assert.equal(requests[0].init.method, 'POST');
  assert.deepEqual(JSON.parse(requests[0].init.body), { email: 'parent@dippie.vn' });

  await assert.rejects(
    api.requestPasswordReset('parent@dippie.vn'),
    (err) => err.code === 'rate_limit' && /Quá nhiều yêu cầu/.test(err.message)
  );
  await assert.rejects(
    api.requestPasswordReset('parent@dippie.vn'),
    (err) => err.code === 'unavailable' && /chưa bật trên máy chủ/.test(err.message)
  );
  await assert.rejects(
    api.requestPasswordReset('parent@dippie.vn'),
    (err) => err.code === 'unavailable' && /chưa bật trên máy chủ/.test(err.message)
  );
});

test('S8 confirmPasswordReset sends verbatim password, validates strength rules, and separates invalid_token from 429/unavailable', async () => {
  const requests = [];
  const responses = [
    { ok: true, status: 204 },
    { ok: false, status: 400, json: async () => ({ detail: 'Liên kết đã hết hạn.' }) },
    { ok: false, status: 410, json: async () => ({}) },
    { ok: false, status: 429, json: async () => ({}) },
    { ok: false, status: 501, json: async () => ({}) },
  ];
  const api = loadService('lib/services/passwordResetService.ts', async (url, init) => {
    requests.push({ url, init });
    return responses.shift();
  });

  assert.equal(api.evaluatePasswordStrength('').level, 'empty');
  assert.equal(api.evaluatePasswordStrength('12345').level, 'weak');
  assert.equal(api.evaluatePasswordStrength('abcdef').level, 'fair');
  assert.equal(api.evaluatePasswordStrength('Family2026!').level, 'good');
  assert.ok(api.validateNewPassword('      ', '      '));
  assert.ok(api.validateNewPassword('a'.repeat(73), 'a'.repeat(73)));
  assert.ok(api.validateNewPassword('MatKhau123', 'KhacNhau123'));
  assert.equal(api.validateNewPassword('MatKhau123', 'MatKhau123'), null);

  await assert.rejects(api.confirmPasswordReset('', 'MatKhau123'), (err) => err.code === 'invalid_token');
  await assert.rejects(api.confirmPasswordReset('tok-1', '12345'), (err) => err.code === 'validation');
  assert.equal(requests.length, 0);

  await api.confirmPasswordReset('tok-valid', '  MatKhau 123  ');
  assert.equal(requests[0].url, '/api/auth/password-reset/confirm');
  assert.equal(requests[0].init.method, 'POST');
  assert.deepEqual(JSON.parse(requests[0].init.body), { token: 'tok-valid', newPassword: '  MatKhau 123  ' });

  await assert.rejects(
    api.confirmPasswordReset('tok-expired', 'MatKhau123'),
    (err) => err.code === 'invalid_token' && /hết hạn/.test(err.message)
  );
  await assert.rejects(
    api.confirmPasswordReset('tok-gone', 'MatKhau123'),
    (err) => err.code === 'invalid_token'
  );
  await assert.rejects(
    api.confirmPasswordReset('tok-rate', 'MatKhau123'),
    (err) => err.code === 'rate_limit'
  );
  await assert.rejects(
    api.confirmPasswordReset('tok-501', 'MatKhau123'),
    (err) => err.code === 'unavailable'
  );
});

test('S8 AuthModal shows "Quên mật khẩu?" link in login mode and switches to forgot mode', () => {
  let mode = 'login';
  const switchedModes = [];
  const h = createHarness(
    'features/auth/components/AuthModal.tsx',
    {
      'react-router': { Link: 'route-link' },
      '*AuthContext': {
        useAuth: () => ({
          isAuthModalOpen: true,
          authModalMode: mode,
          closeAuthModal() {},
          openAuthModal(next) {
            switchedModes.push(next);
            mode = next;
          },
          login: async () => true,
          register: async () => true,
        }),
      },
      '*WatchlistContext': { useWatchlist: () => ({ showToast() {} }) },
    },
    {
      document: { activeElement: null, body: { style: { overflow: '' } } },
      window: { addEventListener() {}, removeEventListener() {} },
    }
  );

  let tree = h.render((m) => m.AuthModal());
  const forgotLink = findNodes(tree, (n) => n.props?.['data-testid'] === 'forgot-password-link')[0];
  assert.ok(forgotLink, 'Login form must display a "Quên mật khẩu?" link');
  assert.match(JSON.stringify(forgotLink), /Quên mật khẩu\?/);

  forgotLink.props.onClick();
  assert.deepEqual(switchedModes, ['forgot']);

  tree = h.render((m) => m.AuthModal());
  const formNode = findNodes(tree, (n) => typeof n.type === 'function' && n.type.name === 'PasswordResetRequestForm')[0];
  assert.ok(formNode, 'Forgot mode must render PasswordResetRequestForm');
  formNode.props.onBackToLogin();
  assert.equal(switchedModes.at(-1), 'login');
});

test('S8 PasswordResetRequestForm submits email, shows neutral confirmation, and surfaces 429 errors', async () => {
  let nextResponse = { ok: false, status: 429, json: async () => ({ detail: 'Bạn vừa gửi quá nhiều yêu cầu.' }) };
  const h = createHarness(
    'features/auth/components/PasswordResetRequestForm.tsx',
    {},
    {
      fetch: async () => nextResponse,
    }
  );

  let tree = h.render((m) => m.PasswordResetRequestForm({ inputId: 'reset-email', onBackToLogin() {} }));
  const input = findNodes(tree, (n) => n.type === 'input')[0];
  const label = findNodes(tree, (n) => n.type === 'label' && n.props?.htmlFor === input.props.id)[0];
  assert.ok(label, 'Email input must have a matching label');

  input.props.onChange({ target: { value: 'parent@dippie.vn' } });
  tree = h.render((m) => m.PasswordResetRequestForm({ inputId: 'reset-email', onBackToLogin() {} }));
  await findNodes(tree, (n) => n.type === 'form')[0].props.onSubmit({ preventDefault() {} });
  tree = h.render((m) => m.PasswordResetRequestForm({ inputId: 'reset-email', onBackToLogin() {} }));
  assert.match(JSON.stringify(findNodes(tree, (n) => n.props?.role === 'alert')[0]), /quá nhiều yêu cầu/);

  nextResponse = { ok: true, status: 202, json: async () => ({}) };
  await findNodes(tree, (n) => n.type === 'form')[0].props.onSubmit({ preventDefault() {} });
  tree = h.render((m) => m.PasswordResetRequestForm({ inputId: 'reset-email', onBackToLogin() {} }));
  const sent = findNodes(tree, (n) => n.props?.['data-testid'] === 'password-reset-request-sent')[0];
  assert.ok(sent);
  assert.match(JSON.stringify(sent), /Nếu email này đã đăng ký Dippie, Dippie vừa gửi liên kết đặt lại mật khẩu/);
});

test('S8 ResetPasswordPage handles missing token, show/hide toggles, strength hints, success -> login, and expired token', async () => {
  let currentToken = '';
  let nextResponse = { ok: true, status: 204 };
  const openedAuthModes = [];

  const h = createHarness(
    'app/dat-lai-mat-khau/page.tsx',
    {
      'react-router': {
        Link: 'route-link',
        useSearchParams: () => [new URLSearchParams(currentToken ? `token=${currentToken}` : '')],
      },
      '*AuthContext': {
        useAuth: () => ({ openAuthModal: (m) => openedAuthModes.push(m) }),
      },
    },
    {
      fetch: async () => nextResponse,
    }
  );

  let tree = h.render((m) => m.default());
  assert.ok(findNodes(tree, (n) => n.props?.['data-testid'] === 'reset-token-invalid')[0]);
  const newLink = findNodes(tree, (n) => n.props?.['data-testid'] === 'request-new-reset-link')[0];
  assert.equal(newLink.props.to, '/quen-mat-khau');

  currentToken = 'tok-123';
  tree = h.render((m) => m.default());
  const newInput = () => findNodes(tree, (n) => n.props?.['data-testid'] === 'reset-new-password-input')[0];
  const confirmInput = () => findNodes(tree, (n) => n.props?.['data-testid'] === 'reset-confirm-password-input')[0];
  assert.equal(newInput().props.type, 'password');
  assert.equal(confirmInput().props.type, 'password');

  findNodes(tree, (n) => n.props?.['data-testid'] === 'toggle-new-password')[0].props.onClick();
  findNodes(tree, (n) => n.props?.['data-testid'] === 'toggle-confirm-password')[0].props.onClick();
  tree = h.render((m) => m.default());
  assert.equal(newInput().props.type, 'text');
  assert.equal(confirmInput().props.type, 'text');

  newInput().props.onChange({ target: { value: 'Dippie2026!' } });
  confirmInput().props.onChange({ target: { value: 'Different!' } });
  tree = h.render((m) => m.default());
  await findNodes(tree, (n) => n.type === 'form')[0].props.onSubmit({ preventDefault() {} });
  tree = h.render((m) => m.default());
  assert.match(JSON.stringify(findNodes(tree, (n) => n.props?.['data-testid'] === 'reset-password-error')[0]), /chưa khớp/);

  nextResponse = { ok: false, status: 404, json: async () => ({}) };
  confirmInput().props.onChange({ target: { value: 'Dippie2026!' } });
  tree = h.render((m) => m.default());
  await findNodes(tree, (n) => n.type === 'form')[0].props.onSubmit({ preventDefault() {} });
  tree = h.render((m) => m.default());
  assert.match(JSON.stringify(findNodes(tree, (n) => n.props?.['data-testid'] === 'reset-password-error')[0]), /chưa bật trên máy chủ/);

  nextResponse = { ok: false, status: 410, json: async () => ({ detail: 'Liên kết đã hết hạn.' }) };
  await findNodes(tree, (n) => n.type === 'form')[0].props.onSubmit({ preventDefault() {} });
  tree = h.render((m) => m.default());
  assert.ok(findNodes(tree, (n) => n.props?.['data-testid'] === 'reset-token-invalid')[0]);
  assert.equal(findNodes(tree, (n) => n.props?.['data-testid'] === 'request-new-reset-link')[0].props.to, '/quen-mat-khau');

  currentToken = 'tok-fresh';
  nextResponse = { ok: true, status: 204 };
  tree = h.render((m) => m.default());
  tree = h.render((m) => m.default());
  newInput().props.onChange({ target: { value: 'Dippie2026!' } });
  confirmInput().props.onChange({ target: { value: 'Dippie2026!' } });
  tree = h.render((m) => m.default());
  await findNodes(tree, (n) => n.type === 'form')[0].props.onSubmit({ preventDefault() {} });
  tree = h.render((m) => m.default());
  assert.ok(findNodes(tree, (n) => n.props?.['data-testid'] === 'reset-password-success')[0]);
  findNodes(tree, (n) => n.props?.['data-testid'] === 'reset-success-login')[0].props.onClick();
  assert.deepEqual(openedAuthModes, ['login']);
});

test('WatchHistory groups entries by day (Hôm nay / Hôm qua / older) and supports undo-friendly single remove', async () => {
  const deleted = [];
  const saved = [];
  const now = new Date(2026, 10, 26, 15, 0, 0).getTime();
  const todayIso = new Date(2026, 10, 26, 10, 30, 0).toISOString();
  const yesterdayIso = new Date(2026, 10, 25, 18, 0, 0).toISOString();
  const olderIso = new Date(2026, 10, 20, 9, 0, 0).toISOString();

  const records = [
    {
      episodeId: 'ep_today',
      seriesId: 'ser_doraemon',
      seriesTitle: 'Doraemon',
      episodeNumber: 1,
      episodeTitle: 'Tập hôm nay',
      assetId: 'youtube:11111111111',
      positionMs: 120000,
      durationMs: 600000,
      completed: false,
      version: 1,
      updatedAt: todayIso,
    },
    {
      episodeId: 'ep_yesterday',
      seriesId: 'ser_jadoo',
      seriesTitle: 'Xin Chào Jadoo',
      episodeNumber: 2,
      episodeTitle: 'Tập hôm qua',
      assetId: 'youtube:22222222222',
      positionMs: 600000,
      durationMs: 600000,
      completed: true,
      version: 2,
      updatedAt: yesterdayIso,
    },
    {
      episodeId: 'ep_older',
      seriesId: 'ser_ori',
      seriesTitle: 'Công chúa Ori',
      episodeNumber: 3,
      episodeTitle: 'Tập tuần trước',
      assetId: 'youtube:33333333333',
      positionMs: 30000,
      durationMs: 600000,
      completed: false,
      version: 1,
      updatedAt: olderIso,
    },
  ];

  const iconStub = () => null;
  const h = createHarness('app/lich-su/page.tsx', {
    'react-router': { Link: 'route-link' },
    'lucide-react': new Proxy({}, { get: () => iconStub }),
    '*AuthContext': { useAuth: () => ({ status: 'authenticated', token: 'tok-1', openAuthModal() {}, retryVerify() {} }) },
    '*useWatchProgress': { useWatchProgress: () => ({ records, loading: false, error: null, reload() {} }) },
    '*ArtworkImage': { ArtworkImage: 'ArtworkImage' },
    '*SiteDialog': { SiteDialog: 'SiteDialog' },
    '*seriesBrowseUtils': {
      computeProgressPercent: (pos, dur) => (dur && dur > 0 ? Math.min(100, Math.round((pos / dur) * 100)) : null),
      formatEpisodeTag: (n) => (n ? `Tập ${n}` : ''),
    },
    '*watchProgressService': {
      buildWatchProgressHref: (entry) => `/series/${entry.seriesId}?episode=${entry.episodeId}`,
      clearWatchProgress: async () => {},
      deleteWatchProgress: async (_token, id) => { deleted.push(id); },
      saveWatchProgress: async (_token, id, payload, expectedVersion) => { saved.push({ id, payload, expectedVersion }); },
      notifyWatchHistoryChanged() {},
    },
  });

  const groups = h.mod.groupWatchHistoryByDay(records, now);
  assert.equal(groups.length, 3);
  assert.equal(groups[0].label, 'Hôm nay');
  assert.equal(groups[1].label, 'Hôm qua');
  assert.equal(groups[2].entries[0].episodeId, 'ep_older');

  const renderInner = () => {
    const pageTree = h.render((m) => m.default());
    const inner = findNodes(pageTree, (n) => typeof n.type === 'function' && n.type.name === 'WatchHistory')[0];
    return h.render(() => inner.type(inner.props));
  };

  let tree = renderInner();
  const removeBtn = findNodes(
    tree,
    (n) => n.type === 'button' && n.props?.['aria-label'] === 'Xóa Tập hôm nay khỏi lịch sử'
  )[0];
  assert.ok(removeBtn);

  removeBtn.props.onClick();
  await new Promise((resolve) => setImmediate(resolve));
  tree = renderInner();

  assert.deepEqual(deleted, ['ep_today']);
  assert.equal(findNodes(tree, (n) => n.props?.['data-testid'] === 'history-item-ep_today').length, 0);
  const undoBtn = findNodes(tree, (n) => n.props?.['data-testid'] === 'watch-history-undo')[0];
  assert.ok(undoBtn, 'Undo button must appear after removing a watch history entry');

  undoBtn.props.onClick();
  await new Promise((resolve) => setImmediate(resolve));
  tree = renderInner();

  assert.equal(saved.length, 1);
  assert.equal(saved[0].id, 'ep_today');
  assert.equal(saved[0].expectedVersion, -1);
  assert.equal(findNodes(tree, (n) => n.props?.['data-testid'] === 'history-item-ep_today').length, 1);
});

test('Clearing all watch history on /lich-su also clears local movie progress for that account and notifies listeners', async () => {
  const clearedMovieUsers = [];
  let clearedServer = 0;
  let notified = 0;
  const iconStub = () => null;
  const h = createHarness('app/lich-su/page.tsx', {
    'react-router': { Link: 'route-link' },
    'lucide-react': new Proxy({}, { get: () => iconStub }),
    '*AuthContext': {
      useAuth: () => ({ status: 'authenticated', token: 'tok-9', user: { id: 9 }, openAuthModal() {}, retryVerify() {} }),
    },
    '*useWatchProgress': {
      useWatchProgress: () => ({
        records: [
          {
            episodeId: 'ep_1',
            seriesId: 'ser_doraemon',
            seriesTitle: 'Doraemon',
            episodeNumber: 1,
            episodeTitle: 'Tập 1',
            assetId: 'youtube:11111111111',
            positionMs: 60000,
            durationMs: 600000,
            completed: false,
            version: 0,
            updatedAt: '2026-11-26T00:00:00Z',
          },
        ],
        loading: false,
        error: null,
        reload() {},
      }),
    },
    '*ArtworkImage': { ArtworkImage: 'ArtworkImage' },
    '*SiteDialog': { SiteDialog: 'SiteDialog' },
    '*seriesBrowseUtils': {
      computeProgressPercent: () => 10,
      formatEpisodeTag: (n) => (n ? `Tập ${n}` : ''),
    },
    '*movieProgress': {
      clearMovieProgress: (uid) => clearedMovieUsers.push(uid),
    },
    '*watchProgressService': {
      buildWatchProgressHref: (entry) => `/series/${entry.seriesId}?episode=${entry.episodeId}`,
      clearWatchProgress: async () => { clearedServer += 1; },
      deleteWatchProgress: async () => {},
      saveWatchProgress: async () => {},
      notifyWatchHistoryChanged: () => { notified += 1; },
    },
  });

  const renderInner = () => {
    const pageTree = h.render((m) => m.default());
    const inner = findNodes(pageTree, (n) => typeof n.type === 'function' && n.type.name === 'WatchHistory')[0];
    return h.render(() => inner.type(inner.props));
  };

  let tree = renderInner();
  const clearBtn = findNodes(tree, (n) => n.props?.['data-testid'] === 'clear-watch-history-button')[0];
  clearBtn.props.onClick();
  tree = renderInner();

  const confirmBtn = findNodes(
    tree,
    (n) =>
      n.type === 'button' &&
      n.props?.['data-testid'] !== 'clear-watch-history-button' &&
      JSON.stringify(n.props?.children).includes('Xóa hết lịch sử')
  )[0];
  assert.ok(confirmBtn);
  confirmBtn.props.onClick();
  await new Promise((resolve) => setImmediate(resolve));

  assert.equal(clearedServer, 1);
  assert.deepEqual(clearedMovieUsers, [9]);
  assert.equal(notified, 1);
});
