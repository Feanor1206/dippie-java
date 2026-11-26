import React, { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { useOptionalAuth } from '@/lib/context/AuthContext';
import { notifyScreenTimeHeartbeat } from '@/lib/services/playFeaturesService';
import {
  SCREEN_TIME_LOCKED_EVENT,
  SCREEN_TIME_UPDATED_EVENT,
  SCREEN_TIME_UPDATED_STORAGE_KEY,
  fetchScreenTimeStatus,
  sendScreenTimeHeartbeat,
  type ScreenTimeHeartbeatResult,
  type ScreenTimeStatus,
} from '@/lib/services/screenTimeService';

export type ScreenTimeContextValue = {
  screenTime: ScreenTimeStatus | null;
  loading: boolean;
  isLimited: boolean;
  isUnlimited: boolean;
  isLocked: boolean;
  remainingSeconds: number | null;
  refreshScreenTime: () => Promise<ScreenTimeStatus | null>;
  reportWatchHeartbeat: (seconds: number) => Promise<ScreenTimeHeartbeatResult | null>;
  applyScreenTimeStatus: (next: ScreenTimeStatus) => void;
  markLockedFromServer: () => void;
};

const ScreenTimeContext =
  typeof createContext === 'function' ? createContext<ScreenTimeContextValue | undefined>(undefined) : null;

function fallbackLockedStatus(previous: ScreenTimeStatus | null, profileId: number | null): ScreenTimeStatus {
  if (previous) {
    return {
      ...previous,
      limited: true,
      unlimited: false,
      remainingSeconds: 0,
      locked: true,
    };
  }
  return {
    limited: true,
    unlimited: false,
    profileId,
    day: '',
    dailyMinutes: 60,
    mathLevel: 1,
    rewardMinutes: 5,
    maxBonusMinutes: 30,
    dailySeconds: 3600,
    watchedSeconds: 3600,
    rewardSeconds: 0,
    grantSeconds: 0,
    bountySeconds: 0,
    remainingSeconds: 0,
    locked: true,
    bonusCapSeconds: 1800,
    bonusRemainingSeconds: 1800,
    bonusCapReached: false,
  };
}

export function ScreenTimeProvider({ children }: { children: ReactNode }) {
  const auth = useOptionalAuth();
  const isAuthenticated = Boolean(auth?.isAuthenticated);
  const isKidMode = Boolean(auth?.isKidMode);
  const token = auth?.token ?? null;
  const activeProfileId = auth?.activeProfile?.id ?? null;

  const [screenTime, setScreenTime] = useState<ScreenTimeStatus | null>(null);
  const [loading, setLoading] = useState(false);
  const scopeKey = isAuthenticated && isKidMode && token ? `${token}:${activeProfileId ?? 'kid'}` : '';
  const scopeKeyRef = useRef(scopeKey);
  scopeKeyRef.current = scopeKey;
  const tokenRef = useRef(token);
  tokenRef.current = token;
  const activeProfileIdRef = useRef(activeProfileId);
  activeProfileIdRef.current = activeProfileId;

  const refreshScreenTime = useCallback(async (): Promise<ScreenTimeStatus | null> => {
    const currentScope = scopeKeyRef.current;
    const currentToken = tokenRef.current;
    if (!currentScope || !currentToken) {
      setScreenTime(null);
      return null;
    }
    setLoading(true);
    try {
      const next = await fetchScreenTimeStatus(currentToken);
      if (scopeKeyRef.current !== currentScope) return null;
      setScreenTime(next);
      return next;
    } catch {
      return null;
    } finally {
      if (scopeKeyRef.current === currentScope) {
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    if (!scopeKey) {
      setScreenTime(null);
      setLoading(false);
      return;
    }
    void refreshScreenTime();
  }, [scopeKey, refreshScreenTime]);

  const markLockedFromServer = useCallback(() => {
    if (!scopeKeyRef.current) return;
    setScreenTime((prev) => fallbackLockedStatus(prev, activeProfileIdRef.current));
    void refreshScreenTime();
  }, [refreshScreenTime]);

  useEffect(() => {
    if (!scopeKey || typeof window === 'undefined' || typeof window.addEventListener !== 'function') {
      return;
    }
    const handleLocked = () => {
      markLockedFromServer();
    };
    const handleRefresh = () => {
      void refreshScreenTime();
    };
    const handleStorage = (event: StorageEvent) => {
      if (event.key === SCREEN_TIME_UPDATED_STORAGE_KEY) {
        void refreshScreenTime();
      }
    };
    const handleVisibility = () => {
      if (typeof document !== 'undefined' && document.visibilityState === 'visible') {
        void refreshScreenTime();
      }
    };
    window.addEventListener(SCREEN_TIME_LOCKED_EVENT, handleLocked);
    window.addEventListener(SCREEN_TIME_UPDATED_EVENT, handleRefresh);
    window.addEventListener('focus', handleRefresh);
    window.addEventListener('storage', handleStorage);
    if (typeof document !== 'undefined' && typeof document.addEventListener === 'function') {
      document.addEventListener('visibilitychange', handleVisibility);
    }
    return () => {
      window.removeEventListener(SCREEN_TIME_LOCKED_EVENT, handleLocked);
      window.removeEventListener(SCREEN_TIME_UPDATED_EVENT, handleRefresh);
      window.removeEventListener('focus', handleRefresh);
      window.removeEventListener('storage', handleStorage);
      if (typeof document !== 'undefined' && typeof document.removeEventListener === 'function') {
        document.removeEventListener('visibilitychange', handleVisibility);
      }
    };
  }, [scopeKey, markLockedFromServer, refreshScreenTime]);

  const reportWatchHeartbeat = useCallback(
    async (seconds: number): Promise<ScreenTimeHeartbeatResult | null> => {
      const currentScope = scopeKeyRef.current;
      const currentToken = tokenRef.current;
      if (!currentScope || !currentToken || seconds <= 0) {
        return null;
      }
      try {
        const result = await sendScreenTimeHeartbeat(currentToken, seconds);
        if (scopeKeyRef.current !== currentScope) return null;
        setScreenTime(result.status);
        notifyScreenTimeHeartbeat();
        return result;
      } catch {
        return null;
      }
    },
    []
  );

  const applyScreenTimeStatus = useCallback((next: ScreenTimeStatus) => {
    if (!scopeKeyRef.current) return;
    setScreenTime(next);
  }, []);

  const isUnlimited = Boolean(isKidMode && screenTime?.unlimited);
  const isLimited = Boolean(isKidMode && screenTime?.limited && !screenTime?.unlimited);
  const isLocked = Boolean(isLimited && screenTime?.locked);
  const remainingSeconds = isLimited ? screenTime?.remainingSeconds ?? null : null;

  if (!ScreenTimeContext) {
    return <>{children}</>;
  }

  return (
    <ScreenTimeContext.Provider
      value={{
        screenTime: isLimited ? screenTime : null,
        loading,
        isLimited,
        isUnlimited,
        isLocked,
        remainingSeconds,
        refreshScreenTime,
        reportWatchHeartbeat,
        applyScreenTimeStatus,
        markLockedFromServer,
      }}
    >
      {children}
    </ScreenTimeContext.Provider>
  );
}

export function useOptionalScreenTime(): ScreenTimeContextValue | null {
  if (!ScreenTimeContext || typeof useContext !== 'function') {
    return null;
  }
  return useContext(ScreenTimeContext) ?? null;
}

export function useScreenTime(): ScreenTimeContextValue {
  const ctx = ScreenTimeContext && typeof useContext === 'function' ? useContext(ScreenTimeContext) : undefined;
  if (!ctx) {
    throw new Error('useScreenTime must be used within a ScreenTimeProvider');
  }
  return ctx;
}
