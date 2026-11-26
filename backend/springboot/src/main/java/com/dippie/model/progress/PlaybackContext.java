package com.dippie.model.progress;

import com.dippie.model.catalog.CatalogEpisode;
import com.dippie.model.catalog.CatalogSeason;
import com.dippie.model.catalog.CatalogSeries;
import com.dippie.model.catalog.EpisodeRuntime;

// ngữ cảnh phát đã check hợp lệ, gom tập, bộ, mùa với nguồn video
public record PlaybackContext(
        CatalogEpisode episode,
        CatalogSeries series,
        CatalogSeason season,
        String assetId,
        Long assetDurationMs
) {

    // qua EpisodeRuntime.known() để bỏ thời lượng metadata lỗi dưới 1 phút (như ep_jadoo_s01_0014) cho cả hiển thị, tính hoàn thành lẫn check positionMs
    public WatchDuration duration() {
        return WatchDuration.resolve(EpisodeRuntime.known(episode.getDurationMs()), assetDurationMs);
    }
}
