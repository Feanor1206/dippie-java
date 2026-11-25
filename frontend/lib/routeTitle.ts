const EXACT_ROUTE_TITLES: Record<string, string> = {
  '/': 'Dippie · Phim hoạt hình',
  '/series': 'Phim bộ · Dippie',
  '/search': 'Kho phim · Dippie',
  '/profile': 'Thư viện · Dippie',
  '/dieu-khoan': 'Điều khoản sử dụng · Dippie',
  '/quyen-rieng-tu': 'Chính sách quyền riêng tư · Dippie',
  '/dat-lai-mat-khau': 'Đặt lại mật khẩu · Dippie',
  '/quen-mat-khau': 'Quên mật khẩu · Dippie',
  '/lich-su': 'Lịch sử xem · Dippie',
  '/admin/reports': 'Báo lỗi video · Dippie',
  '/account': 'Cài đặt tài khoản · Dippie',
  '/admin/import': 'Nhập dữ liệu · Dippie',
  '/admin/users': 'Quản lý người dùng · Dippie',
  '/admin/stats': 'Thống kê · Dippie',
  '/admin/audit': 'Nhật ký hệ thống · Dippie',
};

export function resolveRouteTitle(pathname: string): string {
  const normalized = pathname.length > 1 && pathname.endsWith('/') ? pathname.slice(0, -1) : pathname;
  if (Object.prototype.hasOwnProperty.call(EXACT_ROUTE_TITLES, normalized)) {
    return EXACT_ROUTE_TITLES[normalized];
  }
  if (normalized.startsWith('/series/')) {
    return 'Xem phim bộ · Dippie';
  }
  if (normalized.startsWith('/movies/')) {
    return 'Chi tiết phim · Dippie';
  }
  return 'Không tìm thấy trang · Dippie';
}
