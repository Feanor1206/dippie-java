package com.dippie.dto.catalog;

import com.dippie.common.rating.RatingSummary;
import com.dippie.model.catalog.CatalogEpisode;
import com.dippie.model.catalog.EpisodeRuntime;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.time.LocalDate;
import java.time.LocalDateTime;

// DTO trả dữ liệu tập phim ra API, trải phẳng RatingSummary ở cùng cấp JSON nhờ @JsonUnwrapped.
public record CatalogEpisodeView(
        String id, String seriesId, String seasonId, Integer number, String title, String aliasesJson, String synopsis,
        String translationsJson, String kind, LocalDate releaseDate, Long durationMs, String genreIdsJson,
        String primaryLanguage, String ordersJson, String linksJson, String characterIdsJson, String plotFactsJson,
        String keywordsJson, String themesJson, String attributesJson, String ratingsJson, String artworksJson,
        String creditsJson, String recordJson, String rawJson, int revision, Long optLockVersion,
        LocalDateTime updatedAt, @JsonUnwrapped RatingSummary ratings
) {

    public static CatalogEpisodeView from(CatalogEpisode entity) {
        return from(entity, RatingSummary.EMPTY);
    }

    // Dựng view từ entity và lọc durationMs qua EpisodeRuntime.known để tập ngắn dưới 1 phút thành null thay vì hiện 0 phút.
    public static CatalogEpisodeView from(CatalogEpisode entity, RatingSummary summary) {
        return new CatalogEpisodeView(entity.getId(), entity.getSeriesId(), entity.getSeasonId(), entity.getNumber(),
                entity.getTitle(), entity.getAliasesJson(), entity.getSynopsis(), entity.getTranslationsJson(),
                entity.getKind(), entity.getReleaseDate(), EpisodeRuntime.known(entity.getDurationMs()),
                entity.getGenreIdsJson(), entity.getPrimaryLanguage(), entity.getOrdersJson(), entity.getLinksJson(),
                entity.getCharacterIdsJson(), entity.getPlotFactsJson(), entity.getKeywordsJson(),
                entity.getThemesJson(), entity.getAttributesJson(), entity.getRatingsJson(), entity.getArtworksJson(),
                entity.getCreditsJson(), entity.getRecordJson(), entity.getRawJson(), entity.getRevision(),
                entity.getOptLockVersion(), entity.getUpdatedAt(), summary);
    }
}
