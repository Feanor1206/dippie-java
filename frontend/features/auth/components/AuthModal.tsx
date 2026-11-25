import { Link } from 'react-router';
import React, { useState, useRef, useEffect } from 'react';
import { useAuth } from '@/lib/context/AuthContext';
import { PasswordResetRequestForm } from '@/features/auth/components/PasswordResetRequestForm';
import {
  evaluatePasswordStrength,
  PASSWORD_RULES_HINT,
  PASSWORD_STRENGTH_TIP,
} from '@/lib/services/passwordResetService';

export function AuthModal() {
  const { isAuthModalOpen, closeAuthModal, authModalMode, openAuthModal, login, register } = useAuth();

  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [acceptedTerms, setAcceptedTerms] = useState(false);

  const modalRef = useRef<HTMLDivElement>(null);
  const openerRef = useRef<HTMLElement | null>(null);
  const dialogState = useRef({ isSubmitting, closeAuthModal });
  useEffect(() => { dialogState.current = { isSubmitting, closeAuthModal }; }, [isSubmitting, closeAuthModal]);
  const firstInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    if (!isAuthModalOpen) return;
    if (document.activeElement && (!modalRef.current || !modalRef.current.contains(document.activeElement))) {
      openerRef.current = document.activeElement as HTMLElement | null;
    }
    const originalOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const timer = setTimeout(() => {
      firstInputRef.current?.focus();
    }, 50);
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !dialogState.current.isSubmitting) {
        e.preventDefault();
        dialogState.current.closeAuthModal();
        return;
      }

      if (e.key === 'Tab' && modalRef.current) {
        const focusableElements = modalRef.current.querySelectorAll<HTMLElement>(
          'a[href], button:not([disabled]), input:not([disabled]), [tabindex]:not([tabindex="-1"])'
        );
        if (focusableElements.length === 0) {
          e.preventDefault();
          modalRef.current.focus();
          return;
        }

        const firstElement = focusableElements[0];
        const lastElement = focusableElements[focusableElements.length - 1];

        if (!modalRef.current.contains(document.activeElement)) {
          e.preventDefault();
          if (e.shiftKey) lastElement.focus();
          else firstElement.focus();
          return;
        }

        if (e.shiftKey) {
          if (document.activeElement === firstElement) {
            e.preventDefault();
            lastElement.focus();
          }
        } else {
          if (document.activeElement === lastElement) {
            e.preventDefault();
            firstElement.focus();
          }
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);

    return () => {
      clearTimeout(timer);
      document.body.style.overflow = originalOverflow;
      window.removeEventListener('keydown', handleKeyDown);
      openerRef.current?.focus();
    };
  }, [isAuthModalOpen]);

  useEffect(() => {
    if (isSubmitting && modalRef.current && !modalRef.current.contains(document.activeElement)) {
      modalRef.current.focus();
    }
  }, [isSubmitting]);

  if (!isAuthModalOpen) return null;

  const isLogin = authModalMode === 'login';
  const isRegister = authModalMode === 'register';
  const isForgot = authModalMode === 'forgot';
  const passwordStrength = isRegister ? evaluatePasswordStrength(password) : null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isSubmitting || isForgot) return;
    if (isRegister && !acceptedTerms) {
      setError('Bạn đánh dấu đồng ý với Điều khoản sử dụng và Chính sách quyền riêng tư trước nhé.');
      return;
    }

    setError(null);
    setIsSubmitting(true);

    try {
      if (isLogin) {
        const res = await login(username, password);
        if (res.success) {
          closeAuthModal();
        } else {
          setError(res.message || 'Tên đăng nhập hoặc mật khẩu chưa đúng.');
        }
      } else {
        const res = await register(username, email, password, fullName, acceptedTerms);
        if (res.success) {
          closeAuthModal();
        } else {
          setError(res.message || 'Chưa tạo được tài khoản. Bạn thử lại nhé.');
        }
      }
    } catch {
      setError('Không kết nối được với Dippie. Bạn kiểm tra lại mạng rồi thử lại nhé.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div
      ref={modalRef}
      role="dialog"
      tabIndex={-1}
      aria-modal="true"
      aria-labelledby="auth-modal-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-ink/70 p-4 backdrop-blur-sm animate-fade-in"
    >
      <div className="relative max-h-[calc(100dvh-2rem)] w-full max-w-md overflow-y-auto rounded-2xl border-2 border-ink bg-paper-white p-6 shadow-brand-lg sm:p-8">
        <button
          type="button"
          onClick={closeAuthModal}
          disabled={isSubmitting}
          className="absolute right-3.5 top-3.5 flex min-h-[44px] min-w-[44px] items-center justify-center rounded-lg px-3 text-xs font-semibold text-muted hover:bg-surface-elevated hover:text-ink cursor-pointer disabled:opacity-50"
          aria-label="Đóng cửa sổ"
        >
          Đóng
        </button>

        <div className="mb-5 pr-12">
          <div className="flex items-center gap-1.5 font-bold tracking-tight text-xl text-ink">
            <span className="comic-wordmark text-2xl text-ink">Dippie</span>
          </div>
          <h2 id="auth-modal-title" className="mt-2 text-xl font-bold tracking-tight text-ink">
            {isForgot ? 'Quên mật khẩu' : isLogin ? 'Đăng nhập' : 'Tạo tài khoản gia đình'}
          </h2>
          <p className="mt-1 text-xs text-muted">
            {isForgot
              ? 'Nhận liên kết đặt lại mật khẩu qua email.'
              : isLogin
              ? 'Lưu mục Xem sau, Yêu thích và xem tiếp tập dở trên mọi thiết bị.'
              : 'Dành cho ba mẹ hoặc người giám hộ chọn và lưu phim cho bé.'}
          </p>
        </div>

        {!isForgot && (
          <div className="mb-6 grid grid-cols-2 gap-1 rounded-xl border border-ink/30 bg-surface-muted p-1">
            <button
              type="button"
              aria-pressed={isLogin}
              disabled={isSubmitting}
              onClick={() => {
                setError(null);
                openAuthModal('login');
              }}
              className={`min-h-[44px] rounded-lg py-2 text-xs font-semibold transition-colors cursor-pointer disabled:opacity-60 ${
                isLogin ? 'border border-ink bg-mint font-bold text-ink shadow-[2px_2px_0_#203328]' : 'text-muted hover:text-ink'
              }`}
            >
              Đăng nhập
            </button>
            <button
              type="button"
              aria-pressed={isRegister}
              disabled={isSubmitting}
              onClick={() => {
                setError(null);
                openAuthModal('register');
              }}
              className={`min-h-[44px] rounded-lg py-2 text-xs font-semibold transition-colors cursor-pointer disabled:opacity-60 ${
                isRegister ? 'border border-ink bg-mint font-bold text-ink shadow-[2px_2px_0_#203328]' : 'text-muted hover:text-ink'
              }`}
            >
              Đăng ký
            </button>
          </div>
        )}

        {isForgot ? (
          <PasswordResetRequestForm
            inputRef={firstInputRef}
            onSubmittingChange={setIsSubmitting}
            onBackToLogin={() => {
              setError(null);
              openAuthModal('login');
            }}
          />
        ) : (
          <>
            {error && (
              <div role="alert" className="mb-4 rounded-xl border border-red-800/35 bg-red-50 p-3 text-xs leading-relaxed text-red-900">
                {error}
              </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-4">
              {isRegister && (
                <div>
                  <label htmlFor="auth-full-name" className="mb-1.5 block text-xs font-semibold text-ink">
                    Họ và tên (không bắt buộc)
                  </label>
                  <div className="relative flex items-center rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 focus-within:border-ink focus-within:bg-paper-white">
                    <input
                      id="auth-full-name"
                      ref={isRegister ? firstInputRef : undefined}
                      type="text"
                      disabled={isSubmitting}
                      value={fullName}
                      onChange={(e) => setFullName(e.target.value)}
                      placeholder="Tên hiển thị của bạn"
                      autoComplete="name"
                      className="w-full bg-transparent text-base sm:text-sm text-ink placeholder:text-muted focus:outline-none focus-visible:shadow-none disabled:opacity-50"
                    />
                  </div>
                </div>
              )}

              <div>
                <label htmlFor="auth-username" className="mb-1.5 block text-xs font-semibold text-ink">
                  {isLogin ? 'Tên đăng nhập hoặc email' : 'Tên đăng nhập'}
                </label>
                <div className="relative flex items-center rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 focus-within:border-ink focus-within:bg-paper-white">
                  <input
                    id="auth-username"
                    ref={isLogin ? firstInputRef : undefined}
                    data-testid="login-username-input"
                    type="text"
                    required
                    disabled={isSubmitting}
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    placeholder={isLogin ? 'Nhập tên đăng nhập hoặc email' : 'Ví dụ: mebebo'}
                    autoComplete={isLogin ? 'username' : 'new-username'}
                    className="w-full bg-transparent text-base sm:text-sm text-ink placeholder:text-muted focus:outline-none focus-visible:shadow-none disabled:opacity-50"
                  />
                </div>
              </div>

              {isRegister && (
                <div>
                  <label htmlFor="auth-email" className="mb-1.5 block text-xs font-semibold text-ink">
                    Email
                  </label>
                  <div className="relative flex items-center rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 focus-within:border-ink focus-within:bg-paper-white">
                    <input
                      id="auth-email"
                      type="email"
                      required
                      disabled={isSubmitting}
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      placeholder="ten@email.com"
                      autoComplete="email"
                      className="w-full bg-transparent text-base sm:text-sm text-ink placeholder:text-muted focus:outline-none focus-visible:shadow-none disabled:opacity-50"
                    />
                  </div>
                </div>
              )}

              <div>
                <div className="mb-1.5 flex items-center justify-between gap-2">
                  <label htmlFor="auth-password" className="block text-xs font-semibold text-ink">
                    Mật khẩu
                  </label>
                  {isLogin && (
                    <button
                      type="button"
                      data-testid="forgot-password-link"
                      disabled={isSubmitting}
                      onClick={() => {
                        setError(null);
                        openAuthModal('forgot');
                      }}
                      className="inline-flex min-h-[44px] items-center px-1.5 text-xs font-semibold text-brand-dark underline underline-offset-4 hover:text-ink cursor-pointer disabled:opacity-50"
                    >
                      Quên mật khẩu?
                    </button>
                  )}
                </div>
                <div className="relative flex items-center rounded-lg border border-surface-border bg-surface-muted pl-3 pr-1 focus-within:border-ink focus-within:bg-paper-white">
                  <input
                    id="auth-password"
                    data-testid="login-password-input"
                    type={showPassword ? 'text' : 'password'}
                    required
                    disabled={isSubmitting}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="••••••••"
                    autoComplete={isLogin ? 'current-password' : 'new-password'}
                    aria-describedby={isRegister ? 'auth-password-hint' : undefined}
                    className="w-full bg-transparent py-2.5 text-base sm:text-sm text-ink placeholder:text-muted focus:outline-none focus-visible:shadow-none disabled:opacity-50"
                  />
                  <button
                    type="button"
                    disabled={isSubmitting}
                    aria-pressed={showPassword}
                    aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                    onClick={() => setShowPassword((prev) => !prev)}
                    className="inline-flex min-h-[44px] min-w-[44px] shrink-0 items-center justify-center rounded-md px-2 text-xs font-semibold text-muted hover:bg-surface-elevated hover:text-ink cursor-pointer"
                  >
                    {showPassword ? 'Ẩn' : 'Hiện'}
                  </button>
                </div>
                {isRegister && (
                  <div id="auth-password-hint" className="mt-2 space-y-1.5 text-[11px] text-muted">
                    <p>{PASSWORD_RULES_HINT} {PASSWORD_STRENGTH_TIP}</p>
                    {passwordStrength && passwordStrength.level !== 'empty' && (
                      <div className="flex items-center gap-2">
                        <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-surface-elevated">
                          <div
                            className={`h-full rounded-full transition-all ${
                              passwordStrength.level === 'good'
                                ? 'w-full bg-[#36563c]'
                                : passwordStrength.level === 'fair'
                                ? 'w-2/3 bg-sage'
                                : 'w-1/3 bg-red-700'
                            }`}
                          />
                        </div>
                        <span
                          className={`font-semibold ${
                            passwordStrength.level === 'weak' ? 'text-red-800' : 'text-brand-dark'
                          }`}
                        >
                          {passwordStrength.label}
                        </span>
                      </div>
                    )}
                  </div>
                )}
              </div>

              {isRegister && (
                <div
                  className={`rounded-xl border p-3.5 text-xs leading-relaxed text-ink transition-colors ${
                    acceptedTerms
                      ? 'border-ink bg-mint/40'
                      : 'border-surface-border/70 bg-surface-muted'
                  }`}
                >
                  <label htmlFor="auth-accepted-terms" className="flex min-h-[44px] cursor-pointer items-start gap-3">
                    <input
                      id="auth-accepted-terms"
                      type="checkbox"
                      name="acceptedTerms"
                      required
                      checked={acceptedTerms}
                      disabled={isSubmitting}
                      onChange={(event) => setAcceptedTerms(event.target.checked)}
                      className="mt-0.5 h-5 w-5 shrink-0 accent-[#203328]"
                    />
                    <span className="leading-relaxed">
                      Tôi là phụ huynh hoặc người giám hộ, đã đọc và đồng ý với Điều khoản sử dụng và Chính sách quyền riêng tư của Dippie.
                    </span>
                  </label>
                  <div className="mt-1 flex flex-wrap gap-x-4 pl-8">
                    <Link
                      to="/dieu-khoan"
                      onClick={closeAuthModal}
                      className="inline-flex min-h-[44px] items-center font-semibold text-brand-dark underline underline-offset-4 hover:text-ink"
                    >
                      Điều khoản sử dụng
                    </Link>
                    <Link
                      to="/quyen-rieng-tu"
                      onClick={closeAuthModal}
                      className="inline-flex min-h-[44px] items-center font-semibold text-brand-dark underline underline-offset-4 hover:text-ink"
                    >
                      Chính sách quyền riêng tư
                    </Link>
                  </div>
                </div>
              )}

              <button
                data-testid="login-submit-button"
                type="submit"
                disabled={isSubmitting}
                className="btn-primary w-full justify-center py-2.5 text-xs sm:text-sm font-bold shadow-brand-sm disabled:opacity-50 cursor-pointer"
              >
                {isSubmitting ? (
                  <span className="flex items-center gap-2">
                    {isLogin ? 'Đang đăng nhập…' : 'Đang tạo tài khoản…'}
                  </span>
                ) : isLogin ? (
                  <span className="flex items-center gap-1.5">
                    Đăng nhập
                  </span>
                ) : (
                  <span className="flex items-center gap-1.5">
                    Tạo tài khoản
                  </span>
                )}
              </button>
            </form>
          </>
        )}
      </div>
    </div>
  );
}
