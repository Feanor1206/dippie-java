package com.dippie.model.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

// Phân đoạn nhỏ nằm trong một tập phim theo số thứ tự position (kế thừa CatalogRecord), có ràng buộc duy nhất (episode_id, position).
@Entity
@Table(name = "catalog_segments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_cat_segment_position", columnNames = {"episode_id", "position"})
        })
@Check(name = "ck_cat_segment_time_range",
        constraints = "(episode_start_ms IS NULL AND episode_end_ms IS NULL) OR (episode_start_ms IS NOT NULL AND "
                + "episode_end_ms IS NOT NULL AND episode_start_ms >= 0 AND episode_end_ms > episode_start_ms)")
public class CatalogSegment extends CatalogRecord {

    @Column(name = "episode_id", length = 100, nullable = false)
    private String episodeId;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "aliases_json", columnDefinition = "LONGTEXT")
    private String aliasesJson;

    @Column(name = "synopsis", columnDefinition = "LONGTEXT")
    private String synopsis;

    @Column(name = "translations_json", columnDefinition = "LONGTEXT")
    private String translationsJson;

    @Column(name = "primary_language", length = 50)
    private String primaryLanguage;

    @Column(name = "character_ids_json", columnDefinition = "LONGTEXT")
    private String characterIdsJson;

    @Column(name = "plot_facts_json", columnDefinition = "LONGTEXT")
    private String plotFactsJson;

    @Column(name = "keywords_json", columnDefinition = "LONGTEXT")
    private String keywordsJson;

    @Column(name = "themes_json", columnDefinition = "LONGTEXT")
    private String themesJson;

    @Column(name = "attributes_json", columnDefinition = "LONGTEXT")
    private String attributesJson;

    @Column(name = "episode_start_ms")
    private Long episodeStartMs;

    @Column(name = "episode_end_ms")
    private Long episodeEndMs;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_segment_episode"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CatalogEpisode episode;

    protected CatalogSegment() {
    }

    // Cập nhật nội dung và khoảng thời gian phân đoạn khi import lại (aliases, translations, primaryLanguage, themes giữ nguyên).
    public void applyImport(CatalogSegment imported) {
        replaceRecordFrom(imported);
        episodeId = imported.episodeId;
        position = imported.position;
        title = imported.title;
        synopsis = imported.synopsis;
        episodeStartMs = imported.episodeStartMs;
        episodeEndMs = imported.episodeEndMs;
        characterIdsJson = imported.characterIdsJson;
        keywordsJson = imported.keywordsJson;
        attributesJson = imported.attributesJson;
        plotFactsJson = imported.plotFactsJson;
    }

    // Việt nhớ khi đổi thứ tự segment lúc import phải đẩy sang position tạm trước rồi mới gán số mới, không là dính trùng khóa uq_cat_segment_position ngay.
    public void moveToTemporaryPosition(int position) {
        this.position = position;
    }

    public CatalogSegment(String id, String episodeId, int position, String title, String aliasesJson,
            String synopsis, String translationsJson, String primaryLanguage, String characterIdsJson,
            String plotFactsJson, String keywordsJson, String themesJson, String attributesJson, Long episodeStartMs,
            Long episodeEndMs, String recordJson, String rawJson, int revision) {
        super(id, recordJson, rawJson, revision);
        this.episodeId = episodeId;
        this.position = position;
        this.title = title;
        this.aliasesJson = aliasesJson;
        this.synopsis = synopsis;
        this.translationsJson = translationsJson;
        this.primaryLanguage = primaryLanguage;
        this.characterIdsJson = characterIdsJson;
        this.plotFactsJson = plotFactsJson;
        this.keywordsJson = keywordsJson;
        this.themesJson = themesJson;
        this.attributesJson = attributesJson;
        this.episodeStartMs = episodeStartMs;
        this.episodeEndMs = episodeEndMs;
    }

    public String getEpisodeId() {
        return episodeId;
    }

    public int getPosition() {
        return position;
    }

    public String getTitle() {
        return title;
    }

    public String getAliasesJson() {
        return aliasesJson;
    }

    public String getSynopsis() {
        return synopsis;
    }

    public String getTranslationsJson() {
        return translationsJson;
    }

    public String getPrimaryLanguage() {
        return primaryLanguage;
    }

    public String getCharacterIdsJson() {
        return characterIdsJson;
    }

    public String getPlotFactsJson() {
        return plotFactsJson;
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

    public Long getEpisodeStartMs() {
        return episodeStartMs;
    }

    public Long getEpisodeEndMs() {
        return episodeEndMs;
    }
}
