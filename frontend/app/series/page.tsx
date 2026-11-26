import React, { useMemo, useRef } from 'react';
import { useSearchParams } from 'react-router';
import { Search, X } from 'lucide-react';
import { useSeriesCards } from '@/lib/hooks/useCatalogData';
import * as motion from '@/lib/motion';
import { SeriesCard } from '@/features/series/components/SeriesCard';
import {
  extractSeriesGenres,
  filterAndSortSeriesCards,
  parseSeriesBrowseParams,
  SERIES_SORT_OPTIONS,
  serializeSeriesBrowseParams,
  type SeriesBrowseQueryState,
  type SeriesSortKey,
} from '@/features/series/seriesBrowseUtils';

export default function SeriesBrowsePage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const pageRef = useRef<HTMLElement>(null);
  const queryState = useMemo(() => parseSeriesBrowseParams(searchParams), [searchParams]);
  const { cards, loading, error, retry } = useSeriesCards(true, 'Chưa tải được danh sách phim bộ. Bạn thử lại nhé.');

  const updateQueryState = (next: SeriesBrowseQueryState) => {
    setSearchParams(serializeSeriesBrowseParams(next), { replace: true });
  };

  const handleToggleGenre = (genreId: string) => {
    const targetKey = genreId.toLocaleLowerCase('vi');
    const exists = queryState.genres.some((g) => g.toLocaleLowerCase('vi') === targetKey);
    const nextGenres = exists
      ? queryState.genres.filter((g) => g.toLocaleLowerCase('vi') !== targetKey)
      : [...queryState.genres, genreId];
    updateQueryState({ ...queryState, genres: nextGenres });
  };

  const handleResetFilters = () => updateQueryState({ q: '', genres: [], sort: 'title' });

  const availableGenres = useMemo(() => extractSeriesGenres(cards), [cards]);
  const filteredCards = useMemo(() => filterAndSortSeriesCards(cards, queryState), [cards, queryState]);
  const selectedGenreKeys = useMemo(
    () => new Set(queryState.genres.map((g) => g.toLocaleLowerCase('vi'))),
    [queryState.genres]
  );
  const hasActiveFilters = Boolean(queryState.q.trim() || queryState.genres.length > 0);
  motion.useScrollReveal?.(pageRef, [filteredCards.length, loading]);

  return (
    <main ref={pageRef} id="main-content" tabIndex={-1} className="page-shell min-h-[75vh] py-6 sm:py-8">
      <header data-reveal-section="" className="catalog-cover">
        <div className="self-center px-5 py-4 sm:px-8 sm:py-6">
          <h1 className="comic-heading text-3xl font-bold sm:text-4xl">Phim bộ</h1>
          <p className="mt-1 text-sm text-muted">
            Tìm phim bộ theo tên, lọc theo thể loại hoặc xếp theo tên, năm và số tập.
          </p>
        </div>
        <div className="catalog-cover-art" aria-hidden="true" />
      </header>

      <section aria-label="Tìm và lọc phim bộ" className="mt-5 space-y-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
          <div className="catalog-search mt-0 flex-1">
            <Search size={18} className="ml-1.5 shrink-0 text-muted" aria-hidden="true" />
            <input
              id="series-search-input"
              data-testid="series-search-input"
              type="search"
              aria-label="Tìm phim bộ theo tên"
              value={queryState.q}
              onChange={(event) => updateQueryState({ ...queryState, q: event.target.value })}
              placeholder="Tìm theo tên phim bộ…"
              className="min-h-12 min-w-0 flex-1 bg-transparent px-2.5 text-base text-ink placeholder:text-muted focus:outline-none"
            />
            {queryState.q && (
              <button
                type="button"
                onClick={() => updateQueryState({ ...queryState, q: '' })}
                aria-label="Xóa từ khóa tìm kiếm"
                className="inline-flex min-h-[44px] min-w-[44px] cursor-pointer items-center justify-center rounded-md px-2 text-sm text-muted hover:bg-surface-elevated hover:text-ink"
              >
                <X size={16} aria-hidden="true" />
              </button>
            )}
          </div>

          <div className="flex items-center gap-2 sm:shrink-0">
            <label htmlFor="series-sort-select" className="text-sm font-semibold text-ink whitespace-nowrap">
              Sắp xếp
            </label>
            <select
              id="series-sort-select"
              data-testid="series-sort-select"
              value={queryState.sort}
              onChange={(event) => updateQueryState({ ...queryState, sort: event.target.value as SeriesSortKey })}
              className="min-h-12 rounded-lg border border-[#a6b4a4] bg-paper-white px-3 py-2 text-base sm:text-sm font-medium text-ink focus:border-ink focus:outline-none cursor-pointer"
            >
              {SERIES_SORT_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>
        </div>

        {availableGenres.length > 0 && (
          <div
            className="flex flex-wrap items-center gap-2"
            role="group"
            aria-label="Lọc theo thể loại"
            data-testid="series-genre-chips"
          >
            <button
              type="button"
              data-testid="genre-chip-all"
              aria-pressed={queryState.genres.length === 0}
              onClick={() => queryState.genres.length > 0 && updateQueryState({ ...queryState, genres: [] })}
              style={{ '--stagger-idx': 0 } as React.CSSProperties}
              className={`series-filter-chip motion-chip-pop min-h-[44px] ${queryState.genres.length === 0 ? 'series-filter-chip--active' : ''}`}
            >
              Tất cả
            </button>
            {availableGenres.map((genre, index) => {
              const active = selectedGenreKeys.has(genre.id.toLocaleLowerCase('vi'));
              return (
                <button
                  key={genre.id}
                  type="button"
                  data-testid="genre-chip"
                  data-genre-id={genre.id}
                  aria-pressed={active}
                  onClick={() => handleToggleGenre(genre.id)}
                  style={{ '--stagger-idx': Math.min(index + 1, 10) } as React.CSSProperties}
                  className={`series-filter-chip motion-chip-pop min-h-[44px] ${active ? 'series-filter-chip--active' : ''}`}
                >
                  {genre.label}
                </button>
              );
            })}
          </div>
        )}
      </section>

      <div className="catalog-status" role="status" data-testid="series-result-status">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
          {loading ? (
            <span>Đang tải phim bộ…</span>
          ) : error ? (
            <span>Chưa tải được danh sách phim bộ</span>
          ) : (
            <span>
              {hasActiveFilters
                ? `Hiển thị ${filteredCards.length} / ${cards.length} phim bộ`
                : `${filteredCards.length} phim bộ`}
            </span>
          )}
        </div>
        {hasActiveFilters && !loading && !error && (
          <button
            type="button"
            onClick={handleResetFilters}
            className="inline-flex min-h-[44px] items-center px-2 text-sm font-semibold text-[#36563c] underline-offset-4 hover:underline cursor-pointer"
          >
            Xóa bộ lọc
          </button>
        )}
      </div>

      <section aria-label="Danh sách phim bộ">
        {loading ? (
          <div
            data-testid="series-grid-skeleton"
            role="status"
            aria-label="Đang tải danh sách phim bộ"
            className="series-catalog-grid"
          >
            {Array.from({ length: 6 }).map((_, index) => (
              <div key={index} className="series-skeleton-card animate-pulse">
                <div className="aspect-video w-full bg-surface-elevated" />
                <div className="space-y-2.5 p-4">
                  <div className="h-5 w-3/4 rounded bg-surface-elevated" />
                  <div className="h-3.5 w-1/2 rounded bg-surface-elevated" />
                  <div className="flex gap-1.5 pt-1">
                    <div className="h-5 w-14 rounded bg-surface-elevated" />
                    <div className="h-5 w-16 rounded bg-surface-elevated" />
                  </div>
                </div>
              </div>
            ))}
          </div>
        ) : error ? (
          <div
            role="alert"
            data-testid="series-error-state"
            className="flex flex-col items-center rounded-xl border border-red-500/30 bg-red-950/10 px-6 py-12 text-center"
          >
            <h2 className="text-lg font-bold text-ink">Chưa tải được danh sách phim bộ</h2>
            <p className="mt-1 max-w-md text-sm text-muted">{error}</p>
            <button type="button" data-testid="series-retry-button" onClick={retry} className="btn-primary mt-5 text-xs">
              Thử lại
            </button>
          </div>
        ) : filteredCards.length === 0 ? (
          <div
            data-testid="series-empty-state"
            className="rounded-xl border border-dashed border-surface-border bg-surface px-6 py-16 text-center"
          >
            <h2 className="text-base font-bold text-ink">
              {cards.length === 0 ? 'Chưa có phim bộ nào' : 'Không tìm thấy phim bộ phù hợp'}
            </h2>
            <p className="mx-auto mt-1.5 max-w-md text-sm text-muted">
              {cards.length === 0
                ? 'Kho hiện chưa có phim bộ nào.'
                : 'Thử đổi từ khóa hoặc bỏ bớt thể loại đang chọn.'}
            </p>
            {hasActiveFilters && (
              <button type="button" onClick={handleResetFilters} className="btn-secondary mt-5 text-xs">
                Xem tất cả phim bộ
              </button>
            )}
          </div>
        ) : (
          <div data-testid="series-catalog-grid" data-reveal-group="" className="series-catalog-grid">
            {filteredCards.map((card) => (
              <SeriesCard key={card.id} card={card} headingLevel="h2" className="h-full" />
            ))}
          </div>
        )}
      </section>
    </main>
  );
}
