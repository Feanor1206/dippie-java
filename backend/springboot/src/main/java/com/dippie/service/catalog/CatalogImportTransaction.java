package com.dippie.service.catalog;

import com.dippie.common.json.JsonText;
import com.dippie.dto.catalog.CatalogImportResult;
import com.dippie.exception.ConflictException;
import com.dippie.model.catalog.CatalogCollection;
import com.dippie.model.catalog.CatalogPackage;
import com.dippie.model.catalog.CatalogSnapshot;
import com.dippie.model.catalog.ContentHash;
import com.dippie.model.catalog.IndexSyncOutcome;
import com.dippie.repository.catalog.CatalogSnapshotRepository;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

// Đảm nhận riêng phần transaction ghi CSDL khi nhập gói danh mục.
// Mình tách ra khỏi CatalogImportService để Spring proxy commit xong DB trước rồi lớp ngoài mới đồng bộ chỉ mục và bắn CatalogImportedEvent.
@Repository
public class CatalogImportTransaction {

    private static final String STATUS_COMMITTED = "COMMITTED";
    private static final int MAX_SNAPSHOT_PREFIX = 48;

    private final CatalogDiff diff;
    private final CatalogRecordWriter writer;
    private final CatalogSnapshotRepository snapshots;
    private final EntityManager entities;
    private final Clock clock;

    public CatalogImportTransaction(CatalogDiff diff, CatalogRecordWriter writer, CatalogSnapshotRepository snapshots,
            EntityManager entities, Clock clock) {
        this.diff = diff;
        this.writer = writer;
        this.snapshots = snapshots;
        this.entities = entities;
        this.clock = clock;
    }

    // Khóa hàng cần sửa, kiểm tra xung đột phiên bản rồi ghi gói, các bảng con và bản ghi snapshot trong cùng một transaction.
    @Transactional
    public Commit write(JsonNode document, CatalogPackageValidator.PackageIdentity identity, ContentHash hash) {
        // Tính sai khác kèm khóa hàng; hễ có bất kỳ xung đột phiên bản nào thì hủy ngay.
        CatalogDiff.Analysis changes = diff.calculate(document, identity, hash, true);
        if (changes.counts().conflicts() != 0) {
            throw new ConflictException("Gói danh mục xung đột với phiên bản đã lưu.");
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (CatalogCollection collection : CatalogCollection.values()) {
            counts.put(collection.resultKey(), collection.items(document).size());
        }

        // Gói nhập lại y hệt nội dung cũ thì dùng lại snapshot cùng contentHash để giữ idempotent, nhưng vẫn sửa lại metadata bảng movies cũ.
        if (!changes.counts().hasChanges()) {
            CatalogSnapshot previous = snapshots.findFirstByContentHashOrderBySnapshotVersionDesc(hash.value()).orElse(null);
            if (previous != null) {
                writer.refreshLegacyMetadata(document);
                return new Commit(previous, identity, changes.existingPackage().getImportedAt(), counts, true);
            }
        }

        // Lưu gói, ghi toàn bộ các bảng dữ liệu rồi tạo snapshot PENDING để sau khi commit xong mới đẩy sang bộ tìm kiếm.
        LocalDateTime now = CatalogDates.now(clock);
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
                null, description, hash.value(), STATUS_COMMITTED, null);
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
        int prefixLength = Math.min(MAX_SNAPSHOT_PREFIX, packageId.codePointCount(0, packageId.length()));
        String prefix = packageId.substring(0, packageId.offsetByCodePoints(0, prefixLength));
        String id = "snap_" + clock.millis() + "_" + prefix + "_g" + generation;
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        CatalogSnapshot snapshot = new CatalogSnapshot(id, generation, hash.value(), total, IndexSyncOutcome.PENDING.name(),
                0, null, null);
        snapshot.recordCreation(now);
        entities.persist(snapshot);
        return snapshot;
    }

    private static String metadataText(JsonNode node, String fallback) {
        if (!JsonText.isTruthy(node)) {
            return fallback;
        }
        return node.isBoolean() ? "True" : scalarText(node);
    }

    private static String scalarText(JsonNode node) {
        if (node.isContainerNode()) {
            throw new IllegalArgumentException("Expected a scalar package field");
        }
        return node.asText(null);
    }

    // Kết quả sau khi transaction commit để CatalogImportService biết có cần đồng bộ chỉ mục và phát sự kiện hay không.
    public record Commit(CatalogSnapshot snapshot, CatalogPackageValidator.PackageIdentity identity, LocalDateTime importedAt,
            Map<String, Integer> counts, boolean unchanged) {

        public Commit {
            counts = Collections.unmodifiableMap(new LinkedHashMap<>(counts));
        }

        // Đóng gói kết quả giao dịch thành DTO trả về cho endpoint admin.
        public CatalogImportResult response() {
            return new CatalogImportResult(true, snapshot.getId(), identity.id(), identity.revision(),
                    snapshot.getSnapshotVersion(), snapshot.getContentHash(), importedAt, counts, snapshot.getStatus(),
                    unchanged ? "Gói danh mục này đã được nhập trước đó." : "Đã nhập gói danh mục thành công.");
        }
    }
}
