package com.dippie.service.catalog;

import com.dippie.common.json.CatalogJson;
import com.dippie.common.json.CatalogJsonWriter;
import com.dippie.common.json.JsonText;
import com.dippie.common.text.UnicodeText;
import com.dippie.exception.CatalogInputException;
import com.dippie.model.catalog.CatalogAttributeScale;
import com.dippie.model.catalog.CatalogCharacter;
import com.dippie.model.catalog.CatalogEpisode;
import com.dippie.model.catalog.CatalogGenre;
import com.dippie.model.catalog.CatalogMediaAsset;
import com.dippie.model.catalog.CatalogScene;
import com.dippie.model.catalog.CatalogSeason;
import com.dippie.model.catalog.CatalogSegment;
import com.dippie.model.catalog.CatalogSeries;
import com.dippie.model.catalog.CatalogSource;
import com.dippie.model.catalog.CatalogTextTrack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.math.BigDecimal;
import java.util.Locale;

import static com.dippie.common.json.CatalogJson.absent;
import static com.dippie.common.json.CatalogJson.or;

// Chuyển từng nút JSON trong gói danh mục sang entity JPA tương ứng, xử lý luôn các tên trường cũ và giá trị mặc định.
public final class CatalogRecordMapper {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private CatalogRecordMapper() {
    }

    // Tạo CatalogGenre; nếu thiếu labels thì tự dựng nhãn tiếng Việt từ name hoặc id.
    public static CatalogGenre genre(Row row) {
        JsonNode labels = or(row.field("labels"), NODES.objectNode().set("vi", row.get("name", row.id())));
        return new CatalogGenre(row.id(), json(labels), row.json("aliases"), row.text("description"),
                row.record(), row.raw(), row.revision());
    }

    // Đọc thang thuộc tính với khoảng mặc định 0 đến 1 và version 1.0.0 khi không khai báo.
    public static CatalogAttributeScale scale(Row row) {
        String levels = json(or(row.field("levels"), NODES.arrayNode()));
        return new CatalogAttributeScale(row.id(), row.text("name", row.id()), row.text("definition"),
                decimal(row.get("minimum", 0)).doubleValue(), decimal(row.get("maximum", 1)).doubleValue(),
                levels, row.text("version", "1.0.0"), row.json("source_ids"), row.record(), row.raw(), row.revision());
    }

    // Chấp nhận cả tên trường cũ (source_type, name, uri, notes) cho nguồn dữ liệu.
    public static CatalogSource source(Row row) {
        return new CatalogSource(row.id(), text(or(row.prefer("kind", "source_type"), NODES.textNode("web"))),
                text(or(row.prefer("title", "name"), row.field("id"))), text(row.prefer("url", "uri")),
                text(row.prefer("note", "notes")), CatalogDates.retrievedAt(row.field("retrieved_at")),
                row.record(), row.raw(), row.revision());
    }

    // Gộp các tên trường tương đương như tags, artworks, release_year khi dựng CatalogSeries.
    public static CatalogSeries series(Row row) {
        return new CatalogSeries(row.id(), row.text("title", row.id()), row.text("original_title"),
                row.json("aliases"), row.text("synopsis"), row.json("translations"), row.json("genre_ids"),
                json(row.prefer("keywords", "tags")), row.json("themes"), row.json("origin_countries"),
                row.json("original_languages"), integer(row.prefer("release_year_start", "release_year")),
                integer(row.field("release_year_end")), text(row.prefer("release_status", "status")),
                row.json("character_ids"), json(row.prefer("artwork", "artworks")), row.json("attributes"),
                row.json("ratings"), row.json("content_advisory"), null, row.json("external_ids"),
                row.record(), row.raw(), row.revision());
    }

    // Quy đổi kind "standard" về "regular" cho khớp giá trị chuẩn của bảng mùa phim.
    public static CatalogSeason season(Row row) {
        String kind = row.text("kind", "regular");
        return new CatalogSeason(row.id(), row.text("series_id"), row.number("season_number"), row.text("title", row.id()),
                text(row.prefer("synopsis", "description")), "standard".equals(kind) ? "regular" : kind, null,
                CatalogDates.releaseDate(row.item(), false), json(row.prefer("artwork", "artworks")), null,
                row.record(), row.raw(), row.revision());
    }

    // Gom "standard" và "episode" về loại "regular", đồng thời đọc ngày phát hành kèm dự phòng air_date.
    public static CatalogEpisode episode(Row row) {
        String kind = text(or(row.prefer("kind", "episode_type"), NODES.textNode("regular")));
        if (kind.equals("standard") || kind.equals("episode")) {
            kind = "regular";
        }
        return new CatalogEpisode(row.id(), row.text("series_id"), row.text("season_id"), row.number("episode_number"),
                row.text("title", row.id()), row.json("aliases"), row.text("synopsis"), row.json("translations"),
                kind, CatalogDates.releaseDate(row.item(), true), row.longNumber("duration_ms"), row.json("genre_ids"),
                row.text("primary_language"), null, null, row.json("character_ids"), row.json("plot_facts"),
                json(row.prefer("keywords", "tags")), row.json("themes"), row.json("attributes"), null,
                json(row.prefer("artwork", "artworks")), null, row.record(), row.raw(), row.revision());
    }

    // Đặt vị trí phân đoạn mặc định bằng 1 nếu JSON không truyền trường position.
    public static CatalogSegment segment(Row row) {
        return new CatalogSegment(row.id(), row.text("episode_id"), row.get("position", 1).intValue(),
                row.text("title", row.id()), null, row.text("synopsis"), null, null, row.json("character_ids"),
                row.json("plot_facts"), json(row.prefer("keywords", "tags")), null, row.json("attributes"),
                row.longNumber("episode_start_ms"), row.longNumber("episode_end_ms"),
                row.record(), row.raw(), row.revision());
    }

