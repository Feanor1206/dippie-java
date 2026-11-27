import {
  NETWORK_ERROR_MESSAGE,
  apiFetch,
  formatRateLimitMessage,
  isLikelyNetworkError,
  withRequestTimeout,
} from '@/lib/services/apiRequest';
import type { ScreenTimeStatus } from '@/lib/services/screenTimeService';

const API = '/api';
const DEFAULT_TIMEOUT_MS = 8000;

export type BountyIconOption = {
  key: string;
  label: string;
  emoji: string;
};

export const BOUNTY_ICON_OPTIONS: readonly BountyIconOption[] = [
  { key: 'toys', label: 'Dọn đồ chơi', emoji: '🧸' },
  { key: 'book', label: 'Đọc sách', emoji: '📚' },
  { key: 'teeth', label: 'Đánh răng', emoji: '🪥' },
  { key: 'broom', label: 'Giúp việc nhà', emoji: '🧹' },
  { key: 'bed', label: 'Gấp chăn', emoji: '🛏️' },
  { key: 'apple', label: 'Ăn ngoan', emoji: '🍎' },
  { key: 'pencil', label: 'Học bài', emoji: '✏️' },
  { key: 'star', label: 'Ngôi sao', emoji: '⭐' },
];

export type BountySubmissionStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export type BountyTask = {
  id: number;
  title: string;
  rewardMinutes: number;
  icon: string | null;
  active: boolean;
  repeatableDaily: boolean;
  profileIds: number[];
  createdAt: string;
  updatedAt: string;
};

export type BountySubmission = {
  id: number;
  taskId: number;
  taskTitle: string;
  taskIcon: string | null;
  profileId: number;
  profileName: string;
  profileAvatarKey: string;
  usageDay: string;
  status: BountySubmissionStatus;
  rewardMinutes: number;
  submittedAt: string;
  decidedAt: string | null;
  rejectNote: string | null;
};

export type ParentBountyBoard = {
  tasks: BountyTask[];
  submissions: BountySubmission[];
};

export type KidBountyTaskState = 'AVAILABLE' | 'PENDING' | 'COMPLETED' | 'COMPLETED_TODAY';

export type KidBountyTask = {
  id: number;
  title: string;
  rewardMinutes: number;
  icon: string | null;
  repeatableDaily: boolean;
  state: KidBountyTaskState;
  pendingSubmissionId: number | null;
};

export type KidBountyBoard = {
  profileId: number;
  day: string;
  tasks: KidBountyTask[];
  submissions: BountySubmission[];
};

export type BountyDecisionResult = {
  submission: BountySubmission;
  screenTime: ScreenTimeStatus;
  message: string;
};

export type BountyTaskMutationPayload = {
  pin: string;
  title: string;
  rewardMinutes: number;
  icon?: string | null;
  active: boolean;
  repeatableDaily: boolean;
  profileIds: number[];
};

export type BountyRequestOptions = {
  baseUrl?: string;
  signal?: AbortSignal;
  timeoutMs?: number;
};

export class BountyApiError extends Error {
  status: number;
  retryAfterSeconds?: number;

