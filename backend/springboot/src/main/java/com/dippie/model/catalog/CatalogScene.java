package com.dippie.model.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.Set;

// Phân cảnh cắt theo mốc startMs và endMs trong một tập phim (kế thừa CatalogRecord); DB có check start_ms >= 0 và end_ms > start_ms.
@Entity
@Table(name = "catalog_scenes",
        indexes = {
                @Index(name = "idx_cat_scene_ep", columnList = "episode_id"),
                @Index(name = "idx_cat_scene_ep_start", columnList = "episode_id, start_ms"),
                @Index(name = "idx_cat_scene_asset", columnList = "media_asset_id")
        })
@Check(name = "ck_catalog_scene_time_range", constraints = "start_ms >= 0 AND end_ms > start_ms")
public class CatalogScene extends CatalogRecord {

    @Column(name = "media_asset_id", length = 100)
    private String mediaAssetId;

    @Column(name = "episode_id", length = 100, nullable = false)
    private String episodeId;

    @Column(name = "segment_id", length = 100)
    private String segmentId;

    @Column(name = "start_ms", nullable = false)
    private long startMs;

    @Column(name = "end_ms", nullable = false)
    private long endMs;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "summary", columnDefinition = "LONGTEXT")
    private String summary;

    @Column(name = "visual_description", columnDefinition = "LONGTEXT")
    private String visualDescription;

    @Column(name = "visual_tags_json", columnDefinition = "LONGTEXT")
    private String visualTagsJson;

    @Column(name = "sound_events_json", columnDefinition = "LONGTEXT")
    private String soundEventsJson;

    @Column(name = "transcript_cue_ids_json", columnDefinition = "LONGTEXT")
    private String transcriptCueIdsJson;

    @Column(name = "character_ids_json", columnDefinition = "LONGTEXT")
    private String characterIdsJson;

    @Column(name = "keywords_json", columnDefinition = "LONGTEXT")
    private String keywordsJson;

    @Column(name = "themes_json", columnDefinition = "LONGTEXT")
    private String themesJson;

    @Column(name = "attributes_json", columnDefinition = "LONGTEXT")
    private String attributesJson;

    @Column(name = "provenance_json", columnDefinition = "LONGTEXT")
    private String provenanceJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_asset_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_scene_asset"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CatalogMediaAsset mediaAsset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_scene_episode"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CatalogEpisode episode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "segment_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_scene_segment"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private CatalogSegment segment;

    protected CatalogScene() {
    }

    // Nạp đè nội dung phân cảnh, mốc thời gian và liên kết từ gói mới (keywords, themes, attributes, provenance giữ nguyên).
    public void applyImport(CatalogScene imported) {
        replaceRecordFrom(imported);
        mediaAssetId = imported.mediaAssetId;
        episodeId = imported.episodeId;
        segmentId = imported.segmentId;
        startMs = imported.startMs;
        endMs = imported.endMs;
        title = imported.title;
        summary = imported.summary;
        visualDescription = imported.visualDescription;
        visualTagsJson = imported.visualTagsJson;
        soundEventsJson = imported.soundEventsJson;
        transcriptCueIdsJson = imported.transcriptCueIdsJson;
        characterIdsJson = imported.characterIdsJson;
    }

    public CatalogScene(String id, String mediaAssetId, String episodeId, String segmentId, long startMs,
            long endMs, String title, String summary, String visualDescription, String visualTagsJson,
            String soundEventsJson, String transcriptCueIdsJson, String characterIdsJson, String keywordsJson,
            String themesJson, String attributesJson, String provenanceJson, String recordJson, String rawJson,
            int revision) {
        super(id, recordJson, rawJson, revision);
        this.mediaAssetId = mediaAssetId;
        this.episodeId = episodeId;
        this.segmentId = segmentId;
        this.startMs = startMs;
        this.endMs = endMs;
        this.title = title;
        this.summary = summary;
        this.visualDescription = visualDescription;
        this.visualTagsJson = visualTagsJson;
        this.soundEventsJson = soundEventsJson;
        this.transcriptCueIdsJson = transcriptCueIdsJson;
        this.characterIdsJson = characterIdsJson;
        this.keywordsJson = keywordsJson;
        this.themesJson = themesJson;
        this.attributesJson = attributesJson;
        this.provenanceJson = provenanceJson;
    }

    public String getMediaAssetId() {
        return mediaAssetId;
    }

    public String getEpisodeId() {
        return episodeId;
    }

    public String getSegmentId() {
        return segmentId;
    }

    public long getStartMs() {
        return startMs;
    }

    public long getEndMs() {
        return endMs;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getVisualDescription() {
        return visualDescription;
    }

    public String getVisualTagsJson() {
        return visualTagsJson;
    }

    public String getSoundEventsJson() {
        return soundEventsJson;
    }

    public String getTranscriptCueIdsJson() {
        return transcriptCueIdsJson;
    }

    public String getCharacterIdsJson() {
        return characterIdsJson;
    }

    public String getKeywordsJson() {
        return keywordsJson;
    }

    public String getThemesJson() {
        return themesJson;
    }

    public String getAttributesJson() {
        return attributesJson;
    }

    public String getProvenanceJson() {
        return provenanceJson;
    }

    // Admin thấy hết phân cảnh; người xem thường chỉ thấy phân cảnh đã xuất bản và không gắn vào media asset bị khóa.
    public boolean isVisibleTo(boolean admin, Set<String> accessibleAssets) {
        return admin || (isPublished()
                && (mediaAssetId == null || mediaAssetId.isEmpty() || accessibleAssets.contains(mediaAssetId)));
    }
}
