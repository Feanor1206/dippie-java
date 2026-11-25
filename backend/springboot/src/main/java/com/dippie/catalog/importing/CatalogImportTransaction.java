package com.dippie.catalog.importing;

import com.dippie.catalog.domain.CatalogPackage;
import com.dippie.catalog.domain.CatalogSnapshot;
import com.dippie.catalog.domain.CatalogSnapshotRepository;
import com.dippie.common.error.ConflictException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Repository
class CatalogImportTransaction {

    private final CatalogDiff diff;
    private final CatalogRecordWriter writer;
    private final CatalogSnapshotRepository snapshots;
    private final EntityManager entities;
    private final Clock clock;

    CatalogImportTransaction(CatalogDiff diff, CatalogRecordWriter writer, CatalogSnapshotRepository snapshots,
            EntityManager entities, Clock clock) {
        this.diff = diff;
        this.writer = writer;
        this.snapshots = snapshots;
        this.entities = entities;
        this.clock = clock;
    }

    @Transactional
    public Commit write(JsonNode document, CatalogPackageValidator.PackageIdentity identity, ContentHash hash) {
        CatalogDiff.Analysis changes = diff.calculate(document, identity, hash, true);
        if (changes.counts().conflicts() != 0) {
            throw new ConflictException("Gói danh mục xung đột với phiên bản đã lưu.");
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (CatalogCollection collection : CatalogCollection.values()) {
            counts.put(collection.resultKey, collection.items(document).size());
        }
        if (!changes.counts().hasChanges()) {
            CatalogSnapshot previous = snapshots.findFirstByContentHashOrderBySnapshotVersionDesc(hash.value()).orElse(null);
            if (previous != null) {
                return new Commit(previous, identity, changes.existingPackage().getImportedAt(), counts, true);
            }
        }

        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        savePackage(document.path("package"), identity, hash, changes.existingPackage(), now);
        writer.write(document, changes, identity.revision(), now);
        CatalogSnapshot snapshot = saveSnapshot(identity.id(), hash, counts, now);
        return new Commit(snapshot, identity, now, counts, false);
    }

    private void savePackage(JsonNode metadata, CatalogPackageValidator.PackageIdentity identity, ContentHash hash,
            CatalogPackage stored, LocalDateTime now) {
        String description = stored == null ? scalarText(metadata.path("description")) : null;
        CatalogPackage incoming = new CatalogPackage(identity.id(), metadataText(metadata.path("version"), "1.0.0"),
                identity.revision(), metadataText(metadata.path("default_language"), "vi"),
                metadataText(metadata.path("import_mode"), "upsert"),
                metadataText(metadata.path("reference_scope"), "self_contained"),
                null, description, hash.value(), "COMMITTED", null);
        incoming.recordImportTime(now);
        if (stored == null) {
            entities.persist(incoming);
        } else {
            stored.applyImport(incoming);
        }
    }

    private CatalogSnapshot saveSnapshot(String packageId, ContentHash hash, Map<String, Integer> counts, LocalDateTime now) {
        long generation = snapshots.findTopByOrderBySnapshotVersionDesc()
                .map(latest -> latest.getSnapshotVersion() + 1).orElse(CatalogSnapshotService.FIRST_GENERATION);
        int prefixLength = Math.min(48, packageId.codePointCount(0, packageId.length()));
        String prefix = packageId.substring(0, packageId.offsetByCodePoints(0, prefixLength));
        String id = "snap_" + clock.millis() + "_" + prefix + "_g" + generation;
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        CatalogSnapshot snapshot = new CatalogSnapshot(id, generation, hash.value(), total, IndexSyncOutcome.PENDING.name(),
                0, null, null);
        snapshot.recordCreation(now);
        entities.persist(snapshot);
        return snapshot;
    }

    private static String metadataText(JsonNode value, String fallback) {
        if (!CatalogJson.truthy(value)) {
            return fallback;
        }
        return value.isBoolean() ? "True" : scalarText(value);
    }

    private static String scalarText(JsonNode value) {
        if (value.isContainerNode()) {
            throw new IllegalArgumentException("Expected a scalar package field");
        }
        return value.asText(null);
    }

    record Commit(CatalogSnapshot snapshot, CatalogPackageValidator.PackageIdentity identity, LocalDateTime importedAt,
            Map<String, Integer> counts, boolean unchanged) {

        Commit {
            counts = Collections.unmodifiableMap(new LinkedHashMap<>(counts));
        }

        CatalogImportResult response() {
            return new CatalogImportResult(true, snapshot.getId(), identity.id(), identity.revision(),
                    snapshot.getSnapshotVersion(), snapshot.getContentHash(), importedAt, counts, snapshot.getStatus(),
                    unchanged ? "Gói danh mục đã được nhập." : "Đã nhập gói danh mục.");
        }
    }
}
