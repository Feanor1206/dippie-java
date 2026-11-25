import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import ts from 'typescript';
import { runWithSharedModules } from './internal_loader.mjs';

function load(file, fetch) {
  const { outputText } = ts.transpileModule(fs.readFileSync(new URL('../' + file, import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  });
  const module = { exports: {} };
  runWithSharedModules(outputText, { module, exports: module.exports, fetch, AbortController, setTimeout, clearTimeout, URL, URLSearchParams, TextDecoder });
  return module.exports;
}

test('S4 sends catalog episodeId string and supports guest and authenticated requests', async () => {
  const requests = [];
  const api = load('lib/services/playbackReportService.ts', async (url, init) => {
    requests.push({ url, init });
    return { status: 201, json: async () => ({ id: 17 }) };
  });
  assert.equal(
    await api.submitPlaybackReport({ episodeId: 'ep_doraemon_s05_e001', assetId: null, reason: 'khong_phat' }),
    17
  );
  await api.submitPlaybackReport(
    { episodeId: ' ep_doraemon_s05_e001 ', assetId: 'video / 1', reason: 'am_thanh', note: 'Tiếng nhỏ' },
    'active'
  );
  assert.equal(requests[0].url, '/api/reports/playback');
  assert.equal(requests[0].init.method, 'POST');
  assert.equal(requests[0].init.headers.Authorization, undefined);
  assert.deepEqual(JSON.parse(requests[0].init.body), {
    episodeId: 'ep_doraemon_s05_e001',
    assetId: null,
    reason: 'khong_phat',
  });
  assert.equal(requests[1].init.headers.Authorization, 'Bearer active');
  assert.deepEqual(JSON.parse(requests[1].init.body), {
    episodeId: 'ep_doraemon_s05_e001',
    assetId: 'video / 1',
    reason: 'am_thanh',
    note: 'Tiếng nhỏ',
  });
});

test('S4 validates non-empty catalog episodeId string and note/reason, then shows 429 and 405 messages', async () => {
  const responses = [
    { status: 429, json: async () => ({ detail: 'Bạn vừa gửi nhiều báo lỗi.' }) },
    { status: 405, json: async () => { throw new Error('HTML'); } },
  ];
  const api = load('lib/services/playbackReportService.ts', async () => responses.shift());
  const draft = { episodeId: 'ep_ori_s01_e001', assetId: null, reason: 'khac' };
  await assert.rejects(api.submitPlaybackReport({ ...draft, episodeId: '   ' }), /mã tập/);
  await assert.rejects(api.submitPlaybackReport({ ...draft, note: 'x'.repeat(501) }), /500/);
  await assert.rejects(api.submitPlaybackReport({ ...draft, reason: 'anything' }), /lý do/);
  await assert.rejects(api.submitPlaybackReport(draft), /Bạn vừa gửi nhiều báo lỗi/);
  await assert.rejects(api.submitPlaybackReport(draft), /chưa bật trên máy chủ/);
});

test('S4 admin uses open status, Bearer auth, catalog string episodeId, and PATCH status with no body required', async () => {
  const requests = [];
  const report = {
    id: 17,
    episodeId: 'ep_ori_s01_e001',
    seriesTitle: 'Ori',
    episodeTitle: 'Tập 1',
    assetId: null,
    reason: 'khong_phat',
    note: null,
    status: 'open',
    createdAt: '2026-11-26T12:00:00+07:00',
  };
  const api = load('lib/services/playbackReportService.ts', async (url, init) => {
    requests.push({ url, init });
    return { status: 200, json: async () => [report] };
  });
  const list = await api.listOpenPlaybackReports('admin');
  assert.equal(list[0].episodeId, 'ep_ori_s01_e001');
  assert.equal(list[0].episodeTitle, 'Tập 1');
  await api.updatePlaybackReport('admin', 17, 'dismissed');
  assert.equal(requests[0].url, '/api/admin/reports?status=open');
  assert.equal(requests[1].url, '/api/admin/reports/17');
  assert.equal(requests[1].init.method, 'PATCH');
  assert.equal(requests[1].init.headers.Authorization, 'Bearer admin');
  assert.deepEqual(JSON.parse(requests[1].init.body), { status: 'dismissed' });
});

test('S4 PlaybackReportButton allows reporting any catalog episode string ID without an unavailable path', async () => {
  const requests = [];
  let slots = [];
  let cursor = 0;
  const react = {
    Fragment: 'Fragment',
    useId: () => 'report-id',
    useState(initial) {
      const i = cursor++;
      if (!(i in slots)) slots[i] = typeof initial === 'function' ? initial() : initial;
      return [slots[i], (next) => { slots[i] = typeof next === 'function' ? next(slots[i]) : next; }];
    },
    useRef: (initial) => {
      const i = cursor++;
      return (slots[i] ||= { current: initial });
    },
    useEffect() {},
    useLayoutEffect() {},
    createElement(type, props, ...children) {
      if (typeof type === 'function') return type({ ...props, children });
      return { type, props: { ...props, children } };
    },
  };
  const { outputText } = ts.transpileModule(
    fs.readFileSync(new URL('../features/series/components/PlaybackReportButton.tsx', import.meta.url), 'utf8'),
    { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.React, esModuleInterop: true, target: ts.ScriptTarget.ES2022 } }
  );
  const module = { exports: {} };
  runWithSharedModules(outputText, {
    module,
    exports: module.exports,
    AbortController,
    setTimeout,
    clearTimeout,
    fetch: async (url, init) => {
      requests.push({ url, body: JSON.parse(init.body) });
      return { status: 201, json: async () => ({ id: 21 }) };
    },
    require(name) {
      if (name === 'react') return { __esModule: true, default: react, ...react };
      if (name.includes('AuthContext')) return { useAuth: () => ({ status: 'guest', token: null }) };
      return {};
    },
  });
  const findAll = (n, p) => !n || typeof n !== 'object' ? [] : Array.isArray(n) ? n.flatMap((c) => findAll(c, p)) : [...(p(n) ? [n] : []), ...findAll(n.props?.children, p)];
  const render = () => {
    cursor = 0;
    return module.exports.PlaybackReportButton({ episodeId: 'ep_doraemon_s05_e001', assetId: null, episodeTitle: 'Tập 1' });
  };
  let tree = render();
  const trigger = findAll(tree, (n) => n.props?.['data-testid'] === 'playback-report-trigger')[0];
  assert.ok(trigger);
  trigger.props.onClick();
  tree = render();
  assert.doesNotMatch(JSON.stringify(tree), /chưa hỗ trợ báo lỗi/);
  const firstRadio = findAll(tree, (n) => n.type === 'input' && n.props?.type === 'radio')[0];
  assert.ok(firstRadio);
  firstRadio.props.onChange();
  tree = render();
  const form = findAll(tree, (n) => n.type === 'form')[0];
  assert.ok(form);
  await form.props.onSubmit({ preventDefault() {} });
  tree = render();
  assert.equal(requests.length, 1);
  assert.equal(requests[0].url, '/api/reports/playback');
  assert.deepEqual(requests[0].body, { episodeId: 'ep_doraemon_s05_e001', assetId: null, reason: 'khong_phat' });
  assert.ok(findAll(tree, (n) => n.props?.['data-testid'] === 'playback-report-success')[0]);
});

