package com.dippie.dto.catalog;

import com.dippie.common.rating.RatingSummary;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

// Một dòng kết quả trả ra giao diện tìm kiếm, gắn thêm điểm đánh giá cho bộ phim và tập phim.
public record CatalogSearchHit(String id, String type, String title, String parentTitle, String seriesId, String episodeId,
        String mediaAssetId, Long startMs, Long endMs, String snippet, @JsonUnwrapped RatingSummary ratings) {

    public CatalogSearchHit(String id, String type, String title, String parentTitle, String seriesId, String episodeId,
            String mediaAssetId, Long startMs, Long endMs, String snippet) {
        this(id, type, title, parentTitle, seriesId, episodeId, mediaAssetId, startMs, endMs, snippet, null);
    }

    // Kết quả ở mức bộ phim hoặc tập phim. Việt đừng đặt lại tên kiểu isTitle() nhé,
    // Jackson sẽ coi đó là thuộc tính "title" rồi ghi đè tiêu đề thật bằng true/false.
    public boolean titleLevel() {
        return type.equals("series") || type.equals("episode");
    }

    public CatalogSearchHit withRatings(RatingSummary summary) {
        return new CatalogSearchHit(id, type, title, parentTitle, seriesId, episodeId, mediaAssetId, startMs, endMs, snippet, summary);
    }
}
