import { YouTubeLink } from '@/components/ui/YouTubeLink';
import { Link } from 'react-router';
import { cleanMediaTitle, formatYear, movieSynopsis, normalizeGenreLabels, resolveRatingSummary } from '@/lib/labels';
import { buildMediaItemHref } from '@/lib/services/mediaRouting';
import { isMediaExternalOnly, resolveExternalYoutubeWatchUrl } from '@/lib/services/playbackSource';
import { ArtworkImage } from '@/features/series/components/ArtworkImage';
import { QuickSaveButtons } from '@/features/library/components/LibraryControls';
import type { Movie } from '@/types/movie';

const MAX_VISIBLE_GENRES = 3;

function filmLabel(movie: Movie, type: string): string {
  if (type === 'episode') {
    const number = movie.episode_number || movie.episodeNumber;
    return number ? `Tập ${number}` : 'Tập';
  }
  if (type === 'series') return 'Phim bộ';
  if (type === 'segment' || type === 'scene') return 'Đoạn phim';
  return formatYear(movie.year);
}

function cleanMediaUrl(value?: string | null): string | null {
  return typeof value === 'string' && value.trim().length > 0 ? value.trim() : null;
}

export function FilmGrid({
  movies,
  search = false,
  loading = false,
}: {
  movies: Movie[];
  search?: boolean;
  loading?: boolean;
}) {
  const Title = search ? 'h2' : 'h3';
  return (
    <div data-testid={search ? 'catalog-results' : undefined} className="film-grid" aria-busy={loading}>
      {movies.map((movie) => {
        const type = movie.item_type || 'movie';
        const label = filmLabel(movie, type);
        const seriesTitle = (movie.series_title || movie.seriesTitle || '').trim();
        const cleanedTitle = cleanMediaTitle(movie.title);
        const title = cleanedTitle.charAt(0).toLocaleUpperCase('vi') + cleanedTitle.slice(1);
        const primaryArt =
          cleanMediaUrl(movie.thumbnailUrl) || cleanMediaUrl(movie.backdrop) || cleanMediaUrl(movie.poster);
        const posterArt = cleanMediaUrl(movie.poster);
        const fallbackArt = primaryArt && posterArt && posterArt !== primaryArt ? posterArt : null;
        const ratingSummary = resolveRatingSummary(movie);
        const displayGenres = normalizeGenreLabels(movie.genres).slice(0, MAX_VISIBLE_GENRES);
        const externalOnly =
          typeof isMediaExternalOnly === 'function'
            ? isMediaExternalOnly(movie)
            : Boolean(movie.externalOnly || movie.external_only || movie.canEmbed === false || movie.can_embed === false);
        const externalYoutubeUrl = externalOnly
          ? typeof resolveExternalYoutubeWatchUrl === 'function'
            ? resolveExternalYoutubeWatchUrl(movie)
            : movie.youtubeWatchUrl || movie.youtube_watch_url || null
          : null;

        return (
          <article key={movie.id} className="film-tile group relative overflow-hidden">
            <div className="absolute top-2 right-2 z-20">
              <QuickSaveButtons
                itemId={movie.id}
                title={title}
                poster={primaryArt || ''}
                variant="overlay"
                testIdPrefix={`film-card-${movie.id}`}
              />
            </div>

            <Link
              to={buildMediaItemHref(movie)}
              data-testid={search ? 'search-result-card' : 'film-card'}
              data-movie-id={movie.id}
              data-result-id={search ? movie.id : undefined}
              data-result-type={search ? type : undefined}
              className="flex flex-1 flex-col focus-visible:outline-none"
            >
              <ArtworkImage
                src={primaryArt}
                fallbackSrc={fallbackArt}
                alt={seriesTitle ? `${seriesTitle}: ${title}` : title}
                title={seriesTitle || title}
                seed={movie.series_id || movie.seriesId || movie.id}
                imgClassName="transition-transform duration-300 group-hover:scale-105"
              >
                {label && <span className="film-art-badge film-label">{label}</span>}
              </ArtworkImage>

              <div className="film-tile-body">
                {seriesTitle && (
                  <span className="film-series-context truncate" data-testid="film-series-title">
                    {seriesTitle}
                  </span>
                )}
                <Title>{title}</Title>
                {externalOnly && (
                  <span
                    data-testid="film-external-badge"
                    className="inline-flex items-center rounded border border-amber-500/40 bg-amber-500/10 px-2 py-0.5 text-[11px] font-semibold text-amber-900"
                  >
                    Chỉ mở trên YouTube
                  </span>
                )}
                <span className="text-xs font-medium text-muted" data-testid="film-card-rating">
                  {ratingSummary.hasRating ? `★ ${ratingSummary.label}` : ratingSummary.label}
                </span>
                {displayGenres.length > 0 && (
                  <div className="flex flex-wrap gap-1" data-testid="film-card-genres">
                    {displayGenres.map((genre) => (
                      <span key={genre} className="series-genre-pill">
                        {genre}
                      </span>
                    ))}
                  </div>
                )}
                {movie.description && <p className="film-summary">{movieSynopsis(movie.description)}</p>}
                <span className="film-action">
                  {externalOnly ? 'Xem chi tiết' : type === 'series' ? 'Chọn tập' : 'Xem phim'}
                </span>
              </div>
            </Link>

            {externalOnly && externalYoutubeUrl && (
              <div className="border-t border-surface-border px-4 py-1.5">
                <YouTubeLink
                  url={externalYoutubeUrl}
                  testId="film-open-youtube-link"
                  className="inline-flex min-h-[44px] items-center text-xs font-bold text-brand-dark underline underline-offset-4"
                >
                  Mở trên YouTube
                </YouTubeLink>
              </div>
            )}
          </article>
        );
      })}
    </div>
  );
}

