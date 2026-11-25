package com.dippie.common.rating;

import jakarta.persistence.criteria.Expression;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaCriteriaQuery;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

// Giao diện tra cứu điểm đánh giá đặt ở gói common để các module phim và catalog dùng được mà không phụ thuộc ngược vào gói library.
public interface RatingLookup {

    // Tra hàng loạt tóm tắt đánh giá theo danh sách mã phim để tránh truy vấn N+1.
    Map<String, RatingSummary> byId(List<String> ids);

    default RatingSummary forItem(String id) {
        return byId(List.of(id)).getOrDefault(id, RatingSummary.EMPTY);
    }

    // Tải điểm đánh giá một lượt cho cả danh sách rồi ghép sang đối tượng view tương ứng.
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

    // Tạo subquery tính điểm trung bình dùng trực tiếp trong Hibernate Criteria (ví dụ khi sắp xếp theo điểm).
    Expression<Double> averageSubquery(HibernateCriteriaBuilder builder, JpaCriteriaQuery<?> query,
            Expression<String> itemId);
}
