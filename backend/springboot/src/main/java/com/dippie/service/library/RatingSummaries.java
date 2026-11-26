package com.dippie.service.library;

import com.dippie.common.rating.RatingLookup;
import com.dippie.common.rating.RatingSummary;
import com.dippie.model.library.MovieRating;
import com.dippie.repository.library.MovieRatingRepository;

import jakarta.persistence.criteria.Expression;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaCriteriaQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

// Adapter cài RatingLookup để các gói khác như movie hay catalog tra điểm cộng đồng mà không phụ thuộc vào gói library.
@Service
public class RatingSummaries implements RatingLookup {

    private static final int CHUNK_SIZE = 500;

    private final MovieRatingRepository ratings;

    public RatingSummaries(MovieRatingRepository ratings) {
        this.ratings = ratings;
    }

    @Override
    @Transactional(readOnly = true)
    public <T, R> List<R> withSummaries(List<T> items, Function<T, String> id,
            BiFunction<T, RatingSummary, R> toView) {
        return RatingLookup.super.withSummaries(items, id, toView);
    }

    @Override
    @Transactional(readOnly = true)
    public RatingSummary forItem(String id) {
        return RatingLookup.super.forItem(id);
    }

    // Việt đừng tăng CHUNK_SIZE lên quá cao nhé, mình chia lô 500 ID sau khi lọc trùng để tránh câu SQL IN quá dài trên mấy trang nhiều phim.
    @Override
    @Transactional(readOnly = true)
    public Map<String, RatingSummary> byId(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<String> uniqueIds = ids.stream().distinct().toList();
        if (uniqueIds.size() <= CHUNK_SIZE) {
            return ratings.summarize(uniqueIds).stream().collect(Collectors.toMap(MovieRatingRepository.RatingRow::id,
                    row -> RatingSummary.from(row.average(), row.count())));
        }
        Map<String, RatingSummary> merged = new java.util.HashMap<>(uniqueIds.size());
        for (int start = 0; start < uniqueIds.size(); start += CHUNK_SIZE) {
            List<String> batch = uniqueIds.subList(start, Math.min(start + CHUNK_SIZE, uniqueIds.size()));
            for (MovieRatingRepository.RatingRow row : ratings.summarize(batch)) {
                merged.put(row.id(), RatingSummary.from(row.average(), row.count()));
            }
        }
        return merged;
    }

    // Dựng subquery tính điểm trung bình bằng Criteria API để MovieQueryService sắp xếp theo điểm cộng đồng ngay trong SQL.
    @Override
    public Expression<Double> averageSubquery(HibernateCriteriaBuilder builder, JpaCriteriaQuery<?> query,
            Expression<String> itemId) {
        var average = query.subquery(Double.class);
        var rating = average.from(MovieRating.class);
        return average.select(builder.avg(rating.get("rating"))).where(builder.equal(rating.get("movieId"), itemId));
    }
}