export function FilmShelf({
  movies,
  subtitle,
  loading,
  onRetry,
  controls,
  emptyMessage,
  onResetEmpty,
}: {
  movies: Movie[];
  subtitle?: string;
  loading?: boolean;
  onRetry?: () => void;
  controls?: React.ReactNode;
  emptyMessage?: string;
  onResetEmpty?: () => void;
}) {
  return (
    <section id="goi-y" data-testid="film-shelf" className="shelf-section" aria-labelledby="recommend-title">
      <div className="shelf-heading">
        <div>
          <h2 id="recommend-title" className="section-title">
            Gợi ý cho bạn
          </h2>
          {subtitle && <p className="mt-1 text-sm text-muted">{subtitle}</p>}
        </div>
        <Link to="/search" className="shelf-link">
          Xem kho phim
        </Link>
      </div>
      {controls && <div className="mb-4">{controls}</div>}
      {loading ? (
        <div role="status" className="film-grid" aria-label="Đang tải gợi ý">
          <span className="sr-only">Đang tải gợi ý…</span>
          {Array.from({ length: 3 }).map((_, index) => (
            <div key={index} className="series-skeleton-card overflow-hidden animate-pulse">
              <div className="aspect-video w-full bg-surface-elevated" />
              <div className="space-y-2.5 p-4">
                <div className="h-5 w-3/4 rounded bg-surface-elevated" />
                <div className="h-3.5 w-1/2 rounded bg-surface-elevated" />
                <div className="h-3.5 w-2/3 rounded bg-surface-elevated" />
              </div>
            </div>
          ))}
        </div>
      ) : onRetry ? (
        <div className="shelf-message" role="alert">
          Chưa tải được gợi ý phim.
          <button type="button" className="btn-secondary" onClick={onRetry}>
            Thử lại
          </button>
        </div>
      ) : movies.length ? (
        <FilmGrid movies={movies} />
      ) : (
        <div className="shelf-message" data-testid="film-shelf-empty">
          <span>{emptyMessage || 'Chưa có phim nào ở mục này.'}</span>
          {onResetEmpty && (
            <button type="button" className="btn-secondary" onClick={onResetEmpty}>
              Xem tất cả gợi ý
            </button>
          )}
        </div>
      )}
    </section>
  );
}
