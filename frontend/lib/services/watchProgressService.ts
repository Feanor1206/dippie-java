import { parseTimestamp } from '@/lib/timestamps';
import { apiFetch, RequestTimeoutError, withRequestTimeout } from '@/lib/services/apiRequest';
const API = '/api';
export const WATCH_PROGRESS_LIMIT = 30;
export const GUEST_WATCH_PROGRESS_KEY = 'dippie_guest_watch_progress_v1';
export const WATCH_HISTORY_CHANGED_EVENT = 'dippie:watch-history-changed';
export const WATCH_PROGRESS_UPDATED_EVENT = 'dippie:watch-progress-updated';
export const AUTO_NEXT_PREFERENCE_KEY = 'dippie_auto_next_enabled_v1';
export const YOUTUBE_ASSET_PREFIX = 'youtube:';
const DEFAULT_TIMEOUT_MS = 7000;

export function toYoutubeAssetId(videoId: string): string {
  return `${YOUTUBE_ASSET_PREFIX}${videoId.trim()}`;
}

export function isYoutubeAssetId(assetId?: string | null): boolean {
  return typeof assetId === 'string' && assetId.trim().startsWith(YOUTUBE_ASSET_PREFIX);
}

export type WatchProgressRecord = {
  episodeId: string;
  seriesId: string;
  seriesTitle: string;
  seasonId: string | null;
  seasonNumber: number | null;
  seasonTitle: string | null;
  episodeNumber: number | null;
  episodeTitle: string;
  thumbnailUrl?: string | null;
  assetId: string;
  positionMs: number;
  durationMs: number | null;
  completed: boolean;
  version: number;
  updatedAt: string;
};

export type WatchProgressDraft = {
  episodeId: string;
  seriesId: string;
  seriesTitle: string;
  seasonId?: string | null;
  seasonNumber?: number | null;
  seasonTitle?: string | null;
  episodeNumber?: number | null;
  episodeTitle: string;
  thumbnailUrl?: string | null;
  assetId: string;
  positionMs: number;
  durationMs?: number | null;
  completed?: boolean;
};

export class WatchProgressError extends Error {
  status: number;
  isCanceled: boolean;
  isTimeout: boolean;

  constructor(message: string, status: number, options: { isCanceled?: boolean; isTimeout?: boolean } = {}) {
    super(message);
    this.name = options.isCanceled ? 'AbortError' : 'WatchProgressError';
    this.status = status;
    this.isCanceled = Boolean(options.isCanceled);
    this.isTimeout = Boolean(options.isTimeout);
  }
}

function nonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function nullableString(value: unknown): string | null {
  return typeof value === 'string' && value.trim() ? value.trim() : null;
}

function nullableNumber(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0 ? Math.floor(value) : null;
}

function positiveNullableNumber(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) && value > 0 ? Math.floor(value) : null;
}

export function isProgressCompleted(positionMs: number, durationMs: number | null | undefined): boolean {
  if (typeof durationMs !== 'number' || !Number.isFinite(durationMs) || durationMs <= 0) return false;
  const total = Math.floor(durationMs);
  const fivePercent = Math.max(1, Math.floor(total / 20));
  const nearEnd = Math.min(60_000, fivePercent);
  return positionMs >= total - nearEnd;
}

export function normalizeWatchProgressRecord(raw: unknown): WatchProgressRecord {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    throw new WatchProgressError('Dữ liệu tiến độ xem không hợp lệ.', 502);
  }
  const record = raw as Record<string, unknown>;
  if (
    !nonEmptyString(record.episodeId) ||
    !nonEmptyString(record.seriesId) ||
    !nonEmptyString(record.seriesTitle) ||
    !nonEmptyString(record.episodeTitle) ||
    !nonEmptyString(record.assetId) ||
    typeof record.positionMs !== 'number' ||
    !Number.isSafeInteger(record.positionMs) ||
    record.positionMs < 0 ||
    typeof record.completed !== 'boolean' ||
    typeof record.version !== 'number' ||
    !Number.isSafeInteger(record.version) ||
    record.version < 0 ||
    !nonEmptyString(record.updatedAt) ||
    !Number.isFinite(parseTimestamp(record.updatedAt))
  ) {
    throw new WatchProgressError('Dữ liệu tiến độ xem thiếu trường bắt buộc.', 502);
  }
  const thumbnailUrl = nullableString(record.thumbnailUrl);
  return {
    episodeId: record.episodeId.trim(),
    seriesId: record.seriesId.trim(),
    seriesTitle: record.seriesTitle.trim(),
    seasonId: nullableString(record.seasonId),
    seasonNumber: nullableNumber(record.seasonNumber),
    seasonTitle: nullableString(record.seasonTitle),
    episodeNumber: nullableNumber(record.episodeNumber),
    episodeTitle: record.episodeTitle.trim(),
    ...(thumbnailUrl ? { thumbnailUrl } : {}),
    assetId: record.assetId.trim(),
    positionMs: record.positionMs,
    durationMs: positiveNullableNumber(record.durationMs),
    completed: record.completed,
    version: record.version,
    updatedAt: new Date(parseTimestamp(record.updatedAt)).toISOString(),
  };
}

