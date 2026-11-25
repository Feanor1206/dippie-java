import { runWithSharedModules } from './internal_loader.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '..');

function compileModule(relPath) {
  const source = fs.readFileSync(path.join(root, relPath), 'utf8');
  return ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      jsx: ts.JsxEmit.React,
      esModuleInterop: true,
      target: ts.ScriptTarget.ES2022,
    },
  }).outputText;
}

function loadAdminService(mockFetch) {
  const compiled = compileModule('lib/services/adminService.ts');
  const timers = new Map();
  let timerId = 0;
  const sandbox = {
    exports: {},
    module: { exports: {} },
    fetch: mockFetch,
    URLSearchParams,
    AbortController,
    setTimeout: (fn, ms) => {
      const id = ++timerId;
      timers.set(id, setTimeout(fn, ms));
      return id;
    },
    clearTimeout: (id) => {
      const t = timers.get(id);
      if (t) clearTimeout(t);
      timers.delete(id);
    },
    Error,
    Array,
    String,
    Boolean,
    Number,
    Object,
    JSON,
    console,
  };
  runWithSharedModules(compiled, sandbox);
  const api = sandbox.module.exports.listAdminUsers ? sandbox.module.exports : sandbox.exports;
  return { api, timers };
}

function loadAdminFormat() {
  const compiled = compileModule('features/admin/adminFormat.ts');
  const sandbox = {
    exports: {},
    module: { exports: {} },
    Intl,
    Date,
    Number,
    Math,
    Object,
    Array,
    String,
    Boolean,
    JSON,
  };
  runWithSharedModules(compiled, sandbox);
  return sandbox.module.exports.getAuditActionLabel ? sandbox.module.exports : sandbox.exports;
}

function findNode(node, predicate) {
  if (!node || typeof node !== 'object') return null;
  if (Array.isArray(node)) return node.map((child) => findNode(child, predicate)).find(Boolean) || null;
  return predicate(node) ? node : findNode(node.props?.children, predicate);
}

test('1. Admin service: listAdminUsers sends query, paging params, and Bearer auth header', async () => {
  let capturedUrl = '';
  let capturedInit = null;
  const { api } = loadAdminService(async (url, init) => {
    capturedUrl = url;
    capturedInit = init;
    return {
      ok: true,
      status: 200,
      json: async () => ({
        success: true,
        message: 'Thành công',
        timestamp: '2026-11-25T08:52:00',
        data: {
          items: [
            {
              id: 7,
              username: 'alice',
              email: 'alice@example.com',
              fullName: 'Alice Nguyen',
              role: 'ROLE_USER',
              enabled: true,
              createdAt: '2026-11-20T10:15:00',
            },
          ],
          page: 1,
          size: 20,
          totalElements: 21,
          totalPages: 2,
          hasNext: false,
          hasPrevious: true,
        },
      }),
    };
  });

  const page = await api.listAdminUsers('admin-jwt-123', { query: '  alice  ', page: 1, size: 20 });
  assert.equal(capturedUrl, '/api/admin/users?query=alice&page=1&size=20');
  assert.equal(capturedInit.method, 'GET');
  assert.equal(capturedInit.headers.Authorization, 'Bearer admin-jwt-123');
  assert.equal(capturedInit.headers.Accept, 'application/json');
  assert.equal(page.page, 1);
  assert.equal(page.hasPrevious, true);
  assert.equal(page.hasNext, false);
  assert.equal(page.items[0].username, 'alice');
  assert.equal(page.items[0].fullName, 'Alice Nguyen');
});

