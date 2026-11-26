import {
  NETWORK_ERROR_MESSAGE,
  apiFetch,
  formatRateLimitMessage,
  isLikelyNetworkError,
  withRequestTimeout,
} from '@/lib/services/apiRequest';

const API = '/api';
const DEFAULT_TIMEOUT_MS = 10000;

export const MAX_QUIZ_SETS_PER_ACCOUNT = 20;
export const MAX_QUIZ_QUESTIONS_PER_SET = 500;
export const MAX_QUIZ_CSV_BYTES = 1_048_576;

export type QuizGradeCode = 'MAU_GIAO' | 'LOP_1' | 'LOP_2' | 'LOP_3' | 'LOP_4' | 'LOP_5';
export type QuizSubjectCode = 'TOAN' | 'TIENG_VIET' | 'TIENG_ANH' | 'KHOA_HOC' | 'DO_VUI' | 'KHAC';
export type QuizOptionKey = 'A' | 'B' | 'C' | 'D';

export type QuizOption = {
  key: QuizOptionKey;
  text: string;
};

export type QuizGradeMeta = {
  code: QuizGradeCode;
  label: string;
  description: string;
};

export type QuizSubjectMeta = {
  code: QuizSubjectCode;
  label: string;
};

export const QUIZ_GRADES: readonly QuizGradeMeta[] = [
  { code: 'MAU_GIAO', label: 'Mẫu giáo', description: 'Cộng trừ trong phạm vi 10, từ vựng và đố vui cho bé 5 tuổi' },
  { code: 'LOP_1', label: 'Lớp 1', description: 'Cộng trừ trong phạm vi 20, kiến thức cơ bản lớp 1' },
  { code: 'LOP_2', label: 'Lớp 2', description: 'Cộng trừ trong phạm vi 100 và làm quen phép nhân đơn giản' },
  { code: 'LOP_3', label: 'Lớp 3', description: 'Bảng nhân chia từ 2 đến 9 và kiến thức lớp 3' },
  { code: 'LOP_4', label: 'Lớp 4', description: 'Phép tính số lớn, nhân chia mở rộng và kiến thức lớp 4' },
  { code: 'LOP_5', label: 'Lớp 5', description: 'Biểu thức nhiều bước, tỉ số phần trăm và kiến thức lớp 5' },
];

export const QUIZ_SUBJECTS: readonly QuizSubjectMeta[] = [
  { code: 'TOAN', label: 'Toán' },
  { code: 'TIENG_VIET', label: 'Tiếng Việt' },
  { code: 'TIENG_ANH', label: 'Tiếng Anh' },
  { code: 'KHOA_HOC', label: 'Khoa học' },
  { code: 'DO_VUI', label: 'Đố vui' },
  { code: 'KHAC', label: 'Khác' },
];

export type QuizSet = {
  id: number;
  name: string;
  description: string | null;
  defaultSubject: QuizSubjectCode;
  defaultSubjectLabel: string;
  defaultGrade: QuizGradeCode | null;
  defaultGradeLabel: string | null;
  builtIn: boolean;
  dynamicGenerator: boolean;
  active: boolean;
  questionCount: number;
  maxQuestionsPerSet: number;
  createdAt: string;
  updatedAt: string;
};

export type QuizSetsOverview = {
  sets: QuizSet[];
  ownSetCount: number;
  maxSetsPerAccount: number;
  maxQuestionsPerSet: number;
  maxCsvBytes: number;
  grades: QuizGradeMeta[];
  subjects: QuizSubjectMeta[];
};

