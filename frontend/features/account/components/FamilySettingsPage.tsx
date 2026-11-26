import React, { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useAuth } from '@/lib/context/AuthContext';
import * as motion from '@/lib/motion';
import { MAX_CHILD_PROFILES } from '@/lib/services/profileService';
import { ChildAvatarBadge } from '@/features/profiles/components/ChildAvatarBadge';
import { ExitKidModeDialog } from '@/features/profiles/components/ExitKidModeDialog';
import { ChildProfilesSection } from './ChildProfilesSection';
import { FamilySetupWizard } from './FamilySetupWizard';

export function FamilySettingsPage() {
  const {
    user,
    token,
    status,
    isAuthenticated,
    isLoading,
    isKidMode,
    activeProfile,
    parentModeEnabled,
    childProfiles,
    openAuthModal,
    retryVerify,
  } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const kidMode = Boolean(isKidMode);
  const setupRequested = searchParams.get('setup') === '1';

  const [wizardOpen, setWizardOpen] = useState<boolean>(false);
  const [wizardDismissedManually, setWizardDismissedManually] = useState(false);
  const [sessionPin, setSessionPin] = useState('');
  const [exitDialogOpen, setExitDialogOpen] = useState(false);
  const pageRef = useRef<HTMLElement | null>(null);

  motion.useScrollReveal?.(pageRef, [isLoading, isAuthenticated, kidMode, wizardOpen]);

  useEffect(() => {
    if (setupRequested) {
      setWizardOpen(true);
      setWizardDismissedManually(false);
    }
  }, [setupRequested]);

  const shouldShowWizard =
    wizardOpen || (!wizardDismissedManually && (!parentModeEnabled || childProfiles.length === 0));

  const closeWizardToFullSettings = () => {
    setWizardOpen(false);
    setWizardDismissedManually(true);
    if (searchParams.has('setup')) {
      const next = new URLSearchParams(searchParams);
      next.delete('setup');
      setSearchParams(next, { replace: true });
    }
  };

  return (
    <main ref={pageRef} id="main-content" tabIndex={-1} className="page-shell min-h-[75vh] py-8 sm:py-12">
      {status === 'unavailable' && (
        <div
          role="alert"
          className="mb-6 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 rounded-xl border border-amber-500/30 bg-amber-500/10 p-4 text-amber-900"
        >
          <div className="text-sm">
            <span className="font-bold">Chưa kết nối được máy chủ:</span> Bạn thử kết nối lại để mở cài đặt gia đình.
          </div>
          <button
            type="button"
            onClick={() => void retryVerify()}
            className="btn-secondary min-h-[44px] px-3.5 text-xs font-semibold"
          >
            Thử lại
          </button>
        </div>
      )}

      {isLoading ? (
        <div role="status" aria-live="polite" className="mx-auto max-w-4xl space-y-6">
          <p className="sr-only">Đang tải cài đặt gia đình…</p>
          <div className="h-36 rounded-xl border border-surface-border bg-surface p-6 animate-pulse" />
          <div className="h-72 rounded-xl border border-surface-border bg-surface p-6 animate-pulse" />
        </div>
      ) : !isAuthenticated || !user || !token ? (
        <section
          data-testid="family-guest-overview"
          aria-labelledby="family-guest-heading"
          className="mx-auto max-w-3xl rounded-2xl border-2 border-ink bg-paper-white p-6 shadow-brand sm:p-8"
        >
          <span className="inline-flex rounded-full border border-ink bg-mint px-2.5 py-0.5 text-xs font-bold text-ink">
            Dành cho gia đình
          </span>
          <h1 id="family-guest-heading" className="mt-2 font-display text-2xl sm:text-3xl font-bold text-ink">
            Góc xem hoạt hình vừa vui vừa yên tâm cho cả nhà
          </h1>
          <p className="mt-3 text-base leading-relaxed text-muted">
            Khi đăng nhập, ba mẹ có thể tạo hồ sơ riêng cho từng bé với thời lượng xem hợp lý và câu hỏi ôn bài nhẹ nhàng theo đúng độ tuổi.
          </p>
          <ul className="mt-5 grid grid-cols-1 gap-3 sm:grid-cols-2 text-sm text-ink">
            <li className="rounded-xl border border-surface-border bg-surface p-4">
              <strong className="block font-bold">Hồ sơ riêng &amp; Mã PIN phụ huynh</strong>
              <span className="mt-1 block text-muted">Lưu riêng lịch sử xem của từng bé, cần mã PIN khi đổi về tài khoản người lớn.</span>
            </li>
            <li className="rounded-xl border border-surface-border bg-surface p-4">
              <strong className="block font-bold">Hẹn giờ xem &amp; Nhắc nghỉ mắt</strong>
              <span className="mt-1 block text-muted">Bạn đồng hành Dieppie nhắc bé nghỉ mắt định kỳ, hết giờ có thể trả lời câu hỏi để cộng thêm phút.</span>
            </li>
            <li className="rounded-xl border border-surface-border bg-surface p-4">
              <strong className="block font-bold">Câu hỏi theo lớp &amp; Nhập tệp CSV</strong>
              <span className="mt-1 block text-muted">Có sẵn câu hỏi từ Mẫu giáo tới Lớp 5, hỗ trợ ba mẹ tự thêm hoặc tải lên từ bảng tính Excel.</span>
            </li>
            <li className="rounded-xl border border-surface-border bg-surface p-4">
              <strong className="block font-bold">Phim lẻ cuối tuần &amp; Nhiệm vụ</strong>
              <span className="mt-1 block text-muted">Khuyến khích bé giữ thói quen tốt trong tuần để mở rạp phim cuối tuần cùng cả nhà.</span>
            </li>
          </ul>
          <div className="mt-6 flex flex-wrap items-center gap-3">
            <button
              type="button"
              data-testid="family-guest-register-button"
              onClick={() => openAuthModal('register')}
              className="btn-primary min-h-[44px] text-sm font-bold"
            >
              Tạo tài khoản gia đình
            </button>
            <button
              type="button"
              data-testid="family-guest-login-button"
              onClick={() => openAuthModal('login')}
              className="btn-secondary min-h-[44px] text-sm font-semibold"
            >
              Đăng nhập
            </button>
          </div>
        </section>
      ) : kidMode ? (
        <section
          data-testid="family-kid-mode-blocked"
          aria-labelledby="family-kid-mode-heading"
          className="mx-auto max-w-2xl rounded-2xl border-2 border-ink bg-paper-white p-6 shadow-brand sm:p-8"
        >
          <div className="flex items-center gap-4">
            <ChildAvatarBadge avatarKey={activeProfile?.avatarKey} size="md" />
            <div>
              <span className="text-xs font-bold text-brand-dark">Hồ sơ trẻ em</span>
              <h1 id="family-kid-mode-heading" className="mt-0.5 text-xl sm:text-2xl font-bold text-ink">
                Mục Gia đình dành riêng cho ba mẹ
              </h1>
            </div>
          </div>
          <p className="mt-4 text-base leading-relaxed text-muted">
            Ba mẹ nhập mã PIN phụ huynh để thoát hồ sơ trẻ em trước khi chỉnh giờ xem hoặc bộ câu hỏi của các bé nhé.
          </p>
          <div className="mt-6 flex flex-wrap items-center gap-3">
            <button
              type="button"
              data-testid="family-exit-kid-mode-button"
              onClick={() => setExitDialogOpen(true)}
              className="btn-primary min-h-[44px] text-sm font-bold"
            >
              Thoát hồ sơ trẻ em
            </button>
            <Link to="/" className="btn-secondary min-h-[44px] text-sm">
              Về trang chủ
            </Link>
          </div>
          {exitDialogOpen && (
            <ExitKidModeDialog open={exitDialogOpen} onClose={() => setExitDialogOpen(false)} />
          )}
        </section>
      ) : (
        <div className="mx-auto max-w-4xl space-y-8">
          <div className="rounded-xl border border-surface-border bg-surface p-6 sm:p-8">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <span className="text-xs font-bold text-brand-dark">Không gian phụ huynh</span>
                <h1 className="mt-0.5 font-display text-2xl sm:text-3xl font-bold text-ink">
                  Gia đình &amp; Hồ sơ của bé
                </h1>
                <p className="mt-1 text-base leading-relaxed text-muted">
                  Quản lý hồ sơ các bé, thời gian xem mỗi ngày, bộ câu hỏi theo lớp và các hoạt động thưởng giờ.
                </p>
              </div>
              <div className="flex flex-wrap items-center gap-2.5">
                {!shouldShowWizard && childProfiles.length < MAX_CHILD_PROFILES && (
                  <button
                    type="button"
                    data-testid="open-family-wizard-button"
                    onClick={() => {
                      setWizardDismissedManually(false);
                      setWizardOpen(true);
                    }}
                    className="btn-primary min-h-[44px] text-xs sm:text-sm font-bold"
                  >
                    Thiết lập từng bước
                  </button>
                )}
                <Link to="/account" className="btn-secondary min-h-[44px] text-xs sm:text-sm">
                  Tài khoản &amp; Mật khẩu
                </Link>
              </div>
            </div>
          </div>

          {shouldShowWizard && (
            <FamilySetupWizard
              sessionPin={sessionPin}
              onPinVerified={setSessionPin}
              onCancel={closeWizardToFullSettings}
            />
          )}

          <ChildProfilesSection />
        </div>
      )}
    </main>
  );
}
