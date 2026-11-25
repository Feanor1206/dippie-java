import type { AuthUser } from '@/lib/context/AuthContext';

const API = '/api';
const DEFAULT_TIMEOUT_MS = 8000;
const MIN_PASSWORD_LENGTH = 6;
const MAX_PASSWORD_UTF8_BYTES = 72;
const MAX_EMAIL_LENGTH = 100;
const MAX_FULL_NAME_LENGTH = 100;

export type AccountRequestOptions = {
  baseUrl?: string;
  signal?: AbortSignal;
  timeoutMs?: number;
};

export type UpdateProfilePayload = {
  fullName: string | null;
  email: string;
};

export type UpdateProfileResult = {
  user: AuthUser;
  message: string;
};

export type ChangePasswordPayload = {
  currentPassword: string;
  newPassword: string;
};

export type ChangePasswordResult = {
  token: string;
  user: AuthUser;
  message: string;
};

export class AccountApiError extends Error {
  status: number;
  isCanceled: boolean;
  isTimeout: boolean;

  constructor(
    message: string,
    status: number,
    options: { isCanceled?: boolean; isTimeout?: boolean } = {}
  ) {
    super(message);
    this.name = options.isCanceled ? 'AbortError' : 'AccountApiError';
    this.status = status;
    this.isCanceled = Boolean(options.isCanceled);
    this.isTimeout = Boolean(options.isTimeout);
  }
}

function nonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

export function utf8ByteLength(value: string): number {
  if (typeof TextEncoder !== 'undefined') {
    return new TextEncoder().encode(value).length;
  }
  return unescape(encodeURIComponent(value)).length;
}

export function validateProfileInput(input: {
  fullName?: string | null;
  email: string;
}): string | null {
  const email = input.email.trim();
  if (!email || !email.includes('@') || email.length > MAX_EMAIL_LENGTH) {
    return 'Định dạng email không hợp lệ.';
  }
  if (typeof input.fullName === 'string' && input.fullName.length > MAX_FULL_NAME_LENGTH) {
    return 'Họ và tên tối đa 100 ký tự.';
  }
  return null;
}

export function validatePasswordChangeInput(input: {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}): string | null {
  if (!input.currentPassword) {
    return 'Vui lòng nhập mật khẩu hiện tại.';
  }
  if (!input.newPassword || input.newPassword.length < MIN_PASSWORD_LENGTH) {
    return 'Mật khẩu mới phải có ít nhất 6 ký tự.';
  }
  if (utf8ByteLength(input.newPassword) > MAX_PASSWORD_UTF8_BYTES) {
    return 'Mật khẩu mới được dùng tối đa 72 byte UTF-8.';
  }
  if (input.newPassword !== input.confirmPassword) {
    return 'Xác nhận mật khẩu mới không khớp.';
  }
  if (input.newPassword === input.currentPassword) {
    return 'Mật khẩu mới phải khác mật khẩu hiện tại.';
  }
  return null;
}

export function normalizeAccountUser(raw: any): AuthUser {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    throw new AccountApiError('Phản hồi thông tin tài khoản không hợp lệ.', 502);
  }
  let numericId: number | null = null;
  if (typeof raw.id === 'number' && Number.isFinite(raw.id) && raw.id > 0) {
    numericId = raw.id;
  } else if (typeof raw.id === 'string' && /^\d+$/.test(raw.id.trim())) {
    const parsed = parseInt(raw.id.trim(), 10);
    if (parsed > 0) numericId = parsed;
  }
  if (
    numericId === null ||
    !nonEmptyString(raw.username) ||
    !nonEmptyString(raw.email) ||
    !raw.email.includes('@')
  ) {
    throw new AccountApiError('Dữ liệu tài khoản thiếu trường bắt buộc.', 502);
  }
  return {
    id: numericId,
    username: raw.username.trim(),
    email: raw.email.trim(),
    fullName: nonEmptyString(raw.fullName) ? raw.fullName.trim() : undefined,
    role: nonEmptyString(raw.role) ? raw.role.trim() : 'ROLE_USER',
  };
}

export function normalizePasswordChangeData(raw: any): { token: string; user: AuthUser } {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw) || !nonEmptyString(raw.token)) {
    throw new AccountApiError('Phản hồi đổi mật khẩu không hợp lệ.', 502);
  }
  return {
    token: raw.token.trim(),
    user: normalizeAccountUser(raw),
  };
}

