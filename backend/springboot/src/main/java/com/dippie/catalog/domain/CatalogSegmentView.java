package com.dippie.catalog.domain;

import java.time.LocalDateTime;

// Bản ghi chỉ đọc chứa thông tin một phân đoạn trong tập phim để serialize ra JSON.
public record CatalogSegmentView(
        String id, String episodeId, int position, String title, String aliasesJson, String synopsis,
        String translationsJson, String primaryLanguage, String characterIdsJson, String plotFactsJson,
        String keywordsJson, String themesJson, String attributesJson, Long episodeStartMs, Long episodeEndMs,
        String recordJson, String rawJson, int revision, Long optLockVersion, LocalDateTime updatedAt
) {

    public static CatalogSegmentView from(CatalogSegment entity) {
        return new CatalogSegmentView(entity.getId(), entity.getEpisodeId(), entity.getPosition(), entity.getTitle(),
                entity.getAliasesJson(), entity.getSynopsis(), entity.getTranslationsJson(), entity.getPrimaryLanguage(),
                entity.getCharacterIdsJson(), entity.getPlotFactsJson(), entity.getKeywordsJson(),
                entity.getThemesJson(), entity.getAttributesJson(), entity.getEpisodeStartMs(),
                entity.getEpisodeEndMs(), entity.getRecordJson(), entity.getRawJson(), entity.getRevision(),
                entity.getOptLockVersion(), entity.getUpdatedAt());
    }
}
