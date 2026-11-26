import { apiFetch, withRequestTimeout } from '@/lib/services/apiRequest';

const API = '/api';
const DEFAULT_TIMEOUT_MS = 8000;

export const SCREEN_TIME_LOCKED_EVENT = 'dippie:screen-time-locked';
export const SCREEN_TIME_UPDATED_EVENT = 'dippie:screen-time-updated';
export const SCREEN_TIME_UPDATED_STORAGE_KEY = 'dippie:screen-time-updated';
export const HEARTBEAT_INTERVAL_MS = 15_000;
export const DAILY_MINUTE_PRESETS = [30, 60, 90, 120] as const;

export type MathLevelOption = {
  level: 1 | 2 | 3 | 4;
  shortLabel: string;
  label: string;
};

export const MATH_LEVEL_OPTIONS: readonly MathLevelOption[] = [
  { level: 1, shortLabel: 'Phạm vi 10', label: 'Mức 1 · Cộng trừ trong phạm vi 10' },
  { level: 2, shortLabel: 'Phạm vi 20', label: 'Mức 2 · Cộng trừ trong phạm vi 20' },
  { level: 3, shortLabel: 'Phạm vi 100', label: 'Mức 3 · Cộng trừ trong phạm vi 100' },
  { level: 4, shortLabel: 'Cửu chương × / ÷', label: 'Mức 4 · Bảng cửu chương nhân / chia' },
];

export type ScreenTimeStatus = {
  limited: boolean;
  unlimited: boolean;
  profileId: number | null;
  day: string;
  dailyMinutes: number | null;
  mathLevel: number;
  rewardMinutes: number;
  maxBonusMinutes: number;
  dailySeconds: number;
  watchedSeconds: number;
  rewardSeconds: number;
  grantSeconds: number;
  bountySeconds: number;
  remainingSeconds: number | null;
  locked: boolean;
  bonusCapSeconds: number;
  bonusRemainingSeconds: number;
  bonusCapReached: boolean;
};

export type ScreenTimeHeartbeatResult = {
  limited: boolean;
  creditedSeconds: number;
  ignored: boolean;
  remainingSeconds: number | null;
  locked: boolean;
  status: ScreenTimeStatus;
};

export type MathChallenge = {
  token: string;
  question: string;
  mathLevel: number;
  rewardMinutes: number;
  expiresAt: string;
  expiresInSeconds: number;
  bonusRemainingSeconds: number;
};

export type MathChallengeAnswerResult = {
  correct: boolean;
  earnedSeconds: number;
  earnedMinutes: number;
  bonusCapReached: boolean;
  message: string;
  status: ScreenTimeStatus;
};

export type ChildProfileScreenTime = {
  profileId: number;
  name: string;
  avatarKey: string;
  status: ScreenTimeStatus;
};

export type ScreenTimeSettingsPayload = {
  pin: string;
  dailyMinutes: number | null;
  unlimited?: boolean;
  mathLevel?: number;
  rewardMinutes?: number;
  maxBonusMinutes?: number;
};

export type ScreenTimeRequestOptions = {
  baseUrl?: string;
  signal?: AbortSignal;
  timeoutMs?: number;
};

export class ScreenTimeApiError extends Error {
  status: number;
  retryAfterSeconds?: number;

  constructor(message: string, status: number, retryAfterSeconds?: number) {
    super(message);
    this.name = 'ScreenTimeApiError';
    this.status = status;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

export function notifyScreenTimeLocked(): void {
  if (typeof window === 'undefined' || typeof window.dispatchEvent !== 'function' || typeof Event !== 'function') {
    return;
  }
  window.dispatchEvent(new Event(SCREEN_TIME_LOCKED_EVENT));
}

export function broadcastScreenTimeUpdated(profileId?: number): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage?.setItem(
      SCREEN_TIME_UPDATED_STORAGE_KEY,
      `${profileId ?? 'all'}:${Date.now()}`
    );
  } catch {}
  if (typeof window.dispatchEvent === 'function' && typeof Event === 'function') {
    window.dispatchEvent(new Event(SCREEN_TIME_UPDATED_EVENT));
  }
}

export function formatDailyLimitLabel(dailyMinutes: number | null | undefined, unlimited = false): string {
  if (unlimited || dailyMinutes === null) {
    return 'Không giới hạn';
  }
  const safe = typeof dailyMinutes === 'number' && Number.isFinite(dailyMinutes) ? dailyMinutes : 60;
  return `${safe} phút`;
}

