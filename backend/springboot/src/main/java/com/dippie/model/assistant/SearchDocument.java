package com.dippie.model.assistant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

// tài liệu phẳng đại diện bộ phim, tập, phân đoạn, cảnh hay đoạn thoại để nạp vào LexicalIndex
public record SearchDocument(String id, String itemType, Placement placement, Text text, String runtime, Long durationMs,
        String primaryMediaAssetId, String mediaAssetId, Timecode timecode, Map<String, Object> comparativeAttributes,
        Integer segmentPosition) {

    public SearchDocument {
        comparativeAttributes = comparativeAttributes == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(comparativeAttributes));
    }

    // vị trí của tài liệu trong cây danh mục (bộ, mùa, tập, phân đoạn, năm, thể loại)
    public record Placement(String seriesId, String seriesTitle, String seasonId, Integer seasonNumber, String episodeId,
            String episodeTitle, Integer episodeNumber, String segmentId, String airDate, Integer year,
            List<String> genres, List<String> genreTerms, List<String> genreLabels) {

        public Placement {
            genres = List.copyOf(genres);
            genreTerms = List.copyOf(genreTerms);
            genreLabels = List.copyOf(genreLabels);
        }
    }

    // mốc đầu cuối trên cả file video gốc lẫn dòng thời gian của tập
    public record Timecode(Long sourceStart, Long sourceEnd, Long episodeStart, Long episodeEnd) {

        public static final Timecode NONE = new Timecode(null, null, null, null);

        // file gốc không bị cắt lệch giờ thì mốc file trùng luôn mốc tập
        public static Timecode unshifted(Long start, Long end) {
            return new Timecode(start, end, start, end);
        }
    }

    // tách chữ theo nhóm trọng số để BM25 ưu tiên tên phim với nhân vật hơn đoạn mô tả dài
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

        // gom hết chuỗi chữ không trùng để tách từ và đo độ dài cho công thức BM25
        public String documentText(Placement placement) {
            return String.join("\n", new LinkedHashSet<>(values(title, placement.seriesTitle, placement.episodeTitle,
                    description, synopsis, visualSummary, dialogueSummary, aliases, characters, plotFacts, keywords,
                    themes, soundEvents)));
        }

        // nhóm trọng số cao gồm tiêu đề, tên bộ/tập, bí danh với nhân vật
        public String highWeight(Placement placement) {
            return String.join(" ", values(title, placement.seriesTitle, placement.episodeTitle, aliases, characters));
        }

        // nhóm trọng số vừa gồm từ khóa, chủ đề, tình tiết với âm thanh
        public String mediumWeight() {
            return String.join(" ", values(keywords, themes, plotFacts, soundEvents));
        }

        // nhặt đoạn trích theo thứ tự ưu tiên: lời thoại, mô tả hình, mô tả chung, tóm tắt rồi mới tới tiêu đề
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
