import React from 'react';
import { Link } from 'react-router';
import { Play } from 'lucide-react';
import { normalizeGenreLabels, resolveRatingSummary } from '@/lib/labels';
import type { SeriesCard as SeriesCardData } from '@/lib/services/seriesCardService';
import { ArtworkImage } from '@/features/series/components/ArtworkImage';
import { QuickSaveButtons } from '@/features/library/components/LibraryControls';
import { VipBadge } from '@/features/profiles/components/VipBadge';
import { formatSeriesCounts, formatYearRange, pickSeriesArtwork } from '@/features/series/seriesBrowseUtils';

const MAX_CARD_GENRES = 3;

export type SeriesCardProps = {
  card: SeriesCardData;
  headingLevel?: 'h2' | 'h3';
  className?: string;
  isVip?: boolean;
};

export function SeriesCard({ card, headingLevel = 'h3', className = '', isVip = false }: SeriesCardProps) {
  const Heading = headingLevel;
  const artwork = pickSeriesArtwork(card, 'backdrop');
  const years = formatYearRange(card.releaseYearStart, card.releaseYearEnd);
  const counts = formatSeriesCounts(card.seasonCount, card.episodeCount);
  const visibleGenres = normalizeGenreLabels(card.genres || []).slice(0, MAX_CARD_GENRES);
  const excerpt = card.synopsis || card.originalTitle || '';
  const ratingSummary = resolveRatingSummary(card);

  return (
    <article
      data-testid="series-card"
      data-series-id={card.id}
      className={`series-card group relative overflow-hidden ${className}`}
    >
      {isVip && (
        <div className="absolute top-2 left-2 z-20">
          <VipBadge data-testid={`series-card-vip-${card.id}`} />
        </div>
      )}
      <div className="absolute top-2 right-2 z-20">
        <QuickSaveButtons
          itemId={card.id}
          title={card.title}
          poster={artwork.primary || ''}
          variant="overlay"
          testIdPrefix={`series-card-${card.id}`}
        />
      </div>

      <Link to={`/series/${encodeURIComponent(card.id)}`} className="flex flex-1 flex-col focus-visible:outline-none">
        <ArtworkImage
          src={artwork.primary}
          fallbackSrc={artwork.fallback}
          alt={card.title}
          title={card.title}
          seed={card.id}
          imgClassName="transition-transform duration-300 group-hover:scale-105 group-focus-visible:scale-105"
        />

        <div className="series-card-body">
          <Heading className="line-clamp-2 font-display text-lg font-bold leading-snug text-ink">
            {card.title}
          </Heading>

          {(years || counts) && (
            <div className="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-xs font-medium text-muted">
              {years && <span data-testid="series-card-years">{years}</span>}
              {years && counts && <span aria-hidden="true">·</span>}
              {counts && <span data-testid="series-card-counts">{counts}</span>}
            </div>
          )}

          <p className="mt-1 text-xs font-medium text-muted" data-testid="series-card-rating">
            {ratingSummary.hasRating ? `★ ${ratingSummary.label}` : ratingSummary.label}
          </p>

          {excerpt && (
            <p className="mt-2 line-clamp-2 text-xs leading-relaxed text-muted" data-testid="series-card-synopsis">
              {excerpt}
            </p>
          )}

          {visibleGenres.length > 0 && (
            <div className="mt-2.5 flex flex-wrap gap-1.5" data-testid="series-card-genres">
              {visibleGenres.map((label) => (
                <span key={label} className="series-genre-pill">
                  {label}
                </span>
              ))}
            </div>
          )}

          <div className="mt-auto pt-3">
            <span className="series-card-cta">
              <Play size={12} className="fill-current" aria-hidden="true" />
              <span>Xem</span>
            </span>
          </div>
        </div>
      </Link>
    </article>
  );
}
