package com.dippie.catalog.domain;

import com.dippie.common.json.JsonText;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDate;

// Mùa phim thuộc một series (kế thừa CatalogRecord); khóa duy nhất (series_id, id) để bảng catalog_episodes nối khóa ngoại kép kiểm tra đúng mùa của đúng bộ.
@Entity
@Table(name = "catalog_seasons",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_cat_season_series_id", columnNames = {"series_id", "id"})
        },
        indexes = {
                @Index(name = "idx_cat_season_series_num", columnList = "series_id, number")
        })
@Check(name = "ck_catalog_season_kind",
        constraints = "kind IS NULL OR kind IN ('regular', 'specials', 'collection', 'unknown')")
public class CatalogSeason extends CatalogRecord {

    @Column(name = "series_id", length = 100, nullable = false)
    private String seriesId;

    @Column(name = "number")
    private Integer number;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "synopsis", columnDefinition = "LONGTEXT")
    private String synopsis;

    @Column(name = "kind", length = 50)
    private String kind;

    @Column(name = "translations_json", columnDefinition = "LONGTEXT")
    private String translationsJson;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "artworks_json", columnDefinition = "LONGTEXT")
    private String artworksJson;

    @Column(name = "attributes_json", columnDefinition = "LONGTEXT")
    private String attributesJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_season_series"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CatalogSeries series;

    protected CatalogSeason() {
    }

    // Chép lại metadata mùa phim khi import đè (translationsJson và attributesJson không đụng tới).
    public void applyImport(CatalogSeason imported) {
        replaceRecordFrom(imported);
        seriesId = imported.seriesId;
        number = imported.number;
        title = imported.title;
        synopsis = imported.synopsis;
        kind = imported.kind;
        releaseDate = imported.releaseDate;
        artworksJson = imported.artworksJson;
    }

    public CatalogSeason(String id, String seriesId, Integer number, String title, String synopsis, String kind,
            String translationsJson, LocalDate releaseDate, String artworksJson, String attributesJson,
            String recordJson, String rawJson, int revision) {
        super(id, recordJson, rawJson, revision);
        this.seriesId = seriesId;
        this.number = number;
        this.title = title;
        this.synopsis = synopsis;
        this.kind = kind;
        this.translationsJson = translationsJson;
        this.releaseDate = releaseDate;
        this.artworksJson = artworksJson;
        this.attributesJson = attributesJson;
    }

    public String getSeriesId() {
        return seriesId;
    }

    public Integer getNumber() {
        return number;
    }

    public String getTitle() {
        return title;
    }

    public String getSynopsis() {
        return synopsis;
    }

    public String getKind() {
        return kind;
    }

    public String getTranslationsJson() {
        return translationsJson;
    }

    public LocalDate getReleaseDate() {
        return releaseDate;
    }

    // Ưu tiên lấy release_year_start khai báo sẵn trong rawJson (nếu > 0), thiếu thì rơi về năm của releaseDate.
    public Integer getReleaseYearStart() {
        int declaredYear = JsonText.parse(getRawJson()).map(node -> node.path("release_year_start").asInt()).orElse(0);
        if (declaredYear > 0) {
            return declaredYear;
        }
        return releaseDate == null ? null : releaseDate.getYear();
    }

    public String getArtworksJson() {
        return artworksJson;
    }

    public String getAttributesJson() {
        return attributesJson;
    }
}
