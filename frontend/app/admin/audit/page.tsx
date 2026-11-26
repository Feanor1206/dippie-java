import React, { useEffect, useState } from 'react';
import {
  ClipboardList,
  RefreshCw,
  ChevronLeft,
  ChevronRight,
} from 'lucide-react';
import { useAuth } from '@/lib/context/AuthContext';
import {
  listAdminAuditEvents,
  AdminAuditEventView,
  AdminPageResponse,
} from '@/lib/services/adminService';
import {
  formatAuditDetails,
  formatAuditTarget,
  formatCount,
  formatViDateTime,
  getAuditActionLabel,
  getNextPage,
  getPagingSummary,
  getPreviousPage,
} from '@/features/admin/adminFormat';
import { AdminNav } from '@/features/admin/components/AdminNav';

const PAGE_SIZE = 20;

function actionBadgeClass(action: string): string {
  if (action === 'USER_DISABLED') {
    return 'border-red-500/30 bg-red-500/10 text-red-900';
  }
  if (action === 'USER_ENABLED') {
    return 'border-emerald-500/30 bg-emerald-500/10 text-emerald-900';
  }
  if (action === 'USER_ROLE_CHANGED') {
    return 'border-amber-500/30 bg-amber-500/10 text-amber-900';
  }
  if (action === 'CATALOG_IMPORTED') {
    return 'border-brand/40 bg-brand/15 text-brand-dark';
  }
  return 'border-surface-border/40 bg-surface-elevated text-muted';
}

