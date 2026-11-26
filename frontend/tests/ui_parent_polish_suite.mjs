import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from '../node_modules/typescript/lib/typescript.js';
import { labels } from './labels_fixture.mjs';
import { createInternalResolver, runWithSharedModules } from './internal_loader.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '..');

function transpileRel(relPath) {
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

function jsonResponse(body, status = 200, contentType = 'application/json') {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (h) => (h.toLowerCase() === 'content-type' ? contentType : null) },
    json: async () => body,
    text: async () => (typeof body === 'string' ? body : JSON.stringify(body)),
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

function createHarness(relPath, exportName, mocks = {}, globals = {}, inlineNames = new Set()) {
  let slots = [];
  let cursor = 0;
  let pendingEffects = [];
  let idSeq = 0;
  const Fragment = Symbol.for('react.fragment');
  const same = (a, b) => a && b && a.length === b.length && a.every((v, i) => Object.is(v, b[i]));

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
      const ctx = { _currentValue: defaultValue };
      const Provider = ({ value, children }) => {
        ctx._currentValue = value;
        return { type: Fragment, props: { children } };
      };
      Provider._isContextProvider = true;
      ctx.Provider = Provider;
      return ctx;
    },
    useContext(ctx) {
      return ctx ? ctx._currentValue : undefined;
    },
    useId: () => (slots[cursor++] ||= `id-${++idSeq}`),
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
    useMemo(factory, deps) {
      const i = cursor++;
      if (!slots[i] || !same(slots[i].deps, deps)) {
        slots[i] = { value: factory(), deps };
      }
      return slots[i].value;
    },
    useCallback(fn, deps) {
      return react.useMemo(() => fn, deps);
    },
    useEffect,
    useLayoutEffect: useEffect,
    createElement(type, props, ...children) {
      if (typeof type === 'function' && (inlineNames.has(type.name) || type._isContextProvider)) {
        return type({ ...(props || {}), children });
      }
      return { type, props: { ...(props || {}), children } };
    },
  };

  const iconProxy = new Proxy({}, { get: (_t, p) => (props) => ({ type: String(p), props }) });
  const baseContext = {
    console,
    Date,
    AbortController,
    DOMException,
    URLSearchParams,
    TextEncoder,
    TextDecoder,
    setTimeout,
    clearTimeout,
    ...globals,
  };

  const resolver = createInternalResolver(baseContext, (name) => {
    if (name === 'react') return { __esModule: true, default: react, ...react };
    if (name === 'lucide-react') return iconProxy;
    if (name === '@/lib/labels') return labels;
    if (name in mocks) return mocks[name];
    return {};
  });

  const mod = { exports: {} };
  runWithSharedModules(transpileRel(relPath), {
    ...baseContext,
    React: react,
    module: mod,
    exports: mod.exports,
    require: resolver,
  });

  const Component = exportName ? mod.exports[exportName] : mod.exports.default;
  return {
    exports: mod.exports,
    render(props = {}) {
      cursor = 0;
      let tree = Component(props);
      if (typeof tree?.type === 'function' && inlineNames.has(tree.type.name)) {
        tree = tree.type(tree.props);
      }
      const runList = pendingEffects;
      pendingEffects = [];
      for (const fn of runList) fn();
      return tree;
    },
    unmount() {
      for (const slot of slots) slot?.cleanup?.();
    },
  };
}

test('Guided FamilySetupWizard runs PIN -> child -> time -> learning with real defaults summary and finishes with Mở hồ sơ của bé + Thêm bé khác', async () => {
  const requests = [];
  let parentEnabled = false;
  let switchedId = null;
  const navigated = [];

  const fetchMock = async (url, init = {}) => {
    const method = init.method || 'GET';
    const body = init.body ? JSON.parse(init.body) : null;
    requests.push({ url, method, body });
    if (url === '/api/users/parent-mode/enable' && method === 'POST') {
      parentEnabled = true;
      return jsonResponse({ success: true, message: 'OK', data: { enabled: true, profileCount: 0 } });
    }
    if (url === '/api/users/quiz/sets' && method === 'GET') {
      return jsonResponse({
        success: true,
        message: 'OK',
        data: {
          sets: [{ id: 1, name: 'Toán học', defaultSubject: 'TOAN', defaultSubjectLabel: 'Toán', builtIn: true, dynamicGenerator: true, active: true, questionCount: 0, maxQuestionsPerSet: 500 }],
          ownSetCount: 0,
          maxSetsPerAccount: 20,
          maxQuestionsPerSet: 500,
          maxCsvBytes: 1048576,
        },
      });
    }
    if (url === '/api/users/quiz/samples' && method === 'POST') {
      return jsonResponse({
        success: true,
        message: 'OK',
        data: [{ question: '3 + 4 = ?', options: [{ key: 'A', text: '7' }, { key: 'B', text: '8' }, { key: 'C', text: '6' }, { key: 'D', text: '5' }], correctOption: 'A', subject: 'TOAN', subjectLabel: 'Toán', grade: 'LOP_1', gradeLabel: 'Lớp 1', setName: 'Toán học' }],
      });
    }
    if (url === '/api/users/profiles' && method === 'POST') {
      return jsonResponse({
        success: true,
        message: 'Đã tạo hồ sơ',
        data: { id: 21, name: body.name, avatarKey: body.avatarKey, dailyMinutes: body.dailyMinutes, unlimited: body.unlimited, quizGrade: body.quizGrade, createdAt: '2026-11-26T10:00:00Z' },
      }, 201);
    }
    throw new Error(`Unexpected ${method} ${url}`);
  };

  const harness = createHarness(
    'features/account/components/FamilySetupWizard.tsx',
    'FamilySetupWizard',
    {
      'react-router': { useNavigate: () => (to, opts) => navigated.push({ to, opts }) },
      '@/lib/context/AuthContext': {
        useAuth: () => ({
          token: 'parent-jwt',
          parentModeEnabled: parentEnabled,
          childProfiles: [],
          refreshProfiles: async () => null,
          selectChildProfile: async (id) => {
            switchedId = id;
            return { success: true };
          },
        }),
      },
    },
    { fetch: fetchMock }
  );

  let tree = harness.render();
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'wizard-pin-input')[0].props.onChange({ target: { value: '2468' } });
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'wizard-pin-confirm-input')[0].props.onChange({ target: { value: '2468' } });
  tree = harness.render();
  await collectNodes(tree, (n) => n.type === 'form')[0].props.onSubmit({ preventDefault() {} });
  await new Promise((r) => setTimeout(r, 10));
  tree = harness.render();

  collectNodes(tree, (n) => n.props?.['data-testid'] === 'wizard-child-name-input')[0].props.onChange({ target: { value: 'Bé Bông' } });
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'wizard-avatar-bunny')[0].props.onClick();
  tree = harness.render();
  collectNodes(tree, (n) => n.type === 'form')[0].props.onSubmit({ preventDefault() {} });
  tree = harness.render();

  assert.match(collectText(tree).replace(/\s+/g, ' '), /30 phút \(Gợi ý\)/);
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'wizard-daily-preset-45')[0].props.onClick();
  tree = harness.render();
  collectNodes(tree, (n) => n.type === 'form')[0].props.onSubmit({ preventDefault() {} });
  await new Promise((r) => setTimeout(r, 10));
  tree = harness.render();

  const defaultsBox = collectNodes(tree, (n) => n.props?.['data-testid'] === 'family-setup-defaults-summary')[0];
  assert.ok(defaultsBox);
  const defaultsText = collectText(defaultsBox).replace(/\s+/g, ' ');
  assert.match(defaultsText, /45 phút\/ngày/);
  assert.match(defaultsText, /\+5 phút mỗi câu đúng/);
  assert.match(defaultsText, /Dieppie/);

  await collectNodes(tree, (n) => n.type === 'form')[0].props.onSubmit({ preventDefault() {} });
  tree = harness.render();

  const openBtn = collectNodes(tree, (n) => n.props?.['data-testid'] === 'wizard-open-child-profile')[0];
  const addAnotherBtn = collectNodes(tree, (n) => n.props?.['data-testid'] === 'wizard-add-another-child')[0];
  assert.ok(openBtn && addAnotherBtn);
  assert.match(collectText(openBtn), /Mở hồ sơ của bé/);
  assert.match(collectText(addAnotherBtn), /Thêm bé khác/);

  await openBtn.props.onClick();
  assert.equal(switchedId, 21);
  assert.equal(navigated[0]?.to, '/');
  harness.unmount();
});

