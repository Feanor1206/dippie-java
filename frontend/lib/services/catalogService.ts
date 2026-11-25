import { getStoredAuthToken, SESSION_RESET_EVENT } from '@/lib/services/sessionStorage';
import { apiFetch, PERMISSION_MESSAGE, RequestTimeoutError, withRequestTimeout } from '@/lib/services/apiRequest';
import type { Movie } from '@/types/movie';

export type CatalogGenreOption = {
  id: string;
  label: string;
  count: number;
};

export type PageResponse<T> = {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
};

export type CatalogBrowseOptions = {
  query?: string;
  search?: string;
  genre?: string;
  genres?: string | string[];
  animationOnly?: boolean;
  yearMin?: number;
  yearMax?: number;
  sort?: 'rating' | 'year' | 'title';
  direction?: 'asc' | 'desc';
  page?: number;
  size?: number;
  timeoutMs?: number;
  signal?: AbortSignal;
};

export class CatalogApiError extends Error {
  status: number;
  data?: unknown;
  isCanceled?: boolean;
  isTimeout?: boolean;

  constructor(
    message: string,
    status: number,
    data?: unknown,
    options?: { isCanceled?: boolean; isTimeout?: boolean }
  ) {
    super(message);
    this.name = options?.isCanceled ? 'AbortError' : 'CatalogApiError';
    this.status = status;
    this.data = data;
    this.isCanceled = Boolean(options?.isCanceled);
    this.isTimeout = Boolean(options?.isTimeout);
  }
}

export interface CatalogValidationError {
  path: string;
  code: string;
  message: string;
}

export interface CatalogValidationResult {
  valid: boolean;
  packageId?: string;
  revision?: number;
  contentHash?: string;
  toCreate: number;
  toUpdate: number;
  unchanged: number;
  conflicts: number;
  errors: CatalogValidationError[];
}

export interface CatalogImportResponse {
  success: boolean;
  importId: string;
  packageId: string;
  revision: number;
  contentHash: string;
  importedAt: string;
  counts: Record<string, number>;
  indexStatus: string;
  message: string;
}

export interface CatalogSeriesSummary {
  id: string;
  title: string;
  originalTitle?: string;
  synopsis?: string;
  primaryLanguage?: string;
  releaseYearStart?: number;
  releaseYearEnd?: number;
  releaseStatus?: string;
  genreIds?: string;
  genreIdsJson?: string;
  keywords?: string;
  themes?: string;
  creators?: string;
  studios?: string;
  characterIds?: string;
}

export interface CatalogSeason {
  id: string;
  seriesId: string;
  number?: number;
  seasonNumber?: number;
  title: string;
  synopsis?: string;
  episodeCount?: number;
}

export interface CatalogYoutubeSource {
  videoId: string;
  canEmbed: boolean;
}

export interface CatalogEpisode {
  id: string;
  seriesId: string;
  seasonId?: string;
  number?: number;
  episodeNumber?: number;
  title: string;
  synopsis?: string;
  airDate?: string;
  durationMs?: number;
  plotFacts?: string;
  keywords?: string;
  themes?: string;
  primaryMediaAssetId?: string;
  rawJson?: string;
  canEmbed?: boolean;
  externalOnly?: boolean;
  youtubeWatchUrl?: string | null;
  youtubeVideoId?: string | null;
  youtube?: CatalogYoutubeSource | null;
  youtubeFallback?: CatalogYoutubeSource | null;
  averageRating?: number | null;
  ratingCount?: number;
}

export interface CatalogSegment {
  id: string;
  episodeId: string;
  position: number;
  title: string;
  synopsis?: string;
  episodeStartMs?: number;
  episodeEndMs?: number;
  plotFacts?: string;
}

export interface CatalogScene {
  id: string;
  episodeId: string;
  mediaAssetId?: string;
  startMs: number;
  endMs: number;
  title?: string;
  summary?: string;
  visualDescription?: string;
  keywords?: string;
  themes?: string;
}

export interface CatalogMediaAsset {
  id: string;
  contentType: string;
  editionLabel?: string | null;
  role: string;
  storageProvider: string;
  storageKey?: string;
  relativePath?: string;
  driveFolderId?: string;
  driveFileId?: string;
  durationMs?: number;
  coverageJson?: string;
  accessPolicy?: 'public' | 'authenticated' | 'private' | string;
  available?: boolean;
}

