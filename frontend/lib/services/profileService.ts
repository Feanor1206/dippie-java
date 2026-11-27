import {
  NETWORK_ERROR_MESSAGE,
  apiFetch,
  formatRateLimitMessage,
  isLikelyNetworkError,
  withRequestTimeout,
} from '@/lib/services/apiRequest';
import type { AuthUser } from '@/lib/context/AuthContext';

const API = '/api';
const DEFAULT_TIMEOUT_MS = 8000;
const PIN_PATTERN = /^[0-9]{4,6}$/;

export type ChildAvatarMeta = {
  key: string;
  label: string;
  bgClass: string;
  accentHex: string;
};

export const CHILD_AVATARS: readonly ChildAvatarMeta[] = [
  { key: 'bear', label: 'Gấu nhỏ', bgClass: 'bg-[#f2d18c]', accentHex: '#92400e' },
  { key: 'bunny', label: 'Thỏ trắng', bgClass: 'bg-[#fce7f3]', accentHex: '#9d174d' },
  { key: 'cat', label: 'Mèo cam', bgClass: 'bg-[#ffedd5]', accentHex: '#c2410c' },
  { key: 'dino', label: 'Khủng long', bgClass: 'bg-[#d1fae5]', accentHex: '#065f46' },
  { key: 'fox', label: 'Cáo nhỏ', bgClass: 'bg-[#fee2e2]', accentHex: '#b91c1c' },
  { key: 'owl', label: 'Cú mèo', bgClass: 'bg-[#e0e7ff]', accentHex: '#3730a3' },
  { key: 'panda', label: 'Gấu trúc', bgClass: 'bg-[#e5e7eb]', accentHex: '#1f2937' },
  { key: 'star', label: 'Ngôi sao', bgClass: 'bg-[#fef3c7]', accentHex: '#a16207' },
];

export const MAX_CHILD_PROFILES = 6;

export type ChildProfile = {
  id: number;
  name: string;
  avatarKey: string;
  dailyMinutes?: number | null;
  unlimited?: boolean;
  quizGrade?: string;
  quizGradeLabel?: string;
  rewardMinutes?: number;
  maxBonusMinutes?: number;
  createdAt: string;
};

export type ProfilesState = {
  parentModeEnabled: boolean;
  activeProfileId: number | null;
  activeProfile: ChildProfile | null;
  profiles: ChildProfile[];
  availableAvatars: string[];
};

export type ParentModeState = {
  enabled: boolean;
  profileCount: number;
};

export type ProfileSwitchResult = {
  token: string;
  user: AuthUser;
  activeProfileId: number | null;
  activeProfile: ChildProfile | null;
  message: string;
};

export type ProfileRequestOptions = {
  baseUrl?: string;
  signal?: AbortSignal;
  timeoutMs?: number;
};

export class ProfileApiError extends Error {
  status: number;
  retryAfterSeconds?: number;

