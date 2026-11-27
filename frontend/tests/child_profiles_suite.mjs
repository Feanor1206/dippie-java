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

function jsonResponse(body, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
    headers: { get: () => null },
  };
}

function makeFakeJwt(payload) {
  const encode = (obj) => Buffer.from(JSON.stringify(obj)).toString('base64url');
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode(payload)}.sig`;
}

function collectNodes(node, predicate) {
  if (!node || typeof node !== 'object') return [];
  if (Array.isArray(node)) return node.flatMap((child) => collectNodes(child, predicate));
  return [...(predicate(node) ? [node] : []), ...collectNodes(node.props?.children, predicate)];
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
    atob: globalThis.atob,
    setTimeout,
    clearTimeout,
    ...globals,
  };

  const customRequire = (name) => {
    if (name === 'react') return react;
    if (name === 'lucide-react') return createIconProxy();
    if (name in mocks) return mocks[name];
    return {};
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

  const mod = { exports: {} };
  vm.runInNewContext(
    transpileFile(relativePath),
    { ...baseContext, module: mod, exports: mod.exports, require: wrappedRequire },
    { filename: relativePath }
  );

  const Component = exportName === 'default' ? mod.exports.default : mod.exports[exportName];

  return {
    exports: mod.exports,
    render(props = {}) {
      cursor = 0;
      const tree = Component(props);
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

function createMemoryStorage() {
  const map = new Map();
  return {
    getItem: (k) => (map.has(k) ? map.get(k) : null),
    setItem: (k, v) => { map.set(k, String(v)); },
    removeItem: (k) => { map.delete(k); },
    clear: () => { map.clear(); },
  };
}

function mountAuthProvider(fetchImpl, initialStorage = {}) {
  const storage = createMemoryStorage();
  for (const [k, v] of Object.entries(initialStorage)) storage.setItem(k, v);

  let latestContext = null;
  let slots = [];
  let cursor = 0;
  let pendingEffects = [];
  const same = (a, b) => a && b && a.length === b.length && a.every((v, i) => Object.is(v, b[i]));

  const react = {
    createContext() {
      return {
        Provider: ({ value }) => {
          latestContext = value;
          return null;
        },
      };
    },
    useContext: () => latestContext,
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
      if (typeof type === 'function') return type({ ...props, children });
      return { type, props: { ...props, children } };
    },
  };

  const mod = { exports: {} };
  runWithSharedModules(transpileFile('../lib/context/AuthContext.tsx'), {
    module: mod,
    exports: mod.exports,
    require: (name) => (name === 'react' ? react : {}),
    fetch: fetchImpl,
    localStorage: storage,
    window: { addEventListener() {}, removeEventListener() {} },
    console,
    AbortController,
    DOMException,
    URLSearchParams,
    TextEncoder,
    atob: globalThis.atob,
    setTimeout,
    clearTimeout,
  });

  const render = () => {
    cursor = 0;
    mod.exports.AuthProvider({ children: null });
    const effects = pendingEffects;
    pendingEffects = [];
    effects.forEach((fn) => fn());
    return latestContext;
  };

  const flush = async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
    return render();
  };

  return {
    render,
    flush,
    storage,
    unmount: () => slots.forEach((s) => s?.cleanup?.()),
  };
}

test('profileService validates PIN and profile name and decodes pid claim from JWT', async () => {
  const calls = [];
  const mod = { exports: {} };
  runWithSharedModules(transpileFile('../lib/services/profileService.ts'), {
    module: mod,
    exports: mod.exports,
    fetch: async (url, opts) => {
      calls.push({ url, opts });
      if (url.endsWith('/users/profiles/5/switch')) {
        return jsonResponse({
          success: true,
          message: 'Đã chuyển hồ sơ',
          data: {
            token: makeFakeJwt({ sub: 'parent', pid: 5 }),
            type: 'Bearer',
            id: 1,
            username: 'parent',
            email: 'parent@dippie.vn',
            fullName: 'Ba Mẹ',
            role: 'ROLE_USER',
            activeProfile: { id: 5, name: 'Bé Bông', avatarKey: 'bunny', createdAt: '2026-11-26T10:00:00' },
          },
        });
      }
      return jsonResponse({
        success: true,
        message: 'Thành công',
        data: {
          parentModeEnabled: true,
          activeProfileId: null,
          activeProfile: null,
          profiles: [{ id: 5, name: 'Bé Bông', avatarKey: 'bunny', createdAt: '2026-11-26T10:00:00' }],
          availableAvatars: ['bear', 'bunny'],
        },
      });
    },
    console,
    AbortController,
    DOMException,
    atob: globalThis.atob,
    setTimeout,
    clearTimeout,
  });

  const svc = mod.exports;
  assert.ok(svc.validateParentPin('123'));
  assert.ok(svc.validateParentPin('1234567'));
  assert.ok(svc.validateParentPin('12ab'));
  assert.equal(svc.validateParentPin('1234'), null);
  assert.equal(svc.validateParentPin('123456'), null);

  assert.ok(svc.validateChildProfileName('   '));
  assert.ok(svc.validateChildProfileName('a'.repeat(51)));
  assert.equal(svc.validateChildProfileName('Bé Bông'), null);

  assert.equal(svc.decodeJwtProfileId(makeFakeJwt({ sub: 'parent', pid: 42 })), 42);
  assert.equal(svc.decodeJwtProfileId(makeFakeJwt({ sub: 'parent' })), null);
  assert.equal(svc.decodeJwtProfileId('not-a-jwt'), null);

  const state = await svc.fetchProfilesState('tok-main');
  assert.equal(state.parentModeEnabled, true);
  assert.equal(state.profiles.length, 1);

  const switched = await svc.switchToChildProfile('tok-main', 5);
  assert.equal(switched.activeProfile.id, 5);
  assert.equal(svc.decodeJwtProfileId(switched.token), 5);
  assert.equal(calls[1].opts.method, 'POST');
});

test('AuthProvider opens profile picker after login only when parent mode is on and switches in and out of kid mode', async () => {
  const mainToken = makeFakeJwt({ sub: 'parent', tv: 0 });
  const kidToken = makeFakeJwt({ sub: 'parent', tv: 0, pid: 10 });
  const child = { id: 10, name: 'Bé Bông', avatarKey: 'bunny', createdAt: '2026-11-26T10:00:00' };
  let parentEnabled = true;

  const provider = mountAuthProvider(async (url, opts) => {
    if (url.endsWith('/auth/login')) {
      return jsonResponse({
        success: true,
        data: {
          token: mainToken,
          type: 'Bearer',
          id: 1,
          username: 'parent',
          email: 'parent@dippie.vn',
          fullName: 'Ba Mẹ',
          role: 'ROLE_USER',
        },
      });
    }
    if (url.endsWith('/users/profiles')) {
      return jsonResponse({
        success: true,
        data: {
          parentModeEnabled: parentEnabled,
          activeProfileId: null,
          activeProfile: null,
          profiles: parentEnabled ? [child] : [],
          availableAvatars: ['bear', 'bunny'],
        },
      });
    }
    if (url.endsWith('/users/profiles/10/switch')) {
      return jsonResponse({
        success: true,
        message: 'Đã chuyển sang hồ sơ Bé Bông',
        data: {
          token: kidToken,
          type: 'Bearer',
          id: 1,
          username: 'parent',
          email: 'parent@dippie.vn',
          fullName: 'Ba Mẹ',
          role: 'ROLE_USER',
          activeProfile: child,
        },
      });
    }
    if (url.endsWith('/users/profiles/exit')) {
      const body = JSON.parse(opts.body);
      if (body.pin !== '1234') {
        return jsonResponse({ success: false, message: 'Mã PIN phụ huynh chưa đúng.' }, 400);
      }
      return jsonResponse({
        success: true,
        message: 'Đã quay lại tài khoản chính',
        data: {
          token: mainToken,
          type: 'Bearer',
          id: 1,
          username: 'parent',
          email: 'parent@dippie.vn',
          fullName: 'Ba Mẹ',
          role: 'ROLE_USER',
          activeProfile: null,
        },
      });
    }
    return jsonResponse({ success: false }, 404);
  });

  let ctx = provider.render();
  const loginRes = await ctx.login('parent', 'password123');
  assert.equal(loginRes.success, true);
  ctx = await provider.flush();
  assert.equal(ctx.parentModeEnabled, true);
  assert.equal(ctx.showProfilePicker, true);
  assert.equal(ctx.isKidMode, false);
  assert.equal(ctx.childProfiles.length, 1);

  const epochBeforeSwitch = ctx.sessionEpoch;
  const switchRes = await ctx.selectChildProfile(10);
  assert.equal(switchRes.success, true);
  ctx = await provider.flush();
  assert.equal(ctx.isKidMode, true);
  assert.equal(ctx.activeProfile.name, 'Bé Bông');
  assert.equal(ctx.showProfilePicker, false);
  assert.equal(ctx.token, kidToken);
  assert.ok(ctx.sessionEpoch > epochBeforeSwitch);

  const badExit = await ctx.exitKidMode('9999');
  assert.equal(badExit.success, false);
  assert.match(badExit.message, /Mã PIN/);
  ctx = await provider.flush();
  assert.equal(ctx.isKidMode, true);

  const epochBeforeExit = ctx.sessionEpoch;
  const goodExit = await ctx.exitKidMode('1234');
  assert.equal(goodExit.success, true);
  ctx = await provider.flush();
  assert.equal(ctx.isKidMode, false);
  assert.equal(ctx.activeProfile, null);
  assert.equal(ctx.token, mainToken);
  assert.ok(ctx.sessionEpoch > epochBeforeExit);
  provider.unmount();

  parentEnabled = false;
  const plainProvider = mountAuthProvider(async (url) => {
    if (url.endsWith('/auth/login')) {
      return jsonResponse({
        success: true,
        data: {
          token: mainToken,
          type: 'Bearer',
          id: 2,
          username: 'normal',
          email: 'normal@dippie.vn',
          role: 'ROLE_USER',
        },
      });
    }
    return jsonResponse({
      success: true,
      data: { parentModeEnabled: false, activeProfileId: null, activeProfile: null, profiles: [], availableAvatars: [] },
    });
  });
  let plainCtx = plainProvider.render();
  await plainCtx.login('normal', 'password123');
  plainCtx = await plainProvider.flush();
  assert.equal(plainCtx.parentModeEnabled, false);
  assert.equal(plainCtx.showProfilePicker, false);
  assert.equal(plainCtx.isKidMode, false);
  plainProvider.unmount();
});

test('ProfilePickerModal renders main and child profiles and navigates out of restricted routes on child switch', async () => {
  const navigated = [];
  const switched = [];
  let closed = 0;

  const harness = createComponentHarness(
    '../features/profiles/components/ProfilePickerModal.tsx',
    'ProfilePickerModal',
    {
      'react-router': {
        useLocation: () => ({ pathname: '/account' }),
        useNavigate: () => (to, opts) => navigated.push({ to, opts }),
      },
      '@/lib/labels': { getUserInitials: () => 'BM' },
      '@/lib/context/AuthContext': {
        useAuth: () => ({
          user: { id: 1, username: 'parent', fullName: 'Ba Mẹ', email: 'p@dippie.vn', role: 'ROLE_USER' },
          isAuthenticated: true,
          parentModeEnabled: true,
          childProfiles: [
            { id: 10, name: 'Bé Bông', avatarKey: 'bunny', createdAt: '2026-11-26T10:00:00' },
            { id: 11, name: 'Bé Bi', avatarKey: 'dino', createdAt: '2026-11-26T10:05:00' },
          ],
          activeProfile: null,
          showProfilePicker: true,
          closeProfilePicker: () => { closed += 1; },
          exitKidMode: async (pin) => (pin === '1234' ? { success: true } : { success: false, message: 'Mã PIN phụ huynh chưa đúng.' }),
          selectChildProfile: async (id) => {
            switched.push(id);
            return { success: true };
          },
        }),
      },
    }
  );

  let tree = harness.render();
  assert.ok(collectNodes(tree, (n) => n.props?.['data-testid'] === 'profile-picker-modal').length === 1);
  const mainBtn = collectNodes(tree, (n) => n.props?.['data-testid'] === 'picker-main-profile')[0];
  const kidBtn1 = collectNodes(tree, (n) => n.props?.['data-testid'] === 'picker-child-profile-10')[0];
  const kidBtn2 = collectNodes(tree, (n) => n.props?.['data-testid'] === 'picker-child-profile-11')[0];
  assert.ok(mainBtn && kidBtn1 && kidBtn2);

  kidBtn1.props.onClick();
  await new Promise((resolve) => setTimeout(resolve, 0));
  assert.deepEqual(switched, [10]);
  assert.equal(navigated.length, 1);
  assert.equal(navigated[0].to, '/');

  mainBtn.props.onClick();
  tree = harness.render();
  assert.equal(closed, 0);
  const pinInput = collectNodes(tree, (n) => n.props?.['data-testid'] === 'picker-parent-pin-input')[0];
  assert.ok(pinInput);
  pinInput.props.onChange({ target: { value: '1234' } });
  tree = harness.render();
  await collectNodes(tree, (n) => n.props?.['data-testid'] === 'picker-parent-pin-form')[0].props.onSubmit({ preventDefault() {} });
  assert.equal(closed, 1);
  harness.unmount();
});

test('ExitKidModeDialog requires valid PIN, surfaces error on wrong PIN, and exits on right PIN', async () => {
  const attempts = [];
  let closed = 0;

  const harness = createComponentHarness(
    '../features/profiles/components/ExitKidModeDialog.tsx',
    'ExitKidModeDialog',
    {
      '@/lib/context/AuthContext': {
        useAuth: () => ({
          activeProfile: { id: 10, name: 'Bé Bông', avatarKey: 'bunny', createdAt: '2026-11-26T10:00:00' },
          exitKidMode: async (pin) => {
            attempts.push(pin);
            if (pin === '1234') return { success: true };
            return { success: false, message: 'Mã PIN phụ huynh không đúng' };
          },
        }),
      },
    },
    {},
    new Set(['SiteDialog'])
  );

  let tree = harness.render({ open: true, onClose: () => { closed += 1; } });
  let input = collectNodes(tree, (n) => n.props?.['data-testid'] === 'exit-kid-pin-input')[0];
  assert.ok(input);

  input.props.onChange({ target: { value: '12' } });
  tree = harness.render({ open: true, onClose: () => { closed += 1; } });
  await collectNodes(tree, (n) => n.type === 'form')[0].props.onSubmit({ preventDefault() {} });
  tree = harness.render({ open: true, onClose: () => { closed += 1; } });
  assert.equal(attempts.length, 0);
  assert.ok(collectNodes(tree, (n) => n.props?.['data-testid'] === 'exit-kid-pin-error').length === 1);

  input = collectNodes(tree, (n) => n.props?.['data-testid'] === 'exit-kid-pin-input')[0];
  input.props.onChange({ target: { value: '9999' } });
  tree = harness.render({ open: true, onClose: () => { closed += 1; } });
  await collectNodes(tree, (n) => n.type === 'form')[0].props.onSubmit({ preventDefault() {} });
  tree = harness.render({ open: true, onClose: () => { closed += 1; } });
  assert.deepEqual(attempts, ['9999']);
  assert.equal(closed, 0);
  const errNode = collectNodes(tree, (n) => n.props?.['data-testid'] === 'exit-kid-pin-error')[0];
  assert.match(JSON.stringify(errNode), /Mã PIN phụ huynh không đúng/);

  input = collectNodes(tree, (n) => n.props?.['data-testid'] === 'exit-kid-pin-input')[0];
  input.props.onChange({ target: { value: '1234' } });
  tree = harness.render({ open: true, onClose: () => { closed += 1; } });
  await collectNodes(tree, (n) => n.type === 'form')[0].props.onSubmit({ preventDefault() {} });
  assert.deepEqual(attempts, ['9999', '1234']);
  assert.equal(closed, 1);
  harness.unmount();
});

test('Navbar in kid mode shows child indicator and exit button while hiding account and admin links', () => {
  const authState = {
    isAuthenticated: true,
    user: { id: 1, username: 'admin', fullName: 'Quản trị', email: 'admin@dippie.vn', role: 'ROLE_ADMIN' },
    isKidMode: true,
    parentModeEnabled: true,
    activeProfile: { id: 10, name: 'Bé Bông', avatarKey: 'bunny', createdAt: '2026-11-26T10:00:00' },
    openAuthModal() {},
    logout() {},
    openProfilePicker() {},
    exitKidMode: async () => ({ success: true }),
  };

  const harness = createComponentHarness(
    '../components/layout/Navbar.tsx',
    'Navbar',
    {
      'react-router': { Link: 'route-link', useLocation: () => ({ pathname: '/' }) },
      '@/lib/labels': { getUserInitials: () => 'BB' },
      '@/lib/context/WatchlistContext': { useWatchlist: () => ({ watchlist: [], openChat() {} }) },
      '@/lib/context/AuthContext': { useAuth: () => authState },
    },
    { document: { addEventListener() {}, removeEventListener() {} } }
  );

  let tree = harness.render();
  const banner = collectNodes(tree, (n) => n.props?.['data-testid'] === 'kid-mode-banner')[0];
  assert.ok(banner);
  assert.match(JSON.stringify(banner), /Hồ sơ trẻ em/);
  assert.match(JSON.stringify(banner), /Bé Bông/);
  assert.equal(collectNodes(tree, (n) => n.props?.to === '/admin/reports').length, 0);

  collectNodes(tree, (n) => n.props?.['data-testid'] === 'nav-user-dropdown-button')[0].props.onClick();
  collectNodes(tree, (n) => n.props?.['aria-label'] === 'Mở danh mục')[0].props.onClick();
  tree = harness.render();

  assert.equal(collectNodes(tree, (n) => n.props?.to === '/account').length, 0);
  assert.equal(collectNodes(tree, (n) => typeof n.props?.to === 'string' && n.props.to.startsWith('/admin')).length, 0);
  assert.ok(collectNodes(tree, (n) => n.props?.['data-testid'] === 'nav-exit-kid-mode-menu-item').length === 1);
  assert.ok(collectNodes(tree, (n) => n.props?.['data-testid'] === 'mobile-exit-kid-mode').length === 1);

  collectNodes(tree, (n) => n.props?.['data-testid'] === 'exit-kid-mode-button')[0].props.onClick();
  tree = harness.render();
  assert.ok(collectNodes(tree, (n) => typeof n.type === 'function' && n.type.name === 'ExitKidModeDialog').length === 1);
  harness.unmount();
});

test('AccountSettings, ProfileOverview, and AdminUsersPage restrict settings and admin views in kid mode', () => {
  const kidAuth = {
    user: { id: 1, username: 'admin', fullName: 'Quản trị', email: 'admin@dippie.vn', role: 'ROLE_ADMIN' },
    token: 'kid-token',
    status: 'authenticated',
    isAuthenticated: true,
    isLoading: false,
    isKidMode: true,
    activeProfile: { id: 10, name: 'Bé Bông', avatarKey: 'bunny', createdAt: '2026-11-26T10:00:00' },
    openAuthModal() {},
    retryVerify: async () => {},
    applySession() {},
    updateUser() {},
  };

  const accountHarness = createComponentHarness(
    '../features/account/components/AccountSettings.tsx',
    'AccountSettings',
    {
      'react-router': { Link: 'route-link' },
      '@/lib/context/AuthContext': { useAuth: () => kidAuth },
      '@/lib/services/accountService': {},
      './ChildProfilesSection': { ChildProfilesSection: () => null },
      './DeleteAccountSection': { DeleteAccountSection: () => null },
    }
  );
  const accountTree = accountHarness.render();
  assert.ok(collectNodes(accountTree, (n) => n.props?.['data-testid'] === 'account-kid-mode-blocked').length === 1);
  assert.equal(collectNodes(accountTree, (n) => n.type === 'form').length, 0);
  accountHarness.unmount();

  const profileHarness = createComponentHarness(
    '../features/profile/components/ProfileOverview.tsx',
    'ProfileOverview',
    {
      'react-router': { Link: 'route-link' },
      '@/lib/context/AuthContext': { useAuth: () => kidAuth },
      '@/lib/context/WatchlistContext': {
        useWatchlist: () => ({
          watchlist: [],
          favorites: [],
          ratings: {},
          openChat() {},
          isLibraryLoading: false,
          libraryError: null,
          refetchLibrary: async () => {},
          isGuestMode: false,
          toggleWatchlist: async () => {},
          toggleFavorite: async () => {},
          rateMovie: async () => {},
          removeRating: async () => {},
          isActionPending: () => false,
          isActionUnconfirmed: () => false,
          getActionIntent: () => null,
          getUnconfirmedRating: () => null,
          showToast() {},
        }),
      },
      '@/lib/labels': { genreLabel: (g) => g, getUserInitials: () => 'BB', normalizeGenreLabels: () => [] },
      '@/features/movies/components/MovieCard': { MovieCard: () => null },
      '@/features/profile/useProfileMetadata': {
        clearProfileMetadataCache() {},
        pruneProfileMetadataCache() {},
        useProfileMetadata: () => ({ resolveItem: () => ({ status: 'missing' }), fetchMovie: async () => {} }),
      },
    }
  );
  const profileTree = profileHarness.render();
  assert.equal(collectNodes(profileTree, (n) => n.props?.['data-testid'] === 'profile-account-link').length, 0);
  assert.match(JSON.stringify(profileTree), /Bé Bông/);
  assert.match(JSON.stringify(profileTree), /Hồ sơ trẻ em/);
  profileHarness.unmount();

  let adminFetched = 0;
  const adminHarness = createComponentHarness(
    '../app/admin/users/page.tsx',
    'default',
    {
      '@/lib/context/AuthContext': { useAuth: () => kidAuth },
      '@/lib/services/adminService': {
        listAdminUsers: async () => {
          adminFetched += 1;
          return { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: false };
        },
        updateAdminUserRole: async () => ({}),
        updateAdminUserStatus: async () => ({}),
      },
      '@/features/admin/adminFormat': {
        formatCount: String,
        formatViDate: String,
        formatViDateTime: String,
        getNextPage: () => 0,
        getPagingSummary: () => null,
        getPreviousPage: () => 0,
        getRoleLabel: String,
        getUserStatusLabel: String,
      },
      '@/features/admin/components/AdminNav': { AdminNav: () => null },
      '@/features/admin/components/AdminConfirmDialog': { AdminConfirmDialog: () => null },
    }
  );
  const adminTree = adminHarness.render();
  assert.ok(collectNodes(adminTree, (n) => n.props?.['data-testid'] === 'admin-forbidden').length === 1);
  assert.equal(adminFetched, 0);
  adminHarness.unmount();
});

test('External YouTube links and badges are hidden across player, sidebar, cards, CineBot and YouTubePlayer is sandboxed in kid mode', () => {
  const kidAuthMock = {
    useOptionalAuth: () => ({ isKidMode: true }),
    useAuth: () => ({ status: 'authenticated', user: { id: 1 }, token: 'kid-tok', isKidMode: true }),
  };

  const ytLinkHarness = createComponentHarness(
    '../components/ui/YouTubeLink.tsx',
    'YouTubeLink',
    { '@/lib/context/AuthContext': kidAuthMock }
  );
  assert.equal(
    ytLinkHarness.render({ url: 'https://www.youtube.com/watch?v=SSubOeKMP2c', children: 'Mở trên YouTube' }),
    null
  );
  ytLinkHarness.unmount();

  const oriEpisode = {
    id: 'ep-ori-1',
    title: 'Công chúa Ori tập 1',
    number: 1,
    youtube: { videoId: 'Ori12345678', canEmbed: false },
  };
  const oriMovie = {
    id: 'ep-ori-1',
    title: 'Công chúa Ori tập 1',
    item_type: 'episode',
    series_id: 'ser-ori',
    episode_id: 'ep-ori-1',
    externalOnly: true,
    canEmbed: false,
    youtubeWatchUrl: 'https://www.youtube.com/watch?v=Ori12345678',
    genres: ['animation'],
  };

  const panelHarness = createComponentHarness(
    '../features/series/components/EpisodePlayerPanel.tsx',
    'EpisodePlayerPanel',
    {
      '@/lib/context/AuthContext': kidAuthMock,
      '@/lib/labels': { cleanMediaTitle: (t) => t || '', formatDurationMs: () => '0:00' },
      '@/lib/services/playbackSource': { getUnavailableReasonMessage: () => 'Chỉ xem trên YouTube' },
      '@/features/series/components/PlaybackReportButton': { PlaybackReportButton: () => null },
      '@/features/series/components/YouTubePlayer': { YouTubePlayer: 'youtube-player-stub' },
    }
  );

  const unavailableTree = panelHarness.render({
    seriesTitle: 'Công chúa Ori',
    selectedEpisode: oriEpisode,
    episodeDetail: null,
    playbackSource: {
      kind: 'unavailable',
      reason: 'youtube_embed_disabled',
      canRetryFile: false,
      youtubeWatchUrl: 'https://www.youtube.com/watch?v=Ori12345678',
      youtubeVideoId: 'Ori12345678',
    },
    isLoadingEpisode: false,
    isResolvingStream: false,
    episodeError: null,
    playbackWarning: null,
    onDismissWarning() {},
    isUnmountedDriveFallback: false,
    onRetryOriginalFile() {},
    onRetryEpisode() {},
    streamUrl: '',
    fileRetryCount: 0,
    videoRef: { current: null },
    youtubeSeekRequest: null,
    shouldAutoPlayYoutube: false,
    youtubeHandlers: { onProgress() {}, onEnded() {}, onPlayingChange() {}, onTimeTick() {} },
    videoHandlers: {
      onLoadedMetadata() {}, onLoadedData() {}, onCanPlay() {}, onError() {},
      onTimeUpdate() {}, onPlay() {}, onPlaying() {}, onPause() {}, onSeeking() {}, onEnded() {},
    },
    offerResume: false,
    onResumePlayback() {},
    autoNext: true,
    onAutoNextToggle() {},
    nextCountdown: null,
    nextEpisodeLabel: '',
    hasNextEpisode: false,
    onCancelNextCountdown() {},
    progressError: null,
  });
  assert.equal(collectNodes(unavailableTree, (n) => n.props?.['data-testid'] === 'player-external-badge').length, 0);
  assert.equal(collectNodes(unavailableTree, (n) => n.props?.testId === 'open-on-youtube-link').length, 0);
  assert.match(JSON.stringify(unavailableTree), /Tập này tạm chưa xem được trong hồ sơ trẻ em/);

  const embedTree = panelHarness.render({
    seriesTitle: 'Larva',
    selectedEpisode: { id: 'ep-larva-1', title: 'Larva tập 1' },
    episodeDetail: null,
    playbackSource: {
      kind: 'youtube',
      origin: 'youtube',
      isFallback: false,
      youtube: { videoId: 'SSubOeKMP2c', canEmbed: true },
      embedUrl: 'https://www.youtube-nocookie.com/embed/SSubOeKMP2c',
      watchUrl: 'https://www.youtube.com/watch?v=SSubOeKMP2c',
    },
    isLoadingEpisode: false,
    isResolvingStream: false,
    episodeError: null,
    playbackWarning: null,
    onDismissWarning() {},
    isUnmountedDriveFallback: false,
    onRetryOriginalFile() {},
    onRetryEpisode() {},
    streamUrl: '',
    fileRetryCount: 0,
    videoRef: { current: null },
    youtubeSeekRequest: null,
    shouldAutoPlayYoutube: false,
    youtubeHandlers: { onProgress() {}, onEnded() {}, onPlayingChange() {}, onTimeTick() {} },
    videoHandlers: {
      onLoadedMetadata() {}, onLoadedData() {}, onCanPlay() {}, onError() {},
      onTimeUpdate() {}, onPlay() {}, onPlaying() {}, onPause() {}, onSeeking() {}, onEnded() {},
    },
    offerResume: false,
    onResumePlayback() {},
    autoNext: true,
    onAutoNextToggle() {},
    nextCountdown: null,
    nextEpisodeLabel: '',
    hasNextEpisode: false,
    onCancelNextCountdown() {},
    progressError: null,
  });
  assert.equal(collectNodes(embedTree, (n) => n.props?.testId === 'youtube-external-link').length, 0);
  const ytStub = collectNodes(embedTree, (n) => n.type === 'youtube-player-stub')[0];
  assert.equal(ytStub.props.kidMode, true);
  panelHarness.unmount();

  const sidebarHarness = createComponentHarness(
    '../features/series/components/EpisodeSidebar.tsx',
    'EpisodeSidebar',
    {
      '@/lib/context/AuthContext': kidAuthMock,
      '@/lib/labels': { cleanMediaTitle: (t) => t || '', formatDurationMs: () => '10:00' },
      '@/features/series/seriesBrowseUtils': { foldVietnamese: (s) => s.toLowerCase() },
      '@/lib/services/playbackSource': {
        isEpisodeExternalOnly: () => true,
        resolveExternalYoutubeWatchUrl: () => 'https://www.youtube.com/watch?v=Ori12345678',
      },
      '@/features/library/components/LibraryControls': { QuickSaveButtons: () => null },
    }
  );
  const sidebarTree = sidebarHarness.render({
    orderedSeasons: [],
    hasSeasons: false,
    selectedSeasonId: null,
    seasonEpisodes: [oriEpisode],
    selectedEpisode: oriEpisode,
    previousEpisode: null,
    nextEpisode: null,
    previousEpisodeLabel: 'Trước',
    nextEpisodeLabel: 'Sau',
    episodeQuery: '',
    onEpisodeQueryChange() {},
    onSelectSeason() {},
    onSelectEpisode() {},
    knownExternalEpisodeIds: { 'ep-ori-1': true },
    posterUrl: '',
  });
  assert.equal(collectNodes(sidebarTree, (n) => n.props?.['data-testid'] === 'episode-external-badge').length, 0);
  assert.equal(collectNodes(sidebarTree, (n) => n.props?.testId === 'episode-row-youtube-link').length, 0);
  sidebarHarness.unmount();

  const cardHarness = createComponentHarness(
    '../features/movies/components/MovieCard.tsx',
    'MovieCard',
    {
      'react-router': { Link: 'route-link' },
      '@/lib/context/AuthContext': kidAuthMock,
      '@/lib/labels': {
        cleanMediaTitle: (t) => t || '',
        formatGenreList: () => '',
        formatRuntime: () => '',
        formatYear: () => '',
        resolveRatingSummary: () => ({ hasRating: false, compactLabel: 'Chưa có' }),
      },
      '@/lib/services/mediaRouting': { buildMediaItemHref: () => '/series/ser-ori?episode=ep-ori-1' },
      '@/lib/services/playbackSource': {
        isMediaExternalOnly: () => true,
        resolveExternalYoutubeWatchUrl: () => 'https://www.youtube.com/watch?v=Ori12345678',
      },
      '@/lib/context/WatchlistContext': {
        useWatchlist: () => ({
          isWatchlisted: () => false,
          toggleWatchlist: async () => {},
          isFavorite: () => false,
          toggleFavorite: async () => {},
          isActionPending: () => false,
          isActionUnconfirmed: () => false,
          getActionIntent: () => null,
        }),
      },
    }
  );
  const cardTree = cardHarness.render({ movie: oriMovie });
  assert.equal(collectNodes(cardTree, (n) => n.props?.['data-testid'] === 'movie-external-badge').length, 0);
  assert.equal(collectNodes(cardTree, (n) => n.props?.testId === 'movie-open-youtube-link').length, 0);
  cardHarness.unmount();

  const gridHarness = createComponentHarness(
    '../features/movies/components/FilmShelf.tsx',
    'FilmGrid',
    {
      'react-router': { Link: 'route-link' },
      '@/lib/context/AuthContext': kidAuthMock,
      '@/lib/labels': {
        cleanMediaTitle: (t) => t || '',
        formatYear: () => '',
        movieSynopsis: () => '',
        normalizeGenreLabels: () => [],
        resolveRatingSummary: () => ({ hasRating: false, label: 'Chưa có' }),
      },
      '@/lib/services/mediaRouting': { buildMediaItemHref: () => '/series/ser-ori?episode=ep-ori-1' },
      '@/lib/services/playbackSource': {
        isMediaExternalOnly: () => true,
        resolveExternalYoutubeWatchUrl: () => 'https://www.youtube.com/watch?v=Ori12345678',
      },
      '@/features/series/components/ArtworkImage': { ArtworkImage: () => null },
      '@/features/library/components/LibraryControls': { QuickSaveButtons: () => null },
    }
  );
  const gridTree = gridHarness.render({ movies: [oriMovie] });
  assert.equal(collectNodes(gridTree, (n) => n.props?.['data-testid'] === 'film-external-badge').length, 0);
  assert.equal(collectNodes(gridTree, (n) => n.props?.testId === 'film-open-youtube-link').length, 0);
  gridHarness.unmount();

  const ytPlayerHarness = createComponentHarness(
    '../features/series/components/YouTubePlayer.tsx',
    'YouTubePlayer',
    { '@/lib/context/AuthContext': kidAuthMock },
    { window: { location: { origin: 'http://localhost:3000' } } }
  );
  const iframeTree = ytPlayerHarness.render({
    videoId: 'SSubOeKMP2c',
    src: 'https://www.youtube-nocookie.com/embed/SSubOeKMP2c?rel=0',
    title: 'Larva tập 1',
    kidMode: true,
    onProgress() {},
    onEnded() {},
    onPlayingChange() {},
    onTimeTick() {},
  });
  assert.equal(iframeTree.type, 'iframe');
  assert.equal(iframeTree.props.sandbox, 'allow-scripts allow-same-origin allow-presentation');
  assert.equal(iframeTree.props.sandbox.includes('allow-popups'), false);
  assert.equal(iframeTree.props.sandbox.includes('allow-top-navigation'), false);
  assert.match(iframeTree.props.src, /rel=0/);
  assert.match(iframeTree.props.src, /modestbranding=1/);
  assert.match(iframeTree.props.src, /iv_load_policy=3/);
  assert.match(iframeTree.props.src, /playsinline=1/);
  ytPlayerHarness.unmount();
});

test('YouTubePlayer limits related videos and outbound YouTube links for parent profiles too', () => {
  const harness = createComponentHarness(
    '../features/series/components/YouTubePlayer.tsx',
    'YouTubePlayer',
    { '@/lib/context/AuthContext': { useOptionalAuth: () => ({ isKidMode: false }) } },
    { window: { location: { origin: 'http://localhost:3000' } } }
  );
  const tree = harness.render({
    videoId: 'SSubOeKMP2c',
    src: 'https://www.youtube-nocookie.com/embed/SSubOeKMP2c',
    title: 'Larva tập 1',
    kidMode: false,
    onProgress() {},
    onEnded() {},
    onPlayingChange() {},
    onTimeTick() {},
  });
  assert.equal(tree.type, 'iframe');
  assert.equal(tree.props.sandbox.includes('allow-popups'), false);
  assert.equal(tree.props.sandbox.includes('allow-top-navigation'), false);
  assert.match(tree.props.src, /rel=0/);
  assert.match(tree.props.src, /iv_load_policy=3/);
  harness.unmount();
});
