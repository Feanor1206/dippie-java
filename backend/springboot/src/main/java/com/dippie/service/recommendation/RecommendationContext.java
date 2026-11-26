package com.dippie.service.recommendation;

import com.dippie.common.text.UnicodeText;
import com.dippie.dto.recommendation.RecommendationRequest;
import com.dippie.model.recommendation.RecommendationCandidate;
import com.dippie.model.recommendation.ViewerSignals;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

// gói hết đầu vào cho 1 lượt chạy Strategy gồm request, kho ứng viên, tín hiệu xem với bộ lọc đi kèm
public record RecommendationContext(
        RecommendationRequest request,
        List<RecommendationCandidate> catalog,
        ViewerSignals signals,
        Predicate<RecommendationCandidate> eligibility,
        boolean capSeries
) {
    public RecommendationContext(RecommendationRequest request, List<RecommendationCandidate> catalog, ViewerSignals signals) {
        this(request, catalog, signals, item -> true, false);
    }

    // dùng lúc MoodRecommendationStrategy rơi về AffinityRecommendationStrategy mà vẫn phải giữ bộ lọc thời lượng với cờ chặn lặp bộ
    public RecommendationContext withFilter(Predicate<RecommendationCandidate> filter, boolean cap) {
        return new RecommendationContext(request, catalog, signals, filter, cap);
    }

    public Set<String> explicitGenres() {
        return request.genres().stream().map(UnicodeText::caseFold).collect(Collectors.toSet());
    }

    // bỏ mấy tập đã xem rồi lọc theo bộ với thể loại nếu user có chọn
    public List<RecommendationCandidate> candidates(Set<String> explicitGenres) {
        return catalog.stream()
                .filter(item -> !signals.seen().contains(item.id()))
                .filter(item -> request.seriesId() == null || request.seriesId().equals(item.seriesId()))
                .filter(item -> explicitGenres.isEmpty()
                        || !Collections.disjoint(explicitGenres, item.features().genreTags()))
                .toList();
    }

    // suy ngược ra tập seriesId của mấy phim user từng chê
    public Set<String> dislikedSeries() {
        return catalog.stream().filter(item -> item.seriesId() != null && item.matchesPreference(signals.disliked()))
                .map(RecommendationCandidate::seriesId).collect(Collectors.toSet());
    }
}
