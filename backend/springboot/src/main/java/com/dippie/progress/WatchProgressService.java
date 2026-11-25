package com.dippie.progress;

import com.dippie.catalog.domain.CatalogEpisode;
import com.dippie.catalog.domain.CatalogMediaAsset;
import com.dippie.catalog.domain.CatalogSeason;
import com.dippie.catalog.domain.CatalogSeries;
import com.dippie.catalog.query.CatalogReadModel;
import com.dippie.catalog.query.CatalogReadModelCache;
import com.dippie.common.error.ConflictException;
import com.dippie.common.error.InvalidRequestException;
import com.dippie.common.error.NotFoundException;
import com.dippie.media.MediaAsset;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class WatchProgressService {

    private static final int RECENT_LIMIT = 30;

    private final WatchProgressRepository repository;
    private final PlaybackContextResolver contextResolver;
    private final CatalogReadModelCache catalog;
    private final CurrentViewer currentViewer;
    private final TransactionTemplate requiresNewTransaction;
    private final TransactionTemplate readTransaction;
    private final Clock clock;

    public WatchProgressService(
            WatchProgressRepository repository,
            PlaybackContextResolver contextResolver,
            CatalogReadModelCache catalog,
            CurrentViewer currentViewer,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this.repository = repository;
        this.contextResolver = contextResolver;
        this.catalog = catalog;
        this.currentViewer = currentViewer;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.clock = clock;
    }

    public List<WatchProgressView> listRecent() {
        Viewer viewer = currentViewer.require();
        CatalogReadModel model = catalog.current();
        return readTransaction.execute(status -> {
            List<Object[]> rows = repository.findRecentWithContext(viewer.userId(), PageRequest.of(0, RECENT_LIMIT));
            List<WatchProgressView> result = new ArrayList<>();
            for (Object[] row : rows) {
                WatchProgress progress = (WatchProgress) row[0];
                CatalogEpisode episode = (CatalogEpisode) row[1];
                CatalogSeries series = (CatalogSeries) row[2];
                CatalogSeason season = (CatalogSeason) row[3];
                CatalogMediaAsset catalogAsset = (CatalogMediaAsset) row[4];
                MediaAsset mediaAsset = (MediaAsset) row[5];
                contextResolver.validate(
                        episode,
                        series,
                        season,
                        catalogAsset,
                        mediaAsset,
                        progress.getEpisodeId(),
                        viewer
                ).ifPresent(ctx -> result.add(WatchProgressView.of(progress, ctx,
                        model.episodeThumbnail(episode.getId(), series.getId()))));
            }
            return List.copyOf(result);
        });
    }

    public WatchProgressView getForEpisode(String episodeId) {
        Viewer viewer = currentViewer.require();
        CatalogReadModel model = catalog.current();
        return readTransaction.execute(status -> {
            WatchProgress progress = repository.findByUserIdAndEpisodeId(viewer.userId(), episodeId)
                    .orElseThrow(() -> notPlayable(episodeId));
            PlaybackContext context = contextResolver.resolve(episodeId, progress.getAssetId(), viewer)
                    .orElseThrow(() -> notPlayable(episodeId));
            return WatchProgressView.of(progress, context,
                    model.episodeThumbnail(episodeId, context.series().getId()));
        });
    }

    public WatchProgressView save(String episodeId, JsonNode body) {
        Viewer viewer = currentViewer.require();
        WatchProgressCommand command = WatchProgressCommand.from(body);
        PlaybackContext context = readTransaction.execute(status ->
                contextResolver.resolve(episodeId, command.assetId(), viewer).orElse(null)
        );
        if (context == null) {
            throw notPlayable(episodeId);
        }
        if (context.duration().isExceededBy(command.positionMs())) {
            throw new InvalidRequestException("Vị trí xem vượt quá thời lượng video.");
        }

        String thumbnail = catalog.current().episodeThumbnail(episodeId, context.series().getId());
        WatchProgress saved = command.isCreate()
                ? createProgress(viewer.userId(), episodeId, context.catalogAsset().getId(), command.positionMs())
                : updateProgress(viewer.userId(), episodeId, context.catalogAsset().getId(), command);
        return WatchProgressView.of(saved, context, thumbnail);
    }

    public void delete(String episodeId) {
        Viewer viewer = currentViewer.require();
        requiresNewTransaction.executeWithoutResult(status ->
                repository.deleteByUserIdAndEpisodeId(viewer.userId(), episodeId)
        );
    }

    private WatchProgress createProgress(Long userId, String episodeId, String assetId, long positionMs) {
        Optional<WatchProgress> existing = readTransaction.execute(status ->
                repository.findByUserIdAndEpisodeId(userId, episodeId)
        );
        if (existing.isPresent()) {
            throw new ConflictException(WatchProgress.CONFLICT_MESSAGE);
        }
        WatchProgress created = WatchProgress.start(userId, episodeId, assetId, positionMs, clock);
        try {
            return requiresNewTransaction.execute(status -> repository.saveAndFlush(created));
        } catch (DataIntegrityViolationException ex) {
            boolean duplicate = Boolean.TRUE.equals(readTransaction.execute(status ->
                    repository.findByUserIdAndEpisodeId(userId, episodeId).isPresent()
            ));
            if (duplicate) {
                throw new ConflictException(WatchProgress.CONFLICT_MESSAGE);
            }
            throw ex;
        }
    }

    private WatchProgress updateProgress(
            Long userId,
            String episodeId,
            String assetId,
            WatchProgressCommand command
    ) {
        WatchProgress current = readTransaction.execute(status ->
                repository.findByUserIdAndEpisodeId(userId, episodeId).orElse(null)
        );
        if (current == null) {
            throw new ConflictException(WatchProgress.CONFLICT_MESSAGE);
        }
        current.advance(command.expectedVersion(), assetId, command.positionMs(), clock);
        int updated = requiresNewTransaction.execute(status -> repository.advanceVersion(
                current.getId(),
                command.expectedVersion(),
                current.getOptLockVersion(),
                current.getAssetId(),
                current.getPositionMs(),
                current.getUpdatedAt()
        ));
        if (updated != 1) {
            throw new ConflictException(WatchProgress.CONFLICT_MESSAGE);
        }
        return current;
    }

    private static NotFoundException notPlayable(String episodeId) {
        return new NotFoundException("Không tìm thấy tập có thể phát: " + episodeId);
    }
}
