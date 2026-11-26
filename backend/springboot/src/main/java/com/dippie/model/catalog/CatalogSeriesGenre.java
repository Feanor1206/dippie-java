package com.dippie.model.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.io.Serializable;

// bảng nối nhiều-nhiều thể loại của phim bộ, dùng khóa ghép @IdClass
@Entity
@Table(name = "catalog_series_genres",
        indexes = {
                @Index(name = "idx_cat_series_genre", columnList = "genre_id")
        })
@IdClass(CatalogSeriesGenre.Key.class)
public class CatalogSeriesGenre {

    @Id
    @Column(name = "series_id", length = 100, nullable = false)
    private String seriesId;

    @Id
    @Column(name = "genre_id", length = 100, nullable = false)
    private String genreId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_catalog_series_genres_series_id"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CatalogSeries series;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "genre_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_catalog_series_genres_genre_id"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CatalogGenre genre;

    protected CatalogSeriesGenre() {
    }

    public CatalogSeriesGenre(String seriesId, String genreId) {
        this.seriesId = seriesId;
        this.genreId = genreId;
    }

    public String getSeriesId() {
        return seriesId;
    }

    // khóa ghép (seriesId, genreId) cho @IdClass
    public record Key(String seriesId, String genreId) implements Serializable {
    }
}
