package com.dippie.service.catalog;

import com.dippie.common.json.CatalogJson;
import com.dippie.common.json.JsonText;
import com.dippie.model.catalog.CatalogCollection;
import com.dippie.model.catalog.CatalogPackage;
import com.dippie.model.catalog.PublicationStatus;
import com.dippie.repository.catalog.CatalogPackageRepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

import static com.dippie.model.catalog.CatalogCollection.EPISODES;
import static com.dippie.model.catalog.CatalogCollection.MEDIA_ASSETS;
import static com.dippie.model.catalog.CatalogCollection.SOURCES;
import static com.dippie.model.catalog.CatalogCollection.TEXT_TRACKS;

// xuất danh mục từ DB ra JSON chuẩn, có cờ lọc bản nháp với file riêng tư
@Service
@Transactional(readOnly = true)
public class CatalogExportService {

    private static final String PUBLIC_ACCESS_POLICY = "public";

    private final CatalogPackageRepository packages;
    private final EntityManager entities;
    private final Clock clock;

    public CatalogExportService(CatalogPackageRepository packages, EntityManager entities, Clock clock) {
        this.packages = packages;
        this.entities = entities;
        this.clock = clock;
    }

    // dùng cho API admin, thích giữ hay bỏ bản nháp với file riêng tư đều được
    public JsonNode exportCatalog(boolean includeDraft, boolean includePrivate) {
        return exportCatalog(includeDraft, includePrivate, CatalogDates.now(clock));
    }

    // chỉ xuất phần công khai đã xuất bản cho bộ tìm kiếm, ghim created_at về epoch lúc DB trống để JSON ra ổn định
    public JsonNode exportForIndex() {
        return exportCatalog(false, false, LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC));
    }

    private JsonNode exportCatalog(boolean includeDraft, boolean includePrivate, LocalDateTime defaultCreated) {
        CatalogPackage latest = packages.findFirstByOrderByImportedAtDesc().orElse(null);
        ObjectNode catalog = JsonNodeFactory.instance.objectNode().put("schema_version", "dippie.catalog.v1");
        catalog.set("package", metadata(latest, defaultCreated));
        for (CatalogCollection collection : CatalogCollection.values()) {
            catalog.set(collection.field(), exportRows(collection, includeDraft, includePrivate));
        }
        // bỏ bản nháp thì nhớ lọc luôn phụ đề trỏ sang nguồn nháp nha, kẻo JSON xuất ra dính source_id ma
        if (!includeDraft) {
            filterTrackSources(catalog);
        }
        return catalog;
    }

    private ObjectNode metadata(CatalogPackage latest, LocalDateTime defaultCreated) {
        LocalDateTime created = latest == null ? null : latest.getCreatedAt();
        if (created == null) {
            created = defaultCreated;
        }
        String description = latest == null ? null : latest.getDescription();
        return JsonNodeFactory.instance.objectNode()
                .put("id", latest == null ? "dippie-exported-catalog" : latest.getId())
                .put("version", latest == null ? "1.0.0" : latest.getVersion())
                .put("revision", latest == null ? 1 : latest.getRevision())
                .put("description", description == null || description.isEmpty() ? "Canonical catalog export" : description)
                .put("created_at", created.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z")
                .put("default_language", latest == null ? "vi" : latest.getDefaultLanguage())
                .put("import_mode", latest == null ? "upsert" : latest.getImportMode())
                .put("reference_scope", latest == null ? "self_contained" : latest.getReferenceScope());
    }

    private ArrayNode exportRows(CatalogCollection collection, boolean includeDraft, boolean includePrivate) {
        // dựng JPQL lọc theo trạng thái xuất bản, quyền public với quan hệ tập/file của phụ đề
        boolean publishedEpisode = collection == EPISODES && !includeDraft;
        boolean checkRecord = collection != EPISODES && !includeDraft;
        String projection = checkRecord ? "row.rawJson, row.recordJson" : "row.rawJson";
        StringBuilder query = new StringBuilder("select ").append(projection).append(" from ")
                .append(collection.entityType().getSimpleName()).append(" row where row.rawJson is not null");
        if (publishedEpisode) {
            query.append(" and row.publicationStatus = '").append(PublicationStatus.READY_VALUE).append("'");
        }
        if (checkRecord) {
            query.append(" and lower(row.recordJson) like '%").append(PublicationStatus.READY_VALUE).append("%'");
        }
        if (collection == MEDIA_ASSETS && !includePrivate) {
            query.append(" and lower(row.accessPolicy) = '").append(PUBLIC_ACCESS_POLICY).append("'");
        }
        if (collection == TEXT_TRACKS) {
            if (!includeDraft) {
                query.append(" and (row.episodeId is null or row.episodeId in"
                        + " (select episode.id from CatalogEpisode episode where episode.publicationStatus = '"
                        + PublicationStatus.READY_VALUE + "'))");
            }
            if (!includePrivate) {
                query.append(" and (row.mediaAssetId is null or row.mediaAssetId in"
                        + " (select asset.id from CatalogMediaAsset asset where asset.accessPolicy = '"
                        + PUBLIC_ACCESS_POLICY + "'))");
            }
        }
        // lọc sơ bằng LIKE ở SQL xong vẫn phải parse recordJson kiểm tra lại cho chắc trạng thái xuất bản
        ArrayNode exported = JsonNodeFactory.instance.arrayNode();
        query.append(" order by row.id");
        for (Object[] row : entities.createQuery(query.toString(), Object[].class).getResultList()) {
            if (checkRecord && !PublicationStatus.of((String) row[1]).isPublished()) {
                continue;
            }
            JsonText.parse((String) row[0]).ifPresent(document -> {
                if (publishedEpisode && document.path("record") instanceof ObjectNode record) {
                    record.put("status", PublicationStatus.READY_VALUE);
                }
                exported.add(document);
            });
        }
        return exported;
    }

    // chỉ giữ mấy phụ đề không gắn nguồn hoặc có source_id nằm trong tập nguồn đã xuất
    private static void filterTrackSources(ObjectNode catalog) {
        Set<JsonNode> sources = new HashSet<>();
        catalog.path(SOURCES.field()).forEach(source -> sources.add(source.path("id")));
        ArrayNode tracks = JsonNodeFactory.instance.arrayNode();
        for (JsonNode track : catalog.path(TEXT_TRACKS.field())) {
            JsonNode source = track.path("source_id");
            if (CatalogJson.absent(source) || sources.contains(source)) {
                tracks.add(track);
            }
        }
        catalog.set(TEXT_TRACKS.field(), tracks);
    }
}
