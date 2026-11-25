import { genreLabel } from '@/lib/labels';

export type SeriesCardGenre = {
  id: string;
  label: string;
};

export type SeriesCard = {
  id: string;
  title: string;
  originalTitle: string | null;
  synopsis: string | null;
  releaseYearStart: number | null;
  releaseYearEnd: number | null;
  genres: SeriesCardGenre[];
  seasonCount: number | null;
  episodeCount: number | null;
  posterUrl: string | null;
  backdropUrl: string | null;
};

export type SeriesCardFetchOptions = {
  baseUrl?: string;
  signal?: AbortSignal;
  timeoutMs?: number;
  forceRefresh?: boolean;
};

export class SeriesCardApiError extends Error {
  status: number;
  isCanceled: boolean;
  isTimeout: boolean;

  constructor(message: string, status: number, options: { isCanceled?: boolean; isTimeout?: boolean } = {}) {
    super(message);
    this.name = options.isCanceled ? 'AbortError' : 'SeriesCardApiError';
    this.status = status;
    this.isCanceled = Boolean(options.isCanceled);
    this.isTimeout = Boolean(options.isTimeout);
  }
}

const API = '/api';
const DEFAULT_TIMEOUT_MS = 7000;
const CACHE_TTL_MS = 60_000;

type CacheEntry = {
  expiresAt: number;
  data: SeriesCard[];
};

const cardsCache = new Map<string, CacheEntry>();

export function clearSeriesCardsCache(): void {
  cardsCache.clear();
}

function nullableString(value: unknown): string | null {
  return typeof value === 'string' && value.trim().length > 0 ? value.trim() : null;
}

function nullableYear(value: unknown): number | null {
  if (typeof value !== 'number' || !Number.isFinite(value)) return null;
  const rounded = Math.trunc(value);
  return rounded > 1800 && rounded < 3000 ? rounded : null;
}

function nullableCount(value: unknown): number | null {
  if (typeof value !== 'number' || !Number.isFinite(value)) return null;
  const floored = Math.floor(value);
  return floored >= 0 ? floored : null;
}

function normalizeGenres(raw: unknown): SeriesCardGenre[] {
  if (!Array.isArray(raw)) return [];
  const seen = new Set<string>();
  const result: SeriesCardGenre[] = [];
  for (const item of raw) {
    let id = '';
    let label = '';
    if (typeof item === 'string') {
      id = item.trim();
      label = id ? genreLabel(id) : '';
    } else if (item && typeof item === 'object' && !Array.isArray(item)) {
      const record = item as { id?: unknown; label?: unknown };
      id = typeof record.id === 'string' ? record.id.trim() : '';
      const rawLabel = typeof record.label === 'string' ? record.label.trim() : '';
      label = rawLabel || (id ? genreLabel(id) : '');
    }
    if (!id) continue;
    const key = id.toLocaleLowerCase('vi');
    if (seen.has(key)) continue;
    seen.add(key);
    result.push({ id, label: label || id });
  }
  return result;
}

export function normalizeSeriesCard(raw: unknown): SeriesCard {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    throw new SeriesCardApiError('Dữ liệu bộ phim không hợp lệ.', 502);
  }
  const obj = raw as Record<string, unknown>;
  const id = nullableString(obj.id);
  const title = nullableString(obj.title);
  if (!id) {
    throw new SeriesCardApiError('Bộ phim thiếu ID bắt buộc.', 502);
  }
  if (!title) {
    throw new SeriesCardApiError(`Bộ phim [${id}] thiếu tiêu đề bắt buộc.`, 502);
  }

  return {
    id,
    title,
    originalTitle: nullableString(obj.originalTitle),
    synopsis: nullableString(obj.synopsis),
    releaseYearStart: nullableYear(obj.releaseYearStart),
    releaseYearEnd: nullableYear(obj.releaseYearEnd),
    genres: normalizeGenres(obj.genres),
    seasonCount: nullableCount(obj.seasonCount),
    episodeCount: nullableCount(obj.episodeCount),
    posterUrl: nullableString(obj.posterUrl),
    backdropUrl: nullableString(obj.backdropUrl),
  };
}

