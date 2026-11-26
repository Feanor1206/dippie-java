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
import type { LootFeedbackBanner } from '@/features/profiles/hooks/usePlayFeatures';
import type {
  LootDropAnswerResult,
  LootDropQuiz,
  LootDropSpawn,
  WatchPetStatus,
} from '@/lib/services/playFeaturesService';
import React, { useEffect, useState } from 'react';
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
  isVipLocked?: boolean;
  vipMathLevel?: number;
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
    lootFeedback: LootFeedbackBanner | null;
    lootOpening: boolean;
    lootSubmitting: boolean;
    onOpenLootChest: () => Promise<void>;
    onSubmitLootAnswer: (answer: number) => Promise<LootDropAnswerResult | null>;
    activeRecapEpisodeId: string | null;
    onRecapUnlocked: (targetEpisodeId: string | null) => void;
    onRecapRewatch: () => void;
  };
};

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
  isVipLocked = false,
  vipMathLevel = 2,
  onVipUnlocked,
  kidPlayOverlay,
}: EpisodePlayerPanelProps) {
  const auth = typeof useOptionalAuth === 'function' ? useOptionalAuth() : null;
  const screenTime = typeof useOptionalScreenTime === 'function' ? useOptionalScreenTime() : null;
  const isKidMode = Boolean(auth?.isKidMode);
  const isScreenTimeLocked = Boolean(isKidMode && screenTime?.isLocked);
  const isKidPlayBlocked = Boolean(
    isKidMode && (kidPlayOverlay?.isPetLocked || kidPlayOverlay?.activeRecapEpisodeId)
  );
  const showVipLock = Boolean(isKidMode && isVipLocked && selectedEpisode && auth?.token && !isScreenTimeLocked);
  const isPlaybackPaused = isScreenTimeLocked || showVipLock || isKidPlayBlocked;
  const [holdSolvedOverlay, setHoldSolvedOverlay] = useState(false);
  const [vipDialogOpen, setVipDialogOpen] = useState(false);

  useEffect(() => {
    setVipDialogOpen(false);
  }, [selectedEpisode?.id]);

  useEffect(() => {
    if (!isPlaybackPaused) return;
    try {
      videoRef.current?.pause();
    } catch {}
  }, [isPlaybackPaused, videoRef]);

  const showLockOverlay = Boolean(
    isKidMode && (isScreenTimeLocked || holdSolvedOverlay) && screenTime?.screenTime && auth?.token
  );
  const youtubeEmbed = playbackSource.kind === 'youtube';
  const youtubeUrl =
    playbackSource.kind === 'youtube'
      ? playbackSource.watchUrl
      : playbackSource.kind === 'unavailable'
      ? playbackSource.youtubeWatchUrl
      : null;

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

      {playbackSource.kind === 'youtube' &&
        playbackSource.isFallback &&
        (isUnmountedDriveFallback ? (
          <div
            role="status"
            aria-live="polite"
            data-testid="playback-source-note"
            className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-surface-border bg-surface-muted px-3 py-2 text-xs text-muted"
          >
            <span>Đang phát từ YouTube vì bản lưu trên máy chủ chưa sẵn sàng.</span>
          </div>
        ) : (
          <div
            role="status"
            aria-live="polite"
            data-testid="playback-fallback-notice"
            className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-amber-500/30 bg-amber-500/10 px-3 py-2 text-xs text-amber-900"
          >
            <span>Bản video trên máy chủ chưa sẵn sàng, đang phát từ YouTube.</span>
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

      {isKidMode && screenTime?.isLimited && (
        <div className="flex flex-wrap items-center justify-between gap-2">
          <ScreenTimeChip
            data-testid="player-screen-time-chip"
            remainingSeconds={screenTime.remainingSeconds}
            locked={screenTime.isLocked}
            variant="player"
          />
        </div>
      )}

      <div
        className={`relative overflow-hidden rounded-xl border border-surface-border shadow-2xl aspect-video flex items-center justify-center ${
          !isLoadingEpisode && !isResolvingStream && !episodeError && playbackSource.kind === 'unavailable'
            ? 'bg-surface'
            : 'bg-black'
        } ${youtubeEmbed ? 'min-h-[200px]' : ''}`}
      >
        {showVipLock && selectedEpisode ? (
          <div
            data-testid="vip-episode-lock-overlay"
            className="flex h-full w-full flex-col items-center justify-center bg-ink/95 p-6 text-center text-paper-white"
          >
            <VipBadge data-testid="player-vip-badge" />
            <h3 className="mt-3 font-display text-lg font-bold text-paper-white sm:text-xl">
              Tập phim VIP đang khóa
            </h3>
            <p className="mt-2 max-w-md text-xs leading-relaxed text-zinc-200 sm:text-sm">
              Bé hãy giải đúng 1 bài toán mức {vipMathLevel} để mở khóa tập này trong hôm nay nhé!
            </p>
            <button
              type="button"
              data-testid="open-vip-unlock-button"
              onClick={() => setVipDialogOpen(true)}
              className="btn-primary mt-4 min-h-[44px] text-xs font-bold sm:text-sm"
            >
              Giải toán mở khóa tập này
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
        ) : showLockOverlay || isKidPlayBlocked ? (
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
                className="mb-2 inline-flex items-center rounded border border-amber-500/40 bg-amber-500/10 px-2.5 py-0.5 text-[11px] font-semibold text-amber-900"
              >
                Chỉ mở trên YouTube
              </span>
            )}
            <p className="text-xs font-semibold text-brand-dark">
              {cleanMediaTitle(episodeDetail?.episode?.title || selectedEpisode?.title || seriesTitle)}
            </p>
            <h3 className="mt-1 text-base font-bold text-ink sm:text-lg">Chưa phát được tập này</h3>
            <p className="mt-2 max-w-md text-xs leading-relaxed text-muted">
              {isKidMode &&
              playbackSource.kind === 'unavailable' &&
              (playbackSource.reason === 'youtube_embed_disabled' ||
                playbackSource.reason === 'file_unreachable_youtube_external')
                ? 'Tập này tạm chưa xem được trong hồ sơ trẻ em.'
                : getUnavailableReasonMessage(
                    playbackSource.kind === 'unavailable' ? playbackSource.reason : 'file_unreachable'
                  )}
            </p>
            <div className="mt-4 flex flex-wrap items-center justify-center gap-3">
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
            onStatusUpdated={(next) => {
              if (!next.locked) {
                setHoldSolvedOverlay(true);
              }
              screenTime.applyScreenTimeStatus(next);
            }}
            onContinueWatching={() => setHoldSolvedOverlay(false)}
          />
        )}
        {isKidMode && auth?.token && kidPlayOverlay && !showLockOverlay && !showVipLock && (
          <>
            <WatchPetWidget
              watchPet={kidPlayOverlay.watchPet}
              token={auth.token}
              restCountdown={kidPlayOverlay.restCountdown}
              onStartRest={kidPlayOverlay.onStartRest}
              onWatchPetUpdated={kidPlayOverlay.onWatchPetUpdated}
            />
            {!kidPlayOverlay.isPetLocked && !kidPlayOverlay.activeRecapEpisodeId && (
              <LootDropOverlay
                activeDrop={kidPlayOverlay.activeDrop}
                dropSecondsLeft={kidPlayOverlay.dropSecondsLeft}
                activeQuiz={kidPlayOverlay.activeLootQuiz}
                quizSecondsLeft={kidPlayOverlay.lootQuizSecondsLeft}
                feedback={kidPlayOverlay.lootFeedback}
                opening={kidPlayOverlay.lootOpening}
                submitting={kidPlayOverlay.lootSubmitting}
                onOpenChest={kidPlayOverlay.onOpenLootChest}
                onSubmitAnswer={kidPlayOverlay.onSubmitLootAnswer}
              />
            )}
            {!kidPlayOverlay.isPetLocked && kidPlayOverlay.activeRecapEpisodeId && (
              <RecapQuizOverlay
                token={auth.token}
                episodeId={kidPlayOverlay.activeRecapEpisodeId}
                nextEpisodeLabel={nextEpisodeLabel}
                onUnlocked={kidPlayOverlay.onRecapUnlocked}
                onRewatchEpisode={kidPlayOverlay.onRecapRewatch}
              />
            )}
          </>
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
          className="rounded-xl border-2 border-ink bg-[#f2d18c]/35 flex flex-wrap items-center justify-between gap-3 p-4 shadow-[2px_2px_0_#203328]"
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
            className="rounded-xl border-2 border-ink bg-mint/70 flex flex-wrap items-center gap-2 px-3.5 py-1.5 text-xs font-semibold text-ink shadow-[2px_2px_0_#203328]"
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

      {vipDialogOpen && selectedEpisode && auth?.token && (
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
