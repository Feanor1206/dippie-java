export const PERMISSION_MESSAGE = 'Tài khoản của bạn không có quyền thực hiện việc này.';
export const SESSION_EXPIRED_MESSAGE = 'Phiên đăng nhập đã hết hạn. Bạn đăng nhập lại nhé.';
export const NETWORK_ERROR_MESSAGE = 'Không kết nối được với Dippie. Bạn kiểm tra lại mạng rồi thử lại nhé.';

type SessionListener = (status: 401 | 403, token: string) => void;
let sessionListener: SessionListener | undefined;

export function observeSessionResponses(listener: SessionListener) {
  sessionListener = listener;
  return () => {
    if (sessionListener === listener) sessionListener = undefined;
  };
}

export async function apiFetch(url: string, init: RequestInit = {}): Promise<Response> {
  const headers = init.headers as Record<string, string> | undefined;
  const token = headers?.Authorization?.match(/^Bearer (.+)$/)?.[1];
  const response = await fetch(url, token ? { ...init, cache: 'no-store' } : init);
  if (token && !init.signal?.aborted && (response.status === 401 || response.status === 403)) {
    sessionListener?.(response.status, token);
  }
  return response;
}

export class RequestTimeoutError extends Error {
  readonly isTimeout = true;

  constructor(message: string) {
    super(message);
    this.name = 'TimeoutError';
  }
}

export async function withRequestTimeout<T>(
  run: (signal: AbortSignal) => Promise<T>,
  { signal, timeoutMs = 7000 }: { signal?: AbortSignal | null; timeoutMs?: number } = {},
  message = 'Máy chủ phản hồi chậm. Bạn thử lại nhé.',
): Promise<T> {
  const controller = new AbortController();
  const abort = () => controller.abort(signal?.reason);
  if (signal?.aborted) abort();
  signal?.addEventListener('abort', abort, { once: true });
  const timer = setTimeout(() => controller.abort(new RequestTimeoutError(message)), timeoutMs);
  let rejectAbort: () => void = () => {};
  try {
    controller.signal.throwIfAborted();
    const canceled = new Promise<never>((_, reject) => {
      rejectAbort = () => reject(controller.signal.reason);
      controller.signal.addEventListener('abort', rejectAbort, { once: true });
    });
    return await Promise.race([canceled, run(controller.signal)]);
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener('abort', abort);
    controller.signal.removeEventListener('abort', rejectAbort);
  }
}

export function formatRetryAfterDuration(seconds: number): string {
  const safe = Math.max(1, Math.ceil(seconds));
  if (safe < 60) {
    return `${safe} giây`;
  }
  const minutes = Math.floor(safe / 60);
  const remainSeconds = safe % 60;
  return remainSeconds > 0 ? `${minutes} phút ${remainSeconds} giây` : `${minutes} phút`;
}

export function formatRateLimitMessage(
  serverMessage: string,
  fallback: string,
  retryAfterSeconds?: number
): string {
  const base = (serverMessage || '').trim() || fallback;
  if (!retryAfterSeconds || !Number.isFinite(retryAfterSeconds) || retryAfterSeconds <= 0) {
    return base;
  }
  const waitLabel = formatRetryAfterDuration(retryAfterSeconds);
  if (/sau ít phút\.?$/i.test(base)) {
    return base.replace(/sau ít phút\.?$/i, `sau ${waitLabel}.`);
  }
  if (/\d+\s*(giây|phút)/i.test(base)) {
    return base;
  }
  return `${base.replace(/\.+$/, '')}. Thử lại sau ${waitLabel}.`;
}

export function isLikelyNetworkError(error: unknown): boolean {
  if (!error || typeof error !== 'object') return false;
  const candidate = error as { name?: unknown; message?: unknown };
  const name = typeof candidate.name === 'string' ? candidate.name : '';
  if (name === 'AbortError' || name === 'TimeoutError') return false;
  if (error instanceof TypeError || name === 'TypeError') return true;
  const message = typeof candidate.message === 'string' ? candidate.message : '';
  return /failed to fetch|networkerror|load failed|fetch failed/i.test(message);
}

export async function readApiMessage(response: Response, fallback: string): Promise<string> {
  if (response.status === 403) return PERMISSION_MESSAGE;
  if (response.status === 404 || response.status === 405) return fallback;
  const data = await response.json().catch(() => null);
  for (const message of [data?.detail, data?.message]) {
    if (typeof message === 'string' && message.trim()) return message.trim();
  }
  return fallback;
}
