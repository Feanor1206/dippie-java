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
import com.dippie.movie.Movie;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static com.dippie.catalog.importing.CatalogCollection.ATTRIBUTE_SCALES;
import static com.dippie.catalog.importing.CatalogCollection.CHARACTERS;
import static com.dippie.catalog.importing.CatalogCollection.EPISODES;
import static com.dippie.catalog.importing.CatalogCollection.GENRES;
import static com.dippie.catalog.importing.CatalogCollection.MEDIA_ASSETS;
import static com.dippie.catalog.importing.CatalogCollection.SCENES;
import static com.dippie.catalog.importing.CatalogCollection.SEASONS;
import static com.dippie.catalog.importing.CatalogCollection.SEGMENTS;
import static com.dippie.catalog.importing.CatalogCollection.SERIES;
import static com.dippie.catalog.importing.CatalogCollection.SOURCES;
import static com.dippie.catalog.importing.CatalogCollection.TEXT_TRACKS;

// Ghi các bảng danh mục theo đúng thứ tự khóa ngoại, dùng hàm upsert generic để 11 loại entity đi chung một luồng thêm hoặc cập nhật.
@Repository
class CatalogRecordWriter {

    private final EntityManager entities;
    private final CatalogLegacyRows legacy;
    private final CatalogLinks links;

    CatalogRecordWriter(EntityManager entities, CatalogLegacyRows legacy, CatalogLinks links) {
        this.entities = entities;
        this.legacy = legacy;
        this.links = links;
    }

    // Ghi toàn bộ 11 bảng danh mục theo thứ tự cha trước con sau, xen kẽ flush để khóa ngoại luôn khớp.
    void write(JsonNode document, CatalogDiff.Analysis diff, int revision, LocalDateTime now) {
        Batch batch = new Batch(document, diff.storedRows(), revision, now);
        Map<String, Movie> movies = legacy.loadMovies(document);

        // Nhóm bảng gốc độc lập và bộ phim phải xuống DB trước mùa và tập.
        upsert(batch, GENRES, CatalogGenre.class, CatalogRecordMapper::genre, CatalogGenre::applyImport);
        upsert(batch, ATTRIBUTE_SCALES, CatalogAttributeScale.class, CatalogRecordMapper::scale, CatalogAttributeScale::applyImport);
        upsert(batch, SOURCES, CatalogSource.class, CatalogRecordMapper::source, CatalogSource::applyImport);
        upsert(batch, SERIES, CatalogSeries.class, CatalogRecordMapper::series, CatalogSeries::applyImport);
        entities.flush();

        // Ghi mùa rồi tới tập phim, xong đồng bộ metadata sang bảng movies cũ.
        upsert(batch, SEASONS, CatalogSeason.class, CatalogRecordMapper::season, CatalogSeason::applyImport);
        entities.flush();
        upsert(batch, EPISODES, CatalogEpisode.class,
                CatalogRecordMapper::episode, CatalogEpisode::applyImport);
        entities.flush();
        legacy.syncMetadata(document, movies);

        // Dời tạm các phân đoạn đổi chỗ sang vị trí âm trước khi ghi phân đoạn, nhân vật và các bảng nối phân loại.
        moveSegments(batch);
        upsert(batch, SEGMENTS, CatalogSegment.class, CatalogRecordMapper::segment, CatalogSegment::applyImport);
        upsert(batch, CHARACTERS, CatalogCharacter.class, CatalogRecordMapper::character, CatalogCharacter::applyImport);
        entities.flush();
        links.replaceTaxonomy(document);

        // Cuối cùng ghi tệp media, phụ đề và phân cảnh phụ thuộc vào tập phim.
        writeAssets(batch, movies);
        upsert(batch, TEXT_TRACKS, CatalogTextTrack.class, CatalogRecordMapper::textTrack, CatalogTextTrack::applyImport);
        upsert(batch, SCENES, CatalogScene.class, CatalogRecordMapper::scene, CatalogScene::applyImport);
        entities.flush();
    }

    // Khi gói nhập lại không đổi nội dung vẫn làm mới metadata bảng movies cũ để sửa lệch dữ liệu nếu có.
    void refreshLegacyMetadata(JsonNode document) {
        legacy.syncMetadata(document, legacy.loadMovies(document));
    }

