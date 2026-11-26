package com.dippie.repository.admin;

import com.dippie.dto.admin.TopRatedMovieView;
import com.dippie.dto.admin.TopWatchedEpisodeView;
import com.dippie.model.catalog.EpisodeRuntime;
import com.dippie.model.catalog.PublicationStatus;
import com.dippie.model.progress.CompletionPolicy;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

// Tập hợp các truy vấn JPQL gom số liệu cho bảng điều khiển admin.
// Việt mà sửa ngưỡng hoàn thành bên CompletionPolicy thì nhớ cập nhật COMPLETED_PREDICATE ở đây,
// vì mình phải viết lại đúng công thức đó bằng JPQL để đếm thẳng trong DB.
@Repository
public class AdminStatsQuery {

    private static final long CAP_BOUNDARY_MS = CompletionPolicy.NEAR_END_CAP_MS * CompletionPolicy.COMPLETION_DIVISOR;
    private static final long COMPLETION_NUMERATOR = CompletionPolicy.COMPLETION_DIVISOR - 1L;
    private static final String KNOWN_EPISODE_DURATION = "ep.durationMs >= " + EpisodeRuntime.MINUTE_MS + "L";
    private static final String EFFECTIVE_DURATION =
            "(case "
                    + "when " + KNOWN_EPISODE_DURATION + " "
                    + "and ca.durationMs is not null and ca.durationMs > 0 "
                    + "and ca.durationMs < ep.durationMs then ca.durationMs "
                    + "when " + KNOWN_EPISODE_DURATION + " then ep.durationMs "
                    + "else ca.durationMs "
                    + "end)";

    private static final String COMPLETED_PREDICATE =
            "((" + KNOWN_EPISODE_DURATION + ") "
                    + "or (ca.durationMs is not null and ca.durationMs > 0)) "
                    + "and (((" + EFFECTIVE_DURATION + " >= " + CAP_BOUNDARY_MS + "L) "
                    + "and (wp.positionMs >= " + EFFECTIVE_DURATION + " - " + CompletionPolicy.NEAR_END_CAP_MS + "L)) "
                    + "or ((" + EFFECTIVE_DURATION + " >= " + CompletionPolicy.COMPLETION_DIVISOR + "L) "
                    + "and (" + EFFECTIVE_DURATION + " < " + CAP_BOUNDARY_MS + "L) "
                    + "and (wp.positionMs * " + CompletionPolicy.COMPLETION_DIVISOR + "L >= "
                    + EFFECTIVE_DURATION + " * " + COMPLETION_NUMERATOR + "L)) "
                    + "or ((" + EFFECTIVE_DURATION + " < " + CompletionPolicy.COMPLETION_DIVISOR + "L) "
                    + "and (wp.positionMs >= " + EFFECTIVE_DURATION + " - 1L)))";

    private static final String WATCH_SESSION_COUNTS_JPQL =
            "select count(wp), coalesce(sum(case when " + COMPLETED_PREDICATE + " then 1L else 0L end), 0L) "
                    + "from WatchProgress wp "
                    + "left join CatalogEpisode ep on ep.id = wp.episodeId "
                    + "left join CatalogMediaAsset ca on ca.id = wp.assetId";

    private final EntityManager entityManager;

