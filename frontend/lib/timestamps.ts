export interface DateFormatOptions {
  includeSeconds?: boolean;
  timeZone?: string;
}

const UTC_LOCAL_DATETIME_RE = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d+)?)?$/;

export function parseTimestamp(value: string | null | undefined): number {
  const trimmed = value?.trim() ?? '';
  return Date.parse(UTC_LOCAL_DATETIME_RE.test(trimmed) ? `${trimmed}Z` : trimmed);
}

export function formatViDate(
  value: string | null | undefined,
  options: Pick<DateFormatOptions, 'timeZone'> = {}
): string {
  const date = parseTimestamp(value);
  if (!Number.isFinite(date)) return typeof value === 'string' && value.trim() ? value.trim() : 'Chưa có';
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
  if (!Number.isFinite(date)) return typeof value === 'string' && value.trim() ? value.trim() : 'Chưa có';
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

