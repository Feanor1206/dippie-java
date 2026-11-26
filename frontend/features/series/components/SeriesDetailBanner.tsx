import { safeImageUrl } from '@/lib/safeUrl';
import React, { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { normalizeGenreLabels, resolveRatingSummary } from '@/lib/labels';
import type { CatalogSeriesSummary } from '@/lib/services/catalogService';
import type { SeriesCard } from '@/lib/services/seriesCardService';
import { formatSeriesCounts, formatYearRange, pickSeriesArtwork } from '@/features/series/seriesBrowseUtils';
import { VipBadge } from '@/features/profiles/components/VipBadge';

export type SeriesDetailBannerProps = {
  series: CatalogSeriesSummary;
  card: SeriesCard | null;
  seasonCount: number;
  episodeCount: number;
  nextEpisodeAction: React.ReactNode;
  seriesActions?: React.ReactNode;
  isVipSeries?: boolean;
};

function parseFallbackGenreIds(series: CatalogSeriesSummary): string[] {
  if (typeof series.genreIds === 'string' && series.genreIds.trim()) {
    return series.genreIds.split(',');
  }
  if (typeof series.genreIdsJson === 'string' && series.genreIdsJson.trim()) {
    try {
      const parsed = JSON.parse(series.genreIdsJson);
      if (Array.isArray(parsed)) return parsed.filter((item): item is string => typeof item === 'string');
    } catch {
      return series.genreIdsJson.split(',');
    }
  }
  return [];
}

export function SeriesDetailBanner({
  series,
  card,
  seasonCount,
  episodeCount,
  nextEpisodeAction,
  seriesActions,
  isVipSeries = false,
}: SeriesDetailBannerProps) {
  const artwork = pickSeriesArtwork(card || {}, 'backdrop');
  const [stage, setStage] = useState<0 | 1 | 2>(0);

  useEffect(() => {
    setStage(0);
  }, [artwork.primary, artwork.fallback]);

  const activeBackdrop = stage === 0 ? artwork.primary : stage === 1 ? artwork.fallback : null;
  const hasBackdrop = Boolean(activeBackdrop);
  const years = formatYearRange(
    card?.releaseYearStart ?? series.releaseYearStart,
    card?.releaseYearEnd ?? series.releaseYearEnd
  );
  const counts = formatSeriesCounts(
    card?.seasonCount ?? (seasonCount > 0 ? seasonCount : null),
    card?.episodeCount ?? (episodeCount > 0 ? episodeCount : null)
  );
  const genreLabels = normalizeGenreLabels(
    card && card.genres.length > 0 ? card.genres : parseFallbackGenreIds(series)
  );
  const synopsis = series.synopsis || card?.synopsis;
  const originalTitle = series.originalTitle || card?.originalTitle;
  const ratingSummary = resolveRatingSummary(card);

  return (
    <header
      data-testid="series-detail-banner"
      data-reveal-section=""
      className={`series-detail-banner mb-6 ${hasBackdrop ? 'series-detail-banner--art' : 'series-detail-banner--plain'}`}
    >
      {activeBackdrop && (
        <>
          <img
            src={safeImageUrl(activeBackdrop)}
            alt=""
            aria-hidden="true"
            loading="lazy"
            decoding="async"
            referrerPolicy="no-referrer"
            onError={() => setStage((prev) => (prev === 0 && artwork.fallback ? 1 : 2))}
            className="series-detail-banner-img"
          />
          <div className="series-detail-banner-scrim" aria-hidden="true" />
        </>
      )}

      <div className="relative z-10 p-5 sm:p-7">
        <nav
          aria-label="Đường dẫn"
          className={`flex flex-wrap items-center gap-2 text-xs font-semibold ${
            hasBackdrop ? 'text-[#dce8d4]' : 'text-muted'
          }`}
        >
          <Link
            to="/series"
            data-testid="series-breadcrumb-link"
            className={`inline-flex min-h-[44px] min-w-[44px] items-center underline-offset-4 hover:underline ${
              hasBackdrop ? 'hover:text-white' : 'hover:text-ink'
            }`}
          >
            Phim bộ
          </Link>
          <span aria-hidden="true">/</span>
          <span aria-current="page" className="truncate">
            {series.title}
          </span>
        </nav>

        <div className="mt-3 flex flex-col gap-5 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2.5">
              <h1 className={`break-words text-2xl font-bold leading-tight sm:text-4xl ${hasBackdrop ? 'text-[#fffdf6]' : 'text-ink'}`}>
                {series.title}
              </h1>
              {isVipSeries && <VipBadge data-testid="series-banner-vip-badge" />}
            </div>

            <div
              className={`mt-2.5 flex flex-wrap items-center gap-x-2.5 gap-y-1.5 text-sm font-medium ${
                hasBackdrop ? 'text-[#e6eedf]' : 'text-muted'
              }`}
            >
              {years && (
                <span
                  data-testid="series-banner-years"
                  className={
                    hasBackdrop
                      ? 'rounded bg-[#18241e]/60 px-2 py-0.5 text-xs font-semibold text-[#fffdf6]'
                      : 'rounded bg-surface-elevated px-2 py-0.5 text-xs font-semibold text-ink'
                  }
                >
                  {years}
                </span>
              )}
              {counts && (
                <span
                  data-testid="series-banner-counts"
                  className={
                    hasBackdrop
                      ? 'rounded bg-[#18241e]/60 px-2 py-0.5 text-xs font-semibold text-[#fffdf6]'
                      : 'rounded bg-surface-elevated px-2 py-0.5 text-xs font-semibold text-ink'
                  }
                >
                  {counts}
                </span>
              )}
              <span
                data-testid="series-banner-rating"
                className={
                  hasBackdrop
                    ? 'rounded bg-[#f2d18c]/20 border border-[#f2d18c]/40 px-2 py-0.5 text-xs font-bold text-[#f7dfa8]'
                    : 'rounded bg-[#f2d18c]/40 border border-ink/20 px-2 py-0.5 text-xs font-bold text-ink'
                }
              >
                {ratingSummary.hasRating ? `★ ${ratingSummary.label}` : ratingSummary.label}
              </span>
            </div>

            {genreLabels.length > 0 && (
              <div className="mt-3 flex flex-wrap gap-1.5" data-testid="series-banner-genres">
                {genreLabels.map((label) => (
                  <span
                    key={label}
                    className={
                      hasBackdrop
                        ? 'inline-flex items-center rounded border border-[#c9ddbd]/40 bg-[#18241e]/70 px-2.5 py-0.5 text-xs font-semibold text-[#f7f3e9]'
                        : 'series-genre-pill'
                    }
                  >
                    {label}
                  </span>
                ))}
              </div>
            )}
          </div>

          <div className="shrink-0 sm:pt-1">{nextEpisodeAction}</div>
        </div>

        {seriesActions}

        {(synopsis || originalTitle || series.studios) && (
          <details className={`mt-4 max-w-3xl text-sm ${hasBackdrop ? 'text-[#e6eedf]/90' : 'text-muted'}`}>
            <summary
              className={`inline-flex min-h-[44px] w-fit cursor-pointer items-center rounded-lg border px-3 text-xs font-bold transition-colors ${
                hasBackdrop
                  ? 'border-[#c9ddbd]/40 bg-[#18241e]/65 text-[#fffdf6] hover:bg-[#18241e]/85'
                  : 'border-ink/25 bg-paper-white/80 text-ink hover:bg-mint/50'
              }`}
            >
              Giới thiệu phim bộ
            </summary>
            <div
              className={`motion-details-body mt-2.5 space-y-2 rounded-lg p-3.5 text-sm leading-relaxed ${
                hasBackdrop ? 'bg-[#18241e]/75 text-[#f7f3e9]' : 'border border-surface-border/30 bg-paper-white/90 text-ink'
              }`}
            >
              {synopsis && <p>{synopsis}</p>}
              {originalTitle && <p className="text-xs opacity-90">Tên gốc: {originalTitle}</p>}
              {series.studios && <p className="text-xs opacity-90">Hãng phim: {series.studios}</p>}
            </div>
          </details>
        )}
      </div>
    </header>
  );
}
