import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
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
  vm.runInNewContext(code, {
    module,
    exports: module.exports,
    console,
    AbortController,
    DOMException,
    URLSearchParams,
    setTimeout,
    clearTimeout,
    ...globals,
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
    { label: 'Tiếp tục xem T3', resumeTime: '02:05' }
  );

  assert.deepEqual(
    {
      ...service.formatNextEpisodeLabel({
        reason: 'RESUME',
        episodeNumber: null,
        resumePositionMs: 45000,
      }),
    },
    { label: 'Tiếp tục xem', resumeTime: '00:45' }
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

test('NextEpisodeAction hides for guests, renders action/completed states, stays silent on 500, expires on 401, and refreshes on focus', async () => {
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
  assert.match(JSON.stringify(button), /Tiếp tục xem T2/);
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
  assert.equal(expiredCalls, 1);
  assert.equal(tree, null);

  view.unmount();
});
