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
    text: async () => (typeof body === 'string' ? body : JSON.stringify(body)),
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

const SAMPLE_SETS = [
  {
    id: 1,
    name: 'Toán học',
    description: 'Sinh toán tự động theo lớp',
    defaultSubject: 'TOAN',
    defaultSubjectLabel: 'Toán',
    defaultGrade: null,
    defaultGradeLabel: null,
    builtIn: true,
    dynamicGenerator: true,
    active: true,
    questionCount: 0,
    maxQuestionsPerSet: 500,
    createdAt: '',
    updatedAt: '',
  },
  {
    id: 2,
    name: 'Tiếng Anh từ vựng',
    description: '30 câu từ vựng tiếng Anh',
    defaultSubject: 'TIENG_ANH',
    defaultSubjectLabel: 'Tiếng Anh',
    defaultGrade: null,
    defaultGradeLabel: null,
    builtIn: true,
    dynamicGenerator: false,
    active: true,
    questionCount: 30,
    maxQuestionsPerSet: 500,
    createdAt: '',
    updatedAt: '',
  },
  {
    id: 5,
    name: 'Ôn tập tuần 5',
    description: 'Phiếu ôn tập của cô giáo',
    defaultSubject: 'TIENG_VIET',
    defaultSubjectLabel: 'Tiếng Việt',
    defaultGrade: 'LOP_3',
    defaultGradeLabel: 'Lớp 3',
    builtIn: false,
    dynamicGenerator: false,
    active: true,
    questionCount: 12,
    maxQuestionsPerSet: 500,
    createdAt: '',
    updatedAt: '',
  },
];

const SAMPLE_CHILD_CONFIG = {
  profileId: 10,
  name: 'Bé Bông',
  avatarKey: 'bunny',
  grade: 'LOP_3',
  gradeLabel: 'Lớp 3',
  vipGrade: 'LOP_4',
  vipGradeLabel: 'Lớp 4',
  activeSetIds: [1, 2, 5],
  activeSets: [
    { id: 1, name: 'Toán học', builtIn: true, dynamicGenerator: true, active: true },
    { id: 2, name: 'Tiếng Anh từ vựng', builtIn: true, dynamicGenerator: false, active: true },
    { id: 5, name: 'Ôn tập tuần 5', builtIn: false, dynamicGenerator: false, active: true },
  ],
  weeklySummary: {
    days: 7,
    totalAnswered: 24,
    correctCount: 23,
    accuracyPercent: 96,
    averageResponseMs: 6500,
    bySet: [
      { setId: 1, setName: 'Toán học', answered: 14, correct: 13, accuracyPercent: 93 },
      { setId: 5, setName: 'Ôn tập tuần 5', answered: 10, correct: 10, accuracyPercent: 100 },
    ],
    hardestQuestions: [
      {
        questionText: '7 × 8 = ?',
        setName: 'Toán học',
        subjectLabel: 'Toán',
        gradeLabel: 'Lớp 3',
        attempts: 2,
        wrongCount: 1,
        accuracyPercent: 50,
      },
    ],
    suggestion: {
      direction: 'UP',
      currentGrade: 'LOP_3',
      currentGradeLabel: 'Lớp 3',
      suggestedGrade: 'LOP_4',
      suggestedGradeLabel: 'Lớp 4',
      answeredCount: 24,
      accuracyPercent: 96,
      message: 'Bé làm rất tốt (96% đúng qua 24 câu tuần này)! Ba mẹ có muốn nâng lên Lớp 4 không?',
    },
  },
};

