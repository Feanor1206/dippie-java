import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  AlertCircle,
  CheckCircle2,
  Flag,
  RefreshCw,
  XCircle,
} from 'lucide-react';
import { AdminNav } from '@/features/admin/components/AdminNav';
import { useAuth } from '@/lib/context/AuthContext';
import { cleanMediaTitle } from '@/lib/labels';
import {
  listOpenPlaybackReports,
  PLAYBACK_REPORT_REASONS,
  updatePlaybackReport,
  type PlaybackReport,
  type ReportReason,
  type ReportResolution,
} from '@/lib/services/playbackReportService';
import { formatViDateTime } from '@/lib/timestamps';

function reasonBadgeClass(reason: ReportReason): string {
  if (reason === 'noi_dung_khong_phu_hop') {
    return 'border-red-800/35 bg-red-50 text-red-900';
  }
  if (reason === 'khong_phat' || reason === 'sai_tap') {
    return 'border-amber-700/35 bg-[#f2d18c]/40 text-amber-950';
  }
  if (reason === 'am_thanh') {
    return 'border-brand/40 bg-mint/60 text-ink';
  }
  return 'border-surface-border/50 bg-surface-elevated text-ink';
}

function ReportsSkeleton() {
  return (
    <div role="status" aria-label="Đang tải báo lỗi video" className="space-y-3">
      {Array.from({ length: 3 }).map((_, idx) => (
        <div
          key={idx}
          className="rounded-xl border border-surface-border bg-paper-white p-5 animate-pulse"
        >
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div className="space-y-2.5 flex-1">
              <div className="h-5 w-36 rounded bg-surface-elevated" />
              <div className="h-5 w-2/3 rounded bg-surface-elevated" />
              <div className="h-3.5 w-1/2 rounded bg-surface-elevated" />
            </div>
            <div className="flex gap-2">
              <div className="h-11 w-24 rounded-lg bg-surface-elevated" />
              <div className="h-11 w-24 rounded-lg bg-surface-elevated" />
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}

function PlaybackReports({ token }: { token: string }) {
  const [reports, setReports] = useState<PlaybackReport[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [feedback, setFeedback] = useState<{ text: string; type: 'success' | 'error' } | null>(null);
  const [reload, setReload] = useState(0);
  const [pendingId, setPendingId] = useState<number | null>(null);
  const [selectedReason, setSelectedReason] = useState<ReportReason | 'all'>('all');
  const pending = useRef<AbortController | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setLoadError('');
    void listOpenPlaybackReports(token, controller.signal)
      .then((list) => {
        if (!controller.signal.aborted) setReports(list);
      })
      .catch((failure) => {
        if (!controller.signal.aborted) {
          setLoadError(failure instanceof Error ? failure.message : 'Chưa tải được danh sách báo lỗi.');
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => {
      controller.abort();
      pending.current?.abort();
    };
  }, [token, reload]);

  const reasonCounts = useMemo(() => {
    const counts: Partial<Record<ReportReason, number>> = {};
    for (const report of reports) {
      counts[report.reason] = (counts[report.reason] || 0) + 1;
    }
    return counts;
  }, [reports]);

  const visibleReports = useMemo(
    () => (selectedReason === 'all' ? reports : reports.filter((item) => item.reason === selectedReason)),
    [reports, selectedReason]
  );

  const update = async (report: PlaybackReport, status: ReportResolution) => {
    if (pending.current || loading) return;
    const controller = new AbortController();
    pending.current = controller;
    setPendingId(report.id);
    setFeedback(null);
    try {
      await updatePlaybackReport(token, report.id, status, controller.signal);
      if (controller.signal.aborted) return;
      setReports((items) => items.filter((item) => item.id !== report.id));
      setFeedback({
        type: 'success',
        text:
          status === 'resolved'
            ? `Đã đánh dấu báo lỗi cho "${cleanMediaTitle(report.episodeTitle)}" là đã xử lý.`
            : `Đã bỏ qua báo lỗi cho "${cleanMediaTitle(report.episodeTitle)}".`,
      });
    } catch (failure) {
      if (!controller.signal.aborted) {
        setFeedback({
          type: 'error',
          text: failure instanceof Error ? failure.message : 'Chưa cập nhật được báo lỗi.',
        });
      }
    } finally {
      pending.current = null;
      if (!controller.signal.aborted) setPendingId(null);
    }
  };

  return (
    <>
      <div className="mb-8 flex flex-wrap items-center justify-between gap-4 border-b border-surface-border pb-6">
        <div>
          <div className="flex items-center gap-2 text-xs font-semibold text-brand-dark">
            <Flag size={14} aria-hidden="true" />
            <span>Quản trị nội dung</span>
          </div>
          <h1 className="mt-1 text-2xl font-extrabold text-ink sm:text-3xl">
            Báo lỗi video
          </h1>
          <p className="mt-1 text-sm text-muted">
            Xem và xử lý các báo lỗi video từ ba mẹ.
          </p>
        </div>

        <button
          type="button"
          className="btn-secondary flex items-center gap-1.5 text-xs font-medium text-muted disabled:opacity-50"
          disabled={loading || pendingId !== null}
          onClick={() => setReload((value) => value + 1)}
        >
          <RefreshCw size={13} className={loading ? 'animate-spin' : ''} aria-hidden="true" />
          <span>Làm mới</span>
        </button>
      </div>

      {feedback && (
        <div
          role={feedback.type === 'error' ? 'alert' : 'status'}
          className={`mb-6 flex items-center justify-between gap-3 rounded-xl border p-4 text-xs sm:text-sm ${
            feedback.type === 'success'
              ? 'border-emerald-600/35 bg-mint/45 text-ink'
              : 'border-red-800/35 bg-red-50 text-red-900'
          }`}
        >
          <div className="flex items-center gap-2.5">
            {feedback.type === 'success' ? (
              <CheckCircle2 size={16} className="shrink-0 text-brand-dark" aria-hidden="true" />
            ) : (
              <XCircle size={16} className="shrink-0 text-red-900" aria-hidden="true" />
            )}
            <span>{feedback.text}</span>
          </div>
          <button
            type="button"
            aria-label="Đóng thông báo"
            onClick={() => setFeedback(null)}
            className="inline-flex h-11 w-11 min-h-[44px] min-w-[44px] shrink-0 items-center justify-center rounded-lg text-muted hover:bg-surface-hover hover:text-ink cursor-pointer"
          >
            ✕
          </button>
        </div>
      )}

      {!loading && !loadError && reports.length > 0 && (
        <div
          role="group"
          aria-label="Lọc theo lý do báo lỗi"
          className="mb-5 flex flex-wrap items-center gap-2"
        >
          <button
            type="button"
            aria-pressed={selectedReason === 'all'}
            onClick={() => setSelectedReason('all')}
            className={`series-filter-chip min-h-[44px] ${
              selectedReason === 'all' ? 'series-filter-chip--active' : ''
            }`}
          >
            Tất cả ({reports.length})
          </button>
          {(Object.entries(PLAYBACK_REPORT_REASONS) as Array<[ReportReason, string]>).map(([key, label]) => {
            const count = reasonCounts[key] || 0;
            if (count === 0 && selectedReason !== key) return null;
            const active = selectedReason === key;
            return (
              <button
                key={key}
                type="button"
                aria-pressed={active}
                onClick={() => setSelectedReason(active ? 'all' : key)}
                className={`series-filter-chip min-h-[44px] ${
                  active ? 'series-filter-chip--active' : ''
                }`}
              >
                {label} ({count})
              </button>
            );
          })}
        </div>
      )}

      {loading ? (
        <ReportsSkeleton />
      ) : loadError ? (
        <div
          role="alert"
          className="flex flex-col items-center rounded-xl border border-red-800/35 bg-red-50/70 px-6 py-12 text-center"
        >
          <AlertCircle size={28} className="mb-2 text-red-900" aria-hidden="true" />
          <h2 className="text-base font-bold text-ink">Chưa tải được danh sách báo lỗi</h2>
          <p className="mt-1 max-w-md text-sm text-muted">{loadError}</p>
          <button
            type="button"
            className="btn-primary mt-5 text-xs"
            onClick={() => setReload((value) => value + 1)}
          >
            Thử lại
          </button>
        </div>
      ) : visibleReports.length === 0 ? (
        <div className="flex flex-col items-center rounded-xl border border-dashed border-surface-border bg-paper-white px-6 py-14 text-center">
          <div className="grid h-12 w-12 place-items-center rounded-xl border border-ink/25 bg-mint/60 text-ink">
            <CheckCircle2 size={24} aria-hidden="true" />
          </div>
          <h2 className="mt-4 text-base font-bold text-ink">
            {reports.length === 0 ? 'Không có báo lỗi đang mở' : 'Không có báo lỗi thuộc nhóm này'}
          </h2>
          <p className="mt-1 max-w-md text-sm text-muted">
            {reports.length === 0
              ? 'Tất cả báo lỗi từ ba mẹ đều đã được xử lý.'
              : 'Bạn có thể mở lại danh sách tất cả báo lỗi.'}
          </p>
          {selectedReason !== 'all' && (
            <button
              type="button"
              className="btn-secondary mt-4 text-xs"
              onClick={() => setSelectedReason('all')}
            >
              Xem tất cả ({reports.length})
            </button>
          )}
        </div>
      ) : (
        <ul className="space-y-4">
          {visibleReports.map((report) => {
            const isUpdating = pendingId === report.id;
            return (
              <li
                key={report.id}
                className="rounded-xl border border-[#b7c1b1] bg-paper-white p-5 transition-colors hover:border-ink"
              >
                <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                  <div className="min-w-0 flex-1 space-y-2">
                    <div className="flex flex-wrap items-center gap-2">
                      <span
                        className={`inline-flex items-center rounded border px-2.5 py-0.5 text-xs font-bold ${reasonBadgeClass(
                          report.reason
                        )}`}
                      >
                        {PLAYBACK_REPORT_REASONS[report.reason]}
                      </span>
                      <time dateTime={report.createdAt} className="text-xs text-muted tabular-nums">
                        {formatViDateTime(report.createdAt)}
                      </time>
                    </div>

                    <h2 className="text-base font-bold leading-snug text-ink sm:text-lg">
                      {cleanMediaTitle(report.seriesTitle)} · {cleanMediaTitle(report.episodeTitle)}
                    </h2>

                    <div className="flex flex-wrap items-center gap-2 text-xs text-muted">
                      <span className="rounded border border-surface-border/40 bg-surface-elevated px-2 py-0.5 font-mono text-[11px] text-ink">
                        {report.episodeId}
                      </span>
                      {report.assetId && (
                        <span className="break-all rounded border border-surface-border/40 bg-surface-muted px-2 py-0.5 font-mono text-[11px] text-muted">
                          Nguồn: {report.assetId}
                        </span>
                      )}
                    </div>

                    {report.note && (
                      <p className="mt-2 whitespace-pre-wrap break-words rounded-lg border-l-4 border-sage bg-surface-muted px-3.5 py-2.5 text-sm leading-relaxed text-ink">
                        {report.note}
                      </p>
                    )}
                  </div>

                  <div className="flex flex-wrap items-center gap-2 shrink-0">
                    <button
                      type="button"
                      className="btn-primary text-xs"
                      disabled={pendingId !== null}
                      onClick={() => void update(report, 'resolved')}
                    >
                      <CheckCircle2 size={14} aria-hidden="true" />
                      <span>{isUpdating ? 'Đang cập nhật…' : 'Đã xử lý'}</span>
                    </button>
                    <button
                      type="button"
                      className="btn-secondary text-xs"
                      disabled={pendingId !== null}
                      onClick={() => void update(report, 'dismissed')}
                    >
                      Bỏ qua
                    </button>
                  </div>
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </>
  );
}

export default function AdminReportsPage() {
  const { status, user, token, openAuthModal, retryVerify } = useAuth();
  const isAdmin = status === 'authenticated' && user?.role === 'ROLE_ADMIN' && token;

  return (
    <main id="main-content" tabIndex={-1} className="mx-auto min-h-[70vh] max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
      {status === 'checking' && (
        <div role="status" className="mx-auto max-w-lg rounded-xl border border-surface-border bg-paper-white p-8 text-center text-sm text-muted">
          Đang kiểm tra tài khoản…
        </div>
      )}
      {status === 'unavailable' && (
        <div role="alert" className="mx-auto max-w-lg rounded-xl border border-amber-600/40 bg-[#f2d18c]/25 p-6 text-center">
          <p className="text-sm font-semibold text-ink">Chưa kiểm tra được tài khoản lúc này.</p>
          <button type="button" className="btn-secondary mt-4 text-xs" onClick={() => void retryVerify()}>
            Thử lại
          </button>
        </div>
      )}
      {status === 'guest' && (
        <div className="mx-auto max-w-lg rounded-xl border border-surface-border bg-paper-white p-8 text-center">
          <h1 className="text-xl font-bold text-ink">Quản lý báo lỗi video</h1>
          <p className="mt-2 text-sm text-muted">Đăng nhập bằng tài khoản quản trị để xem và xử lý báo lỗi.</p>
          <button type="button" data-testid="admin-sign-in" className="btn-primary mt-5 text-xs" onClick={() => openAuthModal('login')}>
            Đăng nhập
          </button>
        </div>
      )}
      {status === 'authenticated' && !isAdmin && (
        <div data-testid="admin-forbidden" role="alert" className="mx-auto max-w-lg rounded-xl border border-red-800/30 bg-red-50/70 p-8 text-center">
          <h1 className="text-lg font-bold text-red-900">Không có quyền truy cập</h1>
          <p className="mt-2 text-sm text-muted">Tài khoản này không có quyền quản lý báo lỗi video.</p>
        </div>
      )}
      {isAdmin && (
        <>
          <AdminNav />
          <PlaybackReports key={token} token={token} />
        </>
      )}
    </main>
  );
}
