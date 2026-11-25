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
  const { status, user, token, openAuthModal, retryVerify } = useAuth();
  const isAdmin = status === 'authenticated' && user?.role === 'ROLE_ADMIN' && !!token;

  const [page, setPage] = useState(0);
  const [refreshNonce, setRefreshNonce] = useState(0);
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
        setLoadError(err?.message || 'Không thể tải nhật ký kiểm toán.');
        setIsLoading(false);
      });

    return () => controller.abort();
  }, [isAdmin, token, page, refreshNonce]);

  if (status === 'checking') return <div role="status" className="p-8 text-sm">Đang kiểm tra tài khoản…</div>;
  if (status === 'unavailable') return <div role="alert" className="p-8 text-sm">Không thể kiểm tra tài khoản. <button className="underline" onClick={() => retryVerify()}>Thử lại</button></div>;
  if (status === 'guest') return <div className="p-8 text-sm">Vui lòng đăng nhập để xem nhật ký kiểm toán. <button data-testid="admin-sign-in" className="underline" onClick={() => openAuthModal('login')}>Đăng nhập</button></div>;
  if (!isAdmin) return <div data-testid="admin-forbidden" role="alert" className="p-8 text-sm">Tài khoản này không có quyền xem nhật ký kiểm toán.</div>;

  const items = pageData?.items ?? [];
  const paging = pageData ? getPagingSummary(pageData) : null;

  return (
    <main className="mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
      <AdminNav />

      <div className="mb-8 flex flex-wrap items-center justify-between gap-4 border-b border-surface-border pb-6">
        <div>
          <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-brand-dark">
            <ClipboardList size={14} aria-hidden="true" />
            <span>Quản trị hệ thống</span>
          </div>
          <h1 className="mt-1 text-2xl font-extrabold text-ink sm:text-3xl">
            Nhật ký kiểm toán
          </h1>
          <p className="mt-1 text-sm text-muted">
            Theo dõi lịch sử các thay đổi quản trị mới nhất trên tài khoản và danh mục.
          </p>
        </div>

        <button
          type="button"
          data-testid="admin-audit-refresh"
          onClick={() => setRefreshNonce((n) => n + 1)}
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
              ? 'Đang tải nhật ký kiểm toán…'
              : paging
                ? `Tổng cộng ${formatCount(paging.totalElements)} sự kiện`
                : ''}
          </div>
        </div>

        {isLoading ? (
          <div role="status" className="flex items-center justify-center py-12 text-xs text-muted">
            <RefreshCw size={16} className="mr-2 animate-spin text-brand-dark" aria-hidden="true" />
            <span>Đang tải nhật ký kiểm toán...</span>
          </div>
        ) : loadError ? (
          <div role="alert" data-testid="admin-audit-error" className="py-10 text-center">
            <p className="text-xs text-red-900">{loadError}</p>
            <button
              type="button"
              onClick={() => setRefreshNonce((n) => n + 1)}
              className="btn-secondary mt-3 text-xs font-semibold"
            >
              Thử lại
            </button>
          </div>
        ) : items.length === 0 ? (
          <div data-testid="admin-audit-empty" className="py-10 text-center text-xs text-muted">
            Chưa có sự kiện kiểm toán nào được ghi nhận.
          </div>
        ) : (
          <>
            <div className="mt-4 overflow-x-auto">
              <table className="w-full min-w-[760px] border-collapse text-left text-xs">
                <thead>
                  <tr className="border-b border-surface-border bg-surface-elevated/70 text-[11px] font-bold uppercase tracking-wide text-muted">
                    <th scope="col" className="px-3 py-3">Thời gian</th>
                    <th scope="col" className="px-3 py-3">Người thực hiện</th>
                    <th scope="col" className="px-3 py-3">Hành động</th>
                    <th scope="col" className="px-3 py-3">Đối tượng</th>
                    <th scope="col" className="px-3 py-3">Chi tiết</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-surface-border">
                  {items.map((event) => {
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
                            <span className="text-muted">—</span>
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
                  Hiển thị <strong className="text-ink">{formatCount(paging.from)}–{formatCount(paging.to)}</strong> /{' '}
                  <strong className="text-ink">{formatCount(paging.totalElements)}</strong> sự kiện · Trang{' '}
                  <strong className="text-ink">{paging.currentPage}</strong>/{Math.max(paging.totalPages, 1)}
                </span>

                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    data-testid="admin-audit-prev"
                    disabled={!paging.canPrevious || isLoading}
                    onClick={() => pageData && setPage(getPreviousPage(pageData))}
                    className="btn-secondary min-h-[40px] px-3 py-1.5 text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    <ChevronLeft size={14} aria-hidden="true" />
                    <span>Trước</span>
                  </button>
                  <button
                    type="button"
                    data-testid="admin-audit-next"
                    disabled={!paging.canNext || isLoading}
                    onClick={() => pageData && setPage(getNextPage(pageData))}
                    className="btn-secondary min-h-[40px] px-3 py-1.5 text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-50"
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
