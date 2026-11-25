import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router';
import { genreLabel, resolveGenreLabel } from '@/lib/labels';
import {
  browseCatalog,
  deriveGenresFromMovies,
  getCatalogGenres,
  type CatalogGenreOption,
} from '@/lib/services/catalogService';
import { FilmGrid } from '@/features/movies/components/FilmShelf';
import type { Movie } from '@/types/movie';

type SortField = 'rating' | 'year' | 'title';
type SortDirection = 'asc' | 'desc';
type SortOptionValue = '' | 'year:desc' | 'year:asc' | 'rating:desc' | 'title:asc';

const PAGE_SIZE_DEFAULT = 12;
const MAX_PAGE_SIZE = 48;
const QUERY_DEBOUNCE_MS = 350;
const MIN_FILTER_YEAR = 1900;
const MAX_FILTER_YEAR = 2100;

const SORT_OPTIONS: readonly { value: SortOptionValue; label: string }[] = [
  { value: '', label: 'Mặc định' },
  { value: 'year:desc', label: 'Năm mới nhất' },
  { value: 'year:asc', label: 'Năm cũ nhất' },
  { value: 'rating:desc', label: 'Điểm cao nhất' },
  { value: 'title:asc', label: 'Tên từ A đến Z' },
];

type SearchFilterKey = {
  query: string;
  genre: string;
  yearMin?: number;
  yearMax?: number;
  sort?: SortField;
  direction?: SortDirection;
  page: number;
  size: number;
};

type SearchSnapshot = SearchFilterKey & {
  results: Movie[];
  totalElements: number;
  totalPages: number;
};

function parseNonNegativeInteger(value: string | null, fallback: number): number {
  if (!value || !/^\d+$/.test(value)) return fallback;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : fallback;
}

function parseOptionalYear(value: string | null): number | undefined {
  if (!value || !/^\d{4}$/.test(value.trim())) return undefined;
  const parsed = Number(value.trim());
  return Number.isSafeInteger(parsed) && parsed >= MIN_FILTER_YEAR && parsed <= MAX_FILTER_YEAR ? parsed : undefined;
}

function parseSortParams(
  rawSort: string | null,
  rawDirection: string | null
): { sort?: SortField; direction?: SortDirection; value: SortOptionValue } {
  const sort = (rawSort || '').trim();
  const direction = (rawDirection || '').trim();
  if (sort === 'year' && (direction === 'asc' || direction === 'desc')) return { sort: 'year', direction, value: `year:${direction}` };
  if (sort === 'rating' && direction === 'desc') return { sort: 'rating', direction: 'desc', value: 'rating:desc' };
  if (sort === 'title' && direction === 'asc') return { sort: 'title', direction: 'asc', value: 'title:asc' };
  return { value: '' };
}

function isSnapshotMatching(snapshot: SearchSnapshot | null, key: SearchFilterKey): snapshot is SearchSnapshot {
  return Boolean(
    snapshot &&
      snapshot.query === key.query &&
      snapshot.genre === key.genre &&
      snapshot.yearMin === key.yearMin &&
      snapshot.yearMax === key.yearMax &&
      snapshot.sort === key.sort &&
      snapshot.direction === key.direction &&
      snapshot.page === key.page &&
      snapshot.size === key.size
  );
}

const YEAR_INPUT_CLASS =
  'min-h-[44px] w-24 rounded-lg border border-[#a6b4a4] bg-paper-white px-2.5 py-2 text-base sm:text-sm text-ink focus:border-ink focus:outline-none';

