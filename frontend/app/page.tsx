import React, { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router';
import { FilmShelf } from '@/features/movies/components/FilmShelf';
import { ContinueWatching } from '@/features/movies/components/ContinueWatching';
import { SeriesRow } from '@/features/series/components/SeriesRow';
import { useAuth } from '@/lib/context/AuthContext';
import { listMovieProgress } from '@/lib/services/movieProgress';
import { getGuestWatchProgress } from '@/lib/services/watchProgressService';
import {
  getRecommendations,
  MOOD_PRESETS,
  type MoodPresetId,
} from '@/lib/services/recommendationService';
import { type CatalogGenreOption } from '@/lib/services/catalogService';
import { useCatalogGenres } from '@/lib/hooks/useCatalogData';
import { genreLabel } from '@/lib/labels';
import coverArt from '@/assets/dippie-cover.svg';

const MAX_WATCHED_ID_LENGTH = 100;
const MAX_QUICK_GENRES = 4;

const SERIES_QUICK_TOPICS = [
  { label: 'Larva', href: '/search?q=Larva' },
  { label: 'Công chúa Ori', href: '/search?q=Công chúa Ori' },
];

const DEFAULT_GENRE_TOPICS: CatalogGenreOption[] = [
  { id: 'family', label: 'Gia đình', count: 1 },
  { id: 'comedy', label: 'Hài hước', count: 1 },
  { id: 'slice_of_life', label: 'Đời thường', count: 1 },
  { id: 'school', label: 'Học đường', count: 1 },
];

export default function HomePage() {
  const { status, token, user } = useAuth();
  const userId = status === 'authenticated' ? user?.id ?? null : null;
  const films = useMemo(() => status === 'guest' || userId !== null ? listMovieProgress(userId) : [], [status, userId]);
  const watched = useMemo(
    () =>
      [
        ...films.filter((film) => film.positionMs > 0).map((film) => film.movieId),
        ...(status === 'guest' ? getGuestWatchProgress().filter((episode) => episode.positionMs > 0).map((episode) => episode.episodeId) : []),
      ].filter((id) => id.length <= MAX_WATCHED_ID_LENGTH),
    [films, status]
  );

  const [state, setState] = useState<{
    scope: string;
    result: Awaited<ReturnType<typeof getRecommendations>> | null;
  } | null>(null);
  const [retry, setRetry] = useState(0);
  const [selectedMood, setSelectedMood] = useState<MoodPresetId | null>(null);

  const fetchedGenres = useCatalogGenres();
  const genreTopics = useMemo(() => {
    if (!fetchedGenres || fetchedGenres.length === 0) return DEFAULT_GENRE_TOPICS;
    const available = fetchedGenres.filter((g) => g.count > 0 && g.id.toLowerCase() !== 'animation');
    return (available.length > 0 ? available : fetchedGenres).slice(0, MAX_QUICK_GENRES);
  }, [fetchedGenres]);

  const accountToken = status === 'authenticated' ? token : null;
  const scope = `${accountToken || status}:${selectedMood || ''}`;
  const result = state?.scope === scope ? state.result : undefined;

  const quickTopics = useMemo(
    () => [
      ...SERIES_QUICK_TOPICS,
      ...genreTopics.map((g) => ({
        label: g.label || genreLabel(g.id),
        href: `/search?genre=${encodeURIComponent(g.id)}`,
      })),
    ],
    [genreTopics]
  );

  useEffect(() => {
    if (status === 'checking') return;
    const controller = new AbortController();
    const request = selectedMood
      ? getRecommendations(accountToken, controller.signal, status === 'guest' ? watched : [], { moodPreset: selectedMood })
      : getRecommendations(accountToken, controller.signal, status === 'guest' ? watched : []);
    request
      .then((value) => {
        if (!controller.signal.aborted) setState({ scope, result: value });
      })
      .catch(() => {
        if (!controller.signal.aborted) setState({ scope, result: null });
      });
    return () => controller.abort();
  }, [accountToken, scope, retry, status, watched, selectedMood]);

  const activeMoodLabel = selectedMood
    ? MOOD_PRESETS.find((preset) => preset.id === selectedMood)?.label || ''
    : '';

  const moodChipBar = (
    <div
      role="group"
      aria-label="Chọn gợi ý xem phim"
      data-testid="mood-preset-bar"
      className="flex flex-wrap items-center gap-2"
    >
      <button
        type="button"
        data-testid="mood-preset-all"
        aria-pressed={selectedMood === null}
        onClick={() => setSelectedMood(null)}
        className={`series-filter-chip min-h-[44px] ${selectedMood === null ? 'series-filter-chip--active' : ''}`}
      >
        Tất cả
      </button>
      {MOOD_PRESETS.map((preset) => {
        const active = selectedMood === preset.id;
        return (
          <button
            key={preset.id}
            type="button"
            data-testid={`mood-preset-${preset.id}`}
            data-mood-id={preset.id}
            aria-pressed={active}
            onClick={() => setSelectedMood(active ? null : preset.id)}
            className={`series-filter-chip min-h-[44px] ${active ? 'series-filter-chip--active' : ''}`}
          >
            {preset.label}
          </button>
        );
      })}
    </div>
  );

  return (
    <main id="main-content" tabIndex={-1} className="page-shell home-page">
      <section className="cover-grid" aria-labelledby="cover-title">
        <div className="cover-copy">
          <h1 id="cover-title">Hoạt hình cho cả nhà</h1>
          <p className="cover-intro">Tìm theo tên phim, tên tập hoặc nội dung bé thích.</p>
          <Link to="/search" className="cover-cta">
            Tìm phim
          </Link>

          <nav className="topics-list" aria-label="Gợi ý tìm phim">
            {quickTopics.map((topic) => (
              <Link key={topic.label} to={topic.href} className="topic-chip">
                {topic.label}
              </Link>
            ))}
          </nav>
        </div>

        <div className="cover-art-panel">
          <img
            src={coverArt}
            alt="Bạn nhỏ và thỏ đi đến rạp chiếu phim trong rừng"
            width="760"
            height="580"
          />
        </div>
      </section>

      <ContinueWatching films={films} />
      <SeriesRow />
      <FilmShelf
        movies={result?.movies || []}
        subtitle={result?.reasoning === 'Gợi ý từ kho phim.' ? '' : result?.reasoning}
        loading={result === undefined}
        controls={moodChipBar}
        emptyMessage={
          activeMoodLabel
            ? `Chưa có phim phù hợp cho mục "${activeMoodLabel}".`
            : 'Chưa có phim nào ở mục này.'
        }
        onResetEmpty={selectedMood ? () => setSelectedMood(null) : undefined}
        onRetry={
          result === null
            ? () => {
                setState(null);
                setRetry((n) => n + 1);
              }
            : undefined
        }
      />
    </main>
  );
}
