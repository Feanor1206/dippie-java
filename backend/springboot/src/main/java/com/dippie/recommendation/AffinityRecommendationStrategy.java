package com.dippie.recommendation;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

// Một Strategy của tầng gợi ý: chấm điểm tập phim theo thể loại đã chọn và lịch sử thích, xem, chấm điểm của người xem.
// RecommendationService chọn nhánh này khi request không truyền mood, hoặc khi MoodRecommendationStrategy không tìm ra kết quả để làm phương án dự phòng.
@Component
public class AffinityRecommendationStrategy implements RecommendationStrategy {

    private static final double RATING_WEIGHT = 0.1;
    private static final double GENRE_WEIGHT = 2.0;
    private static final double THEME_WEIGHT = 1.5;
    private static final double CHARACTER_WEIGHT = 1.2;
    private static final double SERIES_BONUS = 0.8;
    private static final double DISLIKED_SERIES_PENALTY = 2.0;
    private static final double DISLIKED_THEME_PENALTY = 0.6;
    private static final double DISLIKED_CHARACTER_PENALTY = 0.5;
    private static final int SCORE_SCALE = 4;

    private static final Comparator<Integer> EPISODE_ORDER = Comparator.nullsLast(Comparator.naturalOrder());
    private static final Comparator<Scored> SCORE_ORDER = Comparator.comparingDouble(Scored::score).reversed()
            .thenComparing(Comparator.comparingDouble(Scored::rating).reversed());
    // Người mới chưa có lịch sử thì ưu tiên tập ở mùa đầu, tóm tắt rõ ràng và số tập nhỏ để dễ bắt đầu xem.
    private static final Comparator<Scored> INTRODUCTION_ORDER = Comparator
            .comparing((Scored item) -> item.item().seasonNumber(), EPISODE_ORDER)
            .thenComparing(Comparator.comparingInt((Scored item) -> item.item().summaryQuality()).reversed())
            .thenComparing(item -> item.item().episodeNumber(), EPISODE_ORDER);

    // Dựng hồ sơ thích và không thích từ danh mục, chấm điểm các tập chưa xem rồi lọc đa dạng theo bộ phim.
    @Override
    public RecommendationResult recommend(RecommendationContext context) {
        // Gom đặc trưng từ thể loại người dùng chọn sẵn và các tập họ từng thích hoặc đã xem.
        Set<String> explicitGenres = context.explicitGenres();
        Preferences preferences = new Preferences(new HashSet<>(explicitGenres), new HashSet<>(), new HashSet<>(), new HashSet<>());
        var liked = context.catalog().stream().filter(item -> item.matchesPreference(context.signals().liked())).toList();
        liked.forEach(preferences::add);

        // Thu thêm đặc trưng từ các phim người xem chấm thấp để trừ điểm những tập cùng gu đó.
        Preferences disliked = new Preferences(new HashSet<>(), new HashSet<>(), new HashSet<>(), new HashSet<>());
        context.catalog().stream().filter(item -> item.matchesPreference(context.signals().disliked()))
                .forEach(disliked::add);

        // Chấm điểm, sắp hạng và đưa qua RecommendationSelection để không bị trùng quá nhiều tập cùng một bộ.
        var order = context.signals().personalized() ? SCORE_ORDER : SCORE_ORDER.thenComparing(INTRODUCTION_ORDER);
        var ranked = context.candidates(explicitGenres).stream().filter(context.eligibility())
                .map(item -> new Scored(item, round(preferences.score(item) - disliked.penalty(item))))
                .sorted(order.thenComparing(scored -> scored.item().id())).toList();
        var movies = RecommendationSelection.select(ranked, scored -> scored.item().seriesId(), context).stream()
                .map(scored -> RecommendedItem.affinity(scored.item(), scored.score()))
                .toList();

        // Chọn câu giải thích tương ứng với nguồn tín hiệu chính tạo ra danh sách này.
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

    // Làm tròn HALF_EVEN 4 chữ số thập phân bằng BigDecimal(double) để khớp kết quả của bản Python trước đây.
    private static double round(double score) {
        return new BigDecimal(score).setScale(SCORE_SCALE, RoundingMode.HALF_EVEN).doubleValue();
    }

    // Gom thẻ thể loại, chủ đề, nhân vật và bộ phim để chấm điểm cộng hoặc điểm phạt.
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
            double score = item.rating() * RATING_WEIGHT;
            score += overlap(GENRE_WEIGHT, features.genreTags(), genres);
            score += overlap(THEME_WEIGHT, features.themeTags(), themes);
            score += overlap(CHARACTER_WEIGHT, features.characterTags(), characters);
            score += series.contains(item.seriesId()) ? SERIES_BONUS : 0;
            return score;
        }

        double penalty(RecommendationCandidate item) {
            var features = item.features();
            return (series.contains(item.seriesId()) ? DISLIKED_SERIES_PENALTY : 0.0)
                    + overlap(DISLIKED_THEME_PENALTY, features.themeTags(), themes)
                    + overlap(DISLIKED_CHARACTER_PENALTY, features.characterTags(), characters);
        }

        private static double overlap(double weight, Set<String> itemTags, Set<String> preferredTags) {
            long matched = itemTags.stream().filter(preferredTags::contains).count();
            return weight * matched / Math.max(1, preferredTags.size());
        }
    }

    // Cặp ứng viên và điểm đã làm tròn dùng trong lúc sắp hạng.
    private record Scored(RecommendationCandidate item, double score) {
        double rating() {
            return item.rating();
        }
    }
}
