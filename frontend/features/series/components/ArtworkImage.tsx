import { safeImageUrl } from '@/lib/safeUrl';
import React, { useEffect, useState } from 'react';
import { getFallbackToneIndex, getTitleInitials } from '@/features/series/seriesBrowseUtils';

const FALLBACK_TONES = [
  'bg-[#dce8d4] text-[#203328]',
  'bg-[#e9e1c7] text-[#2b3020]',
  'bg-[#d5e4df] text-[#1c322e]',
  'bg-[#f0dfba] text-[#352b18]',
  'bg-[#d9e2ec] text-[#1e2d3b]',
  'bg-[#e2ead8] text-[#243826]',
];

export type ArtworkImageProps = {
  src?: string | null;
  fallbackSrc?: string | null;
  alt: string;
  title: string;
  seed?: string;
  className?: string;
  imgClassName?: string;
  aspectClassName?: string;
  children?: React.ReactNode;
};

export function ArtworkImage({
  src,
  fallbackSrc,
  alt,
  title,
  seed,
  className = '',
  imgClassName = '',
  aspectClassName = 'aspect-video',
  children,
}: ArtworkImageProps) {
  const cleanSrc = safeImageUrl(src);
  const fallback = safeImageUrl(fallbackSrc);
  const cleanFallback = fallback !== cleanSrc ? fallback : undefined;

  const [stage, setStage] = useState<0 | 1 | 2>(0);

  useEffect(() => {
    setStage(0);
  }, [cleanSrc, cleanFallback]);

  const activeSrc =
    stage === 0 ? cleanSrc || cleanFallback : stage === 1 && cleanSrc ? cleanFallback : null;

  const handleError = () => {
    setStage((current) => {
      if (current === 0 && cleanSrc && cleanFallback) return 1;
      return 2;
    });
  };

  const initials = getTitleInitials(title);
  const toneClass = FALLBACK_TONES[getFallbackToneIndex(seed || title, FALLBACK_TONES.length)];

  return (
    <div className={`relative w-full overflow-hidden bg-surface-elevated ${aspectClassName} ${className}`}>
      {activeSrc ? (
        <img
          src={activeSrc}
          alt={alt}
          loading="lazy"
          decoding="async"
          referrerPolicy="no-referrer"
          onError={handleError}
          className={`h-full w-full object-cover ${imgClassName}`}
        />
      ) : (
        <div
          data-testid="artwork-fallback"
          role={alt ? 'img' : undefined}
          aria-label={alt || undefined}
          aria-hidden={alt ? undefined : true}
          className={`comic-halftone flex h-full w-full flex-col items-center justify-center gap-1.5 px-4 text-center select-none ${toneClass}`}
        >
          <span
            className="font-display text-3xl font-bold tracking-tight sm:text-4xl"
            data-testid="artwork-fallback-initials"
          >
            {initials}
          </span>
          <span className="line-clamp-1 max-w-[90%] text-[0.6875rem] font-semibold opacity-75">
            {title}
          </span>
        </div>
      )}
      {children}
    </div>
  );
}
