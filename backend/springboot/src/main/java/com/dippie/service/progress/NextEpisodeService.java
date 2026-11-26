package com.dippie.service.progress;

import com.dippie.dto.progress.NextEpisodeView;
import com.dippie.exception.NotFoundException;
import com.dippie.model.progress.NextEpisodePolicy;
import com.dippie.model.progress.WatchProgress;
import com.dippie.repository.progress.WatchProgressRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.service.catalog.CatalogReadModelCache;
import com.dippie.service.progress.PlaybackContextResolver.PendingPlayback;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// lấy tập tiếp theo cho 1 bộ phim bằng cách ghép snapshot danh mục trong cache với tiến độ hợp lệ qua NextEpisodePolicy
// tách bước đọc DB khỏi verifyPlayable ngoài transaction giống WatchProgressService để khỏi ngâm connection lúc check file trên đĩa
@Service
public class NextEpisodeService {

    private final CatalogReadModelCache cache;
    private final WatchProgressRepository progress;
    private final PlaybackContextResolver contextResolver;
    private final CurrentViewer currentViewer;
    private final TransactionTemplate readTransaction;

    public NextEpisodeService(CatalogReadModelCache cache, WatchProgressRepository progress,
            PlaybackContextResolver contextResolver, CurrentViewer currentViewer,
            PlatformTransactionManager transactionManager) {
        this.cache = cache;
        this.progress = progress;
        this.contextResolver = contextResolver;
        this.currentViewer = currentViewer;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
    }

    // check bộ phim còn đang mở, lọc tập với tiến độ theo quyền user rồi đẩy cho NextEpisodePolicy chọn tập hợp nhất
    public Optional<NextEpisodeView> find(String seriesId) {
        var viewer = currentViewer.require();
        var catalog = cache.current();
        if (!catalog.visibleSeriesIds().contains(seriesId)) {
            throw new NotFoundException("Không tìm thấy bộ phim với mã: " + seriesId);
        }
        // chỉ giữ tập thật sự mở được theo role hiện tại (admin được mở thêm mấy tập chỉ có file private/draft)
        var episodes = catalog.seriesEpisodes(seriesId).stream()
                .filter(episode -> viewer.isAdmin() ? episode.adminAccessible() : episode.memberAccessible())
                .map(episode -> new NextEpisodePolicy.Episode(episode.id(), episode.title(), episode.seasonNumber(),
                        episode.episodeNumber(), episode.durationMs()))
                .toList();
        if (episodes.isEmpty()) {
            return Optional.empty();
        }
        List<LoadedProgress> loaded = readTransaction.execute(status -> {
            List<WatchProgressRepository.ContextRow> rows = progress.findForSeriesWithContext(viewer.userId(), seriesId);
            List<LoadedProgress> candidates = new ArrayList<>(rows.size());
            for (WatchProgressRepository.ContextRow row : rows) {
                contextResolver.validateRow(row, viewer)
                        .ifPresent(pending -> candidates.add(new LoadedProgress(row.progress(), pending)));
            }
            return candidates;
        });
        List<NextEpisodePolicy.Progress> validProgress = new ArrayList<>(loaded.size());
        for (LoadedProgress item : loaded) {
            contextResolver.verifyPlayable(item.pending())
                    .ifPresent(context -> validProgress.add(new NextEpisodePolicy.Progress(
                            item.progress().getEpisodeId(),
                            item.progress().getPositionMs(),
                            item.progress().getUpdatedAt(),
                            context.assetDurationMs(),
                            context.assetId(),
                            item.progress().getId())));
        }
        return NextEpisodePolicy.pick(episodes, validProgress)
                .map(choice -> new NextEpisodeView(seriesId, choice.episode().id(), choice.episode().title(),
                        choice.episode().seasonNumber(), choice.episode().episodeNumber(), choice.resumePositionMs(),
                        choice.reason(), choice.assetId()));
    }

    // gói tiến độ lấy từ DB kèm ngữ cảnh chờ check file trên đĩa
    private record LoadedProgress(WatchProgress progress, PendingPlayback pending) {}
}
