package com.dippie.dto.media;

import com.dippie.model.media.MediaAsset;

// view trả frontend gồm định dạng với link phát của một file media
public record MediaAssetView(
        String assetId, String kind, String streamUrl, String mimeType, long byteLength,
        Double durationSeconds, boolean isPublic, String version
) {

    public static final String STREAM_PATH_PREFIX = "/api/media/stream/";
    public static final String DEFAULT_MIME_TYPE = "video/mp4";
    public static final String KIND_FEATURE = "feature";
    public static final String KIND_TRAILER = "trailer";

    public static MediaAssetView from(MediaAsset asset) {
        return new MediaAssetView(asset.getId(), asset.getKind(), STREAM_PATH_PREFIX + asset.getId(),
                asset.getMimeType(), asset.getByteLength(), asset.getDurationSeconds(),
                asset.isPublic(), asset.getVersion());
    }

    // dựng trailer dự phòng từ cột videoUrl cũ ở bảng movies khi chưa có bản ghi trailer riêng
    public static MediaAssetView legacyTrailer(String movieId, String videoUrl) {
        return new MediaAssetView("fallback-trailer-" + movieId, KIND_TRAILER, videoUrl,
                DEFAULT_MIME_TYPE, 0L, null, true, "legacy");
    }
}
