package com.dippie.catalog.domain;

import java.time.LocalDateTime;

public record CatalogSceneView(
        String id,
        String mediaAssetId,
        String episodeId,
        String segmentId,
        long startMs,
        long endMs,
        String title,
        String summary,
        String visualDescription,
        String visualTagsJson,
        String soundEventsJson,
        String transcriptCueIdsJson,
        String characterIdsJson,
        String keywordsJson,
        String themesJson,
        String attributesJson,
        String provenanceJson,
        String recordJson,
        String rawJson,
        int revision,
        Long optLockVersion,
        LocalDateTime updatedAt
) {

    public static CatalogSceneView from(CatalogScene entity) {
        return new CatalogSceneView(
                entity.getId(),
                entity.getMediaAssetId(),
                entity.getEpisodeId(),
                entity.getSegmentId(),
                entity.getStartMs(),
                entity.getEndMs(),
                entity.getTitle(),
                entity.getSummary(),
                entity.getVisualDescription(),
                entity.getVisualTagsJson(),
                entity.getSoundEventsJson(),
                entity.getTranscriptCueIdsJson(),
                entity.getCharacterIdsJson(),
                entity.getKeywordsJson(),
                entity.getThemesJson(),
                entity.getAttributesJson(),
                entity.getProvenanceJson(),
                entity.getRecordJson(),
                entity.getRawJson(),
                entity.getRevision(),
                entity.getOptLockVersion(),
                entity.getUpdatedAt());
    }
}
