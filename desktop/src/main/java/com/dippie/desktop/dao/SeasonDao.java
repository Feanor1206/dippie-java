package com.dippie.desktop.dao;

import com.dippie.desktop.db.JdbcDatabase;
import com.dippie.desktop.model.catalog.CatalogRows.Season;

// DAO chỉ đọc bảng catalog_seasons, giữ cột number dạng Integer vì có mùa không đánh số.
public final class SeasonDao extends AbstractJdbcDao<Season, String> {
    public SeasonDao(JdbcDatabase db) {
        super(db, "catalog_seasons", "id, series_id, number, title, synopsis",
                row -> new Season(row.getString("id"), row.getString("series_id"),
                        row.getObject("number", Integer.class), row.getString("title"), row.getString("synopsis")));
    }
}
