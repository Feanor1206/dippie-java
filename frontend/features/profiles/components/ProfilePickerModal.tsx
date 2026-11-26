import React, { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { getUserInitials } from '@/lib/labels';
import { useAuth } from '@/lib/context/AuthContext';
import { ChildAvatarBadge } from '@/features/profiles/components/ChildAvatarBadge';
import { validateParentPin } from '@/lib/services/profileService';

const FOCUSABLE_SELECTOR =
  'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

export function ProfilePickerModal() {
  const {
    user,
    isAuthenticated,
    parentModeEnabled,
    childProfiles,
    activeProfile,
    showProfilePicker,
    closeProfilePicker,
    selectChildProfile,
    exitKidMode,
  } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [busyProfileId, setBusyProfileId] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [pinPromptOpen, setPinPromptOpen] = useState(false);
  const [pin, setPin] = useState('');
  const [pinBusy, setPinBusy] = useState(false);
  const [pinError, setPinError] = useState('');

  const modalRef = useRef<HTMLDivElement | null>(null);
  const pinInputRef = useRef<HTMLInputElement | null>(null);
  const openerRef = useRef<HTMLElement | null>(null);
  const busyRef = useRef(false);
  busyRef.current = busyProfileId !== null || pinBusy;
  const closeRef = useRef(closeProfilePicker);
  closeRef.current = closeProfilePicker;
  const pinPromptOpenRef = useRef(pinPromptOpen);
  pinPromptOpenRef.current = pinPromptOpen;
  const parentModeRef = useRef(parentModeEnabled);
  parentModeRef.current = parentModeEnabled;

  const isVisible = Boolean(
    showProfilePicker && isAuthenticated && user && parentModeEnabled && !activeProfile
  );

  useEffect(() => {
    if (!isVisible) {
      setPinPromptOpen(false);
      setPin('');
      setPinError('');
      setError('');
      return;
    }
    if (
      typeof document !== 'undefined' &&
      document.activeElement instanceof HTMLElement &&
      !modalRef.current?.contains?.(document.activeElement)
    ) {
      openerRef.current = document.activeElement;
    }

    const originalOverflow = typeof document !== 'undefined' ? document.body?.style?.overflow : undefined;
    if (typeof document !== 'undefined' && document.body?.style) {
      document.body.style.overflow = 'hidden';
    }

    const focusTimer = setTimeout(() => {
      const firstChildBtn = modalRef.current?.querySelector<HTMLElement>('[data-testid^="picker-child-profile-"]');
      const firstTarget =
        firstChildBtn ||
        modalRef.current?.querySelector<HTMLElement>('[data-testid="picker-main-profile"]') ||
        modalRef.current?.querySelector<HTMLElement>(FOCUSABLE_SELECTOR);
      firstTarget?.focus();
    }, 30);

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        if (!busyRef.current) {
          event.preventDefault();
          if (pinPromptOpenRef.current) {
            setPinPromptOpen(false);
            setPin('');
            setPinError('');
          } else if (parentModeRef.current) {
            setPinPromptOpen(true);
            setPinError('');
          } else {
            closeRef.current();
          }
        }
        return;
      }
      if (event.key === 'Tab' && modalRef.current) {
        const focusable = modalRef.current.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR);
        if (!focusable.length) return;
        const firstElement = focusable[0];
        const lastElement = focusable[focusable.length - 1];
        if (!modalRef.current.contains(document.activeElement)) {
          event.preventDefault();
          firstElement.focus();
          return;
        }
        if (event.shiftKey) {
          if (document.activeElement === firstElement) {
            event.preventDefault();
            lastElement.focus();
          }
        } else if (document.activeElement === lastElement) {
          event.preventDefault();
          firstElement.focus();
        }
      }
    };

    if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
      window.addEventListener('keydown', handleKeyDown);
    }

    return () => {
      clearTimeout(focusTimer);
      if (typeof document !== 'undefined' && document.body?.style && originalOverflow !== undefined) {
        document.body.style.overflow = originalOverflow;
      }
      if (typeof window !== 'undefined' && typeof window.removeEventListener === 'function') {
        window.removeEventListener('keydown', handleKeyDown);
      }
      openerRef.current?.focus?.();
      openerRef.current = null;
    };
  }, [isVisible]);

  useEffect(() => {
    if (pinPromptOpen) {
      pinInputRef.current?.focus?.();
    }
  }, [pinPromptOpen]);

  if (!isVisible || !user) {
    return null;
  }

  const mainDisplayName = user.fullName || user.username;

  const requestAdultAccess = () => {
    if (busyProfileId !== null || pinBusy) return;
    if (!parentModeEnabled) {
      closeProfilePicker();
      return;
    }
    setError('');
    setPinError('');
    setPinPromptOpen(true);
  };

  const handlePinSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (pinBusy || busyProfileId !== null) return;
    const validationErr = validateParentPin(pin);
    if (validationErr) {
      setPinError(validationErr);
      return;
    }
    setPinBusy(true);
    setPinError('');
    try {
      const result = typeof exitKidMode === 'function' ? await exitKidMode(pin) : { success: true };
      if (result.success) {
        setPin('');
        setPinPromptOpen(false);
        closeProfilePicker();
      } else {
        setPin('');
        setPinError(result.message || 'Mã PIN phụ huynh chưa đúng.');
      }
    } finally {
      setPinBusy(false);
    }
  };

  const handleSelectChild = async (profileId: number) => {
    if (busyProfileId !== null || pinBusy) return;
    setBusyProfileId(profileId);
    setError('');
    try {
      const result = await selectChildProfile(profileId);
      if (result.success) {
        if (pathname === '/account' || pathname === '/gia-dinh' || pathname.startsWith('/admin')) {
          navigate('/', { replace: true });
        }
      } else {
        setError(result.message || 'Chưa chuyển được sang hồ sơ trẻ em.');
      }
    } finally {
      setBusyProfileId(null);
    }
  };

  return (
    <div
      ref={modalRef}
      role="dialog"
      aria-modal="true"
      aria-labelledby="profile-picker-title"
      data-testid="profile-picker-modal"
      onClick={(event) => {
        if (event.target === event.currentTarget && busyProfileId === null && !pinBusy) {
          requestAdultAccess();
        }
      }}
      className="fixed inset-0 z-[60] flex items-center justify-center bg-ink/75 p-4 backdrop-blur-sm animate-fade-in"
    >
      <div className="motion-dialog-enter relative max-h-[calc(100dvh-2rem)] w-full max-w-2xl overflow-y-auto rounded-2xl border-2 border-ink bg-paper-white p-6 shadow-brand-lg sm:p-8">
        <div className="flex items-start justify-between gap-3">
          <div>
            <span className="motion-chip-pop inline-flex rounded-full border border-ink/30 bg-mint/70 px-2.5 py-0.5 text-[0.6875rem] font-bold text-ink">
              Chọn hồ sơ xem phim
            </span>
            <h2 id="profile-picker-title" className="mt-2 font-display text-2xl font-bold text-ink">
              Ai đang xem Dippie?
            </h2>
            <p className="mt-1 text-sm text-muted">
              Chọn hồ sơ của bé để mở ngay, hoặc nhập mã PIN phụ huynh để vào tài khoản chính.
            </p>
          </div>
          <button
            type="button"
            disabled={busyProfileId !== null || pinBusy}
            onClick={requestAdultAccess}
            aria-label="Đóng chọn hồ sơ"
            className="inline-flex h-11 w-11 min-h-[44px] min-w-[44px] shrink-0 items-center justify-center rounded-lg text-sm font-semibold text-muted hover:bg-surface-elevated hover:text-ink cursor-pointer disabled:opacity-40"
          >
            ✕
          </button>
        </div>

        {error && (
          <div role="alert" className="mt-4 rounded-xl border border-red-800/35 bg-red-50 px-4 py-3 text-sm text-red-900">
            {error}
          </div>
        )}

        {pinPromptOpen ? (
          <form
            onSubmit={handlePinSubmit}
            data-testid="picker-parent-pin-form"
            className="mt-6 space-y-4 rounded-xl border-2 border-ink bg-surface p-5"
          >
            <div>
              <h3 className="text-base font-bold text-ink">Xác nhận mã PIN phụ huynh</h3>
              <p className="mt-1 text-sm leading-relaxed text-muted">
                Nhập mã PIN (4 đến 6 chữ số) để mở tài khoản chính <strong>{mainDisplayName}</strong>.
              </p>
            </div>
            <div>
              <label htmlFor="picker-parent-pin-input" className="mb-1.5 block text-xs font-semibold text-ink">
                Mã PIN phụ huynh
              </label>
              <input
                ref={pinInputRef}
                id="picker-parent-pin-input"
                data-testid="picker-parent-pin-input"
                type="password"
                inputMode="numeric"
                pattern="[0-9]*"
                enterKeyHint="done"
                autoComplete="off"
                autoFocus
                maxLength={6}
                required
                disabled={pinBusy}
                value={pin}
                onChange={(event) => {
                  setPin(event.target.value.replace(/[^0-9]/g, '').slice(0, 6));
                  if (pinError) setPinError('');
                }}
                placeholder="••••"
                className="w-full rounded-lg border border-surface-border bg-paper-white px-3.5 py-2.5 text-base tracking-[0.25em] text-ink focus:border-ink focus:outline-none disabled:opacity-50"
              />
            </div>

            {pinError && (
              <div
                role="alert"
                data-testid="picker-parent-pin-error"
                className="rounded-lg border border-red-800/35 bg-red-50 px-3.5 py-2.5 text-xs text-red-900"
              >
                {pinError}
              </div>
            )}

            <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
              <button
                type="button"
                data-testid="picker-cancel-pin-button"
                disabled={pinBusy}
                onClick={() => {
                  setPinPromptOpen(false);
                  setPin('');
                  setPinError('');
                }}
                className="btn-secondary min-h-[44px] w-full sm:w-auto text-xs font-semibold"
              >
                Quay lại chọn hồ sơ
              </button>
              <button
                type="submit"
                data-testid="picker-parent-pin-submit"
                disabled={pinBusy || pin.length < 4}
                className="btn-primary min-h-[44px] w-full sm:w-auto text-xs font-bold disabled:opacity-50"
              >
                {pinBusy ? 'Đang kiểm tra…' : 'Mở tài khoản chính'}
              </button>
            </div>
          </form>
        ) : (
          <>
            <div className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-3 sm:gap-4">
              {childProfiles.map((profile) => {
                const isBusy = busyProfileId === profile.id;
                return (
                  <button
                    key={profile.id}
                    type="button"
                    data-testid={`picker-child-profile-${profile.id}`}
                    disabled={busyProfileId !== null}
                    onClick={() => void handleSelectChild(profile.id)}
                    className="flex min-h-[140px] flex-col items-center justify-center gap-2.5 rounded-xl border-2 border-ink bg-paper-white p-4 text-center shadow-[3px_3px_0_#203328] transition-transform duration-smooth ease-out-smooth hover:-translate-y-1 hover:bg-[#f2d18c]/35 cursor-pointer disabled:opacity-50"
                  >
                    <ChildAvatarBadge avatarKey={profile.avatarKey} size="lg" />
                    <div className="min-w-0 w-full">
                      <p className="truncate text-sm font-bold text-ink">
                        {isBusy ? 'Đang chuyển…' : profile.name}
                      </p>
                      <p className="mt-0.5 text-[0.6875rem] font-medium text-brand-dark">Hồ sơ của bé</p>
                    </div>
                  </button>
                );
              })}

              <button
                type="button"
                data-testid="picker-main-profile"
                disabled={busyProfileId !== null}
                onClick={requestAdultAccess}
                className="flex min-h-[140px] flex-col items-center justify-center gap-2.5 rounded-xl border-2 border-ink bg-surface p-4 text-center shadow-[3px_3px_0_#203328] transition-transform duration-150 hover:-translate-y-0.5 hover:bg-mint/40 cursor-pointer disabled:opacity-50"
              >
                <div className="grid h-16 w-16 place-items-center rounded-2xl border-2 border-ink bg-brand text-lg font-bold text-ink">
                  {getUserInitials(mainDisplayName, 'P')}
                </div>
                <div className="min-w-0 w-full">
                  <p className="truncate text-sm font-bold text-ink">{mainDisplayName}</p>
                  <p className="mt-0.5 text-[0.6875rem] font-medium text-muted">Tài khoản chính (cần PIN)</p>
                </div>
              </button>
            </div>

            {childProfiles.length === 0 && (
              <div className="mt-4 rounded-xl border border-dashed border-surface-border bg-surface-muted p-4 text-center">
                <p className="text-sm text-muted">
                  Chưa có hồ sơ trẻ em nào. Mở tài khoản chính bằng mã PIN rồi vào mục{' '}
                  <Link to="/gia-dinh" className="font-bold text-ink underline">
                    Gia đình
                  </Link>{' '}
                  để tạo hồ sơ cho bé nhé.
                </p>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
