package com.dippie.dto.assistant;

import com.dippie.common.text.UnicodeText;
import com.dippie.exception.DomainException;
import com.dippie.model.assistant.SearchConstraints;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

// Yêu cầu tìm kiếm khám phá sau khi tự kiểm tra từ JsonNode.
// Mình tách bước kiểm tra thủ công ở đây thay vì dùng Bean Validation để giữ nguyên cấu trúc lỗi (type, loc, msg, input),
// chặn các trường lạ và tách riêng lỗi cấu trúc 422 với lỗi câu tìm kiếm toàn khoảng trắng 400.
public record DiscoverRequest(String query, int limit, SearchConstraints constraints) {

    private static final Set<String> REQUEST_FIELDS = Set.of("query", "sessionId", "userId", "limit", "constraints");
    private static final Set<String> CONSTRAINT_FIELDS = Set.of("include_genres", "exclude_genres", "year_min",
            "year_max", "season_number", "episode_number", "series_only");
    private static final Pattern SESSION_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final Pattern INTEGER_STRING_PATTERN = Pattern.compile("[+-]?[0-9]++(?:_[0-9]++)*+(?:\\.0++)?");

    // Đọc và kiểm tra toàn bộ payload JSON, gom đủ lỗi từng trường trước khi trả 422 hoặc 400.
    public static DiscoverRequest parse(JsonNode body) {
        Validation validation = new Validation();
        List<Object> path = List.of("body");
        if (body == null || body.isNull()) {
            validation.error("missing", path, "Thiếu dữ liệu gửi lên.", NullNode.getInstance());
            throw validation.rejection();
        }
        if (!body.isObject()) {
            validation.expectedObject(path, body);
            throw validation.rejection();
        }
        // Kiểm tra từng trường hợp lệ và chặn trường dư để client dễ phát hiện gõ sai tên khóa.
        String query = null;
        if (!body.has("query")) {
            validation.error("missing", field(path, "query"), "Bạn chưa nhập nội dung tìm kiếm.", body);
        } else {
            query = validation.string(body.path("query"), field(path, "query"), 0, 4000);
        }
        if (body.hasNonNull("sessionId")) {
            var sessionPath = field(path, "sessionId");
            String sessionId = validation.string(body.path("sessionId"), sessionPath, 1, 128);
            if (sessionId != null && !SESSION_PATTERN.matcher(sessionId).matches()) {
                validation.error("string_pattern_mismatch", sessionPath,
                        "Mã phiên chỉ được gồm chữ cái, số, dấu gạch dưới hoặc gạch ngang (mẫu '" + SESSION_PATTERN + "').",
                        sessionId, Map.of("pattern", SESSION_PATTERN.pattern()));
            }
        }
        if (body.hasNonNull("userId")) {
            validation.string(body.path("userId"), field(path, "userId"), 0, 128);
        }
        Integer limit = body.has("limit")
                ? validation.integer(body.path("limit"), field(path, "limit"), 1, 100, false) : Integer.valueOf(8);
        SearchConstraints constraints = validation.constraints(body.path("constraints"), field(path, "constraints"));
        validation.rejectExtras(body, path, REQUEST_FIELDS);
        if (!validation.errors.isEmpty()) {
            throw validation.rejection();
        }
        if (UnicodeText.strip(query).isEmpty()) {
            throw new Rejection(HttpStatus.BAD_REQUEST, "Nội dung tìm kiếm đang bị bỏ trống.");
        }
        return new DiscoverRequest(query, limit, constraints);
    }

    private static List<Object> field(List<Object> path, Object name) {
        var nested = new ArrayList<>(path);
        nested.add(name);
        return List.copyOf(nested);
    }

    // Ngoại lệ mang sẵn mã HTTP và chi tiết lỗi (chuỗi đơn cho 400/503 hoặc danh sách lỗi trường cho 422).
    public static final class Rejection extends DomainException {

        private final transient Object detail;

        public Rejection(HttpStatus status, Object detail) {
            super(status, detail instanceof String message ? message : "Yêu cầu tìm kiếm không hợp lệ.");
            this.detail = detail;
        }

        public Object detail() {
            return detail;
        }
    }

    // Bộ gom lỗi kiểm tra từng trường JSON theo cùng định dạng mà client khám phá đang đọc.
    private static final class Validation {

        private final List<Map<String, Object>> errors = new ArrayList<>();

        private Rejection rejection() {
            return new Rejection(HttpStatus.UNPROCESSABLE_ENTITY, List.copyOf(errors));
        }

        private void error(String type, List<Object> path, String message, Object input) {
            errors.add(Map.of("type", type, "loc", path, "msg", message, "input", input));
        }

        private void error(String type, List<Object> path, String message, Object input, Map<String, Object> context) {
            errors.add(Map.of("type", type, "loc", path, "msg", message, "input", input, "ctx", context));
        }

        private void expectedObject(List<Object> path, JsonNode input) {
            error("model_attributes_type", path, "Dữ liệu gửi lên cần là đối tượng JSON.", input);
        }

