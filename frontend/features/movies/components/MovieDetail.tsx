import { PlaybackReportButton } from '@/features/series/components/PlaybackReportButton';
import { useAuth } from '@/lib/context/AuthContext';
import { YouTubeLink } from '@/components/ui/YouTubeLink';
import { safeImageUrl, resolveStreamUrl } from '@/lib/safeUrl';
import React, { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { AlertCircle, Film, Heart, Loader2, Play, RefreshCw, Star } from 'lucide-react';
import {
  cleanMediaTitle,
  formatDurationMs,
  formatGenreList,
  formatRuntime,
  formatYear,
  movieSynopsis,
  resolveRatingSummary,
} from '@/lib/labels';
import { isMediaExternalOnly, resolveExternalYoutubeWatchUrl } from '@/lib/services/playbackSource';
import type { Movie } from '@/types/movie';
import { useWatchlist } from '@/lib/context/WatchlistContext';
import { getMediaDescriptor, type MediaDescriptor } from '@/lib/services/mediaService';
import * as movieProgress from '@/lib/services/movieProgress';

const ACTION_BUTTON_CLASS =
  'min-h-[44px] inline-flex items-center justify-center rounded-lg border cursor-pointer disabled:opacity-60 transition-all';
const PLAY_BUTTON_LAYOUT =
  'min-h-[44px] px-4 py-2 text-xs font-semibold inline-flex items-center justify-center gap-1.5 cursor-pointer';
const PRIMARY_PLAY_BUTTON_CLASS = `btn-primary ${PLAY_BUTTON_LAYOUT}`;
const PRIMARY_TRAILER_BUTTON_CLASS = `btn-primary min-h-[44px] ${PLAY_BUTTON_LAYOUT}`;
const SECONDARY_PLAY_BUTTON_CLASS = `btn-secondary text-muted ${PLAY_BUTTON_LAYOUT}`;
const SQUARE_BUTTON_CLASS =
  'flex h-11 w-11 min-h-[44px] min-w-[44px] items-center justify-center rounded-lg cursor-pointer transition-colors';
const ICON_BUTTON_CLASS = `${SQUARE_BUTTON_CLASS} text-ink hover:bg-surface-hover`;
const PLAY_PAUSE_BUTTON_CLASS = `${SQUARE_BUTTON_CLASS} bg-brand text-zinc-950 font-bold hover:bg-brand-hover`;
const UNCONFIRMED_TONE_CLASS = 'border-amber-400 bg-amber-400/20 text-amber-900';
const ALERT_BANNER_CLASS =
  'mt-4 flex items-center justify-between gap-2 rounded-lg border border-amber-500/40 bg-amber-500/10 p-3 text-xs text-amber-900';
const ALERT_BUTTON_CLASS =
  'flex min-h-[44px] items-center gap-1 rounded bg-amber-500/20 px-3 py-1 text-xs font-bold text-amber-900 cursor-pointer shrink-0';
const SLIDER_CLASS = 'h-2 rounded cursor-pointer bg-surface-elevated accent-amber-600 min-h-[44px]';
const FOCUSABLE_SELECTOR = 'button:not([disabled]), input:not([disabled]), video, [tabindex]:not([tabindex="-1"])';
const RATING_SCALE = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10] as const;
const RATING_BUTTON_BASE =
  'flex h-11 w-11 min-h-[44px] min-w-[44px] items-center justify-center rounded-lg text-xs font-bold cursor-pointer disabled:opacity-60 transition-colors';
const RATING_BUTTON_ACTIVE = `${RATING_BUTTON_BASE} bg-amber-400 text-black shadow-sm`;
const RATING_BUTTON_IDLE = `${RATING_BUTTON_BASE} border border-surface-border bg-surface-muted text-muted hover:bg-surface-elevated hover:text-ink`;
const WATCHLIST_BUTTON_BASE = `${ACTION_BUTTON_CLASS} px-4 py-2 text-xs font-semibold`;
const WATCHLIST_BUTTON_SAVED = `${WATCHLIST_BUTTON_BASE} btn-secondary text-brand-dark border-brand/50 bg-brand/10`;
const WATCHLIST_BUTTON_DEFAULT = `${WATCHLIST_BUTTON_BASE} btn-secondary text-muted`;
const WATCHLIST_BUTTON_UNCONFIRMED = `${WATCHLIST_BUTTON_BASE} ${UNCONFIRMED_TONE_CLASS}`;
const FAVORITE_BUTTON_BASE = `${ACTION_BUTTON_CLASS} min-w-[44px] p-2`;
const FAVORITE_BUTTON_SAVED = `${FAVORITE_BUTTON_BASE} border-brand bg-brand/15 text-brand-dark`;
const FAVORITE_BUTTON_DEFAULT = `${FAVORITE_BUTTON_BASE} border-surface-border bg-surface text-muted hover:bg-surface-elevated hover:text-ink`;
const FAVORITE_BUTTON_UNCONFIRMED = `${FAVORITE_BUTTON_BASE} ${UNCONFIRMED_TONE_CLASS}`;
const CARD_CLASS = 'rounded-lg border border-surface-border bg-surface p-4';
const STREAM_ERROR_MESSAGE = 'Chưa phát được video này. Bạn thử lại nhé.';
const PROGRESS_SAVE_MIN_DELTA_MS = 5000;
const SEEK_STEP_SEC = 5;
const VOLUME_STEP = 0.1;
const MODAL_FOCUS_DELAY_MS = 40;

