import { apiFetch, RequestTimeoutError, withRequestTimeout } from '@/lib/services/apiRequest';
import { clearSeriesCardsCache } from '@/lib/services/seriesCardService';
import { parseTimestamp } from '@/lib/timestamps';

const API = '/api';
const GUEST_LIBRARY_STORAGE_KEY = 'dippie_guest_library_v1';
const GUEST_WATCH_PROGRESS_STORAGE_KEY = 'dippie_guest_watch_progress_v1';

export const LIBRARY_REQUEST_TIMEOUT_MS = 7000;
export const WATCH_PROGRESS_SYNCED_EVENT = 'dippie:watch-progress-synced';
export const RATING_CHANGED_EVENT = 'dippie:rating-changed';

export type LibraryActionKind = 'watchlist' | 'favorite' | 'rating';
export type LibraryActionIntent = 'add' | 'remove' | 'rate' | 'remove_rating';

export type GuestLibraryData = {
  watchlist: string[];
  favorites: string[];
  ratings: Record<string, number>;
};

export type GuestProgressItem = {
  episodeId: string;
  assetId: string;
  positionMs: number;
  completed: boolean;
  raw: Record<string, unknown>;
};

export type GuestSyncSummary = {
  watchlistCount: number;
  favoritesCount: number;
  ratingsCount: number;
  progressCount: number;
  totalCount: number;
};

export type GuestSyncResult = {
  aborted: boolean;
  authExpired: boolean;
  hasGuestItems: boolean;
  syncedWriteCount: number;
  failedCount: number;
  mergedProgressCount: number;
  mergedRatingsCount: number;
};

export type OperationRecord =
  | {
      id: number;
      action: 'watchlist' | 'favorite';
      movieId: string;
      readVersionAtStart: number;
      committedAtReadVersion: number | null;
      status: 'pending' | 'committed' | 'uncertain';
      desiredValue: boolean;
    }
  | {
      id: number;
      action: 'rating';
      movieId: string;
      readVersionAtStart: number;
      committedAtReadVersion: number | null;
      status: 'pending' | 'committed' | 'uncertain';
      desiredValue: number | undefined;
    };

export class LibraryApiError extends Error {
  status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'LibraryApiError';
    this.status = status;
  }
}

export function isAbortError(err: unknown): boolean {
  if (err instanceof DOMException && err.name === 'AbortError') return true;
  return typeof err === 'object' && err !== null && (err as { name?: unknown }).name === 'AbortError';
}

export function isUncertainNetworkError(err: unknown): boolean {
  if (isAbortError(err) || err instanceof RequestTimeoutError) return true;
  if (err instanceof TypeError) return true;
  return typeof err === 'object' && err !== null && (err as { name?: unknown }).name === 'TypeError';
}

function emptyGuestLibrary(): GuestLibraryData {
  return { watchlist: [], favorites: [], ratings: {} };
}

function hasLocalStorage(): boolean {
  return typeof window !== 'undefined' && typeof localStorage !== 'undefined';
}

export function readGuestLibrary(): GuestLibraryData {
  if (!hasLocalStorage()) return emptyGuestLibrary();
  try {
    const raw = localStorage.getItem(GUEST_LIBRARY_STORAGE_KEY);
    if (!raw) return emptyGuestLibrary();
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    const filterIds = (value: unknown): string[] =>
      Array.isArray(value)
        ? value.filter((id): id is string => typeof id === 'string' && id.trim().length > 0)
        : [];
    const ratings: Record<string, number> = {};
    if (typeof parsed.ratings === 'object' && parsed.ratings !== null && !Array.isArray(parsed.ratings)) {
      for (const [movieId, score] of Object.entries(parsed.ratings)) {
        if (typeof movieId === 'string' && movieId.trim().length > 0 && typeof score === 'number' && Number.isFinite(score) && score >= 1 && score <= 10) {
          ratings[movieId] = Math.round(score);
        }
      }
    }
    return {
      watchlist: filterIds(parsed.watchlist),
      favorites: filterIds(parsed.favorites),
      ratings,
    };
  } catch {
    return emptyGuestLibrary();
  }
}