async function executeWithTimeout<T>(
  action: (signal: AbortSignal) => Promise<T>,
  options: AccountRequestOptions = {}
): Promise<T> {
  const timeoutMs =
    options.timeoutMs && options.timeoutMs > 0 ? options.timeoutMs : DEFAULT_TIMEOUT_MS;
  if (options.signal?.aborted) {
    throw new AccountApiError('Yêu cầu đã bị hủy.', 499, { isCanceled: true });
  }
  const controller = new AbortController();
  return new Promise<T>((resolve, reject) => {
    let settled = false;
    let timedOut = false;
    let callerCanceled = false;
    let timer: ReturnType<typeof setTimeout> | null = null;

    const cleanup = () => {
      if (timer !== null) clearTimeout(timer);
      options.signal?.removeEventListener('abort', handleCallerAbort);
    };

    const finishError = (error: unknown) => {
      if (settled) return;
      settled = true;
      cleanup();
      if (callerCanceled) {
        reject(new AccountApiError('Yêu cầu đã bị hủy.', 499, { isCanceled: true }));
      } else if (timedOut) {
        reject(
          new AccountApiError('Kết nối máy chủ quá thời gian chờ.', 408, { isTimeout: true })
        );
      } else if (error instanceof AccountApiError) {
        reject(error);
      } else {
        reject(new AccountApiError('Không thể kết nối đến máy chủ tài khoản.', 0));
      }
    };

    const handleCallerAbort = () => {
      if (settled) return;
      callerCanceled = true;
      controller.abort(options.signal?.reason);
      finishError(options.signal?.reason);
    };

    options.signal?.addEventListener('abort', handleCallerAbort, { once: true });
    timer = setTimeout(() => {
      if (settled) return;
      timedOut = true;
      controller.abort();
      finishError(new Error('timeout'));
    }, timeoutMs);

    Promise.resolve()
      .then(() => action(controller.signal))
      .then((value) => {
        if (settled) return;
        settled = true;
        cleanup();
        resolve(value);
      })
      .catch(finishError);
  });
}

function authHeaders(token: string): Record<string, string> {
  if (!nonEmptyString(token)) {
    throw new AccountApiError('Chưa xác thực người dùng.', 401);
  }
  return {
    Accept: 'application/json',
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token.trim()}`,
  };
}

async function parseEnvelope(
  res: Response,
  fallbackError: string
): Promise<{ data: any; message: string }> {
  let json: any = null;
  try {
    json = await res.json();
  } catch {
    if (!res.ok) {
      if (res.status === 401) {
        throw new AccountApiError('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.', 401);
      }
      throw new AccountApiError(fallbackError, res.status);
    }
    throw new AccountApiError('Phản hồi từ máy chủ không phải JSON hợp lệ.', 502);
  }

  const serverMessage = nonEmptyString(json?.message) ? json.message.trim() : '';

  if (!res.ok || json?.success !== true) {
    const status = res.ok ? 502 : res.status;
    if (serverMessage) {
      throw new AccountApiError(serverMessage, status);
    }
    if (status === 401) {
      throw new AccountApiError('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.', 401);
    }
    throw new AccountApiError(fallbackError, status);
  }

  if (!Object.prototype.hasOwnProperty.call(json, 'data')) {
    throw new AccountApiError('Phản hồi từ máy chủ thiếu dữ liệu.', 502);
  }

  return { data: json.data, message: serverMessage };
}

export async function updateAccountProfile(
  token: string,
  payload: UpdateProfilePayload,
  options: AccountRequestOptions = {}
): Promise<UpdateProfileResult> {
  const headers = authHeaders(token);
  const url = `${options.baseUrl || API}/users/me`;
  const trimmedFullName =
    typeof payload.fullName === 'string' && payload.fullName.trim().length > 0
      ? payload.fullName.trim()
      : null;
  const body = JSON.stringify({
    fullName: trimmedFullName,
    email: payload.email.trim(),
  });

  return executeWithTimeout(async (signal) => {
    const res = await fetch(url, { method: 'PUT', headers, body, signal });
    const envelope = await parseEnvelope(res, 'Cập nhật thông tin tài khoản thất bại.');
    return {
      user: normalizeAccountUser(envelope.data),
      message: envelope.message || 'Cập nhật thông tin tài khoản thành công',
    };
  }, options);
}

export async function changeAccountPassword(
  token: string,
  payload: ChangePasswordPayload,
  options: AccountRequestOptions = {}
): Promise<ChangePasswordResult> {
  const headers = authHeaders(token);
  const url = `${options.baseUrl || API}/users/me/password`;
  const body = JSON.stringify({
    currentPassword: payload.currentPassword,
    newPassword: payload.newPassword,
  });

  return executeWithTimeout(async (signal) => {
    const res = await fetch(url, { method: 'POST', headers, body, signal });
    const envelope = await parseEnvelope(res, 'Đổi mật khẩu thất bại.');
    const normalized = normalizePasswordChangeData(envelope.data);
    return {
      token: normalized.token,
      user: normalized.user,
      message: envelope.message || 'Đổi mật khẩu thành công',
    };
  }, options);
}