  constructor(message: string, status: number, retryAfterSeconds?: number) {
    super(message);
    this.name = 'ProfileApiError';
    this.status = status;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

export function getChildAvatarMeta(avatarKey?: string | null): ChildAvatarMeta {
  const normalized = typeof avatarKey === 'string' ? avatarKey.trim().toLowerCase() : '';
  return CHILD_AVATARS.find((item) => item.key === normalized) ?? CHILD_AVATARS[0];
}

export function validateParentPin(pin: string): string | null {
  if (!PIN_PATTERN.test(pin)) {
    return 'Mã PIN phụ huynh cần gồm từ 4 đến 6 chữ số.';
  }
  return null;
}

export function validateChildProfileName(name: string): string | null {
  const trimmed = name.trim();
  if (!trimmed || trimmed.length > 50) {
    return 'Tên hồ sơ trẻ em cần từ 1 đến 50 ký tự.';
  }
  return null;
}

export function decodeJwtProfileId(token?: string | null): number | null {
  if (!token || typeof token !== 'string') return null;
  const parts = token.split('.');
  if (parts.length !== 3) return null;
  try {
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=');
    const decoded =
      typeof atob === 'function'
        ? atob(padded)
        : typeof Buffer !== 'undefined'
        ? Buffer.from(padded, 'base64').toString('utf8')
        : '';
    if (!decoded) return null;
    const payload = JSON.parse(decoded) as Record<string, unknown>;
    const rawPid = payload.pid;
    if (typeof rawPid === 'number' && Number.isSafeInteger(rawPid) && rawPid > 0) {
      return rawPid;
    }
    return null;
  } catch {
    return null;
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

function normalizeChildProfile(raw: any): ChildProfile | null {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return null;
  const id = typeof raw.id === 'number' ? raw.id : Number(raw.id);
  if (!Number.isSafeInteger(id) || id <= 0) return null;
  const name = typeof raw.name === 'string' ? raw.name.trim() : '';
  const avatarKey = typeof raw.avatarKey === 'string' ? raw.avatarKey.trim() : '';
  if (!name || !avatarKey) return null;
  const unlimited = raw.unlimited === true || raw.dailyMinutes === null;
  const dailyMinutes = unlimited
    ? null
    : typeof raw.dailyMinutes === 'number'
    ? raw.dailyMinutes
    : undefined;
  return {
    id,
    name,
    avatarKey,
    ...(dailyMinutes !== undefined ? { dailyMinutes } : {}),
    ...(unlimited || typeof raw.unlimited === 'boolean' ? { unlimited } : {}),
    ...(typeof raw.quizGrade === 'string' && raw.quizGrade.trim() ? { quizGrade: raw.quizGrade.trim() } : {}),
    ...(typeof raw.quizGradeLabel === 'string' && raw.quizGradeLabel.trim()
      ? { quizGradeLabel: raw.quizGradeLabel.trim() }
      : {}),
    ...(typeof raw.rewardMinutes === 'number' ? { rewardMinutes: raw.rewardMinutes } : {}),
    ...(typeof raw.maxBonusMinutes === 'number' ? { maxBonusMinutes: raw.maxBonusMinutes } : {}),
    createdAt: typeof raw.createdAt === 'string' ? raw.createdAt : '',
  };
}

function normalizeAuthUser(raw: any): AuthUser {
  const id = typeof raw?.id === 'number' ? raw.id : Number(raw?.id);
  return {
    id,
    username: typeof raw?.username === 'string' ? raw.username.trim() : '',
    email: typeof raw?.email === 'string' ? raw.email.trim() : '',
    fullName: typeof raw?.fullName === 'string' && raw.fullName.trim() ? raw.fullName.trim() : undefined,
    role: typeof raw?.role === 'string' && raw.role.trim() ? raw.role.trim() : 'ROLE_USER',
  };
}

async function runProfileRequest<T>(
  action: (signal: AbortSignal) => Promise<T>,
  options: ProfileRequestOptions = {}
): Promise<T> {
  try {
    return await withRequestTimeout(action, {
      signal: options.signal,
      timeoutMs: options.timeoutMs ?? DEFAULT_TIMEOUT_MS,
    });
  } catch (error) {
    if (isLikelyNetworkError(error)) {
      throw new ProfileApiError(NETWORK_ERROR_MESSAGE, 0);
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
    throw new ProfileApiError(message, response.status, retryAfter);
  }
  const serverMessage = typeof json?.message === 'string' && json.message.trim() ? json.message.trim() : '';
  if (!response.ok || json?.success !== true) {
    const message =
      response.status === 429
        ? formatRateLimitMessage(serverMessage, fallbackMessage, retryAfter)
        : serverMessage || fallbackMessage;
    throw new ProfileApiError(message, response.status, retryAfter);
  }
  return { data: json.data, message: serverMessage };
}

export async function fetchProfilesState(
  token: string,
  options: ProfileRequestOptions = {}
): Promise<ProfilesState> {
  const url = `${options.baseUrl || API}/users/profiles`;
  return runProfileRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'GET',
        headers: authHeaders(token),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa tải được danh sách hồ sơ.');
      const profiles = Array.isArray(data?.profiles)
        ? data.profiles.map(normalizeChildProfile).filter((item: ChildProfile | null): item is ChildProfile => item !== null)
        : [];
      const activeProfile = normalizeChildProfile(data?.activeProfile);
      const availableAvatars = Array.isArray(data?.availableAvatars)
        ? data.availableAvatars.filter((item: unknown): item is string => typeof item === 'string')
        : CHILD_AVATARS.map((item) => item.key);
      return {
        parentModeEnabled: Boolean(data?.parentModeEnabled),
        activeProfileId: activeProfile ? activeProfile.id : null,
        activeProfile,
        profiles,
        availableAvatars,
      };
    },
    options
  );
}

export async function enableParentMode(
  token: string,
  pin: string,
  options: ProfileRequestOptions = {}
): Promise<ParentModeState> {
  const url = `${options.baseUrl || API}/users/parent-mode/enable`;
  return runProfileRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'POST',
        headers: authHeaders(token, true),
        body: JSON.stringify({ pin }),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa bật được chế độ hồ sơ trẻ em.');
      return {
        enabled: Boolean(data?.enabled),
        profileCount: typeof data?.profileCount === 'number' ? data.profileCount : 0,
      };
    },
    options
  );
}

export async function changeParentPin(
  token: string,
  currentPin: string,
  newPin: string,
  options: ProfileRequestOptions = {}
): Promise<ParentModeState> {
  const url = `${options.baseUrl || API}/users/parent-mode/pin`;
  return runProfileRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'PUT',
        headers: authHeaders(token, true),
        body: JSON.stringify({ currentPin, newPin }),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa đổi được mã PIN phụ huynh.');
      return {
        enabled: Boolean(data?.enabled),
        profileCount: typeof data?.profileCount === 'number' ? data.profileCount : 0,
      };
    },
    options
  );
}

