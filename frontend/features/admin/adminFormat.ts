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

export interface DateFormatOptions {
  includeSeconds?: boolean;
  timeZone?: string;
}

export const AUDIT_ACTION_LABELS: Record<string, string> = {
  USER_ROLE_CHANGED: 'Đổi vai trò',
  USER_DISABLED: 'Khóa tài khoản',
  USER_ENABLED: 'Mở khóa tài khoản',
  CATALOG_IMPORTED: 'Nhập danh mục',
};

const UTC_LOCAL_DATETIME_RE = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d+)?)?$/;

export function getAuditActionLabel(action: string | null | undefined): string {
  if (typeof action !== 'string') return '—';
  const trimmed = action.trim();
  if (!trimmed) return '—';
  return AUDIT_ACTION_LABELS[trimmed] ?? trimmed;
}

export function getRoleLabel(role: string | null | undefined): string {
  if (role === 'ROLE_ADMIN') return 'Quản trị viên';
  if (role === 'ROLE_USER') return 'Người dùng';
  return typeof role === 'string' && role.trim() ? role.trim() : '—';
}

export function getUserStatusLabel(enabled: boolean): string {
  return enabled ? 'Đang hoạt động' : 'Đã khóa';
}

function parseTimestamp(value: string | null | undefined): Date | null {
  if (typeof value !== 'string') return null;
  const trimmed = value.trim();
  if (!trimmed) return null;
  const normalized = UTC_LOCAL_DATETIME_RE.test(trimmed) ? `${trimmed}Z` : trimmed;
  const parsed = Date.parse(normalized);
  if (!Number.isFinite(parsed)) return null;
  return new Date(parsed);
}

export function formatViDate(
  value: string | null | undefined,
  options: Pick<DateFormatOptions, 'timeZone'> = {}
): string {
  const date = parseTimestamp(value);
  if (!date) return typeof value === 'string' && value.trim() ? value.trim() : '—';
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    ...(options.timeZone ? { timeZone: options.timeZone } : {}),
  }).format(date);
}

export function formatViDateTime(
  value: string | null | undefined,
  options: DateFormatOptions = {}
): string {
  const date = parseTimestamp(value);
  if (!date) return typeof value === 'string' && value.trim() ? value.trim() : '—';
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    ...(options.includeSeconds ? { second: '2-digit' } : {}),
    ...(options.timeZone ? { timeZone: options.timeZone } : {}),
  }).format(date);
}

export function formatPercentage(rate: number, fractionDigits = 1): string {
  if (typeof rate !== 'number' || !Number.isFinite(rate) || rate <= 0) {
    return '0%';
  }
  const pct = rate * 100;
  const formatted = new Intl.NumberFormat('vi-VN', {
    minimumFractionDigits: 0,
    maximumFractionDigits: fractionDigits,
  }).format(pct);
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
  const cleanType = typeof targetType === 'string' ? targetType.trim() : '';
  const cleanId = typeof targetId === 'string' ? targetId.trim() : '';
  if (cleanType && cleanId) return `${cleanType}: ${cleanId}`;
  return cleanType || cleanId || '—';
}

function formatDetailValue(value: unknown): string {
  if (value === null || value === undefined) return '—';
  if (typeof value === 'boolean') return value ? 'true' : 'false';
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