test('2. Admin service: updateAdminUserRole and updateAdminUserStatus send PATCH bodies and auth headers', async () => {
  const calls = [];
  const { api } = loadAdminService(async (url, init) => {
    calls.push({ url, method: init.method, headers: init.headers, body: JSON.parse(init.body) });
    if (url.endsWith('/role')) {
      return {
        ok: true,
        status: 200,
        json: async () => ({
          success: true,
          message: 'Cập nhật vai trò người dùng thành công',
          data: {
            id: 12,
            username: 'bob',
            email: 'bob@example.com',
            fullName: null,
            role: 'ROLE_ADMIN',
            enabled: true,
            createdAt: '2026-11-01T00:00:00',
          },
        }),
      };
    }
    return {
      ok: true,
      status: 200,
      json: async () => ({
        success: true,
        message: 'Cập nhật trạng thái người dùng thành công',
        data: {
          id: 12,
          username: 'bob',
          email: 'bob@example.com',
          fullName: null,
          role: 'ROLE_ADMIN',
          enabled: false,
          createdAt: '2026-11-01T00:00:00',
        },
      }),
    };
  });

  const promoted = await api.updateAdminUserRole('tok-1', 12, 'ROLE_ADMIN');
  const disabled = await api.updateAdminUserStatus('tok-1', 12, false);

  assert.equal(calls.length, 2);
  assert.equal(calls[0].url, '/api/admin/users/12/role');
  assert.equal(calls[0].method, 'PATCH');
  assert.equal(calls[0].headers.Authorization, 'Bearer tok-1');
  assert.equal(calls[0].headers['Content-Type'], 'application/json');
  assert.deepEqual(calls[0].body, { role: 'ROLE_ADMIN' });
  assert.equal(promoted.role, 'ROLE_ADMIN');
  assert.equal(promoted.fullName, null);

  assert.equal(calls[1].url, '/api/admin/users/12/status');
  assert.equal(calls[1].method, 'PATCH');
  assert.equal(calls[1].headers.Authorization, 'Bearer tok-1');
  assert.equal(calls[1].headers['Content-Type'], 'application/json');
  assert.deepEqual(calls[1].body, { enabled: false });
  assert.equal(disabled.enabled, false);
});

test('3. Admin service: getAdminStats and listAdminAuditEvents request shapes and envelope parsing', async () => {
  const requests = [];
  const { api } = loadAdminService(async (url, init) => {
    requests.push({ url, method: init.method, auth: init.headers.Authorization });
    if (url.includes('/admin/stats')) {
      return {
        ok: true,
        status: 200,
        json: async () => ({
          success: true,
          message: 'Thành công',
          data: {
            totalUsers: 40,
            activeUsers: 38,
            totalMovies: 15,
            publishedEpisodes: 52,
            totalWatchSessions: 120,
            completedWatchSessions: 90,
            completionRate: 0.75,
            topWatchedEpisodes: [{ episodeId: 'ep-1', title: 'Tập 1', viewers: 31 }],
            topRatedMovies: [{ movieId: 'm-1', title: 'Jadoo', averageRating: 8.75, ratingCount: 14 }],
            newUsersLast7Days: 6,
          },
        }),
      };
    }
    return {
      ok: true,
      status: 200,
      json: async () => ({
        success: true,
        message: 'Thành công',
        data: {
          items: [
            {
              id: 99,
              actorUsername: 'admin',
              action: 'USER_ROLE_CHANGED',
              targetType: 'USER',
              targetId: '12',
              details: { username: 'bob', previousRole: 'ROLE_USER', newRole: 'ROLE_ADMIN' },
              createdAt: '2026-11-25T08:52:00',
            },
          ],
          page: 2,
          size: 20,
          totalElements: 41,
          totalPages: 3,
          hasNext: false,
          hasPrevious: true,
        },
      }),
    };
  });

  const stats = await api.getAdminStats('jwt-stats');
  const audit = await api.listAdminAuditEvents('jwt-audit', { page: 2, size: 20 });

  assert.equal(requests[0].url, '/api/admin/stats');
  assert.equal(requests[0].method, 'GET');
  assert.equal(requests[0].auth, 'Bearer jwt-stats');
  assert.equal(stats.completionRate, 0.75);
  assert.equal(stats.topWatchedEpisodes[0].viewers, 31);
  assert.equal(stats.topRatedMovies[0].averageRating, 8.75);

  assert.equal(requests[1].url, '/api/admin/audit-events?page=2&size=20');
  assert.equal(requests[1].method, 'GET');
  assert.equal(requests[1].auth, 'Bearer jwt-audit');
  assert.equal(audit.items[0].action, 'USER_ROLE_CHANGED');
  assert.equal(audit.items[0].details.newRole, 'ROLE_ADMIN');
});

