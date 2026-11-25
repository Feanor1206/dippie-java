import { genreLabel } from '@/lib/labels';
import type { SeriesCard, SeriesCardGenre } from '@/lib/services/seriesCardService';

export type SeriesSortKey = 'title' | 'newest' | 'episodes';

export type SeriesBrowseQueryState = {
  q: string;
  genres: string[];
  sort: SeriesSortKey;
};

export const SERIES_SORT_OPTIONS: { value: SeriesSortKey; label: string }[] = [
  { value: 'title', label: 'Tên A–Z' },
  { value: 'newest', label: 'Mới nhất theo năm' },
  { value: 'episodes', label: 'Nhiều tập nhất' },
];

const VALID_SORTS = new Set<SeriesSortKey>(['title', 'newest', 'episodes']);

function cleanUrl(value?: string | null): string | null {
  return typeof value === 'string' && value.trim().length > 0 ? value.trim() : null;
}

function foldVietnamese(text: string): string {
  return text
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[đĐ]/g, 'd')
    .toLocaleLowerCase('vi');
}

export function pickSeriesArtwork(
  card: { backdropUrl?: string | null; posterUrl?: string | null },
  prefer: 'backdrop' | 'poster' = 'backdrop'
): { primary: string | null; fallback: string | null } {
  const backdrop = cleanUrl(card.backdropUrl);
  const poster = cleanUrl(card.posterUrl);
  if (prefer === 'poster') {
    const primary = poster || backdrop;
    const fallback = poster && backdrop && poster !== backdrop ? backdrop : null;
    return { primary, fallback };
  }
  const primary = backdrop || poster;
  const fallback = backdrop && poster && backdrop !== poster ? poster : null;
  return { primary, fallback };
}

export function pickMovieArtwork(item: {
  thumbnailUrl?: string | null;
  backdrop?: string | null;
  poster?: string | null;
}): { primary: string | null; fallback: string | null } {
  const candidates = [cleanUrl(item.thumbnailUrl), cleanUrl(item.backdrop), cleanUrl(item.poster)].filter(
    (url): url is string => Boolean(url)
  );
  const unique = [...new Set(candidates)];
  return {
    primary: unique[0] || null,
    fallback: unique[1] || null,
  };
}