export async function disableParentMode(
  token: string,
  pin: string,
  options: ProfileRequestOptions = {}
): Promise<ParentModeState> {
  const url = `${options.baseUrl || API}/users/parent-mode/disable`;
  return runProfileRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'POST',
        headers: authHeaders(token, true),
        body: JSON.stringify({ pin }),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa tắt được chế độ hồ sơ trẻ em.');
      return {
        enabled: Boolean(data?.enabled),
        profileCount: typeof data?.profileCount === 'number' ? data.profileCount : 0,
      };
    },
    options
  );
}

export async function createChildProfile(
  token: string,
  payload: {
    pin: string;
    name: string;
    avatarKey: string;
    dailyMinutes?: number | null;
    unlimited?: boolean;
    quizGrade?: string;
    quizSetIds?: number[];
  },
  options: ProfileRequestOptions = {}
): Promise<ChildProfile> {
  const url = `${options.baseUrl || API}/users/profiles`;
  return runProfileRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'POST',
        headers: authHeaders(token, true),
        body: JSON.stringify(payload),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa tạo được hồ sơ trẻ em.');
      const normalized = normalizeChildProfile(data);
      if (!normalized) throw new ProfileApiError('Dữ liệu hồ sơ trẻ em chưa đúng định dạng.', 502);
      return normalized;
    },
    options
  );
}