export function getMathLevelLabel(level: number): string {
  const found = MATH_LEVEL_OPTIONS.find((item) => item.level === level);
  return found ? found.label : MATH_LEVEL_OPTIONS[0].label;
}

export function formatRemainingTime(remainingSeconds: number | null, locked = false): string {
  if (locked || (typeof remainingSeconds === 'number' && remainingSeconds <= 0)) {
    return 'Hết giờ xem';
  }
  if (typeof remainingSeconds !== 'number' || !Number.isFinite(remainingSeconds)) {
    return '';
  }
  const safe = Math.max(0, Math.floor(remainingSeconds));
  if (safe < 60) {
    return `Còn ${safe} giây`;
  }
  const totalMinutes = Math.ceil(safe / 60);
  if (totalMinutes < 60) {
    return `Còn ${totalMinutes} phút`;
  }
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  return minutes === 0 ? `Còn ${hours} giờ` : `Còn ${hours} giờ ${minutes} phút`;
}

export function formatUsageDuration(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds <= 0) {
    return '0 phút';
  }
  const safe = Math.floor(seconds);
  if (safe < 60) {
    return `${safe} giây`;
  }
  const totalMinutes = Math.floor(safe / 60);
  if (totalMinutes < 60) {
    return `${totalMinutes} phút`;
  }
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  return minutes === 0 ? `${hours} giờ` : `${hours} giờ ${minutes} phút`;
}

export function validateScreenTimeSettings(input: {
  dailyMinutes: number | null;
  unlimited?: boolean;
  mathLevel?: number;
  rewardMinutes?: number;
  maxBonusMinutes?: number;
}): string | null {
  if (!input.unlimited) {
    if (
      input.dailyMinutes === null ||
      !Number.isInteger(input.dailyMinutes) ||
      input.dailyMinutes < 0 ||
      input.dailyMinutes > 600
    ) {
      return 'Giới hạn thời gian mỗi ngày cần từ 0 đến 600 phút.';
    }
  }
  if (input.mathLevel !== undefined && (!Number.isInteger(input.mathLevel) || input.mathLevel < 1 || input.mathLevel > 4)) {
    return 'Cấp độ bài toán cần từ 1 đến 4.';
  }
  if (
    input.rewardMinutes !== undefined &&
    (!Number.isInteger(input.rewardMinutes) || input.rewardMinutes < 1 || input.rewardMinutes > 30)
  ) {
    return 'Số phút thưởng mỗi câu đúng cần từ 1 đến 30 phút.';
  }
  if (
    input.maxBonusMinutes !== undefined &&
    (!Number.isInteger(input.maxBonusMinutes) || input.maxBonusMinutes < 0 || input.maxBonusMinutes > 120)
  ) {
    return 'Tổng phút thưởng tối đa mỗi ngày cần từ 0 đến 120 phút.';
  }
  return null;
}

