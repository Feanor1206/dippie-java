import { PlaybackReportButton } from '@/features/series/components/PlaybackReportButton';
import { YouTubeLink } from '@/components/ui/YouTubeLink';
import { useOptionalAuth } from '@/lib/context/AuthContext';
import { useOptionalScreenTime } from '@/lib/context/ScreenTimeContext';
import { ScreenTimeChip } from '@/features/profiles/components/ScreenTimeChip';
import { ScreenTimeLockOverlay } from '@/features/profiles/components/ScreenTimeLockOverlay';
import { VipBadge } from '@/features/profiles/components/VipBadge';
import { VipUnlockDialog } from '@/features/profiles/components/VipUnlockDialog';
import { WatchPetWidget } from '@/features/profiles/components/WatchPetWidget';
import { LootDropOverlay } from '@/features/profiles/components/LootDropOverlay';
import { RecapQuizOverlay } from '@/features/profiles/components/RecapQuizOverlay';
import { useBlockingOverlayFocus } from '@/features/profiles/hooks/useBlockingOverlayFocus';
import {
  isFullScreenPlayerOverlay,
  resolveActivePlayerOverlay,
} from '@/features/profiles/overlayPriority';
import type { LootFeedbackBanner } from '@/features/profiles/hooks/usePlayFeatures';
import type { QuizOptionKey } from '@/features/quiz/quizService';
import type {
  LootDropAnswerResult,
  LootDropQuiz,
  LootDropSpawn,
  WatchPetStatus,
} from '@/lib/services/playFeaturesService';
import React, { useEffect, useRef, useState } from 'react';
import { Eye } from 'lucide-react';
import { cleanMediaTitle, formatDurationMs } from '@/lib/labels';
import {
  getUnavailableReasonMessage,
  type PlaybackSourceSelection,
} from '@/lib/services/playbackSource';
import type { CatalogEpisode, CatalogEpisodeResponse } from '@/lib/services/catalogService';
import type { WatchProgressRecord } from '@/lib/services/watchProgressService';
import {
  YouTubePlayer,
  type YouTubeProgressReason,
  type YouTubeProgressSnapshot,
  type YouTubeSeekRequest,
} from '@/features/series/components/YouTubePlayer';

export type EpisodePlayerPanelProps = {
  seriesTitle: string;
  selectedEpisode: CatalogEpisode | null;
  episodeDetail: CatalogEpisodeResponse | null;
  playbackSource: PlaybackSourceSelection;
  isLoadingEpisode: boolean;
  isResolvingStream: boolean;
  episodeError: string | null;
  playbackWarning: string | null;
  onDismissWarning: () => void;
  isUnmountedDriveFallback: boolean;
  onRetryOriginalFile: () => void;
  onRetryEpisode: () => void;
  streamUrl: string;
  fileRetryCount: number;
  videoRef: React.RefObject<HTMLVideoElement | null>;
  youtubeSeekRequest: YouTubeSeekRequest | null;
  shouldAutoPlayYoutube: boolean;
  youtubeHandlers: {
    onProgress: (snapshot: YouTubeProgressSnapshot, reason: YouTubeProgressReason) => void;
    onEnded: () => void;
    onPlayingChange: (playing: boolean) => void;
    onTimeTick: (snapshot: YouTubeProgressSnapshot) => void;
  };
  videoHandlers: {
    onLoadedMetadata: () => void;
    onLoadedData: () => void;
    onCanPlay: () => void;
    onError: () => void;
    onTimeUpdate: () => void;
    onPlay: () => void;
    onPlaying: () => void;
    onPause: () => void;
    onSeeking: () => void;
    onEnded: () => void;
  };
  offerResume: boolean;
  savedProgress?: WatchProgressRecord;
  onResumePlayback: (fromStart: boolean) => void;
  autoNext: boolean;
  onAutoNextToggle: (checked: boolean) => void;
  nextCountdown: number | null;
  nextEpisodeLabel: string;
  hasNextEpisode: boolean;
  onCancelNextCountdown: () => void;
  progressError: string | null;
  playerScrollNonce?: number;
  onFinishSession?: () => void;
  isVipLocked?: boolean;
  vipQuizGradeLabel?: string;
  onVipUnlocked?: (episodeId: string) => void;
  kidPlayOverlay?: {
    watchPet: WatchPetStatus | null;
    isPetLocked: boolean;
    restCountdown: number;
    onStartRest: () => Promise<WatchPetStatus | null>;
    onWatchPetUpdated: (next: WatchPetStatus) => void;
    activeDrop: LootDropSpawn | null;
    dropSecondsLeft: number;
    activeLootQuiz: LootDropQuiz | null;
    lootQuizSecondsLeft: number;
    lootSelectedOption?: QuizOptionKey | null;
    lootRetryError?: string | null;
    lootFeedback: LootFeedbackBanner | null;
    lootOpening: boolean;
    lootSubmitting: boolean;
    onOpenLootChest: () => Promise<void>;
    onSubmitLootAnswer: (option: QuizOptionKey) => Promise<LootDropAnswerResult | null>;
    onDismissLootQuiz?: () => void;
    onSelectLootDraftOption?: (option: QuizOptionKey) => void;
    activeRecapEpisodeId: string | null;
    onRecapUnlocked: (targetEpisodeId: string | null) => void;
    onRecapRewatch: () => void;
  };
};

