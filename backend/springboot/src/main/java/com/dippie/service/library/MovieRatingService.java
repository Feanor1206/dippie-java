package com.dippie.service.library;

import com.dippie.dto.library.MovieRatingView;
import com.dippie.exception.InvalidRequestException;
import com.dippie.model.library.MovieRating;
import com.dippie.model.movie.Movie;
import com.dippie.repository.library.MovieRatingRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;
import com.dippie.service.catalog.CatalogReadModelCache;
import com.dippie.service.movie.MovieVisibility;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

// lo xem, chấm, sửa với xóa điểm đánh giá phim cho user đang đăng nhập
@Service
public class MovieRatingService {

    private final MovieRatingRepository ratingRepository;
    private final MovieVisibility movieVisibility;
    private final CatalogReadModelCache catalogReadModelCache;
    private final CurrentViewer currentViewer;
    private final TransactionTemplate requiresNewTransaction;
    private final TransactionTemplate readTransaction;
    private final Clock clock;

    public MovieRatingService(
            MovieRatingRepository ratingRepository,
            MovieVisibility movieVisibility,
            CatalogReadModelCache catalogReadModelCache,
            CurrentViewer currentViewer,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this.ratingRepository = ratingRepository;
        this.movieVisibility = movieVisibility;
        this.catalogReadModelCache = catalogReadModelCache;
        this.currentViewer = currentViewer;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.clock = clock;
    }

    // trả list điểm mới sửa nhất lên đầu theo hồ sơ hiện tại, bỏ qua mấy phim đang ẩn hoặc chưa xuất bản
    public List<MovieRatingView> list() {
        Viewer viewer = currentViewer.require();
        Set<String> hiddenIds = catalogReadModelCache.current().hiddenMovieIds();
        return readTransaction.execute(status -> ratingRepository
                .findByUserIdAndProfileScopeIdOrderByUpdatedAtDesc(viewer.userId(), viewer.profileScopeId())
                .stream()
                .filter(rating -> !hiddenIds.contains(rating.getMovieId()))
                .map(MovieRatingView::from)
                .toList());
    }

    // chấm mới hay sửa điểm phim đang mở, tách REQUIRES_NEW để bắt lỗi đụng khóa lúc 2 request gửi cùng lúc
    public MovieRatingView rate(String rawMovieId, Integer rating) {
        Viewer viewer = currentViewer.require();
        // check đầu vào với phim còn đang mở trước khi mở transaction ghi
        String movieId = rawMovieId == null ? "" : rawMovieId.strip();
        if (movieId.isEmpty()) {
            throw new InvalidRequestException(MovieRating.INVALID_RATING_MESSAGE);
        }
        MovieRating.validateRating(rating);
        Movie movie = movieVisibility.findVisibleById(movieId)
                .orElseThrow(() -> new InvalidRequestException("Không tìm thấy phim: " + movieId));

        // chạy transaction riêng để lỡ race condition đụng unique key thì rollback gọn, rồi mở transaction khác cập nhật dòng vừa chèn
        MovieRating saved;
        try {
            saved = requiresNewTransaction.execute(status ->
                    upsertRating(viewer.userId(), viewer.profileId(), viewer.profileScopeId(), movie.getId(), rating));
        } catch (DataIntegrityViolationException ex) {
            saved = requiresNewTransaction.execute(status ->
                    updateExistingRating(viewer.userId(), viewer.profileScopeId(), movie.getId(), rating, ex));
        }
        return MovieRatingView.from(saved);
    }

    public void remove(String movieId) {
        Viewer viewer = currentViewer.require();
        requiresNewTransaction.executeWithoutResult(status ->
                ratingRepository.deleteByUserIdAndProfileScopeIdAndMovieId(viewer.userId(), viewer.profileScopeId(), movieId));
    }

    // có điểm rồi thì sửa, chưa có thì tạo mới gắn profileId rồi flush ngay trong transaction
    private MovieRating upsertRating(Long userId, Long profileId, long profileScopeId, String movieId, int rating) {
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        return ratingRepository.findByUserIdAndProfileScopeIdAndMovieId(userId, profileScopeId, movieId)
                .map(existing -> {
                    existing.updateRating(rating, now);
                    return ratingRepository.saveAndFlush(existing);
                })
                .orElseGet(() -> ratingRepository.saveAndFlush(new MovieRating(userId, profileId, movieId, rating, now)));
    }

    // nhánh dự phòng lúc 2 request cùng tạo trùng (userId, profileScopeId, movieId), đọc lại dòng vừa thắng rồi cập nhật điểm mới nhất
    private MovieRating updateExistingRating(
            Long userId,
            long profileScopeId,
            String movieId,
            int rating,
            DataIntegrityViolationException original
    ) {
        MovieRating existing = ratingRepository.findByUserIdAndProfileScopeIdAndMovieId(userId, profileScopeId, movieId)
                .orElseThrow(() -> original);
        existing.updateRating(rating, LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
        return ratingRepository.saveAndFlush(existing);
    }
}
