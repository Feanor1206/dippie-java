export { formatViDate, formatViDateTime } from '@/lib/timestamps';

export interface PagingMeta {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface PagingSummary {
  currentPage: number;
  totalPages: number;
  from: number;
  to: number;
  totalElements: number;
  canPrevious: boolean;
  canNext: boolean;
}

export const AUDIT_ACTION_LABELS: Record<string, string> = {
  USER_ROLE_CHANGED: 'Đổi vai trò',
  USER_DISABLED: 'Khóa tài khoản',
  USER_ENABLED: 'Mở khóa tài khoản',
  CATALOG_IMPORTED: 'Nhập dữ liệu',
  CATALOG_SNAPSHOT_RETRIED: 'Thử lại bản dữ liệu',
  CATALOG_EXPORTED: 'Xuất dữ liệu',
  ADMIN_PASSWORD_CHANGED: 'Quản trị viên đổi mật khẩu',
  ADMIN_BOOTSTRAP_UPDATED: 'Cập nhật tài khoản quản trị gốc',
  ACCOUNT_DELETED: 'Xóa tài khoản',
  PASSWORD_RESET_COMPLETED: 'Đặt lại mật khẩu',
  PLAYBACK_REPORT_UPDATED: 'Xử lý báo lỗi video',
};

export const AUDIT_TARGET_LABELS: Record<string, string> = {
  USER: 'Tài khoản',
  CATALOG_PACKAGE: 'Gói dữ liệu',
  CATALOG_SNAPSHOT: 'Bản dữ liệu',
  PLAYBACK_REPORT: 'Báo lỗi video',
};

export function getAuditActionLabel(action: string | null | undefined): string {
  if (typeof action !== 'string') return 'Không có';
  const trimmed = action.trim();
  if (!trimmed) return 'Không có';
  return AUDIT_ACTION_LABELS[trimmed] ?? trimmed;
}

export function getRoleLabel(role: string | null | undefined): string {
  if (role === 'ROLE_ADMIN') return 'Quản trị viên';
  if (role === 'ROLE_USER') return 'Người dùng';
  return typeof role === 'string' && role.trim() ? role.trim() : 'Chưa có';
}

export function getUserStatusLabel(enabled: boolean): string {
  return enabled ? 'Đang hoạt động' : 'Đã khóa';
}

export function formatPercentage(rate: number, fractionDigits = 1): string {
  if (typeof rate !== 'number' || !Number.isFinite(rate) || rate <= 0) {
    return '0%';
  }
  const percent = rate * 100;
  const formatted = new Intl.NumberFormat('vi-VN', {
    minimumFractionDigits: 0,
    maximumFractionDigits: fractionDigits,
  }).format(percent);
  return `${formatted}%`;
}

export function formatRating(value: number): string {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return '0.0';
  }
  return value.toFixed(1);
}

export function formatCount(value: number): string {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return '0';
  }
  return new Intl.NumberFormat('vi-VN').format(value);
}

export function getPagingSummary(meta: PagingMeta): PagingSummary {
  const safeTotal = Number.isFinite(meta.totalElements) && meta.totalElements > 0 ? meta.totalElements : 0;
  const safeSize = Number.isFinite(meta.size) && meta.size > 0 ? meta.size : 20;
  const safePage = Number.isFinite(meta.page) && meta.page >= 0 ? meta.page : 0;
  const safeTotalPages = Number.isFinite(meta.totalPages) && meta.totalPages >= 0 ? meta.totalPages : 0;
  const from = safeTotal === 0 ? 0 : safePage * safeSize + 1;
  const to = safeTotal === 0 ? 0 : Math.min((safePage + 1) * safeSize, safeTotal);
  return {
    currentPage: safeTotalPages > 0 ? safePage + 1 : 0,
    totalPages: safeTotalPages,
    from,
    to,
    totalElements: safeTotal,
    canPrevious: Boolean(meta.hasPrevious) && safePage > 0,
    canNext: Boolean(meta.hasNext),
  };
}

export function getNextPage(meta: PagingMeta): number {
  return meta.hasNext ? meta.page + 1 : meta.page;
}

export function getPreviousPage(meta: PagingMeta): number {
  return meta.hasPrevious && meta.page > 0 ? meta.page - 1 : meta.page;
}

export function formatAuditTarget(
  targetType: string | null | undefined,
  targetId: string | null | undefined
): string {
  const rawType = typeof targetType === 'string' ? targetType.trim() : '';
  const cleanType = AUDIT_TARGET_LABELS[rawType] ?? rawType;
  const cleanId = typeof targetId === 'string' ? targetId.trim() : '';
  if (cleanType && cleanId) return `${cleanType}: ${cleanId}`;
  return cleanType || cleanId || 'Không có';
}

function formatDetailValue(value: unknown): string {
  if (value === null || value === undefined) return 'Không có';
  if (typeof value === 'boolean') return value ? 'Có' : 'Không';
  if (typeof value === 'number' || typeof value === 'string') return String(value);
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

export function formatAuditDetails(
  details: Record<string, unknown> | null | undefined
): Array<{ key: string; value: string }> {
  if (!details || typeof details !== 'object' || Array.isArray(details)) {
    return [];
  }
  return Object.entries(details)
    .filter(([key]) => key.trim().length > 0)
    .map(([key, value]) => ({
      key,
      value: formatDetailValue(value),
    }));
}
