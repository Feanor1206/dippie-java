package com.dippie.model.library;

import com.dippie.model.movie.Movie;
import com.dippie.model.profile.ChildProfile;
import com.dippie.model.user.User;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

// bảng favorite_items, kế thừa LibraryEntry lấy mấy cột chung rồi khai báo riêng khóa ngoại với unique key
@Entity
@Table(
        name = "favorite_items",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_user_profile_movie_favorite", columnNames = {"user_id", "profile_scope_id", "movie_id"})
        },
        indexes = {
                @Index(name = "idx_favorite_movie", columnList = "movie_id"),
                @Index(name = "idx_favorite_user_added", columnList = "user_id, added_at"),
                @Index(name = "idx_favorite_user_profile_added", columnList = "user_id, profile_scope_id, added_at")
        }
)
public class FavoriteItem extends LibraryEntry {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_favorite_user")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "profile_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_favorite_profile")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChildProfile profile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "movie_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_favorite_movie")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Movie movie;

    protected FavoriteItem() {
    }

    public FavoriteItem(Long userId, String movieId, LocalDateTime addedAt) {
        super(userId, null, movieId, addedAt);
    }

    public FavoriteItem(Long userId, Long profileId, String movieId, LocalDateTime addedAt) {
        super(userId, profileId, movieId, addedAt);
    }
}
