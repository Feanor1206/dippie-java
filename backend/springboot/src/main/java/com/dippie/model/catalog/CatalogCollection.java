package com.dippie.model.catalog;

import com.fasterxml.jackson.databind.JsonNode;

// Gom tên trường JSON, khóa đếm trong kết quả trả về và lớp entity JPA cho từng loại dữ liệu trong gói.
// Thứ tự khai báo ở đây cũng là thứ tự duyệt khi kiểm tra và xuất danh mục.
public enum CatalogCollection {
    GENRES("genres", CatalogGenre.class),
    ATTRIBUTE_SCALES("attribute_scales", "attributeScales", CatalogAttributeScale.class),
    SOURCES("sources", CatalogSource.class),
    SERIES("series", CatalogSeries.class),
    SEASONS("seasons", CatalogSeason.class),
    EPISODES("episodes", CatalogEpisode.class),
    SEGMENTS("segments", CatalogSegment.class),
    CHARACTERS("characters", CatalogCharacter.class),
    MEDIA_ASSETS("media_assets", "mediaAssets", CatalogMediaAsset.class),
    TEXT_TRACKS("text_tracks", "textTracks", CatalogTextTrack.class),
    SCENES("scenes", CatalogScene.class);

    private final String field;
    private final String resultKey;
    private final Class<? extends CatalogRecord> entityType;

    CatalogCollection(String field, Class<? extends CatalogRecord> entityType) {
        this(field, field, entityType);
    }

    CatalogCollection(String field, String resultKey, Class<? extends CatalogRecord> entityType) {
        this.field = field;
        this.resultKey = resultKey;
        this.entityType = entityType;
    }

    public String field() {
        return field;
    }

    public String resultKey() {
        return resultKey;
    }

    public Class<? extends CatalogRecord> entityType() {
        return entityType;
    }

    public JsonNode items(JsonNode document) {
        return document.path(field);
    }
}