const formatTime = (seconds: number) => formatDurationMs(seconds > 0 ? seconds * 1000 : 0);

function readResumeSeconds(movieId: string, userId: number | null): number {
  if (typeof movieProgress.getMovieResumeSeconds !== 'function') return 0;
  try {
    const seconds = movieProgress.getMovieResumeSeconds(movieId, userId);
    return Number.isFinite(seconds) && seconds > 0 ? seconds : 0;
  } catch {
    return 0;
  }
}

function saveFilmProgress(movie: Movie, seconds: number, durationSeconds: number, completed: boolean, userId: number | null) {
  if (typeof movieProgress.saveMovieProgress !== 'function') return;
  const positionMs = Math.max(0, Math.round(seconds * 1000));
  const durationMs = Number.isFinite(durationSeconds) && durationSeconds > 0 ? Math.round(durationSeconds * 1000) : null;
  try {
    movieProgress.saveMovieProgress({ movieId: movie.id, title: movie.title, positionMs, durationMs, completed }, Date.now(), userId);
  } catch {}
}

function Icon({ d, fill = 'none' }: { d: string; fill?: string }) {
  return (
    <svg width={18} height={18} viewBox="0 0 24 24" fill={fill} stroke="currentColor" strokeWidth={2}>
      <path d={d} />
    </svg>
  );
}

function SimilarMovieCard({ movie }: { movie: Movie }) {
  const rating = resolveRatingSummary(movie);
  const title = cleanMediaTitle(movie.title);
  const meta = [formatYear(movie.year), formatRuntime(movie.runtime)].filter(Boolean).join(' · ');

  return (
    <div className="w-[155px] shrink-0 sm:w-[185px]">
      <div className="overflow-hidden rounded-lg border border-surface-border bg-surface">
        <div className="relative aspect-[2/3] w-full overflow-hidden bg-surface-elevated group">
          {movie.poster?.trim() ? (
            <img
              src={safeImageUrl(movie.poster)}
              alt={title}
              loading="lazy"
              referrerPolicy="no-referrer"
              className="absolute inset-0 h-full w-full object-cover"
            />
          ) : (
            <div className="flex h-full w-full flex-col items-center justify-center p-3 text-center bg-mint text-muted">
              <Film size={24} className="mb-1 text-muted" />
              <span className="text-[11px] font-medium leading-tight line-clamp-2 text-muted">{title}</span>
            </div>
          )}
          {rating.hasRating && (
            <span className="absolute top-2 left-2 z-10 flex items-center gap-1 rounded bg-surface/90 px-1.5 py-0.5 text-[10px] font-bold text-ink border border-surface-border">
              <Star size={10} className="fill-amber-400 text-amber-500" aria-hidden="true" /> {rating.compactLabel}
            </span>
          )}
          <Link to={`/movies/${encodeURIComponent(movie.id)}`} aria-label={cleanMediaTitle(movie.title)} className="absolute inset-0 z-10" />
        </div>
        <div className="p-3">
          <Link to={`/movies/${encodeURIComponent(movie.id)}`} className="block truncate text-sm font-semibold text-ink hover:text-brand-dark">
            {title}
          </Link>
          {meta && (
            <div className="mt-1 flex items-center justify-between text-xs text-muted">
              <span>{meta}</span>
            </div>
          )}
          {!rating.hasRating && <p className="mt-1 text-[11px] text-muted">{rating.compactLabel}</p>}
        </div>
      </div>
    </div>
  );
}

