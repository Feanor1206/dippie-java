package com.dippie.dto.media;

// gom thông tin phim chính với trailer phát được để trả cho player
public record MediaDescriptorView(
        String movieId,
        boolean hasFeature,
        boolean hasTrailer,
        MediaAssetView feature,
        MediaAssetView trailer
) {
}
