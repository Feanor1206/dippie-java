import React from 'react';
import { Clock } from 'lucide-react';
import { formatRemainingTime } from '@/lib/services/screenTimeService';

export function ScreenTimeChip({
  remainingSeconds,
  locked,
  variant = 'bar',
  'data-testid': testId = 'screen-time-chip',
}: {
  remainingSeconds: number | null;
  locked: boolean;
  variant?: 'bar' | 'player';
  'data-testid'?: string;
}) {
  if (!locked && remainingSeconds === null) {
    return null;
  }

  const label = formatRemainingTime(remainingSeconds, locked);
  if (!label) return null;

  const isLow = !locked && remainingSeconds !== null && remainingSeconds > 0 && remainingSeconds <= 300;
  const toneClass = locked
    ? 'border-red-800/60 bg-red-50 text-red-900'
    : isLow
    ? 'border-amber-700/60 bg-[#f2d18c] text-ink'
    : variant === 'player'
    ? 'border-ink bg-mint text-ink'
    : 'border-ink/40 bg-paper-white text-ink';

  return (
    <span
      role="status"
      aria-live="polite"
      data-testid={testId}
      className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-bold leading-none ${toneClass}`}
    >
      <Clock size={13} aria-hidden="true" className="shrink-0" />
      <span>{label}</span>
    </span>
  );
}
