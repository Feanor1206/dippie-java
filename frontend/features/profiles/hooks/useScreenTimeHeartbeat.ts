import { useEffect, useRef } from 'react';
import { HEARTBEAT_INTERVAL_MS } from '@/lib/services/screenTimeService';

const MIN_FLUSH_SECONDS = 10;

export function useScreenTimeHeartbeat({
  enabled,
  isPlaying,
  onHeartbeat,
  intervalMs = HEARTBEAT_INTERVAL_MS,
}: {
  enabled: boolean;
  isPlaying: boolean;
  onHeartbeat: (seconds: number) => void | Promise<unknown>;
  intervalMs?: number;
}): void {
  const callbackRef = useRef(onHeartbeat);
  callbackRef.current = onHeartbeat;
  const lastSentAtRef = useRef<number | null>(null);

  useEffect(() => {
    if (!enabled || !isPlaying) {
      if (lastSentAtRef.current !== null && enabled) {
        const elapsedSec = Math.round((Date.now() - lastSentAtRef.current) / 1000);
        lastSentAtRef.current = null;
        if (elapsedSec >= MIN_FLUSH_SECONDS) {
          void callbackRef.current(elapsedSec);
        }
      } else {
        lastSentAtRef.current = null;
      }
      return;
    }

    lastSentAtRef.current = Date.now();
    const timer = setInterval(() => {
      if (typeof document !== 'undefined' && document.visibilityState === 'hidden') {
        lastSentAtRef.current = Date.now();
        return;
      }
      const now = Date.now();
      const base = lastSentAtRef.current ?? now;
      const elapsedSec = Math.max(1, Math.round((now - base) / 1000));
      lastSentAtRef.current = now;
      void callbackRef.current(elapsedSec);
    }, intervalMs);

    return () => {
      clearInterval(timer);
      if (lastSentAtRef.current !== null) {
        const elapsedSec = Math.round((Date.now() - lastSentAtRef.current) / 1000);
        lastSentAtRef.current = null;
        if (elapsedSec >= MIN_FLUSH_SECONDS) {
          void callbackRef.current(elapsedSec);
        }
      }
    };
  }, [enabled, isPlaying, intervalMs]);
}
