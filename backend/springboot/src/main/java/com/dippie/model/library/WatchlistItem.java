package com.dippie.model.library;

import com.dippie.model.movie.Movie;
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

// bảng watchlist_items, kế thừa LibraryEntry dùng lại mấy cột cơ bản
@Entity
@Table(
        name = "watchlist_items",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_user_movie_watchlist", columnNames = {"user_id", "movie_id"})
        },
        indexes = {
                @Index(name = "idx_watchlist_movie", columnList = "movie_id"),
                @Index(name = "idx_watchlist_user_added", columnList = "user_id, added_at")
        }
)
public class WatchlistItem extends LibraryEntry {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_watchlist_user")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "movie_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_watchlist_movie")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Movie movie;

    protected WatchlistItem() {
    }

    public WatchlistItem(Long userId, String movieId, LocalDateTime addedAt) {
        super(userId, movieId, addedAt);
    }
}
