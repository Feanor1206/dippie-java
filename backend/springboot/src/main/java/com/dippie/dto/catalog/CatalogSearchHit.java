package com.dippie.dto.catalog;

import com.dippie.common.rating.RatingSummary;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

// một dòng kết quả tìm kiếm, gắn thêm điểm đánh giá cho bộ phim với tập phim
public record CatalogSearchHit(String id, String type, String title, String parentTitle, String seriesId, String episodeId,
        String mediaAssetId, Long startMs, Long endMs, String snippet, @JsonUnwrapped RatingSummary ratings) {

    public CatalogSearchHit(String id, String type, String title, String parentTitle, String seriesId, String episodeId,
            String mediaAssetId, Long startMs, Long endMs, String snippet) {
        this(id, type, title, parentTitle, seriesId, episodeId, mediaAssetId, startMs, endMs, snippet, null);
    }

    // kiểm tra kết quả ở mức bộ phim hay tập phim, Việt đừng đổi tên thành isTitle() nha
    // Jackson sẽ tưởng là field "title" rồi đè tiêu đề thật thành true/false là toang
    public boolean titleLevel() {
        return type.equals("series") || type.equals("episode");
    }

    public CatalogSearchHit withRatings(RatingSummary summary) {
        return new CatalogSearchHit(id, type, title, parentTitle, seriesId, episodeId, mediaAssetId, startMs, endMs, snippet, summary);
    }
}