export function validateGrantMinutes(minutes: number): string | null {
  if (!Number.isInteger(minutes) || minutes < 1 || minutes > 180) {
    return 'Số phút cho thêm cần từ 1 đến 180 phút.';
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

function toNonNegativeNumber(value: unknown, fallback = 0): number {
  const num = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(num) && num >= 0 ? num : fallback;
}

function normalizeStatus(raw: any): ScreenTimeStatus {
  const unlimited = raw?.unlimited === true || (raw?.dailyMinutes === null && raw?.limited === false);
  const limited = !unlimited && Boolean(raw?.limited);
  const rawProfileId = raw?.profileId;
  const profileId =
    typeof rawProfileId === 'number' && Number.isSafeInteger(rawProfileId) && rawProfileId > 0
      ? rawProfileId
      : null;
  const remainingSeconds =
    limited && raw?.remainingSeconds !== null && raw?.remainingSeconds !== undefined
      ? toNonNegativeNumber(raw.remainingSeconds, 0)
      : null;
  const bonusRemainingSeconds = toNonNegativeNumber(raw?.bonusRemainingSeconds, 0);
  const locked = limited && (Boolean(raw?.locked) || (remainingSeconds !== null && remainingSeconds <= 0));
  const dailyMinutes =
    unlimited || raw?.dailyMinutes === null ? null : toNonNegativeNumber(raw?.dailyMinutes, 60);
  return {
    limited,
    unlimited,
    profileId,
    day: typeof raw?.day === 'string' ? raw.day : '',
    dailyMinutes,
    mathLevel: Math.min(4, Math.max(1, toNonNegativeNumber(raw?.mathLevel, 1))) || 1,
    rewardMinutes: toNonNegativeNumber(raw?.rewardMinutes, 5),
    maxBonusMinutes: toNonNegativeNumber(raw?.maxBonusMinutes, 30),
    dailySeconds: toNonNegativeNumber(raw?.dailySeconds, 0),
    watchedSeconds: toNonNegativeNumber(raw?.watchedSeconds, 0),
    rewardSeconds: toNonNegativeNumber(raw?.rewardSeconds, 0),
    grantSeconds: toNonNegativeNumber(raw?.grantSeconds, 0),
    bountySeconds: toNonNegativeNumber(raw?.bountySeconds, 0),
    remainingSeconds,
    locked,
    bonusCapSeconds: toNonNegativeNumber(raw?.bonusCapSeconds, 0),
    bonusRemainingSeconds,
    bonusCapReached: limited && (Boolean(raw?.bonusCapReached) || bonusRemainingSeconds <= 0),
  };
}

function normalizeChildOverview(raw: any): ChildProfileScreenTime | null {
  if (!raw || typeof raw !== 'object') return null;
  const profileId = typeof raw.profileId === 'number' ? raw.profileId : Number(raw.profileId);
  if (!Number.isSafeInteger(profileId) || profileId <= 0) return null;
  return {
    profileId,
    name: typeof raw.name === 'string' ? raw.name.trim() : '',
    avatarKey: typeof raw.avatarKey === 'string' ? raw.avatarKey.trim() : 'bear',
    status: normalizeStatus(raw.status),
  };
}

async function parseEnvelope(response: Response, fallbackMessage: string): Promise<{ data: any; message: string }> {
  const retryHeader = response.headers?.get?.('Retry-After');
  const retryAfter = retryHeader && /^\d+$/.test(retryHeader) ? parseInt(retryHeader, 10) : undefined;
  let json: any = null;
  try {
    json = await response.json();
  } catch {
    throw new ScreenTimeApiError(fallbackMessage, response.status, retryAfter);
  }
  const serverMessage = typeof json?.message === 'string' && json.message.trim() ? json.message.trim() : '';
  if (!response.ok || json?.success !== true) {
    throw new ScreenTimeApiError(serverMessage || fallbackMessage, response.status, retryAfter);
  }
  return { data: json.data, message: serverMessage };
}

export async function fetchScreenTimeStatus(
  token: string,
  options: ScreenTimeRequestOptions = {}
): Promise<ScreenTimeStatus> {
  const url = `${options.baseUrl || API}/users/screen-time`;
  return withRequestTimeout(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'GET',
        headers: authHeaders(token),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa tải được thời gian xem hôm nay.');
      return normalizeStatus(data);
    },
    { signal: options.signal, timeoutMs: options.timeoutMs ?? DEFAULT_TIMEOUT_MS }
  );
}

export async function sendScreenTimeHeartbeat(
  token: string,
  seconds: number,
  options: ScreenTimeRequestOptions = {}
): Promise<ScreenTimeHeartbeatResult> {
  const url = `${options.baseUrl || API}/users/screen-time/heartbeat`;
  const cleanSeconds = Math.max(0, Math.round(seconds));
  return withRequestTimeout(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'POST',
        headers: authHeaders(token, true),
        body: JSON.stringify({ seconds: cleanSeconds }),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa cập nhật được thời gian xem.');
      const status = normalizeStatus(data?.status);
      return {
        limited: Boolean(data?.limited),
        creditedSeconds: toNonNegativeNumber(data?.creditedSeconds, 0),
        ignored: Boolean(data?.ignored),
        remainingSeconds: status.remainingSeconds,
        locked: Boolean(data?.locked) || status.locked,
        status,
      };
    },
    { signal: options.signal, timeoutMs: options.timeoutMs ?? DEFAULT_TIMEOUT_MS }
  );
}

