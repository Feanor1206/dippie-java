package com.dippie.service.catalog;

import com.dippie.common.json.CatalogJson;
import com.dippie.common.json.JsonText;
import com.dippie.exception.CatalogInputException;
import com.dippie.model.catalog.CatalogCollection;
import com.dippie.model.catalog.CatalogPackage;
import com.dippie.model.catalog.CatalogRecord;
import com.dippie.model.catalog.CatalogSeason;
import com.dippie.model.catalog.ContentHash;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.math.BigInteger;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

import static com.dippie.model.catalog.CatalogCollection.CHARACTERS;
import static com.dippie.model.catalog.CatalogCollection.EPISODES;
import static com.dippie.model.catalog.CatalogCollection.GENRES;
import static com.dippie.model.catalog.CatalogCollection.MEDIA_ASSETS;
import static com.dippie.model.catalog.CatalogCollection.SCENES;
import static com.dippie.model.catalog.CatalogCollection.SEASONS;
import static com.dippie.model.catalog.CatalogCollection.SEGMENTS;
import static com.dippie.model.catalog.CatalogCollection.SERIES;

// so gói danh mục sắp nhập với dữ liệu đang có trong DB để đếm thay đổi và kiểm tra tham chiếu
@Repository
public class CatalogDiff {

    private static final List<Reference> REFERENCES = List.of(
            new Reference(SERIES, "genre_ids", GENRES, true),
            new Reference(SERIES, "character_ids", CHARACTERS, true),
            new Reference(CHARACTERS, "series_ids", SERIES, true),
            new Reference(MEDIA_ASSETS, "related_asset_id", MEDIA_ASSETS, false),
            new Reference(SCENES, "media_asset_id", MEDIA_ASSETS, false),
            new Reference(SCENES, "segment_id", SEGMENTS, false));

    private final EntityManager entities;

    public CatalogDiff(EntityManager entities) {
        this.entities = entities;
    }

    // tính sai khác cả gói, chạy /validate thì truyền lock = false đọc thường, còn lúc import thật thì khóa hàng lại cho chắc
    public Analysis calculate(JsonNode document, CatalogPackageValidator.PackageIdentity identity, ContentHash hash,
            boolean lock) {
        // so phiên bản với mã băm của bản ghi gói trước
        CatalogPackage existing = lock ? entities.find(CatalogPackage.class, identity.id(), LockModeType.PESSIMISTIC_WRITE)
                : entities.find(CatalogPackage.class, identity.id());
        Tally tally = new Tally();
        tally.count(existing == null ? null : BigInteger.valueOf(existing.getRevision()), identity.revision(),
                () -> hash.value().equals(existing.getContentHash()));

        // kéo mấy hàng đang có của từng bảng lên rồi chia loại tạo mới, cập nhật, giữ nguyên hay xung đột
        Map<CatalogCollection, Map<String, CatalogRecord>> stored = new EnumMap<>(CatalogCollection.class);
        Map<CatalogCollection, Set<String>> incomingIds = new EnumMap<>(CatalogCollection.class);
        for (CatalogCollection collection : CatalogCollection.values()) {
            JsonNode items = collection.items(document);
            Set<String> itemIds = ids(items);
            incomingIds.put(collection, itemIds);
            Map<String, CatalogRecord> rows = findRows(collection, itemIds, lock);
            stored.put(collection, rows);
            for (JsonNode item : items) {
                CatalogRecord row = rows.get(item.path("id").textValue());
                tally.count(row == null ? null : storedRevision(row), CatalogJson.revision(item, identity.revision()),
                        () -> ContentHash.of(item).equals(storedHash(row.getRawJson())));
            }
        }

        // kiểm tra quan hệ mùa với bộ phim, coverage của file media với mấy khóa tham chiếu chéo
        validateSeasonParents(document, stored.get(SEASONS), lock);
        validateCoverage(document, incomingIds, lock);
        validateReferences(document, incomingIds, lock);
        return new Analysis(existing, tally.result(), stored);
    }