function normalizeDraft(draft: WatchProgressDraft): WatchProgressDraft {
  if (
    !draft ||
    !nonEmptyString(draft.episodeId) ||
    !nonEmptyString(draft.seriesId) ||
    !nonEmptyString(draft.seriesTitle) ||
    !nonEmptyString(draft.episodeTitle) ||
    !nonEmptyString(draft.assetId)
  ) {
    throw new WatchProgressError('Tiến độ xem thiếu thông tin phim, tập hoặc video.', 400);
  }
  if (!Number.isSafeInteger(draft.positionMs) || draft.positionMs < 0) {
    throw new WatchProgressError('Mốc phát video không hợp lệ.', 400);
  }
  const durationMs = positiveNullableNumber(draft.durationMs);
  const thumbnailUrl = nullableString(draft.thumbnailUrl);
  const positionMs = durationMs === null ? draft.positionMs : Math.min(draft.positionMs, durationMs);
  return {
    episodeId: draft.episodeId.trim(),
    seriesId: draft.seriesId.trim(),
    seriesTitle: draft.seriesTitle.trim(),
    seasonId: nullableString(draft.seasonId),
    seasonNumber: nullableNumber(draft.seasonNumber),
    seasonTitle: nullableString(draft.seasonTitle),
    episodeNumber: nullableNumber(draft.episodeNumber),
    episodeTitle: draft.episodeTitle.trim(),
    ...(thumbnailUrl ? { thumbnailUrl } : {}),
    assetId: draft.assetId.trim(),
    positionMs,
    durationMs,
    completed: Boolean(draft.completed) || isProgressCompleted(positionMs, durationMs),
  };
}

async function executeWithTimeout<T>(
  action: (signal: AbortSignal) => Promise<T>,
  options: { signal?: AbortSignal; timeoutMs?: number } = {}
): Promise<T> {
  try {
    return await withRequestTimeout(action, { ...options, timeoutMs: options.timeoutMs || DEFAULT_TIMEOUT_MS },
      'Tải lịch sử xem quá chậm.');
  } catch (error) {
    if (options.signal?.aborted) throw new WatchProgressError('Yêu cầu đã được hủy.', 499, { isCanceled: true });
    if (error instanceof RequestTimeoutError) throw new WatchProgressError(error.message, 408, { isTimeout: true });
    if (error instanceof WatchProgressError) throw error;
    throw new WatchProgressError('Chưa kết nối được dịch vụ lịch sử xem.', 0);
  }
}

async function readEnvelope(res: Response, label: string): Promise<unknown> {
  let json: unknown;
  try {
    json = await res.json();
  } catch {
    throw new WatchProgressError(`Dữ liệu ${label} trả về không hợp lệ.`, 502);
  }
  if (
    !json ||
    typeof json !== 'object' ||
    Array.isArray(json) ||
    (json as { success?: unknown }).success !== true ||
    !Object.prototype.hasOwnProperty.call(json, 'data')
  ) {
    const message =
      json && typeof json === 'object' && typeof (json as { message?: unknown }).message === 'string'
        ? (json as { message: string }).message
        : `Phản hồi ${label} không hợp lệ.`;
    throw new WatchProgressError(message, 502);
  }
  return (json as { data: unknown }).data;
}

function authHeaders(token: string, json = false): Record<string, string> {
  if (!nonEmptyString(token)) throw new WatchProgressError('Cần đăng nhập để đồng bộ tiến độ xem.', 401);
  return {
    Accept: 'application/json',
    ...(json ? { 'Content-Type': 'application/json' } : {}),
    Authorization: `Bearer ${token}`,
  };
}

function throwHttpError(res: Response, label: string): never {
  if (res.status === 401) throw new WatchProgressError('Phiên đăng nhập đã hết hạn.', 401);
  if (res.status === 403) throw new WatchProgressError('Bạn không có quyền xem tiến độ này.', 403);
  throw new WatchProgressError(`${label} thất bại (HTTP ${res.status}).`, res.status);
}