export async function updateChildProfile(
  token: string,
  profileId: number,
  payload: {
    pin: string;
    name: string;
    avatarKey: string;
    dailyMinutes?: number | null;
    unlimited?: boolean;
    quizGrade?: string;
    quizSetIds?: number[];
  },
  options: ProfileRequestOptions = {}
): Promise<ChildProfile> {
  const url = `${options.baseUrl || API}/users/profiles/${encodeURIComponent(String(profileId))}`;
  return runProfileRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'PUT',
        headers: authHeaders(token, true),
        body: JSON.stringify(payload),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa cập nhật được hồ sơ trẻ em.');
      const normalized = normalizeChildProfile(data);
      if (!normalized) throw new ProfileApiError('Dữ liệu hồ sơ trẻ em chưa đúng định dạng.', 502);
      return normalized;
    },
    options
  );
}

export async function deleteChildProfile(
  token: string,
  profileId: number,
  pin: string,
  options: ProfileRequestOptions = {}
): Promise<void> {
  const url = `${options.baseUrl || API}/users/profiles/${encodeURIComponent(String(profileId))}`;
  return runProfileRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'DELETE',
        headers: authHeaders(token, true),
        body: JSON.stringify({ pin }),
        signal,
      });
      if (response.status === 204) return;
      await parseEnvelope(response, 'Chưa xóa được hồ sơ trẻ em.');
    },
    options
  );
}

export async function switchToChildProfile(
  token: string,
  profileId: number,
  options: ProfileRequestOptions = {}
): Promise<ProfileSwitchResult> {
  const url = `${options.baseUrl || API}/users/profiles/${encodeURIComponent(String(profileId))}/switch`;
  return runProfileRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'POST',
        headers: authHeaders(token),
        signal,
      });
      const { data, message } = await parseEnvelope(response, 'Chưa chuyển được sang hồ sơ trẻ em.');
      const nextToken = typeof data?.token === 'string' ? data.token.trim() : '';
      if (!nextToken) throw new ProfileApiError('Phản hồi chuyển hồ sơ thiếu phiên đăng nhập.', 502);
      const activeProfile = normalizeChildProfile(data?.activeProfile);
      return {
        token: nextToken,
        user: normalizeAuthUser(data),
        activeProfileId: activeProfile ? activeProfile.id : null,
        activeProfile,
        message: message || 'Đã chuyển sang hồ sơ trẻ em.',
      };
    },
    options
  );
}

export async function exitChildProfile(
  token: string,
  pin: string,
  options: ProfileRequestOptions = {}
): Promise<ProfileSwitchResult> {
  const url = `${options.baseUrl || API}/users/profiles/exit`;
  return runProfileRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'POST',
        headers: authHeaders(token, true),
        body: JSON.stringify({ pin }),
        signal,
      });
      const { data, message } = await parseEnvelope(response, 'Chưa thoát được hồ sơ trẻ em.');
      const nextToken = typeof data?.token === 'string' ? data.token.trim() : '';
      if (!nextToken) throw new ProfileApiError('Phản hồi thoát hồ sơ thiếu phiên đăng nhập.', 502);
      return {
        token: nextToken,
        user: normalizeAuthUser(data),
        activeProfileId: null,
        activeProfile: null,
        message: message || 'Đã quay lại tài khoản chính.',
      };
    },
    options
  );
}

export async function resetParentPinAndExit(
  token: string,
  password: string,
  newPin: string,
  options: ProfileRequestOptions = {}
): Promise<ProfileSwitchResult> {
  const url = `${options.baseUrl || API}/users/profiles/exit/reset-pin`;
  return runProfileRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'POST',
        headers: authHeaders(token, true),
        body: JSON.stringify({ password, newPin }),
        signal,
      });
      const { data, message } = await parseEnvelope(response, 'Chưa đặt lại được mã PIN.');
      const nextToken = typeof data?.token === 'string' ? data.token.trim() : '';
      if (!nextToken) throw new ProfileApiError('Phản hồi đặt lại mã PIN thiếu phiên đăng nhập.', 502);
      return {
        token: nextToken,
        user: normalizeAuthUser(data),
        activeProfileId: null,
        activeProfile: null,
        message: message || 'Đã đặt lại mã PIN và quay lại tài khoản chính.',
      };
    },
    options
  );
}
