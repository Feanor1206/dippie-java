package com.dippie.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

// Phim bộ ở cấp cao nhất trong hệ catalog (kế thừa CatalogRecord), ràng buộc DB ép release_year_start <= release_year_end.
@Entity
@Table(name = "catalog_series",
        indexes = {
                @Index(name = "idx_cat_series_title", columnList = "title")
        })
@Check(name = "ck_catalog_series_years", constraints = "release_year_start <= release_year_end")
public class CatalogSeries extends CatalogRecord {

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "original_title", length = 255)
    private String originalTitle;

    @Column(name = "aliases_json", columnDefinition = "LONGTEXT")
    private String aliasesJson;

    @Column(name = "synopsis", columnDefinition = "LONGTEXT")
    private String synopsis;

    @Column(name = "translations_json", columnDefinition = "LONGTEXT")
    private String translationsJson;

    @Column(name = "genre_ids_json", columnDefinition = "LONGTEXT")
    private String genreIdsJson;

    @Column(name = "keywords_json", columnDefinition = "LONGTEXT")
    private String keywordsJson;

    @Column(name = "themes_json", columnDefinition = "LONGTEXT")
    private String themesJson;

    @Column(name = "origin_countries_json", columnDefinition = "LONGTEXT")
    private String originCountriesJson;

    @Column(name = "original_languages_json", columnDefinition = "LONGTEXT")
    private String originalLanguagesJson;

    @Column(name = "release_year_start")
    private Integer releaseYearStart;

    @Column(name = "release_year_end")
    private Integer releaseYearEnd;

    @Column(name = "release_status", length = 50)
    private String releaseStatus;

    @Column(name = "character_ids_json", columnDefinition = "LONGTEXT")
    private String characterIdsJson;

    @Column(name = "artworks_json", columnDefinition = "LONGTEXT")
    private String artworksJson;

    @Column(name = "attributes_json", columnDefinition = "LONGTEXT")
    private String attributesJson;

    @Column(name = "ratings_json", columnDefinition = "LONGTEXT")
    private String ratingsJson;

    @Column(name = "advisories_json", columnDefinition = "LONGTEXT")
    private String advisoriesJson;

    @Column(name = "credits_json", columnDefinition = "LONGTEXT")
    private String creditsJson;

    @Column(name = "external_ids_json", columnDefinition = "LONGTEXT")
    private String externalIdsJson;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP", updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    protected CatalogSeries() {
    }

    // Cập nhật toàn bộ metadata phim bộ từ lần import mới, riêng creditsJson vẫn giữ lại.
    public void applyImport(CatalogSeries imported) {
        replaceRecordFrom(imported);
        title = imported.title;
        originalTitle = imported.originalTitle;
        aliasesJson = imported.aliasesJson;
        synopsis = imported.synopsis;
        translationsJson = imported.translationsJson;
        genreIdsJson = imported.genreIdsJson;
        keywordsJson = imported.keywordsJson;
        themesJson = imported.themesJson;
        originCountriesJson = imported.originCountriesJson;
        originalLanguagesJson = imported.originalLanguagesJson;
        releaseYearStart = imported.releaseYearStart;
        releaseYearEnd = imported.releaseYearEnd;
        releaseStatus = imported.releaseStatus;
        characterIdsJson = imported.characterIdsJson;
        artworksJson = imported.artworksJson;
        attributesJson = imported.attributesJson;
        ratingsJson = imported.ratingsJson;
        advisoriesJson = imported.advisoriesJson;
        externalIdsJson = imported.externalIdsJson;
    }

    public CatalogSeries(String id, String title, String originalTitle, String aliasesJson, String synopsis,
            String translationsJson, String genreIdsJson, String keywordsJson, String themesJson,
            String originCountriesJson, String originalLanguagesJson, Integer releaseYearStart, Integer releaseYearEnd,
            String releaseStatus, String characterIdsJson, String artworksJson, String attributesJson,
            String ratingsJson, String advisoriesJson, String creditsJson, String externalIdsJson, String recordJson,
            String rawJson, int revision) {
        super(id, recordJson, rawJson, revision);
        this.title = title;
        this.originalTitle = originalTitle;
        this.aliasesJson = aliasesJson;
        this.synopsis = synopsis;
        this.translationsJson = translationsJson;
        this.genreIdsJson = genreIdsJson;
        this.keywordsJson = keywordsJson;
        this.themesJson = themesJson;
        this.originCountriesJson = originCountriesJson;
        this.originalLanguagesJson = originalLanguagesJson;
        this.releaseYearStart = releaseYearStart;
        this.releaseYearEnd = releaseYearEnd;
        this.releaseStatus = releaseStatus;
        this.characterIdsJson = characterIdsJson;
        this.artworksJson = artworksJson;
        this.attributesJson = attributesJson;
        this.ratingsJson = ratingsJson;
        this.advisoriesJson = advisoriesJson;
        this.creditsJson = creditsJson;
        this.externalIdsJson = externalIdsJson;
    }

    public String getTitle() {
        return title;
    }

    public String getOriginalTitle() {
        return originalTitle;
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

    public String getGenreIdsJson() {
        return genreIdsJson;
    }

    public String getKeywordsJson() {
        return keywordsJson;
    }

    public String getThemesJson() {
        return themesJson;
    }

    public String getOriginCountriesJson() {
        return originCountriesJson;
    }

    public String getOriginalLanguagesJson() {
        return originalLanguagesJson;
    }

    public Integer getReleaseYearStart() {
        return releaseYearStart;
    }

    public Integer getReleaseYearEnd() {
        return releaseYearEnd;
    }

    public String getReleaseStatus() {
        return releaseStatus;
    }

    public String getCharacterIdsJson() {
        return characterIdsJson;
    }

    public String getArtworksJson() {
        return artworksJson;
    }

    public String getAttributesJson() {
        return attributesJson;
    }

    public String getRatingsJson() {
        return ratingsJson;
    }

    public String getAdvisoriesJson() {
        return advisoriesJson;
    }

    public String getCreditsJson() {
        return creditsJson;
    }

    public String getExternalIdsJson() {
        return externalIdsJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // Mượn luôn mốc updatedAt vừa được CatalogRecord.updateTimestamp gán ở lớp cha để createdAt khớp y hệt lúc mới tạo.
    @PrePersist
    private void initCreatedAt() {
        createdAt = getUpdatedAt();
    }
}
