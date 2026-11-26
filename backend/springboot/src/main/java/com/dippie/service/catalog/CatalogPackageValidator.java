package com.dippie.service.catalog;

import com.dippie.common.json.CatalogJson;
import com.dippie.common.json.JsonText;
import com.dippie.common.text.UnicodeText;
import com.dippie.exception.CatalogInputException;
import com.dippie.model.catalog.CatalogCollection;
import com.dippie.model.catalog.CatalogDocument;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

// check cấu trúc JSON, giới hạn số lượng, trùng mã với đường dẫn file an toàn trước khi cho gói xuống bước ghi DB
@Component
public class CatalogPackageValidator {

    private static final int MAX_ID_LENGTH = 100;
    private static final Pattern STORAGE_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    private static final Pattern SHA256 = Pattern.compile("[a-fA-F0-9]{64}");
    private static final Pattern PARENT_PATH = Pattern.compile("\\.\\. *");
    private static final Set<String> ACCESS_POLICIES = Set.of("public", "authenticated", "private");
    private static final Set<String> TEXT_TRACK_KINDS = Set.of("subtitle", "transcript", "ocr");

    public PackageIdentity validate(CatalogDocument document) {
        return validate(document.readOnlyJson());
    }

    // check thông tin gói chính rồi chặn tổng số mục quá 100.000 kẻo payload khủng làm nghẽn RAM
    public PackageIdentity validate(JsonNode document) {
        JsonNode packageNode = document.path("package");
        if (!packageNode.isObject() || !JsonText.isTruthy(packageNode.path("id"))) {
            throw new CatalogInputException("package.id", "Thiếu mã gói danh mục.");
        }
        String id = checkId(packageNode.path("id"), "package.id", MAX_ID_LENGTH);
        int revision = checkRevision(packageNode.path("revision"), "package.revision", 1);
        int totalItems = 0;
        for (CatalogCollection collection : CatalogCollection.values()) {
            JsonNode items = collection.items(document);
            if (!CatalogJson.absent(items) && items.isArray()) {
                totalItems += items.size();
                if (totalItems > 100_000) {
                    throw new CatalogInputException("package", "Gói danh mục vượt quá số lượng mục tối đa cho phép.");
                }
            }
            validateCollection(collection, items, revision);
        }
        return new PackageIdentity(id, revision);
    }

    private void validateCollection(CatalogCollection collection, JsonNode items, int revision) {
        if (CatalogJson.absent(items)) {
            return;
        }
        if (!items.isArray()) {
            throw new CatalogInputException(collection.field(), "Trường này phải là một danh sách.");
        }
        if (items.size() > 50_000) {
            throw new CatalogInputException(collection.field(), "Danh sách vượt quá số lượng tối đa cho phép.");
        }
        Set<String> ids = new HashSet<>();
        Set<SegmentPosition> positions = new HashSet<>();
        int idLimit = collection == CatalogCollection.MEDIA_ASSETS ? 64 : MAX_ID_LENGTH;
        // dùng Unicode caseFold lúc check trùng mã (kiểu Straße với STRASSE) kẻo vỡ khóa chính ở collation không phân biệt hoa thường
        for (int index = 0; index < items.size(); index++) {
            JsonNode item = items.get(index);
            String path = collection.field() + "[" + index + "]";
            if (!item.isObject()) {
                throw new CatalogInputException(path, "Mục dữ liệu không hợp lệ.");
            }
            String id = checkId(item.path("id"), path + ".id", idLimit);
            if (!ids.add(UnicodeText.caseFold(id))) {
                throw new CatalogInputException(path + ".id", "Mã bị trùng trong danh sách.");
            }
            validateRecord(item.path("record"), path, revision);
            switch (collection) {
                case SEASONS, EPISODES -> validateSeriesChild(collection, item, path);
                case SOURCES -> validateSource(item, path);
                case SEGMENTS -> validateSegment(item, path, positions);
                case TEXT_TRACKS -> validateTextTrack(item, path);
                case MEDIA_ASSETS -> validateAsset(item, id, path);
                default -> {
                }
            }
        }
    }

