package com.dippie.media;

// DTO trả về sau khi cấp vé phát tạm thời, giữ cả cặp tên trường grant/token và expiresIn/expiresInSeconds cho tương thích frontend.
public record PlaybackGrantResponse(
        String assetId,
        String grant,
        String token,
        String streamUrl,
        long expiresIn,
        long expiresInSeconds
) {
}