test('S2 sends the verbatim password on DELETE and exposes conflict and unavailable outcomes', async () => {
  const requests = [];
  const responses = [
    { status: 204 },
    { status: 400, json: async () => ({ message: 'Mật khẩu hiện tại chưa đúng.' }) },
    { status: 409, json: async () => ({}) },
    { status: 404, json: async () => { throw new Error('HTML'); } },
  ];
  const api = load('lib/services/accountService.ts', async (url, init) => {
    requests.push({ url, init });
    return responses.shift();
  });
  await api.deleteAccount('active', ' password ');
  assert.equal(requests[0].url, '/api/users/me');
  assert.equal(requests[0].init.method, 'DELETE');
  assert.equal(requests[0].init.headers.Authorization, 'Bearer active');
  assert.deepEqual(JSON.parse(requests[0].init.body), { password: ' password ' });
  await assert.rejects(api.deleteAccount('active', 'wrong'), /Mật khẩu hiện tại chưa đúng/);
  await assert.rejects(api.deleteAccount('active', 'correct'), /quản trị viên cuối cùng/i);
  await assert.rejects(api.deleteAccount('active', 'correct'), /chưa bật trên máy chủ/);
});

test('S5 clears history only for the authenticated caller and tolerates an absent endpoint', async () => {
  const responses = [{ status: 204 }, { status: 405 }];
  const api = load('lib/services/watchProgressService.ts', async (url, init) => {
    assert.equal(url, '/api/users/watch-progress');
    assert.equal(init.method, 'DELETE');
    assert.equal(init.headers.Authorization, 'Bearer active');
    assert.equal(init.body, undefined);
    return responses.shift();
  });
  await api.clearWatchProgress('active');
  await assert.rejects(api.clearWatchProgress('active'), /chưa bật trên máy chủ/);
});