export type QuizQuestion = {
  id: number;
  setId: number;
  questionText: string;
  optionA: string;
  optionB: string;
  optionC: string;
  optionD: string;
  correctOption: QuizOptionKey;
  explanation: string | null;
  subject: QuizSubjectCode;
  subjectLabel: string;
  grade: QuizGradeCode;
  gradeLabel: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type QuizSetDetail = {
  set: QuizSet;
  questions: QuizQuestion[];
};

export type QuizCsvRowPreview = {
  rowNumber: number;
  valid: boolean;
  errors: string[];
  questionText: string;
  optionA: string;
  optionB: string;
  optionC: string;
  optionD: string;
  correctOption: string;
  explanation: string | null;
  subject: QuizSubjectCode;
  subjectLabel: string;
  grade: QuizGradeCode;
  gradeLabel: string;
};

export type QuizCsvPreview = {
  totalRows: number;
  validCount: number;
  invalidCount: number;
  existingQuestionCount: number;
  remainingCapacity: number;
  willImportCount: number;
  headerError: string | null;
  rows: QuizCsvRowPreview[];
};

export type QuizCsvImportResult = {
  importedCount: number;
  skippedInvalidCount: number;
  totalQuestionsInSet: number;
  set: QuizSet;
  message: string;
};

export type QuizSampleQuestion = {
  question: string;
  options: QuizOption[];
  correctOption: QuizOptionKey;
  explanation: string | null;
  subject: QuizSubjectCode;
  subjectLabel: string;
  grade: QuizGradeCode;
  gradeLabel: string;
  setName: string;
};

export type ChildQuizSetRef = {
  id: number;
  name: string;
  builtIn: boolean;
  dynamicGenerator: boolean;
  active: boolean;
};

export type QuizSetWeeklyStat = {
  setId: number | null;
  setName: string;
  answered: number;
  correct: number;
  accuracyPercent: number;
};

export type HardQuestionStat = {
  questionText: string;
  setName: string;
  subjectLabel: string;
  gradeLabel: string;
  attempts: number;
  wrongCount: number;
  accuracyPercent: number;
};

export type GradeSuggestion = {
  direction: 'UP' | 'DOWN';
  currentGrade: QuizGradeCode;
  currentGradeLabel: string;
  suggestedGrade: QuizGradeCode;
  suggestedGradeLabel: string;
  answeredCount: number;
  accuracyPercent: number;
  message: string;
};

export type ChildQuizWeeklySummary = {
  days: number;
  totalAnswered: number;
  correctCount: number;
  accuracyPercent: number;
  averageResponseMs: number;
  bySet: QuizSetWeeklyStat[];
  hardestQuestions: HardQuestionStat[];
  suggestion: GradeSuggestion | null;
};

export type ChildQuizConfig = {
  profileId: number;
  name: string;
  avatarKey: string;
  grade: QuizGradeCode;
  gradeLabel: string;
  vipGrade: QuizGradeCode;
  vipGradeLabel: string;
  activeSetIds: number[];
  activeSets: ChildQuizSetRef[];
  weeklySummary: ChildQuizWeeklySummary;
};

export type QuizRequestOptions = {
  baseUrl?: string;
  signal?: AbortSignal;
  timeoutMs?: number;
};

export class QuizApiError extends Error {
  status: number;
  retryAfterSeconds?: number;

  constructor(message: string, status: number, retryAfterSeconds?: number) {
    super(message);
    this.name = 'QuizApiError';
    this.status = status;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

export function getQuizGradeLabel(code?: string | null): string {
  const found = QUIZ_GRADES.find((item) => item.code === code);
  return found ? found.label : 'Lớp 1';
}

export function getQuizSubjectLabel(code?: string | null): string {
  const found = QUIZ_SUBJECTS.find((item) => item.code === code);
  return found ? found.label : 'Khác';
}

export function getSpeechLangForSubject(subject?: string | null): 'en-US' | 'vi-VN' {
  return subject === 'TIENG_ANH' ? 'en-US' : 'vi-VN';
}

export function formatSpeechTextForKids(text: string, lang: 'en-US' | 'vi-VN' = 'vi-VN'): string {
  const trimmed = (text || '').trim();
  if (!trimmed || lang !== 'vi-VN') {
    return trimmed;
  }
  return trimmed
    .replace(/(\d+)\s*\+\s*(\d+)/g, '$1 cộng $2')
    .replace(/(\d+)\s*[-−]\s*(\d+)/g, '$1 trừ $2')
    .replace(/(\d+)\s*[×xX*]\s*(\d+)/g, '$1 nhân $2')
    .replace(/(\d+)\s*[÷:]\s*(\d+)/g, '$1 chia $2')
    .replace(/=\s*\?/g, ' bằng bao nhiêu?')
    .replace(/=/g, ' bằng ')
    .replace(/\s+/g, ' ')
    .trim();
}

export function speakWithWebSpeech(text: string, lang: 'en-US' | 'vi-VN' = 'vi-VN'): boolean {
  const cleanText = (text || '').trim();
  if (!cleanText || typeof window === 'undefined') return false;
  const synth = window.speechSynthesis;
  const UtteranceCtor =
    typeof SpeechSynthesisUtterance === 'function'
      ? SpeechSynthesisUtterance
      : (window as unknown as { SpeechSynthesisUtterance?: typeof SpeechSynthesisUtterance }).SpeechSynthesisUtterance;
  if (!synth || typeof synth.speak !== 'function' || typeof UtteranceCtor !== 'function') {
    return false;
  }
  try {
    if (typeof synth.cancel === 'function') {
      synth.cancel();
    }
    const utterance = new UtteranceCtor(cleanText);
    utterance.lang = lang;
    utterance.rate = 0.92;
    synth.speak(utterance);
    return true;
  } catch {
    return false;
  }
}

export function speakQuizQuestion(text: string, subject?: string | null): boolean {
  const lang = getSpeechLangForSubject(subject);
  return speakWithWebSpeech(formatSpeechTextForKids(text, lang), lang);
}

export function speakQuizQuestionAndOptions(
  question: string,
  options: readonly QuizOption[],
  subject?: string | null
): boolean {
  const lang = getSpeechLangForSubject(subject);
  const cleanQuestion = formatSpeechTextForKids(question, lang);
  if (!cleanQuestion) return false;
  const optionParts = options
    .filter((opt) => Boolean(opt && opt.text))
    .map((opt) =>
      lang === 'en-US'
        ? `${opt.key}: ${opt.text.trim()}`
        : `Đáp án ${opt.key}: ${formatSpeechTextForKids(opt.text, 'vi-VN')}`
    );
  const fullScript = [cleanQuestion, ...optionParts].join('. ');
  return speakWithWebSpeech(fullScript, lang);
}

export function speakQuizOption(key: string, text: string, subject?: string | null): boolean {
  const lang = getSpeechLangForSubject(subject);
  const cleanOption = formatSpeechTextForKids(text, lang);
  if (!cleanOption) return false;
  const script = lang === 'en-US' ? `${key}: ${cleanOption}` : `Đáp án ${key}: ${cleanOption}`;
  return speakWithWebSpeech(script, lang);
}

export function speakVietnamesePrompt(text: string): boolean {
  return speakWithWebSpeech(formatSpeechTextForKids(text, 'vi-VN'), 'vi-VN');
}

export function formatChildQuizSummaryLine(gradeLabel: string, setNames: string[]): string {
  const cleanGrade = gradeLabel || 'Lớp 1';
  if (!setNames.length) {
    return `Câu hỏi: ${cleanGrade} · Toán học`;
  }
  return `Câu hỏi: ${cleanGrade} · ${setNames.join(', ')}`;
}

export function normalizeQuizOptions(raw: unknown): QuizOption[] {
  if (!Array.isArray(raw)) return [];
  const validKeys: QuizOptionKey[] = ['A', 'B', 'C', 'D'];
  return raw
    .filter((item): item is { key: string; text: string } => Boolean(item && typeof item === 'object' && typeof (item as { key?: unknown }).key === 'string' && typeof (item as { text?: unknown }).text === 'string'))
    .map((item) => {
      const upper = item.key.trim().toUpperCase() as QuizOptionKey;
      return validKeys.includes(upper) ? { key: upper, text: item.text } : null;
    })
    .filter((item): item is QuizOption => item !== null);
}

export function validateManualQuestionInput(input: {
  questionText: string;
  optionA: string;
  optionB: string;
  optionC: string;
  optionD: string;
  correctOption: string;
  explanation?: string;
}): string | null {
  const q = input.questionText.trim();
  if (!q) return 'Vui lòng nhập nội dung câu hỏi.';
  if (q.length > 400) return 'Nội dung câu hỏi tối đa 400 ký tự.';
  const opts = [
    ['A', input.optionA.trim()],
    ['B', input.optionB.trim()],
    ['C', input.optionC.trim()],
    ['D', input.optionD.trim()],
  ] as const;
  for (const [label, value] of opts) {
    if (!value) return `Vui lòng nhập đáp án ${label}.`;
    if (value.length > 200) return `Đáp án ${label} tối đa 200 ký tự.`;
  }
  const unique = new Set(opts.map(([, value]) => value.toLowerCase()));
  if (unique.size < 4) {
    return '4 đáp án A, B, C, D không được trùng nhau.';
  }
  if (!['A', 'B', 'C', 'D'].includes(input.correctOption.trim().toUpperCase())) {
    return 'Đáp án đúng phải là A, B, C hoặc D.';
  }
  if (input.explanation && input.explanation.trim().length > 400) {
    return 'Giải thích tối đa 400 ký tự.';
  }
  return null;
}

function authHeaders(token: string, withBody = false): Record<string, string> {
  const headers: Record<string, string> = {
    Accept: 'application/json',
    Authorization: `Bearer ${token.trim()}`,
  };
  if (withBody) {
    headers['Content-Type'] = 'application/json';
  }
  return headers;
}

async function runQuizRequest<T>(
  action: (signal: AbortSignal) => Promise<T>,
  options: QuizRequestOptions = {}
): Promise<T> {
  try {
    return await withRequestTimeout(action, {
      signal: options.signal,
      timeoutMs: options.timeoutMs ?? DEFAULT_TIMEOUT_MS,
    });
  } catch (error) {
    if (isLikelyNetworkError(error)) {
      throw new QuizApiError(NETWORK_ERROR_MESSAGE, 0);
    }
    throw error;
  }
}

async function parseEnvelope(response: Response, fallbackMessage: string): Promise<{ data: any; message: string }> {
  const retryHeader = response.headers?.get?.('Retry-After');
  const retryAfter = retryHeader && /^\d+$/.test(retryHeader) ? parseInt(retryHeader, 10) : undefined;
  let json: any = null;
  try {
    json = await response.json();
  } catch {
    const message =
      response.status === 429
        ? formatRateLimitMessage('', fallbackMessage, retryAfter)
        : fallbackMessage;
    throw new QuizApiError(message, response.status, retryAfter);
  }
  const serverMessage = typeof json?.message === 'string' && json.message.trim() ? json.message.trim() : '';
  if (!response.ok || json?.success !== true) {
    const message =
      response.status === 429
        ? formatRateLimitMessage(serverMessage, fallbackMessage, retryAfter)
        : serverMessage || fallbackMessage;
    throw new QuizApiError(message, response.status, retryAfter);
  }
  return { data: json.data, message: serverMessage };
}

function normalizeQuizSet(raw: any): QuizSet | null {
  if (!raw || typeof raw !== 'object') return null;
  const id = Number(raw.id);
  if (!Number.isSafeInteger(id) || id <= 0) return null;
  return {
    id,
    name: typeof raw.name === 'string' ? raw.name : '',
    description: typeof raw.description === 'string' ? raw.description : null,
    defaultSubject: (typeof raw.defaultSubject === 'string' ? raw.defaultSubject : 'KHAC') as QuizSubjectCode,
    defaultSubjectLabel: typeof raw.defaultSubjectLabel === 'string' ? raw.defaultSubjectLabel : getQuizSubjectLabel(raw.defaultSubject),
    defaultGrade: typeof raw.defaultGrade === 'string' ? (raw.defaultGrade as QuizGradeCode) : null,
    defaultGradeLabel: typeof raw.defaultGradeLabel === 'string' ? raw.defaultGradeLabel : null,
    builtIn: Boolean(raw.builtIn),
    dynamicGenerator: Boolean(raw.dynamicGenerator),
    active: raw.active !== false,
    questionCount: Number(raw.questionCount) || 0,
    maxQuestionsPerSet: Number(raw.maxQuestionsPerSet) || MAX_QUIZ_QUESTIONS_PER_SET,
    createdAt: typeof raw.createdAt === 'string' ? raw.createdAt : '',
    updatedAt: typeof raw.updatedAt === 'string' ? raw.updatedAt : '',
  };
}

function normalizeQuizQuestion(raw: any): QuizQuestion | null {
  if (!raw || typeof raw !== 'object') return null;
  const id = Number(raw.id);
  if (!Number.isSafeInteger(id) || id <= 0) return null;
  return {
    id,
    setId: Number(raw.setId) || 0,
    questionText: typeof raw.questionText === 'string' ? raw.questionText : '',
    optionA: typeof raw.optionA === 'string' ? raw.optionA : '',
    optionB: typeof raw.optionB === 'string' ? raw.optionB : '',
    optionC: typeof raw.optionC === 'string' ? raw.optionC : '',
    optionD: typeof raw.optionD === 'string' ? raw.optionD : '',
    correctOption: (typeof raw.correctOption === 'string' ? raw.correctOption : 'A') as QuizOptionKey,
    explanation: typeof raw.explanation === 'string' ? raw.explanation : null,
    subject: (typeof raw.subject === 'string' ? raw.subject : 'KHAC') as QuizSubjectCode,
    subjectLabel: typeof raw.subjectLabel === 'string' ? raw.subjectLabel : getQuizSubjectLabel(raw.subject),
    grade: (typeof raw.grade === 'string' ? raw.grade : 'LOP_1') as QuizGradeCode,
    gradeLabel: typeof raw.gradeLabel === 'string' ? raw.gradeLabel : getQuizGradeLabel(raw.grade),
    active: raw.active !== false,
    createdAt: typeof raw.createdAt === 'string' ? raw.createdAt : '',
    updatedAt: typeof raw.updatedAt === 'string' ? raw.updatedAt : '',
  };
}

function normalizeChildQuizConfig(raw: any): ChildQuizConfig | null {
  if (!raw || typeof raw !== 'object') return null;
  const profileId = Number(raw.profileId);
  if (!Number.isSafeInteger(profileId) || profileId <= 0) return null;
  const grade = (typeof raw.grade === 'string' ? raw.grade : 'LOP_1') as QuizGradeCode;
  const vipGrade = (typeof raw.vipGrade === 'string' ? raw.vipGrade : 'LOP_2') as QuizGradeCode;
  const activeSets: ChildQuizSetRef[] = Array.isArray(raw.activeSets)
    ? raw.activeSets
        .filter((item: any) => item && Number.isSafeInteger(Number(item.id)))
        .map((item: any) => ({
          id: Number(item.id),
          name: typeof item.name === 'string' ? item.name : '',
          builtIn: Boolean(item.builtIn),
          dynamicGenerator: Boolean(item.dynamicGenerator),
          active: item.active !== false,
        }))
    : [];
  const activeSetIds = Array.isArray(raw.activeSetIds)
    ? raw.activeSetIds.map(Number).filter((id: number) => Number.isSafeInteger(id) && id > 0)
    : activeSets.map((item) => item.id);
  const ws = raw.weeklySummary || {};
  const suggestion: GradeSuggestion | null =
    ws.suggestion && typeof ws.suggestion === 'object'
      ? {
          direction: ws.suggestion.direction === 'DOWN' ? 'DOWN' : 'UP',
          currentGrade: (ws.suggestion.currentGrade || grade) as QuizGradeCode,
          currentGradeLabel: ws.suggestion.currentGradeLabel || getQuizGradeLabel(grade),
          suggestedGrade: (ws.suggestion.suggestedGrade || grade) as QuizGradeCode,
          suggestedGradeLabel: ws.suggestion.suggestedGradeLabel || getQuizGradeLabel(ws.suggestion.suggestedGrade),
          answeredCount: Number(ws.suggestion.answeredCount) || 0,
          accuracyPercent: Number(ws.suggestion.accuracyPercent) || 0,
          message: typeof ws.suggestion.message === 'string' ? ws.suggestion.message : '',
        }
      : null;
  return {
    profileId,
    name: typeof raw.name === 'string' ? raw.name : '',
    avatarKey: typeof raw.avatarKey === 'string' ? raw.avatarKey : 'bear',
    grade,
    gradeLabel: typeof raw.gradeLabel === 'string' ? raw.gradeLabel : getQuizGradeLabel(grade),
    vipGrade,
    vipGradeLabel: typeof raw.vipGradeLabel === 'string' ? raw.vipGradeLabel : getQuizGradeLabel(vipGrade),
    activeSetIds,
    activeSets,
    weeklySummary: {
      days: Number(ws.days) || 7,
      totalAnswered: Number(ws.totalAnswered) || 0,
      correctCount: Number(ws.correctCount) || 0,
      accuracyPercent: Number(ws.accuracyPercent) || 0,
      averageResponseMs: Number(ws.averageResponseMs) || 0,
      bySet: Array.isArray(ws.bySet)
        ? ws.bySet.map((row: any) => ({
            setId: typeof row.setId === 'number' ? row.setId : null,
            setName: typeof row.setName === 'string' ? row.setName : '',
            answered: Number(row.answered) || 0,
            correct: Number(row.correct) || 0,
            accuracyPercent: Number(row.accuracyPercent) || 0,
          }))
        : [],
      hardestQuestions: Array.isArray(ws.hardestQuestions)
        ? ws.hardestQuestions.map((row: any) => ({
            questionText: typeof row.questionText === 'string' ? row.questionText : '',
            setName: typeof row.setName === 'string' ? row.setName : '',
            subjectLabel: typeof row.subjectLabel === 'string' ? row.subjectLabel : '',
            gradeLabel: typeof row.gradeLabel === 'string' ? row.gradeLabel : '',
            attempts: Number(row.attempts) || 0,
            wrongCount: Number(row.wrongCount) || 0,
            accuracyPercent: Number(row.accuracyPercent) || 0,
          }))
        : [],
      suggestion,
    },
  };
}

export async function fetchQuizSetsOverview(
  token: string,
  options: QuizRequestOptions = {}
): Promise<QuizSetsOverview> {
  const url = `${options.baseUrl || API}/users/quiz/sets`;
  return runQuizRequest(
    async (signal) => {
      const response = await apiFetch(url, { method: 'GET', headers: authHeaders(token), signal });
      const { data } = await parseEnvelope(response, 'Chưa tải được ngân hàng câu hỏi.');
      const sets = Array.isArray(data?.sets)
        ? data.sets.map(normalizeQuizSet).filter((item: QuizSet | null): item is QuizSet => item !== null)
        : [];
      return {
        sets,
        ownSetCount: Number(data?.ownSetCount) || sets.filter((s: QuizSet) => !s.builtIn).length,
        maxSetsPerAccount: Number(data?.maxSetsPerAccount) || MAX_QUIZ_SETS_PER_ACCOUNT,
        maxQuestionsPerSet: Number(data?.maxQuestionsPerSet) || MAX_QUIZ_QUESTIONS_PER_SET,
        maxCsvBytes: Number(data?.maxCsvBytes) || MAX_QUIZ_CSV_BYTES,
        grades: Array.isArray(data?.grades) ? data.grades : [...QUIZ_GRADES],
        subjects: Array.isArray(data?.subjects) ? data.subjects : [...QUIZ_SUBJECTS],
      };
    },
    options
  );
}

export async function createQuizSet(
  token: string,
  payload: {
    pin: string;
    name: string;
    description?: string | null;
    defaultSubject?: QuizSubjectCode;
    defaultGrade?: QuizGradeCode | null;
  },
  options: QuizRequestOptions = {}
): Promise<QuizSet> {
  const url = `${options.baseUrl || API}/users/quiz/sets`;
  return runQuizRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'POST',
        headers: authHeaders(token, true),
        body: JSON.stringify(payload),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa tạo được bộ câu hỏi.');
      const normalized = normalizeQuizSet(data);
      if (!normalized) throw new QuizApiError('Dữ liệu bộ câu hỏi chưa hợp lệ.', 502);
      return normalized;
    },
    options
  );
}

export async function updateQuizSet(
  token: string,
  setId: number,
  payload: {
    pin: string;
    name?: string;
    description?: string | null;
    defaultSubject?: QuizSubjectCode;
    defaultGrade?: QuizGradeCode | null;
    active?: boolean;
  },
  options: QuizRequestOptions = {}
): Promise<QuizSet> {
  const url = `${options.baseUrl || API}/users/quiz/sets/${encodeURIComponent(String(setId))}`;
  return runQuizRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'PUT',
        headers: authHeaders(token, true),
        body: JSON.stringify(payload),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa cập nhật được bộ câu hỏi.');
      const normalized = normalizeQuizSet(data);
      if (!normalized) throw new QuizApiError('Dữ liệu bộ câu hỏi chưa hợp lệ.', 502);
      return normalized;
    },
    options
  );
}

