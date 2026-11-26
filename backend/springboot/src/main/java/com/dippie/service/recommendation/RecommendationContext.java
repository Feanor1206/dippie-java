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

// Gói toàn bộ dữ liệu đầu vào cho một lượt chạy Strategy: request, danh mục ứng viên, tín hiệu xem và bộ lọc kèm theo.
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

    // Dùng khi MoodRecommendationStrategy rơi về AffinityRecommendationStrategy mà vẫn phải giữ bộ lọc thời lượng và cờ chặn lặp bộ phim.
    public RecommendationContext withFilter(Predicate<RecommendationCandidate> filter, boolean cap) {
        return new RecommendationContext(request, catalog, signals, filter, cap);
    }

    public Set<String> explicitGenres() {
        return request.genres().stream().map(UnicodeText::caseFold).collect(Collectors.toSet());
    }

    // Bỏ ngay các tập đã xem, rồi lọc theo bộ phim và thể loại nếu người dùng có chọn.
    public List<RecommendationCandidate> candidates(Set<String> explicitGenres) {
        return catalog.stream()
                .filter(item -> !signals.seen().contains(item.id()))
                .filter(item -> request.seriesId() == null || request.seriesId().equals(item.seriesId()))
                .filter(item -> explicitGenres.isEmpty()
                        || !Collections.disjoint(explicitGenres, item.features().genreTags()))
                .toList();
    }

    // Suy ngược ra tập các seriesId mà người xem từng đánh giá không thích.
    public Set<String> dislikedSeries() {
        return catalog.stream().filter(item -> item.seriesId() != null && item.matchesPreference(signals.disliked()))
                .map(RecommendationCandidate::seriesId).collect(Collectors.toSet());
    }
}