    // Ghi và flush hết các media_assets trước rồi mới gắn related_asset_id, tránh tự tham chiếu vào bản ghi chưa có trong DB.
    private void writeAssets(Batch batch, Map<String, Movie> movies) {
        JsonNode items = MEDIA_ASSETS.items(batch.document());
        if (items.isEmpty()) {
            return;
        }
        List<CatalogMediaAsset> assets = upsert(batch, MEDIA_ASSETS, CatalogMediaAsset.class,
                CatalogRecordMapper::asset, CatalogMediaAsset::applyImport);
        legacy.syncAssets(items, assets, movies);
        entities.flush();
        for (int index = 0; index < assets.size(); index++) {
            CatalogMediaAsset asset = assets.get(index);
            asset.linkRelatedAsset(items.get(index).path("related_asset_id").textValue());
            asset.recordImportTime(batch.now());
        }
        links.syncEpisodeAssets(items);
    }

    // Hàm generic dùng chung cho mọi bảng CatalogRecord: chưa có thì persist mới, có rồi thì gọi callback cập nhật tại chỗ.
    private <T extends CatalogRecord> List<T> upsert(Batch batch, CatalogCollection collection, Class<T> type,
            Function<CatalogRecordMapper.Row, T> create, BiConsumer<T, T> update) {
        JsonNode items = collection.items(batch.document());
        List<T> written = new ArrayList<>(items.size());
        for (JsonNode item : items) {
            T imported = create.apply(new CatalogRecordMapper.Row(item, batch.revision()));
            T existing = type.cast(batch.storedRows().get(collection).get(imported.getId()));
            if (existing == null) {
                imported.recordImportTime(batch.now());
                entities.persist(imported);
                written.add(imported);
            } else {
                update.accept(existing, imported);
                existing.recordImportTime(batch.now());
                written.add(existing);
            }
        }
        return written;
    }

    // Việt đừng bỏ bước dời tạm sang vị trí âm này nhé.
    // Khi hai phân đoạn trong cùng tập đổi chỗ cho nhau, ghi thẳng vị trí mới sẽ đụng ngay unique (episode_id, position).
    private void moveSegments(Batch batch) {
        List<CatalogSegment> moving = new ArrayList<>();
        Map<String, Set<Integer>> occupied = new HashMap<>();
        Map<String, CatalogRecord> stored = batch.storedRows().get(SEGMENTS);
        for (JsonNode item : SEGMENTS.items(batch.document())) {
            if (stored.get(item.path("id").textValue()) instanceof CatalogSegment segment
                    && (!segment.getEpisodeId().equals(item.path("episode_id").textValue())
                    || segment.getPosition() != item.path("position").asInt(1))) {
                moving.add(segment);
                occupied.computeIfAbsent(segment.getEpisodeId(), id -> new HashSet<>());
            }
        }
        if (moving.isEmpty()) {
            return;
        }
        // Đọc sẵn các số âm đang tồn tại trong DB (nếu có) để tránh gán trùng số âm tạm.
        entities.createQuery("""
                select row.episodeId, row.position from CatalogSegment row
                where row.episodeId in :ids and row.position < 0
                """, Object[].class).setParameter("ids", occupied.keySet()).getResultList().forEach(row ->
                occupied.get((String) row[0]).add((Integer) row[1]));
        for (CatalogSegment segment : moving) {
            Set<Integer> positions = occupied.get(segment.getEpisodeId());
            int temporaryPosition = -1;
            while (!positions.add(temporaryPosition)) {
                temporaryPosition--;
            }
            segment.moveToTemporaryPosition(temporaryPosition);
            segment.recordImportTime(batch.now());
        }
        entities.flush();
    }

    // Gom tài liệu JSON, bản đồ các hàng đang lưu, phiên bản và thời điểm nhập để đỡ truyền nhiều tham số qua các bước ghi.
    private record Batch(JsonNode document, Map<CatalogCollection, Map<String, CatalogRecord>> storedRows,
            int revision, LocalDateTime now) {
    }
}
