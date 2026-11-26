package com.dippie.dto.report;

import com.dippie.model.report.PlaybackReport;

import java.time.Instant;
import java.time.ZoneOffset;

// view một báo lỗi bên trang admin, kèm tên bộ, tên tập với giờ tạo chuẩn UTC ISO-8601
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

    // đổi entity báo lỗi sang view rồi ép LocalDateTime thành Instant múi giờ UTC
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
