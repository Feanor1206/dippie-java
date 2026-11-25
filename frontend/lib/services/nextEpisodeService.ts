const API = '/api';
const DEFAULT_TIMEOUT_MS = 7000;

export type NextEpisodeReason = 'RESUME' | 'NEXT_UNWATCHED';

export type NextEpisodeRecommendation = {
  seriesId: string;
  episodeId: string;
  episodeTitle: string;
  seasonNumber: number | null;
  episodeNumber: number | null;
  resumePositionMs: number;
  reason: NextEpisodeReason;
};

export type NextEpisodeResult =
  | { status: 'ready'; recommendation: NextEpisodeRecommendation }
  | { status: 'completed'; message: string };

export type NextEpisodeRequestOptions = {
  baseUrl?: string;
  signal?: AbortSignal;
  timeoutMs?: number;
};

export class NextEpisodeApiError extends Error {
  status: number;
  isCanceled: boolean;
  isTimeout: boolean;

  constructor(
    message: string,
    status: number,
    options: { isCanceled?: boolean; isTimeout?: boolean } = {}
  ) {
    super(message);
    this.name = options.isCanceled ? 'AbortError' : 'NextEpisodeApiError';
    this.status = status;
    this.isCanceled = Boolean(options.isCanceled);
    this.isTimeout = Boolean(options.isTimeout);
  }
}

function nonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function nullableNumber(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0
    ? Math.floor(value)
    : null;
}

export function formatResumeTime(ms: number): string {
  if (typeof ms !== 'number' || !Number.isFinite(ms) || ms <= 0) {
    return '00:00';
  }
  const totalSeconds = Math.floor(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}

export function formatNextEpisodeLabel(
  recommendation: Pick<NextEpisodeRecommendation, 'reason' | 'episodeNumber' | 'resumePositionMs'>
): { label: string; resumeTime: string | null } {
  if (recommendation.reason === 'RESUME') {
    const label =
      recommendation.episodeNumber !== null
        ? `Tiếp tục xem T${recommendation.episodeNumber}`
        : 'Tiếp tục xem';
    return {
      label,
      resumeTime: formatResumeTime(recommendation.resumePositionMs),
    };
  }
  const label =
    recommendation.episodeNumber !== null
      ? `Xem tập ${recommendation.episodeNumber}`
      : 'Xem tập tiếp theo';
  return {
    label,
    resumeTime: null,
  };
}

export function buildNextEpisodeHref(
  recommendation: Pick<NextEpisodeRecommendation, 'seriesId' | 'episodeId' | 'reason' | 'resumePositionMs'>,
  assetId?: string | null
): string {
  const params = new URLSearchParams();
  params.set('episode', recommendation.episodeId);
  if (
    recommendation.reason === 'RESUME' &&
    typeof recommendation.resumePositionMs === 'number' &&
    Number.isFinite(recommendation.resumePositionMs) &&
    recommendation.resumePositionMs > 0
  ) {
    if (nonEmptyString(assetId)) {
      params.set('asset', assetId.trim());
    }
    params.set('t', String(Math.floor(recommendation.resumePositionMs)));
    params.set('timeline', 'file');
  }
  return `/series/${encodeURIComponent(recommendation.seriesId)}?${params.toString()}`;
}

export function normalizeNextEpisodeRecommendation(raw: any): NextEpisodeRecommendation {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    throw new NextEpisodeApiError('Dữ liệu tập tiếp theo không hợp lệ.', 502);
  }
  if (
    !nonEmptyString(raw.seriesId) ||
    !nonEmptyString(raw.episodeId) ||
    !nonEmptyString(raw.episodeTitle) ||
    (raw.reason !== 'RESUME' && raw.reason !== 'NEXT_UNWATCHED') ||
    typeof raw.resumePositionMs !== 'number' ||
    !Number.isFinite(raw.resumePositionMs) ||
    raw.resumePositionMs < 0
  ) {
    throw new NextEpisodeApiError('Dữ liệu tập tiếp theo thiếu trường bắt buộc.', 502);
  }
  return {
    seriesId: raw.seriesId.trim(),
    episodeId: raw.episodeId.trim(),
    episodeTitle: raw.episodeTitle.trim(),
    seasonNumber: nullableNumber(raw.seasonNumber),
    episodeNumber: nullableNumber(raw.episodeNumber),
    resumePositionMs: Math.floor(raw.resumePositionMs),
    reason: raw.reason,
  };
}

async function executeWithTimeout<T>(
  action: (signal: AbortSignal) => Promise<T>,
  options: NextEpisodeRequestOptions = {}
): Promise<T> {
  const timeoutMs =
    options.timeoutMs && options.timeoutMs > 0 ? options.timeoutMs : DEFAULT_TIMEOUT_MS;
  if (options.signal?.aborted) {
    throw new NextEpisodeApiError('Yêu cầu đã bị hủy.', 499, { isCanceled: true });
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
        reject(new NextEpisodeApiError('Yêu cầu đã bị hủy.', 499, { isCanceled: true }));
      } else if (timedOut) {
        reject(
          new NextEpisodeApiError('Yêu cầu tập tiếp theo quá thời gian phản hồi.', 408, {
            isTimeout: true,
          })
        );
      } else if (error instanceof NextEpisodeApiError) {
        reject(error);
      } else {
        reject(new NextEpisodeApiError('Không thể kết nối đến dịch vụ tập tiếp theo.', 0));
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

export async function getNextEpisode(
  token: string,
  seriesId: string,
  options: NextEpisodeRequestOptions = {}
): Promise<NextEpisodeResult> {
  if (!nonEmptyString(token)) {
    throw new NextEpisodeApiError('Chưa xác thực người dùng.', 401);
  }
  if (!nonEmptyString(seriesId)) {
    throw new NextEpisodeApiError('Mã bộ phim không hợp lệ.', 400);
  }
  const params = new URLSearchParams();
  params.set('seriesId', seriesId.trim());
  const url = `${options.baseUrl || API}/users/next-episode?${params.toString()}`;
  const headers = {
    Accept: 'application/json',
    Authorization: `Bearer ${token.trim()}`,
  };

  return executeWithTimeout(async (signal) => {
    const res = await fetch(url, { headers, signal });
    let json: any = null;
    try {
      json = await res.json();
    } catch {
      if (!res.ok) {
        throw new NextEpisodeApiError(
          res.status === 401
            ? 'Phiên đăng nhập đã hết hạn.'
            : `Tải tập tiếp theo thất bại (HTTP ${res.status}).`,
          res.status
        );
      }
      throw new NextEpisodeApiError('Phản hồi tập tiếp theo không phải JSON hợp lệ.', 502);
    }

    const serverMessage = nonEmptyString(json?.message) ? json.message.trim() : '';

    if (!res.ok || json?.success !== true) {
      const status = res.ok ? 502 : res.status;
      throw new NextEpisodeApiError(
        serverMessage ||
          (status === 401
            ? 'Phiên đăng nhập đã hết hạn.'
            : `Tải tập tiếp theo thất bại (HTTP ${status}).`),
        status
      );
    }

    if (!Object.prototype.hasOwnProperty.call(json, 'data')) {
      throw new NextEpisodeApiError('Phản hồi tập tiếp theo thiếu dữ liệu.', 502);
    }

    if (json.data === null) {
      return {
        status: 'completed',
        message: serverMessage || 'Bạn đã xem hết bộ phim này.',
      };
    }

    return {
      status: 'ready',
      recommendation: normalizeNextEpisodeRecommendation(json.data),
    };
  }, options);
}
