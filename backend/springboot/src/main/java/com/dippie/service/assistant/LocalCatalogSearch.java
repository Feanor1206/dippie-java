package com.dippie.service.assistant;

import com.dippie.model.assistant.SearchConstraints;
import com.dippie.model.assistant.SearchHit;
import com.dippie.model.catalog.CatalogImportedEvent;
import com.dippie.model.catalog.IndexSyncOutcome;
import com.dippie.repository.catalog.CatalogSnapshotRepository;
import com.dippie.service.catalog.CatalogExportService;
import com.dippie.service.catalog.CatalogReadModelCache;
import com.dippie.service.catalog.MediaFileAvailability;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

// bản chạy cục bộ của CatalogSearch, giữ LexicalIndex trong RAM qua AtomicReference
// kiêm luôn Observer nghe CatalogImportedEvent để tự dựng lại chỉ mục mà không làm gián đoạn truy vấn đang chạy
@Service
public class LocalCatalogSearch implements CatalogSearch {

    private static final Logger log = LoggerFactory.getLogger(LocalCatalogSearch.class);

    private final CatalogExportService export;
    private final CatalogSnapshotRepository snapshots;
    private final CatalogReadModelCache catalog;
    private final MediaFileAvailability files;
    private final AtomicReference<Snapshot> current = new AtomicReference<>();

    public LocalCatalogSearch(CatalogExportService export, CatalogSnapshotRepository snapshots, CatalogReadModelCache catalog,
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

    // chạy tìm kiếm trên LexicalIndex kèm bộ lọc xem tập phim có file phát khả dụng không
    public List<LexicalIndex.Match> matches(LexicalIndex index, String query, int limit, SearchConstraints constraints) {
        var filters = constraints == null ? SearchConstraints.NONE : constraints;
        return index.search(query, limit, filters, discoverable());
    }

    private Predicate<String> discoverable() {
        return catalog.current().playableFilter(files);
    }

    // trả chỉ mục đang dùng, chưa có trong RAM thì dựng lười lần đầu luôn
    public LexicalIndex index() {
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

    // nhớ đối chiếu lại snapshot mới nhất trong DB trước khi dựng lại nha, kẻo event cũ hay chưa lưu DB ghi đè mất bản mới hơn
    @EventListener
    public synchronized void rebuild(CatalogImportedEvent event) {
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

    // nhận bản danh mục mới, chặn thế hệ cũ hơn hay lệch snapshotId rồi swap nguyên tử cả chỉ mục
    public synchronized IndexSyncOutcome accept(long generation, String snapshotId, JsonNode catalog, boolean forceRebuild) {
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

    // gói số thế hệ, mã snapshot với LexicalIndex đã dựng xong
    private record Snapshot(long generation, String id, LexicalIndex index) {
    }
}
