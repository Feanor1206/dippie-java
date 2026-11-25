import React, { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router';
import { Search, X } from 'lucide-react';
import {
  listSeriesCards,
  type SeriesCard as SeriesCardData,
} from '@/lib/services/seriesCardService';
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
  const queryState = useMemo(() => parseSeriesBrowseParams(searchParams), [searchParams]);

  const [cards, setCards] = useState<SeriesCardData[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [retryTick, setRetryTick] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    listSeriesCards({ signal: controller.signal, forceRefresh: retryTick > 0 })
      .then((data) => {
        if (!controller.signal.aborted) {
          setCards(data);
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted || (err as { isCanceled?: boolean })?.isCanceled) return;
        setError((err as { message?: string })?.message || 'Không thể tải danh mục bộ phim.');
        setLoading(false);
      });
    return () => controller.abort();
  }, [retryTick]);

  const updateQueryState = (next: SeriesBrowseQueryState) => {
    setSearchParams(serializeSeriesBrowseParams(next), { replace: true });
  };

  const handleSearchChange = (value: string) => {
    updateQueryState({ ...queryState, q: value });
  };

  const handleToggleGenre = (genreId: string) => {
    const targetKey = genreId.toLocaleLowerCase('vi');
    const exists = queryState.genres.some((g) => g.toLocaleLowerCase('vi') === targetKey);
    const nextGenres = exists
      ? queryState.genres.filter((g) => g.toLocaleLowerCase('vi') !== targetKey)
      : [...queryState.genres, genreId];
    updateQueryState({ ...queryState, genres: nextGenres });
  };

  const handleClearGenres = () => {
    if (queryState.genres.length === 0) return;
    updateQueryState({ ...queryState, genres: [] });
  };

  const handleSortChange = (sort: SeriesSortKey) => {
    updateQueryState({ ...queryState, sort });
  };

  const handleResetFilters = () => {
    updateQueryState({ q: '', genres: [], sort: 'title' });
  };

  const availableGenres = useMemo(() => extractSeriesGenres(cards), [cards]);
  const filteredCards = useMemo(
    () => filterAndSortSeriesCards(cards, queryState),
    [cards, queryState]
  );

  const selectedGenreKeys = useMemo(
    () => new Set(queryState.genres.map((g) => g.toLocaleLowerCase('vi'))),
    [queryState.genres]
  );

  const hasActiveFilters = Boolean(queryState.q.trim() || queryState.genres.length > 0);

  return (
    <main className="page-shell min-h-[75vh] py-6 sm:py-8">
      <header className="catalog-cover">
        <div className="self-center px-5 py-4 sm:px-8 sm:py-6">
          <h1 className="comic-heading text-3xl font-bold sm:text-4xl">Bộ phim</h1>
          <p className="mt-1 text-sm text-muted">
            Khám phá toàn bộ series hoạt hình theo thể loại, năm phát hành và số tập.
          </p>
        </div>
        <div className="catalog-cover-art" aria-hidden="true" />
      </header>

      <section aria-label="Tìm kiếm và lọc bộ phim" className="mt-5 space-y-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
          <div className="catalog-search mt-0 flex-1">
            <Search size={18} className="ml-1.5 shrink-0 text-muted" aria-hidden="true" />
            <input
              id="series-search-input"
              data-testid="series-search-input"
              type="search"
              aria-label="Tìm bộ phim theo tên hoặc tên gốc"
              value={queryState.q}
              onChange={(event) => handleSearchChange(event.target.value)}
              placeholder="Tìm theo tên bộ phim hoặc tên gốc…"
              className="min-h-12 min-w-0 flex-1 bg-transparent px-2.5 text-base text-ink placeholder:text-muted focus:outline-none"
            />
            {queryState.q && (
              <button
                type="button"
                onClick={() => handleSearchChange('')}
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
              onChange={(event) => handleSortChange(event.target.value as SeriesSortKey)}
              className="min-h-12 rounded-lg border border-[#a6b4a4] bg-paper-white px-3 py-2 text-sm font-medium text-ink focus:border-ink focus:outline-none cursor-pointer"
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
              onClick={handleClearGenres}
              className={`series-filter-chip ${queryState.genres.length === 0 ? 'series-filter-chip--active' : ''}`}
            >
              Tất cả
            </button>
            {availableGenres.map((genre) => {
              const active = selectedGenreKeys.has(genre.id.toLocaleLowerCase('vi'));
              return (
                <button
                  key={genre.id}
                  type="button"
                  data-testid="genre-chip"
                  data-genre-id={genre.id}
                  aria-pressed={active}
                  onClick={() => handleToggleGenre(genre.id)}
                  className={`series-filter-chip ${active ? 'series-filter-chip--active' : ''}`}
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
            <span>Đang tải danh sách bộ phim…</span>
          ) : error ? (
            <span>Không thể tải dữ liệu bộ phim</span>
          ) : (
            <span>
              {hasActiveFilters
                ? `Hiển thị ${filteredCards.length} / ${cards.length} bộ phim`
                : `${filteredCards.length} bộ phim`}
            </span>
          )}
        </div>
        {hasActiveFilters && !loading && !error && (
          <button
            type="button"
            onClick={handleResetFilters}
            className="text-sm font-semibold text-[#36563c] underline-offset-4 hover:underline cursor-pointer"
          >
            Xóa bộ lọc
          </button>
        )}
      </div>

      <section aria-label="Danh sách bộ phim">
        {loading ? (
          <div
            data-testid="series-grid-skeleton"
            role="status"
            aria-label="Đang tải danh sách bộ phim"
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
            <h2 className="text-lg font-bold text-ink">Không thể tải danh mục bộ phim</h2>
            <p className="mt-1 max-w-md text-sm text-muted">{error}</p>
            <button
              type="button"
              data-testid="series-retry-button"
              onClick={() => setRetryTick((n) => n + 1)}
              className="btn-primary mt-5 text-xs"
            >
              Thử lại
            </button>
          </div>
        ) : filteredCards.length === 0 ? (
          <div
            data-testid="series-empty-state"
            className="rounded-xl border border-dashed border-surface-border bg-surface px-6 py-16 text-center"
          >
            <h2 className="text-base font-bold text-ink">
              {cards.length === 0 ? 'Chưa có bộ phim nào' : 'Không tìm thấy bộ phim phù hợp'}
            </h2>
            <p className="mx-auto mt-1.5 max-w-md text-sm text-muted">
              {cards.length === 0
                ? 'Danh mục hiện chưa có bộ phim nào được xuất bản.'
                : 'Thử đổi từ khóa tìm kiếm hoặc bỏ bớt bộ lọc thể loại.'}
            </p>
            {hasActiveFilters && (
              <button
                type="button"
                onClick={handleResetFilters}
                className="btn-secondary mt-5 text-xs"
              >
                Xem tất cả bộ phim
              </button>
            )}
          </div>
        ) : (
          <div data-testid="series-catalog-grid" className="series-catalog-grid">
            {filteredCards.map((card) => (
              <SeriesCard key={card.id} card={card} headingLevel="h2" className="h-full" />
            ))}
          </div>
        )}
      </section>
    </main>
  );
}
