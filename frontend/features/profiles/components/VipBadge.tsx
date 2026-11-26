import React from 'react';
import { Crown } from 'lucide-react';

export function VipBadge({
  unlocked = false,
  'data-testid': testId = 'vip-badge',
}: {
  unlocked?: boolean;
  'data-testid'?: string;
}) {
  return (
    <span
      data-testid={testId}
      className={`motion-chip-pop inline-flex items-center gap-1 rounded-full border border-ink px-2 py-0.5 text-[0.625rem] font-extrabold leading-none ${
        unlocked ? 'bg-mint text-ink' : 'bg-[#f2d18c] text-ink'
      }`}
    >
      <Crown size={11} aria-hidden="true" />
      <span>{unlocked ? 'Đã mở câu hỏi' : 'Phim cần trả lời câu hỏi'}</span>
    </span>
  );
}
