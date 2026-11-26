package com.dippie.desktop.dao;

import com.dippie.desktop.db.JdbcDatabase;
import com.dippie.desktop.model.catalog.CatalogRows.MediaAsset;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

// DAO đọc catalog_media_assets để tìm mấy file video nằm trên Google Drive
public final class MediaAssetDao extends AbstractJdbcDao<MediaAsset, String> {
    private static final String COLUMNS = "id, storage_provider, drive_file_id, enabled";
    private static final RowMapper<MediaAsset> MAPPER =
            row -> new MediaAsset(row.getString("id"), row.getString("storage_provider"),
                    row.getString("drive_file_id"), row.getBoolean("enabled"));

    public MediaAssetDao(JdbcDatabase db) {
        super(db, "catalog_media_assets", COLUMNS, MAPPER);
    }

    // chỉ lấy video chính đang bật và có drive_file_id thật, bỏ qua file bị tắt hay rỗng
    public List<MediaAsset> findDriveAssets(Connection connection) throws SQLException {
        return query(connection, "SELECT " + COLUMNS + """
                 FROM catalog_media_assets
                WHERE storage_provider IN ('drive', 'google_drive') AND enabled = TRUE
                  AND content_type = 'video' AND role = 'feature'
                  AND drive_file_id IS NOT NULL AND drive_file_id <> ''
                ORDER BY id
                """, MAPPER);
    }
}
