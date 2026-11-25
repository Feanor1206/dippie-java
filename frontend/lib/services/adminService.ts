const API = '/api';
const DEFAULT_TIMEOUT_MS = 7000;

export type AdminUserRole = 'ROLE_USER' | 'ROLE_ADMIN';

export interface AdminUserView {
  id: number;
  username: string;
  email: string;
  fullName: string | null;
  role: AdminUserRole | string;
  enabled: boolean;
  createdAt: string;
}

export interface AdminPageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface TopWatchedEpisodeView {
  episodeId: string;
  title: string;
  viewers: number;
}

export interface TopRatedMovieView {
  movieId: string;
  title: string;
  averageRating: number;
  ratingCount: number;
}

export interface AdminStatsResponse {
  totalUsers: number;
  activeUsers: number;
  totalMovies: number;
  publishedEpisodes: number;
  totalWatchSessions: number;
  completedWatchSessions: number;
  completionRate: number;
  topWatchedEpisodes: TopWatchedEpisodeView[];
  topRatedMovies: TopRatedMovieView[];
  newUsersLast7Days: number;
}

export interface AdminAuditEventView {
  id: number;
  actorUsername: string;
  action: string;
  targetType: string;
  targetId: string;
  details: Record<string, unknown>;
  createdAt: string;
}

export interface AdminRequestOptions {
  signal?: AbortSignal;
  timeoutMs?: number;
}

export interface AdminUsersListOptions extends AdminRequestOptions {
  query?: string;
  page?: number;
  size?: number;
}

export interface AdminAuditListOptions extends AdminRequestOptions {
  page?: number;
  size?: number;
}

export class AdminApiError extends Error {
  status: number;
  data?: unknown;
  isCanceled: boolean;
  isTimeout: boolean;

  constructor(
    message: string,
    status: number,
    data?: unknown,
    options?: { isCanceled?: boolean; isTimeout?: boolean }
  ) {
    super(message);
    this.name = options?.isCanceled ? 'AbortError' : 'AdminApiError';
    this.status = status;
    this.data = data;
    this.isCanceled = Boolean(options?.isCanceled);
    this.isTimeout = Boolean(options?.isTimeout);
  }
}

function buildAuthHeaders(token: string, hasJsonBody = false): Record<string, string> {
  const cleanToken = typeof token === 'string' ? token.trim() : '';
  if (!cleanToken) {
    throw new AdminApiError('Chưa xác thực người dùng.', 401);
  }
  return {
    Accept: 'application/json',
    ...(hasJsonBody ? { 'Content-Type': 'application/json' } : {}),
    Authorization: `Bearer ${cleanToken}`,
  };
}

async function executeWithLifecycle<T>(
  action: (signal: AbortSignal) => Promise<T>,
  options: AdminRequestOptions = {}
): Promise<T> {
  const timeoutMs = typeof options.timeoutMs === 'number' && options.timeoutMs > 0
    ? options.timeoutMs
    : DEFAULT_TIMEOUT_MS;

  if (options.signal?.aborted) {
    throw new AdminApiError('Yêu cầu đã bị hủy bởi người dùng.', 499, undefined, { isCanceled: true });
  }

  const controller = new AbortController();
  let timedOut = false;
  let callerCanceled = false;

  const abortListener = () => {
    callerCanceled = true;
    controller.abort(options.signal?.reason || new Error('Request aborted by caller'));
  };

  if (options.signal) {
    options.signal.addEventListener('abort', abortListener);
  }

  let timer: ReturnType<typeof setTimeout> | null = null;
  timer = setTimeout(() => {
    timedOut = true;
    controller.abort(new Error(`Admin request timed out after ${timeoutMs}ms`));
  }, timeoutMs);

  try {
    return await action(controller.signal);
  } catch (err: any) {
    if (callerCanceled || options.signal?.aborted) {
      throw new AdminApiError('Yêu cầu đã bị hủy bởi người dùng.', 499, undefined, { isCanceled: true });
    }
    if (timedOut || (err?.name === 'AbortError' && !callerCanceled)) {
      throw new AdminApiError('Yêu cầu quản trị quá thời gian phản hồi.', 408, undefined, { isTimeout: true });
    }
    if (err instanceof AdminApiError) {
      throw err;
    }
    throw new AdminApiError(
      err?.message ? `Không thể kết nối đến máy chủ quản trị: ${err.message}` : 'Không thể kết nối đến máy chủ quản trị.',
      0,
      err
    );
  } finally {
    if (timer !== null) {
      clearTimeout(timer);
    }
    if (options.signal) {
      options.signal.removeEventListener('abort', abortListener);
    }
  }
}

