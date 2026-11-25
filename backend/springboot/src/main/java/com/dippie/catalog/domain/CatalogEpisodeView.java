package com.dippie.catalog.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CatalogEpisodeView(
        String id,
        String seriesId,
        String seasonId,
        Integer number,
        String title,
        String aliasesJson,
        String synopsis,
        String translationsJson,
        String kind,
        LocalDate releaseDate,
        Long durationMs,
        String genreIdsJson,
        String primaryLanguage,
        String ordersJson,
        String linksJson,
        String characterIdsJson,
        String plotFactsJson,
        String keywordsJson,
        String themesJson,
        String attributesJson,
        String ratingsJson,
        String artworksJson,
        String creditsJson,
        String recordJson,
        String rawJson,
        int revision,
        Long optLockVersion,
        LocalDateTime updatedAt
) {

    public static CatalogEpisodeView from(CatalogEpisode entity) {
        return new CatalogEpisodeView(
                entity.getId(),
                entity.getSeriesId(),
                entity.getSeasonId(),
                entity.getNumber(),
                entity.getTitle(),
                entity.getAliasesJson(),
                entity.getSynopsis(),
                entity.getTranslationsJson(),
                entity.getKind(),
                entity.getReleaseDate(),
                entity.getDurationMs(),
                entity.getGenreIdsJson(),
                entity.getPrimaryLanguage(),
                entity.getOrdersJson(),
                entity.getLinksJson(),
                entity.getCharacterIdsJson(),
                entity.getPlotFactsJson(),
                entity.getKeywordsJson(),
                entity.getThemesJson(),
                entity.getAttributesJson(),
                entity.getRatingsJson(),
                entity.getArtworksJson(),
                entity.getCreditsJson(),
                entity.getRecordJson(),
                entity.getRawJson(),
                entity.getRevision(),
                entity.getOptLockVersion(),
                entity.getUpdatedAt());
    }
}
