package com.dippie.admin;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class AdminStatsQuery {

    private static final String EFFECTIVE_DURATION =
            "(case "
                    + "when ep.durationMs is not null and ep.durationMs > 0 "
                    + "and ca.durationMs is not null and ca.durationMs > 0 "
                    + "and ca.durationMs < ep.durationMs then ca.durationMs "
                    + "when ep.durationMs is not null and ep.durationMs > 0 then ep.durationMs "
                    + "else ca.durationMs "
                    + "end)";

    private static final String COMPLETED_SESSIONS_JPQL =
            "select count(wp) "
                    + "from WatchProgress wp "
                    + "left join CatalogEpisode ep on ep.id = wp.episodeId "
                    + "left join CatalogMediaAsset ca on ca.id = wp.assetId "
                    + "where ((ep.durationMs is not null and ep.durationMs > 0) "
                    + "or (ca.durationMs is not null and ca.durationMs > 0)) "
                    + "and (((" + EFFECTIVE_DURATION + " >= 1200000L) "
                    + "and (wp.positionMs >= " + EFFECTIVE_DURATION + " - 60000L)) "
                    + "or ((" + EFFECTIVE_DURATION + " >= 20L) "
                    + "and (" + EFFECTIVE_DURATION + " < 1200000L) "
                    + "and (wp.positionMs * 20L >= " + EFFECTIVE_DURATION + " * 19L)) "
                    + "or ((" + EFFECTIVE_DURATION + " < 20L) "
                    + "and (wp.positionMs >= " + EFFECTIVE_DURATION + " - 1L)))";

    private final EntityManager entityManager;

    public AdminStatsQuery(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    UserCounts countUsers(LocalDateTime since) {
        Object[] row = entityManager.createQuery("""
                        select count(u),
                               coalesce(sum(case when u.enabled = true then 1L else 0L end), 0L),
                               coalesce(sum(case when u.createdAt >= :since then 1L else 0L end), 0L)
                        from User u
                        """, Object[].class)
                .setParameter("since", since)
                .getSingleResult();
        return new UserCounts(
                ((Number) row[0]).longValue(),
                ((Number) row[1]).longValue(),
                ((Number) row[2]).longValue()
        );
    }

    record UserCounts(long total, long active, long recent) {
    }

    public long countTotalMovies() {
        return entityManager.createQuery("select count(m) from Movie m", Long.class).getSingleResult();
    }

    public long countPublishedEpisodes() {
        return entityManager.createQuery(
                "select count(e) from CatalogEpisode e where e.publicationStatus = 'ready'",
                Long.class
        ).getSingleResult();
    }

    public long countTotalWatchSessions() {
        return entityManager.createQuery("select count(wp) from WatchProgress wp", Long.class).getSingleResult();
    }

    public long countCompletedWatchSessions() {
        return entityManager.createQuery(COMPLETED_SESSIONS_JPQL, Long.class).getSingleResult();
    }

    public List<TopWatchedEpisodeView> findTopWatchedEpisodes(int limit) {
        return entityManager.createQuery("""
                        select new com.dippie.admin.TopWatchedEpisodeView(
                            wp.episodeId,
                            ep.title,
                            count(distinct wp.userId)
                        )
                        from WatchProgress wp
                        join CatalogEpisode ep on ep.id = wp.episodeId
                        group by wp.episodeId, ep.title
                        order by count(distinct wp.userId) desc, wp.episodeId asc
                        """, TopWatchedEpisodeView.class)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<TopRatedMovieView> findTopRatedMovies(int limit) {
        return entityManager.createQuery("""
                        select new com.dippie.admin.TopRatedMovieView(
                            r.movieId,
                            m.title,
                            avg(r.rating * 1.0),
                            count(r.id)
                        )
                        from MovieRating r
                        join Movie m on m.id = r.movieId
                        group by r.movieId, m.title
                        having count(r.id) >= 1
                        order by avg(r.rating * 1.0) desc, count(r.id) desc, r.movieId asc
                        """, TopRatedMovieView.class)
                .setMaxResults(limit)
                .getResultList();
    }
}
