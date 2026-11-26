package com.dippie.dto.media;

// trả vé phát tạm, giữ cả cặp grant/token với expiresIn/expiresInSeconds cho hợp frontend
public record PlaybackGrantResponse(
        String assetId,
        String grant,
        String token,
        String streamUrl,
        long expiresIn,
        long expiresInSeconds
) {
}
