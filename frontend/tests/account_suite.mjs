import { runWithSharedModules } from './internal_loader.mjs';
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
  runWithSharedModules(code, {
    module,
    exports: module.exports,
    console,
    AbortController,
    DOMException,
    URLSearchParams,
    TextEncoder,
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

function collectNodes(node, predicate) {
  if (!node || typeof node !== 'object') return [];
  if (Array.isArray(node)) return node.flatMap((child) => collectNodes(child, predicate));
  return [...(predicate(node) ? [node] : []), ...collectNodes(node.props?.children, predicate)];
}

function renderAccountSettings(authState, serviceMocks) {
  let slots = [];
  let cursor = 0;
  let pendingEffects = [];
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
    createElement(type, props, ...children) {
      return { type, props: { ...props, children } };
    },
  };
  const iconStub = () => null;
  const mod = loadTsModule('../features/account/components/AccountSettings.tsx', {
    require(name) {
      if (name === 'react') return react;
      if (name === 'react-router') return { Link: 'route-link' };
      if (name === 'lucide-react') {
        return {
          AlertCircle: iconStub,
          CheckCircle2: iconStub,
          KeyRound: iconStub,
          Loader2: iconStub,
          RefreshCw: iconStub,
          User: iconStub,
        };
      }
      if (name.includes('AuthContext')) return { useAuth: () => authState };
      if (name.includes('accountService')) return serviceMocks;
      return {};
    },
  });
  return {
    render() {
      cursor = 0;
      const tree = mod.AccountSettings();
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

test('updateAccountProfile sends PUT /api/users/me with bearer token and normalized payload', async () => {
  const calls = [];
  const service = loadTsModule('../lib/services/accountService.ts', {
    fetch: async (url, opts) => {
      calls.push({ url, opts });
      return jsonResponse({
        success: true,
        message: 'Cập nhật thông tin tài khoản thành công',
        data: {
          id: 7,
          username: 'viewer01',
          email: 'moi@dippie.vn',
          fullName: 'Nguyễn Minh',
          role: 'ROLE_USER',
        },
      });
    },
  });

  const result = await service.updateAccountProfile('tok-abc', {
    fullName: '  Nguyễn Minh  ',
    email: '  moi@dippie.vn ',
  });

  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, '/api/users/me');
  assert.equal(calls[0].opts.method, 'PUT');
  assert.equal(calls[0].opts.headers.Authorization, 'Bearer tok-abc');
  assert.equal(calls[0].opts.headers['Content-Type'], 'application/json');
  assert.deepEqual(JSON.parse(calls[0].opts.body), {
    fullName: 'Nguyễn Minh',
    email: 'moi@dippie.vn',
  });
  assert.deepEqual(
    { ...result.user },
    {
      id: 7,
      username: 'viewer01',
      email: 'moi@dippie.vn',
      fullName: 'Nguyễn Minh',
      role: 'ROLE_USER',
    }
  );
  assert.equal(result.message, 'Cập nhật thông tin tài khoản thành công');

  await service.updateAccountProfile('tok-abc', { fullName: '   ', email: 'moi@dippie.vn' });
  assert.deepEqual(JSON.parse(calls[1].opts.body), {
    fullName: null,
    email: 'moi@dippie.vn',
  });
});

test('changeAccountPassword preserves verbatim passwords and returns replacement token and user', async () => {
  const calls = [];
  const service = loadTsModule('../lib/services/accountService.ts', {
    fetch: async (url, opts) => {
      calls.push({ url, opts });
      return jsonResponse({
        success: true,
        message: 'Đổi mật khẩu thành công',
        data: {
          token: 'new-jwt-v2',
          type: 'Bearer',
          id: 7,
          username: 'viewer01',
          email: 'viewer@dippie.vn',
          fullName: 'Viewer One',
          role: 'ROLE_USER',
        },
      });
    },
  });

  const result = await service.changeAccountPassword('old-jwt-v1', {
    currentPassword: ' old pass ',
    newPassword: ' new pass 123 ',
  });

  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, '/api/users/me/password');
  assert.equal(calls[0].opts.method, 'POST');
  assert.equal(calls[0].opts.headers.Authorization, 'Bearer old-jwt-v1');
  assert.deepEqual(JSON.parse(calls[0].opts.body), {
    currentPassword: ' old pass ',
    newPassword: ' new pass 123 ',
  });
  assert.equal(result.token, 'new-jwt-v2');
  assert.equal(result.user.id, 7);
  assert.equal(result.user.username, 'viewer01');
  assert.equal(result.message, 'Đổi mật khẩu thành công');
});

test('accountService propagates envelope error messages, status codes, timeouts, and aborts', async () => {
  const service = loadTsModule('../lib/services/accountService.ts', {
    fetch: async (url) => {
      if (url.endsWith('/password')) {
        return jsonResponse({ success: false, message: 'Mật khẩu hiện tại không đúng.', data: null }, 400);
      }
      return jsonResponse({ success: false, message: 'Email đã được sử dụng!', data: null }, 400);
    },
  });

  await assert.rejects(
    service.updateAccountProfile('tok', { fullName: 'A', email: 'dup@dippie.vn' }),
    (err) => err.status === 400 && err.message === 'Email đã được sử dụng!'
  );

  await assert.rejects(
    service.changeAccountPassword('tok', { currentPassword: 'wrong', newPassword: 'newpassword' }),
    (err) => err.status === 400 && err.message === 'Mật khẩu hiện tại không đúng.'
  );

  const hangingService = loadTsModule('../lib/services/accountService.ts', {
    fetch: () => new Promise(() => {}),
  });
  await assert.rejects(
    hangingService.updateAccountProfile('tok', { fullName: 'A', email: 'a@b.com' }, { timeoutMs: 15 }),
    (err) => err.isTimeout === true && err.status === 408
  );
  const external = new AbortController();
  const req = hangingService.changeAccountPassword(
    'tok',
    { currentPassword: 'old', newPassword: 'newpassword' },
    { signal: external.signal }
  );
  external.abort();
  await assert.rejects(req, (err) => err.isCanceled === true && err.status === 499);
});

test('client-side password and profile validation enforces min 6 chars, max 72 UTF-8 bytes, and confirm match', () => {
  const service = loadTsModule('../lib/services/accountService.ts', {
    fetch: async () => {
      throw new Error('unexpected');
    },
  });

  assert.ok(service.validateProfileInput({ fullName: 'Ok', email: 'invalid-email' }));
  assert.equal(service.validateProfileInput({ fullName: 'Ok', email: 'ok@dippie.vn' }), null);

  assert.ok(
    service.validatePasswordChangeInput({
      currentPassword: '',
      newPassword: 'abcdef',
      confirmPassword: 'abcdef',
    })
  );
  assert.ok(
    service.validatePasswordChangeInput({
      currentPassword: 'oldpass',
      newPassword: '12345',
      confirmPassword: '12345',
    })
  );
  const seventyThreeAscii = 'a'.repeat(73);
  assert.ok(
    service.validatePasswordChangeInput({
      currentPassword: 'oldpass',
      newPassword: seventyThreeAscii,
      confirmPassword: seventyThreeAscii,
    })
  );
  const multiByteOver72 = 'ệ'.repeat(25);
  assert.equal(service.utf8ByteLength(multiByteOver72), 75);
  assert.ok(
    service.validatePasswordChangeInput({
      currentPassword: 'oldpass',
      newPassword: multiByteOver72,
      confirmPassword: multiByteOver72,
    })
  );
  const multiByteExact72 = 'ệ'.repeat(24);
  assert.equal(service.utf8ByteLength(multiByteExact72), 72);
  assert.equal(
    service.validatePasswordChangeInput({
      currentPassword: 'oldpass',
      newPassword: multiByteExact72,
      confirmPassword: multiByteExact72,
    }),
    null
  );
  assert.ok(
    service.validatePasswordChangeInput({
      currentPassword: 'oldpass',
      newPassword: 'newpass1',
      confirmPassword: 'newpass2',
    })
  );
});

test('AccountSettings replaces session token on password change, updates user on profile save, and labels all inputs', async () => {
  const realService = loadTsModule('../lib/services/accountService.ts', {
    fetch: async (url, opts) => {
      if (url.endsWith('/users/me/password')) {
        return jsonResponse({
          success: true,
          message: 'Đổi mật khẩu thành công',
          data: {
            token: 'fresh-token-99',
            type: 'Bearer',
            id: 1,
            username: 'viewer',
            email: 'viewer@dippie.vn',
            fullName: 'Viewer Name',
            role: 'ROLE_USER',
          },
        });
      }
      const parsed = JSON.parse(opts.body);
      return jsonResponse({
        success: true,
        message: 'Cập nhật thông tin tài khoản thành công',
        data: {
          id: 1,
          username: 'viewer',
          email: parsed.email,
          fullName: parsed.fullName,
          role: 'ROLE_USER',
        },
      });
    },
  });

  const applied = [];
  const updated = [];
  const auth = {
    user: { id: 1, username: 'viewer', email: 'viewer@dippie.vn', fullName: 'Viewer Name', role: 'ROLE_USER' },
    token: 'initial-token',
    status: 'authenticated',
    isAuthenticated: true,
    isLoading: false,
    openAuthModal() {},
    retryVerify: async () => {},
    applySession(token, user) {
      applied.push({ token, user });
    },
    updateUser(user) {
      updated.push(user);
    },
    handleSessionExpired() {},
  };

  const view = renderAccountSettings(auth, realService);
  let tree = view.render();

  const inputs = collectNodes(tree, (n) => n.type === 'input');
  const labels = collectNodes(tree, (n) => n.type === 'label');
  assert.equal(inputs.length, 5);
  for (const input of inputs) {
    assert.ok(
      input.props.id && labels.some((label) => label.props.htmlFor === input.props.id),
      `input ${input.props.id} must have a matching label`
    );
  }

  const fullNameInput = inputs.find((n) => n.props.id === 'account-full-name');
  fullNameInput.props.onChange({ target: { value: 'Tên Mới' } });
  tree = view.render();

  const forms = collectNodes(tree, (n) => n.type === 'form');
  await forms[0].props.onSubmit({ preventDefault() {} });
  tree = view.render();
  assert.equal(updated.length, 1);
  assert.equal(updated[0].fullName, 'Tên Mới');

  const curPwd = collectNodes(tree, (n) => n.props?.id === 'account-current-password')[0];
  const newPwd = collectNodes(tree, (n) => n.props?.id === 'account-new-password')[0];
  const confPwd = collectNodes(tree, (n) => n.props?.id === 'account-confirm-password')[0];
  curPwd.props.onChange({ target: { value: 'oldpass123' } });
  newPwd.props.onChange({ target: { value: 'newpass456' } });
  confPwd.props.onChange({ target: { value: 'newpass456' } });
  tree = view.render();

  const updatedForms = collectNodes(tree, (n) => n.type === 'form');
  await updatedForms[1].props.onSubmit({ preventDefault() {} });
  tree = view.render();

  assert.equal(applied.length, 1);
  assert.equal(applied[0].token, 'fresh-token-99');
  const pwdFeedback = collectNodes(tree, (n) => n.props?.['data-testid'] === 'account-password-feedback')[0];
  assert.ok(pwdFeedback);
  assert.match(JSON.stringify(pwdFeedback), /thiết bị khác đã được đăng xuất/);

  view.unmount();
});

test('AccountSettings guest view prompts login and opens auth modal', () => {
  const openedModes = [];
  const auth = {
    user: null,
    token: null,
    status: 'guest',
    isAuthenticated: false,
    isLoading: false,
    openAuthModal(mode) {
      openedModes.push(mode);
    },
    retryVerify: async () => {},
    applySession() {},
    updateUser() {},
    handleSessionExpired() {},
  };
  const view = renderAccountSettings(auth, {});
  const tree = view.render();
  const loginButton = collectNodes(tree, (n) => n.props?.['data-testid'] === 'account-guest-login-button')[0];
  assert.ok(loginButton);
  loginButton.props.onClick();
  assert.deepEqual(openedModes, ['login']);
  view.unmount();
});

test('AuthContext applySession and updateUser persist session updates to localStorage without logout', () => {
  const stored = new Map();
  let slots = [];
  let cursor = 0;
  let capturedValue = null;
  const react = {
    createContext: () => ({
      Provider: ({ value }) => {
        capturedValue = value;
        return null;
      },
    }),
    useContext: () => capturedValue,
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
    useCallback: (fn) => fn,
    useEffect() {},
    createElement(type, props) {
      if (typeof type === 'function') return type(props);
      return { type, props };
    },
  };
  const mod = loadTsModule('../lib/context/AuthContext.tsx', {
    localStorage: {
      getItem: (key) => stored.get(key) ?? null,
      setItem: (key, value) => stored.set(key, value),
      removeItem: (key) => stored.delete(key),
    },
    fetch: async () => {
      throw new Error('unexpected');
    },
    require(name) {
      if (name === 'react') return react;
      return {};
    },
  });
  const render = () => {
    cursor = 0;
    mod.AuthProvider({ children: null });
    return capturedValue;
  };

  let ctx = render();
  const user1 = { id: 4, username: 'minh', email: 'minh@dippie.vn', fullName: 'Minh', role: 'ROLE_USER' };
  ctx.applySession('tok-v2', user1);
  ctx = render();
  assert.equal(ctx.token, 'tok-v2');
  assert.equal(ctx.status, 'authenticated');
  assert.equal(stored.get('dippie_auth_token'), 'tok-v2');
  assert.equal(JSON.parse(stored.get('dippie_auth_user')).fullName, 'Minh');

  const user2 = { ...user1, fullName: 'Minh Updated', email: 'new@dippie.vn' };
  ctx.updateUser(user2);
  ctx = render();
  assert.equal(ctx.token, 'tok-v2');
  assert.equal(ctx.user.fullName, 'Minh Updated');
  assert.equal(stored.get('dippie_auth_token'), 'tok-v2');
  assert.equal(JSON.parse(stored.get('dippie_auth_user')).email, 'new@dippie.vn');
});
