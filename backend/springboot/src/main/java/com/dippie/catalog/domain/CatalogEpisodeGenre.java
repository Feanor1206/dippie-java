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

@Entity
@Table(name = "catalog_episode_genres",
        indexes = {
                @Index(name = "idx_cat_ep_genre", columnList = "genre_id")
        })
@IdClass(CatalogEpisodeGenre.Key.class)
public class CatalogEpisodeGenre {

    @Id
    @Column(name = "episode_id", length = 100, nullable = false)
    private String episodeId;

    @Id
    @Column(name = "genre_id", length = 100, nullable = false)
    private String genreId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id", insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CatalogEpisode episode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "genre_id", insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CatalogGenre genre;

    protected CatalogEpisodeGenre() {
    }

    public CatalogEpisodeGenre(String episodeId, String genreId) {
        this.episodeId = episodeId;
        this.genreId = genreId;
    }

    public String getEpisodeId() {
        return episodeId;
    }

    public String getGenreId() {
        return genreId;
    }

    public record Key(String episodeId, String genreId) implements Serializable {
    }
}
