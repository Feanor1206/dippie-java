package com.dippie.service.progress;

import com.dippie.dto.progress.WatchProgressView;
import com.dippie.exception.ConflictException;
import com.dippie.exception.InvalidRequestException;
import com.dippie.exception.NotFoundException;
import com.dippie.model.progress.PlaybackContext;
import com.dippie.model.progress.WatchProgress;
import com.dippie.model.progress.WatchProgressCommand;
import com.dippie.repository.progress.WatchProgressRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;
import com.dippie.service.catalog.CatalogReadModel;
import com.dippie.service.catalog.CatalogReadModelCache;
import com.dippie.service.play.PlayPlaybackGuard;
import com.dippie.service.play.RecapQuizService;
import com.dippie.service.profile.ScreenTimeService;
import com.dippie.service.vip.VipTitleService;
import com.dippie.service.progress.PlaybackContextResolver.PendingPlayback;

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

// kiểu Facade lo đọc, lưu với xóa tiến độ xem có optimistic locking
// tách riêng transaction đọc với ghi (REQUIRES_NEW), còn bước check file trên đĩa đẩy ra ngoài kẻo ngâm connection DB
@Service
public class WatchProgressService {

    private static final int RECENT_LIMIT = 30;
    private static final int RECENT_FETCH_LIMIT = RECENT_LIMIT * 3;

    private final WatchProgressRepository repository;
    private final PlaybackContextResolver contextResolver;
    private final CatalogReadModelCache catalog;
    private final CurrentViewer currentViewer;
    private final ScreenTimeService screenTimeService;
    private final VipTitleService vipTitleService;
    private final PlayPlaybackGuard playPlaybackGuard;
    private final RecapQuizService recapQuizService;
    private final TransactionTemplate requiresNewTransaction;
    private final TransactionTemplate readTransaction;
    private final Clock clock;

    public WatchProgressService(WatchProgressRepository repository, PlaybackContextResolver contextResolver,
            CatalogReadModelCache catalog, CurrentViewer currentViewer, ScreenTimeService screenTimeService,
            VipTitleService vipTitleService, PlayPlaybackGuard playPlaybackGuard, RecapQuizService recapQuizService,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.repository = repository;
        this.contextResolver = contextResolver;
        this.catalog = catalog;
        this.currentViewer = currentViewer;
        this.screenTimeService = screenTimeService;
        this.vipTitleService = vipTitleService;
        this.playPlaybackGuard = playPlaybackGuard;
        this.recapQuizService = recapQuizService;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.clock = clock;
    }

    // trả tối đa 30 tập xem gần đây còn mở được, kéo từng lô 90 dòng để lỡ dính nhiều tập bị ẩn vẫn bù đủ 30 mục
    public List<WatchProgressView> listRecent() {
        Viewer viewer = currentViewer.require();
        CatalogReadModel model = catalog.current();
        List<WatchProgressView> result = new ArrayList<>(RECENT_LIMIT);
        for (int page = 0; result.size() < RECENT_LIMIT; page++) {
            int currentPage = page;
            PageBatch batch = readTransaction.execute(status -> {
                List<WatchProgressRepository.ContextRow> rows = repository.findRecentWithContext(
                        viewer.userId(), viewer.profileScopeId(), PageRequest.of(currentPage, RECENT_FETCH_LIMIT));
                List<LoadedProgress> candidates = new ArrayList<>(Math.min(rows.size(), RECENT_LIMIT));
                for (WatchProgressRepository.ContextRow row : rows) {
                    contextResolver.validateRow(row, viewer)
                            .ifPresent(pending -> candidates.add(new LoadedProgress(row.progress(), pending)));
                }
                return new PageBatch(candidates, rows.size() == RECENT_FETCH_LIMIT);
            });
            // đẩy bước check file trên đĩa ra ngoài transaction rồi gom đủ tối đa RECENT_LIMIT mục
            for (LoadedProgress item : batch.candidates()) {
                if (result.size() >= RECENT_LIMIT) {
                    break;
                }
                contextResolver.verifyPlayable(item.pending())
                        .ifPresent(context -> result.add(WatchProgressView.of(item.progress(), context,
                                model.episodeThumbnail(context.episode().getId(), context.series().getId()))));
            }
            if (!batch.hasMore()) {
                break;
            }
        }
        return List.copyOf(result);
    }

    // lấy tiến độ của 1 tập, chưa xem bao giờ hay tập đã bị ẩn, không phát được nữa thì 404
    public WatchProgressView getForEpisode(String episodeId) {
        Viewer viewer = currentViewer.require();
        CatalogReadModel model = catalog.current();
        LoadedProgress loaded = readTransaction.execute(status -> {
            WatchProgress progress = repository.findByUserIdAndProfileScopeIdAndEpisodeId(
                            viewer.userId(), viewer.profileScopeId(), episodeId)
                    .orElseThrow(() -> notPlayable(episodeId));
            PendingPlayback pending = contextResolver.loadCandidate(episodeId, progress.getAssetId(), viewer)
                    .orElseThrow(() -> notPlayable(episodeId));
            return new LoadedProgress(progress, pending);
        });
        PlaybackContext context = contextResolver.verifyPlayable(loaded.pending())
                .orElseThrow(() -> notPlayable(episodeId));
        return WatchProgressView.of(loaded.progress(), context,
                model.episodeThumbnail(episodeId, context.series().getId()));
    }

