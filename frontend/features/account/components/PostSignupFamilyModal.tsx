import React, { useEffect } from 'react';
import * as ReactRouter from 'react-router';
import { SiteDialog } from '@/components/ui/SiteDialog';
import { useAuth } from '@/lib/context/AuthContext';

export function PostSignupFamilyModal() {
  const auth = useAuth();
  const navigate = ReactRouter.useNavigate();
  const location = typeof ReactRouter.useLocation === 'function' ? ReactRouter.useLocation() : null;
  const pathname = location?.pathname || '';
  const isOnFamilyRoute = pathname === '/gia-dinh' || pathname.startsWith('/gia-dinh/');

  const shouldOffer = Boolean(
    auth.showPostSignupFamilyOffer && auth.isAuthenticated && auth.user && !auth.isKidMode
  );

  useEffect(() => {
    if (shouldOffer && isOnFamilyRoute) {
      auth.dismissPostSignupFamilyOffer?.();
    }
  }, [shouldOffer, isOnFamilyRoute, auth]);

  if (!shouldOffer || isOnFamilyRoute) return null;

  const handleClose = () => {
    auth.dismissPostSignupFamilyOffer?.();
  };

  const handleStartSetup = () => {
    auth.dismissPostSignupFamilyOffer?.();
    navigate('/gia-dinh?setup=1');
  };

  return (
    <SiteDialog title="Chào mừng gia đình đến với Dippie!" size="md" onClose={handleClose}>
      <div data-testid="post-signup-family-modal" className="space-y-4">
        <p className="text-base leading-relaxed text-ink">
          Tài khoản đã tạo xong! Bạn có muốn thiết lập góc xem phim riêng cho bé ngay bây giờ không?
        </p>
        <ul className="space-y-1.5 rounded-xl border border-surface-border bg-surface-muted p-3.5 text-sm text-muted">
          <li>• Đặt mã PIN phụ huynh từ 4–6 chữ số</li>
          <li>• Tạo hồ sơ tên và hình đại diện riêng cho bé</li>
          <li>• Hẹn giờ xem mỗi ngày và chọn câu hỏi theo khối lớp</li>
        </ul>
        <div className="flex flex-col-reverse gap-2 pt-1 sm:flex-row sm:justify-end">
          <button
            type="button"
            data-testid="post-signup-skip-family-setup"
            onClick={handleClose}
            className="btn-secondary min-h-[44px] w-full sm:w-auto text-sm"
          >
            Để sau, xem phim trước
          </button>
          <button
            type="button"
            data-testid="post-signup-start-family-setup"
            onClick={handleStartSetup}
            className="btn-primary min-h-[44px] w-full sm:w-auto text-sm font-bold"
          >
            Thiết lập gia đình ngay
          </button>
        </div>
      </div>
    </SiteDialog>
  );
}
