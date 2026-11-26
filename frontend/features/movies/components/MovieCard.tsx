import { YouTubeLink } from '@/components/ui/YouTubeLink';
import { useOptionalAuth } from '@/lib/context/AuthContext';
import { safeImageUrl } from '@/lib/safeUrl';
import React, { useState } from 'react';
import { Link } from 'react-router';
import { Bookmark, Check, Film, Heart, Loader2, RefreshCw, Star } from 'lucide-react';
import {
  cleanMediaTitle,
  formatGenreList,
  formatRuntime,
  formatYear,
  resolveRatingSummary,
  toCompactCardArtworkUrl,
} from '@/lib/labels';
import { buildMediaItemHref } from '@/lib/services/mediaRouting';
import { isMediaExternalOnly, resolveExternalYoutubeWatchUrl } from '@/lib/services/playbackSource';
import type { Movie } from '@/types/movie';
import { useWatchlist } from '@/lib/context/WatchlistContext';

type MovieCardProps = {
  movie: Movie;
  fluid?: boolean;
  actionMode?: 'watchlist' | 'favorite';
};

export function MovieCard({ movie, fluid = false, actionMode = 'watchlist' }: MovieCardProps) {
  const {
    isWatchlisted,
    toggleWatchlist,
    isFavorite,
    toggleFavorite,
    isActionPending,
    isActionUnconfirmed,
    getActionIntent,
  } = useWatchlist();
  const auth = typeof useOptionalAuth === 'function' ? useOptionalAuth() : null;
  const isKidMode = Boolean(auth?.isKidMode);
  const [posterError, setPosterError] = useState(false);

  const displayTitle = cleanMediaTitle(movie.title);
  const isFavoriteMode = actionMode === 'favorite';
  const actionKey = isFavoriteMode ? 'favorite' : 'watchlist';
  const isSaved = isFavoriteMode
    ? typeof isFavorite === 'function' && isFavorite(movie.id)
    : isWatchlisted(movie.id);
  const isPending = isActionPending(movie.id, actionKey);
  const isUnconfirmed = isActionUnconfirmed(movie.id, actionKey);
  const unconfirmedIntent = isUnconfirmed ? getActionIntent(movie.id, actionKey) : null;

  const buttonAriaLabel = isPending
    ? `Đang cập nhật ${isFavoriteMode ? 'Yêu thích' : 'Xem sau'} cho ${displayTitle}…`
    : isFavoriteMode
    ? isUnconfirmed
      ? unconfirmedIntent === 'add'
        ? `Chưa thêm được ${displayTitle} vào Yêu thích, nhấn để thử lại`
        : `Chưa bỏ được ${displayTitle} khỏi Yêu thích, nhấn để thử lại`
      : isSaved
      ? `Bỏ ${displayTitle} khỏi Yêu thích`
      : `Thêm ${displayTitle} vào Yêu thích`
    : isUnconfirmed
    ? unconfirmedIntent === 'add'
      ? `Chưa lưu được ${displayTitle} vào Xem sau, nhấn để thử lại`
      : `Chưa bỏ được ${displayTitle} khỏi Xem sau, nhấn để thử lại`
    : isSaved
    ? `Bỏ ${displayTitle} khỏi Xem sau`
    : `Thêm ${displayTitle} vào Xem sau`;

  const buttonTitle = isUnconfirmed
    ? unconfirmedIntent === 'add'
      ? isFavoriteMode
        ? 'Chưa thêm được vào Yêu thích, nhấn để thử lại'
        : 'Chưa lưu được vào Xem sau, nhấn để thử lại'
      : isFavoriteMode
      ? 'Chưa bỏ được khỏi Yêu thích, nhấn để thử lại'
      : 'Chưa bỏ được khỏi Xem sau, nhấn để thử lại'
    : undefined;

  const compactPoster =
    typeof toCompactCardArtworkUrl === 'function' ? toCompactCardArtworkUrl(movie.poster) : movie.poster;
  const hasPoster = Boolean(compactPoster && compactPoster.trim() && !posterError);
  const ratingSummary = resolveRatingSummary(movie);
  const metaLine = [formatYear(movie.year), formatRuntime(movie.runtime)].filter(Boolean).join(' · ');
  const genresText = formatGenreList(movie.genres);
  const externalOnly =
    typeof isMediaExternalOnly === 'function'
      ? isMediaExternalOnly(movie)
      : Boolean(movie.externalOnly || movie.external_only || movie.canEmbed === false || movie.can_embed === false);
  const externalYoutubeUrl = externalOnly
    ? typeof resolveExternalYoutubeWatchUrl === 'function'
      ? resolveExternalYoutubeWatchUrl(movie)
      : movie.youtubeWatchUrl || movie.youtube_watch_url || null
    : null;
  const mediaHref = buildMediaItemHref(movie);

  return (
    <div
      data-reveal-item=""
      className={`group relative block outline-none transition-transform duration-smooth ease-out-smooth hover:-translate-y-1 ${
        fluid ? 'w-full' : 'w-[155px] shrink-0 sm:w-[185px] md:w-[205px]'
      }`}
    >
      <article className="comic-card overflow-hidden rounded-xl border border-surface-border bg-surface transition-all duration-smooth ease-out-smooth hover:border-brand/40 group-hover:shadow-card-hover">
        <div className="relative aspect-[4/5] w-full overflow-hidden bg-surface-elevated">
          {hasPoster ? (
            <img
              src={safeImageUrl(compactPoster || undefined)}
              alt={displayTitle}
              loading="lazy"
              decoding="async"
              referrerPolicy="no-referrer"
              onError={() => setPosterError(true)}
              className="absolute inset-0 h-full w-full object-cover transition-transform duration-300 group-hover:scale-105"
            />
          ) : (
            <div className="flex h-full w-full flex-col items-center justify-center p-3 text-center bg-surface-elevated text-muted">
              <Film size={28} className="mb-2 text-muted" />
              <span className="text-[0.6875rem] font-medium leading-tight line-clamp-2 text-muted">{displayTitle}</span>
            </div>
          )}

          <span
            data-testid="movie-rating-badge"
            className="absolute top-2 left-2 z-10 flex items-center gap-1 rounded bg-black/75 px-2 py-0.5 text-[0.6875rem] font-bold text-amber-300 border border-white/10 backdrop-blur-md"
          >
            {ratingSummary.hasRating ? (
              <>
                <Star size={11} className="fill-amber-400 text-amber-400" aria-hidden="true" />
                {ratingSummary.compactLabel}
              </>
            ) : (
              <span className="font-medium text-paper-white/95">{ratingSummary.compactLabel}</span>
            )}
          </span>

          {isUnconfirmed && (
            <span
              data-testid="unconfirmed-badge"
              className="absolute bottom-2 left-2 z-10 flex items-center gap-1 rounded bg-amber-500/95 px-2 py-0.5 text-[0.625rem] font-bold text-zinc-950 shadow-md border border-amber-300 backdrop-blur-md"
            >
              <RefreshCw size={10} className="stroke-[2.5]" />
              {unconfirmedIntent === 'add' ? 'Chưa lưu' : 'Chưa bỏ'}
            </span>
          )}

          <Link
            to={mediaHref}
            aria-label={`Xem chi tiết ${displayTitle}`}
            className="absolute inset-0 z-10 flex items-center justify-center bg-black/50 opacity-0 transition-opacity duration-200 group-hover:opacity-100 group-focus-within:opacity-100 cursor-pointer"
          >
            <span className="inline-flex items-center gap-1.5 rounded-lg bg-brand px-3.5 py-1.5 text-xs font-bold text-zinc-950 shadow-brand-sm backdrop-blur-sm transition-transform duration-150 hover:scale-105 hover:bg-brand-hover">
              Xem chi tiết
            </span>
          </Link>

          <button
            type="button"
            data-testid={isFavoriteMode ? 'movie-card-favorite-btn' : 'movie-card-watchlist-btn'}
            aria-label={buttonAriaLabel}
            aria-pressed={isSaved}
            title={buttonTitle}
            disabled={isPending}
            onClick={(e) => {
              e.preventDefault();
              e.stopPropagation();
              if (isFavoriteMode) void toggleFavorite(movie.id, displayTitle, movie.poster);
              else void toggleWatchlist(movie.id, displayTitle, movie.poster);
            }}
            className={`absolute top-2 right-2 z-20 flex h-11 w-11 min-h-[44px] min-w-[44px] items-center justify-center rounded-full backdrop-blur-md transition-all duration-150 cursor-pointer focus-visible:ring-2 focus-visible:ring-brand focus-visible:outline-none disabled:opacity-60 ${
              isUnconfirmed
                ? 'bg-amber-400 text-zinc-950 shadow-md border border-amber-300 hover:bg-amber-300 ring-2 ring-amber-400/40'
                : isSaved
                ? isFavoriteMode
                  ? 'bg-[#f2d18c] text-ink border border-ink shadow-brand-sm'
                  : 'bg-brand text-zinc-950 shadow-brand-sm'
                : 'bg-black/60 text-paper-white border border-white/10 hover:bg-brand hover:text-zinc-950 hover:border-brand'
            }`}
          >
            {isPending ? (
              <Loader2 size={15} className="animate-spin" />
            ) : isUnconfirmed ? (
              <RefreshCw size={15} className="stroke-[2.5]" />
            ) : isFavoriteMode ? (
              <Heart size={15} className={isSaved ? 'fill-current' : ''} />
            ) : isSaved ? (
              <Check size={15} />
            ) : (
              <Bookmark size={15} />
            )}
          </button>
        </div>

        <div className="border-t border-surface-border p-3">
          <Link
            to={mediaHref}
            className="block truncate text-sm font-semibold text-ink transition-colors hover:text-brand-dark focus-visible:outline-none focus-visible:text-brand-dark"
          >
            {displayTitle}
          </Link>

          {externalOnly && !isKidMode && (
            <span
              data-testid="movie-external-badge"
              className="mt-1 inline-flex items-center rounded border border-amber-500/40 bg-amber-500/10 px-1.5 py-0.5 text-[0.625rem] font-semibold text-amber-900"
            >
              Chỉ mở trên YouTube
            </span>
          )}

          {metaLine && (
            <div className="mt-1 flex items-center justify-between text-xs text-muted">
              <span>{metaLine}</span>
            </div>
          )}

          {genresText && (
            <p className="mt-1 truncate text-xs text-muted" data-testid="movie-card-genres">
              {genresText}
            </p>
          )}

          {externalOnly && !isKidMode && externalYoutubeUrl && (
            <YouTubeLink
              url={externalYoutubeUrl}
              testId="movie-open-youtube-link"
              className="mt-1 inline-flex min-h-[44px] items-center text-xs font-semibold text-brand-dark underline underline-offset-4"
            >
              Mở trên YouTube
            </YouTubeLink>
          )}
        </div>
      </article>
    </div>
  );
}