test('S6 uses the 429 detail for CineBot before starting a stream', async () => {
  const api = load('lib/services/chatService.ts', async () => ({
    ok: false, status: 429, json: async () => ({ detail: 'Bạn có thể hỏi lại sau một phút.', text: 'Generic' }),
  }));
  await assert.rejects(api.sendChatMessage('Tìm một tập phim'), /Bạn có thể hỏi lại sau một phút/);
});

test('S5 removes a URL-encoded episode, accepts the existing 200 JSON envelope and handles 204', async () => {
  const responses = [
    { ok: true, status: 200, json: async () => ({ success: true, data: null }) },
    { ok: true, status: 204 },
  ];
  const episodeId = 'ep:a / ?';
  const api = load('lib/services/watchProgressService.ts', async (url, init) => {
    assert.equal(url, '/api/users/watch-progress/' + encodeURIComponent(episodeId));
    assert.equal(init.method, 'DELETE');
    assert.equal(init.headers.Authorization, 'Bearer active');
    return responses.shift();
  });
  await api.deleteWatchProgress('active', episodeId);
  await api.deleteWatchProgress('active', episodeId);
});

test('Shared deadline covers a stuck body and caller cancellation even if work ignores abort', async () => {
  const never = () => new Promise(() => {});
  const { withRequestTimeout } = load('lib/services/apiRequest.ts');
  let requestSignal;
  const hanging = (signal) => { requestSignal = signal; return never(); };
  await assert.rejects(withRequestTimeout(hanging, { timeoutMs: 10 }, 'Hết thời gian chờ.'), /Hết thời gian chờ/);
  assert.equal(requestSignal.aborted, true);
  const caller = new AbortController();
  const canceled = withRequestTimeout(hanging, { signal: caller.signal, timeoutMs: 1000 });
  caller.abort();
  await assert.rejects(canceled, { name: 'AbortError' });
  assert.equal(requestSignal.aborted, true);
});

test('S7 interprets legacy offsetless values as UTC and explicit offsets as instants', () => {
  const { parseTimestamp, formatViDateTime } = load('lib/timestamps.ts');
  const utc = Date.parse('2026-11-25T17:05:00Z');
  assert.equal(parseTimestamp('2026-11-25T17:05:00'), utc);
  assert.equal(parseTimestamp('2026-11-26T00:05:00+07:00'), utc);
  assert.match(formatViDateTime('2026-11-25T17:05:00Z', { timeZone: 'Asia/Bangkok' }), /00:05.*26/);
});

test('External URLs and artwork reject unsafe schemes and ambiguous paths', () => {
  const { safeHttpUrl, safeImageUrl } = load('lib/safeUrl.ts');
  for (const bad of ['javascript:alert(1)', 'data:image/svg+xml,unsafe', 'file:///tmp/a', '//evil.test/a', '/\\\\evil.test/a']) {
    assert.equal(safeHttpUrl(bad), undefined, bad);
    assert.equal(safeImageUrl(bad), undefined, bad);
  }
  assert.equal(safeHttpUrl('http://example.test/'), 'http://example.test/');
  assert.equal(safeImageUrl('http://example.test/poster'), undefined);
  assert.equal(safeImageUrl('/assets/poster.webp'), '/assets/poster.webp');
});
