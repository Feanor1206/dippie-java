import React from 'react';
import { getChildAvatarMeta } from '@/lib/services/profileService';

function AvatarGlyph({ avatarKey, color }: { avatarKey: string; color: string }) {
  switch (avatarKey) {
    case 'bunny':
      return (
        <svg viewBox="0 0 24 24" className="h-3/5 w-3/5" fill="none" stroke={color} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M9 10V4.5a1.5 1.5 0 0 1 3 0V10" />
          <path d="M12 10V4.5a1.5 1.5 0 0 1 3 0V10" />
          <circle cx="12" cy="14.5" r="5.5" />
          <circle cx="10" cy="14" r="0.8" fill={color} />
          <circle cx="14" cy="14" r="0.8" fill={color} />
          <path d="M10.8 16.5c.7.6 1.7.6 2.4 0" />
        </svg>
      );
    case 'cat':
      return (
        <svg viewBox="0 0 24 24" className="h-3/5 w-3/5" fill="none" stroke={color} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M5 9 7 4l3.5 2.5h3L17 4l2 5v5a7 7 0 0 1-14 0V9Z" />
          <circle cx="9.5" cy="13" r="0.8" fill={color} />
          <circle cx="14.5" cy="13" r="0.8" fill={color} />
          <path d="M10.8 15.5c.7.5 1.7.5 2.4 0" />
        </svg>
      );
    case 'dino':
      return (
        <svg viewBox="0 0 24 24" className="h-3/5 w-3/5" fill="none" stroke={color} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M6 19v-6a6 6 0 0 1 12 0v2h-5v4H6Z" />
          <path d="M9 7 8 4m4 2V3m3 4 1.5-2" />
          <circle cx="13.5" cy="11" r="0.8" fill={color} />
        </svg>
      );
    case 'fox':
      return (
        <svg viewBox="0 0 24 24" className="h-3/5 w-3/5" fill="none" stroke={color} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M4 6l4 4h8l4-4-1.5 9L12 20 5.5 15 4 6Z" />
          <circle cx="9.5" cy="13" r="0.8" fill={color} />
          <circle cx="14.5" cy="13" r="0.8" fill={color} />
          <circle cx="12" cy="16.5" r="0.9" fill={color} />
        </svg>
      );
    case 'owl':
      return (
        <svg viewBox="0 0 24 24" className="h-3/5 w-3/5" fill="none" stroke={color} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M6 7c0-1.5 2-2.5 6-2.5S18 5.5 18 7v7a6 6 0 0 1-12 0V7Z" />
          <circle cx="9.5" cy="11" r="1.7" />
          <circle cx="14.5" cy="11" r="1.7" />
          <path d="m11 14 1 1.5 1-1.5" />
        </svg>
      );
    case 'panda':
      return (
        <svg viewBox="0 0 24 24" className="h-3/5 w-3/5" fill="none" stroke={color} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <circle cx="6.5" cy="7.5" r="2.2" fill={color} />
          <circle cx="17.5" cy="7.5" r="2.2" fill={color} />
          <circle cx="12" cy="13" r="6" />
          <circle cx="9.6" cy="12.2" r="1.3" fill={color} />
          <circle cx="14.4" cy="12.2" r="1.3" fill={color} />
          <path d="M10.8 15.5c.7.5 1.7.5 2.4 0" />
        </svg>
      );
    case 'star':
      return (
        <svg viewBox="0 0 24 24" className="h-3/5 w-3/5" fill="none" stroke={color} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="m12 3.5 2.7 5.5 6 .9-4.3 4.2 1 6-5.4-2.8-5.4 2.8 1-6-4.3-4.2 6-.9L12 3.5Z" />
        </svg>
      );
    case 'bear':
    default:
      return (
        <svg viewBox="0 0 24 24" className="h-3/5 w-3/5" fill="none" stroke={color} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <circle cx="6.5" cy="7.5" r="2.3" />
          <circle cx="17.5" cy="7.5" r="2.3" />
          <circle cx="12" cy="13.5" r="6" />
          <circle cx="9.8" cy="12.5" r="0.8" fill={color} />
          <circle cx="14.2" cy="12.5" r="0.8" fill={color} />
          <path d="M10.5 15.5c.8.7 2.2.7 3 0" />
        </svg>
      );
  }
}

export function ChildAvatarBadge({
  avatarKey,
  size = 'md',
  decorative = true,
}: {
  avatarKey?: string | null;
  size?: 'sm' | 'md' | 'lg';
  decorative?: boolean;
}) {
  const meta = getChildAvatarMeta(avatarKey);
  const sizeClass =
    size === 'sm'
      ? 'h-7 w-7 rounded-md'
      : size === 'lg'
      ? 'h-16 w-16 rounded-2xl'
      : 'h-11 w-11 rounded-xl';
  return (
    <span
      role={decorative ? undefined : 'img'}
      aria-label={decorative ? undefined : meta.label}
      aria-hidden={decorative ? 'true' : undefined}
      className={`inline-flex shrink-0 items-center justify-center border-2 border-ink ${meta.bgClass} ${sizeClass}`}
    >
      <AvatarGlyph avatarKey={meta.key} color={meta.accentHex} />
    </span>
  );
}
