import { YouTubeLink } from '@/components/ui/YouTubeLink';
import { useOptionalAuth } from '@/lib/context/AuthContext';
import React from 'react';
import { cleanMediaTitle, formatDurationMs } from '@/lib/labels';
import { foldVietnamese } from '@/features/series/seriesBrowseUtils';
import { isEpisodeExternalOnly, resolveExternalYoutubeWatchUrl } from '@/lib/services/playbackSource';
import type { CatalogEpisode, CatalogSeason } from '@/lib/services/catalogService';
import { QuickSaveButtons } from '@/features/library/components/LibraryControls';
import { VipBadge } from '@/features/profiles/components/VipBadge';

export type EpisodeSidebarProps = {
  seriesTitle?: string;
  orderedSeasons: CatalogSeason[];
  hasSeasons: boolean;
  selectedSeasonId: string | null;
  seasonEpisodes: CatalogEpisode[];
  selectedEpisode: CatalogEpisode | null;
  previousEpisode: CatalogEpisode | null;
  nextEpisode: CatalogEpisode | null;
  previousEpisodeLabel: string;
  nextEpisodeLabel: string;
  episodeQuery: string;
  onEpisodeQueryChange: (value: string) => void;
  onSelectSeason: (seasonId: string) => void;
  onSelectEpisode: (episode: CatalogEpisode) => void;
  knownExternalEpisodeIds: Record<string, boolean>;
  posterUrl: string;
  isVipSeries?: boolean;
  unlockedVipEpisodeIds?: string[];
};

function foldEpisodeText(value: string): string {
  if (typeof foldVietnamese === 'function') {
    return foldVietnamese(value);
  }
  return (value || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'd')
    .toLowerCase();
}

function formatSeasonTabLabel(season: CatalogSeason, seriesTitle?: string): string {
  const rawTitle = (season.title || '').trim();
  const cleanSeries = cleanMediaTitle(seriesTitle || '').trim();
  let stripped = rawTitle;
  if (cleanSeries && stripped) {
    const lowerRaw = foldEpisodeText(stripped);
    const lowerSeries = foldEpisodeText(cleanSeries);
    if (lowerRaw.startsWith(lowerSeries)) {
      const remainder = stripped.slice(cleanSeries.length).replace(/^\s*[-–—:·|]\s*/, '').trim();
      if (remainder) {
        stripped = remainder;
      }
    }
  }
  if (!stripped || /^các tập đánh số$/i.test(stripped) || /^cac tap danh so$/i.test(foldEpisodeText(stripped))) {
    const seasonNum = season.number ?? season.seasonNumber;
    return seasonNum ? `Mùa ${seasonNum}` : 'Phần 1';
  }
  return stripped;
}

