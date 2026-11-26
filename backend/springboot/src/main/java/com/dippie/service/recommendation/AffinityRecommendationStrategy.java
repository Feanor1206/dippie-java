package com.dippie.service.recommendation;

import com.dippie.dto.recommendation.RecommendationResult;
import com.dippie.dto.recommendation.RecommendedItem;
import com.dippie.model.recommendation.RecommendationCandidate;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

// nhánh Strategy gợi ý theo gu, chấm điểm tập phim dựa trên thể loại chọn sẵn với lịch sử thích, xem, chấm điểm
// RecommendationService gọi nhánh này lúc không truyền mood, hoặc làm phương án dự phòng khi MoodRecommendationStrategy tìm không ra
@Component
public class AffinityRecommendationStrategy implements RecommendationStrategy {

    private static final Comparator<Integer> EPISODE_ORDER = Comparator.nullsLast(Comparator.naturalOrder());
    private static final Comparator<Scored> SCORE_ORDER = Comparator.comparingDouble(Scored::score).reversed()
            .thenComparing(Comparator.comparingDouble(Scored::rating).reversed());
    // người mới chưa có lịch sử thì ưu tiên tập mùa đầu, tóm tắt rõ và số tập nhỏ cho dễ xem
    private static final Comparator<Scored> INTRODUCTION_ORDER = Comparator
            .comparing((Scored item) -> item.item().seasonNumber(), EPISODE_ORDER)
            .thenComparing(Comparator.comparingInt((Scored item) -> item.item().summaryQuality()).reversed())
            .thenComparing(item -> item.item().episodeNumber(), EPISODE_ORDER);

    // dựng hồ sơ thích với không thích từ danh mục, chấm điểm mấy tập chưa xem rồi lọc đa dạng theo bộ
    @Override
    public RecommendationResult recommend(RecommendationContext context) {
        // gom đặc trưng từ thể loại user chọn sẵn với mấy tập họ từng thích hoặc xem rồi
        Set<String> explicitGenres = context.explicitGenres();
        Preferences preferences = new Preferences(new HashSet<>(explicitGenres), new HashSet<>(), new HashSet<>(), new HashSet<>());
        var liked = context.catalog().stream().filter(item -> item.matchesPreference(context.signals().liked())).toList();
        liked.forEach(preferences::add);

        // gom đặc trưng mấy phim bị chấm thấp để trừ điểm những tập na ná
        Preferences disliked = new Preferences(new HashSet<>(), new HashSet<>(), new HashSet<>(), new HashSet<>());
        context.catalog().stream().filter(item -> item.matchesPreference(context.signals().disliked()))
                .forEach(disliked::add);

        // chấm điểm, xếp hạng rồi đưa qua RecommendationSelection kẻo 1 bộ chiếm hết kệ
        var order = context.signals().personalized() ? SCORE_ORDER : SCORE_ORDER.thenComparing(INTRODUCTION_ORDER);
        var ranked = context.candidates(explicitGenres).stream().filter(context.eligibility())
                .map(item -> new Scored(item, round(preferences.score(item) - disliked.penalty(item))))
                .sorted(order.thenComparing(scored -> scored.item().id())).toList();
        var movies = RecommendationSelection.select(ranked, scored -> scored.item().seriesId(), context).stream()
                .map(scored -> RecommendedItem.affinity(scored.item(), scored.score()))
                .toList();

        // chọn câu giải thích khớp với nguồn tín hiệu chính tạo ra list này
        if (!liked.isEmpty()) {
            return new RecommendationResult(movies, "Phim gần gu với những gì bạn đã thích hoặc đã xem.");
        }
        if (!context.signals().disliked().isEmpty()) {
            return new RecommendationResult(movies, "Gợi ý theo các phim bạn đã chấm điểm.");
        }
        String reasoning = explicitGenres.isEmpty()
                ? "Một vài gợi ý từ kho phim cho bạn."
                : "Phim hợp với thể loại bạn đã chọn.";
        return new RecommendationResult(movies, reasoning);
    }

    // làm tròn HALF_EVEN 4 số lẻ bằng BigDecimal(double) cho khớp y hệt bản Python cũ
    private static double round(double score) {
        return new BigDecimal(score).setScale(4, RoundingMode.HALF_EVEN).doubleValue();
    }

    // gom tag thể loại, chủ đề, nhân vật với bộ phim để tính điểm cộng hoặc trừ
    private record Preferences(Set<String> genres, Set<String> themes, Set<String> characters, Set<String> series) {
        void add(RecommendationCandidate item) {
            var features = item.features();
            genres.addAll(features.genreTags());
            themes.addAll(features.themeTags());
            characters.addAll(features.characterTags());
            if (item.seriesId() != null && !item.seriesId().isEmpty()) {
                series.add(item.seriesId());
            }
        }

        double score(RecommendationCandidate item) {
            var features = item.features();
            double score = item.rating() * 0.1;
            score += overlap(2.0, features.genreTags(), genres);
            score += overlap(1.5, features.themeTags(), themes);
            score += overlap(1.2, features.characterTags(), characters);
            score += series.contains(item.seriesId()) ? 0.8 : 0;
            return score;
        }

        double penalty(RecommendationCandidate item) {
            var features = item.features();
            return (series.contains(item.seriesId()) ? 2.0 : 0.0)
                    + overlap(0.6, features.themeTags(), themes)
                    + overlap(0.5, features.characterTags(), characters);
        }

        private static double overlap(double weight, Set<String> itemTags, Set<String> preferredTags) {
            long matched = itemTags.stream().filter(preferredTags::contains).count();
            return weight * matched / Math.max(1, preferredTags.size());
        }
    }

    // cặp ứng viên với điểm đã làm tròn dùng lúc sắp hạng
    private record Scored(RecommendationCandidate item, double score) {
        double rating() {
            return item.rating();
        }
    }
}
