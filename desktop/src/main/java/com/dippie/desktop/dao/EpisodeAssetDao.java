package com.dippie.desktop.dao;

import com.dippie.desktop.db.JdbcDatabase;
import com.dippie.desktop.model.catalog.CatalogRows.EpisodeAsset;

import java.util.List;

// DAO chỉ đọc bảng nối catalog_episode_assets. Bảng này dùng khóa ghép hai cột nên truyền cả hai lên AbstractJdbcDao.
public final class EpisodeAssetDao extends AbstractJdbcDao<EpisodeAsset, EpisodeAsset> {
    public EpisodeAssetDao(JdbcDatabase db) {
        super(db, "catalog_episode_assets", "episode_id, media_asset_id",
                row -> new EpisodeAsset(row.getString("episode_id"), row.getString("media_asset_id")),
                List.of("episode_id", "media_asset_id"), key -> new Object[]{key.episodeId(), key.mediaAssetId()});
    }
}
