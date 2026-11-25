import React, { useEffect, useState } from 'react';
import {
  Users,
  Search,
  RefreshCw,
  Shield,
  UserCheck,
  UserX,
  CheckCircle2,
  XCircle,
  ChevronLeft,
  ChevronRight,
  X,
} from 'lucide-react';
import { useAuth } from '@/lib/context/AuthContext';
import {
  listAdminUsers,
  updateAdminUserRole,
  updateAdminUserStatus,
  AdminPageResponse,
  AdminUserView,
  AdminUserRole,
} from '@/lib/services/adminService';
import {
  formatCount,
  formatViDate,
  formatViDateTime,
  getNextPage,
  getPagingSummary,
  getPreviousPage,
  getRoleLabel,
  getUserStatusLabel,
} from '@/features/admin/adminFormat';
import { AdminNav } from '@/features/admin/components/AdminNav';
import { AdminConfirmDialog } from '@/features/admin/components/AdminConfirmDialog';

const PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;
const SELF_ACTION_TOOLTIP = 'Không thể thay đổi tài khoản của chính bạn';

type PendingAction =
  | { type: 'role'; target: AdminUserView; nextRole: AdminUserRole }
  | { type: 'status'; target: AdminUserView; nextEnabled: boolean };

export default function AdminUsersPage() {
  const { status, user, token, openAuthModal, retryVerify } = useAuth();
  const isAdmin = status === 'authenticated' && user?.role === 'ROLE_ADMIN' && !!token;

  const [searchInput, setSearchInput] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [page, setPage] = useState(0);
  const [refreshNonce, setRefreshNonce] = useState(0);

  const [pageData, setPageData] = useState<AdminPageResponse<AdminUserView> | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [pendingAction, setPendingAction] = useState<PendingAction | null>(null);
  const [isMutating, setIsMutating] = useState(false);
  const [dialogError, setDialogError] = useState<string | null>(null);
  const [actionMessage, setActionMessage] = useState<{ text: string; type: 'success' | 'error' } | null>(null);

  useEffect(() => {
    const timer = setTimeout(() => {
      const nextQuery = searchInput.trim();
      setDebouncedQuery((prev) => {
        if (prev !== nextQuery) {
          setPage(0);
        }
        return nextQuery;
      });
    }, SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [searchInput]);

  useEffect(() => {
    if (!isAdmin || !token) return;
    const controller = new AbortController();
    setIsLoading(true);
    setLoadError(null);

    listAdminUsers(
      token,
      { query: debouncedQuery, page, size: PAGE_SIZE, signal: controller.signal }
    )
      .then((res) => {
        if (controller.signal.aborted) return;
        setPageData(res);
        setIsLoading(false);
      })
      .catch((err: any) => {
        if (controller.signal.aborted || err?.isCanceled) return;
        setLoadError(err?.message || 'Không thể tải danh sách người dùng.');
        setIsLoading(false);
      });

    return () => controller.abort();
  }, [isAdmin, token, debouncedQuery, page, refreshNonce]);

  const openRoleDialog = (target: AdminUserView) => {
    const nextRole: AdminUserRole = target.role === 'ROLE_ADMIN' ? 'ROLE_USER' : 'ROLE_ADMIN';
    setDialogError(null);
    setPendingAction({ type: 'role', target, nextRole });
  };

  const openStatusDialog = (target: AdminUserView) => {
    setDialogError(null);
    setPendingAction({ type: 'status', target, nextEnabled: !target.enabled });
  };

  const closeDialog = () => {
    if (isMutating) return;
    setPendingAction(null);
    setDialogError(null);
  };

  const handleConfirmAction = async () => {
    if (!pendingAction || !token) return;
    setIsMutating(true);
    setDialogError(null);
    setActionMessage(null);

    try {
      const updated = pendingAction.type === 'role'
        ? await updateAdminUserRole(token, pendingAction.target.id, pendingAction.nextRole)
        : await updateAdminUserStatus(token, pendingAction.target.id, pendingAction.nextEnabled);

      setPageData((prev) => {
        if (!prev) return prev;
        return {
          ...prev,
          items: prev.items.map((item) => (item.id === updated.id ? updated : item)),
        };
      });

      const successText = pendingAction.type === 'role'
        ? `Đã cập nhật vai trò của ${updated.username} thành ${getRoleLabel(updated.role)}.`
        : updated.enabled
          ? `Đã mở khóa tài khoản ${updated.username}.`
          : `Đã khóa tài khoản ${updated.username}.`;

      setActionMessage({ text: successText, type: 'success' });
      setPendingAction(null);
    } catch (err: any) {
      const msg = err?.message || 'Không thể thực hiện thay đổi tài khoản.';
      setDialogError(msg);
      setActionMessage({ text: msg, type: 'error' });
    } finally {
      setIsMutating(false);
    }
  };

  if (status === 'checking') return <div role="status" className="p-8 text-sm">Đang kiểm tra tài khoản…</div>;
  if (status === 'unavailable') return <div role="alert" className="p-8 text-sm">Không thể kiểm tra tài khoản. <button className="underline" onClick={() => retryVerify()}>Thử lại</button></div>;
  if (status === 'guest') return <div className="p-8 text-sm">Vui lòng đăng nhập để quản trị người dùng. <button data-testid="admin-sign-in" className="underline" onClick={() => openAuthModal('login')}>Đăng nhập</button></div>;
  if (!isAdmin) return <div data-testid="admin-forbidden" role="alert" className="p-8 text-sm">Tài khoản này không có quyền quản trị người dùng.</div>;

  const items = pageData?.items ?? [];
  const paging = pageData ? getPagingSummary(pageData) : null;

  const dialogTitle = pendingAction
    ? pendingAction.type === 'role'
      ? pendingAction.nextRole === 'ROLE_ADMIN'
        ? 'Cấp quyền quản trị viên?'
        : 'Hạ quyền về người dùng?'
      : pendingAction.nextEnabled
        ? 'Mở khóa tài khoản?'
        : 'Khóa tài khoản?'
    : '';

  const dialogDescription = pendingAction
    ? pendingAction.type === 'role'
      ? `Bạn có chắc muốn đổi vai trò của tài khoản "${pendingAction.target.username}" sang ${getRoleLabel(pendingAction.nextRole)}?`
      : pendingAction.nextEnabled
        ? `Tài khoản "${pendingAction.target.username}" sẽ được kích hoạt lại và có thể đăng nhập vào hệ thống.`
        : `Tài khoản "${pendingAction.target.username}" sẽ bị khóa và không thể tiếp tục truy cập hệ thống.`
    : '';

  const dialogConfirmLabel = pendingAction
    ? pendingAction.type === 'role'
      ? 'Xác nhận đổi vai trò'
      : pendingAction.nextEnabled
        ? 'Xác nhận mở khóa'
        : 'Xác nhận khóa'
    : 'Xác nhận';

  const dialogTone = pendingAction && (
    (pendingAction.type === 'status' && !pendingAction.nextEnabled) ||
    (pendingAction.type === 'role' && pendingAction.nextRole === 'ROLE_USER')
  )
    ? 'danger'
    : 'primary';

  return (
    <main className="mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
      <AdminNav />

      <div className="mb-8 flex flex-wrap items-center justify-between gap-4 border-b border-surface-border pb-6">
        <div>
          <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-brand-dark">
            <Users size={14} aria-hidden="true" />
            <span>Quản trị người dùng</span>
          </div>
          <h1 className="mt-1 text-2xl font-extrabold text-ink sm:text-3xl">
            Quản lý tài khoản
          </h1>
          <p className="mt-1 text-sm text-muted">
            Tìm kiếm người dùng, phân quyền quản trị và kiểm soát trạng thái hoạt động.
          </p>
        </div>

        <button
          type="button"
          onClick={() => setRefreshNonce((n) => n + 1)}
          disabled={isLoading}
          className="btn-secondary flex items-center gap-1.5 text-xs font-medium text-muted disabled:opacity-50"
        >
          <RefreshCw size={13} className={isLoading ? 'animate-spin' : ''} aria-hidden="true" />
          <span>Làm mới</span>
        </button>
      </div>

      <div aria-live="polite" className="mb-6">
        {actionMessage && (
          <div
            role={actionMessage.type === 'error' ? 'alert' : 'status'}
            data-testid="admin-users-action-message"
            className={`flex items-center justify-between gap-3 rounded-xl border p-4 text-xs ${
              actionMessage.type === 'success'
                ? 'border-emerald-500/30 bg-emerald-500/10 text-emerald-900'
                : 'border-red-500/30 bg-red-500/10 text-red-900'
            }`}
          >
            <div className="flex items-center gap-2">
              {actionMessage.type === 'success' ? (
                <CheckCircle2 size={16} className="shrink-0 text-emerald-900" aria-hidden="true" />
              ) : (
                <XCircle size={16} className="shrink-0 text-red-900" aria-hidden="true" />
              )}
              <span>{actionMessage.text}</span>
            </div>
            <button
              type="button"
              aria-label="Đóng thông báo"
              onClick={() => setActionMessage(null)}
              className="flex min-h-[36px] min-w-[36px] items-center justify-center rounded-lg text-muted hover:bg-surface-hover hover:text-ink"
            >
              ✕
            </button>
          </div>
        )}
      </div>

      <section className="rounded-xl border border-surface-border bg-surface p-6 shadow-sm">
        <div className="flex flex-wrap items-end justify-between gap-4 border-b border-surface-border pb-4">
          <div className="w-full max-w-md">
            <label htmlFor="admin-users-search" className="mb-1.5 block text-xs font-semibold text-ink">
              Tìm kiếm theo tên đăng nhập hoặc email
            </label>
            <div className="relative flex items-center rounded-lg border border-surface-border bg-surface-elevated px-3 focus-within:border-brand focus-within:bg-surface">
              <Search size={14} className="shrink-0 text-muted" aria-hidden="true" />
              <input
                id="admin-users-search"
                data-testid="admin-users-search"
                type="search"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                placeholder="Nhập tên đăng nhập hoặc email..."
                className="min-h-[44px] w-full bg-transparent px-2.5 text-xs text-ink placeholder:text-muted focus:outline-none"
              />
              {searchInput && (
                <button
                  type="button"
                  aria-label="Xóa từ khóa tìm kiếm"
                  onClick={() => setSearchInput('')}
                  className="flex min-h-[36px] min-w-[36px] items-center justify-center rounded text-muted hover:text-ink"
                >
                  <X size={14} aria-hidden="true" />
                </button>
              )}
            </div>
          </div>

          <div role="status" aria-live="polite" className="text-xs text-muted">
            {isLoading
              ? 'Đang tải danh sách người dùng…'
              : paging
                ? `Tổng cộng ${formatCount(paging.totalElements)} tài khoản`
                : ''}
          </div>
        </div>

        {isLoading ? (
          <div role="status" className="flex items-center justify-center py-12 text-xs text-muted">
            <RefreshCw size={16} className="mr-2 animate-spin text-brand-dark" aria-hidden="true" />
            <span>Đang tải danh sách người dùng...</span>
          </div>
        ) : loadError ? (
          <div role="alert" data-testid="admin-users-error" className="py-10 text-center">
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
          <div data-testid="admin-users-empty" className="py-10 text-center text-xs text-muted">
            {debouncedQuery
              ? `Không tìm thấy người dùng nào khớp với "${debouncedQuery}".`
              : 'Chưa có tài khoản người dùng nào.'}
          </div>
        ) : (
          <>
            <div className="mt-4 overflow-x-auto">
              <table className="w-full min-w-[760px] border-collapse text-left text-xs">
                <thead>
                  <tr className="border-b border-surface-border bg-surface-elevated/70 text-[11px] font-bold uppercase tracking-wide text-muted">
                    <th scope="col" className="px-3 py-3">Tên đăng nhập</th>
                    <th scope="col" className="px-3 py-3">Email</th>
                    <th scope="col" className="px-3 py-3">Họ và tên</th>
                    <th scope="col" className="px-3 py-3">Vai trò</th>
                    <th scope="col" className="px-3 py-3">Trạng thái</th>
                    <th scope="col" className="px-3 py-3">Ngày tạo</th>
                    <th scope="col" className="px-3 py-3 text-right">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-surface-border">
                  {items.map((item) => {
                    const isSelf = item.id === user?.id || item.username === user?.username;
                    const isTargetAdmin = item.role === 'ROLE_ADMIN';
                    return (
                      <tr
                        key={item.id}
                        data-testid={`admin-user-row-${item.id}`}
                        className="transition-colors hover:bg-surface-hover/50"
                      >
                        <td className="px-3 py-3.5 font-mono font-semibold text-ink">
                          <div className="flex flex-wrap items-center gap-1.5">
                            <span>{item.username}</span>
                            {isSelf && (
                              <span className="rounded border border-brand/30 bg-brand/15 px-1.5 py-0.5 font-sans text-[10px] font-bold text-brand-dark">
                                Bạn
                              </span>
                            )}
                          </div>
                        </td>
                        <td className="px-3 py-3.5 text-muted">{item.email}</td>
                        <td className="px-3 py-3.5 text-ink">{item.fullName || '—'}</td>
                        <td className="px-3 py-3.5">
                          <span
                            className={`inline-flex items-center gap-1 rounded border px-2 py-0.5 text-[11px] font-bold ${
                              isTargetAdmin
                                ? 'border-amber-500/30 bg-amber-500/10 text-amber-900'
                                : 'border-surface-border/40 bg-surface-elevated text-muted'
                            }`}
                          >
                            {isTargetAdmin && <Shield size={11} aria-hidden="true" />}
                            <span>{getRoleLabel(item.role)}</span>
                          </span>
                        </td>
                        <td className="px-3 py-3.5">
                          <span
                            className={`inline-flex items-center gap-1 rounded border px-2 py-0.5 text-[11px] font-bold ${
                              item.enabled
                                ? 'border-emerald-500/30 bg-emerald-500/10 text-emerald-900'
                                : 'border-red-500/30 bg-red-500/10 text-red-900'
                            }`}
                          >
                            {item.enabled ? (
                              <UserCheck size={11} aria-hidden="true" />
                            ) : (
                              <UserX size={11} aria-hidden="true" />
                            )}
                            <span>{getUserStatusLabel(item.enabled)}</span>
                          </span>
                        </td>
                        <td className="px-3 py-3.5 font-mono text-[11px] text-muted" title={formatViDateTime(item.createdAt)}>
                          {formatViDate(item.createdAt)}
                        </td>
                        <td className="px-3 py-3.5 text-right">
                          <div className="inline-flex flex-wrap items-center justify-end gap-2">
                            <span title={isSelf ? SELF_ACTION_TOOLTIP : undefined}>
                              <button
                                type="button"
                                data-testid={`toggle-role-${item.id}`}
                                disabled={isSelf}
                                title={isSelf ? SELF_ACTION_TOOLTIP : undefined}
                                aria-label={
                                  isSelf
                                    ? `${SELF_ACTION_TOOLTIP} (${item.username})`
                                    : isTargetAdmin
                                      ? `Hạ quyền quản trị của ${item.username}`
                                      : `Cấp quyền quản trị cho ${item.username}`
                                }
                                onClick={() => openRoleDialog(item)}
                                className="btn-secondary min-h-[38px] px-3 py-1.5 text-[11px] font-semibold disabled:cursor-not-allowed disabled:opacity-50"
                              >
                                {isTargetAdmin ? 'Hạ xuống người dùng' : 'Cấp quyền quản trị'}
                              </button>
                            </span>
                            <span title={isSelf ? SELF_ACTION_TOOLTIP : undefined}>
                              <button
                                type="button"
                                data-testid={`toggle-status-${item.id}`}
                                disabled={isSelf}
                                title={isSelf ? SELF_ACTION_TOOLTIP : undefined}
                                aria-label={
                                  isSelf
                                    ? `${SELF_ACTION_TOOLTIP} (${item.username})`
                                    : item.enabled
                                      ? `Khóa tài khoản ${item.username}`
                                      : `Mở khóa tài khoản ${item.username}`
                                }
                                onClick={() => openStatusDialog(item)}
                                className={`inline-flex min-h-[38px] items-center justify-center rounded border-2 border-ink px-3 py-1.5 text-[11px] font-semibold shadow-brand-sm transition-transform duration-150 hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:translate-y-0 ${
                                  item.enabled
                                    ? 'bg-red-500/15 text-red-900'
                                    : 'bg-emerald-500/15 text-emerald-900'
                                }`}
                              >
                                {item.enabled ? 'Khóa' : 'Mở khóa'}
                              </button>
                            </span>
                          </div>
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
                  <strong className="text-ink">{formatCount(paging.totalElements)}</strong> tài khoản · Trang{' '}
                  <strong className="text-ink">{paging.currentPage}</strong>/{Math.max(paging.totalPages, 1)}
                </span>

                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    data-testid="admin-users-prev"
                    disabled={!paging.canPrevious || isLoading}
                    onClick={() => pageData && setPage(getPreviousPage(pageData))}
                    className="btn-secondary min-h-[40px] px-3 py-1.5 text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    <ChevronLeft size={14} aria-hidden="true" />
                    <span>Trước</span>
                  </button>
                  <button
                    type="button"
                    data-testid="admin-users-next"
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

      <AdminConfirmDialog
        open={pendingAction !== null}
        title={dialogTitle}
        description={dialogDescription}
        confirmLabel={dialogConfirmLabel}
        tone={dialogTone}
        isSubmitting={isMutating}
        error={dialogError}
        onConfirm={handleConfirmAction}
        onCancel={closeDialog}
      />
    </main>
  );
}
