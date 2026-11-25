package com.dippie.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDate;
import java.util.Locale;

@Entity
@Table(name = "catalog_episodes",
        indexes = {
                @Index(name = "idx_cat_ep_series_season", columnList = "series_id, season_id"),
                @Index(name = "idx_cat_ep_season", columnList = "season_id"),
                @Index(name = "idx_cat_ep_publication", columnList = "publication_status"),
                @Index(name = "idx_cat_ep_status_series_duration", columnList = "publication_status, series_id, duration_ms")
        })
@Check(name = "ck_catalog_episode_publication",
        constraints = "publication_status IN ('draft', 'ready', 'archived')")
@Check(name = "ck_catalog_episode_kind",
        constraints = "kind IN ('regular', 'special', 'ova', 'ona', 'recap', 'short', 'pilot', 'movie', 'unknown')")
@Check(name = "ck_catalog_episode_duration", constraints = "duration_ms > 0")
public class CatalogEpisode extends CatalogRecord {

    @Column(name = "series_id", length = 100, nullable = false)
    private String seriesId;

    @Column(name = "season_id", length = 100)
    private String seasonId;

    @Column(name = "number")
    private Integer number;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "aliases_json", columnDefinition = "LONGTEXT")
    private String aliasesJson;

    @Column(name = "synopsis", columnDefinition = "LONGTEXT")
    private String synopsis;

    @Column(name = "translations_json", columnDefinition = "LONGTEXT")
    private String translationsJson;

    @Column(name = "kind", length = 50, nullable = false)
    private String kind;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "genre_ids_json", columnDefinition = "LONGTEXT")
    private String genreIdsJson;

    @Column(name = "primary_language", length = 50)
    private String primaryLanguage;

    @Column(name = "orders_json", columnDefinition = "LONGTEXT")
    private String ordersJson;

    @Column(name = "links_json", columnDefinition = "LONGTEXT")
    private String linksJson;

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

    @Column(name = "ratings_json", columnDefinition = "LONGTEXT")
    private String ratingsJson;

    @Column(name = "artworks_json", columnDefinition = "LONGTEXT")
    private String artworksJson;

    @Column(name = "credits_json", columnDefinition = "LONGTEXT")
    private String creditsJson;

    @Column(name = "publication_status", length = 16, nullable = false)
    @ColumnDefault("'draft'")
    private String publicationStatus = "draft";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_episode_series"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CatalogSeries series;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns(value = {
            @JoinColumn(name = "series_id", referencedColumnName = "series_id", insertable = false, updatable = false),
            @JoinColumn(name = "season_id", referencedColumnName = "id", insertable = false, updatable = false)
    }, foreignKey = @ForeignKey(name = "fk_episode_season_same_series"))
    private CatalogSeason season;

    protected CatalogEpisode() {
    }

    public void applyImport(CatalogEpisode imported) {
        replaceRecordFrom(imported);
        seriesId = imported.seriesId;
        seasonId = imported.seasonId;
        number = imported.number;
        title = imported.title;
        aliasesJson = imported.aliasesJson;
        synopsis = imported.synopsis;
        translationsJson = imported.translationsJson;
        kind = imported.kind;
        releaseDate = imported.releaseDate;
        durationMs = imported.durationMs;
        genreIdsJson = imported.genreIdsJson;
        primaryLanguage = imported.primaryLanguage;
        characterIdsJson = imported.characterIdsJson;
        plotFactsJson = imported.plotFactsJson;
        keywordsJson = imported.keywordsJson;
        themesJson = imported.themesJson;
        attributesJson = imported.attributesJson;
        artworksJson = imported.artworksJson;
        synchronizePublicationStatus();
    }

    public CatalogEpisode(String id, String seriesId, String seasonId, Integer number, String title,
            String aliasesJson, String synopsis, String translationsJson, String kind, LocalDate releaseDate,
            Long durationMs, String genreIdsJson, String primaryLanguage, String ordersJson, String linksJson,
            String characterIdsJson, String plotFactsJson, String keywordsJson, String themesJson,
            String attributesJson, String ratingsJson, String artworksJson, String creditsJson, String recordJson,
            String rawJson, int revision) {
        super(id, recordJson, rawJson, revision);
        this.seriesId = seriesId;
        this.seasonId = seasonId;
        this.number = number;
        this.title = title;
        this.aliasesJson = aliasesJson;
        this.synopsis = synopsis;
        this.translationsJson = translationsJson;
        this.kind = kind;
        this.releaseDate = releaseDate;
        this.durationMs = durationMs;
        this.genreIdsJson = genreIdsJson;
        this.primaryLanguage = primaryLanguage;
        this.ordersJson = ordersJson;
        this.linksJson = linksJson;
        this.characterIdsJson = characterIdsJson;
        this.plotFactsJson = plotFactsJson;
        this.keywordsJson = keywordsJson;
        this.themesJson = themesJson;
        this.attributesJson = attributesJson;
        this.ratingsJson = ratingsJson;
        this.artworksJson = artworksJson;
        this.creditsJson = creditsJson;
        synchronizePublicationStatus();
    }

    public String getSeriesId() {
        return seriesId;
    }

    public String getSeasonId() {
        return seasonId;
    }

    public Integer getNumber() {
        return number;
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

    public String getKind() {
        return kind;
    }

    public LocalDate getReleaseDate() {
        return releaseDate;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public String getGenreIdsJson() {
        return genreIdsJson;
    }

    public String getPrimaryLanguage() {
        return primaryLanguage;
    }

    public String getOrdersJson() {
        return ordersJson;
    }

    public String getLinksJson() {
        return linksJson;
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

    public String getRatingsJson() {
        return ratingsJson;
    }

    public String getArtworksJson() {
        return artworksJson;
    }

    public String getCreditsJson() {
        return creditsJson;
    }

    public String getPublicationStatus() {
        return publicationStatus;
    }

    public void updateRecordJson(String recordJson) {
        replaceRecordJson(recordJson);
        synchronizePublicationStatus();
    }

    @PrePersist
    @PreUpdate
    private void synchronizePublicationStatus() {
        publicationStatus = PublicationStatus.of(getRecordJson()).name().toLowerCase(Locale.ROOT);
    }
}