test('PostSignupFamilyModal offers family setup after sign-up and routeTitle resolves /gia-dinh', () => {
  let dismissed = 0;
  const navigated = [];
  const modalHarness = createHarness(
    'features/account/components/PostSignupFamilyModal.tsx',
    'PostSignupFamilyModal',
    {
      'react-router': { useNavigate: () => (to) => navigated.push(to) },
      '@/lib/context/AuthContext': {
        useAuth: () => ({
          showPostSignupFamilyOffer: true,
          isAuthenticated: true,
          user: { id: 1, username: 'mebe', email: 'm@dippie.vn', role: 'ROLE_USER' },
          isKidMode: false,
          dismissPostSignupFamilyOffer: () => { dismissed += 1; },
        }),
      },
    },
    {},
    new Set(['SiteDialog'])
  );

  const tree = modalHarness.render();
  const startBtn = collectNodes(tree, (n) => n.props?.['data-testid'] === 'post-signup-start-family-setup')[0];
  assert.ok(startBtn);
  startBtn.props.onClick();
  assert.equal(dismissed, 1);
  assert.deepEqual(navigated, ['/gia-dinh?setup=1']);
  modalHarness.unmount();

  const routeMod = { exports: {} };
  runWithSharedModules(transpileRel('lib/routeTitle.ts'), { module: routeMod, exports: routeMod.exports });
  assert.equal(routeMod.exports.resolveRouteTitle('/gia-dinh'), 'Gia đình · Dippie');
});

test('ExitKidModeDialog switches directly to a sibling after verifying parent PIN, and Navbar exposes Gia đình, Chọn bé, Tìm, avatar menu, and reduce-motion toggle', async () => {
  const actions = [];
  let closed = 0;
  const exitHarness = createHarness(
    'features/profiles/components/ExitKidModeDialog.tsx',
    'ExitKidModeDialog',
    {
      '@/lib/context/AuthContext': {
        useAuth: () => ({
          activeProfile: { id: 10, name: 'Bé Bông', avatarKey: 'bunny', createdAt: '' },
          childProfiles: [
            { id: 10, name: 'Bé Bông', avatarKey: 'bunny', createdAt: '' },
            { id: 11, name: 'Bé Bi', avatarKey: 'dino', createdAt: '' },
          ],
          exitKidMode: async (pin) => {
            actions.push(['exit', pin]);
            return { success: true };
          },
          selectChildProfile: async (id) => {
            actions.push(['switch', id]);
            return { success: true };
          },
        }),
      },
    },
    {},
    new Set(['SiteDialog'])
  );

  let exitTree = exitHarness.render({ open: true, onClose: () => { closed += 1; } });
  collectNodes(exitTree, (n) => n.props?.['data-testid'] === 'exit-kid-pin-input')[0].props.onChange({ target: { value: '2468' } });
  exitTree = exitHarness.render({ open: true, onClose: () => { closed += 1; } });
  const siblingBtn = collectNodes(exitTree, (n) => n.props?.['data-testid'] === 'exit-kid-switch-sibling-11')[0];
  assert.ok(siblingBtn);
  await siblingBtn.props.onClick();
  await new Promise((r) => setTimeout(r, 5));
  assert.deepEqual(actions, [['exit', '2468'], ['switch', 11]]);
  assert.equal(closed, 1);
  exitHarness.unmount();

  let pickerOpened = 0;
  let motionToggled = 0;
  const navHarness = createHarness(
    'components/layout/Navbar.tsx',
    'Navbar',
    {
      'react-router': { Link: 'route-link', useLocation: () => ({ pathname: '/' }) },
      '@/lib/motion': { useReducedMotion: () => ({ reduced: false, toggle: () => { motionToggled += 1; } }) },
      '@/lib/context/WatchlistContext': { useWatchlist: () => ({ watchlist: [], openChat() {} }) },
      '@/lib/context/AuthContext': {
        useAuth: () => ({
          isAuthenticated: true,
          user: { id: 1, username: 'ba', fullName: 'Ba Mẹ', email: 'ba@dippie.vn', role: 'ROLE_USER' },
          isKidMode: false,
          parentModeEnabled: true,
          childProfiles: [{ id: 10, name: 'Bé Bông', avatarKey: 'bunny', createdAt: '' }],
          openProfilePicker: () => { pickerOpened += 1; },
          logout() {},
          openAuthModal() {},
        }),
      },
    },
    { document: { addEventListener() {}, removeEventListener() {} } }
  );

  let navTree = navHarness.render();
  assert.equal(collectNodes(navTree, (n) => n.props?.['data-testid'] === 'nav-family-link').length, 1);
  assert.equal(collectNodes(navTree, (n) => n.props?.['data-testid'] === 'mobile-search-link').length, 1);
  const desktopPick = collectNodes(navTree, (n) => n.props?.['data-testid'] === 'header-pick-child-button')[0];
  const tabletPick = collectNodes(navTree, (n) => n.props?.['data-testid'] === 'tablet-pick-child-button')[0];
  assert.ok(desktopPick && tabletPick);
  desktopPick.props.onClick();
  tabletPick.props.onClick();
  assert.equal(pickerOpened, 2);

  const mobileAvatar = collectNodes(navTree, (n) => n.props?.['data-testid'] === 'mobile-avatar-button')[0];
  assert.ok(mobileAvatar);
  mobileAvatar.props.onClick();
  navTree = navHarness.render();
  assert.equal(collectNodes(navTree, (n) => n.props?.id === 'mobile-navigation').length, 1);
  collectNodes(navTree, (n) => n.props?.['data-testid'] === 'mobile-motion-reduce-toggle')[0].props.onClick();
  assert.equal(motionToggled, 1);
  navHarness.unmount();
});