    public AdminStatsQuery(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    // Đếm tổng tài khoản, số đang mở và số đăng ký mới từ mốc since trong một câu query.
    public UserCounts countUsers(LocalDateTime since) {
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

    // Bộ ba số đếm tài khoản gồm tổng số, đang hoạt động và mới đăng ký.
    public record UserCounts(long total, long active, long recent) {
    }

    // Tổng số phiên xem kèm số phiên đạt ngưỡng hoàn thành.
    public record WatchSessionCounts(long total, long completed) {
    }

    // Đếm tất cả đầu phim hiện có trong bảng movies.
    public long countTotalMovies() {
        return entityManager.createQuery("select count(m) from Movie m", Long.class).getSingleResult();
    }

    // Chỉ tính tập đã đăng khi cả bộ lẫn mùa cha (nếu có) đều đang ready; trạng thái bộ và mùa nằm trong JSON nên phải lọc thêm ở Java.
    public long countPublishedEpisodes() {
        List<Object[]> rows = entityManager.createQuery("""
                        select e.seasonId, s.recordJson, sea.recordJson
                        from CatalogEpisode e
                        join CatalogSeries s on s.id = e.seriesId
                        left join CatalogSeason sea on sea.id = e.seasonId
                        where e.publicationStatus = :ready
                        """, Object[].class)
                .setParameter("ready", PublicationStatus.READY_VALUE)
                .getResultList();
        long count = 0L;
        for (Object[] row : rows) {
            String seasonId = (String) row[0];
            String seriesRecord = (String) row[1];
            String seasonRecord = (String) row[2];
            if (isParentChainPublished(seasonId, seriesRecord, seasonRecord)) {
                count++;
            }
        }
        return count;
    }

    // Đếm số phiên xem và số phiên hoàn thành theo thời lượng hiệu lực của tập và tệp phát.
    public WatchSessionCounts countWatchSessions() {
        Object[] row = entityManager.createQuery(WATCH_SESSION_COUNTS_JPQL, Object[].class).getSingleResult();
        return new WatchSessionCounts(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
    }

    // Lấy dư gấp 3 lần limit từ DB rồi mới lọc chuỗi bộ và mùa đã đăng ở Java để sau khi loại tập ẩn vẫn đủ số lượng cần hiển thị.
    public List<TopWatchedEpisodeView> findTopWatchedEpisodes(int limit) {
        List<Object[]> rows = entityManager.createQuery("""
                        select wp.episodeId, ep.title, count(wp.userId), ep.seasonId, s.recordJson, sea.recordJson
                        from WatchProgress wp
                        join CatalogEpisode ep on ep.id = wp.episodeId
                        join CatalogSeries s on s.id = ep.seriesId
                        left join CatalogSeason sea on sea.id = ep.seasonId
                        where ep.publicationStatus = :ready
                        group by wp.episodeId, ep.title, ep.seasonId, s.recordJson, sea.recordJson
                        order by count(wp.userId) desc, wp.episodeId asc
                        """, Object[].class)
                .setParameter("ready", PublicationStatus.READY_VALUE)
                .setMaxResults(Math.max(limit * 3, limit))
                .getResultList();
        return rows.stream()
                .filter(row -> isParentChainPublished((String) row[3], (String) row[4], (String) row[5]))
                .limit(limit)
                .map(row -> new TopWatchedEpisodeView(
                        (String) row[0],
                        (String) row[1],
                        ((Number) row[2]).longValue()))
                .toList();
    }

    // Xếp hạng phim theo điểm trung bình rồi tới số lượt đánh giá; bỏ qua phim nào có bản ghi danh mục đang để nháp hoặc ẩn.
    public List<TopRatedMovieView> findTopRatedMovies(int limit) {
        List<Object[]> rows = entityManager.createQuery("""
                        select r.movieId, m.title, avg(r.rating * 1.0), count(r.id), s.id, s.recordJson
                        from MovieRating r
                        join Movie m on m.id = r.movieId
                        left join CatalogSeries s on s.id = m.id
                        group by r.movieId, m.title, s.id, s.recordJson
                        having count(r.id) >= 1
                        order by avg(r.rating * 1.0) desc, count(r.id) desc, r.movieId asc
                        """, Object[].class)
                .setMaxResults(Math.max(limit * 3, limit))
                .getResultList();
        return rows.stream()
                .filter(row -> row[4] == null || PublicationStatus.of((String) row[5]).isPublished())
                .limit(limit)
                .map(row -> new TopRatedMovieView(
                        (String) row[0],
                        (String) row[1],
                        ((Number) row[2]).doubleValue(),
                        ((Number) row[3]).longValue()))
                .toList();
    }

    // Kiểm tra bộ phim cha và mùa phim cha (khi tập có gắn mùa) đều đang xuất bản.
    private static boolean isParentChainPublished(String seasonId, String seriesRecord, String seasonRecord) {
        if (!PublicationStatus.of(seriesRecord).isPublished()) {
            return false;
        }
        return seasonId == null || seasonId.isEmpty()
                || (seasonRecord != null && PublicationStatus.of(seasonRecord).isPublished());
    }
}