export function writeGuestLibrary(data: GuestLibraryData): boolean {
  if (!hasLocalStorage()) return false;
  try {
    localStorage.setItem(GUEST_LIBRARY_STORAGE_KEY, JSON.stringify(data));
    return true;
  } catch {
    return false;
  }
}

export function readGuestWatchProgress(): GuestProgressItem[] {
  if (!hasLocalStorage()) return [];
  try {
    const raw = localStorage.getItem(GUEST_WATCH_PROGRESS_STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    const seen = new Set<string>();
    const items: GuestProgressItem[] = [];
    for (const entry of parsed) {
      if (!entry || typeof entry !== 'object' || Array.isArray(entry)) continue;
      const record = entry as Record<string, unknown>;
      const episodeId = typeof record.episodeId === 'string' ? record.episodeId.trim() : '';
      const assetId = typeof record.assetId === 'string' ? record.assetId.trim() : '';
      const positionMs =
        typeof record.positionMs === 'number' && Number.isSafeInteger(record.positionMs) && record.positionMs >= 0
          ? record.positionMs
          : -1;
      if (!episodeId || !assetId || positionMs < 0 || seen.has(episodeId)) continue;
      seen.add(episodeId);
      const completed = Boolean(record.completed);
      items.push({
        episodeId,
        assetId,
        positionMs,
        completed,
        raw: { ...record, episodeId, assetId, positionMs, completed },
      });
    }
    return items;
  } catch {
    return [];
  }
}

export function readGuestSyncSummary(): GuestSyncSummary {
  const library = readGuestLibrary();
  const progress = readGuestWatchProgress();
  const watchlistCount = library.watchlist.length;
  const favoritesCount = library.favorites.length;
  const ratingsCount = Object.keys(library.ratings).length;
  const progressCount = progress.length;
  return {
    watchlistCount,
    favoritesCount,
    ratingsCount,
    progressCount,
    totalCount: watchlistCount + favoritesCount + ratingsCount + progressCount,
  };
}

export function hasGuestSyncData(): boolean {
  return readGuestSyncSummary().totalCount > 0;
}

function removeGuestKey(key: string): void {
  if (!hasLocalStorage()) return;
  try {
    localStorage.removeItem(key);
  } catch {}
}

export function clearGuestSyncData(): void {
  removeGuestKey(GUEST_LIBRARY_STORAGE_KEY);
  removeGuestKey(GUEST_WATCH_PROGRESS_STORAGE_KEY);
}

function persistRemainingGuestData(library: GuestLibraryData, progressItems: GuestProgressItem[]): void {
  const hasLibrary =
    library.watchlist.length > 0 ||
    library.favorites.length > 0 ||
    Object.keys(library.ratings).length > 0;
  if (hasLibrary) {
    writeGuestLibrary(library);
  } else {
    removeGuestKey(GUEST_LIBRARY_STORAGE_KEY);
  }
  if (progressItems.length > 0 && hasLocalStorage()) {
    try {
      localStorage.setItem(
        GUEST_WATCH_PROGRESS_STORAGE_KEY,
        JSON.stringify(progressItems.map((item) => item.raw))
      );
    } catch {}
  } else {
    removeGuestKey(GUEST_WATCH_PROGRESS_STORAGE_KEY);
  }
}

export function notifyWatchProgressSynced(): void {
  if (typeof window === 'undefined' || typeof window.dispatchEvent !== 'function') return;
  try {
    const event =
      typeof CustomEvent === 'function'
        ? new CustomEvent(WATCH_PROGRESS_SYNCED_EVENT)
        : typeof Event === 'function'
        ? new Event(WATCH_PROGRESS_SYNCED_EVENT)
        : null;
    if (event) window.dispatchEvent(event);
  } catch {}
}

export function notifyRatingChanged(movieId?: string): void {
  if (typeof clearSeriesCardsCache === 'function') {
    clearSeriesCardsCache();
  }
  if (typeof window === 'undefined' || typeof window.dispatchEvent !== 'function') return;
  try {
    const event =
      typeof CustomEvent === 'function'
        ? new CustomEvent(RATING_CHANGED_EVENT, { detail: movieId ? { movieId } : {} })
        : typeof Event === 'function'
        ? new Event(RATING_CHANGED_EVENT)
        : null;
    if (event) window.dispatchEvent(event);
  } catch {}
}

function buildAuthHeaders(token: string, json = false): Record<string, string> {
  return {
    ...(json ? { 'Content-Type': 'application/json' } : {}),
    Authorization: `Bearer ${token}`,
    Accept: 'application/json',
  };
}

async function isWriteResponseSuccess(res: Response): Promise<boolean> {
  if (!res.ok) return false;
  if (typeof res.json !== 'function') return true;
  try {
    const json = (await res.json()) as { success?: unknown } | null;
    if (json && typeof json === 'object' && 'success' in json) {
      return json.success === true;
    }
    return true;
  } catch {
    return res.status === 204;
  }
}

async function assertMutationSuccess(res: Response, failureLabel: string): Promise<void> {
  if (res.status === 401) {
    throw new LibraryApiError('Phiên đăng nhập đã hết hạn.', 401);
  }
  if (res.status === 403) {
    throw new LibraryApiError('Tài khoản của bạn không có quyền thực hiện việc này.', 403);
  }
  if (!res.ok) {
    throw new LibraryApiError(`${failureLabel} (HTTP ${res.status})`, res.status);
  }
  const json = (await res.json()) as { success?: unknown } | null;
  if (!json || json.success !== true) {
    throw new LibraryApiError('Máy chủ trả về dữ liệu chưa đúng định dạng.', 502);
  }
}

export async function fetchUserLibrary(token: string, signal: AbortSignal): Promise<GuestLibraryData> {
  return withRequestTimeout(async (requestSignal) => {
    const headers = buildAuthHeaders(token);
    const [watchlistResponse, favoritesResponse, ratingsResponse] = await Promise.all([
      apiFetch(`${API}/users/watchlist`, { headers, signal: requestSignal }),
      apiFetch(`${API}/users/favorites`, { headers, signal: requestSignal }),
      apiFetch(`${API}/users/ratings`, { headers, signal: requestSignal }),
    ]);

    if (watchlistResponse.status === 401 || favoritesResponse.status === 401 || ratingsResponse.status === 401) {
      throw new LibraryApiError('Phiên đăng nhập đã hết hạn. Bạn đăng nhập lại nhé.', 401);
    }
    if ([watchlistResponse, favoritesResponse, ratingsResponse].some((response) => response.status === 403)) {
      throw new LibraryApiError('Tài khoản của bạn không có quyền thực hiện việc này.', 403);
    }
    if (!watchlistResponse.ok || !favoritesResponse.ok || !ratingsResponse.ok) {
      throw new LibraryApiError('Chưa tải được thư viện của bạn từ máy chủ.', 500);
    }

    const [watchlistBody, favoritesBody, ratingsBody] = (await Promise.all([
      watchlistResponse.json(),
      favoritesResponse.json(),
      ratingsResponse.json(),
    ])) as Array<{ success?: unknown; data?: unknown } | null>;

    if (
      watchlistBody?.success !== true || !Array.isArray(watchlistBody.data) ||
      favoritesBody?.success !== true || !Array.isArray(favoritesBody.data) ||
      ratingsBody?.success !== true || !Array.isArray(ratingsBody.data)
    ) {
      throw new LibraryApiError('Dữ liệu thư viện từ máy chủ không hợp lệ.', 502);
    }

    const extractIds = (rows: unknown[]): string[] =>
      rows
        .map((row) => (row && typeof row === 'object' ? (row as { movieId?: unknown }).movieId : null))
        .filter((id): id is string => typeof id === 'string' && id.length > 0);

    const ratings: Record<string, number> = {};
    for (const row of ratingsBody.data) {
      if (row && typeof row === 'object') {
        const { movieId, rating } = row as { movieId?: unknown; rating?: unknown };
        if (typeof movieId === 'string' && movieId && typeof rating === 'number') {
          ratings[movieId] = rating;
        }
      }
    }

    return {
      watchlist: extractIds(watchlistBody.data),
      favorites: extractIds(favoritesBody.data),
      ratings,
    };
  }, { signal, timeoutMs: LIBRARY_REQUEST_TIMEOUT_MS }, 'Tải thư viện quá chậm. Bạn thử lại nhé.');
}

export async function updateLibraryList(
  listKey: 'watchlist' | 'favorites',
  token: string,
  movieId: string,
  options: { remove: boolean; title: string; poster: string; signal: AbortSignal }
): Promise<void> {
  return withRequestTimeout(async (requestSignal) => {
    const listName = listKey === 'watchlist' ? 'Xem sau' : 'Yêu thích';
    const res = options.remove
      ? await apiFetch(`${API}/users/${listKey}/${encodeURIComponent(movieId)}`, {
          method: 'DELETE',
          headers: buildAuthHeaders(token),
          signal: requestSignal,
        })
      : await apiFetch(`${API}/users/${listKey}`, {
          method: 'POST',
          headers: buildAuthHeaders(token, true),
          body: JSON.stringify({
            movieId,
            movieTitle: options.title,
            posterUrl: options.poster,
          }),
          signal: requestSignal,
        });

    await assertMutationSuccess(res, `${options.remove ? 'Chưa bỏ được khỏi' : 'Chưa thêm được vào'} ${listName}`);
  }, { signal: options.signal, timeoutMs: LIBRARY_REQUEST_TIMEOUT_MS }, 'Cập nhật thư viện quá chậm.');
}

export async function saveUserRating(
  token: string,
  movieId: string,
  rating: number,
  signal: AbortSignal
): Promise<void> {
  return withRequestTimeout(async (requestSignal) => {
    const res = await apiFetch(`${API}/users/ratings`, {
      method: 'POST',
      headers: buildAuthHeaders(token, true),
      body: JSON.stringify({ movieId, rating }),
      signal: requestSignal,
    });
    await assertMutationSuccess(res, 'Chưa lưu được điểm');
  }, { signal, timeoutMs: LIBRARY_REQUEST_TIMEOUT_MS }, 'Lưu điểm quá chậm.');
}

export async function deleteUserRating(
  token: string,
  movieId: string,
  signal: AbortSignal
): Promise<void> {
  return withRequestTimeout(async (requestSignal) => {
    const res = await apiFetch(`${API}/users/ratings/${encodeURIComponent(movieId)}`, {
      method: 'DELETE',
      headers: buildAuthHeaders(token),
      signal: requestSignal,
    });
    await assertMutationSuccess(res, 'Chưa xóa được điểm');
  }, { signal, timeoutMs: LIBRARY_REQUEST_TIMEOUT_MS }, 'Xóa điểm quá chậm.');
}

type ServerProgressSummary = {
  version: number;
  positionMs: number;
  completed: boolean;
};

function parseServerProgressSummary(raw: unknown): ServerProgressSummary | null {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return null;
  const entry = raw as { version?: unknown; positionMs?: unknown; completed?: unknown };
  if (typeof entry.version !== 'number' || !Number.isSafeInteger(entry.version)) return null;
  return {
    version: entry.version,
    positionMs: typeof entry.positionMs === 'number' && Number.isFinite(entry.positionMs) ? entry.positionMs : 0,
    completed: Boolean(entry.completed),
  };
}

export async function syncGuestDataToAccount(
  token: string,
  serverLibrary: GuestLibraryData,
  signal: AbortSignal,
  isStillCurrent: () => boolean,
  requestTimeoutMs = LIBRARY_REQUEST_TIMEOUT_MS
): Promise<GuestSyncResult> {
  const guestLibrary = readGuestLibrary();
  const guestProgress = readGuestWatchProgress();
  const hasGuestItems =
    guestLibrary.watchlist.length > 0 ||
    guestLibrary.favorites.length > 0 ||
    Object.keys(guestLibrary.ratings).length > 0 ||
    guestProgress.length > 0;

  if (!hasGuestItems) {
    return {
      aborted: false,
      authExpired: false,
      hasGuestItems: false,
      syncedWriteCount: 0,
      failedCount: 0,
      mergedProgressCount: 0,
      mergedRatingsCount: 0,
    };
  }

  const readHeaders = buildAuthHeaders(token);
  const writeHeaders = buildAuthHeaders(token, true);
  let authExpired = false;
  let syncedWriteCount = 0;
  let failedCount = 0;
  let mergedRatingsCount = 0;
  let mergedProgressCount = 0;

  const timedRequest = <T,>(run: (requestSignal: AbortSignal) => Promise<T>): Promise<T> =>
    withRequestTimeout(run, { signal, timeoutMs: requestTimeoutMs }, 'Đồng bộ dữ liệu tạm quá chậm.');

  const syncIdList = async (
    items: string[],
    serverList: string[],
    endpoint: string
  ): Promise<string[] | null> => {
    if (authExpired) return [...items];
    const existing = new Set(serverList);
    const remaining: string[] = [];
    for (let i = 0; i < items.length; i++) {
      if (!isStillCurrent() || signal.aborted) return null;
      const movieId = items[i];
      if (existing.has(movieId)) continue;
      try {
        const outcome = await timedRequest(async (requestSignal) => {
          const res = await apiFetch(`${API}/users/${endpoint}`, {
            method: 'POST',
            headers: writeHeaders,
            body: JSON.stringify({ movieId, movieTitle: movieId, posterUrl: '' }),
            signal: requestSignal,
          });
          if (res.status === 401) return 'unauthorized' as const;
          return (await isWriteResponseSuccess(res)) ? ('ok' as const) : ('failed' as const);
        });
        if (!isStillCurrent() || signal.aborted) return null;
        if (outcome === 'unauthorized') {
          authExpired = true;
          remaining.push(...items.slice(i));
          break;
        }
        if (outcome === 'ok') {
          existing.add(movieId);
          serverList.push(movieId);
          syncedWriteCount += 1;
        } else {
          failedCount += 1;
          remaining.push(movieId);
        }
      } catch {
        if (!isStillCurrent() || signal.aborted) return null;
        failedCount += 1;
        remaining.push(movieId);
      }
    }
    return remaining;
  };

  const remainingWatchlist = await syncIdList(guestLibrary.watchlist, serverLibrary.watchlist, 'watchlist');
  if (remainingWatchlist === null) {
    return { aborted: true, authExpired: false, hasGuestItems: true, syncedWriteCount, failedCount, mergedProgressCount, mergedRatingsCount };
  }

  const remainingFavorites = await syncIdList(guestLibrary.favorites, serverLibrary.favorites, 'favorites');
  if (remainingFavorites === null) {
    return { aborted: true, authExpired: false, hasGuestItems: true, syncedWriteCount, failedCount, mergedProgressCount, mergedRatingsCount };
  }

  const remainingRatings: Record<string, number> = {};
  const ratingEntries = Object.entries(guestLibrary.ratings);
  if (authExpired) {
    for (const [movieId, score] of ratingEntries) remainingRatings[movieId] = score;
  } else {
    for (let i = 0; i < ratingEntries.length; i++) {
      if (!isStillCurrent() || signal.aborted) {
        return { aborted: true, authExpired: false, hasGuestItems: true, syncedWriteCount, failedCount, mergedProgressCount, mergedRatingsCount };
      }
      const [movieId, score] = ratingEntries[i];
      if (Object.prototype.hasOwnProperty.call(serverLibrary.ratings, movieId)) continue;
      try {
        const outcome = await timedRequest(async (requestSignal) => {
          const res = await apiFetch(`${API}/users/ratings`, {
            method: 'POST',
            headers: writeHeaders,
            body: JSON.stringify({ movieId, rating: score }),
            signal: requestSignal,
          });
          if (res.status === 401) return 'unauthorized' as const;
          return (await isWriteResponseSuccess(res)) ? ('ok' as const) : ('failed' as const);
        });
        if (!isStillCurrent() || signal.aborted) {
          return { aborted: true, authExpired: false, hasGuestItems: true, syncedWriteCount, failedCount, mergedProgressCount, mergedRatingsCount };
        }
        if (outcome === 'unauthorized') {
          authExpired = true;
          for (const [remainingId, remainingScore] of ratingEntries.slice(i)) remainingRatings[remainingId] = remainingScore;
          break;
        }
        if (outcome === 'ok') {
          serverLibrary.ratings[movieId] = score;
          mergedRatingsCount += 1;
          syncedWriteCount += 1;
        } else {
          failedCount += 1;
          remainingRatings[movieId] = score;
        }
      } catch {
        if (!isStillCurrent() || signal.aborted) {
          return { aborted: true, authExpired: false, hasGuestItems: true, syncedWriteCount, failedCount, mergedProgressCount, mergedRatingsCount };
        }
        failedCount += 1;
        remainingRatings[movieId] = score;
      }
    }
  }

  const putProgressOnce = async (progressEntry: GuestProgressItem, expectedVersion: number) =>
    timedRequest(async (requestSignal) => {
      const res = await apiFetch(`${API}/users/watch-progress/${encodeURIComponent(progressEntry.episodeId)}`, {
        method: 'PUT',
        headers: writeHeaders,
        body: JSON.stringify({
          assetId: progressEntry.assetId,
          positionMs: progressEntry.positionMs,
          expectedVersion,
        }),
        signal: requestSignal,
      });
      if (res.status === 401) return 'unauthorized' as const;
      if (res.status === 409) return 'conflict' as const;
      return (await isWriteResponseSuccess(res)) ? ('ok' as const) : ('failed' as const);
    });

  const fetchLatestEpisodeProgress = async (episodeId: string) =>
    timedRequest(async (requestSignal) => {
      const res = await apiFetch(`${API}/users/watch-progress/${encodeURIComponent(episodeId)}`, {
        headers: readHeaders,
        signal: requestSignal,
      });
      if (res.status === 401) return { status: 'unauthorized' as const };
      if (res.status === 404) return { status: 'missing' as const };
      if (!res.ok) return { status: 'failed' as const };
      const json = (await res.json()) as { success?: unknown; data?: unknown } | null;
      if (json?.success !== true) return { status: 'failed' as const };
      const summary = parseServerProgressSummary(json.data);
      return summary ? { status: 'found' as const, record: summary } : { status: 'failed' as const };
    });

  const remainingProgress: GuestProgressItem[] = [];
  if (authExpired) {
    remainingProgress.push(...guestProgress);
  } else if (guestProgress.length > 0) {
    const existingByEpisode = new Map<string, ServerProgressSummary>();
    let lookupFailed = false;
    try {
      const lookupOutcome = await timedRequest(async (requestSignal) => {
        const progressResponse = await apiFetch(`${API}/users/watch-progress`, {
          headers: readHeaders,
          signal: requestSignal,
        });
        if (progressResponse.status === 401) return { status: 'unauthorized' as const };
        if (!progressResponse.ok) return { status: 'failed' as const };
        const progressBody = (await progressResponse.json()) as { success?: unknown; data?: unknown } | null;
        if (progressBody?.success !== true || !Array.isArray(progressBody.data)) {
          return { status: 'failed' as const };
        }
        return { status: 'ok' as const, rows: progressBody.data };
      });
      if (!isStillCurrent() || signal.aborted) {
        return { aborted: true, authExpired: false, hasGuestItems: true, syncedWriteCount, failedCount, mergedProgressCount, mergedRatingsCount };
      }
      if (lookupOutcome.status === 'unauthorized') {
        authExpired = true;
        remainingProgress.push(...guestProgress);
      } else if (lookupOutcome.status === 'ok') {
        for (const row of lookupOutcome.rows) {
          if (!row || typeof row !== 'object') continue;
          const entry = row as { episodeId?: unknown };
          const summary = parseServerProgressSummary(row);
          if (entry.episodeId && summary) {
            existingByEpisode.set(String(entry.episodeId), summary);
          }
        }
      } else {
        lookupFailed = true;
      }
    } catch {
      if (!isStillCurrent() || signal.aborted) {
        return { aborted: true, authExpired: false, hasGuestItems: true, syncedWriteCount, failedCount, mergedProgressCount, mergedRatingsCount };
      }
      lookupFailed = true;
    }

    if (!authExpired && lookupFailed) {
      remainingProgress.push(...guestProgress);
      failedCount += guestProgress.length;
    } else if (!authExpired) {
      const orderedGuestProgress = [...guestProgress].reverse().sort((a, b) => {
        const timeA = typeof a.raw.updatedAt === 'string' ? parseTimestamp(a.raw.updatedAt) : NaN;
        const timeB = typeof b.raw.updatedAt === 'string' ? parseTimestamp(b.raw.updatedAt) : NaN;
        if (Number.isFinite(timeA) && Number.isFinite(timeB)) return timeA - timeB;
        return 0;
      });
      const failedProgress: GuestProgressItem[] = [];
      for (let i = 0; i < orderedGuestProgress.length; i++) {
        if (!isStillCurrent() || signal.aborted) {
          return { aborted: true, authExpired: false, hasGuestItems: true, syncedWriteCount, failedCount, mergedProgressCount, mergedRatingsCount };
        }
        const progressEntry = orderedGuestProgress[i];
        const existing = existingByEpisode.get(progressEntry.episodeId);
        if (existing && (existing.completed || existing.positionMs >= progressEntry.positionMs)) continue;
        try {
          let outcome = await putProgressOnce(progressEntry, existing ? existing.version : -1);
          if (!isStillCurrent() || signal.aborted) {
            return { aborted: true, authExpired: false, hasGuestItems: true, syncedWriteCount, failedCount, mergedProgressCount, mergedRatingsCount };
          }
          if (outcome === 'conflict') {
            const latest = await fetchLatestEpisodeProgress(progressEntry.episodeId);
            if (!isStillCurrent() || signal.aborted) {
              return { aborted: true, authExpired: false, hasGuestItems: true, syncedWriteCount, failedCount, mergedProgressCount, mergedRatingsCount };
            }
            if (latest.status === 'unauthorized') {
              outcome = 'unauthorized';
            } else if (latest.status === 'found') {
              existingByEpisode.set(progressEntry.episodeId, latest.record);
              if (latest.record.completed || latest.record.positionMs >= progressEntry.positionMs) {
                continue;
              }
              outcome = await putProgressOnce(progressEntry, latest.record.version);
            } else if (latest.status === 'missing') {
              outcome = await putProgressOnce(progressEntry, -1);
            } else {
              outcome = 'failed';
            }
          }
          if (!isStillCurrent() || signal.aborted) {
            return { aborted: true, authExpired: false, hasGuestItems: true, syncedWriteCount, failedCount, mergedProgressCount, mergedRatingsCount };
          }
          if (outcome === 'unauthorized') {
            authExpired = true;
            remainingProgress.push(...orderedGuestProgress.slice(i).reverse());
            break;
          }
          if (outcome === 'ok') {
            mergedProgressCount += 1;
            syncedWriteCount += 1;
          } else {
            failedCount += 1;
            failedProgress.push(progressEntry);
          }
        } catch {
          if (!isStillCurrent() || signal.aborted) {
            return { aborted: true, authExpired: false, hasGuestItems: true, syncedWriteCount, failedCount, mergedProgressCount, mergedRatingsCount };
          }
          failedCount += 1;
          failedProgress.push(progressEntry);
        }
      }
      if (failedProgress.length > 0) {
        remainingProgress.push(...failedProgress.reverse());
      }
    }
  }

  if (!isStillCurrent() || signal.aborted) {
    return { aborted: true, authExpired: false, hasGuestItems: true, syncedWriteCount, failedCount, mergedProgressCount, mergedRatingsCount };
  }

  persistRemainingGuestData(
    { watchlist: remainingWatchlist, favorites: remainingFavorites, ratings: remainingRatings },
    remainingProgress
  );

  return {
    aborted: false,
    authExpired,
    hasGuestItems: true,
    syncedWriteCount,
    failedCount,
    mergedProgressCount,
    mergedRatingsCount,
  };
}

function isOperationAcknowledged(operation: OperationRecord, readVersion: number): boolean {
  return (
    operation.status === 'committed' &&
    operation.committedAtReadVersion !== null &&
    readVersion > operation.committedAtReadVersion
  );
}

function reconcileIdSet(
  serverIds: string[],
  action: 'watchlist' | 'favorite',
  journal: Map<string, OperationRecord>,
  readVersion: number
): string[] {
  const resolved = new Set<string>();
  for (const id of serverIds) {
    const key = `${action}:${id}`;
    const operation = journal.get(key);
    if (!operation || isOperationAcknowledged(operation, readVersion)) {
      resolved.add(id);
    } else if (operation.status === 'uncertain') {
      if (operation.desiredValue === true) {
        operation.status = 'committed';
        operation.committedAtReadVersion = readVersion;
        resolved.add(id);
      } else if (readVersion > operation.readVersionAtStart) {
        journal.delete(key);
        resolved.add(id);
      }
    } else if (operation.desiredValue === true) {
      resolved.add(id);
    }
  }

  for (const [key, operation] of journal.entries()) {
    if (operation.action !== action || isOperationAcknowledged(operation, readVersion)) continue;
    if (operation.status === 'uncertain' && readVersion > operation.readVersionAtStart) {
      journal.delete(key);
      continue;
    }
    if (operation.desiredValue === true) resolved.add(operation.movieId);
    else resolved.delete(operation.movieId);
  }

  return Array.from(resolved);
}

export function reconcileLibrarySnapshot(
  serverData: GuestLibraryData,
  journal: Map<string, OperationRecord>,
  readVersion: number
): GuestLibraryData {
  const watchlist = reconcileIdSet(serverData.watchlist, 'watchlist', journal, readVersion);
  const favorites = reconcileIdSet(serverData.favorites, 'favorite', journal, readVersion);
  const ratings: Record<string, number> = { ...serverData.ratings };

  for (const [key, operation] of journal.entries()) {
    if (operation.action !== 'rating' || isOperationAcknowledged(operation, readVersion)) continue;
    if (operation.status === 'uncertain' && readVersion > operation.readVersionAtStart) {
      journal.delete(key);
      continue;
    }
    if (typeof operation.desiredValue === 'number') {
      ratings[operation.movieId] = operation.desiredValue;
    } else {
      delete ratings[operation.movieId];
    }
  }

  for (const [key, operation] of journal.entries()) {
    if (isOperationAcknowledged(operation, readVersion)) {
      journal.delete(key);
    }
  }

  return { watchlist, favorites, ratings };
}
