package com.dippie.catalog.importing;

import com.dippie.catalog.domain.CatalogAttributeScale;
import com.dippie.catalog.domain.CatalogCharacter;
import com.dippie.catalog.domain.CatalogEpisode;
import com.dippie.catalog.domain.CatalogGenre;
import com.dippie.catalog.domain.CatalogMediaAsset;
import com.dippie.catalog.domain.CatalogRecord;
import com.dippie.catalog.domain.CatalogScene;
import com.dippie.catalog.domain.CatalogSeason;
import com.dippie.catalog.domain.CatalogSegment;
import com.dippie.catalog.domain.CatalogSeries;
import com.dippie.catalog.domain.CatalogSource;
import com.dippie.catalog.domain.CatalogTextTrack;
import com.fasterxml.jackson.databind.JsonNode;

// Gom tên trường JSON, khóa đếm trong kết quả trả về và lớp entity JPA cho từng loại dữ liệu trong gói.
// Thứ tự khai báo ở đây cũng là thứ tự duyệt khi kiểm tra và xuất danh mục.
enum CatalogCollection {
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

    final String field;
    final String resultKey;
    final Class<? extends CatalogRecord> entityType;

    CatalogCollection(String field, Class<? extends CatalogRecord> entityType) {
        this(field, field, entityType);
    }

    CatalogCollection(String field, String resultKey, Class<? extends CatalogRecord> entityType) {
        this.field = field;
        this.resultKey = resultKey;
        this.entityType = entityType;
    }

    JsonNode items(JsonNode document) {
        return document.path(field);
    }
}