test('4. Admin service: propagates backend error messages (409, 403) and rejects missing token or malformed envelope', async () => {
  const { api: conflictApi } = loadAdminService(async () => ({
    ok: false,
    status: 409,
    json: async () => ({
      success: false,
      message: 'Không thể hạ quyền quản trị viên hoạt động cuối cùng.',
      data: null,
      timestamp: '2026-11-25T09:00:00',
    }),
  }));

  await assert.rejects(
    conflictApi.updateAdminUserRole('tok', 1, 'ROLE_USER'),
    (err) => {
      assert.equal(err.status, 409);
      assert.equal(err.message, 'Không thể hạ quyền quản trị viên hoạt động cuối cùng.');
      return true;
    }
  );

  const { api: forbiddenApi } = loadAdminService(async () => ({
    ok: false,
    status: 403,
    json: async () => ({
      success: false,
      message: 'Quyền truy cập bị từ chối (Yêu cầu ROLE_ADMIN)',
      data: null,
    }),
  }));

  await assert.rejects(
    forbiddenApi.getAdminStats('tok'),
    (err) => {
      assert.equal(err.status, 403);
      assert.equal(err.message, 'Tài khoản của bạn không có quyền thực hiện việc này.');
      return true;
    }
  );

  await assert.rejects(
    conflictApi.listAdminUsers('   '),
    (err) => err.status === 401
  );

  const { api: badEnvelopeApi } = loadAdminService(async () => ({
    ok: true,
    status: 200,
    json: async () => ({ success: false, message: 'Dữ liệu lỗi' }),
  }));

  await assert.rejects(
    badEnvelopeApi.listAdminAuditEvents('tok'),
    (err) => err.message === 'Dữ liệu lỗi'
  );
});

test('5. Admin format helpers: audit action labels, role and status labels', () => {
  const fmt = loadAdminFormat();

  assert.equal(fmt.getAuditActionLabel('USER_ROLE_CHANGED'), 'Đổi vai trò');
  assert.equal(fmt.getAuditActionLabel('USER_DISABLED'), 'Khóa tài khoản');
  assert.equal(fmt.getAuditActionLabel('USER_ENABLED'), 'Mở khóa tài khoản');
  assert.equal(fmt.getAuditActionLabel('CATALOG_IMPORTED'), 'Nhập dữ liệu');
  assert.equal(fmt.getAuditActionLabel('CUSTOM_ACTION_CODE'), 'CUSTOM_ACTION_CODE');
  assert.equal(fmt.getAuditActionLabel(''), 'Không có');
  assert.equal(fmt.getAuditActionLabel(null), 'Không có');

  assert.equal(fmt.getRoleLabel('ROLE_ADMIN'), 'Quản trị viên');
  assert.equal(fmt.getRoleLabel('ROLE_USER'), 'Người dùng');
  assert.equal(fmt.getUserStatusLabel(true), 'Đang hoạt động');
  assert.equal(fmt.getUserStatusLabel(false), 'Đã khóa');
});