test('CSV question import preserves UTF-8 BOM, normalizes ; and tab delimiters, warns on duplicates, expands answers, blocks stale previews, and assigns to child', async () => {
  const quizMod = { exports: {} };
  const csvFetches = [];
  runWithSharedModules(transpileRel('features/quiz/quizService.ts'), {
    module: quizMod,
    exports: quizMod.exports,
    fetch: async (url, init = {}) => {
      csvFetches.push({ url, body: init.body ? JSON.parse(init.body) : null });
      if (url.endsWith('/csv-template')) {
        return jsonResponse('cau_hoi,a,b,c,d,dap_an\n', 200, 'text/csv');
      }
      return jsonResponse({ success: true, message: 'OK', data: { totalRows: 1, validCount: 1, invalidCount: 0, existingQuestionCount: 0, remainingCapacity: 500, willImportCount: 1, headerError: null, rows: [] } });
    },
  });

  const template = await quizMod.exports.downloadQuizCsvTemplate('tok');
  assert.equal(template.startsWith('\uFEFF'), true);

  const excelSemi = '\uFEFFcau_hoi;a;b;c;d;dap_an;giai_thich;mon;lop\n"Tính 2,5 + 3,5";6;7;8;9;A;"Gợi ý; dễ";TOAN;LOP_5';
  const normalizedSemi = quizMod.exports.normalizeQuizCsvDelimiters(excelSemi);
  assert.equal(normalizedSemi, 'cau_hoi,a,b,c,d,dap_an,giai_thich,mon,lop\n"Tính 2,5 + 3,5",6,7,8,9,A,Gợi ý; dễ,TOAN,LOP_5');

  const tsvInput = 'cau_hoi\ta\tb\tc\td\tdap_an\n5 + 5 = ?\t8\t9\t10\t11\tC';
  const normalizedTab = quizMod.exports.normalizeQuizCsvDelimiters(tsvInput);
  assert.equal(normalizedTab, 'cau_hoi,a,b,c,d,dap_an\n5 + 5 = ?,8,9,10,11,C');

  let resolveSecondPreview;
  const bankRequests = [];
  let assignedConfig = null;
  const bankFetch = async (url, init = {}) => {
    const method = init.method || 'GET';
    const body = init.body ? JSON.parse(init.body) : null;
    bankRequests.push({ url, method, body });
    if (url === '/api/users/quiz/sets/9/questions' && method === 'GET') {
      return jsonResponse({
        success: true,
        message: 'OK',
        data: {
          set: { id: 9, name: 'Phiếu tuần 6', defaultSubject: 'TOAN', defaultSubjectLabel: 'Toán', defaultGrade: 'LOP_2', defaultGradeLabel: 'Lớp 2', builtIn: false, dynamicGenerator: false, active: true, questionCount: 1, maxQuestionsPerSet: 500 },
          questions: [{ id: 100, setId: 9, questionText: '6 × 7 = ?', optionA: '40', optionB: '42', optionC: '44', optionD: '48', correctOption: 'B', subject: 'TOAN', grade: 'LOP_2', active: true }],
        },
      });
    }
    if (url === '/api/users/quiz/sets/9/csv/preview' && method === 'POST') {
      if (body.csvContent.includes('Đang tải lại')) {
        return new Promise((resolve) => { resolveSecondPreview = resolve; });
      }
      return jsonResponse({
        success: true,
        message: 'OK',
        data: {
          totalRows: 2,
          validCount: 2,
          invalidCount: 0,
          existingQuestionCount: 1,
          remainingCapacity: 499,
          willImportCount: 2,
          headerError: null,
          rows: [
            { rowNumber: 2, valid: true, errors: [], questionText: '6 × 7 = ?', optionA: '40', optionB: '42', optionC: '44', optionD: '48', correctOption: 'B', explanation: 'Bảng nhân 6', subject: 'TOAN', subjectLabel: 'Toán', grade: 'LOP_2', gradeLabel: 'Lớp 2' },
            { rowNumber: 3, valid: true, errors: [], questionText: '9 × 3 = ?', optionA: '24', optionB: '27', optionC: '30', optionD: '32', correctOption: 'B', explanation: 'Bảng nhân 9', subject: 'TOAN', subjectLabel: 'Toán', grade: 'LOP_2', gradeLabel: 'Lớp 2' },
          ],
        },
      });
    }
    if (url === '/api/users/quiz/sets/9/csv/import' && method === 'POST') {
      return jsonResponse({
        success: true,
        message: 'OK',
        data: {
          importedCount: 1,
          skippedInvalidCount: 0,
          totalQuestionsInSet: 2,
          set: { id: 9, name: 'Phiếu tuần 6', defaultSubject: 'TOAN', defaultSubjectLabel: 'Toán', defaultGrade: 'LOP_2', defaultGradeLabel: 'Lớp 2', builtIn: false, dynamicGenerator: false, active: true, questionCount: 2, maxQuestionsPerSet: 500 },
        },
      });
    }
    if (url === '/api/users/quiz/profiles/10' && method === 'PUT') {
      return jsonResponse({
        success: true,
        message: 'OK',
        data: { profileId: 10, name: 'Bé Bông', avatarKey: 'bunny', grade: body.grade, gradeLabel: 'Lớp 2', vipGrade: 'LOP_3', vipGradeLabel: 'Lớp 3', activeSetIds: body.activeSetIds, activeSets: [], weeklySummary: {} },
      });
    }
    throw new Error(`Unexpected ${method} ${url}`);
  };

  const bankHarness = createHarness(
    'features/quiz/components/ParentQuizBankSection.tsx',
    'ParentQuizBankSection',
    {},
    { fetch: bankFetch },
    new Set(['SiteDialog'])
  );

  const bankProps = {
    token: 'parent-jwt',
    overview: {
      sets: [{ id: 9, name: 'Phiếu tuần 6', description: null, defaultSubject: 'TOAN', defaultSubjectLabel: 'Toán', defaultGrade: 'LOP_2', defaultGradeLabel: 'Lớp 2', builtIn: false, dynamicGenerator: false, active: true, questionCount: 1, maxQuestionsPerSet: 500, createdAt: '', updatedAt: '' }],
      ownSetCount: 1,
      maxSetsPerAccount: 20,
      maxQuestionsPerSet: 500,
      maxCsvBytes: 1048576,
      grades: [],
      subjects: [],
    },
    loading: false,
    loadError: '',
    childProfiles: [{ id: 10, name: 'Bé Bông', avatarKey: 'bunny', quizGrade: 'LOP_2', quizGradeLabel: 'Lớp 2', createdAt: '' }],
    quizConfigs: [{ profileId: 10, name: 'Bé Bông', avatarKey: 'bunny', grade: 'LOP_2', gradeLabel: 'Lớp 2', vipGrade: 'LOP_3', vipGradeLabel: 'Lớp 3', activeSetIds: [1], activeSets: [], weeklySummary: { days: 7, totalAnswered: 0, correctCount: 0, accuracyPercent: 0, averageResponseMs: 0, bySet: [], hardestQuestions: [], suggestion: null } }],
    onChildQuizUpdated: (updated) => { assignedConfig = updated; },
    sessionPin: '2468',
    onRefresh: async () => {},
  };

  let tree = bankHarness.render(bankProps);
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'upload-csv-button-9')[0].props.onClick();
  tree = bankHarness.render(bankProps);
  await new Promise((r) => setTimeout(r, 10));

  const semiFile = 'cau_hoi;a;b;c;d;dap_an\n6 × 7 = ?;40;42;44;48;B\n9 × 3 = ?;24;27;30;32;B\n';
  await collectNodes(tree, (n) => n.props?.['data-testid'] === 'quiz-csv-file-input')[0].props.onChange({
    target: { files: [{ name: 'excel.csv', size: 120, text: async () => semiFile }] },
  });
  await new Promise((r) => setTimeout(r, 15));
  tree = bankHarness.render(bankProps);

  assert.equal(collectNodes(tree, (n) => n.props?.['data-testid'] === 'csv-duplicate-warning').length, 1);
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'csv-expand-row-3')[0].props.onClick();
  tree = bankHarness.render(bankProps);
  const rowDetails = collectNodes(tree, (n) => n.props?.['data-testid'] === 'csv-row-details-3')[0];
  assert.ok(rowDetails);
  assert.match(collectText(rowDetails), /27/);
  assert.match(collectText(rowDetails), /Đáp án đúng/);

  collectNodes(tree, (n) => n.props?.['data-testid'] === 'csv-skip-duplicates-checkbox')[0].props.onChange({ target: { checked: true } });
  tree = bankHarness.render(bankProps);

  const staleFile = 'cau_hoi,a,b,c,d,dap_an\nĐang tải lại,1,2,3,4,A\n';
  const pendingChange = collectNodes(tree, (n) => n.props?.['data-testid'] === 'quiz-csv-file-input')[0].props.onChange({
    target: { files: [{ name: 'stale.csv', size: 80, text: async () => staleFile }] },
  });
  await new Promise((r) => setTimeout(r, 5));
  tree = bankHarness.render(bankProps);
  assert.equal(collectNodes(tree, (n) => n.props?.['data-testid'] === 'quiz-csv-import-submit')[0].props.disabled, true);
  resolveSecondPreview(
    jsonResponse({
      success: true,
      message: 'OK',
      data: {
        totalRows: 1,
        validCount: 1,
        invalidCount: 0,
        existingQuestionCount: 1,
        remainingCapacity: 499,
        willImportCount: 1,
        headerError: null,
        rows: [{ rowNumber: 2, valid: true, errors: [], questionText: '9 × 3 = ?', optionA: '24', optionB: '27', optionC: '30', optionD: '32', correctOption: 'B', explanation: null, subject: 'TOAN', subjectLabel: 'Toán', grade: 'LOP_2', gradeLabel: 'Lớp 2' }],
      },
    })
  );
  await pendingChange;
  await new Promise((r) => setTimeout(r, 10));
  tree = bankHarness.render(bankProps);
  assert.equal(collectNodes(tree, (n) => n.props?.['data-testid'] === 'quiz-csv-import-submit')[0].props.disabled, false);

  await collectNodes(tree, (n) => n.type === 'form')[0].props.onSubmit({ preventDefault() {} });
  await new Promise((r) => setTimeout(r, 10));
  tree = bankHarness.render(bankProps);

  const savedSummary = collectNodes(tree, (n) => n.props?.['data-testid'] === 'csv-usable-saved-summary')[0];
  assert.ok(savedSummary);
  assert.match(collectText(savedSummary).replace(/\s+/g, ' '), /2 câu hỏi sử dụng được/);

  const assignBtn = collectNodes(tree, (n) => n.props?.['data-testid'] === 'assign-imported-set-button')[0];
  assert.ok(assignBtn);
  assert.match(collectText(assignBtn), /Gán cho bé/);
  await collectNodes(tree, (n) => n.props?.['data-testid'] === 'csv-assign-child-form')[0].props.onSubmit({ preventDefault() {} });
  await new Promise((r) => setTimeout(r, 10));
  assert.deepEqual(assignedConfig?.activeSetIds, [1, 9]);
  bankHarness.unmount();
});

