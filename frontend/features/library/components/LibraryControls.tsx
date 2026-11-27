import React, { useState } from 'react';
import { Bookmark, Check, Heart, Loader2, RefreshCw, Star, X } from 'lucide-react';
import { useWatchlist } from '@/lib/context/WatchlistContext';
import type { LibraryActionIntent } from '@/lib/services/libraryService';

const RATING_SCALE = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10] as const;
const ICON_BUTTON_CLASS =
  'inline-flex h-11 w-11 min-h-[44px] min-w-[44px] items-center justify-center border transition-colors cursor-pointer disabled:opacity-60';
const PANEL_BUTTON_CLASS =
  'inline-flex min-h-[44px] items-center gap-1.5 rounded-md border px-3 py-2 text-xs font-semibold transition-colors cursor-pointer disabled:opacity-60';

function ActionIcon({
  kind,
  pending,
  unconfirmed,
  active,
  size,
}: {
  kind: 'watchlist' | 'favorite';
  pending: boolean;
  unconfirmed: boolean;
  active: boolean;
  size: number;
}) {
  if (pending) return <Loader2 size={size} className="animate-spin" aria-hidden="true" />;
  if (unconfirmed) return <RefreshCw size={size} aria-hidden="true" />;
  if (kind === 'favorite') {
    return <Heart size={size} className={active ? 'fill-current' : ''} aria-hidden="true" />;
  }
  return active ? <Check size={size} aria-hidden="true" /> : <Bookmark size={size} aria-hidden="true" />;
}

function getToggleButtonState(
  kind: 'watchlist' | 'favorite',
  title: string,
  active: boolean,
  pending: boolean,
  unconfirmed: boolean,
  intent: LibraryActionIntent | null
) {
  const isWatchlist = kind === 'watchlist';
  const ariaLabel = pending
    ? `Đang cập nhật ${isWatchlist ? 'Xem sau' : 'Yêu thích'} cho ${title}`
    : unconfirmed
    ? intent === 'add'
      ? isWatchlist
        ? `Chưa lưu được ${title} vào Xem sau, nhấn để thử lại`
        : `Chưa thêm được ${title} vào Yêu thích, nhấn để thử lại`
      : isWatchlist
      ? `Chưa bỏ được ${title} khỏi Xem sau, nhấn để thử lại`
      : `Chưa bỏ được ${title} khỏi Yêu thích, nhấn để thử lại`
    : active
    ? isWatchlist
      ? `Bỏ ${title} khỏi Xem sau`
      : `Bỏ ${title} khỏi Yêu thích`
    : isWatchlist
    ? `Thêm ${title} vào Xem sau`
    : `Thêm ${title} vào Yêu thích`;

  const panelLabel = pending
    ? 'Đang lưu…'
    : unconfirmed
    ? intent === 'add'
      ? isWatchlist
        ? 'Thử lưu lại'
        : 'Thử thêm lại'
      : 'Thử bỏ lại'
    : active
    ? isWatchlist
      ? 'Đã lưu Xem sau'
      : 'Đã yêu thích'
    : isWatchlist
    ? 'Xem sau'
    : 'Yêu thích';

  const tooltip = active
    ? isWatchlist
      ? 'Đã lưu Xem sau'
      : 'Đã yêu thích'
    : isWatchlist
    ? 'Xem sau'
    : 'Yêu thích';

  return { ariaLabel, panelLabel, tooltip };
}

export type QuickSaveButtonsProps = {
  itemId: string;
  title: string;
  poster?: string;
  variant?: 'overlay' | 'inline';
  testIdPrefix?: string;
};

