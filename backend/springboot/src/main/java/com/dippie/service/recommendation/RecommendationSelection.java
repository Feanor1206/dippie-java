package com.dippie.service.recommendation;

import com.dippie.common.collection.DiverseSelection;

import java.util.List;
import java.util.function.Function;

// lọc tập từ list đã chấm điểm qua DiverseSelection kẻo 1 bộ chiếm sạch cả kệ gợi ý
public final class RecommendationSelection {

    private RecommendationSelection() {
    }

    public static <T> List<T> select(List<T> ranked, Function<T, String> series, RecommendationContext context) {
        return select(ranked, series, context, context.capSeries());
    }

    // lọc 1 bộ thì lấy theo hạng, có cá nhân hóa hay capSeries thì chặn trần ceil(limit / 3) tập mỗi bộ, còn lại chia vòng tròn
    public static <T> List<T> select(List<T> ranked, Function<T, String> series, RecommendationContext context,
            boolean capSeries) {
        int limit = context.request().limit();
        if (context.request().seriesId() != null) {
            return ranked.stream().limit(limit).toList();
        }
        return capSeries || context.signals().personalized()
                ? DiverseSelection.capped(ranked, series, limit, Math.ceilDiv(limit, 3))
                : DiverseSelection.roundRobin(ranked, series, limit);
    }
}