test('quizService validates manual questions, formats child summary lines, and picks vi-VN / en-US speech language', () => {
  const spoken = [];
  let cancelled = 0;
  class FakeUtterance {
    constructor(text) {
      this.text = text;
      this.lang = '';
      this.rate = 1;
    }
  }
  const fakeWindow = {
    speechSynthesis: {
      cancel: () => {
        cancelled += 1;
      },
      speak: (u) => {
        spoken.push(u);
      },
    },
    SpeechSynthesisUtterance: FakeUtterance,
  };

  const harness = createComponentHarness('../features/quiz/quizService.ts', null, {}, { window: fakeWindow, SpeechSynthesisUtterance: FakeUtterance });
  const svc = harness.exports;

  assert.equal(svc.getSpeechLangForSubject('TIENG_ANH'), 'en-US');
  assert.equal(svc.getSpeechLangForSubject('TOAN'), 'vi-VN');
  assert.equal(svc.getSpeechLangForSubject('KHOA_HOC'), 'vi-VN');

  assert.equal(svc.speakQuizQuestion('7 × 8 = ?', 'TOAN'), true);
  assert.equal(svc.speakQuizQuestion('What color is the sky?', 'TIENG_ANH'), true);
  assert.equal(cancelled, 2);
  assert.equal(spoken[0].lang, 'vi-VN');
  assert.equal(spoken[1].lang, 'en-US');

  assert.equal(
    svc.formatChildQuizSummaryLine('Lớp 3', ['Toán học', 'Tiếng Anh', 'Ôn tập tuần 5']),
    'Câu hỏi: Lớp 3 · Toán học, Tiếng Anh, Ôn tập tuần 5'
  );

  assert.equal(
    svc.validateManualQuestionInput({
      questionText: '2 + 3 = ?',
      optionA: '4',
      optionB: '5',
      optionC: '6',
      optionD: '7',
      correctOption: 'B',
    }),
    null
  );
  assert.match(
    svc.validateManualQuestionInput({
      questionText: '2 + 3 = ?',
      optionA: '5',
      optionB: '5',
      optionC: '6',
      optionD: '7',
      correctOption: 'A',
    }),
    /trùng nhau/
  );
});

test('QuizQuestionCard renders 4 big colored buttons, read-aloud button in vi-VN/en-US, and gentle wrong-answer reveal', () => {
  const spoken = [];
  const selected = [];
  let nextClicks = 0;
  class FakeUtterance {
    constructor(text) {
      this.text = text;
      this.lang = '';
      this.rate = 1;
    }
  }
  const fakeWindow = {
    speechSynthesis: {
      cancel: () => {},
      speak: (u) => spoken.push(u),
    },
    SpeechSynthesisUtterance: FakeUtterance,
  };

  const harness = createComponentHarness(
    '../features/quiz/components/QuizQuestionCard.tsx',
    'QuizQuestionCard',
    {},
    { window: fakeWindow, SpeechSynthesisUtterance: FakeUtterance }
  );

  const options = [
    { key: 'A', text: 'Blue' },
    { key: 'B', text: 'Yellow' },
    { key: 'C', text: 'Green' },
    { key: 'D', text: 'Red' },
  ];

  const tree = harness.render({
    question: 'What color is a ripe banana?',
    options,
    subject: 'TIENG_ANH',
    subjectLabel: 'Tiếng Anh',
    gradeLabel: 'Mẫu giáo',
    setName: 'Tiếng Anh từ vựng',
    submitting: false,
    onSelectOption: (key) => selected.push(key),
    onNextQuestion: () => {
      nextClicks += 1;
    },
  });

  const optButtons = ['A', 'B', 'C', 'D'].map(
    (k) => collectNodes(tree, (n) => n.props?.['data-testid'] === `quiz-option-${k}`)[0]
  );
  assert.equal(optButtons.filter(Boolean).length, 4);
  for (const btn of optButtons) {
    assert.ok(String(btn.props.className).includes('min-h-[56px]'));
  }

  const readBtn = collectNodes(tree, (n) => n.props?.['data-testid'] === 'quiz-read-aloud-button')[0];
  assert.ok(readBtn);
  assert.match(collectText(readBtn), /Nghe đọc/);
  readBtn.props.onClick();
  assert.equal(spoken.length, 1);
  assert.equal(spoken[0].lang, 'en-US');
  assert.equal(
    spoken[0].text,
    'What color is a ripe banana?. A: Blue. B: Yellow. C: Green. D: Red'
  );

  optButtons[0].props.onClick();
  assert.deepEqual(selected, []);
  const selectedTree = harness.render({
    question: 'What color is a ripe banana?',
    options,
    subject: 'TIENG_ANH',
    subjectLabel: 'Tiếng Anh',
    gradeLabel: 'Mẫu giáo',
    setName: 'Tiếng Anh từ vựng',
    submitting: false,
    onSelectOption: (key) => selected.push(key),
    onNextQuestion: () => {
      nextClicks += 1;
    },
  });
  assert.equal(
    collectNodes(selectedTree, (n) => n.props?.['data-testid'] === 'quiz-option-A')[0].props['aria-pressed'],
    true
  );
  collectNodes(selectedTree, (n) => n.props?.['data-testid'] === 'quiz-confirm-answer-button')[0].props.onClick();
  assert.deepEqual(selected, ['A']);

  const revealTree = harness.render({
    question: 'What color is a ripe banana?',
    options,
    subject: 'TIENG_ANH',
    subjectLabel: 'Tiếng Anh',
    gradeLabel: 'Mẫu giáo',
    submitting: false,
    wrongReveal: {
      chosenOption: 'A',
      correctOption: 'B',
      correctText: 'Yellow',
      explanation: 'Yellow là màu vàng giống quả chuối chín!',
      message: 'Gần đúng rồi! Đáp án là B: Yellow nè',
    },
    onSelectOption: () => {},
    onNextQuestion: () => {
      nextClicks += 1;
    },
  });

  const feedback = collectNodes(revealTree, (n) => n.props?.['data-testid'] === 'quiz-feedback')[0];
  assert.match(collectText(feedback), /Gần đúng rồi! Đáp án là B: Yellow nè/);
  assert.match(collectText(feedback), /Yellow là màu vàng/);
  assert.equal(collectNodes(revealTree, (n) => n.props?.['data-testid'] === 'quiz-option-A')[0].props.disabled, true);

  collectNodes(revealTree, (n) => n.props?.['data-testid'] === 'quiz-next-question-button')[0].props.onClick();
  assert.equal(nextClicks, 1);
  harness.unmount();
});

