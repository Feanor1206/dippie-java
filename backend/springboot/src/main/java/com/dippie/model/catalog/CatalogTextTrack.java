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
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

// track chữ (phụ đề, lời thoại hay OCR) thuộc media asset hay tập phim, kế thừa CatalogRecord
// DB ép phải có media_asset_id, còn nếu gắn thẳng vào episode_id thì bắt buộc kèm source_id
@Entity
@Table(name = "catalog_text_tracks",
        indexes = {
                @Index(name = "idx_cat_track_asset", columnList = "media_asset_id"),
                @Index(name = "idx_cat_track_episode", columnList = "episode_id")
        })
@Check(name = "ck_cat_text_track_origin",
        constraints = "media_asset_id IS NOT NULL OR (episode_id IS NOT NULL AND source_id IS NOT NULL)")
@Check(name = "ck_catalog_track_kind", constraints = "kind IN ('subtitle', 'transcript', 'ocr')")
public class CatalogTextTrack extends CatalogRecord {

    @Column(name = "media_asset_id", length = 100)
    private String mediaAssetId;

    @Column(name = "episode_id", length = 100)
    private String episodeId;

    @Column(name = "source_id", length = 100)
    private String sourceId;

    @Column(name = "language", length = 50, nullable = false)
    private String language;

    @Column(name = "kind", length = 50, nullable = false)
    private String kind;

    @Column(name = "label", length = 255)
    private String label;

    @Column(name = "format", length = 50, nullable = false)
    private String format;

    @Column(name = "is_default", nullable = false)
    @ColumnDefault("FALSE")
    private boolean defaultTrack;

    @Column(name = "external_file_json", columnDefinition = "LONGTEXT")
    private String externalFileJson;

    @Column(name = "cues_json", columnDefinition = "LONGTEXT")
    private String cuesJson;

    @Column(name = "provenance_json", columnDefinition = "LONGTEXT")
    private String provenanceJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_asset_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_text_track_asset"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CatalogMediaAsset mediaAsset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_cat_track_episode"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CatalogEpisode episode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_cat_track_source"))
    private CatalogSource source;

    protected CatalogTextTrack() {
    }

    // re-import thì chép lại ngôn ngữ, định dạng với nội dung cues, không đè cờ defaultTrack
    public void applyImport(CatalogTextTrack imported) {
        replaceRecordFrom(imported);
        mediaAssetId = imported.mediaAssetId;
        episodeId = imported.episodeId;
        sourceId = imported.sourceId;
        language = imported.language;
        kind = imported.kind;
        label = imported.label;
        format = imported.format;
        externalFileJson = imported.externalFileJson;
        cuesJson = imported.cuesJson;
        provenanceJson = imported.provenanceJson;
    }

    public CatalogTextTrack(String id, String mediaAssetId, String episodeId, String sourceId, String language,
            String kind, String label, String format, boolean defaultTrack, String externalFileJson, String cuesJson,
            String provenanceJson, String recordJson, String rawJson, int revision) {
        super(id, recordJson, rawJson, revision);
        this.mediaAssetId = mediaAssetId;
        this.episodeId = episodeId;
        this.sourceId = sourceId;
        this.language = language;
        this.kind = kind;
        this.label = label;
        this.format = format;
        this.defaultTrack = defaultTrack;
        this.externalFileJson = externalFileJson;
        this.cuesJson = cuesJson;
        this.provenanceJson = provenanceJson;
    }

    public String getEpisodeId() {
        return episodeId;
    }

    public String getKind() {
        return kind;
    }
}
