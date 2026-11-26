package com.dippie.model.library;

import com.dippie.exception.InvalidRequestException;
import com.dippie.model.movie.Movie;
import com.dippie.model.profile.ChildProfile;
import com.dippie.model.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

// bảng movie_ratings lưu điểm chấm 1 đến 10 của từng user theo phim
// chặn hai lớp luôn: validateRating ngay trong entity kèm @Check dưới SQL cho khỏi lọt điểm bậy
@Entity
@Table(
        name = "movie_ratings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_user_profile_movie_rating", columnNames = {"user_id", "profile_scope_id", "movie_id"})
        },
        indexes = {
                @Index(name = "idx_ratings_movie", columnList = "movie_id"),
                @Index(name = "idx_ratings_user_updated", columnList = "user_id, updated_at"),
                @Index(name = "idx_ratings_user_profile_updated", columnList = "user_id, profile_scope_id, updated_at")
        }
)
@Check(name = "ck_movie_rating_range", constraints = "rating >= 1 AND rating <= 10")
public class MovieRating {

    public static final int MIN_RATING = 1;
    public static final int MAX_RATING = 10;
    public static final String INVALID_RATING_MESSAGE = "Điểm đánh giá cần từ 1 đến 10.";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "profile_id")
    private Long profileId;

    @Column(name = "profile_scope_id", nullable = false)
    @ColumnDefault("0")
    private long profileScopeId;

    @Column(name = "movie_id", length = 100, nullable = false)
    private String movieId;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP", updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_rating_user")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "profile_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_rating_profile")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChildProfile profile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "movie_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_rating_movie")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Movie movie;

    protected MovieRating() {
    }

    public MovieRating(Long userId, String movieId, int rating, LocalDateTime timestamp) {
        this(userId, null, movieId, rating, timestamp);
    }

    public MovieRating(Long userId, Long profileId, String movieId, int rating, LocalDateTime timestamp) {
        validateRating(rating);
        this.userId = userId;
        this.profileId = profileId;
        this.profileScopeId = profileId == null ? 0L : profileId;
        this.movieId = movieId;
        this.rating = rating;
        this.createdAt = timestamp;
        this.updatedAt = timestamp;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getProfileId() {
        return profileId;
    }

    public long getProfileScopeId() {
        return profileScopeId;
    }

    public String getMovieId() {
        return movieId;
    }

    public int getRating() {
        return rating;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    // check điểm trước lúc ghi đè kẻo lọt số ngoài 1 đến 10
    public void updateRating(int newRating, LocalDateTime timestamp) {
        validateRating(newRating);
        this.rating = newRating;
        this.updatedAt = timestamp;
    }

    // chặn null hay điểm ngoài 1 đến 10 trước khi đụng xuống DB
    public static void validateRating(Integer rating) {
        if (rating == null || rating < MIN_RATING || rating > MAX_RATING) {
            throw new InvalidRequestException(INVALID_RATING_MESSAGE);
        }
    }
}
