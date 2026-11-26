import React, { useEffect, useMemo, useRef, useState } from 'react';
import * as ReactRouter from 'react-router';
import { Link } from 'react-router';
import { FilmShelf } from '@/features/movies/components/FilmShelf';
import { ContinueWatching } from '@/features/movies/components/ContinueWatching';
import { SeriesRow } from '@/features/series/components/SeriesRow';
import { KidModeDashboard } from '@/features/profiles/components/KidModeDashboard';
import { KidHomeExperience } from '@/features/profiles/components/KidHomeExperience';
import { useAuth } from '@/lib/context/AuthContext';
import * as motion from '@/lib/motion';
import { listMovieProgress } from '@/lib/services/movieProgress';
import { getGuestWatchProgress, WATCH_HISTORY_CHANGED_EVENT } from '@/lib/services/watchProgressService';
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
  const navigate = typeof ReactRouter.useNavigate === 'function' ? ReactRouter.useNavigate() : null;
  const pageRef = useRef<HTMLElement>(null);
  const userId = status === 'authenticated' ? user?.id ?? null : null;
  const [historyVersion, setHistoryVersion] = useState(0);
  const [heroQuery, setHeroQuery] = useState('');

  useEffect(() => {
    if (typeof window === 'undefined' || typeof window.addEventListener !== 'function') return;
    const eventName =
      typeof WATCH_HISTORY_CHANGED_EVENT === 'string' ? WATCH_HISTORY_CHANGED_EVENT : 'dippie:watch-history-changed';
    const refreshHistory = () => setHistoryVersion((value) => value + 1);
    window.addEventListener(eventName, refreshHistory);
    return () => {
      if (typeof window.removeEventListener === 'function') {
        window.removeEventListener(eventName, refreshHistory);
      }
    };
  }, []);

  const films = useMemo(
    () => (status === 'guest' || userId !== null ? listMovieProgress(userId) : []),
    [status, userId, historyVersion]
  );
  const watched = useMemo(
    () =>
      [
        ...films.filter((film) => film.positionMs > 0).map((film) => film.movieId),
        ...(status === 'guest' ? getGuestWatchProgress().filter((episode) => episode.positionMs > 0).map((episode) => episode.episodeId) : []),
      ].filter((id) => id.length <= MAX_WATCHED_ID_LENGTH),
    [films, status, historyVersion]
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

  motion.useHeroEntrance?.(pageRef);
  motion.useScrollReveal?.(pageRef, [films.length, result?.movies?.length ?? 0]);

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
        style={{ '--stagger-idx': 0 } as React.CSSProperties}
        className={`series-filter-chip motion-chip-pop min-h-[44px] ${selectedMood === null ? 'series-filter-chip--active' : ''}`}
      >
        Tất cả
      </button>
      {MOOD_PRESETS.map((preset, index) => {
        const active = selectedMood === preset.id;
        return (
          <button
            key={preset.id}
            type="button"
            data-testid={`mood-preset-${preset.id}`}
            data-mood-id={preset.id}
            aria-pressed={active}
            onClick={() => setSelectedMood(active ? null : preset.id)}
            style={{ '--stagger-idx': index + 1 } as React.CSSProperties}
            className={`series-filter-chip motion-chip-pop min-h-[44px] ${active ? 'series-filter-chip--active' : ''}`}
          >
            {preset.label}
          </button>
        );
      })}
    </div>
  );

  if (useAuth()?.isKidMode) return <KidHomeExperience films={films} recommendedMovies={result?.movies || []} recommendationsLoading={result === undefined} moodControls={moodChipBar} emptyRecommendationMessage={activeMoodLabel ? `Chưa có phim phù hợp cho mục "${activeMoodLabel}".` : 'Chưa có phim nào ở mục này.'} onResetMood={selectedMood ? () => setSelectedMood(null) : undefined} onRetryRecommendations={result === null ? () => { setState(null); setRetry((n) => n + 1); } : undefined} />;

  return (
    <main ref={pageRef} id="main-content" tabIndex={-1} className="page-shell home-page">
      <section className="cover-grid" aria-labelledby="cover-title">
        <div className="cover-copy">
          <h1 id="cover-title">
            <span data-hero-word="" className="inline-block">Hoạt</span>{' '}
            <span data-hero-word="" className="inline-block">hình</span>{' '}
            <span data-hero-word="" className="inline-block">cho</span>{' '}
            <span data-hero-word="" className="motion-underline-sweep">cả nhà</span>
          </h1>
          <p data-hero-fade="" className="cover-intro">Tìm nhanh theo tên phim, tập phim hoặc câu chuyện yêu thích.</p>
          <form
            role="search"
            data-hero-fade=""
            data-testid="home-hero-search-form"
            onSubmit={(event) => {
              event.preventDefault();
              const trimmed = heroQuery.trim();
              const target = trimmed ? `/search?q=${encodeURIComponent(trimmed)}` : '/search';
              if (navigate) {
                navigate(target);
              } else if (typeof window !== 'undefined') {
                window.location.assign(target);
              }
            }}
            className="flex w-full max-w-md flex-wrap items-center gap-2 sm:flex-nowrap"
          >
            <div className="input-shell flex min-h-[46px] min-w-0 flex-1 items-center rounded-md border border-[#a6b4a4] bg-paper-white px-3">
              <label htmlFor="home-search-input" className="sr-only">
                Tìm phim hoạt hình hoặc nhân vật
              </label>
              <input
                id="home-search-input"
                name="q"
                data-testid="home-hero-search-input"
                type="search"
                enterKeyHint="search"
                value={heroQuery}
                onChange={(event) => setHeroQuery(event.target.value)}
                placeholder="Nhập tên phim hoặc nhân vật…"
                className="w-full min-w-0 bg-transparent text-base sm:text-sm text-ink placeholder:text-muted focus:outline-none"
              />
            </div>
            <button
              type="submit"
              data-testid="home-hero-search-submit"
              className="cover-cta motion-sheen shrink-0 cursor-pointer"
            >
              Tìm phim
            </button>
          </form>

          <nav className="topics-list" aria-label="Gợi ý tìm phim">
            {quickTopics.map((topic, index) => (
              <Link
                key={topic.label}
                to={topic.href}
                style={{ '--stagger-idx': index } as React.CSSProperties}
                className="topic-chip motion-chip-pop"
              >
                {topic.label}
              </Link>
            ))}
          </nav>
        </div>

        <div data-hero-fade="" className="cover-art-panel">
          <img
            src={coverArt}
            alt="Bạn nhỏ và thỏ đi đến rạp chiếu phim trong rừng"
            width="760"
            height="580"
            decoding="async"
          />
        </div>
      </section>

      <KidModeDashboard />
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
