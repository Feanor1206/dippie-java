import { runWithSharedModules } from './internal_loader.mjs';
import { labels } from './labels_fixture.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import ts from 'typescript';

function renderComponent(path, exportName, mocks, browser = {}) {
  const source = fs.readFileSync(new URL(path, import.meta.url), 'utf8');
  const code = ts.transpileModule(source, { compilerOptions: {
    module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.React, esModuleInterop: true, target: ts.ScriptTarget.ES2022,
  } }).outputText;
  let slots = [], index = 0, effects = [];
  const react = {
    useState(initial) {
      const i = index++;
      if (!(i in slots)) slots[i] = typeof initial === 'function' ? initial() : initial;
      return [slots[i], (value) => { slots[i] = typeof value === 'function' ? value(slots[i]) : value; }];
    },
    useRef(initial) { const i = index++; return slots[i] ||= { current: initial }; },
    useEffect(run, deps) {
      const i = index++;
      if (!browser.window || slots[i]?.deps?.every((value, j) => Object.is(value, deps[j]))) return;
      const previous = slots[i];
      slots[i] = { deps };
      effects.push(() => { previous?.cleanup?.(); slots[i].cleanup = run(); });
    },
    createElement(type, props, ...children) { return { type, props: { ...props, children } }; },
  };
  const module = { exports: {} };
  runWithSharedModules(code, { URL, ...browser, module, exports: module.exports, require(name) {
    if (name === '@/lib/labels') return labels;
    return name === 'react' ? react : mocks[name] || {};
  } });
  return (props) => {
    index = 0;
    effects = [];
    const tree = module.exports[exportName](props);
    for (const run of effects) run();
    return tree;
  };
}

function nodes(node, predicate) {
  if (!node || typeof node !== 'object') return [];
  if (Array.isArray(node)) return node.flatMap((child) => nodes(child, predicate));
  return [...(predicate(node) ? [node] : []), ...nodes(node.props?.children, predicate)];
}

test('Authenticated mobile navigation offers logout and closes after use', () => {
  let logouts = 0;
  const render = renderComponent('../components/layout/Navbar.tsx', 'Navbar', {
    'react-router': { Link: 'route-link', useLocation: () => ({ pathname: '/' }) },
    '@/lib/context/WatchlistContext': { useWatchlist: () => ({ watchlist: [], openChat() {} }) },
    '@/lib/context/AuthContext': { useAuth: () => ({ isAuthenticated: true, user: { username: 'user' }, logout: () => logouts++ }) },
  });
  let tree = render();
  nodes(tree, (n) => n.props?.['aria-label'] === 'Mở danh mục')[0].props.onClick();
  tree = render();
  const mobileMenu = nodes(tree, (n) => n.props?.id === 'mobile-navigation')[0];
  const logout = nodes(mobileMenu, (n) => n.type === 'button' && n.props?.['data-testid'] === 'mobile-logout')[0];
  assert.ok(logout, 'mobile menu should contain a logout action');
  logout.props.onClick();
  assert.equal(logouts, 1);
  assert.equal(nodes(render(), (n) => n.props?.id === 'mobile-navigation').length, 0);
});

test('Mobile header exposes a visible CineBot launcher button outside the hamburger menu', () => {
  let openedChat = 0;
  const render = renderComponent('../components/layout/Navbar.tsx', 'Navbar', {
    'react-router': { Link: 'route-link', useLocation: () => ({ pathname: '/' }) },
    '@/lib/context/WatchlistContext': { useWatchlist: () => ({ watchlist: [], openChat: () => openedChat++ }) },
    '@/lib/context/AuthContext': { useAuth: () => ({ isAuthenticated: false, user: null, openAuthModal() {}, logout() {} }) },
  });
  const tree = render();
  const launcher = nodes(tree, (n) => n.props?.['data-testid'] === 'mobile-cinebot-launcher')[0];
  assert.ok(launcher, 'Mobile header must show a visible CineBot button without opening the menu');
  assert.ok(launcher.props.className.includes('min-h-[44px]'));
  launcher.props.onClick();
  assert.equal(openedChat, 1);
});

test('Every visible authentication input has a programmatically associated label', () => {
  for (const mode of ['login', 'register']) {
    const render = renderComponent('../features/auth/components/AuthModal.tsx', 'AuthModal', {
      '@/lib/context/AuthContext': { useAuth: () => ({ isAuthModalOpen: true, authModalMode: mode }) },
      '@/lib/context/WatchlistContext': { useWatchlist: () => ({ showToast() {} }) },
    });
    const tree = render();
    const inputs = nodes(tree, (n) => n.type === 'input');
    const labels = nodes(tree, (n) => n.type === 'label');
    for (const input of inputs) {
      assert.ok(input.props.id && labels.some((label) => label.props.htmlFor === input.props.id), `${mode}: ${input.props.type} input needs a label`);
    }
  }
});

test('YouTube destination only becomes an external link after opening the parent warning', () => {
  const render = renderComponent('../components/ui/YouTubeLink.tsx', 'YouTubeLink', {});
  const url = 'https://www.youtube.com/watch?v=SSubOeKMP2c';
  let tree = render({ url, children: 'Mở trên YouTube' });
  assert.equal(nodes(tree, (node) => node.type === 'a').length, 0);
  nodes(tree, (node) => node.type === 'button')[0].props.onClick();
  tree = render({ url, children: 'Mở trên YouTube' });
  assert.match(JSON.stringify(tree), /Bạn sắp rời Dippie để xem trên YouTube/);
  const link = nodes(tree, (node) => node.type === 'a')[0];
  assert.equal(link.props.href, url);
  assert.equal(link.props.rel, 'noopener noreferrer');
  assert.equal(link.props.target, '_blank');
  assert.equal(render({ url: 'javascript:alert(1)' }), null);
  assert.equal(render({ url: 'https://www.youtube.com.example.com/watch?v=x' }), null);
});

