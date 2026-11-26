import React, { useEffect, useState } from 'react';
import { useOptionalAuth } from '@/lib/context/AuthContext';
import { fetchWeekendVaultStatus, type WeekendVaultStatus } from '@/lib/services/weekendVaultService';
import { WeekendVaultBanner } from '@/features/profiles/components/WeekendVaultBanner';
import { KidBountyBoard } from '@/features/profiles/components/KidBountyBoard';

export function KidModeDashboard() {
  const auth = typeof useOptionalAuth === 'function' ? useOptionalAuth() : null;
  const isKidMode = Boolean(auth?.isAuthenticated && auth?.isKidMode && auth?.token);
  const token = auth?.token ?? '';
  const profileId = auth?.activeProfile?.id ?? null;

  const [vaultStatus, setVaultStatus] = useState<WeekendVaultStatus | null>(null);

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

  return (
    <section
      id="goc-cua-be"
      aria-label="Góc nhiệm vụ và vé xem phim của bé"
      data-testid="kid-mode-dashboard"
      className="my-6 space-y-4"
    >
      {vaultStatus && vaultStatus.enabled && (
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