function prefersReducedScroll(): boolean {
  if (typeof document !== 'undefined' && document.documentElement?.dataset?.motion === 'reduce') {
    return true;
  }
  if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
    return Boolean(window.matchMedia('(prefers-reduced-motion: reduce)').matches);
  }
  return false;
}

export function EpisodePlayerPanel({
  seriesTitle,
  selectedEpisode,
  episodeDetail,
  playbackSource,
  isLoadingEpisode,
  isResolvingStream,
  episodeError,
  playbackWarning,
  onDismissWarning,
  isUnmountedDriveFallback,
  onRetryOriginalFile,
  onRetryEpisode,
  streamUrl,
  fileRetryCount,
  videoRef,
  youtubeSeekRequest,
  shouldAutoPlayYoutube,
  youtubeHandlers,
  videoHandlers,
  offerResume,
  savedProgress,
  onResumePlayback,
  autoNext,
  onAutoNextToggle,
  nextCountdown,
  nextEpisodeLabel,
  hasNextEpisode,
  onCancelNextCountdown,
  progressError,
  playerScrollNonce = 0,
  onFinishSession,
  isVipLocked = false,
  vipQuizGradeLabel = 'khó hơn',
  onVipUnlocked,
  kidPlayOverlay,
}: EpisodePlayerPanelProps) {
  const auth = typeof useOptionalAuth === 'function' ? useOptionalAuth() : null;
  const screenTime = typeof useOptionalScreenTime === 'function' ? useOptionalScreenTime() : null;
  const isKidMode = Boolean(auth?.isKidMode);
  const isScreenTimeLocked = Boolean(isKidMode && screenTime?.isLocked);
  const [holdSolvedOverlay, setHoldSolvedOverlay] = useState(false);
  const [vipDialogOpen, setVipDialogOpen] = useState(false);
  const playerFrameRef = useRef<HTMLDivElement | null>(null);
  const vipLockRef = useRef<HTMLDivElement | null>(null);

  const hasTimeUpLock = Boolean(
    isKidMode && (isScreenTimeLocked || holdSolvedOverlay) && screenTime?.screenTime && auth?.token
  );
  const hasWatchPetLock = Boolean(isKidMode && auth?.token && kidPlayOverlay?.isPetLocked);
  const hasRecapGate = Boolean(isKidMode && auth?.token && kidPlayOverlay?.activeRecapEpisodeId);
  const hasVipLock = Boolean(isKidMode && isVipLocked && selectedEpisode && auth?.token);
  const hasLootOverlay = Boolean(
    isKidMode &&
      auth?.token &&
      kidPlayOverlay &&
      (kidPlayOverlay.activeDrop || kidPlayOverlay.activeLootQuiz || kidPlayOverlay.lootFeedback)
  );
  const activeOverlay = resolveActivePlayerOverlay({
    hasTimeUpLock,
    hasWatchPetLock,
    hasRecapGate,
    hasVipLock,
    hasLootOverlay,
  });
  const showLockOverlay = activeOverlay === 'time-up';
  const showWatchPetOverlay = activeOverlay === 'watch-pet';
  const showRecapOverlay = activeOverlay === 'recap';
  const showVipLock = activeOverlay === 'vip';
  const showCornerPet = Boolean(
    isKidMode && auth?.token && kidPlayOverlay && !isFullScreenPlayerOverlay(activeOverlay)
  );
  const showLootOverlay = activeOverlay === 'loot' && Boolean(kidPlayOverlay);
  const isLootQuizOpen = Boolean(showLootOverlay && kidPlayOverlay?.activeLootQuiz);
  const needsOverlayHeight = isFullScreenPlayerOverlay(activeOverlay) || isLootQuizOpen;
  const isPlaybackPaused = isScreenTimeLocked || isFullScreenPlayerOverlay(activeOverlay) || isLootQuizOpen;

  useBlockingOverlayFocus(
    vipLockRef,
    Boolean(showVipLock && selectedEpisode && !vipDialogOpen),
    playerFrameRef
  );

  useEffect(() => {
    setVipDialogOpen(false);
  }, [selectedEpisode?.id]);

  useEffect(() => {
    if (activeOverlay !== 'vip') {
      setVipDialogOpen(false);
    }
  }, [activeOverlay]);

  const wasVideoPlayingRef = useRef(false);
  const prevPlaybackPausedRef = useRef(isPlaybackPaused);
  useEffect(() => {
    const wasPaused = prevPlaybackPausedRef.current;
    prevPlaybackPausedRef.current = isPlaybackPaused;
    if (isPlaybackPaused) {
      if (!wasPaused) {
        wasVideoPlayingRef.current = Boolean(
          videoRef.current && !videoRef.current.paused && !videoRef.current.ended
        );
      }
      try {
        videoRef.current?.pause();
      } catch {}
      return;
    }
    if (
      wasPaused &&
      wasVideoPlayingRef.current &&
      videoRef.current &&
      typeof videoRef.current.play === 'function'
    ) {
      wasVideoPlayingRef.current = false;
      void videoRef.current.play().catch(() => {});
    }
  }, [isPlaybackPaused, videoRef]);

  const scrollPlayerIntoView = () => {
    const el = playerFrameRef.current;
    if (!el || typeof el.scrollIntoView !== 'function') return;
    if (typeof el.getBoundingClientRect === 'function' && typeof window !== 'undefined') {
      const rect = el.getBoundingClientRect();
      const viewportHeight = window.innerHeight || 800;
      if (rect.top >= 0 && rect.top <= viewportHeight * 0.35) {
        return;
      }
    }
    el.scrollIntoView({
      behavior: prefersReducedScroll() ? 'auto' : 'smooth',
      block: 'start',
    });
  };

  useEffect(() => {
    if (playerScrollNonce <= 0) return;
    scrollPlayerIntoView();
  }, [playerScrollNonce]);

  const prevFullOverlayRef = useRef(activeOverlay);
  useEffect(() => {
    const prev = prevFullOverlayRef.current;
    prevFullOverlayRef.current = activeOverlay;
    if (activeOverlay && activeOverlay !== prev && isFullScreenPlayerOverlay(activeOverlay)) {
      scrollPlayerIntoView();
    }
  }, [activeOverlay]);

  const youtubeEmbed = playbackSource.kind === 'youtube';
  const youtubeUrl =
    playbackSource.kind === 'youtube'
      ? playbackSource.watchUrl
      : playbackSource.kind === 'unavailable'
      ? playbackSource.youtubeWatchUrl
      : null;
  const requiresGuestLogin = Boolean(
    !auth?.isAuthenticated &&
      playbackSource.kind === 'unavailable' &&
      playbackSource.reason === 'forbidden'
  );

  const savedProgressLabel =
    savedProgress && savedProgress.positionMs > 0
      ? `${cleanMediaTitle(selectedEpisode?.title || seriesTitle)} · ${formatDurationMs(savedProgress.positionMs)}`
      : selectedEpisode?.title
      ? cleanMediaTitle(selectedEpisode.title)
      : null;

  const stStatus = screenTime?.screenTime ?? null;
  const totalAllowanceSeconds = stStatus
    ? stStatus.dailySeconds + stStatus.rewardSeconds + stStatus.grantSeconds + stStatus.bountySeconds
    : null;

  const watchPet = kidPlayOverlay?.watchPet ?? null;
  const eyeBreakText = watchPet?.resting
    ? 'Đang nghỉ mắt cùng Dieppie'
    : watchPet?.fainted || watchPet?.stage === 'FAINTED'
    ? 'Đến giờ nghỉ mắt'
    : watchPet?.stage === 'DIZZY' || watchPet?.stage === 'SWEATY'
    ? 'Sắp tới giờ nghỉ mắt'
    : 'Mắt khỏe';

  const showUnifiedKidBar = Boolean(
    isKidMode && (screenTime?.isLimited || Boolean(watchPet?.enabled))
  );

  return (
    <>
      {playbackWarning && (
        <div className="flex items-center justify-between gap-2 rounded-lg border border-amber-500/30 bg-amber-500/10 p-3 text-xs text-amber-900">
          <div className="flex items-center gap-2">
            <span>{playbackWarning}</span>
          </div>
          <button
            onClick={onDismissWarning}
            className="inline-flex min-h-[44px] min-w-[44px] shrink-0 items-center justify-center px-2 text-muted hover:text-ink cursor-pointer"
          >
            Đóng
          </button>
        </div>
      )}

      {!isKidMode &&
        playbackSource.kind === 'youtube' &&
        playbackSource.isFallback &&
        (isUnmountedDriveFallback ? (
          <div
            role="status"
            aria-live="polite"
            data-testid="playback-source-note"
            className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-surface-border bg-surface-muted px-3 py-2 text-xs text-muted"
          >
            <span>Đang phát từ YouTube.</span>
          </div>
        ) : (
          <div
            role="status"
            aria-live="polite"
            data-testid="playback-fallback-notice"
            className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-amber-500/30 bg-amber-500/10 px-3 py-2 text-xs text-amber-900"
          >
            <span>Đang phát bản dự phòng trên YouTube.</span>
            <button
              type="button"
              data-testid="retry-original-file"
              onClick={onRetryOriginalFile}
              className="btn-secondary min-h-[44px] shrink-0 px-3 py-1 text-xs font-semibold"
            >
              Thử lại bản gốc
            </button>
          </div>
        ))}

      {showUnifiedKidBar && (
        <div
          data-testid="kid-playback-status-bar"
          data-kid-playback-bar="true"
          className="flex flex-wrap items-center justify-between gap-2"
        >
          {screenTime?.isLimited && (
            <ScreenTimeChip
              data-testid="player-screen-time-chip"
              remainingSeconds={screenTime.remainingSeconds}
              locked={screenTime.isLocked}
              totalSeconds={totalAllowanceSeconds}
              variant="player"
            />
          )}
          {watchPet?.enabled && (
            <div
              data-testid="player-eye-break-indicator"
              className="inline-flex min-h-[36px] items-center gap-1.5 rounded-xl border border-ink/45 bg-mint/45 px-3 py-1.5 text-xs font-bold text-ink"
            >
              <Eye size={14} aria-hidden="true" className="shrink-0 text-brand-dark" />
              <span>Nghỉ mắt: {eyeBreakText}</span>
            </div>
          )}
        </div>
      )}

      <div
        ref={playerFrameRef}
        tabIndex={-1}
        data-testid="series-player-frame"
        data-active-overlay={activeOverlay || 'none'}
        className={`relative scroll-mt-20 overflow-hidden rounded-xl border border-surface-border shadow-2xl flex items-center justify-center ${
          !isLoadingEpisode && !isResolvingStream && !episodeError && playbackSource.kind === 'unavailable'
            ? 'bg-surface'
            : 'bg-black'
        } ${
          needsOverlayHeight
            ? 'min-h-[30rem] sm:min-h-[32rem] lg:aspect-video'
            : youtubeEmbed
            ? 'aspect-video min-h-[200px]'
            : 'aspect-video'
        }`}
      >
        {showVipLock && selectedEpisode ? (
          <div
            ref={vipLockRef}
            tabIndex={-1}
            role="dialog"
            aria-modal="true"
            aria-label="Mở khóa tập phim thử thách"
            data-testid="vip-episode-lock-overlay"
            className="motion-overlay-overshoot flex h-full w-full flex-col items-center justify-center overflow-y-auto overscroll-contain bg-ink/95 p-5 text-center text-paper-white sm:p-6"
          >
            <VipBadge data-testid="player-vip-badge" />
            <h3 className="mt-3 font-display text-lg font-bold text-paper-white sm:text-xl">
              Phim cần trả lời câu hỏi · Tập đang khóa
            </h3>
            <p className="mt-2 max-w-md text-xs leading-relaxed text-zinc-200 sm:text-sm">
              Bé trả lời đúng 1 câu hỏi thử thách ({vipQuizGradeLabel}) để mở tập này hôm nay nhé!
            </p>
            <button
              type="button"
              data-autofocus=""
              data-testid="open-vip-unlock-button"
              onClick={() => setVipDialogOpen(true)}
              className="btn-primary motion-sheen mt-4 min-h-[44px] text-xs font-bold sm:text-sm"
            >
              Trả lời câu hỏi mở khóa tập này
            </button>
          </div>
        ) : isLoadingEpisode || isResolvingStream ? (
          <p role="status" className="p-6 text-center text-xs text-zinc-200">
            Đang tải tập…
          </p>
        ) : episodeError ? (
          <div role="alert" aria-live="assertive" className="p-6 text-center text-xs text-zinc-200">
            <p>{episodeError}</p>
            <button data-testid="retry-episode" onClick={onRetryEpisode} className="btn-secondary mt-3">
              Thử lại
            </button>
          </div>
        ) : playbackSource.kind === 'youtube' ? (
          <YouTubePlayer
            key={`${selectedEpisode?.id || ''}:${playbackSource.youtube.videoId}`}
            data-testid="series-youtube-player"
            videoId={playbackSource.youtube.videoId}
            src={playbackSource.embedUrl}
            title={cleanMediaTitle(selectedEpisode?.title) || 'Tập trên YouTube'}
            seekRequest={youtubeSeekRequest}
            autoPlay={shouldAutoPlayYoutube && !isPlaybackPaused}
            kidMode={isKidMode}
            paused={isPlaybackPaused}
            {...youtubeHandlers}
          />
        ) : playbackSource.kind === 'file' && streamUrl ? (
          <video
            ref={videoRef}
            data-testid="series-video-player"
            key={`${selectedEpisode?.id || ''}:${streamUrl}:${fileRetryCount}`}
            src={streamUrl}
            controls
            playsInline
            {...videoHandlers}
            className="h-full w-full object-contain"
          />
        ) : showLockOverlay || showWatchPetOverlay || showRecapOverlay ? (
          <div className="h-full w-full bg-ink" />
        ) : (
          <div
            role="alert"
            aria-live="assertive"
            data-testid="playback-unavailable-card"
            className="flex h-full w-full flex-col items-center justify-center p-6 text-center text-ink"
          >
            {!isKidMode && playbackSource.kind === 'unavailable' && playbackSource.youtubeWatchUrl && (
              <span
                data-testid="player-external-badge"
                className="mb-2 inline-flex items-center rounded border border-amber-500/40 bg-amber-500/10 px-2.5 py-0.5 text-[0.6875rem] font-semibold text-amber-900"
              >
                Chỉ mở trên YouTube
              </span>
            )}
            <p className="text-xs font-semibold text-brand-dark">
              {cleanMediaTitle(episodeDetail?.episode?.title || selectedEpisode?.title || seriesTitle)}
            </p>
            <h3 className="mt-1 text-base font-bold text-ink sm:text-lg">Chưa phát được tập này</h3>
            <p className="mt-2 max-w-md text-xs leading-relaxed text-muted">
              {requiresGuestLogin
                ? 'Bạn đăng nhập tài khoản Dippie để xem bản video này nhé.'
                : isKidMode &&
                  playbackSource.kind === 'unavailable' &&
                  (playbackSource.reason === 'youtube_embed_disabled' ||
                    playbackSource.reason === 'file_unreachable_youtube_external')
                ? 'Tập này tạm chưa xem được trong hồ sơ trẻ em.'
                : getUnavailableReasonMessage(
                    playbackSource.kind === 'unavailable' ? playbackSource.reason : 'file_unreachable'
                  )}
            </p>
            <div className="mt-4 flex flex-wrap items-center justify-center gap-3">
              {requiresGuestLogin && auth?.openAuthModal && (
                <button
                  type="button"
                  data-testid="player-login-button"
                  onClick={() => auth.openAuthModal('login')}
                  className="btn-primary inline-flex min-h-[44px] items-center justify-center px-4 py-2 text-xs font-semibold"
                >
                  Đăng nhập để xem
                </button>
              )}
              {!isKidMode && playbackSource.kind === 'unavailable' && playbackSource.youtubeWatchUrl && (
                <YouTubeLink
                  url={playbackSource.youtubeWatchUrl}
                  testId="open-on-youtube-link"
                  className="btn-primary inline-flex min-h-[44px] items-center justify-center px-4 py-2 text-xs font-semibold"
                >
                  Mở trên YouTube
                </YouTubeLink>
              )}
              {((playbackSource.kind === 'unavailable' && playbackSource.canRetryFile) ||
                playbackSource.kind === 'file') && (
                <button
                  type="button"
                  data-testid="retry-original-file"
                  onClick={onRetryOriginalFile}
                  className="btn-secondary inline-flex min-h-[44px] items-center justify-center px-4 py-2 text-xs font-semibold"
                >
                  Thử lại bản gốc
                </button>
              )}
            </div>
          </div>
        )}
        {showLockOverlay && screenTime?.screenTime && auth?.token && (
          <ScreenTimeLockOverlay
            status={screenTime.screenTime}
            token={auth.token}
            savedProgressLabel={savedProgressLabel}
            onStatusUpdated={(next) => {
              if (!next.locked) {
                setHoldSolvedOverlay(true);
              }
              screenTime.applyScreenTimeStatus(next);
            }}
            onContinueWatching={() => setHoldSolvedOverlay(false)}
            onFinishSession={onFinishSession}
          />
        )}
        {showWatchPetOverlay && auth?.token && kidPlayOverlay && (
          <WatchPetWidget
            watchPet={kidPlayOverlay.watchPet}
            token={auth.token}
            restCountdown={kidPlayOverlay.restCountdown}
            savedProgressLabel={savedProgressLabel}
            onStartRest={kidPlayOverlay.onStartRest}
            onWatchPetUpdated={kidPlayOverlay.onWatchPetUpdated}
            onScreenTimeUpdated={(next) => screenTime?.applyScreenTimeStatus(next)}
            onFinishSession={onFinishSession}
            hideCorner
          />
        )}
        {showRecapOverlay && auth?.token && kidPlayOverlay?.activeRecapEpisodeId && (
          <RecapQuizOverlay
            token={auth.token}
            episodeId={kidPlayOverlay.activeRecapEpisodeId}
            nextEpisodeLabel={nextEpisodeLabel}
            onUnlocked={kidPlayOverlay.onRecapUnlocked}
            onRewatchEpisode={kidPlayOverlay.onRecapRewatch}
            onFinishSession={onFinishSession}
          />
        )}
        {showCornerPet && auth?.token && kidPlayOverlay && (
          <WatchPetWidget
            watchPet={kidPlayOverlay.watchPet}
            token={auth.token}
            restCountdown={kidPlayOverlay.restCountdown}
            savedProgressLabel={savedProgressLabel}
            onStartRest={kidPlayOverlay.onStartRest}
            onWatchPetUpdated={kidPlayOverlay.onWatchPetUpdated}
            onScreenTimeUpdated={(next) => screenTime?.applyScreenTimeStatus(next)}
            onFinishSession={onFinishSession}
          />
        )}
        {showLootOverlay && kidPlayOverlay && (
          <LootDropOverlay
            activeDrop={kidPlayOverlay.activeDrop}
            dropSecondsLeft={kidPlayOverlay.dropSecondsLeft}
            activeQuiz={kidPlayOverlay.activeLootQuiz}
            quizSecondsLeft={kidPlayOverlay.lootQuizSecondsLeft}
            selectedOption={kidPlayOverlay.lootSelectedOption}
            retryError={kidPlayOverlay.lootRetryError}
            feedback={kidPlayOverlay.lootFeedback}
            opening={kidPlayOverlay.lootOpening}
            submitting={kidPlayOverlay.lootSubmitting}
            onOpenChest={kidPlayOverlay.onOpenLootChest}
            onSubmitAnswer={kidPlayOverlay.onSubmitLootAnswer}
            onDismissQuiz={kidPlayOverlay.onDismissLootQuiz}
            onSelectDraftOption={kidPlayOverlay.onSelectLootDraftOption}
          />
        )}
      </div>

      {!isKidMode && youtubeEmbed && youtubeUrl && (
        <YouTubeLink
          url={youtubeUrl}
          testId="youtube-external-link"
          className="inline-flex min-h-[44px] items-center py-2 text-sm font-medium text-brand-dark underline underline-offset-4"
        >
          Mở trên YouTube
        </YouTubeLink>
      )}

      {offerResume && savedProgress && (
        <div
          className="motion-toast-enter rounded-xl border-2 border-ink bg-[#f2d18c]/35 flex flex-wrap items-center justify-between gap-3 p-4 shadow-[2px_2px_0_#203328]"
          data-testid="resume-choice"
        >
          <span className="text-xs sm:text-sm text-ink">
            Bạn đang xem dở ở <strong className="font-mono text-brand-dark">{formatDurationMs(savedProgress.positionMs)}</strong>.
          </span>
          <div className="flex flex-wrap items-center gap-2">
            <button type="button" className="btn-primary text-xs py-1.5 px-3" onClick={() => onResumePlayback(false)}>
              Xem tiếp
            </button>
            <button
              type="button"
              className="inline-flex min-h-[44px] items-center px-2 text-xs font-semibold text-muted hover:text-ink underline cursor-pointer"
              onClick={() => onResumePlayback(true)}
            >
              Xem từ đầu
            </button>
          </div>
        </div>
      )}

      <div className="flex flex-wrap items-center gap-3 text-xs text-muted">
        {selectedEpisode && (
          <PlaybackReportButton
            key={selectedEpisode.id}
            episodeId={selectedEpisode.id}
            episodeTitle={cleanMediaTitle(selectedEpisode.title)}
            assetId={playbackSource.kind === 'file' ? playbackSource.asset.id : null}
          />
        )}
        {(playbackSource.kind === 'file' || playbackSource.kind === 'youtube') && (
          <label className="inline-flex min-h-[44px] items-center gap-2 cursor-pointer hover:text-ink">
            <input
              type="checkbox"
              data-testid="auto-next-checkbox"
              className="h-4 w-4 rounded border-surface-border bg-surface accent-brand"
              checked={autoNext}
              onChange={(event) => onAutoNextToggle(event.target.checked)}
            />
            Tự chuyển sang tập tiếp theo
          </label>
        )}
        {nextCountdown !== null && hasNextEpisode && (
          <div
            role="status"
            className="motion-toast-enter rounded-xl border-2 border-ink bg-mint/70 flex flex-wrap items-center gap-2 px-3.5 py-1.5 text-xs font-semibold text-ink shadow-[2px_2px_0_#203328]"
            data-testid="next-countdown"
          >
            <span>
              {nextEpisodeLabel} sau <strong className="text-brand-dark font-mono">{nextCountdown}</strong> giây
            </span>
            <button
              type="button"
              className="inline-flex min-h-[44px] min-w-[44px] items-center justify-center rounded-md px-2.5 font-bold underline text-ink hover:bg-paper-white/60 cursor-pointer"
              onClick={onCancelNextCountdown}
            >
              Hủy
            </button>
          </div>
        )}
      </div>

      {progressError && (
        <p role="status" className="text-xs text-muted">
          {progressError}
        </p>
      )}

      {vipDialogOpen && showVipLock && selectedEpisode && auth?.token && (
        <VipUnlockDialog
          episodeId={selectedEpisode.id}
          episodeTitle={cleanMediaTitle(selectedEpisode.title)}
          token={auth.token}
          onUnlocked={(episodeId) => {
            setVipDialogOpen(false);
            onVipUnlocked?.(episodeId);
          }}
          onClose={() => setVipDialogOpen(false)}
        />
      )}
    </>
  );
}
