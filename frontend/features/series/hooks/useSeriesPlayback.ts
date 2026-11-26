import { useEffect, useRef, useState } from 'react';
import {
  getCatalogEpisodeDetail,
  getCatalogMovieById,
  getCatalogSeriesDetail,
  type CatalogEpisode,
  type CatalogEpisodeResponse,
  type CatalogMediaAsset,
  type CatalogScene,
  type CatalogSegment,
  type CatalogSeriesResponse,
} from '@/lib/services/catalogService';
import {
  getStoredAuthToken,
  requestPlaybackGrant,
  requestPlaybackGrantResult,
  resolveStreamUrl,
  type PlaybackGrantResult,
} from '@/lib/services/mediaService';
import { resolveFileTimestamp, resolveMediaSeekSeconds } from '@/lib/services/mediaRouting';
import {
  PLAYBACK_METADATA_TIMEOUT_MS,
  classifyGrantFailureReason,
  isUnmountedDriveFallback,
  resolveFallbackStartTimeMs,
  selectPlaybackSource,
  type PlaybackFailureReason,
  type PlaybackRuntimeFailure,
} from '@/lib/services/playbackSource';
import { cleanMediaTitle } from '@/lib/labels';
import { useOptionalAuth } from '@/lib/context/AuthContext';
import { useOptionalScreenTime } from '@/lib/context/ScreenTimeContext';
import { usePlayFeatures } from '@/features/profiles/hooks/usePlayFeatures';
import { useScreenTimeHeartbeat } from '@/features/profiles/hooks/useScreenTimeHeartbeat';
import { fetchKidVipStatus, type KidVipStatus } from '@/lib/services/vipService';
import { RATING_CHANGED_EVENT } from '@/lib/context/WatchlistContext';
import { useWatchProgress, type WatchProgressSaveReason } from '@/lib/hooks/useWatchProgress';
import {
  isProgressCompleted,
  isUsableResumeProgress,
  isYoutubeAssetId,
  readAutoNextPreference,
  toYoutubeAssetId,
  writeAutoNextPreference,
  type WatchProgressDraft,
} from '@/lib/services/watchProgressService';
import {
  formatAdjacentEpisodeLabel,
  getAdjacentEpisode,
  getEpisodesForSeason,
  getInitialEpisode,
  getInitialSeasonId,
  sortCatalogSeasons,
} from '@/lib/services/episodeNavigation';
import type { NextEpisodeRecommendation } from '@/lib/services/nextEpisodeService';
import { getSeriesCardById, type SeriesCard } from '@/lib/services/seriesCardService';
import type {
  YouTubeProgressReason,
  YouTubeProgressSnapshot,
  YouTubeSeekRequest,
} from '@/features/series/components/YouTubePlayer';

const RESUME_MIN_POSITION_MS = 5000;
const RESUME_BACKTRACK_TOLERANCE_MS = 2000;
const COMPLETED_REMAINING_MAX_MS = 60_000;
const AUTO_NEXT_COUNTDOWN_SEC = 8;
const SEEK_END_MARGIN_SEC = 0.5;
const KNOWN_EPISODE_MIN_DURATION_MS = 60_000;

function isEpisodeCompleted(positionMs: number, durationMs: number | null): boolean {
  if (typeof isProgressCompleted === 'function') {
    return isProgressCompleted(positionMs, durationMs);
  }
  if (durationMs === null || !Number.isFinite(durationMs) || durationMs <= 0) return false;
  const total = Math.floor(durationMs);
  const fivePercent = Math.max(1, Math.floor(total / 20));
  const nearEnd = Math.min(COMPLETED_REMAINING_MAX_MS, fivePercent);
  return positionMs >= total - nearEnd;
}

function resolveEffectiveDurationMs(
  episodeDurationMs: number | null | undefined,
  assetDurationMs: number | null | undefined,
  fallbackDurationMs: number | null
): number | null {
  const episodeKnown =
    typeof episodeDurationMs === 'number' && Number.isFinite(episodeDurationMs) && episodeDurationMs >= KNOWN_EPISODE_MIN_DURATION_MS
      ? Math.floor(episodeDurationMs)
      : null;
  const assetKnown =
    typeof assetDurationMs === 'number' && Number.isFinite(assetDurationMs) && assetDurationMs > 0
      ? Math.floor(assetDurationMs)
      : null;
  if (episodeKnown !== null && assetKnown !== null) return Math.min(episodeKnown, assetKnown);
  if (episodeKnown !== null) return episodeKnown;
  if (assetKnown !== null) return assetKnown;
  return fallbackDurationMs !== null && Number.isFinite(fallbackDurationMs) && fallbackDurationMs > 0
    ? Math.floor(fallbackDurationMs)
    : null;
}

function buildDeepLinkKey(params: URLSearchParams): string {
  return [
    params.get('episode') || '',
    params.get('asset') || '',
    params.get('scene') || '',
    params.get('segment') || '',
    params.get('t') || '',
    params.get('timeline') || params.get('timescale') || '',
  ].join('|');
}

type EpisodePlaybackState = {
  started: boolean;
  switchFlushed: boolean;
};

export class EpisodePlaybackTracker {
  private readonly sessions = new Map<string, EpisodePlaybackState>();

  prepareEpisode(episodeId: string | null): void {
    if (!episodeId) return;
    this.sessions.set(episodeId, { started: false, switchFlushed: false });
  }

  markStarted(episodeId: string | null): void {
    if (!episodeId) return;
    this.sessions.set(episodeId, { started: true, switchFlushed: false });
  }

  hasStarted(episodeId: string | null): boolean {
    return Boolean(episodeId && this.sessions.get(episodeId)?.started);
  }

  canSave(episodeId: string | null, reason: WatchProgressSaveReason): boolean {
    if (!episodeId) return false;
    const session = this.sessions.get(episodeId);
    if (!session || !session.started || session.switchFlushed) return false;
    if (reason === 'episode-change') {
      session.switchFlushed = true;
      return true;
    }
    return true;
  }
}

export type EpisodeRatingData = {
  episodeId: string;
  averageRating: number | null;
  ratingCount: number;
};

type PendingSeekIdentity = {
  seriesId: string;
  episodeId: string;
  assetId: string;
  sceneId?: string | null;
  segmentId?: string | null;
  targetSec: number;
  generation: number;
};

export function extractEpisodeRating(
  detail?: CatalogEpisodeResponse | null,
  episode?: CatalogEpisode | null
): { averageRating: number | null; ratingCount: number } | null {
  for (const candidate of [detail, detail?.episode, episode]) {
    if (candidate && (typeof candidate.ratingCount === 'number' || candidate.averageRating !== undefined)) {
      return {
        averageRating: typeof candidate.averageRating === 'number' ? candidate.averageRating : null,
        ratingCount: typeof candidate.ratingCount === 'number' ? candidate.ratingCount : 0,
      };
    }
  }
  return null;
}

function isFullEpisodeAsset(asset: CatalogMediaAsset, episodeId: string): boolean {
  try {
    const coverage = JSON.parse(asset.coverageJson || '[]');
    return (
      Array.isArray(coverage) &&
      coverage.some(
        (item: unknown) =>
          typeof item === 'object' &&
          item !== null &&
          (item as { episode_id?: unknown }).episode_id === episodeId &&
          (item as { scope?: unknown }).scope === 'full_episode'
      )
    );
  } catch {
    return false;
  }
}

export function episodeSearchParams(
  current: URLSearchParams,
  episodeId: string | null,
  resume?: { startMs: number; assetId: string | null },
): URLSearchParams {
  const params = new URLSearchParams(current);
  for (const name of ['episode', 'scene', 'segment', 'timescale', 't', 'timeline', 'asset']) {
    params.delete(name);
  }
  if (episodeId) params.set('episode', episodeId);
  if (resume && resume.startMs > 0) {
    const isYoutube = isYoutubeAssetId(resume.assetId);
    params.set('t', String(resume.startMs));
    params.set('timeline', isYoutube ? 'episode' : 'file');
    if (resume.assetId && !isYoutube) params.set('asset', resume.assetId);
  }
  return params;
}

function seekVideoNow(video: HTMLVideoElement, targetSeconds: number): void {
  const duration = video.duration || 0;
  video.currentTime = duration > 0 ? Math.min(targetSeconds, Math.max(0, duration - SEEK_END_MARGIN_SEC)) : targetSeconds;
  void video.play().catch(() => {});
}

