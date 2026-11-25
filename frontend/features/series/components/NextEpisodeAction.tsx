import React, { useCallback, useEffect, useRef, useState } from 'react';
import { CheckCircle2, Play } from 'lucide-react';
import { useAuth } from '@/lib/context/AuthContext';
import {
  formatNextEpisodeLabel,
  getNextEpisode,
  NextEpisodeApiError,
  type NextEpisodeRecommendation,
  type NextEpisodeResult,
} from '@/lib/services/nextEpisodeService';

export type NextEpisodeActionProps = {
  seriesId: string;
  progressRevision: string;
  onSelectRecommendation: (recommendation: NextEpisodeRecommendation) => void;
};

export function NextEpisodeAction({
  seriesId,
  progressRevision,
  onSelectRecommendation,
}: NextEpisodeActionProps) {
  const { isAuthenticated, token, handleSessionExpired } = useAuth();
  const [result, setResult] = useState<NextEpisodeResult | null>(null);
  const requestGenRef = useRef(0);
  const activeControllerRef = useRef<AbortController | null>(null);

  const loadNextEpisode = useCallback(() => {
    if (!isAuthenticated || !token || !seriesId) {
      activeControllerRef.current?.abort();
      activeControllerRef.current = null;
      setResult(null);
      return;
    }

    activeControllerRef.current?.abort();
    const controller = new AbortController();
    activeControllerRef.current = controller;
    const generation = ++requestGenRef.current;

    getNextEpisode(token, seriesId, { signal: controller.signal })
      .then((next) => {
        if (controller.signal.aborted || generation !== requestGenRef.current) return;
        setResult(next);
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted || generation !== requestGenRef.current) return;
        if (err instanceof NextEpisodeApiError && err.isCanceled) return;
        if (err instanceof NextEpisodeApiError && err.status === 401) {
          setResult(null);
          handleSessionExpired();
        }
      })
      .finally(() => {
        if (activeControllerRef.current === controller) {
          activeControllerRef.current = null;
        }
      });
  }, [isAuthenticated, token, seriesId, handleSessionExpired]);

  useEffect(() => {
    loadNextEpisode();
    return () => {
      requestGenRef.current += 1;
      activeControllerRef.current?.abort();
      activeControllerRef.current = null;
    };
  }, [loadNextEpisode, progressRevision]);

  useEffect(() => {
    if (!isAuthenticated || !token || !seriesId || typeof window === 'undefined') {
      return;
    }
    const handleFocus = () => {
      loadNextEpisode();
    };
    const handleVisibility = () => {
      if (typeof document !== 'undefined' && !document.hidden) {
        loadNextEpisode();
      }
    };
    window.addEventListener('focus', handleFocus);
    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', handleVisibility);
    }
    return () => {
      window.removeEventListener('focus', handleFocus);
      if (typeof document !== 'undefined') {
        document.removeEventListener('visibilitychange', handleVisibility);
      }
    };
  }, [isAuthenticated, token, seriesId, loadNextEpisode]);

  if (!isAuthenticated || !token || !result) {
    return null;
  }

  if (result.status === 'completed') {
    return (
      <span
        data-testid="series-completed-badge"
        role="status"
        aria-live="polite"
        className="inline-flex items-center gap-1.5 rounded-md border border-surface-border/40 bg-surface-muted px-3 py-1.5 text-xs font-semibold text-muted"
      >
        <CheckCircle2 size={14} className="shrink-0 text-brand-dark" aria-hidden="true" />
        <span>Đã xem hết</span>
      </span>
    );
  }

  const { label, resumeTime } = formatNextEpisodeLabel(result.recommendation);

  return (
    <button
      type="button"
      data-testid="next-episode-action"
      onClick={() => onSelectRecommendation(result.recommendation)}
      className="btn-primary text-xs sm:text-sm"
    >
      <Play size={15} className="shrink-0 fill-current" aria-hidden="true" />
      <span>{label}</span>
      {resumeTime && (
        <span className="resume-mark font-mono text-xs">{resumeTime}</span>
      )}
    </button>
  );
}
