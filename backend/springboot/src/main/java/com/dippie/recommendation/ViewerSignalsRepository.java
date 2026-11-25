package com.dippie.recommendation;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Gom lịch sử yêu thích, chấm điểm và tiến độ xem từ CSDL thành ViewerSignals cho tầng gợi ý.
@Repository
@Transactional(readOnly = true)
class ViewerSignalsRepository {

    private static final int RECENT_LIMIT = 100;
    private static final long LIKED_RATING_THRESHOLD = 8L;
    private static final long DISLIKED_RATING_THRESHOLD = 4L;
    private static final long MIN_WATCH_POSITION_MS = 1L;

    private final EntityManager entities;

    ViewerSignalsRepository(EntityManager entities) {
        this.entities = entities;
    }

    // Ghép mã phim gửi kèm trong request với tối đa 100 mục gần nhất từ mỗi bảng tương tác; phim chấm điểm thấp (<= 4) sẽ ghi đè tín hiệu thích.
    ViewerSignals load(Long userId, List<String> requestedLikedIds) {
        Set<String> liked = new HashSet<>(requestedLikedIds);
        Set<String> seen = new HashSet<>(liked);
        Set<String> disliked = new HashSet<>();
        if (userId != null) {
            // Lấy toàn bộ danh sách yêu thích vào seen để khỏi gợi ý lại, nhưng chỉ lấy 100 mục mới nhất làm tín hiệu thích.
            List<String> favorites = entities.createQuery("""
                    select f.movieId from FavoriteItem f where f.userId = :userId order by f.addedAt desc, f.id desc
                    """, String.class).setParameter("userId", userId).getResultList();
            liked.addAll(favorites.subList(0, Math.min(RECENT_LIMIT, favorites.size())));
            seen.addAll(favorites);

            // Chấm từ 8 điểm trở lên tính là thích, từ 4 điểm trở xuống tính là không thích; tập đã mở xem (>= 1ms) cũng đưa vào cả seen lẫn liked.
            collectSignals("""
                    select r.movieId, r.rating from MovieRating r where r.userId = :userId
                    order by r.updatedAt desc, r.id desc
                    """, userId, LIKED_RATING_THRESHOLD, liked, seen, disliked);
            collectSignals("""
                    select p.episodeId, p.positionMs from WatchProgress p where p.userId = :userId
                    order by p.updatedAt desc, p.id desc
                    """, userId, MIN_WATCH_POSITION_MS, liked, seen, null);
        }
        // Nếu người xem đã bấm thích rồi sau đó chấm điểm thấp thì ưu tiên tín hiệu không thích.
        liked.removeAll(disliked);
        return new ViewerSignals(Set.copyOf(liked), Set.copyOf(seen), Set.copyOf(disliked));
    }

    private void collectSignals(String query, long userId, long likedThreshold, Set<String> liked, Set<String> seen,
            Set<String> disliked) {
        int likedCount = 0;
        for (Object[] row : entities.createQuery(query, Object[].class).setParameter("userId", userId).getResultList()) {
            String itemId = (String) row[0];
            seen.add(itemId);
            long metric = ((Number) row[1]).longValue();
            if (disliked != null && metric <= DISLIKED_RATING_THRESHOLD) {
                disliked.add(itemId);
            }
            if (likedCount < RECENT_LIMIT && metric >= likedThreshold) {
                liked.add(itemId);
                likedCount++;
            }
        }
    }
}
