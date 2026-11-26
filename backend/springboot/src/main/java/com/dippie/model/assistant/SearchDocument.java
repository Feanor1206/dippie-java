package com.dippie.model.assistant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

// Tài liệu phẳng đại diện cho một bộ phim, tập, phân đoạn, cảnh hoặc cửa sổ lời thoại đưa vào LexicalIndex.
public record SearchDocument(String id, String itemType, Placement placement, Text text, String runtime, Long durationMs,
        String primaryMediaAssetId, String mediaAssetId, Timecode timecode, Map<String, Object> comparativeAttributes,
        Integer segmentPosition) {

    public SearchDocument {
        comparativeAttributes = comparativeAttributes == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(comparativeAttributes));
    }

    // Thông tin vị trí phân cấp của tài liệu trong danh mục (bộ, mùa, tập, phân đoạn, năm và thể loại).
    public record Placement(String seriesId, String seriesTitle, String seasonId, Integer seasonNumber, String episodeId,
            String episodeTitle, Integer episodeNumber, String segmentId, String airDate, Integer year,
            List<String> genres, List<String> genreTerms, List<String> genreLabels) {

        public Placement {
            genres = List.copyOf(genres);
            genreTerms = List.copyOf(genreTerms);
            genreLabels = List.copyOf(genreLabels);
        }
    }

    // Mốc thời gian bắt đầu và kết thúc trên cả tệp video nguồn lẫn dòng thời gian của tập phim.
    public record Timecode(Long sourceStart, Long sourceEnd, Long episodeStart, Long episodeEnd) {

        public static final Timecode NONE = new Timecode(null, null, null, null);

        // Dùng khi tệp nguồn không bị cắt ghép lệch giờ nên mốc tệp trùng luôn mốc tập.
        public static Timecode unshifted(Long start, Long end) {
            return new Timecode(start, end, start, end);
        }
    }

    // Các trường chữ của tài liệu được tách theo nhóm trọng số để BM25 ưu tiên tên phim và nhân vật hơn mô tả dài.
    public record Text(String title, String synopsis, String description, List<String> aliases, List<String> characters,
            List<String> plotFacts, List<String> keywords, List<String> themes, String visualSummary,
            String dialogueSummary, List<String> soundEvents, List<String> characterNames) {

        public Text {
            aliases = copy(aliases);
            characters = copy(characters);
            plotFacts = copy(plotFacts);
            keywords = copy(keywords);
            themes = copy(themes);
            soundEvents = copy(soundEvents);
            characterNames = copy(characterNames);
        }

        // Gom mọi chuỗi chữ không trùng để tách từ và đo độ dài chuẩn cho công thức BM25.
        public String documentText(Placement placement) {
            return String.join("\n", new LinkedHashSet<>(values(title, placement.seriesTitle, placement.episodeTitle,
                    description, synopsis, visualSummary, dialogueSummary, aliases, characters, plotFacts, keywords,
                    themes, soundEvents)));
        }

        // Nhóm trường cộng trọng số cao (tiêu đề, tên bộ/tập, bí danh và nhân vật).
        public String highWeight(Placement placement) {
            return String.join(" ", values(title, placement.seriesTitle, placement.episodeTitle, aliases, characters));
        }

        // Nhóm trường cộng trọng số vừa (từ khóa, chủ đề, tình tiết và âm thanh).
        public String mediumWeight() {
            return String.join(" ", values(keywords, themes, plotFacts, soundEvents));
        }

        // Chọn đoạn trích dẫn đại diện theo thứ tự ưu tiên lời thoại, mô tả hình ảnh, mô tả chung, tóm tắt rồi mới đến tiêu đề.
        public String evidenceText() {
            return values(dialogueSummary, visualSummary, description, synopsis, title).stream().findFirst().orElse("");
        }

        private static List<String> values(Object... fields) {
            List<String> values = new ArrayList<>();
            for (Object field : fields) {
                if (field instanceof String value && !value.isEmpty()) {
                    values.add(value);
                } else if (field instanceof List<?> list) {
                    for (Object element : list) {
                        if (element instanceof String value) {
                            values.add(value);
                        }
                    }
                }
            }
            return values;
        }

        private static List<String> copy(List<String> values) {
            return values == null ? null : List.copyOf(values);
        }
    }
}
