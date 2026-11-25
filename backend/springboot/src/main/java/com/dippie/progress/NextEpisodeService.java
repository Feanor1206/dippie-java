package com.dippie.progress;

import com.dippie.catalog.query.CatalogReadModelCache;
import com.dippie.common.error.NotFoundException;
import com.dippie.security.CurrentViewer;
import org.springframework.stereotype.Service;

import java.util.Optional;

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

    public Optional<NextEpisodeView> find(String seriesId) {
        var viewer = currentViewer.require();
        var catalog = cache.current();
        if (!catalog.visibleSeriesIds().contains(seriesId)) {
            throw new NotFoundException("Không tìm thấy bộ phim (series) với mã: " + seriesId);
        }
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
                        choice.reason()));
    }

    public record NextEpisodeView(String seriesId, String episodeId, String episodeTitle, Integer seasonNumber,
            Integer episodeNumber, long resumePositionMs, NextEpisodePolicy.Reason reason) {
    }
}
