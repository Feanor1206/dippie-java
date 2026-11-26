package com.dippie.dto.catalog;

import com.dippie.model.catalog.CatalogScene;

import java.time.LocalDateTime;

// Record bất biến mang chi tiết phân cảnh ra API, khởi tạo từ CatalogScene qua hàm tĩnh from.
public record CatalogSceneView(
        String id, String mediaAssetId, String episodeId, String segmentId, long startMs, long endMs, String title,
        String summary, String visualDescription, String visualTagsJson, String soundEventsJson,
        String transcriptCueIdsJson, String characterIdsJson, String keywordsJson, String themesJson,
        String attributesJson, String provenanceJson, String recordJson, String rawJson, int revision,
        Long optLockVersion, LocalDateTime updatedAt
) {

    public static CatalogSceneView from(CatalogScene entity) {
        return new CatalogSceneView(entity.getId(), entity.getMediaAssetId(), entity.getEpisodeId(),
                entity.getSegmentId(), entity.getStartMs(), entity.getEndMs(), entity.getTitle(), entity.getSummary(),
                entity.getVisualDescription(), entity.getVisualTagsJson(), entity.getSoundEventsJson(),
                entity.getTranscriptCueIdsJson(), entity.getCharacterIdsJson(), entity.getKeywordsJson(),
                entity.getThemesJson(), entity.getAttributesJson(), entity.getProvenanceJson(), entity.getRecordJson(),
                entity.getRawJson(), entity.getRevision(), entity.getOptLockVersion(), entity.getUpdatedAt());
    }
}
