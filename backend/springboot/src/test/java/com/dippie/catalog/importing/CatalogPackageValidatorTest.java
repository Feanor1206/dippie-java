package com.dippie.catalog.importing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class CatalogPackageValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final CatalogPackageValidator validator = new CatalogPackageValidator();

    @ParameterizedTest
    @MethodSource("invalidDocuments")
    void rejectsFirstInvalidField(String json, String path, String message) throws Exception {
        var document = new CatalogDocument(mapper.readTree(json));

        CatalogInputException error = catchThrowableOfType(CatalogInputException.class, () -> validator.validate(document));

        assertThat(error.path()).isEqualTo(path);
        assertThat(error.getMessage()).isEqualTo(message);
    }

    @Test
    void defaultsRevisionAndAcceptsNullCollections() throws Exception {
        var document = new CatalogDocument(mapper.readTree("{\"package\":{\"id\":\"demo\"},\"genres\":null}"));

        assertThat(validator.validate(document).revision()).isEqualTo(1);
    }

    private static Stream<Arguments> invalidDocuments() {
        return Stream.of(
                arguments("[]", "package.id", "Thiếu mã gói danh mục."),
                arguments("{\"package\":{\"id\":\"x\",\"revision\":true},\"genres\":{}}",
                        "package.revision", "Phiên bản phải là số nguyên dương hợp lệ."),
                arguments(document("\"scenes\":{},\"genres\":{}"), "genres", "Cần một danh sách bản ghi."),
                arguments(document("\"genres\":[{\"id\":\"Straße\"},{\"id\":\"STRASSE\"}]"),
                        "genres[1].id", "Mã định danh bị trùng."),
                arguments(document("\"episodes\":[{\"id\":\"e\",\"air_date\":\"bad\"}]"),
                        "episodes[0].series_id", "Mã định danh không hợp lệ."),
                arguments(document("\"episodes\":[{\"id\":\"e\",\"series_id\":\"s\",\"air_date\":\"2025-02-29\"}]"),
                        "episodes[0].release_date", "day is out of range for month"),
                arguments(document("\"sources\":[{\"id\":\"s\",\"retrieved_at\":\"2026-11-24\"}]"),
                        "sources[0].retrieved_at", "Thời điểm phải có dạng ISO 8601."),
                arguments(document("\"segments\":[{\"id\":\"a\",\"episode_id\":\"e\"},{\"id\":\"b\",\"episode_id\":\"e\"}]"),
                        "segments[1].position", "Vị trí phân đoạn phải duy nhất trong tập."),
                arguments(segment("\"position\":0"), "segments[0].position", "Vị trí phân đoạn phải là số nguyên dương."),
                arguments(segment("\"episode_start_ms\":-1,\"episode_end_ms\":20"), "segments[0]",
                        "Phân đoạn cần đủ hai mốc và mốc kết thúc phải sau mốc bắt đầu."),
                arguments(segment("\"episode_start_ms\":0,\"episode_end_ms\":null"), "segments[0]",
                        "Phân đoạn cần đủ hai mốc và mốc kết thúc phải sau mốc bắt đầu."),
                arguments(segment("\"episode_start_ms\":0,\"episode_end_ms\":1.5"), "segments[0]",
                        "Phân đoạn cần đủ hai mốc và mốc kết thúc phải sau mốc bắt đầu."),
                arguments(asset("\"coverage\":[{}]"), "media_assets[0].coverage[0].episode_id", "Mã định danh không hợp lệ."),
                arguments(asset("\"content_type\":[]"), "media_assets[0].content_type", "Loại tệp phải là video hoặc audio."),
                arguments(asset("\"access_policy\":\"restricted\""), "media_assets[0].access_policy", "Quyền truy cập video không hợp lệ."),
                arguments(asset("\"storage\":{\"relative_path\":\"video/.. /private.mp4\"}"),
                        "media_assets[0].storage.relative_path", "Đường dẫn video phải nằm trong thư mục lưu trữ."),
                arguments(track("\"sha256\":null,\"relative_path\":\"../private.json\""),
                        "text_tracks[0].external_file.relative_path", "Đường dẫn tệp phụ đề phải là đường dẫn tương đối."),
                arguments(track("\"sha256\":\"not-a-hash\",\"relative_path\":\"captions/episode.json\""),
                        "text_tracks[0].external_file.sha256", "Mã SHA-256 của tệp phụ đề không hợp lệ."));
    }

    private static String document(String fields) {
        return "{\"package\":{\"id\":\"demo\"}," + fields + "}";
    }

    private static String segment(String fields) {
        return document("\"segments\":[{\"id\":\"segment\",\"episode_id\":\"episode\"," + fields + "}]");
    }

    private static String asset(String fields) {
        return document("\"media_assets\":[{\"id\":\"asset\"," + fields + "}]");
    }

    private static String track(String fields) {
        return document("\"text_tracks\":[{\"id\":\"track\",\"episode_id\":\"ep\",\"source_id\":\"source\","
                + "\"external_file\":{\"storage_key\":\"caption_evidence\"," + fields + "}}]");
    }
}
