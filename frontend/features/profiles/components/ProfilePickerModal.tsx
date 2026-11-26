import React, { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { getUserInitials } from '@/lib/labels';
import { useAuth } from '@/lib/context/AuthContext';
import { ChildAvatarBadge } from '@/features/profiles/components/ChildAvatarBadge';

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
  } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [busyProfileId, setBusyProfileId] = useState<number | null>(null);
  const [error, setError] = useState('');
  const modalRef = useRef<HTMLDivElement | null>(null);
  const openerRef = useRef<HTMLElement | null>(null);
  const busyRef = useRef(false);
  busyRef.current = busyProfileId !== null;
  const closeRef = useRef(closeProfilePicker);
  closeRef.current = closeProfilePicker;

  const isVisible = Boolean(
    showProfilePicker && isAuthenticated && user && parentModeEnabled && !activeProfile
  );

  useEffect(() => {
    if (!isVisible) return;
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
      const firstTarget =
        modalRef.current?.querySelector<HTMLElement>('[data-testid="picker-main-profile"]') ||
        modalRef.current?.querySelector<HTMLElement>(FOCUSABLE_SELECTOR);
      firstTarget?.focus();
    }, 30);

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        if (!busyRef.current) {
          event.preventDefault();
          closeRef.current();
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

  if (!isVisible || !user) {
    return null;
  }

  const mainDisplayName = user.fullName || user.username;

  const handleSelectChild = async (profileId: number) => {
    if (busyProfileId !== null) return;
    setBusyProfileId(profileId);
    setError('');
    try {
      const result = await selectChildProfile(profileId);
      if (result.success) {
        if (pathname === '/account' || pathname.startsWith('/admin')) {
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
        if (event.target === event.currentTarget && busyProfileId === null) {
          closeProfilePicker();
        }
      }}
      className="fixed inset-0 z-[60] flex items-center justify-center bg-ink/75 p-4 backdrop-blur-sm animate-fade-in"
    >
      <div className="relative max-h-[calc(100dvh-2rem)] w-full max-w-2xl overflow-y-auto rounded-2xl border-2 border-ink bg-paper-white p-6 shadow-brand-lg sm:p-8">
        <div className="flex items-start justify-between gap-3">
          <div>
            <span className="inline-flex rounded-full border border-ink/30 bg-mint/70 px-2.5 py-0.5 text-[11px] font-bold text-ink">
              Chọn hồ sơ xem phim
            </span>
            <h2 id="profile-picker-title" className="mt-2 font-display text-2xl font-bold text-ink">
              Ai đang xem Dippie?
            </h2>
            <p className="mt-1 text-xs sm:text-sm text-muted">
              Mỗi hồ sơ lưu riêng danh sách xem tiếp, phim yêu thích và điểm chấm.
            </p>
          </div>
          <button
            type="button"
            disabled={busyProfileId !== null}
            onClick={closeProfilePicker}
            aria-label="Đóng chọn hồ sơ"
            className="inline-flex h-11 w-11 min-h-[44px] min-w-[44px] shrink-0 items-center justify-center rounded-lg text-sm font-semibold text-muted hover:bg-surface-elevated hover:text-ink cursor-pointer disabled:opacity-40"
          >
            ✕
          </button>
        </div>

        {error && (
          <div role="alert" className="mt-4 rounded-xl border border-red-800/35 bg-red-50 px-4 py-3 text-xs text-red-900">
            {error}
          </div>
        )}

        <div className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-3 sm:gap-4">
          <button
            type="button"
            data-testid="picker-main-profile"
            disabled={busyProfileId !== null}
            onClick={closeProfilePicker}
            className="flex min-h-[140px] flex-col items-center justify-center gap-2.5 rounded-xl border-2 border-ink bg-surface p-4 text-center shadow-[3px_3px_0_#203328] transition-transform duration-150 hover:-translate-y-0.5 hover:bg-mint/40 cursor-pointer disabled:opacity-50"
          >
            <div className="grid h-16 w-16 place-items-center rounded-2xl border-2 border-ink bg-brand text-lg font-bold text-ink">
              {getUserInitials(mainDisplayName, 'P')}
            </div>
            <div className="min-w-0 w-full">
              <p className="truncate text-sm font-bold text-ink">{mainDisplayName}</p>
              <p className="mt-0.5 text-[11px] font-medium text-muted">Tài khoản chính</p>
            </div>
          </button>

          {childProfiles.map((profile) => {
            const isBusy = busyProfileId === profile.id;
            return (
              <button
                key={profile.id}
                type="button"
                data-testid={`picker-child-profile-${profile.id}`}
                disabled={busyProfileId !== null}
                onClick={() => void handleSelectChild(profile.id)}
                className="flex min-h-[140px] flex-col items-center justify-center gap-2.5 rounded-xl border-2 border-ink bg-paper-white p-4 text-center shadow-[3px_3px_0_#203328] transition-transform duration-150 hover:-translate-y-0.5 hover:bg-[#f2d18c]/35 cursor-pointer disabled:opacity-50"
              >
                <ChildAvatarBadge avatarKey={profile.avatarKey} size="lg" />
                <div className="min-w-0 w-full">
                  <p className="truncate text-sm font-bold text-ink">
                    {isBusy ? 'Đang chuyển…' : profile.name}
                  </p>
                  <p className="mt-0.5 text-[11px] font-medium text-brand-dark">Hồ sơ trẻ em</p>
                </div>
              </button>
            );
          })}
        </div>

        {childProfiles.length === 0 && (
          <p className="mt-4 text-center text-xs text-muted">
            Chưa có hồ sơ trẻ em nào. Bạn có thể thêm hồ sơ trong mục Cài đặt tài khoản.
          </p>
        )}
      </div>
    </div>
  );
}
