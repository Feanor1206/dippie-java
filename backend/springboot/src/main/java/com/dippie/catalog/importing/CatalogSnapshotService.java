package com.dippie.catalog.importing;

import com.dippie.catalog.domain.CatalogSnapshot;
import com.dippie.catalog.domain.CatalogSnapshotRepository;
import com.dippie.catalog.domain.CatalogSnapshotView;
import com.dippie.common.error.ConflictException;
import com.dippie.common.error.InvalidRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
class CatalogSnapshotService {

    static final long FIRST_GENERATION = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(CatalogSnapshotService.class);

    private final CatalogSnapshotRepository snapshots;
    private final CatalogExportService export;
    private final CatalogIndexPublisher publisher;
    private final Clock clock;

    CatalogSnapshotService(CatalogSnapshotRepository snapshots, CatalogExportService export,
            CatalogIndexPublisher publisher, Clock clock) {
        this.snapshots = snapshots;
        this.export = export;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Transactional
    public List<CatalogSnapshotView> list() {
        LocalDateTime now = now();
        List<CatalogSnapshot> history = snapshots.findAllByOrderBySnapshotVersionDesc();
        for (int position = 0; position < history.size(); position++) {
            reconcile(history.get(position), position == 0, now);
        }
        return history.stream().map(CatalogSnapshotView::from).toList();
    }

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

    CatalogSnapshotView retry(String id) {
        CatalogSnapshot snapshot = snapshots.findById(id)
                .orElseThrow(() -> new InvalidRequestException("Không tìm thấy snapshot với ID: " + id));
        CatalogSnapshot latest = snapshots.findFirstByOrderBySnapshotVersionDesc().orElseThrow();
        if (!snapshot.getId().equals(latest.getId())) {
            throw new ConflictException("Chỉ có thể thử lại snapshot mới nhất.");
        }
        String outcome = publish(snapshot, true).name();
        snapshot.recordRetry(outcome, now());
        return CatalogSnapshotView.from(snapshots.save(snapshot));
    }

    void synchronize(CatalogSnapshot snapshot) {
        String outcome = publish(snapshot, false).name();
        snapshot.completeIndexSync(outcome, now());
        snapshots.save(snapshot);
    }

    private IndexSyncOutcome publish(CatalogSnapshot snapshot, boolean retry) {
        try {
            IndexSyncOutcome outcome = publisher.publish(snapshot.getSnapshotVersion(), snapshot.getId(),
                    export.exportForIndex(), retry);
            return outcome == null ? IndexSyncOutcome.SYNC_FAILED : outcome;
        } catch (RuntimeException exception) {
            LOG.warn("Catalog index sync failed for snapshot generation {} ({})",
                    snapshot.getSnapshotVersion(), exception.getClass().getSimpleName());
            return IndexSyncOutcome.SYNC_FAILED;
        }
    }

    private LocalDateTime now() {
        return CatalogDates.now(clock);
    }
}