    private void validateRecord(JsonNode record, String path, int revision) {
        if (CatalogJson.absent(record)) {
            return;
        }
        if (!record.isObject()) {
            throw new CatalogInputException(path + ".record", "Thông tin bản ghi không hợp lệ.");
        }
        checkRevision(record.path("revision"), path + ".record.revision", revision);
    }

    private void validateSeriesChild(CatalogCollection collection, JsonNode item, String path) {
        checkId(item.path("series_id"), path + ".series_id", 100);
        boolean episode = collection == CatalogCollection.EPISODES;
        try {
            CatalogDates.releaseDate(item, episode);
        } catch (IllegalArgumentException exception) {
            throw new CatalogInputException(path + ".release_date", exception.getMessage());
        }
        if (episode && !CatalogJson.absent(item.path("season_id"))) {
            checkId(item.path("season_id"), path + ".season_id", 100);
        }
    }

    private void validateSource(JsonNode item, String path) {
        try {
            CatalogDates.retrievedAt(item.path("retrieved_at"));
        } catch (IllegalArgumentException exception) {
            throw new CatalogInputException(path + ".retrieved_at", exception.getMessage());
        }
    }

    private void validateSegment(JsonNode item, String path, Set<SegmentPosition> positions) {
        String episode = checkId(item.path("episode_id"), path + ".episode_id", 100);
        JsonNode position = item.path("position");
        if (position.isMissingNode()) {
            position = IntNode.valueOf(1);
        }
        if (!positiveInteger(position)) {
            throw new CatalogInputException(path + ".position", "Vị trí phân đoạn phải là số nguyên dương.");
        }
        if (!positions.add(new SegmentPosition(episode, position.intValue()))) {
            throw new CatalogInputException(path + ".position", "Vị trí phân đoạn bị trùng trong cùng một tập.");
        }
        JsonNode start = item.path("episode_start_ms");
        JsonNode end = item.path("episode_end_ms");
        if ((!CatalogJson.absent(start) || !CatalogJson.absent(end))
                && (!start.isIntegralNumber() || !end.isIntegralNumber() || start.bigIntegerValue().signum() < 0
                || start.bigIntegerValue().compareTo(end.bigIntegerValue()) >= 0)) {
            throw new CatalogInputException(path, "Phân đoạn cần đủ cả hai mốc thời gian và mốc kết thúc phải sau mốc bắt đầu.");
        }
    }

    private void validateTextTrack(JsonNode item, String path) {
        if (CatalogJson.absent(item.path("media_asset_id"))) {
            checkId(item.path("episode_id"), path + ".episode_id", 100);
            checkId(item.path("source_id"), path + ".source_id", 100);
        }
        // chặn kind nằm ngoài tập ràng buộc CHECK của bảng text_tracks
        JsonNode kind = item.path("kind");
        if (!CatalogJson.absent(kind) && (!kind.isTextual() || !TEXT_TRACK_KINDS.contains(kind.textValue()))) {
            throw new CatalogInputException(path + ".kind", "Loại phụ đề hoặc văn bản không hợp lệ.");
        }
        // check đủ 3 trường của file phụ đề ngoài và chặn đường dẫn nhảy khỏi thư mục lưu trữ
        JsonNode file = item.path("external_file");
        if (CatalogJson.absent(file)) {
            return;
        }
        String filePath = path + ".external_file";
        if (!file.isObject() || file.size() != 3 || !file.has("storage_key") || !file.has("relative_path") || !file.has("sha256")) {
            throw new CatalogInputException(filePath, "Thiếu thông tin lưu tệp phụ đề.");
        }
        if (!matches(STORAGE_KEY, file.path("storage_key"))) {
            throw new CatalogInputException(filePath + ".storage_key", "Mã kho lưu trữ tệp phụ đề không hợp lệ.");
        }
        if (!isRelativeMediaPath(file.path("relative_path").textValue())) {
            throw new CatalogInputException(filePath + ".relative_path", "Đường dẫn tệp phụ đề phải là đường dẫn tương đối.");
        }
        if (!file.path("sha256").isNull() && !matches(SHA256, file.path("sha256"))) {
            throw new CatalogInputException(filePath + ".sha256", "Mã SHA-256 của tệp phụ đề không hợp lệ.");
        }
    }