function getAuthHeader(): Record<string, string> {
  if (typeof window !== 'undefined') {
    try {
      const token = window.localStorage.getItem('dippie_auth_token') || window.localStorage.getItem('token');
      if (token) return { Authorization: `Bearer ${token}` };
    } catch {
      return {};
    }
  }
  return {};
}

async function executeWithTimeout<T>(
  action: (signal: AbortSignal) => Promise<T>,
  options: { signal?: AbortSignal; timeoutMs?: number } = {}
): Promise<T> {
  const timeoutMs = typeof options.timeoutMs === 'number' && options.timeoutMs > 0 ? options.timeoutMs : DEFAULT_TIMEOUT_MS;
  if (options.signal?.aborted) {
    throw new SeriesCardApiError('Yêu cầu đã bị hủy.', 499, { isCanceled: true });
  }

  const controller = new AbortController();
  let timedOut = false;
  let callerCanceled = false;

  const abortListener = () => {
    callerCanceled = true;
    controller.abort(options.signal?.reason);
  };

  if (options.signal) {
    options.signal.addEventListener('abort', abortListener, { once: true });
  }

  const timer = setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, timeoutMs);

  try {
    return await action(controller.signal);
  } catch (err: unknown) {
    if (callerCanceled || options.signal?.aborted) {
      throw new SeriesCardApiError('Yêu cầu đã bị hủy.', 499, { isCanceled: true });
    }
    if (timedOut || ((err as { name?: string })?.name === 'AbortError' && !callerCanceled)) {
      throw new SeriesCardApiError('Yêu cầu danh sách bộ phim quá thời gian phản hồi.', 408, { isTimeout: true });
    }
    if (err instanceof SeriesCardApiError) {
      throw err;
    }
    throw new SeriesCardApiError('Không thể kết nối đến máy chủ danh mục bộ phim.', 0);
  } finally {
    clearTimeout(timer);
    if (options.signal) {
      options.signal.removeEventListener('abort', abortListener);
    }
  }
}

export async function listSeriesCards(options: SeriesCardFetchOptions = {}): Promise<SeriesCard[]> {
  const baseUrl = options.baseUrl || API;
  const cached = cardsCache.get(baseUrl);
  if (!options.forceRefresh && cached && cached.expiresAt > Date.now()) {
    return cached.data;
  }

  const url = `${baseUrl}/catalog/series/cards`;
  const cards = await executeWithTimeout(async (signal) => {
    const res = await fetch(url, {
      method: 'GET',
      headers: { Accept: 'application/json', ...getAuthHeader() },
      signal,
    });
    if (!res.ok) {
      throw new SeriesCardApiError(`Không thể tải danh sách bộ phim (${res.status}).`, res.status);
    }
    let body: unknown;
    try {
      body = await res.json();
    } catch {
      throw new SeriesCardApiError('Phản hồi danh sách bộ phim không phải JSON hợp lệ.', 502);
    }
    if (!Array.isArray(body)) {
      throw new SeriesCardApiError('Danh sách bộ phim không hợp lệ (thiếu mảng dữ liệu).', 502);
    }
    return body.map(normalizeSeriesCard);
  }, options);

  cardsCache.set(baseUrl, { data: cards, expiresAt: Date.now() + CACHE_TTL_MS });
  return cards;
}

export async function getSeriesCardById(
  seriesId: string,
  options: SeriesCardFetchOptions = {}
): Promise<SeriesCard | null> {
  const cleanId = typeof seriesId === 'string' ? seriesId.trim() : '';
  if (!cleanId) return null;
  const cards = await listSeriesCards(options);
  return cards.find((card) => card.id === cleanId) || null;
}
