package com.dippie.dto.catalog;

import com.dippie.model.catalog.YoutubeSource;

import java.util.List;

// Kết quả chi tiết một tập phim kèm phân đoạn, tệp phát, phân cảnh và nguồn YouTube chính lẫn dự phòng.
public record CatalogEpisodeDetail(CatalogEpisodeView episode, List<CatalogSegmentView> segments,
        List<CatalogMediaAssetView> mediaAssets, List<CatalogSceneView> scenes, YoutubeSource youtube,
        YoutubeSource youtubeFallback) {

    public CatalogEpisodeDetail {
        segments = segments == null ? List.of() : List.copyOf(segments);
        mediaAssets = mediaAssets == null ? List.of() : List.copyOf(mediaAssets);
        scenes = scenes == null ? List.of() : List.copyOf(scenes);
    }
}
