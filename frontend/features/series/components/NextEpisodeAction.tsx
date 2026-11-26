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
  const { isAuthenticated, token } = useAuth();
  const [result, setResult] = useState<NextEpisodeResult | null>(null);
  const requestIdRef = useRef(0);
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
    const requestId = ++requestIdRef.current;

    getNextEpisode(token, seriesId, { signal: controller.signal })
      .then((next) => {
        if (controller.signal.aborted || requestId !== requestIdRef.current) return;
        setResult(next);
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted || requestId !== requestIdRef.current) return;
        if (err instanceof NextEpisodeApiError && err.isCanceled) return;
        if (err instanceof NextEpisodeApiError && err.status === 401) {
          setResult(null);
        }
      })
      .finally(() => {
        if (activeControllerRef.current === controller) {
          activeControllerRef.current = null;
        }
      });
  }, [isAuthenticated, token, seriesId]);

  useEffect(() => {
    loadNextEpisode();
    return () => {
      requestIdRef.current += 1;
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
        className="inline-flex min-h-[44px] items-center gap-2 rounded-lg border-2 border-ink bg-mint px-3.5 py-2 text-xs font-bold text-ink shadow-[2px_2px_0_#203328]"
      >
        <CheckCircle2 size={15} className="shrink-0 text-brand-dark" aria-hidden="true" />
        <span>Đã xem hết</span>
      </span>
    );
  }

  const { label, resumeTime } = formatNextEpisodeLabel(result.recommendation);
  const rawPreview = (result.recommendation.episodeTitle || '')
    .replace(/\s*\[[A-Za-z0-9_-]{6,}\](?=\.\w+$|$)/g, '')
    .replace(/\.(mp4|mkv|webm|mov|avi)$/i, '')
    .replaceAll('⧸', '/')
    .trim();
  const normalizedPreview = rawPreview.toLowerCase();
  const showEpisodePreview =
    Boolean(rawPreview) &&
    normalizedPreview !== label.toLowerCase() &&
    !(
      result.recommendation.episodeNumber !== null &&
      result.recommendation.episodeNumber !== undefined &&
      normalizedPreview === `tập ${result.recommendation.episodeNumber}`
    );

  return (
    <button
      type="button"
      data-testid="next-episode-action"
      onClick={() => onSelectRecommendation(result.recommendation)}
      className={`inline-flex min-h-[44px] max-w-full items-center justify-center gap-2.5 rounded-lg border-2 border-ink px-4 py-2.5 text-left text-xs font-bold text-ink shadow-[3px_3px_0_#203328] transition-transform duration-150 hover:-translate-y-0.5 active:translate-x-0.5 active:translate-y-0.5 cursor-pointer sm:text-sm ${
        resumeTime ? 'bg-[#f2d18c] hover:bg-[#f7dfa8]' : 'bg-sage hover:bg-brand-hover'
      }`}
    >
      <Play size={16} className="shrink-0 fill-current" aria-hidden="true" />
      <span className="flex min-w-0 flex-col">
        <span className="flex flex-wrap items-center gap-2">
          <span>{label}</span>
          {resumeTime && (
            <span className="resume-mark font-mono text-xs">{resumeTime}</span>
          )}
        </span>
        {showEpisodePreview && (
          <span className="max-w-[240px] truncate text-[0.6875rem] font-medium text-ink/80">
            {rawPreview}
          </span>
        )}
      </span>
    </button>
  );
}