export function MovieDetail({ movie, similarMovies }: { movie: Movie; similarMovies: Movie[] }) {
  const { status, user } = useAuth();
  const progressOwner = status === 'guest' ? null : status === 'authenticated' ? user?.id : undefined;
  const {
    isWatchlisted,
    toggleWatchlist,
    isFavorite,
    toggleFavorite,
    getRating,
    rateMovie,
    removeRating,
    isActionPending,
    isActionUnconfirmed,
    getActionIntent,
    getUnconfirmedRating,
    openChat,
  } = useWatchlist();

  const [hoverRating, setHoverRating] = useState<number | null>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [posterError, setPosterError] = useState(false);
  const [backdropError, setBackdropError] = useState(false);
  const [descriptor, setDescriptor] = useState<MediaDescriptor | null>(null);
  const [descriptorError, setDescriptorError] = useState<string | null>(null);
  const [descriptorRetryCount, setDescriptorRetryCount] = useState(0);
  const [activeMediaKind, setActiveMediaKind] = useState<'feature' | 'trailer'>('trailer');
  const [activeMediaUrl, setActiveMediaUrl] = useState('');
  const [playerState, setPlayerState] = useState<'idle' | 'loading' | 'buffering' | 'playing' | 'paused' | 'error'>('idle');
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [volume, setVolume] = useState(1);
  const [isMuted, setIsMuted] = useState(false);
  const [playbackError, setPlaybackError] = useState<string | null>(null);
  const [retryTime, setRetryTime] = useState(0);

  const videoRef = useRef<HTMLVideoElement>(null);
  const videoModalRef = useRef<HTMLDivElement>(null);
  const playButtonRef = useRef<HTMLButtonElement>(null);
  const handlePlayerKey = useRef<(event: KeyboardEvent) => void>(() => {});
  const playerOpenerRef = useRef<HTMLElement | null>(null);
  const resumeSecondsRef = useRef(0);
  const lastSavedMsRef = useRef(0);

  useEffect(() => {
    setDescriptor(null);
    setDescriptorError(null);
    setIsPlaying(false);
    setActiveMediaUrl('');
    setPlayerState('idle');
    setPlaybackError(null);
    setCurrentTime(0);
    setDuration(0);
    setRetryTime(0);

    const abortController = typeof AbortController !== 'undefined' ? new AbortController() : null;
    let mounted = true;
    const fail = () => {
      setDescriptor(null);
      setDescriptorError('Chưa tải được nguồn phát cho phim này.');
    };

    getMediaDescriptor(movie.id, { signal: abortController?.signal })
      .then((loadedDescriptor) => {
        if (!mounted || abortController?.signal.aborted) return;
        const valid =
          loadedDescriptor &&
          loadedDescriptor.movieId === movie.id &&
          typeof loadedDescriptor.hasFeature === 'boolean' &&
          typeof loadedDescriptor.hasTrailer === 'boolean' &&
          (!loadedDescriptor.hasFeature || loadedDescriptor.feature?.streamUrl) &&
          (!loadedDescriptor.hasTrailer || loadedDescriptor.trailer?.streamUrl);
        if (valid) {
          setDescriptor(loadedDescriptor);
          setDescriptorError(null);
        } else {
          fail();
        }
      })
      .catch((error: unknown) => {
        if (!mounted || abortController?.signal.aborted) return;
        fail();
        if (error instanceof Error && error.name === 'TimeoutError') setDescriptorError(error.message);
      });

    return () => {
      mounted = false;
      abortController?.abort();
    };
  }, [movie.id, descriptorRetryCount]);

  const hasVideo = Boolean(resolveStreamUrl(movie.videoUrl || ''));
  const hasValidFeature = Boolean(descriptor?.movieId === movie.id && descriptor.hasFeature);
  const hasValidTrailer = Boolean(descriptor?.movieId === movie.id && descriptor.hasTrailer);

  const startPlayback = (kind: 'feature' | 'trailer', opener?: HTMLElement | null) => {
    if (opener) playerOpenerRef.current = opener;
    setActiveMediaKind(kind);
    const url = kind === 'feature' ? descriptor?.feature?.streamUrl : descriptor?.trailer?.streamUrl || movie.videoUrl;
    const safeUrl = resolveStreamUrl(url || '');
    if (!safeUrl) return;
    const resumeSeconds = kind === 'feature' && progressOwner !== undefined ? readResumeSeconds(movie.id, progressOwner) : 0;
    resumeSecondsRef.current = resumeSeconds;
    lastSavedMsRef.current = Math.round(resumeSeconds * 1000);
    setActiveMediaUrl(safeUrl);
    setPlayerState('buffering');
    setPlaybackError(null);
    setCurrentTime(resumeSeconds);
    setRetryTime(0);
    setIsPlaying(true);
  };

  const rememberPosition = (seconds: number, durationSeconds: number, completed: boolean, force = false) => {
    if (activeMediaKind !== 'feature' || progressOwner === undefined) return;
    const positionMs = Math.max(0, Math.round(seconds * 1000));
    if (!force && !completed && Math.abs(positionMs - lastSavedMsRef.current) < PROGRESS_SAVE_MIN_DELTA_MS) return;
    lastSavedMsRef.current = positionMs;
    saveFilmProgress(movie, seconds, durationSeconds, completed, progressOwner);
  };

  const closePlayer = () => {
    const video = videoRef.current;
    if (
      video &&
      Number.isFinite(video.currentTime) &&
      video.currentTime > 0 &&
      !video.ended &&
      (!Number.isFinite(video.duration) || video.duration <= 0 || video.currentTime < video.duration)
    ) {
      rememberPosition(video.currentTime, video.duration || duration, false, true);
    }
    setIsPlaying(false);
  };

  const handleRetry = () => {
    setPlaybackError(null);
    setPlayerState('buffering');
    const video = videoRef.current;
    if (!video) return;
    video.load();
    video.addEventListener(
      'loadedmetadata',
      () => {
        if (retryTime > 0) {
          try {
            video.currentTime = retryTime;
          } catch {}
        }
        video.play().catch(() => setPlayerState('paused'));
      },
      { once: true }
    );
  };

  const togglePlayPause = () => {
    const video = videoRef.current;
    if (!video) return;
    if (video.paused) {
      video.play().catch(() => setPlayerState('paused'));
      setPlayerState('playing');
    } else {
      video.pause();
      setPlayerState('paused');
    }
  };

  const toggleMute = () => {
    const video = videoRef.current;
    if (video) setIsMuted((video.muted = !video.muted));
  };

  const toggleFullscreen = () => {
    const modal = videoModalRef.current;
    if (modal) (document.fullscreenElement ? document.exitFullscreen() : modal.requestFullscreen()).catch(() => {});
  };

  const seekBy = (seconds: number) => {
    const video = videoRef.current;
    if (video) setCurrentTime((video.currentTime = Math.min(Math.max(0, video.currentTime + seconds), video.duration || duration || 100)));
  };

  const adjustVolume = (delta: number) => {
    const video = videoRef.current;
    if (!video) return;
    const nextVolume = Math.min(Math.max(0, video.volume + delta), 1);
    setVolume(nextVolume);
    setIsMuted((video.muted = !nextVolume));
    video.volume = nextVolume;
  };

  handlePlayerKey.current = (e: KeyboardEvent) => {
    if (e.key === 'Escape') {
      e.preventDefault();
      closePlayer();
      return;
    }
    if (e.key === 'Tab' && videoModalRef.current) {
      const focusable = videoModalRef.current.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR);
      if (!focusable.length) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (
        !videoModalRef.current.contains(document.activeElement) ||
        (e.shiftKey ? document.activeElement === first : document.activeElement === last)
      ) {
        e.preventDefault();
        (e.shiftKey ? last : first).focus();
      }
      return;
    }

    const target = e.target as HTMLElement | null;
    if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable)) return;
    if (e.key === ' ' || e.code === 'Space') {
      if (target?.tagName !== 'BUTTON') {
        e.preventDefault();
        togglePlayPause();
      }
    } else if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') {
      e.preventDefault();
      seekBy(e.key === 'ArrowLeft' ? -SEEK_STEP_SEC : SEEK_STEP_SEC);
    } else if (e.key === 'ArrowUp' || e.key === 'ArrowDown') {
      e.preventDefault();
      adjustVolume(e.key === 'ArrowUp' ? VOLUME_STEP : -VOLUME_STEP);
    } else if (e.key.toLowerCase() === 'm') {
      e.preventDefault();
      toggleMute();
    } else if (e.key.toLowerCase() === 'f') {
      e.preventDefault();
      toggleFullscreen();
    }
  };

  useEffect(() => {
    if (isPlaying && activeMediaUrl) {
      videoRef.current?.play()?.catch((err: unknown) => {
        if ((err as { name?: unknown } | null)?.name !== 'AbortError') setPlayerState('paused');
      });
    }
  }, [isPlaying, activeMediaUrl]);

  useEffect(() => {
    if (!isPlaying) return;
    if (document.activeElement && !videoModalRef.current?.contains?.(document.activeElement)) {
      playerOpenerRef.current = document.activeElement as HTMLElement | null;
    }

    const originalOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    const timer = setTimeout(() => {
      const focusable = videoModalRef.current?.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR);
      if (focusable?.length) focusable[0].focus();
    }, MODAL_FOCUS_DELAY_MS);

    const handleKeyDown = (event: KeyboardEvent) => handlePlayerKey.current(event);

    window.addEventListener('keydown', handleKeyDown);
    return () => {
      clearTimeout(timer);
      document.body.style.overflow = originalOverflow;
      window.removeEventListener('keydown', handleKeyDown);
      (playerOpenerRef.current || playButtonRef.current)?.focus();
    };
  }, [isPlaying]);

  const movieId = movie.id;
  const reportEpisodeId = (movie.episode_id ?? movie.episodeId ?? movie.id).trim();
  const posterUrl = safeImageUrl(movie.poster);
  const backdropUrl = safeImageUrl(movie.backdrop);
  const displayTitle = cleanMediaTitle(movie.title);
  const ratingSummary = resolveRatingSummary(movie);
  const movieYearText = formatYear(movie.year);
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
  const headerMeta = [movieYearText, formatRuntime(movie.runtime), genresText].filter(Boolean).join(' · ');
  const infoMeta = [movieYearText, formatRuntime(movie.runtime)].filter(Boolean).join(' · ');

  const inWatchlist = isWatchlisted(movieId);
  const favorite = isFavorite(movieId);
  const currentRating = getRating(movieId);
  const isWatchlistPending = isActionPending(movieId, 'watchlist');
  const isFavoritePending = isActionPending(movieId, 'favorite');
  const isRatingPending = isActionPending(movieId, 'rating');
  const isWatchlistUnconfirmed = isActionUnconfirmed(movieId, 'watchlist');
  const isAddingToWatchlist = getActionIntent(movieId, 'watchlist') === 'add';
  const isFavoriteUnconfirmed = isActionUnconfirmed(movieId, 'favorite');
  const isAddingToFavorites = getActionIntent(movieId, 'favorite') === 'add';
  const isRatingUnconfirmed = isActionUnconfirmed(movieId, 'rating');
  const isSavingRating = getActionIntent(movieId, 'rating') === 'rate';
  const unconfirmedRatingScore = getUnconfirmedRating(movieId);

  const watchlistAriaLabel = isWatchlistPending
    ? 'Đang lưu…'
    : isWatchlistUnconfirmed
    ? isAddingToWatchlist
      ? 'Chưa lưu được vào Xem sau, nhấn để thử lại'
      : 'Chưa bỏ được khỏi Xem sau, nhấn để thử lại'
    : inWatchlist
    ? 'Bỏ khỏi Xem sau'
    : 'Thêm vào Xem sau';

  const favoriteAriaLabel = isFavoritePending
    ? 'Đang lưu…'
    : isFavoriteUnconfirmed
    ? isAddingToFavorites
      ? 'Chưa thêm được vào Yêu thích, nhấn để thử lại'
      : 'Chưa bỏ được khỏi Yêu thích, nhấn để thử lại'
    : favorite
    ? 'Bỏ khỏi Yêu thích'
    : 'Thêm vào Yêu thích';

  return (
    <main id="main-content" tabIndex={-1} className="min-h-screen bg-background">
      {isPlaying && (
        <div
          ref={videoModalRef}
          role="dialog"
          aria-modal="true"
          aria-labelledby="video-player-title"
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/95 p-4"
        >
          <div className="relative w-full max-w-5xl overflow-hidden rounded-lg border border-surface-border bg-surface flex flex-col">
            <div className="flex items-center justify-between border-b border-surface-border bg-surface-muted px-4 py-3">
              <div className="flex items-center gap-2 truncate">
                <span className="h-2 w-2 rounded-full bg-brand shrink-0" />
                <h3 id="video-player-title" className="text-sm font-bold text-ink truncate">
                  {displayTitle}
                </h3>
                <span className="text-xs text-muted shrink-0">
                  • {activeMediaKind === 'feature' ? 'Phim chính' : 'Đoạn giới thiệu'}
                </span>
              </div>
              <div className="flex items-center gap-2">
                <PlaybackReportButton episodeId={reportEpisodeId}
                  assetId={(activeMediaKind === 'feature' ? descriptor?.feature?.assetId : descriptor?.trailer?.assetId) ?? null} />
                <button onClick={closePlayer} className={ICON_BUTTON_CLASS} aria-label="Đóng trình phát">
                  ✕
                </button>
              </div>
            </div>

            <div className="relative aspect-video w-full bg-black flex items-center justify-center overflow-hidden">
              <video
                ref={videoRef}
                src={activeMediaUrl}
                autoPlay
                playsInline
                preload="metadata"
                onLoadedMetadata={(e) => {
                  const video = videoRef.current;
                  const nextDuration = e?.currentTarget?.duration || video?.duration || 0;
                  setDuration(nextDuration);
                  const startAt = retryTime > 0 ? retryTime : resumeSecondsRef.current;
                  if (startAt > 0 && video && startAt < (nextDuration || startAt + 1)) {
                    try {
                      video.currentTime = startAt;
                    } catch {}
                    setCurrentTime(startAt);
                  }
                }}
                onTimeUpdate={() => {
                  const seconds = videoRef.current?.currentTime || 0;
                  setCurrentTime(seconds);
                  rememberPosition(seconds, videoRef.current?.duration || duration, false);
                }}
                onWaiting={() => setPlayerState('buffering')}
                onPlaying={() => setPlayerState('playing')}
                onPause={() => {
                  if (playerState !== 'buffering' && playerState !== 'error') setPlayerState('paused');
                  rememberPosition(videoRef.current?.currentTime || 0, videoRef.current?.duration || duration, false, true);
                }}
                onEnded={() => {
                  setPlayerState('paused');
                  const total = videoRef.current?.duration || duration || 0;
                  rememberPosition(total, total, true, true);
                }}
                onError={() => {
                  setPlayerState('error');
                  setRetryTime(videoRef.current?.currentTime || 0);
                  setPlaybackError(STREAM_ERROR_MESSAGE);
                }}
                className="h-full w-full object-contain cursor-pointer"
                onClick={togglePlayPause}
              />

              {playerState === 'buffering' && (
                <div data-testid="video-buffering" className="absolute inset-0 flex items-center justify-center bg-black/40 pointer-events-none">
                  <Loader2 size={36} className="animate-spin text-brand" />
                </div>
              )}

              {playerState === 'error' && (
                <div data-testid="video-error-overlay" className="absolute inset-0 flex flex-col items-center justify-center bg-black/85 p-6 text-center z-20">
                  <AlertCircle size={44} className="text-amber-400 mb-3" />
                  <p className="text-xs text-zinc-200 mb-4">{playbackError || STREAM_ERROR_MESSAGE}</p>
                  <button data-testid="video-retry-btn" onClick={handleRetry} className={PRIMARY_PLAY_BUTTON_CLASS}>
                    <RefreshCw size={16} /> Thử lại ({formatTime(retryTime)})
                  </button>
                </div>
              )}
            </div>

            <div className="flex flex-col gap-2 border-t border-surface-border bg-surface-muted/95 p-3 sm:px-4">
              <div className="flex items-center gap-3">
                <span data-testid="video-time-display" className="text-xs font-mono text-muted shrink-0 min-w-[80px]">
                  {formatTime(currentTime)} / {formatTime(duration)}
                </span>
                <input
                  data-testid="video-timeline"
                  type="range"
                  min={0}
                  max={duration || 100}
                  step="0.1"
                  value={currentTime}
                  onChange={(e) => {
                    const nextTime = +e.target.value;
                    setCurrentTime(nextTime);
                    if (videoRef.current) videoRef.current.currentTime = nextTime;
                  }}
                  aria-label="Thanh tua video"
                  className={`w-full ${SLIDER_CLASS}`}
                />
              </div>

              <div className="flex items-center justify-between pt-1">
                <div className="flex items-center gap-2">
                  <button
                    data-testid="video-play-pause-btn"
                    onClick={togglePlayPause}
                    className={PLAY_PAUSE_BUTTON_CLASS}
                    aria-label={playerState === 'playing' ? 'Tạm dừng video' : 'Phát video'}
                  >
                    {playerState === 'playing' ? (
                      <Icon d="M6 4h4v16H6zM14 4h4v16h-4z" fill="currentColor" />
                    ) : (
                      <Play size={18} className="fill-current" />
                    )}
                  </button>

                  <div className="flex items-center gap-1.5 ml-2">
                    <button
                      data-testid="video-volume-btn"
                      onClick={toggleMute}
                      className={ICON_BUTTON_CLASS}
                      aria-label={isMuted || volume === 0 ? 'Bật âm thanh' : 'Tắt âm thanh'}
                    >
                      <Icon
                        d={
                          isMuted || !volume
                            ? 'M11 5L6 9H2v6h4l5 4V5zM22 9l-6 6M16 9l6 6'
                            : 'M11 5L6 9H2v6h4l5 4V5zM15.54 8.46a5 5 0 0 1 0 7.07'
                        }
                      />
                    </button>
                    <input
                      type="range"
                      min={0}
                      max={1}
                      step={0.05}
                      value={isMuted ? 0 : volume}
                      onChange={(e) => {
                        const nextVolume = +e.target.value;
                        const video = videoRef.current;
                        setVolume(nextVolume);
                        setIsMuted(!nextVolume);
                        if (video) {
                          video.volume = nextVolume;
                          video.muted = !nextVolume;
                        }
                      }}
                      aria-label="Thanh âm lượng"
                      className={`w-16 sm:w-20 ${SLIDER_CLASS}`}
                    />
                  </div>
                </div>

                <button data-testid="video-fullscreen-btn" onClick={toggleFullscreen} className={ICON_BUTTON_CLASS} aria-label="Toàn màn hình">
                  <Icon d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7" />
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      <section className="relative overflow-hidden border-b border-surface-border bg-background">
        <div className="absolute inset-0 z-0 h-full w-full">
          {backdropUrl && !backdropError ? (
            <img
              src={backdropUrl}
              alt={displayTitle}
              referrerPolicy="no-referrer"
              fetchPriority="high"
              onError={() => setBackdropError(true)}
              className="absolute inset-0 h-full w-full object-cover opacity-80"
            />
          ) : (
            <div className="h-full w-full bg-surface-elevated" />
          )}
          <div className="absolute inset-0 bg-gradient-to-t from-background via-background/80 to-transparent" />
        </div>

        <div className="page-shell relative z-10 pb-12 pt-6 sm:pt-10">
          <Link to="/" className="inline-flex min-h-[44px] items-center gap-1 text-xs font-medium text-ink/80 hover:text-ink mb-6">
            &larr; Trang chủ
          </Link>

          <div className="flex flex-col gap-6 md:flex-row md:items-end">
            <div className="relative shrink-0">
              <div className="relative aspect-[2/3] w-44 overflow-hidden rounded-lg border border-surface-border bg-surface sm:w-56">
                {posterUrl && !posterError ? (
                  <img
                    src={posterUrl}
                    alt={displayTitle}
                    loading="lazy"
                    referrerPolicy="no-referrer"
                    onError={() => setPosterError(true)}
                    className="absolute inset-0 h-full w-full object-cover"
                  />
                ) : (
                  <div data-testid="movie-poster-placeholder" className="flex h-full w-full flex-col items-center justify-center p-4 text-center bg-mint">
                    <Film size={36} className="mb-2 text-muted" />
                    <span className="text-xs text-muted">{displayTitle}</span>
                  </div>
                )}
                {ratingSummary.hasRating && (
                  <span
                    data-testid="movie-poster-rating"
                    className="absolute top-2 left-2 flex items-center gap-1 rounded bg-black/80 px-2 py-0.5 text-xs font-bold text-amber-400"
                  >
                    <Star size={12} className="fill-amber-400" aria-hidden="true" /> {ratingSummary.compactLabel}
                  </span>
                )}
              </div>
            </div>

            <div className="flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <span className="rounded bg-brand px-2 py-0.5 font-bold text-zinc-950 text-[10px]">Phim hoạt hình</span>
                {externalOnly && (
                  <span
                    data-testid="movie-detail-external-badge"
                    className="rounded border border-amber-500/40 bg-amber-500/10 px-2 py-0.5 text-[11px] font-semibold text-amber-900"
                  >
                    Chỉ mở trên YouTube
                  </span>
                )}
              </div>

              <h1 className="mt-3 text-3xl font-extrabold text-ink sm:text-5xl">{displayTitle}</h1>

              <div className="mt-3 flex flex-wrap items-center gap-3 text-xs sm:text-sm text-muted">
                <span data-testid="movie-detail-rating" className="flex items-center gap-1 text-ink font-bold">
                  {ratingSummary.hasRating && <Star size={14} className="fill-amber-400 text-amber-600" aria-hidden="true" />}
                  {ratingSummary.label}
                </span>
                {headerMeta && <span>{headerMeta}</span>}
              </div>

              <p className="mt-4 max-w-2xl text-sm leading-relaxed text-ink">{movieSynopsis(movie.description)}</p>

              {descriptorError && (
                <div role="alert" className={ALERT_BANNER_CLASS}>
                  <div className="flex items-center gap-2 truncate">
                    <AlertCircle size={15} className="text-amber-800 shrink-0" />
                    <span className="truncate">{descriptorError}</span>
                  </div>
                  <button
                    type="button"
                    data-testid="descriptor-retry-btn"
                    onClick={() => {
                      setDescriptorError(null);
                      setDescriptorRetryCount((n) => n + 1);
                    }}
                    className={ALERT_BUTTON_CLASS}
                  >
                    <RefreshCw size={13} /> Thử lại
                  </button>
                </div>
              )}

              <div className="mt-6 flex flex-wrap items-center gap-3">
                {externalOnly && externalYoutubeUrl && (
                  <YouTubeLink
                    url={externalYoutubeUrl}
                    testId="movie-detail-open-youtube-link"
                    className={PRIMARY_PLAY_BUTTON_CLASS}
                  >
                    Mở trên YouTube
                  </YouTubeLink>
                )}

                {externalOnly && <PlaybackReportButton episodeId={reportEpisodeId} assetId={null} />}

                {hasValidFeature && (
                  <button
                    ref={playButtonRef}
                    data-testid="play-feature-btn"
                    onClick={(e) => startPlayback('feature', e.currentTarget)}
                    className={PRIMARY_PLAY_BUTTON_CLASS}
                  >
                    <Play size={16} className="fill-current" />
                    Xem phim
                  </button>
                )}

                {(hasValidTrailer || hasVideo) && (
                  <button
                    ref={hasValidFeature ? undefined : playButtonRef}
                    data-testid="play-trailer-btn"
                    onClick={(e) => startPlayback('trailer', e.currentTarget)}
                    className={hasValidFeature || descriptorError ? SECONDARY_PLAY_BUTTON_CLASS : PRIMARY_TRAILER_BUTTON_CLASS}
                  >
                    {hasValidTrailer ? <Play size={16} className="fill-current" /> : <Film size={16} />}
                    Xem đoạn giới thiệu
                  </button>
                )}

                <button
                  disabled={isWatchlistPending}
                  onClick={() => void toggleWatchlist(movieId, displayTitle, movie.poster)}
                  className={isWatchlistUnconfirmed ? WATCHLIST_BUTTON_UNCONFIRMED : inWatchlist ? WATCHLIST_BUTTON_SAVED : WATCHLIST_BUTTON_DEFAULT}
                  aria-label={watchlistAriaLabel}
                >
                  {isWatchlistPending ? (
                    <span className="flex items-center gap-1.5">
                      <Loader2 size={15} className="animate-spin text-muted" /> Đang lưu…
                    </span>
                  ) : isWatchlistUnconfirmed ? (
                    <span className="flex items-center gap-1.5 font-bold">
                      <RefreshCw size={15} className="text-amber-800" /> {isAddingToWatchlist ? 'Thử lưu lại' : 'Thử bỏ lại'}
                    </span>
                  ) : inWatchlist ? (
                    'Đã lưu Xem sau'
                  ) : (
                    'Xem sau'
                  )}
                </button>

                <button
                  disabled={isFavoritePending}
                  onClick={() => void toggleFavorite(movieId, displayTitle, movie.poster)}
                  className={isFavoriteUnconfirmed ? FAVORITE_BUTTON_UNCONFIRMED : favorite ? FAVORITE_BUTTON_SAVED : FAVORITE_BUTTON_DEFAULT}
                  aria-label={favoriteAriaLabel}
                >
                  {isFavoritePending ? (
                    <Loader2 size={16} className="animate-spin" />
                  ) : isFavoriteUnconfirmed ? (
                    <RefreshCw size={16} className="text-amber-800 stroke-[2.5]" />
                  ) : (
                    <Heart size={16} className={favorite ? 'fill-current' : ''} aria-hidden="true" />
                  )}
                </button>

                <button onClick={openChat} className={`border border-ink/30 bg-surface text-muted ${PLAY_BUTTON_LAYOUT}`}>
                  Hỏi CineBot
                </button>
              </div>
            </div>
          </div>
        </div>
      </section>

      <div className="page-shell py-10">
        <div className="grid gap-8 lg:grid-cols-[1fr_320px]">
          <div className="space-y-8">
            <div className={CARD_CLASS}>
              <h2 className="text-sm font-bold text-ink">Chấm điểm phim này</h2>

              <div className="mt-4 flex flex-wrap items-center gap-1.5">
                {RATING_SCALE.map((star) => (
                  <button
                    key={star}
                    disabled={isRatingPending}
                    onMouseEnter={() => setHoverRating(star)}
                    onMouseLeave={() => setHoverRating(null)}
                    onClick={() => void rateMovie(movieId, star, movie.title)}
                    className={(hoverRating !== null ? hoverRating >= star : (currentRating || 0) >= star) ? RATING_BUTTON_ACTIVE : RATING_BUTTON_IDLE}
                    aria-label={`Chấm ${star} trên 10 điểm`}
                  >
                    {star}
                  </button>
                ))}
              </div>

              {isRatingUnconfirmed && (
                <div data-testid="rating-unconfirmed-banner" className={ALERT_BANNER_CLASS}>
                  <span className="flex items-center gap-1.5 font-medium">
                    <AlertCircle size={14} className="text-amber-800 shrink-0" />
                    {isSavingRating
                      ? `Chưa lưu được điểm ${unconfirmedRatingScore || currentRating}/10 lên máy chủ.`
                      : 'Chưa xóa được điểm trên máy chủ.'}
                  </span>
                  <button
                    disabled={isRatingPending}
                    onClick={() =>
                      isSavingRating
                        ? void rateMovie(movieId, unconfirmedRatingScore || currentRating || 10, displayTitle)
                        : void removeRating(movieId, displayTitle)
                    }
                    className={ALERT_BUTTON_CLASS}
                    aria-label={isSavingRating ? 'Thử lưu lại điểm' : 'Thử xóa lại điểm'}
                  >
                    <RefreshCw size={12} /> {isSavingRating ? 'Thử lưu lại' : 'Thử xóa lại'}
                  </button>
                </div>
              )}

              {!isRatingUnconfirmed && currentRating && (
                <div className="mt-3 flex items-center justify-between text-xs">
                  <p className="text-amber-900 font-medium">Đã chấm {currentRating}/10.</p>
                  <button
                    type="button"
                    disabled={isRatingPending}
                    onClick={() => void removeRating(movieId, displayTitle)}
                    className="inline-flex min-h-[44px] items-center px-2 text-muted hover:text-red-800 underline text-xs cursor-pointer disabled:opacity-50"
                  >
                    Xóa điểm
                  </button>
                </div>
              )}
            </div>
          </div>

          <div className={`${CARD_CLASS} h-fit space-y-2 text-xs`}>
            <h3 className="font-bold text-muted">Thông tin</h3>
            <p>
              <span className="text-muted">Thể loại: </span>
              {genresText}
            </p>
            <p className="pt-2 border-t border-surface-border">
              <span className="text-muted">Năm / Thời lượng: </span>
              {infoMeta || 'Đang cập nhật'}
            </p>
          </div>
        </div>

        <div className="mt-12 pt-8 border-t border-surface-border">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-sm font-bold text-ink">Phim tương tự</h2>
            <Link to="/search?genre=animation" className="inline-flex min-h-[44px] items-center text-xs text-muted hover:text-brand-dark">
              Tất cả &rarr;
            </Link>
          </div>
          {similarMovies.length > 0 ? (
            <div className="movie-row -mx-4 flex gap-3 overflow-x-auto px-4 pb-3 sm:mx-0 sm:px-0 sm:gap-4 scroll-smooth">
              {similarMovies.map((similarMovie) => (
                <SimilarMovieCard key={similarMovie.id} movie={similarMovie} />
              ))}
            </div>
          ) : (
            <div className="rounded-lg border border-surface-border bg-surface p-6 text-center text-xs text-muted">
              Chưa có phim tương tự nào ở mục này.
            </div>
          )}
        </div>
      </div>
    </main>
  );
}
