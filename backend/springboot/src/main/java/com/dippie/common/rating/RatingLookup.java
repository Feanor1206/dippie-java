package com.dippie.common.rating;

import jakarta.persistence.criteria.Expression;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaCriteriaQuery;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

// interface tra điểm đánh giá đặt ở common để module phim và catalog dùng mà không phụ thuộc ngược vào library
public interface RatingLookup {

    // tra hàng loạt tóm tắt đánh giá theo danh sách mã phim cho khỏi dính truy vấn N+1
    Map<String, RatingSummary> byId(List<String> ids);

    default RatingSummary forItem(String id) {
        return byId(List.of(id)).getOrDefault(id, RatingSummary.EMPTY);
    }

    // tải điểm đánh giá 1 lượt cho cả danh sách rồi ghép sang view
    default <T, R> List<R> withSummaries(List<T> items, Function<T, String> id,
            BiFunction<T, RatingSummary, R> toView) {
        if (items.isEmpty()) {
            return List.of();
        }
        var summaries = byId(items.stream().map(id).distinct().toList());
        return items.stream()
                .map(item -> toView.apply(item, summaries.getOrDefault(id.apply(item), RatingSummary.EMPTY)))
                .toList();
    }

    // tạo subquery tính điểm trung bình dùng thẳng trong Hibernate Criteria (như lúc sắp xếp theo điểm)
    Expression<Double> averageSubquery(HibernateCriteriaBuilder builder, JpaCriteriaQuery<?> query,
            Expression<String> itemId);
}
