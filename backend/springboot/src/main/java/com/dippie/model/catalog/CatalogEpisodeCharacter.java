package com.dippie.model.catalog;

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

// bảng nối nhân vật xuất hiện trong tập nào, xóa bản ghi gốc là DB tự cascade
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

    // khóa ghép (episodeId, characterId) cho @IdClass
    public record Key(String episodeId, String characterId) implements Serializable {
    }
}
