import {
  NETWORK_ERROR_MESSAGE,
  apiFetch,
  formatRateLimitMessage,
  isLikelyNetworkError,
  withRequestTimeout,
} from '@/lib/services/apiRequest';
import {
  normalizeQuizOptions,
  type QuizOption,
  type QuizOptionKey,
} from '@/features/quiz/quizService';

const API = '/api';
const DEFAULT_TIMEOUT_MS = 8000;

export type ChildProfileVipSeries = {
  profileId: number;
  name: string;
  avatarKey: string;
  quizGrade: string;
  quizGradeLabel: string;
  vipQuizGrade: string;
  vipQuizGradeLabel: string;
  seriesIds: string[];
};

export type KidVipStatus = {
  profileId: number | null;
  day: string;
  quizGrade: string | null;
  quizGradeLabel: string | null;
  vipQuizGrade: string | null;
  vipQuizGradeLabel: string | null;
  vipSeriesIds: string[];
  unlockedEpisodeIds: string[];
};

export type VipChallenge = {
  token: string;
  episodeId: string;
  seriesId: string;
  question: string;
  options: QuizOption[];
  subject: string;
  subjectLabel: string;
  grade: string;
  gradeLabel: string;
  setName: string;
  expiresAt: string;
  expiresInSeconds: number;
};

export type VipUnlockResult = {
  correct: boolean;
  unlocked: boolean;
  episodeId: string;
  day: string;
  correctOption: QuizOptionKey | null;
  correctText: string | null;
  explanation: string | null;
  message: string;
};

export type VipRequestOptions = {
  baseUrl?: string;
  signal?: AbortSignal;
  timeoutMs?: number;
};

export class VipApiError extends Error {
  status: number;
  retryAfterSeconds?: number;

