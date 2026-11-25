package com.dippie.report;

// Thân request chứa trạng thái mới khi admin xử lý một báo lỗi phát video.
public record UpdatePlaybackReportStatusRequest(String status) {
}
