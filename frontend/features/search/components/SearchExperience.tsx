import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router';
import { SiteDialog } from '@/components/ui/SiteDialog';
import { genreLabel, resolveGenreLabel } from '@/lib/labels';
import * as motion from '@/lib/motion';
import { useWatchlist } from '@/lib/context/WatchlistContext';
import {
  browseCatalog,
  deriveGenresFromMovies,
  getCatalogGenres,
  type CatalogGenreOption,
} from '@/lib/services/catalogService';
import { listSeriesCards, type SeriesCard } from '@/lib/services/seriesCardService';
import { FilmGrid } from '@/features/movies/components/FilmShelf';
import type { Movie } from '@/types/movie';

type SortField = 'rating' | 'year' | 'title';
type SortDirection = 'asc' | 'desc';
type SortOptionValue = '' | 'year:desc' | 'year:asc' | 'rating:desc' | 'title:asc';
type ContentTypeFilter = 'all' | 'series' | 'episode';

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
  contentType: ContentTypeFilter;
  shortOnly: boolean;
  page: number;
  size: number;
};

type SearchSnapshot = SearchFilterKey & {
  results: Movie[];
  totalElements: number;
  totalPages: number;
};

function foldSearchText(raw: string): string {
  return (raw || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[đĐ]/g, 'd')
    .toLowerCase()
    .trim();
}

function parseMovieRuntimeMinutes(movie: Movie): number | null {
  const startMs = movie.start_ms ?? movie.startMs;
  const endMs = movie.end_ms ?? movie.endMs;
  if (typeof startMs === 'number' && typeof endMs === 'number' && endMs > startMs) {
    return Math.max(1, Math.round((endMs - startMs) / 60000));
  }
  const raw = typeof movie.runtime === 'string' ? movie.runtime.trim() : '';
  if (!raw) return null;
  const hourMatch = raw.match(/(\d+)\s*(?:giờ|gio|g|h)\s*(\d+)?/i);
  if (hourMatch) {
    return Number(hourMatch[1]) * 60 + (hourMatch[2] ? Number(hourMatch[2]) : 0);
  }
  const minMatch = raw.match(/(\d+)/);
  return minMatch ? Number(minMatch[1]) : null;
}

function isShortDurationMovie(movie: Movie): boolean {
  if (movie.item_type === 'series' || movie.type === 'series') return false;
  const mins = parseMovieRuntimeMinutes(movie);
  return mins !== null && mins > 0 && mins <= 10;
}

function resolveSeriesCardYear(card: SeriesCard): number | null {
  if (typeof card.releaseYearStart === 'number') return card.releaseYearStart;
  if (typeof card.releaseYearEnd === 'number') return card.releaseYearEnd;
  return null;
}

function extractSeriesGenreTokens(g: SeriesCard['genres'][number] | string): { id: string; label: string } {
  if (typeof g === 'string') {
    return { id: g, label: genreLabel(g) };
  }
  return { id: g.id || '', label: g.label || genreLabel(g.id || '') };
}

function seriesCardToMovie(card: SeriesCard): Movie {
  const art = card.backdropUrl || card.posterUrl || '';
  return {
    id: card.id,
    item_type: 'series',
    type: 'series',
    series_id: card.id,
    seriesId: card.id,
    title: card.title,
    year: resolveSeriesCardYear(card),
    rating: typeof card.averageRating === 'number' ? card.averageRating : 0,
    averageRating: card.averageRating,
    ratingCount: card.ratingCount,
    runtime: '',
    genres: Array.isArray(card.genres)
      ? card.genres.map((g) => extractSeriesGenreTokens(g).label).filter(Boolean)
      : [],
    description: card.synopsis || '',
    poster: card.posterUrl || art,
    backdrop: card.backdropUrl || art,
    thumbnailUrl: art || null,
  };
}

