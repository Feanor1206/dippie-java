package com.dippie.media;

// DTO gom thông tin phim chính và trailer có thể phát của một bộ phim để trả về cho player.
public record MediaDescriptorView(
        String movieId,
        boolean hasFeature,
        boolean hasTrailer,
        MediaAssetView feature,
        MediaAssetView trailer
) {
}
