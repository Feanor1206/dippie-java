import React, { useEffect, useId, useRef } from 'react';
import { useOptionalAuth } from '@/lib/context/AuthContext';

const YOUTUBE_IFRAME_API_SRC = 'https://www.youtube.com/iframe_api';
const KID_MODE_SANDBOX = 'allow-scripts allow-same-origin allow-presentation';
const PROGRESS_REPORT_INTERVAL_MS = 15_000;
const TICK_INTERVAL_MS = 1000;
const MIN_REPORT_SECONDS = 1;

const YT_STATE_ENDED = 0;
const YT_STATE_PLAYING = 1;
const YT_STATE_PAUSED = 2;

type YouTubePlayerInstance = {
  getCurrentTime?: () => number;
  getDuration?: () => number;
  seekTo?: (seconds: number, allowSeekAhead?: boolean) => void;
  playVideo?: () => void;
  pauseVideo?: () => void;
  destroy?: () => void;
};

type YouTubeNamespace = {
  Player: new (
    element: HTMLElement | string,
    config: {
      playerVars?: Record<string, string | number>;
      events?: {
        onReady?: (event: { target: YouTubePlayerInstance }) => void;
        onStateChange?: (event: { data: number; target: YouTubePlayerInstance }) => void;
      };
    }
  ) => YouTubePlayerInstance;
  PlayerState?: {
    ENDED?: number;
    PLAYING?: number;
    PAUSED?: number;
  };
};

declare global {
  interface Window {
    YT?: YouTubeNamespace;
    onYouTubeIframeAPIReady?: () => void;
  }
}

let apiLoadPromise: Promise<YouTubeNamespace | null> | null = null;

export function resetYouTubeIframeApiLoader(): void {
  apiLoadPromise = null;
}

export function loadYouTubeIframeApi(): Promise<YouTubeNamespace | null> {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return Promise.resolve(null);
  }
  if (window.YT && typeof window.YT.Player === 'function') {
    return Promise.resolve(window.YT);
  }
  if (apiLoadPromise) return apiLoadPromise;

  apiLoadPromise = new Promise<YouTubeNamespace | null>((resolve) => {
    const finish = () => {
      if (window.YT && typeof window.YT.Player === 'function') {
        resolve(window.YT);
      } else {
        apiLoadPromise = null;
        resolve(null);
      }
    };

    const previousReady = window.onYouTubeIframeAPIReady;
    window.onYouTubeIframeAPIReady = () => {
      if (typeof previousReady === 'function') {
        try {
          previousReady();
        } catch {}
      }
      finish();
    };

    const existingScript =
      typeof document.querySelector === 'function'
        ? document.querySelector(`script[src="${YOUTUBE_IFRAME_API_SRC}"]`)
        : null;

    if (!existingScript && typeof document.createElement === 'function') {
      const script = document.createElement('script');
      script.src = YOUTUBE_IFRAME_API_SRC;
      script.async = true;
      script.onerror = () => {
        apiLoadPromise = null;
        resolve(null);
      };
      (document.head || document.body || document.documentElement)?.appendChild(script);
    }
  });

  return apiLoadPromise;
}

function withEmbedParams(embedUrl: string, kidMode: boolean): string {
  try {
    const parsed = new URL(embedUrl);
    if (!parsed.searchParams.has('enablejsapi')) parsed.searchParams.set('enablejsapi', '1');
    if (typeof window !== 'undefined' && window.location?.origin && /^https?:\/\//i.test(window.location.origin)) {
      if (!parsed.searchParams.has('origin')) parsed.searchParams.set('origin', window.location.origin);
    }
    if (kidMode) {
      parsed.searchParams.set('rel', '0');
      parsed.searchParams.set('modestbranding', '1');
      parsed.searchParams.set('iv_load_policy', '3');
      parsed.searchParams.set('playsinline', '1');
    }
    return parsed.toString();
  } catch {
    return embedUrl;
  }
}