export async function listWatchProgress(
  token: string,
  options: { baseUrl?: string; signal?: AbortSignal; timeoutMs?: number } = {}
): Promise<WatchProgressRecord[]> {
  const headers = authHeaders(token);
  const url = `${options.baseUrl || API}/users/watch-progress`;
  return executeWithTimeout(async (signal) => {
    const res = await apiFetch(url, { headers, signal });
    if (!res.ok) throwHttpError(res, 'Tải tiến độ xem');
    const records = await readEnvelope(res, 'tiến độ xem');
    if (!Array.isArray(records)) throw new WatchProgressError('Danh sách tiến độ xem không hợp lệ.', 502);
    return records.slice(0, WATCH_PROGRESS_LIMIT).map(normalizeWatchProgressRecord);
  }, options);
}

export async function getWatchProgress(
  token: string,
  episodeId: string,
  options: { baseUrl?: string; signal?: AbortSignal; timeoutMs?: number; keepalive?: boolean } = {}
): Promise<WatchProgressRecord | null> {
  if (!nonEmptyString(episodeId)) throw new WatchProgressError('Mã tập không hợp lệ.', 400);
  const headers = authHeaders(token);
  const url = `${options.baseUrl || API}/users/watch-progress/${encodeURIComponent(episodeId.trim())}`;
  return executeWithTimeout(async (signal) => {
    const res = await apiFetch(url, { headers, signal, ...(options.keepalive ? { keepalive: true } : {}) });
    if (res.status === 404) return null;
    if (!res.ok) throwHttpError(res, 'Tải tiến độ tập');
    return normalizeWatchProgressRecord(await readEnvelope(res, 'tiến độ tập'));
  }, options);
}

export async function saveWatchProgress(
  token: string,
  episodeId: string,
  draft: Pick<WatchProgressDraft, 'assetId' | 'positionMs'>,
  expectedVersion: number,
  options: { baseUrl?: string; signal?: AbortSignal; timeoutMs?: number; keepalive?: boolean } = {}
): Promise<WatchProgressRecord> {
  if (!nonEmptyString(episodeId) || !nonEmptyString(draft?.assetId)) {
    throw new WatchProgressError('Mã tập hoặc video không hợp lệ.', 400);
  }
  if (!Number.isSafeInteger(draft.positionMs) || draft.positionMs < 0) {
    throw new WatchProgressError('Mốc phát video không hợp lệ.', 400);
  }
  if (!Number.isSafeInteger(expectedVersion) || expectedVersion < -1) {
    throw new WatchProgressError('Phiên bản tiến độ không hợp lệ.', 400);
  }
  const headers = authHeaders(token, true);
  const url = `${options.baseUrl || API}/users/watch-progress/${encodeURIComponent(episodeId.trim())}`;
  const payload = { assetId: draft.assetId.trim(), positionMs: Math.floor(draft.positionMs), expectedVersion };
  return executeWithTimeout(async (signal) => {
    const res = await apiFetch(url, {
      method: 'PUT',
      headers,
      body: JSON.stringify(payload),
      signal,
      ...(options.keepalive ? { keepalive: true } : {}),
    });
    if (!res.ok) throwHttpError(res, 'Lưu tiến độ xem');
    return normalizeWatchProgressRecord(await readEnvelope(res, 'tiến độ xem'));
  }, options);
}

export async function deleteWatchProgress(
  token: string,
  episodeId: string,
  options: { baseUrl?: string; signal?: AbortSignal; timeoutMs?: number } = {}
): Promise<void> {
  if (!nonEmptyString(episodeId)) throw new WatchProgressError('Mã tập không hợp lệ.', 400);
  const headers = authHeaders(token);
  const url = `${options.baseUrl || API}/users/watch-progress/${encodeURIComponent(episodeId.trim())}`;
  return executeWithTimeout(async (signal) => {
    const res = await apiFetch(url, { method: 'DELETE', headers, signal });
    if (!res.ok) throwHttpError(res, 'Xóa tiến độ xem');
    if (res.status === 204) return;
    const payload = await readEnvelope(res, 'tiến độ xem');
    if (payload !== null) throw new WatchProgressError('Phản hồi xóa tiến độ không hợp lệ.', 502);
  }, options);
}

export async function clearWatchProgress(token: string, options: { signal?: AbortSignal } = {}): Promise<void> {
  const headers = authHeaders(token);
  return executeWithTimeout(async (signal) => {
    const res = await apiFetch(`${API}/users/watch-progress`, { method: 'DELETE', headers, signal });
    if (res.status === 204) return;
    if (res.status === 404 || res.status === 405) {
      throw new WatchProgressError('Tính năng xóa hết lịch sử tạm chưa bật trên máy chủ.', res.status);
    }
    throwHttpError(res, 'Xóa hết lịch sử xem');
  }, options);
}

