package com.dippie.model.report;

import com.dippie.exception.InvalidRequestException;

import java.util.Optional;

// 3 trạng thái vòng đời báo lỗi (open, resolved, dismissed) kèm luật lọc và chuyển trạng thái
public enum PlaybackReportStatus {

    OPEN("open"),
    RESOLVED("resolved"),
    DISMISSED("dismissed");

    private static final String INVALID_RESOLUTION_MESSAGE =
            "Trạng thái xử lý chỉ nhận resolved hoặc dismissed.";

    private final String code;

    PlaybackReportStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    // đọc tham số lọc trạng thái, bỏ trống hay gửi all thì trả Optional.empty để lấy hết
    public static Optional<PlaybackReportStatus> parseFilter(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank() || "all".equalsIgnoreCase(rawStatus.trim())) {
            return Optional.empty();
        }
        String normalized = rawStatus.trim();
        for (PlaybackReportStatus candidate : values()) {
            if (candidate.code.equals(normalized)) {
                return Optional.of(candidate);
            }
        }
        throw new InvalidRequestException("Trạng thái báo cáo không hợp lệ.");
    }

    // admin xử lý chỉ được chuyển sang resolved hay dismissed, cấm kéo ngược về open
    public static PlaybackReportStatus parseResolution(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            throw new InvalidRequestException(INVALID_RESOLUTION_MESSAGE);
        }
        String normalized = rawStatus.trim();
        if (RESOLVED.code.equals(normalized)) {
            return RESOLVED;
        }
        if (DISMISSED.code.equals(normalized)) {
            return DISMISSED;
        }
        throw new InvalidRequestException(INVALID_RESOLUTION_MESSAGE);
    }
}
