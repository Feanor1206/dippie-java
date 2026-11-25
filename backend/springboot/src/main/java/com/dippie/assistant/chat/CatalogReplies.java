package com.dippie.assistant.chat;

import com.dippie.assistant.search.SearchEvidence;
import com.dippie.assistant.search.SearchHit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class CatalogReplies {

    private static final int MAX_EVIDENCE_ITEMS = 3;
    private static final int MAX_EVIDENCE_LENGTH = 1200;
    private static final Pattern VIDEO_SUFFIX = Pattern.compile(
            "(?:\\s*\\[[A-Za-z0-9_-]{11}\\])?\\.(?:mp4|mkv|webm)$",
            Pattern.CASE_INSENSITIVE
    );

    private CatalogReplies() {
    }

    public static List<String> evidenceTexts(SearchHit item) {
        if (item == null || item.evidence() == null || item.evidence().isEmpty()) {
            return List.of();
        }
        List<String> quotes = new ArrayList<>();
        int limit = Math.min(MAX_EVIDENCE_ITEMS, item.evidence().size());
        for (int i = 0; i < limit; i++) {
            SearchEvidence quote = item.evidence().get(i);
            if (quote == null || quote.text() == null || quote.text().isEmpty()) {
                continue;
            }
            String text = quote.text();
            quotes.add(text.length() > MAX_EVIDENCE_LENGTH ? text.substring(0, MAX_EVIDENCE_LENGTH) : text);
        }
        return List.copyOf(quotes);
    }

    public static String sourceIntro(String title, Long startMs, String mode) {
        String safeTitle = title == null ? "" : title.trim();
        String cleaned = stripTrailingDots(VIDEO_SUFFIX.matcher(safeTitle).replaceFirst(""));
        String intro = "recommend".equals(mode) ? "Bạn có thể xem" : "Có vẻ bạn đang tìm";
        if (startMs == null) {
            return intro + " " + cleaned + ".";
        }
        long seconds = Math.max(0L, startMs) / 1000L;
        return String.format("%s %s, khoảng %02d:%02d.", intro, cleaned, seconds / 60L, seconds % 60L);
    }

    public static String catalogReply(List<SearchHit> results, String mode) {
        if ("chitchat".equals(mode)) {
            return "Mình là CineBot. Bạn có thể mô tả cảnh hoặc tập phim muốn tìm trong danh mục.";
        }
        if (results == null || results.isEmpty()) {
            return "Hiện chưa tìm thấy nội dung phù hợp trong danh mục.";
        }
        SearchHit first = results.getFirst();
        String title = resolveTitle(first);
        String prefix = sourceIntro(title, first.effectiveStartMs(), mode);
        List<String> quotes = evidenceTexts(first);
        if (quotes.isEmpty()) {
            return prefix;
        }
        String quote = quotes.getFirst();
        if (quote.startsWith("Tập phim " + title) && quote.contains("của bộ hoạt hình")) {
            return prefix;
        }
        String episodeNumberText = first.episodeNumber() == null ? "None" : String.valueOf(first.episodeNumber());
        if (quote.equals("Episode " + episodeNumberText + ": " + title)) {
            return prefix;
        }
        return prefix + " " + quote;
    }

    public static String seriesReply(List<SearchHit> results, String detail) {
        if (results == null || results.isEmpty()) {
            return "Chưa tìm thấy bộ phim này trong danh mục.";
        }
        SearchHit series = results.getFirst();
        String title = series.title() == null ? "" : series.title();
        if ("episodes".equals(detail)) {
            return "Danh mục hiện có " + countOrZero(series.episodeCount()) + " tập " + title + ".";
        }
        if ("seasons".equals(detail)) {
            return "Danh mục hiện có " + countOrZero(series.seasonCount()) + " mùa " + title + ".";
        }
        if ("characters".equals(detail) && series.characters() != null && !series.characters().isEmpty()) {
            return "Nhân vật trong " + title + " gồm " + String.join(", ", series.characters()) + ".";
        }
        if ("summary".equals(detail)) {
            String synopsis = resolveSynopsis(series, title);
            if (synopsis != null && !synopsis.isEmpty()) {
                return synopsis;
            }
        }
        return "Danh mục chưa có thông tin này về " + title + ".";
    }

    static String resolveTitle(SearchHit hit) {
        if (hit.title() != null && !hit.title().isEmpty()) {
            return hit.title();
        }
        if (hit.episodeTitle() != null && !hit.episodeTitle().isEmpty()) {
            return hit.episodeTitle();
        }
        return "Tập phim";
    }

    private static int countOrZero(Integer count) {
        return count == null ? 0 : count;
    }

    static String attributeText(SearchHit hit, String key) {
        String fromDiagnostics = stringValue(hit.diagnostics(), key);
        if (fromDiagnostics != null) {
            return fromDiagnostics;
        }
        return stringValue(hit.comparativeAttributes(), key);
    }

    private static String stringValue(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object raw = map.get(key);
        return raw instanceof String text ? text : null;
    }

    private static String resolveSynopsis(SearchHit series, String title) {
        if (series.diagnostics() != null && series.diagnostics().containsKey("synopsis")) {
            Object raw = series.diagnostics().get("synopsis");
            return raw instanceof String text && !text.isEmpty() ? text : null;
        }
        if (series.comparativeAttributes() != null && series.comparativeAttributes().containsKey("synopsis")) {
            Object raw = series.comparativeAttributes().get("synopsis");
            return raw instanceof String text && !text.isEmpty() ? text : null;
        }
        if (series.description() != null
                && !series.description().isEmpty()
                && !series.description().equals("Animation series: " + title)) {
            return series.description();
        }
        return null;
    }

    private static String stripTrailingDots(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '.') {
            end--;
        }
        return value.substring(0, end);
    }
}