export async function deleteQuizSet(
  token: string,
  setId: number,
  pin: string,
  options: QuizRequestOptions = {}
): Promise<void> {
  const url = `${options.baseUrl || API}/users/quiz/sets/${encodeURIComponent(String(setId))}`;
  return runQuizRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'DELETE',
        headers: authHeaders(token, true),
        body: JSON.stringify({ pin }),
        signal,
      });
      if (response.status === 204) return;
      await parseEnvelope(response, 'Chưa xóa được bộ câu hỏi.');
    },
    options
  );
}

export async function fetchQuizSetDetail(
  token: string,
  setId: number,
  options: QuizRequestOptions = {}
): Promise<QuizSetDetail> {
  const url = `${options.baseUrl || API}/users/quiz/sets/${encodeURIComponent(String(setId))}/questions`;
  return runQuizRequest(
    async (signal) => {
      const response = await apiFetch(url, { method: 'GET', headers: authHeaders(token), signal });
      const { data } = await parseEnvelope(response, 'Chưa tải được danh sách câu hỏi.');
      const set = normalizeQuizSet(data?.set);
      if (!set) throw new QuizApiError('Dữ liệu bộ câu hỏi chưa hợp lệ.', 502);
      const questions = Array.isArray(data?.questions)
        ? data.questions.map(normalizeQuizQuestion).filter((item: QuizQuestion | null): item is QuizQuestion => item !== null)
        : [];
      return { set, questions };
    },
    options
  );
}