export default function AdminAuditPage() {
  const { status, user, token, isKidMode, openAuthModal, retryVerify } = useAuth();
  const isAdmin = status === 'authenticated' && user?.role === 'ROLE_ADMIN' && !isKidMode && !!token;

  const [page, setPage] = useState(0);
  const [refreshCount, setRefreshCount] = useState(0);
  const [pageData, setPageData] = useState<AdminPageResponse<AdminAuditEventView> | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    if (!isAdmin || !token) return;
    const controller = new AbortController();
    setIsLoading(true);
    setLoadError(null);

    listAdminAuditEvents(token, { page, size: PAGE_SIZE, signal: controller.signal })
      .then((res) => {
        if (controller.signal.aborted) return;
        setPageData(res);
        setIsLoading(false);
      })
      .catch((err: any) => {
        if (controller.signal.aborted || err?.isCanceled) return;
        setLoadError(err?.message || 'Chưa tải được nhật ký hệ thống.');
        setIsLoading(false);
      });

    return () => controller.abort();
  }, [isAdmin, token, page, refreshCount]);

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
          <p className="text-sm text-muted">Đăng nhập để xem nhật ký hệ thống.</p>
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
          Tài khoản này không có quyền xem nhật ký hệ thống.
        </div>
      </main>
    );
  }

  const events = pageData?.items ?? [];
  const paging = pageData ? getPagingSummary(pageData) : null;

  return (
    <main id="main-content" tabIndex={-1} className="mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
      <AdminNav />

      <div className="mb-8 flex flex-wrap items-center justify-between gap-4 border-b border-surface-border pb-6">
        <div>
          <div className="flex items-center gap-2 text-xs font-semibold text-brand-dark">
            <ClipboardList size={14} aria-hidden="true" />
            <span>Quản trị hệ thống</span>
          </div>
          <h1 className="mt-1 text-2xl font-extrabold text-ink sm:text-3xl">
            Nhật ký hệ thống
          </h1>
          <p className="mt-1 text-sm text-muted">
            Xem các thay đổi gần đây với tài khoản và kho phim.
          </p>
        </div>

        <button
          type="button"
          data-testid="admin-audit-refresh"
          onClick={() => setRefreshCount((count) => count + 1)}
          disabled={isLoading}
          className="btn-secondary flex items-center gap-1.5 text-xs font-medium text-muted disabled:opacity-50"
        >
          <RefreshCw size={13} className={isLoading ? 'animate-spin' : ''} aria-hidden="true" />
          <span>Làm mới</span>
        </button>
      </div>

      <section className="rounded-xl border border-surface-border bg-surface p-6 shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-surface-border pb-3">
          <div className="flex items-center gap-2">
            <ClipboardList size={18} className="text-brand-dark" aria-hidden="true" />
            <h2 className="text-base font-bold text-ink">Sự kiện gần đây</h2>
          </div>
          <div role="status" aria-live="polite" className="text-xs text-muted">
            {isLoading
              ? 'Đang tải nhật ký hệ thống…'
              : paging
                ? `Tổng cộng ${formatCount(paging.totalElements)} sự kiện`
                : ''}
          </div>
        </div>

        {isLoading ? (
          <div role="status" className="flex items-center justify-center py-12 text-xs text-muted">
            <RefreshCw size={16} className="mr-2 animate-spin text-brand-dark" aria-hidden="true" />
            <span>Đang tải nhật ký hệ thống…</span>
          </div>
        ) : loadError ? (
          <div role="alert" data-testid="admin-audit-error" className="py-10 text-center">
            <p className="text-xs text-red-900">{loadError}</p>
            <button
              type="button"
              onClick={() => setRefreshCount((count) => count + 1)}
              className="btn-secondary mt-3 text-xs font-semibold"
            >
              Thử lại
            </button>
          </div>
        ) : events.length === 0 ? (
          <div data-testid="admin-audit-empty" className="py-10 text-center text-xs text-muted">
            Chưa có sự kiện nào được ghi lại.
          </div>
        ) : (
          <>
            <div className="mt-4 overflow-x-auto">
              <table className="w-full min-w-[760px] border-collapse text-left text-xs">
                <thead>
                  <tr className="border-b border-surface-border bg-surface-elevated/70 text-[11px] font-bold text-muted">
                    <th scope="col" className="px-3 py-3">Thời gian</th>
                    <th scope="col" className="px-3 py-3">Người thực hiện</th>
                    <th scope="col" className="px-3 py-3">Hành động</th>
                    <th scope="col" className="px-3 py-3">Đối tượng</th>
                    <th scope="col" className="px-3 py-3">Chi tiết</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-surface-border">
                  {events.map((event) => {
                    const detailEntries = formatAuditDetails(event.details);
                    return (
                      <tr key={event.id} className="align-top transition-colors hover:bg-surface-hover/50">
                        <td className="whitespace-nowrap px-3 py-3.5 font-mono text-[11px] text-muted">
                          {formatViDateTime(event.createdAt, { includeSeconds: true })}
                        </td>
                        <td className="px-3 py-3.5 font-mono font-semibold text-ink">
                          {event.actorUsername}
                        </td>
                        <td className="px-3 py-3.5">
                          <span
                            className={`inline-flex items-center rounded border px-2 py-0.5 text-[11px] font-bold ${actionBadgeClass(event.action)}`}
                          >
                            {getAuditActionLabel(event.action)}
                          </span>
                        </td>
                        <td className="px-3 py-3.5 font-mono text-[11px] text-ink">
                          {formatAuditTarget(event.targetType, event.targetId)}
                        </td>
                        <td className="px-3 py-3.5">
                          {detailEntries.length === 0 ? (
                            <span className="text-muted">Không có</span>
                          ) : (
                            <div className="flex flex-wrap gap-1.5">
                              {detailEntries.map(({ key, value }) => (
                                <span
                                  key={key}
                                  className="inline-flex items-center gap-1 rounded border border-surface-border/40 bg-surface-elevated px-2 py-0.5 font-mono text-[11px] text-ink"
                                >
                                  <span className="text-muted">{key}:</span>
                                  <span className="break-all font-semibold">{value}</span>
                                </span>
                              ))}
                            </div>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {paging && (
              <div className="mt-5 flex flex-wrap items-center justify-between gap-3 border-t border-surface-border pt-4 text-xs text-muted">
                <span>
                  Hiển thị <strong className="text-ink">{formatCount(paging.from)} đến {formatCount(paging.to)}</strong> /{' '}
                  <strong className="text-ink">{formatCount(paging.totalElements)}</strong> sự kiện · Trang{' '}
                  <strong className="text-ink">{paging.currentPage}</strong>/{Math.max(paging.totalPages, 1)}
                </span>

                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    data-testid="admin-audit-prev"
                    disabled={!paging.canPrevious || isLoading}
                    onClick={() => pageData && setPage(getPreviousPage(pageData))}
                    className="btn-secondary min-h-[44px] px-3 py-1.5 text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    <ChevronLeft size={14} aria-hidden="true" />
                    <span>Trước</span>
                  </button>
                  <button
                    type="button"
                    data-testid="admin-audit-next"
                    disabled={!paging.canNext || isLoading}
                    onClick={() => pageData && setPage(getNextPage(pageData))}
                    className="btn-secondary min-h-[44px] px-3 py-1.5 text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    <span>Sau</span>
                    <ChevronRight size={14} aria-hidden="true" />
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </section>
    </main>
  );
}
