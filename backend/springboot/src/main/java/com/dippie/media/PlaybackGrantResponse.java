package com.dippie.media;

public record PlaybackGrantResponse(
        String assetId,
        String grant,
        String token,
        String streamUrl,
        long expiresIn,
        long expiresInSeconds
) {
}
