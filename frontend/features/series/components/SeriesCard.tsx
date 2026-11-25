import React from 'react';
import { Link } from 'react-router';
import { Play } from 'lucide-react';
import type { SeriesCard as SeriesCardData } from '@/lib/services/seriesCardService';
import { ArtworkImage } from '@/features/series/components/ArtworkImage';
import {
  formatSeriesCounts,
  formatYearRange,
  pickSeriesArtwork,
} from '@/features/series/seriesBrowseUtils';

export type SeriesCardProps = {
  card: SeriesCardData;
  headingLevel?: 'h2' | 'h3';
  className?: string;
};

export function SeriesCard({ card, headingLevel = 'h3', className = '' }: SeriesCardProps) {
  const Heading = headingLevel;
  const artwork = pickSeriesArtwork(card, 'backdrop');
  const years = formatYearRange(card.releaseYearStart, card.releaseYearEnd);
  const counts = formatSeriesCounts(card.seasonCount, card.episodeCount);
  const visibleGenres = (card.genres || []).slice(0, 3);
  const excerpt = card.synopsis || card.originalTitle || '';

  return (
    <Link
      to={`/series/${encodeURIComponent(card.id)}`}
      data-testid="series-card"
      data-series-id={card.id}
      className={`series-card group block overflow-hidden focus-visible:outline-none ${className}`}
    >
      <ArtworkImage
        src={artwork.primary}
        fallbackSrc={artwork.fallback}
        alt={card.title}
        title={card.title}
        seed={card.id}
        imgClassName="transition-transform duration-300 group-hover:scale-105 group-focus-visible:scale-105"
      >
        <div className="series-card-overlay">
          {excerpt && (
            <p className="line-clamp-2 text-xs leading-relaxed text-[#f7f3e9]/95" data-testid="series-card-synopsis">
              {excerpt}
            </p>
          )}
          <span className="series-card-cta">
            <Play size={12} className="fill-current" aria-hidden="true" />
            <span>Xem</span>
          </span>
        </div>
      </ArtworkImage>

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

        {visibleGenres.length > 0 && (
          <div className="mt-2.5 flex flex-wrap gap-1.5" data-testid="series-card-genres">
            {visibleGenres.map((genre) => (
              <span key={genre.id} className="series-genre-pill">
                {genre.label}
              </span>
            ))}
          </div>
        )}
      </div>
    </Link>
  );
}
