package com.dippie.dto.progress;

import com.dippie.model.progress.NextEpisodePolicy;

import com.fasterxml.jackson.annotation.JsonInclude;

// view tập gợi ý tiếp theo, assetId chỉ hiện trong JSON khi nối lại tập đang xem dở
public record NextEpisodeView(String seriesId, String episodeId, String episodeTitle, Integer seasonNumber,
        Integer episodeNumber, long resumePositionMs, NextEpisodePolicy.Reason reason,
        @JsonInclude(JsonInclude.Include.NON_NULL) String assetId) {
}
