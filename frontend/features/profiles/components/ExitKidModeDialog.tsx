import React, { useEffect, useState } from 'react';
import { SiteDialog } from '@/components/ui/SiteDialog';
import { useAuth } from '@/lib/context/AuthContext';
import { ChildAvatarBadge } from '@/features/profiles/components/ChildAvatarBadge';
import { validateParentPin } from '@/lib/services/profileService';

export function ExitKidModeDialog({
  open,
  onClose,
  preferSiblingSwitch = false,
}: {
  open: boolean;
  onClose: () => void;
  preferSiblingSwitch?: boolean;
}) {
  const { exitKidMode, selectChildProfile, activeProfile, childProfiles = [] } = useAuth();
  const siblings = Array.isArray(childProfiles)
    ? childProfiles.filter((item) => item.id !== activeProfile?.id)
    : [];

  const [pin, setPin] = useState('');
  const [targetDestination, setTargetDestination] = useState<'main' | number>('main');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!open) return;
    if (preferSiblingSwitch && siblings.length > 0) {
      setTargetDestination(siblings[0].id);
    } else {
      setTargetDestination('main');
    }
  }, [open, preferSiblingSwitch, siblings.length]);

  if (!open) return null;

  const handleClose = () => {
    if (busy) return;
    setPin('');
    setError('');
    setTargetDestination('main');
    onClose();
  };

  const runPinVerification = async (destination: 'main' | number) => {
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
      if (!result.success) {
        setPin('');
        setError(result.message || 'Mã PIN phụ huynh chưa đúng.');
        return;
      }
      if (destination !== 'main' && typeof selectChildProfile === 'function') {
        const switchResult = await selectChildProfile(destination);
        if (!switchResult.success) {
          setError(switchResult.message || 'Đã mở khóa nhưng chưa chuyển được sang hồ sơ bé khác.');
          return;
        }
      }
      setPin('');
      setError('');
      setTargetDestination('main');
      onClose();
    } finally {
      setBusy(false);
    }
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    await runPinVerification(targetDestination);
  };

  const selectedSibling =
    typeof targetDestination === 'number'
      ? siblings.find((item) => item.id === targetDestination) ?? null
      : null;

  return (
    <SiteDialog
      title={preferSiblingSwitch && siblings.length > 0 ? 'Đổi hồ sơ xem phim' : 'Thoát hồ sơ trẻ em'}
      tone="warning"
      onClose={handleClose}
      busy={busy}
    >
      <p className="mb-4 text-sm leading-relaxed text-muted">
        {activeProfile
          ? `Nhập mã PIN phụ huynh để rời hồ sơ ${activeProfile.name}, quay về tài khoản chính hoặc chuyển thẳng sang bé khác.`
          : 'Nhập mã PIN phụ huynh để quay lại tài khoản chính.'}
      </p>
      <form onSubmit={handleSubmit} className="space-y-4">
        <div className="space-y-1.5">
          <label htmlFor="exit-kid-pin-input" className="block text-xs font-semibold text-ink">
            Mã PIN phụ huynh (4 đến 6 chữ số)
          </label>
          <div className="input-shell flex items-center rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5">
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

        {siblings.length > 0 && (
          <fieldset className="space-y-2">
            <legend className="text-xs font-semibold text-ink">Chuyển tới sau khi xác nhận mã PIN</legend>
            <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
              <button
                type="button"
                data-testid="exit-kid-target-main"
                aria-pressed={targetDestination === 'main'}
                disabled={busy}
                onClick={() => setTargetDestination('main')}
                className={`flex min-h-[48px] items-center gap-2.5 rounded-xl border px-3 py-2 text-left text-xs font-bold cursor-pointer ${
                  targetDestination === 'main'
                    ? 'border-2 border-ink bg-mint text-ink shadow-[2px_2px_0_#203328]'
                    : 'border-surface-border bg-paper-white text-muted hover:border-ink/50 hover:text-ink'
                }`}
              >
                <span>Tài khoản chính (Ba mẹ)</span>
              </button>
              {siblings.map((sibling) => {
                const selected = targetDestination === sibling.id;
                return (
                  <button
                    key={sibling.id}
                    type="button"
                    data-testid={`exit-kid-switch-sibling-${sibling.id}`}
                    aria-pressed={selected}
                    disabled={busy}
                    onClick={() => {
                      setTargetDestination(sibling.id);
                      if (pin.length >= 4) {
                        void runPinVerification(sibling.id);
                      }
                    }}
                    className={`flex min-h-[48px] items-center gap-2.5 rounded-xl border px-3 py-2 text-left text-xs font-bold cursor-pointer ${
                      selected
                        ? 'border-2 border-ink bg-mint text-ink shadow-[2px_2px_0_#203328]'
                        : 'border-surface-border bg-paper-white text-ink hover:border-ink/50'
                    }`}
                  >
                    <ChildAvatarBadge avatarKey={sibling.avatarKey} size="sm" />
                    <span className="truncate">Chuyển sang {sibling.name}</span>
                  </button>
                );
              })}
            </div>
          </fieldset>
        )}

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
            Ở lại hồ sơ {activeProfile?.name || 'trẻ em'}
          </button>
          <button
            type="submit"
            data-testid="exit-kid-pin-submit"
            disabled={busy || pin.length < 4}
            className="btn-primary w-full sm:w-auto text-xs disabled:opacity-50"
          >
            {busy
              ? 'Đang kiểm tra…'
              : selectedSibling
              ? `Chuyển sang ${selectedSibling.name}`
              : 'Thoát hồ sơ trẻ em'}
          </button>
        </div>
      </form>
    </SiteDialog>
  );
}
