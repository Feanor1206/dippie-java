import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router';
import * as motion from '@/lib/motion';
import {
  AlertCircle,
  Clock,
  Film,
  History,
  Play,
  RefreshCw,
  RotateCcw,
  Trash2,
} from 'lucide-react';
import { SiteDialog } from '@/components/ui/SiteDialog';
import { ArtworkImage } from '@/features/series/components/ArtworkImage';
import { computeProgressPercent, formatEpisodeTag } from '@/features/series/seriesBrowseUtils';
import { useAuth } from '@/lib/context/AuthContext';
import { useWatchProgress } from '@/lib/hooks/useWatchProgress';
import { cleanMediaTitle, formatDurationMs } from '@/lib/labels';
import {
  buildWatchProgressHref,
  clearWatchProgress,
  deleteWatchProgress,
  notifyWatchHistoryChanged,
  saveWatchProgress,
  type WatchProgressRecord,
} from '@/lib/services/watchProgressService';
import * as movieProgress from '@/lib/services/movieProgress';
import { parseTimestamp } from '@/lib/timestamps';

export type WatchHistoryDayGroup = {
  dayKey: string;
  label: string;
  entries: WatchProgressRecord[];
};

const TIME_ONLY_FORMAT = new Intl.DateTimeFormat('vi-VN', {
  hour: '2-digit',
  minute: '2-digit',
});

const DAY_LABEL_FORMAT = new Intl.DateTimeFormat('vi-VN', {
  weekday: 'long',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
});

function toLocalDayKey(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function groupWatchHistoryByDay(
  records: WatchProgressRecord[],
  nowMs = Date.now()
): WatchHistoryDayGroup[] {
  const sorted = [...records].sort((a, b) => parseTimestamp(b.updatedAt) - parseTimestamp(a.updatedAt));
  const today = new Date(nowMs);
  const yesterday = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 1);
  const todayKey = toLocalDayKey(today);
  const yesterdayKey = toLocalDayKey(yesterday);

  const groups = new Map<string, WatchHistoryDayGroup>();
  for (const entry of sorted) {
    const instant = parseTimestamp(entry.updatedAt);
    let dayKey = 'unknown';
    let label = 'Trước đó';
    if (Number.isFinite(instant)) {
      const date = new Date(instant);
      dayKey = toLocalDayKey(date);
      if (dayKey === todayKey) {
        label = 'Hôm nay';
      } else if (dayKey === yesterdayKey) {
        label = 'Hôm qua';
      } else {
        const formatted = DAY_LABEL_FORMAT.format(date);
        label = formatted.charAt(0).toLocaleUpperCase('vi') + formatted.slice(1);
      }
    }
    const existing = groups.get(dayKey);
    if (existing) existing.entries.push(entry);
    else groups.set(dayKey, { dayKey, label, entries: [entry] });
  }
  return [...groups.values()];
}

function formatWatchedTime(value: string): string {
  const instant = parseTimestamp(value);
  if (!Number.isFinite(instant)) return '';
  return TIME_ONLY_FORMAT.format(instant);
}

function WatchHistorySkeleton() {
  return (
    <div
      role="status"
      aria-label="Đang tải lịch sử xem"
      data-testid="watch-history-skeleton"
      className="mt-6 space-y-4"
    >
      <div className="h-5 w-32 rounded bg-surface-elevated animate-pulse" />
      {Array.from({ length: 3 }).map((_, idx) => (
        <div
          key={idx}
          className="flex flex-col gap-4 rounded-xl border border-surface-border bg-paper-white p-4 sm:flex-row sm:items-center animate-pulse"
        >
          <div className="aspect-video w-full shrink-0 rounded-lg bg-surface-elevated sm:w-44" />
          <div className="min-w-0 flex-1 space-y-2.5">
            <div className="h-4 w-28 rounded bg-surface-elevated" />
            <div className="h-5 w-3/4 rounded bg-surface-elevated" />
            <div className="h-3.5 w-1/2 rounded bg-surface-elevated" />
          </div>
          <div className="h-11 w-24 shrink-0 rounded-lg bg-surface-elevated" />
        </div>
      ))}
    </div>
  );
}

