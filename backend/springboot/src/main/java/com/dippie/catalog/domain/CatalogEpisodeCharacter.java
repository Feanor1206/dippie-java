package com.dippie.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.io.Serializable;

// Bảng nối cho biết nhân vật nào xuất hiện trong tập phim nào, khóa ngoại tự cascade khi bản ghi gốc bị xóa.
@Entity
@Table(name = "catalog_episode_characters",
        indexes = {
                @Index(name = "idx_cat_ep_character", columnList = "character_id")
        })
@IdClass(CatalogEpisodeCharacter.Key.class)
public class CatalogEpisodeCharacter {

    @Id
    @Column(name = "episode_id", length = 100, nullable = false)
    private String episodeId;

    @Id
    @Column(name = "character_id", length = 100, nullable = false)
    private String characterId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id", insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CatalogEpisode episode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id", insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CatalogCharacter character;

    protected CatalogEpisodeCharacter() {
    }

    public CatalogEpisodeCharacter(String episodeId, String characterId) {
        this.episodeId = episodeId;
        this.characterId = characterId;
    }

    // Khóa ghép (episodeId, characterId) của dòng liên kết.
    public record Key(String episodeId, String characterId) implements Serializable {
    }
}
