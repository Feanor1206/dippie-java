package com.dippie.progress;

import com.dippie.catalog.domain.CatalogEpisode;
import com.dippie.catalog.domain.CatalogSeason;
import com.dippie.catalog.domain.CatalogSeries;
import com.dippie.catalog.domain.EpisodeRuntime;

// Ngữ cảnh phát đã kiểm tra hợp lệ, gom tập phim, bộ phim, mùa và thông tin nguồn video.
record PlaybackContext(
        CatalogEpisode episode,
        CatalogSeries series,
        CatalogSeason season,
        String assetId,
        Long assetDurationMs
) {

    // Dùng EpisodeRuntime.known() để bỏ qua thời lượng lỗi quá nhỏ (dưới 1 phút) khi hiển thị và đánh dấu hoàn thành.
    WatchDuration duration() {
        return WatchDuration.resolve(EpisodeRuntime.known(episode.getDurationMs()), assetDurationMs);
    }

    // Giữ nguyên durationMs gốc của tập để chặn vị trí lưu vượt quá độ dài thực tế kể cả với clip ngắn.
    WatchDuration storageDuration() {
        return WatchDuration.resolve(episode.getDurationMs(), assetDurationMs);
    }
}
