package com.dippie.dto.catalog;

import com.dippie.model.catalog.CatalogSeason;

import java.time.LocalDate;
import java.time.LocalDateTime;

// Thông tin một mùa phim trả ra JSON cho frontend, chuyển đổi từ CatalogSeason qua from.
public record CatalogSeasonView(
        String id, String seriesId, Integer number, String title, String synopsis, String kind, String translationsJson,
        LocalDate releaseDate, String artworksJson, String attributesJson, String recordJson, String rawJson,
        int revision, Long optLockVersion, LocalDateTime updatedAt
) {

    public static CatalogSeasonView from(CatalogSeason entity) {
        return new CatalogSeasonView(entity.getId(), entity.getSeriesId(), entity.getNumber(), entity.getTitle(),
                entity.getSynopsis(), entity.getKind(), entity.getTranslationsJson(), entity.getReleaseDate(),
                entity.getArtworksJson(), entity.getAttributesJson(), entity.getRecordJson(), entity.getRawJson(),
                entity.getRevision(), entity.getOptLockVersion(), entity.getUpdatedAt());
    }
}
