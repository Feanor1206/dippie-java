import React from 'react';
import { Link } from 'react-router';
import { useWatchProgress } from '@/lib/hooks/useWatchProgress';
import { buildWatchProgressHref, isUsableResumeProgress } from '@/lib/services/watchProgressService';
import {
  buildMovieProgressHref,
  isUsableMovieProgress,
  type MovieProgressRecord,
} from '@/lib/services/movieProgress';
import { ArtworkImage } from '@/features/series/components/ArtworkImage';
import {
  computeProgressPercent,
  formatEpisodeTag,
} from '@/features/series/seriesBrowseUtils';

function formatMark(positionMs: number): string {
  const totalSeconds = Math.floor(positionMs / 1000);
  return `${Math.floor(totalSeconds / 60)}:${String(totalSeconds % 60).padStart(2, '0')}`;
}

export function ContinueWatching({ films }: { films: MovieProgressRecord[] }) {
  const { records } = useWatchProgress();
  const entries = [
    ...records.filter(isUsableResumeProgress).map((episode) => ({
      id: episode.episodeId,
      title: episode.episodeTitle || episode.seriesTitle,
      seriesTitle: episode.seriesTitle,
      episodeTag: formatEpisodeTag(episode.episodeNumber),
      thumbnailUrl: episode.thumbnailUrl ?? null,
      href: buildWatchProgressHref(episode),
      position: episode.positionMs,
      duration: episode.durationMs,
      updatedAt: episode.updatedAt,
    })),
    ...films.filter(isUsableMovieProgress).map((film) => ({
      id: film.movieId,
      title: film.title,
      seriesTitle: '',
      episodeTag: '',
      thumbnailUrl: film.thumbnailUrl ?? null,
      href: buildMovieProgressHref(film),
      position: film.positionMs,
      duration: film.durationMs,
      updatedAt: film.updatedAt,
    })),
  ];
  const unique = [...new Map(entries.reverse().map((entry) => [entry.id, entry])).values()]
    .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
    .slice(0, 3);
  if (!unique.length) return null;

  return (
    <section className="shelf-section" aria-labelledby="continue-title" data-testid="continue-watching">
      <h2 id="continue-title" className="section-title mb-4">Xem tiếp</h2>
      <div className="film-grid">
        {unique.map((entry) => {
          const percent = computeProgressPercent(entry.position, entry.duration);
          const timeMark = formatMark(entry.position);
          const totalMark = entry.duration && entry.duration > 0 ? formatMark(entry.duration) : '';
          return (
            <Link
              key={entry.id}
              to={entry.href}
              data-testid="continue-card"
              className="continue-card group overflow-hidden focus-visible:outline-none"
            >
              <ArtworkImage
                src={entry.thumbnailUrl}
                alt={entry.seriesTitle ? `${entry.seriesTitle} — ${entry.title}` : entry.title}
                title={entry.seriesTitle || entry.title}
                seed={entry.id}
                imgClassName="transition-transform duration-300 group-hover:scale-105"
              >
                <span className="continue-time-badge resume-mark">
                  {totalMark ? `${timeMark} / ${totalMark}` : timeMark}
                </span>
                {percent !== null && (
                  <div
                    className="continue-progress-track"
                    role="progressbar"
                    aria-valuenow={percent}
                    aria-valuemin={0}
                    aria-valuemax={100}
                    aria-label={`Đã xem ${percent}%`}
                    data-testid="continue-progress-bar"
                  >
                    <div className="continue-progress-fill" style={{ width: `${percent}%` }} />
                  </div>
                )}
              </ArtworkImage>

              <div className="continue-card-body">
                {(entry.seriesTitle || entry.episodeTag) && (
                  <div className="flex flex-wrap items-center gap-1.5 text-xs">
                    {entry.episodeTag && (
                      <span className="film-label" data-testid="continue-episode-tag">
                        {entry.episodeTag}
                      </span>
                    )}
                    {entry.seriesTitle && (
                      <span className="truncate font-semibold text-muted" data-testid="continue-series-title">
                        {entry.seriesTitle}
                      </span>
                    )}
                  </div>
                )}
                <h3 className="line-clamp-2 font-display text-base font-bold leading-snug text-ink">
                  {entry.title}
                </h3>
              </div>
            </Link>
          );
        })}
      </div>
    </section>
  );
}
