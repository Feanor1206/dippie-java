package com.dippie.repository.recommendation;

import com.dippie.model.recommendation.ViewerSignals;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// gom lịch sử yêu thích, chấm điểm với tiến độ xem từ DB thành ViewerSignals cho tầng gợi ý
@Repository
@Transactional(readOnly = true)
public class ViewerSignalsRepository {

    private static final int RECENT_LIMIT = 100;

    private final EntityManager entities;

    public ViewerSignalsRepository(EntityManager entities) {
        this.entities = entities;
    }

    public ViewerSignals load(Long userId, List<String> requestedLikedIds) {
        return load(userId, 0L, requestedLikedIds);
    }

    // ghép mã phim trong request với tối đa 100 mục gần nhất mỗi bảng theo đúng hồ sơ đang mở, chấm <= 4 sẽ đè tín hiệu thích
    public ViewerSignals load(Long userId, long profileScopeId, List<String> requestedLikedIds) {
        Set<String> liked = new HashSet<>(requestedLikedIds);
        Set<String> seen = new HashSet<>(liked);
        Set<String> disliked = new HashSet<>();
        if (userId != null) {
            // đẩy hết yêu thích vào seen để khỏi gợi ý lại, nhưng chỉ lấy 100 mục mới nhất làm tín hiệu thích
            List<String> favorites = entities.createQuery("""
                    select f.movieId from FavoriteItem f
                    where f.userId = :userId and f.profileScopeId = :profileScopeId
                    order by f.addedAt desc, f.id desc
                    """, String.class)
                    .setParameter("userId", userId)
                    .setParameter("profileScopeId", profileScopeId)
                    .getResultList();
            liked.addAll(favorites.subList(0, Math.min(RECENT_LIMIT, favorites.size())));
            seen.addAll(favorites);

            // chấm >= 8 tính là thích, <= 4 tính là không thích, tập đã mở xem (>= 1ms) cho vào cả seen lẫn liked
            collectSignals("""
                    select r.movieId, r.rating from MovieRating r
                    where r.userId = :userId and r.profileScopeId = :profileScopeId
                    order by r.updatedAt desc, r.id desc
                    """, userId, profileScopeId, 8L, liked, seen, disliked);
            collectSignals("""
                    select p.episodeId, p.positionMs from WatchProgress p
                    where p.userId = :userId and p.profileScopeId = :profileScopeId
                    order by p.updatedAt desc, p.id desc
                    """, userId, profileScopeId, 1L, liked, seen, null);
        }
        // lỡ bấm thích trước rồi sau lại chấm thấp thì ưu tiên tính không thích
        liked.removeAll(disliked);
        return new ViewerSignals(Set.copyOf(liked), Set.copyOf(seen), Set.copyOf(disliked));
    }

    private void collectSignals(String query, long userId, long profileScopeId, long likedThreshold, Set<String> liked,
            Set<String> seen, Set<String> disliked) {
        int likedCount = 0;
        for (Object[] row : entities.createQuery(query, Object[].class)
                .setParameter("userId", userId)
                .setParameter("profileScopeId", profileScopeId)
                .getResultList()) {
            String itemId = (String) row[0];
            seen.add(itemId);
            long metric = ((Number) row[1]).longValue();
            if (disliked != null && metric <= 4L) {
                disliked.add(itemId);
            }
            if (likedCount < RECENT_LIMIT && metric >= likedThreshold) {
                liked.add(itemId);
                likedCount++;
            }
        }
    }
}
