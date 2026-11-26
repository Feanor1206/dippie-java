import { resolveStreamUrl } from '@/lib/safeUrl';
import { getStoredAuthToken } from '@/lib/services/sessionStorage';
import { apiFetch, RequestTimeoutError, withRequestTimeout } from '@/lib/services/apiRequest';
import { LOCK_REASON_HEADER, notifyPlayFeaturesLocked } from '@/lib/services/playFeaturesService';
import { notifyScreenTimeLocked } from '@/lib/services/screenTimeService';
export interface MediaAssetInfo {
  assetId: string;
  kind: 'feature' | 'trailer';
  streamUrl: string;
  mimeType: string;
  byteLength: number;
  durationSeconds?: number | null;
  isPublic: boolean;
  version: string;
}

export interface MediaDescriptor {
  movieId: string;
  hasFeature: boolean;
  hasTrailer: boolean;
  feature?: MediaAssetInfo | null;
  trailer?: MediaAssetInfo | null;
}

const DEFAULT_TIMEOUT_MS = 6000;
const API = '/api';

export { resolveStreamUrl };

export async function getMediaDescriptor(
  movieId: string,
  options?: { timeoutMs?: number; signal?: AbortSignal }
): Promise<MediaDescriptor> {
  return withRequestTimeout(async (signal) => {
    const url = `${API}/movies/${encodeURIComponent(movieId)}/media-descriptor`;
    const response = await apiFetch(url, {
      method: 'GET',
      headers: { Accept: 'application/json' },
      signal,
    });

    if (!response.ok) {
      throw new Error(`Chưa tải được thông tin phát phim (${response.status}).`);
    }

    const json = await response.json();
    if (!json || typeof json !== 'object' || Array.isArray(json)) {
      throw new Error('Thông tin phát phim không hợp lệ.');
    }

    const payload =
      json.data && typeof json.data === 'object' && !Array.isArray(json.data)
        ? json.data
        : json;

    if (typeof payload.movieId !== 'string' || payload.movieId.trim() === '' || payload.movieId !== movieId) {
      throw new Error('Thông tin phát phim không khớp mã phim.');
    }

    if (typeof payload.hasFeature !== 'boolean' || typeof payload.hasTrailer !== 'boolean') {
      throw new Error('Thông tin phát phim thiếu nguồn video.');
    }

    const isInvalidAsset = (asset: any, expectedKind: string) =>
      !asset || typeof asset !== 'object' || Array.isArray(asset) || asset.kind !== expectedKind || typeof asset.streamUrl !== 'string' || !asset.streamUrl.trim();
    if (payload.hasFeature && isInvalidAsset(payload.feature, 'feature')) throw new Error('Nguồn phát phim chính không hợp lệ.');
    if (payload.hasTrailer && isInvalidAsset(payload.trailer, 'trailer')) throw new Error('Nguồn phát đoạn giới thiệu không hợp lệ.');

    return {
      movieId: payload.movieId,
      hasFeature: payload.hasFeature,
      hasTrailer: payload.hasTrailer,
      feature: payload.hasFeature && payload.feature
        ? {
            ...payload.feature,
            streamUrl: resolveStreamUrl(payload.feature.streamUrl),
          }
        : null,
      trailer: payload.hasTrailer && payload.trailer
        ? {
            ...payload.trailer,
            streamUrl: resolveStreamUrl(payload.trailer.streamUrl),
          }
        : null,
    };
  }, { ...options, timeoutMs: options?.timeoutMs ?? DEFAULT_TIMEOUT_MS }, 'Tải thông tin phát phim quá chậm. Bạn thử lại nhé.');
}

export interface PlaybackGrantResponse {
  assetId: string;
  grant: string;
  streamUrl: string;
  expiresIn: number;
}

export type PlaybackLockReason =
  | 'SCREEN_TIME'
  | 'WEEKEND_VAULT'
  | 'VIP_EPISODE'
  | 'WATCH_PET_REST'
  | 'WATCH_PET_FAINT'
  | 'RECAP_REQUIRED';

export interface PlaybackGrantResult {
  ok: boolean;
  status: number;
  grant: PlaybackGrantResponse | null;
  aborted?: boolean;
  lockReason?: PlaybackLockReason;
  message?: string;
}

export { getStoredAuthToken };

function parseLockReason(value?: string | null): PlaybackLockReason | undefined {
  const trimmed = value?.trim();
  if (
    trimmed === 'SCREEN_TIME' ||
    trimmed === 'WEEKEND_VAULT' ||
    trimmed === 'VIP_EPISODE' ||
    trimmed === 'WATCH_PET_REST' ||
    trimmed === 'WATCH_PET_FAINT' ||
    trimmed === 'RECAP_REQUIRED'
  ) {
    return trimmed;
  }
  return undefined;
}

export async function requestPlaybackGrantResult(
  assetId: string,
  signal?: AbortSignal
): Promise<PlaybackGrantResult> {
  if (!assetId) return { ok: false, status: 400, grant: null };
  const token = getStoredAuthToken();
  try {
    return await withRequestTimeout(async (requestSignal) => {
      const res = await apiFetch(`${API}/media/grant/${encodeURIComponent(assetId)}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
        signal: requestSignal,
      });
      if (!res.ok) {
        const lockReason = parseLockReason(res.headers?.get?.(LOCK_REASON_HEADER));
        if (res.status === 423) {
          let message: string | undefined;
          try {
            const body = await res.json();
            if (typeof body?.message === 'string' && body.message.trim()) {
              message = body.message.trim();
            }
          } catch {}
          if (!lockReason || lockReason === 'SCREEN_TIME') {
            notifyScreenTimeLocked();
          } else if (
            lockReason === 'WATCH_PET_REST' ||
            lockReason === 'WATCH_PET_FAINT' ||
            lockReason === 'RECAP_REQUIRED'
          ) {
            notifyPlayFeaturesLocked(lockReason);
          }
          return { ok: false, status: res.status, grant: null, lockReason, message };
        }
        return { ok: false, status: res.status, grant: null, ...(lockReason ? { lockReason } : {}) };
      }
      const grantData = await res.json();
      const resolvedUrl = resolveStreamUrl(grantData?.streamUrl || '');
      if (!resolvedUrl) return { ok: false, status: 502, grant: null };
      return {
        ok: true,
        status: res.status,
        grant: { assetId: grantData.assetId, grant: grantData.grant, streamUrl: resolvedUrl, expiresIn: grantData.expiresIn },
      };
    }, { signal, timeoutMs: DEFAULT_TIMEOUT_MS }, 'Hết thời gian kết nối nguồn phát.');
  } catch (err: any) {
    if (signal?.aborted || err?.name === 'AbortError') {
      return { ok: false, status: 0, grant: null, aborted: true };
    }
    return { ok: false, status: err instanceof RequestTimeoutError ? 408 : 0, grant: null };
  }
}

export async function requestPlaybackGrant(
  assetId: string,
  signal?: AbortSignal
): Promise<PlaybackGrantResponse | null> {
  const result = await requestPlaybackGrantResult(assetId, signal);
  return result.grant;
}
