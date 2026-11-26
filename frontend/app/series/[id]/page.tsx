import React from 'react';
import { Link, useParams, useSearchParams } from 'react-router';
import { cleanMediaTitle, formatDurationMs, resolveRatingSummary } from '@/lib/labels';
import { NextEpisodeAction } from '@/features/series/components/NextEpisodeAction';
import { SeriesDetailBanner } from '@/features/series/components/SeriesDetailBanner';
import { ItemLibraryPanel } from '@/features/library/components/LibraryControls';
import { EpisodePlayerPanel } from '@/features/series/components/EpisodePlayerPanel';
import { EpisodeSidebar } from '@/features/series/components/EpisodeSidebar';
import {
  EpisodePlaybackTracker,
  extractEpisodeRating,
  useSeriesPlayback,
} from '@/features/series/hooks/useSeriesPlayback';

export { EpisodePlaybackTracker };

type TimelineMarkerItem = {
  id: string;
  timeLabel: string;
  title: string;
  description?: string;
  onSelect: () => void;
};

function TimelineListCard({
  heading,
  hint,
  kind,
  activeId,
  items,
}: {
  heading: string;
  hint: string;
  kind: 'segment' | 'scene';
  activeId: string | null;
  items: TimelineMarkerItem[];
}) {
  return (
    <div className="rounded-xl border border-surface-border bg-surface p-5">
      <div className="flex items-center justify-between border-b border-surface-border pb-3">
        <div className="flex items-center gap-2">
          <h3 className="text-sm font-bold text-ink">
            {heading} ({items.length})
          </h3>
        </div>
        <span className="text-xs text-muted">{hint}</span>
      </div>

      <div className="mt-3 divide-y divide-surface-border">
        {items.map((item) => {
          const isActive = activeId === item.id;
          const dataAttributes =
            kind === 'segment' ? { 'data-segment-id': item.id } : { 'data-scene-id': item.id };
          return (
            <div
              key={item.id}
              {...dataAttributes}
              role="button"
              tabIndex={0}
              onClick={item.onSelect}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  item.onSelect();
                }
              }}
              className={`flex min-h-[44px] items-start justify-between gap-4 p-3 rounded-lg transition-colors cursor-pointer outline-none focus:ring-2 focus:ring-brand ${
                isActive
                  ? 'bg-brand/15 border border-brand/30'
                  : 'hover:bg-surface-hover/60 border border-transparent'
              }`}
            >
              <div className="space-y-1">
                <div className="flex items-center gap-2">
                  <span className="font-mono text-xs font-bold text-brand-dark">{item.timeLabel}</span>
                  <span className="text-xs font-bold text-ink">{item.title}</span>
                </div>
                {item.description && (
                  <p className="text-xs text-muted line-clamp-2">{item.description}</p>
                )}
              </div>

              <span className="btn-secondary flex items-center gap-1 text-[11px] font-semibold shrink-0 py-1 px-2.5 pointer-events-none">
                <span>Tua tới</span>
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

export default function SeriesDetailPage() {
  const { id: seriesId = '' } = useParams<'id'>();
  const [searchParams, setSearchParams] = useSearchParams();
  const playback = useSeriesPlayback(seriesId, searchParams, setSearchParams);

  if (playback.isLoadingSeries) {
    return (
      <main id="main-content" tabIndex={-1} className="series-page mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
        <div role="status" className="space-y-6 animate-pulse">
          <span className="sr-only">Đang tải phim bộ…</span>
          <div className="rounded-xl border border-surface-border/40 bg-paper-white p-6 sm:p-8 space-y-3">
            <div className="h-3.5 w-28 rounded bg-surface-elevated" />
            <div className="h-8 w-2/3 max-w-md rounded bg-surface-elevated" />
            <div className="flex gap-2">
              <div className="h-5 w-16 rounded bg-surface-elevated" />
              <div className="h-5 w-24 rounded bg-surface-elevated" />
              <div className="h-5 w-20 rounded bg-surface-elevated" />
            </div>
          </div>
          <div className="grid grid-cols-1 gap-8 lg:grid-cols-3">
            <div className="lg:col-span-2 space-y-4">
              <div className="aspect-video w-full rounded-xl border border-surface-border/40 bg-surface-elevated" />
              <div className="h-28 rounded-xl border border-surface-border/40 bg-paper-white" />
            </div>
            <div className="rounded-xl border border-surface-border/40 bg-paper-white p-5 space-y-3">
              <div className="h-5 w-1/2 rounded bg-surface-elevated" />
              <div className="h-12 rounded bg-surface-elevated" />
              <div className="h-12 rounded bg-surface-elevated" />
              <div className="h-12 rounded bg-surface-elevated" />
            </div>
          </div>
        </div>
      </main>
    );
  }

  if (playback.error || !playback.seriesData) {
    return (
      <main id="main-content" tabIndex={-1} className="mx-auto max-w-xl px-4 py-16 text-center">
        <div className="rounded-2xl border-2 border-ink bg-paper-white p-8 shadow-brand">
          <h1 className="comic-heading text-2xl font-bold text-ink">Chưa tải được phim bộ này</h1>
          <p className="mt-2 text-sm text-muted">{playback.error || 'Không tìm thấy phim bộ này.'}</p>
          <div className="mt-6 flex flex-wrap items-center justify-center gap-3">
            <button
              type="button"
              onClick={playback.retrySeries}
              className="btn-primary inline-flex min-h-[44px] items-center gap-2 text-xs"
            >
              Thử tải lại
            </button>
            <Link to="/search" className="btn-secondary inline-flex min-h-[44px] items-center gap-2 text-xs">
              <span>Về kho phim</span>
            </Link>
          </div>
        </div>
      </main>
    );
  }

  const { series, seasons, episodes } = playback.seriesData;
  const selectedEpisode = playback.selectedEpisode;
  const activeEpisodeRating =
    selectedEpisode && playback.episodeRating && playback.episodeRating.episodeId === selectedEpisode.id
      ? playback.episodeRating
      : selectedEpisode
      ? extractEpisodeRating(
          playback.episodeDetail?.episode?.id === selectedEpisode.id ? playback.episodeDetail : null,
          selectedEpisode
        )
      : null;
  const episodeRatingSummary = resolveRatingSummary(activeEpisodeRating);

  const segmentItems: TimelineMarkerItem[] = (playback.episodeDetail?.segments || []).map((segment) => ({
    id: segment.id,
    timeLabel:
      segment.episodeStartMs !== undefined
        ? `${formatDurationMs(segment.episodeStartMs)}${segment.episodeEndMs ? ` đến ${formatDurationMs(segment.episodeEndMs)}` : ''}`
        : `Phân đoạn ${segment.position}`,
    title: segment.title || `Phân đoạn ${segment.position}`,
    description: segment.synopsis,
    onSelect: () => playback.handleSeekSegment(segment),
  }));

  const sceneItems: TimelineMarkerItem[] = (playback.episodeDetail?.scenes || []).map((scene) => ({
    id: scene.id,
    timeLabel: `${formatDurationMs(scene.startMs)} đến ${formatDurationMs(scene.endMs)}`,
    title: scene.title || 'Phân cảnh',
    description: scene.visualDescription || scene.summary,
    onSelect: () => playback.handleSeekScene(scene),
  }));

  return (
    <main id="main-content" tabIndex={-1} className="series-page mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <SeriesDetailBanner
        series={series}
        card={playback.seriesCard}
        seasonCount={seasons.length}
        episodeCount={episodes.length}
        isVipSeries={playback.isVipSeries}
        nextEpisodeAction={
          <NextEpisodeAction
            seriesId={seriesId}
            progressRevision={playback.seriesProgressRevision}
            onSelectRecommendation={playback.handlePlayNextEpisode}
          />
        }
        seriesActions={
          <ItemLibraryPanel
            itemId={series.id}
            title={series.title}
            poster={playback.seriesCard?.posterUrl || playback.seriesCard?.backdropUrl || ''}
            scopeLabel="Phim bộ"
            testIdPrefix="series"
            compact
          />
        }
      />

      <div className="grid grid-cols-1 gap-8 lg:grid-cols-3">
        <div className="min-w-0 lg:col-span-2 space-y-4">
          <EpisodePlayerPanel
            seriesTitle={series.title}
            selectedEpisode={selectedEpisode}
            episodeDetail={playback.episodeDetail}
            playbackSource={playback.playbackSource}
            isLoadingEpisode={playback.isLoadingEpisode}
            isResolvingStream={playback.isResolvingStream}
            episodeError={playback.episodeError}
            playbackWarning={playback.playbackWarning}
            onDismissWarning={() => playback.setPlaybackWarning(null)}
            isUnmountedDriveFallback={playback.isUnmountedDriveFallback}
            onRetryOriginalFile={playback.handleRetryOriginalFile}
            onRetryEpisode={playback.retryEpisode}
            streamUrl={playback.streamUrl}
            fileRetryCount={playback.fileRetryCount}
            videoRef={playback.videoRef}
            youtubeSeekRequest={playback.youtubeSeekRequest}
            shouldAutoPlayYoutube={playback.shouldAutoPlayYoutube}
            youtubeHandlers={playback.youtubeHandlers}
            videoHandlers={playback.videoHandlers}
            offerResume={playback.offerResume}
            savedProgress={playback.savedProgress}
            onResumePlayback={playback.handleResumePlayback}
            autoNext={playback.autoNext}
            onAutoNextToggle={playback.handleAutoNextToggle}
            nextCountdown={playback.nextCountdown}
            nextEpisodeLabel={playback.nextEpisodeLabel}
            hasNextEpisode={Boolean(playback.nextEpisode)}
            onCancelNextCountdown={() => playback.setNextCountdown(null)}
            progressError={playback.progressError}
            isVipLocked={playback.isSelectedEpisodeVipLocked}
            vipMathLevel={playback.vipMathLevel}
            onVipUnlocked={playback.markVipEpisodeUnlocked}
            kidPlayOverlay={{
              watchPet: playback.playFeatures.watchPet,
              isPetLocked: playback.isPetLocked,
              restCountdown: playback.playFeatures.restCountdown,
              onStartRest: playback.playFeatures.handleStartRest,
              onWatchPetUpdated: playback.playFeatures.applyWatchPetStatus,
              activeDrop: playback.playFeatures.activeDrop,
              dropSecondsLeft: playback.playFeatures.dropSecondsLeft,
              activeLootQuiz: playback.playFeatures.activeLootQuiz,
              lootQuizSecondsLeft: playback.playFeatures.lootQuizSecondsLeft,
              lootFeedback: playback.playFeatures.lootFeedback,
              lootOpening: playback.playFeatures.lootOpening,
              lootSubmitting: playback.playFeatures.lootSubmitting,
              onOpenLootChest: playback.playFeatures.handleOpenLootChest,
              onSubmitLootAnswer: playback.playFeatures.handleSubmitLootAnswer,
              activeRecapEpisodeId: playback.activeRecapEpisodeId,
              onRecapUnlocked: playback.handleRecapUnlocked,
              onRecapRewatch: playback.handleRecapRewatch,
            }}
          />

          {selectedEpisode && (
            <div className="rounded-xl border border-surface-border bg-surface p-5">
              <div className="flex flex-wrap items-start justify-between gap-3 border-b border-surface-border pb-3">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="inline-flex items-center rounded bg-mint px-2 py-0.5 text-[11px] font-bold text-ink">
                      Đang phát · Tập {selectedEpisode.number ?? selectedEpisode.episodeNumber}
                    </span>
                  </div>
                  <h2 className="mt-1.5 text-lg font-bold text-ink break-words">{cleanMediaTitle(selectedEpisode.title)}</h2>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <span
                    data-testid="episode-header-rating"
                    className="inline-flex items-center gap-1 rounded bg-surface-elevated px-2 py-1 text-xs font-medium text-muted border border-surface-border"
                  >
                    {episodeRatingSummary.hasRating ? `★ ${episodeRatingSummary.label}` : episodeRatingSummary.label}
                  </span>
                  {selectedEpisode.durationMs && (
                    <span className="flex items-center gap-1 rounded bg-surface-elevated px-2 py-1 text-xs text-muted border border-surface-border">
                      {formatDurationMs(selectedEpisode.durationMs)}
                    </span>
                  )}
                </div>
              </div>

              {selectedEpisode.synopsis && (
                <p className="mt-3 text-xs leading-relaxed text-muted">{selectedEpisode.synopsis}</p>
              )}

              <ItemLibraryPanel
                itemId={selectedEpisode.id}
                title={cleanMediaTitle(selectedEpisode.title)}
                poster={playback.seriesCard?.posterUrl || ''}
                scopeLabel="Tập này"
                testIdPrefix="episode"
              />

              {playback.episodeDetail?.mediaAssets && playback.episodeDetail.mediaAssets.length > 0 && (
                <div className="mt-4 flex flex-wrap items-center gap-2 border-t border-surface-border pt-3 text-xs">
                  <span className="text-muted">Nguồn phát:</span>
                  {playback.episodeDetail.mediaAssets.map((asset) => (
                    <button
                      key={asset.id}
                      data-testid="media-asset-button"
                      data-asset-id={asset.id}
                      aria-pressed={playback.selectedAsset?.id === asset.id}
                      onClick={() => playback.handleSelectAsset(asset)}
                      className={`min-h-11 max-w-full break-words rounded px-2.5 py-1 text-xs font-semibold transition-colors cursor-pointer border ${
                        playback.selectedAsset?.id === asset.id
                          ? 'border-brand bg-brand/10 text-brand-dark'
                          : 'border-surface-border bg-surface-elevated text-muted hover:text-ink'
                      }`}
                    >
                      {asset.editionLabel || (asset.storageProvider === 'drive' ? 'Google Drive' : 'Máy chủ Dippie')}
                      {asset.available === false ? ' (Chưa sẵn sàng)' : ''}
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}

          {segmentItems.length > 0 && (
            <TimelineListCard
              heading="Phân đoạn"
              hint="Bấm để tua tới"
              kind="segment"
              activeId={playback.activeSegmentId}
              items={segmentItems}
            />
          )}

          {sceneItems.length > 0 && (
            <TimelineListCard
              heading="Phân cảnh"
              hint="Bấm để tua tới"
              kind="scene"
              activeId={playback.activeSceneId}
              items={sceneItems}
            />
          )}
        </div>

        <EpisodeSidebar
          orderedSeasons={playback.orderedSeasons}
          hasSeasons={playback.hasSeasons}
          selectedSeasonId={playback.selectedSeasonId}
          seasonEpisodes={playback.seasonEpisodes}
          selectedEpisode={selectedEpisode}
          previousEpisode={playback.previousEpisode}
          nextEpisode={playback.nextEpisode}
          previousEpisodeLabel={playback.previousEpisodeLabel}
          nextEpisodeLabel={playback.nextEpisodeLabel}
          episodeQuery={playback.episodeQuery}
          onEpisodeQueryChange={playback.setEpisodeQuery}
          onSelectSeason={playback.handleSelectSeason}
          onSelectEpisode={playback.handleSelectEpisode}
          knownExternalEpisodeIds={playback.knownExternalEpisodeIds}
          posterUrl={playback.seriesCard?.posterUrl || ''}
          isVipSeries={playback.isVipSeries}
          unlockedVipEpisodeIds={playback.unlockedVipEpisodeIds}
        />
      </div>
    </main>
  );
}