export async function addQuizQuestion(
  token: string,
  setId: number,
  payload: {
    pin: string;
    questionText: string;
    optionA: string;
    optionB: string;
    optionC: string;
    optionD: string;
    correctOption: QuizOptionKey;
    explanation?: string | null;
    subject: QuizSubjectCode;
    grade: QuizGradeCode;
    active?: boolean;
  },
  options: QuizRequestOptions = {}
): Promise<QuizQuestion> {
  const url = `${options.baseUrl || API}/users/quiz/sets/${encodeURIComponent(String(setId))}/questions`;
  return runQuizRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'POST',
        headers: authHeaders(token, true),
        body: JSON.stringify(payload),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa thêm được câu hỏi.');
      const normalized = normalizeQuizQuestion(data);
      if (!normalized) throw new QuizApiError('Dữ liệu câu hỏi chưa hợp lệ.', 502);
      return normalized;
    },
    options
  );
}

export async function updateQuizQuestion(
  token: string,
  setId: number,
  questionId: number,
  payload: {
    pin: string;
    questionText?: string;
    optionA?: string;
    optionB?: string;
    optionC?: string;
    optionD?: string;
    correctOption?: QuizOptionKey;
    explanation?: string | null;
    subject?: QuizSubjectCode;
    grade?: QuizGradeCode;
    active?: boolean;
  },
  options: QuizRequestOptions = {}
): Promise<QuizQuestion> {
  const url = `${options.baseUrl || API}/users/quiz/sets/${encodeURIComponent(String(setId))}/questions/${encodeURIComponent(String(questionId))}`;
  return runQuizRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'PUT',
        headers: authHeaders(token, true),
        body: JSON.stringify(payload),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa cập nhật được câu hỏi.');
      const normalized = normalizeQuizQuestion(data);
      if (!normalized) throw new QuizApiError('Dữ liệu câu hỏi chưa hợp lệ.', 502);
      return normalized;
    },
    options
  );
}