test('ChildProfilesSection shows per-child quiz summary line, supports quiz selection on profile create, and opens ChildQuizSettingsDialog', async () => {
  const requests = [];
  const fetchMock = async (url, init = {}) => {
    const method = init.method || 'GET';
    const body = init.body ? JSON.parse(init.body) : null;
    requests.push({ url, method, body });
    if (url === '/api/users/profiles/screen-time' && method === 'GET') {
      return jsonResponse({ success: true, message: 'OK', data: [] });
    }
    if (url === '/api/users/quiz/sets' && method === 'GET') {
      return jsonResponse({
        success: true,
        message: 'OK',
        data: {
          sets: SAMPLE_SETS,
          ownSetCount: 1,
          maxSetsPerAccount: 20,
          maxQuestionsPerSet: 500,
          maxCsvBytes: 1048576,
          grades: [],
          subjects: [],
        },
      });
    }
    if (url === '/api/users/quiz/profiles' && method === 'GET') {
      return jsonResponse({ success: true, message: 'OK', data: [SAMPLE_CHILD_CONFIG] });
    }
    if (url === '/api/users/quiz/samples' && method === 'POST') {
      return jsonResponse({
        success: true,
        message: 'OK',
        data: [
          {
            question: '7 × 8 = ?',
            options: [
              { key: 'A', text: '54' },
              { key: 'B', text: '56' },
              { key: 'C', text: '48' },
              { key: 'D', text: '63' },
            ],
            correctOption: 'B',
            explanation: '7 × 8 = 56',
            subject: 'TOAN',
            subjectLabel: 'Toán',
            grade: 'LOP_3',
            gradeLabel: 'Lớp 3',
            setName: 'Toán học',
          },
        ],
      });
    }
    if (url === '/api/users/profiles' && method === 'POST') {
      return jsonResponse(
        {
          success: true,
          message: 'Đã tạo hồ sơ.',
          data: {
            id: 22,
            name: body.name,
            avatarKey: body.avatarKey,
            dailyMinutes: 60,
            unlimited: false,
            quizGrade: body.quizGrade,
            quizGradeLabel: 'Mẫu giáo',
            createdAt: '',
          },
        },
        201
      );
    }
    throw new Error(`Unexpected ${method} ${url}`);
  };

  const harness = createComponentHarness(
    '../features/account/components/ChildProfilesSection.tsx',
    'ChildProfilesSection',
    {
      'react-router': { useNavigate: () => () => {} },
      '@/lib/context/AuthContext': {
        useAuth: () => ({
          token: 'parent-jwt',
          parentModeEnabled: true,
          childProfiles: [
            { id: 10, name: 'Bé Bông', avatarKey: 'bunny', dailyMinutes: 60, quizGrade: 'LOP_3', quizGradeLabel: 'Lớp 3', createdAt: '' },
          ],
          refreshProfiles: async () => null,
          selectChildProfile: async () => ({ success: true }),
        }),
      },
      '@/features/quiz/components/ParentQuizBankSection': { ParentQuizBankSection: () => null },
      '@/features/quiz/components/ParentQuizInsightsSection': { ParentQuizInsightsSection: () => null },
      '@/features/account/components/ParentBountySection': { ParentBountySection: () => null },
      '@/features/account/components/ParentWeekendVaultSection': { ParentWeekendVaultSection: () => null },
      '@/features/account/components/ParentVipSeriesSection': { ParentVipSeriesSection: () => null },
    },
    { fetch: fetchMock },
    new Set(['SiteDialog'])
  );

  let tree = harness.render();
  await new Promise((r) => setTimeout(r, 15));
  tree = harness.render();

  const summary = collectNodes(tree, (n) => n.props?.['data-testid'] === 'child-quiz-summary-10')[0];
  assert.ok(summary);
  assert.equal(
    collectText(summary).replace(/\s+/g, ' ').trim(),
    'Câu hỏi: Lớp 3 · Toán học, Tiếng Anh từ vựng, Ôn tập tuần 5'
  );
  assert.equal(collectNodes(tree, (n) => n.props?.['data-testid'] === 'edit-child-quiz-button-10').length, 1);

  collectNodes(tree, (n) => n.props?.['data-testid'] === 'add-child-profile-button')[0].props.onClick();
  tree = harness.render();
  await new Promise((r) => setTimeout(r, 15));
  tree = harness.render();

  collectNodes(tree, (n) => n.props?.id === 'child-profile-name')[0].props.onChange({ target: { value: 'Bé Mầm' } });
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'child-profile-grade-select')[0].props.onChange({ target: { value: 'MAU_GIAO' } });
  collectNodes(tree, (n) => n.props?.['data-testid'] === 'create-quiz-set-5')[0].props.onChange();
  collectNodes(tree, (n) => n.props?.id === 'child-profile-pin')[0].props.onChange({ target: { value: '1234' } });
  tree = harness.render();

  const sampleBox = collectNodes(tree, (n) => n.props?.['data-testid'] === 'create-quiz-samples')[0];
  assert.ok(sampleBox);
  assert.match(collectText(sampleBox), /7 × 8 = \?/);

  await collectNodes(tree, (n) => n.type === 'form')[0].props.onSubmit({ preventDefault: () => {} });
  const createReq = requests.find((r) => r.method === 'POST' && r.url === '/api/users/profiles');
  assert.equal(createReq?.body.quizGrade, 'MAU_GIAO');
  assert.deepEqual(createReq?.body.quizSetIds, [1, 2, 5]);
  harness.unmount();
});