    private void validateAsset(JsonNode item, String id, String path) {
        // check danh sách tập trong coverage nếu gói có khai báo
        JsonNode coverage = item.path("coverage");
        if (!CatalogJson.absent(coverage)) {
            if (!coverage.isArray()) {
                throw new CatalogInputException(path + ".coverage", "Phạm vi áp dụng của tệp phải là một danh sách.");
            }
            for (int index = 0; index < coverage.size(); index++) {
                JsonNode part = coverage.get(index);
                String partPath = path + ".coverage[" + index + "]";
                if (!part.isObject()) {
                    throw new CatalogInputException(partPath, "Thông tin phạm vi của tệp không hợp lệ.");
                }
                checkId(part.path("episode_id"), partPath + ".episode_id", 100);
            }
        }
        // check loại file, đường dẫn tương đối an toàn với quyền truy cập hợp lệ
        JsonNode type = item.path("content_type");
        String contentType = type.isMissingNode() ? "video" : type.textValue();
        if (!"audio".equals(contentType) && !"video".equals(contentType)) {
            throw new CatalogInputException(path + ".content_type", "Loại tệp phải là video hoặc audio.");
        }
        JsonNode storage = item.path("storage");
        if (!CatalogJson.absent(storage) && !storage.isObject()) {
            throw new CatalogInputException(path + ".storage", "Thông tin lưu trữ không hợp lệ.");
        }
        JsonNode relativePathNode = storage.path("relative_path");
        String relativePath = relativePathNode.isMissingNode() ? "media/" + id + ".mp4" : relativePathNode.textValue();
        if (!isRelativeMediaPath(relativePath) || relativePath.codePointCount(0, relativePath.length()) > 500) {
            throw new CatalogInputException(path + ".storage.relative_path", "Đường dẫn tệp phải nằm trong thư mục lưu trữ.");
        }
        JsonNode policy = item.path("access_policy");
        if (!policy.isMissingNode() && (!policy.isTextual()
                || !ACCESS_POLICIES.contains(policy.textValue()))) {
            throw new CatalogInputException(path + ".access_policy", "Quyền truy cập tệp không hợp lệ.");
        }
    }

    // chặn path tuyệt đối, backslash, dấu hai chấm, ký tự điều khiển với mọi đoạn ".." (kể cả dính khoảng trắng) để ngừa path traversal
    private static boolean isRelativeMediaPath(String path) {
        if (path == null || UnicodeText.strip(path).isEmpty() || path.startsWith("/")
                || path.chars().anyMatch(ch -> ch == '\\' || ch == ':' || ch < 32 || ch == 127)) {
            return false;
        }
        for (String segment : path.split("/", -1)) {
            if (PARENT_PATH.matcher(segment).matches()) {
                return false;
            }
        }
        return true;
    }

    private static String checkId(JsonNode node, String path, int maxLength) {
        String id = node.textValue();
        if (id == null || id.isEmpty() || !id.equals(UnicodeText.strip(id)) || id.codePointCount(0, id.length()) > maxLength) {
            throw new CatalogInputException(path, "Mã không hợp lệ.");
        }
        return id;
    }

    private static int checkRevision(JsonNode node, String path, int fallback) {
        if (node.isMissingNode()) {
            return fallback;
        }
        if (!positiveInteger(node)) {
            throw new CatalogInputException(path, "Số phiên bản phải là số nguyên dương.");
        }
        return node.intValue();
    }

    private static boolean positiveInteger(JsonNode node) {
        return node.isIntegralNumber() && node.canConvertToInt() && node.intValue() >= 1;
    }

    private static boolean matches(Pattern pattern, JsonNode node) {
        return node.isTextual() && pattern.matcher(node.textValue()).matches();
    }

    // mã gói với số phiên bản sau khi qua bước kiểm tra
    public record PackageIdentity(String id, int revision) {
    }

    // cặp episodeId với position để bắt trùng thứ tự phân đoạn ngay trong cùng gói nhập
    private record SegmentPosition(String episodeId, int position) {
    }
}