export function QuickSaveButtons({
  itemId,
  title,
  poster = '',
  variant = 'overlay',
  testIdPrefix,
}: QuickSaveButtonsProps) {
  const {
    isWatchlisted,
    toggleWatchlist,
    isFavorite,
    toggleFavorite,
    isActionPending,
    isActionUnconfirmed,
    getActionIntent,
  } = useWatchlist();

  const isOverlay = variant === 'overlay';
  const shapeClass = isOverlay ? `${ICON_BUTTON_CLASS} rounded-full shadow-sm` : `${ICON_BUTTON_CLASS} rounded-md`;
  const idleClass = isOverlay
    ? 'border-ink/40 bg-paper-white/95 text-ink hover:bg-mint'
    : 'border-surface-border bg-surface text-muted hover:bg-surface-hover hover:text-ink';

  const actions = [
    {
      kind: 'watchlist' as const,
      active: isWatchlisted(itemId),
      pending: isActionPending(itemId, 'watchlist'),
      unconfirmed: isActionUnconfirmed(itemId, 'watchlist'),
      activeClass: 'border-ink bg-mint text-ink hover:bg-brand-hover',
      testId: testIdPrefix ? `${testIdPrefix}-quick-watchlist` : 'quick-watchlist-btn',
      onToggle: () => void toggleWatchlist(itemId, title, poster),
    },
    {
      kind: 'favorite' as const,
      active: isFavorite(itemId),
      pending: isActionPending(itemId, 'favorite'),
      unconfirmed: isActionUnconfirmed(itemId, 'favorite'),
      activeClass: 'border-ink bg-[#f2d18c] text-ink hover:bg-[#f7dfa8]',
      testId: testIdPrefix ? `${testIdPrefix}-quick-favorite` : 'quick-favorite-btn',
      onToggle: () => void toggleFavorite(itemId, title, poster),
    },
  ];

  return (
    <div className="flex items-center gap-1.5">
      {actions.map((control) => {
        const intent = control.unconfirmed ? getActionIntent(itemId, control.kind) : null;
        const copy = getToggleButtonState(control.kind, title, control.active, control.pending, control.unconfirmed, intent);
        const toneClass = control.unconfirmed
          ? 'border-amber-500 bg-amber-300 text-ink hover:bg-amber-200'
          : control.active
          ? control.activeClass
          : idleClass;
        return (
          <button
            key={control.kind}
            type="button"
            data-testid={control.testId}
            aria-label={copy.ariaLabel}
            aria-pressed={control.active}
            title={copy.tooltip}
            disabled={control.pending}
            onClick={(event) => {
              event.preventDefault();
              event.stopPropagation();
              control.onToggle();
            }}
            className={`${shapeClass} ${toneClass}`}
          >
            <ActionIcon
              kind={control.kind}
              pending={control.pending}
              unconfirmed={control.unconfirmed}
              active={control.active}
              size={15}
            />
          </button>
        );
      })}
    </div>
  );
}

export type ItemLibraryPanelProps = {
  itemId: string;
  title: string;
  poster?: string;
  scopeLabel: string;
  testIdPrefix: string;
  compact?: boolean;
};

