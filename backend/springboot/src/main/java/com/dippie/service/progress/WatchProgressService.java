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

// Facade điều phối việc đọc, lưu và xóa tiến độ xem kèm optimistic locking.
// Giao dịch đọc và ghi (REQUIRES_NEW) được tách riêng, còn bước kiểm tra tệp trên đĩa chạy bên ngoài giao dịch để tránh giữ connection DB lâu.
@Service
public class WatchProgressService {

    private static final int RECENT_LIMIT = 30;
    private static final int RECENT_FETCH_LIMIT = RECENT_LIMIT * 3;

    private final WatchProgressRepository repository;
    private final PlaybackContextResolver contextResolver;
    private final CatalogReadModelCache catalog;
    private final CurrentViewer currentViewer;
    private final TransactionTemplate requiresNewTransaction;
    private final TransactionTemplate readTransaction;
    private final Clock clock;

    public WatchProgressService(WatchProgressRepository repository, PlaybackContextResolver contextResolver,
            CatalogReadModelCache catalog, CurrentViewer currentViewer, PlatformTransactionManager transactionManager,
            Clock clock) {
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

    // Trả tối đa 30 tập xem gần đây còn phát được; đọc theo từng lô 90 dòng để lỡ tài khoản có nhiều tập bị ẩn vẫn kéo bù đủ 30 mục hợp lệ.
    public List<WatchProgressView> listRecent() {
        Viewer viewer = currentViewer.require();
        CatalogReadModel model = catalog.current();
        List<WatchProgressView> result = new ArrayList<>(RECENT_LIMIT);
        for (int page = 0; result.size() < RECENT_LIMIT; page++) {
            int currentPage = page;
            PageBatch batch = readTransaction.execute(status -> {
                List<WatchProgressRepository.ContextRow> rows = repository.findRecentWithContext(
                        viewer.userId(), PageRequest.of(currentPage, RECENT_FETCH_LIMIT));
                List<LoadedProgress> candidates = new ArrayList<>(Math.min(rows.size(), RECENT_LIMIT));
                for (WatchProgressRepository.ContextRow row : rows) {
                    contextResolver.validateRow(row, viewer)
                            .ifPresent(pending -> candidates.add(new LoadedProgress(row.progress(), pending)));
                }
                return new PageBatch(candidates, rows.size() == RECENT_FETCH_LIMIT);
            });
            // Đẩy bước kiểm tra tệp trên ổ đĩa ra ngoài giao dịch rồi gom đủ tối đa RECENT_LIMIT mục
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

    // Đọc tiến độ của một tập và báo 404 nếu chưa có lịch sử hoặc tập đã bị ẩn, không còn phát được.
    public WatchProgressView getForEpisode(String episodeId) {
        Viewer viewer = currentViewer.require();
        CatalogReadModel model = catalog.current();
        LoadedProgress loaded = readTransaction.execute(status -> {
            WatchProgress progress = repository.findByUserIdAndEpisodeId(viewer.userId(), episodeId)
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

    // Lưu tiến độ mới (expectedVersion = -1) hoặc cập nhật bản ghi cũ sau khi kiểm tra tập phát được và thời điểm không vượt thời lượng video.
    public WatchProgressView save(String episodeId, JsonNode body) {
        Viewer viewer = currentViewer.require();
        WatchProgressCommand command = WatchProgressCommand.from(body);
        // Nạp ngữ cảnh phát; vẫn nhận tiến độ YouTube dự phòng kể cả khi tập cũng có file nội bộ
        PendingPlayback pending = readTransaction.execute(status ->
                contextResolver.loadCandidate(episodeId, command.assetId(), viewer)
                        .orElseThrow(() -> notPlayable(episodeId))
        );
        // Kiểm tra tệp trên đĩa ngoài giao dịch rồi chặn thời điểm xem vượt quá độ dài hiệu dụng của video
        PlaybackContext context = contextResolver.verifyPlayable(pending)
                .orElseThrow(() -> notPlayable(episodeId));
        if (context.duration().isExceededBy(command.positionMs())) {
            throw new InvalidRequestException("Thời điểm xem không thể vượt quá thời lượng video.");
        }

        String thumbnail = catalog.current().episodeThumbnail(episodeId, context.series().getId());
        WatchProgress saved = command.isCreate()
                ? createProgress(viewer.userId(), episodeId, context.assetId(), command.positionMs())
                : updateProgress(viewer.userId(), episodeId, context.assetId(), command);
        return WatchProgressView.of(saved, context, thumbnail);
    }

    public void delete(String episodeId) {
        Viewer viewer = currentViewer.require();
        requiresNewTransaction.executeWithoutResult(status ->
                repository.deleteByUserIdAndEpisodeId(viewer.userId(), episodeId)
        );
    }

    // Việt đừng bỏ đoạn bắt DataIntegrityViolationException ở đây nhé: hai request cùng tạo version -1 sẽ đụng unique key, mình kiểm tra lại trong DB để trả đúng 409 Conflict thay vì 500.
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

    // Cập nhật nguyên tử theo expectedVersion; nếu số dòng cập nhật khác 1 nghĩa là có request khác đã nhảy version trước.
    private WatchProgress updateProgress(Long userId, String episodeId, String assetId, WatchProgressCommand command) {
        WatchProgress current = readTransaction.execute(status ->
                repository.findByUserIdAndEpisodeId(userId, episodeId).orElse(null));
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

    // Gói bản ghi tiến độ lấy từ DB cùng ngữ cảnh đang chờ kiểm tra tệp trên đĩa.
    private record LoadedProgress(WatchProgress progress, PendingPlayback pending) {}

    // Kết quả một lô đọc lịch sử kèm cờ báo DB còn bản ghi ở trang sau hay đã hết.
    private record PageBatch(List<LoadedProgress> candidates, boolean hasMore) {}
}