async function readEnvelope<T>(res: Response, fallbackLabel: string): Promise<T> {
  if (!res.ok) {
    let errBody: any = null;
    try {
      errBody = await res.json();
    } catch {
    }
    const serverMessage = typeof errBody?.message === 'string' && errBody.message.trim()
      ? errBody.message.trim()
      : `${fallbackLabel} thất bại (HTTP ${res.status}).`;
    throw new AdminApiError(serverMessage, res.status, errBody);
  }

  let body: any;
  try {
    body = await res.json();
  } catch {
    throw new AdminApiError('Máy chủ phản hồi định dạng dữ liệu không hợp lệ.', 502);
  }

  if (!body || typeof body !== 'object' || body.success !== true || !Object.prototype.hasOwnProperty.call(body, 'data')) {
    const message = typeof body?.message === 'string' && body.message.trim()
      ? body.message.trim()
      : `Phản hồi ${fallbackLabel.toLowerCase()} không thành công.`;
    throw new AdminApiError(message, res.status || 502, body);
  }

  return body.data as T;
}

function parseFiniteNumber(value: unknown, label: string): number {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    throw new AdminApiError(`Dữ liệu ${label} không hợp lệ.`, 502);
  }
  return value;
}

function parseRequiredString(value: unknown, label: string): string {
  if (typeof value !== 'string' || !value.trim()) {
    throw new AdminApiError(`Dữ liệu ${label} thiếu hoặc không hợp lệ.`, 502);
  }
  return value.trim();
}

function normalizeAdminUser(raw: any): AdminUserView {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    throw new AdminApiError('Dữ liệu người dùng không hợp lệ.', 502);
  }
  const id = parseFiniteNumber(raw.id, 'ID người dùng');
  const username = parseRequiredString(raw.username, 'tên đăng nhập');
  const email = parseRequiredString(raw.email, 'email');
  const role = parseRequiredString(raw.role, 'vai trò');
  if (typeof raw.enabled !== 'boolean') {
    throw new AdminApiError('Dữ liệu trạng thái người dùng không hợp lệ.', 502);
  }
  const fullName = typeof raw.fullName === 'string' && raw.fullName.trim() ? raw.fullName.trim() : null;
  const createdAt = typeof raw.createdAt === 'string' ? raw.createdAt : '';
  return {
    id,
    username,
    email,
    fullName,
    role,
    enabled: raw.enabled,
    createdAt,
  };
}

function normalizeAuditEvent(raw: any): AdminAuditEventView {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    throw new AdminApiError('Dữ liệu sự kiện kiểm toán không hợp lệ.', 502);
  }
  const id = parseFiniteNumber(raw.id, 'ID sự kiện');
  const actorUsername = parseRequiredString(raw.actorUsername, 'người thực hiện');
  const action = parseRequiredString(raw.action, 'hành động');
  const targetType = typeof raw.targetType === 'string' ? raw.targetType.trim() : '';
  const targetId = typeof raw.targetId === 'string' ? raw.targetId.trim() : '';
  const details = raw.details && typeof raw.details === 'object' && !Array.isArray(raw.details)
    ? (raw.details as Record<string, unknown>)
    : {};
  const createdAt = typeof raw.createdAt === 'string' ? raw.createdAt : '';
  return {
    id,
    actorUsername,
    action,
    targetType,
    targetId,
    details,
    createdAt,
  };
}

