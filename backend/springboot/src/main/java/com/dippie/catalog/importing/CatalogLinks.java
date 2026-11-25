package com.dippie.catalog.importing;

import com.dippie.catalog.domain.CatalogCharacterSeries;
import com.dippie.catalog.domain.CatalogEpisodeAsset;
import com.dippie.catalog.domain.CatalogEpisodeCharacter;
import com.dippie.catalog.domain.CatalogEpisodeGenre;
import com.dippie.catalog.domain.CatalogSeriesCharacter;
import com.dippie.catalog.domain.CatalogSeriesGenre;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import static com.dippie.catalog.importing.CatalogCollection.CHARACTERS;
import static com.dippie.catalog.importing.CatalogCollection.EPISODES;
import static com.dippie.catalog.importing.CatalogCollection.SERIES;

@Repository
class CatalogLinks {

    private final EntityManager entities;

    CatalogLinks(EntityManager entities) {
        this.entities = entities;
    }

    void replaceTaxonomy(JsonNode document) {
        JsonNode episodes = EPISODES.items(document);
        replace(episodes, CatalogEpisodeCharacter.class, "episodeId", "character_ids", CatalogEpisodeCharacter::new);
        replace(episodes, CatalogEpisodeGenre.class, "episodeId", "genre_ids", CatalogEpisodeGenre::new);
        JsonNode series = SERIES.items(document);
        replace(series, CatalogSeriesGenre.class, "seriesId", "genre_ids", CatalogSeriesGenre::new);
        replace(series, CatalogSeriesCharacter.class, "seriesId", "character_ids", CatalogSeriesCharacter::new);
        replace(CHARACTERS.items(document), CatalogCharacterSeries.class, "characterId", "series_ids", CatalogCharacterSeries::new);
    }

    void syncEpisodeAssets(JsonNode assets) {
        if (assets.isEmpty()) {
            return;
        }
        Map<String, Set<String>> expected = new LinkedHashMap<>();
        for (JsonNode asset : assets) {
            Set<String> episodes = new HashSet<>();
            asset.path("coverage").forEach(part -> episodes.add(part.path("episode_id").textValue()));
            expected.put(asset.path("id").textValue(), episodes);
        }
        Map<String, Set<String>> actual = new HashMap<>();
        entities.createQuery("""
                select link.mediaAssetId, link.episodeId from CatalogEpisodeAsset link where link.mediaAssetId in :ids
                """, Object[].class).setParameter("ids", expected.keySet()).getResultList().forEach(row ->
                actual.computeIfAbsent((String) row[0], id -> new HashSet<>()).add((String) row[1]));
        expected.entrySet().removeIf(entry -> entry.getValue().equals(actual.getOrDefault(entry.getKey(), Set.of())));
        delete(CatalogEpisodeAsset.class, "mediaAssetId", expected.keySet());
        expected.forEach((asset, episodes) -> episodes.stream().sorted(CatalogJson.CODE_POINT_ORDER)
                .forEach(episode -> entities.persist(new CatalogEpisodeAsset(episode, asset))));
    }

    private <T> void replace(JsonNode items, Class<T> type, String owner, String field, BiFunction<String, String, T> link) {
        Set<String> ids = new HashSet<>();
        items.forEach(item -> ids.add(item.path("id").textValue()));
        delete(type, owner, ids);
        for (JsonNode item : items) {
            for (String reference : referenceIds(item.path(field))) {
                entities.persist(link.apply(item.path("id").textValue(), reference));
            }
        }
    }

    private static Set<String> referenceIds(JsonNode nodes) {
        Set<String> ids = new LinkedHashSet<>();
        if (!CatalogJson.truthy(nodes)) {
            return ids;
        }
        if (nodes.isTextual()) {
            nodes.textValue().codePoints().mapToObj(Character::toString).forEach(ids::add);
        } else if (nodes.isObject()) {
            nodes.fieldNames().forEachRemaining(ids::add);
        } else if (nodes.isArray()) {
            for (JsonNode node : nodes) {
                if (node.isContainerNode()) {
                    throw new IllegalArgumentException("Invalid catalog reference");
                }
                ids.add(node.asText(null));
            }
        } else {
            throw new IllegalArgumentException("Invalid catalog references");
        }
        return ids;
    }

    private void delete(Class<?> type, String owner, Set<String> ids) {
        if (!ids.isEmpty()) {
            entities.createQuery("delete from " + type.getSimpleName() + " link where link." + owner + " in :ids")
                    .setParameter("ids", ids).executeUpdate();
        }
    }
}