export type WatchProgressUpdateDetail = {
  scopeId: string;
  record: WatchProgressRecord;
};

export function notifyWatchHistoryChanged(): void {
  if (typeof window === 'undefined' || typeof window.dispatchEvent !== 'function' || typeof Event !== 'function') {
    return;
  }
  window.dispatchEvent(new Event(WATCH_HISTORY_CHANGED_EVENT));
}

export function notifyWatchProgressUpdated(scopeId: string, record: WatchProgressRecord): void {
  if (typeof window === 'undefined' || typeof window.dispatchEvent !== 'function' || typeof CustomEvent !== 'function') {
    return;
  }
  window.dispatchEvent(
    new CustomEvent<WatchProgressUpdateDetail>(WATCH_PROGRESS_UPDATED_EVENT, { detail: { scopeId, record } })
  );
}

function readGuestStorage(): WatchProgressRecord[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = window.localStorage.getItem(GUEST_WATCH_PROGRESS_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed
      .map((item) => {
        try {
          return normalizeWatchProgressRecord(item);
        } catch {
          return null;
        }
      })
      .filter((item): item is WatchProgressRecord => item !== null)
      .sort((a, b) => parseTimestamp(b.updatedAt) - parseTimestamp(a.updatedAt))
      .slice(0, WATCH_PROGRESS_LIMIT);
  } catch {
    return [];
  }
}

export function getGuestWatchProgress(): WatchProgressRecord[] {
  return readGuestStorage();
}

export function saveGuestWatchProgress(draft: WatchProgressDraft, now = Date.now()): WatchProgressRecord[] {
  if (typeof window === 'undefined') throw new WatchProgressError('Chưa lưu được trên máy này.', 0);
  const clean = normalizeDraft(draft);
  const current = readGuestStorage();
  const prior = current.find((item) => item.episodeId === clean.episodeId);
  const record = normalizeWatchProgressRecord({
    ...clean,
    seasonId: clean.seasonId ?? null,
    seasonNumber: clean.seasonNumber ?? null,
    seasonTitle: clean.seasonTitle ?? null,
    episodeNumber: clean.episodeNumber ?? null,
    durationMs: clean.durationMs ?? null,
    version: prior?.version ?? 0,
    updatedAt: new Date(now).toISOString(),
  });
  const next = [record, ...current.filter((item) => item.episodeId !== record.episodeId)]
    .sort((a, b) => parseTimestamp(b.updatedAt) - parseTimestamp(a.updatedAt))
    .slice(0, WATCH_PROGRESS_LIMIT);
  try {
    window.localStorage.setItem(GUEST_WATCH_PROGRESS_KEY, JSON.stringify(next));
  } catch {
    throw new WatchProgressError('Chưa lưu được mốc xem trên máy này.', 0);
  }
  return next;
}

export function clearGuestWatchProgress(): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.removeItem(GUEST_WATCH_PROGRESS_KEY);
  } catch {}
}

export function isUsableResumeProgress(record: WatchProgressRecord | null | undefined): record is WatchProgressRecord {
  return Boolean(
    record &&
      !record.completed &&
      nonEmptyString(record.episodeId) &&
      nonEmptyString(record.seriesId) &&
      nonEmptyString(record.assetId) &&
      Number.isSafeInteger(record.positionMs) &&
      record.positionMs > 0 &&
      (record.durationMs === null || (record.durationMs > 0 && !isProgressCompleted(record.positionMs, record.durationMs)))
  );
}

export function buildWatchProgressHref(record: WatchProgressRecord): string {
  const params = new URLSearchParams();
  params.set('episode', record.episodeId);
  if (isYoutubeAssetId(record.assetId)) {
    params.set('t', String(record.positionMs));
    params.set('timeline', 'episode');
  } else {
    params.set('asset', record.assetId);
    params.set('t', String(record.positionMs));
    params.set('timeline', 'file');
  }
  return `/series/${encodeURIComponent(record.seriesId)}?${params.toString()}`;
}

export function readAutoNextPreference(): boolean {
  if (typeof window === 'undefined') return false;
  try {
    return window.localStorage.getItem(AUTO_NEXT_PREFERENCE_KEY) === 'true';
  } catch {
    return false;
  }
}

export function writeAutoNextPreference(enabled: boolean): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(AUTO_NEXT_PREFERENCE_KEY, enabled ? 'true' : 'false');
  } catch {}
}
