package com.dippie.service.catalog;

import com.dippie.dto.catalog.CatalogSnapshotView;
import com.dippie.exception.ConflictException;
import com.dippie.exception.InvalidRequestException;
import com.dippie.model.catalog.CatalogSnapshot;
import com.dippie.model.catalog.IndexSyncOutcome;
import com.dippie.repository.catalog.CatalogSnapshotRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

// Quản lý vòng đời các snapshot danh mục: liệt kê chỉ đọc, rà soát bản đang chờ và đồng bộ lại snapshot mới nhất.
@Service
public class CatalogSnapshotService {

    public static final long FIRST_GENERATION = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(CatalogSnapshotService.class);

    private final CatalogSnapshotRepository snapshots;
    private final CatalogExportService export;
    private final CatalogIndexPublisher publisher;
    private final Clock clock;

    public CatalogSnapshotService(CatalogSnapshotRepository snapshots, CatalogExportService export,
            CatalogIndexPublisher publisher, Clock clock) {
        this.snapshots = snapshots;
        this.export = export;
        this.publisher = publisher;
        this.clock = clock;
    }

    // Giữ readOnly hoàn toàn để HTTP GET /snapshots không bao giờ ghi ngầm xuống CSDL.
    @Transactional(readOnly = true)
    public List<CatalogSnapshotView> list() {
        return snapshots.findAllByOrderBySnapshotVersionDesc().stream()
                .map(CatalogSnapshotView::from)
                .toList();
    }

    // Rà soát các snapshot còn treo hoặc mang trạng thái cũ, chạy lúc khởi động thay vì sửa trong request GET.
    @Transactional
    public void reconcilePending() {
        LocalDateTime now = now();
        List<CatalogSnapshot> history = snapshots.findAllByOrderBySnapshotVersionDesc();
        for (int position = 0; position < history.size(); position++) {
            reconcile(history.get(position), position == 0, now);
        }
    }

    // Chỉ đẩy lại snapshot mới nhất sang bộ tìm kiếm; mấy bản cũ hơn còn chờ thì đánh dấu đã lỗi thời để khỏi đè dữ liệu mới.
    private void reconcile(CatalogSnapshot snapshot, boolean latest, LocalDateTime now) {
        snapshot.normalizeLegacyStatus(now);
        if (!snapshot.isAwaitingIndex()) {
            return;
        }
        if (latest) {
            snapshot.completeIndexSync(publish(snapshot, false).name(), now);
        } else {
            snapshot.supersede(now);
        }
    }

    // Việt nhớ giữ chốt chỉ cho retry snapshot mới nhất thôi, tránh đẩy nhầm thế hệ cũ ghi đè lên chỉ mục mới hơn.
    public CatalogSnapshotView retry(String id) {
        CatalogSnapshot snapshot = snapshots.findById(id)
                .orElseThrow(() -> new InvalidRequestException("Không tìm thấy snapshot có mã: " + id));
        CatalogSnapshot latest = snapshots.findFirstByOrderBySnapshotVersionDesc().orElseThrow();
        if (!snapshot.getId().equals(latest.getId())) {
            throw new ConflictException("Chỉ được đồng bộ lại snapshot mới nhất.");
        }
        String outcome = publish(snapshot, true).name();
        snapshot.recordRetry(outcome, now());
        return CatalogSnapshotView.from(snapshots.save(snapshot));
    }

    // Gọi sau khi transaction nhập đã commit để đẩy bản xuất mới sang bộ tìm kiếm và cập nhật trạng thái snapshot.
    public void synchronize(CatalogSnapshot snapshot) {
        String outcome = publish(snapshot, false).name();
        snapshot.completeIndexSync(outcome, now());
        snapshots.save(snapshot);
    }

    // Chặn mọi RuntimeException từ bộ tìm kiếm rồi đánh dấu SYNC_FAILED để luồng nhập chính vẫn trả kết quả bình thường.
    private IndexSyncOutcome publish(CatalogSnapshot snapshot, boolean retry) {
        try {
            IndexSyncOutcome outcome = publisher.publish(snapshot.getSnapshotVersion(), snapshot.getId(),
                    export.exportForIndex(), retry);
            return outcome == null ? IndexSyncOutcome.SYNC_FAILED : outcome;
        } catch (RuntimeException exception) {
            LOG.warn("Đồng bộ chỉ mục thất bại cho snapshot thế hệ {} ({})",
                    snapshot.getSnapshotVersion(), exception.getClass().getSimpleName());
            return IndexSyncOutcome.SYNC_FAILED;
        }
    }

    private LocalDateTime now() {
        return CatalogDates.now(clock);
    }
}
