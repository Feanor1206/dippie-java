package com.dippie.media;

public record MediaAssetView(
        String assetId,
        String kind,
        String streamUrl,
        String mimeType,
        long byteLength,
        Double durationSeconds,
        boolean isPublic,
        String version
) {

    public static MediaAssetView from(MediaAsset asset) {
        return new MediaAssetView(
                asset.getId(),
                asset.getKind(),
                "/api/media/stream/" + asset.getId(),
                asset.getMimeType(),
                asset.getByteLength(),
                asset.getDurationSeconds(),
                asset.isPublic(),
                asset.getVersion());
    }
}
