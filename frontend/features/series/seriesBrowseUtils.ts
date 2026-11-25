import { resolveGenreLabel } from '@/lib/labels';
import type { SeriesCard, SeriesCardGenre } from '@/lib/services/seriesCardService';

export type SeriesSortKey = 'title' | 'newest' | 'episodes';

export type SeriesBrowseQueryState = {
  q: string;
  genres: string[];
  sort: SeriesSortKey;
};

export const SERIES_SORT_OPTIONS: { value: SeriesSortKey; label: string }[] = [
  { value: 'title', label: 'Tên từ A đến Z' },
  { value: 'newest', label: 'Mới nhất theo năm' },
  { value: 'episodes', label: 'Nhiều tập nhất' },
];

const VALID_SORTS = new Set<SeriesSortKey>(['title', 'newest', 'episodes']);

function cleanUrl(value?: string | null): string | null {
  return typeof value === 'string' && value.trim().length > 0 ? value.trim() : null;
}

export function foldVietnamese(text: string): string {
  return text
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[đĐ]/g, 'd')
    .toLocaleLowerCase('vi')
    .replace(/\s+/g, ' ')
    .trim();
}

function validPositiveYear(value?: number | null): number | null {
  return typeof value === 'number' && Number.isFinite(value) && value > 0 ? Math.trunc(value) : null;
}

export function pickSeriesArtwork(
  card: { backdropUrl?: string | null; posterUrl?: string | null },
  prefer: 'backdrop' | 'poster' = 'backdrop'
): { primary: string | null; fallback: string | null } {
  const backdrop = cleanUrl(card.backdropUrl);
  const poster = cleanUrl(card.posterUrl);
  const [first, second] = prefer === 'poster' ? [poster, backdrop] : [backdrop, poster];
  const primary = first || second;
  const fallback = first && second && first !== second ? second : null;
  return { primary, fallback };
}

export function pickMovieArtwork(item: {
  thumbnailUrl?: string | null;
  backdrop?: string | null;
  poster?: string | null;
}): { primary: string | null; fallback: string | null } {
  const unique = [
    ...new Set(
      [cleanUrl(item.thumbnailUrl), cleanUrl(item.backdrop), cleanUrl(item.poster)].filter(
        (url): url is string => Boolean(url)
      )
    ),
  ];
  return { primary: unique[0] || null, fallback: unique[1] || null };
}

export function getTitleInitials(title?: string | null): string {
  const clean = typeof title === 'string' ? title.trim() : '';
  if (!clean) return '?';
  const words = clean.split(/[\s\-_:|/.,!?()[\]"]+/).filter(Boolean);
  if (words.length === 0) return clean.slice(0, 2).toLocaleUpperCase('vi');
  if (words.length === 1) return Array.from(words[0]).slice(0, 2).join('').toLocaleUpperCase('vi');
  return `${Array.from(words[0])[0] || ''}${Array.from(words[words.length - 1])[0] || ''}`.toLocaleUpperCase('vi');
}

export function getFallbackToneIndex(seed?: string | null, paletteSize = 6): number {
  const clean = typeof seed === 'string' && seed.trim() ? seed.trim() : 'dippie';
  let hash = 0;
  for (let i = 0; i < clean.length; i++) {
    hash = (hash * 31 + clean.charCodeAt(i)) >>> 0;
  }
  return hash % Math.max(1, paletteSize);
}

export function formatYearRange(start?: number | null, end?: number | null): string {
  const validStart = validPositiveYear(start);
  const validEnd = validPositiveYear(end);
  if (validStart !== null && validEnd !== null) {
    return validStart === validEnd ? String(validStart) : `${validStart} đến ${validEnd}`;
  }
  return String(validStart ?? validEnd ?? '');
}

export function formatSeriesCounts(seasonCount?: number | null, episodeCount?: number | null): string {
  const parts: string[] = [];
  if (typeof seasonCount === 'number' && Number.isFinite(seasonCount) && seasonCount > 0) {
    parts.push(`${Math.floor(seasonCount)} mùa`);
  }
  if (typeof episodeCount === 'number' && Number.isFinite(episodeCount) && episodeCount > 0) {
    parts.push(`${Math.floor(episodeCount)} tập`);
  }
  return parts.join(' · ');
}

export function formatEpisodeTag(episodeNumber?: number | null): string {
  return typeof episodeNumber === 'number' && Number.isFinite(episodeNumber) && episodeNumber > 0
    ? `Tập ${Math.floor(episodeNumber)}`
    : '';
}

export function computeProgressPercent(positionMs?: number | null, durationMs?: number | null): number | null {
  if (
    typeof positionMs !== 'number' ||
    !Number.isFinite(positionMs) ||
    positionMs < 0 ||
    typeof durationMs !== 'number' ||
    !Number.isFinite(durationMs) ||
    durationMs <= 0
  ) {
    return null;
  }
  return Math.round(Math.min(1, Math.max(0, positionMs / durationMs)) * 100);
}

export function extractSeriesGenres(cards: SeriesCard[]): SeriesCardGenre[] {
  const byLabel = new Map<string, SeriesCardGenre>();
  for (const card of cards) {
    for (const genre of card.genres || []) {
      const id = genre.id.trim();
      if (!id) continue;
      const label = resolveGenreLabel(genre) || id;
      const key = label.toLocaleLowerCase('vi');
      if (!byLabel.has(key)) byLabel.set(key, { id, label });
    }
  }
  return [...byLabel.values()].sort((a, b) => a.label.localeCompare(b.label, 'vi', { sensitivity: 'base' }));
}

export function normalizeGenreSelection(genres: string[]): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const raw of genres) {
    const clean = typeof raw === 'string' ? raw.trim() : '';
    if (!clean) continue;
    const key = clean.toLocaleLowerCase('vi');
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(clean);
  }
  return out;
}

