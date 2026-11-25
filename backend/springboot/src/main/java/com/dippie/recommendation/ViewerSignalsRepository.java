package com.dippie.recommendation;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
@Transactional(readOnly = true)
class ViewerSignalsRepository {

    private static final int RECENT_LIMIT = 100;

    private final EntityManager entities;

    ViewerSignalsRepository(EntityManager entities) {
        this.entities = entities;
    }

    ViewerSignals load(Long userId, List<String> requested) {
        Set<String> liked = new HashSet<>(requested);
        Set<String> seen = new HashSet<>(liked);
        if (userId != null) {
            List<String> favorites = entities.createQuery("""
                    select f.movieId from FavoriteItem f where f.userId = :userId order by f.addedAt desc, f.id desc
                    """, String.class).setParameter("userId", userId).getResultList();
            liked.addAll(favorites.subList(0, Math.min(RECENT_LIMIT, favorites.size())));
            seen.addAll(favorites);

            collectSignals("""
                    select r.movieId, r.rating from MovieRating r where r.userId = :userId
                    order by r.updatedAt desc, r.id desc
                    """, userId, 8, liked, seen);
            collectSignals("""
                    select p.episodeId, p.positionMs from WatchProgress p where p.userId = :userId
                    order by p.updatedAt desc, p.id desc
                    """, userId, 1, liked, seen);
            seen.addAll(liked);
        }
        return new ViewerSignals(Set.copyOf(liked), Set.copyOf(seen));
    }

    private void collectSignals(String query, long userId, long likedThreshold, Set<String> liked, Set<String> seen) {
        int likedCount = 0;
        for (Object[] row : entities.createQuery(query, Object[].class).setParameter("userId", userId).getResultList()) {
            String id = (String) row[0];
            seen.add(id);
            if (likedCount < RECENT_LIMIT && row[1] instanceof Number number && number.longValue() >= likedThreshold) {
                liked.add(id);
                likedCount++;
            }
        }
    }
}