export async function deleteQuizQuestion(
  token: string,
  setId: number,
  questionId: number,
  pin: string,
  options: QuizRequestOptions = {}
): Promise<void> {
  const url = `${options.baseUrl || API}/users/quiz/sets/${encodeURIComponent(String(setId))}/questions/${encodeURIComponent(String(questionId))}`;
  return runQuizRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'DELETE',
        headers: authHeaders(token, true),
        body: JSON.stringify({ pin }),
        signal,
      });
      if (response.status === 204) return;
      await parseEnvelope(response, 'Chưa xóa được câu hỏi.');
    },
    options
  );
}

function countUnquotedDelimiter(line: string, delimiter: string): number {
  let count = 0;
  let inQuotes = false;
  for (let i = 0; i < line.length; i += 1) {
    const ch = line[i];
    if (ch === '"') {
      if (inQuotes && line[i + 1] === '"') {
        i += 1;
      } else {
        inQuotes = !inQuotes;
      }
    } else if (!inQuotes && ch === delimiter) {
      count += 1;
    }
  }
  return count;
}

function parseDelimitedRecords(content: string, delimiter: string): string[][] {
  const rows: string[][] = [];
  let currentRow: string[] = [];
  let currentField = '';
  let inQuotes = false;

  for (let i = 0; i < content.length; i += 1) {
    const ch = content[i];
    if (inQuotes) {
      if (ch === '"') {
        if (content[i + 1] === '"') {
          currentField += '"';
          i += 1;
        } else {
          inQuotes = false;
        }
      } else {
        currentField += ch;
      }
      continue;
    }

    if (ch === '"') {
      inQuotes = true;
    } else if (ch === delimiter) {
      currentRow.push(currentField);
      currentField = '';
    } else if (ch === '\r') {
      if (content[i + 1] === '\n') i += 1;
      currentRow.push(currentField);
      rows.push(currentRow);
      currentRow = [];
      currentField = '';
    } else if (ch === '\n') {
      currentRow.push(currentField);
      rows.push(currentRow);
      currentRow = [];
      currentField = '';
    } else {
      currentField += ch;
    }
  }

  if (currentField.length > 0 || currentRow.length > 0) {
    currentRow.push(currentField);
    rows.push(currentRow);
  }
  return rows;
}

