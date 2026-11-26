package com.dippie.model.report;

import com.dippie.exception.InvalidRequestException;

import java.util.Optional;

// Ba trạng thái vòng đời của một báo lỗi phát video (open, resolved, dismissed) kèm luật lọc và chuyển trạng thái.
public enum PlaybackReportStatus {

    OPEN("open"),
    RESOLVED("resolved"),
    DISMISSED("dismissed");

    private static final String INVALID_FILTER_MESSAGE = "Trạng thái báo cáo không hợp lệ.";
    private static final String INVALID_RESOLUTION_MESSAGE =
            "Trạng thái xử lý chỉ nhận resolved hoặc dismissed.";

    private final String code;

    PlaybackReportStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    // Đọc tham số lọc trạng thái; bỏ trống hoặc truyền all thì trả Optional.empty để lấy tất cả.
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
        throw new InvalidRequestException(INVALID_FILTER_MESSAGE);
    }

    // Khi admin xử lý chỉ cho chuyển sang resolved hoặc dismissed, không cho đặt lại về open.
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
