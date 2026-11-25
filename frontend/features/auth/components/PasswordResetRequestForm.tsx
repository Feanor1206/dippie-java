import React, { useEffect, useRef, useState } from 'react';
import {
  PasswordResetError,
  requestPasswordReset,
  validateResetEmail,
} from '@/lib/services/passwordResetService';

export type PasswordResetRequestFormProps = {
  inputId?: string;
  inputRef?: React.RefObject<HTMLInputElement | null>;
  onSubmittingChange?: (submitting: boolean) => void;
  onBackToLogin?: () => void;
  backLabel?: string;
};

export function PasswordResetRequestForm({
  inputId = 'auth-reset-email',
  inputRef,
  onSubmittingChange,
  onBackToLogin,
  backLabel = 'Quay lại đăng nhập',
}: PasswordResetRequestFormProps) {
  const [email, setEmail] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmationMessage, setConfirmationMessage] = useState<string | null>(null);
  const requestRef = useRef<AbortController | null>(null);

  useEffect(() => () => requestRef.current?.abort(), []);

  useEffect(() => {
    onSubmittingChange?.(submitting);
  }, [submitting, onSubmittingChange]);

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (submitting || requestRef.current) return;

    const validationError = validateResetEmail(email);
    if (validationError) {
      setError(validationError);
      return;
    }

    const controller = new AbortController();
    requestRef.current = controller;
    setError(null);
    setSubmitting(true);

    try {
      const result = await requestPasswordReset(email, { signal: controller.signal });
      if (controller.signal.aborted) return;
      setConfirmationMessage(result.message);
    } catch (err: unknown) {
      if (controller.signal.aborted) return;
      if (err instanceof PasswordResetError) {
        if (err.code === 'canceled') return;
        setError(err.message);
        return;
      }
      setError(err instanceof Error && err.message ? err.message : 'Chưa gửi được yêu cầu. Bạn thử lại nhé.');
    } finally {
      if (requestRef.current === controller) {
        requestRef.current = null;
      }
      if (!controller.signal.aborted) {
        setSubmitting(false);
      }
    }
  };

  if (confirmationMessage) {
    return (
      <div className="space-y-4">
        <div
          role="status"
          data-testid="password-reset-request-sent"
          className="rounded-xl border border-ink/30 bg-mint/55 p-4 text-sm leading-relaxed text-ink"
        >
          <p className="font-bold">Kiểm tra hộp thư của bạn</p>
          <p className="mt-1.5 text-xs leading-relaxed text-ink/90">{confirmationMessage}</p>
          <p className="mt-2 text-xs text-muted">
            Liên kết dùng được một lần trong vòng 30 phút.
          </p>
        </div>

        <div className="flex flex-col gap-2 sm:flex-row sm:justify-between">
          <button
            type="button"
            onClick={() => {
              setConfirmationMessage(null);
              setError(null);
            }}
            className="btn-secondary w-full sm:w-auto text-xs"
          >
            Nhập email khác
          </button>
          {onBackToLogin && (
            <button
              type="button"
              data-testid="forgot-back-to-login"
              onClick={onBackToLogin}
              className="btn-primary w-full sm:w-auto text-xs"
            >
              {backLabel}
            </button>
          )}
        </div>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4" noValidate>
      <p className="text-xs leading-relaxed text-muted">
        Nhập email bạn dùng để đăng ký Dippie. Nếu có tài khoản khớp với email này, Dippie sẽ gửi liên kết đặt lại mật khẩu.
      </p>

      {error && (
        <div
          role="alert"
          data-testid="password-reset-request-error"
          className="rounded-xl border border-red-800/35 bg-red-50 p-3 text-xs leading-relaxed text-red-900"
        >
          {error}
        </div>
      )}

      <div>
        <label htmlFor={inputId} className="mb-1.5 block text-xs font-semibold text-ink">
          Email tài khoản
        </label>
        <div className="relative flex items-center rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 focus-within:border-ink focus-within:bg-paper-white">
          <input
            id={inputId}
            ref={inputRef}
            data-testid="forgot-email-input"
            type="email"
            required
            maxLength={100}
            disabled={submitting}
            value={email}
            onChange={(event) => {
              setEmail(event.target.value);
              if (error) setError(null);
            }}
            placeholder="ten@email.com"
            autoComplete="email"
            className="w-full bg-transparent text-base sm:text-sm text-ink placeholder:text-muted focus:outline-none focus-visible:shadow-none disabled:opacity-50"
          />
        </div>
      </div>

      <button
        type="submit"
        data-testid="forgot-submit-button"
        disabled={submitting}
        className="btn-primary w-full justify-center py-2.5 text-xs sm:text-sm font-bold shadow-brand-sm"
      >
        {submitting ? 'Đang gửi liên kết…' : 'Gửi liên kết đặt lại mật khẩu'}
      </button>

      {onBackToLogin && (
        <div className="pt-1 text-center">
          <button
            type="button"
            data-testid="forgot-back-to-login"
            disabled={submitting}
            onClick={onBackToLogin}
            className="inline-flex min-h-[44px] items-center px-3 text-xs font-semibold text-brand-dark underline underline-offset-4 hover:text-ink cursor-pointer disabled:opacity-50"
          >
            {backLabel}
          </button>
        </div>
      )}
    </form>
  );
}
