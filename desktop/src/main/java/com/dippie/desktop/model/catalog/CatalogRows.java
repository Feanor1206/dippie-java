package com.dippie.desktop.model.catalog;

// gom mấy record phẳng map từ 5 bảng danh mục trước khi CatalogService lắp thành cây Composite
public final class CatalogRows {
    private CatalogRows() {
    }

    // dòng từ catalog_series, giữ thêm originalTitle để tìm theo tên gốc
    public record Series(String id, String title, String originalTitle, String synopsis) {
    }

    // dòng từ catalog_seasons, nối về bộ phim qua seriesId
    public record Season(String id, String seriesId, Integer number, String title, String synopsis) {
    }

    // dòng từ catalog_episodes, mấy tập lẻ thì seasonId có thể null
    public record Episode(String id, String seriesId, String seasonId, Integer number, String title,
                          String synopsis, Long durationMs, String recordJson) {
    }

    // nơi lưu và mã file Google Drive của 1 file media
    public record MediaAsset(String id, String storageProvider, String driveFileId, boolean enabled) {
    }

    // dòng bảng nối giữa tập phim và file media, kiêm luôn khóa ghép cho EpisodeAssetDao
    public record EpisodeAsset(String episodeId, String mediaAssetId) {
    }
}