  constructor(message: string, status: number, retryAfterSeconds?: number) {
    super(message);
    this.name = 'BountyApiError';
    this.status = status;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

export function getBountyIconMeta(icon?: string | null): BountyIconOption {
  const normalized = typeof icon === 'string' ? icon.trim().toLowerCase() : '';
  return BOUNTY_ICON_OPTIONS.find((item) => item.key === normalized) ?? BOUNTY_ICON_OPTIONS[BOUNTY_ICON_OPTIONS.length - 1];
}

export function validateBountyTaskInput(input: {
  title: string;
  rewardMinutes: number;
  profileIds: number[];
}): string | null {
  const trimmed = input.title.trim();
  if (!trimmed || trimmed.length > 100) {
    return 'Tên nhiệm vụ cần từ 1 đến 100 ký tự.';
  }
  if (!Number.isInteger(input.rewardMinutes) || input.rewardMinutes < 1 || input.rewardMinutes > 120) {
    return 'Phút thưởng nhiệm vụ cần từ 1 đến 120 phút.';
  }
  if (!Array.isArray(input.profileIds) || input.profileIds.length === 0) {
    return 'Hãy chọn ít nhất một hồ sơ trẻ em cho nhiệm vụ này.';
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

async function runBountyRequest<T>(
  action: (signal: AbortSignal) => Promise<T>,
  options: BountyRequestOptions = {}
): Promise<T> {
  try {
    return await withRequestTimeout(action, {
      signal: options.signal,
      timeoutMs: options.timeoutMs ?? DEFAULT_TIMEOUT_MS,
    });
  } catch (error) {
    if (isLikelyNetworkError(error)) {
      throw new BountyApiError(NETWORK_ERROR_MESSAGE, 0);
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
    throw new BountyApiError(message, response.status, retryAfter);
  }
  const serverMessage = typeof json?.message === 'string' && json.message.trim() ? json.message.trim() : '';
  if (!response.ok || json?.success !== true) {
    const message =
      response.status === 429
        ? formatRateLimitMessage(serverMessage, fallbackMessage, retryAfter)
        : serverMessage || fallbackMessage;
    throw new BountyApiError(message, response.status, retryAfter);
  }
  return { data: json.data, message: serverMessage };
}

function normalizeTask(raw: any): BountyTask | null {
  if (!raw || typeof raw !== 'object') return null;
  const id = typeof raw.id === 'number' ? raw.id : Number(raw.id);
  if (!Number.isSafeInteger(id) || id <= 0) return null;
  const profileIds = Array.isArray(raw.profileIds)
    ? raw.profileIds.map((item: unknown) => Number(item)).filter((item: number) => Number.isSafeInteger(item) && item > 0)
    : [];
  return {
    id,
    title: typeof raw.title === 'string' ? raw.title.trim() : '',
    rewardMinutes: typeof raw.rewardMinutes === 'number' ? raw.rewardMinutes : Number(raw.rewardMinutes) || 10,
    icon: typeof raw.icon === 'string' && raw.icon.trim() ? raw.icon.trim() : null,
    active: Boolean(raw.active),
    repeatableDaily: Boolean(raw.repeatableDaily),
    profileIds,
    createdAt: typeof raw.createdAt === 'string' ? raw.createdAt : '',
    updatedAt: typeof raw.updatedAt === 'string' ? raw.updatedAt : '',
  };
}

function normalizeSubmission(raw: any): BountySubmission | null {
  if (!raw || typeof raw !== 'object') return null;
  const id = typeof raw.id === 'number' ? raw.id : Number(raw.id);
  if (!Number.isSafeInteger(id) || id <= 0) return null;
  const rawStatus = typeof raw.status === 'string' ? raw.status.toUpperCase() : 'PENDING';
  const status: BountySubmissionStatus =
    rawStatus === 'APPROVED' || rawStatus === 'REJECTED' ? rawStatus : 'PENDING';
  return {
    id,
    taskId: Number(raw.taskId) || 0,
    taskTitle: typeof raw.taskTitle === 'string' ? raw.taskTitle : '',
    taskIcon: typeof raw.taskIcon === 'string' && raw.taskIcon.trim() ? raw.taskIcon.trim() : null,
    profileId: Number(raw.profileId) || 0,
    profileName: typeof raw.profileName === 'string' ? raw.profileName : '',
    profileAvatarKey: typeof raw.profileAvatarKey === 'string' ? raw.profileAvatarKey : 'bear',
    usageDay: typeof raw.usageDay === 'string' ? raw.usageDay : '',
    status,
    rewardMinutes: Number(raw.rewardMinutes) || 0,
    submittedAt: typeof raw.submittedAt === 'string' ? raw.submittedAt : '',
    decidedAt: typeof raw.decidedAt === 'string' ? raw.decidedAt : null,
    rejectNote: typeof raw.rejectNote === 'string' && raw.rejectNote.trim() ? raw.rejectNote.trim() : null,
  };
}

function normalizeKidTask(raw: any): KidBountyTask | null {
  if (!raw || typeof raw !== 'object') return null;
  const id = typeof raw.id === 'number' ? raw.id : Number(raw.id);
  if (!Number.isSafeInteger(id) || id <= 0) return null;
  const rawState = typeof raw.state === 'string' ? raw.state.toUpperCase() : 'AVAILABLE';
  const state: KidBountyTaskState =
    rawState === 'PENDING' || rawState === 'COMPLETED' || rawState === 'COMPLETED_TODAY' ? rawState : 'AVAILABLE';
  const pendingSubmissionId =
    typeof raw.pendingSubmissionId === 'number' && Number.isSafeInteger(raw.pendingSubmissionId)
      ? raw.pendingSubmissionId
      : null;
  return {
    id,
    title: typeof raw.title === 'string' ? raw.title.trim() : '',
    rewardMinutes: Number(raw.rewardMinutes) || 0,
    icon: typeof raw.icon === 'string' && raw.icon.trim() ? raw.icon.trim() : null,
    repeatableDaily: Boolean(raw.repeatableDaily),
    state,
    pendingSubmissionId,
  };
}

export async function fetchParentBountyBoard(
  token: string,
  options: BountyRequestOptions = {}
): Promise<ParentBountyBoard> {
  const url = `${options.baseUrl || API}/users/bounties`;
  return runBountyRequest(
    async (signal) => {
      const response = await apiFetch(url, { method: 'GET', headers: authHeaders(token), signal });
      const { data } = await parseEnvelope(response, 'Chưa tải được bảng nhiệm vụ đời thực.');
      return {
        tasks: Array.isArray(data?.tasks)
          ? data.tasks.map(normalizeTask).filter((item: BountyTask | null): item is BountyTask => item !== null)
          : [],
        submissions: Array.isArray(data?.submissions)
          ? data.submissions
              .map(normalizeSubmission)
              .filter((item: BountySubmission | null): item is BountySubmission => item !== null)
          : [],
      };
    },
    options
  );
}

export async function createBountyTask(
  token: string,
  payload: BountyTaskMutationPayload,
  options: BountyRequestOptions = {}
): Promise<BountyTask> {
  const url = `${options.baseUrl || API}/users/bounties/tasks`;
  return runBountyRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'POST',
        headers: authHeaders(token, true),
        body: JSON.stringify(payload),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa tạo được nhiệm vụ mới.');
      const normalized = normalizeTask(data);
      if (!normalized) throw new BountyApiError('Dữ liệu nhiệm vụ không hợp lệ.', 502);
      return normalized;
    },
    options
  );
}

export async function updateBountyTask(
  token: string,
  taskId: number,
  payload: BountyTaskMutationPayload,
  options: BountyRequestOptions = {}
): Promise<BountyTask> {
  const url = `${options.baseUrl || API}/users/bounties/tasks/${encodeURIComponent(String(taskId))}`;
  return runBountyRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'PUT',
        headers: authHeaders(token, true),
        body: JSON.stringify(payload),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa lưu được thay đổi nhiệm vụ.');
      const normalized = normalizeTask(data);
      if (!normalized) throw new BountyApiError('Dữ liệu nhiệm vụ không hợp lệ.', 502);
      return normalized;
    },
    options
  );
}

export async function deleteBountyTask(
  token: string,
  taskId: number,
  pin: string,
  options: BountyRequestOptions = {}
): Promise<void> {
  const url = `${options.baseUrl || API}/users/bounties/tasks/${encodeURIComponent(String(taskId))}`;
  return runBountyRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'DELETE',
        headers: authHeaders(token, true),
        body: JSON.stringify({ pin }),
        signal,
      });
      if (response.status === 204) return;
      await parseEnvelope(response, 'Chưa xóa được nhiệm vụ.');
    },
    options
  );
}