function normalizePageResponse<T>(raw: any, itemNormalizer: (item: any) => T): AdminPageResponse<T> {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw) || !Array.isArray(raw.items)) {
    throw new AdminApiError('Dữ liệu phân trang không hợp lệ.', 502);
  }
  if (
    typeof raw.page !== 'number' || !Number.isFinite(raw.page) || raw.page < 0 ||
    typeof raw.size !== 'number' || !Number.isFinite(raw.size) || raw.size < 1 ||
    typeof raw.totalElements !== 'number' || !Number.isFinite(raw.totalElements) || raw.totalElements < 0 ||
    typeof raw.totalPages !== 'number' || !Number.isFinite(raw.totalPages) || raw.totalPages < 0 ||
    typeof raw.hasNext !== 'boolean' ||
    typeof raw.hasPrevious !== 'boolean'
  ) {
    throw new AdminApiError('Thông tin phân trang không hợp lệ.', 502);
  }
  return {
    items: raw.items.map(itemNormalizer),
    page: raw.page,
    size: raw.size,
    totalElements: raw.totalElements,
    totalPages: raw.totalPages,
    hasNext: raw.hasNext,
    hasPrevious: raw.hasPrevious,
  };
}

function normalizeAdminStats(raw: any): AdminStatsResponse {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    throw new AdminApiError('Dữ liệu thống kê không hợp lệ.', 502);
  }
  if (!Array.isArray(raw.topWatchedEpisodes) || !Array.isArray(raw.topRatedMovies)) {
    throw new AdminApiError('Dữ liệu bảng xếp hạng thống kê không hợp lệ.', 502);
  }
  const topWatchedEpisodes: TopWatchedEpisodeView[] = raw.topWatchedEpisodes.map((item: any) => {
    if (!item || typeof item !== 'object' || Array.isArray(item)) {
      throw new AdminApiError('Dữ liệu tập phim xem nhiều không hợp lệ.', 502);
    }
    return {
      episodeId: parseRequiredString(item.episodeId, 'ID tập phim'),
      title: parseRequiredString(item.title, 'tên tập phim'),
      viewers: parseFiniteNumber(item.viewers, 'số lượt xem'),
    };
  });
  const topRatedMovies: TopRatedMovieView[] = raw.topRatedMovies.map((item: any) => {
    if (!item || typeof item !== 'object' || Array.isArray(item)) {
      throw new AdminApiError('Dữ liệu phim đánh giá cao không hợp lệ.', 502);
    }
    return {
      movieId: parseRequiredString(item.movieId, 'ID phim'),
      title: parseRequiredString(item.title, 'tên phim'),
      averageRating: parseFiniteNumber(item.averageRating, 'điểm trung bình'),
      ratingCount: parseFiniteNumber(item.ratingCount, 'số lượt đánh giá'),
    };
  });
  return {
    totalUsers: parseFiniteNumber(raw.totalUsers, 'tổng người dùng'),
    activeUsers: parseFiniteNumber(raw.activeUsers, 'người dùng hoạt động'),
    totalMovies: parseFiniteNumber(raw.totalMovies, 'tổng số phim'),
    publishedEpisodes: parseFiniteNumber(raw.publishedEpisodes, 'tập đã phát hành'),
    totalWatchSessions: parseFiniteNumber(raw.totalWatchSessions, 'tổng phiên xem'),
    completedWatchSessions: parseFiniteNumber(raw.completedWatchSessions, 'phiên xem hoàn thành'),
    completionRate: parseFiniteNumber(raw.completionRate, 'tỷ lệ hoàn thành'),
    topWatchedEpisodes,
    topRatedMovies,
    newUsersLast7Days: parseFiniteNumber(raw.newUsersLast7Days, 'người dùng mới 7 ngày'),
  };
}