    // tạo mới (expectedVersion = -1) hoặc cập nhật tiến độ sau khi check tập mở được và mốc xem không quá thời lượng video
    public WatchProgressView save(String episodeId, JsonNode body) {
        Viewer viewer = currentViewer.require();
        WatchProgressCommand command = WatchProgressCommand.from(body);
        // nạp ngữ cảnh phát, vẫn nhận tiến độ YouTube dự phòng kể cả lúc tập có sẵn file nội bộ
        PendingPlayback pending = readTransaction.execute(status ->
                contextResolver.loadCandidate(episodeId, command.assetId(), viewer)
                        .orElseThrow(() -> notPlayable(episodeId))
        );
        // check file trên đĩa ngoài transaction rồi chặn mốc xem vượt quá thời lượng thật của video
        PlaybackContext context = contextResolver.verifyPlayable(pending)
                .orElseThrow(() -> notPlayable(episodeId));
        // kiểm tra khóa theo thứ tự: hạn mức ngày, Dieppie nghỉ mắt, ôn tập qua tập rồi tập VIP
        screenTimeService.requireBudgetUnlockedForPlayback(viewer);
        if (viewer.isChildProfile()) {
            playPlaybackGuard.requireWatchPetUnlocked(viewer.userId(), viewer.profileId());
            playPlaybackGuard.requireEpisodesUnlocked(viewer, List.of(episodeId));
        }
        vipTitleService.requireEpisodeUnlocked(viewer, context.series().getId(), episodeId);
        if (context.duration().isExceededBy(command.positionMs())) {
            throw new InvalidRequestException("Thời điểm xem không thể vượt quá thời lượng video.");
        }

        String thumbnail = catalog.current().episodeThumbnail(episodeId, context.series().getId());
        WatchProgress saved = command.isCreate()
                ? createProgress(viewer.userId(), viewer.profileId(), viewer.profileScopeId(), episodeId, context.assetId(), command.positionMs())
                : updateProgress(viewer.userId(), viewer.profileScopeId(), episodeId, context.assetId(), command);
        if (viewer.isChildProfile() && context.duration().isCompletedAt(saved.getPositionMs())) {
            recapQuizService.onEpisodeCompleted(viewer.profileId(), context.series().getId(), episodeId);
        }
        return WatchProgressView.of(saved, context, thumbnail);
    }

    public void delete(String episodeId) {
        Viewer viewer = currentViewer.require();
        requiresNewTransaction.executeWithoutResult(status ->
                repository.deleteByUserIdAndProfileScopeIdAndEpisodeId(viewer.userId(), viewer.profileScopeId(), episodeId)
        );
    }

    // Việt đừng bỏ catch DataIntegrityViolationException nha, 2 request tạo version -1 đụng khóa thì check DB trả 409 chứ không 500
    private WatchProgress createProgress(Long userId, Long profileId, long profileScopeId, String episodeId, String assetId,
            long positionMs) {
        Optional<WatchProgress> existing = readTransaction.execute(status ->
                repository.findByUserIdAndProfileScopeIdAndEpisodeId(userId, profileScopeId, episodeId)
        );
        if (existing.isPresent()) {
            throw new ConflictException(WatchProgress.CONFLICT_MESSAGE);
        }
        WatchProgress created = WatchProgress.start(userId, profileId, episodeId, assetId, positionMs, clock);
        try {
            return requiresNewTransaction.execute(status -> repository.saveAndFlush(created));
        } catch (DataIntegrityViolationException ex) {
            boolean duplicate = Boolean.TRUE.equals(readTransaction.execute(status ->
                    repository.findByUserIdAndProfileScopeIdAndEpisodeId(userId, profileScopeId, episodeId).isPresent()
            ));
            if (duplicate) {
                throw new ConflictException(WatchProgress.CONFLICT_MESSAGE);
            }
            throw ex;
        }
    }

    // cập nhật nguyên tử theo expectedVersion, số dòng sửa khác 1 là biết có request khác nhảy version trước rồi
    private WatchProgress updateProgress(Long userId, long profileScopeId, String episodeId, String assetId,
            WatchProgressCommand command) {
        WatchProgress current = readTransaction.execute(status ->
                repository.findByUserIdAndProfileScopeIdAndEpisodeId(userId, profileScopeId, episodeId).orElse(null));
        if (current == null) {
            throw new ConflictException(WatchProgress.CONFLICT_MESSAGE);
        }
        current.advance(command.expectedVersion(), assetId, command.positionMs(), clock);
        int updated = requiresNewTransaction.execute(status -> repository.advanceVersion(current.getId(),
                command.expectedVersion(), current.getOptLockVersion(), current.getAssetId(),
                current.getPositionMs(), current.getUpdatedAt()));
        if (updated != 1) {
            throw new ConflictException(WatchProgress.CONFLICT_MESSAGE);
        }
        return current;
    }

    private static NotFoundException notPlayable(String episodeId) {
        return new NotFoundException("Không tìm thấy tập phim để phát: " + episodeId);
    }

    // gói tiến độ lấy từ DB kèm ngữ cảnh chờ check file trên đĩa
    private record LoadedProgress(WatchProgress progress, PendingPlayback pending) {}

    // kết quả 1 lô đọc lịch sử kèm cờ báo dưới DB còn trang sau không
    private record PageBatch(List<LoadedProgress> candidates, boolean hasMore) {}
}
