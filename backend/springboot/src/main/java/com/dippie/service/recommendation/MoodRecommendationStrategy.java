package com.dippie.service.recommendation;

import com.dippie.dto.recommendation.RecommendationResult;
import com.dippie.dto.recommendation.RecommendedItem;
import com.dippie.exception.ServiceUnavailableException;
import com.dippie.model.assistant.SearchConstraints;
import com.dippie.model.assistant.SearchHit;
import com.dippie.model.catalog.EpisodeMood;
import com.dippie.model.catalog.EpisodeRuntime;
import com.dippie.model.recommendation.MoodPreset;
import com.dippie.model.recommendation.RecommendationCandidate;
import com.dippie.service.assistant.CatalogSearch;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

// Đây là một Strategy, RecommendationService chọn nó khi request có moodPreset hoặc mô tả mood tự do.
// Việt nhớ giữ nhánh rơi về AffinityRecommendationStrategy bên dưới: lúc chỉ mục tìm kiếm chưa sẵn sàng hoặc không khớp tập nào thì kệ phim vẫn có gợi ý, không bị trắng trơn.
@Component
public class MoodRecommendationStrategy implements RecommendationStrategy {

    private static final int SEARCH_LIMIT = 100;
    private static final double DISLIKED_SERIES_PENALTY = 2.0;

    private final CatalogSearch search;
    private final AffinityRecommendationStrategy affinity;

    public MoodRecommendationStrategy(CatalogSearch search, AffinityRecommendationStrategy affinity) {
        this.search = search;
        this.affinity = affinity;
    }

    // Nếu người xem gõ câu tự do mang ý đi ngủ hoặc xem nhanh thì chuyển thẳng sang preset tương ứng trước khi phải gọi tìm kiếm.
    @Override
    public RecommendationResult recommend(RecommendationContext context) {
        if (context.request().moodPreset() != null) {
            return recommendPreset(context, MoodPreset.require(context.request().moodPreset()));
        }
        String query = context.request().mood();
        if (EpisodeMood.bedtimeRequest(query)) {
            return recommendPreset(context, MoodPreset.BEDTIME);
        }
        if (EpisodeMood.shortRequest(query)) {
            return recommendPreset(context, MoodPreset.SHORT);
        }
        return recommendText(context, query);
    }

    private RecommendationResult recommendPreset(RecommendationContext context, MoodPreset preset) {
        // Lọc theo trần thời lượng của preset, chấm điểm tâm trạng và trừ điểm các bộ người xem không thích.
        var disliked = context.dislikedSeries();
        boolean capSeries = preset == MoodPreset.BEDTIME;
        var ranked = context.candidates(context.explicitGenres()).stream().filter(preset::withinRuntime)
                .map(item -> {
                    double baseScore = preset.score(item);
                    double effective = baseScore - (disliked.contains(item.seriesId()) ? DISLIKED_SERIES_PENALTY : 0.0);
                    return new PresetMatch(item, baseScore, effective, item.features().knownDurationMs());
                })
                .filter(match -> match.score() > 0)
                .sorted(Comparator.comparingDouble(PresetMatch::effectiveScore).reversed()
                        .thenComparing(PresetMatch::durationMs, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(match -> match.item().id()))
                .toList();

        // Không có tập nào khớp preset thì lùi về gợi ý theo sở thích nhưng vẫn giữ nguyên bộ lọc thời lượng.
        if (ranked.isEmpty()) {
            return affinity.recommend(context.withFilter(preset::withinRuntime, capSeries));
        }
        var items = RecommendationSelection.select(ranked, match -> match.item().seriesId(), context, capSeries).stream()
                .map(match -> RecommendedItem.affinity(match.item(), match.score())).toList();
        return new RecommendationResult(items, "Phim hợp với tâm trạng bạn chọn.");
    }

    private RecommendationResult recommendText(RecommendationContext context, String query) {
        // Nếu chỉ mục tìm kiếm chưa sẵn sàng hoặc lỗi tạm thời thì chuyển sang gợi ý theo sở thích để kệ không bị rỗng.
        var explicitGenres = context.explicitGenres();
        boolean forChildren = EpisodeMood.childRequest(query);
        Predicate<RecommendationCandidate> eligible = item -> MoodPreset.suitableForMood(item)
                && (!forChildren || EpisodeRuntime.atMost(item.durationMs(), EpisodeRuntime.HOUR_MS));
        List<SearchHit> hits;
        try {
            if (!search.isReady()) {
                return affinity.recommend(context.withFilter(eligible, false));
            }
            var constraints = new SearchConstraints(explicitGenres.stream().sorted().toList(), List.of(), null,
                    null, null, null, false);
            hits = search.search(query, SEARCH_LIMIT, constraints);
        } catch (ServiceUnavailableException exception) {
            return affinity.recommend(context.withFilter(eligible, false));
        }

        // Giữ nguyên thứ tự khớp của search, loại bản trùng id bằng remove(), rồi đẩy các bộ bị chê xuống cuối thay vì xóa hẳn.
        Map<String, RecommendationCandidate> candidates = new HashMap<>();
        context.candidates(explicitGenres).stream().filter(eligible).forEach(item -> candidates.put(item.id(), item));
        var disliked = context.dislikedSeries();
        List<RecommendedItem> preferred = new ArrayList<>();
        List<RecommendedItem> deprioritized = new ArrayList<>();
        for (SearchHit hit : hits) {
            RecommendationCandidate item = candidates.remove(hit.id());
            if (item != null) {
                Double score = hit.score();
                var evidence = hit.evidence().stream().map(entry -> new RecommendedItem.Evidence(entry.documentId(),
                        entry.text(), entry.startMs(), entry.endMs(), entry.itemType())).toList();
                RecommendedItem recommended = RecommendedItem.from(
                        item, score != null && Double.isFinite(score) ? score : null, evidence);
                (disliked.contains(recommended.seriesId()) ? deprioritized : preferred).add(recommended);
            }
        }
        preferred.addAll(deprioritized);

        // Tìm xong không còn tập hợp lệ nào thì cũng lùi về AffinityRecommendationStrategy.
        if (preferred.isEmpty()) {
            return affinity.recommend(context.withFilter(eligible, false));
        }
        return new RecommendationResult(RecommendationSelection.select(preferred, RecommendedItem::seriesId, context),
                "Phim hợp với tâm trạng bạn chọn.");
    }

    // Lưu tập khớp preset cùng điểm gốc (trả ra API) và điểm sau trừ phạt (dùng để xếp hạng).
    private record PresetMatch(RecommendationCandidate item, double score, double effectiveScore, Long durationMs) {
    }
}
