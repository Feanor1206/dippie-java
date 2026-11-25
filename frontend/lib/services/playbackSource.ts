import type {
  CatalogEpisodeResponse,
  CatalogMediaAsset,
  CatalogYoutubeSource,
} from './catalogService';

export const PLAYBACK_METADATA_TIMEOUT_MS = 10_000;

const YOUTUBE_VIDEO_ID_PATTERN = /^[A-Za-z0-9_-]{11}$/;

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
  return `https://www.youtube-nocookie.com/embed/${videoId}?start=${safeStart}`;
}

export function buildYoutubeWatchUrl(videoId: string, startSeconds = 0): string {
  const safeStart = Number.isFinite(startSeconds) && startSeconds > 0 ? Math.floor(startSeconds) : 0;
  return `https://www.youtube.com/watch?v=${videoId}&t=${safeStart}`;
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
  return 'stream_error';
}

export function getUnavailableReasonMessage(reason: PlaybackUnavailableReason): string {
  switch (reason) {
    case 'file_unreachable':
      return 'Không thể truy cập tệp video trên máy chủ. Tệp có thể chưa sẵn sàng hoặc kết nối lưu trữ đang gián đoạn.';
    case 'timeout':
      return 'Quá thời gian chờ tải dữ liệu tệp video từ máy chủ.';
    case 'forbidden':
      return 'Video được bảo vệ hoặc tài khoản chưa có quyền truy cập tệp này.';
    case 'stream_error':
      return 'Máy chủ không thể cấp luồng phát cho tệp video này.';
    case 'youtube_embed_disabled':
      return 'Bản YouTube của tập này không hỗ trợ phát nhúng trên trang.';
    case 'file_unreachable_youtube_external':
      return 'Không thể truy cập tệp video trên máy chủ và bản YouTube không hỗ trợ phát nhúng.';
    case 'no_source':
    default:
      return 'Tập này chưa có nguồn video khả dụng.';
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
  const hasFileFallbackContext = failedAsset !== null;

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
      isFallback: hasFileFallbackContext,
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
      isFallback: hasFileFallbackContext,
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
    canRetryFile: hasFileFallbackContext,
    youtubeWatchUrl,
    youtubeVideoId,
  };
}
