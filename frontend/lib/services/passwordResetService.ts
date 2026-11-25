import { readApiMessage, RequestTimeoutError, withRequestTimeout } from '@/lib/services/apiRequest';

const API = '/api';
const DEFAULT_TIMEOUT_MS = 8000;
const MIN_PASSWORD_LENGTH = 6;
const RECOMMENDED_PASSWORD_LENGTH = 10;
const MAX_PASSWORD_UTF8_BYTES = 72;
const MAX_EMAIL_LENGTH = 100;

export const PASSWORD_RULES_HINT = 'Mật khẩu dài từ 6 ký tự, tối đa khoảng 70 chữ (chữ có dấu tính nhiều hơn).';
export const PASSWORD_STRENGTH_TIP =
  'Nên dài từ 10 ký tự trở lên, có cả chữ và số để bảo vệ tài khoản tốt hơn.';
export const PASSWORD_RESET_REQUEST_SENT_MESSAGE =
  'Nếu email này đã đăng ký Dippie, Dippie vừa gửi liên kết đặt lại mật khẩu. Bạn kiểm tra hộp thư đến và cả mục thư rác trong vài phút tới nhé.';
export const PASSWORD_RESET_UNAVAILABLE_MESSAGE =
  'Tính năng đặt lại mật khẩu tạm chưa bật trên máy chủ. Bạn thử lại sau nhé.';
export const PASSWORD_RESET_RATE_LIMIT_MESSAGE =
  'Bạn vừa gửi quá nhiều yêu cầu. Chờ vài phút rồi thử lại nhé.';
export const PASSWORD_RESET_INVALID_TOKEN_MESSAGE =
  'Liên kết đặt lại mật khẩu không hợp lệ, đã được dùng hoặc đã hết hạn.';

export type PasswordResetErrorCode =
  | 'validation'
  | 'rate_limit'
  | 'invalid_token'
  | 'unavailable'
  | 'timeout'
  | 'canceled'
  | 'network'
  | 'server';

export class PasswordResetError extends Error {
  status: number;
  code: PasswordResetErrorCode;

  constructor(message: string, status: number, code: PasswordResetErrorCode) {
    super(message);
    this.name = code === 'canceled' ? 'AbortError' : 'PasswordResetError';
    this.status = status;
    this.code = code;
  }
}

export type PasswordStrength = {
  level: 'empty' | 'weak' | 'fair' | 'good';
  label: string;
  lengthOk: boolean;
  withinMaxBytes: boolean;
  notBlankOnly: boolean;
  hasRecommendedLength: boolean;
  hasLetterAndNumber: boolean;
};

export function utf8Length(value: string): number {
  if (typeof TextEncoder !== 'undefined') {
    return new TextEncoder().encode(value).length;
  }
  return unescape(encodeURIComponent(value)).length;
}

export function evaluatePasswordStrength(password: string): PasswordStrength {
  const lengthOk = password.length >= MIN_PASSWORD_LENGTH;
  const notBlankOnly = password.trim().length > 0;
  const withinMaxBytes = utf8Length(password) <= MAX_PASSWORD_UTF8_BYTES;
  const hasRecommendedLength = password.length >= RECOMMENDED_PASSWORD_LENGTH;
  const hasLetterAndNumber = /[A-Za-zÀ-ỹ]/.test(password) && /\d/.test(password);

  if (!password) {
    return {
      level: 'empty',
      label: '',
      lengthOk: false,
      withinMaxBytes: true,
      notBlankOnly: false,
      hasRecommendedLength: false,
      hasLetterAndNumber: false,
    };
  }
  if (!lengthOk || !notBlankOnly || !withinMaxBytes) {
    return {
      level: 'weak',
      label: 'Chưa đạt yêu cầu',
      lengthOk,
      withinMaxBytes,
      notBlankOnly,
      hasRecommendedLength,
      hasLetterAndNumber,
    };
  }
  if (hasRecommendedLength && hasLetterAndNumber) {
    return {
      level: 'good',
      label: 'Mật khẩu mạnh',
      lengthOk,
      withinMaxBytes,
      notBlankOnly,
      hasRecommendedLength,
      hasLetterAndNumber,
    };
  }
  return {
    level: 'fair',
    label: 'Mật khẩu hợp lệ',
    lengthOk,
    withinMaxBytes,
    notBlankOnly,
    hasRecommendedLength,
    hasLetterAndNumber,
  };
}

export function validateResetEmail(email: string): string | null {
  const clean = email.trim();
  if (!clean) return 'Bạn nhập email tài khoản nhé.';
  if (clean.length > MAX_EMAIL_LENGTH || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(clean)) {
    return 'Email chưa đúng định dạng.';
  }
  return null;
}