function applySeekAndPlay(target: YouTubePlayerInstance, seconds: number, autoPlay?: boolean): void {
  if (typeof target.seekTo === 'function') {
    target.seekTo(seconds, true);
  }
  if (autoPlay && typeof target.playVideo === 'function') {
    target.playVideo();
  }
}

export type YouTubeProgressSnapshot = {
  currentTimeSec: number;
  durationSec: number;
};

export type YouTubeProgressReason = 'interval' | 'pause' | 'ended' | 'leave';

export type YouTubeSeekRequest = {
  seconds: number;
  nonce: number;
  autoPlay?: boolean;
};

export type YouTubePlayerProps = {
  videoId: string;
  src: string;
  title: string;
  seekRequest?: YouTubeSeekRequest | null;
  autoPlay?: boolean;
  kidMode?: boolean;
  paused?: boolean;
  onProgress?: (snapshot: YouTubeProgressSnapshot, reason: YouTubeProgressReason) => void;
  onEnded?: () => void;
  onPlayingChange?: (playing: boolean) => void;
  onTimeTick?: (snapshot: YouTubeProgressSnapshot) => void;
  'data-testid'?: string;
};

export function YouTubePlayer({
  videoId,
  src,
  title,
  seekRequest = null,
  autoPlay = false,
  kidMode,
  paused = false,
  onProgress,
  onEnded,
  onPlayingChange,
  onTimeTick,
  'data-testid': testId = 'series-youtube-player',
}: YouTubePlayerProps) {
  const auth = typeof useOptionalAuth === 'function' ? useOptionalAuth() : null;
  const effectiveKidMode = typeof kidMode === 'boolean' ? kidMode : Boolean(auth?.isKidMode);
  const reactId = useId();
  const iframeId = `dippie-yt-${videoId}-${reactId.replace(/[^a-zA-Z0-9_-]/g, '')}`;
  const iframeRef = useRef<HTMLIFrameElement | null>(null);
  const playerRef = useRef<YouTubePlayerInstance | null>(null);
  const readyRef = useRef(false);
  const playingRef = useRef(false);
  const hasStartedPlaybackRef = useRef(false);
  const initialSrcRef = useRef<{ videoId: string; kidMode: boolean; url: string }>({
    videoId,
    kidMode: effectiveKidMode,
    url: withEmbedParams(src, effectiveKidMode),
  });
  if (initialSrcRef.current.videoId !== videoId || initialSrcRef.current.kidMode !== effectiveKidMode) {
    initialSrcRef.current = {
      videoId,
      kidMode: effectiveKidMode,
      url: withEmbedParams(src, effectiveKidMode),
    };
  }

  const pendingSeekRef = useRef<YouTubeSeekRequest | null>(null);
  const lastSnapshotRef = useRef<YouTubeProgressSnapshot>({ currentTimeSec: 0, durationSec: 0 });
  const lastIntervalReportRef = useRef(0);
  const callbacksRef = useRef({ onProgress, onEnded, onPlayingChange, onTimeTick });
  callbacksRef.current = { onProgress, onEnded, onPlayingChange, onTimeTick };
  const autoPlayRef = useRef(autoPlay);
  autoPlayRef.current = autoPlay;
  const pausedRef = useRef(paused);
  pausedRef.current = paused;
  const wasPlayingBeforePauseRef = useRef(false);
  const prevPausedRef = useRef(paused);

  useEffect(() => {
    const wasPaused = prevPausedRef.current;
    prevPausedRef.current = paused;
    if (paused) {
      if (!wasPaused) {
        wasPlayingBeforePauseRef.current = playingRef.current;
      }
      playingRef.current = false;
      if (playerRef.current && typeof playerRef.current.pauseVideo === 'function') {
        try {
          playerRef.current.pauseVideo();
        } catch {}
      }
      callbacksRef.current.onPlayingChange?.(false);
      return;
    }
    if (
      wasPaused &&
      wasPlayingBeforePauseRef.current &&
      readyRef.current &&
      playerRef.current &&
      typeof playerRef.current.playVideo === 'function'
    ) {
      wasPlayingBeforePauseRef.current = false;
      try {
        playerRef.current.playVideo();
      } catch {}
    }
  }, [paused]);

  const readSnapshot = (instance?: YouTubePlayerInstance | null): YouTubeProgressSnapshot => {
    const target = instance || playerRef.current;
    if (!target) return lastSnapshotRef.current;
    const rawTime = typeof target.getCurrentTime === 'function' ? target.getCurrentTime() : NaN;
    const rawDuration = typeof target.getDuration === 'function' ? target.getDuration() : NaN;
    const next: YouTubeProgressSnapshot = {
      currentTimeSec: Number.isFinite(rawTime) && rawTime >= 0 ? rawTime : lastSnapshotRef.current.currentTimeSec,
      durationSec: Number.isFinite(rawDuration) && rawDuration > 0 ? rawDuration : lastSnapshotRef.current.durationSec,
    };
    lastSnapshotRef.current = next;
    return next;
  };

  useEffect(() => {
    if (!seekRequest) return;
    const safeSeconds = Number.isFinite(seekRequest.seconds) && seekRequest.seconds >= 0 ? seekRequest.seconds : 0;
    if (readyRef.current && playerRef.current) {
      applySeekAndPlay(playerRef.current, safeSeconds, seekRequest.autoPlay);
      lastSnapshotRef.current = { currentTimeSec: safeSeconds, durationSec: lastSnapshotRef.current.durationSec };
      callbacksRef.current.onTimeTick?.(lastSnapshotRef.current);
      pendingSeekRef.current = null;
    } else {
      pendingSeekRef.current = { ...seekRequest, seconds: safeSeconds };
    }
  }, [seekRequest?.nonce, seekRequest?.seconds, seekRequest?.autoPlay]);

  useEffect(() => {
    let active = true;
    let tickTimer: ReturnType<typeof setInterval> | null = null;
    readyRef.current = false;
    playingRef.current = false;
    hasStartedPlaybackRef.current = false;
    lastIntervalReportRef.current = 0;
    lastSnapshotRef.current = { currentTimeSec: 0, durationSec: 0 };

    const stopTimers = () => {
      if (tickTimer !== null) {
        clearInterval(tickTimer);
        tickTimer = null;
      }
    };

    const reportLeave = () => {
      if (!hasStartedPlaybackRef.current) return;
      const snapshot = readSnapshot();
      if (snapshot.currentTimeSec >= MIN_REPORT_SECONDS) {
        callbacksRef.current.onProgress?.(snapshot, 'leave');
      }
    };

    const handleVisibilityChange = () => {
      if (typeof document !== 'undefined' && document.visibilityState === 'hidden') {
        reportLeave();
      }
    };

    if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
      window.addEventListener('pagehide', reportLeave);
    }
    if (typeof document !== 'undefined' && typeof document.addEventListener === 'function') {
      document.addEventListener('visibilitychange', handleVisibilityChange);
    }

    void loadYouTubeIframeApi().then((YT) => {
      if (!active || !YT || !iframeRef.current) return;

      const playingState = YT.PlayerState?.PLAYING ?? YT_STATE_PLAYING;
      const pausedState = YT.PlayerState?.PAUSED ?? YT_STATE_PAUSED;
      const endedState = YT.PlayerState?.ENDED ?? YT_STATE_ENDED;

      try {
        playerRef.current = new YT.Player(iframeRef.current, {
          ...(effectiveKidMode
            ? {
                playerVars: {
                  rel: 0,
                  modestbranding: 1,
                  iv_load_policy: 3,
                  playsinline: 1,
                },
              }
            : {}),
          events: {
            onReady: (event) => {
              if (!active) return;
              playerRef.current = event.target;
              readyRef.current = true;
              const pending = pendingSeekRef.current;
              if (pending) {
                applySeekAndPlay(event.target, pending.seconds, pending.autoPlay && !pausedRef.current);
                const snapshot = readSnapshot(event.target);
                const syncedSnapshot: YouTubeProgressSnapshot = {
                  currentTimeSec: pending.seconds,
                  durationSec: snapshot.durationSec,
                };
                lastSnapshotRef.current = syncedSnapshot;
                pendingSeekRef.current = null;
                callbacksRef.current.onTimeTick?.(syncedSnapshot);
              } else {
                if (autoPlayRef.current && !pausedRef.current && typeof event.target.playVideo === 'function') {
                  event.target.playVideo();
                }
                callbacksRef.current.onTimeTick?.(readSnapshot(event.target));
              }
            },
            onStateChange: (event) => {
              if (!active) return;
              playerRef.current = event.target;
              if (event.data === playingState) {
                hasStartedPlaybackRef.current = true;
                playingRef.current = true;
                callbacksRef.current.onPlayingChange?.(true);
                callbacksRef.current.onTimeTick?.(readSnapshot(event.target));
                stopTimers();
                if (lastIntervalReportRef.current === 0) lastIntervalReportRef.current = Date.now();
                tickTimer = setInterval(() => {
                  if (!active || !playingRef.current) return;
                  const snapshot = readSnapshot(event.target);
                  callbacksRef.current.onTimeTick?.(snapshot);
                  const now = Date.now();
                  if (now - lastIntervalReportRef.current >= PROGRESS_REPORT_INTERVAL_MS) {
                    lastIntervalReportRef.current = now;
                    if (snapshot.currentTimeSec >= MIN_REPORT_SECONDS) {
                      callbacksRef.current.onProgress?.(snapshot, 'interval');
                    }
                  }
                }, TICK_INTERVAL_MS);
              } else if (event.data === pausedState) {
                playingRef.current = false;
                stopTimers();
                callbacksRef.current.onPlayingChange?.(false);
                const snapshot = readSnapshot(event.target);
                callbacksRef.current.onTimeTick?.(snapshot);
                if (hasStartedPlaybackRef.current && snapshot.currentTimeSec >= MIN_REPORT_SECONDS) {
                  callbacksRef.current.onProgress?.(snapshot, 'pause');
                }
              } else if (event.data === endedState) {
                playingRef.current = false;
                stopTimers();
                callbacksRef.current.onPlayingChange?.(false);
                const snapshot = readSnapshot(event.target);
                const endedSnapshot: YouTubeProgressSnapshot = {
                  currentTimeSec: snapshot.durationSec > 0 ? snapshot.durationSec : snapshot.currentTimeSec,
                  durationSec: snapshot.durationSec,
                };
                lastSnapshotRef.current = endedSnapshot;
                callbacksRef.current.onTimeTick?.(endedSnapshot);
                if (hasStartedPlaybackRef.current) {
                  callbacksRef.current.onProgress?.(endedSnapshot, 'ended');
                }
                callbacksRef.current.onEnded?.();
              }
            },
          },
        });
      } catch {}
    });

    return () => {
      active = false;
      stopTimers();
      reportLeave();
      if (typeof window !== 'undefined' && typeof window.removeEventListener === 'function') {
        window.removeEventListener('pagehide', reportLeave);
      }
      if (typeof document !== 'undefined' && typeof document.removeEventListener === 'function') {
        document.removeEventListener('visibilitychange', handleVisibilityChange);
      }
      if (playerRef.current && typeof playerRef.current.destroy === 'function') {
        try {
          playerRef.current.destroy();
        } catch {}
      }
      playerRef.current = null;
      readyRef.current = false;
      playingRef.current = false;
      hasStartedPlaybackRef.current = false;
    };
  }, [videoId]);

  return (
    <iframe
      ref={iframeRef}
      id={iframeId}
      data-testid={testId}
      className="absolute inset-0 h-full w-full"
      title={title}
      src={initialSrcRef.current.url}
      allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture; web-share"
      referrerPolicy="strict-origin-when-cross-origin"
      sandbox={effectiveKidMode ? KID_MODE_SANDBOX : undefined}
      allowFullScreen
    />
  );
}