export function parseSeriesBrowseParams(input: URLSearchParams | string): SeriesBrowseQueryState {
  const params = typeof input === 'string' ? new URLSearchParams(input) : input;
  const rawGenres = [...params.getAll('genres'), ...params.getAll('genre')]
    .flatMap((val) => val.split(','))
    .map((part) => part.trim())
    .filter(Boolean);
  const rawSort = (params.get('sort') || '').trim() as SeriesSortKey;
  return {
    q: (params.get('q') || '').trim(),
    genres: normalizeGenreSelection(rawGenres),
    sort: VALID_SORTS.has(rawSort) ? rawSort : 'title',
  };
}

export function serializeSeriesBrowseParams(state: SeriesBrowseQueryState): URLSearchParams {
  const params = new URLSearchParams();
  const q = state.q.trim();
  if (q) params.set('q', q);
  const genres = normalizeGenreSelection(state.genres);
  if (genres.length > 0) params.set('genres', genres.join(','));
  if (VALID_SORTS.has(state.sort) && state.sort !== 'title') params.set('sort', state.sort);
  return params;
}

export function filterAndSortSeriesCards(cards: SeriesCard[], state: SeriesBrowseQueryState): SeriesCard[] {
  const rawQuery = state.q.trim();
  const lowerQuery = rawQuery.toLocaleLowerCase('vi');
  const foldedQuery = rawQuery ? foldVietnamese(rawQuery) : '';
  const selectedGenres = new Set(normalizeGenreSelection(state.genres).map((g) => g.toLocaleLowerCase('vi')));

  const filtered = cards.filter((card) => {
    if (rawQuery) {
      const title = card.title || '';
      const original = card.originalTitle || '';
      const matches =
        title.toLocaleLowerCase('vi').includes(lowerQuery) ||
        original.toLocaleLowerCase('vi').includes(lowerQuery) ||
        foldVietnamese(title).includes(foldedQuery) ||
        foldVietnamese(original).includes(foldedQuery);
      if (!matches) return false;
    }
    if (selectedGenres.size > 0) {
      return (card.genres || []).some((genre) => selectedGenres.has(genre.id.toLocaleLowerCase('vi')));
    }
    return true;
  });

  const compareTitle = (a: SeriesCard, b: SeriesCard) =>
    a.title.localeCompare(b.title, 'vi', { sensitivity: 'base' }) || a.id.localeCompare(b.id);

  return [...filtered].sort((a, b) => {
    if (state.sort === 'newest') {
      const yearA = validPositiveYear(a.releaseYearStart) ?? validPositiveYear(a.releaseYearEnd) ?? -1;
      const yearB = validPositiveYear(b.releaseYearStart) ?? validPositiveYear(b.releaseYearEnd) ?? -1;
      if (yearB !== yearA) return yearB - yearA;
      const endA = validPositiveYear(a.releaseYearEnd) ?? -1;
      const endB = validPositiveYear(b.releaseYearEnd) ?? -1;
      return endB - endA || compareTitle(a, b);
    }
    if (state.sort === 'episodes') {
      const episodeDiff = (b.episodeCount ?? -1) - (a.episodeCount ?? -1);
      if (episodeDiff !== 0) return episodeDiff;
      return (b.seasonCount ?? -1) - (a.seasonCount ?? -1) || compareTitle(a, b);
    }
    return compareTitle(a, b);
  });
}