function filterAndSortSeriesCards(cards: SeriesCard[], key: SearchFilterKey): Movie[] {
  const foldedQuery = key.query ? foldSearchText(key.query) : '';
  const foldedGenre = key.genre ? foldSearchText(key.genre) : '';
  const foldedTranslatedGenre = key.genre ? foldSearchText(genreLabel(key.genre)) : '';

  const filtered = cards.filter((card) => {
    if (foldedQuery) {
      const haystack = foldSearchText(`${card.title} ${card.originalTitle || ''} ${card.synopsis || ''}`);
      if (!haystack.includes(foldedQuery)) return false;
    }
    if (foldedGenre) {
      const cardGenres = Array.isArray(card.genres) ? card.genres : [];
      const matchesGenre = cardGenres.some((g) => {
        const tokens = extractSeriesGenreTokens(g);
        const fg = foldSearchText(tokens.id);
        const fl = foldSearchText(tokens.label);
        return fg === foldedGenre || fl === foldedGenre || Boolean(foldedTranslatedGenre && fl === foldedTranslatedGenre);
      });
      if (!matchesGenre) return false;
    }
    const cardYear = resolveSeriesCardYear(card);
    if (key.yearMin !== undefined) {
      if (cardYear === null || cardYear < key.yearMin) return false;
    }
    if (key.yearMax !== undefined) {
      if (cardYear === null || cardYear > key.yearMax) return false;
    }
    return true;
  });

  const sorted = filtered.slice();
  if (key.sort === 'year') {
    sorted.sort((a, b) => {
      const ay = resolveSeriesCardYear(a) ?? 0;
      const by = resolveSeriesCardYear(b) ?? 0;
      return key.direction === 'asc' ? ay - by : by - ay;
    });
  } else if (key.sort === 'rating') {
    sorted.sort((a, b) => (b.averageRating ?? -1) - (a.averageRating ?? -1));
  } else if (key.sort === 'title') {
    sorted.sort((a, b) => a.title.localeCompare(b.title, 'vi'));
  }
  return sorted.map(seriesCardToMovie);
}

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
      snapshot.contentType === key.contentType &&
      snapshot.shortOnly === key.shortOnly &&
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
  const watchlist = typeof useWatchlist === 'function' ? useWatchlist() : null;

  const urlQuery = (searchParams.get('q') || searchParams.get('query') || '').trim();
  const genre = (searchParams.get('genre') || '').trim();
  const yearMin = parseOptionalYear(searchParams.get('yearMin'));
  const yearMax = parseOptionalYear(searchParams.get('yearMax'));
  const sortState = parseSortParams(searchParams.get('sort'), searchParams.get('direction'));
  const page = parseNonNegativeInteger(searchParams.get('page'), 0);
  const requestedSize = parseNonNegativeInteger(searchParams.get('size'), PAGE_SIZE_DEFAULT);
  const pageSize = requestedSize >= 1 && requestedSize <= MAX_PAGE_SIZE ? requestedSize : PAGE_SIZE_DEFAULT;

  const [inputValue, setInputValue] = useState(urlQuery);
  const [yearMinInput, setYearMinInput] = useState(yearMin !== undefined ? String(yearMin) : '');
  const [yearMaxInput, setYearMaxInput] = useState(yearMax !== undefined ? String(yearMax) : '');
  const [contentTypeFilter, setContentTypeFilter] = useState<ContentTypeFilter>('all');
  const [shortDurationOnly, setShortDurationOnly] = useState(false);
  const [filterSheetOpen, setFilterSheetOpen] = useState(false);

  const filterKey: SearchFilterKey = {
    query: urlQuery,
    genre,
    yearMin,
    yearMax,
    sort: sortState.sort,
    direction: sortState.direction,
    contentType: contentTypeFilter,
    shortOnly: shortDurationOnly,
    page,
    size: pageSize,
  };
  const [serverGenres, setServerGenres] = useState<CatalogGenreOption[] | null>(null);
  const [fallbackGenres, setFallbackGenres] = useState<CatalogGenreOption[]>([]);
  const [snapshot, setSnapshot] = useState<SearchSnapshot | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [retryCount, setRetryCount] = useState(0);
  const lastPushedQueryRef = useRef<string | null>(null);
  const requestIdRef = useRef(0);
  const pageRef = useRef<HTMLElement>(null);

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

    const recordDerivedGenres = (items: Movie[]) => {
      if (items.length === 0) return;
      const derived = deriveGenresFromMovies(items, genreLabel);
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
    };

    const handleFailure = (err: unknown) => {
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
    };

    if (contentTypeFilter === 'series' && !shortDurationOnly) {
      const loadSeriesList = async () => {
        if (typeof listSeriesCards === 'function') {
          const cards = await listSeriesCards({ signal: controller.signal });
          return filterAndSortSeriesCards(cards, filterKey);
        }
        const fallback = await browseCatalog({
          query: urlQuery || undefined,
          genre: genre || undefined,
          animationOnly: true,
          yearMin,
          yearMax,
          sort: sortState.sort,
          direction: sortState.direction,
          page: 0,
          size: MAX_PAGE_SIZE,
          signal: controller.signal,
        });
        return fallback.items.filter((item) => item.item_type === 'series');
      };

      loadSeriesList()
        .then((allSeries) => {
          if (controller.signal.aborted || requestId !== requestIdRef.current) return;
          const total = allSeries.length;
          const pages = total > 0 ? Math.ceil(total / pageSize) : 0;
          const targetPage = pages > 0 ? Math.min(page, pages - 1) : 0;
          const sliced = allSeries.slice(targetPage * pageSize, (targetPage + 1) * pageSize);
          setSnapshot({
            ...filterKey,
            results: sliced,
            totalElements: total,
            totalPages: pages,
          });
          recordDerivedGenres(allSeries);
          setLoading(false);
        })
        .catch(handleFailure);

      return () => controller.abort();
    }

    if (contentTypeFilter === 'episode' || shortDurationOnly) {
      const loadFilteredEpisodes = async () => {
        const batchSize = 100;
        const firstPage = await browseCatalog({
          query: urlQuery || undefined,
          genre: genre || undefined,
          animationOnly: true,
          yearMin,
          yearMax,
          sort: sortState.sort,
          direction: sortState.direction,
          page: 0,
          size: batchSize,
          signal: controller.signal,
        });
        let combined = [...firstPage.items];
        if (firstPage.totalPages > 1 && firstPage.totalPages <= 12) {
          const restPages = await Promise.all(
            Array.from({ length: firstPage.totalPages - 1 }, (_, idx) =>
              browseCatalog({
                query: urlQuery || undefined,
                genre: genre || undefined,
                animationOnly: true,
                yearMin,
                yearMax,
                sort: sortState.sort,
                direction: sortState.direction,
                page: idx + 1,
                size: batchSize,
                signal: controller.signal,
              })
            )
          );
          for (const extra of restPages) {
            combined = combined.concat(extra.items);
          }
        }
        return combined.filter((item) => {
          if (item.item_type === 'series' || item.type === 'series') return false;
          if (shortDurationOnly && !isShortDurationMovie(item)) return false;
          return true;
        });
      };

      loadFilteredEpisodes()
        .then((filteredItems) => {
          if (controller.signal.aborted || requestId !== requestIdRef.current) return;
          const total = filteredItems.length;
          const pages = total > 0 ? Math.ceil(total / pageSize) : 0;
          const targetPage = pages > 0 ? Math.min(page, pages - 1) : 0;
          const sliced = filteredItems.slice(targetPage * pageSize, (targetPage + 1) * pageSize);
          setSnapshot({
            ...filterKey,
            results: sliced,
            totalElements: total,
            totalPages: pages,
          });
          recordDerivedGenres(filteredItems);
          setLoading(false);
        })
        .catch(handleFailure);

      return () => controller.abort();
    }

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
        recordDerivedGenres(result.items);
        setLoading(false);
      })
      .catch(handleFailure);

    return () => controller.abort();
  }, [
    urlQuery,
    genre,
    yearMin,
    yearMax,
    sortState.sort,
    sortState.direction,
    contentTypeFilter,
    shortDurationOnly,
    page,
    pageSize,
    retryCount,
  ]);

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
    return source.filter((genreOption) => {
      if (genreOption.count <= 0) return false;
      const lowerId = genreOption.id.toLocaleLowerCase('vi');
      const isActive = genre.toLocaleLowerCase('vi') === lowerId;
      if ((lowerId === 'animation' || lowerId === 'hoạt hình') && !isActive) return false;
      return true;
    });
  }, [serverGenres, fallbackGenres, genre]);

  const sheetFilterCount =
    (genre ? 1 : 0) +
    (yearMin !== undefined || yearMax !== undefined ? 1 : 0) +
    (sortState.value ? 1 : 0);

  const hasNonQueryFilters =
    sheetFilterCount > 0 || contentTypeFilter !== 'all' || shortDurationOnly;

  const clearNonQueryFilters = () => {
    setYearMinInput('');
    setYearMaxInput('');
    setContentTypeFilter('all');
    setShortDurationOnly(false);
    updateUrl({ genre: '', yearMin: null, yearMax: null, sort: null, direction: null, page: 0 });
  };

  const handleSelectContentType = (nextType: ContentTypeFilter) => {
    setContentTypeFilter(nextType);
    if (nextType === 'series' && shortDurationOnly) {
      setShortDurationOnly(false);
    }
    if (page !== 0) {
      updateUrl({ page: 0 });
    }
  };

  const handleToggleShortDuration = () => {
    setShortDurationOnly((prev) => {
      const next = !prev;
      if (next && contentTypeFilter === 'series') {
        setContentTypeFilter('all');
      }
      return next;
    });
    if (page !== 0) {
      updateUrl({ page: 0 });
    }
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

  const displayedResults = currentResults;

  const seriesItems = useMemo(
    () => displayedResults.filter((item) => item.item_type === 'series'),
    [displayedResults]
  );
  const nonSeriesItems = useMemo(
    () => displayedResults.filter((item) => item.item_type !== 'series'),
    [displayedResults]
  );
  const shouldSeparateGroups =
    contentTypeFilter === 'all' && seriesItems.length > 0 && nonSeriesItems.length > 0;

  motion.useScrollReveal?.(pageRef, [displayedResults.length, safePage, genre, sortState.value]);

  const effectiveQueryText = urlQuery || inputValue.trim();

  return (
    <main ref={pageRef} id="main-content" tabIndex={-1} className="page-shell min-h-[75vh] py-4 sm:py-8">
      <header data-reveal-section="" className="catalog-cover">
        <h1 className="comic-heading text-2xl font-bold sm:text-4xl">Kho phim</h1>
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
          id="search-query-input"
          name="q"
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

      <section aria-label="Bộ lọc tìm kiếm" className="mt-3 space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div
            role="group"
            aria-label="Loại nội dung"
            data-testid="search-type-filter"
            className="flex flex-wrap items-center gap-1.5"
          >
            {(
              [
                ['all', 'Tất cả'],
                ['series', 'Phim bộ'],
                ['episode', 'Tập phim'],
              ] as const
            ).map(([key, label]) => {
              const active = contentTypeFilter === key;
              return (
                <button
                  key={key}
                  type="button"
                  data-testid={`search-type-${key}`}
                  aria-pressed={active}
                  onClick={() => handleSelectContentType(key)}
                  className={`series-filter-chip min-h-[44px] ${active ? 'series-filter-chip--active' : ''}`}
                >
                  {label}
                </button>
              );
            })}
            <button
              type="button"
              data-testid="search-short-duration-toggle"
              aria-pressed={shortDurationOnly}
              onClick={handleToggleShortDuration}
              className={`series-filter-chip min-h-[44px] ${shortDurationOnly ? 'series-filter-chip--active' : ''}`}
            >
              Dưới 10 phút
            </button>
          </div>

          <div className="flex items-center gap-2 md:hidden">
            <button
              type="button"
              data-testid="search-filter-sheet-toggle"
              onClick={() => setFilterSheetOpen(true)}
              className="btn-secondary min-h-[44px] px-3.5 text-xs font-bold"
            >
              Bộ lọc{sheetFilterCount > 0 ? ` (${sheetFilterCount})` : ''}
            </button>
          </div>
        </div>

        <div className="hidden md:block space-y-3">
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
                style={{ '--stagger-idx': 0 } as React.CSSProperties}
                className={`series-filter-chip motion-chip-pop min-h-[44px] ${!genre ? 'series-filter-chip--active' : ''}`}
              >
                Tất cả thể loại
              </button>
              {availableGenres.map((item, index) => {
                const active = genre.toLocaleLowerCase('vi') === item.id.toLocaleLowerCase('vi');
                return (
                  <button
                    key={item.id}
                    type="button"
                    data-testid="search-genre-chip"
                    data-genre-id={item.id}
                    aria-pressed={active}
                    onClick={() => handleToggleGenre(item.id)}
                    style={{ '--stagger-idx': Math.min(index + 1, 10) } as React.CSSProperties}
                    className={`series-filter-chip motion-chip-pop min-h-[44px] ${active ? 'series-filter-chip--active' : ''}`}
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

            {hasNonQueryFilters && (
              <button
                type="button"
                data-testid="search-clear-filters"
                onClick={clearNonQueryFilters}
                className="min-h-[44px] rounded-lg border border-surface-border bg-surface px-3.5 text-xs font-semibold text-ink hover:bg-surface-hover cursor-pointer"
              >
                Xóa bộ lọc
              </button>
            )}
          </div>
        </div>

        {hasNonQueryFilters && (
          <div className="flex flex-wrap items-center gap-2 md:hidden">
            <button
              type="button"
              onClick={clearNonQueryFilters}
              className="min-h-[44px] rounded-lg border border-surface-border bg-surface px-3 text-xs font-semibold text-ink hover:bg-surface-hover cursor-pointer"
            >
              Xóa bộ lọc (giữ từ khóa)
            </button>
          </div>
        )}
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
        {loading && snapshot ? <span>Đang cập nhật…</span> : isCurrentSnapshot && <span>{totalElements} kết quả</span>}
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
        ) : displayedResults.length === 0 ? (
          <div className="rounded-xl border border-dashed border-surface-border bg-surface px-6 py-14 text-center">
            <h2 className="mt-2 font-bold text-ink text-base">Không tìm thấy phim phù hợp</h2>
            <p className="mt-1 text-sm text-muted max-w-md mx-auto">
              {urlQuery
                ? `Không có phim nào khớp với "${urlQuery}".`
                : genre
                ? `Chưa có phim thuộc thể loại ${activeGenreDisplay}.`
                : 'Kho hiện chưa có phim nào.'}
            </p>
            <div className="mt-5 flex flex-wrap items-center justify-center gap-3">
              {effectiveQueryText && (
                <button
                  type="button"
                  data-testid="search-ask-cinebot-button"
                  onClick={() => watchlist?.openChat?.(effectiveQueryText)}
                  className="btn-primary min-h-[44px] text-xs sm:text-sm font-bold"
                >
                  Hỏi CineBot về câu này
                </button>
              )}
              {hasNonQueryFilters && (
                <button
                  type="button"
                  data-testid="search-empty-clear-filters"
                  onClick={clearNonQueryFilters}
                  className="btn-secondary min-h-[44px] text-xs"
                >
                  Xóa bộ lọc
                </button>
              )}
            </div>
          </div>
        ) : (
          <>
            {shouldSeparateGroups ? (
              <div data-testid="catalog-results" className="space-y-8">
                <div data-testid="search-series-section" className="space-y-3">
                  <div className="flex items-center justify-between border-b border-surface-border pb-2">
                    <h2 className="font-display text-xl font-bold text-ink">Phim bộ ({seriesItems.length})</h2>
                  </div>
                  <FilmGrid movies={seriesItems} search loading={loading} includeTestId={false} headingLevel="h3" />
                </div>

                <div data-testid="search-episodes-section" className="space-y-3">
                  <div className="flex items-center justify-between border-b border-surface-border pb-2">
                    <h2 className="font-display text-xl font-bold text-ink">Tập phim &amp; Phim lẻ ({nonSeriesItems.length})</h2>
                  </div>
                  <FilmGrid movies={nonSeriesItems} search loading={loading} includeTestId={false} headingLevel="h3" />
                </div>
              </div>
            ) : (
              <FilmGrid movies={displayedResults} search loading={loading} />
            )}

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

      {filterSheetOpen && (
        <SiteDialog
          title="Bộ lọc kho phim"
          size="lg"
          onClose={() => setFilterSheetOpen(false)}
          footer={
            <div className="flex items-center justify-between gap-2">
              <button
                type="button"
                disabled={!hasNonQueryFilters}
                onClick={clearNonQueryFilters}
                className="btn-secondary min-h-[44px] text-xs disabled:opacity-40"
              >
                Xóa bộ lọc
              </button>
              <button
                type="button"
                onClick={() => setFilterSheetOpen(false)}
                className="btn-primary min-h-[44px] px-5 text-xs font-bold"
              >
                Xem kết quả
              </button>
            </div>
          }
        >
          <div data-testid="search-filter-sheet" className="space-y-5">
            {availableGenres.length > 0 && (
              <fieldset className="space-y-2">
                <legend className="text-sm font-bold text-ink">Thể loại</legend>
                <div className="flex flex-wrap gap-2">
                  <button
                    type="button"
                    data-autofocus="true"
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
                        aria-pressed={active}
                        onClick={() => handleToggleGenre(item.id)}
                        className={`series-filter-chip min-h-[44px] ${active ? 'series-filter-chip--active' : ''}`}
                      >
                        {item.label}
                      </button>
                    );
                  })}
                </div>
              </fieldset>
            )}

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label htmlFor="sheet-year-min" className="mb-1.5 block text-xs font-semibold text-ink">
                  Từ năm
                </label>
                <input
                  id="sheet-year-min"
                  type="number"
                  inputMode="numeric"
                  min={MIN_FILTER_YEAR}
                  max={MAX_FILTER_YEAR}
                  placeholder="2000"
                  value={yearMinInput}
                  onChange={(event) => handleYearChange('yearMin', event.target.value)}
                  className="min-h-[44px] w-full rounded-lg border border-[#a6b4a4] bg-paper-white px-3 py-2 text-base text-ink focus:border-ink focus:outline-none"
                />
              </div>
              <div>
                <label htmlFor="sheet-year-max" className="mb-1.5 block text-xs font-semibold text-ink">
                  Đến năm
                </label>
                <input
                  id="sheet-year-max"
                  type="number"
                  inputMode="numeric"
                  min={MIN_FILTER_YEAR}
                  max={MAX_FILTER_YEAR}
                  placeholder="2026"
                  value={yearMaxInput}
                  onChange={(event) => handleYearChange('yearMax', event.target.value)}
                  className="min-h-[44px] w-full rounded-lg border border-[#a6b4a4] bg-paper-white px-3 py-2 text-base text-ink focus:border-ink focus:outline-none"
                />
              </div>
            </div>

            <div>
              <label htmlFor="sheet-sort-select" className="mb-1.5 block text-xs font-semibold text-ink">
                Sắp xếp theo
              </label>
              <select
                id="sheet-sort-select"
                value={sortState.value}
                onChange={(event) => handleSortChange(event.target.value as SortOptionValue)}
                className="min-h-[44px] w-full rounded-lg border border-[#a6b4a4] bg-paper-white px-3 py-2 text-base font-medium text-ink focus:border-ink focus:outline-none"
              >
                {SORT_OPTIONS.map((option) => (
                  <option key={option.value || 'default'} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </SiteDialog>
      )}
    </main>
  );
}
