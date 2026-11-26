import React, { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useAuth } from '@/lib/context/AuthContext';
import { PasswordResetRequestForm } from '@/features/auth/components/PasswordResetRequestForm';
import {
  confirmPasswordReset,
  evaluatePasswordStrength,
  PASSWORD_RULES_HINT,
  PASSWORD_STRENGTH_TIP,
  PasswordResetError,
  validateNewPassword,
} from '@/lib/services/passwordResetService';

export function ForgotPasswordPage() {
  const { openAuthModal } = useAuth();

  return (
    <main id="main-content" tabIndex={-1} className="page-shell flex min-h-[72vh] items-center justify-center py-8 sm:py-12">
      <section
        aria-labelledby="forgot-page-title"
        className="w-full max-w-md rounded-2xl border-2 border-ink bg-paper-white p-6 shadow-brand-lg sm:p-8"
      >
        <span className="comic-label">Hỗ trợ tài khoản</span>
        <h1 id="forgot-page-title" className="mt-3 text-2xl font-bold text-ink sm:text-3xl">
          Quên mật khẩu
        </h1>
        <div className="mt-4">
          <PasswordResetRequestForm
            inputId="forgot-page-email"
            onBackToLogin={() => openAuthModal('login')}
            backLabel="Quay lại đăng nhập"
          />
        </div>
        <div className="mt-4 border-t border-surface-border/40 pt-4 text-center">
          <Link
            to="/"
            className="inline-flex min-h-[44px] items-center px-3 text-xs font-semibold text-muted hover:text-ink underline underline-offset-4"
          >
            Về trang chủ
          </Link>
        </div>
      </section>
    </main>
  );
}

