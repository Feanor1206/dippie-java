import React, { useEffect, useState } from 'react';
import { useOptionalAuth } from '@/lib/context/AuthContext';
import { browseCatalog } from '@/lib/services/catalogService';
import { fetchWeekendVaultStatus, type WeekendVaultStatus } from '@/lib/services/weekendVaultService';
import { WeekendVaultBanner } from '@/features/profiles/components/WeekendVaultBanner';
import { KidBountyBoard } from '@/features/profiles/components/KidBountyBoard';
import type { Movie } from '@/types/movie';

export function isStandaloneCatalogFilm(item: Movie | null | undefined): boolean {
  if (!item || typeof item !== 'object') return false;
  const kind = (item.item_type || item.type || '').toLowerCase();
  if (kind === 'series' || kind === 'episode' || kind === 'segment' || kind === 'scene') {
    return false;
  }
  if (item.series_id || item.seriesId || item.episode_id || item.episodeId) {
    return false;
  }
  const id = (item.id || '').trim();
  if (!id || id.startsWith('ser_') || id.startsWith('ep_')) {
    return false;
  }
  return true;
}

export function KidModeDashboard({
  standaloneFilmCount,
}: {
  standaloneFilmCount?: number;
} = {}) {
  const auth = typeof useOptionalAuth === 'function' ? useOptionalAuth() : null;
  const isKidMode = Boolean(auth?.isAuthenticated && auth?.isKidMode && auth?.token);
  const token = auth?.token ?? '';
  const profileId = auth?.activeProfile?.id ?? null;

  const [vaultStatus, setVaultStatus] = useState<WeekendVaultStatus | null>(null);
  const [detectedStandaloneCount, setDetectedStandaloneCount] = useState<number>(0);

  useEffect(() => {
    if (!isKidMode || !token) {
      setVaultStatus(null);
      return;
    }
    const controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
    fetchWeekendVaultStatus(token, { signal: controller?.signal })
      .then((status) => {
        if (!controller?.signal.aborted) {
          setVaultStatus(status);
        }
      })
      .catch(() => {
        if (!controller?.signal.aborted) {
          setVaultStatus(null);
        }
      });
    return () => controller?.abort();
  }, [isKidMode, token, profileId]);

  useEffect(() => {
    if (!isKidMode || standaloneFilmCount !== undefined || typeof browseCatalog !== 'function') {
      return;
    }
    const controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
    browseCatalog({ page: 0, size: 48, signal: controller?.signal })
      .then((page) => {
        if (controller?.signal.aborted) return;
        const count = Array.isArray(page?.items)
          ? page.items.filter(isStandaloneCatalogFilm).length
          : 0;
        setDetectedStandaloneCount(count);
      })
      .catch(() => {
        if (!controller?.signal.aborted) {
          setDetectedStandaloneCount(0);
        }
      });
    return () => controller?.abort();
  }, [isKidMode, standaloneFilmCount]);

  if (!isKidMode || !token) {
    return null;
  }

  const effectiveStandaloneCount =
    typeof standaloneFilmCount === 'number' ? standaloneFilmCount : detectedStandaloneCount;
  const showWeekendVault = Boolean(
    vaultStatus && vaultStatus.enabled && effectiveStandaloneCount > 0
  );

  return (
    <section
      id="goc-cua-be"
      aria-label="Góc nhiệm vụ và vé xem phim của bé"
      data-testid="kid-mode-dashboard"
      data-reveal-group=""
      className="my-6 space-y-4"
    >
      {showWeekendVault && vaultStatus && (
        <WeekendVaultBanner
          status={vaultStatus}
          token={token}
          onStatusUpdated={(next) => setVaultStatus(next)}
        />
      )}
      <KidBountyBoard token={token} />
    </section>
  );
}
