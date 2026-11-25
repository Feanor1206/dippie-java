package com.dippie.desktop.model.catalog;

// Gom các record phẳng ánh xạ từ 5 bảng danh mục trước khi CatalogService lắp thành cây Composite.
public final class CatalogRows {
    private CatalogRows() {
    }

    // Dòng đọc từ bảng catalog_series, giữ thêm originalTitle để tìm theo tên gốc.
    public record Series(String id, String title, String originalTitle, String synopsis) {
    }

    // Dòng đọc từ bảng catalog_seasons, nối về bộ phim qua seriesId.
    public record Season(String id, String seriesId, Integer number, String title, String synopsis) {
    }

    // Dòng đọc từ bảng catalog_episodes; seasonId có thể null với các tập lẻ.
    public record Episode(String id, String seriesId, String seasonId, Integer number, String title,
                          String synopsis, Long durationMs, String recordJson) {
    }

    // Thông tin nơi lưu và mã tệp Google Drive của một tệp media.
    public record MediaAsset(String id, String storageProvider, String driveFileId, boolean enabled) {
    }

    // Dòng bảng nối giữa tập phim và tệp media, đồng thời làm khóa ghép cho EpisodeAssetDao.
    public record EpisodeAsset(String episodeId, String mediaAssetId) {
    }
}
