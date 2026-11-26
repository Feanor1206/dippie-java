import React from 'react';
import { Clock } from 'lucide-react';
import { formatRemainingTime } from '@/lib/services/screenTimeService';

export function resolveScreenTimeThresholdAnnouncement(
  remainingSeconds: number | null,
  locked: boolean
): string | null {
  if (locked || (typeof remainingSeconds === 'number' && remainingSeconds <= 0)) {
    return 'Đã hết giờ xem hôm nay rồi.';
  }
  if (typeof remainingSeconds !== 'number' || !Number.isFinite(remainingSeconds)) {
    return null;
  }
  const minutesLeft = Math.ceil(Math.max(0, remainingSeconds) / 60);
  if (minutesLeft === 10 || minutesLeft === 5 || minutesLeft === 1) {
    return `Thời gian xem hôm nay còn ${minutesLeft} phút.`;
  }
  return null;
}

export function ScreenTimeChip({
  remainingSeconds,
  locked,
  totalSeconds = null,
  variant = 'bar',
  'data-testid': testId = 'screen-time-chip',
}: {
  remainingSeconds: number | null;
  locked: boolean;
  totalSeconds?: number | null;
  variant?: 'bar' | 'player';
  'data-testid'?: string;
}) {
  if (!locked && remainingSeconds === null) {
    return null;
  }

  const label = formatRemainingTime(remainingSeconds, locked);
  if (!label) return null;

  const isLow = !locked && remainingSeconds !== null && remainingSeconds > 0 && remainingSeconds <= 300;
  const thresholdAnnouncement = resolveScreenTimeThresholdAnnouncement(remainingSeconds, locked);

  const safeTotal =
    typeof totalSeconds === 'number' && Number.isFinite(totalSeconds) && totalSeconds > 0
      ? totalSeconds
      : null;
  const progressPercent =
    locked || remainingSeconds === null
      ? 0
      : safeTotal !== null
      ? Math.max(0, Math.min(100, Math.round((remainingSeconds / safeTotal) * 100)))
      : null;

  if (variant === 'player') {
    return (
      <div
        data-testid={testId}
        className={`inline-flex min-h-[36px] flex-wrap items-center gap-2.5 rounded-xl border-2 px-3 py-1.5 text-xs font-bold ${
          locked
            ? 'border-red-800/60 bg-red-50 text-red-900'
            : isLow
            ? 'border-amber-700/70 bg-[#f2d18c] text-ink'
            : 'border-ink bg-paper-white text-ink shadow-[2px_2px_0_#203328]'
        }`}
      >
        <span className="inline-flex items-center gap-1.5">
          <Clock size={14} aria-hidden="true" className="shrink-0" />
          <span>Giờ xem hôm nay: {label}</span>
        </span>
        {progressPercent !== null && (
          <span
            role="progressbar"
            aria-label="Phần thời gian xem còn lại hôm nay"
            aria-valuenow={progressPercent}
            aria-valuemin={0}
            aria-valuemax={100}
            data-testid="player-screen-time-progress"
            className="h-2 w-20 overflow-hidden rounded-full border border-ink/40 bg-surface-elevated sm:w-28"
          >
            <span
              className={`block h-full transition-all duration-300 ${isLow ? 'bg-amber-700' : 'bg-sage'}`}
              style={{ width: `${progressPercent}%` }}
            />
          </span>
        )}
        {thresholdAnnouncement && (
          <span role="status" aria-live="polite" data-testid="screen-time-threshold-live" className="sr-only">
            {thresholdAnnouncement}
          </span>
        )}
      </div>
    );
  }

  const toneClass = locked
    ? 'border-red-800/60 bg-red-50 text-red-900'
    : isLow
    ? 'border-amber-700/60 bg-[#f2d18c] text-ink'
    : 'border-ink/40 bg-paper-white text-ink';

  return (
    <span
      data-testid={testId}
      className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-bold leading-none ${toneClass}`}
    >
      <Clock size={13} aria-hidden="true" className="shrink-0" />
      <span>{label}</span>
      {thresholdAnnouncement && (
        <span role="status" aria-live="polite" data-testid="screen-time-threshold-live" className="sr-only">
          {thresholdAnnouncement}
        </span>
      )}
    </span>
  );
}