function WatchHistory({ token, userId }: { token: string; userId: number | null }) {
  const { records, loading, error, reload } = useWatchProgress();
  const [confirmClear, setConfirmClear] = useState(false);
  const [removingId, setRemovingId] = useState<string | null>(null);
  const [clearingAll, setClearingAll] = useState(false);
  const [restoring, setRestoring] = useState(false);
  const [actionError, setActionError] = useState('');
  const [notice, setNotice] = useState('');
  const [removedIds, setRemovedIds] = useState<Set<string>>(() => new Set());
  const [lastRemoved, setLastRemoved] = useState<WatchProgressRecord | null>(null);
  const requestRef = useRef<AbortController | null>(null);

  useEffect(() => () => requestRef.current?.abort(), []);

  const visibleRecords = useMemo(
    () => records.filter((record) => !removedIds.has(record.episodeId)),
    [records, removedIds]
  );

  const dayGroups = useMemo(() => groupWatchHistoryByDay(visibleRecords), [visibleRecords]);
  const isBusy = removingId !== null || clearingAll || restoring;
  const listRef = useRef<HTMLDivElement | null>(null);

  motion.useScrollReveal?.(listRef, [loading, dayGroups.length]);

  const removeSingle = async (entry: WatchProgressRecord) => {
    if (requestRef.current) return;
    const controller = new AbortController();
    requestRef.current = controller;
    setRemovingId(entry.episodeId);
    setActionError('');
    setNotice('');
    try {
      await deleteWatchProgress(token, entry.episodeId, { signal: controller.signal });
      if (controller.signal.aborted) return;
      setRemovedIds((prev) => new Set(prev).add(entry.episodeId));
      setLastRemoved(entry);
      notifyWatchHistoryChanged();
    } catch (failure) {
      if (!controller.signal.aborted) {
        setActionError(failure instanceof Error ? failure.message : 'Chưa xóa được tập này khỏi lịch sử. Bạn thử lại nhé.');
      }
    } finally {
      if (requestRef.current === controller) requestRef.current = null;
      if (!controller.signal.aborted) setRemovingId(null);
    }
  };

  const handleUndoRemove = async () => {
    if (!lastRemoved || requestRef.current) return;
    const entry = lastRemoved;
    const controller = new AbortController();
    requestRef.current = controller;
    setRestoring(true);
    setActionError('');
    try {
      await saveWatchProgress(
        token,
        entry.episodeId,
        { assetId: entry.assetId, positionMs: entry.positionMs },
        -1,
        { signal: controller.signal }
      );
      if (controller.signal.aborted) return;
      setRemovedIds((prev) => {
        const next = new Set(prev);
        next.delete(entry.episodeId);
        return next;
      });
      setLastRemoved(null);
      setNotice(`Đã đưa "${cleanMediaTitle(entry.episodeTitle)}" trở lại lịch sử xem.`);
      notifyWatchHistoryChanged();
    } catch (failure) {
      if (!controller.signal.aborted) {
        setActionError(failure instanceof Error ? failure.message : 'Chưa hoàn tác được. Bạn thử lại nhé.');
      }
    } finally {
      if (requestRef.current === controller) requestRef.current = null;
      if (!controller.signal.aborted) setRestoring(false);
    }
  };

  const handleClearAll = async () => {
    if (requestRef.current) return;
    const controller = new AbortController();
    requestRef.current = controller;
    setClearingAll(true);
    setActionError('');
    setNotice('');
    try {
      await clearWatchProgress(token, { signal: controller.signal });
      if (controller.signal.aborted) return;
      if (typeof movieProgress.clearMovieProgress === 'function') {
        movieProgress.clearMovieProgress(userId);
      }
      setConfirmClear(false);
      setLastRemoved(null);
      setRemovedIds(new Set());
      setNotice('Đã xóa hết lịch sử xem.');
      notifyWatchHistoryChanged();
    } catch (failure) {
      if (!controller.signal.aborted) {
        setActionError(failure instanceof Error ? failure.message : 'Chưa xóa được lịch sử xem. Bạn thử lại nhé.');
      }
    } finally {
      if (requestRef.current === controller) requestRef.current = null;
      if (!controller.signal.aborted) setClearingAll(false);
    }
  };

  return (
    <div ref={listRef} className="mt-6 space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-muted">
          {loading
            ? 'Đang tải lịch sử xem…'
            : error
            ? 'Lịch sử xem tạm chưa tải được'
            : visibleRecords.length > 0
            ? `${visibleRecords.length} tập đã xem gần đây`
            : 'Lịch sử đang trống'}
        </p>
        <div className="flex flex-wrap items-center gap-2">
          <Link to="/profile" className="btn-secondary text-xs">
            Thư viện
          </Link>
          {visibleRecords.length > 0 && (
            <button
              type="button"
              data-testid="clear-watch-history-button"
              disabled={isBusy}
              onClick={() => {
                setActionError('');
                setConfirmClear(true);
              }}
              className="btn-secondary text-xs text-red-900"
            >
              <Trash2 size={14} aria-hidden="true" />
              <span>Xóa hết lịch sử</span>
            </button>
          )}
        </div>
      </div>

      {lastRemoved && (
        <div
          role="status"
          data-testid="watch-history-undo-bar"
          className="motion-toast-enter flex flex-wrap items-center justify-between gap-3 rounded-xl border-2 border-ink bg-mint/60 px-4 py-3 text-sm text-ink shadow-brand-sm"
        >
          <span className="min-w-0 flex-1">
            Đã xóa <strong>{cleanMediaTitle(lastRemoved.episodeTitle)}</strong> khỏi lịch sử xem.
          </span>
          <div className="flex items-center gap-2">
            <button
              type="button"
              data-testid="watch-history-undo"
              disabled={isBusy}
              onClick={() => void handleUndoRemove()}
              className="btn-secondary min-h-[44px] px-3.5 py-2 text-xs font-bold"
            >
              <RotateCcw size={13} aria-hidden="true" />
              <span>{restoring ? 'Đang hoàn tác…' : 'Hoàn tác'}</span>
            </button>
            <button
              type="button"
              disabled={isBusy}
              onClick={() => setLastRemoved(null)}
              aria-label="Đóng thanh hoàn tác"
              className="inline-flex h-11 w-11 min-h-[44px] min-w-[44px] items-center justify-center rounded-lg text-muted hover:bg-surface-elevated hover:text-ink cursor-pointer"
            >
              ✕
            </button>
          </div>
        </div>
      )}

      {notice && (
        <div
          role="status"
          className="motion-toast-enter flex items-center justify-between gap-3 rounded-xl border border-ink/30 bg-mint/45 px-4 py-3 text-xs sm:text-sm font-medium text-ink"
        >
          <span>{notice}</span>
          <button
            type="button"
            aria-label="Đóng thông báo"
            onClick={() => setNotice('')}
            className="inline-flex h-11 w-11 min-h-[44px] min-w-[44px] items-center justify-center rounded-lg text-muted hover:bg-surface-elevated hover:text-ink cursor-pointer"
          >
            ✕
          </button>
        </div>
      )}

      {!confirmClear && actionError && (
        <div
          role="alert"
          className="flex items-start gap-2.5 rounded-xl border border-red-800/35 bg-red-50 px-4 py-3 text-xs sm:text-sm text-red-900"
        >
          <AlertCircle size={16} className="mt-0.5 shrink-0" aria-hidden="true" />
          <span>{actionError}</span>
        </div>
      )}

      {loading ? (
        <WatchHistorySkeleton />
      ) : error ? (
        <div
          role="alert"
          className="flex flex-col items-center rounded-xl border border-red-800/30 bg-red-50/70 px-6 py-12 text-center"
        >
          <AlertCircle size={28} className="mb-2 text-red-900" aria-hidden="true" />
          <h2 className="text-base font-bold text-ink">Chưa tải được lịch sử xem</h2>
          <p className="mt-1 max-w-md text-sm text-muted">{error}</p>
          <button type="button" className="btn-primary mt-5 text-xs" onClick={reload}>
            <RefreshCw size={14} aria-hidden="true" />
            <span>Thử tải lại</span>
          </button>
        </div>
      ) : dayGroups.length === 0 ? (
        <section
          data-testid="watch-history-empty"
          className="flex flex-col items-center rounded-xl border border-dashed border-surface-border bg-paper-white px-6 py-14 text-center"
        >
          <div className="grid h-12 w-12 place-items-center rounded-xl border border-ink/25 bg-mint/60 text-ink">
            <Film size={24} aria-hidden="true" />
          </div>
          <h2 className="mt-4 text-lg font-bold text-ink">Chưa có tập nào trong lịch sử xem</h2>
          <p className="mt-1.5 max-w-md text-sm leading-relaxed text-muted">
            Mỗi khi bé mở xem một tập trên Dippie, mốc dừng sẽ tự lưu lại ở đây để ba mẹ bấm xem tiếp.
          </p>
          <div className="mt-6 flex flex-wrap justify-center gap-3">
            <Link to="/series" className="btn-primary text-xs">
              Xem phim bộ
            </Link>
            <Link to="/search" className="btn-secondary text-xs">
              Tìm phim
            </Link>
          </div>
        </section>
      ) : (
        <div className="space-y-8" data-testid="watch-history-groups">
          {dayGroups.map((group) => (
            <section key={group.dayKey} data-reveal-section="" aria-labelledby={`history-day-${group.dayKey}`} className="space-y-3">
              <div className="flex items-center justify-between gap-2 border-b border-surface-border/40 pb-2">
                <h2
                  id={`history-day-${group.dayKey}`}
                  className="text-lg font-bold text-ink"
                >
                  {group.label}
                </h2>
                <span className="rounded-full bg-surface-elevated px-2.5 py-0.5 text-xs font-semibold text-muted tabular-nums">
                  {group.entries.length} tập
                </span>
              </div>

              <ul className="space-y-3">
                {group.entries.map((entry) => {
                  const percent = entry.completed ? 100 : computeProgressPercent(entry.positionMs, entry.durationMs);
                  const episodeTag = formatEpisodeTag(entry.episodeNumber);
                  const episodeHref = buildWatchProgressHref(entry);
                  const watchedAt = formatWatchedTime(entry.updatedAt);
                  const isRemovingThis = removingId === entry.episodeId;
                  const cleanSeriesTitle = cleanMediaTitle(entry.seriesTitle);
                  const cleanEpisodeTitle = cleanMediaTitle(entry.episodeTitle);

                  return (
                    <li
                      key={entry.episodeId}
                      data-reveal-card=""
                      data-testid={`history-item-${entry.episodeId}`}
                      className="flex flex-col gap-4 rounded-xl border border-[#b7c1b1] bg-paper-white p-3.5 transition-colors hover:border-ink sm:flex-row sm:items-center sm:p-4"
                    >
                      <Link
                        to={episodeHref}
                        className="group relative block w-full shrink-0 overflow-hidden rounded-lg border border-ink/20 sm:w-44"
                        aria-label={`Xem tiếp ${cleanEpisodeTitle}`}
                      >
                        <ArtworkImage
                          src={entry.thumbnailUrl ?? null}
                          alt={`${cleanSeriesTitle}: ${cleanEpisodeTitle}`}
                          title={cleanSeriesTitle || cleanEpisodeTitle}
                          seed={entry.seriesId || entry.episodeId}
                          imgClassName="transition-transform duration-300 group-hover:scale-105"
                        >
                          <span className="continue-time-badge resume-mark">
                            {entry.completed
                              ? 'Đã xong'
                              : entry.durationMs && entry.durationMs > 0
                              ? `${formatDurationMs(entry.positionMs)} / ${formatDurationMs(entry.durationMs)}`
                              : formatDurationMs(entry.positionMs)}
                          </span>
                          {percent !== null && (
                            <div
                              className="continue-progress-track"
                              role="progressbar"
                              aria-label="Tiến độ xem"
                              aria-valuenow={percent}
                              aria-valuemin={0}
                              aria-valuemax={100}
                            >
                              <div className="continue-progress-fill motion-progress-fill" style={{ width: `${percent}%` }} />
                            </div>
                          )}
                        </ArtworkImage>
                      </Link>

                      <div className="min-w-0 flex-1 space-y-1.5">
                        <div className="flex flex-wrap items-center gap-2 text-xs">
                          {episodeTag && <span className="film-label">{episodeTag}</span>}
                          <span className="truncate font-semibold text-brand-dark">{cleanSeriesTitle}</span>
                        </div>

                        <Link
                          to={episodeHref}
                          className="inline-flex min-h-[44px] items-center font-display text-base font-bold leading-snug text-ink hover:text-brand-dark hover:underline underline-offset-4"
                        >
                          {cleanEpisodeTitle}
                        </Link>

                        <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted">
                          <span className="font-medium text-ink/80">
                            {entry.completed
                              ? 'Đã xem xong'
                              : `Dừng ở ${formatDurationMs(entry.positionMs)}${
                                  percent !== null ? ` (${percent}%)` : ''
                                }`}
                          </span>
                          {watchedAt && (
                            <time dateTime={entry.updatedAt} className="inline-flex items-center gap-1 tabular-nums">
                              <Clock size={12} aria-hidden="true" />
                              <span>Lúc {watchedAt}</span>
                            </time>
                          )}
                        </div>
                      </div>

                      <div className="flex flex-wrap items-center gap-2 sm:flex-col sm:items-end lg:flex-row">
                        <Link to={episodeHref} className="btn-primary flex-1 text-xs sm:flex-none">
                          <Play size={13} className="fill-current" aria-hidden="true" />
                          <span>{entry.completed ? 'Xem lại' : 'Xem tiếp'}</span>
                        </Link>
                        <button
                          type="button"
                          disabled={isBusy}
                          aria-label={`Xóa ${cleanEpisodeTitle} khỏi lịch sử`}
                          onClick={() => void removeSingle(entry)}
                          className="btn-secondary text-xs text-red-900"
                        >
                          <Trash2 size={13} aria-hidden="true" />
                          <span>{isRemovingThis ? 'Đang xóa…' : 'Xóa'}</span>
                        </button>
                      </div>
                    </li>
                  );
                })}
              </ul>
            </section>
          ))}
        </div>
      )}

      {confirmClear && (
        <SiteDialog
          title="Xóa hết lịch sử xem?"
          busy={clearingAll}
          tone="danger"
          onClose={() => setConfirmClear(false)}
        >
          <p className="text-sm leading-relaxed text-muted">
            Dippie sẽ xóa mốc xem của tất cả các tập trong tài khoản này. Lịch sử đã xóa sẽ không lấy lại được và mục <strong>Xem tiếp</strong> sẽ trống cho đến khi nhà mình mở xem tập mới.
          </p>
          {actionError && (
            <p role="alert" className="mt-3 rounded-lg border border-red-800/35 bg-red-50 p-3 text-xs text-red-900">
              {actionError}
            </p>
          )}
          <div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <button
              type="button"
              autoFocus
              className="btn-secondary w-full sm:w-auto text-xs"
              disabled={clearingAll}
              onClick={() => setConfirmClear(false)}
            >
              Giữ lại lịch sử
            </button>
            <button
              type="button"
              className="inline-flex min-h-[44px] w-full sm:w-auto items-center justify-center gap-2 border-2 border-ink bg-red-800 px-5 py-2.5 text-xs font-bold text-paper-white shadow-brand-sm transition-transform duration-150 hover:-translate-y-0.5 hover:bg-red-900 disabled:cursor-not-allowed disabled:opacity-50 cursor-pointer"
              disabled={clearingAll}
              onClick={() => void handleClearAll()}
            >
              {clearingAll ? 'Đang xóa…' : 'Xóa hết lịch sử'}
            </button>
          </div>
        </SiteDialog>
      )}
    </div>
  );
}

