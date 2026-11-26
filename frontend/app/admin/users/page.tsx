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
  const { status, user, token, isKidMode, openAuthModal, retryVerify } = useAuth();
  const isAdmin = status === 'authenticated' && user?.role === 'ROLE_ADMIN' && !isKidMode && !!token;

  const [searchInput, setSearchInput] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [page, setPage] = useState(0);
  const [refreshCount, setRefreshCount] = useState(0);

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
        setLoadError(err?.message || 'Chưa tải được danh sách người dùng.');
        setIsLoading(false);
      });

    return () => controller.abort();
  }, [isAdmin, token, debouncedQuery, page, refreshCount]);

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
          items: prev.items.map((account) => (account.id === updated.id ? updated : account)),
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
      const message = err?.message || 'Chưa lưu được thay đổi tài khoản.';
      setDialogError(message);
      setActionMessage({ text: message, type: 'error' });
    } finally {
      setIsMutating(false);
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
          <p className="text-sm text-muted">Đăng nhập để quản lý người dùng.</p>
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
          Tài khoản này không có quyền quản lý người dùng.
        </div>
      </main>
    );
  }

  const users = pageData?.items ?? [];
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
      ? `Bạn có chắc muốn đổi vai trò của tài khoản "${pendingAction.target.username}" thành ${getRoleLabel(pendingAction.nextRole)}?`
      : pendingAction.nextEnabled
        ? `Tài khoản "${pendingAction.target.username}" sẽ hoạt động lại và có thể đăng nhập vào Dippie.`
        : `Tài khoản "${pendingAction.target.username}" sẽ bị khóa và không thể đăng nhập vào Dippie.`
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
    <main id="main-content" tabIndex={-1} className="mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
      <AdminNav />

      <div className="mb-8 flex flex-wrap items-center justify-between gap-4 border-b border-surface-border pb-6">
        <div>
          <div className="flex items-center gap-2 text-xs font-semibold text-brand-dark">
            <Users size={14} aria-hidden="true" />
            <span>Quản trị người dùng</span>
          </div>
          <h1 className="mt-1 text-2xl font-extrabold text-ink sm:text-3xl">
            Quản lý tài khoản
          </h1>
          <p className="mt-1 text-sm text-muted">
            Tìm tài khoản, đổi quyền quản trị và khóa hoặc mở khóa người dùng.
          </p>
        </div>

        <button
          type="button"
          onClick={() => setRefreshCount((count) => count + 1)}
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
              className="flex min-h-[44px] min-w-[44px] items-center justify-center rounded-lg text-muted hover:bg-surface-hover hover:text-ink cursor-pointer"
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
              Tìm theo tên đăng nhập hoặc email
            </label>
            <div className="relative flex items-center rounded-lg border border-surface-border bg-surface-elevated px-3 focus-within:border-brand focus-within:bg-surface">
              <Search size={14} className="shrink-0 text-muted" aria-hidden="true" />
              <input
                id="admin-users-search"
                data-testid="admin-users-search"
                type="search"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                placeholder="Nhập tên đăng nhập hoặc email…"
                className="min-h-[44px] w-full bg-transparent px-2.5 text-base sm:text-xs text-ink placeholder:text-muted focus:outline-none"
              />
              {searchInput && (
                <button
                  type="button"
                  aria-label="Xóa từ khóa tìm kiếm"
                  onClick={() => setSearchInput('')}
                  className="flex min-h-[44px] min-w-[44px] items-center justify-center rounded text-muted hover:text-ink cursor-pointer"
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
            <span>Đang tải danh sách người dùng…</span>
          </div>
        ) : loadError ? (
          <div role="alert" data-testid="admin-users-error" className="py-10 text-center">
            <p className="text-xs text-red-900">{loadError}</p>
            <button
              type="button"
              onClick={() => setRefreshCount((count) => count + 1)}
              className="btn-secondary mt-3 text-xs font-semibold"
            >
              Thử lại
            </button>
          </div>
        ) : users.length === 0 ? (
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
                  <tr className="border-b border-surface-border bg-surface-elevated/70 text-[0.6875rem] font-bold text-muted">
                    <th scope="col" className="px-3 py-3">Tên đăng nhập</th>
                    <th scope="col" className="px-3 py-3">Email</th>
                    <th scope="col" className="px-3 py-3">Họ và tên</th>
                    <th scope="col" className="px-3 py-3">Vai trò</th>
                    <th scope="col" className="px-3 py-3">Trạng thái</th>
                    <th scope="col" className="px-3 py-3">Ngày tạo</th>
                    <th scope="col" className="px-3 py-3 text-right">Tùy chỉnh</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-surface-border">
                  {users.map((account) => {
                    const isSelf = account.id === user?.id || account.username === user?.username;
                    const isTargetAdmin = account.role === 'ROLE_ADMIN';
                    return (
                      <tr
                        key={account.id}
                        data-testid={`admin-user-row-${account.id}`}
                        className="transition-colors hover:bg-surface-hover/50"
                      >
                        <td className="px-3 py-3.5 font-mono font-semibold text-ink">
                          <div className="flex flex-wrap items-center gap-1.5">
                            <span>{account.username}</span>
                            {isSelf && (
                              <span className="rounded border border-brand/30 bg-brand/15 px-1.5 py-0.5 font-sans text-[0.625rem] font-bold text-brand-dark">
                                Bạn
                              </span>
                            )}
                          </div>
                        </td>
                        <td className="px-3 py-3.5 text-muted">{account.email}</td>
                        <td className="px-3 py-3.5 text-ink">{account.fullName || 'Chưa có'}</td>
                        <td className="px-3 py-3.5">
                          <span
                            className={`inline-flex items-center gap-1 rounded border px-2 py-0.5 text-[0.6875rem] font-bold ${
                              isTargetAdmin
                                ? 'border-amber-500/30 bg-amber-500/10 text-amber-900'
                                : 'border-surface-border/40 bg-surface-elevated text-muted'
                            }`}
                          >
                            {isTargetAdmin && <Shield size={11} aria-hidden="true" />}
                            <span>{getRoleLabel(account.role)}</span>
                          </span>
                        </td>
                        <td className="px-3 py-3.5">
                          <span
                            className={`inline-flex items-center gap-1 rounded border px-2 py-0.5 text-[0.6875rem] font-bold ${
                              account.enabled
                                ? 'border-emerald-500/30 bg-emerald-500/10 text-emerald-900'
                                : 'border-red-500/30 bg-red-500/10 text-red-900'
                            }`}
                          >
                            {account.enabled ? (
                              <UserCheck size={11} aria-hidden="true" />
                            ) : (
                              <UserX size={11} aria-hidden="true" />
                            )}
                            <span>{getUserStatusLabel(account.enabled)}</span>
                          </span>
                        </td>
                        <td className="px-3 py-3.5 font-mono text-[0.6875rem] text-muted" title={formatViDateTime(account.createdAt)}>
                          {formatViDate(account.createdAt)}
                        </td>
                        <td className="px-3 py-3.5 text-right">
                          <div className="inline-flex flex-wrap items-center justify-end gap-2">
                            <span title={isSelf ? SELF_ACTION_TOOLTIP : undefined}>
                              <button
                                type="button"
                                data-testid={`toggle-role-${account.id}`}
                                disabled={isSelf}
                                title={isSelf ? SELF_ACTION_TOOLTIP : undefined}
                                aria-label={
                                  isSelf
                                    ? `${SELF_ACTION_TOOLTIP} (${account.username})`
                                    : isTargetAdmin
                                      ? `Hạ quyền quản trị của ${account.username}`
                                      : `Cấp quyền quản trị cho ${account.username}`
                                }
                                onClick={() => openRoleDialog(account)}
                                className="btn-secondary min-h-[44px] px-3 py-1.5 text-[0.6875rem] font-semibold disabled:cursor-not-allowed disabled:opacity-50"
                              >
                                {isTargetAdmin ? 'Hạ xuống người dùng' : 'Cấp quyền quản trị'}
                              </button>
                            </span>
                            <span title={isSelf ? SELF_ACTION_TOOLTIP : undefined}>
                              <button
                                type="button"
                                data-testid={`toggle-status-${account.id}`}
                                disabled={isSelf}
                                title={isSelf ? SELF_ACTION_TOOLTIP : undefined}
                                aria-label={
                                  isSelf
                                    ? `${SELF_ACTION_TOOLTIP} (${account.username})`
                                    : account.enabled
                                      ? `Khóa tài khoản ${account.username}`
                                      : `Mở khóa tài khoản ${account.username}`
                                }
                                onClick={() => openStatusDialog(account)}
                                className={`inline-flex min-h-[44px] items-center justify-center rounded border-2 border-ink px-3 py-1.5 text-[0.6875rem] font-semibold shadow-brand-sm transition-transform duration-150 hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:translate-y-0 cursor-pointer ${
                                  account.enabled
                                    ? 'bg-red-500/15 text-red-900'
                                    : 'bg-emerald-500/15 text-emerald-900'
                                }`}
                              >
                                {account.enabled ? 'Khóa' : 'Mở khóa'}
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
                  Hiển thị <strong className="text-ink">{formatCount(paging.from)} đến {formatCount(paging.to)}</strong> /{' '}
                  <strong className="text-ink">{formatCount(paging.totalElements)}</strong> tài khoản · Trang{' '}
                  <strong className="text-ink">{paging.currentPage}</strong>/{Math.max(paging.totalPages, 1)}
                </span>

                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    data-testid="admin-users-prev"
                    disabled={!paging.canPrevious || isLoading}
                    onClick={() => pageData && setPage(getPreviousPage(pageData))}
                    className="btn-secondary min-h-[44px] px-3 py-1.5 text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    <ChevronLeft size={14} aria-hidden="true" />
                    <span>Trước</span>
                  </button>
                  <button
                    type="button"
                    data-testid="admin-users-next"
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