test('ParentQuizBankSection previews CSV errors in Vietnamese and imports only valid rows with PIN', async () => {
  const requests = [];
  let refreshed = 0;
  const fetchMock = async (url, init = {}) => {
    const method = init.method || 'GET';
    const body = init.body ? JSON.parse(init.body) : null;
    requests.push({ url, method, body });
    if (url === '/api/users/quiz/sets/5/csv/preview' && method === 'POST') {
      return jsonResponse({
        success: true,
        message: 'OK',
        data: {
          totalRows: 2,
          validCount: 1,
          invalidCount: 1,
          existingQuestionCount: 12,
          remainingCapacity: 488,
          willImportCount: 1,
          headerError: null,
          rows: [
            {
              rowNumber: 2,
              valid: true,
              errors: [],
              questionText: '5 × 6 = ?',
              optionA: '25',
              optionB: '30',
              optionC: '35',
              optionD: '40',
              correctOption: 'B',
              explanation: '5 × 6 = 30',
              subject: 'TOAN',
              subjectLabel: 'Toán',
              grade: 'LOP_3',
              gradeLabel: 'Lớp 3',
            },
            {
              rowNumber: 3,
              valid: false,
              errors: ['Đáp án đúng phải là A, B, C hoặc D.'],
              questionText: 'Câu lỗi',
              optionA: '1',
              optionB: '2',
              optionC: '3',
              optionD: '4',
              correctOption: 'Z',
              explanation: null,
              subject: 'TOAN',
              subjectLabel: 'Toán',
              grade: 'LOP_3',
              gradeLabel: 'Lớp 3',
            },
          ],
        },
      });
    }
    if (url === '/api/users/quiz/sets/5/csv/import' && method === 'POST') {
      return jsonResponse({
        success: true,
        message: 'Đã nhập 1 câu hỏi hợp lệ vào bộ câu hỏi.',
        data: {
          importedCount: 1,
          skippedInvalidCount: 1,
          totalQuestionsInSet: 13,
          set: { ...SAMPLE_SETS[2], questionCount: 13 },
        },
      });
    }
    throw new Error(`Unexpected ${method} ${url}`);
  };

  const harness = createComponentHarness(
    '../features/quiz/components/ParentQuizBankSection.tsx',
    'ParentQuizBankSection',
    {},
    { fetch: fetchMock },
    new Set(['SiteDialog'])
  );

  const props = {
    token: 'parent-jwt',
    overview: {
      sets: SAMPLE_SETS,
      ownSetCount: 1,
      maxSetsPerAccount: 20,
      maxQuestionsPerSet: 500,
      maxCsvBytes: 1048576,
      grades: [],
      subjects: [],
    },
    loading: false,
    loadError: '',
    sessionPin: '2468',
    onRefresh: async () => {
      refreshed += 1;
    },
  };

  let tree = harness.render(props);
  assert.match(collectText(tree), /Ôn tập tuần 5/);
  assert.match(collectText(tree), /Tiếng Anh từ vựng/);

  collectNodes(tree, (n) => n.props?.['data-testid'] === 'upload-csv-button-5')[0].props.onClick();
  tree = harness.render(props);

  const csvText = 'cau_hoi,a,b,c,d,dap_an\n5 × 6 = ?,25,30,35,40,B\nCâu lỗi,1,2,3,4,Z\n';
  const fileInput = collectNodes(tree, (n) => n.props?.['data-testid'] === 'quiz-csv-file-input')[0];
  await fileInput.props.onChange({
    target: {
      files: [{ name: 'tuan5.csv', size: Buffer.byteLength(csvText, 'utf8'), text: async () => csvText }],
    },
  });
  await new Promise((r) => setTimeout(r, 15));
  tree = harness.render(props);

  const previewNode = collectNodes(tree, (n) => n.props?.['data-testid'] === 'quiz-csv-preview-panel')[0];
  assert.ok(previewNode);
  const previewText = collectText(previewNode);
  assert.equal(collectText(collectNodes(tree, (n) => n.props?.['data-testid'] === 'csv-valid-count')[0]).trim(), '1');
  assert.equal(collectText(collectNodes(tree, (n) => n.props?.['data-testid'] === 'csv-invalid-count')[0]).trim(), '1');
  assert.match(previewText, /Đáp án đúng phải là A, B, C hoặc D/);

  await collectNodes(tree, (n) => n.type === 'form')[0].props.onSubmit({ preventDefault: () => {} });
  await new Promise((r) => setTimeout(r, 15));
  tree = harness.render(props);

  const importReq = requests.find((r) => r.url === '/api/users/quiz/sets/5/csv/import');
  assert.equal(importReq?.body.pin, '2468');
  assert.equal(refreshed, 1);
  assert.match(collectText(tree), /Đã nhập 1 câu hợp lệ \(bỏ qua 1 dòng lỗi\)/);
  harness.unmount();
});