export function SearchExperience() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { pathname } = useLocation();

  const urlQuery = (searchParams.get('q') || searchParams.get('query') || '').trim();
  const genre = (searchParams.get('genre') || '').trim();
  const yearMin = parseOptionalYear(searchParams.get('yearMin'));
  const yearMax = parseOptionalYear(searchParams.get('yearMax'));
  const sortState = parseSortParams(searchParams.get('sort'), searchParams.get('direction'));
  const page = parseNonNegativeInteger(searchParams.get('page'), 0);
  const requestedSize = parseNonNegativeInteger(searchParams.get('size'), PAGE_SIZE_DEFAULT);
  const pageSize = requestedSize >= 1 && requestedSize <= MAX_PAGE_SIZE ? requestedSize : PAGE_SIZE_DEFAULT;

  const filterKey: SearchFilterKey = {
    query: urlQuery,
    genre,
    yearMin,
    yearMax,
    sort: sortState.sort,
    direction: sortState.direction,
    page,
    size: pageSize,
  };

  const [inputValue, setInputValue] = useState(urlQuery);
  const [yearMinInput, setYearMinInput] = useState(yearMin !== undefined ? String(yearMin) : '');
  const [yearMaxInput, setYearMaxInput] = useState(yearMax !== undefined ? String(yearMax) : '');
  const [serverGenres, setServerGenres] = useState<CatalogGenreOption[] | null>(null);
  const [fallbackGenres, setFallbackGenres] = useState<CatalogGenreOption[]>([]);
  const [snapshot, setSnapshot] = useState<SearchSnapshot | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [retryCount, setRetryCount] = useState(0);
  const lastPushedQueryRef = useRef<string | null>(null);
  const requestIdRef = useRef(0);

  useEffect(() => {
    if (lastPushedQueryRef.current !== null && urlQuery === lastPushedQueryRef.current) {
      lastPushedQueryRef.current = null;
      return;
    }
    setInputValue(urlQuery);
  }, [urlQuery]);

  useEffect(() => setYearMinInput(yearMin !== undefined ? String(yearMin) : ''), [yearMin]);
  useEffect(() => setYearMaxInput(yearMax !== undefined ? String(yearMax) : ''), [yearMax]);

  useEffect(() => {
    if (typeof getCatalogGenres !== 'function') return;
    const controller = new AbortController();
    getCatalogGenres({ signal: controller.signal })
      .then((genres) => {
        if (!controller.signal.aborted && genres.length > 0) {
          setServerGenres(genres.map((genreOption) => ({ ...genreOption, label: resolveGenreLabel(genreOption) })));
        }
      })
      .catch(() => {});
    return () => controller.abort();
  }, []);

  const updateUrl = (updates: {
    q?: string;
    genre?: string;
    yearMin?: number | null;
    yearMax?: number | null;
    sort?: SortField | null;
    direction?: SortDirection | null;
    page?: number;
  }) => {
    const params = new URLSearchParams(searchParams.toString());
    if (updates.q !== undefined) {
      params.delete('query');
      const trimmed = updates.q.trim();
      if (trimmed) params.set('q', trimmed);
      else params.delete('q');
      lastPushedQueryRef.current = trimmed;
    }
    const optionalParams: Array<[string, string | number | null | undefined]> = [
      ['genre', updates.genre !== undefined ? updates.genre.trim() || null : undefined],
      ['yearMin', updates.yearMin],
      ['yearMax', updates.yearMax],
      ['sort', updates.sort],
      ['direction', updates.direction],
      ['page', updates.page !== undefined ? (updates.page > 0 ? updates.page : null) : undefined],
    ];
    for (const [key, val] of optionalParams) {
      if (val === undefined) continue;
      if (val === null || val === '') params.delete(key);
      else params.set(key, String(val));
    }
    const queryString = params.toString();
    navigate(queryString ? `${pathname}?${queryString}` : pathname);
  };

  useEffect(() => {
    if (inputValue.trim() === urlQuery) return;
    const timer = setTimeout(() => updateUrl({ q: inputValue, page: 0 }), QUERY_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [inputValue, urlQuery, searchParams, pathname, navigate]);

  useEffect(() => {
    const requestId = ++requestIdRef.current;
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    browseCatalog({
      query: urlQuery || undefined,
      genre: genre || undefined,
      animationOnly: true,
      yearMin,
      yearMax,
      sort: sortState.sort,
      direction: sortState.direction,
      page,
      size: pageSize,
      signal: controller.signal,
    })
      .then((result) => {
        if (controller.signal.aborted || requestId !== requestIdRef.current) return;
        setSnapshot({
          ...filterKey,
          results: result.items,
          totalElements: result.totalElements,
          totalPages: result.totalPages,
        });
        if (result.items.length > 0) {
          const derived = deriveGenresFromMovies(result.items, genreLabel);
          if (derived.length > 0) {
            setFallbackGenres((prev) => {
              const byKey = new Map<string, CatalogGenreOption>();
              for (const genreOption of [...prev, ...derived]) {
                const key = genreOption.label.toLocaleLowerCase('vi');
                if (!byKey.has(key)) byKey.set(key, genreOption);
              }
              return [...byKey.values()];
            });
          }
        }
        setLoading(false);
      })
      .catch((err: unknown) => {
        const candidate = err as { name?: unknown; isCanceled?: unknown; message?: unknown } | null;
        if (
          controller.signal.aborted ||
          requestId !== requestIdRef.current ||
          candidate?.name === 'AbortError' ||
          candidate?.isCanceled
        ) {
          return;
        }
        setSnapshot({ ...filterKey, results: [], totalElements: 0, totalPages: 0 });
        setError(typeof candidate?.message === 'string' ? candidate.message : 'Chưa tải được kho phim. Bạn thử lại nhé.');
        setLoading(false);
      });

    return () => controller.abort();
  }, [urlQuery, genre, yearMin, yearMax, sortState.sort, sortState.direction, page, pageSize, retryCount]);

  const isCurrentSnapshot = isSnapshotMatching(snapshot, filterKey);
  const currentResults = isCurrentSnapshot ? snapshot.results : [];
  const totalElements = isCurrentSnapshot ? snapshot.totalElements : 0;
  const totalPages = Math.max(1, isCurrentSnapshot ? snapshot.totalPages : 1);
  const safePage = isCurrentSnapshot && snapshot.totalPages > 0 ? Math.min(page, snapshot.totalPages - 1) : page;

  useEffect(() => {
    if (isCurrentSnapshot && snapshot.totalPages > 0 && page !== safePage) {
      updateUrl({ page: safePage });
    }
  }, [isCurrentSnapshot, page, safePage, searchParams, pathname, navigate]);

  const availableGenres = useMemo(() => {
    const source = serverGenres && serverGenres.length > 0 ? serverGenres : fallbackGenres;
    return source.filter((genreOption) => genreOption.count > 0);
  }, [serverGenres, fallbackGenres]);

  const hasActiveFilters = Boolean(urlQuery || genre || yearMin !== undefined || yearMax !== undefined || sortState.value);

  const clearAllFilters = () => {
    setInputValue('');
    setYearMinInput('');
    setYearMaxInput('');
    updateUrl({ q: '', genre: '', yearMin: null, yearMax: null, sort: null, direction: null, page: 0 });
  };

  const handleToggleGenre = (genreId: string) => {
    const isSame = genre.toLocaleLowerCase('vi') === genreId.toLocaleLowerCase('vi');
    updateUrl({ genre: isSame ? '' : genreId, page: 0 });
  };

  const handleYearChange = (field: 'yearMin' | 'yearMax', raw: string) => {
    const current = field === 'yearMin' ? yearMin : yearMax;
    if (field === 'yearMin') setYearMinInput(raw);
    else setYearMaxInput(raw);
    const trimmed = raw.trim();
    if (!trimmed) {
      if (current !== undefined) updateUrl({ [field]: null, page: 0 });
      return;
    }
    const parsed = parseOptionalYear(trimmed);
    if (parsed !== undefined && parsed !== current) {
      updateUrl({ [field]: parsed, page: 0 });
    }
  };

  const handleSortChange = (nextValue: SortOptionValue) => {
    if (!nextValue) {
      updateUrl({ sort: null, direction: null, page: 0 });
      return;
    }
    const [nextSort, nextDirection] = nextValue.split(':') as [SortField, SortDirection];
    updateUrl({ sort: nextSort, direction: nextDirection, page: 0 });
  };

  const activeGenreDisplay = useMemo(() => {
    if (!genre) return '';
    const matched = availableGenres.find((item) => item.id.toLocaleLowerCase('vi') === genre.toLocaleLowerCase('vi'));
    return matched?.label || genreLabel(genre);
  }, [genre, availableGenres]);

  return (
    <main id="main-content" tabIndex={-1} className="page-shell min-h-[75vh] py-6 sm:py-8">
      <header className="catalog-cover">
        <h1 className="comic-heading text-3xl font-bold sm:text-4xl">Kho phim</h1>
        <div className="catalog-cover-art" aria-hidden="true" />
      </header>

      <form
        className="catalog-search"
        onSubmit={(event) => {
          event.preventDefault();
          updateUrl({ q: inputValue, page: 0 });
        }}
      >
        <input
          data-testid="search-query-input"
          aria-label="Tìm phim hoạt hình"
          type="text"
          enterKeyHint="search"
          value={inputValue}
          onChange={(event) => setInputValue(event.target.value)}
          placeholder="Tìm theo tên phim, tập hoặc nội dung…"
          className="min-h-12 min-w-0 flex-1 bg-transparent px-2 text-base text-ink placeholder:text-muted"
        />
        {inputValue && (
          <button
            type="button"
            onClick={() => {
              setInputValue('');
              updateUrl({ q: '', page: 0 });
            }}
            className="min-h-[44px] min-w-[44px] cursor-pointer rounded-md px-2 text-sm text-muted hover:bg-surface-elevated hover:text-ink"
            aria-label="Xóa từ khóa"
          >
            Xóa
          </button>
        )}
      </form>

      <section aria-label="Bộ lọc tìm kiếm" className="mt-4 space-y-3">
        {availableGenres.length > 0 && (
          <div
            role="group"
            aria-label="Lọc theo thể loại"
            data-testid="search-genre-chips"
            className="flex flex-wrap items-center gap-2"
          >
            <button
              type="button"
              data-testid="search-genre-all"
              aria-pressed={!genre}
              onClick={() => updateUrl({ genre: '', page: 0 })}
              className={`series-filter-chip min-h-[44px] ${!genre ? 'series-filter-chip--active' : ''}`}
            >
              Tất cả
            </button>
            {availableGenres.map((item) => {
              const active = genre.toLocaleLowerCase('vi') === item.id.toLocaleLowerCase('vi');
              return (
                <button
                  key={item.id}
                  type="button"
                  data-testid="search-genre-chip"
                  data-genre-id={item.id}
                  aria-pressed={active}
                  onClick={() => handleToggleGenre(item.id)}
                  className={`series-filter-chip min-h-[44px] ${active ? 'series-filter-chip--active' : ''}`}
                >
                  {item.label}
                </button>
              );
            })}
          </div>
        )}

        <div className="flex flex-wrap items-end gap-3">
          <div className="flex items-center gap-2">
            <label htmlFor="search-year-min" className="text-xs font-semibold text-ink whitespace-nowrap">
              Từ năm
            </label>
            <input
              id="search-year-min"
              data-testid="search-year-min"
              type="number"
              inputMode="numeric"
              min={MIN_FILTER_YEAR}
              max={MAX_FILTER_YEAR}
              placeholder="2000"
              value={yearMinInput}
              onChange={(event) => handleYearChange('yearMin', event.target.value)}
              className={YEAR_INPUT_CLASS}
            />
          </div>

          <div className="flex items-center gap-2">
            <label htmlFor="search-year-max" className="text-xs font-semibold text-ink whitespace-nowrap">
              Đến năm
            </label>
            <input
              id="search-year-max"
              data-testid="search-year-max"
              type="number"
              inputMode="numeric"
              min={MIN_FILTER_YEAR}
              max={MAX_FILTER_YEAR}
              placeholder="2026"
              value={yearMaxInput}
              onChange={(event) => handleYearChange('yearMax', event.target.value)}
              className={YEAR_INPUT_CLASS}
            />
          </div>

          <div className="flex items-center gap-2">
            <label htmlFor="search-sort-select" className="text-xs font-semibold text-ink whitespace-nowrap">
              Sắp xếp
            </label>
            <select
              id="search-sort-select"
              data-testid="search-sort-select"
              value={sortState.value}
              onChange={(event) => handleSortChange(event.target.value as SortOptionValue)}
              className="min-h-[44px] rounded-lg border border-[#a6b4a4] bg-paper-white px-3 py-2 text-base sm:text-sm font-medium text-ink focus:border-ink focus:outline-none cursor-pointer"
            >
              {SORT_OPTIONS.map((option) => (
                <option key={option.value || 'default'} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>

          {hasActiveFilters && (
            <button
              type="button"
              data-testid="search-clear-filters"
              onClick={clearAllFilters}
              className="min-h-[44px] rounded-lg border border-surface-border bg-surface px-3.5 text-xs font-semibold text-ink hover:bg-surface-hover cursor-pointer"
            >
              Xóa bộ lọc
            </button>
          )}
        </div>
      </section>

      <div className="catalog-status" role="status">
        <div className="flex min-w-0 flex-wrap items-center gap-x-3 gap-y-1 break-words">
          {urlQuery && <span className="min-w-0">Kết quả cho "{urlQuery}"</span>}
          {genre && <span>Thể loại {activeGenreDisplay}</span>}
          {(yearMin !== undefined || yearMax !== undefined) && (
            <span>
              {yearMin !== undefined && yearMax !== undefined
                ? `Năm ${yearMin} đến ${yearMax}`
                : yearMin !== undefined
                ? `Từ năm ${yearMin}`
                : `Đến năm ${yearMax}`}
            </span>
          )}
        </div>
        {loading && snapshot ? <span>Đang cập nhật…</span> : isCurrentSnapshot && <span>{totalElements} phim</span>}
      </div>

      <section aria-label="Kết quả tìm phim">
        {error && isCurrentSnapshot ? (
          <div role="alert" className="flex flex-col items-center rounded-xl border border-red-500/30 bg-red-950/10 px-6 py-12 text-center">
            <h2 className="mt-3 font-bold text-ink">Chưa tải được kho phim</h2>
            <p className="mt-1 max-w-md text-sm text-muted">{error}</p>
            <button
              type="button"
              onClick={() => setRetryCount((value) => value + 1)}
              className="btn-primary mt-5 flex items-center gap-2 text-xs"
            >
              Thử lại
            </button>
          </div>
        ) : !isCurrentSnapshot ? (
          <div data-testid="catalog-skeleton" role="status" aria-label="Đang tải kho phim" className="film-grid">
            <span className="sr-only">Đang tải kho phim…</span>
            {Array.from({ length: 6 }).map((_, index) => (
              <div key={index} className="series-skeleton-card overflow-hidden animate-pulse">
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
        ) : currentResults.length === 0 ? (
          <div className="rounded-xl border border-dashed border-surface-border bg-surface px-6 py-16 text-center">
            <h2 className="mt-2 font-bold text-ink text-base">Không tìm thấy phim phù hợp</h2>
            <p className="mt-1 text-sm text-muted max-w-md mx-auto">
              {urlQuery
                ? `Không có phim nào khớp với "${urlQuery}".`
                : genre
                ? `Chưa có phim thuộc thể loại ${activeGenreDisplay}.`
                : 'Kho hiện chưa có phim nào.'}
            </p>
            {hasActiveFilters && (
              <button type="button" onClick={clearAllFilters} className="btn-secondary mt-5 text-xs">
                Xóa bộ lọc
              </button>
            )}
          </div>
        ) : (
          <>
            <FilmGrid movies={currentResults} search loading={loading} />
            {totalPages > 1 && (
              <nav
                aria-label="Phân trang kho phim"
                className="mt-8 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-surface-border bg-surface px-5 py-3 text-xs text-muted"
              >
                <span>
                  Trang {safePage + 1} / {totalPages}
                </span>
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={() => updateUrl({ page: Math.max(0, safePage - 1) })}
                    disabled={safePage === 0 || loading}
                    className="btn-secondary flex items-center gap-1 text-xs py-1.5 px-3 disabled:opacity-40"
                  >
                    Trước
                  </button>
                  <button
                    type="button"
                    onClick={() => updateUrl({ page: Math.min(totalPages - 1, safePage + 1) })}
                    disabled={safePage >= totalPages - 1 || loading}
                    className="btn-secondary flex items-center gap-1 text-xs py-1.5 px-3 disabled:opacity-40"
                  >
                    Sau
                  </button>
                </div>
              </nav>
            )}
          </>
        )}
      </section>
    </main>
  );
}