test('Offline status follows navigator and online/offline events', () => {
  const listeners = new Map();
  const navigator = { onLine: true };
  const render = renderComponent('../components/layout/OfflineBanner.tsx', 'OfflineBanner', {}, {
    navigator,
    window: {
      addEventListener: (name, listener) => listeners.set(name, listener),
      removeEventListener: (name) => listeners.delete(name),
    },
  });
  assert.equal(render().props.className, 'sr-only');
  navigator.onLine = false;
  listeners.get('offline')();
  const offline = render();
  assert.equal(offline.props.role, 'status');
  assert.match(JSON.stringify(offline), /Đang mất mạng/);
  navigator.onLine = true;
  listeners.get('online')();
  assert.equal(render().props.className, 'sr-only');
});

test('Desktop account menu supports arrow keys, Home/End and Escape focus restoration', () => {
  const document = { activeElement: null };
  const render = renderComponent('../components/layout/Navbar.tsx', 'Navbar', {
    'react-router': { Link: 'route-link', useLocation: () => ({ pathname: '/series/test' }) },
    '@/lib/context/WatchlistContext': { useWatchlist: () => ({ watchlist: [], openChat() {} }) },
    '@/lib/context/AuthContext': { useAuth: () => ({ isAuthenticated: true, user: { username: 'viewer' } }) },
  }, { document });
  let tree = render();
  const trigger = nodes(tree, (node) => node.props?.['aria-haspopup'] === 'menu')[0];
  const opener = { focus: () => { document.activeElement = opener; } };
  trigger.props.ref.current = opener;
  trigger.props.onKeyDown({ key: 'ArrowDown', preventDefault() {} });
  tree = render();
  const menu = nodes(tree, (node) => node.props?.role === 'menu')[0];
  const items = Array.from({ length: 4 }, () => {
    const item = { focus: () => { document.activeElement = item; } };
    return item;
  });
  menu.props.ref.current = { querySelectorAll: () => items };
  document.activeElement = items[0];
  const press = (key) => menu.props.onKeyDown({ key, preventDefault() {} });
  press('ArrowUp');
  assert.equal(document.activeElement, items[3]);
  press('ArrowDown');
  assert.equal(document.activeElement, items[0]);
  press('End');
  assert.equal(document.activeElement, items[3]);
  press('Home');
  assert.equal(document.activeElement, items[0]);
  press('Escape');
  assert.equal(document.activeElement, opener);
  assert.equal(nodes(render(), (node) => node.props?.role === 'menu').length, 0);
  assert.ok(nodes(tree, (node) => node.props?.to === '/series' && node.props?.['aria-current'] === 'page').length);
});

test('Register shows an unchecked required checkbox and both legal links', async () => {
  const render = renderComponent('../features/auth/components/AuthModal.tsx', 'AuthModal', {
    'react-router': { Link: 'route-link' },
    '@/lib/context/AuthContext': { useAuth: () => ({ isAuthModalOpen: true, authModalMode: 'register' }) },
  });
  const tree = render();
  const checkbox = nodes(tree, (node) => node.props?.name === 'acceptedTerms')[0];
  assert.equal(checkbox.props.required, true);
  assert.equal(checkbox.props.checked, false);
  assert.ok(nodes(tree, (node) => node.props?.to === '/dieu-khoan').length);
  assert.ok(nodes(tree, (node) => node.props?.to === '/quyen-rieng-tu').length);
  await nodes(tree, (node) => node.type === 'form')[0].props.onSubmit({ preventDefault() {} });
  assert.ok(nodes(render(), (node) => node.props?.role === 'alert').length);
});

test('Admin confirmation keeps its focus trap installed while submitting and uses the latest cancel callback', () => {
  const listeners = new Map();
  let openerFocused = 0;
  let cancelCalls = 0;
  let keydownRegistrations = 0;
  const opener = { focus: () => { openerFocused++; } };
  const document = { activeElement: opener, body: { style: { overflow: '' } } };
  const render = renderComponent('../features/admin/components/AdminConfirmDialog.tsx', 'AdminConfirmDialog', {}, {
    document,
    window: {
      addEventListener(name, listener) { listeners.set(name, listener); keydownRegistrations++; },
      removeEventListener: (name) => listeners.delete(name),
    },
    setTimeout: () => 1,
    clearTimeout() {},
  });
  const props = { open: true, title: 'Xác nhận', description: 'Mô tả', confirmLabel: 'Lưu', onConfirm() {} };
  render({ ...props, onCancel: () => { cancelCalls += 100; } });
  render({ ...props, isSubmitting: true, onCancel: () => { cancelCalls += 100; } });
  const escape = () => listeners.get('keydown')({ key: 'Escape', preventDefault() {} });
  escape();
  assert.equal(cancelCalls, 0);
  assert.equal(openerFocused, 0);
  render({ ...props, onCancel: () => { cancelCalls++; } });
  escape();
  assert.equal(cancelCalls, 1);
  assert.equal(keydownRegistrations, 1);
  render({ ...props, open: false });
  assert.equal(openerFocused, 1);
  assert.equal(listeners.has('keydown'), false);
});