    private Map<String, CatalogRecord> findRows(CatalogCollection collection, Set<String> ids, boolean lock) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Class<? extends CatalogRecord> type = collection.entityType();
        var query = entities.createQuery("select row from " + type.getSimpleName() + " row where row.id in :ids", type)
                .setParameter("ids", ids);
        Map<String, CatalogRecord> rows = new HashMap<>();
        results(query, lock).forEach(row -> rows.put(row.getId(), row));
        return rows;
    }

    private void validateSeasonParents(JsonNode document, Map<String, CatalogRecord> stored, boolean lock) {
        Map<String, String> parents = new HashMap<>();
        Map<String, Integer> moved = new HashMap<>();
        JsonNode seasons = SEASONS.items(document);
        for (int index = 0; index < seasons.size(); index++) {
            JsonNode season = seasons.get(index);
            String id = season.path("id").textValue();
            String parent = season.path("series_id").textValue();
            parents.put(id, parent);
            if (stored.get(id) instanceof CatalogSeason row && !parent.equals(row.getSeriesId())) {
                moved.put(id, index);
            }
        }
        // mùa nào đang có tập rồi thì chặn đổi bộ cha kẻo lệch series_id giữa mùa với tập
        if (!moved.isEmpty()) {
            List<CatalogSeason> used = results(entities.createQuery("""
                    select season from CatalogSeason season where season.id in :ids
                    and exists (select 1 from CatalogEpisode episode where episode.seasonId = season.id)
                    """, CatalogSeason.class).setParameter("ids", moved.keySet()).setMaxResults(1), lock);
            if (!used.isEmpty()) {
                int index = moved.get(used.getFirst().getId());
                throw new CatalogInputException("seasons[" + index + "].series_id",
                        "Không thể chuyển mùa sang bộ phim khác khi mùa vẫn còn tập.");
            }
        }

        // nạp thêm mấy mùa có sẵn trong DB rồi check xem mùa mà tập trỏ tới có cùng bộ phim không
        JsonNode episodes = EPISODES.items(document);
        Set<String> external = new HashSet<>();
        for (JsonNode episode : episodes) {
            String seasonId = episode.path("season_id").textValue();
            if (seasonId != null && !parents.containsKey(seasonId)) {
                external.add(seasonId);
            }
        }
        findRows(SEASONS, external, lock).forEach((id, row) -> parents.put(id, ((CatalogSeason) row).getSeriesId()));
        for (int index = 0; index < episodes.size(); index++) {
            JsonNode episode = episodes.get(index);
            String seasonId = episode.path("season_id").textValue();
            if (seasonId != null && !episode.path("series_id").textValue().equals(parents.get(seasonId))) {
                throw new CatalogInputException("episodes[" + index + "].season_id",
                        "Mùa phim phải tồn tại và thuộc đúng bộ phim của tập này.");
            }
        }
    }

    private void validateCoverage(JsonNode document, Map<CatalogCollection, Set<String>> incomingIds, boolean lock) {
        Map<String, String> paths = new HashMap<>();
        JsonNode assets = MEDIA_ASSETS.items(document);
        for (int assetIndex = 0; assetIndex < assets.size(); assetIndex++) {
            JsonNode coverage = assets.get(assetIndex).path("coverage");
            for (int partIndex = 0; partIndex < coverage.size(); partIndex++) {
                paths.putIfAbsent(coverage.get(partIndex).path("episode_id").textValue(),
                        "media_assets[" + assetIndex + "].coverage[" + partIndex + "].episode_id");
            }
        }
        checkExternalPaths(paths, EPISODES, incomingIds, lock, "Tập phim được gắn với tệp này chưa tồn tại.");
    }

    private void validateReferences(JsonNode document, Map<CatalogCollection, Set<String>> incomingIds, boolean lock) {
        for (Reference rule : REFERENCES) {
            Map<String, String> paths = new HashMap<>();
            JsonNode items = rule.collection().items(document);
            for (int index = 0; index < items.size(); index++) {
                JsonNode fieldNode = items.get(index).path(rule.field());
                if (CatalogJson.absent(fieldNode)) {
                    continue;
                }
                String path = rule.collection().field() + "[" + index + "]." + rule.field();
                JsonNode references = rule.multiple() ? fieldNode : JsonNodeFactory.instance.arrayNode().add(fieldNode);
                if (!references.isArray()) {
                    throw new CatalogInputException(path, "Mã tham chiếu không hợp lệ.");
                }
                for (JsonNode reference : references) {
                    String id = reference.textValue();
                    if (id == null || id.isEmpty()) {
                        throw new CatalogInputException(path, "Mã tham chiếu không hợp lệ.");
                    }
                    paths.putIfAbsent(id, path);
                }
            }
            checkExternalPaths(paths, rule.target(), incomingIds, lock, "Mục được tham chiếu chưa tồn tại.");
        }
    }

    private void checkExternalPaths(Map<String, String> paths, CatalogCollection target,
            Map<CatalogCollection, Set<String>> incomingIds, boolean lock, String message) {
        if (paths.isEmpty()) {
            return;
        }
        Set<String> missing = new HashSet<>(paths.keySet());
        missing.removeAll(incomingIds.getOrDefault(target, Set.of()));
        missing.removeAll(findRows(target, missing, lock).keySet());
        missing.stream().min(CatalogJson.CODE_POINT_ORDER).ifPresent(id -> {
            throw new CatalogInputException(paths.get(id), message);
        });
    }

    private static Set<String> ids(JsonNode items) {
        Set<String> ids = new HashSet<>();
        items.forEach(item -> ids.add(item.path("id").textValue()));
        return ids;
    }

    private static BigInteger storedRevision(CatalogRecord row) {
        return JsonText.parse(row.getRecordJson()).map(record -> record.path("revision"))
                .filter(JsonNode::isIntegralNumber).map(JsonNode::bigIntegerValue)
                .orElseGet(() -> BigInteger.valueOf(row.getRevision()));
    }

    private static ContentHash storedHash(String rawJson) {
        if (rawJson == null || rawJson.isEmpty()) {
            return null;
        }
        return JsonText.parse(rawJson).map(ContentHash::of).orElseGet(() -> ContentHash.ofText(rawJson));
    }

    private static <T> List<T> results(TypedQuery<T> query, boolean lock) {
        if (lock) {
            query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        }
        return query.getResultList();
    }

    // kết quả so sánh gồm gói cũ, bộ đếm thay đổi với mấy hàng đang có trong DB theo từng bảng
    public record Analysis(CatalogPackage existingPackage, Counts counts,
            Map<CatalogCollection, Map<String, CatalogRecord>> storedRows) {
    }

    // số mục tạo mới, cập nhật, giữ nguyên với xung đột phiên bản
    public record Counts(int toCreate, int toUpdate, int unchanged, int conflicts) {
        public boolean hasChanges() {
            return toCreate > 0 || toUpdate > 0;
        }
    }

    // khai báo một trường tham chiếu trong JSON kèm bảng đích cần check tồn tại
    private record Reference(CatalogCollection collection, String field, CatalogCollection target, boolean multiple) {
    }

    // bộ đếm nội bộ chia loại từng mục theo số phiên bản với mã băm nội dung
    private static final class Tally {

        private int created;
        private int updated;
        private int unchanged;
        private int conflicts;

        // phiên bản cao hơn thì cập nhật, bằng nhau mà nội dung y hệt thì giữ nguyên, còn lại tính xung đột hết
        void count(BigInteger storedRevision, int revision, BooleanSupplier sameContent) {
            if (storedRevision == null) {
                created++;
                return;
            }
            int order = BigInteger.valueOf(revision).compareTo(storedRevision);
            if (order > 0) {
                updated++;
            } else if (order == 0 && sameContent.getAsBoolean()) {
                unchanged++;
            } else {
                conflicts++;
            }
        }

        Counts result() {
            return new Counts(created, updated, unchanged, conflicts);
        }
    }
}
