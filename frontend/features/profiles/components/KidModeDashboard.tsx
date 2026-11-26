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

let cachedStandaloneFilmCount: number | null = null;
let pendingStandaloneFilmRequest: Promise<number | null> | null = null;

export function resetStandaloneFilmCountCache(): void {
  cachedStandaloneFilmCount = null;
  pendingStandaloneFilmRequest = null;
}

export function useDetectedStandaloneFilmCount(
  enabled = true,
  options: { allowWithoutLocation?: boolean } = {}
): number | null {
  const [detected, setDetected] = useState<number | null>(() => cachedStandaloneFilmCount);
  const allowWithoutLocation = Boolean(options.allowWithoutLocation);

  useEffect(() => {
    if (!enabled) return;
    if (cachedStandaloneFilmCount !== null) {
      setDetected(cachedStandaloneFilmCount);
      return;
    }
    if (!allowWithoutLocation && (typeof window === 'undefined' || !window.location?.href)) {
      return;
    }
    if (typeof browseCatalog !== 'function') return;
    if (!pendingStandaloneFilmRequest) {
      pendingStandaloneFilmRequest = browseCatalog({ page: 0, size: 48 })
        .then((page) => {
          if (!page || !Array.isArray(page.items)) return null;
          const total = typeof page.totalElements === 'number' ? page.totalElements : page.items.length;
          if (total <= 0 && page.items.length === 0) return null;
          const count = page.items.filter(isStandaloneCatalogFilm).length;
          cachedStandaloneFilmCount = count;
          return count;
        })
        .catch(() => null)
        .finally(() => {
          pendingStandaloneFilmRequest = null;
        });
    }
    let active = true;
    void pendingStandaloneFilmRequest.then((count) => {
      if (active && count !== null) {
        setDetected(count);
      }
    });
    return () => {
      active = false;
    };
  }, [enabled]);

  return detected;
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
  const detectedStandalone = useDetectedStandaloneFilmCount(isKidMode && standaloneFilmCount === undefined, {
    allowWithoutLocation: true,
  });

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

  if (!isKidMode || !token) {
    return null;
  }

  const effectiveStandaloneCount =
    typeof standaloneFilmCount === 'number' ? standaloneFilmCount : (detectedStandalone ?? 0);
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
