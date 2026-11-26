import { parseTimestamp } from '@/lib/timestamps';
export const MOVIE_PROGRESS_KEY = 'dippie_movie_progress_v2';
export const MOVIE_PROGRESS_LIMIT = 30;

export type MovieProgressRecord = {
  movieId: string;
  title: string;
  thumbnailUrl?: string | null;
  positionMs: number;
  durationMs: number | null;
  completed: boolean;
  updatedAt: string;
};

export type MovieProgressDraft = {
  movieId: string;
  title: string;
  thumbnailUrl?: string | null;
  positionMs: number;
  durationMs?: number | null;
  completed?: boolean;
};

function nonEmpty(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function storage(): Storage | null {
  if (typeof window === 'undefined') return null;
  try {
    return window.localStorage;
  } catch {
    return null;
  }
}

export function normalizeMovieProgress(raw: unknown): MovieProgressRecord | null {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return null;
  const record = raw as Partial<MovieProgressRecord>;
  if (!nonEmpty(record.movieId) || !nonEmpty(record.title)) return null;
  if (typeof record.positionMs !== 'number' || !Number.isSafeInteger(record.positionMs) || record.positionMs < 0) return null;
  if (typeof record.completed !== 'boolean') return null;
  if (!nonEmpty(record.updatedAt) || !Number.isFinite(parseTimestamp(record.updatedAt))) return null;
  const durationMs = typeof record.durationMs === 'number' && Number.isSafeInteger(record.durationMs) && record.durationMs > 0
    ? record.durationMs
    : null;
  const thumbnailUrl = nonEmpty(record.thumbnailUrl) ? record.thumbnailUrl.trim() : null;
  return {
    movieId: record.movieId.trim(),
    title: record.title.trim(),
    ...(thumbnailUrl ? { thumbnailUrl } : {}),
    positionMs: durationMs === null ? record.positionMs : Math.min(record.positionMs, durationMs),
    durationMs,
    completed: record.completed,
    updatedAt: new Date(parseTimestamp(record.updatedAt)).toISOString(),
  };
}

function movieProgressKey(userId: number | null): string {
  return userId === null ? MOVIE_PROGRESS_KEY : `${MOVIE_PROGRESS_KEY}:${userId}`;
}

export function listMovieProgress(userId: number | null = null): MovieProgressRecord[] {
  const store = storage();
  if (!store) return [];
  try {
    const parsed = JSON.parse(store.getItem(movieProgressKey(userId)) || '[]');
    if (!Array.isArray(parsed)) return [];
    return parsed
      .map(normalizeMovieProgress)
      .filter((entry): entry is MovieProgressRecord => entry !== null)
      .sort((a, b) => parseTimestamp(b.updatedAt) - parseTimestamp(a.updatedAt))
      .slice(0, MOVIE_PROGRESS_LIMIT);
  } catch {
    return [];
  }
}

export function getMovieProgress(movieId: string, userId: number | null = null): MovieProgressRecord | null {
  if (!nonEmpty(movieId)) return null;
  return listMovieProgress(userId).find((entry) => entry.movieId === movieId.trim()) || null;
}

export function isMovieProgressCompleted(positionMs: number, durationMs: number | null | undefined): boolean {
  if (typeof durationMs !== 'number' || !Number.isFinite(durationMs) || durationMs <= 0) return false;
  const total = Math.floor(durationMs);
  const fivePercent = Math.max(1, Math.floor(total / 20));
  const nearEnd = Math.min(60_000, fivePercent);
  return positionMs >= total - nearEnd;
}

export function saveMovieProgress(
  draft: MovieProgressDraft,
  now = Date.now(),
  userId: number | null = null,
): MovieProgressRecord | null {
  const store = storage();
  if (!store || !draft || !nonEmpty(draft.movieId) || !nonEmpty(draft.title)) return null;
  if (!Number.isSafeInteger(draft.positionMs) || draft.positionMs < 0) return null;
  const durationMs = typeof draft.durationMs === 'number' && Number.isSafeInteger(draft.durationMs) && draft.durationMs > 0
    ? draft.durationMs
    : null;
  const thumbnailUrl = nonEmpty(draft.thumbnailUrl) ? draft.thumbnailUrl.trim() : null;
  const positionMs = durationMs === null ? draft.positionMs : Math.min(draft.positionMs, durationMs);
  const record: MovieProgressRecord = {
    movieId: draft.movieId.trim(),
    title: draft.title.trim(),
    ...(thumbnailUrl ? { thumbnailUrl } : {}),
    positionMs,
    durationMs,
    completed: Boolean(draft.completed) || isMovieProgressCompleted(positionMs, durationMs),
    updatedAt: new Date(now).toISOString(),
  };
  const next = [record, ...listMovieProgress(userId).filter((entry) => entry.movieId !== record.movieId)]
    .sort((a, b) => parseTimestamp(b.updatedAt) - parseTimestamp(a.updatedAt))
    .slice(0, MOVIE_PROGRESS_LIMIT);
  try {
    store.setItem(movieProgressKey(userId), JSON.stringify(next));
  } catch {
    return null;
  }
  return record;
}

export function clearMovieProgress(userId: number | null = null): void {
  const store = storage();
  if (!store) return;
  try {
    store.removeItem(movieProgressKey(userId));
  } catch {}
}

export function isUsableMovieProgress(record: MovieProgressRecord | null | undefined): record is MovieProgressRecord {
  return Boolean(
    record &&
      !record.completed &&
      nonEmpty(record.movieId) &&
      nonEmpty(record.title) &&
      record.positionMs > 0 &&
      (record.durationMs === null || (record.durationMs > 0 && !isMovieProgressCompleted(record.positionMs, record.durationMs)))
  );
}

export function buildMovieProgressHref(record: MovieProgressRecord): string {
  const params = new URLSearchParams();
  params.set('t', String(record.positionMs));
  return `/movies/${encodeURIComponent(record.movieId)}?${params.toString()}`;
}

export function getMovieResumeSeconds(movieId: string, userId: number | null = null): number {
  try {
    if (typeof window !== 'undefined' && window.location?.search) {
      const raw = new URLSearchParams(window.location.search).get('t');
      const positionMs = raw === null ? NaN : Number(raw);
      if (Number.isSafeInteger(positionMs) && positionMs > 0) return positionMs / 1000;
    }
  } catch {
  }
  const saved = getMovieProgress(movieId, userId);
  if (isUsableMovieProgress(saved)) return saved.positionMs / 1000;
  return 0;
}
