package com.dippie.progress;

import com.dippie.catalog.query.CatalogReadModelCache;
import com.dippie.common.error.NotFoundException;
import com.dippie.security.CurrentViewer;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// Service lấy tập tiếp theo cho một bộ phim bằng cách ghép snapshot danh mục trong cache với tiến độ đã lưu và NextEpisodePolicy.
@Service
public class NextEpisodeService {

    private final CatalogReadModelCache cache;
    private final WatchProgressRepository progress;
    private final CurrentViewer currentViewer;

    public NextEpisodeService(CatalogReadModelCache cache, WatchProgressRepository progress, CurrentViewer currentViewer) {
        this.cache = cache;
        this.progress = progress;
        this.currentViewer = currentViewer;
    }

    // Kiểm tra bộ phim còn hiển thị công khai, lọc tập theo quyền người xem rồi giao cho NextEpisodePolicy chọn tập phù hợp nhất.
    @Transactional(readOnly = true)
    public Optional<NextEpisodeView> find(String seriesId) {
        var viewer = currentViewer.require();
        var catalog = cache.current();
        if (!catalog.visibleSeriesIds().contains(seriesId)) {
            throw new NotFoundException("Không tìm thấy bộ phim với mã: " + seriesId);
        }
        // Chỉ giữ tập mà tài khoản hiện tại có quyền mở (admin được thấy cả tập private)
        var episodes = catalog.seriesEpisodes(seriesId).stream()
                .filter(episode -> episode.memberAccessible() || viewer.isAdmin())
                .map(episode -> new NextEpisodePolicy.Episode(episode.id(), episode.title(), episode.seasonNumber(),
                        episode.episodeNumber(), episode.durationMs()))
                .toList();
        if (episodes.isEmpty()) {
            return Optional.empty();
        }
        return NextEpisodePolicy.pick(episodes, progress.findForSeries(viewer.userId(), seriesId))
                .map(choice -> new NextEpisodeView(seriesId, choice.episode().id(), choice.episode().title(),
                        choice.episode().seasonNumber(), choice.episode().episodeNumber(), choice.resumePositionMs(),
                        choice.reason(), choice.assetId()));
    }

    // DTO trả thông tin tập được gợi ý; assetId chỉ xuất hiện trong JSON khi nối lại một tập đang xem dở.
    public record NextEpisodeView(String seriesId, String episodeId, String episodeTitle, Integer seasonNumber,
            Integer episodeNumber, long resumePositionMs, NextEpisodePolicy.Reason reason,
            @JsonInclude(JsonInclude.Include.NON_NULL) String assetId) {
    }
}
