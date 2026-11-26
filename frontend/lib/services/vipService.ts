import {
  NETWORK_ERROR_MESSAGE,
  apiFetch,
  formatRateLimitMessage,
  isLikelyNetworkError,
  withRequestTimeout,
} from '@/lib/services/apiRequest';

const API = '/api';
const DEFAULT_TIMEOUT_MS = 8000;

export type ChildProfileVipSeries = {
  profileId: number;
  name: string;
  avatarKey: string;
  mathLevel: number;
  vipMathLevel: number;
  seriesIds: string[];
};

export type KidVipStatus = {
  profileId: number | null;
  day: string;
  mathLevel: number;
  vipMathLevel: number;
  vipSeriesIds: string[];
  unlockedEpisodeIds: string[];
};

export type VipChallenge = {
  token: string;
  episodeId: string;
  seriesId: string;
  question: string;
  mathLevel: number;
  expiresAt: string;
  expiresInSeconds: number;
};

export type VipUnlockResult = {
  correct: boolean;
  unlocked: boolean;
  episodeId: string;
  day: string;
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
  const mathLevel = Math.min(4, Math.max(1, Number(raw.mathLevel) || 1));
  const vipMathLevel = Math.min(4, Math.max(1, Number(raw.vipMathLevel) || Math.min(4, mathLevel + 1)));
  return {
    profileId,
    name: typeof raw.name === 'string' ? raw.name.trim() : '',
    avatarKey: typeof raw.avatarKey === 'string' ? raw.avatarKey.trim() : 'bear',
    mathLevel,
    vipMathLevel,
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
      const { data } = await parseEnvelope(response, 'Chưa tải được danh sách phim VIP.');
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
      const { data } = await parseEnvelope(response, 'Chưa lưu được danh sách phim VIP.');
      const normalized = normalizeParentItem(data);
      if (!normalized) throw new VipApiError('Dữ liệu phim VIP không hợp lệ.', 502);
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
      const { data } = await parseEnvelope(response, 'Chưa tải được trạng thái phim VIP.');
      return {
        profileId: typeof data?.profileId === 'number' ? data.profileId : null,
        day: typeof data?.day === 'string' ? data.day : '',
        mathLevel: Number(data?.mathLevel) || 0,
        vipMathLevel: Number(data?.vipMathLevel) || 0,
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
      const { data } = await parseEnvelope(response, 'Chưa lấy được câu hỏi toán mở khóa tập VIP.');
      const challengeToken = typeof data?.token === 'string' ? data.token.trim() : '';
      const question = typeof data?.question === 'string' ? data.question.trim() : '';
      if (!challengeToken || !question) {
        throw new VipApiError('Dữ liệu câu hỏi toán VIP chưa hợp lệ.', 502);
      }
      return {
        token: challengeToken,
        episodeId: typeof data?.episodeId === 'string' ? data.episodeId : episodeId,
        seriesId: typeof data?.seriesId === 'string' ? data.seriesId : '',
        question,
        mathLevel: Math.min(4, Math.max(1, Number(data?.mathLevel) || 2)),
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
  answer: number,
  options: VipRequestOptions = {}
): Promise<VipUnlockResult> {
  const url = `${options.baseUrl || API}/users/vip-series/episodes/${encodeURIComponent(episodeId)}/unlock`;
  return runVipRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'POST',
        headers: authHeaders(token, true),
        body: JSON.stringify({ token: challengeToken, answer }),
        signal,
      });
      const { data, message } = await parseEnvelope(response, 'Chưa kiểm tra được đáp án mở khóa VIP.');
      return {
        correct: Boolean(data?.correct),
        unlocked: Boolean(data?.unlocked),
        episodeId: typeof data?.episodeId === 'string' ? data.episodeId : episodeId,
        day: typeof data?.day === 'string' ? data.day : '',
        message,
      };
    },
    options
  );
}
