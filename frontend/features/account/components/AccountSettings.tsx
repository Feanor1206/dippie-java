import React, { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { AlertCircle, CheckCircle2, KeyRound, Loader2, RefreshCw, User } from 'lucide-react';
import { useAuth } from '@/lib/context/AuthContext';
import {
  AccountApiError,
  changeAccountPassword,
  updateAccountProfile,
  validatePasswordChangeInput,
  validateProfileInput,
} from '@/lib/services/accountService';

type FormFeedback = {
  type: 'success' | 'error';
  text: string;
} | null;

function getInitials(name?: string) {
  if (!name) return 'U';
  const parts = name.trim().split(/\s+/);
  if (parts.length >= 2) return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
  return name.slice(0, 2).toUpperCase();
}

export function AccountSettings() {
  const {
    user,
    token,
    status,
    isAuthenticated,
    isLoading,
    openAuthModal,
    retryVerify,
    applySession,
    updateUser,
    handleSessionExpired,
  } = useAuth();

  const [fullName, setFullName] = useState(user?.fullName || '');
  const [email, setEmail] = useState(user?.email || '');
  const [profileSubmitting, setProfileSubmitting] = useState(false);
  const [profileFeedback, setProfileFeedback] = useState<FormFeedback>(null);

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordSubmitting, setPasswordSubmitting] = useState(false);
  const [passwordFeedback, setPasswordFeedback] = useState<FormFeedback>(null);

  const profileAbortRef = useRef<AbortController | null>(null);
  const passwordAbortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    setFullName(user?.fullName || '');
    setEmail(user?.email || '');
  }, [user?.id, user?.fullName, user?.email]);

  useEffect(() => {
    return () => {
      profileAbortRef.current?.abort();
      passwordAbortRef.current?.abort();
    };
  }, []);

  const handleProfileSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (profileSubmitting || !token) return;

    setProfileFeedback(null);
    const validationError = validateProfileInput({ fullName, email });
    if (validationError) {
      setProfileFeedback({ type: 'error', text: validationError });
      return;
    }

    profileAbortRef.current?.abort();
    const controller = new AbortController();
    profileAbortRef.current = controller;
    setProfileSubmitting(true);

    try {
      const result = await updateAccountProfile(
        token,
        { fullName: fullName.trim() || null, email: email.trim() },
        { signal: controller.signal }
      );
      if (controller.signal.aborted) return;
      updateUser(result.user);
      setFullName(result.user.fullName || '');
      setEmail(result.user.email);
      setProfileFeedback({ type: 'success', text: result.message });
    } catch (err: unknown) {
      if (err instanceof AccountApiError && err.isCanceled) return;
      if (err instanceof AccountApiError && err.status === 401) {
        handleSessionExpired();
        return;
      }
      const message =
        err instanceof Error && err.message
          ? err.message
          : 'Không thể cập nhật thông tin tài khoản. Vui lòng thử lại.';
      setProfileFeedback({ type: 'error', text: message });
    } finally {
      if (profileAbortRef.current === controller) {
        profileAbortRef.current = null;
        setProfileSubmitting(false);
      }
    }
  };

  const handlePasswordSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (passwordSubmitting || !token) return;

    setPasswordFeedback(null);
    const validationError = validatePasswordChangeInput({
      currentPassword,
      newPassword,
      confirmPassword,
    });
    if (validationError) {
      setPasswordFeedback({ type: 'error', text: validationError });
      return;
    }

    passwordAbortRef.current?.abort();
    const controller = new AbortController();
    passwordAbortRef.current = controller;
    setPasswordSubmitting(true);

    try {
      const result = await changeAccountPassword(
        token,
        { currentPassword, newPassword },
        { signal: controller.signal }
      );
      if (controller.signal.aborted) return;
      applySession(result.token, result.user);
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      const baseMessage = result.message.replace(/\.+$/, '');
      setPasswordFeedback({
        type: 'success',
        text: `${baseMessage}. Các thiết bị khác đã được đăng xuất.`,
      });
    } catch (err: unknown) {
      if (err instanceof AccountApiError && err.isCanceled) return;
      if (err instanceof AccountApiError && err.status === 401) {
        handleSessionExpired();
        return;
      }
      const message =
        err instanceof Error && err.message
          ? err.message
          : 'Không thể đổi mật khẩu. Vui lòng thử lại.';
      setPasswordFeedback({ type: 'error', text: message });
    } finally {
      if (passwordAbortRef.current === controller) {
        passwordAbortRef.current = null;
        setPasswordSubmitting(false);
      }
    }
  };

  return (
    <main className="page-shell min-h-[75vh] py-8 sm:py-12">
      {status === 'unavailable' && (
        <div
          role="alert"
          className="mb-6 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 rounded-xl border border-amber-500/30 bg-amber-500/10 p-4 text-amber-900"
        >
          <div className="flex items-center gap-3">
            <AlertCircle size={20} className="shrink-0 text-amber-900" aria-hidden="true" />
            <div className="text-xs sm:text-sm">
              <span className="font-bold">Không thể kết nối đến máy chủ xác thực:</span> Vui lòng thử kết nối lại để quản lý tài khoản.
            </div>
          </div>
          <button
            type="button"
            onClick={() => retryVerify()}
            className="flex min-h-[44px] items-center gap-1.5 rounded-md bg-amber-500/20 px-3 py-1.5 text-xs font-semibold text-amber-900 hover:bg-amber-500/30 transition-colors cursor-pointer"
          >
            <RefreshCw size={13} aria-hidden="true" /> Thử kết nối lại
          </button>
        </div>
      )}

      {isLoading ? (
        <div
          role="status"
          aria-live="polite"
          className="flex flex-col items-center justify-center rounded-xl border border-surface-border bg-surface py-20 text-muted"
        >
          <Loader2 className="animate-spin text-brand-dark mb-3" size={28} aria-hidden="true" />
          <p className="text-xs sm:text-sm">Đang kiểm tra phiên đăng nhập…</p>
        </div>
      ) : !isAuthenticated || !user || !token ? (
        <section
          aria-labelledby="account-guest-heading"
          className="mx-auto max-w-2xl rounded-xl border border-surface-border bg-surface p-6 sm:p-8"
        >
          <div className="flex items-center gap-4">
            <div className="grid h-12 w-12 shrink-0 place-items-center rounded-md bg-brand text-base font-bold text-zinc-950 shadow-brand-sm">
              <User size={22} aria-hidden="true" />
            </div>
            <div>
              <span className="text-xs font-bold uppercase tracking-wider text-brand-dark">
                Chế độ khách
              </span>
              <h1 id="account-guest-heading" className="mt-0.5 text-xl sm:text-2xl font-bold text-ink">
                Cài đặt tài khoản
              </h1>
            </div>
          </div>

          <p className="mt-5 text-sm text-muted leading-relaxed">
            Đăng nhập để cập nhật họ tên, email và đổi mật khẩu tài khoản Dippie của bạn.
          </p>

          <div className="mt-6 flex flex-wrap items-center gap-3">
            <button
              type="button"
              data-testid="account-guest-login-button"
              onClick={() => openAuthModal('login')}
              className="btn-primary text-xs sm:text-sm"
            >
              Đăng nhập
            </button>
            <button
              type="button"
              onClick={() => openAuthModal('register')}
              className="btn-secondary text-xs sm:text-sm"
            >
              Tạo tài khoản
            </button>
          </div>
        </section>
      ) : (
        <div className="mx-auto max-w-4xl space-y-8">
          <div className="rounded-xl border border-surface-border bg-surface p-6 sm:p-8">
            <div className="flex flex-col gap-6 sm:flex-row sm:items-center sm:justify-between">
              <div className="flex items-center gap-4">
                <div className="grid h-12 w-12 shrink-0 place-items-center rounded-md bg-brand text-base font-bold text-zinc-950 shadow-brand-sm">
                  {getInitials(user.fullName || user.username)}
                </div>
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-xs font-bold uppercase tracking-wider text-brand-dark">
                      Thành viên Dippie
                    </span>
                    <span className="text-muted" aria-hidden="true">•</span>
                    <span className="text-xs text-muted truncate">@{user.username}</span>
                  </div>
                  <h1 className="mt-0.5 text-xl sm:text-2xl font-bold text-ink">
                    Cài đặt tài khoản
                  </h1>
                </div>
              </div>

              <div className="flex items-center gap-3">
                <Link to="/profile" className="btn-secondary text-xs w-full sm:w-auto">
                  Hồ sơ cá nhân
                </Link>
              </div>
            </div>
          </div>

          <div className="grid grid-cols-1 gap-8 lg:grid-cols-2">
            <section
              aria-labelledby="account-profile-heading"
              className="rounded-xl border border-surface-border bg-surface p-6 sm:p-8"
            >
              <div className="flex items-center gap-2 border-b border-surface-border pb-4">
                <User size={18} className="text-brand-dark shrink-0" aria-hidden="true" />
                <h2 id="account-profile-heading" className="text-lg font-bold text-ink">
                  Thông tin tài khoản
                </h2>
              </div>

              <div aria-live="polite" className="mt-4">
                {profileFeedback && (
                  <div
                    role={profileFeedback.type === 'error' ? 'alert' : 'status'}
                    data-testid="account-profile-feedback"
                    className={`flex items-start gap-2 rounded-xl border p-3 text-xs leading-relaxed ${
                      profileFeedback.type === 'error'
                        ? 'border-red-500/40 bg-red-500/10 text-red-800'
                        : 'border-brand/50 bg-brand/15 text-ink'
                    }`}
                  >
                    {profileFeedback.type === 'error' ? (
                      <AlertCircle size={15} className="mt-0.5 shrink-0 text-red-800" aria-hidden="true" />
                    ) : (
                      <CheckCircle2 size={15} className="mt-0.5 shrink-0 text-brand-dark" aria-hidden="true" />
                    )}
                    <span>{profileFeedback.text}</span>
                  </div>
                )}
              </div>

              <form onSubmit={handleProfileSubmit} className="mt-5 space-y-4">
                <div>
                  <span className="block text-xs font-medium text-muted mb-1.5">
                    Tên đăng nhập
                  </span>
                  <div className="rounded-lg border border-surface-border/50 bg-surface-elevated/60 px-3 py-2.5 text-sm text-muted">
                    {user.username}
                  </div>
                </div>

                <div>
                  <label htmlFor="account-full-name" className="block text-xs font-medium text-muted mb-1.5">
                    Họ và tên
                  </label>
                  <div className="relative flex items-center rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 focus-within:border-brand focus-within:ring-1 focus-within:ring-brand">
                    <input
                      id="account-full-name"
                      type="text"
                      maxLength={100}
                      disabled={profileSubmitting}
                      value={fullName}
                      onChange={(event) => setFullName(event.target.value)}
                      placeholder="Nguyễn Văn A"
                      autoComplete="name"
                      className="w-full bg-transparent text-base sm:text-sm text-ink placeholder:text-muted focus:outline-none focus-visible:shadow-none disabled:opacity-50"
                    />
                  </div>
                </div>

                <div>
                  <label htmlFor="account-email" className="block text-xs font-medium text-muted mb-1.5">
                    Email
                  </label>
                  <div className="relative flex items-center rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 focus-within:border-brand focus-within:ring-1 focus-within:ring-brand">
                    <input
                      id="account-email"
                      type="email"
                      required
                      maxLength={100}
                      disabled={profileSubmitting}
                      value={email}
                      onChange={(event) => setEmail(event.target.value)}
                      placeholder="email@vidu.com"
                      autoComplete="email"
                      className="w-full bg-transparent text-base sm:text-sm text-ink placeholder:text-muted focus:outline-none focus-visible:shadow-none disabled:opacity-50"
                    />
                  </div>
                </div>

                <button
                  type="submit"
                  data-testid="account-profile-submit"
                  disabled={profileSubmitting}
                  className="btn-primary w-full justify-center py-2.5 text-xs sm:text-sm font-bold shadow-brand-sm"
                >
                  {profileSubmitting ? 'Đang lưu…' : 'Lưu thay đổi'}
                </button>
              </form>
            </section>

            <section
              aria-labelledby="account-password-heading"
              className="rounded-xl border border-surface-border bg-surface p-6 sm:p-8"
            >
              <div className="flex items-center gap-2 border-b border-surface-border pb-4">
                <KeyRound size={18} className="text-brand-dark shrink-0" aria-hidden="true" />
                <h2 id="account-password-heading" className="text-lg font-bold text-ink">
                  Đổi mật khẩu
                </h2>
              </div>

              <p className="mt-4 text-xs text-muted leading-relaxed">
                Sau khi đổi mật khẩu, phiên hiện tại vẫn được giữ nguyên và mọi thiết bị khác sẽ tự động đăng xuất.
              </p>

              <div aria-live="polite" className="mt-4">
                {passwordFeedback && (
                  <div
                    role={passwordFeedback.type === 'error' ? 'alert' : 'status'}
                    data-testid="account-password-feedback"
                    className={`flex items-start gap-2 rounded-xl border p-3 text-xs leading-relaxed ${
                      passwordFeedback.type === 'error'
                        ? 'border-red-500/40 bg-red-500/10 text-red-800'
                        : 'border-brand/50 bg-brand/15 text-ink'
                    }`}
                  >
                    {passwordFeedback.type === 'error' ? (
                      <AlertCircle size={15} className="mt-0.5 shrink-0 text-red-800" aria-hidden="true" />
                    ) : (
                      <CheckCircle2 size={15} className="mt-0.5 shrink-0 text-brand-dark" aria-hidden="true" />
                    )}
                    <span>{passwordFeedback.text}</span>
                  </div>
                )}
              </div>

              <form onSubmit={handlePasswordSubmit} className="mt-5 space-y-4">
                <div>
                  <label htmlFor="account-current-password" className="block text-xs font-medium text-muted mb-1.5">
                    Mật khẩu hiện tại
                  </label>
                  <div className="relative flex items-center rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 focus-within:border-brand focus-within:ring-1 focus-within:ring-brand">
                    <input
                      id="account-current-password"
                      type="password"
                      required
                      disabled={passwordSubmitting}
                      value={currentPassword}
                      onChange={(event) => setCurrentPassword(event.target.value)}
                      placeholder="Nhập mật khẩu hiện tại"
                      autoComplete="current-password"
                      className="w-full bg-transparent text-base sm:text-sm text-ink placeholder:text-muted focus:outline-none focus-visible:shadow-none disabled:opacity-50"
                    />
                  </div>
                </div>

                <div>
                  <label htmlFor="account-new-password" className="block text-xs font-medium text-muted mb-1.5">
                    Mật khẩu mới
                  </label>
                  <div className="relative flex items-center rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 focus-within:border-brand focus-within:ring-1 focus-within:ring-brand">
                    <input
                      id="account-new-password"
                      type="password"
                      required
                      disabled={passwordSubmitting}
                      value={newPassword}
                      onChange={(event) => setNewPassword(event.target.value)}
                      placeholder="Tối thiểu 6 ký tự"
                      autoComplete="new-password"
                      aria-describedby="account-password-hint"
                      className="w-full bg-transparent text-base sm:text-sm text-ink placeholder:text-muted focus:outline-none focus-visible:shadow-none disabled:opacity-50"
                    />
                  </div>
                  <p id="account-password-hint" className="mt-1 text-[11px] text-muted">
                    Tối thiểu 6 ký tự, tối đa 72 byte UTF-8.
                  </p>
                </div>

                <div>
                  <label htmlFor="account-confirm-password" className="block text-xs font-medium text-muted mb-1.5">
                    Xác nhận mật khẩu mới
                  </label>
                  <div className="relative flex items-center rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 focus-within:border-brand focus-within:ring-1 focus-within:ring-brand">
                    <input
                      id="account-confirm-password"
                      type="password"
                      required
                      disabled={passwordSubmitting}
                      value={confirmPassword}
                      onChange={(event) => setConfirmPassword(event.target.value)}
                      placeholder="Nhập lại mật khẩu mới"
                      autoComplete="new-password"
                      className="w-full bg-transparent text-base sm:text-sm text-ink placeholder:text-muted focus:outline-none focus-visible:shadow-none disabled:opacity-50"
                    />
                  </div>
                </div>

                <button
                  type="submit"
                  data-testid="account-password-submit"
                  disabled={passwordSubmitting}
                  className="btn-primary w-full justify-center py-2.5 text-xs sm:text-sm font-bold shadow-brand-sm"
                >
                  {passwordSubmitting ? 'Đang cập nhật…' : 'Đổi mật khẩu'}
                </button>
              </form>
            </section>
          </div>
        </div>
      )}
    </main>
  );
}