    // Đọc thông tin nhân vật sang CatalogCharacter, dùng id làm tên dự phòng nếu thiếu name.
    public static CatalogCharacter character(Row row) {
        return new CatalogCharacter(row.id(), row.json("series_ids"), row.text("name", row.id()), row.json("aliases"),
                row.text("description"), row.text("role"), row.json("traits"), row.json("relationships"), null, null,
                row.record(), row.raw(), row.revision());
    }

    // Chuẩn hóa provider về "drive" hoặc "local", suy đường dẫn mặc định và bật enabled khi trường này không có mặt.
    public static CatalogMediaAsset asset(Row row) {
        JsonNode storage = row.field("storage");
        String providerName = storage.path("provider").asText("local").toLowerCase(Locale.ROOT);
        String provider = providerName.equals("drive") || providerName.equals("google_drive") ? "drive" : "local";
        String storageKey = text(or(or(storage.path("storage_key"), storage.path("root_storage_key")),
                NODES.textNode("local_main")));
        String relativePath = storage.path("relative_path").asText("media/" + row.id() + ".mp4");
        JsonNode size = row.field("size_bytes");
        if (absent(size)) {
            size = or(row.field("byte_length"), NODES.numberNode(0));
        }
        String role = row.text("role", "feature");
        if (role == null) {
            throw new CatalogInputException("media_assets.role", "Dữ liệu hoặc liên kết trong gói danh mục không hợp lệ.");
        }
        return new CatalogMediaAsset(row.id(), row.text("content_type", "video"), role,
                text(row.prefer("edition_label", "edition")), null, null, provider, storageKey, relativePath,
                text(storage.path("drive_file_id")), text(storage.path("drive_folder_id")), longNumber(size),
                row.text("sha256"), row.longNumber("duration_ms"), row.json("technical"), row.json("coverage"),
                null, row.text("access_policy", "public"), JsonText.isTruthy(row.field("enabled")) || row.field("enabled").isMissingNode(),
                row.record(), row.raw(), row.revision());
    }

    // Mặc định kind là "subtitle" và format "vtt" để thỏa mãn ràng buộc CHECK của bảng text_tracks.
    public static CatalogTextTrack textTrack(Row row) {
        return new CatalogTextTrack(row.id(), row.text("media_asset_id"), row.text("episode_id"), row.text("source_id"),
                row.text("language", "vi"), row.text("kind", "subtitle"), row.text("label"), row.text("format", "vtt"),
                false, row.json("external_file"), row.json("cues"), row.json("provenance"),
                row.record(), row.raw(), row.revision());
    }

    // Dựng CatalogScene và chặn trường hợp thiếu episode_id trước khi ghi xuống CSDL.
    public static CatalogScene scene(Row row) {
        if (row.field("episode_id").isMissingNode()) {
            throw new IllegalArgumentException("Missing scene episode_id");
        }
        return new CatalogScene(row.id(), row.text("media_asset_id"), row.text("episode_id"), row.text("segment_id"),
                longNumber(or(row.field("start_ms"), NODES.numberNode(0))),
                longNumber(or(row.field("end_ms"), NODES.numberNode(0))), row.text("title"), row.text("summary"),
                text(row.prefer("visual_description", "visual_summary")), json(row.prefer("visual_tags", "keywords")),
                row.json("sound_events"), row.json("transcript_cue_ids"), row.json("character_ids"), null, null, null, null,
                row.record(), row.raw(), row.revision());
    }

    private static String json(JsonNode node) {
        return absent(node) ? null : CatalogJsonWriter.encode(node, false);
    }

    private static String text(JsonNode node) {
        if (absent(node)) {
            return null;
        }
        if (node.isContainerNode()) {
            throw new IllegalArgumentException("Expected a scalar value");
        }
        return node.asText();
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue() ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        return new BigDecimal(UnicodeText.strip(node.asText()));
    }

    private static Integer integer(JsonNode node) {
        return absent(node) ? null : decimal(node).toBigInteger().intValueExact();
    }

    private static Long longNumber(JsonNode node) {
        return absent(node) ? null : decimal(node).toBigInteger().longValueExact();
    }

    // Gói một mục JSON kèm packageRevision để các hàm ánh xạ đọc trường có fallback, chuỗi record và rawJson gọn hơn.
    public record Row(JsonNode item, int packageRevision) {

        public String id() {
            return field("id").textValue();
        }

        public JsonNode field(String name) {
            return item.path(name);
        }

        public JsonNode prefer(String name, String fallback) {
            return or(field(name), field(fallback));
        }

        public JsonNode get(String name, String fallback) {
            return field(name).isMissingNode() ? NODES.textNode(fallback) : field(name);
        }

        public JsonNode get(String name, int fallback) {
            return field(name).isMissingNode() ? NODES.numberNode(fallback) : field(name);
        }

        public String text(String name) {
            return CatalogRecordMapper.text(field(name));
        }

        public String text(String name, String fallback) {
            return CatalogRecordMapper.text(get(name, fallback));
        }

        public String json(String name) {
            return CatalogRecordMapper.json(field(name));
        }

        public Integer number(String fallback) {
            return integer(absent(field("number")) ? field(fallback) : field("number"));
        }

        public Long longNumber(String name) {
            return CatalogRecordMapper.longNumber(field(name));
        }

        public int revision() {
            return CatalogJson.revision(item, packageRevision);
        }

        public String record() {
            JsonNode record = or(field("record"), NODES.objectNode().put("status", "ready").put("revision", packageRevision));
            return CatalogRecordMapper.json(record);
        }

        public String raw() {
            return CatalogRecordMapper.json(item);
        }
    }
}
