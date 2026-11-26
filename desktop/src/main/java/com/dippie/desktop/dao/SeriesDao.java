package com.dippie.desktop.dao;

import com.dippie.desktop.db.JdbcDatabase;
import com.dippie.desktop.model.catalog.CatalogRows.Series;

// DAO đọc bảng catalog_series để lấy danh sách bộ phim
public final class SeriesDao extends AbstractJdbcDao<Series, String> {
    public SeriesDao(JdbcDatabase db) {
        super(db, "catalog_series", "id, title, original_title, synopsis",
                row -> new Series(row.getString("id"), row.getString("title"),
                        row.getString("original_title"), row.getString("synopsis")));
    }
}