function serializeCommaCsvRow(fields: string[]): string {
  return fields
    .map((field) => {
      if (field.includes(',') || field.includes('"') || field.includes('\n') || field.includes('\r')) {
        return `"${field.replace(/"/g, '""')}"`;
      }
      return field;
    })
    .join(',');
}

export function detectQuizCsvDelimiter(rawCsv: string): ',' | ';' | '\t' {
  const clean = rawCsv.startsWith('\uFEFF') ? rawCsv.slice(1) : rawCsv;
  const firstLineMatch = clean.split(/\r?\n/).find((line) => line.trim().length > 0) ?? '';
  const commas = countUnquotedDelimiter(firstLineMatch, ',');
  const semicolons = countUnquotedDelimiter(firstLineMatch, ';');
  const tabs = countUnquotedDelimiter(firstLineMatch, '\t');
  if (semicolons >= 5 && semicolons > commas && semicolons >= tabs) return ';';
  if (tabs >= 5 && tabs > commas) return '\t';
  return ',';
}

export function normalizeQuizCsvDelimiters(rawCsv: string): string {
  if (!rawCsv) return '';
  const hadBom = rawCsv.startsWith('\uFEFF');
  const clean = hadBom ? rawCsv.slice(1) : rawCsv;
  const delimiter = detectQuizCsvDelimiter(clean);
  if (delimiter === ',') {
    return clean;
  }
  const records = parseDelimitedRecords(clean, delimiter);
  return records.map(serializeCommaCsvRow).join('\n');
}