export interface CatalogSnapshot {
  id: string;
  snapshotVersion: number;
  contentHash: string;
  totalItems: number;
  status: 'PENDING' | 'SYNCING' | 'READY' | 'BLOCKED' | 'SYNC_FAILED' | string;
  errorMessage?: string;
  retryCount?: number;
  createdAt?: string;
  completedAt?: string;
  updatedAt?: string;
}

export interface CatalogSeriesResponse {
  series: CatalogSeriesSummary;
  seasons: CatalogSeason[];
  episodes: CatalogEpisode[];
}

export interface CatalogEpisodeResponse {
  episode: CatalogEpisode;
  segments: CatalogSegment[];
  mediaAssets: CatalogMediaAsset[];
  scenes: CatalogScene[];
  youtube?: CatalogYoutubeSource | null;
  youtubeFallback?: CatalogYoutubeSource | null;
  averageRating?: number | null;
  ratingCount?: number;
}

export interface CatalogSearchResult {
  id: string;
  type: 'series' | 'episode' | 'segment' | 'scene';
  title: string;
  parentTitle?: string;
  seriesId?: string;
  episodeId?: string;
  mediaAssetId?: string;
  startMs?: number;
  endMs?: number;
  snippet?: string;
  releaseYearStart?: number;
}

const DEFAULT_TIMEOUT_MS = 7000;
const GENRES_CACHE_TTL_MS = 60_000;
const MIN_VALID_YEAR = 1800;
const API = '/api';

const genresCache = new Map<string, { expiresAt: number; data: CatalogGenreOption[] }>();

export function clearCatalogGenresCache(): void {
  genresCache.clear();
}

if (typeof window !== 'undefined') window.addEventListener?.(SESSION_RESET_EVENT, clearCatalogGenresCache);

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

function nonEmptyTrimmed(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined;
}

