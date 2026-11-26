import React, { useState } from 'react';
import { SiteDialog } from '@/components/ui/SiteDialog';
import { useAuth } from '@/lib/context/AuthContext';
import { validateParentPin } from '@/lib/services/profileService';

export function ExitKidModeDialog({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}) {
  const { exitKidMode, activeProfile } = useAuth();
  const [pin, setPin] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  if (!open) return null;

  const handleClose = () => {
    if (busy) return;
    setPin('');
    setError('');
    onClose();
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (busy) return;
    const pinError = validateParentPin(pin);
    if (pinError) {
      setError(pinError);
      return;
    }
    setBusy(true);
    setError('');
    try {
      const result = await exitKidMode(pin);
      if (result.success) {
        setPin('');
        setError('');
        onClose();
      } else {
        setPin('');
        setError(result.message || 'Mã PIN phụ huynh chưa đúng.');
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <SiteDialog title="Thoát hồ sơ trẻ em" tone="warning" onClose={handleClose} busy={busy}>
      <p className="mb-4 text-sm leading-relaxed text-muted">
        {activeProfile
          ? `Nhập mã PIN phụ huynh để thoát khỏi hồ sơ ${activeProfile.name} và quay lại tài khoản chính.`
          : 'Nhập mã PIN phụ huynh để quay lại tài khoản chính.'}
      </p>
      <form onSubmit={handleSubmit} className="space-y-4">
        <div className="space-y-1.5">
          <label htmlFor="exit-kid-pin-input" className="block text-xs font-semibold text-ink">
            Mã PIN phụ huynh (4 đến 6 chữ số)
          </label>
          <div className="flex items-center rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 focus-within:border-ink focus-within:bg-paper-white">
            <input
              id="exit-kid-pin-input"
              data-testid="exit-kid-pin-input"
              type="password"
              inputMode="numeric"
              pattern="[0-9]*"
              enterKeyHint="done"
              autoComplete="off"
              autoFocus
              maxLength={6}
              required
              disabled={busy}
              value={pin}
              onChange={(event) => {
                setPin(event.target.value.replace(/[^0-9]/g, '').slice(0, 6));
                if (error) setError('');
              }}
              placeholder="••••"
              className="w-full bg-transparent text-base sm:text-sm tracking-[0.25em] text-ink placeholder:text-muted focus:outline-none disabled:opacity-50"
            />
          </div>
        </div>

        {error && (
          <div
            role="alert"
            data-testid="exit-kid-pin-error"
            className="rounded-lg border border-red-800/35 bg-red-50 px-3.5 py-2.5 text-xs leading-relaxed text-red-900"
          >
            {error}
          </div>
        )}

        <div className="flex flex-col-reverse gap-2 pt-1 sm:flex-row sm:justify-end">
          <button
            type="button"
            disabled={busy}
            onClick={handleClose}
            className="btn-secondary w-full sm:w-auto text-xs"
          >
            Ở lại hồ sơ trẻ em
          </button>
          <button
            type="submit"
            data-testid="exit-kid-pin-submit"
            disabled={busy || pin.length < 4}
            className="btn-primary w-full sm:w-auto text-xs disabled:opacity-50"
          >
            {busy ? 'Đang kiểm tra…' : 'Thoát hồ sơ trẻ em'}
          </button>
        </div>
      </form>
    </SiteDialog>
  );
}
