package com.dippie.service.catalog;

import com.dippie.model.catalog.CatalogAttributeScale;
import com.dippie.model.catalog.CatalogCharacter;
import com.dippie.model.catalog.CatalogCollection;
import com.dippie.model.catalog.CatalogEpisode;
import com.dippie.model.catalog.CatalogGenre;
import com.dippie.model.catalog.CatalogMediaAsset;
import com.dippie.model.catalog.CatalogRecord;
import com.dippie.model.catalog.CatalogScene;
import com.dippie.model.catalog.CatalogSeason;
import com.dippie.model.catalog.CatalogSegment;
import com.dippie.model.catalog.CatalogSeries;
import com.dippie.model.catalog.CatalogSource;
import com.dippie.model.catalog.CatalogTextTrack;
import com.dippie.model.movie.Movie;

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

import static com.dippie.model.catalog.CatalogCollection.ATTRIBUTE_SCALES;
import static com.dippie.model.catalog.CatalogCollection.CHARACTERS;
import static com.dippie.model.catalog.CatalogCollection.EPISODES;
import static com.dippie.model.catalog.CatalogCollection.GENRES;
import static com.dippie.model.catalog.CatalogCollection.MEDIA_ASSETS;
import static com.dippie.model.catalog.CatalogCollection.SCENES;
import static com.dippie.model.catalog.CatalogCollection.SEASONS;
import static com.dippie.model.catalog.CatalogCollection.SEGMENTS;
import static com.dippie.model.catalog.CatalogCollection.SERIES;
import static com.dippie.model.catalog.CatalogCollection.SOURCES;
import static com.dippie.model.catalog.CatalogCollection.TEXT_TRACKS;

// ghi mấy bảng danh mục đúng thứ tự khóa ngoại, viết hàm upsert generic để 11 loại entity đi chung 1 luồng thêm hay sửa
@Repository
public class CatalogRecordWriter {

    private final EntityManager entities;
    private final CatalogLegacyRows legacy;
    private final CatalogLinks links;

    public CatalogRecordWriter(EntityManager entities, CatalogLegacyRows legacy, CatalogLinks links) {
        this.entities = entities;
        this.legacy = legacy;
        this.links = links;
    }

    // ghi đủ 11 bảng theo thứ tự cha trước con sau, kẹp flush ở giữa cho khóa ngoại luôn khớp
    public void write(JsonNode document, CatalogDiff.Analysis diff, int revision, LocalDateTime now) {
        Batch batch = new Batch(document, diff.storedRows(), revision, now);
        Map<String, Movie> movies = legacy.loadMovies(document);

        // mấy bảng gốc độc lập với bộ phim phải xuống DB trước mùa và tập nha
        upsert(batch, GENRES, CatalogGenre.class, CatalogRecordMapper::genre, CatalogGenre::applyImport);
        upsert(batch, ATTRIBUTE_SCALES, CatalogAttributeScale.class, CatalogRecordMapper::scale, CatalogAttributeScale::applyImport);
        upsert(batch, SOURCES, CatalogSource.class, CatalogRecordMapper::source, CatalogSource::applyImport);
        upsert(batch, SERIES, CatalogSeries.class, CatalogRecordMapper::series, CatalogSeries::applyImport);
        entities.flush();

        // ghi mùa rồi tới tập, xong xuôi thì đồng bộ metadata sang bảng movies cũ
        upsert(batch, SEASONS, CatalogSeason.class, CatalogRecordMapper::season, CatalogSeason::applyImport);
        entities.flush();
        upsert(batch, EPISODES, CatalogEpisode.class,
                CatalogRecordMapper::episode, CatalogEpisode::applyImport);
        entities.flush();
        legacy.syncMetadata(document, movies);

        // đẩy tạm mấy phân đoạn đổi chỗ sang vị trí âm trước khi ghi phân đoạn, nhân vật với mấy bảng nối
        moveSegments(batch);
        upsert(batch, SEGMENTS, CatalogSegment.class, CatalogRecordMapper::segment, CatalogSegment::applyImport);
        upsert(batch, CHARACTERS, CatalogCharacter.class, CatalogRecordMapper::character, CatalogCharacter::applyImport);
        entities.flush();
        links.replaceTaxonomy(document);

        // chốt hạ bằng file media, phụ đề với phân cảnh phụ thuộc tập phim
        writeAssets(batch, movies);
        upsert(batch, TEXT_TRACKS, CatalogTextTrack.class, CatalogRecordMapper::textTrack, CatalogTextTrack::applyImport);
        upsert(batch, SCENES, CatalogScene.class, CatalogRecordMapper::scene, CatalogScene::applyImport);
        entities.flush();
    }

    // gói nhập lại y hệt vẫn refresh metadata bảng movies cũ để vá lệch data nếu có
    public void refreshLegacyMetadata(JsonNode document) {
        legacy.syncMetadata(document, legacy.loadMovies(document));
    }

    // ghi rồi flush hết media_assets trước rồi mới móc related_asset_id kẻo tự trỏ vào dòng chưa nằm trong DB
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

    // hàm generic xài chung cho mọi bảng CatalogRecord, chưa có thì persist mới, có rồi thì gọi callback sửa tại chỗ
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

    // Việt đừng bỏ bước dời tạm sang vị trí âm chỗ này nha
    // hai phân đoạn trong cùng tập mà đổi chỗ nhau, ghi thẳng vị trí mới là dính ngay unique (episode_id, position)
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
        // kéo sẵn mấy số âm đang có trong DB (nếu có) kẻo gán trùng số âm tạm
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

    // gom JSON, map hàng đang lưu, phiên bản với thời điểm nhập vào 1 record cho đỡ truyền lỉnh kỉnh nhiều tham số
    private record Batch(JsonNode document, Map<CatalogCollection, Map<String, CatalogRecord>> storedRows,
            int revision, LocalDateTime now) {
    }
}
