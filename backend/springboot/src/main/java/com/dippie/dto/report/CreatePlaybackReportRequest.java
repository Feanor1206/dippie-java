package com.dippie.dto.report;

import com.dippie.exception.InvalidRequestException;

import com.fasterxml.jackson.databind.JsonNode;

// body gửi lên lúc người xem báo lỗi phát video
// giữ episodeId kiểu JsonNode kẻo Jackson tự ép số hay boolean sang String, tự check kiểu cho chắc
public record CreatePlaybackReportRequest(
        JsonNode episodeId,
        String assetId,
        String reason,
        String note
) {

    private static final int MAX_ID_LENGTH = 100;

    // chỉ nhận episodeId là chuỗi đúng độ dài, đá số nguyên, boolean hay object ra hết
    public String resolvedEpisodeId() {
        if (episodeId == null || episodeId.isNull()) {
            throw new InvalidRequestException("Vui lòng chọn tập phim cần báo lỗi.");
        }
        if (!episodeId.isTextual()) {
            throw new InvalidRequestException("Mã tập phim không hợp lệ.");
        }
        String trimmedId = episodeId.asText().trim();
        if (trimmedId.isEmpty() || trimmedId.length() > MAX_ID_LENGTH) {
            throw new InvalidRequestException("Mã tập phim không hợp lệ.");
        }
        return trimmedId;
    }

    // assetId có cũng được không có cũng sao, trim ra rỗng thì đổi thành null
    public String resolvedAssetId() {
        if (assetId == null) {
            return null;
        }
        String trimmed = assetId.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_ID_LENGTH) {
            throw new InvalidRequestException("Mã nguồn phát chỉ dài tối đa 100 ký tự.");
        }
        return trimmed;
    }

    // chặn ghi chú quá 500 ký tự, chuỗi toàn dấu cách thì kéo về null
    public String resolvedNote() {
        if (note == null) {
            return null;
        }
        if (note.length() > 500) {
            throw new InvalidRequestException("Ghi chú không được dài quá 500 ký tự.");
        }
        String trimmed = note.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