function getAuthHeader(): Record<string, string> {
  const token = getStoredAuthToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function readErrorMessage(res: Response, fallback: string): Promise<string> {
  if (res.status === 403) return PERMISSION_MESSAGE;
  try {
    const errBody = await res.json();
    if (isRecord(errBody) && typeof errBody.message === 'string' && errBody.message) {
      return errBody.message;
    }
  } catch {}
  return fallback;
}

async function parseJsonOrThrow(res: Response, errorMessage: string, status = res.status): Promise<unknown> {
  try {
    return await res.json();
  } catch {
    throw new CatalogApiError(errorMessage, status);
  }
}

export function normalizeCatalogMovie(raw: unknown): Movie {
  if (!isRecord(raw)) {
    throw new CatalogApiError('Dữ liệu phim từ máy chủ không hợp lệ.', 502);
  }
  const id = nonEmptyTrimmed(raw.id);
  if (!id) throw new CatalogApiError('Dữ liệu phim thiếu mã phim.', 502);
  const title = nonEmptyTrimmed(raw.title);
  if (!title) throw new CatalogApiError(`Phim [${id}] thiếu tiêu đề.`, 502);

  let year: number | null = null;
  if (raw.year === null) {
    year = null;
  } else if (typeof raw.year === 'number' && Number.isFinite(raw.year) && raw.year > MIN_VALID_YEAR) {
    year = Math.round(raw.year);
  } else {
    throw new CatalogApiError(`Phim [${id}] có năm phát hành không hợp lệ.`, 502);
  }

  if (typeof raw.rating !== 'number' || !Number.isFinite(raw.rating) || raw.rating < 0 || raw.rating > 10) {
    throw new CatalogApiError(`Phim [${id}] có điểm không hợp lệ.`, 502);
  }
  const rating = Number(raw.rating.toFixed(1));
  const averageRating =
    raw.averageRating === null
      ? null
      : typeof raw.averageRating === 'number' &&
        Number.isFinite(raw.averageRating) &&
        raw.averageRating >= 0 &&
        raw.averageRating <= 10
      ? Number(raw.averageRating.toFixed(1))
      : undefined;
  const ratingCount =
    typeof raw.ratingCount === 'number' && Number.isFinite(raw.ratingCount) && raw.ratingCount >= 0
      ? Math.floor(raw.ratingCount)
      : undefined;

  let genres: string[] = [];
  if (Array.isArray(raw.genres)) {
    genres = raw.genres.map((g) => (typeof g === 'string' ? g.trim() : '')).filter(Boolean);
  } else if (typeof raw.genres === 'string' && raw.genres.trim()) {
    genres = raw.genres.split(',').map((g) => g.trim()).filter(Boolean);
  }

  const runtime = typeof raw.runtime === 'string' || typeof raw.runtime === 'number' ? String(raw.runtime).trim() : '';
  const description = typeof raw.description === 'string' ? raw.description.trim() : '';
  const poster = nonEmptyTrimmed(raw.poster) ?? '';
  const backdrop = nonEmptyTrimmed(raw.backdrop) ?? '';
  const videoUrl = nonEmptyTrimmed(raw.videoUrl);

  const navigation: Partial<Movie> = {};
  if (raw.item_type === 'series' || raw.item_type === 'episode') {
    navigation.item_type = raw.item_type;
    const seriesId = nonEmptyTrimmed(raw.series_id);
    if (seriesId) navigation.series_id = seriesId;
    const seriesTitle = nonEmptyTrimmed(raw.series_title) ?? nonEmptyTrimmed(raw.seriesTitle);
    if (seriesTitle) navigation.series_title = seriesTitle;
    const episodeId = nonEmptyTrimmed(raw.episode_id);
    if (episodeId) navigation.episode_id = episodeId;
    if (typeof raw.episode_number === 'number' && Number.isSafeInteger(raw.episode_number) && raw.episode_number > 0) {
      navigation.episode_number = raw.episode_number;
    }
  } else {
    const seriesTitle = nonEmptyTrimmed(raw.series_title);
    if (seriesTitle) navigation.series_title = seriesTitle;
  }

  if (typeof raw.externalOnly === 'boolean') navigation.externalOnly = raw.externalOnly;
  else if (typeof raw.external_only === 'boolean') navigation.externalOnly = raw.external_only;
  if (typeof raw.canEmbed === 'boolean') navigation.canEmbed = raw.canEmbed;
  else if (typeof raw.can_embed === 'boolean') navigation.canEmbed = raw.can_embed;

  const youtubeWatchUrl = nonEmptyTrimmed(raw.youtubeWatchUrl) ?? nonEmptyTrimmed(raw.youtube_watch_url);
  if (youtubeWatchUrl) navigation.youtubeWatchUrl = youtubeWatchUrl;
  const youtubeVideoId = nonEmptyTrimmed(raw.youtubeVideoId) ?? nonEmptyTrimmed(raw.youtube_video_id);
  if (youtubeVideoId) navigation.youtubeVideoId = youtubeVideoId;

  return {
    id,
    title,
    year,
    rating,
    ...(averageRating !== undefined ? { averageRating } : {}),
    ...(ratingCount !== undefined ? { ratingCount } : {}),
    runtime,
    genres,
    description,
    poster,
    backdrop,
    videoUrl,
    ...navigation,
  };
}

async function runCatalogRequest<T>(
  action: (signal: AbortSignal) => Promise<T>,
  options: { timeoutMs?: number; signal?: AbortSignal } = {}
): Promise<T> {
  try {
    return await withRequestTimeout(action, { ...options, timeoutMs: options.timeoutMs || DEFAULT_TIMEOUT_MS },
      'Tải kho phim quá chậm. Bạn thử lại nhé.');
  } catch (error) {
    if (options.signal?.aborted) throw new CatalogApiError('Yêu cầu đã được hủy.', 499, undefined, { isCanceled: true });
    if (error instanceof RequestTimeoutError) throw new CatalogApiError(error.message, 408, undefined, { isTimeout: true });
    if (error instanceof CatalogApiError) throw error;
    throw new CatalogApiError('Chưa kết nối được kho phim.', 0, error);
  }
}

function resolveGenreDisplayLabel(
  id: string,
  explicitLabel?: string,
  labelResolver?: (genreId: string) => string
): string {
  const cleanLabel = typeof explicitLabel === 'string' ? explicitLabel.trim() : '';
  if (cleanLabel) return cleanLabel;
  return typeof labelResolver === 'function' ? labelResolver(id) : id;
}

export function normalizeCatalogGenres(
  raw: unknown,
  labelResolver?: (genreId: string) => string
): CatalogGenreOption[] {
  const list = Array.isArray(raw)
    ? raw
    : isRecord(raw) && Array.isArray(raw.data)
    ? raw.data
    : null;
  if (!list) throw new CatalogApiError('Danh sách thể loại không hợp lệ', 502);

  const byKey = new Map<string, CatalogGenreOption>();
  for (const item of list) {
    if (!isRecord(item)) continue;
    const id = nonEmptyTrimmed(item.id);
    const count = typeof item.count === 'number' && Number.isFinite(item.count) ? Math.floor(item.count) : 0;
    if (!id || count <= 0) continue;
    const key = id.toLocaleLowerCase('vi');
    if (byKey.has(key)) continue;
    byKey.set(key, {
      id,
      label: resolveGenreDisplayLabel(id, typeof item.label === 'string' ? item.label : undefined, labelResolver),
      count,
    });
  }
  return [...byKey.values()];
}

export function deriveGenresFromMovies(
  movies: Movie[],
  labelResolver?: (genreId: string) => string
): CatalogGenreOption[] {
  const counts = new Map<string, CatalogGenreOption>();
  for (const movie of movies) {
    const seenInMovie = new Set<string>();
    for (const rawGenre of movie.genres || []) {
      const clean = nonEmptyTrimmed(rawGenre);
      if (!clean) continue;
      const label = resolveGenreDisplayLabel(clean, undefined, labelResolver);
      const key = label.toLocaleLowerCase('vi');
      if (seenInMovie.has(key)) continue;
      seenInMovie.add(key);
      const existing = counts.get(key);
      if (existing) existing.count += 1;
      else counts.set(key, { id: clean, label, count: 1 });
    }
  }
  return [...counts.values()].sort((a, b) => b.count - a.count || a.label.localeCompare(b.label, 'vi'));
}

export async function getCatalogGenres(
  options: { signal?: AbortSignal; timeoutMs?: number; forceRefresh?: boolean } = {},
  baseUrl = API
): Promise<CatalogGenreOption[]> {
  const cached = genresCache.get(baseUrl);
  if (!options.forceRefresh && cached && cached.expiresAt > Date.now()) return cached.data;

  const genres = await runCatalogRequest(async (signal) => {
    const res = await apiFetch(`${baseUrl}/movies/genres`, {
      method: 'GET',
      headers: { Accept: 'application/json' },
      signal,
    });
    if (!res.ok) throw new CatalogApiError(`Chưa tải được danh sách thể loại (${res.status}).`, res.status);
    const body = await parseJsonOrThrow(res, 'Dữ liệu danh sách thể loại không hợp lệ.', 502);
    return normalizeCatalogGenres(body);
  }, options);

  genresCache.set(baseUrl, { data: genres, expiresAt: Date.now() + GENRES_CACHE_TTL_MS });
  return genres;
}

export async function browseCatalog(
  options: CatalogBrowseOptions = {},
  baseUrl = API
): Promise<PageResponse<Movie>> {
  const params = new URLSearchParams();
  const effectiveQuery = nonEmptyTrimmed(options.query) || nonEmptyTrimmed(options.search);
  if (effectiveQuery) params.set('query', effectiveQuery);
  const genre = nonEmptyTrimmed(options.genre);
  if (genre) params.set('genre', genre);
  if (options.genres) {
    const genresParam = Array.isArray(options.genres)
      ? options.genres.map((g) => (typeof g === 'string' ? g.trim() : '')).filter(Boolean).join(',')
      : nonEmptyTrimmed(options.genres) ?? '';
    if (genresParam) params.set('genres', genresParam);
  }
  if (options.animationOnly) params.set('animationOnly', 'true');
  if (typeof options.yearMin === 'number' && Number.isFinite(options.yearMin)) params.set('yearMin', String(options.yearMin));
  if (typeof options.yearMax === 'number' && Number.isFinite(options.yearMax)) params.set('yearMax', String(options.yearMax));
  if (options.sort) params.set('sort', options.sort);
  if (options.direction) params.set('direction', options.direction);
  if (typeof options.page === 'number' && Number.isFinite(options.page)) params.set('page', String(options.page));
  if (typeof options.size === 'number' && Number.isFinite(options.size)) params.set('size', String(options.size));

  const queryString = params.toString();
  const url = `${baseUrl}/movies/browse${queryString ? `?${queryString}` : ''}`;

  return runCatalogRequest(async (signal) => {
    const res = await apiFetch(url, { method: 'GET', headers: { Accept: 'application/json' }, signal });
    if (!res.ok) throw new CatalogApiError(await readErrorMessage(res, `Chưa tải được kho phim (${res.status}).`), res.status);

    const body = await parseJsonOrThrow(res, 'Dữ liệu kho phim từ máy chủ không hợp lệ.');
    if (!isRecord(body) || body.success !== true) {
      const message = isRecord(body) && typeof body.message === 'string' ? body.message : 'Chưa tải được kho phim.';
      throw new CatalogApiError(message, res.status, body);
    }

    const pageData = body.data;
    if (!isRecord(pageData)) throw new CatalogApiError('Dữ liệu phân trang không hợp lệ.', 502);
    if (!Array.isArray(pageData.items)) throw new CatalogApiError('Dữ liệu phân trang thiếu danh sách phim.', 502);

    if (
      typeof pageData.page !== 'number' || !Number.isFinite(pageData.page) || pageData.page < 0 ||
      typeof pageData.size !== 'number' || !Number.isFinite(pageData.size) || pageData.size < 1 ||
      typeof pageData.totalElements !== 'number' || !Number.isFinite(pageData.totalElements) || pageData.totalElements < 0 ||
      typeof pageData.totalPages !== 'number' || !Number.isFinite(pageData.totalPages) || pageData.totalPages < 0 ||
      typeof pageData.hasNext !== 'boolean' || typeof pageData.hasPrevious !== 'boolean'
    ) {
      throw new CatalogApiError('Dữ liệu phân trang không hợp lệ.', 502);
    }

    return {
      items: pageData.items.map(normalizeCatalogMovie),
      page: pageData.page,
      size: pageData.size,
      totalElements: pageData.totalElements,
      totalPages: pageData.totalPages,
      hasNext: pageData.hasNext,
      hasPrevious: pageData.hasPrevious,
    };
  }, options);
}

export async function getCatalogMovieById(
  id: string,
  options: { signal?: AbortSignal; timeoutMs?: number } = {},
  baseUrl = API
): Promise<Movie | null> {
  const cleanId = nonEmptyTrimmed(id);
  if (!cleanId) throw new CatalogApiError('Mã phim không hợp lệ.', 400);

  return runCatalogRequest(async (signal) => {
    const res = await apiFetch(`${baseUrl}/movies/${encodeURIComponent(cleanId)}`, {
      method: 'GET',
      headers: { Accept: 'application/json' },
      signal,
    });
    if (res.status === 404) return null;
    if (!res.ok) throw new CatalogApiError(await readErrorMessage(res, `Chưa tải được thông tin phim (${res.status}).`), res.status);

    const body = await parseJsonOrThrow(res, 'Dữ liệu phim từ máy chủ không hợp lệ.');
    if (!isRecord(body) || body.success !== true || !body.data) {
      const message = isRecord(body) && typeof body.message === 'string' ? body.message : 'Chưa tải được thông tin phim.';
      throw new CatalogApiError(message, res.status, body);
    }

    const movie = normalizeCatalogMovie(body.data);
    if (movie.id !== cleanId) {
      throw new CatalogApiError(`Mã phim trả về (${movie.id}) không khớp với phim đang mở (${cleanId}).`, 502);
    }
    return movie;
  }, options);
}

export async function validateCatalogPackage(
  payload: unknown,
  baseUrl = API
): Promise<CatalogValidationResult> {
  const res = await apiFetch(`${baseUrl}/admin/catalog/validate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...getAuthHeader() },
    body: JSON.stringify(payload),
  });
  if (!res.ok && res.status !== 400) {
    throw new CatalogApiError(`Chưa kiểm tra được tệp dữ liệu (${res.status}).`, res.status);
  }
  const result = await res.json();
  if (!isRecord(result) || typeof result.valid !== 'boolean' || !Array.isArray(result.errors)) {
    throw new CatalogApiError('Kết quả kiểm tra tệp dữ liệu không hợp lệ.', 502);
  }
  return result as unknown as CatalogValidationResult;
}

export async function importCatalogPackage(
  payload: unknown,
  baseUrl = API
): Promise<CatalogImportResponse> {
  const res = await apiFetch(`${baseUrl}/admin/catalog/import`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...getAuthHeader() },
    body: JSON.stringify(payload),
  });
  if (!res.ok) {
    let errBody: unknown;
    try {
      errBody = await res.json();
    } catch {}
    const message =
      isRecord(errBody) && typeof errBody.message === 'string'
        ? errBody.message
        : `Chưa nhập được dữ liệu (HTTP ${res.status}).`;
    throw new CatalogApiError(message, res.status, errBody);
  }
  return (await res.json()) as CatalogImportResponse;
}

export async function getImportHistory(
  baseUrl = API,
  signal?: AbortSignal,
  timeoutMs = DEFAULT_TIMEOUT_MS
): Promise<Record<string, unknown>[]> {
  return runCatalogRequest(
    async (requestSignal) => {
      const res = await apiFetch(`${baseUrl}/admin/catalog/history`, {
        method: 'GET',
        headers: { Accept: 'application/json', ...getAuthHeader() },
        signal: requestSignal,
      });
      if (!res.ok) throw new CatalogApiError(`Chưa tải được lịch sử nhập (${res.status}).`, res.status);
      const history = await res.json();
      if (!Array.isArray(history)) throw new CatalogApiError('Dữ liệu lịch sử nhập không hợp lệ.', 502);
      return history as Record<string, unknown>[];
    },
    { signal, timeoutMs }
  );
}

export async function getCatalogSnapshots(
  baseUrl = API,
  signal?: AbortSignal,
  timeoutMs = DEFAULT_TIMEOUT_MS
): Promise<CatalogSnapshot[]> {
  return runCatalogRequest(
    async (requestSignal) => {
      const res = await apiFetch(`${baseUrl}/admin/catalog/snapshots`, {
        method: 'GET',
        headers: { Accept: 'application/json', ...getAuthHeader() },
        signal: requestSignal,
      });
      if (!res.ok) throw new CatalogApiError(`Chưa tải được trạng thái đồng bộ (${res.status}).`, res.status);
      const snapshots = await res.json();
      if (!Array.isArray(snapshots)) throw new CatalogApiError('Dữ liệu trạng thái đồng bộ không hợp lệ.', 502);
      return snapshots as CatalogSnapshot[];
    },
    { signal, timeoutMs }
  );
}

export async function retryCatalogSnapshot(
  snapshotId: string,
  baseUrl = API
): Promise<CatalogSnapshot> {
  const res = await apiFetch(`${baseUrl}/admin/catalog/snapshots/${encodeURIComponent(snapshotId)}/retry`, {
    method: 'POST',
    headers: { Accept: 'application/json', ...getAuthHeader() },
  });
  if (!res.ok) throw new CatalogApiError(`Chưa đồng bộ lại được bản dữ liệu (${res.status}).`, res.status);
  return (await res.json()) as CatalogSnapshot;
}

function normalizeCatalogSeriesSummary(raw: unknown): CatalogSeriesSummary {
  if (!isRecord(raw)) {
    throw new CatalogApiError('Dữ liệu phim bộ từ máy chủ không hợp lệ.', 502, raw);
  }
  const id = nonEmptyTrimmed(raw.id);
  if (!id) throw new CatalogApiError('Phim bộ thiếu mã phim.', 502, raw);
  const title = nonEmptyTrimmed(raw.title);
  if (!title) throw new CatalogApiError(`Phim bộ [${id}] thiếu tiêu đề.`, 502, raw);

  for (const key of ['releaseYearStart', 'releaseYearEnd'] as const) {
    const value = raw[key];
    if (value != null && (typeof value !== 'number' || !Number.isFinite(value))) {
      throw new CatalogApiError(`Phim bộ [${id}] có năm phát hành không hợp lệ.`, 502, raw);
    }
  }
  return {
    id,
    title,
    originalTitle: typeof raw.originalTitle === 'string' ? raw.originalTitle : undefined,
    synopsis: typeof raw.synopsis === 'string' ? raw.synopsis : undefined,
    primaryLanguage: typeof raw.primaryLanguage === 'string' ? raw.primaryLanguage : undefined,
    releaseYearStart: typeof raw.releaseYearStart === 'number' ? raw.releaseYearStart : undefined,
    releaseYearEnd: typeof raw.releaseYearEnd === 'number' ? raw.releaseYearEnd : undefined,
    releaseStatus: typeof raw.releaseStatus === 'string' ? raw.releaseStatus : undefined,
    genreIds: typeof raw.genreIds === 'string' ? raw.genreIds : undefined,
    genreIdsJson: typeof raw.genreIdsJson === 'string' ? raw.genreIdsJson : undefined,
  };
}

export async function getCatalogSeriesList(
  baseUrl = API,
  signal?: AbortSignal,
  timeoutMs = DEFAULT_TIMEOUT_MS
): Promise<CatalogSeriesSummary[]> {
  return runCatalogRequest(
    async (requestSignal) => {
      const res = await apiFetch(`${baseUrl}/catalog/series`, {
        method: 'GET',
        headers: { Accept: 'application/json', ...getAuthHeader() },
        signal: requestSignal,
      });
      if (!res.ok) throw new CatalogApiError(`Chưa tải được danh sách phim bộ (${res.status}).`, res.status);
      const body = await res.json();
      if (!Array.isArray(body)) {
        throw new CatalogApiError('Danh sách phim bộ từ máy chủ không hợp lệ.', 502, body);
      }
      return body.map(normalizeCatalogSeriesSummary);
    },
    { signal, timeoutMs }
  );
}

export async function getCatalogSeriesDetail(
  id: string,
  baseUrl = API,
  signal?: AbortSignal
): Promise<CatalogSeriesResponse | null> {
  return runCatalogRequest(async (requestSignal) => {
    const res = await apiFetch(`${baseUrl}/catalog/series/${encodeURIComponent(id)}`, {
      method: 'GET',
      headers: { Accept: 'application/json', ...getAuthHeader() },
      signal: requestSignal,
    });
    if (res.status === 404) return null;
    if (res.status === 403) throw new CatalogApiError(PERMISSION_MESSAGE, 403);
    if (!res.ok) throw new CatalogApiError(`Chưa tải được phim bộ (${res.status}).`, res.status);
    return (await res.json()) as CatalogSeriesResponse;
  }, { signal });
}

export async function getCatalogEpisodeDetail(
  id: string,
  baseUrl = API,
  signal?: AbortSignal
): Promise<CatalogEpisodeResponse | null> {
  return runCatalogRequest(async (requestSignal) => {
    const res = await apiFetch(`${baseUrl}/catalog/episodes/${encodeURIComponent(id)}`, {
      method: 'GET',
      headers: { Accept: 'application/json', ...getAuthHeader() },
      signal: requestSignal,
    });
    if (res.status === 404) return null;
    if (res.status === 403) throw new CatalogApiError(PERMISSION_MESSAGE, 403);
    if (!res.ok) throw new CatalogApiError(`Chưa tải được tập này (${res.status}).`, res.status);
    return (await res.json()) as CatalogEpisodeResponse;
  }, { signal });
}

const VALID_SEARCH_RESULT_TYPES = new Set(['series', 'episode', 'segment', 'scene']);

export async function searchAnimationCatalog(
  query: string,
  baseUrl = API,
  signal?: AbortSignal,
  timeoutMs = DEFAULT_TIMEOUT_MS
): Promise<CatalogSearchResult[]> {
  const trimmed = query.trim();
  if (!trimmed) return [];

  return runCatalogRequest(
    async (requestSignal) => {
      const res = await apiFetch(`${baseUrl}/catalog/search?q=${encodeURIComponent(trimmed)}`, {
        method: 'GET',
        headers: { Accept: 'application/json', ...getAuthHeader() },
        signal: requestSignal,
      });
      if (!res.ok) throw new CatalogApiError(`Chưa tìm được trong kho phim (${res.status}).`, res.status);
      const body = await res.json();
      if (!Array.isArray(body)) {
        throw new CatalogApiError('Kết quả tìm kiếm không hợp lệ.', 502, body);
      }
      return body.map((entry: unknown) => {
        if (!isRecord(entry)) {
          throw new CatalogApiError('Kết quả tìm kiếm thiếu mã, tiêu đề hoặc loại nội dung.', 502, entry);
        }
        const id = nonEmptyTrimmed(entry.id);
        const title = nonEmptyTrimmed(entry.title);
        const type = typeof entry.type === 'string' ? entry.type : '';
        if (!id || !title || !VALID_SEARCH_RESULT_TYPES.has(type)) {
          throw new CatalogApiError('Kết quả tìm kiếm thiếu mã, tiêu đề hoặc loại nội dung.', 502, entry);
        }
        return {
          ...entry,
          id,
          title,
          type: type as CatalogSearchResult['type'],
        };
      });
    },
    { signal, timeoutMs }
  );
}
