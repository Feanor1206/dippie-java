import React, { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { Play, Search } from 'lucide-react';
import { useOptionalAuth } from '@/lib/context/AuthContext';
import { ChildAvatarBadge } from '@/features/profiles/components/ChildAvatarBadge';
import {
  KidModeDashboard,
  isStandaloneCatalogFilm,
} from '@/features/profiles/components/KidModeDashboard';
import { SeriesCard } from '@/features/series/components/SeriesCard';
import { ArtworkImage } from '@/features/series/components/ArtworkImage';
import { FilmShelf } from '@/features/movies/components/FilmShelf';
import { useSeriesCards } from '@/lib/hooks/useCatalogData';
import { useWatchProgress } from '@/lib/hooks/useWatchProgress';
import { browseCatalog } from '@/lib/services/catalogService';
import { fetchKidVipStatus } from '@/lib/services/vipService';
import { buildWatchProgressHref, isUsableResumeProgress } from '@/lib/services/watchProgressService';
import {
  buildMovieProgressHref,
  isUsableMovieProgress,
  type MovieProgressRecord,
} from '@/lib/services/movieProgress';
import { cleanMediaTitle } from '@/lib/labels';
import { parseTimestamp } from '@/lib/timestamps';
import { computeProgressPercent, formatEpisodeTag } from '@/features/series/seriesBrowseUtils';
import type { Movie } from '@/types/movie';
import coverArt from '@/assets/dippie-cover.svg';

type ResumeTileItem = {
  id: string;
  seriesId: string | null;
  title: string;
  seriesTitle: string;
  episodeTag: string;
  thumbnailUrl: string | null;
  href: string;
  positionMs: number;
  durationMs: number | null;
  updatedAt: string;
};

function formatResumeClock(positionMs: number): string {
  const totalSeconds = Math.max(0, Math.floor(positionMs / 1000));
  return `${Math.floor(totalSeconds / 60)}:${String(totalSeconds % 60).padStart(2, '0')}`;
}

export function KidHomeExperience({
  films = [],
  recommendedMovies,
  recommendationsLoading = false,
  moodControls,
  emptyRecommendationMessage,
  onResetMood,
  onRetryRecommendations,
  standaloneFilmCount,
}: {
  films?: MovieProgressRecord[];
  recommendedMovies?: Movie[];
  recommendationsLoading?: boolean;
  moodControls?: React.ReactNode;
  emptyRecommendationMessage?: string;
  onResetMood?: () => void;
  onRetryRecommendations?: () => void;
  standaloneFilmCount?: number;
}) {
  const auth = typeof useOptionalAuth === 'function' ? useOptionalAuth() : null;
  const activeProfile = auth?.activeProfile ?? null;
  const token = auth?.token ?? '';
  const isKidMode = Boolean(auth?.isAuthenticated && auth?.isKidMode && token);

  const seriesQuery =
    typeof useSeriesCards === 'function'
      ? useSeriesCards(true, 'Chưa tải được danh sách hoạt hình.')
      : { cards: [], loading: false, error: null, retry: () => {} };
  const watchProgress =
    typeof useWatchProgress === 'function' ? useWatchProgress() : { records: [] };
  const [vipSeriesIds, setVipSeriesIds] = useState<string[]>([]);
  const [detectedStandaloneCount, setDetectedStandaloneCount] = useState<number>(0);

  useEffect(() => {
    if (!isKidMode || !token || typeof fetchKidVipStatus !== 'function') {
      setVipSeriesIds([]);
      return;
    }
    const controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
    fetchKidVipStatus(token, { signal: controller?.signal })
      .then((res) => {
        if (!controller?.signal.aborted) setVipSeriesIds(res.vipSeriesIds);
      })
      .catch(() => {
        if (!controller?.signal.aborted) setVipSeriesIds([]);
      });
    return () => controller?.abort();
  }, [isKidMode, token, activeProfile?.id]);

  useEffect(() => {
    if (standaloneFilmCount !== undefined || typeof browseCatalog !== 'function') {
      return;
    }
    const controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
    browseCatalog({ page: 0, size: 48, signal: controller?.signal })
      .then((page) => {
        if (controller?.signal.aborted) return;
        const count = Array.isArray(page?.items)
          ? page.items.filter(isStandaloneCatalogFilm).length
          : 0;
        setDetectedStandaloneCount(count);
      })
      .catch(() => {
        if (!controller?.signal.aborted) setDetectedStandaloneCount(0);
      });
    return () => controller?.abort();
  }, [standaloneFilmCount]);

  const checkEpisodeResume =
    typeof isUsableResumeProgress === 'function'
      ? isUsableResumeProgress
      : (ep: { positionMs?: number; completed?: boolean }) => Boolean(ep && (ep.positionMs ?? 0) > 0 && !ep.completed);
  const checkMovieResume =
    typeof isUsableMovieProgress === 'function'
      ? isUsableMovieProgress
      : (f: { positionMs?: number; completed?: boolean }) => Boolean(f && (f.positionMs ?? 0) > 0 && !f.completed);

  const rawResumeItems: ResumeTileItem[] = [
    ...(watchProgress?.records || []).filter(checkEpisodeResume).map((episode) => ({
      id: episode.episodeId,
      seriesId: episode.seriesId,
      title: cleanMediaTitle(episode.episodeTitle || episode.seriesTitle),
      seriesTitle: cleanMediaTitle(episode.seriesTitle),
      episodeTag: typeof formatEpisodeTag === 'function' ? formatEpisodeTag(episode.episodeNumber) : '',
      thumbnailUrl: episode.thumbnailUrl ?? null,
      href: typeof buildWatchProgressHref === 'function' ? buildWatchProgressHref(episode) : `/series/${episode.seriesId}`,
      positionMs: episode.positionMs,
      durationMs: episode.durationMs,
      updatedAt: episode.updatedAt,
    })),
    ...(films || []).filter(checkMovieResume).map((film) => ({
      id: film.movieId,
      seriesId: null,
      title: cleanMediaTitle(film.title),
      seriesTitle: '',
      episodeTag: '',
      thumbnailUrl: film.thumbnailUrl ?? null,
      href: typeof buildMovieProgressHref === 'function' ? buildMovieProgressHref(film) : `/movies/${film.movieId}`,
      positionMs: film.positionMs,
      durationMs: film.durationMs,
      updatedAt: film.updatedAt,
    })),
  ].sort((a, b) => parseTimestamp(b.updatedAt) - parseTimestamp(a.updatedAt));

  const seenIds = new Set<string>();
  const seenSeries = new Set<string>();
  const resumeItems: ResumeTileItem[] = [];
  for (const entry of rawResumeItems) {
    if (seenIds.has(entry.id)) continue;
    if (entry.seriesId && seenSeries.has(entry.seriesId)) continue;
    seenIds.add(entry.id);
    if (entry.seriesId) seenSeries.add(entry.seriesId);
    resumeItems.push(entry);
    if (resumeItems.length >= 3) break;
  }

  const primaryResume = resumeItems[0] ?? null;
  const secondaryResumes = resumeItems.slice(1);
  const primaryPercent = primaryResume
    ? computeProgressPercent(primaryResume.positionMs, primaryResume.durationMs)
    : null;

  const effectiveStandaloneCount =
    typeof standaloneFilmCount === 'number'
      ? standaloneFilmCount
      : Math.max(
          detectedStandaloneCount,
          (recommendedMovies || []).filter(isStandaloneCatalogFilm).length
        );

  return (
    <main id="main-content" tabIndex={-1} data-testid="kid-home-experience" className="page-shell home-page space-y-7">
      <section
        aria-label="Lời chào hồ sơ của bé"
        data-testid="kid-home-greeting"
        className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border-2 border-ink bg-paper-white px-4 py-3.5 shadow-[3px_3px_0_#203328] sm:px-5"
      >
        <div className="flex min-w-0 items-center gap-3">
          <span data-testid="kid-home-avatar" className="inline-flex shrink-0">
            <ChildAvatarBadge avatarKey={activeProfile?.avatarKey || 'bear'} size="md" />
          </span>
          <div className="min-w-0">
            <h1 className="truncate font-display text-xl font-bold text-ink sm:text-2xl">
              {`Chào ${activeProfile?.name || 'bé'}!`}
            </h1>
            <p className="text-xs font-medium text-muted sm:text-sm">
              Chạm vào hình bộ phim bé thích để xem ngay nhé!
            </p>
          </div>
        </div>

        <Link
          to="/search"
          data-testid="kid-home-search-shortcut"
          className="btn-secondary inline-flex min-h-[44px] shrink-0 items-center gap-2 px-4 py-2 text-xs font-bold sm:text-sm"
        >
          <Search size={16} aria-hidden="true" />
          <span>Tìm phim</span>
        </Link>
      </section>

      {primaryResume && (
        <section aria-labelledby="kid-resume-heading" data-testid="continue-watching" className="space-y-3">
          <h2 id="kid-resume-heading" className="section-title">
            Xem tiếp
          </h2>
          <Link
            to={primaryResume.href}
            data-testid="kid-home-resume-tile"
            className="group grid grid-cols-1 overflow-hidden rounded-2xl border-2 border-ink bg-paper-white shadow-[4px_4px_0_#203328] transition-transform hover:-translate-y-0.5 md:grid-cols-[minmax(0,1.25fr)_minmax(0,1fr)]"
          >
            <ArtworkImage
              src={primaryResume.thumbnailUrl}
              alt={
                primaryResume.seriesTitle
                  ? `${primaryResume.seriesTitle}: ${primaryResume.title}`
                  : primaryResume.title
              }
              title={primaryResume.seriesTitle || primaryResume.title}
              seed={primaryResume.id}
              imgClassName="transition-transform duration-300 group-hover:scale-105"
            >
              <span className="continue-time-badge resume-mark">
                {primaryResume.durationMs && primaryResume.durationMs > 0
                  ? `${formatResumeClock(primaryResume.positionMs)} / ${formatResumeClock(primaryResume.durationMs)}`
                  : formatResumeClock(primaryResume.positionMs)}
              </span>
              {primaryPercent !== null && (
                <div
                  className="continue-progress-track"
                  role="progressbar"
                  aria-valuenow={primaryPercent}
                  aria-valuemin={0}
                  aria-valuemax={100}
                  aria-label={`Đã xem ${primaryPercent}%`}
                  data-testid="kid-home-resume-progress"
                >
                  <div className="continue-progress-fill" style={{ width: `${primaryPercent}%` }} />
                </div>
              )}
            </ArtworkImage>

            <div className="flex flex-col justify-between gap-3 p-4 sm:p-6">
              <div className="space-y-2">
                <div className="flex flex-wrap items-center gap-2">
                  {primaryResume.episodeTag && (
                    <span className="rounded-full border border-ink bg-[#f2d18c] px-2.5 py-0.5 text-xs font-extrabold text-ink">
                      {primaryResume.episodeTag}
                    </span>
                  )}
                  {primaryResume.seriesTitle && (
                    <span className="text-xs font-bold text-brand-dark sm:text-sm">
                      {primaryResume.seriesTitle}
                    </span>
                  )}
                </div>
                <h3 className="font-display text-xl font-bold leading-snug text-ink sm:text-2xl">
                  {primaryResume.title}
                </h3>
                <p className="text-xs text-muted sm:text-sm">
                  Bấm để xem tiếp ngay từ chỗ bé đang dừng nhé!
                </p>
              </div>

              <span className="btn-primary motion-sheen inline-flex min-h-[48px] w-full items-center justify-center gap-2 text-sm font-bold sm:w-auto sm:self-start">
                <Play size={16} aria-hidden="true" className="fill-current" />
                <span>Xem tiếp ngay</span>
              </span>
            </div>
          </Link>

          {secondaryResumes.length > 0 && (
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              {secondaryResumes.map((entry) => (
                <Link
                  key={entry.id}
                  to={entry.href}
                  data-testid="continue-card"
                  className="continue-card group overflow-hidden"
                >
                  <ArtworkImage
                    src={entry.thumbnailUrl}
                    alt={entry.seriesTitle ? `${entry.seriesTitle}: ${entry.title}` : entry.title}
                    title={entry.seriesTitle || entry.title}
                    seed={entry.id}
                    imgClassName="transition-transform duration-300 group-hover:scale-105"
                  />
                  <div className="continue-card-body">
                    {entry.seriesTitle && (
                      <span className="truncate text-xs font-bold text-brand-dark">{entry.seriesTitle}</span>
                    )}
                    <h3 className="line-clamp-1 font-display text-base font-bold text-ink">{entry.title}</h3>
                  </div>
                </Link>
              ))}
            </div>
          )}
        </section>
      )}

      <section aria-labelledby="kid-series-heading" data-testid="kid-home-series-section" className="space-y-3.5">
        <div className="flex items-center justify-between gap-3">
          <h2 id="kid-series-heading" className="section-title">
            Hoạt hình cho bé
          </h2>
          <Link to="/series" className="shelf-link" data-testid="kid-series-view-all">
            Xem tất cả
          </Link>
        </div>

        {seriesQuery.loading ? (
          <div
            role="status"
            data-testid="kid-series-skeleton"
            className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4"
          >
            {Array.from({ length: 4 }).map((_, index) => (
              <div key={index} className="series-skeleton-card animate-pulse overflow-hidden">
                <div className="aspect-video w-full bg-surface-elevated" />
                <div className="space-y-2 p-3.5">
                  <div className="h-4 w-3/4 rounded bg-surface-elevated" />
                  <div className="h-3 w-1/2 rounded bg-surface-elevated" />
                </div>
              </div>
            ))}
          </div>
        ) : seriesQuery.error ? (
          <div className="shelf-message" role="alert">
            <span>{seriesQuery.error}</span>
            <button type="button" className="btn-secondary" onClick={seriesQuery.retry}>
              Thử lại
            </button>
          </div>
        ) : (
          <div
            data-testid="kid-home-series-grid"
            className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4"
          >
            {seriesQuery.cards.map((card) => (
              <SeriesCard
                key={card.id}
                card={card}
                headingLevel="h3"
                className="h-full"
                isVip={vipSeriesIds.includes(card.id)}
              />
            ))}
          </div>
        )}
      </section>

      {recommendedMovies !== undefined && (
        <FilmShelf
          movies={recommendedMovies}
          loading={recommendationsLoading}
          controls={moodControls}
          emptyMessage={emptyRecommendationMessage || 'Chưa có phim nào ở mục này.'}
          onResetEmpty={onResetMood}
          onRetry={onRetryRecommendations}
        />
      )}

      <KidModeDashboard standaloneFilmCount={effectiveStandaloneCount} />

      <section
        aria-label="Khám phá thêm kho phim"
        data-testid="kid-home-explore-card"
        className="flex flex-col items-center justify-between gap-4 overflow-hidden rounded-2xl border border-ink bg-paper-white p-5 shadow-[0_3px_0_#203328] sm:flex-row sm:p-6"
      >
        <div className="space-y-1.5 text-center sm:text-left">
          <h2 className="font-display text-lg font-bold text-ink sm:text-xl">
            Tìm tập hoạt hình theo cảnh bé nhớ
          </h2>
          <p className="text-xs text-muted sm:text-sm">
            Ba mẹ và bé có thể gõ tên nhân vật hoặc nội dung tập phim để tìm ngay.
          </p>
          <div className="pt-1">
            <Link to="/search" className="cover-cta inline-flex min-h-[44px]">
              Khám phá kho phim
            </Link>
          </div>
        </div>
        <img
          src={coverArt}
          alt="Bạn nhỏ và thỏ đi đến rạp chiếu phim trong rừng"
          width="220"
          height="168"
          className="h-auto w-44 shrink-0 rounded-lg border border-ink/20 bg-[#d7e7cb] sm:w-52"
        />
      </section>
    </main>
  );
}
