import React, { useState, useEffect } from 'react';
import { Link } from 'react-router';
import {
  Database,
  Film,
  ArrowRight,
  Clock,
  RefreshCw,
  RotateCcw,
  CheckCircle2,
  AlertTriangle,
  XCircle,
  Activity,
} from 'lucide-react';
import { CatalogImportModal } from '@/features/admin/components/CatalogImportModal';
import { AdminNav } from '@/features/admin/components/AdminNav';
import { useAuth } from '@/lib/context/AuthContext';
import {
  getCatalogSeriesList,
  getImportHistory,
  getCatalogSnapshots,
  retryCatalogSnapshot,
  CatalogSeriesSummary,
  CatalogImportResponse,
  CatalogSnapshot,
} from '@/lib/services/catalogService';

export default function AdminImportPage() {
  const { status, user, token, openAuthModal, retryVerify } = useAuth();
  const isAdmin = status === 'authenticated' && user?.role === 'ROLE_ADMIN' && !!token;
  const [seriesList, setSeriesList] = useState<CatalogSeriesSummary[]>([]);
  const [history, setHistory] = useState<any[]>([]);
  const [snapshots, setSnapshots] = useState<CatalogSnapshot[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [refreshCount, setRefreshCount] = useState<number>(0);
  const [retryingId, setRetryingId] = useState<string | null>(null);
  const [actionMessage, setActionMessage] = useState<{ text: string; type: 'success' | 'error' } | null>(null);
  const [loadErrors, setLoadErrors] = useState({ series: false, history: false, snapshots: false });

  useEffect(() => {
    if (!isAdmin) return;
    const controller = new AbortController();
    setIsLoading(true);
    setLoadErrors({ series: false, history: false, snapshots: false });

    Promise.allSettled([
      getCatalogSeriesList(undefined, controller.signal),
      getImportHistory(undefined, controller.signal),
      getCatalogSnapshots(undefined, controller.signal),
    ]).then(([seriesResult, historyResult, snapshotsResult]) => {
      if (controller.signal.aborted) return;
      setSeriesList(seriesResult.status === 'fulfilled' ? seriesResult.value : []);
      setHistory(historyResult.status === 'fulfilled' ? historyResult.value : []);
      setSnapshots(snapshotsResult.status === 'fulfilled' ? snapshotsResult.value : []);
      setLoadErrors({
        series: seriesResult.status === 'rejected',
        history: historyResult.status === 'rejected',
        snapshots: snapshotsResult.status === 'rejected',
      });
      setIsLoading(false);
    });

    return () => controller.abort();
  }, [refreshCount, isAdmin, token]);

  const handleImportSuccess = (_res: CatalogImportResponse) => {
    setRefreshCount((count) => count + 1);
  };

  const handleRetrySnapshot = async (id: string) => {
    setRetryingId(id);
    setActionMessage(null);
    try {
      const updated = await retryCatalogSnapshot(id);
      setActionMessage({
        text: `Đã gửi yêu cầu đồng bộ lại bản dữ liệu [${id}] (bản #${updated.snapshotVersion}).`,
        type: 'success',
      });
      setRefreshCount((count) => count + 1);
    } catch (err: any) {
      setActionMessage({
        text: err?.message || `Chưa gửi được yêu cầu đồng bộ lại bản dữ liệu [${id}].`,
        type: 'error',
      });
    } finally {
      setRetryingId(null);
    }
  };

  if (status === 'checking') {
    return (
      <main id="main-content" tabIndex={-1} className="page-shell py-12">
        <div role="status" className="mx-auto max-w-md rounded-2xl border border-surface-border/40 bg-paper-white p-8 text-center text-sm text-muted">
          Đang kiểm tra tài khoản…
        </div>
      </main>
    );
  }
  if (status === 'unavailable') {
    return (
      <main id="main-content" tabIndex={-1} className="page-shell py-12">
        <div role="alert" className="mx-auto max-w-md rounded-2xl border-2 border-amber-700/40 bg-amber-500/10 p-8 text-center text-sm text-amber-950">
          <p className="font-semibold">Chưa kiểm tra được tài khoản.</p>
          <button type="button" className="btn-secondary mt-4 text-xs" onClick={() => retryVerify()}>
            Thử lại
          </button>
        </div>
      </main>
    );
  }
  if (status === 'guest') {
    return (
      <main id="main-content" tabIndex={-1} className="page-shell py-12">
        <div className="mx-auto max-w-md rounded-2xl border-2 border-ink bg-paper-white p-8 text-center shadow-brand">
          <p className="text-sm text-muted">Đăng nhập để nhập dữ liệu kho phim.</p>
          <button type="button" data-testid="admin-sign-in" className="btn-primary mt-4 text-xs" onClick={() => openAuthModal('login')}>
            Đăng nhập
          </button>
        </div>
      </main>
    );
  }
  if (!isAdmin) {
    return (
      <main id="main-content" tabIndex={-1} className="page-shell py-12">
        <div data-testid="admin-forbidden" role="alert" className="mx-auto max-w-md rounded-2xl border-2 border-red-800/35 bg-red-500/10 p-8 text-center text-sm font-medium text-red-900">
          Tài khoản này không có quyền nhập dữ liệu kho phim.
        </div>
      </main>
    );
  }

  return (
    <main id="main-content" tabIndex={-1} className="mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
      <AdminNav />
      <div className="mb-8 flex flex-wrap items-center justify-between gap-4 border-b border-surface-border pb-6">
        <div>
          <div className="flex items-center gap-2 text-xs font-semibold text-brand-dark">
            <Database size={14} />
            <span>Quản trị kho phim</span>
          </div>
          <h1 className="mt-1 text-2xl font-extrabold text-ink sm:text-3xl">
            Nhập dữ liệu kho phim
          </h1>
          <p className="mt-1 text-sm text-muted">
            Chọn tệp JSON và kiểm tra dữ liệu trước khi nhập.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={() => setRefreshCount((count) => count + 1)}
            className="btn-secondary flex items-center gap-1.5 text-xs font-medium text-muted"
          >
            <RefreshCw size={13} className={isLoading ? 'animate-spin' : ''} />
            <span>Làm mới</span>
          </button>
          <Link
            to="/search"
            className="btn-secondary flex items-center gap-1.5 text-xs font-medium text-muted"
          >
            <Film size={14} />
            <span>Xem kho phim</span>
          </Link>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-8 lg:grid-cols-3">
        <div className="min-w-0 lg:col-span-2 space-y-8">
          <CatalogImportModal onImportSuccess={handleImportSuccess} />

          <div className="rounded-xl border border-surface-border bg-surface p-6 shadow-sm">
            <div className="flex items-center justify-between border-b border-surface-border pb-3">
              <div className="flex items-center gap-2">
                <Film size={18} className="text-brand-dark" />
                <h2 className="text-base font-bold text-ink">
                  Phim bộ đã nhập ({seriesList.length})
                </h2>
              </div>
              <span className="text-xs text-muted">Xem chi tiết các mùa, tập và cảnh</span>
            </div>

            {isLoading ? (
              <div className="flex items-center justify-center py-10 text-xs text-muted">
                <RefreshCw size={16} className="animate-spin text-brand-dark mr-2" />
                <span>Đang tải danh sách phim bộ…</span>
              </div>
            ) : loadErrors.series ? (
              <LoadError label="danh sách phim bộ" onRetry={() => setRefreshCount((count) => count + 1)} />
            ) : seriesList.length === 0 ? (
              <div className="py-8 text-center text-xs text-muted">
                Chưa có phim bộ nào được nhập. Chọn tệp JSON để bắt đầu.
              </div>
            ) : (
              <div className="mt-4 divide-y divide-surface-border">
                {seriesList.map((series) => (
                  <div
                    key={series.id}
                    className="flex flex-wrap items-center justify-between gap-4 py-3.5 transition-colors hover:bg-surface-hover/50 rounded-lg px-2"
                  >
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <Link
                          to={`/series/${encodeURIComponent(series.id)}`}
                          className="text-sm font-bold text-ink hover:text-brand-dark transition-colors"
                        >
                          {series.title}
                        </Link>
                        {typeof series.releaseYearStart === 'number' && Number.isFinite(series.releaseYearStart) && series.releaseYearStart > 0 && (
                          <span className="rounded bg-surface-elevated px-1.5 py-0.5 text-[10px] text-muted border border-surface-border">
                            {Math.trunc(series.releaseYearStart)}
                          </span>
                        )}
                        <span className="max-w-full break-all font-mono text-[11px] text-muted">
                          [{series.id}]
                        </span>
                      </div>
                      {series.synopsis && (
                        <p className="mt-1 line-clamp-1 text-xs text-muted max-w-xl">
                          {series.synopsis}
                        </p>
                      )}
                    </div>

                    <Link
                      to={`/series/${encodeURIComponent(series.id)}`}
                      className="btn-secondary flex items-center gap-1.5 text-xs font-semibold shrink-0"
                    >
                      <span>Xem tập và cảnh</span>
                      <ArrowRight size={13} />
                    </Link>
                  </div>
                ))}
              </div>
            )}
          </div>

          {actionMessage && (
            <div
              className={`flex items-center justify-between gap-3 rounded-xl border p-4 text-xs ${
                actionMessage.type === 'success'
                  ? 'border-emerald-500/30 bg-emerald-500/10 text-emerald-900'
                  : 'border-red-500/30 bg-red-500/10 text-red-900'
              }`}
            >
              <div className="flex items-center gap-2">
                {actionMessage.type === 'success' ? (
                  <CheckCircle2 size={16} className="text-emerald-900 shrink-0" />
                ) : (
                  <XCircle size={16} className="text-red-900 shrink-0" />
                )}
                <span>{actionMessage.text}</span>
              </div>
              <button
                type="button"
                aria-label="Đóng thông báo"
                onClick={() => setActionMessage(null)}
                className="flex min-h-[44px] min-w-[44px] shrink-0 items-center justify-center rounded-lg text-muted hover:bg-surface-hover hover:text-ink cursor-pointer"
              >
                ✕
              </button>
            </div>
          )}

          <div className="rounded-xl border border-surface-border bg-surface p-6 shadow-sm">
            <div className="flex items-center justify-between border-b border-surface-border pb-3">
              <div className="flex items-center gap-2">
                <Activity size={18} className="text-brand-dark" />
                <h2 className="text-base font-bold text-ink">
                  Đồng bộ dữ liệu tìm kiếm ({snapshots.length})
                </h2>
              </div>
              <span className="text-xs text-muted">Trạng thái đồng bộ sau khi nhập</span>
            </div>

            {isLoading ? (
              <div className="flex items-center justify-center py-8 text-xs text-muted">
                <RefreshCw size={16} className="animate-spin text-brand-dark mr-2" />
                <span>Đang tải trạng thái đồng bộ…</span>
              </div>
            ) : loadErrors.snapshots ? (
              <LoadError label="trạng thái đồng bộ" onRetry={() => setRefreshCount((count) => count + 1)} />
            ) : snapshots.length === 0 ? (
              <div className="py-6 text-center text-xs text-muted">
                Chưa có bản dữ liệu nào. Trạng thái đồng bộ sẽ hiện ở đây sau khi bạn nhập.
              </div>
            ) : (
              <div className="mt-4 space-y-3">
                {snapshots.map((snapshot) => {
                  const isRetrying = retryingId === snapshot.id;
                  const canRetry = snapshot.status === 'SYNC_FAILED' || snapshot.status === 'BLOCKED' || snapshot.status === 'PENDING';
                  return (
                    <div
                      key={snapshot.id}
                      className="rounded-lg border border-surface-border bg-surface-elevated p-3.5 text-xs space-y-2"
                    >
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <div className="flex min-w-0 flex-wrap items-center gap-2">
                          <span className="font-mono font-bold text-brand-dark">
                            Bản #{snapshot.snapshotVersion}
                          </span>
                          <span className="max-w-full break-all font-mono text-muted">[{snapshot.id}]</span>
                          <span className="text-muted">({snapshot.totalItems} mục)</span>
                        </div>

                        <div className="flex flex-wrap items-center gap-2">
                          {snapshot.status === 'READY' && (
                            <span className="flex items-center gap-1 rounded bg-emerald-500/10 px-2 py-0.5 text-[11px] font-bold text-emerald-900 border border-emerald-500/30">
                              <CheckCircle2 size={12} /> Đã đồng bộ
                            </span>
                          )}
                          {(snapshot.status === 'SYNCING' || snapshot.status === 'BUILDING') && (
                            <span className="flex items-center gap-1 rounded bg-blue-500/10 px-2 py-0.5 text-[11px] font-bold text-blue-900 border border-blue-500/30 animate-pulse">
                              <RefreshCw size={12} className="animate-spin" /> Đang đồng bộ
                            </span>
                          )}
                          {snapshot.status === 'PENDING' && (
                            <span className="flex items-center gap-1 rounded bg-amber-500/10 px-2 py-0.5 text-[11px] font-bold text-amber-900 border border-amber-500/30">
                              <Clock size={12} /> Chờ xử lý
                            </span>
                          )}
                          {snapshot.status === 'BLOCKED' && (
                            <span className="flex items-center gap-1 rounded bg-orange-500/10 px-2 py-0.5 text-[11px] font-bold text-orange-900 border border-orange-500/30">
                              <AlertTriangle size={12} /> {snapshot.errorMessage?.startsWith('CACHE_REQUIRED')
                                ? 'Tạm dừng: thiếu dữ liệu chỉ mục' : 'Tạm dừng: thiếu mã truy cập'}
                            </span>
                          )}
                          {snapshot.status === 'SYNC_FAILED' && (
                            <span className="flex items-center gap-1 rounded bg-red-500/10 px-2 py-0.5 text-[11px] font-bold text-red-900 border border-red-500/30">
                              <XCircle size={12} /> Lỗi đồng bộ
                            </span>
                          )}

                          {canRetry && (
                            <button
                              type="button"
                              onClick={() => handleRetrySnapshot(snapshot.id)}
                              disabled={isRetrying}
                              className="btn-secondary flex items-center gap-1 text-[11px] py-1 px-2.5 font-semibold text-brand-dark hover:text-ink disabled:opacity-50"
                            >
                              <RotateCcw size={11} className={isRetrying ? 'animate-spin' : ''} />
                              <span>{isRetrying ? 'Đang gửi…' : 'Thử lại'}</span>
                            </button>
                          )}
                        </div>
                      </div>

                      {snapshot.errorMessage && (
                        <p className="whitespace-pre-wrap break-words rounded bg-red-500/10 p-2 font-mono text-[11px] text-red-900 border border-red-500/20">
                          {snapshot.errorMessage}
                        </p>
                      )}

                      <div className="flex flex-wrap items-center justify-between gap-2 text-[10px] text-muted font-mono">
                        <span title={snapshot.contentHash}>SHA-256: {snapshot.contentHash.slice(0, 16)}…</span>
                        {snapshot.retryCount !== undefined && snapshot.retryCount > 0 && (
                          <span>Số lần thử lại: {snapshot.retryCount}</span>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>

        <div className="min-w-0 space-y-6">
          <div className="rounded-xl border border-surface-border bg-surface p-5">
            <div className="flex items-center gap-2 border-b border-surface-border pb-3">
              <Clock size={16} className="text-brand-dark" />
              <h3 className="text-sm font-bold text-ink">Lịch sử nhập dữ liệu</h3>
            </div>

            {isLoading ? <p role="status" className="mt-3 text-xs text-muted">Đang tải lịch sử…</p> : loadErrors.history ? (
              <div data-testid="history-error"><LoadError label="lịch sử nhập" onRetry={() => setRefreshCount((count) => count + 1)} /></div>
            ) : history.length === 0 ? (
              <p className="mt-3 text-xs text-muted">Chưa có lịch sử nhập dữ liệu.</p>
            ) : (
              <div className="mt-3 space-y-3">
                {history.map((entry: any) => (
                  <div
                    key={entry.id}
                    className="rounded-lg border border-surface-border bg-surface-elevated p-3 text-xs"
                  >
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <strong className="max-w-full break-all text-ink font-mono">{entry.id}</strong>
                      <span className="rounded bg-brand/10 px-1.5 py-0.5 text-[10px] font-bold text-brand-dark border border-brand/20">
                        Phiên bản {entry.revision}
                      </span>
                    </div>
                    {entry.description && (
                      <p className="mt-1 text-[11px] text-muted line-clamp-2">
                        {entry.description}
                      </p>
                    )}
                    <div className="mt-2 flex items-center justify-between text-[10px] text-muted font-mono">
                      <span>{entry.defaultLanguage?.toUpperCase()}</span>
                      <span title={entry.contentHash}>
                        {entry.contentHash ? `SHA: ${entry.contentHash.slice(0, 10)}…` : ''}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </main>
  );
}

function LoadError({ label, onRetry }: { label: string; onRetry: () => void }) {
  return (
    <p role="alert" className="mt-3 flex flex-wrap items-center gap-2 text-xs text-red-900">
      <span>Chưa tải được {label}.</span>
      <button type="button" className="inline-flex min-h-[44px] items-center font-semibold underline cursor-pointer" onClick={onRetry}>
        Thử lại
      </button>
    </p>
  );
}
