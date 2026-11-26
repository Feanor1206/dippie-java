package com.dippie.service.assistant;

import com.dippie.common.collection.DiverseSelection;
import com.dippie.common.text.TextFolding;
import com.dippie.model.assistant.ChatHistoryMessage;
import com.dippie.model.assistant.ChatIntent;
import com.dippie.model.assistant.SearchHit;
import com.dippie.model.catalog.EpisodeMood;
import com.dippie.model.catalog.EpisodeRuntime;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

// Lo phần tìm phim cho CineBot: bỏ qua chào hỏi và phép tính, giải câu hỏi nối tiếp, xếp hạng tâm trạng và chia đều nhiều bộ phim.
public final class ChatRetrieval {

    private static final int DISCOVERY_LIMIT = 100;
    private static final int LONG_EPISODE_MINUTES = 90;
    private static final int SHORT_EPISODE_MINUTES = 10;
    private static final Pattern GENERAL_MOOD = Pattern.compile(
            "(?:hom nay )?(?:(?:toi|minh) )?(?:dang |thay |bi |can |muon )?"
                    + "(?:(?:stress|cang thang|giai tri|thu gian)\\b[ ]*)+(?:qua|mot chut)?[.!? ]*");
    private static final Pattern MINUTES = Pattern.compile("(\\d{1,6})m");

    private final CatalogSearch catalog;

    public ChatRetrieval(CatalogSearch catalog) {
        this.catalog = catalog;
    }

    // Chọn danh sách phim khớp ý định và kiểm tra xem kho phim còn tập thay thế cho nút "tập khác" hay không.
    public Selection find(ChatIntent intent, String query, List<ChatHistoryMessage> history,
            Optional<ChatFollowUp> followUp, boolean asksForAlternate) {
        // Không gọi tìm kiếm khi người dùng chỉ chào hỏi hoặc hỏi phép tính số học ngoài phạm vi phim.
        if ("chitchat".equals(intent.mode()) || IntentParser.asksForArithmetic(query)) {
            return new Selection(List.of(), false);
        }
        if (followUp.isPresent()) {
            return new Selection(followUp.orElseThrow().resolve(history, catalog), false);
        }
        var shown = ChatFollowUp.shownItems(history);
        var excluded = asksForAlternate ? shown : Set.<String>of();
        if (intent.similarTo() != null) {
            var similar = catalog.similarSeries(intent.similarTo(), intent.requestedK() + excluded.size()).stream()
                    .filter(hit -> !excluded.contains(hit.id())).limit(intent.requestedK()).toList();
            return new Selection(similar, false);
        }

        // Nếu câu hỏi chỉ than mệt/stress chung mà không khớp từ nào, mình tìm dự phòng bằng từ khóa "hài hước".
        var constraints = intent.toSearchConstraints(excluded);
        int limit = constraints.discovery() ? DISCOVERY_LIMIT : intent.requestedK();
        String searchQuery = intent.semanticQuery();
        var hits = catalog.search(searchQuery, limit, constraints);
        if (hits == null) {
            return new Selection(List.of(), false);
        }
        if (hits.isEmpty() && intent.moodRecommendation()
                && GENERAL_MOOD.matcher(TextFolding.foldNormalized(searchQuery)).matches()) {
            hits = catalog.search("hài hước", limit, constraints);
        }
        if (!constraints.discovery()) {
            return new Selection(List.copyOf(hits), false);
        }
        // Ở chế độ khám phá, xếp lại theo điểm tâm trạng (trừ yêu cầu trước khi ngủ đã xếp riêng) rồi xoay vòng theo bộ phim cho đa dạng.
        var ranked = intent.moodRecommendation() && !EpisodeMood.bedtimeRequest(searchQuery)
                ? rankMood(hits, intent, constraints.preferShort()) : hits;
        var selected = DiverseSelection.roundRobin(ranked, hit -> Objects.requireNonNullElse(hit.seriesId(), hit.id()),
                intent.requestedK());
        return new Selection(selected, hasAlternatives(ranked, selected, shown));
    }

    private static boolean hasAlternatives(List<SearchHit> ranked, List<SearchHit> selected, Set<String> shown) {
        if (selected.size() < 2) {
            return false;
        }
        var used = new HashSet<>(shown);
        selected.forEach(hit -> used.add(hit.id()));
        return ranked.stream().anyMatch(hit -> "episode".equals(hit.itemType()) && !hit.externalOnly()
                && !used.contains(hit.id()));
    }

    // Đẩy tập quá dài (trên 90 phút) hoặc vượt ngưỡng ngắn (10 phút) xuống dưới rồi mới so điểm tâm trạng.
    private static List<SearchHit> rankMood(List<SearchHit> hits, ChatIntent intent, boolean preferShort) {
        boolean episodes = intent.asksForEpisodes();
        var ranked = hits.stream().filter(item -> !episodes || !"series".equals(item.itemType()))
                .map(item -> new MoodHit(item, MoodEvidence.score(item, intent.moodTarget()),
                        episodes && overMinutes(item, LONG_EPISODE_MINUTES),
                        preferShort && overMinutes(item, SHORT_EPISODE_MINUTES)))
                .filter(item -> item.score() > 0)
                .sorted(Comparator.comparing(MoodHit::overLong)
                        .thenComparing(MoodHit::overShort)
                        .thenComparing(Comparator.comparingInt(MoodHit::score).reversed()))
                .map(MoodHit::hit).toList();
        return ranked.isEmpty() ? hits : ranked;
    }

    private static boolean overMinutes(SearchHit item, int minutes) {
        if (item.durationMs() != null) {
            return item.durationMs() > minutes * EpisodeRuntime.MINUTE_MS;
        }
        var matcher = MINUTES.matcher(item.runtime() == null ? "" : item.runtime());
        return matcher.matches() && Integer.parseInt(matcher.group(1)) > minutes;
    }

    // Bản ghi tạm gắn điểm tâm trạng và cờ vượt thời lượng phục vụ sắp xếp.
    private record MoodHit(SearchHit hit, int score, boolean overLong, boolean overShort) {
    }

    // Danh sách phim đã chọn kèm cờ báo còn tập phát nội bộ chưa gợi ý hay không.
    public record Selection(List<SearchHit> hits, boolean hasAlternatives) {
    }
}
