import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import type { SeriesCard as SeriesCardData } from '@/lib/services/seriesCardService';
import { useSeriesCards } from '@/lib/hooks/useCatalogData';
import { SeriesCard } from '@/features/series/components/SeriesCard';
import { useOptionalAuth } from '@/lib/context/AuthContext';
import { fetchKidVipStatus } from '@/lib/services/vipService';

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
  title = 'Phim bộ',
  viewAllHref = '/series',
}: SeriesRowProps) {
  const isControlled = controlledCards !== undefined || controlledLoading !== undefined;
  const fetched = useSeriesCards(!isControlled, 'Chưa tải được danh sách phim bộ.');

  const cards = controlledCards ?? fetched.cards;
  const loading = controlledLoading ?? fetched.loading;
  const error = controlledError !== undefined ? controlledError : fetched.error;
  const handleRetry = controlledRetry ?? fetched.retry;

  const trackRef = useRef<HTMLDivElement>(null);
  const [canScrollLeft, setCanScrollLeft] = useState(false);
  const [canScrollRight, setCanScrollRight] = useState(false);
  const auth = typeof useOptionalAuth === 'function' ? useOptionalAuth() : null;
  const isKidMode = Boolean(auth?.isAuthenticated && auth?.isKidMode && auth?.token);
  const [vipSeriesIds, setVipSeriesIds] = useState<string[]>([]);

  useEffect(() => {
    if (!isKidMode || !auth?.token) {
      setVipSeriesIds([]);
      return;
    }
    const controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
    fetchKidVipStatus(auth.token, { signal: controller?.signal })
      .then((status) => {
        if (!controller?.signal.aborted) setVipSeriesIds(status.vipSeriesIds);
      })
      .catch(() => {
        if (!controller?.signal.aborted) setVipSeriesIds([]);
      });
    return () => controller?.abort();
  }, [isKidMode, auth?.token, auth?.activeProfile?.id]);

  const updateScrollState = useCallback(() => {
    const track = trackRef.current;
    if (!track) return;
    setCanScrollLeft(track.scrollLeft > 4);
    setCanScrollRight(track.scrollLeft + track.clientWidth < track.scrollWidth - 4);
  }, []);

  useEffect(() => {
    updateScrollState();
    const track = trackRef.current;
    if (!track || typeof window === 'undefined') return;
    track.addEventListener('scroll', updateScrollState, { passive: true });
    window.addEventListener('resize', updateScrollState);
    return () => {
      track.removeEventListener('scroll', updateScrollState);
      window.removeEventListener('resize', updateScrollState);
    };
  }, [cards.length, loading, updateScrollState]);

  const scrollByDirection = (direction: -1 | 1) => {
    const track = trackRef.current;
    if (!track) return;
    const prefersReduced =
      typeof window !== 'undefined' &&
      Boolean(window.matchMedia?.('(prefers-reduced-motion: reduce)').matches);
    const distance = Math.max(260, Math.round(track.clientWidth * 0.8));
    track.scrollBy({
      left: direction * distance,
      behavior: prefersReduced ? 'auto' : 'smooth',
    });
  };

  return (
    <section className="shelf-section" aria-labelledby="series-row-title" data-testid="series-row">
      <div className="shelf-heading items-center">
        <h2 id="series-row-title" className="section-title">
          {title}
        </h2>
        <div className="flex items-center gap-2">
          {!loading && !error && cards.length > 0 && (
            <div className="hidden sm:flex items-center gap-1.5">
              <button
                type="button"
                aria-label="Cuộn danh sách phim bộ sang trái"
                disabled={!canScrollLeft}
                onClick={() => scrollByDirection(-1)}
                className="series-scroll-btn"
                data-testid="series-row-prev"
              >
                <ChevronLeft size={18} aria-hidden="true" />
              </button>
              <button
                type="button"
                aria-label="Cuộn danh sách phim bộ sang phải"
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
          aria-label="Đang tải phim bộ"
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
        <div className="shelf-message">Chưa có phim bộ nào.</div>
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
              <SeriesCard card={card} headingLevel="h3" className="h-full" isVip={isKidMode && vipSeriesIds.includes(card.id)} />
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