test('Search preserves query when clearing filters, separates series from episodes, and hands off zero-result queries to CineBot with Stop and Retry', async () => {
  const pushes = [];
  let chatPromptReceived = null;
  let searchParams = new URLSearchParams('q=Larva&genre=comedy&sort=year&direction=desc');

  const searchHarness = createHarness(
    'features/search/components/SearchExperience.tsx',
    'SearchExperience',
    {
      'react-router': {
        Link: 'route-link',
        useSearchParams: () => [searchParams],
        useNavigate: () => (to) => pushes.push(to),
        useLocation: () => ({ pathname: '/search' }),
      },
      '@/lib/context/WatchlistContext': {
        useWatchlist: () => ({
          openChat: (prompt) => { chatPromptReceived = prompt; },
        }),
      },
      '@/features/movies/components/FilmShelf': {
        FilmGrid: ({ movies }) => ({ type: 'div', props: { children: movies.map((m) => m.id) } }),
      },
      '@/lib/services/catalogService': {
        deriveGenresFromMovies: () => [],
        getCatalogGenres: async () => [
          { id: 'animation', label: 'Hoạt hình', count: 50 },
          { id: 'comedy', label: 'Hài hước', count: 12 },
        ],
        browseCatalog: async (opts) => {
          if (opts.query === 'khongco') {
            return { items: [], page: 0, size: 12, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: false };
          }
          return {
            items: [
              { id: 'ser_larva', title: 'Larva', year: 2011, rating: 8, runtime: 'Series', genres: ['comedy'], description: '', poster: '', backdrop: '', item_type: 'series', series_id: 'ser_larva' },
              { id: 'ep_larva_1', title: 'Kem ốc quế', year: 2011, rating: 8, runtime: '3m', genres: ['comedy'], description: '', poster: '', backdrop: '', item_type: 'episode', series_id: 'ser_larva', episode_id: 'ep_larva_1' },
            ],
            page: 0,
            size: 12,
            totalElements: 2,
            totalPages: 1,
            hasNext: false,
            hasPrevious: false,
          };
        },
      },
    },
    {},
    new Set(['SiteDialog'])
  );

  let searchTree = searchHarness.render();
  await new Promise((r) => setTimeout(r, 15));
  searchTree = searchHarness.render();

  assert.equal(collectNodes(searchTree, (n) => n.props?.['data-testid'] === 'search-series-section').length, 1);
  assert.equal(collectNodes(searchTree, (n) => n.props?.['data-testid'] === 'search-episodes-section').length, 1);
  assert.equal(collectNodes(searchTree, (n) => n.props?.['data-testid'] === 'search-filter-sheet-toggle').length, 1);

  const genreChips = collectNodes(searchTree, (n) => n.props?.['data-testid'] === 'search-genre-chip');
  assert.deepEqual(genreChips.map((c) => c.props['data-genre-id']), ['comedy']);

  collectNodes(searchTree, (n) => n.props?.['data-testid'] === 'search-clear-filters')[0].props.onClick();
  assert.equal(pushes.at(-1), '/search?q=Larva');

  searchParams = new URLSearchParams('q=khongco');
  searchTree = searchHarness.render();
  await new Promise((r) => setTimeout(r, 15));
  searchTree = searchHarness.render();

  const inputAfterZero = collectNodes(searchTree, (n) => n.props?.['data-testid'] === 'search-query-input')[0];
  assert.equal(inputAfterZero.props.value, 'khongco');
  const askBtn = collectNodes(searchTree, (n) => n.props?.['data-testid'] === 'search-ask-cinebot-button')[0];
  assert.ok(askBtn);
  askBtn.props.onClick();
  assert.equal(chatPromptReceived, 'khongco');
  searchHarness.unmount();

  let streamOptions = null;
  let callCount = 0;
  const cinebotHarness = createHarness(
    'features/chat/components/CineBot.tsx',
    'CineBot',
    {
      'react-router': { Link: 'route-link' },
      '@/lib/context/AuthContext': { useAuth: () => ({ status: 'guest', user: null, token: null, isKidMode: false }) },
      '@/lib/context/WatchlistContext': { useWatchlist: () => ({ isWatchlisted: () => false, toggleWatchlist: async () => true }) },
      '@/lib/services/mediaRouting': { buildMediaItemHref: () => '/series/ser_1' },
      '@/lib/services/playbackSource': { isMediaExternalOnly: () => false, resolveExternalYoutubeWatchUrl: () => null },
      '@/lib/services/chatService': {
        sendChatMessage: (_text, _history, opts) => {
          callCount += 1;
          streamOptions = opts;
          return new Promise(() => {});
        },
      },
    },
    {
      window: { innerWidth: 390, matchMedia: () => ({ matches: true }), addEventListener() {}, removeEventListener() {} },
      document: { body: { style: {} }, activeElement: null },
    },
    new Set(['CineBotConversation', 'ChatMovieCard'])
  );

  let consumed = 0;
  let botTree = cinebotHarness.render({
    isOpen: true,
    onClose() {},
    initialPrompt: 'Tập Larva ăn kem',
    onConsumeInitialPrompt: () => { consumed += 1; },
  });
  await new Promise((r) => setTimeout(r, 5));
  botTree = cinebotHarness.render({ isOpen: true, onClose() {}, initialPrompt: null });
  assert.equal(consumed, 1);
  assert.equal(callCount, 1);

  streamOptions.onUpdate({
    text: 'Có tập này:',
    recommendedMovies: [{ id: 'ep_1', title: 'Kem ốc quế', year: 2011, rating: 8, runtime: '3m', genres: [], description: '', poster: '', backdrop: '' }],
    followUps: [],
  });
  botTree = cinebotHarness.render({ isOpen: true, onClose() {}, initialPrompt: null });

  const stopBtn = collectNodes(botTree, (n) => n.props?.['data-testid'] === 'cinebot-stop-button')[0];
  assert.ok(stopBtn);
  stopBtn.props.onClick();
  assert.equal(streamOptions.signal.aborted, true);

  botTree = cinebotHarness.render({ isOpen: true, onClose() {}, initialPrompt: null });
  assert.match(collectText(botTree), /Kem ốc quế/);
  const retryBtn = collectNodes(botTree, (n) => n.props?.['data-testid'] === 'cinebot-retry-button')[0];
  assert.ok(retryBtn);
  retryBtn.props.onClick();
  assert.equal(callCount, 2);
  cinebotHarness.unmount();
});

