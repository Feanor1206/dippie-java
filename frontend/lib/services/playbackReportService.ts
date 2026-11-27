import { apiFetch, readApiMessage, withRequestTimeout } from '@/lib/services/apiRequest';

export const PLAYBACK_REPORT_REASONS = {
  khong_phat: 'Không phát được video',
  sai_tap: 'Sai tập',
  am_thanh: 'Lỗi âm thanh',
  noi_dung_khong_phu_hop: 'Nội dung chưa phù hợp',
  khac: 'Lý do khác',
} as const;

export type ReportReason = keyof typeof PLAYBACK_REPORT_REASONS;
export type ReportResolution = 'resolved' | 'dismissed';

type PlaybackReportDraft = {
  episodeId: string;
  assetId: string | null;
  reason: ReportReason;
  note?: string;
};

export type PlaybackReport = Omit<PlaybackReportDraft, 'note'> & {
  id: number;
  seriesTitle: string;
  episodeTitle: string;
  note: string | null;
  status: 'open';
  createdAt: string;
};

const UNAVAILABLE = 'Tính năng báo lỗi video tạm chưa bật trên máy chủ.';

function isId(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0;
}

function isEpisodeId(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function isReason(value: unknown): value is ReportReason {
  return typeof value === 'string' && Object.hasOwn(PLAYBACK_REPORT_REASONS, value);
}

function headers(token?: string | null, json = false): Record<string, string> {
  return {
    Accept: 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(json ? { 'Content-Type': 'application/json' } : {}),
  };
}

async function requireResponse(response: Response, status: number, fallback: string) {
  if (response.status === status) return;
  const message = response.status === 404 || response.status === 405 ? UNAVAILABLE : fallback;
  throw new Error(await readApiMessage(response, message));
}

export async function submitPlaybackReport(draft: PlaybackReportDraft, token?: string | null, signal?: AbortSignal): Promise<number> {
  if (!isEpisodeId(draft.episodeId)) throw new Error('Thiếu mã tập để gửi báo lỗi.');
  if (!isReason(draft.reason)) throw new Error('Bạn chọn một lý do trước khi gửi nhé.');
  if (draft.note && draft.note.length > 500) throw new Error('Ghi chú dài tối đa 500 ký tự.');
  const episodeId = draft.episodeId.trim();
  const { assetId, reason, note } = draft;
  return withRequestTimeout(async (requestSignal) => {
    const response = await apiFetch('/api/reports/playback', {
      method: 'POST',
      headers: headers(token, true),
      body: JSON.stringify({ episodeId, assetId, reason, ...(note?.trim() ? { note: note.trim() } : {}) }),
      signal: requestSignal,
    });
    await requireResponse(response, 201, response.status === 429
      ? 'Bạn vừa gửi khá nhiều báo lỗi. Chờ một chút rồi thử lại nhé.'
      : 'Chưa gửi được báo lỗi. Bạn thử lại nhé.');
    const result = unwrapEnvelope(await response.json().catch(() => null)) as { id?: unknown } | null;
    if (!isId(result?.id)) throw new Error('Máy chủ trả về mã báo lỗi không hợp lệ.');
    return result.id;
  }, { signal });
}

function unwrapEnvelope(body: unknown): unknown {
  if (body && typeof body === 'object' && !Array.isArray(body) && 'data' in body) {
    return (body as { data: unknown }).data;
  }
  return body;
}

function isReport(value: unknown): value is PlaybackReport {
  if (!value || typeof value !== 'object') return false;
  const report = value as Partial<PlaybackReport>;
  return isId(report.id) && isEpisodeId(report.episodeId) && typeof report.seriesTitle === 'string'
    && typeof report.episodeTitle === 'string' && (report.assetId === null || typeof report.assetId === 'string')
    && isReason(report.reason) && (report.note == null || typeof report.note === 'string') && report.status === 'open'
    && typeof report.createdAt === 'string';
}

export async function listOpenPlaybackReports(token: string, signal?: AbortSignal): Promise<PlaybackReport[]> {
  return withRequestTimeout(async (requestSignal) => {
    const response = await apiFetch('/api/admin/reports?status=open', { headers: headers(token), signal: requestSignal });
    await requireResponse(response, 200, 'Chưa tải được danh sách báo lỗi.');
    const list = unwrapEnvelope(await response.json().catch(() => null));
    if (!Array.isArray(list) || !list.every(isReport)) throw new Error('Danh sách báo lỗi không hợp lệ.');
    return list;
  }, { signal });
}

export async function updatePlaybackReport(token: string, id: number, status: ReportResolution, signal?: AbortSignal): Promise<void> {
  if (!isId(id)) throw new Error('Mã báo lỗi không hợp lệ.');
  return withRequestTimeout(async (requestSignal) => {
    const response = await apiFetch(`/api/admin/reports/${encodeURIComponent(String(id))}`, {
      method: 'PATCH',
      headers: headers(token, true),
      body: JSON.stringify({ status }),
      signal: requestSignal,
    });
    await requireResponse(response, 200, 'Chưa cập nhật được báo lỗi.');
  }, { signal });
}
