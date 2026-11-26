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

// bảng nối nhiều-nhiều phim bộ với nhân vật, xóa bên nào DB cũng tự cascade
@Entity
@Table(name = "catalog_series_characters",
        indexes = {
                @Index(name = "idx_cat_series_character", columnList = "character_id")
        })
@IdClass(CatalogSeriesCharacter.Key.class)
public class CatalogSeriesCharacter {

    @Id
    @Column(name = "series_id", length = 100, nullable = false)
    private String seriesId;

    @Id
    @Column(name = "character_id", length = 100, nullable = false)
    private String characterId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_catalog_series_characters_series_id"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CatalogSeries series;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_catalog_series_characters_character_id"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CatalogCharacter character;

    protected CatalogSeriesCharacter() {
    }

    public CatalogSeriesCharacter(String seriesId, String characterId) {
        this.seriesId = seriesId;
        this.characterId = characterId;
    }

    // khóa ghép (seriesId, characterId) cho @IdClass
    public record Key(String seriesId, String characterId) implements Serializable {
    }
}
