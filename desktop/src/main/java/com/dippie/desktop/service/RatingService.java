package com.dippie.desktop.service;

import com.dippie.desktop.dao.AuditDao;
import com.dippie.desktop.dao.MovieDao;
import com.dippie.desktop.dao.RatingDao;
import com.dippie.desktop.dao.UserDao;
import com.dippie.desktop.db.DataAccessException;
import com.dippie.desktop.db.JdbcDatabase;
import com.dippie.desktop.model.AdminSession;
import com.dippie.desktop.model.Movie;
import com.dippie.desktop.model.Rating;
import com.dippie.desktop.model.RatingRow;

import java.util.List;
import java.util.stream.Collectors;

// service cho tab Đánh giá, lo tải danh sách kèm tên phim và xóa đánh giá kèm audit log trong 1 transaction
public final class RatingService {
    private final JdbcDatabase db;
    private final RatingDao ratingDao;
    private final MovieDao movieDao;
    private final UserDao userDao;
    private final AuditDao auditDao;

    public RatingService(JdbcDatabase db, RatingDao ratingDao, MovieDao movieDao, UserDao userDao, AuditDao auditDao) {
        this.db = db;
        this.ratingDao = ratingDao;
        this.movieDao = movieDao;
        this.userDao = userDao;
        this.auditDao = auditDao;
    }

    // ghép tên phim từ bảng movies vào từng đánh giá, phim cũ ngoài danh mục thì gắn nhãn Phim ngoài danh mục
    public List<RatingRow> list() {
        return db.read(connection -> {
            var movieTitles = movieDao.findRatedMovies(connection).stream()
                    .collect(Collectors.toMap(Movie::id, Movie::title));
            return ratingDao.findContexts(connection).stream().map(context -> new RatingRow(context.rating(), context.username(),
                    movieTitles.getOrDefault(context.rating().movieId(), context.rating().movieId()),
                    context.seriesId(), context.seriesTitle() == null ? "Phim ngoài danh mục" : context.seriesTitle())).toList();
        });
    }

    // khóa dòng đánh giá rồi so với bản chụp lúc admin bấm xóa kẻo xóa nhầm khi user vừa đổi điểm
    public void delete(AdminSession session, RatingRow selected) {
        db.transaction(connection -> {
            var actor = AdminQuery.requireCurrent(session, userDao.lockAccount(connection, session.userId()).orElse(null));
            Rating current = ratingDao.lockRating(connection, selected.id())
                    .orElseThrow(() -> new DataAccessException.Conflict("Đánh giá này đã bị xóa trước đó. Hãy làm mới bảng."));
            if (!current.equals(selected.rating())) {
                throw new DataAccessException.Conflict("Đánh giá này vừa thay đổi. Hãy làm mới bảng rồi xem lại trước khi xóa.");
            }

            // xóa và ghi audit log trên cùng kết nối để 2 bước cùng thành công hoặc cùng rollback
            if (ratingDao.delete(connection, current.id()) != 1) {
                throw new DataAccessException.Conflict("Chưa xóa được đánh giá này.");
            }
            String details = """
                    {"userId":%d,"rating":%d}
                    """.formatted(current.userId(), current.score()).strip();
            auditDao.insert(connection, actor.id(), actor.username(), "RATING_DELETED", "RATING",
                    Long.toString(current.id()), details);
            return null;
        });
    }
}