export async function fetchKidBountyBoard(
  token: string,
  options: BountyRequestOptions = {}
): Promise<KidBountyBoard> {
  const url = `${options.baseUrl || API}/users/bounties/kid`;
  return runBountyRequest(
    async (signal) => {
      const response = await apiFetch(url, { method: 'GET', headers: authHeaders(token), signal });
      const { data } = await parseEnvelope(response, 'Chưa tải được bảng nhiệm vụ của bé.');
      return {
        profileId: Number(data?.profileId) || 0,
        day: typeof data?.day === 'string' ? data.day : '',
        tasks: Array.isArray(data?.tasks)
          ? data.tasks.map(normalizeKidTask).filter((item: KidBountyTask | null): item is KidBountyTask => item !== null)
          : [],
        submissions: Array.isArray(data?.submissions)
          ? data.submissions
              .map(normalizeSubmission)
              .filter((item: BountySubmission | null): item is BountySubmission => item !== null)
          : [],
      };
    },
    options
  );
}

export async function submitKidBountyTask(
  token: string,
  taskId: number,
  options: BountyRequestOptions = {}
): Promise<BountySubmission> {
  const url = `${options.baseUrl || API}/users/bounties/tasks/${encodeURIComponent(String(taskId))}/submit`;
  return runBountyRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'POST',
        headers: authHeaders(token),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa nộp được nhiệm vụ này.');
      const normalized = normalizeSubmission(data);
      if (!normalized) throw new BountyApiError('Dữ liệu lượt nộp không hợp lệ.', 502);
      return normalized;
    },
    options
  );
}

export async function approveBountySubmission(
  token: string,
  submissionId: number,
  pin: string,
  note?: string,
  options: BountyRequestOptions = {}
): Promise<BountyDecisionResult> {
  const url = `${options.baseUrl || API}/users/bounties/submissions/${encodeURIComponent(String(submissionId))}/approve`;
  return runBountyRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'POST',
        headers: authHeaders(token, true),
        body: JSON.stringify({ pin, ...(note ? { note } : {}) }),
        signal,
      });
      const { data, message } = await parseEnvelope(response, 'Chưa duyệt được nhiệm vụ.');
      const submission = normalizeSubmission(data?.submission);
      if (!submission) throw new BountyApiError('Dữ liệu duyệt nhiệm vụ không hợp lệ.', 502);
      return {
        submission,
        screenTime: data?.screenTime as ScreenTimeStatus,
        message,
      };
    },
    options
  );
}

export async function rejectBountySubmission(
  token: string,
  submissionId: number,
  pin: string,
  note?: string,
  options: BountyRequestOptions = {}
): Promise<BountyDecisionResult> {
  const url = `${options.baseUrl || API}/users/bounties/submissions/${encodeURIComponent(String(submissionId))}/reject`;
  return runBountyRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'POST',
        headers: authHeaders(token, true),
        body: JSON.stringify({ pin, ...(note ? { note } : {}) }),
        signal,
      });
      const { data, message } = await parseEnvelope(response, 'Chưa từ chối được lượt nộp.');
      const submission = normalizeSubmission(data?.submission);
      if (!submission) throw new BountyApiError('Dữ liệu từ chối nhiệm vụ không hợp lệ.', 502);
      return {
        submission,
        screenTime: data?.screenTime as ScreenTimeStatus,
        message,
      };
    },
    options
  );
}
