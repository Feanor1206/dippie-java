package com.dippie.media;

// DTO trả về cho frontend gồm thông tin định dạng và đường dẫn phát của một tệp media.
public record MediaAssetView(
        String assetId, String kind, String streamUrl, String mimeType, long byteLength,
        Double durationSeconds, boolean isPublic, String version
) {

    static final String STREAM_PATH_PREFIX = "/api/media/stream/";
    static final String DEFAULT_MIME_TYPE = "video/mp4";
    static final String KIND_FEATURE = "feature";
    static final String KIND_TRAILER = "trailer";
    private static final String LEGACY_VERSION = "legacy";

    public static MediaAssetView from(MediaAsset asset) {
        return new MediaAssetView(asset.getId(), asset.getKind(), STREAM_PATH_PREFIX + asset.getId(),
                asset.getMimeType(), asset.getByteLength(), asset.getDurationSeconds(),
                asset.isPublic(), asset.getVersion());
    }

    // Dựng trailer dự phòng từ trường videoUrl cũ của bảng movies khi phim chưa có bản ghi trailer riêng.
    static MediaAssetView legacyTrailer(String movieId, String videoUrl) {
        return new MediaAssetView("fallback-trailer-" + movieId, KIND_TRAILER, videoUrl,
                DEFAULT_MIME_TYPE, 0L, null, true, LEGACY_VERSION);
    }
}
