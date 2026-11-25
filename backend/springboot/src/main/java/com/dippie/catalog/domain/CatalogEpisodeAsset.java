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

// Liên kết nhiều-nhiều giữa tập phim và file media; xóa tập hoặc xóa media thì DB tự cascade xóa dòng nối.
@Entity
@Table(name = "catalog_episode_assets",
        indexes = {
                @Index(name = "idx_cat_episode_assets_media", columnList = "media_asset_id")
        })
@IdClass(CatalogEpisodeAsset.Key.class)
public class CatalogEpisodeAsset {

    @Id
    @Column(name = "episode_id", length = 100, nullable = false)
    private String episodeId;

    @Id
    @Column(name = "media_asset_id", length = 100, nullable = false)
    private String mediaAssetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_cat_ep_asset_ep"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CatalogEpisode episode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_asset_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_cat_ep_asset_media"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CatalogMediaAsset mediaAsset;

    protected CatalogEpisodeAsset() {
    }

    public CatalogEpisodeAsset(String episodeId, String mediaAssetId) {
        this.episodeId = episodeId;
        this.mediaAssetId = mediaAssetId;
    }

    // Cặp khóa ghép (episodeId, mediaAssetId) dùng cho @IdClass.
    public record Key(String episodeId, String mediaAssetId) implements Serializable {
    }
}
