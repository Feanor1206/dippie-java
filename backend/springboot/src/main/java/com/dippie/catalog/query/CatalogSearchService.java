package com.dippie.catalog.query;

import com.dippie.common.text.UnicodeText;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
class CatalogSearchService {

    private final CatalogReadModelCache cache;

    CatalogSearchService(CatalogReadModelCache cache) {
        this.cache = cache;
    }

    List<Hit> search(String query, boolean authenticated, boolean admin) {
        String term = query == null ? "" : lower(UnicodeText.strip(query));
        if (term.isEmpty()) {
            return List.of();
        }
        return cache.current().searchRows().stream()
                .filter(row -> row.matches(term, authenticated, admin))
                .map(SearchRow::hit)
                .toList();
    }

    static String firstText(String text, String fallback) {
        return text == null || text.isEmpty() ? fallback : text;
    }

    private static String lower(String text) {
        return text.toLowerCase(Locale.ROOT);
    }

    record SearchRow(Hit hit, boolean publicVisible, boolean memberVisible, List<String> fields) {

        static SearchRow of(Hit hit, boolean publicVisible, boolean memberVisible, String... fields) {
            return new SearchRow(hit, publicVisible, memberVisible, Arrays.stream(fields).filter(Objects::nonNull)
                    .map(CatalogSearchService::lower).toList());
        }

        boolean matches(String term, boolean authenticated, boolean admin) {
            return (admin || (authenticated ? memberVisible : publicVisible))
                    && fields.stream().anyMatch(field -> field.contains(term));
        }
    }

    public record Hit(String id, String type, String title, String parentTitle, String seriesId, String episodeId,
            String mediaAssetId, Long startMs, Long endMs, String snippet) {
    }
}