export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const { openAuthModal } = useAuth();
  const token = (searchParams.get('token') || '').trim();

  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showNew, setShowNew] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [tokenInvalidMessage, setTokenInvalidMessage] = useState<string | null>(null);
  const [succeeded, setSucceeded] = useState(false);
  const requestRef = useRef<AbortController | null>(null);

  useEffect(() => () => requestRef.current?.abort(), []);

  useEffect(() => {
    setError(null);
    setTokenInvalidMessage(null);
    setSucceeded(false);
  }, [token]);

  const strength = evaluatePasswordStrength(newPassword);

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (submitting || requestRef.current) return;

    const validationError = validateNewPassword(newPassword, confirmPassword);
    if (validationError) {
      setError(validationError);
      return;
    }

    const controller = new AbortController();
    requestRef.current = controller;
    setError(null);
    setTokenInvalidMessage(null);
    setSubmitting(true);

    try {
      await confirmPasswordReset(token, newPassword, { signal: controller.signal });
      if (controller.signal.aborted) return;
      setNewPassword('');
      setConfirmPassword('');
      setSucceeded(true);
    } catch (err: unknown) {
      if (controller.signal.aborted) return;
      if (err instanceof PasswordResetError) {
        if (err.code === 'canceled') return;
        if (err.code === 'invalid_token') {
          setTokenInvalidMessage(err.message);
          return;
        }
        setError(err.message);
        return;
      }
      setError('Chưa đặt lại được mật khẩu lúc này. Bạn thử lại nhé.');
    } finally {
      if (requestRef.current === controller) {
        requestRef.current = null;
      }
      if (!controller.signal.aborted) {
        setSubmitting(false);
      }
    }
  };

  return (
    <main id="main-content" tabIndex={-1} className="page-shell flex min-h-[72vh] items-center justify-center py-8 sm:py-12">
      <section
        aria-labelledby="reset-password-title"
        className="w-full max-w-md rounded-2xl border-2 border-ink bg-paper-white p-6 shadow-brand-lg sm:p-8"
      >
        <span className="comic-label">Bảo mật tài khoản</span>
        <h1 id="reset-password-title" className="mt-3 text-2xl font-bold text-ink sm:text-3xl">
          Đặt lại mật khẩu
        </h1>

        {succeeded ? (
          <div className="mt-5 space-y-4">
            <div
              role="status"
              data-testid="reset-password-success"
              className="rounded-xl border border-ink/30 bg-mint/55 p-4 text-sm leading-relaxed text-ink"
            >
              <p className="font-bold">Mật khẩu mới đã được lưu</p>
              <p className="mt-1.5 text-xs leading-relaxed text-ink/90">
                Các thiết bị khác đã được đăng xuất để giữ an toàn cho tài khoản.
              </p>
            </div>
            <div className="flex flex-col gap-2.5 sm:flex-row">
              <button
                type="button"
                data-testid="reset-success-login"
                onClick={() => openAuthModal('login')}
                className="btn-primary flex-1 text-xs sm:text-sm"
              >
                Đăng nhập
              </button>
              <Link to="/" className="btn-secondary flex-1 text-xs sm:text-sm">
                Về trang chủ
              </Link>
            </div>
          </div>
        ) : !token || tokenInvalidMessage ? (
          <div className="mt-5 space-y-4">
            <div
              role="alert"
              data-testid="reset-token-invalid"
              className="rounded-xl border border-red-800/35 bg-red-50 p-4 text-sm leading-relaxed text-red-900"
            >
              <p className="font-bold">Liên kết không còn hiệu lực</p>
              <p className="mt-1.5 text-xs leading-relaxed">
                {tokenInvalidMessage ||
                  'Liên kết này không còn dùng được hoặc đã hết hạn.'}
              </p>
              <p className="mt-2 text-xs text-red-900/80">
                Mỗi liên kết chỉ dùng được một lần trong vòng 30 phút.
              </p>
            </div>
            <div className="flex flex-col gap-2.5 sm:flex-row">
              <Link
                to="/quen-mat-khau"
                data-testid="request-new-reset-link"
                className="btn-primary flex-1 text-xs sm:text-sm"
              >
                Gửi liên kết mới
              </Link>
              <Link to="/" className="btn-secondary flex-1 text-xs sm:text-sm">
                Về trang chủ
              </Link>
            </div>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="mt-4 space-y-4" noValidate>
            <p className="text-xs leading-relaxed text-muted">
              Đặt mật khẩu mới cho tài khoản Dippie của bạn, rồi đăng nhập lại bằng mật khẩu này.
            </p>

            {error && (
              <div
                role="alert"
                data-testid="reset-password-error"
                className="rounded-xl border border-red-800/35 bg-red-50 p-3 text-xs leading-relaxed text-red-900"
              >
                {error}
              </div>
            )}

            <div>
              <label htmlFor="reset-new-password" className="mb-1.5 block text-xs font-semibold text-ink">
                Mật khẩu mới
              </label>
              <div className="relative flex items-center rounded-lg border border-surface-border bg-surface-muted pl-3 pr-1 focus-within:border-ink focus-within:bg-paper-white">
                <input
                  id="reset-new-password"
                  data-testid="reset-new-password-input"
                  type={showNew ? 'text' : 'password'}
                  required
                  disabled={submitting}
                  value={newPassword}
                  onChange={(event) => {
                    setNewPassword(event.target.value);
                    if (error) setError(null);
                  }}
                  placeholder="Ít nhất 6 ký tự"
                  autoComplete="new-password"
                  aria-describedby="reset-password-hint"
                  className="w-full bg-transparent py-2.5 text-base sm:text-sm text-ink placeholder:text-muted focus:outline-none focus-visible:shadow-none disabled:opacity-50"
                />
                <button
                  type="button"
                  data-testid="toggle-new-password"
                  disabled={submitting}
                  aria-pressed={showNew}
                  aria-label={showNew ? 'Ẩn mật khẩu mới' : 'Hiện mật khẩu mới'}
                  onClick={() => setShowNew((prev) => !prev)}
                  className="inline-flex min-h-[44px] min-w-[44px] shrink-0 items-center justify-center rounded-md px-2 text-xs font-semibold text-muted hover:bg-surface-elevated hover:text-ink cursor-pointer"
                >
                  {showNew ? 'Ẩn' : 'Hiện'}
                </button>
              </div>
              <div id="reset-password-hint" className="mt-2 space-y-1.5 text-[0.6875rem] text-muted">
                <p>{PASSWORD_RULES_HINT} {PASSWORD_STRENGTH_TIP}</p>
                {strength.level !== 'empty' && (
                  <div className="flex items-center gap-2">
                    <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-surface-elevated">
                      <div
                        className={`h-full rounded-full transition-all ${
                          strength.level === 'good'
                            ? 'w-full bg-[#36563c]'
                            : strength.level === 'fair'
                            ? 'w-2/3 bg-sage'
                            : 'w-1/3 bg-red-700'
                        }`}
                      />
                    </div>
                    <span
                      className={`font-semibold ${
                        strength.level === 'weak' ? 'text-red-800' : 'text-brand-dark'
                      }`}
                    >
                      {strength.label}
                    </span>
                  </div>
                )}
              </div>
            </div>

            <div>
              <label htmlFor="reset-confirm-password" className="mb-1.5 block text-xs font-semibold text-ink">
                Nhập lại mật khẩu mới
              </label>
              <div className="relative flex items-center rounded-lg border border-surface-border bg-surface-muted pl-3 pr-1 focus-within:border-ink focus-within:bg-paper-white">
                <input
                  id="reset-confirm-password"
                  data-testid="reset-confirm-password-input"
                  type={showConfirm ? 'text' : 'password'}
                  required
                  disabled={submitting}
                  value={confirmPassword}
                  onChange={(event) => {
                    setConfirmPassword(event.target.value);
                    if (error) setError(null);
                  }}
                  placeholder="Nhập lại mật khẩu mới"
                  autoComplete="new-password"
                  className="w-full bg-transparent py-2.5 text-base sm:text-sm text-ink placeholder:text-muted focus:outline-none focus-visible:shadow-none disabled:opacity-50"
                />
                <button
                  type="button"
                  data-testid="toggle-confirm-password"
                  disabled={submitting}
                  aria-pressed={showConfirm}
                  aria-label={showConfirm ? 'Ẩn mật khẩu nhập lại' : 'Hiện mật khẩu nhập lại'}
                  onClick={() => setShowConfirm((prev) => !prev)}
                  className="inline-flex min-h-[44px] min-w-[44px] shrink-0 items-center justify-center rounded-md px-2 text-xs font-semibold text-muted hover:bg-surface-elevated hover:text-ink cursor-pointer"
                >
                  {showConfirm ? 'Ẩn' : 'Hiện'}
                </button>
              </div>
            </div>

            <button
              type="submit"
              data-testid="reset-password-submit"
              disabled={submitting}
              className="btn-primary w-full justify-center py-2.5 text-xs sm:text-sm font-bold shadow-brand-sm"
            >
              {submitting ? 'Đang lưu…' : 'Đặt lại mật khẩu'}
            </button>

            <div className="pt-1 text-center">
              <Link
                to="/quen-mat-khau"
                className="inline-flex min-h-[44px] items-center px-3 text-xs font-semibold text-brand-dark underline underline-offset-4 hover:text-ink"
              >
                Liên kết hết hạn? Gửi liên kết mới
              </Link>
            </div>
          </form>
        )}
      </section>
    </main>
  );
}
