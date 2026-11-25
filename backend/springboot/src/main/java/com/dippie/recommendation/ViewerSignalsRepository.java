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

    public ViewerSignals load(Long userId, List<String> requested) {
        Set<String> liked = new HashSet<>(requested);
        Set<String> seen = new HashSet<>(liked);
        if (userId != null) {
            List<String> favorites = ids("""
                    select f.movieId from FavoriteItem f where f.userId = :userId order by f.addedAt desc, f.id desc
                    """, userId);
            liked.addAll(favorites.subList(0, Math.min(RECENT_LIMIT, favorites.size())));
            liked.addAll(recent("""
                    select r.movieId from MovieRating r where r.userId = :userId and r.rating >= 8
                    order by r.updatedAt desc, r.id desc
                    """, userId));
            liked.addAll(recent("""
                    select p.episodeId from WatchProgress p where p.userId = :userId and p.positionMs > 0
                    order by p.updatedAt desc, p.id desc
                    """, userId));

            seen.addAll(liked);
            seen.addAll(favorites);
            seen.addAll(ids("select r.movieId from MovieRating r where r.userId = :userId", userId));
            seen.addAll(ids("select p.episodeId from WatchProgress p where p.userId = :userId", userId));
        }
        return new ViewerSignals(Set.copyOf(liked), Set.copyOf(seen));
    }

    private List<String> ids(String query, long userId) {
        return entities.createQuery(query, String.class).setParameter("userId", userId).getResultList();
    }

    private List<String> recent(String query, long userId) {
        return entities.createQuery(query, String.class).setParameter("userId", userId)
                .setMaxResults(RECENT_LIMIT).getResultList();
    }
}