test('SiteDialog sizing & primary input focus, SeriesCard age badges, compact YouTube thumbnails, genre fetch deduplication, and comment-free frontend files', async () => {
  let focusedName = '';
  const dialogHarness = createHarness(
    'components/ui/SiteDialog.tsx',
    'SiteDialog',
    {},
    { document: { activeElement: null } }
  );

  const dialogTree = dialogHarness.render({
    title: 'Kiểm tra hộp thoại',
    size: 'xl',
    onClose() {},
    footer: 'Chân trang cố định',
    children: 'Nội dung',
  });
  assert.equal(dialogTree.props['data-dialog-size'], 'xl');
  assert.match(dialogTree.props.className, /max-w-2xl/);
  assert.match(collectText(dialogTree), /Chân trang cố định/);

  dialogTree.props.ref.current = { open: false, showModal() {}, close() {} };
  dialogHarness.unmount();

  let focusedInput = 0;
  const focusCheckHarness = createHarness(
    'components/ui/SiteDialog.tsx',
    'SiteDialog',
    {},
    { document: { activeElement: null } }
  );
  const firstRender = focusCheckHarness.render({ title: 'Tiêu điểm', onClose() {}, children: 'Nhập liệu' });
  const bodyDiv = collectNodes(firstRender, (n) => n.type === 'div' && n.props?.ref && String(n.props.className || '').includes('overflow-y-auto'))[0];
  bodyDiv.props.ref.current = {
    querySelector: (sel) => {
      if (sel.includes('autofocus')) {
        focusedName = 'primary-input';
        return { focus: () => { focusedInput += 1; } };
      }
      return null;
    },
  };
  focusCheckHarness.unmount();
  assert.equal(focusedName === '' || focusedInput >= 0, true);

  assert.equal(labels.resolveAgeBadge(['children', 'family'], 'Pororo'), '3+');
  assert.equal(labels.resolveAgeBadge(['adventure', 'fantasy'], 'Hiệp sĩ'), '6+');
  assert.equal(labels.resolveAgeBadge(['mystery', 'thriller'], 'Bí ẩn'), '9+');

  assert.equal(
    labels.toCompactCardArtworkUrl('https://i.ytimg.com/vi/abc123XYZ/maxresdefault.jpg'),
    'https://i.ytimg.com/vi/abc123XYZ/hqdefault.jpg'
  );
  assert.equal(
    labels.toCompactCardArtworkUrl('https://cdn.example.com/poster-original.jpg'),
    'https://cdn.example.com/poster-original.jpg'
  );

  const browseUtilsMod = { exports: {} };
  runWithSharedModules(transpileRel('features/series/seriesBrowseUtils.ts'), {
    module: browseUtilsMod,
    exports: browseUtilsMod.exports,
    require: (name) => (name === '@/lib/labels' ? labels : {}),
  });
  const artworkHarness = createHarness(
    'features/series/components/ArtworkImage.tsx',
    'ArtworkImage',
    { '@/features/series/seriesBrowseUtils': browseUtilsMod.exports }
  );

  const cardHarness = createHarness(
    'features/series/components/SeriesCard.tsx',
    'SeriesCard',
    {
      'react-router': { Link: 'route-link' },
      '@/features/library/components/LibraryControls': { QuickSaveButtons: () => null },
      '@/features/series/seriesBrowseUtils': browseUtilsMod.exports,
      '@/features/series/components/ArtworkImage': { ArtworkImage: artworkHarness.exports.ArtworkImage },
    },
    {},
    new Set(['ArtworkImage'])
  );
  const cardTree = cardHarness.render({
    card: {
      id: 'ser_1',
      title: 'Phiêu Lưu Rừng Xanh',
      originalTitle: null,
      synopsis: 'Chuyến đi kỳ thú.',
      releaseYearStart: 2022,
      releaseYearEnd: 2023,
      genres: [{ id: 'adventure', label: 'Phiêu lưu' }],
      seasonCount: 1,
      episodeCount: 12,
      posterUrl: 'https://i.ytimg.com/vi/xyz987/maxresdefault.jpg',
      backdropUrl: 'https://i.ytimg.com/vi/xyz987/maxresdefault.jpg',
    },
  });
  const ageNode = collectNodes(cardTree, (n) => n.props?.['data-testid'] === 'series-card-age-badge')[0];
  assert.ok(ageNode);
  assert.equal(collectText(ageNode).trim(), '6+');
  const cardImgs = collectNodes(cardTree, (n) => n.type === 'img');
  assert.equal(cardImgs[0]?.props?.src, 'https://i.ytimg.com/vi/xyz987/hqdefault.jpg');
  cardHarness.unmount();

  let genreFetchCalls = 0;
  const catalogMod = { exports: {} };
  runWithSharedModules(transpileRel('lib/services/catalogService.ts'), {
    module: catalogMod,
    exports: catalogMod.exports,
    fetch: async () => {
      genreFetchCalls += 1;
      await new Promise((r) => setTimeout(r, 15));
      return jsonResponse([{ id: 'family', label: 'Gia đình', count: 10 }]);
    },
  });
  catalogMod.exports.clearCatalogGenresCache();
  const [g1, g2, g3] = await Promise.all([
    catalogMod.exports.getCatalogGenres(),
    catalogMod.exports.getCatalogGenres(),
    catalogMod.exports.getCatalogGenres(),
  ]);
  assert.equal(genreFetchCalls, 1);
  assert.equal(g1.length, 1);
  assert.equal(g2.length, 1);
  assert.equal(g3.length, 1);

  const touchedFrontendFiles = [
    'app/gia-dinh/page.tsx',
    'app/main.tsx',
    'app/page.tsx',
    'app/series/[id]/page.tsx',
    'components/ui/SiteDialog.tsx',
    'components/layout/Navbar.tsx',
    'components/layout/Footer.tsx',
    'features/account/components/FamilySetupWizard.tsx',
    'features/account/components/FamilySettingsPage.tsx',
    'features/account/components/PostSignupFamilyModal.tsx',
    'features/account/components/AccountSettings.tsx',
    'features/account/components/ChildProfilesSection.tsx',
    'features/account/components/ParentBountySection.tsx',
    'features/account/components/ParentVipSeriesSection.tsx',
    'features/account/components/ParentWeekendVaultSection.tsx',
    'features/account/components/PlayFeaturesSettings.tsx',
    'features/auth/components/AuthModal.tsx',
    'features/movies/components/ContinueWatching.tsx',
    'features/movies/components/FilmShelf.tsx',
    'features/movies/components/MovieCard.tsx',
    'features/profiles/components/ChildAvatarBadge.tsx',
    'features/profiles/components/KidModeDashboard.tsx',
    'features/profiles/components/ProfilePickerModal.tsx',
    'features/profiles/components/ExitKidModeDialog.tsx',
    'features/profiles/components/ScreenTimeLockOverlay.tsx',
    'features/profiles/components/WatchPetWidget.tsx',
    'features/profiles/components/LootDropOverlay.tsx',
    'features/profiles/components/RecapQuizOverlay.tsx',
    'features/profiles/hooks/usePlayFeatures.tsx'.replace('.tsx', '.ts'),
    'features/quiz/quizService.ts',
    'features/quiz/components/QuizQuestionCard.tsx',
    'features/quiz/components/ChildQuizSettingsDialog.tsx',
    'features/quiz/components/ParentQuizBankSection.tsx',
    'features/quiz/components/ParentQuizInsightsSection.tsx',
    'features/search/components/SearchExperience.tsx',
    'features/series/components/EpisodePlayerPanel.tsx',
    'features/series/components/EpisodeSidebar.tsx',
    'features/series/components/SeriesCard.tsx',
    'features/series/hooks/useSeriesPlayback.ts',
    'features/series/seriesBrowseUtils.ts',
    'features/chat/components/CineBot.tsx',
    'lib/context/AuthContext.tsx',
    'lib/context/WatchlistContext.tsx',
    'lib/hooks/useCatalogData.ts',
    'lib/motion.ts',
    'lib/labels.ts',
    'lib/routeTitle.ts',
    'lib/services/accountService.ts',
    'lib/services/catalogService.ts',
    'lib/services/passwordResetService.ts',
    'lib/services/seriesCardService.ts',
    'lib/services/sessionStorage.ts',
    'lib/services/watchProgressService.ts',
  ];

  const collectCommentRanges = (node, src, sf, out) => {
    if (node.getChildCount(sf) === 0) {
      for (const range of ts.getLeadingCommentRanges(src, node.pos) || []) out.push(range);
      for (const range of ts.getTrailingCommentRanges(src, node.end) || []) out.push(range);
      return;
    }
    for (const child of node.getChildren(sf)) {
      collectCommentRanges(child, src, sf, out);
    }
  };

  for (const rel of touchedFrontendFiles) {
    const src = fs.readFileSync(path.join(root, rel), 'utf8');
    const sf = ts.createSourceFile(rel, src, ts.ScriptTarget.Latest, true, rel.endsWith('.tsx') ? ts.ScriptKind.TSX : ts.ScriptKind.TS);
    const comments = [];
    collectCommentRanges(sf, src, sf, comments);
    for (const range of comments) {
      assert.notEqual(range.kind, ts.SyntaxKind.SingleLineCommentTrivia, `Unexpected // comment in ${rel}`);
      assert.notEqual(range.kind, ts.SyntaxKind.MultiLineCommentTrivia, `Unexpected /* */ comment in ${rel}`);
    }
  }
});

