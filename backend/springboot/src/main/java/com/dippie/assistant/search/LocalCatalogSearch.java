package com.dippie.assistant.search;

import com.dippie.catalog.domain.CatalogImportedEvent;
import com.dippie.catalog.domain.CatalogSnapshotRepository;
import com.dippie.catalog.domain.MediaFileAvailability;
import com.dippie.catalog.importing.CatalogExportService;
import com.dippie.catalog.importing.IndexSyncOutcome;
import com.dippie.catalog.query.CatalogReadModelCache;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

// Bản cài đặt cục bộ của CatalogSearch giữ LexicalIndex trong bộ nhớ qua AtomicReference.
// Lớp này cũng là Observer nghe CatalogImportedEvent để tự dựng lại chỉ mục mà không làm gián đoạn truy vấn đang chạy.
@Service
class LocalCatalogSearch implements CatalogSearch {

    private static final Logger log = LoggerFactory.getLogger(LocalCatalogSearch.class);

    private final CatalogExportService export;
    private final CatalogSnapshotRepository snapshots;
    private final CatalogReadModelCache catalog;
    private final MediaFileAvailability files;
    private final AtomicReference<Snapshot> current = new AtomicReference<>();

    LocalCatalogSearch(CatalogExportService export, CatalogSnapshotRepository snapshots, CatalogReadModelCache catalog,
            MediaFileAvailability files) {
        this.export = export;
        this.snapshots = snapshots;
        this.catalog = catalog;
        this.files = files;
    }

    @Override
    public boolean isReady() {
        return index() != null;
    }

    @Override
    public int documentCount() {
        Snapshot snapshot = current.get();
        return snapshot == null ? 0 : snapshot.index().size();
    }

    @Override
    public List<SearchHit> search(String query, int limit, SearchConstraints constraints) {
        LexicalIndex index = index();
        return index == null ? List.of() : matches(index, query, limit, constraints).stream()
                .map(LexicalIndex.Match::hit).toList();
    }

    @Override
    public Optional<SearchHit> item(String id) {
        LexicalIndex index = index();
        return index == null ? Optional.empty() : index.item(id, discoverable());
    }

    @Override
    public Optional<SearchHit> nextSegment(String documentId) {
        LexicalIndex index = index();
        return index == null ? Optional.empty() : index.nextSegment(documentId, discoverable());
    }

    @Override
    public List<SearchHit> similarSeries(String reference, int limit) {
        LexicalIndex index = index();
        return index == null ? List.of() : index.similarSeries(reference, limit);
    }

    @Override
    public List<String> availableSeriesTitles() {
        LexicalIndex index = index();
        return index == null ? List.of() : index.availableSeriesTitles();
    }

    // Chạy truy vấn trên LexicalIndex kèm bộ lọc kiểm tra tập phim có tệp phát khả dụng hay không.
    List<LexicalIndex.Match> matches(LexicalIndex index, String query, int limit, SearchConstraints constraints) {
        var filters = constraints == null ? SearchConstraints.NONE : constraints;
        return index.search(query, limit, filters, discoverable());
    }

    private Predicate<String> discoverable() {
        return catalog.current().playableFilter(files);
    }

    // Trả về chỉ mục đang dùng hoặc dựng lười lần đầu khi chưa có phiên bản nào trong bộ nhớ.
    LexicalIndex index() {
        Snapshot snapshot = current.get();
        return snapshot == null ? initialize() : snapshot.index();
    }

    private synchronized LexicalIndex initialize() {
        Snapshot snapshot = current.get();
        if (snapshot == null) {
            try {
                snapshot = new Snapshot(0, null, build(export.exportForIndex()));
                current.set(snapshot);
            } catch (RuntimeException exception) {
                log.warn("Khởi tạo chỉ mục tìm kiếm cục bộ thất bại", exception);
                return null;
            }
        }
        return snapshot.index();
    }

    // Phải đối chiếu lại snapshot mới nhất trong DB trước khi dựng lại, tránh sự kiện cũ hoặc chưa lưu DB ghi đè nhầm chỉ mục mới hơn.
    @EventListener
    synchronized void rebuild(CatalogImportedEvent event) {
        Snapshot previous = current.get();
        if (previous != null && previous.generation() == event.snapshotVersion()
                && event.snapshotId().equals(previous.id())) {
            return;
        }
        try {
            snapshots.findFirstByOrderBySnapshotVersionDesc()
                    .filter(snapshot -> snapshot.getId().equals(event.snapshotId())
                            && snapshot.getSnapshotVersion() == event.snapshotVersion())
                    .ifPresent(snapshot -> accept(event.snapshotVersion(), event.snapshotId(), export.exportForIndex(), false));
        } catch (RuntimeException exception) {
            log.warn("Xây dựng lại chỉ mục tìm kiếm cục bộ thất bại cho phiên bản {}", event.snapshotVersion(), exception);
        }
    }

    // Nhận bản xuất danh mục mới, chặn thế hệ cũ hơn hoặc lệch snapshotId rồi hoán đổi nguyên tử toàn bộ chỉ mục.
    synchronized IndexSyncOutcome accept(long generation, String snapshotId, JsonNode catalog, boolean forceRebuild) {
        Snapshot previous = current.get();
        if (previous != null && previous.generation() >= generation && previous.id() != null) {
            if (previous.generation() > generation || !previous.id().equals(snapshotId)) {
                return IndexSyncOutcome.SYNC_FAILED;
            }
            if (!forceRebuild) {
                return IndexSyncOutcome.READY;
            }
        }
        current.set(new Snapshot(generation, snapshotId, build(catalog)));
        return IndexSyncOutcome.READY;
    }

    private static LexicalIndex build(JsonNode catalog) {
        return new LexicalIndex(SearchCatalogFlattener.flatten(catalog));
    }

    // Cặp thông tin thế hệ và bản chụp gắn với một LexicalIndex đã dựng xong.
    private record Snapshot(long generation, String id, LexicalIndex index) {
    }
}
