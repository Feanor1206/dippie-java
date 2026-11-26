package com.dippie.desktop.dao;

import com.dippie.desktop.db.JdbcDatabase;
import com.dippie.desktop.model.catalog.CatalogRows.Episode;

// DAO đọc catalog_episodes, chỗ cột cuối dễ nhầm nha Việt vì record_json chỉ giữ trạng thái và phiên bản
// mã YouTube nằm ở external_ids trong raw_json nên lấy raw_json đưa vào Episode.recordJson
public final class EpisodeDao extends AbstractJdbcDao<Episode, String> {
    public EpisodeDao(JdbcDatabase db) {
        super(db, "catalog_episodes", "id, series_id, season_id, number, title, synopsis, duration_ms, raw_json",
                row -> new Episode(row.getString("id"), row.getString("series_id"), row.getString("season_id"),
                        row.getObject("number", Integer.class), row.getString("title"), row.getString("synopsis"),
                        row.getObject("duration_ms", Long.class), row.getString("raw_json")));
    }
}
