export const PERMISSION_MESSAGE = 'Tài khoản của bạn không có quyền thực hiện việc này.';
export const SESSION_EXPIRED_MESSAGE = 'Phiên đăng nhập đã hết hạn. Bạn đăng nhập lại nhé.';

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

export async function readApiMessage(response: Response, fallback: string): Promise<string> {
  if (response.status === 403) return PERMISSION_MESSAGE;
  if (response.status === 404 || response.status === 405) return fallback;
  const data = await response.json().catch(() => null);
  for (const message of [data?.detail, data?.message]) {
    if (typeof message === 'string' && message.trim()) return message.trim();
  }
  return fallback;
}
