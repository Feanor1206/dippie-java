package com.dippie.dto.report;

import com.dippie.exception.InvalidRequestException;

import com.fasterxml.jackson.databind.JsonNode;

// Dữ liệu gửi lên khi người xem báo lỗi phát video.
// Mình giữ episodeId ở dạng JsonNode vì Jackson tự ép số hay boolean sang String, nên cần tự kiểm tra kiểu để chặn id sai định dạng.
public record CreatePlaybackReportRequest(
        JsonNode episodeId,
        String assetId,
        String reason,
        String note
) {

    private static final int MAX_ID_LENGTH = 100;
    private static final int MAX_NOTE_LENGTH = 500;

    // Chỉ nhận episodeId dạng chuỗi có độ dài hợp lệ, từ chối số nguyên, boolean hay object.
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

    // assetId là trường tùy chọn; chuỗi rỗng sau khi trim sẽ đổi về null.
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

    // Chặn ghi chú dài quá 500 ký tự và quy chuỗi toàn khoảng trắng về null.
    public String resolvedNote() {
        if (note == null) {
            return null;
        }
        if (note.length() > MAX_NOTE_LENGTH) {
            throw new InvalidRequestException("Ghi chú không được dài quá 500 ký tự.");
        }
        String trimmed = note.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
