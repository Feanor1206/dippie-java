import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import {
  listSeriesCards,
  type SeriesCard as SeriesCardData,
} from '@/lib/services/seriesCardService';
import { SeriesCard } from '@/features/series/components/SeriesCard';

export type SeriesRowProps = {
  cards?: SeriesCardData[];
  loading?: boolean;
  error?: string | null;
  onRetry?: () => void;
  title?: string;
  viewAllHref?: string;
};

export function SeriesRow({
  cards: controlledCards,
  loading: controlledLoading,
  error: controlledError,
  onRetry: controlledRetry,
  title = 'Các bộ phim',
  viewAllHref = '/series',
}: SeriesRowProps) {
  const isControlled = controlledCards !== undefined || controlledLoading !== undefined;
  const [internalCards, setInternalCards] = useState<SeriesCardData[]>([]);
  const [internalLoading, setInternalLoading] = useState(!isControlled);
  const [internalError, setInternalError] = useState<string | null>(null);
  const [retryTick, setRetryTick] = useState(0);

  const trackRef = useRef<HTMLDivElement>(null);
  const [canScrollLeft, setCanScrollLeft] = useState(false);
  const [canScrollRight, setCanScrollRight] = useState(false);

  useEffect(() => {
    if (isControlled) return;
    const controller = new AbortController();
    setInternalLoading(true);
    setInternalError(null);
    listSeriesCards({ signal: controller.signal, forceRefresh: retryTick > 0 })
      .then((data) => {
        if (!controller.signal.aborted) {
          setInternalCards(data);
          setInternalLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted || (err as { isCanceled?: boolean })?.isCanceled) return;
        setInternalError((err as { message?: string })?.message || 'Không thể tải danh sách bộ phim.');
        setInternalLoading(false);
      });
    return () => controller.abort();
  }, [isControlled, retryTick]);

  const cards = controlledCards ?? internalCards;
  const loading = controlledLoading ?? internalLoading;
  const error = controlledError !== undefined ? controlledError : internalError;
  const handleRetry = controlledRetry ?? (() => setRetryTick((n) => n + 1));

  const updateScrollState = useCallback(() => {
    const el = trackRef.current;
    if (!el) return;
    setCanScrollLeft(el.scrollLeft > 4);
    setCanScrollRight(el.scrollLeft + el.clientWidth < el.scrollWidth - 4);
  }, []);

  useEffect(() => {
    updateScrollState();
    const el = trackRef.current;
    if (!el || typeof window === 'undefined') return;
    const onScroll = () => updateScrollState();
    const onResize = () => updateScrollState();
    el.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', onResize);
    return () => {
      el.removeEventListener('scroll', onScroll);
      window.removeEventListener('resize', onResize);
    };
  }, [cards.length, loading, updateScrollState]);

  const scrollByDirection = (direction: -1 | 1) => {
    const el = trackRef.current;
    if (!el) return;
    const prefersReduced =
      typeof window !== 'undefined' &&
      typeof window.matchMedia === 'function' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const distance = Math.max(260, Math.round(el.clientWidth * 0.8));
    el.scrollBy({
      left: direction * distance,
      behavior: prefersReduced ? 'auto' : 'smooth',
    });
  };

  return (
    <section
      className="shelf-section"
      aria-labelledby="series-row-title"
      data-testid="series-row"
    >
      <div className="shelf-heading items-center">
        <h2 id="series-row-title" className="section-title">
          {title}
        </h2>
        <div className="flex items-center gap-2">
          {!loading && !error && cards.length > 0 && (
            <div className="hidden sm:flex items-center gap-1.5">
              <button
                type="button"
                aria-label="Cuộn danh sách bộ phim sang trái"
                disabled={!canScrollLeft}
                onClick={() => scrollByDirection(-1)}
                className="series-scroll-btn"
                data-testid="series-row-prev"
              >
                <ChevronLeft size={18} aria-hidden="true" />
              </button>
              <button
                type="button"
                aria-label="Cuộn danh sách bộ phim sang phải"
                disabled={!canScrollRight}
                onClick={() => scrollByDirection(1)}
                className="series-scroll-btn"
                data-testid="series-row-next"
              >
                <ChevronRight size={18} aria-hidden="true" />
              </button>
            </div>
          )}
          <Link to={viewAllHref} className="shelf-link ml-1" data-testid="series-row-view-all">
            Xem tất cả
          </Link>
        </div>
      </div>

      {loading ? (
        <div
          className="series-row-track movie-row"
          role="status"
          aria-label="Đang tải các bộ phim"
          data-testid="series-row-skeleton"
        >
          {Array.from({ length: 4 }).map((_, index) => (
            <div key={index} className="series-row-item series-skeleton-card animate-pulse">
              <div className="aspect-video w-full bg-surface-elevated" />
              <div className="space-y-2 p-3.5">
                <div className="h-4 w-3/4 rounded bg-surface-elevated" />
                <div className="h-3 w-1/2 rounded bg-surface-elevated" />
              </div>
            </div>
          ))}
        </div>
      ) : error ? (
        <div className="shelf-message" role="alert">
          <span>{error}</span>
          <button type="button" className="btn-secondary" onClick={handleRetry}>
            Thử lại
          </button>
        </div>
      ) : cards.length === 0 ? (
        <div className="shelf-message">Chưa có bộ phim nào.</div>
      ) : (
        <div
          ref={trackRef}
          className="series-row-track movie-row"
          role="region"
          aria-label={title}
          data-testid="series-row-track"
        >
          {cards.map((card) => (
            <div key={card.id} className="series-row-item">
              <SeriesCard card={card} headingLevel="h3" className="h-full" />
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