export function ItemLibraryPanel({
  itemId,
  title,
  poster = '',
  scopeLabel,
  testIdPrefix,
  compact = false,
}: ItemLibraryPanelProps) {
  const {
    isWatchlisted,
    toggleWatchlist,
    isFavorite,
    toggleFavorite,
    getRating,
    rateMovie,
    removeRating,
    isActionPending,
    isActionUnconfirmed,
    getActionIntent,
    getUnconfirmedRating,
  } = useWatchlist();
  const [hoverScore, setHoverScore] = useState<number | null>(null);
  const [ratingOpen, setRatingOpen] = useState(!compact);

  const currentRating = getRating(itemId);
  const ratingPending = isActionPending(itemId, 'rating');
  const ratingUnconfirmed = isActionUnconfirmed(itemId, 'rating');
  const ratingIntent = ratingUnconfirmed ? getActionIntent(itemId, 'rating') : null;
  const unconfirmedScore = getUnconfirmedRating(itemId);

  const toggles = [
    {
      kind: 'watchlist' as const,
      active: isWatchlisted(itemId),
      pending: isActionPending(itemId, 'watchlist'),
      unconfirmed: isActionUnconfirmed(itemId, 'watchlist'),
      activeClass: 'border-ink bg-mint text-ink hover:bg-brand-hover',
      testId: `${testIdPrefix}-watchlist-toggle`,
      onToggle: () => void toggleWatchlist(itemId, title, poster),
    },
    {
      kind: 'favorite' as const,
      active: isFavorite(itemId),
      pending: isActionPending(itemId, 'favorite'),
      unconfirmed: isActionUnconfirmed(itemId, 'favorite'),
      activeClass: 'border-ink bg-[#f2d18c] text-ink hover:bg-[#f7dfa8]',
      testId: `${testIdPrefix}-favorite-toggle`,
      onToggle: () => void toggleFavorite(itemId, title, poster),
    },
  ];

  return (
    <div
      data-testid={`${testIdPrefix}-library-controls`}
      className={
        compact
          ? 'mt-4 w-fit max-w-full rounded-lg border border-ink/25 bg-paper-white/95 p-2.5 text-ink shadow-sm'
          : 'mt-4 border-t border-surface-border pt-4'
      }
    >
      <div className="flex flex-wrap items-center gap-2">
        {toggles.map((control) => {
          const intent = control.unconfirmed ? getActionIntent(itemId, control.kind) : null;
          const copy = getToggleButtonState(control.kind, title, control.active, control.pending, control.unconfirmed, intent);
          const toneClass = control.unconfirmed
            ? 'border-amber-500 bg-amber-500/15 text-amber-900'
            : control.active
            ? control.activeClass
            : 'border-surface-border bg-surface text-ink hover:bg-surface-hover';
          return (
            <button
              key={control.kind}
              type="button"
              data-testid={control.testId}
              aria-pressed={control.active}
              disabled={control.pending}
              onClick={control.onToggle}
              className={`${PANEL_BUTTON_CLASS} ${toneClass}`}
            >
              <ActionIcon
                kind={control.kind}
                pending={control.pending}
                unconfirmed={control.unconfirmed}
                active={control.active}
                size={14}
              />
              <span>{copy.panelLabel}</span>
            </button>
          );
        })}

        {compact && (
          <button
            type="button"
            data-testid={`${testIdPrefix}-rating-expander`}
            aria-expanded={ratingOpen}
            onClick={() => setRatingOpen((prev) => !prev)}
            className={`${PANEL_BUTTON_CLASS} ${
              currentRating
                ? 'border-ink bg-surface-elevated text-ink'
                : 'border-surface-border bg-surface text-ink hover:bg-surface-hover'
            }`}
          >
            <Star size={14} className={currentRating ? 'fill-amber-400 text-amber-600' : ''} aria-hidden="true" />
            <span>{currentRating ? `Điểm của bạn: ${currentRating}/10` : 'Chấm điểm'}</span>
          </button>
        )}
      </div>

      {ratingOpen && (
        <div data-testid={`${testIdPrefix}-rating-control`} className="mt-3 flex flex-col gap-2">
          <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
            <span className="font-semibold text-ink">
              {currentRating
                ? `Điểm bạn chấm cho ${scopeLabel.toLowerCase()}: ${currentRating}/10`
                : `Chấm điểm ${scopeLabel.toLowerCase()} (từ 1 đến 10)`}
            </span>
            {currentRating !== undefined && !ratingUnconfirmed && (
              <button
                type="button"
                data-testid={`${testIdPrefix}-rating-remove`}
                disabled={ratingPending}
                onClick={() => void removeRating(itemId, title)}
                className="inline-flex min-h-[44px] items-center gap-1 rounded px-2 text-xs font-semibold text-muted underline underline-offset-4 hover:text-red-800 cursor-pointer disabled:opacity-50"
              >
                <X size={13} aria-hidden="true" />
                <span>Xóa điểm</span>
              </button>
            )}
          </div>

          <div
            role="group"
            aria-label={`Chọn điểm từ 1 đến 10 cho ${title}`}
            className="flex flex-wrap items-center gap-1.5"
          >
            {RATING_SCALE.map((score) => {
              const active = hoverScore !== null ? hoverScore >= score : (currentRating ?? 0) >= score;
              return (
                <button
                  key={score}
                  type="button"
                  data-testid={`${testIdPrefix}-rate-${score}`}
                  disabled={ratingPending}
                  aria-pressed={currentRating === score}
                  aria-label={`Chấm ${score} trên 10 điểm cho ${title}`}
                  onMouseEnter={() => setHoverScore(score)}
                  onMouseLeave={() => setHoverScore(null)}
                  onClick={() => void rateMovie(itemId, score, title)}
                  className={`flex h-11 w-11 min-h-[44px] min-w-[44px] items-center justify-center rounded-md border text-xs font-bold transition-colors cursor-pointer disabled:opacity-60 ${
                    active
                      ? 'border-ink bg-[#f2d18c] text-ink shadow-sm'
                      : 'border-surface-border bg-surface-muted text-muted hover:bg-surface-hover hover:text-ink'
                  }`}
                >
                  {score}
                </button>
              );
            })}
          </div>

          {ratingUnconfirmed && (
            <div
              data-testid={`${testIdPrefix}-rating-unconfirmed`}
              className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-xs text-amber-900"
            >
              <span>
                {ratingIntent === 'rate'
                  ? `Chưa lưu được điểm ${unconfirmedScore ?? currentRating ?? ''}/10.`
                  : 'Chưa xóa được điểm.'}
              </span>
              <button
                type="button"
                disabled={ratingPending}
                onClick={() =>
                  ratingIntent === 'rate'
                    ? void rateMovie(itemId, unconfirmedScore ?? currentRating ?? 10, title)
                    : void removeRating(itemId, title)
                }
                className="inline-flex min-h-[44px] items-center gap-1 rounded bg-amber-500/20 px-3 py-1 font-semibold text-amber-900 hover:bg-amber-500/30 cursor-pointer"
              >
                <RefreshCw size={12} aria-hidden="true" />
                <span>{ratingIntent === 'rate' ? 'Thử lưu lại' : 'Thử xóa lại'}</span>
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
