import { getStoredAuthToken, SESSION_RESET_EVENT } from '@/lib/services/sessionStorage';
import { apiFetch, PERMISSION_MESSAGE, RequestTimeoutError, withRequestTimeout } from '@/lib/services/apiRequest';
import { resolveGenreLabel } from '@/lib/labels';

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
  averageRating?: number | null;
  ratingCount?: number;
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

let cacheGeneration = 0;
const cardsCache = new Map<string, { expiresAt: number; data: SeriesCard[] }>();
const cardsInFlight = new Map<string, Promise<SeriesCard[]>>();

export function clearSeriesCardsCache(): void {
  cacheGeneration += 1;
  cardsCache.clear();
  cardsInFlight.clear();
}

if (typeof window !== 'undefined') window.addEventListener?.(SESSION_RESET_EVENT, clearSeriesCardsCache);

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
  for (const entry of raw) {
    let id = '';
    if (typeof entry === 'string') {
      id = entry.trim();
    } else if (entry && typeof entry === 'object' && !Array.isArray(entry)) {
      const record = entry as { id?: unknown };
      id = typeof record.id === 'string' ? record.id.trim() : '';
    }
    if (!id) continue;
    const label = resolveGenreLabel(entry as string | { id?: string; label?: string }) || id;
    const key = label.toLocaleLowerCase('vi');
    if (seen.has(key)) continue;
    seen.add(key);
    result.push({ id, label });
  }
  return result;
}

export function normalizeSeriesCard(raw: unknown): SeriesCard {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    throw new SeriesCardApiError('Dữ liệu phim bộ không hợp lệ.', 502);
  }
  const record = raw as Record<string, unknown>;
  const id = nullableString(record.id);
  const title = nullableString(record.title);
  if (!id) throw new SeriesCardApiError('Phim bộ thiếu mã phim.', 502);
  if (!title) throw new SeriesCardApiError(`Phim bộ [${id}] thiếu tiêu đề.`, 502);

  const averageRating =
    typeof record.averageRating === 'number' &&
    Number.isFinite(record.averageRating) &&
    record.averageRating > 0 &&
    record.averageRating <= 10
      ? Number(record.averageRating.toFixed(1))
      : null;

  return {
    id,
    title,
    originalTitle: nullableString(record.originalTitle),
    synopsis: nullableString(record.synopsis),
    releaseYearStart: nullableYear(record.releaseYearStart),
    releaseYearEnd: nullableYear(record.releaseYearEnd),
    genres: normalizeGenres(record.genres),
    seasonCount: nullableCount(record.seasonCount),
    episodeCount: nullableCount(record.episodeCount),
    posterUrl: nullableString(record.posterUrl),
    backdropUrl: nullableString(record.backdropUrl),
    averageRating,
    ratingCount: nullableCount(record.ratingCount) ?? 0,
  };
}

function getAuthHeader(): Record<string, string> {
  const token = getStoredAuthToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function executeWithTimeout<T>(
  action: (signal: AbortSignal) => Promise<T>,
  options: { signal?: AbortSignal; timeoutMs?: number } = {}
): Promise<T> {
  try {
    return await withRequestTimeout(action, { ...options, timeoutMs: options.timeoutMs || DEFAULT_TIMEOUT_MS },
      'Tải danh sách phim bộ quá chậm.');
  } catch (error) {
    if (options.signal?.aborted) throw new SeriesCardApiError('Yêu cầu đã được hủy.', 499, { isCanceled: true });
    if (error instanceof RequestTimeoutError) throw new SeriesCardApiError(error.message, 408, { isTimeout: true });
    if (error instanceof SeriesCardApiError) throw error;
    throw new SeriesCardApiError('Chưa kết nối được kho phim bộ.', 0);
  }
}

export async function listSeriesCards(options: SeriesCardFetchOptions = {}): Promise<SeriesCard[]> {
  if (options.signal?.aborted) {
    throw new SeriesCardApiError('Yêu cầu đã được hủy.', 499, { isCanceled: true });
  }
  const baseUrl = options.baseUrl || API;
  const cached = cardsCache.get(baseUrl);
  if (!options.forceRefresh && cached && cached.expiresAt > Date.now()) {
    return cached.data;
  }

  const existingInFlight = !options.forceRefresh ? cardsInFlight.get(baseUrl) : undefined;
  if (existingInFlight && !options.signal) {
    return existingInFlight;
  }

  const generation = cacheGeneration;
  const requestPromise = executeWithTimeout(async (signal) => {
    const res = await apiFetch(`${baseUrl}/catalog/series/cards`, {
      method: 'GET',
      headers: { Accept: 'application/json', ...getAuthHeader() },
      signal,
    });
    if (res.status === 403) throw new SeriesCardApiError(PERMISSION_MESSAGE, 403);
    if (!res.ok) {
      throw new SeriesCardApiError(`Chưa tải được danh sách phim bộ (${res.status}).`, res.status);
    }
    let body: unknown;
    try {
      body = await res.json();
    } catch {
      throw new SeriesCardApiError('Dữ liệu danh sách phim bộ không hợp lệ.', 502);
    }
    if (!Array.isArray(body)) {
      throw new SeriesCardApiError('Danh sách phim bộ không hợp lệ.', 502);
    }
    return body.map(normalizeSeriesCard);
  }, options)
    .then((cards) => {
      if (generation === cacheGeneration) {
        cardsCache.set(baseUrl, { data: cards, expiresAt: Date.now() + CACHE_TTL_MS });
      }
      return cards;
    })
    .finally(() => {
      if (cardsInFlight.get(baseUrl) === requestPromise) {
        cardsInFlight.delete(baseUrl);
      }
    });

  if (!options.forceRefresh && !options.signal) {
    cardsInFlight.set(baseUrl, requestPromise);
  }
  return requestPromise;
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