export function filterQuizCsvRows(rawCsv: string, excludedRowNumbers: ReadonlySet<number>): string {
  const normalized = normalizeQuizCsvDelimiters(rawCsv);
  if (excludedRowNumbers.size === 0) return normalized;
  const records = parseDelimitedRecords(normalized, ',');
  const kept = records.filter((_, idx) => !excludedRowNumbers.has(idx + 1));
  return kept.map(serializeCommaCsvRow).join('\n');
}

export async function downloadQuizCsvTemplate(
  token: string,
  options: QuizRequestOptions = {}
): Promise<string> {
  const url = `${options.baseUrl || API}/users/quiz/csv-template`;
  return runQuizRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${token.trim()}`,
          Accept: 'text/csv, application/json',
        },
        signal,
      });
      if (!response.ok) {
        throw new QuizApiError('Chưa tải được tệp mẫu CSV.', response.status);
      }
      const text = await response.text();
      return text.startsWith('\uFEFF') ? text : `\uFEFF${text}`;
    },
    options
  );
}

export async function previewQuizCsv(
  token: string,
  setId: number,
  csvContent: string,
  options: QuizRequestOptions = {}
): Promise<QuizCsvPreview> {
  const url = `${options.baseUrl || API}/users/quiz/sets/${encodeURIComponent(String(setId))}/csv/preview`;
  const normalizedContent = normalizeQuizCsvDelimiters(csvContent);
  return runQuizRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'POST',
        headers: authHeaders(token, true),
        body: JSON.stringify({ csvContent: normalizedContent }),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa kiểm tra được tệp CSV.');
      return {
        totalRows: Number(data?.totalRows) || 0,
        validCount: Number(data?.validCount) || 0,
        invalidCount: Number(data?.invalidCount) || 0,
        existingQuestionCount: Number(data?.existingQuestionCount) || 0,
        remainingCapacity: Number(data?.remainingCapacity) || 0,
        willImportCount: Number(data?.willImportCount) || 0,
        headerError: typeof data?.headerError === 'string' && data.headerError ? data.headerError : null,
        rows: Array.isArray(data?.rows) ? data.rows : [],
      };
    },
    options
  );
}

export async function importQuizCsv(
  token: string,
  setId: number,
  pin: string,
  csvContent: string,
  options: QuizRequestOptions = {}
): Promise<QuizCsvImportResult> {
  const url = `${options.baseUrl || API}/users/quiz/sets/${encodeURIComponent(String(setId))}/csv/import`;
  const normalizedContent = normalizeQuizCsvDelimiters(csvContent);
  return runQuizRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'POST',
        headers: authHeaders(token, true),
        body: JSON.stringify({ pin, csvContent: normalizedContent }),
        signal,
      });
      const { data, message } = await parseEnvelope(response, 'Chưa nhập được tệp CSV.');
      const set = normalizeQuizSet(data?.set);
      if (!set) throw new QuizApiError('Dữ liệu bộ câu hỏi sau khi nhập chưa hợp lệ.', 502);
      return {
        importedCount: Number(data?.importedCount) || 0,
        skippedInvalidCount: Number(data?.skippedInvalidCount) || 0,
        totalQuestionsInSet: Number(data?.totalQuestionsInSet) || set.questionCount,
        set,
        message: message || `Đã nhập ${Number(data?.importedCount) || 0} câu hỏi hợp lệ.`,
      };
    },
    options
  );
}

export async function fetchQuizSampleQuestions(
  token: string,
  grade: QuizGradeCode,
  setIds: number[],
  options: QuizRequestOptions = {}
): Promise<QuizSampleQuestion[]> {
  const url = `${options.baseUrl || API}/users/quiz/samples`;
  return runQuizRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'POST',
        headers: authHeaders(token, true),
        body: JSON.stringify({ grade, setIds }),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa tải được câu hỏi mẫu.');
      if (!Array.isArray(data)) return [];
      return data.map((item: any) => ({
        question: typeof item?.question === 'string' ? item.question : '',
        options: normalizeQuizOptions(item?.options),
        correctOption: (typeof item?.correctOption === 'string' ? item.correctOption : 'A') as QuizOptionKey,
        explanation: typeof item?.explanation === 'string' ? item.explanation : null,
        subject: (typeof item?.subject === 'string' ? item.subject : 'KHAC') as QuizSubjectCode,
        subjectLabel: typeof item?.subjectLabel === 'string' ? item.subjectLabel : getQuizSubjectLabel(item?.subject),
        grade: (typeof item?.grade === 'string' ? item.grade : grade) as QuizGradeCode,
        gradeLabel: typeof item?.gradeLabel === 'string' ? item.gradeLabel : getQuizGradeLabel(item?.grade),
        setName: typeof item?.setName === 'string' ? item.setName : '',
      }));
    },
    options
  );
}

export async function fetchChildQuizConfigs(
  token: string,
  options: QuizRequestOptions = {}
): Promise<ChildQuizConfig[]> {
  const url = `${options.baseUrl || API}/users/quiz/profiles`;
  return runQuizRequest(
    async (signal) => {
      const response = await apiFetch(url, { method: 'GET', headers: authHeaders(token), signal });
      const { data } = await parseEnvelope(response, 'Chưa tải được cài đặt câu hỏi của các bé.');
      if (!Array.isArray(data)) return [];
      return data
        .map(normalizeChildQuizConfig)
        .filter((item: ChildQuizConfig | null): item is ChildQuizConfig => item !== null);
    },
    options
  );
}

export async function updateChildQuizSettings(
  token: string,
  profileId: number,
  payload: { pin: string; grade: QuizGradeCode; activeSetIds: number[] },
  options: QuizRequestOptions = {}
): Promise<ChildQuizConfig> {
  const url = `${options.baseUrl || API}/users/quiz/profiles/${encodeURIComponent(String(profileId))}`;
  return runQuizRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'PUT',
        headers: authHeaders(token, true),
        body: JSON.stringify(payload),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa lưu được cài đặt câu hỏi cho bé.');
      const normalized = normalizeChildQuizConfig(data);
      if (!normalized) throw new QuizApiError('Dữ liệu cài đặt câu hỏi chưa hợp lệ.', 502);
      return normalized;
    },
    options
  );
}

export async function confirmChildGradeChange(
  token: string,
  profileId: number,
  pin: string,
  grade: QuizGradeCode,
  options: QuizRequestOptions = {}
): Promise<ChildQuizConfig> {
  const url = `${options.baseUrl || API}/users/quiz/profiles/${encodeURIComponent(String(profileId))}/grade`;
  return runQuizRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'POST',
        headers: authHeaders(token, true),
        body: JSON.stringify({ pin, grade }),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa đổi được khối lớp cho bé.');
      const normalized = normalizeChildQuizConfig(data);
      if (!normalized) throw new QuizApiError('Dữ liệu khối lớp chưa hợp lệ.', 502);
      return normalized;
    },
    options
  );
}