export default function WatchHistoryPage() {
  const { status, token, user, openAuthModal, retryVerify } = useAuth();

  return (
    <main id="main-content" tabIndex={-1} className="page-shell min-h-[72vh] max-w-4xl py-8 sm:py-12">
      <header className="rounded-xl border-2 border-ink bg-paper-white p-6 shadow-brand-sm sm:p-7">
        <div className="flex items-center gap-3">
          <div className="grid h-11 w-11 shrink-0 place-items-center rounded-lg border border-ink bg-mint text-ink">
            <History size={20} aria-hidden="true" />
          </div>
          <div>
            <span className="text-xs font-bold text-brand-dark">
              Tài khoản của bạn
            </span>
            <h1 className="text-2xl font-bold text-ink sm:text-3xl">Lịch sử xem</h1>
          </div>
        </div>
      </header>

      {status === 'authenticated' && token && <WatchHistory token={token} userId={user?.id ?? null} />}

      {status === 'checking' && <WatchHistorySkeleton />}

      {status === 'guest' && (
        <section className="mt-6 rounded-xl border border-surface-border bg-paper-white p-6 sm:p-8">
          <h2 className="text-lg font-bold text-ink">Đăng nhập để xem lịch sử xem</h2>
          <p className="mt-2 max-w-xl text-sm leading-relaxed text-muted">
            Khi đăng nhập, các tập bé đã xem được xếp theo từng ngày để ba mẹ mở lại hoặc xóa bất cứ lúc nào.
          </p>
          <div className="mt-5 flex flex-wrap gap-3">
            <button type="button" className="btn-primary text-xs sm:text-sm" onClick={() => openAuthModal('login')}>
              Đăng nhập
            </button>
            <button type="button" className="btn-secondary text-xs sm:text-sm" onClick={() => openAuthModal('register')}>
              Tạo tài khoản
            </button>
          </div>
        </section>
      )}

      {status === 'unavailable' && (
        <section role="alert" className="mt-6 rounded-xl border border-amber-600/40 bg-[#f2d18c]/25 p-6">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-start gap-3">
              <AlertCircle size={20} className="mt-0.5 shrink-0 text-amber-900" aria-hidden="true" />
              <div>
                <h2 className="text-sm font-bold text-ink">Chưa kiểm tra được tài khoản lúc này</h2>
                <p className="mt-1 text-xs text-muted">
                  Kết nối đến máy chủ đang tạm gián đoạn. Bạn chờ vài giây rồi thử lại nhé.
                </p>
              </div>
            </div>
            <button type="button" className="btn-secondary shrink-0 text-xs" onClick={() => void retryVerify()}>
              <RefreshCw size={14} aria-hidden="true" />
              <span>Thử lại</span>
            </button>
          </div>
        </section>
      )}
    </main>
  );
}
