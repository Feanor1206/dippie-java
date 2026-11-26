import { useCallback, useEffect, useState } from 'react';
import { RATING_CHANGED_EVENT } from '@/lib/context/WatchlistContext';
import { resolveGenreLabel } from '@/lib/labels';
import { getCatalogGenres, type CatalogGenreOption } from '@/lib/services/catalogService';
import { listSeriesCards, type SeriesCard } from '@/lib/services/seriesCardService';

export function useCatalogGenres(): CatalogGenreOption[] | null {
  const [genres, setGenres] = useState<CatalogGenreOption[] | null>(null);

  useEffect(() => {
    if (typeof getCatalogGenres !== 'function' || typeof fetch !== 'function') return;
    const controller = new AbortController();
    getCatalogGenres({ signal: controller.signal })
      .then((fetchedGenres) => {
        if (controller.signal.aborted || fetchedGenres.length === 0) return;
        setGenres(fetchedGenres.map((genre) => ({ ...genre, label: resolveGenreLabel(genre) })));
      })
      .catch(() => {});
    return () => controller.abort();
  }, []);

  return genres;
}

export function useSeriesCards(enabled = true, fallbackError = 'Chưa tải được danh sách phim bộ.') {
  const canLoadSeries = Boolean(enabled && typeof listSeriesCards === 'function');
  const [cards, setCards] = useState<SeriesCard[]>([]);
  const [loading, setLoading] = useState(canLoadSeries);
  const [error, setError] = useState<string | null>(null);
  const [retryCount, setRetryCount] = useState(0);
  const [ratingRefreshCount, setRatingRefreshCount] = useState(0);

  useEffect(() => {
    if (!canLoadSeries || typeof window === 'undefined' || typeof window.addEventListener !== 'function') return;
    const eventName = typeof RATING_CHANGED_EVENT === 'string' ? RATING_CHANGED_EVENT : 'dippie:rating-changed';
    const onRatingChanged = () => setRatingRefreshCount((n) => n + 1);
    window.addEventListener(eventName, onRatingChanged);
    return () => window.removeEventListener(eventName, onRatingChanged);
  }, [canLoadSeries]);

  useEffect(() => {
    if (!canLoadSeries) return;
    const controller = new AbortController();
    const silentRefresh = ratingRefreshCount > 0 && retryCount === 0;
    if (!silentRefresh) {
      setLoading(true);
      setError(null);
    }
    listSeriesCards({ signal: controller.signal, forceRefresh: retryCount > 0 || ratingRefreshCount > 0 })
      .then((fetchedCards) => {
        if (!controller.signal.aborted) {
          setCards(fetchedCards);
          setLoading(false);
          setError(null);
        }
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted || (err as { isCanceled?: boolean })?.isCanceled) return;
        if (!silentRefresh) {
          setError((err as { message?: string })?.message || fallbackError);
          setLoading(false);
        }
      });
    return () => controller.abort();
  }, [canLoadSeries, retryCount, ratingRefreshCount, fallbackError]);

  const retry = useCallback(() => setRetryCount((n) => n + 1), []);

  return { cards, loading, error, retry };
}
