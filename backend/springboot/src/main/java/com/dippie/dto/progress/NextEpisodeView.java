package com.dippie.dto.progress;

import com.dippie.model.progress.NextEpisodePolicy;

import com.fasterxml.jackson.annotation.JsonInclude;

// DTO trả thông tin tập được gợi ý; assetId chỉ xuất hiện trong JSON khi nối lại một tập đang xem dở.
public record NextEpisodeView(String seriesId, String episodeId, String episodeTitle, Integer seasonNumber,
        Integer episodeNumber, long resumePositionMs, NextEpisodePolicy.Reason reason,
        @JsonInclude(JsonInclude.Include.NON_NULL) String assetId) {
}
