package com.dippie.model.progress;

import com.dippie.model.catalog.CatalogEpisode;
import com.dippie.model.catalog.CatalogSeason;
import com.dippie.model.catalog.CatalogSeries;
import com.dippie.model.catalog.EpisodeRuntime;

// Ngữ cảnh phát đã kiểm tra hợp lệ, gom tập phim, bộ phim, mùa và thông tin nguồn video.
public record PlaybackContext(
        CatalogEpisode episode,
        CatalogSeries series,
        CatalogSeason season,
        String assetId,
        Long assetDurationMs
) {

    // Dùng EpisodeRuntime.known() để bỏ qua thời lượng metadata lỗi (dưới 1 phút, như ep_jadoo_s01_0014) cho cả hiển thị, đánh dấu hoàn thành lẫn kiểm tra positionMs khi lưu.
    public WatchDuration duration() {
        return WatchDuration.resolve(EpisodeRuntime.known(episode.getDurationMs()), assetDurationMs);
    }
}