export async function listAdminUsers(
  token: string,
  options: AdminUsersListOptions = {},
  baseUrl = API
): Promise<AdminPageResponse<AdminUserView>> {
  const headers = buildAuthHeaders(token);
  const params = new URLSearchParams();
  const cleanQuery = typeof options.query === 'string' ? options.query.trim() : '';
  params.set('query', cleanQuery);
  params.set('page', String(typeof options.page === 'number' && Number.isFinite(options.page) ? options.page : 0));
  params.set('size', String(typeof options.size === 'number' && Number.isFinite(options.size) ? options.size : 20));
  const url = `${baseUrl}/admin/users?${params.toString()}`;

  return executeWithLifecycle(async (signal) => {
    const res = await fetch(url, { method: 'GET', headers, signal });
    const data = await readEnvelope<any>(res, 'Tải danh sách người dùng');
    return normalizePageResponse(data, normalizeAdminUser);
  }, options);
}

export async function updateAdminUserRole(
  token: string,
  userId: number,
  role: AdminUserRole,
  options: AdminRequestOptions = {},
  baseUrl = API
): Promise<AdminUserView> {
  if (!Number.isSafeInteger(userId) || userId <= 0) {
    throw new AdminApiError('ID người dùng không hợp lệ.', 400);
  }
  const headers = buildAuthHeaders(token, true);
  const url = `${baseUrl}/admin/users/${encodeURIComponent(String(userId))}/role`;

  return executeWithLifecycle(async (signal) => {
    const res = await fetch(url, {
      method: 'PATCH',
      headers,
      body: JSON.stringify({ role }),
      signal,
    });
    const data = await readEnvelope<any>(res, 'Cập nhật vai trò người dùng');
    return normalizeAdminUser(data);
  }, options);
}

export async function updateAdminUserStatus(
  token: string,
  userId: number,
  enabled: boolean,
  options: AdminRequestOptions = {},
  baseUrl = API
): Promise<AdminUserView> {
  if (!Number.isSafeInteger(userId) || userId <= 0) {
    throw new AdminApiError('ID người dùng không hợp lệ.', 400);
  }
  const headers = buildAuthHeaders(token, true);
  const url = `${baseUrl}/admin/users/${encodeURIComponent(String(userId))}/status`;

  return executeWithLifecycle(async (signal) => {
    const res = await fetch(url, {
      method: 'PATCH',
      headers,
      body: JSON.stringify({ enabled }),
      signal,
    });
    const data = await readEnvelope<any>(res, 'Cập nhật trạng thái người dùng');
    return normalizeAdminUser(data);
  }, options);
}

export async function getAdminStats(
  token: string,
  options: AdminRequestOptions = {},
  baseUrl = API
): Promise<AdminStatsResponse> {
  const headers = buildAuthHeaders(token);
  const url = `${baseUrl}/admin/stats`;

  return executeWithLifecycle(async (signal) => {
    const res = await fetch(url, { method: 'GET', headers, signal });
    const data = await readEnvelope<any>(res, 'Tải dữ liệu thống kê');
    return normalizeAdminStats(data);
  }, options);
}

export async function listAdminAuditEvents(
  token: string,
  options: AdminAuditListOptions = {},
  baseUrl = API
): Promise<AdminPageResponse<AdminAuditEventView>> {
  const headers = buildAuthHeaders(token);
  const params = new URLSearchParams();
  params.set('page', String(typeof options.page === 'number' && Number.isFinite(options.page) ? options.page : 0));
  params.set('size', String(typeof options.size === 'number' && Number.isFinite(options.size) ? options.size : 20));
  const url = `${baseUrl}/admin/audit-events?${params.toString()}`;

  return executeWithLifecycle(async (signal) => {
    const res = await fetch(url, { method: 'GET', headers, signal });
    const data = await readEnvelope<any>(res, 'Tải nhật ký kiểm toán');
    return normalizePageResponse(data, normalizeAuditEvent);
  }, options);
}
