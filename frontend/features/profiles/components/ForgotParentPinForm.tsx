import React, { useState } from 'react';
import { useAuth } from '@/lib/context/AuthContext';
import { validateParentPin } from '@/lib/services/profileService';

export function ForgotParentPinForm({
  onBack,
  onDone,
  onBusyChange,
}: {
  onBack: () => void;
  onDone: () => void;
  onBusyChange?: (busy: boolean) => void;
}) {
  const { resetPinAndExitKidMode } = useAuth();
  const [password, setPassword] = useState('');
  const [newPin, setNewPin] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const updateBusy = (next: boolean) => {
    setBusy(next);
    onBusyChange?.(next);
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (busy) return;
    if (!password) {
      setError('Hãy nhập mật khẩu tài khoản.');
      return;
    }
    const pinError = validateParentPin(newPin);
    if (pinError) {
      setError(pinError);
      return;
    }
    updateBusy(true);
    setError('');
    try {
      const result = await resetPinAndExitKidMode(password, newPin);
      if (result.success) {
        setPassword('');
        setNewPin('');
        onDone();
      } else {
        setPassword('');
        setError(result.message || 'Chưa đặt lại được mã PIN.');
      }
    } finally {
      updateBusy(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} data-testid="forgot-pin-form" className="space-y-4">
      <div>
        <h3 className="text-base font-bold text-ink">Đặt lại mã PIN phụ huynh</h3>
        <p className="mt-1 text-sm leading-relaxed text-muted">
          Nhập mật khẩu đăng nhập Dippie của ba mẹ và chọn mã PIN mới. Xong là vào thẳng tài khoản chính.
        </p>
      </div>
      <div>
        <label htmlFor="forgot-pin-password" className="mb-1.5 block text-xs font-semibold text-ink">
          Mật khẩu tài khoản
        </label>
        <input
          id="forgot-pin-password"
          data-testid="forgot-pin-password"
          type="password"
          autoComplete="current-password"
          autoFocus
          required
          disabled={busy}
          value={password}
          onChange={(event) => {
            setPassword(event.target.value);
            if (error) setError('');
          }}
          className="w-full rounded-lg border border-surface-border bg-paper-white px-3.5 py-2.5 text-base text-ink focus:border-ink focus:outline-none disabled:opacity-50"
        />
      </div>
      <div>
        <label htmlFor="forgot-pin-new" className="mb-1.5 block text-xs font-semibold text-ink">
          Mã PIN mới (4 đến 6 chữ số)
        </label>
        <input
          id="forgot-pin-new"
          data-testid="forgot-pin-new"
          type="password"
          inputMode="numeric"
          pattern="[0-9]*"
          autoComplete="new-password"
          maxLength={6}
          required
          disabled={busy}
          value={newPin}
          onChange={(event) => {
            setNewPin(event.target.value.replace(/[^0-9]/g, '').slice(0, 6));
            if (error) setError('');
          }}
          placeholder="••••"
          className="w-full rounded-lg border border-surface-border bg-paper-white px-3.5 py-2.5 text-base tracking-[0.25em] text-ink focus:border-ink focus:outline-none disabled:opacity-50"
        />
      </div>
      {error && (
        <div
          role="alert"
          data-testid="forgot-pin-error"
          className="rounded-lg border border-red-800/35 bg-red-50 px-3.5 py-2.5 text-xs text-red-900"
        >
          {error}
        </div>
      )}
      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <button
          type="button"
          disabled={busy}
          onClick={onBack}
          className="btn-secondary min-h-[44px] w-full sm:w-auto text-xs font-semibold"
        >
          Quay lại nhập PIN
        </button>
        <button
          type="submit"
          data-testid="forgot-pin-submit"
          disabled={busy || !password || newPin.length < 4}
          className="btn-primary min-h-[44px] w-full sm:w-auto text-xs font-bold disabled:opacity-50"
        >
          {busy ? 'Đang kiểm tra…' : 'Đặt lại PIN'}
        </button>
      </div>
    </form>
  );
}
