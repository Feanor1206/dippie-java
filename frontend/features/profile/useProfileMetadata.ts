import { SESSION_RESET_EVENT } from '@/lib/services/sessionStorage';
import { useCallback, useEffect, useRef, useState } from 'react';
import { RATING_CHANGED_EVENT } from '@/lib/context/WatchlistContext';
import { getCatalogMovieById } from '@/lib/services/catalogService';
import type { Movie } from '@/types/movie';

export type HydratedMovieItem =
  | { status: 'loading' }
  | { status: 'found'; movie: Movie; expiresAt: number }
  | { status: 'not_found' }
  | { status: 'error' };

const METADATA_TTL_MS = 5 * 60 * 1000;
const METADATA_FETCH_TIMEOUT_MS = 6000;
const MAX_CACHE_ENTRIES = 200;

const profileMetadataCache = new Map<string, { movie: Movie; expiresAt: number }>();

export function clearProfileMetadataCache(): void {
  profileMetadataCache.clear();
}

export function pruneProfileMetadataCache(now = Date.now()): void {
  for (const [id, cached] of profileMetadataCache.entries()) {
    if (cached.expiresAt <= now) profileMetadataCache.delete(id);
  }
}

const RATING_EVENT_NAME =
  typeof RATING_CHANGED_EVENT === 'string' ? RATING_CHANGED_EVENT : 'dippie:rating-changed';

if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
  window.addEventListener(RATING_EVENT_NAME, clearProfileMetadataCache);
  window.addEventListener(SESSION_RESET_EVENT, clearProfileMetadataCache);
}

function isAbortLike(err: unknown): boolean {
  if (typeof err !== 'object' || err === null) return false;
  const candidate = err as { isCanceled?: unknown; name?: unknown };
  return Boolean(candidate.isCanceled) || candidate.name === 'AbortError';
}

export function useProfileMetadata(itemIds: string[]) {
  const [remoteMovies, setRemoteMovies] = useState<Record<string, HydratedMovieItem>>({});
  const controllersRef = useRef<Map<string, AbortController>>(new Map());
  const remoteMoviesRef = useRef(remoteMovies);
  remoteMoviesRef.current = remoteMovies;

  const fetchMovie = useCallback(async (id: string, options?: { forceRefresh?: boolean; silent?: boolean }) => {
    const now = Date.now();
    const cached = options?.forceRefresh ? undefined : profileMetadataCache.get(id);
    if (cached && cached.expiresAt > now) {
      setRemoteMovies((prev) => ({
        ...prev,
        [id]: { status: 'found', movie: cached.movie, expiresAt: cached.expiresAt },
      }));
      return;
    }
    if (cached) profileMetadataCache.delete(id);

    controllersRef.current.get(id)?.abort();
    const controller = new AbortController();
    controllersRef.current.set(id, controller);

    if (!options?.silent) {
      setRemoteMovies((prev) => ({ ...prev, [id]: { status: 'loading' } }));
    }

    try {
      const movie = await getCatalogMovieById(id, {
        signal: controller.signal,
        timeoutMs: METADATA_FETCH_TIMEOUT_MS,
      });
      if (controller.signal.aborted) return;
      if (!movie) {
        setRemoteMovies((prev) => ({ ...prev, [id]: { status: 'not_found' } }));
        return;
      }
      const expiresAt = Date.now() + METADATA_TTL_MS;
      if (profileMetadataCache.size >= MAX_CACHE_ENTRIES) {
        const oldestKey = profileMetadataCache.keys().next().value;
        if (oldestKey) profileMetadataCache.delete(oldestKey);
      }
      profileMetadataCache.set(id, { movie, expiresAt });
      setRemoteMovies((prev) => ({ ...prev, [id]: { status: 'found', movie, expiresAt } }));
    } catch (err: unknown) {
      if (isAbortLike(err)) return;
      if (!options?.silent) {
        setRemoteMovies((prev) => ({ ...prev, [id]: { status: 'error' } }));
      }
    } finally {
      if (controllersRef.current.get(id) === controller) {
        controllersRef.current.delete(id);
      }
    }
  }, []);

  useEffect(() => {
    const controllers = controllersRef.current;
    return () => {
      for (const controller of controllers.values()) {
        try {
          controller.abort();
        } catch {}
      }
      controllers.clear();
    };
  }, []);

  useEffect(() => {
    const currentIdSet = new Set(itemIds);
    for (const [id, controller] of controllersRef.current.entries()) {
      if (!currentIdSet.has(id)) {
        try {
          controller.abort();
        } catch {}
        controllersRef.current.delete(id);
      }
    }
    setRemoteMovies((prev) => {
      const staleKeys = Object.keys(prev).filter((movieId) => !currentIdSet.has(movieId));
      if (staleKeys.length === 0) return prev;
      const next = { ...prev };
      for (const movieId of staleKeys) delete next[movieId];
      return next;
    });
  }, [itemIds]);

  useEffect(() => {
    const now = Date.now();
    for (const id of itemIds) {
      const cached = profileMetadataCache.get(id);
      if (cached && cached.expiresAt > now) continue;
      const remote = remoteMovies[id];
      if (!remote || (remote.status === 'found' && remote.expiresAt <= now)) {
        void fetchMovie(id);
      }
    }
  }, [itemIds, remoteMovies, fetchMovie]);

  useEffect(() => {
    if (typeof window === 'undefined' || typeof window.addEventListener !== 'function') return;

    const handleRevalidateStale = () => {
      const now = Date.now();
      for (const id of itemIds) {
        const cached = profileMetadataCache.get(id);
        if (cached && cached.expiresAt <= now) profileMetadataCache.delete(id);
        const remote = remoteMoviesRef.current[id];
        if (remote?.status === 'found' && remote.expiresAt <= now) {
          void fetchMovie(id);
        }
      }
    };

    const handleRatingChanged = (event: Event) => {
      clearProfileMetadataCache();
      const changedId = (event as CustomEvent<{ movieId?: string }>)?.detail?.movieId;
      const targetIds = changedId ? itemIds.filter((id) => id === changedId) : itemIds;
      for (const id of targetIds) {
        void fetchMovie(id, { forceRefresh: true, silent: true });
      }
    };

    window.addEventListener('focus', handleRevalidateStale);
    window.addEventListener(RATING_EVENT_NAME, handleRatingChanged);
    return () => {
      window.removeEventListener('focus', handleRevalidateStale);
      window.removeEventListener(RATING_EVENT_NAME, handleRatingChanged);
    };
  }, [itemIds, fetchMovie]);

  const resolveItem = useCallback(
    (id: string): HydratedMovieItem => {
      const now = Date.now();
      const cached = profileMetadataCache.get(id);
      if (cached) {
        if (cached.expiresAt > now) {
          return { status: 'found', movie: cached.movie, expiresAt: cached.expiresAt };
        }
        profileMetadataCache.delete(id);
      }
      const remote = remoteMovies[id];
      if (!remote) return { status: 'loading' };
      if (remote.status === 'found' && remote.expiresAt <= now) return { status: 'loading' };
      return remote;
    },
    [remoteMovies]
  );

  return { resolveItem, fetchMovie };
}