export function EpisodeSidebar({
  seriesTitle = '',
  orderedSeasons,
  hasSeasons,
  selectedSeasonId,
  seasonEpisodes,
  selectedEpisode,
  previousEpisode,
  nextEpisode,
  previousEpisodeLabel,
  nextEpisodeLabel,
  episodeQuery,
  onEpisodeQueryChange,
  onSelectSeason,
  onSelectEpisode,
  knownExternalEpisodeIds,
  posterUrl,
  isVipSeries = false,
  unlockedVipEpisodeIds = [],
}: EpisodeSidebarProps) {
  const auth = typeof useOptionalAuth === 'function' ? useOptionalAuth() : null;
  const isKidMode = Boolean(auth?.isKidMode);
  const trimmedQuery = episodeQuery.trim();
  const foldedQuery = trimmedQuery ? foldEpisodeText(trimmedQuery) : '';
  const filteredEpisodes = foldedQuery
    ? seasonEpisodes.filter((episode) =>
        foldEpisodeText(
          `${episode.number ?? episode.episodeNumber ?? ''} ${cleanMediaTitle(episode.title)} ${episode.synopsis || ''}`
        ).includes(foldedQuery)
      )
    : seasonEpisodes;

  const showSeasonTabs =
    hasSeasons &&
    !(
      orderedSeasons.length === 1 &&
      foldEpisodeText((orderedSeasons[0]?.title || '').trim()) === 'cac tap danh so'
    );

  return (
    <div className="space-y-6">
      <div className="rounded-xl border border-surface-border bg-surface p-5">
        <div className="flex items-center justify-between border-b border-surface-border pb-3">
          <div className="flex items-center gap-2">
            <h3 className="text-sm font-bold text-ink">Danh sách tập ({seasonEpisodes.length})</h3>
          </div>
        </div>

        {showSeasonTabs && (
          <div className="mt-4 flex flex-wrap gap-2" aria-label="Chọn mùa">
            {orderedSeasons.map((season) => (
              <button
                key={season.id}
                type="button"
                data-season-id={season.id}
                aria-pressed={selectedSeasonId === season.id}
                onClick={() => onSelectSeason(season.id)}
                className={`min-h-[44px] min-w-[44px] rounded-md border px-3 py-2 text-xs font-semibold transition-colors cursor-pointer ${
                  selectedSeasonId === season.id
                    ? 'border-brand bg-brand/10 text-ink'
                    : 'border-surface-border bg-surface-elevated text-muted hover:text-ink'
                }`}
              >
                {formatSeasonTabLabel(season, seriesTitle)}
              </button>
            ))}
          </div>
        )}

        {selectedEpisode && (
          <div className="mt-4 flex flex-wrap items-center justify-between gap-2">
            <button
              type="button"
              data-testid="previous-episode"
              disabled={!previousEpisode}
              onClick={() => previousEpisode && onSelectEpisode(previousEpisode)}
              className="btn-secondary min-h-[44px] px-3 py-2 text-xs disabled:cursor-not-allowed disabled:opacity-40"
            >
              {previousEpisodeLabel}
            </button>
            <span className="font-mono text-[0.6875rem] font-semibold text-muted tabular-nums">
              {seasonEpisodes.findIndex((episode) => episode.id === selectedEpisode.id) + 1}/{seasonEpisodes.length} trong mùa
            </span>
            <button
              type="button"
              data-testid="next-episode"
              disabled={!nextEpisode}
              onClick={() => nextEpisode && onSelectEpisode(nextEpisode)}
              className="btn-secondary min-h-[44px] px-3 py-2 text-xs disabled:cursor-not-allowed disabled:opacity-40"
            >
              {nextEpisodeLabel}
            </button>
          </div>
        )}

        <label htmlFor="episode-search-input" className="mt-4 block">
          <span className="sr-only">Tìm tập trong phim bộ này</span>
          <input
            id="episode-search-input"
            name="episodeQuery"
            data-testid="episode-search-input"
            type="search"
            value={episodeQuery}
            onChange={(event) => onEpisodeQueryChange(event.target.value)}
            placeholder="Tìm tập theo số hoặc tên…"
            className="min-h-[44px] w-full rounded-md border border-surface-border bg-surface-elevated px-3 py-2 text-base sm:text-sm text-ink placeholder:text-muted focus:border-brand focus:outline-none"
          />
        </label>

        <div className="mt-3 max-h-[600px] space-y-2 overflow-y-auto">
          {filteredEpisodes.length === 0 ? (
            <p className="rounded-md border border-dashed border-surface-border p-4 text-xs text-muted">
              {seasonEpisodes.length ? 'Không tìm thấy tập phù hợp.' : 'Mùa này chưa có tập nào.'}
            </p>
          ) : (
            filteredEpisodes.map((episode, index) => {
              const isSelected = selectedEpisode?.id === episode.id;
              const displayTitle = cleanMediaTitle(episode.title);
              const isExternalOnly = Boolean(knownExternalEpisodeIds[episode.id] || isEpisodeExternalOnly(episode));
              const externalUrl = isExternalOnly ? resolveExternalYoutubeWatchUrl(episode) : null;
              return (
                <div
                  key={episode.id}
                  style={index < 12 ? ({ '--ep-idx': index } as React.CSSProperties) : undefined}
                  className={`${index < 12 ? 'motion-episode-item ' : ''}rounded-lg border p-2 transition-colors ${
                    isSelected
                      ? 'border-ink border-l-4 border-l-ink bg-mint/45 text-ink'
                      : 'border-surface-border bg-surface-elevated text-muted hover:bg-surface-hover hover:text-ink'
                  }`}
                >
                  <div className="flex items-start gap-2">
                    <button
                      type="button"
                      data-episode-id={episode.id}
                      aria-pressed={isSelected}
                      onClick={() => onSelectEpisode(episode)}
                      className="flex min-h-[44px] min-w-0 flex-1 items-start justify-between gap-3 p-1 text-left cursor-pointer"
                    >
                      <div className="min-w-0">
                        <div className="flex items-start gap-1.5">
                          <span className={`grid h-5 min-w-[20px] shrink-0 place-items-center rounded px-1 text-[0.625rem] font-bold border ${
                            isSelected
                              ? 'border-ink bg-ink text-[#fffdf6]'
                              : 'border-surface-border bg-surface text-brand-dark'
                          }`}>
                            {episode.number ?? episode.episodeNumber}
                          </span>
                          <strong className="min-w-0 flex-1 break-words text-xs font-semibold leading-5 text-ink">{displayTitle}</strong>
                        </div>
                        {((isKidMode && isVipSeries) || isSelected) && (
                          <div className="mt-1 flex flex-wrap items-center gap-1.5">
                            {isKidMode && isVipSeries && (
                              <VipBadge
                                unlocked={unlockedVipEpisodeIds.includes(episode.id)}
                                data-testid={`episode-vip-badge-${episode.id}`}
                              />
                            )}
                            {isSelected && (
                              <span className="rounded bg-[#f2d18c] border border-ink/30 px-1.5 py-0.5 text-[0.625rem] font-bold text-ink">
                                Đang xem
                              </span>
                            )}
                          </div>
                        )}
                        {isExternalOnly && !isKidMode && (
                          <span
                            data-testid="episode-external-badge"
                            className="mt-1 inline-flex items-center rounded border border-amber-500/40 bg-amber-500/10 px-1.5 py-0.5 text-[0.625rem] font-semibold text-amber-900"
                          >
                            Chỉ mở trên YouTube
                          </span>
                        )}
                        {episode.synopsis && (
                          <p className="mt-1 line-clamp-2 text-[0.6875rem] text-muted">{episode.synopsis}</p>
                        )}
                      </div>

                      {episode.durationMs && (
                        <span className="font-mono text-[0.625rem] text-muted shrink-0">
                          {formatDurationMs(episode.durationMs)}
                        </span>
                      )}
                    </button>

                    <div className="shrink-0 self-center">
                      <QuickSaveButtons
                        itemId={episode.id}
                        title={displayTitle}
                        poster={posterUrl}
                        variant="inline"
                        testIdPrefix={`episode-row-${episode.id}`}
                      />
                    </div>
                  </div>

                  {isExternalOnly && !isKidMode && externalUrl && (
                    <div className="mt-1.5 flex items-center justify-end border-t border-surface-border/60 pt-1.5">
                      <YouTubeLink
                        url={externalUrl}
                        testId="episode-row-youtube-link"
                        className="inline-flex min-h-[44px] items-center rounded-md border border-ink/30 bg-surface px-3 py-1.5 text-xs font-semibold text-brand-dark hover:bg-mint"
                      >
                        Mở trên YouTube
                      </YouTubeLink>
                    </div>
                  )}
                </div>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
}
