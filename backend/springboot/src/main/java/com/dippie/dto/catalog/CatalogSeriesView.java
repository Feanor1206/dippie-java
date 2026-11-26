package com.dippie.dto.catalog;

import com.dippie.common.rating.RatingSummary;
import com.dippie.model.catalog.CatalogSeries;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.time.LocalDateTime;

// view phim bộ trả API, dùng @JsonUnwrapped trải phẳng mấy trường điểm của RatingSummary
public record CatalogSeriesView(
        String id, String title, String originalTitle, String aliasesJson, String synopsis, String translationsJson,
        String genreIdsJson, String keywordsJson, String themesJson, String originCountriesJson,
        String originalLanguagesJson, Integer releaseYearStart, Integer releaseYearEnd, String releaseStatus,
        String characterIdsJson, String artworksJson, String attributesJson, String ratingsJson, String advisoriesJson,
        String creditsJson, String externalIdsJson, String recordJson, String rawJson, int revision,
        Long optLockVersion, LocalDateTime createdAt, LocalDateTime updatedAt, @JsonUnwrapped RatingSummary ratings
) {

    public static CatalogSeriesView from(CatalogSeries entity) {
        return new CatalogSeriesView(entity.getId(), entity.getTitle(), entity.getOriginalTitle(),
                entity.getAliasesJson(), entity.getSynopsis(), entity.getTranslationsJson(), entity.getGenreIdsJson(),
                entity.getKeywordsJson(), entity.getThemesJson(), entity.getOriginCountriesJson(),
                entity.getOriginalLanguagesJson(), entity.getReleaseYearStart(), entity.getReleaseYearEnd(),
                entity.getReleaseStatus(), entity.getCharacterIdsJson(), entity.getArtworksJson(),
                entity.getAttributesJson(), entity.getRatingsJson(), entity.getAdvisoriesJson(),
                entity.getCreditsJson(), entity.getExternalIdsJson(), entity.getRecordJson(), entity.getRawJson(),
                entity.getRevision(), entity.getOptLockVersion(), entity.getCreatedAt(), entity.getUpdatedAt(),
                RatingSummary.EMPTY);
    }

    // nhân bản view này để gắn thêm điểm đánh giá, giữ nguyên metadata phim bộ
    public CatalogSeriesView withRatings(RatingSummary summary) {
        return new CatalogSeriesView(id, title, originalTitle, aliasesJson, synopsis, translationsJson, genreIdsJson,
                keywordsJson, themesJson, originCountriesJson, originalLanguagesJson, releaseYearStart, releaseYearEnd,
                releaseStatus, characterIdsJson, artworksJson, attributesJson, ratingsJson, advisoriesJson, creditsJson,
                externalIdsJson, recordJson, rawJson, revision, optLockVersion, createdAt, updatedAt, summary);
    }
}
