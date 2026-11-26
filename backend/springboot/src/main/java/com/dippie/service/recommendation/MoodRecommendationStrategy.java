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

// nhánh Strategy chạy khi request có moodPreset hoặc câu mô tả mood tự do
// Việt giữ nhánh rơi về AffinityRecommendationStrategy nha, lỡ index chưa sẵn hay không khớp tập nào thì kệ vẫn có phim
@Component
public class MoodRecommendationStrategy implements RecommendationStrategy {

    private final CatalogSearch search;
    private final AffinityRecommendationStrategy affinity;

    public MoodRecommendationStrategy(CatalogSearch search, AffinityRecommendationStrategy affinity) {
        this.search = search;
        this.affinity = affinity;
    }

    // user gõ câu tự do kiểu đi ngủ hay xem nhanh thì bẻ thẳng qua preset tương ứng cho lẹ, khỏi gọi search
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
        // lọc theo trần thời lượng của preset, chấm điểm mood rồi trừ điểm mấy bộ user không thích
        var disliked = context.dislikedSeries();
        boolean capSeries = preset == MoodPreset.BEDTIME;
        var ranked = context.candidates(context.explicitGenres()).stream().filter(preset::withinRuntime)
                .map(item -> {
                    double baseScore = preset.score(item);
                    double effective = baseScore - (disliked.contains(item.seriesId()) ? 2.0 : 0.0);
                    return new PresetMatch(item, baseScore, effective, item.features().knownDurationMs());
                })
                .filter(match -> match.score() > 0)
                .sorted(Comparator.comparingDouble(PresetMatch::effectiveScore).reversed()
                        .thenComparing(PresetMatch::durationMs, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(match -> match.item().id()))
                .toList();

        // không khớp tập nào thì lùi về gợi ý theo gu nhưng vẫn giữ nguyên bộ lọc thời lượng
        if (ranked.isEmpty()) {
            return affinity.recommend(context.withFilter(preset::withinRuntime, capSeries));
        }
        var items = RecommendationSelection.select(ranked, match -> match.item().seriesId(), context, capSeries).stream()
                .map(match -> RecommendedItem.affinity(match.item(), match.score())).toList();
        return new RecommendationResult(items, "Phim hợp với tâm trạng bạn chọn.");
    }

    private RecommendationResult recommendText(RecommendationContext context, String query) {
        // index chưa sẵn hay đang lỗi tạm thì lùi sang gợi ý theo gu cho kệ khỏi rỗng
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
            hits = search.search(query, 100, constraints);
        } catch (ServiceUnavailableException exception) {
            return affinity.recommend(context.withFilter(eligible, false));
        }

        // giữ nguyên thứ tự kết quả search, chống trùng id bằng remove(), rồi đẩy mấy bộ bị chê xuống cuối chứ không xóa hẳn
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

        // tìm xong không còn tập nào hợp lệ thì cũng lùi về AffinityRecommendationStrategy
        if (preferred.isEmpty()) {
            return affinity.recommend(context.withFilter(eligible, false));
        }
        return new RecommendationResult(RecommendationSelection.select(preferred, RecommendedItem::seriesId, context),
                "Phim hợp với tâm trạng bạn chọn.");
    }

    // gói tập khớp preset kèm điểm gốc (trả API) và điểm sau trừ phạt (dùng xếp hạng)
    private record PresetMatch(RecommendationCandidate item, double score, double effectiveScore, Long durationMs) {
    }
}