export function useSeriesPlayback(
  seriesId: string,
  searchParams: URLSearchParams,
  setSearchParams: (params: URLSearchParams, options: { replace: boolean; preventScrollReset: boolean }) => void,
) {
  const replaceEpisodeUrl = (episodeId: string | null, resume?: { startMs: number; assetId: string | null }) => {
    setSearchParams(episodeSearchParams(searchParams, episodeId, resume), { replace: true, preventScrollReset: true });
  };
  const targetEpisodeId = searchParams.get('episode');
  const targetSceneId = searchParams.get('scene');
  const targetSegmentId = searchParams.get('segment');
  const targetAssetId = searchParams.get('asset');
  const targetTimeMs = searchParams.get('t');
  const targetTimeline = searchParams.get('timeline') || searchParams.get('timescale');
  const currentUrlDeepLinkKey = buildDeepLinkKey(searchParams);

  const [seriesData, setSeriesData] = useState<CatalogSeriesResponse | null>(null);
  const [seriesCard, setSeriesCard] = useState<SeriesCard | null>(null);
  const [selectedEpisode, setSelectedEpisode] = useState<CatalogEpisode | null>(null);
  const [selectedSeasonId, setSelectedSeasonId] = useState<string | null>(null);
  const [episodeQuery, setEpisodeQuery] = useState('');
  const [episodeDetail, setEpisodeDetail] = useState<CatalogEpisodeResponse | null>(null);
  const [episodeRating, setEpisodeRating] = useState<EpisodeRatingData | null>(null);
  const [knownExternalEpisodeIds, setKnownExternalEpisodeIds] = useState<Record<string, boolean>>({});
  const [preferredAssetId, setPreferredAssetId] = useState<string | null>(null);
  const [manualAssetId, setManualAssetId] = useState<string | null>(null);
  const [runtimeFailure, setRuntimeFailure] = useState<PlaybackRuntimeFailure | null>(null);
  const [fallbackStartMs, setFallbackStartMs] = useState<number | null>(null);
  const [fileRetryCount, setFileRetryCount] = useState(0);
  const [hasLoadedMetadata, setHasLoadedMetadata] = useState(false);
  const [youtubeSeekMs, setYoutubeSeekMs] = useState<number | null>(null);
  const [youtubeSeekRequest, setYoutubeSeekRequest] = useState<YouTubeSeekRequest | null>(null);
  const [isLoadingSeries, setIsLoadingSeries] = useState(true);
  const [isLoadingEpisode, setIsLoadingEpisode] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [seriesRetry, setSeriesRetry] = useState(0);
  const [episodeError, setEpisodeError] = useState<string | null>(null);
  const [episodeRetry, setEpisodeRetry] = useState(0);
  const [playbackWarning, setPlaybackWarning] = useState<string | null>(null);
  const [currentTime, setCurrentTime] = useState(0);
  const [activeSceneId, setActiveSceneId] = useState<string | null>(null);
  const [activeSegmentId, setActiveSegmentId] = useState<string | null>(null);
  const [streamUrl, setStreamUrl] = useState('');
  const [isResolvingStream, setIsResolvingStream] = useState(false);
  const [dismissedResume, setDismissedResume] = useState('');
  const [autoNext, setAutoNext] = useState(false);
  const [nextCountdown, setNextCountdown] = useState<number | null>(null);

  const videoRef = useRef<HTMLVideoElement>(null);
  const youtubeSeekCountRef = useRef(0);
  const lastYoutubeSnapshotRef = useRef<{ episodeId: string | null; currentTimeSec: number; durationSec: number }>({
    episodeId: null,
    currentTimeSec: 0,
    durationSec: 0,
  });
  const playbackTrackerRef = useRef<EpisodePlaybackTracker | null>(null);
  if (!playbackTrackerRef.current) playbackTrackerRef.current = new EpisodePlaybackTracker();
  const fileLeaveFlushRef = useRef<() => void>(() => {});
  const autoPlayEpisodeRef = useRef<string | null>(null);
  const episodeRequestIdRef = useRef(0);
  const seriesRequestIdRef = useRef(0);
  const seekGenerationRef = useRef(0);
  const pendingSeekRef = useRef<PendingSeekIdentity | null>(null);
  const pendingResumeRef = useRef<{ episodeId: string; positionMs: number; assetId?: string | null } | null>(null);
  const deepLinkHandledKeyRef = useRef<string | null>(null);
  const selfHandledUrlKeyRef = useRef<string | null>(null);
  const previousUrlEpisodeIdRef = useRef<string | null>(targetEpisodeId);
  const selectedEpisodeIdRef = useRef<string | null>(null);
  selectedEpisodeIdRef.current = selectedEpisode?.id || null;
  const hasDetailRatingRef = useRef(false);
  const seriesCardAbortRef = useRef<AbortController | null>(null);
  const episodeRatingAbortRef = useRef<AbortController | null>(null);

  const progress = useWatchProgress();
  const auth = typeof useOptionalAuth === 'function' ? useOptionalAuth() : null;
  const screenTime = typeof useOptionalScreenTime === 'function' ? useOptionalScreenTime() : null;
  const isKidMode = Boolean(auth?.isKidMode);
  const isScreenTimeLocked = Boolean(isKidMode && screenTime?.isLocked);
  const [vipStatus, setVipStatus] = useState<KidVipStatus | null>(null);
  const [vipRefreshNonce, setVipRefreshNonce] = useState(0);
  const isVipSeries = Boolean(isKidMode && vipStatus && vipStatus.vipSeriesIds.includes(seriesId));
  const unlockedVipEpisodeIds = vipStatus?.unlockedEpisodeIds ?? [];
  const isSelectedEpisodeVipLocked = Boolean(
    isVipSeries && selectedEpisode?.id && !unlockedVipEpisodeIds.includes(selectedEpisode.id)
  );
  const vipMathLevel = vipStatus?.vipMathLevel || 2;
  const [isMediaPlaying, setIsMediaPlaying] = useState(false);
  const [activeRecapEpisodeId, setActiveRecapEpisodeId] = useState<string | null>(null);
  const playFeatures = usePlayFeatures({
    enabled: isKidMode,
    token: auth?.token ?? null,
    seriesId,
    isPlaying: isMediaPlaying,
    isScreenTimeLocked,
    onScreenTimeUpdated: (next) => screenTime?.applyScreenTimeStatus(next),
  });
  const isPetLocked = Boolean(isKidMode && playFeatures.isPetLocked);
  const isPlaybackBlocked =
    isScreenTimeLocked || isSelectedEpisodeVipLocked || isPetLocked || activeRecapEpisodeId !== null;
  const wasPlaybackBlockedRef = useRef(isPlaybackBlocked);

  useEffect(() => {
    if (!isKidMode || !auth?.token) {
      setVipStatus(null);
      return;
    }
    const controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
    fetchKidVipStatus(auth.token, { signal: controller?.signal })
      .then((next) => {
        if (!controller?.signal.aborted) {
          setVipStatus(next);
        }
      })
      .catch(() => {
        if (!controller?.signal.aborted) setVipStatus(null);
      });
    return () => controller?.abort();
  }, [isKidMode, auth?.token, auth?.activeProfile?.id, vipRefreshNonce]);

  const markVipEpisodeUnlocked = (episodeId: string) => {
    setVipStatus((prev) => {
      if (!prev) return prev;
      if (prev.unlockedEpisodeIds.includes(episodeId)) return prev;
      return {
        ...prev,
        unlockedEpisodeIds: [...prev.unlockedEpisodeIds, episodeId],
      };
    });
    setFileRetryCount((value) => value + 1);
  };

  useScreenTimeHeartbeat({
    enabled: Boolean(isKidMode && !isPlaybackBlocked && screenTime),
    isPlaying: isMediaPlaying,
    onHeartbeat: (seconds) => screenTime?.reportWatchHeartbeat(seconds),
  });

  useEffect(() => {
    if (isPlaybackBlocked) {
      setIsMediaPlaying(false);
      setNextCountdown(null);
      try {
        videoRef.current?.pause();
      } catch {}
    } else if (wasPlaybackBlockedRef.current && selectedAsset?.id && !streamUrl) {
      setFileRetryCount((value) => value + 1);
    }
    wasPlaybackBlockedRef.current = isPlaybackBlocked;
  }, [isPlaybackBlocked]);

  const youtubeTargetMs =
    targetEpisodeId === selectedEpisode?.id
      ? targetTimeMs ??
        episodeDetail?.segments.find((segment) => segment.id === targetSegmentId)?.episodeStartMs ??
        episodeDetail?.scenes.find((scene) => scene.id === targetSceneId)?.startMs
      : 0;
  const youtubeStartMs = youtubeSeekMs ?? fallbackStartMs ?? youtubeTargetMs;

  const playbackSource = selectPlaybackSource({
    episodeDetail,
    selectedEpisodeId: selectedEpisode?.id,
    episodeDurationMs: selectedEpisode?.durationMs,
    preferredAssetId,
    manualAssetId,
    runtimeFailure,
    startTimeMs: youtubeStartMs,
  });

  const selectedAsset = playbackSource.kind === 'file' ? playbackSource.asset : null;
  const youtubeVideoId = playbackSource.kind === 'youtube' ? playbackSource.youtube.videoId : null;
  const activeProgressAssetId = selectedAsset?.id || (youtubeVideoId ? toYoutubeAssetId(youtubeVideoId) : '');
  const progressIdentity = `${selectedEpisode?.id || ''}:${activeProgressAssetId}`;

  const requestYoutubeSeek = (ms: number, autoPlay = true) => {
    const safeMs = Number.isFinite(ms) && ms > 0 ? Math.floor(ms) : 0;
    setYoutubeSeekMs(safeMs);
    setYoutubeSeekRequest({ seconds: safeMs / 1000, nonce: ++youtubeSeekCountRef.current, autoPlay });
  };

  const queueOrSeekVideo = (
    targetSec: number,
    context: { assetId: string; sceneId?: string | null; segmentId?: string | null }
  ) => {
    const seekGeneration = ++seekGenerationRef.current;
    if (videoRef.current && videoRef.current.readyState >= 1) {
      seekVideoNow(videoRef.current, targetSec);
      pendingSeekRef.current = null;
    } else if (selectedEpisode) {
      pendingSeekRef.current = {
        seriesId: String(seriesId),
        episodeId: selectedEpisode.id,
        assetId: context.assetId,
        sceneId: context.sceneId ?? null,
        segmentId: context.segmentId ?? null,
        targetSec,
        generation: seekGeneration,
      };
    }
  };

  const refreshSeriesCard = (targetSeriesId: string, forceRefresh: boolean) => {
    if (!targetSeriesId || typeof getSeriesCardById !== 'function') return;
    seriesCardAbortRef.current?.abort();
    const controller = new AbortController();
    seriesCardAbortRef.current = controller;
    getSeriesCardById(targetSeriesId, { signal: controller.signal, forceRefresh })
      .then((card) => {
        if (!controller.signal.aborted) setSeriesCard(card);
      })
      .catch(() => {
        if (!controller.signal.aborted && !forceRefresh) setSeriesCard(null);
      });
  };

  const refreshEpisodeRating = (episodeId: string) => {
    if (!episodeId) return;
    episodeRatingAbortRef.current?.abort();
    const controller = new AbortController();
    episodeRatingAbortRef.current = controller;
    const applyMovieFallback = () => {
      if (typeof getCatalogMovieById !== 'function') return;
      getCatalogMovieById(episodeId, { signal: controller.signal })
        .then((movie) => {
          if (controller.signal.aborted || selectedEpisodeIdRef.current !== episodeId || !movie) return;
          setEpisodeRating({
            episodeId,
            averageRating: typeof movie.averageRating === 'number' ? movie.averageRating : null,
            ratingCount: typeof movie.ratingCount === 'number' ? movie.ratingCount : 0,
          });
        })
        .catch(() => {});
    };
    if (hasDetailRatingRef.current && typeof getCatalogEpisodeDetail === 'function') {
      getCatalogEpisodeDetail(episodeId, undefined, controller.signal)
        .then((freshDetail) => {
          if (controller.signal.aborted || selectedEpisodeIdRef.current !== episodeId) return;
          const extracted = extractEpisodeRating(freshDetail, freshDetail?.episode);
          if (extracted) setEpisodeRating({ episodeId, ...extracted });
          else applyMovieFallback();
        })
        .catch(() => {
          if (!controller.signal.aborted) applyMovieFallback();
        });
      return;
    }
    applyMovieFallback();
  };

  useEffect(() => setAutoNext(readAutoNextPreference()), []);
  useEffect(() => {
    setNextCountdown(null);
    setDismissedResume('');
  }, [progressIdentity]);
  useEffect(() => {
    setManualAssetId(null);
  }, [selectedEpisode?.id]);

  useEffect(() => {
    if (
      episodeDetail?.episode?.id &&
      episodeDetail.episode.id === selectedEpisode?.id &&
      playbackSource.kind === 'unavailable' &&
      (playbackSource.reason === 'youtube_embed_disabled' ||
        playbackSource.reason === 'file_unreachable_youtube_external')
    ) {
      const episodeId = episodeDetail.episode.id;
      setKnownExternalEpisodeIds((prev) => (prev[episodeId] ? prev : { ...prev, [episodeId]: true }));
    }
  }, [
    episodeDetail?.episode?.id,
    selectedEpisode?.id,
    playbackSource.kind,
    playbackSource.kind === 'unavailable' ? playbackSource.reason : '',
  ]);

  useEffect(() => {
    if (typeof document === 'undefined' || !seriesData?.series?.title) return;
    const seriesTitle = cleanMediaTitle(seriesData.series.title);
    const episodeTitle = selectedEpisode?.title ? cleanMediaTitle(selectedEpisode.title) : '';
    document.title = episodeTitle ? `${episodeTitle} · ${seriesTitle} · Dippie` : `${seriesTitle} · Dippie`;
  }, [seriesData?.series?.title, selectedEpisode?.title]);

  useEffect(() => {
    return () => {
      pendingSeekRef.current = null;
      pendingResumeRef.current = null;
      seriesCardAbortRef.current?.abort();
      episodeRatingAbortRef.current?.abort();
    };
  }, []);

  useEffect(() => {
    if (!seriesId) return;
    setIsLoadingSeries(true);
    setError(null);

    const requestId = ++seriesRequestIdRef.current;
    const controller = new AbortController();

    getCatalogSeriesDetail(seriesId, undefined, controller.signal)
      .then((data) => {
        if (requestId !== seriesRequestIdRef.current) return;
        if (!data) {
          setError('Không tìm thấy phim bộ này.');
        } else {
          setSeriesData(data);
          const initialSeasonId = getInitialSeasonId(data.seasons || [], data.episodes || [], targetEpisodeId);
          setSelectedSeasonId(initialSeasonId);
          setSelectedEpisode(
            getInitialEpisode(data.episodes || [], initialSeasonId, targetEpisodeId, (data.seasons || []).length > 0)
          );
        }
      })
      .catch((err: unknown) => {
        if (requestId !== seriesRequestIdRef.current) return;
        if (typeof err === 'object' && err !== null && (err as { name?: unknown }).name === 'AbortError') return;
        const message = typeof err === 'object' && err !== null && typeof (err as { message?: unknown }).message === 'string'
          ? (err as { message: string }).message
          : 'Chưa tải được phim bộ này. Bạn thử lại nhé.';
        setError(message);
      })
      .finally(() => {
        if (requestId === seriesRequestIdRef.current) setIsLoadingSeries(false);
      });

    return () => controller.abort();
  }, [seriesId, seriesRetry]);

  useEffect(() => {
    setSeriesCard(null);
    if (!seriesId) return;
    refreshSeriesCard(seriesId, false);
    return () => seriesCardAbortRef.current?.abort();
  }, [seriesId]);

  useEffect(() => {
    if (typeof window === 'undefined' || typeof window.addEventListener !== 'function') return;
    const eventName = typeof RATING_CHANGED_EVENT === 'string' ? RATING_CHANGED_EVENT : 'dippie:rating-changed';
    const handleRatingChanged = (event: Event) => {
      const changedId = (event as CustomEvent<{ movieId?: string }>)?.detail?.movieId;
      if (!changedId || changedId === seriesId) refreshSeriesCard(seriesId, true);
      const activeEpisodeId = selectedEpisodeIdRef.current;
      if (activeEpisodeId && (!changedId || changedId === activeEpisodeId)) {
        refreshEpisodeRating(activeEpisodeId);
      }
    };
    window.addEventListener(eventName, handleRatingChanged);
    return () => window.removeEventListener(eventName, handleRatingChanged);
  }, [seriesId]);

  useEffect(() => {
    if (!seriesData?.episodes || targetEpisodeId === previousUrlEpisodeIdRef.current) return;
    previousUrlEpisodeIdRef.current = targetEpisodeId;
    if (targetEpisodeId) {
      const matched = seriesData.episodes.find((episode) => episode.id === targetEpisodeId);
      if (matched) {
        if (matched.id !== selectedEpisode?.id) saveCurrentProgress('episode-change');
        setSelectedSeasonId(matched.seasonId || null);
        setSelectedEpisode(matched);
      }
    } else if (seriesData.episodes.length > 0) {
      const firstSeasonId = getInitialSeasonId(seriesData.seasons || [], seriesData.episodes);
      const firstEpisode = getInitialEpisode(
        seriesData.episodes,
        firstSeasonId,
        null,
        (seriesData.seasons || []).length > 0
      );
      if (firstEpisode && firstEpisode.id !== selectedEpisode?.id) saveCurrentProgress('episode-change');
      setSelectedSeasonId(firstSeasonId);
      setSelectedEpisode(firstEpisode);
    }
  }, [targetEpisodeId, seriesData]);

  const orderedSeasons = sortCatalogSeasons(seriesData?.seasons || []);
  const hasSeasons = orderedSeasons.length > 0;
  const seasonEpisodes = getEpisodesForSeason(seriesData?.episodes || [], selectedSeasonId, hasSeasons);
  const previousEpisode = getAdjacentEpisode(
    seriesData?.episodes || [],
    selectedSeasonId,
    selectedEpisode?.id || null,
    -1,
    hasSeasons,
    orderedSeasons
  );
  const nextEpisode = getAdjacentEpisode(
    seriesData?.episodes || [],
    selectedSeasonId,
    selectedEpisode?.id || null,
    1,
    hasSeasons,
    orderedSeasons
  );
  const previousEpisodeLabel = formatAdjacentEpisodeLabel(previousEpisode, selectedSeasonId, orderedSeasons, -1);
  const nextEpisodeLabel = formatAdjacentEpisodeLabel(nextEpisode, selectedSeasonId, orderedSeasons, 1);

  const savedProgress = activeProgressAssetId
    ? progress.records.find((record) => record.episodeId === selectedEpisode?.id)
    : undefined;
  const offerResume =
    (playbackSource.kind === 'file' || playbackSource.kind === 'youtube') &&
    !(targetEpisodeId === selectedEpisode?.id && (targetTimeMs || targetSceneId || targetSegmentId)) &&
    dismissedResume !== progressIdentity &&
    isUsableResumeProgress(savedProgress) &&
    savedProgress.positionMs >= RESUME_MIN_POSITION_MS;

  function saveCurrentProgress(reason: WatchProgressSaveReason, youtubeSnapshot?: YouTubeProgressSnapshot) {
    if (!selectedEpisode || !seriesData || episodeDetail?.episode?.id !== selectedEpisode.id) return;

    let assetId: string;
    let positionMs: number;
    let durationMs: number | null;
    let thumbnailUrl: string | undefined;

    if (playbackSource.kind === 'youtube') {
      if (!youtubeSnapshot && lastYoutubeSnapshotRef.current.episodeId !== selectedEpisode.id) return;
      const snapshot = youtubeSnapshot ?? lastYoutubeSnapshotRef.current;
      const currentSec = Number.isFinite(snapshot.currentTimeSec) && snapshot.currentTimeSec >= 0 ? snapshot.currentTimeSec : 0;
      if (reason !== 'ended' && currentSec < 1) return;
      const playerDurationMs =
        Number.isFinite(snapshot.durationSec) && snapshot.durationSec > 0 ? Math.round(snapshot.durationSec * 1000) : null;
      durationMs = resolveEffectiveDurationMs(selectedEpisode.durationMs, null, playerDurationMs);
      const rawPositionMs = reason === 'ended' && durationMs !== null ? durationMs : Math.floor(currentSec * 1000);
      positionMs = Math.min(rawPositionMs, durationMs ?? Number.MAX_SAFE_INTEGER);
      assetId = toYoutubeAssetId(playbackSource.youtube.videoId);
      thumbnailUrl = `https://i.ytimg.com/vi/${encodeURIComponent(playbackSource.youtube.videoId)}/hqdefault.jpg`;
    } else {
      const video = videoRef.current;
      const videoDurationMs =
        video && Number.isFinite(video.duration) && video.duration > 0 ? Math.round(video.duration * 1000) : null;
      const resolvedDurationMs = selectedAsset
        ? resolveEffectiveDurationMs(selectedEpisode.durationMs, selectedAsset.durationMs, videoDurationMs)
        : null;
      const currentSec = video && Number.isFinite(video.currentTime) && video.currentTime >= 0 ? video.currentTime : 0;
      if (
        playbackSource.kind !== 'file' ||
        !video ||
        !selectedAsset ||
        (reason !== 'ended' && currentSec < 1) ||
        (reason === 'ended' && currentSec < 1 && resolvedDurationMs === null) ||
        !playbackTrackerRef.current?.hasStarted(selectedEpisode.id) ||
        !(video.currentSrc || video.src || '').includes(encodeURIComponent(selectedAsset.id)) ||
        !isFullEpisodeAsset(selectedAsset, selectedEpisode.id)
      ) {
        return;
      }
      const pendingSeek = pendingSeekRef.current;
      if (
        reason !== 'ended' &&
        pendingSeek &&
        pendingSeek.episodeId === selectedEpisode.id &&
        pendingSeek.assetId === selectedAsset.id &&
        pendingSeek.targetSec > 0 &&
        currentSec * 1000 + RESUME_BACKTRACK_TOLERANCE_MS < pendingSeek.targetSec * 1000
      ) {
        return;
      }
      durationMs = resolvedDurationMs;
      const rawPositionMs = reason === 'ended' && durationMs !== null ? durationMs : Math.floor(currentSec * 1000);
      positionMs = Math.min(rawPositionMs, durationMs ?? Number.MAX_SAFE_INTEGER);
      assetId = selectedAsset.id;
    }

    if (reason !== 'ended' && offerResume && savedProgress && positionMs + RESUME_BACKTRACK_TOLERANCE_MS < savedProgress.positionMs) {
      return;
    }
    if (!playbackTrackerRef.current?.canSave(selectedEpisode.id, reason)) return;

    const season = seriesData.seasons.find((item) => item.id === selectedEpisode.seasonId);
    const draft: WatchProgressDraft = {
      episodeId: selectedEpisode.id,
      assetId,
      seriesId,
      seriesTitle: cleanMediaTitle(seriesData.series.title),
      episodeTitle: cleanMediaTitle(selectedEpisode.title),
      ...(thumbnailUrl ? { thumbnailUrl } : {}),
      seasonId: selectedEpisode.seasonId,
      seasonNumber: season?.number ?? season?.seasonNumber,
      seasonTitle: season?.title,
      episodeNumber: selectedEpisode.number ?? selectedEpisode.episodeNumber,
      positionMs,
      durationMs,
      completed: reason === 'ended' || isEpisodeCompleted(positionMs, durationMs),
    };
    void progress.saveProgress(draft, reason)?.catch?.(() => {});
  }

  fileLeaveFlushRef.current = () => {
    if (playbackSource.kind === 'file') saveCurrentProgress('leave');
  };

  useEffect(() => {
    const handlePageHide = () => fileLeaveFlushRef.current();
    const handleVisibilityChange = () => {
      if (typeof document !== 'undefined' && document.visibilityState === 'hidden') fileLeaveFlushRef.current();
    };
    if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
      window.addEventListener('pagehide', handlePageHide);
    }
    if (typeof document !== 'undefined' && typeof document.addEventListener === 'function') {
      document.addEventListener('visibilitychange', handleVisibilityChange);
    }
    return () => {
      fileLeaveFlushRef.current();
      if (typeof window !== 'undefined' && typeof window.removeEventListener === 'function') {
        window.removeEventListener('pagehide', handlePageHide);
      }
      if (typeof document !== 'undefined' && typeof document.removeEventListener === 'function') {
        document.removeEventListener('visibilitychange', handleVisibilityChange);
      }
    };
  }, []);

  const findPendingRecapSource = (episodeId: string | null | undefined): string | null => {
    if (!isKidMode || !episodeId || !playFeatures.status?.recap.enabled) return null;
    const gate = playFeatures.status.recap.pendingGates.find((item) => item.targetEpisodeId === episodeId);
    return gate ? gate.sourceEpisodeId : null;
  };

  const handleSelectEpisode = (episode: CatalogEpisode) => {
    const blockingRecapSource = findPendingRecapSource(episode.id);
    if (blockingRecapSource) {
      setNextCountdown(null);
      setActiveRecapEpisodeId(blockingRecapSource);
      return;
    }
    setActiveRecapEpisodeId(null);
    if (episode.id !== selectedEpisode?.id) saveCurrentProgress('episode-change');
    pendingResumeRef.current = null;
    setNextCountdown(null);
    setSelectedSeasonId(episode.seasonId || null);
    setEpisodeQuery('');
    if (episode.id === selectedEpisode?.id) return;
    setSelectedEpisode(episode);
    replaceEpisodeUrl(episode.id);
    previousUrlEpisodeIdRef.current = episode.id;
  };

  const handlePlayNextEpisode = (recommendation: NextEpisodeRecommendation) => {
    const targetEpisode = seriesData?.episodes?.find((episode) => episode.id === recommendation.episodeId);
    if (!targetEpisode) return;
    const blockingRecapSource = findPendingRecapSource(targetEpisode.id);
    if (blockingRecapSource) {
      setNextCountdown(null);
      setActiveRecapEpisodeId(blockingRecapSource);
      return;
    }
    setActiveRecapEpisodeId(null);

    const matchingProgress = progress.records.find((record) => record.episodeId === recommendation.episodeId);
    const nextAssetId =
      (typeof recommendation.assetId === 'string' && recommendation.assetId.trim()) ||
      matchingProgress?.assetId ||
      null;
    const startMs =
      recommendation.reason === 'RESUME' && recommendation.resumePositionMs > 0
        ? Math.floor(recommendation.resumePositionMs)
        : 0;

    const isSameEpisode = targetEpisode.id === selectedEpisode?.id;
    if (!isSameEpisode) {
      saveCurrentProgress('episode-change');
    }

    const nextParams = episodeSearchParams(searchParams, targetEpisode.id, { startMs, assetId: nextAssetId });
    selfHandledUrlKeyRef.current = buildDeepLinkKey(nextParams);

    setNextCountdown(null);
    setSelectedSeasonId(targetEpisode.seasonId || null);
    setEpisodeQuery('');
    setSearchParams(nextParams, { replace: true, preventScrollReset: true });
    previousUrlEpisodeIdRef.current = targetEpisode.id;

    if (isSameEpisode) {
      if (startMs > 0) {
        setDismissedResume(progressIdentity);
      }
      if (youtubeVideoId) {
        requestYoutubeSeek(startMs, true);
        return;
      }
      if (selectedAsset) {
        queueOrSeekVideo(startMs / 1000, { assetId: selectedAsset.id });
      } else if (videoRef.current) {
        videoRef.current.currentTime = startMs / 1000;
        void videoRef.current.play().catch(() => {});
      }
      return;
    }

    if (startMs > 0) {
      pendingResumeRef.current = { episodeId: targetEpisode.id, positionMs: startMs, assetId: nextAssetId };
      autoPlayEpisodeRef.current = null;
    } else {
      pendingResumeRef.current = null;
      autoPlayEpisodeRef.current = targetEpisode.id;
    }
    setSelectedEpisode(targetEpisode);
  };

  const handleSelectSeason = (seasonId: string) => {
    if (seasonId === selectedSeasonId) return;
    saveCurrentProgress('episode-change');
    pendingResumeRef.current = null;
    setNextCountdown(null);
    const firstEpisode = getInitialEpisode(seriesData?.episodes || [], seasonId);
    setSelectedSeasonId(seasonId);
    setSelectedEpisode(firstEpisode);
    setEpisodeQuery('');
    previousUrlEpisodeIdRef.current = firstEpisode?.id || null;
    replaceEpisodeUrl(firstEpisode?.id || null);
  };

  function handleFilePlaybackFailure(assetId: string, reason: PlaybackFailureReason) {
    if (!selectedEpisode?.id || episodeDetail?.episode?.id !== selectedEpisode.id) return;
    if (selectedAsset?.id !== assetId) return;
    const isUrlEpisode = targetEpisodeId === selectedEpisode.id;
    const matchingSegment = isUrlEpisode && targetSegmentId
      ? episodeDetail.segments.find((segment) => segment.id === targetSegmentId)
      : undefined;
    const matchingScene = isUrlEpisode && targetSceneId
      ? episodeDetail.scenes.find((scene) => scene.id === targetSceneId)
      : undefined;
    const pendingSeekSec =
      pendingSeekRef.current &&
      pendingSeekRef.current.episodeId === selectedEpisode.id &&
      pendingSeekRef.current.assetId === assetId
        ? pendingSeekRef.current.targetSec
        : null;
    const capturedStartMs = resolveFallbackStartTimeMs({
      videoCurrentTimeSec: videoRef.current?.currentTime,
      pendingSeekSec,
      trackedTimeSec: currentTime,
      manualSeekMs: youtubeSeekMs,
      deepLinkTimeMs: isUrlEpisode ? targetTimeMs : null,
      segmentStartMs: matchingSegment?.episodeStartMs,
      sceneStartMs: matchingScene?.startMs,
    });
    setFallbackStartMs(capturedStartMs > 0 ? capturedStartMs : null);
    setRuntimeFailure({ episodeId: selectedEpisode.id, assetId, reason });
    setStreamUrl('');
    setIsResolvingStream(false);
    setHasLoadedMetadata(false);
  }

  function handleRetryOriginalFile() {
    setPlaybackWarning(null);
    const failedAsset =
      playbackSource.kind === 'youtube' || playbackSource.kind === 'unavailable' ? playbackSource.failedAsset : null;
    const carryStartMs = resolveFallbackStartTimeMs({
      manualSeekMs: youtubeSeekMs,
      deepLinkTimeMs: fallbackStartMs ?? (targetEpisodeId === selectedEpisode?.id ? targetTimeMs : null),
    });

    if (runtimeFailure) {
      if (failedAsset && carryStartMs > 0 && selectedEpisode) {
        pendingSeekRef.current = {
          seriesId: String(seriesId),
          episodeId: selectedEpisode.id,
          assetId: failedAsset.id,
          sceneId: null,
          segmentId: null,
          targetSec: carryStartMs / 1000,
          generation: ++seekGenerationRef.current,
        };
      }
      setRuntimeFailure(null);
      setHasLoadedMetadata(false);
      setFileRetryCount((value) => value + 1);
      return;
    }

    if (selectedEpisode && carryStartMs > 0) {
      pendingResumeRef.current = {
        episodeId: selectedEpisode.id,
        positionMs: carryStartMs,
        assetId: failedAsset?.id || null,
      };
    }
    setRuntimeFailure(null);
    setHasLoadedMetadata(false);
    setEpisodeRetry((value) => value + 1);
  }

  function handleSelectAsset(asset: CatalogMediaAsset) {
    setPlaybackWarning(null);
    setManualAssetId(asset.id);
    setRuntimeFailure(null);
    setHasLoadedMetadata(false);
    setFileRetryCount((value) => value + 1);
    if (asset.available === false) setEpisodeRetry((value) => value + 1);
  }

  useEffect(() => {
    if (nextCountdown === null || !autoNext || !nextEpisode) return;
    const cancelHidden = () => {
      if (document.hidden) setNextCountdown(null);
    };
    document.addEventListener('visibilitychange', cancelHidden);
    const timer = window.setTimeout(() => {
      if (document.hidden) {
        setNextCountdown(null);
        return;
      }
      if (nextCountdown > 1) {
        setNextCountdown(nextCountdown - 1);
      } else {
        autoPlayEpisodeRef.current = nextEpisode.id;
        handleSelectEpisode(nextEpisode);
      }
    }, 1000);
    return () => {
      window.clearTimeout(timer);
      document.removeEventListener('visibilitychange', cancelHidden);
    };
  }, [nextCountdown, autoNext, nextEpisode?.id, progressIdentity]);

  useEffect(() => {
    const video = videoRef.current;
    if (!video || autoPlayEpisodeRef.current !== selectedEpisode?.id) return;
    const playNext = () => {
      if (
        autoPlayEpisodeRef.current !== selectedEpisode?.id ||
        document.hidden ||
        episodeDetail?.episode?.id !== selectedEpisode?.id ||
        !selectedAsset ||
        !(video.currentSrc || video.src).includes(encodeURIComponent(selectedAsset.id))
      ) {
        return;
      }
      autoPlayEpisodeRef.current = null;
      void video.play().catch(() => setPlaybackWarning('Bấm Phát để xem tập tiếp theo.'));
    };
    video.addEventListener('canplay', playNext);
    if (video.readyState >= 3) playNext();
    return () => video.removeEventListener('canplay', playNext);
  }, [streamUrl, selectedEpisode?.id, selectedAsset?.id, episodeDetail?.episode?.id]);

  useEffect(() => {
    pendingSeekRef.current = null;
    deepLinkHandledKeyRef.current = null;
    setPlaybackWarning(null);
    setActiveSegmentId(null);
    setEpisodeError(null);
    setEpisodeDetail(null);
    setEpisodeRating(null);
    hasDetailRatingRef.current = false;
    episodeRatingAbortRef.current?.abort();
    setPreferredAssetId(null);
    setRuntimeFailure(null);
    setFallbackStartMs(null);
    setHasLoadedMetadata(false);
    setStreamUrl('');
    setIsResolvingStream(false);
    setIsMediaPlaying(false);
    setCurrentTime(0);
    setYoutubeSeekMs(null);
    setYoutubeSeekRequest(null);
    lastYoutubeSnapshotRef.current = {
      episodeId: selectedEpisode?.id || null,
      currentTimeSec: 0,
      durationSec: 0,
    };
    playbackTrackerRef.current?.prepareEpisode(selectedEpisode?.id || null);
    setActiveSceneId(null);

    const requestId = ++episodeRequestIdRef.current;
    if (!selectedEpisode?.id) {
      setIsLoadingEpisode(false);
      return;
    }
    setIsLoadingEpisode(true);
    const controller = new AbortController();

    getCatalogEpisodeDetail(selectedEpisode.id, undefined, controller.signal)
      .then((detail) => {
        if (controller.signal.aborted || requestId !== episodeRequestIdRef.current) return;
        if (!detail) {
          setEpisodeError('Không tìm thấy tập này.');
          return;
        }
        setEpisodeDetail(detail);
        const extractedRating = extractEpisodeRating(detail, detail.episode);
        if (extractedRating) {
          hasDetailRatingRef.current = true;
          setEpisodeRating({ episodeId: detail.episode.id, ...extractedRating });
        } else {
          hasDetailRatingRef.current = false;
          refreshEpisodeRating(detail.episode.id);
        }
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted || requestId !== episodeRequestIdRef.current) return;
        const candidate = err as { name?: unknown; isCanceled?: unknown } | null;
        if (candidate?.name === 'AbortError' || candidate?.isCanceled) return;
        setEpisodeError((err as { isTimeout?: boolean })?.isTimeout && err instanceof Error ? err.message : 'Chưa tải được tập này. Bạn thử lại nhé.');
      })
      .finally(() => {
        if (!controller.signal.aborted && requestId === episodeRequestIdRef.current) setIsLoadingEpisode(false);
      });

    return () => controller.abort();
  }, [selectedEpisode?.id, seriesId, episodeRetry]);

  useEffect(() => {
    if (!episodeDetail || episodeDetail.episode.id !== selectedEpisode?.id) return;

    const pendingResume =
      pendingResumeRef.current && pendingResumeRef.current.episodeId === episodeDetail.episode.id
        ? pendingResumeRef.current
        : null;
    if (pendingResume) {
      pendingResumeRef.current = null;
      deepLinkHandledKeyRef.current = currentUrlDeepLinkKey;
      const nextPreferredAssetId =
        pendingResume.assetId && !isYoutubeAssetId(pendingResume.assetId) ? pendingResume.assetId : null;
      setPreferredAssetId(nextPreferredAssetId);
      const initialSelection = selectPlaybackSource({
        episodeDetail,
        selectedEpisodeId: episodeDetail.episode.id,
        episodeDurationMs: episodeDetail.episode.durationMs,
        preferredAssetId: nextPreferredAssetId,
        manualAssetId,
        startTimeMs: pendingResume.positionMs,
      });
      const assetToSelect = initialSelection.kind === 'file' ? initialSelection.asset : null;
      if (pendingResume.positionMs > 0) {
        if (assetToSelect) {
          setDismissedResume(`${episodeDetail.episode.id}:${assetToSelect.id}`);
          queueOrSeekVideo(pendingResume.positionMs / 1000, { assetId: assetToSelect.id });
        } else if (initialSelection.kind === 'youtube') {
          setDismissedResume(`${episodeDetail.episode.id}:${toYoutubeAssetId(initialSelection.youtube.videoId)}`);
          requestYoutubeSeek(pendingResume.positionMs, true);
        }
      }
      return;
    }

    if (selfHandledUrlKeyRef.current && selfHandledUrlKeyRef.current === currentUrlDeepLinkKey) {
      selfHandledUrlKeyRef.current = null;
      deepLinkHandledKeyRef.current = currentUrlDeepLinkKey;
      return;
    }

    const isUrlEpisode = targetEpisodeId ? episodeDetail.episode.id === targetEpisodeId : false;
    if (!isUrlEpisode || deepLinkHandledKeyRef.current === currentUrlDeepLinkKey) return;
    const isInitialLoadForEpisode = deepLinkHandledKeyRef.current === null;
    deepLinkHandledKeyRef.current = currentUrlDeepLinkKey;

    const scene = targetSceneId ? episodeDetail.scenes?.find((entry) => entry.id === targetSceneId) : undefined;
    const segment = targetSegmentId ? episodeDetail.segments?.find((entry) => entry.id === targetSegmentId) : undefined;
    const nextPreferredAssetId =
      (targetAssetId && !isYoutubeAssetId(targetAssetId) ? targetAssetId : null) ||
      (scene?.mediaAssetId || null);
    setPreferredAssetId(nextPreferredAssetId);
    if (targetSegmentId && segment) setActiveSegmentId(segment.id);
    if (targetSceneId && scene) setActiveSceneId(scene.id);

    const startTimeMs = targetTimeMs ?? segment?.episodeStartMs ?? scene?.startMs ?? 0;
    const initialSelection = selectPlaybackSource({
      episodeDetail,
      selectedEpisodeId: episodeDetail.episode.id,
      episodeDurationMs: episodeDetail.episode.durationMs,
      preferredAssetId: nextPreferredAssetId,
      manualAssetId,
      startTimeMs,
    });
    const assetToSelect = initialSelection.kind === 'file' ? initialSelection.asset : null;

    if (assetToSelect) {
      const seekSec = resolveMediaSeekSeconds({
        timeMs: targetTimeMs,
        timeline: targetTimeline,
        isScene: Boolean(targetSceneId),
        isSegment: Boolean(targetSegmentId),
        asset: assetToSelect,
        segmentId: targetSegmentId || undefined,
        episodeId: episodeDetail.episode.id,
        sceneStartMs: scene?.startMs,
        segmentStartMs: segment?.episodeStartMs,
      });
      if (seekSec === null) {
        if (targetSegmentId && segment) setPlaybackWarning(`Phân đoạn "${segment.title || 'này'}" chưa có mốc thời gian bắt đầu.`);
        if (targetSceneId && scene) setPlaybackWarning(`Phân cảnh "${scene.title || 'này'}" chưa có mốc thời gian bắt đầu.`);
      } else if (Number.isFinite(seekSec) && seekSec >= 0) {
        queueOrSeekVideo(seekSec, {
          assetId: assetToSelect.id,
          sceneId: targetSceneId || null,
          segmentId: targetSegmentId || null,
        });
      }
    } else if (!isInitialLoadForEpisode && initialSelection.kind === 'youtube') {
      const parsedMs = Number(startTimeMs);
      if (Number.isFinite(parsedMs) && parsedMs >= 0) {
        requestYoutubeSeek(parsedMs, false);
      }
    }
  }, [
    episodeDetail,
    selectedEpisode?.id,
    seriesId,
    targetEpisodeId,
    targetAssetId,
    targetSceneId,
    targetSegmentId,
    targetTimeMs,
    targetTimeline,
    currentUrlDeepLinkKey,
  ]);

  useEffect(() => {
    setHasLoadedMetadata(false);
    if (!selectedAsset?.id || isSelectedEpisodeVipLocked) {
      setStreamUrl('');
      setIsResolvingStream(false);
      return;
    }
    const currentAssetId = selectedAsset.id;
    const defaultUrl = resolveStreamUrl(`/api/media/stream/${encodeURIComponent(currentAssetId)}`);
    const token = getStoredAuthToken();
    const isProtected = Boolean(selectedAsset.accessPolicy && selectedAsset.accessPolicy !== 'public');

    if (!token && !isProtected) {
      setIsResolvingStream(false);
      setStreamUrl(defaultUrl);
      return;
    }

    const controller = new AbortController();
    setStreamUrl('');
    setIsResolvingStream(true);
    const grantPromise: Promise<PlaybackGrantResult> =
      typeof requestPlaybackGrantResult === 'function'
        ? requestPlaybackGrantResult(currentAssetId, controller.signal)
        : requestPlaybackGrant(currentAssetId, controller.signal).then((grant) =>
            grant?.streamUrl
              ? { ok: true, status: 200, grant }
              : { ok: false, status: isProtected ? 403 : 404, grant: null }
          );

    grantPromise
      .then((result) => {
        if (controller.signal.aborted || result.aborted) return;
        if (result.ok && result.grant?.streamUrl) {
          setPlaybackWarning(null);
          setStreamUrl(result.grant.streamUrl);
        } else if (result.status === 423) {
          setStreamUrl('');
          setIsMediaPlaying(false);
          if (result.lockReason === 'VIP_EPISODE') {
            setVipRefreshNonce((value) => value + 1);
          } else if (result.lockReason === 'WEEKEND_VAULT') {
            setPlaybackWarning(result.message || 'Két sắt phim cuối tuần đang khóa.');
          } else if (result.lockReason === 'RECAP_REQUIRED') {
            const sourceId = findPendingRecapSource(selectedEpisode?.id) ?? previousEpisode?.id ?? null;
            if (sourceId) setActiveRecapEpisodeId(sourceId);
            void playFeatures.refreshStatus();
          } else if (result.lockReason === 'WATCH_PET_REST' || result.lockReason === 'WATCH_PET_FAINT') {
            void playFeatures.refreshStatus();
          } else {
            screenTime?.markLockedFromServer();
          }
        } else {
          setStreamUrl('');
          handleFilePlaybackFailure(currentAssetId, classifyGrantFailureReason(result.status));
        }
      })
      .catch(() => {
        if (controller.signal.aborted) return;
        setStreamUrl('');
        handleFilePlaybackFailure(currentAssetId, isProtected ? 'forbidden' : 'stream_error');
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsResolvingStream(false);
      });

    return () => controller.abort();
  }, [selectedAsset?.id, selectedAsset?.accessPolicy, fileRetryCount, isSelectedEpisodeVipLocked]);

  useEffect(() => {
    if (
      !selectedAsset?.id ||
      !streamUrl ||
      isResolvingStream ||
      hasLoadedMetadata ||
      episodeDetail?.episode?.id !== selectedEpisode?.id
    ) {
      return;
    }
    if (videoRef.current && videoRef.current.readyState >= 1) {
      setHasLoadedMetadata(true);
      return;
    }
    if (typeof setTimeout !== 'function') return;
    const assetId = selectedAsset.id;
    const timer = setTimeout(() => {
      if (videoRef.current && videoRef.current.readyState >= 1) {
        setHasLoadedMetadata(true);
        return;
      }
      handleFilePlaybackFailure(assetId, 'timeout');
    }, PLAYBACK_METADATA_TIMEOUT_MS);
    return () => {
      if (typeof clearTimeout === 'function') clearTimeout(timer);
    };
  }, [selectedAsset?.id, streamUrl, isResolvingStream, hasLoadedMetadata, fileRetryCount, selectedEpisode?.id, episodeDetail?.episode?.id]);

  const syncTimelineMarkers = (currentSeconds: number, matchAssetId?: string | null) => {
    const currentMs = currentSeconds * 1000;
    if (episodeDetail?.scenes) {
      const matchingScene = episodeDetail.scenes.find(
        (scene) => currentMs >= scene.startMs && currentMs < scene.endMs && (!matchAssetId || !scene.mediaAssetId || scene.mediaAssetId === matchAssetId)
      );
      setActiveSceneId(matchingScene ? matchingScene.id : null);
    }
    if (episodeDetail?.segments) {
      const matchingSegment = episodeDetail.segments.find((segment) => {
        const segmentStart = segment.episodeStartMs ?? 0;
        const segmentEnd = segment.episodeEndMs ?? Infinity;
        return currentMs >= segmentStart && currentMs < segmentEnd;
      });
      setActiveSegmentId(matchingSegment ? matchingSegment.id : null);
    }
  };

  const handleSeekScene = (scene: CatalogScene) => {
    setPlaybackWarning(null);
    if (youtubeVideoId) {
      setActiveSceneId(scene.id);
      requestYoutubeSeek(scene.startMs, true);
      return;
    }
    const targetSec = scene.startMs / 1000;
    if (scene.mediaAssetId && episodeDetail?.mediaAssets && selectedAsset?.id !== scene.mediaAssetId) {
      const matchingAsset = episodeDetail.mediaAssets.find((asset) => asset.id === scene.mediaAssetId);
      if (!matchingAsset) {
        setPlaybackWarning(`Phân cảnh "${scene.title || 'này'}" không có trong bản video hiện tại.`);
        return;
      }
      pendingSeekRef.current = {
        seriesId: String(seriesId),
        episodeId: selectedEpisode?.id || '',
        assetId: matchingAsset.id,
        sceneId: scene.id,
        targetSec,
        generation: ++seekGenerationRef.current,
      };
      setActiveSceneId(scene.id);
      setManualAssetId(matchingAsset.id);
      setRuntimeFailure(null);
      setHasLoadedMetadata(false);
      return;
    }
    setActiveSceneId(scene.id);
    queueOrSeekVideo(targetSec, { assetId: selectedAsset?.id || '', sceneId: scene.id });
  };

  const handleSeekSegment = (segment: CatalogSegment) => {
    setPlaybackWarning(null);
    setActiveSegmentId(segment.id);
    if (typeof segment.episodeStartMs !== 'number' || Number.isNaN(segment.episodeStartMs)) {
      setPlaybackWarning(`Phân đoạn "${segment.title || 'này'}" chưa có mốc thời gian bắt đầu.`);
      return;
    }
    if (youtubeVideoId) {
      requestYoutubeSeek(segment.episodeStartMs, true);
      return;
    }
    const targetSec = resolveFileTimestamp(segment.episodeStartMs, selectedAsset, segment.id, selectedEpisode?.id);
    if (targetSec === null) {
      setPlaybackWarning('Chưa tua được tới phân đoạn này trên bản video đang phát.');
      return;
    }
    queueOrSeekVideo(targetSec, { assetId: selectedAsset?.id || '', segmentId: segment.id });
  };

  const handleLoadedMetadata = () => {
    const pending = pendingSeekRef.current;
    if (
      !pending ||
      !videoRef.current ||
      pending.generation !== seekGenerationRef.current ||
      pending.seriesId !== String(seriesId) ||
      pending.episodeId !== selectedEpisode?.id ||
      pending.assetId !== selectedAsset?.id
    ) {
      return;
    }
    seekVideoNow(videoRef.current, pending.targetSec);
    if (pending.sceneId) setActiveSceneId(pending.sceneId);
    if (pending.segmentId) setActiveSegmentId(pending.segmentId);
    pendingSeekRef.current = null;
  };

  const handleYoutubeTimeTick = (snapshot: YouTubeProgressSnapshot) => {
    lastYoutubeSnapshotRef.current = { ...snapshot, episodeId: selectedEpisode?.id || null };
    setCurrentTime(snapshot.currentTimeSec);
    syncTimelineMarkers(snapshot.currentTimeSec);
  };

  const handleYoutubeProgress = (snapshot: YouTubeProgressSnapshot, reason: YouTubeProgressReason) => {
    lastYoutubeSnapshotRef.current = { ...snapshot, episodeId: selectedEpisode?.id || null };
    if ((reason === 'interval' || reason === 'pause' || reason === 'ended') && selectedEpisode?.id) {
      playbackTrackerRef.current?.markStarted(selectedEpisode.id);
    }
    saveCurrentProgress(reason, snapshot);
  };

  const handleYoutubePlayingChange = (playing: boolean) => {
    if (!playing) {
      setIsMediaPlaying(false);
      return;
    }
    if (isPlaybackBlocked) {
      setIsMediaPlaying(false);
      return;
    }
    setIsMediaPlaying(true);
    if (selectedEpisode?.id) playbackTrackerRef.current?.markStarted(selectedEpisode.id);
    setNextCountdown(null);
    if (autoPlayEpisodeRef.current === selectedEpisode?.id) autoPlayEpisodeRef.current = null;
  };

  const triggerAutoNextIfEnabled = () => {
    if (
      isKidMode &&
      playFeatures.status?.recap.enabled &&
      selectedEpisode &&
      nextEpisode &&
      !playFeatures.status.recap.unlockedEpisodeIds.includes(nextEpisode.id)
    ) {
      playFeatures.markRecapPendingLocally(selectedEpisode.id, nextEpisode.id);
      setNextCountdown(null);
      setActiveRecapEpisodeId(selectedEpisode.id);
      return;
    }
    if (autoNext && nextEpisode && !document.hidden) {
      setNextCountdown(AUTO_NEXT_COUNTDOWN_SEC);
    }
  };

  const handleRecapUnlocked = (targetEpisodeId: string | null) => {
    playFeatures.markRecapUnlocked(targetEpisodeId);
    setActiveRecapEpisodeId(null);
    if (!targetEpisodeId) return;
    if (targetEpisodeId === selectedEpisode?.id) {
      setFileRetryCount((value) => value + 1);
      return;
    }
    const targetEpisode = seriesData?.episodes?.find((episode) => episode.id === targetEpisodeId);
    if (!targetEpisode) return;
    autoPlayEpisodeRef.current = targetEpisode.id;
    pendingResumeRef.current = null;
    setNextCountdown(null);
    setSelectedSeasonId(targetEpisode.seasonId || null);
    setEpisodeQuery('');
    setSelectedEpisode(targetEpisode);
    replaceEpisodeUrl(targetEpisode.id);
    previousUrlEpisodeIdRef.current = targetEpisode.id;
  };

  const handleRecapRewatch = () => {
    setActiveRecapEpisodeId(null);
    if (playbackSource.kind === 'youtube') {
      requestYoutubeSeek(0, true);
      return;
    }
    if (selectedAsset) {
      queueOrSeekVideo(0, { assetId: selectedAsset.id });
    } else if (videoRef.current) {
      seekVideoNow(videoRef.current, 0);
    }
  };

  const handleVideoTimeUpdate = () => {
    const video = videoRef.current;
    if (!video) return;
    if (!video.paused) saveCurrentProgress('interval');
    setCurrentTime(video.currentTime);
    syncTimelineMarkers(video.currentTime, selectedAsset?.id);
  };

  const handleResumePlayback = (fromStart: boolean) => {
    setDismissedResume(progressIdentity);
    const targetMs = fromStart ? 0 : savedProgress?.positionMs ?? 0;
    if (playbackSource.kind === 'youtube') {
      requestYoutubeSeek(targetMs, true);
      return;
    }
    if (selectedAsset) {
      queueOrSeekVideo(targetMs / 1000, { assetId: selectedAsset.id });
      return;
    }
    const video = videoRef.current;
    if (!video) return;
    seekVideoNow(video, targetMs / 1000);
  };

  const handleAutoNextToggle = (checked: boolean) => {
    setAutoNext(checked);
    writeAutoNextPreference(checked);
    setNextCountdown(null);
  };

  const seriesProgressRevision = progress.records
    .filter((record) => record.seriesId === seriesId)
    .map((record) => `${record.episodeId}:${record.version}:${record.positionMs}:${record.completed ? 1 : 0}`)
    .join('|');

  const isUnmountedDriveFallbackSource = isUnmountedDriveFallback(playbackSource, runtimeFailure);

  const markEpisodeStarted = () => {
    if (selectedEpisode?.id) playbackTrackerRef.current?.markStarted(selectedEpisode.id);
  };

  const handleVideoLoadedMetadata = () => {
    setHasLoadedMetadata(true);
    handleLoadedMetadata();
  };

  const videoHandlers = {
    onLoadedMetadata: handleVideoLoadedMetadata,
    onLoadedData: handleVideoLoadedMetadata,
    onCanPlay: handleVideoLoadedMetadata,
    onError: () => {
      setIsMediaPlaying(false);
      if (selectedAsset) handleFilePlaybackFailure(selectedAsset.id, 'file_unreachable');
    },
    onTimeUpdate: handleVideoTimeUpdate,
    onPlay: () => {
      if (isPlaybackBlocked) {
        try {
          videoRef.current?.pause();
        } catch {}
        return;
      }
      setIsMediaPlaying(true);
      setNextCountdown(null);
      markEpisodeStarted();
    },
    onPlaying: () => {
      if (isPlaybackBlocked) {
        try {
          videoRef.current?.pause();
        } catch {}
        return;
      }
      setIsMediaPlaying(true);
      markEpisodeStarted();
    },
    onPause: () => {
      setIsMediaPlaying(false);
      saveCurrentProgress('pause');
    },
    onSeeking: () => setNextCountdown(null),
    onEnded: () => {
      setIsMediaPlaying(false);
      saveCurrentProgress('ended');
      triggerAutoNextIfEnabled();
    },
  };

  const youtubeHandlers = {
    onProgress: handleYoutubeProgress,
    onEnded: triggerAutoNextIfEnabled,
    onPlayingChange: handleYoutubePlayingChange,
    onTimeTick: handleYoutubeTimeTick,
  };

  return {
    seriesData,
    seriesCard,
    selectedEpisode,
    selectedSeasonId,
    episodeQuery,
    setEpisodeQuery,
    episodeDetail,
    episodeRating,
    knownExternalEpisodeIds,
    playbackSource,
    selectedAsset,
    isLoadingSeries,
    isLoadingEpisode,
    error,
    episodeError,
    playbackWarning,
    setPlaybackWarning,
    streamUrl,
    isResolvingStream,
    fileRetryCount,
    youtubeSeekRequest,
    shouldAutoPlayYoutube: autoPlayEpisodeRef.current === selectedEpisode?.id,
    videoRef,
    activeSceneId,
    activeSegmentId,
    orderedSeasons,
    hasSeasons,
    seasonEpisodes,
    previousEpisode,
    nextEpisode,
    previousEpisodeLabel,
    nextEpisodeLabel,
    savedProgress,
    offerResume,
    autoNext,
    nextCountdown,
    setNextCountdown,
    progressError: progress.error,
    seriesProgressRevision,
    isUnmountedDriveFallback: isUnmountedDriveFallbackSource,
    isVipSeries,
    unlockedVipEpisodeIds,
    isSelectedEpisodeVipLocked,
    vipMathLevel,
    markVipEpisodeUnlocked,
    retrySeries: () => setSeriesRetry((v) => v + 1),
    retryEpisode: () => setEpisodeRetry((v) => v + 1),
    handleSelectEpisode,
    handlePlayNextEpisode,
    handleSelectSeason,
    handleSelectAsset,
    handleRetryOriginalFile,
    handleSeekScene,
    handleSeekSegment,
    handleResumePlayback,
    handleAutoNextToggle,
    videoHandlers,
    youtubeHandlers,
    playFeatures,
    isPetLocked,
    activeRecapEpisodeId,
    handleRecapUnlocked,
    handleRecapRewatch,
  };
}