export async function fetchMathChallenge(
  token: string,
  options: ScreenTimeRequestOptions = {}
): Promise<MathChallenge> {
  const url = `${options.baseUrl || API}/users/screen-time/challenge`;
  return withRequestTimeout(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'GET',
        headers: authHeaders(token),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa lấy được câu hỏi toán.');
      const challengeToken = typeof data?.token === 'string' ? data.token.trim() : '';
      const question = typeof data?.question === 'string' ? data.question.trim() : '';
      if (!challengeToken || !question) {
        throw new ScreenTimeApiError('Dữ liệu câu hỏi toán chưa hợp lệ.', 502);
      }
      return {
        token: challengeToken,
        question,
        mathLevel: Math.min(4, Math.max(1, toNonNegativeNumber(data?.mathLevel, 1))) || 1,
        rewardMinutes: toNonNegativeNumber(data?.rewardMinutes, 5),
        expiresAt: typeof data?.expiresAt === 'string' ? data.expiresAt : '',
        expiresInSeconds: toNonNegativeNumber(data?.expiresInSeconds, 120),
        bonusRemainingSeconds: toNonNegativeNumber(data?.bonusRemainingSeconds, 0),
      };
    },
    { signal: options.signal, timeoutMs: options.timeoutMs ?? DEFAULT_TIMEOUT_MS }
  );
}

export async function submitMathChallengeAnswer(
  token: string,
  challengeToken: string,
  answer: number,
  options: ScreenTimeRequestOptions = {}
): Promise<MathChallengeAnswerResult> {
  const url = `${options.baseUrl || API}/users/screen-time/challenge/answer`;
  return withRequestTimeout(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'POST',
        headers: authHeaders(token, true),
        body: JSON.stringify({ token: challengeToken, answer }),
        signal,
      });
      const { data, message } = await parseEnvelope(response, 'Chưa kiểm tra được đáp án.');
      const status = normalizeStatus(data?.status);
      return {
        correct: Boolean(data?.correct),
        earnedSeconds: toNonNegativeNumber(data?.earnedSeconds, 0),
        earnedMinutes: toNonNegativeNumber(data?.earnedMinutes, 0),
        bonusCapReached: Boolean(data?.bonusCapReached) || status.bonusCapReached,
        message,
        status,
      };
    },
    { signal: options.signal, timeoutMs: options.timeoutMs ?? DEFAULT_TIMEOUT_MS }
  );
}

export async function fetchParentScreenTimeOverview(
  token: string,
  options: ScreenTimeRequestOptions = {}
): Promise<ChildProfileScreenTime[]> {
  const url = `${options.baseUrl || API}/users/profiles/screen-time`;
  return withRequestTimeout(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'GET',
        headers: authHeaders(token),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa tải được thống kê thời gian xem.');
      if (!Array.isArray(data)) return [];
      return data
        .map(normalizeChildOverview)
        .filter((item: ChildProfileScreenTime | null): item is ChildProfileScreenTime => item !== null);
    },
    { signal: options.signal, timeoutMs: options.timeoutMs ?? DEFAULT_TIMEOUT_MS }
  );
}

export async function updateChildScreenTimeSettings(
  token: string,
  profileId: number,
  payload: ScreenTimeSettingsPayload,
  options: ScreenTimeRequestOptions = {}
): Promise<ChildProfileScreenTime> {
  const url = `${options.baseUrl || API}/users/profiles/${encodeURIComponent(String(profileId))}/screen-time`;
  return withRequestTimeout(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'PUT',
        headers: authHeaders(token, true),
        body: JSON.stringify(payload),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa lưu được cài đặt thời gian xem.');
      const normalized = normalizeChildOverview(data);
      if (!normalized) {
        throw new ScreenTimeApiError('Dữ liệu thời gian xem chưa hợp lệ.', 502);
      }
      return normalized;
    },
    { signal: options.signal, timeoutMs: options.timeoutMs ?? DEFAULT_TIMEOUT_MS }
  );
}

export async function grantChildExtraMinutes(
  token: string,
  profileId: number,
  pin: string,
  minutes: number,
  options: ScreenTimeRequestOptions = {}
): Promise<ChildProfileScreenTime> {
  const url = `${options.baseUrl || API}/users/profiles/${encodeURIComponent(String(profileId))}/screen-time/grant`;
  return withRequestTimeout(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'POST',
        headers: authHeaders(token, true),
        body: JSON.stringify({ pin, minutes }),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa cộng thêm phút cho bé được.');
      const normalized = normalizeChildOverview(data);
      if (!normalized) {
        throw new ScreenTimeApiError('Dữ liệu thời gian xem chưa hợp lệ.', 502);
      }
      return normalized;
    },
    { signal: options.signal, timeoutMs: options.timeoutMs ?? DEFAULT_TIMEOUT_MS }
  );
}
