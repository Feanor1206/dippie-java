package com.dippie.catalog.domain;

import java.time.LocalDateTime;

public record CatalogSeriesView(
        String id,
        String title,
        String originalTitle,
        String aliasesJson,
        String synopsis,
        String translationsJson,
        String genreIdsJson,
        String keywordsJson,
        String themesJson,
        String originCountriesJson,
        String originalLanguagesJson,
        Integer releaseYearStart,
        Integer releaseYearEnd,
        String releaseStatus,
        String characterIdsJson,
        String artworksJson,
        String attributesJson,
        String ratingsJson,
        String advisoriesJson,
        String creditsJson,
        String externalIdsJson,
        String recordJson,
        String rawJson,
        int revision,
        Long optLockVersion,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static CatalogSeriesView from(CatalogSeries entity) {
        return new CatalogSeriesView(
                entity.getId(),
                entity.getTitle(),
                entity.getOriginalTitle(),
                entity.getAliasesJson(),
                entity.getSynopsis(),
                entity.getTranslationsJson(),
                entity.getGenreIdsJson(),
                entity.getKeywordsJson(),
                entity.getThemesJson(),
                entity.getOriginCountriesJson(),
                entity.getOriginalLanguagesJson(),
                entity.getReleaseYearStart(),
                entity.getReleaseYearEnd(),
                entity.getReleaseStatus(),
                entity.getCharacterIdsJson(),
                entity.getArtworksJson(),
                entity.getAttributesJson(),
                entity.getRatingsJson(),
                entity.getAdvisoriesJson(),
                entity.getCreditsJson(),
                entity.getExternalIdsJson(),
                entity.getRecordJson(),
                entity.getRawJson(),
                entity.getRevision(),
                entity.getOptLockVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
