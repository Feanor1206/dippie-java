package com.dippie.common.rating;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.math.RoundingMode;

// điểm trung bình và tổng lượt đánh giá của 1 phim, luôn xuất cả field null ra JSON cho frontend đọc ổn định
@JsonInclude(JsonInclude.Include.ALWAYS)
public record RatingSummary(BigDecimal averageRating, long ratingCount) {

    public static final RatingSummary EMPTY = new RatingSummary(null, 0);

    // làm tròn điểm trung bình 1 chữ số thập phân kiểu HALF_UP
    public static RatingSummary from(double average, long count) {
        return new RatingSummary(BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP), count);
    }
}