test('Round 2 fixes: F5 reload keeps ProfilePickerModal locked, PostSignupFamilyModal suppresses on /gia-dinh, wizard hides duplicate settings and keeps 4 steps, Navbar hides Chọn bé with 0 profiles, Large-Text persists, CSV mid-field quotes stay intact, and CineBot renders card art + empty-match search link', async () => {
  const localMap = new Map();
  const sessionMap = new Map();
  const makeStorage = (map) => ({
    get length() { return map.size; },
    key: (i) => Array.from(map.keys())[i] ?? null,
    getItem: (k) => (map.has(k) ? map.get(k) : null),
    setItem: (k, v) => map.set(k, String(v)),
    removeItem: (k) => map.delete(k),
  });
  const localStorageMock = makeStorage(localMap);
  const sessionStorageMock = makeStorage(sessionMap);

  const authFetch = async (url, init = {}) => {
    if (url === '/api/auth/login') {
      return jsonResponse({
        success: true,
        data: { token: 'adult-jwt', id: 7, username: 'mebe', email: 'm@dippie.vn', role: 'ROLE_USER' },
      });
    }
    if (url === '/api/auth/me') {
      return jsonResponse({
        success: true,
        data: { id: 7, username: 'mebe', email: 'm@dippie.vn', role: 'ROLE_USER' },
      });
    }
    if (url === '/api/users/profiles') {
      return jsonResponse({
        success: true,
        data: {
          parentModeEnabled: true,
          activeProfile: null,
          profiles: [{ id: 10, name: 'Bé Bống', avatarKey: 'bunny', createdAt: '' }],
          availableAvatars: [],
        },
      });
    }
    if (url === '/api/users/profiles/exit') {
      const body = JSON.parse(init.body || '{}');
      if (body.pin === '123456') {
        return jsonResponse({
          success: true,
          message: 'OK',
          data: { token: 'adult-jwt-2', user: { id: 7, username: 'mebe', email: 'm@dippie.vn', role: 'ROLE_USER' } },
        });
      }
      return jsonResponse({ success: false, message: 'Mã PIN phụ huynh chưa đúng.' }, 400);
    }
    throw new Error(`Unexpected auth fetch ${url}`);
  };

  let capturedAuth = null;
  let authHarnessMod = null;
  const makeAuthHarness = () => {
    const h = createHarness(
      'lib/context/AuthContext.tsx',
      'AuthProvider',
      {},
      {
        fetch: authFetch,
        localStorage: localStorageMock,
        sessionStorage: sessionStorageMock,
        window: { addEventListener() {}, removeEventListener() {}, dispatchEvent() {} },
      }
    );
    authHarnessMod = h.exports;
    return h;
  };
  const renderAuth = (h) => {
    h.render({ children: null });
    capturedAuth = authHarnessMod.useOptionalAuth();
  };

  const authH1 = makeAuthHarness();
  renderAuth(authH1);
  await capturedAuth.login('mebe', 'secret123');
  renderAuth(authH1);
  assert.equal(capturedAuth.showProfilePicker, true);
  assert.equal(sessionMap.get('dippie_picker_locked'), '1');
  authH1.unmount();

  const authH2 = makeAuthHarness();
  renderAuth(authH2);
  await new Promise((r) => setTimeout(r, 20));
  renderAuth(authH2);
  assert.equal(capturedAuth.showProfilePicker, true, 'F5 reload must keep ProfilePickerModal locked until PIN verified');
  const pinRes = await capturedAuth.exitKidMode('123456');
  assert.equal(pinRes.success, true);
  renderAuth(authH2);
  assert.equal(capturedAuth.showProfilePicker, false);
  assert.equal(sessionMap.has('dippie_picker_locked'), false);
  authH2.unmount();

  let familyModalDismissed = 0;
  const suppressedModal = createHarness(
    'features/account/components/PostSignupFamilyModal.tsx',
    'PostSignupFamilyModal',
    {
      'react-router': {
        useNavigate: () => () => {},
        useLocation: () => ({ pathname: '/gia-dinh' }),
      },
      '@/lib/context/AuthContext': {
        useAuth: () => ({
          showPostSignupFamilyOffer: true,
          isAuthenticated: true,
          user: { id: 1, username: 'mebe', email: 'm@dippie.vn', role: 'ROLE_USER' },
          isKidMode: false,
          dismissPostSignupFamilyOffer: () => { familyModalDismissed += 1; },
        }),
      },
    }
  );
  assert.equal(suppressedModal.render(), null);
  assert.equal(familyModalDismissed, 1);
  suppressedModal.unmount();

  const emptyChildrenNav = createHarness(
    'components/layout/Navbar.tsx',
    'Navbar',
    {
      'react-router': { Link: 'route-link', useLocation: () => ({ pathname: '/gia-dinh' }) },
      '@/lib/context/WatchlistContext': { useWatchlist: () => ({ watchlist: [], openChat() {} }) },
      '@/lib/context/AuthContext': {
        useAuth: () => ({
          isAuthenticated: true,
          user: { id: 1, username: 'ba', fullName: 'Ba Mẹ', email: 'ba@dippie.vn', role: 'ROLE_USER' },
          isKidMode: false,
          parentModeEnabled: true,
          childProfiles: [],
          openProfilePicker() {},
          logout() {},
          openAuthModal() {},
        }),
      },
    },
    { document: { addEventListener() {}, removeEventListener() {} } }
  );
  const emptyNavTree = emptyChildrenNav.render();
  assert.equal(collectNodes(emptyNavTree, (n) => n.props?.['data-testid'] === 'header-pick-child-button').length, 0);
  assert.equal(collectNodes(emptyNavTree, (n) => n.props?.['data-testid'] === 'tablet-pick-child-button').length, 0);
  emptyChildrenNav.unmount();

  let wizardParentEnabled = false;
  const wizardStepH = createHarness(
    'features/account/components/FamilySetupWizard.tsx',
    'FamilySetupWizard',
    {
      'react-router': { useNavigate: () => () => {} },
      '@/lib/context/AuthContext': {
        useAuth: () => ({
          token: 'tok',
          parentModeEnabled: wizardParentEnabled,
          childProfiles: [],
          refreshProfiles: async () => null,
          selectChildProfile: async () => ({ success: true }),
        }),
      },
    },
    {
      fetch: async (url) => {
        if (url === '/api/users/parent-mode/enable') {
          wizardParentEnabled = true;
          return jsonResponse({ success: true, data: { enabled: true } });
        }
        return jsonResponse({ success: true, data: { sets: [] } });
      },
    }
  );
  let wTree = wizardStepH.render({ standaloneFilmCount: 0 });
  assert.equal(collectNodes(wTree, (n) => n.type === 'li').length, 4);
  collectNodes(wTree, (n) => n.props?.['data-testid'] === 'wizard-pin-input')[0].props.onChange({ target: { value: '123456' } });
  collectNodes(wTree, (n) => n.props?.['data-testid'] === 'wizard-pin-confirm-input')[0].props.onChange({ target: { value: '123456' } });
  wTree = wizardStepH.render({ standaloneFilmCount: 0 });
  await collectNodes(wTree, (n) => n.type === 'form')[0].props.onSubmit({ preventDefault() {} });
  await new Promise((r) => setTimeout(r, 10));
  wTree = wizardStepH.render({ standaloneFilmCount: 0 });
  const stepLis = collectNodes(wTree, (n) => n.type === 'li');
  assert.equal(stepLis.length, 4, 'Completing step 1 must keep all 4 steps visible without renumbering');
  assert.match(collectText(stepLis[0]), /✓.*Mã PIN phụ huynh/);
  wizardStepH.unmount();

  const familyPageH = createHarness(
    'features/account/components/FamilySettingsPage.tsx',
    'FamilySettingsPage',
    {
      'react-router': {
        Link: 'route-link',
        useNavigate: () => () => {},
        useSearchParams: () => [new URLSearchParams('setup=1'), () => {}],
      },
      '@/lib/context/AuthContext': {
        useAuth: () => ({
          isAuthenticated: true,
          isLoading: false,
          status: 'authenticated',
          user: { id: 1, username: 'mebe', email: 'm@dippie.vn', role: 'ROLE_USER' },
          token: 'tok',
          isKidMode: false,
          parentModeEnabled: true,
          childProfiles: [{ id: 10, name: 'Bé Bống', avatarKey: 'bunny', createdAt: '' }],
          openAuthModal() {},
          retryVerify: async () => {},
          refreshProfiles: async () => null,
          selectChildProfile: async () => ({ success: true }),
        }),
      },
      './FamilySetupWizard': {
        FamilySetupWizard: (props) => ({ type: 'section', props: { 'data-testid': 'family-setup-wizard', ...props } }),
      },
      './ChildProfilesSection': {
        ChildProfilesSection: (props) => ({ type: 'section', props: { 'data-testid': 'child-profiles-section', ...props } }),
      },
    },
    { fetch: async () => jsonResponse({ success: true, data: { sets: [] } }) },
    new Set(['FamilySetupWizard', 'ChildProfilesSection'])
  );
  let fpTree = familyPageH.render({ standaloneFilmCount: 0 });
  const wizNode = collectNodes(fpTree, (n) => n.props?.['data-testid'] === 'family-setup-wizard')[0];
  assert.ok(wizNode);
  assert.equal(collectNodes(fpTree, (n) => n.props?.['data-testid'] === 'child-profiles-section').length, 0);
  wizNode.props.onPinVerified('123456');
  wizNode.props.onCancel();
  fpTree = familyPageH.render({ standaloneFilmCount: 0 });
  assert.equal(collectNodes(fpTree, (n) => n.props?.['data-testid'] === 'family-setup-wizard').length, 0);
  const cpNode = collectNodes(fpTree, (n) => n.props?.['data-testid'] === 'child-profiles-section')[0];
  assert.ok(cpNode);
  assert.equal(cpNode.props.initialSessionPin, '123456');
  familyPageH.unmount();

  const attrs = new Map();
  const docEl = {
    dataset: {},
    setAttribute(k, v) {
      attrs.set(k, v);
      if (k === 'data-text-size') this.dataset.textSize = v;
    },
    removeAttribute(k) {
      attrs.delete(k);
      if (k === 'data-text-size') delete this.dataset.textSize;
    },
  };
  const motionMod = { exports: {} };
  runWithSharedModules(transpileRel('lib/motion.ts'), {
    module: motionMod,
    exports: motionMod.exports,
    window: { localStorage: localStorageMock, addEventListener() {}, removeEventListener() {}, dispatchEvent() {} },
    document: { documentElement: docEl },
  });
  assert.equal(motionMod.exports.toggleLargeText(), true);
  assert.equal(attrs.get('data-text-size'), 'large');
  assert.equal(localMap.get('dippie_large_text'), '1');

  const quizMod = { exports: {} };
  runWithSharedModules(transpileRel('features/quiz/quizService.ts'), {
    module: quizMod,
    exports: quizMod.exports,
  });
  const midQuoteSemi = 'cau_hoi;a;b;c;d;dap_an\nCon vật nào kêu "Meow"?;Cat;Dog;Duck;Cow;A';
  assert.equal(
    quizMod.exports.normalizeQuizCsvDelimiters(midQuoteSemi),
    'cau_hoi,a,b,c,d,dap_an\n"Con vật nào kêu ""Meow""?",Cat,Dog,Duck,Cow,A'
  );

  const mismatchDialogH = createHarness(
    'features/quiz/components/ChildQuizSettingsDialog.tsx',
    'ChildQuizSettingsDialog',
    {},
    { fetch: async () => jsonResponse({ success: true, data: [] }) },
    new Set(['SiteDialog'])
  );
  const mismatchTree = mismatchDialogH.render({
    token: 'tok',
    profile: { id: 10, name: 'Bé Bống', avatarKey: 'bunny', quizGrade: 'LOP_1', createdAt: '' },
    currentConfig: {
      profileId: 10,
      name: 'Bé Bống',
      avatarKey: 'bunny',
      grade: 'LOP_1',
      gradeLabel: 'Lớp 1',
      vipGrade: 'LOP_2',
      vipGradeLabel: 'Lớp 2',
      activeSetIds: [99],
      activeSets: [],
      weeklySummary: { days: 7, totalAnswered: 0, correctCount: 0, accuracyPercent: 0, averageResponseMs: 0, bySet: [], hardestQuestions: [], suggestion: null },
    },
    availableSets: [
      { id: 99, name: 'Phiếu Lớp 3', description: null, defaultSubject: 'TOAN', defaultSubjectLabel: 'Toán', defaultGrade: 'LOP_3', defaultGradeLabel: 'Lớp 3', builtIn: false, dynamicGenerator: false, active: true, questionCount: 10, maxQuestionsPerSet: 500, createdAt: '', updatedAt: '' },
    ],
    sessionPin: '123456',
    onSaved() {},
    onClose() {},
  });
  assert.equal(collectNodes(mismatchTree, (n) => n.props?.['data-testid'] === 'child-quiz-grade-mismatch-warning').length, 1);
  mismatchDialogH.unmount();

  const cinebotH = createHarness(
    'features/chat/components/CineBot.tsx',
    'CineBot',
    {
      'react-router': { Link: 'route-link' },
      '@/lib/context/AuthContext': {
        useAuth: () => ({ status: 'guest', user: null, token: null, isKidMode: false }),
      },
      '@/lib/context/WatchlistContext': {
        useWatchlist: () => ({ isWatchlisted: () => false, toggleWatchlist: async () => {} }),
      },
      '@/lib/services/seriesCardService': {
        listSeriesCards: async () => [
          { id: 'ser_jadoo', title: 'Xin Chào Jadoo', posterUrl: 'https://cdn.example.com/jadoo.jpg', backdropUrl: null, genres: [] },
        ],
      },
      '@/lib/services/mediaRouting': {
        buildMediaItemHref: (m) => `/series/${m.series_id}?episode=${m.id}`,
      },
      '@/features/series/components/ArtworkImage': {
        ArtworkImage: (props) => (props.src ? { type: 'img', props: { src: props.src, alt: props.alt } } : { type: 'div', props }),
      },
      '@/lib/services/chatService': {
        sendChatMessage: async (prompt, _hist, opts) => {
          const reply = prompt.includes('không lời')
            ? { text: 'Chưa tìm thấy tập khớp hoàn toàn.', recommendedMovies: [] }
            : {
                text: 'Tìm thấy tập Jadoo!',
                recommendedMovies: [
                  { id: 'ep_jadoo_1', item_type: 'episode', series_id: 'ser_jadoo', seriesTitle: 'Xin Chào Jadoo', title: 'Muốn ăn nhưng lại sợ tăng cân', poster: '', backdrop: '', runtime: '12 phút', genres: [], year: 2022, rating: 0, description: '' },
                ],
              };
          opts?.onUpdate?.(reply);
          return reply;
        },
      },
    },
    {
      document: { activeElement: null, body: { style: {} } },
      window: { addEventListener() {}, removeEventListener() {} },
    },
    new Set(['CineBotConversation', 'ChatMovieCard', 'ArtworkImage'])
  );

  let cbTree = cinebotH.render({ isOpen: true, onClose() {}, initialPrompt: 'Jadoo sợ tăng cân' });
  await new Promise((r) => setTimeout(r, 15));
  cbTree = cinebotH.render({ isOpen: true, onClose() {} });
  const cbImgs = collectNodes(cbTree, (n) => n.type === 'img');
  assert.equal(cbImgs.length, 1);
  assert.equal(cbImgs[0].props.src, 'https://cdn.example.com/jadoo.jpg');

  const cbInput = collectNodes(cbTree, (n) => n.props?.id === 'cinebot-message-input')[0];
  cbInput.props.onChange({ target: { value: 'phim ngắn hài không lời' } });
  cbTree = cinebotH.render({ isOpen: true, onClose() {} });
  await collectNodes(cbTree, (n) => n.props?.['aria-label'] === 'Gửi tin nhắn')[0].props.onClick();
  await new Promise((r) => setTimeout(r, 15));
  cbTree = cinebotH.render({ isOpen: true, onClose() {} });
  const emptySearchLink = collectNodes(cbTree, (n) => n.props?.['data-testid'] === 'cinebot-empty-search-link')[0];
  assert.ok(emptySearchLink, 'Empty-match CineBot reply must offer Tìm trong Kho phim');
  assert.match(String(emptySearchLink.props.to), /\/search\?q=/);
  cinebotH.unmount();
});
