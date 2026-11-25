package com.dippie.assistant.search;

import com.dippie.catalog.domain.CatalogSnapshotRepository;
import com.dippie.catalog.importing.CatalogExportService;
import com.dippie.catalog.importing.CatalogImportedEvent;
import com.dippie.catalog.importing.IndexSyncOutcome;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
class LocalCatalogSearch implements CatalogSearch {

    private static final Logger LOG = LoggerFactory.getLogger(LocalCatalogSearch.class);

    private final CatalogExportService export;
    private final CatalogSnapshotRepository snapshots;
    private final AtomicReference<Snapshot> current = new AtomicReference<>();

    LocalCatalogSearch(CatalogExportService export, CatalogSnapshotRepository snapshots) {
        this.export = export;
        this.snapshots = snapshots;
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

    static List<LexicalIndex.Match> matches(LexicalIndex index, String query, int limit, SearchConstraints constraints) {
        return index.search(query, limit, constraints == null ? SearchConstraints.NONE : constraints);
    }

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
                LOG.warn("Local catalog index initialization failed", exception);
                return null;
            }
        }
        return snapshot.index();
    }

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
            LOG.warn("Local catalog rebuild failed for generation {}", event.snapshotVersion(), exception);
        }
    }

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

    private record Snapshot(long generation, String id, LexicalIndex index) {
    }
}
