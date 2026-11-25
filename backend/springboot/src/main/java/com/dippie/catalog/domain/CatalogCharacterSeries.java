package com.dippie.catalog.domain;

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

// Bảng nối nhiều-nhiều giữa nhân vật và phim bộ; hai cột khóa ngoại đặt chỉ đọc để khỏi load entity cha lúc chèn link.
@Entity
@Table(name = "catalog_character_series",
        indexes = {
                @Index(name = "idx_cat_character_series", columnList = "series_id")
        })
@IdClass(CatalogCharacterSeries.Key.class)
public class CatalogCharacterSeries {

    @Id
    @Column(name = "character_id", length = 100, nullable = false)
    private String characterId;

    @Id
    @Column(name = "series_id", length = 100, nullable = false)
    private String seriesId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_catalog_character_series_character_id"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CatalogCharacter character;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_catalog_character_series_series_id"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CatalogSeries series;

    protected CatalogCharacterSeries() {
    }

    public CatalogCharacterSeries(String characterId, String seriesId) {
        this.characterId = characterId;
        this.seriesId = seriesId;
    }

    // Khóa chính ghép (characterId, seriesId) cho @IdClass của bảng nối.
    public record Key(String characterId, String seriesId) implements Serializable {
    }
}
