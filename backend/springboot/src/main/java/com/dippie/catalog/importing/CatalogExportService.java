package com.dippie.catalog.importing;

import com.dippie.catalog.domain.CatalogPackage;
import com.dippie.catalog.domain.CatalogPackageRepository;
import com.dippie.catalog.domain.PublicationStatus;
import com.dippie.common.json.JsonText;
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
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

import static com.dippie.catalog.importing.CatalogCollection.EPISODES;
import static com.dippie.catalog.importing.CatalogCollection.MEDIA_ASSETS;
import static com.dippie.catalog.importing.CatalogCollection.SOURCES;
import static com.dippie.catalog.importing.CatalogCollection.TEXT_TRACKS;

@Service
@Transactional(readOnly = true)
public class CatalogExportService {

    private final CatalogPackageRepository packages;
    private final EntityManager entities;
    private final Clock clock;

    public CatalogExportService(CatalogPackageRepository packages, EntityManager entities, Clock clock) {
        this.packages = packages;
        this.entities = entities;
        this.clock = clock;
    }

    public JsonNode exportEligibleCatalog(boolean includeDraft, boolean includePrivate) {
        return exportEligibleCatalog(includeDraft, includePrivate, LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS));
    }

    public JsonNode exportForIndex() {
        return exportEligibleCatalog(false, false, LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC));
    }

    private JsonNode exportEligibleCatalog(boolean includeDraft, boolean includePrivate, LocalDateTime defaultCreated) {
        CatalogPackage latest = packages.findFirstByOrderByImportedAtDesc().orElse(null);
        ObjectNode catalog = JsonNodeFactory.instance.objectNode().put("schema_version", "dippie.catalog.v1");
        catalog.set("package", metadata(latest, defaultCreated));
        for (CatalogCollection collection : CatalogCollection.values()) {
            catalog.set(collection.field, exportRows(collection, includeDraft, includePrivate));
        }
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
        boolean publishedEpisode = collection == EPISODES && !includeDraft;
        boolean checkRecord = collection != EPISODES && !includeDraft;
        String projection = checkRecord ? "row.rawJson, row.recordJson" : "row.rawJson";
        StringBuilder query = new StringBuilder("select ").append(projection).append(" from ")
                .append(collection.entityType.getSimpleName()).append(" row where row.rawJson is not null");
        if (publishedEpisode) {
            query.append(" and row.publicationStatus = 'ready'");
        }
        if (checkRecord) {
            query.append(" and lower(row.recordJson) like '%ready%'");
        }
        if (collection == MEDIA_ASSETS && !includePrivate) {
            query.append(" and lower(row.accessPolicy) = 'public'");
        }
        if (collection == TEXT_TRACKS) {
            if (!includeDraft) {
                query.append("""
                         and (row.episodeId is null or row.episodeId in
                           (select episode.id from CatalogEpisode episode where episode.publicationStatus = 'ready'))
                        """);
            }
            if (!includePrivate) {
                query.append("""
                         and (row.mediaAssetId is null or row.mediaAssetId in
                           (select asset.id from CatalogMediaAsset asset where asset.accessPolicy = 'public'))
                        """);
            }
        }
        ArrayNode exported = JsonNodeFactory.instance.arrayNode();
        query.append(" order by row.id");
        for (Object[] row : entities.createQuery(query.toString(), Object[].class).getResultList()) {
            if (checkRecord && !PublicationStatus.of((String) row[1]).isPublished()) {
                continue;
            }
            JsonText.parse((String) row[0]).ifPresent(document -> {
                if (publishedEpisode && document.path("record") instanceof ObjectNode record) {
                    record.put("status", "ready");
                }
                exported.add(document);
            });
        }
        return exported;
    }

    private static void filterTrackSources(ObjectNode catalog) {
        Set<JsonNode> sources = new HashSet<>();
        catalog.path(SOURCES.field).forEach(source -> sources.add(source.path("id")));
        ArrayNode tracks = JsonNodeFactory.instance.arrayNode();
        for (JsonNode track : catalog.path(TEXT_TRACKS.field)) {
            JsonNode source = track.path("source_id");
            if (CatalogJson.absent(source) || sources.contains(source)) {
                tracks.add(track);
            }
        }
        catalog.set(TEXT_TRACKS.field, tracks);
    }
}