test('6. Admin format helpers: percentage, rating, vi-VN date/datetime, details, and paging state', () => {
  const fmt = loadAdminFormat();

  assert.equal(fmt.formatPercentage(0), '0%');
  assert.equal(fmt.formatPercentage(0.75), '75%');
  assert.equal(fmt.formatPercentage(0.6667), '66,7%');
  assert.equal(fmt.formatPercentage(1), '100%');
  assert.equal(fmt.formatPercentage(-0.2), '0%');
  assert.equal(fmt.formatPercentage(Number.NaN), '0%');

  assert.equal(fmt.formatRating(8.76), '8.8');
  assert.equal(fmt.formatRating(9), '9.0');
  assert.equal(fmt.formatRating(Number.NaN), '0.0');

  assert.equal(fmt.formatViDate('2026-11-25T08:52:00', { timeZone: 'UTC' }), '25/11/2026');
  const formattedDateTime = fmt.formatViDateTime('2026-11-25T08:52:09', {
    includeSeconds: true,
    timeZone: 'UTC',
  });
  assert.match(formattedDateTime, /25\/11\/2026/);
  assert.match(formattedDateTime, /08:52:09/);
  assert.equal(fmt.formatViDate(null), 'Chưa có');
  assert.equal(fmt.formatViDate('not-a-date'), 'not-a-date');

  const pagingMid = fmt.getPagingSummary({
    page: 1,
    size: 20,
    totalElements: 45,
    totalPages: 3,
    hasNext: true,
    hasPrevious: true,
  });
  assert.deepEqual({ ...pagingMid }, {
    currentPage: 2,
    totalPages: 3,
    from: 21,
    to: 40,
    totalElements: 45,
    canPrevious: true,
    canNext: true,
  });
  assert.equal(fmt.getNextPage({ page: 1, size: 20, totalElements: 45, totalPages: 3, hasNext: true, hasPrevious: true }), 2);
  assert.equal(fmt.getPreviousPage({ page: 1, size: 20, totalElements: 45, totalPages: 3, hasNext: true, hasPrevious: true }), 0);

  const pagingEmpty = fmt.getPagingSummary({
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
    hasNext: false,
    hasPrevious: false,
  });
  assert.equal(pagingEmpty.from, 0);
  assert.equal(pagingEmpty.to, 0);
  assert.equal(pagingEmpty.canPrevious, false);
  assert.equal(pagingEmpty.canNext, false);

  assert.equal(fmt.formatAuditTarget('USER', '42'), 'USER: 42');
  assert.deepEqual(
    JSON.parse(JSON.stringify(fmt.formatAuditDetails({ username: 'alice', enabled: false, count: 3 }))),
    [
      { key: 'username', value: 'alice' },
      { key: 'enabled', value: 'false' },
      { key: 'count', value: '3' },
    ]
  );
});

test('7. AdminConfirmDialog follows modal accessibility pattern (role, aria-modal, focus trap, Escape, opener restore)', () => {
  const source = fs.readFileSync(path.join(root, 'features/admin/components/AdminConfirmDialog.tsx'), 'utf8');
  assert.ok(source.includes('role="dialog"'));
  assert.ok(source.includes('aria-modal="true"'));
  assert.ok(source.includes("e.key === 'Escape'"));
  assert.ok(source.includes("e.key === 'Tab'"));
  assert.ok(source.includes('e.shiftKey'));
  assert.ok(source.includes('openerRef.current?.focus()'));
  assert.ok(source.includes('min-h-[44px]') && source.includes('min-w-[44px]'));
});