export function validateNewPassword(newPassword: string, confirmPassword?: string): string | null {
  if (!newPassword) return 'Bạn nhập mật khẩu mới nhé.';
  if (newPassword.length < MIN_PASSWORD_LENGTH) {
    return 'Mật khẩu mới cần có ít nhất 6 ký tự.';
  }
  if (!newPassword.trim()) {
    return 'Mật khẩu mới không được chỉ có khoảng trắng.';
  }
  if (utf8Length(newPassword) > MAX_PASSWORD_UTF8_BYTES) {
    return 'Mật khẩu dài quá. Bạn rút ngắn lại nhé, chữ có dấu chiếm chỗ nhiều hơn chữ không dấu.';
  }
  if (confirmPassword !== undefined && newPassword !== confirmPassword) {
    return 'Mật khẩu nhập lại chưa khớp.';
  }
  return null;
}

export async function requestPasswordReset(
  email: string,
  options: { signal?: AbortSignal; timeoutMs?: number; baseUrl?: string } = {}
): Promise<{ message: string }> {
  const validationError = validateResetEmail(email);
  if (validationError) {
    throw new PasswordResetError(validationError, 400, 'validation');
  }
  const cleanEmail = email.trim().toLowerCase();
  const url = `${options.baseUrl || API}/auth/password-reset/request`;

  try {
    return await withRequestTimeout(
      async (signal) => {
        const response = await fetch(url, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Accept: 'application/json',
          },
          body: JSON.stringify({ email: cleanEmail }),
          signal,
        });
        if (response.status === 202 || response.ok) {
          return { message: PASSWORD_RESET_REQUEST_SENT_MESSAGE };
        }
        if (response.status === 429) {
          const message = await readApiMessage(response, PASSWORD_RESET_RATE_LIMIT_MESSAGE);
          throw new PasswordResetError(message, 429, 'rate_limit');
        }
        if (response.status === 404 || response.status === 405 || response.status === 501) {
          throw new PasswordResetError(PASSWORD_RESET_UNAVAILABLE_MESSAGE, response.status, 'unavailable');
        }
        const message = await readApiMessage(response, 'Chưa gửi được yêu cầu đặt lại mật khẩu. Bạn thử lại nhé.');
        throw new PasswordResetError(message, response.status, 'server');
      },
      { signal: options.signal, timeoutMs: options.timeoutMs || DEFAULT_TIMEOUT_MS },
      'Máy chủ phản hồi chậm. Bạn thử lại nhé.'
    );
  } catch (err: unknown) {
    if (err instanceof PasswordResetError) throw err;
    if (options.signal?.aborted) {
      throw new PasswordResetError('Yêu cầu đã được hủy.', 499, 'canceled');
    }
    if (err instanceof RequestTimeoutError) {
      throw new PasswordResetError(err.message, 408, 'timeout');
    }
    throw new PasswordResetError('Không kết nối được với Dippie. Bạn kiểm tra lại mạng rồi thử lại nhé.', 0, 'network');
  }
}

export async function confirmPasswordReset(
  token: string,
  newPassword: string,
  options: { signal?: AbortSignal; timeoutMs?: number; baseUrl?: string } = {}
): Promise<void> {
  const cleanToken = token.trim();
  if (!cleanToken) {
    throw new PasswordResetError(PASSWORD_RESET_INVALID_TOKEN_MESSAGE, 400, 'invalid_token');
  }
  const validationError = validateNewPassword(newPassword);
  if (validationError) {
    throw new PasswordResetError(validationError, 400, 'validation');
  }
  const url = `${options.baseUrl || API}/auth/password-reset/confirm`;

  try {
    await withRequestTimeout(
      async (signal) => {
        const response = await fetch(url, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Accept: 'application/json',
          },
          body: JSON.stringify({ token: cleanToken, newPassword }),
          signal,
        });
        if (response.status === 204 || response.ok) {
          return;
        }
        if (response.status === 400 || response.status === 410 || response.status === 422) {
          const message = await readApiMessage(response, PASSWORD_RESET_INVALID_TOKEN_MESSAGE);
          throw new PasswordResetError(message, response.status, 'invalid_token');
        }
        if (response.status === 429) {
          const message = await readApiMessage(response, PASSWORD_RESET_RATE_LIMIT_MESSAGE);
          throw new PasswordResetError(message, 429, 'rate_limit');
        }
        if (response.status === 404 || response.status === 405 || response.status === 501) {
          throw new PasswordResetError(PASSWORD_RESET_UNAVAILABLE_MESSAGE, response.status, 'unavailable');
        }
        const message = await readApiMessage(response, 'Chưa đổi được mật khẩu lúc này. Bạn thử lại nhé.');
        throw new PasswordResetError(message, response.status, 'server');
      },
      { signal: options.signal, timeoutMs: options.timeoutMs || DEFAULT_TIMEOUT_MS },
      'Máy chủ phản hồi chậm. Bạn thử lại nhé.'
    );
  } catch (err: unknown) {
    if (err instanceof PasswordResetError) throw err;
    if (options.signal?.aborted) {
      throw new PasswordResetError('Yêu cầu đã được hủy.', 499, 'canceled');
    }
    if (err instanceof RequestTimeoutError) {
      throw new PasswordResetError(err.message, 408, 'timeout');
    }
    throw new PasswordResetError('Không kết nối được với Dippie. Bạn kiểm tra lại mạng rồi thử lại nhé.', 0, 'network');
  }
}
