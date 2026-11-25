package com.dippie.report;

import java.time.Instant;
import java.time.ZoneOffset;

// Dữ liệu hiển thị một báo lỗi trên trang admin, kèm tên bộ, tên tập và mốc tạo theo UTC ISO-8601.
public record PlaybackReportView(
        Long id,
        String episodeId,
        String seriesTitle,
        String episodeTitle,
        String assetId,
        String reason,
        String note,
        String status,
        Instant createdAt
) {

    // Chuyển entity báo lỗi sang DTO và quy đổi LocalDateTime sang Instant ở múi giờ UTC.
    public static PlaybackReportView of(PlaybackReport report, String seriesTitle, String episodeTitle) {
        Instant createdInstant = report.getCreatedAt() == null
                ? null
                : report.getCreatedAt().toInstant(ZoneOffset.UTC);
        return new PlaybackReportView(
                report.getId(),
                report.getEpisodeId(),
                seriesTitle,
                episodeTitle,
                report.getAssetId(),
                report.getReason(),
                report.getNote(),
                report.getStatus(),
                createdInstant
        );
    }
}