test('ParentQuizInsightsSection displays weekly stats, hardest questions, and confirms suggested grade change with PIN', async () => {
  const requests = [];
  let updatedChild = null;
  const fetchMock = async (url, init = {}) => {
    const method = init.method || 'GET';
    const body = init.body ? JSON.parse(init.body) : null;
    requests.push({ url, method, body });
    if (url === '/api/users/quiz/profiles/10/grade' && method === 'POST') {
      return jsonResponse({
        success: true,
        message: 'Đã đổi sang Lớp 4.',
        data: {
          ...SAMPLE_CHILD_CONFIG,
          grade: 'LOP_4',
          gradeLabel: 'Lớp 4',
          vipGrade: 'LOP_5',
          vipGradeLabel: 'Lớp 5',
          weeklySummary: { ...SAMPLE_CHILD_CONFIG.weeklySummary, suggestion: null },
        },
      });
    }
    throw new Error(`Unexpected ${method} ${url}`);
  };

  const harness = createComponentHarness(
    '../features/quiz/components/ParentQuizInsightsSection.tsx',
    'ParentQuizInsightsSection',
    {},
    { fetch: fetchMock },
    new Set(['SiteDialog'])
  );

  const props = {
    token: 'parent-jwt',
    configs: [SAMPLE_CHILD_CONFIG],
    loading: false,
    loadError: '',
    sessionPin: '2468',
    onGradeChanged: (next) => {
      updatedChild = next;
    },
    onRetry: async () => {},
  };

  let tree = harness.render(props);
  const fullText = collectText(tree).replace(/\s+/g, ' ');
  assert.match(fullText, /96\s*%/);
  assert.match(fullText, /7 × 8 = \?/);
  assert.match(fullText, /nâng lên Lớp 4/);

  collectNodes(tree, (n) => n.props?.['data-testid'] === 'confirm-grade-suggestion-button-10')[0].props.onClick();
  tree = harness.render(props);

  await collectNodes(tree, (n) => n.type === 'form')[0].props.onSubmit({ preventDefault: () => {} });
  await new Promise((r) => setTimeout(r, 15));

  const req = requests.find((r) => r.url === '/api/users/quiz/profiles/10/grade');
  assert.deepEqual(req?.body, { pin: '2468', grade: 'LOP_4' });
  assert.equal(updatedChild?.grade, 'LOP_4');
  harness.unmount();
});