test('8. AdminUsersPage gates non-admins, disables self actions with tooltip, updates row in place, and displays 409 errors inline', async () => {
  let auth = { status: 'guest', user: null, token: null, openAuthModal() {}, retryVerify() {} };
  let slots = [];
  let index = 0;
  let effects = [];
  let fetchCount = 0;
  let statusError = null;
  const formatMod = loadAdminFormat();

  const react = {
    useState(initial) {
      const i = index++;
      if (!(i in slots)) slots[i] = { value: typeof initial === 'function' ? initial() : initial };
      return [slots[i].value, (v) => { slots[i].value = typeof v === 'function' ? v(slots[i].value) : v; }];
    },
    useEffect(effect, deps) {
      const i = index++;
      const prev = slots[i];
      if (!prev || deps.some((v, j) => !Object.is(v, prev.deps[j]))) {
        slots[i] = { deps, cleanup: prev?.cleanup };
        effects.push(() => {
          slots[i].cleanup?.();
          slots[i].cleanup = effect();
        });
      }
    },
    createElement(type, props, ...children) {
      return { type, props: { ...props, children } };
    },
  };

  const module = { exports: {} };
  runWithSharedModules(compileModule('app/admin/users/page.tsx'), {
    module,
    exports: module.exports,
    AbortController,
    setTimeout: (fn) => {
      fn();
      return 1;
    },
    clearTimeout() {},
    require(name) {
      if (name === 'react') return react;
      if (name.includes('AuthContext')) return { useAuth: () => auth };
      if (name.includes('adminFormat')) return formatMod;
      if (name.includes('adminService')) {
        return {
          listAdminUsers: async () => {
            fetchCount += 1;
            return {
              items: [
                {
                  id: 1,
                  username: 'rootadmin',
                  email: 'root@example.com',
                  fullName: 'Root Admin',
                  role: 'ROLE_ADMIN',
                  enabled: true,
                  createdAt: '2026-11-01T00:00:00',
                },
                {
                  id: 2,
                  username: 'member',
                  email: 'member@example.com',
                  fullName: 'Member User',
                  role: 'ROLE_USER',
                  enabled: true,
                  createdAt: '2026-11-02T00:00:00',
                },
              ],
              page: 0,
              size: 20,
              totalElements: 2,
              totalPages: 1,
              hasNext: false,
              hasPrevious: false,
            };
          },
          updateAdminUserRole: async (_token, id, role) => ({
            id,
            username: 'member',
            email: 'member@example.com',
            fullName: 'Member User',
            role,
            enabled: true,
            createdAt: '2026-11-02T00:00:00',
          }),
          updateAdminUserStatus: async () => {
            if (statusError) throw statusError;
            return {
              id: 2,
              username: 'member',
              email: 'member@example.com',
              fullName: 'Member User',
              role: 'ROLE_ADMIN',
              enabled: false,
              createdAt: '2026-11-02T00:00:00',
            };
          },
        };
      }
      if (name.includes('AdminConfirmDialog')) {
        return {
          AdminConfirmDialog: (props) => ({ type: 'AdminConfirmDialog', props }),
        };
      }
      return {};
    },
  });

  const render = () => {
    index = 0;
    effects = [];
    const tree = module.exports.default();
    for (const effect of effects) effect();
    return tree;
  };
  const settle = async () => {
    let tree;
    for (let i = 0; i < 3; i++) {
      tree = render();
      await new Promise(setImmediate);
    }
    return render();
  };

  let tree = await settle();
  assert.equal(fetchCount, 0);
  assert.ok(findNode(tree, (n) => n.props?.['data-testid'] === 'admin-sign-in'));

  auth = { ...auth, status: 'authenticated', token: 'admin-tok', user: { id: 1, username: 'rootadmin', role: 'ROLE_ADMIN' } };
  tree = await settle();
  assert.equal(fetchCount, 1);

  const selfRoleBtn = findNode(tree, (n) => n.props?.['data-testid'] === 'toggle-role-1');
  const selfStatusBtn = findNode(tree, (n) => n.props?.['data-testid'] === 'toggle-status-1');
  assert.equal(selfRoleBtn.props.disabled, true);
  assert.equal(selfStatusBtn.props.disabled, true);
  assert.equal(selfRoleBtn.props.title, 'Không thể thay đổi tài khoản của chính bạn');
  assert.equal(selfStatusBtn.props.title, 'Không thể thay đổi tài khoản của chính bạn');

  const otherRoleBtn = findNode(tree, (n) => n.props?.['data-testid'] === 'toggle-role-2');
  assert.equal(otherRoleBtn.props.disabled, false);
  otherRoleBtn.props.onClick();
  tree = render();

  const dialog = findNode(tree, (n) => typeof n.type === 'function' && n.props?.open === true);
  assert.ok(dialog);
  await dialog.props.onConfirm();
  tree = await settle();

  assert.equal(fetchCount, 1);
  const updatedRow = findNode(tree, (n) => n.props?.['data-testid'] === 'admin-user-row-2');
  assert.match(JSON.stringify(updatedRow), /Quản trị viên/);

  statusError = new Error('Không thể khóa quản trị viên hoạt động cuối cùng.');
  const otherStatusBtn = findNode(tree, (n) => n.props?.['data-testid'] === 'toggle-status-2');
  otherStatusBtn.props.onClick();
  tree = render();
  const statusDialog = findNode(tree, (n) => typeof n.type === 'function' && n.props?.open === true);
  await statusDialog.props.onConfirm();
  tree = render();

  const alertBanner = findNode(tree, (n) => n.props?.['data-testid'] === 'admin-users-action-message');
  assert.ok(alertBanner);
  assert.match(JSON.stringify(alertBanner), /Không thể khóa quản trị viên hoạt động cuối cùng/);
});
