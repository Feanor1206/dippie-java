package com.dippie.movie;

import com.dippie.media.MediaAssetView;

public record MediaDescriptorView(
        String movieId,
        boolean hasFeature,
        boolean hasTrailer,
        MediaAssetView feature,
        MediaAssetView trailer
) {
}
