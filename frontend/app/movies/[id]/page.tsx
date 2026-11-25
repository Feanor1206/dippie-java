import React, { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { MovieDetail } from '@/features/movies/components/MovieDetail';
import { browseCatalog, getCatalogMovieById } from '@/lib/services/catalogService';
import { RATING_CHANGED_EVENT } from '@/lib/context/WatchlistContext';
import { cleanMediaTitle } from '@/lib/labels';
import type { Movie } from '@/types/movie';

type MoviePageState =
  | { status: 'loading' }
  | { status: 'not_found' }
  | { status: 'error'; message: string }
  | { status: 'ready'; movie: Movie };

export default function MovieDetailPage() {
  const canonicalId = useParams<'id'>().id?.trim() || '';
  const [state, setState] = useState<MoviePageState>({ status: 'loading' });
  const [similarMovies, setSimilarMovies] = useState<Movie[]>([]);
  const [retryCount, setRetryCount] = useState(0);

  useEffect(() => {
    if (!canonicalId) {
      setState({ status: 'not_found' });
      return;
    }

    let isCurrent = true;
    const controller = new AbortController();
    setState({ status: 'loading' });
    setSimilarMovies([]);

    getCatalogMovieById(canonicalId, { signal: controller.signal })
      .then((fetchedMovie) => {
        if (!isCurrent) return;
        if (!fetchedMovie) {
          setState({ status: 'not_found' });
          return;
        }
        setState({ status: 'ready', movie: fetchedMovie });

        browseCatalog({
          animationOnly: true,
          genres: fetchedMovie.genres?.length ? fetchedMovie.genres.slice(0, 2) : ['Animation'],
          size: 6,
          signal: controller.signal,
        })
          .then((response) => isCurrent && setSimilarMovies(response.items.filter((movie) => movie.id !== fetchedMovie.id).slice(0, 5)))
          .catch(() => {});
      })
      .catch((err: unknown) => {
        const candidate = err as { isCanceled?: unknown; name?: unknown; message?: unknown } | null;
        if (!isCurrent || candidate?.isCanceled || candidate?.name === 'AbortError') return;
        setState({
          status: 'error',
          message: typeof candidate?.message === 'string' ? candidate.message : 'Chưa kết nối được máy chủ. Bạn thử lại nhé.',
        });
      });

    return () => {
      isCurrent = false;
      controller.abort();
    };
  }, [canonicalId, retryCount]);

  useEffect(() => {
    if (!canonicalId || typeof window === 'undefined' || typeof window.addEventListener !== 'function') return;
    const eventName = typeof RATING_CHANGED_EVENT === 'string' ? RATING_CHANGED_EVENT : 'dippie:rating-changed';
    let activeController: AbortController | null = null;
    const handleRatingChanged = (event: Event) => {
      const changedId = (event as CustomEvent<{ movieId?: string }>)?.detail?.movieId;
      if (changedId && changedId !== canonicalId) return;
      activeController?.abort();
      activeController = new AbortController();
      getCatalogMovieById(canonicalId, { signal: activeController.signal })
        .then((freshMovie) => {
          if (!activeController?.signal.aborted && freshMovie) {
            setState({ status: 'ready', movie: freshMovie });
          }
        })
        .catch(() => {});
    };
    window.addEventListener(eventName, handleRatingChanged);
    return () => {
      activeController?.abort();
      window.removeEventListener(eventName, handleRatingChanged);
    };
  }, [canonicalId]);

  const loadedMovie = state.status === 'ready' ? state.movie : null;

  useEffect(() => {
    if (typeof document === 'undefined') return;
    if (loadedMovie?.title) {
      document.title = `${cleanMediaTitle(loadedMovie.title)} · Dippie`;
    } else if (state.status === 'not_found') {
      document.title = 'Không tìm thấy phim · Dippie';
    }
  }, [loadedMovie?.title, state.status]);

  if (state.status === 'loading') {
    return (
      <main id="main-content" tabIndex={-1} className="min-h-screen bg-background">
        <div role="status" className="page-shell py-8 sm:py-12 animate-pulse">
          <span className="sr-only">Đang tải thông tin phim…</span>
          <div className="grid gap-8 rounded-2xl border border-surface-border/40 bg-paper-white p-6 sm:grid-cols-[240px_1fr] sm:p-8">
            <div className="aspect-[2/3] w-full rounded-xl bg-surface-elevated" />
            <div className="space-y-4">
              <div className="h-4 w-28 rounded bg-surface-elevated" />
              <div className="h-9 w-3/4 rounded bg-surface-elevated" />
              <div className="flex gap-2">
                <div className="h-6 w-16 rounded bg-surface-elevated" />
                <div className="h-6 w-20 rounded bg-surface-elevated" />
              </div>
              <div className="space-y-2 pt-2">
                <div className="h-4 w-full rounded bg-surface-elevated" />
                <div className="h-4 w-5/6 rounded bg-surface-elevated" />
                <div className="h-4 w-2/3 rounded bg-surface-elevated" />
              </div>
            </div>
          </div>
        </div>
      </main>
    );
  }

  if (state.status === 'not_found' || state.status === 'error') {
    const isNotFound = state.status === 'not_found';
    return (
      <main id="main-content" tabIndex={-1} className="min-h-[70vh] flex items-center justify-center bg-background px-4 py-16 text-center">
        <div className="max-w-md space-y-4 rounded-xl border border-surface-border bg-surface p-8">
          <div className="grid h-12 w-12 place-items-center rounded-full mx-auto border border-surface-border bg-surface-elevated text-ink font-bold">
            {isNotFound ? '404' : 'Lỗi'}
          </div>
          <h1 className="text-xl font-bold text-ink">
            {isNotFound ? 'Không tìm thấy phim này' : 'Chưa kết nối được máy chủ'}
          </h1>
          <p className="text-sm text-muted">
            {isNotFound ? `Phim "${canonicalId}" không có trong kho phim Dippie.` : state.message}
          </p>
          <div className="pt-2 flex flex-wrap justify-center gap-3">
            {isNotFound ? (
              <Link to="/search?genre=animation" className="btn-primary text-xs">
                Xem kho phim
              </Link>
            ) : (
              <button onClick={() => setRetryCount((count) => count + 1)} className="btn-primary text-xs cursor-pointer">
                Thử lại
              </button>
            )}
            <Link to="/" className="btn-secondary text-xs">
              Về trang chủ
            </Link>
          </div>
        </div>
      </main>
    );
  }

  return <MovieDetail movie={state.movie} similarMovies={similarMovies} />;
}