  constructor(message: string, status: number, retryAfterSeconds?: number) {
    super(message);
    this.name = 'VipApiError';
    this.status = status;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

function normalizeOptionKey(raw: unknown): QuizOptionKey | null {
  if (typeof raw !== 'string') return null;
  const upper = raw.trim().toUpperCase();
  if (upper === 'A' || upper === 'B' || upper === 'C' || upper === 'D') return upper;
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

async function runVipRequest<T>(
  action: (signal: AbortSignal) => Promise<T>,
  options: VipRequestOptions = {}
): Promise<T> {
  try {
    return await withRequestTimeout(action, {
      signal: options.signal,
      timeoutMs: options.timeoutMs ?? DEFAULT_TIMEOUT_MS,
    });
  } catch (error) {
    if (isLikelyNetworkError(error)) {
      throw new VipApiError(NETWORK_ERROR_MESSAGE, 0);
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
    throw new VipApiError(message, response.status, retryAfter);
  }
  const serverMessage = typeof json?.message === 'string' && json.message.trim() ? json.message.trim() : '';
  if (!response.ok || json?.success !== true) {
    const message =
      response.status === 429
        ? formatRateLimitMessage(serverMessage, fallbackMessage, retryAfter)
        : serverMessage || fallbackMessage;
    throw new VipApiError(message, response.status, retryAfter);
  }
  return { data: json.data, message: serverMessage };
}

function normalizeParentItem(raw: any): ChildProfileVipSeries | null {
  if (!raw || typeof raw !== 'object') return null;
  const profileId = Number(raw.profileId);
  if (!Number.isSafeInteger(profileId) || profileId <= 0) return null;
  const seriesIds = Array.isArray(raw.seriesIds)
    ? raw.seriesIds.filter((item: unknown): item is string => typeof item === 'string' && item.trim().length > 0)
    : [];
  const quizGrade = typeof raw.quizGrade === 'string' && raw.quizGrade.trim() ? raw.quizGrade.trim() : 'LOP_1';
  const quizGradeLabel =
    typeof raw.quizGradeLabel === 'string' && raw.quizGradeLabel.trim() ? raw.quizGradeLabel.trim() : 'Lớp 1';
  const vipQuizGrade =
    typeof raw.vipQuizGrade === 'string' && raw.vipQuizGrade.trim() ? raw.vipQuizGrade.trim() : 'LOP_2';
  const vipQuizGradeLabel =
    typeof raw.vipQuizGradeLabel === 'string' && raw.vipQuizGradeLabel.trim()
      ? raw.vipQuizGradeLabel.trim()
      : 'Lớp 2';
  return {
    profileId,
    name: typeof raw.name === 'string' ? raw.name.trim() : '',
    avatarKey: typeof raw.avatarKey === 'string' ? raw.avatarKey.trim() : 'bear',
    quizGrade,
    quizGradeLabel,
    vipQuizGrade,
    vipQuizGradeLabel,
    seriesIds,
  };
}

export async function fetchParentVipSeriesOverview(
  token: string,
  options: VipRequestOptions = {}
): Promise<ChildProfileVipSeries[]> {
  const url = `${options.baseUrl || API}/users/vip-series`;
  return runVipRequest(
    async (signal) => {
      const response = await apiFetch(url, { method: 'GET', headers: authHeaders(token), signal });
      const { data } = await parseEnvelope(response, 'Chưa tải được danh sách phim cần trả lời câu hỏi.');
      if (!Array.isArray(data)) return [];
      return data
        .map(normalizeParentItem)
        .filter((item: ChildProfileVipSeries | null): item is ChildProfileVipSeries => item !== null);
    },
    options
  );
}

export async function updateChildVipSeries(
  token: string,
  profileId: number,
  pin: string,
  seriesIds: string[],
  options: VipRequestOptions = {}
): Promise<ChildProfileVipSeries> {
  const url = `${options.baseUrl || API}/users/profiles/${encodeURIComponent(String(profileId))}/vip-series`;
  return runVipRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'PUT',
        headers: authHeaders(token, true),
        body: JSON.stringify({ pin, seriesIds }),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa lưu được danh sách phim cần trả lời câu hỏi.');
      const normalized = normalizeParentItem(data);
      if (!normalized) throw new VipApiError('Dữ liệu phim cần trả lời câu hỏi không hợp lệ.', 502);
      return normalized;
    },
    options
  );
}

export async function fetchKidVipStatus(
  token: string,
  options: VipRequestOptions = {}
): Promise<KidVipStatus> {
  const url = `${options.baseUrl || API}/users/vip-series/status`;
  return runVipRequest(
    async (signal) => {
      const response = await apiFetch(url, { method: 'GET', headers: authHeaders(token), signal });
      const { data } = await parseEnvelope(response, 'Chưa tải được trạng thái phim cần trả lời câu hỏi.');
      return {
        profileId: typeof data?.profileId === 'number' ? data.profileId : null,
        day: typeof data?.day === 'string' ? data.day : '',
        quizGrade: typeof data?.quizGrade === 'string' && data.quizGrade.trim() ? data.quizGrade.trim() : null,
        quizGradeLabel:
          typeof data?.quizGradeLabel === 'string' && data.quizGradeLabel.trim() ? data.quizGradeLabel.trim() : null,
        vipQuizGrade:
          typeof data?.vipQuizGrade === 'string' && data.vipQuizGrade.trim() ? data.vipQuizGrade.trim() : null,
        vipQuizGradeLabel:
          typeof data?.vipQuizGradeLabel === 'string' && data.vipQuizGradeLabel.trim()
            ? data.vipQuizGradeLabel.trim()
            : null,
        vipSeriesIds: Array.isArray(data?.vipSeriesIds)
          ? data.vipSeriesIds.filter((item: unknown): item is string => typeof item === 'string')
          : [],
        unlockedEpisodeIds: Array.isArray(data?.unlockedEpisodeIds)
          ? data.unlockedEpisodeIds.filter((item: unknown): item is string => typeof item === 'string')
          : [],
      };
    },
    options
  );
}

export async function fetchVipEpisodeChallenge(
  token: string,
  episodeId: string,
  options: VipRequestOptions = {}
): Promise<VipChallenge> {
  const url = `${options.baseUrl || API}/users/vip-series/episodes/${encodeURIComponent(episodeId)}/challenge`;
  return runVipRequest(
    async (signal) => {
      const response = await apiFetch(url, { method: 'GET', headers: authHeaders(token), signal });
      const { data } = await parseEnvelope(response, 'Chưa lấy được câu hỏi mở khóa tập phim.');
      const challengeToken = typeof data?.token === 'string' ? data.token.trim() : '';
      const question = typeof data?.question === 'string' ? data.question.trim() : '';
      const quizOptions = normalizeQuizOptions(data?.options);
      if (!challengeToken || !question || quizOptions.length !== 4) {
        throw new VipApiError('Dữ liệu câu hỏi mở khóa phim chưa hợp lệ.', 502);
      }
      return {
        token: challengeToken,
        episodeId: typeof data?.episodeId === 'string' ? data.episodeId : episodeId,
        seriesId: typeof data?.seriesId === 'string' ? data.seriesId : '',
        question,
        options: quizOptions,
        subject: typeof data?.subject === 'string' ? data.subject : 'TOAN',
        subjectLabel: typeof data?.subjectLabel === 'string' ? data.subjectLabel : 'Toán',
        grade: typeof data?.grade === 'string' ? data.grade : 'LOP_2',
        gradeLabel: typeof data?.gradeLabel === 'string' ? data.gradeLabel : 'Lớp 2',
        setName: typeof data?.setName === 'string' ? data.setName : '',
        expiresAt: typeof data?.expiresAt === 'string' ? data.expiresAt : '',
        expiresInSeconds: Number(data?.expiresInSeconds) || 120,
      };
    },
    options
  );
}

export async function submitVipEpisodeUnlock(
  token: string,
  episodeId: string,
  challengeToken: string,
  option: QuizOptionKey,
  options: VipRequestOptions = {}
): Promise<VipUnlockResult> {
  const url = `${options.baseUrl || API}/users/vip-series/episodes/${encodeURIComponent(episodeId)}/unlock`;
  return runVipRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'POST',
        headers: authHeaders(token, true),
        body: JSON.stringify({ token: challengeToken, option }),
        signal,
      });
      const { data, message } = await parseEnvelope(response, 'Chưa kiểm tra được đáp án mở khóa phim.');
      return {
        correct: Boolean(data?.correct),
        unlocked: Boolean(data?.unlocked),
        episodeId: typeof data?.episodeId === 'string' ? data.episodeId : episodeId,
        day: typeof data?.day === 'string' ? data.day : '',
        correctOption: normalizeOptionKey(data?.correctOption),
        correctText: typeof data?.correctText === 'string' ? data.correctText : null,
        explanation: typeof data?.explanation === 'string' && data.explanation.trim() ? data.explanation : null,
        message,
      };
    },
    options
  );
}