        private void rejectExtras(JsonNode node, List<Object> path, Set<String> allowedFields) {
            node.fields().forEachRemaining(entry -> {
                if (!allowedFields.contains(entry.getKey())) {
                    error("extra_forbidden", field(path, entry.getKey()), "Trường này không được hỗ trợ.", entry.getValue());
                }
            });
        }

        private String string(JsonNode node, List<Object> path, int min, int max) {
            if (!node.isTextual()) {
                error("string_type", path, "Giá trị này phải là chuỗi ký tự.", node);
                return null;
            }
            String value = node.textValue();
            int length = value.codePointCount(0, value.length());
            if (length < min) {
                error("string_too_short", path, "Nội dung cần có ít nhất " + min + " ký tự.", node,
                        Map.of("min_length", min));
                return null;
            }
            if (length > max) {
                error("string_too_long", path, "Nội dung tối đa " + max + " ký tự.", node,
                        Map.of("max_length", max));
                return null;
            }
            return value;
        }

        // Trường limit cho phép ép kiểu mềm từ chuỗi số hoặc boolean (giống hành vi Pydantic cũ), còn các số trong constraints phải đúng kiểu số nguyên.
        private Integer integer(JsonNode node, List<Object> path, int min, int max, boolean strict) {
            BigDecimal value;
            if (!strict && node.isFloatingPointNumber() && !Double.isFinite(node.doubleValue())) {
                error("finite_number", path, "Giá trị phải là một số hợp lệ.", node);
                return null;
            }
            if (node.isIntegralNumber() || (!strict && node.isFloatingPointNumber())) {
                value = node.decimalValue();
            } else if (!strict && node.isBoolean()) {
                value = node.booleanValue() ? BigDecimal.ONE : BigDecimal.ZERO;
            } else if (!strict && node.isTextual()) {
                String text = UnicodeText.strip(node.textValue());
                if (!INTEGER_STRING_PATTERN.matcher(text).matches()) {
                    error("int_parsing", path, "Không đọc được giá trị này dưới dạng số nguyên.", node);
                    return null;
                }
                value = new BigDecimal(text.replace("_", ""));
            } else {
                error("int_type", path, "Giá trị này phải là số nguyên.", node);
                return null;
            }
            if (value.stripTrailingZeros().scale() > 0) {
                error("int_from_float", path, "Giá trị này cần là số nguyên, không chứa phần thập phân.", node);
            } else if (value.compareTo(BigDecimal.valueOf(min)) < 0) {
                error("greater_than_equal", path, "Giá trị phải từ " + min + " trở lên.", node, Map.of("ge", min));
            } else if (value.compareTo(BigDecimal.valueOf(max)) > 0) {
                error("less_than_equal", path, "Giá trị không được vượt quá " + max + ".", node, Map.of("le", max));
            } else {
                return value.intValueExact();
            }
            return null;
        }

        private SearchConstraints constraints(JsonNode node, List<Object> path) {
            if (node.isMissingNode() || node.isNull()) {
                return SearchConstraints.NONE;
            }
            if (!node.isObject()) {
                expectedObject(path, node);
                return SearchConstraints.NONE;
            }
            var include = genres(node.path("include_genres"), field(path, "include_genres"));
            var exclude = genres(node.path("exclude_genres"), field(path, "exclude_genres"));
            Integer yearMin = constraint(node, path, "year_min", 1800, 2200);
            Integer yearMax = constraint(node, path, "year_max", 1800, 2200);
            Integer seasonNumber = constraint(node, path, "season_number", 1, 10_000);
            Integer episodeNumber = constraint(node, path, "episode_number", 1, 100_000);
            JsonNode seriesOnly = node.path("series_only");
            if (!seriesOnly.isMissingNode() && !seriesOnly.isBoolean()) {
                error("bool_type", field(path, "series_only"), "Giá trị phải là true hoặc false.", seriesOnly);
            }
            rejectExtras(node, path, CONSTRAINT_FIELDS);
            return new SearchConstraints(include, exclude, yearMin, yearMax, seasonNumber, episodeNumber, seriesOnly.booleanValue());
        }

        private Integer constraint(JsonNode node, List<Object> path, String key, int min, int max) {
            return node.hasNonNull(key) ? integer(node.path(key), field(path, key), min, max, true) : null;
        }

        private List<String> genres(JsonNode node, List<Object> path) {
            if (node.isMissingNode()) {
                return List.of();
            }
            if (!node.isArray()) {
                error("list_type", path, "Giá trị này cần là một danh sách.", node);
                return List.of();
            }
            if (node.size() > 20) {
                error("too_long", path, "Danh sách tối đa 20 mục.",
                        node, Map.of("field_type", "List", "max_length", 20, "actual_length", node.size()));
                return List.of();
            }
            var genres = new ArrayList<String>();
            for (int index = 0; index < node.size(); index++) {
                String value = string(node.get(index), field(path, index), 1, 80);
                if (value != null) {
                    genres.add(value);
                }
            }
            return genres;
        }
    }
}
