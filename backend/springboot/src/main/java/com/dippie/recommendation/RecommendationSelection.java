package com.dippie.recommendation;

import com.dippie.common.collection.DiverseSelection;

import java.util.List;
import java.util.function.Function;

// Chọn tập phim từ danh sách đã chấm điểm qua DiverseSelection để một bộ phim không chiếm hết cả kệ gợi ý.
final class RecommendationSelection {

    private static final int SERIES_CAP_DIVISOR = 3;

    private RecommendationSelection() {
    }

    static <T> List<T> select(List<T> ranked, Function<T, String> series, RecommendationContext context) {
        return select(ranked, series, context, context.capSeries());
    }

    // Người xem đã lọc riêng một bộ thì lấy thẳng theo hạng; có cá nhân hóa hoặc bật capSeries thì chặn tối đa ceil(limit / 3) tập mỗi bộ, còn lại chia vòng tròn giữa các bộ.
    static <T> List<T> select(List<T> ranked, Function<T, String> series, RecommendationContext context,
            boolean capSeries) {
        int limit = context.request().limit();
        if (context.request().seriesId() != null) {
            return ranked.stream().limit(limit).toList();
        }
        return capSeries || context.signals().personalized()
                ? DiverseSelection.capped(ranked, series, limit, Math.ceilDiv(limit, SERIES_CAP_DIVISOR))
                : DiverseSelection.roundRobin(ranked, series, limit);
    }
}
