package com.dippie.common.rating;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.math.RoundingMode;

// Điểm đánh giá trung bình và tổng số lượt đánh giá của một phim; luôn xuất cả trường null ra JSON để frontend đọc ổn định.
@JsonInclude(JsonInclude.Include.ALWAYS)
public record RatingSummary(BigDecimal averageRating, long ratingCount) {

    public static final RatingSummary EMPTY = new RatingSummary(null, 0);

    // Làm tròn điểm trung bình 1 chữ số thập phân theo quy tắc HALF_UP.
    public static RatingSummary from(double average, long count) {
        return new RatingSummary(BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP), count);
    }
}