export function getTitleInitials(title?: string | null): string {
  const clean = typeof title === 'string' ? title.trim() : '';
  if (!clean) return '?';
  const words = clean
    .split(/[\s\-_:|/.,!?()[\]"]+/)
    .map((part) => part.trim())
    .filter(Boolean);
  if (words.length === 0) return clean.slice(0, 2).toLocaleUpperCase('vi');
  if (words.length === 1) {
    const chars = Array.from(words[0]);
    return chars.slice(0, 2).join('').toLocaleUpperCase('vi');
  }
  const first = Array.from(words[0])[0] || '';
  const last = Array.from(words[words.length - 1])[0] || '';
  return `${first}${last}`.toLocaleUpperCase('vi');
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
  const validStart = typeof start === 'number' && Number.isFinite(start) && start > 0 ? Math.trunc(start) : null;
  const validEnd = typeof end === 'number' && Number.isFinite(end) && end > 0 ? Math.trunc(end) : null;
  if (validStart !== null && validEnd !== null) {
    return validStart === validEnd ? String(validStart) : `${validStart}–${validEnd}`;
  }
  if (validStart !== null) return String(validStart);
  if (validEnd !== null) return String(validEnd);
  return '';
}

export function formatSeriesCounts(seasonCount?: number | null, episodeCount?: number | null): string {
  const seasons = typeof seasonCount === 'number' && Number.isFinite(seasonCount) && seasonCount > 0 ? Math.floor(seasonCount) : null;
  const episodes = typeof episodeCount === 'number' && Number.isFinite(episodeCount) && episodeCount > 0 ? Math.floor(episodeCount) : null;
  const parts: string[] = [];
  if (seasons !== null) parts.push(`${seasons} mùa`);
  if (episodes !== null) parts.push(`${episodes} tập`);
  return parts.join(' · ');
}

export function formatEpisodeTag(episodeNumber?: number | null): string {
  if (typeof episodeNumber !== 'number' || !Number.isFinite(episodeNumber) || episodeNumber <= 0) {
    return '';
  }
  return `Tập ${Math.floor(episodeNumber)}`;
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
  const ratio = Math.min(1, Math.max(0, positionMs / durationMs));
  return Math.round(ratio * 100);
}

export function extractSeriesGenres(cards: SeriesCard[]): SeriesCardGenre[] {
  const byId = new Map<string, SeriesCardGenre>();
  for (const card of cards) {
    for (const genre of card.genres || []) {
      const id = genre.id.trim();
      if (!id) continue;
      const key = id.toLocaleLowerCase('vi');
      if (!byId.has(key)) {
        byId.set(key, { id, label: genre.label?.trim() || genreLabel(id) });
      }
    }
  }
  return [...byId.values()].sort((a, b) => a.label.localeCompare(b.label, 'vi', { sensitivity: 'base' }));
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
  const q = (params.get('q') || '').trim();
  const rawGenres: string[] = [];
  for (const val of params.getAll('genres')) {
    for (const part of val.split(',')) {
      if (part.trim()) rawGenres.push(part.trim());
    }
  }
  for (const val of params.getAll('genre')) {
    for (const part of val.split(',')) {
      if (part.trim()) rawGenres.push(part.trim());
    }
  }
  const rawSort = (params.get('sort') || '').trim() as SeriesSortKey;
  const sort: SeriesSortKey = VALID_SORTS.has(rawSort) ? rawSort : 'title';
  return {
    q,
    genres: normalizeGenreSelection(rawGenres),
    sort,
  };
}

export function serializeSeriesBrowseParams(state: SeriesBrowseQueryState): URLSearchParams {
  const params = new URLSearchParams();
  const q = state.q.trim();
  if (q) params.set('q', q);
  const genres = normalizeGenreSelection(state.genres);
  if (genres.length > 0) params.set('genres', genres.join(','));
  if (VALID_SORTS.has(state.sort) && state.sort !== 'title') {
    params.set('sort', state.sort);
  }
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
      const directMatch =
        title.toLocaleLowerCase('vi').includes(lowerQuery) ||
        original.toLocaleLowerCase('vi').includes(lowerQuery);
      const foldedMatch =
        foldVietnamese(title).includes(foldedQuery) ||
        foldVietnamese(original).includes(foldedQuery);
      if (!directMatch && !foldedMatch) return false;
    }
    if (selectedGenres.size > 0) {
      const hasGenre = (card.genres || []).some((genre) => selectedGenres.has(genre.id.toLocaleLowerCase('vi')));
      if (!hasGenre) return false;
    }
    return true;
  });

  const compareTitle = (a: SeriesCard, b: SeriesCard) => {
    const cmp = a.title.localeCompare(b.title, 'vi', { sensitivity: 'base' });
    return cmp !== 0 ? cmp : a.id.localeCompare(b.id);
  };

  return [...filtered].sort((a, b) => {
    if (state.sort === 'newest') {
      const yearA = a.releaseYearStart ?? a.releaseYearEnd ?? -1;
      const yearB = b.releaseYearStart ?? b.releaseYearEnd ?? -1;
      if (yearB !== yearA) return yearB - yearA;
      const endA = a.releaseYearEnd ?? -1;
      const endB = b.releaseYearEnd ?? -1;
      if (endB !== endA) return endB - endA;
      return compareTitle(a, b);
    }
    if (state.sort === 'episodes') {
      const epA = a.episodeCount ?? -1;
      const epB = b.episodeCount ?? -1;
      if (epB !== epA) return epB - epA;
      const seasonA = a.seasonCount ?? -1;
      const seasonB = b.seasonCount ?? -1;
      if (seasonB !== seasonA) return seasonB - seasonA;
      return compareTitle(a, b);
    }
    return compareTitle(a, b);
  });
}
