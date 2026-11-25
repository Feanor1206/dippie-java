import React, { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { genreLabel } from '@/lib/labels';
import type { CatalogSeriesSummary } from '@/lib/services/catalogService';
import type { SeriesCard, SeriesCardGenre } from '@/lib/services/seriesCardService';
import {
  formatSeriesCounts,
  formatYearRange,
  pickSeriesArtwork,
} from '@/features/series/seriesBrowseUtils';

export type SeriesDetailBannerProps = {
  series: CatalogSeriesSummary;
  card: SeriesCard | null;
  seasonCount: number;
  episodeCount: number;
  nextEpisodeAction: React.ReactNode;
};

function resolveFallbackGenres(series: CatalogSeriesSummary): SeriesCardGenre[] {
  const rawList: string[] = [];
  if (typeof series.genreIds === 'string' && series.genreIds.trim()) {
    rawList.push(...series.genreIds.split(','));
  } else if (typeof series.genreIdsJson === 'string' && series.genreIdsJson.trim()) {
    try {
      const parsed = JSON.parse(series.genreIdsJson);
      if (Array.isArray(parsed)) {
        for (const item of parsed) {
          if (typeof item === 'string') rawList.push(item);
        }
      }
    } catch {
      rawList.push(...series.genreIdsJson.split(','));
    }
  }
  const seen = new Set<string>();
  const out: SeriesCardGenre[] = [];
  for (const item of rawList) {
    const clean = item.trim();
    if (!clean) continue;
    const key = clean.toLocaleLowerCase('vi');
    if (seen.has(key)) continue;
    seen.add(key);
    out.push({ id: clean, label: genreLabel(clean) });
  }
  return out;
}

export function SeriesDetailBanner({
  series,
  card,
  seasonCount,
  episodeCount,
  nextEpisodeAction,
}: SeriesDetailBannerProps) {
  const artwork = pickSeriesArtwork(card || {}, 'backdrop');
  const [stage, setStage] = useState<0 | 1 | 2>(0);

  useEffect(() => {
    setStage(0);
  }, [artwork.primary, artwork.fallback]);

  const activeBackdrop =
    stage === 0 ? artwork.primary : stage === 1 ? artwork.fallback : null;

  const hasBackdrop = Boolean(activeBackdrop);
  const years = formatYearRange(
    card?.releaseYearStart ?? series.releaseYearStart,
    card?.releaseYearEnd ?? series.releaseYearEnd
  );
  const counts = formatSeriesCounts(
    card?.seasonCount ?? (seasonCount > 0 ? seasonCount : null),
    card?.episodeCount ?? (episodeCount > 0 ? episodeCount : null)
  );
  const genres = card && card.genres.length > 0 ? card.genres : resolveFallbackGenres(series);
  const synopsis = series.synopsis || card?.synopsis;
  const originalTitle = series.originalTitle || card?.originalTitle;

  return (
    <header
      data-testid="series-detail-banner"
      className={`series-detail-banner mb-6 ${hasBackdrop ? 'series-detail-banner--art' : 'series-detail-banner--plain'}`}
    >
      {activeBackdrop && (
        <>
          <img
            src={activeBackdrop}
            alt=""
            aria-hidden="true"
            loading="lazy"
            decoding="async"
            referrerPolicy="no-referrer"
            onError={() => {
              setStage((prev) => (prev === 0 && artwork.fallback ? 1 : 2));
            }}
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
          <Link to="/series" className={hasBackdrop ? 'hover:text-white underline-offset-4 hover:underline' : 'hover:text-ink underline-offset-4 hover:underline'}>
            Bộ phim
          </Link>
          <span aria-hidden="true">/</span>
          <span aria-current="page" className="truncate">{series.title}</span>
        </nav>

        <div className="mt-3 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0">
            <h1
              className={`break-words text-2xl font-bold sm:text-4xl ${
                hasBackdrop ? 'text-[#fffdf6]' : 'text-ink'
              }`}
            >
              {series.title}
            </h1>

            {(years || counts) && (
              <div
                className={`mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm font-medium ${
                  hasBackdrop ? 'text-[#e6eedf]' : 'text-muted'
                }`}
              >
                {years && <span data-testid="series-banner-years">{years}</span>}
                {years && counts && <span aria-hidden="true">·</span>}
                {counts && <span data-testid="series-banner-counts">{counts}</span>}
              </div>
            )}

            {genres.length > 0 && (
              <div className="mt-3 flex flex-wrap gap-1.5" data-testid="series-banner-genres">
                {genres.map((genre) => (
                  <span
                    key={genre.id}
                    className={
                      hasBackdrop
                        ? 'inline-flex items-center rounded border border-[#c9ddbd]/40 bg-[#18241e]/70 px-2.5 py-0.5 text-xs font-semibold text-[#f7f3e9]'
                        : 'series-genre-pill'
                    }
                  >
                    {genre.label}
                  </span>
                ))}
              </div>
            )}
          </div>

          <div className="shrink-0">{nextEpisodeAction}</div>
        </div>

        {(synopsis || originalTitle || series.studios) && (
          <details
            className={`mt-4 max-w-3xl text-sm ${
              hasBackdrop ? 'text-[#e6eedf]/90' : 'text-muted'
            }`}
          >
            <summary
              className={`w-fit cursor-pointer font-semibold ${
                hasBackdrop ? 'text-[#fffdf6]' : 'text-ink'
              }`}
            >
              Giới thiệu bộ phim
            </summary>
            {synopsis && <p className="mt-2 leading-relaxed">{synopsis}</p>}
            {originalTitle && <p className="mt-2">Tên gốc: {originalTitle}</p>}
            {series.studios && <p className="mt-1">Hãng phim: {series.studios}</p>}
          </details>
        )}
      </div>
    </header>
  );
}
