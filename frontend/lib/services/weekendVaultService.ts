import {
  NETWORK_ERROR_MESSAGE,
  apiFetch,
  formatRateLimitMessage,
  isLikelyNetworkError,
  withRequestTimeout,
} from '@/lib/services/apiRequest';

const API = '/api';
const DEFAULT_TIMEOUT_MS = 8000;

export const WEEKDAY_SHORT_LABELS = ['T2', 'T3', 'T4', 'T5', 'T6'] as const;

export type WeekendVaultDay = {
  day: string;
  dayOfWeek: number;
  ended: boolean;
  stoppedInTime: boolean;
  mathRewardCount: number;
  mathGoalMet: boolean;
  earnedPercent: number;
};

export type WeekendVaultStatus = {
  enabled: boolean;
  profileId: number | null;
  day: string;
  weekStart: string;
  weekend: boolean;
  progressPercent: number;
  unlocked: boolean;
  manualUnlocked: boolean;
  locked: boolean;
  days: WeekendVaultDay[];
};

export type ChildProfileWeekendVault = {
  profileId: number;
  name: string;
  avatarKey: string;
  status: WeekendVaultStatus;
};

export type WeekendVaultRequestOptions = {
  baseUrl?: string;
  signal?: AbortSignal;
  timeoutMs?: number;
};

export class WeekendVaultApiError extends Error {
  status: number;
  retryAfterSeconds?: number;

  constructor(message: string, status: number, retryAfterSeconds?: number) {
    super(message);
    this.name = 'WeekendVaultApiError';
    this.status = status;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

export function getWeekdayLabel(dayOfWeek: number): string {
  return WEEKDAY_SHORT_LABELS[dayOfWeek - 1] ?? `T${dayOfWeek + 1}`;
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

async function runWeekendVaultRequest<T>(
  action: (signal: AbortSignal) => Promise<T>,
  options: WeekendVaultRequestOptions = {}
): Promise<T> {
  try {
    return await withRequestTimeout(action, {
      signal: options.signal,
      timeoutMs: options.timeoutMs ?? DEFAULT_TIMEOUT_MS,
    });
  } catch (error) {
    if (isLikelyNetworkError(error)) {
      throw new WeekendVaultApiError(NETWORK_ERROR_MESSAGE, 0);
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
    throw new WeekendVaultApiError(message, response.status, retryAfter);
  }
  const serverMessage = typeof json?.message === 'string' && json.message.trim() ? json.message.trim() : '';
  if (!response.ok || json?.success !== true) {
    const message =
      response.status === 429
        ? formatRateLimitMessage(serverMessage, fallbackMessage, retryAfter)
        : serverMessage || fallbackMessage;
    throw new WeekendVaultApiError(message, response.status, retryAfter);
  }
  return { data: json.data, message: serverMessage };
}

function normalizeDay(raw: any): WeekendVaultDay | null {
  if (!raw || typeof raw !== 'object') return null;
  return {
    day: typeof raw.day === 'string' ? raw.day : '',
    dayOfWeek: Number(raw.dayOfWeek) || 1,
    ended: Boolean(raw.ended),
    stoppedInTime: Boolean(raw.stoppedInTime),
    mathRewardCount: Number(raw.mathRewardCount) || 0,
    mathGoalMet: Boolean(raw.mathGoalMet),
    earnedPercent: Number(raw.earnedPercent) || 0,
  };
}

function normalizeStatus(raw: any): WeekendVaultStatus {
  const days = Array.isArray(raw?.days)
    ? raw.days.map(normalizeDay).filter((item: WeekendVaultDay | null): item is WeekendVaultDay => item !== null)
    : [];
  const enabled = Boolean(raw?.enabled);
  const progressPercent = Math.min(100, Math.max(0, Number(raw?.progressPercent) || 0));
  const unlocked = !enabled || Boolean(raw?.unlocked);
  return {
    enabled,
    profileId: typeof raw?.profileId === 'number' ? raw.profileId : null,
    day: typeof raw?.day === 'string' ? raw.day : '',
    weekStart: typeof raw?.weekStart === 'string' ? raw.weekStart : '',
    weekend: Boolean(raw?.weekend),
    progressPercent,
    unlocked,
    manualUnlocked: Boolean(raw?.manualUnlocked),
    locked: enabled && Boolean(raw?.locked ?? !unlocked),
    days,
  };
}

function normalizeChildOverview(raw: any): ChildProfileWeekendVault | null {
  if (!raw || typeof raw !== 'object') return null;
  const profileId = Number(raw.profileId);
  if (!Number.isSafeInteger(profileId) || profileId <= 0) return null;
  return {
    profileId,
    name: typeof raw.name === 'string' ? raw.name.trim() : '',
    avatarKey: typeof raw.avatarKey === 'string' ? raw.avatarKey.trim() : 'bear',
    status: normalizeStatus(raw.status),
  };
}

export async function fetchWeekendVaultStatus(
  token: string,
  options: WeekendVaultRequestOptions = {}
): Promise<WeekendVaultStatus> {
  const url = `${options.baseUrl || API}/users/weekend-vault`;
  return runWeekendVaultRequest(
    async (signal) => {
      const response = await apiFetch(url, { method: 'GET', headers: authHeaders(token), signal });
      const { data } = await parseEnvelope(response, 'Chưa tải được trạng thái Vé xem phim.');
      return normalizeStatus(data);
    },
    options
  );
}

export async function fetchParentWeekendVaultOverview(
  token: string,
  options: WeekendVaultRequestOptions = {}
): Promise<ChildProfileWeekendVault[]> {
  const url = `${options.baseUrl || API}/users/profiles/weekend-vault`;
  return runWeekendVaultRequest(
    async (signal) => {
      const response = await apiFetch(url, { method: 'GET', headers: authHeaders(token), signal });
      const { data } = await parseEnvelope(response, 'Chưa tải được trạng thái phim lẻ cuối tuần.');
      if (!Array.isArray(data)) return [];
      return data
        .map(normalizeChildOverview)
        .filter((item: ChildProfileWeekendVault | null): item is ChildProfileWeekendVault => item !== null);
    },
    options
  );
}

export async function unlockWeekendVaultManually(
  token: string,
  profileId: number,
  pin: string,
  options: WeekendVaultRequestOptions = {}
): Promise<ChildProfileWeekendVault> {
  const url = `${options.baseUrl || API}/users/profiles/${encodeURIComponent(String(profileId))}/weekend-vault/unlock`;
  return runWeekendVaultRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'POST',
        headers: authHeaders(token, true),
        body: JSON.stringify({ pin }),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa mở khóa được phim lẻ cuối tuần.');
      const normalized = normalizeChildOverview(data);
      if (!normalized) throw new WeekendVaultApiError('Dữ liệu phim lẻ cuối tuần không hợp lệ.', 502);
      return normalized;
    },
    options
  );
}

export async function restoreWeekendVaultLock(
  token: string,
  profileId: number,
  pin: string,
  options: WeekendVaultRequestOptions = {}
): Promise<ChildProfileWeekendVault> {
  const url = `${options.baseUrl || API}/users/profiles/${encodeURIComponent(String(profileId))}/weekend-vault/lock`;
  return runWeekendVaultRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'POST',
        headers: authHeaders(token, true),
        body: JSON.stringify({ pin }),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa khóa lại được phim lẻ cuối tuần.');
      const normalized = normalizeChildOverview(data);
      if (!normalized) throw new WeekendVaultApiError('Dữ liệu phim lẻ cuối tuần không hợp lệ.', 502);
      return normalized;
    },
    options
  );
}
