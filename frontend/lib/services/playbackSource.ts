import type {
  CatalogEpisode,
  CatalogEpisodeResponse,
  CatalogMediaAsset,
  CatalogYoutubeSource,
} from './catalogService';

export const PLAYBACK_METADATA_TIMEOUT_MS = 10_000;

const YOUTUBE_VIDEO_ID_PATTERN = /^[A-Za-z0-9_-]{11}$/;

type ExternalMediaLike = {
  externalOnly?: boolean;
  external_only?: boolean;
  canEmbed?: boolean;
  can_embed?: boolean;
  primaryMediaAssetId?: string | null;
  primary_media_asset_id?: string | null;
  youtube?: CatalogYoutubeSource | null;
  youtubeFallback?: CatalogYoutubeSource | null;
  youtubeWatchUrl?: string | null;
  youtube_watch_url?: string | null;
  externalUrl?: string | null;
  external_url?: string | null;
  watchUrl?: string | null;
  watch_url?: string | null;
  youtubeVideoId?: string | null;
  youtube_video_id?: string | null;
  videoUrl?: string | null;
  rawJson?: string | null;
  startMs?: number | null;
  start_ms?: number | null;
  durationMs?: number | null;
  evidence?: Array<{ startMs?: number | null }>;
};

function parseYoutubeUrlVideoId(rawUrl?: string | null): string | null {
  if (typeof rawUrl !== 'string' || !rawUrl.trim()) return null;
  const trimmed = rawUrl.trim();
  if (!/^https?:\/\//i.test(trimmed)) return null;
  try {
    const parsed = new URL(trimmed);
    const host = parsed.hostname.toLowerCase().replace(/^www\./, '');
    if (host === 'youtube.com' || host === 'm.youtube.com' || host === 'youtube-nocookie.com') {
      const videoIdParam = parsed.searchParams.get('v')?.trim() || '';
      if (YOUTUBE_VIDEO_ID_PATTERN.test(videoIdParam)) return videoIdParam;
      const match = parsed.pathname.match(/^\/(?:embed|shorts|live)\/([A-Za-z0-9_-]{11})(?:\/|$)/);
      if (match) return match[1];
    } else if (host === 'youtu.be') {
      const candidate = parsed.pathname.replace(/^\/+/, '').split('/')[0] || '';
      if (YOUTUBE_VIDEO_ID_PATTERN.test(candidate)) return candidate;
    }
  } catch {
  }
  return null;
}

function extractRawJsonYoutubeIds(rawJson?: string | null): { playbackId: string | null; watchId: string | null } {
  if (typeof rawJson !== 'string' || !rawJson.trim()) {
    return { playbackId: null, watchId: null };
  }
  try {
    const parsed = JSON.parse(rawJson) as {
      external_ids?: Array<{ namespace?: unknown; value?: unknown }>;
    };
    const externalIds = Array.isArray(parsed?.external_ids) ? parsed.external_ids : [];
    let playbackId: string | null = null;
    let watchId: string | null = null;
    for (const entry of externalIds) {
      if (!entry || typeof entry !== 'object') continue;
      const value = typeof entry.value === 'string' ? entry.value.trim() : '';
      if (!YOUTUBE_VIDEO_ID_PATTERN.test(value)) continue;
      if (entry.namespace === 'youtube_playback' && !playbackId) {
        playbackId = value;
      } else if (entry.namespace === 'youtube_watch' && !watchId) {
        watchId = value;
      }
    }
    return { playbackId, watchId };
  } catch {
    return { playbackId: null, watchId: null };
  }
}

export function isMediaExternalOnly(item?: ExternalMediaLike | null): boolean {
  if (!item) return false;
  if (item.externalOnly === true || item.external_only === true) return true;
  if (item.canEmbed === false || item.can_embed === false) return true;
  if (item.canEmbed === true || item.can_embed === true) return false;

  const primaryYoutube = normalizeYoutubeSource(item.youtube);
  const fallbackYoutube = normalizeYoutubeSource(item.youtubeFallback);
  if (primaryYoutube?.canEmbed || fallbackYoutube?.canEmbed) return false;
  const hasPrimaryAsset = Boolean(item.primaryMediaAssetId || item.primary_media_asset_id);
  if (primaryYoutube && !primaryYoutube.canEmbed && !hasPrimaryAsset) return true;

  const rawIds = extractRawJsonYoutubeIds(item.rawJson);
  if (rawIds.playbackId) return false;
  if (rawIds.watchId && !hasPrimaryAsset) return true;

  return false;
}

export function resolveExternalYoutubeWatchUrl(
  item?: ExternalMediaLike | null,
  startTimeMs?: number | string | null
): string | null {
  if (!item) return null;
  const effectiveStartMs =
    startTimeMs !== undefined && startTimeMs !== null
      ? startTimeMs
      : item.startMs ?? item.start_ms ?? item.evidence?.[0]?.startMs ?? 0;
  const startSeconds = resolveYoutubeStartSeconds(effectiveStartMs, item.durationMs);

  const directCandidates = [
    item.youtubeWatchUrl,
    item.youtube_watch_url,
    item.externalUrl,
    item.external_url,
    item.watchUrl,
    item.watch_url,
    item.videoUrl,
  ];
  for (const candidate of directCandidates) {
    const parsedId = parseYoutubeUrlVideoId(candidate);
    if (parsedId) {
      return buildYoutubeWatchUrl(parsedId, startSeconds);
    }
  }

  const directIds = [
    item.youtubeVideoId,
    item.youtube_video_id,
    item.youtube?.videoId,
    item.youtubeFallback?.videoId,
  ];
  for (const candidateId of directIds) {
    if (typeof candidateId === 'string' && YOUTUBE_VIDEO_ID_PATTERN.test(candidateId.trim())) {
      return buildYoutubeWatchUrl(candidateId.trim(), startSeconds);
    }
  }

  const rawIds = extractRawJsonYoutubeIds(item.rawJson);
  const fallbackId = rawIds.watchId || rawIds.playbackId;
  if (fallbackId) {
    return buildYoutubeWatchUrl(fallbackId, startSeconds);
  }

  return null;
}

export type PlaybackFailureReason =
  | 'file_unreachable'
  | 'timeout'
  | 'forbidden'
  | 'stream_error';

export type PlaybackUnavailableReason =
  | PlaybackFailureReason
  | 'youtube_embed_disabled'
  | 'file_unreachable_youtube_external'
  | 'no_source';

export interface PlaybackRuntimeFailure {
  episodeId: string;
  assetId: string;
  reason: PlaybackFailureReason;
}

export type PlaybackSourceSelection =
  | {
      kind: 'file';
      asset: CatalogMediaAsset;
    }
  | {
      kind: 'youtube';
      youtube: CatalogYoutubeSource;
      origin: 'youtube' | 'youtubeFallback';
      isFallback: boolean;
      failedAsset: CatalogMediaAsset | null;
      startSeconds: number;
      embedUrl: string;
      watchUrl: string;
      reason: PlaybackFailureReason | null;
    }
  | {
      kind: 'unavailable';
      reason: PlaybackUnavailableReason;
      failedAsset: CatalogMediaAsset | null;
      canRetryFile: boolean;
      youtubeWatchUrl: string | null;
      youtubeVideoId: string | null;
    };

export interface SelectPlaybackSourceParams {
  episodeDetail: CatalogEpisodeResponse | null;
  selectedEpisodeId?: string | null;
  episodeDurationMs?: number | null;
  preferredAssetId?: string | null;
  manualAssetId?: string | null;
  runtimeFailure?: PlaybackRuntimeFailure | null;
  startTimeMs?: number | string | null;
}

export function normalizeYoutubeSource(
  source?: CatalogYoutubeSource | null
): CatalogYoutubeSource | null {
  if (!source || typeof source.videoId !== 'string') return null;
  if (!YOUTUBE_VIDEO_ID_PATTERN.test(source.videoId)) return null;
  return {
    videoId: source.videoId,
    canEmbed: Boolean(source.canEmbed),
  };
}

export function isMediaAssetAvailable(asset?: CatalogMediaAsset | null): boolean {
  return Boolean(asset) && asset!.available !== false;
}

export function isEpisodeExternalOnly(episode?: CatalogEpisode | null): boolean {
  return isMediaExternalOnly(episode);
}

export function isUnmountedDriveFallback(
  selection: PlaybackSourceSelection,
  runtimeFailure?: PlaybackRuntimeFailure | null
): boolean {
  return (
    selection.kind === 'youtube' &&
    selection.isFallback &&
    !runtimeFailure &&
    selection.failedAsset?.storageProvider === 'drive' &&
    selection.failedAsset.available === false
  );
}

export function resolveYoutubeStartSeconds(
  startTimeMs?: number | string | null,
  episodeDurationMs?: number | null
): number {
  if (startTimeMs === undefined || startTimeMs === null || startTimeMs === '') return 0;
  const ms = Number(startTimeMs);
  if (!Number.isFinite(ms) || ms <= 0) return 0;
  if (
    typeof episodeDurationMs === 'number' &&
    Number.isFinite(episodeDurationMs) &&
    episodeDurationMs > 0 &&
    ms >= episodeDurationMs
  ) {
    return 0;
  }
  return Math.floor(ms / 1000);
}

export function buildYoutubeEmbedUrl(videoId: string, startSeconds = 0): string {
  const safeStart = Number.isFinite(startSeconds) && startSeconds > 0 ? Math.floor(startSeconds) : 0;
  return `https://www.youtube-nocookie.com/embed/${encodeURIComponent(videoId)}?start=${safeStart}&enablejsapi=1&rel=0&modestbranding=1`;
}

export function buildYoutubeWatchUrl(videoId: string, startSeconds = 0): string {
  const safeStart = Number.isFinite(startSeconds) && startSeconds > 0 ? Math.floor(startSeconds) : 0;
  return `https://www.youtube.com/watch?v=${encodeURIComponent(videoId)}&t=${safeStart}`;
}

export function resolveFallbackStartTimeMs(params: {
  videoCurrentTimeSec?: number | null;
  pendingSeekSec?: number | null;
  trackedTimeSec?: number | null;
  manualSeekMs?: number | null;
  deepLinkTimeMs?: number | string | null;
  segmentStartMs?: number | null;
  sceneStartMs?: number | null;
}): number {
  const {
    videoCurrentTimeSec,
    pendingSeekSec,
    trackedTimeSec,
    manualSeekMs,
    deepLinkTimeMs,
    segmentStartMs,
    sceneStartMs,
  } = params;

  if (typeof videoCurrentTimeSec === 'number' && Number.isFinite(videoCurrentTimeSec) && videoCurrentTimeSec > 0) {
    return Math.floor(videoCurrentTimeSec * 1000);
  }
  if (typeof pendingSeekSec === 'number' && Number.isFinite(pendingSeekSec) && pendingSeekSec > 0) {
    return Math.floor(pendingSeekSec * 1000);
  }
  if (typeof trackedTimeSec === 'number' && Number.isFinite(trackedTimeSec) && trackedTimeSec > 0) {
    return Math.floor(trackedTimeSec * 1000);
  }
  if (typeof manualSeekMs === 'number' && Number.isFinite(manualSeekMs) && manualSeekMs > 0) {
    return Math.floor(manualSeekMs);
  }
  if (deepLinkTimeMs !== undefined && deepLinkTimeMs !== null && deepLinkTimeMs !== '') {
    const parsed = Number(deepLinkTimeMs);
    if (Number.isFinite(parsed) && parsed > 0) {
      return Math.floor(parsed);
    }
  }
  if (typeof segmentStartMs === 'number' && Number.isFinite(segmentStartMs) && segmentStartMs > 0) {
    return Math.floor(segmentStartMs);
  }
  if (typeof sceneStartMs === 'number' && Number.isFinite(sceneStartMs) && sceneStartMs > 0) {
    return Math.floor(sceneStartMs);
  }
  return 0;
}

export function classifyGrantFailureReason(status: number): PlaybackFailureReason {
  if (status === 401 || status === 403) return 'forbidden';
  if (status === 404) return 'file_unreachable';
  if (status === 408) return 'timeout';
  return 'stream_error';
}

export function getUnavailableReasonMessage(reason: PlaybackUnavailableReason): string {
  switch (reason) {
    case 'file_unreachable':
      return 'Video trên máy chủ chưa sẵn sàng hoặc kết nối lưu trữ đang gián đoạn.';
    case 'timeout':
      return 'Tải video từ máy chủ quá chậm. Bạn thử lại nhé.';
    case 'forbidden':
      return 'Tài khoản hiện chưa có quyền xem bản video này.';
    case 'stream_error':
      return 'Máy chủ chưa mở được bản video này.';
    case 'youtube_embed_disabled':
      return 'Tập này chỉ xem được trực tiếp trên YouTube.';
    case 'file_unreachable_youtube_external':
      return 'Video trên máy chủ chưa sẵn sàng, tập này chỉ mở được trực tiếp trên YouTube.';
    case 'no_source':
    default:
      return 'Tập này chưa có nguồn video.';
  }
}

function pickCandidateMediaAsset(
  mediaAssets: CatalogMediaAsset[],
  options: { manualAssetId?: string | null; preferredAssetId?: string | null }
): { playableAsset: CatalogMediaAsset | null; unavailableAsset: CatalogMediaAsset | null } {
  const assets = Array.isArray(mediaAssets) ? mediaAssets : [];
  const videoAssets = assets.filter((asset) => asset.contentType === 'video');

  if (options.manualAssetId) {
    const manual = assets.find((asset) => asset.id === options.manualAssetId) || null;
    if (manual) {
      return isMediaAssetAvailable(manual)
        ? { playableAsset: manual, unavailableAsset: null }
        : { playableAsset: null, unavailableAsset: manual };
    }
  }

  if (options.preferredAssetId) {
    const preferred = assets.find((asset) => asset.id === options.preferredAssetId) || null;
    if (preferred) {
      if (isMediaAssetAvailable(preferred)) {
        return { playableAsset: preferred, unavailableAsset: null };
      }
      const alternateVideo = videoAssets.find(
        (asset) => asset.id !== preferred.id && isMediaAssetAvailable(asset)
      );
      if (alternateVideo) {
        return { playableAsset: alternateVideo, unavailableAsset: null };
      }
      return { playableAsset: null, unavailableAsset: preferred };
    }
  }

  const firstAvailableVideo = videoAssets.find(isMediaAssetAvailable) || null;
  if (firstAvailableVideo) {
    return { playableAsset: firstAvailableVideo, unavailableAsset: null };
  }

  const firstUnavailableVideo = videoAssets[0] || null;
  return { playableAsset: null, unavailableAsset: firstUnavailableVideo };
}

export function selectPlaybackSource(
  params: SelectPlaybackSourceParams
): PlaybackSourceSelection {
  const {
    episodeDetail,
    selectedEpisodeId,
    episodeDurationMs,
    preferredAssetId,
    manualAssetId,
    runtimeFailure,
    startTimeMs,
  } = params;

  if (!episodeDetail || (selectedEpisodeId && episodeDetail.episode.id !== selectedEpisodeId)) {
    return {
      kind: 'unavailable',
      reason: 'no_source',
      failedAsset: null,
      canRetryFile: false,
      youtubeWatchUrl: null,
      youtubeVideoId: null,
    };
  }

  const { playableAsset, unavailableAsset } = pickCandidateMediaAsset(
    episodeDetail.mediaAssets || [],
    { manualAssetId, preferredAssetId }
  );

  const failedAtRuntime = Boolean(
    playableAsset &&
      runtimeFailure &&
      runtimeFailure.episodeId === episodeDetail.episode.id &&
      runtimeFailure.assetId === playableAsset.id
  );

  if (playableAsset && !failedAtRuntime) {
    return {
      kind: 'file',
      asset: playableAsset,
    };
  }

  const failedAsset = failedAtRuntime ? playableAsset : unavailableAsset;
  const fileFailureReason: PlaybackFailureReason | null = failedAtRuntime
    ? runtimeFailure!.reason
    : unavailableAsset
    ? 'file_unreachable'
    : null;
  const hadFileFailure = failedAsset !== null;

  const primaryYoutube = normalizeYoutubeSource(episodeDetail.youtube);
  const fallbackYoutube = normalizeYoutubeSource(episodeDetail.youtubeFallback);
  const startSeconds = resolveYoutubeStartSeconds(
    startTimeMs,
    episodeDurationMs ?? episodeDetail.episode.durationMs
  );

  if (primaryYoutube?.canEmbed) {
    return {
      kind: 'youtube',
      youtube: primaryYoutube,
      origin: 'youtube',
      isFallback: hadFileFailure,
      failedAsset,
      startSeconds,
      embedUrl: buildYoutubeEmbedUrl(primaryYoutube.videoId, startSeconds),
      watchUrl: buildYoutubeWatchUrl(primaryYoutube.videoId, startSeconds),
      reason: fileFailureReason,
    };
  }

  if (fallbackYoutube?.canEmbed) {
    return {
      kind: 'youtube',
      youtube: fallbackYoutube,
      origin: 'youtubeFallback',
      isFallback: hadFileFailure,
      failedAsset,
      startSeconds,
      embedUrl: buildYoutubeEmbedUrl(fallbackYoutube.videoId, startSeconds),
      watchUrl: buildYoutubeWatchUrl(fallbackYoutube.videoId, startSeconds),
      reason: fileFailureReason,
    };
  }

  const externalYoutube = primaryYoutube ?? fallbackYoutube;
  const youtubeWatchUrl = externalYoutube
    ? buildYoutubeWatchUrl(externalYoutube.videoId, startSeconds)
    : null;
  const youtubeVideoId = externalYoutube?.videoId ?? null;

  let reason: PlaybackUnavailableReason;
  if (fileFailureReason && externalYoutube) {
    reason =
      fileFailureReason === 'file_unreachable'
        ? 'file_unreachable_youtube_external'
        : fileFailureReason;
  } else if (fileFailureReason) {
    reason = fileFailureReason;
  } else if (externalYoutube) {
    reason = 'youtube_embed_disabled';
  } else {
    reason = 'no_source';
  }

  return {
    kind: 'unavailable',
    reason,
    failedAsset,
    canRetryFile: hadFileFailure,
    youtubeWatchUrl,
    youtubeVideoId,
  };
}
