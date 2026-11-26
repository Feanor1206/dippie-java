package com.dippie.service.assistant;

import com.dippie.common.text.TextFolding;
import com.dippie.model.assistant.SearchEvidence;
import com.dippie.model.assistant.SearchHit;
import com.dippie.model.catalog.CatalogDisplayText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

// Dựng các câu trả lời tiếng Việt theo mẫu cho CineBot từ kết quả tìm được trong kho phim.
public final class CatalogReplies {

    public static final String NO_MORE_EPISODES = "Không còn tập phù hợp nào khác rồi.";
    private static final int MAX_EVIDENCE_ITEMS = 3;
    private static final int MAX_EVIDENCE_LENGTH = 1200;
    private static final Pattern VIDEO_SUFFIX = Pattern.compile(
            "(?:\\s*\\[[A-Za-z0-9_-]{11}\\])?\\.(?:mp4|mkv|webm)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern EPISODE_SERIES = Pattern.compile("\\bthuoc (?:bo(?: phim)?|phim|series) nao\\b");
    private static final Pattern EPISODE_CHARACTERS = Pattern.compile("\\bnhan vat (?:nao|gi|chinh)\\b");

    private CatalogReplies() {
    }

    // Lấy tối đa 3 trích đoạn bằng chứng không rỗng và cắt ở 1200 ký tự để không làm phình câu trả lời hay prompt.
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

    // Dựng câu mở đầu kèm tên tập đã gọt đuôi file video và mốc phút:giây nếu có.
    public static String sourceIntro(String title, Long startMs, String mode) {
        String cleaned = cleanTitle(title);
        String intro = "recommend".equals(mode) ? "Bạn có thể xem" : "Có vẻ bạn đang tìm";
        if (startMs == null) {
            return intro + " " + cleaned + ".";
        }
        long seconds = Math.max(0L, startMs) / 1000L;
        return String.format("%s %s, khoảng %02d:%02d.", intro, cleaned, seconds / 60L, seconds % 60L);
    }

    // Báo chưa có phim khớp và liệt kê các bộ đang có để người xem biết kho phim hỗ trợ gì.
    public static String noMatch(List<String> available) {
        String text = "Mình chưa tìm thấy phim phù hợp trong kho phim.";
        return available.isEmpty() ? text : text + " Hiện kho phim có: " + String.join(", ", available) + ".";
    }

    // Ghép danh sách gợi ý khi tìm ra nhiều tập hoặc nhiều bộ phim cùng lúc.
    public static String discovery(List<SearchHit> results) {
        List<String> suggestions = results.stream().map(CatalogReplies::namedItem).toList();
        return "Bạn có thể xem " + String.join(", ", suggestions) + ".";
    }

    // Gắn thêm nhắc nhở nếu trong kết quả có tập không nhúng phát trực tiếp được mà phải mở trên YouTube.
    public static String withPlaybackNote(String answer, List<SearchHit> results) {
        var external = results.stream().filter(SearchHit::externalOnly).toList();
        if (external.isEmpty()) {
            return answer;
        }
        if (results.size() == 1) {
            return answer + " Tập này hiện chỉ mở được trên YouTube nhé.";
        }
        if (external.size() == results.size()) {
            return answer + " Các tập này hiện chỉ mở được trên YouTube nhé.";
        }
        return answer + " Các tập chỉ mở được trên YouTube gồm "
                + String.join(", ", external.stream().map(CatalogReplies::namedItem).toList()) + ".";
    }

    // Đáp lại các câu chào, cảm ơn hoặc tạm biệt ngắn.
    public static String chitchat(String query) {
        String text = TextFolding.foldNormalized(query);
        if (text.startsWith("cam on") || text.startsWith("thank")) {
            return "Không có gì đâu, chúc bạn và bé xem phim vui vẻ!";
        }
        if (text.contains("tam biet") || text.startsWith("hen gap lai")
                || text.startsWith("goodbye") || text.startsWith("bye") || text.startsWith("see you")) {
            return "Hẹn gặp lại bạn sau nhé!";
        }
        return catalogReply(List.of(), "chitchat");
    }

    // Trả lời câu hỏi nối tiếp về việc một tập thuộc bộ nào hoặc có những nhân vật nào.
    public static String episodeDetails(SearchHit item, String query) {
        String folded = TextFolding.foldNormalized(query);
        List<String> sentences = new ArrayList<>();
        if (EPISODE_SERIES.matcher(folded).find() && item.seriesTitle() != null && !item.seriesTitle().isBlank()) {
            sentences.add("Tập \"" + cleanTitle(resolveTitle(item)) + "\" thuộc bộ \"" + item.seriesTitle() + "\".");
        }
        if (EPISODE_CHARACTERS.matcher(folded).find()) {
            var characters = item.characters();
            sentences.add(characters == null || characters.isEmpty() ? "Nhân vật trong tập này chưa được ghi trong kho phim."
                    : "Nhân vật trong tập gồm " + String.join(", ", characters) + ".");
        }
        return String.join(" ", sentences);
    }

    // Lấy tóm tắt của tập hoặc bộ phim, bỏ qua các chuỗi giữ chỗ chỉ lặp lại tên tập.
    public static String summary(SearchHit item) {
        String synopsis = resolveSynopsis(item, resolveTitle(item));
        if (synopsis == null || synopsis.isBlank() || isEpisodePlaceholder(item, synopsis)) {
            synopsis = evidenceTexts(item).stream().filter(quote -> !isEpisodePlaceholder(item, quote))
                    .findFirst().orElse("Kho phim chưa có phần tóm tắt cho mục này.");
        }
        return "Tóm tắt " + namedItem(item) + ": " + synopsis;
    }

    private static String namedItem(SearchHit item) {
        String kind = "series".equals(item.itemType()) ? "bộ phim" : "tập";
        return kind + " \"" + cleanTitle(resolveTitle(item)) + "\"";
    }

    private static String cleanTitle(String title) {
        String safeTitle = title == null ? "" : title.strip();
        return stripTrailingDots(VIDEO_SUFFIX.matcher(safeTitle).replaceFirst(""));
    }

    // Dựng câu trả lời chính từ kết quả đầu tiên và nối thêm đoạn trích đầu tiên nếu đó không phải mô tả giữ chỗ.
    public static String catalogReply(List<SearchHit> results, String mode) {
        if ("chitchat".equals(mode)) {
            return "Mình là CineBot. Bạn cứ kể lại cảnh phim, nhân vật hoặc tập muốn tìm nhé.";
        }
        if (results == null || results.isEmpty()) {
            return noMatch(List.of());
        }
        SearchHit first = results.getFirst();
        String title = resolveTitle(first);
        String prefix = "series".equals(first.itemType()) ? "Đây là " + namedItem(first) + "."
                : sourceIntro(title, first.effectiveStartMs(), mode);
        List<String> quotes = evidenceTexts(first);
        if (quotes.isEmpty()) {
            return prefix;
        }
        // Bỏ qua các đoạn trích tự sinh lặp tên tập để câu trả lời khỏi bị thừa chữ.
        String quote = quotes.getFirst();
        if (quote.startsWith("Tập phim " + title) && quote.contains("của bộ hoạt hình")) {
            return prefix;
        }
        if (isEpisodePlaceholder(first, quote)) {
            return prefix;
        }
        return prefix + " " + quote;
    }

    private static boolean isEpisodePlaceholder(SearchHit item, String text) {
        return text.equals(CatalogDisplayText.episode(item.episodeNumber(), resolveTitle(item)));
    }

    // Trả lời câu hỏi tổng quan về một bộ phim (số tập, số mùa, nhân vật chính hoặc tóm tắt).
    public static String seriesReply(List<SearchHit> results, String detail) {
        if (results == null || results.isEmpty()) {
            return "Mình chưa tìm thấy bộ phim này trong kho phim.";
        }
        SearchHit series = results.getFirst();
        String title = series.title() == null ? "" : series.title();
        if ("episodes".equals(detail)) {
            return "Kho phim hiện có " + countOrZero(series.episodeCount()) + " tập " + title + ".";
        }
        if ("seasons".equals(detail)) {
            return "Kho phim hiện có " + countOrZero(series.seasonCount()) + " mùa " + title + ".";
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
        return "Kho phim chưa có thông tin này về " + title + ".";
    }

    // Ưu tiên lấy title, thiếu thì rơi về episodeTitle rồi nhãn "Tập phim" mặc định.
    public static String resolveTitle(SearchHit hit) {
        if (hit.title() != null && !hit.title().isEmpty()) {
            return hit.title();
        }
        if (hit.episodeTitle() != null && !hit.episodeTitle().isEmpty()) {
            return hit.episodeTitle();
        }
        return CatalogDisplayText.EPISODE;
    }

    private static int countOrZero(Integer count) {
        return count == null ? 0 : count;
    }

    // Đọc một trường văn bản phụ, ưu tiên diagnostics trước rồi mới tới comparativeAttributes.
    public static String attributeText(SearchHit hit, String key) {
        String fromDiagnostics = readString(hit.diagnostics(), key);
        if (fromDiagnostics != null) {
            return fromDiagnostics;
        }
        return readString(hit.comparativeAttributes(), key);
    }

    private static String readString(Map<String, Object> attributes, String key) {
        if (attributes == null) {
            return null;
        }
        Object value = attributes.get(key);
        return value instanceof String text ? text : null;
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
                && !series.description().equals(CatalogDisplayText.series(title))) {
            return series.description();
        }
        return null;
    }

    private static String stripTrailingDots(String title) {
        int end = title.length();
        while (end > 0 && title.charAt(end - 1) == '.') {
            end--;
        }
        return title.substring(0, end);
    }
}
